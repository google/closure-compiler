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
 *
 *  @see http://www.whatwg.org/specs/web-apps/current-work/multipage/index.html
 *  @see http://dev.w3.org/html5/spec/Overview.html
 *
 *  This also includes Typed Array definitions from
 *  http://www.khronos.org/registry/typedarray/specs/latest/
 *
 *  This relies on w3c_event.js being included first.
 *
 * @externs
 */

/**
 * @constructor
 * @extends {HTMLElement}
 */
function HTMLCanvasElement() {}

/** @type {number} */
HTMLCanvasElement.prototype.width;

/** @type {number} */
HTMLCanvasElement.prototype.height;

/**
 * @param {string=} opt_type
 * @return {string}
 */
HTMLCanvasElement.prototype.toDataURL = function(opt_type) {};

/**
 * @param {string} contextId
 * @param {Object=} opt_args
 * @return {Object}
 */
HTMLCanvasElement.prototype.getContext = function(contextId, opt_args) {};

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
 * @param {HTMLImageElement|HTMLCanvasElement|Image|HTMLVideoElement} image
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

/**
 * Note: Webkit only
 * @param {number|string=} opt_a
 * @param {number=} opt_b
 * @param {number=} opt_c
 * @param {number=} opt_d
 * @param {number=} opt_e
 * @see http://developer.apple.com/library/safari/#documentation/appleapplications/reference/WebKitDOMRef/CanvasRenderingContext2D_idl/Classes/CanvasRenderingContext2D/index.html
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.setFillColor;

/**
 * Note: Webkit only
 * @param {number|string=} opt_a
 * @param {number=} opt_b
 * @param {number=} opt_c
 * @param {number=} opt_d
 * @param {number=} opt_e
 * @see http://developer.apple.com/library/safari/#documentation/appleapplications/reference/WebKitDOMRef/CanvasRenderingContext2D_idl/Classes/CanvasRenderingContext2D/index.html
 * @return {undefined}
 */
CanvasRenderingContext2D.prototype.setStrokeColor;

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
 * @param {function(!SQLTransaction) : void} callback
 * @param {(function(!SQLError) : void)=} opt_errorCallback
 * @param {Function=} opt_Callback
 */
Database.prototype.transaction = function(
    callback, opt_errorCallback, opt_Callback) {};

/**
 * @param {function(!SQLTransaction) : void} callback
 * @param {(function(!SQLError) : void)=} opt_errorCallback
 * @param {Function=} opt_Callback
 */
Database.prototype.readTransaction = function(
    callback, opt_errorCallback, opt_Callback) {};

/**
 * @param {string} oldVersion
 * @param {string} newVersion
 * @param {function(!SQLTransaction) : void} callback
 * @param {function(!SQLError) : void} errorCallback
 * @param {Function} successCallback
 */
Database.prototype.changeVersion = function(
    oldVersion, newVersion, callback, errorCallback, successCallback) {};

/**
 * @interface
 */
function DatabaseCallback() {}

/**
 * @param {!Database} db
 * @return {undefined}
 */
DatabaseCallback.prototype.handleEvent = function(db) {};

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
 * @param {SQLStatementCallback=} opt_callback
 * @param {(function(!SQLTransaction, !SQLError) : void)=} opt_errorCallback
 */
SQLTransaction.prototype.executeSql = function(
    sqlStatement, opt_queryArgs, opt_callback, opt_errorCallback) {};

/**
 * @typedef {(function(!SQLTransaction, !SQLResultSet) : void)}
 */
var SQLStatementCallback;

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
 * @nosideeffects
 */
SQLResultSetRowList.prototype.item = function(index) {};

/**
 * @param {string} name
 * @param {string} version
 * @param {string} description
 * @param {number} size
 * @param {(DatabaseCallback|function(Database))=} opt_callback
 * @return {Database}
 */
function openDatabase(name, version, description, size, opt_callback) {}

/**
 * @param {string} name
 * @param {string} version
 * @param {string} description
 * @param {number} size
 * @param {(DatabaseCallback|function(Database))=} opt_callback
 * @return {Database}
 */
Window.prototype.openDatabase =
    function(name, version, description, size, opt_callback) {};

/**
 * @type {boolean}
 */
HTMLImageElement.prototype.complete;

/**
 * @type {string}
 * @see http://www.whatwg.org/specs/web-apps/current-work/multipage/embedded-content-1.html#attr-img-crossorigin
 */
