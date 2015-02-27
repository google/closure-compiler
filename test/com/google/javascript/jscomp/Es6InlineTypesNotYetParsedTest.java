/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.THROW_ASSERTION_ERROR;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;

import com.google.common.base.Joiner;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.testing.TestErrorManager;
import com.google.javascript.rhino.Node;

/**
 * This test is temporary. It asserts that the CodeGenerator produces the
 * right ES6_TYPED sources, even though those sources don't yet parse.
 * All these test cases should be migrated to a round-trip test as the parser
 * catches up.
 *
 * @author alexeagle@google.com (Alex Eagle)
 */

public class Es6InlineTypesNotYetParsedTest extends CompilerTestCase {

  private Compiler compiler;

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    enableAstValidation(true);
    compiler = createCompiler();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT6_TYPED);
    return options;
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new ConvertToTypedES6(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testNullType() {
    assertSource("/** @type {null} */ var n;")
        .transpilesTo("var n;");
  }

  public void testUntypedVarargs() {
    assertSource("/** @param {function(this:T, ...)} fn */ function f(fn) {}")
        .transpilesTo("function f(fn: (...p1) => any) {\n}\n;");
  }

  public void testAnyTypeVarargsParam() {
    assertSource("/** @param {...*} v */ function f(v){}")
        .transpilesTo("function f(...v: any[]) {\n}\n;");
  }

  public void testUnionWithUndefined() {
    assertSource("/** @param {Object|undefined} v */ function f(v){}")
        .transpilesTo("function f(v: Object) {\n}\n;");
  }

  public void testUnionWithNullAndUndefined() {
    assertSource("/** @param {null|undefined} v */ function f(v){}")
        .transpilesTo("function f(v) {\n}\n;");
  }

  public void testFunctionType() {
    assertSource("/** @type {function(string,number):boolean} */ var n;")
        .transpilesTo("var n: (p1:string, p2:number) => boolean;");
  }

  public void testTypeUnion() {
    assertSource("/** @type {(number|boolean)} */ var n;")
        .transpilesTo("var n: number | boolean;");
  }

  public void testArrayType() {
    assertSource("/** @type {Array.<string>} */ var s;")
        .transpilesTo("var s: string[];");
    assertSource("/** @type {!Array.<!$jscomp.typecheck.Checker>} */ var s;")
        .transpilesTo("var s: $jscomp.typecheck.Checker[];");
  }

  public void testRecordType() {
    assertSource("/** @type {{myNum: number, myObject}} */ var s;")
        .transpilesTo("var s: {myNum:number ; myObject};");
  }

  public void testParameterizedType() {
    assertSource("/** @type {MyCollection.<string>} */ var s;")
        .transpilesTo("var s: MyCollection<string>;");
    assertSource("/** @type {Object.<string, number>}  */ var s;")
        .transpilesTo("var s: Object<string, number>;");
    assertSource("/** @type {Object.<number>}  */ var s;")
        .transpilesTo("var s: Object<number>;");
  }

  public void testParameterizedTypeWithVoid() throws Exception {
    assertSource("/** @return {!goog.async.Deferred.<void>} */ f = function() {};")
        .transpilesTo("f = function(): goog.async.Deferred {\n};");
  }

  public void testOptionalParameterTypeWithUndefined() throws Exception {
    assertSource("/** @param {(null|undefined)=} opt_ignored */ f = function(opt_ignored) {};")
        .transpilesTo("f = function(opt_ignored) {\n};");
  }

  private SourceTranslationSubject assertSource(String... s) {
    return new SourceTranslationSubject(THROW_ASSERTION_ERROR, s);
  }

  private class SourceTranslationSubject
      extends Subject<SourceTranslationSubject, String[]> {

    public SourceTranslationSubject(FailureStrategy failureStrategy, String[] s)
    {
      super(failureStrategy, s);
    }

    private String doCompile(String... lines) {
      compiler.init(externsInputs,
          asList(SourceFile.fromCode("expected", Joiner.on("\n").join(lines))),
          getOptions());
      compiler.setErrorManager(new TestErrorManager());
      Node root = compiler.parseInputs();
      getProcessor(compiler).process(root.getFirstChild(), root.getLastChild());
      return compiler.toSource();
    }

    public void transpilesTo(String... lines) {
      assertThat(doCompile(getSubject()).trim())
          .isEqualTo("'use strict';" + Joiner.on("\n").join(lines));
    }
  }
}
