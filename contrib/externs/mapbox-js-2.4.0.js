/**
 * Copyright 2016 Anomaly Software Pty Ltd.
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
 * @fileoverview Externs for Mapbox.js
 *
 * Currently only selected constructors and functions have been mapped.
 * 
 * @see https://www.mapbox.com/mapbox.js/api
 * @externs
 */

/** @const */
var L = {};

/** @const */
L.mapbox = {};

/**
 * @param {string} idOrUrl
 * @param {Object} options
 */
L.mapbox.geocoder = function(idOrUrl, options) { };

/**
 * @param {string|Object} queryStringOrOptions
 * @param {Function} callback
 */
L.mapbox.geocoder.query = function(queryStringOrOptions, callback) { };