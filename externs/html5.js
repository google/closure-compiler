/*
 * Copyright 2008 Google Inc.
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
 * @fileoverview Definitions for all the extensions over the
 *  W3C's DOM3 specification in HTML5. This file depends on
 *  w3c_dom3.js. The whole file has been fully type annotated.
 *  Created from
 *  http://www.whatwg.org/specs/web-apps/current-work/multipage/index.html
 *
 *  This relies on w3c_event.js being included first.
 *
 * @externs
*
 */

/**
 * @constructor
 * @extends {HTMLElement}
 */
function HTMLCanvasElement() {}

/**
 * @param {string=} opt_type
 * @return {string}
 */
HTMLCanvasElement.prototype.toDataURL = function(opt_type) {};

/**
 * @param {string} contextId
 * @return {Object}
 */
HTMLCanvasElement.prototype.getContext = function(contextId) {};

/**
 * @constructor
 */
function CanvasRenderingContext2D() {}

/** @type {HTMLCanvasElement} */
CanvasRenderingContext2D.prototype.canvas;

/**
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.save = function() {};

/**
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.restore = function() {};

/**
 * @param {number} x
 * @param {number} y
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.scale = function(x, y) {};

/**
 * @param {number} angle
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.rotate = function(angle) {};

/**
 * @param {number} x
 * @param {number} y
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.translate = function(x, y) {};

/**
 * @param {number} m11
 * @param {number} m12
 * @param {number} m21
 * @param {number} m22
 * @param {number} dx
 * @param {number} dy
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.transform = function(
    m11, m12, m21, m22, dx, dy) {};

/**
 * @param {number} m11
 * @param {number} m12
 * @param {number} m21
 * @param {number} m22
 * @param {number} dx
 * @param {number} dy
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.setTransform = function(
    m11, m12, m21, m22, dx, dy) {};

/**
 * @param {number} x0
 * @param {number} y0
 * @param {number} x1
 * @param {number} y1
 * @return {CanvasGradient}
 */
CanvasRenderingContext2D.prototype.createLinearGradient = function(
    x0, y0, x1, y1) {};

/**
 * @param {number} x0
 * @param {number} y0
 * @param {number} r0
 * @param {number} x1
 * @param {number} y1
 * @param {number} r1
 * @return {CanvasGradient}
 */
CanvasRenderingContext2D.prototype.createRadialGradient = function(
    x0, y0, r0, x1, y1, r1) {};

/**
 * @param {HTMLImageElement|HTMLCanvasElement} image
 * @param {string} repetition
 * @return {CanvasPattern}
 */
CanvasRenderingContext2D.prototype.createPattern = function(
    image, repetition) {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} w
 * @param {number} h
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.clearRect = function(x, y, w, h) {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} w
 * @param {number} h
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.fillRect = function(x, y, w, h) {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} w
 * @param {number} h
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.strokeRect = function(x, y, w, h) {};

/**
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.beginPath = function() {};

/**
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.closePath = function() {};

/**
 * @param {number} x
 * @param {number} y
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.moveTo = function(x, y) {};

/**
 * @param {number} x
 * @param {number} y
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.lineTo = function(x, y) {};

/**
 * @param {number} cpx
 * @param {number} cpy
 * @param {number} x
 * @param {number} y
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.quadraticCurveTo = function(
    cpx, cpy, x, y) {};

/**
 * @param {number} cp1x
 * @param {number} cp1y
 * @param {number} cp2x
 * @param {number} cp2y
 * @param {number} x
 * @param {number} y
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.bezierCurveTo = function(
    cp1x, cp1y, cp2x, cp2y, x, y) {};

/**
 * @param {number} x1
 * @param {number} y1
 * @param {number} x2
 * @param {number} y2
 * @param {number} radius
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.arcTo = function(x1, y1, x2, y2, radius) {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} w
 * @param {number} h
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.rect = function(x, y, w, h) {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} radius
 * @param {number} startAngle
 * @param {number} endAngle
 * @param {boolean} anticlockwise
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.arc = function(
    x, y, radius, startAngle, endAngle, anticlockwise) {};

/**
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.fill = function() {};

/**
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.stroke = function() {};

/**
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.clip = function() {};

/**
 * @param {number} x
 * @param {number} y
 * @return {boolean}
 */
