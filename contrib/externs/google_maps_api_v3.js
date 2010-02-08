/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @fileoverview Externs for the Google Maps V3 API.
 * @see http://code.google.com/apis/maps/documentation/v3/reference.html
 */

google.maps = {};

/**
 * @constructor
 * @extends {google.maps.MVCObject}
 * @param {Node} mapDiv
 * @param {Object.<string, *>=} opt_mapOptions
 */
google.maps.Map = function(mapDiv, opt_mapOptions) {};

/**
 * @param {google.maps.LatLngBounds} bounds
 */
google.maps.Map.prototype.fitBounds = function(bounds) {};

/**
 * @nosideeffects
 * @return {google.maps.LatLngBounds}
 */
google.maps.Map.prototype.getBounds = function() {};

/**
 * @nosideeffects
 * @return {google.maps.LatLng}
 */
google.maps.Map.prototype.getCenter = function() {};

/**
 * @nosideeffects
 * @return {Node}
 */
google.maps.Map.prototype.getDiv = function() {};

/**
 * @nosideeffects
 * @return {google.maps.MapTypeId}
 */
google.maps.Map.prototype.getMapTypeId = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
google.maps.Map.prototype.getZoom = function() {};

/**
 * @param {number} x
 * @param {number} y
 */
google.maps.Map.prototype.panBy = function(x, y) {};

/** @param {google.maps.LatLng} LatLng */
google.maps.Map.prototype.panTo = function(LatLng) {};

/** @param {google.maps.LatLngBounds} LatLngBounds */
google.maps.Map.prototype.panToBounds = function(LatLngBounds) {};

/** @param {google.maps.LatLng} latLng */
google.maps.Map.prototype.setCenter = function(latLng) {};

/** @param {google.maps.MapTypeId} mapTypeId */
google.maps.Map.prototype.setMapTypeId = function(mapTypeId) {};

/** @param {Object.<string,*>} options */
google.maps.Map.prototype.setOptions = function(options) {};

/** @param {number} zoom */
google.maps.Map.prototype.setZoom = function(zoom) {};

/** @type {google.maps.MVCArray} */
google.maps.Map.prototype.controls;

/** @type {Array.<string>} */
google.maps.Map.prototype.mapTypes;

/** @type {google.maps.MVCArray} */
google.maps.Map.prototype.overlayMapTypes;

/**
 * @enum {string}
 */
google.maps.MapTypeId = {
  HYBRID: '0',
  ROADMAP: '1',
  SATELLITE: '2',
  TERRAIN: '3'
};

/**
 * @enum
 */
google.maps.MapTypeControlStyle = {
  DEFAULT: 0,
  DROPDOWN_MENU: 1,
  HORIZONTAL_BAR: 2
};

/**
 * @enum
 */
google.maps.NavigationControlStyle = {
  ANDROID: 0,
  DEFAULT: 1,
  SMALL: 2,
  ZOOM_PAN: 3
};

/** @enum */
google.maps.ScaleControlStyle = {DEFAULT: 0};

/** @enum */
google.maps.ControlPosition = {
  BOTTOM: 0,
  BOTTOM_LEFT: 1,
  BOTTOM_RIGHT: 2,
  LEFT: 3,
  RIGHT: 4,
  TOP: 5,
  TOP_LEFT: 6,
  TOP_RIGHT: 7
};

/** @constructor */
google.maps.MapPanes = function() {};

/** @type {Node} */
google.maps.MapPanes.prototype.floatPane;

/** @type {Node} */
google.maps.MapPanes.prototype.floatShadow;

/** @type {Node} */
google.maps.MapPanes.prototype.mapPane;

/** @type {Node} */
google.maps.MapPanes.prototype.overlayImage;

/** @type {Node} */
google.maps.MapPanes.prototype.overlayLayer;

/** @type {Node} */
google.maps.MapPanes.prototype.overlayMouseTarget;