HTMLImageElement.prototype.crossOrigin;

/**
 * The postMessage method (as defined by HTML5 spec and implemented in FF3).
 * @param {*} message
 * @param {string|Array} targetOrigin The target origin in the 2-argument
 *     version of this function. Webkit seems to have implemented this
 *     function wrong in the 3-argument version so that ports is the
 *     second argument.
 * @param {string|Array=} ports An optional array of ports or the target
 *     origin. Webkit seems to have implemented this
 *     function wrong in the 3-argument version so that targetOrigin is the
 *     third argument.
 * @see http://dev.w3.org/html5/postmsg/#dom-window-postmessage
 */
Window.prototype.postMessage = function(message, targetOrigin, ports) {};

/**
 * The postMessage method (as implemented in Opera).
 * @param {string} message
 */
Document.prototype.postMessage = function(message) {};

/**
 * Document head accessor.
 * @see http://www.whatwg.org/specs/web-apps/current-work/multipage/dom.html#the-head-element-0
 * @type {HTMLHeadElement}
 */
Document.prototype.head;

/**
 * @see https://developer.apple.com/webapps/docs/documentation/AppleApplications/Reference/SafariJSRef/DOMApplicationCache/DOMApplicationCache.html
 * @constructor
 * @implements {EventTarget}
 */
function DOMApplicationCache() {}

/** @override */
DOMApplicationCache.prototype.addEventListener = function(
    type, listener, useCapture) {};

/** @override */
DOMApplicationCache.prototype.removeEventListener = function(
    type, listener, useCapture) {};

/** @override */
DOMApplicationCache.prototype.dispatchEvent = function(evt) {};

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
 * @see https://developer.mozilla.org/En/DOM/Worker/Functions_available_to_workers
 * @param {...string} var_args
 */
Window.prototype.importScripts = function(var_args) {};

/**
 * @see https://developer.mozilla.org/En/DOM/Worker/Functions_available_to_workers
 * @param {...string} var_args
 */
var importScripts = function(var_args) {};

/**
 * @see http://dev.w3.org/html5/workers/
 * @constructor
 * @implements {EventTarget}
 */
function WebWorker() {}

/** @override */
WebWorker.prototype.addEventListener = function(
    type, listener, useCapture) {};

/** @override */
WebWorker.prototype.removeEventListener = function(
    type, listener, useCapture) {};

/** @override */
WebWorker.prototype.dispatchEvent = function(evt) {};

/**
 * Stops the worker process
 */
WebWorker.prototype.terminate = function() {};

/**
 * Posts a message to the worker thread.
 * @param {string} message
 */
WebWorker.prototype.postMessage = function(message) {};

/**
 * Sent when the worker thread posts a message to its creator.
 * @type {EventListener}
 */
WebWorker.prototype.onmessage;

/**
 * Sent when the worker thread encounters an error.
 * @type {EventListener}
 */
WebWorker.prototype.onerror;

/**
 * @see http://dev.w3.org/html5/workers/
 * @constructor
 * @implements {EventTarget}
 */
function Worker(opt_arg0) {}

/** @override */
Worker.prototype.addEventListener = function(
    type, listener, useCapture) {};

/** @override */
Worker.prototype.removeEventListener = function(
    type, listener, useCapture) {};

/** @override */
Worker.prototype.dispatchEvent = function(evt) {};

/**
 * Stops the worker process
 */
Worker.prototype.terminate = function() {};

/**
 * Posts a message to the worker thread.
 * @param {*} message
 * @param {Array.<MessagePort>=} opt_ports
 */
Worker.prototype.postMessage = function(message, opt_ports) {};

/**
 * Sent when the worker thread posts a message to its creator.
 */
Worker.prototype.onmessage = function() {};

/**
 * Sent when the worker thread encounters an error.
 */
Worker.prototype.onerror = function() {};

/**
 * @see http://dev.w3.org/html5/workers/
 * @param {string} scriptURL The URL of the script to run in the SharedWorker.
 * @param {string=} opt_name A name that can later be used to obtain a
 *     reference to the same SharedWorker.
 * @constructor
 * @implements {EventTarget}
 */
function SharedWorker(scriptURL, opt_name) {}

/** @override */
SharedWorker.prototype.addEventListener = function(
    type, listener, useCapture) {};

/** @override */
SharedWorker.prototype.removeEventListener = function(
    type, listener, useCapture) {};

