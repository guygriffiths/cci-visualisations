from matplotlib.patches import Rectangle
from mpl_toolkits.basemap import Basemap
from os.path import isfile

import matplotlib.pyplot as plt
import netCDF4 as nc
import netcdf_utils as ncu
import numpy as np
import numpy.ma as ma


def plot_timeseries(times, values, ylabel, title='', \
                    lon=0.0, lat=0.0, \
                    t0=None, data0=None, lons0=None, lats0=None, \
                    t1=None, data1=None, lons1=None, lats1=None, \
                    cmap='seismic', symmetrical=False, fig=None):
    """
    Plots a timeseries graph with up to two additional times of interest plotted on globes positioned carefully on the graph
    Args:
    times - the time values to plot
    values - the corresponding values
    ylabel - the label for the y axis
    title - the title of the plot - blank by default
    t0 - the time of the first point of interest
    data0 - a 2d numpy array with the data for the 1st p.o.i.
    lons0 - a 1d numpy array with the longitude values for the 1st p.o.i.
    lats0 - a 1d numpy array with the latitude values for the 1st p.o.i.
    t1 - the time of the second point of interest
    data1 - a 2d numpy array with the data for the 2nd p.o.i.
    lons1 - a 1d numpy array with the longitude values for the 2nd p.o.i.
    lats1 - a 1d numpy array with the latitude values for the 2nd p.o.i.
    cmap - the name of the colour map to use when plotting the data.  Defaults to 'seismic'
    symmetrical - whether the colour map should be symmetrical about 0.  Defaults to False
    fig - the matplotlib to plot on.  By default this is None and gets initialised in the function, but can be passed in e.g. to set figure size
    
    returns the resulting figure.  Users can either call plt.show() to display it, or save it with fig.savefig() 
    """
    
    # Create a new figure if necessary
    if(fig is None):
        ts_fig = plt.figure()
    else:
        ts_fig = fig
    # Plot the data and set the labels
    ts_ax = ts_fig.gca()
    ts_ax.plot(times, values, 'k.-')
    ts_ax.set_xlabel('Date')
    ts_ax.set_ylabel(ylabel)
    ts_ax.set_title(title)
    
    plot_area = None
    # Plot the first area of interest
    if (t0 is not None) and (data0 is not None) and (lons0 is not None) and (lats0 is not None):
        # Find the best place to plot it
        plot_area = _get_emptiest_area(ts_ax, 3, t0.toordinal())
        # Add an arrow to the graph
        ts_ax.annotate('',
                       xy=(t0, values[np.where(times == t0)[0][0]]),
                       xycoords='data',
                       xytext=(plot_area.get_x() + plot_area.get_width() / 2, plot_area.get_y() + plot_area.get_height() / 2),
                       textcoords='figure fraction',
                       size=20,
                       arrowprops=dict(arrowstyle="simple",
                                       fc="0.6", ec="none",
                                       connectionstyle="arc3,rad=0.3"))
        # Now do the plot
        ax = ts_fig.add_axes([plot_area.get_x(), plot_area.get_y(), plot_area.get_width(), plot_area.get_height()])
        _plot_globe(ax, data0, lons0, lats0, lon, lat, cmap, symmetrical)

    # Plot the second area of interest
    if (t1 is not None) and (data1 is not None) and (lons1 is not None) and (lats1 is not None):
        plot_area = _get_emptiest_area(ts_ax, 3, t1.toordinal(), [plot_area])
        if plot_area is None:
            # This could theoretically happen if the 1st globe was big enough to take more space than was available
            # Recalculate but allow to overlap the first one
            plot_area = _get_emptiest_area(ts_ax, 3, t1.toordinal())
        ts_ax.annotate('',
                       xy=(t1, values[np.where(times == t1)[0][0]]),
                       xycoords='data',
                       xytext=(plot_area.get_x() + plot_area.get_width() / 2, plot_area.get_y() + plot_area.get_height() / 2),
                       textcoords='figure fraction',
                       size=20,
                       arrowprops=dict(arrowstyle="simple",
                                       fc="0.6", ec="none",
                                       connectionstyle="arc3,rad=0.3"))
        ax = ts_fig.add_axes([plot_area.get_x(), plot_area.get_y(), plot_area.get_width(), plot_area.get_height()])
        _plot_globe(ax, data1, lons1, lats1, lon, lat, cmap, symmetrical)
        
    return ts_fig
    
def _get_emptiest_area(ax, factor, target_x, areas_to_avoid=[]):
    """
    Get's the emptiest area of size (1/factor x 1/factor) compared to the overall size of the plot area.
    
    ax - the axes of the plot
    factor - 1 / the fraction of the size the area should be
    target_x - the ideal x-value for the area to be centred on
    areas_to_avoid - a list of figure-space Rectangles which must be avoided
    
    returns a Rectangle in figure-space which 
    """    
    lines = ax.get_lines()
    min_points = np.inf
    min_rect = None
    
    x_range = ax.get_xlim()
    coord_width = x_range[1] - x_range[0]
    plot_width = coord_width / factor
    y_range = ax.get_ylim()
    coord_height = y_range[1] - y_range[0]
    plot_height = coord_height / factor
    
    # Change the target x so that the centre will be at the target x
    target_x -= plot_width / 2
    if target_x < x_range[0]:
        target_x = x_range[0]
    
    # Start from the target x as an ideal position, then go right, then left
    for i in np.concatenate([np.linspace(target_x, x_range[1] - plot_width, 10), np.linspace(target_x, x_range[0], 10)]):
        # Start from the TOP of the plot as ideal, then downwards
        for j in np.linspace(y_range[1] - plot_height, y_range[0], 10):
            rect = Rectangle([i, j], plot_width, plot_height)
            
            overlap = False
            # Check that this rectangle will not overlap any of the explicitly-banned areas
            rect_bbox = _coord_space_rect_to_figure_space_rect(rect, ax).get_bbox()
            for area in areas_to_avoid:
                if rect_bbox.overlaps(area.get_bbox()):
                    overlap = True
                    break
            if overlap:
                continue
                
            points = 0
            for line in lines:
                for point in line.get_xydata():
                    if rect.contains_point(point, radius=0.0):
                        points += 1
            if points < min_points:
                min_points = points
                min_rect = rect
            if min_points == 0:
                break
        if min_points == 0:
            break
    
    return _coord_space_rect_to_figure_space_rect(min_rect, ax)    

