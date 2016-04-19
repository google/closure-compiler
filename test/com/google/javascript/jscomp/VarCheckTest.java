/*
 * Copyright 2005 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.VarCheck.VAR_MULTIPLY_DECLARED_ERROR;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

public final class VarCheckTest extends Es6CompilerTestCase {
  private static final String EXTERNS = "var window; function alert() {}";

  private CheckLevel strictModuleDepErrorLevel;
  private boolean sanityCheck = false;

  private CheckLevel externValidationErrorLevel;

  private boolean declarationCheck;

  public VarCheckTest() {
    super(EXTERNS);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // Setup value set by individual tests to the appropriate defaults.
    super.allowExternsChanges(true);
    strictModuleDepErrorLevel = CheckLevel.OFF;
    externValidationErrorLevel = null;
    sanityCheck = false;
    declarationCheck = false;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.STRICT_MODULE_DEP_CHECK,
        strictModuleDepErrorLevel);
    if (externValidationErrorLevel != null) {
     options.setWarningLevel(DiagnosticGroups.EXTERNS_VALIDATION,
         externValidationErrorLevel);
    }
    return options;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override public void process(Node externs, Node root) {
        new VarCheck(compiler, sanityCheck).process(externs, root);
        if (!sanityCheck && !compiler.hasErrors()) {
          // If the original test turned off sanity check, make sure our synthesized
          // code passes it.
          new VarCheck(compiler, true).process(externs, root);
        }
        if (declarationCheck) {
          new VariableTestCheck(compiler).process(externs, root);
        }
      }
    };
  }

  @Override
  protected int getNumRepetitions() {
    // Because we synthesize externs, the second pass won't emit a warning.
    return 1;
  }

  public void testShorthandObjLit() {
    testErrorEs6("var x = {y};", VarCheck.UNDEFINED_VAR_ERROR);
    testSameEs6("var {x} = {x: 5}; let y = x;");
  }

  public void testBreak() {
    testSame("a: while(1) break a;");
  }

  public void testContinue() {
    testSame("a: while(1) continue a;");
  }

  public void testReferencedVarNotDefined() {
    testError("x = 0;", VarCheck.UNDEFINED_VAR_ERROR);
  }

  public void testReferencedLetNotDefined() {
    testErrorEs6("{ let x = 1; } var y = x;", VarCheck.UNDEFINED_VAR_ERROR);
  }

  public void testReferencedLetDefined1() {
    testSameEs6("let x; x = 1;");
  }

  public void testReferencedLetDefined2() {
    testSameEs6("let x; function y() {x = 1;}");
  }

  public void testReferencedConstDefined2() {
    testSameEs6("const x = 1; var y = x + 1;");
  }

  public void testReferencedVarDefined1() {
    testSame("var x, y; x=1;");
  }

  public void testReferencedVarDefined2() {
    testSame("var x; function y() {x=1;}");
  }

  public void testReferencedVarsExternallyDefined() {
    testSame("var x = window; alert(x);");
  }

  public void testMultiplyDeclaredVars1() {
    testError("var x = 1; var x = 2;", VarCheck.VAR_MULTIPLY_DECLARED_ERROR);
  }

  public void testMultiplyDeclaredVars2() {
    testSame("var y; try { y=1 } catch (x) {} try { y=1 } catch (x) {}");
  }

  public void testMultiplyDeclaredVars3() {
    testSame("try { var x = 1; x *=2; } catch (x) {}");
  }

  public void testMultiplyDeclaredVars4() {
    testSame("x;", "var x = 1; var x = 2;",
        VarCheck.VAR_MULTIPLY_DECLARED_ERROR, true);
  }

  public void testMultiplyDeclaredLets() {
    testErrorEs6("let x = 1; let x = 2;", VarCheck.LET_CONST_CLASS_MULTIPLY_DECLARED_ERROR);
    testErrorEs6("let x = 1; var x = 2;", VarCheck.LET_CONST_CLASS_MULTIPLY_DECLARED_ERROR);
    testErrorEs6("var x = 1; let x = 2;", VarCheck.LET_CONST_CLASS_MULTIPLY_DECLARED_ERROR);
  }

  public void testMultiplyDeclaredConsts() {
    testErrorEs6("const x = 1; const x = 2;", VarCheck.LET_CONST_CLASS_MULTIPLY_DECLARED_ERROR);
    testErrorEs6("const x = 1; var x = 2;", VarCheck.LET_CONST_CLASS_MULTIPLY_DECLARED_ERROR);
    testErrorEs6("var x = 1; const x = 2;", VarCheck.LET_CONST_CLASS_MULTIPLY_DECLARED_ERROR);
  }

  public void testMultiplyDeclareLetsInDifferentScope() {
    testSameEs6("let x = 1; if (123) {let x = 2;}");
    testSameEs6("try {let x = 1;} catch(x){}");
  }

  public void testReferencedVarDefinedClass() {
    testErrorEs6("var x; class x{ }", VarCheck.LET_CONST_CLASS_MULTIPLY_DECLARED_ERROR);
    testErrorEs6("let x; class x{ }", VarCheck.LET_CONST_CLASS_MULTIPLY_DECLARED_ERROR);
    testErrorEs6("const x = 1; class x{ }", VarCheck.LET_CONST_CLASS_MULTIPLY_DECLARED_ERROR);
    testErrorEs6("class x{ } let x;", VarCheck.LET_CONST_CLASS_MULTIPLY_DECLARED_ERROR);
  }

  public void testNamedClass() {
    testSameEs6("class x {}");
    testSameEs6("var x = class x {};");
    testSameEs6("var y = class x {};");
    testSameEs6("var y = class x { foo() { return new x; } };");
    testErrorEs6("var Foo = class extends Bar {};", VarCheck.UNDEFINED_VAR_ERROR);
  }

  public void testVarReferenceInExterns() {
    testSame("asdf;", "var /** @suppress {duplicate} */ asdf;",
        VarCheck.NAME_REFERENCE_IN_EXTERNS_ERROR);
  }

  public void testCallInExterns() {
    testSame("yz();", "function /** @suppress {duplicate} */ yz() {}",
        VarCheck.NAME_REFERENCE_IN_EXTERNS_ERROR);
  }

  public void testVarDeclarationInExterns() {
    testSame("var asdf;", "asdf;", null);
  }

  public void testVarAssignmentInExterns() {
    testSame("/** @type{{foo:string}} */ var foo; var asdf = foo;", "asdf.foo;", null);
  }

  public void testAliasesInExterns() {
    externValidationErrorLevel = CheckLevel.ERROR;

    testSame("var foo; /** @const */ var asdf = foo;", "", null);
    testSame(
        "var Foo; var ns = {}; /** @const */ ns.FooAlias = Foo;", "", null);
    testSame(
        LINE_JOINER.join(
            "var ns = {}; /** @constructor */ ns.Foo = function() {};",
            "var ns2 = {}; /** @const */ ns2.Bar = ns.Foo;"),
        "", null);
  }

  public void testDuplicateNamespaceInExterns() {
    testExternChanges(
        "/** @const */ var ns = {}; /** @const */ var ns = {};",
        "",
        "/** @const */ var ns = {};");
  }

  public void testLetDeclarationInExterns() {
    testSameEs6("let asdf;", "asdf;", null);
  }

  public void testConstDeclarationInExterns() {
    testSameEs6("const asdf = 1;", "asdf;", null);
  }

  public void testNewInExterns() {
    // Class is not hoisted.
    testSameEs6("x = new Klass();", "class Klass{}",
        VarCheck.UNDEFINED_VAR_ERROR, true);
  }

  public void testPropReferenceInExterns1() {
    testSame("asdf.foo;", "var /** @suppress {duplicate} */ asdf;",
        VarCheck.UNDEFINED_EXTERN_VAR_ERROR);
  }

  public void testPropReferenceInExterns2() {
    testSame("asdf.foo;", "",
        VarCheck.UNDEFINED_VAR_ERROR, true);
  }

  public void testPropReferenceInExterns3() {
    testSame("asdf.foo;", "var /** @suppress {duplicate} */ asdf;",
        VarCheck.UNDEFINED_EXTERN_VAR_ERROR);

    externValidationErrorLevel = CheckLevel.ERROR;
    testSame(
        "asdf.foo;", "var asdf;",
         VarCheck.UNDEFINED_EXTERN_VAR_ERROR, true);

    externValidationErrorLevel = CheckLevel.OFF;
    test("asdf.foo;", "var asdf;", "var /** @suppress {duplicate} */ asdf;", null, null);
  }

  public void testPropReferenceInExterns4() {
    testSameEs6("asdf.foo;", "let asdf;",
        VarCheck.UNDEFINED_EXTERN_VAR_ERROR);
  }

  public void testPropReferenceInExterns5() {
    testSameEs6("asdf.foo;", "class asdf {}",
        VarCheck.UNDEFINED_EXTERN_VAR_ERROR);
  }

  public void testVarInWithBlock() {
    testError("var a = {b:5}; with (a){b;}", VarCheck.UNDEFINED_VAR_ERROR);
  }

  public void testFunctionDeclaredInBlock() {
    testError("if (true) {function foo() {}} foo();", VarCheck.UNDEFINED_VAR_ERROR);
    testError("foo(); if (true) {function foo() {}}", VarCheck.UNDEFINED_VAR_ERROR);

    testSameEs6("if (true) {var foo = ()=>{}} foo();");
    testErrorEs6("if (true) {let foo = ()=>{}} foo();", VarCheck.UNDEFINED_VAR_ERROR);
    testErrorEs6("if (true) {const foo = ()=>{}} foo();", VarCheck.UNDEFINED_VAR_ERROR);

    testSameEs6("foo(); if (true) {var foo = ()=>{}}");
    testErrorEs6("foo(); if (true) {let foo = ()=>{}}", VarCheck.UNDEFINED_VAR_ERROR);
    testErrorEs6("foo(); if (true) {const foo = ()=>{}}", VarCheck.UNDEFINED_VAR_ERROR);
  }

  public void testValidFunctionExpr() {
    testSame("(function() {});");
  }

  public void testRecursiveFunction() {
    testSame("(function a() { return a(); })();");
  }

  public void testRecursiveFunction2() {
    testSame("var a = 3; (function a() { return a(); })();");
  }

  public void testParam() {
    testSame("function fn(a){ var b = a; }");
    testSame("function fn(a){ var a = 2; }");
    testError("function fn(){ var b = a; }", VarCheck.UNDEFINED_VAR_ERROR);

    // Default parameters
    testErrorEs6(
        "function fn(a = b) { function g(a = 3) { var b; } }", VarCheck.UNDEFINED_VAR_ERROR);
    testErrorEs6("function f(x=a) { let a; }", VarCheck.UNDEFINED_VAR_ERROR);
    testErrorEs6("function f(x=a) { { let a; } }", VarCheck.UNDEFINED_VAR_ERROR);
    testErrorEs6("function f(x=b) { function a(x=1) { var b; } }", VarCheck.UNDEFINED_VAR_ERROR);
    testErrorEs6("function f(x=a) { var a; }", VarCheck.UNDEFINED_VAR_ERROR);
    testErrorEs6("function f(x=a()) { function a() {} }", VarCheck.UNDEFINED_VAR_ERROR);
    testErrorEs6("function f(x=[a]) { var a; }", VarCheck.UNDEFINED_VAR_ERROR);
    testErrorEs6("function f(x = new foo.bar()) {}", VarCheck.UNDEFINED_VAR_ERROR);
    testSameEs6("var foo = {}; foo.bar = class {}; function f(x = new foo.bar()) {}");

    testSameEs6("function fn(a = 2){ var b = a; }");
    testSameEs6("function fn(a = 2){ var a = 3; }");
    testSameEs6("function fn({a, b}){ var c = a; }");
    testSameEs6("function fn({a, b}){ var a = 3; }");
  }

  public void testLegalVarReferenceBetweenModules() {
    testDependentModules("var x = 10;", "var y = x++;", null);
  }

  public void testLegalLetReferenceBetweenModules() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testDependentModules("let x = 10;", "let y = x++;", null);
  }

  public void testLegalConstReferenceBetweenModules() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testDependentModules("const x = 10;", "const y = x + 1;", null);
  }

  public void testMissingModuleDependencyDefault() {
    testIndependentModules("var x = 10;", "var y = x++;",
                           null, VarCheck.MISSING_MODULE_DEP_ERROR);
  }

  public void testMissingModuleDependencyLetAndConst() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testIndependentModules("let x = 10;", "let y = x++;",
        null, VarCheck.MISSING_MODULE_DEP_ERROR);
    testIndependentModules("const x = 10;", "const y = x + 1;",
        null, VarCheck.MISSING_MODULE_DEP_ERROR);
  }

  public void testViolatedModuleDependencyDefault() {
    testDependentModules("var y = x++;", "var x = 10;",
                         VarCheck.VIOLATED_MODULE_DEP_ERROR);
  }

  public void testViolatedModuleDependencyLetAndConst() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testDependentModules("let y = x++;", "let x = 10;",
        VarCheck.VIOLATED_MODULE_DEP_ERROR);
    testDependentModules("const y = x + 1;", "const x = 10;",
        VarCheck.VIOLATED_MODULE_DEP_ERROR);
  }


  public void testMissingModuleDependencySkipNonStrict() {
    sanityCheck = true;
    testIndependentModules("var x = 10;", "var y = x++;",
                           null, null);
  }

  public void testViolatedModuleDependencySkipNonStrict() {
    sanityCheck = true;
    testDependentModules("var y = x++;", "var x = 10;",
                         null);
  }

  public void testMissingModuleDependencySkipNonStrictNotPromoted() {
    sanityCheck = true;
    strictModuleDepErrorLevel = CheckLevel.ERROR;
    testIndependentModules("var x = 10;", "var y = x++;", null, null);
  }

  public void testViolatedModuleDependencyNonStrictNotPromoted() {
    sanityCheck = true;
    strictModuleDepErrorLevel = CheckLevel.ERROR;
    testDependentModules("var y = x++;", "var x = 10;", null);
  }

  public void testDependentStrictModuleDependencyCheck() {
    strictModuleDepErrorLevel = CheckLevel.ERROR;
    testDependentModules("var f = function() {return new B();};",
        "var B = function() {}",
        VarCheck.STRICT_MODULE_DEP_ERROR);
  }

  public void testIndependentStrictModuleDependencyCheck() {
    strictModuleDepErrorLevel = CheckLevel.ERROR;
    testIndependentModules("var f = function() {return new B();};",
        "var B = function() {}",
        VarCheck.STRICT_MODULE_DEP_ERROR, null);
  }

  public void testStarStrictModuleDependencyCheck() {
    strictModuleDepErrorLevel = CheckLevel.WARNING;
    testSame(createModuleStar("function a() {}", "function b() { a(); c(); }",
        "function c() { a(); }"),
        VarCheck.STRICT_MODULE_DEP_ERROR);
  }

  public void testForwardVarReferenceInLocalScope1() {
    testDependentModules("var x = 10; function a() {y++;}",
                         "var y = 11; a();", null);
  }

  public void testForwardVarReferenceInLocalScope2() {
    // It would be nice if this pass could use a call graph to flag this case
    // as an error, but it currently doesn't.
    testDependentModules("var x = 10; function a() {y++;} a();",
                         "var y = 11;", null);
  }

  private void testDependentModules(String code1, String code2,
                                    DiagnosticType error) {
    testDependentModules(code1, code2, error, null);
  }

  private void testDependentModules(String code1, String code2,
                                    DiagnosticType error,
                                    DiagnosticType warning) {
    testTwoModules(code1, code2, true, error, warning);
  }

  private void testIndependentModules(String code1, String code2,
                                      DiagnosticType error,
                                      DiagnosticType warning) {
    testTwoModules(code1, code2, false, error, warning);
  }

  private void testTwoModules(String code1, String code2, boolean m2DependsOnm1,
                              DiagnosticType error, DiagnosticType warning) {
    JSModule m1 = new JSModule("m1");
    m1.add(SourceFile.fromCode("input1", code1));
    JSModule m2 = new JSModule("m2");
    m2.add(SourceFile.fromCode("input2", code2));
    if (m2DependsOnm1) {
      m2.addDependency(m1);
    }
    if (error == null) {
      test(new JSModule[] { m1, m2 },
           new String[] { code1, code2 }, null, warning);
    } else {
      test(new JSModule[] { m1, m2 },
           null, error, warning);
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Test synthesis of externs

  public void testSimple() {
    checkSynthesizedExtern("x", "var x;");
    checkSynthesizedExtern("var x", "");
  }

  public void testSimpleSanityCheck() {
    sanityCheck = true;
    try {
      checkSynthesizedExtern("x", "");
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).contains("Unexpected variable x");
    }
  }

  public void testParameter() {
    checkSynthesizedExtern("function f(x){}", "");
  }

  public void testLocalVar() {
    checkSynthesizedExtern("function f(){x}", "var x");
  }

  public void testTwoLocalVars() {
    checkSynthesizedExtern("function f(){x}function g() {x}", "var x");
  }

  public void testInnerFunctionLocalVar() {
    checkSynthesizedExtern("function f(){function g() {x}}", "var x");
  }

  public void testNoCreateVarsForLabels() {
    checkSynthesizedExtern("x:var y", "");
  }

  public void testVariableInNormalCodeUsedInExterns1() {
    checkSynthesizedExtern(
        "x.foo;", "var x;", "var x; x.foo;");
  }

  public void testVariableInNormalCodeUsedInExterns2() {
    checkSynthesizedExtern(
        "x;", "var x;", "var x; x;");
  }

  public void testVariableInNormalCodeUsedInExterns3() {
    checkSynthesizedExtern(
        "x.foo;", "function x() {}", "var x; x.foo; ");
  }

  public void testVariableInNormalCodeUsedInExterns4() {
    checkSynthesizedExtern(
        "x;", "function x() {}", "var x; x; ");
  }

  public void testRedeclaration1() {
     String js = "var a; var a;";
     testError(js, VarCheck.VAR_MULTIPLY_DECLARED_ERROR);
  }

  public void testRedeclaration2() {
    String js = "var a; /** @suppress {duplicate} */ var a;";
    testSame(js);
  }

  public void testRedeclaration3() {
    String js = " /** @suppress {duplicate} */ var a; var a; ";
    testSame(js);
  }

  public void testSuppressionWithInlineJsDoc() {
    testSame("/** @suppress {duplicate} */ var /** number */ a; var a;");
  }

  public void testDuplicateVar() {
    testError("/** @define {boolean} */ var DEF = false; var DEF = true;",
        VAR_MULTIPLY_DECLARED_ERROR);
  }

  public void testDontAllowSuppressDupeOnLet() {
    testErrorEs6(
        "let a; /** @suppress {duplicate} */ let a; ",
        VarCheck.LET_CONST_CLASS_MULTIPLY_DECLARED_ERROR);

    testErrorEs6(
        "function f() { let a; /** @suppress {duplicate} */ let a; }",
        VarCheck.LET_CONST_CLASS_MULTIPLY_DECLARED_ERROR);
  }

  public void testDuplicateBlockScopedDeclarationInSwitch() {
    testErrorEs6(
        LINE_JOINER.join(
            "function f(x) {",
            "  switch (x) {",
            "    case 'a':",
            "      let z = 123;",
            "      break;",
            "    case 'b':",
            "      let z = 234;",
            "      break;",
            "  }",
            "}"),
        VarCheck.LET_CONST_CLASS_MULTIPLY_DECLARED_ERROR);

    testErrorEs6(
        LINE_JOINER.join(
            "function f(x) {",
            "  switch (x) {",
            "    case 'a':",
            "      class C {}",
            "      break;",
            "    case 'b':",
            "      class C {}",
            "      break;",
            "  }",
            "}"),
        VarCheck.LET_CONST_CLASS_MULTIPLY_DECLARED_ERROR);
  }

  public void testFunctionScopeArguments() {
    // A var declaration doesn't mask arguments
    testSame("function f() {var arguments}");

    testError("var f = function arguments() {}", VarCheck.VAR_ARGUMENTS_SHADOWED_ERROR);
    testError("var f = function (arguments) {}", VarCheck.VAR_ARGUMENTS_SHADOWED_ERROR);
    testError("function f() {try {} catch(arguments) {}}", VarCheck.VAR_ARGUMENTS_SHADOWED_ERROR);

    sanityCheck = true;
    testSame("function f() {var arguments}");
  }

  public void testNoUndeclaredVarWhenUsingClosurePass() {
    enableClosurePass();
    // We don't want to get goog as an undeclared var here.
    testError("goog.require('namespace.Class1');\n",
        ProcessClosurePrimitives.MISSING_PROVIDE_ERROR);
  }

  private static final class VariableTestCheck implements CompilerPass {

    final AbstractCompiler compiler;
    VariableTestCheck(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      NodeTraversal.traverseRootsEs6(compiler,
          new AbstractPostOrderCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              if (n.isName() && !parent.isFunction()
                  && !parent.isLabel()) {
                assertTrue("Variable " + n.getString() + " should have be declared",
                    t.getScope().isDeclared(n.getString(), true));
              }
            }
          },
          externs, root);
    }
  }

  public void checkSynthesizedExtern(
      String input, String expectedExtern) {
    checkSynthesizedExtern("", input, expectedExtern);
  }

  public void checkSynthesizedExtern(
      String extern, String input, String expectedExtern) {
    declarationCheck = !sanityCheck;
    this.enableCompareAsTree(false);
    testExternChanges(extern, input, expectedExtern);
  }
}
