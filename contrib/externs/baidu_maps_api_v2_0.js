/**
 * @externs
 */

var BMap = {};


/**
 * @enum {number}
 */
BMap.ControlAnchor = {
  BMAP_ANCHOR_TOP_LEFT: '',
  BMAP_ANCHOR_TOP_RIGHT: '',
  BMAP_ANCHOR_BOTTOM_LEFT: '',
  BMAP_ANCHOR_BOTTOM_RIGHT: ''
};


/**
 * @enum {string}
 */
BMap.LengthUnit = {
  BMAP_UNIT_METRIC: '',
  BMAP_UNIT_IMPERIAL: ''
};


/**
 * @enum {number}
 */
BMap.NavigationControlType = {
  BMAP_NAVIGATION_CONTROL_LARGE: '',
  BMAP_NAVIGATION_CONTROL_SMALL: '',
  BMAP_NAVIGATION_CONTROL_PAN: '',
  BMAP_NAVIGATION_CONTROL_ZOOM: ''
};


/**
 * @enum {number}
 */
BMap.MapTypeControlType = {
  BMAP_MAPTYPE_CONTROL_HORIZONTAL: '',
  BMAP_MAPTYPE_CONTROL_DROPDOWN: '',
  BMAP_MAPTYPE_CONTROL_MAP: ''
};


/**
 * @enum {number}
 */
BMap.StatusCode = {
  BMAP_STATUS_SUCCESS: '',
  BMAP_STATUS_CITY_LIST: '',
  BMAP_STATUS_UNKNOWN_LOCATION: '',
  BMAP_STATUS_UNKNOWN_ROUTE: '',
  BMAP_STATUS_INVALID_KEY: '',
  BMAP_STATUS_INVALID_REQUEST: '',
  BMAP_STATUS_PERMISSION_DENIED: '',
  BMAP_STATUS_SERVICE_UNAVAILABLE: '',
  BMAP_STATUS_TIMEOUT: ''
};


/**
 * @enum {number}
 */
BMap.SymbolShapeType = {
  BMap_Symbol_SHAPE_CIRCLE: '',
  BMap_Symbol_SHAPE_RECTANGLE: '',
  BMap_Symbol_SHAPE_RHOMBUS: '',
  BMap_Symbol_SHAPE_STAR: '',
  BMap_Symbol_SHAPE_BACKWARD_CLOSED_ARROW: '',
  BMap_Symbol_SHAPE_FORWARD_CLOSED_ARROW: '',
  BMap_Symbol_SHAPE_BACKWARD_OPEN_ARROW: '',
  BMap_Symbol_SHAPE_FORWARD_OPEN_ARROW: '',
  BMap_Symbol_SHAPE_POINT: '',
  BMap_Symbol_SHAPE_PLANE: '',
  BMap_Symbol_SHAPE_CAMERA: '',
  BMap_Symbol_SHAPE_WARNING: '',
  BMap_Symbol_SHAPE_SMILE: '',
  BMap_Symbol_SHAPE_CLOCK: ''
};


/**
 * @enum {number}
 */
BMap.Animation = {
  BMAP_ANIMATION_DROP: '',
  BMAP_ANIMATION_BOUNCE: ''
};


/**
 * @enum {number}
 */
BMap.ShapeType = {
  BMAP_POINT_SHAPE_CIRCLE: '',
  BMAP_POINT_SHAPE_STAR: '',
  BMAP_POINT_SHAPE_SQUARE: '',
  BMAP_POINT_SHAPE_RHOMBUS: '',
  BMAP_POINT_SHAPE_WATERDROP: ''
};


/**
 * @enum {number}
 */
BMap.SizeType = {
  BMAP_POINT_SIZE_TINY: '',
  BMAP_POINT_SIZE_SMALLER: '',
  BMAP_POINT_SIZE_SMALL: '',
  BMAP_POINT_SIZE_NORMAL: '',
  BMAP_POINT_SIZE_BIG: '',
  BMAP_POINT_SIZE_BIGGER: '',
  BMAP_POINT_SIZE_HUGE: ''
};


/**
 * @enum {string}
 */
BMap.ContextMenuIcon = {
  BMAP_CONTEXT_MENU_ICON_ZOOMIN: '',
  BMAP_CONTEXT_MENU_ICON_ZOOMOUT: ''
};


/**
 * @enum {number}
 */
BMap.PointDensityType = {
  BMAP_POINT_DENSITY_HIGH: '',
  BMAP_POINT_DENSITY_MEDIUM: '',
  BMAP_POINT_DENSITY_LOW: ''
};


/**
 * @enum {number}
 */
BMap.PoiType = {
  BMAP_POI_TYPE_NORMAL: '',
  BMAP_POI_TYPE_BUSSTOP: '',
  BMAP_POI_TYPE_SUBSTOP: ''
};


/**
 * @enum {number}
 */
BMap.TransitPolicy = {
  BMAP_TRANSIT_POLICY_LEAST_TIME: '',
  BMAP_TRANSIT_POLICY_LEAST_TRANSFER: '',
  BMAP_TRANSIT_POLICY_LEAST_WALKING: '',
  BMAP_TRANSIT_POLICY_AVOID_SUBWAYS: ''
};


/**
 * @enum {number}
 */
BMap.LineType = {
  BMAP_LINE_TYPE_BUS: '',
  BMAP_LINE_TYPE_SUBWAY: '',
  BMAP_LINE_TYPE_FERRY: ''
};


/**
 * @enum {number}
 */
BMap.DrivingPolicy = {
  BMAP_DRIVING_POLICY_LEAST_TIME: '',
  BMAP_DRIVING_POLICY_LEAST_DISTANCE: '',
  BMAP_DRIVING_POLICY_AVOID_HIGHWAYS: ''
};


/**
 * @enum {number}
 */
BMap.RouteType = {
  BMAP_ROUTE_TYPE_DRIVING: '',
  BMAP_ROUTE_TYPE_WALKING: ''
};


/**
 * @enum {number}
 */
BMap.HighlightModes = {
  BMAP_HIGHLIGHT_STEP: '',
  BMAP_HIGHLIGHT_ROUTE: ''
};


/**
 * @enum {string}
 */
BMap.PanoramaSceneType = {
  BMAP_PANORAMA_INDOOR_SCENE: '',
  BMAP_PANORAMA_STREET_SCENE: ''
};


/**
 * @enum {string}
 */
BMap.PanoramaPOIType = {
  BMAP_PANORAMA_POI_HOTEL: '',
  BMAP_PANORAMA_POI_CATERING: '',
  BMAP_PANORAMA_POI_MOVIE: '',
  BMAP_PANORAMA_POI_TRANSIT: '',
  BMAP_PANORAMA_POI_INDOOR_SCENE: '',
  BMAP_PANORAMA_POI_NONE: ''
};



/**
 * @constructor
 */
BMap.AddressComponent = function() {};


/**
 * @type {string}
 */
BMap.AddressComponent.prototype.streetNumber;


/**
 * @type {string}
 */
BMap.AddressComponent.prototype.street;


/**
 * @type {string}
 */
BMap.AddressComponent.prototype.district;


/**
 * @type {string}
 */
BMap.AddressComponent.prototype.city;


/**
 * @type {string}
 */
BMap.AddressComponent.prototype.province;



/**
 * @interface
 */
BMap.AlbumsControlOptions = function() {};


/**
 * @type {BMap.ControlAnchor}
 */
BMap.AlbumsControlOptions.prototype.anchor;


/**
 * @type {BMap.Size}
 */
BMap.AlbumsControlOptions.prototype.offset;


/**
 * @type {number|string}
 */
BMap.AlbumsControlOptions.prototype.maxWidth;


/**
 * @type {number}
 */
BMap.AlbumsControlOptions.prototype.imageHeight;



/**
 * @constructor
 * @param {(BMap.AutocompleteOptions|Object.<string>)=} opt_autocompleteOptions
 */
BMap.Autocomplete = function(opt_autocompleteOptions) {};


/**
 *
 */
BMap.Autocomplete.prototype.show = function() {};


/**
 *
 */
BMap.Autocomplete.prototype.hide = function() {};


/**
 * @param {Array<string>} types
 */
BMap.Autocomplete.prototype.setTypes = function(types) {};


/**
 * @param {string|BMap.Map|BMap.Point} location
 */
BMap.Autocomplete.prototype.setLocation = function(location) {};


/**
 * @param {string} keywords
 */
BMap.Autocomplete.prototype.search = function(keywords) {};


/**
 * @return {BMap.AutocompleteResult}
 */
BMap.Autocomplete.prototype.getResults = function() {};


/**
 * @param {string} keyword
 */
BMap.Autocomplete.prototype.setInputValue = function(keyword) {};


/**
 *
 */
BMap.Autocomplete.prototype.dispose = function() {};



/**
 * @interface
 */
BMap.AutocompleteOptions = function() {};


/**
 * @type {string|BMap.Point|BMap.Map}
 */
BMap.AutocompleteOptions.prototype.location;


/**
 * @type {string}
 */
BMap.AutocompleteOptions.prototype.types;


/**
 * @type {Function}
 */
BMap.AutocompleteOptions.prototype.onSearchComplete;


/**
 * @type {string|HTMLElement}
 */
BMap.AutocompleteOptions.prototype.input;



/**
 * @constructor
 */
BMap.AutocompleteResult = function() {};


/**
 * @param {number} i
 * @return {BMap.AutocompleteResultPoi}
 */
BMap.AutocompleteResult.prototype.getPoi = function(i) {};


/**
 * @return {number}
 */
BMap.AutocompleteResult.prototype.getNumPois = function() {};


/**
 * @type {string}
 */
BMap.AutocompleteResult.prototype.keyword;


/**
 * @interface
 */
BMap.AutocompleteResultPoi = function() {};


/**
 * @type {string}
 */
BMap.AutocompleteResultPoi.prototype.province;


/**
 * @type {string}
 */
BMap.AutocompleteResultPoi.prototype.city;


/**
 * @type {string}
 */
BMap.AutocompleteResultPoi.prototype.district;


/**
 * @type {string}
 */
BMap.AutocompleteResultPoi.prototype.street;


/**
 * @type {string}
 */
BMap.AutocompleteResultPoi.prototype.streetNumber;


/**
 * @type {string}
 */
BMap.AutocompleteResultPoi.prototype.business;



/**
 * @constructor
 */
BMap.Boundary = function() {};


/**
 * @param {string} name
 * @param {Function} callback
 */
BMap.Boundary.prototype.get = function(name,callback) {};



/**
 * @param {BMap.Point} sw
 * @param {BMap.Point} ne
 * @constructor
 */
BMap.Bounds = function(sw, ne) {};


/**
 * @param {BMap.Bounds} other
 * @return {boolean}
 */
BMap.Bounds.prototype.equals = function(other) {};


/**
 * @param {BMap.Point} point
 * @return {boolean}
 */
BMap.Bounds.prototype.containsPoint = function(point) {};


/**
 * @param {BMap.Bounds} bounds
 * @return {boolean}
 */
BMap.Bounds.prototype.containsBounds = function(bounds) {};


/**
 * @param {BMap.Bounds} other
 * @return {BMap.Bounds}
 */
BMap.Bounds.prototype.intersects = function(other) {};


/**
 * @param {BMap.Point} point
 */
BMap.Bounds.prototype.extend = function(point) {};


/**
 * @return {BMap.Point}
 */
BMap.Bounds.prototype.getCenter = function() {};


/**
 * @return {boolean}
 */
BMap.Bounds.prototype.isEmpty = function() {};


/**
 * @return {BMap.Point}
 */
BMap.Bounds.prototype.getSouthWest = function() {};


/**
 * @return {BMap.Point}
 */
BMap.Bounds.prototype.getNorthEast = function() {};


/**
 * @return {BMap.Point}
 */
BMap.Bounds.prototype.toSpan = function() {};



/**
 * @constructor
 */
BMap.BusLine = function() {};


/**
 * @return {number}
 */
BMap.BusLine.prototype.getNumBusStations = function() {};


/**
 * @param {number} i
 * @return {BMap.BusStation}
 */
BMap.BusLine.prototype.getBusStation = function(i) {};


/**
 * @return {Array<BMap.Point>}
 */
BMap.BusLine.prototype.getPath = function() {};


/**
 * @return {BMap.Polyline}
 */
BMap.BusLine.prototype.getPolyline = function() {};


/**
 * @type {string}
 */
BMap.BusLine.prototype.name;


/**
 * @type {string}
 */
BMap.BusLine.prototype.startTime;


/**
 * @type {string}
 */
BMap.BusLine.prototype.endTime;


/**
 * @type {string}
 */
BMap.BusLine.prototype.company;



/**
 * @param {BMap.Map|BMap.Point|string} location
 * @param {(BMap.BusLineSearchOptions|Object.<string>)=} opt_busLineSearchOptions
 * @constructor
 */
BMap.BusLineSearch = function(location, opt_busLineSearchOptions) {};


/**
 * @param {string} keyword
 */
BMap.BusLineSearch.prototype.getBusList = function(keyword) {};


/**
 * @param {BMap.BusListItem} busListItem
 */
BMap.BusLineSearch.prototype.getBusLine = function(busListItem) {};


/**
 *
 */
BMap.BusLineSearch.prototype.clearResults = function() {};


/**
 *
 */
BMap.BusLineSearch.prototype.enableAutoViewport = function() {};


/**
 *
 */
BMap.BusLineSearch.prototype.disableAutoViewport = function() {};


/**
 * @param {BMap.Map|BMap.Point|string} location
 */
BMap.BusLineSearch.prototype.setLocation = function(location) {};


