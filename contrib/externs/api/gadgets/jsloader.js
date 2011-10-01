/*
 * Copyright 2011 Google Inc.
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
 * @fileoverview Externs file for JS loader libraries. This is used by +1 child
 * iframes to load the feature JS that is equivalent to/compatible with its
 * parent. This makes use of "hints" appearing in the parent/iframe URL.
 *
 * @externs
 */

// Root namespace for JS loader.
var jsloader = {};

/**
 * Loads a Javascript by dynamically script-sourcing a JS URL calculated based
 * on directives minted in the parent/iframe URL.
 * @param {Array.<string>} features An array of requested features.
 * @param {string=} opt_callback Name of global function to callback upon load.
 */
jsloader.load = function(features, opt_callback) {};

