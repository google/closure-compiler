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
 * @fileoverview Tests that for/of works correctly in browsers that support
 * Map and Set.
 */
goog.require('goog.testing.jsunit');

function shouldRunTests() {
  return 'Map' in goog.global && 'Set' in goog.global &&
      'keys' in Map.prototype;
}

function testMap() {
  let m = new Map();
  m.set(1, 2);
  m.set(3, 4);

  let keys = [];
  for (let key of m.keys()) {
    keys.push(key);
  }
  assertArrayEquals([1, 3], keys);
}

function testSet() {
  let s = new Set();
  s.add(1);
  s.add(2);

  let values = [];
  for (let x of s) {
    values.push(x);
  }
  assertArrayEquals([1, 2], values);
}

function testSetValuesIterable() {
  let s = new Set();
  s.add(3);
  s.add(2);
  s.add(1);

  let values = [...s.values()];
  values.sort();
  assertArrayEquals([1, 2, 3], values);
}
