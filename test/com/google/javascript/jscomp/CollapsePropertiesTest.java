/*
 * Copyright 2006 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.CollapseProperties.UNSAFE_THIS;

/**
 * Tests for {@link CollapseProperties}.
 *
 */
public class CollapsePropertiesTest extends CompilerTestCase {

  private static String EXTERNS =
      "var window; function alert(s) {} function parseInt(s) {}" +
      "/** @constructor */ function String() {}";

  private boolean collapsePropertiesOnExternTypes = false;

  public CollapsePropertiesTest() {
    super(EXTERNS);
  }

  @Override public CompilerPass getProcessor(Compiler compiler) {
    return new CollapseProperties(
        compiler, collapsePropertiesOnExternTypes, true);
  }

  @Override
  public void setUp() {
    enableLineNumberCheck(true);
    enableNormalize(true);
  }

  @Override public int getNumRepetitions() {
    return 1;
  }

  public void testCollapse() {
    test("var a = {}; a.b = {}; var c = a.b;",
         "var a$b = {}; var c = a$b");
  }

  public void testMultiLevelCollapse() {
    test("var a = {}; a.b = {}; a.b.c = {}; var d = a.b.c;",
         "var a$b$c = {}; var d = a$b$c;");
  }

  public void testDecrement() {
    test("var a = {}; a.b = 5; a.b--; a.b = 5",
         "var a$b = 5; a$b--; a$b = 5");
  }

  public void testIncrement() {
    test("var a = {}; a.b = 5; a.b++; a.b = 5",
         "var a$b = 5; a$b++; a$b = 5");
  }

  public void testObjLitDeclaration() {
    test("var a = {b: {}, c: {}}; var d = a.b; var e = a.c",
         "var a$b = {}; var a$c = {}; var d = a$b; var e = a$c");
  }

  public void testObjLitDeclarationWithGet1() {
    testSame("var a = {get b(){}};");
  }

  public void testObjLitDeclarationWithGet2() {
    test("var a = {b: {}, get c(){}}; var d = a.b; var e = a.c",
         "var a$b = {};var a = {get c(){}};var d = a$b; var e = a.c");
  }

  public void testObjLitDeclarationWithGet3() {
    test("var a = {b: {get c() { return 3; }}};",
         "var a$b = {get c() { return 3; }};");
  }

  public void testObjLitDeclarationWithSet1() {
    testSame("var a = {set b(a){}};");
  }

  public void testObjLitDeclarationWithSet2() {
    test("var a = {b: {}, set c(a){}}; var d = a.b; var e = a.c",
         "var a$b = {};var a = {set c(a){}};var d = a$b; var e = a.c");
  }

  public void testObjLitDeclarationWithSet3() {
    test("var a = {b: {set c(d) {}}};",
         "var a$b = {set c(d) {}};");
  }

  public void testObjLitDeclarationWithGetAndSet1() {
    test("var a = {b: {get c() { return 3; },set c(d) {}}};",
         "var a$b = {get c() { return 3; },set c(d) {}};");
  }

  public void testObjLitDeclarationWithDuplicateKeys() {
    test("var a = {b: 0, b: 1}; var c = a.b;",
         "var a$b = 0; var a$b = 1; var c = a$b;",
         SyntacticScopeCreator.VAR_MULTIPLY_DECLARED_ERROR);
  }

  public void testObjLitAssignmentDepth1() {
    test("var a = {b: {}, c: {}}; var d = a.b; var e = a.c",
         "var a$b = {}; var a$c = {}; var d = a$b; var e = a$c");
  }

  public void testObjLitAssignmentDepth2() {
    test("var a = {}; a.b = {c: {}, d: {}}; var e = a.b.c; var f = a.b.d",
         "var a$b$c = {}; var a$b$d = {}; var e = a$b$c; var f = a$b$d");
  }

  public void testObjLitAssignmentDepth3() {
    test("var a = {}; a.b = {}; a.b.c = {d: 1, e: 2}; var f = a.b.c.d",
         "var a$b$c$d = 1; var a$b$c$e = 2; var f = a$b$c$d");
  }

  public void testObjLitAssignmentDepth4() {
    test("var a = {}; a.b = {}; a.b.c = {}; a.b.c.d = {e: 1, f: 2}; " +
         "var g = a.b.c.d.e",
         "var a$b$c$d$e = 1; var a$b$c$d$f = 2; var g = a$b$c$d$e");
  }

  public void testGlobalObjectDeclaredToPreserveItsPreviousValue1() {
    test("var a = a ? a : {}; a.c = 1;",
         "var a = a ? a : {}; var a$c = 1;");
  }

  public void testGlobalObjectDeclaredToPreserveItsPreviousValue2() {
    test("var a = a || {}; a.c = 1;",
         "var a = a || {}; var a$c = 1;");
  }

  public void testGlobalObjectDeclaredToPreserveItsPreviousValue3() {
    test("var a = a || {get b() {}}; a.c = 1;",
         "var a = a || {get b() {}}; var a$c = 1;");
  }

  public void testGlobalObjectNameInBooleanExpressionDepth1_1() {
    test("var a = {b: 0}; a.c = 1; if (a) x();",
         "var a$b = 0; var a = {}; var a$c = 1; if (a) x();");
  }

  public void testGlobalObjectNameInBooleanExpressionDepth1_2() {
    test("var a = {b: 0}; a.c = 1; if (!(a && a.c)) x();",
         "var a$b = 0; var a = {}; var a$c = 1; if (!(a && a$c)) x();");
  }

  public void testGlobalObjectNameInBooleanExpressionDepth1_3() {
    test("var a = {b: 0}; a.c = 1; while (a || a.c) x();",
         "var a$b = 0; var a = {}; var a$c = 1; while (a || a$c) x();");
  }

  public void testGlobalObjectNameInBooleanExpressionDepth1_4() {
    testSame("var a = {}; a.c = 1; var d = a || {}; a.c;");
  }

  public void testGlobalObjectNameInBooleanExpressionDepth1_5() {
    testSame("var a = {}; a.c = 1; var d = a.c || a; a.c;");
  }

  public void testGlobalObjectNameInBooleanExpressionDepth1_6() {
    test("var a = {b: 0}; a.c = 1; var d = !(a.c || a); a.c;",
         "var a$b = 0; var a = {}; var a$c = 1; var d = !(a$c || a); a$c;");
  }

  public void testGlobalObjectNameInBooleanExpressionDepth2() {
    test("var a = {b: {}}; a.b.c = 1; if (a.b) x(a.b.c);",
         "var a$b = {}; var a$b$c = 1; if (a$b) x(a$b$c);");
  }

