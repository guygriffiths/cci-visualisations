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
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.domain.MapDomainImpl;
import uk.ac.rdg.resc.edal.exceptions.DataReadingException;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.exceptions.VariableNotFoundException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.MapFeature;
import uk.ac.rdg.resc.edal.graphics.style.ScaleRange;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme2D;
import uk.ac.rdg.resc.edal.graphics.style.ContourLayer;
import uk.ac.rdg.resc.edal.graphics.style.ContourLayer.ContourLineStyle;
import uk.ac.rdg.resc.edal.graphics.style.Drawable.NameAndRange;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
import uk.ac.rdg.resc.edal.graphics.style.MappedSegmentColorScheme2D;
import uk.ac.rdg.resc.edal.graphics.style.Raster2DLayer;
import uk.ac.rdg.resc.edal.graphics.style.SegmentColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue;
import uk.ac.rdg.resc.edal.graphics.style.util.LegendDataGenerator;
import uk.ac.rdg.resc.edal.grid.RegularAxis;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.metadata.GridVariableMetadata;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.Array2D;
import uk.ac.rdg.resc.edal.util.CollectionUtils;
import uk.ac.rdg.resc.edal.util.Extents;
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;

public class LatitudeDependentSST implements FeatureCatalogue {
    public static final String LATITUDE = "latitude";

    private final String sstVar;

    /** The {@link TimeAxis} of the data */
    private final TimeAxis timeAxis;
    /** Cached features */
    private final Map<CacheKey, MapFeature> mapFeatures;

    private final Dataset dataset;

    private float[] means;
    private float scaleRange;

    private FeaturesAndMemberName latitudeFeature = null;
    private Raster2DLayer sstLayer = null;

    private final RegularGrid averagingGrid;

    public LatitudeDependentSST(String location, String sstVar, RegularGrid averagingGrid)
            throws IOException, EdalException {
        this.sstVar = sstVar;

        CdmGridDatasetFactory datasetFactory = new CdmGridDatasetFactory();
        dataset = datasetFactory.createDataset("cci-sst", location);

        GridVariableMetadata sstMetadata = (GridVariableMetadata) dataset
                .getVariableMetadata(sstVar);

        timeAxis = sstMetadata.getTemporalDomain();

        this.averagingGrid = averagingGrid;

        mapFeatures = new HashMap<>();
    }

    public TimeAxis getTimeAxis() {
        return timeAxis;
    }

