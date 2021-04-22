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
 * @see https://developers.google.com/maps/documentation/javascript/reference
 * @externs
 */

/**
 * @const
 * @suppress {const,duplicate,strictMissingProperties}
 */
var google = {};

/**
 * @const
 * @suppress {const,duplicate,strictMissingProperties}
 */
google.maps = {};

/**
 * Google Maps JavaScript API version loaded by the browser.
 * @see https://developers.google.com/maps/documentation/javascript/versions
 *
 * @type {string}
 * @const
 */
google.maps.version;

/**
 * Animations that can be played on a marker. Use the {@link
 * google.maps.Marker.setAnimation} method on Marker or the {@link
 * google.maps.MarkerOptions.animation} option to play an animation.
 * @enum {number}
 */
google.maps.Animation = {
  /**
   * Marker bounces until animation is stopped by calling {@link
   * google.maps.Marker.setAnimation} with <code>null</code>.
   */
  BOUNCE: 0,
  /**
   * Marker drops from the top of the map to its final location. Animation will
   * cease once the marker comes to rest and {@link
   * google.maps.Marker.getAnimation} will return <code>null</code>. This type
   * of animation is usually specified during creation of the marker.
   */
  DROP: 1,
};

/**
 * A layer showing bike lanes and paths.
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.BicyclingLayer = function() {};

/**
 * Returns the map on which this layer is displayed.
 * @return {google.maps.Map}
 */
google.maps.BicyclingLayer.prototype.getMap = function() {};

/**
 * Renders the layer on the specified map. If map is set to <code>null</code>,
 * the layer will be removed.
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.BicyclingLayer.prototype.setMap = function(map) {};

/**
 * A circle on the Earth&#39;s surface; also known as a &quot;spherical
 * cap&quot;.
 * @param {google.maps.CircleOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.Circle = function(opts) {};

/**
 * Gets the <code>LatLngBounds</code> of this Circle.
 * @return {google.maps.LatLngBounds}
 */
google.maps.Circle.prototype.getBounds = function() {};

/**
 * Returns the center of this circle.
 * @return {google.maps.LatLng}
 */
google.maps.Circle.prototype.getCenter = function() {};

/**
 * Returns whether this circle can be dragged by the user.
 * @return {boolean}
 */
google.maps.Circle.prototype.getDraggable = function() {};

/**
 * Returns whether this circle can be edited by the user.
 * @return {boolean}
 */
google.maps.Circle.prototype.getEditable = function() {};

/**
 * Returns the map on which this circle is displayed.
 * @return {google.maps.Map}
 */
google.maps.Circle.prototype.getMap = function() {};

/**
 * Returns the radius of this circle (in meters).
 * @return {number}
 */
google.maps.Circle.prototype.getRadius = function() {};

/**
 * Returns whether this circle is visible on the map.
 * @return {boolean}
 */
google.maps.Circle.prototype.getVisible = function() {};

/**
 * Sets the center of this circle.
 * @param {google.maps.LatLng|google.maps.LatLngLiteral} center
 * @return {undefined}
 */
google.maps.Circle.prototype.setCenter = function(center) {};

/**
 * If set to <code>true</code>, the user can drag this circle over the map.
 * @param {boolean} draggable
 * @return {undefined}
 */
google.maps.Circle.prototype.setDraggable = function(draggable) {};

/**
 * If set to <code>true</code>, the user can edit this circle by dragging the
 * control points shown at the center and around the circumference of the
 * circle.
 * @param {boolean} editable
 * @return {undefined}
 */
google.maps.Circle.prototype.setEditable = function(editable) {};

/**
 * Renders the circle on the specified map. If map is set to <code>null</code>,
 * the circle will be removed.
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
 * Sets the radius of this circle (in meters).
 * @param {number} radius
 * @return {undefined}
 */
google.maps.Circle.prototype.setRadius = function(radius) {};

/**
 * Hides this circle if set to <code>false</code>.
 * @param {boolean} visible
 * @return {undefined}
 */
google.maps.Circle.prototype.setVisible = function(visible) {};

/**
 * Object literal which represents a circle.
 * @extends {google.maps.CircleOptions}
 * @record
 */
google.maps.CircleLiteral = function() {};

/**
 * The center of the Circle.
 * @type {!google.maps.LatLng|!google.maps.LatLngLiteral}
 */
google.maps.CircleLiteral.prototype.center;

/**
 * The radius in meters on the Earth&#39;s surface.
 * @type {number}
 */
google.maps.CircleLiteral.prototype.radius;

/**
 * CircleOptions object used to define the properties that can be set on a
 * Circle.
 * @record
 */
google.maps.CircleOptions = function() {};

/**
 * The center of the Circle.
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.CircleOptions.prototype.center;

/**
 * Indicates whether this <code>Circle</code> handles mouse events. Defaults to
 * <code>true</code>.
 * @type {?boolean|undefined}
 */
google.maps.CircleOptions.prototype.clickable;

/**
 * If set to <code>true</code>, the user can drag this circle over the map.
 * Defaults to <code>false</code>.
 * @type {?boolean|undefined}
 */
google.maps.CircleOptions.prototype.draggable;

/**
 * If set to <code>true</code>, the user can edit this circle by dragging the
 * control points shown at the center and around the circumference of the
 * circle. Defaults to <code>false</code>.
 * @type {?boolean|undefined}
 */
google.maps.CircleOptions.prototype.editable;

/**
 * The fill color. All CSS3 colors are supported except for extended named
 * colors.
 * @type {?string|undefined}
 */
google.maps.CircleOptions.prototype.fillColor;

/**
 * The fill opacity between 0.0 and 1.0.
 * @type {?number|undefined}
 */
google.maps.CircleOptions.prototype.fillOpacity;

/**
 * Map on which to display the Circle.
 * @type {?google.maps.Map|undefined}
 */
google.maps.CircleOptions.prototype.map;

/**
 * The radius in meters on the Earth&#39;s surface.
 * @type {?number|undefined}
 */
google.maps.CircleOptions.prototype.radius;

/**
 * The stroke color. All CSS3 colors are supported except for extended named
 * colors.
 * @type {?string|undefined}
 */
google.maps.CircleOptions.prototype.strokeColor;

/**
 * The stroke opacity between 0.0 and 1.0.
 * @type {?number|undefined}
 */
google.maps.CircleOptions.prototype.strokeOpacity;

/**
 * The stroke position. Defaults to CENTER. This property is not supported on
 * Internet Explorer 8 and earlier.
 * @type {?google.maps.StrokePosition|undefined}
 */
google.maps.CircleOptions.prototype.strokePosition;

/**
 * The stroke width in pixels.
 * @type {?number|undefined}
 */
google.maps.CircleOptions.prototype.strokeWeight;

/**
 * Whether this circle is visible on the map. Defaults to <code>true</code>.
 * @type {?boolean|undefined}
 */
google.maps.CircleOptions.prototype.visible;

/**
 * The zIndex compared to other polys.
 * @type {?number|undefined}
 */
google.maps.CircleOptions.prototype.zIndex;

/**
 * Identifiers used to specify the placement of controls on the map. Controls
 * are positioned relative to other controls in the same layout position.
 * Controls that are added first are positioned closer to the edge of the map.
 * <code> <br>&nbsp;&nbsp;+----------------+
 * <br>&nbsp;&nbsp;+&nbsp;TL&nbsp;&nbsp;&nbsp;&nbsp;TC&nbsp;&nbsp;&nbsp;&nbsp;TR
 * +
 * <br>&nbsp;&nbsp;+&nbsp;LT&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;RT
 * +
 * <br>&nbsp;&nbsp;+&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;+
 * <br>&nbsp;&nbsp;+&nbsp;LC&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;RC
 * +
 * <br>&nbsp;&nbsp;+&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;+
 * <br>&nbsp;&nbsp;+&nbsp;LB&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;RB
 * +
 * <br>&nbsp;&nbsp;+&nbsp;BL&nbsp;&nbsp;&nbsp;&nbsp;BC&nbsp;&nbsp;&nbsp;&nbsp;BR
 * + <br>&nbsp;&nbsp;+----------------+ <br></code> Elements in the top or
 * bottom row flow towards the middle of the row. Elements in the left or right
 * column flow towards the middle of the column.
 * @enum {number}
 */
google.maps.ControlPosition = {
  /**
   * Elements are positioned in the center of the bottom row.
   */
  BOTTOM_CENTER: 0,
  /**
   * Elements are positioned in the bottom left and flow towards the middle.
   * Elements are positioned to the right of the Google logo.
   */
  BOTTOM_LEFT: 1,
  /**
   * Elements are positioned in the bottom right and flow towards the middle.
   * Elements are positioned to the left of the copyrights.
   */
  BOTTOM_RIGHT: 2,
  /**
   * Elements are positioned on the left, above bottom-left elements, and flow
   * upwards.
   */
  LEFT_BOTTOM: 3,
  /**
   * Elements are positioned in the center of the left side.
   */
  LEFT_CENTER: 4,
  /**
   * Elements are positioned on the left, below top-left elements, and flow
   * downwards.
   */
  LEFT_TOP: 5,
  /**
   * Elements are positioned on the right, above bottom-right elements, and flow
   * upwards.
   */
  RIGHT_BOTTOM: 6,
  /**
   * Elements are positioned in the center of the right side.
   */
  RIGHT_CENTER: 7,
  /**
   * Elements are positioned on the right, below top-right elements, and flow
   * downwards.
   */
  RIGHT_TOP: 8,
  /**
   * Elements are positioned in the center of the top row.
   */
  TOP_CENTER: 9,
  /**
   * Elements are positioned in the top left and flow towards the middle.
   */
  TOP_LEFT: 10,
  /**
   * Elements are positioned in the top right and flow towards the middle.
   */
  TOP_RIGHT: 11,
};

/**
 * A layer for displaying geospatial data. Points, line-strings and polygons can
 * be displayed. <p> Every <code>Map</code> has a <code>Data</code> object by
 * default, so most of the time there is no need to construct one. For example:
 * <pre> var myMap = new google.maps.Map(...);<br>
 * myMap.data.addGeoJson(...);<br> myMap.data.setStyle(...); </pre> The
 * <code>Data</code> object is a collection of <a
 * href="#Data.Feature"><code>Features</code></a>.
 * @param {google.maps.Data.DataOptions=} options
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.Data = function(options) {};

/**
 * Adds a feature to the collection, and returns the added feature. <p> If the
 * feature has an ID, it will replace any existing feature in the collection
 * with the same ID. If no feature is given, a new feature will be created with
 * null geometry and no properties. If <code>FeatureOptions</code> are given, a
 * new feature will be created with the specified properties. <p> Note that the
 * IDs <code>1234</code> and <code>'1234'</code> are equivalent. Adding a
 * feature with ID <code>1234</code> will replace a feature with ID
 * <code>'1234'</code>, and vice versa.
 * @param {(google.maps.Data.Feature|google.maps.Data.FeatureOptions)=} feature
 * @return {!google.maps.Data.Feature}
 */
google.maps.Data.prototype.add = function(feature) {};

/**
 * Adds GeoJSON features to the collection. Give this method a parsed JSON. The
 * imported features are returned. Throws an exception if the GeoJSON could not
 * be imported.
 * @param {!Object} geoJson
 * @param {google.maps.Data.GeoJsonOptions=} options
 * @return {!Array<!google.maps.Data.Feature>}
 */
google.maps.Data.prototype.addGeoJson = function(geoJson, options) {};

/**
 * Checks whether the given feature is in the collection.
 * @param {!google.maps.Data.Feature} feature
 * @return {boolean}
 */
google.maps.Data.prototype.contains = function(feature) {};

/**
 * Repeatedly invokes the given function, passing a feature in the collection to
 * the function on each invocation. The order of iteration through the features
 * is undefined.
 * @param {function(!google.maps.Data.Feature): void} callback
 * @return {undefined}
 */
google.maps.Data.prototype.forEach = function(callback) {};

/**
 * Returns the position of the drawing controls on the map.
 * @return {google.maps.ControlPosition}
 */
google.maps.Data.prototype.getControlPosition = function() {};

/**
 * Returns which drawing modes are available for the user to select, in the
 * order they are displayed. This does not include the <code>null</code> drawing
 * mode, which is added by default. Possible drawing modes are
 * <code>"Point"</code>, <code>"LineString"</code> or <code>"Polygon"</code>.
 * @return {Array<string>}
 */
google.maps.Data.prototype.getControls = function() {};

/**
 * Returns the current drawing mode of the given Data layer. A drawing mode of
 * <code>null</code> means that the user can interact with the map as normal,
 * and clicks do not draw anything. Possible drawing modes are
 * <code>null</code>, <code>"Point"</code>, <code>"LineString"</code> or
 * <code>"Polygon"</code>.
 * @return {?string}
 */
google.maps.Data.prototype.getDrawingMode = function() {};

/**
 * Returns the feature with the given ID, if it exists in the collection.
 * Otherwise returns <code>undefined</code>. <p> Note that the IDs
 * <code>1234</code> and <code>'1234'</code> are equivalent. Either can be used
 * to look up the same feature.
 * @param {number|string} id
 * @return {!google.maps.Data.Feature|undefined}
 */
google.maps.Data.prototype.getFeatureById = function(id) {};

/**
 * Returns the map on which the features are displayed.
 * @return {google.maps.Map}
 */
google.maps.Data.prototype.getMap = function() {};

/**
 * Gets the style for all features in the collection.
 * @return {google.maps.Data.StylingFunction|google.maps.Data.StyleOptions}
 */
google.maps.Data.prototype.getStyle = function() {};

/**
 * Loads GeoJSON from a URL, and adds the features to the collection. <p> NOTE:
 * The GeoJSON is fetched using XHR, and may not work cross-domain. If you have
 * issues, we recommend you fetch the GeoJSON using your choice of AJAX library,
 * and then call <code>addGeoJson()</code>.
 * @param {string} url
 * @param {google.maps.Data.GeoJsonOptions=} options
 * @param {(function(!Array<!google.maps.Data.Feature>): void)=} callback
 * @return {undefined}
 */
google.maps.Data.prototype.loadGeoJson = function(url, options, callback) {};

/**
 * Changes the style of a feature. These changes are applied on top of the style
 * specified by <code>setStyle()</code>. Style properties set to
 * <code>null</code> revert to the value specified via <code>setStyle()</code>.
 * @param {!google.maps.Data.Feature} feature
 * @param {!google.maps.Data.StyleOptions} style
 * @return {undefined}
 */
google.maps.Data.prototype.overrideStyle = function(feature, style) {};

/**
 * Removes a feature from the collection.
 * @param {!google.maps.Data.Feature} feature
 * @return {undefined}
 */
google.maps.Data.prototype.remove = function(feature) {};

/**
 * Removes the effect of previous <code>overrideStyle()</code> calls. The style
 * of the given feature reverts to the style specified by
 * <code>setStyle()</code>. <p>If no feature is given, all features have their
 * style reverted.</p>
 * @param {google.maps.Data.Feature=} feature
 * @return {undefined}
 */
google.maps.Data.prototype.revertStyle = function(feature) {};

/**
 * Sets the position of the drawing controls on the map.
 * @param {google.maps.ControlPosition} controlPosition
 * @return {undefined}
 */
google.maps.Data.prototype.setControlPosition = function(controlPosition) {};

/**
 * Sets which drawing modes are available for the user to select, in the order
 * they are displayed. This should not include the <code>null</code> drawing
 * mode, which is added by default. If <code>null</code>, drawing controls are
 * disabled and not displayed. Possible drawing modes are <code>"Point"</code>,
 * <code>"LineString"</code> or <code>"Polygon"</code>.
 * @param {Array<string>} controls
 * @return {undefined}
 */
google.maps.Data.prototype.setControls = function(controls) {};

/**
 * Sets the current drawing mode of the given Data layer. A drawing mode of
 * <code>null</code> means that the user can interact with the map as normal,
 * and clicks do not draw anything. Possible drawing modes are
 * <code>null</code>, <code>"Point"</code>, <code>"LineString"</code> or
 * <code>"Polygon"</code>.
 * @param {?string} drawingMode
 * @return {undefined}
 */
google.maps.Data.prototype.setDrawingMode = function(drawingMode) {};

/**
 * Renders the features on the specified map. If map is set to
 * <code>null</code>, the features will be removed from the map.
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.Data.prototype.setMap = function(map) {};

/**
 * Sets the style for all features in the collection. Styles specified on a
 * per-feature basis via <code>overrideStyle()</code> continue to apply. <p>Pass
 * either an object with the desired style options, or a function that computes
 * the style for each feature. The function will be called every time a
 * feature&#39;s properties are updated.
 * @param {google.maps.Data.StylingFunction|google.maps.Data.StyleOptions} style
 * @return {undefined}
 */
google.maps.Data.prototype.setStyle = function(style) {};

/**
 * Exports the features in the collection to a GeoJSON object.
 * @param {function(!Object): void} callback
 * @return {undefined}
 */
google.maps.Data.prototype.toGeoJson = function(callback) {};

/**
 * The properties of a <code>addfeature</code> event.
 * @record
 */
google.maps.Data.AddFeatureEvent = function() {};

/**
 * The feature that was added to the <code>FeatureCollection</code>.
 * @type {!google.maps.Data.Feature}
 */
google.maps.Data.AddFeatureEvent.prototype.feature;

/**
 * DataOptions object used to define the properties that a developer can set on
 * a <code>Data</code> object.
 * @record
 */
google.maps.Data.DataOptions = function() {};

/**
 * The position of the drawing controls on the map. The default position is
 * TOP_LEFT.
 * @type {!google.maps.ControlPosition|undefined}
 */
google.maps.Data.DataOptions.prototype.controlPosition;

/**
 * Describes which drawing modes are available for the user to select, in the
 * order they are displayed. This should not include the <code>null</code>
 * drawing mode, which is added by default. If <code>null</code>, drawing
 * controls are disabled and not displayed. Defaults to <code>null</code>.
 * Possible drawing modes are <code>"Point"</code>, <code>"LineString"</code> or
 * <code>"Polygon"</code>.
 * @type {?Array<string>|undefined}
 */
google.maps.Data.DataOptions.prototype.controls;

/**
 * The current drawing mode of the given Data layer. A drawing mode of
 * <code>null</code> means that the user can interact with the map as normal,
 * and clicks do not draw anything. Defaults to <code>null</code>. Possible
 * drawing modes are <code>null</code>, <code>"Point"</code>,
 * <code>"LineString"</code> or <code>"Polygon"</code>.
 * @type {?string|undefined}
 */
google.maps.Data.DataOptions.prototype.drawingMode;

/**
 * When drawing is enabled and a user draws a Geometry (a Point, Line String or
 * Polygon), this function is called with that Geometry and should return a
 * Feature that is to be added to the Data layer. If a featureFactory is not
 * supplied, a Feature with no id and no properties will be created from that
 * Geometry instead. Defaults to <code>null</code>.
 * @type {?(function(!google.maps.Data.Geometry):
 *     !google.maps.Data.Feature)|undefined}
 */
google.maps.Data.DataOptions.prototype.featureFactory;

/**
 * Map on which to display the features in the collection.
 * @type {!google.maps.Map}
 */
google.maps.Data.DataOptions.prototype.map;

/**
 * Style for all features in the collection. For more details, see the <code><a
 * href='#Data'>setStyle()</a></code> method above.
 * @type {!google.maps.Data.StylingFunction|!google.maps.Data.StyleOptions|undefined}
 */
google.maps.Data.DataOptions.prototype.style;

/**
 * A feature has a geometry, an id, and a set of properties.
 * @param {google.maps.Data.FeatureOptions=} options
 * @constructor
 */
google.maps.Data.Feature = function(options) {};

/**
 * Repeatedly invokes the given function, passing a property value and name on
 * each invocation. The order of iteration through the properties is undefined.
 * @param {function(*, string): void} callback
 * @return {undefined}
 */
google.maps.Data.Feature.prototype.forEachProperty = function(callback) {};

/**
 * Returns the feature&#39;s geometry.
 * @return {google.maps.Data.Geometry}
 */
google.maps.Data.Feature.prototype.getGeometry = function() {};

/**
 * Returns the feature ID.
 * @return {number|string|undefined}
 */
google.maps.Data.Feature.prototype.getId = function() {};

/**
 * Returns the value of the requested property, or <code>undefined</code> if the
 * property does not exist.
 * @param {string} name
 * @return {*}
 */
google.maps.Data.Feature.prototype.getProperty = function(name) {};

/**
 * Removes the property with the given name.
 * @param {string} name
 * @return {undefined}
 */
google.maps.Data.Feature.prototype.removeProperty = function(name) {};

/**
 * Sets the feature&#39;s geometry.
 * @param {google.maps.Data.Geometry|google.maps.LatLng|google.maps.LatLngLiteral}
 *     newGeometry
 * @return {undefined}
 */
google.maps.Data.Feature.prototype.setGeometry = function(newGeometry) {};

/**
 * Sets the value of the specified property. If <code>newValue</code> is
 * <code>undefined</code> this is equivalent to calling
 * <code>removeProperty</code>.
 * @param {string} name
 * @param {*} newValue
 * @return {undefined}
 */
google.maps.Data.Feature.prototype.setProperty = function(name, newValue) {};

/**
 * Exports the feature to a GeoJSON object.
 * @param {function(!Object): void} callback
 * @return {undefined}
 */
google.maps.Data.Feature.prototype.toGeoJson = function(callback) {};

/**
 * Optional parameters for creating <code>Data.Feature</code> objects.
 * @record
 */
google.maps.Data.FeatureOptions = function() {};

/**
 * The feature geometry. If none is specified when a feature is constructed, the
 * feature&#39;s geometry will be <code>null</code>. If a <code>LatLng</code>
 * object or <code>LatLngLiteral</code> is given, this will be converted to a
 * <code>Data.Point</code> geometry.
 * @type {?google.maps.Data.Geometry|?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.Data.FeatureOptions.prototype.geometry;

/**
 * Feature ID is optional. If provided, it can be used to look up the feature in
 * a <code>Data</code> object using the <code>getFeatureById()</code> method.
 * Note that a feature&#39;s ID cannot be subsequently changed.
 * @type {number|string|undefined}
 */
google.maps.Data.FeatureOptions.prototype.id;

/**
 * The feature properties. This is an arbitrary mapping of property names to
 * values.
 * @type {?Object|undefined}
 */
google.maps.Data.FeatureOptions.prototype.properties;

/**
 * Optional parameters for importing GeoJSON.
 * @record
 */
google.maps.Data.GeoJsonOptions = function() {};

/**
 * The name of the Feature property to use as the feature ID. If not specified,
 * the GeoJSON Feature id will be used.
 * @type {?string|undefined}
 */
google.maps.Data.GeoJsonOptions.prototype.idPropertyName;

/**
 * A superclass for the various geometry objects.
 * @record
 */
google.maps.Data.Geometry = function() {};

/**
 * Repeatedly invokes the given function, passing a point from the geometry to
 * the function on each invocation.
 * @param {function(!google.maps.LatLng): void} callback
 * @return {undefined}
 */
google.maps.Data.Geometry.prototype.forEachLatLng = function(callback) {};

/**
 * Returns the type of the geometry object. Possibilities are
 * <code>"Point"</code>, <code>"MultiPoint"</code>, <code>"LineString"</code>,
 * <code>"MultiLineString"</code>, <code>"LinearRing"</code>,
 * <code>"Polygon"</code>, <code>"MultiPolygon"</code>, or
 * <code>"GeometryCollection"</code>.
 * @return {string}
 */
google.maps.Data.Geometry.prototype.getType = function() {};

/**
 * A GeometryCollection contains a number of geometry objects. Any
 * <code>LatLng</code> or <code>LatLngLiteral</code> objects are automatically
 * converted to <code>Data.Point</code> geometry objects.
 * @param {!Array<!google.maps.Data.Geometry|!google.maps.LatLng|!google.maps.LatLngLiteral>}
 *     elements
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.GeometryCollection = function(elements) {};

/**
 * @param {function(!google.maps.LatLng): void} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.GeometryCollection.prototype.forEachLatLng = function(
    callback) {};

/**
 * Returns an array of the contained geometry objects. A new array is returned
 * each time <code>getArray()</code> is called.
 * @return {!Array<!google.maps.Data.Geometry>}
 */
google.maps.Data.GeometryCollection.prototype.getArray = function() {};

/**
 * Returns the <code>n</code>-th contained geometry object.
 * @param {number} n
 * @return {!google.maps.Data.Geometry}
 */
google.maps.Data.GeometryCollection.prototype.getAt = function(n) {};

/**
 * Returns the number of contained geometry objects.
 * @return {number}
 */
google.maps.Data.GeometryCollection.prototype.getLength = function() {};

/**
 * Returns the string <code>"GeometryCollection"</code>.
 * @return {string}
 * @override
 */
google.maps.Data.GeometryCollection.prototype.getType = function() {};

/**
 * A LineString geometry contains a number of <code>LatLng</code>s.
 * @param {!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>} elements
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.LineString = function(elements) {};

/**
 * @param {function(!google.maps.LatLng): void} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.LineString.prototype.forEachLatLng = function(callback) {};

/**
 * Returns an array of the contained <code>LatLngs</code>. A new array is
 * returned each time <code>getArray()</code> is called.
 * @return {!Array<!google.maps.LatLng>}
 */
google.maps.Data.LineString.prototype.getArray = function() {};

/**
 * Returns the <code>n</code>-th contained <code>LatLng</code>.
 * @param {number} n
 * @return {!google.maps.LatLng}
 */
google.maps.Data.LineString.prototype.getAt = function(n) {};

/**
 * Returns the number of contained <code>LatLng</code>s.
 * @return {number}
 */
google.maps.Data.LineString.prototype.getLength = function() {};

/**
 * Returns the string <code>"LineString"</code>.
 * @return {string}
 * @override
 */
google.maps.Data.LineString.prototype.getType = function() {};

/**
 * A LinearRing geometry contains a number of <code>LatLng</code>s, representing
 * a closed LineString. There is no need to make the first <code>LatLng</code>
 * equal to the last <code>LatLng</code>. The LinearRing is closed implicitly.
 * @param {!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>} elements
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.LinearRing = function(elements) {};

/**
 * @param {function(!google.maps.LatLng): void} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.LinearRing.prototype.forEachLatLng = function(callback) {};

/**
 * Returns an array of the contained <code>LatLng</code>s. A new array is
 * returned each time <code>getArray()</code> is called.
 * @return {!Array<!google.maps.LatLng>}
 */
google.maps.Data.LinearRing.prototype.getArray = function() {};

/**
 * Returns the <code>n</code>-th contained <code>LatLng</code>.
 * @param {number} n
 * @return {!google.maps.LatLng}
 */
google.maps.Data.LinearRing.prototype.getAt = function(n) {};

/**
 * Returns the number of contained <code>LatLng</code>s.
 * @return {number}
 */
google.maps.Data.LinearRing.prototype.getLength = function() {};

/**
 * Returns the string <code>"LinearRing"</code>.
 * @return {string}
 * @override
 */
google.maps.Data.LinearRing.prototype.getType = function() {};

/**
 * This object is passed to mouse event handlers on a <code>Data</code> object.
 * @extends {google.maps.MapMouseEvent}
 * @record
 */
google.maps.Data.MouseEvent = function() {};

/**
 * The feature which generated the mouse event.
 * @type {!google.maps.Data.Feature}
 */
google.maps.Data.MouseEvent.prototype.feature;

/**
 * A MultiLineString geometry contains a number of <code>LineString</code>s.
 * @param {!Array<!google.maps.Data.LineString|!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>>}
 *     elements
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.MultiLineString = function(elements) {};

/**
 * @param {function(!google.maps.LatLng): void} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.MultiLineString.prototype.forEachLatLng = function(
    callback) {};

/**
 * Returns an array of the contained <code>Data.LineString</code>s. A new array
 * is returned each time <code>getArray()</code> is called.
 * @return {!Array<!google.maps.Data.LineString>}
 */
google.maps.Data.MultiLineString.prototype.getArray = function() {};

/**
 * Returns the <code>n</code>-th contained <code>Data.LineString</code>.
 * @param {number} n
 * @return {!google.maps.Data.LineString}
 */
google.maps.Data.MultiLineString.prototype.getAt = function(n) {};

/**
 * Returns the number of contained <code>Data.LineString</code>s.
 * @return {number}
 */
google.maps.Data.MultiLineString.prototype.getLength = function() {};

/**
 * Returns the string <code>"MultiLineString"</code>.
 * @return {string}
 * @override
 */
google.maps.Data.MultiLineString.prototype.getType = function() {};

/**
 * A MultiPoint geometry contains a number of <code>LatLng</code>s.
 * @param {!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>} elements
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.MultiPoint = function(elements) {};

/**
 * @param {function(!google.maps.LatLng): void} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.MultiPoint.prototype.forEachLatLng = function(callback) {};

/**
 * Returns an array of the contained <code>LatLng</code>s. A new array is
 * returned each time <code>getArray()</code> is called.
 * @return {!Array<!google.maps.LatLng>}
 */
google.maps.Data.MultiPoint.prototype.getArray = function() {};

/**
 * Returns the <code>n</code>-th contained <code>LatLng</code>.
 * @param {number} n
 * @return {!google.maps.LatLng}
 */
google.maps.Data.MultiPoint.prototype.getAt = function(n) {};

/**
 * Returns the number of contained <code>LatLng</code>s.
 * @return {number}
 */
google.maps.Data.MultiPoint.prototype.getLength = function() {};

/**
 * Returns the string <code>"MultiPoint"</code>.
 * @return {string}
 * @override
 */
google.maps.Data.MultiPoint.prototype.getType = function() {};

/**
 * A MultiPolygon geometry contains a number of <code>Data.Polygon</code>s.
 * @param {!Array<!google.maps.Data.Polygon|!Array<!google.maps.Data.LinearRing|!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>>>}
 *     elements
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.MultiPolygon = function(elements) {};

/**
 * @param {function(!google.maps.LatLng): void} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.MultiPolygon.prototype.forEachLatLng = function(callback) {};

/**
 * Returns an array of the contained <code>Data.Polygon</code>s. A new array is
 * returned each time <code>getArray()</code> is called.
 * @return {!Array<!google.maps.Data.Polygon>}
 */
google.maps.Data.MultiPolygon.prototype.getArray = function() {};

/**
 * Returns the <code>n</code>-th contained <code>Data.Polygon</code>.
 * @param {number} n
 * @return {!google.maps.Data.Polygon}
 */
google.maps.Data.MultiPolygon.prototype.getAt = function(n) {};

/**
 * Returns the number of contained <code>Data.Polygon</code>s.
 * @return {number}
 */
google.maps.Data.MultiPolygon.prototype.getLength = function() {};

/**
 * Returns the string <code>"MultiPolygon"</code>.
 * @return {string}
 * @override
 */
google.maps.Data.MultiPolygon.prototype.getType = function() {};

/**
 * A Point geometry contains a single <code>LatLng</code>.
 * @param {!google.maps.LatLng|!google.maps.LatLngLiteral} latLng
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.Point = function(latLng) {};

/**
 * @param {function(!google.maps.LatLng): void} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.Point.prototype.forEachLatLng = function(callback) {};

/**
 * Returns the contained <code>LatLng</code>.
 * @return {!google.maps.LatLng}
 */
google.maps.Data.Point.prototype.get = function() {};

/**
 * Returns the string <code>"Point"</code>.
 * @return {string}
 * @override
 */
google.maps.Data.Point.prototype.getType = function() {};

/**
 * A Polygon geometry contains a number of <code>Data.LinearRing</code>s. The
 * first linear-ring must be the polygon exterior boundary and subsequent
 * linear-rings must be interior boundaries, also known as holes. See the <a
 * href="https://developers.google.com/maps/documentation/javascript/examples/layer-data-polygon">sample
 * polygon with a hole</a>.
 * @param {!Array<!google.maps.Data.LinearRing|!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>>}
 *     elements
 * @implements {google.maps.Data.Geometry}
 * @constructor
 */
google.maps.Data.Polygon = function(elements) {};

/**
 * @param {function(!google.maps.LatLng): void} callback
 * @return {undefined}
 * @override
 */
google.maps.Data.Polygon.prototype.forEachLatLng = function(callback) {};

/**
 * Returns an array of the contained <code>Data.LinearRing</code>s. A new array
 * is returned each time <code>getArray()</code> is called.
 * @return {!Array<!google.maps.Data.LinearRing>}
 */
google.maps.Data.Polygon.prototype.getArray = function() {};

/**
 * Returns the <code>n</code>-th contained <code>Data.LinearRing</code>.
 * @param {number} n
 * @return {!google.maps.Data.LinearRing}
 */
google.maps.Data.Polygon.prototype.getAt = function(n) {};

/**
 * Returns the number of contained <code>Data.LinearRing</code>s.
 * @return {number}
 */
google.maps.Data.Polygon.prototype.getLength = function() {};

/**
 * Returns the string <code>"Polygon"</code>.
 * @return {string}
 * @override
 */
google.maps.Data.Polygon.prototype.getType = function() {};

/**
 * The properties of a <code>removefeature</code> event.
 * @record
 */
google.maps.Data.RemoveFeatureEvent = function() {};

/**
 * The feature that was removed from the <code>FeatureCollection</code>.
 * @type {!google.maps.Data.Feature}
 */
google.maps.Data.RemoveFeatureEvent.prototype.feature;

/**
 * The properties of a <code>removeproperty</code> event.
 * @record
 */
google.maps.Data.RemovePropertyEvent = function() {};

/**
 * The feature whose property was removed.
 * @type {!google.maps.Data.Feature}
 */
google.maps.Data.RemovePropertyEvent.prototype.feature;

/**
 * The property name.
 * @type {string}
 */
google.maps.Data.RemovePropertyEvent.prototype.name;

/**
 * The previous value.
 * @type {*}
 */
google.maps.Data.RemovePropertyEvent.prototype.oldValue;

/**
 * The properties of a <code>setgeometry</code> event.
 * @record
 */
google.maps.Data.SetGeometryEvent = function() {};

/**
 * The feature whose geometry was set.
 * @type {!google.maps.Data.Feature}
 */
google.maps.Data.SetGeometryEvent.prototype.feature;

/**
 * The new feature geometry.
 * @type {!google.maps.Data.Geometry|undefined}
 */
google.maps.Data.SetGeometryEvent.prototype.newGeometry;

/**
 * The previous feature geometry.
 * @type {!google.maps.Data.Geometry|undefined}
 */
google.maps.Data.SetGeometryEvent.prototype.oldGeometry;

/**
 * The properties of a <code>setproperty</code> event.
 * @record
 */
google.maps.Data.SetPropertyEvent = function() {};

/**
 * The feature whose property was set.
 * @type {!google.maps.Data.Feature}
 */
google.maps.Data.SetPropertyEvent.prototype.feature;

/**
 * The property name.
 * @type {string}
 */
google.maps.Data.SetPropertyEvent.prototype.name;

/**
 * The new value.
 * @type {*}
 */
google.maps.Data.SetPropertyEvent.prototype.newValue;

/**
 * The previous value. Will be <code>undefined</code> if the property was added.
 * @type {*}
 */
google.maps.Data.SetPropertyEvent.prototype.oldValue;

/**
 * These options specify the way a Feature should appear when displayed on a
 * map.
 * @record
 */
google.maps.Data.StyleOptions = function() {};

/**
 * If <code>true</code>, the marker receives mouse and touch events. Default
 * value is <code>true</code>.
 * @type {boolean|undefined}
 */
google.maps.Data.StyleOptions.prototype.clickable;

/**
 * Mouse cursor to show on hover. Only applies to point geometries.
 * @type {string|undefined}
 */
google.maps.Data.StyleOptions.prototype.cursor;

/**
 * If <code>true</code>, the object can be dragged across the map and the
 * underlying feature will have its geometry updated. Default value is
 * <code>false</code>.
 * @type {boolean|undefined}
 */
google.maps.Data.StyleOptions.prototype.draggable;

/**
 * If <code>true</code>, the object can be edited by dragging control points and
 * the underlying feature will have its geometry updated. Only applies to
 * LineString and Polygon geometries. Default value is <code>false</code>.
 * @type {boolean|undefined}
 */
google.maps.Data.StyleOptions.prototype.editable;

/**
 * The fill color. All CSS3 colors are supported except for extended named
 * colors. Only applies to polygon geometries.
 * @type {string|undefined}
 */
google.maps.Data.StyleOptions.prototype.fillColor;

/**
 * The fill opacity between 0.0 and 1.0. Only applies to polygon geometries.
 * @type {number|undefined}
 */
google.maps.Data.StyleOptions.prototype.fillOpacity;

/**
 * Icon for the foreground. If a string is provided, it is treated as though it
 * were an <code>Icon</code> with the string as <code>url</code>. Only applies
 * to point geometries.
 * @type {string|!google.maps.Icon|!google.maps.Symbol|undefined}
 */
google.maps.Data.StyleOptions.prototype.icon;

/**
 * Defines the image map used for hit detection. Only applies to point
 * geometries.
 * @type {!google.maps.MarkerShape|undefined}
 */
google.maps.Data.StyleOptions.prototype.shape;

/**
 * The stroke color. All CSS3 colors are supported except for extended named
 * colors. Only applies to line and polygon geometries.
 * @type {string|undefined}
 */
google.maps.Data.StyleOptions.prototype.strokeColor;

/**
 * The stroke opacity between 0.0 and 1.0. Only applies to line and polygon
 * geometries.
 * @type {number|undefined}
 */
google.maps.Data.StyleOptions.prototype.strokeOpacity;

/**
 * The stroke width in pixels. Only applies to line and polygon geometries.
 * @type {number|undefined}
 */
google.maps.Data.StyleOptions.prototype.strokeWeight;

/**
 * Rollover text. Only applies to point geometries.
 * @type {string|undefined}
 */
google.maps.Data.StyleOptions.prototype.title;

/**
 * Whether the feature is visible. Defaults to <code>true</code>.
 * @type {boolean|undefined}
 */
google.maps.Data.StyleOptions.prototype.visible;

/**
 * All features are displayed on the map in order of their zIndex, with higher
 * values displaying in front of features with lower values. Markers are always
 * displayed in front of line-strings and polygons.
 * @type {number|undefined}
 */
google.maps.Data.StyleOptions.prototype.zIndex;

/**
 * @typedef {function(!google.maps.Data.Feature):
 * !google.maps.Data.StyleOptions}
 */
google.maps.Data.StylingFunction;

/**
 * A single geocoded waypoint.
 * @record
 */
google.maps.DirectionsGeocodedWaypoint = function() {};

/**
 * Whether the geocoder did not return an exact match for the original waypoint,
 * though it was able to match part of the requested address.
 * @type {boolean|undefined}
 */
google.maps.DirectionsGeocodedWaypoint.prototype.partial_match;

/**
 * The place ID associated with the waypoint. Place IDs uniquely identify a
 * place in the Google Places database and on Google Maps. Learn more about <a
 * href="https://developers.google.com/maps/documentation/places/web-service/place-id">Place
 * IDs</a> in the Places API developer guide.
 * @type {string|undefined}
 */
google.maps.DirectionsGeocodedWaypoint.prototype.place_id;

/**
 * An array of strings denoting the type of the returned geocoded element. For a
 * list of possible strings, refer to the <a href=
 * "https://developers.google.com/maps/documentation/javascript/geocoding#GeocodingAddressTypes">
 * Address Component Types</a> section of the Developer&#39;s Guide.
 * @type {!Array<string>|undefined}
 */
google.maps.DirectionsGeocodedWaypoint.prototype.types;