/** @override */
SharedWorker.prototype.dispatchEvent = function(evt) {};

/**
 * @type {!MessagePort}
 */
SharedWorker.prototype.port;

/**
 * Called on network errors for loading the initial script.
 */
SharedWorker.prototype.onerror = function() {};

/** @type {Element} */
HTMLElement.prototype.contextMenu;

/** @type {boolean} */
HTMLElement.prototype.draggable;

/**
 * This is actually a DOMSettableTokenList property. However since that
 * interface isn't currently defined and no known browsers implement this
 * feature, just define the property for now.
 *
 * @const
 * @type {Object}
 */
HTMLElement.prototype.dropzone;

/**
 * @see http://www.w3.org/TR/html5/dom.html#dom-getelementsbyclassname
 * @param {string} classNames
 * @return {!NodeList}
 * @nosideeffects
 */
HTMLElement.prototype.getElementsByClassName = function(classNames) {};
// NOTE: Document.prototype.getElementsByClassName is in gecko_dom.js

/** @type {boolean} */
HTMLElement.prototype.hidden;

/** @type {boolean} */
HTMLElement.prototype.spellcheck;

/** @type {string} */
HTMLInputElement.prototype.autocomplete;

/** @type {string} */
HTMLInputElement.prototype.dirname;

/** @type {FileList} */
HTMLInputElement.prototype.files;

/** @type {string} */
HTMLInputElement.prototype.list;

/** @type {string} */
HTMLInputElement.prototype.max;

/** @type {string} */
HTMLInputElement.prototype.min;

/** @type {string} */
HTMLInputElement.prototype.pattern;

/** @type {boolean} */
HTMLInputElement.prototype.multiple;

/** @type {string} */
HTMLInputElement.prototype.placeholder;

/** @type {boolean} */
HTMLInputElement.prototype.required;

/** @type {string} */
HTMLInputElement.prototype.step;


/**
 * @constructor
 * @extends {HTMLElement}
 */
function HTMLMediaElement() {}

/** @type {MediaError} */
HTMLMediaElement.prototype.error;

/** @type {string} */
HTMLMediaElement.prototype.src;

/** @type {string} */
HTMLMediaElement.prototype.currentSrc;

/** @type {number} */
HTMLMediaElement.prototype.networkState;

/** @type {boolean} */
HTMLMediaElement.prototype.autobuffer;

/** @type {TimeRanges} */
HTMLMediaElement.prototype.buffered;

/**
 * Loads the media element.
 */
HTMLMediaElement.prototype.load = function() {};

/**
 * @param {string} type Type of the element in question in question.
 * @return {string} Whether it can play the type.
 * @nosideeffects
 */
HTMLMediaElement.prototype.canPlayType = function(type) {};

/** @type {number} */
HTMLMediaElement.prototype.readyState;

/** @type {boolean} */
HTMLMediaElement.prototype.seeking;

/** @type {number} */
HTMLMediaElement.prototype.currentTime;

/** @type {number} */
HTMLMediaElement.prototype.startTime;

/** @type {number} */
HTMLMediaElement.prototype.duration;

/** @type {boolean} */
HTMLMediaElement.prototype.paused;

/** @type {number} */
HTMLMediaElement.prototype.defaultPlaybackRate;

/** @type {number} */
HTMLMediaElement.prototype.playbackRate;

/** @type {TimeRanges} */
HTMLMediaElement.prototype.played;

/** @type {TimeRanges} */
HTMLMediaElement.prototype.seekable;

/** @type {boolean} */
HTMLMediaElement.prototype.ended;

/** @type {boolean} */
HTMLMediaElement.prototype.autoplay;

/** @type {boolean} */
HTMLMediaElement.prototype.loop;

/**
 * Starts playing the media.
 */
HTMLMediaElement.prototype.play = function() {};

/**
 * Pauses the media.
 */
HTMLMediaElement.prototype.pause = function() {};

/** @type {boolean} */
HTMLMediaElement.prototype.controls;

/** @type {number} */
HTMLMediaElement.prototype.volume;

/** @type {boolean} */
HTMLMediaElement.prototype.muted;

/**
 * @constructor
 * @extends {HTMLMediaElement}
 */
function HTMLAudioElement() {}

/**
 * @constructor
 * @extends {HTMLMediaElement}
 */
function HTMLVideoElement() {}

/**
 * Starts displaying the video in full screen mode.
 */
