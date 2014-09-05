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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.domain.MapDomainImpl;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.MapFeature;
import uk.ac.rdg.resc.edal.graphics.formats.AviFormat;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme2D;
import uk.ac.rdg.resc.edal.graphics.style.MappedSegmentColorScheme2D;
import uk.ac.rdg.resc.edal.graphics.style.Raster2DLayer;
import uk.ac.rdg.resc.edal.graphics.style.SegmentColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue;
import uk.ac.rdg.resc.edal.grid.RegularAxis;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.grid.RegularGridImpl;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.metadata.GridVariableMetadata;
import uk.ac.rdg.resc.edal.util.Array2D;
import uk.ac.rdg.resc.edal.util.CollectionUtils;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;

public class LatitudeDependentSST {
    private static final String SST_VAR = "analysed_sst";
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 500;

    public static void main(String[] args) throws IOException, EdalException {
        CdmGridDatasetFactory datasetFactory = new CdmGridDatasetFactory();
        Dataset dataset = datasetFactory.createDataset("cci-sst",
                "/home/guy/Data/cci-sst/201012*.nc");

        GridVariableMetadata sstMetadata = (GridVariableMetadata) dataset
                .getVariableMetadata(SST_VAR);

        final TimeAxis timeAxis = sstMetadata.getTemporalDomain();

        final RegularGrid imageGrid = new RegularGridImpl(-180, -90, 180, 90, DefaultGeographicCRS.WGS84,
                WIDTH, HEIGHT);
        final RegularAxis latitudeAxis = imageGrid.getYAxis();

        final Map<DateTime, MapFeature> mapFeatures = new HashMap<>();
        for (DateTime time : timeAxis.getCoordinateValues()) {
            PlottingDomainParams params = new PlottingDomainParams(imageGrid, null, null, null,
                    null, time);
            List<? extends DiscreteFeature<?, ?>> extractedMapFeatures = dataset
                    .extractMapFeatures(CollectionUtils.setOf(SST_VAR), params);
            System.out.println("Extracted map for time " + time);
            assert (extractedMapFeatures.size() == 1);
            MapFeature mapFeature = (MapFeature) extractedMapFeatures.get(0);
            mapFeatures.put(time, mapFeature);
        }

        Map<Number, SegmentColourScheme> colorSchemeMap = new HashMap<>();
        for (int y = 0; y < latitudeAxis.size(); y++) {
            Double latVal = latitudeAxis.getCoordinateValue(y);
            Float minSst = Float.MAX_VALUE;
            Float maxSst = -Float.MAX_VALUE;
            for (DateTime time : timeAxis.getCoordinateValues()) {
                MapFeature mapFeature = mapFeatures.get(time);

                Array2D<Number> sstValues = mapFeature.getValues(SST_VAR);
                for (int x = 0; x < sstValues.getXSize(); x++) {
                    Number sstValue = sstValues.get(y, x);
                    if (sstValue != null) {
                        minSst = Math.min(minSst, sstValue.floatValue());
                        maxSst = Math.max(maxSst, sstValue.floatValue());
                    }
                }
            }
            System.out.println("SST range at latitude " + latVal + " is " + minSst + " to "
                    + maxSst);
            SegmentColourScheme colourScheme;
            if (minSst != Float.MAX_VALUE) {
                colourScheme = new SegmentColourScheme(new ColourScale(minSst, maxSst, false),
                        Color.black, Color.black, new Color(0, true), "default", 100);
            } else {
                colourScheme = new SegmentColourScheme(new ColourScale(0f, 100f, false),
                        Color.black, Color.black, new Color(0, true), "default", 100);
            }
            colorSchemeMap.put(latVal, colourScheme);
        }

        ColourScheme2D colourScheme = new MappedSegmentColorScheme2D(colorSchemeMap, new Color(0,
                true));
        Raster2DLayer latDependentSstLayer = new Raster2DLayer("latitude", "sst", colourScheme);

        List<BufferedImage> frames = new ArrayList<>();
        for(final DateTime time : timeAxis.getCoordinateValues()) {
            PlottingDomainParams params = new PlottingDomainParams(imageGrid, null, null, null, null,
                    time);
            BufferedImage image = latDependentSstLayer.drawImage(params, new FeatureCatalogue() {
                @Override
                public FeaturesAndMemberName getFeaturesForLayer(String id, PlottingDomainParams params)
                        throws EdalException {
                    if (id.equals("sst")) {
                        return new FeaturesAndMemberName(
                                mapFeatures.get(time), SST_VAR);
                    } else {
                        Map<String, Array2D<Number>> latitudeValuesMap = new HashMap<>();
                        latitudeValuesMap.put("latitude", new Array2D<Number>(HEIGHT, WIDTH) {
                            @Override
                            public Number get(int... coords) {
                                return latitudeAxis.getCoordinateValue(coords[Y_IND]);
                            }
    
                            @Override
                            public void set(Number value, int... coords) {
                                throw new UnsupportedOperationException();
                            }
                        });
                        return new FeaturesAndMemberName(
                                new MapFeature("latitude", "", "", new MapDomainImpl(imageGrid, null,
                                        null, time), null, latitudeValuesMap), "latitude");
                    }
                }
            });
            frames.add(image);
        }
        
        AviFormat avi = new AviFormat();
        avi.writeImage(frames, new FileOutputStream("/home/guy/test.avi"), 10);
    }
}
