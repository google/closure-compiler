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
 * @externs
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

/**
 * @see http://www.w3.org/TR/FileAPI/#FileErrorInterface
 * @constructor
 */
function FileError() {}

/**
 * @see http://www.w3.org/TR/FileAPI/#FileErrorInterface
 * @type {number}
 */
FileError.prototype.NOT_FOUND_ERR = 8;

/**
 * @see http://www.w3.org/TR/FileAPI/#FileErrorInterface
 * @type {number}
 */
FileError.prototype.SECURITY_ERR = 18;

/**
 * @see http://www.w3.org/TR/FileAPI/#FileErrorInterface
 * @type {number}
 */
FileError.prototype.ABORT_ERR = 20;

/**
 * @see http://www.w3.org/TR/FileAPI/#FileErrorInterface
 * @type {number}
 */
FileError.prototype.NOT_READABLE_ERR = 24;

/**
 * @see http://www.w3.org/TR/FileAPI/#FileErrorInterface
 * @type {number}
 */
FileError.prototype.ENCODING_ERR = 26;

/**
 * @see http://www.w3.org/TR/FileAPI/#FileErrorInterface
 * @type {number}
 */
FileError.prototype.code;

/**
 * @see http://www.w3.org/TR/FileAPI/#filereader-interface
 * @constructor
 */
function FileReader() {}

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-readAsBinary
 * @param {File} file
 */
FileReader.prototype.readAsBinaryString = function(file) {};

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-readAsText
 * @param {File} file
 * @param {string=} opt_encoding
 */
FileReader.prototype.readAsText = function(file, opt_encoding) {};

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-readAsDataURL
 * @param {File} file
 */
FileReader.prototype.readAsDataURL = function(file) {};

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-abort
 */
FileReader.prototype.abort = function() {};

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-empty
 * @type {number}
 */
FileReader.prototype.EMPTY = 0;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-loading
 * @type {number}
 */
FileReader.prototype.LOADING = 1;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-done
 * @type {number}
 */
FileReader.prototype.DONE = 2;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-done
 * @type {number}
 */
FileReader.prototype.readyState;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-result
 * @type {string}
 */
FileReader.prototype.result;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-error
 * @type {FileError}
 */
FileReader.prototype.error;

/** @type {?function (Event)} */ FileReader.prototype.onloadstart;
/** @type {?function (Event)} */ FileReader.prototype.onprogress;
/** @type {?function (Event)} */ FileReader.prototype.onload;
/** @type {?function (Event)} */ FileReader.prototype.onabort;
/** @type {?function (Event)} */ FileReader.prototype.onerror;
/** @type {?function (Event)} */ FileReader.prototype.onloadend;
