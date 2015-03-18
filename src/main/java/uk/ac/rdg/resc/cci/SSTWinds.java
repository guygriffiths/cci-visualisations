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
import java.util.List;

import javax.imageio.ImageIO;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

import uk.ac.rdg.resc.cci.IBTracsReader.PosAndName;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.exceptions.VariableNotFoundException;
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
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.GridCoordinates2D;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;

public class SSTWinds {
    static final String SST_VAR = "analysed_sst";
    private static final String WIND_X_VAR = "U10";
    private static final String WIND_Y_VAR = "V10";
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 960;

    public static void main(String[] args) throws IOException, EdalException {

        BufferedImage background = ImageIO.read(SSTWinds.class.getResource("/bluemarble_bg.png"));

        String outputPath = "/home/guy/sst-wind";
        final SimpleFeatureCatalogue cciSst = new SimpleFeatureCatalogue("cci",
                "/home/guy/Data/cci-sst/**/**/**/*.nc", false);
        final SimpleFeatureCatalogue xWind = new SimpleFeatureCatalogue("wind",
                "/home/guy/Data/era_interim/U10.erai.19812013.nc", false);
        final SimpleFeatureCatalogue yWind = new SimpleFeatureCatalogue("wind",
                "/home/guy/Data/era_interim/V10.erai.19812013.nc", false);
        TimeAxis timeAxis = (TimeAxis) xWind.getDataset().getVariableMetadata(WIND_X_VAR)
                .getTemporalDomain();

        IBTracsReader ibtracs = new IBTracsReader(
                "/home/guy/Data/storm_tracks/Allstorms.ibtracs_all.v03r06.nc", new DateTime(1992,
                        1, 1, 0, 0), new DateTime(2010, 12, 31, 23, 59));

        MapImage compositeImage = new MapImage();
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

        RasterLayer sstDiffLayer = new RasterLayer(SST_VAR, new SegmentColourScheme(
                new ColourScale(-3f, 3f, false), null, null, new Color(0, true), "div-BuRd2", 250));
        compositeImage.getLayers().add(sstDiffLayer);

        FeatureCatalogue featureCatalogue = new FeatureCatalogue() {
            @Override
            public FeaturesAndMemberName getFeaturesForLayer(String id, PlottingDomainParams params)
                    throws EdalException {
                if (SST_VAR.equals(id)) {
                    return cciSst.getFeaturesForLayer(id, params);
                } else if (WIND_X_VAR.equals(id)) {
                    return xWind.getFeaturesForLayer(id, params);
                } else if (WIND_Y_VAR.equals(id)) {
                    return yWind.getFeaturesForLayer(id, params);
                }
                throw new VariableNotFoundException(id + " not in this catalogue");
            }
        };

        DecimalFormat frameNoFormat = new DecimalFormat("0000");

        /*
         * Simple format for the date.
         */
        DateTimeFormatter dateFormatter = (new DateTimeFormatterBuilder()).appendDayOfMonth(2)
                .appendLiteral("-").appendMonthOfYear(2).appendLiteral("-").appendYear(4, 4)
                .toFormatter();
        Font font = null;

        for (int year = 1992; year <= 2010; year++) {
            String yearOutPath = outputPath + "/" + year;
            File dir = new File(yearOutPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            int startTimeIndex = GISUtils.getIndexOfClosestTimeTo(new DateTime(year, 6, 1, 0, 0),
                    timeAxis);
            int endTimeIndex = GISUtils.getIndexOfClosestTimeTo(new DateTime(year, 12, 1, 0, 0),
                    timeAxis);
            int frameNo = 0;
            RunningAverageDiffFeatureCatalogue diffFc = new RunningAverageDiffFeatureCatalogue(
                    SST_VAR, 10, featureCatalogue, (TimeAxis) cciSst.getDataset()
                            .getVariableMetadata(SST_VAR).getTemporalDomain());
            EvolvingWindPlotter windPlotter = new EvolvingWindPlotter(imageGrid, 0.05, 24,
                    new Color(0f, 0f, 0f, 0.3f), 20);

            for (int i = startTimeIndex; i < endTimeIndex; i++) {
                DateTime time = timeAxis.getCoordinateValue(i);
                System.out.println("Generating frame for " + time);

                PlottingDomainParams params = new PlottingDomainParams(imageGrid, null, null, null,
                        null, time);

                FeaturesAndMemberName xF = xWind.getFeaturesForLayer(WIND_X_VAR, params);
                FeaturesAndMemberName yF = yWind.getFeaturesForLayer(WIND_Y_VAR, params);
                MapFeature xMapFeature = (MapFeature) xF.getFeatures().iterator().next();
                MapFeature yMapFeature = (MapFeature) yF.getFeatures().iterator().next();
                Array2D<Number> xCompVals = xMapFeature.getValues(WIND_X_VAR);
                Array2D<Number> yCompVals = yMapFeature.getValues(WIND_Y_VAR);

                windPlotter.evolve(xCompVals, yCompVals);
                BufferedImage frame = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
                BufferedImage sst = compositeImage.drawImage(params, diffFc);
                BufferedImage winds = windPlotter.plot();
                Graphics2D g = frame.createGraphics();

                if (font == null) {
                    /*
                     * Calculate a font which should take up at most
                     * targetFontHeight vertically (but will be at least font
                     * size 6)
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
                g.setColor(new Color(1.0f, 1.0f, 1.0f, 0.5f));
                g.fillRect(0, 0, WIDTH, HEIGHT);

                /*
                 * Draw date
                 */
                g.setColor(Color.black);
                g.drawString(dateFormatter.print(time), WIDTH / 40, font.getSize() + HEIGHT / 20);

                /*
                 * Draw data layers
                 */
                g.drawImage(sst, 0, 0, null);
                g.drawImage(winds, 0, 0, null);

                /*
                 * Label storms
                 */
                g.setColor(Color.black);
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
                List<PosAndName> stormPositionsForTime = ibtracs.getStormPositionsForTime(time);
                for (PosAndName posName : stormPositionsForTime) {
                    GridCoordinates2D stormCentre = imageGrid.findIndexOf(posName.getPos());
                    if (stormCentre != null) {
                        int yPos = HEIGHT - 1 - stormCentre.getY();
                        g.fillOval(stormCentre.getX(), yPos, 10, 10);
                        g.drawString(posName.getName(), stormCentre.getX() + 10, yPos);
                    }
                }
                ImageIO.write(frame, "png",
                        new File(yearOutPath + "/frame-" + frameNoFormat.format(frameNo++) + ".png"));
            }

            System.out.println("Finished writing frames.  Now run:\nffmpeg -r 25 -i '"
                    + yearOutPath
                    + "/frame-%04d.png' -crf 18 -c:v libx264 -pix_fmt yuv420p output.mp4");
        }
    }
}
