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
 * @fileoverview Tests of static async methods. Based on the "jsasync"
 *     j2cl integration test.
 */
goog.module('jscomp.test.static_async');

const testSuite = goog.require('goog.testing.testSuite');

class Main {
  /**
   * @return {!IThenable<number>}
   */
  static async ten() {
    return 10;
  }

  /**
   * @return {!IThenable<number>}
   */
  static async main() {
    return Main.same(await Main.ten());
  }

  /**
   * @param {number} i
   * @return {number}
   */
  static same(i) {
    return i;
  }
}

testSuite({
  testCallStaticAsyncMethod() {
    return Main.main();
  }
});