/** @type {Node} */
google.maps.MapPanes.prototype.overlayShadow;

/** @constructor */
google.maps.MapCanvasProjection = function() {};

/**
 * @nosideeffects
 * @param {google.maps.Point} pixel
 * @return {google.maps.LatLng}
 */
google.maps.MapCanvasProjection.prototype.fromContainerPixelToLatLng =
    function(pixel) {};

/**
 * @nosideeffects
 * @param {google.maps.Point} pixel
 * @return {google.maps.LatLng}
 */
google.maps.MapCanvasProjection.prototype.fromDivPixelToLatLng =
    function(pixel) {};

/**
 * @nosideeffects
 * @param {google.maps.LatLng} latLng
 * @return {google.maps.Point}
 */
google.maps.MapCanvasProjection.prototype.fromLatLngToContainerPixel =
    function(latLng) {};

/**
 * @nosideeffects
 * @param {google.maps.LatLng} latLng
 * @return {google.maps.Point}
 */
google.maps.MapCanvasProjection.prototype.fromLatLngToDivPixel =
    function(latLng) {};

/**
 * @nosideeffects
 * @return {number}
 */
google.maps.MapCanvasProjection.prototype.getWorldWidth = function() {};

/**
 * @constructor
 * @extends {google.maps.MVCObject}
 * @param {Object.<string, *>=} opt_opts
 */
google.maps.Marker = function(opt_opts) {};

/**
 * @nosideeffects
 * @return {boolean}
 */
google.maps.Marker.prototype.getClickable = function() {};

/**
 * @nosideeffects
 * @return {string}
 */
google.maps.Marker.prototype.getCursor = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
google.maps.Marker.prototype.getDraggable = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
google.maps.Marker.prototype.getFlat = function() {};

/**
 * @nosideeffects
 * @return {(string|Object.<string,*>)}
 */
google.maps.Marker.prototype.getIcon = function() {};

/**
 * @nosideeffects
 * @return {google.maps.Map}
 */
google.maps.Marker.prototype.getMap = function() {};

/**
 * @nosideeffects
 * @return {google.maps.LatLng}
 */
google.maps.Marker.prototype.getPosition = function() {};

/**
 * @nosideeffects
 * @return {(string|Object.<string,*>)}
 */
google.maps.Marker.prototype.getShadow = function() {};

/**
 * @nosideeffects
 * @return {Object}
 */
google.maps.Marker.prototype.getShape = function() {};

/**
 * @nosideeffects
 * @return {string}
 */
google.maps.Marker.prototype.getTitle = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
google.maps.Marker.prototype.getVisible = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
google.maps.Marker.prototype.getZIndex= function() {};

/** @param {boolean} clickable */
google.maps.Marker.prototype.setClickable = function(clickable) {};

/** @param {string} cursor */
google.maps.Marker.prototype.setCursor = function(cursor) {};

/** @param {boolean} draggable */
google.maps.Marker.prototype.setDraggable = function(draggable) {};

/** @param {boolean} flat */
google.maps.Marker.prototype.setFlat = function(flat) {};

/** @param {(string|Object.<string,*>)} icon */
google.maps.Marker.prototype.setIcon = function(icon) {};

/** @param {google.maps.Map} map */
google.maps.Marker.prototype.setMap = function(map) {};

/** @param {Object.<string, *>} options */
google.maps.Marker.prototype.setOptions = function(options) {};

/** @param {google.maps.LatLng} position */
google.maps.Marker.prototype.setPosition = function(position) {};

/** @param {(string|Object.<string,*>)} shadow */
google.maps.Marker.prototype.setShadow = function(shadow) {};

/** @param {Object} shape */
google.maps.Marker.prototype.setShape = function(shape) {};

/** @param {string} title */
google.maps.Marker.prototype.setTitle = function(title) {};

/** @param {boolean} visible */
google.maps.Marker.prototype.setVisible = function(visible) {};

