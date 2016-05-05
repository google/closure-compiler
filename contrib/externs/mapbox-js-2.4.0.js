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

/**
 * @param {number} lat
 * @param {number} lng
 * @param {number=} alt
 */
L.latLng = function(lat, lng, alt) { };

/**
 * @param latLng
 * @return {!number}
 */
L.latLng.distanceTo = function(latLng) { };

/**
 * @param {Array|Object} latlng
 * @param {Object=} options
 */
L.marker = function(latlng, options) { };

L.marker.getLatLng = function() { };

/**
 * @param map
 */
L.marker.addTo = function(map) { };

/** @const */
L.mapbox = {};

/**
 * @param {string} idOrUrl
 * @param {Object=} options
 */
L.mapbox.geocoder = function(idOrUrl, options) { };

/**
 * @param {string|Object} queryStringOrOptions
 * @param {Function} callback
 */
L.mapbox.geocoder.query = function(queryStringOrOptions, callback) { };

/**
 * @param {Array|Object} location
 * @param {Function} callback
 */
L.mapbox.geocoder.reverseQuery = function(location, callback) { };

/**
 * @param {string|Element} element
 * @param {string} idOrUrl
 */
L.mapbox.map = function(element, idOrUrl) { };

/** @const */
L.mapbox.scrollWheelZoom = {};

L.mapbox.scrollWheelZoom.disable = { };

/**
 * @param layer 
 */
L.mapbox.map.removeLayer = function(layer) { };

/**
 * @param center
 * @param {number=} zoom
 * @param {Object=} options
 */
L.mapbox.map.setView = function(center, zoom, options) { };

/** @const */
L.mapbox.marker = {};

/**
 * @param {Object} feature
 */
L.mapbox.marker.icon = function(feature) { };