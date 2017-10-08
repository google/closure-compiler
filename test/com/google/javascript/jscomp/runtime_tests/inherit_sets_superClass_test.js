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

goog.module('es6.inherit_sets_superClass.test');

var Example = goog.require('jscomp.runtime_tests.Example');
var testSuite = goog.require('goog.testing.testSuite');

var MyClass = class extends Example {};

testSuite({
  testMyClass() {
    // Some really old browsers don't have Object.getPrototypeOf.
    // (IE8, I'm looking at you.)
    // For the sake of some apps that need to dynamically find the
    // super class prototype for a class in transpiled code on such browsers,
    // the ES6 class inheritance transpilation adds a superClass_ field.
    // WARNING: This is a hack that may disappear without (further) warning.
    const parentClassPrototype =
        Object.getPrototypeOf ?
            Object.getPrototypeOf(MyClass.prototype) : MyClass.superClass_;
    assertEquals(Example.prototype, parentClassPrototype);
  }
});
