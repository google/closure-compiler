/*
 * Copyright 2011 The Closure Compiler Authors.
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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for the RemoveUnusedPolyfills compiler pass. */
@RunWith(JUnit4.class)
public final class RemoveUnusedPolyfillsTest extends CompilerTestCase {

  private static final String EXTERNS = lines(
      DEFAULT_EXTERNS,
      // Polyfill
      "var $jscomp = {};",
      "$jscomp.polyfill = function(name, func, from, to) {};",
      // Methods
      "Array.prototype.includes = function() {};",
      "String.prototype.includes = function() {};",
      "/** @constructor */ function Foo() {}",
      "Foo.prototype.includes = function() {};",
      // Statics
      "Array.from = function() {};",
      "Array.of = function() {};",
      "goog = {}; goog.global = {}; goog.global.Array = Array;",
      "window.Array = Array;",
      // Subtype of array
      "/** @constructor @extends {Array} */ function MyArray() {}",
      // Instances
      "/** @type {?} */ var any;",
      "/** @type {*} */ var all;",
      "/** @type {Object} */ var obj;",
      "/** @type {string} */ var str;",
      "/** @type {Array} */ var arr;",
      "/** @type {string|Foo} */ var strOrFoo;",
      "/** @type {string|Array} */ var strOrArr;",
      "/** @type {string|MyArray} */ var strOrMyArray;",
      "/** @type {Foo|MyArray} */ var fooOrMyArray;",
      "/** @type {Foo} */ var foo;");

  private static final String STRING_INCLUDES =
      "$jscomp.polyfill('String.prototype.includes', function() {}, 'es6', 'es3');\n";
  private static final String ARRAY_INCLUDES =
      "$jscomp.polyfill('Array.prototype.includes', function() {}, 'es6', 'es3');\n";
  private static final String BOTH_INCLUDES = ARRAY_INCLUDES + STRING_INCLUDES;

  private static final String ARRAY_OF =
      "$jscomp.polyfill('Array.of', function() {}, 'es6', 'es3');\n";
  private static final String ARRAY_FROM =
      "$jscomp.polyfill('Array.from', function() {}, 'es6', 'es3');\n";

  public RemoveUnusedPolyfillsTest() {
    super(EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new RemoveUnusedPolyfills(compiler);
  }

  @Test
  public void testRemovesPolyfillInstanceMethods() {
    test(STRING_INCLUDES, "");
    test(STRING_INCLUDES + ARRAY_INCLUDES, "");
  }

  @Test
  public void testRemovesMethodsCalledOnKnownTypes() {
    test(STRING_INCLUDES + "foo.includes();", "foo.includes();");
    test(BOTH_INCLUDES + "foo.includes();", "foo.includes();");
    test(ARRAY_INCLUDES + "var x = {}; x.includes = function() {}; x.includes();",
        "var x = {}; x.includes = function() {}; x.includes();");
    test(BOTH_INCLUDES + "var x = {includes: function() {}}; x.includes();",
        "var x = {includes: function() {}}; x.includes();");

    // Note: after CollapseProperties, goog.string.includes is goog$string$includes.
    // Removing it property depends on running after CollapseProperies, since without
    // it, goog.string looks like {?}, which is an unknown type we can't remove.
    testSame(STRING_INCLUDES + "var goog = goog || {};"
        + "goog.string = goog.string || {};"
        + "goog.string.includes = function() {};"
        + "goog.string.includes();");
  }

  @Test
  public void testRemovesPolyfillStaticMethods() {
    test(ARRAY_OF + ARRAY_FROM + "Array.from();", ARRAY_FROM + "Array.from();");
    test(ARRAY_OF + ARRAY_FROM + "goog.global.Array.of();", ARRAY_OF + "goog.global.Array.of();");
    test(ARRAY_OF + ARRAY_FROM + "window.Array.from();", ARRAY_FROM + "window.Array.from();");
    test(ARRAY_OF + ARRAY_FROM + "var x = new Array();", "var x = new Array();");
  }

  @Test
  public void testDoesNotRemoveMethodsCalledOnUnknownTypes() {
    testSame(STRING_INCLUDES + "any.includes();");
    testSame(BOTH_INCLUDES + "any.includes();");
  }

  @Test
  public void testDoesNotRemoveMethodsCalledOnAllType() {
    testSame(STRING_INCLUDES + "all.includes();");
    testSame(BOTH_INCLUDES + "all.includes();");
  }

  @Test
  public void testDoesNotRemoveMethodsCalledOnObject() {
    testSame(STRING_INCLUDES + "obj.includes();");
    testSame(BOTH_INCLUDES + "obj.includes();");
    testSame(ARRAY_INCLUDES
        + "function f(/** !Object */ x) { x.includes = function() {}; x.includes(); }");
  }

  @Test
  public void testDoesNotRemoveMethodsCalledOnCorrectTypes() {
    testSame(STRING_INCLUDES + "str.includes();");

    test(BOTH_INCLUDES + "str.includes();", STRING_INCLUDES + "str.includes();");
    test(BOTH_INCLUDES + "''.includes();", STRING_INCLUDES + "''.includes();");
    test(BOTH_INCLUDES + "new String('').includes();",
        STRING_INCLUDES + "new String('').includes();");

    test(BOTH_INCLUDES + "arr.includes();", ARRAY_INCLUDES + "arr.includes();");
    test(BOTH_INCLUDES + "[].includes();", ARRAY_INCLUDES + "[].includes();");

    testSame(BOTH_INCLUDES + "strOrArr.includes();");
    testSame(BOTH_INCLUDES + "strOrMyArray.includes();");
    test(BOTH_INCLUDES + "strOrFoo.includes();", STRING_INCLUDES + "strOrFoo.includes();");
    test(BOTH_INCLUDES + "fooOrMyArray.includes();", ARRAY_INCLUDES + "fooOrMyArray.includes();");
  }

  @Test
  public void testDoesNotRemoveMethodsCalledOnPrototype() {
    testSame(STRING_INCLUDES + "String.prototype.includes.call(any);");

    test(BOTH_INCLUDES + "String.prototype.includes.call(any);",
        STRING_INCLUDES + "String.prototype.includes.call(any);");
    test(BOTH_INCLUDES + "Array.prototype.includes.call(any);",
        ARRAY_INCLUDES + "Array.prototype.includes.call(any);");
    test(BOTH_INCLUDES + "Foo.prototype.includes.call(any);",
        "Foo.prototype.includes.call(any);");
  }
}
