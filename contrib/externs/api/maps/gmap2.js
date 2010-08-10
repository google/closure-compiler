/*
 * Copyright 2009 Google Inc.
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
 * @fileoverview Externs for Maps API V2
 * @externs
*
 */

/**
 * @constructor
 * @param {Node} container
 * @param {GMapOptions=} options
 * @see http://code.google.com/apis/maps/documentation/reference.html#GMap2
 */
function GMap2(container, options) {}

/**
 * @return {undefined}
 */
GMap2.prototype.enableDragging = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.disableDragging = function() {};

/**
 * @return {boolean}
 */
GMap2.prototype.draggingEnabled = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.enableInfoWindow = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.disableInfoWindow = function() {};

/**
 * @return {boolean}
 */
GMap2.prototype.infoWindowEnabled = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.enableDoubleClickZoom = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.disableDoubleClickZoom = function() {};

/**
 * @return {boolean}
 */
GMap2.prototype.doubleClickZoomEnabled = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.enableContinuousZoom = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.disableContinuousZoom = function() {};

/**
 * @return {boolean}
 */
GMap2.prototype.continuousZoomEnabled = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.enableGoogleBar = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.disableGoogleBar = function() {};

/**
 * @return {boolean}
 */
GMap2.prototype.googleBarEnabled = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.enableScrollWheelZoom = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.disableScrollWheelZoom = function() {};

/**
 * @return {boolean}
 */
GMap2.prototype.scrollWheelZoomEnabled = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.enablePinchToZoom = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.disablePinchToZoom = function() {};

/**
 * @return {boolean}
 */
GMap2.prototype.pinchToZoomEnabled = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.setUIToDefault = function() {};

/**
 * @param {GMapUIOptions} ui
 * @return {undefined}
 */
GMap2.prototype.setUI = function(ui) {};

/**
 * @param {GControl} control
 * @param {GControlPosition=} position
 * @return {undefined}
 */
GMap2.prototype.addControl = function(control, position) {};

/**
 * @param {GControl} control
 * @return {undefined}
 */
GMap2.prototype.removeControl = function(control) {};

/**
 * @return {Element}
 */
GMap2.prototype.getContainer = function() {};

/**
 * @return {Array.<GMapType>}
 */
GMap2.prototype.getMapTypes = function() {};

/**
 * @return {GMapType}
 */
GMap2.prototype.getCurrentMapType = function() {};

/**
 * @param {GMapType} type
 * @return {undefined}
 */
GMap2.prototype.setMapType = function(type) {};

/**
 * @param {GMapType} type
 * @return {undefined}
 */
GMap2.prototype.addMapType = function(type) {};

/**
 * @param {GMapType} type
 * @return {undefined}
 */
GMap2.prototype.removeMapType = function(type) {};

/**
 * @return {boolean};
 */
GMap2.prototype.isLoaded = function() {};

/**
 * @return {GLatLng};
 */
GMap2.prototype.getCenter = function() {};

/**
 * @return {GLatLngBounds};
 */
GMap2.prototype.getBounds = function() {};

/**
 * @param {GLatLngBounds} bounds
 * @return {number};
 */
GMap2.prototype.getBoundsZoomLevel = function(bounds) {};

/**
 * @return {GSize};
 */
GMap2.prototype.getSize = function() {};

/**
 * @return {number};
 */
GMap2.prototype.getZoom = function() {};

/**
 * @return {GDraggableObject};
 */
GMap2.prototype.getDragObject = function() {};

/**
 * @param {function(GEPlugin):undefined} callback
 * @return {Object};
 */
GMap2.prototype.getEarthInstance = function(callback) {};

/**
 * @param {GLatLng} center
 * @param {number=} zoom
 * @param {GMapType=} type
 * @return {undefined}
 */
GMap2.prototype.setCenter = function(center, zoom, type) {};

/**
 * @param {GLatLng} center
 * @return {undefined}
 */
GMap2.prototype.panTo = function(center) {};

/**
 * @param {GSize} distance
 * @return {undefined}
 */
GMap2.prototype.panBy = function(distance) {};

/**
 * @param {number} level
 * @return {undefined}
 */
GMap2.prototype.setZoom = function(level) {};

/**
 * @param {GLatLng=} latlng
 * @param {boolean=} doCenter
 * @param {boolean=} doContinuousZoom
 * @return {undefined}
 */
GMap2.prototype.zoomIn = function(latlng, doCenter, doContinuousZoom) {};

/**
 * @return {undefined}
 */
GMap2.prototype.savePosition = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.returnToSavedPosition = function() {};

/**
 * @return {undefined}
 */
GMap2.prototype.checkResize = function() {};

/**
 * @param {GOverlay} overlay
 * @return {undefined}
 */
GMap2.prototype.addOverlay = function(overlay) {};

/**
 * @param {GOverlay} overlay
 * @return {undefined}
 */
GMap2.prototype.removeOverlay = function(overlay) {};

/**
 * @return {undefined}
 */
GMap2.prototype.clearOverlays = function() {};

/**
 * @param {GMapPane} pane
 * @return {Object}
 */
GMap2.prototype.getPane = function(pane) {};

/**
 * @param {GLatLng} latlng
 * @param {Node} node
 * @param {GInfoWindowOptions=} opts
 * @return {undefined}
 */
GMap2.prototype.openInfoWindow = function(latlng, node, opts) {};

/**
 * @param {GLatLng} latlng
 * @param {string} html
 * @param {GInfoWindowOptions=} opts
 * @return {undefined}
 */
GMap2.prototype.openInfoWindowHtml = function(latlng, html, opts) {};

/**
 * @param {GLatLng} latlng
 * @param {Array.<GInfoWindowOptions>} tabs
 * @param {GInfoWindowOptions=} opts
 * @return {undefined}
 */
GMap2.prototype.openInfoWindowTabs = function(latlng, tabs, opts) {};

/**
 * @param {GLatLng} latlng
 * @param {GInfoWindowOptions=} opts
 * @return {undefined}
 */
