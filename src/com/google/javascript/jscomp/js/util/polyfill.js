/*
 * Copyright 2016 The Closure Compiler Authors.
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
 * @fileoverview Provides methods to polyfill native objects.
 */
'require util/defineproperty util/global util/patches';


/**
 * @param {string} target
 * @param {?function(*): *} polyfill
 * @param {string} fromLang
 * @param {string} toLang
 */
$jscomp.polyfill = function(target, polyfill, fromLang, toLang) {
  if (!polyfill) return;
  var obj = $jscomp.global;
  var split = target.split('.');
  for (var i = 0; i < split.length - 1; i++) {
    var key = split[i];
    if (!(key in obj)) obj[key] = {};  // Might want to be defineProperty.
    obj = obj[key];
  }
  var property = split[split.length - 1];
  var orig = obj[property];
  var impl = polyfill(orig);
  if (impl == orig) return;
  var patches = $jscomp.patches[target] || [];
  for (i = 0; i < patches.length; i++) {
    impl = patches[i](/** @type {!Function} */ (impl));
  }
  $jscomp.defineProperty(
      obj, property, {configurable: true, writable: true, value: impl});
};
