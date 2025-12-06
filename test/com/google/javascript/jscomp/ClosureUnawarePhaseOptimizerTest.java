/*
 * Copyright 2024 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for interactions between {@link ManageClosureUnawareCode} and {@link PhaseOptimizer}
 *
 * <p>This is to demonstrate problems that arise from attempting to run optimization passes over
 * shadow ASTs alongside the main AST.
 */
@RunWith(JUnit4.class)
public final class ClosureUnawarePhaseOptimizerTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    allowExternsChanges();
    // This is instead done via the ValidationCheck in PhaseOptimizer
    // This set of tests can't use the build-in AST change marking validation
    // as the "before" is supposed to be identical to the "after" (with changes happening between
    // each pass). Unfortunately, CompilerTestCase is designed to only handle a single pass running,
    // and so it sees that the AST hasn't changed (expected) but changes have been reported (yep -
    // the changes were "done" and then "undone"), and fails.
    disableValidateAstChangeMarking();
  }

  @Before
  public void resetAditionalPasses() {
    aditionalPasses.clear();
  }

  private final List<Node> shadowNodes = new ArrayList<>();
  private final List<PassFactory> aditionalPasses = new ArrayList<>();
  public Optional<CompilerOptions.LanguageMode> languageInOverride = Optional.empty();
  private static final String ARBITRARY_NUMERIC_CHANGE_CLOSURE_UNAWARE_CODE =
      "arbitraryNumericChangeClosureUnawareCode";
  private static final String ARBITRARY_NUMERIC_CHANGE_MAIN_AST = "arbitraryNumericChangeMainAST";

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    if (languageInOverride.isPresent()) {
      options.setLanguageIn(languageInOverride.get());
    }
    return options;
  }

  private final class ChangedScopeNodesIterativePass implements CompilerPass {
    private final String passName;
    private final AbstractCompiler compiler;

    private ChangedScopeNodesIterativePass(String passName, AbstractCompiler compiler) {
      this.passName = passName;
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      var cb =
          new NodeTraversal.AbstractPostOrderCallback() {
            @Override
            public void visit(NodeTraversal t, Node node, Node parent) {
              if (node.isNumber()) {
                double value = node.getDouble();
                if (value <= 3.0) {
                  node.setDouble(4.0);
                  compiler.reportChangeToEnclosingScope(node);
                }
              }
            }
          };
      for (List<Node> changedScopeNodes =
              compiler.getChangeTracker().getChangedScopeNodesForPass(passName);
          changedScopeNodes == null || !changedScopeNodes.isEmpty();
          changedScopeNodes = compiler.getChangeTracker().getChangedScopeNodesForPass(passName)) {
        // changedScopeNodes is only null if this is the first run of this pass.
        if (changedScopeNodes != null) {
          NodeTraversal.traverseScopeRoots(
              compiler, changedScopeNodes, cb, /* traverseNested= */ false);
          continue;
        }
        if (passName.equals(ARBITRARY_NUMERIC_CHANGE_CLOSURE_UNAWARE_CODE)) {
          NodeTraversal.traverseScopeRoots(compiler, shadowNodes, cb, /* traverseNested= */ false);
        } else {
          NodeTraversal.traverse(compiler, root, cb);
        }
      }
    }
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    PhaseOptimizer phaseopt = new PhaseOptimizer(compiler, null);
    List<PassFactory> passes = new ArrayList<>();
    passes.add(
        PassFactory.builder()
            .setName("wrapClosureUnawareCode")
            .setInternalFactory(ManageClosureUnawareCode::wrap)
            .build());
    passes.add(
        PassFactory.builder()
            .setName("gatherShadowNodes")
            .setInternalFactory(
                (c) ->
                    new CompilerPass() {

                      @Override
                      public void process(Node externs, Node root) {
                        NodeUtil.visitPreOrder(
                            root,
                            (Node node) -> {
                              Node shadow = node.getClosureUnawareShadow();
                              if (shadow != null) {
                                /*
                                 * shadow is a ROOT node that follows the pattern:
                                 * ROOT -> SCRIPT -> EXPR_RESULT -> FUNCTION
                                 * so we need to get the 3rd child to get to the function node.
                                 */
                                shadowNodes.add(
                                    shadow.getFirstChild().getFirstChild().getFirstChild());
                              }
                            });
                      }
                    })
            .build());
    passes.addAll(aditionalPasses);

    passes.add(
        PassFactory.builder()
            .setName("unwrapClosureUnawareCode")
            .setInternalFactory(ManageClosureUnawareCode::unwrap)
            .build());
    phaseopt.consume(passes);
    phaseopt.setValidityCheck(
        PassFactory.builder()
            .setName("validityCheck")
            .setRunInFixedPointLoop(true)
            .setInternalFactory(ValidityCheck::new)
            .build());
    return phaseopt;
  }

  @Test
  public void testClosureUnawareCodePassModifiesMainAST_incorrectly() {
    aditionalPasses.add(
        PassFactory.builder()
            .setName(ARBITRARY_NUMERIC_CHANGE_CLOSURE_UNAWARE_CODE)
            .setInternalFactory(
                (c) ->
                    new ChangedScopeNodesIterativePass(
                        ARBITRARY_NUMERIC_CHANGE_CLOSURE_UNAWARE_CODE, c))
            .setRunInFixedPointLoop(true)
            .build());

    aditionalPasses.add(
        PassFactory.builder()
            .setName(ARBITRARY_NUMERIC_CHANGE_MAIN_AST)
            .setInternalFactory(
                (c) ->
                    new CompilerPass() {

                      @Override
                      public void process(Node externs, Node root) {
                        NodeUtil.visitPreOrder(
                            root,
                            (Node node) -> {
                              if (node.isNumber()) {
                                double value = node.getDouble();
                                if (value == 1.0 || value == 2.0) {
                                  node.setDouble(value + 1);
                                  c.reportChangeToEnclosingScope(node);
                                }
                              }
                            });
                      }
                    })
            .setRunInFixedPointLoop(true)
            .build());

    /*
     * This is an incorrect behavior: 1. arbitraryNumericChangeMainAST turns 1.0 to 2.0 and 2.0 to
     * 3.0 only in the main AST. 2. arbitraryNumericChangeClosureUnawareCode turns any number <= 3.0
     * to 4.0 only in closure-unaware shadows. Variable Y is in the main AST, so it should not be
     * modified by the pass that changes only closure-unaware code. However, since we are also
     * running arbitraryNumericChangeMainAST, it modifies variable Y and this change is reported to
     * the changeTimeline, which is used by the close-unaware code pass to determine which nodes to
     * modify, so, variable Y ends up being incorrectly modified by
     * arbitraryNumericChangeClosureUnawareCode.
     */
    test(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        /** @closureUnaware */
        (function() {
          var x = 1;
        }).call(globalThis);
        var y = 1;
        """,
        // After the passes run, var x should be 4, and var y should be 3.
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        /** @closureUnaware */
        (function() {
          var x = 4;
        }).call(globalThis);
        var y = 4; // This should be 3.
        """);
  }

  @Test
  public void testMainASTPassModifiesClosureUnawareCode_incorrectly() {
    aditionalPasses.add(
        PassFactory.builder()
            .setName(ARBITRARY_NUMERIC_CHANGE_MAIN_AST)
            .setInternalFactory(
                (c) -> new ChangedScopeNodesIterativePass(ARBITRARY_NUMERIC_CHANGE_MAIN_AST, c))
            .setRunInFixedPointLoop(true)
            .build());

    aditionalPasses.add(
        PassFactory.builder()
            .setName(ARBITRARY_NUMERIC_CHANGE_CLOSURE_UNAWARE_CODE)
            .setInternalFactory(
                (c) ->
                    new CompilerPass() {

                      @Override
                      public void process(Node externs, Node root) {
                        for (Node shadowRoot : shadowNodes) {
                          NodeUtil.visitPreOrder(
                              shadowRoot,
                              (Node node) -> {
                                if (node.isNumber()) {
                                  double value = node.getDouble();
                                  if (value == 1.0 || value == 2.0) {
                                    node.setDouble(value + 1);
                                    c.reportChangeToEnclosingScope(node);
                                  }
                                }
                              });
                        }
                      }
                    })
            .setRunInFixedPointLoop(true)
            .build());

    /*
     * This is an incorrect behavior. 1.arbitraryNumericChangeClosureUnawareCode turns 1.0 to 2.0
     * and 2.0 to 3.0 only in closure-unaware shadows. 2. arbitraryNumericChangeMainAST turns any
     * number <= 3.0 to 4.0 only in the main AST. Variable X is in a closure-unaware shadow, so it
     * should not be modified by the pass that changes only the main AST. However, since we are also
     * running arbitraryNumericChangeClosureUnawareCode, it modifies variable X and this change is
     * reported to the changeTimeline, which is used by the main AST code pass to determine which
     * nodes to modify, so, variable X ends up being incorrectly modified by
     * arbitraryNumericChangeMainAST.
     */
    test(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        /** @closureUnaware */
        (function() {
          var x = 1;
        }).call(globalThis);
        var y = 1;
        """,
        // After the passes run, var x should be 3, and var y should be 4.
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        /** @closureUnaware */
        (function() {
          var x = 4; // This should be 3.
        }).call(globalThis);
        var y = 4;
        """);
  }
}
