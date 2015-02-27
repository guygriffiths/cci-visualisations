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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.MapFeature;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.util.Array2D;
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;
import uk.ac.rdg.resc.edal.util.ValuesArray2D;

/**
 * A {@link FeatureCatalogue} which wraps an existing one and for a selected
 * variable it returns the difference from the running average instead of the
 * raw variable values.
 * 
 * The running average is calculated from n previous (distinct) calls to
 * getFeatureForLayer()
 *
 * @author Guy Griffiths
 */
public class RunningAverageDiffFeatureCatalogue implements FeatureCatalogue {
    private String varId;
    private FeatureCatalogue catalogue;
    private FixedSizeBuffer<Array2D<Number>> arraysHistory;
    private int lastTimeIndex = -1;
    private Array2D<Number> lastArray = null;
    private TimeAxis tAxis;

    /**
     * Create a new {@link RunningAverageDiffFeatureCatalogue}
     * 
     * @param varId
     *            The variable to calculate the difference from the average for
     * @param length
     *            The number of timesteps to calculate the running average over
     * @param originalDataFeatureCatalogue
     *            The {@link FeatureCatalogue} to wrap
     * @param tAxis
     *            The {@link TimeAxis} of the variable. This is required to
     *            calculate whether a new time value
     */
    public RunningAverageDiffFeatureCatalogue(String varId, int length,
            FeatureCatalogue originalDataFeatureCatalogue, TimeAxis tAxis) {
        this.varId = varId;
        this.catalogue = originalDataFeatureCatalogue;
        this.tAxis = tAxis;
        arraysHistory = new FixedSizeBuffer<>(length);
    }

    @Override
    public FeaturesAndMemberName getFeaturesForLayer(String id, PlottingDomainParams params)
            throws EdalException {
        /*
         * TODO This is quite specific. Could do with generalising and being put
         * into EDAL libraries
         */
        if (varId.equals(id)) {
            FeaturesAndMemberName features = catalogue.getFeaturesForLayer(id, params);
            List<DiscreteFeature<?, ?>> retFeatures = new ArrayList<>();
            String member = features.getMember();
            for (DiscreteFeature<?, ?> feature : features.getFeatures()) {
                /*
                 * Only transform MapFeatures. It would be nice if there was a
                 * generic way of changing values of a DiscreteFeature, but
                 * having a setValues() method on it (whilst certainly possible)
                 * opens up more possibilities for errors.
                 */
                if (feature instanceof MapFeature) {
                    MapFeature mapFeature = (MapFeature) feature;
                    final Array2D<Number> values = mapFeature.getValues(member);
                    Map<String, Array2D<Number>> valuesMap = new HashMap<>();
                    /*
                     * Check if we're just using the same time index
                     */
                    int timeIndex = GISUtils.getIndexOfClosestTimeTo(mapFeature.getDomain()
                            .getTime(), tAxis);
                    Array2D<Number> diffValues;
                    if (lastTimeIndex == timeIndex) {
                        diffValues = lastArray;
                    } else {
                        diffValues = new ValuesArray2D(values.getYSize(), values.getXSize());
                        for (int i = 0; i < values.getXSize(); i++) {
                            for (int j = 0; j < values.getYSize(); j++) {
                                Number value = values.get(j, i);
                                if (value == null) {
                                    diffValues.set(null, j, i);
                                    continue;
                                }
                                if (Double.isNaN(value.doubleValue())) {
                                    diffValues.set(null, j, i);
                                    continue;
                                }
                                Double mean = 0.0;
                                int count = 0;
                                for (Array2D<Number> history : arraysHistory) {
                                    if (history != null) {
                                        Number avVal = history.get(j, i);
                                        if (avVal != null && !Double.isNaN(avVal.doubleValue())) {
                                            mean += avVal.doubleValue();
                                            count++;
                                        }
                                    }
                                }
                                if (count > 0) {
                                    diffValues.set(value.doubleValue() - (mean / count), j, i);
                                } else {
                                    diffValues.set(0.0, j, i);
                                }
                            }
                        }
                        arraysHistory.add(values);

                        lastTimeIndex = timeIndex;
                        lastArray = diffValues;
                    }

                    valuesMap.put(member, diffValues);
                    MapFeature diffMapFeature = new MapFeature(mapFeature.getId(),
                            mapFeature.getName(), mapFeature.getDescription(),
                            mapFeature.getDomain(), mapFeature.getParameterMap(), valuesMap);
                    retFeatures.add(diffMapFeature);
                } else {
                    retFeatures.add(feature);
                }
            }
            return new FeaturesAndMemberName(retFeatures, member);
        } else {
            return catalogue.getFeaturesForLayer(id, params);
        }
    }
}
