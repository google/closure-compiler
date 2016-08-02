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

/**
 * @fileoverview Tests for user-defined Symbols.
 */
goog.require('goog.testing.jsunit');

const s1 = Symbol('example');
const s2 = Symbol('example');
const s3 = Symbol();

/** @unrestricted */
const SymbolProps = class {
  [s1]() { return 's1'; }
  [s2]() { return 's2'; }
  [s3]() { return 's3'; }
}

function testSymbols() {
  const sp = new SymbolProps();
  assertEquals('s1', sp[s1]());
  assertEquals('s2', sp[s2]());
  assertEquals('s3', sp[s3]());
}

function testArrayIterator() {
  // Note: this test cannot pass in IE8 since we can't polyfill
  // Array.prototype methods and maintain correct for-in behavior.
  if (typeof Object.defineProperties !== 'function') return;

  const iter = [2, 4, 6][Symbol.iterator]();
  assertObjectEquals({value: 2, done: false}, iter.next());
  assertObjectEquals({value: 4, done: false}, iter.next());
  assertObjectEquals({value: 6, done: false}, iter.next());
  assertTrue(iter.next().done);
}
