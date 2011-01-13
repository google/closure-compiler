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
 * @fileoverview Definitions for objects in the File API, File Writer API, and
 * File System API. Details of the API are at:
 * http://www.w3.org/TR/FileAPI/
 * http://www.w3.org/TR/file-writer-api/
 * http://www.w3.org/TR/file-system-api/
 *
 * @externs
 * @author dbk@google.com (David Barrett-Kahn)
 * @author mpd@google.com (Michael Davidson)
 */


/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-Blob
 * @constructor
 */
function Blob() {}

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-size
 * @type {number}
 */
Blob.prototype.size;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-type
 * @type {string}
 */
Blob.prototype.type;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-slice
 * @param {number} start
 * @param {number} length
 * @return {Blob}
 */
Blob.prototype.slice = function(start, length) {};

/**
 * @see http://www.w3.org/TR/file-writer-api/#the-blobbuilder-interface
 * @constructor
 */
function BlobBuilder() {}

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-BlobBuilder-getBlob
 * @param {string=} contentType
 * @return {!Blob}
 */
BlobBuilder.prototype.getBlob = function(contentType) {};

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-BlobBuilder-append0
 * @see http://www.w3.org/TR/file-writer-api/#widl-BlobBuilder-append1
 * TODO(dbk): Add http://www.w3.org/TR/file-writer-api/#widl-BlobBuilder-append1,
 *     which involves adding ArrayBuffer.
 * @param {string|Blob} data
 * @param {string=} endings
 */
BlobBuilder.prototype.append = function(data, endings) {};

/**
 * @see http://www.w3.org/TR/file-system-api/#the-directoryentry-interface
 * TODO(dbk): Add http://www.w3.org/TR/file-system-api/#widl-DirectoryEntry-createReader
 * @constructor
 * @extends {Entry}
 */
function DirectoryEntry() {};

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-DirectoryEntry-getFile
 * @param {string} path
 * @param {Object=} options
 * @param {function(!FileEntry)=} successCallback
 * @param {function(!FileError)=} errorCallback
 */
DirectoryEntry.prototype.getFile = function(path, options, successCallback,
    errorCallback) {};

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-DirectoryEntry-getDirectory
 * @param {string} path
 * @param {Object=} options
 * @param {function(!DirectoryEntry)=} successCallback
 * @param {function(!FileError)=} errorCallback
 */
DirectoryEntry.prototype.getDirectory = function(path, options, successCallback,
    errorCallback) {};

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-DirectoryEntry-removeRecursively
 * @param {function()} successCallback
 * @param {function(!FileError)=} errorCallback
 */
DirectoryEntry.prototype.removeRecursively = function(successCallback,
    errorCallback) {};

/**
 * @see http://www.w3.org/TR/file-system-api/#the-entry-interface
 * TODO(dbk): add http://www.w3.org/TR/file-system-api/#widl-Entry-getMetadata
 * @constructor
 */
function Entry() {};

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-Entry-isFile
 * @return {boolean}
 */
Entry.prototype.isFile = function() {};

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-Entry-isDirectory
 * @return {boolean}
 */
Entry.prototype.isDirectory = function() {};

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-Entry-name
 * @type {string}
 */
Entry.prototype.name;

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-Entry-fullPath
 * @type {string}
 */
Entry.prototype.fullPath;

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-Entry-filesystem
 * @type {!FileSystem}
 */
Entry.prototype.filesystem;

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-Entry-moveTo
 * @param {!DirectoryEntry} parent
 * @param {string=} newName
 * @param {function(!Entry)=} successCallback
 * @param {function(!FileError)=} errorCallback
 */
Entry.prototype.moveTo = function(parent, newName, successCallback,
    errorCallback) {};

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-Entry-copyTo
 * @param {!DirectoryEntry} parent
 * @param {string=} newName
 * @param {function(!Entry)=} successCallback
 * @param {function(!FileError)=} errorCallback
 */
Entry.prototype.copyTo = function(parent, newName, successCallback,
    errorCallback) {};

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-Entry-toURI
 * @param {string=} mimeType
 * @return {string}
 */
Entry.prototype.toURI = function(mimeType) {};

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-Entry-remove
 * @param {function()} successCallback
 * @param {function(!FileError)=} errorCallback
 */
Entry.prototype.remove = function(successCallback, errorCallback) {};

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-Entry-getParent
 * @param {function(!Entry)} successCallback
 * @param {function(!FileError)=} errorCallback
 */
Entry.prototype.getParent = function(successCallback, errorCallback) {};

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-file
 * @constructor
 * @extends {Blob}
 */
function File() {}

/**
 * Chrome uses this instead of name.
 * @deprecated Use name instead.
 * @type {string}
 */
File.prototype.fileName;

