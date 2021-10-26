/*
 * Copyright 2021 The Closure Compiler Authors.
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
 * @fileoverview Provides a method to return the rest arguments from a
 * function's arguments. Should be called using `apply`, using the start index
 * as the value for `this` and the original function's arguments array as the
 * arguments, to avoid issues with V8 function optimization backoff cause by
 * passing arguments around as a function parameter (apply is special-cased).
 *
 * @suppress {reportUnknownTypes, uselessCode}
 */

'require base';

/**
 * @this {number}
 * @return {!Array<?>}
 * @noinline
 */
$jscomp.getRestArguments = function() {
  var startIndex = Number(this);
  var restArgs = [];
  for (var i = startIndex; i < arguments.length; i++) {
    restArgs[i - startIndex] = arguments[i];
  }
  return restArgs;
};