/** @param {number} zIndex */
google.maps.Marker.prototype.setZIndex= function(zIndex) {};

/**
 * @constructor
 * @param {string} url
 * @param {google.maps.Size} size
 * @param {google.maps.Point} origin
 * @param {google.maps.Point=} anchor
 */
google.maps.MarkerImage = function(url, size, origin, anchor) {};

/**
 * @constructor
 * @extends {google.maps.MVCObject}
 * @param {Object.<string, *>=} opt_opts
 */
google.maps.Polyline = function(opt_opts) {};

/**
 * @nosideeffects
 * @return {google.maps.Map}
 */
google.maps.Polyline.prototype.getMap = function() {};

/**
 * @nosideeffects
 * @return {google.maps.MVCArray}
 */
google.maps.Polyline.prototype.getPath = function() {};

/**
 * @param {google.maps.Map} map
 */
google.maps.Polyline.prototype.setMap = function(map) {};

/**
 * @param {Object.<string, *>} opts
 */
google.maps.Polyline.prototype.setOptions = function(opts) {};

/**
 * @param {(google.maps.MVCArray|Array.<google.maps.LatLng>)} path
 */
google.maps.Polyline.prototype.setPath = function(path) {};

/**
 * @constructor
 * @extends {google.maps.MVCObject}
 * @param {Object.<string, *>=} opt_opts
 */
google.maps.Polygon = function(opt_opts) {};

/**
 * @nosideeffects
 * @return {google.maps.Map}
 */
google.maps.Polygon.prototype.getMap = function() {};

/**
 * @return {google.maps.MVCArray}
 */
google.maps.Polygon.prototype.getPath = function() {};

/**
 * @return {google.maps.MVCArray}
 */
google.maps.Polygon.prototype.getPaths = function() {};

/**
 * @param {google.maps.Map} map
 */
google.maps.Polygon.prototype.setMap = function(map) {};

/**
 * @param {Object.<string, *>} opts
 */
google.maps.Polygon.prototype.setOptions = function(opts) {};

/**
 * @param {(google.maps.MVCArray|Array.<google.maps.LatLng>)} path
 */
google.maps.Polygon.prototype.setPath = function(path) {};

/**
 * @param {(google.maps.MVCArray|Array.<Array.<google.maps.LatLng>>)} paths
 */
google.maps.Polygon.prototype.setPaths = function(paths) {};

/**
 * @constructor
 * @extends {google.maps.MVCObject}
 * @param {Object.<string, *>=} opt_opts
 */
google.maps.InfoWindow = function(opt_opts) {};

google.maps.InfoWindow.prototype.close = function() {};

/**
 * @nosideeffects
 * @return {(string|Node)}
 */
google.maps.InfoWindow.prototype.getContent = function() {};

/**
 * @nosideeffects
 * @return {google.maps.LatLng}
 */
google.maps.InfoWindow.prototype.getPosition = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
google.maps.InfoWindow.prototype.getZIndex = function() {};

/**
 * @param {google.maps.Map} map
 * @param {?google.maps.MVCObject} anchor
 */
google.maps.InfoWindow.prototype.open = function(map, anchor) {};

/** @param {(string|Node)} content */
google.maps.InfoWindow.prototype.setContent = function(content) {};

/** @param {Object.<string, *>} options */
google.maps.InfoWindow.prototype.setOptions = function(options) {};

/** @param {google.maps.LatLng} position */
google.maps.InfoWindow.prototype.setPosition = function(position) {};

/** @param {number} zIndex */
google.maps.InfoWindow.prototype.setZIndex = function(zIndex) {};

/** @constructor */
google.maps.Geocoder = function() {};

/**
 * @param {Object.<string, *>} req
 * @param {function(Array.<Object.<string, *>>,
 *         google.maps.GeocoderStatus)} callback
 */
google.maps.Geocoder.prototype.geocode = function(req, callback) {};

