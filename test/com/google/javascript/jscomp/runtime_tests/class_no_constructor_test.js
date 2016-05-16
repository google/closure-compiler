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

goog.require('goog.testing.jsunit');

var Base = class {
  constructor(var_args) {
    this.ctorArgs = arguments;
  }
}

var Sub = class extends Base {}

/**
 * Test for https://github.com/google/closure-compiler/issues/690
 */
function testIssue690() {
  var s = new Sub(6, 9, 0);
  assertEquals(3, s.ctorArgs.length);

  assertEquals(6, s.ctorArgs[0]);
  assertEquals(9, s.ctorArgs[1]);
  assertEquals(0, s.ctorArgs[2]);
}
