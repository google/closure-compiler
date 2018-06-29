/*
 * Copyright 2018 The Closure Compiler Authors.
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

package com.google.javascript.jscomp;

import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a string containing standard externs definitions for use in testing.
 *
 * <p>The externs in this file are intentionally far from complete. Please use the actual externs
 * definitions (es3.js, etc.) as a model when you need to add something for a test case.
 */
public class TestExternsBuilder {
  private static final Joiner LINE_JOINER = Joiner.on('\n');

  private static final String ITERABLE_EXTERNS =
      lines(
          // Symbol is needed for Symbol.iterator
          "/** ",
          " * @constructor",
          " * @param {*=} opt_description",
          " * @return {symbol}",
          " */",
          "function Symbol(opt_description) {}",
          "",
          "/** @const {!symbol} */ Symbol.iterator;",
          "",
          "/**",
          " * @record",
          " * @template VALUE",
          " */",
          "function IIterableResult() {};",
          "/** @type {boolean} */",
          "IIterableResult.prototype.done;",
          "/** @type {VALUE} */",
          "IIterableResult.prototype.value;",
          "",
          "/**",
          " * @interface",
          " * @template VALUE",
          " */",
          "function Iterator() {}",
          "/**",
          " * @param {VALUE=} value",
          " * @return {!IIterableResult<VALUE>}",
          " */",
          "Iterator.prototype.next;",
          "",
          "/**",
          " * @interface",
          " * @template VALUE",
          " */",
          "function Iterable() {}",
          "",
          "/**",
          " * @return {!Iterator<VALUE>}",
          " * @suppress {externsValidation}",
          " */",
          "Iterable.prototype[Symbol.iterator] = function() {};",
          "",
          "/**",
          " * @interface",
          " * @extends {Iterator<VALUE>}",
          " * @extends {Iterable<VALUE>}",
          " * @template VALUE",
          " */",
          "function IteratorIterable() {}",
          "");
  private static final String STRING_EXTERNS =
      lines(
          "/**",
          " * @constructor",
          " * @implements {Iterable<string>}",
          " * @param {*=} arg",
          " * @return {string}",
          " */",
          "function String(arg) {}",
          "/** @type {number} */",
          "String.prototype.length;",
          "/** @param {number} sliceArg */",
          "String.prototype.slice = function(sliceArg) {};",
          "/**",
          " * @this {?String|string}",
          " * @param {?} regex",
          " * @param {?} str",
          " * @param {string=} opt_flags",
          " * @return {string}",
          " */",
          "String.prototype.replace = function(regex, str, opt_flags) {};",
          "/**",
          " * @this {String|string}",
          " * @param {number} index",
          " * @return {string}",
          " */",
          "String.prototype.charAt = function(index) {};",
          "/**",
          " * @this {String|string}",
          " * @param {*} regexp",
          " * @return {Array<string>}",
          " */",
          "String.prototype.match = function(regexp) {};",
          "/**",
          " * @this {String|string}",
          " * @return {string}",
          " */",
          "String.prototype.toLowerCase = function() {};",
          "");
  private static final String FUNCTION_EXTERNS =
      lines(
          "/**",
          " * @constructor",
          " * @param {...*} var_args",
          " */",
          "function Function(var_args) {}",
          "/** @type {!Function} */ Function.prototype.apply;",
          "/** @type {!Function} */ Function.prototype.bind;",
          "/** @type {!Function} */ Function.prototype.call;",
          "/** @type {number} */",
          "Function.prototype.length;",
          "/** @type {string} */",
          "Function.prototype.name;",
          "");
  private static final String OBJECT_EXTERNS =
      lines(
          "/**",
          " * @constructor",
          " */",
          "function ObjectPropertyDescriptor() {}",
          "/** @type {*} */",
          "ObjectPropertyDescriptor.prototype.value;",
          "",
          "/**",
          " * @constructor",
          " * @param {*=} opt_value",
          " * @return {!Object}",
          " */",
          "function Object(opt_value) {}",
          "",
          "/** @type {?Object} */ Object.prototype.__proto__;",
          "/** @return {string} */",
          "Object.prototype.toString = function() {};",
          "/**",
          " * @param {*} propertyName",
          " * @return {boolean}",
          " */",
          "Object.prototype.hasOwnProperty = function(propertyName) {};",
          "/** @type {?Function} */ Object.prototype.constructor;",
          "/** @return {*} */",
          "Object.prototype.valueOf = function() {};",
          "/**",
          " * @param {!Object} obj",
          " * @param {string} prop",
          " * @return {!ObjectPropertyDescriptor|undefined}",
          " * @nosideeffects",
          " */",
          "Object.getOwnPropertyDescriptor = function(obj, prop) {};",
          "/**",
          " * @param {!Object} obj",
          " * @param {string} prop",
          " * @param {!Object} descriptor",
          " * @return {!Object}",
          " */",
          "Object.defineProperty = function(obj, prop, descriptor) {};",
          "/**",
          " * @param {?Object} proto",
          " * @param {?Object=} opt_properties",
          " * @return {!Object}",
          " */",
          "Object.create = function(proto, opt_properties) {};",
          "/**",
          " * @param {!Object} obj",
          " * @param {?} proto",
          " * @return {!Object}",
          " */",
          "Object.setPrototypeOf = function(obj, proto) {};",
          "");
  private static final String ARRAY_EXTERNS =
      lines(
          "/**",
          " * @interface",
          " * @template KEY1, VALUE1",
          " */",
          "function IObject() {};",
          "",
          "/**",
          " * @record",
          " * @extends IObject<number, VALUE2>",
          " * @template VALUE2",
          " */",
          "function IArrayLike() {};",
          "",
          "/**",
          " * @template T",
          " * @constructor",
          " * @implements {IArrayLike<T>} ",
          " * @implements {Iterable<T>}",
          " * @param {...*} var_args",
          " * @return {!Array<?>}",
          " */",
          "function Array(var_args) {}",
          "/** @type {number} */ Array.prototype.length;",
          "/**",
          " * @param {*} arr",
          " * @return {boolean}",
          " */",
          "Array.isArray = function(arr) {};",
          "/**",
          " * @param {...T} var_args",
          " * @return {number} The new length of the array.",
          " * @this {IArrayLike<T>}",
          " * @template T",
          " * @modifies {this}",
          " */",
          "Array.prototype.push = function(var_args) {};",
          "/**",
          " * @this {IArrayLike<T>}",
          " * @return {T}",
          " * @template T",
          " */",
          "Array.prototype.shift = function() {};",
          "/**",
          " * @param {?function(this:S, T, number, !Array<T>): ?} callback",
          " * @param {S=} opt_thisobj",
          " * @this {?IArrayLike<T>|string}",
          " * @template T,S",
          " * @return {undefined}",
          " */",
          "Array.prototype.forEach = function(callback, opt_thisobj) {};",
          "/**",
          " * @param {?function(this:S, T, number, !Array<T>): ?} callback",
          " * @param {S=} opt_thisobj",
          " * @return {!Array<T>}",
          " * @this {?IArrayLike<T>|string}",
          " * @template T,S",
          " */",
          "Array.prototype.filter = function(callback, opt_thisobj) {};",
          "/**",
          " * @param {...*} var_args",
          " * @return {!Array<?>}",
          " * @this {*}",
          " */",
          "Array.prototype.concat = function(var_args) {};",
          "");

