/*
 * Copyright 2020 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests related to optional chaining. */
@RunWith(JUnit4.class)
public final class OptionalChainingIntegrationTest extends IntegrationTestCase {

  /** Creates a CompilerOptions object with google coding conventions. */
  CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.NO_TRANSPILE);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDevMode(DevMode.EVERY_PASS);
    options.setCodingConvention(new GoogleCodingConvention());
    options.setRenamePrefixNamespaceAssumeCrossChunkNames(true);
    options.setAssumeGettersArePure(false);
    options.setEmitUseStrict(false); // `"use strict";` is just noise here
    options.setPrettyPrint(true);
    return options;
  }

  @Test
  public void bareMinimumTranspileTest() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019); // transpile

    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));
    test(
        options,
        lines(
            "class MyClass {", //
            "  method(msg) {",
            "    console.log(msg);",
            "  }",
            "}",
            "",
            "/**",
            " * @param {?MyClass} maybeMyClass",
            " */",
            "function maybeLog(maybeMyClass) {",
            "  maybeMyClass?.method('log message')",
            "}",
            "",
            "maybeLog(null);",
            "maybeLog(new MyClass());",
            ""),
        "console.log('log message');");
  }

  @Test
  public void testRemoveUnusedCode() {
    CompilerOptions options = createCompilerOptions();
    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));
    test(
        options,
        lines(
            "class MyClass {", //
            "  method() {",
            "    return 4;",
            "  }",
            "}",
            "",
            "/**",
            " * @param {?MyClass} maybeMyClass",
            " */",
            "function maybeLog(maybeMyClass) {",
            "  maybeMyClass?.method()", // optional chaining call
            "}",
            "",
            "maybeLog(new MyClass());",
            ""),
        "");
  }

  @Test
  public void testInline() {
    CompilerOptions options = createCompilerOptions();
    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));
    test(
        options,
        lines(
            "class MyClass {", //
            "  method() {",
            "    return 4;",
            "  }",
            "}",
            "let x = (new MyClass()).method();", // regular call gets devirtualized and inlined
            "console.log(x);",
            ""),
        "console.log(4);");

    test(
        options,
        lines(
            "class MyClass {", //
            "  method() {",
            "    return 4;",
            "  }",
            "}",
            "let x = (new MyClass())?.method();",
            "console.log(x);",
            ""),
        lines(
            "class a {", //
            "  a() {",
            "    return 4;",
            "  }",
            "}",
            // optional call is not devirtualized and not inlined
            "console.log((new a)?.a());"));
  }

  @Test
  public void testCheckTypes() {
    CompilerOptions options = createCompilerOptions();
    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));
    options.setCheckTypes(true);
    // JSC_WRONG_ARGUMENT_COUNT error is reported
    test(options, "var x = x || {}; x.f = function() {}; x?.f(3);", DiagnosticGroups.CHECK_TYPES);
  }
}