GMap2.prototype.showMapBlowup = function(latlng, opts) {};

/**
 * @param {Array.<GInfoWindowOptions>} tabs
 * @param {Function=} onupdate
 * @return {undefined}
 */
GMap2.prototype.updateInfoWindow = function(tabs, onupdate) {};

/**
 * @param {Function} modifier
 * @param {Function=} onupdate
 * @return {undefined}
 */
GMap2.prototype.updateCurrentTab = function(modifier, onupdate) {};

/**
 * @return {undefined}
 */
GMap2.prototype.closeInfoWindow = function() {};

/**
 * @return {GInfoWindow}
 */
GMap2.prototype.getInfoWindow = function() {};

/**
 * @param {GPoint} pixel
 * @return {GLatLng}
 */
GMap2.prototype.fromContainerPixelToLatLng = function(pixel) {};

/**
 * @param {GLatLng} latlng
 * @return {GPoint}
 */
GMap2.prototype.fromLatLngToContainerPixel = function(latlng) {};

/**
 * @param {GLatLng} latlng
 * @return {GPoint}
 */
GMap2.prototype.fromLatLngToDivPixel = function(latlng) {};

/**
 * @param {GPoint} pixel
 * @return {GLatLng}
 */
GMap2.prototype.fromDivPixelToLatLng = function(pixel) {};


/**
 * @constructor
 * @param {Array.<GPoint>} points
 * @see http://code.google.com/apis/maps/documentation/reference.html#GBounds
 */
function GBounds(points) {}

/**
 * @type{number}
 */
GBounds.prototype.minX;

/**
 * @type{number}
 */
GBounds.prototype.minY;

/**
 * @type{number}
 */
GBounds.prototype.maxX;

/**
 * @type{number}
 */
GBounds.prototype.maxY;

/**
 * @param {GBounds} other
 * @return {boolean}
 */
GBounds.prototype.equals = function(other) {};

/**
 * @return {GPoint}
 */
GBounds.prototype.mid = function() {};

/**
 * @return {GPoint}
 */
GBounds.prototype.min = function() {};

/**
 * @return {GPoint}
 */
GBounds.prototype.max = function() {};

/**
 * @param {GBounds} other
 * @return {boolean}
 */
GBounds.prototype.containsBounds = function(other) {};

/**
 * @param {GPoint} point
 * @return {boolean}
 */
GBounds.prototype.containsPoint = function(point) {};

/**
 * @param {GPoint} point
 * @return {undefined}
 */
GBounds.prototype.extend = function(point) {};

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GBrowserIsCompatible.GBrowserIsCompatible
 * @return {boolean}
 */
function GBrowserIsCompatible() {}

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GDraggableObject
 * @constructor
 * @param {Node} src
 * @param {GDraggableObjectOptions=} opts
 */
function GDraggableObject(src, opts) {}

/**
 * @param {string} cursor
 * @return {undefined}
 */
GDraggableObject.setDraggableCursor = function(cursor) {};

/**
 * @param {string} cursor
 * @return {undefined}
 */
GDraggableObject.setDragingCursor = function(cursor) {};

/**
 * @deprecated
 * @return {string}
 */
GDraggableObject.getDraggableCursor = function() {};

/**
 * @deprecated
 * @return {string}
 */
GDraggableObject.getDraggingCursor = function() {};

/**
 * @param {string} cursor
 * @return {undefined}
 */
GDraggableObject.prototype.setDraggableCursor = function(cursor) {};

/**
 * @param {string} cursor
 * @return {undefined}
 */
GDraggableObject.prototype.setDragingCursor = function(cursor) {};

/**
 * @param {GPoint} point
 * @return {undefined}
 */
GDraggableObject.prototype.moveTo = function(point) {};

/**
 * @param {GSize} size
 * @return {undefined}
 */
GDraggableObject.prototype.moveBy = function(size) {};

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GDraggableObjectOptions
 * @private
 * @constructor
 */
function GDraggableObjectOptions() {}

/**
 * @type {number}
 */
GDraggableObjectOptions.prototype.left;

/**
 * @type {number}
 */
GDraggableObjectOptions.prototype.top;

/**
 * @type {Node}
 */
GDraggableObjectOptions.prototype.container;

/**
 * @type {string}
 */
GDraggableObjectOptions.prototype.draggableCursor;

/**
 * @type {string}
 */
GDraggableObjectOptions.prototype.draggingCursor;

/**
 * @deprecated
 * @type {boolean}
 */
GDraggableObjectOptions.prototype.delayDrag;

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GInfoWindow
 * @private
 * @constructor
 */
function GInfoWindow() {}

/**
 * @param {number} index
 * @return {undefined}
 */
GInfoWindow.prototype.selectTab = function(index) {};

/**
 * @return {undefined}
 */
GInfoWindow.prototype.hide = function() {};

/**
 * @return {undefined}
 */
GInfoWindow.prototype.show = function() {};

/**
 * @return {boolean}
 */
GInfoWindow.prototype.isHidden = function() {};

/**
 * @param {GLatLng} latlng
 * @param {Array.<GInfoWindowTab>} tabs
 * @param {GSize} size
 * @param {GSize=} offset
 * @param {number=} selectedTab
 * @return {undefined}
 */
GInfoWindow.prototype.reset = function(latlng, tabs, size, offset, selectedTab) {};

/**
 * @return {GLatLng}
 */
GInfoWindow.prototype.getPoint = function() {};

/**
 * @return {GSize}
 */
GInfoWindow.prototype.getPixelOffset = function() {};

/**
 * @return {number}
 */
GInfoWindow.prototype.getSelectedTab = function() {};

/**
 * @return {Array.<GInfoWindowTab>}
 */
GInfoWindow.prototype.getTabs = function() {};

/**
 * @return {Array.<Node>}
 */
GInfoWindow.prototype.getContentContainers = function() {};

/**
 * @return {undefined}
 */
GInfoWindow.prototype.enableMaximize = function() {};

/**
 * @return {undefined}
 */
GInfoWindow.prototype.disableMaximize = function() {};

