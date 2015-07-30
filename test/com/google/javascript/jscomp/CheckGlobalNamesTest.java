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

import static com.google.javascript.jscomp.CheckGlobalNames.NAME_DEFINED_LATE_WARNING;
import static com.google.javascript.jscomp.CheckGlobalNames.STRICT_MODULE_DEP_QNAME;
import static com.google.javascript.jscomp.CheckGlobalNames.UNDEFINED_NAME_WARNING;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;

/**
 * Tests for {@code CheckGlobalNames.java}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class CheckGlobalNamesTest extends Es6CompilerTestCase {

  private boolean injectNamespace = false;

  public CheckGlobalNamesTest() {
    super("function alert() {}" +
          "/** @constructor */ function Object(){}" +
          "Object.prototype.hasOwnProperty = function() {};" +
          "/** @constructor */ function Function(){}" +
          "Function.prototype.call = function() {};");
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    final CheckGlobalNames checkGlobalNames = new CheckGlobalNames(
        compiler, CheckLevel.WARNING);
    if (injectNamespace) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node js) {
          checkGlobalNames.injectNamespace(
              new GlobalNamespace(compiler, externs, js))
              .process(externs, js);
        }
      };
    } else {
      return checkGlobalNames;
    }
  }

  @Override
  public void setUp() {
    injectNamespace = false;
    STRICT_MODULE_DEP_QNAME.level = CheckLevel.WARNING;
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
  }

  private static final String GET_NAMES =
      "var a = {get d() {return 1}}; a.b = 3; a.c = {get e() {return 5}};";
  private static final String SET_NAMES =
      "var a = {set d(x) {}}; a.b = 3; a.c = {set e(y) {}};";
  private static final String NAMES = "var a = {d: 1}; a.b = 3; a.c = {e: 5};";
  private static final String LET_NAMES = "let a = {d: 1}; a.b = 3; a.c = {e: 5};";
  private static final String CONST_NAMES = "const a = {d: 1, b: 3, c: {e: 5}};";
  private static final String CLASS_DECLARATION_NAMES = "class A{ b(){} }";
  private static final String CLASS_EXPRESSION_NAMES_STUB = "A = class{ b(){} };";
  private static final String CLASS_EXPRESSION_NAMES = "var " + CLASS_EXPRESSION_NAMES_STUB;
  private static final String EXT_OBJLIT_NAMES = "var a = {b(){}, d}; a.c = 3;";

  public void testRefToDefinedProperties1() {
    testSame(NAMES + "alert(a.b); alert(a.c.e);");
    testSame(GET_NAMES + "alert(a.b); alert(a.c.e);");
    testSame(SET_NAMES + "alert(a.b); alert(a.c.e);");

    testSameEs6(LET_NAMES + "alert(a.b); alert(a.c.e);");
    testSameEs6(CONST_NAMES + "alert(a.b); alert(a.c.e);");

    testSameEs6(CLASS_DECLARATION_NAMES + "alert(A.b());");
    testSameEs6(CLASS_EXPRESSION_NAMES + "alert(A.b());");
    testSameEs6("let " + CLASS_EXPRESSION_NAMES_STUB + "alert(A.b());");
    testSameEs6("const " + CLASS_EXPRESSION_NAMES_STUB + "alert(A.b());");

    testSameEs6(EXT_OBJLIT_NAMES + "alert(a.b()); alert(a.c); alert(a.d);");
  }

  public void testRefToDefinedProperties2() {
    testSame(NAMES + "a.x={}; alert(a.c);");
    testSame(GET_NAMES + "a.x={}; alert(a.c);");
    testSame(SET_NAMES + "a.x={}; alert(a.c);");

    testSameEs6(LET_NAMES + "a.x={}; alert(a.c);");
    testSameEs6(EXT_OBJLIT_NAMES + "a.x = {}; alert(a.c);");
  }

  public void testRefToDefinedProperties3() {
    testSame(NAMES + "alert(a.d);");
    testSame(GET_NAMES + "alert(a.d);");
    testSame(SET_NAMES + "alert(a.d);");

    testSameEs6(LET_NAMES + "alert(a.d);");
    testSameEs6(CONST_NAMES + "alert(a.d);");
  }

  public void testRefToMethod1() {
    testSame("function foo() {}; foo.call();");
  }

  public void testRefToMethod2() {
    testSame("function foo() {}; foo.call.call();");
  }

  public void testCallUndefinedFunctionGivesNoWaring() {
    // We don't bother checking undeclared variables--there's another
    // pass that does this already.
    testSame("foo();");
  }

  public void testRefToPropertyOfAliasedName() {
    // this is OK, because "a" was aliased
    testSame(NAMES + "alert(a); alert(a.x);");
  }

  public void testRefToUndefinedProperty1() {
    testWarning(NAMES + "alert(a.x);", UNDEFINED_NAME_WARNING);

    testWarningEs6(CLASS_DECLARATION_NAMES + "alert(A.x);", UNDEFINED_NAME_WARNING);
    testWarningEs6(CLASS_EXPRESSION_NAMES + "alert(A.x);", UNDEFINED_NAME_WARNING);
    testWarningEs6("let " + CLASS_EXPRESSION_NAMES_STUB + "alert(A.x);",
        UNDEFINED_NAME_WARNING);
    testWarningEs6("const " + CLASS_EXPRESSION_NAMES_STUB + "alert(A.x);",
        UNDEFINED_NAME_WARNING);
    testWarningEs6(EXT_OBJLIT_NAMES + "alert(a.x);", UNDEFINED_NAME_WARNING);

  }

  public void testRefToUndefinedProperty2() {
    testWarning(NAMES + "a.x();", UNDEFINED_NAME_WARNING);

    testWarningEs6(LET_NAMES + "a.x();", UNDEFINED_NAME_WARNING);
    testWarningEs6(CONST_NAMES + "a.x();", UNDEFINED_NAME_WARNING);

    testWarningEs6(CLASS_DECLARATION_NAMES + "alert(A.x());", UNDEFINED_NAME_WARNING);
    testWarningEs6(CLASS_EXPRESSION_NAMES + "alert(A.x());", UNDEFINED_NAME_WARNING);
    testWarningEs6("let " + CLASS_EXPRESSION_NAMES_STUB + "alert(A.x());",
        UNDEFINED_NAME_WARNING);
    testWarningEs6("const " + CLASS_EXPRESSION_NAMES_STUB + "alert(A.x());",
        UNDEFINED_NAME_WARNING);
  }

  public void testRefToUndefinedProperty3() {
    testWarning(NAMES + "alert(a.c.x);", UNDEFINED_NAME_WARNING);
    testWarning(GET_NAMES + "alert(a.c.x);", UNDEFINED_NAME_WARNING);
    testWarning(SET_NAMES + "alert(a.c.x);", UNDEFINED_NAME_WARNING);

    testWarningEs6(LET_NAMES + "alert(a.c.x);", UNDEFINED_NAME_WARNING);
    testWarningEs6(CONST_NAMES + "alert(a.c.x);", UNDEFINED_NAME_WARNING);
  }

  public void testRefToUndefinedProperty4() {
    testSame(NAMES + "alert(a.d.x);");
    testSame(GET_NAMES + "alert(a.d.x);");
    testSame(SET_NAMES + "alert(a.d.x);");
  }

  public void testRefToDescendantOfUndefinedProperty1() {
    testWarning(NAMES + "var c = a.x.b;", UNDEFINED_NAME_WARNING);

    testWarningEs6(LET_NAMES + "var c = a.x.b;", UNDEFINED_NAME_WARNING);
    testWarningEs6(CONST_NAMES + "var c = a.x.b;", UNDEFINED_NAME_WARNING);

    testWarningEs6(CLASS_DECLARATION_NAMES + "var z = A.x.y;", UNDEFINED_NAME_WARNING);
    testWarningEs6(CLASS_EXPRESSION_NAMES + "var z = A.x.y;", UNDEFINED_NAME_WARNING);
    testWarningEs6("let " + CLASS_EXPRESSION_NAMES_STUB + "var z = A.x.y;",
        UNDEFINED_NAME_WARNING);
    testWarningEs6("const " + CLASS_EXPRESSION_NAMES_STUB + "var z = A.x.y;",
        UNDEFINED_NAME_WARNING);
  }

  public void testRefToDescendantOfUndefinedProperty2() {
    testWarning(NAMES + "a.x.b();", UNDEFINED_NAME_WARNING);

    testWarningEs6(CLASS_DECLARATION_NAMES + "A.x.y();", UNDEFINED_NAME_WARNING);
    testWarningEs6(CLASS_EXPRESSION_NAMES + "A.x.y();", UNDEFINED_NAME_WARNING);
    testWarningEs6("let " + CLASS_EXPRESSION_NAMES_STUB + "A.x.y();",
        UNDEFINED_NAME_WARNING);
    testWarningEs6("const " + CLASS_EXPRESSION_NAMES_STUB + "A.x.y();",
        UNDEFINED_NAME_WARNING);
  }

  public void testRefToDescendantOfUndefinedProperty3() {
    testWarning(NAMES + "a.x.b = 3;", UNDEFINED_NAME_WARNING);

    testWarningEs6(CLASS_DECLARATION_NAMES + "A.x.y = 42;", UNDEFINED_NAME_WARNING);
    testWarningEs6(CLASS_EXPRESSION_NAMES + "A.x.y = 42;", UNDEFINED_NAME_WARNING);
    testWarningEs6("let " + CLASS_EXPRESSION_NAMES_STUB + "A.x.y = 42;",
        UNDEFINED_NAME_WARNING);
    testWarningEs6("const " + CLASS_EXPRESSION_NAMES_STUB + "A.x.y = 42;",
        UNDEFINED_NAME_WARNING);
  }

  public void testComputedPropNameNoWarning() {
    // Computed prop name is not collected in GlobalNamespace
    testSameEs6("var comp; var a = {}; a[comp + 'name'] = 3");
  }

  public void testUndefinedPrototypeMethodRefGivesNoWarning() {
    testSame("function Foo() {} var a = new Foo(); a.bar();");
  }

  public void testComplexPropAssignGivesNoWarning() {
    testSame("var a = {}; var b = a.b = 3;");
  }

  public void testTypedefGivesNoWarning() {
    testSame("var a = {}; /** @typedef {number} */ a.b;");
  }

  public void testRefToDescendantOfUndefinedPropertyGivesCorrectWarning() {
    testSameNoExterns(NAMES + "a.x.b = 3;", UNDEFINED_NAME_WARNING,
        UNDEFINED_NAME_WARNING.format("a.x"));

    testSameNoExternsEs6(LET_NAMES + "a.x.b = 3;", UNDEFINED_NAME_WARNING,
        UNDEFINED_NAME_WARNING.format("a.x"));
    testSameNoExternsEs6(CONST_NAMES + "a.x.b = 3;", UNDEFINED_NAME_WARNING,
        UNDEFINED_NAME_WARNING.format("a.x"));

    testSameNoExternsEs6(CLASS_DECLARATION_NAMES + "A.x.y = 42;", UNDEFINED_NAME_WARNING,
        UNDEFINED_NAME_WARNING.format("A.x"));
    testSameNoExternsEs6(CLASS_EXPRESSION_NAMES + "A.x.y = 42;", UNDEFINED_NAME_WARNING,
        UNDEFINED_NAME_WARNING.format("A.x"));
  }

  public void testNamespaceInjection() {
    injectNamespace = true;
    testWarning(NAMES + "var c = a.x.b;", UNDEFINED_NAME_WARNING);
  }

  public void testSuppressionOfUndefinedNamesWarning() {
    testSame(new String[] {
        NAMES +
        "/** @constructor */ function Foo() { };" +
        "/** @suppress {undefinedNames} */" +
        "Foo.prototype.bar = function() {" +
        "  alert(a.x);" +
        "  alert(a.x.b());" +
        "  a.x();" +
        "  var c = a.x.b;" +
        "  var c = a.x.b();" +
        "  a.x.b();" +
        "  a.x.b = 3;" +
        "};",
    });
  }

  public void testNoWarningForSimpleVarModuleDep1() {
    testSame(createModuleChain(
        NAMES,
        "var c = a;"
    ));
  }

  public void testNoWarningForSimpleVarModuleDep2() {
    testSame(createModuleChain(
        "var c = a;",
        NAMES
    ));
  }
  public void testNoWarningForGoodModuleDep1() {
    testSame(createModuleChain(
        NAMES,
        "var c = a.b;"
    ));
  }

  public void testBadModuleDep1() {
    testSame(createModuleChain(
        "var c = a.b;",
        NAMES
    ), STRICT_MODULE_DEP_QNAME);
  }

  public void testBadModuleDep2() {
    testSame(createModuleStar(
        NAMES,
        "a.xxx = 3;",
        "var x = a.xxx;"
    ), STRICT_MODULE_DEP_QNAME);
  }

  public void testSelfModuleDep() {
    testSame(createModuleChain(
        NAMES + "var c = a.b;"
    ));
  }

  public void testUndefinedModuleDep1() {
    testSame(createModuleChain(
        "var c = a.xxx;",
        NAMES
    ), UNDEFINED_NAME_WARNING);
  }

  public void testLateDefinedName1() {
    testWarning("x.y = {}; var x = {};", NAME_DEFINED_LATE_WARNING);
    testWarningEs6("x.y = {}; let x = {};", NAME_DEFINED_LATE_WARNING);
    testWarningEs6("x.y = {}; const x = {};", NAME_DEFINED_LATE_WARNING);
  }

  public void testLateDefinedName2() {
    testWarning("var x = {}; x.y.z = {}; x.y = {};", NAME_DEFINED_LATE_WARNING);
    testWarningEs6("let x = {}; x.y.z = {}; x.y = {};", NAME_DEFINED_LATE_WARNING);
    testWarningEs6("const x = {}; x.y.z = {}; x.y = {};", NAME_DEFINED_LATE_WARNING);
  }

  public void testLateDefinedName3() {
    testWarning("var x = {}; x.y.z = {}; x.y = {z: {}};",
        NAME_DEFINED_LATE_WARNING);
    testWarningEs6("let x = {}; x.y.z = {}; x.y = {z: {}};",
        NAME_DEFINED_LATE_WARNING);
    testWarningEs6("const x = {}; x.y.z = {}; x.y = {z: {}};",
        NAME_DEFINED_LATE_WARNING);
    testWarningEs6("var x = {}; x.y.z = {}; x.y = {z};",
        NAME_DEFINED_LATE_WARNING);
  }

  public void testLateDefinedName4() {
    testWarning("var x = {}; x.y.z.bar = {}; x.y = {z: {}};",
        NAME_DEFINED_LATE_WARNING);
  }

  public void testLateDefinedName5() {
    testWarning("var x = {}; /** @typedef {number} */ x.y.z; x.y = {};",
        NAME_DEFINED_LATE_WARNING);
  }

  public void testLateDefinedName6() {
    testWarning(
        "var x = {}; x.y.prototype.z = 3;" +
        "/** @constructor */ x.y = function() {};",
        NAME_DEFINED_LATE_WARNING);
  }

  public void testLateDefinedNameOfClass1() {
    testWarningEs6("X.y = function(){}; class X{};", NAME_DEFINED_LATE_WARNING);
    testWarningEs6("X.y = function(){}; var X = class{};", NAME_DEFINED_LATE_WARNING);
  }

  public void testLateDefinedNameOfClass2() {
    testWarningEs6("X.y = {}; class X{};", NAME_DEFINED_LATE_WARNING);
    testWarningEs6("X.y = {}; var X = class{};", NAME_DEFINED_LATE_WARNING);
  }

  public void testLateDefinedNameOfClass3() {
    testWarningEs6("class X{}; X.y.z = {}; X.y = {};", NAME_DEFINED_LATE_WARNING);
    testWarningEs6("var X = class{}; X.y.z = {}; X.y = {};", NAME_DEFINED_LATE_WARNING);
  }

  public void testOkLateDefinedName1() {
    testSame("function f() { x.y = {}; } var x = {};");
  }

  public void testOkLateDefinedName2() {
    testSame("var x = {}; function f() { x.y.z = {}; } x.y = {};");
  }

  public void testPathologicalCaseThatsOkAnyway() {
    testSame(
        "var x = {};" +
        "switch (x) { " +
        "  default: x.y.z = {}; " +
        "  case (x.y = {}): break;" +
        "}", NAME_DEFINED_LATE_WARNING);
  }

  public void testOkGlobalDeclExpr() {
    testSame("var x = {}; /** @type {string} */ x.foo;");
  }

  public void testBadInterfacePropRef() {
    testWarning(
        "/** @interface */ function F() {}" +
         "F.bar();",
         UNDEFINED_NAME_WARNING);
  }

  public void testInterfaceFunctionPropRef() {
    testSame(
        "/** @interface */ function F() {}" +
         "F.call(); F.hasOwnProperty('z');");
  }

  public void testObjectPrototypeProperties() {
    testSame("var x = {}; var y = x.hasOwnProperty('z');");
  }

  public void testCustomObjectPrototypeProperties() {
    testSame("Object.prototype.seal = function() {};" +
        "var x = {}; x.seal();");
  }

  public void testFunctionPrototypeProperties() {
    testSame("var x = {}; var y = x.hasOwnProperty('z');");
  }

  public void testIndirectlyDeclaredProperties() {
    testSame(
        "Function.prototype.inherits = function(ctor) {" +
        "  this.superClass_ = ctor;" +
        "};" +
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() {};" +
        "/** @constructor */ function SubFoo() {}" +
        "SubFoo.inherits(Foo);" +
        "SubFoo.superClass_.bar();");
  }

  public void testGoogInheritsAlias() {
    testSame(
        "Function.prototype.inherits = function(ctor) {" +
        "  this.superClass_ = ctor;" +
        "};" +
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() {};" +
        "/** @constructor */ function SubFoo() {}" +
        "SubFoo.inherits(Foo);" +
        "SubFoo.superClass_.bar();");
  }

  public void testGoogInheritsAlias2() {
    testWarning(
        CompilerTypeTestCase.CLOSURE_DEFS +
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() {};" +
        "/** @constructor */ function SubFoo() {}" +
        "goog.inherits(SubFoo, Foo);" +
        "SubFoo.superClazz();",
         UNDEFINED_NAME_WARNING);
  }

  public void testGlobalCatch() throws Exception {
    testSame(
        "try {" +
        "  throw Error();" +
        "} catch (e) {" +
        "  console.log(e.name)" +
        "}");
  }
}