  public void testGlobalObjectNameInBooleanExpressionDepth3() {
    // TODO(user): Make CollapseProperties even more aggressive so that
    // a$b.z gets collapsed. Right now, it doesn't get collapsed because the
    // expression (a.b && a.b.c) could return a.b. But since it returns a.b iff
    // a.b *is* safely collapsible, the boolean logic should be smart enough to
    // only consider the right side of the && as aliasing.
    test("var a = {}; a.b = {}; /** @constructor */ a.b.c = function(){};" +
         " a.b.z = 1; var d = a.b && a.b.c;",
         "var a$b = {}; var a$b$c = function(){};" +
         " a$b.z = 1; var d = a$b && a$b$c;", null,
         CollapseProperties.UNSAFE_NAMESPACE_WARNING);
  }

  public void testGlobalFunctionNameInBooleanExpressionDepth1() {
    test("function a() {} a.c = 1; if (a) x(a.c);",
         "function a() {} var a$c = 1; if (a) x(a$c);");
  }

  public void testGlobalFunctionNameInBooleanExpressionDepth2() {
    test("var a = {b: function(){}}; a.b.c = 1; if (a.b) x(a.b.c);",
         "var a$b = function(){}; var a$b$c = 1; if (a$b) x(a$b$c);");
  }

  public void testAliasCreatedForObjectDepth1_1() {
    // An object's properties are not collapsed if the object is referenced
    // in a such a way that an alias is created for it.
    testSame("var a = {b: 0}; var c = a; c.b = 1; a.b == c.b;");
  }

  public void testAliasCreatedForObjectDepth1_2() {
    testSame("var a = {b: 0}; f(a); a.b;");
  }

  public void testAliasCreatedForObjectDepth1_3() {
    testSame("var a = {b: 0}; new f(a); a.b;");
  }

  public void testAliasCreatedForObjectDepth2_1() {
    test("var a = {}; a.b = {c: 0}; var d = a.b; a.b.c == d.c;",
         "var a$b = {c: 0}; var d = a$b; a$b.c == d.c;");
  }

  public void testAliasCreatedForObjectDepth2_2() {
    test("var a = {}; a.b = {c: 0}; for (var p in a.b) { e(a.b[p]); }",
         "var a$b = {c: 0}; for (var p in a$b) { e(a$b[p]); }");
  }

  public void testAliasCreatedForEnumDepth1_1() {
    // An enum's values are always collapsed, even if the enum object is
    // referenced in a such a way that an alias is created for it.
    test("/** @enum */ var a = {b: 0}; var c = a; c.b = 1; a.b != c.b;",
         "var a$b = 0; var a = {b: a$b}; var c = a; c.b = 1; a$b != c.b;");
  }

  public void testAliasCreatedForEnumDepth1_2() {
    test("/** @enum */ var a = {b: 0}; f(a); a.b;",
         "var a$b = 0; var a = {b: a$b}; f(a); a$b;");
  }

  public void testAliasCreatedForEnumDepth1_3() {
    test("/** @enum */ var a = {b: 0}; new f(a); a.b;",
         "var a$b = 0; var a = {b: a$b}; new f(a); a$b;");
  }

  public void testAliasCreatedForEnumDepth1_4() {
    test("/** @enum */ var a = {b: 0}; for (var p in a) { f(a[p]); }",
         "var a$b = 0; var a = {b: a$b}; for (var p in a) { f(a[p]); }");
  }

  public void testAliasCreatedForEnumDepth2_1() {
    test("var a = {}; /** @enum */ a.b = {c: 0};" +
         "var d = a.b; d.c = 1; a.b.c != d.c;",
         "var a$b$c = 0; var a$b = {c: a$b$c};" +
         "var d = a$b; d.c = 1; a$b$c != d.c;");
  }

  public void testAliasCreatedForEnumDepth2_2() {
    test("var a = {}; /** @enum */ a.b = {c: 0};" +
         "for (var p in a.b) { f(a.b[p]); }",
         "var a$b$c = 0; var a$b = {c: a$b$c};" +
         "for (var p in a$b) { f(a$b[p]); }");
  }

  public void testAliasCreatedForEnumDepth2_3() {
    test("var a = {}; var d = a; /** @enum */ a.b = {c: 0};" +
         "for (var p in a.b) { f(a.b[p]); }",
         "var a = {}; var d = a; var a$b$c = 0; var a$b = {c: a$b$c};" +
         "for (var p in a$b) { f(a$b[p]); }",
         null, CollapseProperties.UNSAFE_NAMESPACE_WARNING);
  }

  public void testAliasCreatedForEnumOfObjects() {
    test("var a = {}; " +
         "/** @enum {Object} */ a.b = {c: {d: 1}}; a.b.c;" +
         "searchEnum(a.b);",
         "var a$b$c = {d: 1};var a$b = {c: a$b$c}; a$b$c; " +
         "searchEnum(a$b)");
  }

  public void testAliasCreatedForEnumOfObjects2() {
    test("var a = {}; " +
         "/** @enum {Object} */ a.b = {c: {d: 1}}; a.b.c.d;" +
         "searchEnum(a.b);",
         "var a$b$c = {d: 1};var a$b = {c: a$b$c}; a$b$c.d; " +
         "searchEnum(a$b)");
  }

  public void testAliasCreatedForPropertyOfEnumOfObjects() {
    test("var a = {}; " +
         "/** @enum {Object} */ a.b = {c: {d: 1}}; a.b.c;" +
         "searchEnum(a.b.c);",
         "var a$b$c = {d: 1}; a$b$c; searchEnum(a$b$c);");
  }

  public void testAliasCreatedForPropertyOfEnumOfObjects2() {
    test("var a = {}; " +
         "/** @enum {Object} */ a.b = {c: {d: 1}}; a.b.c.d;" +
         "searchEnum(a.b.c);",
         "var a$b$c = {d: 1}; a$b$c.d; searchEnum(a$b$c);");
  }

  public void testMisusedEnumTag() {
    testSame("var a = {}; var d = a; a.b = function() {};" +
             "/** @enum */ a.b.c = 0; a.b.c;");
  }

  public void testMisusedConstructorTag() {
    testSame("var a = {}; var d = a; a.b = function() {};" +
             "/** @constructor */ a.b.c = 0; a.b.c;");
  }

  public void testAliasCreatedForFunctionDepth1_1() {
    testSame("var a = function(){}; a.b = 1; var c = a; c.b = 2; a.b != c.b;");
  }

