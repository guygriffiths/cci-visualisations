'''
Example script using the utilities from cci_timeseries

Created on 9 Jun 2015

@author: Guy Griffiths
'''

import matplotlib.pyplot as plt
from cci_timeseries import *

# Set some variables for convenience
path = '/mnt/surft/data/SST_CCI_L4-anom/v02.0/'
suffix = '120000-ESACCI-L4_GHRSST-SSTanomaly-OSTIA-GLOB_LT-v02.0-fv01.0.nc'
varname = 'sst_anomaly_analysis'

# Create bounding box for the area of interest.  Omitting this will give a global average
bbox = [-40,-10,40,70]
# Read the timeseries data.  This returns a numpy array of times and values 
times, anomalies = get_timeseries(path, varname, 2010, 2010, suffix, bbox)

# This reads the 2d data, one with the bounding box and one without, for comparispn
t0, sst_data, lons, lats = read_2d_data(get_file(path, 2010, 5, 28, suffix), varname, bbox)
t1, sst_data1, lons1, lats1 = read_2d_data(get_file(path, 2010, 5, 25, suffix), varname)

# This puts both together to do the overall plot.  The 5th/6th argument are the lon/lat to view the data from
fig = plot_timeseries(times, anomalies, 'SST', 'SST timeseries', # x, y, ylabel, title
                      0.0, 52.0, # Centre of globe view (lon/lat)
                      t0, sst_data, lons, lats, # First point of interest
                      t1, sst_data1, lons1, lats1, # Second point of interest
                      symmetrical = True) # Colour scale should be centred around 0
# We could now change anything about the figure which was required (e.g. axis limits, labels, etc...), or save it, but 
plt.show()
