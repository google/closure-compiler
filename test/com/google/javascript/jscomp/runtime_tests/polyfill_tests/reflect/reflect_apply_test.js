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

goog.module('jscomp.runtime_tests.polyfill_tests.reflect_apply_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  testApply() {
    /**
     * @param {number} a
     * @param {number} b
     * @this {{x: number}}
     * @return {number}
     */
    const foo = function(a, b) { return 100 * this.x + 10 * a + b; };
    // mess up the standard Function.prototype methods
    foo.apply = () => 99;
    foo.call = () => 99;
    foo.bind = () => () => 99;

    assertEquals(654, Reflect.apply(foo, {x: 6}, [5, 4]));
  },
});
