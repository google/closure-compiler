/*
 * Copyright 2021 The Closure Compiler Authors
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
 * @fileoverview APIs used for file/directory upload by drag-and-drop
 * @see https://wicg.github.io/entries-api
 * @see https://github.com/WICG/entries-api/blob/main/EXPLAINER.md
 * @externs
 */

/**
 * @see https://wicg.github.io/entries-api/#filesystementry
 * @constructor
 */
function FileSystemEntry() {}

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystementry-isfile
 * @type {boolean}
 */
FileSystemEntry.prototype.isFile;

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystementry-isdirectory
 * @type {boolean}
 */
FileSystemEntry.prototype.isDirectory;

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystementry-name
 * @type {string}
 */
FileSystemEntry.prototype.name;

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystementry-fullpath
 * @type {string}
 */
FileSystemEntry.prototype.fullPath;

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystementry-filesystem
 * @type {!FileSystem}
 */
FileSystemEntry.prototype.filesystem;

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystementry-getparent
 * @param {function(!FileSystemEntry)=} successCallback
 * @param {function(!DOMException)=} errorCallback
 * @return {undefined}
 */
FileSystemEntry.prototype.getParent = function(
    successCallback, errorCallback) {};

/**
 * @see https://wicg.github.io/entries-api/#filesystemdirectoryentry
 * @constructor
 * @extends {FileSystemEntry}
 */
function FileSystemDirectoryEntry() {}

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystemdirectoryentry-createreader
 * @return {!FileSystemDirectoryReader}
 */
FileSystemDirectoryEntry.prototype.createReader = function() {};

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystemdirectoryentry-getfile
 * @param {string|null=} path
 * @param {!FileSystemFlags=} options
 * @param {function(!FileSystemFileEntry)=} successCallback
 * @param {function(!DOMException)=} errorCallback
 * @return {undefined}
 */
FileSystemDirectoryEntry.prototype.getFile = function(
    path, options, successCallback, errorCallback) {};

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystemdirectoryentry-getdirectory
 * @param {string|null=} path
 * @param {!FileSystemFlags=} options
 * @param {function(!FileSystemDirectoryEntry)=} successCallback
 * @param {function(!DOMException)=} errorCallback
 * @return {undefined}
 */
FileSystemDirectoryEntry.prototype.getDirectory = function(
    path, options, successCallback, errorCallback) {};

/**
 * @see https://wicg.github.io/entries-api/#dictdef-filesystemflags
 * @record
 */
function FileSystemFlags() {}

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystemflags-create
 * @type {(undefined|boolean)}
 */
FileSystemFlags.prototype.create;

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystemflags-exclusive
 * @type {(undefined|boolean)}
 */
FileSystemFlags.prototype.exclusive;

/**
 * @see https://wicg.github.io/entries-api/#filesystemdirectoryreader
 * @constructor
 */
function FileSystemDirectoryReader() {}

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystemdirectoryentry-readentries
 * @param {function(!Array<FileSystemEntry>)} successCallback
 * @param {function(!DOMException)=} errorCallback
 * @return {undefined}
 */
FileSystemDirectoryReader.prototype.readEntries = function(
    successCallback, errorCallback) {};

/**
 * @see https://wicg.github.io/entries-api/#filesystemfileentry
 * @constructor
 * @extends {FileSystemEntry}
 */
function FileSystemFileEntry() {}

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystemfileentry-file
 * @param {function(!File)} successCallback
 * @param {function(!DOMException)=} errorCallback
 * @return {undefined}
 */
FileSystemFileEntry.prototype.file = function(
    successCallback, errorCallback) {};

/**
 * @see https://wicg.github.io/entries-api/#filesystem
 * @constructor
 */
function FileSystem() {}

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystem-name
 * @type {string}
 */
FileSystem.prototype.name;

/**
 * @see https://wicg.github.io/entries-api/#dom-filesystem-root
 * @type {!FileSystemDirectoryEntry}
 */
FileSystem.prototype.root;
