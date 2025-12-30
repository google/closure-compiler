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
    // Wrapping and unwrapping closureUnaware code does not need to record AST changes, as it is
    // conceptually really part of parsing/pre-code-printing. (We just use the CompilerPass API &
    // CompilerTestCase class for convenience.)
    disableValidateAstChangeMarking();
    disableCompareJsDoc();
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    runUnwrapPass = true;
    gatheredShadowNodeRoot = IR.root();
  }

  private boolean runUnwrapPass = true;
  private Node gatheredShadowNodeRoot = IR.root();
  public Optional<CompilerOptions.LanguageMode> languageInOverride = Optional.empty();

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    if (languageInOverride.isPresent()) {
      options.setLanguageIn(languageInOverride.get());
    }
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    PhaseOptimizer phaseopt = new PhaseOptimizer(compiler, null);
    List<PassFactory> passes = new ArrayList<>();
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
                                if (!runUnwrapPass) {
                                  node.setClosureUnawareShadow(null);
                                }
                                gatheredShadowNodeRoot.addChildToBack(shadow.cloneTree());
                              }
                            });
                      }
                    })
            .build());

    if (runUnwrapPass) {
      passes.add(
          PassFactory.builder()
              .setName("unwrapClosureUnawareCode")
              .setInternalFactory(ManageClosureUnawareCode::unwrap)
              .build());
    }
    phaseopt.consume(passes);
    return phaseopt;
  }

  private void doTest(String js, String expectedWrapped, List<String> expectedShadowNodeContents) {
    runUnwrapPass = false; // Validate that only wrapping results in the expected wrapped contents.
    test(js, expectedWrapped);
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

    // now test with unwrapping enabled so it is a no-op
    runUnwrapPass = true;
    String jsWithoutClosureUnware = js.replace("@closureUnaware", "");
    test(js, jsWithoutClosureUnware);
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
            $jscomp_sink_closure_unaware_impl(
            function() {
              window['foo'] = 5;
            });
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
            $jscomp_sink_closure_unaware_impl(
              function() {
                window['foo'] = 5;
              });
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
            $jscomp_sink_closure_unaware_impl(
              function() {
                window['foo'] = 5;
              });
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
            $jscomp_sink_closure_unaware_impl(
              function() {
                window['foo'] = 5;
              });
            """,
            """
            $jscomp_sink_closure_unaware_impl(
              function() {
                window['foo'] = 10;
              });
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
            $jscomp_sink_closure_unaware_impl(
              function() {
                window['foo'] = 5;
              });
            """,
            """
            $jscomp_sink_closure_unaware_impl(
              function() {
                window['foo'] = 10;
              });
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
            $jscomp_sink_closure_unaware_impl(
              function() {
                function bar() { window['foo'] = 5;} bar();
              });
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
            $jscomp_sink_closure_unaware_impl(
              function() {
               (function() {
                  window['foo'] = 10;
                }).call(globalThis);
              });
            """));
  }

  @Test
  public void testErrorsOnWrapping_invalidAnnotation_statement() {
    test(
        srcs(
            """
            /**
             * @fileoverview
             * @closureUnaware
             */
            goog.module('foo.bar.baz_raw');
            /** @closureUnaware */
            exports.bar = 10;
            """),
        error(RhinoErrorReporter.PARSE_ERROR)
            .withMessageContaining("@closureUnaware root must be function"));
  }

  @Test
  public void testErrorsForScriptsWithoutFileoverviewClosureUnawareAnnotation() {
    test(
        srcs(
            """
            /**
             * @fileoverview
             */
            goog.module('foo.bar.baz_raw');
            /** @closureUnaware */
            (function() {
              window['foo'] = 10;
            }).call(globalThis);
            """),
        error(RhinoErrorReporter.PARSE_ERROR)
            .withMessageContaining("must be in a file annotated with @closureUnaware"));
  }

  @Test
  public void testNoErrorIfMissingNodeLevelAnnotation() {
    // It is an error to have a file-level @closureUnaware annotation, but no annotation on some
    // other node to indicate the start of the closure-unaware sub-AST.
    testSame(
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('foo.bar.baz_raw');
        $jscomp_wrap_closure_unaware_code(5)
        """);
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
            $jscomp_sink_closure_unaware_impl(
              function() {
                // Note that there isn't a JSDoc closureUnaware annotation on the inner function, as
                // it was never created during parsing of the AST.
                (function() {
                  window['foo'] = 10;
                }).call(globalThis);
              });
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
            $jscomp_sink_closure_unaware_impl(
              function() {
                window['foo'] = 5;
              });
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
            $jscomp_sink_closure_unaware_impl(
              function() {
                window['foo'] = 5;
              });
            """));
  }
}
