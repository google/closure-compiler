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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests related to optional chaining. */
@RunWith(JUnit4.class)
public final class OptionalChainingIntegrationTest extends IntegrationTestCase {

  /** Creates a CompilerOptions object with google coding conventions. */
  @Override
  protected CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
    // TODO(b/145761297): Add non-transpiling test cases when the optimization passes have been
    //     updated to handle optional chaining.
    // Default to testing with transpilation, since that will be the most common use case for
    // awhile.
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);
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
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);

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
}
