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
'require base';
'declare window global';


/**
 * @param {!Object} maybeGlobal
 * @return {!Object} The global object.
 * @suppress {undefinedVars}
 */
$jscomp.getGlobal = function(maybeGlobal) {
  return (typeof window != 'undefined' && window === maybeGlobal) ?
      maybeGlobal :
      (typeof global != 'undefined') ? global : maybeGlobal;
};


/**
 * The global object. For browsers we could just use `this` but in Node that
 * doesn't work.
 * @const {!Object}
 */
$jscomp.global = $jscomp.getGlobal(this);


/**
 * Initializes the Symbol function.
 * @suppress {reportUnknownTypes}
 */
$jscomp.initSymbol = function() {
  if (!$jscomp.global.Symbol) {
    $jscomp.global.Symbol = $jscomp.Symbol;
  }

  // Only need to do this once. All future calls are no-ops.
  $jscomp.initSymbol = function() {};
};


/** @private {number} */
$jscomp.symbolCounter_ = 0;


/**
 * Produces "symbols" (actually just unique strings).
 * @param {string} description
 * @return {symbol}
 * @suppress {reportUnknownTypes}
 */
$jscomp.Symbol = function(description) {
  return /** @type {symbol} */ (
      'jscomp_symbol_' + description + ($jscomp.symbolCounter_++));
};


/**
 * Initializes Symbol.iterator, if it's not already defined.
 * @suppress {reportUnknownTypes}
 */
$jscomp.initSymbolIterator = function() {
  $jscomp.initSymbol();
  if (!$jscomp.global.Symbol.iterator) {
    $jscomp.global.Symbol.iterator = $jscomp.global.Symbol('iterator');
  }

  // Only need to do this once. All future calls are no-ops.
  $jscomp.initSymbolIterator = function() {};
};


/**
 * Creates an iterator for the given iterable.
 *
 * @param {string|!Array<T>|!Iterable<T>|!Iterator<T>|!Arguments<T>} iterable
 * @return {!Iterator<T>}
 * @template T
 * @suppress {reportUnknownTypes}
 */
$jscomp.makeIterator = function(iterable) {
  $jscomp.initSymbolIterator();

  if (iterable[$jscomp.global.Symbol.iterator]) {
    return iterable[$jscomp.global.Symbol.iterator]();
  }

  let index = 0;
  return /** @type {!Iterator} */ ({
    next() {
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
 * Copies the values from an Iterator into an Array. The important difference
 * between this and $jscomp.arrayFromIterable is that if the iterator's
 * next() method has already been called one or more times, this method returns
 * only the values that haven't been yielded yet.
 * @param {!Iterator<T>} iterator
 * @return {!Array<T>}
 * @template T
 */
$jscomp.arrayFromIterator = function(iterator) {
  let i;
  const arr = [];
  while (!(i = iterator.next()).done) {
    arr.push(i.value);
  }
  return arr;
};


/**
 * Copies the values from an Iterable into an Array.
 * @param {string|!Array<T>|!Iterable<T>|!Arguments<T>} iterable
 * @return {!Array<T>}
 * @template T
 */
$jscomp.arrayFromIterable = function(iterable) {
  if (iterable instanceof Array) {
    return iterable;
  } else {
    return $jscomp.arrayFromIterator($jscomp.makeIterator(iterable));
  }
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

  for (const p in parentCtor) {
    if ($jscomp.global.Object.defineProperties) {
      let descriptor = $jscomp.global.Object.getOwnPropertyDescriptor(
          parentCtor, p);
      if (descriptor) {
        $jscomp.global.Object.defineProperty(childCtor, p, descriptor);
      }
    } else {
      // Pre-ES5 browser. Just copy with an assignment.
      childCtor[p] = parentCtor[p];
    }
  }
};