/**
 * A single leg consisting of a set of steps in a <code><a
 * href="#DirectionsResult">DirectionsResult</a></code>. Some fields in the leg
 * may not be returned for all requests. Note that though this result is
 * &quot;JSON-like,&quot; it is not strictly JSON, as it directly and indirectly
 * includes <code>LatLng</code> objects.
 * @record
 */
google.maps.DirectionsLeg = function() {};

/**
 * An estimated arrival time for this leg. Only applicable for TRANSIT requests.
 * @type {!google.maps.Time|undefined}
 */
google.maps.DirectionsLeg.prototype.arrival_time;

/**
 * An estimated departure time for this leg. Only applicable for TRANSIT
 * requests.
 * @type {!google.maps.Time|undefined}
 */
google.maps.DirectionsLeg.prototype.departure_time;

/**
 * The total distance covered by this leg. This property may be undefined as the
 * distance may be unknown.
 * @type {!google.maps.Distance|undefined}
 */
google.maps.DirectionsLeg.prototype.distance;

/**
 * The total duration of this leg. This property may be <code>undefined</code>
 * as the duration may be unknown.
 * @type {!google.maps.Duration|undefined}
 */
google.maps.DirectionsLeg.prototype.duration;

/**
 * The total duration of this leg, taking into account the traffic conditions
 * indicated by the <code>trafficModel</code> property. This property may be
 * <code>undefined</code> as the duration may be unknown. Only available to
 * Premium Plan customers when <code>drivingOptions</code> is defined when
 * making the request.
 * @type {!google.maps.Duration|undefined}
 */
google.maps.DirectionsLeg.prototype.duration_in_traffic;

/**
 * The address of the destination of this leg.
 * @type {string}
 */
google.maps.DirectionsLeg.prototype.end_address;

/**
 * The <code>DirectionsService</code> calculates directions between locations by
 * using the nearest transportation option (usually a road) at the start and end
 * locations. <code>end_location</code> indicates the actual geocoded
 * destination, which may be different than the <code>end_location</code> of the
 * last step if, for example, the road is not near the destination of this leg.
 * @type {!google.maps.LatLng}
 */
google.maps.DirectionsLeg.prototype.end_location;

/**
 * The address of the origin of this leg.
 * @type {string}
 */
google.maps.DirectionsLeg.prototype.start_address;

/**
 * The <code>DirectionsService</code> calculates directions between locations by
 * using the nearest transportation option (usually a road) at the start and end
 * locations. <code>start_location</code> indicates the actual geocoded origin,
 * which may be different than the <code>start_location</code> of the first step
 * if, for example, the road is not near the origin of this leg.
 * @type {!google.maps.LatLng}
 */
google.maps.DirectionsLeg.prototype.start_location;

/**
 * An array of <code>DirectionsStep</code>s, each of which contains information
 * about the individual steps in this leg.
 * @type {!Array<!google.maps.DirectionsStep>}
 */
google.maps.DirectionsLeg.prototype.steps;

/**
 * An array of non-stopover waypoints along this leg, which were specified in
 * the original request. <p> <strong>Deprecated in alternative routes</strong>.
 * Version 3.27 will be the last version of the API that adds extra
 * <code>via_waypoints</code> in alternative routes. <p> When using the
 * Directions Service to implement draggable directions, it is recommended to
 * disable dragging of alternative routes. Only the main route should be
 * draggable. Users can drag the main route until it matches an alternative
 * route.
 * @type {!Array<!google.maps.LatLng>}
 */
google.maps.DirectionsLeg.prototype.via_waypoints;

/**
 * Renders directions obtained from the <code><a
 * href="#DirectionsService">DirectionsService</a></code>.
 * @param {google.maps.DirectionsRendererOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.DirectionsRenderer = function(opts) {};

/**
 * Returns the renderer&#39;s current set of directions.
 * @return {google.maps.DirectionsResult}
 */
google.maps.DirectionsRenderer.prototype.getDirections = function() {};

/**
 * Returns the map on which the <code>DirectionsResult</code> is rendered.
 * @return {google.maps.Map}
 */
google.maps.DirectionsRenderer.prototype.getMap = function() {};

/**
 * Returns the panel <code>&lt;div&gt;</code> in which the
 * <code>DirectionsResult</code> is rendered.
 * @return {Node}
 */
google.maps.DirectionsRenderer.prototype.getPanel = function() {};

/**
 * Returns the current (zero-based) route index in use by this
 * <code>DirectionsRenderer</code> object.
 * @return {number}
 */
google.maps.DirectionsRenderer.prototype.getRouteIndex = function() {};

/**
 * Set the renderer to use the result from the <code>DirectionsService</code>.
 * Setting a valid set of directions in this manner will display the directions
 * on the renderer&#39;s designated map and panel.
 * @param {google.maps.DirectionsResult} directions
 * @return {undefined}
 */
google.maps.DirectionsRenderer.prototype.setDirections = function(
    directions) {};

/**
 * This method specifies the map on which directions will be rendered. Pass
 * <code>null</code> to remove the directions from the map.
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.DirectionsRenderer.prototype.setMap = function(map) {};

/**
 * Change the options settings of this <code>DirectionsRenderer</code> after
 * initialization.
 * @param {google.maps.DirectionsRendererOptions} options
 * @return {undefined}
 */
google.maps.DirectionsRenderer.prototype.setOptions = function(options) {};

/**
 * This method renders the directions in a <code>&lt;div&gt;</code>. Pass
 * <code>null</code> to remove the content from the panel.
 * @param {Node} panel
 * @return {undefined}
 */
google.maps.DirectionsRenderer.prototype.setPanel = function(panel) {};

/**
 * Set the (zero-based) index of the route in the <code>DirectionsResult</code>
 * object to render. By default, the first route in the array will be rendered.
 * @param {number} routeIndex
 * @return {undefined}
 */
google.maps.DirectionsRenderer.prototype.setRouteIndex = function(
    routeIndex) {};

/**
 * This object defines the properties that can be set on a
 * <code>DirectionsRenderer</code> object.
 * @record
 */
google.maps.DirectionsRendererOptions = function() {};

/**
 * The directions to display on the map and/or in a <code>&lt;div&gt;</code>
 * panel, retrieved as a <code>DirectionsResult</code> object from
 * <code>DirectionsService</code>.
 * @type {?google.maps.DirectionsResult|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.directions;

/**
 * If <code>true</code>, allows the user to drag and modify the paths of routes
 * rendered by this <code>DirectionsRenderer</code>.
 * @type {?boolean|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.draggable;

/**
 * This property indicates whether the renderer should provide UI to select
 * amongst alternative routes. By default, this flag is <code>false</code> and a
 * user-selectable list of routes will be shown in the directions&#39;
 * associated panel. To hide that list, set <code>hideRouteList</code> to
 * <code>true</code>.
 * @type {?boolean|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.hideRouteList;

/**
 * The <code>InfoWindow</code> in which to render text information when a marker
 * is clicked. Existing info window content will be overwritten and its position
 * moved. If no info window is specified, the <code>DirectionsRenderer</code>
 * will create and use its own info window. This property will be ignored if
 * <code>suppressInfoWindows</code> is set to <code>true</code>.
 * @type {?google.maps.InfoWindow|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.infoWindow;

/**
 * Map on which to display the directions.
 * @type {?google.maps.Map|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.map;

/**
 * Options for the markers. All markers rendered by the
 * <code>DirectionsRenderer</code> will use these options.
 * @type {?google.maps.MarkerOptions|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.markerOptions;

/**
 * The <code>&lt;div&gt;</code> in which to display the directions steps.
 * @type {?Node|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.panel;

/**
 * Options for the polylines. All polylines rendered by the
 * <code>DirectionsRenderer</code> will use these options.
 * @type {?google.maps.PolylineOptions|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.polylineOptions;

/**
 * By default, the input map is centered and zoomed to the bounding box of this
 * set of directions. If this option is set to <code>true</code>, the viewport
 * is left unchanged, unless the map&#39;s center and zoom were never set.
 * @type {?boolean|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.preserveViewport;

/**
 * The index of the route within the <code>DirectionsResult</code> object. The
 * default value is 0.
 * @type {?number|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.routeIndex;

/**
 * Suppress the rendering of the <code>BicyclingLayer</code> when bicycling
 * directions are requested.
 * @type {?boolean|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.suppressBicyclingLayer;

/**
 * Suppress the rendering of info windows.
 * @type {?boolean|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.suppressInfoWindows;

/**
 * Suppress the rendering of markers.
 * @type {?boolean|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.suppressMarkers;

/**
 * Suppress the rendering of polylines.
 * @type {?boolean|undefined}
 */
google.maps.DirectionsRendererOptions.prototype.suppressPolylines;

/**
 * A directions query to be sent to the <code><a
 * href="#DirectionsService">DirectionsService</a></code>.
 * @record
 */
google.maps.DirectionsRequest = function() {};

/**
 * If <code>true</code>, instructs the Directions service to avoid ferries where
 * possible. Optional.
 * @type {boolean|undefined}
 */
google.maps.DirectionsRequest.prototype.avoidFerries;

/**
 * If <code>true</code>, instructs the Directions service to avoid highways
 * where possible. Optional.
 * @type {boolean|undefined}
 */
google.maps.DirectionsRequest.prototype.avoidHighways;

/**
 * If <code>true</code>, instructs the Directions service to avoid toll roads
 * where possible. Optional.
 * @type {boolean|undefined}
 */
google.maps.DirectionsRequest.prototype.avoidTolls;

/**
 * Location of destination. This can be specified as either a string to be
 * geocoded, or a <code>LatLng</code>, or a <code>Place</code>. Required.
 * @type {string|!google.maps.LatLng|!google.maps.Place|!google.maps.LatLngLiteral}
 */
google.maps.DirectionsRequest.prototype.destination;

/**
 * Settings that apply only to requests where <code>travelMode</code> is
 * <code>DRIVING</code>. This object will have no effect for other travel modes.
 * @type {!google.maps.DrivingOptions|undefined}
 */
google.maps.DirectionsRequest.prototype.drivingOptions;

/**
 * If set to <code>true</code>, the <code>DirectionsService</code> will attempt
 * to re-order the supplied intermediate waypoints to minimize overall cost of
 * the route. If waypoints are optimized, inspect
 * <code>DirectionsRoute.waypoint_order</code> in the response to determine the
 * new ordering.
 * @type {boolean|undefined}
 */
google.maps.DirectionsRequest.prototype.optimizeWaypoints;

/**
 * Location of origin. This can be specified as either a string to be geocoded,
 * or a <code>LatLng</code>, or a <code>Place</code>. Required.
 * @type {string|!google.maps.LatLng|!google.maps.Place|!google.maps.LatLngLiteral}
 */
google.maps.DirectionsRequest.prototype.origin;

/**
 * Whether or not route alternatives should be provided. Optional.
 * @type {boolean|undefined}
 */
google.maps.DirectionsRequest.prototype.provideRouteAlternatives;

/**
 * Region code used as a bias for geocoding requests. Optional.
 * @type {string|undefined}
 */
google.maps.DirectionsRequest.prototype.region;

/**
 * Settings that apply only to requests where <code>travelMode</code> is
 * TRANSIT. This object will have no effect for other travel modes.
 * @type {!google.maps.TransitOptions|undefined}
 */
google.maps.DirectionsRequest.prototype.transitOptions;

/**
 * Type of routing requested. Required.
 * @type {!google.maps.TravelMode}
 */
google.maps.DirectionsRequest.prototype.travelMode;

/**
 * Preferred unit system to use when displaying distance. Defaults to the unit
 * system used in the country of origin.
 * @type {!google.maps.UnitSystem|undefined}
 */
google.maps.DirectionsRequest.prototype.unitSystem;

/**
 * Array of intermediate waypoints. Directions are calculated from the origin to
 * the destination by way of each waypoint in this array. See the <a
 * href="https://developers.google.com/maps/documentation/javascript/directions#UsageLimits">
 * developer&#39;s guide</a> for the maximum number of waypoints allowed.
 * Waypoints are not supported for transit directions. Optional.
 * @type {!Array<!google.maps.DirectionsWaypoint>|undefined}
 */
google.maps.DirectionsRequest.prototype.waypoints;

/**
 * The directions response retrieved from the directions server. You can render
 * these using a {@link google.maps.DirectionsRenderer} or parse this object and
 * render it yourself. You must display the warnings and copyrights as noted in
 * the <a href="https://cloud.google.com/maps-platform/terms">Google Maps
 * Platform Terms of Service</a>. Note that though this result is
 * &quot;JSON-like,&quot; it is not strictly JSON, as it indirectly includes
 * <code>LatLng</code> objects.
 * @record
 */
google.maps.DirectionsResult = function() {};

/**
 * An array of <code>DirectionsGeocodedWaypoint</code>s, each of which contains
 * information about the geocoding of origin, destination and waypoints.
 * @type {!Array<!google.maps.DirectionsGeocodedWaypoint>|undefined}
 */
google.maps.DirectionsResult.prototype.geocoded_waypoints;

/**
 * An array of <code>DirectionsRoute</code>s, each of which contains information
 * about the legs and steps of which it is composed. There will only be one
 * route unless the <code>DirectionsRequest</code> was made with
 * <code>provideRouteAlternatives</code> set to <code>true</code>.
 * @type {!Array<!google.maps.DirectionsRoute>}
 */
google.maps.DirectionsResult.prototype.routes;

/**
 * A single route containing a set of legs in a <code><a
 * href="#DirectionsResult">DirectionsResult</a></code>. Note that though this
 * object is &quot;JSON-like,&quot; it is not strictly JSON, as it directly and
 * indirectly includes <code>LatLng</code> objects.
 * @record
 */
google.maps.DirectionsRoute = function() {};

/**
 * The bounds for this route.
 * @type {!google.maps.LatLngBounds}
 */
google.maps.DirectionsRoute.prototype.bounds;

/**
 * Copyrights text to be displayed for this route.
 * @type {string}
 */
google.maps.DirectionsRoute.prototype.copyrights;

/**
 * The total fare for the whole transit trip. Only applicable to transit
 * requests.
 * @type {!google.maps.TransitFare|undefined}
 */
google.maps.DirectionsRoute.prototype.fare;

/**
 * An array of <code>DirectionsLeg</code>s, each of which contains information
 * about the steps of which it is composed. There will be one leg for each
 * stopover waypoint or destination specified. So a route with no stopover
 * waypoints will contain one <code>DirectionsLeg</code> and a route with one
 * stopover waypoint will contain two.
 * @type {!Array<!google.maps.DirectionsLeg>}
 */
google.maps.DirectionsRoute.prototype.legs;

/**
 * An array of <code>LatLng</code>s representing the entire course of this
 * route. The path is simplified in order to make it suitable in contexts where
 * a small number of vertices is required (such as Static Maps API URLs).
 * @type {!Array<!google.maps.LatLng>}
 */
google.maps.DirectionsRoute.prototype.overview_path;

/**
 * An <a
 * href="https://developers.google.com/maps/documentation/utilities/polylinealgorithm">encoded
 * polyline representation</a> of the route in overview_path. This polyline is
 * an approximate (smoothed) path of the resulting directions.
 * @type {string}
 */
google.maps.DirectionsRoute.prototype.overview_polyline;

/**
 * Warnings to be displayed when showing these directions.
 * @type {!Array<string>}
 */
google.maps.DirectionsRoute.prototype.warnings;

/**
 * If <code>optimizeWaypoints</code> was set to <code>true</code>, this field
 * will contain the re-ordered permutation of the input waypoints. For example,
 * if the input was:<br> &nbsp;&nbsp;Origin: Los Angeles<br>
 * &nbsp;&nbsp;Waypoints: Dallas, Bangor, Phoenix<br> &nbsp;&nbsp;Destination:
 * New York<br> and the optimized output was ordered as follows:<br>
 * &nbsp;&nbsp;Origin: Los Angeles<br> &nbsp;&nbsp;Waypoints: Phoenix, Dallas,
 * Bangor<br> &nbsp;&nbsp;Destination: New York<br> then this field will be an
 * <code>Array</code> containing the values [2, 0, 1]. Note that the numbering
 * of waypoints is zero-based.<br> If any of the input waypoints has
 * <code>stopover</code> set to <code>false</code>, this field will be empty,
 * since route optimization is not available for such queries.
 * @type {!Array<number>}
 */
google.maps.DirectionsRoute.prototype.waypoint_order;

/**
 * A service for computing directions between two or more places.
 * @constructor
 */
google.maps.DirectionsService = function() {};

/**
 * Issue a directions search request. <aside class="note">Note: <strong>For the
 * beta release, <code>v=beta</code>, the callback is optional and a Promise is
 * returned</strong>. More information is available in the <a
 * href="https://developers.google.com/maps/documentation/javascript/promises">Promises
 * guide</a>.</aside>
 * @param {!google.maps.DirectionsRequest} request
 * @param {(function(?google.maps.DirectionsResult,
 *     !google.maps.DirectionsStatus): void)=} callback
 * @return {?Promise<!google.maps.DirectionsResult>}
 */
google.maps.DirectionsService.prototype.route = function(request, callback) {};

/**
 * The status returned by the <code>DirectionsService</code> on the completion
 * of a call to <code>route()</code>. Specify these by value, or by using the
 * constant&#39;s name. For example, <code>'OK'</code> or
 * <code>google.maps.DirectionsStatus.OK</code>.
 * @enum {string}
 */
google.maps.DirectionsStatus = {
  /**
   * The <code>DirectionsRequest</code> provided was invalid.
   */
  INVALID_REQUEST: 'INVALID_REQUEST',
  /**
   * Too many <code>DirectionsWaypoint</code>s were provided in the
   * <code>DirectionsRequest</code>. See the <a
   * href="https://developers.google.com/maps/documentation/javascript/directions#UsageLimits">
   * developer&#39;s guide</a> for the maximum number of waypoints allowed.
   */
  MAX_WAYPOINTS_EXCEEDED: 'MAX_WAYPOINTS_EXCEEDED',
  /**
   * At least one of the origin, destination, or waypoints could not be
   * geocoded.
   */
  NOT_FOUND: 'NOT_FOUND',
  /**
   * The response contains a valid <code>DirectionsResult</code>.
   */
  OK: 'OK',
  /**
   * The webpage has gone over the requests limit in too short a period of time.
   */
  OVER_QUERY_LIMIT: 'OVER_QUERY_LIMIT',
  /**
   * The webpage is not allowed to use the directions service.
   */
  REQUEST_DENIED: 'REQUEST_DENIED',
  /**
   * A directions request could not be processed due to a server error. The
   * request may succeed if you try again.
   */
  UNKNOWN_ERROR: 'UNKNOWN_ERROR',
  /**
   * No route could be found between the origin and destination.
   */
  ZERO_RESULTS: 'ZERO_RESULTS',
};

/**
 * A single <code>DirectionsStep</code> in a <code>DirectionsResult</code>. Some
 * fields may be <code>undefined</code>. Note that though this object is
 * &quot;JSON-like,&quot; it is not strictly JSON, as it directly includes
 * <code>LatLng</code> objects.
 * @record
 */
google.maps.DirectionsStep = function() {};

/**
 * The distance covered by this step. This property may be
 * <code>undefined</code> as the distance may be unknown.
 * @type {!google.maps.Distance|undefined}
 */
google.maps.DirectionsStep.prototype.distance;

/**
 * The typical time required to perform this step in seconds and in text form.
 * This property may be <code>undefined</code> as the duration may be unknown.
 * @type {!google.maps.Duration|undefined}
 */
google.maps.DirectionsStep.prototype.duration;

/**
 * The ending location of this step.
 * @type {!google.maps.LatLng}
 */
google.maps.DirectionsStep.prototype.end_location;

/**
 * Instructions for this step.
 * @type {string}
 */
google.maps.DirectionsStep.prototype.instructions;

/**
 * A sequence of <code>LatLng</code>s describing the course of this step.
 * @type {!Array<!google.maps.LatLng>}
 */
google.maps.DirectionsStep.prototype.path;

/**
 * The starting location of this step.
 * @type {!google.maps.LatLng}
 */
google.maps.DirectionsStep.prototype.start_location;

/**
 * Sub-steps of this step. Specified for non-transit sections of transit routes.
 * @type {!Array<!google.maps.DirectionsStep>|undefined}
 */
google.maps.DirectionsStep.prototype.steps;

/**
 * Transit-specific details about this step. This property will be undefined
 * unless the travel mode of this step is <code>TRANSIT</code>.
 * @type {!google.maps.TransitDetails|undefined}
 */
google.maps.DirectionsStep.prototype.transit;

/**
 * The mode of travel used in this step.
 * @type {!google.maps.TravelMode}
 */
google.maps.DirectionsStep.prototype.travel_mode;

/**
 * A <code>DirectionsWaypoint</code> represents a location between origin and
 * destination through which the trip should be routed.
 * @record
 */
google.maps.DirectionsWaypoint = function() {};

/**
 * Waypoint location. Can be an address string, a <code>LatLng</code>, or a
 * <code>Place</code>. Optional.
 * @type {string|!google.maps.LatLng|!google.maps.Place|undefined}
 */
google.maps.DirectionsWaypoint.prototype.location;

/**
 * If <code>true</code>, indicates that this waypoint is a stop between the
 * origin and destination. This has the effect of splitting the route into two
 * legs. If <code>false</code>, indicates that the route should be biased to go
 * through this waypoint, but not split into two legs. This is useful if you
 * want to create a route in response to the user dragging waypoints on a map.
 * This value is <code>true</code> by default. Optional.
 * @type {boolean|undefined}
 */
google.maps.DirectionsWaypoint.prototype.stopover;

/**
 * A representation of distance as a numeric value and a display string.
 * @record
 */
google.maps.Distance = function() {};

/**
 * A string representation of the distance value, using the
 * <code>UnitSystem</code> specified in the request.
 * @type {string}
 */
google.maps.Distance.prototype.text;

/**
 * The distance in meters.
 * @type {number}
 */
google.maps.Distance.prototype.value;

/**
 * The element-level status about a particular origin-destination pairing
 * returned by the <code>DistanceMatrixService</code> upon completion of a
 * distance matrix request. These values are specified as strings, for example,
 * <code>'OK'</code>.
 * @enum {string}
 */
google.maps.DistanceMatrixElementStatus = {
  /**
   * The origin and/or destination of this pairing could not be geocoded.
   */
  NOT_FOUND: 'NOT_FOUND',
  /**
   * The response contains a valid result.
   */
  OK: 'OK',
  /**
   * No route could be found between the origin and destination.
   */
  ZERO_RESULTS: 'ZERO_RESULTS',
};

/**
 * A distance matrix query sent by the <code>DistanceMatrixService</code>
 * containing arrays of origin and destination locations, and various options
 * for computing metrics.
 * @record
 */
google.maps.DistanceMatrixRequest = function() {};

/**
 * If <code>true</code>, instructs the Distance Matrix service to avoid ferries
 * where possible. Optional.
 * @type {boolean|undefined}
 */
google.maps.DistanceMatrixRequest.prototype.avoidFerries;

/**
 * If <code>true</code>, instructs the Distance Matrix service to avoid highways
 * where possible. Optional.
 * @type {boolean|undefined}
 */
google.maps.DistanceMatrixRequest.prototype.avoidHighways;

/**
 * If <code>true</code>, instructs the Distance Matrix service to avoid toll
 * roads where possible. Optional.
 * @type {boolean|undefined}
 */
google.maps.DistanceMatrixRequest.prototype.avoidTolls;

/**
 * An array containing destination address strings, or <code>LatLng</code>, or
 * <code>Place</code> objects, to which to calculate distance and time.
 * Required.
 * @type {!Array<string|!google.maps.LatLng|!google.maps.LatLngLiteral|!google.maps.Place>}
 */
google.maps.DistanceMatrixRequest.prototype.destinations;

/**
 * Settings that apply only to requests where <code>travelMode</code> is
 * <code>DRIVING</code>. This object will have no effect for other travel modes.
 * @type {!google.maps.DrivingOptions|undefined}
 */
google.maps.DistanceMatrixRequest.prototype.drivingOptions;

/**
 * An array containing origin address strings, or <code>LatLng</code>, or
 * <code>Place</code> objects, from which to calculate distance and time.
 * Required.
 * @type {!Array<string|!google.maps.LatLng|!google.maps.LatLngLiteral|!google.maps.Place>}
 */
google.maps.DistanceMatrixRequest.prototype.origins;

/**
 * Region code used as a bias for geocoding requests. Optional.
 * @type {string|undefined}
 */
google.maps.DistanceMatrixRequest.prototype.region;

/**
 * Settings that apply only to requests where <code>travelMode</code> is
 * TRANSIT. This object will have no effect for other travel modes.
 * @type {!google.maps.TransitOptions|undefined}
 */
google.maps.DistanceMatrixRequest.prototype.transitOptions;

/**
 * Type of routing requested. Required.
 * @type {!google.maps.TravelMode}
 */
google.maps.DistanceMatrixRequest.prototype.travelMode;

/**
 * Preferred unit system to use when displaying distance. Optional; defaults to
 * metric.
 * @type {!google.maps.UnitSystem|undefined}
 */
google.maps.DistanceMatrixRequest.prototype.unitSystem;

/**
 * The response to a <code>DistanceMatrixService</code> request, consisting of
 * the formatted origin and destination addresses, and a sequence of
 * <code>DistanceMatrixResponseRow</code>s, one for each corresponding origin
 * address.
 * @record
 */
google.maps.DistanceMatrixResponse = function() {};

/**
 * The formatted destination addresses.
 * @type {!Array<string>}
 */
google.maps.DistanceMatrixResponse.prototype.destinationAddresses;

/**
 * The formatted origin addresses.
 * @type {!Array<string>}
 */
google.maps.DistanceMatrixResponse.prototype.originAddresses;

/**
 * The rows of the matrix, corresponding to the origin addresses.
 * @type {!Array<!google.maps.DistanceMatrixResponseRow>}
 */
google.maps.DistanceMatrixResponse.prototype.rows;

/**
 * A single element of a response to a <code>DistanceMatrixService</code>
 * request, which contains the duration and distance from one origin to one
 * destination.
 * @record
 */
google.maps.DistanceMatrixResponseElement = function() {};

/**
 * The distance for this origin-destination pairing. This property may be
 * undefined as the distance may be unknown.
 * @type {!google.maps.Distance}
 */
google.maps.DistanceMatrixResponseElement.prototype.distance;

/**
 * The duration for this origin-destination pairing. This property may be
 * undefined as the duration may be unknown.
 * @type {!google.maps.Duration}
 */
google.maps.DistanceMatrixResponseElement.prototype.duration;

/**
 * The duration for this origin-destination pairing, taking into account the
 * traffic conditions indicated by the <code>trafficModel</code> property. This
 * property may be <code>undefined</code> as the duration may be unknown. Only
 * available to Premium Plan customers when <code>drivingOptions</code> is
 * defined when making the request.
 * @type {!google.maps.Duration}
 */
google.maps.DistanceMatrixResponseElement.prototype.duration_in_traffic;

/**
 * The total fare for this origin-destination pairing. Only applicable to
 * transit requests.
 * @type {!google.maps.TransitFare}
 */
google.maps.DistanceMatrixResponseElement.prototype.fare;

/**
 * The status of this particular origin-destination pairing.
 * @type {!google.maps.DistanceMatrixElementStatus}
 */
google.maps.DistanceMatrixResponseElement.prototype.status;

/**
 * A row of the response to a <code>DistanceMatrixService</code> request,
 * consisting of a sequence of <code>DistanceMatrixResponseElement</code>s, one
 * for each corresponding destination address.
 * @record
 */
google.maps.DistanceMatrixResponseRow = function() {};

/**
 * The row&#39;s elements, corresponding to the destination addresses.
 * @type {!Array<!google.maps.DistanceMatrixResponseElement>}
 */
google.maps.DistanceMatrixResponseRow.prototype.elements;

/**
 * A service for computing distances between multiple origins and destinations.
 * @constructor
 */
google.maps.DistanceMatrixService = function() {};

/**
 * Issues a distance matrix request. <aside class="note">Note: <strong>For the
 * beta release, <code>v=beta</code>, the callback is optional and a Promise is
 * returned</strong>. More information is available in the <a
 * href="https://developers.google.com/maps/documentation/javascript/promises">Promises
 * guide</a>.</aside>
 * @param {!google.maps.DistanceMatrixRequest} request
 * @param {(function(?google.maps.DistanceMatrixResponse,
 *     !google.maps.DistanceMatrixStatus): void)=} callback
 * @return {?Promise<!google.maps.DistanceMatrixResponse>}
 */
google.maps.DistanceMatrixService.prototype.getDistanceMatrix = function(
    request, callback) {};

/**
 * The top-level status about the request in general returned by the
 * <code>DistanceMatrixService</code> upon completion of a distance matrix
 * request. Specify these by value, or by using the constant&#39;s name. For
 * example, <code>'OK'</code> or
 * <code>google.maps.DistanceMatrixStatus.OK</code>.
 * @enum {string}
 */
google.maps.DistanceMatrixStatus = {
  /**
   * The provided request was invalid.
   */
  INVALID_REQUEST: 'INVALID_REQUEST',
  /**
   * The request contains more than 25 origins, or more than 25 destinations.
   */
  MAX_DIMENSIONS_EXCEEDED: 'MAX_DIMENSIONS_EXCEEDED',
  /**
   * The product of origins and destinations exceeds the per-query limit.
   */
  MAX_ELEMENTS_EXCEEDED: 'MAX_ELEMENTS_EXCEEDED',
  /**
   * The response contains a valid result.
   */
  OK: 'OK',
  /**
   * Too many elements have been requested within the allowed time period. The
   * request should succeed if you try again after some time.
   */
  OVER_QUERY_LIMIT: 'OVER_QUERY_LIMIT',
  /**
   * The service denied use of the Distance Matrix service by your web page.
   */
  REQUEST_DENIED: 'REQUEST_DENIED',
  /**
   * A Distance Matrix request could not be processed due to a server error. The
   * request may succeed if you try again.
   */
  UNKNOWN_ERROR: 'UNKNOWN_ERROR',
};

/**
 * Configures the <code><a
 * href="#DirectionsRequest">DirectionsRequest</a></code> when the travel mode
 * is set to <code>DRIVING</code>.
 * @record
 */
google.maps.DrivingOptions = function() {};

/**
 * The desired departure time for the route, specified as a <code>Date</code>
 * object. The <code>Date</code> object measures time in milliseconds since 1
 * January 1970. This must be specified for a <code>DrivingOptions</code> to be
 * valid. The departure time must be set to the current time or some time in the
 * future. It cannot be in the past.
 * @type {!Date}
 */
google.maps.DrivingOptions.prototype.departureTime;

/**
 * The preferred assumption to use when predicting duration in traffic. The
 * default is <code>BEST_GUESS</code>.
 * @type {!google.maps.TrafficModel|undefined}
 */
google.maps.DrivingOptions.prototype.trafficModel;

/**
 * A representation of duration as a numeric value and a display string.
 * @record
 */
google.maps.Duration = function() {};

/**
 * A string representation of the duration value.
 * @type {string}
 */
google.maps.Duration.prototype.text;

/**
 * The duration in seconds.
 * @type {number}
 */
google.maps.Duration.prototype.value;

/**
 * The result of an <code>ElevationService</code> request, consisting of the set
 * of elevation coordinates and their elevation values. Note that a single
 * request may produce multiple <code>ElevationResult</code>s.
 * @record
 */
google.maps.ElevationResult = function() {};

/**
 * The elevation of this point on Earth, in meters above sea level.
 * @type {number}
 */
google.maps.ElevationResult.prototype.elevation;

/**
 * The location of this elevation result.
 * @type {google.maps.LatLng}
 */
google.maps.ElevationResult.prototype.location;

/**
 * The distance, in meters, between sample points from which the elevation was
 * interpolated. This property will be missing if the resolution is not known.
 * Note that elevation data becomes more coarse (larger <code>resolution</code>
 * values) when multiple points are passed. To obtain the most accurate
 * elevation value for a point, it should be queried independently.
 * @type {number}
 */
google.maps.ElevationResult.prototype.resolution;

/**
 * Defines a service class that talks directly to Google servers for requesting
 * elevation data.
 * @constructor
 */
google.maps.ElevationService = function() {};

/**
 * Makes an elevation request along a path, where the elevation data are
 * returned as distance-based samples along that path. <aside class="note">Note:
 * <strong>A Promise is only returned for the beta release,
 * <code>v=beta</code></strong>. More information is available in the <a
 * href="https://developers.google.com/maps/documentation/javascript/promises">Promises
 * guide</a>.</aside>
 * @param {!google.maps.PathElevationRequest} request
 * @param {(function(?Array<!google.maps.ElevationResult>,
 *     !google.maps.ElevationStatus): void)=} callback
 * @return {?Promise<!google.maps.PathElevationResponse>}
 */
google.maps.ElevationService.prototype.getElevationAlongPath = function(
    request, callback) {};

/**
 * Makes an elevation request for a list of discrete locations. <aside
 * class="note">Note: <strong>For the beta release, <code>v=beta</code>, the
 * callback is optional and a Promise is returned</strong>. More information is
 * available in the <a
 * href="https://developers.google.com/maps/documentation/javascript/promises">Promises
 * guide</a>.</aside>
 * @param {!google.maps.LocationElevationRequest} request
 * @param {(function(?Array<!google.maps.ElevationResult>,
 *     !google.maps.ElevationStatus): void)=} callback
 * @return {?Promise<!google.maps.LocationElevationResponse>}
 */
google.maps.ElevationService.prototype.getElevationForLocations = function(
    request, callback) {};

/**
 * The status returned by the <code>ElevationService</code> upon completion of
 * an elevation request. Specify these by value, or by using the constant&#39;s
 * name. For example, <code>'OK'</code> or
 * <code>google.maps.ElevationStatus.OK</code>.
 * @enum {string}
 */
google.maps.ElevationStatus = {
  /**
   * The request was invalid.
   */
  INVALID_REQUEST: 'INVALID_REQUEST',
  /**
   * The request did not encounter any errors.
   */
  OK: 'OK',
  /**
   * The webpage has gone over the requests limit in too short a period of time.
   */
  OVER_QUERY_LIMIT: 'OVER_QUERY_LIMIT',
  /**
   * The webpage is not allowed to use the elevation service.
   */
  REQUEST_DENIED: 'REQUEST_DENIED',
  /**
   * The elevation request could not be successfully processed, yet the exact
   * reason for the failure is not known.
   */
  UNKNOWN_ERROR: 'UNKNOWN_ERROR',
};

/**
 * An event with an associated Error.
 * @record
 */
google.maps.ErrorEvent = function() {};

/**
 * The Error related to the event.
 * @type {!Error}
 */
google.maps.ErrorEvent.prototype.error;

/**
 * Options for the rendering of the fullscreen control.
 * @record
 */
google.maps.FullscreenControlOptions = function() {};

/**
 * Position id. Used to specify the position of the control on the map. The
 * default position is <code>RIGHT_TOP</code>.
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.FullscreenControlOptions.prototype.position;

/**
 * A service for converting between an address and a <code>LatLng</code>.
 * @constructor
 */
google.maps.Geocoder = function() {};

/**
 * Geocode a request. <aside class="note">Note: <strong>A Promise is only
 * returned for the beta release, <code>v=beta</code></strong>. More information
 * is available in the <a
 * href="https://developers.google.com/maps/documentation/javascript/promises">Promises
 * guide</a>.</aside>
 * @param {!google.maps.GeocoderRequest} request
 * @param {?(function(?Array<!google.maps.GeocoderResult>,
 *     !google.maps.GeocoderStatus): void)} callback
 * @return {?Promise<!google.maps.GeocoderResponse>}
 */
google.maps.Geocoder.prototype.geocode = function(request, callback) {};

/**
 * A single address component within a <code>GeocoderResult</code>. A full
 * address may consist of multiple address components.
 * @record
 */
google.maps.GeocoderAddressComponent = function() {};

/**
 * The full text of the address component
 * @type {string}
 */
google.maps.GeocoderAddressComponent.prototype.long_name;

/**
 * The abbreviated, short text of the given address component
 * @type {string}
 */
google.maps.GeocoderAddressComponent.prototype.short_name;

/**
 * An array of strings denoting the type of this address component. A list of
 * valid types can be found <a
 * href="https://developers.google.com/maps/documentation/javascript/geocoding#GeocodingAddressTypes">here</a>
 * @type {!Array<string>}
 */
google.maps.GeocoderAddressComponent.prototype.types;

/**
 * <code>GeocoderComponentRestrictions</code> represents a set of filters that
 * resolve to a specific area. For details on how this works, see <a
 * href="https://developers.google.com/maps/documentation/javascript/geocoding#ComponentFiltering">
 * Geocoding Component Filtering</a>.
 * @record
 */
google.maps.GeocoderComponentRestrictions = function() {};

/**
 * Matches all the <code>administrative_area levels</code>. Optional.
 * @type {string|undefined}
 */
google.maps.GeocoderComponentRestrictions.prototype.administrativeArea;

/**
 * Matches a country name or a two letter ISO 3166-1 country code. Optional.
 * @type {string|undefined}
 */
google.maps.GeocoderComponentRestrictions.prototype.country;

/**
 * Matches against both <code>locality</code> and <code>sublocality</code>
 * types. Optional.
 * @type {string|undefined}
 */
google.maps.GeocoderComponentRestrictions.prototype.locality;

/**
 * Matches <code>postal_code</code> and <code>postal_code_prefix</code>.
 * Optional.
 * @type {string|undefined}
 */
google.maps.GeocoderComponentRestrictions.prototype.postalCode;

/**
 * Matches the long or short name of a <code>route</code>. Optional.
 * @type {string|undefined}
 */
google.maps.GeocoderComponentRestrictions.prototype.route;

/**
 * Geometry information about this <code>GeocoderResult</code>
 * @record
 */
google.maps.GeocoderGeometry = function() {};

/**
 * The precise bounds of this <code>GeocoderResult</code>, if applicable
 * @type {!google.maps.LatLngBounds|undefined}
 */
google.maps.GeocoderGeometry.prototype.bounds;

/**
 * The latitude/longitude coordinates of this result
 * @type {!google.maps.LatLng}
 */
google.maps.GeocoderGeometry.prototype.location;

/**
 * The type of location returned in <code>location</code>
 * @type {!google.maps.GeocoderLocationType}
 */
google.maps.GeocoderGeometry.prototype.location_type;

/**
 * The bounds of the recommended viewport for displaying this
 * <code>GeocoderResult</code>
 * @type {!google.maps.LatLngBounds}
 */
google.maps.GeocoderGeometry.prototype.viewport;

/**
 * Describes the type of location returned from a geocode. Specify these by
 * value, or by using the constant&#39;s name. For example,
 * <code>'ROOFTOP'</code> or
 * <code>google.maps.GeocoderLocationType.ROOFTOP</code>.
 * @enum {string}
 */
google.maps.GeocoderLocationType = {
  /**
   * The returned result is approximate.
   */
  APPROXIMATE: 'APPROXIMATE',
  /**
   * The returned result is the geometric center of a result such a line (e.g.
   * street) or polygon (region).
   */
  GEOMETRIC_CENTER: 'GEOMETRIC_CENTER',
  /**
   * The returned result reflects an approximation (usually on a road)
   * interpolated between two precise points (such as intersections).
   * Interpolated results are generally returned when rooftop geocodes are
   * unavailable for a street address.
   */
  RANGE_INTERPOLATED: 'RANGE_INTERPOLATED',
  /**
   * The returned result reflects a precise geocode.
   */
  ROOFTOP: 'ROOFTOP',
};

