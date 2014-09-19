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
public class GenerateExportsTest extends CompilerTestCase {

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
    super.enableLineNumberCheck(false);

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
         "google_exportSymbol(\"FOO\",FOO)");
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
    test("var BAR;\n/** @export */var FOO = BAR = 5",
         null, FindExportableNodes.NON_GLOBAL_ERROR);

    this.allowNonGlobalExports = true;
    test("var BAR;\n/** @export */var FOO = BAR = 5",
        null, FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);

  }

  /**
   * Nested assignments are ambiguous and therefore not supported.
   * @see FindExportableNodes
   */
  public void testNestedAssign() {
    this.allowNonGlobalExports = false;
    test("var BAR;var FOO = {};\n/** @export */FOO.test = BAR = 5",
         null, FindExportableNodes.NON_GLOBAL_ERROR);

    this.allowNonGlobalExports = true;
    test("var BAR;var FOO = {};\n/** @export */FOO.test = BAR = 5",
         null, FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);
  }

  public void testNonGlobalScopeExport1() {
    this.allowNonGlobalExports = false;
    test("(function() { /** @export */var FOO = 5 })()",
         null, FindExportableNodes.NON_GLOBAL_ERROR);

    this.allowNonGlobalExports = true;
    test("(function() { /** @export */var FOO = 5 })()",
        null, FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);
  }

  public void testNonGlobalScopeExport2() {
    this.allowNonGlobalExports = false;
    test("var x = {/** @export */ A:function() {}}",
         null, FindExportableNodes.NON_GLOBAL_ERROR);
  }

  public void testExportClass() {
    test("/** @export */ function G() {} foo();",
         "function G() {} google_exportSymbol('G', G); foo();");
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

  public void testExportClassMemberStub() {
    allowExternsChanges(true);
    String code = "var E = function() { /** @export */ this.foo; };";
    testSame(code);
    testExternChanges(code, "Object.prototype.foo;");
  }

}
