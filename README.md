Use this tool to extract metadata from microscopy formats supported by Bioformats and convert to OME-XML.
Because CZI format stores multiple resolutions as different `Image` tags, by default the ouput will be only first `Image` tag in the metadata.

Examples:

**XML output:**

`java -jar extract_meta.jar raw_microscopy.czi output.xml`

Will dump OME-XML metadata from CZI image to XML file


**XML output with remapping:**

`java -jar extract_meta.jar raw_microscopy.czi output.xml mapping.yaml`

Will replace Channel attributes Fluor, Name, SizeX, SizeY and dump OME-XML metadata from CZI image to XML file


**Replace OME metadata in OME-TIFF**

`java -jar extract_meta.jar raw_microscopy.czi output.ome.tiff`

Will replace metadata in the existing OME-TIFF file with metadata from raw file converted to OME-XML, or if output.ome.tiff does not exist will convert raw file to OME-TIFF.


**Replace OME metadata in OME-TIFF with remapping**

`java -jar extract_meta.jar raw_microscopy.czi output.ome.tiff mapping.yaml`

Will replace Channel attributes Fluor, Name, SizeX, SizeY of metadata from raw file and put this new metadata in the existing OME-TIFF file, or if output.ome.tiff does not exist will convert raw file to OME-TIFF.

**Dump all metadata from CZI to OME-XML**

`java -jar extract_meta.jar raw_microscopy.czi output.xml --nochanges`

Will dump metada without modifying it.

