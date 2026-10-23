/*
 * Copyright 2022 The Closure Compiler Authors
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
 * @fileoverview The current draft spec of MediaMetadata.
 * @see https://wicg.github.io/mediasession/
 * @externs
 */

/**
 * @see https://wicg.github.io/mediasession/#the-mediaimage-dictionary
 * @record
 * @struct
 */
function MediaImage() {}

/** @type {string} */
MediaImage.prototype.src;

/** @type {(string|undefined)} */
MediaImage.prototype.sizes;

/** @type {(string|undefined)} */
MediaImage.prototype.type;


/**
 * @see https://wicg.github.io/mediasession/#the-chapterinformation-interface
 * @interface
 * @struct
 */
function ChapterInformation() {}

/** @const {string} */
ChapterInformation.prototype.title;

/** @const {number} */
ChapterInformation.prototype.startTime;

/** @const {!Array<!MediaImage>} */
ChapterInformation.prototype.artwork;


/**
 * @see https://w3c.github.io/mediasession/#dictdef-chapterinformationinit
 * @record
 * @struct
 */
function ChapterInformationInit() {}

/** @type {(string|undefined)} */
ChapterInformationInit.prototype.title;

/** @type {(number|undefined)} */
ChapterInformationInit.prototype.startTime;

/** @type {(!Array<!MediaImage>|undefined)} */
ChapterInformationInit.prototype.artwork;


/**
 * A MediaMetadata object is a representation of the metadata associated with a
 * MediaSession that can be used by user agents to provide customized user
 * interface.
 * @see https://wicg.github.io/mediasession/#the-mediametadata-interface
 * @constructor
 * @param {?MediaMetadataInit=} init
 */
function MediaMetadata(init) {}

/** @type {string} */
MediaMetadata.prototype.album;

/** @type {string} */
MediaMetadata.prototype.artist;

/** @type {!Array<!MediaImage>} */
MediaMetadata.prototype.artwork;

/** @type {string} */
MediaMetadata.prototype.title;

/** @const {!Array<!ChapterInformation>} */
MediaMetadata.prototype.chapterInfo;


/**
 * @see https://wicg.github.io/mediasession/#the-mediametadata-interface
 * @record
 * @struct
 */
function MediaMetadataInit() {}

/** @type {(string|undefined)} */
MediaMetadataInit.prototype.album;

/** @type {(string|undefined)} */
MediaMetadataInit.prototype.artist;

/** @type {(!Array<!MediaImage>|undefined)} */
MediaMetadataInit.prototype.artwork;

/** @type {(string|undefined)} */
MediaMetadataInit.prototype.title;

/** @type {(!Array<!ChapterInformationInit>|undefined)} */
MediaMetadataInit.prototype.chapterInfo;


/**
 * A MediaSession objects represents a media session for a given document and
 * allows a document to communicate to the user agent some information about the
 * playback and how to handle it.
 * @see https://wicg.github.io/mediasession/#the-mediasession-interface
 * @interface
 * @struct
 */
function MediaSession() {}

/** @type {(null|!MediaMetadata)} */
MediaSession.prototype.metadata;

/** @type {string} */
MediaSession.prototype.playbackState;

/**
 * @param {string} action
 * @param {(null|function(?): void)|(null|function(!MediaSessionActionDetails): void)} listener
 * @return {void}
 */
MediaSession.prototype.setActionHandler = function(action, listener) {};


/** @type {function((undefined|!MediaPositionState)=): void} */
MediaSession.prototype.setPositionState;

/**
 * @param {boolean} active
 * @return {!Promise<void>}
 */
MediaSession.prototype.setCameraActive = function(active) {};

/**
 * @param {boolean} active
 * @return {!Promise<void>}
 */
MediaSession.prototype.setMicrophoneActive = function(active) {};

/**
 * @param {boolean} active
 * @return {!Promise<void>}
 */
MediaSession.prototype.setScreenshareActive = function(active) {};

/** @type {?MediaSession} */
Navigator.prototype.mediaSession;


/** @typedef {string} */
var MediaSessionPlaybackState;

/** @typedef {string} */
var MediaSessionAction;

/** @typedef {string} */
var MediaSessionEnterPictureInPictureReason;


/**
 * @see https://wicg.github.io/mediasession/#the-mediapositionstate-dictionary
 * @record
 * @struct
 */
function MediaPositionState() {}

/** @type {(undefined|number)} */
MediaPositionState.prototype.duration;
/** @type {(undefined|number)} */
MediaPositionState.prototype.playbackRate;
/** @type {(undefined|number)} */
MediaPositionState.prototype.position;


/**
 * @see https://wicg.github.io/mediasession/#the-mediasessionactiondetails-dictionary
 * @record
 * @struct
 */
function MediaSessionActionDetails() {}

/** @type {string} */
MediaSessionActionDetails.prototype.action;
/** @type {(undefined|null|number)} */
MediaSessionActionDetails.prototype.seekOffset;
/** @type {(undefined|null|number)} */
MediaSessionActionDetails.prototype.seekTime;
/** @type {(undefined|null|boolean)} */
MediaSessionActionDetails.prototype.fastSeek;
/** @type {(undefined|null|boolean)} */
MediaSessionActionDetails.prototype.isActivating;
/** @type {(undefined|null|MediaSessionEnterPictureInPictureReason)} */
MediaSessionActionDetails.prototype.enterPictureInPictureReason;