  public void testAliasCreatedForCtorDepth1_1() {
    // A constructor's properties *are* collapsed even if the function is
    // referenced in a such a way that an alias is created for it,
    // since a function with custom properties is considered a class and its
    // non-prototype properties are considered static methods and variables.
    // People don't typically iterate through static members of a class or
    // refer to them using an alias for the class name.
    test("/** @constructor */ var a = function(){}; a.b = 1; " +
         "var c = a; c.b = 2; a.b != c.b;",
         "var a = function(){}; var a$b = 1; var c = a; c.b = 2; a$b != c.b;");
  }

  public void testAliasCreatedForFunctionDepth1_2() {
    testSame("var a = function(){}; a.b = 1; f(a); a.b;");
  }

  public void testAliasCreatedForCtorDepth1_2() {
    test("/** @constructor */ var a = function(){}; a.b = 1; f(a); a.b;",
         "var a = function(){}; var a$b = 1; f(a); a$b;");
  }

  public void testAliasCreatedForFunctionDepth1_3() {
    testSame("var a = function(){}; a.b = 1; new f(a); a.b;");
  }

  public void testAliasCreatedForCtorDepth1_3() {
    test("/** @constructor */ var a = function(){}; a.b = 1; new f(a); a.b;",
         "var a = function(){}; var a$b = 1; new f(a); a$b;");
  }

  public void testAliasCreatedForFunctionDepth2() {
    test(
        "var a = {}; a.b = function() {}; a.b.c = 1; var d = a.b;" +
        "a.b.c != d.c;",
        "var a$b = function() {}; a$b.c = 1; var d = a$b;" +
        "a$b.c != d.c;");
  }

  public void testAliasCreatedForCtorDepth2() {
    test("var a = {}; /** @constructor */ a.b = function() {}; " +
         "a.b.c = 1; var d = a.b;" +
         "a.b.c != d.c;",
         "var a$b = function() {}; var a$b$c = 1; var d = a$b;" +
         "a$b$c != d.c;");
  }

  public void testAliasCreatedForClassDepth1_1() {
    // A class's name is always collapsed, even if one of its prefixes is
    // referenced in a such a way that an alias is created for it.
    test("var a = {}; /** @constructor */ a.b = function(){};" +
         "var c = a; c.b = 0; a.b != c.b;",
         "var a = {}; var a$b = function(){};" +
         "var c = a; c.b = 0; a$b != c.b;", null,
         CollapseProperties.UNSAFE_NAMESPACE_WARNING);
  }

  public void testAliasCreatedForClassDepth1_2() {
    test("var a = {}; /** @constructor */ a.b = function(){}; f(a); a.b;",
         "var a = {}; var a$b = function(){}; f(a); a$b;",
         null, CollapseProperties.UNSAFE_NAMESPACE_WARNING);
  }

  public void testAliasCreatedForClassDepth1_3() {
    test("var a = {}; /** @constructor */ a.b = function(){}; new f(a); a.b;",
         "var a = {}; var a$b = function(){}; new f(a); a$b;",
         null, CollapseProperties.UNSAFE_NAMESPACE_WARNING);
  }

  public void testAliasCreatedForClassDepth2_1() {
    test("var a = {}; a.b = {}; /** @constructor */ a.b.c = function(){};" +
         "var d = a.b; a.b.c != d.c;",
         "var a$b = {}; var a$b$c = function(){};" +
         "var d = a$b; a$b$c != d.c;",
         null, CollapseProperties.UNSAFE_NAMESPACE_WARNING);
  }

  public void testAliasCreatedForClassDepth2_2() {
    test("var a = {}; a.b = {}; /** @constructor */ a.b.c = function(){};" +
         "f(a.b); a.b.c;",
         "var a$b = {}; var a$b$c = function(){}; f(a$b); a$b$c;",
         null, CollapseProperties.UNSAFE_NAMESPACE_WARNING);
  }

  public void testAliasCreatedForClassDepth2_3() {
    test("var a = {}; a.b = {}; /** @constructor */ a.b.c = function(){};" +
         "new f(a.b); a.b.c;",
         "var a$b = {}; var a$b$c = function(){}; new f(a$b); a$b$c;",
         null, CollapseProperties.UNSAFE_NAMESPACE_WARNING);
  }

  public void testAliasCreatedForClassProperty() {
    test("var a = {}; /** @constructor */ a.b = function(){};" +
         "a.b.c = {d: 3}; new f(a.b.c); a.b.c.d;",
         "var a$b = function(){}; var a$b$c = {d:3}; new f(a$b$c); a$b$c.d;");
  }

  public void testNestedObjLit() {
    test("var a = {}; a.b = {f: 0, c: {d: 1}}; var e = a.b.c.d",
         "var a$b$f = 0; var a$b$c$d = 1; var e = a$b$c$d;");
  }

  public void testObjLitDeclarationUsedInSameVarList() {
    // The collapsed properties must defined in the same place in the var list
    // where they were originally defined (and not, for example, at the end).
    test("var a = {b: {}, c: {}}; var d = a.b; var e = a.c;",
         "var a$b = {}; var a$c = {}; var d = a$b; var e = a$c;");
  }

  public void testPropGetInsideAnObjLit() {
    test("var x = {}; x.y = 1; var a = {}; a.b = {c: x.y}",
         "var x$y = 1; var a$b$c = x$y;");
  }

  public void testObjLitWithQuotedKeyThatDoesNotGetRead() {
    test("var a = {}; a.b = {c: 0, 'd': 1}; var e = a.b.c;",
         "var a$b$c = 0; var a$b$d = 1; var e = a$b$c;");
  }

  public void testObjLitWithQuotedKeyThatGetsRead() {
    test("var a = {}; a.b = {c: 0, 'd': 1}; var e = a.b['d'];",
         "var a$b = {c: 0, 'd': 1}; var e = a$b['d'];");
  }

  public void testFunctionWithQuotedPropertyThatDoesNotGetRead() {
    test("var a = {}; a.b = function() {}; a.b['d'] = 1;",
         "var a$b = function() {}; a$b['d'] = 1;");
  }

  public void testFunctionWithQuotedPropertyThatGetsRead() {
    test("var a = {}; a.b = function() {}; a.b['d'] = 1; f(a.b['d']);",
         "var a$b = function() {}; a$b['d'] = 1; f(a$b['d']);");
  }

  public void testObjLitAssignedToMultipleNames1() {
    // An object literal that's assigned to multiple names isn't collapsed.
    testSame("var a = b = {c: 0, d: 1}; var e = a.c; var f = b.d;");
  }

  public void testObjLitAssignedToMultipleNames2() {
    testSame("a = b = {c: 0, d: 1}; var e = a.c; var f = b.d;");
  }

  public void testObjLitRedefinedInGlobalScope() {
    testSame("a = {b: 0}; a = {c: 1}; var d = a.b; var e = a.c;");
  }