/**
 * @return {undefined}
 */
GInfoWindow.prototype.maximize = function() {};

/**
 * @return {undefined}
 */
GInfoWindow.prototype.restore = function() {};

/**
 * @constructor
 * @private
 */
function GInfoWindowOptions() {}

/**
 * @type {number}
 */
GInfoWindowOptions.prototype.selectedTab;

/**
 * @type {number}
 */
GInfoWindowOptions.prototype.maxWidth;

/**
 * @type {boolean}
 */
GInfoWindowOptions.prototype.noCloseOnClick;

/**
 * @type {Function}
 */
GInfoWindowOptions.prototype.onOpenFn;

/**
 * @type {Function}
 */
GInfoWindowOptions.prototype.onCloseFn;

/**
 * @type {number}
 */
GInfoWindowOptions.prototype.zoomLevel;

/**
 * @type {GMapType}
 */
GInfoWindowOptions.prototype.mapType;

/**
 * @type {string}
 */
GInfoWindowOptions.prototype.maxContent;

/**
 * @type {string}
 */
GInfoWindowOptions.prototype.maxTitle;

/**
 * @type {GSize}
 */
GInfoWindowOptions.prototype.pixelOffset;

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GKeyboardHandler
 * @constructor
 * @param {GMap2} map
 */
function GKeyboardHandler(map) {}

// Namespace
var GLanguage = {};

/**
 * @return {string}
 */
GLanguage.getLanguageCode = function() {};

/**
 * @return {boolean}
 */
GLanguage.isRtl = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GLatLng
 * @constructor
 * @param {number} lat
 * @param {number} lng
 * @param {boolean=} unbounded
 */
function GLatLng(lat, lng, unbounded) {}

/**
 * @return {number}
 */
GLatLng.prototype.lat = function() {};

/**
 * @return {number}
 */
GLatLng.prototype.lng = function() {};

/**
 * @return {number}
 */
GLatLng.prototype.latRadians = function() {};

/**
 * @return {number}
 */
GLatLng.prototype.lngRadians = function() {};

/**
 * @param {GLatLng} other
 * @return {boolean}
 */
GLatLng.prototype.equals = function(other) {};

/**
 * @param {GLatLng} other
 * @param {number=} radius
 * @return {number}
 */
GLatLng.prototype.distanceFrom = function(other, radius) {};

/**
 * @param {number=} precision
 * @return {string}
 */
GLatLng.prototype.toUrlValue = function(precision) {};

/**
 * @param {string} latlng
 * @return {GLatLng}
 */
GLatLng.fromUrlValue = function(latlng) {};

/**
 * @deprecated
 * @type {number}
 */
GLatLng.prototype.x;

/**
 * @deprecated
 * @type {number}
 */
GLatLng.prototype.y;

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GLatLngBounds
 * @constructor
 * @param {GLatLng=} sw
 * @param {GLatLng=} ne
 */
function GLatLngBounds(sw, ne) {}

/**
 * @param {GLatLngBounds} other
 * @return {boolean}
 */
GLatLngBounds.prototype.equals = function(other) {};

/**
 * @deprecated
 * @param {GLatLng} latlng
 * @return {boolean}
 */
GLatLngBounds.prototype.contains = function(latlng) {};

/**
 * @param {GLatLng} latlng
 * @return {boolean}
 */
GLatLngBounds.prototype.containsLatLng = function(latlng) {};

/**
 * @param {GLatLngBounds} other
 * @return {boolean}
 */
GLatLngBounds.prototype.intersects = function(other) {};

/**
 * @param {GLatLngBounds} other
 * @return {boolean}
 */
GLatLngBounds.prototype.containsBounds = function(other) {};

/**
 * @param {GLatLng} latlng
 * @return {undefined}
 */
GLatLngBounds.prototype.extend = function(latlng) {};

/**
 * @return {GLatLng}
 */
GLatLngBounds.prototype.getSouthWest = function() {};

/**
 * @return {GLatLng}
 */
GLatLngBounds.prototype.getNorthEast = function() {};

/**
 * @return {GLatLng}
 */
GLatLngBounds.prototype.toSpan = function() {};

/**
 * @return {boolean}
 */
GLatLngBounds.prototype.isFullLat = function() {};

/**
 * @return {boolean}
 */
GLatLngBounds.prototype.isFullLng = function() {};

/**
 * @return {boolean}
 */
GLatLngBounds.prototype.isEmpty = function() {};

/**
 * @return {GLatLng}
 */
GLatLngBounds.prototype.getCenter = function() {};

// namespace
var GLog = {}

/**
 * @param {string} message
 * @param {string=} color
 * @return {undefined}
 */
GLog.write = function(message, color) {};

/**
 * @param {string} url
 * @return {undefined}
 */
GLog.writeUrl = function(url) {};

/**
 * @param {string} html
 * @return {undefined}
 */
GLog.writeHtml = function(html) {};

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GMapOptions
 * @constructor
 * @private
 */
function GMapOptions() {}

/**
 * @type {GSize}
 */
GMapOptions.prototype.size;

/**
 * @type {Array.<GMapType>}
 */
GMapOptions.prototype.mapTypes;

/**
 * @type {string}
 */
GMapOptions.prototype.draggableCursor;

/**
 * @type {string}
 */
GMapOptions.prototype.draggingCursor;

/**
 * @type {GGoogleBarOptions}
 */
GMapOptions.prototype.googleBarOptions;

/**
 * @type {string}
 */
GMapOptions.prototype.backgroundColor;

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GMapPane
 * @enum {*}
 */
var GMapPane = {
 G_MAP_MAP_PANE : {},
 G_MAP_OVERLAY_LAYER_PANE : {},
 G_MAP_MARKER_SHADOW_PANE : {},
 G_MAP_MARKER_PANE : {},
 G_MAP_FLOAT_SHADOW_PANE : {},
 G_MAP_MARKER_MOUSE_TARGET_PANE : {},
 G_MAP_FLOAT_PANE : {}
};

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GPoint
 * @constructor
 * @param {number} x
 * @param {number} y
 */