    /**
     * Generates a Raster2DLayer with a {@link ColourScheme2D} where each
     * latitude has a {@link SegmentColourScheme} based on the average value of
     * SST at that latitude, and a span equal to a given multiple of the maximum
     * span. This average is calculated over only the times supplied in the
     * argument.
     * 
     * @param times
     *            The {@link DateTime}s over which to calculated the average
     * @param rangeMultiplier
     *            The amount to multiply the final calculated range by. Smaller
     *            numbers will mean more constrast of features, but greater
     *            saturation at the scale extremes
     * @param smoothingSpan
     *            The number of latitude points either side of the mean to
     *            calculate a running average over (to smooth the data)
     * @return A {@link Raster2DLayer}
     * @throws DataReadingException
     * @throws VariableNotFoundException
     */
    public void generateSSTLayer(List<DateTime> times, double rangeMultiplier, int smoothingSpan,
            String palette) throws DataReadingException, VariableNotFoundException {
        scaleRange = 0.0f;
        RegularAxis latitudeAxis = averagingGrid.getYAxis();
        means = new float[latitudeAxis.size()];
        /*
         * Calculate the mean value of SST at each latitude, and the maximum
         * range of SST values
         */
        for (int y = 0; y < latitudeAxis.size(); y++) {
            Float minSst = Float.MAX_VALUE;
            Float maxSst = -Float.MAX_VALUE;
            float mean = 0.0f;
            int points = 0;
            for (DateTime time : times) {
                MapFeature mapFeature = getMapFeature(time, sstVar, true, averagingGrid);
                Array2D<Number> sstValues = mapFeature.getValues(sstVar);
                for (int x = 0; x < sstValues.getXSize(); x++) {
                    Number sstValue = sstValues.get(y, x);
                    if (sstValue != null && !Float.isNaN(sstValue.floatValue())) {
                        minSst = Math.min(minSst, sstValue.floatValue());
                        maxSst = Math.max(maxSst, sstValue.floatValue());
                        mean += sstValue.doubleValue();
                        points++;
                    }
                }
            }
            if (minSst != Float.MAX_VALUE) {
                scaleRange = Math.max(scaleRange, maxSst - minSst);
            }
            mean /= points;
            means[y] = mean;
        }
        /*
         * Process the means to smooth the curve. We could just have calculated
         * the means at half the latitude values, but this could potentially
         * miss the maximum scale range. This routine generally only gets called
         * once anyway
         */
        if (latitudeAxis.getCoordinateExtent().getLow()
                .equals(-latitudeAxis.getCoordinateExtent().getHigh())) {
            for (int y = latitudeAxis.size() - 1; y > latitudeAxis.size() / 2; y--) {
                means[latitudeAxis.size() - 1 - y] = means[y];
            }
        }
        /*
         * Replace the mean values with a mean over the given span. This smooths
         * the curve
         */
        float[] newmeans = new float[means.length];
        for (int y = 0; y < latitudeAxis.size(); y++) {
            float runningmean = 0.0f;
            int span = smoothingSpan / 2;
            int points = 0;
            for (int i = -span; i <= span; i++) {
                int currentIndex = i + y;
                if (currentIndex < 0 || currentIndex > latitudeAxis.size() - 1)
                    continue;
                runningmean += means[currentIndex];
                points++;
            }
            newmeans[y] = runningmean / points;
        }
        means = newmeans;

        /*
         * Adjust the max range by the multiplier to allow for more contrast
         */
        scaleRange *= rangeMultiplier;

        /*
         * Generate a ColourScheme for each latitude
         */
        SegmentColourScheme[] schemes = new SegmentColourScheme[latitudeAxis.size()];
        for (int y = 0; y < latitudeAxis.size(); y++) {
            float mean = means[y];
            SegmentColourScheme colourScheme = new SegmentColourScheme(new ScaleRange(mean
                    - scaleRange / 2, mean + scaleRange / 2, false), null, null,
                    new Color(0, true), palette, 250);
            schemes[y] = colourScheme;
        }

        /*
         * Now create the 2d colour scheme
         */
        ColourScheme2D colourScheme = new MappedSegmentColorScheme2D(latitudeAxis, schemes,
                new Color(0, true));
        /*
         * And the corresponding 2d raster layer
         */
        sstLayer = new Raster2DLayer(LATITUDE, sstVar, colourScheme);
    }

    public Raster2DLayer getSSTLayer() {
        if (sstLayer == null) {
            throw new IllegalStateException(
                    "SST Layer not initialised.  You must call generateSSTLayer() with a list of times to use in the latitude averaging before you can call this method.");
        }
        return sstLayer;
    }

    /**
     * Reads a {@link MapFeature} from the data/cache
     * 
     * @param time
     *            The {@link DateTime} at which to read the feature
     * @param varId
     *            The variable ID to read
     * @param cache
     *            Whether to cache the resulting feature
     * @return The extracted {@link MapFeature}
     * @throws DataReadingException
     * @throws VariableNotFoundException
     */
    private MapFeature getMapFeature(DateTime time, String varId, boolean cache,
            RegularGrid imageGrid) throws DataReadingException, VariableNotFoundException {
        CacheKey key = new CacheKey(time, varId, imageGrid);
        if (mapFeatures.containsKey(key)) {
            return mapFeatures.get(key);
        } else {
            /*
             * Extract the map feature onto the desired image grid
             */
            PlottingDomainParams params = new PlottingDomainParams(imageGrid, null, null, null,
                    null, time);
            List<? extends DiscreteFeature<?, ?>> extractedMapFeatures = dataset
                    .extractMapFeatures(CollectionUtils.setOf(varId), params);
            /*
             * Exactly one feature is extracted.
             */
            assert (extractedMapFeatures.size() == 1);
            MapFeature mapFeature = (MapFeature) extractedMapFeatures.get(0);
            if (cache) {
                /*
                 * Cache the feature if required. Since this generally works
                 * with a very large dataset, only features which will be used
                 * multiple times should be cached
                 */
                mapFeatures.put(key, mapFeature);
            }
            return mapFeature;
        }
    }

