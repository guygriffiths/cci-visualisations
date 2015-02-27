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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.MapFeature;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
import uk.ac.rdg.resc.edal.graphics.style.RasterLayer;
import uk.ac.rdg.resc.edal.graphics.style.SegmentColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue.FeaturesAndMemberName;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.grid.RegularGridImpl;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.util.Array2D;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;

public class SSTWinds {
    static final String SST_VAR = "analysed_sst";
    private static final String WIND_X_VAR = "u10";
    private static final String WIND_Y_VAR = "v10";
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 960;

    public static void main(String[] args) throws IOException, EdalException {
//        String outputPath = "/home/guy/sst-wind";
        String outputPath = "/home/guy/windtest";

        BufferedImage background = ImageIO.read(SSTWinds.class.getResource("/bluemarble_bg.png"));

        final SimpleFeatureCatalogue cciSst = new SimpleFeatureCatalogue("cci",
                "/home/guy/Data/cci-sst/2010/**/**/*.nc", false);
        final SimpleFeatureCatalogue wind = new SimpleFeatureCatalogue("wind",
                "/home/guy/Data/era_interim/2010/era.ncml", false);

        TimeAxis timeAxis = (TimeAxis) wind.getDataset().getVariableMetadata(WIND_X_VAR)
                .getTemporalDomain();
//        for(int i=0;i<timeAxis.size();i++) {
//            System.out.println(i+", "+timeAxis.getCoordinateValue(i));
//        }

        MapImage compositeImage = new MapImage();
//        SizedArrowLayer windLayer = new SizedArrowLayer(WIND_DIR_VAR, WIND_MAG_VAR, 0, 15,
//                Extents.newExtent(0f, 20f), Color.black, ArrowStyle.THIN_ARROW);
        RegularGrid imageGrid = new RegularGridImpl(new BoundingBoxImpl(-110, -7.5, -5, 45,
                DefaultGeographicCRS.WGS84), WIDTH, HEIGHT);

        int bgWidth = background.getWidth();
        int bgHeight = background.getHeight();
        double imageCoordWidth = imageGrid.getBoundingBox().getWidth();
        double imageCoordHeight = imageGrid.getBoundingBox().getHeight();
        int bgSubImageOffsetX = (int) ((180.0 + imageGrid.getBoundingBox().getMinX()) * (bgWidth / 360.0));
        int bgSubImageOffsetY = bgHeight
                - (int) ((90.0 + imageGrid.getBoundingBox().getMaxY()) * (bgHeight / 180.0));
        int bgSubImageWidth = (int) (bgWidth * imageCoordWidth / 360.0);
        int bgSubImageHeight = (int) (bgHeight * imageCoordHeight / 180.0);

        BufferedImage backgroundSub = background.getSubimage(bgSubImageOffsetX, bgSubImageOffsetY,
                bgSubImageWidth, bgSubImageHeight);

//        final LatitudeDependentSST latitudeDependentSST = new LatitudeDependentSST(
//                "/home/guy/Data/cci-sst/2010/**/**/*.nc", SST_VAR, imageGrid);
        List<DateTime> useInAverage = new ArrayList<>();
        for (DateTime time : ((TimeAxis) cciSst.getDataset().getVariableMetadata(SST_VAR)
                .getTemporalDomain()).getCoordinateValues()) {
            if (time.getYear() == 2010 && time.getDayOfMonth() == 1) {
                useInAverage.add(time);
            }
        }
        //        latitudeDependentSST.generateSSTLayer(useInAverage, 0.5, HEIGHT / 20, "default");
//        compositeImage.getLayers().add(latitudeDependentSST.getSSTLayer());
//        compositeImage.getLayers().add(windLayer);
        RasterLayer sstDiffLayer = new RasterLayer(SST_VAR, new SegmentColourScheme(
                new ColourScale(-3f, 3f, false), null, null, new Color(0, true), "div-BuRd2", 250));
        compositeImage.getLayers().add(sstDiffLayer);

        FeatureCatalogue featureCatalogue = new FeatureCatalogue() {
            @Override
            public FeaturesAndMemberName getFeaturesForLayer(String id, PlottingDomainParams params)
                    throws EdalException {
                if (SST_VAR.equals(id)) {
                    return cciSst.getFeaturesForLayer(id, params);
//                } else if (LatitudeDependentSST.LATITUDE.equals(id)) {
//                    return latitudeDependentSST.getFeaturesForLayer(id, params);
                } else if (WIND_X_VAR.equals(id)) {
                    return wind.getFeaturesForLayer(id, params);
                } else if (WIND_Y_VAR.equals(id)) {
                    return wind.getFeaturesForLayer(id, params);
                }
                return null;
            }
        };

        RunningAverageDiffFeatureCatalogue diffFc = new RunningAverageDiffFeatureCatalogue(
                SST_VAR, 10, featureCatalogue, (TimeAxis) cciSst.getDataset()
                        .getVariableMetadata(SST_VAR).getTemporalDomain());

        int frameNo = 0;
        DecimalFormat frameNoFormat = new DecimalFormat("0000");

        EvolvingWindPlotter windPlotter = new EvolvingWindPlotter(imageGrid, 0.05, 24, new Color(
                0f, 0f, 0f, 0.3f), 20);

        for (int i = 800; i < timeAxis.size(); i++) {
            DateTime time = timeAxis.getCoordinateValue(i);
            PlottingDomainParams params = new PlottingDomainParams(imageGrid, null, null, null,
                    null, time);

            FeaturesAndMemberName xF = wind.getFeaturesForLayer(WIND_X_VAR, params);
            FeaturesAndMemberName yF = wind.getFeaturesForLayer(WIND_Y_VAR, params);
            MapFeature xMapFeature = (MapFeature) xF.getFeatures().iterator().next();
            MapFeature yMapFeature = (MapFeature) yF.getFeatures().iterator().next();
            Array2D<Number> xCompVals = xMapFeature.getValues(WIND_X_VAR);
            Array2D<Number> yCompVals = yMapFeature.getValues(WIND_Y_VAR);

            System.out.println("Generating frame for " + time);

            windPlotter.evolve(xCompVals, yCompVals);
            BufferedImage frame = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
//            BufferedImage frame = compositeImage.drawImage(params, featureCatalogue);
            BufferedImage sst = compositeImage.drawImage(params, diffFc);
            BufferedImage winds = windPlotter.plot();
            frame.createGraphics().drawImage(backgroundSub, 0, 0, WIDTH, HEIGHT, null);
            frame.createGraphics().drawImage(sst, 0, 0, null);
            frame.createGraphics().drawImage(winds, 0, 0, null);
            ImageIO.write(frame, "png",
                    new File(outputPath + "/frame-" + frameNoFormat.format(frameNo++) + ".png"));
        }

//        DateTimeFormatter dateFormatter = (new DateTimeFormatterBuilder()).appendDayOfMonth(2)
//                .appendLiteral("-").appendMonthOfYear(2).appendLiteral("-").appendYear(4, 4)
//                .toFormatter();

        System.out.println("Finished writing frames.  Now run:\nffmpeg -r 25 -i '" + outputPath
                + "/frame-%04d.png' -crf 18 -c:v libx264 -pix_fmt yuv420p output.mp4");
    }
}
