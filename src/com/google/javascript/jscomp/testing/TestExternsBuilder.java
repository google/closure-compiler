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

package com.google.javascript.jscomp.testing;

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.SourceFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds a string containing standard externs definitions for use in testing.
 *
 * <p>The externs in this file are intentionally far from complete. Please use the actual externs
 * definitions (es3.js, etc.) as a model when you need to add something for a test case.
 */
public class TestExternsBuilder {
  private static final Joiner LINE_JOINER = Joiner.on('\n');

  private static final String BIGINT_EXTERNS =
      lines(
          "/** ",
          " * @constructor",
          " * @param {*=} arg",
          " * @return {bigint}",
          " */",
          "function BigInt(arg) {}",
          "");

  private static final String ITERABLE_EXTERNS =
      lines(
          // Symbol is needed for Symbol.iterator
          "/** ",
          " * @constructor",
          " * @param {*=} opt_description",
          " * @return {symbol}",
          " * @nosideeffects",
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
          " * @template VALUE, UNUSED_RETURN_T, UNUSED_NEXT_T",
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
          " * @template VALUE, UNUSED_RETURN_T, UNUSED_NEXT_T",
          " */",
          "function IteratorIterable() {}",
          "",
          "/**",
          " * @interface",
          " * @extends {IteratorIterable<VALUE>}",
          " * @template VALUE, UNUSED_RETURN_T, UNUSED_NEXT_T",
          " */",
          "function Generator() {}",
          "/**",
          " * @param {?=} opt_value",
          " * @return {!IIterableResult<VALUE>}",
          " * @override",
          " */",
          "Generator.prototype.next = function(opt_value) {};",
          "/**",
          " * @param {VALUE} value",
          " * @return {!IIterableResult<VALUE>}",
          " */",
          "Generator.prototype.return = function(value) {};",
          "/**",
          " * @param {?} exception",
          " * @return {!IIterableResult<VALUE>}",
          " */",
          "Generator.prototype.throw = function(exception) {};",
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
          " * @this {string|String}",
          " * @param {*=} opt_separator",
          " * @param {number=} opt_limit",
          " * @return {!Array<string>}",
          " */",
          "String.prototype.split = function(opt_separator, opt_limit) {};",
          "/**",
          " * @this {string|String}",
          " * @param {string} search_string",
          " * @param {number=} opt_position",
          " * @return {boolean}",
          " * @nosideeffects",
          " */",
          "String.prototype.startsWith = function(search_string, opt_position) {};",
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
          " * @record",
          " * @template THIS",
          " */",
          "function ObjectPropertyDescriptor() {}",
          "/** @type {(*|undefined)} */",
          "ObjectPropertyDescriptor.prototype.value;",
          "/** @type {(function(this: THIS):?)|undefined} */",
          "ObjectPropertyDescriptor.prototype.get;",
          "",
          "/** @type {(function(this: THIS, ?):void)|undefined} */",
          "ObjectPropertyDescriptor.prototype.set;",
          "",
          "/** @type {boolean|undefined} */",
          "ObjectPropertyDescriptor.prototype.writable;",
          "",
          "/** @type {boolean|undefined} */",
          "ObjectPropertyDescriptor.prototype.enumerable;",
          "",
          "/** @type {boolean|undefined} */",
          "ObjectPropertyDescriptor.prototype.configurable;",
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
          " * @param {string | symbol} prop",
          " * @param {!ObjectPropertyDescriptor} descriptor",
          " * @return {!Object}",
          " */",
          "Object.defineProperty = function(obj, prop, descriptor) {};",
          "",
          "/**",
          " * @template T",
          " * @param {T} obj",
          " * @param {!Object<string|symbol, !ObjectPropertyDescriptor<T>>} props",
          " * @return {T}",
          " */",
          "Object.defineProperties = function(obj, props) {};",
          "",
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
          "/**",
          " * @param {!Object} obj",
          " * @return {Object}",
          " * @nosideeffects",
          " */",
          "Object.getPrototypeOf = function(obj) {};",
          "",
          "/**",
          " * @param {!Object} target",
          " * @param {...(Object|null|undefined)} var_args",
          " * @return {!Object}",
          " */",
          "Object.assign = function(target, var_args) {};",
          "",
          "/**",
          " * @param {T} obj",
          " * @return {T}",
          " * @template T",
          " */",
          "Object.seal = function(obj) {}",
          "");