HTMLVideoElement.prototype.webkitEnterFullscreen = function() {};

/**
 * Starts displaying the video in full screen mode.
 */
HTMLVideoElement.prototype.webkitEnterFullScreen = function() {};

/**
 * Stops displaying the video in full screen mode.
 */
HTMLVideoElement.prototype.webkitExitFullscreen = function() {};

/**
 * Stops displaying the video in full screen mode.
 */
HTMLVideoElement.prototype.webkitExitFullScreen = function() {};

/** @type {string} */
HTMLVideoElement.prototype.width;

/** @type {string} */
HTMLVideoElement.prototype.height;

/** @type {number} */
HTMLVideoElement.prototype.videoWidth;

/** @type {number} */
HTMLVideoElement.prototype.videoHeight;

/** @type {string} */
HTMLVideoElement.prototype.poster;

/** @type {boolean} */
HTMLVideoElement.prototype.webkitSupportsFullscreen;

/** @type {boolean} */
HTMLVideoElement.prototype.webkitDisplayingFullscreen;

/**
 * @constructor
 */
function MediaError() {}

/** @type {number} */
MediaError.prototype.code;

// HTML5 MessageChannel
/**
 * @see http://dev.w3.org/html5/spec/comms.html#messagechannel
 * @constructor
 */
function MessageChannel() {}

/**
 * Returns the first port.
 * @type {!MessagePort}
 */
MessageChannel.prototype.port1;

/**
 * Returns the second port.
 * @type {!MessagePort}
 */
MessageChannel.prototype.port2;

// HTML5 MessagePort
/**
 * @see http://dev.w3.org/html5/spec/comms.html#messageport
 * @constructor
 * @implements {EventTarget}
 */
function MessagePort() {}

/** @override */
MessagePort.prototype.addEventListener = function(
    type, listener, useCapture) {};

/** @override */
MessagePort.prototype.removeEventListener = function(
    type, listener, useCapture) {};

/** @override */
MessagePort.prototype.dispatchEvent = function(evt) {};


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

/**
 * HTML5 DataTransfer class
 * @see http://dev.w3.org/html5/spec/dnd.html#the-dragevent-and-datatransfer-interfaces
 * @constructor
 */
function DataTransfer() {}

/** @type {string} */
DataTransfer.prototype.dropEffect;

/** @type {string} */
DataTransfer.prototype.effectAllowed;

/** @type {Array.<string>} */
DataTransfer.prototype.types;

/** @type {FileList} */
DataTransfer.prototype.files;

/**
 * @param {string=} opt_format Format for which to remove data.
 */
DataTransfer.prototype.clearData = function(opt_format) {};

/**
 * @param {string} format Format for which to set data.
 * @param {string} data Data to add.
 */
DataTransfer.prototype.setData = function(format, data) {};

/**
 * @param {string} format Format for which to set data.
 * @return {string} Data for the given format.
 */
DataTransfer.prototype.getData = function(format) { return ''; };

/**
 * @param {HTMLElement} img The image to use when dragging.
 * @param {number} x Horizontal position of the cursor.
 * @param {number} y Vertical position of the cursor.
 */
DataTransfer.prototype.setDragImage = function(img, x, y) {};

/**
 * @param {HTMLElement} elem Element to receive drag result events.
 */
DataTransfer.prototype.addElement = function(elem) {};

/**
 * Addition for accessing clipboard file data that are part of the proposed
 * HTML5 spec.
 * @type {DataTransfer}
 */
MouseEvent.prototype.dataTransfer;


/**
 * @constructor
 * @extends {Event}
 */
function ProgressEvent() {}

/** @type {number} */
ProgressEvent.prototype.total;

/** @type {number} */
ProgressEvent.prototype.loaded;

/** @type {boolean} */
ProgressEvent.prototype.lengthComputable;


/**
 * @constructor
 */
function TimeRanges() {}

/** @type {number} */
TimeRanges.prototype.length;

/**
 * @param {number} index The index.
 * @return {number} The start time of the range at index.
 * @throws {DOMException}
 */
TimeRanges.prototype.start = function(index) { return 0; };

/**
 * @param {number} index The index.
 * @return {number} The end time of the range at index.
 * @throws {DOMException}
 */
TimeRanges.prototype.end = function(index) { return 0; };


// HTML5 Web Socket class
/**
 * @see http://dev.w3.org/html5/websockets/
 * @constructor
 * @param {string} url
 * @param {string=} opt_protocol
 * @implements {EventTarget}
 */
