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
 * This function wrapper is required to suppress the unknown expression type
 * error as it doesn't seem to work at fileoverview level
 * @param {!Object} maybeGlobal
 * @return {!Global} The global object.
 * @suppress {undefinedVars|reportUnknownTypes}
 */
$jscomp.getGlobal = function(maybeGlobal) {
  return /** @type {!Global} */ (
      (typeof globalThis == 'object') ? globalThis :  // use if exists
          (typeof window == 'object') ? window :      // normal browser window
              (typeof self == 'object') ? self :      // WebWorker
                  (typeof global != 'undefined' && global != null) ?
                                          global :  // NodeJS
                      maybeGlobal);                 // app scripts and others
};

/**
 * The global object.
 * @const {!Global}
 */
$jscomp.global = $jscomp.getGlobal(this);

