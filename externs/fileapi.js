/*
 * Copyright 2010 Google Inc.
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
 * @fileoverview Definitions for objects in the File API. Details of the API are
 * at http://dev.w3.org/2006/webapi/FileAPI/
 *
*
 */


/**
 * @see http://dev.w3.org/2006/webapi/FileAPI/
 * @constructor
 */
function Blob() {}

/**
 * Size of the blob.
 * @type {number}
 */
Blob.prototype.size;

/**
 * Returns a new Blob containing the range of data.
 * @param {number} start The beginning of the slice.
 * @param {number} length The length of the slice.
 * @return {Blob} A new Blob object.
 */
Blob.prototype.slice = function(start, length) {};

/**
 * @see http://dev.w3.org/2006/webapi/FileAPI/
 * @constructor
 * @extends {Blob}
 */
function File() {}

/**
 * Chrome uses this instead of name.
 * @type {string}
 */
File.prototype.fileName;

/**
 * Chrome uses this instead of size.
 * @type {string}
 */
File.prototype.fileSize;

/**
 * The name of the File.
 * @type {string}
 */
File.prototype.name;

/**
 * The MIME type of the File.
 * @type {string}
 */
File.prototype.type;

/**
 * A URN that can be used to reference the File.
 * @type {string}
 */
File.prototype.urn;
