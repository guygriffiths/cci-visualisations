""" This module contains code for reading data from NetCDF files and
    intepreting metadata """

# First set of functions originally written by Jon Blower

def findNearestIndex(vals, target):
    """ Searches through vals for the target value, returning the index
    of the value in vals that is closest numerically to the target value.
    If more than one value in vals is equally close to the target, the index
    of the first value will be returned. """
    minIndex = -1
    minDiff = None
    # Loop over all the values in the given list
    for i in range(len(vals)):
        # Find the absolute difference between this value and the target
        diff = abs(vals[i] - target)
        # If this is the first time, or if the difference is smaller than
        # the smallest difference found so far, remember the difference and
        # the index
        if minDiff is None or diff < minDiff:
            minDiff = diff
            minIndex = i
    return minIndex
    
def getAttribute(var, attName, default=None):
    """ Gets the value of the given attribute as a string.  Returns the
    given default if the attribute is not defined. This is a useful
    "helper" method, which avoids AttributeErrors being raised if the
    attribute isn't defined.  If no default is specified, this function
    returns None if the attribute is not found. """
    if attName in var.ncattrs():
        return var.getncattr(attName)
    else:
        return default

def getTitle(var):
    """ Returns a title for a variable in the form "name (units)"
    The name is taken from the standard_name if it is provided, but
    if it is not provided, it is taken from the id of the variable
    (i.e. var._name).  If the units are not provided, the string
    "no units" is used instead. """
    standardName = getAttribute(var, 'standard_name', var._name)
    units = getAttribute(var, 'units', 'no units')
    return "%s (%s)" % (standardName, units)

#######################################################################################
#####  The following functions test to see if coordinate variables represent geographic
#####  or time axes
#######################################################################################

def isLongitudeVar(coordVar):
    """ Given a coordinate variable (i.e. a NetCDF Variable object), this returns
    True if the variable holds values of longitude, False otherwise """
    # In the Climate and Forecast conventions, longitude variables are indicated
    # by their units (not by their names)
    units = getAttribute(coordVar, 'units')
    # There are many possible options for valid longitude units
    if units in ['degrees_east', 'degree_east', 'degree_E', 'degrees_E', 'degreeE', 'degreesE']:
        return True
    else:
        return False
    
def isLatitudeVar(coordVar):
    """ Given a coordinate variable (i.e. a NetCDF Variable object), this returns
    True if the variable holds values of latitude, False otherwise """
    # In the Climate and Forecast conventions, latitude variables are indicated
    # by their units (not by their names)
    units = getAttribute(coordVar, 'units')
    # There are many possible options for valid latitude units
    if units in ['degrees_north', 'degree_north', 'degree_N', 'degrees_N', 'degreeN', 'degreesN']:
        return True
    else:
        return False
    
def isVerticalVar(coordVar):
    """ Given a coordinate variable (i.e. a NetCDF Variable object), this returns
    True if the variable represents a vertical coordinate, False otherwise """
    # In the Climate and Forecast conventions, vertical coordinates are indicated
    # by units of pressure, or by the presence of a "positive" attribute.
    units = getAttribute(coordVar, "units")
    # First we look for units of pressure.  (There may be more possible pressure units
    # than are used here.)
    if units in ['Pa', 'hPa', 'pascal', 'Pascal']:
        return True
    else:
        # We don't have units of pressure, but perhaps we have a "positive" attribute
        positive = getAttribute(coordVar, 'positive')
        if positive in ['up', 'down']:
            return True
            
    # If we've got this far, we haven't satisfied either of the conditions for a
    # valid vertical axis
    return False
    
def isTimeVar(coordVar):
    """ Given a coordinate variable (i.e. a NetCDF Variable object), this returns
    True if the variable represents a time coordinate, False otherwise """
    # In the Climate and Forecast conventions, time coordinates are indicated
    # by units that conform to the pattern "X since Y", e.g. "days since 1970-1-1 0:0:0".
    # For simplicity, we just look for the word "since" in the units.  A complete
    # implementation should check this more thoroughly.
    units = getAttribute(coordVar, 'units')
    if units is None:
        # There are no units, so this can't be a time coordinate variable
        return False
    # The "find()" function on strings returns the index of the first match of the given
    # pattern.  If no match is found, find() returns -1.
    if units.find("since") >= 0:
        return True
    else:
        return False


#######################################################################################
#####  The following functions find geographic and time coordinate axes
#####  for data variables.
#######################################################################################

# As you can see, there is a lot of repetition in these functions - they all do basically
# the same thing.  There is a way to avoid this repetition, but it involves a technique
# that you may not be familiar with (i.e. passing functions as arguments to other functions)
# - see me if you want to know more.
    
def findLongitudeVar(nc, dataVar):
    """ Given a NetCDF Dataset object and a Variable object representing a data
    variable, this function finds and returns the Variable object representing the
    longitude axis for the data variable.  If no longitude axis is found, this returns
    None. """
    # First we iterate over the dimensions of the data variable
    for dim in dataVar.dimensions:
        # We get the coordinate variable that holds the values for this dimension
        coordVar = nc.variables[dim]
        # We test to see if this is a longitude variable, if so we return it
        if isLongitudeVar(coordVar):
            return coordVar
    # If we get this far we have not found the required coordinate variable
    return None

def findLatitudeVar(nc, dataVar):
    """ Given a NetCDF Dataset object and a Variable object representing a data
    variable, this function finds and returns the Variable object representing the
    latitude axis for the data variable.  If no latitude axis is found, this returns
    None. """
    for dim in dataVar.dimensions:
        coordVar = nc.variables[dim]
        if isLatitudeVar(coordVar):
            return coordVar
    return None

