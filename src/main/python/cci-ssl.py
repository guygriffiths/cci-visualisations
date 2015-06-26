'''
Created on 19 Mar 2015

@author: Guy Griffiths
'''

from netCDF4 import Dataset
from mpl_toolkits.mplot3d import Axes3D
from mpl_toolkits.basemap import Basemap, interp
from matplotlib import cm
from matplotlib.ticker import LinearLocator, FormatStrFormatter
import matplotlib.pyplot as plt
import numpy as np
import matplotlib

def reproject_data(location, varname, map, lonname='lon', latname='lat', step=1, xsize=100, ysize=100, filter=np.nan):
    nc = Dataset(location)
    latvar = nc.variables[latname]
    lonvar = nc.variables[lonname]
    datavar = nc.variables[varname]

    lons = lonvar[::step]
    lats = latvar[::step]
    if len(datavar.dimensions) == 2:
        data = datavar[::step, ::step]
    elif len(datavar.dimensions) == 3:
        data = datavar[0,::step, ::step]
    
    # Set masked (i.e. land) data to 0.
    # plot_surface ignores masks, and if we set it to NaN, it screws up the colour map
    # TODO: try this again with a custom colour map...
    if filter is not None:
        data[np.where(np.ma.getmask(data) == True)] = filter

    # Now fix the longitude wrapping so that all values go from -180:180
    wrapindex = None
    for i, lon in enumerate(lons):
        if lon > 180:
            lons[i] -= 360
            if wrapindex is None:
                wrapindex = i
    if wrapindex is not None: 
        lons = np.hstack((lons[wrapindex:],lons[:wrapindex]))
        data = np.hstack((data[:,wrapindex:],data[:,:wrapindex]))

    lons_proj, lats_proj = map.makegrid(xsize, ysize)
    
    data_proj = interp(data, lons, lats, lons_proj, lats_proj, checkbounds=False, masked=False, order=1)

    XX, YY = np.meshgrid(np.arange(xsize), np.arange(ysize))
    
    nc.close()
    return (lons_proj, lats_proj, XX, YY, data_proj)
    

def generate_image(year, month, xs=500, ys=500, elev=60, azim=-90, colormap=cm.seismic):
    m = Basemap(width=12000000,height=12000000,
                rsphere=(6378137.00,6356752.3142),\
                resolution='l',area_thresh=1000.,projection='lcc',\
                lat_0=45,lon_0=170)

    ssl_loc = '/home/guy/Data/cci-ssl/ESACCI-SEALEVEL-L4-MSLA-MERGED-%04d%02d15000000-fv01.nc' % (year, month)
    lons_proj, lats_proj, XX, YY, sla = reproject_data(ssl_loc,'sla', m, xsize=xs, ysize=ys, filter=0)
    sst_loc = '/mnt/surft/data/SST_CCI_L4_monthly_mean_anomalies/%04d%02d--ESACCI-L4_GHRSST-SSTdepth-OSTIA-GLOB_LT-v02.0-fv01.0_anomalies.nc' %(year, month)
    lons_proj, lats_proj, XX, YY, sst = reproject_data(sst_loc,'sst_anomaly', m, xsize=xs, ysize=ys, filter=None)
    
    min_sst = -4
    max_sst = 4
    
    colors = np.empty(sst.shape, dtype=np.dtype((float, (4))))
    for y in range(sst.shape[1]):
        for x in range(sst.shape[0]):
            val = sst[x, y]
            if(np.ma.getmask(sst[x,y]) == True):
                colors[x,y] = (1,0,0,0)
            else:
                zero_to_one = (val - min_sst) / (max_sst - min_sst)
                colors[x, y] = colormap(zero_to_one)
    
    fig = plt.figure(figsize=(19.2,9.6))
    
    ax = plt.subplot(121, projection='3d')
       
    # ax = fig.gca(projection='3d')
    ax.view_init(elev=elev, azim=azim)
    ax.set_axis_off()
     
    surf = ax.plot_surface(XX, YY, sla, rstride=1, cstride=1, facecolors=colors,#cmap=cm.coolwarm,
                           linewidth=0, antialiased=False)
    ax.set_zlim(-3, 3)
    ax.set_xlim((0.22 * xs, 0.78 * xs))
    ax.set_ylim((0.18 * ys, 0.82 * ys))
    
    ax2d = plt.subplot(122, aspect=1)
    m.bluemarble(ax=ax2d, scale=0.2)
    #m.imshow(sst, ax=ax, cmap=cm.coolwarm)
    x, y = m(lons_proj, lats_proj)
    m.pcolor(x,y, sst, ax=ax2d, cmap=colormap, vmin=min_sst, vmax=max_sst)
    
    #matplotlib.rcParams['contour.negative_linestyle'] = 'dashed'
    m.contour(x,y, sla, np.linspace(-1,1,11), colors='k', ax=ax2d)
    # m.pcolor(XX, YY, sla, ax=ax)
    #ax.pcolormesh(XX,YY,sst, vmin=min_sst, vmax=max_sst, cmap=cm.coolwarm)
    
    
    # ax = fig.gca()
    # surf = ax.pcolormesh(XX,YY,sla, vmin=-limit, vmax=limit)
    # fig.colorbar(surf, shrink=0.5, aspect=5)
    
    fig.tight_layout()
    return fig
    
if __name__ == '__main__':
#     a = -45
#     e = 80
    for year in range(1993,2011):
        start=1
#         if year == 2003:
#             start = 9
        for month in range(start,13):
            fig = generate_image(year, month, 450, 450)    
            fig.savefig('/home/guy/ssl3d-out/ssl-%04d-%02d.png' % (year, month))
            # The way matplotlib works means that this figure is never garbage collected until we call clf() and close()
            fig.clf()
            plt.close()
            print 'generated for ',month,'/',year
#             a -= 90.0/216
#             e -= 20.0/216