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

import org.jspecify.nullness.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Generate exports unit test. */
@RunWith(JUnit4.class)
public final class GenerateExportsTest extends CompilerTestCase {

  private static final String EXTERNS =
      lines(
          "/** @const */ var goog = {};",
          "goog.exportSymbol = function(a, b, c) {};",
          "goog.exportProperty = function(a, b, c) {};");

  private boolean allowNonGlobalExports = true;
  private @Nullable String exportSymbolFunction;
  private @Nullable String exportPropertyFunction;

  public GenerateExportsTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new GenerateExports(
        compiler, allowNonGlobalExports, exportSymbolFunction, exportPropertyFunction);
  }

  @Override
  protected int getNumRepetitions() {
    // This pass only runs once.
    return 1;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    this.allowNonGlobalExports = true;
    this.exportSymbolFunction = "goog.exportSymbol";
    this.exportPropertyFunction = "goog.exportProperty";
    // TODO(b/76025401): since this pass sometimes runs after typechecking, verify it correctly
    // propagates type information.
    // enableTypeCheck();
    // enableTypeInfoValidation();
  }

  @Test
  public void testExportSymbol() {
    test(
        "/** @export */function foo() {}",
        lines(
            "/** @export */ function foo(){}", //
            "goog.exportSymbol(\"foo\",foo)"));
  }

  @Test
  public void testExportSymbolAndProperties() {
    test(
        lines("/** @export */function foo() {}", "/** @export */foo.prototype.bar = function() {}"),
        lines(
            "/** @export */function foo(){}",
            "goog.exportSymbol(\"foo\",foo);",
            "/** @export */foo.prototype.bar=function(){};",
            "goog.exportProperty(foo.prototype,\"bar\",foo.prototype.bar)"));
  }

  @Test
  public void testExportPrototypeProperty() {
    test(
        lines("function Foo() {}", "/** @export */ Foo.prototype.bar = function() {};"),
        lines(
            "function Foo() {}",
            "/** @export */ Foo.prototype.bar = function(){};",
            "goog.exportProperty(Foo.prototype, 'bar', Foo.prototype.bar);"));
  }

  @Test
  public void testExportSymbolAndConstantProperties() {
    test(
        lines("/** @export */function foo() {}", "/** @export */foo.BAR = 5;"),
        lines(
            "/** @export */function foo(){}",
            "goog.exportSymbol(\"foo\",foo);",
            "/** @export */foo.BAR=5;",
            "goog.exportProperty(foo,\"BAR\",foo.BAR)"));
  }

  @Test
  public void testExportVars() {
    test(
        "/** @export */var FOO = 5",
        lines("/** @export */var FOO=5;", "goog.exportSymbol('FOO',FOO)"));
  }

  @Test
  public void testExportLet() {
    test(
        "/** @export */let FOO = 5",
        lines("/** @export */let FOO = 5;", "goog.exportSymbol('FOO', FOO)"));
  }

  @Test
  public void testExportConst() {
    test(
        "/** @export */const FOO = 5",
        lines("/** @export */const FOO = 5;", "goog.exportSymbol('FOO', FOO)"));
  }

  @Test
  public void testExportEs6ArrowFunction() {
    test(
        "/** @export */var fn = ()=>{};",
        lines("/** @export */var fn = ()=>{};", "goog.exportSymbol('fn', fn)"));
  }

  @Test
  public void testExportPublicField() {
    allowExternsChanges();
    String code = "class Foo { /** @export */ FIELD; }";
    testSame(code);
    testExternChanges(srcs(code), expected("Object.prototype.FIELD"));

    code = "class Bar { /** @export */ MSG = 'str'; }";
    testSame(code);
    testExternChanges(srcs(code), expected("Object.prototype.MSG"));
  }

  @Test
  public void testExportAnonymousClassPublicField() {
    allowExternsChanges();
    String code = "foo(class { /** @export */ x = 4; });";
    testSame(code);
    testExternChanges(srcs(code), expected("Object.prototype.x"));
  }

  @Test
  public void testExportDeclaredClassPrototypeProperty() {
    test(
        "class Foo {} /** @export */ Foo.prototype.x = 3;",
        lines(
            "class Foo {}",
            "/** @export */",
            "Foo.prototype.x = 3;",
            "goog.exportProperty(Foo.prototype, 'x', Foo.prototype.x);"));
  }

  @Test
  public void testExportConstructorProperty() {
    allowExternsChanges();
    String code =
        lines(
            "class Foo {",
            "  constructor() {",
            "    /** @export */",
            "    this.x = 3;",
            "  }",
            "}");
    testSame(code);
    testExternChanges(srcs(code), expected("Object.prototype.x;"));
  }

  @Test
  public void testNoExport() {
    test("var FOO = 5", "var FOO=5");
  }

  /**
   * Nested assignments are ambiguous and therefore not supported.
   *
   * @see FindExportableNodes
   */
  @Test
  public void testNestedVarAssign() {
    this.allowNonGlobalExports = false;
    testError(
        lines("var BAR;", "/** @export */ var FOO = BAR = 5"),
        FindExportableNodes.NON_GLOBAL_ERROR);

    this.allowNonGlobalExports = true;
    testError(
        lines("var BAR;", "/** @export */ var FOO = BAR = 5"),
        FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);
  }

  /**
   * Nested assignments are ambiguous and therefore not supported.
   *
   * @see FindExportableNodes
   */
  @Test
  public void testNestedAssign() {
    this.allowNonGlobalExports = false;
    testError(
        lines("var BAR;var FOO = {};", "/** @export */FOO.test = BAR = 5"),
        FindExportableNodes.NON_GLOBAL_ERROR);

    this.allowNonGlobalExports = true;
    testError(
        lines("var BAR;", "var FOO = {};", "/** @export */FOO.test = BAR = 5"),
        FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);
  }

  @Test
  public void testNonGlobalScopeExport1() {
    this.allowNonGlobalExports = false;
    testError("(function() { /** @export */var FOO = 5 })()", FindExportableNodes.NON_GLOBAL_ERROR);

    this.allowNonGlobalExports = true;
    testError(
        "(function() { /** @export */var FOO = 5 })()",
        FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);
  }

  @Test
  public void testNonGlobalScopeExport2() {
    this.allowNonGlobalExports = false;
    testError("var x = {/** @export */ A:function() {}}", FindExportableNodes.NON_GLOBAL_ERROR);
  }

  @Test
  public void testExportClass() {
    test(
        "/** @export */ function G() {} foo();",
        "/** @export */ function G() {} goog.exportSymbol('G', G); foo();");
  }

  @Test
  public void testExportClassMember() {
    test(
        lines(
            "/** @export */ function F() {}", "/** @export */ F.prototype.method = function() {};"),
        lines(
            "/** @export */ function F() {}",
            "goog.exportSymbol('F', F);",
            "/** @export */ F.prototype.method = function() {};",
            "goog.exportProperty(F.prototype, 'method', F.prototype.method);"));
  }

  @Test
  public void testExportEs6ClassSymbol() {
    test(
        "/** @export */ class G {} foo();",
        "/** @export */ class G {} goog.exportSymbol('G', G); foo();");

    test(
        "/** @export */ G = class {}; foo();",
        "/** @export */ G = class {}; goog.exportSymbol('G', G); foo();");
  }

  @Test
  public void testExportEs6ClassProperty() {
    test(
        lines(
            "/** @export */ G = class {};", //
            "/** @export */ G.foo = class {};"),
        lines(
            "/** @export */ G = class {};",
            "goog.exportSymbol('G', G);",
            "/** @export */ G.foo = class {};",
            "goog.exportProperty(G, 'foo', G.foo)"));

    test(
        lines("G = class {};", "/** @export */ G.prototype.foo = class {};"),
        lines(
            "G = class {};",
            "/** @export */ G.prototype.foo = class {};",
            "goog.exportProperty(G.prototype, 'foo', G.prototype.foo)"));
  }

  @Test
  public void testExportEs6ClassMembers() {
    test(
        lines("/** @export */", "class G {", "  /** @export */ method() {}", "}"),
        lines(
            "/** @export */ class G { /** @export */ method() {} }",
            "goog.exportSymbol('G', G);",
            "goog.exportProperty(G.prototype, 'method', G.prototype.method);"));

    test(
        lines("/** @export */", "class G {", "  /** @export */ static method() {}", "}"),
        lines(
            "/** @export */ class G { /** @export */ static method() {} }",
            "goog.exportSymbol('G', G);",
            "goog.exportProperty(G, 'method', G.method);"));
  }

  @Test
  public void testExportEs6ClassMembersWithSameName() {
    // Regression test for b/113617023, where we were silently dropping exports of different member
    // functions with the same name.
    test(
        lines(
            "/** @export */",
            "class G {",
            "  /** @export */ method() {}",
            "}",
            "/** @export */",
            "class H {",
            "  /** @export */ method() {}",
            "}"),
        lines(
            "/** @export */",
            "class G {",
            "  /** @export */ method() {}",
            "}",
            "goog.exportSymbol('G', G);",
            "goog.exportProperty(G.prototype, 'method', G.prototype.method);",
            "/** @export */",
            "class H {",
            "  /** @export */ method() {}",
            "}",
            "goog.exportSymbol('H', H);",
            "goog.exportProperty(H.prototype, 'method', H.prototype.method);"));
  }

  @Test
  public void testExportEs6ClassMembersOnSameClass() {
    test(
        lines(
            "class G {", //
            "  /** @export */ methodA() {}",
            "  /** @export */ methodB() {}",
            "}"),
        lines(
            "class G {",
            "  /** @export */ methodA() {}",
            "  /** @export */ methodB() {}",
            "}",
            "goog.exportProperty(G.prototype, 'methodB', G.prototype.methodB);",
            "goog.exportProperty(G.prototype, 'methodA', G.prototype.methodA);"));
  }

  @Test
  public void testExportEs6ClassMemberInObjectLitProperty() {
    test(
        lines(
            "const obj = {", //
            "  prop: class G {",
            "    /** @export */ method() {}",
            "  }",
            "};"),
        lines(
            "const obj = {", //
            "  prop: class G {",
            "    /** @export */ method() {}",
            "  }",
            "};",
            "goog.exportProperty(obj.prop.prototype, 'method', obj.prop.prototype.method);"));
  }

  @Test
  public void testCannotExportEs6ClassMemberInClassExpressionAssignedToGetElem() {
    testError(
        lines(
            "const ns = {};",
            "ns['G'] = class G {", //
            "  /** @export */ method() {}",
            "};"),
        FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);
  }

  @Test
  public void testCannotExportEs6ClassMemberInClassExpressionWithoutLValue() {
    testError(
        lines(
            "use(class G {", //
            "  /** @export */ method() {}",
            "});"),
        FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);
  }

  @Test
  public void testCannotExportEs6ClassMemberInClassExpressionInReturnExpression() {
    testError(
        lines(
            "function f() {",
            "  return class {", //
            "    /** @export */ method() {}",
            "  }",
            "};"),
        FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);
  }

  @Test
  public void testCannotExportEs6StaticBlockInClassExpressionInReturnExpression() {
    testError(
        lines(
            "function f() {", //
            "  return class {",
            "    /** @export */ static {}",
            "  }",
            "};"),
        FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);
  }

  @Test
  public void testCannotExportStaticBlock() {
    testError(
        lines(
            "class G {", //
            "/** @export */ static {}",
            "}"),
        FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);
  }

  @Test
  public void testExportPublicStaticField() {
    test(
        lines(
            "class G {", //
            "  /** @export */ static x = 3;",
            "}"),
        "class G { /** @export */ static x = 3; } goog.exportSymbol('G.x', G.x);");

    test(
        lines(
            "const G = class {", //
            "  /** @export */ static x;",
            "};"),
        "const G = class { /** @export */ static x; }; goog.exportSymbol('G.x', G.x);");

    test(
        lines(
            "const G = class localOnlyName {", //
            "  /** @export */ static x;",
            "};"),
        lines(
            "const G = class localOnlyName {",
            "  /** @export */ static x;",
            "};",
            "goog.exportSymbol('G.x', G.x);"));

    test(
        lines(
            "let ns = {};", //
            "ns = class Bar {",
            "  /** @export */ static x;",
            "};"),
        lines(
            "let ns = {};",
            "ns = class Bar {",
            "  /** @export */ static x;",
            "};",
            "goog.exportSymbol('ns.x', ns.x);"));

    test(
        lines(
            "const ns = {};", //
            "ns.G = class {",
            "  /** @export */ static x;",
            "};"),
        lines(
            "const ns = {};",
            "ns.G = class {",
            "  /** @export */ static x;",
            "};",
            "goog.exportSymbol('ns.G.x', ns.G.x);"));

    test(
        lines(
            "class G {", //
            "  /** @export */ static getRand = Math.random;",
            "}"),
        lines(
            "class G {",
            "  /** @export */",
            "  static getRand = Math.random;",
            "}",
            "goog.exportSymbol('G.getRand', G.getRand);"));
  }

  @Test
  public void testCannotExportStaticFieldInAnonymousClass() {
    testError(
        "foo(class { /** @export */ static x = 4; });",
        FindExportableNodes.EXPORT_ANNOTATION_NOT_ALLOWED);
  }

  @Test
  public void testOldStyleStaticField() {
    test(
        "class Foo {} /** @export */ Foo.x = 3;",
        "class Foo {} /** @export */ Foo.x = 3; goog.exportSymbol('Foo.x', Foo.x);");
  }

  @Test
  public void testExportClassMemberInStaticBlock() {
    allowExternsChanges();
    String code = "class G { static { /** @export */ this.foo = 1;} }";
    testSame(code);
    testExternChanges(srcs(code), expected("Object.prototype.foo;"));
  }

  @Test
  public void testClassMemberExportDoesntConflictInStaticBlock() {
    allowExternsChanges();
    String code =
        lines(
            "/** @export */", //
            "class G {",
            "  static {",
            // TODO(b/235871861): Either generate an export for this property or report an error for
            // it.
            "    /** @export */ this.foo=1;",
            "  }",
            "}");
    String result =
        lines(
            "/** @export */", //
            "class G {",
            "  static {",
            "    /** @export */ this.foo=1;}",
            "  }",
            "goog.exportSymbol('G', G);");
    test(code, result);
    testExternChanges(srcs(code), expected("Object.prototype.foo; var goog;"));
  }

  @Test
  public void testGoogScopeFunctionOutput() {
    test(
        "/** @export */ $jscomp.scope.foo = /** @export */ function() {}",
        lines(
            "/** @export */ $jscomp.scope.foo = /** @export */ function() {};",
            "goog.exportSymbol('$jscomp.scope.foo', $jscomp.scope.foo);"));
  }

  @Test
  public void testGoogScopeClassOutput() {
    test(
        "/** @export */ $jscomp.scope.foo = /** @export */ class {}",
        lines(
            "/** @export */ $jscomp.scope.foo = /** @export */ class {};",
            "goog.exportSymbol('$jscomp.scope.foo', $jscomp.scope.foo);"));
  }

  @Test
  public void testExportSubclass() {
    test(
        lines(
            "var goog = {}; function F() {}",
            "/** @export */ function G() {} goog.inherits(G, F);"),
        lines(
            "var goog = {}; function F() {}",
            "/** @export */ function G() {} goog.inherits(G, F); goog.exportSymbol('G', G);"));
  }

  @Test
  public void testExportEnum() {
    // TODO(johnlenz): Issue 310, should the values also be externed?
    test(
        "/** @enum {string}\n @export */ var E = {A:1, B:2};",
        "/** @enum {string}\n @export */ var E = {A:1, B:2}; goog.exportSymbol('E', E);");
  }

  @Test
  public void testExportObjectLit1() {
    allowExternsChanges();
    String code = "var E = {/** @export */ A:1, B:2};";
    testSame(code);
    testExternChanges(srcs(code), expected("Object.prototype.A;"));
  }

  @Test
  public void testExportObjectLit2() {
    allowExternsChanges();
    String code = "var E = {/** @export */ get A() { return 1 }, B:2};";
    testSame(code);
    testExternChanges(srcs(code), expected("Object.prototype.A;"));
  }

  @Test
  public void testExportObjectLit3() {
    allowExternsChanges();
    String code = "var E = {/** @export */ set A(v) {}, B:2};";
    testSame(code);
    testExternChanges(srcs(code), expected("Object.prototype.A;"));
  }

  @Test
  public void testExportObjectLit4() {
    allowExternsChanges();
    String code = "var E = {/** @export */ A:function() {}, B:2};";
    testSame(code);
    testExternChanges(srcs(code), expected("Object.prototype.A;"));
  }

  @Test
  public void testExportObjectLit5() {
    allowExternsChanges();
    String code = "var E = {/** @export */ A() {}, B:2};";
    testSame(code);
    testExternChanges(srcs(code), expected("Object.prototype.A;"));
  }

  @Test
  public void testExportClassMember1() {
    allowExternsChanges();
    String code = "var E = function() { /** @export */ this.foo = 1; };";
    testSame(code);
    testExternChanges(srcs(code), expected("Object.prototype.foo;"));
  }

  @Test
  public void testMemberExportDoesntConflict() {
    allowExternsChanges();
    String code =
        lines(
            "var foo = function() { /** @export */ this.foo = 1; };",
            "/** @export */ foo.method = function(){};");
    String result =
        lines(
            "var foo = function() { /** @export */ this.foo = 1; };",
            "/** @export */ foo.method = function(){};",
            "goog.exportSymbol('foo.method', foo.method);");
    test(code, result);
    disableCompareAsTree();
    testExternChanges(srcs(code), expected("Object.prototype.foo; var goog;"));
  }

  @Test
  public void testExportClassMemberStub() {
    allowExternsChanges();
    String code = "var E = function() { /** @export */ this.foo; };";
    testSame(code);
    testExternChanges(srcs(code), expected("Object.prototype.foo;"));
  }

  @Test
  public void testExportExprResultProperty() {
    allowNonGlobalExports = false;
    testSame(
        lines("/** @record */ function Foo() {}", "/** @export {number} */ Foo.prototype.myprop;"));
  }

  @Test
  public void testRequiresExportSymbolDefinition() {
    test(
        externs(""),
        srcs("/** @export */ function Foo() {}"),
        error(GenerateExports.MISSING_GOOG_FOR_EXPORT));
  }

  @Test
  public void testRequiresExportPropertyDefinition() {
    test(
        externs(""),
        srcs("var a = {}; /** @export */ a.b = function() {}"),
        error(GenerateExports.MISSING_GOOG_FOR_EXPORT));
  }

  @Test
  public void testRequiresExportSymbolConvention_ifUsesExport() {
    this.exportSymbolFunction = null;

    test(
        srcs("/** @export */ function Foo() {}"), error(GenerateExports.MISSING_EXPORT_CONVENTION));

    this.exportSymbolFunction = "exportSymbol";
    this.exportPropertyFunction = null;

    test(
        srcs("/** @export */ function Foo() {}"), error(GenerateExports.MISSING_EXPORT_CONVENTION));
  }

  @Test
  public void testDoesNotRequireExportSymbolConvention_ifNoExports() {
    this.exportSymbolFunction = null;

    testSame(srcs("function Foo() {}"));
  }
}
