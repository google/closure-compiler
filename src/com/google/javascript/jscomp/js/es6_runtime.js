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



/**
 * Creates an iterator for the given iterable.
 *
 * @param {string|!Array<T>|!Iterable<T>} iterable
 * @return {!Iterator<T>}
 * @template T
 */
$jscomp.makeIterator = function(iterable) {
  if (iterable.$$iterator) {
    return iterable.$$iterator();
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
 * Transfers properties on the from object onto the to object.
 *
 * @param {!Object} to
 * @param {!Object} from
 */
$jscomp.copyProperties = function(to, from) {
  for (var p in from) {
    to[p] = from[p];
  }
};

/**
 * Inherit the prototype methods from one constructor into another.
 *
 * NOTE: This is a copy of goog.inherits moved here to remove dependency on
 * the closure library for Es6ToEs3 transpilation.
 *
 * Usage:
 * <pre>
 * function ParentClass(a, b) { }
 * ParentClass.prototype.foo = function(a) { };
 *
 * function ChildClass(a, b, c) {
 *   ChildClass.base(this, 'constructor', a, b);
 * }
 * $jscomp$inherits(ChildClass, ParentClass);
 *
 * var child = new ChildClass('a', 'b', 'see');
 * child.foo(); // This works.
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
};
