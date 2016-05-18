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
package com.google.javascript.jscomp;


/**
 * Tests that the property renaming primitive goog.reflect.objectProperty
 * is recognized across the optimization passes.
 *
 * @author chadkillingsworth@gmail.com (Chad Killingsworth)
 */

public final class ClosurePrimitivesIntegrationTest extends IntegrationTestCase {

  private static final String RENAME_FN_DEFINITION = LINE_JOINER.join(
      "/** @const */ var goog = {};",
      "/** @const */ goog.reflect = {};",
      "/**",
      " * @param {string} propName",
      " * @param {Object} type",
      " * @return {string}",
      " */",
      "goog.reflect.objectProperty = function(propName, type) {",
      "  return propName;",
      "};"
  );

  private static final String EXTERNS = LINE_JOINER.join(
      "/**",
      " * @fileoverview",
      " * @externs",
      " */",
      "/** @constructor */ function Console() {}",
      "/** @param {*} input */ Console.prototype.log = function(input) {};",
      "/** @type {Console} */ var console;");

  public void testPrototypePropRename() {
    test(createCompilerOptions(),
        new String[] {
            EXTERNS,
            RENAME_FN_DEFINITION,
            LINE_JOINER.join(
                "/** @constructor */ function Foo() {}",
                "window['Foo'] = Foo;",
                "Foo.prototype.log = function(input) { console.log(input) };",
                "Foo.prototype['log'] = Foo.prototype.log;",
                "var foo = new Foo;",
                "console.log(goog.reflect.objectProperty('log', foo));",
                "foo.log('foobar');")},
        new String[] {
            "",
            "",
            LINE_JOINER.join(
                "function a() {}",
                "window.Foo = a;",
                "a.prototype.a = function(b) { console.log(b) };",
                "a.prototype.log = a.prototype.a;",
                "var c = new a;",
                "console.log('a');",
                "c.a('foobar');")}
    );
  }

  public void testStaticPropRename() {
    test(createCompilerOptions(),
        LINE_JOINER.join(
            RENAME_FN_DEFINITION,
            "/** @const */ var foo = {};",
            "foo.log = function(input) { alert(input) };",
            "alert(goog.reflect.objectProperty('log', foo));",
            "foo.log('foobar');"),
        LINE_JOINER.join(
            "var b = { a: function(a) { alert(a); } };",
            "alert('a');",
            "b.a('foobar');"));
  }

  @Override
  protected CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setCodingConvention(new ClosureCodingConvention());
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);

    return options;
  }
}
