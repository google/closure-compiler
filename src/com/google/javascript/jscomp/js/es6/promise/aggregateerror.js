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
 * @fileoverview
 * @suppress {uselessCode}
 */
'require util/polyfill';
'require es6/promise/promise';
'require es6/util/inherits';

$jscomp.polyfill('AggregateError', function(orig) {
  if (orig) return orig;

  /**
   * @constructor
   * @extends {AggregateError}
   * @param {!Iterable<*>} errors
   * @param {string} message
   * @suppress {reportUnknownTypes}
   */
  var polyfill = function(errors, message) {
    // Create a new error object just so we can copy its stack trace into ours,
    // if appropriate. Include the message, because v8 & possibly other engines
    // include the message in the stack trace they create.
    var $jscomp$tmp$error = Error(message);
    if ('stack' in $jscomp$tmp$error) {
      // Old versions of IE Don't set stack until the object is thrown, and
      // won't set it then if it already exists on the object. Hence,
      // conditionally setting it here.
      this.stack = $jscomp$tmp$error.stack;
    }
    this.errors = /** @type {!Array<!Error>} */ (errors);
    this.message = $jscomp$tmp$error.message;
  };
  /**
   * @suppress {checkTypes}
   * Suppress type-mismatch between the `@extends {AggregateError}` and passing
   * just Error as the superclass. `@extend`ing AggregateError is only for the
   * disambiguation pass to work.
   */
  $jscomp.inherits(polyfill, Error);

  /**
   * Error name, defaults to AggregateError.
   * @type {string}
   * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Error/name
   * @override
   */
  polyfill.prototype.name = 'AggregateError';

  return polyfill;
}, 'es_2021', 'es3');
