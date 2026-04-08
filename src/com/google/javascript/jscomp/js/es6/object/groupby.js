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
 * @fileoverview Polyfill for Object.groupBy.
 */
'require es6/util/makeiterator';
'require util/polyfill';

$jscomp.polyfill('Object.groupBy', function(orig) {
  if (orig) return orig;

  /**
   * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/groupBy
   *
   * @param {!Iterable<VALUE>} items
   * @param {function(VALUE, number=): string|symbol|number} callbackFn
   * @return {!Object<string|symbol|number, !Array<VALUE>>}
   * @template VALUE
   */
  var polyfill = function(items, callbackFn) {
    if (typeof callbackFn !== 'function') {
      throw new TypeError('callbackFn must be a function');
    }
    var result = Object.create(null);
    var index = 0;
    var iter = $jscomp.makeIterator(items);
    for (var entry = iter.next(); !entry.done; entry = iter.next()) {
      var item = entry.value;
      var key = callbackFn(item, index++);
      var group;
      // SAFEGUARD: Check existence without triggering Object.prototype methods
      if (Object.prototype.hasOwnProperty.call(result, key)) {
        group = result[key];
      } else {
        group = [];
        result[key] = group;
      }
      group.push(item);
    }
    return result;
  };

  return polyfill;
}, 'es_next', 'es5');
