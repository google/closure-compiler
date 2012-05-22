/*
 * Copyright 2007 Google Inc.
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
 * @fileoverview Extern types introduced by the Gears extension.
 * @externs
 */


/** @constructor */
function GearsFactory() {}

/**
 * @param {*=} opt_classVersion
 * @return {Object}
 */
GearsFactory.prototype.create = function(className, opt_classVersion) {};

/** @return {string} */
GearsFactory.prototype.getBuildInfo = function() {};

/**
 * @param {*=} opt_siteName
 * @param {*=} opt_imageUrl
 * @param {*=} opt_extraMessage
 * @return {boolean}
 */
GearsFactory.prototype.getPermission = function(opt_siteName,
                                                opt_imageUrl,
                                                opt_extraMessage) {};

/** @type {boolean} */
GearsFactory.prototype.hasPermission = false;

/** @param {Object} globalObject */
GearsFactory.prototype.privateSetGlobalObject = function(globalObject) {};

/** @type {string} */
GearsFactory.prototype.version;


/** @constructor */
function GearsBlob() {}

/** @type {number} */
GearsBlob.prototype.length = 0;

/** @return {GearsBlob} */
GearsBlob.prototype.slice = function(offset, length) {};


/** @constructor */
function GearsBlobBuilder() {}

/** @param {string|GearsBlob} appendee */
GearsBlobBuilder.prototype.append = function(appendee) {};

/** @return {GearsBlob} */
GearsBlobBuilder.prototype.getAsBlob = function() {};


/** @constructor */
function GearsDatabase() {}

/** @param {string=} opt_name */
GearsDatabase.prototype.open = function(opt_name) {};

/**
 * @param {*=} opt_argArray
 * @return {GearsResultSet}
 */
GearsDatabase.prototype.execute = function(sqlStatement, opt_argArray) {};

GearsDatabase.prototype.close = function() {};

GearsDatabase.prototype.remove = function() {};

/** @type {number} */
GearsDatabase.prototype.lastInsertRowId = 0;

/** @type {number} */
GearsDatabase.prototype.rowsAffected = 0;


/** @constructor */
function GearsResultSet() {}

/** @return {boolean} */
GearsResultSet.prototype.isValidRow = function() {};

GearsResultSet.prototype.next = function() {};

GearsResultSet.prototype.close = function() {};

/** @return {number} */
GearsResultSet.prototype.fieldCount = function() {};

/** @return {string} */
GearsResultSet.prototype.fieldName = function(fieldIndex) {};

GearsResultSet.prototype.field = function(fieldIndex) {};

GearsResultSet.prototype.fieldByName = function(fieldName) {};


/** @constructor */
function GearsDesktop() {}

/**
 * @param {string} name The name of the shortcut.
 * @param {string} url The URL for the shortcut.
 * @param {Object} icons The icon set to use for the shortcut.
 * @param {string=} opt_description The description of the shortcut.
 */
GearsDesktop.prototype.createShortcut = function(name,
                                                 url,
                                                 icons,
                                                 opt_description) {};

/**
 * @param {GearsBlob} blob The blob to get metadata for.
 * @return {Object} The metadata object.
 */
GearsDesktop.prototype.extractMetaData = function(blob) { return null; };

/**
 * @param {Object} event The event object we're calling from.
 * @param {string} flavor The type of object to get.
 * @return {Object} The drag data.
 */
GearsDesktop.prototype.getDragData = function(event, flavor) { return null; };

/**
 * @param {function(Array.<GearsFile>)} callback The callback after opening.
 * @param {GearsOpenFileOptions=} opt_options The options to use for opening.
 */
GearsDesktop.prototype.openFiles = function(callback, opt_options) {};

/**
 * @param {Object} event
 * @param {string} dropEffect
 */
GearsDesktop.prototype.setDropEffect = function(event, dropEffect) {};


/** @constructor */
function GearsFile() {}

/** @type {string} */
GearsFile.prototype.name = '';

/** @type {GearsBlob} */
GearsFile.prototype.blob;


/** @constructor */
function GearsOpenFileOptions() {}

/** @type {boolean} */
GearsOpenFileOptions.prototype.singleFile = false;