/**
 * Chrome uses this instead of size.
 * @deprecated Use size instead.
 * @type {string}
 */
File.prototype.fileSize;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-name
 * @type {string}
 */
File.prototype.name;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-lastModifiedDate
 * @type {string}
 */
File.prototype.lastModifiedDate;

/**
 * @see http://www.w3.org/TR/file-system-api/#the-fileentry-interface
 * @constructor
 * @extends {Entry}
 */
function FileEntry() {};

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-FileEntry-createWriter
 * @param {function(!FileWriter)} successCallback
 * @param {function(!FileError)=} errorCallback
 */
FileEntry.prototype.createWriter = function(successCallback, errorCallback) {};

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-FileEntry-file
 * @param {function(!File)} successCallback
 * @param {function(!FileError)=} errorCallback
 */
FileEntry.prototype.file = function(successCallback, errorCallback) {};

/**
 * @see http://www.w3.org/TR/FileAPI/#FileErrorInterface
 * @constructor
 */
function FileError() {}

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-NOT_FOUND_ERR
 * @type {number}
 */
FileError.prototype.NOT_FOUND_ERR = 1;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-SECURITY_ERR
 * @type {number}
 */
FileError.prototype.SECURITY_ERR = 2;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-ABORT_ERR
 * @type {number}
 */
FileError.prototype.ABORT_ERR = 3;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-NOT_READABLE_ERR
 * @type {number}
 */
FileError.prototype.NOT_READABLE_ERR = 4;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-ENCODING_ERR
 * @type {number}
 */
FileError.prototype.ENCODING_ERR = 5;

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileError-NO_MODIFICATION_ALLOWED_ERR
 * @type {number}
 */
FileError.prototype.NO_MODIFICATION_ALLOWED_ERR = 6;

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileException-INVALID_STATE_ERR
 * @type {number}
 */
FileError.prototype.INVALID_STATE_ERR = 7;

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileException-SYNTAX_ERR
 * @type {number}
 */
FileError.prototype.SYNTAX_ERR = 8;

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-FileError-INVALID_MODIFICATION_ERR
 * @type {number}
 */
FileError.prototype.INVALID_MODIFICATION_ERR = 9;

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-FileError-QUOTA_EXCEEDED_ERR
 * @type {number}
 */
FileError.prototype.QUOTA_EXCEEDED_ERR = 10;

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-FileException-TYPE_MISMATCH_ERR
 * @type {number}
 */
FileError.prototype.TYPE_MISMATCH_ERR = 11;

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-FileException-PATH_EXISTS_ERR
 * @type {number}
 */
FileError.prototype.PATH_EXISTS_ERR = 12;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-code-exception
 * @type {number}
 */
FileError.prototype.code;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-filereader
 * @constructor
 * @implements {EventTarget}
 */
function FileReader() {}

/** @inheritDoc */
FileReader.prototype.addEventListener = function(type, listener, useCapture) {};

/** @inheritDoc */
FileReader.prototype.removeEventListener = function(type, listener, useCapture)
    {};

/** @inheritDoc */
FileReader.prototype.dispatchEvent = function(evt) {};

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-readAsArrayBuffer
 * @param {!Blob} blob
 */
FileReader.prototype.readAsArrayBuffer = function(blob) {};

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-readAsBinaryStringAsync
 * @param {!Blob} blob
 */
FileReader.prototype.readAsBinaryString = function(blob) {};

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-readAsText
 * @param {!Blob} blob
 * @param {string=} encoding
 */
FileReader.prototype.readAsText = function(blob, encoding) {};

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-readAsDataURL
 * @param {!Blob} blob
 */
FileReader.prototype.readAsDataURL = function(blob) {};

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
 * @see http://www.w3.org/TR/FileAPI/#dfn-readystate
 * @type {number}
 */
FileReader.prototype.readyState;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-result
 * @type {string|Blob}
 */
FileReader.prototype.result;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-error
 * @type {FileError}
 */
FileReader.prototype.error;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-onloadstart
 * @type {?function(!ProgressEvent)}
 */
FileReader.prototype.onloadstart;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-onprogress
 * @type {?function(!ProgressEvent)}
 */
FileReader.prototype.onprogress;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-onload
 * @type {?function(!ProgressEvent)}
 */
FileReader.prototype.onload;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-onabort
 * @type {?function(!ProgressEvent)}
 */
FileReader.prototype.onabort;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-onerror
 * @type {?function(!ProgressEvent)}
 */
FileReader.prototype.onerror;

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-onloadend
 * @type {?function(!ProgressEvent)}
 */
FileReader.prototype.onloadend;

/**
 * @see http://www.w3.org/TR/file-writer-api/#idl-def-FileSaver
 * @constructor
 */
function FileSaver() {};