/**
 * This type is not in the API reference. We use it purely to
 * help specify the GeocoderResponse.
 * @constructor
 * @private
 */
google.maps.AddressComponent_ = function() {};

/** @type {string} */
google.maps.AddressComponent_.prototype.short_name;

/** @type {string} */
google.maps.AddressComponent_.prototype.long_name;

/** @type {Array.<string>} */
google.maps.AddressComponent_.prototype.types;

/** @constructor */
google.maps.GeocoderResponse = function() {};

/** @type {Array.<string>} */
google.maps.GeocoderResponse.prototype.types;

/** @type {string} */
google.maps.GeocoderResponse.prototype.formatted_address;

/** @type {Array.<google.maps.AddressComponent_>} */
google.maps.GeocoderResponse.prototype.address_components;

google.maps.GeocoderResponse.prototype.geometry = {};

/** @type {google.maps.LatLng} */
google.maps.GeocoderResponse.prototype.geometry.location;

/** @type {google.maps.GeocoderLocationType} */
google.maps.GeocoderResponse.prototype.geometry.location_type;

/** @type {google.maps.LatLngBounds} */
google.maps.GeocoderResponse.prototype.geometry.viewport;

/** @type {google.maps.LatLngBounds|undefined} */
google.maps.GeocoderResponse.prototype.geometry.bounds;

/** @enum */
google.maps.GeocoderStatus = {
  ERROR: 0,
  INVALID_REQUEST: 1,
  OK: 2,
  OVER_QUERY_LIMIT: 3,
  REQUEST_DENIED: 4,
  UNKNOWN_ERROR: 5,
  ZERO_RESULTS: 6
};

/** @enum */
google.maps.GeocoderLocationType = {
  APPROXIMATE: 1,
  GEOMETRIC_CENTER: 2,
  RANGE_INTERPOLATED: 3,
  ROOFTOP: 4
};

/**
 * @constructor
 * @extends {google.maps.MVCObject}
 * @param {Object.<string, *>} opts
 */
google.maps.DirectionsRenderer = function(opts) {};

/**
 * @nosideeffects
 * @return {google.maps.DirectionsResult}
 */
google.maps.DirectionsRenderer.prototype.getDirections = function() {};

/**
 * @nosideeffects
 * @return {google.maps.Map}
 */
google.maps.DirectionsRenderer.prototype.getMap = function() {};

/**
 * @nosideeffects
 * @return {Node}
 */
google.maps.DirectionsRenderer.prototype.getPanel = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
google.maps.DirectionsRenderer.prototype.getTripIndex = function() {};

/** @param {google.maps.DirectionsResult} dir */
google.maps.DirectionsRenderer.prototype.setDirections = function(dir) {};

/** @param {google.maps.Map} map */
google.maps.DirectionsRenderer.prototype.setMap = function(map) {};

/** @param {Node} elem */
google.maps.DirectionsRenderer.prototype.setPanel = function(elem) {};

/** @param {number} index */
google.maps.DirectionsRenderer.prototype.setTripIndex = function(index) {};

/** @constructor */
google.maps.DirectionsService = function() {};

/**
 * @param {Object.<string, *>} request
 * @param {function(google.maps.DirectionsResult,
 *                  google.maps.DirectionsStatus)} callback
 */
google.maps.DirectionsService.prototype.route =
    function(request, callback) {};

/** @enum */
google.maps.DirectionsTravelMode = {
  DRIVING: 0,
  WALKING: 1
};

/** @enum */
google.maps.DirectionsUnitSystem = {
  IMPERIAL: 0,
  METRIC: 1
};

/** @enum */
google.maps.DirectionsStatus = {
  INVALID_REQUEST: 0,
  MAX_WAYPOINTS_EXCEEDED: 1,
  NOT_FOUND: 2,
  OK: 3,
  OVER_QUERY_LIMIT: 4,
  REQUEST_DENIED: 5,
  UNKNOWN_ERROR: 6,
  ZERO_RESULTS: 7
};

