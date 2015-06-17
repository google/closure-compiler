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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.jscomp.testing.NodeSubject;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;

public final class Es6TypedToEs6ConverterTest extends CompilerTestCase {

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
        return new Es6TypedToEs6Converter(compiler);
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
        Es6TypedToEs6Converter.CANNOT_CONVERT_MEMBER_VARIABLES);
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

  public void testBuiltins() throws Exception {
    assertTypeConversion("?", "any");
    assertTypeConversion("number", "number");
    assertTypeConversion("boolean", "boolean");
    assertTypeConversion("string", "string");
    assertTypeConversion("void", "void");
  }

  public void testNamedType() throws Exception {
    assertTypeConversion("!foo", "foo");
    assertTypeConversion("!foo.bar.Baz", "foo.bar.Baz");
  }

  public void testArrayType() throws Exception {
    assertTypeConversion("!Array.<string>", "string[]");
    assertTypeConversion("!Array.<!test.Type>", "test.Type[]");
  }

  public void testParameterizedType() throws Exception {
    assertTypeConversion("!test.Type<string>", "test.Type<string>");
    assertTypeConversion("!test.Type<!A, !B>", "test.Type<A, B>");
    assertTypeConversion("!test.Type<!A<!X>, !B>", "test.Type<A<X>, B>");
  }

  public void testParameterizedArrayType() throws Exception {
    assertTypeConversion("!Array.<!test.Type<number>>", "test.Type<number>[]");
  }

  public Node parseAndProcess(String js) {
    Compiler compiler = new Compiler();

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT6_TYPED);
    compiler.init(
        ImmutableList.<SourceFile>of(), ImmutableList.of(SourceFile.fromCode("js", js)), options);
    compiler.parseInputs();

    CompilerPass pass = new Es6TypedToEs6Converter(compiler);
    pass.process(compiler.getRoot().getFirstChild(), compiler.getRoot().getLastChild());

    return compiler.getRoot().getLastChild();
  }

  private void assertTypeConversion(String expected, String typeSyntax) {
    Node jsDocAst = JsDocInfoParser.parseTypeString(expected);
    Node block = parseAndProcess("var x: " + typeSyntax + ";");
    Node script = block.getFirstChild();
    Node var = script.getFirstChild();
    Node name = var.getFirstChild();

    JSTypeExpression typeAst = name.getJSDocInfo().getType();
    assertNotNull(typeSyntax + " should produce a type AST", typeAst);

    NodeSubject.assertNode(typeAst.getRoot()).isEqualTo(jsDocAst);
    assertNoDeclaredTypes(block);
  }

  private void assertNoDeclaredTypes(Node n) {
    assertNull("declared type should be removed at " + n, n.getDeclaredTypeExpression());
    for (Node child : n.children()) {
      assertNoDeclaredTypes(child);
    }
  }
}
