/*
 * Copyright 2008 The Closure Compiler Authors.
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

/**
 * Generate exports unit test.
 *
 */
public final class GenerateExportsTest extends Es6CompilerTestCase {

  private static final String EXTERNS =
      "function google_exportSymbol(a, b) {}; " +
      "goog.exportProperty = function(a, b, c) {}; ";

  private boolean allowNonGlobalExports = true;

  public GenerateExportsTest() {
    super(EXTERNS);
    compareJsDoc = false;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new GenerateExports(compiler, allowNonGlobalExports,
        "google_exportSymbol", "goog.exportProperty");
  }

  @Override
  protected int getNumRepetitions() {
    // This pass only runs once.
    return 1;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    this.allowNonGlobalExports  = true;
  }

  @Override
  protected void testExternChanges(String input, String expectedExtern) {
    this.enableCompareAsTree(false);
    super.testExternChanges(input, expectedExtern);
  }

  public void testExportSymbol() {
    test("/** @export */function foo() {}",
         "function foo(){}google_exportSymbol(\"foo\",foo)");
  }

  public void testExportSymbolAndProperties() {
    test("/** @export */function foo() {}" +
         "/** @export */foo.prototype.bar = function() {}",
         "function foo(){}" +
         "google_exportSymbol(\"foo\",foo);" +
         "foo.prototype.bar=function(){};" +
         "goog.exportProperty(foo.prototype,\"bar\",foo.prototype.bar)");
  }

  public void testExportPrototypeProperty() {
    test(
        LINE_JOINER.join(
            "function Foo() {}",
            "/** @export */ Foo.prototype.bar = function() {};"),
        LINE_JOINER.join(
            "function Foo() {}",
            "Foo.prototype.bar = function(){};",
            "goog.exportProperty(Foo.prototype, 'bar', Foo.prototype.bar);"));
  }

  public void testExportSymbolAndConstantProperties() {
    test("/** @export */function foo() {}" +
         "/** @export */foo.BAR = 5;",
         "function foo(){}" +
         "google_exportSymbol(\"foo\",foo);" +
         "foo.BAR=5;" +
         "goog.exportProperty(foo,\"BAR\",foo.BAR)");
  }

  public void testExportVars() {
    test("/** @export */var FOO = 5",
         "var FOO=5;" +
         "google_exportSymbol('FOO',FOO)");
  }

  public void testExportLet() {
    testEs6("/** @export */let FOO = 5",
         "let FOO = 5;" +
         "google_exportSymbol('FOO', FOO)");
  }

  public void testExportConst() {
    testEs6("/** @export */const FOO = 5",
         "const FOO = 5;" +
         "google_exportSymbol('FOO', FOO)");
  }

  public void testExportEs6ArrowFunction() {
    testEs6("/** @export */var fn = ()=>{};",
          "var fn = ()=>{};"
        + "google_exportSymbol('fn', fn)");
  }

  public void testNoExport() {
    test("var FOO = 5", "var FOO=5");
  }

  /**
   * Nested assignments are ambiguous and therefore not supported.
   * @see FindExportableNodes
   */
  public void testNestedVarAssign() {
    this.allowNonGlobalExports = false;
    testError(LINE_JOINER.join(
        "var BAR;",
        "/** @export */ var FOO = BAR = 5"),
        FindExportableNodes.NON_GLOBAL_ERROR);

    this.allowNonGlobalExports = true;
    testError(LINE_JOINER.join(
        "var BAR;",
        "/** @export */ var FOO = BAR = 5"),
        FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);
  }

  /**
   * Nested assignments are ambiguous and therefore not supported.
   * @see FindExportableNodes
   */
  public void testNestedAssign() {
    this.allowNonGlobalExports = false;
    testError(LINE_JOINER.join(
        "var BAR;var FOO = {};",
        "/** @export */FOO.test = BAR = 5"),
        FindExportableNodes.NON_GLOBAL_ERROR);

    this.allowNonGlobalExports = true;
    testError(LINE_JOINER.join(
        "var BAR;var FOO = {};",
        "/** @export */FOO.test = BAR = 5"),
        FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);
  }

  public void testNonGlobalScopeExport1() {
    this.allowNonGlobalExports = false;
    testError("(function() { /** @export */var FOO = 5 })()",
        FindExportableNodes.NON_GLOBAL_ERROR);

    this.allowNonGlobalExports = true;
    testError("(function() { /** @export */var FOO = 5 })()",
        FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);
  }

  public void testNonGlobalScopeExport2() {
    this.allowNonGlobalExports = false;
    testError("var x = {/** @export */ A:function() {}}",
        FindExportableNodes.NON_GLOBAL_ERROR);
  }

  public void testExportClass() {
    test("/** @export */ function G() {} foo();",
         "function G() {} google_exportSymbol('G', G); foo();");
  }

