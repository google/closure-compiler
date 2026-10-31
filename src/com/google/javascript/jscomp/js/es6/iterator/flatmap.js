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
 * @fileoverview Polyfill for the ES2025 Iterator.prototype.flatMap method.
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.flatmap
 */
'require es6/iterator';
'require es6/util/getiteratorflattenable';
'require es6/util/lazyiterator';
'require es6/util/validateiteratorcallback';
'require util/checkisobject';
'require util/polyfill';

$jscomp.polyfill('Iterator.prototype.flatMap', function(orig) {
  return orig ||
      /**
       * @see https://tc39.es/ecma262/#sec-iterator.prototype.flatmap
       * @see https://tc39.es/ecma262/#sec-getiteratorflattenable
       * @this {!Iterator<VALUE>}
       * @param {function(VALUE, number): (!Iterable<RESULT>|!IteratorLike<RESULT>|!Iterator<RESULT>)} mapper
       * @return {!Iterator<RESULT>}
       * @template VALUE, RESULT
       * @suppress {reportUnknownTypes}
       */
      function(mapper) {
        $jscomp.validateIteratorCallback(this, mapper);
        var outerIterator = this;
        var outerNextMethod = outerIterator.next;
        var counter = 0;
        var /** ? */ innerIterator = null;
        var /** ? */ innerNextMethod = null;
        return $jscomp.createLazyIterator(
            {
              'return': function() {
                if (innerIterator) {
                  try {
                    $jscomp.iteratorCloseNormal(innerIterator);
                  } catch (e) {
                    // Close outerIterator via iteratorCloseAbrupt (rather than
                    // iteratorCloseNormal in a `finally`) so any secondary
                    // error from outerIterator.return() is suppressed and does
                    // not mask `e` (ECMA-262 27.1.4.5 step 5.a.viii.4.b.iii.2).
                    $jscomp.iteratorCloseAbrupt(outerIterator);
                    throw e;
                  }
                }
                $jscomp.iteratorCloseNormal(outerIterator);
                return outerIterator;
              },
            },
            function() {
              while (true) {
                var outerValue;
                if (!innerIterator) {
                  var outerResult =
                      $jscomp.checkIsObject(outerNextMethod.call(outerIterator));
                  if (outerResult.done) {
                    return;
                  }
                  outerValue = outerResult.value;
                }
                try {
                  if (!innerIterator) {
                    innerIterator = $jscomp.getIteratorFlattenable(
                        mapper(outerValue, counter++), false);
                    innerNextMethod = innerIterator.next;
                  }
                  var innerResult =
                      $jscomp.checkIsObject(innerNextMethod.call(innerIterator));
                  if (!innerResult.done) {
                    return $jscomp.iteratorResult(innerResult.value, false);
                  }
                  innerIterator = null;
                } catch (e) {
                  $jscomp.iteratorCloseAbrupt(outerIterator);
                  throw e;
                }
              }
            });
      };
}, 'es_next', 'es3');
