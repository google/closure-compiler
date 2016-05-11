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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public abstract class BaseReplaceScriptTestCase extends TestCase {
  protected static final Joiner LINE_JOINER = Joiner.on('\n');

  protected static final String CLOSURE_BASE =
      LINE_JOINER.join(
          "/** @const */ var goog = goog || {};",
          "goog.require = function(x) {};",
          "goog.provide = function(x) {};");

  protected static final List<SourceFile> EXTERNS =
      ImmutableList.of(SourceFile.fromCode("externs", "var extVar = 3;"));

  /**
   * In addition to the passed parameter adds a few options necessary options for
   * {@code replaceScript} and creates a {@code CompilerOptions}.
   */
  protected CompilerOptions getOptions(DiagnosticGroup... typesOfGuard) {
    CompilerOptions options = new CompilerOptions();
    options.declaredGlobalExternsOnWindow = false;
    options.setClosurePass(true);
    // These are the options that are always on in JsDev which is the only
    // use-case for replaceScript currently.
    options.setInferTypes(true);
    options.setIdeMode(true);
    for (DiagnosticGroup group : typesOfGuard) {
      options.setWarningLevel(group, CheckLevel.ERROR);
    }
    return options;
  }

  protected void flushResults(Compiler compiler) {
    // TODO(bashir) Maybe implement error-flush functionality in Compiler?
    compiler.setErrorManager(new PrintStreamErrorManager(System.err));
  }

  protected void runReplaceScriptNoWarnings(
      List<String> sources, String newSource, int newSourceInd) {
    Result result =
        runReplaceScript(getOptions(), sources, 0, 0, newSource, newSourceInd, true).getResult();
    assertNoWarningsOrErrors(result);
    assertTrue(result.success);
  }

  /**
   * For a given set of sources, first runs a full compile, then replaces one source with a given
   * new version and calls {@code replaceScript}.
   *
   * @param options Compiler options.
   * @param sources The list of sources.
   * @param expectedCompileErrors Expected number of errors after full compile.
   * @param expectedCompileWarnings Expected number of warnings of full compile.
   * @param newSource The source version.
   * @param newSourceInd Index of the source in {@code sources} to be replaced.
   * @param flushResults Whether to flush results after full-build or not.
   * @return The compiler which can be reused for further inc-compiles.
   */
  protected Compiler runReplaceScript(
      CompilerOptions options,
      List<String> sources,
      int expectedCompileErrors,
      int expectedCompileWarnings,
      String newSource,
      int newSourceInd,
      boolean flushResults) {
    Preconditions.checkArgument(newSourceInd < sources.size());

    // First do a full compile.
    Compiler compiler =
        runFullCompile(
            options, sources, expectedCompileErrors, expectedCompileWarnings, flushResults);

    // Now replace one of the source files and run replaceScript.
    doReplaceScript(compiler, newSource, newSourceInd);
    return compiler;
  }

  protected Compiler runAddScript(
      CompilerOptions options,
      List<String> sources,
      int expectedCompileErrors,
      int expectedCompileWarnings,
      String newSource,
      boolean flushResults) {
    // First do a full compile.
    Compiler compiler =
        runFullCompile(
            options, sources, expectedCompileErrors, expectedCompileWarnings, flushResults);

    // Now replace one of the source files and run replaceScript.
    doAddScript(compiler, newSource, sources.size());
    return compiler;
  }

  protected Compiler runFullCompile(
      CompilerOptions options,
      List<String> sources,
      int expectedCompileErrors,
      int expectedCompileWarnings,
      boolean flushResults) {
    List<SourceFile> inputs = new ArrayList<>();
    int i = 0;
    for (String source : sources) {
      inputs.add(SourceFile.fromCode("in" + i, source));
      i++;
    }
    Compiler compiler = new Compiler();
    Compiler.setLoggingLevel(Level.INFO);
    Result result = compiler.compile(EXTERNS, inputs, options);
    if (expectedCompileErrors == 0) {
      assertTrue("Expected no errors, found: " + Arrays.toString(result.errors), result.success);
    } else {
      assertFalse(result.success);
      assertEquals(expectedCompileErrors, compiler.getErrorCount());
    }
    assertEquals(expectedCompileWarnings, compiler.getWarningCount());
    if (flushResults) {
      flushResults(compiler);
    }

    return compiler;
  }

  protected void doReplaceScript(Compiler compiler, String newSource, int newSourceInd) {
    SourceFile replacedSource = SourceFile.fromCode("in" + newSourceInd, newSource);
    JsAst ast = new JsAst(replacedSource);
    compiler.replaceScript(ast);
  }

  protected void doAddScript(Compiler compiler, String newSource, int newSourceInd) {
    SourceFile replacedSource = SourceFile.fromCode("in" + newSourceInd, newSource);
    JsAst ast = new JsAst(replacedSource);
    compiler.addNewScript(ast);
  }

  protected void assertNoWarningsOrErrors(Result result) {
    assertNumWarningsAndErrors(result, 0, 0);
  }

  protected void assertNumWarningsAndErrors(Result result, int e, int w) {
    assertEquals(
        "Unexpected warnings:\n" + Joiner.on("\n").join(result.warnings),
        w,
        result.warnings.length);
    assertEquals(
        "Unexpected errors:\n" + Joiner.on("\n").join(result.errors), e, result.errors.length);
    assertEquals(e == 0, result.success);
  }

  protected void assertErrorType(JSError e, DiagnosticType type, int lineNumber) {
    assertEquals(e.getType(), type);
    assertEquals(e.lineNumber, lineNumber);
  }
}