/** @constructor */
google.maps.DirectionsResult = function() {};

/** @type {Array.<google.maps.DirectionsTrip>} */
google.maps.DirectionsResult.prototype.trips;

/** @constructor */
google.maps.DirectionsTrip = function() {};

/** @type {string} */
google.maps.DirectionsTrip.prototype.copyrights;

/** @type {Array.<google.maps.DirectionsRoute>} */
google.maps.DirectionsTrip.prototype.routes;

/** @type {Array.<string>} */
google.maps.DirectionsTrip.prototype.warnings;

/** @constructor */
google.maps.DirectionsRoute = function() {};

/** @type {google.maps.DirectionsDistance} */
google.maps.DirectionsRoute.prototype.distance;

/** @type {Array.<google.maps.DirectionsDuration>} */
google.maps.DirectionsRoute.prototype.duration;

/** @type {google.maps.GeocoderResponse} */
google.maps.DirectionsRoute.prototype.end_geocode;

/** @type {google.maps.GeocoderResponse} */
google.maps.DirectionsRoute.prototype.start_geocode;

/** @type {Array.<google.maps.DirectionsStep>} */
google.maps.DirectionsRoute.prototype.steps;

/** @constructor */
google.maps.DirectionsStep = function() {};

/** @type {google.maps.DirectionsDistance} */
google.maps.DirectionsStep.prototype.distance;

/** @type {google.maps.DirectionsDuration} */
google.maps.DirectionsStep.prototype.duration;

/** @type {string} */
google.maps.DirectionsStep.prototype.endcoded_lat_lngs;

/** @type {google.maps.LatLng} */
google.maps.DirectionsStep.prototype.end_point;

/** @type {string} */
google.maps.DirectionsStep.prototype.instructions;

/** @type {Array.<google.maps.LatLng>} */
google.maps.DirectionsStep.prototype.lat_lngs;

/** @type {google.maps.LatLng} */
google.maps.DirectionsStep.prototype.start_point;

/** @constructor */
google.maps.DirectionsDistance = function() {};

/** @type {string} */
google.maps.DirectionsDistance.prototype.text;

/** @type {number} */
google.maps.DirectionsDistance.prototype.value;

/** @constructor */
google.maps.DirectionsDuration = function() {};

/** @type {string} */
google.maps.DirectionsDuration.prototype.text;

/** @type {number} */
google.maps.DirectionsDuration.prototype.value;

/**
 * @constructor
 * @extends {google.maps.MVCObject}
 */
google.maps.OverlayView = function() {};

google.maps.OverlayView.prototype.draw = function() {};

/**
 * @nosideeffects
 * @return {google.maps.Map}
 */
google.maps.OverlayView.prototype.getMap = function() {};

/**
 * @nosideeffects
 * @return {google.maps.MapPanes}
 */
google.maps.OverlayView.prototype.getPanes = function() {};

/**
 * @nosideeffects
 * @return {google.maps.MapCanvasProjection}
 */
google.maps.OverlayView.prototype.getProjection = function() {};

google.maps.OverlayView.prototype.onAdd = function() {};

google.maps.OverlayView.prototype.onRemove = function() {};

/** @param {google.maps.Map} map */
google.maps.OverlayView.prototype.setMap = function(map) {};

/**
 * @interface
 */
google.maps.MapType = function() {};

/**
 * @param {google.maps.Point} tileCoord
 * @param {number} zoom
 * @param {Node} doc
 */
google.maps.MapType.prototype.getTile = function(tileCoord, zoom, doc) {};

/**
 * @param {Node} tile
 */
google.maps.MapType.prototype.releaseTile = function(tile) {};

/** @type {string} */
google.maps.MapType.prototype.alt;

/** @type {number} */
google.maps.MapType.prototype.maxZoom;

/** @type {number} */
google.maps.MapType.prototype.minZoom;

