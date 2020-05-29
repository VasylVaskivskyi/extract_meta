package com.extmet;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.yaml.snakeyaml.Yaml;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.common.RandomAccessInputStream;
import loci.common.RandomAccessOutputStream;
import loci.formats.tiff.TiffSaver;
import loci.formats.tools.ImageConverter;


class ExtractMeta
{
    public static void main(String[] args) throws IOException, FormatException
    {
        if (args.length == 0)
        {
            System.out.println("\nUSAGE: ExtractMeta Input Output Mapping Overwite\n\n" +
                    "Input - image file with metadata.\n\n" +
                    "Output - file to write OMEXML metadata.\n" +
                    "\tIf Output is XML file (e.g. metadata.xml), will just write xml metadata.\n" +
                    "\tIf Output is EXISTING OME-TIFF file will overwrite metadata in it.\n" +
                    "\tIf Output is NON-EXISTING OME-TIFF file will convert Input and write metadata to it.\n\n" +
                    "Mapping - json file that specifies how to rename channel names and fluorophores.\n" +
                    "--overwite - will overwrite output OME-TIFF if it already exists.\n"+
                    "--nochanges - will dump convert raw metadata to OMEXML without any modifications.\n");

            return;
        }
        String inputPath = args[0];
        String outputPath = args[1];
        String mappingPath = null;
        boolean overwrite = false;
        boolean noChanges = false;


        if (args.length > 2)
        {
            for (String arg : args)
            {
                if (arg.endsWith("yaml") || arg.endsWith("yml")){ mappingPath = arg; }
                if (arg.equals("--nochanges")){ noChanges = true; }
            }
        }

        String metadata = "";
        File inFile = new File(inputPath);
        File outputFile = new File(outputPath);


        if (!outputFile.exists()){ overwrite=false; }
        else
        {
            for (String arg : args)
            {
                if (arg.equals("--overwrite")){ overwrite = true; break;}
            }
        }

        if (!inFile.exists())
        {
            System.out.println("Input file does not exist");
            return;
        }

        if (inputPath.endsWith(".xml"))
        {
            metadata = readFile(inputPath);
        }
        else
        {
            try{ metadata = getOMEXML(inputPath); }
            catch(Exception e){ e.printStackTrace(); }
        }


        String strXML = "";
        HashMap<String, HashMap<String, String>> mappings = new HashMap<>();
        if (mappingPath != null)
        {
            mappings = readYAMLToMap(mappingPath);
        }

        org.w3c.dom.Document xml = convertStringToXMLDocument(metadata);
        replaceAttributes(xml, noChanges, mappings);

        try{ strXML = convertXMLToString(xml); }
        catch(Exception e){ e.printStackTrace(); }



        if (outputPath.endsWith(".xml") || outputPath.endsWith(".XML"))
        {
            Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            out.write(strXML);
            out.close();
        }
        else
        {

            if (outputFile.exists() && !outputFile.isDirectory() && (outputPath.endsWith(".tif") || outputPath.endsWith(".tiff")))
            {
                try { overwriteComment(outputPath, strXML); }
                catch(Exception e){ e.printStackTrace(); }
            }
            else if (!outputFile.exists() && !outputFile.isDirectory())
            {
                if ( overwrite ){ outputFile.delete(); }
                String[] argList = {inputPath, outputPath};
                ImageConverter.main(argList);
            }
        }

        System.out.println("Finished");
    }


    public static void replaceAttributes(org.w3c.dom.Document xml, boolean noChanges, HashMap<String,HashMap<String,String>> mappings)
    {
        if (noChanges == false)
        {
            NodeList images = xml.getElementsByTagName("Image");
            for (int i = images.getLength() - 1; i > 0; i--)
            {
                Node delImage = images.item(i);
                delImage.getParentNode().removeChild(delImage);
            }

            NodeList originalMetadata = xml.getElementsByTagName("StructuredAnnotations");
            for (int i = originalMetadata.getLength() - 1; i >= 0; i--)
            {
                Node delOriginMeta = originalMetadata.item(i);
                delOriginMeta.getParentNode().removeChild(delOriginMeta);
            }
        }
        Set<String> keySet = mappings.keySet();
        if (!keySet.isEmpty())
        {
            if (mappings.containsKey("fluor_name"))
            {
                HashMap<String, String> fluorMap = mappings.get("fluor_name");
                replaceNames(xml, fluorMap, "Fluor");
            }
            if (mappings.containsKey("channel_name"))
            {
                HashMap<String, String> channelMap = mappings.get("channel_name");
                replaceNames(xml, channelMap, "Name");
            }
            if (mappings.containsKey("size"))
            {
                HashMap<String, String> sizeMap = mappings.get("size");
                replaceSize(xml, sizeMap);
            }
            if (mappings.containsKey("physical_size"))
            {
                HashMap<String, String> physicalSizeMap = mappings.get("physical_size");
                replaceSize(xml, physicalSizeMap);
            }
        }
    }