function GPoint(x, y) {}

/**
 * @type {number}
 */
GPoint.prototype.x;

/**
 * @type {number}
 */
GPoint.prototype.y;

/**
 * @param {GPoint} other
 * @return {boolean}
 */
GPoint.prototype.equals = function(other) {};

/**
 * @return {String}
 */
GPoint.prototype.toString = function() {};

/**
 * @type {GPoint}
 */
GPoint.ORIGIN;

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GSize
 * @constructor
 * @param {number} width
 * @param {number} height
 */
function GSize(width, height) {}

/**
 * @type {number}
 */
GSize.prototype.width;

/**
 * @type {number}
 */
GSize.prototype.height;

/**
 * @param {GSize} other
 * @return {boolean}
 */
GSize.prototype.equals = function(other) {};

/**
 * @return {String}
 */
GSize.prototype.toString = function() {};

/**
 * @type {GSize}
 */
GSize.ZERO;

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GUnload
 * @return {undefined}
 */
function GUnload() {};

/**
 * @return {undefined}
 */
var G_API_VERSION = function() {};

// namespace
var GEvent = {};

/**
 * @param {Object} source
 * @param {string} event
 * @param {Function} handler
 * @return {GEventListener}
 */
GEvent.addListener = function(source, event, handler) {};

/**
 * @param {Node} source
 * @param {string} event
 * @param {Function} handler
 * @return {GEventListener}
 */
GEvent.addDomListener = function(source, event, handler) {};

/**
 * @param {GEventListener} handle
 * @return {undefined}
 */
GEvent.removeListener = function(handle) {};

/**
 * @param {Object|Node} source
 * @param {string} event
 * @return {undefined}
 */
GEvent.clearListeners = function(source, event) {};

/**
 * @param {Object|Node} source
 * @return {undefined}
 */
GEvent.clearInstanceListeners = function(source) {};

/**
 * @param {Object|Node} source
 * @return {undefined}
 */
GEvent.clearNode = function(source) {};

/**
 * @param {Object|Node} source
 * @param {string} event
 * @param {...Function} handlers
 * @return {undefined}
 */
GEvent.trigger = function(source, event, handlers) {};

/**
 * @param {Object} source
 * @param {string} event
 * @param {Object} object
 * @param {Function} method
 * @return {GEventListener}
 */
GEvent.bind = function(source, event, object, method) {};

/**
 * @param {Node} source
 * @param {string} event
 * @param {Object} object
 * @param {Function} method
 * @return {GEventListener}
 */
GEvent.bindDom = function(source, event, object, method) {};

/**
 * @param {Object} object
 * @param {Function} method
 * @param {...*} args
 * @return {Function}
 */
GEvent.callback = function(object, method, args) {};

/**
 * @constructor
 * @see http://code.google.com/apis/maps/documentation/reference.html#GEventListener
 */
function GEventListener() {}

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GControl
 * @constructor
 * @param {boolean?} printable
 * @param {boolean?} selectable
 */
function GControl(printable, selectable) {}

/**
 * @return {boolean}
 */
GControl.prototype.printable = function() {};

/**
 * @return {boolean}
 */
GControl.prototype.selectable = function() {};

/**
 * @param {GMap2} map
 * @return {Element}
 */
GControl.prototype.initialize = function(map) {};

/**
 * @return {GControlPosition}
 */
GControl.prototype.getDefaultPosition = function() {};

/**
 * @return {boolean}
 */
GControl.prototype.allowSetVisibility = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GControlAnchor
 * @enum {*}
 */
var GControlAnchor = {
 G_ANCHOR_TOP_RIGHT : {},
 G_ANCHOR_TOP_LEFT : {},
 G_ANCHOR_BOTTOM_RIGHT : {},
 G_ANCHOR_BOTTOM_LEFT : {}
};

/**
 * @extends {GControl}
 * @constructor
 */
function GSmallMapControl() {}

// TODO(user): Note that we don't need these declarations
// but the compiler current warns about unimplemented interface.
// I am going to remove that warning for externs. Once that's
// done, remove the extra declarations.
/**
 * @return {boolean}
 */
GSmallMapControl.prototype.printable = function() {};

/**
 * @return {boolean}
 */
GSmallMapControl.prototype.selectable = function() {};

/**
 * @param {GMap2} map
 * @return {!Node}
 */
GSmallMapControl.prototype.initialize = function(map) {};

/**
 * @return {GControlPosition}
 */
GSmallMapControl.prototype.getDefaultPosition = function() {};

/**
 * @extends GControl
 * @constructor
 */
function GLargeMapControl() {}

// TODO(user): Note that we don't need these declarations
// but the compiler current warns about unimplemented interface.
// I am going to remove that warning for externs. Once that's
// done, remove the extra declarations.
/**
 * @return {boolean}
 */
GLargeMapControl.prototype.printable = function() {};

/**
 * @return {boolean}
 */
GLargeMapControl.prototype.selectable = function() {};

/**
 * @param {GMap2} map
 * @return {!Node}
 */
GLargeMapControl.prototype.initialize = function(map) {};

/**
 * @return {GControlPosition}
 */
GLargeMapControl.prototype.getDefaultPosition = function() {};

/**
 * @extends GControl
 * @constructor
 */
function GSmallZoomControl() {}

// TODO(user): Note that we don't need these declarations
// but the compiler current warns about unimplemented interface.
// I am going to remove that warning for externs. Once that's
// done, remove the extra declarations.
/**
 * @return {boolean}
 */
GSmallZoomControl.prototype.printable = function() {};

/**
 * @return {boolean}
 */
GSmallZoomControl.prototype.selectable = function() {};

/**
 * @param {GMap2} map
 * @return {!Node}
 */
GSmallZoomControl.prototype.initialize = function(map) {};

/**
 * @return {GControlPosition}
 */
GSmallZoomControl.prototype.getDefaultPosition = function() {};

/**
 * @extends GControl
 * @constructor
 */
function GLargeMapControl3D() {}

