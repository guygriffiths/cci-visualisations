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
import uk.ac.rdg.resc.edal.feature.Feature;
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

public class SSTSimple {
//    static final String SST_VAR = "analysed_sst";
    static final String SST_VAR = "u10";
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 960;

    public static void main(String[] args) throws IOException, EdalException, InterruptedException {
        String outputPath = "/home/guy/speedtest";

//        final SimpleFeatureCatalogue catalogue = new SimpleFeatureCatalogue("cci",
//                "/home/guy/Data/cci-sst/2010/**/**/*.nc", false);
//        final SimpleFeatureCatalogue catalogue = new SimpleFeatureCatalogue("cci",
//                "/home/guy/Data/era_interim/2010/era.ncml", false);
        final SimpleFeatureCatalogue catalogue = new SimpleFeatureCatalogue("cci",
                "/home/guy/Data/era_interim/2010/10U_2010.nc", false);
        
        TimeAxis timeAxis = (TimeAxis) catalogue.getDataset().getVariableMetadata(SST_VAR)
                .getTemporalDomain();

        MapImage compositeImage = new MapImage();

        RegularGrid imageGrid = new RegularGridImpl(new BoundingBoxImpl(-110, -7.5, -5, 45,
                DefaultGeographicCRS.WGS84), WIDTH, HEIGHT);

        RasterLayer sstLayer = new RasterLayer(SST_VAR, new SegmentColourScheme(
                new ColourScale(270f, 305f, false), null, null, new Color(0, true), "default", 250));
        compositeImage.getLayers().add(sstLayer);

        int frameNo = 0;
        DecimalFormat frameNoFormat = new DecimalFormat("0000");

        long t1 = System.currentTimeMillis();
        int n = 20;
        for (int i = 0; i < n; i++) {
            DateTime time = timeAxis.getCoordinateValue(i);
            PlottingDomainParams params = new PlottingDomainParams(imageGrid, null, null, null,
                    null, time);

            System.out.println("Generating frame for " + time);

            BufferedImage sst = compositeImage.drawImage(params, catalogue);
            ImageIO.write(sst, "png",
                    new File(outputPath + "/frame-" + frameNoFormat.format(frameNo++) + ".png"));
        }
        long t2 = System.currentTimeMillis();
        double timeS = ((t2 - t1) / 1000.0);
        double timeF = (t2 - t1) / (double) n;
        System.out.println(timeS + "s to generate " + n + " frames " + timeF + "ms/frame");
    }
}
