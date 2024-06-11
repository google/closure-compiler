/*
 * Copyright 2024 The Closure Compiler Authors.
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

'require util/global';
'require util/polyfill';
'require es6/symbol';
'require es6/util/inherits';
'require es6/reflect/construct';


$jscomp.polyfill('Symbol.dispose', function(orig) {
  if (orig) {
    return orig;
  }
  return Symbol('Symbol.dispose');
  // probably ES2025
}, 'es_next', 'es3');

$jscomp.polyfill('Symbol.asyncDispose', function(orig) {
  if (orig) {
    return orig;
  }
  return Symbol('Symbol.asyncDispose');
  // probably ES2025
}, 'es_next', 'es3');

$jscomp.polyfill('SuppressedError', function(orig) {
  if (orig) {
    return orig;
  }
  /**
   * @constructor
   * @extends {Error}
   * @param {*} error The error that resulted in a suppression.
   * @param {*} suppressed The error that was suppressed.
   * @param {string=} message The message for the error.
   * @return {!SuppressedError}
   */
  function SuppressedError(error, suppressed, message) {
    // Support being called without `new`.
    if (!(this instanceof SuppressedError)) {
      return new SuppressedError(error, suppressed, message);
    }
    // Create a new error object just so we can copy its stack trace into ours,
    // if appropriate. Include the message, because v8 & possibly other engines
    // include the message in the stack trace they create.
    var tmpError = Error(message);
    if ('stack' in tmpError) {
      // Old versions of IE Don't set stack until the object is thrown, and
      // won't set it then if it already exists on the object. Hence,
      // conditionally setting it here.
      this.stack = tmpError.stack;
    }
    this.message = tmpError.message;
    /** @type {*} */
    this.error = error;
    /** @type {*} */
    this.suppressed = suppressed;
  }
  $jscomp.inherits(SuppressedError, Error);
  /**
   * Error name, defaults to SuppressedError.
   *
   * @type {string}
   * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Error/name
   * @override
   */
  SuppressedError.prototype.name = 'SuppressedError';

  return SuppressedError;
  // probably ES2025
}, 'es_next', 'es3');

