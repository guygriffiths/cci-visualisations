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

import javax.imageio.ImageIO;

import org.joda.time.DateTime;

import uk.ac.rdg.resc.edal.dataset.GriddedDataset;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
import uk.ac.rdg.resc.edal.graphics.style.RasterLayer;
import uk.ac.rdg.resc.edal.graphics.style.ScaleRange;
import uk.ac.rdg.resc.edal.graphics.style.SegmentColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.sld.SLDException;
import uk.ac.rdg.resc.edal.graphics.utils.PlottingDomainParams;
import uk.ac.rdg.resc.edal.graphics.utils.SimpleFeatureCatalogue;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.grid.RegularGridImpl;
import uk.ac.rdg.resc.edal.grid.TimeAxis;

/**
 * Program to illustrate various ways of visualising uncertainty
 *
 * @author Guy Griffiths
 */
public class SSTErrors {
    static final String SST_VAR = "analysed_sst";
    static final String SST_ERROR_VAR = "analysis_error";
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 960;

    public static void main(String[] args) throws IOException, EdalException, InterruptedException,
            SLDException, InstantiationException {
        String outputPath = "/home/guy/speedtest";

        CdmGridDatasetFactory df = new CdmGridDatasetFactory();
        GriddedDataset ds = (GriddedDataset) df.createDataset("cci",
                "/home/guy/Data/cci-sst/2010/01/01/20100101120000-ESACCI-L4_GHRSST-SSTdepth-OSTIA-GLOB_LT-v02.0-fv01.0.nc");
        final SimpleFeatureCatalogue<GriddedDataset> catalogue = new SimpleFeatureCatalogue<>(ds,
                false);

        TimeAxis timeAxis = catalogue.getDataset().getVariableMetadata(SST_VAR).getTemporalDomain();

        MapImage compositeImage = new MapImage();

        RegularGrid imageGrid = new RegularGridImpl(BoundingBoxImpl.global(), WIDTH, HEIGHT);

        RasterLayer sstLayer = new RasterLayer(SST_VAR, new SegmentColourScheme(
                new ScaleRange(270f, 305f, false), null, null, new Color(0, true), "default", 250));

        /*
         * Shaded
         */
//        RasterLayer errorLayer = new RasterLayer(SST_ERROR_VAR, new SegmentColourScheme(new ColourScale(
//                0f, 2f, false), null, null, new Color(0, true), "#00000000,#ff000000", 250));

        /*
         * Stippled
         */
//        SLDRange range = new SLDRange(0f, 2f, Spacing.LINEAR);
//        DensityMap function = new SegmentDensityMap(10, range, 0f, 1f, 0f, 1f, 0f);
//        StippleLayer errorLayer = new StippleLayer(SST_ERROR_VAR, function);

        /*
         * Contoured
         */
//        ContourLayer errorLayer = new ContourLayer(SST_ERROR_VAR, new ColourScale(0f, 2f, false),
//                true, 30, Color.black, 1, ContourLineStyle.SOLID, true);

        compositeImage.getLayers().add(sstLayer);
//        compositeImage.getLayers().add(errorLayer);

        BufferedImage background = ImageIO.read(SSTErrors.class.getResource("/bluemarble_bg.png"));

        DateTime time = timeAxis.getCoordinateValue(0);
        PlottingDomainParams params = new PlottingDomainParams(imageGrid.getXSize(),
                imageGrid.getYSize(), imageGrid.getBoundingBox(), null, null, null, null, time);

        /*
         * Bivariate
         */
//        ColourScheme2D colourScheme = new ThresholdColourScheme2D(Arrays.asList(280f, 290f, 300f),
//                Arrays.asList(0.25f, 0.5f, 0.75f, 1.0f), Arrays.asList(new Color(0.4f, 0, 0),
//                        new Color(0.6f, 0, 0), new Color(0.8f, 0, 0), new Color(1.0f, 0, 0),
//                        new Color(0.4f, .1f, 0), new Color(0.6f, .1f, 0), new Color(0.8f, .1f, 0),
//                        new Color(1.0f, .1f, 0), new Color(0.4f, .2f, 0), new Color(0.6f, .2f, 0),
//                        new Color(0.8f, .2f, 0), new Color(1.0f, .2f, 0), new Color(0.4f, .3f, 0),
//                        new Color(0.6f, .3f, 0), new Color(0.8f, .3f, 0), new Color(1.0f, .3f, 0),
//                        new Color(0.4f, .4f, 0), new Color(0.6f, .4f, 0), new Color(0.8f, .4f, 0),
//                        new Color(1.0f, .4f, 0)), new Color(0, true)
//
//        );
//        Raster2DLayer raster2dLayer = new Raster2DLayer(SST_VAR, SST_ERROR_VAR, colourScheme);
//        compositeImage.getLayers().add(raster2dLayer);

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        BufferedImage sst = compositeImage.drawImage(params, catalogue);
        image.createGraphics().drawImage(background, 0, 0, WIDTH, HEIGHT, null);
        image.createGraphics().drawImage(sst, 0, 0, WIDTH, HEIGHT, null);
        ImageIO.write(image, "png", new File(outputPath + "/glyph.png"));
    }
}
