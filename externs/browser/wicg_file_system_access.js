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
 * File System Access
 * Draft Community Group Report, 12 January 2023
 * @see https://wicg.github.io/file-system-access/
 * @externs
 */



/**
 * @typedef {string}
 * @see https://wicg.github.io/file-system-access/#enumdef-filesystempermissionmode
 */
var FileSystemPermissionMode;


/**
 * @record
 * @struct
 * @extends {PermissionDescriptor}
 * @see https://wicg.github.io/file-system-access/#dictdef-filesystempermissiondescriptor
 */
var FileSystemPermissionDescriptor = function() {};

/** @type {!FileSystemHandle} */
FileSystemPermissionDescriptor.prototype.handle;

/** @type {undefined|!FileSystemPermissionMode} */
FileSystemPermissionDescriptor.prototype.mode;


/**
 * @record
 * @struct
 * @see https://wicg.github.io/file-system-access/#dictdef-filesystemhandlepermissiondescriptor
 */
var FileSystemHandlePermissionDescriptor = function() {};

/** @type {undefined|!FileSystemPermissionMode} */
FileSystemHandlePermissionDescriptor.prototype.mode;


/**
 * @param {!FileSystemHandlePermissionDescriptor=} opt_descriptor
 * @return {!Promise<!PermissionState>}
 * @see https://wicg.github.io/file-system-access/#api-filesystemhandle
 */
FileSystemHandle.prototype.queryPermission = function(opt_descriptor) {};

/**
 * @param {!FileSystemHandlePermissionDescriptor=} opt_descriptor
 * @return {!Promise<!PermissionState>}
 * @see https://wicg.github.io/file-system-access/#api-filesystemhandle
 */
FileSystemHandle.prototype.requestPermission = function(opt_descriptor) {};


/**
 * @typedef {string}
 * @see https://wicg.github.io/file-system-access/#local-filesystem
 */
var WellKnownDirectory;


/**
 * @typedef {!WellKnownDirectory|!FileSystemHandle}
 * @see https://wicg.github.io/file-system-access/#local-filesystem
 */
var StartInDirectory;


/**
 * @record
 * @struct
 * @see https://wicg.github.io/file-system-access/#dictdef-filepickeraccepttype
 */
var FilePickerAcceptType = function() {};

/** @type {undefined|string} */
FilePickerAcceptType.prototype.description;

/** @type {undefined|!Object<string,(string|!Array<string>)>} */
FilePickerAcceptType.prototype.accept;


/**
 * @record
 * @struct
 * @see https://wicg.github.io/file-system-access/#dictdef-filepickeroptions
 */
var FilePickerOptions = function() {};

/** @type {undefined|!Array<!FilePickerAcceptType>} */
FilePickerOptions.prototype.types;

/** @type {undefined|boolean} */
FilePickerOptions.prototype.excludeAcceptAllOption;

/** @type {undefined|string} */
FilePickerOptions.prototype.id;

/** @type {undefined|!StartInDirectory} */
FilePickerOptions.prototype.startIn;


/**
 * @record
 * @struct
 * @extends {FilePickerOptions}
 * @see https://wicg.github.io/file-system-access/#dictdef-openfilepickeroptions
 */
var OpenFilePickerOptions = function() {};

/** @type {undefined|boolean} */
OpenFilePickerOptions.prototype.multiple;


/**
 * @record
 * @struct
 * @extends {FilePickerOptions}
 * @see https://wicg.github.io/file-system-access/#dictdef-savefilepickeroptions
 */
var SaveFilePickerOptions = function() {};

/** @type {undefined|?string} */
SaveFilePickerOptions.prototype.suggestedName;


/**
 * @record
 * @struct
 * @see https://wicg.github.io/file-system-access/#dictdef-directorypickeroptions
 */
var DirectoryPickerOptions = function() {};

/** @type {undefined|string} */
DirectoryPickerOptions.prototype.id;

/** @type {undefined|!StartInDirectory} */
DirectoryPickerOptions.prototype.startIn;

/** @type {undefined|!FileSystemPermissionMode} */
DirectoryPickerOptions.prototype.mode;


/**
 * @param {!OpenFilePickerOptions=} opt_options
 * @return {!Promise<!Array<!FileSystemFileHandle>>}
 * @see https://wicg.github.io/file-system-access/#local-filesystem
 */
Window.prototype.showOpenFilePicker = function(opt_options) {};


/**
 * @param {!SaveFilePickerOptions=} opt_options
 * @return {!Promise<!FileSystemFileHandle>}
 * @see https://wicg.github.io/file-system-access/#local-filesystem
 */
Window.prototype.showSaveFilePicker = function(opt_options) {};


/**
 * @param {!DirectoryPickerOptions=} opt_options
 * @return {!Promise<!FileSystemDirectoryHandle>}
 * @see https://wicg.github.io/file-system-access/#local-filesystem
 */
Window.prototype.showDirectoryPicker = function(opt_options) {};


/**
 * @return {!Promise<?FileSystemHandle>}
 * @see https://wicg.github.io/file-system-access/#drag-and-drop
 */
DataTransferItem.prototype.getAsFileSystemHandle = function() {};
