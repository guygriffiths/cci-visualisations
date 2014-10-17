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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.edal.domain.TemporalDomain;
import uk.ac.rdg.resc.edal.exceptions.DataReadingException;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.MapFeature;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.ArrowLayer.ArrowStyle;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme2D;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
import uk.ac.rdg.resc.edal.graphics.style.MappedSegmentColorScheme2D;
import uk.ac.rdg.resc.edal.graphics.style.Raster2DLayer;
import uk.ac.rdg.resc.edal.graphics.style.RasterLayer;
import uk.ac.rdg.resc.edal.graphics.style.SegmentColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.SizedArrowLayer;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.util.Array2D;
import uk.ac.rdg.resc.edal.util.Extents;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;

public class SSTWinds {
    static final String SST_VAR = "analysed_sst";
    private static final String WIND_DIR_VAR = "u10v10-dir";
    private static final String WIND_MAG_VAR = "u10v10-mag";
    private static final int WIDTH = 2160;
    private static final int HEIGHT = 1080;

    public static void main(String[] args) throws IOException, EdalException {
        String outputPath = "/home/guy/sst-wind";

//        BufferedImage background = ImageIO.read(LatitudeDependentSST.class
//                .getResource("/bluemarble_bg.png"));

        final SimpleFeatureCatalogue cciSst = new SimpleFeatureCatalogue("cci",
                "/home/guy/Data/cci-sst/2010/**/**/*.nc", false);
        final SimpleFeatureCatalogue wind = new SimpleFeatureCatalogue("wind",
                "/home/guy/Data/era_interim/2010/era.ncml", false);

        System.out.println(cciSst.getDataset().getVariableIds());
        System.out.println(wind.getDataset().getVariableIds());

        TimeAxis timeAxis = (TimeAxis) wind.getDataset().getVariableMetadata(WIND_DIR_VAR)
                .getTemporalDomain();
//        for(int i=0;i<timeAxis.size();i++) {
//            System.out.println(i+", "+timeAxis.getCoordinateValue(i));
//        }

        MapImage compositeImage = new MapImage();
        RasterLayer sstLayer = new RasterLayer(SST_VAR, new SegmentColourScheme(new ColourScale(
                298f, 305f, false), null, null, new Color(0, true), "default", 250));
        SizedArrowLayer windLayer = new SizedArrowLayer(WIND_DIR_VAR, WIND_MAG_VAR, 0, 15,
                Extents.newExtent(0f, 20f), Color.black, ArrowStyle.TRI_ARROW);
//        Raster2DLayer raster2dLayer = latitudeDependentSST.calculateRaster2DLayer(useInAverage);
        compositeImage.getLayers().add(sstLayer);
        compositeImage.getLayers().add(windLayer);

        FeatureCatalogue featureCatalogue = new FeatureCatalogue() {
            @Override
            public FeaturesAndMemberName getFeaturesForLayer(String id, PlottingDomainParams params)
                    throws EdalException {
                if (SST_VAR.equals(id)) {
                    return cciSst.getFeaturesForLayer(id, params);
                } else if (WIND_DIR_VAR.equals(id)) {
                    return wind.getFeaturesForLayer(id, params);
                } else if (WIND_MAG_VAR.equals(id)) {
                    return wind.getFeaturesForLayer(id, params);
                }
                return null;
            }
        };

        int frameNo = 0;
        DecimalFormat frameNoFormat = new DecimalFormat("0000");
        for (int i = 838; i < 1336; i++) {
            DateTime time = timeAxis.getCoordinateValue(i);
//            PlottingDomainParams params = new PlottingDomainParams(WIDTH, HEIGHT,
//                    new BoundingBoxImpl(-130, 5, -85, 27.5, DefaultGeographicCRS.WGS84), null,
//                    null, null, null, time);
            PlottingDomainParams params = new PlottingDomainParams(WIDTH, HEIGHT,
                    new BoundingBoxImpl(-110, -7.5, -5, 45, DefaultGeographicCRS.WGS84), null, null,
                    null, null, time);
            System.out.println("Generating frame for " + time);

            BufferedImage frame = compositeImage.drawImage(params, featureCatalogue);
            ImageIO.write(frame, "png",
                    new File(outputPath + "/frame-" + frameNoFormat.format(frameNo++) + ".png"));
        }

//        DateTimeFormatter dateFormatter = (new DateTimeFormatterBuilder()).appendDayOfMonth(2)
//                .appendLiteral("-").appendMonthOfYear(2).appendLiteral("-").appendYear(4, 4)
//                .toFormatter();

//        System.out.println("Finished writing frames.  Now run:\nffmpeg -r 25 -i '" + outputPath
//                + "/frame-%04d.png' -c:v libx264 -pix_fmt yuv420p output.mp4");
    }

}