// TODO(user): Note that we don't need these declarations
// but the compiler current warns about unimplemented interface.
// I am going to remove that warning for externs. Once that's
// done, remove the extra declarations.
/**
 * @return {boolean}
 */
GLargeMapControl3D.prototype.printable = function() {};

/**
 * @return {boolean}
 */
GLargeMapControl3D.prototype.selectable = function() {};

/**
 * @param {GMap2} map
 * @return {!Node}
 */
GLargeMapControl3D.prototype.initialize = function(map) {};

/**
 * @return {GControlPosition}
 */
GLargeMapControl3D.prototype.getDefaultPosition = function() {};

/**
 * @extends GControl
 * @constructor
 */
function GSmallZoomControl3D() {}

// TODO(user): Note that we don't need these declarations
// but the compiler current warns about unimplemented interface.
// I am going to remove that warning for externs. Once that's
// done, remove the extra declarations.
/**
 * @return {boolean}
 */
GSmallZoomControl3D.prototype.printable = function() {};

/**
 * @return {boolean}
 */
GSmallZoomControl3D.prototype.selectable = function() {};

/**
 * @param {GMap2} map
 * @return {!Node}
 */
GSmallZoomControl3D.prototype.initialize = function(map) {};

/**
 * @return {GControlPosition}
 */
GSmallZoomControl3D.prototype.getDefaultPosition = function() {};

/**
 * @extends GControl
 * @constructor
 */
function GScaleControl() {}

// TODO(user): Note that we don't need these declarations
// but the compiler current warns about unimplemented interface.
// I am going to remove that warning for externs. Once that's
// done, remove the extra declarations.
/**
 * @return {boolean}
 */
GScaleControl.prototype.printable = function() {};

/**
 * @return {boolean}
 */
GScaleControl.prototype.selectable = function() {};

/**
 * @param {GMap2} map
 * @return {!Node}
 */
GScaleControl.prototype.initialize = function(map) {};

/**
 * @return {GControlPosition}
 */
GScaleControl.prototype.getDefaultPosition = function() {};


/**
 * @extends GControl
 * @constructor
 */
function GMenuMapTypeControl() {}

// TODO(user): Note that we don't need these declarations
// but the compiler current warns about unimplemented interface.
// I am going to remove that warning for externs. Once that's
// done, remove the extra declarations.
/**
 * @return {boolean}
 */
GMenuMapTypeControl.prototype.printable = function() {};

/**
 * @return {boolean}
 */
GMenuMapTypeControl.prototype.selectable = function() {};

/**
 * @param {GMap2} map
 * @return {!Node}
 */
GMenuMapTypeControl.prototype.initialize = function(map) {};

/**
 * @return {GControlPosition}
 */
GMenuMapTypeControl.prototype.getDefaultPosition = function() {};

/**
 * @extends GControl
 * @constructor
 */
function GOverviewMapControl() {}

// TODO(user): Note that we don't need these declarations
// but the compiler current warns about unimplemented interface.
// I am going to remove that warning for externs. Once that's
// done, remove the extra declarations.
/**
 * @return {boolean}
 */
GOverviewMapControl.prototype.printable = function() {};

/**
 * @return {boolean}
 */
GOverviewMapControl.prototype.selectable = function() {};

/**
 * @param {GMap2} map
 * @return {!Node}
 */
GOverviewMapControl.prototype.initialize = function(map) {};

/**
 * @return {GControlPosition}
 */
GOverviewMapControl.prototype.getDefaultPosition = function() {};

/**
 * @extends GControl
 * @constructor
 */
function GNavLabelControl() {}

// TODO(user): Note that we don't need these declarations
// but the compiler current warns about unimplemented interface.
// I am going to remove that warning for externs. Once that's
// done, remove the extra declarations.
/**
 * @return {boolean}
 */
GNavLabelControl.prototype.printable = function() {};

/**
 * @return {boolean}
 */
GNavLabelControl.prototype.selectable = function() {};

/**
 * @param {GMap2} map
 * @return {!Node}
 */
GNavLabelControl.prototype.initialize = function(map) {};

/**
 * @return {GControlPosition}
 */
GNavLabelControl.prototype.getDefaultPosition = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GControlPosition
 * @constructor
 * @param {GControlAnchor} anchor
 * @param {GSize} offset
 */
function GControlPosition(anchor, offset) {}

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GHierarchicalMapTypeControl
 * @constructor
 */
function GHierarchicalMapTypeControl() {}

/**
 * @param {GMapType} parentType
 * @param {GMapType} childType
 * @param {string=} childText
 * @param {boolean=} isDefault
 * @return {undefined}
 */
GHierarchicalMapTypeControl.prototype.addRelationship = function(
    parentType, childType, childText, isDefault) {};

/**
 * @param {GMapType} mapType
 * @return {undefined}
 */
GHierarchicalMapTypeControl.prototype.removeRelationship = function(mapType) {};

/**
 * @return {undefined}
 */
GHierarchicalMapTypeControl.prototype.clearRelationships = function() {};

// TODO(user): Note that we don't need these declarations
// but the compiler current warns about unimplemented interface.
// I am going to remove that warning for externs. Once that's
// done, remove the extra declarations.
/**
 * @return {boolean}
 */
GHierarchicalMapTypeControl.prototype.printable = function() {};

/**
 * @return {boolean}
 */
GHierarchicalMapTypeControl.prototype.selectable = function() {};

/**
 * @param {GMap2} map
 * @return {!Node}
 */
GHierarchicalMapTypeControl.prototype.initialize = function(map) {};

/**
 * @return {GControlPosition}
 */
GHierarchicalMapTypeControl.prototype.getDefaultPosition = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GMapType
 * @constructor
 * @param {Array.<GTileLayer>} layers
 * @param {GProjection} projection
 * @param {string} name
 * @param {GMapTypeOptions=} opts
 */
function GMapType(layers, projection, name, opts) {}

/**
 * @param {GLatLng} center
 * @param {GLatLng} span
 * @param {GSize} viewSize
 * @return {number}
 */
