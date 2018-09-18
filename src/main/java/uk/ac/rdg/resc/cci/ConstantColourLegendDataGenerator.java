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

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.feature.MapFeature;
import uk.ac.rdg.resc.edal.graphics.style.Drawable.NameAndRange;
import uk.ac.rdg.resc.edal.graphics.utils.LegendDataGenerator;
import uk.ac.rdg.resc.edal.util.Array2D;
import uk.ac.rdg.resc.edal.util.Extents;

public class ConstantColourLegendDataGenerator extends LegendDataGenerator {

    private float[] means;
    private float span;
    private String averagedVar;

    public ConstantColourLegendDataGenerator(int width, int height, BufferedImage backgroundMask,
            float fractionExtraX, float fractionExtraY, float[] means, float range, String averagedVar) {
        super(width, height, backgroundMask, fractionExtraX, fractionExtraY);
        this.means = means;
        this.span = range / 2;
        this.averagedVar = averagedVar;
    }

    @SuppressWarnings("serial")
    protected MapFeature getMapFeature(NameAndRange field, MatrixType type) {
        Map<String, Array2D<Number>> values = new HashMap<String, Array2D<Number>>();
        if (field != null) {
            if (averagedVar.equals(field.getFieldLabel())) {
                values.put(field.getFieldLabel(), new Array2D<Number>(yAxis.size(), xAxis.size()) {
                    @Override
                    public Number get(int... coords) {
                        Extent<Float> latrange = Extents.newExtent(means[coords[0]] - span, means[coords[0]] + span);
                        return getLinearInterpolatedValue(
                                coords[1],
                                latrange,
                                xAxis.size());
                    }

                    @Override
                    public void set(Number value, int... coords) {
                        throw new UnsupportedOperationException();
                    }
                });
            } else {
                values.put(field.getFieldLabel(), new XYNan(type, field.getScaleRange()));
            }
        }
        MapFeature feature = new MapFeature("", "", "", domain, null, values);
        return feature;
    }
}