def findVerticalVar(nc, dataVar):
    """ Given a NetCDF Dataset object and a Variable object representing a data
    variable, this function finds and returns the Variable object representing the
    vertical axis for the data variable.  If no vertical axis is found, this returns
    None. """
    for dim in dataVar.dimensions:
        coordVar = nc.variables[dim]
        if isVerticalVar(coordVar):
            return coordVar
    return None

def findTimeVar(nc, dataVar):
    """ Given a NetCDF Dataset object and a Variable object representing a data
    variable, this function finds and returns the Variable object representing the
    time axis for the data variable.  If no time axis is found, this returns
    None. """
    for dim in dataVar.dimensions:
        coordVar = nc.variables[dim]
        if isTimeVar(coordVar):
            return coordVar
    return None

def isPositiveUp(zVar):
    """ Given a vertical coordinate variable, this function returns true if the
    values on the vertical axis increase upwards. For vertical axes based on pressure,
    the values increase downward, so this returns False.  If the axis is not based
    on pressure, the value of the "positive" attribute (which can be "up" or "down")
    is used instead. """
    units = getAttribute(zVar, "units")
    # First we look for units of pressure.  (If we find them, this is a pressure axis
    # and the values increase downward)
    if units in ['Pa', 'hPa', 'pascal', 'Pascal']:
        return False
    else:
        # We don't have units of pressure, but perhaps we have a "positive" attribute
        positive = getAttribute(zVar, 'positive')
        if positive == 'up':
            return True
        else:
            return False

# Rest of functions written by Caroline Dunning

def findNearestLatIndex(nc, dataVar, latval):
    """This function finds the index of the nearest latitude value to latval.The function takes three arguments:
    nc is a netCDF dataset object
    dataVar is a variable object representing a data variable
    latval is a latitude value (must be between -90 and 90)
    The function works by first finding the coordinate variable representing latitude values (using the findLatitudeVar function from this file)
    It checks a latitude variable is present and raises a value error if it is not present.
    It then finds the index of the latitude that is closest to latval. This is completed using the findNearestIndex function from utils.py (imported above)"""

    # Find the coordinate variable representing latitude values
    latitude = findLatitudeVar(nc, dataVar)

    # Check there is a valid latitude variable
    if latitude == None:
        raise ValueError("The given data variable doesn't have a valid latitude variable. Please check the file or enter a new data variable")

    # Find and return the nearest index
    index = findNearestIndex(latitude[:], latval)
    return index  


def findNearestZIndex(nc, dataVar, verval):
    """This function finds the index of the nearest latitude value to latval.The function takes three arguments:
    nc is a netCDF dataset object
    dataVar is a variable object representing a data variable
    verval is a value along the vertical axis (any value)
    The function finds the coordinate variable representing the vertical values for the data variable (using findVerticalVar, a function in this file)
    If a vertical variable is not found, a value error is raised (note: vertical variable must be in line with CF conventions to be considered a vertical variable)
    The index of the vertical value that is closest to verval is returned ( found using findNearestIndex function from utils.py (imported above))
    The function does not check that verval is within the range of the vertical axis;
    If verval is not within the range of the vertical axis then the index of the relevant end point will be given"""

    # Find the coordinate variable representing the vertical variables
    vertical_axis = findVerticalVar(nc, dataVar)

    # Check there is a valid vertical axis
    if vertical_axis == None:
        raise ValueError("The given data variable doesn't have a valid vertical variable in accordance with CF conventions.")

    # Find and return the nearest index
    index = findNearestIndex(vertical_axis[:], verval)
    return int(index)


def findNearestLonIndex(nc, dataVar, lonval):
    """This function finds the index of the nearest longitude value to lonval.The function takes three arguments:
    nc is a netCDF dataset object
    dataVar is a variable object representing a data variable
    lonval is a longitude value and should be in the range 0-359.999 
    ie 25 degrees west would be 335, 25 degrees east would be 25 etc.

    The function finds the co-ordinate variable representing the longitude values for the data variable 
    This is completed using the findLongitudeVar function from above
    if a valid longitude variable is not found, a value error is raised

    The two methods of representing longitude are then considered to check lonval is consistent with the netcdf file being used.
    Two cases are considered:
    1) If the minimum longitude value in the file is greater than or equal to 0
    If this is the case then the longitude values in the file are either in the range 0-180 or 0-360
    In this case the value we want to find the index for is the same as lonval so findval = lonval
    2) If the minimum longitude value in the file is less than 0
    If this is the case then the longitude values in the file are in the range -180 - 180
    In this case if lonval is between 0-180 then findval = lonval (same values whichever system used)
    However, if lonval is between 180-360 it needs to be transformed to -180 - 0: this is done by subtracting 360

    The index of the longitude value closest to findval is found and returned.
    This is completed using the findNearestIndex function from utils.py (imported above)"""

    # Find the coordinate variable representing longitude values
    longvar = findLongitudeVar(nc, dataVar)

    # Check there is a valid longitude variable
    if longvar == None:
        raise ValueError("The given data variable doesn't have a valid longitude variable in accordance with CF conventions")

    # Transform axis and value to be in correct form
    # Find minimum value to indicate which system has been used
    low = min(longvar[:])
    if low >= 0.0:
        # Positive system used; same values, no changes needed 
        findval = lonval
    else:
        # Negative values have been used; need to transform vertval to -180 - 180
        if lonval > 180.0:
            findval = lonval - 360
        else: 
            findval = lonval 

    # Find and return the nearest index as an integer
    index = findNearestIndex(longvar[:], findval)
    return int(index)  
