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
 * @fileoverview Polyfills for ES6 Number functions.
 */

$jscomp.number = $jscomp.number || {};


/**
 * Returns whether the given argument is a finite number.
 *
 * <p>Polyfills the static function Number.isFinite().
 *
 * @param {*} x Any value.
 * @return {boolean} True if x is a number and not NaN or infinite.
 */
$jscomp.number.isFinite = function(x) {
  if (typeof x !== 'number') return false;
  return !isNaN(x) && x !== Infinity && x !== -Infinity;
};


/**
 * Returns whether the given argument is an integer.
 *
 * <p>Polyfills the static function Number.isInteger().
 *
 * @param {*} x Any value.
 * @return {boolean} True if x is an integer.
 */
$jscomp.number.isInteger = function(x) {
  if (!$jscomp.number.isFinite(x)) return false;
  return x === Math.floor(x);
};


/**
 * Returns whether the given argument is the value NaN,
 * guaranteeing not to coerce to a number first.
 *
 * <p>Polyfills the static function Number.isNaN().
 *
 * @param {*} x Any value.
 * @return {boolean} True if x is exactly NaN.
 */
$jscomp.number.isNaN = function(x) {
  return typeof x === 'number' && isNaN(x);
};


/**
 * Returns whether the given argument is a "safe" integer,
 * that is, its magnitude is less than 2^53.
 *
 * <p>Polyfills the static function Number.isSafeInteger().
 *
 * @param {*} x Any value.
 * @return {boolean} True if x is a safe integer.
 */
$jscomp.number.isSafeInteger = function(x) {
  return $jscomp.number.isInteger(x) &&
      Math.abs(x) <= $jscomp.number.MAX_SAFE_INTEGER;
};


/**
 * The difference 1 and the smallest number greater than 1.
 *
 * <p>Polyfills the static field Number.EPSILON.
 *
 * @const {number}
 */
$jscomp.number.EPSILON = Math.pow(2, -52);


/**
 * The maximum safe integer, 2^53 - 1.
 *
 * <p>Polyfills the static field Number.MAX_SAFE_INTEGER.
 *
 * @const {number}
 */
$jscomp.number.MAX_SAFE_INTEGER = 0x1fffffffffffff;


/**
 * The minimum safe integer, -(2^53 - 1).
 *
 * <p>Polyfills the static field Number.MIN_SAFE_INTEGER.
 *
 * @const {number}
 */
$jscomp.number.MIN_SAFE_INTEGER = -0x1fffffffffffff;
