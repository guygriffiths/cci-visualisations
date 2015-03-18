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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.nc2.Variable;
import ucar.nc2.dataset.NetcdfDataset;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.TimeAxisImpl;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.Extents;
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.TimeUtils;
import uk.ac.rdg.resc.edal.util.cdm.CdmUtils;

public class IBTracsReader {
    private static final String NAME_VAR = "name";
    private static final String LAT_VAR = "lat_for_mapping";
    private static final String LON_VAR = "lon_for_mapping";
    private static final String TIME_VAR = "source_time";
    private static final DateTimeFormatter IBTRACS_DATE_TIME_FORMATTER = (new DateTimeFormatterBuilder())
            .appendYear(4, 4).appendLiteral("-").appendMonthOfYear(1).appendLiteral("-")
            .appendDayOfMonth(1).appendLiteral(" ").appendHourOfDay(1).appendLiteral(":")
            .appendMinuteOfHour(1).appendLiteral(":").appendSecondOfMinute(1).toFormatter();

    private Map<TimeAxis, Integer> range2StormNum = new HashMap<>();
    private Variable nameVar;
    private Variable latVar;
    private Variable lonVar;

    public IBTracsReader(String location, DateTime startTime, DateTime endTime) throws IOException {
        NetcdfDataset dataset = CdmUtils.openDataset(location);
        nameVar = dataset.findVariable(NAME_VAR);
        latVar = dataset.findVariable(LAT_VAR);
        lonVar = dataset.findVariable(LON_VAR);
        Variable timeVar = dataset.findVariable(TIME_VAR);
        String units = timeVar.findAttribute("units").getStringValue();
        String[] timeUnitsParts = units.split(" since ");
        /*
         * Find the length of a unit, in seconds (we don't use milliseconds
         * because the DateTime.plusMillis takes an integer argument and there
         * is a very good chance of integer overflow for recent values)
         */
        int unitLength = TimeUtils.getUnitLengthSeconds(timeUnitsParts[0]);
        DateTime refTime = IBTRACS_DATE_TIME_FORMATTER.parseDateTime(timeUnitsParts[1]);
        System.out.println(refTime.getChronology() + "," + startTime.getChronology());
        System.out.println(units + "\n" + refTime + "  " + unitLength);

        Array times = timeVar.read();
        Index index = times.getIndex();
        int[] shape = index.getShape();
        /*
         * Loop over all storms
         */
        for (int s = 0; s < shape[0]; s++) {
            index.set0(s);
            DateTime minT = new DateTime(Long.MAX_VALUE);
            DateTime maxT = new DateTime(-Long.MAX_VALUE);
            List<DateTime> timeAxisVals = new ArrayList<>();
            for (int t = 0; t < shape[1]; t++) {
                index.set1(t);
                double timeDouble = times.getDouble(index);
                if (!Double.isNaN(timeDouble)) {
                    DateTime time = refTime.plus((long) (1000 * unitLength * timeDouble));
                    timeAxisVals.add(time);
                    if (time.isAfter(maxT)) {
                        maxT = time;
                    }
                    if (time.isBefore(minT)) {
                        minT = time;
                    }
                }
            }
            TimeAxisImpl tAxis = new TimeAxisImpl("time", timeAxisVals);
            Extent<DateTime> range = Extents.newExtent(startTime, endTime);
            if (range.contains(tAxis.getExtent().getLow())
                    || range.contains(tAxis.getExtent().getHigh()) ||
                    tAxis.getExtent().contains(startTime) || tAxis.getExtent().contains(endTime)

            ) {
                range2StormNum.put(tAxis, s);
            }
        }
//        GridDataset gridDataset = CdmUtils.getGridDataset(dataset);
//        for (Gridset gridset : gridDataset.) {
//            GridCoordSystem coordSys = gridset.getGeoCoordSystem();
//            HorizontalGrid hDomain = CdmUtils.createHorizontalGrid(coordSys);
//            VerticalAxis zDomain = CdmUtils.createVerticalAxis(coordSys);
//            TimeAxis tDomain = CdmUtils.createTimeAxis(coordSys);
//            System.out.println(tDomain);
//        }
    }

    class PosAndName {
        HorizontalPosition pos;
        String name;

        public PosAndName(HorizontalPosition pos, String name) {
            super();
            this.pos = pos;
            StringBuilder b = new StringBuilder(name.toLowerCase());
            b.replace(0, 1, b.substring(0, 1).toUpperCase());
            this.name = b.toString();
        }

        public HorizontalPosition getPos() {
            return pos;
        }

        public String getName() {
            return name;
        }
    }

    public List<PosAndName> getStormPositionsForTime(DateTime time) throws IOException {
        List<PosAndName> ret = new ArrayList<>();
        for (TimeAxis axis : range2StormNum.keySet()) {
            if (axis.contains(time)) {
                Integer stormIndex = range2StormNum.get(axis);
                int timeIndex = GISUtils.getIndexOfClosestTimeTo(time, axis);
                Array lon = lonVar.read();
                Index lonIndex = lon.getIndex();
                lonIndex.set0(stormIndex);
                lonIndex.set1(timeIndex);
                double lonDouble = lon.getDouble(lonIndex);
                Array lat = latVar.read();
                Index latIndex = lat.getIndex();
                latIndex.set0(stormIndex);
                latIndex.set1(timeIndex);
                double latDouble = lat.getDouble(latIndex);

                Array name = nameVar.read();
                StringBuilder nameStr = new StringBuilder();
                for (int i = 0; i < 57; i++) {
                    char nameChar = name.getChar(stormIndex * 57 + i);
                    if (nameChar != 0) {
                        nameStr.append(nameChar);
                    }
                }
                PosAndName posAndName = new PosAndName(new HorizontalPosition(lonDouble, latDouble,
                        DefaultGeographicCRS.WGS84), nameStr.toString());
                if(!posAndName.name.equalsIgnoreCase("Not named")) {
                    ret.add(posAndName);
                }
            }
        }
        return ret;
    }

    public static void main(String[] args) throws IOException {
        IBTracsReader ibtracs = new IBTracsReader(
                "/home/guy/Data/storm_tracks/Allstorms.ibtracs_all.v03r06.nc", new DateTime(1991,
                        1, 1, 0, 0), new DateTime(2010, 12, 31, 23, 59));
        for (PosAndName posAndName : ibtracs.getStormPositionsForTime(new DateTime(2010, 5, 28, 12,
                00))) {
            System.out.println(posAndName.getPos() + "," + posAndName.getName());
        }
    }
}
