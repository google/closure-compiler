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

goog.module('jscomp.runtime_tests.polyfill_tests.testing');
goog.setTestOnly();

/** @suppress {extraRequire} */
var asserts = goog.require('goog.testing.asserts');


/**
 * Asserts deep equality for objects and arrays.
 * @param {*} expected
 * @param {*} actual
 * @param {string=} opt_position
 */
exports.assertDeepEquals = function(expected, actual, opt_position) {
  var position = opt_position || '';
  position = position || '<actual>';
  assertEquals(position + ' instanceof Array',
      expected instanceof Array, actual instanceof Array);
  assertEquals(position + ' instanceof Object',
      expected instanceof Object, actual instanceof Object);
  if (expected instanceof Array && actual instanceof Array) {
    assertEquals(position + '.length', expected.length, actual.length);
    for (var i = 0; i < expected.length; i++) {
      exports.assertDeepEquals(
          expected[i], actual[i], position + '[' + i + ']');
    }
  } else if (expected instanceof Object && actual instanceof Object) {
    for (var key in expected) {
      assertTrue('Missing key: ' + position + '.' + key, key in actual);
      exports.assertDeepEquals(
          expected[key], actual[key], position + '.' + key);
    }
    for (key in actual) {
      assertTrue('Unexpected key: ' + position + '.' + key, key in expected);
    }
  } else {
    assertEquals(position, expected, actual);
  }
};


/**
 * Asserts the contents of an iterator.
 * @param {?} iterator
 * @param {...*} var_args Expected elements.
 */
exports.assertIteratorContents = function(iterator, var_args) {
  if (iterator[Symbol.iterator]) iterator = iterator[Symbol.iterator]();
  for (var i = 1; i < arguments.length; i++) {
    var elem = arguments[i];
    exports.assertDeepEquals(
        {value: elem, done: false}, iterator.next(), '[' + (i - 1) + ']');
  }
  assertTrue(iterator.next().done);
  assertTrue(iterator.next().done);
};


/**
 * Extracts the keys of an object (effectively Object.keys).
 * @param {!Object} obj
 * @return {!Array} The keys of the object, as an array.
 */
exports.getKeys = function(obj) {
  var keys = [];
  for (var key in obj) {
    if (obj.hasOwnProperty(key)) keys.push(key);
  }
  keys.sort(); // IE doesn't sort array indexes!
  return keys;
};


/**
 * Builds an iterable from the given objects.
 * @param {...*} var_args
 * @return {!Iterable} An iterable.
 */
exports.iterable = function(var_args) {
  var args = Array.prototype.slice.call(arguments);
  var out = {};
  // Note: we may not be transpiling this file, but if this method
  // is being called, then the test should already depend on Symbol,
  // so it should have been pulled in anyway.
  out[Symbol.iterator] = function() {
    return exports.iterator.apply(null, args);
  };
  return /** @type {!Iterable} */ (out);
};


/**
 * Builds an iterator from the given objects.
 * @param {...*} var_args
 * @return {!Iterator}
 */
exports.iterator = function(var_args) {
  var i = -1;
  var args = Array.prototype.slice.call(arguments);
  var out = {
    next: function() {
      return ++i < args.length ? {value: args[i], done: false} : {done: true};
    }
  };
  out[Symbol.iterator] = function() {
    return out;
  };
  return /** @type {!Iterator} */ (out);
};


// Note: jsunit's assertNaN doesn't ensure it's a number.
/** @param {*} x Number to assert is exactly NaN. */
exports.assertExactlyNaN = function(x) {
  assertTrue('Expected NaN: ' + x, typeof x === 'number' && isNaN(x));
};


/** @param {*} x Number to assert is exactly positive zero. */
exports.assertPositiveZero = function(x) {
  assertTrue('Expected +0: ' + x, x === 0 && 1 / x == Infinity);
};


/** @param {*} x Number to assert is exactly negative zero. */
exports.assertNegativeZero = function(x) {
  assertTrue('Expected -0: ' + x, x === 0 && 1 / x == -Infinity);
};


// Note: jsunit's assertThrows doesn't take a constructor
/**
 * Asserts that the function fails with the given type of error.
 * @param {function(new: T)} expectedError
 * @param {function()} func
 * @template T
 */
exports.assertFails = function(expectedError, func) {
  try {
    func();
  } catch (err) {
    if (err instanceof expectedError) {
      return;
    }
    fail('Wrong exception type: expected ' + expectedError + ' but got ' + err);
  }
  fail('Expected to throw but didn\'t');
};


/**
 * Suppreses type checking by turning a known type into an unknown type.
 * @param {*} input
 * @return {?} The input.
 */
exports.noCheck = function(input) {
  return input;
};