GMapType.prototype.getSpanZoomLevel = function(center, span, viewSize) {};

/**
 * @param {GLatLngBounds} bounds
 * @param {GSize} viewSize
 * @return {undefined}
 */
GMapType.prototype.getBoundsZoomLevel = function(bounds, viewSize) {};

/**
 * @param {boolean} shortName
 * @return {string}
 */
GMapType.prototype.getName = function(shortName) {};

/**
 * @return {GProjection}
 */
GMapType.prototype.getProjection = function() {};

/**
 * @return {number}
 */
GMapType.prototype.getTileSize = function() {};

/**
 * @return {Array.<GTileLayer>}
 */
GMapType.prototype.getTileLayers = function() {};

/**
 * @return {number}
 */
GMapType.prototype.getMinimumResolution = function() {};

/**
 * @return {number}
 */
GMapType.prototype.getMaximumResolution = function() {};

/**
 * @param {GLatLng} latlng
 * @param {Function} callback
 * @param {number=} targetZoom
 * @return {undefined}
 */
GMapType.prototype.getMaxZoomAtLatLng = function(latlng, callback, targetZoom) {};

/**
 * @return {string}
 */
GMapType.prototype.getTextColor = function() {};

/**
 * @return {string}
 */
GMapType.prototype.getLinkColor = function() {};

/**
 * @return {string}
 */
GMapType.prototype.getErrorMessage = function() {};

/**
 * @param {GLatLngBounds} bounds
 * @param {number} zoom
 * @return {Array.<string>}
 */
GMapType.prototype.getCopyrights = function(bounds, zoom) {};

/**
 * @return {string}
 */
GMapType.prototype.getUrlArg = function() {};

/**
 * @return {string}
 */
GMapType.prototype.getAlt = function() {};

/**
 * @type {GMapType}
 */
GMapType.prototype.G_NORMAL_MAP;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_SATELLITE_MAP;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_HYBRID_MAP;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_PHYSICAL_MAP;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_MAPMAKER_NORMAL_MAP;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_MAPMAKER_HYBRID_MAP;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_MOON_ELEVATION_MAP;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_MOON_VISIBLE_MAP;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_MARS_ELEVATION_MAP;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_MARS_VISIBLE_MAP;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_MARS_INFRARED_MAP;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_SKY_VISIBLE_MAP;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_SATELLITE_3D_MAP;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_DEFAULT_MAP_TYPES;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_MAPMAKER_MAP_TYPES;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_MOON_MAP_TYPES;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_MARS_MAP_TYPES;

/**
 * @type {GMapType}
 */
GMapType.prototype.G_SKY_MAP_TYPES;

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GMapTypeControl
 * @extends GControl
 * @constructor
 */
function GMapTypeControl(useShortNames) {}

// TODO(user): Note that we don't need these declarations
// but the compiler current warns about unimplemented interface.
// I am going to remove that warning for externs. Once that's
// done, remove the extra declarations.
/**
 * @return {boolean}
 */
GMapTypeControl.prototype.printable = function() {};

/**
 * @return {boolean}
 */
GMapTypeControl.prototype.selectable = function() {};

/**
 * @param {GMap2} map
 * @return {!Node}
 */
GMapTypeControl.prototype.initialize = function(map) {};

/**
 * @return {GControlPosition}
 */
GMapTypeControl.prototype.getDefaultPosition = function() {};
/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GMapTypeOptions
 * @constructor
 * @private
 */
function GMapTypeOptions() {}

/**
 * @type {string}
 */
GMapTypeOptions.prototype.shortName;

/**
 * @type {string}
 */
GMapTypeOptions.prototype.urlArg;

/**
 * @type {number}
 */
GMapTypeOptions.prototype.maxResolution;

/**
 * @type {number}
 */
GMapTypeOptions.prototype.minResolution;

/**
 * @type {number}
 */
GMapTypeOptions.prototype.tileSize;

/**
 * @type {string}
 */
GMapTypeOptions.prototype.textColor;

/**
 * @type {string}
 */
GMapTypeOptions.prototype.linkColor;

/**
 * @type {string}
 */
GMapTypeOptions.prototype.errorMessage;

/**
 * @type {string}
 */
GMapTypeOptions.prototype.alt;

/**
 * @type {number}
 */
GMapTypeOptions.prototype.radius;

/**
 * @see http://code.google.com/apis/maps/documentation/reference.html#GMapUIOptions
 * @constructor
 * @param {GSize=} size
 */
function GMapUIOptions(size) {}

// namespace
GMapUIOptions.prototype.maptypes = {};

/**
 * @type {boolean}
 */
GMapUIOptions.prototype.maptypes.normal;

/**
 * @type {boolean}
 */
GMapUIOptions.prototype.maptypes.satellite;

/**
 * @type {boolean}
 */
GMapUIOptions.prototype.maptypes.hybrid;

/**
 * @type {boolean}
 */
GMapUIOptions.prototype.maptypes.physical;

// namespace
GMapUIOptions.prototype.zoom = {};

/**
 * @type {boolean}
 */
GMapUIOptions.prototype.zoom.scrollwheel;

/**
 * @type {boolean}
 */
GMapUIOptions.prototype.zoom.doubleclick;

/**
 * @type {boolean}
 */
GMapUIOptions.prototype.keyboard;

// namespace
GMapUIOptions.prototype.controls;

/**
 * @type {boolean}
 */
GMapUIOptions.prototype.controls.largemapcontrol3d

/**
 * @type {boolean}
 */
GMapUIOptions.prototype.controls.smallzoomcontrol3d

/**
 * @type {boolean}
 */
GMapUIOptions.prototype.controls.maptypecontrol

/**
 * @type {boolean}
 */
GMapUIOptions.prototype.controls.menumaptypecontrol

/**
 * @type {boolean}
 */
GMapUIOptions.prototype.controls.scalecontrol

// TODO(user): NOT finished, but these are there for
// proper type resolution of the finished declarations.
// ------------------------------------------------------

/**
 * @constructor
 */
function GGoogleBarOptions() {}

