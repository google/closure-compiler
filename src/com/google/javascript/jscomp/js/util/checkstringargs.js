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

'require base';


/**
 * Throws if the argument is a RegExp, or if thisArg is undefined.
 * @param {?} thisArg The 'this' arg, which must be defined.
 * @param {*} arg The first argument of the function, which mustn't be a RegExp.
 * @param {string} func Name of the function, for reporting.
 * @return {string} The thisArg, coerced to a string.
 */
$jscomp.checkStringArgs = function(thisArg, arg, func) {
  if (thisArg == null) {
    throw new TypeError(
        "The 'this' value for String.prototype." + func +
        ' must not be null or undefined');
  }
  if (arg instanceof RegExp) {
    throw new TypeError(
        'First argument to String.prototype.' + func +
        ' must not be a regular expression');
  }
  return thisArg + '';
};
