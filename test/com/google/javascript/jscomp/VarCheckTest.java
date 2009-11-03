/*
 * Copyright 2005 Google Inc.
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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;


public class VarCheckTest extends CompilerTestCase {
  private static final String EXTERNS = "var window; function alert() {}";

  private CheckLevel strictModuleDepErrorLevel;
  private boolean nonStrictModuleChecks = true;

  public VarCheckTest() {
    super(EXTERNS);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    strictModuleDepErrorLevel = CheckLevel.OFF;
    nonStrictModuleChecks = true;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.STRICT_MODULE_DEP_CHECK,
        strictModuleDepErrorLevel);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new VarCheck(compiler, nonStrictModuleChecks);
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
         SyntacticScopeCreator.VAR_MULTIPLY_DECLARED_ERROR);
  }

  public void testMultiplyDeclaredVars2() {
    test("var y; try { y=1 } catch (x) {}" +
         "try { y=1 } catch (x) {}",
         "var y;try{y=1}catch(x){}try{y=1}catch(x){}");
  }

  public void testMultiplyDeclaredVars3() {
    test("try { var x = 1; x *=2; } catch (x) {}", null,
         SyntacticScopeCreator.VAR_MULTIPLY_DECLARED_ERROR);
  }

  public void testVarReferenceInExterns() {
    testSame("asdf;", "var asdf;", VarCheck.NAME_REFERENCE_IN_EXTERNS_ERROR);
  }

  public void testCallInExterns() {
    testSame("yz();", "function yz() {}",
             VarCheck.NAME_REFERENCE_IN_EXTERNS_ERROR);
  }

  public void testVarInWithBlock() {
    test("var a = {b:5}; with (a){b;}", null, VarCheck.UNDEFINED_VAR_ERROR);
  }

  public void testInvalidFunctionDecl1() {
    test("function() {};", null, VarCheck.INVALID_FUNCTION_DECL);
  }

  public void testInvalidFunctionDecl2() {
    test("if (true) { function() {}; }", null, VarCheck.INVALID_FUNCTION_DECL);
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
    nonStrictModuleChecks = false;
    testIndependentModules("var x = 10;", "var y = x++;",
                           null, null);
  }

  public void testViolatedModuleDependencySkipNonStrict() {
    nonStrictModuleChecks = false;
    testDependentModules("var y = x++;", "var x = 10;",
                         null);
  }

  public void testMissingModuleDependencySkipNonStrictPromoted() {
    nonStrictModuleChecks = false;
    strictModuleDepErrorLevel = CheckLevel.ERROR;
    testIndependentModules("var x = 10;", "var y = x++;",
        VarCheck.STRICT_MODULE_DEP_ERROR, null);
  }

  public void testViolatedModuleDependencyNonStrictPromoted() {
    nonStrictModuleChecks = false;
    strictModuleDepErrorLevel = CheckLevel.ERROR;
    testDependentModules("var y = x++;", "var x = 10;",
        VarCheck.STRICT_MODULE_DEP_ERROR);
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
    m1.add(JSSourceFile.fromCode("input1", code1));
    JSModule m2 = new JSModule("m2");
    m2.add(JSSourceFile.fromCode("input2", code2));
    if (m2DependsOnm1) {
      m2.addDependency(m1);
    }
    test(new JSModule[] { m1, m2 },
         new String[] { code1, code2 }, error, warning);
  }

  //////////////////////////////////////////////////////////////////////////////
  // Test synthesis of externs

  public void testSimple() {
    checkSynthesizedExtern("x", "var x");
    checkSynthesizedExtern("var x", "");
  }

  public void testParameter() {
    checkSynthesizedExtern("function(x){}", "");
  }

  public void testLocalVar() {
    checkSynthesizedExtern("function(){x}", "var x");
  }

  public void testInnerFunctionLocalVar() {
    checkSynthesizedExtern("function(){function() {x}}", "var x");
  }

  public void testNoCreateVarsForLabels() {
    checkSynthesizedExtern("x:var y", "");
  }

  private final static class VariableTestCheck implements CompilerPass {

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
          if (NodeUtil.isName(n) && !NodeUtil.isFunction(parent)
              && parent.getType() != Token.LABEL) {
            assertTrue("Variable " + n.getString() + " should have be declared",
                t.getScope().isDeclared(n.getString(), true));
          }
        }
      });
    }
  }

  public void checkSynthesizedExtern(String input, String expectedExtern) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setWarningLevel(
        DiagnosticGroup.forType(VarCheck.UNDEFINED_VAR_ERROR),
        CheckLevel.OFF);
    compiler.init(
        new JSSourceFile[] {},
        new JSSourceFile[] { JSSourceFile.fromCode("input", input) },
        options);
    compiler.parseInputs();
    assertFalse(compiler.hasErrors());

    Node externsAndJs = compiler.getRoot();
    Node root = externsAndJs.getLastChild();

    Node rootOriginal = root.cloneTree();
    Node externs = externsAndJs.getFirstChild();

    Node expected = compiler.parseTestCode(expectedExtern);
    assertFalse(compiler.hasErrors());

    (new VarCheck(compiler)).process(externs, root);
    (new VariableTestCheck(compiler)).process(externs, root);

    String externsCode = compiler.toSource(externs);
    String expectedCode = compiler.toSource(expected);

    assertEquals(expectedCode, externsCode);
  }
}
