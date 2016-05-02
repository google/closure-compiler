/**
 * Copyright 2015 Anomaly Software Pty Ltd.
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
 * @fileoverview Externs for AppEngine
 *
 * @see https://cloud.google.com/appengine/docs/python/channel/
 * @see https://groups.google.com/forum/#!topic/closure-compiler-discuss/Z8sThaEEb34
 * @externs
 */

/**
 * Suppresses the compiler warning when multiple externs files declare the
 * goog namespace.
 * @suppress {duplicate}
 */
 var goog = {};

/** @const */
goog.appengine = {};

/**
 * @param {string} t
 */
goog.appengine.Channel = function(t) { };


goog.appengine.Channel.prototype.open = function() {};