/**
 * The specification for a geocoding request to be sent to the
 * <code>Geocoder</code>.
 * @record
 */
google.maps.GeocoderRequest = function() {};

/**
 * Address to geocode. One, and only one, of <code>address</code>,
 * <code>location</code> and <code>placeId</code> must be supplied.
 * @type {?string|undefined}
 */
google.maps.GeocoderRequest.prototype.address;

/**
 * <code>LatLngBounds</code> within which to search. Optional.
 * @type {?google.maps.LatLngBounds|?google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.GeocoderRequest.prototype.bounds;

/**
 * Components are used to restrict results to a specific area. A filter consists
 * of one or more of: <code>route</code>, <code>locality</code>,
 * <code>administrativeArea</code>, <code>postalCode</code>,
 * <code>country</code>. Only the results that match all the filters will be
 * returned. Filter values support the same methods of spelling correction and
 * partial matching as other geocoding requests. Optional.
 * @type {?google.maps.GeocoderComponentRestrictions|undefined}
 */
google.maps.GeocoderRequest.prototype.componentRestrictions;

/**
 * <code>LatLng</code> (or <code>LatLngLiteral</code>) for which to search. The
 * geocoder performs a reverse geocode. See <a
 * href="https://developers.google.com/maps/documentation/javascript/geocoding#ReverseGeocoding">
 * Reverse Geocoding</a> for more information. One, and only one, of
 * <code>address</code>, <code>location</code> and <code>placeId</code> must be
 * supplied.
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.GeocoderRequest.prototype.location;

/**
 * The place ID associated with the location. Place IDs uniquely identify a
 * place in the Google Places database and on Google Maps. Learn more about <a
 * href="https://developers.google.com/maps/documentation/places/web-service/place-id">place
 * IDs</a> in the Places API developer guide. The geocoder performs a reverse
 * geocode. See <a
 * href="https://developers.google.com/maps/documentation/javascript/geocoding#ReverseGeocoding">Reverse
 * Geocoding</a> for more information. One, and only one, of
 * <code>address</code>, <code>location</code> and <code>placeId</code> must be
 * supplied.
 * @type {?string|undefined}
 */
google.maps.GeocoderRequest.prototype.placeId;

/**
 * Country code used to bias the search, specified as a Unicode region subtag /
 * CLDR identifier. Optional.
 * @type {?string|undefined}
 */
google.maps.GeocoderRequest.prototype.region;

/**
 * An Geocoder response returned by the {@link google.maps.Geocoder} containing
 * the list of {@link google.maps.GeocoderResult}s.
 * @record
 */
google.maps.GeocoderResponse = function() {};

/**
 * The list of {@link google.maps.GeocoderResult}s.
 * @type {!Array<!google.maps.GeocoderResult>}
 */
google.maps.GeocoderResponse.prototype.results;

/**
 * A single geocoder result retrieved from the geocode server. A geocode request
 * may return multiple result objects. Note that though this result is
 * &quot;JSON-like,&quot; it is not strictly JSON, as it indirectly includes a
 * <code>LatLng</code> object.
 * @record
 */
google.maps.GeocoderResult = function() {};

/**
 * An array of <code>GeocoderAddressComponent</code>s
 * @type {!Array<!google.maps.GeocoderAddressComponent>}
 */
google.maps.GeocoderResult.prototype.address_components;

/**
 * A string containing the human-readable address of this location.
 * @type {string}
 */
google.maps.GeocoderResult.prototype.formatted_address;

/**
 * A <code>GeocoderGeometry</code> object
 * @type {!google.maps.GeocoderGeometry}
 */
google.maps.GeocoderResult.prototype.geometry;

/**
 * Whether the geocoder did not return an exact match for the original request,
 * though it was able to match part of the requested address. If an exact match,
 * the value will be <code>undefined</code>.
 * @type {boolean|undefined}
 */
google.maps.GeocoderResult.prototype.partial_match;

/**
 * The place ID associated with the location. Place IDs uniquely identify a
 * place in the Google Places database and on Google Maps. Learn more about <a
 * href="https://developers.google.com/maps/documentation/places/web-service/place-id">Place
 * IDs</a> in the Places API developer guide.
 * @type {string}
 */
google.maps.GeocoderResult.prototype.place_id;

/**
 * An array of strings denoting all the localities contained in a postal code.
 * This is only present when the result is a postal code that contains multiple
 * localities. This array can contain up to 10 localities.
 * @type {!Array<string>|undefined}
 */
google.maps.GeocoderResult.prototype.postcode_localities;

/**
 * An array of strings denoting the type of the returned geocoded element. For a
 * list of possible strings, refer to the <a href=
 * "https://developers.google.com/maps/documentation/javascript/geocoding#GeocodingAddressTypes">
 * Address Component Types</a> section of the Developer&#39;s Guide.
 * @type {!Array<string>}
 */
google.maps.GeocoderResult.prototype.types;

/**
 * The status returned by the <code>Geocoder</code> on the completion of a call
 * to <code>geocode()</code>. Specify these by value, or by using the
 * constant&#39;s name. For example, <code>'OK'</code> or
 * <code>google.maps.GeocoderStatus.OK</code>.
 * @enum {string}
 */
google.maps.GeocoderStatus = {
  /**
   * There was a problem contacting the Google servers.
   */
  ERROR: 'ERROR',
  /**
   * This <code>GeocoderRequest</code> was invalid.
   */
  INVALID_REQUEST: 'INVALID_REQUEST',
  /**
   * The response contains a valid <code>GeocoderResponse</code>.
   */
  OK: 'OK',
  /**
   * The webpage has gone over the requests limit in too short a period of time.
   */
  OVER_QUERY_LIMIT: 'OVER_QUERY_LIMIT',
  /**
   * The webpage is not allowed to use the geocoder.
   */
  REQUEST_DENIED: 'REQUEST_DENIED',
  /**
   * A geocoding request could not be processed due to a server error. The
   * request may succeed if you try again.
   */
  UNKNOWN_ERROR: 'UNKNOWN_ERROR',
  /**
   * No result was found for this <code>GeocoderRequest</code>.
   */
  ZERO_RESULTS: 'ZERO_RESULTS',
};

/**
 * A rectangular image overlay on the map.
 * @param {string} url
 * @param {google.maps.LatLngBounds|google.maps.LatLngBoundsLiteral} bounds
 * @param {google.maps.GroundOverlayOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.GroundOverlay = function(url, bounds, opts) {};

/**
 * Gets the <code>LatLngBounds</code> of this overlay.
 * @return {google.maps.LatLngBounds}
 */
google.maps.GroundOverlay.prototype.getBounds = function() {};

/**
 * Returns the map on which this ground overlay is displayed.
 * @return {google.maps.Map}
 */
google.maps.GroundOverlay.prototype.getMap = function() {};

/**
 * Returns the opacity of this ground overlay.
 * @return {number}
 */
google.maps.GroundOverlay.prototype.getOpacity = function() {};

/**
 * Gets the url of the projected image.
 * @return {string}
 */
google.maps.GroundOverlay.prototype.getUrl = function() {};

/**
 * Renders the ground overlay on the specified map. If map is set to
 * <code>null</code>, the overlay is removed.
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.GroundOverlay.prototype.setMap = function(map) {};

/**
 * Sets the opacity of this ground overlay.
 * @param {number} opacity
 * @return {undefined}
 */
google.maps.GroundOverlay.prototype.setOpacity = function(opacity) {};

/**
 * This object defines the properties that can be set on a
 * <code>GroundOverlay</code> object.
 * @record
 */
google.maps.GroundOverlayOptions = function() {};

/**
 * If <code>true</code>, the ground overlay can receive mouse events.
 * @type {?boolean|undefined}
 */
google.maps.GroundOverlayOptions.prototype.clickable;

/**
 * The map on which to display the overlay.
 * @type {?google.maps.Map|undefined}
 */
google.maps.GroundOverlayOptions.prototype.map;

/**
 * The opacity of the overlay, expressed as a number between 0 and 1. Optional.
 * Defaults to 1.
 * @type {?number|undefined}
 */
google.maps.GroundOverlayOptions.prototype.opacity;

/**
 * A structure representing a Marker icon image.
 * @record
 */
google.maps.Icon = function() {};

/**
 * The position at which to anchor an image in correspondence to the location of
 * the marker on the map. By default, the anchor is located along the center
 * point of the bottom of the image.
 * @type {?google.maps.Point|undefined}
 */
google.maps.Icon.prototype.anchor;

/**
 * The origin of the label relative to the top-left corner of the icon image, if
 * a label is supplied by the marker. By default, the origin is located in the
 * center point of the image.
 * @type {?google.maps.Point|undefined}
 */
google.maps.Icon.prototype.labelOrigin;

/**
 * The position of the image within a sprite, if any. By default, the origin is
 * located at the top left corner of the image <code>(0, 0)</code>.
 * @type {?google.maps.Point|undefined}
 */
google.maps.Icon.prototype.origin;

/**
 * The size of the entire image after scaling, if any. Use this property to
 * stretch/shrink an image or a sprite.
 * @type {?google.maps.Size|undefined}
 */
google.maps.Icon.prototype.scaledSize;

/**
 * The display size of the sprite or image. When using sprites, you must specify
 * the sprite size. If the size is not provided, it will be set when the image
 * loads.
 * @type {?google.maps.Size|undefined}
 */
google.maps.Icon.prototype.size;

/**
 * The URL of the image or sprite sheet.
 * @type {string}
 */
google.maps.Icon.prototype.url;

/**
 * This object is sent in an event when a user clicks on an icon on the map. The
 * place ID of this place is stored in the placeId member. To prevent the
 * default info window from showing up, call the stop() method on this event to
 * prevent it being propagated. Learn more about <a
 * href="https://developers.google.com/maps/documentation/places/web-service/place-id">place
 * IDs</a> in the Places API developer guide.
 * @extends {google.maps.MapMouseEvent}
 * @record
 */
google.maps.IconMouseEvent = function() {};

/**
 * The place ID of the place that was clicked. This place ID can be used to
 * query more information about the feature that was clicked. <p> Learn more
 * about <a
 * href="https://developers.google.com/maps/documentation/places/web-service/place-id">place
 * IDs</a> in the Places API developer guide.
 * @type {?string}
 */
google.maps.IconMouseEvent.prototype.placeId;

/**
 * Describes how icons are to be rendered on a line. <br><br> If your polyline
 * is geodesic, then the distances specified for both offset and repeat are
 * calculated in meters by default. Setting either offset or repeat to a pixel
 * value will cause the distances to be calculated in pixels on the screen.
 * @record
 */
google.maps.IconSequence = function() {};

/**
 * If <code>true</code>, each icon in the sequence has the same fixed rotation
 * regardless of the angle of the edge on which it lies. Defaults to
 * <code>false</code>, in which case each icon in the sequence is rotated to
 * align with its edge.
 * @type {boolean|undefined}
 */
google.maps.IconSequence.prototype.fixedRotation;

/**
 * The icon to render on the line.
 * @type {?google.maps.Symbol|undefined}
 */
google.maps.IconSequence.prototype.icon;

/**
 * The distance from the start of the line at which an icon is to be rendered.
 * This distance may be expressed as a percentage of line&#39;s length (e.g.
 * &#39;50%&#39;) or in pixels (e.g. &#39;50px&#39;). Defaults to
 * &#39;100%&#39;.
 * @type {string|undefined}
 */
google.maps.IconSequence.prototype.offset;

/**
 * The distance between consecutive icons on the line. This distance may be
 * expressed as a percentage of the line&#39;s length (e.g. &#39;50%&#39;) or in
 * pixels (e.g. &#39;50px&#39;). To disable repeating of the icon, specify
 * &#39;0&#39;. Defaults to &#39;0&#39;.
 * @type {string|undefined}
 */
google.maps.IconSequence.prototype.repeat;

/**
 * This class implements the MapType interface and is provided for rendering
 * image tiles.
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
 * Returns the opacity level (<code>0</code> (transparent) to <code>1.0</code>)
 * of the <code>ImageMapType</code> tiles.
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
google.maps.ImageMapType.prototype.getTile = function(
    tileCoord, zoom, ownerDocument) {};

/**
 * @param {Node} tileDiv
 * @return {undefined}
 * @override
 */
google.maps.ImageMapType.prototype.releaseTile = function(tileDiv) {};

/**
 * Sets the opacity level (<code>0</code> (transparent) to <code>1.0</code>) of
 * the <code>ImageMapType</code> tiles.
 * @param {number} opacity
 * @return {undefined}
 */
google.maps.ImageMapType.prototype.setOpacity = function(opacity) {};

/**
 * This class is used to create a MapType that renders image tiles.
 * @record
 */
google.maps.ImageMapTypeOptions = function() {};

/**
 * Alt text to display when this MapType&#39;s button is hovered over in the
 * MapTypeControl.
 * @type {?string|undefined}
 */
google.maps.ImageMapTypeOptions.prototype.alt;

/**
 * Returns a string (URL) for given tile coordinate (x, y) and zoom level.
 * @type {?(function(!google.maps.Point, number): ?string)|undefined}
 */
google.maps.ImageMapTypeOptions.prototype.getTileUrl;

/**
 * The maximum zoom level for the map when displaying this MapType.
 * @type {?number|undefined}
 */
google.maps.ImageMapTypeOptions.prototype.maxZoom;

/**
 * The minimum zoom level for the map when displaying this MapType. Optional.
 * @type {?number|undefined}
 */
google.maps.ImageMapTypeOptions.prototype.minZoom;

/**
 * Name to display in the MapTypeControl.
 * @type {?string|undefined}
 */
google.maps.ImageMapTypeOptions.prototype.name;

/**
 * The opacity to apply to the tiles. The opacity should be specified as a float
 * value between 0 and 1.0, where 0 is fully transparent and 1 is fully opaque.
 * @type {?number|undefined}
 */
google.maps.ImageMapTypeOptions.prototype.opacity;

/**
 * The tile size.
 * @type {?google.maps.Size|undefined}
 */
google.maps.ImageMapTypeOptions.prototype.tileSize;

/**
 * An overlay that looks like a bubble and is often connected to a marker.
 * @param {google.maps.InfoWindowOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.InfoWindow = function(opts) {};

/**
 * Closes this InfoWindow by removing it from the DOM structure.
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
 * Opens this InfoWindow on the given map. Optionally, an InfoWindow can be
 * associated with an anchor. In the core API, the only anchor is the Marker
 * class. However, an anchor can be any MVCObject that exposes a LatLng
 * <code>position</code> property and optionally a Point
 * <code>anchorPoint</code> property for calculating the
 * <code>pixelOffset</code> (see InfoWindowOptions). The
 * <code>anchorPoint</code> is the offset from the anchor&#39;s position to the
 * tip of the InfoWindow.
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
 * InfoWindowOptions object used to define the properties that can be set on a
 * InfoWindow.
 * @record
 */
google.maps.InfoWindowOptions = function() {};

/**
 * Content to display in the InfoWindow. This can be an HTML element, a
 * plain-text string, or a string containing HTML. The InfoWindow will be sized
 * according to the content. To set an explicit size for the content, set
 * content to be a HTML element with that size.
 * @type {?string|?Node|undefined}
 */
google.maps.InfoWindowOptions.prototype.content;

/**
 * Disable auto-pan on open. By default, the InfoWindow will pan the map so that
 * it is fully visible when it opens.
 * @type {?boolean|undefined}
 */
google.maps.InfoWindowOptions.prototype.disableAutoPan;

/**
 * Maximum width of the InfoWindow, regardless of content&#39;s width. This
 * value is only considered if it is set before a call to <code>open()</code>.
 * To change the maximum width when changing content, call <code>close()</code>,
 * <code>setOptions()</code>, and then <code>open()</code>.
 * @type {?number|undefined}
 */
google.maps.InfoWindowOptions.prototype.maxWidth;

/**
 * Minimum width of the InfoWindow, regardless of the content&#39;s width. When
 * using this property, it is strongly recommended to set the
 * <code>minWidth</code> to a value less than the width of the map (in pixels).
 * This value is only considered if it is set before a call to
 * <code>open()</code>. To change the minimum width when changing content, call
 * <code>close()</code>, <code>setOptions()</code>, and then
 * <code>open()</code>.
 * @type {?number|undefined}
 */
google.maps.InfoWindowOptions.prototype.minWidth;

/**
 * The offset, in pixels, of the tip of the info window from the point on the
 * map at whose geographical coordinates the info window is anchored. If an
 * InfoWindow is opened with an anchor, the <code>pixelOffset</code> will be
 * calculated from the anchor&#39;s <code>anchorPoint</code> property.
 * @type {?google.maps.Size|undefined}
 */
google.maps.InfoWindowOptions.prototype.pixelOffset;

/**
 * The LatLng at which to display this InfoWindow. If the InfoWindow is opened
 * with an anchor, the anchor&#39;s position will be used instead.
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.InfoWindowOptions.prototype.position;

/**
 * All InfoWindows are displayed on the map in order of their zIndex, with
 * higher values displaying in front of InfoWindows with lower values. By
 * default, InfoWindows are displayed according to their latitude, with
 * InfoWindows of lower latitudes appearing in front of InfoWindows at higher
 * latitudes. InfoWindows are always displayed in front of markers.
 * @type {?number|undefined}
 */
google.maps.InfoWindowOptions.prototype.zIndex;

/**
 * Contains details of the author of a KML document or feature.
 * @record
 */
google.maps.KmlAuthor = function() {};

/**
 * The author&#39;s e-mail address, or an empty string if not specified.
 * @type {string}
 */
google.maps.KmlAuthor.prototype.email;

/**
 * The author&#39;s name, or an empty string if not specified.
 * @type {string}
 */
google.maps.KmlAuthor.prototype.name;

/**
 * The author&#39;s home page, or an empty string if not specified.
 * @type {string}
 */
google.maps.KmlAuthor.prototype.uri;

/**
 * Data for a single KML feature in JSON format, returned when a KML feature is
 * clicked. The data contained in this object mirrors that associated with the
 * feature in the KML or GeoRSS markup in which it is declared.
 * @record
 */
google.maps.KmlFeatureData = function() {};

/**
 * The feature&#39;s <code>&lt;atom:author&gt;</code>, extracted from the layer
 * markup (if specified).
 * @type {google.maps.KmlAuthor}
 */
google.maps.KmlFeatureData.prototype.author;

/**
 * The feature&#39;s <code>&lt;description&gt;</code>, extracted from the layer
 * markup.
 * @type {string}
 */
google.maps.KmlFeatureData.prototype.description;

/**
 * The feature&#39;s <code>&lt;id&gt;</code>, extracted from the layer markup.
 * If no <code>&lt;id&gt;</code> has been specified, a unique ID will be
 * generated for this feature.
 * @type {string}
 */
google.maps.KmlFeatureData.prototype.id;

/**
 * The feature&#39;s balloon styled text, if set.
 * @type {string}
 */
google.maps.KmlFeatureData.prototype.infoWindowHtml;

/**
 * The feature&#39;s <code>&lt;name&gt;</code>, extracted from the layer markup.
 * @type {string}
 */
google.maps.KmlFeatureData.prototype.name;

/**
 * The feature&#39;s <code>&lt;Snippet&gt;</code>, extracted from the layer
 * markup.
 * @type {string}
 */
google.maps.KmlFeatureData.prototype.snippet;

/**
 * A <code>KmlLayer</code> adds geographic markup to the map from a KML, KMZ or
 * GeoRSS file that is hosted on a publicly accessible web server. A
 * <code>KmlFeatureData</code> object is provided for each feature when clicked.
 * @param {google.maps.KmlLayerOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.KmlLayer = function(opts) {};

/**
 * Get the default viewport for the layer being displayed.
 * @return {google.maps.LatLngBounds}
 */
google.maps.KmlLayer.prototype.getDefaultViewport = function() {};

/**
 * Get the map on which the KML Layer is being rendered.
 * @return {google.maps.Map}
 */
google.maps.KmlLayer.prototype.getMap = function() {};

/**
 * Get the metadata associated with this layer, as specified in the layer
 * markup.
 * @return {google.maps.KmlLayerMetadata}
 */
google.maps.KmlLayer.prototype.getMetadata = function() {};

/**
 * Get the status of the layer, set once the requested document has loaded.
 * @return {google.maps.KmlLayerStatus}
 */
google.maps.KmlLayer.prototype.getStatus = function() {};

/**
 * Gets the URL of the KML file being displayed.
 * @return {string}
 */
google.maps.KmlLayer.prototype.getUrl = function() {};

/**
 * Gets the z-index of the KML Layer.
 * @return {number}
 */
google.maps.KmlLayer.prototype.getZIndex = function() {};

/**
 * Renders the KML Layer on the specified map. If map is set to
 * <code>null</code>, the layer is removed.
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
 * Sets the URL of the KML file to display.
 * @param {string} url
 * @return {undefined}
 */
google.maps.KmlLayer.prototype.setUrl = function(url) {};

/**
 * Sets the z-index of the KML Layer.
 * @param {number} zIndex
 * @return {undefined}
 */
google.maps.KmlLayer.prototype.setZIndex = function(zIndex) {};

/**
 * Metadata for a single KML layer, in JSON format.
 * @record
 */
google.maps.KmlLayerMetadata = function() {};

/**
 * The layer&#39;s <code>&lt;atom:author&gt;</code>, extracted from the layer
 * markup.
 * @type {google.maps.KmlAuthor}
 */
google.maps.KmlLayerMetadata.prototype.author;

/**
 * The layer&#39;s <code>&lt;description&gt;</code>, extracted from the layer
 * markup.
 * @type {string}
 */
google.maps.KmlLayerMetadata.prototype.description;

/**
 * Whether the layer has any screen overlays.
 * @type {boolean}
 */
google.maps.KmlLayerMetadata.prototype.hasScreenOverlays;

/**
 * The layer&#39;s <code>&lt;name&gt;</code>, extracted from the layer markup.
 * @type {string}
 */
google.maps.KmlLayerMetadata.prototype.name;

/**
 * The layer&#39;s <code>&lt;Snippet&gt;</code>, extracted from the layer markup
 * @type {string}
 */
google.maps.KmlLayerMetadata.prototype.snippet;

/**
 * This object defines the properties that can be set on a <code>KmlLayer</code>
 * object.
 * @record
 */
google.maps.KmlLayerOptions = function() {};

/**
 * If <code>true</code>, the layer receives mouse events. Default value is
 * <code>true</code>.
 * @type {?boolean|undefined}
 */
google.maps.KmlLayerOptions.prototype.clickable;

/**
 * The map on which to display the layer.
 * @type {?google.maps.Map|undefined}
 */
google.maps.KmlLayerOptions.prototype.map;

/**
 * By default, the input map is centered and zoomed to the bounding box of the
 * contents of the layer. If this option is set to <code>true</code>, the
 * viewport is left unchanged, unless the map&#39;s center and zoom were never
 * set.
 * @type {?boolean|undefined}
 */
google.maps.KmlLayerOptions.prototype.preserveViewport;

/**
 * Whether to render the screen overlays. Default <code>true</code>.
 * @type {?boolean|undefined}
 */
google.maps.KmlLayerOptions.prototype.screenOverlays;

/**
 * Suppress the rendering of info windows when layer features are clicked.
 * @type {?boolean|undefined}
 */
google.maps.KmlLayerOptions.prototype.suppressInfoWindows;

/**
 * The URL of the KML document to display.
 * @type {?string|undefined}
 */
google.maps.KmlLayerOptions.prototype.url;

/**
 * The z-index of the layer.
 * @type {?number|undefined}
 */
google.maps.KmlLayerOptions.prototype.zIndex;

/**
 * The status returned by <code>KmlLayer</code> on the completion of loading a
 * document. Specify these by value, or by using the constant&#39;s name. For
 * example, <code>'OK'</code> or <code>google.maps.KmlLayerStatus.OK</code>.
 * @enum {string}
 */
google.maps.KmlLayerStatus = {
  /**
   * The document could not be found. Most likely it is an invalid URL, or the
   * document is not publicly available.
   */
  DOCUMENT_NOT_FOUND: 'DOCUMENT_NOT_FOUND',
  /**
   * The document exceeds the file size limits of KmlLayer.
   */
  DOCUMENT_TOO_LARGE: 'DOCUMENT_TOO_LARGE',
  /**
   * The document could not be fetched.
   */
  FETCH_ERROR: 'FETCH_ERROR',
  /**
   * The document is not a valid KML, KMZ or GeoRSS document.
   */
  INVALID_DOCUMENT: 'INVALID_DOCUMENT',
  /**
   * The <code>KmlLayer</code> is invalid.
   */
  INVALID_REQUEST: 'INVALID_REQUEST',
  /**
   * The document exceeds the feature limits of KmlLayer.
   */
  LIMITS_EXCEEDED: 'LIMITS_EXCEEDED',
  /**
   * The layer loaded successfully.
   */
  OK: 'OK',
  /**
   * The document could not be loaded within a reasonable amount of time.
   */
  TIMED_OUT: 'TIMED_OUT',
  /**
   * The document failed to load for an unknown reason.
   */
  UNKNOWN: 'UNKNOWN',
};

/**
 * The properties of a click event on a KML/KMZ or GeoRSS document.
 * @record
 */
google.maps.KmlMouseEvent = function() {};

/**
 * A <code>KmlFeatureData</code> object, containing information about the
 * clicked feature.
 * @type {google.maps.KmlFeatureData}
 */
google.maps.KmlMouseEvent.prototype.featureData;

/**
 * The position at which to anchor an infowindow on the clicked feature.
 * @type {google.maps.LatLng}
 */
google.maps.KmlMouseEvent.prototype.latLng;

/**
 * The offset to apply to an infowindow anchored on the clicked feature.
 * @type {google.maps.Size}
 */
google.maps.KmlMouseEvent.prototype.pixelOffset;

/**
 * A <code>LatLng</code> is a point in geographical coordinates: latitude and
 * longitude.<br> <ul> <li>Latitude ranges between -90 and 90 degrees,
 * inclusive. Values above or below this range will be clamped to the range
 * [-90, 90]. This means that if the value specified is less than -90, it will
 * be set to -90. And if the value is greater than 90, it will be set
 * to 90.</li> <li>Longitude ranges between -180 and 180 degrees, inclusive.
 * Values above or below this range will be wrapped so that they fall within the
 * range. For example, a value of -190 will be converted to 170. A value of 190
 * will be converted to -170. This reflects the fact that longitudes wrap around
 * the globe.</li> </ul> Although the default map projection associates
 * longitude with the x-coordinate of the map, and latitude with the
 * y-coordinate, the latitude coordinate is always written <em>first</em>,
 * followed by the longitude.<br> Notice that you cannot modify the coordinates
 * of a <code>LatLng</code>. If you want to compute another point, you have to
 * create a new one.<br> <p> Most methods that accept <code>LatLng</code>
 * objects also accept a <code><a href="#LatLngLiteral">LatLngLiteral</a></code>
 * object, so that the following are equivalent: <pre> map.setCenter(new
 * google.maps.LatLng(-34, 151));<br> map.setCenter({lat: -34, lng: 151});
 * </pre> <p> The constructor also accepts literal objects, and converts them to
 * instances of <code>LatLng</code>. The possible calls to the constructor are
 * below: <pre> new google.maps.LatLng(-34, 151);<br> new
 * google.maps.LatLng(-34, 151, true);<br> new google.maps.LatLng({lat: -34,
 * lng: 151});<br> new google.maps.LatLng({lat: -34, lng: 151}, true);<br> new
 * google.maps.LatLng({lat: -34, lng: 151}, null, true); </pre>
 * @param {number|!google.maps.LatLngLiteral} latOrLatLngLiteral
 * @param {?(number|boolean)=} lngOrNoWrap
 * @param {boolean=} noWrap
 * @constructor
 */
google.maps.LatLng = function(latOrLatLngLiteral, lngOrNoWrap, noWrap) {};

/**
 * Comparison function.
 * @param {google.maps.LatLng} other
 * @return {boolean}
 */
google.maps.LatLng.prototype.equals = function(other) {};

/**
 * Returns the latitude in degrees.
 * @return {number}
 */
google.maps.LatLng.prototype.lat = function() {};

/**
 * Returns the longitude in degrees.
 * @return {number}
 */
google.maps.LatLng.prototype.lng = function() {};

/**
 * Converts to JSON representation. This function is intended to be used via
 * <code>JSON.stringify</code>.
 * @return {!google.maps.LatLngLiteral}
 * @override
 */
google.maps.LatLng.prototype.toJSON = function() {};

/**
 * Converts to string representation.
 * @return {string}
 * @override
 */
google.maps.LatLng.prototype.toString = function() {};

/**
 * Returns a string of the form &quot;lat,lng&quot; for this LatLng. We round
 * the lat/lng values to 6 decimal places by default.
 * @param {number=} precision
 * @return {string}
 */
google.maps.LatLng.prototype.toUrlValue = function(precision) {};

/**
 * A <code><a href="#LatLngBounds">LatLngBounds</a></code> instance represents a
 * rectangle in geographical coordinates, including one that crosses the 180
 * degrees longitudinal meridian.
 * @param {(?google.maps.LatLng|?google.maps.LatLngLiteral)=} sw
 * @param {(?google.maps.LatLng|?google.maps.LatLngLiteral)=} ne
 * @constructor
 */
google.maps.LatLngBounds = function(sw, ne) {};

/**
 * Returns <code>true</code> if the given lat/lng is in this bounds.
 * @param {!google.maps.LatLng|!google.maps.LatLngLiteral} latLng
 * @return {boolean}
 */
google.maps.LatLngBounds.prototype.contains = function(latLng) {};

/**
 * Returns <code>true</code> if this bounds approximately equals the given
 * bounds.
 * @param {?google.maps.LatLngBounds|?google.maps.LatLngBoundsLiteral} other
 * @return {boolean}
 */
google.maps.LatLngBounds.prototype.equals = function(other) {};

/**
 * Extends this bounds to contain the given point.
 * @param {!google.maps.LatLng|!google.maps.LatLngLiteral} point
 * @return {!google.maps.LatLngBounds}
 */
google.maps.LatLngBounds.prototype.extend = function(point) {};

/**
 * Computes the center of this LatLngBounds
 * @return {!google.maps.LatLng}
 */
google.maps.LatLngBounds.prototype.getCenter = function() {};

/**
 * Returns the north-east corner of this bounds.
 * @return {!google.maps.LatLng}
 */
google.maps.LatLngBounds.prototype.getNorthEast = function() {};

/**
 * Returns the south-west corner of this bounds.
 * @return {!google.maps.LatLng}
 */
google.maps.LatLngBounds.prototype.getSouthWest = function() {};

/**
 * Returns <code>true</code> if this bounds shares any points with the other
 * bounds.
 * @param {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral} other
 * @return {boolean}
 */
google.maps.LatLngBounds.prototype.intersects = function(other) {};

/**
 * Returns if the bounds are empty.
 * @return {boolean}
 */
google.maps.LatLngBounds.prototype.isEmpty = function() {};

/**
 * Converts to JSON representation. This function is intended to be used via
 * <code>JSON.stringify</code>.
 * @return {!google.maps.LatLngBoundsLiteral}
 * @override
 */
google.maps.LatLngBounds.prototype.toJSON = function() {};

/**
 * Converts the given map bounds to a lat/lng span.
 * @return {!google.maps.LatLng}
 */
google.maps.LatLngBounds.prototype.toSpan = function() {};

/**
 * Converts to string.
 * @return {string}
 * @override
 */
google.maps.LatLngBounds.prototype.toString = function() {};

/**
 * Returns a string of the form &quot;lat_lo,lng_lo,lat_hi,lng_hi&quot; for this
 * bounds, where &quot;lo&quot; corresponds to the southwest corner of the
 * bounding box, while &quot;hi&quot; corresponds to the northeast corner of
 * that box.
 * @param {number=} precision
 * @return {string}
 */
google.maps.LatLngBounds.prototype.toUrlValue = function(precision) {};

/**
 * Extends this bounds to contain the union of this and the given bounds.
 * @param {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral} other
 * @return {!google.maps.LatLngBounds}
 */
google.maps.LatLngBounds.prototype.union = function(other) {};

/**
 * Object literals are accepted in place of <code>LatLngBounds</code> objects
 * throughout the API. These are automatically converted to
 * <code>LatLngBounds</code> objects. All <code>south</code>, <code>west</code>,
 * <code>north</code> and <code>east</code> must be set, otherwise an exception
 * is thrown.
 * @record
 */
google.maps.LatLngBoundsLiteral = function() {};

/**
 * East longitude in degrees. Values outside the range [-180, 180] will be
 * wrapped to the range [-180, 180). For example, a value of -190 will be
 * converted to 170. A value of 190 will be converted to -170. This reflects the
 * fact that longitudes wrap around the globe.
 * @type {number}
 */
google.maps.LatLngBoundsLiteral.prototype.east;

/**
 * North latitude in degrees. Values will be clamped to the range [-90, 90].
 * This means that if the value specified is less than -90, it will be set to
 * -90. And if the value is greater than 90, it will be set to 90.
 * @type {number}
 */
google.maps.LatLngBoundsLiteral.prototype.north;

/**
 * South latitude in degrees. Values will be clamped to the range [-90, 90].
 * This means that if the value specified is less than -90, it will be set to
 * -90. And if the value is greater than 90, it will be set to 90.
 * @type {number}
 */
google.maps.LatLngBoundsLiteral.prototype.south;

/**
 * West longitude in degrees. Values outside the range [-180, 180] will be
 * wrapped to the range [-180, 180). For example, a value of -190 will be
 * converted to 170. A value of 190 will be converted to -170. This reflects the
 * fact that longitudes wrap around the globe.
 * @type {number}
 */
google.maps.LatLngBoundsLiteral.prototype.west;

/**
 * Object literals are accepted in place of <code>LatLng</code> objects, as a
 * convenience, in many places. These are converted to <code>LatLng</code>
 * objects when the Maps API encounters them. <p> Examples: <pre>
 * map.setCenter({lat: -34, lng: 151});<br> new
 * google.maps.Marker({position: {lat: -34, lng: 151}, map: map}); </pre> <p
 * class="note">LatLng object literals are not supported in the Geometry
 * library.</p>
 * @record
 */
google.maps.LatLngLiteral = function() {};

/**
 * Latitude in degrees. Values will be clamped to the range [-90, 90]. This
 * means that if the value specified is less than -90, it will be set to -90.
 * And if the value is greater than 90, it will be set to 90.
 * @type {number}
 */
google.maps.LatLngLiteral.prototype.lat;

/**
 * Longitude in degrees. Values outside the range [-180, 180] will be wrapped so
 * that they fall within the range. For example, a value of -190 will be
 * converted to 170. A value of 190 will be converted to -170. This reflects the
 * fact that longitudes wrap around the globe.
 * @type {number}
 */
google.maps.LatLngLiteral.prototype.lng;

/**
 * An elevation request sent by the <code>ElevationService</code> containing the
 * list of discrete coordinates (<code>LatLng</code>s) for which to return
 * elevation data.
 * @record
 */
google.maps.LocationElevationRequest = function() {};

/**
 * The discrete locations for which to retrieve elevations.
 * @type {?Array<!google.maps.LatLng|!google.maps.LatLngLiteral>|undefined}
 */
google.maps.LocationElevationRequest.prototype.locations;

/**
 * An elevation response returned by the {@link google.maps.ElevationService}
 * containing the list of {@link google.maps.ElevationResult}s matching the
 * locations of the {@link google.maps.LocationElevationRequest}.
 * @record
 */
google.maps.LocationElevationResponse = function() {};

/**
 * The list of {@link google.maps.ElevationResult}s matching the locations of
 * the {@link google.maps.LocationElevationRequest}.
 * @type {!Array<!google.maps.ElevationResult>}
 */
google.maps.LocationElevationResponse.prototype.results;

/**
 * @param {Array<T>=} array
 * @extends {google.maps.MVCObject}
 * @template T
 * @constructor
 */
google.maps.MVCArray = function(array) {};

/**
 * Removes all elements from the array.
 * @return {undefined}
 */
google.maps.MVCArray.prototype.clear = function() {};

/**
 * Iterate over each element, calling the provided callback. The callback is
 * called for each element like: callback(element, index).
 * @param {function(T, number): void} callback
 * @return {undefined}
 */
google.maps.MVCArray.prototype.forEach = function(callback) {};

/**
 * Returns a reference to the underlying Array. Warning: if the Array is
 * mutated, no events will be fired by this object.
 * @return {!Array<T>}
 */
google.maps.MVCArray.prototype.getArray = function() {};

/**
 * Returns the element at the specified index.
 * @param {number} i
 * @return {T}
 */
google.maps.MVCArray.prototype.getAt = function(i) {};

/**
 * Returns the number of elements in this array.
 * @return {number}
 */
google.maps.MVCArray.prototype.getLength = function() {};

/**
 * Inserts an element at the specified index.
 * @param {number} i
 * @param {T} elem
 * @return {undefined}
 */
google.maps.MVCArray.prototype.insertAt = function(i, elem) {};

/**
 * Removes the last element of the array and returns that element.
 * @return {T}
 */
google.maps.MVCArray.prototype.pop = function() {};

/**
 * Adds one element to the end of the array and returns the new length of the
 * array.
 * @param {T} elem
 * @return {number}
 */
google.maps.MVCArray.prototype.push = function(elem) {};

/**
 * Removes an element from the specified index.
 * @param {number} i
 * @return {T}
 */
google.maps.MVCArray.prototype.removeAt = function(i) {};

/**
 * Sets an element at the specified index.
 * @param {number} i
 * @param {T} elem
 * @return {undefined}
 */
google.maps.MVCArray.prototype.setAt = function(i, elem) {};

/**
 * Base class implementing KVO. <br><br>The <code>MVCObject</code> constructor
 * is guaranteed to be an empty function, and so you may inherit from
 * <code>MVCObject</code> by writing <code>MySubclass.prototype = new
 * google.maps.MVCObject();</code>. Unless otherwise noted, this is not true of
 * other classes in the API, and inheriting from other classes in the API is not
 * supported.
 * @constructor
 */
google.maps.MVCObject = function() {};

/**
 * Adds the given listener function to the given event name. Returns an
 * identifier for this listener that can be used with
 * <code>google.maps.event.removeListener</code>.
 * @param {string} eventName
 * @param {!Function} handler
 * @return {!google.maps.MapsEventListener}
 */
google.maps.MVCObject.prototype.addListener = function(eventName, handler) {};

/**
 * Binds a View to a Model.
 * @param {string} key
 * @param {!google.maps.MVCObject} target
 * @param {?string=} targetKey
 * @param {boolean=} noNotify
 * @return {undefined}
 */
google.maps.MVCObject.prototype.bindTo = function(
    key, target, targetKey, noNotify) {};

/**
 * Gets a value.
 * @param {string} key
 * @return {?}
 */
google.maps.MVCObject.prototype.get = function(key) {};

/**
 * Notify all observers of a change on this property. This notifies both objects
 * that are bound to the object&#39;s property as well as the object that it is
 * bound to.
 * @param {string} key
 * @return {undefined}
 */
google.maps.MVCObject.prototype.notify = function(key) {};

/**
 * Sets a value.
 * @param {string} key
 * @param {*} value
 * @return {undefined}
 */
google.maps.MVCObject.prototype.set = function(key, value) {};

/**
 * Sets a collection of key-value pairs.
 * @param {?Object=} values
 * @return {undefined}
 */
google.maps.MVCObject.prototype.setValues = function(values) {};

/**
 * Removes a binding. Unbinding will set the unbound property to the current
 * value. The object will not be notified, as the value has not changed.
 * @param {string} key
 * @return {undefined}
 */
google.maps.MVCObject.prototype.unbind = function(key) {};

