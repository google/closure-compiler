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

goog.module('jscomp.runtime_tests.polyfill_tests.object_getownpropertysymbols_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('jscomp.runtime_tests.polyfill_tests.testing');

const {
  assertPropertyListEquals,
  objectCreate,
} = testing;

testSuite({
  testGetOwnPropertySymbols_symbols() {
    const a = Symbol('a');
    const b = Symbol('b');
    const c = Symbol('c');
    const obj = {[b]: 42, [a]: 13};
    assertPropertyListEquals([b, a], Object.getOwnPropertySymbols(obj));

    const sub = objectCreate(obj);
    assertObjectEquals([], Object.getOwnPropertySymbols(sub));
    sub[a] = 23;
    sub[c] = 12;

    assertPropertyListEquals([a, c], Object.getOwnPropertySymbols(sub));
  },

  testGetOwnPropertySymbols_stringsExcluded() {
    const a = Symbol('a');
    const b = Symbol('b');
    const c = Symbol('c');
    const obj = {[a]: 32, [c]: 12, 12: 14, 'a': 23};
    const sub = objectCreate(obj);
    sub[c] = 2;
    sub[b] = 1;
    sub['42'] = 42;
    sub['x'] = 25;

    assertPropertyListEquals([a, c], Object.getOwnPropertySymbols(obj));
    assertPropertyListEquals([c, b], Object.getOwnPropertySymbols(sub));
  },
});