CanvasRenderingContext2D.prototype.isPointInPath = function(x, y) {};

/**
 * @param {string} text
 * @param {number} x
 * @param {number} y
 * @param {number=} opt_maxWidth
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.fillText = function(
    text, x, y, opt_maxWidth) {};

/**
 * @param {string} text
 * @param {number} x
 * @param {number} y
 * @param {number=} opt_maxWidth
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.strokeText = function(
    text, x, y, opt_maxWidth) {};

/**
 * @param {string} text
 * @return {TextMetrics}
 */
CanvasRenderingContext2D.prototype.measureText = function(text) {};

/**
 * @param {HTMLImageElement|HTMLCanvasElement} image
 * @param {number} dx Destination x coordinate.
 * @param {number} dy Destination y coordinate.
 * @param {number=} opt_dw Destination box width.  Defaults to the image width.
 * @param {number=} opt_dh Destination box height.  Defaults to the image height.
 * @param {number=} opt_sx Source box x coordinate.  Used to select a portion of
 *     the source image to draw.  Defaults to 0.
 * @param {number=} opt_sy Source box y coordinate.  Used to select a portion of
 *     the source image to draw.  Defaults to 0.
 * @param {number=} opt_sw Source box width.  Used to select a portion of
 *     the source image to draw.  Defaults to the full image width.
 * @param {number=} opt_sh Source box height.  Used to select a portion of
 *     the source image to draw.  Defaults to the full image height.
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.drawImage = function(
    image, dx, dy, opt_dw, opt_dh, opt_sx, opt_sy, opt_sw, opt_sh) {};

/**
 * @param {number} sw
 * @param {number} sh
 * @return {ImageData}
 */
CanvasRenderingContext2D.prototype.createImageData = function(sw, sh) {};

/**
 * @param {number} sx
 * @param {number} sy
 * @param {number} sw
 * @param {number} sh
 * @return {ImageData}
 */
CanvasRenderingContext2D.prototype.getImageData = function(sx, sy, sw, sh) {};

/**
 * @param {ImageData} imagedata
 * @param {number} dx
 * @param {number} dy
 * @param {number=} opt_dirtyX
 * @param {number=} opt_dirtyY
 * @param {number=} opt_dirtyWidth
 * @param {number=} opt_dirtyHeight
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.putImageData = function(imagedata, dx, dy,
    opt_dirtyX, opt_dirtyY, opt_dirtyWidth, opt_dirtyHeight) {};

/** @type {string} */
CanvasRenderingContext2D.prototype.fillColor;

/**
 * @type {string}
 * @implicitCast
 */
CanvasRenderingContext2D.prototype.fillStyle;

/** @type {string} */
CanvasRenderingContext2D.prototype.font;

/** @type {number} */
CanvasRenderingContext2D.prototype.globalAlpha;

/** @type {string} */
CanvasRenderingContext2D.prototype.globalCompositeOperation;

/** @type {number} */
CanvasRenderingContext2D.prototype.lineWidth;

/** @type {string} */
CanvasRenderingContext2D.prototype.lineCap;

/** @type {string} */
CanvasRenderingContext2D.prototype.lineJoin;

/** @type {number} */
CanvasRenderingContext2D.prototype.miterLimit;

/** @type {number} */
CanvasRenderingContext2D.prototype.shadowBlur;

/** @type {string} */
CanvasRenderingContext2D.prototype.shadowColor;

/** @type {number} */
CanvasRenderingContext2D.prototype.shadowOffsetX;

/** @type {number} */
CanvasRenderingContext2D.prototype.shadowOffsetY;

/**
 * @type {string}
 * @implicitCast
 */
CanvasRenderingContext2D.prototype.strokeStyle;

/** @type {string} */
CanvasRenderingContext2D.prototype.strokeColor;

/** @type {string} */
CanvasRenderingContext2D.prototype.textAlign;

/** @type {string} */
CanvasRenderingContext2D.prototype.textBaseline;

/**
 * @constructor
 */
function CanvasGradient() {}

/**
 * @param {number} offset
 * @param {string} color
 * @return {undefined}
 */