  public void testObjLitRedefinedInLocalScope() {
    test("var a = {}; a.b = {c: 0}; function d() { a.b = {c: 1}; } e(a.b.c);",
         "var a$b = {c: 0}; function d() { a$b = {c: 1}; } e(a$b.c);");
  }

  public void testObjLitAssignedInTernaryExpression1() {
    testSame("a = x ? {b: 0} : d; var c = a.b;");
  }

  public void testObjLitAssignedInTernaryExpression2() {
    testSame("a = x ? {b: 0} : {b: 1}; var c = a.b;");
  }

  public void testGlobalVarSetToObjLitConditionally1() {
    testSame("var a; if (x) a = {b: 0}; var c = x ? a.b : 0;");
  }

  public void testGlobalVarSetToObjLitConditionally1b() {
    test("if (x) var a = {b: 0}; var c = x ? a.b : 0;",
         "if (x) var a$b = 0; var c = x ? a$b : 0;");
  }

  public void testGlobalVarSetToObjLitConditionally2() {
    test("if (x) var a = {b: 0}; var c = a.b; var d = a.c;",
         "if (x){ var a$b = 0; var a = {}; }var c = a$b; var d = a.c;");
  }

  public void testGlobalVarSetToObjLitConditionally3() {
    testSame("var a; if (x) a = {b: 0}; else a = {b: 1}; var c = a.b;");
  }

  public void testObjectPropertySetToObjLitConditionally() {
    test("var a = {}; if (x) a.b = {c: 0}; var d = a.b ? a.b.c : 0;",
         "if (x){ var a$b$c = 0; var a$b = {} } var d = a$b ? a$b$c : 0;");
  }

  public void testFunctionPropertySetToObjLitConditionally() {
    test("function a() {} if (x) a.b = {c: 0}; var d = a.b ? a.b.c : 0;",
         "function a() {} if (x){ var a$b$c = 0; var a$b = {} }" +
         "var d = a$b ? a$b$c : 0;");
  }

  public void testPrototypePropertySetToAnObjectLiteral() {
    test("var a = {b: function(){}}; a.b.prototype.c = {d: 0};",
         "var a$b = function(){}; a$b.prototype.c = {d: 0};");
  }

  public void testObjectPropertyResetInLocalScope() {
    test("var z = {}; z.a = 0; function f() {z.a = 5; return z.a}",
         "var z$a = 0; function f() {z$a = 5; return z$a}");
  }

  public void testFunctionPropertyResetInLocalScope() {
    test("function z() {} z.a = 0; function f() {z.a = 5; return z.a}",
         "function z() {} var z$a = 0; function f() {z$a = 5; return z$a}");
  }

  public void testNamespaceResetInGlobalScope1() {
    test("var a = {}; /** @constructor */a.b = function() {}; a = {};",
         "var a = {}; var a$b = function() {}; a = {};",
         null, CollapseProperties.NAMESPACE_REDEFINED_WARNING);
  }

  public void testNamespaceResetInGlobalScope2() {
    test("var a = {}; a = {}; /** @constructor */a.b = function() {};",
         "var a = {}; a = {}; var a$b = function() {};",
         null, CollapseProperties.NAMESPACE_REDEFINED_WARNING);
  }

  public void testNamespaceResetInLocalScope1() {
    test("var a = {}; /** @constructor */a.b = function() {};" +
         " function f() { a = {}; }",
         "var a = {};var a$b = function() {};" +
         " function f() { a = {}; }",
         null, CollapseProperties.NAMESPACE_REDEFINED_WARNING);
  }

  public void testNamespaceResetInLocalScope2() {
    test("var a = {}; function f() { a = {}; }" +
         " /** @constructor */a.b = function() {};",
         "var a = {}; function f() { a = {}; }" +
         " var a$b = function() {};",
         null, CollapseProperties.NAMESPACE_REDEFINED_WARNING);
  }

  public void testNamespaceDefinedInLocalScope() {
    test("var a = {}; (function() { a.b = {}; })();" +
         " /** @constructor */a.b.c = function() {};",
         "var a$b; (function() { a$b = {}; })(); var a$b$c = function() {};");
  }

  public void testAddPropertyToObjectInLocalScopeDepth1() {
    test("var a = {b: 0}; function f() { a.c = 5; return a.c; }",
         "var a$b = 0; var a$c; function f() { a$c = 5; return a$c; }");
  }

  public void testAddPropertyToObjectInLocalScopeDepth2() {
    test("var a = {}; a.b = {}; (function() {a.b.c = 0;})(); x = a.b.c;",
         "var a$b$c; (function() {a$b$c = 0;})(); x = a$b$c;");
  }

  public void testAddPropertyToFunctionInLocalScopeDepth1() {
    test("function a() {} function f() { a.c = 5; return a.c; }",
         "function a() {} var a$c; function f() { a$c = 5; return a$c; }");
  }

  public void testAddPropertyToFunctionInLocalScopeDepth2() {
    test("var a = {}; a.b = function() {}; function f() {a.b.c = 0;}",
         "var a$b = function() {}; var a$b$c; function f() {a$b$c = 0;}");
  }

  public void testAddPropertyToUncollapsibleObjectInLocalScopeDepth1() {
    testSame("var a = {}; var c = a; (function() {a.b = 0;})(); a.b;");
  }

  public void testAddPropertyToUncollapsibleFunctionInLocalScopeDepth1() {
    testSame("function a() {} var c = a; (function() {a.b = 0;})(); a.b;");
  }

  public void testAddPropertyToUncollapsibleNamedCtorInLocalScopeDepth1() {
    testSame(
          "/** @constructor */ function a() {} var a$b; var c = a; " +
          "(function() {a$b = 0;})(); a$b;");
  }

  public void testAddPropertyToUncollapsibleCtorInLocalScopeDepth1() {
    test("/** @constructor */ var a = function() {}; var c = a; " +
         "(function() {a.b = 0;})(); a.b;",
         "var a = function() {}; var a$b; " +
         "var c = a; (function() {a$b = 0;})(); a$b;");
  }

  public void testAddPropertyToUncollapsibleObjectInLocalScopeDepth2() {
    test("var a = {}; a.b = {}; var d = a.b;" +
         "(function() {a.b.c = 0;})(); a.b.c;",
         "var a$b = {}; var d = a$b;" +
         "(function() {a$b.c = 0;})(); a$b.c;");
  }

  public void testAddPropertyToUncollapsibleFunctionInLocalScopeDepth2() {
    test("var a = {}; a.b = function (){}; var d = a.b;" +
         "(function() {a.b.c = 0;})(); a.b.c;",
         "var a$b = function (){}; var d = a$b;" +
         "(function() {a$b.c = 0;})(); a$b.c;");
  }