/**
 * @return {BMap.StatusCode}
 */
BMap.BusLineSearch.prototype.getStatus = function() {};


/**
 * @return {string}
 */
BMap.BusLineSearch.prototype.toString = function() {};


/**
 * @param {Function} callback
 */
BMap.BusLineSearch.prototype.setGetBusListCompleteCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.BusLineSearch.prototype.setGetBusLineCompleteCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.BusLineSearch.prototype.setBusListHtmlSetCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.BusLineSearch.prototype.setBusLineHtmlSetCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.BusLineSearch.prototype.setPolylinesSetCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.BusLineSearch.prototype.setMarkersSetCallback = function(callback) {};



/**
 * @interface
 */
BMap.BusLineSearchOptions = function() {};


/**
 * @type {BMap.RenderOptions}
 */
BMap.BusLineSearchOptions.prototype.renderOptions;


/**
 * @type {Function}
 */
BMap.BusLineSearchOptions.prototype.onGetBusListComplete;


/**
 * @type {Function}
 */
BMap.BusLineSearchOptions.prototype.onGetBusLineComplete;


/**
 * @type {Function}
 */
BMap.BusLineSearchOptions.prototype.onBusListHtmlSet;


/**
 * @type {Function}
 */
BMap.BusLineSearchOptions.prototype.onBusLineHtmlSet;


/**
 * @type {Function}
 */
BMap.BusLineSearchOptions.prototype.onPolylinesSet;


/**
 * @type {Function}
 */
BMap.BusLineSearchOptions.prototype.onMarkersSet;



/**
 * @constructor
 */
BMap.BusListItem = function() {};


/**
 * @type {string}
 */
BMap.BusListItem.prototype.name;



/**
 * @constructor
 */
BMap.BusListResult = function() {};


/**
 * @return {number}
 */
BMap.BusListResult.prototype.getNumBusList = function() {};


/**
 * @param {number} i
 * @return {BMap.BusListItem}
 */
BMap.BusListResult.prototype.getBusListItem = function(i) {};


/**
 * @type {string}
 */
BMap.BusListResult.prototype.keyword;


/**
 * @type {string}
 */
BMap.BusListResult.prototype.city;


/**
 * @type {string}
 */
BMap.BusListResult.prototype.moreResultsUrl;



/**
 * @constructor
 */
BMap.BusStation = function() {};


/**
 * @type {string}
 */
BMap.BusStation.prototype.name;


/**
 * @type {BMap.Point}
 */
BMap.BusStation.prototype.position;



/**
 * @param {BMap.Point} center
 * @param {number} radius
 * @param {(BMap.CircleOptions|Object.<string>)=} opt_circleOptions
 * @constructor
 */
BMap.Circle = function(center, radius, opt_circleOptions) {};


/**
 * @param {BMap.Point} center
 */
BMap.Circle.prototype.setCenter = function(center) {};


/**
 * @return {BMap.Point}
 */
BMap.Circle.prototype.getCenter = function() {};


/**
 * @param {number} radius
 */
BMap.Circle.prototype.setRadius = function(radius) {};


/**
 * @return {number}
 */
BMap.Circle.prototype.getRadius = function() {};


/**
 * @return {BMap.Bounds}
 */
BMap.Circle.prototype.getBounds = function() {};


/**
 * @param {string} color
 */
BMap.Circle.prototype.setStrokeColor = function(color) {};


/**
 * @return {string}
 */
BMap.Circle.prototype.getStrokeColor = function() {};


/**
 * @param {string} color
 */
BMap.Circle.prototype.setFillColor = function(color) {};


/**
 * @return {string}
 */
BMap.Circle.prototype.getFillColor = function() {};


/**
 * @param {number} opacity
 */
BMap.Circle.prototype.setStrokeOpacity = function(opacity) {};


/**
 * @return {number}
 */
BMap.Circle.prototype.getStrokeOpacity = function() {};


/**
 * @param {number} opacity
 */
BMap.Circle.prototype.setFillOpacity = function(opacity) {};


/**
 * @return {number}
 */
BMap.Circle.prototype.getFillOpacity = function() {};


/**
 * @param {number} weight
 */
BMap.Circle.prototype.setStrokeWeight = function(weight) {};


/**
 * @return {number}
 */
BMap.Circle.prototype.getStrokeWeight = function() {};


/**
 * @param {string} style
 */
BMap.Circle.prototype.setStrokeStyle = function(style) {};


/**
 * @return {string}
 */
BMap.Circle.prototype.getStrokeStyle = function() {};


/**
 *
 */
BMap.Circle.prototype.enableEditing = function() {};


/**
 *
 */
BMap.Circle.prototype.disableEditing = function() {};


/**
 *
 */
BMap.Circle.prototype.enableMassClear = function() {};


/**
 *
 */
BMap.Circle.prototype.disableMassClear = function() {};


/**
 * @return {BMap.Map}
 */
BMap.Circle.prototype.getMap = function() {};


/**
 * @param {string} event
 * @param {Function} handler
 */
BMap.Circle.prototype.addEventListener = function(event, handler) {};


/**
 * @param {string} event
 * @param {Function} handler
 */
BMap.Circle.prototype.removeEventListener = function(event, handler) {};



/**
 * @interface
 */
BMap.CircleOptions = function() {};


/**
 * @type {string}
 */
BMap.CircleOptions.prototype.strokeColor;


/**
 * @type {string}
 */
BMap.CircleOptions.prototype.fillColor;


/**
 * @type {number}
 */
BMap.CircleOptions.prototype.strokeWeight;


/**
 * @type {number}
 */
BMap.CircleOptions.prototype.strokeOpacity;


/**
 * @type {number}
 */
BMap.CircleOptions.prototype.fillOpacity;


/**
 * @type {string}
 */
BMap.CircleOptions.prototype.strokeStyle;


/**
 * @type {boolean}
 */
BMap.CircleOptions.prototype.enableMassClear;


/**
 * @type {boolean}
 */
BMap.CircleOptions.prototype.enableEditing;


/**
 * @type {boolean}
 */
BMap.CircleOptions.prototype.enableClicking;



/**
 * @constructor
 */
BMap.ContextMenu = function() {};


/**
 * @param {BMap.MenuItem} item
 */
BMap.ContextMenu.prototype.addItem = function(item) {};


/**
 * @param {number} index
 * @return {BMap.MenuItem}
 */
BMap.ContextMenu.prototype.getItem = function(index) {};


/**
 * @param {BMap.MenuItem} item
 */
BMap.ContextMenu.prototype.removeItem = function(item) {};


/**
 *
 */
BMap.ContextMenu.prototype.addSeparator = function() {};


/**
 * @param {number} index
 */
BMap.ContextMenu.prototype.removeSeparator = function(index) {};



/**
 * @constructor
 */
BMap.Control = function() {};


/**
 * @type {BMap.ControlAnchor}
 */
BMap.Control.prototype.defaultAnchor;


/**
 * @type {BMap.Size}
 */
BMap.Control.prototype.defaultOffset;


/**
 * @param {BMap.Map} map
 */
BMap.Control.prototype.initialize = function(map) {};


/**
 * @param {BMap.ControlAnchor} anchor
 */
BMap.Control.prototype.setAnchor = function(anchor) {};


/**
 * @return {BMap.ControlAnchor}
 */
BMap.Control.prototype.getAnchor = function() {};


/**
 * @param {BMap.Size} offset
 */
BMap.Control.prototype.setOffset = function(offset) {};


/**
 * @return {BMap.Size}
 */
BMap.Control.prototype.getOffset = function() {};


/**
 *
 */
BMap.Control.prototype.show = function() {};


/**
 *
 */
BMap.Control.prototype.hide = function() {};


/**
 * @return {boolean}
 */
BMap.Control.prototype.isVisible = function() {};



/**
 * @constructor
 */
BMap.Copyright = function() {};


/**
 * @type {number}
 */
BMap.Copyright.prototype.id;


/**
 * @type {string}
 */
BMap.Copyright.prototype.content;


/**
 * @type {BMap.Bounds}
 */
BMap.Copyright.prototype.bounds;



/**
 * @param {(BMap.CopyrightControlOptions|Object.<string>)=} opt_copyrightControlOptions
 * @constructor
 * @extends {BMap.Control}
 */
BMap.CopyrightControl = function(opt_copyrightControlOptions) {};


/**
 * @param {BMap.Copyright} copyright
 */
BMap.CopyrightControl.prototype.addCopyright = function(copyright) {};


/**
 * @param {number} id
 */
BMap.CopyrightControl.prototype.removeCopyright = function(id) {};


/**
 * @param {number} id
 * @return {BMap.Copyright}
 */
BMap.CopyrightControl.prototype.getCopyright = function(id) {};


/**
 * @return {Array<BMap.Copyright>}
 */
BMap.CopyrightControl.prototype.getCopyrightCollection = function() {};



/**
 * @interface
 */
BMap.CopyrightControlOptions = function() {};


/**
 * @type {BMap.ControlAnchor}
 */
BMap.CopyrightControlOptions.prototype.anchor;


/**
 * @type {BMap.Size}
 */
BMap.CopyrightControlOptions.prototype.offset;



/**
 * @constructor
 */
BMap.CustomData = function() {};


/**
 * @type {number}
 */
BMap.CustomData.prototype.geotableId;


/**
 * @type {string}
 */
BMap.CustomData.prototype.tags;


/**
 * @type {string}
 */
BMap.CustomData.prototype.filter;



/**
 * @param {(BMap.CustomLayerOptions|Object.<string>)=} opt_customLayerOptions
 * @constructor
 */
BMap.CustomLayer = function(opt_customLayerOptions) {};


/**
 * @interface
 */
BMap.CustomLayerOptions = function() {};


/**
 * @type {string}
 */
BMap.CustomLayerOptions.prototype.databoxId;


/**
 * @type {string}
 */
BMap.CustomLayerOptions.prototype.geotableId;


/**
 * @type {string}
 */
BMap.CustomLayerOptions.prototype.q;


/**
 * @type {string}
 */
BMap.CustomLayerOptions.prototype.tags;


/**
 * @type {string}
 */
BMap.CustomLayerOptions.prototype.filter;


/**
 * @type {BMap.PointDensityType}
 */
BMap.CustomLayerOptions.prototype.pointDensityType;



/**
 * @param {BMap.Map|BMap.Point|string} location
 * @param {(BMap.DrivingRouteOptions|Object.<string>)=} opt_drivingRouteOptions
 * @constructor
 */
BMap.DrivingRoute = function(location, opt_drivingRouteOptions) {};


/**
 * @param {string|BMap.Point|BMap.LocalResultPoi} start
 * @param {string|BMap.Point|BMap.LocalResultPoi} end
 * @param {(Object.<string>)=} opt_options
 */
BMap.DrivingRoute.prototype.search = function(start, end, opt_options) {};


/**
 * @return {BMap.DrivingRouteResult}
 */
BMap.DrivingRoute.prototype.getResults = function() {};


/**
 *
 */
BMap.DrivingRoute.prototype.clearResults = function() {};


/**
 *
 */
BMap.DrivingRoute.prototype.enableAutoViewport = function() {};


/**
 *
 */
BMap.DrivingRoute.prototype.disableAutoViewport = function() {};


/**
 * @param {string|BMap.Point|string} location
 */
BMap.DrivingRoute.prototype.setLocation = function(location) {};


/**
 * @param {BMap.DrivingPolicy} policy
 */
BMap.DrivingRoute.prototype.setPolicy = function(policy) {};


/**
 * @param {Function} callback
 */
BMap.DrivingRoute.prototype.setSearchCompleteCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.DrivingRoute.prototype.setMarkersSetCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.DrivingRoute.prototype.setInfoHtmlSetCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.DrivingRoute.prototype.setPolylinesSetCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.DrivingRoute.prototype.setResultsHtmlSetCallback = function(callback) {};


/**
 * @return {BMap.StatusCode}
 */
BMap.DrivingRoute.prototype.getStatus = function() {};


/**
 * @return {string}
 */
BMap.DrivingRoute.prototype.toString = function() {};



/**
 * @interface
 */
BMap.DrivingRouteOptions = function() {};


/**
 * @type {BMap.RenderOptions}
 */
BMap.DrivingRouteOptions.prototype.renderOptions;


/**
 * @type {BMap.DrivingPolicy}
 */
BMap.DrivingRouteOptions.prototype.policy;


/**
 * @type {Function}
 */
BMap.DrivingRouteOptions.prototype.onSearchComplete;


/**
 * @type {Function}
 */
BMap.DrivingRouteOptions.prototype.onMarkersSet;


/**
 * @type {Function}
 */
BMap.DrivingRouteOptions.prototype.onInfoHtmlSet;


/**
 * @type {Function}
 */
BMap.DrivingRouteOptions.prototype.onPolylinesSet;


/**
 * @type {Function}
 */
BMap.DrivingRouteOptions.prototype.onResultsHtmlSet;



/**
 * @constructor
 */
BMap.DrivingRouteResult = function() {};


/**
 * @return {BMap.LocalResultPoi}
 */
BMap.DrivingRouteResult.prototype.getStart = function() {};


/**
 * @return {BMap.LocalResultPoi}
 */
BMap.DrivingRouteResult.prototype.getEnd = function() {};


/**
 * @return {number}
 */
BMap.DrivingRouteResult.prototype.getNumPlans = function() {};


/**
 * @param {number} i
 * @return {BMap.RoutePlan}
 */
BMap.DrivingRouteResult.prototype.getPlan = function(i) {};


/**
 * @type {BMap.DrivingPolicy}
 */
BMap.DrivingRouteResult.prototype.policy;


