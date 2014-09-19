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

import static com.google.javascript.jscomp.VarCheck.VAR_MULTIPLY_DECLARED_ERROR;

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

public class VarCheckTest extends CompilerTestCase {
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
    super.enableAstValidation(true);
    strictModuleDepErrorLevel = CheckLevel.OFF;
    externValidationErrorLevel = null;
    sanityCheck = false;
    declarationCheck = false;
    compareJsDoc = false;
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

  public void testBreak() {
    testSame("a: while(1) break a;");
  }

  public void testContinue() {
    testSame("a: while(1) continue a;");
  }

  public void testReferencedVarNotDefined() {
    test("x = 0;", null, VarCheck.UNDEFINED_VAR_ERROR);
  }

  public void testReferencedLetNotDefined() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    test("{ let x = 1; } var y = x;", null, VarCheck.UNDEFINED_VAR_ERROR);
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
    test("var x = 1; var x = 2;", null,
        VarCheck.VAR_MULTIPLY_DECLARED_ERROR);
  }

  public void testMultiplyDeclaredVars2() {
    test("var y; try { y=1 } catch (x) {}" +
         "try { y=1 } catch (x) {}",
         "var y;try{y=1}catch(x){}try{y=1}catch(x){}");
  }

  public void testMultiplyDeclaredVars3() {
    test("try { var x = 1; x *=2; } catch (x) {}", null,
         VarCheck.VAR_MULTIPLY_DECLARED_ERROR);
  }

  public void testMultiplyDeclaredVars4() {
    testSame("x;", "var x = 1; var x = 2;",
        VarCheck.VAR_MULTIPLY_DECLARED_ERROR, true);
  }

  public void testVarReferenceInExterns() {
    testSame("asdf;", "var asdf;",
        VarCheck.NAME_REFERENCE_IN_EXTERNS_ERROR);
  }

  public void testCallInExterns() {
    testSame("yz();", "function yz() {}",
        VarCheck.NAME_REFERENCE_IN_EXTERNS_ERROR);
  }

  public void testPropReferenceInExterns1() {
    testSame("asdf.foo;", "var asdf;",
        VarCheck.UNDEFINED_EXTERN_VAR_ERROR);
  }

  public void testPropReferenceInExterns2() {
    testSame("asdf.foo;", "",
        VarCheck.UNDEFINED_VAR_ERROR, true);
  }

  public void testPropReferenceInExterns3() {
    testSame("asdf.foo;", "var asdf;",
        VarCheck.UNDEFINED_EXTERN_VAR_ERROR);

    externValidationErrorLevel = CheckLevel.ERROR;
    test(
        "asdf.foo;", "var asdf;", "",
         VarCheck.UNDEFINED_EXTERN_VAR_ERROR, null);

    externValidationErrorLevel = CheckLevel.OFF;
    test("asdf.foo;", "var asdf;", "var asdf;", null, null);
  }

  public void testVarInWithBlock() {
    test("var a = {b:5}; with (a){b;}", null, VarCheck.UNDEFINED_VAR_ERROR);
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

  public void testLegalVarReferenceBetweenModules() {
    testDependentModules("var x = 10;", "var y = x++;", null);
  }

  public void testMissingModuleDependencyDefault() {
    testIndependentModules("var x = 10;", "var y = x++;",
                           null, VarCheck.MISSING_MODULE_DEP_ERROR);
  }

  public void testViolatedModuleDependencyDefault() {
    testDependentModules("var y = x++;", "var x = 10;",
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
    test(new JSModule[] { m1, m2 },
         new String[] { code1, code2 }, error, warning);
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
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().contains("Unexpected variable x"));
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
     test(js, null, VarCheck.VAR_MULTIPLY_DECLARED_ERROR);
  }

  public void testRedeclaration2() {
    String js = "var a; /** @suppress {duplicate} */ var a;";
    testSame(js);
  }

  public void testRedeclaration3() {
    String js = " /** @suppress {duplicate} */ var a; var a; ";
    testSame(js);
  }

  public void testDuplicateVar() {
    test("/** @define {boolean} */ var DEF = false; var DEF = true;",
         null, VAR_MULTIPLY_DECLARED_ERROR);
  }

  public void testFunctionScopeArguments() {
    // A var declaration doesn't mask arguments
    testSame("function f() {var arguments}");

    test("var f = function arguments() {}",
        null, VarCheck.VAR_ARGUMENTS_SHADOWED_ERROR);
    test("var f = function (arguments) {}",
        null, VarCheck.VAR_ARGUMENTS_SHADOWED_ERROR);
    test("function f() {try {} catch(arguments) {}}",
        null, VarCheck.VAR_ARGUMENTS_SHADOWED_ERROR);
  }

  public void testNoUndeclaredVarWhenUsingClosurePass() {
    enableClosurePass();
    // We don't want to get goog as an undeclared var here.
    test("goog.require('namespace.Class1');\n", null,
        ProcessClosurePrimitives.MISSING_PROVIDE_ERROR);
  }

  private static final class VariableTestCheck implements CompilerPass {

    final AbstractCompiler compiler;
    VariableTestCheck(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      NodeTraversal.traverseRoots(compiler, Lists.newArrayList(externs, root),
          new AbstractPostOrderCallback() {
        @Override
        public void visit(NodeTraversal t, Node n, Node parent) {
          if (n.isName() && !parent.isFunction()
              && !parent.isLabel()) {
            assertTrue("Variable " + n.getString() + " should have be declared",
                t.getScope().isDeclared(n.getString(), true));
          }
        }
      });
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
