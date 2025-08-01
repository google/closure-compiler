/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.AccessorSummary.PropertyAccessKind;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.testing.JSCompCorrespondences;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link PureFunctionIdentifier} */
@RunWith(JUnit4.class)
public final class PureFunctionIdentifierTest extends CompilerTestCase {
  List<Node> noSideEffectCalls;

  boolean regExpHaveSideEffects = true;
  boolean enableArtificialPurityDebugError = false;

  private static final String TEST_EXTERNS =
      CompilerTypeTestCase.DEFAULT_EXTERNS
          + """
          var window; window.setTimeout;
          /**@nosideeffects*/ function externSENone(){}
          /**@modifies{this}*/ function externSEThis(){}
          /**@constructor
           * @modifies{this}*/
          function externObjSEThis(){}
          /**
           * @param {string} s id.
           * @return {string}
           * @modifies{this}
           */
          externObjSEThis.prototype.externObjSEThisMethod = function(s) {};
          /**
           * @param {string} s id.
           * @return {string}
           * @modifies{arguments}
           */
          externObjSEThis.prototype.externObjSEThisMethod2 = function(s) {};
          /**@nosideeffects*/function Error(){}
          function externSef1(){}
          /**@nosideeffects*/function externNsef1(){}
          var externSef2 = function(){};
          /**@nosideeffects*/var externNsef2 = function(){};
          var externNsef3 = /**@nosideeffects*/function(){};
          var externObj;
          externObj.sef1 = function(){};
          /**@nosideeffects*/externObj.nsef1 = function(){};
          externObj.nsef2 = /**@nosideeffects*/function(){};
          externObj.partialFn = function(){};
          externObj.partialSharedFn;
          var externObj2;
          externObj2.partialSharedFn = /**@nosideeffects*/function(){};
          /**@constructor*/function externSefConstructor(){}
          externSefConstructor.prototype.sefFnOfSefObj = function(){};
          externSefConstructor.prototype.nsefFnOfSefObj =
            /**@nosideeffects*/function(){};
          externSefConstructor.prototype.externShared = function(){};
          /**@constructor@nosideeffects*/function externNsefConstructor(){}
          externNsefConstructor.prototype.sefFnOfNsefObj = function(){};
          externNsefConstructor.prototype.nsefFnOfNsefObj =
            /**@nosideeffects*/function(){};
          externNsefConstructor.prototype.externShared =
            /**@nosideeffects*/function(){};
          /**@constructor @nosideeffects*/function externNsefConstructor2(){}
          externNsefConstructor2.prototype.externShared =
            /**@nosideeffects*/function(){};
          externNsefConstructor.prototype.sharedPartialSef;
          /**@nosideeffects*/externNsefConstructor.prototype.sharedPartialNsef;
          // An externs definition with a stub before.
          /**@constructor*/function externObj3(){}
          externObj3.prototype.propWithStubBefore;
          /**
           * @param {string} s id.
           * @return {string}
           * @nosideeffects
           */
          externObj3.prototype.propWithStubBefore = function(s) {};
          // useless JsDoc
          /**
           * @see {foo}
           */
          externObj3.prototype.propWithStubBeforeWithJSDoc;
          /**
           * @param {string} s id.
           * @return {string}
           * @nosideeffects
           */
          externObj3.prototype.propWithStubBeforeWithJSDoc = function(s) {};
          // An externs definition with a stub after.
          /**@constructor*/function externObj4(){}
          /**
           * @param {string} s id.
           * @return {string}
           * @nosideeffects
           */
          externObj4.prototype.propWithStubAfter = function(s) {};
          externObj4.prototype.propWithStubAfter;
          /**
           * @param {string} s id.
           * @return {string}
           * @nosideeffects
           */
          externObj4.prototype.propWithStubAfterWithJSDoc = function(s) {};
          // useless JsDoc
          /**
           * @see {foo}
           */
          externObj4.prototype.propWithStubAfterWithJSDoc;
          goog.reflect = {};
          goog.reflect.cache = function(a, b, c, opt_d) {};
          /** @nosideeffects */
          externObj.prototype.duplicateExternFunc = function() {};
          externObj2.prototype.duplicateExternFunc = function() {};
          externObj.prototype['weirdDefinition'] = function() {};
          """;

  public PureFunctionIdentifierTest() {
    super(TEST_EXTERNS);
  }

  @Override
  protected int getNumRepetitions() {
    // run pass once.
    return 1;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    // Allow testing of features that are not yet fully supported.
    enableNormalize();
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    disableCompareJsDoc();
    enableGatherExternProperties();
    enableTypeCheck();
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    regExpHaveSideEffects = true;
    enableArtificialPurityDebugError = false;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    if (enableArtificialPurityDebugError) {
      options.setWarningLevel(
          DiagnosticGroup.forType(PureFunctionIdentifier.UNUSED_ARTIFICIAL_PURE_ANNOTATION),
          CheckLevel.ERROR);
    }
    return options;
  }

