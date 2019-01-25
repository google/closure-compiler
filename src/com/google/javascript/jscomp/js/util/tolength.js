/*
 * Copyright 2019 The Closure Compiler Authors.
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

'require util/tointeger';

/**
 * Converts argument to an integer suitable for use as the length of an array-like object.
 *
 * @see https://www.ecma-international.org/ecma-262/9.0/#sec-tolength
 *
 * @param {*} arg
 * @return {number}
 */
$jscomp.toLength = function(arg) {
  var len = $jscomp.toInteger(arg);
  if (len < 0) {
    return 0;
  }
  return Math.min(len, Math.pow(2, 53) - 1);
};