/**
 * @type {string}
 */
BMap.DrivingRouteResult.prototype.city;


/**
 * @type {string}
 */
BMap.DrivingRouteResult.prototype.moreResultsUrl;


/**
 * @type {BMap.TaxiFare}
 */
BMap.DrivingRouteResult.prototype.taxiFare;



/**
 * @constructor
 */
BMap.Geocoder = function() {};


/**
 * @param {string} address
 * @param {Function} callback
 * @param {string} city
 */
BMap.Geocoder.prototype.getPoint = function(address, callback, city) {};


/**
 * @param {BMap.Point} point
 * @param {Function} callback
 * @param {(BMap.LocationOptions|Object.<string>)=} opt_locationOptions
 */
BMap.Geocoder.prototype.getLocation = function(point, callback, opt_locationOptions) {};



/**
 * @constructor
 */
BMap.GeocoderResult = function() {};


/**
 * @type {BMap.Point}
 */
BMap.GeocoderResult.prototype.point;


/**
 * @type {string}
 */
BMap.GeocoderResult.prototype.address;


/**
 * @type {BMap.AddressComponent}
 */
BMap.GeocoderResult.prototype.addressComponents;


/**
 * @type {Array<BMap.LocalResultPoi>}
 */
BMap.GeocoderResult.prototype.surroundingPois;


/**
 * @type {string}
 */
BMap.GeocoderResult.prototype.business;



/**
 * @constructor
 */
BMap.Geolocation = function() {};


/**
 * @param {Function} callback
 * @param {(BMap.PositionOptions|Object.<string>)=} opt_positionOptions
 */
BMap.Geolocation.prototype.getCurrentPosition = function(callback, opt_positionOptions) {};


/**
 * @return {BMap.StatusCode}
 */
BMap.Geolocation.prototype.getStatus = function() {};



/**
 * @constructor
 * @extends {BMap.Control}
 */
BMap.GeolocationControl = function() {};


/**
 *
 */
BMap.GeolocationControl.prototype.location = function() {};


/**
 * @return {BMap.AddressComponent}
 */
BMap.GeolocationControl.prototype.getAddressComponent = function() {};



/**
 * @interface
 */
BMap.GeolocationControlOptions = function() {};


/**
 * @type {BMap.ControlAnchor}
 */
BMap.GeolocationControlOptions.prototype.anchor;


/**
 * @type {BMap.Size}
 */
BMap.GeolocationControlOptions.prototype.offset;


/**
 * @type {boolean}
 */
BMap.GeolocationControlOptions.prototype.showAddressBar;


/**
 * @type {boolean}
 */
BMap.GeolocationControlOptions.prototype.enableAutoLocation;


/**
 * @type {BMap.Icon}
 */
BMap.GeolocationControlOptions.prototype.locationIcon;



/**
 * @constructor
 */
BMap.GeolocationResult = function() {};


/**
 * @type {BMap.Point}
 */
BMap.GeolocationResult.prototype.point;


/**
 * @type {number}
 */
BMap.GeolocationResult.prototype.accuracy;



/**
 * @param {BMap.Bounds} bounds
 * @param {(BMap.GroundOverlayOptions|Object.<string>)=} opt_groundOverlayOptions
 * @constructor
 */
BMap.GroundOverlay = function(bounds, opt_groundOverlayOptions) {};


/**
 * @param {BMap.Bounds} bounds
 */
BMap.GroundOverlay.prototype.setBounds = function(bounds) {};


/**
 * @return {BMap.Bounds}
 */
BMap.GroundOverlay.prototype.getBounds = function() {};


/**
 * @param {number} opacity
 */
BMap.GroundOverlay.prototype.setOpacity = function(opacity) {};


/**
 * @return {number}
 */
BMap.GroundOverlay.prototype.getOpacity = function() {};


/**
 * @param {string} url
 */
BMap.GroundOverlay.prototype.setImageURL = function(url) {};


/**
 * @return {string}
 */
BMap.GroundOverlay.prototype.getImageURL = function() {};


/**
 * @param {number} level
 */
BMap.GroundOverlay.prototype.setDisplayOnMinLevel = function(level) {};


/**
 * @return {number}
 */
BMap.GroundOverlay.prototype.getDisplayOnMinLevel = function() {};


/**
 * @param {number} level
 */
BMap.GroundOverlay.prototype.setDispalyOnMaxLevel = function(level) {};


/**
 * @return {number}
 */
BMap.GroundOverlay.prototype.getDispalyOnMaxLevel = function() {};



/**
 * @interface
 */
BMap.GroundOverlayOptions = function() {};


/**
 * @type {number}
 */
BMap.GroundOverlayOptions.prototype.opacity;


/**
 * @type {string}
 */
BMap.GroundOverlayOptions.prototype.imageURL;


/**
 * @type {number}
 */
BMap.GroundOverlayOptions.prototype.displayOnMinLevel;


/**
 * @type {number}
 */
BMap.GroundOverlayOptions.prototype.displayOnMaxLevel;



/**
 * @param {BMap.Point} position
 * @param {(BMap.HotspotOptions|Object.<string>)=} opt_hotspotOptions
 * @constructor
 */
BMap.Hotspot = function(position, opt_hotspotOptions) {};


/**
 * @return {BMap.Point}
 */
BMap.Hotspot.prototype.getPosition = function() {};


/**
 * @param {BMap.Point} position
 */
BMap.Hotspot.prototype.setPosition = function(position) {};


/**
 * @return {string}
 */
BMap.Hotspot.prototype.getText = function() {};


/**
 * @param {string} text
 */
BMap.Hotspot.prototype.setText = function(text) {};


/**
 * @return {*}
 */
BMap.Hotspot.prototype.getUserData = function() {};


/**
 * @param {*} data
 */
BMap.Hotspot.prototype.setUserData = function(data) {};



/**
 * @interface
 */
BMap.HotspotOptions = function() {};


/**
 * @type {string}
 */
BMap.HotspotOptions.prototype.text;


/**
 * @type {Array<number>}
 */
BMap.HotspotOptions.prototype.offsets;


/**
 * @type {*}
 */
BMap.HotspotOptions.prototype.userData;


/**
 * @type {number}
 */
BMap.HotspotOptions.prototype.minZoom;


/**
 * @type {number}
 */
BMap.HotspotOptions.prototype.maxZoom;



/**
 * @param {Array<BMap.Point>} points
 * @param {(BMap.IconOptions|Object.<string>)=} opt_iconOptions
 * @constructor
 */
BMap.Icon = function(points, opt_iconOptions) {};


/**
 * @type {BMap.Size}
 */
BMap.Icon.prototype.anchor;


/**
 * @type {BMap.Size}
 */
BMap.Icon.prototype.size;


/**
 * @type {BMap.Size}
 */
BMap.Icon.prototype.imageOffset;


/**
 * @type {BMap.Size}
 */
BMap.Icon.prototype.imageSize;


/**
 * @type {string}
 */
BMap.Icon.prototype.imageUrl;


/**
 * @type {BMap.Size}
 */
BMap.Icon.prototype.infoWindowAnchor;


/**
 * @type {string}
 */
BMap.Icon.prototype.printImageUrl;


/**
 * @param {string} imageUrl
 */
BMap.Icon.prototype.setImageUrl = function(imageUrl) {};


/**
 * @param {BMap.Size} size
 */
BMap.Icon.prototype.setSize = function(size) {};


/**
 * @param {BMap.Size} offset
 */
BMap.Icon.prototype.setImageSize = function(offset) {};


/**
 * @param {BMap.Size} anchor
 */
BMap.Icon.prototype.setAnchor = function(anchor) {};


/**
 * @param {BMap.Size} offset
 */
BMap.Icon.prototype.setImageOffset = function(offset) {};


/**
 * @param {BMap.Size} anchor
 */
BMap.Icon.prototype.setInfoWindowAnchor = function(anchor) {};


/**
 * @param {string} url
 */
BMap.Icon.prototype.setPrintImageUrl = function(url) {};



/**
 * @interface
 */
BMap.IconOptions = function() {};


/**
 * @type {BMap.Size}
 */
BMap.IconOptions.prototype.anchor;


/**
 * @type {BMap.Size}
 */
BMap.IconOptions.prototype.imageOffset;


/**
 * @type {BMap.Size}
 */
BMap.IconOptions.prototype.infoWindowAnchor;


/**
 * @type {string}
 */
BMap.IconOptions.prototype.printImageUrl;



/**
 * @param {BMap.Symbol} symbol
 * @param {string=} opt_offset
 * @param {string=} opt_repeat
 * @param {boolean=} opt_fixedRotation
 * @constructor
 */
BMap.IconSequence = function(symbol, opt_offset, opt_repeat, opt_fixedRotation) {};



/**
 * @param {string|HTMLElement} content
 * @param {(BMap.InfoWindowOptions|Object.<string>)=} opt_infoWindowOptions
 * @constructor
 */
BMap.InfoWindow = function(content, opt_infoWindowOptions) {};


/**
 * @param {number} width
 */
BMap.InfoWindow.prototype.setWidth = function(width) {};


/**
 * @param {number} height
 */
BMap.InfoWindow.prototype.setHeight = function(height) {};


/**
 *
 */
BMap.InfoWindow.prototype.redraw = function() {};


/**
 * @param {string|HTMLElement} title
 */
BMap.InfoWindow.prototype.setTitle = function(title) {};


/**
 * @return {string}
 */
BMap.InfoWindow.prototype.getTitle = function() {};


/**
 * @param {string|HTMLElement} content
 */
BMap.InfoWindow.prototype.setContent = function(content) {};


/**
 * @return {string|HTMLElement}
 */
BMap.InfoWindow.prototype.getContent = function() {};


/**
 * @return {BMap.Point}
 */
BMap.InfoWindow.prototype.getPosition = function() {};


/**
 *
 */
BMap.InfoWindow.prototype.enableMaximize = function() {};


/**
 *
 */
BMap.InfoWindow.prototype.disableMaximize = function() {};


/**
 * @return {boolean}
 */
BMap.InfoWindow.prototype.isOpen = function() {};


/**
 * @param {string} content
 */
BMap.InfoWindow.prototype.setMaxContent = function(content) {};


/**
 *
 */
BMap.InfoWindow.prototype.maximize = function() {};


/**
 *
 */
BMap.InfoWindow.prototype.restore = function() {};


/**
 *
 */
BMap.InfoWindow.prototype.enableAutoPan = function() {};


/**
 *
 */
BMap.InfoWindow.prototype.disableAutoPan = function() {};


/**
 *
 */
BMap.InfoWindow.prototype.enableCloseOnClick = function() {};


/**
 *
 */
BMap.InfoWindow.prototype.disableCloseOnClick = function() {};


/**
 * @param {string} event
 * @param {Function} handler
 */
BMap.InfoWindow.prototype.addEventListener = function(event,handler) {};


/**
 * @param {string} event
 * @param {Function} handler
 */
BMap.InfoWindow.prototype.removeEventListener = function(event,handler) {};



/**
 * @interface
 */
BMap.InfoWindowOptions = function() {};


/**
 * @type {number}
 */
BMap.InfoWindowOptions.prototype.width;


/**
 * @type {number}
 */
BMap.InfoWindowOptions.prototype.height;


/**
 * @type {number}
 */
BMap.InfoWindowOptions.prototype.maxWidth;


/**
 * @type {BMap.Size}
 */
BMap.InfoWindowOptions.prototype.offset;


/**
 * @type {string}
 */
BMap.InfoWindowOptions.prototype.title;


/**
 * @type {boolean}
 */
BMap.InfoWindowOptions.prototype.enableAutoPan;


/**
 * @type {boolean}
 */
BMap.InfoWindowOptions.prototype.enableCloseOnClick;


/**
 * @type {boolean}
 */
BMap.InfoWindowOptions.prototype.enableMessage;


/**
 * @type {string}
 */
BMap.InfoWindowOptions.prototype.message;



/**
 * @param {string} content
 * @param {(BMap.LabelOptions|Object.<string>)=} opt_labelOptions
 * @constructor
 */
BMap.Label = function(content, opt_labelOptions) {};


/**
 * @param {Object} styles
 */
BMap.Label.prototype.setStyle = function(styles) {};


/**
 * @param {string} content
 */
BMap.Label.prototype.setContent = function(content) {};


/**
 * @param {BMap.Point} position
 */
BMap.Label.prototype.setPosition = function(position) {};


/**
 * @return {BMap.Point}
 */
BMap.Label.prototype.getPosition = function() {};


/**
 * @param {BMap.Size} offset
 */
BMap.Label.prototype.setOffset = function(offset) {};


/**
 * @return {BMap.Size}
 */
BMap.Label.prototype.getOffset = function() {};


/**
 * @param {string} title
 */
BMap.Label.prototype.setTitle = function(title) {};


/**
 * @return {string}
 */
BMap.Label.prototype.getTitle = function() {};


/**
 *
 */
BMap.Label.prototype.enableMassClear = function() {};


/**
 *
 */
BMap.Label.prototype.disableMassClear = function() {};


/**
 * @param {number} zIndex
 */
BMap.Label.prototype.setZIndex = function(zIndex) {};


/**
 * @return {BMap.Map}
 */
BMap.Label.prototype.getMap = function() {};


/**
 * @param {string} event
 * @param {Function} handler
 */
BMap.Label.prototype.addEventListener = function(event,handler) {};


/**
 * @param {string} event
 * @param {Function} handler
 */
BMap.Label.prototype.removeEventListener = function(event,handler) {};



/**
 * @interface
 */
BMap.LabelOptions = function() {};


/**
 * @type {BMap.Size}
 */
