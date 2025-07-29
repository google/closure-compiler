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
 * Document Picture-in-Picture Specification
 * Draft Community Group Report, 1 October 2024
 * @see https://wicg.github.io/document-picture-in-picture/
 * @externs
 */



/**
 * @const {!DocumentPictureInPicture}
 * @see https://wicg.github.io/document-picture-in-picture/#idl-index
 */
Window.prototype.documentPictureInPicture;

/**
 * @type {!DocumentPictureInPicture}
 * @see https://wicg.github.io/document-picture-in-picture/#idl-index
 */
var documentPictureInPicture;


/**
 * @interface
 * @extends {EventTarget}
 * @see https://wicg.github.io/document-picture-in-picture/#documentpictureinpicture
 */
function DocumentPictureInPicture() {};

/** @type {undefined|?function(!DocumentPictureInPictureEvent)} */
DocumentPictureInPicture.prototype.onenter;

/**
 * @param {!DocumentPictureInPictureOptions=} opt_options
 * @return {!Promise<!Window>}
 */
DocumentPictureInPicture.prototype.requestWindow = function(opt_options) {};

/** @const {!Window} */
DocumentPictureInPicture.prototype.window;


/**
 * @record
 * @struct
 * @see https://wicg.github.io/document-picture-in-picture/#dictdef-documentpictureinpictureoptions
 */
function DocumentPictureInPictureOptions() {};

/** @type {undefined|boolean} */
DocumentPictureInPictureOptions.prototype.disallowReturnToOpener;

/** @type {undefined|number} */
DocumentPictureInPictureOptions.prototype.height;

/** @type {undefined|boolean} */
DocumentPictureInPictureOptions.prototype.preferInitialWindowPlacement;

/** @type {undefined|number} */
DocumentPictureInPictureOptions.prototype.width;


/**
 * @param {string} type
 * @param {?DocumentPictureInPictureEventInit=} opt_eventInitDict
 * @constructor
 * @extends {Event}
 * @see https://wicg.github.io/document-picture-in-picture/#documentpictureinpictureevent
 */
function DocumentPictureInPictureEvent(type, opt_eventInitDict) {};

/** @const {!Window} */
DocumentPictureInPictureEvent.prototype.window;


/**
 * @record
 * @extends {EventInit}
 * @see https://wicg.github.io/document-picture-in-picture/#dictdef-documentpictureinpictureeventinit
 */
function DocumentPictureInPictureEventInit() {};

/** @type {!Window} */
DocumentPictureInPictureEventInit.prototype.window;