CanvasGradient.prototype.addColorStop = function(offset, color) {};

/**
 * @constructor
 */
function CanvasPattern() {}

/**
 * @constructor
 */
function TextMetrics() {}

/** @type {number} */
TextMetrics.prototype.width;

/**
 * @constructor
 */
function ImageData() {}

/** @type {CanvasPixelArray} */
ImageData.prototype.data;

/**
 * @constructor
 */
function CanvasPixelArray() {}

/**
 * @constructor
 */
function ClientInformation() {}

/** @type {boolean} */
ClientInformation.prototype.onLine;

/**
 * @param {string} protocol
 * @param {string} uri
 * @param {string} title
 * @return {undefined}
 */
ClientInformation.prototype.registerProtocolHandler = function(
    protocol, uri, title) {};

/**
 * @param {string} mimeType
 * @param {string} uri
 * @param {string} title
 * @return {undefined}
 */
ClientInformation.prototype.registerContentHandler = function(
    mimeType, uri, title) {};

// HTML5 Database objects
/**
 * @constructor
 */
function Database() {}

/**
 * @type {string}
 */
Database.prototype.version;

/**
 * @param {function(SQLTransaction) : void} callback
 * @param {(function(SQLError) : void)=} opt_errorCallback
 * @param {Function=} opt_Callback
 */
Database.prototype.transaction = function(
    callback, opt_errorCallback, opt_Callback) {};

/**
 * @param {function(SQLTransaction) : void} callback
 * @param {(function(SQLError) : void)=} opt_errorCallback
 * @param {Function=} opt_Callback
 */
Database.prototype.readTransaction = function(
    callback, opt_errorCallback, opt_Callback) {};

/**
 * @param {string} oldVersion
 * @param {string} newVersion
 * @param {function(SQLTransaction) : void} callback
 * @param {function(SQLError) : void} errorCallback
 * @param {Function} successCallback
 */
Database.prototype.changeVersion = function(
    oldVersion, newVersion, callback, errorCallback, successCallback) {};

/**
 * @constructor
 */
function SQLError() {}

/**
 * @type {number}
 */
SQLError.prototype.code;

/**
 * @type {string}
 */
SQLError.prototype.message;

/**
 * @constructor
 */
function SQLTransaction() {}

/**
 * @param {string} sqlStatement
 * @param {Array.<*>=} opt_queryArgs
 * @param {(function(SQLTransaction, SQLResultSet) : void)=} opt_callback
 * @param {(function(SQLTransaction, SQLError) : void)=} opt_errorCallback
 */
SQLTransaction.prototype.executeSql = function(
    sqlStatement, opt_queryArgs, opt_callback, opt_errorCallback) {};

/**
 * @constructor
 */
function SQLResultSet() {}

/**
 * @type {number}
 */
SQLResultSet.prototype.insertId;

/**
 * @type {number}
 */
SQLResultSet.prototype.rowsAffected;

/**
 * @type {SQLResultSetRowList}
 */
SQLResultSet.prototype.rows;

/**
 * @constructor
 */
function SQLResultSetRowList() {}

/**
 * @type {number}
 */
SQLResultSetRowList.prototype.length;

/**
 * @param {number} index
 * @return {Object}
 */
SQLResultSetRowList.prototype.item = function(index) {};

/**
 * @param {string} name
 * @param {string} version
 * @param {string} description
 * @param {number} size
 */
function openDatabase(name, version, description, size) {}

/**
 * @param {string} name
 * @param {string} version
 * @param {string} description
 * @param {number} size
 */
Window.prototype.openDatabase = function(name, version, description, size) {};

/**
 * @type {boolean}
 */
HTMLImageElement.prototype.complete;

/**
 * The postMessage method (as defined by HTML5 spec and implemented in FF3).
 * @param {*} message
 * @param {string} targetOrigin
 */
Window.prototype.postMessage = function(message, targetOrigin) {};

/**
 * The postMessage method (as implemented in Opera).
 * @param {string} message
 */
Document.prototype.postMessage = function(message) {};

/**
 * @see https://developer.apple.com/webapps/docs/documentation/AppleApplications/Reference/SafariJSRef/DOMApplicationCache/DOMApplicationCache.html
 * @constructor
 * @extends {EventTarget}
 */