  private static final String REFLECT_EXTERNS =
      lines(
          "/** @const */",
          "var Reflect = {}",
          "",
          "/**",
          " * @param {function(new: ?, ...?)} targetConstructorFn",
          " * @param {!Array<?>} argList",
          " * @param {function(new: TARGET, ...?)=} opt_newTargetConstructorFn",
          " * @return {TARGET}",
          " * @template TARGET",
          " * @nosideeffects",
          " */",
          "Reflect.construct = function(",
          "    targetConstructorFn, argList, opt_newTargetConstructorFn) {};",
          "",
          "/**",
          " * @param {!Object} target",
          " * @param {?Object} proto",
          " * @return {boolean}",
          " */",
          "Reflect.setPrototypeOf = function(target, proto) {};",
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
          "/** @type {number} */",
          "IArrayLike.prototype.length;",
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
          "",
          "/**",
          " * @param {string|IArrayLike<T>|!Iterable<T>} arrayLike",
          " * @param {function(this:S, (string|T), number): R=} mapFn",
          " * @param {S=} thisObj",
          " * @return {!Array<R>}",
          " * @template T,S,R",
          " */",
          "Array.from = function(arrayLike, mapFn, thisObj) {}",
          "",
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
          "/**",
          " * @param {?number=} begin Zero-based index at which to begin extraction.",
          " * @param {?number=} end Zero-based index at which to end extraction.  slice",
          " *     extracts up to but not including end.",
          " * @return {!Array<T>}",
          " * @this {IArrayLike<T>|string}",
          " * @template T",
          " * @nosideeffects",
          " */",
          "Array.prototype.slice = function(begin, end) {};",
          "",
          "/** @return {!IteratorIterable<T>} */",
          "Array.prototype.values;",
          "");

  private static final String ARGUMENTS_EXTERNS =
      lines(
          "/**",
          " * @constructor",
          " * @implements {IArrayLike<T>}",
          " * @implements {Iterable<?>}",
          " * @template T",
          " */",
          "function Arguments() {}",
          "",
          "/** @type {number} */",
          "Arguments.prototype.length;",
          "",
          "/** @type {!Arguments} */",
          "var arguments;",
          "");

  private static final String CONSOLE_EXTERNS =
      lines(
          "/** @constructor */",
          "function Console() {};",
          "",
          "/**",
          " * @param {...*} var_args",
          " * @return {undefined}",
          " */",
          "Console.prototype.log = function(var_args) {};",
          "",
          "/** @const {!Console} */",
          "var console;",
          "");

  private static final String ALERT_EXTERNS =
      lines(
          "/**",
          " * @param {*} message",
          " * @return {undefined}",
          " */",
          "function alert(message) {}",
          "");

