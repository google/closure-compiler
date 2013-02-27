/*
 * Copyright 2013 The Closure Compiler Authors.
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
 * @fileoverview Definitions for W3C's Gamepad specification.
 * @see http://www.w3.org/TR/gamepad/
 * @externs
 */

/**
 * @typedef {{id: string,
 *            index: number,
 *            timestamp: number,
 *            axes: Array.<number>,
 *            buttons: Array.<number>}}
 */
var Gamepad;

/**
 * @return {Array.<Gamepad>}
 */
navigator.getGamepads = function() {};

/**
 * @return {Array.<Gamepad>}
 */
navigator.webkitGetGamepads = function() {};
