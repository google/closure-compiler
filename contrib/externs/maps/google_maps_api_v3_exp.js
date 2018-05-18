/*
 * Copyright 2010 The Closure Compiler Authors.
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
 * @fileoverview Externs for the Google Maps v3 API.
 * @see http://code.google.com/apis/maps/documentation/javascript/reference.html
 * @externs
 */

/**
 * @const
 * @suppress {const,duplicate,strictMissingProperties}
 */
var google = {};

/** @const */
google.maps = {};

/**
 * @enum {number}
 */
google.maps.Animation = {
  BOUNCE: 0,
  DROP: 1
};

/**
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.BicyclingLayer = function() {};

/**
 * @return {google.maps.Map}
 */
google.maps.BicyclingLayer.prototype.getMap = function() {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.BicyclingLayer.prototype.setMap = function(map) {};

/**
 * @param {google.maps.CircleOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.Circle = function(opts) {};

/**
 * @return {google.maps.LatLngBounds}
 */
google.maps.Circle.prototype.getBounds = function() {};

/**
 * @return {google.maps.LatLng}
 */
google.maps.Circle.prototype.getCenter = function() {};

/**
 * @return {boolean}
 */
google.maps.Circle.prototype.getDraggable = function() {};

/**
 * @return {boolean}
 */
google.maps.Circle.prototype.getEditable = function() {};

/**
 * @return {google.maps.Map}
 */
google.maps.Circle.prototype.getMap = function() {};

/**
 * @return {number}
 */
google.maps.Circle.prototype.getRadius = function() {};

/**
 * @return {boolean}
 */
google.maps.Circle.prototype.getVisible = function() {};

/**
 * @param {google.maps.LatLng|google.maps.LatLngLiteral} center
 * @return {undefined}
 */
google.maps.Circle.prototype.setCenter = function(center) {};

/**
 * @param {boolean} draggable
 * @return {undefined}
 */
google.maps.Circle.prototype.setDraggable = function(draggable) {};

/**
 * @param {boolean} editable
 * @return {undefined}
 */
google.maps.Circle.prototype.setEditable = function(editable) {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.Circle.prototype.setMap = function(map) {};

/**
 * @param {google.maps.CircleOptions} options
 * @return {undefined}
 */
google.maps.Circle.prototype.setOptions = function(options) {};

/**
 * @param {number} radius
 * @return {undefined}
 */
google.maps.Circle.prototype.setRadius = function(radius) {};

/**
 * @param {boolean} visible
 * @return {undefined}
 */
google.maps.Circle.prototype.setVisible = function(visible) {};

/**
 * @record
 */
google.maps.CircleOptions = function() {};

/**
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.CircleOptions.prototype.center;

/**
 * @type {?boolean|undefined}
 */
google.maps.CircleOptions.prototype.clickable;

/**
 * @type {?boolean|undefined}
 */
google.maps.CircleOptions.prototype.draggable;

/**
 * @type {?boolean|undefined}
 */
google.maps.CircleOptions.prototype.editable;

/**
 * @type {?string|undefined}
 */
google.maps.CircleOptions.prototype.fillColor;

/**
 * @type {?number|undefined}
 */
google.maps.CircleOptions.prototype.fillOpacity;

/**
 * @type {?google.maps.Map|undefined}
 */
google.maps.CircleOptions.prototype.map;

/**
 * @type {?number|undefined}
 */
google.maps.CircleOptions.prototype.radius;

/**
 * @type {?string|undefined}
 */
google.maps.CircleOptions.prototype.strokeColor;

/**
 * @type {?number|undefined}
 */
google.maps.CircleOptions.prototype.strokeOpacity;

/**
 * @type {?google.maps.StrokePosition|undefined}
 */
google.maps.CircleOptions.prototype.strokePosition;

/**
 * @type {?number|undefined}
 */
google.maps.CircleOptions.prototype.strokeWeight;

/**
 * @type {?boolean|undefined}
 */
google.maps.CircleOptions.prototype.visible;

/**
 * @type {?number|undefined}
 */
google.maps.CircleOptions.prototype.zIndex;

/**
 * @enum {number}
 */
google.maps.ControlPosition = {
  BOTTOM_CENTER: 0,
  BOTTOM_LEFT: 1,
  BOTTOM_RIGHT: 2,
  LEFT_BOTTOM: 3,
  LEFT_CENTER: 4,
  LEFT_TOP: 5,
  RIGHT_BOTTOM: 6,
  RIGHT_CENTER: 7,
  RIGHT_TOP: 8,
  TOP_CENTER: 9,
  TOP_LEFT: 10,
  TOP_RIGHT: 11
};

/**
 * @param {google.maps.Data.DataOptions=} options
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.Data = function(options) {};

/**
 * @param {(google.maps.Data.Feature|google.maps.Data.FeatureOptions)=} feature
 * @return {!google.maps.Data.Feature}
 */
google.maps.Data.prototype.add = function(feature) {};

/**
 * @param {!Object} geoJson
 * @param {google.maps.Data.GeoJsonOptions=} options
 * @return {!Array<!google.maps.Data.Feature>}
 */
google.maps.Data.prototype.addGeoJson = function(geoJson, options) {};

/**
 * @param {!google.maps.Data.Feature} feature
 * @return {boolean}
 */
google.maps.Data.prototype.contains = function(feature) {};

/**
 * @param {function(!google.maps.Data.Feature)} callback
 * @return {undefined}
 */
google.maps.Data.prototype.forEach = function(callback) {};

/**
 * @return {google.maps.ControlPosition}
 */
google.maps.Data.prototype.getControlPosition = function() {};

/**
 * @return {Array<string>}
 */
google.maps.Data.prototype.getControls = function() {};

/**
 * @return {?string}
 */
google.maps.Data.prototype.getDrawingMode = function() {};

/**
 * @param {number|string} id
 * @return {google.maps.Data.Feature|undefined}
 */
google.maps.Data.prototype.getFeatureById = function(id) {};

/**
 * @return {google.maps.Map}
 */
google.maps.Data.prototype.getMap = function() {};

/**
 * @return {google.maps.Data.StylingFunction|google.maps.Data.StyleOptions}
 */
google.maps.Data.prototype.getStyle = function() {};

/**
 * @param {string} url
 * @param {google.maps.Data.GeoJsonOptions=} options
 * @param {function(!Array<!google.maps.Data.Feature>)=} callback
 * @return {undefined}
 */
google.maps.Data.prototype.loadGeoJson = function(url, options, callback) {};

/**
 * @param {!google.maps.Data.Feature} feature
 * @param {!google.maps.Data.StyleOptions} style
 * @return {undefined}
 */
google.maps.Data.prototype.overrideStyle = function(feature, style) {};

/**
 * @param {!google.maps.Data.Feature} feature
 * @return {undefined}
 */
google.maps.Data.prototype.remove = function(feature) {};

/**
 * @param {google.maps.Data.Feature=} feature
 * @return {undefined}
 */
google.maps.Data.prototype.revertStyle = function(feature) {};

/**
 * @param {google.maps.ControlPosition} controlPosition
 * @return {undefined}
 */
google.maps.Data.prototype.setControlPosition = function(controlPosition) {};

/**
 * @param {Array<string>} controls
 * @return {undefined}
 */
google.maps.Data.prototype.setControls = function(controls) {};

/**
 * @param {?string} drawingMode
 * @return {undefined}
 */
google.maps.Data.prototype.setDrawingMode = function(drawingMode) {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.Data.prototype.setMap = function(map) {};

/**
 * @param {google.maps.Data.StylingFunction|google.maps.Data.StyleOptions} style
 * @return {undefined}
 */
google.maps.Data.prototype.setStyle = function(style) {};

/**
 * @param {function(!Object)} callback
 * @return {undefined}
 */
google.maps.Data.prototype.toGeoJson = function(callback) {};

/**
 * @record
 */
google.maps.Data.AddFeatureEvent = function() {};

/**
 * @type {!google.maps.Data.Feature}
 */
google.maps.Data.AddFeatureEvent.prototype.feature;

/**
 * @record
 */
google.maps.Data.DataOptions = function() {};

/**
 * @type {!google.maps.ControlPosition|undefined}
 */
google.maps.Data.DataOptions.prototype.controlPosition;

/**
 * @type {?Array<string>|undefined}
 */
google.maps.Data.DataOptions.prototype.controls;

/**
 * @type {?string|undefined}
 */
google.maps.Data.DataOptions.prototype.drawingMode;

/**
 * @type {?(function(!google.maps.Data.Geometry): !google.maps.Data.Feature)|undefined}
 */
google.maps.Data.DataOptions.prototype.featureFactory;

/**
 * @type {!google.maps.Map}
 */
google.maps.Data.DataOptions.prototype.map;

/**
 * @type {!google.maps.Data.StylingFunction|!google.maps.Data.StyleOptions|undefined}
 */
google.maps.Data.DataOptions.prototype.style;

/**
 * @param {google.maps.Data.FeatureOptions=} options
 * @constructor
 */
google.maps.Data.Feature = function(options) {};

/**
 * @param {function(*, string)} callback
 * @return {undefined}
 */
google.maps.Data.Feature.prototype.forEachProperty = function(callback) {};

/**
 * @return {google.maps.Data.Geometry}
 */
google.maps.Data.Feature.prototype.getGeometry = function() {};

/**
 * @return {number|string|undefined}
 */
google.maps.Data.Feature.prototype.getId = function() {};

/**
 * @param {string} name
 * @return {*}
 */
google.maps.Data.Feature.prototype.getProperty = function(name) {};

/**
 * @param {string} name
 * @return {undefined}
 */
google.maps.Data.Feature.prototype.removeProperty = function(name) {};

/**
 * @param {google.maps.Data.Geometry|google.maps.LatLng|google.maps.LatLngLiteral} newGeometry
 * @return {undefined}
 */
google.maps.Data.Feature.prototype.setGeometry = function(newGeometry) {};

/**
 * @param {string} name
 * @param {*} newValue
 * @return {undefined}
 */
google.maps.Data.Feature.prototype.setProperty = function(name, newValue) {};

/**
 * @param {function(!Object)} callback
 * @return {undefined}
 */
google.maps.Data.Feature.prototype.toGeoJson = function(callback) {};

/**
 * @record
 */
google.maps.Data.FeatureOptions = function() {};

/**
 * @type {?google.maps.Data.Geometry|?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.Data.FeatureOptions.prototype.geometry;

/**
 * @type {number|string|undefined}
 */
google.maps.Data.FeatureOptions.prototype.id;

/**
 * @type {?Object|undefined}
 */
google.maps.Data.FeatureOptions.prototype.properties;

/**
 * @record
 */
google.maps.Data.GeoJsonOptions = function() {};

/**
 * @type {?string|undefined}
 */
google.maps.Data.GeoJsonOptions.prototype.idPropertyName;

/**
 * @record
 */
google.maps.Data.Geometry = function() {};

/**
 * @param {function(!google.maps.LatLng)} callback
 * @return {undefined}
 */
google.maps.Data.Geometry.prototype.forEachLatLng = function(callback) {};

/**
 * @return {string}
 */
google.maps.Data.Geometry.prototype.getType = function() {};

/**
 * @param {!Array<!google.maps.Data.Geometry|!google.maps.LatLng|!google.maps.LatLngLiteral>} elements
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.GeometryCollection = function(elements) {};

/**
 * @param {function(!google.maps.LatLng)} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.GeometryCollection.prototype.forEachLatLng = function(callback) {};

/**
 * @return {!Array<!google.maps.Data.Geometry>}
 */
google.maps.Data.GeometryCollection.prototype.getArray = function() {};

/**
 * @param {number} n
 * @return {!google.maps.Data.Geometry}
 */
google.maps.Data.GeometryCollection.prototype.getAt = function(n) {};

/**
 * @return {number}
 */
google.maps.Data.GeometryCollection.prototype.getLength = function() {};

/**
 * @return {string}
 * @override
 */
google.maps.Data.GeometryCollection.prototype.getType = function() {};

/**
 * @param {!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>} elements
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.LineString = function(elements) {};

/**
 * @param {function(!google.maps.LatLng)} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.LineString.prototype.forEachLatLng = function(callback) {};

/**
 * @return {!Array<!google.maps.LatLng>}
 */
google.maps.Data.LineString.prototype.getArray = function() {};

/**
 * @param {number} n
 * @return {!google.maps.LatLng}
 */
google.maps.Data.LineString.prototype.getAt = function(n) {};

/**
 * @return {number}
 */
google.maps.Data.LineString.prototype.getLength = function() {};

/**
 * @return {string}
 * @override
 */
google.maps.Data.LineString.prototype.getType = function() {};

/**
 * @param {!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>} elements
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.LinearRing = function(elements) {};

/**
 * @param {function(!google.maps.LatLng)} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.LinearRing.prototype.forEachLatLng = function(callback) {};

/**
 * @return {!Array<!google.maps.LatLng>}
 */
google.maps.Data.LinearRing.prototype.getArray = function() {};

/**
 * @param {number} n
 * @return {!google.maps.LatLng}
 */
google.maps.Data.LinearRing.prototype.getAt = function(n) {};

/**
 * @return {number}
 */
google.maps.Data.LinearRing.prototype.getLength = function() {};

/**
 * @return {string}
 * @override
 */
google.maps.Data.LinearRing.prototype.getType = function() {};

/**
 * @extends {google.maps.MouseEvent}
 * @record
 */
google.maps.Data.MouseEvent = function() {};

/**
 * @type {!google.maps.Data.Feature}
 */
google.maps.Data.MouseEvent.prototype.feature;

/**
 * @param {!Array<!google.maps.Data.LineString|!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>>} elements
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.MultiLineString = function(elements) {};

/**
 * @param {function(!google.maps.LatLng)} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.MultiLineString.prototype.forEachLatLng = function(callback) {};

/**
 * @return {!Array<!google.maps.Data.LineString>}
 */
google.maps.Data.MultiLineString.prototype.getArray = function() {};

/**
 * @param {number} n
 * @return {!google.maps.Data.LineString}
 */
google.maps.Data.MultiLineString.prototype.getAt = function(n) {};

/**
 * @return {number}
 */
google.maps.Data.MultiLineString.prototype.getLength = function() {};

/**
 * @return {string}
 * @override
 */
google.maps.Data.MultiLineString.prototype.getType = function() {};

/**
 * @param {!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>} elements
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.MultiPoint = function(elements) {};

/**
 * @param {function(!google.maps.LatLng)} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.MultiPoint.prototype.forEachLatLng = function(callback) {};

/**
 * @return {!Array<!google.maps.LatLng>}
 */
google.maps.Data.MultiPoint.prototype.getArray = function() {};

/**
 * @param {number} n
 * @return {!google.maps.LatLng}
 */
google.maps.Data.MultiPoint.prototype.getAt = function(n) {};

/**
 * @return {number}
 */
google.maps.Data.MultiPoint.prototype.getLength = function() {};

/**
 * @return {string}
 * @override
 */
google.maps.Data.MultiPoint.prototype.getType = function() {};

/**
 * @param {!Array<!google.maps.Data.Polygon|!Array<!google.maps.Data.LinearRing|!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>>>} elements
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.MultiPolygon = function(elements) {};

/**
 * @param {function(!google.maps.LatLng)} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.MultiPolygon.prototype.forEachLatLng = function(callback) {};

/**
 * @return {!Array<!google.maps.Data.Polygon>}
 */
google.maps.Data.MultiPolygon.prototype.getArray = function() {};

/**
 * @param {number} n
 * @return {!google.maps.Data.Polygon}
 */
google.maps.Data.MultiPolygon.prototype.getAt = function(n) {};

/**
 * @return {number}
 */
google.maps.Data.MultiPolygon.prototype.getLength = function() {};

/**
 * @return {string}
 * @override
 */
google.maps.Data.MultiPolygon.prototype.getType = function() {};

/**
 * @param {!google.maps.LatLng|!google.maps.LatLngLiteral} latLng
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.Point = function(latLng) {};

/**
 * @param {function(!google.maps.LatLng)} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.Point.prototype.forEachLatLng = function(callback) {};

/**
 * @return {!google.maps.LatLng}
 */
google.maps.Data.Point.prototype.get = function() {};

/**
 * @return {string}
 * @override
 */
google.maps.Data.Point.prototype.getType = function() {};

/**
 * @param {!Array<!google.maps.Data.LinearRing|!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>>} elements
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.Polygon = function(elements) {};

/**
 * @param {function(!google.maps.LatLng)} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.Polygon.prototype.forEachLatLng = function(callback) {};

/**
 * @return {!Array<!google.maps.Data.LinearRing>}
 */
google.maps.Data.Polygon.prototype.getArray = function() {};

/**
 * @param {number} n
 * @return {!google.maps.Data.LinearRing}
 */
google.maps.Data.Polygon.prototype.getAt = function(n) {};

/**
 * @return {number}
 */
google.maps.Data.Polygon.prototype.getLength = function() {};

/**
 * @return {string}
 * @override
 */
google.maps.Data.Polygon.prototype.getType = function() {};

/**
 * @record
 */
google.maps.Data.RemoveFeatureEvent = function() {};

/**
 * @type {!google.maps.Data.Feature}
 */
google.maps.Data.RemoveFeatureEvent.prototype.feature;

/**
 * @record
 */
google.maps.Data.RemovePropertyEvent = function() {};

/**
 * @type {!google.maps.Data.Feature}
 */
google.maps.Data.RemovePropertyEvent.prototype.feature;

/**
 * @type {string}
 */
google.maps.Data.RemovePropertyEvent.prototype.name;

/**
 * @type {*}
 */
google.maps.Data.RemovePropertyEvent.prototype.oldValue;

/**
 * @record
 */
google.maps.Data.SetGeometryEvent = function() {};

/**
 * @type {!google.maps.Data.Feature}
 */
google.maps.Data.SetGeometryEvent.prototype.feature;

/**
 * @type {!google.maps.Data.Geometry|undefined}
 */
google.maps.Data.SetGeometryEvent.prototype.newGeometry;

/**
 * @type {!google.maps.Data.Geometry|undefined}
 */
google.maps.Data.SetGeometryEvent.prototype.oldGeometry;

/**
 * @record
 */
google.maps.Data.SetPropertyEvent = function() {};

/**
 * @type {!google.maps.Data.Feature}
 */
google.maps.Data.SetPropertyEvent.prototype.feature;

/**
 * @type {string}
 */
google.maps.Data.SetPropertyEvent.prototype.name;

/**
 * @type {*}
 */
google.maps.Data.SetPropertyEvent.prototype.newValue;

/**
 * @type {*}
 */
google.maps.Data.SetPropertyEvent.prototype.oldValue;

/**
 * @record
 */
google.maps.Data.StyleOptions = function() {};

/**
 * @type {boolean|undefined}
 */
google.maps.Data.StyleOptions.prototype.clickable;

/**
 * @type {string|undefined}
 */
google.maps.Data.StyleOptions.prototype.cursor;

/**
 * @type {boolean|undefined}
 */
google.maps.Data.StyleOptions.prototype.draggable;

/**
 * @type {boolean|undefined}
 */
google.maps.Data.StyleOptions.prototype.editable;

/**
 * @type {string|undefined}
 */
google.maps.Data.StyleOptions.prototype.fillColor;

/**
 * @type {number|undefined}
 */
google.maps.Data.StyleOptions.prototype.fillOpacity;

/**
 * @type {string|!google.maps.Icon|!google.maps.Symbol|undefined}
 */
google.maps.Data.StyleOptions.prototype.icon;

/**
 * @type {!google.maps.MarkerShape|undefined}
 */
google.maps.Data.StyleOptions.prototype.shape;

/**
 * @type {string|undefined}
 */
google.maps.Data.StyleOptions.prototype.strokeColor;

/**
 * @type {number|undefined}
 */
google.maps.Data.StyleOptions.prototype.strokeOpacity;

/**
 * @type {number|undefined}
 */
google.maps.Data.StyleOptions.prototype.strokeWeight;

/**
 * @type {string|undefined}
 */
google.maps.Data.StyleOptions.prototype.title;

/**
 * @type {boolean|undefined}
 */
google.maps.Data.StyleOptions.prototype.visible;

/**
 * @type {number|undefined}
 */
google.maps.Data.StyleOptions.prototype.zIndex;

/**
 * @typedef {function(!google.maps.Data.Feature): !google.maps.Data.StyleOptions}
 */
google.maps.Data.StylingFunction;

/**
 * @record
 */
google.maps.DirectionsGeocodedWaypoint = function() {};

/**
 * @type {boolean}
 */
google.maps.DirectionsGeocodedWaypoint.prototype.partial_match;

/**
 * @type {string}
 */
google.maps.DirectionsGeocodedWaypoint.prototype.place_id;

/**
 * @type {Array<string>}
 */
google.maps.DirectionsGeocodedWaypoint.prototype.types;

/**
 * @record
 */
google.maps.DirectionsLeg = function() {};

/**
 * @type {google.maps.Time}
 */
google.maps.DirectionsLeg.prototype.arrival_time;

/**
 * @type {google.maps.Time}
 */
google.maps.DirectionsLeg.prototype.departure_time;

/**
 * @type {google.maps.Distance}
 */
google.maps.DirectionsLeg.prototype.distance;

/**
 * @type {google.maps.Duration}
 */
google.maps.DirectionsLeg.prototype.duration;

/**
 * @type {google.maps.Duration}
 */
google.maps.DirectionsLeg.prototype.duration_in_traffic;

/**
 * @type {string}
 */
google.maps.DirectionsLeg.prototype.end_address;

/**
 * @type {google.maps.LatLng}
 */
google.maps.DirectionsLeg.prototype.end_location;

/**
 * @type {string}
 */
google.maps.DirectionsLeg.prototype.start_address;

/**
 * @type {google.maps.LatLng}
 */
google.maps.DirectionsLeg.prototype.start_location;

/**
 * @type {Array<google.maps.DirectionsStep>}
 */
google.maps.DirectionsLeg.prototype.steps;

/**
 * @type {Array<google.maps.LatLng>}
 */
google.maps.DirectionsLeg.prototype.via_waypoints;

/**
 * @param {google.maps.DirectionsRendererOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.DirectionsRenderer = function(opts) {};

/**
 * @return {google.maps.DirectionsResult}
 */
google.maps.DirectionsRenderer.prototype.getDirections = function() {};

/**
 * @return {google.maps.Map}
 */
google.maps.DirectionsRenderer.prototype.getMap = function() {};

/**
 * @return {Node}
 */
google.maps.DirectionsRenderer.prototype.getPanel = function() {};

/**
 * @return {number}
 */
google.maps.DirectionsRenderer.prototype.getRouteIndex = function() {};

/**
 * @param {google.maps.DirectionsResult} directions
 * @return {undefined}
 */
google.maps.DirectionsRenderer.prototype.setDirections = function(directions) {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.DirectionsRenderer.prototype.setMap = function(map) {};

/**
 * @param {google.maps.DirectionsRendererOptions} options
 * @return {undefined}
 */
google.maps.DirectionsRenderer.prototype.setOptions = function(options) {};

/**
 * @param {Node} panel
 * @return {undefined}
 */
google.maps.DirectionsRenderer.prototype.setPanel = function(panel) {};

/**
 * @param {number} routeIndex
 * @return {undefined}
 */
google.maps.DirectionsRenderer.prototype.setRouteIndex = function(routeIndex) {};

/**
 * @record
 */
google.maps.DirectionsRendererOptions = function() {};

/**
 * @type {?google.maps.DirectionsResult|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.directions;

/**
 * @type {?boolean|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.draggable;

/**
 * @type {?boolean|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.hideRouteList;

/**
 * @type {?google.maps.InfoWindow|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.infoWindow;

/**
 * @type {?google.maps.Map|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.map;

/**
 * @type {?google.maps.MarkerOptions|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.markerOptions;

/**
 * @type {?Node|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.panel;

/**
 * @type {?google.maps.PolylineOptions|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.polylineOptions;

/**
 * @type {?boolean|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.preserveViewport;

/**
 * @type {?number|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.routeIndex;

/**
 * @type {?boolean|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.suppressBicyclingLayer;

/**
 * @type {?boolean|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.suppressInfoWindows;

/**
 * @type {?boolean|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.suppressMarkers;

/**
 * @type {?boolean|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.suppressPolylines;

/**
 * @record
 */
google.maps.DirectionsRequest = function() {};

/**
 * @type {boolean|undefined}
 */
google.maps.DirectionsRequest.prototype.avoidFerries;

/**
 * @type {boolean|undefined}
 */
google.maps.DirectionsRequest.prototype.avoidHighways;

/**
 * @type {boolean|undefined}
 */
google.maps.DirectionsRequest.prototype.avoidTolls;

/**
 * @type {string|!google.maps.LatLng|!google.maps.Place|!google.maps.LatLngLiteral}
 */
google.maps.DirectionsRequest.prototype.destination;

/**
 * @type {!google.maps.DrivingOptions|undefined}
 */
google.maps.DirectionsRequest.prototype.drivingOptions;

/**
 * @type {boolean|undefined}
 */
google.maps.DirectionsRequest.prototype.optimizeWaypoints;

/**
 * @type {string|!google.maps.LatLng|!google.maps.Place|!google.maps.LatLngLiteral}
 */
google.maps.DirectionsRequest.prototype.origin;

/**
 * @type {boolean|undefined}
 */
google.maps.DirectionsRequest.prototype.provideRouteAlternatives;

/**
 * @type {string|undefined}
 */
google.maps.DirectionsRequest.prototype.region;

/**
 * @type {!google.maps.TransitOptions|undefined}
 */
google.maps.DirectionsRequest.prototype.transitOptions;

/**
 * @type {!google.maps.TravelMode}
 */
google.maps.DirectionsRequest.prototype.travelMode;

/**
 * @type {!google.maps.UnitSystem|undefined}
 */
google.maps.DirectionsRequest.prototype.unitSystem;

/**
 * @type {!Array<!google.maps.DirectionsWaypoint>|undefined}
 */
google.maps.DirectionsRequest.prototype.waypoints;

/**
 * @record
 */
google.maps.DirectionsResult = function() {};

/**
 * @type {Array<google.maps.DirectionsGeocodedWaypoint>}
 */
google.maps.DirectionsResult.prototype.geocoded_waypoints;

/**
 * @type {Array<google.maps.DirectionsRoute>}
 */
google.maps.DirectionsResult.prototype.routes;

/**
 * @record
 */
google.maps.DirectionsRoute = function() {};

/**
 * @type {google.maps.LatLngBounds}
 */
google.maps.DirectionsRoute.prototype.bounds;

/**
 * @type {string}
 */
google.maps.DirectionsRoute.prototype.copyrights;

/**
 * @type {google.maps.TransitFare}
 */
google.maps.DirectionsRoute.prototype.fare;

/**
 * @type {Array<google.maps.DirectionsLeg>}
 */
google.maps.DirectionsRoute.prototype.legs;

/**
 * @type {Array<google.maps.LatLng>}
 */
google.maps.DirectionsRoute.prototype.overview_path;

/**
 * @type {string}
 */
google.maps.DirectionsRoute.prototype.overview_polyline;

/**
 * @type {Array<string>}
 */
google.maps.DirectionsRoute.prototype.warnings;

/**
 * @type {Array<number>}
 */
google.maps.DirectionsRoute.prototype.waypoint_order;

/**
 * @constructor
 */
google.maps.DirectionsService = function() {};

/**
 * @param {google.maps.DirectionsRequest} request
 * @param {function(google.maps.DirectionsResult, google.maps.DirectionsStatus)} callback
 * @return {undefined}
 */
google.maps.DirectionsService.prototype.route = function(request, callback) {};

/**
 * @enum {string}
 */
google.maps.DirectionsStatus = {
  INVALID_REQUEST: 'INVALID_REQUEST',
  MAX_WAYPOINTS_EXCEEDED: 'MAX_WAYPOINTS_EXCEEDED',
  NOT_FOUND: 'NOT_FOUND',
  OK: 'OK',
  OVER_QUERY_LIMIT: 'OVER_QUERY_LIMIT',
  REQUEST_DENIED: 'REQUEST_DENIED',
  UNKNOWN_ERROR: 'UNKNOWN_ERROR',
  ZERO_RESULTS: 'ZERO_RESULTS'
};

/**
 * @record
 */
google.maps.DirectionsStep = function() {};

/**
 * @type {google.maps.Distance}
 */
google.maps.DirectionsStep.prototype.distance;

/**
 * @type {google.maps.Duration}
 */
google.maps.DirectionsStep.prototype.duration;

/**
 * @type {google.maps.LatLng}
 */
google.maps.DirectionsStep.prototype.end_location;

/**
 * @type {string}
 */
google.maps.DirectionsStep.prototype.instructions;

/**
 * @type {Array<google.maps.LatLng>}
 */
google.maps.DirectionsStep.prototype.path;

/**
 * @type {google.maps.LatLng}
 */
google.maps.DirectionsStep.prototype.start_location;

/**
 * @type {Array<google.maps.DirectionsStep>}
 */
google.maps.DirectionsStep.prototype.steps;

/**
 * @type {google.maps.TransitDetails}
 */
google.maps.DirectionsStep.prototype.transit;

/**
 * @type {google.maps.TravelMode}
 */
google.maps.DirectionsStep.prototype.travel_mode;

/**
 * @record
 */
google.maps.DirectionsWaypoint = function() {};

/**
 * @type {string|!google.maps.LatLng|!google.maps.Place|undefined}
 */
google.maps.DirectionsWaypoint.prototype.location;

/**
 * @type {boolean|undefined}
 */
google.maps.DirectionsWaypoint.prototype.stopover;

/**
 * @record
 */
google.maps.Distance = function() {};

/**
 * @type {string}
 */
google.maps.Distance.prototype.text;

/**
 * @type {number}
 */
google.maps.Distance.prototype.value;

/**
 * @enum {string}
 */
google.maps.DistanceMatrixElementStatus = {
  NOT_FOUND: 'NOT_FOUND',
  OK: 'OK',
  ZERO_RESULTS: 'ZERO_RESULTS'
};

/**
 * @record
 */
google.maps.DistanceMatrixRequest = function() {};

/**
 * @type {boolean|undefined}
 */
google.maps.DistanceMatrixRequest.prototype.avoidFerries;

/**
 * @type {boolean|undefined}
 */
google.maps.DistanceMatrixRequest.prototype.avoidHighways;

/**
 * @type {boolean|undefined}
 */
google.maps.DistanceMatrixRequest.prototype.avoidTolls;

/**
 * @type {!Array<string|!google.maps.LatLng|!google.maps.Place>}
 */
google.maps.DistanceMatrixRequest.prototype.destinations;

/**
 * @type {!google.maps.DrivingOptions|undefined}
 */
google.maps.DistanceMatrixRequest.prototype.drivingOptions;

/**
 * @type {!Array<string|!google.maps.LatLng|!google.maps.Place>}
 */
google.maps.DistanceMatrixRequest.prototype.origins;

/**
 * @type {string|undefined}
 */
google.maps.DistanceMatrixRequest.prototype.region;

/**
 * @type {!google.maps.TransitOptions|undefined}
 */
google.maps.DistanceMatrixRequest.prototype.transitOptions;

/**
 * @type {!google.maps.TravelMode}
 */
google.maps.DistanceMatrixRequest.prototype.travelMode;

/**
 * @type {!google.maps.UnitSystem|undefined}
 */
google.maps.DistanceMatrixRequest.prototype.unitSystem;

/**
 * @record
 */
google.maps.DistanceMatrixResponse = function() {};

/**
 * @type {!Array<string>}
 */
google.maps.DistanceMatrixResponse.prototype.destinationAddresses;

/**
 * @type {!Array<string>}
 */
google.maps.DistanceMatrixResponse.prototype.originAddresses;

/**
 * @type {!Array<!google.maps.DistanceMatrixResponseRow>}
 */
google.maps.DistanceMatrixResponse.prototype.rows;

/**
 * @record
 */
google.maps.DistanceMatrixResponseElement = function() {};

/**
 * @type {!google.maps.Distance}
 */
google.maps.DistanceMatrixResponseElement.prototype.distance;

/**
 * @type {!google.maps.Duration}
 */
google.maps.DistanceMatrixResponseElement.prototype.duration;

/**
 * @type {!google.maps.Duration}
 */
google.maps.DistanceMatrixResponseElement.prototype.duration_in_traffic;

/**
 * @type {!google.maps.TransitFare}
 */
google.maps.DistanceMatrixResponseElement.prototype.fare;

/**
 * @type {!google.maps.DistanceMatrixElementStatus}
 */
google.maps.DistanceMatrixResponseElement.prototype.status;

/**
 * @record
 */
google.maps.DistanceMatrixResponseRow = function() {};

/**
 * @type {!Array<!google.maps.DistanceMatrixResponseElement>}
 */
google.maps.DistanceMatrixResponseRow.prototype.elements;

/**
 * @constructor
 */
google.maps.DistanceMatrixService = function() {};

/**
 * @param {google.maps.DistanceMatrixRequest} request
 * @param {function(google.maps.DistanceMatrixResponse, google.maps.DistanceMatrixStatus)} callback
 * @return {undefined}
 */
google.maps.DistanceMatrixService.prototype.getDistanceMatrix = function(request, callback) {};

/**
 * @enum {string}
 */
google.maps.DistanceMatrixStatus = {
  INVALID_REQUEST: 'INVALID_REQUEST',
  MAX_DIMENSIONS_EXCEEDED: 'MAX_DIMENSIONS_EXCEEDED',
  MAX_ELEMENTS_EXCEEDED: 'MAX_ELEMENTS_EXCEEDED',
  OK: 'OK',
  OVER_QUERY_LIMIT: 'OVER_QUERY_LIMIT',
  REQUEST_DENIED: 'REQUEST_DENIED',
  UNKNOWN_ERROR: 'UNKNOWN_ERROR'
};

/**
 * @record
 */
google.maps.DrivingOptions = function() {};

/**
 * @type {!Date}
 */
google.maps.DrivingOptions.prototype.departureTime;

/**
 * @type {!google.maps.TrafficModel|undefined}
 */
google.maps.DrivingOptions.prototype.trafficModel;

/**
 * @record
 */
google.maps.Duration = function() {};

/**
 * @type {string}
 */
google.maps.Duration.prototype.text;

/**
 * @type {number}
 */
google.maps.Duration.prototype.value;

/**
 * @record
 */
google.maps.ElevationResult = function() {};

/**
 * @type {number}
 */
google.maps.ElevationResult.prototype.elevation;

/**
 * @type {google.maps.LatLng}
 */
google.maps.ElevationResult.prototype.location;

/**
 * @type {number}
 */
google.maps.ElevationResult.prototype.resolution;

/**
 * @constructor
 */
google.maps.ElevationService = function() {};

/**
 * @param {!google.maps.PathElevationRequest} request
 * @param {function(?Array<!google.maps.ElevationResult>, !google.maps.ElevationStatus)} callback
 * @return {undefined}
 */
google.maps.ElevationService.prototype.getElevationAlongPath = function(request, callback) {};

/**
 * @param {!google.maps.LocationElevationRequest} request
 * @param {function(?Array<!google.maps.ElevationResult>, !google.maps.ElevationStatus)} callback
 * @return {undefined}
 */
google.maps.ElevationService.prototype.getElevationForLocations = function(request, callback) {};

/**
 * @enum {string}
 */
google.maps.ElevationStatus = {
  INVALID_REQUEST: 'INVALID_REQUEST',
  OK: 'OK',
  OVER_QUERY_LIMIT: 'OVER_QUERY_LIMIT',
  REQUEST_DENIED: 'REQUEST_DENIED',
  UNKNOWN_ERROR: 'UNKNOWN_ERROR'
};

/**
 * @record
 */
google.maps.FullscreenControlOptions = function() {};

/**
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.FullscreenControlOptions.prototype.position;

/**
 * @record
 */
google.maps.FusionTablesCell = function() {};

/**
 * @type {string}
 */
google.maps.FusionTablesCell.prototype.columnName;

/**
 * @type {string}
 */
google.maps.FusionTablesCell.prototype.value;

/**
 * @record
 */
google.maps.FusionTablesHeatmap = function() {};

/**
 * @type {boolean}
 */
google.maps.FusionTablesHeatmap.prototype.enabled;

/**
 * @param {google.maps.FusionTablesLayerOptions} options
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.FusionTablesLayer = function(options) {};

/**
 * @return {google.maps.Map}
 */
google.maps.FusionTablesLayer.prototype.getMap = function() {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.FusionTablesLayer.prototype.setMap = function(map) {};

/**
 * @param {google.maps.FusionTablesLayerOptions} options
 * @return {undefined}
 */
google.maps.FusionTablesLayer.prototype.setOptions = function(options) {};

/**
 * @record
 */
google.maps.FusionTablesLayerOptions = function() {};

/**
 * @type {?boolean|undefined}
 */
google.maps.FusionTablesLayerOptions.prototype.clickable;

/**
 * @type {?google.maps.FusionTablesHeatmap|undefined}
 */
google.maps.FusionTablesLayerOptions.prototype.heatmap;

/**
 * @type {?google.maps.Map|undefined}
 */
google.maps.FusionTablesLayerOptions.prototype.map;

/**
 * @type {?google.maps.FusionTablesQuery|undefined}
 */
google.maps.FusionTablesLayerOptions.prototype.query;

/**
 * @type {?Array<!google.maps.FusionTablesStyle>|undefined}
 */
google.maps.FusionTablesLayerOptions.prototype.styles;

/**
 * @type {?boolean|undefined}
 */
google.maps.FusionTablesLayerOptions.prototype.suppressInfoWindows;

/**
 * @record
 */
google.maps.FusionTablesMarkerOptions = function() {};

/**
 * @type {?string|undefined}
 */
google.maps.FusionTablesMarkerOptions.prototype.iconName;

/**
 * @record
 */
google.maps.FusionTablesMouseEvent = function() {};

/**
 * @type {string}
 */
google.maps.FusionTablesMouseEvent.prototype.infoWindowHtml;

/**
 * @type {google.maps.LatLng}
 */
google.maps.FusionTablesMouseEvent.prototype.latLng;

/**
 * @type {google.maps.Size}
 */
google.maps.FusionTablesMouseEvent.prototype.pixelOffset;

/**
 * @type {Object<google.maps.FusionTablesCell>}
 */
google.maps.FusionTablesMouseEvent.prototype.row;

/**
 * @record
 */
google.maps.FusionTablesPolygonOptions = function() {};

/**
 * @type {?string|undefined}
 */
google.maps.FusionTablesPolygonOptions.prototype.fillColor;

/**
 * @type {?number|undefined}
 */
google.maps.FusionTablesPolygonOptions.prototype.fillOpacity;

/**
 * @type {?string|undefined}
 */
google.maps.FusionTablesPolygonOptions.prototype.strokeColor;

/**
 * @type {?number|undefined}
 */
google.maps.FusionTablesPolygonOptions.prototype.strokeOpacity;

/**
 * @type {?number|undefined}
 */
google.maps.FusionTablesPolygonOptions.prototype.strokeWeight;

/**
 * @record
 */
google.maps.FusionTablesPolylineOptions = function() {};

/**
 * @type {?string|undefined}
 */
google.maps.FusionTablesPolylineOptions.prototype.strokeColor;

/**
 * @type {?number|undefined}
 */
google.maps.FusionTablesPolylineOptions.prototype.strokeOpacity;

/**
 * @type {?number|undefined}
 */
google.maps.FusionTablesPolylineOptions.prototype.strokeWeight;

/**
 * @record
 */
google.maps.FusionTablesQuery = function() {};

/**
 * @type {?string|undefined}
 */
google.maps.FusionTablesQuery.prototype.from;

/**
 * @type {?number|undefined}
 */
google.maps.FusionTablesQuery.prototype.limit;

/**
 * @type {?number|undefined}
 */
google.maps.FusionTablesQuery.prototype.offset;

/**
 * @type {?string|undefined}
 */
google.maps.FusionTablesQuery.prototype.orderBy;

/**
 * @type {?string|undefined}
 */
google.maps.FusionTablesQuery.prototype.select;

/**
 * @type {?string|undefined}
 */
google.maps.FusionTablesQuery.prototype.where;

/**
 * @record
 */
google.maps.FusionTablesStyle = function() {};

/**
 * @type {google.maps.FusionTablesMarkerOptions}
 */
google.maps.FusionTablesStyle.prototype.markerOptions;

/**
 * @type {google.maps.FusionTablesPolygonOptions}
 */
google.maps.FusionTablesStyle.prototype.polygonOptions;

/**
 * @type {google.maps.FusionTablesPolylineOptions}
 */
google.maps.FusionTablesStyle.prototype.polylineOptions;

/**
 * @type {string}
 */
google.maps.FusionTablesStyle.prototype.where;

/**
 * @constructor
 */
google.maps.Geocoder = function() {};

/**
 * @param {google.maps.GeocoderRequest} request
 * @param {function(Array<google.maps.GeocoderResult>, google.maps.GeocoderStatus)} callback
 * @return {undefined}
 */
google.maps.Geocoder.prototype.geocode = function(request, callback) {};

/**
 * @record
 */
google.maps.GeocoderAddressComponent = function() {};

/**
 * @type {string}
 */
google.maps.GeocoderAddressComponent.prototype.long_name;

/**
 * @type {string}
 */
google.maps.GeocoderAddressComponent.prototype.short_name;

/**
 * @type {Array<string>}
 */
google.maps.GeocoderAddressComponent.prototype.types;

/**
 * @record
 */
google.maps.GeocoderComponentRestrictions = function() {};

/**
 * @type {string}
 */
google.maps.GeocoderComponentRestrictions.prototype.administrativeArea;

/**
 * @type {string}
 */
google.maps.GeocoderComponentRestrictions.prototype.country;

/**
 * @type {string}
 */
google.maps.GeocoderComponentRestrictions.prototype.locality;

/**
 * @type {string}
 */
google.maps.GeocoderComponentRestrictions.prototype.postalCode;

/**
 * @type {string}
 */
google.maps.GeocoderComponentRestrictions.prototype.route;

/**
 * @record
 */
google.maps.GeocoderGeometry = function() {};

/**
 * @type {google.maps.LatLngBounds}
 */
google.maps.GeocoderGeometry.prototype.bounds;

/**
 * @type {google.maps.LatLng}
 */
google.maps.GeocoderGeometry.prototype.location;

/**
 * @type {google.maps.GeocoderLocationType}
 */
google.maps.GeocoderGeometry.prototype.location_type;

/**
 * @type {google.maps.LatLngBounds}
 */
google.maps.GeocoderGeometry.prototype.viewport;

/**
 * @enum {string}
 */
google.maps.GeocoderLocationType = {
  APPROXIMATE: 'APPROXIMATE',
  GEOMETRIC_CENTER: 'GEOMETRIC_CENTER',
  RANGE_INTERPOLATED: 'RANGE_INTERPOLATED',
  ROOFTOP: 'ROOFTOP'
};

/**
 * @record
 */
google.maps.GeocoderRequest = function() {};

/**
 * @type {?string|undefined}
 */
google.maps.GeocoderRequest.prototype.address;

/**
 * @type {?google.maps.LatLngBounds|?google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.GeocoderRequest.prototype.bounds;

/**
 * @type {?google.maps.GeocoderComponentRestrictions|undefined}
 */
google.maps.GeocoderRequest.prototype.componentRestrictions;

/**
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.GeocoderRequest.prototype.location;

/**
 * @type {?string|undefined}
 */
google.maps.GeocoderRequest.prototype.placeId;

/**
 * @type {?string|undefined}
 */
google.maps.GeocoderRequest.prototype.region;

/**
 * @record
 */
google.maps.GeocoderResult = function() {};

/**
 * @type {Array<google.maps.GeocoderAddressComponent>}
 */
google.maps.GeocoderResult.prototype.address_components;

/**
 * @type {string}
 */
google.maps.GeocoderResult.prototype.formatted_address;

/**
 * @type {google.maps.GeocoderGeometry}
 */
google.maps.GeocoderResult.prototype.geometry;

/**
 * @type {boolean}
 */
google.maps.GeocoderResult.prototype.partial_match;

/**
 * @type {string}
 */
google.maps.GeocoderResult.prototype.place_id;

/**
 * @type {Array<string>}
 */
google.maps.GeocoderResult.prototype.postcode_localities;

/**
 * @type {Array<string>}
 */
google.maps.GeocoderResult.prototype.types;

/**
 * @enum {string}
 */
google.maps.GeocoderStatus = {
  ERROR: 'ERROR',
  INVALID_REQUEST: 'INVALID_REQUEST',
  OK: 'OK',
  OVER_QUERY_LIMIT: 'OVER_QUERY_LIMIT',
  REQUEST_DENIED: 'REQUEST_DENIED',
  UNKNOWN_ERROR: 'UNKNOWN_ERROR',
  ZERO_RESULTS: 'ZERO_RESULTS'
};

/**
 * @param {string} url
 * @param {google.maps.LatLngBounds|google.maps.LatLngBoundsLiteral} bounds
 * @param {google.maps.GroundOverlayOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.GroundOverlay = function(url, bounds, opts) {};

/**
 * @return {google.maps.LatLngBounds}
 */
google.maps.GroundOverlay.prototype.getBounds = function() {};

/**
 * @return {google.maps.Map}
 */
google.maps.GroundOverlay.prototype.getMap = function() {};

/**
 * @return {number}
 */
google.maps.GroundOverlay.prototype.getOpacity = function() {};

/**
 * @return {string}
 */
google.maps.GroundOverlay.prototype.getUrl = function() {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.GroundOverlay.prototype.setMap = function(map) {};

/**
 * @param {number} opacity
 * @return {undefined}
 */
google.maps.GroundOverlay.prototype.setOpacity = function(opacity) {};

/**
 * @record
 */
google.maps.GroundOverlayOptions = function() {};

/**
 * @type {?boolean|undefined}
 */
google.maps.GroundOverlayOptions.prototype.clickable;

/**
 * @type {?google.maps.Map|undefined}
 */
google.maps.GroundOverlayOptions.prototype.map;

/**
 * @type {?number|undefined}
 */
google.maps.GroundOverlayOptions.prototype.opacity;

/**
 * @record
 */
google.maps.Icon = function() {};

/**
 * @type {google.maps.Point}
 */
google.maps.Icon.prototype.anchor;

/**
 * @type {google.maps.Point}
 */
google.maps.Icon.prototype.labelOrigin;

/**
 * @type {google.maps.Point}
 */
google.maps.Icon.prototype.origin;

/**
 * @type {google.maps.Size}
 */
google.maps.Icon.prototype.scaledSize;

/**
 * @type {google.maps.Size}
 */
google.maps.Icon.prototype.size;

/**
 * @type {string}
 */
google.maps.Icon.prototype.url;

/**
 * @extends {google.maps.MouseEvent}
 * @record
 */
google.maps.IconMouseEvent = function() {};

/**
 * @type {?string}
 */
google.maps.IconMouseEvent.prototype.placeId;

/**
 * @record
 */
google.maps.IconSequence = function() {};

/**
 * @type {boolean}
 */
google.maps.IconSequence.prototype.fixedRotation;

/**
 * @type {google.maps.Symbol}
 */
google.maps.IconSequence.prototype.icon;

/**
 * @type {string}
 */
google.maps.IconSequence.prototype.offset;

/**
 * @type {string}
 */
google.maps.IconSequence.prototype.repeat;

/**
 * @param {google.maps.ImageMapTypeOptions} opts
 * @implements {google.maps.MapType}
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.ImageMapType = function(opts) {};

/**
 * @type {?string}
 */
google.maps.ImageMapType.prototype.alt;

/**
 * @type {number}
 */
google.maps.ImageMapType.prototype.maxZoom;

/**
 * @type {number}
 */
google.maps.ImageMapType.prototype.minZoom;

/**
 * @type {?string}
 */
google.maps.ImageMapType.prototype.name;

/**
 * @type {google.maps.Projection}
 */
google.maps.ImageMapType.prototype.projection;

/**
 * @type {number}
 */
google.maps.ImageMapType.prototype.radius;

/**
 * @type {google.maps.Size}
 */
google.maps.ImageMapType.prototype.tileSize;

/**
 * @return {number}
 */
google.maps.ImageMapType.prototype.getOpacity = function() {};

/**
 * @param {google.maps.Point} tileCoord
 * @param {number} zoom
 * @param {Document} ownerDocument
 * @return {Node}
 * @override
 */
google.maps.ImageMapType.prototype.getTile = function(tileCoord, zoom, ownerDocument) {};

/**
 * @param {Node} tileDiv
 * @return {undefined}
 * @override
 */
google.maps.ImageMapType.prototype.releaseTile = function(tileDiv) {};

/**
 * @param {number} opacity
 * @return {undefined}
 */
google.maps.ImageMapType.prototype.setOpacity = function(opacity) {};

/**
 * @record
 */
google.maps.ImageMapTypeOptions = function() {};

/**
 * @type {?string|undefined}
 */
google.maps.ImageMapTypeOptions.prototype.alt;

/**
 * @type {?(function(!google.maps.Point, number): string)|undefined}
 */
google.maps.ImageMapTypeOptions.prototype.getTileUrl;

/**
 * @type {?number|undefined}
 */
google.maps.ImageMapTypeOptions.prototype.maxZoom;

/**
 * @type {?number|undefined}
 */
google.maps.ImageMapTypeOptions.prototype.minZoom;

/**
 * @type {?string|undefined}
 */
google.maps.ImageMapTypeOptions.prototype.name;

/**
 * @type {?number|undefined}
 */
google.maps.ImageMapTypeOptions.prototype.opacity;

/**
 * @type {?google.maps.Size|undefined}
 */
google.maps.ImageMapTypeOptions.prototype.tileSize;

/**
 * @param {google.maps.InfoWindowOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.InfoWindow = function(opts) {};

/**
 * @return {undefined}
 */
google.maps.InfoWindow.prototype.close = function() {};

/**
 * @return {string|Node}
 */
google.maps.InfoWindow.prototype.getContent = function() {};

/**
 * @return {google.maps.LatLng}
 */
google.maps.InfoWindow.prototype.getPosition = function() {};

/**
 * @return {number}
 */
google.maps.InfoWindow.prototype.getZIndex = function() {};

/**
 * @param {(google.maps.Map|google.maps.StreetViewPanorama)=} map
 * @param {google.maps.MVCObject=} anchor
 * @return {undefined}
 */
google.maps.InfoWindow.prototype.open = function(map, anchor) {};

/**
 * @param {string|Node} content
 * @return {undefined}
 */
google.maps.InfoWindow.prototype.setContent = function(content) {};

/**
 * @param {google.maps.InfoWindowOptions} options
 * @return {undefined}
 */
google.maps.InfoWindow.prototype.setOptions = function(options) {};

/**
 * @param {google.maps.LatLng|google.maps.LatLngLiteral} position
 * @return {undefined}
 */
google.maps.InfoWindow.prototype.setPosition = function(position) {};

/**
 * @param {number} zIndex
 * @return {undefined}
 */
google.maps.InfoWindow.prototype.setZIndex = function(zIndex) {};

/**
 * @record
 */
google.maps.InfoWindowOptions = function() {};

/**
 * @type {?string|?Node|undefined}
 */
google.maps.InfoWindowOptions.prototype.content;

/**
 * @type {?boolean|undefined}
 */
google.maps.InfoWindowOptions.prototype.disableAutoPan;

/**
 * @type {?number|undefined}
 */
google.maps.InfoWindowOptions.prototype.maxWidth;

/**
 * @type {?google.maps.Size|undefined}
 */
google.maps.InfoWindowOptions.prototype.pixelOffset;

/**
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.InfoWindowOptions.prototype.position;

/**
 * @type {?number|undefined}
 */
google.maps.InfoWindowOptions.prototype.zIndex;

/**
 * @record
 */
google.maps.KmlAuthor = function() {};

/**
 * @type {string}
 */
google.maps.KmlAuthor.prototype.email;

/**
 * @type {string}
 */
google.maps.KmlAuthor.prototype.name;

/**
 * @type {string}
 */
google.maps.KmlAuthor.prototype.uri;

/**
 * @record
 */
google.maps.KmlFeatureData = function() {};

/**
 * @type {google.maps.KmlAuthor}
 */
google.maps.KmlFeatureData.prototype.author;

/**
 * @type {string}
 */
google.maps.KmlFeatureData.prototype.description;

/**
 * @type {string}
 */
google.maps.KmlFeatureData.prototype.id;

/**
 * @type {string}
 */
google.maps.KmlFeatureData.prototype.infoWindowHtml;

/**
 * @type {string}
 */
google.maps.KmlFeatureData.prototype.name;

/**
 * @type {string}
 */
google.maps.KmlFeatureData.prototype.snippet;

/**
 * @param {google.maps.KmlLayerOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.KmlLayer = function(opts) {};

/**
 * @return {google.maps.LatLngBounds}
 */
google.maps.KmlLayer.prototype.getDefaultViewport = function() {};

/**
 * @return {google.maps.Map}
 */
google.maps.KmlLayer.prototype.getMap = function() {};

/**
 * @return {google.maps.KmlLayerMetadata}
 */
google.maps.KmlLayer.prototype.getMetadata = function() {};

/**
 * @return {google.maps.KmlLayerStatus}
 */
google.maps.KmlLayer.prototype.getStatus = function() {};

/**
 * @return {string}
 */
google.maps.KmlLayer.prototype.getUrl = function() {};

/**
 * @return {number}
 */
google.maps.KmlLayer.prototype.getZIndex = function() {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.KmlLayer.prototype.setMap = function(map) {};

/**
 * @param {google.maps.KmlLayerOptions} options
 * @return {undefined}
 */
google.maps.KmlLayer.prototype.setOptions = function(options) {};

/**
 * @param {string} url
 * @return {undefined}
 */
google.maps.KmlLayer.prototype.setUrl = function(url) {};

/**
 * @param {number} zIndex
 * @return {undefined}
 */
google.maps.KmlLayer.prototype.setZIndex = function(zIndex) {};

/**
 * @record
 */
google.maps.KmlLayerMetadata = function() {};

/**
 * @type {google.maps.KmlAuthor}
 */
google.maps.KmlLayerMetadata.prototype.author;

/**
 * @type {string}
 */
google.maps.KmlLayerMetadata.prototype.description;

/**
 * @type {boolean}
 */
google.maps.KmlLayerMetadata.prototype.hasScreenOverlays;

/**
 * @type {string}
 */
google.maps.KmlLayerMetadata.prototype.name;

/**
 * @type {string}
 */
google.maps.KmlLayerMetadata.prototype.snippet;

/**
 * @record
 */
google.maps.KmlLayerOptions = function() {};

/**
 * @type {?boolean|undefined}
 */
google.maps.KmlLayerOptions.prototype.clickable;

/**
 * @type {?google.maps.Map|undefined}
 */
google.maps.KmlLayerOptions.prototype.map;

/**
 * @type {?boolean|undefined}
 */
google.maps.KmlLayerOptions.prototype.preserveViewport;

/**
 * @type {?boolean|undefined}
 */
google.maps.KmlLayerOptions.prototype.screenOverlays;

/**
 * @type {?boolean|undefined}
 */
google.maps.KmlLayerOptions.prototype.suppressInfoWindows;

/**
 * @type {?string|undefined}
 */
google.maps.KmlLayerOptions.prototype.url;

/**
 * @type {?number|undefined}
 */
google.maps.KmlLayerOptions.prototype.zIndex;

/**
 * @enum {string}
 */
google.maps.KmlLayerStatus = {
  DOCUMENT_NOT_FOUND: 'DOCUMENT_NOT_FOUND',
  DOCUMENT_TOO_LARGE: 'DOCUMENT_TOO_LARGE',
  FETCH_ERROR: 'FETCH_ERROR',
  INVALID_DOCUMENT: 'INVALID_DOCUMENT',
  INVALID_REQUEST: 'INVALID_REQUEST',
  LIMITS_EXCEEDED: 'LIMITS_EXCEEDED',
  OK: 'OK',
  TIMED_OUT: 'TIMED_OUT',
  UNKNOWN: 'UNKNOWN'
};

/**
 * @record
 */
google.maps.KmlMouseEvent = function() {};

/**
 * @type {google.maps.KmlFeatureData}
 */
google.maps.KmlMouseEvent.prototype.featureData;

/**
 * @type {google.maps.LatLng}
 */
google.maps.KmlMouseEvent.prototype.latLng;

/**
 * @type {google.maps.Size}
 */
google.maps.KmlMouseEvent.prototype.pixelOffset;

/**
 * @param {number} lat
 * @param {number} lng
 * @param {boolean=} noWrap
 * @constructor
 */
google.maps.LatLng = function(lat, lng, noWrap) {};

/**
 * @param {google.maps.LatLng} other
 * @return {boolean}
 */
google.maps.LatLng.prototype.equals = function(other) {};

/**
 * @return {number}
 */
google.maps.LatLng.prototype.lat = function() {};

/**
 * @return {number}
 */
google.maps.LatLng.prototype.lng = function() {};

/**
 * @return {!google.maps.LatLngLiteral}
 * @override
 */
google.maps.LatLng.prototype.toJSON = function() {};

/**
 * @return {string}
 * @override
 */
google.maps.LatLng.prototype.toString = function() {};

/**
 * @param {number=} precision
 * @return {string}
 */
google.maps.LatLng.prototype.toUrlValue = function(precision) {};

/**
 * @param {(google.maps.LatLng|google.maps.LatLngLiteral)=} sw
 * @param {(google.maps.LatLng|google.maps.LatLngLiteral)=} ne
 * @constructor
 */
google.maps.LatLngBounds = function(sw, ne) {};

/**
 * @param {google.maps.LatLng|google.maps.LatLngLiteral} latLng
 * @return {boolean}
 */
google.maps.LatLngBounds.prototype.contains = function(latLng) {};

/**
 * @param {google.maps.LatLngBounds|google.maps.LatLngBoundsLiteral} other
 * @return {boolean}
 */
google.maps.LatLngBounds.prototype.equals = function(other) {};

/**
 * @param {google.maps.LatLng|google.maps.LatLngLiteral} point
 * @return {google.maps.LatLngBounds}
 */
google.maps.LatLngBounds.prototype.extend = function(point) {};

/**
 * @return {google.maps.LatLng}
 */
google.maps.LatLngBounds.prototype.getCenter = function() {};

/**
 * @return {!google.maps.LatLng}
 */
google.maps.LatLngBounds.prototype.getNorthEast = function() {};

/**
 * @return {!google.maps.LatLng}
 */
google.maps.LatLngBounds.prototype.getSouthWest = function() {};

/**
 * @param {google.maps.LatLngBounds|google.maps.LatLngBoundsLiteral} other
 * @return {boolean}
 */
google.maps.LatLngBounds.prototype.intersects = function(other) {};

/**
 * @return {boolean}
 */
google.maps.LatLngBounds.prototype.isEmpty = function() {};

/**
 * @return {!google.maps.LatLngBoundsLiteral}
 * @override
 */
google.maps.LatLngBounds.prototype.toJSON = function() {};

/**
 * @return {google.maps.LatLng}
 */
google.maps.LatLngBounds.prototype.toSpan = function() {};

/**
 * @return {string}
 * @override
 */
google.maps.LatLngBounds.prototype.toString = function() {};

/**
 * @param {number=} precision
 * @return {string}
 */
google.maps.LatLngBounds.prototype.toUrlValue = function(precision) {};

/**
 * @param {google.maps.LatLngBounds|google.maps.LatLngBoundsLiteral} other
 * @return {google.maps.LatLngBounds}
 */
google.maps.LatLngBounds.prototype.union = function(other) {};

/**
 * @record
 */
google.maps.LatLngBoundsLiteral = function() {};

/**
 * @type {number}
 */
google.maps.LatLngBoundsLiteral.prototype.east;

/**
 * @type {number}
 */
google.maps.LatLngBoundsLiteral.prototype.north;

/**
 * @type {number}
 */
google.maps.LatLngBoundsLiteral.prototype.south;

/**
 * @type {number}
 */
google.maps.LatLngBoundsLiteral.prototype.west;

/**
 * @record
 */
google.maps.LatLngLiteral = function() {};

/**
 * @type {number}
 */
google.maps.LatLngLiteral.prototype.lat;

/**
 * @type {number}
 */
google.maps.LatLngLiteral.prototype.lng;

/**
 * @record
 */
google.maps.LocationElevationRequest = function() {};

/**
 * @type {?Array<!google.maps.LatLng>|undefined}
 */
google.maps.LocationElevationRequest.prototype.locations;

/**
 * @param {Array<T>=} array
 * @extends {google.maps.MVCObject}
 * @template T
 * @constructor
 */
google.maps.MVCArray = function(array) {};

/**
 * @return {undefined}
 */
google.maps.MVCArray.prototype.clear = function() {};

/**
 * @param {function(T, number)} callback
 * @return {undefined}
 */
google.maps.MVCArray.prototype.forEach = function(callback) {};

/**
 * @return {!Array<T>}
 */
google.maps.MVCArray.prototype.getArray = function() {};

/**
 * @param {number} i
 * @return {T}
 */
google.maps.MVCArray.prototype.getAt = function(i) {};

/**
 * @return {number}
 */
google.maps.MVCArray.prototype.getLength = function() {};

/**
 * @param {number} i
 * @param {T} elem
 * @return {undefined}
 */
google.maps.MVCArray.prototype.insertAt = function(i, elem) {};

/**
 * @return {T}
 */
google.maps.MVCArray.prototype.pop = function() {};

/**
 * @param {T} elem
 * @return {number}
 */
google.maps.MVCArray.prototype.push = function(elem) {};

/**
 * @param {number} i
 * @return {T}
 */
google.maps.MVCArray.prototype.removeAt = function(i) {};

/**
 * @param {number} i
 * @param {T} elem
 * @return {undefined}
 */
google.maps.MVCArray.prototype.setAt = function(i, elem) {};

/**
 * @constructor
 */
google.maps.MVCObject = function() {};

/**
 * @param {string} eventName
 * @param {!Function} handler
 * @return {!google.maps.MapsEventListener}
 */
google.maps.MVCObject.prototype.addListener = function(eventName, handler) {};

/**
 * @param {string} key
 * @param {!google.maps.MVCObject} target
 * @param {?string=} targetKey
 * @param {boolean=} noNotify
 * @return {undefined}
 */
google.maps.MVCObject.prototype.bindTo = function(key, target, targetKey, noNotify) {};

/**
 * @param {string} key
 * @return {?}
 */
google.maps.MVCObject.prototype.get = function(key) {};

/**
 * @param {string} key
 * @return {undefined}
 */
google.maps.MVCObject.prototype.notify = function(key) {};

/**
 * @param {string} key
 * @param {*} value
 * @return {undefined}
 */
google.maps.MVCObject.prototype.set = function(key, value) {};

/**
 * @param {?Object=} values
 * @return {undefined}
 */
google.maps.MVCObject.prototype.setValues = function(values) {};

/**
 * @param {string} key
 * @return {undefined}
 */
google.maps.MVCObject.prototype.unbind = function(key) {};

/**
 * @return {undefined}
 */
google.maps.MVCObject.prototype.unbindAll = function() {};

/**
 * @param {!Element} mapDiv
 * @param {!google.maps.MapOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.Map = function(mapDiv, opts) {};

/**
 * @type {Array<google.maps.MVCArray<Node>>}
 */
google.maps.Map.prototype.controls;

/**
 * @type {google.maps.Data}
 */
google.maps.Map.prototype.data;

/**
 * @type {google.maps.MapTypeRegistry}
 */
google.maps.Map.prototype.mapTypes;

/**
 * @type {!google.maps.MVCArray<?google.maps.MapType>}
 */
google.maps.Map.prototype.overlayMapTypes;

/**
 * @param {google.maps.LatLngBounds|google.maps.LatLngBoundsLiteral} bounds
 * @param {(number|!google.maps.Padding)=} padding
 * @return {undefined}
 */
google.maps.Map.prototype.fitBounds = function(bounds, padding) {};

/**
 * @return {google.maps.LatLngBounds}
 */
google.maps.Map.prototype.getBounds = function() {};

/**
 * @return {google.maps.LatLng}
 */
google.maps.Map.prototype.getCenter = function() {};

/**
 * @return {boolean}
 */
google.maps.Map.prototype.getClickableIcons = function() {};

/**
 * @return {!Element}
 */
google.maps.Map.prototype.getDiv = function() {};

/**
 * @return {number}
 */
google.maps.Map.prototype.getHeading = function() {};

/**
 * @return {google.maps.MapTypeId|string}
 */
google.maps.Map.prototype.getMapTypeId = function() {};

/**
 * @return {google.maps.Projection}
 */
google.maps.Map.prototype.getProjection = function() {};

/**
 * @return {google.maps.StreetViewPanorama}
 */
google.maps.Map.prototype.getStreetView = function() {};

/**
 * @return {number}
 */
google.maps.Map.prototype.getTilt = function() {};

/**
 * @return {number}
 */
google.maps.Map.prototype.getZoom = function() {};

/**
 * @param {number} x
 * @param {number} y
 * @return {undefined}
 */
google.maps.Map.prototype.panBy = function(x, y) {};

/**
 * @param {google.maps.LatLng|google.maps.LatLngLiteral} latLng
 * @return {undefined}
 */
google.maps.Map.prototype.panTo = function(latLng) {};

/**
 * @param {google.maps.LatLngBounds|google.maps.LatLngBoundsLiteral} latLngBounds
 * @param {(number|!google.maps.Padding)=} padding
 * @return {undefined}
 */
google.maps.Map.prototype.panToBounds = function(latLngBounds, padding) {};

/**
 * @param {google.maps.LatLng|google.maps.LatLngLiteral} latlng
 * @return {undefined}
 */
google.maps.Map.prototype.setCenter = function(latlng) {};

/**
 * @param {boolean} value
 * @return {undefined}
 */
google.maps.Map.prototype.setClickableIcons = function(value) {};

/**
 * @param {number} heading
 * @return {undefined}
 */
google.maps.Map.prototype.setHeading = function(heading) {};

/**
 * @param {google.maps.MapTypeId|string} mapTypeId
 * @return {undefined}
 */
google.maps.Map.prototype.setMapTypeId = function(mapTypeId) {};

/**
 * @param {google.maps.MapOptions} options
 * @return {undefined}
 */
google.maps.Map.prototype.setOptions = function(options) {};

/**
 * @param {google.maps.StreetViewPanorama} panorama
 * @return {undefined}
 */
google.maps.Map.prototype.setStreetView = function(panorama) {};

/**
 * @param {number} tilt
 * @return {undefined}
 */
google.maps.Map.prototype.setTilt = function(tilt) {};

/**
 * @param {number} zoom
 * @return {undefined}
 */
google.maps.Map.prototype.setZoom = function(zoom) {};

/**
 * @record
 */
google.maps.MapCanvasProjection = function() {};

/**
 * @param {?google.maps.Point} pixel
 * @param {boolean=} nowrap
 * @return {?google.maps.LatLng}
 */
google.maps.MapCanvasProjection.prototype.fromContainerPixelToLatLng = function(pixel, nowrap) {};

/**
 * @param {?google.maps.Point} pixel
 * @param {boolean=} nowrap
 * @return {?google.maps.LatLng}
 */
google.maps.MapCanvasProjection.prototype.fromDivPixelToLatLng = function(pixel, nowrap) {};

/**
 * @param {?google.maps.LatLng} latLng
 * @return {?google.maps.Point}
 */
google.maps.MapCanvasProjection.prototype.fromLatLngToContainerPixel = function(latLng) {};

/**
 * @param {?google.maps.LatLng} latLng
 * @return {?google.maps.Point}
 */
google.maps.MapCanvasProjection.prototype.fromLatLngToDivPixel = function(latLng) {};

/**
 * @return {number}
 */
google.maps.MapCanvasProjection.prototype.getWorldWidth = function() {};

/**
 * @record
 */
google.maps.MapOptions = function() {};

/**
 * @type {?string|undefined}
 */
google.maps.MapOptions.prototype.backgroundColor;

/**
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.MapOptions.prototype.center;

/**
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.clickableIcons;

/**
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.disableDefaultUI;

/**
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.disableDoubleClickZoom;

/**
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.draggable;

/**
 * @type {?string|undefined}
 */
google.maps.MapOptions.prototype.draggableCursor;

/**
 * @type {?string|undefined}
 */
google.maps.MapOptions.prototype.draggingCursor;

/**
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.fullscreenControl;

/**
 * @type {?google.maps.FullscreenControlOptions|undefined}
 */
google.maps.MapOptions.prototype.fullscreenControlOptions;

/**
 * @type {?string|undefined}
 */
google.maps.MapOptions.prototype.gestureHandling;

/**
 * @type {?number|undefined}
 */
google.maps.MapOptions.prototype.heading;

/**
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.keyboardShortcuts;

/**
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.mapTypeControl;

/**
 * @type {?google.maps.MapTypeControlOptions|undefined}
 */
google.maps.MapOptions.prototype.mapTypeControlOptions;

/**
 * @type {?google.maps.MapTypeId|?string|undefined}
 */
google.maps.MapOptions.prototype.mapTypeId;

/**
 * @type {?number|undefined}
 */
google.maps.MapOptions.prototype.maxZoom;

/**
 * @type {?number|undefined}
 */
google.maps.MapOptions.prototype.minZoom;

/**
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.noClear;

/**
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.panControl;

/**
 * @type {?google.maps.PanControlOptions|undefined}
 */
google.maps.MapOptions.prototype.panControlOptions;

/**
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.rotateControl;

/**
 * @type {?google.maps.RotateControlOptions|undefined}
 */
google.maps.MapOptions.prototype.rotateControlOptions;

/**
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.scaleControl;

/**
 * @type {?google.maps.ScaleControlOptions|undefined}
 */
google.maps.MapOptions.prototype.scaleControlOptions;

/**
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.scrollwheel;

/**
 * @type {?google.maps.StreetViewPanorama|undefined}
 */
google.maps.MapOptions.prototype.streetView;

/**
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.streetViewControl;

/**
 * @type {?google.maps.StreetViewControlOptions|undefined}
 */
google.maps.MapOptions.prototype.streetViewControlOptions;

/**
 * @type {?Array<!google.maps.MapTypeStyle>|undefined}
 */
google.maps.MapOptions.prototype.styles;

/**
 * @type {?number|undefined}
 */
google.maps.MapOptions.prototype.tilt;

/**
 * @type {?number|undefined}
 */
google.maps.MapOptions.prototype.zoom;

/**
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.zoomControl;

/**
 * @type {?google.maps.ZoomControlOptions|undefined}
 */
google.maps.MapOptions.prototype.zoomControlOptions;

/**
 * @record
 */
google.maps.MapPanes = function() {};

/**
 * @type {!Element}
 */
google.maps.MapPanes.prototype.floatPane;

/**
 * @type {!Element}
 */
google.maps.MapPanes.prototype.mapPane;

/**
 * @type {!Element}
 */
google.maps.MapPanes.prototype.markerLayer;

/**
 * @type {!Element}
 */
google.maps.MapPanes.prototype.overlayLayer;

/**
 * @type {!Element}
 */
google.maps.MapPanes.prototype.overlayMouseTarget;

/**
 * @record
 */
google.maps.MapType = function() {};

/**
 * @type {?string}
 */
google.maps.MapType.prototype.alt;

/**
 * @type {number}
 */
google.maps.MapType.prototype.maxZoom;

/**
 * @type {number}
 */
google.maps.MapType.prototype.minZoom;

/**
 * @type {?string}
 */
google.maps.MapType.prototype.name;

/**
 * @type {?google.maps.Projection}
 */
google.maps.MapType.prototype.projection;

/**
 * @type {number}
 */
google.maps.MapType.prototype.radius;

/**
 * @type {google.maps.Size}
 */
google.maps.MapType.prototype.tileSize;

/**
 * @param {google.maps.Point} tileCoord
 * @param {number} zoom
 * @param {Document} ownerDocument
 * @return {Node}
 */
google.maps.MapType.prototype.getTile = function(tileCoord, zoom, ownerDocument) {};

/**
 * @param {Node} tile
 * @return {undefined}
 */
google.maps.MapType.prototype.releaseTile = function(tile) {};

/**
 * @record
 */
google.maps.MapTypeControlOptions = function() {};

/**
 * @type {?Array<!google.maps.MapTypeId|string>|undefined}
 */
google.maps.MapTypeControlOptions.prototype.mapTypeIds;

/**
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.MapTypeControlOptions.prototype.position;

/**
 * @type {?google.maps.MapTypeControlStyle|undefined}
 */
google.maps.MapTypeControlOptions.prototype.style;

/**
 * @enum {number}
 */
google.maps.MapTypeControlStyle = {
  DEFAULT: 0,
  DROPDOWN_MENU: 1,
  HORIZONTAL_BAR: 2
};

/**
 * @enum {string}
 */
google.maps.MapTypeId = {
  HYBRID: 'HYBRID',
  ROADMAP: 'ROADMAP',
  SATELLITE: 'SATELLITE',
  TERRAIN: 'TERRAIN'
};

/**
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.MapTypeRegistry = function() {};

/**
 * @param {string} id
 * @param {google.maps.MapType|*} mapType
 * @return {undefined}
 * @override
 */
google.maps.MapTypeRegistry.prototype.set = function(id, mapType) {};

/**
 * @record
 */
google.maps.MapTypeStyle = function() {};

/**
 * @type {?string}
 */
google.maps.MapTypeStyle.prototype.elementType;

/**
 * @type {?string}
 */
google.maps.MapTypeStyle.prototype.featureType;

/**
 * @type {Array<Object>}
 */
google.maps.MapTypeStyle.prototype.stylers;

/**
 * @record
 */
google.maps.MapsEventListener = function() {};

/**
 * @return {undefined}
 */
google.maps.MapsEventListener.prototype.remove = function() {};

/**
 * @param {google.maps.MarkerOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.Marker = function(opts) {};

/**
 * @return {?google.maps.Animation}
 */
google.maps.Marker.prototype.getAnimation = function() {};

/**
 * @return {boolean}
 */
google.maps.Marker.prototype.getClickable = function() {};

/**
 * @return {string}
 */
google.maps.Marker.prototype.getCursor = function() {};

/**
 * @return {boolean}
 */
google.maps.Marker.prototype.getDraggable = function() {};

/**
 * @return {string|google.maps.Icon|google.maps.Symbol}
 */
google.maps.Marker.prototype.getIcon = function() {};

/**
 * @return {google.maps.MarkerLabel}
 */
google.maps.Marker.prototype.getLabel = function() {};

/**
 * @return {google.maps.Map|google.maps.StreetViewPanorama}
 */
google.maps.Marker.prototype.getMap = function() {};

/**
 * @return {number}
 */
google.maps.Marker.prototype.getOpacity = function() {};

/**
 * @return {google.maps.LatLng}
 */
google.maps.Marker.prototype.getPosition = function() {};

/**
 * @return {google.maps.MarkerShape}
 */
google.maps.Marker.prototype.getShape = function() {};

/**
 * @return {string}
 */
google.maps.Marker.prototype.getTitle = function() {};

/**
 * @return {boolean}
 */
google.maps.Marker.prototype.getVisible = function() {};

/**
 * @return {number}
 */
google.maps.Marker.prototype.getZIndex = function() {};

/**
 * @param {?google.maps.Animation} animation
 * @return {undefined}
 */
google.maps.Marker.prototype.setAnimation = function(animation) {};

/**
 * @param {boolean} flag
 * @return {undefined}
 */
google.maps.Marker.prototype.setClickable = function(flag) {};

/**
 * @param {string} cursor
 * @return {undefined}
 */
google.maps.Marker.prototype.setCursor = function(cursor) {};

/**
 * @param {?boolean} flag
 * @return {undefined}
 */
google.maps.Marker.prototype.setDraggable = function(flag) {};

/**
 * @param {string|google.maps.Icon|google.maps.Symbol} icon
 * @return {undefined}
 */
google.maps.Marker.prototype.setIcon = function(icon) {};

/**
 * @param {string|google.maps.MarkerLabel} label
 * @return {undefined}
 */
google.maps.Marker.prototype.setLabel = function(label) {};

/**
 * @param {google.maps.Map|google.maps.StreetViewPanorama} map
 * @return {undefined}
 */
google.maps.Marker.prototype.setMap = function(map) {};

/**
 * @param {number} opacity
 * @return {undefined}
 */
google.maps.Marker.prototype.setOpacity = function(opacity) {};

/**
 * @param {google.maps.MarkerOptions} options
 * @return {undefined}
 */
google.maps.Marker.prototype.setOptions = function(options) {};

/**
 * @param {google.maps.LatLng|google.maps.LatLngLiteral} latlng
 * @return {undefined}
 */
google.maps.Marker.prototype.setPosition = function(latlng) {};

/**
 * @param {google.maps.MarkerShape} shape
 * @return {undefined}
 */
google.maps.Marker.prototype.setShape = function(shape) {};

/**
 * @param {string} title
 * @return {undefined}
 */
google.maps.Marker.prototype.setTitle = function(title) {};

/**
 * @param {boolean} visible
 * @return {undefined}
 */
google.maps.Marker.prototype.setVisible = function(visible) {};

/**
 * @param {number} zIndex
 * @return {undefined}
 */
google.maps.Marker.prototype.setZIndex = function(zIndex) {};

/**
 * @constant
 * @type {number|string}
 */
google.maps.Marker.MAX_ZINDEX;

/**
 * @record
 */
google.maps.MarkerLabel = function() {};

/**
 * @type {string}
 */
google.maps.MarkerLabel.prototype.color;

/**
 * @type {string}
 */
google.maps.MarkerLabel.prototype.fontFamily;

/**
 * @type {string}
 */
google.maps.MarkerLabel.prototype.fontSize;

/**
 * @type {string}
 */
google.maps.MarkerLabel.prototype.fontWeight;

/**
 * @type {string}
 */
google.maps.MarkerLabel.prototype.text;

/**
 * @record
 */
google.maps.MarkerOptions = function() {};

/**
 * @type {?google.maps.Point|undefined}
 */
google.maps.MarkerOptions.prototype.anchorPoint;

/**
 * @type {?google.maps.Animation|undefined}
 */
google.maps.MarkerOptions.prototype.animation;

/**
 * @type {?boolean|undefined}
 */
google.maps.MarkerOptions.prototype.clickable;

/**
 * @type {?boolean|undefined}
 */
google.maps.MarkerOptions.prototype.crossOnDrag;

/**
 * @type {?string|undefined}
 */
google.maps.MarkerOptions.prototype.cursor;

/**
 * @type {?boolean|undefined}
 */
google.maps.MarkerOptions.prototype.draggable;

/**
 * @type {?string|?google.maps.Icon|?google.maps.Symbol|undefined}
 */
google.maps.MarkerOptions.prototype.icon;

/**
 * @type {?string|?google.maps.MarkerLabel|undefined}
 */
google.maps.MarkerOptions.prototype.label;

/**
 * @type {?google.maps.Map|?google.maps.StreetViewPanorama|undefined}
 */
google.maps.MarkerOptions.prototype.map;

/**
 * @type {?number|undefined}
 */
google.maps.MarkerOptions.prototype.opacity;

/**
 * @type {?boolean|undefined}
 */
google.maps.MarkerOptions.prototype.optimized;

/**
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.MarkerOptions.prototype.position;

/**
 * @type {?google.maps.MarkerShape|undefined}
 */
google.maps.MarkerOptions.prototype.shape;

/**
 * @type {?string|undefined}
 */
google.maps.MarkerOptions.prototype.title;

/**
 * @type {?boolean|undefined}
 */
google.maps.MarkerOptions.prototype.visible;

/**
 * @type {?number|undefined}
 */
google.maps.MarkerOptions.prototype.zIndex;

/**
 * @record
 */
google.maps.MarkerShape = function() {};

/**
 * @type {Array<number>}
 */
google.maps.MarkerShape.prototype.coords;

/**
 * @type {string}
 */
google.maps.MarkerShape.prototype.type;

/**
 * @record
 */
google.maps.MaxZoomResult = function() {};

/**
 * @type {google.maps.MaxZoomStatus}
 */
google.maps.MaxZoomResult.prototype.status;

/**
 * @type {number}
 */
google.maps.MaxZoomResult.prototype.zoom;

/**
 * @constructor
 */
google.maps.MaxZoomService = function() {};

/**
 * @param {google.maps.LatLng|google.maps.LatLngLiteral} latlng
 * @param {function(google.maps.MaxZoomResult)} callback
 * @return {undefined}
 */
google.maps.MaxZoomService.prototype.getMaxZoomAtLatLng = function(latlng, callback) {};

/**
 * @enum {string}
 */
google.maps.MaxZoomStatus = {
  ERROR: 'ERROR',
  OK: 'OK'
};

/**
 * @record
 */
google.maps.MotionTrackingControlOptions = function() {};

/**
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.MotionTrackingControlOptions.prototype.position;

/**
 * @record
 */
google.maps.MouseEvent = function() {};

/**
 * @type {google.maps.LatLng}
 */
google.maps.MouseEvent.prototype.latLng;

/**
 * @return {undefined}
 */
google.maps.MouseEvent.prototype.stop = function() {};

/**
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.OverlayView = function() {};

/**
 * @return {undefined}
 */
google.maps.OverlayView.prototype.draw = function() {};

/**
 * @return {google.maps.Map|google.maps.StreetViewPanorama}
 */
google.maps.OverlayView.prototype.getMap = function() {};

/**
 * @return {?google.maps.MapPanes}
 */
google.maps.OverlayView.prototype.getPanes = function() {};

/**
 * @return {!google.maps.MapCanvasProjection}
 */
google.maps.OverlayView.prototype.getProjection = function() {};

/**
 * @return {undefined}
 */
google.maps.OverlayView.prototype.onAdd = function() {};

/**
 * @return {undefined}
 */
google.maps.OverlayView.prototype.onRemove = function() {};

/**
 * @param {google.maps.Map|google.maps.StreetViewPanorama} map
 * @return {undefined}
 */
google.maps.OverlayView.prototype.setMap = function(map) {};

/**
 * @record
 */
google.maps.Padding = function() {};

/**
 * @type {number|undefined}
 */
google.maps.Padding.prototype.bottom;

/**
 * @type {number|undefined}
 */
google.maps.Padding.prototype.left;

/**
 * @type {number|undefined}
 */
google.maps.Padding.prototype.right;

/**
 * @type {number|undefined}
 */
google.maps.Padding.prototype.top;

/**
 * @record
 */
google.maps.PanControlOptions = function() {};

/**
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.PanControlOptions.prototype.position;

/**
 * @record
 */
google.maps.PanoProviderOptions = function() {};

/**
 * @type {boolean|undefined}
 */
google.maps.PanoProviderOptions.prototype.cors;

/**
 * @record
 */
google.maps.PathElevationRequest = function() {};

/**
 * @type {?Array<!google.maps.LatLng>|undefined}
 */
google.maps.PathElevationRequest.prototype.path;

/**
 * @type {number}
 */
google.maps.PathElevationRequest.prototype.samples;

/**
 * @record
 */
google.maps.Place = function() {};

/**
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.Place.prototype.location;

/**
 * @type {string|undefined}
 */
google.maps.Place.prototype.placeId;

/**
 * @type {string|undefined}
 */
google.maps.Place.prototype.query;

/**
 * @param {number} x
 * @param {number} y
 * @constructor
 */
google.maps.Point = function(x, y) {};

/**
 * @type {number}
 */
google.maps.Point.prototype.x;

/**
 * @type {number}
 */
google.maps.Point.prototype.y;

/**
 * @param {google.maps.Point} other
 * @return {boolean}
 */
google.maps.Point.prototype.equals = function(other) {};

/**
 * @return {string}
 * @override
 */
google.maps.Point.prototype.toString = function() {};

/**
 * @extends {google.maps.MouseEvent}
 * @record
 */
google.maps.PolyMouseEvent = function() {};

/**
 * @type {number}
 */
google.maps.PolyMouseEvent.prototype.edge;

/**
 * @type {number}
 */
google.maps.PolyMouseEvent.prototype.path;

/**
 * @type {number}
 */
google.maps.PolyMouseEvent.prototype.vertex;

/**
 * @param {google.maps.PolygonOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.Polygon = function(opts) {};

/**
 * @return {boolean}
 */
google.maps.Polygon.prototype.getDraggable = function() {};

/**
 * @return {boolean}
 */
google.maps.Polygon.prototype.getEditable = function() {};

/**
 * @return {google.maps.Map}
 */
google.maps.Polygon.prototype.getMap = function() {};

/**
 * @return {google.maps.MVCArray<google.maps.LatLng>}
 */
google.maps.Polygon.prototype.getPath = function() {};

/**
 * @return {!google.maps.MVCArray<!google.maps.MVCArray<!google.maps.LatLng>>}
 */
google.maps.Polygon.prototype.getPaths = function() {};

/**
 * @return {boolean}
 */
google.maps.Polygon.prototype.getVisible = function() {};

/**
 * @param {boolean} draggable
 * @return {undefined}
 */
google.maps.Polygon.prototype.setDraggable = function(draggable) {};

/**
 * @param {boolean} editable
 * @return {undefined}
 */
google.maps.Polygon.prototype.setEditable = function(editable) {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.Polygon.prototype.setMap = function(map) {};

/**
 * @param {google.maps.PolygonOptions} options
 * @return {undefined}
 */
google.maps.Polygon.prototype.setOptions = function(options) {};

/**
 * @param {google.maps.MVCArray<google.maps.LatLng>|Array<google.maps.LatLng|google.maps.LatLngLiteral>} path
 * @return {undefined}
 */
google.maps.Polygon.prototype.setPath = function(path) {};

/**
 * @param {google.maps.MVCArray<google.maps.MVCArray<google.maps.LatLng>>|google.maps.MVCArray<google.maps.LatLng>|Array<Array<google.maps.LatLng|google.maps.LatLngLiteral>>|Array<google.maps.LatLng|google.maps.LatLngLiteral>} paths
 * @return {undefined}
 */
google.maps.Polygon.prototype.setPaths = function(paths) {};

/**
 * @param {boolean} visible
 * @return {undefined}
 */
google.maps.Polygon.prototype.setVisible = function(visible) {};

/**
 * @record
 */
google.maps.PolygonOptions = function() {};

/**
 * @type {?boolean|undefined}
 */
google.maps.PolygonOptions.prototype.clickable;

/**
 * @type {?boolean|undefined}
 */
google.maps.PolygonOptions.prototype.draggable;

/**
 * @type {?boolean|undefined}
 */
google.maps.PolygonOptions.prototype.editable;

/**
 * @type {?string|undefined}
 */
google.maps.PolygonOptions.prototype.fillColor;

/**
 * @type {?number|undefined}
 */
google.maps.PolygonOptions.prototype.fillOpacity;

/**
 * @type {?boolean|undefined}
 */
google.maps.PolygonOptions.prototype.geodesic;

/**
 * @type {?google.maps.Map|undefined}
 */
google.maps.PolygonOptions.prototype.map;

/**
 * @type {?google.maps.MVCArray<!google.maps.MVCArray<!google.maps.LatLng>>|?google.maps.MVCArray<!google.maps.LatLng>|?Array<!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>>|?Array<!google.maps.LatLng|!google.maps.LatLngLiteral>|undefined}
 */
google.maps.PolygonOptions.prototype.paths;

/**
 * @type {?string|undefined}
 */
google.maps.PolygonOptions.prototype.strokeColor;

/**
 * @type {?number|undefined}
 */
google.maps.PolygonOptions.prototype.strokeOpacity;

/**
 * @type {?google.maps.StrokePosition|undefined}
 */
google.maps.PolygonOptions.prototype.strokePosition;

/**
 * @type {?number|undefined}
 */
google.maps.PolygonOptions.prototype.strokeWeight;

/**
 * @type {?boolean|undefined}
 */
google.maps.PolygonOptions.prototype.visible;

/**
 * @type {?number|undefined}
 */
google.maps.PolygonOptions.prototype.zIndex;

/**
 * @param {google.maps.PolylineOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.Polyline = function(opts) {};

/**
 * @return {boolean}
 */
google.maps.Polyline.prototype.getDraggable = function() {};

/**
 * @return {boolean}
 */
google.maps.Polyline.prototype.getEditable = function() {};

/**
 * @return {google.maps.Map}
 */
google.maps.Polyline.prototype.getMap = function() {};

/**
 * @return {!google.maps.MVCArray<!google.maps.LatLng>}
 */
google.maps.Polyline.prototype.getPath = function() {};

/**
 * @return {boolean}
 */
google.maps.Polyline.prototype.getVisible = function() {};

/**
 * @param {boolean} draggable
 * @return {undefined}
 */
google.maps.Polyline.prototype.setDraggable = function(draggable) {};

/**
 * @param {boolean} editable
 * @return {undefined}
 */
google.maps.Polyline.prototype.setEditable = function(editable) {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.Polyline.prototype.setMap = function(map) {};

/**
 * @param {google.maps.PolylineOptions} options
 * @return {undefined}
 */
google.maps.Polyline.prototype.setOptions = function(options) {};

/**
 * @param {google.maps.MVCArray<google.maps.LatLng>|Array<google.maps.LatLng|google.maps.LatLngLiteral>} path
 * @return {undefined}
 */
google.maps.Polyline.prototype.setPath = function(path) {};

/**
 * @param {boolean} visible
 * @return {undefined}
 */
google.maps.Polyline.prototype.setVisible = function(visible) {};

/**
 * @record
 */
google.maps.PolylineOptions = function() {};

/**
 * @type {?boolean|undefined}
 */
google.maps.PolylineOptions.prototype.clickable;

/**
 * @type {?boolean|undefined}
 */
google.maps.PolylineOptions.prototype.draggable;

/**
 * @type {?boolean|undefined}
 */
google.maps.PolylineOptions.prototype.editable;

/**
 * @type {?boolean|undefined}
 */
google.maps.PolylineOptions.prototype.geodesic;

/**
 * @type {?Array<!google.maps.IconSequence>|undefined}
 */
google.maps.PolylineOptions.prototype.icons;

/**
 * @type {?google.maps.Map|undefined}
 */
google.maps.PolylineOptions.prototype.map;

/**
 * @type {?google.maps.MVCArray<!google.maps.LatLng>|?Array<!google.maps.LatLng|!google.maps.LatLngLiteral>|undefined}
 */
google.maps.PolylineOptions.prototype.path;

/**
 * @type {?string|undefined}
 */
google.maps.PolylineOptions.prototype.strokeColor;

/**
 * @type {?number|undefined}
 */
google.maps.PolylineOptions.prototype.strokeOpacity;

/**
 * @type {?number|undefined}
 */
google.maps.PolylineOptions.prototype.strokeWeight;

/**
 * @type {?boolean|undefined}
 */
google.maps.PolylineOptions.prototype.visible;

/**
 * @type {?number|undefined}
 */
google.maps.PolylineOptions.prototype.zIndex;

/**
 * @record
 */
google.maps.Projection = function() {};

/**
 * @param {google.maps.LatLng} latLng
 * @param {google.maps.Point=} point
 * @return {google.maps.Point}
 */
google.maps.Projection.prototype.fromLatLngToPoint = function(latLng, point) {};

/**
 * @param {google.maps.Point} pixel
 * @param {boolean=} nowrap
 * @return {google.maps.LatLng}
 */
google.maps.Projection.prototype.fromPointToLatLng = function(pixel, nowrap) {};

/**
 * @param {google.maps.RectangleOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.Rectangle = function(opts) {};

/**
 * @return {google.maps.LatLngBounds}
 */
google.maps.Rectangle.prototype.getBounds = function() {};

/**
 * @return {boolean}
 */
google.maps.Rectangle.prototype.getDraggable = function() {};

/**
 * @return {boolean}
 */
google.maps.Rectangle.prototype.getEditable = function() {};

/**
 * @return {google.maps.Map}
 */
google.maps.Rectangle.prototype.getMap = function() {};

/**
 * @return {boolean}
 */
google.maps.Rectangle.prototype.getVisible = function() {};

/**
 * @param {google.maps.LatLngBounds|google.maps.LatLngBoundsLiteral} bounds
 * @return {undefined}
 */
google.maps.Rectangle.prototype.setBounds = function(bounds) {};

/**
 * @param {boolean} draggable
 * @return {undefined}
 */
google.maps.Rectangle.prototype.setDraggable = function(draggable) {};

/**
 * @param {boolean} editable
 * @return {undefined}
 */
google.maps.Rectangle.prototype.setEditable = function(editable) {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.Rectangle.prototype.setMap = function(map) {};

/**
 * @param {google.maps.RectangleOptions} options
 * @return {undefined}
 */
google.maps.Rectangle.prototype.setOptions = function(options) {};

/**
 * @param {boolean} visible
 * @return {undefined}
 */
google.maps.Rectangle.prototype.setVisible = function(visible) {};

/**
 * @record
 */
google.maps.RectangleOptions = function() {};

/**
 * @type {?google.maps.LatLngBounds|?google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.RectangleOptions.prototype.bounds;

/**
 * @type {?boolean|undefined}
 */
google.maps.RectangleOptions.prototype.clickable;

/**
 * @type {?boolean|undefined}
 */
google.maps.RectangleOptions.prototype.draggable;

/**
 * @type {?boolean|undefined}
 */
google.maps.RectangleOptions.prototype.editable;

/**
 * @type {?string|undefined}
 */
google.maps.RectangleOptions.prototype.fillColor;

/**
 * @type {?number|undefined}
 */
google.maps.RectangleOptions.prototype.fillOpacity;

/**
 * @type {?google.maps.Map|undefined}
 */
google.maps.RectangleOptions.prototype.map;

/**
 * @type {?string|undefined}
 */
google.maps.RectangleOptions.prototype.strokeColor;

/**
 * @type {?number|undefined}
 */
google.maps.RectangleOptions.prototype.strokeOpacity;

/**
 * @type {?google.maps.StrokePosition|undefined}
 */
google.maps.RectangleOptions.prototype.strokePosition;

/**
 * @type {?number|undefined}
 */
google.maps.RectangleOptions.prototype.strokeWeight;

/**
 * @type {?boolean|undefined}
 */
google.maps.RectangleOptions.prototype.visible;

/**
 * @type {?number|undefined}
 */
google.maps.RectangleOptions.prototype.zIndex;

/**
 * @record
 */
google.maps.RotateControlOptions = function() {};

/**
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.RotateControlOptions.prototype.position;

/**
 * @record
 */
google.maps.ScaleControlOptions = function() {};

/**
 * @type {?google.maps.ScaleControlStyle|undefined}
 */
google.maps.ScaleControlOptions.prototype.style;

/**
 * @enum {number}
 */
google.maps.ScaleControlStyle = {
  DEFAULT: 0
};

/**
 * @param {number} width
 * @param {number} height
 * @param {string=} widthUnit
 * @param {string=} heightUnit
 * @constructor
 */
google.maps.Size = function(width, height, widthUnit, heightUnit) {};

/**
 * @type {number}
 */
google.maps.Size.prototype.height;

/**
 * @type {number}
 */
google.maps.Size.prototype.width;

/**
 * @param {google.maps.Size} other
 * @return {boolean}
 */
google.maps.Size.prototype.equals = function(other) {};

/**
 * @return {string}
 * @override
 */
google.maps.Size.prototype.toString = function() {};

/**
 * @record
 */
google.maps.StreetViewAddressControlOptions = function() {};

/**
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.StreetViewAddressControlOptions.prototype.position;

/**
 * @record
 */
google.maps.StreetViewControlOptions = function() {};

/**
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.StreetViewControlOptions.prototype.position;

/**
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.StreetViewCoverageLayer = function() {};

/**
 * @return {google.maps.Map}
 */
google.maps.StreetViewCoverageLayer.prototype.getMap = function() {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.StreetViewCoverageLayer.prototype.setMap = function(map) {};

/**
 * @record
 */
google.maps.StreetViewLink = function() {};

/**
 * @type {?string}
 */
google.maps.StreetViewLink.prototype.description;

/**
 * @type {?number}
 */
google.maps.StreetViewLink.prototype.heading;

/**
 * @type {?string}
 */
google.maps.StreetViewLink.prototype.pano;

/**
 * @record
 */
google.maps.StreetViewLocation = function() {};

/**
 * @type {?string}
 */
google.maps.StreetViewLocation.prototype.description;

/**
 * @type {?google.maps.LatLng}
 */
google.maps.StreetViewLocation.prototype.latLng;

/**
 * @type {string}
 */
google.maps.StreetViewLocation.prototype.pano;

/**
 * @type {?string}
 */
google.maps.StreetViewLocation.prototype.shortDescription;

/**
 * @record
 */
google.maps.StreetViewLocationRequest = function() {};

/**
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.StreetViewLocationRequest.prototype.location;

/**
 * @type {?google.maps.StreetViewPreference|undefined}
 */
google.maps.StreetViewLocationRequest.prototype.preference;

/**
 * @type {?number|undefined}
 */
google.maps.StreetViewLocationRequest.prototype.radius;

/**
 * @type {?google.maps.StreetViewSource|undefined}
 */
google.maps.StreetViewLocationRequest.prototype.source;

/**
 * @record
 */
google.maps.StreetViewPanoRequest = function() {};

/**
 * @type {?string|undefined}
 */
google.maps.StreetViewPanoRequest.prototype.pano;

/**
 * @param {!Element} container
 * @param {google.maps.StreetViewPanoramaOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.StreetViewPanorama = function(container, opts) {};

/**
 * @type {!Array<google.maps.MVCArray<Node>>}
 */
google.maps.StreetViewPanorama.prototype.controls;

/**
 * @return {Array<google.maps.StreetViewLink>}
 */
google.maps.StreetViewPanorama.prototype.getLinks = function() {};

/**
 * @return {google.maps.StreetViewLocation}
 */
google.maps.StreetViewPanorama.prototype.getLocation = function() {};

/**
 * @return {boolean}
 */
google.maps.StreetViewPanorama.prototype.getMotionTracking = function() {};

/**
 * @return {string}
 */
google.maps.StreetViewPanorama.prototype.getPano = function() {};

/**
 * @return {!google.maps.StreetViewPov}
 */
google.maps.StreetViewPanorama.prototype.getPhotographerPov = function() {};

/**
 * @return {google.maps.LatLng}
 */
google.maps.StreetViewPanorama.prototype.getPosition = function() {};

/**
 * @return {!google.maps.StreetViewPov}
 */
google.maps.StreetViewPanorama.prototype.getPov = function() {};

/**
 * @return {google.maps.StreetViewStatus}
 */
google.maps.StreetViewPanorama.prototype.getStatus = function() {};

/**
 * @return {boolean}
 */
google.maps.StreetViewPanorama.prototype.getVisible = function() {};

/**
 * @return {number}
 */
google.maps.StreetViewPanorama.prototype.getZoom = function() {};

/**
 * @param {function(string): ?google.maps.StreetViewPanoramaData} provider
 * @param {!google.maps.PanoProviderOptions=} opt_options
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.registerPanoProvider = function(provider, opt_options) {};

/**
 * @param {Array<google.maps.StreetViewLink>} links
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setLinks = function(links) {};

/**
 * @param {boolean} motionTracking
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setMotionTracking = function(motionTracking) {};

/**
 * @param {google.maps.StreetViewPanoramaOptions} options
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setOptions = function(options) {};

/**
 * @param {string} pano
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setPano = function(pano) {};

/**
 * @param {google.maps.LatLng|google.maps.LatLngLiteral} latLng
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setPosition = function(latLng) {};

/**
 * @param {!google.maps.StreetViewPov} pov
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setPov = function(pov) {};

/**
 * @param {boolean} flag
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setVisible = function(flag) {};

/**
 * @param {number} zoom
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setZoom = function(zoom) {};

/**
 * @record
 */
google.maps.StreetViewPanoramaData = function() {};

/**
 * @type {string}
 */
google.maps.StreetViewPanoramaData.prototype.copyright;

/**
 * @type {string}
 */
google.maps.StreetViewPanoramaData.prototype.imageDate;

/**
 * @type {Array<google.maps.StreetViewLink>}
 */
google.maps.StreetViewPanoramaData.prototype.links;

/**
 * @type {google.maps.StreetViewLocation}
 */
google.maps.StreetViewPanoramaData.prototype.location;

/**
 * @type {google.maps.StreetViewTileData}
 */
google.maps.StreetViewPanoramaData.prototype.tiles;

/**
 * @record
 */
google.maps.StreetViewPanoramaOptions = function() {};

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.addressControl;

/**
 * @type {?google.maps.StreetViewAddressControlOptions|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.addressControlOptions;

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.clickToGo;

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.disableDefaultUI;

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.disableDoubleClickZoom;

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.enableCloseButton;

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.fullscreenControl;

/**
 * @type {?google.maps.FullscreenControlOptions|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.fullscreenControlOptions;

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.imageDateControl;

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.linksControl;

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.motionTracking;

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.motionTrackingControl;

/**
 * @type {?google.maps.MotionTrackingControlOptions|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.motionTrackingControlOptions;

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.panControl;

/**
 * @type {?google.maps.PanControlOptions|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.panControlOptions;

/**
 * @type {?string|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.pano;

/**
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.position;

/**
 * @type {?google.maps.StreetViewPov|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.pov;

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.scrollwheel;

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.showRoadLabels;

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.visible;

/**
 * @type {?number|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.zoom;

/**
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.zoomControl;

/**
 * @type {?google.maps.ZoomControlOptions|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.zoomControlOptions;

/**
 * @record
 */
google.maps.StreetViewPov = function() {};

/**
 * @type {number}
 */
google.maps.StreetViewPov.prototype.heading;

/**
 * @type {number}
 */
google.maps.StreetViewPov.prototype.pitch;

/**
 * @enum {string}
 */
google.maps.StreetViewPreference = {
  BEST: 'BEST',
  NEAREST: 'NEAREST'
};

/**
 * @constructor
 */
google.maps.StreetViewService = function() {};

/**
 * @param {!google.maps.StreetViewLocationRequest|!google.maps.StreetViewPanoRequest} request
 * @param {function(google.maps.StreetViewPanoramaData, !google.maps.StreetViewStatus)} callback
 * @return {undefined}
 */
google.maps.StreetViewService.prototype.getPanorama = function(request, callback) {};

/**
 * @enum {string}
 */
google.maps.StreetViewSource = {
  DEFAULT: 'DEFAULT',
  OUTDOOR: 'OUTDOOR'
};

/**
 * @enum {string}
 */
google.maps.StreetViewStatus = {
  OK: 'OK',
  UNKNOWN_ERROR: 'UNKNOWN_ERROR',
  ZERO_RESULTS: 'ZERO_RESULTS'
};

/**
 * @record
 */
google.maps.StreetViewTileData = function() {};

/**
 * @type {number}
 */
google.maps.StreetViewTileData.prototype.centerHeading;

/**
 * @type {google.maps.Size}
 */
google.maps.StreetViewTileData.prototype.tileSize;

/**
 * @type {google.maps.Size}
 */
google.maps.StreetViewTileData.prototype.worldSize;

/**
 * @param {string} pano
 * @param {number} tileZoom
 * @param {number} tileX
 * @param {number} tileY
 * @return {string}
 */
google.maps.StreetViewTileData.prototype.getTileUrl = function(pano, tileZoom, tileX, tileY) {};

/**
 * @enum {number}
 */
google.maps.StrokePosition = {
  CENTER: 0,
  INSIDE: 1,
  OUTSIDE: 2
};

/**
 * @param {Array<google.maps.MapTypeStyle>} styles
 * @param {google.maps.StyledMapTypeOptions=} options
 * @implements {google.maps.MapType}
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.StyledMapType = function(styles, options) {};

/**
 * @type {string}
 */
google.maps.StyledMapType.prototype.alt;

/**
 * @type {number}
 */
google.maps.StyledMapType.prototype.maxZoom;

/**
 * @type {number}
 */
google.maps.StyledMapType.prototype.minZoom;

/**
 * @type {string}
 */
google.maps.StyledMapType.prototype.name;

/**
 * @type {google.maps.Projection}
 */
google.maps.StyledMapType.prototype.projection;

/**
 * @type {number}
 */
google.maps.StyledMapType.prototype.radius;

/**
 * @type {google.maps.Size}
 */
google.maps.StyledMapType.prototype.tileSize;

/**
 * @param {google.maps.Point} tileCoord
 * @param {number} zoom
 * @param {Document} ownerDocument
 * @return {Node}
 * @override
 */
google.maps.StyledMapType.prototype.getTile = function(tileCoord, zoom, ownerDocument) {};

/**
 * @param {Node} tile
 * @return {undefined}
 * @override
 */
google.maps.StyledMapType.prototype.releaseTile = function(tile) {};

/**
 * @record
 */
google.maps.StyledMapTypeOptions = function() {};

/**
 * @type {?string|undefined}
 */
google.maps.StyledMapTypeOptions.prototype.alt;

/**
 * @type {?number|undefined}
 */
google.maps.StyledMapTypeOptions.prototype.maxZoom;

/**
 * @type {?number|undefined}
 */
google.maps.StyledMapTypeOptions.prototype.minZoom;

/**
 * @type {?string|undefined}
 */
google.maps.StyledMapTypeOptions.prototype.name;

/**
 * @record
 */
google.maps.Symbol = function() {};

/**
 * @type {google.maps.Point}
 */
google.maps.Symbol.prototype.anchor;

/**
 * @type {string}
 */
google.maps.Symbol.prototype.fillColor;

/**
 * @type {number}
 */
google.maps.Symbol.prototype.fillOpacity;

/**
 * @type {google.maps.Point}
 */
google.maps.Symbol.prototype.labelOrigin;

/**
 * @type {google.maps.SymbolPath|string}
 */
google.maps.Symbol.prototype.path;

/**
 * @type {number}
 */
google.maps.Symbol.prototype.rotation;

/**
 * @type {number}
 */
google.maps.Symbol.prototype.scale;

/**
 * @type {string}
 */
google.maps.Symbol.prototype.strokeColor;

/**
 * @type {number}
 */
google.maps.Symbol.prototype.strokeOpacity;

/**
 * @type {number}
 */
google.maps.Symbol.prototype.strokeWeight;

/**
 * @enum {number}
 */
google.maps.SymbolPath = {
  BACKWARD_CLOSED_ARROW: 0,
  BACKWARD_OPEN_ARROW: 1,
  CIRCLE: 2,
  FORWARD_CLOSED_ARROW: 3,
  FORWARD_OPEN_ARROW: 4
};

/**
 * @record
 */
google.maps.Time = function() {};

/**
 * @type {string}
 */
google.maps.Time.prototype.text;

/**
 * @type {string}
 */
google.maps.Time.prototype.time_zone;

/**
 * @type {!Date}
 */
google.maps.Time.prototype.value;

/**
 * @param {google.maps.TrafficLayerOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.TrafficLayer = function(opts) {};

/**
 * @return {google.maps.Map}
 */
google.maps.TrafficLayer.prototype.getMap = function() {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.TrafficLayer.prototype.setMap = function(map) {};

/**
 * @param {google.maps.TrafficLayerOptions} options
 * @return {undefined}
 */
google.maps.TrafficLayer.prototype.setOptions = function(options) {};

/**
 * @record
 */
google.maps.TrafficLayerOptions = function() {};

/**
 * @type {?boolean|undefined}
 */
google.maps.TrafficLayerOptions.prototype.autoRefresh;

/**
 * @type {?google.maps.Map|undefined}
 */
google.maps.TrafficLayerOptions.prototype.map;

/**
 * @enum {string}
 */
google.maps.TrafficModel = {
  BEST_GUESS: 'BEST_GUESS',
  OPTIMISTIC: 'OPTIMISTIC',
  PESSIMISTIC: 'PESSIMISTIC'
};

/**
 * @record
 */
google.maps.TransitAgency = function() {};

/**
 * @type {string}
 */
google.maps.TransitAgency.prototype.name;

/**
 * @type {string}
 */
google.maps.TransitAgency.prototype.phone;

/**
 * @type {string}
 */
google.maps.TransitAgency.prototype.url;

/**
 * @record
 */
google.maps.TransitDetails = function() {};

/**
 * @type {!google.maps.TransitStop}
 */
google.maps.TransitDetails.prototype.arrival_stop;

/**
 * @type {!google.maps.Time}
 */
google.maps.TransitDetails.prototype.arrival_time;

/**
 * @type {!google.maps.TransitStop}
 */
google.maps.TransitDetails.prototype.departure_stop;

/**
 * @type {!google.maps.Time}
 */
google.maps.TransitDetails.prototype.departure_time;

/**
 * @type {string}
 */
google.maps.TransitDetails.prototype.headsign;

/**
 * @type {number}
 */
google.maps.TransitDetails.prototype.headway;

/**
 * @type {!google.maps.TransitLine}
 */
google.maps.TransitDetails.prototype.line;

/**
 * @type {number}
 */
google.maps.TransitDetails.prototype.num_stops;

/**
 * @record
 */
google.maps.TransitFare = function() {};

/**
 * @type {string}
 */
google.maps.TransitFare.prototype.currency;

/**
 * @type {number}
 */
google.maps.TransitFare.prototype.value;

/**
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.TransitLayer = function() {};

/**
 * @return {google.maps.Map}
 */
google.maps.TransitLayer.prototype.getMap = function() {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.TransitLayer.prototype.setMap = function(map) {};

/**
 * @record
 */
google.maps.TransitLine = function() {};

/**
 * @type {Array<google.maps.TransitAgency>}
 */
google.maps.TransitLine.prototype.agencies;

/**
 * @type {string}
 */
google.maps.TransitLine.prototype.color;

/**
 * @type {string}
 */
google.maps.TransitLine.prototype.icon;

/**
 * @type {string}
 */
google.maps.TransitLine.prototype.name;

/**
 * @type {string}
 */
google.maps.TransitLine.prototype.short_name;

/**
 * @type {string}
 */
google.maps.TransitLine.prototype.text_color;

/**
 * @type {string}
 */
google.maps.TransitLine.prototype.url;

/**
 * @type {!google.maps.TransitVehicle}
 */
google.maps.TransitLine.prototype.vehicle;

/**
 * @enum {string}
 */
google.maps.TransitMode = {
  BUS: 'BUS',
  RAIL: 'RAIL',
  SUBWAY: 'SUBWAY',
  TRAIN: 'TRAIN',
  TRAM: 'TRAM'
};

/**
 * @record
 */
google.maps.TransitOptions = function() {};

/**
 * @type {?Date|undefined}
 */
google.maps.TransitOptions.prototype.arrivalTime;

/**
 * @type {?Date|undefined}
 */
google.maps.TransitOptions.prototype.departureTime;

/**
 * @type {?Array<!google.maps.TransitMode>|undefined}
 */
google.maps.TransitOptions.prototype.modes;

/**
 * @type {?google.maps.TransitRoutePreference|undefined}
 */
google.maps.TransitOptions.prototype.routingPreference;

/**
 * @enum {string}
 */
google.maps.TransitRoutePreference = {
  FEWER_TRANSFERS: 'FEWER_TRANSFERS',
  LESS_WALKING: 'LESS_WALKING'
};

/**
 * @record
 */
google.maps.TransitStop = function() {};

/**
 * @type {!google.maps.LatLng}
 */
google.maps.TransitStop.prototype.location;

/**
 * @type {string}
 */
google.maps.TransitStop.prototype.name;

/**
 * @record
 */
google.maps.TransitVehicle = function() {};

/**
 * @type {string}
 */
google.maps.TransitVehicle.prototype.icon;

/**
 * @type {string}
 */
google.maps.TransitVehicle.prototype.local_icon;

/**
 * @type {string}
 */
google.maps.TransitVehicle.prototype.name;

/**
 * @type {!google.maps.VehicleType}
 */
google.maps.TransitVehicle.prototype.type;

/**
 * @enum {string}
 */
google.maps.TravelMode = {
  BICYCLING: 'BICYCLING',
  DRIVING: 'DRIVING',
  TRANSIT: 'TRANSIT',
  WALKING: 'WALKING'
};

/**
 * @enum {number}
 */
google.maps.UnitSystem = {
  IMPERIAL: 0,
  METRIC: 1
};

/**
 * @enum {string}
 */
google.maps.VehicleType = {
  BUS: 'BUS',
  CABLE_CAR: 'CABLE_CAR',
  COMMUTER_TRAIN: 'COMMUTER_TRAIN',
  FERRY: 'FERRY',
  FUNICULAR: 'FUNICULAR',
  GONDOLA_LIFT: 'GONDOLA_LIFT',
  HEAVY_RAIL: 'HEAVY_RAIL',
  HIGH_SPEED_TRAIN: 'HIGH_SPEED_TRAIN',
  INTERCITY_BUS: 'INTERCITY_BUS',
  METRO_RAIL: 'METRO_RAIL',
  MONORAIL: 'MONORAIL',
  OTHER: 'OTHER',
  RAIL: 'RAIL',
  SHARE_TAXI: 'SHARE_TAXI',
  SUBWAY: 'SUBWAY',
  TRAM: 'TRAM',
  TROLLEYBUS: 'TROLLEYBUS'
};

/**
 * @record
 */
google.maps.ZoomControlOptions = function() {};

/**
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.ZoomControlOptions.prototype.position;

/** @const */
google.maps.drawing = {};

/**
 * @record
 */
google.maps.drawing.DrawingControlOptions = function() {};

/**
 * @type {?Array<!google.maps.drawing.OverlayType>|undefined}
 */
google.maps.drawing.DrawingControlOptions.prototype.drawingModes;

/**
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.drawing.DrawingControlOptions.prototype.position;

/**
 * @param {google.maps.drawing.DrawingManagerOptions=} options
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.drawing.DrawingManager = function(options) {};

/**
 * @return {?google.maps.drawing.OverlayType}
 */
google.maps.drawing.DrawingManager.prototype.getDrawingMode = function() {};

/**
 * @return {google.maps.Map}
 */
google.maps.drawing.DrawingManager.prototype.getMap = function() {};

/**
 * @param {?google.maps.drawing.OverlayType} drawingMode
 * @return {undefined}
 */
google.maps.drawing.DrawingManager.prototype.setDrawingMode = function(drawingMode) {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.drawing.DrawingManager.prototype.setMap = function(map) {};

/**
 * @param {google.maps.drawing.DrawingManagerOptions} options
 * @return {undefined}
 */
google.maps.drawing.DrawingManager.prototype.setOptions = function(options) {};

/**
 * @record
 */
google.maps.drawing.DrawingManagerOptions = function() {};

/**
 * @type {?google.maps.CircleOptions|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.circleOptions;

/**
 * @type {?boolean|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.drawingControl;

/**
 * @type {?google.maps.drawing.DrawingControlOptions|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.drawingControlOptions;

/**
 * @type {?google.maps.drawing.OverlayType|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.drawingMode;

/**
 * @type {?google.maps.Map|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.map;

/**
 * @type {?google.maps.MarkerOptions|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.markerOptions;

/**
 * @type {?google.maps.PolygonOptions|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.polygonOptions;

/**
 * @type {?google.maps.PolylineOptions|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.polylineOptions;

/**
 * @type {?google.maps.RectangleOptions|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.rectangleOptions;

/**
 * @record
 */
google.maps.drawing.OverlayCompleteEvent = function() {};

/**
 * @type {google.maps.Marker|google.maps.Polygon|google.maps.Polyline|google.maps.Rectangle|google.maps.Circle}
 */
google.maps.drawing.OverlayCompleteEvent.prototype.overlay;

/**
 * @type {google.maps.drawing.OverlayType}
 */
google.maps.drawing.OverlayCompleteEvent.prototype.type;

/**
 * @enum {string}
 */
google.maps.drawing.OverlayType = {
  CIRCLE: 'CIRCLE',
  MARKER: 'MARKER',
  POLYGON: 'POLYGON',
  POLYLINE: 'POLYLINE',
  RECTANGLE: 'RECTANGLE'
};

/** @const */
google.maps.event = {};

/**
 * @param {!Object} instance
 * @param {string} eventName
 * @param {!Function} handler
 * @param {boolean=} capture
 * @return {!google.maps.MapsEventListener}
 */
google.maps.event.addDomListener = function(instance, eventName, handler, capture) {};

/**
 * @param {!Object} instance
 * @param {string} eventName
 * @param {!Function} handler
 * @param {boolean=} capture
 * @return {!google.maps.MapsEventListener}
 */
google.maps.event.addDomListenerOnce = function(instance, eventName, handler, capture) {};

/**
 * @param {!Object} instance
 * @param {string} eventName
 * @param {!Function} handler
 * @return {!google.maps.MapsEventListener}
 */
google.maps.event.addListener = function(instance, eventName, handler) {};

/**
 * @param {!Object} instance
 * @param {string} eventName
 * @param {!Function} handler
 * @return {!google.maps.MapsEventListener}
 */
google.maps.event.addListenerOnce = function(instance, eventName, handler) {};

/**
 * @param {!Object} instance
 * @return {undefined}
 */
google.maps.event.clearInstanceListeners = function(instance) {};

/**
 * @param {!Object} instance
 * @param {string} eventName
 * @return {undefined}
 */
google.maps.event.clearListeners = function(instance, eventName) {};

/**
 * @param {!google.maps.MapsEventListener} listener
 * @return {undefined}
 */
google.maps.event.removeListener = function(listener) {};

/**
 * @param {!Object} instance
 * @param {string} eventName
 * @param {...*} var_args
 * @return {undefined}
 */
google.maps.event.trigger = function(instance, eventName, var_args) {};

/** @const */
google.maps.geometry = {};

/** @const */
google.maps.geometry.encoding = {};

/**
 * @param {string} encodedPath
 * @return {Array<google.maps.LatLng>}
 */
google.maps.geometry.encoding.decodePath = function(encodedPath) {};

/**
 * @param {Array<google.maps.LatLng>|google.maps.MVCArray<google.maps.LatLng>} path
 * @return {string}
 */
google.maps.geometry.encoding.encodePath = function(path) {};

/** @const */
google.maps.geometry.poly = {};

/**
 * @param {google.maps.LatLng} point
 * @param {google.maps.Polygon} polygon
 * @return {boolean}
 */
google.maps.geometry.poly.containsLocation = function(point, polygon) {};

/**
 * @param {google.maps.LatLng} point
 * @param {google.maps.Polygon|google.maps.Polyline} poly
 * @param {number=} tolerance
 * @return {boolean}
 */
google.maps.geometry.poly.isLocationOnEdge = function(point, poly, tolerance) {};

/** @const */
google.maps.geometry.spherical = {};

/**
 * @param {Array<google.maps.LatLng>|google.maps.MVCArray<google.maps.LatLng>} path
 * @param {number=} radius
 * @return {number}
 */
google.maps.geometry.spherical.computeArea = function(path, radius) {};

/**
 * @param {google.maps.LatLng} from
 * @param {google.maps.LatLng} to
 * @param {number=} radius
 * @return {number}
 */
google.maps.geometry.spherical.computeDistanceBetween = function(from, to, radius) {};

/**
 * @param {google.maps.LatLng} from
 * @param {google.maps.LatLng} to
 * @return {number}
 */
google.maps.geometry.spherical.computeHeading = function(from, to) {};

/**
 * @param {Array<google.maps.LatLng>|google.maps.MVCArray<google.maps.LatLng>} path
 * @param {number=} radius
 * @return {number}
 */
google.maps.geometry.spherical.computeLength = function(path, radius) {};

/**
 * @param {google.maps.LatLng} from
 * @param {number} distance
 * @param {number} heading
 * @param {number=} radius
 * @return {google.maps.LatLng}
 */
google.maps.geometry.spherical.computeOffset = function(from, distance, heading, radius) {};

/**
 * @param {google.maps.LatLng} to
 * @param {number} distance
 * @param {number} heading
 * @param {number=} radius
 * @return {google.maps.LatLng}
 */
google.maps.geometry.spherical.computeOffsetOrigin = function(to, distance, heading, radius) {};

/**
 * @param {Array<google.maps.LatLng>|google.maps.MVCArray<google.maps.LatLng>} loop
 * @param {number=} radius
 * @return {number}
 */
google.maps.geometry.spherical.computeSignedArea = function(loop, radius) {};

/**
 * @param {google.maps.LatLng} from
 * @param {google.maps.LatLng} to
 * @param {number} fraction
 * @return {google.maps.LatLng}
 */
google.maps.geometry.spherical.interpolate = function(from, to, fraction) {};

/** @const */
google.maps.places = {};

/**
 * @param {!HTMLInputElement} inputField
 * @param {google.maps.places.AutocompleteOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.places.Autocomplete = function(inputField, opts) {};

/**
 * @return {!google.maps.LatLngBounds|undefined}
 */
google.maps.places.Autocomplete.prototype.getBounds = function() {};

/**
 * @return {google.maps.places.PlaceResult}
 */
google.maps.places.Autocomplete.prototype.getPlace = function() {};

/**
 * @param {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral|undefined} bounds
 * @return {undefined}
 */
google.maps.places.Autocomplete.prototype.setBounds = function(bounds) {};

/**
 * @param {google.maps.places.ComponentRestrictions} restrictions
 * @return {undefined}
 */
google.maps.places.Autocomplete.prototype.setComponentRestrictions = function(restrictions) {};

/**
 * @param {google.maps.places.AutocompleteOptions} options
 * @return {undefined}
 */
google.maps.places.Autocomplete.prototype.setOptions = function(options) {};

/**
 * @param {Array<string>} types
 * @return {undefined}
 */
google.maps.places.Autocomplete.prototype.setTypes = function(types) {};

/**
 * @record
 */
google.maps.places.AutocompleteOptions = function() {};

/**
 * @type {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.places.AutocompleteOptions.prototype.bounds;

/**
 * @type {!google.maps.places.ComponentRestrictions|undefined}
 */
google.maps.places.AutocompleteOptions.prototype.componentRestrictions;

/**
 * @type {boolean|undefined}
 */
google.maps.places.AutocompleteOptions.prototype.placeIdOnly;

/**
 * @type {boolean|undefined}
 */
google.maps.places.AutocompleteOptions.prototype.strictBounds;

/**
 * @type {!Array<string>|undefined}
 */
google.maps.places.AutocompleteOptions.prototype.types;

/**
 * @record
 */
google.maps.places.AutocompletePrediction = function() {};

/**
 * @type {string}
 */
google.maps.places.AutocompletePrediction.prototype.description;

/**
 * @type {!Array<!google.maps.places.PredictionSubstring>}
 */
google.maps.places.AutocompletePrediction.prototype.matched_substrings;

/**
 * @type {string}
 */
google.maps.places.AutocompletePrediction.prototype.place_id;

/**
 * @type {!google.maps.places.StructuredFormatting}
 */
google.maps.places.AutocompletePrediction.prototype.structured_formatting;

/**
 * @type {!Array<!google.maps.places.PredictionTerm>}
 */
google.maps.places.AutocompletePrediction.prototype.terms;

/**
 * @type {!Array<string>}
 */
google.maps.places.AutocompletePrediction.prototype.types;

/**
 * @constructor
 */
google.maps.places.AutocompleteService = function() {};

/**
 * @param {google.maps.places.AutocompletionRequest} request
 * @param {function(Array<google.maps.places.AutocompletePrediction>, google.maps.places.PlacesServiceStatus)} callback
 * @return {undefined}
 */
google.maps.places.AutocompleteService.prototype.getPlacePredictions = function(request, callback) {};

/**
 * @param {google.maps.places.QueryAutocompletionRequest} request
 * @param {function(Array<google.maps.places.QueryAutocompletePrediction>, google.maps.places.PlacesServiceStatus)} callback
 * @return {undefined}
 */
google.maps.places.AutocompleteService.prototype.getQueryPredictions = function(request, callback) {};

/**
 * @record
 */
google.maps.places.AutocompletionRequest = function() {};

/**
 * @type {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.places.AutocompletionRequest.prototype.bounds;

/**
 * @type {!google.maps.places.ComponentRestrictions|undefined}
 */
google.maps.places.AutocompletionRequest.prototype.componentRestrictions;

/**
 * @type {string}
 */
google.maps.places.AutocompletionRequest.prototype.input;

/**
 * @type {!google.maps.LatLng|undefined}
 */
google.maps.places.AutocompletionRequest.prototype.location;

/**
 * @type {number}
 */
google.maps.places.AutocompletionRequest.prototype.offset;

/**
 * @type {number|undefined}
 */
google.maps.places.AutocompletionRequest.prototype.radius;

/**
 * @type {!Array<string>|undefined}
 */
google.maps.places.AutocompletionRequest.prototype.types;

/**
 * @record
 */
google.maps.places.ComponentRestrictions = function() {};

/**
 * @type {string|Array<string>}
 */
google.maps.places.ComponentRestrictions.prototype.country;

/**
 * @record
 */
google.maps.places.PhotoOptions = function() {};

/**
 * @type {?number|undefined}
 */
google.maps.places.PhotoOptions.prototype.maxHeight;

/**
 * @type {?number|undefined}
 */
google.maps.places.PhotoOptions.prototype.maxWidth;

/**
 * @record
 */
google.maps.places.PlaceAspectRating = function() {};

/**
 * @type {number}
 */
google.maps.places.PlaceAspectRating.prototype.rating;

/**
 * @type {string}
 */
google.maps.places.PlaceAspectRating.prototype.type;

/**
 * @record
 */
google.maps.places.PlaceDetailsRequest = function() {};

/**
 * @type {string}
 */
google.maps.places.PlaceDetailsRequest.prototype.placeId;

/**
 * @record
 */
google.maps.places.PlaceGeometry = function() {};

/**
 * @type {!google.maps.LatLng}
 */
google.maps.places.PlaceGeometry.prototype.location;

/**
 * @type {!google.maps.LatLngBounds|undefined}
 */
google.maps.places.PlaceGeometry.prototype.viewport;

/**
 * @record
 */
google.maps.places.PlacePhoto = function() {};

/**
 * @type {number}
 */
google.maps.places.PlacePhoto.prototype.height;

/**
 * @type {!Array<string>}
 */
google.maps.places.PlacePhoto.prototype.html_attributions;

/**
 * @type {number}
 */
google.maps.places.PlacePhoto.prototype.width;

/**
 * @param {!google.maps.places.PhotoOptions} opts
 * @return {string}
 */
google.maps.places.PlacePhoto.prototype.getUrl = function(opts) {};

/**
 * @record
 */
google.maps.places.PlaceResult = function() {};

/**
 * @type {!Array<!google.maps.GeocoderAddressComponent>|undefined}
 */
google.maps.places.PlaceResult.prototype.address_components;

/**
 * @type {!Array<!google.maps.places.PlaceAspectRating>|undefined}
 */
google.maps.places.PlaceResult.prototype.aspects;

/**
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.formatted_address;

/**
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.formatted_phone_number;

/**
 * @type {!google.maps.places.PlaceGeometry|undefined}
 */
google.maps.places.PlaceResult.prototype.geometry;

/**
 * @type {!Array<string>|undefined}
 */
google.maps.places.PlaceResult.prototype.html_attributions;

/**
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.icon;

/**
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.international_phone_number;

/**
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.name;

/**
 * @type {boolean|undefined}
 */
google.maps.places.PlaceResult.prototype.permanently_closed;

/**
 * @type {!Array<!google.maps.places.PlacePhoto>|undefined}
 */
google.maps.places.PlaceResult.prototype.photos;

/**
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.place_id;

/**
 * @type {number|undefined}
 */
google.maps.places.PlaceResult.prototype.price_level;

/**
 * @type {number|undefined}
 */
google.maps.places.PlaceResult.prototype.rating;

/**
 * @type {!Array<!google.maps.places.PlaceReview>|undefined}
 */
google.maps.places.PlaceResult.prototype.reviews;

/**
 * @type {!Array<string>|undefined}
 */
google.maps.places.PlaceResult.prototype.types;

/**
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.url;

/**
 * @type {number|undefined}
 */
google.maps.places.PlaceResult.prototype.utc_offset;

/**
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.vicinity;

/**
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.website;

/**
 * @record
 */
google.maps.places.PlaceReview = function() {};

/**
 * @type {Array<google.maps.places.PlaceAspectRating>}
 */
google.maps.places.PlaceReview.prototype.aspects;

/**
 * @type {string}
 */
google.maps.places.PlaceReview.prototype.author_name;

/**
 * @type {string}
 */
google.maps.places.PlaceReview.prototype.author_url;

/**
 * @type {string}
 */
google.maps.places.PlaceReview.prototype.language;

/**
 * @type {string}
 */
google.maps.places.PlaceReview.prototype.text;

/**
 * @record
 */
google.maps.places.PlaceSearchPagination = function() {};

/**
 * @type {boolean}
 */
google.maps.places.PlaceSearchPagination.prototype.hasNextPage;

/**
 * @return {undefined}
 */
google.maps.places.PlaceSearchPagination.prototype.nextPage = function() {};

/**
 * @record
 */
google.maps.places.PlaceSearchRequest = function() {};

/**
 * @type {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.bounds;

/**
 * @type {string|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.keyword;

/**
 * @type {!google.maps.LatLng|!google.maps.LatLngLiteral|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.location;

/**
 * @type {number|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.maxPriceLevel;

/**
 * @type {number|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.minPriceLevel;

/**
 * @type {string|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.name;

/**
 * @type {boolean|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.openNow;

/**
 * @type {number|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.radius;

/**
 * @type {!google.maps.places.RankBy|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.rankBy;

/**
 * @type {string|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.type;

/**
 * @param {HTMLDivElement|google.maps.Map} attrContainer
 * @constructor
 */
google.maps.places.PlacesService = function(attrContainer) {};

/**
 * @param {!google.maps.places.PlaceDetailsRequest} request
 * @param {function(?google.maps.places.PlaceResult, !google.maps.places.PlacesServiceStatus)} callback
 * @return {undefined}
 */
google.maps.places.PlacesService.prototype.getDetails = function(request, callback) {};

/**
 * @param {!google.maps.places.PlaceSearchRequest} request
 * @param {function(?Array<!google.maps.places.PlaceResult>, !google.maps.places.PlacesServiceStatus, ?google.maps.places.PlaceSearchPagination)} callback
 * @return {undefined}
 */
google.maps.places.PlacesService.prototype.nearbySearch = function(request, callback) {};

/**
 * @param {!google.maps.places.RadarSearchRequest} request
 * @param {function(?Array<!google.maps.places.PlaceResult>, !google.maps.places.PlacesServiceStatus)} callback
 * @return {undefined}
 */
google.maps.places.PlacesService.prototype.radarSearch = function(request, callback) {};

/**
 * @param {!google.maps.places.TextSearchRequest} request
 * @param {function(?Array<!google.maps.places.PlaceResult>, !google.maps.places.PlacesServiceStatus, ?google.maps.places.PlaceSearchPagination)} callback
 * @return {undefined}
 */
google.maps.places.PlacesService.prototype.textSearch = function(request, callback) {};

/**
 * @enum {string}
 */
google.maps.places.PlacesServiceStatus = {
  INVALID_REQUEST: 'INVALID_REQUEST',
  NOT_FOUND: 'NOT_FOUND',
  OK: 'OK',
  OVER_QUERY_LIMIT: 'OVER_QUERY_LIMIT',
  REQUEST_DENIED: 'REQUEST_DENIED',
  UNKNOWN_ERROR: 'UNKNOWN_ERROR',
  ZERO_RESULTS: 'ZERO_RESULTS'
};

/**
 * @record
 */
google.maps.places.PredictionSubstring = function() {};

/**
 * @type {number}
 */
google.maps.places.PredictionSubstring.prototype.length;

/**
 * @type {number}
 */
google.maps.places.PredictionSubstring.prototype.offset;

/**
 * @record
 */
google.maps.places.PredictionTerm = function() {};

/**
 * @type {number}
 */
google.maps.places.PredictionTerm.prototype.offset;

/**
 * @type {string}
 */
google.maps.places.PredictionTerm.prototype.value;

/**
 * @record
 */
google.maps.places.QueryAutocompletePrediction = function() {};

/**
 * @type {string}
 */
google.maps.places.QueryAutocompletePrediction.prototype.description;

/**
 * @type {!Array<!google.maps.places.PredictionSubstring>}
 */
google.maps.places.QueryAutocompletePrediction.prototype.matched_substrings;

/**
 * @type {string|undefined}
 */
google.maps.places.QueryAutocompletePrediction.prototype.place_id;

/**
 * @type {!Array<!google.maps.places.PredictionTerm>}
 */
google.maps.places.QueryAutocompletePrediction.prototype.terms;

/**
 * @record
 */
google.maps.places.QueryAutocompletionRequest = function() {};

/**
 * @type {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.places.QueryAutocompletionRequest.prototype.bounds;

/**
 * @type {string}
 */
google.maps.places.QueryAutocompletionRequest.prototype.input;

/**
 * @type {!google.maps.LatLng|undefined}
 */
google.maps.places.QueryAutocompletionRequest.prototype.location;

/**
 * @type {number}
 */
google.maps.places.QueryAutocompletionRequest.prototype.offset;

/**
 * @type {number|undefined}
 */
google.maps.places.QueryAutocompletionRequest.prototype.radius;

/**
 * @record
 */
google.maps.places.RadarSearchRequest = function() {};

/**
 * @type {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.places.RadarSearchRequest.prototype.bounds;

/**
 * @type {string|undefined}
 */
google.maps.places.RadarSearchRequest.prototype.keyword;

/**
 * @type {!google.maps.LatLng|!google.maps.LatLngLiteral|undefined}
 */
google.maps.places.RadarSearchRequest.prototype.location;

/**
 * @type {string|undefined}
 */
google.maps.places.RadarSearchRequest.prototype.name;

/**
 * @type {number|undefined}
 */
google.maps.places.RadarSearchRequest.prototype.radius;

/**
 * @type {string|undefined}
 */
google.maps.places.RadarSearchRequest.prototype.type;

/**
 * @enum {number}
 */
google.maps.places.RankBy = {
  DISTANCE: 0,
  PROMINENCE: 1
};

/**
 * @param {!HTMLInputElement} inputField
 * @param {google.maps.places.SearchBoxOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.places.SearchBox = function(inputField, opts) {};

/**
 * @return {google.maps.LatLngBounds}
 */
google.maps.places.SearchBox.prototype.getBounds = function() {};

/**
 * @return {Array<google.maps.places.PlaceResult>}
 */
google.maps.places.SearchBox.prototype.getPlaces = function() {};

/**
 * @param {google.maps.LatLngBounds|google.maps.LatLngBoundsLiteral} bounds
 * @return {undefined}
 */
google.maps.places.SearchBox.prototype.setBounds = function(bounds) {};

/**
 * @record
 */
google.maps.places.SearchBoxOptions = function() {};

/**
 * @type {?google.maps.LatLngBounds|?google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.places.SearchBoxOptions.prototype.bounds;

/**
 * @record
 */
google.maps.places.StructuredFormatting = function() {};

/**
 * @type {string}
 */
google.maps.places.StructuredFormatting.prototype.main_text;

/**
 * @type {!Array<!google.maps.places.PredictionSubstring>}
 */
google.maps.places.StructuredFormatting.prototype.main_text_matched_substrings;

/**
 * @type {string}
 */
google.maps.places.StructuredFormatting.prototype.secondary_text;

/**
 * @record
 */
google.maps.places.TextSearchRequest = function() {};

/**
 * @type {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.places.TextSearchRequest.prototype.bounds;

/**
 * @type {!google.maps.LatLng|!google.maps.LatLngLiteral|undefined}
 */
google.maps.places.TextSearchRequest.prototype.location;

/**
 * @type {string|undefined}
 */
google.maps.places.TextSearchRequest.prototype.query;

/**
 * @type {number|undefined}
 */
google.maps.places.TextSearchRequest.prototype.radius;

/**
 * @type {string|undefined}
 */
google.maps.places.TextSearchRequest.prototype.type;

/** @const */
google.maps.visualization = {};

/**
 * @param {google.maps.visualization.HeatmapLayerOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.visualization.HeatmapLayer = function(opts) {};

/**
 * @return {google.maps.MVCArray<google.maps.LatLng|google.maps.visualization.WeightedLocation>}
 */
google.maps.visualization.HeatmapLayer.prototype.getData = function() {};

/**
 * @return {google.maps.Map}
 */
google.maps.visualization.HeatmapLayer.prototype.getMap = function() {};

/**
 * @param {google.maps.MVCArray<google.maps.LatLng|google.maps.visualization.WeightedLocation>|Array<google.maps.LatLng|google.maps.visualization.WeightedLocation>} data
 * @return {undefined}
 */
google.maps.visualization.HeatmapLayer.prototype.setData = function(data) {};

/**
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.visualization.HeatmapLayer.prototype.setMap = function(map) {};

/**
 * @param {google.maps.visualization.HeatmapLayerOptions} options
 * @return {undefined}
 */
google.maps.visualization.HeatmapLayer.prototype.setOptions = function(options) {};

/**
 * @record
 */
google.maps.visualization.HeatmapLayerOptions = function() {};

/**
 * @type {?google.maps.MVCArray<!google.maps.LatLng>|?Array<!google.maps.LatLng>|undefined}
 */
google.maps.visualization.HeatmapLayerOptions.prototype.data;

/**
 * @type {?boolean|undefined}
 */
google.maps.visualization.HeatmapLayerOptions.prototype.dissipating;

/**
 * @type {?Array<string>|undefined}
 */
google.maps.visualization.HeatmapLayerOptions.prototype.gradient;

/**
 * @type {?google.maps.Map|undefined}
 */
google.maps.visualization.HeatmapLayerOptions.prototype.map;

/**
 * @type {?number|undefined}
 */
google.maps.visualization.HeatmapLayerOptions.prototype.maxIntensity;

/**
 * @type {?number|undefined}
 */
google.maps.visualization.HeatmapLayerOptions.prototype.opacity;

/**
 * @type {?number|undefined}
 */
google.maps.visualization.HeatmapLayerOptions.prototype.radius;

/**
 * @record
 */
google.maps.visualization.WeightedLocation = function() {};

/**
 * @type {google.maps.LatLng}
 */
google.maps.visualization.WeightedLocation.prototype.location;

/**
 * @type {number}
 */
google.maps.visualization.WeightedLocation.prototype.weight;
