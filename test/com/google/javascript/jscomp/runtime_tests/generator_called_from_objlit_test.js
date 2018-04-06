/*
 * Copyright 2017 The Closure Compiler Authors.
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
 * Regression test for https://github.com/google/closure-compiler/issues/2873
 */
goog.module('jscomp.runtime_tests.generator_called_from_objlit');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  testIssue2873() {
    /**
     * A simple generator function that references 'this' and expects it to
     * refer to the object literal below.
     * @suppress {checkTypes}
     */
    function* generator() {
      yield this.x;
      yield this.y;
    }

    var iterator = { g: generator, x: 5, y: 6 }.g();
    var item = iterator.next();
    assertObjectEquals({value: 5, done: false}, item);

    item = iterator.next();
    assertObjectEquals({value: 6, done: false}, item);

    item = iterator.next();
    assertObjectEquals({value: undefined, done: true}, item);
  }
});