function WebSocket(url, opt_protocol) {}

/** @override */
WebSocket.prototype.addEventListener = function(
    type, listener, useCapture) {};

/** @override */
WebSocket.prototype.removeEventListener = function(
    type, listener, useCapture) {};

/** @override */
WebSocket.prototype.dispatchEvent = function(evt) {};

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
 * @type {?function(!Event)}
 */
WebSocket.prototype.onopen;

/**
 * An event handler called on message event.
 * @type {?function(!MessageEvent)}
 */
WebSocket.prototype.onmessage;

/**
 * An event handler called on close event.
 * @type {?function(!Event)}
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

/**
 * @see http://www.w3.org/TR/html5/history.html#event-definitions
 * @constructor
 * @extends {Event}
 */
function PopStateEvent() {}

/**
 * @type {*}
 */
PopStateEvent.prototype.state;

/**
 * Initializes the event after it has been created with document.createEvent
 * @param {string} typeArg
 * @param {boolean} canBubbleArg
 * @param {boolean} cancelableArg
 * @param {*} stateArg
 */
PopStateEvent.prototype.initPopStateEvent = function(typeArg, canBubbleArg,
    cancelableArg, stateArg) {};

/**
 * @see http://www.w3.org/TR/html5/history.html#event-definitions
 * @constructor
 * @extends {Event}
 */
function HashChangeEvent() {}

/** @type {string} */
HashChangeEvent.prototype.oldURL;

/** @type {string} */
HashChangeEvent.prototype.newURL;

/**
 * Initializes the event after it has been created with document.createEvent
 * @param {string} typeArg
 * @param {boolean} canBubbleArg
 * @param {boolean} cancelableArg
 * @param {string} oldURLArg
 * @param {string} newURLArg
 */
HashChangeEvent.prototype.initHashChangeEvent = function(typeArg, canBubbleArg,
    cancelableArg, oldURLArg, newURLArg) {};

/**
 * @see http://www.w3.org/TR/html5/history.html#event-definitions
 * @constructor
 * @extends {Event}
 */
function PageTransitionEvent() {}

/** @type {boolean} */
PageTransitionEvent.prototype.persisted;

/**
 * Initializes the event after it has been created with document.createEvent
 * @param {string} typeArg
 * @param {boolean} canBubbleArg
 * @param {boolean} cancelableArg
 * @param {*} persistedArg
 */
PageTransitionEvent.prototype.initPageTransitionEvent = function(typeArg,
    canBubbleArg, cancelableArg, persistedArg) {};

/**
 * @constructor
 */
function FileList() {}

/** @type {number} */
FileList.prototype.length;

/**
 * @param {number} i File to return from the list.
 * @return {File} The ith file in the list.
 * @nosideeffects
 */
FileList.prototype.item = function(i) { return null; };

/**
 * @type {boolean}
 * @see http://dev.w3.org/2006/webapi/XMLHttpRequest-2/#withcredentials
 */
XMLHttpRequest.prototype.withCredentials;

/**
 * @type {XMLHttpRequestUpload}
 * @see http://dev.w3.org/2006/webapi/XMLHttpRequest-2/#the-upload-attribute
 */
XMLHttpRequest.prototype.upload;

/**
 * @param {string} mimeType The mime type to override with.
 */
XMLHttpRequest.prototype.overrideMimeType = function(mimeType) {};

/**
 * @type {string}
 * @see http://dev.w3.org/2006/webapi/XMLHttpRequest-2/#the-responsetype-attribute
 */
XMLHttpRequest.prototype.responseType;

/**
 * @type {*}
 * @see http://dev.w3.org/2006/webapi/XMLHttpRequest-2/#the-responsetype-attribute
 */
XMLHttpRequest.prototype.response;


/**
 * @type {ArrayBuffer}
 * Implemented as a draft spec in Firefox 4 as the way to get a requested array
 * buffer from an XMLHttpRequest.
 * @see https://developer.mozilla.org/En/Using_XMLHttpRequest#Receiving_binary_data_using_JavaScript_typed_arrays
 */
XMLHttpRequest.prototype.mozResponseArrayBuffer;

/**
 * XMLHttpRequestEventTarget defines events for checking the status of a data
 * transfer between a client and a server. This should be a common base class
 * for XMLHttpRequest and XMLHttpRequestUpload.
 *
 * @constructor
 * @implements {EventTarget}
 */
