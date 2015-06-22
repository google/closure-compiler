/*
 * Copyright 2014 The Closure Compiler Authors.
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
 * Runtime functions required for transpilation from ES6 to ES3.
 *
 * @author mattloring@google.com (Matthew Loring)
 */



/** The global object. */
$jscomp.global = this;


/**
 * Initializes Symbol.iterator, if it's not already defined.
 * @suppress {reportUnknownTypes}
 */
$jscomp.initSymbolIterator = function() {
  Symbol = $jscomp.global.Symbol || {};
  if (!Symbol.iterator) {
    Symbol.iterator = '$jscomp$iterator';
  }

  // Only need to do this once. All future calls are no-ops.
  $jscomp.initSymbolIterator = function() {};
};


/**
 * Creates an iterator for the given iterable.
 *
 * @param {string|!Array<T>|!Iterable<T>|!Iterator<T>} iterable
 * @return {!Iterator<T>}
 * @template T
 * @suppress {reportUnknownTypes}
 */
$jscomp.makeIterator = function(iterable) {
  $jscomp.initSymbolIterator();

  if (iterable[Symbol.iterator]) {
    return iterable[Symbol.iterator]();
  }
  if (!(iterable instanceof Array) && typeof iterable != 'string') {
    throw new Error();
  }
  var index = 0;
  return /** @type {!Iterator} */ ({
    next: function() {
      if (index == iterable.length) {
        return { done: true };
      } else {
        return {
          done: false,
          value: iterable[index++]
        };
      }
    }
  });
};

/**
 * Inherit the prototype methods and static methods from one constructor
 * into another.
 *
 * This wires up the prototype chain (like goog.inherits) and copies static
 * properties, for ES6-to-ES{3,5} transpilation.
 *
 * Usage:
 * <pre>
 *   function ParentClass() {}
 *
 *   // Regular method.
 *   ParentClass.prototype.foo = function(a) {};
 *
 *   // Static method.
 *   ParentClass.bar = function() {};
 *
 *   function ChildClass() {
 *     ParentClass.call(this);
 *   }
 *   $jscomp.inherits(ChildClass, ParentClass);
 *
 *   var child = new ChildClass();
 *   child.foo();
 *   ChildClass.bar();  // Static inheritance.
 * </pre>
 *
 * @param {!Function} childCtor Child class.
 * @param {!Function} parentCtor Parent class.
 */
$jscomp.inherits = function(childCtor, parentCtor) {
  /** @constructor */
  function tempCtor() {}
  tempCtor.prototype = parentCtor.prototype;
  childCtor.prototype = new tempCtor();
  /** @override */
  childCtor.prototype.constructor = childCtor;

  for (var p in parentCtor) {
    if (Object.defineProperties) {
      var descriptor = Object.getOwnPropertyDescriptor(parentCtor, p);
      Object.defineProperty(
          childCtor, p, /** @type {!ObjectPropertyDescriptor} */ (descriptor));
    } else {
      // Pre-ES5 browser. Just copy with an assignment.
      childCtor[p] = parentCtor[p];
    }
  }
};
