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
 * @fileoverview Polyfills for ES6 Math functions.
 */

$jscomp.math = $jscomp.math || {};


/**
 * Counts the leading zeros in the 32-bit binary representation.
 *
 * <p>Polyfills the static function Math.clz32().
 *
 * @param {*} x Any number, or value that can be coerced to a number.
 * @return {number} The number of leading zero bits.
 */
$jscomp.math.clz32 = function(x) {
  // This binary search algorithm is taken from v8.
  x = Number(x) >>> 0;  // first ensure we have a 32-bit unsigned integer.
  if (x === 0) return 32;
  let result = 0;
  if ((x & 0xFFFF0000) === 0) {
    x <<= 16;
    result += 16;
  }
  if ((x & 0xFF000000) === 0) {
    x <<= 8;
    result += 8;
  }
  if ((x & 0xF0000000) === 0) {
    x <<= 4;
    result += 4;
  }
  if ((x & 0xC0000000) === 0) {
    x <<= 2;
    result += 2;
  }
  if ((x & 0x80000000) === 0) result++;
  return result;
};


/**
 * Performs C-like 32-bit signed integer multiplication.
 *
 * <p>Polyfills the static function Math.imul().
 *
 * @param {*} a Any number, or value that can be coerced to a number.
 * @param {*} b Any number, or value that can be coerced to a number.
 * @return {number} The 32-bit integer product of a and b.
 */
$jscomp.math.imul = function(a, b) {
  // This algorithm is taken from v8.
  // Note: If multiplication overflows 32 bits, then we risk losing
  // precision.  We must therefore break the inputs into 16-bit
  // words and multiply separately.
  a = Number(a);
  b = Number(b);
  const ah = (a >>> 16) & 0xFFFF;  // Treat individual words as unsigned
  const al = a & 0xFFFF;
  const bh = (b >>> 16) & 0xFFFF;
  const bl = b & 0xFFFF;
  const lh = ((ah * bl + al * bh) << 16) >>> 0;  // >>> 0 casts to uint
  return (al * bl + lh) | 0;  // | 0 casts back to signed
};


/**
 * Returns the sign of the number, indicating whether it is
 * positive, negative, or zero.
 *
 * <p>Polyfills the static function Math.sign().
 *
 * @param {*} x Any number, or value that can be coerced to a number.
 * @return {number} The sign, +1 if x is positive, -1 if x is
 *     negative, or 0 if x is zero.
 */
$jscomp.math.sign = function(x) {
  x = Number(x);
  return x === 0 || isNaN(x) ? x : x > 0 ? 1 : -1;
};


/**
 * Returns the base-10 logarithm.
 *
 * <p>Polyfills the static function Math.log10().
 *
 * @param {*} x Any number, or value that can be coerced to a number.
 * @return {number} The common log of x.
 */
$jscomp.math.log10 = function(x) {
  return Math.log(x) / Math.LN10;
};


/**
 * Returns the base-2 logarithm.
 *
 * <p>Polyfills the static function Math.log2().
 *
 * @param {*} x Any number, or value that can be coerced to a number.
 * @return {number} The base-2 log of x.
 */
$jscomp.math.log2 = function(x) {
  return Math.log(x) / Math.LN2;
};


/**
 * Returns the natural logarithm of 1+x, implemented in a way that is
 * accurate for numbers close to zero.
 *
 * <p>Polyfills the static function Math.log1p().
 *
 * @param {*} x Any number, or value that can be coerced to a number.
 * @return {number} The natural log of 1+x.
 */
$jscomp.math.log1p = function(x) {
  // This implementation is based on the Taylor expansion
  //   log(1 + x) ~ x - x^2/2 + x^3/3 - x^4/4 + x^5/5 - ...
  x = Number(x);
  if (x < 0.25 && x > -0.25) {
    let y = x;
    let d = 1;
    let z = x;
    let zPrev = 0;
    let s = 1;
    while (zPrev != z) {
      y *= x;
      s *= -1;
      z = (zPrev = z) + s * y / (++d);
    }
    return z;
  }
  return Math.log(1 + x);
};


/**
 * Exponentiates x and then subtracts one.  This is implemented in a
 * way that is accurate for numbers close to zero.
 *
 * <p>Polyfills the static function Math.expm1().
 *
 * @param {*} x Any number, or value that can be coerced to a number.
 * @return {number} The exponential of x, less 1.
 */
$jscomp.math.expm1 = function(x) {
  // This implementation is based on the Taylor expansion
  //   exp(x) ~ 1 + x + x^2/2 + x^3/6 + x^4/24 + ...
  x = Number(x);
  if (x < .25 && x > -.25) {
    let y = x;
    let d = 1;
    let z = x;
    let zPrev = 0;
    while (zPrev != z) {
      y *= x / (++d);
      z = (zPrev = z) + y;
    }
    return z;
  }
  return Math.exp(x) - 1;
};


