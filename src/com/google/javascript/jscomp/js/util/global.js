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
 * @fileoverview Runtime code to store the global object.
 * @suppress {uselessCode}
 */
'require base';

/**
 * Locate and return a reference to the global object.
 *
 * NOTE: This method is marked with `noinline`, because `RemoveUnusedCode` has
 * trouble removing the loop it contains if it gets inlined into the global
 * scope.
 * @param {?Object} passedInThis
 * @return {!Global} The global object.
 * @suppress {undefinedVars|reportUnknownTypes}
 * @noinline
 */
$jscomp.getGlobal = function(passedInThis) {
  var possibleGlobals = [
    // Web Audio Worklets do not have any way to refer to their global object
    // other than `globalThis`.
    // This use of `globalThis` does not prevent removal of the `globalThis`
    // polyfill, because the usage is guarded by a typeof check.
    'object' == typeof globalThis && globalThis,
    // Rhino (used by older Google Docs Script projects) has no `window` or
    // `self`, but `this` from the global scope is the global object. Also,
    // some GWT tests running on Rhino do have a `window` and `self` but, for
    // unknown reasons, see runtime exceptions when we return `window` instead
    // of `passedInThis`.
    // NOTE: If the compiler's output is wrapped in a strict-mode function,
    // this file's code won't actually be executing in global scope, so this
    // value will be undefined.
    passedInThis,
    // Browser windows always have `window`
    'object' == typeof window && window,
    // WebWorkers have `self`
    'object' == typeof self && self,
    // NodeJS has `global`
    'object' == typeof global && global,
  ];
  for (var i = 0; i < possibleGlobals.length; ++i) {
    var maybeGlobal = possibleGlobals[i];
    // It can happen that an environment has, for example, both `global` and
    // `window` defined in the global scope, but one of them isn't actually
    // the global object, so check that it really seems to be the global object.
    // We use `Math` to check for this because it's only 4 characters long,
    // exists in all possible JS environments, and doesn't have the problematic
    // equality behavior of `NaN`.
    if (maybeGlobal && maybeGlobal['Math'] == Math) {
      return /** @type {!Global} */ (maybeGlobal);
    }
  }
  // Throw an exception if we cannot find the global object.
  // We have to be sneaky about it, otherwise the compiler will think this code
  // has a side effect (throwing an exception) that prevents it from being
  // removed, even when all usages of `$jscomp.global` get removed.
  // Casting through unknown is necessary to keep the compiler from rejecting
  // this code.
  return /** @type {!Global} */ (
      /** @type {?} */ ({
        valueOf: function() {
          throw new Error('Cannot find global object');
        }
      }.valueOf()));
};


/**
 * The global object.
 * @const {!Global}
 */
$jscomp.global = $jscomp.getGlobal(this);