  public void testAddPropertyToUncollapsibleCtorInLocalScopeDepth2() {
    test("var a = {}; /** @constructor */ a.b = function (){}; var d = a.b;" +
         "(function() {a.b.c = 0;})(); a.b.c;",
         "var a$b = function (){}; var a$b$c; var d = a$b;" +
         "(function() {a$b$c = 0;})(); a$b$c;");
  }

  public void testPropertyOfChildFuncOfUncollapsibleObjectDepth1() {
    testSame("var a = {}; var c = a; a.b = function (){}; a.b.x = 0; a.b.x;");
  }

  public void testPropertyOfChildFuncOfUncollapsibleObjectDepth2() {
    test("var a = {}; a.b = {}; var c = a.b;" +
         "a.b.c = function (){}; a.b.c.x = 0; a.b.c.x;",
         "var a$b = {}; var c = a$b;" +
         "a$b.c = function (){}; a$b.c.x = 0; a$b.c.x;");
  }

  public void testAddPropertyToChildFuncOfUncollapsibleObjectInLocalScope() {
    testSame("var a = {}; a.b = function (){}; a.b.x = 0;" +
             "var c = a; (function() {a.b.y = 1;})(); a.b.x; a.b.y;");
  }

  public void testAddPropertyToChildTypeOfUncollapsibleObjectInLocalScope() {
    test("var a = {}; /** @constructor */ a.b = function (){}; a.b.x = 0;" +
         "var c = a; (function() {a.b.y = 1;})(); a.b.x; a.b.y;",
         "var a = {}; var a$b = function (){}; var a$b$y; var a$b$x = 0;" +
         "var c = a; (function() {a$b$y = 1;})(); a$b$x; a$b$y;",
         null, CollapseProperties.UNSAFE_NAMESPACE_WARNING);
  }

  public void testAddPropertyToChildOfUncollapsibleFunctionInLocalScope() {
    testSame(
        "function a() {} a.b = {x: 0}; var c = a;" +
        "(function() {a.b.y = 0;})(); a.b.y;");
  }

  public void testAddPropertyToChildOfUncollapsibleCtorInLocalScope() {
    test("/** @constructor */ var a = function() {}; a.b = {x: 0}; var c = a;" +
         "(function() {a.b.y = 0;})(); a.b.y;",
         "var a = function() {}; var a$b$x = 0; var a$b$y; var c = a;" +
         "(function() {a$b$y = 0;})(); a$b$y;");
  }

  public void testResetObjectPropertyInLocalScope() {
    test("var a = {b: 0}; a.c = 1; function f() { a.c = 5; }",
         "var a$b = 0; var a$c = 1; function f() { a$c = 5; }");
  }

  public void testResetFunctionPropertyInLocalScope() {
    test("function a() {}; a.c = 1; function f() { a.c = 5; }",
         "function a() {}; var a$c = 1; function f() { a$c = 5; }");
  }

  public void testGlobalNameReferencedInLocalScopeBeforeDefined1() {
    // Because referencing global names earlier in the source code than they're
    // defined is such a common practice, we collapse them even though a runtime
    // exception could result (in the off-chance that the function gets called
    // before the alias variable is defined).
    test("var a = {b: 0}; function f() { a.c = 5; } a.c = 1;",
         "var a$b = 0; function f() { a$c = 5; } var a$c = 1;");
  }

  public void testGlobalNameReferencedInLocalScopeBeforeDefined2() {
    test("var a = {b: 0}; function f() { return a.c; } a.c = 1;",
         "var a$b = 0; function f() { return a$c; } var a$c = 1;");
  }

  public void testTwiceDefinedGlobalNameDepth1_1() {
    testSame("var a = {}; function f() { a.b(); }" +
             "a = function() {}; a.b = function() {};");
  }

  public void testTwiceDefinedGlobalNameDepth1_2() {
    testSame("var a = {}; /** @constructor */ a = function() {};" +
             "a.b = {}; a.b.c = 0; function f() { a.b.d = 1; }");
  }

  public void testTwiceDefinedGlobalNameDepth2() {
    test("var a = {}; a.b = {}; function f() { a.b.c(); }" +
         "a.b = function() {}; a.b.c = function() {};",
         "var a$b = {}; function f() { a$b.c(); }" +
         "a$b = function() {}; a$b.c = function() {};");
  }

  public void testFunctionCallDepth1() {
    test("var a = {}; a.b = function(){}; var c = a.b();",
         "var a$b = function(){}; var c = a$b()");
  }

  public void testFunctionCallDepth2() {
    test("var a = {}; a.b = {}; a.b.c = function(){}; a.b.c();",
         "var a$b$c = function(){}; a$b$c();");
  }

  public void testFunctionAlias() {
    test("var a = {}; a.b = {}; a.b.c = function(){}; a.b.d = a.b.c;",
         "var a$b$c = function(){}; var a$b$d = a$b$c;");
  }

  public void testCallToRedefinedFunction() {
    test("var a = {}; a.b = function(){}; a.b = function(){}; a.b();",
         "var a$b = function(){}; a$b = function(){}; a$b();");
  }

  public void testCollapsePrototypeName() {
    test("var a = {}; a.b = {}; a.b.c = function(){}; " +
         "a.b.c.prototype.d = function(){}; (new a.b.c()).d();",
         "var a$b$c = function(){}; a$b$c.prototype.d = function(){}; " +
         "new a$b$c().d();");
  }

  public void testReferencedPrototypeProperty() {
    test("var a = {b: {}}; a.b.c = function(){}; a.b.c.prototype.d = {};" +
         "e = a.b.c.prototype.d;",
         "var a$b$c = function(){}; a$b$c.prototype.d = {};" +
         "e = a$b$c.prototype.d;");
  }

  public void testSetStaticAndPrototypePropertiesOnFunction() {
    test("var a = {}; a.b = function(){}; a.b.prototype.d = 0; a.b.c = 1;",
         "var a$b = function(){}; a$b.prototype.d = 0; var a$b$c = 1;");
  }

  public void testReadUndefinedPropertyDepth1() {
    test("var a = {b: 0}; var c = a.d;",
         "var a$b = 0; var a = {}; var c = a.d;");
  }

  public void testReadUndefinedPropertyDepth2() {
    test("var a = {b: {c: 0}}; f(a.b.c); f(a.b.d);",
         "var a$b$c = 0; var a$b = {}; f(a$b$c); f(a$b.d);");
  }

  public void testCallUndefinedMethodOnObjLitDepth1() {
    test("var a = {b: 0}; a.c();",
         "var a$b = 0; var a = {}; a.c();");
  }