BMap.LabelOptions.prototype.offset;


/**
 * @type {BMap.Point}
 */
BMap.LabelOptions.prototype.position;


/**
 * @type {boolean}
 */
BMap.LabelOptions.prototype.enableMassClear;



/**
 * @constructor
 */
BMap.Line = function() {};


/**
 * @return {number}
 */
BMap.Line.prototype.getNumViaStops = function() {};


/**
 * @return {BMap.LocalResultPoi}
 */
BMap.Line.prototype.getGetOnStop = function() {};


/**
 * @return {BMap.LocalResultPoi}
 */
BMap.Line.prototype.getGetOffStop = function() {};


/**
 * @type {string}
 */
BMap.Line.prototype.title;


/**
 * @type {BMap.LineType}
 */
BMap.Line.prototype.type;



/**
 * @param {(BMap.LocalCityOptions|Object.<string>)=} opt_localCityOptions
 * @constructor
 */
BMap.LocalCity = function(opt_localCityOptions) {};


/**
 * @param {Function} callback
 */
BMap.LocalCity.prototype.get = function(callback) {};



/**
 * @interface
 */
BMap.LocalCityOptions = function() {};


/**
 * @type {BMap.RenderOptions}
 */
BMap.LocalCityOptions.prototype.renderOptions;



/**
 * @constructor
 */
BMap.LocalCityResult = function() {};


/**
 * @type {BMap.Point}
 */
BMap.LocalCityResult.prototype.center;


/**
 * @type {number}
 */
BMap.LocalCityResult.prototype.level;


/**
 * @type {string}
 */
BMap.LocalCityResult.prototype.name;



/**
 * @interface
 * @extends {BMap.RenderOptions}
 */
BMap.LocalRenderOptions = function() {};



/**
 * @constructor
 */
BMap.LocalResult = function() {};


/**
 * @param {number} i
 * @return {BMap.LocalResultPoi}
 */
BMap.LocalResult.prototype.getPoi = function(i) {};


/**
 * @return {number}
 */
BMap.LocalResult.prototype.getCurrentNumPois = function() {};


/**
 * @return {number}
 */
BMap.LocalResult.prototype.getNumPois = function() {};


/**
 * @return {number}
 */
BMap.LocalResult.prototype.getNumPages = function() {};


/**
 * @return {number}
 */
BMap.LocalResult.prototype.getPageIndex = function() {};


/**
 * @return {Array<Object>}
 */
BMap.LocalResult.prototype.getCityList = function() {};


/**
 * @type {string}
 */
BMap.LocalResult.prototype.keyword;


/**
 * @type {BMap.LocalResultPoi}
 */
BMap.LocalResult.prototype.center;


/**
 * @type {number}
 */
BMap.LocalResult.prototype.radius;


/**
 * @type {BMap.Bounds}
 */
BMap.LocalResult.prototype.bounds;


/**
 * @type {string}
 */
BMap.LocalResult.prototype.city;


/**
 * @type {string}
 */
BMap.LocalResult.prototype.moreResultsUrl;


/**
 * @type {string}
 */
BMap.LocalResult.prototype.province;


/**
 * @type {string}
 */
BMap.LocalResult.prototype.suggestions;



/**
 * @constructor
 */
BMap.LocalResultPoi = function() {};


/**
 * @type {string}
 */
BMap.LocalResultPoi.prototype.title;


/**
 * @type {BMap.Point}
 */
BMap.LocalResultPoi.prototype.point;


/**
 * @type {string}
 */
BMap.LocalResultPoi.prototype.url;


/**
 * @type {string}
 */
BMap.LocalResultPoi.prototype.address;


/**
 * @type {string}
 */
BMap.LocalResultPoi.prototype.city;


/**
 * @type {string}
 */
BMap.LocalResultPoi.prototype.phoneNumber;


/**
 * @type {string}
 */
BMap.LocalResultPoi.prototype.postcode;


/**
 * @type {BMap.PoiType}
 */
BMap.LocalResultPoi.prototype.type;


/**
 * @type {boolean}
 */
BMap.LocalResultPoi.prototype.isAccurate;


/**
 * @type {Array<string>}
 */
BMap.LocalResultPoi.prototype.tags;


/**
 * @type {string}
 */
BMap.LocalResultPoi.prototype.province;


/**
 * @type {string}
 */
BMap.LocalResultPoi.prototype.detailUrl;



/**
 * @param {BMap.Map|BMap.Point|string} location
 * @param {(BMap.LocalSearchOptions|Object.<string>)=} opt_localSearchOptions
 * @constructor
 */
BMap.LocalSearch = function(location, opt_localSearchOptions) {};



/**
 * @param {string} keyword
 * @param {(Object.<string>)} opt_
 */
BMap.LocalSearch.prototype.search = function(keyword,opt_) {};


/**
 * @param {string|Array<string>} keyword
 * @param {BMap.Bounds} bounds
 * @param {(Object.<string>)=} opt_object
 */
BMap.LocalSearch.prototype.searchInBounds = function(keyword,bounds,opt_object) {};


/**
 * @param {string|Array<string>} keyword
 * @param {BMap.Point|BMap.LocalResultPoi|string} center
 * @param {number} radius
 * @param {(Object.<string>)=} opt_object
 */
BMap.LocalSearch.prototype.searchNearby = function(keyword,center,radius,opt_object) {};


/**
 * @return {BMap.LocalResult|Array<BMap.LocalResult>}
 */
BMap.LocalSearch.prototype.getResults = function() {};


/**
 *
 */
BMap.LocalSearch.prototype.clearResults = function() {};


/**
 * @param {number} page
 */
BMap.LocalSearch.prototype.gotoPage = function(page) {};


/**
 *
 */
BMap.LocalSearch.prototype.enableAutoViewport = function() {};


/**
 *
 */
BMap.LocalSearch.prototype.disableAutoViewport = function() {};


/**
 *
 */
BMap.LocalSearch.prototype.enableFirstResultSelection = function() {};


/**
 *
 */
BMap.LocalSearch.prototype.disableFirstResultSelection = function() {};


/**
 * @param {BMap.Map|BMap.Point|string} location
 */
BMap.LocalSearch.prototype.setLocation = function(location) {};


/**
 * @param {number} capacity
 */
BMap.LocalSearch.prototype.setPageCapacity = function(capacity) {};


/**
 * @return {number}
 */
BMap.LocalSearch.prototype.getPageCapacity = function() {};


/**
 * @param {Function} callback
 */
BMap.LocalSearch.prototype.setSearchCompleteCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.LocalSearch.prototype.setMarkersSetCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.LocalSearch.prototype.setInfoHtmlSetCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.LocalSearch.prototype.setResultsHtmlSetCallback = function(callback) {};


/**
 * @return {BMap.StatusCode}
 */
BMap.LocalSearch.prototype.getStatus = function() {};



/**
 * @interface
 */
BMap.LocalSearchOptions = function() {};


/**
 * @type {BMap.LocalRenderOptions}
 */
BMap.LocalSearchOptions.prototype.renderOptions;


/**
 * @type {Function}
 */
BMap.LocalSearchOptions.prototype.onMarkersSet;


/**
 * @type {Function}
 */
BMap.LocalSearchOptions.prototype.onInfoHtmlSet;


/**
 * @type {Function}
 */
BMap.LocalSearchOptions.prototype.onResultsHtmlSet;


/**
 * @type {number}
 */
BMap.LocalSearchOptions.prototype.pageCapacity;


/**
 * @type {Function}
 */
BMap.LocalSearchOptions.prototype.onSearchComplete;



/**
 * @interface
 */
BMap.LocationOptions = function() {};


/**
 * @type {number}
 */
BMap.LocationOptions.prototype.poiRadius;


/**
 * @type {number}
 */
BMap.LocationOptions.prototype.numPois;



/**
 * @param {string|HTMLElement} container
 * @param {(BMap.MapOptions|Object.<string>)=} opt_mapOptions
 * @constructor
 */
BMap.Map = function(container, opt_mapOptions) {};


/**
 *
 */
BMap.Map.prototype.enableDragging = function() {};


/**
 *
 */
BMap.Map.prototype.disableDragging = function() {};


/**
 *
 */
BMap.Map.prototype.enableScrollWheelZoom = function() {};


/**
 *
 */
BMap.Map.prototype.disableScrollWheelZoom = function() {};


/**
 *
 */
BMap.Map.prototype.enableDoubleClickZoom = function() {};


/**
 *
 */
BMap.Map.prototype.disableDoubleClickZoom = function() {};


/**
 *
 */
BMap.Map.prototype.enableKeyboard = function() {};


/**
 *
 */
BMap.Map.prototype.disableKeyboard = function() {};


/**
 *
 */
BMap.Map.prototype.enableInertialDragging = function() {};


/**
 *
 */
BMap.Map.prototype.disableInertialDragging = function() {};


/**
 *
 */
BMap.Map.prototype.enableContinuousZoom = function() {};


/**
 *
 */
BMap.Map.prototype.disableContinuousZoom = function() {};


/**
 *
 */
BMap.Map.prototype.enablePinchToZoom = function() {};


/**
 *
 */
BMap.Map.prototype.disablePinchToZoom = function() {};


/**
 *
 */
BMap.Map.prototype.enableAutoResize = function() {};


/**
 *
 */
BMap.Map.prototype.disableAutoResize = function() {};


/**
 * @param {string} cursor
 */
BMap.Map.prototype.setDefaultCursor = function(cursor) {};


/**
 * @return {string}
 */
BMap.Map.prototype.getDefaultCursor = function() {};


/**
 * @param {string} cursor
 */
BMap.Map.prototype.setDraggingCursor = function(cursor) {};


/**
 * @return {string}
 */
BMap.Map.prototype.getDraggingCursor = function() {};


/**
 * @param {number} zoom
 */
BMap.Map.prototype.setMinZoom = function(zoom) {};


/**
 * @param {number} zoom
 */
BMap.Map.prototype.setMaxZoom = function(zoom) {};


/**
 * @param {BMap.MapStyle} mapStyle
 */
BMap.Map.prototype.setMapStyle = function(mapStyle) {};


/**
 *
 */
BMap.Map.prototype.disable3DBuilding = function() {};


/**
 * @return {BMap.Bounds}
 */
BMap.Map.prototype.getBounds = function() {};


/**
 * @return {BMap.Point}
 */
BMap.Map.prototype.getCenter = function() {};


/**
 * @param {BMap.Point} start
 * @param {BMap.Point} end
 */
BMap.Map.prototype.getDistance = function(start,end) {};


/**
 * @return {BMap.MapType}
 */
BMap.Map.prototype.getMapType = function() {};


/**
 * @return {BMap.Size}
 */
BMap.Map.prototype.getSize = function() {};


/**
 * @param {Array<BMap.Point>} view
 * @param {(BMap.ViewportOptions|Object.<string>)=} opt_viewportOptions
 */
BMap.Map.prototype.getViewport = function(view,opt_viewportOptions) {};


/**
 * @return {number}
 */
BMap.Map.prototype.getZoom = function() {};


/**
 * @param {BMap.Point} center
 * @param {number} zoom
 */
BMap.Map.prototype.centerAndZoom = function(center,zoom) {};


/**
 * @param {BMap.Point} center
 * @param {(BMap.PanOptions|Object.<string>)=} opt_
 */
BMap.Map.prototype.panTo = function(center,opt_) {};


/**
 * @param {number} x
 * @param {number} y
 * @param {(BMap.PanOptions|Object.<string>)=} opt_
 */
BMap.Map.prototype.panBy = function(x,y,opt_) {};


/**
 *
 */
BMap.Map.prototype.reset = function() {};


/**
 * @param {BMap.Point|string} center
 */
BMap.Map.prototype.setCenter = function(center) {};


/**
 * @param {string} city
 */
BMap.Map.prototype.setCurrentCity = function(city) {};


/**
 * @param {BMap.MapType} mapType
 */
BMap.Map.prototype.setMapType = function(mapType) {};


/**
 * @param {Array<BMap.Point>|BMap.Viewport} view
 * @param {(BMap.ViewportOptions|Object.<string>)=} opt_viewportOptions
 */
BMap.Map.prototype.setViewport = function(view,opt_viewportOptions) {};


/**
 * @param {number} zoom
 */
BMap.Map.prototype.zoomTo = function(zoom) {};


/**
 * @param {number} zoom
 */
BMap.Map.prototype.setZoom = function(zoom) {};


/**
 *
 */
BMap.Map.prototype.highResolutionEnabled = function() {};


/**
 *
 */
BMap.Map.prototype.zoomIn = function() {};


/**
 *
 */
BMap.Map.prototype.zoomOut = function() {};


/**
 * @param {BMap.Hotspot} hotspot
 */
BMap.Map.prototype.addHotspot = function(hotspot) {};


/**
 * @param {BMap.Hotspot} hotspot
 */
BMap.Map.prototype.removeHotspot = function(hotspot) {};


/**
 *
 */
BMap.Map.prototype.clearHotspots = function() {};


/**
 * @param {BMap.Control} control
 */
BMap.Map.prototype.addControl = function(control) {};


/**
 * @param {BMap.Control} control
 */
BMap.Map.prototype.removeControl = function(control) {};


/**
 * @return {HTMLElement}
 */
BMap.Map.prototype.getContainer = function() {};


/**
 * @param {BMap.ContextMenu} menu
 */
BMap.Map.prototype.addContextMenu = function(menu) {};


/**
 * @param {BMap.ContextMenu} menu
 */
BMap.Map.prototype.removeContextMenu = function(menu) {};


/**
 * @param {BMap.Overlay} overlay
 */
