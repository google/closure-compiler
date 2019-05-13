/*
 * Copyright 2018 The Closure Compiler Authors.
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
 * @fileoverview GeoJSON externs
 *
 * @see http://geojson.org/
 * @see https://tools.ietf.org/html/rfc7946
 *
 * To use the externs, add a dependency on either the generated js_library or
 * the implicit file target:
 *
 *   //third_party/java_src/jscomp/contrib/externs:geojson
 *   //third_party/java_src/jscomp/contrib/externs:geojson.js
 *
 * @externs
 */


/**
 * Common ancestor for all GeoJSON objects.
 * @record
 */
var GeoJSON = function() {};

/**
 * @type {string}
 */
GeoJSON.prototype.type;

/**
 * Coordinate reference system.
 * @type {?GeoJSON.NamedCRS | GeoJSON.LinkedCRS | undefined}
 * @see https://tools.ietf.org/html/rfc7946#section-4
 */
GeoJSON.prototype.crs;

/**
 * @type {?Array<number> | undefined}
 * @see https://tools.ietf.org/html/rfc7946#section-5
 */
GeoJSON.prototype.bbox;


/**
 * @record
 */
GeoJSON.NamedCRS = function() {};

/**
 * @type {string}
 */
GeoJSON.NamedCRS.prototype.type;

/**
 * @type {{name: string}}
 */
GeoJSON.NamedCRS.prototype.properties;


/**
 * @record
 */
GeoJSON.LinkedCRS = function() {};

/**
 * @type {string}
 */
GeoJSON.LinkedCRS.prototype.type;

/**
 * @type {{href: string, type: (string | undefined)}}
 */
GeoJSON.LinkedCRS.prototype.properties;


/**
 * @record
 * @extends {GeoJSON}
 * @see https://tools.ietf.org/html/rfc7946#section-3.1
 */
GeoJSON.Geometry = function() {};


/**
 * @record
 * @extends {GeoJSON.Geometry}
 * @see https://tools.ietf.org/html/rfc7946#section-3.1.2
 */
GeoJSON.Point = function() {};

/**
 * @type {!Array<number>}
 */
GeoJSON.Point.prototype.coordinates;


/**
 * @record
 * @extends {GeoJSON.Geometry}
 * @see https://tools.ietf.org/html/rfc7946#section-3.1.3
 */
GeoJSON.MultiPoint = function() {};

/**
 * @type {!Array<!Array<number>>}
 */
GeoJSON.MultiPoint.prototype.coordinates;


/**
 * @record
 * @extends {GeoJSON.Geometry}
 * @see https://tools.ietf.org/html/rfc7946#section-3.1.4
 */
GeoJSON.LineString = function() {};

/**
 * @type {!Array<!Array<number>>}
 */
GeoJSON.LineString.prototype.coordinates;


/**
 * @record
 * @extends {GeoJSON.Geometry}
 * @see https://tools.ietf.org/html/rfc7946#section-3.1.5
 */
GeoJSON.MultiLineString = function() {};

/**
 * @type {!Array<!Array<!Array<number>>>}
 */
GeoJSON.MultiLineString.prototype.coordinates;


/**
 * @record
 * @extends {GeoJSON.Geometry}
 * @see https://tools.ietf.org/html/rfc7946#section-3.1.6
 */
GeoJSON.Polygon = function() {};

/**
 * @type {!Array<!Array<!Array<number>>>}
 */
GeoJSON.Polygon.prototype.coordinates;


/**
 * @record
 * @extends {GeoJSON.Geometry}
 * @see https://tools.ietf.org/html/rfc7946#section-3.1.7
 */
GeoJSON.MultiPolygon = function() {};

/**
 * @type {!Array<!Array<!Array<!Array<number>>>>}
 */
GeoJSON.MultiPolygon.prototype.coordinates;


/**
 * @record
 * @extends {GeoJSON.Geometry}
 * @see https://tools.ietf.org/html/rfc7946#section-3.1.8
 */
GeoJSON.GeometryCollection = function() {};

/**
 * @type {!Array<!GeoJSON.Geometry>}
 */
GeoJSON.GeometryCollection.prototype.geometries;


/**
 * @record
 * @extends {GeoJSON}
 * @see https://tools.ietf.org/html/rfc7946#section-3.2
 */
GeoJSON.Feature = function() {};

/**
 * @type {?string}
 */
GeoJSON.Feature.prototype.id;

/**
 * @type {?GeoJSON.Geometry}
 */
GeoJSON.Feature.prototype.geometry;

/**
 * @type {?Object}
 */
GeoJSON.Feature.prototype.properties;


/**
 * @record
 * @extends {GeoJSON}
 * @see https://tools.ietf.org/html/rfc7946#section-3.3
 */
GeoJSON.FeatureCollection = function() {};

/**
 * @type {!Array<!GeoJSON.Feature>}
 */
GeoJSON.FeatureCollection.prototype.features;
