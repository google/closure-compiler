/*
 * Copyright 2011 The Closure Compiler Authors.
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
 * @fileoverview Externs for YouTube Player API for <iframe> Embeds
 * @see http://code.google.com/apis/youtube/iframe_api_reference.html
 * @externs
 */

/** @return {undefined} */
var onYouTubePlayerAPIReady = function() {};

/** @return {undefined} */
window.onYouTubePlayerAPIReady = function() {};

/** @const */
var YT = {};

/**
 * @constructor
 * @param {(string|Element)} container
 * @param {Object.<string, *>} opts
 */
YT.Player = function(container, opts) {};

/**
 * @param {string} videoId
 * @param {number=} startSeconds
 * @param {string=} suggestedQuality
 */
YT.Player.prototype.cueVideoById =
    function(videoId, startSeconds, suggestedQuality) {};

/**
 * @param {string} videoId
 * @param {number=} startSeconds
 * @param {string=} suggestedQuality
 */
YT.Player.prototype.loadVideoById =
    function(videoId, startSeconds, suggestedQuality) {};

/**
 * @param {string} mediaContentUrl
 * @param {number} startSeconds
 * @param {string=} suggestedQuality
 */
YT.Player.prototype.cueVideoByUrl =
    function(mediaContentUrl, startSeconds, suggestedQuality) {};

/**
 * @param {string} mediaContentUrl
 * @param {number} startSeconds
 * @param {string=} suggestedQuality
 */
YT.Player.prototype.loadVideoByUrl =
    function(mediaContentUrl, startSeconds, suggestedQuality) {};

/**
 * @param {(String|Array.<String>)} playlist
 * @param {number=} index
 * @param {number=} startSeconds
 * @param {string=} suggestedQuality
 */
YT.Player.prototype.cuePlaylist =
    function(playlist, index, startSeconds, suggestedQuality) {};

/**
 * @param {(String|Array.<String>)} playlist
 * @param {number=} index
 * @param {number=} startSeconds
 * @param {string=} suggestedQuality
 */
YT.Player.prototype.loadPlaylist =
    function(playlist, index, startSeconds, suggestedQuality) {};

/** @return {undefined} */
YT.Player.prototype.playVideo = function() {};

/** @return {undefined} */
YT.Player.prototype.pauseVideo = function() {};

/** @return {undefined} */
YT.Player.prototype.stopVideo = function() {};

/**
 * @param {number} seconds
 * @param {boolean} allowSeekAhead
 */
YT.Player.prototype.seekTo = function(seconds, allowSeekAhead) {};

/** @return {undefined} */
YT.Player.prototype.clearVideo = function() {};

/** @return {undefined} */
YT.Player.prototype.nextVideo = function() {};

/** @return {undefined} */
YT.Player.prototype.previousVideo = function() {};

/** @param {number} index */
YT.Player.prototype.playVideoAt = function(index) {};

/** @return {undefined} */
YT.Player.prototype.mute = function() {};

/** @return {undefined} */
YT.Player.prototype.unMute = function() {};

/** @return {boolean} */
YT.Player.prototype.isMuted = function() {};

/** @param {number} volume */
YT.Player.prototype.setVolume = function(volume) {};

/**
 * @return {number}
 * @nosideeffects
 */
YT.Player.prototype.getVolume = function() {};

/** @param {boolean} loopPlaylists */
YT.Player.prototype.setLoop = function(loopPlaylists) {};

/** @param {boolean} shufflePlaylist */
YT.Player.prototype.setShuffle = function(shufflePlaylist) {};

/**
 * @return {number}
 * @nosideeffects
 */
YT.Player.prototype.getVideoBytesLoaded = function() {};

/**
 * @return {number}
 * @nosideeffects
 */
YT.Player.prototype.getVideoBytesTotal = function() {};

/**
 * @return {number}
 * @nosideeffects
 */
YT.Player.prototype.getVideoStartBytes = function() {};

/**
 * @return {YT.PlayerState|number}
 * @nosideeffects
 */
YT.Player.prototype.getPlayerState = function() {};

/**
 * @return {number}
 * @nosideeffects
 */
YT.Player.prototype.getCurrentTime = function() {};

/**
 * @return {(undefined|string)}
 * @nosideeffects
 */
YT.Player.prototype.getPlaybackQuality = function() {};

/** @param {string} suggestedQuality */
YT.Player.prototype.setPlaybackQuality = function(suggestedQuality) {};

/**
 * @return {Array.<string>}
 * @nosideeffects
 */
YT.Player.prototype.getAvailableQualityLevels = function() {};

/**
 * @return {number}
 * @nosideeffects
 */
YT.Player.prototype.getDuration = function() {};

/**
 * @return {string}
 * @nosideeffects
 */
YT.Player.prototype.getVideoUrl = function() {};

/**
 * @return {string}
 * @nosideeffects
 */
YT.Player.prototype.getVideoEmbedCode = function() {};

/**
 * @return {Array.<string>}
 * @nosideeffects
 */
YT.Player.prototype.getPlaylist = function() {};

/**
 * @return {number}
 * @nosideeffects
 */
YT.Player.prototype.getPlaylistIndex = function() {};

/**
 * @param {string} eventName
 * @param {string} listenerName
 */
YT.Player.prototype.addEventListener = function(eventName, listenerName) {};

/** @enum */
YT.PlayerState = {
    ENDED: 0,
    PLAYING: 1,
    PAUSED: 2,
    BUFFERING: 3,
    CUED: 4
};

/**
 * @constructor
 * @private
 */
YT.Event = function() {};

/** @type {string|number|undefined} */
YT.Event.prototype.data;

/** @type {YT.Player} */
YT.Event.prototype.target = null;