/**
 * Removes all bindings.
 * @return {undefined}
 */
google.maps.MVCObject.prototype.unbindAll = function() {};

/**
 * @param {!Element} mapDiv The map will render to fill this element.
 * @param {!google.maps.MapOptions=} opts Options
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.Map = function(mapDiv, opts) {};

/**
 * Additional controls to attach to the map. To add a control to the map, add
 * the control&#39;s <code>&lt;div&gt;</code> to the <code>MVCArray</code>
 * corresponding to the <code>ControlPosition</code> where it should be
 * rendered.
 * @type {!Array<!google.maps.MVCArray<!Node>>}
 */
google.maps.Map.prototype.controls;

/**
 * An instance of <code>Data</code>, bound to the map. Add features to this
 * <code>Data</code> object to conveniently display them on this map.
 * @type {!google.maps.Data}
 */
google.maps.Map.prototype.data;

/**
 * A registry of <code>MapType</code> instances by string ID.
 * @type {!google.maps.MapTypeRegistry}
 */
google.maps.Map.prototype.mapTypes;

/**
 * Additional map types to overlay. Overlay map types will display on top of the
 * base map they are attached to, in the order in which they appear in the
 * <code>overlayMapTypes</code> array (overlays with higher index values are
 * displayed in front of overlays with lower index values).
 * @type {!google.maps.MVCArray<?google.maps.MapType>}
 */
google.maps.Map.prototype.overlayMapTypes;

/**
 * Sets the viewport to contain the given bounds.</br> <strong>Note:</strong>
 * When the map is set to <code>display: none</code>, the <code>fitBounds</code>
 * function reads the map&#39;s size as 0x0, and therefore does not do anything.
 * To change the viewport while the map is hidden, set the map to
 * <code>visibility: hidden</code>, thereby ensuring the map div has an actual
 * size.
 * @param {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral} bounds
 *     Bounds to show.
 * @param {(number|!google.maps.Padding)=} padding Padding in pixels. The bounds
 *     will be fit in the part of the map that remains after padding is removed.
 *     A number value will yield the same padding on all 4 sides. Supply 0 here
 *     to make a fitBounds idempotent on the result of getBounds.
 * @return {undefined}
 */
google.maps.Map.prototype.fitBounds = function(bounds, padding) {};

/**
 * Returns the lat/lng bounds of the current viewport. If more than one copy of
 * the world is visible, the bounds range in longitude from -180 to 180 degrees
 * inclusive. If the map is not yet initialized or center and zoom have not been
 * set then the result is <code>undefined</code>.
 * @return {!google.maps.LatLngBounds|undefined}
 */
google.maps.Map.prototype.getBounds = function() {};

/**
 * Returns the position displayed at the center of the map. Note that this
 * <code>LatLng</code> object is <em>not</em> wrapped. See <code><a
 * href="#LatLng">LatLng</a></code> for more information. If the center or
 * bounds have not been set then the result is <code>undefined</code>.
 * @return {!google.maps.LatLng|undefined}
 */
google.maps.Map.prototype.getCenter = function() {};

/**
 * Returns the clickability of the map icons. A map icon represents a point of
 * interest, also known as a POI. If the returned value is <code>true</code>,
 * then the icons are clickable on the map.
 * @return {boolean|undefined}
 */
google.maps.Map.prototype.getClickableIcons = function() {};

/**
 * @return {!Element} The mapDiv of the map.
 */
google.maps.Map.prototype.getDiv = function() {};

/**
 * Returns the compass heading of aerial imagery. The heading value is measured
 * in degrees (clockwise) from cardinal direction North. If the map is not yet
 * initialized then the result is <code>undefined</code>.
 * @return {number|undefined}
 */
google.maps.Map.prototype.getHeading = function() {};

/**
 * @return {!google.maps.MapTypeId|string|undefined}
 */
google.maps.Map.prototype.getMapTypeId = function() {};

/**
 * Returns the current <code>Projection</code>. If the map is not yet
 * initialized then the result is <code>undefined</code>. Listen to the
 * <code>projection_changed</code> event and check its value to ensure it is not
 * <code>undefined</code>.
 * @return {!google.maps.Projection|undefined}
 */
google.maps.Map.prototype.getProjection = function() {};

/**
 * Returns the default <code>StreetViewPanorama</code> bound to the map, which
 * may be a default panorama embedded within the map, or the panorama set using
 * <code>setStreetView()</code>. Changes to the map&#39;s
 * <code>streetViewControl</code> will be reflected in the display of such a
 * bound panorama.
 * @return {!google.maps.StreetViewPanorama} The panorama bound to the map.
 */
google.maps.Map.prototype.getStreetView = function() {};

/**
 * Returns the current angle of incidence of the map, in degrees from the
 * viewport plane to the map plane. The result will be <code>0</code> for
 * imagery taken directly overhead or <code>45</code> for 45&deg; imagery.
 * 45&deg; imagery is only available for <code>satellite</code> and
 * <code>hybrid</code> map types, within some locations, and at some zoom
 * levels. <b>Note:</b> This method does not return the value set by
 * <code>setTilt</code>. See <code>setTilt</code> for details.
 * @return {number|undefined}
 */
google.maps.Map.prototype.getTilt = function() {};

/**
 * Returns the zoom of the map. If the zoom has not been set then the result is
 * <code>undefined</code>.
 * @return {number|undefined}
 */
google.maps.Map.prototype.getZoom = function() {};

/**
 * Changes the center of the map by the given distance in pixels. If the
 * distance is less than both the width and height of the map, the transition
 * will be smoothly animated. Note that the map coordinate system increases from
 * west to east (for x values) and north to south (for y values).
 * @param {number} x Number of pixels to move the map in the x direction.
 * @param {number} y Number of pixels to move the map in the y direction.
 * @return {undefined}
 */
google.maps.Map.prototype.panBy = function(x, y) {};

/**
 * Changes the center of the map to the given <code>LatLng</code>. If the change
 * is less than both the width and height of the map, the transition will be
 * smoothly animated.
 * @param {!google.maps.LatLng|!google.maps.LatLngLiteral} latLng The new center
 *     latitude/longitude of the map.
 * @return {undefined}
 */
google.maps.Map.prototype.panTo = function(latLng) {};

/**
 * Pans the map by the minimum amount necessary to contain the given
 * <code>LatLngBounds</code>. It makes no guarantee where on the map the bounds
 * will be, except that the map will be panned to show as much of the bounds as
 * possible inside <code>{currentMapSizeInPx} - {padding}</code>.
 * @param {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral}
 *     latLngBounds The bounds to pan the map to.
 * @param {(number|!google.maps.Padding)=} padding Padding in pixels. A number
 *     value will yield the same padding on all 4 sides. The default value is 0.
 * @return {undefined}
 */
google.maps.Map.prototype.panToBounds = function(latLngBounds, padding) {};

/**
 * @param {!google.maps.LatLng|!google.maps.LatLngLiteral} latlng
 * @return {undefined}
 */
google.maps.Map.prototype.setCenter = function(latlng) {};

/**
 * Controls whether the map icons are clickable or not. A map icon represents a
 * point of interest, also known as a POI. To disable the clickability of map
 * icons, pass a value of <code>false</code> to this method.
 * @param {boolean} value
 * @return {undefined}
 */
google.maps.Map.prototype.setClickableIcons = function(value) {};

/**
 * Sets the compass heading for aerial imagery measured in degrees from cardinal
 * direction North.
 * @param {number} heading
 * @return {undefined}
 */
google.maps.Map.prototype.setHeading = function(heading) {};

/**
 * @param {!google.maps.MapTypeId|string} mapTypeId
 * @return {undefined}
 */
google.maps.Map.prototype.setMapTypeId = function(mapTypeId) {};

/**
 * @param {?google.maps.MapOptions} options
 * @return {undefined}
 */
google.maps.Map.prototype.setOptions = function(options) {};

/**
 * Binds a <code>StreetViewPanorama</code> to the map. This panorama overrides
 * the default <code>StreetViewPanorama</code>, allowing the map to bind to an
 * external panorama outside of the map. Setting the panorama to
 * <code>null</code> binds the default embedded panorama back to the map.
 * @param {google.maps.StreetViewPanorama} panorama The panorama to bind to the
 *     map.
 * @return {undefined}
 */
google.maps.Map.prototype.setStreetView = function(panorama) {};

/**
 * Controls the automatic switching behavior for the angle of incidence of the
 * map. The only allowed values are <code>0</code> and <code>45</code>.
 * <code>setTilt(0)</code> causes the map to always use a 0&deg; overhead view
 * regardless of the zoom level and viewport. <code>setTilt(45)</code> causes
 * the tilt angle to automatically switch to 45 whenever 45&deg; imagery is
 * available for the current zoom level and viewport, and switch back to 0
 * whenever 45&deg; imagery is not available (this is the default behavior).
 * 45&deg; imagery is only available for <code>satellite</code> and
 * <code>hybrid</code> map types, within some locations, and at some zoom
 * levels. <b>Note:</b> <code>getTilt</code> returns the current tilt angle, not
 * the value set by <code>setTilt</code>. Because <code>getTilt</code> and
 * <code>setTilt</code> refer to different things, do not <code>bind()</code>
 * the <code>tilt</code> property; doing so may yield unpredictable effects.
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
 * This object is made available to the <code>OverlayView</code> from within the
 * draw method. It is not guaranteed to be initialized until draw is called.
 * @record
 */
google.maps.MapCanvasProjection = function() {};

/**
 * Computes the geographical coordinates from pixel coordinates in the map&#39;s
 * container.
 * @param {?google.maps.Point} pixel
 * @param {boolean=} nowrap
 * @return {?google.maps.LatLng}
 */
google.maps.MapCanvasProjection.prototype.fromContainerPixelToLatLng = function(
    pixel, nowrap) {};

/**
 * Computes the geographical coordinates from pixel coordinates in the div that
 * holds the draggable map.
 * @param {?google.maps.Point} pixel
 * @param {boolean=} nowrap
 * @return {?google.maps.LatLng}
 */
google.maps.MapCanvasProjection.prototype.fromDivPixelToLatLng = function(
    pixel, nowrap) {};

/**
 * Computes the pixel coordinates of the given geographical location in the
 * map&#39;s container element.
 * @param {?google.maps.LatLng} latLng
 * @return {?google.maps.Point}
 */
google.maps.MapCanvasProjection.prototype.fromLatLngToContainerPixel = function(
    latLng) {};

/**
 * Computes the pixel coordinates of the given geographical location in the DOM
 * element that holds the draggable map.
 * @param {?google.maps.LatLng} latLng
 * @return {?google.maps.Point}
 */
google.maps.MapCanvasProjection.prototype.fromLatLngToDivPixel = function(
    latLng) {};

/**
 * The width of the world in pixels in the current zoom level. For projections
 * with a heading angle of either 90 or 270 degrees, this corresponds to the
 * pixel span in the Y-axis.
 * @return {number}
 */
google.maps.MapCanvasProjection.prototype.getWorldWidth = function() {};

/**
 * This object is returned from various mouse events on the map and overlays,
 * and contains all the fields shown below.
 * @record
 */
google.maps.MapMouseEvent = function() {};

/**
 * The corresponding native DOM event. Developers should not rely on
 * <code>target</code>, <code>currentTarget</code>, <code>relatedTarget</code>
 * and <code>path</code> properties being defined and consistent. Developers
 * should not also rely on the DOM structure of the internal implementation of
 * the Maps API. Due to internal event mapping, the <code>domEvent</code> may
 * have different semantics from the {@link google.maps.MapMouseEvent} (e.g.
 * a {@link google.maps.MapMouseEvent} &quot;click&quot; may have a
 * <code>domEvent</code> of type <code>KeyboardEvent</code>).
 * @type {!MouseEvent|!TouchEvent|!PointerEvent|!KeyboardEvent|!Event}
 */
google.maps.MapMouseEvent.prototype.domEvent;

/**
 * The latitude/longitude that was below the cursor when the event occurred.
 * @type {google.maps.LatLng}
 */
google.maps.MapMouseEvent.prototype.latLng;

/**
 * Prevents this event from propagating further.
 * @return {undefined}
 */
google.maps.MapMouseEvent.prototype.stop = function() {};

/**
 * MapOptions object used to define the properties that can be set on a Map.
 * @record
 */
google.maps.MapOptions = function() {};

/**
 * Color used for the background of the Map div. This color will be visible when
 * tiles have not yet loaded as the user pans. This option can only be set when
 * the map is initialized.
 * @type {?string|undefined}
 */
google.maps.MapOptions.prototype.backgroundColor;

/**
 * The initial Map center.
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.MapOptions.prototype.center;

/**
 * When <code>false</code>, map icons are not clickable. A map icon represents a
 * point of interest, also known as a POI. By default map icons are clickable.
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.clickableIcons;

/**
 * Size in pixels of the controls appearing on the map. This value must be
 * supplied directly when creating the Map, updating this value later may bring
 * the controls into an <code>undefined</code> state. Only governs the controls
 * made by the Maps API itself. Does not scale developer created custom
 * controls.
 * @type {?number|undefined}
 */
google.maps.MapOptions.prototype.controlSize;

/**
 * Enables/disables all default UI buttons. May be overridden individually. Does
 * not disable the keyboard controls, which are separately controlled by
 * the {@link google.maps.MapOptions.keyboardShortcuts} option. Does not disable
 * gesture controls, which are separately controlled by the {@link
 * google.maps.MapOptions.gestureHandling} option.
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.disableDefaultUI;

/**
 * Enables/disables zoom and center on double click. Enabled by default.
 * <p><strong>Note</strong>: This property is <strong>not recommended</strong>.
 * To disable zooming on double click, you can use the
 * <code>gestureHandling</code> property, and set it to <code>"none"</code>.
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.disableDoubleClickZoom;

/**
 * If <code>false</code>, prevents the map from being dragged. Dragging is
 * enabled by default.
 * @type {?boolean|undefined}
 * @deprecated Deprecated in 2017. To disable dragging on the map, you can use
 *     the <code>gestureHandling</code> property, and set it to
 *     <code>"none"</code>.
 */
google.maps.MapOptions.prototype.draggable;

/**
 * The name or url of the cursor to display when mousing over a draggable map.
 * This property uses the css <code>cursor</code> attribute to change the icon.
 * As with the css property, you must specify at least one fallback cursor that
 * is not a URL. For example: <code>draggableCursor: 'url(<a
 * href="http://www.example.com/icon.png">http://www.example.com/icon.png</a>),
 * auto;'</code>.
 * @type {?string|undefined}
 */
google.maps.MapOptions.prototype.draggableCursor;

/**
 * The name or url of the cursor to display when the map is being dragged. This
 * property uses the css <code>cursor</code> attribute to change the icon. As
 * with the css property, you must specify at least one fallback cursor that is
 * not a URL. For example: <code>draggingCursor: 'url(<a
 * href="http://www.example.com/icon.png">http://www.example.com/icon.png</a>),
 * auto;'</code>.
 * @type {?string|undefined}
 */
google.maps.MapOptions.prototype.draggingCursor;

/**
 * The enabled/disabled state of the Fullscreen control.
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.fullscreenControl;

/**
 * The display options for the Fullscreen control.
 * @type {?google.maps.FullscreenControlOptions|undefined}
 */
google.maps.MapOptions.prototype.fullscreenControlOptions;

/**
 * This setting controls how the API handles gestures on the map. Allowed
 * values: <ul> <li> <code>"cooperative"</code>: Scroll events and one-finger
 * touch gestures scroll the page, and do not zoom or pan the map. Two-finger
 * touch gestures pan and zoom the map. Scroll events with a ctrl key or ⌘ key
 * pressed zoom the map.<br> In this mode the map <em>cooperates</em> with the
 * page. <li> <code>"greedy"</code>: All touch gestures and scroll events pan or
 * zoom the map. <li> <code>"none"</code>: The map cannot be panned or zoomed by
 * user gestures. <li> <code>"auto"</code>: (default) Gesture handling is either
 * cooperative or greedy, depending on whether the page is scrollable or in an
 * iframe. </ul>
 * @type {?string|undefined}
 */
google.maps.MapOptions.prototype.gestureHandling;

/**
 * The heading for aerial imagery in degrees measured clockwise from cardinal
 * direction North. Headings are snapped to the nearest available angle for
 * which imagery is available.
 * @type {?number|undefined}
 */
google.maps.MapOptions.prototype.heading;

/**
 * If <code>false</code>, prevents the map from being controlled by the
 * keyboard. Keyboard shortcuts are enabled by default.
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.keyboardShortcuts;

/**
 * The initial enabled/disabled state of the Map type control.
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.mapTypeControl;

/**
 * The initial display options for the Map type control.
 * @type {?google.maps.MapTypeControlOptions|undefined}
 */
google.maps.MapOptions.prototype.mapTypeControlOptions;

/**
 * The initial Map mapTypeId. Defaults to <code>ROADMAP</code>.
 * @type {?google.maps.MapTypeId|?string|undefined}
 */
google.maps.MapOptions.prototype.mapTypeId;

/**
 * The maximum zoom level which will be displayed on the map. If omitted, or set
 * to <code>null</code>, the maximum zoom from the current map type is used
 * instead. Valid values: Integers between zero, and up to the supported <a
 * href="https://developers.google.com/maps/documentation/javascript/maxzoom">maximum
 * zoom level</a>.
 * @type {?number|undefined}
 */
google.maps.MapOptions.prototype.maxZoom;

/**
 * The minimum zoom level which will be displayed on the map. If omitted, or set
 * to <code>null</code>, the minimum zoom from the current map type is used
 * instead. Valid values: Integers between zero, and up to the supported <a
 * href="https://developers.google.com/maps/documentation/javascript/maxzoom">maximum
 * zoom level</a>.
 * @type {?number|undefined}
 */
google.maps.MapOptions.prototype.minZoom;

/**
 * If <code>true</code>, do not clear the contents of the Map div.
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.noClear;

/**
 * The enabled/disabled state of the Pan control. <p> Note: The Pan control is
 * not available in the new set of controls introduced in v3.22 of the Google
 * Maps JavaScript API. While using v3.22 and v3.23, you can choose to use the
 * earlier set of controls rather than the new controls, thus making the Pan
 * control available as part of the old control set. See <a
 * href="https://developers.google.com/maps/articles/v322-controls-diff">What&#39;s
 * New in the v3.22 Map Controls</a>.
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.panControl;

/**
 * The display options for the Pan control. <p> Note: The Pan control is not
 * available in the new set of controls introduced in v3.22 of the Google Maps
 * JavaScript API. While using v3.22 and v3.23, you can choose to use the
 * earlier set of controls rather than the new controls, thus making the Pan
 * control available as part of the old control set. See <a
 * href="https://developers.google.com/maps/articles/v322-controls-diff">What&#39;s
 * New in the v3.22 Map Controls</a>.
 * @type {?google.maps.PanControlOptions|undefined}
 */
google.maps.MapOptions.prototype.panControlOptions;

/**
 * Defines a boundary that restricts the area of the map accessible to users.
 * When set, a user can only pan and zoom while the camera view stays inside the
 * limits of the boundary.
 * @type {?google.maps.MapRestriction|undefined}
 */
google.maps.MapOptions.prototype.restriction;

/**
 * The enabled/disabled state of the Rotate control.
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.rotateControl;

/**
 * The display options for the Rotate control.
 * @type {?google.maps.RotateControlOptions|undefined}
 */
google.maps.MapOptions.prototype.rotateControlOptions;

/**
 * The initial enabled/disabled state of the Scale control.
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.scaleControl;

/**
 * The initial display options for the Scale control.
 * @type {?google.maps.ScaleControlOptions|undefined}
 */
google.maps.MapOptions.prototype.scaleControlOptions;

/**
 * If <code>false</code>, disables zooming on the map using a mouse scroll
 * wheel. The scrollwheel is enabled by default. <p><strong>Note</strong>: This
 * property is <strong>not recommended</strong>. To disable zooming using
 * scrollwheel, you can use the <code>gestureHandling</code> property, and set
 * it to either <code>"cooperative"</code> or <code>"none"</code>.
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.scrollwheel;

/**
 * A <code>StreetViewPanorama</code> to display when the Street View pegman is
 * dropped on the map. If no panorama is specified, a default
 * <code>StreetViewPanorama</code> will be displayed in the map&#39;s
 * <code>div</code> when the pegman is dropped.
 * @type {?google.maps.StreetViewPanorama|undefined}
 */
google.maps.MapOptions.prototype.streetView;

/**
 * The initial enabled/disabled state of the Street View Pegman control. This
 * control is part of the default UI, and should be set to <code>false</code>
 * when displaying a map type on which the Street View road overlay should not
 * appear (e.g. a non-Earth map type).
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.streetViewControl;

/**
 * The initial display options for the Street View Pegman control.
 * @type {?google.maps.StreetViewControlOptions|undefined}
 */
google.maps.MapOptions.prototype.streetViewControlOptions;

/**
 * Styles to apply to each of the default map types. Note that for
 * <code>satellite</code>/<code>hybrid</code> and <code>terrain</code> modes,
 * these styles will only apply to labels and geometry.
 * @type {?Array<!google.maps.MapTypeStyle>|undefined}
 */
google.maps.MapOptions.prototype.styles;

/**
 * Controls the automatic switching behavior for the angle of incidence of the
 * map. The only allowed values are <code>0</code> and <code>45</code>. The
 * value <code>0</code> causes the map to always use a 0&deg; overhead view
 * regardless of the zoom level and viewport. The value <code>45</code> causes
 * the tilt angle to automatically switch to 45 whenever 45&deg; imagery is
 * available for the current zoom level and viewport, and switch back to 0
 * whenever 45&deg; imagery is not available (this is the default behavior).
 * 45&deg; imagery is only available for <code>satellite</code> and
 * <code>hybrid</code> map types, within some locations, and at some zoom
 * levels. <b>Note:</b> <code>getTilt</code> returns the current tilt angle, not
 * the value specified by this option. Because <code>getTilt</code> and this
 * option refer to different things, do not <code>bind()</code> the
 * <code>tilt</code> property; doing so may yield unpredictable effects.
 * @type {?number|undefined}
 */
google.maps.MapOptions.prototype.tilt;

/**
 * The initial Map zoom level. Valid values: Integers between zero, and up to
 * the supported <a
 * href="https://developers.google.com/maps/documentation/javascript/maxzoom">maximum
 * zoom level</a>.
 * @type {?number|undefined}
 */
google.maps.MapOptions.prototype.zoom;

/**
 * The enabled/disabled state of the Zoom control.
 * @type {?boolean|undefined}
 */
google.maps.MapOptions.prototype.zoomControl;

/**
 * The display options for the Zoom control.
 * @type {?google.maps.ZoomControlOptions|undefined}
 */
google.maps.MapOptions.prototype.zoomControlOptions;

/**
 * @record
 */
google.maps.MapPanes = function() {};

/**
 * This pane contains the info window. It is above all map overlays. (Pane 4).
 * @type {!Element}
 */
google.maps.MapPanes.prototype.floatPane;

/**
 * This pane is the lowest pane and is above the tiles. It does not receive DOM
 * events. (Pane 0).
 * @type {!Element}
 */
google.maps.MapPanes.prototype.mapPane;

/**
 * This pane contains markers. It does not receive DOM events. (Pane 2).
 * @type {!Element}
 */
google.maps.MapPanes.prototype.markerLayer;

/**
 * This pane contains polylines, polygons, ground overlays and tile layer
 * overlays. It does not receive DOM events. (Pane 1).
 * @type {!Element}
 */
google.maps.MapPanes.prototype.overlayLayer;

/**
 * This pane contains elements that receive DOM events. (Pane 3).
 * @type {!Element}
 */
google.maps.MapPanes.prototype.overlayMouseTarget;

/**
 * A restriction that can be applied to the Map. The map&#39;s viewport will not
 * exceed these restrictions.
 * @record
 */
google.maps.MapRestriction = function() {};

/**
 * When set, a user can only pan and zoom inside the given bounds. Bounds can
 * restrict both longitude and latitude, or can restrict latitude only. For
 * latitude-only bounds use west and east longitudes of -180 and 180,
 * respectively, for example, <code>latLngBounds: {north: northLat, south:
 * southLat, west: -180, east: 180}</code>.
 * @type {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral}
 */
google.maps.MapRestriction.prototype.latLngBounds;

/**
 * Bounds can be made more restrictive by setting the <code>strictBounds</code>
 * flag to <code>true</code>. This reduces how far a user can zoom out, ensuring
 * that everything outside of the restricted bounds stays hidden. The default is
 * <code>false</code>, meaning that a user can zoom out until the entire bounded
 * area is in view, possibly including areas outside the bounded area.
 * @type {boolean|undefined}
 */
google.maps.MapRestriction.prototype.strictBounds;

/**
 * This interface defines the map type, and is typically used for custom map
 * types. Immutable.
 * @record
 */
google.maps.MapType = function() {};

/**
 * Alt text to display when this MapType&#39;s button is hovered over in the
 * MapTypeControl. Optional.
 * @type {?string}
 */
google.maps.MapType.prototype.alt;

/**
 * The maximum zoom level for the map when displaying this MapType. Required for
 * base MapTypes, ignored for overlay MapTypes.
 * @type {number}
 */
google.maps.MapType.prototype.maxZoom;

/**
 * The minimum zoom level for the map when displaying this MapType. Optional;
 * defaults to 0.
 * @type {number}
 */
google.maps.MapType.prototype.minZoom;

/**
 * Name to display in the MapTypeControl. Optional.
 * @type {?string}
 */
google.maps.MapType.prototype.name;

/**
 * The Projection used to render this MapType. Optional; defaults to Mercator.
 * @type {?google.maps.Projection}
 */
google.maps.MapType.prototype.projection;

/**
 * Radius of the planet for the map, in meters. Optional; defaults to
 * Earth&#39;s equatorial radius of 6378137 meters.
 * @type {number}
 */
google.maps.MapType.prototype.radius;

/**
 * The dimensions of each tile. Required.
 * @type {google.maps.Size}
 */
google.maps.MapType.prototype.tileSize;

/**
 * Returns a tile for the given tile coordinate (x, y) and zoom level. This tile
 * will be appended to the given ownerDocument. Not available for base map
 * types.
 * @param {google.maps.Point} tileCoord
 * @param {number} zoom
 * @param {Document} ownerDocument
 * @return {Node}
 */
google.maps.MapType.prototype.getTile = function(
    tileCoord, zoom, ownerDocument) {};

/**
 * Releases the given tile, performing any necessary cleanup. The provided tile
 * will have already been removed from the document. Optional.
 * @param {Node} tile
 * @return {undefined}
 */
google.maps.MapType.prototype.releaseTile = function(tile) {};

/**
 * Options for the rendering of the map type control.
 * @record
 */
google.maps.MapTypeControlOptions = function() {};

/**
 * IDs of map types to show in the control.
 * @type {?Array<!google.maps.MapTypeId|string>|undefined}
 */
google.maps.MapTypeControlOptions.prototype.mapTypeIds;

/**
 * Position id. Used to specify the position of the control on the map. The
 * default position is <code>TOP_RIGHT</code>.
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.MapTypeControlOptions.prototype.position;

/**
 * Style id. Used to select what style of map type control to display.
 * @type {?google.maps.MapTypeControlStyle|undefined}
 */
google.maps.MapTypeControlOptions.prototype.style;

/**
 * Identifiers for common MapTypesControls.
 * @enum {number}
 */
google.maps.MapTypeControlStyle = {
  /**
   * Uses the default map type control. When the <code>DEFAULT</code> control is
   * shown, it will vary according to window size and other factors. The
   * <code>DEFAULT</code> control may change in future versions of the API.
   */
  DEFAULT: 0,
  /**
   * A dropdown menu for the screen realestate conscious.
   */
  DROPDOWN_MENU: 1,
  /**
   * The standard horizontal radio buttons bar.
   */
  HORIZONTAL_BAR: 2,
};

/**
 * Identifiers for common MapTypes. Specify these by value, or by using the
 * constant&#39;s name. For example, <code>'satellite'</code> or
 * <code>google.maps.MapTypeId.SATELLITE</code>.
 * @enum {string}
 */
google.maps.MapTypeId = {
  /**
   * This map type displays a transparent layer of major streets on satellite
   * images.
   */
  HYBRID: 'HYBRID',
  /**
   * This map type displays a normal street map.
   */
  ROADMAP: 'ROADMAP',
  /**
   * This map type displays satellite images.
   */
  SATELLITE: 'SATELLITE',
  /**
   * This map type displays maps with physical features such as terrain and
   * vegetation.
   */
  TERRAIN: 'TERRAIN',
};

/**
 * A registry for MapType instances, keyed by MapType id.
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.MapTypeRegistry = function() {};

/**
 * Sets the registry to associate the passed string identifier with the passed
 * MapType.
 * @param {string} id
 * @param {google.maps.MapType|*} mapType
 * @return {undefined}
 * @override
 */
google.maps.MapTypeRegistry.prototype.set = function(id, mapType) {};

/**
 * The <code>MapTypeStyle</code> is a collection of selectors and stylers that
 * define how the map should be styled. Selectors specify the map features
 * and/or elements that should be affected, and stylers specify how those
 * features and elements should be modified. For details, see the <a
 * href="https://developers.google.com/maps/documentation/javascript/style-reference">style
 * reference</a>.
 * @record
 */
google.maps.MapTypeStyle = function() {};

/**
 * The element to which a styler should be applied. An element is a visual
 * aspect of a feature on the map. Example: a label, an icon, the stroke or fill
 * applied to the geometry, and more. Optional. If <code>elementType</code> is
 * not specified, the value is assumed to be <code>'all'</code>. For details of
 * usage and allowed values, see the <a
 * href="https://developers.google.com/maps/documentation/javascript/style-reference#style-elements">style
 * reference</a>.
 * @type {?string|undefined}
 */
google.maps.MapTypeStyle.prototype.elementType;

/**
 * The feature, or group of features, to which a styler should be applied.
 * Optional. If <code>featureType</code> is not specified, the value is assumed
 * to be <code>'all'</code>. For details of usage and allowed values, see the <a
 * href="https://developers.google.com/maps/documentation/javascript/style-reference#style-features">style
 * reference</a>.
 * @type {?string|undefined}
 */
google.maps.MapTypeStyle.prototype.featureType;

/**
 * The style rules to apply to the selected map features and elements. The rules
 * are applied in the order that you specify in this array. For guidelines on
 * usage and allowed values, see the <a
 * href="https://developers.google.com/maps/documentation/javascript/style-reference#stylers">style
 * reference</a>.
 * @type {!Array<!Object>}
 */
google.maps.MapTypeStyle.prototype.stylers;

/**
 * An event listener, created by <code><a
 * href="#event">google.maps.event.addListener</a>()</code> and friends.
 * @record
 */
google.maps.MapsEventListener = function() {};

/**
 * Removes the listener. <p>Calling <code>listener.remove()</code> is equivalent
 * to <code>google.maps.event.removeListener(listener)</code>.
 * @return {undefined}
 */
google.maps.MapsEventListener.prototype.remove = function() {};

/**
 * @param {google.maps.MarkerOptions=} opts Named optional arguments
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.Marker = function(opts) {};

/**
 * Get the currently running animation.
 * @return {?google.maps.Animation}
 */
google.maps.Marker.prototype.getAnimation = function() {};

/**
 * Get the clickable status of the {@link google.maps.Marker}.
 * @return {boolean} True if the Marker is clickable.
 */
google.maps.Marker.prototype.getClickable = function() {};

/**
 * Get the mouse cursor type shown on hover.
 * @return {string}
 */
google.maps.Marker.prototype.getCursor = function() {};

/**
 * Get the draggable status of the {@link google.maps.Marker}.
 * @return {boolean} True if the Marker is draggable.
 */
google.maps.Marker.prototype.getDraggable = function() {};

/**
 * Get the icon of the {@link google.maps.Marker}. See {@link
 * google.maps.MarkerOptions.icon}.
 * @return {string|google.maps.Icon|google.maps.Symbol}
 */
google.maps.Marker.prototype.getIcon = function() {};

/**
 * Get the label of the {@link google.maps.Marker}. See {@link
 * google.maps.MarkerOptions.label}.
 * @return {google.maps.MarkerLabel}
 */
google.maps.Marker.prototype.getLabel = function() {};

/**
 * Get the map or panaroama the {@link google.maps.Marker} is rendered on.
 * @return {google.maps.Map|google.maps.StreetViewPanorama}
 */
google.maps.Marker.prototype.getMap = function() {};

/**
 * Get the opacity of the {@link google.maps.Marker}.
 * @return {number} A number between 0.0 and 1.0.
 */
google.maps.Marker.prototype.getOpacity = function() {};

/**
 * Get the position of the {@link google.maps.Marker}.
 * @return {?google.maps.LatLng|undefined}
 */
google.maps.Marker.prototype.getPosition = function() {};

/**
 * Get the shape of the {@link google.maps.Marker} used for interaction.
 * See {@link google.maps.MarkerOptions.shape} and {@link
 * google.maps.MarkerShape}.
 * @return {google.maps.MarkerShape}
 */
google.maps.Marker.prototype.getShape = function() {};

/**
 * Get the title of the {@link google.maps.Marker} tooltip. See {@link
 * google.maps.MarkerOptions.title}.
 * @return {string}
 */
google.maps.Marker.prototype.getTitle = function() {};

/**
 * Get the visibility of the {@link google.maps.Marker}.
 * @return {boolean} True if the Marker is visible.
 */
google.maps.Marker.prototype.getVisible = function() {};

/**
 * Get the zIndex of the {@link google.maps.Marker}. See {@link
 * google.maps.MarkerOptions.zIndex}.
 * @return {number} zIndex of the Marker.
 */
google.maps.Marker.prototype.getZIndex = function() {};

/**
 * Start an animation. Any ongoing animation will be cancelled. Currently
 * supported animations are: {@link google.maps.Animation.BOUNCE}, {@link
 * google.maps.Animation.DROP}. Passing in <code>null</code> will cause any
 * animation to stop.
 * @param {?google.maps.Animation} animation The animation to play.
 * @return {undefined}
 */
google.maps.Marker.prototype.setAnimation = function(animation) {};

/**
 * Set if the {@link google.maps.Marker} is clickable.
 * @param {boolean} flag If <code>true</code>, the Marker can be clicked.
 * @return {undefined}
 */
google.maps.Marker.prototype.setClickable = function(flag) {};

/**
 * Set the mouse cursor type shown on hover.
 * @param {string} cursor Mouse cursor type.
 * @return {undefined}
 */
google.maps.Marker.prototype.setCursor = function(cursor) {};

/**
 * Set if the {@link google.maps.Marker} is draggable.
 * @param {?boolean} flag If <code>true</code>, the Marker can be dragged.
 * @return {undefined}
 */
google.maps.Marker.prototype.setDraggable = function(flag) {};

/**
 * Set the icon for the {@link google.maps.Marker}. See {@link
 * google.maps.MarkerOptions.icon}.
 * @param {string|google.maps.Icon|google.maps.Symbol} icon
 * @return {undefined}
 */
google.maps.Marker.prototype.setIcon = function(icon) {};

/**
 * Set the label for the {@link google.maps.Marker}. See {@link
 * google.maps.MarkerOptions.label}.
 * @param {string|google.maps.MarkerLabel} label The label can either be a
 *     character string or a {@link google.maps.MarkerLabel} object.
 * @return {undefined}
 */
google.maps.Marker.prototype.setLabel = function(label) {};

/**
 * Renders the {@link google.maps.Marker} on the specified map or panorama. If
 * map is set to <code>null</code>, the marker will be removed.
 * @param {google.maps.Map|google.maps.StreetViewPanorama} map
 * @return {undefined}
 */
google.maps.Marker.prototype.setMap = function(map) {};

/**
 * Set the opacity of the {@link google.maps.Marker}.
 * @param {number} opacity A number between 0.0, transparent, and 1.0, opaque.
 * @return {undefined}
 */
google.maps.Marker.prototype.setOpacity = function(opacity) {};

/**
 * Set the options for the {@link google.maps.Marker}.
 * @param {google.maps.MarkerOptions} options
 * @return {undefined}
 */
google.maps.Marker.prototype.setOptions = function(options) {};

/**
 * Set the postition for the {@link google.maps.Marker}.
 * @param {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined} latlng The
 *     new position.
 * @return {undefined}
 */
google.maps.Marker.prototype.setPosition = function(latlng) {};

/**
 * Set the shape of the {@link google.maps.Marker} used for interaction.
 * See {@link google.maps.MarkerOptions.shape} and {@link
 * google.maps.MarkerShape}.
 * @param {google.maps.MarkerShape} shape
 * @return {undefined}
 */
google.maps.Marker.prototype.setShape = function(shape) {};

/**
 * Set the title of the {@link google.maps.Marker} tooltip. See {@link
 * google.maps.MarkerOptions.title}.
 * @param {string} title
 * @return {undefined}
 */
google.maps.Marker.prototype.setTitle = function(title) {};

/**
 * Set if the {@link google.maps.Marker} is visible.
 * @param {boolean} visible If <code>true</code>, the Marker is visible
 * @return {undefined}
 */
google.maps.Marker.prototype.setVisible = function(visible) {};

/**
 * Set the zIndex of the {@link google.maps.Marker}. See {@link
 * google.maps.MarkerOptions.zIndex}.
 * @param {number} zIndex
 * @return {undefined}
 */
google.maps.Marker.prototype.setZIndex = function(zIndex) {};

/**
 * The maximum default z-index that the API will assign to a marker. You may set
 * a higher z-index to bring a marker to the front.
 * @const
 * @type {number|string}
 */
google.maps.Marker.MAX_ZINDEX;

/**
 * These options specify the appearance of a marker label. A marker label is a
 * string (often a single character) which will appear inside the marker. If you
 * are using it with a custom marker, you can reposition it with the
 * <code>labelOrigin</code> property in the <code>Icon</code> class.
 * @record
 */
google.maps.MarkerLabel = function() {};

/**
 * The className property of the label&#39;s element (equivalent to the
 * element&#39;s class attribute). Multiple space-separated CSS classes can be
 * added. Default is no CSS class (an empty string). The font color, size,
 * weight, and family can only be set via the other properties of
 * <code>MarkerLabel</code>. CSS classes should not be used to change the
 * position nor orientation of the label (e.g. using translations and rotations)
 * if also using <a
 * href="https://developers.google.com/maps/documentation/javascript/examples/marker-collision-management">marker
 * collision management</a>.
 * @type {string|undefined}
 */
google.maps.MarkerLabel.prototype.className;

/**
 * The color of the label text. Default color is black.
 * @type {string|undefined}
 */
google.maps.MarkerLabel.prototype.color;

/**
 * The font family of the label text (equivalent to the CSS font-family
 * property).
 * @type {string|undefined}
 */
google.maps.MarkerLabel.prototype.fontFamily;

/**
 * The font size of the label text (equivalent to the CSS font-size property).
 * Default size is 14px.
 * @type {string|undefined}
 */
google.maps.MarkerLabel.prototype.fontSize;

/**
 * The font weight of the label text (equivalent to the CSS font-weight
 * property).
 * @type {string|undefined}
 */
google.maps.MarkerLabel.prototype.fontWeight;

/**
 * The text to be displayed in the label.
 * @type {string}
 */
google.maps.MarkerLabel.prototype.text;

/**
 * MarkerOptions object used to define the properties that can be set on a
 * Marker.
 * @record
 */
google.maps.MarkerOptions = function() {};

/**
 * The offset from the marker&#39;s position to the tip of an InfoWindow that
 * has been opened with the marker as anchor.
 * @type {?google.maps.Point|undefined}
 */
