/*
 * Copyright 2015 The Closure Compiler Authors.
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
 * @fileoverview Polyfills for ES6 Object functions.
 */

$jscomp.object = $jscomp.object || {};


/**
 * Polyfill for Object.assign() method:
 * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/assign
 *
 * Copies values of all enumerable own properties from one or more
 * sources to the given target object, and returns the target.
 * @param {!Object} target The target object onto which to copy.
 * @param {...?Object} sources The source objects.
 * @return {!Object} The target object is returned.
 */
$jscomp.object.assign = function(target, ...sources) {
  for (const source of sources) {
    if (!source) continue;
    for (const key in source) {
      // Note: it's possible that source.hasOwnPropery was overwritten,
      // so call the version on Object.prototype just to be sure.
      if (Object.prototype.hasOwnProperty.call(source, key)) {
        target[key] = source[key];
      }
    }
  }
  return target;
};


/**
 * Polyfill for Object.is() method:
 * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/is
 *
 * Determines whether two values are the same value (that is,
 * functionally equivalent).  This is the same as ===-equality,
 * except for two cases: 0 is not the same as -0, and NaN is
 * the same as NaN.
 *
 * @param {*} left
 * @param {*} right
 * @return {boolean}
 */
$jscomp.object.is = function(left, right) {
  if (left === right) {
    // Handle the 0 === -0 exception
    return (left !== 0) || (1 / left === 1 / /** @type {number} */ (right));
  } else {
    // Handle the NaN !== NaN exception
    return (left !== left) && (right !== right);
  }
};
