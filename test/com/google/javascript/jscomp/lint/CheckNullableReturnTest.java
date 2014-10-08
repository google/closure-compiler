/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;

import java.io.IOException;

/**
 * Test case for {@link CheckNullableReturn}.
 *
 */
public class CheckNullableReturnTest extends CompilerTestCase {
  private String externs = "/** @constructor */ function SomeType() {}";

  public void setUp() throws IOException {
    enableTypeCheck(CheckLevel.ERROR);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckNullableReturn(compiler);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testSimpleWarning() {
    testSame(externs, ""
        + "/** @return {SomeType} */\n"
        + "function f() {\n"
        + "  return new SomeType();\n"
        + "}",
        CheckNullableReturn.NULLABLE_RETURN_WITH_NAME);
  }

  public void testTwoBranches() {
    testSame(externs, ""
        + "/** @return {SomeType} */\n"
        + "function f() {\n"
        + "  if (foo) {\n"
        + "    return new SomeType();\n"
        + "  } else {\n"
        + "    return new SomeType();\n"
        + "  }\n"
        + "}",
        CheckNullableReturn.NULLABLE_RETURN_WITH_NAME);
  }

  public void testTryCatch() {
    testSame(externs, ""
        + "/** @return {SomeType} */\n"
        + "function f() {\n"
        + "  try {\n"
        + "    return new SomeType();\n"
        + "  } catch (e) {\n"
        + "    return new SomeType();\n"
        + "  }\n"
        + "}",
        CheckNullableReturn.NULLABLE_RETURN_WITH_NAME);
  }

  public void testNoExplicitReturn() {
    testSame(externs, ""
        + "/** @return {SomeType} */\n"
        + "function f() {\n"
        + "  if (foo) {\n"
        + "    return new SomeType();\n"
        + "  }\n"
        + "}",
        CheckNullableReturn.NULLABLE_RETURN_WITH_NAME);
  }

  public void testNoWarningIfCanReturnNull() {
    testSame(externs, ""
        + "/** @return {SomeType} */\n"
        + "function f() {\n"
        + "  if (foo) {\n"
        + "    return new SomeType();\n"
        + "  } else {\n"
        + "    return null;\n"
        + "  }\n"
        + "}",
        null);
  }

  public void testNoWarningOnEmptyFunction() {
    testSame(externs, ""
        + "/** @return {SomeType} */\n"
        + "function f() {}",
        null);
  }

  public void testNoWarningOnXOrNull() {
    testSame(externs, ""
        + "/**\n"
        + " * @param {!Array.<!Object>} arr\n"
        + " * @return {Object}\n"
        + " */\n"
        + "function f4(arr) {\n"
        + "  return arr[0] || null;\n"
        + "}",
        null);
  }
}
