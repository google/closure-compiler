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

goog.module('$jscomp_array_test');
goog.setTestOnly();

const jsunit = goog.require('goog.testing.jsunit');
const testSuite = goog.require('goog.testing.testSuite');
const testing = goog.require('testing');
const userAgent = goog.require('goog.userAgent');

const assertDeepEquals = testing.assertDeepEquals;

/**
 * Asserts the contents of an iterator.
 * @param {!Iterator} iterator
 * @param {...*} expected
 */
function assertIteratorContents(iterator, ...expected) {
  let i = 0;
  for (let elem of expected) {
    assertDeepEquals({value: elem, done: false}, iterator.next(), `[${i++}]`);
  }
  assertTrue(iterator.next().done);
  assertTrue(iterator.next().done);
}

/**
 * Extracts the keys of an object (effectively Object.keys).
 * @param {!Object} obj
 * @return {!Array} The keys of the object, as an array.
 */
function getKeys(obj) {
  const keys = [];
  for (let key in obj) {
    if (obj.hasOwnProperty(key)) keys.push(key);
  }
  keys.sort(); // IE doesn't sort array indexes!
  return keys;
}

/**
 * Builds an iterable from the given objects.
 * @param {...*} args
 * @return {!Iteratble} An iterable.
 */
function iterable(...args) {
  return {
    [Symbol.iterator]() { return iterator(...args); }
  };
}

/**
 * Builds an iterator from the given objects.
 * @param {...*} args
 */
function *iterator(...args) {
  for (let arg of args) {
    yield arg;
  }
}

