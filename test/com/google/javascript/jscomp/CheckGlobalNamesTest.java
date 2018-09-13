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

import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@code CheckGlobalNames.java}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public final class CheckGlobalNamesTest extends CompilerTestCase {

  private boolean injectNamespace = false;

  public CheckGlobalNamesTest() {
    super("function alert() {}" +
          "/** @constructor */ function Object(){}" +
          "Object.prototype.hasOwnProperty = function() {};" +
          "/** @constructor */ function Function(){}" +
          "Function.prototype.call = function() {};");
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.STRICT_MODULE_DEP_CHECK, CheckLevel.WARNING);
    return options;
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
  @Before
  public void setUp() throws Exception {
    super.setUp();
    injectNamespace = false;
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

  @Test
  public void testRefToDefinedProperties1() {
    testSame(NAMES + "alert(a.b); alert(a.c.e);");
    testSame(GET_NAMES + "alert(a.b); alert(a.c.e);");
    testSame(SET_NAMES + "alert(a.b); alert(a.c.e);");

    testSame(LET_NAMES + "alert(a.b); alert(a.c.e);");
    testSame(CONST_NAMES + "alert(a.b); alert(a.c.e);");

    testSame(CLASS_DECLARATION_NAMES + "alert(A.b());");
    testSame(CLASS_EXPRESSION_NAMES + "alert(A.b());");
    testSame("let " + CLASS_EXPRESSION_NAMES_STUB + "alert(A.b());");
    testSame("const " + CLASS_EXPRESSION_NAMES_STUB + "alert(A.b());");

    testSame(EXT_OBJLIT_NAMES + "alert(a.b()); alert(a.c); alert(a.d);");
  }

  @Test
  public void testRefToDefinedProperties2() {
    testSame(NAMES + "a.x={}; alert(a.c);");
    testSame(GET_NAMES + "a.x={}; alert(a.c);");
    testSame(SET_NAMES + "a.x={}; alert(a.c);");

    testSame(LET_NAMES + "a.x={}; alert(a.c);");
    testSame(EXT_OBJLIT_NAMES + "a.x = {}; alert(a.c);");
  }

  @Test
  public void testRefToDefinedProperties3() {
    testSame(NAMES + "alert(a.d);");
    testSame(GET_NAMES + "alert(a.d);");
    testSame(SET_NAMES + "alert(a.d);");

    testSame(LET_NAMES + "alert(a.d);");
    testSame(CONST_NAMES + "alert(a.d);");
  }

  @Test
  public void testRefToMethod1() {
    testSame("function foo() {}; foo.call();");
  }

  @Test
  public void testRefToMethod2() {
    testSame("function foo() {}; foo.call.call();");
  }

  @Test
  public void testCallUndefinedFunctionGivesNoWaring() {
    // We don't bother checking undeclared variables--there's another
    // pass that does this already.
    testSame("foo();");
  }

  @Test
  public void testRefToPropertyOfAliasedName() {
    // this is OK, because "a" was aliased
    testSame(NAMES + "alert(a); alert(a.x);");
  }

  @Test
  public void testRefToUndefinedProperty1() {
    testWarning(NAMES + "alert(a.x);", UNDEFINED_NAME_WARNING);

    testWarning(CLASS_DECLARATION_NAMES + "alert(A.x);", UNDEFINED_NAME_WARNING);
    testWarning(CLASS_EXPRESSION_NAMES + "alert(A.x);", UNDEFINED_NAME_WARNING);
    testWarning("let " + CLASS_EXPRESSION_NAMES_STUB + "alert(A.x);",
        UNDEFINED_NAME_WARNING);
    testWarning("const " + CLASS_EXPRESSION_NAMES_STUB + "alert(A.x);",
        UNDEFINED_NAME_WARNING);
    testWarning(EXT_OBJLIT_NAMES + "alert(a.x);", UNDEFINED_NAME_WARNING);

  }

  @Test
  public void testRefToUndefinedProperty2() {
    testWarning(NAMES + "a.x();", UNDEFINED_NAME_WARNING);

    testWarning(LET_NAMES + "a.x();", UNDEFINED_NAME_WARNING);
    testWarning(CONST_NAMES + "a.x();", UNDEFINED_NAME_WARNING);

    testWarning(CLASS_DECLARATION_NAMES + "alert(A.x());", UNDEFINED_NAME_WARNING);
    testWarning(CLASS_EXPRESSION_NAMES + "alert(A.x());", UNDEFINED_NAME_WARNING);
    testWarning("let " + CLASS_EXPRESSION_NAMES_STUB + "alert(A.x());",
        UNDEFINED_NAME_WARNING);
    testWarning("const " + CLASS_EXPRESSION_NAMES_STUB + "alert(A.x());",
        UNDEFINED_NAME_WARNING);
  }

  @Test
  public void testRefToUndefinedProperty3() {
    testWarning(NAMES + "alert(a.c.x);", UNDEFINED_NAME_WARNING);
    testWarning(GET_NAMES + "alert(a.c.x);", UNDEFINED_NAME_WARNING);
    testWarning(SET_NAMES + "alert(a.c.x);", UNDEFINED_NAME_WARNING);

    testWarning(LET_NAMES + "alert(a.c.x);", UNDEFINED_NAME_WARNING);
    testWarning(CONST_NAMES + "alert(a.c.x);", UNDEFINED_NAME_WARNING);
  }

  @Test
  public void testRefToUndefinedProperty4() {
    testSame(NAMES + "alert(a.d.x);");
    testSame(GET_NAMES + "alert(a.d.x);");
    testSame(SET_NAMES + "alert(a.d.x);");
  }

  @Test
  public void testRefToDescendantOfUndefinedProperty1() {
    testWarning(NAMES + "var c = a.x.b;", UNDEFINED_NAME_WARNING);

    testWarning(LET_NAMES + "var c = a.x.b;", UNDEFINED_NAME_WARNING);
    testWarning(CONST_NAMES + "var c = a.x.b;", UNDEFINED_NAME_WARNING);

    testWarning(CLASS_DECLARATION_NAMES + "var z = A.x.y;", UNDEFINED_NAME_WARNING);
    testWarning(CLASS_EXPRESSION_NAMES + "var z = A.x.y;", UNDEFINED_NAME_WARNING);
    testWarning("let " + CLASS_EXPRESSION_NAMES_STUB + "var z = A.x.y;",
        UNDEFINED_NAME_WARNING);
    testWarning("const " + CLASS_EXPRESSION_NAMES_STUB + "var z = A.x.y;",
        UNDEFINED_NAME_WARNING);
  }

  @Test
  public void testRefToDescendantOfUndefinedProperty2() {
    testWarning(NAMES + "a.x.b();", UNDEFINED_NAME_WARNING);

    testWarning(CLASS_DECLARATION_NAMES + "A.x.y();", UNDEFINED_NAME_WARNING);
    testWarning(CLASS_EXPRESSION_NAMES + "A.x.y();", UNDEFINED_NAME_WARNING);
    testWarning("let " + CLASS_EXPRESSION_NAMES_STUB + "A.x.y();",
        UNDEFINED_NAME_WARNING);
    testWarning("const " + CLASS_EXPRESSION_NAMES_STUB + "A.x.y();",
        UNDEFINED_NAME_WARNING);
  }

  @Test
  public void testRefToDescendantOfUndefinedProperty3() {
    testWarning(NAMES + "a.x.b = 3;", UNDEFINED_NAME_WARNING);

    testWarning(CLASS_DECLARATION_NAMES + "A.x.y = 42;", UNDEFINED_NAME_WARNING);
    testWarning(CLASS_EXPRESSION_NAMES + "A.x.y = 42;", UNDEFINED_NAME_WARNING);
    testWarning("let " + CLASS_EXPRESSION_NAMES_STUB + "A.x.y = 42;",
        UNDEFINED_NAME_WARNING);
    testWarning("const " + CLASS_EXPRESSION_NAMES_STUB + "A.x.y = 42;",
        UNDEFINED_NAME_WARNING);
  }

  @Test
  public void testComputedPropNameNoWarning() {
    // Computed prop name is not collected in GlobalNamespace
    testSame("var comp; var a = {}; a[comp + 'name'] = 3");
  }

  @Test
  public void testUndefinedPrototypeMethodRefGivesNoWarning() {
    testSame("function Foo() {} var a = new Foo(); a.bar();");
  }

  @Test
  public void testComplexPropAssignGivesNoWarning() {
    testSame("var a = {}; var b = a.b = 3;");
  }

  @Test
  public void testTypedefGivesNoWarning() {
    testSame("var a = {}; /** @typedef {number} */ a.b;");
  }

  @Test
  public void testRefToDescendantOfUndefinedPropertyGivesCorrectWarning() {
    testWarning(NAMES + "a.x.b = 3;", UNDEFINED_NAME_WARNING,
        UNDEFINED_NAME_WARNING.format("a.x"));

    testWarning(LET_NAMES + "a.x.b = 3;", UNDEFINED_NAME_WARNING,
        UNDEFINED_NAME_WARNING.format("a.x"));
    testWarning(CONST_NAMES + "a.x.b = 3;", UNDEFINED_NAME_WARNING,
        UNDEFINED_NAME_WARNING.format("a.x"));

    testWarning(CLASS_DECLARATION_NAMES + "A.x.y = 42;", UNDEFINED_NAME_WARNING,
        UNDEFINED_NAME_WARNING.format("A.x"));
    testWarning(CLASS_EXPRESSION_NAMES + "A.x.y = 42;", UNDEFINED_NAME_WARNING,
        UNDEFINED_NAME_WARNING.format("A.x"));
  }

  @Test
  public void testNamespaceInjection() {
    injectNamespace = true;
    testWarning(NAMES + "var c = a.x.b;", UNDEFINED_NAME_WARNING);
  }

  @Test
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

  @Test
  public void testNoWarningForSimpleVarModuleDep1() {
    testSame(createModuleChain(
        NAMES,
        "var c = a;"
    ));
  }

  @Test
  public void testNoWarningForSimpleVarModuleDep2() {
    testSame(createModuleChain(
        "var c = a;",
        NAMES
    ));
  }

  @Test
  public void testNoWarningForGoodModuleDep1() {
    testSame(createModuleChain(
        NAMES,
        "var c = a.b;"
    ));
  }

  @Test
  public void testBadModuleDep1() {
    testSame(createModuleChain(
        "var c = a.b;",
        NAMES
    ), STRICT_MODULE_DEP_QNAME);
  }

  @Test
  public void testBadModuleDep2() {
    testSame(createModuleStar(
        NAMES,
        "a.xxx = 3;",
        "var x = a.xxx;"
    ), STRICT_MODULE_DEP_QNAME);
  }

  @Test
  public void testSelfModuleDep() {
    testSame(createModuleChain(
        NAMES + "var c = a.b;"
    ));
  }

  @Test
  public void testUndefinedModuleDep1() {
    testSame(createModuleChain(
        "var c = a.xxx;",
        NAMES
    ), UNDEFINED_NAME_WARNING);
  }

  @Test
  public void testLateDefinedName1() {
    testWarning("x.y = {}; var x = {};", NAME_DEFINED_LATE_WARNING);
    testWarning("x.y = {}; let x = {};", NAME_DEFINED_LATE_WARNING);
    testWarning("x.y = {}; const x = {};", NAME_DEFINED_LATE_WARNING);
  }

  @Test
  public void testLateDefinedName2() {
    testWarning("var x = {}; x.y.z = {}; x.y = {};", NAME_DEFINED_LATE_WARNING);
    testWarning("let x = {}; x.y.z = {}; x.y = {};", NAME_DEFINED_LATE_WARNING);
    testWarning("const x = {}; x.y.z = {}; x.y = {};", NAME_DEFINED_LATE_WARNING);
  }

  @Test
  public void testLateDefinedName3() {
    testWarning("var x = {}; x.y.z = {}; x.y = {z: {}};",
        NAME_DEFINED_LATE_WARNING);
    testWarning("let x = {}; x.y.z = {}; x.y = {z: {}};",
        NAME_DEFINED_LATE_WARNING);
    testWarning("const x = {}; x.y.z = {}; x.y = {z: {}};",
        NAME_DEFINED_LATE_WARNING);
    testWarning("var x = {}; x.y.z = {}; x.y = {z};",
        NAME_DEFINED_LATE_WARNING);
  }

  @Test
  public void testLateDefinedName4() {
    testWarning("var x = {}; x.y.z.bar = {}; x.y = {z: {}};",
        NAME_DEFINED_LATE_WARNING);
  }

  @Test
  public void testLateDefinedName5() {
    testWarning("var x = {}; /** @typedef {number} */ x.y.z; x.y = {};",
        NAME_DEFINED_LATE_WARNING);
  }

  @Test
  public void testLateDefinedName6() {
    testWarning(
        "var x = {}; x.y.prototype.z = 3;" +
        "/** @constructor */ x.y = function() {};",
        NAME_DEFINED_LATE_WARNING);
  }

  @Test
  public void testLateDefinedNameOfClass1() {
    testWarning("X.y = function(){}; class X{};", NAME_DEFINED_LATE_WARNING);
    testWarning("X.y = function(){}; var X = class{};", NAME_DEFINED_LATE_WARNING);
  }

  @Test
  public void testLateDefinedNameOfClass2() {
    testWarning("X.y = {}; class X{};", NAME_DEFINED_LATE_WARNING);
    testWarning("X.y = {}; var X = class{};", NAME_DEFINED_LATE_WARNING);
  }

  @Test
  public void testLateDefinedNameOfClass3() {
    testWarning("class X{}; X.y.z = {}; X.y = {};", NAME_DEFINED_LATE_WARNING);
    testWarning("var X = class{}; X.y.z = {}; X.y = {};", NAME_DEFINED_LATE_WARNING);
  }

  @Test
  public void testOkLateDefinedName1() {
    testSame("function f() { x.y = {}; } var x = {};");
  }

  @Test
  public void testOkLateDefinedName2() {
    testSame("var x = {}; function f() { x.y.z = {}; } x.y = {};");
  }

  @Test
  public void testPathologicalCaseThatsOkAnyway() {
    testWarning(
        "var x = {};"
            + "switch (x) { "
            + "  default: x.y.z = {}; "
            + "  case (x.y = {}): break;"
            + "}",
        NAME_DEFINED_LATE_WARNING);
  }

  @Test
  public void testOkGlobalDeclExpr() {
    testSame("var x = {}; /** @type {string} */ x.foo;");
  }

  @Test
  public void testBadInterfacePropRef() {
    testWarning(
        "/** @interface */ function F() {}" +
         "F.bar();",
         UNDEFINED_NAME_WARNING);
  }

  @Test
  public void testInterfaceFunctionPropRef() {
    testSame(
        "/** @interface */ function F() {}" +
         "F.call(); F.hasOwnProperty('z');");
  }

  @Test
  public void testObjectPrototypeProperties() {
    testSame("var x = {}; var y = x.hasOwnProperty('z');");
  }

  @Test
  public void testCustomObjectPrototypeProperties() {
    testSame("Object.prototype.seal = function() {};" +
        "var x = {}; x.seal();");
  }

  @Test
  public void testFunctionPrototypeProperties() {
    // don't warn for "Foo.call"
    testSame("/** @constructor */ function Foo() {}; Foo.call({});");
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testGlobalCatch() {
    testSame(
        "try {" +
        "  throw Error();" +
        "} catch (e) {" +
        "  console.log(e.name)" +
        "}");
  }

  @Test
  public void testEs6Subclass_noWarningOnValidPropertyAccess() {
    testNoWarning(
        lines(
            "class Parent {",
            "  static f() {}",
            "}",
            "class Child extends Parent {}",
            "Child.f();"));
  }

  @Test
  public void testEs6Subclass_noWarningOnInvalidPropertyAccess() {
    // We don't warn for accesses on any ES6 class that extend another class and let typechecking
    // handle warning for missing static properties.
    // It's not clear that this class can actually do any better than typechecking.

    // Not ok but we don't warn
    testNoWarning(
        lines(
            "class Parent {}", // preserve newline
            "class Child extends Parent {}",
            "Child.f();"));
  }

  @Test
  public void testEs6NonSubclass_stillWarnsForMissingProperty() {
    // We still do warn for undefined properties on an ES6 class with no superclass
    testWarning("class Child {} Child.f();", UNDEFINED_NAME_WARNING);
  }

  @Test
  public void testObjectDestructuringAlias() {
    testSame(
        lines(
            "var ns = {};",
            "/** @enum */",
            "ns.AdjustMode = {SELECT: 0, MOVE: 1};",
            "const {AdjustMode} = ns;",
            "alert(AdjustMode.SELECT);"));
  }

  @Test
  public void testObjectDestructuringAlias_computedProperty() {
    testSame(
        lines(
            "var ns = {};",
            "/** @enum */",
            "ns.AdjustMode = {SELECT: 0, MOVE: 1};",
            "const {['AdjustMode']: AdjustMode} = ns;",
            "alert(AdjustMode.SELECT);"));
  }

  @Test
  public void testObjectDestructuringAlias_defaultValue() {
    testSame(
        lines(
            "var ns = {};",
            "/** @enum */",
            "ns.AdjustMode = {SELECT: 0, MOVE: 1};",
            "const {AdjustMode = {}} = ns;",
            "alert(AdjustMode.SELECT);"));
  }

  @Test
  public void testArrayDestructuring() {
    // Currently, we never issue warnings for property access on an object created through
    // destructuring.
    testSame(lines("const [ns] = [{foo: 3}];", "alert(ns.foo);", "alert(ns.bar);")); // undefined
  }

  @Test
  public void testArrayDestructuring_rest() {
    testSame(
        lines(
            "const [...ns] = [{foo: 3}];", //
            "alert(ns[0].foo);",
            "alert(ns[0].bar);"));
  }

  @Test
  public void testPropertyCreationOnDestructuringAlias() {
    testSame(
        lines(
            "var ns = {};",
            "ns.AdjustMode = {SELECT: 0, MOVE: 1};",
            "const {AdjustMode} = ns;",
            "AdjustMode.OTHER = 2;",
            "alert(ns.AdjustMode.OTHER);"));
  }

  @Test
  public void testNamespaceAliasPreventsWarning() {
    // When a variable is aliased, we back off warning for potentially missing properties on that
    // variable.
    testSame(
        lines(
            "var a = {};",
            "var alias = a;",
            "alert(a.b.c.d);")); // This will cause a runtime error but not a compiler warning.
  }
}
