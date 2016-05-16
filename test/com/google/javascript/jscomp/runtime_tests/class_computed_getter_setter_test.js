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

let value = Symbol('value');

// For now, classes with computed properties need to be @unrestricted because
// we use [] access on them.
// TODO(tbreisacher): Allow *some* [] access (at least if the property is known
// to be a symbol) on @struct classes.

/** @unrestricted */
const GettableCounter = class {
  constructor() { this.x = 0; }
  get [value]() { return this.x++; }
};

function testGetter() {
  let c = new GettableCounter();
  assertEquals(0, c[value]);
  assertEquals(1, c[value]);
}

/** @unrestricted */
const SettableCounter = class {
  constructor() { this.x = 0; }
  set [value](val) {
    this.x = val;
  }
}

function testSetter() {
  let s = new SettableCounter();
  assertEquals(0, s.x);
  s[value] = 5;
  assertEquals(5, s.x);
  s[value] = 10;
  assertEquals(10, s.x);
}

/**
 * @unrestricted
 * @implements {Iterable<number>}
 */
const GetAndSet = class {
  constructor() {
    this.x = 0;
  }

  get [value]() {
    return this.x++;
  }

  set [value](val) {
    this.x = val;
  }

  [Symbol.iterator]() {
    return this;
  }

  next() {
    return {
      value: this[value],
      done: false,
    };
  }
};

function testGetAndSet() {
  var gs = new GetAndSet();
  var log = [];
  for (let i of gs) {
    log.push(i);
    if (i == 5) {
      break;
    }
  }
  gs[value] = 50;
  for (let i of gs) {
    log.push(i);
    if (i == 55) {
      break;
    }
  }

  assertArrayEquals([0, 1, 2, 3, 4, 5, 50, 51, 52, 53, 54, 55], log);
}
