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

import Foo from 'goog:test.ns.Foo';
import {staticBar} from 'goog:test.ns.Foo';
import {foo, bar} from 'goog:test.goog.module';

function testImportDefault() {
  assertEquals(42, new Foo().bar);
}

function testImportDestructuring() {
  assertEquals(23, staticBar);
}

function testImportDestructuring_googModule() {
  assertEquals(23, foo);
  assertEquals(42, bar);
}