BMap.Map.prototype.addOverlay = function(overlay) {};


/**
 * @param {BMap.Overlay} overlay
 */
BMap.Map.prototype.removeOverlay = function(overlay) {};


/**
 *
 */
BMap.Map.prototype.clearOverlays = function() {};


/**
 * @param {BMap.InfoWindow} infoWnd
 * @param {BMap.Point} point
 */
BMap.Map.prototype.openInfoWindow = function(infoWnd,point) {};


/**
 *
 */
BMap.Map.prototype.closeInfoWindow = function() {};


/**
 * @param {BMap.Point} point
 */
BMap.Map.prototype.pointToOverlayPixel = function(point) {};


/**
 * @param {BMap.Pixel} pixel
 */
BMap.Map.prototype.overlayPixelToPoint = function(pixel) {};


/**
 * @return {BMap.InfoWindow}
 */
BMap.Map.prototype.getInfoWindow = function() {};


/**
 * @return {Array<BMap.Overlay>}
 */
BMap.Map.prototype.getOverlays = function() {};


/**
 * @return {BMap.MapPanes}
 */
BMap.Map.prototype.getPanes = function() {};


/**
 * @param {BMap.TileLayer|BMap.CustomLayer} tileLayer
 */
BMap.Map.prototype.addTileLayer = function(tileLayer) {};


/**
 * @param {BMap.TileLayer|BMap.CustomLayer} tilelayer
 */
BMap.Map.prototype.removeTileLayer = function(tilelayer) {};


/**
 * @param {string} mapType
 */
BMap.Map.prototype.getTileLayer = function(mapType) {};


/**
 * @param {BMap.Pixel} pixel
 */
BMap.Map.prototype.pixelToPoint = function(pixel) {};


/**
 * @param {BMap.Point} point
 */
BMap.Map.prototype.pointToPixel = function(point) {};


/**
 * @return {BMap.Panorama}
 */
BMap.Map.prototype.getPanorama = function() {};


/**
 * @param {BMap.Panorama} pano
 */
BMap.Map.prototype.setPanorama = function(pano) {};


/**
 * @interface
 */
BMap.MapOptions = function() {};


/**
 * @type {number}
 */
BMap.MapOptions.prototype.minZoom;


/**
 * @type {number}
 */
BMap.MapOptions.prototype.maxZoom;


/**
 * @type {BMap.MapType}
 */
BMap.MapOptions.prototype.mapType;


/**
 * @type {boolean}
 */
BMap.MapOptions.prototype.enableHighResolution;


/**
 * @type {boolean}
 */
BMap.MapOptions.prototype.enableAutoResize;


/**
 * @type {boolean}
 */
BMap.MapOptions.prototype.enableMapClick;



/**
 * @constructor
 */
BMap.MapPanes = function() {};


/**
 * @type {HTMLElement}
 */
BMap.MapPanes.prototype.floatPane;


/**
 * @type {HTMLElement}
 */
BMap.MapPanes.prototype.markerMouseTarget;


/**
 * @type {HTMLElement}
 */
BMap.MapPanes.prototype.floatShadow;


/**
 * @type {HTMLElement}
 */
BMap.MapPanes.prototype.labelPane;


/**
 * @type {HTMLElement}
 */
BMap.MapPanes.prototype.markerPane;


/**
 * @type {HTMLElement}
 */
BMap.MapPanes.prototype.markerShadow;


/**
 * @type {HTMLElement}
 */
BMap.MapPanes.prototype.mapPane;



/**
 * @constructor
 */
BMap.MapStyle = function() {};


/**
 * @type {Array<string>}
 */
BMap.MapStyle.prototype.features;


/**
 * @type {string}
 */
BMap.MapStyle.prototype.style;



/**
 * @param {string} name
 * @param {BMap.TileLayer|Array<BMap.TileLayer>} layers
 * @param {(BMap.MapTypeOptions|Object.<string>)=} opt_mapTypeOptions
 * @constructor
 */
BMap.MapType = function(name, layers,opt_mapTypeOptions) {};


/**
 * @return {string}
 */
BMap.MapType.prototype.getName = function() {};


/**
 * @return {BMap.TileLayer}
 */
BMap.MapType.prototype.getTileLayer = function() {};


/**
 * @return {number}
 */
BMap.MapType.prototype.getMinZoom = function() {};


/**
 * @return {number}
 */
BMap.MapType.prototype.getMaxZoom = function() {};


/**
 * @return {BMap.Projection}
 */
BMap.MapType.prototype.getProjection = function() {};


/**
 * @return {string}
 */
BMap.MapType.prototype.getTextColor = function() {};


/**
 * @return {string}
 */
BMap.MapType.prototype.getTips = function() {};



/**
 * @param {(BMap.MapTypeControlOptions|Object.<string>)=} opt_mapTypeControlOptions
 * @constructor
 * @extends {BMap.Control}
 */
BMap.MapTypeControl = function(opt_mapTypeControlOptions) {};



/**
 * @interface
 */
BMap.MapTypeControlOptions = function() {};


/**
 * @type {BMap.ControlAnchor}
 */
BMap.MapTypeControlOptions.prototype.anchor;


/**
 * @type {BMap.MapTypeControlType}
 */
BMap.MapTypeControlOptions.prototype.type;


/**
 * @type {Array<BMap.MapType>}
 */
BMap.MapTypeControlOptions.prototype.mapTypes;



/**
 * @interface
 */
BMap.MapTypeOptions = function() {};


/**
 * @type {number}
 */
BMap.MapTypeOptions.prototype.minZoom;


/**
 * @type {number}
 */
BMap.MapTypeOptions.prototype.maxZoom;


/**
 * @type {string}
 */
BMap.MapTypeOptions.prototype.errorImageUrl;


/**
 * @type {number}
 */
BMap.MapTypeOptions.prototype.textColor;


/**
 * @type {string}
 */
BMap.MapTypeOptions.prototype.tips;



/**
 * @param {BMap.Point} point
 * @param {(BMap.MarkerOptions|Object.<string>)=} opt_markerOptions
 * @constructor
 * @extends {BMap.Overlay}
 */
BMap.Marker = function(point, opt_markerOptions) {};


/**
 * @param {BMap.InfoWindow} infoWnd
 */
BMap.Marker.prototype.openInfoWindow = function(infoWnd) {};


/**
 *
 */
BMap.Marker.prototype.closeInfoWindow = function() {};


/**
 * @param {BMap.Icon} icon
 */
BMap.Marker.prototype.setIcon = function(icon) {};


/**
 * @return {BMap.Icon}
 */
BMap.Marker.prototype.getIcon = function() {};


/**
 * @param {BMap.Point} position
 */
BMap.Marker.prototype.setPosition = function(position) {};


/**
 * @return {BMap.Point}
 */
BMap.Marker.prototype.getPosition = function() {};


/**
 * @param {BMap.Size} offset
 */
BMap.Marker.prototype.setOffset = function(offset) {};


/**
 * @return {BMap.Size}
 */
BMap.Marker.prototype.getOffset = function() {};


/**
 * @return {BMap.Label}
 */
BMap.Marker.prototype.getLabel = function() {};


/**
 * @param {BMap.Label} label
 */
BMap.Marker.prototype.setLabel = function(label) {};


/**
 * @param {string} title
 */
BMap.Marker.prototype.setTitle = function(title) {};


/**
 * @return {string}
 */
BMap.Marker.prototype.getTitle = function() {};


/**
 * @param {boolean} isTop
 */
BMap.Marker.prototype.setTop = function(isTop) {};


/**
 *
 */
BMap.Marker.prototype.enableDragging = function() {};


/**
 *
 */
BMap.Marker.prototype.disableDragging = function() {};


/**
 *
 */
BMap.Marker.prototype.enableMassClear = function() {};


/**
 *
 */
BMap.Marker.prototype.disableMassClear = function() {};


/**
 * @param {number} zIndex
 */
BMap.Marker.prototype.setZIndex = function(zIndex) {};


/**
 * @return {BMap.Map}
 */
BMap.Marker.prototype.getMap = function() {};


/**
 * @param {BMap.ContextMenu} menu
 */
BMap.Marker.prototype.addContextMenu = function(menu) {};


/**
 * @param {BMap.ContextMenu} menu
 */
BMap.Marker.prototype.removeContextMenu = function(menu) {};


/**
 * @param {BMap.Animation} animation
 */
BMap.Marker.prototype.setAnimation = function(animation) {};


/**
 * @return {number}
 */
BMap.Marker.prototype.getRotation = function() {};


/**
 * @param {BMap.Icon} shadow
 */
BMap.Marker.prototype.setShadow = function(shadow) {};


/**
 * @return {BMap.Icon}
 */
BMap.Marker.prototype.getShadow = function() {};


/**
 * @param {string} event
 * @param {Function} handler
 */
BMap.Marker.prototype.addEventListener = function(event,handler) {};


/**
 * @param {string} event
 * @param {Function} handler
 */
BMap.Marker.prototype.removeEventListener = function(event,handler) {};



/**
 * @interface
 */
BMap.MarkerOptions = function() {};


/**
 * @type {BMap.Size}
 */
BMap.MarkerOptions.prototype.offset;


/**
 * @type {BMap.Icon}
 */
BMap.MarkerOptions.prototype.icon;


/**
 * @type {boolean}
 */
BMap.MarkerOptions.prototype.enableMassClear;


/**
 * @type {boolean}
 */
BMap.MarkerOptions.prototype.enableDragging;


/**
 * @type {boolean}
 */
BMap.MarkerOptions.prototype.enableClicking;


/**
 * @type {boolean}
 */
BMap.MarkerOptions.prototype.raiseOnDrag;


/**
 * @type {string}
 */
BMap.MarkerOptions.prototype.draggingCursor;


/**
 * @type {number}
 */
BMap.MarkerOptions.prototype.rotation;


/**
 * @type {BMap.Icon}
 */
BMap.MarkerOptions.prototype.shadow;


/**
 * @type {string}
 */
BMap.MarkerOptions.prototype.title;



/**
 * @param {string} text
 * @param {Function} callback
 * @param {(BMap.MenuItemOptions|Object.<string>)=} opt_menuItemOptions
 * @constructor
 */
BMap.MenuItem = function(text, callback, opt_menuItemOptions) {};


/**
 * @param {string} text
 */
BMap.MenuItem.prototype.setText = function(text) {};


/**
 * @param {string} iconUrl
 */
BMap.MenuItem.prototype.setIcon = function(iconUrl) {};


/**
 *
 */
BMap.MenuItem.prototype.enable = function() {};


/**
 *
 */
BMap.MenuItem.prototype.disable = function() {};



/**
 * @interface
 */
BMap.MenuItemOptions = function() {};


/**
 * @type {number}
 */
BMap.MenuItemOptions.prototype.width;


/**
 * @type {string}
 */
BMap.MenuItemOptions.id;


/**
 * @type {string|BMap.ContextMenuIcon}
 */
BMap.MenuItemOptions.iconUrl;



/**
 * @constructor
 * @extends {BMap.Projection}
 */
BMap.MercatorProjection = function() {};



/**
 * @param {(BMap.NavigationControlOptions|Object.<string>)=} opt_type
 * @constructor
 * @extends {BMap.Control}
 */
BMap.NavigationControl = function(opt_type) {};


/**
 * @return {BMap.NavigationControlType}
 */
BMap.NavigationControl.prototype.getType = function() {};


/**
 * @param {BMap.NavigationControlType} type
 */
BMap.NavigationControl.prototype.setType = function(type) {};



/**
 * @interface
 */
BMap.NavigationControlOptions = function() {};


/**
 * @type {BMap.ControlAnchor}
 */
BMap.NavigationControlOptions.prototype.anchor;


/**
 * @type {BMap.Size}
 */
BMap.NavigationControlOptions.prototype.offset;


/**
 * @type {BMap.NavigationControlType}
 */
BMap.NavigationControlOptions.prototype.type;


/**
 * @type {boolean}
 */
BMap.NavigationControlOptions.prototype.showZoomInfo;


/**
 * @type {boolean}
 */
BMap.NavigationControlOptions.prototype.enableGeolocation;



/**
 * @constructor
 */
BMap.Overlay = function() {};


/**
 * @param {BMap.Map} map
 * @return {HTMLElement}
 */
BMap.Overlay.prototype.initialize = function(map) {};


/**
 * @return {boolean}
 */
BMap.Overlay.prototype.isVisible = function() {};


/**
 *
 */
BMap.Overlay.prototype.dispose = function() {};


/**
 *
 */
BMap.Overlay.prototype.draw = function() {};


/**
 *
 */
BMap.Overlay.prototype.show = function() {};


/**
 *
 */
BMap.Overlay.prototype.hide = function() {};



/**
 * @param {(BMap.OverviewMapControlOptions|Object.<string>)=} opt_overviewMapControlOptions
 * @constructor
 * @extends {BMap.Control}
 */
BMap.OverviewMapControl = function(opt_overviewMapControlOptions) {};


/**
 *
 */
BMap.OverviewMapControl.prototype.changeView = function() {};


/**
 * @param {BMap.Size} size
 */
BMap.OverviewMapControl.prototype.setSize = function(size) {};


/**
 * @return {BMap.Size}
 */
BMap.OverviewMapControl.prototype.getSize = function() {};



/**
 * @interface
 */
BMap.OverviewMapControlOptions = function() {};


/**
 * @type {BMap.ControlAnchor}
 */
BMap.OverviewMapControlOptions.prototype.anchor;


/**
 * @type {BMap.Size}
 */
BMap.OverviewMapControlOptions.prototype.offset;


/**
 * @type {BMap.Size}
 */
