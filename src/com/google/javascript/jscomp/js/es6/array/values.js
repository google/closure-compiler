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

'require es6/util/iteratorfromarray';
'require util/polyfill';

// NOTE: Although Array.prototype.values was added to the 2015 edition of the
// spec, we consider it an "ES8" feature because many browsers which are
// otherwise ES6-compatible, have not implemented it due to web compatibility
// issues. See https://bugs.chromium.org/p/chromium/issues/detail?id=615873
$jscomp.polyfill('Array.prototype.values', function(orig) {
  if (orig) return orig;

  /**
   * Returns an iterator of values of the given array.
   *
   * @this {!IArrayLike<VALUE>}
   * @return {!IteratorIterable<VALUE>}
   * @template VALUE
   * @suppress {reportUnknownTypes}
   */
  var polyfill = function() {
    return $jscomp.iteratorFromArray(this, function(k, v) { return v; });
  };

  return polyfill;
}, 'es8', 'es3');
