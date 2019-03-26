/*
 * Copyright 2009 The Closure Compiler Authors
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
 * @fileoverview Definitions for W3C's Geolocation specification
 *     http://www.w3.org/TR/geolocation-API/
 * @externs
 */

/**
 * @constructor
 * @see http://www.w3.org/TR/geolocation-API/#geolocation
 */
function Geolocation() {}

/**
 * @typedef {function(!Position): void}
 */
var PositionCallback;

/**
 * @typedef {function(!PositionError): void}
 */
var PositionErrorCallback;

/**
 * @param {PositionCallback} successCallback
 * @param {PositionErrorCallback=} opt_errorCallback
 * @param {PositionOptions=} opt_options
 * @return {undefined}
 */
Geolocation.prototype.getCurrentPosition = function(successCallback,
                                                       opt_errorCallback,
                                                       opt_options) {};

/**
 * @param {PositionCallback} successCallback
 * @param {PositionErrorCallback=} opt_errorCallback
 * @param {PositionOptions=} opt_options
 * @return {number}
 */
Geolocation.prototype.watchPosition = function(successCallback,
                                                  opt_errorCallback,
                                                  opt_options) {};

/**
 * @param {number} watchId
 * @return {undefined}
 */
Geolocation.prototype.clearWatch = function(watchId) {};


/**
 * @record
 * @see http://www.w3.org/TR/geolocation-API/#coordinates
 */
function Coordinates() {}
/** @type {number} */
Coordinates.prototype.latitude;
/** @type {number} */
Coordinates.prototype.longitude;
/** @type {number} */
Coordinates.prototype.accuracy;
/** @type {number} */
Coordinates.prototype.altitude;
/** @type {number} */
Coordinates.prototype.altitudeAccuracy;
/** @type {number} */
Coordinates.prototype.heading;
/** @type {number} */
Coordinates.prototype.speed;

/**
 * @record
 * @see http://www.w3.org/TR/geolocation-API/#position
 */
function Position() {}
/** @type {Coordinates} */
Position.prototype.coords;
/** @type {number} */
Position.prototype.timestamp;

/**
 * @record
 * @see http://www.w3.org/TR/geolocation-API/#position-options
 */
function PositionOptions() {}
/** @type {boolean|undefined} */
PositionOptions.prototype.enableHighAccuracy;
/** @type {number|undefined} */
PositionOptions.prototype.maximumAge;
/** @type {number|undefined} */
PositionOptions.prototype.timeout;

/**
 * @record
 * @see http://www.w3.org/TR/geolocation-API/#position-error
 */
function PositionError() {}
/** @type {number} */
PositionError.prototype.code;
/** @type {string} */
PositionError.prototype.message;
/** @const {number} */
PositionError.UNKNOWN_ERROR;
/** @const {number} */
PositionError.PERMISSION_DENIED;
/** @const {number} */
PositionError.POSITION_UNAVAILABLE;
/** @const {number} */
PositionError.TIMEOUT;

/** @type {Geolocation} */
Navigator.prototype.geolocation;
