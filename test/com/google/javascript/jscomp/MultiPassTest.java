/*
 * Copyright 2013 The Closure Compiler Authors.
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

import java.util.List;

/**
 * This file contains the only tests that use the infrastructure in
 * CompilerTestCase to run multiple passes and do sanity checks. The other files
 * that use CompilerTestCase unit test a single pass.
 *
 */
public class MultiPassTest extends CompilerTestCase {
  private List<PassFactory> passes;

  public MultiPassTest() {
    enableNormalize();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    PhaseOptimizer phaseopt = new PhaseOptimizer(compiler, null, null);
    phaseopt.consume(passes);
    phaseopt.setSanityCheck(
        new PassFactory("sanityCheck", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            return new SanityCheck(compiler);
          }
        });
    compiler.setPhaseOptimizer(phaseopt);
    return phaseopt;
  }

  public void testInlineVarsAndPeephole() {
    passes = Lists.newLinkedList();
    addInlineVariables();
    addPeephole();
    test("function f() { var x = 1; return x + 5; }",
        "function f() { return 6; }");
  }

  public void testInlineFunctionsAndPeephole() {
    passes = Lists.newLinkedList();
    addInlineFunctions();
    addPeephole();
    test("function f() { return 1; }" +
        "function g() { return f(); }" +
        "function h() { return g(); } var n = h();",
        "var n = 1");
  }

  public void testInlineVarsAndDeadCodeElim() {
    passes = Lists.newLinkedList();
    addDeadCodeElimination();
    addInlineVariables();
    test("function f() { var x = 1; return x; x = 3; }",
        "function f() { return 1; }");
  }

  public void testCollapseObjectLiteralsScopeChange() {
    passes = Lists.newLinkedList();
    addCollapseObjectLiterals();
    test("function f() {" +
        "  var obj = { x: 1 };" +
        "  var z = function() { return obj.x; }" +
        "}",
        "function f(){" +
        "  var JSCompiler_object_inline_x_0 = 1;" +
        "  var z = function(){" +
        "    return JSCompiler_object_inline_x_0;" +
        "  }" +
        "}");
  }

  public void testRemoveUnusedClassPropertiesScopeChange() {
    passes = Lists.newLinkedList();
    addRemoveUnusedClassProperties();
    test("/** @constructor */" +
        "function Foo() { this.a = 1; }" +
        "Foo.baz = function() {};",
        "/** @constructor */" +
        "function Foo() { 1; }" +
        "Foo.baz = function() {};");
  }

  public void testRemoveUnusedVariablesScopeChange() {
    passes = Lists.newLinkedList();
    addRemoveUnusedVars();
    test("function f() { var x; }",
        "function f() {}");
    test("function g() { function f(x, y) { return 1; } }",
        "function g() {}");
    test("function f() { var x = 123; }",
        "function f() {}");
  }

  public void testTopScopeChange() {
    passes = Lists.newLinkedList();
    addInlineVariables();
    addPeephole();
    test("var x = 1, y = x, z = x + y;", "var z = 2;");
  }

  private void addCollapseObjectLiterals() {
    passes.add(
        new PassFactory("collapseObjectLiterals", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            return new InlineObjectLiterals(
                compiler, compiler.getUniqueNameIdSupplier());
          }
        });
  }

  private void addDeadCodeElimination() {
    passes.add(
        new PassFactory("removeUnreachableCode", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            return new UnreachableCodeElimination(compiler, true);
          }
        });
  }

  private void addInlineFunctions() {
    passes.add(
        new PassFactory("inlineFunctions", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            return new InlineFunctions(
                compiler, compiler.getUniqueNameIdSupplier(),
                true, true, true, true, true);
          }
        });
  }

  private void addInlineVariables() {
    passes.add(
        new PassFactory("inlineVariables", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            return new InlineVariables(
                compiler, InlineVariables.Mode.ALL, true);
          }
        });
  }

  private void addPeephole() {
    passes.add(
        new PassFactory("peepholeOptimizations", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            final boolean late = false;
            return new PeepholeOptimizationsPass(compiler,
                new PeepholeMinimizeConditions(late),
                new PeepholeSubstituteAlternateSyntax(late),
                new PeepholeReplaceKnownMethods(late),
                new PeepholeRemoveDeadCode(),
                new PeepholeFoldConstants(late),
                new PeepholeCollectPropertyAssignments());
          }
        });
  }

  private void addRemoveUnusedClassProperties() {
    passes.add(
        new PassFactory("removeUnusedClassProperties", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            return new RemoveUnusedClassProperties(compiler);
          }
        });
  }

  private void addRemoveUnusedVars() {
    passes.add(
        new PassFactory("removeUnusedVars", false) {
          @Override
          protected CompilerPass create(AbstractCompiler compiler) {
            return new RemoveUnusedVars(compiler, false, false, false);
          }
        });
  }
}