  public void testExportClassMember() {
    test(LINE_JOINER.join(
          "/** @export */ function F() {}",
          "/** @export */ F.prototype.method = function() {};"),
         LINE_JOINER.join(
          "function F() {}",
          "google_exportSymbol('F', F);",
          "F.prototype.method = function() {};",
          "goog.exportProperty(F.prototype, 'method', F.prototype.method);"));
  }

  public void testExportEs6ClassSymbol() {
    testEs6("/** @export */ class G {} foo();",
            "class G {} google_exportSymbol('G', G); foo();");

    testEs6("/** @export */ G = class {}; foo();",
            "G = class {}; google_exportSymbol('G', G); foo();");
  }

  public void testExportEs6ClassProperty() {
    testEs6(LINE_JOINER.join(
          "/** @export */ G = class {};",
          "/** @export */ G.foo = class {};"),
            LINE_JOINER.join(
          "G = class {}; google_exportSymbol('G', G);",
          "G.foo = class {};",
          "goog.exportProperty(G, 'foo', G.foo)"));

    testEs6(LINE_JOINER.join(
        "G = class {};",
        "/** @export */ G.prototype.foo = class {};"),
            LINE_JOINER.join(
        "G = class {}; G.prototype.foo = class {};",
        "goog.exportProperty(G.prototype, 'foo', G.prototype.foo)"));
  }

  public void testExportEs6ClassMembers() {
    testEs6(LINE_JOINER.join(
          "/** @export */ class G {",
          "  /** @export */ method() {} }"),
            LINE_JOINER.join(
          "class G { method() {} }",
          "google_exportSymbol('G', G);",
          "goog.exportProperty(G.prototype, 'method', G.prototype.method);"));

    testEs6(LINE_JOINER.join(
          "/** @export */ class G {",
          "/** @export */ static method() {} }"),
            LINE_JOINER.join(
          "class G { static method() {} }",
          "google_exportSymbol('G', G);",
          "goog.exportProperty(G, 'method', G.method);"));
  }

  public void testGoogScopeFunctionOutput() {
    test(
        "/** @export */ $jscomp.scope.foo = /** @export */ function() {}",
        "$jscomp.scope.foo = /** @export */ function() {};"
            + "google_exportSymbol('$jscomp.scope.foo', $jscomp.scope.foo);");
  }

  public void testGoogScopeClassOutput() {
    testEs6(
        "/** @export */ $jscomp.scope.foo = /** @export */ class {}",
        "$jscomp.scope.foo = /** @export */ class {};"
            + "google_exportSymbol('$jscomp.scope.foo', $jscomp.scope.foo);");
  }

  public void testExportSubclass() {
    test("var goog = {}; function F() {}" +
         "/** @export */ function G() {} goog.inherits(G, F);",
         "var goog = {}; function F() {}" +
         "function G() {} goog.inherits(G, F); google_exportSymbol('G', G);");
  }

  public void testExportEnum() {
    // TODO(johnlenz): Issue 310, should the values also be externed?
    test("/** @enum {string}\n @export */ var E = {A:1, B:2};",
         "/** @enum {string}\n @export */ var E = {A:1, B:2};" +
         "google_exportSymbol('E', E);");
  }

  public void testExportObjectLit1() {
    allowExternsChanges(true);
    String code = "var E = {/** @export */ A:1, B:2};";
    testSame(code);
    testExternChanges(code, "Object.prototype.A;");
  }

  public void testExportObjectLit2() {
    allowExternsChanges(true);
    String code = "var E = {/** @export */ get A() { return 1 }, B:2};";
    testSame(code);
    testExternChanges(code, "Object.prototype.A;");
  }

  public void testExportObjectLit3() {
    allowExternsChanges(true);
    String code = "var E = {/** @export */ set A(v) {}, B:2};";
    testSame(code);
    testExternChanges(code, "Object.prototype.A;");
  }

  public void testExportObjectLit4() {
    allowExternsChanges(true);
    String code = "var E = {/** @export */ A:function() {}, B:2};";
    testSame(code);
    testExternChanges(code, "Object.prototype.A;");
  }

  public void testExportClassMember1() {
    allowExternsChanges(true);
    String code = "var E = function() { /** @export */ this.foo = 1; };";
    testSame(code);
    testExternChanges(code, "Object.prototype.foo;");
  }

  public void testMemberExportDoesntConflict() {
    allowExternsChanges(true);
    String code = LINE_JOINER.join(
        "var foo = function() { /** @export */ this.foo = 1; };",
        "/** @export */ foo.method = function(){};");
    String result = LINE_JOINER.join(
        "var foo = function() { /** @export */ this.foo = 1; };",
        "/** @export */ foo.method = function(){};",
        "google_exportSymbol('foo.method', foo.method);");
    test(code, result);
    testExternChanges(code, "Object.prototype.foo;");
  }

  public void testExportClassMemberStub() {
    allowExternsChanges(true);
    String code = "var E = function() { /** @export */ this.foo; };";
    testSame(code);
    testExternChanges(code, "Object.prototype.foo;");
  }
}
