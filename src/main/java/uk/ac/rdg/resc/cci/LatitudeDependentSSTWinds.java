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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.util.FactoryException;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.domain.MapDomainImpl;
import uk.ac.rdg.resc.edal.exceptions.DataReadingException;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.MapFeature;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme2D;
import uk.ac.rdg.resc.edal.graphics.style.ContourLayer;
import uk.ac.rdg.resc.edal.graphics.style.ArrowLayer.ArrowStyle;
import uk.ac.rdg.resc.edal.graphics.style.ContourLayer.ContourLineStyle;
import uk.ac.rdg.resc.edal.graphics.style.Drawable.NameAndRange;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
import uk.ac.rdg.resc.edal.graphics.style.MappedSegmentColorScheme2D;
import uk.ac.rdg.resc.edal.graphics.style.Raster2DLayer;
import uk.ac.rdg.resc.edal.graphics.style.RasterLayer;
import uk.ac.rdg.resc.edal.graphics.style.SegmentColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.SizedArrowLayer;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue;
import uk.ac.rdg.resc.edal.graphics.style.util.LegendDataGenerator;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue.FeaturesAndMemberName;
import uk.ac.rdg.resc.edal.grid.RegularAxis;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.grid.RegularGridImpl;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.metadata.GridVariableMetadata;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.Array2D;
import uk.ac.rdg.resc.edal.util.CollectionUtils;
import uk.ac.rdg.resc.edal.util.Extents;
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;

public class LatitudeDependentSSTWinds implements FeatureCatalogue {
    static final String SST_VAR = "analysed_sst";
    private static final String WIND_DIR_VAR = "u10v10-dir";
    private static final String WIND_MAG_VAR = "u10v10-mag";
    private static final String ICE_VAR = "sea_ice_fraction";
    private static final String LATITUDE_VAR = "latitude";
    private static final int WIDTH = 2160;
    private static final int HEIGHT = 1080;

    private TimeAxis timeAxis;
    private RegularGrid imageGrid;
    private RegularAxis latitudeAxis;
    private Map<CacheKey, MapFeature> mapFeatures = null;
    private Dataset dataset;
    private int width;
    private int height;

    private float[] means;
    private float maxRange;
    private SimpleFeatureCatalogue wind;

    public LatitudeDependentSSTWinds(String location, String windLocation, int width, int height,
            BoundingBox bbox) throws IOException, EdalException, NoSuchAuthorityCodeException,
            FactoryException {
        this.width = width;
        this.height = height;

        CdmGridDatasetFactory datasetFactory = new CdmGridDatasetFactory();
        dataset = datasetFactory.createDataset("cci-sst", location);

        GridVariableMetadata sstMetadata = (GridVariableMetadata) dataset
                .getVariableMetadata(SST_VAR);

        timeAxis = sstMetadata.getTemporalDomain();

        imageGrid = new RegularGridImpl(bbox, width, height);
        latitudeAxis = imageGrid.getYAxis();

        mapFeatures = new HashMap<>();

        wind = new SimpleFeatureCatalogue("wind", windLocation, false);
    }