/** @type {Array.<string>} */
GearsOpenFileOptions.prototype.filter = [];


/** @constructor */
function GearsGeolocation() {}

/** @const @type {GearsPosition} */
GearsGeolocation.prototype.lastPosition;

/**
 * @param {function(GearsPosition)} successCallback
 * @param {(function(GearsPositionError)|null)=} opt_errorCallback
 * @param {GearsPositionOptions=} opt_options
 */
GearsGeolocation.prototype.getCurrentPosition = function(successCallback,
                                                         opt_errorCallback,
                                                         opt_options) {};

/**
 * @param {function(GearsPosition)} successCallback
 * @param {(function(GearsPositionError)|null)=} opt_errorCallback
 * @param {GearsPositionOptions=} opt_options
 * @return {number}
 */
GearsGeolocation.prototype.watchPosition = function(successCallback,
                                                    opt_errorCallback,
                                                    opt_options) {};

/** @param {number} watchId */
GearsGeolocation.prototype.clearWatch = function(watchId) {};

/**
 * @param {string=} opt_siteName
 * @param {string=} opt_imageUrl
 * @param {string=} opt_extraMessage
 * @return {boolean}
 */
GearsGeolocation.prototype.getPermission = function(opt_siteName,
                                                    opt_imageUrl,
                                                    opt_extraMessage) {};


/** @constructor */
function GearsCoords() {}

/** @const @type {number} */
GearsCoords.prototype.latitude;

/** @const @type {number} */
GearsCoords.prototype.longitude;

/** @const @type {number} */
GearsCoords.prototype.accuracy;

/** @const @type {number} */
GearsCoords.prototype.altitude;

/** @const @type {number} */
GearsCoords.prototype.altitudeAccuracy;


/** @constructor */
function GearsPosition() {}

/** @const @type {number} */
GearsPosition.prototype.latitude;

/** @const @type {number} */
GearsPosition.prototype.longitude;

/** @const @type {number} */
GearsPosition.prototype.accuracy;

/** @const @type {number} */
GearsPosition.prototype.altitude;

/** @const @type {number} */
GearsPosition.prototype.altitudeAccuracy;

/** @const @type {GearsCoords} */
GearsPosition.prototype.coords;

/** @const @type {Date} */
GearsPosition.prototype.timestamp;

/** @const @type {GearsAddress} */
GearsPosition.prototype.gearsAddress;


/** @constructor */
function GearsPositionOptions() {}

/** @type {boolean} */
GearsPositionOptions.prototype.enableHighAccuracy;

/** @type {boolean} */
GearsPositionOptions.prototype.gearsRequestAddress;

/** @type {string} */
GearsPositionOptions.prototype.gearsAddressLanguage = '';

/** @type {Array.<string>} */
GearsPositionOptions.prototype.gearsLocationProviderUrls;

/** @type {number} */
GearsPositionOptions.prototype.maximumAge;

/** @type {number} */
GearsPositionOptions.prototype.timeout;


/** @constructor */
function GearsPositionError() {}

/** @const @type {number} */
GearsPositionError.prototype.code = 0;

/** @const @type {string} */
GearsPositionError.prototype.message = '';

/** @const @type {number} */
GearsPositionError.prototype.UNKNOWN_ERROR;

/** @const @type {number} */
GearsPositionError.prototype.PERMISSION_DENIED;

/** @const @type {number} */
GearsPositionError.prototype.POSITION_UNAVAILABLE;

/** @const @type {number} */
GearsPositionError.prototype.TIMEOUT;


/** @constructor */
function GearsAddress() {}

/** @const @type {string} */
GearsAddress.prototype.streetNumber = '';

/** @const @type {string} */
GearsAddress.prototype.street = '';

/** @const @type {string} */
GearsAddress.prototype.premises = '';

/** @const @type {string} */
GearsAddress.prototype.city = '';

/** @const @type {string} */
GearsAddress.prototype.county = '';

/** @const @type {string} */
GearsAddress.prototype.region = '';

/** @const @type {string} */
GearsAddress.prototype.country = '';

/** @const @type {string} */
GearsAddress.prototype.countryCode = '';

/** @const @type {string} */
GearsAddress.prototype.postalCode = '';


/** @constructor */
function GearsHttpRequest() {}

