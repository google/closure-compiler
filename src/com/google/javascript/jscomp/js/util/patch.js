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

'require util/global util/patches';

/**
 * Utility for monkey-patching built-in functions when needed.
 * The basic approach is to patch any function that currently exists at the
 * given name, but to also save a list of the patches so that they can also
 * be applied to any later polyfills (this is done in polyfill.js).  Note
 * that this is orthogonal to polyfills because whether a function is
 * patched is independent of language versions or whether the function is
 * defined natively (or is polyfilled earlier, later, or not at all), but
 * rather whether a different polyfill requires the functionality to be
 * altered.  The primary example use cases include  Object.keys (from ES5)
 * needing to be patched to be aware of our Symbol polyfill for non-ES6
 * browsers, but left alone for modern browsers, and WeakMap needing to
 * ensure its hidden property is included before keys can be frozen.
 *
 * @param {string} target
 * @param {function(!Function): !Function} patch
 */
$jscomp.patch = function(target, patch) {
  ($jscomp.patches[target] = $jscomp.patches[target] || []).push(patch);
  var obj = $jscomp.global;
  var split = target.split('.');
  for (var i = 0; i < split.length - 1 && obj; i++) {
    obj = obj[split[i]];
  }
  var property = split[split.length - 1];
  if (obj && obj[property] instanceof Function) {
    obj[property] = patch(obj[property]);
  }
};