def _coord_space_rect_to_figure_space_rect(r, ax):
    """
    Converts a Rectangle in coordinate space to one in figure space
    
    r - the Rectangle in coordinate space
    ax - the axes of the the figure
    """
    x_range = ax.get_xlim()
    y_range = ax.get_ylim()
    
    xmin_rel_axis = (r.get_x() - x_range[0]) / (x_range[1] - x_range[0])
    ymin_rel_axis = (r.get_y() - y_range[0]) / (y_range[1] - y_range[0])
    ax_bbox = ax.get_position()
    
    factor = (x_range[1] - x_range[0]) / (r.get_bbox().width)
    
    return Rectangle([ax_bbox.x0 + xmin_rel_axis * ax_bbox.width, \
                      ax_bbox.y0 + ymin_rel_axis * ax_bbox.height], \
                      ax_bbox.width / factor, \
                      ax_bbox.height / factor)

def _plot_globe(ax, data, lons, lats, lon0, lat0, cmap, symmetrical=False):  
    """
    Plots data onto a globe
    Args:
    ax - the axis to plot onto
    data - a 2d numpy array containing the data to plot (latitude axis first)
    lons - a 1d numpy array containing the longitude values of the data
    lats - a 1d numpy array containing the latitude values of the data 
    """  
    m = Basemap(projection='nsper', lon_0=lon0, lat_0=lat0, resolution='l')
    X, Y = np.meshgrid(lons, lats)
    x, y = m(X, Y)
    
    if(symmetrical):
        scale = np.abs(data).max()
        m.contourf(x, y, data, np.linspace(-scale, scale, 100), cmap=plt.get_cmap(cmap), ax=ax)
    else: 
        m.contourf(x, y, data, 100, cmap=plt.get_cmap(cmap), ax=ax)
    m.bluemarble(ax=ax)

def calculate_mean(data, lats):
    """
    data - a 2d lat-lon array with latitude axis first
    lats - a 1d array containing the corresponding latitude values
    
    returns - a latitude-weighted mean of the entire data array
    """
    
    # Create a 2d-array containing the weights of each cell - i.e. the cosine of the latitude of that cell
    lat_weights = np.repeat(np.cos([lats * np.pi / 180.0]).T, np.shape(data)[1], axis=1)
    return ma.average(data, weights=lat_weights)

def get_file(path, year, month, day, suffix='120000-ESACCI-L4_GHRSST-SSTanomaly-OSTIA-GLOB_LT-v02.0-fv01.0.nc'):
    filename = path + '/%04d/%02d/%02d/%04d%02d%02d' % (year, month, day, year, month, day) + suffix
    if isfile(filename):
        return filename
    else:
        return None

def get_timeseries(path,
                   varname,
                   start_year, end_year,
                   suffix,
                   bbox=[-180, -90, 180, 90]):
    """ Returns a globally averaged timeseries """
    times = []
    values = []
    for year in range(start_year, end_year + 1):
        for month in range(1, 13):
            for day in range(1, 32):
                filename = get_file(path, year, month, day, suffix)
                if filename is not None:
                    time, data, lons, lats = read_2d_data(filename, varname, bbox)
                    if data is not None:
                        times.append(time)
                        values.append(calculate_mean(data, lats))
    return np.array(times), np.array(values)

def read_2d_data(filename, varname, bbox=[-180.0, -90.0, 180.0, 90.0]):
    """
    Reads 2d data.  Assumes that we have a single time value per file
    
    path - the path to the data
    varname - the name of the variable to read
    year - the desired year
    month - the desired month
    day - the desired day
    suffix - the suffix of the data file (i.e. everything after the path and the initial yyyymmdd part of the name)
    """
    ds = nc.Dataset(filename)
    data_var = ds.variables[varname]
    
    min_lon = ncu.findNearestLonIndex(ds, data_var, bbox[0])
    min_lat = ncu.findNearestLatIndex(ds, data_var, bbox[1])
    max_lon = ncu.findNearestLonIndex(ds, data_var, bbox[2])
    max_lat = ncu.findNearestLatIndex(ds, data_var, bbox[3])
    
    time_var = ncu.findTimeVar(ds, data_var)
    time = nc.num2date(time_var[0], time_var.units)
    lats = ncu.findLatitudeVar(ds, ds.variables[varname])[min_lat:max_lat]
    lons = ncu.findLongitudeVar(ds, ds.variables[varname])[min_lon:max_lon]

    data = data_var[0, min_lat:max_lat, min_lon:max_lon]
    return time, data, lons, lats