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
package com.google.javascript.refactoring;

import static com.google.javascript.jscomp.CheckLevel.ERROR;
import static com.google.javascript.refactoring.testing.SuggestedFixes.assertChanges;
import static com.google.javascript.refactoring.testing.SuggestedFixes.assertReplacement;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.SourceFile;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;

/**
 * Test case for {@link ErrorToFixMapper}.
 */

@RunWith(JUnit4.class)
public class ErrorToFixMapperTest {
  @Test
  public void testDebugger() {
    String before = "function f() { ";
    String after = "debugger; }";
    Compiler compiler = getCompiler(before + after);
    JSError[] errors = compiler.getErrors();
    assertEquals(Arrays.toString(errors), 1, errors.length);
    SuggestedFix fix = ErrorToFixMapper.getFixForJsError(errors[0], compiler);
    CodeReplacement replacement = new CodeReplacement(before.length(), "debugger;".length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRemoveCast() {
    String code = "var x = /** @type {string} */ ('foo');";
    String expectedCode = "var x = 'foo';";
    Compiler compiler = getCompiler(code);
    JSError[] errors = compiler.getErrors();
    assertEquals(Arrays.toString(errors), 1, errors.length);
    SuggestedFix fix = ErrorToFixMapper.getFixForJsError(errors[0], compiler);
    assertChanges(fix, "", code, expectedCode);
  }

  private Compiler getCompiler(String jsInput) {
    Compiler compiler = new Compiler();
    CompilerOptions options = getCompilerOptions();
    compiler.compile(
        ImmutableList.<SourceFile>of(), // Externs
        ImmutableList.of(SourceFile.fromCode("test", jsInput)),
        options);
    return compiler;
  }

  private CompilerOptions getCompilerOptions() {
    CompilerOptions options = RefactoringDriver.getCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.DEBUGGER_STATEMENT_PRESENT, ERROR);
    options.setWarningLevel(DiagnosticGroups.UNNECESSARY_CASTS, ERROR);
    return options;
  }
}
