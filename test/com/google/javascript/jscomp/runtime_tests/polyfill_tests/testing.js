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
var userAgent = goog.require('goog.userAgent');

/** @const */
var IE8 = userAgent.IE && !userAgent.isVersionOrHigher(9);


/** @const {function(?Object): !Object} Version of Object.create for IE8. */
exports.objectCreate =
    typeof Object.create == 'function' ?
    Object.create :
    function(proto) {
      /** @constructor */
      function Ctor() {}
      Ctor.prototype = exports.noCheck(proto);
      return new Ctor();
    };


/**
 * Asserts that an array of property names are the same.
 * If the browser is IE8, sorts the lists first, since IE8
 * does not sort them correctly.
 * @param {!Array} expected
 * @param {!Array} actual
 */
exports.assertPropertyListEquals = function(expected, actual) {
  if (IE8) {
    expected = expected.slice().sort();
    actual = actual.slice().sort();
  }
  assertObjectEquals(expected, actual);
};


/**
 * Asserts the contents of an iterator.
 * @param {?} iterator
 * @param {...*} var_args Expected elements.
 */
exports.assertIteratorContents = function(iterator, var_args) {
  if (iterator[Symbol.iterator]) iterator = iterator[Symbol.iterator]();
  var expected = [];
  var actual = [];
  for (var i = 1; i < arguments.length; i++) {
    var elem = arguments[i];
    expected.push({value: arguments[i], done: false});
    actual.push(iterator.next());
  }
  function normalizeDone(result) {
    // Normalize done values so that assertObjectEquals always passes for them.
    return result.done ? {value: undefined, done: true} : result;
  }
  expected.push({value: undefined, done: true}, {value: undefined, done: true});
  actual.push(normalizeDone(iterator.next()), normalizeDone(iterator.next()));
  assertObjectEquals(expected, actual);
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