    public static void replaceNames(org.w3c.dom.Document xml, HashMap<String,String> map, String attribute)
    {
        //org.w3c.dom.Document
        NodeList images = xml.getElementsByTagName("Image");

        if (!map.keySet().isEmpty())
        {
            Set<String> keySet = map.keySet();
            for (int i = 0; i < images.getLength(); i++)
            {
                Element image = (Element) images.item(0);
                Element pixels = (Element) image.getElementsByTagName("Pixels").item(0);
                NodeList channels = pixels.getElementsByTagName("Channel");

                for (int j = 0; j < channels.getLength(); j++)
                {
                    Element ch = (Element) channels.item(j);
                    //System.out.println(ch.getAttribute(attribute));
                    String attrValue = ch.getAttribute(attribute); //"Name" for channel name, "Fluor" for fluorophore name

                    if (keySet.contains(attrValue))
                    {
                        if (attribute.equals("Fluor"))
                        {
                            ch.setAttribute("Fluor", map.get(attrValue));
                            ch.setAttribute("Name", map.get(attrValue)); // Need to replace channel name as it is the same as fluor name in original metadata
                        }

                        else if (attribute.equals("Name"))
                        {
                            ch.setAttribute(attribute, map.get(attrValue));
                        }

                    }
                }
            }
        }
    }


    public static void replaceSize(Document xml, HashMap<String,String> map)
    {
        //org.w3c.dom.Document
        NodeList images = xml.getElementsByTagName("Image");

        if (!map.keySet().isEmpty())
        {
            Set<String> keySet = map.keySet();
            for (String name : keySet)
            {
                String size = map.get(name);
                Element image = (Element) images.item(0);
                Element pixels = (Element) image.getElementsByTagName("Pixels").item(0);
                pixels.setAttribute(name, size);
            }
        }
    }

    public static String convertXMLToString(Document xml) throws TransformerException
    {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(xml), new StreamResult(writer));
        String output = writer.toString();
        return output;
    }


    public static String readFile(String path)
    {
        StringBuilder sb = new StringBuilder();
        try ( BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8)) )
        {
            while ( true )
            {
                String sCurrentLine = br.readLine();
                if ( sCurrentLine != null ){ sb.append(sCurrentLine); }
                else { break; }
            }
        }
        catch(IOException e){ e.printStackTrace(); }

        return sb.toString();
    }


    public static Document convertStringToXMLDocument(String xmlString)
    {
        //Parser that produces DOM object trees from XML content
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        //API to obtain DOM Document instance
        DocumentBuilder builder = null;
        try
        {
            //Create DocumentBuilder with default configuration
            builder = factory.newDocumentBuilder();

            //Parse the content to Document object
            Document doc = builder.parse(new InputSource(new StringReader(xmlString)));
            return doc;
        }
        catch (Exception e) { e.printStackTrace(); }
        return null;
    }


    public static  HashMap<String, HashMap<String, String>> readYAMLToMap(String path) throws IOException
    {
        Yaml yaml = new Yaml();

        //String yamlAsString = readToString(path);
        InputStream inputStream = new FileInputStream(new File(path));
        HashMap<String, ArrayList<HashMap<String,Object>>> yamlData = yaml.load(inputStream);
        Set<String> keySet = yamlData.keySet();
        HashMap<String, HashMap<String, String>> mappedYAML = new HashMap<>();

        for (String key : keySet)
        {
            ArrayList<HashMap<String,Object>> valueList = yamlData.get(key);
            HashMap<String,String> valueMap = new HashMap<>();
            for (HashMap<String,Object> values : valueList)
            {
                String firstKey = values.keySet().iterator().next();
                String firstValue = values.values().iterator().next().toString();

                valueMap.put(firstKey, firstValue);
            }
            mappedYAML.put(key,valueMap);
        }

        return mappedYAML;
    }

    public static String readToString(String path) throws IOException
    {
        try (BufferedReader br = new BufferedReader(new FileReader(path)))
        {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null)
            {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            String everything = sb.toString();
            return everything;
        }
    }

    public static String getOMEXML(String path) throws IOException, DependencyException, ServiceException, FormatException
    {
        ServiceFactory serviceFactory = new ServiceFactory();
        OMEXMLService omexmlService = serviceFactory.getInstance(OMEXMLService.class);
        IMetadata meta = omexmlService.createOMEXMLMetadata();

        ImageReader r = new ImageReader();
        r.setMetadataStore(meta);
        r.setOriginalMetadataPopulated(true);
        r.setId(path);
        r.close();

        String OMEMeta = omexmlService.getOMEXML(meta);
        return OMEMeta;
    }


    public static void overwriteComment(String file, String comment) throws IOException, DependencyException
    {
        RandomAccessInputStream in = null;
        RandomAccessOutputStream out = null;
        try
        {
            in = new RandomAccessInputStream(file);
            out = new RandomAccessOutputStream(file);
            TiffSaver saver = new TiffSaver(out, file);
            saver.overwriteComment(in, comment);
            saver.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                if (in != null) in.close();
            }
            catch (Exception e) {}

            try
            {
                if (out != null) out.close();
            }
            catch (Exception e) {}

        }
    }


}