GearsHttpRequest.prototype.open = function(method, url, opt_async) {};

GearsHttpRequest.prototype.setRequestHeader = function(name, value) {};

/**
 * @param {(string|GearsBlob|null)=} opt_postData
 */
GearsHttpRequest.prototype.send = function(opt_postData) {};

GearsHttpRequest.prototype.abort = function() {};

/** @return {string} */
GearsHttpRequest.prototype.getResponseHeader = function(name) {};

/** @return {string} */
GearsHttpRequest.prototype.getAllResponseHeaders = function() {};

/** @type {function(GearsProgressEvent)|null} */
GearsHttpRequest.prototype.onprogress = function(progressEvent) {};

GearsHttpRequest.prototype.onreadystatechange = function() {};

/** @type {number} */
GearsHttpRequest.prototype.readyState = 0;

/** @type {GearsBlob} */
GearsHttpRequest.prototype.responseBlob;

/** @type {string} */
GearsHttpRequest.prototype.responseText = '';

/** @type {number} */
GearsHttpRequest.prototype.status = 0;

/** @type {string} */
GearsHttpRequest.prototype.statusText = '';

/** @type {GearsHttpRequestUpload} */
GearsHttpRequest.prototype.upload;


/** @constructor */
function GearsHttpRequestUpload() {}

/** @type {function(GearsProgressEvent)|null} */
GearsHttpRequestUpload.prototype.onprogress = function(progressEvent) {};


/** @constructor */
function GearsProgressEvent() {}

/** @type {number} */
GearsProgressEvent.prototype.total = 0;

/** @type {number} */
GearsProgressEvent.prototype.loaded = 0;

/** @type {boolean} */
GearsProgressEvent.prototype.lengthComputable;


/** @constructor */
function GearsLocalServer() {}

/** @return {boolean} */
GearsLocalServer.prototype.canServeLocally = function(url) {};

/**
 * @param {*=} opt_requiredCookie
 * @return {GearsResourceStore}
 */
GearsLocalServer.prototype.createStore = function(name, opt_requiredCookie) {};

/**
 * @param {*=} opt_requiredCookie
 * @return {GearsResourceStore}
 */
GearsLocalServer.prototype.openStore = function(name, opt_requiredCookie) {};

/**
 * @param {*=} opt_requiredCookie
 */
GearsLocalServer.prototype.removeStore = function(name, opt_requiredCookie) {};

/**
 * @param {*=} opt_requiredCookie
 * @return {GearsManagedResourceStore}
 */
GearsLocalServer.prototype.createManagedStore = function(name,
                                                         opt_requiredCookie) {};
/**
 * @param {*=} opt_requiredCookie
 * @return {GearsManagedResourceStore}
 */
GearsLocalServer.prototype.openManagedStore = function(name,
                                                       opt_requiredCookie) {};

/**
 * @param {*=} opt_requiredCookie
 */
GearsLocalServer.prototype.removeManagedStore = function(name,
                                                         opt_requiredCookie) {};


/** @constructor */
function GearsManagedResourceStore() {}

/** @type {string} */
GearsManagedResourceStore.prototype.name = '';

/** @type {string} */
GearsManagedResourceStore.prototype.requiredCookie = '';

/** @type {boolean} */
GearsManagedResourceStore.prototype.enabled = false;

/** @type {string} */
GearsManagedResourceStore.prototype.manifestUrl = '';

/** @type {number} */
GearsManagedResourceStore.prototype.lastUpdateCheckTime = 0;

/** @type {number} */
GearsManagedResourceStore.prototype.updateStatus = 0;

/** @type {string} */
GearsManagedResourceStore.prototype.lastErrorMessage = '';

/** @type {string} */
GearsManagedResourceStore.prototype.currentVersion = '';

/** @type {function(GearsCompleteObject)|null} */
GearsManagedResourceStore.prototype.oncomplete = function(details) {};

/** @type {function(GearsErrorObject)|null} */
GearsManagedResourceStore.prototype.onerror = function(error) {};

/** @type {function(GearsProgressObject)|null} */
GearsManagedResourceStore.prototype.onprogress = function(details) {};

GearsManagedResourceStore.prototype.checkForUpdate = function() {};


