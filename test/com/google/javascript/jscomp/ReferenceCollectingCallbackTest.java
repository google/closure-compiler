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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CompilerOptions.LanguageMode.ECMASCRIPT_NEXT;
import static com.google.javascript.jscomp.CompilerOptions.LanguageMode.UNSUPPORTED;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.truth.Correspondence;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Behavior;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ReferenceCollectingCallbackTest extends CompilerTestCase {
  private Behavior behavior;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setLanguage(ECMASCRIPT_NEXT, ECMASCRIPT_NEXT);
    behavior = null;
  }

  @Override
  protected int getNumRepetitions() {
    // Default behavior for CompilerTestCase.test*() methods is to do the whole test twice,
    // because passes that modify the AST need to be idempotent.
    // Since ReferenceCollectingCallback() just gathers information, it doesn't make sense to
    // run it twice, and doing so just complicates debugging test cases.
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    ScopeCreator scopeCreator = new SyntacticScopeCreator(compiler);
    return new ReferenceCollectingCallback(
        compiler,
        this.behavior,
        scopeCreator);
  }

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    return options;
  }

  private void testBehavior(String js, Behavior behavior) {
    this.behavior = behavior;
    testSame(js);
  }

  private static final Correspondence<Reference, Boolean> IS_DECLARATION =
      Correspondence.transforming(Reference::isDeclaration, "isDeclaration() is");

  @Test
  public void testIterableRest_declaration() {
    testBehavior(
        "let [x, ...arr] = [1, 2, 3]; [x, ...arr] = [4, 5, 6];",
        (NodeTraversal t, ReferenceMap rm) -> {
          ReferenceCollection arrReferenceCollection = rm.getReferences(t.getScope().getVar("arr"));
          assertThat(arrReferenceCollection)
              .comparingElementsUsing(IS_DECLARATION)
              .containsExactly(true, false)
              .inOrder();
        });
  }

  @Test
  public void testObjectRest_declaration() {
    testBehavior(
        "let {x, ...obj} = {x: 1, y: 2, z: 3}; ({x, ...obj} = {x: 4, q: 5, u: 6});",
        (NodeTraversal t, ReferenceMap rm) -> {
          ReferenceCollection objReferenceCollection = rm.getReferences(t.getScope().getVar("obj"));
          assertThat(objReferenceCollection)
              .comparingElementsUsing(IS_DECLARATION)
              .containsExactly(true, false)
              .inOrder();
        });
  }

  @Test
  public void testImport1() {
    testBehavior(
        "import x from '/m';",
        (NodeTraversal t, ReferenceMap rm) -> {
          if (t.getScope().isModuleScope()) {
            ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));

            assertThat(x.isAssignedOnceInLifetime()).isTrue();
            assertThat(x.isWellDefined()).isTrue();
            assertThat(x).comparingElementsUsing(IS_DECLARATION).containsExactly(true).inOrder();
          }
        });
  }

  @Test
  public void testImport2() {
    testBehavior(
        "import {x} from '/m';",
        (NodeTraversal t, ReferenceMap rm) -> {
          if (t.getScope().isModuleScope()) {
            ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));

            assertThat(x.isAssignedOnceInLifetime()).isTrue();
            assertThat(x.isWellDefined()).isTrue();
            assertThat(x).comparingElementsUsing(IS_DECLARATION).containsExactly(true).inOrder();
          }
        });
  }

  @Test
  public void testImport2_alternate() {
    testBehavior(
        "import {x as x} from '/m';",
        (NodeTraversal t, ReferenceMap rm) -> {
          if (t.getScope().isModuleScope()) {
            ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));

            assertThat(x.isAssignedOnceInLifetime()).isTrue();
            assertThat(x.isWellDefined()).isTrue();
            assertThat(x).comparingElementsUsing(IS_DECLARATION).containsExactly(true).inOrder();
          }
        });
  }

  @Test
  public void testImport3() {
    testBehavior(
        "import {y as x} from '/m';",
        (NodeTraversal t, ReferenceMap rm) -> {
          if (t.getScope().isModuleScope()) {
            assertThat(t.getScope().getVar("y")).isNull();
            ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));

            assertThat(x.isAssignedOnceInLifetime()).isTrue();
            assertThat(x.isWellDefined()).isTrue();
            assertThat(x).comparingElementsUsing(IS_DECLARATION).containsExactly(true).inOrder();
          }
        });
  }

  @Test
  public void testImport4() {
    testBehavior(
        "import * as x from '/m';",
        (NodeTraversal t, ReferenceMap rm) -> {
          if (t.getScope().isModuleScope()) {
            Var var = t.getScope().getVar("x");
            checkNotNull(var);
            ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
            checkNotNull(x);

            assertThat(x.isAssignedOnceInLifetime()).isTrue();
            assertThat(x.isWellDefined()).isTrue();
            assertThat(x).comparingElementsUsing(IS_DECLARATION).containsExactly(true).inOrder();
          }
        });
  }

  @Test
  public void testVarInBlock() {
    testBehavior(
        lines(
            "function f(x) {",
            "  if (true) {",
            "    var y = x;",
            "    y;",
            "    y;",
            "  }",
            "}"),
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isBlockScope() && t.getScope().getParent().isFunctionBlockScope()) {
              ReferenceCollection y = rm.getReferences(t.getScope().getVar("y"));
              assertThat(y.isAssignedOnceInLifetime()).isTrue();
              assertThat(y.isWellDefined()).isTrue();
            }
          }
        });
  }

  @Test
  public void testVarInLoopNotAssignedOnlyOnceInLifetime() {
    Behavior behavior =
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isBlockScope()) {
              ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
              assertThat(x.isAssignedOnceInLifetime()).isFalse();
            }
          }
        };
    testBehavior("while (true) { var x = 0; }", behavior);
    testBehavior("while (true) { let x = 0; }", behavior);
  }

  /**
   * Although there is only one assignment to x in the code, it's in a function which could be
   * called multiple times, so {@code isAssignedOnceInLifetime()} returns false.
   */
  @Test
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

  @Test
  public void testParameterAssignedOnlyOnceInLifetime() {
    testBehavior(
        "function f(x) { x; }",
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isFunctionScope()) {
              ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
              assertThat(x.isAssignedOnceInLifetime()).isTrue();
            }
          }
        });
  }

  @Test
  public void testModifiedParameterNotAssignedOnlyOnceInLifetime() {
    testBehavior(
        "function f(x) { x = 3; }",
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isFunctionScope()) {
              ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
              assertThat(x.isAssignedOnceInLifetime()).isFalse();
            }
          }
        });
  }

  @Test
  public void testVarAssignedOnceInLifetime1() {
    Behavior behavior =
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isFunctionBlockScope()) {
              ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
              assertThat(x.isAssignedOnceInLifetime()).isTrue();
            }
          }
        };
    testBehavior("function f() { var x = 0; }", behavior);
    testBehavior("function f() { let x = 0; }", behavior);
  }

  @Test
  public void testVarAssignedOnceInLifetime2() {
    testBehavior(
        "function f() { { let x = 0; } }",
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isBlockScope() && !t.getScope().isFunctionBlockScope()) {
              ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
              assertThat(x.isAssignedOnceInLifetime()).isTrue();
            }
          }
        });
  }

  @Test
  public void testVarAssignedOnceInLifetime3() {
    Behavior behavior =
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isCatchScope()) {
              ReferenceCollection e = rm.getReferences(t.getScope().getVar("e"));
              assertThat(e.isAssignedOnceInLifetime()).isTrue();
              ReferenceCollection y = rm.getReferences(t.getScope().getVar("y"));
              assertThat(y.isAssignedOnceInLifetime()).isTrue();
              assertThat(y.isWellDefined()).isTrue();
            }
          }
        };
    testBehavior(
        lines(
            "try {",
            "} catch (e) {",
            "  var y = e;",
            "  g();",
            "  y;y;",
            "}"),
        behavior);
    testBehavior(
        lines(
            "try {",
            "} catch (e) {",
            "  var y; y = e;",
            "  g();",
            "  y;y;",
            "}"),
        behavior);
  }

  @Test
  public void testLetAssignedOnceInLifetime1() {
    testBehavior(
        lines(
            "try {",
            "} catch (e) {",
            "  let y = e;",
            "  g();",
            "  y;y;",
            "}"),
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isCatchScope()) {
              ReferenceCollection e = rm.getReferences(t.getScope().getVar("e"));
              assertThat(e.isAssignedOnceInLifetime()).isTrue();
              ReferenceCollection y = rm.getReferences(t.getScope().getVar("y"));
              assertThat(y.isAssignedOnceInLifetime()).isTrue();
              assertThat(y.isWellDefined()).isTrue();
            }
          }
        });
  }

  @Test
  public void testLetAssignedOnceInLifetime2() {
    testBehavior(
        lines(
            "try {",
            "} catch (e) {",
            "  let y; y = e;",
            "  g();",
            "  y;y;",
            "}"),
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isCatchScope()) {
              ReferenceCollection e = rm.getReferences(t.getScope().getVar("e"));
              assertThat(e.isAssignedOnceInLifetime()).isTrue();
              ReferenceCollection y = rm.getReferences(t.getScope().getVar("y"));
              assertThat(y.isAssignedOnceInLifetime()).isTrue();
              assertThat(y.isWellDefined()).isTrue();
            }
          }
        });
  }

  @Test
  public void testBasicBlocks() {
    testBehavior(
        lines(
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

  @Test
  public void nullishCoalesce() {
    setLanguage(UNSUPPORTED, UNSUPPORTED);
    testBehavior(
        "var x = 0; var y = x ?? (x = 1)",
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isGlobal()) {
              ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
              assertThat(x.references).hasSize(3);
              assertNode(x.references.get(0).getBasicBlock().getRoot()).hasType(Token.ROOT);
              assertNode(x.references.get(1).getBasicBlock().getRoot()).hasType(Token.ROOT);
              // first child of ?? is not a boundary, but the second child is.
              assertNode(x.references.get(2).getBasicBlock().getRoot()).hasType(Token.ASSIGN);
            }
          }
        });
  }

  @Test
  public void optionalChaining() {
    setLanguage(UNSUPPORTED, UNSUPPORTED);
    testBehavior(
        "var x = 0; var y = x?.(x = 1)",
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isGlobal()) {
              ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
              assertThat(x.references).hasSize(3);
              assertNode(x.references.get(0).getBasicBlock().getRoot()).hasType(Token.ROOT);
              assertNode(x.references.get(1).getBasicBlock().getRoot()).hasType(Token.ROOT);
              // first child of `?.` is not a boundary, but the second child is.
              assertNode(x.references.get(2).getBasicBlock().getRoot()).hasType(Token.ASSIGN);
            }
          }
        });
  }

  @Test
  public void testThis() {
    testBehavior(
        lines(
            "/** @constructor */",
            "function C() {}",
            "",
            "C.prototype.m = function m() {",
            "  var self = this;",
            "  if (true) {",
            "    alert(self);",
            "  }",
            "};"),
        new Behavior() {
          @Override
          public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
            if (t.getScope().isFunctionBlockScope()
                && t.getScopeRoot().getParent().getFirstChild().matchesName("m")) {
              ReferenceCollection self = rm.getReferences(t.getScope().getVar("self"));
              assertThat(self.isEscaped()).isFalse();
            }
          }
        });
  }

  @Test
  public void testProcessScopeThatsNotABasicBlock() {
    // Tests the case where the scope we pass in is not really a basic block, but we create a new
    // basic block anyway because ReferenceCollectingCallback expects all nodes to be in a block.
    Compiler compiler = createCompiler();
    SyntacticScopeCreator syntacticScopeCreator = new SyntacticScopeCreator(compiler);
    ReferenceCollectingCallback referenceCollectingCallback =
        new ReferenceCollectingCallback(
            compiler,
            new Behavior() {
              @Override
              public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
                ReferenceCollection y = rm.getReferences(t.getScope().getVar("y"));
                assertThat(y.isWellDefined()).isTrue();
                BasicBlock firstBasicBlock = y.references.get(0).getBasicBlock();
                assertNode(firstBasicBlock.getRoot()).hasType(Token.BLOCK);
                assertNode(firstBasicBlock.getRoot().getParent()).hasType(Token.SCRIPT);

                // We do not create a new BasicBlock for the second { use(y); }
                BasicBlock secondBasicBlock = y.references.get(1).getBasicBlock();
                assertNode(secondBasicBlock.getRoot()).hasType(Token.BLOCK);
                assertNode(secondBasicBlock.getRoot().getParent()).hasType(Token.IF);
              }
            },
            syntacticScopeCreator);

    String js = "let x = 5; { let y = x + 1; if (true) { { use(y); } } }";
    Node root = compiler.parseTestCode(js);
    Node block = root.getSecondChild();

    Scope globalScope = syntacticScopeCreator.createScope(root, null);
    Scope blockScope = syntacticScopeCreator.createScope(block, globalScope);
    referenceCollectingCallback.processScope(blockScope);
  }
}
