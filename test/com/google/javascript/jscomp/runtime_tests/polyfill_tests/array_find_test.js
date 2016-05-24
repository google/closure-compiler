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

goog.module('jscomp.runtime_tests.polyfill_tests.array_find_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const userAgent = goog.require('goog.userAgent');

testSuite({
  shouldRunTests() {
    // Disable tests for IE8 and below.
    return !userAgent.IE || userAgent.isVersionOrHigher(9);
  },

  testFind() {
    let arr = [1, 2, 3, 4, 5];
    assertEquals(2, arr.find(x => x % 2 == 0));
    assertEquals(4, arr.find(x => x > 3));
    assertEquals(1, arr.find(() => true));
    assertUndefined(arr.find(() => false));

    arr = ['x', 'y', 'z', 'w'];
    assertEquals('y', arr.find((_, i) => i == 1));
    arr[2] = arr;
    assertEquals(arr, arr.find((x, _, a) => x == a));

    arr = ['xx', 'yy', 'zz', 'ww'];
    /**
     * @param {string} x
     * @return {boolean}
     * @this {{first: string}}
     */
    const checkFirst = function(x) { return x[0] == this.first; };
    assertEquals('ww', arr.find(checkFirst, {first: 'w'}));

    arr = {5: 42, 2: 23, 6: 100, length: 6};
    assertEquals(42, Array.prototype.find.call(arr, x => x > 30));
    assertUndefined(Array.prototype.find.call(arr, x => x > 50));

    arr = 'abcABC';
    assertEquals(
        'A', Array.prototype.find.call(arr, x => x == x.toUpperCase()));
  },
});
