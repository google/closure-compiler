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

import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ManageClosureUnawareCode}, specifically the wrapping part. */
@RunWith(JUnit4.class)
public final class ManageClosureUnawareCodeTest extends CompilerTestCase {

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

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    runWrapPass = true;
    runUnwrapPass = true;
    gatheredShadowNodeRoot = IR.root();
  }

  private boolean runWrapPass = true;
  private boolean runUnwrapPass = true;
  private boolean gatherShadowNodesBeforeUnwrapping = true;
  private Node gatheredShadowNodeRoot = IR.root();
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
    if (runWrapPass) {
      passes.add(
          PassFactory.builder()
              .setName("wrapClosureUnawareCode")
              .setInternalFactory(ManageClosureUnawareCode::wrap)
              .build());
    }
    if (gatherShadowNodesBeforeUnwrapping) {
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
                                  gatheredShadowNodeRoot.addChildToBack(shadow.cloneTree());
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
    }

    passes.addAll(aditionalPasses);

    if (runUnwrapPass) {
      passes.add(
          PassFactory.builder()
              .setName("unwrapClosureUnawareCode")
              .setInternalFactory(ManageClosureUnawareCode::unwrap)
              .build());
    }
    phaseopt.consume(passes);
    phaseopt.setValidityCheck(
        PassFactory.builder()
            .setName("validityCheck")
            .setRunInFixedPointLoop(true)
            .setInternalFactory(ValidityCheck::new)
            .build());
    return phaseopt;
  }

  private void doTest(String js, String expectedWrapped, List<String> expectedShadowNodeContents) {
    runWrapPass = true;
    runUnwrapPass = false; // Validate that only wrapping results in the expected wrapped contents.
    gatherShadowNodesBeforeUnwrapping = !expectedShadowNodeContents.isEmpty();
    test(js, expectedWrapped);
    if (!expectedShadowNodeContents.isEmpty()) {
      Node expectedShadowNodeRoot =
          expectedShadowNodeContents.stream()
              .map(s -> parseExpectedJs(s).detach())
              .collect(IR::root, Node::addChildToBack, Node::addChildToBack);
      assertNode(gatheredShadowNodeRoot)
          .usingSerializer(
              (n) ->
                  new CodePrinter.Builder(n)
                      .setCompilerOptions(getLastCompiler().getOptions())
                      .setPrettyPrint(true)
                      .build())
          .isEqualIncludingJsDocTo(expectedShadowNodeRoot);
    }

    // now test with unwrapping enabled so it is a no-op
    runWrapPass = true;
    runUnwrapPass = true;
    testSame(js);
  }

  @Test
  public void testClosureUnawareCodePassModifiesMainAST_incorrectly() {
    runWrapPass = true;
    runUnwrapPass = true;

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
    runWrapPass = true;
    runUnwrapPass = true;
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

  @Test
  public void testDirectLoad() {
    doTest(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        /** @closureUnaware */
        (function() {
          window['foo'] = 5;
        }).call(globalThis);
        """,
        """
        /** @fileoverview @closureUnaware */
        goog.module('foo.bar.baz_raw');
        $jscomp_wrap_closure_unaware_code.call(globalThis)
        """,
        ImmutableList.of(
            """
            /** @closureUnaware */
            (function() {
              window['foo'] = 5;
            })
            """));
  }

  @Test
  public void testDirectLoadWithRequireAndExports() {
    doTest(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        goog.require('foo.bar');
        const {a} = goog.require('foo.baz');
        /** @closureUnaware */
        (function() {
          window['foo'] = 5;
        }).call(globalThis);
        exports = globalThis['foo'];
        """,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        goog.require('foo.bar');
        const {a} = goog.require('foo.baz');
        $jscomp_wrap_closure_unaware_code.call(globalThis)
        exports = globalThis['foo'];
        """,
        ImmutableList.of(
            """
            /** @closureUnaware */
            (function() {
              window['foo'] = 5;
            })
            """));
  }

  @Test
  public void testConditionalLoad() {
    doTest(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        if (!window['foo']) {
          /** @closureUnaware */
          (function() {
            window['foo'] = 5;
          }).call(globalThis);
        }
        """,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        if (!window['foo']) {
          $jscomp_wrap_closure_unaware_code.call(globalThis)
        }
        """,
        ImmutableList.of(
            """
            /** @closureUnaware */
            (function() {
              window['foo'] = 5;
            })
            """));
  }

  @Test
  public void testDebugSrcLoad() {
    doTest(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        if (goog.DEBUG) {
          /** @closureUnaware */
          (function() {
            window['foo'] = 5;
          }).call(globalThis);
        } else {
          /** @closureUnaware */
          (function() {
             window['foo'] = 10;
          }).call(globalThis);
        }
        """,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        if (goog.DEBUG) {
          $jscomp_wrap_closure_unaware_code.call(globalThis)
        } else {
          $jscomp_wrap_closure_unaware_code.call(globalThis)
        }
        """,
        ImmutableList.of(
            """
            /** @closureUnaware */
            (function() {
              window['foo'] = 5;
            })
            """,
            """
            /** @closureUnaware */
            (function() {
              window['foo'] = 10;
            })
            """));
  }

  @Test
  public void testConditionalAndDebugSrcLoad() {
    doTest(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        if (!window['foo']) {
          if (goog.DEBUG) {
            /** @closureUnaware */
            (function() {
              window['foo'] = 5;
            }).call(globalThis);
          } else {
            /** @closureUnaware */
            (function() {
               window['foo'] = 10;
            }).call(globalThis);
          }
        }
        """,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        if (!window['foo']) {
          if (goog.DEBUG) {
            $jscomp_wrap_closure_unaware_code.call(globalThis)
          } else {
            $jscomp_wrap_closure_unaware_code.call(globalThis)
          }
        }
        """,
        ImmutableList.of(
            """
            /** @closureUnaware */
            (function() {
              window['foo'] = 5;
            })
            """,
            """
            /** @closureUnaware */
            (function() {
              window['foo'] = 10;
            })
            """));
  }

  @Test
  public void testDirectLoad_nestedChangeScopes() {
    doTest(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        if (goog.DEBUG) {
          /** @closureUnaware */
          (function() {
            function bar() {
              window['foo'] = 5;
            }
            bar();
          }).call(globalThis);
        }
        """,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        if (goog.DEBUG) {
          $jscomp_wrap_closure_unaware_code.call(globalThis)
        }
        """,
        ImmutableList.of(
            """
            /** @closureUnaware */
            (function() {
              function bar() { window['foo'] = 5;} bar();
            })
            """));
  }

  @Test
  public void testDirectLoad_nestedGlobalThisIIFEIsNotRewritten() {
    doTest(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        if (goog.DEBUG) {
          /** @closureUnaware */
          (function() {
            (function() {
              window['foo'] = 10;
            }).call(globalThis);
          }).call(globalThis);
        }
        """,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        if (goog.DEBUG) {
          $jscomp_wrap_closure_unaware_code.call(globalThis)
        }
        """,
        ImmutableList.of(
            """
            /** @closureUnaware */
            (function() {
             (function() {
                window['foo'] = 10;
              }).call(globalThis);
            })
            """));
  }

  @Test
  public void testUnwrap_doesNotEmitParseErrorsThatShouldBeSuppressed() {
    runWrapPass = false;
    runUnwrapPass = true;
    languageInOverride = Optional.of(CompilerOptions.LanguageMode.ECMASCRIPT_2015);

    testNoWarning(
        """
        /** @fileoverview @closureUnaware */
        goog.module('foo.bar.baz_raw');
        $jscomp_wrap_closure_unaware_code('{class C { foo = "bar"; } }');
        """);
  }

  @Test
  public void testErrorsOnWrapping_invalidAnnotation_statement() {
    testError(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        /** @closureUnaware */
        exports.bar = 10;
        """,
        ManageClosureUnawareCode.UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_CODE);
  }

  @Test
  public void testErrorsForScriptsWithoutFileoverviewClosureUnawareAnnotation() {
    testError(
        """
        /**
         * @fileoverview
         */
        goog.module('foo.bar.baz_raw');
        /** @closureUnaware */
        (function() {
          window['foo'] = 10;
        }).call(globalThis);
        """,
        ManageClosureUnawareCode.UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_CODE);
  }

  @Test
  public void testErrorsIfMissingNodeLevelAnnotation() {
    // It is an error to have a file-level @closureUnaware annotation, but no annotation on some
    // other node to indicate the start of the closure-unaware sub-AST.
    testError(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        $jscomp_wrap_closure_unaware_code(5)
        """,
        ManageClosureUnawareCode.UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_CODE);
  }

  @Test
  public void testNoErrorsForConfusingNestedAnnotations() {
    doTest(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        (/** @closureUnaware */ function() {
        // This nested usage of the @closureUnaware annotation is not a parse error, but it
        // could be confusing if a human author expected there to be multiple shadowed ASTs /
        // sections of closure-aware and closure-unaware code.
        // This test-case is just codifying that it is always the top-most AST node that is
        // converted into a shadow AST node, and that there won't be nested shadow ASTs.
          (/** @closureUnaware */ function() {
            window['foo'] = 10;
          }).call(globalThis);
        }).call(globalThis);
        """,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        $jscomp_wrap_closure_unaware_code.call(globalThis)
        """,
        ImmutableList.of(
            """
            /** @closureUnaware */
            (function() {
            // Note that there isn't a JSDoc closureUnaware annotation on the inner function, as
            // it was never created during parsing of the AST.
              (function() {
                window['foo'] = 10;
              }).call(globalThis);
            })
            """));
  }

  @Test
  public void testAllowsSpecifyingAnnotationOnIIFE() {
    doTest(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        /** @closureUnaware */
        (function() {
          window['foo'] = 5;
        }).call(globalThis);
        """,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        $jscomp_wrap_closure_unaware_code.call(globalThis)
        """,
        ImmutableList.of(
            """
            /** @closureUnaware */
            (function() {
              window['foo'] = 5;
            })
            """));
  }

  @Test
  public void testReparseWithInvalidJSDocTags() {
    // @dependency is not a valid JSDoc tag, and for normal code we would issue a parser error.
    // However, for closure-unaware code we don't care at all what is in jsdoc comments.
    doTest(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        /** @closureUnaware */
        (function() {
          /** @dependency */
          window['foo'] = 5;
        }).call(globalThis);
        """,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        $jscomp_wrap_closure_unaware_code.call(globalThis)
        """,
        ImmutableList.of(
            """
            /** @closureUnaware */
            (function() {
              window['foo'] = 5;
            })
            """));
  }
}
