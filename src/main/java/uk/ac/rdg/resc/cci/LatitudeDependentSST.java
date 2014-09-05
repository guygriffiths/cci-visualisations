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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.domain.MapDomainImpl;
import uk.ac.rdg.resc.edal.exceptions.DataReadingException;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.MapFeature;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme2D;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
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

public class LatitudeDependentSST implements FeatureCatalogue {
    private static final String SST_VAR = "analysed_sst";
    private static final String ICE_VAR = "sea_ice_fraction";
    private static final String LATITUDE_VAR = "latitude";
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 500;

    private TimeAxis timeAxis;
    private RegularGrid imageGrid;
    private RegularAxis latitudeAxis;
    private Map<DateTime, MapFeature> mapFeatures = null;
    private Dataset dataset;
    private int width;
    private int height;

    public LatitudeDependentSST(String location, int width, int height) throws IOException, EdalException {
        this.width = width;
        this.height = height;
        
        CdmGridDatasetFactory datasetFactory = new CdmGridDatasetFactory();
        dataset = datasetFactory.createDataset("cci-sst", location);

        GridVariableMetadata sstMetadata = (GridVariableMetadata) dataset
                .getVariableMetadata(SST_VAR);

        timeAxis = sstMetadata.getTemporalDomain();

        imageGrid = new RegularGridImpl(-180, -90, 180, 90, DefaultGeographicCRS.WGS84, width,
                height);
        latitudeAxis = imageGrid.getYAxis();
    }

    private void cacheMapFeatures() throws DataReadingException {
        mapFeatures = new HashMap<>();
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
    }

    public Raster2DLayer calculateRaster2DLayer() throws DataReadingException {
        if (mapFeatures == null) {
            cacheMapFeatures();
        }

        float maxRange = 0.0f;
        Map<Double, Float> meanValuesMap = new HashMap<>();
        for (int y = 0; y < latitudeAxis.size(); y++) {
            Double latVal = latitudeAxis.getCoordinateValue(y);
            Float minSst = Float.MAX_VALUE;
            Float maxSst = -Float.MAX_VALUE;
            float mean = 0.0f;
            int points = 0;
            for (DateTime time : timeAxis.getCoordinateValues()) {
                MapFeature mapFeature = mapFeatures.get(time);

                Array2D<Number> sstValues = mapFeature.getValues(SST_VAR);
                for (int x = 0; x < sstValues.getXSize(); x++) {
                    Number sstValue = sstValues.get(y, x);
                    if (sstValue != null) {
                        minSst = Math.min(minSst, sstValue.floatValue());
                        maxSst = Math.max(maxSst, sstValue.floatValue());
                        mean += sstValue.doubleValue();
                        points++;
                    }
                }
            }
            mean /= points;
            System.out.println("SST range at latitude " + latVal + " is " + minSst + " to "
                    + maxSst);
            if (minSst != Float.MAX_VALUE) {
                maxRange = Math.max(maxRange, maxSst - minSst);
                meanValuesMap.put(latVal, mean);
            }
        }

        Map<Number, SegmentColourScheme> colorSchemeMap = new HashMap<>();
        for (Entry<Double, Float> extentEntry : meanValuesMap.entrySet()) {
            SegmentColourScheme colourScheme = new SegmentColourScheme(new ColourScale(
                    extentEntry.getValue() - maxRange / 2, extentEntry.getValue() + maxRange / 2,
                    false), Color.black, Color.black, new Color(0, true), "default", 100);
            colorSchemeMap.put(extentEntry.getKey(), colourScheme);
        }

        ColourScheme2D colourScheme = new MappedSegmentColorScheme2D(colorSchemeMap, new Color(0,
                true));
        return new Raster2DLayer(LATITUDE_VAR, SST_VAR, colourScheme);
    }

    public static void main(String[] args) throws IOException, EdalException {
        BufferedImage background = ImageIO.read(LatitudeDependentSST.class.getResource("/bluemarble_bg.png"));
        
        LatitudeDependentSST latitudeDependentSST = new LatitudeDependentSST(
                "/home/guy/Data/cci-sst/20101201*.nc", WIDTH, HEIGHT);
        MapImage compositeImage = new MapImage();
        compositeImage.getLayers().add(latitudeDependentSST.calculateRaster2DLayer());

        List<BufferedImage> frames = new ArrayList<>();
        for (final DateTime time : latitudeDependentSST.timeAxis.getCoordinateValues()) {
            BufferedImage frame = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            
            PlottingDomainParams params = new PlottingDomainParams(latitudeDependentSST.imageGrid,
                    null, null, null, null, time);
            BufferedImage sstImage = compositeImage.drawImage(params, latitudeDependentSST);
            
            Graphics2D g = frame.createGraphics();
            g.drawImage(background, 0, 0, WIDTH, HEIGHT, null);
            g.drawImage(sstImage, 0, 0, WIDTH, HEIGHT, null);
            frames.add(frame);
        }

//        AviFormat avi = new AviFormat();
//        avi.writeImage(frames, new FileOutputStream("/home/guy/test.avi"), 10);
        ImageIO.write(frames.get(0), "png", new File("/home/guy/test2.png"));
    }

    @Override
    public FeaturesAndMemberName getFeaturesForLayer(String id, PlottingDomainParams params)
            throws EdalException {
        if (id.equals(SST_VAR)) {
            return new FeaturesAndMemberName(mapFeatures.get(params.getTargetT()), SST_VAR);
        } else if(id.equals(ICE_VAR)) {
            return new FeaturesAndMemberName(mapFeatures.get(params.getTargetT()), ICE_VAR);
        } else if(id.equals(LATITUDE_VAR)) {
            Map<String, Array2D<Number>> latitudeValuesMap = new HashMap<>();
            latitudeValuesMap.put(LATITUDE_VAR, new Array2D<Number>(height, width) {
                @Override
                public Number get(int... coords) {
                    return latitudeAxis.getCoordinateValue(coords[Y_IND]);
                }

                @Override
                public void set(Number value, int... coords) {
                    throw new UnsupportedOperationException();
                }
            });
            return new FeaturesAndMemberName(new MapFeature(LATITUDE_VAR, "", "", new MapDomainImpl(
                    imageGrid, null, null, params.getTargetT()), null, latitudeValuesMap), LATITUDE_VAR);
        } else {
            return null;
        }
    }
}
