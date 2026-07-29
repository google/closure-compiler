/*
 * Copyright 2026 The Closure Compiler Authors.
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

'require es6/weakmap';
'require util/global';

/**
 * Wrapper around WeakMap for private elements transpilation.
 * @constructor
 */
$jscomp.PrivateMap = function() {
  this.map = new WeakMap();
};

/**
 * @param {*} receiver
 * @return {boolean}
 */
$jscomp.PrivateMap.prototype.has = function(receiver) {
  if (!$jscomp.PrivateMap.isValidReceiver(receiver)) {
    throw new TypeError(
        'Cannot use \'in\' operator to search for private member in ' +
        receiver);
  }
  return this.map.has(receiver);
};

/**
 * @param {*} receiver
 * @return {*}
 */
$jscomp.PrivateMap.prototype.get = function(receiver) {
  var value;
  if (!$jscomp.PrivateMap.isValidReceiver(receiver) ||
      (value = this.map.get(receiver)) === undefined) {
    throw new TypeError(
        'Cannot read private member from an object whose class did not declare it');
  }
  return value;
};

/**
 * @param {*} receiver
 * @param {*} value
 * @return {!$jscomp.PrivateMap}
 */
$jscomp.PrivateMap.prototype.set = function(receiver, value) {
  // No receiver check needed: .set() is only emitted in constructors where
  // `this` is the receiver.
  this.map.set(receiver, value);
  return this;
};

/**
 * @param {*} receiver
 * @return {boolean}
 */
$jscomp.PrivateMap.isValidReceiver = function(receiver) {
  return receiver !== null &&
      (typeof receiver === 'object' || typeof receiver === 'function');
};