function XMLHttpRequestEventTarget() {}

/** @override */
XMLHttpRequestEventTarget.prototype.addEventListener = function(
    type, listener, useCapture) {};

/** @override */
XMLHttpRequestEventTarget.prototype.removeEventListener = function(
    type, listener, useCapture) {};

/** @override */
XMLHttpRequestEventTarget.prototype.dispatchEvent = function(evt) {};

/**
 * An event target to track the status of an upload.
 *
 * @constructor
 * @extends {XMLHttpRequestEventTarget}
 */
function XMLHttpRequestUpload() {}

/**
 * @param {number=} opt_width
 * @param {number=} opt_height
 * @constructor
 * @extends {HTMLImageElement}
 */
function Image(opt_width, opt_height) {}


/**
 * Dataset collection.
 * This is really a DOMStringMap but it behaves close enough to an object to
 * pass as an object.
 * @type {Object}
 * @const
 */
HTMLElement.prototype.dataset;


/**
 * @constructor
 */
function DOMTokenList() {}

/**
 * Returns the number of CSS classes applied to this Element.
 * @type {number}
 */
DOMTokenList.prototype.length;

/**
 * @param {number} index The index of the item to return.
 * @return {string} The CSS class at the specified index.
 * @nosideeffects
 */
DOMTokenList.prototype.item = function(index) {};

/**
 * @param {string} token The CSS class to check for.
 * @return {boolean} Whether the CSS class has been applied to the Element.
 * @nosideeffects
 */
DOMTokenList.prototype.contains = function(token) {};

/**
 * @param {string} token The CSS class to add to this element.
 */
DOMTokenList.prototype.add = function(token) {};

/**
 * @param {string} token The CSS class to remove from this element.
 */
DOMTokenList.prototype.remove = function(token) {};

/**
 * @param {string} token The CSS class to toggle from this element.
 * @return {boolean} False if the token was removed; True otherwise.
 */
DOMTokenList.prototype.toggle = function(token) {};

/**
 * @return {string} A stringified representation of CSS classes.
 * @nosideeffects
 */
DOMTokenList.prototype.toString = function() {};

/**
 * A better interface to CSS classes than className.
 * @type {DOMTokenList}
 * @see http://www.w3.org/TR/html5/elements.html#dom-classlist
 * @const
 */
HTMLElement.prototype.classList;


/**
 * @param {number} length The length in bytes
 * @constructor
 * @noalias
 */
function ArrayBuffer(length) {}

/** @type {number} */
ArrayBuffer.prototype.byteLength;

/**
 * @param {number} begin
 * @param {number=} opt_end
 * @return {!ArrayBuffer}
 */
ArrayBuffer.prototype.slice = function(begin, opt_end) {};


/**
 * @constructor
 * @noalias
 */
function ArrayBufferView() {}

/** @type {!ArrayBuffer} */
ArrayBufferView.prototype.buffer;

/** @type {number} */
ArrayBufferView.prototype.byteOffset;

/** @type {number} */
ArrayBufferView.prototype.byteLength;


/**
 * @param {number|ArrayBufferView|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {ArrayBufferView}
 * @constructor
 * @noalias
 */
function Int8Array(length, opt_byteOffset, opt_length) {}

/** @type {number} */
Int8Array.BYTES_PER_ELEMENT;

/** @type {number} */
Int8Array.prototype.BYTES_PER_ELEMENT;

/** @type {number} */
Int8Array.prototype.length;

/**
 * @param {ArrayBufferView|Array.<number>} array
 * @param {number=} opt_offset
 */
Int8Array.prototype.set = function(array, opt_offset) {};

/**
 * @param {number} begin
 * @param {number=} opt_end
 * @return {!Int8Array}
 */
Int8Array.prototype.subarray = function(begin, opt_end) {};


/**
 * @param {number|ArrayBufferView|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {ArrayBufferView}
 * @constructor
 * @noalias
 */
function Uint8Array(length, opt_byteOffset, opt_length) {}

/** @type {number} */
Uint8Array.BYTES_PER_ELEMENT;

/** @type {number} */
Uint8Array.prototype.BYTES_PER_ELEMENT;

/** @type {number} */
Uint8Array.prototype.length;

/**
 * @param {ArrayBufferView|Array.<number>} array
 * @param {number=} opt_offset
 */
Uint8Array.prototype.set = function(array, opt_offset) {};

