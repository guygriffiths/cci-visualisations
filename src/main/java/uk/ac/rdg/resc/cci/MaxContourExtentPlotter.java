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

import java.awt.BasicStroke;
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

import uk.ac.rdg.resc.cci.EvolvingWindPlotter.EvolvingWindLine;
import uk.ac.rdg.resc.edal.grid.GridCell2D;
import uk.ac.rdg.resc.edal.grid.RegularAxis;
import uk.ac.rdg.resc.edal.grid.RegularGrid;
import uk.ac.rdg.resc.edal.grid.RegularGridImpl;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.Array2D;
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.GridCoordinates2D;

public class MaxContourExtentPlotter {
    private List<Flotsam> particles = new ArrayList<>();
    private RegularGrid imageGrid;

    private Color baseColour;

    public MaxContourExtentPlotter(RegularGrid imageGrid, double[] values, Color baseColour) {
        this.imageGrid = imageGrid;
        this.baseColour = baseColour;
        RegularAxis xAxis = imageGrid.getXAxis();
        List<Double> xVals = xAxis.getCoordinateValues();
        /*
         * TODO what start values should we use?
         */
        RegularAxis yAxis = imageGrid.getYAxis();
        for (double value : values) {
            for (Double xPos : xVals) {
                particles.add(new Flotsam(new HorizontalPosition(xPos, yAxis.getCoordinateValue(yAxis.size()/2),
                        imageGrid.getCoordinateReferenceSystem()), value, imageGrid, true));
                particles.add(new Flotsam(new HorizontalPosition(xPos, yAxis
                        .getCoordinateValue(yAxis.size()/2), imageGrid
                        .getCoordinateReferenceSystem()), value, imageGrid, false));
            }
        }
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
        for (Flotsam particle : particles) {
            if(particle.invisible) {
                continue;
            }
            HorizontalPosition position = particle.getPosition();
            CoordinateReferenceSystem crs = particle.getCrs();

            /*
             * Convert position to image grid co-ordinate space
             */
            if (!GISUtils.crsMatch(crs, imageGrid.getCoordinateReferenceSystem())) {
                position = GISUtils.transformPosition(position,
                        imageGrid.getCoordinateReferenceSystem());
            }
            int x = xAxis.findIndexOfUnconstrained(position.getX());
            int y = imageGrid.getYSize() - 1 - yAxis.findIndexOfUnconstrained(position.getY());
            g.setColor(baseColour);
            g.setStroke(new BasicStroke(1));
            g.drawRect(x, y, 1, 1);
        }

        return image;
    }

    public void evolve(Array2D<Number> values) {
        for (Flotsam particle : particles) {
            particle.evolve(values);
        }
    }

    public static class Flotsam {
        private HorizontalPosition position;
        /*
         * TODO Add a time component dependency
         */
        private CoordinateReferenceSystem crs;
        private boolean invisible = true;
        private RegularGrid coordGrid;
        private double contourValue;
        private boolean flowUp;
        private Number lastValue = Double.NaN;

        public Flotsam(HorizontalPosition startPos, double contourValue, 
                RegularGrid coordGrid, boolean flowUp) {
            this.contourValue = contourValue;
            this.coordGrid = coordGrid;
            this.flowUp = flowUp;
            this.position = startPos;
            crs = startPos.getCoordinateReferenceSystem();
        }

        public void evolve(Array2D<Number> grid) {
            GridCoordinates2D gridIndex = coordGrid.findIndexOf(position);
            int yIndex = gridIndex.getY();

            Number value = grid.get(yIndex, gridIndex.getX());
            if(lastValue != null && Double.isNaN(lastValue.doubleValue())) {
                lastValue = value;
            }
            while (value == null || value.doubleValue() > contourValue) {
                if (flowUp) {
                    yIndex++;
                } else {
                    yIndex--;
                }
                if (yIndex < 0) {
                    yIndex = 0;
                    break;
                }
                if (yIndex >= coordGrid.getYSize()) {
                    yIndex = coordGrid.getYSize() - 1;
                    break;
                }

                lastValue = value;
                value = grid.get(yIndex, gridIndex.getX());
            }
            if(lastValue == null) {
                invisible = true;
            } else {
                invisible = false; 
            }
            position = new HorizontalPosition(position.getX(), coordGrid.getYAxis()
                    .getCoordinateValue(yIndex), crs);
        }

        public HorizontalPosition getPosition() {
            return position;
        }

        public CoordinateReferenceSystem getCrs() {
            return crs;
        }
    }
}