/** @type {string} */
google.maps.MapType.prototype.name;

/** @type {string} */
google.maps.MapType.prototype.projection;

/** @type {google.maps.Size} */
google.maps.MapType.prototype.tileSize;

/**
 * @constructor
 * @implements {google.maps.MapType}
 * @param {Object.<string, *>} a
 */
google.maps.ImageMapType = function(a) {};

/**
 * @param {google.maps.Point} tileCoord
 * @param {number} zoom
 * @param {Node} doc
 */
google.maps.ImageMapType.prototype.getTile = function(tileCoord, zoom, doc) {};

/**
 * @param {Node} tile
 */
google.maps.ImageMapType.prototype.releaseTile = function(tile) {};

/** @type {string} */
google.maps.ImageMapType.prototype.alt;

/**
 * @param {google.maps.Point} point
 * @param {number} zoom
 * @return {string}
 */
google.maps.ImageMapType.prototype.getTileUrl = function(point, zoom) {};

/** @type {boolean} */
google.maps.ImageMapType.prototype.isPng;

/** @type {number} */
google.maps.ImageMapType.prototype.maxZoom;

/** @type {number} */
google.maps.ImageMapType.prototype.minZoom;

/** @type {string} */
google.maps.ImageMapType.prototype.name;

/** @type {string} */
google.maps.ImageMapType.prototype.projection;

/** @type {google.maps.Size} */
google.maps.ImageMapType.prototype.tileSize;

/**
 * @constructor
 * @extends {google.maps.MVCObject}
 * @param {Object.<string, *>=} opts
 */
google.maps.Rectangle = function(opts) {};

/**
 * @nosideeffects
 * @returns {google.maps.LatLngBounds} 
 */
google.maps.Rectangle.prototype.getBounds = function() {};

/**
 * @nosideeffects
 * @returns {google.maps.Map} 
 */
google.maps.Rectangle.prototype.getMap = function() {};

/** @param {google.maps.LatLngBounds} bounds */
google.maps.Rectangle.prototype.setBounds = function(bounds) {};

/** @param {google.maps.Map} map */
google.maps.Rectangle.prototype.setMap = function(map) {};

/** @param {Object.<string, *>} options */
google.maps.Rectangle.prototype.setOptions = function(options) {};

/**
 * @constructor
 * @extends {google.maps.MVCObject}
 * @param {Object.<string, *>=} opts
 */
google.maps.Circle = function(opts) {};

/**
 * @nosideeffects
 * @returns {google.maps.LatLng} 
 */
google.maps.Circle.prototype.getCenter = function() {};

/**
 * @nosideeffects
 * @returns {google.maps.Map} 
 */
google.maps.Circle.prototype.getMap = function() {};

/**
 * @nosideeffects
 * @returns {number} 
 */
google.maps.Circle.prototype.getRadius = function() {};

/** @param {google.maps.LatLng} center */
google.maps.Circle.prototype.setCenter = function(center) {};

/** @param {google.maps.Map} map */
google.maps.Circle.prototype.setMap = function(map) {};

/** @param {Object.<string, *>} options */
google.maps.Circle.prototype.setOptions = function(options) {};

/** @param {number} radius */
google.maps.Circle.prototype.setRadius = function(radius) {};

/** @constructor */
google.maps.Projection = function() {};

/**
 * @nosideeffects
 * @param {google.maps.LatLng} latLng
 * @param {google.maps.Point=} point
 * @return {google.maps.Point}
 */
google.maps.Projection.prototype.fromLatLngToPoint = function(latLng, point) {};

/**
 * @nosideeffects
 * @param {google.maps.Point} pixel
 * @param {boolean=} nowrap
 * @return {google.maps.LatLng}
 */
google.maps.Projection.prototype.fromPointToLatLng = function(pixel, nowrap) {};

/**
 * @constructor
 * @private
 */
google.maps.MapsEventListener = function() {};