/**
 * @param {number} begin
 * @param {number=} opt_end
 * @return {!Uint8Array}
 */
Uint8Array.prototype.subarray = function(begin, opt_end) {};


/**
 * @param {number|ArrayBufferView|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {ArrayBufferView}
 * @constructor
 * @noalias
 */
function Uint8ClampedArray(length, opt_byteOffset, opt_length) {}

/** @type {number} */
Uint8ClampedArray.BYTES_PER_ELEMENT;

/** @type {number} */
Uint8ClampedArray.prototype.BYTES_PER_ELEMENT;

/** @type {number} */
Uint8ClampedArray.prototype.length;

/**
 * @param {ArrayBufferView|Array.<number>} array
 * @param {number=} opt_offset
 */
Uint8ClampedArray.prototype.set = function(array, opt_offset) {};

/**
 * @param {number} begin
 * @param {number=} opt_end
 * @return {!Uint8ClampedArray}
 */
Uint8ClampedArray.prototype.subarray = function(begin, opt_end) {};


/**
 * @param {number|ArrayBufferView|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {ArrayBufferView}
 * @constructor
 * @noalias
 */
function Int16Array(length, opt_byteOffset, opt_length) {}

/** @type {number} */
Int16Array.BYTES_PER_ELEMENT;

/** @type {number} */
Int16Array.prototype.BYTES_PER_ELEMENT;

/** @type {number} */
Int16Array.prototype.length;

/**
 * @param {ArrayBufferView|Array.<number>} array
 * @param {number=} opt_offset
 */
Int16Array.prototype.set = function(array, opt_offset) {};

/**
 * @param {number} begin
 * @param {number=} opt_end
 * @return {!Int16Array}
 */
Int16Array.prototype.subarray = function(begin, opt_end) {};


/**
 * @param {number|ArrayBufferView|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {ArrayBufferView}
 * @constructor
 * @noalias
 */
function Uint16Array(length, opt_byteOffset, opt_length) {}

/** @type {number} */
Uint16Array.BYTES_PER_ELEMENT;

/** @type {number} */
Uint16Array.prototype.BYTES_PER_ELEMENT;

/** @type {number} */
Uint16Array.prototype.length;

/**
 * @param {ArrayBufferView|Array.<number>} array
 * @param {number=} opt_offset
 */
Uint16Array.prototype.set = function(array, opt_offset) {};

/**
 * @param {number} begin
 * @param {number=} opt_end
 * @return {!Uint16Array}
 */
Uint16Array.prototype.subarray = function(begin, opt_end) {};


/**
 * @param {number|ArrayBufferView|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {ArrayBufferView}
 * @constructor
 * @noalias
 */
function Int32Array(length, opt_byteOffset, opt_length) {}

/** @type {number} */
Int32Array.BYTES_PER_ELEMENT;

/** @type {number} */
Int32Array.prototype.BYTES_PER_ELEMENT;

/** @type {number} */
Int32Array.prototype.length;

/**
 * @param {ArrayBufferView|Array.<number>} array
 * @param {number=} opt_offset
 */
Int32Array.prototype.set = function(array, opt_offset) {};

/**
 * @param {number} begin
 * @param {number=} opt_end
 * @return {!Int32Array}
 */
Int32Array.prototype.subarray = function(begin, opt_end) {};


/**
 * @param {number|ArrayBufferView|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {ArrayBufferView}
 * @constructor
 * @noalias
 */
function Uint32Array(length, opt_byteOffset, opt_length) {}

/** @type {number} */
Uint32Array.BYTES_PER_ELEMENT;

/** @type {number} */
Uint32Array.prototype.BYTES_PER_ELEMENT;

/** @type {number} */
Uint32Array.prototype.length;

/**
 * @param {ArrayBufferView|Array.<number>} array
 * @param {number=} opt_offset
 */
Uint32Array.prototype.set = function(array, opt_offset) {};

/**
 * @param {number} begin
 * @param {number=} opt_end
 * @return {!Uint32Array}
 */
Uint32Array.prototype.subarray = function(begin, opt_end) {};


/**
 * @param {number|ArrayBufferView|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {ArrayBufferView}
 * @constructor
 * @noalias
 */
function Float32Array(length, opt_byteOffset, opt_length) {}

/** @type {number} */
Float32Array.BYTES_PER_ELEMENT;

/** @type {number} */
Float32Array.prototype.BYTES_PER_ELEMENT;

