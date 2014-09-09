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

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.domain.MapDomainImpl;
import uk.ac.rdg.resc.edal.exceptions.DataReadingException;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.MapFeature;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme2D;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
import uk.ac.rdg.resc.edal.graphics.style.MappedSegmentColorScheme2D;
import uk.ac.rdg.resc.edal.graphics.style.Raster2DLayer;
import uk.ac.rdg.resc.edal.graphics.style.RasterLayer;
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
    private static final int WIDTH = 4320;
    private static final int HEIGHT = 2160;
//    private static final int WIDTH = 1500;
//    private static final int HEIGHT = 750;

    private TimeAxis timeAxis;
    private RegularGrid imageGrid;
    private RegularAxis latitudeAxis;
    private Map<CacheKey, MapFeature> mapFeatures = null;
    private Dataset dataset;
    private int width;
    private int height;

    public LatitudeDependentSST(String location, int width, int height) throws IOException,
            EdalException {
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

        mapFeatures = new HashMap<>();
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
        float maxRange = 0.0f;
        Map<Float, Float> meanValuesMap = new HashMap<>();
        for (int y = 0; y < latitudeAxis.size(); y++) {
            float latVal = latitudeAxis.getCoordinateValue(y).floatValue();
            Float minSst = Float.MAX_VALUE;
            Float maxSst = -Float.MAX_VALUE;
            float mean = 0.0f;
            int points = 0;
            for (DateTime time : times) {
                MapFeature mapFeature = getMapFeature(time, SST_VAR, true);

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

        /*
         * Half the max range so that we get more saturation at the extremes -
         * this will mean that features stand out more clearly
         */
        maxRange *= 0.5;

        Map<Float, SegmentColourScheme> colorSchemeMap = new HashMap<>();
        for (Entry<Float, Float> extentEntry : meanValuesMap.entrySet()) {
            SegmentColourScheme colourScheme = new SegmentColourScheme(new ColourScale(
                    extentEntry.getValue() - maxRange / 2, extentEntry.getValue() + maxRange / 2,
                    false), null, null, new Color(0, true), "default", 250);
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

    private MapFeature getMapFeature(DateTime time, String varId, boolean cache)
            throws DataReadingException {
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
            return new FeaturesAndMemberName(getMapFeature(params.getTargetT(), SST_VAR, false),
                    SST_VAR);
        } else if (id.equals(ICE_VAR)) {
            return new FeaturesAndMemberName(getMapFeature(params.getTargetT(), ICE_VAR, false),
                    ICE_VAR);
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
                    new MapDomainImpl(imageGrid, null, null, params.getTargetT()), null,
                    latitudeValuesMap), LATITUDE_VAR);
        } else {
            return null;
        }
    }

    public static void main(String[] args) throws IOException, EdalException {
        String outputPath = "/home/guy/sst-out";

        BufferedImage background = ImageIO.read(LatitudeDependentSST.class
                .getResource("/bluemarble_bg.png"));

        LatitudeDependentSST latitudeDependentSST = new LatitudeDependentSST(
                "/home/guy/Data/cci-sst/**/**/**/*.nc", WIDTH, HEIGHT);

        List<DateTime> useInAverage = new ArrayList<>();
        for (DateTime time : latitudeDependentSST.timeAxis.getCoordinateValues()) {
            if (time.getYear() == 2010 && time.getDayOfMonth() == 1) {
                useInAverage.add(time);
            }
        }

        MapImage compositeImage = new MapImage();
        compositeImage.getLayers().add(latitudeDependentSST.calculateRaster2DLayer(useInAverage));

        BufferedImage legend = compositeImage.getLegend((int) (HEIGHT * 0.4), (int) (HEIGHT * 0.8), Color.white, Color.black, true,
                false, 0.025f, 0.03f);
        ImageIO.write(legend, "png", new File("/home/guy/legend.png"));

        compositeImage.getLayers().add(latitudeDependentSST.calculateIceLayer());

        DateTimeFormatter dateFormatter = (new DateTimeFormatterBuilder()).appendDayOfMonth(2)
                .appendLiteral("-").appendMonthOfYear(2).appendLiteral("-")
                .appendYear(4, 4).toFormatter();

        int frameNo = 0;
        DecimalFormat frameNoFormat = new DecimalFormat("0000");
        for (DateTime time : latitudeDependentSST.timeAxis.getCoordinateValues()) {
            System.out.println("Generating frame for time " + time);
            BufferedImage frame = new BufferedImage(WIDTH + legend.getWidth() + WIDTH / 20, HEIGHT,
                    BufferedImage.TYPE_INT_ARGB);

            PlottingDomainParams params = new PlottingDomainParams(latitudeDependentSST.imageGrid,
                    null, null, null, null, time);
            BufferedImage sstImage = compositeImage.drawImage(params, latitudeDependentSST);

            Graphics2D g = frame.createGraphics();
            g.setColor(Color.black);
            g.fillRect(0, 0, WIDTH + legend.getWidth() + WIDTH / 20, HEIGHT);
            g.drawImage(background, 0, 0, WIDTH, HEIGHT, null);
            g.drawImage(sstImage, 0, 0, WIDTH, HEIGHT, null);
            g.drawImage(legend, WIDTH + WIDTH / 20, 0, legend.getWidth(), legend.getHeight(), null);
            g.setColor(Color.white);
            int fontSize = 10;
            Font font = new Font(Font.MONOSPACED, Font.PLAIN, fontSize);
            int height = 0;
            while(height < HEIGHT * 0.09) {
                font = new Font(Font.MONOSPACED, Font.PLAIN, fontSize++);
                height = g.getFontMetrics(font).getHeight();
            }
            g.setFont(font);
            g.drawString(dateFormatter.print(time), (int) (WIDTH + WIDTH / 20),
                    (int) (HEIGHT * 0.97));
            
            ImageIO.write(frame, "png",
                    new File(outputPath + "/frame-" + frameNoFormat.format(frameNo++) + ".png"));
        }

        System.out.println("Finished writing frames.  Now run:\nffmpeg -r 25 -i '" + outputPath
                + "/frame-%04d.png' -c:v libx264 output.mp4");
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

        private LatitudeDependentSST getOuterType() {
            return LatitudeDependentSST.this;
        }
    }
}