/**
 * @constructor
 */
function GOverlay() {}

/**
 * @constructor
 */
function GEPlugin() {}

/**
 * @constructor
 * @param {string} label
 * @param {Node|string} content
 */
function GInfoWindowTab(label, content) {}

/**
 * @constructor
 * @param {GCopyrightCollection} copyrights
 * @param {number} minResolution
 * @param {number} maxResolution
 * @param {Object=} options
 */
function GTileLayer(copyrights, minResolution, maxResolution, options) {}

GTileLayer.prototype.minResolution = function() {}
GTileLayer.prototype.maxResolution = function() {}
GTileLayer.prototype.getTileUrl = function(tile, zoom) {}
GTileLayer.prototype.isPng = function(){}
GTileLayer.prototype.getOpacity = function() {}

/**
 * @constructor
 */
function GProjection() {}

function GDownloadUrl() {}
GLanguage.isRtl;

// Namespaces
var GGeoAddressAccuracy;
var GGeoStatusCode;
var GXml;
var GXmlHttp;
var GXslt;

// Classes
/** @constructor */ function GClientGeocoder(opt_cache) {}
/** @constructor */ function GCopyright(id,  bounds,  minZoom,  text) {}
/** @constructor */ function GCopyrightCollection(opt_prefix) {}
/** @constructor */ function GFactualGeocodeCache() {}
/** @constructor */ function GGeocodeCache() {}
/** @constructor */ function GGeoXml(urlOfXml) {}
/** @constructor */ function GGroundOverlay(imageUrl, bounds) {}

/** @constructor */ function GIcon(opt_copy, opt_image) {}

GIcon.image;
GIcon.shadow;
GIcon.iconSize;
GIcon.shadowSize;
GIcon.iconAnchor;
GIcon.infoWindowAnchor;

/**
 * @constructor
 * @extends {GOverlay}
 * @param {GLatLng} latlng
 * @param {Object=} opts
 */
function GMarker(latlng, opts) {}

/**
 * @param {Node} content
 * @param {GInfoWindowOptions=} opts
 */
GMarker.prototype.bindInfoWindow = function(content, opts) {};

/**
 * @param {Array.<GInfoWindowTab>} tabs
 * @param {GInfoWindowOptions=} opts
 */
GMarker.prototype.bindInfoWindowTabs = function(tabs, opts) {};

/** @constructor */ function GMarkerManager(map, opt_opts) {}
/** @constructor */ function GMarkerManagerOptions() {}

/**
 * @constructor
 * @extends {GProjection}
 * @param {number} zoomlevels
 */
function GMercatorProjection(zoomlevels) {}

/**
 * @constructor
 * @extends {GOverlay}
 */
function GPolygon(points,  opt_strokeColor, opt_strokeWeight,
                  opt_strokeOpacity, opt_fillColor,  opt_fillOpacity) {}

/** @constructor */ function GPolyline(points, opt_color,
                                       opt_weight, opt_opacity) {}

/**
 * @constructor
 * @extends {GOverlay}
 * @param {GTileLayer} tileLayer
 */
function GTileLayerOverlay(tileLayer) {}

/**
 * @constructor
 * @param {Node} container
 * @param {Object=} opt_opts
 */
function GStreetviewPanorama(container, opt_opts) {}

GStreetviewPanorama.prototype.remove = function() {};

/**
 * @param {Node} container
 */
GStreetviewPanorama.prototype.setContainer = function(container) {};

GStreetviewPanorama.prototype.checkResize = function() {};

GStreetviewPanorama.prototype.hide = function() {};

GStreetviewPanorama.prototype.show = function() {};

/**
 * @return {boolean}
 */
GStreetviewPanorama.prototype.isHidden = function() {};

/**
 * @return {GPov}
 */
GStreetviewPanorama.prototype.getPOV = function() {};

/**
 * @param {GPov} pov
 */
GStreetviewPanorama.prototype.setPOV = function(pov) {};

/**
 * @param {GPov} pov
 * @param {boolean=} longRoute
 */
GStreetviewPanorama.prototype.panTo = function(pov, longRoute) {};

/**
 * @param {GLatLng} latlng
 * @param {GPov=} pov
 */
GStreetviewPanorama.prototype.setLocationAndPOV = function(latlng, pov) {};

/**
 * @param {number} yaw
 */
GStreetviewPanorama.prototype.followLink = function(yaw) {};

/**
 * @param {number} yaw
 */
GStreetviewPanorama.prototype.yawchanged = function(yaw) {};

/**
 * @param {number} pitch
 */
GStreetviewPanorama.prototype.pitchchanged = function(pitch) {};

/**
 * @param {number} zoom
 */
GStreetviewPanorama.prototype.zoomchanged = function(zoom) {};

/**
 * @param {GStreetviewLocation} location
 */
GStreetviewPanorama.prototype.initialized = function(location) {};

/**
 * @constructor
 * @extends {GOverlay}
 */
function GStreetviewOverlay() {}

/**
 * @param {boolean} hasStreetviewData
 */
GStreetviewOverlay.prototype.changed = function(hasStreetviewData) {};

/**
 * @constructor
 */
function GPov() {}

/**
 * @constructor
 */
function GStreetviewLocation() {}

var G_ANCHOR_BOTTOM_LEFT;
var G_ANCHOR_BOTTOM_RIGHT;
var G_ANCHOR_TOP_LEFT;
var G_ANCHOR_TOP_RIGHT;
var G_DEFAULT_ICON;
var G_DEFAULT_MAP_TYPES;
var G_GEO_BAD_KEY;
var G_GEO_MISSING_ADDRESS;
var G_GEO_SERVER_ERROR;
var G_GEO_SUCCESS;
var G_GEO_UNKNOWN_ADDRESS;
var G_HYBRID_MAP;
var G_MAP_FLOAT_PANE;
var G_MAP_FLOAT_SHADOW_PANE;
var G_MAP_MAP_PANE;
var G_MAP_MARKER_MOUSE_TARGET_PANE;
var G_MAP_MARKER_PANE;
var G_MAP_MARKER_SHADOW_PANE;
var G_NORMAL_MAP;
var G_PHYSICAL_MAP;
var G_SATELLITE_MAP;
var G_UNAVAILABLE_ADDRESS;

