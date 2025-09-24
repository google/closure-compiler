/*
 * Copyright 2025 The Closure Compiler Authors.
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
 * @fileoverview Implementation of abstract operation ToIntegerOrInfinity.
 *
 * @see https://tc39.es/ecma262/multipage/abstract-operations.html#sec-tointegerorinfinity
 */

'require es6/math/trunc';

/**
 * Converts argument to an integer representing its Number value with fractional
 * part truncated, or to +∞ or -∞ when that Number value is infinite.
 * @param {*} argument The argument to convert to an integer.
 * @return {number} Returns +∞, -∞, or an integer.
 */
$jscomp.toIntegerOrInfinity = function(argument) {
  var number = Number(argument);
  // Coalesce NaN, -0, and 0 to 0.
  if (isNaN(number) || number == 0) return 0;
  return Math.trunc(number);
};