  public void testCallUndefinedMethodOnObjLitDepth2() {
    test("var a = {b: {}}; a.b.c = function() {}; a.b.c(); a.b.d();",
         "var a$b = {}; var a$b$c = function() {}; a$b$c(); a$b.d();");
  }

  public void testPropertiesOfAnUndefinedVar() {
    testSame("a.document = d; f(a.document.innerHTML);");
  }

  public void testPropertyOfAnObjectThatIsNeitherFunctionNorObjLit() {
    testSame("var a = window; a.document = d; f(a.document)");
  }

  public void testStaticFunctionReferencingThis1() {
    // Note: Google's Javascript Style Guide says to avoid using the 'this'
    // keyword in a static function.
    test("var a = {}; a.b = function() {this.c}; var d = a.b;",
         "var a$b = function() {this.c}; var d = a$b;", null, UNSAFE_THIS);
  }

  public void testStaticFunctionReferencingThis2() {
    // This gives no warning, because "this" is in a scope whose name is not
    // getting collapsed.
    test("var a = {}; " +
         "a.b = function() { return function(){ return this; }; };",
         "var a$b = function() { return function(){ return this; }; };");
  }

  public void testStaticFunctionReferencingThis3() {
    test("var a = {b: function() {this.c}};",
         "var a$b = function() { this.c };", null, UNSAFE_THIS);
  }

  public void testStaticFunctionReferencingThis4() {
    test("var a = {/** @this {Element} */ b: function() {this.c}};",
         "var a$b = function() { this.c };");
  }

  public void testPrototypeMethodReferencingThis() {
    testSame("var A = function(){}; A.prototype = {b: function() {this.c}};");
  }

  public void testConstructorReferencingThis() {
    test("var a = {}; " +
         "/** @constructor */ a.b = function() { this.a = 3; };",
         "var a$b = function() { this.a = 3; };");
  }

  public void testSafeReferenceOfThis() {
    test("var a = {}; " +
         "/** @this {Object} */ a.b = function() { this.a = 3; };",
         "var a$b = function() { this.a = 3; };");
  }

  public void testGlobalFunctionReferenceOfThis() {
    testSame("var a = function() { this.a = 3; };");
  }

  public void testFunctionGivenTwoNames() {
    // It's okay to collapse f's properties because g is not added to the
    // global scope as an alias for f. (Try it in your browser.)
    test("var f = function g() {}; f.a = 1; h(f.a);",
         "var f = function g() {}; var f$a = 1; h(f$a);");
  }

  public void testObjLitWithUsedNumericKey() {
    testSame("a = {40: {}, c: {}}; var d = a[40]; var e = a.c;");
  }

  public void testObjLitWithUnusedNumericKey() {
    test("var a = {40: {}, c: {}}; var e = a.c;",
         "var a$1 = {}; var a$c = {}; var e = a$c");
  }

  public void testObjLitWithNonIdentifierKeys() {
    testSame("a = {' ': 0, ',': 1}; var c = a[' '];");
  }

  public void testChainedAssignments1() {
    test("var x = {}; x.y = a = 0;",
         "var x$y = a = 0;");
  }

  public void testChainedAssignments2() {
    test("var x = {}; x.y = a = b = c();",
         "var x$y = a = b = c();");
  }

  public void testChainedAssignments3() {
    test("var x = {y: 1}; a = b = x.y;",
         "var x$y = 1; a = b = x$y;");
  }

  public void testChainedAssignments4() {
    test("var x = {}; a = b = x.y;",
         "var x = {}; a = b = x.y;");
  }

  public void testChainedAssignments5() {
    test("var x = {}; a = x.y = 0;", "var x$y; a = x$y = 0;");
  }

  public void testChainedAssignments6() {
    test("var x = {}; a = x.y = b = c();",
         "var x$y; a = x$y = b = c();");
  }

  public void testChainedAssignments7() {
    test("var x = {}; a = x.y = {}; /** @constructor */ x.y.z = function() {};",
         "var x$y; a = x$y = {}; var x$y$z = function() {};",
         null, CollapseProperties.UNSAFE_NAMESPACE_WARNING);
  }

  public void testChainedVarAssignments1() {
    test("var x = {y: 1}; var a = x.y = 0;",
         "var x$y = 1; var a = x$y = 0;");
  }

  public void testChainedVarAssignments2() {
    test("var x = {y: 1}; var a = x.y = b = 0;",
         "var x$y = 1; var a = x$y = b = 0;");
  }

  public void testChainedVarAssignments3() {
    test("var x = {y: {z: 1}}; var b = 0; var a = x.y.z = 1; var c = 2;",
         "var x$y$z = 1; var b = 0; var a = x$y$z = 1; var c = 2;");
  }

  public void testChainedVarAssignments4() {
    test("var x = {}; var a = b = x.y = 0;",
         "var x$y; var a = b = x$y = 0;");
  }

  public void testChainedVarAssignments5() {
    test("var x = {y: {}}; var a = b = x.y.z = 0;",
         "var x$y$z; var a = b = x$y$z = 0;");
  }

  public void testPeerAndSubpropertyOfUncollapsibleProperty() {
    test("var x = {}; var a = x.y = 0; x.w = 1; x.y.z = 2;" +
         "b = x.w; c = x.y.z;",
         "var x$y; var a = x$y = 0; var x$w = 1; x$y.z = 2;" +
         "b = x$w; c = x$y.z;");
  }

  public void testComplexAssignmentAfterInitialAssignment() {
    test("var d = {}; d.e = {}; d.e.f = 0; a = b = d.e.f = 1;",
         "var d$e$f = 0; a = b = d$e$f = 1;");
  }

  public void testRenamePrefixOfUncollapsibleProperty() {
    test("var d = {}; d.e = {}; a = b = d.e.f = 0;",
         "var d$e$f; a = b = d$e$f = 0;");
  }

  public void testNewOperator() {
    // Using the new operator on a name doesn't prevent its (static) properties
    // from getting collapsed.
    test("var a = {}; a.b = function() {}; a.b.c = 1; var d = new a.b();",
         "var a$b = function() {}; var a$b$c = 1; var d = new a$b();");
  }

  public void testMethodCall() {
    test("var a = {}; a.b = function() {}; var d = a.b();",
         "var a$b = function() {}; var d = a$b();");
  }

  public void testObjLitDefinedInLocalScopeIsLeftAlone() {
    test("var a = {}; a.b = function() {};" +
         "a.b.prototype.f_ = function() {" +
         "  var x = { p: '', q: '', r: ''}; var y = x.q;" +
         "};",
         "var a$b = function() {};" +
         "a$b.prototype.f_ = function() {" +
         "  var x = { p: '', q: '', r: ''}; var y = x.q;" +
         "};");
  }