google.maps.MarkerOptions.prototype.anchorPoint;

/**
 * Which animation to play when marker is added to a map.
 * @type {?google.maps.Animation|undefined}
 */
google.maps.MarkerOptions.prototype.animation;

/**
 * If <code>true</code>, the marker receives mouse and touch events. Default
 * value is <code>true</code>.
 * @type {?boolean|undefined}
 */
google.maps.MarkerOptions.prototype.clickable;

/**
 * If <code>false</code>, disables cross that appears beneath the marker when
 * dragging. This option is <code>true</code> by default.
 * @type {?boolean|undefined}
 */
google.maps.MarkerOptions.prototype.crossOnDrag;

/**
 * Mouse cursor type to show on hover.
 * @type {?string|undefined}
 */
google.maps.MarkerOptions.prototype.cursor;

/**
 * If <code>true</code>, the marker can be dragged. Default value is
 * <code>false</code>.
 * @type {?boolean|undefined}
 */
google.maps.MarkerOptions.prototype.draggable;

/**
 * Icon for the foreground. If a string is provided, it is treated as though it
 * were an <code>Icon</code> with the string as <code>url</code>.
 * @type {?string|?google.maps.Icon|?google.maps.Symbol|undefined}
 */
google.maps.MarkerOptions.prototype.icon;

/**
 * Adds a label to the marker. A marker label is a letter or number that appears
 * inside a marker. The label can either be a string, or a
 * <code>MarkerLabel</code> object. If provided and {@link
 * google.maps.MarkerOptions.title} is not provided, an accessibility text (e.g.
 * for use with screen readers) will be added to the marker with the provided
 * label&#39;s text. Please note that the <code>label</code> is currently only
 * used for accessibility text for non-optimized markers.
 * @type {?string|?google.maps.MarkerLabel|undefined}
 */
google.maps.MarkerOptions.prototype.label;

/**
 * Map on which to display Marker. The map is required to display the marker and
 * can be provided with {@link google.maps.Marker.setMap} if not provided at
 * marker construction.
 * @type {?google.maps.Map|?google.maps.StreetViewPanorama|undefined}
 */
google.maps.MarkerOptions.prototype.map;

/**
 * A number between 0.0, transparent, and 1.0, opaque.
 * @type {?number|undefined}
 */
google.maps.MarkerOptions.prototype.opacity;

/**
 * Optimization renders many markers as a single static element. Disable
 * optimized rendering for animated GIFs or PNGs, or when each marker must be
 * rendered as a separate DOM element (advanced usage only). By default, the
 * Maps JavaScript API will decide whether or not a Marker will be optimized.
 * Not all Markers can be optimized; in some situations, the Maps JavaScript API
 * may need to render Markers without optimization.
 * @type {?boolean|undefined}
 */
google.maps.MarkerOptions.prototype.optimized;

/**
 * Marker position. The position is required to display the marker and can be
 * provided with {@link google.maps.Marker.setPosition} if not provided at
 * marker construction.
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.MarkerOptions.prototype.position;

/**
 * Image map region definition used for drag/click.
 * @type {?google.maps.MarkerShape|undefined}
 */
google.maps.MarkerOptions.prototype.shape;

/**
 * Rollover text. If provided, an accessibility text (e.g. for use with screen
 * readers) will be added to the marker with the provided value. Please note
 * that the <code>title</code> is currently only used for accessibility text for
 * non-optimized markers.
 * @type {?string|undefined}
 */
google.maps.MarkerOptions.prototype.title;

/**
 * If <code>true</code>, the marker is visible.
 * @type {?boolean|undefined}
 */
google.maps.MarkerOptions.prototype.visible;

/**
 * All markers are displayed on the map in order of their zIndex, with higher
 * values displaying in front of markers with lower values. By default, markers
 * are displayed according to their vertical position on screen, with lower
 * markers appearing in front of markers further up the screen.
 * @type {?number|undefined}
 */
google.maps.MarkerOptions.prototype.zIndex;

/**
 * This object defines the clickable region of a marker image. The shape
 * consists of two properties &mdash; <code>type</code> and <code>coord</code>
 * &mdash; which define the non-transparent region of an image.
 * @record
 */
google.maps.MarkerShape = function() {};

/**
 * The format of this attribute depends on the value of the <code>type</code>
 * and follows the w3 AREA <code>coords</code> specification found at <a
 * href="http://www.w3.org/TR/REC-html40/struct/objects.html#adef-coords">
 * http://www.w3.org/TR/REC-html40/struct/objects.html#adef-coords</a>. <br>The
 * <code>coords</code> attribute is an array of integers that specify the pixel
 * position of the shape relative to the top-left corner of the target image.
 * The coordinates depend on the value of <code>type</code> as follows:
 * <br>&nbsp;&nbsp;- <code>circle</code>: coords is <code>[x1,y1,r]</code> where
 * x1,y2 are the coordinates of the center of the circle, and r is the radius of
 * the circle. <br>&nbsp;&nbsp;- <code>poly</code>: coords is
 * <code>[x1,y1,x2,y2...xn,yn]</code> where each x,y pair contains the
 * coordinates of one vertex of the polygon. <br>&nbsp;&nbsp;-
 * <code>rect</code>: coords is <code>[x1,y1,x2,y2]</code> where x1,y1 are the
 * coordinates of the upper-left corner of the rectangle and x2,y2 are the
 * coordinates of the lower-right coordinates of the rectangle.
 * @type {Array<number>}
 */
google.maps.MarkerShape.prototype.coords;

/**
 * Describes the shape&#39;s type and can be <code>circle</code>,
 * <code>poly</code> or <code>rect</code>.
 * @type {string}
 */
google.maps.MarkerShape.prototype.type;

/**
 * A MaxZoom result in JSON format retrieved from the MaxZoomService.
 * @record
 */
google.maps.MaxZoomResult = function() {};

/**
 * Status of the request. This property is only defined when using callbacks
 * with {@link google.maps.MaxZoomService.getMaxZoomAtLatLng} (it is not defined
 * when using Promises).
 * @type {?google.maps.MaxZoomStatus}
 */
google.maps.MaxZoomResult.prototype.status;

/**
 * The maximum zoom level found at the given <code>LatLng</code>.
 * @type {number}
 */
google.maps.MaxZoomResult.prototype.zoom;

/**
 * A service for obtaining the highest zoom level at which satellite imagery is
 * available for a given location.
 * @constructor
 */
google.maps.MaxZoomService = function() {};

/**
 * Returns the maximum zoom level for which detailed imagery is available at a
 * particular <code>LatLng</code> for the <code>satellite</code> map type. As
 * this request is asynchronous, you must pass a <code>callback</code> function
 * which will be executed upon completion of the request, being passed a
 * <code>MaxZoomResult</code>.<aside class="note">Note: <strong>For the beta
 * release, <code>v=beta</code>, the callback is optional and a Promise is
 * returned</strong>. More information is available in the <a
 * href="https://developers.google.com/maps/documentation/javascript/promises">Promises
 * guide</a>.</aside>
 * @param {google.maps.LatLng|google.maps.LatLngLiteral} latlng
 * @param {(function(!google.maps.MaxZoomResult): void)=} callback
 * @return {?Promise<!google.maps.MaxZoomResult>}
 */
google.maps.MaxZoomService.prototype.getMaxZoomAtLatLng = function(
    latlng, callback) {};

/**
 * The status returned by the <code>MaxZoomService</code> on the completion of a
 * call to <code>getMaxZoomAtLatLng()</code>. Specify these by value, or by
 * using the constant&#39;s name. For example, <code>'OK'</code> or
 * <code>google.maps.MaxZoomStatus.OK</code>.
 * @enum {string}
 */
google.maps.MaxZoomStatus = {
  /**
   * An unknown error occurred.
   */
  ERROR: 'ERROR',
  /**
   * The response contains a valid <code>MaxZoomResult</code>.
   */
  OK: 'OK',
};

/**
 * Options for the rendering of the motion tracking control.
 * @record
 */
google.maps.MotionTrackingControlOptions = function() {};

/**
 * Position id. This is used to specify the position of this control on the
 * panorama. The default position is <code>RIGHT_BOTTOM</code>.
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.MotionTrackingControlOptions.prototype.position;

/**
 * You can implement this class if you want to display custom types of overlay
 * objects on the map. <br><br>Inherit from this class by setting your
 * overlay&#39;s prototype: <code>MyOverlay.prototype = new
 * google.maps.OverlayView();</code>. The <code>OverlayView</code> constructor
 * is guaranteed to be an empty function. <br><br>You must implement three
 * methods: <code>onAdd()</code>, <code>draw()</code>, and
 * <code>onRemove()</code>. <ul> <li>In the <code>onAdd()</code> method, you
 * should create DOM objects and append them as children of the panes.</li>
 * <li>In the <code>draw()</code> method, you should position these
 * elements.</li> <li>In the <code>onRemove()</code> method, you should remove
 * the objects from the DOM.</li> </ul> You must call <code>setMap()</code> with
 * a valid <code>Map</code> object to trigger the call to the
 * <code>onAdd()</code> method and <code>setMap(null)</code> in order to trigger
 * the <code>onRemove()</code> method. The <code>setMap()</code> method can be
 * called at the time of construction or at any point afterward when the overlay
 * should be re-shown after removing. The <code>draw()</code> method will then
 * be called whenever a map property changes that could change the position of
 * the element, such as zoom, center, or map type.
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.OverlayView = function() {};

/**
 * Stops click, tap, drag, and wheel events on the element from bubbling up to
 * the map. Use this to prevent map dragging and zooming, as well as map
 * &quot;click&quot; events.
 * @param {!Element} element
 * @return {undefined}
 */
google.maps.OverlayView.preventMapHitsAndGesturesFrom = function(element) {};

/**
 * Stops click or tap on the element from bubbling up to the map. Use this to
 * prevent the map from triggering &quot;click&quot; events.
 * @param {!Element} element
 * @return {undefined}
 */
google.maps.OverlayView.preventMapHitsFrom = function(element) {};

/**
 * Implement this method to draw or update the overlay. Use the position from
 * projection.fromLatLngToDivPixel() to correctly position the overlay relative
 * to the MapPanes. This method is called after onAdd(), and is called on change
 * of zoom or center. It is not recommended to do computationally expensive work
 * in this method.
 * @return {undefined}
 */
google.maps.OverlayView.prototype.draw = function() {};

/**
 * @return {google.maps.Map|google.maps.StreetViewPanorama}
 */
google.maps.OverlayView.prototype.getMap = function() {};

/**
 * Returns the panes in which this OverlayView can be rendered. The panes are
 * not initialized until <code>onAdd</code> is called by the API.
 * @return {?google.maps.MapPanes}
 */
google.maps.OverlayView.prototype.getPanes = function() {};

/**
 * Returns the <code>MapCanvasProjection</code> object associated with this
 * <code>OverlayView</code>. The projection is not initialized until
 * <code>onAdd</code> is called by the API.
 * @return {!google.maps.MapCanvasProjection}
 */
google.maps.OverlayView.prototype.getProjection = function() {};

/**
 * Implement this method to initialize the overlay DOM elements. This method is
 * called once after setMap() is called with a valid map. At this point, panes
 * and projection will have been initialized.
 * @return {undefined}
 */
google.maps.OverlayView.prototype.onAdd = function() {};

/**
 * Implement this method to remove your elements from the DOM. This method is
 * called once following a call to setMap(null).
 * @return {undefined}
 */
google.maps.OverlayView.prototype.onRemove = function() {};

/**
 * Adds the overlay to the map or panorama.
 * @param {google.maps.Map|google.maps.StreetViewPanorama} map
 * @return {undefined}
 */
google.maps.OverlayView.prototype.setMap = function(map) {};

/**
 * @record
 */
google.maps.Padding = function() {};

/**
 * Padding for the bottom, in pixels.
 * @type {number|undefined}
 */
google.maps.Padding.prototype.bottom;

/**
 * Padding for the left, in pixels.
 * @type {number|undefined}
 */
google.maps.Padding.prototype.left;

/**
 * Padding for the right, in pixels.
 * @type {number|undefined}
 */
google.maps.Padding.prototype.right;

/**
 * Padding for the top, in pixels.
 * @type {number|undefined}
 */
google.maps.Padding.prototype.top;

/**
 * Options for the rendering of the pan control.
 * @record
 */
google.maps.PanControlOptions = function() {};

/**
 * Position id. Used to specify the position of the control on the map. The
 * default position is <code>TOP_LEFT</code>.
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.PanControlOptions.prototype.position;

/**
 * Options for the Custom Pano Provider.
 * @record
 */
google.maps.PanoProviderOptions = function() {};

/**
 * If set, the renderer will use technologies (like webgl) that only work when
 * cors headers are appropiately set on the provided images. It is the
 * developer&#39;s task to serve the images correctly in combination with this
 * flag, which might otherwise lead to SecurityErrors.
 * @type {boolean|undefined}
 */
google.maps.PanoProviderOptions.prototype.cors;

/**
 * An elevation query sent by the <code>ElevationService</code> containing the
 * path along which to return sampled data. This request defines a continuous
 * path along the earth along which elevation samples should be taken at
 * evenly-spaced distances. All paths from vertex to vertex use segments of the
 * great circle between those two points.
 * @record
 */
google.maps.PathElevationRequest = function() {};

/**
 * The path along which to collect elevation values.
 * @type {?Array<!google.maps.LatLng|!google.maps.LatLngLiteral>|undefined}
 */
google.maps.PathElevationRequest.prototype.path;

/**
 * Required. The number of equidistant points along the given path for which to
 * retrieve elevation data, including the endpoints. The number of samples must
 * be a value between 2 and 512 inclusive.
 * @type {number}
 */
google.maps.PathElevationRequest.prototype.samples;

/**
 * An elevation response returned by the {@link google.maps.ElevationService}
 * containing the list of {@link google.maps.ElevationResult}s evenly-spaced
 * along the path of the {@link google.maps.PathElevationRequest}.
 * @record
 */
google.maps.PathElevationResponse = function() {};

/**
 * The list of {@link google.maps.ElevationResult}s matching the samples of
 * the {@link google.maps.PathElevationRequest}.
 * @type {!Array<!google.maps.ElevationResult>}
 */
google.maps.PathElevationResponse.prototype.results;

/**
 * Contains information needed to locate, identify, or describe a place for
 * a {@link google.maps.DirectionsRequest} or {@link
 * google.maps.DistanceMatrixRequest}. In this context, &quot;place&quot; means
 * a business, point of interest, or geographic location. For fetching
 * information about a place, see {@link google.maps.places.PlacesService}.
 * @record
 */
google.maps.Place = function() {};

/**
 * The <code>LatLng</code> of the entity described by this place.
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.Place.prototype.location;

/**
 * The place ID of the place (such as a business or point of interest). The
 * place ID is a unique identifier of a place in the Google Maps database. Note
 * that the <code>placeId</code> is the most accurate way of identifying a
 * place. If possible, you should specify the <code>placeId</code> rather than a
 * <code>query</code>. A place ID can be retrieved from any request to the
 * Places API, such as a <a
 * href="https://developers.google.com/maps/documentation/places/web-service/search">TextSearch</a>.
 * Place IDs can also be retrieved from requests to the Geocoding API. For more
 * information, see the <a
 * href="https://developers.google.com/maps/documentation/places/web-service/place-id">overview
 * of place IDs</a>.
 * @type {string|undefined}
 */
google.maps.Place.prototype.placeId;

/**
 * A search query describing the place (such as a business or point of
 * interest). An example query is &quot;Quay, Upper Level, Overseas Passenger
 * Terminal 5 Hickson Road, The Rocks NSW&quot;. If possible, you should specify
 * the <code>placeId</code> rather than a <code>query</code>. The API does not
 * guarantee the accuracy of resolving the query string to a place. If both the
 * <code>placeId</code> and <code>query</code> are provided, an error occurs.
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
 * The X coordinate
 * @type {number}
 */
google.maps.Point.prototype.x;

/**
 * The Y coordinate
 * @type {number}
 */
google.maps.Point.prototype.y;

/**
 * Compares two Points
 * @param {google.maps.Point} other
 * @return {boolean}
 */
google.maps.Point.prototype.equals = function(other) {};

/**
 * Returns a string representation of this Point.
 * @return {string}
 * @override
 */
google.maps.Point.prototype.toString = function() {};

/**
 * This object is returned from mouse events on polylines and polygons.
 * @extends {google.maps.MapMouseEvent}
 * @record
 */
google.maps.PolyMouseEvent = function() {};

/**
 * The index of the edge within the path beneath the cursor when the event
 * occurred, if the event occurred on a mid-point on an editable polygon.
 * @type {number|undefined}
 */
google.maps.PolyMouseEvent.prototype.edge;

/**
 * The index of the path beneath the cursor when the event occurred, if the
 * event occurred on a vertex and the polygon is editable. Otherwise
 * <code>undefined</code>.
 * @type {number|undefined}
 */
google.maps.PolyMouseEvent.prototype.path;

/**
 * The index of the vertex beneath the cursor when the event occurred, if the
 * event occurred on a vertex and the polyline or polygon is editable. If the
 * event does not occur on a vertex, the value is <code>undefined</code>.
 * @type {number|undefined}
 */
google.maps.PolyMouseEvent.prototype.vertex;

/**
 * A polygon (like a polyline) defines a series of connected coordinates in an
 * ordered sequence. Additionally, polygons form a closed loop and define a
 * filled region. See the samples in the developer&#39;s guide, starting with a
 * <a
 * href="https://developers.google.com/maps/documentation/javascript/examples/polygon-simple">simple
 * polygon</a>, a <a
 * href="https://developers.google.com/maps/documentation/javascript/examples/polygon-hole">polygon
 * with a hole</a>, and more. Note that you can also use the <a
 * href="#Data.Polygon">Data layer</a> to create a polygon. The Data layer
 * offers a simpler way of creating holes because it handles the order of the
 * inner and outer paths for you.
 * @param {?google.maps.PolygonOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.Polygon = function(opts) {};

/**
 * Returns whether this shape can be dragged by the user.
 * @return {boolean}
 */
google.maps.Polygon.prototype.getDraggable = function() {};

/**
 * Returns whether this shape can be edited by the user.
 * @return {boolean}
 */
google.maps.Polygon.prototype.getEditable = function() {};

/**
 * Returns the map on which this shape is attached.
 * @return {?google.maps.Map}
 */
google.maps.Polygon.prototype.getMap = function() {};

/**
 * Retrieves the first path.
 * @return {!google.maps.MVCArray<!google.maps.LatLng>}
 */
google.maps.Polygon.prototype.getPath = function() {};

/**
 * Retrieves the paths for this polygon.
 * @return {!google.maps.MVCArray<!google.maps.MVCArray<!google.maps.LatLng>>}
 */
google.maps.Polygon.prototype.getPaths = function() {};

/**
 * Returns whether this poly is visible on the map.
 * @return {boolean}
 */
google.maps.Polygon.prototype.getVisible = function() {};

/**
 * If set to <code>true</code>, the user can drag this shape over the map. The
 * <code>geodesic</code> property defines the mode of dragging.
 * @param {boolean} draggable
 * @return {undefined}
 */
google.maps.Polygon.prototype.setDraggable = function(draggable) {};

/**
 * If set to <code>true</code>, the user can edit this shape by dragging the
 * control points shown at the vertices and on each segment.
 * @param {boolean} editable
 * @return {undefined}
 */
google.maps.Polygon.prototype.setEditable = function(editable) {};

/**
 * Renders this shape on the specified map. If map is set to <code>null</code>,
 * the shape will be removed.
 * @param {?google.maps.Map} map
 * @return {undefined}
 */
google.maps.Polygon.prototype.setMap = function(map) {};

/**
 * @param {?google.maps.PolygonOptions} options
 * @return {undefined}
 */
google.maps.Polygon.prototype.setOptions = function(options) {};

/**
 * Sets the first path. See <em><code><a
 * href="#PolygonOptions">PolygonOptions</a></code></em> for more details.
 * @param {!google.maps.MVCArray<!google.maps.LatLng>|!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>}
 *     path
 * @return {undefined}
 */
google.maps.Polygon.prototype.setPath = function(path) {};

/**
 * Sets the path for this polygon.
 * @param {!google.maps.MVCArray<!google.maps.MVCArray<!google.maps.LatLng>>|!google.maps.MVCArray<!google.maps.LatLng>|!Array<!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>>|!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>}
 *     paths
 * @return {undefined}
 */
google.maps.Polygon.prototype.setPaths = function(paths) {};

/**
 * Hides this poly if set to <code>false</code>.
 * @param {boolean} visible
 * @return {undefined}
 */
google.maps.Polygon.prototype.setVisible = function(visible) {};

/**
 * PolygonOptions object used to define the properties that can be set on a
 * Polygon.
 * @record
 */
google.maps.PolygonOptions = function() {};

/**
 * Indicates whether this <code>Polygon</code> handles mouse events. Defaults to
 * <code>true</code>.
 * @type {?boolean|undefined}
 */
google.maps.PolygonOptions.prototype.clickable;

/**
 * If set to <code>true</code>, the user can drag this shape over the map. The
 * <code>geodesic</code> property defines the mode of dragging. Defaults to
 * <code>false</code>.
 * @type {?boolean|undefined}
 */
google.maps.PolygonOptions.prototype.draggable;

/**
 * If set to <code>true</code>, the user can edit this shape by dragging the
 * control points shown at the vertices and on each segment. Defaults to
 * <code>false</code>.
 * @type {?boolean|undefined}
 */
google.maps.PolygonOptions.prototype.editable;

/**
 * The fill color. All CSS3 colors are supported except for extended named
 * colors.
 * @type {?string|undefined}
 */
google.maps.PolygonOptions.prototype.fillColor;

/**
 * The fill opacity between 0.0 and 1.0
 * @type {?number|undefined}
 */
google.maps.PolygonOptions.prototype.fillOpacity;

/**
 * When <code>true</code>, edges of the polygon are interpreted as geodesic and
 * will follow the curvature of the Earth. When <code>false</code>, edges of the
 * polygon are rendered as straight lines in screen space. Note that the shape
 * of a geodesic polygon may appear to change when dragged, as the dimensions
 * are maintained relative to the surface of the earth. Defaults to
 * <code>false</code>.
 * @type {?boolean|undefined}
 */
google.maps.PolygonOptions.prototype.geodesic;

/**
 * Map on which to display Polygon.
 * @type {?google.maps.Map|undefined}
 */
google.maps.PolygonOptions.prototype.map;

/**
 * The ordered sequence of coordinates that designates a closed loop. Unlike
 * polylines, a polygon may consist of one or more paths. As a result, the paths
 * property may specify one or more arrays of <code>LatLng</code> coordinates.
 * Paths are closed automatically; do not repeat the first vertex of the path as
 * the last vertex. Simple polygons may be defined using a single array of
 * <code>LatLng</code>s. More complex polygons may specify an array of arrays.
 * Any simple arrays are converted into <code><a
 * href="#MVCArray">MVCArray</a></code>s. Inserting or removing
 * <code>LatLng</code>s from the <code>MVCArray</code> will automatically update
 * the polygon on the map.
 * @type {?google.maps.MVCArray<!google.maps.MVCArray<!google.maps.LatLng>>|?google.maps.MVCArray<!google.maps.LatLng>|?Array<!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>>|?Array<!google.maps.LatLng|!google.maps.LatLngLiteral>|undefined}
 */
google.maps.PolygonOptions.prototype.paths;

/**
 * The stroke color. All CSS3 colors are supported except for extended named
 * colors.
 * @type {?string|undefined}
 */
google.maps.PolygonOptions.prototype.strokeColor;

/**
 * The stroke opacity between 0.0 and 1.0
 * @type {?number|undefined}
 */
google.maps.PolygonOptions.prototype.strokeOpacity;

/**
 * The stroke position. Defaults to CENTER. This property is not supported on
 * Internet Explorer 8 and earlier.
 * @type {?google.maps.StrokePosition|undefined}
 */
google.maps.PolygonOptions.prototype.strokePosition;

/**
 * The stroke width in pixels.
 * @type {?number|undefined}
 */
google.maps.PolygonOptions.prototype.strokeWeight;

/**
 * Whether this polygon is visible on the map. Defaults to <code>true</code>.
 * @type {?boolean|undefined}
 */
google.maps.PolygonOptions.prototype.visible;

/**
 * The zIndex compared to other polys.
 * @type {?number|undefined}
 */
google.maps.PolygonOptions.prototype.zIndex;

/**
 * A polyline is a linear overlay of connected line segments on the map.
 * @param {?google.maps.PolylineOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.Polyline = function(opts) {};

/**
 * Returns whether this shape can be dragged by the user.
 * @return {boolean}
 */
google.maps.Polyline.prototype.getDraggable = function() {};

/**
 * Returns whether this shape can be edited by the user.
 * @return {boolean}
 */
google.maps.Polyline.prototype.getEditable = function() {};

/**
 * Returns the map on which this shape is attached.
 * @return {?google.maps.Map}
 */
google.maps.Polyline.prototype.getMap = function() {};

/**
 * Retrieves the path.
 * @return {!google.maps.MVCArray<!google.maps.LatLng>}
 */
google.maps.Polyline.prototype.getPath = function() {};

/**
 * Returns whether this poly is visible on the map.
 * @return {boolean}
 */
google.maps.Polyline.prototype.getVisible = function() {};

/**
 * If set to <code>true</code>, the user can drag this shape over the map. The
 * <code>geodesic</code> property defines the mode of dragging.
 * @param {boolean} draggable
 * @return {undefined}
 */
google.maps.Polyline.prototype.setDraggable = function(draggable) {};

/**
 * If set to <code>true</code>, the user can edit this shape by dragging the
 * control points shown at the vertices and on each segment.
 * @param {boolean} editable
 * @return {undefined}
 */
google.maps.Polyline.prototype.setEditable = function(editable) {};

/**
 * Renders this shape on the specified map. If map is set to <code>null</code>,
 * the shape will be removed.
 * @param {?google.maps.Map} map
 * @return {undefined}
 */
google.maps.Polyline.prototype.setMap = function(map) {};

/**
 * @param {?google.maps.PolylineOptions} options
 * @return {undefined}
 */
google.maps.Polyline.prototype.setOptions = function(options) {};

/**
 * Sets the path. See <em><code><a
 * href="#PolylineOptions">PolylineOptions</a></code></em> for more details.
 * @param {!google.maps.MVCArray<!google.maps.LatLng>|!Array<!google.maps.LatLng|!google.maps.LatLngLiteral>}
 *     path
 * @return {undefined}
 */
google.maps.Polyline.prototype.setPath = function(path) {};

/**
 * Hides this poly if set to <code>false</code>.
 * @param {boolean} visible
 * @return {undefined}
 */
google.maps.Polyline.prototype.setVisible = function(visible) {};

/**
 * PolylineOptions object used to define the properties that can be set on a
 * Polyline.
 * @record
 */
google.maps.PolylineOptions = function() {};

/**
 * Indicates whether this <code>Polyline</code> handles mouse events. Defaults
 * to <code>true</code>.
 * @type {?boolean|undefined}
 */
google.maps.PolylineOptions.prototype.clickable;

/**
 * If set to <code>true</code>, the user can drag this shape over the map. The
 * <code>geodesic</code> property defines the mode of dragging. Defaults to
 * <code>false</code>.
 * @type {?boolean|undefined}
 */
google.maps.PolylineOptions.prototype.draggable;

/**
 * If set to <code>true</code>, the user can edit this shape by dragging the
 * control points shown at the vertices and on each segment. Defaults to
 * <code>false</code>.
 * @type {?boolean|undefined}
 */
google.maps.PolylineOptions.prototype.editable;

/**
 * When <code>true</code>, edges of the polygon are interpreted as geodesic and
 * will follow the curvature of the Earth. When <code>false</code>, edges of the
 * polygon are rendered as straight lines in screen space. Note that the shape
 * of a geodesic polygon may appear to change when dragged, as the dimensions
 * are maintained relative to the surface of the earth. Defaults to
 * <code>false</code>.
 * @type {?boolean|undefined}
 */
google.maps.PolylineOptions.prototype.geodesic;

/**
 * The icons to be rendered along the polyline.
 * @type {?Array<!google.maps.IconSequence>|undefined}
 */
google.maps.PolylineOptions.prototype.icons;

/**
 * Map on which to display Polyline.
 * @type {?google.maps.Map|undefined}
 */
google.maps.PolylineOptions.prototype.map;

/**
 * The ordered sequence of coordinates of the Polyline. This path may be
 * specified using either a simple array of <code>LatLng</code>s, or an
 * <code>MVCArray</code> of <code>LatLng</code>s. Note that if you pass a simple
 * array, it will be converted to an <code>MVCArray</code> Inserting or removing
 * LatLngs in the <code>MVCArray</code> will automatically update the polyline
 * on the map.
 * @type {?google.maps.MVCArray<!google.maps.LatLng>|?Array<!google.maps.LatLng|!google.maps.LatLngLiteral>|undefined}
 */
google.maps.PolylineOptions.prototype.path;

/**
 * The stroke color. All CSS3 colors are supported except for extended named
 * colors.
 * @type {?string|undefined}
 */
google.maps.PolylineOptions.prototype.strokeColor;

/**
 * The stroke opacity between 0.0 and 1.0.
 * @type {?number|undefined}
 */
google.maps.PolylineOptions.prototype.strokeOpacity;

/**
 * The stroke width in pixels.
 * @type {?number|undefined}
 */
google.maps.PolylineOptions.prototype.strokeWeight;

/**
 * Whether this polyline is visible on the map. Defaults to <code>true</code>.
 * @type {?boolean|undefined}
 */
google.maps.PolylineOptions.prototype.visible;

/**
 * The zIndex compared to other polys.
 * @type {?number|undefined}
 */
google.maps.PolylineOptions.prototype.zIndex;

/**
 * @record
 */
google.maps.Projection = function() {};

/**
 * Translates from the LatLng cylinder to the Point plane. This interface
 * specifies a function which implements translation from given
 * <code>LatLng</code> values to world coordinates on the map projection. The
 * Maps API calls this method when it needs to plot locations on screen.
 * <code>Projection</code> objects must implement this method, but may return
 * <code>null</code> if the projection cannot calculate the <code>Point</code>.
 * @param {!google.maps.LatLng} latLng
 * @param {!google.maps.Point=} point
 * @return {?google.maps.Point}
 */
google.maps.Projection.prototype.fromLatLngToPoint = function(latLng, point) {};

/**
 * This interface specifies a function which implements translation from world
 * coordinates on a map projection to <code>LatLng</code> values. The Maps API
 * calls this method when it needs to translate actions on screen to positions
 * on the map. <code>Projection</code> objects must implement this method, but
 * may return <code>null</code> if the projection cannot calculate the
 * <code>LatLng</code>.
 * @param {!google.maps.Point} pixel
 * @param {boolean=} noWrap
 * @return {?google.maps.LatLng}
 */
google.maps.Projection.prototype.fromPointToLatLng = function(pixel, noWrap) {};

/**
 * A rectangle overlay.
 * @param {google.maps.RectangleOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.Rectangle = function(opts) {};

/**
 * Returns the bounds of this rectangle.
 * @return {google.maps.LatLngBounds}
 */
google.maps.Rectangle.prototype.getBounds = function() {};

/**
 * Returns whether this rectangle can be dragged by the user.
 * @return {boolean}
 */
google.maps.Rectangle.prototype.getDraggable = function() {};

/**
 * Returns whether this rectangle can be edited by the user.
 * @return {boolean}
 */
google.maps.Rectangle.prototype.getEditable = function() {};

/**
 * Returns the map on which this rectangle is displayed.
 * @return {google.maps.Map}
 */
google.maps.Rectangle.prototype.getMap = function() {};

/**
 * Returns whether this rectangle is visible on the map.
 * @return {boolean}
 */
google.maps.Rectangle.prototype.getVisible = function() {};

/**
 * Sets the bounds of this rectangle.
 * @param {google.maps.LatLngBounds|google.maps.LatLngBoundsLiteral} bounds
 * @return {undefined}
 */
google.maps.Rectangle.prototype.setBounds = function(bounds) {};

/**
 * If set to <code>true</code>, the user can drag this rectangle over the map.
 * @param {boolean} draggable
 * @return {undefined}
 */
google.maps.Rectangle.prototype.setDraggable = function(draggable) {};

/**
 * If set to <code>true</code>, the user can edit this rectangle by dragging the
 * control points shown at the corners and on each edge.
 * @param {boolean} editable
 * @return {undefined}
 */
google.maps.Rectangle.prototype.setEditable = function(editable) {};

/**
 * Renders the rectangle on the specified map. If map is set to
 * <code>null</code>, the rectangle will be removed.
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
 * Hides this rectangle if set to <code>false</code>.
 * @param {boolean} visible
 * @return {undefined}
 */
google.maps.Rectangle.prototype.setVisible = function(visible) {};

/**
 * RectangleOptions object used to define the properties that can be set on a
 * Rectangle.
 * @record
 */
google.maps.RectangleOptions = function() {};

/**
 * The bounds.
 * @type {?google.maps.LatLngBounds|?google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.RectangleOptions.prototype.bounds;

/**
 * Indicates whether this <code>Rectangle</code> handles mouse events. Defaults
 * to <code>true</code>.
 * @type {?boolean|undefined}
 */
google.maps.RectangleOptions.prototype.clickable;

/**
 * If set to <code>true</code>, the user can drag this rectangle over the map.
 * Defaults to <code>false</code>.
 * @type {?boolean|undefined}
 */
google.maps.RectangleOptions.prototype.draggable;

/**
 * If set to <code>true</code>, the user can edit this rectangle by dragging the
 * control points shown at the corners and on each edge. Defaults to
 * <code>false</code>.
 * @type {?boolean|undefined}
 */
google.maps.RectangleOptions.prototype.editable;

/**
 * The fill color. All CSS3 colors are supported except for extended named
 * colors.
 * @type {?string|undefined}
 */
google.maps.RectangleOptions.prototype.fillColor;

/**
 * The fill opacity between 0.0 and 1.0
 * @type {?number|undefined}
 */
google.maps.RectangleOptions.prototype.fillOpacity;

/**
 * Map on which to display Rectangle.
 * @type {?google.maps.Map|undefined}
 */
google.maps.RectangleOptions.prototype.map;

/**
 * The stroke color. All CSS3 colors are supported except for extended named
 * colors.
 * @type {?string|undefined}
 */
google.maps.RectangleOptions.prototype.strokeColor;

/**
 * The stroke opacity between 0.0 and 1.0
 * @type {?number|undefined}
 */
google.maps.RectangleOptions.prototype.strokeOpacity;

/**
 * The stroke position. Defaults to CENTER. This property is not supported on
 * Internet Explorer 8 and earlier.
 * @type {?google.maps.StrokePosition|undefined}
 */
google.maps.RectangleOptions.prototype.strokePosition;

/**
 * The stroke width in pixels.
 * @type {?number|undefined}
 */
google.maps.RectangleOptions.prototype.strokeWeight;

/**
 * Whether this rectangle is visible on the map. Defaults to <code>true</code>.
 * @type {?boolean|undefined}
 */
google.maps.RectangleOptions.prototype.visible;

/**
 * The zIndex compared to other polys.
 * @type {?number|undefined}
 */
google.maps.RectangleOptions.prototype.zIndex;

/**
 * Options for the rendering of the rotate control.
 * @record
 */
google.maps.RotateControlOptions = function() {};

/**
 * Position id. Used to specify the position of the control on the map. The
 * default position is <code>TOP_LEFT</code>.
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.RotateControlOptions.prototype.position;

/**
 * Options for the rendering of the scale control.
 * @record
 */
google.maps.ScaleControlOptions = function() {};

/**
 * Style id. Used to select what style of scale control to display.
 * @type {?google.maps.ScaleControlStyle|undefined}
 */
google.maps.ScaleControlOptions.prototype.style;

/**
 * Identifiers for scale control ids.
 * @enum {number}
 */
