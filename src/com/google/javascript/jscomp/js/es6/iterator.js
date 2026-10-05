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

/**
 * @fileoverview Polyfill for the ES2025 Iterator base class.
 */
'require es6/symbol';
'require util/defineproperty';
'require util/polyfill';

$jscomp.polyfill('Iterator', function(localIterator) {
  // 1. Climb the native prototype chain to find nameless %IteratorPrototype%
  var getPrototypeOf = Object.getPrototypeOf;
  var arrIterator = typeof Symbol != 'undefined' && Symbol.iterator && [][Symbol.iterator] ?
      [][Symbol.iterator]() : null;
  var ArrayIteratorProto = (arrIterator && getPrototypeOf) ? getPrototypeOf(arrIterator) : null;
  var IteratorPrototype = (ArrayIteratorProto && getPrototypeOf) ? getPrototypeOf(ArrayIteratorProto) : null;
  if (IteratorPrototype === Object.prototype) {
    IteratorPrototype = null;
  }

  // Older SpiderMonkey engines (e.g. Cobalt 9) define a legacy non-standard
  // global `Iterator(obj)` function whose prototype is not %IteratorPrototype%.
  if (localIterator && IteratorPrototype && localIterator.prototype === IteratorPrototype) {
    return localIterator;
  }

  // 2. Define abstract constructor (ES3-safe check)
  var Iterator = function() {
    if (!(this instanceof Iterator) || this.constructor === Iterator) {
      throw new TypeError("Abstract class Iterator not directly constructable");
    }
  };

  // 3. Link class prototype to the native nameless %IteratorPrototype% when
  // available (ES6+ engines). Otherwise (in ES3/ES5 or when Symbol.iterator is
  // polyfilled, where `[][Symbol.iterator]()` returns a plain object inheriting
  // directly from Object.prototype), keep the default `Iterator.prototype`
  // object inheriting from Object.prototype.
  if (IteratorPrototype) {
    Iterator.prototype = IteratorPrototype;
  }
  $jscomp.defineProperty(Iterator.prototype, 'constructor', {
    configurable: true,
    writable: true,
    value: Iterator,
  });
  // Per the ECMAScript spec (%IteratorPrototype%[@@iterator]),
  // Iterator.prototype defines a `[Symbol.iterator]()` method that returns
  // `this` so that all Iterator instances (and subclasses) are also iterables
  // usable in `for..of`, spread, etc. Native %IteratorPrototype% already has
  // this method, so we only need to define it in the fallback case.
  if (!Iterator.prototype[Symbol.iterator]) {
    Iterator.prototype[Symbol.iterator] = function() {
      return this;
    };
  }
  return Iterator;
}, 'es_next', 'es3');
