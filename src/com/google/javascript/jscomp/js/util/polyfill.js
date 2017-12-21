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
'require util/defineproperty';
'require util/global';


/**
 * @param {string} target Qualified name of the class or method to polyfill,
 *     e.g. 'Array.prototype.includes' or 'Map'.
 * @param {?function(*): *} polyfill A function that takes the current browser
 *     implementation of the target and returns an optional new polyfill
 *     implementation.  If null is returned, then no polyfill will be added.  A
 *     null argument for this parameter indicates that the function will not be
 *     polyfilled, and is only useful for `build_polyfill_table.js` bookkeeping.
 * @param {string} fromLang The language level in which the target is expected
 *     to already be present in the browser.  The compiler requires that
 *     `languageOut < fromLang` before injecting a polyfill (i.e. if the
 *     specified output language already includes the feature then there's no
 *     need to polyfill it).
 * @param {string} toLang The language level required by the polyfill
 *     implementation.  The compiler will issue an error if a polyfill is
 *     required, but `languageOut < toLang`.  Additionally, the
 *     `build_polyfill_table.js` script audits the polyfill dependency tree to
 *     ensure that no polyfill with a lower `toLang` depends on one with a
 *     higher `toLang`.
 * @suppress {reportUnknownTypes}
 * @noinline
 * NOTE: We prevent inlining so RemoveUnusedPolyfills can always recognize this
 * call.
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
  if (impl == orig || impl == null) return;
  $jscomp.defineProperty(
      obj, property, {configurable: true, writable: true, value: impl});
};