/**
 * Computes the hyperbolic cosine.
 *
 * <p>Polyfills the static function Math.cosh().
 *
 * @param {*} x Any number, or value that can be coerced to a number.
 * @return {number} The hyperbolic cosine of x.
 */
$jscomp.math.cosh = function(x) {
  x = Number(x);
  return (Math.exp(x) + Math.exp(-x)) / 2;
};


/**
 * Computes the hyperbolic sine.
 *
 * <p>Polyfills the static function Math.sinh().
 *
 * @param {*} x Any number, or value that can be coerced to a number.
 * @return {number} The hyperbolic sine of x.
 */
$jscomp.math.sinh = function(x) {
  x = Number(x);
  if (x === 0) return x;
  return (Math.exp(x) - Math.exp(-x)) / 2;
};


/**
 * Computes the hyperbolic tangent.
 *
 * <p>Polyfills the static function Math.tanh().
 *
 * @param {*} x Any number, or value that can be coerced to a number.
 * @return {number} The hyperbolic tangent of x.
 */
$jscomp.math.tanh = function(x) {
  x = Number(x);
  if (x === 0) return x;
  // Ensure exponent is negative to prevent overflow.
  const y = Math.exp(2 * -Math.abs(x));
  const z = (1 - y) / (1 + y);
  return x < 0 ? -z : z;
};


/**
 * Computes the inverse hyperbolic cosine.
 *
 * <p>Polyfills the static function Math.acosh().
 *
 * @param {*} x Any number, or value that can be coerced to a number.
 * @return {number} The inverse hyperbolic cosine of x.
 */
$jscomp.math.acosh = function(x) {
  x = Number(x);
  return Math.log(x + Math.sqrt(x * x - 1));
};


/**
 * Computes the inverse hyperbolic sine.
 *
 * <p>Polyfills the static function Math.asinh().
 *
 * @param {*} x Any number, or value that can be coerced to a number.
 * @return {number} The inverse hyperbolic sine of x.
 */
$jscomp.math.asinh = function(x) {
  x = Number(x);
  if (x === 0) return x;
  const y = Math.log(Math.abs(x) + Math.sqrt(x * x + 1));
  return x < 0 ? -y : y;
};


/**
 * Computes the inverse hyperbolic tangent.
 *
 * <p>Polyfills the static function Math.atanh().
 *
 * @param {*} x Any number, or value that can be coerced to a number.
 * @return {number} The inverse hyperbolic tangent of x.
 */
$jscomp.math.atanh = function(x) {
  x = Number(x);
  return ($jscomp.math.log1p(x) - $jscomp.math.log1p(-x)) / 2;
};


/**
 * Returns the sum of its arguments in quadrature.
 *
 * <p>Polyfills the static function Math.hypot().
 *
 * @param {*} x Any number, or value that can be coerced to a number.
 * @param {*} y Any number, or value that can be coerced to a number.
 * @param {...*} rest More numbers.
 * @return {number} The square root of the sum of the squares.
 */
$jscomp.math.hypot = function(x, y, ...rest) {
  // Make the type checker happy.
  x = Number(x);
  y = Number(y);
  // Note: we need to normalize the numbers in case of over/underflow.
  let max = Math.max(Math.abs(x), Math.abs(y));
  for (let z of rest) {
    max = Math.max(max, Math.abs(z));
  }
  if (max > 1e100 || max < 1e-100) {
    x = x / max;
    y = y / max;
    let sum = x * x + y * y;
    for (let z of rest) {
      z = Number(z) / max;
      sum += z * z;
    }
    return Math.sqrt(sum) * max;
  } else {
    let sum = x * x + y * y;
    for (let z of rest) {
      z = Number(z);
      sum += z * z;
    }
    return Math.sqrt(sum);
  }
};


/**
 * Truncates any fractional digits from its argument (towards zero).
 *
 * <p>Polyfills the static function Math.trunc().
 *
 * @param {*} x Any number, or value that can be coerced to a number.
 * @return {number}
 */
$jscomp.math.trunc = function(x) {
  x = Number(x);
  if (isNaN(x) || x === Infinity || x === -Infinity || x === 0) return x;
  const y = Math.floor(Math.abs(x));
  return x < 0 ? -y : y;
};


/**
 * Returns the cube root of the number, handling negatives safely.
 *
 * <p>Polyfills the static function Math.cbrt().
 *
 * @param {*} x Any number, or value that can be coerced into a number.
 * @return {number} The cube root of x.
 */
$jscomp.math.cbrt = function(x) {
  if (x === 0) return x;
  x = Number(x);
  const y = Math.pow(Math.abs(x), 1 / 3);
  return x < 0 ? -y : y;
};
