/*
 * Copyright 2020 The Closure Compiler Authors
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
 * File System
 * Living Standard — Last Updated 24 January 2023
 * @see https://fs.spec.whatwg.org/
 * @externs
 */



/**
 * @typedef {string}
 * @see https://fs.spec.whatwg.org/#enumdef-filesystemhandlekind
 */
var FileSystemHandleKind;


/**
 * @interface
 * @see https://fs.spec.whatwg.org/#api-filesystemhandle
 */
var FileSystemHandle = function() {};

/** @const {!FileSystemHandleKind} */
FileSystemHandle.prototype.kind;

/** @const {string} */
FileSystemHandle.prototype.name;

/**
 * @param {!FileSystemHandle} other
 * @return {!Promise<boolean>}
 */
FileSystemHandle.prototype.isSameEntry = function(other) {};


/**
 * @record
 * @struct
 * @see https://fs.spec.whatwg.org/#dictdef-filesystemcreatewritableoptions
 */
var FileSystemCreateWritableOptions = function() {};

/** @type {undefined|boolean} */
FileSystemCreateWritableOptions.prototype.keepExistingData;


/**
 * @interface
 * @extends {FileSystemHandle}
 * @see https://fs.spec.whatwg.org/#filesystemfilehandle
 */
var FileSystemFileHandle = function() {};

/**
 * @return {!Promise<!File>}
 */
FileSystemFileHandle.prototype.getFile = function() {};

/**
 * @param {!FileSystemCreateWritableOptions=} opt_options
 * @return {!Promise<!FileSystemWritableFileStream>}
 */
FileSystemFileHandle.prototype.createWritable = function(opt_options) {};

/**
 * @return {!Promise<!FileSystemSyncAccessHandle>}
 */
FileSystemFileHandle.prototype.createSyncAccessHandle = function() {};


/**
 * @record
 * @struct
 * @see https://fs.spec.whatwg.org/#dictdef-filesystemgetfileoptions
 */
var FileSystemGetFileOptions = function() {};

/** @type {undefined|boolean} */
FileSystemGetFileOptions.prototype.create;


/**
 * @record
 * @struct
 * @see https://fs.spec.whatwg.org/#dictdef-filesystemgetdirectoryoptions
 */
var FileSystemGetDirectoryOptions = function() {};

/** @type {undefined|boolean} */
FileSystemGetDirectoryOptions.prototype.create;


/**
 * @record
 * @struct
 * @see https://fs.spec.whatwg.org/#dictdef-filesystemremoveoptions
 */
var FileSystemRemoveOptions = function() {};

/** @type {undefined|boolean} */
FileSystemRemoveOptions.prototype.recursive;


/**
 * @interface
 * @extends {FileSystemHandle}
 * @extends {AsyncIterable<!Array<string|!FileSystemHandle>>}
 * @see https://fs.spec.whatwg.org/#filesystemdirectoryhandle
 */
var FileSystemDirectoryHandle = function() {};

/**
 * @param {string} name
 * @param {!FileSystemGetFileOptions=} opt_options
 * @return {!Promise<!FileSystemFileHandle>}
 */
FileSystemDirectoryHandle.prototype.getFileHandle = function(name, opt_options) {};

/**
 * @param {string} name
 * @param {!FileSystemGetDirectoryOptions=} opt_options
 * @return {!Promise<!FileSystemDirectoryHandle>}
 */
FileSystemDirectoryHandle.prototype.getDirectoryHandle = function(name, opt_options) {};

/**
 * @param {string} name
 * @param {!FileSystemRemoveOptions=} opt_options
 * @return {!Promise<void>}
 */
FileSystemDirectoryHandle.prototype.removeEntry = function(name, opt_options) {};

/**
 * @param {!FileSystemHandle} possibleDescendant
 * @return {!Promise<?Array<string>>}
 */
FileSystemDirectoryHandle.prototype.resolve = function(possibleDescendant) {};

/**
 * @return {!AsyncIterable<!Array<string|!FileSystemHandle>>}
 * @see https://fs.spec.whatwg.org/#api-filesystemdirectoryhandle-asynciterable
 */
FileSystemDirectoryHandle.prototype.entries = function() {};

/**
 * @return {!AsyncIterable<!FileSystemHandle>}
 * @see https://fs.spec.whatwg.org/#api-filesystemdirectoryhandle-asynciterable
 */
FileSystemDirectoryHandle.prototype.values = function() {};

/**
 * @return {!AsyncIterable<string>}
 * @see https://fs.spec.whatwg.org/#api-filesystemdirectoryhandle-asynciterable
 */
FileSystemDirectoryHandle.prototype.keys = function() {};


/**
 * @typedef {string}
 * @see https://fs.spec.whatwg.org/#enumdef-writecommandtype
 */
var WriteCommandType;


/**
 * @record
 * @struct
 * @see https://fs.spec.whatwg.org/#dictdef-writeparams
 */
var WriteParams = function() {};

/** @type {!WriteCommandType} */
WriteParams.prototype.type;

/** @type {undefined|?number} */
WriteParams.prototype.size;

/** @type {undefined|?number} */
WriteParams.prototype.position;

/** @type {undefined|!BufferSource|!Blob|?string} */
WriteParams.prototype.data;


/**
 * @typedef {!BufferSource|!Blob|string|!WriteParams}
 * @see https://fs.spec.whatwg.org/#typedefdef-filesystemwritechunktype
 */
var FileSystemWriteChunkType;


/**
 * @constructor
 * @extends {WritableStream}
 * @see https://fs.spec.whatwg.org/#filesystemwritablefilestream
 */
var FileSystemWritableFileStream = function() {};

/**
 * @param {!FileSystemWriteChunkType} data
 * @return {!Promise<void>}
 */
FileSystemWritableFileStream.prototype.write = function(data) {};

/**
 * @param {number} position
 * @return {!Promise<void>}
 */
FileSystemWritableFileStream.prototype.seek = function(position) {};

/**
 * @param {number} size
 * @return {!Promise<void>}
 */
FileSystemWritableFileStream.prototype.truncate = function(size) {};


/**
 * @record
 * @struct
 * @see https://fs.spec.whatwg.org/#filesystemwritablefilestream
 */
var FileSystemReadWriteOptions = function() {};

/** @type {undefined|number} */
FileSystemReadWriteOptions.prototype.at;


/**
 * @interface
 * @see https://fs.spec.whatwg.org/#filesystemsyncaccesshandle
 */
var FileSystemSyncAccessHandle = function() {};

/**
 * @param {!BufferSource} buffer
 * @param {!FileSystemReadWriteOptions=} opt_options
 * @return {number}
 */
FileSystemSyncAccessHandle.prototype.read = function(buffer, opt_options) {};

/**
 * @param {!BufferSource} buffer
 * @param {!FileSystemReadWriteOptions=} opt_options
 * @return {number}
 */
FileSystemSyncAccessHandle.prototype.write = function(buffer, opt_options) {};

/**
 * @param {number} newSize
 * @return {void}
 */
FileSystemSyncAccessHandle.prototype.truncate = function(newSize) {};

/**
 * @return {number}
 */
FileSystemSyncAccessHandle.prototype.getSize = function() {};

/**
 * @return {void}
 */
FileSystemSyncAccessHandle.prototype.flush = function() {};

/**
 * @return {void}
 */
FileSystemSyncAccessHandle.prototype.close = function() {};


/**
 * @return {!Promise<!FileSystemDirectoryHandle>}
 * @see https://fs.spec.whatwg.org/#sandboxed-filesystem
 */
StorageManager.prototype.getDirectory = function() {};