testSuite({
  shouldRunTests() {
    // Disable tests for IE8 and below.
    return !userAgent.IE || userAgent.isVersionOrHigher(9);
  },

  testFrom() {
    const arr = ['a', 2, 'c'];
    assertNotEquals(arr, Array.from(arr));
    assertDeepEquals(arr, Array.from(arr));

    assertDeepEquals(['a', void 0, 42], Array.from({length: 3, 0: 'a', 2: 42}));
    assertDeepEquals(['a', void 0], Array.from({length: 2, 0: 'a', 2: 42}));
    assertDeepEquals(['a', 'c', 'b'], Array.from('acb'));
    assertDeepEquals(['a', 'c', 'b'], Array.from('acb'));

    (function() {
      assertDeepEquals(['x', 'y'], Array.from(arguments));
    })('x', 'y');

    assertDeepEquals([1, 4], Array.from(iterator(1, 4)));
    assertDeepEquals(['x', 'y'], Array.from(iterable('x', 'y')));

    const x2 = x => x + x;
    assertDeepEquals([2, 4, 8], Array.from([1, 2, 4], x2));
    assertDeepEquals(['aa', 'cc', 'bb'], Array.from('acb', x2));
    assertDeepEquals([6], Array.from({length: 1, 0: 3}, x2));
    assertDeepEquals([6, 10], Array.from(iterator(3, 5), x2));
    assertDeepEquals([6, 'xx'], Array.from(iterable(3, 'x'), x2));

    const applyThisTwice = function(x) { return this(this(x)); };
    assertDeepEquals([4, 8, 16], Array.from([1, 2, 4], applyThisTwice, x2));
    assertDeepEquals(['aaaa'], Array.from('a', applyThisTwice, x2));
  },

  testOf() {
    assertDeepEquals([1, 'x', void 0], Array.of(1, 'x', void 0));
  },

  testEntries() {
    assertIteratorContents(['x', 'c'].entries(), [0, 'x'], [1, 'c']);

    const arr = new Array();
    arr[3] = 42;
    assertIteratorContents(arr.entries(),
                           [0, void 0], [1, void 0], [2, void 0], [3, 42]);

    assertIteratorContents([].entries.call({length: 3, 1: 'c'}),
                           [0, void 0], [1, 'c'], [2, void 0]);

    assertIteratorContents(Array.prototype.entries.call('xc'),
                           [0, 'x'], [1, 'c']);
  },

  testKeys() {
    assertIteratorContents(['y', 'q'].keys(), 0, 1);

    const arr = new Array();
    arr[3] = 42;
    assertIteratorContents(arr.keys(), 0, 1, 2, 3);

    assertIteratorContents(Array.prototype.keys.call('xc'), 0, 1);
    assertIteratorContents([].keys.call({length: 3, 1: 'c'}), 0, 1, 2);
  },

  testValues() {
    assertIteratorContents(['x', 'c'].values(), 'x', 'c');

    const arr = new Array();
    arr[3] = 42;
    assertIteratorContents(arr.values(), void 0, void 0, void 0, 42);

    assertIteratorContents([].values.call({length: 3, 1: 'x'}),
                           void 0, 'x', void 0);

    assertIteratorContents(Array.prototype.values.call('yq'), 'y', 'q');
  },

  testCopyWithin() {
    let arr = [5, 4, 3, 2, 1, 0];
    assertEquals(arr, arr.copyWithin(0, 3, 5));
    assertDeepEquals([2, 1, 3, 2, 1, 0], arr);

    arr = [5, 4, 3, 2, 1, 0];
    assertEquals(arr, arr.copyWithin(0, 3));
    assertDeepEquals([2, 1, 0, 2, 1, 0], arr);

    arr = [5, 4, 3, 2, 1, 0];
    assertEquals(arr, arr.copyWithin(3, 1, 3));
    assertDeepEquals([5, 4, 3, 4, 3, 0], arr);

    arr = [5, 4, 3, 2, 1, 0];
    assertEquals(arr, arr.copyWithin(3, 1));
    assertDeepEquals([5, 4, 3, 4, 3, 2], arr);

    arr = [5, 4, 3, 2, 1, 0];
    assertEquals(arr, arr.copyWithin(1, 0));
    assertDeepEquals([5, 5, 4, 3, 2, 1], arr);

    arr = [5, 4, 3, 2, 1, 0];
    assertEquals(arr, arr.copyWithin(0, 1));
    assertDeepEquals([4, 3, 2, 1, 0, 0], arr);

    arr = [];
    arr[4] = 42;
    arr[2] = 21;
    assertEquals(arr, arr.copyWithin(0, 1));
    assertDeepEquals(['1', '3', '4'], getKeys(arr));

    arr = {length: 3, 1: 4, 3: 'unused'};
    assertEquals(arr, Array.prototype.copyWithin.call(arr, 0, 1));
    assertDeepEquals({length: 3, 0: 4, 3: 'unused'}, arr);
  },

  testFill() {
    let arr = [];
    arr[6] = 42;
    assertEquals(arr, arr.fill('x', 2, 5));
    assertDeepEquals([,, 'x', 'x', 'x',, 42], arr);

    assertEquals(arr, arr.fill('y', 4));
    assertDeepEquals([,, 'x', 'x', 'y', 'y', 'y'], arr);

    assertEquals(arr, arr.fill('z'));
    assertDeepEquals(['z', 'z', 'z', 'z', 'z', 'z', 'z'], arr);

    arr = {length: 3, 1: 'x', 3: 'safe'};
    assertEquals(arr, Array.prototype.fill.call(arr, 'y'));
    assertDeepEquals({0: 'y', 1: 'y', 2: 'y', 3: 'safe', length: 3}, arr);

    assertEquals(arr, Array.prototype.fill.call(arr, 'z', void 0, 2));
    assertDeepEquals({0: 'z', 1: 'z', 2: 'y', 3: 'safe', length: 3}, arr);

    arr = {2: 'x'}; // does nothing if no length
    assertEquals(arr, Array.prototype.fill.call(arr, 'y', 0, 4));
  },

  testFind() {
    let arr = [1, 2, 3, 4, 5];
    assertEquals(2, arr.find(x => x % 2 == 0));
    assertEquals(4, arr.find(x => x > 3));
    assertEquals(1, arr.find(() => true));
    assertUndefined(arr.find(() => false));

    arr = ['x', 'y', 'z', 'w'];
    assertEquals('y', arr.find((_, i) => i == 1));
    arr[2] = arr;
    assertEquals(arr, arr.find((x, _, a) => x == a));

    arr = ['xx', 'yy', 'zz', 'ww'];
    const checkFirst = function(x) { return x[0] == this.first; };
    assertEquals('ww', arr.find(checkFirst, {first: 'w'}));

    arr = {5: 42, 2: 23, 6: 100, length: 6};
    assertEquals(42, Array.prototype.find.call(arr, x => x > 30));
    assertUndefined(Array.prototype.find.call(arr, x => x > 50));

    arr = 'abcABC';
    assertEquals(
        'A', Array.prototype.find.call(arr, x => x == x.toUpperCase()));
  },

  testFindIndex() {
    let arr = [1, 2, 3, 4, 5];
    assertEquals(1, arr.findIndex(x => x % 2 == 0));
    assertEquals(3, arr.findIndex(x => x > 3));
    assertEquals(0, arr.findIndex(() => true));
    assertEquals(-1, arr.findIndex(() => false));

    arr = ['x', 'y', 'z', 'w'];
    assertEquals(1, arr.findIndex((_, i) => i == 1));
    arr[2] = arr;
    assertEquals(2, arr.findIndex((x, _, a) => x == a));

    arr = ['xx', 'yy', 'zz', 'ww'];
    const checkFirst = function(x) { return x[0] == this.first; };
    assertEquals(3, arr.findIndex(checkFirst, {first: 'w'}));

    arr = {5: 42, 2: 23, 6: 100, length: 6};
    assertEquals(5, Array.prototype.findIndex.call(arr, x => x > 30));
    assertEquals(-1, Array.prototype.findIndex.call(arr, x => x > 50));

    arr = 'abcABC';
    assertEquals(
        3, Array.prototype.findIndex.call(arr, x => x == x.toUpperCase()));
  },
});