BMap.OverviewMapControlOptions.prototype.size;


/**
 * @type {boolean}
 */
BMap.OverviewMapControlOptions.prototype.isOpen;



/**
 * @interface
 */
BMap.PanOptions = function() {};


/**
 * @type {boolean}
 */
BMap.PanOptions.prototype.noAnimation;



/**
 * @param {string|HTMLElement} container
 * @param {(BMap.PanoramaOptions|Object.<string>)=} opt_panoramaOptions
 * @constructor
 */
BMap.Panorama = function(container, opt_panoramaOptions) {};


/**
 * @return {Array<BMap.PanoramaLink>}
 */
BMap.Panorama.prototype.getLinks = function() {};


/**
 * @return {string}
 */
BMap.Panorama.prototype.getId = function() {};


/**
 * @return {BMap.Point}
 */
BMap.Panorama.prototype.getPosition = function() {};


/**
 * @return {BMap.PanoramaPov}
 */
BMap.Panorama.prototype.getPov = function() {};


/**
 * @return {number}
 */
BMap.Panorama.prototype.getZoom = function() {};


/**
 * @param {string} id
 */
BMap.Panorama.prototype.setId = function(id) {};


/**
 * @param {BMap.Point} position
 */
BMap.Panorama.prototype.setPosition = function(position) {};


/**
 * @param {BMap.PanoramaPov} pov
 */
BMap.Panorama.prototype.setPov = function(pov) {};


/**
 * @param {number} zoom
 */
BMap.Panorama.prototype.setZoom = function(zoom) {};


/**
 *
 */
BMap.Panorama.prototype.enableScrollWheelZoom = function() {};


/**
 *
 */
BMap.Panorama.prototype.disableScrollWheelZoom = function() {};


/**
 *
 */
BMap.Panorama.prototype.show = function() {};


/**
 *
 */
BMap.Panorama.prototype.hide = function() {};



/**
 * @constructor
 * @extends {BMap.Control}
 */
BMap.PanoramaControl = function() {};



/**
 * @constructor
 */
BMap.PanoramaData = function() {};


/**
 * @type {string}
 */
BMap.PanoramaData.prototype.id;


/**
 * @type {string}
 */
BMap.PanoramaData.prototype.description;


/**
 * @type {Array<BMap.PanoramaLink>}
 */
BMap.PanoramaData.prototype.links;


/**
 * @type {BMap.Point}
 */
BMap.PanoramaData.prototype.position;


/**
 * @type {BMap.PanoramaTileData}
 */
BMap.PanoramaData.prototype.tiles;



/**
 * @param {string} content
 * @param {(BMap.PanoramaLabelOptions|Object.<string>)=} opt_panoramaLabelOptions
 * @constructor
 */
BMap.PanoramaLabel = function(content, opt_panoramaLabelOptions) {};


/**
 * @param {BMap.Point} position
 */
BMap.PanoramaLabel.prototype.setPosition = function(position) {};


/**
 * @return {BMap.Point}
 */
BMap.PanoramaLabel.prototype.getPosition = function() {};


/**
 * @return {BMap.PanoramaPov}
 */
BMap.PanoramaLabel.prototype.getPov = function() {};


/**
 * @param {string} content
 */
BMap.PanoramaLabel.prototype.setContent = function(content) {};


/**
 * @return {string}
 */
BMap.PanoramaLabel.prototype.getContent = function() {};


/**
 *
 */
BMap.PanoramaLabel.prototype.show = function() {};


/**
 *
 */
BMap.PanoramaLabel.prototype.hide = function() {};


/**
 * @param {number} altitude
 */
BMap.PanoramaLabel.prototype.setAltitude = function(altitude) {};


/**
 * @return {number}
 */
BMap.PanoramaLabel.prototype.getAltitude = function() {};


/**
 *
 */
BMap.PanoramaLabel.prototype.addEventListener = function() {};


/**
 *
 */
BMap.PanoramaLabel.prototype.removeEventListener = function() {};



/**
 * @interface
 */
BMap.PanoramaLabelOptions = function() {};


/**
 * @type {BMap.Point}
 */
BMap.PanoramaLabelOptions.prototype.position;


/**
 * @type {number}
 */
BMap.PanoramaLabelOptions.prototype.altitude;



/**
 * @constructor
 */
BMap.PanoramaLink = function() {};


/**
 * @type {string}
 */
BMap.PanoramaLink.prototype.description;


/**
 * @type {number}
 */
BMap.PanoramaLink.prototype.heading;


/**
 * @type {string}
 */
BMap.PanoramaLink.prototype.id;



/**
 * @interface
 */
BMap.PanoramaOptions = function() {};


/**
 * @type {boolean}
 */
BMap.PanoramaOptions.prototype.navigationControl;


/**
 * @type {boolean}
 */
BMap.PanoramaOptions.prototype.linksControl;


/**
 * @type {boolean}
 */
BMap.PanoramaOptions.prototype.indoorSceneSwitchControl;


/**
 * @type {boolean}
 */
BMap.PanoramaOptions.prototype.albumsControl;


/**
 * @type {BMap.AlbumsControlOptions}
 */
BMap.PanoramaOptions.prototype.albumsControlOptions;



/**
 * @constructor
 */
BMap.PanoramaPov = function() {};


/**
 * @type {number}
 */
BMap.PanoramaPov.prototype.heading;


/**
 * @type {number}
 */
BMap.PanoramaPov.prototype.pitch;



/**
 * @constructor
 */
BMap.PanoramaService = function() {};


/**
 * @param {string} id
 * @param {Function} callback
 */
BMap.PanoramaService.prototype.getPanoramaById = function(id,callback) {};


/**
 * @param {BMap.Point} point
 * @param {number|Function} radius    Radius or callback
 * @param {Function=} opt_callback
 */
BMap.PanoramaService.prototype.getPanoramaByLocation = function(point,radius,opt_callback) {};


/**
 * @constructor
 */
BMap.PanoramaTileData = function() {};


/**
 * @type {number}
 */
BMap.PanoramaTileData.prototype.centerHeading;


/**
 * @type {BMap.Size}
 */
BMap.PanoramaTileData.prototype.tileSize;


/**
 * @type {BMap.Size}
 */
BMap.PanoramaTileData.prototype.worldSize;



/**
 * @constructor
 * @extends {BMap.Projection}
 */
BMap.PerspectiveProjection = function() {};



/**
 * @param {number} x
 * @param {number} y
 * @constructor
 */
BMap.Pixel = function(x,y) {};


/**
 * @type {number}
 */
BMap.Pixel.prototype.x;


/**
 * @type {number}
 */
BMap.Pixel.prototype.y;


/**
 * @param {BMap.Pixel} other
 * @return {boolean}
 */
BMap.Pixel.prototype.equals = function(other) {};



/**
 * @param {number} lng
 * @param {number} lat
 * @constructor
 */
BMap.Point = function(lng, lat) {};


/**
 * @type {number}
 */
BMap.Point.prototype.lng;


/**
 * @type {number}
 */
BMap.Point.prototype.lat;


/**
 * @param {BMap.Point} other
 * @return {boolean}
 */
BMap.Point.prototype.equals = function(other) {};




/**
 * @param {Array<BMap.Point>} points
 * @param {(BMap.PointCollectionOption|Object.<string>)=} opt_pointCollectionOption
 * @constructor
 */
BMap.PointCollection = function(points, opt_pointCollectionOption) {};



/**
 * @param {Array<BMap.Point>} points
 */
BMap.PointCollection.prototype.setPoints = function(points) {};



/**
 * @param {BMap.PointCollectionOption} styles
 */
BMap.PointCollection.prototype.setStyles = function(styles) {};


/**
 *
 */
BMap.PointCollection.prototype.clear = function() {};



/**
 * @interface
 */
BMap.PointCollectionOption = function() {};


/**
 * @type {BMap.ShapeType}
 */
BMap.PointCollectionOption.prototype.shape;


/**
 * @type {string}
 */
BMap.PointCollectionOption.prototype.color;


/**
 * @type {BMap.SizeType}
 */
BMap.PointCollectionOption.prototype.size;



/**
 * @param {Array<BMap.Point>} points
 * @param {(BMap.PolygonOptions|Object.<string>)=} opt_polygonOptions
 * @constructor
 */
BMap.Polygon = function(points, opt_polygonOptions) {};


/**
 * @param {Array<BMap.Point>} path
 */
BMap.Polygon.prototype.setPath = function(path) {};


/**
 * @return {Array<BMap.Point>}
 */
BMap.Polygon.prototype.getPath = function() {};


/**
 * @param {string} color
 */
BMap.Polygon.prototype.setStrokeColor = function(color) {};


/**
 * @return {string}
 */
BMap.Polygon.prototype.getStrokeColor = function() {};


/**
 * @param {string} color
 */
BMap.Polygon.prototype.setFillColor = function(color) {};


/**
 * @return {string}
 */
BMap.Polygon.prototype.getFillColor = function() {};


/**
 * @param {number} opacity
 */
BMap.Polygon.prototype.setStrokeOpacity = function(opacity) {};


/**
 * @return {number}
 */
BMap.Polygon.prototype.getStrokeOpacity = function() {};


/**
 * @param {number} opacity
 */
BMap.Polygon.prototype.setFillOpacity = function(opacity) {};


/**
 * @return {number}
 */
BMap.Polygon.prototype.getFillOpacity = function() {};


/**
 * @param {number} weight
 */
BMap.Polygon.prototype.setStrokeWeight = function(weight) {};


/**
 * @return {number}
 */
BMap.Polygon.prototype.getStrokeWeight = function() {};


/**
 * @param {string} style
 */
BMap.Polygon.prototype.setStrokeStyle = function(style) {};


/**
 * @return {string}
 */
BMap.Polygon.prototype.getStrokeStyle = function() {};


/**
 * @return {BMap.Bounds}
 */
BMap.Polygon.prototype.getBounds = function() {};


/**
 *
 */
BMap.Polygon.prototype.enableEditing = function() {};


/**
 *
 */
BMap.Polygon.prototype.disableEditing = function() {};


/**
 *
 */
BMap.Polygon.prototype.enableMassClear = function() {};


/**
 *
 */
BMap.Polygon.prototype.disableMassClear = function() {};


/**
 * @param {number} index
 * @param {BMap.Point} point
 */
BMap.Polygon.prototype.setPositionAt = function(index,point) {};


/**
 * @param {string} event
 * @param {Function} handler
 */
BMap.Polygon.prototype.addEventListener = function(event,handler) {};


/**
 * @param {string} event
 * @param {Function} handler
 */
BMap.Polygon.prototype.removeEventListener = function(event,handler) {};


/**
 * @return {BMap.Map}
 */
BMap.Polygon.prototype.getMap = function() {};



/**
 * @interface
 */
BMap.PolygonOptions = function() {};


/**
 * @type {string}
 */
BMap.PolygonOptions.prototype.strokeColor;


/**
 * @type {string}
 */
BMap.PolygonOptions.prototype.fillColor;


/**
 * @type {number}
 */
BMap.PolygonOptions.prototype.strokeWeight;


/**
 * @type {number}
 */
BMap.PolygonOptions.prototype.strokeOpacity;


/**
 * @type {number}
 */
BMap.PolygonOptions.prototype.fillOpacity;


/**
 * @type {string}
 */
BMap.PolygonOptions.prototype.strokeStyle;


/**
 * @type {boolean}
 */
BMap.PolygonOptions.prototype.enableMassClear;


/**
 * @type {boolean}
 */
BMap.PolygonOptions.prototype.enableEditing;


/**
 * @type {boolean}
 */
BMap.PolygonOptions.prototype.enableClicking;



/**
 * @param {Array<BMap.Point>} points
 * @param {(BMap.PolylineOptions|Object.<string>)=} opt_polylineOptions
 * @constructor
 */
BMap.Polyline = function(points, opt_polylineOptions) {};



/**
 * @param {Array<BMap.Point>} path
 */
BMap.Polyline.prototype.setPath = function(path) {};


/**
 * @return {Array<BMap.Point>}
 */
BMap.Polyline.prototype.getPath = function() {};


/**
 * @param {string} color
 */
BMap.Polyline.prototype.setStrokeColor = function(color) {};


/**
 * @return {string}
 */
BMap.Polyline.prototype.getStrokeColor = function() {};


/**
 * @param {number} opacity
 */
BMap.Polyline.prototype.setStrokeOpacity = function(opacity) {};


/**
 * @return {number}
 */
BMap.Polyline.prototype.getStrokeOpacity = function() {};


/**
 * @param {number} weight
 */
BMap.Polyline.prototype.setStrokeWeight = function(weight) {};


/**
 * @return {number}
 */
BMap.Polyline.prototype.getStrokeWeight = function() {};


/**
 * @param {string} style
 */
BMap.Polyline.prototype.setStrokeStyle = function(style) {};


/**
 * @return {string}
 */
BMap.Polyline.prototype.getStrokeStyle = function() {};


/**
 * @return {BMap.Bounds}
 */
BMap.Polyline.prototype.getBounds = function() {};


/**
 *
 */
BMap.Polyline.prototype.enableEditing = function() {};


/**
 *
 */
BMap.Polyline.prototype.disableEditing = function() {};


/**
 *
 */
BMap.Polyline.prototype.enableMassClear = function() {};


/**
 *
 */
BMap.Polyline.prototype.disableMassClear = function() {};


/**
 * @param {number} index
 * @param {BMap.Point} point
 */
BMap.Polyline.prototype.setPositionAt = function(index,point) {};


/**
 * @return {BMap.Map}
 */
BMap.Polyline.prototype.getMap = function() {};