  /**
   * Run PureFunctionIdentifier, then gather a list of calls that are marked as having no side
   * effects.
   */
  private class NoSideEffectCallEnumerator extends AbstractPostOrderCallback
      implements CompilerPass {
    private final Compiler compiler;

    NoSideEffectCallEnumerator(Compiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      noSideEffectCalls = new ArrayList<>();
      // TODO(nickreid): Move these into 'getOptions' and 'getCompiler' overrides.
      compiler.setHasRegExpGlobalReferences(regExpHaveSideEffects);
      compiler.getOptions().setUseTypesForLocalOptimization(true);

      new PureFunctionIdentifier.Driver(compiler).process(externs, root);
      NodeTraversal.traverse(compiler, externs, this);
      NodeTraversal.traverse(compiler, root, this);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isNew()) {
        if (!compiler.getAstAnalyzer().constructorCallHasSideEffects(n)) {
          noSideEffectCalls.add(n.getFirstChild());
        }
      } else if (NodeUtil.isInvocation(n)) {
        if (!compiler.getAstAnalyzer().functionCallHasSideEffects(n)) {
          noSideEffectCalls.add(n.getFirstChild());
        }
      }
    }
  }

  @Test
  public void testOptionalChainGetProp() {
    assertNoPureCalls("externObj?.sef1()");
    assertPureCallsMarked("externObj?.nsef1()", ImmutableList.of("externObj?.nsef1"));
  }

  @Test
  public void testOptionalChainGetElem() {
    assertNoPureCalls("externObj?.['sef1']()");
    // The use of `[]` hides the exact method being called from the compiler,
    // so it will assume there are side-effects.
    assertNoPureCalls("externObj?.['nsef1']()");
  }

  @Test
  public void testOptionalChainCall() {
    assertNoPureCalls("externObj.sef1?.()");
    assertNoPureCalls("externObj?.sef1?.()");
    assertPureCallsMarked("externObj.nsef1?.()", ImmutableList.of("externObj.nsef1"));
    assertPureCallsMarked("externObj?.nsef1?.()", ImmutableList.of("externObj?.nsef1"));
  }

  @Test
  public void testOptionalGetterAccess_hasSideEffects() {
    assertNoPureCalls(
        """
        class Foo { get getter() { } }

        function foo(a) { a?.getter; }
        foo(x);
        """);
  }

  @Test
  public void testOptionalNormalPropAccess_hasNoSideEffects() {
    assertPureCallsMarked(
        """
        function foo(a) { a?.prop; }
        foo(x);
        """,
        ImmutableList.of("foo"));
  }

  @Test
  public void testOptionalElementAccess_hasNoSideEffects() {
    assertPureCallsMarked(
        """
        function foo(a) { a?.['prop']; }
        foo(x);
        """,
        ImmutableList.of("foo"));
  }

  @Test
  public void testIssue303() {
    String source =
        """
        /** @constructor */ function F() {
          var self = this;
          window.setTimeout(function() {
            window.location = self.location;
          }, 0);
        }
        F.prototype.setLocation = function(x) {
          this.location = x;
        };
        (new F()).setLocation('http://www.google.com/');
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testIssue303b() {
    String source =
        """
        /** @constructor */ function F() {
          var self = this;
          window.setTimeout(function() {
            window.location = self.location;
          }, 0);
        }
        F.prototype.setLocation = function(x) {
          this.location = x;
        };
        function x() {
          (new F()).setLocation('http://www.google.com/');
        } window['x'] = x;
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testAnnotationInExterns_new1() {
    assertPureCallsMarked("externSENone()", ImmutableList.of("externSENone"));
  }

  @Test
  public void testAnnotationInExterns_new2() {
    assertNoPureCalls("externSEThis()");
  }

  @Test
  public void testAnnotationInExterns_new3() {
    assertPureCallsMarked("new externObjSEThis()", ImmutableList.of("externObjSEThis"));
  }

  @Test
  public void testAnnotationInExterns_new4() {
    // The entire expression containing "externObjSEThisMethod" is considered
    // side-effect free in this context.

    assertPureCallsMarked(
        "new externObjSEThis().externObjSEThisMethod('')",
        ImmutableList.of("externObjSEThis", "new externObjSEThis().externObjSEThisMethod"));
  }

  @Test
  public void testAnnotationInExterns_new5() {
    assertPureCallsMarked(
        "function f() { new externObjSEThis() }; f();", ImmutableList.of("externObjSEThis", "f"));
  }

  @Test
  public void testAnnotationInExterns_new6() {
    // While "externObjSEThisMethod" has modifies "this"
    // it does not have global side-effects with "this" is
    // a known local value.
    // TODO(johnlenz): "f" is side-effect free but we need
    // to propagate that "externObjSEThisMethod" is modifying
    // a local object.
    String source =
        """
        function f() {
          new externObjSEThis().externObjSEThisMethod('')
        };
        f();
        """;
    assertPureCallsMarked(
        source, ImmutableList.of("externObjSEThis", "new externObjSEThis().externObjSEThisMethod"));
  }

  @Test
  public void testAnnotationInExterns_new7() {
    // While "externObjSEThisMethod" has modifies "this"
    // it does not have global side-effects with "this" is
    // a known local value.
    String source =
        """
        function f() {
          var x = new externObjSEThis();
          x.externObjSEThisMethod('')
        };
        f();
        """;
    assertPureCallsMarked(source, ImmutableList.of("externObjSEThis"));
  }

  @Test
  public void testAnnotationInExterns_new8() {
    // "externObjSEThisMethod" modifies "this", the 'this'
    // is not a known local value, so it must be assumed it is to
    // have global side-effects.
    String source =
        """
        function f(x) {
          x.externObjSEThisMethod('')
        };
        f(new externObjSEThis());
        """;
    assertPureCallsMarked(source, ImmutableList.of("externObjSEThis"));
  }

  @Test
  public void testAnnotationInExterns_new9() {
    // "externObjSEThisMethod" modifies "this", the 'this'
    // is not a known local value, so it must be assumed it is to
    // have global side-effects.  All possible values of "x" are considered
    // as no intraprocedural data flow is done.
    String source =
        """
        function f(x) {
          x = new externObjSEThis();
          x.externObjSEThisMethod('')
        };
        f(g);
        """;
    assertPureCallsMarked(source, ImmutableList.of("externObjSEThis"));
  }

  @Test
  public void testAnnotationInExterns_new10() {
    String source =
        """
        function f() {
          new externObjSEThis().externObjSEThisMethod2('')
        };
        f();
        """;
    assertPureCallsMarked(
        source,
        ImmutableList.of("externObjSEThis", "new externObjSEThis().externObjSEThisMethod2", "f"));
  }

  @Test
  public void testAnnotationInExterns1() {
    assertNoPureCalls("externSef1()");
  }

  @Test
  public void testAnnotationInExterns2() {
    assertNoPureCalls("externSef2()");
  }

  @Test
  public void testAnnotationInExterns3() {
    assertPureCallsMarked("externNsef1()", ImmutableList.of("externNsef1"));
  }

  @Test
  public void testAnnotationInExterns4() {
    assertPureCallsMarked("externNsef2()", ImmutableList.of("externNsef2"));
  }

  @Test
  public void testAnnotationInExterns5() {
    assertPureCallsMarked("externNsef3()", ImmutableList.of("externNsef3"));
  }

  @Test
  public void testNamespaceAnnotationInExterns1() {
    assertNoPureCalls("externObj.sef1()");
  }

  @Test
  public void testNamespaceAnnotationInExterns2() {
    assertPureCallsMarked("externObj.nsef1()", ImmutableList.of("externObj.nsef1"));
  }

  @Test
  public void testNamespaceAnnotationInExterns3() {
    assertPureCallsMarked("externObj.nsef2()", ImmutableList.of("externObj.nsef2"));
  }

  @Test
  public void testNamespaceAnnotationInExterns4() {
    assertNoPureCalls("externObj.partialFn()");
  }

  @Test
  public void testNamespaceAnnotationInExterns5() {
    // Test that adding a second definition for a partially defined
    // function doesn't make us think that the function has no side
    // effects.
    String templateSrc = "var o = {}; o.<fnName> = function(){}; o.<fnName>()";

    // Ensure that functions with name != "partialFn" get marked.
    assertPureCallsMarked(
        templateSrc.replace("<fnName>", "notPartialFn"), ImmutableList.of("o.notPartialFn"));

    assertNoPureCalls(templateSrc.replace("<fnName>", "partialFn"));
  }

  @Test
  public void testNamespaceAnnotationInExterns6() {
    assertNoPureCalls("externObj.partialSharedFn()");
  }

  @Test
  public void testConstructorAnnotationInExterns1() {
    assertNoPureCalls("new externSefConstructor()");
  }

  @Test
  public void testConstructorAnnotationInExterns2() {
    String source =
        """
        var a = new externSefConstructor();
        a.sefFnOfSefObj()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testConstructorAnnotationInExterns3() {
    String source =
        """
        var a = new externSefConstructor();
        a.nsefFnOfSefObj()
        """;
    assertPureCallsMarked(source, ImmutableList.of("a.nsefFnOfSefObj"));
  }

  @Test
  public void testConstructorAnnotationInExterns4() {
    String source =
        """
        var a = new externSefConstructor();
        a.externShared()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testConstructorAnnotationInExterns5() {
    assertPureCallsMarked("new externNsefConstructor()", ImmutableList.of("externNsefConstructor"));
  }

  @Test
  public void testConstructorAnnotationInExterns6() {
    String source =
        """
        var a = new externNsefConstructor();
        a.sefFnOfNsefObj()
        """;
    assertPureCallsMarked(source, ImmutableList.of("externNsefConstructor"));
  }

  @Test
  public void testConstructorAnnotationInExterns7() {
    String source =
        """
        var a = new externNsefConstructor();
        a.nsefFnOfNsefObj()
        """;
    assertPureCallsMarked(source, ImmutableList.of("externNsefConstructor", "a.nsefFnOfNsefObj"));
  }

  @Test
  public void testConstructorAnnotationInExterns8() {
    String source =
        """
        var a = new externNsefConstructor();
        a.externShared()
        """;
    assertPureCallsMarked(source, ImmutableList.of("externNsefConstructor"));
  }

  @Test
  public void testSharedFunctionName1() {
    String source =
        """
        if (true) {
          a = new externNsefConstructor()
        } else {
          a = new externSefConstructor()
        }
        a.externShared()
        """;
    assertPureCallsMarked(source, ImmutableList.of("externNsefConstructor"));
  }

  @Test
  public void testSharedFunctionName2() {
    // Implementation for both externNsefConstructor and externNsefConstructor2
    // have no side effects.
    boolean broken = true;
    if (broken) {
      assertPureCallsMarked(
          """
          var a;
          if (true) {
            a = new externNsefConstructor()
          } else {
            a = new externNsefConstructor2()
          }
          a.externShared()
          """,
          ImmutableList.of("externNsefConstructor", "externNsefConstructor2"));
    } else {
      assertPureCallsMarked(
          """
          var a;
          if (true) {
            a = new externNsefConstructor()
          } else {
            a = new externNsefConstructor2()
          }
          a.externShared()
          """,
          ImmutableList.of("externNsefConstructor", "externNsefConstructor2", "a.externShared"));
    }
  }

  @Test
  public void testNoSideEffectsJsDocOnVariable() {
    assertPureCallsMarked(
        """
        /** @nosideeffects */
        function b() { throw 0; }
        b();
        """,
        ImmutableList.of("b"));
  }

  @Test
  public void testNoSideEffectsJsDocOnMethod() {
    assertPureCallsMarked(
        """
        class Foo {
          /** @nosideeffects */ b() { throw 0; }
        }
        x.b();
        """,
        ImmutableList.of("x.b"));
  }

  @Test
  public void testNoSideEffectsJsDoc_notPropagatedIntoNestedFunctions() {
    assertPureCallsMarked(
        """
        const obj = {};
        /** @nosideeffects */
        function pureOuter() {
          obj.impureInner = function() {
            throw 1;
          }
          /** @nosideeffects */
          obj.pureInner = function() {
            throw 1;
          }
          throw 0;
        }
        pureOuter();
        obj.impureInner();
        obj.pureInner();
        """,
        ImmutableList.of("pureOuter", "obj.pureInner"));
  }

  @Test
  public void testNoSideEffectsJsDoc_astWithinFunctionBodyStillMarkedWithSideEffects() {
    assertPureCallsMarked(
        """
        /** @nosideeffects */
        function pure() {
          // this is marked as a side-effectful call, but because of the earlier `@nosideeffects`
          // annotation, the call to `pure` is still marked as side-effect free.
          externSef1();
          externNsef1();
          throw 0;
        }
        pure();
        """,
        ImmutableList.of("pure", "externNsef1"));
  }

  @Test
  public void testAmbiguousExternAndSourceWithNoSideEffectsJsDoc() {
    test(
        externs("const a = {}; a.m = function() {};"),
        srcs("class C { /** @nosideeffects */ m() {}} foo.m();"));

    assertThat(noSideEffectCalls).isEmpty();
  }

  @Test
  public void testMultipleMethodDefinitionsOnlyOneWithNoSideEffectsJsDoc_otherIsPure() {
    assertPureCallsMarked(
        """
        class Foo {
          /** @nosideeffects */ b() { throw 0; }
        }
        class Bar {
          b() {}
        }
        x.b();
        """,
        ImmutableList.of("x.b"));
  }

  @Test
  public void testMultipleMethodDefinitionsOnlyOneWithNoSideEffectsJsDoc_otherHasSideEffects() {
    assertNoPureCalls(
        """
        class Foo {
          /** @nosideeffects */ b() { throw 0; }
        }
        class Bar {
          b() { throw 0; }
        }
        x.b();
        """);
  }

  @Test
  public void testMultipleFunctionDefinitionsOnlyOneWithNoSideEffectsJsDoc() {
    String source =
        """
        let b = /** @nosideeffects */(function(){ throw 0; });
        if (cond) {
          b = () => {}
        }
        b()
        """;
    test(externs(""), srcs(source));
    assertThat(noSideEffectCalls).hasSize(1);
  }

  @Test
  public void testNoSideEffectsJsDoc_errorIfCannotBeApplied_ambiguousDefModifyingArguments() {
    this.enableArtificialPurityDebugError = true;
    test(
        srcs(
            """
            class Foo {
              /** @nosideeffects */ b() { throw 0; } // line 2
            }
            class Bar {
              b(obj) { obj.prop = 34; } // line 5
            }
            x.b();
            """),
        error(PureFunctionIdentifier.UNUSED_ARTIFICIAL_PURE_ANNOTATION)
            .withMessageContaining(
                """
                Side-effectful definitions:
                  testcode:5:2\
                """));
  }

  @Test
  public void testNoSideEffectsJsDoc_errorIfCannotBeApplied_ambiguousDefModifyingThis() {
    this.enableArtificialPurityDebugError = true;
    test(
        srcs(
            """
            class Foo {
              /** @nosideeffects */ b() { throw 0; } // line 2
            }
            class Bar {
              b() { this.prop = 34; } // line 5
            }
            x.b();
            """),
        error(PureFunctionIdentifier.UNUSED_ARTIFICIAL_PURE_ANNOTATION)
            .withMessageContaining(
                """
                Side-effectful definitions:
                  testcode:5:2\
                """));
  }

  @Test
  public void testNoSideEffectsJsDoc_errorIfCannotBeApplied_ambiguousDefWithThrow() {
    this.enableArtificialPurityDebugError = true;
    test(
        srcs(
            """
            class Foo {
              /** @nosideeffects */ b() { throw 0; } // line 2
            }
            class Bar {
              b() { throw 0; } // line 5
            }
            x.b();
            """),
        error(PureFunctionIdentifier.UNUSED_ARTIFICIAL_PURE_ANNOTATION)
            .withMessageContaining(
                """
                Side-effectful definitions:
                  testcode:5:2\
                """));
  }

  @Test
  public void testNoSideEffectsJsDoc_errorIfCannotBeApplied_ambiguousExternFunction() {
    this.enableArtificialPurityDebugError = true;
    test(
        externs(
            """
            class Bar {
              b() {}
            }
            """),
        srcs(
            """
            class Foo {
              /** @nosideeffects */ b() { throw 0; } // line 2
            }
            x.b();
            """),
        error(PureFunctionIdentifier.UNUSED_ARTIFICIAL_PURE_ANNOTATION)
            .withMessageContaining(
                """
                Side-effectful definitions:
                  externs:2:2\
                """));
  }

  @Test
  public void testMultipleFunctionDefinitions_onlySomeWithNoSideEffectsJsDoc_errorForDebugging() {
    this.enableArtificialPurityDebugError = true;
    test(
        srcs(
            """
            class Foo {
              /** @nosideeffects */ b() { throw 0; } // line 2
            }
            class Bar {
              b() { throw 0; } // line 5
            }
            class Baz {
              b() {} // line 8
            }
            class Qux {
              b() {
                // multiple side-effectful lines doesn't cause duplicate error message lines.
                if (cond) {
                  throw 1;
                } else {
                  throw 2;
                }
              } // line 11
            }
            x.b();
            """),
        error(PureFunctionIdentifier.UNUSED_ARTIFICIAL_PURE_ANNOTATION)
            .withMessageContaining(
                """
                Side-effectful definitions:
                  testcode:5:2
                  testcode:11:2\
                """));
  }

  @Test
  public void testConstructorPropertyWithSideEffects_skipDebuggingError() {
    this.enableArtificialPurityDebugError = true;
    testNoWarning(
        externs("Object.prototype.constructor = function() {};"),
        srcs(
            """
            class Foo {
              /** @nosideeffects */
              constructor() { throw 0; }
            }
            new Foo();
            """));
  }

  @Test
  public void testNoSideEffectsOnConstructor() {
    assertPureCallsMarked(
        """
        class Foo {
          /** @nosideeffects */ constructor() { throw 0; }
        }
        new Foo();
        """,
        ImmutableList.of("Foo"));
  }

  @Test
  public void testNoSideEffectsOnConstructor_ambiguousWithOtherSideEffects() {
    assertPureCallsMarked(
        """
        class Foo {
          /** @nosideeffects */ constructor() { throw 0; }
        }
        class Bar {
          constructor() { throw 0; }
        }
        new Foo();
        new Bar();
        """,
        ImmutableList.of("Foo"));
  }

  @Test
  public void testNoSideEffectsNoPropagationFromOtherCalls() {
    String source =
        """
        var hasSideEffects = function() { throw 0; }
        var foo = /** @nosideeffects */ function(){ hasSideEffects(); return 0; };
        var bar = function(){ hasSideEffects(); return 0; };
        foo();
        bar();
        """;
    // bar is not marked as pure because it inherits side effects from hasSideEffects
    assertPureCallsMarked(source, ImmutableList.of("foo"));
  }

  @Test
  public void testNoSideEffectsNoPropagationToOtherCalls() {
    String source =
        """
        var liesAboutSideEffects = /** @nosideeffects */ function() { throw 0; }
        var foo = function(){ liesAboutSideEffects(); return 0; };
        var bar = function(){ liesAboutSideEffects(); return 0; };
        foo();
        bar();
        """;
    assertPureCallsMarked(
        source, ImmutableList.of("liesAboutSideEffects", "liesAboutSideEffects", "foo", "bar"));
  }

  @Test
  public void testAnnotationInExternStubs1() {
    // In the case where a property is defined first as a stub and then with a FUNCTION:
    // we have to make a conservative assumption about the behaviour of the extern, since the stub
    // carried no information.
    assertNoPureCalls("o.propWithStubBefore('a');");
  }

  @Test
  public void testAnnotationInExternStubs1b() {
    // In the case where a property is defined first as a stub and then with a FUNCTION:
    // we have to make a conservative assumption about the behaviour of the extern, since the stub
    // carried no information.
    assertNoPureCalls("o.propWithStubBeforeWithJSDoc('a');");
  }

  @Test
  public void testAnnotationInExternStubs2() {
    // In the case where a property is defined first with a FUNCTION and then as a stub:
    // we have to make a conservative assumption about the behaviour of the extern, since the stub
    // carried no information.
    assertNoPureCalls("o.propWithStubAfter('a');");
  }

  @Test
  public void testAnnotationInExternStubs3() {
    // In the case where a property is defined first with a FUNCTION and then as a stub:
    // we have to make a conservative assumption about the behaviour of the extern, since the stub
    // carried no information.
    assertNoPureCalls("propWithAnnotatedStubAfter('a');");
  }

  @Test
  public void testAnnotationInExternStubs4() {
    // An externs definition with a stub that differs from the declaration.
    // Verify our assumption is valid about this.
    String externs =
        """
        /**@constructor*/function externObj5(){}
        externObj5.prototype.propWithAnnotatedStubAfter = function(s) {};
        /**
         * @param {string} s id.
         * @return {string}
         * @nosideeffects
         */
        externObj5.prototype.propWithAnnotatedStubAfter;
        """;

    enableTypeCheck();
    testSame(externs(externs), srcs("o.prototype.propWithAnnotatedStubAfter"));
    assertThat(noSideEffectCalls).isEmpty();
  }

  @Test
  public void testAnnotationInExternStubs5() {
    // An externs definition with a stub that differs from the declaration.
    // Verify our assumption is valid about this.
    String externs =
        """
        /**@constructor*/function externObj5(){}
        /**
         * @param {string} s id.
         * @return {string}
         * @nosideeffects
         */
        externObj5.prototype.propWithAnnotatedStubAfter = function(s) {};
        /**
         * @param {string} s id.
         * @return {string}
         */
        externObj5.prototype.propWithAnnotatedStubAfter;
        """;

    enableTypeCheck();
    testSame(externs(externs), srcs("o.prototype.propWithAnnotatedStubAfter"));
    assertThat(noSideEffectCalls).isEmpty();
  }

  @Test
  public void testNoSideEffectsSimple() {
    String prefix = "function f(){";
    String suffix = "} f()";
    ImmutableList<String> expected = ImmutableList.of("f");

    assertPureCallsMarked(prefix + "" + suffix, expected);
    assertPureCallsMarked(prefix + "return 1" + suffix, expected);
    assertPureCallsMarked(prefix + "return 1 + 2" + suffix, expected);

    // local var
    assertPureCallsMarked(prefix + "var a = 1; return a" + suffix, expected);

    // mutate local var
    assertPureCallsMarked(prefix + "var a = 1; a = 2; return a" + suffix, expected);
    assertPureCallsMarked(prefix + "var a = 1; a = 2; return a + 1" + suffix, expected);

    // read from obj literal
    assertPureCallsMarked(prefix + "var a = {foo : 1}; return a.foo" + suffix, expected);
    assertPureCallsMarked(prefix + "var a = {foo : 1}; return a.foo + 1" + suffix, expected);

    // read from extern
    assertPureCallsMarked(prefix + "return externObj" + suffix, expected);
    assertPureCallsMarked(
        "function g(x) { x.foo = 3; }" /* to suppress missing property */
            + prefix
            + "return externObj.foo"
            + suffix,
        expected);
  }

  @Test
  public void testNoSideEffectsSimple2() {
    regExpHaveSideEffects = false;
    String source =
        """
        function f() {
          return ''.replace(/xyz/g, '');
        }
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("''.replace", "f"));
  }

  @Test
  public void testNoSideEffectsSimple3() {
    regExpHaveSideEffects = false;
    String source =
        """
        function f(/** string */ x) {
          return x.replace(/xyz/g, '');
        }
        f('')
        """;
    assertPureCallsMarked(source, ImmutableList.of("x.replace", "f"));
  }

  @Test
  public void testExternCalls() {
    testExternCallsForTypeInferenceMode(/* typeChecked= */ true);
  }

  @Test
  public void testExternCallsNoTypeChecking() {
    testExternCallsForTypeInferenceMode(/* typeChecked= */ false);
  }

  private void testExternCallsForTypeInferenceMode(boolean typeChecked) {
    if (typeChecked) {
      enableTypeCheck();
    } else {
      disableTypeCheck();
    }
    String prefix = "function f(){";
    String suffix = "} f()";

    assertPureCallsMarked(prefix + "externNsef1()" + suffix, ImmutableList.of("externNsef1", "f"));
    assertPureCallsMarked(
        prefix + "externObj.nsef1()" + suffix, ImmutableList.of("externObj.nsef1", "f"));

    assertNoPureCalls(prefix + "externSef1()" + suffix);
    assertNoPureCalls(prefix + "externObj.sef1()" + suffix);
  }

  @Test
  public void testApply() {
    String source =
        """
        function f() {return 42}
        f.apply(null)
        """;
    assertPureCallsMarked(source, ImmutableList.of("f.apply"));
  }

  @Test
  public void testCall() {
    String source =
        """
        function f() {return 42}
        f.call(null)
        """;
    assertPureCallsMarked(source, ImmutableList.of("f.call"));
  }

  @Test
  public void testApplyToUnknownDefinition() {
    String source =
        """
        var dict = {'func': function() {}};
        function f() { var s = dict['func'];}
        f.apply(null)
        """;
    assertPureCallsMarked(source, ImmutableList.of("f.apply"));

    // Not marked because the definition cannot be found so unknown side effects.
    source =
        """
        var dict = {'func': function() {}};
        function f() { var s = dict['func'].apply(null); }
        f.apply(null)
        """;
    assertNoPureCalls(source);

    // Not marked because the definition cannot be found so unknown side effects.
    source =
        """
        var pure = function() {};
        var dict = {'func': function() {}};
        function f() { var s = (dict['func'] || pure)();}
        f()
        """;
    assertNoPureCalls(source);

    // Not marked because the definition cannot be found so unknown side effects.
    source =
        """
        var pure = function() {};
        var dict = {'func': function() {}};
        function f() { var s = (condition ? dict['func'] : pure)();}
        f()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testOptChainCall() {
    String source =
        """
        function f() {return g?.()}
        function g() {return 42}
        f?.()
        """;
    assertPureCallsMarked(source, ImmutableList.of("g", "f"));
  }

  @Test
  public void testInference1() {
    String source =
        """
        function f() {return g()}
        function g() {return 42}
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("g", "f"));
  }

  @Test
  public void testInference2() {
    String source =
        """
        var a = 1;
        function f() {g()}
        function g() {a=2}
        f()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testInference3() {
    String source =
        """
        var f = function() {return g()};
        var g = function() {return 42};
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("g", "f"));
  }

  @Test
  public void testInference4() {
    String source =
        """
        var a = 1; var f = function() {g()};
        var g = function() {a=2};
        f()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testInference5() {
    String source =
        """
        goog.f = function() {return goog.g()};
        goog.g = function() {return 42};
        goog.f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("goog.g", "goog.f"));
  }

  @Test
  public void testInference6() {
    String source =
        """
        var a = 1;
        goog.f = function() {goog.g()};
        goog.g = function() {a=2};
        goog.f()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testLocalizedSideEffects1() {
    // Returning a function that contains a modification of a local
    // is not a global side-effect.
    String source =
        """
        function f() {
          var x = {foo : 0}; return function() {x.foo++};
        }
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  @Test
  public void testLocalizedSideEffects2() {
    // Calling a function that contains a modification of a local
    // is a global side-effect (the value has escaped).
    String source =
        """
        function f() {
          var x = {foo : 0}; (function() {x.foo++})();
        }
        f()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testLocalizedSideEffects3() {
    // A local that might be assigned a global value and whose properties
    // are modified must be considered a global side-effect.
    String source =
        """
        var g = {foo:1};
        function f() {var x = g; x.foo++};
        f();
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testLocalizedSideEffects4() {
    // An array is a local object, assigning a local array is not a global side-effect.
    assertPureCallsMarked(
        """
        function f() {var x = []; x[0] = 1;} // preserve newline
        f()
        """,
        ImmutableList.of("f"));

    assertPureCallsMarked(
        """
        function f() {const x = []; x[0] = 1;} // preserve newline
        f()
        """,
        ImmutableList.of("f"));
  }

  @Test
  public void testLocalizedSideEffects5() {
    // Assigning a local alias of a global is a global
    // side-effect.
    String source =
        """
        var g = [];
        function f() {var x = g; x[0] = 1;};
        f()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testLocalizedSideEffects6() {
    // Returning a local object that has been modified
    // is not a global side-effect.
    assertPureCallsMarked(
        """
        function f() { // preserve newline
          var x = {}; x.foo = 1; return x;
        }
        f()
        """,
        ImmutableList.of("f"));

    assertPureCallsMarked(
        """
        function f() { // preserve newline
          const x = {}; x.foo = 1; return x;
        }
        f()
        """,
        ImmutableList.of("f"));
  }

  @Test
  public void testLocalizedSideEffects7() {
    // Returning a local object that has been modified
    // is not a global side-effect.
    assertPureCallsMarked(
        """
        /** @constructor A */ function A() {};
        function f() {
          var a = []; a[1] = 1; return a;
        }
        f()
        """,
        ImmutableList.of("f"));

    assertPureCallsMarked(
        """
        /** @constructor A */ function A() {};
        function f() {
          const a = []; a[1] = 1; return a;
        }
        f()
        """,
        ImmutableList.of("f"));
  }

  @Test
  public void testLocalizedSideEffects8() {
    // Returning a local object that has been modified
    // is not a global side-effect.
    // TODO(tdeegan): Not yet. Propagate local object information.
    String source =
        """
        /** @constructor A */ function A() {};
        function f() {
          var a = new A; a.foo = 1; return a;
        }
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("A"));
  }

  @Test
  public void testLocalizedSideEffects9() {
    // Returning a local object that has been modified
    // is not a global side-effect.
    // TODO(johnlenz): Not yet. Propagate local object information.
    String source =
        """
        /** @constructor A */ function A() {this.x = 1};
        function f() {
          var a = new A; a.foo = 1; return a;
        }
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("A"));
  }

  @Test
  public void testLocalizedSideEffects10() {
    // Returning a local object that has been modified
    // is not a global side-effect.
    String source =
        """
        /** @constructor A */ function A() {};
        A.prototype.g = function() {this.x = 1};
        function f() {
          var a = new A; a.g(); return a;
        }
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("A"));
  }

  @Test
  public void testLocalizedSideEffects11() {
    // TODO(tdeegan): updateA is side effect free.
    // Calling a function of a local object that taints this.
    String source =
        """
        /** @constructor */
        function A() {}
        A.prototype.update = function() { this.x = 1; };

        /** @constructor */
        function B() {
          this.a_ = new A();
        }
        B.prototype.updateA = function() {
          var b = this.a_;
          b.update();
        };

        var x = new B();
        x.updateA();
        """;
    assertPureCallsMarked(source, ImmutableList.of("A", "B"));
  }

  @Test
  public void testLocalizedSideEffects12() {
    // An array is a local object, assigning a local array is not a global
    // side-effect. This tests the behavior if the access is in a block scope.
    assertPureCallsMarked(
        """
        function f() {var x = []; { x[0] = 1; } } // preserve newline
        f()
        """,
        ImmutableList.of("f"));

    assertPureCallsMarked(
        """
        function f() {const x = []; { x[0] = 1; } } // preserve newline
        f()
        """,
        ImmutableList.of("f"));
  }

  @Test
  public void testLocalizedSideEffects13() {
    String source =
        """
        function f() {var [x, y] = [3, 4]; }
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  @Test
  public void testLocalizedSideEffects14() {
    String source =
        """
        function f() {var x; if (true) { [x] = [5]; } }
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  @Test
  public void testLocalizedSideEffects15() {
    String source =
        """
        function f() {var {length} = 'a string'; }
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  @Test
  public void testLocalizedSideEffects16() {
    String source =
        """
        function f(someArray) {var [a, , b] = someArray; }
        f([])
        """;
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  @Test
  public void testLocalizedSideEffects17() {

    String source =
        """
        function f(someObj) {var { very: { nested: { lhs: pattern }} } = someObj; }
        f({very: {nested: {lhs: 0}}})
        """;
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  @Test
  public void testLocalizedSideEffects18() {
    String source =
        """
        /** @constructor */
        function SomeCtor() { [this.x, this.y] = getCoordinates(); }
        new SomeCtor()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testLocalizedSideEffects19() {
    String source =
        """
        /** @constructor */
        function SomeCtor() { [this.x, this.y] = [0, 1]; }
        new SomeCtor()
        """;
    assertPureCallsMarked(source, ImmutableList.of("SomeCtor"));
  }

  @Test
  public void testLocalizedSideEffects20() {
    String source =
        """
        /** @constructor */
        function SomeCtor() { this.x += 1; }
        new SomeCtor()
        """;
    assertPureCallsMarked(source, ImmutableList.of("SomeCtor"));
  }

  @Test
  public void testLocalizedSideEffects21() {
    String source =
        """
        function f(values) { const x = {}; [x.y, x.z] = values; }
        f([])
        """;
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  @Test
  public void testLocalizedSideEffects22() {
    String source =
        """
        var x = {};
        function f(values) { [x.y, x.z] = values; }
        f([])
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testLocalizedSideEffects23() {
    String source =
        """
        function f(values) { const x = {}; [x.y, x.z = defaultNoSideEffects] = values; }
        f([])
        """;
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  @Test
  public void testLocalizedSideEffects24() {
    String source =
        """
        function f(values) { var x = {}; [x.y, x.z = defaultWithSideEffects()] = values; }
        f([])
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testUnaryOperators1() {
    String source =
        """
        function f() {var x = 1; x++}
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  @Test
  public void testUnaryOperators2() {
    String source =
        """
        var x = 1;
        function f() {x++}
        f()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testUnaryOperators3() {
    assertPureCallsMarked(
        """
        function f() {var x = {foo : 0}; x.foo++} // preserve newline
        f()
        """,
        ImmutableList.of("f"));

    assertPureCallsMarked(
        """
        function f() {const x = {foo : 0}; x.foo++} // preserve newline
        f()
        """,
        ImmutableList.of("f"));
  }

  @Test
  public void testUnaryOperators4() {
    String source =
        """
        var x = {foo : 0};
        function f() {x.foo++}
        f()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testUnaryOperators5() {
    assertPureCallsMarked(
        """
        function f(x) {x.foo++} // preserve newline
        f({foo : 0})
        """,
        ImmutableList.of("f"));

    assertNoPureCalls(
        """
        function f({x}) {x.foo++} // preserve newline
        f({x: {foo : 0}})
        """);
  }

  @Test
  public void testDeleteOperator1() {
    String source =
        """
        var x = {};
        function f() {delete x}
        f()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testDeleteOperator1_getProp() {
    String source =
        """
        var x = {y:2};
        function f() {delete x.y}
        f()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testDeleteOperator1_optChainGetProp() {
    String source =
        """
        var x = {y:2};
        function f() {delete x?.y}
        f()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testDeleteOperator2() {
    String source =
        """
        function f() {var x = {}; delete x}
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  @Test
  public void testDeleteOperator2_getProp() {
    String source =
        """
        function f() {var x = {y:2}; delete x.y}
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  @Test
  public void testDeleteOperator2_optChainGetProp() {
    String source =
        """
        function f() {var x = {y:2}; delete x?.y}
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  // Start pure alias

  @Test
  public void testOperatorsTraversal_pureAlias_pureAlias() {
    assertCallableExpressionPure(true, "externNsef1 || externNsef2");
    assertCallableExpressionPure(true, "externNsef1 && externNsef2");
    assertCallableExpressionPure(true, "externNsef1, externNsef2");
    assertCallableExpressionPure(true, "x ? externNsef1 : externNsef2");
  }

  @Test
  public void testOperatorsTraversal_pureAlias_pureLiteral() {
    assertCallableExpressionPure(true, "externNsef1 || function() { }");
    assertCallableExpressionPure(true, "externNsef1 && function() { }");
    assertCallableExpressionPure(true, "externNsef1, function() { }");
    assertCallableExpressionPure(true, "x ? externNsef1 : function() { }");
  }

  @Test
  public void testOperatorsTraversal_pureAlias_impureAlias() {
    assertCallableExpressionPure(false, "externNsef1 || externSef2");
    assertCallableExpressionPure(false, "externNsef1 && externSef2");
    assertCallableExpressionPure(false, "externNsef1, externSef2");
    assertCallableExpressionPure(false, "x ? externNsef1 : externSef2");
  }

  @Test
  public void testOperatorsTraversal_pureAlias_impureLiteral() {
    assertCallableExpressionPure(false, "externNsef1 || function() { throw 0; }");
    assertCallableExpressionPure(false, "externNsef1 && function() { throw 0; }");
    assertCallableExpressionPure(false, "externNsef1, function() { throw 0; }");
    assertCallableExpressionPure(false, "x ? externNsef1 : function() { throw 0; }");
  }

  @Test
  public void nullishCoalesce_pureAlias() {
    assertCallableExpressionPure(true, "externNsef1 ?? externNsef2");
    assertCallableExpressionPure(true, "externNsef1 ?? function() { }");
    assertCallableExpressionPure(false, "externNsef1 ?? externSef2");
    assertCallableExpressionPure(false, "externNsef1 ?? function() { throw 0; }");
  }

  // End pure alias, start pure literal

  @Test
  public void testOperatorsTraversal_pureLiteral_pureAlias() {
    assertCallableExpressionPure(true, "function() { } || externNsef2");
    assertCallableExpressionPure(true, "function() { } && externNsef2");
    assertCallableExpressionPure(true, "function() { }, externNsef2");
    assertCallableExpressionPure(true, "x ? function() { } : externNsef2");
  }

  @Test
  public void testOperatorsTraversal_pureLiteral_pureLiteral() {
    assertCallableExpressionPure(true, "function() { } || function() { }");
    assertCallableExpressionPure(true, "function() { } && function() { }");
    assertCallableExpressionPure(true, "function() { }, function() { }");
    assertCallableExpressionPure(true, "x ? function() { } : function() { }");
  }

  @Test
  public void testOperatorsTraversal_pureLiteral_impureAlias() {
    assertCallableExpressionPure(false, "function() { } || externSef2");
    assertCallableExpressionPure(false, "function() { } && externSef2");
    assertCallableExpressionPure(false, "function() { }, externSef2");
    assertCallableExpressionPure(false, "x ? function() { } : externSef2");
  }

  @Test
  public void testOperatorsTraversal_pureLiteral_impureLiteral() {
    assertCallableExpressionPure(false, "function() { } || function() { throw 0; }");
    assertCallableExpressionPure(false, "function() { } && function() { throw 0; }");
    assertCallableExpressionPure(false, "function() { }, function() { throw 0; }");
    assertCallableExpressionPure(false, "x ? function() { } : function() { throw 0; }");
  }

  @Test
  public void nullishCoalesce_pureLiteral() {
    assertCallableExpressionPure(true, "function() { } ?? externNsef2");
    assertCallableExpressionPure(true, "function() { } ?? function() { }");
    assertCallableExpressionPure(false, "function() { } ?? externSef2");
    assertCallableExpressionPure(false, "function() { } ?? function() { throw 0; }");
  }

  // End pure literal, start impure alias

  @Test
  public void testOperatorsTraversal_impureAlias_pureAlias() {
    assertCallableExpressionPure(false, "externSef1 || externNsef2");
    assertCallableExpressionPure(false, "externSef1 && externNsef2");
    assertCallableExpressionPure(true, "externSef1, externNsef2");
    assertCallableExpressionPure(false, "x ? externSef1 : externNsef2");
  }

  @Test
  public void testOperatorsTraversal_impureAlias_pureLiteral() {
    assertCallableExpressionPure(false, "externSef1 || function() { }");
    assertCallableExpressionPure(false, "externSef1 && function() { }");
    assertCallableExpressionPure(true, "externSef1, function() { }");
    assertCallableExpressionPure(false, "x ? externSef1 : function() { }");
  }

  @Test
  public void testOperatorsTraversal_impureAlias_impureAlias() {
    assertCallableExpressionPure(false, "externSef1 || externSef2");
    assertCallableExpressionPure(false, "externSef1 && externSef2");
    assertCallableExpressionPure(false, "externSef1, externSef2");
    assertCallableExpressionPure(false, "x ? externSef1 : externSef2");
  }

  @Test
  public void testOperatorsTraversal_impureAlias_impureLiteral() {
    assertCallableExpressionPure(false, "externSef1 || function() { throw 0; }");
    assertCallableExpressionPure(false, "externSef1 && function() { throw 0; }");
    assertCallableExpressionPure(false, "externSef1, function() { throw 0; }");
    assertCallableExpressionPure(false, "x ? externSef1 : function() { throw 0; }");
  }

  @Test
  public void nullishCoalesce_impureAlias() {
    assertCallableExpressionPure(false, "externSef1 ?? externNsef2");
    assertCallableExpressionPure(false, "externSef1 ?? function() { }");
    assertCallableExpressionPure(false, "externSef1 ?? externSef2");
    assertCallableExpressionPure(false, "externSef1 ?? function() { throw 0; }");
  }

  // End impure alias, start impure literal

  @Test
  public void testOperatorsTraversal_impureLiteral_pureAlias() {
    assertCallableExpressionPure(false, "function() { throw 0; } || externNsef2");
    assertCallableExpressionPure(false, "function() { throw 0; } && externNsef2");
    assertCallableExpressionPure(true, "function() { throw 0; }, externNsef2");
    assertCallableExpressionPure(false, "x ? function() { throw 0; } : externNsef2");
  }

  @Test
  public void testOperatorsTraversal_impureLiteral_pureLiteral() {
    assertCallableExpressionPure(false, "function() { throw 0; } || function() { }");
    assertCallableExpressionPure(false, "function() { throw 0; } && function() { }");
    assertCallableExpressionPure(true, "function() { throw 0; }, function() { }");
    assertCallableExpressionPure(false, "x ? function() { throw 0; } : function() { }");
  }

  @Test
  public void testOperatorsTraversal_impureLiteral_impureAlias() {
    assertCallableExpressionPure(false, "function() { throw 0; } || externSef2");
    assertCallableExpressionPure(false, "function() { throw 0; } && externSef2");
    assertCallableExpressionPure(false, "function() { throw 0; }, externSef2");
    assertCallableExpressionPure(false, "x ? function() { throw 0; } : externSef2");
  }

  @Test
  public void testOperatorsTraversal_impureLiteral_impureLiteral() {
    assertCallableExpressionPure(false, "function() { throw 0; } || function() { throw 0; }");
    assertCallableExpressionPure(false, "function() { throw 0; } && function() { throw 0; }");
    assertCallableExpressionPure(false, "function() { throw 0; }, function() { throw 0; }");
    assertCallableExpressionPure(false, "x ? function() { throw 0; } : function() { throw 0; }");
  }

  @Test
  public void nullishCoalesce_impureLiteral() {
    assertCallableExpressionPure(false, "function() { throw 0; } ?? externNsef2");
    assertCallableExpressionPure(false, "function() { throw 0; } ?? function() { }");
    assertCallableExpressionPure(false, "function() { throw 0; } ?? externSef2");
    assertCallableExpressionPure(false, "function() { throw 0; } ?? function() { throw 0; }");
  }

  // End impure literal

  @Test
  public void testKnownDynamicFunctions_areSkiplistedForAliasing() {
    for (String name : ImmutableList.of("call", "apply", "constructor")) {
      assertNoPureCalls(
          // Create a known pure definition so we're sure the name isn't just undefined.
          """
          foo.NAME = function() { };
          const f = foo.NAME;
          f();
          """
              .replace("NAME", name));
    }
  }

  @Test
  public void testThrow1() {
    String source =
        """
        function f(){throw Error()};
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("Error"));
  }

  @Test
  public void testThrow2() {
    String source =
        """
        /**@constructor*/function A(){throw Error()};
        function f(){return new A()}
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("Error"));
  }

  @Test
  public void testThrow_insideTry_withCatch_hasNoSideEffects() {
    String source =
        """
        function f() {
          try {
            throw Error();
          } catch (e) {
          } finally {
          }
        }

        f();
        """;
    assertPureCallsMarked(source, ImmutableList.of("f", "Error"));
  }

  @Test
  public void testThrow_insideTry_withoutCatch_hasSideEffects() {
    String source =
        """
        function f() {
          try {
            throw Error();
          } finally {
          }
        }

        f();
        """;
    assertPureCallsMarked(source, ImmutableList.of("Error"));
  }

  @Test
  public void testThrow_insideCatch_hasSideEffects() {
    String source =
        """
        function f() {
          try {
          } catch (e) {
            throw Error();
          } finally {
          }
        }

        f();
        """;
    assertPureCallsMarked(source, ImmutableList.of("Error"));
  }

  @Test
  public void testThrow_insideFinally_hasSideEffects() {
    String source =
        """
        function f() {
          try {
          } catch (e) {
          } finally {
            throw Error();
          }
        }

        f();
        """;
    assertPureCallsMarked(source, ImmutableList.of("Error"));
  }

  @Test
  public void testThrow_insideTry_afterNestedTry_hasNoSideEffects() {
    /* Ensure we track the stack of trys correctly, rather than just toggling a boolean. */
    String source =
        """
        function f() {
          try {
            try {
            } finally {
            }

            throw Error();
          } catch (e) {
          } finally {
          }
        }

        f();
        """;
    assertPureCallsMarked(source, ImmutableList.of("f", "Error"));
  }

  @Test
  public void testThrow_insideFunction_insideTry_hasNoSideEffects() {
    /* Ensure we track the stack of trys correctly, rather than just toggling a boolean. */
    String source =
        """
        function f() {
          try {
            function g() {
              throw Error();
            }
          } catch (e) {
          } finally {
          }
        }

        f();
        """;
    assertPureCallsMarked(source, ImmutableList.of("f", "Error"));
  }

  @Test
  public void testThrow_fromCallee_insideTry_withCatch_hasNoSideEffects() {
    String source =
        """
        function h() {
          throw Error();
        }

        function f() {
          try {
            h()
          } catch (e) {
          } finally {
          }
        }

        h();
        f();
        """;
    assertPureCallsMarked(source, ImmutableList.of("f", "Error"));
  }

  @Test
  public void testAssignmentOverride() {
    String source =
        """
        /**@constructor*/function A(){}
        A.prototype.foo = function(){};
        var a = new A;
        a.foo();
        """;
    assertPureCallsMarked(source, ImmutableList.of("A", "a.foo"));

    // Ideally inline aliases takes care of this.
    String sourceOverride =
        """
        /**@constructor*/ function A(){}
        A.prototype.foo = function(){};
        var x = 1
        function f(){x = 10}
        var a = new A;
        a.foo = f;
        a.foo();
        """;
    assertPureCallsMarked(sourceOverride, ImmutableList.of("A"));
  }

  @Test
  public void testAmbiguousDefinitions() {
    String source =
        CompilerTypeTestCase.CLOSURE_DEFS
            + """
            var globalVar = 1;
            A.f = function() {globalVar = 2;};
            A.f = function() {};
            function sideEffectCaller() { A.f() };
            sideEffectCaller();
            """;
    // Can't tell which f is being called so it assumes both.
    assertNoPureCalls(source);
  }

  @Test
  public void testAmbiguousDefinitionsCall() {
    String source =
        CompilerTypeTestCase.CLOSURE_DEFS
            + """
            var globalVar = 1;
            A.f = function() {globalVar = 2;};
            A.f = function() {};
            function sideEffectCaller() { A.f.call(null); };
            sideEffectCaller();
            """;
    // Can't tell which f is being called so it assumes both.
    assertNoPureCalls(source);
  }

  @Test
  public void testAmbiguousDefinitionsAllPropagationTypes() {
    String source =
        CompilerTypeTestCase.CLOSURE_DEFS
            + """
            /**@constructor*/A.f = function() { this.x = 5; };
            /**@constructor*/B.f = function() {};
            function sideEffectCaller() { new C.f() };
            sideEffectCaller();
            """;
    // "f" is only known to mutate `this`, but `this` is a local object when invoked with `new`.
    assertPureCallsMarked(source, ImmutableList.of("C.f", "sideEffectCaller"));
  }

  @Test
  public void testAmbiguousDefinitionsCallWithThis() {
    String source =
        CompilerTypeTestCase.CLOSURE_DEFS
            + """
            A.modifiesThis = function() { this.x = 5; };
            B.modifiesThis = function() {};

            /** @constructor */ function Constructor() { B.modifiesThis.call(this); };

            new Constructor();
            A.modifiesThis();
            """;

    // Can't tell which modifiesThis is being called so it assumes both.
    assertPureCallsMarked(source, ImmutableList.of("Constructor"));
  }

  @Test
  public void testAmbiguousDefinitionsBothCallThis() {
    String source =
        """
        B.f = function() {
          this.x = 1;
        }
        /** @constructor */ function C() {
          this.f.apply(this);
        }
        C.prototype.f = function() {
          this.x = 2;
        }
        new C();
        """;
    assertPureCallsMarked(source, ImmutableList.of("C"));
  }

  @Test
  public void testAmbiguousDefinitionsBothCallThisWithOptionalChain() {
    String source =
        """
        B.f = function() {
          this.x = 1;
        }
        /** @constructor */ function C() {
          this.f?.apply(this);
        }
        C.prototype.f = function() {
          this.x = 2;
        }
        new C();
        """;
    assertPureCallsMarked(source, ImmutableList.of("C"));
  }

  @Test
  public void testAmbiguousDefinitionsAllCallThis() {
    String source =
        """
        A.f = function() { this.y = 1 };
        C.f = function() { };
        var g = function() {D.f()};
        /** @constructor */ var h = function() {E.f.apply(this)};
        // TODO(nickreid): Detect that `{}` being passed as `this` is local. With that
        // understanding, we could determine that `i` has no side-effects.
        var i = function() { F.f.apply({}); };
        g();
        new h();
        i();
        """;
    assertPureCallsMarked(source, ImmutableList.of("F.f.apply", "h"));
  }

  @Test
  public void testAmbiguousDefinitionsMutatesGlobalArgument() {
    String source =
        """
        // Mutates argument
        A.a = function(argument) {
          argument.x = 2;
        };
        // No side effects
        B.a = function() {};
        var b = function(x) {C.a(x)};
        b({});
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testAmbiguousDefinitionsMutatesLocalArgument() {
    assertPureCallsMarked(
        """
        // Mutates argument
        A.a = function(argument) {
          argument.x = 2;
        };
        // No side effects
        B.a = function() {};
        var b = function() {
          C.a({});
        };
        b();
        """,
        ImmutableList.of("C.a", "b"));
  }

  @Test
  public void testAmbiguousExternDefinitions() {
    assertNoPureCalls("x.duplicateExternFunc()");

    // nsef1 is defined as no side effect in the externs.
    String source =
        """
        var global = 1;
        // Overwrite the @nosideeffects with this side effect
        A.nsef1 = function () {global = 2;};
        externObj.nsef1();
        """;
    assertNoPureCalls(source);
  }

  /**
   * Test bug where the FunctionInformation for "A.x" and "a" were separate causing .x() calls to
   * appear pure because the global side effect was only registed for the function linked to "a".
   */
  @Test
  public void testAmbiguousDefinitionsDoubleDefinition() {
    String source =
        """
        var global = 1;
        A.x = function a() { global++; }
        B.x = function() {}
        B.x();
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testAmbiguousDefinitionsDoubleDefinition2() {
    String source =
        """
        var global = 1;
        A.x = function a() { global++; }
        var a = function() {} // This is the only `a` is in scope below.
        B.x();
        a();
        """;
    assertPureCallsMarked(source, ImmutableList.of("a"));
  }

  @Test
  public void testAmbiguousDefinitionsDoubleDefinition3() {
    String source =
        """
        var global = 1;
        A.x = function a() {}
        var a = function() { global++; } // This is the only `a` is in scope below.
        B.x();
        a();
        """;
    assertPureCallsMarked(source, ImmutableList.of("B.x"));
  }

  @Test
  public void testAmbiguousDefinitionsDoubleDefinition4() {
    String source =
        """
        var global = 1;

        A.x = function a() {}
        B.x = function() { global++; }

        B.x();
        a();
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testAmbiguousDefinitionsDoubleDefinition5() {
    String source =
        """
        var global = 1;

        A.x = cond ? function a() { global++ } : function b() {}
        B.x = function() { global++; }

        B.x();
        a(); // `a` isn't in scope here.
        b();
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testAmbiguousDefinitionsDoubleDefinition6() {
    String source =
        """
        var SetCustomData1 = function SetCustomData2(element, dataName, dataValue) {
            var x = element['_customData'];
            x[dataName] = dataValue;
        }
        SetCustomData1(window, "foo", "bar");
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testInnerFunction_isNeverPure() {
    // TODO(b/129503101): `pure` should be marked pure in this case.
    assertNoPureCalls(
        """
        function f() {
          function pure() { };
          pure();
        }
        """);
  }

  @Test
  public void testNamedFunctionExpression_isNeverPure() {
    // TODO(b/129503101): `pure` should be marked pure in this case.
    assertNoPureCalls(
        """
        (function pure() {
          pure();
        })
        """);
  }

  @Test
  public void testNameAliasPurity() {
    assertAliasPurity(true, "const alias = pure;", false);
    assertAliasPurity(true, "let alias = pure;", false);
    assertAliasPurity(false, "let alias; alias = pure;", false);
    assertAliasPurity(true, "var alias = pure;", false);
    assertAliasPurity(false, "var alias; alias = pure;", false);
  }

  @Test
  public void testPropertyAliasPurity() {
    assertAliasPurity(true, "({alias: pure});", true);
    assertAliasPurity(true, "obj.alias = pure;", true);
  }

  private void assertAliasPurity(boolean isPure, String aliasDefinition, boolean isProperty) {
    String aliasExpression = isProperty ? "obj.alias" : "alias";

    ArrayList<String> expected = new ArrayList<>();
    expected.add("pure");
    if (isPure) {
      expected.add(aliasExpression);
    }

    assertPureCallsMarked(
        """
        const /** ? */ obj = {};
        function pure() { }
        ALIAS_DEFINITION

        pure();
        ALIAS_EXPRESSION();
        """
            .replace("ALIAS_DEFINITION", aliasDefinition)
            .replace("ALIAS_EXPRESSION", aliasExpression),
        expected);
  }

  @Test
  public void testCallBeforeDefinition() {
    assertPureCallsMarked("f(); function f(){}", ImmutableList.of("f"));
    assertPureCallsMarked("var a = {}; a.f(); a.f = function (){}", ImmutableList.of("a.f"));
  }

  @Test
  public void testSideEffectsArePropagated_toPrecedingCaller_fromFollowingCallee() {
    assertPureCallsMarked(
        """
        var following = function() { }; // This implementation is pure...
        var preceding = function() { following(); }; // so this one should be initially...
        following = function() { throw 'something'; }; //  but then it becomes impure.

        preceding();
        """,
        ImmutableList.of());
  }

  @Test
  public void testConstructorThatModifiesThis1() {
    String source =
        """
        /**@constructor*/function A(){this.foo = 1}
        function f() {return new A}
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("A", "f"));
  }

  @Test
  public void testConstructorThatModifiesThis2() {
    String source =
        """
        /**@constructor*/function A(){this.foo()}
        A.prototype.foo = function(){this.data=24};
        function f() {return new A}
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("A", "f"));
  }

  @Test
  public void testOptChainCallInConstructorThatModifiesThis() {
    String source =
        """
        /**@constructor*/function A(){this?.foo()}
        A.prototype.foo = function(){this.data=24};
        function f() {return new A}
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("A", "f"));
  }

  @Test
  public void testConstructorThatModifiesThis3() {
    // test chained
    String source =
        """
        /**@constructor*/function A(){this.foo()}
        A.prototype.foo = function(){this.bar()};
        A.prototype.bar = function(){this.data=24};
        function f() {return new A}
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("A", "f"));
  }

  @Test
  public void testConstructorThatModifiesThis4() {
    String source =
        """
        /**@constructor*/function A(){foo.call(this)}
        function foo(){this.data=24};
        function f() {return new A}
        f()
        """;
    assertPureCallsMarked(source, ImmutableList.of("A", "f"));
  }

  @Test
  public void testConstructorThatModifiesGlobal1() {
    String source =
        """
        var b = 0;
        /**@constructor*/function A(){b=1};
        function f() {return new A}
        f()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testConstructorThatModifiesGlobal2() {
    String source =
        """
        /**@constructor*/function A(){this.foo()}
        A.prototype.foo = function(){b=1};
        function f() {return new A}
        f()
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testCallFunctionThatModifiesThis() {
    String source =
        """
        /**@constructor*/function A(){}
        A.prototype.foo = function(){this.data=24};
        function f(){var a = new A; return a}
        function g(){var a = new A; a.foo(); return a}
        f(); g()
        """;
    assertPureCallsMarked(source, ImmutableList.of("A", "A", "f"));
  }

  @Test
  public void testMutatesArguments1() {
    assertPureCallsMarked(
        """
        function f(x) { x.y = 1; } // preserve newline
        f({});
        """,
        ImmutableList.of("f"));

    assertPureCallsMarked(
        """
        function f(x = {}) { x.y = 1; } // preserve newline
        f({});
        """,
        ImmutableList.of("f"));
  }

  @Test
  public void testMutatesArguments_notTrackedThroughDestructuring() {
    assertNoPureCalls(
        """
        const obj = {}; // preserve newline
        function f([x]) { x.y = 1; }
        f({obj});
        """);

    // This is technically pure, but right now we don't track that the array being destructured was
    // a literal containing literals.
    assertNoPureCalls(
        """
        function f([x]) { x.y = 1; } // preserve newline
        f([{}]);
        """);
  }

  @Test
  public void testMutatesArguments2() {
    String source =
        """
        function f(x) { x.y = 1; }
        f(window);
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testMutatesArguments3() {
    // We could do better here with better side-effect propagation.
    String source =
        """
        function f(x) { x.y = 1; }
        function g(x) { f(x); }
        g({});
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testMutatesArguments4() {
    assertPureCallsMarked(
        """
        function f(x) { x.y = 1; } // preserve newline
        function g(x) { f({}); x.y = 1; }
        g({});
        """,
        ImmutableList.of("f", "g"));
  }

  @Test
  public void testMutatesArguments5() {
    String source =
        """
        function f(x) {
          function g() {
            x.prop = 5;
          }
          g();
        }
        f(window);
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testMutatesArgumentsArray1() {
    String source =
        """
        function f(x) { arguments[0] = 1; }
        f({});
        """;
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  @Test
  public void testMutatesArgumentsArray2() {
    // We could be smarter here.
    String source =
        """
        function f(x) { arguments[0].y = 1; }
        f({});
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testMutatesArgumentsArray3() {
    String source =
        """
        function f(x) { arguments[0].y = 1; }
        f(x);
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testCallGenerator1() {
    String source =
        """
        var x = 0;
        function* f() {
          x = 2
          while (true) {
            yield x;
          }
        }
        var g = f();
        """;
    assertNoPureCalls(source);
    Node lastRoot = getLastCompiler().getRoot().getLastChild();
    Node call = findQualifiedNameNode("f", lastRoot).getParent();
    assertThat(call.isNoSideEffectsCall()).isFalse();
    assertThat(call.getSideEffectFlags()).isEqualTo(Node.SideEffectFlags.ALL_SIDE_EFFECTS);
  }

  @Test
  public void testCallGenerator2() {
    String source =
        """
        function* f() {
          while (true) {
            yield 1;
          }
        }
        var g = f();
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testCallFunctionFOrG() {
    String source =
        """
        function f(){}
        function g(){}
        function h(){ (f || g)() }
        h()
        """;
    assertPureCallsMarked(source, ImmutableList.of("f || g", "h"));
  }

  @Test
  public void testCallFunctionFOrGViaHook() {
    String source =
        """
        function f(){}
        function g(){}
        function h(){ (false ? f : g)() }
        h()
        """;
    assertPureCallsMarked(source, ImmutableList.of("false ? f : g", "h"));
  }

  @Test
  public void testCallFunctionForGorH() {
    String source =
        """
        function f(){}
        function g(){}
        function h(){}
        function i(){ (false ? f : (g || h))() }
        i()
        """;
    assertPureCallsMarked(source, ImmutableList.of("false ? f : (g || h)", "i"));
  }

  @Test
  public void testCallFunctionForGWithSideEffects() {
    String source =
        """
        var x = 0;
        function f(){x = 10}
        function g(){}
        function h(){ (f || g)() }
        function i(){ (g || f)() }
        function j(){ (f || f)() }
        function k(){ (g || g)() }
        h(); i(); j(); k()
        """;
    assertPureCallsMarked(source, ImmutableList.of("g || g", "k"));
  }

  @Test
  public void testCallFunctionFOrGViaHookWithSideEffects() {
    String source =
        """
        var x = 0;
        function f(){x = 10}
        function g(){}
        function h(){ (false ? f : g)() }
        function i(){ (false ? g : f)() }
        function j(){ (false ? f : f)() }
        function k(){ (false ? g : g)() }
        h(); i(); j(); k()
        """;

    assertPureCallsMarked(source, ImmutableList.of("false ? g : g", "k"));
  }

  @Test
  public void testCallRegExpWithSideEffects() {
    String source =
        """
        var x = 0;
        function k(){(/a/).exec('')}
        k()
        """;

    regExpHaveSideEffects = true;
    assertNoPureCalls(source);
    regExpHaveSideEffects = false;
    assertPureCallsMarked(source, ImmutableList.of("/a/.exec", "k"));
  }

  @Test
  public void testAnonymousFunction1() {
    assertPureCallsMarked("(function (){})();", ImmutableList.of("function(){}"));
  }

  @Test
  public void testAnonymousFunction2() {
    String source = "(Error || function (){})();";

    assertPureCallsMarked(source, ImmutableList.of("(Error || function(){})"));
  }

  @Test
  public void testAnonymousFunction3() {
    String source = "var a = (Error || function (){})();";

    assertPureCallsMarked(source, ImmutableList.of("(Error || function(){})"));
  }

  // Indirect complex function definitions aren't yet supported.
  @Test
  public void testAnonymousFunction4() {
    String source =
        """
        var a = (Error || function (){});
        a();
        """;

    assertPureCallsMarked(source, ImmutableList.of("a"));
  }

  @Test
  public void testClassMethod1() {
    String source = "class C { m() { alert(1); } }; (new C).m();";
    assertPureCallsMarked(source, ImmutableList.of("C"));
  }

  @Test
  public void testClassMethod2() {
    String source = "class C { m() { } }; (new C).m();";
    assertPureCallsMarked(source, ImmutableList.of("C", "new C().m"));
  }

  @Test
  public void testClassMethod3() {
    String source = "class C { m1() { } m2() { this.m1(); }}; (new C).m2();";
    assertPureCallsMarked(source, ImmutableList.of("C", "this.m1", "new C().m2"));
  }

  @Test
  public void testClassInstantiation_implicitCtor_noSuperclass_isPure() {
    assertPureCallsMarked(
        """
        class C { }

        new C()
        """,
        ImmutableList.of("C"));
  }

  @Test
  public void testClassInstantiation_literal_implicitCtor_noSuperclass_isPure() {
    assertPureCallsMarked("new class C {}", ImmutableList.of("class C {}"));
  }

  @Test
  public void testClassInstantiation_pureCtor_noSuperclass_isPure() {
    assertPureCallsMarked(
        """
        class C {
          constructor() { }
        }

        new C()
        """,
        ImmutableList.of("C"));
  }

  @Test
  public void testClassInstantiation_literal_pureCtor_noSuperclass_isPure() {
    assertPureCallsMarked(
        "new class C { constructor() {} }", ImmutableList.of("class C { constructor() {} }"));
  }

  @Test
  public void testClassInstantiation_impureCtor_noSuperclass_isImpure() {
    assertNoPureCalls(
        """
        class C {
          constructor() { throw 0; }
        }

        new C()
        """);
  }

  @Test
  public void testClassInstantiation_implictCtor_pureSuperclassCtor_isPure() {
    assertPureCallsMarked(
        """
        class A { } // Implict ctor is pure.
        class C extends A { }

        new C()
        """,
        ImmutableList.of("C"));
  }

  @Test
  public void testClassInstantiation_literal_implictCtor_pureSuperclassCtor_isPure() {
    assertPureCallsMarked(
        """
        class A { } // Implicit ctor is pure.
        new class C extends A { }
        """,
        ImmutableList.of("class C extends A { }"));
  }

  @Test
  public void testClassInstantiation_implictCtor_impureSuperclassCtor_isImpure() {
    assertNoPureCalls(
        """
        class A {
          constructor() { throw 0; }
        }
        class C extends A { }

        new C()
        """);
  }

  @Test
  public void testClassInstantiation_literal_implictCtor_impureSuperclassCtor_isImpure() {
    assertNoPureCalls(
        """
        class A {
          constructor() { throw 0; }
        }
        new class C extends A { }
        """);
  }

  @Test
  public void testClassInstantiation_explitCtor_pureSuperclassCtor_isPure() {
    assertPureCallsMarked(
        """
        class A { }
        class C extends A {
          constructor() {
        // This should be marked as "mutates this" despite being a call to a pure function.
        // `super()` binds `this`, and more importantly is syntactically required. Marking it
        // this way prevents removal.
            super();
          }
        }

        new C()
        """,
        ImmutableList.of("C"));
  }

  @Test
  public void testClassInstantiation_explitCtor_impureSuperclassCtor_isImpure() {
    assertNoPureCalls(
        """
        class A {
          constructor() { throw 0; }
        }
        class C extends A {
          constructor() { super(); }
        }

        new C()
        """);
  }

  @Test
  public void testClassInstantiation_mutatesThis_traversesSuper() {
    assertPureCallsMarked(
        """
        class A {
          constructor() { this.foo = 9; }
        }
        class C extends A {
          constructor() { super(); }
        }

        new C()
        """,
        ImmutableList.of("C"));
  }

  @Test
  public void testMutatesThis_traversesThisAsReceiver() {
    assertPureCallsMarked(
        """
        class C {
          constructor() {
            /** @type {number} */
            this.foo;

            this.mutateThis();
          }

          mutateThis() { this.foo = 9; }
        }

        // Instantiation swallows "mutates this" side-effects.
        var x = new C();
        // But in general it is still a side-effect.
        x.mutateThis()
        """,
        ImmutableList.of("C"));
  }

  @Test
  public void testMutatesThis_traversesSuperAsReceiver() {
    assertPureCallsMarked(
        """
        class C {
          constructor() {
            /** @type {number} */
            this.foo;

        // Ignore the fact that there's no superclass.
            super.mutateThis();
          }

          mutateThis() { this.foo = 9; }
        }

        // Instantiation swallows "mutates this" side-effects.
        var x = new C();
        // But in general it is still a side-effect.
        x.mutateThis()
        """,
        ImmutableList.of("C"));
  }

  @Test
  public void testMutatesThis_expandsToMutatesGlobalScope_traversingArbitraryReceiver() {
    assertNoPureCalls(
        """
        class C {
          constructor() {
            /** @type {number} */
            this.foo;

            somethingElse.mutateThis();
          }

          mutateThis() {
            this.foo = 9;
          }
        }

        // Instantiation swallows "mutates this" side-effects.
        var x = new C();
        // But in general it is still a side-effect.
        x.mutateThis()
        """);
  }

  @Test
  public void testGlobalScopeTaintedByWayOfThisPropertyAndForOfLoop() {
    String source =
        """
        class C {
          constructor(elements) {
            this.elements = elements;
            this.m1();
          }
          m1() {
            for (const element of this.elements) {
              element.someProp = 1;
            }
          }
        }
        new C([]).m1()
        """;
    assertPureCallsMarked(source, ImmutableList.of());
  }

  @Test
  public void testGlobalScopeTaintedByWayOfThisPropertyAndForOfLoopWithDestructuring() {
    String source =
        """
        class C {
          constructor(elements) {
            this.elements = elements;
            this.m1();
          }
          m1() {
            this.elements[0].someProp = 1;
            for (const {prop} of this.elements) {
              prop.someProp = 1;
            }
          }
        }
        new C([{prop: {}}]).m1()
        """;
    assertPureCallsMarked(source, ImmutableList.of());
  }

  @Test
  public void testArgumentTaintedByWayOfFunctionScopedLet() {
    String source =
        """
        function m1(elements) {
          let e;
          for (let i = 0; i < elements.length; ++i) {
            e = elements[i];
            e.someProp = 1;
          }
        }
        m1([]);
        """;
    assertPureCallsMarked(source, ImmutableList.of());
  }

  @Test
  public void testArgumentTaintedByWayOfFunctionScopedLetAssignedInForOf() {
    String source =
        """
        function m1(elements) {
          let e;
          for (e of elements) {
            e.someProp = 1;
          }
        }
        m1([]);
        """;
    assertPureCallsMarked(source, ImmutableList.of());
  }

  @Test
  public void testArgumentTaintedByWayOfFunctionScopedLetAssignedInForOfWithDestructuring() {
    String source =
        """
        function m1(elements) {
          let e;
          for ({e} of elements) {
            e.someProp = 1;
          }
        }
        m1([{e: {}}]);
        """;
    assertPureCallsMarked(source, ImmutableList.of());
  }

  @Test
  public void testArgumentTaintedByWayOfForOfAssignmentToQualifiedName() {
    String source =
        """
        function m1(obj, elements) {
          for (obj.e of elements) {
            if (obj.e != null) break;
          }
        }
        var globalObj = {};
        m1(globalObj, []);
        """;
    assertPureCallsMarked(source, ImmutableList.of());
  }

  @Test
  public void testArgumentTaintedByWayOfForInAssignmentToQualifiedName() {
    String source =
        """
        /**
         *@param {!Object} obj
         *@param {!Object} propObj
         *@returns {number}
         */
        function m1(/** ? */ obj, /** Object */ propObj) {
          var propCharCount = 0;
          obj.e = '';
          for (obj.e in propObj) {
            propCharCount += obj.e.length;
          }
          return propCharCount;
        }
        var globalObj = {};
        m1(globalObj, {x: 1});
        """;
    assertPureCallsMarked(source, ImmutableList.of());
  }

  @Test
  public void testArgumentTaintedByWayOfBlockScopedLet() {
    String source =
        """
        function m1(elements) {
          for (let i = 0; i < elements.length; ++i) {
            let e;
            e = elements[i];
            e.someProp = 1;
          }
        }
        m1([]);
        """;
    assertPureCallsMarked(source, ImmutableList.of());
  }

  @Test
  public void testArgumentTaintedByWayOfBlockScopedConst() {
    String source =
        """
        function m1(elements) {
          for (let i = 0; i < elements.length; ++i) {
            const e = elements[i];
            e.someProp = 1;
          }
        }
        m1([]);
        """;
    assertPureCallsMarked(source, ImmutableList.of());
  }

  @Test
  public void testArgumentTaintedByWayOfForOfScopedConst() {
    String source =
        """
        function m1(elements) {
          for (const e of elements) {
            e.someProp = 1;
          }
        }
        m1([]);
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testArgumentTaintedByWayOfForOfScopedConstWithDestructuring() {
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    String source =
        """
        function m1(elements) {
          for (const {e} of elements) {
            e.someProp = 1;
          }
        }
        m1([]);
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testArgumentTaintedByWayOfNameDeclaration() {
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    String source =
        """
        function m1(obj) {
          let p = obj.p;
          p.someProp = 1;
        }
        m1([]);
        """;
    assertPureCallsMarked(source, ImmutableList.of());
  }

  @Test
  public void testUnusedDestructuringNameDeclarationIsPure() {
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    String source =
        """
        function m1(obj) {
          let {p} = obj;
        }
        m1([]);
        """;
    assertPureCallsMarked(source, ImmutableList.of("m1"));
  }

  @Test
  public void testArgumentTaintedByWayOfDestructuringNameDeclaration() {
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    String source =
        """
        function m1(obj) {
          let {p} = obj;
          p.someProp = 1;
        }
        m1([]);
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testArgumentTaintedByWayOfNestedDestructuringNameDeclaration() {
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    String source =
        """
        function m1(obj) {
          let {q: {p}} = obj;
          p.someProp = 1;
        }
        m1({});
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testArgumentTaintedByWayOfDestructuringAssignment() {
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    String source =
        """
        function m1(obj) {
          let p;
          ({p} = obj);
          p.someProp = 1;
        }
        m1([]);
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testArgumentTaintedByWayOfArrayDestructuringAssignment() {
    String source =
        """
        function m1(obj) {
          let p;
          ([p] = obj);
          p.someProp = 1;
        }
        m1([]);
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testDestructuringAssignMutatingGlobalState() {
    String source =
        """
        var a = 0;
        function m1() {
          ([a] = []);
        }
        m1();
        """;
    assertNoPureCalls(source);

    source =
        """
        var a = 0;
        function m1() {
          ({a} = {a: 0});
        }
        m1();
        """;
    assertNoPureCalls(source);
  }

  @Test
  public void testDefaultValueInitializers_areConsidered_whenAnalyzingDestructuring() {
    assertNoPureCalls(
        """
        function impure() { throw 'something'; }

        function foo() { const {a = impure()} = {a: undefined}; }
        foo();
        """);

    assertPureCallsMarked(
        """
        function foo() { const {a = 0} = {a: undefined}; }
        foo();
        """,
        ImmutableList.of("foo"));

    assertNoPureCalls(
        """
        function impure() { throw 'something'; }

        function foo() { const [{a = impure()}] = [{a: undefined}]; }
        foo();
        """);
  }

  @Test
  public void testDefaultValueInitializers_areConsidered_whenAnalyzingFunctions() {
    assertNoPureCalls(
        """
        function impure() { throw 'something'; }

        function foo(a = impure()) { }
        foo();
        """);

    assertPureCallsMarked(
        """
        function pure() { }

        function foo(a = pure()) { }
        foo();
        """,
        ImmutableList.of("pure", "foo"));

    assertPureCallsMarked(
        """
        function foo(a = 0) { }
        foo();
        """,
        ImmutableList.of("foo"));
  }

  @Test
  public void testFunctionProperties1() {
    String source =
        """
        /** @constructor */
        function F() { this.bar; }
        function g() {
          this.bar = function() { alert(3); };
        }
        var x = new F();
        g.call(x);
        x.bar();
        """;
    assertPureCallsMarked(
        source,
        ImmutableList.of("F"),
        compiler -> {
          Node lastRoot = compiler.getRoot();
          Node call = findQualifiedNameNode("g.call", lastRoot).getParent();
          assertThat(call.getSideEffectFlags())
              .isEqualTo(
                  new Node.SideEffectFlags().clearAllFlags().setMutatesArguments().valueOf());
        });
  }

  @Test
  public void testAwait_makesFunctionImpure() {
    assertNoPureCalls(
        """
        async function foo() { await 0; }
        foo();
        """);
  }

  @Test
  public void testTaggedTemplatelit_propagatesCalleeSideEffects() {
    assertPureCallsMarked(
        """
        function tag(a) { throw 'something'; }

        function foo() { tag`Hello World!`; }
        foo();
        """,
        ImmutableList.of(),
        compiler -> {
          Node lastRoot = compiler.getRoot();
          Node tagDef = findQualifiedNameNode("tag", lastRoot).getParent();
          Node fooDef = findQualifiedNameNode("foo", lastRoot).getParent();
          assertThat(fooDef.getSideEffectFlags()).isEqualTo(tagDef.getSideEffectFlags());
        });
  }

  @Test
  public void testIterableIteration_forOf_makesFunctionImpure() {
    // TODO(b/127862986): We could consider iteration over a known Array value to be pure.
    assertNoPureCalls(
        """
        function foo(a) { for (const t of a) { } }
        foo(x);
        """);
  }

  @Test
  public void testIterableIteration_forAwaitOf_makesFunctionImpure() {
    // TODO(b/127862986): We could consider iteration over a known Array value to be pure.
    assertNoPureCalls(
        """
        async function foo(a) { for await (const t of a) { } }
        foo(x);
        """);
  }

  @Test
  public void testIterableIteration_arraySpread_makesFunctionImpure() {
    // TODO(b/127862986): We could consider iteration over a known Array value to be pure.
    assertNoPureCalls(
        """
        function foo(a) { [...a]; }
        foo(x);
        """);
  }

  @Test
  public void testIterableIteration_callSpread_makesFunctionImpure() {
    // TODO(b/127862986): We could consider iteration over a known Array value to be pure.
    assertPureCallsMarked(
        """
        function pure(...rest) { }

        function foo(a) { pure(...a); }
        foo(x);
        """,
        ImmutableList.of("pure"));
  }

  @Test
  public void testIterableIteration_newSpread_makesFunctionImpure() {
    // TODO(b/127862986): We could consider iteration over a known Array value to be pure.
    assertPureCallsMarked(
        """
        /** @constructor */
        function pure(...rest) { }

        function foo(a) { new pure(...a); }
        foo(x);
        """,
        ImmutableList.of("pure"));
  }

  @Test
  public void testIterableIteration_paramListRest_leavesFunctionPure() {
    assertPureCallsMarked(
        """
        function foo(...a) { }
        foo();
        """,
        ImmutableList.of("foo"));
  }

  @Test
  public void testIterableIteration_arrayPatternRest_makesFunctionImpure() {
    // TODO(b/127862986): We could consider iteration over a known Array value to be pure.
    assertNoPureCalls(
        """
        function foo(a) { const [...x] = a; }
        foo(v);
        """);
  }

  @Test
  public void testIterableIteration_yieldStar_makesFunctionImpure() {
    // TODO(b/127862986): We could consider iteration over a known Array value to be pure.
    assertNoPureCalls(
        """
        function* foo(a) { yield* a; }
        foo(x);
        """);
  }

  @Test
  public void testObjectSpread_hasSideEffects() {
    // Object-spread may trigger a getter.
    assertNoPureCalls(
        """
        function foo(a) { ({...a}); }
        foo(x);
        """);
  }

  @Test
  public void testObjectRest_hasSideEffects() {
    // Object-rest may trigger a getter.
    assertNoPureCalls(
        """
        function foo(a, b) { ({...b} = a); }
        foo(x, y);
        """);
    assertNoPureCalls(
        """
        function foo({...b}) { }
        foo(x);
        """);
  }

  @Test
  public void testGetterAccess_hasSideEffects() {
    assertNoPureCalls(
        """
        class Foo { get getter() { } }

        function foo(a) { a.getter; }
        foo(x);
        """);
    assertNoPureCalls(
        """
        class Foo { get getter() { } }

        function foo(a) { const {getter} = a; }
        foo(x);
        """);
  }

  @Test
  public void testSetterAccess_hasSideEffects() {
    assertNoPureCalls(
        """
        class Foo { set setter(x) { } }

        function foo(a) { a.setter = 0; }
        foo(x);
        """);
  }

  @Test
  public void testForAwaitOf_makesFunctionImpure() {
    assertNoPureCalls(
        """
        async function foo() { for await (const t of []) { } }
        foo();
        """);
  }

  @Test
  public void testExistenceOfAGetter_makesPropertyNameImpure() {
    declareAccessor("foo", PropertyAccessKind.GETTER_ONLY);

    // Imagine the getter returned a function.
    assertNoPureCalls(
        """
        class Foo {
          foo() {}
        }
        x.foo();
        """);
  }

  @Test
  public void testExistenceOfASetter_makesPropertyNameImpure() {
    declareAccessor("foo", PropertyAccessKind.SETTER_ONLY);

    // This doesn't actually seem like a risk but it's hard to say, and other optimizations that use
    // optimize calls would be dangerous on setters.
    assertNoPureCalls(
        """
        class Foo {
          foo() {}
        }
        x.foo();
        """);
  }

  @Test
  public void testCallCache() {
    String source =
        """
        var valueFn = function() {};
        goog.reflect.cache(externObj, "foo", valueFn)
        """;
    assertPureCallsMarked(
        source,
        ImmutableList.of("goog.reflect.cache"),
        compiler -> {
          Node lastRoot = compiler.getRoot().getLastChild();
          Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
          assertThat(call.isNoSideEffectsCall()).isTrue();
          assertThat(call.mayMutateGlobalStateOrThrow()).isFalse();
        });
  }

  @Test
  public void testCallCache_withKeyFn() {
    String source =
        """
        var valueFn = function(v) { return v };
        var keyFn = function(v) { return v };
        goog.reflect.cache(externObj, "foo", valueFn, keyFn)
        """;
    assertPureCallsMarked(
        source,
        ImmutableList.of("goog.reflect.cache"),
        compiler -> {
          Node lastRoot = compiler.getRoot().getLastChild();
          Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
          assertThat(call.isNoSideEffectsCall()).isTrue();
          assertThat(call.mayMutateGlobalStateOrThrow()).isFalse();
        });
  }

  @Test
  public void testCallCache_anonymousFn() {
    String source = "goog.reflect.cache(externObj, \"foo\", function(v) { return v })";
    assertPureCallsMarked(
        source,
        ImmutableList.of("goog.reflect.cache"),
        compiler -> {
          Node lastRoot = compiler.getRoot().getLastChild();
          Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
          assertThat(call.isNoSideEffectsCall()).isTrue();
          assertThat(call.mayMutateGlobalStateOrThrow()).isFalse();
        });
  }

  @Test
  public void testCallCache_anonymousFn_hasSideEffects() {
    String source =
        """
        var x = 0;
        goog.reflect.cache(externObj, "foo", function(v) { return (x+=1) })
        """;
    assertNoPureCalls(
        source,
        compiler -> {
          Node lastRoot = compiler.getRoot().getLastChild();
          Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
          assertThat(call.isNoSideEffectsCall()).isFalse();
          assertThat(call.mayMutateGlobalStateOrThrow()).isTrue();
        });
  }

  @Test
  public void testCallCache_hasSideEffects() {
    String source =
        """
        var x = 0;
        var valueFn = function() { return (x+=1); };
        goog.reflect.cache(externObj, "foo", valueFn)
        """;
    assertNoPureCalls(
        source,
        compiler -> {
          Node lastRoot = compiler.getRoot().getLastChild();
          Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
          assertThat(call.isNoSideEffectsCall()).isFalse();
          assertThat(call.mayMutateGlobalStateOrThrow()).isTrue();
        });
  }

  @Test
  public void testCallCache_withKeyFn_hasSideEffects() {
    String source =
        """
        var x = 0;
        var keyFn = function(v) { return (x+=1) };
        var valueFn = function(v) { return v };
        goog.reflect.cache(externObj, "foo", valueFn, keyFn)
        """;
    assertNoPureCalls(
        source,
        compiler -> {
          Node lastRoot = compiler.getRoot().getLastChild();
          Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
          assertThat(call.isNoSideEffectsCall()).isFalse();
          assertThat(call.mayMutateGlobalStateOrThrow()).isTrue();
        });
  }

  @Test
  public void testCallCache_propagatesSideEffects() {
    String source =
        """
        var valueFn = function(x) { return x * 2; };
        var helper = function(x) { return goog.reflect.cache(externObj, x, valueFn); };
        helper(10);
        """;
    assertPureCallsMarked(
        source,
        ImmutableList.of("goog.reflect.cache", "helper"),
        compiler -> {
          Node lastRoot = compiler.getRoot().getLastChild();
          Node cacheCall = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
          assertThat(cacheCall.isNoSideEffectsCall()).isTrue();
          assertThat(cacheCall.mayMutateGlobalStateOrThrow()).isFalse();

          Node helperCall =
              Iterables.getLast(findQualifiedNameNodes("helper", lastRoot)).getParent();
          assertThat(helperCall.isNoSideEffectsCall()).isTrue();
          assertThat(helperCall.mayMutateGlobalStateOrThrow()).isFalse();
        });
  }

  @Test
  public void testDynamicImport() {
    ignoreWarnings(DiagnosticGroups.MODULE_LOAD);
    assertNoPureCalls("import('./module.js')");
    assertNoPureCalls("function doImport() { import('./module.js'); } doImport();");
  }

  @Test
  public void testParenthesizedExpression() {
    assertPureCallsMarked(
        """
        const namespace = {};
        namespace.noSideEffects = function(x) { return 1; };
        // NOTE: `IRFactory` will unwrap `(0, callee)(42)`, so we need to use a non-number
        // to preserve the parentheses for the test.
        ('', namespace.noSideEffects)(42);
        """,
        ImmutableList.of("('', namespace.noSideEffects)"));
  }

  void assertCallableExpressionPure(boolean purity, String expression) {
    expression = "(" + expression + ")";
    String directInvocation = expression + "()";
    String aliasInvocation = "var $testFn = " + expression + "; $testFn()";

    if (purity) {
      assertPureCallsMarked(directInvocation, ImmutableList.of(expression));
      assertPureCallsMarked(aliasInvocation, ImmutableList.of("$testFn"));
    } else {
      assertNoPureCalls(directInvocation);
      assertNoPureCalls(aliasInvocation);
    }
  }

  void assertNoPureCalls(String source) {
    assertPureCallsMarked(source, ImmutableList.of(), null);
  }

  void assertNoPureCalls(String source, Postcondition post) {
    assertPureCallsMarked(source, ImmutableList.of(), post);
  }

  void assertPureCallsMarked(String source, List<String> expected) {
    assertPureCallsMarked(source, expected, null);
  }

  void assertPureCallsMarked(
      String source, final List<String> expected, final @Nullable Postcondition post) {
    testSame(
        srcs(source),
        postcondition(
            compiler -> {
              assertThat(noSideEffectCalls)
                  .comparingElementsUsing(JSCompCorrespondences.EQUALITY_WHEN_PARSED_AS_EXPRESSION)
                  .containsExactlyElementsIn(expected);
              if (post != null) {
                post.verify(compiler);
              }
            }));
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new NoSideEffectCallEnumerator(compiler);
  }
}
