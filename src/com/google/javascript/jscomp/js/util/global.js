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
 */
'require base';
'declare global';
'declare window';


/**
 * @param {!Object} maybeGlobal
 * @return {!Object} The global object.
 * @suppress {undefinedVars|reportUnknownTypes}
 */
$jscomp.getGlobal = function(maybeGlobal) {
  // This logic can be simplified to be made more optimizable.
  return (typeof window != 'undefined' && window === maybeGlobal) ?
      maybeGlobal :
      (typeof global != 'undefined' && global != null) ?
      /** @type {!Object} */ (global) :
      maybeGlobal;
};


// TODO(sdh): This should be typed as "the global object" now that the type
// of "Global" has landed in the type system.
/**
 * The global object. For browsers we could just use `this` but in Node that
 * doesn't work.
 * @const {?}
 */
$jscomp.global = $jscomp.getGlobal(this);