/**
 * @param {string} event
 * @param {Function} handler
 */
BMap.Polyline.prototype.addEventListener = function(event,handler) {};


/**
 * @param {string} event
 * @param {Function} handler
 */
BMap.Polyline.prototype.removeEventListener = function(event,handler) {};



/**
 * @interface
 */
BMap.PolylineOptions = function() {};


/**
 * @type {string}
 */
BMap.PolylineOptions.prototype.strokeColor;


/**
 * @type {number}
 */
BMap.PolylineOptions.prototype.strokeWeight;


/**
 * @type {number}
 */
BMap.PolylineOptions.prototype.strokeOpacity;


/**
 * @type {string}
 */
BMap.PolylineOptions.prototype.strokeStyle;


/**
 * @type {boolean}
 */
BMap.PolylineOptions.prototype.enableMassClear;


/**
 * @type {boolean}
 */
BMap.PolylineOptions.prototype.enableEditing;


/**
 * @type {boolean}
 */
BMap.PolylineOptions.prototype.enableClicking;



/**
 * @interface
 */
BMap.PositionOptions = function() {};


/**
 * @type {boolean}
 */
BMap.PositionOptions.prototype.enableHighAccuracy;


/**
 * @type {number}
 */
BMap.PositionOptions.prototype.timeout;


/**
 * @type {number}
 */
BMap.PositionOptions.prototype.maximumAge;



/**
 * @constructor
 */
BMap.PredictDate = function() {};


/**
 * @type {number}
 */
BMap.PredictDate.prototype.weekday;


/**
 * @type {number}
 */
BMap.PredictDate.prototype.hour;



/**
 * @constructor
 */
BMap.Projection = function() {};


/**
 * @param {BMap.Point} lngLat
 * @return {BMap.Pixel}
 */
BMap.Projection.prototype.lngLatToPoint = function(lngLat) {};


/**
 * @param {BMap.Pixel} point
 * @return {BMap.Point}
 */
BMap.Projection.prototype.pointToLngLat = function(point) {};



/**
 * @interface
 */
BMap.RenderOptions = function() {};


/**
 * @type {BMap.Map}
 */
BMap.RenderOptions.prototype.map;


/**
 * @type {string|HTMLElement}
 */
BMap.RenderOptions.prototype.panel


/**
 * @type {boolean}
 */
BMap.RenderOptions.prototype.selectFirstResult;


/**
 * @type {boolean}
 */
BMap.RenderOptions.prototype.autoViewport;


/**
 * @type {BMap.HighlightModes}
 */
BMap.RenderOptions.prototype.highlightMode;



/**
 * @constructor
 */
BMap.Route = function() {};


/**
 * @return {number}
 */
BMap.Route.prototype.getNumSteps = function() {};


/**
 * @param {number} i
 * @return {BMap.Step}
 */
BMap.Route.prototype.getStep = function(i) {};


/**
 * @param {boolean} format
 * @return {string|number}
 */
BMap.Route.prototype.getDistance = function(format) {};


/**
 * @return {number}
 */
BMap.Route.prototype.getIndex = function() {};


/**
 * @return {BMap.Polyline}
 */
BMap.Route.prototype.getPolyline = function() {};


/**
 * @return {Array<BMap.Point>}
 */
BMap.Route.prototype.getPoints = function() {};


/**
 * @return {Array<BMap.Point>}
 */
BMap.Route.prototype.getPath = function() {};


/**
 * @return {BMap.RouteType}
 */
BMap.Route.prototype.getRouteType = function() {};



/**
 * @constructor
 */
BMap.RoutePlan = function() {};


/**
 * @return {number}
 */
BMap.RoutePlan.prototype.getNumRoutes = function() {};


/**
 * @param {number} i
 * @return {BMap.Route}
 */
BMap.RoutePlan.prototype.getRoute = function(i) {};


/**
 * @param {boolean} format
 * @return {string|number}
 */
BMap.RoutePlan.prototype.getDistance = function(format) {};


/**
 * @param {boolean} format
 * @return {string|number}
 */
BMap.RoutePlan.prototype.getDuration = function(format) {};


/**
 * @return {Array<BMap.LocalResultPoi>}
 */
BMap.RoutePlan.prototype.getDragPois = function() {};



/**
 * @param {(BMap.ScaleControlOptions|Object.<string>)=} opt_scaleControlOptions
 * @constructor
 * @extends {BMap.Control}
 */
BMap.ScaleControl = function(opt_scaleControlOptions) {};


/**
 * @return {BMap.LengthUnit}
 */
BMap.ScaleControl.prototype.getUnit = function() {};


/**
 * @param {BMap.LengthUnit} unit
 */
BMap.ScaleControl.prototype.setUnit = function(unit) {};



/**
 * @interface
 */
BMap.ScaleControlOptions = function() {};


/**
 * @type {BMap.ControlAnchor}
 */
BMap.ScaleControlOptions.anchor;


/**
 * @type {BMap.Size}
 */
BMap.ScaleControlOptions.offset;



/**
 * @param {number} width
 * @param {number} height
 * @constructor
 */
BMap.Size = function(width, height) {};


/**
 * @type {number}
 */
BMap.Size.prototype.width;


/**
 * @type {number}
 */
BMap.Size.prototype.height;


/**
 * @param {BMap.Size} other
 * @return {boolean}
 */
BMap.Size.prototype.equals = function(other) {};



/**
 * @constructor
 */
BMap.Step = function() {};


/**
 * @return {BMap.Point}
 */
BMap.Step.prototype.getPosition = function() {};


/**
 * @return {number}
 */
BMap.Step.prototype.getIndex = function() {};


/**
 * @param {boolean} includeHtml
 * @return {string}
 */
BMap.Step.prototype.getDescription = function(includeHtml) {};


/**
 * @param {boolean} format
 * @return {string|number}
 */
BMap.Step.prototype.getDistance = function(format) {};



/**
 * @param {string|BMap.SymbolShapeType} path
 * @param {(BMap.SymbolOptions|Object.<string>)=} opt_symbolOptions
 * @constructor
 */
BMap.Symbol = function(path, opt_symbolOptions) {};


/**
 * @param {string|BMap.SymbolShapeType} path
 */
BMap.Symbol.prototype.setPath = function(path) {};


/**
 * @param {BMap.Size} anchor
 */
BMap.Symbol.prototype.setAnchor = function(anchor) {};


/**
 * @param {number} rotation
 */
BMap.Symbol.prototype.setRotation = function(rotation) {};


/**
 * @param {number} scale
 */
BMap.Symbol.prototype.setScale = function(scale) {};


/**
 * @param {number} strokeWeight
 */
BMap.Symbol.prototype.setStrokeWeight = function(strokeWeight) {};


/**
 * @param {string} color
 */
BMap.Symbol.prototype.setStrokeColor = function(color) {};


/**
 * @param {number} opacity
 */
BMap.Symbol.prototype.setStrokeOpacity = function(opacity) {};


/**
 * @param {number} opacity
 */
BMap.Symbol.prototype.setFillOpacity = function(opacity) {};


/**
 * @param {string} color
 */
BMap.Symbol.prototype.setFillColor = function(color) {};



/**
 * @interface
 */
BMap.SymbolOptions = function() {};


/**
 * @type {BMap.Size}
 */
BMap.SymbolOptions.prototype.anchor;


/**
 * @type {string}
 */
BMap.SymbolOptions.prototype.fillColor;


/**
 * @type {number}
 */
BMap.SymbolOptions.prototype.fillOpacity;


/**
 * @type {number}
 */
BMap.SymbolOptions.prototype.scale;


/**
 * @type {number}
 */
BMap.SymbolOptions.prototype.rotation;


/**
 * @type {string}
 */
BMap.SymbolOptions.prototype.strokeColor;


/**
 * @type {number}
 */
BMap.SymbolOptions.prototype.strokeOpacity;


/**
 * @type {number}
 */
BMap.SymbolOptions.prototype.strokeWeight;



/**
 * @constructor
 */
BMap.TaxiFare = function() {};


/**
 * @type {BMap.TaxiFareDetail}
 */
BMap.TaxiFare.prototype.day;


/**
 * @type {BMap.TaxiFareDetail}
 */
BMap.TaxiFare.prototype.night;


/**
 * @type {number}
 */
BMap.TaxiFare.prototype.distance;


/**
 * @type {string}
 */
BMap.TaxiFare.prototype.remark;



/**
 * @constructor
 */
BMap.TaxiFareDetail = function() {};


/**
 * @type {number}
 */
BMap.TaxiFareDetail.prototype.initialFare;


/**
 * @type {number}
 */
BMap.TaxiFareDetail.prototype.unitFare;


/**
 * @type {number}
 */
BMap.TaxiFareDetail.prototype.totalFare;



/**
 * @param {(BMap.TileLayerOptions|Object.<string>)=} opt_tileLayerOptions
 * @constructor
 */
BMap.TileLayer = function(opt_tileLayerOptions) {};


/**
 * @param {BMap.Pixel} tileCoord
 * @param {number} zoom
 * @return {string}
 */
BMap.TileLayer.prototype.getTilesUrl = function(tileCoord,zoom) {};


/**
 * @return {string|BMap.MapType}
 */
BMap.TileLayer.prototype.getMapType = function() {};


/**
 * @return {number}
 */
BMap.TileLayer.prototype.getCopyright = function() {};


/**
 * @return {number}
 */
BMap.TileLayer.prototype.isTransparentPng = function() {};



/**
 * @interface
 */
BMap.TileLayerOptions = function() {};


/**
 * @type {boolean}
 */
BMap.TileLayerOptions.transparentPng;


/**
 * @type {string}
 */
BMap.TileLayerOptions.tileUrlTemplate;


/**
 * @type {BMap.Copyright}
 */
BMap.TileLayerOptions.copyright;


/**
 * @type {number}
 */
BMap.TileLayerOptions.zIndex;



/**
 * @constructor
 * @extends {BMap.Control}
 */
BMap.TrafficControl = function() {};


/**
 * @param {BMap.Size} offset
 */
BMap.TrafficControl.prototype.setPanelOffset = function(offset) {};


/**
 *
 */
BMap.TrafficControl.prototype.show = function() {};


/**
 *
 */
BMap.TrafficControl.prototype.hide = function() {};


/**
 * @param {(BMap.TrafficLayerOptions|Object.<string>)=} opt_trafficLayerOptions
 * @constructor
 */
BMap.TrafficLayer = function(opt_trafficLayerOptions) {};



/**
 * @interface
 */
BMap.TrafficLayerOptions = function() {};


/**
 * @type {BMap.PredictDate}
 */
BMap.TrafficLayerOptions.prototype.predictDate;



/**
 * @param {BMap.Map|BMap.Point|string} location
 * @param {(BMap.TransitRouteOptions|Object.<string>)=} opt_transitRouteOptions
 * @constructor
 */
BMap.TransitRoute = function(location, opt_transitRouteOptions) {};


/**
 * @param {string|BMap.Point|BMap.LocalResultPoi} start
 * @param {string|BMap.Point|BMap.LocalResultPoi} end
 */
BMap.TransitRoute.prototype.search = function(start,end) {};


/**
 * @return {BMap.TransitRouteResult}
 */
BMap.TransitRoute.prototype.getResults = function() {};


/**
 *
 */
BMap.TransitRoute.prototype.clearResults = function() {};


/**
 *
 */
BMap.TransitRoute.prototype.enableAutoViewport = function() {};


/**
 *
 */
BMap.TransitRoute.prototype.disableAutoViewport = function() {};


/**
 * @param {number} capacity
 */
BMap.TransitRoute.prototype.setPageCapacity = function(capacity) {};


/**
 * @param {BMap.Map|BMap.Point|string} location
 */
BMap.TransitRoute.prototype.setLocation = function(location) {};


/**
 * @param {BMap.TransitPolicy} policy
 */
BMap.TransitRoute.prototype.setPolicy = function(policy) {};


/**
 * @param {Function} callback
 */
BMap.TransitRoute.prototype.setSearchCompleteCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.TransitRoute.prototype.setMarkersSetCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.TransitRoute.prototype.setInfoHtmlSetCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.TransitRoute.prototype.setPolylinesSetCallback = function(callback) {};
/**
 * @param {Function} callback
 */
BMap.TransitRoute.prototype.setResultsHtmlSetCallback = function(callback) {};


/**
 * @return {BMap.StatusCode}
 */
BMap.TransitRoute.prototype.getStatus = function() {};


/**
 * @return {string}
 */
BMap.TransitRoute.prototype.toString = function() {};



/**
 * @interface
 */
BMap.TransitRouteOptions = function() {};


/**
 * @type {BMap.RenderOptions}
 */
BMap.TransitRouteOptions.prototype.renderOptions;


/**
 * @type {BMap.TransitPolicy}
 */
BMap.TransitRouteOptions.prototype.policy;


/**
 * @type {number}
 */
BMap.TransitRouteOptions.prototype.pageCapacity;


/**
 * @type {Function}
 */
BMap.TransitRouteOptions.prototype.onSearchComplete;


/**
 * @type {Function}
 */
BMap.TransitRouteOptions.prototype.onMarkersSet;


/**
 * @type {Function}
 */
BMap.TransitRouteOptions.prototype.onInfoHtmlSet;


/**
 * @type {Function}
 */
BMap.TransitRouteOptions.prototype.onPolylinesSet;


/**
 * @type {Function}
 */
BMap.TransitRouteOptions.prototype.onResultsHtmlSet;



/**
 * @constructor
 */
BMap.TransitRoutePlan = function() {};


/**
 * @return {number}
 */
