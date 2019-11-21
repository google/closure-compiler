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


/**
 * @param {!Object} maybeGlobal
 * @return {!Global} The global object.
 * @suppress {undefinedVars|reportUnknownTypes}
 */
$jscomp.getGlobal = function(maybeGlobal) {
  // TODO(b/144860612): This logic could be improved to work in more
  // environments https://mathiasbynens.be/notes/globalthis
  return (typeof window != 'undefined' && window === maybeGlobal) ?
      /** @type {!Global} */ (maybeGlobal) :
      (typeof global != 'undefined' && global != null) ?
      /** @type {!Global} */ (global) :
      /** @type {!Global} */ (maybeGlobal);
};


/**
 * The global object.
 * @const {!Global}
 */
$jscomp.global = $jscomp.getGlobal(this);
