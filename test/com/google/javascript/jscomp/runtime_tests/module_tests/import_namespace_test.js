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

import {bar, foo} from 'goog:data.goog.module';
import Foo from 'goog:data.ns.Foo';
import {staticBar} from 'goog:data.ns.Foo';

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  testImportDefault() {
    assertEquals(42, new Foo().bar);
  },

  testImportDestructuring() {
    assertEquals(23, staticBar);
  },

  testImportDestructuring_googModule() {
    assertEquals(23, foo);
    assertEquals(42, bar);
  },
});