/** @constructor */
function GearsResourceStore() {}

/** @type {string} */
GearsResourceStore.prototype.name = '';

/** @type {string} */
GearsResourceStore.prototype.requiredCookie = '';

/** @type {boolean} */
GearsResourceStore.prototype.enabled = false;

/** @return {number} */
GearsResourceStore.prototype.capture = function(urlOrUrlArray, callback) {};

/**
 * @param {GearsBlob} blob
 * @param {string} url
 * @param {string=} opt_contentType
 */
GearsResourceStore.prototype.captureBlob = function(blob,
                                                    url,
                                                    opt_contentType) {};

GearsResourceStore.prototype.abortCapture = function(captureId) {};

GearsResourceStore.prototype.remove = function(url) {};

GearsResourceStore.prototype.rename = function(srcUrl, destUrl) {};

GearsResourceStore.prototype.copy = function(srcUrl, destUrl) {};

/** @return {boolean} */
GearsResourceStore.prototype.isCaptured = function(url) {};

GearsResourceStore.prototype.captureFile = function(fileInputElement, url) {};

/** @return {string} */
GearsResourceStore.prototype.getCapturedFileName = function(url) {};

/** @return {string} */
GearsResourceStore.prototype.getHeader = function(url, name) {};

/** @return {string} */
GearsResourceStore.prototype.getAllHeaders = function(url) {};

/**
 * @param {string} url
 * @return {GearsBlob}
 */
GearsResourceStore.prototype.getAsBlob = function(url) {};

/** @return {GearsFileSubmitter} */
GearsResourceStore.prototype.createFileSubmitter = function() {};


/** @constructor */
function GearsFileSubmitter() {}

GearsFileSubmitter.prototype.setFileInputElement = function(htmlElement,
                                                            url) {};


/** @constructor */
function GearsTimer() {}

/** @return {number} */
GearsTimer.prototype.setTimeout = function(fullScriptOrCallback, msecDelay) {};

/** @return {number} */
GearsTimer.prototype.setInterval = function(fullScript, msecDelay) {};

GearsTimer.prototype.clearTimeout = function(timerId) {};

GearsTimer.prototype.clearInterval = function(timerId) {};


/** @constructor */
function GearsWorkerPool() {}

/**
 * @param {string} messageText The message contents
 *     (Deprecated, use messageObject.text)
 * @param {number} senderId ID of the source worker.
 *     (Deprecated, use messageObject.sender)
 * @param {GearsMessageObject} messageObject An object containing
 *     all information about a message.
 */
GearsWorkerPool.prototype.onmessage = function(messageText,
                                               senderId,
                                               messageObject) {};

/**
 * @param {!GearsErrorObject} errorObject An object that explains
 *     the problem.
 * @return {boolean|undefined} Return true to indicate that the error was
 *     handled, which prevents it from bubbling up to the parent.
 */
GearsWorkerPool.prototype.onerror = function(errorObject) {};

/** @return {number} */
GearsWorkerPool.prototype.createWorker = function(scriptText) {};

/** @return {number} */
GearsWorkerPool.prototype.createWorkerFromUrl = function(scriptUrl) {};

/**
 * @param {*} message
 * @param {number} destWorkerId
 */
GearsWorkerPool.prototype.sendMessage = function(message, destWorkerId) {};

GearsWorkerPool.prototype.allowCrossOrigin = function() {};


/** @constructor */
function GearsMessageObject() {}

/** @type {string} */
GearsMessageObject.prototype.text = '';

/** @type {string} */
GearsMessageObject.prototype.sender = '';

/** @type {string} */
GearsMessageObject.prototype.origin = '';

/** @type {*} */
GearsMessageObject.prototype.body;


/** @constructor */
function GearsCompleteObject() {}

/** @type {string} */
GearsCompleteObject.prototype.newVersion = '';


/** @constructor */
function GearsErrorObject() {}

/** @type {string} */
GearsErrorObject.prototype.message = '';

/** @type {number} */
GearsErrorObject.prototype.lineNumber;


/** @constructor */
function GearsProgressObject() {}

/** @type {number} */
GearsProgressObject.prototype.filesComplete = 0;

/** @type {number} */
GearsProgressObject.prototype.filesTotal = 0;
