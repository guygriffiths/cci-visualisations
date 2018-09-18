/*******************************************************************************
 * Copyright (c) 2014 The University of Reading
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
import java.io.IOException;
import java.text.DecimalFormat;

import javax.imageio.ImageIO;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

import uk.ac.rdg.resc.edal.dataset.GriddedDataset;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.MapFeature;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
import uk.ac.rdg.resc.edal.graphics.style.RasterLayer;
import uk.ac.rdg.resc.edal.graphics.style.ScaleRange;
import uk.ac.rdg.resc.edal.graphics.style.SegmentColourScheme;
import uk.ac.rdg.resc.edal.graphics.utils.FeatureCatalogue.FeaturesAndMemberName;
import uk.ac.rdg.resc.edal.graphics.utils.PlottingDomainParams;
import uk.ac.rdg.resc.edal.graphics.utils.SimpleFeatureCatalogue;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.grid.RegularGridImpl;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.util.Array2D;

/**
 * Program to generate images which visualise wind, hurricane labels, and SST
 * anomalies
 *
 * @author Guy Griffiths
 */
public class SSTTempWaves {
    static final String SST_VAR = "analysed_sst";
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 960;

    public static void main(String[] args) throws IOException, EdalException {
        BufferedImage background = ImageIO
                .read(SSTTempWaves.class.getResource("/bluemarble_bg.png"));

        String outputPath = "/home/guy/sst-flotsam";
        System.out.println("About to read DS");
        CdmGridDatasetFactory df = new CdmGridDatasetFactory();
        GriddedDataset ds = (GriddedDataset) df.createDataset("cci",
                "/home/guy/Data/cci-sst/**/**/**/*.nc");
        final SimpleFeatureCatalogue<GriddedDataset> cciSst = new SimpleFeatureCatalogue<>(ds,
                false);

        System.out.println("About to read time axis");
        TimeAxis timeAxis = cciSst.getDataset().getVariableMetadata(SST_VAR).getTemporalDomain();

        MapImage compositeImage = new MapImage();
//        RegularGrid imageGrid = new RegularGridImpl(new BoundingBoxImpl(-110, -7.5, -5, 45,
//                DefaultGeographicCRS.WGS84), WIDTH, HEIGHT);
        RegularGrid imageGrid = new RegularGridImpl(BoundingBoxImpl.global(), WIDTH, HEIGHT);

        int bgWidth = background.getWidth();
        int bgHeight = background.getHeight();
        double imageCoordWidth = imageGrid.getBoundingBox().getWidth();
        double imageCoordHeight = imageGrid.getBoundingBox().getHeight();
        int bgSubImageOffsetX = (int) ((180.0 + imageGrid.getBoundingBox().getMinX())
                * (bgWidth / 360.0));
        int bgSubImageOffsetY = bgHeight
                - (int) ((90.0 + imageGrid.getBoundingBox().getMaxY()) * (bgHeight / 180.0));
        int bgSubImageWidth = (int) (bgWidth * imageCoordWidth / 360.0);
        int bgSubImageHeight = (int) (bgHeight * imageCoordHeight / 180.0);

        BufferedImage backgroundSub = background.getSubimage(bgSubImageOffsetX, bgSubImageOffsetY,
                bgSubImageWidth, bgSubImageHeight);

        RasterLayer sstDiffLayer = new RasterLayer(SST_VAR, new SegmentColourScheme(
                new ScaleRange(270f, 305f, false), null, null, new Color(0, true), "default", 250));
        compositeImage.getLayers().add(sstDiffLayer);

        DecimalFormat frameNoFormat = new DecimalFormat("0000");

        /*
         * Simple format for the date.
         */
        DateTimeFormatter dateFormatter = (new DateTimeFormatterBuilder()).appendDayOfMonth(2)
                .appendLiteral("-").appendMonthOfYear(2).appendLiteral("-").appendYear(4, 4)
                .toFormatter();
        Font font = null;

        int startTimeIndex = 0;
        int endTimeIndex = timeAxis.size() - 1;
//        int startTimeIndex = GISUtils.getIndexOfClosestTimeTo(new DateTime(1993, 1, 1, 0, 0),
//                timeAxis);
//        int endTimeIndex = GISUtils.getIndexOfClosestTimeTo(new DateTime(1995, 12, 31, 0, 0),
//                timeAxis);
        int frameNo = 0;
        MaxContourExtentPlotter particlePlotter = new MaxContourExtentPlotter(imageGrid,
                new double[] { 275, 280, 285, 290, 295 }, Color.white);

        for (int i = startTimeIndex; i < endTimeIndex; i++) {
            DateTime time = timeAxis.getCoordinateValue(i);
            System.out.println("Generating frame for " + time);

            PlottingDomainParams params = new PlottingDomainParams(imageGrid.getXSize(),
                    imageGrid.getYSize(), imageGrid.getBoundingBox(), null, null, null, null, time);

            FeaturesAndMemberName sstFeatures = cciSst.getFeaturesForLayer(SST_VAR, params);
            MapFeature sstMapFeature = (MapFeature) sstFeatures.getFeatures().iterator().next();
            Array2D<Number> sstVals = sstMapFeature.getValues(SST_VAR);

            particlePlotter.evolve(sstVals);

            BufferedImage frame = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            BufferedImage sst = compositeImage.drawImage(params, cciSst);

            BufferedImage flotsam = particlePlotter.plot();
            Graphics2D g = frame.createGraphics();

            if (font == null) {
                /*
                 * Calculate a font which should take up at most
                 * targetFontHeight vertically (but will be at least font size
                 * 6)
                 */
                int fontSize = 6;
                font = new Font(Font.MONOSPACED, Font.PLAIN, fontSize);
                int fontHeight = 0;
                while (fontHeight < HEIGHT / 15) {
                    font = new Font(Font.MONOSPACED, Font.PLAIN, fontSize++);
                    fontHeight = g.getFontMetrics(font).getHeight();
                }
                font = new Font(Font.MONOSPACED, Font.PLAIN, fontSize - 1);
            }
            g.setFont(font);

            /*
             * Draw background with lighter layer on top
             */
            g.drawImage(backgroundSub, 0, 0, WIDTH, HEIGHT, null);
//            g.setColor(new Color(1.0f, 1.0f, 1.0f, 0.5f));
//            g.fillRect(0, 0, WIDTH, HEIGHT);

            /*
             * Draw date
             */
            g.setColor(Color.black);
            final int targetFontHeight = HEIGHT / 15;
            /*
             * Calculate a font which should take up at most targetFontHeight
             * vertically (but will be at least font size 6)
             */
            int fontSize = 6;
            font = new Font(Font.MONOSPACED, Font.PLAIN, fontSize);
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
            g.drawString(dateFormatter.print(time), (int) (WIDTH * 0.4), HEIGHT - 10);

            /*
             * Draw data layers
             */
            g.drawImage(sst, 0, 0, null);
            g.drawImage(flotsam, 0, 0, null);

            ImageIO.write(frame, "png",
                    new File(outputPath + "/frame-" + frameNoFormat.format(frameNo++) + ".png"));

        }
        System.out.println("Finished writing frames.  Now run:\nffmpeg -r 25 -i '" + outputPath
                + "/frame-%04d.png' -crf 18 -c:v libx264 -pix_fmt yuv420p output.mp4");
    }
}