/** @see http://www.w3.org/TR/file-writer-api/#widl-FileSaver-abort */
FileSaver.prototype.abort = function() {};

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileSaver-INIT
 * @type {number}
 */
FileSaver.prototype.INIT = 0;

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileSaver-WRITING
 * @type {number}
 */
FileSaver.prototype.WRITING = 1;

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileSaver-DONE
 * @type {number}
 */
FileSaver.prototype.DONE = 2;

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileSaver-readyState
 * @type {number}
 */
FileSaver.prototype.readyState;

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileSaver-error
 * @type {FileError}
 */
FileSaver.prototype.error;

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileSaver-onwritestart
 * @type {?function(!ProgressEvent)}
 */
FileSaver.prototype.onwritestart;

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileSaver-onprogress
 * @type {?function(!ProgressEvent)}
 */
FileSaver.prototype.onprogress;

/** @see http://www.w3.org/TR/file-writer-api/#widl-FileSaver-onwrite
 * @type {?function(!ProgressEvent)}
 */
FileSaver.prototype.onwrite;

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileSaver-onabort
 * @type {?function(!ProgressEvent)}
 */
FileSaver.prototype.onabort;

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileSaver-onerror
 * @type {?function(!ProgressEvent)}
 */
FileSaver.prototype.onerror;

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileSaver-onwriteend
 * @type {?function(!ProgressEvent)}
 */
FileSaver.prototype.onwriteend;

/**
 * @see http://www.w3.org/TR/file-system-api/#the-filesystem-interface
 * @constructor
 */
function FileSystem() {}

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-FileSystem-name
 * @type {string}
 */
FileSystem.prototype.name;

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-FileSystem-root
 * @type {!DirectoryEntry}
 */
FileSystem.prototype.root;

/**
 * @see http://www.w3.org/TR/file-writer-api/#idl-def-FileWriter
 * @constructor
 * @extends {FileSaver}
 */
function FileWriter() {}

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileWriter-position
 * @type {number}
 */
FileWriter.prototype.position;

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileWriter-length
 * @type {number}
 */
FileWriter.prototype.length;

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileWriter-write
 * @param {!Blob} blob
 */
FileWriter.prototype.write = function(blob) {};

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileWriter-seek
 * @param {number} offset
 */
FileWriter.prototype.seek = function(offset) {};

/**
 * @see http://www.w3.org/TR/file-writer-api/#widl-FileWriter-truncate
 * @param {number} size
 */
FileWriter.prototype.truncate = function(size) {};

/**
 * LocalFileSystem interface, implemented by Window and WorkerGlobalScope.
 * @see http://www.w3.org/TR/file-system-api/#idl-def-LocalFileSystem
 * @constructor
 */
function LocalFileSystem() {}

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-LocalFileSystem-TEMPORARY
 * @type {number}
*/
Window.prototype.TEMPORARY = 0;

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-LocalFileSystem-PERSISTENT
 * @type {number}
*/
Window.prototype.PERSISTENT = 1;

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-LocalFileSystem-requestFileSystem
 * @param {number} type
 * @param {number} size
 * @param {function(!FileSystem)} successCallback
 * @param {function(!FileError)=} errorCallback
 */
function requestFileSystem(type, size, successCallback, errorCallback) {}

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-LocalFileSystem-requestFileSystem
 * @param {number} type
 * @param {number} size
 * @param {function(!FileSystem)} successCallback
 * @param {function(!FileError)=} errorCallback
 */
Window.prototype.requestFileSystem = function(type, size, successCallback,
    errorCallback) {};

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-LocalFileSystem-resolveLocalFileSystemURI
 * @param {string} uri
 * @param {function(!Entry)} successCallback
 * @param {function(!FileError)=} errorCallback
 */
function resolveLocalFileSystemURI(uri, successCallback, errorCallback) {}

/**
 * @see http://www.w3.org/TR/file-system-api/#widl-LocalFileSystem-resolveLocalFileSystemURI
 * @param {string} uri
 * @param {function(!Entry)} successCallback
 * @param {function(!FileError)=} errorCallback
 */
Window.prototype.resolveLocalFileSystemURI = function(uri, successCallback,
    errorCallback) {}

// WindowBlobURIMethods interface, implemented by Window and WorkerGlobalScope
// @see http://www.w3.org/TR/FileAPI/#creating-revoking

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-createObjectURL
 * @param {!Blob} blob
 */
function createObjectURL(blob) {};

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-createObjectURL
 * @param {!Blob} blob
 */
Window.prototype.createObjectURL = function(blob) {};

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-revokeObjectURL
 * @param {string} url
 */
function revokeObjectURL(url) {};

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-revokeObjectURL
 * @param {string} url
 */
Window.prototype.revokeObjectURL = function(url) {};
