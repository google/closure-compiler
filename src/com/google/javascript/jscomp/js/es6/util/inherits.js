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
 * @suppress {uselessCode}
 */
'require base';
'require util/objectcreate';
'require es6/util/setprototypeof';


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
 * @suppress {strictMissingProperties} 'superClass_' is not defined on Function
 */
$jscomp.inherits = function(childCtor, parentCtor) {
  childCtor.prototype = $jscomp.objectCreate(parentCtor.prototype);
  /** @override */ childCtor.prototype.constructor = childCtor;
  if ($jscomp.setPrototypeOf) {
    // avoid null dereference warning
    /** @const {!Function} */
    var setPrototypeOf = $jscomp.setPrototypeOf;
    setPrototypeOf(childCtor, parentCtor);
  } else {
    // setPrototypeOf is not available so we need to copy the static
    // methods to the child
    for (var p in parentCtor) {
      if (p == 'prototype') {
        // Don't copy parentCtor.prototype to childCtor.
        continue;
      }
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
  }

  childCtor.superClass_ = parentCtor.prototype;
};
