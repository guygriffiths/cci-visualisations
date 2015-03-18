/*******************************************************************************
 * Copyright (c) 2015 The University of Reading
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University of Reading, nor the names of the
 *    authors or contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

package uk.ac.rdg.resc.cci;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

import uk.ac.rdg.resc.edal.exceptions.BadTimeFormatException;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
import uk.ac.rdg.resc.edal.graphics.style.RasterLayer;
import uk.ac.rdg.resc.edal.graphics.style.SegmentColourScheme;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.grid.RegularGridImpl;
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;
import uk.ac.rdg.resc.edal.util.TimeUtils;

/**
 * Contains a main method to read a simple properties file and generate a series
 * of global latitude-averaged SST images, ready to be converted into an
 * animation
 */
public class SSTRender {
    public static void main(String[] args) throws IOException, EdalException {
        /*
         * Load a properties file to determine what to plot.
         */
        Properties properties = new Properties();
        File propertiesFile = new File("sst_render.properties");
        if (propertiesFile.exists()) {
            properties.load(new FileReader(propertiesFile));
        } else {
            properties.load(SSTRender.class.getResourceAsStream("/sst_render.properties"));
        }
        String outputPath = properties.getProperty("outputPath");
        String dataPath = properties.getProperty("dataPath");

        if (outputPath == null || dataPath == null) {
            System.out
                    .println("You must provide at least the output path and the path to the SST data");
            System.exit(1);
        }

        String widthStr = properties.getProperty("imageWidth");
        String heightStr = properties.getProperty("imageHeight");
        String palette = properties.getProperty("palette", "default");
        String includeLegendStr = properties.getProperty("includeLegend");
        String legendPath = properties.getProperty("legendPath");
        String legendWidthStr = properties.getProperty("legendWidth");
        String includeDateStr = properties.getProperty("includeDate");

        String startStr = properties.getProperty("startData");
        String endStr = properties.getProperty("endData");

        String sstVar = properties.getProperty("sstVar", "analysed_sst");
        String iceVar = properties.getProperty("iceVar", "sea_ice_fraction");

        String yearsStr = properties.getProperty("yearsInAverage", "");
        String monthsStr = properties.getProperty("monthsInAverage", "");
        String daysStr = properties.getProperty("daysInAverage", "");

        /*
         * Determine the size of the image to plot
         */
        int width = 1920;
        int height = 960;
        if (widthStr != null) {
            try {
                width = Integer.parseInt(widthStr);
            } catch (NumberFormatException e) {
                /* Ignore - use default value if property isn't an integer */
            }
        }
        if (heightStr != null) {
            try {
                height = Integer.parseInt(heightStr);
            } catch (NumberFormatException e) {
                /* Ignore - use default value if property isn't an integer */
            }
        }

        RegularGrid imageGrid = new RegularGridImpl(-180, -90, 180, 90, DefaultGeographicCRS.WGS84,
                width, height);

        /*
         * Do we want a legend on the frames?
         */
        boolean includeLegend = true;
        if (includeLegendStr != null) {
            includeLegend = Boolean.parseBoolean(includeLegendStr);
        }

        /*
         * How wide should the legend be?
         */
        int legendWidth = (int) (0.4 * height);
        if (legendWidthStr != null) {
            try {
                legendWidth = Integer.parseInt(legendWidthStr);
            } catch (NumberFormatException e) {
                /* Ignore - use default value if property isn't an integer */
            }
        }

        /*
         * Do we want the date written on each frame?
         */
        boolean includeDate = true;
        if (includeDateStr != null) {
            includeDate = Boolean.parseBoolean(includeDateStr);
        }

        /*
         * Create the image generator object, using the image size as the
         * sampling dimensions
         */
        LatitudeDependentSST latitudeDependentSST = new LatitudeDependentSST(dataPath, sstVar,
                imageGrid);

        /*
         * Using the time axis of the dataset, select the indices we want to
         * generate images to/from
         */
        int firstFrame = 0;
        int lastFrame = latitudeDependentSST.getTimeAxis().size() - 1;
        if (startStr != null) {
            try {
                firstFrame = Integer.parseInt(startStr);
            } catch (NumberFormatException nfe) {
                /*
                 * Not an integer. Maybe a time string
                 */
                try {
                    DateTime startTime = TimeUtils.iso8601ToDateTime(startStr,
                            ISOChronology.getInstance());
                    firstFrame = GISUtils.getIndexOfClosestTimeTo(startTime,
                            latitudeDependentSST.getTimeAxis());
                } catch (BadTimeFormatException btfe) {
                    /*
                     * Not a time string. Use the default
                     */
                }
            }
        }
        if (endStr != null) {
            try {
                lastFrame = Integer.parseInt(endStr);
            } catch (NumberFormatException nfe) {
                /*
                 * Not an integer. Maybe a time string
                 */
                try {
                    DateTime endTime = TimeUtils.iso8601ToDateTime(endStr,
                            ISOChronology.getInstance());
                    lastFrame = GISUtils.getIndexOfClosestTimeTo(endTime,
                            latitudeDependentSST.getTimeAxis());
                } catch (BadTimeFormatException btfe) {
                    /*
                     * Not a time string. Use the default
                     */
                }
            }
        }

        String[] yearsStrs = yearsStr.split(",");
        String[] monthsStrs = monthsStr.split(",");
        String[] daysStrs = daysStr.split(",");

        List<Integer> averageYears = new ArrayList<>();
        List<Integer> averageMonths = new ArrayList<>();
        List<Integer> averageDays = new ArrayList<>();

        for (String yearStr : yearsStrs) {
            try {
                averageYears.add(Integer.parseInt(yearStr));
            } catch (NumberFormatException e) {
                /*
                 * Ignore unparseable years
                 */
            }
        }

        for (String monthStr : monthsStrs) {
            try {
                averageMonths.add(Integer.parseInt(monthStr));
            } catch (NumberFormatException e) {
                /*
                 * Ignore unparseable months
                 */
            }
        }

        for (String dayStr : daysStrs) {
            try {
                averageDays.add(Integer.parseInt(dayStr));
            } catch (NumberFormatException e) {
                /*
                 * Ignore unparseable days
                 */
            }
        }

        /*
         * Pick a subset of the data to use in the latitude-averaging
         */
        List<DateTime> useInAverage = new ArrayList<>();
        for (DateTime time : latitudeDependentSST.getTimeAxis().getCoordinateValues()) {
            if ((averageYears.size() == 0 || averageYears.contains(time.getYear()))
                    && (averageMonths.size() == 0 || averageMonths.contains(time.getMonthOfYear()))
                    && (averageDays.size() == 0 || averageDays.contains(time.getDayOfMonth()))) {
                useInAverage.add(time);
            }
        }

        /*
         * Read the background blue marble image
         */
        BufferedImage background = ImageIO.read(SSTRender.class.getResource("/bluemarble_bg.png"));

        /*
         * Generate the 2D latitude-dependent raster image layer (just
         * calculates the average and generates the layer, doesn't plot the
         * data)
         */
        latitudeDependentSST.generateSSTLayer(useInAverage, 0.7, height / 20, palette);

        /*
         * Create a new composite image with an SST layer and an ice layer
         */
        MapImage compositeImage = new MapImage();
        compositeImage.getLayers().add(latitudeDependentSST.getSSTLayer());
        ColourScheme iceColourScheme = new SegmentColourScheme(new ColourScale(0f, 1.0f, false),
                new Color(0, true), null, new Color(0, true), "#00ffffff,#ffffff", 100);
        RasterLayer iceLayer = new RasterLayer(iceVar, iceColourScheme);
        compositeImage.getLayers().add(iceLayer);

        /*
         * Generate the legend
         */
        BufferedImage legend = latitudeDependentSST.drawLegend(legendWidth, height);
        if (legendPath != null) {
            /*
             * Write legend to disk if desired
             */
            try {
                ImageIO.write(legend, "png", new File(legendPath));
            } catch (IOException e) {
                System.out
                        .println("Problem writing legend to file.  Stack trace follows.  Not a critical error, continuing...");
                e.printStackTrace();
            }
        }

        /*
         * Simple format for the date.
         */
        DateTimeFormatter dateFormatter = (new DateTimeFormatterBuilder()).appendDayOfMonth(2)
                .appendLiteral("-").appendMonthOfYear(2).appendLiteral("-").appendYear(4, 4)
                .toFormatter();

        /*
         * Some constants
         */
        final int gap = width / 100;
        final int targetFontHeight = height / 15;
        final int totalWidth = includeLegend ? width + legend.getWidth() + gap : width;
        /*
         * The frame number. Frames are numbered according to the position in
         * the dataset.
         */
        int frameNo = firstFrame;
        DecimalFormat frameNoFormat = new DecimalFormat("00000");
        
        /*
         * Loop over all frames to generate images
         */
        for (DateTime time : latitudeDependentSST.getTimeAxis().getCoordinateValues()
                .subList(firstFrame, lastFrame + 1)) {
            System.out.println("Generating frame for time " + time);
            /*
             * Create an image to render the frame with space for the legend and
             * a gap
             */
            BufferedImage frame = new BufferedImage(totalWidth, height, BufferedImage.TYPE_INT_ARGB);

            /*
             * Create parameters to plot with
             */
            imageGrid = new RegularGridImpl(-9036842, -9036842, 9036842, 9036842,
                    GISUtils.getCrs("EPSG:3408"), width, height);
            PlottingDomainParams params = new PlottingDomainParams(imageGrid, null, null, null,
                    null, time);
            /*
             * Render the image with SST and ice layers
             */
            BufferedImage sstImage = compositeImage.drawImage(params, latitudeDependentSST);

            Graphics2D g = frame.createGraphics();

            /*
             * Fill background black
             */
            g.setColor(Color.black);
            g.fillRect(0, 0, frame.getWidth(), frame.getHeight());

            /*
             * Draw the blue marble background
             */
            g.drawImage(background, 0, 0, width, height, null);
            /*
             * Draw the SST / ice layer
             */
            g.drawImage(sstImage, 0, 0, width, height, null);

            if (includeLegend) {
                /*
                 * Draw the legend
                 */
                g.drawImage(legend, width + gap, 0, legend.getWidth(), legend.getHeight(), null);
            }

            if (includeDate) {
                /*
                 * Calculate a font which should take up at most
                 * targetFontHeight vertically (but will be at least font size
                 * 6)
                 */
                int fontSize = 6;
                Font font = new Font(Font.MONOSPACED, Font.PLAIN, fontSize);
                int fontHeight = 0;
                while (fontHeight < targetFontHeight) {
                    font = new Font(Font.MONOSPACED, Font.PLAIN, fontSize++);
                    fontHeight = g.getFontMetrics(font).getHeight();
                }
                font = new Font(Font.MONOSPACED, Font.PLAIN, fontSize - 1);
                g.setFont(font);

                /*
                 * Draw the date/time on the map, somewhere in the antarctic
                 * (position found empirically, but works well for a
                 * targetFontHeight of height/15)
                 */
                g.drawString(dateFormatter.print(time), (int) (width * 0.4), height - 10);
            }

            /*
             * Write the image to disk
             */
            ImageIO.write(frame, "png",
                    new File(outputPath + "/frame-" + frameNoFormat.format(frameNo++) + ".png"));
        }
        /*
         * Add a helpful message of how to convert frames to an MP4 video.
         */
        System.out.println("Finished writing frames.  Now run:\nffmpeg -r 25 -i '" + outputPath
                + "/frame-%05d.png' -c:v libx264 -pix_fmt yuv420p output.mp4");
    }

}