google.maps.event = {};

/**
 * @param {Object} instance
 * @param {string} eventName
 * @param {function(...[*])} handler
 * @return {google.maps.MapsEventListener}
 */
google.maps.event.addDomListener =
    function(instance, eventName, handler) {};

/**
 * @param {Object} instance
 * @param {string} eventName
 * @param {function(...[*])} handler
 * @return {google.maps.MapsEventListener}
 */
google.maps.event.addDomListenerOnce =
    function(instance, eventName, handler) {};

/**
 * @param {Object} instance
 * @param {string} eventName
 * @param {function(?google.maps.MouseEvent)} handler
 * @return {google.maps.MapsEventListener}
 */
google.maps.event.addListener = function(instance, eventName, handler) {};

/**
 * @param {Object} instance
 * @param {string} eventName
 * @param {function(?google.maps.MouseEvent)} handler
 * @return {google.maps.MapsEventListener}
 */
google.maps.event.addListenerOnce = function(instance, eventName, handler) {};

/**
 * @param {Object} instance
 */
google.maps.event.clearInstanceListeners = function(instance) {};

/**
 * @param {Object} instance
 * @param {string} eventName
 */
google.maps.event.clearListeners = function(instance, eventName) {};

/**
 * @param {google.maps.MapsEventListener} listener
 */
google.maps.event.removeListener = function(listener) {};

/**
 * @param {Object} instance
 * @param {string} eventName
 * @param {...[*]} var_args
 */
google.maps.event.trigger = function(instance, eventName, var_args) {};

/** @constructor */
google.maps.MouseEvent = function() {};

/** @type {google.maps.LatLng} */
google.maps.MouseEvent.prototype.latLng;

/**
 * @constructor
 * @param {number} lat
 * @param {number} lng
 * @param {boolean=} opt_noWrap
 */
google.maps.LatLng = function(lat, lng, opt_noWrap) {};

/**
 * @nosideeffects
 * @param {google.maps.LatLng} other
 * @return {boolean}
 */
google.maps.LatLng.prototype.equals = function(other) {};

/**
 * @nosideeffects
 * @return {number}
 */
google.maps.LatLng.prototype.lat = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
google.maps.LatLng.prototype.lng = function() {};

/**
 * @nosideeffects
 * @return {string}
 */
google.maps.LatLng.prototype.toString = function() {};

/**
 * @nosideeffects
 * @param {number=} opt_precision
 * @return {string}
 */
google.maps.LatLng.prototype.toUrlValue = function(opt_precision) {};

/**
 * @constructor
 * @param {?google.maps.LatLng} sw
 * @param {?google.maps.LatLng} ne
 */
google.maps.LatLngBounds = function(sw, ne) {};

/**
 * @nosideeffects
 * @param {google.maps.LatLng} LatLng
 * @return {boolean}
 */
google.maps.LatLngBounds.prototype.contains = function(LatLng) {};

/**
 * @nosideeffects
 * @param {google.maps.LatLngBounds} other
 * @return {boolean}
 */
google.maps.LatLngBounds.prototype.equals = function(other) {};

/**
 * @param {google.maps.LatLng} LatLng
 * @return {google.maps.LatLngBounds}
 */
google.maps.LatLngBounds.prototype.extend = function(LatLng) {};

/**
 * @nosideeffects
 * @return {google.maps.LatLng}
 */
google.maps.LatLngBounds.prototype.getCenter = function() {};

/**
 * @nosideeffects
 * @return {google.maps.LatLng}
 */
google.maps.LatLngBounds.prototype.getNorthEast = function() {};

/**
 * @nosideeffects
 * @return {google.maps.LatLng}
 */
google.maps.LatLngBounds.prototype.getSouthWest = function() {};

/**
 * @nosideeffects
 * @param {google.maps.LatLngBounds} other
 * @return {boolean}
 */
google.maps.LatLngBounds.prototype.intersects = function(other) {};

