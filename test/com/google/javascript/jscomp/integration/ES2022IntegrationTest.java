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
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DiagnosticGroup;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests related to ES2022 features like class fields */
@RunWith(JUnit4.class)
public final class ES2022IntegrationTest extends IntegrationTestCase {

  /** Creates a CompilerOptions object with google coding conventions. */
  @Override
  protected CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
    options.setLanguageOut(LanguageMode.NO_TRANSPILE);
    options.setDevMode(DevMode.EVERY_PASS);
    options.setCodingConvention(new GoogleCodingConvention());
    return options;
  }

  @Test
  @Ignore("TODO(b/189993301): re-enable this test in next CL. right now it gives error.")
  public void publicClassFields_supportedInChecksOnlyMode() {
    CompilerOptions options = createCompilerOptions();
    options.setChecksOnly(true);

    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));
    test(
        options,
        lines(
            "class MyClass {", //
            "  /** @type {string} */",
            "  x = '';",
            "  y;",
            "}",
            "console.log(new MyClass().x);"),
        // TODO(b/189993301): the compiler should allow this @type annotation
        DiagnosticGroups.MISPLACED_TYPE_ANNOTATION);
  }

  @Test
  @Ignore("TODO(b/189993301): re-enable this test in next CL. right now it gives error.")
  public void computedPublicClassFields_supportedInChecksOnlyMode() {
    CompilerOptions options = createCompilerOptions();
    options.setChecksOnly(true);

    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));
    testNoWarnings(
        options,
        lines(
            "/** @dict */", //
            "class MyClass {",
            "  [3 + 4] = 5;",
            "  [6];",
            "}",
            "console.log(new MyClass()[6]);"));
  }

  @Test
  @Ignore("TODO(b/189993301): re-enable this test in next CL. right now it gives error.")
  public void publicClassFields_cannotBeOutputYet() {
    CompilerOptions options = createCompilerOptions();

    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));
    test(
        options,
        new String[] {
          lines(
              "/** @unrestricted */", //
              "class MyClass {",
              "  /** @type {string} */",
              "  x = '';",
              "  y;",
              "}",
              "console.log(new MyClass().x);")
        },
        /* compiled= */ null,
        new DiagnosticGroup[] {
          // TODO(b/189993301): the compiler should allow this @type annotation
          DiagnosticGroups.MISPLACED_TYPE_ANNOTATION,
          DiagnosticGroups.FEATURES_NOT_SUPPORTED_BY_PASS
        });
  }
}
