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
package com.google.javascript.jscomp.integration;

import static com.google.javascript.jscomp.base.JSCompStrings.lines;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class J2clIntegrationTest extends IntegrationTestCase {
  @Test
  public void testInlineClassStaticGetterSetter() {
    externs =
        ImmutableList.of(
            new TestExternsBuilder().addFunction().addConsole().buildExternsFile("externs.js"));
    test(
        createCompilerOptions(),
        lines(
            "var A = class {",
            "  static $clinit() {",
            "    A.$x = 2;",
            "  }",
            "  static get x() {",
            "    return A.$clinit(), A.$x;",
            "  }",
            "  static set x(value) {",
            "    A.$clinit(), A.$x = value;",
            "  }",
            "};",
            "A.x = 3;",
            "console.log(A.x);"),
        lines(
            "var a;", //
            "a = 2;",
            "a = 3;",
            "console.log(a);"));
  }

  @Test
  public void testInlineDefinePropertiesGetterSetter() {
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addObject()
                .addFunction()
                .addConsole()
                .buildExternsFile("externs.js"));
    test(
        createCompilerOptions(),
        lines(
            "/** @constructor */",
            "var A = function() {};",
            "A.$clinit = function() {",
            "  A.$x = 2;",
            "};",
            "Object.defineProperties(",
            "    A,",
            "    {",
            "      x: {",
            "        configurable:true,",
            "        enumerable:true,",
            "        get: function() {",
            "          return A.$clinit(), A.$x;",
            "        },",
            "        set: function(value) {",
            "          A.$clinit(), A.$x = value;",
            "        }",
            "      }",
            "    });",
            "A.x = 3;",
            "console.log(A.x);"),
        lines(
            "var a;", //
            "a = 2;",
            "a = 3;",
            "console.log(a);"));
  }

  @Test
  public void testStripNoSideEffectsClinit() {
    String source =
        lines(
            "class Preconditions {",
            "  static $clinit() {",
            "    Preconditions.$clinit = function() {};",
            "  }",
            "  static check(str) {",
            "    Preconditions.$clinit();",
            "    if (str[0] > 'a') {",
            "      return Preconditions.check(str + str);",
            "    }",
            "    return str;",
            "  }",
            "}",
            "class Main {",
            "  static main() {",
            "    var a = Preconditions.check('a');",
            "    alert('hello');",
            "  }",
            "}",
            "Main.main();");
    test(createCompilerOptions(), source, "alert('hello')");
  }

  @Test
  public void testFoldJ2clClinits() {
    String code =
        lines(
            "function InternalWidget(){}",
            "InternalWidget.$clinit = function () {",
            "  InternalWidget.$clinit = function() {};",
            "  InternalWidget.$clinit();",
            "};",
            "InternalWidget.$clinit();");

    test(createCompilerOptions(), code, "");
  }

  @Override
  @Before
  public void setUp() {
    super.setUp();
    inputFileNameSuffix = ".java.js";
  }

  @Override
  public CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    return options;
  }
}