function DOMApplicationCache() {}

/**
 * The object isn't associated with an application cache. This can occur if the
 * update process fails and there is no previous cache to revert to, or if there
 * is no manifest file.
 * @type {number}
 */
DOMApplicationCache.prototype.UNCACHED = 0;

/**
 * The cache is idle.
 * @type {number}
 */
DOMApplicationCache.prototype.IDLE = 1;

/**
 * The update has started but the resources are not downloaded yet - for
 * example, this can happen when the manifest file is fetched.
 * @type {number}
 */
DOMApplicationCache.prototype.CHECKING = 2;

/**
 * The resources are being downloaded into the cache.
 * @type {number}
 */
DOMApplicationCache.prototype.DOWNLOADING = 3;

/**
 * Resources have finished downloading and the new cache is ready to be used.
 * @type {number}
 */
DOMApplicationCache.prototype.UPDATEREADY = 4;

/**
 * The current status of the application cache.
 * @type {number}
 */
DOMApplicationCache.prototype.status;

/**
 * Sent when the update process finishes for the first time; that is, the first
 * time an application cache is saved.
 * @type {EventListener}
 */
DOMApplicationCache.prototype.oncached;

/**
 * Sent when the cache update process begins.
 * @type {EventListener}
 */
DOMApplicationCache.prototype.onchecking;

/**
 * Sent when the update process begins downloading resources in the manifest
 * file.
 * @type {EventListener}
 */
DOMApplicationCache.prototype.ondownloading;

/**
 * Sent when an error occurs.
 * @type {EventListener}
 */
DOMApplicationCache.prototype.onerror;

/**
 * Sent when the update process finishes but the manifest file does not change.
 * @type {EventListener}
 */
DOMApplicationCache.prototype.onnoupdate;

/**
 * Sent when each resource in the manifest file begins to download.
 * @type {EventListener}
 */
DOMApplicationCache.prototype.onprogress;

/**
 * Sent when there is an existing application cache, the update process
 * finishes, and there is a new application cache ready for use.
 * @type {EventListener}
 */
DOMApplicationCache.prototype.onupdateready;

/**
 * Replaces the active cache with the latest version.
 * @throws {DOMException}
 */
DOMApplicationCache.prototype.swapCache = function() {};

/**
 * Manually triggers the update process.
 * @throws {DOMException}
 */
DOMApplicationCache.prototype.update = function() {};

/** @type {DOMApplicationCache} */
var applicationCache;

/** @type {DOMApplicationCache} */
Window.prototype.applicationCache;

/**
 * Addition for accessing clipboard file data that are part of the proposed
 * HTML5 spec.
 * @type {ClipboardData}
 */
MouseEvent.prototype.dataTransfer;

// HTML5 MessageChannel
/**
 * @see http://dev.w3.org/html5/spec/comms.html#messagechannel
 * @constructor
 */
function MessageChannel() {}

/**
 * Returns the first port.
 * @type {MessagePort}
 */
MessageChannel.prototype.port1;

/**
 * Returns the second port.
 * @type {MessagePort}
 */
MessageChannel.prototype.port2;

// HTML5 MessagePort
/**
 * @see http://dev.w3.org/html5/spec/comms.html#messageport
 * @constructor
 * @extends {EventTarget}
 */
function MessagePort() {}

/**
 * Posts a message through the channel, optionally with the given ports.
 * @param {*} message
 * @param {Array.<MessagePort>=} opt_ports
 */
MessagePort.prototype.postMessage = function(message, opt_ports) {};

/**
 * Begins dispatching messages received on the port.
 */
MessagePort.prototype.start = function() {};

/**
 * Disconnects the port, so that it is no longer active.
 */
MessagePort.prototype.close = function() {};

/**
 * @type {?function(MessageEvent)}
 */
MessagePort.prototype.onmessage;

// HTML5 MessageEvent class
/**
 * @see http://dev.w3.org/html5/spec/comms.html#messageevent
 * @constructor
 * @extends {Event}
 */
function MessageEvent() {}

/**
 * Returns the data of the message.
 * @type {*}
 */
MessageEvent.prototype.data;

