/*
 * Copyright 2017 The Closure Compiler Authors.
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
 * @fileoverview Test cases for array_indexof_to_includes RefasterJs template.
 * @suppress {unusedLocalVariables}
 */

goog.provide('refactoring_testcase');


/**
 * @param {!Array} arr The array.
 * @param {string} elem The element to check.
 * @return {boolean} Whether the element is in the array.
 */
refactoring_testcase.not_equals_minus_one = function(arr, elem) {
  const arrA = [3];
  const foundA = arrA.includes(1);

  const arrB = [];
  arrB.push('str');
  const foundB = arrB.includes('s');

  return arr.includes(elem);
};

/**
 * @param {!Array} arr The array.
 * @param {string} elem The element to check.
 * @return {boolean} Whether the element is in the array.
 */
refactoring_testcase.not_strongly_equals_minus_one = function(arr, elem) {
  const arrA = [3];
  const foundA = arrA.includes(1);

  const arrB = [];
  arrB.push('str');
  const foundB = arrB.includes('s');

  return arr.includes(elem);
};

/**
 * @param {!Array} arr The array.
 * @param {string} elem The element to check.
 * @return {boolean} Whether the element is in the array.
 */
refactoring_testcase.greater_than_minus_one = function(arr, elem) {
  const arrA = [3];
  const foundA = arrA.includes(1);

  const arrB = [];
  arrB.push('str');
  const foundB = arrB.includes('s');

  return arr.includes(elem);
};

/**
 * @param {!Array} arr The array.
 * @param {string} elem The element to check.
 * @return {boolean} Whether the element is in the array.
 */
refactoring_testcase.greater_than_or_equals_zero = function(arr, elem) {
  const arrA = [3];
  const foundA = arrA.includes(1);

  const arrB = [];
  arrB.push('str');
  const foundB = arrB.includes('s');

  return arr.includes(elem);
};

/**
 * @param {!Array} arr The array.
 * @param {string} elem The element to check.
 * @return {boolean} Whether the element is in the array.
 */
refactoring_testcase.equals_minus_one = function(arr, elem) {
  const arrA = [3];
  const foundA = !arrA.includes(1);

  const arrB = [];
  arrB.push('str');
  const foundB = !arrB.includes('s');

  return !arr.includes(elem);
};

/**
 * @param {!Array} arr The array.
 * @param {string} elem The element to check.
 * @return {boolean} Whether the element is in the array.
 */
refactoring_testcase.strongly_equals_minus_one = function(arr, elem) {
  const arrA = [3];
  const foundA = !arrA.includes(1);

  const arrB = [];
  arrB.push('str');
  const foundB = !arrB.includes('s');

  return !arr.includes(elem);
};

/**
 * @param {!Array} arr The array.
 * @param {string} elem The element to check.
 * @return {boolean} Whether the element is in the array.
 */
refactoring_testcase.less_than_zero = function(arr, elem) {
  const arrA = [3];
  const foundA = !arrA.includes(1);

  const arrB = [];
  arrB.push('str');
  const foundB = !arrB.includes('s');

  return !arr.includes(elem);
};

/**
 * @param {!Array} arr The array.
 * @param {string} elem The element to check.
 * @return {number|boolean} Whether the element is in the array.
 *     Returns true/truthy if the value is found, false/falsy otherwise.
 */
refactoring_testcase.bitwise_not = function(arr, elem) {
  const arrA = [3];
  const foundA = arrA.includes(1);

  const arrB = [];
  arrB.push('str');
  const foundB = arrB.includes('s');

  return arr.includes(elem);
};

/**
 * The refactoring should ignore calls to indexOf on non-array objects.
 * @param {string} str A string.
 * @param {string} subStr The substring to check.
 * @return {boolean} Whether the substring is in the array.
 */
refactoring_testcase.ignore_non_arrays = function(str, subStr) {
  const strA = 'abc';
  strA.indexOf('z');
  const indexOfZ = 'xyz'.indexOf('z') == 2;

  // method in object.
  const objA = {indexOf: function(a) {}};
  objA.indexOf('');

  // shorthand method names.
  const objB = {indexOf(a) {}};
  objB.indexOf('');

  return str.indexOf(subStr) != -1;
};
