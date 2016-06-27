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

'require es6/symbol util/polyfill';

$jscomp.polyfill('Array.from', function(orig) {
  if (orig) return orig;

  /**
   * Creates a new Array from an array-like or iterable object.
   *
   * <p>Polyfills the static function Array.from().  Does not support
   * constructor inheritance (i.e. (subclass of Array).from), and
   * relies on the compiler to check the validity of inputs rather
   * than producing spec-compliant TypeErrors.
   *
   * @param {!IArrayLike<INPUT>|!Iterator<INPUT>|!Iterable<INPUT>}
   *     arrayLike An array-like or iterable.
   * @param {(function(this: THIS, INPUT): OUTPUT)=} opt_mapFn
   *     Function to call on each argument.
   * @param {THIS=} opt_thisArg
   *     Object to use as 'this' when calling mapFn.
   * @return {!Array<OUTPUT>}
   * @template INPUT, OUTPUT, THIS
   */
  var polyfill = function(arrayLike, opt_mapFn, opt_thisArg) {
    $jscomp.initSymbolIterator();
    opt_mapFn = opt_mapFn != null ? opt_mapFn : function(x) { return x; };
    var result = [];
    // NOTE: this is cast to ? because [] on @struct is an error
    var iteratorFunction = /** @type {?} */ (arrayLike)[Symbol.iterator];
    if (typeof iteratorFunction == 'function') {
      arrayLike = iteratorFunction.call(arrayLike);
    }
    if (typeof arrayLike.next == 'function') {
      var next;
      while (!(next = arrayLike.next()).done) {
        result.push(
            opt_mapFn.call(/** @type {?} */ (opt_thisArg), next.value));
      }
    } else {
      var len = arrayLike.length;  // need to support non-iterables
      for (var i = 0; i < len; i++) {
        result.push(
            opt_mapFn.call(/** @type {?} */ (opt_thisArg), arrayLike[i]));
      }
    }
    return result;
  };

  return polyfill;
}, 'es6-impl', 'es3');
