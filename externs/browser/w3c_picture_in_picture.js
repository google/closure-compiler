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
 * Picture-in-Picture
 * Editor’s Draft, 16 December 2024
 * @see https://w3c.github.io/picture-in-picture/
 * @externs
 */



/**
 * @type {boolean}
 * @see https://w3c.github.io/picture-in-picture/#dom-htmlvideoelement-disablepictureinpicture
 */
HTMLVideoElement.prototype.disablePictureInPicture;

/**
 * @type {undefined|?function(!PictureInPictureEvent)}
 * @see https://w3c.github.io/picture-in-picture/#dom-htmlvideoelement-onenterpictureinpicture
 */
HTMLVideoElement.prototype.onenterpictureinpicture;

/**
 * @type {undefined|?function(!PictureInPictureEvent)}
 * @see https://w3c.github.io/picture-in-picture/#dom-htmlvideoelement-onleavepictureinpicture
 */
HTMLVideoElement.prototype.onleavepictureinpicture;

/**
 * @return {!Promise<!PictureInPictureWindow>}
 * @see https://w3c.github.io/picture-in-picture/#dom-htmlvideoelement-requestpictureinpicture
 */
HTMLVideoElement.prototype.requestPictureInPicture = function() {};


/**
 * @return {!Promise<void>}
 * @see https://w3c.github.io/picture-in-picture/#dom-document-exitpictureinpicture
 */
Document.prototype.exitPictureInPicture = function() {};

/**
 * @const {?Element}
 * @see https://w3c.github.io/picture-in-picture/#dom-documentorshadowroot-pictureinpictureelement
 */
Document.prototype.pictureInPictureElement;

/**
 * @const {boolean}
 * @see https://w3c.github.io/picture-in-picture/#dom-document-pictureinpictureenabled
 */
Document.prototype.pictureInPictureEnabled;


/**
 * @const {?Element}
 * @see https://w3c.github.io/picture-in-picture/#dom-documentorshadowroot-pictureinpictureelement
 */
ShadowRoot.prototype.pictureInPictureElement;


/**
 * @interface
 * @extends {EventTarget}
 * @see https://w3c.github.io/picture-in-picture/#interface-picture-in-picture-window
 */
function PictureInPictureWindow() {};

/** @const {number} */
PictureInPictureWindow.prototype.height;

/** @type {undefined|?function(!Event)} */
PictureInPictureWindow.prototype.onresize;

/** @const {number} */
PictureInPictureWindow.prototype.width;


/**
 * @param {string} type
 * @param {?PictureInPictureEventInit=} opt_eventInitDict
 * @constructor
 * @extends {Event}
 * @see https://w3c.github.io/picture-in-picture/#pictureinpictureevent
 */
function PictureInPictureEvent(type, opt_eventInitDict) {};

/** @const {!PictureInPictureWindow} */
PictureInPictureEvent.prototype.pictureInPictureWindow;


/**
 * @record
 * @extends {EventInit}
 * @see https://w3c.github.io/picture-in-picture/#dictdef-pictureinpictureeventinit
 */
function PictureInPictureEventInit() {};

/** @type {!PictureInPictureWindow} */
PictureInPictureEventInit.prototype.pictureInPictureWindow;