google.maps.ScaleControlStyle = {
  /**
   * The standard scale control.
   */
  DEFAULT: 0,
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
 * The height along the y-axis, in pixels.
 * @type {number}
 */
google.maps.Size.prototype.height;

/**
 * The width along the x-axis, in pixels.
 * @type {number}
 */
google.maps.Size.prototype.width;

/**
 * Compares two Sizes.
 * @param {google.maps.Size} other
 * @return {boolean}
 */
google.maps.Size.prototype.equals = function(other) {};

/**
 * Returns a string representation of this Size.
 * @return {string}
 * @override
 */
google.maps.Size.prototype.toString = function() {};

/**
 * Options for the rendering of the Street View address control.
 * @record
 */
google.maps.StreetViewAddressControlOptions = function() {};

/**
 * Position id. This id is used to specify the position of the control on the
 * map. The default position is <code>TOP_LEFT</code>.
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.StreetViewAddressControlOptions.prototype.position;

/**
 * Options for the rendering of the Street View pegman control on the map.
 * @record
 */
google.maps.StreetViewControlOptions = function() {};

/**
 * Position id. Used to specify the position of the control on the map. The
 * default position is embedded within the navigation (zoom and pan) controls.
 * If this position is empty or the same as that specified in the
 * <code>zoomControlOptions</code> or <code>panControlOptions</code>, the Street
 * View control will be displayed as part of the navigation controls. Otherwise,
 * it will be displayed separately.
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.StreetViewControlOptions.prototype.position;

/**
 * A layer that illustrates the locations where Street View is available.
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.StreetViewCoverageLayer = function() {};

/**
 * Returns the map on which this layer is displayed.
 * @return {google.maps.Map}
 */
google.maps.StreetViewCoverageLayer.prototype.getMap = function() {};

/**
 * Renders the layer on the specified map. If the map is set to null, the layer
 * will be removed.
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.StreetViewCoverageLayer.prototype.setMap = function(map) {};

/**
 * A collection of references to adjacent Street View panos.
 * @record
 */
google.maps.StreetViewLink = function() {};

/**
 * A localized string describing the link.
 * @type {?string}
 */
google.maps.StreetViewLink.prototype.description;

/**
 * The heading of the link.
 * @type {?number}
 */
google.maps.StreetViewLink.prototype.heading;

/**
 * A unique identifier for the panorama. This id is stable within a session but
 * unstable across sessions.
 * @type {?string}
 */
google.maps.StreetViewLink.prototype.pano;

/**
 * A representation of a location in the Street View panorama.
 * @record
 */
google.maps.StreetViewLocation = function() {};

/**
 * A localized string describing the location.
 * @type {?string|undefined}
 */
google.maps.StreetViewLocation.prototype.description;

/**
 * The latlng of the panorama.
 * @type {?google.maps.LatLng|undefined}
 */
google.maps.StreetViewLocation.prototype.latLng;

/**
 * A unique identifier for the panorama. This is stable within a session but
 * unstable across sessions.
 * @type {string}
 */
google.maps.StreetViewLocation.prototype.pano;

/**
 * Short description of the location.
 * @type {?string|undefined}
 */
google.maps.StreetViewLocation.prototype.shortDescription;

/**
 * A Street View request to be sent with <code>getPanorama</code>.
 * <code>StreetViewLocationRequest</code> lets you search for a Street View
 * panoroma at a specified location.
 * @record
 */
google.maps.StreetViewLocationRequest = function() {};

/**
 * Specifies the location where to search for a Street View panorama.
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.StreetViewLocationRequest.prototype.location;

/**
 * Sets a preference for which panorama should be found within the radius: the
 * one nearest to the provided location, or the best one within the radius.
 * @type {?google.maps.StreetViewPreference|undefined}
 */
google.maps.StreetViewLocationRequest.prototype.preference;

/**
 * Sets a radius in meters in which to search for a panorama. Defaults to 50
 * when not supplied.
 * @type {?number|undefined}
 */
google.maps.StreetViewLocationRequest.prototype.radius;

/**
 * Specifies the source of panoramas to search. This allows a restriction to
 * search for just outdoor panoramas for example. If not specified it is set to
 * <code>DEFAULT</code>.
 * @type {?google.maps.StreetViewSource|undefined}
 */
google.maps.StreetViewLocationRequest.prototype.source;

/**
 * A <code>StreetViewPanoRequest</code> is used with the
 * <code>getPanorama</code> to find a panorama with a specified ID.
 * @record
 */
google.maps.StreetViewPanoRequest = function() {};

/**
 * Specifies the pano ID to search for.
 * @type {?string|undefined}
 */
google.maps.StreetViewPanoRequest.prototype.pano;

/**
 * Displays the panorama for a given <code>LatLng</code> or panorama ID. A
 * <code>StreetViewPanorama</code> object provides a Street View
 * &quot;viewer&quot; which can be stand-alone within a separate
 * <code>&lt;div&gt;</code> or bound to a <code>Map</code>.
 * @param {!Element} container
 * @param {google.maps.StreetViewPanoramaOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.StreetViewPanorama = function(container, opts) {};

/**
 * Additional controls to attach to the panorama. To add a control to the
 * panorama, add the control&#39;s <code>&lt;div&gt;</code> to the
 * <code>MVCArray</code> corresponding to the <code>ControlPosition</code> where
 * it should be rendered.
 * @type {!Array<google.maps.MVCArray<Node>>}
 */
google.maps.StreetViewPanorama.prototype.controls;

/**
 * Returns the set of navigation links for the Street View panorama.
 * @return {Array<google.maps.StreetViewLink>}
 */
google.maps.StreetViewPanorama.prototype.getLinks = function() {};

/**
 * Returns the StreetViewLocation of the current panorama.
 * @return {google.maps.StreetViewLocation}
 */
google.maps.StreetViewPanorama.prototype.getLocation = function() {};

/**
 * Returns the state of motion tracker. If <code>true</code> when the user
 * physically moves the device and the browser supports it, the Street View
 * Panorama tracks the physical movements.
 * @return {boolean}
 */
google.maps.StreetViewPanorama.prototype.getMotionTracking = function() {};

/**
 * Returns the current panorama ID for the Street View panorama. This id is
 * stable within the browser&#39;s current session only.
 * @return {string}
 */
google.maps.StreetViewPanorama.prototype.getPano = function() {};

/**
 * Returns the heading and pitch of the photographer when this panorama was
 * taken. For Street View panoramas on the road, this also reveals in which
 * direction the car was travelling. This data is available after the
 * <code>pano_changed</code> event.
 * @return {!google.maps.StreetViewPov}
 */
google.maps.StreetViewPanorama.prototype.getPhotographerPov = function() {};

/**
 * Returns the current <code>LatLng</code> position for the Street View
 * panorama.
 * @return {google.maps.LatLng}
 */
google.maps.StreetViewPanorama.prototype.getPosition = function() {};

/**
 * Returns the current point of view for the Street View panorama.
 * @return {!google.maps.StreetViewPov}
 */
google.maps.StreetViewPanorama.prototype.getPov = function() {};

/**
 * Returns the status of the panorama on completion of the
 * <code>setPosition()</code> or <code>setPano()</code> request.
 * @return {google.maps.StreetViewStatus}
 */
google.maps.StreetViewPanorama.prototype.getStatus = function() {};

/**
 * Returns <code>true</code> if the panorama is visible. It does not specify
 * whether Street View imagery is available at the specified position.
 * @return {boolean}
 */
google.maps.StreetViewPanorama.prototype.getVisible = function() {};

/**
 * Returns the zoom level of the panorama. Fully zoomed-out is level 0, where
 * the field of view is 180 degrees. Zooming in increases the zoom level.
 * @return {number}
 */
google.maps.StreetViewPanorama.prototype.getZoom = function() {};

/**
 * Set the custom panorama provider called on pano change to load custom
 * panoramas.
 * @param {function(string): ?google.maps.StreetViewPanoramaData} provider
 * @param {!google.maps.PanoProviderOptions=} opt_options
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.registerPanoProvider = function(
    provider, opt_options) {};

/**
 * Sets the set of navigation links for the Street View panorama.
 * @param {Array<google.maps.StreetViewLink>} links
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setLinks = function(links) {};

/**
 * Sets the state of motion tracker. If <code>true</code> when the user
 * physically moves the device and the browser supports it, the Street View
 * Panorama tracks the physical movements.
 * @param {boolean} motionTracking
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setMotionTracking = function(
    motionTracking) {};

/**
 * Sets a collection of key-value pairs.
 * @param {google.maps.StreetViewPanoramaOptions} options
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setOptions = function(options) {};

/**
 * Sets the current panorama ID for the Street View panorama.
 * @param {string} pano
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setPano = function(pano) {};

/**
 * Sets the current <code>LatLng</code> position for the Street View panorama.
 * @param {google.maps.LatLng|google.maps.LatLngLiteral} latLng
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setPosition = function(latLng) {};

/**
 * Sets the point of view for the Street View panorama.
 * @param {!google.maps.StreetViewPov} pov
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setPov = function(pov) {};

/**
 * Sets to <code>true</code> to make the panorama visible. If set to
 * <code>false</code>, the panorama will be hidden whether it is embedded in the
 * map or in its own <code>&lt;div&gt;</code>.
 * @param {boolean} flag
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setVisible = function(flag) {};

/**
 * Sets the zoom level of the panorama. Fully zoomed-out is level 0, where the
 * field of view is 180 degrees. Zooming in increases the zoom level.
 * @param {number} zoom
 * @return {undefined}
 */
google.maps.StreetViewPanorama.prototype.setZoom = function(zoom) {};

/**
 * The representation of a panorama returned from the provider defined using
 * <code>registerPanoProvider</code>.
 * @record
 */
google.maps.StreetViewPanoramaData = function() {};

/**
 * Specifies the copyright text for this panorama.
 * @type {string|undefined}
 */
google.maps.StreetViewPanoramaData.prototype.copyright;

/**
 * Specifies the year and month in which the imagery in this panorama was
 * acquired. The date string is in the form YYYY-MM.
 * @type {string|undefined}
 */
google.maps.StreetViewPanoramaData.prototype.imageDate;

/**
 * Specifies the navigational links to adjacent panoramas.
 * @type {!Array<!google.maps.StreetViewLink>|undefined}
 */
google.maps.StreetViewPanoramaData.prototype.links;

/**
 * Specifies the location meta-data for this panorama.
 * @type {!google.maps.StreetViewLocation|undefined}
 */
google.maps.StreetViewPanoramaData.prototype.location;

/**
 * Specifies the custom tiles for this panorama.
 * @type {!google.maps.StreetViewTileData}
 */
google.maps.StreetViewPanoramaData.prototype.tiles;

/**
 * Options defining the properties of a <code>StreetViewPanorama</code> object.
 * @record
 */
google.maps.StreetViewPanoramaOptions = function() {};

/**
 * The enabled/disabled state of the address control.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.addressControl;

/**
 * The display options for the address control.
 * @type {?google.maps.StreetViewAddressControlOptions|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.addressControlOptions;

/**
 * The enabled/disabled state of click-to-go.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.clickToGo;

/**
 * Size in pixels of the controls appearing on the panorama. This value must be
 * supplied directly when creating the Panorama, updating this value later may
 * bring the controls into an undefined state. Only governs the controls made by
 * the Maps API itself. Does not scale developer created custom controls.
 * @type {?number|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.controlSize;

/**
 * Enables/disables all default UI. May be overridden individually.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.disableDefaultUI;

/**
 * Enables/disables zoom on double click. Disabled by default.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.disableDoubleClickZoom;

/**
 * If <code>true</code>, the close button is displayed. Disabled by default.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.enableCloseButton;

/**
 * The enabled/disabled state of the fullscreen control.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.fullscreenControl;

/**
 * The display options for the fullscreen control.
 * @type {?google.maps.FullscreenControlOptions|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.fullscreenControlOptions;

/**
 * The enabled/disabled state of the imagery acquisition date control. Disabled
 * by default.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.imageDateControl;

/**
 * The enabled/disabled state of the links control.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.linksControl;

/**
 * Whether motion tracking is on or off. Enabled by default when the motion
 * tracking control is present, so that the POV (point of view) follows the
 * orientation of the device. This is primarily applicable to mobile devices. If
 * <code>motionTracking</code> is set to <code>false</code> while
 * <code>motionTrackingControl</code> is enabled, the motion tracking control
 * appears but tracking is off. The user can tap the motion tracking control to
 * toggle this option.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.motionTracking;

/**
 * The enabled/disabled state of the motion tracking control. Enabled by default
 * when the device has motion data, so that the control appears on the map. This
 * is primarily applicable to mobile devices.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.motionTrackingControl;

/**
 * The display options for the motion tracking control.
 * @type {?google.maps.MotionTrackingControlOptions|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.motionTrackingControlOptions;

/**
 * The enabled/disabled state of the pan control.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.panControl;

/**
 * The display options for the pan control.
 * @type {?google.maps.PanControlOptions|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.panControlOptions;

/**
 * The panorama ID, which should be set when specifying a custom panorama.
 * @type {?string|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.pano;

/**
 * The <code>LatLng</code> position of the Street View panorama.
 * @type {?google.maps.LatLng|?google.maps.LatLngLiteral|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.position;

/**
 * The camera orientation, specified as heading and pitch, for the panorama.
 * @type {?google.maps.StreetViewPov|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.pov;

/**
 * If <code>false</code>, disables scrollwheel zooming in Street View. The
 * scrollwheel is enabled by default.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.scrollwheel;

/**
 * The display of street names on the panorama. If this value is not specified,
 * or is set to <code>true</code>, street names are displayed on the panorama.
 * If set to <code>false</code>, street names are not displayed.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.showRoadLabels;

/**
 * If <code>true</code>, the Street View panorama is visible on load.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.visible;

/**
 * The zoom of the panorama, specified as a number. A zoom of 0 gives a 180
 * degrees Field of View.
 * @type {?number|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.zoom;

/**
 * The enabled/disabled state of the zoom control.
 * @type {?boolean|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.zoomControl;

/**
 * The display options for the zoom control.
 * @type {?google.maps.ZoomControlOptions|undefined}
 */
google.maps.StreetViewPanoramaOptions.prototype.zoomControlOptions;

/**
 * A point of view object which specifies the camera&#39;s orientation at the
 * Street View panorama&#39;s position. The point of view is defined as heading
 * and pitch.
 * @record
 */
google.maps.StreetViewPov = function() {};

/**
 * The camera heading in degrees relative to <code>true</code> north. True north
 * is 0&deg;, east is 90&deg;, south is 180&deg;, west is 270&deg;.
 * @type {number}
 */
google.maps.StreetViewPov.prototype.heading;

/**
 * The camera pitch in degrees, relative to the street view vehicle. Ranges from
 * 90&deg; (directly upwards) to -90&deg; (directly downwards).
 * @type {number}
 */
google.maps.StreetViewPov.prototype.pitch;

/**
 * Options that bias a search result towards returning a Street View panorama
 * that is nearest to the request location, or a panorama that is considered
 * most likely to be what the user wants to see. Specify these by value, or by
 * using the constant&#39;s name. For example, <code>'best'</code> or
 * <code>google.maps.StreetViewPreference.BEST</code>.
 * @enum {string}
 */
google.maps.StreetViewPreference = {
  /**
   * Return the Street View panorama that is considered most likely to be what
   * the user wants to see. The best result is determined by algorithms based on
   * user research and parameters such as recognised points of interest, image
   * quality, and distance from the given location.
   */
  BEST: 'BEST',
  /**
   * Return the Street View panorama that is the shortest distance from the
   * provided location. This works well only within a limited radius. The
   * recommended radius is 1km or less.
   */
  NEAREST: 'NEAREST',
};

/**
 * The response resolved for a Promise from {@link
 * google.maps.StreetViewService.getPanorama}.
 * @record
 */
google.maps.StreetViewResponse = function() {};

/**
 * The representation of a panorama.
 * @type {!google.maps.StreetViewPanoramaData}
 */
google.maps.StreetViewResponse.prototype.data;

/**
 * A <code>StreetViewService</code> object performs searches for Street View
 * data.
 * @constructor
 */
google.maps.StreetViewService = function() {};

/**
 * Retrieves the <code>StreetViewPanoramaData</code> for a panorama that matches
 * the supplied Street View query request. The
 * <code>StreetViewPanoramaData</code> is passed to the provided callback.
 * <aside class="note">Note: <strong>For the beta release, <code>v=beta</code>,
 * the callback is optional and a Promise is returned</strong>. More information
 * is available in the <a
 * href="https://developers.google.com/maps/documentation/javascript/promises">Promises
 * guide</a>.</aside>
 * @param {!google.maps.StreetViewLocationRequest|!google.maps.StreetViewPanoRequest}
 *     request
 * @param {(function(?google.maps.StreetViewPanoramaData,
 *     !google.maps.StreetViewStatus): void)=} callback
 * @return {?Promise<!google.maps.StreetViewResponse>}
 */
google.maps.StreetViewService.prototype.getPanorama = function(
    request, callback) {};

/**
 * Identifiers to limit Street View searches to selected sources. These values
 * are specified as strings. For example, <code>'outdoor'</code>.
 * @enum {string}
 */
google.maps.StreetViewSource = {
  /**
   * Uses the default sources of Street View, searches will not be limited to
   * specific sources.
   */
  DEFAULT: 'DEFAULT',
  /**
   * Limits Street View searches to outdoor collections. Indoor collections are
   * not included in search results. Note also that the search only returns
   * panoramas where it&#39;s possible to determine whether they&#39;re indoors
   * or outdoors. For example, PhotoSpheres are not returned because it&#39;s
   * unknown whether they are indoors or outdoors.
   */
  OUTDOOR: 'OUTDOOR',
};

/**
 * The status returned by the <code>StreetViewService</code> on completion of a
 * Street View request. These can be specified by value, or by using the
 * constant&#39;s name. For example, <code>'OK'</code> or
 * <code>google.maps.StreetViewStatus.OK</code>.
 * @enum {string}
 */
google.maps.StreetViewStatus = {
  /**
   * The request was successful.
   */
  OK: 'OK',
  /**
   * The request could not be successfully processed, yet the exact reason for
   * failure is unknown.
   */
  UNKNOWN_ERROR: 'UNKNOWN_ERROR',
  /**
   * There are no panoramas found that match the search criteria.
   */
  ZERO_RESULTS: 'ZERO_RESULTS',
};

/**
 * The properties of the tile set used in a Street View panorama.
 * @record
 */
google.maps.StreetViewTileData = function() {};

/**
 * The heading (in degrees) at the center of the panoramic tiles.
 * @type {number}
 */
google.maps.StreetViewTileData.prototype.centerHeading;

/**
 * The size (in pixels) at which tiles will be rendered.
 * @type {!google.maps.Size}
 */
google.maps.StreetViewTileData.prototype.tileSize;

/**
 * The size (in pixels) of the whole panorama&#39;s &quot;world&quot;.
 * @type {!google.maps.Size}
 */
google.maps.StreetViewTileData.prototype.worldSize;

/**
 * Gets the tile image URL for the specified tile.<br/> This is a custom method
 * which you must implement, to supply your custom tiles. The API calls this
 * method, supplying the following parameters:</br> <code>pano</code> is the
 * panorama ID of the Street View tile.<br/> <code>tileZoom</code> is the zoom
 * level of the tile.<br/> <code>tileX</code> is the x-coordinate of the
 * tile.<br/> <code>tileY</code> is the y-coordinate of the tile.<br/> Your
 * custom method must return the URL for the tile image.<br/>
 * @param {string} pano
 * @param {number} tileZoom
 * @param {number} tileX
 * @param {number} tileY
 * @return {string}
 */
google.maps.StreetViewTileData.prototype.getTileUrl = function(
    pano, tileZoom, tileX, tileY) {};

/**
 * The possible positions of the stroke on a polygon.
 * @enum {number}
 */
google.maps.StrokePosition = {
  /**
   * The stroke is centered on the polygon&#39;s path, with half the stroke
   * inside the polygon and half the stroke outside the polygon.
   */
  CENTER: 0,
  /**
   * The stroke lies inside the polygon.
   */
  INSIDE: 1,
  /**
   * The stroke lies outside the polygon.
   */
  OUTSIDE: 2,
};

/**
 * Creates a <code>MapType</code> with a custom style.
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
google.maps.StyledMapType.prototype.getTile = function(
    tileCoord, zoom, ownerDocument) {};

/**
 * @param {Node} tile
 * @return {undefined}
 * @override
 */
google.maps.StyledMapType.prototype.releaseTile = function(tile) {};

/**
 * This class is used to specify options when creating a
 * <code>StyledMapType</code>. These options cannot be changed after the
 * <code>StyledMapType</code> is instantiated.
 * @record
 */
google.maps.StyledMapTypeOptions = function() {};

/**
 * Text to display when this <code>MapType</code>&#39;s button is hovered over
 * in the map type control.
 * @type {?string|undefined}
 */
google.maps.StyledMapTypeOptions.prototype.alt;

/**
 * The maximum zoom level for the map when displaying this <code>MapType</code>.
 * Optional.
 * @type {?number|undefined}
 */
google.maps.StyledMapTypeOptions.prototype.maxZoom;

/**
 * The minimum zoom level for the map when displaying this <code>MapType</code>.
 * Optional.
 * @type {?number|undefined}
 */
google.maps.StyledMapTypeOptions.prototype.minZoom;

/**
 * The name to display in the map type control.
 * @type {?string|undefined}
 */
google.maps.StyledMapTypeOptions.prototype.name;

/**
 * Describes a symbol, which consists of a vector path with styling. A symbol
 * can be used as the icon of a marker, or placed on a polyline.
 * @record
 */
google.maps.Symbol = function() {};

/**
 * The position of the symbol relative to the marker or polyline. The
 * coordinates of the symbol&#39;s path are translated left and up by the
 * anchor&#39;s x and y coordinates respectively. By default, a symbol is
 * anchored at <code>(0, 0)</code>. The position is expressed in the same
 * coordinate system as the symbol&#39;s path.
 * @type {?google.maps.Point|undefined}
 */
google.maps.Symbol.prototype.anchor;

/**
 * The symbol&#39;s fill color. All CSS3 colors are supported except for
 * extended named colors. For symbol markers, this defaults to &#39;black&#39;.
 * For symbols on polylines, this defaults to the stroke color of the
 * corresponding polyline.
 * @type {?string|undefined}
 */
google.maps.Symbol.prototype.fillColor;

/**
 * The symbol&#39;s fill opacity. Defaults to 0.
 * @type {?number|undefined}
 */
google.maps.Symbol.prototype.fillOpacity;

/**
 * The origin of the label relative to the origin of the path, if label is
 * supplied by the marker. By default, the origin is located at <code>(0,
 * 0)</code>. The origin is expressed in the same coordinate system as the
 * symbol&#39;s path. This property is unused for symbols on polylines.
 * @type {?google.maps.Point|undefined}
 */
google.maps.Symbol.prototype.labelOrigin;

/**
 * The symbol&#39;s path, which is a built-in symbol path, or a custom path
 * expressed using <a href="http://www.w3.org/TR/SVG/paths.html#PathData">SVG
 * path notation</a>. Required.
 * @type {!google.maps.SymbolPath|string}
 */
google.maps.Symbol.prototype.path;

/**
 * The angle by which to rotate the symbol, expressed clockwise in degrees.
 * Defaults to 0. A symbol in an <code>IconSequence</code> where
 * <code>fixedRotation</code> is <code>false</code> is rotated relative to the
 * angle of the edge on which it lies.
 * @type {?number|undefined}
 */
google.maps.Symbol.prototype.rotation;

/**
 * The amount by which the symbol is scaled in size. For symbol markers, this
 * defaults to 1; after scaling, the symbol may be of any size. For symbols on a
 * polyline, this defaults to the stroke weight of the polyline; after scaling,
 * the symbol must lie inside a square 22 pixels in size centered at the
 * symbol&#39;s anchor.
 * @type {?number|undefined}
 */
google.maps.Symbol.prototype.scale;

/**
 * The symbol&#39;s stroke color. All CSS3 colors are supported except for
 * extended named colors. For symbol markers, this defaults to &#39;black&#39;.
 * For symbols on a polyline, this defaults to the stroke color of the polyline.
 * @type {?string|undefined}
 */
google.maps.Symbol.prototype.strokeColor;

/**
 * The symbol&#39;s stroke opacity. For symbol markers, this defaults to 1. For
 * symbols on a polyline, this defaults to the stroke opacity of the polyline.
 * @type {?number|undefined}
 */
google.maps.Symbol.prototype.strokeOpacity;

/**
 * The symbol&#39;s stroke weight. Defaults to the <code>scale</code> of the
 * symbol.
 * @type {?number|undefined}
 */
google.maps.Symbol.prototype.strokeWeight;

/**
 * Built-in symbol paths.
 * @enum {number}
 */
google.maps.SymbolPath = {
  /**
   * A backward-pointing closed arrow.
   */
  BACKWARD_CLOSED_ARROW: 0,
  /**
   * A backward-pointing open arrow.
   */
  BACKWARD_OPEN_ARROW: 1,
  /**
   * A circle.
   */
  CIRCLE: 2,
  /**
   * A forward-pointing closed arrow.
   */
  FORWARD_CLOSED_ARROW: 3,
  /**
   * A forward-pointing open arrow.
   */
  FORWARD_OPEN_ARROW: 4,
};

/**
 * A representation of time as a Date object, a localized string, and a time
 * zone.
 * @record
 */
google.maps.Time = function() {};

/**
 * A string representing the time&#39;s value. The time is displayed in the time
 * zone of the transit stop.
 * @type {string}
 */
google.maps.Time.prototype.text;

/**
 * The time zone in which this stop lies. The value is the name of the time zone
 * as defined in the IANA Time Zone Database, e.g. &quot;America/New_York&quot;.
 * @type {string}
 */
google.maps.Time.prototype.time_zone;

/**
 * The time of this departure or arrival, specified as a JavaScript Date object.
 * @type {!Date}
 */
google.maps.Time.prototype.value;

/**
 * A traffic layer.
 * @param {google.maps.TrafficLayerOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.TrafficLayer = function(opts) {};

/**
 * Returns the map on which this layer is displayed.
 * @return {google.maps.Map}
 */
google.maps.TrafficLayer.prototype.getMap = function() {};

/**
 * Renders the layer on the specified map. If map is set to <code>null</code>,
 * the layer will be removed.
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
 * TrafficLayerOptions object used to define the properties that can be set on a
 * TrafficLayer.
 * @record
 */
google.maps.TrafficLayerOptions = function() {};

/**
 * Whether the traffic layer refreshes with updated information automatically.
 * This is <code>true</code> by default.
 * @type {?boolean|undefined}
 */
google.maps.TrafficLayerOptions.prototype.autoRefresh;

/**
 * Map on which to display the traffic layer.
 * @type {?google.maps.Map|undefined}
 */
google.maps.TrafficLayerOptions.prototype.map;

/**
 * The assumptions to use when predicting duration in traffic. Specified as part
 * of a <code><a href="#DirectionsRequest">DirectionsRequest</a></code> or
 * <code><a href="#DistanceMatrixRequest">DistanceMatrixRequest</a></code>.
 * Specify these by value, or by using the constant&#39;s name. For example,
 * <code>'bestguess'</code> or <code>google.maps.TrafficModel.BEST_GUESS</code>.
 * @enum {string}
 */
google.maps.TrafficModel = {
  /**
   * Use historical traffic data to best estimate the time spent in traffic.
   */
  BEST_GUESS: 'BEST_GUESS',
  /**
   * Use historical traffic data to make an optimistic estimate of what the
   * duration in traffic will be.
   */
  OPTIMISTIC: 'OPTIMISTIC',
  /**
   * Use historical traffic data to make a pessimistic estimate of what the
   * duration in traffic will be.
   */
  PESSIMISTIC: 'PESSIMISTIC',
};

/**
 * Information about an agency that operates a transit line.
 * @record
 */
google.maps.TransitAgency = function() {};

/**
 * The name of this transit agency.
 * @type {string}
 */
google.maps.TransitAgency.prototype.name;

/**
 * The transit agency&#39;s phone number.
 * @type {string}
 */
google.maps.TransitAgency.prototype.phone;

/**
 * The transit agency&#39;s URL.
 * @type {string}
 */
google.maps.TransitAgency.prototype.url;

/**
 * Details about the departure, arrival, and mode of transit used in this step.
 * @record
 */
google.maps.TransitDetails = function() {};

/**
 * The arrival stop of this transit step.
 * @type {!google.maps.TransitStop}
 */
google.maps.TransitDetails.prototype.arrival_stop;

/**
 * The arrival time of this step, specified as a Time object.
 * @type {!google.maps.Time}
 */
google.maps.TransitDetails.prototype.arrival_time;

/**
 * The departure stop of this transit step.
 * @type {!google.maps.TransitStop}
 */
google.maps.TransitDetails.prototype.departure_stop;

/**
 * The departure time of this step, specified as a Time object.
 * @type {!google.maps.Time}
 */
google.maps.TransitDetails.prototype.departure_time;

/**
 * The direction in which to travel on this line, as it is marked on the vehicle
 * or at the departure stop.
 * @type {string}
 */
google.maps.TransitDetails.prototype.headsign;

/**
 * The expected number of seconds between equivalent vehicles at this stop.
 * @type {number}
 */
google.maps.TransitDetails.prototype.headway;

/**
 * Details about the transit line used in this step.
 * @type {!google.maps.TransitLine}
 */
google.maps.TransitDetails.prototype.line;

/**
 * The number of stops on this step. Includes the arrival stop, but not the
 * departure stop.
 * @type {number}
 */
google.maps.TransitDetails.prototype.num_stops;

/**
 * The text that appears in schedules and sign boards to identify a transit trip
 * to passengers, for example, to identify train numbers for commuter rail
 * trips. The text uniquely identifies a trip within a service day.
 * @type {string}
 */
google.maps.TransitDetails.prototype.trip_short_name;

/**
 * A fare of a <code><a href="#DirectionsResult">DirectionsRoute</a> </code>
 * consisting of value and currency.
 * @record
 */
google.maps.TransitFare = function() {};

/**
 * An <a href="http://en.wikipedia.org/wiki/ISO_4217">ISO 4217 currency code</a>
 * indicating the currency in which the fare is expressed.
 * @type {string}
 */
google.maps.TransitFare.prototype.currency;

/**
 * The numerical value of the fare, expressed in the given
 * <code>currency</code>.
 * @type {number}
 */
google.maps.TransitFare.prototype.value;

/**
 * A transit layer.
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.TransitLayer = function() {};

/**
 * Returns the map on which this layer is displayed.
 * @return {google.maps.Map}
 */
google.maps.TransitLayer.prototype.getMap = function() {};

/**
 * Renders the layer on the specified map. If map is set to <code>null</code>,
 * the layer will be removed.
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.TransitLayer.prototype.setMap = function(map) {};

/**
 * Information about the transit line that operates this transit step.
 * @record
 */
google.maps.TransitLine = function() {};

/**
 * The transit agency that operates this transit line.
 * @type {Array<google.maps.TransitAgency>}
 */
google.maps.TransitLine.prototype.agencies;

/**
 * The color commonly used in signage for this transit line, represented as a
 * hex string.
 * @type {string}
 */
google.maps.TransitLine.prototype.color;

/**
 * The URL for an icon associated with this line.
 * @type {string}
 */
google.maps.TransitLine.prototype.icon;

/**
 * The full name of this transit line, e.g. &quot;8 Avenue Local&quot;.
 * @type {string}
 */
google.maps.TransitLine.prototype.name;

/**
 * The short name of this transit line, e.g. &quot;E&quot;.
 * @type {string}
 */
google.maps.TransitLine.prototype.short_name;

/**
 * The text color commonly used in signage for this transit line, represented as
 * a hex string.
 * @type {string}
 */
google.maps.TransitLine.prototype.text_color;

/**
 * The agency&#39;s URL which is specific to this transit line.
 * @type {string}
 */
google.maps.TransitLine.prototype.url;

/**
 * The type of vehicle used, e.g. train or bus.
 * @type {!google.maps.TransitVehicle}
 */
google.maps.TransitLine.prototype.vehicle;

/**
 * The valid transit mode e.g. bus that can be specified in a <i><code><a
 * href="#TransitOptions">TransitOptions</a></code></i>. Specify these by value,
 * or by using the constant&#39;s name. For example, <code>'BUS'</code> or
 * <code>google.maps.TransitMode.BUS</code>.
 * @enum {string}
 */
google.maps.TransitMode = {
  /**
   * Specifies bus as a preferred mode of transit.
   */
  BUS: 'BUS',
  /**
   * Specifies rail as a preferred mode of transit.
   */
  RAIL: 'RAIL',
  /**
   * Specifies subway as a preferred mode of transit.
   */
  SUBWAY: 'SUBWAY',
  /**
   * Specifies train as a preferred mode of transit.
   */
  TRAIN: 'TRAIN',
  /**
   * Specifies tram as a preferred mode of transit.
   */
  TRAM: 'TRAM',
};

/**
 * The TransitOptions object to be included in a <code><a
 * href="#DirectionsRequest">DirectionsRequest</a></code> when the travel mode
 * is set to TRANSIT.
 * @record
 */
google.maps.TransitOptions = function() {};

/**
 * The desired arrival time for the route, specified as a Date object. The Date
 * object measures time in milliseconds since 1 January 1970. If arrival time is
 * specified, departure time is ignored.
 * @type {?Date|undefined}
 */
google.maps.TransitOptions.prototype.arrivalTime;

/**
 * The desired departure time for the route, specified as a Date object. The
 * Date object measures time in milliseconds since 1 January 1970. If neither
 * departure time nor arrival time is specified, the time is assumed to be
 * &quot;now&quot;.
 * @type {?Date|undefined}
 */
google.maps.TransitOptions.prototype.departureTime;

/**
 * One or more preferred modes of transit, such as bus or train. If no
 * preference is given, the API returns the default best route.
 * @type {?Array<!google.maps.TransitMode>|undefined}
 */
google.maps.TransitOptions.prototype.modes;

/**
 * A preference that can bias the choice of transit route, such as less walking.
 * If no preference is given, the API returns the default best route.
 * @type {?google.maps.TransitRoutePreference|undefined}
 */
google.maps.TransitOptions.prototype.routingPreference;

/**
 * The valid transit route type that can be specified in a <i><code><a
 * href="#TransitOptions">TransitOptions</a></code></i>. Specify these by value,
 * or by using the constant&#39;s name. For example, <code>'LESS_WALKING'</code>
 * or <code>google.maps.TransitRoutePreference.LESS_WALKING</code>.
 * @enum {string}
 */
google.maps.TransitRoutePreference = {
  /**
   * Specifies that the calculated route should prefer a limited number of
   * transfers.
   */
  FEWER_TRANSFERS: 'FEWER_TRANSFERS',
  /**
   * Specifies that the calculated route should prefer limited amounts of
   * walking.
   */
  LESS_WALKING: 'LESS_WALKING',
};

/**
 * Details about a transit stop or station.
 * @record
 */
google.maps.TransitStop = function() {};

/**
 * The location of this stop.
 * @type {!google.maps.LatLng}
 */
google.maps.TransitStop.prototype.location;

/**
 * The name of this transit stop.
 * @type {string}
 */
google.maps.TransitStop.prototype.name;

/**
 * Information about the vehicle that operates on a transit line.
 * @record
 */
google.maps.TransitVehicle = function() {};

/**
 * A URL for an icon that corresponds to the type of vehicle used on this line.
 * @type {string}
 */
google.maps.TransitVehicle.prototype.icon;

/**
 * A URL for an icon that corresponds to the type of vehicle used in this region
 * instead of the more general icon.
 * @type {string}
 */
google.maps.TransitVehicle.prototype.local_icon;

/**
 * A name for this type of TransitVehicle, e.g. &quot;Train&quot; or
 * &quot;Bus&quot;.
 * @type {string}
 */
google.maps.TransitVehicle.prototype.name;

/**
 * The type of vehicle used, e.g. train, bus, or ferry.
 * @type {!google.maps.VehicleType}
 */
google.maps.TransitVehicle.prototype.type;

/**
 * The valid travel modes that can be specified in a
 * <code>DirectionsRequest</code> as well as the travel modes returned in a
 * <code>DirectionsStep</code>. Specify these by value, or by using the
 * constant&#39;s name. For example, <code>'BICYCLING'</code> or
 * <code>google.maps.TravelMode.BICYCLING</code>.
 * @enum {string}
 */
google.maps.TravelMode = {
  /**
   * Specifies a bicycling directions request.
   */
  BICYCLING: 'BICYCLING',
  /**
   * Specifies a driving directions request.
   */
  DRIVING: 'DRIVING',
  /**
   * Specifies a transit directions request.
   */
  TRANSIT: 'TRANSIT',
  /**
   * Specifies a walking directions request.
   */
  WALKING: 'WALKING',
};

/**
 * The valid unit systems that can be specified in a <i><code><a
 * href="#DirectionsRequest">DirectionsRequest</a></code></i>.
 * @enum {number}
 */
google.maps.UnitSystem = {
  /**
   * Specifies that distances in the <code>DirectionsResult</code> should be
   * expressed in imperial units.
   */
  IMPERIAL: 0,
  /**
   * Specifies that distances in the <code>DirectionsResult</code> should be
   * expressed in metric units.
   */
  METRIC: 1,
};

/**
 * Possible values for vehicle types. These values are specified as strings,
 * i.e. &#39;BUS&#39; or &#39;TRAIN&#39;.
 * @enum {string}
 */
google.maps.VehicleType = {
  /**
   * Bus.
   */
  BUS: 'BUS',
  /**
   * A vehicle that operates on a cable, usually on the ground. Aerial cable
   * cars may be of the type <code>GONDOLA_LIFT</code>.
   */
  CABLE_CAR: 'CABLE_CAR',
  /**
   * Commuter rail.
   */
  COMMUTER_TRAIN: 'COMMUTER_TRAIN',
  /**
   * Ferry.
   */
  FERRY: 'FERRY',
  /**
   * A vehicle that is pulled up a steep incline by a cable.
   */
  FUNICULAR: 'FUNICULAR',
  /**
   * An aerial cable car.
   */
  GONDOLA_LIFT: 'GONDOLA_LIFT',
  /**
   * Heavy rail.
   */
  HEAVY_RAIL: 'HEAVY_RAIL',
  /**
   * High speed train.
   */
  HIGH_SPEED_TRAIN: 'HIGH_SPEED_TRAIN',
  /**
   * Intercity bus.
   */
  INTERCITY_BUS: 'INTERCITY_BUS',
  /**
   * Light rail.
   */
  METRO_RAIL: 'METRO_RAIL',
  /**
   * Monorail.
   */
  MONORAIL: 'MONORAIL',
  /**
   * Other vehicles.
   */
  OTHER: 'OTHER',
  /**
   * Rail.
   */
  RAIL: 'RAIL',
  /**
   * Share taxi is a sort of bus transport with ability to drop off and pick up
   * passengers anywhere on its route. Generally share taxi uses minibus
   * vehicles.
   */
  SHARE_TAXI: 'SHARE_TAXI',
  /**
   * Underground light rail.
   */
  SUBWAY: 'SUBWAY',
  /**
   * Above ground light rail.
   */
  TRAM: 'TRAM',
  /**
   * Trolleybus.
   */
  TROLLEYBUS: 'TROLLEYBUS',
};

/**
 * Options for the rendering of the zoom control.
 * @record
 */
google.maps.ZoomControlOptions = function() {};

/**
 * Position id. Used to specify the position of the control on the map. The
 * default position is <code>TOP_LEFT</code>.
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.ZoomControlOptions.prototype.position;

/**
 * @const
 */
google.maps.drawing = {};

/**
 * Options for the rendering of the drawing control.
 * @record
 */
google.maps.drawing.DrawingControlOptions = function() {};

/**
 * The drawing modes to display in the drawing control, in the order in which
 * they are to be displayed. The hand icon (which corresponds to the
 * <code>null</code> drawing mode) is always available and is not to be
 * specified in this array. Defaults to <code>['marker', 'polyline',
 * 'rectangle', 'circle', 'polygon']</code>.
 * @type {?Array<!google.maps.drawing.OverlayType>|undefined}
 */
google.maps.drawing.DrawingControlOptions.prototype.drawingModes;

/**
 * Position id. Used to specify the position of the control on the map. The
 * default position is <code>TOP_LEFT</code>.
 * @type {?google.maps.ControlPosition|undefined}
 */
google.maps.drawing.DrawingControlOptions.prototype.position;

/**
 * Allows users to draw markers, polygons, polylines, rectangles, and circles on
 * the map. The <code>DrawingManager</code>&#39;s drawing mode defines the type
 * of overlay that will be created by the user. Adds a control to the map,
 * allowing the user to switch drawing mode.
 * @param {google.maps.drawing.DrawingManagerOptions=} options
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.drawing.DrawingManager = function(options) {};

/**
 * Returns the <code>DrawingManager</code>&#39;s drawing mode.
 * @return {?google.maps.drawing.OverlayType}
 */
google.maps.drawing.DrawingManager.prototype.getDrawingMode = function() {};

/**
 * Returns the <code>Map</code> to which the <code>DrawingManager</code> is
 * attached, which is the <code>Map</code> on which the overlays created will be
 * placed.
 * @return {google.maps.Map}
 */
google.maps.drawing.DrawingManager.prototype.getMap = function() {};

/**
 * Changes the <code>DrawingManager</code>&#39;s drawing mode, which defines the
 * type of overlay to be added on the map. Accepted values are
 * <code>'marker'</code>, <code>'polygon'</code>, <code>'polyline'</code>,
 * <code>'rectangle'</code>, <code>'circle'</code>, or <code>null</code>. A
 * drawing mode of <code>null</code> means that the user can interact with the
 * map as normal, and clicks do not draw anything.
 * @param {?google.maps.drawing.OverlayType} drawingMode
 * @return {undefined}
 */
google.maps.drawing.DrawingManager.prototype.setDrawingMode = function(
    drawingMode) {};

/**
 * Attaches the <code>DrawingManager</code> object to the specified
 * <code>Map</code>.
 * @param {google.maps.Map} map
 * @return {undefined}
 */
google.maps.drawing.DrawingManager.prototype.setMap = function(map) {};

/**
 * Sets the <code>DrawingManager</code>&#39;s options.
 * @param {google.maps.drawing.DrawingManagerOptions} options
 * @return {undefined}
 */
google.maps.drawing.DrawingManager.prototype.setOptions = function(options) {};

/**
 * Options for the drawing manager.
 * @record
 */
google.maps.drawing.DrawingManagerOptions = function() {};

/**
 * Options to apply to any new circles created with this
 * <code>DrawingManager</code>. The <code>center</code> and <code>radius</code>
 * properties are ignored, and the <code>map</code> property of a new circle is
 * always set to the <code>DrawingManager</code>&#39;s map.
 * @type {?google.maps.CircleOptions|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.circleOptions;

/**
 * The enabled/disabled state of the drawing control. Defaults to
 * <code>true</code>.
 * @type {?boolean|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.drawingControl;

/**
 * The display options for the drawing control.
 * @type {?google.maps.drawing.DrawingControlOptions|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.drawingControlOptions;

/**
 * The <code>DrawingManager</code>&#39;s drawing mode, which defines the type of
 * overlay to be added on the map. Accepted values are <code>'marker'</code>,
 * <code>'polygon'</code>, <code>'polyline'</code>, <code>'rectangle'</code>,
 * <code>'circle'</code>, or <code>null</code>. A drawing mode of
 * <code>null</code> means that the user can interact with the map as normal,
 * and clicks do not draw anything.
 * @type {?google.maps.drawing.OverlayType|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.drawingMode;

/**
 * The <code>Map</code> to which the <code>DrawingManager</code> is attached,
 * which is the <code>Map</code> on which the overlays created will be placed.
 * @type {?google.maps.Map|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.map;

/**
 * Options to apply to any new markers created with this
 * <code>DrawingManager</code>. The <code>position</code> property is ignored,
 * and the <code>map</code> property of a new marker is always set to the
 * <code>DrawingManager</code>&#39;s map.
 * @type {?google.maps.MarkerOptions|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.markerOptions;

/**
 * Options to apply to any new polygons created with this
 * <code>DrawingManager</code>. The <code>paths</code> property is ignored, and
 * the <code>map</code> property of a new polygon is always set to the
 * <code>DrawingManager</code>&#39;s map.
 * @type {?google.maps.PolygonOptions|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.polygonOptions;

/**
 * Options to apply to any new polylines created with this
 * <code>DrawingManager</code>. The <code>path</code> property is ignored, and
 * the <code>map</code> property of a new polyline is always set to the
 * <code>DrawingManager</code>&#39;s map.
 * @type {?google.maps.PolylineOptions|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.polylineOptions;

/**
 * Options to apply to any new rectangles created with this
 * <code>DrawingManager</code>. The <code>bounds</code> property is ignored, and
 * the <code>map</code> property of a new rectangle is always set to the
 * <code>DrawingManager</code>&#39;s map.
 * @type {?google.maps.RectangleOptions|undefined}
 */
google.maps.drawing.DrawingManagerOptions.prototype.rectangleOptions;

/**
 * The properties of an overlaycomplete event on a <code>DrawingManager</code>.
 * @record
 */
google.maps.drawing.OverlayCompleteEvent = function() {};

/**
 * The completed overlay.
 * @type {google.maps.Marker|google.maps.Polygon|google.maps.Polyline|google.maps.Rectangle|google.maps.Circle}
 */
google.maps.drawing.OverlayCompleteEvent.prototype.overlay;

/**
 * The completed overlay&#39;s type.
 * @type {google.maps.drawing.OverlayType}
 */
google.maps.drawing.OverlayCompleteEvent.prototype.type;

/**
 * The types of overlay that may be created by the <code>DrawingManager</code>.
 * Specify these by value, or by using the constant&#39;s name. For example,
 * <code>'polygon'</code> or
 * <code>google.maps.drawing.OverlayType.POLYGON</code>.
 * @enum {string}
 */
google.maps.drawing.OverlayType = {
  /**
   * Specifies that the <code>DrawingManager</code> creates circles, and that
   * the overlay given in the <code>overlaycomplete</code> event is a circle.
   */
  CIRCLE: 'CIRCLE',
  /**
   * Specifies that the <code>DrawingManager</code> creates markers, and that
   * the overlay given in the <code>overlaycomplete</code> event is a marker.
   */
  MARKER: 'MARKER',
  /**
   * Specifies that the <code>DrawingManager</code> creates polygons, and that
   * the overlay given in the <code>overlaycomplete</code> event is a polygon.
   */
  POLYGON: 'POLYGON',
  /**
   * Specifies that the <code>DrawingManager</code> creates polylines, and that
   * the overlay given in the <code>overlaycomplete</code> event is a polyline.
   */
  POLYLINE: 'POLYLINE',
  /**
   * Specifies that the <code>DrawingManager</code> creates rectangles, and that
   * the overlay given in the <code>overlaycomplete</code> event is a rectangle.
   */
  RECTANGLE: 'RECTANGLE',
};

/**
 * @const
 */
google.maps.event = {};

/**
 * Cross browser event handler registration. This listener is removed by calling
 * removeListener(handle) for the handle that is returned by this function.
 * @param {!Object} instance
 * @param {string} eventName
 * @param {!Function} handler
 * @param {boolean=} capture
 * @return {!google.maps.MapsEventListener}
 */
google.maps.event.addDomListener = function(
    instance, eventName, handler, capture) {};

/**
 * Wrapper around addDomListener that removes the listener after the first
 * event.
 * @param {!Object} instance
 * @param {string} eventName
 * @param {!Function} handler
 * @param {boolean=} capture
 * @return {!google.maps.MapsEventListener}
 */
google.maps.event.addDomListenerOnce = function(
    instance, eventName, handler, capture) {};

/**
 * Adds the given listener function to the given event name for the given object
 * instance. Returns an identifier for this listener that can be used with
 * removeListener().
 * @param {!Object} instance
 * @param {string} eventName
 * @param {!Function} handler
 * @return {!google.maps.MapsEventListener}
 */
google.maps.event.addListener = function(instance, eventName, handler) {};

/**
 * Like addListener, but the handler removes itself after handling the first
 * event.
 * @param {!Object} instance
 * @param {string} eventName
 * @param {!Function} handler
 * @return {!google.maps.MapsEventListener}
 */
google.maps.event.addListenerOnce = function(instance, eventName, handler) {};

/**
 * Removes all listeners for all events for the given instance.
 * @param {!Object} instance
 * @return {undefined}
 */
google.maps.event.clearInstanceListeners = function(instance) {};

/**
 * Removes all listeners for the given event for the given instance.
 * @param {!Object} instance
 * @param {string} eventName
 * @return {undefined}
 */
google.maps.event.clearListeners = function(instance, eventName) {};

/**
 * Removes the given listener, which should have been returned by addListener
 * above. Equivalent to calling <code>listener.remove()</code>.
 * @param {!google.maps.MapsEventListener} listener
 * @return {undefined}
 */
google.maps.event.removeListener = function(listener) {};

/**
 * Triggers the given event. All arguments after eventName are passed as
 * arguments to the listeners.
 * @param {!Object} instance
 * @param {string} eventName
 * @param {...*} eventArgs
 * @return {undefined}
 */
google.maps.event.trigger = function(instance, eventName, eventArgs) {};

/**
 * @const
 */
google.maps.geometry = {};

/**
 * Utilities for polyline encoding and decoding.
 * @const
 */
google.maps.geometry.encoding = {};

/**
 * Decodes an encoded path string into a sequence of LatLngs.
 * @param {string} encodedPath
 * @return {!Array<!google.maps.LatLng>}
 */
google.maps.geometry.encoding.decodePath = function(encodedPath) {};

/**
 * Encodes a sequence of LatLngs into an encoded path string.
 * @param {!Array<!google.maps.LatLng>|!google.maps.MVCArray<!google.maps.LatLng>}
 *     path
 * @return {string}
 */
google.maps.geometry.encoding.encodePath = function(path) {};

/**
 * Utility functions for computations involving polygons and polylines.
 * @const
 */
google.maps.geometry.poly = {};

/**
 * Computes whether the given point lies inside the specified polygon.
 * @param {google.maps.LatLng} point
 * @param {google.maps.Polygon} polygon
 * @return {boolean}
 */
google.maps.geometry.poly.containsLocation = function(point, polygon) {};

/**
 * Computes whether the given point lies on or near to a polyline, or the edge
 * of a polygon, within a specified tolerance. Returns <code>true</code> when
 * the difference between the latitude and longitude of the supplied point, and
 * the closest point on the edge, is less than the tolerance. The tolerance
 * defaults to 10<sup>-9</sup> degrees.
 * @param {google.maps.LatLng} point
 * @param {google.maps.Polygon|google.maps.Polyline} poly
 * @param {number=} tolerance
 * @return {boolean}
 */
google.maps.geometry.poly.isLocationOnEdge = function(
    point, poly, tolerance) {};

/**
 * Utility functions for computing geodesic angles, distances and areas. The
 * default radius is Earth&#39;s radius of 6378137 meters.
 * @const
 */
google.maps.geometry.spherical = {};

/**
 * Returns the area of a closed path. The computed area uses the same units as
 * the radius. The radius defaults to the Earth&#39;s radius in meters, in which
 * case the area is in square meters.
 * @param {!Array<!google.maps.LatLng>|!google.maps.MVCArray<!google.maps.LatLng>}
 *     path
 * @param {number=} radius
 * @return {number}
 */
google.maps.geometry.spherical.computeArea = function(path, radius) {};

/**
 * Returns the distance, in meters, between two LatLngs. You can optionally
 * specify a custom radius. The radius defaults to the radius of the Earth.
 * @param {!google.maps.LatLng} from
 * @param {!google.maps.LatLng} to
 * @param {number=} radius
 * @return {number}
 */
google.maps.geometry.spherical.computeDistanceBetween = function(
    from, to, radius) {};

/**
 * Returns the heading from one LatLng to another LatLng. Headings are expressed
 * in degrees clockwise from North within the range [-180,180).
 * @param {!google.maps.LatLng} from
 * @param {!google.maps.LatLng} to
 * @return {number}
 */
google.maps.geometry.spherical.computeHeading = function(from, to) {};

/**
 * Returns the length of the given path.
 * @param {!Array<!google.maps.LatLng>|!google.maps.MVCArray<!google.maps.LatLng>}
 *     path
 * @param {number=} radius
 * @return {number}
 */
google.maps.geometry.spherical.computeLength = function(path, radius) {};

/**
 * Returns the LatLng resulting from moving a distance from an origin in the
 * specified heading (expressed in degrees clockwise from north).
 * @param {!google.maps.LatLng} from
 * @param {number} distance
 * @param {number} heading
 * @param {number=} radius
 * @return {!google.maps.LatLng}
 */
google.maps.geometry.spherical.computeOffset = function(
    from, distance, heading, radius) {};

/**
 * Returns the location of origin when provided with a LatLng destination,
 * meters travelled and original heading. Headings are expressed in degrees
 * clockwise from North. This function returns <code>null</code> when no
 * solution is available.
 * @param {!google.maps.LatLng} to
 * @param {number} distance
 * @param {number} heading
 * @param {number=} radius
 * @return {?google.maps.LatLng}
 */
google.maps.geometry.spherical.computeOffsetOrigin = function(
    to, distance, heading, radius) {};

/**
 * Returns the signed area of a closed path. The signed area may be used to
 * determine the orientation of the path. The computed area uses the same units
 * as the radius. The radius defaults to the Earth&#39;s radius in meters, in
 * which case the area is in square meters.
 * @param {!Array<!google.maps.LatLng>|!google.maps.MVCArray<!google.maps.LatLng>}
 *     loop
 * @param {number=} radius
 * @return {number}
 */
google.maps.geometry.spherical.computeSignedArea = function(loop, radius) {};

/**
 * Returns the LatLng which lies the given fraction of the way between the
 * origin LatLng and the destination LatLng.
 * @param {!google.maps.LatLng} from
 * @param {!google.maps.LatLng} to
 * @param {number} fraction
 * @return {!google.maps.LatLng}
 */
google.maps.geometry.spherical.interpolate = function(from, to, fraction) {};

/**
 * @const
 */
google.maps.localContext = {};

/**
 * Shows a Local Context experience with a {@link google.maps.Map}.
 * @param {!google.maps.localContext.LocalContextMapViewOptions} options
 * @implements {google.maps.localContext.LocalContextMapViewOptions}
 * @constructor
 */
google.maps.localContext.LocalContextMapView = function(options) {};

/**
 * See {@link
 * google.maps.localContext.LocalContextMapViewOptions.directionsOptions}.
 * @type {?google.maps.localContext.MapDirectionsOptions|!google.maps.localContext.MapDirectionsOptionsLiteral|undefined}
 */
google.maps.localContext.LocalContextMapView.prototype.directionsOptions;

/**
 * The DOM Element backing the view.
 * @type {?Element|undefined}
 */
google.maps.localContext.LocalContextMapView.prototype.element;

/**
 * Is set to <code>true</code> before {@link
 * google.maps.localContext.LocalContextMapView} begins changing the bounds of
 * the inner {@link google.maps.Map}, and set to <code>false</code> after {@link
 * google.maps.localContext.LocalContextMapView} finishes changing the bounds of
 * the inner {@link google.maps.Map}. (Not set when layout mode changes happen
 * due to responsive resizing.)
 * @type {boolean}
 */
google.maps.localContext.LocalContextMapView.prototype.isTransitioningMapBounds;

/**
 * See {@link google.maps.localContext.LocalContextMapViewOptions.locationBias}.
 * Changing this property on the <code>LocalContextMapView</code> may trigger a
 * new search.
 * @type {?google.maps.places.LocationBias|undefined}
 */
google.maps.localContext.LocalContextMapView.prototype.locationBias;

/**
 * See {@link
 * google.maps.localContext.LocalContextMapViewOptions.locationRestriction}.
 * Changing this property on the <code>LocalContextMapView</code> may trigger a
 * new search.
 * @type {?google.maps.places.LocationRestriction|undefined}
 */
google.maps.localContext.LocalContextMapView.prototype.locationRestriction;

/**
 * See {@link
 * google.maps.localContext.LocalContextMapViewOptions.maxPlaceCount}. Changing
 * this property on the <code>LocalContextMapView</code> may trigger a new
 * search.
 * @type {number}
 */
google.maps.localContext.LocalContextMapView.prototype.maxPlaceCount;

/**
 * See {@link
 * google.maps.localContext.LocalContextMapViewOptions.placeTypePreferences}.
 * Changing this property on the <code>LocalContextMapView</code> may trigger a
 * new search. <code>Iterable&lt;string|PlaceTypePreference&gt;</code> is also
 * accepted.
 * @type {!Array<!google.maps.localContext.PlaceTypePreference>}
 */
google.maps.localContext.LocalContextMapView.prototype.placeTypePreferences;

/**
 * Adds the given listener function to the given event name.
 * @param {string} eventName
 * @param {!Function} handler
 * @return {!google.maps.MapsEventListener}
 */
google.maps.localContext.LocalContextMapView.prototype.addListener = function(
    eventName, handler) {};

/**
 * Hides the place details.
 * @return {undefined}
 */
google.maps.localContext.LocalContextMapView.prototype.hidePlaceDetailsView =
    function() {};

/**
 * Searches for places to show the user based on the current
 * <code>maxPlaceCount</code>, <code>placeTypePreferences</code>,
 * <code>locationRestriction</code>, and <code>locationBias</code>.
 * @return {undefined}
 */
google.maps.localContext.LocalContextMapView.prototype.search = function() {};

/**
 * Options for constructing a {@link
 * google.maps.localContext.LocalContextMapView}, or accessing an
 * existing {@link google.maps.localContext.LocalContextMapView}.
 * @record
 */
google.maps.localContext.LocalContextMapViewOptions = function() {};

/**
 * Options for customizing directions. If not set, directions and distance will
 * be disabled.
 * @type {?google.maps.localContext.MapDirectionsOptions|!google.maps.localContext.MapDirectionsOptionsLiteral|undefined}
 */
google.maps.localContext.LocalContextMapViewOptions.prototype.directionsOptions;

/**
 * The DOM Element backing the view.
 * @type {?Element|undefined}
 */
google.maps.localContext.LocalContextMapViewOptions.prototype.element;

/**
 * A soft boundary or hint to use when searching for places. Defaults to no
 * bias.
 * @type {?google.maps.places.LocationBias|undefined}
 */
google.maps.localContext.LocalContextMapViewOptions.prototype.locationBias;

/**
 * Bounds to constrain search results. If not specified, results will be
 * constrained to the map viewport.
 * @type {?google.maps.places.LocationRestriction|undefined}
 */
google.maps.localContext.LocalContextMapViewOptions.prototype
    .locationRestriction;

/**
 * An already instantiated {@link google.maps.Map} instance. If passed in, the
 * map will be moved into the LocalContextMapView&#39;s DOM, and will
 * <strong>not</strong> be re-styled. The element associated with the Map may
 * also have styles and classes applied to it by the
 * <code>LocalContextMapView</code>.
 * @type {?google.maps.Map|undefined}
 */
google.maps.localContext.LocalContextMapViewOptions.prototype.map;

/**
 * The maximum number of places to show. When this parameter is 0, the Local
 * Context Library does not load places. [0,24]
 * @type {number}
 */
google.maps.localContext.LocalContextMapViewOptions.prototype.maxPlaceCount;

/**
 * Configure the place marker icon based on the icon state. Invoked whenever the
 * input to the callback changes. Pass a function to dynamically override the
 * default setup when the LocalContextMapView draws the place marker. Errors and
 * invalid configurations may be determined asynchronously, and will be ignored
 * (defaults will be used, and errors will be logged to the console).
 * @type {(function({isSelected:boolean, isHighlighted:boolean}):
 *     (?google.maps.localContext.PinOptions|undefined))|?google.maps.localContext.PinOptions|undefined}
 */
google.maps.localContext.LocalContextMapViewOptions.prototype.pinOptionsSetup;

/**
 * Overrides the setup of the place chooser view. Pass a function to dynamically
 * override the default setup when the LocalContextMapView might change its
 * layout due to resizing. Errors and invalid configurations may be determined
 * asynchronously, and will be ignored (defaults will be used instead, and
 * errors will be logged to the console). Errors detected at construction will
 * cause errors to be thrown synchronously.
 * @type {(function({defaultLayoutMode:!google.maps.localContext.PlaceChooserLayoutMode,
 *     defaultPosition:?google.maps.localContext.PlaceChooserPosition}):
 *     (?google.maps.localContext.PlaceChooserViewSetupOptions|undefined))|?google.maps.localContext.PlaceChooserViewSetupOptions|undefined}
 */
google.maps.localContext.LocalContextMapViewOptions.prototype
    .placeChooserViewSetup;

/**
 * Overrides the setup of the place details view. Pass a function to dynamically
 * override the default setup when the LocalContextMapView might change its
 * layout due to resizing. Errors and invalid configurations may be determined
 * asynchronously, and will be ignored (defaults will be used, and errors will
 * be logged to the console). Errors detected at construction will cause errors
 * to be thrown synchronously.
 * @type {(function({defaultLayoutMode:!google.maps.localContext.PlaceDetailsLayoutMode,
 *     defaultPosition:?google.maps.localContext.PlaceDetailsPosition}):
 *     (?google.maps.localContext.PlaceDetailsViewSetupOptions|undefined))|?google.maps.localContext.PlaceDetailsViewSetupOptions|undefined}
 */
google.maps.localContext.LocalContextMapViewOptions.prototype
    .placeDetailsViewSetup;

/**
 * The types of places to search for (up to 10). The type
 * <code>Iterable&lt;string|PlaceTypePreference&gt;</code> is also accepted, but
 * is only supported in browsers which natively support JavaScript Symbols.
 * @type {!Array<!google.maps.localContext.PlaceTypePreference>}
 */
google.maps.localContext.LocalContextMapViewOptions.prototype
    .placeTypePreferences;

/**
 * Provides settings for directions with a {@link
 * google.maps.localContext.LocalContextMapView}.
 * @implements {google.maps.localContext.MapDirectionsOptionsLiteral}
 * @constructor
 */
google.maps.localContext.MapDirectionsOptions = function() {};

/**
 * Adds the given listener function to the given event name.
 * @param {string} eventName
 * @param {!Function} handler
 * @return {!google.maps.MapsEventListener}
 */
google.maps.localContext.MapDirectionsOptions.prototype.addListener = function(
    eventName, handler) {};

/**
 * Object literals are accepted in place of {@link
 * google.maps.localContext.MapDirectionsOptions} objects, as a convenience, in
 * many places. These are converted to {@link
 * google.maps.localContext.MapDirectionsOptions} objects when the Maps API
 * encounters them.
 * @record
 */
google.maps.localContext.MapDirectionsOptionsLiteral = function() {};

/**
 * Origin for directions and distance.
 * @type {!google.maps.LatLng|!google.maps.LatLngLiteral}
 */
google.maps.localContext.MapDirectionsOptionsLiteral.prototype.origin;

/**
 * Options for customizing a pin marker.
 * @record
 */
google.maps.localContext.PinOptions = function() {};

/**
 * The color of the icon&#39;s shape, can be any valid CSS color.
 * @type {?string|undefined}
 */
google.maps.localContext.PinOptions.prototype.background;

/**
 * The color of the icon&#39;s glyph, can be any valid CSS color.
 * @type {?string|undefined}
 */
google.maps.localContext.PinOptions.prototype.glyphColor;

/**
 * The scale of the icon. The value is absolute, not relative to the default
 * sizes in each state.
 * @type {?number|undefined}
 */
google.maps.localContext.PinOptions.prototype.scale;

/**
 * Layout modes for the place chooser.
 * @enum {string}
 */
google.maps.localContext.PlaceChooserLayoutMode = {
  /**
   * Place chooser is hidden.
   */
  HIDDEN: 'HIDDEN',
  /**
   * Place chooser is shown as a sheet.
   */
  SHEET: 'SHEET',
};

/**
 * Display positions for the place chooser.
 * @enum {string}
 */
google.maps.localContext.PlaceChooserPosition = {
  /**
   * Place chooser is displayed on a line below the map extending to the end of
   * the container.
   */
  BLOCK_END: 'BLOCK_END',
  /**
   * Place chooser is displayed inline with the map at the end of the line. (In
   * a left-to-right language this means that the place chooser is to the right
   * of the map.)
   */
  INLINE_END: 'INLINE_END',
  /**
   * Place chooser is displayed inline with the map at the start of the line.
   * (In a left-to-right language this means that the place chooser is to the
   * left of the map.)
   */
  INLINE_START: 'INLINE_START',
};

/**
 * Setup options for the place chooser. Read more about <a
 * href="https://developers.google.com/maps/documentation/javascript/local-context/set-options#setting_layout_and_visibility">setting
 * layout and visibility</a>.
 * @record
 */
google.maps.localContext.PlaceChooserViewSetupOptions = function() {};

/**
 * @type {?google.maps.localContext.PlaceChooserLayoutMode|undefined}
 */
google.maps.localContext.PlaceChooserViewSetupOptions.prototype.layoutMode;

/**
 * Ignored when <code>layoutMode:HIDDEN</code>. If not passed, a position will
 * be determined automatically based on the <code>layoutMode</code>.
 * @type {?google.maps.localContext.PlaceChooserPosition|undefined}
 */
google.maps.localContext.PlaceChooserViewSetupOptions.prototype.position;

/**
 * Layout modes for the place details.
 * @enum {string}
 */
google.maps.localContext.PlaceDetailsLayoutMode = {
  /**
   * Place details is displayed in an {@link google.maps.InfoWindow}.
   */
  INFO_WINDOW: 'INFO_WINDOW',
  /**
   * Place details is displayed in a sheet.
   */
  SHEET: 'SHEET',
};

/**
 * Display positions for the place details.
 * @enum {string}
 */
google.maps.localContext.PlaceDetailsPosition = {
  /**
   * Place details is displayed inline with the map at the end of the line. (In
   * a left-to-right language this means that the place details is to the right
   * of the map.)
   */
  INLINE_END: 'INLINE_END',
  /**
   * Place details is displayed inline with the map at the start of the line.
   * (In a left-to-right language this means that the place details is to the
   * left of the map.)
   */
  INLINE_START: 'INLINE_START',
};

/**
 * Setup options for the place details. Read more about <a
 * href="https://developers.google.com/maps/documentation/javascript/local-context/set-options#setting_layout_and_visibility">setting
 * layout and visibility</a>.
 * @record
 */
google.maps.localContext.PlaceDetailsViewSetupOptions = function() {};

/**
 * @type {boolean|undefined}
 */
google.maps.localContext.PlaceDetailsViewSetupOptions.prototype.hidesOnMapClick;

/**
 * @type {?google.maps.localContext.PlaceDetailsLayoutMode|undefined}
 */
google.maps.localContext.PlaceDetailsViewSetupOptions.prototype.layoutMode;

/**
 * Ignored when <code>layoutMode:INFO_WINDOW</code>. If not passed, a position
 * will be determined automatically based on the <code>layoutMode</code>.
 * @type {?google.maps.localContext.PlaceDetailsPosition|undefined}
 */
google.maps.localContext.PlaceDetailsViewSetupOptions.prototype.position;

/**
 * @typedef {{type:string, weight:(number|undefined)}}
 */
google.maps.localContext.PlaceTypePreference;

/**
 * @const
 */
google.maps.places = {};

/**
 * A widget that provides Place predictions based on a user&#39;s text input. It
 * attaches to an input element of type <code>text</code>, and listens for text
 * entry in that field. The list of predictions is presented as a drop-down
 * list, and is updated as text is entered.
 * @param {!HTMLInputElement} inputField
 * @param {?google.maps.places.AutocompleteOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.places.Autocomplete = function(inputField, opts) {};

/**
 * Returns the bounds to which predictions are biased.
 * @return {!google.maps.LatLngBounds|undefined}
 */
google.maps.places.Autocomplete.prototype.getBounds = function() {};

/**
 * Returns the fields to be included for the Place in the details response when
 * the details are successfully retrieved. For a list of fields see {@link
 * google.maps.places.PlaceResult}.
 * @return {!Array<string>|undefined}
 */
google.maps.places.Autocomplete.prototype.getFields = function() {};

/**
 * Returns the details of the Place selected by user if the details were
 * successfully retrieved. Otherwise returns a stub Place object, with the
 * <code>name</code> property set to the current value of the input field.
 * @return {!google.maps.places.PlaceResult}
 */
google.maps.places.Autocomplete.prototype.getPlace = function() {};

/**
 * Sets the preferred area within which to return Place results. Results are
 * biased towards, but not restricted to, this area.
 * @param {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral|undefined}
 *     bounds
 * @return {undefined}
 */
google.maps.places.Autocomplete.prototype.setBounds = function(bounds) {};

/**
 * Sets the component restrictions. Component restrictions are used to restrict
 * predictions to only those within the parent component. For example, the
 * country.
 * @param {?google.maps.places.ComponentRestrictions} restrictions
 * @return {undefined}
 */
google.maps.places.Autocomplete.prototype.setComponentRestrictions = function(
    restrictions) {};

/**
 * Sets the fields to be included for the Place in the details response when the
 * details are successfully retrieved. For a list of fields see {@link
 * google.maps.places.PlaceResult}.
 * @param {!Array<string>|undefined} fields
 * @return {undefined}
 */
google.maps.places.Autocomplete.prototype.setFields = function(fields) {};

/**
 * @param {?google.maps.places.AutocompleteOptions} options
 * @return {undefined}
 */
google.maps.places.Autocomplete.prototype.setOptions = function(options) {};

/**
 * Sets the types of predictions to be returned. For a list of supported types,
 * see the <a
 * href="https://developers.google.com/maps/documentation/places/web-service/supported_types#table3">
 * developer&#39;s guide</a>. If no type is specified, all types will be
 * returned. The <code>setTypes</code> method accepts a single element array.
 * @param {?Array<string>} types
 * @return {undefined}
 */
google.maps.places.Autocomplete.prototype.setTypes = function(types) {};

/**
 * The options that can be set on an <code>Autocomplete</code> object.
 * @record
 */
google.maps.places.AutocompleteOptions = function() {};

/**
 * The area in which to search for places.
 * @type {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.places.AutocompleteOptions.prototype.bounds;

/**
 * The component restrictions. Component restrictions are used to restrict
 * predictions to only those within the parent component. For example, the
 * country.
 * @type {!google.maps.places.ComponentRestrictions|undefined}
 */
google.maps.places.AutocompleteOptions.prototype.componentRestrictions;

/**
 * Fields to be included for the Place in the details response when the details
 * are successfully retrieved, <a
 * href="https://developers.google.com/maps/billing/understanding-cost-of-use#places-product">which
 * will be billed for</a>. If <code>[&#39;ALL&#39;]</code> is passed in, all
 * available fields will be returned and billed for (this is not recommended for
 * production deployments). For a list of fields see {@link
 * google.maps.places.PlaceResult}. Nested fields can be specified with
 * dot-paths (for example, <code>"geometry.location"</code>).
 * @type {!Array<string>|undefined}
 */
google.maps.places.AutocompleteOptions.prototype.fields;

/**
 * Whether to retrieve only Place IDs. The PlaceResult made available when the
 * place_changed event is fired will only have the place_id, types and name
 * fields, with the place_id, types and description returned by the Autocomplete
 * service. Disabled by default.
 * @type {boolean|undefined}
 * @deprecated <code>placeIdOnly</code> is deprecated as of January 15, 2019,
 *     and will be turned off on January 15, 2020. Use {@link
 *     google.maps.places.AutocompleteOptions.fields} instead: <code>fields:
 *     [&#39;place_id&#39;, &#39;name&#39;, &#39;types&#39;]</code>.
 */
google.maps.places.AutocompleteOptions.prototype.placeIdOnly;

/**
 * A boolean value, indicating that the Autocomplete widget should only return
 * those places that are inside the bounds of the Autocomplete widget at the
 * time the query is sent. Setting strictBounds to <code>false</code> (which is
 * the default) will make the results biased towards, but not restricted to,
 * places contained within the bounds.
 * @type {boolean|undefined}
 */
google.maps.places.AutocompleteOptions.prototype.strictBounds;

/**
 * The types of predictions to be returned. For a list of supported types, see
 * the <a
 * href="https://developers.google.com/maps/documentation/places/web-service/supported_types#table3">developer&#39;s
 * guide</a>. If nothing is specified, all types are returned. In general only a
 * single type is allowed. The exception is that you can safely mix the
 * <code>'geocode'</code> and <code>'establishment'</code> types, but note that
 * this will have the same effect as specifying no types.
 * @type {!Array<string>|undefined}
 */
google.maps.places.AutocompleteOptions.prototype.types;

/**
 * Represents a single autocomplete prediction.
 * @record
 */
google.maps.places.AutocompletePrediction = function() {};

/**
 * This is the unformatted version of the query suggested by the Places service.
 * @type {string}
 */
google.maps.places.AutocompletePrediction.prototype.description;

/**
 * The distance in meters of the place from the {@link
 * google.maps.places.AutocompletionRequest.origin}.
 * @type {number|undefined}
 */
google.maps.places.AutocompletePrediction.prototype.distance_meters;

/**
 * A set of substrings in the place&#39;s description that match elements in the
 * user&#39;s input, suitable for use in highlighting those substrings. Each
 * substring is identified by an offset and a length, expressed in unicode
 * characters.
 * @type {!Array<!google.maps.places.PredictionSubstring>}
 */
google.maps.places.AutocompletePrediction.prototype.matched_substrings;

/**
 * A place ID that can be used to retrieve details about this place using the
 * place details service (see {@link
 * google.maps.places.PlacesService.getDetails}).
 * @type {string}
 */
google.maps.places.AutocompletePrediction.prototype.place_id;

/**
 * Structured information about the place&#39;s description, divided into a main
 * text and a secondary text, including an array of matched substrings from the
 * autocomplete input, identified by an offset and a length, expressed in
 * unicode characters.
 * @type {!google.maps.places.StructuredFormatting}
 */
google.maps.places.AutocompletePrediction.prototype.structured_formatting;

/**
 * Information about individual terms in the above description, from most to
 * least specific. For example, &quot;Taco Bell&quot;, &quot;Willitis&quot;, and
 * &quot;CA&quot;.
 * @type {!Array<!google.maps.places.PredictionTerm>}
 */
google.maps.places.AutocompletePrediction.prototype.terms;

/**
 * An array of types that the prediction belongs to, for example
 * <code>'establishment'</code> or <code>'geocode'</code>.
 * @type {!Array<string>}
 */
google.maps.places.AutocompletePrediction.prototype.types;

/**
 * An Autocomplete response returned by the call to {@link
 * google.maps.places.AutocompleteService.getPlacePredictions} containing a list
 * of {@link google.maps.places.AutocompletePrediction}s.
 * @constructor
 */
google.maps.places.AutocompleteResponse = function() {};

/**
 * The list of {@link google.maps.places.AutocompletePrediction}s.
 * @type {!Array<!google.maps.places.AutocompletePrediction>}
 */
google.maps.places.AutocompleteResponse.prototype.predictions;

/**
 * Contains methods related to retrieving Autocomplete predictions.
 * @constructor
 */
google.maps.places.AutocompleteService = function() {};

/**
 * Retrieves place autocomplete predictions based on the supplied autocomplete
 * request. <aside class="note">Note: <strong>For the beta release,
 * <code>v=beta</code>, the callback is optional and a Promise is
 * returned</strong>. More information is available in the <a
 * href="https://developers.google.com/maps/documentation/javascript/promises">Promises
 * guide</a>.</aside>
 * @param {!google.maps.places.AutocompletionRequest} request
 * @param {(function(?Array<!google.maps.places.AutocompletePrediction>,
 *     !google.maps.places.PlacesServiceStatus): void)=} callback
 * @return {?Promise<!google.maps.places.AutocompleteResponse>}
 */
google.maps.places.AutocompleteService.prototype.getPlacePredictions = function(
    request, callback) {};

/**
 * Retrieves query autocomplete predictions based on the supplied query
 * autocomplete request.
 * @param {!google.maps.places.QueryAutocompletionRequest} request
 * @param {function(?Array<!google.maps.places.QueryAutocompletePrediction>,
 *     !google.maps.places.PlacesServiceStatus): void} callback
 * @return {undefined}
 */
google.maps.places.AutocompleteService.prototype.getQueryPredictions = function(
    request, callback) {};

/**
 * Represents a session token used for tracking an autocomplete session, which
 * can be a series of {@link
 * google.maps.places.AutocompleteService.getPlacePredictions} calls followed by
 * a single {@link google.maps.places.PlacesService.getDetails} call.
 * @constructor
 */
google.maps.places.AutocompleteSessionToken = function() {};

/**
 * An Autocompletion request to be sent to {@link
 * google.maps.places.AutocompleteService.getPlacePredictions}.
 * @record
 */
google.maps.places.AutocompletionRequest = function() {};

/**
 * Bounds for prediction biasing. Predictions will be biased towards, but not
 * restricted to, the given <code>bounds</code>. Both <code>location</code> and
 * <code>radius</code> will be ignored if <code>bounds</code> is set.
 * @type {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.places.AutocompletionRequest.prototype.bounds;

/**
 * The component restrictions. Component restrictions are used to restrict
 * predictions to only those within the parent component. For example, the
 * country.
 * @type {!google.maps.places.ComponentRestrictions|undefined}
 */
google.maps.places.AutocompletionRequest.prototype.componentRestrictions;

/**
 * The user entered input string.
 * @type {string}
 */
google.maps.places.AutocompletionRequest.prototype.input;

/**
 * Location for prediction biasing. Predictions will be biased towards the given
 * <code>location</code> and <code>radius</code>. Alternatively,
 * <code>bounds</code> can be used.
 * @type {!google.maps.LatLng|undefined}
 */
google.maps.places.AutocompletionRequest.prototype.location;

/**
 * The character position in the input term at which the service uses text for
 * predictions (the position of the cursor in the input field).
 * @type {number|undefined}
 */
google.maps.places.AutocompletionRequest.prototype.offset;

/**
 * The location where {@link
 * google.maps.places.AutocompletePrediction.distance_meters} is calculated
 * from.
 * @type {!google.maps.LatLng|!google.maps.LatLngLiteral|undefined}
 */
google.maps.places.AutocompletionRequest.prototype.origin;

/**
 * The radius of the area used for prediction biasing. The <code>radius</code>
 * is specified in meters, and must always be accompanied by a
 * <code>location</code> property. Alternatively, <code>bounds</code> can be
 * used.
 * @type {number|undefined}
 */
google.maps.places.AutocompletionRequest.prototype.radius;

/**
 * Unique reference used to bundle individual requests into sessions.
 * @type {!google.maps.places.AutocompleteSessionToken|undefined}
 */
google.maps.places.AutocompletionRequest.prototype.sessionToken;

/**
 * The types of predictions to be returned. For a list of supported types, see
 * the <a
 * href="https://developers.google.com/maps/documentation/places/web-service/supported_types#table3">developer&#39;s
 * guide</a>. If nothing is specified, all types are returned. In general only a
 * single type is allowed. The exception is that you can safely mix the
 * <code>'geocode'</code> and <code>'establishment'</code> types, but note that
 * this will have the same effect as specifying no types.
 * @type {!Array<string>|undefined}
 */
google.maps.places.AutocompletionRequest.prototype.types;

/**
 * The operational status of the Place, if it is a business, returned in a
 * PlaceResult (indicates whether the place is operational, or closed either
 * temporarily or permanently). Specify these by value, or the constant&#39;s
 * name (example: <code>&#39;OPERATIONAL&#39;</code> or
 * <code>google.maps.places.BusinessStatus.OPERATIONAL</code>).
 * @enum {string}
 */
google.maps.places.BusinessStatus = {
  /**
   * The business is closed permanently.
   */
  CLOSED_PERMANENTLY: 'CLOSED_PERMANENTLY',
  /**
   * The business is closed temporarily.
   */
  CLOSED_TEMPORARILY: 'CLOSED_TEMPORARILY',
  /**
   * The business is operating normally.
   */
  OPERATIONAL: 'OPERATIONAL',
};

/**
 * Defines the component restrictions that can be used with the autocomplete
 * service.
 * @record
 */
google.maps.places.ComponentRestrictions = function() {};

/**
 * Restricts predictions to the specified country (ISO 3166-1 Alpha-2 country
 * code, case insensitive). For example, <code>'us'</code>, <code>'br'</code>,
 * or <code>'au'</code>. You can provide a single one, or an array of up to five
 * country code strings.
 * @type {string|?Array<string>}
 */
google.maps.places.ComponentRestrictions.prototype.country;

/**
 * A find place from text search request to be sent to {@link
 * google.maps.places.PlacesService.findPlaceFromPhoneNumber}.
 * @record
 */
google.maps.places.FindPlaceFromPhoneNumberRequest = function() {};

/**
 * Fields to be included in the response, <a
 * href="https://developers.google.com/maps/billing/understanding-cost-of-use#places-product">which
 * will be billed for</a>. If <code>[&#39;ALL&#39;]</code> is passed in, all
 * available fields will be returned and billed for (this is not recommended for
 * production deployments). For a list of fields see {@link
 * google.maps.places.PlaceResult}. Nested fields can be specified with
 * dot-paths (for example, <code>"geometry.location"</code>).
 * @type {!Array<string>}
 */
google.maps.places.FindPlaceFromPhoneNumberRequest.prototype.fields;

/**
 * The bias used when searching for Place. The result will be biased towards,
 * but not restricted to, the given {@link google.maps.places.LocationBias}.
 * @type {!google.maps.places.LocationBias|undefined}
 */
google.maps.places.FindPlaceFromPhoneNumberRequest.prototype.locationBias;

/**
 * The phone number of the place to look up. Format must be <a
 * href="https://en.wikipedia.org/wiki/E.164">E.164</a>.
 * @type {string}
 */
google.maps.places.FindPlaceFromPhoneNumberRequest.prototype.phoneNumber;

/**
 * A find place from text search request to be sent to {@link
 * google.maps.places.PlacesService.findPlaceFromQuery}.
 * @record
 */
google.maps.places.FindPlaceFromQueryRequest = function() {};

/**
 * Fields to be included in the response, <a
 * href="https://developers.google.com/maps/billing/understanding-cost-of-use#places-product">which
 * will be billed for</a>. If <code>[&#39;ALL&#39;]</code> is passed in, all
 * available fields will be returned and billed for (this is not recommended for
 * production deployments). For a list of fields see {@link
 * google.maps.places.PlaceResult}. Nested fields can be specified with
 * dot-paths (for example, <code>"geometry.location"</code>).
 * @type {!Array<string>}
 */
google.maps.places.FindPlaceFromQueryRequest.prototype.fields;

/**
 * The bias used when searching for Place. The result will be biased towards,
 * but not restricted to, the given {@link google.maps.places.LocationBias}.
 * @type {!google.maps.places.LocationBias|undefined}
 */
google.maps.places.FindPlaceFromQueryRequest.prototype.locationBias;

/**
 * The request&#39;s query. For example, the name or address of a place.
 * @type {string}
 */
google.maps.places.FindPlaceFromQueryRequest.prototype.query;

/**
 * @typedef {!google.maps.LatLng|!google.maps.LatLngLiteral|!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral|!google.maps.Circle|!google.maps.CircleLiteral|string}
 */
google.maps.places.LocationBias;

/**
 * @typedef {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral}
 */
google.maps.places.LocationRestriction;

/**
 * Defines photo-requesting options.
 * @record
 */
google.maps.places.PhotoOptions = function() {};

/**
 * The maximum height in pixels of the returned image.
 * @type {?number|undefined}
 */
google.maps.places.PhotoOptions.prototype.maxHeight;

/**
 * The maximum width in pixels of the returned image.
 * @type {?number|undefined}
 */
google.maps.places.PhotoOptions.prototype.maxWidth;

/**
 * Defines information about an aspect of the place that users have reviewed.
 * @record
 */
google.maps.places.PlaceAspectRating = function() {};

/**
 * The rating of this aspect. For individual reviews this is an integer from 0
 * to 3. For aggregated ratings of a place this is an integer from 0 to 30.
 * @type {number}
 */
google.maps.places.PlaceAspectRating.prototype.rating;

/**
 * The aspect type. For example, <code>"food"</code>, <code>"decor"</code>,
 * <code>"service"</code>, or <code>"overall"</code>.
 * @type {string}
 */
google.maps.places.PlaceAspectRating.prototype.type;

/**
 * A Place details query to be sent to the <code>PlacesService</code>.
 * @record
 */
google.maps.places.PlaceDetailsRequest = function() {};

/**
 * Fields to be included in the details response, <a
 * href="https://developers.google.com/maps/billing/understanding-cost-of-use#places-product">which
 * will be billed for</a>. If no fields are specified or
 * <code>[&#39;ALL&#39;]</code> is passed in, all available fields will be
 * returned and billed for (this is not recommended for production deployments).
 * For a list of fields see {@link google.maps.places.PlaceResult}. Nested
 * fields can be specified with dot-paths (for example,
 * <code>"geometry.location"</code>).
 * @type {!Array<string>|undefined}
 */
google.maps.places.PlaceDetailsRequest.prototype.fields;

/**
 * The Place ID of the Place for which details are being requested.
 * @type {string}
 */
google.maps.places.PlaceDetailsRequest.prototype.placeId;

/**
 * Unique reference used to bundle the details request with an autocomplete
 * session.
 * @type {!google.maps.places.AutocompleteSessionToken|undefined}
 */
google.maps.places.PlaceDetailsRequest.prototype.sessionToken;

/**
 * Defines information about the geometry of a Place.
 * @record
 */
google.maps.places.PlaceGeometry = function() {};

/**
 * The Place’s position.
 * @type {!google.maps.LatLng|undefined}
 */
google.maps.places.PlaceGeometry.prototype.location;

/**
 * The preferred viewport when displaying this Place on a map. This property
 * will be <code>null</code> if the preferred viewport for the Place is not
 * known. Only available with {@link
 * google.maps.places.PlacesService.getDetails}.
 * @type {!google.maps.LatLngBounds|undefined}
 */
google.maps.places.PlaceGeometry.prototype.viewport;

/**
 * Defines information about the opening hours of a Place.
 * @record
 */
google.maps.places.PlaceOpeningHours = function() {};

/**
 * Whether the Place is open at the current time.
 * @type {boolean|undefined}
 * @deprecated <code>open_now</code> is deprecated as of November 2019. Use
 *     the {@link google.maps.places.PlaceOpeningHours.isOpen} method from
 *     a {@link google.maps.places.PlacesService.getDetails} result instead. See
 *     <a href="https://goo.gle/js-open-now">https://goo.gle/js-open-now</a>
 */
google.maps.places.PlaceOpeningHours.prototype.open_now;

/**
 * Opening periods covering for each day of the week, starting from Sunday, in
 * chronological order. Days in which the Place is not open are not included.
 * Only available with {@link google.maps.places.PlacesService.getDetails}.
 * @type {!Array<!google.maps.places.PlaceOpeningHoursPeriod>|undefined}
 */
google.maps.places.PlaceOpeningHours.prototype.periods;

/**
 * An array of seven strings representing the formatted opening hours for each
 * day of the week. The Places Service will format and localize the opening
 * hours appropriately for the current language. The ordering of the elements in
 * this array depends on the language. Some languages start the week on Monday
 * while others start on Sunday. Only available with {@link
 * google.maps.places.PlacesService.getDetails}. Other calls may return an empty
 * array.
 * @type {!Array<string>|undefined}
 */
google.maps.places.PlaceOpeningHours.prototype.weekday_text;

/**
 * Check whether the place is open now (when no date is passed), or at the given
 * date. If this place does not have {@link
 * google.maps.places.PlaceResult.utc_offset_minutes} or {@link
 * google.maps.places.PlaceOpeningHours.periods} then <code>undefined</code> is
 * returned ({@link google.maps.places.PlaceOpeningHours.periods} is only
 * available via {@link google.maps.places.PlacesService.getDetails}).
 * @param {!Date=} date
 * @return {boolean|undefined}
 */
google.maps.places.PlaceOpeningHours.prototype.isOpen = function(date) {};

/**
 * Defines structured information about the opening hours of a Place.
 * <strong>Note:</strong> If a Place is <strong>always open</strong>, the
 * <code>close</code> section will be missing from the response. Clients can
 * rely on always-open being represented as an <code>open</code> period
 * containing <code>day</code> with value <code>0</code> and <code>time</code>
 * with value <code>"0000"</code>, and no <code>close</code>.</li>
 * @record
 */
google.maps.places.PlaceOpeningHoursPeriod = function() {};

/**
 * The closing time for the Place.
 * @type {!google.maps.places.PlaceOpeningHoursTime|undefined}
 */
google.maps.places.PlaceOpeningHoursPeriod.prototype.close;

/**
 * The opening time for the Place.
 * @type {!google.maps.places.PlaceOpeningHoursTime}
 */
google.maps.places.PlaceOpeningHoursPeriod.prototype.open;

/**
 * Defines when a Place opens or closes.
 * @record
 */
google.maps.places.PlaceOpeningHoursTime = function() {};

/**
 * The days of the week, as a number in the range [<code>0</code>,
 * <code>6</code>], starting on Sunday. For example, <code>2</code> means
 * Tuesday.
 * @type {number}
 */
google.maps.places.PlaceOpeningHoursTime.prototype.day;

/**
 * The hours of the {@link google.maps.places.PlaceOpeningHoursTime.time} as a
 * number, in the range [<code>0</code>, <code>23</code>]. This will be reported
 * in the Place’s time zone.
 * @type {number}
 */
google.maps.places.PlaceOpeningHoursTime.prototype.hours;

/**
 * The minutes of the {@link google.maps.places.PlaceOpeningHoursTime.time} as a
 * number, in the range [<code>0</code>, <code>59</code>]. This will be reported
 * in the Place’s time zone.
 * @type {number}
 */
google.maps.places.PlaceOpeningHoursTime.prototype.minutes;

/**
 * The timestamp (as milliseconds since the epoch, suitable for use with
 * <code>new Date()</code>) representing the next occurrence of this
 * PlaceOpeningHoursTime. It is calculated from the {@link
 * google.maps.places.PlaceOpeningHoursTime.day} of week, the {@link
 * google.maps.places.PlaceOpeningHoursTime.time}, and the {@link
 * google.maps.places.PlaceResult.utc_offset}. If the {@link
 * google.maps.places.PlaceResult.utc_offset} is <code>undefined</code>, then
 * <code>nextDate</code> will be <code>undefined</code>.
 * @type {number|undefined}
 */
google.maps.places.PlaceOpeningHoursTime.prototype.nextDate;

/**
 * The time of day in 24-hour &quot;hhmm&quot; format. Values are in the range
 * [<code>"0000"</code>, <code>"2359"</code>]. The time will be reported in the
 * Place’s time zone.
 * @type {string}
 */
google.maps.places.PlaceOpeningHoursTime.prototype.time;

/**
 * Represents a photo element of a Place.
 * @record
 */
google.maps.places.PlacePhoto = function() {};

/**
 * The height of the photo in pixels.
 * @type {number}
 */
google.maps.places.PlacePhoto.prototype.height;

/**
 * Attribution text to be displayed for this photo.
 * @type {!Array<string>}
 */
google.maps.places.PlacePhoto.prototype.html_attributions;

/**
 * The width of the photo in pixels.
 * @type {number}
 */
google.maps.places.PlacePhoto.prototype.width;

/**
 * Returns the image URL corresponding to the specified options.
 * @param {!google.maps.places.PhotoOptions=} opts
 * @return {string}
 */
google.maps.places.PlacePhoto.prototype.getUrl = function(opts) {};

/**
 * Defines Open Location Codes or &quot;<a href="https://plus.codes/">plus
 * codes</a>&quot; for a Place. Plus codes can be used as a replacement for
 * street addresses in places where they do not exist (where buildings are not
 * numbered or streets are not named).
 * @record
 */
google.maps.places.PlacePlusCode = function() {};

/**
 * A <a href="https://plus.codes/">plus code</a> with a 1/8000th of a degree by
 * 1/8000th of a degree area where the first four characters (the area code) are
 * dropped and replaced with a locality description. For example, <code>"9G8F+5W
 * Zurich, Switzerland"</code>. If no suitable locality that can be found to
 * shorten the code then this field is omitted.
 * @type {string|undefined}
 */
google.maps.places.PlacePlusCode.prototype.compound_code;

/**
 * A <a href="https://plus.codes/">plus code</a> with a 1/8000th of a degree by
 * 1/8000th of a degree area. For example, <code>"8FVC9G8F+5W"</code>.
 * @type {string}
 */
google.maps.places.PlacePlusCode.prototype.global_code;

/**
 * Defines information about a Place.
 * @record
 */
google.maps.places.PlaceResult = function() {};

/**
 * The collection of address components for this Place’s location. Only
 * available with {@link google.maps.places.PlacesService.getDetails}.
 * @type {!Array<!google.maps.GeocoderAddressComponent>|undefined}
 */
google.maps.places.PlaceResult.prototype.address_components;

/**
 * The representation of the Place’s address in the <a
 * href="http://microformats.org/wiki/adr">adr microformat</a>. Only available
 * with {@link google.maps.places.PlacesService.getDetails}.
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.adr_address;

/**
 * The rated aspects of this Place, based on Google and Zagat user reviews. The
 * ratings are on a scale of 0 to 30.
 * @type {!Array<!google.maps.places.PlaceAspectRating>|undefined}
 */
google.maps.places.PlaceResult.prototype.aspects;

/**
 * A flag indicating the operational status of the Place, if it is a business
 * (indicates whether the place is operational, or closed either temporarily or
 * permanently). If no data is available, the flag is not present in search or
 * details responses.
 * @type {!google.maps.places.BusinessStatus|undefined}
 */
google.maps.places.PlaceResult.prototype.business_status;

/**
 * The Place’s full address.
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.formatted_address;

/**
 * The Place’s phone number, formatted according to the <a
 * href="http://en.wikipedia.org/wiki/Local_conventions_for_writing_telephone_numbers">
 * number&#39;s regional convention</a>. Only available with {@link
 * google.maps.places.PlacesService.getDetails}.
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.formatted_phone_number;

/**
 * The Place’s geometry-related information.
 * @type {!google.maps.places.PlaceGeometry|undefined}
 */
google.maps.places.PlaceResult.prototype.geometry;

/**
 * Attribution text to be displayed for this Place result. Available
 * <code>html_attributions</code> are always returned regardless of what
 * <code>fields</code> have been requested, and must be displayed.
 * @type {!Array<string>|undefined}
 */
google.maps.places.PlaceResult.prototype.html_attributions;

/**
 * URL to an image resource that can be used to represent this Place’s category.
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.icon;

/**
 * The Place’s phone number in international format. International format
 * includes the country code, and is prefixed with the plus (+) sign. Only
 * available with {@link google.maps.places.PlacesService.getDetails}.
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.international_phone_number;

/**
 * The Place’s name. Note: In the case of user entered Places, this is the raw
 * text, as typed by the user. Please exercise caution when using this data, as
 * malicious users may try to use it as a vector for code injection attacks (See
 * <a href="http://en.wikipedia.org/wiki/Code_injection">
 * http://en.wikipedia.org/wiki/Code_injection</a>).
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.name;

/**
 * Defines when the Place opens or closes.
 * @type {!google.maps.places.PlaceOpeningHours|undefined}
 */
google.maps.places.PlaceResult.prototype.opening_hours;

/**
 * A flag indicating whether the Place is closed, either permanently or
 * temporarily. If the place is operational, or if no data is available, the
 * flag is absent from the response.
 * @type {boolean|undefined}
 * @deprecated <code>permanently_closed</code> is deprecated as of May 2020 and
 *     will be turned off in May 2021. Use {@link
 *     google.maps.places.PlaceResult.business_status} instead as
 *     <code>permanently_closed</code> does not distinguish between temporary
 *     and permanent closures.
 */
google.maps.places.PlaceResult.prototype.permanently_closed;

/**
 * Photos of this Place. The collection will contain up to ten {@link
 * google.maps.places.PlacePhoto} objects.
 * @type {!Array<!google.maps.places.PlacePhoto>|undefined}
 */
google.maps.places.PlaceResult.prototype.photos;

/**
 * A unique identifier for the Place.
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.place_id;

/**
 * Defines Open Location Codes or &quot;<a href="https://plus.codes/">plus
 * codes</a>&quot; for the Place.
 * @type {!google.maps.places.PlacePlusCode|undefined}
 */
google.maps.places.PlaceResult.prototype.plus_code;

/**
 * The price level of the Place, on a scale of 0 to 4. Price levels are
 * interpreted as follows: <ul style="list-style-type: none;">
 * <li><code>0</code>: Free <li><code>1</code>: Inexpensive <li><code>2</code>:
 * Moderate <li><code>3</code>: Expensive <li><code>4</code>: Very Expensive
 * </ul>
 * @type {number|undefined}
 */
google.maps.places.PlaceResult.prototype.price_level;

/**
 * A rating, between 1.0 to 5.0, based on user reviews of this Place.
 * @type {number|undefined}
 */
google.maps.places.PlaceResult.prototype.rating;

/**
 * A list of reviews of this Place. Only available with {@link
 * google.maps.places.PlacesService.getDetails}.
 * @type {!Array<!google.maps.places.PlaceReview>|undefined}
 */
google.maps.places.PlaceResult.prototype.reviews;

/**
 * An array of <a
 * href="https://developers.google.com/maps/documentation/places/web-service/supported_types">
 * types for this Place</a> (for example, <code>["political", "locality"]</code>
 * or <code>["restaurant", "establishment"]</code>).
 * @type {!Array<string>|undefined}
 */
google.maps.places.PlaceResult.prototype.types;

/**
 * URL of the official Google page for this place. This is the Google-owned page
 * that contains the best available information about the Place. Only available
 * with {@link google.maps.places.PlacesService.getDetails}.
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.url;

/**
 * The number of user ratings which contributed to this Place’s {@link
 * google.maps.places.PlaceResult.rating}.
 * @type {number|undefined}
 */
google.maps.places.PlaceResult.prototype.user_ratings_total;

/**
 * The offset from UTC of the Place’s current timezone, in minutes. For example,
 * Sydney, Australia in daylight savings is 11 hours ahead of UTC, so the
 * <code>utc_offset</code> will be <code>660</code>. For timezones behind UTC,
 * the offset is negative. For example, the <code>utc_offset</code> is
 * <code>-60</code> for Cape Verde. Only available with {@link
 * google.maps.places.PlacesService.getDetails}.
 * @type {number|undefined}
 * @deprecated <code>utc_offset</code> is deprecated as of November 2019.
 *     Use {@link google.maps.places.PlaceResult.utc_offset_minutes} instead.
 *     See <a href="https://goo.gle/js-open-now">https://goo.gle/js-open-now</a>
 */
google.maps.places.PlaceResult.prototype.utc_offset;

/**
 * The offset from UTC of the Place’s current timezone, in minutes. For example,
 * Sydney, Australia in daylight savings is 11 hours ahead of UTC, so the
 * <code>utc_offset_minutes</code> will be <code>660</code>. For timezones
 * behind UTC, the offset is negative. For example, the
 * <code>utc_offset_minutes</code> is <code>-60</code> for Cape Verde. Only
 * available with {@link google.maps.places.PlacesService.getDetails}.
 * @type {number|undefined}
 */
google.maps.places.PlaceResult.prototype.utc_offset_minutes;

/**
 * The simplified address for the Place, including the street name, street
 * number, and locality, but not the province/state, postal code, or country.
 * For example, Google&#39;s Sydney, Australia office has a vicinity value of
 * <code>"48 Pirrama Road, Pyrmont"</code>. Only available with {@link
 * google.maps.places.PlacesService.getDetails}.
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.vicinity;

/**
 * The authoritative website for this Place, such as a business&#39; homepage.
 * Only available with {@link google.maps.places.PlacesService.getDetails}.
 * @type {string|undefined}
 */
google.maps.places.PlaceResult.prototype.website;

/**
 * Represents a single review of a place.
 * @record
 */
google.maps.places.PlaceReview = function() {};

/**
 * The aspects rated by the review. The ratings on a scale of 0 to 3.
 * @type {!Array<!google.maps.places.PlaceAspectRating>|undefined}
 */
google.maps.places.PlaceReview.prototype.aspects;

/**
 * The name of the reviewer.
 * @type {string}
 */
google.maps.places.PlaceReview.prototype.author_name;

/**
 * A URL to the reviewer&#39;s profile. This will be <code>undefined</code> when
 * the reviewer&#39;s profile is unavailable.
 * @type {string|undefined}
 */
google.maps.places.PlaceReview.prototype.author_url;

/**
 * An IETF language code indicating the language in which this review is
 * written. Note that this code includes only the main language tag without any
 * secondary tag indicating country or region. For example, all the English
 * reviews are tagged as <code>'en'</code> rather than &#39;en-AU&#39; or
 * &#39;en-UK&#39;.
 * @type {string}
 */
google.maps.places.PlaceReview.prototype.language;

/**
 * A URL to the reviwer&#39;s profile image.
 * @type {string}
 */
google.maps.places.PlaceReview.prototype.profile_photo_url;

/**
 * A string of formatted recent time, expressing the review time relative to the
 * current time in a form appropriate for the language and country. For example
 * <code>&quot;a month ago&quot;</code>.
 * @type {string}
 */
google.maps.places.PlaceReview.prototype.relative_time_description;

/**
 * The text of a review.
 * @type {string}
 */
google.maps.places.PlaceReview.prototype.text;

/**
 * Timestamp for the review, expressed in seconds since epoch.
 * @type {number}
 */
google.maps.places.PlaceReview.prototype.time;

/**
 * An object used to fetch additional pages of Places results.
 * @record
 */
google.maps.places.PlaceSearchPagination = function() {};

/**
 * Indicates if further results are available. <code>true</code> when there is
 * an additional results page.
 * @type {boolean}
 */
google.maps.places.PlaceSearchPagination.prototype.hasNextPage;

/**
 * Fetches the next page of results. Uses the same callback function that was
 * provided to the first search request.
 * @return {undefined}
 */
google.maps.places.PlaceSearchPagination.prototype.nextPage = function() {};

/**
 * A Place search query to be sent to the <code>PlacesService</code>.
 * @record
 */
google.maps.places.PlaceSearchRequest = function() {};

/**
 * The bounds within which to search for Places. Both <code>location</code> and
 * <code>radius</code> will be ignored if <code>bounds</code> is set.
 * @type {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.bounds;

/**
 * A term to be matched against all available fields, including but not limited
 * to name, type, and address, as well as customer reviews and other third-party
 * content.
 * @type {string|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.keyword;

/**
 * The location around which to search for Places.
 * @type {!google.maps.LatLng|!google.maps.LatLngLiteral|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.location;

/**
 * Restricts results to only those places at the specified price level or lower.
 * Valid values are in the range from 0 (most affordable) to 4 (most expensive),
 * inclusive. Must be greater than or equal to <code>minPrice </code>, if
 * specified.
 * @type {number|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.maxPriceLevel;

/**
 * Restricts results to only those places at the specified price level or
 * higher. Valid values are in the range from 0 (most affordable) to 4 (most
 * expensive), inclusive. Must be less than or equal to <code>maxPrice</code>,
 * if specified.
 * @type {number|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.minPriceLevel;

/**
 * Restricts the Place search results to Places that include this text in the
 * name.
 * @type {string|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.name;

/**
 * Restricts results to only those places that are open right now.
 * @type {boolean|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.openNow;

/**
 * The distance from the given location within which to search for Places, in
 * meters. The maximum allowed value is 50&thinsp;000.
 * @type {number|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.radius;

/**
 * Specifies the ranking method to use when returning results. Defaults to
 * <code>PROMINENCE</code>. Note that when <code>rankBy</code> is set to
 * <code>DISTANCE</code>, you must specify a <code>location</code> but you
 * cannot specify a <code>radius</code> or <code>bounds</code>.
 * @type {!google.maps.places.RankBy|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.rankBy;

/**
 * Searches for places of the given type. The type is translated to the local
 * language of the request&#39;s target location and used as a query string. If
 * a query is also provided, it is concatenated to the localized type string.
 * Results of a different type are dropped from the response. Use this field to
 * perform language and region independent categorical searches. Valid types are
 * given <a
 * href="https://developers.google.com/maps/documentation/places/web-service/supported_types">here</a>.
 * @type {string|undefined}
 */
google.maps.places.PlaceSearchRequest.prototype.type;

/**
 * Contains methods related to searching for places and retrieving details about
 * a place.
 * @param {!HTMLDivElement|!google.maps.Map} attrContainer
 * @constructor
 */
google.maps.places.PlacesService = function(attrContainer) {};

/**
 * Retrieves a list of places based on a phone number. In most cases there
 * should be just one item in the result list, however if the request is
 * ambiguous more than one result may be returned. The {@link
 * google.maps.places.PlaceResult}s passed to the callback are subsets of a
 * full {@link google.maps.places.PlaceResult}. Your app can get a more
 * detailed {@link google.maps.places.PlaceResult} for each place by
 * calling {@link google.maps.places.PlacesService.getDetails} and passing
 * the {@link google.maps.places.PlaceResult.place_id} for the desired place.
 * @param {!google.maps.places.FindPlaceFromPhoneNumberRequest} request
 * @param {function(?Array<!google.maps.places.PlaceResult>,
 *     !google.maps.places.PlacesServiceStatus): void} callback
 * @return {undefined}
 */
google.maps.places.PlacesService.prototype.findPlaceFromPhoneNumber = function(
    request, callback) {};

/**
 * Retrieves a list of places based on a query string. In most cases there
 * should be just one item in the result list, however if the request is
 * ambiguous more than one result may be returned. The {@link
 * google.maps.places.PlaceResult}s passed to the callback are subsets of a
 * full {@link google.maps.places.PlaceResult}. Your app can get a more
 * detailed {@link google.maps.places.PlaceResult} for each place by
 * calling {@link google.maps.places.PlacesService.getDetails} and passing
 * the {@link google.maps.places.PlaceResult.place_id} for the desired place.
 * @param {!google.maps.places.FindPlaceFromQueryRequest} request
 * @param {function(?Array<!google.maps.places.PlaceResult>,
 *     !google.maps.places.PlacesServiceStatus): void} callback
 * @return {undefined}
 */
google.maps.places.PlacesService.prototype.findPlaceFromQuery = function(
    request, callback) {};

/**
 * Retrieves details about the place identified by the given
 * <code>placeId</code>.
 * @param {!google.maps.places.PlaceDetailsRequest} request
 * @param {function(?google.maps.places.PlaceResult,
 *     !google.maps.places.PlacesServiceStatus): void} callback
 * @return {undefined}
 */
google.maps.places.PlacesService.prototype.getDetails = function(
    request, callback) {};

/**
 * Retrieves a list of places near a particular location, based on keyword or
 * type. Location must always be specified, either by passing a
 * <code>LatLngBounds</code>, or <code>location</code> and <code>radius</code>
 * parameters. The {@link google.maps.places.PlaceResult}s passed to the
 * callback are subsets of the full {@link google.maps.places.PlaceResult}. Your
 * app can get a more detailed {@link google.maps.places.PlaceResult} for each
 * place by sending a <a
 * href="https://developers.google.com/maps/documentation/javascript/places#place_details_requests">Place
 * Details request</a> passing the {@link
 * google.maps.places.PlaceResult.place_id} for the desired place. The {@link
 * google.maps.places.PlaceSearchPagination} object can be used to fetch
 * additional pages of results (null if this is the last page of results or if
 * there is only one page of results).
 * @param {!google.maps.places.PlaceSearchRequest} request
 * @param {function(?Array<!google.maps.places.PlaceResult>,
 *     !google.maps.places.PlacesServiceStatus,
 *     ?google.maps.places.PlaceSearchPagination): void} callback
 * @return {undefined}
 */
google.maps.places.PlacesService.prototype.nearbySearch = function(
    request, callback) {};

/**
 * Retrieves a list of places based on a query string (for example, &quot;pizza
 * in New York&quot;, or &quot;shoe stores near Ottawa&quot;). Location
 * parameters are optional; when the location is specified, results are only
 * biased toward nearby results rather than restricted to places inside the
 * area. Use <code>textSearch</code> when you want to search for places using an
 * arbitrary string, and in cases where you may not want to restrict search
 * results to a particular location. The <code>PlaceSearchPagination</code>
 * object can be used to fetch additional pages of results (null if this is the
 * last page of results or if there is only one page of results).
 * @param {!google.maps.places.TextSearchRequest} request
 * @param {function(?Array<!google.maps.places.PlaceResult>,
 *     !google.maps.places.PlacesServiceStatus,
 *     ?google.maps.places.PlaceSearchPagination): void} callback
 * @return {undefined}
 */
google.maps.places.PlacesService.prototype.textSearch = function(
    request, callback) {};

/**
 * The status returned by the <code>PlacesService</code> on the completion of
 * its searches. Specify these by value, or by using the constant&#39;s name.
 * For example, <code>'OK'</code> or
 * <code>google.maps.places.PlacesServiceStatus.OK</code>.
 * @enum {string}
 */
google.maps.places.PlacesServiceStatus = {
  /**
   * This request was invalid.
   */
  INVALID_REQUEST: 'INVALID_REQUEST',
  /**
   * The place referenced was not found.
   */
  NOT_FOUND: 'NOT_FOUND',
  /**
   * The response contains a valid result.
   */
  OK: 'OK',
  /**
   * The application has gone over its request quota.
   */
  OVER_QUERY_LIMIT: 'OVER_QUERY_LIMIT',
  /**
   * The application is not allowed to use the <code>PlacesService</code>.
   */
  REQUEST_DENIED: 'REQUEST_DENIED',
  /**
   * The <code>PlacesService</code> request could not be processed due to a
   * server error. The request may succeed if you try again.
   */
  UNKNOWN_ERROR: 'UNKNOWN_ERROR',
  /**
   * No result was found for this request.
   */
  ZERO_RESULTS: 'ZERO_RESULTS',
};

/**
 * Represents a prediction substring.
 * @record
 */
google.maps.places.PredictionSubstring = function() {};

/**
 * The length of the substring.
 * @type {number}
 */
google.maps.places.PredictionSubstring.prototype.length;

/**
 * The offset to the substring&#39;s start within the description string.
 * @type {number}
 */
google.maps.places.PredictionSubstring.prototype.offset;

/**
 * Represents a prediction term.
 * @record
 */
google.maps.places.PredictionTerm = function() {};

/**
 * The offset, in unicode characters, of the start of this term in the
 * description of the place.
 * @type {number}
 */
google.maps.places.PredictionTerm.prototype.offset;

/**
 * The value of this term, for example, &quot;Taco Bell&quot;.
 * @type {string}
 */
google.maps.places.PredictionTerm.prototype.value;

/**
 * Represents a single Query Autocomplete prediction.
 * @record
 */
google.maps.places.QueryAutocompletePrediction = function() {};

/**
 * This is the unformatted version of the query suggested by the Places service.
 * @type {string}
 */
google.maps.places.QueryAutocompletePrediction.prototype.description;

/**
 * A set of substrings in the place&#39;s description that match elements in the
 * user&#39;s input, suitable for use in highlighting those substrings. Each
 * substring is identified by an offset and a length, expressed in unicode
 * characters.
 * @type {!Array<!google.maps.places.PredictionSubstring>}
 */
google.maps.places.QueryAutocompletePrediction.prototype.matched_substrings;

/**
 * Only available if prediction is a place. A place ID that can be used to
 * retrieve details about this place using the place details service (see {@link
 * google.maps.places.PlacesService.getDetails}).
 * @type {string|undefined}
 */
google.maps.places.QueryAutocompletePrediction.prototype.place_id;

/**
 * Information about individual terms in the above description. Categorical
 * terms come first (for example, &quot;restaurant&quot;). Address terms appear
 * from most to least specific. For example, &quot;San Francisco&quot;, and
 * &quot;CA&quot;.
 * @type {!Array<!google.maps.places.PredictionTerm>}
 */
google.maps.places.QueryAutocompletePrediction.prototype.terms;

/**
 * An QueryAutocompletion request to be sent to the
 * <code>QueryAutocompleteService</code>.
 * @record
 */
google.maps.places.QueryAutocompletionRequest = function() {};

/**
 * Bounds for prediction biasing. Predictions will be biased towards, but not
 * restricted to, the given <code>bounds</code>. Both <code>location</code> and
 * <code>radius</code> will be ignored if <code>bounds</code> is set.
 * @type {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.places.QueryAutocompletionRequest.prototype.bounds;

/**
 * The user entered input string.
 * @type {string}
 */
google.maps.places.QueryAutocompletionRequest.prototype.input;

/**
 * Location for prediction biasing. Predictions will be biased towards the given
 * <code>location</code> and <code>radius</code>. Alternatively,
 * <code>bounds</code> can be used.
 * @type {!google.maps.LatLng|undefined}
 */
google.maps.places.QueryAutocompletionRequest.prototype.location;

/**
 * The character position in the input term at which the service uses text for
 * predictions (the position of the cursor in the input field).
 * @type {number|undefined}
 */
google.maps.places.QueryAutocompletionRequest.prototype.offset;

/**
 * The radius of the area used for prediction biasing. The <code>radius</code>
 * is specified in meters, and must always be accompanied by a
 * <code>location</code> property. Alternatively, <code>bounds</code> can be
 * used.
 * @type {number|undefined}
 */
google.maps.places.QueryAutocompletionRequest.prototype.radius;

/**
 * Ranking options for a PlaceSearchRequest.
 * @enum {number}
 */
google.maps.places.RankBy = {
  /**
   * Ranks place results by distance from the location.
   */
  DISTANCE: 0,
  /**
   * Ranks place results by their prominence.
   */
  PROMINENCE: 1,
};

/**
 * A widget that provides query predictions based on a user&#39;s text input. It
 * attaches to an input element of type <code>text</code>, and listens for text
 * entry in that field. The list of predictions is presented as a drop-down
 * list, and is updated as text is entered.
 * @param {!HTMLInputElement} inputField
 * @param {?google.maps.places.SearchBoxOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.places.SearchBox = function(inputField, opts) {};

/**
 * Returns the bounds to which query predictions are biased.
 * @return {!google.maps.LatLngBounds|undefined}
 */
google.maps.places.SearchBox.prototype.getBounds = function() {};

/**
 * Returns the query selected by the user to be used with
 * <code>places_changed</code> event.
 * @return {!Array<!google.maps.places.PlaceResult>|undefined}
 */
google.maps.places.SearchBox.prototype.getPlaces = function() {};

/**
 * Sets the region to use for biasing query predictions. Results will only be
 * biased towards this area and not be completely restricted to it.
 * @param {?google.maps.LatLngBounds|?google.maps.LatLngBoundsLiteral} bounds
 * @return {undefined}
 */
google.maps.places.SearchBox.prototype.setBounds = function(bounds) {};

/**
 * The options that can be set on a <code>SearchBox</code> object.
 * @record
 */
google.maps.places.SearchBoxOptions = function() {};

/**
 * The area towards which to bias query predictions. Predictions are biased
 * towards, but not restricted to, queries targeting these bounds.
 * @type {?google.maps.LatLngBounds|?google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.places.SearchBoxOptions.prototype.bounds;

/**
 * Contains structured information about the place&#39;s description, divided
 * into a main text and a secondary text, including an array of matched
 * substrings from the autocomplete input, identified by an offset and a length,
 * expressed in unicode characters.
 * @record
 */
google.maps.places.StructuredFormatting = function() {};

/**
 * This is the main text part of the unformatted description of the place
 * suggested by the Places service. Usually the name of the place.
 * @type {string}
 */
google.maps.places.StructuredFormatting.prototype.main_text;

/**
 * A set of substrings in the main text that match elements in the user&#39;s
 * input, suitable for use in highlighting those substrings. Each substring is
 * identified by an offset and a length, expressed in unicode characters.
 * @type {!Array<!google.maps.places.PredictionSubstring>}
 */
google.maps.places.StructuredFormatting.prototype.main_text_matched_substrings;

/**
 * This is the secondary text part of the unformatted description of the place
 * suggested by the Places service. Usually the location of the place.
 * @type {string}
 */
google.maps.places.StructuredFormatting.prototype.secondary_text;

/**
 * A text search request to be sent to the <code>PlacesService</code>.
 * @record
 */
google.maps.places.TextSearchRequest = function() {};

/**
 * Bounds used to bias results when searching for Places (optional). Both
 * <code>location</code> and <code>radius</code> will be ignored if
 * <code>bounds</code> is set. Results will not be restricted to those inside
 * these bounds; but, results inside it will rank higher.
 * @type {!google.maps.LatLngBounds|!google.maps.LatLngBoundsLiteral|undefined}
 */
google.maps.places.TextSearchRequest.prototype.bounds;

/**
 * The center of the area used to bias results when searching for Places.
 * @type {!google.maps.LatLng|!google.maps.LatLngLiteral|undefined}
 */
google.maps.places.TextSearchRequest.prototype.location;

/**
 * The request&#39;s query term. For example, the name of a place (&#39;Eiffel
 * Tower&#39;), a category followed by the name of a location (&#39;pizza in New
 * York&#39;), or the name of a place followed by a location disambiguator
 * (&#39;Starbucks in Sydney&#39;).
 * @type {string|undefined}
 */
google.maps.places.TextSearchRequest.prototype.query;

/**
 * The radius of the area used to bias results when searching for Places, in
 * meters.
 * @type {number|undefined}
 */
google.maps.places.TextSearchRequest.prototype.radius;

/**
 * Searches for places of the given type. The type is translated to the local
 * language of the request&#39;s target location and used as a query string. If
 * a query is also provided, it is concatenated to the localized type string.
 * Results of a different type are dropped from the response. Use this field to
 * perform language and region independent categorical searches. Valid types are
 * given <a
 * href="https://developers.google.com/maps/documentation/places/web-service/supported_types">here</a>.
 * @type {string|undefined}
 */
google.maps.places.TextSearchRequest.prototype.type;

/**
 * @const
 */
google.maps.visualization = {};

/**
 * A layer that provides a client-side rendered heatmap, depicting the intensity
 * of data at geographical points.
 * @param {google.maps.visualization.HeatmapLayerOptions=} opts
 * @extends {google.maps.MVCObject}
 * @constructor
 */
google.maps.visualization.HeatmapLayer = function(opts) {};

/**
 * Returns the data points currently displayed by this heatmap.
 * @return {!google.maps.MVCArray<!google.maps.LatLng|!google.maps.visualization.WeightedLocation>}
 */
google.maps.visualization.HeatmapLayer.prototype.getData = function() {};

/**
 * @return {!google.maps.Map|undefined}
 */
google.maps.visualization.HeatmapLayer.prototype.getMap = function() {};

/**
 * Sets the data points to be displayed by this heatmap.
 * @param {!google.maps.MVCArray<!google.maps.LatLng|!google.maps.visualization.WeightedLocation>|!Array<!google.maps.LatLng|!google.maps.visualization.WeightedLocation>}
 *     data
 * @return {undefined}
 */
google.maps.visualization.HeatmapLayer.prototype.setData = function(data) {};

/**
 * Renders the heatmap on the specified map. If map is set to <code>null</code>,
 * the heatmap will be removed.
 * @param {?google.maps.Map} map
 * @return {undefined}
 */
google.maps.visualization.HeatmapLayer.prototype.setMap = function(map) {};

/**
 * @param {?google.maps.visualization.HeatmapLayerOptions} options
 * @return {undefined}
 */
google.maps.visualization.HeatmapLayer.prototype.setOptions = function(
    options) {};

/**
 * This object defines the properties that can be set on a
 * <code>HeatmapLayer</code> object.
 * @record
 */
google.maps.visualization.HeatmapLayerOptions = function() {};

/**
 * The data points to display. Required.
 * @type {?google.maps.MVCArray<!google.maps.LatLng|!google.maps.visualization.WeightedLocation>|?Array<!google.maps.LatLng|!google.maps.visualization.WeightedLocation>|undefined}
 */
google.maps.visualization.HeatmapLayerOptions.prototype.data;

/**
 * Specifies whether heatmaps dissipate on zoom. By default, the radius of
 * influence of a data point is specified by the radius option only. When
 * dissipating is disabled, the radius option is interpreted as a radius at zoom
 * level 0.
 * @type {?boolean|undefined}
 */
google.maps.visualization.HeatmapLayerOptions.prototype.dissipating;

/**
 * The color gradient of the heatmap, specified as an array of CSS color
 * strings. All CSS3 colors are supported except for extended named colors.
 * @type {?Array<string>|undefined}
 */
google.maps.visualization.HeatmapLayerOptions.prototype.gradient;

/**
 * The map on which to display the layer.
 * @type {?google.maps.Map|undefined}
 */
google.maps.visualization.HeatmapLayerOptions.prototype.map;

/**
 * The maximum intensity of the heatmap. By default, heatmap colors are
 * dynamically scaled according to the greatest concentration of points at any
 * particular pixel on the map. This property allows you to specify a fixed
 * maximum.
 * @type {?number|undefined}
 */
google.maps.visualization.HeatmapLayerOptions.prototype.maxIntensity;

/**
 * The opacity of the heatmap, expressed as a number between 0 and 1. Defaults
 * to 0.6.
 * @type {?number|undefined}
 */
google.maps.visualization.HeatmapLayerOptions.prototype.opacity;

/**
 * The radius of influence for each data point, in pixels.
 * @type {?number|undefined}
 */
google.maps.visualization.HeatmapLayerOptions.prototype.radius;

/**
 * A data point entry for a heatmap. This is a geographical data point with a
 * weight attribute.
 * @record
 */
google.maps.visualization.WeightedLocation = function() {};

/**
 * The location of the data point.
 * @type {google.maps.LatLng}
 */
google.maps.visualization.WeightedLocation.prototype.location;

/**
 * The weighting value of the data point.
 * @type {number}
 */
google.maps.visualization.WeightedLocation.prototype.weight;
