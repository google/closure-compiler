/*
 * Copyright 2017 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.CompilerOptions.LanguageMode.ECMASCRIPT_NEXT;
import static com.google.javascript.jscomp.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.CrossModuleReferenceCollector.Behavior;
import com.google.javascript.rhino.Token;

public final class CrossModuleReferenceCollectorTest extends CompilerTestCase {
  private Behavior behavior;

  @Override
  public void setUp() {
    setLanguage(ECMASCRIPT_NEXT, ECMASCRIPT_NEXT);
    behavior = null;
  }

  @Override
  protected int getNumRepetitions() {
    // Default behavior for CompilerTestCase.test*() methods is to do the whole test twice,
    // because passes that modify the AST need to be idempotent.
    // Since CrossModuleReferenceCollector() just gathers information, it doesn't make sense to
    // run it twice, and doing so just complicates debugging test cases.
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    ScopeCreator scopeCreator = new Es6SyntacticScopeCreator(compiler);
    return new CrossModuleReferenceCollector(
        compiler,
        this.behavior,
        scopeCreator);
  }

  private void testBehavior(String js, Behavior behavior) {
    this.behavior = behavior;
    testSame(js);
  }

  public void testVarInBlock() {
    testBehavior(
        LINE_JOINER.join(
            "  if (true) {",
            "    var y = x;",
            "    y;",
            "    y;",
            "  }"),
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isGlobal()) {
              ReferenceCollection y = rm.getReferences(t.getScope().getVar("y"));
              assertThat(y.isAssignedOnceInLifetime()).isTrue();
              assertThat(y.isWellDefined()).isTrue();
            }
          }
        });
  }

  public void testVarInLoopNotAssignedOnlyOnceInLifetime() {
    Behavior behavior =
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isGlobal()) {
              ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
              assertThat(x.isAssignedOnceInLifetime()).isFalse();
            }
          }
        };
    testBehavior("var x; while (true) { x = 0; }", behavior);
    testBehavior("let x; while (true) { x = 0; }", behavior);
  }

  /**
   * Although there is only one assignment to x in the code, it's in a function which could be
   * called multiple times, so {@code isAssignedOnceInLifetime()} returns false.
   */
  public void testVarInFunctionNotAssignedOnlyOnceInLifetime() {
    Behavior behavior =
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isGlobal()) {
              ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
              assertThat(x.isAssignedOnceInLifetime()).isFalse();
            }
          }
        };
    testBehavior("var x; function f() { x = 0; }", behavior);
    testBehavior("let x; function f() { x = 0; }", behavior);
  }

  public void testVarAssignedOnceInLifetime1() {
    Behavior behavior =
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isGlobal()) {
              ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
              assertThat(x.isAssignedOnceInLifetime()).isTrue();
            }
          }
        };
    testBehavior("var x = 0;", behavior);
    testBehavior("let x = 0;", behavior);
  }

  public void testVarAssignedOnceInLifetime2() {
    testBehavior(
        "{ var x = 0; }",
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isGlobal()) {
              ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
              assertThat(x.isAssignedOnceInLifetime()).isTrue();
            }
          }
        });
  }

  public void testBasicBlocks() {
    testBehavior(
        LINE_JOINER.join(
            "var x = 0;",
            "switch (x) {",
            "  case 0:",
            "    x;",
            "}"),
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isGlobal()) {
              ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
              assertThat(x.references).hasSize(3);
              assertNode(x.references.get(0).getBasicBlock().getRoot()).hasType(Token.ROOT);
              assertNode(x.references.get(1).getBasicBlock().getRoot()).hasType(Token.ROOT);
              assertNode(x.references.get(2).getBasicBlock().getRoot()).hasType(Token.CASE);
            }
          }
        });
  }
}
