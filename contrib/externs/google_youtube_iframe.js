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
 * @fileoverview Externs for YouTube Player API for <iframe> Embeds.
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


/** @typedef {!{
 *    videoId: string,
 *    startSeconds: (number|undefined),
 *    endSeconds: (number|undefined),
 *    suggestedQuality: (string|undefined),
 *  }} */
YT.Player.VideoIdParams;


/**
 * Loads the specified video's thumbnail and prepares the player to play the
 * video.
 * @param {!YT.Player.VideoIdParams|string} videoIdOrObject YouTube Video ID or
 *     parameters object.
 * @param {number=} opt_startSeconds Float/integer that specifies the time
 *     from which the video should start playing.
 * @param {string=} opt_suggestedQuality The suggested playback quality for
 *     the video.
 */
YT.Player.prototype.cueVideoById = function(
    videoIdOrObject, opt_startSeconds, opt_suggestedQuality) {};


/**
 * Loads and plays the specified video.
 * @param {!YT.Player.VideoIdParams|string} videoIdOrObject YouTube Video ID or
 *     parameters object.
 * @param {number=} opt_startSeconds Float/integer that specifies the time
 *     from which the video should start playing.
 * @param {string=} opt_suggestedQuality The suggested playback quality for
 *     the video.
 */
YT.Player.prototype.loadVideoById = function(
    videoIdOrObject, opt_startSeconds, opt_suggestedQuality) {};


/** @typedef {!{
 *    mediaContentUrl: string,
 *    startSeconds: (number|undefined),
 *    endSeconds: (number|undefined),
 *    suggestedQuality: (string|undefined),
 *  }} */
YT.Player.MediaContentUrlParams;


/**
 * Loads the specified video's thumbnail and prepares the player to play the
 * video.
 * @param {!YT.Player.MediaContentUrlParams|string} mediaContentUrlOrObject
 *     YouTube player URL in the format http://www.youtube.com/v/VIDEO_ID or
 *     parameters object.
 * @param {number=} opt_startSeconds Float/integer that specifies the time
 *     from which the video should start playing.
 * @param {string=} opt_suggestedQuality The suggested playback quality for
 *     the video.
 */
YT.Player.prototype.cueVideoByUrl = function(
    mediaContentUrlOrObject, opt_startSeconds, opt_suggestedQuality) {};


/**
 * Loads and plays the specified video.
 * @param {!YT.Player.MediaContentUrlParams|string} mediaContentUrlOrObject
 *     YouTube player URL in the format http://www.youtube.com/v/VIDEO_ID or
 *     parameters object.
 * @param {number=} opt_startSeconds Float/integer that specifies the time
 *     from which the video should start playing.
 * @param {string=} opt_suggestedQuality The suggested playback quality for
 *     the video.
 */
YT.Player.prototype.loadVideoByUrl = function(
    mediaContentUrlOrObject, opt_startSeconds, opt_suggestedQuality) {};


/**
 * Loads and cues a specified video in a playlist.
 *
 * @param {string|!Array<string>|!Object} playlistOrObject An object containing
 *     the parameters.  N.B. a String object will be treated as an object
 *     instead of as a string.  Otherwise, a string literal that is a playlist
 *     id (without the PL list prefix) or an array of video ids.
 * @param {number=} opt_index The index to begin playback at.
 * @param {number=} opt_startSeconds Float/integer that specifies the time
 *     from which the video should start playing.
 * @param {string=} opt_suggestedQuality The suggested playback quality for
 *     the video.
 */
YT.Player.prototype.cuePlaylist = function(
    playlistOrObject, opt_index, opt_startSeconds, opt_suggestedQuality) {};


/**
 * @param {string|!Array<string>|!Object} playlistOrObject An object
 *     containing the parameters.  N.B. a String object will be treated as an
 *     object instead of as a string.  Otherwise, a string literal that is a
 *     playlist id (without the PL list prefix) or an array of video ids.
 * @param {number=} opt_index The index to begin playback at.
 * @param {number=} opt_startSeconds Float/integer that specifies the time
 *     from which the video should start playing.
 * @param {string=} opt_suggestedQuality The suggested playback quality for
 *     the video.
 */
YT.Player.prototype.loadPlaylist = function(
    playlistOrObject, opt_index, opt_startSeconds, opt_suggestedQuality) {};


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
YT.Player.prototype.getVideoLoadedFraction = function() {};


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


/** Destroys the player reference. */
YT.Player.prototype.destroy = function() {};


/**
 * @return {HTMLIFrameElement} The DOM node for the embedded iframe.
 * @nosideeffects
 */
YT.Player.prototype.getIframe = function() {};


/**
 * @return {number}
 * @nosideeffects
 */
YT.Player.prototype.getPlaybackRate = function() {};


/** @param {number} suggestedRate */
YT.Player.prototype.setPlaybackRate = function(suggestedRate) {};


/**
 * @return {!Array.<number>}
 * @nosideeffects
 */
YT.Player.prototype.getAvailablePlaybackRates = function() {};


/** @enum */
YT.PlayerState = {
  UNSTARTED: -1,
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