    /**
     * Generates a Raster2DLayer with a {@link ColourScheme2D} where each
     * latitude has a {@link SegmentColourScheme} based on the average value of
     * SST at that latitude, and a span equal to 105% of the maximum span. To
     * save time, this average is calculated over only the files supplied in the
     * argument.
     * 
     * @param location
     *            The files to calculate the average over
     * @return A {@link Raster2DLayer}
     * @throws DataReadingException
     */
    public Raster2DLayer calculateRaster2DLayer(List<DateTime> times) throws DataReadingException {
        maxRange = 0.0f;
        means = new float[latitudeAxis.size()];
        Map<Float, Float> meanValuesMap = new HashMap<>();
        for (int y = 0; y < latitudeAxis.size(); y++) {
            Float minSst = Float.MAX_VALUE;
            Float maxSst = -Float.MAX_VALUE;
            float mean = 0.0f;
            int points = 0;
            for (DateTime time : times) {
                MapFeature mapFeature = getMapFeature(imageGrid, time, SST_VAR, true);

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
            if (minSst != Float.MAX_VALUE) {
                maxRange = Math.max(maxRange, maxSst - minSst);
            }
            mean /= points;
            means[y] = mean;
        }
        /*
         * Process the means to smooth the curve
         */
        for (int y = latitudeAxis.size() - 1; y > latitudeAxis.size() / 2; y--) {
            means[latitudeAxis.size() - 1 - y] = means[y];
        }
        float[] newmeans = new float[means.length];
        for (int y = 0; y < latitudeAxis.size(); y++) {
            float runningmean = 0.0f;
            int span = 17;
            for (int i = -span; i <= span; i++) {
                int currentIndex = i + y;
                if (currentIndex < 0)
                    currentIndex = 0;
                if (currentIndex > latitudeAxis.size() - 1)
                    currentIndex = latitudeAxis.size() - 1;
                runningmean += means[currentIndex] / (span * 2 + 1);
            }
            newmeans[y] = runningmean;
        }
        means = newmeans;

        for (int y = 0; y < latitudeAxis.size(); y++) {
            float latVal = latitudeAxis.getCoordinateValue(y).floatValue();
            meanValuesMap.put(latVal, means[y]);
        }

        /*
         * Now process the means
         */

        /*
         * Half the max range so that we get more saturation at the extremes -
         * this will mean that features stand out more clearly
         */
        maxRange *= 0.5;

        Map<Float, SegmentColourScheme> colorSchemeMap = new HashMap<>();
        for (Entry<Float, Float> extentEntry : meanValuesMap.entrySet()) {
            SegmentColourScheme colourScheme = new SegmentColourScheme(new ColourScale(
                    extentEntry.getValue() - maxRange / 2, extentEntry.getValue() + maxRange / 2,
                    false), null, null, new Color(0, true), "#080D38,#41B6C4,#FFFFD9", 250);
            colorSchemeMap.put(extentEntry.getKey(), colourScheme);
        }

        ColourScheme2D colourScheme = new MappedSegmentColorScheme2D(colorSchemeMap, new Color(0,
                true));
        return new Raster2DLayer(LATITUDE_VAR, SST_VAR, colourScheme);
    }

    public RasterLayer calculateIceLayer() {
        ColourScheme colourScheme = new SegmentColourScheme(new ColourScale(0f, 1.0f, false),
                new Color(0, true), null, new Color(0, true), "#00ffffff,#ffffff", 100);
        return new RasterLayer(ICE_VAR, colourScheme);
    }

    private MapFeature getMapFeature(RegularGrid imageGrid, DateTime time, String varId,
            boolean cache) throws DataReadingException {
        CacheKey key = new CacheKey(time, varId);
        if (mapFeatures.containsKey(key)) {
            return mapFeatures.get(key);
        } else {
            PlottingDomainParams params = new PlottingDomainParams(imageGrid, null, null, null,
                    null, time);
            List<? extends DiscreteFeature<?, ?>> extractedMapFeatures = dataset
                    .extractMapFeatures(CollectionUtils.setOf(varId), params);
            assert (extractedMapFeatures.size() == 1);
            MapFeature mapFeature = (MapFeature) extractedMapFeatures.get(0);
            if (cache) {
                mapFeatures.put(key, mapFeature);
            }
            return mapFeature;
        }
    }

    @Override
    public FeaturesAndMemberName getFeaturesForLayer(String id, PlottingDomainParams params)
            throws EdalException {
        if (id.equals(SST_VAR)) {
            return new FeaturesAndMemberName(getMapFeature(params.getImageGrid(),
                    params.getTargetT(), SST_VAR, false), SST_VAR);
        } else if (WIND_DIR_VAR.equals(id)) {
            return wind.getFeaturesForLayer(id, params);
        } else if (WIND_MAG_VAR.equals(id)) {
            return wind.getFeaturesForLayer(id, params);
        } else if (id.equals(ICE_VAR)) {
            return new FeaturesAndMemberName(getMapFeature(params.getImageGrid(),
                    params.getTargetT(), ICE_VAR, false), ICE_VAR);
        } else if (id.equals(LATITUDE_VAR)) {
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
            return new FeaturesAndMemberName(new MapFeature(LATITUDE_VAR, "", "",
                    new MapDomainImpl(params.getImageGrid(), null, null, params.getTargetT()),
                    null, latitudeValuesMap), LATITUDE_VAR);
        } else {
            return null;
        }
    }

    public static BufferedImage drawLegend(Raster2DLayer layer, float[] means, float range)
            throws EdalException {
        int width = (int) (HEIGHT * 0.4);
        int height = HEIGHT;
        float extra = 0.1f;
//        LegendDataGenerator dataGenerator = new LegendDataGenerator(width, height, null, extra, 0f);
        LegendDataGenerator dataGenerator = new ConstantColourLegendDataGenerator(width, height,
                null, extra, 0f, means, range);
        NameAndRange sstNameAndRange = null;
        for (NameAndRange testNameAndRange : layer.getFieldsWithScales()) {
            if (testNameAndRange.getFieldLabel().equals(SST_VAR)) {
                sstNameAndRange = testNameAndRange;
                break;
            }
        }

        BufferedImage legend = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = legend.createGraphics();
        graphics.setColor(Color.black);
        graphics.fillRect(0, 0, width, height);

        MapImage contoursRaster = new MapImage();
        contoursRaster.getLayers().add(layer);
        ContourLayer contours = new ContourLayer(SST_VAR, new ColourScale(250f, 320f, false),
                false, 14, Color.darkGray, 1, ContourLineStyle.SOLID, true);
        contoursRaster.getLayers().add(contours);

        BufferedImage colorbar2d = contoursRaster.drawImage(
                dataGenerator.getPlottingDomainParams(), dataGenerator.getFeatureCatalogue(
                        sstNameAndRange,
                        new NameAndRange(LATITUDE_VAR, Extents.newExtent(-90f, 90f))));
//        BufferedImage legendLabels = MapImage.getLegendLabels(sstNameAndRange, extra, width,
//                Color.white, true, HEIGHT / 60);
//        AffineTransform at = new AffineTransform();
//        at.translate(width, height - legendLabels.getWidth());
//        at.rotate(Math.PI / 2);
        graphics.drawImage(colorbar2d, 0, 0, null);
//        graphics.drawImage(legendLabels, at, null);
        return legend;
    }

    public static void main(String[] args) throws IOException, EdalException,
            NoSuchAuthorityCodeException, FactoryException {

        String outputPath = "/home/guy/sst-wind";

        BoundingBox bbox = new BoundingBoxImpl(-110, -7.5, -5, 45, DefaultGeographicCRS.WGS84);

        BufferedImage background = ImageIO.read(LatitudeDependentSSTWinds.class
                .getResource("/bluemarble_bg.png"));

        LatitudeDependentSSTWinds latitudeDependentSSTWinds = new LatitudeDependentSSTWinds(
                "/home/guy/Data/cci-sst/2010/**/**/*.nc",
                "/home/guy/Data/era_interim/2010/era.ncml", WIDTH, HEIGHT, bbox);
        
        TimeAxis timeAxis = (TimeAxis) latitudeDependentSSTWinds.wind.getDataset()
                .getVariableMetadata(WIND_DIR_VAR).getTemporalDomain();

        List<DateTime> useInAverage = new ArrayList<>();
        for (DateTime time : latitudeDependentSSTWinds.timeAxis.getCoordinateValues()) {
            if (time.getYear() == 2010 && time.getDayOfMonth() == 1 && time.getMonthOfYear() > 6 && time.getMonthOfYear() < 11) {
                useInAverage.add(time);
            }
        }

        MapImage compositeImage = new MapImage();
        Raster2DLayer sstLayer = latitudeDependentSSTWinds.calculateRaster2DLayer(useInAverage);


        SizedArrowLayer windLayer = new SizedArrowLayer(WIND_DIR_VAR, WIND_MAG_VAR, 0, 15,
                Extents.newExtent(0f, 20f), Color.black, ArrowStyle.TRI_ARROW);
        compositeImage.getLayers().add(sstLayer);
        compositeImage.getLayers().add(windLayer);

        int frameNo = 497;
        DecimalFormat frameNoFormat = new DecimalFormat("0000");
//        for (int i = 838; i < 1336; i++) {
        for (int i = 980; i < 1336; i++) {
            DateTime time = timeAxis.getCoordinateValue(i);
            PlottingDomainParams params = new PlottingDomainParams(WIDTH, HEIGHT, bbox, null, null,
                    null, null, time);
            System.out.println("Generating frame for " + time);

            BufferedImage frame = compositeImage.drawImage(params, latitudeDependentSSTWinds);
            ImageIO.write(frame, "png",
                    new File(outputPath + "/frame-" + frameNoFormat.format(frameNo++) + ".png"));
        }

//        DateTimeFormatter dateFormatter = (new DateTimeFormatterBuilder()).appendDayOfMonth(2)
//                .appendLiteral("-").appendMonthOfYear(2).appendLiteral("-").appendYear(4, 4)
//                .toFormatter();

//        System.out.println("Finished writing frames.  Now run:\nffmpeg -r 25 -i '" + outputPath
//                + "/frame-%04d.png' -c:v libx264 -pix_fmt yuv420p output.mp4");
    }

    private class CacheKey {
        private DateTime time;
        private String varId;

        public CacheKey(DateTime time, String varId) {
            super();
            this.time = time;
            this.varId = varId;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((time == null) ? 0 : time.hashCode());
            result = prime * result + ((varId == null) ? 0 : varId.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CacheKey other = (CacheKey) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (time == null) {
                if (other.time != null)
                    return false;
            } else if (!time.equals(other.time))
                return false;
            if (varId == null) {
                if (other.varId != null)
                    return false;
            } else if (!varId.equals(other.varId))
                return false;
            return true;
        }

        private LatitudeDependentSSTWinds getOuterType() {
            return LatitudeDependentSSTWinds.this;
        }
    }
}