  public void testPropertiesOnBothSidesOfAssignment() {
    // This verifies that replacements are done in the right order. Collapsing
    // the lvalue in an assignment affects the parse tree immediately above
    // the rvalue, so we update all rvalues before any lvalues.
    test("var a = {b: 0}; a.c = a.b;", "var a$b = 0; var a$c = a$b;");
  }

  public void testCallOnUndefinedProperty() {
    // The "inherits" property is not explicitly defined on a.b anywhere, but
    // it is accessed as though it certainly exists (it is called), so we infer
    // that it must be an uncollapsible property that has come into existence
    // some other way.
    test("var a = {}; a.b = function(){}; a.b.inherits(x);",
         "var a$b = function(){}; a$b.inherits(x);");
  }

  public void testGetPropOnUndefinedProperty() {
    // The "superClass_" property is not explicitly defined on a.b anywhere,
    // but it is accessed as though it certainly exists (a subproperty of it
    // is accessed), so we infer that it must be an uncollapsible property that
    // has come into existence some other way.
    test("var a = {b: function(){}}; a.b.prototype.c =" +
         "function() { a.b.superClass_.c.call(this); }",
         "var a$b = function(){}; a$b.prototype.c =" +
         "function() { a$b.superClass_.c.call(this); }");
  }

  public void testLocalAlias1() {
    test("var a = {b: 3}; function f() { var x = a; f(x.b); }",
         "var a$b = 3; function f() { var x = null; f(a$b); }");
  }

  public void testLocalAlias2() {
    test("var a = {b: 3, c: 4}; function f() { var x = a; f(x.b); f(x.c);}",
         "var a$b = 3; var a$c = 4; " +
         "function f() { var x = null; f(a$b); f(a$c);}");
  }

  public void testLocalAlias3() {
    test("var a = {b: 3, c: {d: 5}}; " +
         "function f() { var x = a; f(x.b); f(x.c); f(x.c.d); }",
         "var a$b = 3; var a$c = {d: 5}; " +
         "function f() { var x = null; f(a$b); f(a$c); f(a$c.d);}");
  }

  public void testLocalAlias4() {
    test("var a = {b: 3}; var c = {d: 5}; " +
         "function f() { var x = a; var y = c; f(x.b); f(y.d); }",
         "var a$b = 3; var c$d = 5; " +
         "function f() { var x = null; var y = null; f(a$b); f(c$d);}");
  }

  public void testLocalAlias5() {
    test("var a = {b: {c: 5}}; " +
         "function f() { var x = a; var y = x.b; f(a.b.c); f(y.c); }",
         "var a$b$c = 5; " +
         "function f() { var x = null; var y = null; f(a$b$c); f(a$b$c);}");
  }

  public void testLocalAlias6() {
    test("var a = {b: 3}; function f() { var x = a; if (x.b) { f(x.b); } }",
         "var a$b = 3; function f() { var x = null; if (a$b) { f(a$b); } }");
  }

  public void testLocalAlias7() {
    test("var a = {b: {c: 5}}; function f() { var x = a.b; f(x.c); }",
         "var a$b$c = 5; function f() { var x = null; f(a$b$c); }");
  }

  public void testGlobalWriteToAncestor() {
    testSame("var a = {b: 3}; function f() { var x = a; f(a.b); } a = 5;");
  }

  public void testGlobalWriteToNonAncestor() {
    test("var a = {b: 3}; function f() { var x = a; f(a.b); } a.b = 5;",
         "var a$b = 3; function f() { var x = null; f(a$b); } a$b = 5;");
  }

  public void testLocalWriteToAncestor() {
    testSame("var a = {b: 3}; function f() { a = 5; var x = a; f(a.b); } ");
  }

  public void testLocalWriteToNonAncestor() {
    test("var a = {b: 3}; " +
         "function f() { a.b = 5; var x = a; f(a.b); }",
         "var a$b = 3; function f() { a$b = 5; var x = null; f(a$b); } ");
  }

  public void testNonWellformedAlias1() {
    testSame("var a = {b: 3}; function f() { f(x); var x = a; f(x.b); }");
  }

  public void testNonWellformedAlias2() {
    testSame("var a = {b: 3}; " +
             "function f() { if (false) { var x = a; f(x.b); } f(x); }");
  }

  public void testLocalAliasOfAncestor() {
    testSame("var a = {b: {c: 5}}; function g() { f(a); } " +
             "function f() { var x = a.b; f(x.c); }");
  }

  public void testGlobalAliasOfAncestor() {
    testSame("var a = {b: {c: 5}}; var y = a; " +
             "function f() { var x = a.b; f(x.c); }");
  }

  public void testLocalAliasOfOtherName() {
    testSame("var foo = function() { return {b: 3}; };" +
             "var a = foo(); a.b = 5; " +
             "function f() { var x = a.b; f(x); }");
  }

  public void testLocalAliasOfFunction() {
    test("var a = function() {}; a.b = 5; " +
         "function f() { var x = a.b; f(x); }",
         "var a = function() {}; var a$b = 5; " +
         "function f() { var x = null; f(a$b); }");
  }

  public void testNoInlineGetpropIntoCall() {
    test("var b = x; function f() { var a = b; a(); }",
         "var b = x; function f() { var a = null; b(); }");
    test("var b = {}; b.c = x; function f() { var a = b.c; a(); }",
         "var b$c = x; function f() { var a = null; b$c(); }");
  }

  public void testCollapsePropertyOnExternType() {
    collapsePropertiesOnExternTypes = true;
    test("String.myFunc = function() {}; String.myFunc();",
         "var String$myFunc = function() {}; String$myFunc()");
  }

  public void testCollapseForEachWithoutExterns() {
    collapsePropertiesOnExternTypes = true;
    test("/** @constructor */function Array(){};\n",
         "if (!Array.forEach) {\n" +
         "  Array.forEach = function() {};\n" +
         "}",
         "if (!Array$forEach) {\n" +
         "  var Array$forEach = function() {};\n" +
         "}", null, null);
  }

  public void testNoCollapseForEachInExterns() {
    collapsePropertiesOnExternTypes = true;
    test("/** @constructor */ function Array() {}" +
         "Array.forEach = function() {}",
         "if (!Array.forEach) {\n" +
         "  Array.forEach = function() {};\n" +
         "}",
         "if (!Array.forEach) {\n" +
         "  Array.forEach = function() {};\n" +
         "}", null, null);
  }

  public void testDoNotCollapsePropertyOnExternType() {
    collapsePropertiesOnExternTypes = false;
    test("String.myFunc = function() {}; String.myFunc()",
         "String.myFunc = function() {}; String.myFunc()");
  }

