CCI SST Visualisations
======================

This repository contains code to generate various visualisations of the CCI SST dataset.  The majority are one-shot programs which will require editing and running in a development environment to generate visualisations.  The two main visualisations are of latitude-dependent sea-surface temperature, in equirectangular and polar projections, and are designed to be run standalone.

Latitude-dependent SST on an Equirectangular Projection
-------------------------------------------------------

This will generate the frames for an animation of SST + sea ice fraction using a latitude-dependent colour scale, using an equirectangular projection (EPSG:4326).

This is defined as the main program in the released JAR file.  Therefore running it is very straightforward:

```
java -jar cci-sst-visualisation.jar
```

Note that you may also want the `-Xmx` option to increase the maximum available memory for the Java Virtual Machine.  It is recommended to make at least 4GB of memory available:

```
java -Xmx4G -jar cci-sst-visualisation.jar
```

To configure how this behaves, a file named `sst_render.properties` should be present in the directory from which the command is being run.  This contains all required information for locating the data, and setting the available options for the output.  An fully-commented example file can be found [here](src/main/resources/sst_render.properties), and is also available to download alongside the release.


Latitude-dependent SST on a Polar Stereographic Projection
----------------------------------------------------------

This will generate the frames for an animation of SST + sea ice fraction using a latitude-dependent colour scale, using polar stereographic projections (EPSG:3408 and EPSG:3409).

The main program for this visualisation is defined in the class `uk.ac.rdg.resc.SSTRenderPolar`, and can hence be run from the JAR file using:

```
java -Xmx4G -cp cci-sst-visualisation.jar uk.ac.rdg.resc.SSTRenderPolar`
```

It behaves almost identically to the equirectangular version, using a file named `sst_render_polar.properties`, a fully-commented example of which can be found [here](src/main/resources/sst_render_polar.properties), or alongside the release download.

Author
------
[@guygriffiths](https://github.com/guygriffiths)