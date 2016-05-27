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
 * @fileoverview Bootstrap code for GWT/J2CL.
 */


/** @const */
var util = {};


/**
 * @param {!Array<T>} arr
 * @param {number} index
 * @return {T|undefined}
 * @template T
 */
util.arrayGet = function(arr, index) {
  return arr[index];
};


/**
 * @param {!Array<T>} arr
 * @param {number} index
 * @param {T} elem
 * @template T
 */
util.arraySet = function(arr, index, elem) {
  arr[index] = elem;
};


/**
 * @param {!Object<T>} obj
 * @param {string} key
 * @return {T|undefined}
 * @template T
 */
util.objectGet = function(obj, key) {
  return obj[key];
};


/**
 * @param {!Object<T>} obj
 * @param {string} key
 * @param {T} value
 * @template T
 */
util.objectSet = function(obj, key, value) {
  obj[key] = value;
};
