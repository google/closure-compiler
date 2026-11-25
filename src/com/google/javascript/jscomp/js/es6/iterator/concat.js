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
 * @fileoverview Polyfill for the ES2026 Iterator.concat static method.
 * @see https://tc39.es/ecma262/#sec-iterator.concat
 */
'require es6/iterator';
'require es6/symbol';
'require es6/util/lazyiterator';
'require util/checkisobject';
'require util/polyfill';

$jscomp.polyfill('Iterator.concat', function(orig) {
  if (orig) return orig;

  /**
   * @return {!Iterator<?>}
   * @suppress {reportUnknownTypes}
   */
  var concat = function() {
    var /** !Array<!Object|!Function> */ iterables = [];
    for (var i = 0; i < arguments.length; i++) {
      var item = $jscomp.checkIsObject(arguments[i]);
      var method = /** @type {?} */ (item)[Symbol.iterator];
      if (typeof method !== 'function') {
        throw new TypeError('[Symbol.iterator] is not a function');
      }
      iterables.push(item, method);
    }
    var index = 0;
    var /** ? */ innerIterator = null;
    var /** ? */ innerNextMethod = null;
    return $jscomp.createLazyIterator(
        {
          'return': function() {
            if (innerIterator) {
              $jscomp.iteratorCloseNormal(innerIterator);
            }
            return this;
          },
        },
        function() {
          while (index < iterables.length || innerIterator) {
            if (!innerIterator) {
              var item = iterables[index++];
              var method = /** @type {!Function} */ (iterables[index++]);
              innerIterator = $jscomp.checkIsObject(method.call(item));
              innerNextMethod = innerIterator.next;
            }
            var result = $jscomp.checkIsObject(
                innerNextMethod.call(innerIterator));
            if (!result.done) {
              return $jscomp.iteratorResult(result.value, false);
            }
            innerIterator = null;
          }
        });
  };

  return concat;
}, 'es_next', 'es3');
