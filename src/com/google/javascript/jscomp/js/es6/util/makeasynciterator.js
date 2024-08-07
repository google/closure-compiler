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
 * @fileoverview Polyfill for for-of loops.
 */
'require es6/symbol';
'require es6/util/makeiterator';

/**
 * Creates an iterator for the given iterable.
 *
 * @param {string|!AsyncIterable<T>|!Iterable<T>|!Iterator<T>|!Arguments} iterable
 * @return {!AsyncIteratorIterable<T>}
 * @template T
 * @suppress {reportUnknownTypes}
 */
$jscomp.makeAsyncIterator = function(iterable) {
  var asyncIteratorFunction = (iterable)[Symbol.asyncIterator];
  if (asyncIteratorFunction !== undefined) {
    return asyncIteratorFunction.call(iterable);
  }
  return new $jscomp.AsyncIteratorFromSyncWrapper($jscomp.makeIterator(
      /** @type {string|!Iterable<T>|!Iterator<T>|!Arguments} */
      (iterable)));
};

/**
 *
 * @param {!Iterator<T>} iterator
 * @constructor
 * @implements {AsyncIteratorIterable<T>}
 * @template T
 * @suppress {reportUnknownTypes}
 */
$jscomp.AsyncIteratorFromSyncWrapper = function(iterator) {
  /**
   * @return {!AsyncIterator<T>}
   */
  this[Symbol.asyncIterator] = function() {
    return this;
  };

  /**
   * @return {!Iterator<!Promise<!IIterableResult<T>>>}
   */
  this[Symbol.iterator] = function() {
    return iterator;
  };

  /**
   * @param {?=} param
   * @return {!Promise<!IIterableResult<T>>}
   */
  this.next = function(param) {
    return Promise.resolve(iterator.next(param));
  };

  /**
   * @param {?} param
   * @return {!Promise<!IIterableResult<T>>}
   */
  this['throw'] = function(param) {
    return new Promise(function(resolve, reject) {
      var rethrow = iterator['throw'];
      if (rethrow !== undefined) {
        resolve(rethrow.call(iterator, param));
      } else {
        var close = iterator['return'];
        if (close !== undefined) close.call(iterator);
        reject(new TypeError('no `throw` method'));
      }
    });
  };

  if (iterator['return'] !== undefined) {
    /**
     * @param {T} param
     * @return {!Promise<!IIterableResult<T>>}
     */
    this['return'] = function(param) {
      return Promise.resolve(iterator['return'](param));
    };
  }
};
