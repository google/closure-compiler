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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

public final class Es6TypedToEs6ConverterForClassTest extends CompilerTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6_TYPED);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT6_TYPED);
    options.setLanguageOut(LanguageMode.ECMASCRIPT6);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    PhaseOptimizer optimizer = new PhaseOptimizer(compiler, null, null);
    optimizer.addOneTimePass(new PassFactory("convertDeclaredTypesToJSDoc", true) {
      // To make sure types copied.
      @Override CompilerPass create(AbstractCompiler compiler) {
        return new Es6TypedToEs6ConverterForColonTypes(compiler);
      }
    });
    optimizer.addOneTimePass(new PassFactory("convertEs6TypedToEs6", true) {
      // Required for classes that don't have a ctor.
      @Override CompilerPass create(AbstractCompiler compiler) {
        return new Es6TypedToEs6ConverterForClass(compiler);
      }
    });
    return optimizer;
  }

  public void testMemberVariable() throws Exception {
    test(
        LINE_JOINER.join(
            "class C {",
            "  mv: number;",
            "  constructor() {",
            "    this.f = 1;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "class C {",
            "  constructor() {",
            "    this.f = 1;",
            "  }",
            "}",
            "/** @type {number} */ C.prototype.mv;"));
  }

  public void testMemberVariable_noCtor() throws Exception {
    test("class C { mv: number; }",
         "class C {} /** @type {number} */ C.prototype.mv;");
  }

  public void testMemberVariable_static() throws Exception {
    test("class C { static smv; }", "class C {} C.smv;");
  }

  public void testMemberVariable_anonymousClass() throws Exception {
    testSame("(class {})");

    testError("(class { x: number; })",
        Es6TypedToEs6ConverterForClass.CANNOT_CONVERT_MEMBER_VARIABLES);
  }

  public void testComputedPropertyVariable() throws Exception {
    test(
        LINE_JOINER.join(
            "class C {",
            "  ['mv']: number;",
            "  ['mv' + 2]: number;",
            "  constructor() {",
            "    this.f = 1;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "class C {",
            "  constructor() {",
            "    this.f = 1;",
            "  }",
            "}",
            "/** @type {number} */ C.prototype['mv'];",
            "/** @type {number} */ C.prototype['mv' + 2];"));
  }

  public void testComputedPropertyVariable_static() throws Exception {
    test("class C { static ['smv' + 2]: number; }",
         "class C {} /** @type {number} */ C['smv' + 2];");
  }
}