/** @type {number} */
Float32Array.prototype.length;

/**
 * @param {ArrayBufferView|Array.<number>} array
 * @param {number=} opt_offset
 */
Float32Array.prototype.set = function(array, opt_offset) {};

/**
 * @param {number} begin
 * @param {number=} opt_end
 * @return {!Float32Array}
 */
Float32Array.prototype.subarray = function(begin, opt_end) {};


/**
 * @param {number|ArrayBufferView|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {ArrayBufferView}
 * @constructor
 * @noalias
 */
function Float64Array(length, opt_byteOffset, opt_length) {}

/** @type {number} */
Float64Array.BYTES_PER_ELEMENT;

/** @type {number} */
Float64Array.prototype.BYTES_PER_ELEMENT;

/** @type {number} */
Float64Array.prototype.length;

/**
 * @param {ArrayBufferView|Array.<number>} array
 * @param {number=} opt_offset
 */
Float64Array.prototype.set = function(array, opt_offset) {};

/**
 * @param {number} begin
 * @param {number=} opt_end
 * @return {!Float64Array}
 */
Float64Array.prototype.subarray = function(begin, opt_end) {};


/**
 * @param {ArrayBuffer} buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_byteLength
 * @extends {ArrayBufferView}
 * @constructor
 * @noalias
 */
function DataView(buffer, opt_byteOffset, opt_byteLength) {}

/**
 * @param {number} byteOffset
 * @return {number}
 */
DataView.prototype.getInt8 = function(byteOffset) {};

/**
 * @param {number} byteOffset
 * @return {number}
 */
DataView.prototype.getUint8 = function(byteOffset) {};

/**
 * @param {number} byteOffset
 * @param {boolean=} opt_littleEndian
 * @return {number}
 */
DataView.prototype.getInt16 = function(byteOffset, opt_littleEndian) {};

/**
 * @param {number} byteOffset
 * @param {boolean=} opt_littleEndian
 * @return {number}
 */
DataView.prototype.getUint16 = function(byteOffset, opt_littleEndian) {};

/**
 * @param {number} byteOffset
 * @param {boolean=} opt_littleEndian
 * @return {number}
 */
DataView.prototype.getInt32 = function(byteOffset, opt_littleEndian) {};

/**
 * @param {number} byteOffset
 * @param {boolean=} opt_littleEndian
 * @return {number}
 */
DataView.prototype.getUint32 = function(byteOffset, opt_littleEndian) {};

/**
 * @param {number} byteOffset
 * @param {boolean=} opt_littleEndian
 * @return {number}
 */
DataView.prototype.getFloat32 = function(byteOffset, opt_littleEndian) {};

/**
 * @param {number} byteOffset
 * @param {boolean=} opt_littleEndian
 * @return {number}
 */
DataView.prototype.getFloat64 = function(byteOffset, opt_littleEndian) {};

/**
 * @param {number} byteOffset
 * @param {number} value
 */
DataView.prototype.setInt8 = function(byteOffset, value) {};

/**
 * @param {number} byteOffset
 * @param {number} value
 */
DataView.prototype.setUint8 = function(byteOffset, value) {};

/**
 * @param {number} byteOffset
 * @param {number} value
 * @param {boolean=} opt_littleEndian
 */
DataView.prototype.setInt16 = function(byteOffset, value, opt_littleEndian) {};

/**
 * @param {number} byteOffset
 * @param {number} value
 * @param {boolean=} opt_littleEndian
 */
DataView.prototype.setUint16 = function(byteOffset, value, opt_littleEndian) {};

/**
 * @param {number} byteOffset
 * @param {number} value
 * @param {boolean=} opt_littleEndian
 */
DataView.prototype.setInt32 = function(byteOffset, value, opt_littleEndian) {};

/**
 * @param {number} byteOffset
 * @param {number} value
 * @param {boolean=} opt_littleEndian
 */
DataView.prototype.setUint32 = function(byteOffset, value, opt_littleEndian) {};

/**
 * @param {number} byteOffset
 * @param {number} value
 * @param {boolean=} opt_littleEndian
 */
DataView.prototype.setFloat32 = function(
    byteOffset, value, opt_littleEndian) {};

/**
 * @param {number} byteOffset
 * @param {number} value
 * @param {boolean=} opt_littleEndian
 */
DataView.prototype.setFloat64 = function(
    byteOffset, value, opt_littleEndian) {};