var mapsMethods = {};

mapsMethods.addControl;
mapsMethods.addDomListener;
mapsMethods.addListener;
mapsMethods.addMapType;
mapsMethods.addMarker;
mapsMethods.addMarkers;
mapsMethods.addOverlay;
mapsMethods.addRelationship;
mapsMethods.addCopyright;
mapsMethods.bind;
mapsMethods.bindDom;
mapsMethods.callback;
mapsMethods.callbackArgs;
mapsMethods.checkResize;
mapsMethods.checkTileRange;
mapsMethods.clearInstanceListeners;
mapsMethods.clearListeners;
mapsMethods.clearOverlays;
mapsMethods.clearRelationships;
mapsMethods.closeInfoWindow;
mapsMethods.contains;
mapsMethods.containsPoint;
mapsMethods.containsBounds;
mapsMethods.continuousZoomEnabled;
mapsMethods.copy;
mapsMethods.create;
mapsMethods.disableContinuousZoom;
mapsMethods.disableDoubleClickZoom;
mapsMethods.disableDragging;
mapsMethods.disableInfoWindow;
mapsMethods.disableScrollWheelZoom;
mapsMethods.distanceFrom;
mapsMethods.doubleClickZoomEnabled;
mapsMethods.draggable;
mapsMethods.draggingEnabled;
mapsMethods.enableContinuousZoom;
mapsMethods.enableDoubleClickZoom;
mapsMethods.enableDragging;
mapsMethods.enableGoogleBar;
mapsMethods.enableInfoWindow;
mapsMethods.enableScrollWheelZoom;
mapsMethods.equals;
mapsMethods.extend;
mapsMethods.fromContainerPixelToLatLng;
mapsMethods.fromDivPixelToLatLng;
mapsMethods.fromEncoded;
mapsMethods.fromLatLngToDivPixel;
mapsMethods.fromLatLngToPixel;
mapsMethods.fromPixelToLatLng;
mapsMethods.get;
mapsMethods.getBounds;
mapsMethods.getBoundsZoomLevel;
mapsMethods.getCache;
mapsMethods.getCenter;
mapsMethods.getContainer;
mapsMethods.getContentContainers;
mapsMethods.getCopyrights;
mapsMethods.getCurrentMapType;
mapsMethods.getDefaultPosition;
mapsMethods.getErrorMessage;
mapsMethods.getIcon;
mapsMethods.getInfoWindow;
mapsMethods.getLatLng;
mapsMethods.getLinkColor;
mapsMethods.getLocations;
mapsMethods.getMapTypes;
mapsMethods.getMarkerCount;
mapsMethods.getMaximumResolution;
mapsMethods.getMinimumResolution;
mapsMethods.getName;
mapsMethods.getNorthEast;
mapsMethods.getOpacity;
mapsMethods.getPane;
mapsMethods.getPixelOffset;
mapsMethods.getPoint;
mapsMethods.getProjection;
mapsMethods.getSelectedTab;
mapsMethods.getSize;
mapsMethods.getSouthWest;
mapsMethods.getSpanZoomLevel;
mapsMethods.getTabs;
mapsMethods.getTextColor;
mapsMethods.getTileLayer;
mapsMethods.getTileLayers;
mapsMethods.getTileSize;
mapsMethods.getTileUrl;
mapsMethods.getUrlArg;
mapsMethods.getVertex;
mapsMethods.getVertex;
mapsMethods.getVertexCount;
mapsMethods.getWrapWidth;
mapsMethods.getZIndex;
mapsMethods.getZoom;
mapsMethods.hide;
mapsMethods.infoWindowEnabled;
mapsMethods.initialize;
mapsMethods.intersection;
mapsMethods.intersects;
mapsMethods.isCachable;
mapsMethods.isEmpty;
mapsMethods.isFullLat;
mapsMethods.isFullLng;
mapsMethods.isHidden;
mapsMethods.isLoaded;
mapsMethods.isPng;
mapsMethods.lat;
mapsMethods.latRadians;
mapsMethods.lng;
mapsMethods.lngRadians;
mapsMethods.max;
mapsMethods.maxResolution;
mapsMethods.min;
mapsMethods.minResolution;
mapsMethods.openInfoWindow;
mapsMethods.openInfoWindowHtml;
mapsMethods.openInfoWindowTabs;
mapsMethods.openInfoWindowTabsHtml;
mapsMethods.panBy;
mapsMethods.panDirection;
mapsMethods.panTo;
mapsMethods.parse;
mapsMethods.printable;
mapsMethods.put;
mapsMethods.redraw;
mapsMethods.refresh;
mapsMethods.remove;
mapsMethods.removeControl;
mapsMethods.removeListener;
mapsMethods.removeMapType;
mapsMethods.removeOverlay;
mapsMethods.removeRelationship;
mapsMethods.reset;
mapsMethods.returnToSavedPosition;
mapsMethods.savePosition;
mapsMethods.scrollWheelZoomEnabled;
mapsMethods.selectable;
mapsMethods.selectTab;
mapsMethods.setCache;
mapsMethods.setCenter;
mapsMethods.setDraggableCursor;
mapsMethods.setDraggingCursor;
mapsMethods.setImage;
mapsMethods.setMapType;
mapsMethods.setPoint;
mapsMethods.setZoom;
mapsMethods.show;
mapsMethods.showMapBlowup;
mapsMethods.showMapBlowup;
mapsMethods.tileCheckRange;
mapsMethods.toCanonical;
mapsMethods.toSpan;
mapsMethods.toUrlValue;
mapsMethods.transformToHtml;
mapsMethods.trigger;
mapsMethods.value;
mapsMethods.write;
mapsMethods.writeHtml;
mapsMethods.writeUrl;
mapsMethods.zoomIn;
mapsMethods.zoomOut;
mapsMethods.setLatLng;