  private static final String PROMISE_EXTERNS =
      lines(
          "", //
          "/**",
          " * @typedef {{then: ?}}",
          " */",
          "var Thenable;",
          "",
          "",
          "/**",
          " * @interface",
          " * @template TYPE",
          " */",
          "function IThenable() {}",
          "",
          "",
          "/**",
          " * @param {?(function(TYPE):VALUE)=} opt_onFulfilled",
          " * @param {?(function(*): *)=} opt_onRejected",
          " * @return {RESULT}",
          " * @template VALUE",
          " *",
          " * @template RESULT := type('IThenable',",
          " *     cond(isUnknown(VALUE), unknown(),",
          " *       mapunion(VALUE, (V) =>",
          " *         cond(isTemplatized(V) && sub(rawTypeOf(V), 'IThenable'),",
          " *           templateTypeOf(V, 0),",
          " *           cond(sub(V, 'Thenable'),",
          " *              unknown(),",
          " *              V)))))",
          " * =:",
          " */",
          "IThenable.prototype.then = function(opt_onFulfilled, opt_onRejected) {};",
          "",
          "",
          "/**",
          " * @param {function(",
          " *             function((TYPE|IThenable<TYPE>|Thenable|null)=),",
          " *             function(*=))} resolver",
          " * @constructor",
          " * @implements {IThenable<TYPE>}",
          " * @template TYPE",
          " */",
          "function Promise(resolver) {}",
          "",
          "",
          "/**",
          " * @param {VALUE=} opt_value",
          " * @return {RESULT}",
          " * @template VALUE",
          " * @template RESULT := type('Promise',",
          " *     cond(isUnknown(VALUE), unknown(),",
          " *       mapunion(VALUE, (V) =>",
          " *         cond(isTemplatized(V) && sub(rawTypeOf(V), 'IThenable'),",
          " *           templateTypeOf(V, 0),",
          " *           cond(sub(V, 'Thenable'),",
          " *              unknown(),",
          " *              V)))))",
          " * =:",
          " */",
          "Promise.resolve = function(opt_value) {};",
          "",
          "",
          "/**",
          " * @param {*=} opt_error",
          " * @return {!Promise<?>}",
          " */",
          "Promise.reject = function(opt_error) {};",
          "",
          "",
          "/**",
          " * @param {!Iterable<VALUE>} iterable",
          " * @return {!Promise<!Array<RESULT>>}",
          " * @template VALUE",
          " * @template RESULT := mapunion(VALUE, (V) =>",
          " *     cond(isUnknown(V),",
          " *         unknown(),",
          " *         cond(isTemplatized(V) && sub(rawTypeOf(V), 'IThenable'),",
          " *             templateTypeOf(V, 0),",
          " *             cond(sub(V, 'Thenable'), unknown(), V))))",
          " * =:",
          " */",
          "Promise.all = function(iterable) {};",
          "",
          "",
          "/**",
          " * @param {!Iterable<VALUE>} iterable",
          " * @return {!Promise<RESULT>}",
          " * @template VALUE",
          " * @template RESULT := mapunion(VALUE, (V) =>",
          " *     cond(isUnknown(V),",
          " *         unknown(),",
          " *         cond(isTemplatized(V) && sub(rawTypeOf(V), 'IThenable'),",
          " *             templateTypeOf(V, 0),",
          " *             cond(sub(V, 'Thenable'), unknown(), V))))",
          " * =:",
          " */",
          "Promise.race = function(iterable) {};",
          "",
          "",
          "/**",
          " * @param {?(function(this:void, TYPE):VALUE)=} opt_onFulfilled",
          " * @param {?(function(this:void, *): *)=} opt_onRejected",
          " * @return {RESULT}",
          " * @template VALUE",
          " *",
          " * @template RESULT := type('Promise',",
          " *     cond(isUnknown(VALUE), unknown(),",
          " *       mapunion(VALUE, (V) =>",
          " *         cond(isTemplatized(V) && sub(rawTypeOf(V), 'IThenable'),",
          " *           templateTypeOf(V, 0),",
          " *           cond(sub(V, 'Thenable'),",
          " *              unknown(),",
          " *              V)))))",
          " * =:",
          " * @override",
          " */",
          "Promise.prototype.then = function(opt_onFulfilled, opt_onRejected) {};",
          "",
          "",
          "/**",
          " * @param {function(*): RESULT} onRejected",
          " * @return {!Promise<RESULT>}",
          " * @template RESULT",
          " */",
          "Promise.prototype.catch = function(onRejected) {};",
          "",
          "",
          "/**",
          " * @param {function()} callback",
          " * @return {!Promise<TYPE>}",
          " */",
          "Promise.prototype.finally = function(callback) {};",
          "");

  private static final String ASYNC_ITERABLE_EXTERNS =
      lines(
          "/**",
          " * @const {symbol}",
          " */",
          "Symbol.asyncIterator;",
          "/**",
          " * @interface",
          " * @template VALUE, UNUSED_RETURN_T, UNUSED_NEXT_T",
          " */",
          "function AsyncIterator() {}",
          "/**",
          " * @param {?=} opt_value",
          " * @return {!Promise<!IIterableResult<VALUE>>}",
          " */",
          "AsyncIterator.prototype.next;",
          "/**",
          " * @interface",
          " * @template VALUE",
          " */",
          "function AsyncIterable() {}",
          "/**",
          " * @return {!AsyncIterator<VALUE,?,*>}",
          " */",
          "AsyncIterable.prototype[Symbol.asyncIterator] = function() {};",
          "/**",
          " * @interface",
          " * @extends {AsyncIterator<VALUE,?,*>}",
          " * @extends {AsyncIterable<VALUE>}",
          " * @template VALUE",
          " */",
          "function AsyncIteratorIterable() {}",
          "/**",
          " * @interface",
          " * @extends {AsyncIteratorIterable<VALUE>}",
          " * @template VALUE, UNUSED_RETURN_T, UNUSED_NEXT_T",
          " */",
          "function AsyncGenerator() {}",
          "/**",
          " * @param {?=} opt_value",
          " * @return {!Promise<!IIterableResult<VALUE>>}",
          " * @override",
          " */",
          "AsyncGenerator.prototype.next = function(opt_value) {};",
          "/**",
          " * @param {VALUE} value",
          " * @return {!Promise<!IIterableResult<VALUE>>}",
          " */",
          "AsyncGenerator.prototype.return = function(value) {};",
          "/**",
          " * @param {?} exception",
          " * @return {!Promise<!IIterableResult<VALUE>>}",
          " */",
          "AsyncGenerator.prototype.throw = function(exception) {};");

