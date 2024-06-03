/*
 * Copyright 2024 The Closure Compiler Authors.
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
 * @fileoverview Provides methods to patch native objects and functions.
 * @suppress {uselessCode}
 */
'require util/polyfill';

/**
 * @param {string} target Qualified name of the class or method to polyfill,
 *     e.g. 'Array.prototype.includes' or 'Map'.
 * @param {?function(*): *} patcher A function that takes the current
 *     implementation of the target and returns an optional new implementation.
 *     If null is returned, then no change will be made.
 * @noinline
 * NOTE: We prevent inlining so RemoveUnusedPolyfills can always recognize this
 * call.
 */
$jscomp.patch = function(target, patcher) {
  $jscomp.polyfillUnisolated(target, function(original) {
    return original && patcher(original);
  });
};