/**
 * Returns the origin of the message, for server-sent events and cross-document
 * messaging.
 * @type {string}
 */
MessageEvent.prototype.origin;

/**
 * Returns the last event ID, for server-sent events.
 * @type {string}
 */
MessageEvent.prototype.lastEventId;

/**
 * Returns the last event ID, for server-sent events.
 * @type {Window}
 */
MessageEvent.prototype.source;

/**
 * Returns the Array of MessagePorts sent with the message, for cross-document
 * messaging and channel messaging.
 * @type {Array.<MessagePort>}
 */
MessageEvent.prototype.ports;

/**
 * Initializes the event in a manner analogous to the similarly-named methods in
 * the DOM Events interfaces.
 * @param {string} typeArg
 * @param {boolean} canBubbleArg
 * @param {boolean} cancelableArg
 * @param {*} dataArg
 * @param {string} originArg
 * @param {string} lastEventIdArg
 * @param {Window} sourceArg
 * @param {Array.<MessagePort>} portsArg
 * @override
 */
MessageEvent.prototype.initMessageEvent = function(typeArg, canBubbleArg,
    cancelableArg, dataArg, originArg, lastEventIdArg, sourceArg, portsArg) {};

/**
 * Initializes the event in a manner analogous to the similarly-named methods in
 * the DOM Events interfaces.
 * @param {string} namespaceURI
 * @param {string} typeArg
 * @param {boolean} canBubbleArg
 * @param {boolean} cancelableArg
 * @param {*} dataArg
 * @param {string} originArg
 * @param {string} lastEventIdArg
 * @param {Window} sourceArg
 * @param {Array.<MessagePort>} portsArg
 */
MessageEvent.prototype.initMessageEventNS = function(namespaceURI, typeArg,
    canBubbleArg, cancelableArg, dataArg, originArg, lastEventIdArg, sourceArg,
    portsArg) {};

// HTML5 Web Socket class
/**
 * @see http://dev.w3.org/html5/websockets/
 * @constructor
 * @param {string} url
 * @param {string=} opt_protocol
 * @extends {EventTarget}
 */
function WebSocket(url, opt_protocol) {}

/**
 * Returns the URL value that was passed to the constructor.
 * @type {string}
 */
WebSocket.prototype.URL;

/**
 * The connection has not yet been established.
 * @type {number}
 */
WebSocket.prototype.CONNECTING = 0;

/**
 * The Web Socket connection is established and communication is possible.
 * @type {number}
 */
WebSocket.prototype.OPEN = 1;

/**
 * The connection has been closed or could not be opened.
 * @type {number}
 */
WebSocket.prototype.CLOSED = 2;

/**
 * Represents the state of the connection.
 * @type {number}
 */
WebSocket.prototype.readyState;

/**
 * Returns the number of bytes that have been queued but not yet sent.
 * @type {number}
 */
WebSocket.prototype.bufferedAmount;

/**
 * An event handler called on open event.
 * @type {?function(Event)}
 */
WebSocket.prototype.onopen;

/**
 * An event handler called on message event.
 * @type {?function(MessageEvent)}
 */
WebSocket.prototype.onmessage;

/**
 * An event handler called on close event.
 * @type {?function(Event)}
 */
WebSocket.prototype.onclose;

/**
 * Transmits data using the connection.
 * @param {string} data
 * @return {boolean}
 */
WebSocket.prototype.send = function(data) {};

/**
 * Closes the Web Socket connection or connection attempt, if any.
 */
WebSocket.prototype.close = function() {};

// HTML5 History
/**
 * Pushes a new state into the session history.
 * @see http://www.w3.org/TR/html5/history.html#the-history-interface
 * @param {*} data New state.
 * @param {string} title The title for a new session history entry.
 * @param {string=} opt_url The URL for a new session history entry.
 */
History.prototype.pushState = function(data, title, opt_url) {};

/**
 * Replaces the current state in the session history.
 * @see http://www.w3.org/TR/html5/history.html#the-history-interface
 * @param {*} data New state.
 * @param {string} title The title for a session history entry.
 * @param {string=} opt_url The URL for a new session history entry.
 */
History.prototype.replaceState = function(data, title, opt_url) {};