  // Test cases that perform transpilation of ES6 classes but use a non-injecting compiler need
  // these definitions.
  private static final String ES6_CLASS_TRANSPILATION_EXTERNS =
      lines(
          "var $jscomp = {};",
          "",
          "/**",
          " * @param {?} subClass",
          " * @param {?} superClass",
          " * @return {?} newClass",
          " */",
          "$jscomp.inherits = function(subClass, superClass) {};",
          "");

  private boolean includeBigIntExterns = false;
  private boolean includeIterableExterns = false;
  private boolean includeStringExterns = false;
  private boolean includeFunctionExterns = false;
  private boolean includeObjectExterns = false;
  private boolean includeArrayExterns = false;
  private boolean includeArgumentsExterns = false;
  private boolean includeConsoleExterns = false;
  private boolean includeAlertExterns = false;
  private boolean includePromiseExterns = false;
  private boolean includeAsyncIterableExterns = false;
  private boolean includeEs6ClassTranspilationExterns = false;
  private boolean includeReflectExterns = false;
  private final List<String> extraExterns = new ArrayList<>();

  public TestExternsBuilder addBigInt() {
    includeBigIntExterns = true;
    return this;
  }

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

  public TestExternsBuilder addPromise() {
    includePromiseExterns = true;
    addIterable(); // Promise.all() and Promise.race() need Iterable
    return this;
  }

  /** Adds declaration of `console.log()` */
  public TestExternsBuilder addConsole() {
    includeConsoleExterns = true;
    return this;
  }

  /** Adds declaration of `alert(message)` */
  public TestExternsBuilder addAlert() {
    includeAlertExterns = true;
    return this;
  }

  public TestExternsBuilder addAsyncIterable() {
    includeAsyncIterableExterns = true;
    addIterable(); // IIterableResult + Symbol
    addPromise(); // Promise
    return this;
  }

  public TestExternsBuilder addReflect() {
    includeReflectExterns = true;
    addObject(); // Reflect shares many things in common with Object
    return this;
  }

  /**
   * Externs needed for successful transpilation of ES6 classes without injecting the runtime code.
   *
   * <p>ES6 class transpilation depends on some runtime code that we often don't want to actually
   * generate in test cases, so we use a non-injecting compiler and include these externs
   * definitions to keep the type checker happy.
   */
  public TestExternsBuilder addEs6ClassTranspilationExterns() {
    includeEs6ClassTranspilationExterns = true;
    addFunction(); // need definition of Function.prototype.apply
    return this;
  }

  public TestExternsBuilder addExtra(String... lines) {
    Collections.addAll(extraExterns, lines);
    return this;
  }

  public String build() {
    List<String> externSections = new ArrayList<>();
    if (includeBigIntExterns) {
      externSections.add(BIGINT_EXTERNS);
    }
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
    if (includeReflectExterns) {
      externSections.add(REFLECT_EXTERNS);
    }
    if (includeArgumentsExterns) {
      externSections.add(ARGUMENTS_EXTERNS);
    }
    if (includeConsoleExterns) {
      externSections.add(CONSOLE_EXTERNS);
    }
    if (includeAlertExterns) {
      externSections.add(ALERT_EXTERNS);
    }
    if (includePromiseExterns) {
      externSections.add(PROMISE_EXTERNS);
    }
    if (includeAsyncIterableExterns) {
      externSections.add(ASYNC_ITERABLE_EXTERNS);
    }
    if (includeEs6ClassTranspilationExterns) {
      externSections.add(ES6_CLASS_TRANSPILATION_EXTERNS);
    }
    externSections.addAll(extraExterns);
    return LINE_JOINER.join(externSections);
  }

  public SourceFile buildExternsFile(String filePath) {
    String externsString = build();
    return SourceFile.fromCode(filePath, externsString);
  }

  private static String lines(String... lines) {
    return LINE_JOINER.join(lines);
  }
}
