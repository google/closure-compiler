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
 * @fileoverview Externs for the Google Maps V2 API.
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html
 * @externs
 */

google.maps = {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GMap2.GMap2
 * @constructor
 * @param {Node} container
 * @param {Object.<string, *>=} opts
 */
function GMap2(container, opts) {}

/** @typedef {GMap2} */
google.maps.Map2;

/** @return {undefined} */
GMap2.prototype.enableDragging = function() {};

/** @return {undefined} */
GMap2.prototype.disableDragging = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GMap2.prototype.draggingEnabled = function() {};

/** @return {undefined} */
GMap2.prototype.enableInfoWindow = function() {};

/** @return {undefined} */
GMap2.prototype.disableInfoWindow = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GMap2.prototype.infoWindowEnabled = function() {};

/** @return {undefined} */
GMap2.prototype.enableDoubleClickZoom = function() {};

/** @return {undefined} */
GMap2.prototype.disableDoubleClickZoom = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GMap2.prototype.doubleClickZoomEnabled = function() {};

/** @return {undefined} */
GMap2.prototype.enableContinuousZoom = function() {};

/** @return {undefined} */
GMap2.prototype.disableContinuousZoom = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GMap2.prototype.continuousZoomEnabled = function() {};

/** @return {undefined} */
GMap2.prototype.enableGoogleBar = function() {};

/** @return {undefined} */
GMap2.prototype.disableGoogleBar = function() {};

/** @return {undefined} */
GMap2.prototype.enableScrollWheelZoom = function() {};

/** @return {undefined} */
GMap2.prototype.disableScrollWheelZoom = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GMap2.prototype.scrollWheelZoomEnabled = function() {};

/** @return {undefined} */
GMap2.prototype.enablePinchToZoom = function() {};

/** @return {undefined} */
GMap2.prototype.disablePinchToZoom = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GMap2.prototype.pinchToZoomEnabled = function() {};

/**
 * @nosideeffects
 * @return {Object}
 */
GMap2.prototype.getDefaultUI = function() {};

/** @return {undefined} */
GMap2.prototype.setUIToDefault = function() {};

/** @param {GMapUIOptions} ui */
GMap2.prototype.setUI = function(ui) {};

/**
 * @param {GControl} control
 * @param {GControlPosition=} position
 */
GMap2.prototype.addControl = function(control, position) {};

/** @param {GControl} control */
GMap2.prototype.removeControl = function(control) {};

/**
 * @nosideeffects
 * @return {Node}
 */
GMap2.prototype.getContainer = function() {};

/**
 * @nosideeffects
 * @return {Array.<GMapType>}
 */
GMap2.prototype.getMapTypes = function() {};

/**
 * @nosideeffects
 * @return {GMapType}
 */
GMap2.prototype.getCurrentMapType = function() {};

/** @param {GMapType} type */
GMap2.prototype.setMapType = function(type) {};

/** @param {GMapType} type */
GMap2.prototype.addMapType = function(type) {};

/** @param {GMapType} type */
GMap2.prototype.removeMapType = function(type) {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GMap2.prototype.isLoaded = function() {};

/**
 * @nosideeffects
 * @return {GLatLng}
 */
GMap2.prototype.getCenter = function() {};

/**
 * @nosideeffects
 * @return {GLatLngBounds}
 */
GMap2.prototype.getBounds = function() {};

/**
 * @nosideeffects
 * @param {GLatLngBounds} bounds
 * @return {number}
 */
GMap2.prototype.getBoundsZoomLevel = function(bounds) {};

/**
 * @nosideeffects
 * @return {GSize}
 */
GMap2.prototype.getSize = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
GMap2.prototype.getZoom = function() {};

/**
 * @nosideeffects
 * @return {GDraggableObject}
 */
GMap2.prototype.getDragObject = function() {};

/** @param {function(Object)} callback */
GMap2.prototype.getEarthInstance = function(callback) {};

/**
 * @param {GLatLng} center
 * @param {number=} zoom
 * @param {GMapType=} type
 */
GMap2.prototype.setCenter = function(center, zoom, type) {};

/** @param {GLatLng} center */
GMap2.prototype.panTo = function(center) {};

/** @param {GSize} distance */
GMap2.prototype.panBy = function(distance) {};

/**
 * @param {number} dx
 * @param {number} dy
 */
GMap2.prototype.panDirection = function(dx, dy) {};

/** @param {number} level */
GMap2.prototype.setZoom = function(level) {};

/**
 * @param {GLatLng=} latlng
 * @param {boolean=} doCenter
 * @param {boolean=} doContinuousZoom
 */
GMap2.prototype.zoomIn = function(latlng, doCenter, doContinuousZoom) {};

/**
 * @param {GLatLng=} latlng
 * @param {boolean=} doContinuousZoom
 */
GMap2.prototype.zoomOut = function(latlng, doContinuousZoom) {};

/** @return {undefined} */
GMap2.prototype.savePosition = function() {};

/** @return {undefined} */
GMap2.prototype.returnToSavedPosition = function() {};

/** @return {undefined} */
GMap2.prototype.checkResize = function() {};

/** @param {GOverlay} overlay */
GMap2.prototype.addOverlay = function(overlay) {};

/** @param {GOverlay} overlay */
GMap2.prototype.removeOverlay = function(overlay) {};

/** @return {undefined} */
GMap2.prototype.clearOverlays = function() {};

/**
 * @nosideeffects
 * @param {GMapPane} pane
 * @return {Node}
 */
GMap2.prototype.getPane = function(pane) {};

/**
 * @param {GLatLng} latlng
 * @param {Node} node
 * @param {Object.<string, *>=} opts
 */
GMap2.prototype.openInfoWindow = function(latlng, node, opts) {};

/**
 * @param {GLatLng} latlng
 * @param {string} html
 * @param {Object.<string, *>=} opts
 */
GMap2.prototype.openInfoWindowHtml = function(latlng, html, opts) {};

/**
 * @param {GLatLng} latlng
 * @param {Array.<GInfoWindowTab>} tabs
 * @param {Object.<string,*>=} opts
 */
GMap2.prototype.openInfoWindowTabs = function(latlng, tabs, opts) {};

/**
 * @param {GLatLng} latlng
 * @param {Array.<GInfoWindowTab>} tabs
 * @param {Object.<string, *>=} opts
 */
GMap2.prototype.openInfoWindowTabsHtml = function(latlng, tabs, opts) {};

/**
 * @param {GLatLng} latlng
 * @param {Object.<string, *>=} opts
 */
GMap2.prototype.showMapBlowup = function(latlng, opts) {};

/**
 * @param {Array.<GInfoWindowTab>} tabs
 * @param {function()=} onupdate
 */
GMap2.prototype.updateInfoWindow = function(tabs, onupdate) {};

/**
 * @param {function(GInfoWindowTab)} modifier
 * @param {function()=} onupdate
 */
GMap2.prototype.updateCurrentTab = function(modifier, onupdate) {};

/** @return {undefined} */
GMap2.prototype.closeInfoWindow = function() {};

/**
 * @nosideeffects
 * @return {GInfoWindow}
 */
GMap2.prototype.getInfoWindow = function() {};

/**
 * @nosideeffects
 * @param {GPoint} pixel
 * @return {GLatLng}
 */
GMap2.prototype.fromContainerPixelToLatLng = function(pixel) {};

/**
 * @nosideeffects
 * @param {GLatLng} latlng
 * @return {GPoint}
 */
GMap2.prototype.fromLatLngToContainerPixel = function(latlng) {};

/**
 * @nosideeffects
 * @param {GLatLng} latlng
 * @return {GPoint}
 */
GMap2.prototype.fromLatLngToDivPixel = function(latlng) {};

/**
 * @nosideeffects
 * @param {GPoint} pixel
 * @return {GLatLng}
 */
GMap2.prototype.fromDivPixelToLatLng = function(pixel) {};

/** @param {number=} level */
GMap2.prototype.enableRotation = function(level) {};

/** @return {undefined} */
GMap2.prototype.disableRotation = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GMap2.prototype.rotationEnabled = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GMap2.prototype.isRotatable = function() {};

/** @param {number} heading */
GMap2.prototype.changeHeading = function(heading) {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GBounds.GBounds
 * @constructor
 * @param {Array.<GPoint>} points
 */
function GBounds(points) {}

/** @typedef {GBounds} */
google.maps.Bounds;

/**
 * @nosideeffects
 * @return {string}
 */
GBounds.prototype.toString = function() {};

/**
 * @nosideeffects
 * @param {GBounds} other
 * @return {boolean}
 */
GBounds.prototype.equals = function(other) {};

/** @return {GPoint} */
GBounds.prototype.mid = function() {};

/**
 * @nosideeffects
 * @return {GPoint}
 */
GBounds.prototype.min = function() {};

/**
 * @nosideeffects
 * @return {GPoint}
 */
GBounds.prototype.max = function() {};

/**
 * @nosideeffects
 * @param {GBounds} other
 * @return {boolean}
 */
GBounds.prototype.containsBounds = function(other) {};

/**
 * @nosideeffects
 * @param {GPoint} point
 * @return {boolean}
 */
GBounds.prototype.containsPoint = function(point) {};

/** @param {GPoint} point */
GBounds.prototype.extend = function(point) {};

/** @type {number} */
GBounds.prototype.minX;

/** @type {number} */
GBounds.prototype.minY;

/** @type {number} */
GBounds.prototype.maxX;

/** @type {number} */
GBounds.prototype.maxY;

/**
 * @nosideeffects
 * @return {boolean}
 */
function GBrowserIsCompatible() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
google.maps.BrowserIsCompatible = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GDraggableObject.GDraggableObject
 * @constructor
 * @param {Node} src
 * @param {Object.<string, *>=} opts
 */
function GDraggableObject(src, opts) {}

/** @typedef {GDraggableObject} */
google.maps.DraggableObject;

/** @param {string} cursor */
GDraggableObject.setDraggableCursor = function(cursor) {};

/** @param {string} cursor */
GDraggableObject.setDraggingCursor = function(cursor) {};

/**
 * @nosideeffects
 * @return {string}
 * @deprecated
 */
GDraggableObject.getDraggingCursor = function() {};

/**
 * @nosideeffects
 * @return {string}
 * @deprecated
 */
GDraggableObject.getDraggableCursor = function() {};

/** @param {string} cursor */
GDraggableObject.prototype.setDraggableCursor = function(cursor) {};

/** @param {string} cursor */
GDraggableObject.prototype.setDraggingCursor = function(cursor) {};

/** @param {GPoint} point */
GDraggableObject.prototype.moveTo = function(point) {};

/** @param {GSize} size */
GDraggableObject.prototype.moveBy = function(size) {};

/**
 * @constructor
 * @private
 */
function GInfoWindow() {};

/** @param {number} index */
GInfoWindow.prototype.selectTab = function(index) {};

/** @return {undefined} */
GInfoWindow.prototype.hide = function() {};

/** @return {undefined} */
GInfoWindow.prototype.show = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GInfoWindow.prototype.isHidden = function() {};

/**
 * @param {GLatLng} latlng
 * @param {Array.<GInfoWindowTab>} tabs
 * @param {GSize} size
 * @param {GSize=} offset
 * @param {number=} selectedTab
 */
GInfoWindow.prototype.reset =
    function(latlng, tabs, size, offset, selectedTab) {};

/**
 * @nosideeffects
 * @return {GLatLng}
 */
GInfoWindow.prototype.getPoint = function() {};

/**
 * @nosideeffects
 * @return {GSize}
 */
GInfoWindow.prototype.getPixelOffset = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
GInfoWindow.prototype.getSelectedTab = function() {};

/**
 * @nosideeffects
 * @return {Array.<GInfoWindowTab>}
 */
GInfoWindow.prototype.getTabs = function() {};

/**
 * @nosideeffects
 * @return {Array.<Node>}
 */
GInfoWindow.prototype.getContentContainers = function() {};

/** @return {undefined} */
GInfoWindow.prototype.enableMaximize = function() {};

/** @return {undefined} */
GInfoWindow.prototype.disableMaximize = function() {};

/**
 * @nosideeffects
 */
GInfoWindow.prototype.maximize = function() {};

/** @return {undefined} */
GInfoWindow.prototype.restore = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GInfoWindowTab.GInfoWindowTab
 * @constructor
 * @param {string} label
 * @param {Node|string} content
 */
function GInfoWindowTab(label, content) {}

/** @typedef {GInfoWindowTab} */
google.maps.InfoWindowTab;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GKeyboardHandler.GKeyboardHandler
 * @constructor
 * @param {GMap2} map
 */
function GKeyboardHandler(map) {}

/** @typedef {GKeyboardHandler} */
google.maps.KeyboardHandler;

var GLanguage = {};

google.maps.Language = {};

/**
 * @nosideeffects
 * @return {string}
 */
GLanguage.getLanguageCode = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GLanguage.isRtl = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GLatLng.GLatLng
 * @constructor
 * @param {number} lat
 * @param {number} lng
 * @param {boolean=} unbounded
 */
function GLatLng(lat, lng, unbounded) {}

/** @typedef {GLatLng} */
google.maps.LatLng;

/**
 * @nosideeffects
 * @return {number}
 */
GLatLng.prototype.lat = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
GLatLng.prototype.lng = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
GLatLng.prototype.latRadians = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
GLatLng.prototype.lngRadians = function() {};

/**
 * @nosideeffects
 * @param {GLatLng} other
 * @return {boolean}
 */
GLatLng.prototype.equals = function(other) {};

/**
 * @nosideeffects
 * @param {GLatLng} other
 * @param {number=} radius
 * @return {number}
 */
GLatLng.prototype.distanceFrom = function(other, radius) {};

/**
 * @nosideeffects
 * @param {number=} precision
 * @return {string}
 */
GLatLng.prototype.toUrlValue = function(precision) {};

/**
 * @nosideeffects
 * @param {string} latlng
 * @return {GLatLng}
 */
GLatLng.fromUrlValue = function(latlng) {};

/** @type {number} */
GLatLng.prototype.x;

/** @type {number} */
GLatLng.prototype.y;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GLatLngBounds.GLatLngBounds
 * @constructor
 * @param {GLatLng=} sw
 * @param {GLatLng=} ne
 */
function GLatLngBounds(sw, ne) {}

/** @typedef {GLatLngBounds} */
google.maps.LatLngBounds;

/**
 * @nosideeffects
 * @param {GLatLngBounds} other
 * @return {boolean}
 */
GLatLngBounds.prototype.equals = function(other) {};

/**
 * @nosideeffects
 * @param {GLatLng} latlng
 * @return {boolean}
 */
GLatLngBounds.prototype.containsLatLng = function(latlng) {};

/**
 * @nosideeffects
 * @param {GLatLngBounds} other
 * @return {boolean}
 */
GLatLngBounds.prototype.intersects = function(other) {};

/**
 * @nosideeffects
 * @param {GLatLngBounds} other
 * @return {boolean}
 */
GLatLngBounds.prototype.containsBounds = function(other) {};

/** @param {GLatLng} latlng */
GLatLngBounds.prototype.extend = function(latlng) {};

/**
 * @nosideeffects
 * @return {GLatLng}
 */
GLatLngBounds.prototype.getSouthWest = function() {};

/**
 * @nosideeffects
 * @return {GLatLng}
 */
GLatLngBounds.prototype.getNorthEast = function() {};

/**
 * @nosideeffects
 * @return {GLatLng}
 */
GLatLngBounds.prototype.toSpan = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GLatLngBounds.prototype.isFullLat = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GLatLngBounds.prototype.isFullLng = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GLatLngBounds.prototype.isEmpty = function() {};

/**
 * @nosideeffects
 * @return {GLatLng}
 */
GLatLngBounds.prototype.getCenter = function() {};

var GLog = {};

google.maps.Log = {};

/**
 * @param {string} message
 * @param {string=} color
 */
GLog.write = function(message, color) {};

/** @param {string} url */
GLog.writeUrl = function(url) {};

/** @param {string} html */
GLog.writeHtml = function(html) {};

/**
 * @constructor
 * @private
 */
function GMapPane() {};

/** @typedef {GMapPane} */
google.maps.MapPane;

/**
 * @const
 * @type {GMapPane}
 */
var G_MAP_MAP_PANE;

/**
 * @const
 * @type {google.maps.MapPane}
 */
google.maps.MAP_MAP_PANE;

/**
 * @const
 * @type {GMapPane}
 */
var G_MAP_OVERLAY_LAYER_PANE;

/**
 * @const
 * @type {google.maps.MapPane}
 */
google.maps.MAP_OVERLAY_LAYER_PANE;

/**
 * @const
 * @type {GMapPane}
 */
var G_MAP_MARKER_SHADOW_PANE;

/**
 * @const
 * @type {google.maps.MapPane}
 */
google.maps.MAP_MARKER_SHADOW_PANE;

/**
 * @const
 * @type {GMapPane}
 */
var G_MAP_MARKER_PANE;

/**
 * @const
 * @type {google.maps.MapPane}
 */
google.maps.MAP_MARKER_PANE;

/**
 * @const
 * @type {GMapPane}
 */
var G_MAP_FLOAT_SHADOW_PANE;

/**
 * @const
 * @type {google.maps.MapPane}
 */
google.maps.MAP_FLOAT_SHADOW_PANE;

/**
 * @const
 * @type {GMapPane}
 */
var G_MAP_MARKER_MOUSE_TARGET_PANE;

/**
 * @const
 * @type {google.maps.MapPane}
 */
google.maps.MAP_MARKER_MOUSE_TARGET_PANE;

/**
 * @const
 * @type {GMapPane}
 */
var G_MAP_FLOAT_PANE;

/**
 * @const
 * @type {google.maps.MapPane}
 */
google.maps.MAP_FLOAT_PANE;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GPoint.GPoint
 * @constructor
 * @param {number} x
 * @param {number} y
 */
function GPoint(x, y) {}

/** @typedef {GPoint} */
google.maps.Point;

/**
 * @nosideeffects
 * @param {GPoint} other
 * @return {boolean}
 */
GPoint.prototype.equals = function(other) {};

/**
 * @nosideeffects
 * @return {string}
 */
GPoint.prototype.toString = function() {};

/** @type {number} */
GPoint.prototype.x;

/** @type {number} */
GPoint.prototype.y;

/**
 * @const
 * @type {GPoint}
 */
GPoint.ORIGIN;

/**
 * @const
 * @type {google.maps.Point}
 */
google.maps.Point.ORIGIN;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GSize.GSize
 * @constructor
 * @param {number} width
 * @param {number} height
 */
function GSize(width, height) {}

/** @typedef {GSize} */
google.maps.Size;

/**
 * @nosideeffects
 * @param {GSize} other
 * @return {boolean}
 */
GSize.prototype.equals = function(other) {};

/**
 * @nosideeffects
 * @return {string}
 */
GSize.prototype.toString = function() {};

/** @type {number} */
GSize.prototype.width;

/** @type {number} */
GSize.prototype.height;

/**
 * @const
 * @type {GSize}
 */
GSize.ZERO;

/**
 * @const
 * @type {google.maps.Size}
 */
google.maps.Size.ZERO;

/** @return {undefined} */
function GUnload() {};

/** @return {undefined} */
google.maps.Unload = function() {};

/**
 * @const
 * @type {string}
 */
var G_API_VERSION;

/**
 * @const
 * @type {string}
 */
google.maps.API_VERSION;

var GEvent = {};

google.maps.Event = {};

/**
 * @param {Object} source
 * @param {string} event
 * @param {function(*=)} handler
 * @return {GEventListener}
 */
GEvent.addListener = function(source, event, handler) {};

/**
 * @param {Node} source
 * @param {string} event
 * @param {function(*=)} handler
 * @return {GEventListener}
 */
GEvent.addDomListener = function(source, event, handler) {};

/** @param {GEventListener} handle */
GEvent.removeListener = function(handle) {};

/**
 * @param {Object|Node} source
 * @param {string} event
 */
GEvent.clearListeners = function(source, event) {};

/** @param {Object|Node} source */
GEvent.clearInstanceListeners = function(source) {};

/** @param {Node} source */
GEvent.clearNode = function(source) {};

/**
 * @param {Object} source
 * @param {string} event
 * @param {...*} var_args
 */
GEvent.trigger = function(source, event, var_args) {};

/**
 * @param {Object} source
 * @param {string} event
 * @param {Object} object
 * @param {function(*=)} method
 * @return {GEventListener}
 */
GEvent.bind = function(source, event, object, method) {};

/**
 * @param {Node} source
 * @param {string} event
 * @param {Object} object
 * @param {function(*=)} method
 * @return {GEventListener}
 */
GEvent.bindDom = function(source, event, object, method) {};

/**
 * @param {Object} object
 * @param {function(*=)} method
 * @return {function()}
 */
GEvent.callback = function(object, method) {};

/**
 * @param {Object} object
 * @param {function(...*)} method
 * @param {...*} var_args
 * @return {function(...*)}
 */
GEvent.callbackArgs = function(object, method, var_args) {};

/**
 * @constructor
 * @private
 */
function GEventListener() {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GControl.GControl
 * @constructor
 * @param {boolean=} printable
 * @param {boolean=} selectable
 */
function GControl(printable, selectable) {}

/** @typedef {GControl} */
google.maps.Control;

/**
 * @nosideeffects
 * @return {boolean}
 */
GControl.prototype.printable = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GControl.prototype.selectable = function() {};

/**
 * @param {GMap2} map
 * @return {Node}
 */
GControl.prototype.initialize = function(map) {};

/**
 * @nosideeffects
 * @return {GControlPosition}
 */
GControl.prototype.getDefaultPosition = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GControl.prototype.allowSetVisibility = function() {};

/**
 * @constructor
 * @private
 */
function GControlAnchor() {};

/** @typedef {GControlAnchor} */
google.maps.ControlAnchor;

/**
 * @const
 * @type {GControlAnchor}
 */
var G_ANCHOR_TOP_RIGHT;

/**
 * @const
 * @type {google.maps.ControlAnchor}
 */
google.maps.ANCHOR_TOP_RIGHT;

/**
 * @const
 * @type {GControlAnchor}
 */
var G_ANCHOR_TOP_LEFT;

/**
 * @const
 * @type {google.maps.ControlAnchor}
 */
google.maps.ANCHOR_TOP_LEFT;

/**
 * @const
 * @type {GControlAnchor}
 */
var G_ANCHOR_BOTTOM_RIGHT;

/**
 * @const
 * @type {google.maps.ControlAnchor}
 */
google.maps.ANCHOR_BOTTOM_RIGHT;

/**
 * @const
 * @type {GControlAnchor}
 */
var G_ANCHOR_BOTTOM_LEFT;

/**
 * @const
 * @type {google.maps.ControlAnchor}
 */
google.maps.ANCHOR_BOTTOM_LEFT;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GControlImpl.GSmallMapControl
 * @constructor
 * @extends {GControl}
 */
function GSmallMapControl() {}

/** @typedef {GSmallMapControl} */
google.maps.SmallMapControl;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GControlImpl.GLargeMapControl
 * @constructor
 * @extends {GControl}
 */
function GLargeMapControl() {}

/** @typedef {GLargeMapControl} */
google.maps.LargeMapControl;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GControlImpl.GSmallZoomControl
 * @constructor
 * @extends {GControl}
 */
function GSmallZoomControl() {}

/** @typedef {GSmallZoomControl} */
google.maps.SmallZoomControl;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GControlImpl.GLargeMapControl3D
 * @constructor
 * @extends {GControl}
 */
function GLargeMapControl3D() {}

/** @typedef {GLargeMapControl3D} */
google.maps.LargeMapControl3D;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GControlImpl.GSmallZoomControl3D
 * @constructor
 * @extends {GControl}
 */
function GSmallZoomControl3D() {}

/** @typedef {GSmallZoomControl3D} */
google.maps.SmallZoomControl3D;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GControlImpl.GScaleControl
 * @constructor
 * @extends {GControl}
 */
function GScaleControl() {}

/** @typedef {GScaleControl} */
google.maps.ScaleControl;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GControlImpl.GOverviewMapControl
 * @constructor
 * @extends {GControl}
 */
function GOverviewMapControl() {}

/** @typedef {GOverviewMapControl} */
google.maps.OverviewMapControl;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GControlPosition.GControlPosition
 * @constructor
 * @param {GControlAnchor} anchor
 * @param {GSize} offset
 */
function GControlPosition(anchor, offset) {}

/** @typedef {GControlPosition} */
google.maps.ControlPosition;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GHierarchicalMapTypeControl.GHierarchicalMapTypeControl
 * @constructor
 * @extends {GControl}
 */
function GHierarchicalMapTypeControl() {}

/** @typedef {GHierarchicalMapTypeControl} */
google.maps.HierarchicalMapTypeControl;

/**
 * @param {GMapType} parentType
 * @param {GMapType} childType
 * @param {string=} childText
 * @param {boolean=} isDefault
 */
GHierarchicalMapTypeControl.prototype.addRelationship =
    function(parentType, childType, childText, isDefault) {};

/** @param {GMapType} mapType */
GHierarchicalMapTypeControl.prototype.removeRelationship =
    function(mapType) {};

/** @return {undefined} */
GHierarchicalMapTypeControl.prototype.clearRelationships = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GMapType.GMapType
 * @constructor
 * @param {Array.<GTileLayer>} layers
 * @param {GProjection} projection
 * @param {string} name
 * @param {Object.<string, *>=} opts
 */
function GMapType(layers, projection, name, opts) {}

/** @typedef {GMapType} */
google.maps.MapType;

/**
 * @nosideeffects
 * @param {GLatLng} center
 * @param {GLatLng} span
 * @param {GSize} viewSize
 * @return {number}
 */
GMapType.prototype.getSpanZoomLevel = function(center, span, viewSize) {};

/**
 * @nosideeffects
 * @param {GLatLngBounds} bounds
 * @param {GSize} viewSize
 */
GMapType.prototype.getBoundsZoomLevel = function(bounds, viewSize) {};

/**
 * @nosideeffects
 * @param {boolean=} short_name
 * @return {string}
 */
GMapType.prototype.getName = function(short_name) {};

/**
 * @nosideeffects
 * @return {GProjection}
 */
GMapType.prototype.getProjection = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
GMapType.prototype.getTileSize = function() {};

/**
 * @nosideeffects
 * @return {Array.<GTileLayer>}
 */
GMapType.prototype.getTileLayers = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
GMapType.prototype.getMinimumResolution = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
GMapType.prototype.getMaximumResolution = function() {};

/**
 * @param {GLatLng} latlng
 * @param {function(Object.<string,*>)} callback
 * @param {number=} opt_targetZoom
 */
GMapType.prototype.getMaxZoomAtLatLng =
    function(latlng, callback, opt_targetZoom) {};

/**
 * @nosideeffects
 * @return {string}
 */
GMapType.prototype.getTextColor = function() {};

/**
 * @nosideeffects
 * @return {string}
 */
GMapType.prototype.getLinkColor = function() {};

/**
 * @nosideeffects
 * @return {string}
 */
GMapType.prototype.getErrorMessage = function() {};

/**
 * @nosideeffects
 * @param {GLatLngBounds} bounds
 * @param {number} zoom
 * @return {Array.<string>}
 */
GMapType.prototype.getCopyrights = function(bounds, zoom) {};

/**
 * @nosideeffects
 * @return {string}
 * @deprecated
 */
GMapType.prototype.getUrlArg = function() {};

/**
 * @nosideeffects
 * @return {string}
 */
GMapType.prototype.getAlt = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
GMapType.prototype.getHeading = function() {};

/**
 * @const
 * @type {GMapType}
 */
var G_NORMAL_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.NORMAL_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_SATELLITE_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.SATELLITE_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_AERIAL_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.AERIAL_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_HYBRID_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.HYBRID_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_AERIAL_HYBRID_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.AERIAL_HYBRID_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_PHYSICAL_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.PHYSICAL_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_MAPMAKER_NORMAL_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.MAPMAKER_NORMAL_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_MAPMAKER_HYBRID_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.MAPMAKER_HYBRID_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_MOON_ELEVATION_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.MOON_ELEVATION_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_MOON_VISIBLE_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.MOON_VISIBLE_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_MARS_ELEVATION_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.MARS_ELEVATION_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_MARS_VISIBLE_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.MARS_VISIBLE_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_MARS_INFRARED_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.MARS_INFRARED_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_SKY_VISIBLE_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.SKY_VISIBLE_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_SATELLITE_3D_MAP;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.SATELLITE_3D_MAP;

/**
 * @const
 * @type {GMapType}
 */
var G_DEFAULT_MAP_TYPES;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.DEFAULT_MAP_TYPES;

/**
 * @const
 * @type {GMapType}
 */
var G_MAPMAKER_MAP_TYPES;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.MAPMAKER_MAP_TYPES;

/**
 * @const
 * @type {GMapType}
 */
var G_MOON_MAP_TYPES;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.MOON_MAP_TYPES;

/**
 * @const
 * @type {GMapType}
 */
var G_MARS_MAP_TYPES;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.MARS_MAP_TYPES;

/**
 * @const
 * @type {GMapType}
 */
var G_SKY_MAP_TYPES;

/**
 * @const
 * @type {google.maps.MapType}
 */
google.maps.SKY_MAP_TYPES;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GMapTypeControl.GMapTypeControl
 * @constructor
 * @extends {GControl}
 * @param {boolean=} useShortNames
 */
function GMapTypeControl(useShortNames) {}

/** @typedef {GMapTypeControl} */
google.maps.MapTypeControl;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GMapUIOptions.GMapUIOptions
 * @constructor
 * @param {GSize=} opt_size
 */
function GMapUIOptions(opt_size) {}

/** @typedef {GMapUIOptions} */
google.maps.MapUIOptions;

/**
 * This type is not in the API reference. We use it purely to
 * help specify GMapUIOptions.
 * @constructor
 * @private
 */
GMapUIOptions.MapTypes_ = function() {};

/** @type {boolean} */
GMapUIOptions.MapTypes_.prototype.normal;

/** @type {boolean} */
GMapUIOptions.MapTypes_.prototype.satellite;

/** @type {boolean} */
GMapUIOptions.MapTypes_.prototype.hybrid;

/** @type {boolean} */
GMapUIOptions.MapTypes_.prototype.physical;

/**
 * This type is not in the API reference. We use it purely to
 * help specify GMapUIOptions.
 * @constructor
 * @private
 */
GMapUIOptions.Zoom_ = function() {};

/** @type {boolean} */
GMapUIOptions.Zoom_.prototype.scrollwheel;

/** @type {boolean} */
GMapUIOptions.Zoom_.prototype.doubleclick;

/**
 * This type is not in the API reference. We use it purely to
 * help specify GMapUIOptions.
 * @constructor
 * @private
 */
GMapUIOptions.Controls_ = function() {};

/** @type {boolean} */
GMapUIOptions.Controls_.prototype.largemapcontrol3d;

/** @type {boolean} */
GMapUIOptions.Controls_.prototype.smallzoomcontrol3d;

/** @type {boolean} */
GMapUIOptions.Controls_.prototype.maptypecontrol;

/** @type {boolean} */
GMapUIOptions.Controls_.prototype.menumaptypecontrol;

/** @type {boolean} */
GMapUIOptions.Controls_.prototype.scalecontrol;

/** @type {GMapUIOptions.MapTypes_} */
GMapUIOptions.prototype.maptypes;

/** @type {GMapUIOptions.Zoom_} */
GMapUIOptions.prototype.zoom;

/** @type {GMapUIOptions.Controls_} */
GMapUIOptions.prototype.controls;

/** @type {boolean} */
GMapUIOptions.prototype.keyboard;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GMenuMapTypeControl.GMenuMapTypeControl
 * @constructor
 * @extends {GControl}
 * @param {boolean=} useShortNames
 */
function GMenuMapTypeControl(useShortNames) {}

/** @typedef {GMenuMapTypeControl} */
google.maps.MenuMapTypeControl;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GNavLabelControl.GNavLabelControl
 * @constructor
 * @extends {GControl}
 */
function GNavLabelControl() {}

/** @typedef {GNavLabelControl} */
google.maps.NavLabelControl;

/** @param {number} level */
GNavLabelControl.prototype.setMinAddressLinkLevel = function(level) {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GCopyright.GCopyright
 * @constructor
 * @param {number} id
 * @param {GLatLngBounds} bounds
 * @param {number} minZoom
 * @param {string} text
 */
function GCopyright(id, bounds, minZoom, text) {}

/** @typedef {GCopyright} */
google.maps.Copyright;

/** @type {number} */
GCopyright.prototype.id;

/** @type {number} */
GCopyright.prototype.minZoom;

/** @type {GLatLngBounds} */
GCopyright.prototype.bounds;

/** @type {string} */
GCopyright.prototype.text;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GCopyrightCollection.GCopyrightCollection
 * @constructor
 * @param {string=} prefix
 */
function GCopyrightCollection(prefix) {}

/** @typedef {GCopyrightCollection} */
google.maps.CopyrightCollection;

/** @param {GCopyright} copyright */
GCopyrightCollection.prototype.addCopyright = function(copyright) {};

/**
 * @nosideeffects
 * @param {GLatLngBounds} bounds
 * @param {number} zoom
 * @return {Array.<string>}
 */
GCopyrightCollection.prototype.getCopyrights = function(bounds, zoom) {};

/**
 * @nosideeffects
 * @param {GLatLngBounds} bounds
 * @param {number} zoom
 * @return {string}
 */
GCopyrightCollection.prototype.getCopyrightNotice = function(bounds, zoom) {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GGroundOverlay.GGroundOverlay
 * @constructor
 * @extends {GOverlay}
 * @param {string} imageUrl
 * @param {GLatLngBounds} bounds
 */
function GGroundOverlay(imageUrl, bounds) {}

/** @typedef {GGroundOverlay} */
google.maps.GroundOverlay;

/** @return {undefined} */
GGroundOverlay.prototype.hide = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GGroundOverlay.prototype.isHidden = function() {};

/** @return {undefined} */
GGroundOverlay.prototype.show = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GGroundOverlay.prototype.supportsHide = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GIcon.GIcon
 * @constructor
 * @param {GIcon=} copy
 * @param {string=} image
 */
function GIcon(copy, image) {}

/** @typedef {GIcon} */
google.maps.Icon;

/** @type {string} */
GIcon.prototype.image;

/** @type {string} */
GIcon.prototype.shadow;

/** @type {GSize} */
GIcon.prototype.iconSize;

/** @type {GSize} */
GIcon.prototype.shadowSize;

/** @type {GPoint} */
GIcon.prototype.iconAnchor;

/** @type {GPoint} */
GIcon.prototype.infoWindowAnchor;

/** @type {string} */
GIcon.prototype.printImage;

/** @type {string} */
GIcon.prototype.mozPrintImage;

/** @type {string} */
GIcon.prototype.printShadow;

/** @type {string} */
GIcon.prototype.transparent;

/** @type {Array.<number>} */
GIcon.prototype.imageMap;

/** @type {number} */
GIcon.prototype.maxHeight;

/** @type {string} */
GIcon.prototype.dragCrossImage;

/** @type {GSize} */
GIcon.prototype.dragCrossSize;

/** @type {GPoint} */
GIcon.prototype.dragCrossAnchor;

/**
 * @const
 * @type {GIcon}
 */
var G_DEFAULT_ICON;

/**
 * @const
 * @type {google.maps.Icon}
 */
google.maps.DEFAULT_ICON;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GLayer.GLayer
 * @constructor
 * @param {string} layerId
 */
function GLayer(layerId) {}

/** @typedef {GLayer} */
google.maps.Layer;

/** @return {undefined} */
GLayer.prototype.hide = function() {};

/** @return {undefined} */
GLayer.prototype.show = function() {};

/**
 * @nosideeffects
 * @param {string} layerId
 */
GLayer.isHidden = function(layerId) {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GMarker.GMarker
 * @constructor
 * @extends {GOverlay}
 * @param {GLatLng} latlng
 * @param {Object.<string, *>=} opts
 */
function GMarker(latlng, opts) {}

/** @typedef {GMarker} */
google.maps.Marker;

/**
 * @param {Node} content
 * @param {Object.<string, *>=} opts
 */
GMarker.prototype.openInfoWindow = function(content, opts) {};

/**
 * @param {string} content
 * @param {Object.<string, *>=} opts
 */
GMarker.prototype.openInfoWindowHtml = function(content, opts) {};

/**
 * @param {Array.<GInfoWindowTab>} tabs
 * @param {Object.<string, *>=} opts
 */
GMarker.prototype.openInfoWindowTabs = function(tabs, opts) {};

/**
 * @param {Array.<GInfoWindowTab>} tabs
 * @param {Object.<string, *>=} opts
 */
GMarker.prototype.openInfoWindowTabsHtml = function(tabs, opts) {};

/**
 * @param {Node} content
 * @param {Object.<string, *>=} opts
 */
GMarker.prototype.bindInfoWindow = function(content, opts) {};

/**
 * @param {string} content
 * @param {Object.<string, *>=} opts
 */
GMarker.prototype.bindInfoWindowHtml = function(content, opts) {};

/**
 * @param {Array.<GInfoWindowTab>} tabs
 * @param {Object.<string, *>=} opts
 */
GMarker.prototype.bindInfoWindowTabs = function(tabs, opts) {};

/**
 * @param {Array.<GInfoWindowTab>} tabs
 * @param {Object.<string, *>=} opts
 */
GMarker.prototype.bindInfoWindowTabsHtml = function(tabs, opts) {};

/** @return {undefined} */
GMarker.prototype.closeInfoWindow = function() {};

/** @param {Object.<string, *>=} opts */
GMarker.prototype.showMapBlowup = function(opts) {};

/**
 * @nosideeffects
 * @return {GIcon}
 */
GMarker.prototype.getIcon = function() {};

/**
 * @nosideeffects
 * @return {string}
 */
GMarker.prototype.getTitle = function() {};

/**
 * @nosideeffects
 * @return {GLatLng}
 */
GMarker.prototype.getLatLng = function() {};

/** @param {GLatLng} latlng */
GMarker.prototype.setLatLng = function(latlng) {};

/** @return {undefined} */
GMarker.prototype.enableDragging = function() {};

/** @return {undefined} */
GMarker.prototype.disableDragging = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GMarker.prototype.draggable = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GMarker.prototype.draggingEnabled = function() {};

/** @param {string} url */
GMarker.prototype.setImage = function(url) {};

/** @return {undefined} */
GMarker.prototype.hide = function() {};

/** @return {undefined} */
GMarker.prototype.show = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GMarker.prototype.isHidden = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GMercatorProjection.GMercatorProjection
 * @constructor
 * @implements {GProjection}
 * @param {number} zoomlevels
 */
function GMercatorProjection(zoomlevels) {}

/** @typedef {GMercatorProjection} */
google.maps.MercatorProjection;

/**
 * @nosideeffects
 * @param {GLatLng} latlng
 * @param {number} zoom
 * @return {GPoint}
 */
GMercatorProjection.prototype.fromLatLngToPixel = function(latlng, zoom) {};

/**
 * @nosideeffects
 * @param {GPoint} pixel
 * @param {number} zoom
 * @param {boolean=} unbounded
 * @return {GLatLng}
 */
GMercatorProjection.prototype.fromPixelToLatLng =
    function(pixel, zoom, unbounded) {};

/**
 * @param {GPoint} tile
 * @param {number} zoom
 * @param {number} tilesize
 * @return {boolean}
 */
GMercatorProjection.prototype.tileCheckRange =
    function(tile, zoom, tilesize) {};

/**
 * @nosideeffects
 * @param {number} zoom
 */
GMercatorProjection.prototype.getWrapWidth = function(zoom) {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GObliqueMercator.GObliqueMercator
 * @constructor
 * @param {number} zoomlevels
 * @param {number} heading
 */
function GObliqueMercator(zoomlevels, heading) {}

/** @typedef {GObliqueMercator} */
google.maps.ObliqueMercator;

/**
 * @nosideeffects
 * @param {GLatLng} latlng
 * @param {number} zoom
 * @return {GPoint}
 */
GObliqueMercator.prototype.fromLatLngToPixel = function(latlng, zoom) {};

/**
 * @nosideeffects
 * @param {GPoint} pixel
 * @param {number} zoom
 * @param {boolean=} unbounded
 * @return {GLatLng}
 */
GObliqueMercator.prototype.fromPixelToLatLng =
    function(pixel, zoom, unbounded) {};

/**
 * @param {GPoint} tile
 * @param {number} zoom
 * @param {number} tilesize
 * @return {boolean}
 */
GObliqueMercator.prototype.tileCheckRange = function(tile, zoom, tilesize) {};

/**
 * @nosideeffects
 * @param {number} zoom
 */
GObliqueMercator.prototype.getWrapWidth = function(zoom) {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GOverlay.GOverlay
 * @constructor
 */
function GOverlay() {}

/** @typedef {GOverlay} */
google.maps.Overlay;

/**
 * @nosideeffects
 * @param {number} latitude
 * @return {number}
 */
GOverlay.getZIndex = function(latitude) {};

/** @param {GMap2} map */
GOverlay.prototype.initialize = function(map) {};

/** @return {undefined} */
GOverlay.prototype.remove = function() {};

/** @return {GOverlay} */
GOverlay.prototype.copy = function() {};

/** @param {boolean} force */
GOverlay.prototype.redraw = function(force) {};

/** @param {function(string)} callback */
GOverlay.prototype.getKml = function(callback) {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GPolygon.GPolygon
 * @constructor
 * @extends {GOverlay}
 * @param {Array.<GLatLng>} latlngs
 * @param {string=} strokeColor
 * @param {number=} strokeWeight
 * @param {number=} strokeOpacity
 * @param {number=} fillColor
 * @param {number=} fillOpacity
 * @param {Object.<string, *>=} opts
 */
function GPolygon(latlngs, strokeColor, strokeWeight, strokeOpacity, fillColor,
    fillOpacity, opts) {}

/** @typedef {GPolygon} */
google.maps.Polygon;

/** @param {number} index */
GPolygon.prototype.deleteVertex = function(index) {};

/** @return {undefined} */
GPolygon.prototype.disableEditing = function() {};

/** @param {Object.<string, *>=} opts */
GPolygon.prototype.enableDrawing = function(opts) {};

/** @param {Object.<string, *>=} opts */
GPolygon.prototype.enableEditing = function(opts) {};

/**
 * @nosideeffects
 * @return {number}
 */
GPolygon.prototype.getVertexCount = function() {};

/**
 * @nosideeffects
 * @param {number} index
 * @return {GLatLng}
 */
GPolygon.prototype.getVertex = function(index) {};

/**
 * @nosideeffects
 * @return {number}
 */
GPolygon.prototype.getArea = function() {};

/**
 * @nosideeffects
 * @return {GLatLngBounds}
 */
GPolygon.prototype.getBounds = function() {};

/** @return {undefined} */
GPolygon.prototype.hide = function() {};

/**
 * @param {number} index
 * @param {GLatLng} latlng
 */
GPolygon.prototype.insertVertex = function(index, latlng) {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GPolygon.prototype.isHidden = function() {};

/** @return {undefined} */
GPolygon.prototype.show = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GPolygon.prototype.supportsHide = function() {};

/** @param {Object.<string, *>} style */
GPolygon.prototype.setFillStyle = function(style) {};

/** @param {Object.<string, *>} style */
GPolygon.prototype.setStrokeStyle = function(style) {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GPolyline.GPolyline
 * @constructor
 * @extends {GOverlay}
 * @param {Array.<GLatLng>} latlngs
 * @param {string=} color
 * @param {number=} weight
 * @param {number=} opacity
 * @param {Object.<string, *>=} opts
 */
function GPolyline(latlngs, color, weight, opacity, opts) {}

/** @typedef {GPolyline} */
google.maps.Polyline;

/** @param {number} index */
GPolyline.prototype.deleteVertex = function(index) {};

/** @return {undefined} */
GPolyline.prototype.disableEditing = function() {};

/** @param {Object.<string, *>=} opts */
GPolyline.prototype.enableDrawing = function(opts) {};

/** @param {Object.<string, *>=} opts */
GPolyline.prototype.enableEditing = function(opts) {};

/**
 * @nosideeffects
 * @return {number}
 */
GPolyline.prototype.getVertexCount = function() {};

/**
 * @nosideeffects
 * @param {number} index
 * @return {GLatLng}
 */
GPolyline.prototype.getVertex = function(index) {};

/**
 * @nosideeffects
 * @return {number}
 */
GPolyline.prototype.getLength = function() {};

/**
 * @nosideeffects
 * @return {GLatLngBounds}
 */
GPolyline.prototype.getBounds = function() {};

/** @return {undefined} */
GPolyline.prototype.hide = function() {};

/**
 * @param {number} index
 * @param {GLatLng} latlng
 */
GPolyline.prototype.insertVertex = function(index, latlng) {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GPolyline.prototype.isHidden = function() {};

/** @return {undefined} */
GPolyline.prototype.show = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GPolyline.prototype.supportsHide = function() {};

/** @param {Object.<string, *>} style */
GPolyline.prototype.setStrokeStyle = function(style) {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GProjection
 * @interface
 */
function GProjection() {};

/** @typedef {GProjection} */
google.maps.Projection;

/**
 * @nosideeffects
 * @param {GLatLng} latlng
 * @param {number} zoom
 * @return {GPoint}
 */
GProjection.prototype.fromLatLngToPixel = function(latlng, zoom) {};

/**
 * @nosideeffects
 * @param {GPoint} pixel
 * @param {number} zoom
 * @param {boolean=} unbounded
 * @return {GLatLng}
 */
GProjection.prototype.fromPixelToLatLng = function(pixel, zoom, unbounded) {};

/**
 * @param {GPoint} tile
 * @param {number} zoom
 * @param {number} tilesize
 * @return {boolean}
 */
GProjection.prototype.tileCheckRange = function(tile, zoom, tilesize) {};

/**
 * @nosideeffects
 * @param {number} zoom
 * @return {number}
 */
GProjection.prototype.getWrapWidth = function(zoom) {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GScreenOverlay.GScreenOverlay
 * @constructor
 * @extends {GOverlay}
 * @param {string} imageUrl
 * @param {GScreenPoint} screenXY
 * @param {GScreenPoint} overlayXY
 * @param {GScreenSize} size
 */
function GScreenOverlay(imageUrl, screenXY, overlayXY, size) {}

/** @typedef {GScreenOverlay} */
google.maps.ScreenOverlay;

/** @return {undefined} */
GScreenOverlay.prototype.hide = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GScreenOverlay.prototype.isHidden = function() {};

/** @return {undefined} */
GScreenOverlay.prototype.show = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GScreenOverlay.prototype.supportsHide = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GScreenPoint.GScreenPoint
 * @constructor
 * @param {number} x
 * @param {number} y
 * @param {string=} xunits
 * @param {string=} yunits
 */
function GScreenPoint(x, y, xunits, yunits) {}

/** @typedef {GScreenPoint} */
google.maps.ScreenPoint;

/** @type {number} */
GScreenPoint.prototype.x;

/** @type {number} */
GScreenPoint.prototype.y;

/** @type {string} */
GScreenPoint.prototype.xunits;

/** @type {string} */
GScreenPoint.prototype.yunits;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GScreenSize.GScreenSize
 * @constructor
 * @param {number} width
 * @param {number} height
 * @param {string=} xunits
 * @param {string=} yunits
 */
function GScreenSize(width, height, xunits, yunits) {}

/** @typedef {GScreenSize} */
google.maps.ScreenSize;

/** @type {number} */
GScreenSize.prototype.width;

/** @type {number} */
GScreenSize.prototype.height;

/** @type {string} */
GScreenSize.prototype.xunits;

/** @type {string} */
GScreenSize.prototype.yunits;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GTileLayer.GTileLayer
 * @constructor
 * @param {GCopyrightCollection} copyrights
 * @param {number} minResolution
 * @param {number} maxResolution
 * @param {Object.<string, *>=} options
 */
function GTileLayer(copyrights, minResolution, maxResolution, options) {}

/** @typedef {GTileLayer} */
google.maps.TileLayer;

/**
 * @nosideeffects
 * @return {number}
 */
GTileLayer.prototype.minResolution = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
GTileLayer.prototype.maxResolution = function() {};

/**
 * @nosideeffects
 * @param {GPoint} tile
 * @param {number} zoom
 * @return {string}
 */
GTileLayer.prototype.getTileUrl = function(tile, zoom) {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GTileLayer.prototype.isPng = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
GTileLayer.prototype.getOpacity = function() {};

/**
 * @nosideeffects
 * @param {GLatLngBounds} bounds
 * @param {number} zoom
 * @return {string}
 */
GTileLayer.prototype.getCopyright = function(bounds, zoom) {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GTileLayerOverlay.GTileLayerOverlay
 * @constructor
 * @extends {GOverlay}
 * @param {GTileLayer} tileLayer
 * @param {Object.<string, *>=} opts
 */
function GTileLayerOverlay(tileLayer, opts) {}

/** @typedef {GTileLayerOverlay} */
google.maps.TileLayerOverlay;

/** @return {undefined} */
GTileLayerOverlay.prototype.hide = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GTileLayerOverlay.prototype.isHidden = function() {};

/** @return {undefined} */
GTileLayerOverlay.prototype.show = function() {};

/** @return {undefined} */
GTileLayerOverlay.prototype.refresh = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GTileLayerOverlay.prototype.supportsHide = function() {};

/**
 * @nosideeffects
 * @return {GTileLayer}
 */
GTileLayerOverlay.prototype.getTileLayer = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GAdsManager.GAdsManager
 * @constructor
 * @param {GMap2} map
 * @param {string} publisherId
 * @param {Object.<string, *>=} adsManagerOptions
 */
function GAdsManager(map, publisherId, adsManagerOptions) {}

/** @typedef {GAdsManager} */
google.maps.AdsManager;

/** @return {undefined} */
GAdsManager.prototype.enable = function() {};

/** @return {undefined} */
GAdsManager.prototype.disable = function() {};

/**
 * @constructor
 * @private
 */
function GAdsManagerStyle() {};

/** @typedef {GAdsManagerStyle} */
google.maps.AdsManagerStyle;

/**
 * @const
 * @type {GAdsManagerStyle}
 */
var G_ADSMANAGER_STYLE_ADUNIT;

/**
 * @const
 * @type {google.maps.AdsManagerStyle}
 */
google.maps.ADSMANAGER_STYLE_ADUNIT;

/**
 * @const
 * @type {GAdsManagerStyle}
 */
var G_ADSMANAGER_STYLE_ICON;

/**
 * @const
 * @type {google.maps.AdsManagerStyle}
 */
google.maps.ADSMANAGER_STYLE_ICON;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GClientGeocoder.GClientGeocoder
 * @constructor
 * @param {GGeocodeCache=} cache
 */
function GClientGeocoder(cache) {}

/** @typedef {GClientGeocoder} */
google.maps.ClientGeocoder;

/**
 * @param {string} address
 * @param {function(GLatLng)} callback
 */
GClientGeocoder.prototype.getLatLng = function(address, callback) {};

/**
 * @param {string|GLatLng} query
 * @param {function((string|GLatLng))} callback
 */
GClientGeocoder.prototype.getLocations = function(query, callback) {};

/**
 * @nosideeffects
 * @return {GGeocodeCache}
 */
GClientGeocoder.prototype.getCache = function() {};

/** @param {GGeocodeCache} cache */
GClientGeocoder.prototype.setCache = function(cache) {};

/** @param {GLatLngBounds} bounds */
GClientGeocoder.prototype.setViewport = function(bounds) {};

/**
 * @nosideeffects
 * @return {GLatLngBounds}
 */
GClientGeocoder.prototype.getViewport = function() {};

/** @param {string} countryCode */
GClientGeocoder.prototype.setBaseCountryCode = function(countryCode) {};

/**
 * @nosideeffects
 * @return {string}
 */
GClientGeocoder.prototype.getBaseCountryCode = function() {};

/** @return {undefined} */
GClientGeocoder.prototype.reset = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GDirections.GDirections
 * @constructor
 * @param {GMap2=} map
 * @param {Node=} panel
 */
function GDirections(map, panel) {}

/** @typedef {GDirections} */
google.maps.Directions;

/**
 * @param {string} query
 * @param {Object.<string, *>=} queryOpts
 */
GDirections.prototype.load = function(query, queryOpts) {};

/**
 * @param {Array} waypoints
 * @param {Object.<string, *>=} queryOpts
 */
GDirections.prototype.loadFromWaypoints = function(waypoints, queryOpts) {};

/** @return {undefined} */
GDirections.prototype.clear = function() {};

/**
 * @nosideeffects
 * @return {Object}
 */
GDirections.prototype.getStatus = function() {};

/**
 * @nosideeffects
 * @return {GLatLngBounds}
 */
GDirections.prototype.getBounds = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
GDirections.prototype.getNumRoutes = function() {};

/**
 * @nosideeffects
 * @param {number} i
 * @return {GRoute}
 */
GDirections.prototype.getRoute = function(i) {};

/**
 * @nosideeffects
 * @return {number}
 */
GDirections.prototype.getNumGeocodes = function() {};

/**
 * @nosideeffects
 * @param {number} i
 * @return {Object}
 */
GDirections.prototype.getGeocode = function(i) {};

/**
 * @nosideeffects
 * @return {string}
 */
GDirections.prototype.getCopyrightsHtml = function() {};

/**
 * @nosideeffects
 * @return {string}
 */
GDirections.prototype.getSummaryHtml = function() {};

/**
 * @nosideeffects
 * @return {Object}
 */
GDirections.prototype.getDistance = function() {};

/**
 * @nosideeffects
 * @return {Object}
 */
GDirections.prototype.getDuration = function() {};

/**
 * @nosideeffects
 * @return {GPolyline}
 */
GDirections.prototype.getPolyline = function() {};

/**
 * @nosideeffects
 * @param {number} i
 * @return {GMarker}
 */
GDirections.prototype.getMarker = function(i) {};

/**
 * @param {string} url
 * @param {function(string,number)} onload
 * @param {string=} postBody
 * @param {string=} postContentType
 */
function GDownloadUrl(url, onload, postBody, postContentType) {};

/**
 * @param {string} url
 * @param {function(string,number)} onload
 * @param {string=} postBody
 * @param {string=} postContentType
 */
google.maps.DownloadUrl = function(url, onload, postBody, postContentType) {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GFactualGeocodeCache.GFactualGeocodeCache
 * @constructor
 */
function GFactualGeocodeCache() {}

/** @typedef {GFactualGeocodeCache} */
google.maps.FactualGeocodeCache;

/**
 * @nosideeffects
 * @param {Object} reply
 * @return {boolean}
 */
GFactualGeocodeCache.prototype.isCachable = function(reply) {};

/**
 * @constructor
 * @private
 */
function GGeoStatusCode() {};

/** @typedef {GGeoStatusCode} */
google.maps.GeoStatusCode;

/**
 * @const
 * @type {GGeoStatusCode}
 */
var G_GEO_SUCCESS;

/**
 * @const
 * @type {google.maps.GeoStatusCode}
 */
google.maps.GEO_SUCCESS;

/**
 * @const
 * @type {GGeoStatusCode}
 */
var G_GEO_BAD_REQUEST;

/**
 * @const
 * @type {google.maps.GeoStatusCode}
 */
google.maps.GEO_BAD_REQUEST;

/**
 * @const
 * @type {GGeoStatusCode}
 */
var G_GEO_SERVER_ERROR;

/**
 * @const
 * @type {google.maps.GeoStatusCode}
 */
google.maps.GEO_SERVER_ERROR;

/**
 * @const
 * @type {GGeoStatusCode}
 */
var G_GEO_MISSING_QUERY;

/**
 * @const
 * @type {google.maps.GeoStatusCode}
 */
google.maps.GEO_MISSINQUERY;

/**
 * @const
 * @type {GGeoStatusCode}
 */
var G_GEO_MISSING_ADDRESS;

/**
 * @const
 * @type {google.maps.GeoStatusCode}
 */
google.maps.GEO_MISSINADDRESS;

/**
 * @const
 * @type {GGeoStatusCode}
 */
var G_GEO_UNKNOWN_ADDRESS;

/**
 * @const
 * @type {google.maps.GeoStatusCode}
 */
google.maps.GEO_UNKNOWN_ADDRESS;

/**
 * @const
 * @type {GGeoStatusCode}
 */
var G_GEO_UNAVAILABLE_ADDRESS;

/**
 * @const
 * @type {google.maps.GeoStatusCode}
 */
google.maps.GEO_UNAVAILABLE_ADDRESS;

/**
 * @const
 * @type {GGeoStatusCode}
 */
var G_GEO_UNKNOWN_DIRECTIONS;

/**
 * @const
 * @type {google.maps.GeoStatusCode}
 */
google.maps.GEO_UNKNOWN_DIRECTIONS;

/**
 * @const
 * @type {GGeoStatusCode}
 */
var G_GEO_BAD_KEY;

/**
 * @const
 * @type {google.maps.GeoStatusCode}
 */
google.maps.GEO_BAD_KEY;

/**
 * @const
 * @type {GGeoStatusCode}
 */
var G_GEO_TOO_MANY_QUERIES;

/**
 * @const
 * @type {google.maps.GeoStatusCode}
 */
google.maps.GEO_TOO_MANY_QUERIES;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GGeoXml.GGeoXml
 * @constructor
 * @extends {GOverlay}
 * @param {string} urlOfXml
 */
function GGeoXml(urlOfXml) {}

/** @typedef {GGeoXml} */
google.maps.GeoXml;

/**
 * @nosideeffects
 * @return {GTileLayerOverlay}
 * @deprecated
 */
GGeoXml.prototype.getTileLayerOverlay = function() {};

/**
 * @nosideeffects
 * @return {GLatLng}
 */
GGeoXml.prototype.getDefaultCenter = function() {};

/**
 * @nosideeffects
 * @return {GLatLng}
 */
GGeoXml.prototype.getDefaultSpan = function() {};

/**
 * @nosideeffects
 * @return {GLatLngBounds}
 */
GGeoXml.prototype.getDefaultBounds = function() {};

/** @param {GMap2} map */
GGeoXml.prototype.gotoDefaultViewport = function(map) {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GGeoXml.prototype.hasLoaded = function() {};

/** @return {undefined} */
GGeoXml.prototype.hide = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GGeoXml.prototype.isHidden = function() {};

/**
 * @return {boolean}
 * @deprecated
 */
GGeoXml.prototype.loadedCorrectly = function() {};

/** @return {undefined} */
GGeoXml.prototype.show = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GGeoXml.prototype.supportsHide = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GGeocodeCache.GGeocodeCache
 * @constructor
 */
function GGeocodeCache() {}

/** @typedef {GGeocodeCache} */
google.maps.GeocodeCache;

/**
 * @nosideeffects
 * @param {string} address
 * @return {Object}
 */
GGeocodeCache.prototype.get = function(address) {};

/**
 * @nosideeffects
 * @param {Object} reply
 * @return {boolean}
 */
GGeocodeCache.prototype.isCachable = function(reply) {};

/**
 * @param {string} address
 * @param {Object} reply
 */
GGeocodeCache.prototype.put = function(address, reply) {};

/** @return {undefined} */
GGeocodeCache.prototype.reset = function() {};

/**
 * @nosideeffects
 * @param {string} address
 * @return {string}
 */
GGeocodeCache.prototype.toCanonical = function(address) {};

/**
 * @constructor
 * @private
 */
function GGoogleBar() {};

/**
 * @constructor
 * @private
 */
function GGoogleBarLinkTarget() {};

/** @typedef {GGoogleBarLinkTarget} */
google.maps.GoogleBarLinkTarget;

/**
 * @const
 * @type {GGoogleBarLinkTarget}
 */
var G_GOOGLEBAR_LINK_TARGET_BLANK;

/**
 * @const
 * @type {google.maps.GoogleBarLinkTarget}
 */
google.maps.GOOGLEBAR_LINK_TARGET_BLANK;

/**
 * @const
 * @type {GGoogleBarLinkTarget}
 */
var G_GOOGLEBAR_LINK_TARGET_PARENT;

/**
 * @const
 * @type {google.maps.GoogleBarLinkTarget}
 */
google.maps.GOOGLEBAR_LINK_TARGET_PARENT;

/**
 * @const
 * @type {GGoogleBarLinkTarget}
 */
var G_GOOGLEBAR_LINK_TARGET_SELF;

/**
 * @const
 * @type {google.maps.GoogleBarLinkTarget}
 */
google.maps.GOOGLEBAR_LINK_TARGET_SELF;

/**
 * @const
 * @type {GGoogleBarLinkTarget}
 */
var G_GOOGLEBAR_LINK_TARGET_TOP;

/**
 * @const
 * @type {google.maps.GoogleBarLinkTarget}
 */
google.maps.GOOGLEBAR_LINK_TARGET_TOP;

/**
 * @constructor
 * @private
 */
function GGoogleBarListingTypes() {};

/** @typedef {GGoogleBarListingTypes} */
google.maps.GoogleBarListingTypes;

/**
 * @const
 * @type {GGoogleBarListingTypes}
 */
var G_GOOGLEBAR_TYPE_BLENDED_RESULTS;

/**
 * @const
 * @type {google.maps.GoogleBarListingTypes}
 */
google.maps.GOOGLEBAR_TYPE_BLENDED_RESULTS;

/**
 * @const
 * @type {GGoogleBarListingTypes}
 */
var G_GOOGLEBAR_TYPE_KMLONLY_RESULTS;

/**
 * @const
 * @type {google.maps.GoogleBarListingTypes}
 */
google.maps.GOOGLEBAR_TYPE_KMLONLY_RESULTS;

/**
 * @const
 * @type {GGoogleBarListingTypes}
 */
var G_GOOGLEBAR_TYPE_LOCALONLY_RESULTS;

/**
 * @const
 * @type {google.maps.GoogleBarListingTypes}
 */
google.maps.GOOGLEBAR_TYPE_LOCALONLY_RESULTS;

/**
 * @constructor
 * @private
 */
function GGoogleBarResultList() {};

/** @typedef {GGoogleBarResultList} */
google.maps.GoogleBarResultList;

/**
 * @const
 * @type {GGoogleBarResultList}
 */
var G_GOOGLEBAR_RESULT_LIST_INLINE;

/**
 * @const
 * @type {google.maps.GoogleBarResultList}
 */
google.maps.GOOGLEBAR_RESULT_LIST_INLINE;

/**
 * @const
 * @type {GGoogleBarResultList}
 */
var G_GOOGLEBAR_RESULT_LIST_SUPPRESS;

/**
 * @const
 * @type {google.maps.GoogleBarResultList}
 */
google.maps.GOOGLEBAR_RESULT_LIST_SUPPRESS;

/**
 * @constructor
 * @private
 */
function GRoute() {};

/**
 * @nosideeffects
 * @return {number}
 */
GRoute.prototype.getNumSteps = function() {};

/**
 * @nosideeffects
 * @param {number} i
 * @return {GStep}
 */
GRoute.prototype.getStep = function(i) {};

/**
 * @nosideeffects
 * @return {Object}
 */
GRoute.prototype.getStartGeocode = function() {};

/**
 * @nosideeffects
 * @return {Object}
 */
GRoute.prototype.getEndGeocode = function() {};

/**
 * @nosideeffects
 * @return {GLatLng}
 */
GRoute.prototype.getEndLatLng = function() {};

/**
 * @nosideeffects
 * @return {string}
 */
GRoute.prototype.getSummaryHtml = function() {};

/**
 * @nosideeffects
 * @return {Object}
 */
GRoute.prototype.getDistance = function() {};

/**
 * @nosideeffects
 * @return {Object}
 */
GRoute.prototype.getDuration = function() {};

/**
 * @constructor
 * @private
 */
function GStep() {};

/**
 * @nosideeffects
 * @return {GLatLng}
 */
GStep.prototype.getLatLng = function() {};

/**
 * @nosideeffects
 * @return {number}
 */
GStep.prototype.getPolylineIndex = function() {};

/**
 * @nosideeffects
 * @return {string}
 */
GStep.prototype.getDescriptionHtml = function() {};

/**
 * @nosideeffects
 * @return {Object}
 */
GStep.prototype.getDistance = function() {};

/**
 * @nosideeffects
 * @return {Object}
 */
GStep.prototype.getDuration = function() {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GStreetviewClient.GStreetviewClient
 * @constructor
 */
function GStreetviewClient() {}

/** @typedef {GStreetviewClient} */
google.maps.StreetviewClient;

/**
 * @param {GLatLng} latlng
 * @param {function(GLatLng)} callback
 */
GStreetviewClient.prototype.getNearestPanoramaLatLng =
    function(latlng, callback) {};

/**
 * @param {GLatLng} latlng
 * @param {function(GStreetviewData)} callback
 */
GStreetviewClient.prototype.getNearestPanorama = function(latlng, callback) {};

/**
 * @param {string} panoId
 * @param {function(GStreetviewData)} callback
 */
GStreetviewClient.prototype.getPanoramaById = function(panoId, callback) {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GStreetviewClient.ReturnValues
 * @enum
 */
GStreetviewClient.ReturnValues = {
    SUCCESS: 200,
    SERVER_ERROR: 500,
    NO_NEARBY_PANO: 600
};

/**
 * @constructor
 * @private
 */
function GStreetviewData() {};

/** @type {Object.<string, *>} */
GStreetviewData.prototype.location;

/** @type {string} */
GStreetviewData.prototype.copyright;

/** @type {Array.<GStreetviewLink>} */
GStreetviewData.prototype.links;

/** @type {GStreetviewClient.ReturnValues} */
GStreetviewData.prototype.code;

/**
 * @constructor
 * @private
 */
function GStreetviewLink() {};

/** @type {number} */
GStreetviewLink.prototype.yaw;

/** @type {string} */
GStreetviewLink.prototype.description;

/** @type {string} */
GStreetviewLink.prototype.panoId;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GStreetviewOverlay.GStreetviewOverlay
 * @constructor
 * @extends {GOverlay}
 */
function GStreetviewOverlay() {}

/** @typedef {GStreetviewOverlay} */
google.maps.StreetviewOverlay;

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GStreetviewPanorama.GStreetviewPanorama
 * @constructor
 * @param {Node} container
 * @param {Object.<string, *>=} opts
 */
function GStreetviewPanorama(container, opts) {}

/** @typedef {GStreetviewPanorama} */
google.maps.StreetviewPanorama;

/** @return {undefined} */
GStreetviewPanorama.prototype.remove = function() {};

/** @param {Node} container */
GStreetviewPanorama.prototype.setContainer = function(container) {};

/** @return {undefined} */
GStreetviewPanorama.prototype.checkResize = function() {};

/** @return {undefined} */
GStreetviewPanorama.prototype.hide = function() {};

/** @return {undefined} */
GStreetviewPanorama.prototype.show = function() {};

/**
 * @nosideeffects
 * @return {boolean}
 */
GStreetviewPanorama.prototype.isHidden = function() {};

/**
 * @nosideeffects
 * @return {GLatLng}
 */
GStreetviewPanorama.prototype.getLatLng = function() {};

/**
 * @nosideeffects
 * @return {string}
 */
GStreetviewPanorama.prototype.getPanoId = function() {};

/**
 * @nosideeffects
 * @return {Object.<string, *>}
 */
GStreetviewPanorama.prototype.getPOV = function() {};

/** @param {Object.<string, *>} pov */
GStreetviewPanorama.prototype.setPOV = function(pov) {};

/**
 * @param {Object.<string, *>} pov
 * @param {boolean=} opt_longRoute
 */
GStreetviewPanorama.prototype.panTo = function(pov, opt_longRoute) {};

/**
 * @param {GLatLng} latlng
 * @param {Object.<string, *>=} opt_pov
 */
GStreetviewPanorama.prototype.setLocationAndPOV = function(latlng, opt_pov) {};

/** @param {Object.<string, *>} photoSpec */
GStreetviewPanorama.prototype.setUserPhoto = function(photoSpec) {};

/** @param {number} yaw */
GStreetviewPanorama.prototype.followLink = function(yaw) {};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GStreetviewPanorama.ErrorValues
 * @enum
 */
GStreetviewPanorama.ErrorValues = {
    NO_NEARBY_PANO: 600,
    NO_PHOTO: 601,
    FLASH_UNAVAILABLE: 603
};

/**
 * @see http://code.google.com/apis/maps/documentation/javascript/v2/reference.html#GTrafficOverlay.GTrafficOverlay
 * @constructor
 * @extends {GOverlay}
 * @param {Object.<string, *>=} opts
 */
function GTrafficOverlay(opts) {}

/** @typedef {GTrafficOverlay} */
google.maps.TrafficOverlay;

/** @return {undefined} */
GTrafficOverlay.prototype.hide = function() {};

/** @return {undefined} */
GTrafficOverlay.prototype.show = function() {};

/**
 * @constructor
 * @private
 */
function GTravelModes() {};

/** @typedef {GTravelModes} */
google.maps.TravelModes;

/**
 * @const
 * @type {GTravelModes}
 */
var G_TRAVEL_MODE_WALKING;

/**
 * @const
 * @type {google.maps.TravelModes}
 */
google.maps.TRAVEL_MODE_WALKING;

/**
 * @const
 * @type {GTravelModes}
 */
var G_TRAVEL_MODE_DRIVING;

/**
 * @const
 * @type {google.maps.TravelModes}
 */
google.maps.TRAVEL_MODE_DRIVING;

var GXml = {};

google.maps.Xml = {};

/**
 * @param {string} xmltext
 * @return {Node}
 */
GXml.parse = function(xmltext) {};

/**
 * @param {Node} xmlnode
 * @return {string}
 */
GXml.value = function(xmlnode) {};

var GXmlHttp = {};

google.maps.XmlHttp = {};

/** @return {XMLHttpRequest} */
GXmlHttp.create = function() {};

/**
 * @constructor
 * @private
 */
function GXslt() {};

/**
 * @param {Node} xsltnode
 * @return {GXslt}
 */
GXslt.create = function(xsltnode) {};

/**
 * @param {Node} xmlnode
 * @param {Node} htmlnode
 * @return {boolean}
 */
GXslt.transformToHtml = function(xmlnode, htmlnode) {};
