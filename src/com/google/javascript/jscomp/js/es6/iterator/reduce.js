/*
 * Copyright 2026 The Closure Compiler Authors.
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
 * @fileoverview Polyfill for the ES2025 Iterator.prototype.reduce method.
 */
'require es6/iterator';
'require es6/util/iteratordo';
'require util/polyfill';

$jscomp.polyfill('Iterator.prototype.reduce', function(orig) {
  return orig || function(fn) {
    var hasAccumulator = arguments.length > 1;
    var accumulator = arguments[1];
    $jscomp.iteratorDo(this, fn, function(v, i) {
      if (!hasAccumulator) {
        hasAccumulator = true;
        accumulator = v;
      } else {
        accumulator = fn(accumulator, v, i);
      }
    });
    if (!hasAccumulator) {
      throw new TypeError('Reduce of empty iterator');
    }
    return accumulator;
  };
}, 'es_next', 'es3');
