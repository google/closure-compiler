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
 * @fileoverview Polyfill for ES6 extends keyword.
 */
'require base';


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
      if (descriptor) {
        Object.defineProperty(childCtor, p, descriptor);
      }
    } else {
      // Pre-ES5 browser. Just copy with an assignment.
      childCtor[p] = parentCtor[p];
    }
  }
};