/**
 * @nosideeffects
 * @return {boolean}
 */
google.maps.LatLngBounds.prototype.isEmpty = function() {};

/**
 * @nosideeffects
 * @return {google.maps.LatLng}
 */
google.maps.LatLngBounds.prototype.toSpan = function() {};

/**
 * @nosideeffects
 * @return {string}
 */
google.maps.LatLngBounds.prototype.toString = function() {};

/**
 * @nosideeffects
 * @param {?number} precision
 * @return {string}
 */
google.maps.LatLngBounds.prototype.toUrlValue = function(precision) {};

/**
 * @param {google.maps.LatLngBounds} other
 * @return {google.maps.LatLngBounds}
 */
google.maps.LatLngBounds.prototype.union = function(other) {};

/**
 * @constructor
 * @param {number} x
 * @param {number} y
 */
google.maps.Point = function(x, y) {};

/**
 * @nosideeffects
 * @param {google.maps.Point} other
 * @return {boolean}
 */
google.maps.Point.prototype.equals = function(other) {};

/**
 * @nosideeffects
 * @return {string}
 */
google.maps.Point.prototype.toString = function() {};

/** @type {number} */
google.maps.Point.prototype.x;

/** @type {number} */
google.maps.Point.prototype.y;

/**
 * @constructor
 * @param {number} width
 * @param {number} height
 */
google.maps.Size = function(width, height) {};

/**
 * @nosideeffects
 * @param {google.maps.Size} other
 * @return {boolean}
 */
google.maps.Size.prototype.equals = function(other) {};

/**
 * @nosideeffects
 * @return {string}
 */
google.maps.Size.prototype.toString = function() {};

/** @type {number} */
google.maps.Size.prototype.height;

/** @type {number} */
google.maps.Size.prototype.width;

/** @constructor */
google.maps.MVCObject = function() {};

/**
 * @param {string} key
 * @param {google.maps.MVCObject} target
 * @param {string} targetKey
 * @param {boolean=} opt_noNotify
 */
google.maps.MVCObject.prototype.bindTo =
    function(key, target, targetKey, opt_noNotify) {};

/** @param {string} key */
google.maps.MVCObject.prototype.changed = function(key) {};

/**
 * @nosideeffects
 * @param {string} key
 * @return {*}
 */
google.maps.MVCObject.prototype.get = function(key) {};

/** @param {string} key */
google.maps.MVCObject.prototype.notify = function(key) {};

/**
 * @param {string} key
 * @param {*} value
 */
google.maps.MVCObject.prototype.set = function(key, value) {};

/** @param {Object.<string, *>=} opt_values */
google.maps.MVCObject.prototype.setValues = function(opt_values) {};

/** @param {string} key */
google.maps.MVCObject.prototype.unbind = function(key) {};

google.maps.MVCObject.prototype.unbindAll = function() {};

/**
 * @constructor
 * @extends {google.maps.MVCObject}
 */
google.maps.MVCArray = function() {};

/** @param {function(*, number)} callback */
google.maps.MVCArray.prototype.forEach = function(callback) {};

/**
 * @nosideeffects
 * @param {number} i
 * @return {*}
 */
google.maps.MVCArray.prototype.getAt = function(i) {};

/**
 * @nosideeffects
 * @return {number}
 */
google.maps.MVCArray.prototype.getLength = function() {};

/**
 * @param {number} i
 * @param {*} elem
 */
google.maps.MVCArray.prototype.insertAt = function(i, elem) {};

/** @return {*} */
google.maps.MVCArray.prototype.pop = function() {};

/** @param {*} elem */
google.maps.MVCArray.prototype.push = function(elem) {};

/** @param {number} i */
google.maps.MVCArray.prototype.removeAt = function(i) {};

/**
 * @param {number} i
 * @param {*} elem
 */
google.maps.MVCArray.prototype.setAt = function(i, elem) {};