BMap.TransitRoutePlan.prototype.getNumLines = function() {};


/**
 * @param {number} i
 * @return {BMap.Line}
 */
BMap.TransitRoutePlan.prototype.getLine = function(i) {};


/**
 * @return {number}
 */
BMap.TransitRoutePlan.prototype.getNumRoutes = function() {};



/**
 * @param {number} i
 * @return {BMap.Route}
 */
BMap.TransitRoutePlan.prototype.getRoute = function(i) {};


/**
 * @param {boolean=} opt_format
 * @return {string|number}
 */
BMap.TransitRoutePlan.prototype.getDistance = function(opt_format) {};


/**
 * @param {boolean} format
 * @return {string|number}
 */
BMap.TransitRoutePlan.prototype.getDuration = function(format) {};


/**
 * @param {boolean=} opt_includeHtml
 * @return {string}
 */
BMap.TransitRoutePlan.prototype.getDescription = function(opt_includeHtml) {};



/**
 * @constructor
 */
BMap.TransitRouteResult = function() {};


/**
 * @return {BMap.LocalResultPoi}
 */
BMap.TransitRouteResult.prototype.getStart = function() {};


/**
 * @return {BMap.LocalResultPoi}
 */
BMap.TransitRouteResult.prototype.getEnd = function() {};


/**
 * @return {number}
 */
BMap.TransitRouteResult.prototype.getNumPlans = function() {};


/**
 * @param {number} i
 * @return {BMap.TransitRoutePlan}
 */
BMap.TransitRouteResult.prototype.getPlan = function(i) {};


/**
 * @type {BMap.TransitPolicy}
 */
BMap.TransitRouteResult.prototype.policy;


/**
 * @type {string}
 */
BMap.TransitRouteResult.prototype.city;


/**
 * @type {string}
 */
BMap.TransitRouteResult.prototype.moreResultsUrl;



/**
 * @constructor
 */
BMap.Viewport = function() {};


/**
 * @type {BMap.Point}
 */
BMap.Viewport.prototype.center;


/**
 * @type {number}
 */
BMap.Viewport.prototype.zoom;



/**
 * @interface
 */
BMap.ViewportOptions = function() {};


/**
 * @type {boolean}
 */
BMap.ViewportOptions.enableAnimation;


/**
 * @type {Array<number>}
 */
BMap.ViewportOptions.margins;


/**
 * @type {number}
 */
BMap.ViewportOptions.zoomFactor;


/**
 * @type {number}
 */
BMap.ViewportOptions.delay;



/**
 * @param {BMap.Map|BMap.Point|string} location
 * @param {(BMap.WalkingRouteOptions|Object.<string>)=} opt_walkingRouteOptions
 * @constructor
 */
BMap.WalkingRoute = function(location, opt_walkingRouteOptions) {};


/**
 * @param {BMap.Map|BMap.Point|string} start
 * @param {BMap.Map|BMap.Point|string} end
 */
BMap.WalkingRoute.prototype.search = function(start,end) {};


/**
 * @return {BMap.WalkingRouteResult}
 */
BMap.WalkingRoute.prototype.getResults = function() {};


/**
 *
 */
BMap.WalkingRoute.prototype.clearResults = function() {};


/**
 *
 */
BMap.WalkingRoute.prototype.enableAutoViewport = function() {};


/**
 *
 */
BMap.WalkingRoute.prototype.disableAutoViewport = function() {};


/**
 * @param {BMap.Map|BMap.Point|string} location
 */
BMap.WalkingRoute.prototype.setLocation = function(location) {};


/**
 * @param {Function} callback
 */
BMap.WalkingRoute.prototype.setSearchCompleteCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.WalkingRoute.prototype.setMarkersSetCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.WalkingRoute.prototype.setInfoHtmlSetCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.WalkingRoute.prototype.setPolylinesSetCallback = function(callback) {};


/**
 * @param {Function} callback
 */
BMap.WalkingRoute.prototype.setResultsHtmlSetCallback = function(callback) {};


/**
 * @return {BMap.StatusCode}
 */
BMap.WalkingRoute.prototype.getStatus = function() {};


/**
 * @return {string}
 */
BMap.WalkingRoute.prototype.toString = function() {};



/**
 * @interface
 */
BMap.WalkingRouteOptions = function() {};


/**
 * @type {BMap.RenderOptions}
 */
BMap.WalkingRouteOptions.prototype.renderOptions;


/**
 * @type {Function}
 */
BMap.WalkingRouteOptions.prototype.onSearchComplete;


/**
 * @type {Function}
 */
BMap.WalkingRouteOptions.prototype.onMarkersSet;


/**
 * @type {Function}
 */
BMap.WalkingRouteOptions.prototype.onPolylinesSet;


/**
 * @type {Function}
 */
BMap.WalkingRouteOptions.prototype.onInfoHtmlSet;


/**
 * @type {Function}
 */
BMap.WalkingRouteOptions.prototype.onResultsHtmlSet;



/**
 * @constructor
 */
BMap.WalkingRouteResult = function() {};


/**
 * @return {BMap.LocalResultPoi}
 */
BMap.WalkingRouteResult.prototype.getStart = function() {};


/**
 * @return {BMap.LocalResultPoi}
 */
BMap.WalkingRouteResult.prototype.getEnd = function() {};


/**
 * @return {number}
 */
BMap.WalkingRouteResult.prototype.getNumPlans = function() {};


/**
 * @param {number} i
 * @return {BMap.RoutePlan}
 */
BMap.WalkingRouteResult.prototype.getPlan = function(i) {};


/**
 * @type {string}
 */
BMap.WalkingRouteResult.prototype.city;


// Constant
/**
 * @type {string}
 * @const
 */
var BMAP_API_VERSION;

/**
 * @type {number}
 * @const
 */
var BMAP_ANCHOR_TOP_LEFT;

/**
 * @type {number}
 * @const
 */
var BMAP_ANCHOR_TOP_RIGHT;

/**
 * @type {number}
 * @const
 */
var BMAP_ANCHOR_BOTTOM_LEFT;

/**
 * @type {number}
 * @const
 */
var BMAP_ANCHOR_BOTTOM_RIGHT;

/**
 * @type {string}
 * @const
 */
var BMAP_UNIT_METRIC;

/**
 * @type {string}
 * @const
 */
var BMAP_UNIT_IMPERIAL;

/**
 * @type {number}
 * @const
 */
var BMAP_NAVIGATION_CONTROL_LARGE;

/**
 * @type {number}
 * @const
 */
var BMAP_NAVIGATION_CONTROL_SMALL;

/**
 * @type {number}
 * @const
 */
var BMAP_NAVIGATION_CONTROL_PAN;

/**
 * @type {number}
 */
var BMAP_NAVIGATION_CONTROL_ZOOM;

/**
 * @type {number}
 * @const
 */
var BMAP_MAPTYPE_CONTROL_HORIZONTAL;

/**
 * @type {number}
 * @const
 */
var BMAP_MAPTYPE_CONTROL_DROPDOWN;

/**
 * @type {number}
 * @const
 */
var BMAP_MAPTYPE_CONTROL_MAP;

/**
 * @type {number}
 * @const
 */
var BMAP_STATUS_SUCCESS;

/**
 * @type {number}
 * @const
 */
var BMAP_STATUS_CITY_LIST;

/**
 * @type {number}
 * @const
 */
var BMAP_STATUS_UNKNOWN_LOCATION;

/**
 * @type {number}
 * @const
 */
var BMAP_STATUS_UNKNOWN_ROUTE;

/**
 * @type {number}
 * @const
 */
var BMAP_STATUS_INVALID_KEY;

/**
 * @type {number}
 * @const
 */
var BMAP_STATUS_INVALID_REQUEST;

/**
 * @type {number}
 * @const
 */
var BMAP_STATUS_PERMISSION_DENIED;

/**
 * @type {number}
 * @const
 */
var BMAP_STATUS_SERVICE_UNAVAILABLE;

/**
 * @type {number}
 * @const
 */
var BMAP_STATUS_TIMEOUT;

/**
 * @type {number}
 * @const
 */
var BMap_Symbol_SHAPE_CIRCLE;

/**
 * @type {number}
 * @const
 */
var BMap_Symbol_SHAPE_RECTANGLE;

/**
 * @type {number}
 * @const
 */
var BMap_Symbol_SHAPE_RHOMBUS;

/**
 * @type {number}
 * @const
 */
var BMap_Symbol_SHAPE_STAR;

/**
 * @type {number}
 * @const
 */
var BMap_Symbol_SHAPE_BACKWARD_CLOSED_ARROW;

/**
 * @type {number}
 * @const
 */
var BMap_Symbol_SHAPE_FORWARD_CLOSED_ARROW;

/**
 * @type {number}
 * @const
 */
var BMap_Symbol_SHAPE_BACKWARD_OPEN_ARROW;

/**
 * @type {number}
 * @const
 */
var BMap_Symbol_SHAPE_FORWARD_OPEN_ARROW;

/**
 * @type {number}
 * @const
 */
var BMap_Symbol_SHAPE_POINT;

/**
 * @type {number}
 * @const
 */
var BMap_Symbol_SHAPE_PLANE;

/**
 * @type {number}
 * @const
 */
var BMap_Symbol_SHAPE_CAMERA;

/**
 * @type {number}
 * @const
 */
var BMap_Symbol_SHAPE_WARNING;

/**
 * @type {number}
 * @const
 */
var BMap_Symbol_SHAPE_SMILE;

/**
 * @type {number}
 * @const
 */
var BMap_Symbol_SHAPE_CLOCK;

/**
 * @type {number}
 * @const
 */
var BMAP_ANIMATION_DROP;

/**
 * @type {number}
 * @const
 */
var BMAP_ANIMATION_BOUNCE;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_SHAPE_CIRCLE;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_SHAPE_STAR;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_SHAPE_SQUARE;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_SHAPE_RHOMBUS;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_SHAPE_WATERDROP;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_SIZE_TINY;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_SIZE_SMALLER;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_SIZE_SMALL;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_SIZE_NORMAL;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_SIZE_BIG;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_SIZE_BIGGER;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_SIZE_HUGE;

/**
 * @type {string}
 * @const
 */
var BMAP_CONTEXT_MENU_ICON_ZOOMIN;

/**
 * @type {string}
 * @const
 */
var BMAP_CONTEXT_MENU_ICON_ZOOMOUT;

/**
 * @type {BMap.MapType}
 * @const
 */
var BMAP_NORMAL_MAP;

/**
 * @type {BMap.MapType}
 * @const
 */
var BMAP_PERSPECTIVE_MAP;

/**
 * @type {BMap.MapType}
 * @const
 */
var BMAP_SATELLITE_MAP;

/**
 * @type {BMap.MapType}
 * @const
 */
var BMAP_HYBRID_MAP;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_DENSITY_HIGH;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_DENSITY_MEDIUM;

/**
 * @type {number}
 * @const
 */
var BMAP_POINT_DENSITY_LOW;

/**
 * @type {string}
 * @const
 */
var BMAP_PANORAMA_INDOOR_SCENE;

/**
 * @type {string}
 * @const
 */
var BMAP_PANORAMA_STREET_SCENE;

/**
 * @type {string}
 * @const
 */
var BMAP_PANORAMA_POI_HOTEL;

/**
 * @type {string}
 * @const
 */
var BMAP_PANORAMA_POI_CATERING;

/**
 * @type {string}
 * @const
 */
var BMAP_PANORAMA_POI_MOVIE;

/**
 * @type {string}
 * @const
 */
var BMAP_PANORAMA_POI_TRANSIT;

/**
 * @type {string}
 * @const
 */
var BMAP_PANORAMA_POI_INDOOR_SCENE;

/**
 * @type {string}
 * @const
 */
var BMAP_PANORAMA_POI_NONE;

/**
 * @type {number}
 * @const
 */
var BMAP_POI_TYPE_NORMAL;

/**
 * @type {number}
 * @const
 */
var BMAP_POI_TYPE_BUSSTOP;

/**
 * @type {number}
 * @const
 */
var BMAP_POI_TYPE_SUBSTOP;

/**
 * @type {number}
 * @const
 */
var BMAP_TRANSIT_POLICY_LEAST_TIME;

/**
 * @type {number}
 * @const
 */
var BMAP_TRANSIT_POLICY_LEAST_TRANSFER;

/**
 * @type {number}
 * @const
 */
var BMAP_TRANSIT_POLICY_LEAST_WALKING;

/**
 * @type {number}
 * @const
 */
var BMAP_TRANSIT_POLICY_AVOID_SUBWAYS;

/**
 * @type {number}
 * @const
 */
var BMAP_LINE_TYPE_BUS;

/**
 * @type {number}
 * @const
 */
var BMAP_LINE_TYPE_SUBWAY;

/**
 * @type {number}
 * @const
 */
var BMAP_LINE_TYPE_FERRY;

/**
 * @type {number}
 * @const
 */
var BMAP_DRIVING_POLICY_LEAST_TIME;

/**
 * @type {number}
 * @const
 */
var BMAP_DRIVING_POLICY_LEAST_DISTANCE;

/**
 * @type {number}
 * @const
 */
var BMAP_DRIVING_POLICY_AVOID_HIGHWAYS;

/**
 * @type {number}
 * @const
 */
var BMAP_ROUTE_TYPE_DRIVING;

/**
 * @type {number}
 * @const
 */
var BMAP_ROUTE_TYPE_WALKING;

/**
 * @type {number}
 * @const
 */
var BMAP_HIGHLIGHT_STEP;

/**
 * @type {number}
 * @const
 */
var BMAP_HIGHLIGHT_ROUTE;