  private final String argumentsExterns =
      lines(
          "/**",
          " * @constructor",
          " * @implements {IArrayLike<T>}",
          " * @implements {Iterable<?>}",
          " * @template T",
          " * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Functions_and_function_scope/arguments",
          " */",
          "function Arguments() {}",
          "");

  private boolean includeIterableExterns = false;
  private boolean includeStringExterns = false;
  private boolean includeFunctionExterns = false;
  private boolean includeObjectExterns = false;
  private boolean includeArrayExterns = false;
  private boolean includeArgumentsExterns = false;

  public TestExternsBuilder addIterable() {
    includeIterableExterns = true;
    return this;
  }

  public TestExternsBuilder addString() {
    includeStringExterns = true;
    addIterable(); // String implements Iterable<string>
    return this;
  }

  public TestExternsBuilder addFunction() {
    includeFunctionExterns = true;
    return this;
  }

  public TestExternsBuilder addObject() {
    includeObjectExterns = true;
    addFunction(); // Object.prototype.constructor has type {?Function}
    return this;
  }

  public TestExternsBuilder addArray() {
    includeArrayExterns = true;
    addIterable(); // Array implements Iterable
    return this;
  }

  public TestExternsBuilder addArguments() {
    includeArgumentsExterns = true;
    addArray(); // Arguments implements IArrayLike
    addIterable(); // Arguments implements Iterable
    return this;
  }

  public String build() {
    List<String> externSections = new ArrayList<>();
    if (includeIterableExterns) {
      externSections.add(ITERABLE_EXTERNS);
    }
    if (includeStringExterns) {
      externSections.add(STRING_EXTERNS);
    }
    if (includeFunctionExterns) {
      externSections.add(FUNCTION_EXTERNS);
    }
    if (includeObjectExterns) {
      externSections.add(OBJECT_EXTERNS);
    }
    if (includeArrayExterns) {
      externSections.add(ARRAY_EXTERNS);
    }
    if (includeArgumentsExterns) {
      externSections.add(argumentsExterns);
    }
    return LINE_JOINER.join(externSections);
  }

  private static String lines(String... lines) {
    return LINE_JOINER.join(lines);
  }
}
