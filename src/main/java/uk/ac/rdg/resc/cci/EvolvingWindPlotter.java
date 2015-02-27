/*******************************************************************************
 * Copyright (c) 2015 The University of Reading
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.opengis.referencing.crs.CoordinateReferenceSystem;

import uk.ac.rdg.resc.edal.grid.GridCell2D;
import uk.ac.rdg.resc.edal.grid.RegularAxis;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.grid.RegularGridImpl;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.Array2D;
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.GridCoordinates2D;

public class EvolvingWindPlotter {
    private final double weight;
    private List<EvolvingWindLine> windLines = new ArrayList<>();
    private RegularGrid imageGrid;
    private RegularGrid positionGrid;

    private Color baseColour;
    private int length;

    public EvolvingWindPlotter(RegularGrid imageGrid, double weight, int gridSpace,
            Color baseColour, int length) {
        this.imageGrid = imageGrid;
        this.weight = weight;
        this.positionGrid = new RegularGridImpl(imageGrid.getBoundingBox(), imageGrid.getXSize()
                / gridSpace, imageGrid.getYSize() / gridSpace);

        Iterator<GridCell2D> iterator = positionGrid.getDomainObjects().iterator();
        while (iterator.hasNext()) {
            windLines.add(new EvolvingWindLine(iterator.next().getCentre(), weight, length));
        }

        this.baseColour = baseColour;
        this.length = length;
    }

    public void addWindLine(HorizontalPosition position) {
        windLines.add(new EvolvingWindLine(position, weight, length));
    }

    public BufferedImage plot() {
        BufferedImage image = new BufferedImage(imageGrid.getXSize(), imageGrid.getYSize(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        RegularAxis xAxis = imageGrid.getXAxis();
        RegularAxis yAxis = imageGrid.getYAxis();

        /*
         * Plot each wind line
         */
        for (EvolvingWindLine line : windLines) {
            List<HorizontalPosition> positions = line.getPositions();
            CoordinateReferenceSystem crs = line.getCrs();
            for (int i = 0; i < positions.size() - 1; i++) {
                /*
                 * p1 is the position closest to the head of the line p2 is the
                 * next one along
                 */
                HorizontalPosition p1 = positions.get(i);
                HorizontalPosition p2 = positions.get(i + 1);

                if (p1 != null) {
                    /*
                     * Convert p1 to image grid co-ordinate space
                     */
                    if (!GISUtils.crsMatch(crs, imageGrid.getCoordinateReferenceSystem())) {
                        p1 = GISUtils.transformPosition(p1,
                                imageGrid.getCoordinateReferenceSystem());
                    }
                    int x1 = xAxis.findIndexOfUnconstrained(p1.getX());
                    int y1 = imageGrid.getYSize() - 1 - yAxis.findIndexOfUnconstrained(p1.getY());

                    /*
                     * Set the transparency based on proximity to the head of
                     * the line
                     */
                    float alpha = ((float) (line.length - 1 - i)) / (line.length - 1);
                    /*
                     * Using alpha^3 gives a nicer fadeout
                     */
                    alpha = 255 * (alpha * alpha * alpha * (baseColour.getAlpha() / 255f));
                    Color c = new Color(baseColour.getRed(), baseColour.getGreen(),
                            baseColour.getBlue(), (int) alpha);
                    g.setColor(c);

                    if (p2 == null) {
                        /*
                         * We only have one point - plot it as a pixel
                         */
                        image.setRGB(x1, y1, c.getRGB());
                    } else {
                        /*
                         * We have a line segment - plot it.
                         */
                        if (!GISUtils.crsMatch(crs, imageGrid.getCoordinateReferenceSystem())) {
                            p2 = GISUtils.transformPosition(p2,
                                    imageGrid.getCoordinateReferenceSystem());
                        }

                        int x2 = xAxis.findIndexOfUnconstrained(p2.getX());
                        int y2 = imageGrid.getYSize() - 1
                                - yAxis.findIndexOfUnconstrained(p2.getY());

                        if (Math.abs(x1 - x2) < imageGrid.getXSize() / 2) {
                            /*
                             * Simple check for date line crossing.
                             * 
                             * Far from ideal, but if the winds are so strong
                             * that a particle covers more than half the image
                             * grid, something funny is probably going on
                             * anyway...
                             */
                            g.drawLine(x1, y1, x2, y2);
                        }
                    }
                }
            }
        }

        return image;
    }

    public void evolve(Array2D<Number> xComps, Array2D<Number> yComps) {
        Map<GridCoordinates2D, Integer> counts = new HashMap<>();
        for (int i = 0; i < positionGrid.getXSize(); i++) {
            for (int j = 0; j < positionGrid.getYSize(); j++) {
                counts.put(new GridCoordinates2D(i, j), 0);
            }
        }

        for (EvolvingWindLine line : windLines) {
            if (line.expired) {
                /* Doesn't matter how we evolve this, since it's expired */
                line.evolve(0, 0);
                continue;
            }
            HorizontalPosition position = line.getPositions().get(0);
            int xIndex = imageGrid.getXAxis().findIndexOf(position.getX());
            int yIndex = imageGrid.getYAxis().findIndexOf(position.getY());
            if (xIndex < 0 || yIndex < 0) {
                line.expired = true;
                /* Doesn't matter how we evolve this, since it's expired */
                line.evolve(0, 0);
                continue;
            }

            Number xNumber = xComps.get(yIndex, xIndex);
            Number yNumber = yComps.get(yIndex, xIndex);
            line.evolve(xNumber.doubleValue(), yNumber.doubleValue());

            int xDensityIndex = positionGrid.getXAxis().findIndexOf(position.getX());
            int yDensityIndex = positionGrid.getYAxis().findIndexOf(position.getY());
            GridCoordinates2D densityCoords = new GridCoordinates2D(xDensityIndex, yDensityIndex);
            Integer count = counts.get(densityCoords);
            if (count > 3) {
                line.expired = true;
            } else {
                counts.put(densityCoords, count + 1);
            }
        }

        for (Entry<GridCoordinates2D, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == 0) {
                /*
                 * Add a new wind line at the given position
                 */
                GridCoordinates2D coords = entry.getKey();
                Double x = positionGrid.getXAxis().getCoordinateValue(coords.getX());
                Double y = positionGrid.getYAxis().getCoordinateValue(coords.getY());
                windLines.add(new EvolvingWindLine(new HorizontalPosition(x, y, positionGrid
                        .getCoordinateReferenceSystem()), weight, length));
            }
        }

        /*
         * Now we need to iterate over the image grid calculating the density
         * per grid square, adding new lines when it's too low and expiring
         * lines when it's too high...
         */
    }

    public static class EvolvingWindLine {
        private final int length;
        private FixedSizeBuffer<HorizontalPosition> positions;
        /*
         * TODO Add a time component dependency
         */
        /** Distance to evolve line per unit of wind */
        private double weight;
        private CoordinateReferenceSystem crs;
        private boolean expired = false;

        public EvolvingWindLine(HorizontalPosition startPos, double weight, int length) {
            positions = new FixedSizeBuffer<>(length);
            positions.add(startPos);
            crs = startPos.getCoordinateReferenceSystem();
            this.weight = weight;
            this.length = length;
        }

        public void evolve(double xComp, double yComp) {
            if (expired) {
                positions.add(null);
            } else {
                /*
                 * TODO add time component here
                 */
                HorizontalPosition position = positions.get(0);
                double newX = position.getX() + weight * xComp;
                double newY = position.getY() + weight * yComp;
                positions.add(new HorizontalPosition(newX, newY, crs));
            }
        }

        public List<HorizontalPosition> getPositions() {
            return positions;
        }

        public CoordinateReferenceSystem getCrs() {
            return crs;
        }
    }
}