  public void testBug1704733() {
    String prelude =
        "function protect(x) { return x; }" +
        "function O() {}" +
        "protect(O).m1 = function() {};" +
        "protect(O).m2 = function() {};" +
        "protect(O).m3 = function() {};";

    testSame(prelude +
        "alert(O.m1); alert(O.m2()); alert(!O.m3);");
  }

  public void testBug1956277() {
    test("var CONST = {}; CONST.URL = 3;",
         "var CONST$URL = 3;");
  }

  public void testBug1974371() {
    test(
        "/** @enum {Object} */ var Foo = {A: {c: 2}, B: {c: 3}};" +
        "for (var key in Foo) {}",
        "var Foo$A = {c: 2}; var Foo$B = {c: 3};" +
        "var Foo = {A: Foo$A, B: Foo$B};" +
         "for (var key in Foo) {}");
  }

  private final String COMMON_ENUM =
        "/** @enum {Object} */ var Foo = {A: {c: 2}, B: {c: 3}};";

  public void testEnumOfObjects1() {
    test(
        COMMON_ENUM +
        "for (var key in Foo.A) {}",
         "var Foo$A = {c: 2}; var Foo$B$c = 3; for (var key in Foo$A) {}");
  }

  public void testEnumOfObjects2() {
    test(
        COMMON_ENUM +
        "foo(Foo.A.c);",
         "var Foo$A$c = 2; var Foo$B$c = 3; foo(Foo$A$c);");
  }

  public void testEnumOfObjects3() {
    test(
        "var x = {c: 2}; var y = {c: 3};" +
        "/** @enum {Object} */ var Foo = {A: x, B: y};" +
        "for (var key in Foo) {}",
        "var x = {c: 2}; var y = {c: 3};" +
        "var Foo$A = x; var Foo$B = y; var Foo = {A: Foo$A, B: Foo$B};" +
        "for (var key in Foo) {}");
  }

  public void testEnumOfObjects4() {
    // Note that this produces bad code, but that's OK, because
    // checkConsts will yell at you for reassigning an enum value.
    // (enum values have to be constant).
    test(
        COMMON_ENUM +
        "for (var key in Foo) {} Foo.A = 3; alert(Foo.A);",
        "var Foo$A = {c: 2}; var Foo$B = {c: 3};" +
        "var Foo = {A: Foo$A, B: Foo$B};" +
        "for (var key in Foo) {} Foo$A = 3; alert(Foo$A);");
  }

  public void testObjectOfObjects1() {
    // Basically the same as testEnumOfObjects4, but without the
    // constant enum values.
    testSame(
        "var Foo = {a: {c: 2}, b: {c: 3}};" +
        "for (var key in Foo) {} Foo.a = 3; alert(Foo.a);");
  }

  public void testReferenceInAnonymousObject0() {
    test("var a = {};" +
         "a.b = function(){};" +
         "a.b.prototype.c = function(){};" +
         "var d = a.b.prototype.c;",
         "var a$b = function(){};" +
         "a$b.prototype.c = function(){};" +
         "var d = a$b.prototype.c;");
  }

  public void testReferenceInAnonymousObject1() {
    test("var a = {};" +
         "a.b = function(){};" +
         "var d = a.b.prototype.c;",
         "var a$b = function(){};" +
         "var d = a$b.prototype.c;");
  }

  public void testReferenceInAnonymousObject2() {
    test("var a = {};" +
         "a.b = function(){};" +
         "a.b.prototype.c = function(){};" +
         "var d = {c: a.b.prototype.c};",
         "var a$b = function(){};" +
         "a$b.prototype.c = function(){};" +
         "var d$c = a$b.prototype.c;");
  }

  public void testReferenceInAnonymousObject3() {
    test("function CreateClass(a$$1) {}" +
         "var a = {};" +
         "a.b = function(){};" +
         "a.b.prototype.c = function(){};" +
         "a.d = CreateClass({c: a.b.prototype.c});",
         "function CreateClass(a$$1) {}" +
         "var a$b = function(){};" +
         "a$b.prototype.c = function(){};" +
         "var a$d = CreateClass({c: a$b.prototype.c});");
  }

  public void testReferenceInAnonymousObject4() {
    test("function CreateClass(a) {}" +
         "var a = {};" +
         "a.b = CreateClass({c: function() {}});" +
         "a.d = CreateClass({c: a.b.c});",
         "function CreateClass(a$$1) {}" +
         "var a$b = CreateClass({c: function() {}});" +
         "var a$d = CreateClass({c: a$b.c});");
  }

  public void testReferenceInAnonymousObject5() {
    test("function CreateClass(a) {}" +
         "var a = {};" +
         "a.b = CreateClass({c: function() {}});" +
         "a.d = CreateClass({c: a.b.prototype.c});",
         "function CreateClass(a$$1) {}" +
         "var a$b = CreateClass({c: function() {}});" +
         "var a$d = CreateClass({c: a$b.prototype.c});");
  }

  public void testCrashInCommaOperator() {
    test("var a = {}; a.b = function() {},a.b();",
         "var a$b; a$b=function() {},a$b();");
  }

  public void testCrashInNestedAssign() {
    test("var a = {}; if (a.b = function() {}) a.b();",
         "var a$b; if (a$b=function() {}) { a$b(); }");
  }

  public void testTwinReferenceCancelsChildCollapsing() {
    test("var a = {}; if (a.b = function() {}) { a.b.c = 3; a.b(a.b.c); }",
         "var a$b; if (a$b = function() {}) { a$b.c = 3; a$b(a$b.c); }");
  }

  public void testPropWithDollarSign() {
    test("var a = {$: 3}", "var a$$0 = 3;");
  }

  public void testPropWithDollarSign2() {
    test("var a = {$: function(){}}", "var a$$0 = function(){};");
  }

  public void testPropWithDollarSign3() {
    test("var a = {b: {c: 3}, b$c: function(){}}",
         "var a$b$c = 3; var a$b$0c = function(){};");
  }

  public void testPropWithDollarSign4() {
    test("var a = {$$: {$$$: 3}};", "var a$$0$0$$0$0$0 = 3;");
  }

  public void testPropWithDollarSign5() {
    test("var a = {b: {$0c: true}, b$0c: false};",
         "var a$b$$00c = true; var a$b$00c = false;");
  }

  public void testConstKey() {
    test("var foo = {A: 3};", "var foo$A = 3;");
  }

  public void testPropertyOnGlobalCtor() {
    test("/** @constructor */ function Map() {} Map.foo = 3; Map;",
         "function Map() {} var Map$foo = 3; Map;");
  }

  public void testPropertyOnGlobalFunction() {
    testSame("function Map() {} Map.foo = 3; Map;");
  }
}
