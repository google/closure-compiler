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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.junit.Before;

public abstract class BaseReplaceScriptTestCase {
  protected static final Joiner LINE_JOINER = Joiner.on('\n');

  protected static final String CLOSURE_BASE =
      LINE_JOINER.join(
          "/** @const */ var goog = goog || {};",
          "goog.require = function(x) {};",
          "goog.provide = function(x) {};");

  /** Externs used by most test cases and containing only a single definition. */
  protected static final ImmutableList<SourceFile> EXTVAR_EXTERNS =
      ImmutableList.of(SourceFile.fromCode("externs", "var extVar = 3;"));

  /**
   * Default externs containing definitions needed for transpilation of async functions and other
   * post-ES5 features.
   */
  protected static final ImmutableList<SourceFile> DEFAULT_EXTERNS =
      ImmutableList.of(SourceFile.fromCode("default_externs", CompilerTestCase.DEFAULT_EXTERNS));

  /**
   * Test methods may set this variable to control the externs passed to the compiler.
   *
   * <p>Most test cases don't need externs at all or only need the one `extVar` variable defined in
   * the EXTVAR_EXTERNS used here.
   */
  protected ImmutableList<SourceFile> testExterns = EXTVAR_EXTERNS;

  @Before
  public void setUp() throws Exception {
    testExterns = EXTVAR_EXTERNS;
  }

  /**
   * In addition to the passed parameter adds a few options necessary options for
   * {@code replaceScript} and creates a {@code CompilerOptions}.
   */
  protected CompilerOptions getOptions(DiagnosticGroup... typesOfGuard) {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.declaredGlobalExternsOnWindow = false;
    options.setClosurePass(true);
    // These are the options that are always on in JsDev which is the only
    // use-case for replaceScript currently.
    options.setInferTypes(true);
    options.setAllowHotswapReplaceScript(true);
    options.setChecksOnly(true);
    options.setContinueAfterErrors(true);

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
    assertThat(result.success).isTrue();
  }

  protected void runReplaceScriptWithError(
      List<String> sources, String newSource, int newSourceInd, DiagnosticType errorType) {
    Result result =
        runReplaceScript(getOptions(), sources, 0, 0, newSource, newSourceInd, true).getResult();
    assertNumWarningsAndErrors(result, 1, 0);
    assertError(result.errors[0]).hasType(errorType);
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
    checkArgument(newSourceInd < sources.size());

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
    Result result = compiler.compile(testExterns, inputs, options);
    if (expectedCompileErrors == 0) {
      assertThat(compiler.getErrors()).isEmpty();
      assertThat(result.success).isTrue();
    } else {
      assertThat(compiler.getErrors()).hasLength(expectedCompileErrors);
      assertThat(result.success).isFalse();
    }
    assertThat(compiler.getWarnings()).hasLength(expectedCompileWarnings);
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
    assertThat(result.warnings).hasLength(w);
    assertThat(result.errors).hasLength(e);
    assertThat(result.success).isEqualTo(e == 0);
  }

  protected void assertErrorType(JSError e, DiagnosticType type, int lineNumber) {
    assertError(e).hasType(type);
    assertThat(lineNumber).isEqualTo(e.lineNumber);
  }
}
