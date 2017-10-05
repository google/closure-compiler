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
 * @fileoverview Test cases for string_indexof_to_includes RefasterJs template.
 * @suppress {unusedLocalVariables}
 */

goog.provide('refactoring_testcase');


/**
 * @param {string} str The string.
 * @param {string} subStr The sub-string to check.
 * @return {boolean} Whether the sub-string is found the string.
 */
refactoring_testcase.not_equals_minus_one = function(str, subStr) {
  const strA = 'abc';
  const foundA = strA.includes('b');

  const foundB = 'xyz'.includes('x');

  return str.includes(subStr);
};

/**
 * @param {string} str The string.
 * @param {string} subStr The sub-string to check.
 * @return {boolean} Whether the sub-string is found the string.
 */
refactoring_testcase.not_strongly_equals_minus_one = function(str, subStr) {
  const strA = 'abc';
  const foundA = strA.includes('b');

  const foundB = 'xyz'.includes('x');

  return str.includes(subStr);
};

/**
 * @param {string} str The string.
 * @param {string} subStr The sub-string to check.
 * @return {boolean} Whether the sub-string is found the string.
 */
refactoring_testcase.greater_than_minus_one = function(str, subStr) {
  const strA = 'abc';
  const foundA = strA.includes('b');

  const foundB = 'xyz'.includes('x');

  return str.includes(subStr);
};

/**
 * @param {string} str The string.
 * @param {string} subStr The sub-string to check.
 * @return {boolean} Whether the sub-string is found the string.
 */
refactoring_testcase.greater_than_or_equals_zero = function(str, subStr) {
  const strA = 'abc';
  const foundA = strA.includes('b');

  const foundB = 'xyz'.includes('x');

  return str.includes(subStr);
};

/**
 * @param {string} str The string.
 * @param {string} subStr The sub-string to check.
 * @return {boolean} Whether the sub-string is found the string.
 */
refactoring_testcase.equals_minus_one = function(str, subStr) {
  const strA = 'abc';
  const foundA = !strA.includes('b');

  const foundB = !'xyz'.includes('x');

  return !str.includes(subStr);
};

/**
 * @param {string} str The string.
 * @param {string} subStr The sub-string to check.
 * @return {boolean} Whether the sub-string is found the string.
 */
refactoring_testcase.strongly_equals_minus_one = function(str, subStr) {
  const strA = 'abc';
  const foundA = !strA.includes('b');

  const foundB = !'xyz'.includes('x');

  return !str.includes(subStr);
};

/**
 * @param {string} str The string.
 * @param {string} subStr The sub-string to check.
 * @return {boolean} Whether the sub-string is found the string.
 */
refactoring_testcase.less_than_zero = function(str, subStr) {
  const strA = 'abc';
  const foundA = !strA.includes('b');

  const foundB = !'xyz'.includes('x');

  return !str.includes(subStr);
};

/**
 * @param {string} str The string.
 * @param {string} subStr The sub-string to check.
 * @return {number|boolean} Whether the sub-string is found the string.
 *     Returns true/truthy if the value is found, false/falsy otherwise.
 */
refactoring_testcase.bitwise_not = function(str, subStr) {
  const strA = 'abc';
  const foundA = strA.includes('b');

  const foundB = 'xyz'.includes('x');

  return str.includes(subStr);
};

/**
 * The refactoring should ignore calls to indexOf on non-array objects.
 * @param {string} str A string.
 * @param {string} subStr The substring to check.
 * @return {boolean} Whether the substring is in the array.
 */
refactoring_testcase.ignore_non_strings = function(str, subStr) {
  // Array.prototype.indexOf.
  const arrA = ['a', 'b', 'c'];
  arrA.indexOf('z');
  const indexOfZ = ['x', 'y', 'z'].indexOf('z') == 2;

  // method in object.
  const objA = {indexOf: function(a) {}};
  objA.indexOf('');

  // shorthand method names.
  const objB = {indexOf(a) {}};
  objB.indexOf('');

  return str.includes(subStr);
};