    @Override
    public FeaturesAndMemberName getFeaturesForLayer(String id, PlottingDomainParams params) {
        /*
         * This is called by the image generation code (i.e. when we call
         * drawImage() on the MapImage object).
         * 
         * Generally we simply read the given variable at the given time.
         * However, the underlying data has no 2d latitude variable, so we add a
         * special case for that
         */
        if (id.equals(LATITUDE)) {
            /*
             * We only want to generate this once, since it will never change
             */
            if (latitudeFeature == null) {
                final RegularGrid imageGrid = params.getImageGrid();
                Map<String, Array2D<Number>> latitudeValuesMap = new HashMap<>();
                latitudeValuesMap.put(LATITUDE,
                        new Array2D<Number>(imageGrid.getYSize(), imageGrid.getXSize()) {
                            @Override
                            public Number get(int... coords) {
                                Double yval = imageGrid.getYAxis()
                                        .getCoordinateValue(coords[Y_IND]);
                                Double xval = imageGrid.getXAxis()
                                        .getCoordinateValue(coords[X_IND]);
                                HorizontalPosition llPos = GISUtils.transformPosition(
                                        new HorizontalPosition(xval, yval, imageGrid
                                                .getCoordinateReferenceSystem()),
                                        DefaultGeographicCRS.WGS84);

                                RegularAxis latitudeAxis = averagingGrid.getYAxis();
                                int latIndex = latitudeAxis.findIndexOf(llPos.getY());
                                return latitudeAxis.getCoordinateValue(latIndex);
                            }

                            @Override
                            public void set(Number value, int... coords) {
                                throw new UnsupportedOperationException();
                            }
                        });
                latitudeFeature = new FeaturesAndMemberName(new MapFeature(LATITUDE,
                        "Temperature_height_above_ground", "", new MapDomainImpl(imageGrid, null,
                                null, params.getTargetT()), null, latitudeValuesMap), LATITUDE);
            }
            return latitudeFeature;
        } else {
            try {
                return new FeaturesAndMemberName(getMapFeature(params.getTargetT(), id, false,
                        params.getImageGrid()), id);
            } catch (DataReadingException | VariableNotFoundException e) {
                return null;
            }
        }
    }

    public BufferedImage drawLegend(int width, int height) throws EdalException {
        if (sstLayer == null) {
            throw new IllegalStateException(
                    "SST Layer not initialised.  You must call generateSSTLayer() with a list of times to use in the latitude averaging before you can call this method.");
        }
        float extra = 0.1f;
//        LegendDataGenerator dataGenerator = new LegendDataGenerator(width, height, null, extra, 0f);
        LegendDataGenerator dataGenerator = new ConstantColourLegendDataGenerator(width, height,
                null, extra, 0f, means, scaleRange, sstVar);
        NameAndRange sstNameAndRange = null;
        for (NameAndRange testNameAndRange : sstLayer.getFieldsWithScales()) {
            if (testNameAndRange.getFieldLabel().equals(sstVar)) {
                sstNameAndRange = testNameAndRange;
                break;
            }
        }

        BufferedImage legend = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = legend.createGraphics();
        graphics.setColor(Color.black);
        graphics.fillRect(0, 0, width, height);

        MapImage contoursRaster = new MapImage();
        contoursRaster.getLayers().add(sstLayer);
        ContourLayer contours = new ContourLayer(sstVar, new ScaleRange(250f, 320f, false), false,
                14, Color.darkGray, 1, ContourLineStyle.SOLID, true);
        contoursRaster.getLayers().add(contours);

        BufferedImage colorbar2d = contoursRaster.drawImage(
                dataGenerator.getPlottingDomainParams(), dataGenerator.getFeatureCatalogue(
                        sstNameAndRange, new NameAndRange(LATITUDE, Extents.newExtent(-90f, 90f))));
//        BufferedImage legendLabels = MapImage.getLegendLabels(sstNameAndRange, extra, width,
//                Color.white, true, HEIGHT / 60);
//        AffineTransform at = new AffineTransform();
//        at.translate(width, height - legendLabels.getWidth());
//        at.rotate(Math.PI / 2);
        graphics.drawImage(colorbar2d, 0, 0, null);
//        graphics.drawImage(legendLabels, at, null);
        return legend;
    }

    private class CacheKey {
        private DateTime time;
        private String varId;
        private RegularGrid grid;

        public CacheKey(DateTime time, String varId, RegularGrid grid) {
            super();
            this.time = time;
            this.varId = varId;
            this.grid = grid;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((grid == null) ? 0 : grid.hashCode());
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
            if (grid == null) {
                if (other.grid != null)
                    return false;
            } else if (!grid.equals(other.grid))
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

        private LatitudeDependentSST getOuterType() {
            return LatitudeDependentSST.this;
        }
    }
}
