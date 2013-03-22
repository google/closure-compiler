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
 */

/**
 * This file contains the only tests that use the infrastructure in
 * CompilerTestCase to run multiple passes. The other files that use
 * CompilerTestCase test a single pass.
 */
public class MultiPassTest extends CompilerTestCase {
  private List<PassFactory> passes;

  public MultiPassTest() {}

  protected CompilerPass getProcessor(Compiler compiler) {
    PhaseOptimizer po = new PhaseOptimizer(compiler, null, null);
    po.consume(passes);
    po.setSanityCheck(new PassFactory("sanityCheck", false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new SanityCheck(compiler);
        }
      });
    compiler.setPhaseOptimizer(po);
    return po;
  }

  public void testInlineVarsAndPeephole() {
    passes = Lists.newLinkedList();
    addInlineVariables();
    addPeephole();
    test("function f() { var x = 1; return x + 5; }",
        "function f() { return 6; }");
  }

  public void testInlineVarsAndDeadCodeElim() {
    passes = Lists.newLinkedList();
    addDeadCodeElimination();
    addInlineVariables();
    test("function f() { var x = 1; return x; x = 3; }",
        "function f() { return 1; }");
  }

  public void testTopScopeChange() {
    passes = Lists.newLinkedList();
    addInlineVariables();
    addPeephole();
    test("var x = 1, y = x, z = x + y;", "var z = 2;");
  }

  private void addDeadCodeElimination() {
    passes.add(new PassFactory("removeUnreachableCode", false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new UnreachableCodeElimination(compiler, true);
        }
      });
  }

  private void addInlineVariables() {
    passes.add(new PassFactory("inlineVariables", false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new InlineVariables(compiler, InlineVariables.Mode.ALL, true);
        }
      });
  }

  private void addPeephole() {
    passes.add(new PassFactory("peepholeOptimizations", false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          final boolean late = false;
          return new PeepholeOptimizationsPass(compiler,
              new PeepholeSubstituteAlternateSyntax(late),
              new PeepholeReplaceKnownMethods(late),
              new PeepholeRemoveDeadCode(),
              new PeepholeFoldConstants(late),
              new PeepholeCollectPropertyAssignments());
        }
      });
  }
}
