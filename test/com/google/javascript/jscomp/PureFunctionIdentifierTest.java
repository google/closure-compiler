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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link PureFunctionIdentifier}
 *
 * @author johnlenz@google.com (John Lenz)
 */

public final class PureFunctionIdentifierTest extends TypeICompilerTestCase {
  List<String> noSideEffectCalls;
  List<String> localResultCalls;

  boolean regExpHaveSideEffects = true;

  private static final String TEST_EXTERNS =
      CompilerTypeTestCase.DEFAULT_EXTERNS + lines(
          "var window; window.setTimeout;",
          "/**@nosideeffects*/ function externSENone(){}",

          "/**@modifies{this}*/ function externSEThis(){}",

          "/**@constructor",
          " * @modifies{this}*/",
          "function externObjSEThis(){}",

          "/**",
          " * @param {string} s id.",
          " * @return {string}",
          " * @modifies{this}",
          " */",
          "externObjSEThis.prototype.externObjSEThisMethod = function(s) {};",

          "/**",
          " * @param {string} s id.",
          " * @return {string}",
          " * @modifies{arguments}",
          " */",
          "externObjSEThis.prototype.externObjSEThisMethod2 = function(s) {};",

          "/**@nosideeffects*/function Error(){}",

          "function externSef1(){}",

          "/**@nosideeffects*/function externNsef1(){}",

          "var externSef2 = function(){};",

          "/**@nosideeffects*/var externNsef2 = function(){};",

          "var externNsef3 = /**@nosideeffects*/function(){};",

          "var externObj;",

          "externObj.sef1 = function(){};",

          "/**@nosideeffects*/externObj.nsef1 = function(){};",

          "externObj.nsef2 = /**@nosideeffects*/function(){};",

          "externObj.partialFn;",

          "externObj.partialSharedFn;",

          "var externObj2;",

          "externObj2.partialSharedFn = /**@nosideeffects*/function(){};",

          "/**@constructor*/function externSefConstructor(){}",

          "externSefConstructor.prototype.sefFnOfSefObj = function(){};",

          "externSefConstructor.prototype.nsefFnOfSefObj = ",
          "  /**@nosideeffects*/function(){};",

          "externSefConstructor.prototype.externShared = function(){};",

          "/**@constructor@nosideeffects*/function externNsefConstructor(){}",

          "externNsefConstructor.prototype.sefFnOfNsefObj = function(){};",

          "externNsefConstructor.prototype.nsefFnOfNsefObj = ",
          "  /**@nosideeffects*/function(){};",

          "externNsefConstructor.prototype.externShared = ",
          "  /**@nosideeffects*/function(){};",

          "/**@constructor @nosideeffects*/function externNsefConstructor2(){}",
          "externNsefConstructor2.prototype.externShared = ",
          "  /**@nosideeffects*/function(){};",

          "externNsefConstructor.prototype.sharedPartialSef;",
          "/**@nosideeffects*/externNsefConstructor.prototype.sharedPartialNsef;",

          // An externs definition with a stub before.
          "/**@constructor*/function externObj3(){}",

          "externObj3.prototype.propWithStubBefore;",

          "/**",
          " * @param {string} s id.",
          " * @return {string}",
          " * @nosideeffects",
          " */",
          "externObj3.prototype.propWithStubBefore = function(s) {};",

          // useless JsDoc
          "/**",
          " * @see {foo}",
          " */",
          "externObj3.prototype.propWithStubBeforeWithJSDoc;",

          "/**",
          " * @param {string} s id.",
          " * @return {string}",
          " * @nosideeffects",
          " */",
          "externObj3.prototype.propWithStubBeforeWithJSDoc = function(s) {};",

          // An externs definition with a stub after.
          "/**@constructor*/function externObj4(){}",

          "/**",
          " * @param {string} s id.",
          " * @return {string}",
          " * @nosideeffects",
          " */",
          "externObj4.prototype.propWithStubAfter = function(s) {};",

          "externObj4.prototype.propWithStubAfter;",

          "/**",
          " * @param {string} s id.",
          " * @return {string}",
          " * @nosideeffects",
          " */",
          "externObj4.prototype.propWithStubAfterWithJSDoc = function(s) {};",

          // useless JsDoc
          "/**",
          " * @see {foo}",
          " */",
          "externObj4.prototype.propWithStubAfterWithJSDoc;",
          "var goog = {};",
          "goog.reflect = {};",
          "goog.reflect.cache = function(a, b, c, opt_d) {};",


          "/** @nosideeffects */",
          "externObj.prototype.duplicateExternFunc = function() {};",
          "externObj2.prototype.duplicateExternFunc = function() {};",

          "externObj.prototype['weirdDefinition'] = function() {};"
          );

  public PureFunctionIdentifierTest() {
    super(TEST_EXTERNS);
  }

  @Override
  protected int getNumRepetitions() {
    // run pass once.
    return 1;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ignoreWarnings(NewTypeInference.GLOBAL_THIS, NewTypeInference.INEXISTENT_PROPERTY);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    regExpHaveSideEffects = true;
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
      localResultCalls = new ArrayList<>();
      compiler.setHasRegExpGlobalReferences(regExpHaveSideEffects);
      compiler.getOptions().setUseTypesForLocalOptimization(true);
      NameBasedDefinitionProvider defFinder = new NameBasedDefinitionProvider(compiler, true);
      defFinder.process(externs, root);

      PureFunctionIdentifier pureFunctionIdentifier =
          new PureFunctionIdentifier(compiler, defFinder);
      pureFunctionIdentifier.process(externs, root);

      // Ensure that debug report computation doesn't crash.
      pureFunctionIdentifier.getDebugReport();

      NodeTraversal.traverseEs6(compiler, externs, this);
      NodeTraversal.traverseEs6(compiler, root, this);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isNew()) {
        if (!NodeUtil.constructorCallHasSideEffects(n)) {
          noSideEffectCalls.add(generateNameString(n.getFirstChild()));
        }
      } else if (n.isCall()) {
        if (!NodeUtil.functionCallHasSideEffects(n, compiler)) {
          noSideEffectCalls.add(generateNameString(n.getFirstChild()));
        }
        if (NodeUtil.callHasLocalResult(n)) {
          localResultCalls.add(generateNameString(n.getFirstChild()));
        }
      }
    }

    private String generateNameString(Node node) {
      if (node.isOr()) {
        return "(" + generateNameString(node.getFirstChild())
            + " || " + generateNameString(node.getLastChild()) + ")";
      } else if (node.isHook()) {
        return "(" + generateNameString(node.getSecondChild())
            + " : " + generateNameString(node.getLastChild()) + ")";
      } else {
        String result = node.getQualifiedName();
        if (result == null) {
          if (node.isFunction()) {
            result = node.toString(false, false, false).trim();
          } else {
            result = node.getFirstChild().toString(false, false, false);
            result += " " + node.getLastChild().toString(false, false, false);
          }
        }
        return result;
      }
    }
  }

  public void testIssue303() throws Exception {
    String source = lines(
        "/** @constructor */ function F() {",
        "  var self = this;",
        "  window.setTimeout(function() {",
        "    window.location = self.location;",
        "  }, 0);",
        "}",
        "F.prototype.setLocation = function(x) {",
        "  this.location = x;",
        "};",
        "(new F()).setLocation('http://www.google.com/');"
    );
    assertNoPureCalls(source);
  }

  public void testIssue303b() throws Exception {
    String source = lines(
        "/** @constructor */ function F() {",
        "  var self = this;",
        "  window.setTimeout(function() {",
        "    window.location = self.location;",
        "  }, 0);",
        "}",
        "F.prototype.setLocation = function(x) {",
        "  this.location = x;",
        "};",
        "function x() {",
        "  (new F()).setLocation('http://www.google.com/');",
        "} window['x'] = x;"
    );
    assertNoPureCalls(source);
  }

  public void testAnnotationInExterns_new1() throws Exception {
    assertPureCallsMarked("externSENone()", ImmutableList.of("externSENone"));
  }

  public void testAnnotationInExterns_new2() throws Exception {
    assertNoPureCalls("externSEThis()");
  }

  public void testAnnotationInExterns_new3() throws Exception {
    assertPureCallsMarked("new externObjSEThis()", ImmutableList.of("externObjSEThis"));
  }

  public void testAnnotationInExterns_new4() throws Exception {
    // The entire expression containing "externObjSEThisMethod" is considered
    // side-effect free in this context.

    assertPureCallsMarked("new externObjSEThis().externObjSEThisMethod('')",
        ImmutableList.of("externObjSEThis", "NEW STRING externObjSEThisMethod"));
  }

  public void testAnnotationInExterns_new5() throws Exception {
    assertPureCallsMarked(
        "function f() { new externObjSEThis() }; f();", ImmutableList.of("externObjSEThis", "f"));
  }

  public void testAnnotationInExterns_new6() throws Exception {
    // While "externObjSEThisMethod" has modifies "this"
    // it does not have global side-effects with "this" is
    // a known local value.
    // TODO(johnlenz): "f" is side-effect free but we need
    // to propagate that "externObjSEThisMethod" is modifying
    // a local object.
    String source = lines(
        "function f() {",
        "  new externObjSEThis().externObjSEThisMethod('') ",
        "};",
        "f();"
    );
    assertPureCallsMarked(
        source,
        ImmutableList.of("externObjSEThis", "NEW STRING externObjSEThisMethod"));
  }

  public void testAnnotationInExterns_new7() throws Exception {
    // While "externObjSEThisMethod" has modifies "this"
    // it does not have global side-effects with "this" is
    // a known local value.
    String source = lines(
        "function f() {",
        "  var x = new externObjSEThis(); ",
        "  x.externObjSEThisMethod('') ",
        "};",
        "f();"
    );
    assertPureCallsMarked(source, ImmutableList.of("externObjSEThis"));
  }

  public void testAnnotationInExterns_new8() throws Exception {
    // "externObjSEThisMethod" modifies "this", the 'this'
    // is not a known local value, so it must be assumed it is to
    // have global side-effects.
    String source = lines(
        "function f(x) {",
        "  x.externObjSEThisMethod('') ",
        "};",
        "f(new externObjSEThis());"
    );
    assertPureCallsMarked(source, ImmutableList.of("externObjSEThis"));
  }

  public void testAnnotationInExterns_new9() throws Exception {
    // "externObjSEThisMethod" modifies "this", the 'this'
    // is not a known local value, so it must be assumed it is to
    // have global side-effects.  All possible values of "x" are considered
    // as no intraprocedural data flow is done.
    String source = lines(
        "function f(x) {",
        "  x = new externObjSEThis(); ",
        "  x.externObjSEThisMethod('') ",
        "};",
        "f(g);"
    );
    assertPureCallsMarked(source, ImmutableList.of("externObjSEThis"));
  }

  public void testAnnotationInExterns_new10() throws Exception {
    String source = lines(
        "function f() {",
        "  new externObjSEThis().externObjSEThisMethod2('') ",
        "};",
        "f();"
    );
    assertPureCallsMarked(source,
        ImmutableList.of("externObjSEThis", "NEW STRING externObjSEThisMethod2", "f"));
  }

  public void testAnnotationInExterns1() throws Exception {
    assertNoPureCalls("externSef1()");
  }

  public void testAnnotationInExterns2() throws Exception {
    assertNoPureCalls("externSef2()");
  }

  public void testAnnotationInExterns3() throws Exception {
    assertPureCallsMarked("externNsef1()", ImmutableList.of("externNsef1"));
  }

  public void testAnnotationInExterns4() throws Exception {
    assertPureCallsMarked("externNsef2()", ImmutableList.of("externNsef2"));
  }

  public void testAnnotationInExterns5() throws Exception {
    assertPureCallsMarked("externNsef3()", ImmutableList.of("externNsef3"));
  }

  public void testNamespaceAnnotationInExterns1() throws Exception {
    assertNoPureCalls("externObj.sef1()");
  }

  public void testNamespaceAnnotationInExterns2() throws Exception {
    assertPureCallsMarked("externObj.nsef1()", ImmutableList.of("externObj.nsef1"));
  }

  public void testNamespaceAnnotationInExterns3() throws Exception {
    assertPureCallsMarked("externObj.nsef2()", ImmutableList.of("externObj.nsef2"));
  }

  public void testNamespaceAnnotationInExterns4() throws Exception {
    assertNoPureCalls("externObj.partialFn()");
  }

  public void testNamespaceAnnotationInExterns5() throws Exception {
    // Test that adding a second definition for a partially defined
    // function doesn't make us think that the function has no side
    // effects.
    String templateSrc = "var o = {}; o.<fnName> = function(){}; o.<fnName>()";

    // Ensure that functions with name != "partialFn" get marked.
    assertPureCallsMarked(
        templateSrc.replace("<fnName>", "notPartialFn"), ImmutableList.of("o.notPartialFn"));

    assertNoPureCalls(templateSrc.replace("<fnName>", "partialFn"));
  }

  public void testNamespaceAnnotationInExterns6() throws Exception {
    assertNoPureCalls("externObj.partialSharedFn()");
  }

  public void testConstructorAnnotationInExterns1() throws Exception {
    assertNoPureCalls("new externSefConstructor()");
  }

  public void testConstructorAnnotationInExterns2() throws Exception {
    String source = lines(
        "var a = new externSefConstructor();",
        "a.sefFnOfSefObj()");
    assertNoPureCalls(source);
  }

  public void testConstructorAnnotationInExterns3() throws Exception {
    String source = lines(
        "var a = new externSefConstructor();",
        "a.nsefFnOfSefObj()");
    assertPureCallsMarked(source, ImmutableList.of("a.nsefFnOfSefObj"));
  }

  public void testConstructorAnnotationInExterns4() throws Exception {
    String source = lines(
        "var a = new externSefConstructor();",
        "a.externShared()");
    assertNoPureCalls(source);
  }

  public void testConstructorAnnotationInExterns5() throws Exception {
    assertPureCallsMarked("new externNsefConstructor()", ImmutableList.of("externNsefConstructor"));
  }

  public void testConstructorAnnotationInExterns6() throws Exception {
    String source = lines(
        "var a = new externNsefConstructor();",
        "a.sefFnOfNsefObj()");
    assertPureCallsMarked(source, ImmutableList.of("externNsefConstructor"));
  }

  public void testConstructorAnnotationInExterns7() throws Exception {
    String source = lines(
        "var a = new externNsefConstructor();",
        "a.nsefFnOfNsefObj()");
    assertPureCallsMarked(source, ImmutableList.of("externNsefConstructor", "a.nsefFnOfNsefObj"));
  }

  public void testConstructorAnnotationInExterns8() throws Exception {
    String source = lines(
        "var a = new externNsefConstructor();",
        "a.externShared()");
    assertPureCallsMarked(source, ImmutableList.of("externNsefConstructor"));
  }

  public void testSharedFunctionName1() throws Exception {
    String source = lines(
        "if (true) {",
        "  a = new externNsefConstructor()",
        "} else {",
        "  a = new externSefConstructor()",
        "}",
        "a.externShared()");
    assertPureCallsMarked(source, ImmutableList.of("externNsefConstructor"));
  }

  public void testSharedFunctionName2() throws Exception {
    // Implementation for both externNsefConstructor and externNsefConstructor2
    // have no side effects.
    boolean broken = true;
    if (broken) {
      assertPureCallsMarked(
          lines(
              "var a;",
              "if (true) {",
              "  a = new externNsefConstructor()",
              "} else {",
              "  a = new externNsefConstructor2()",
              "}",
              "a.externShared()"),
          ImmutableList.of("externNsefConstructor", "externNsefConstructor2"));
    } else {
      assertPureCallsMarked(
          lines(
              "var a;",
              "if (true) {",
              "  a = new externNsefConstructor()",
              "} else {",
              "  a = new externNsefConstructor2()",
              "}",
              "a.externShared()"),
          ImmutableList.of(
              "externNsefConstructor", "externNsefConstructor2", "a.externShared"));
    }
  }

  public void testAnnotationInExternStubs1() throws Exception {
    assertPureCallsMarked("o.propWithStubBefore('a');",
        ImmutableList.of("o.propWithStubBefore"));
  }

  public void testAnnotationInExternStubs1b() throws Exception {
    assertPureCallsMarked("o.propWithStubBeforeWithJSDoc('a');",
        ImmutableList.of("o.propWithStubBeforeWithJSDoc"));
  }

  public void testAnnotationInExternStubs2() throws Exception {
    assertPureCallsMarked("o.propWithStubAfter('a');",
        ImmutableList.of("o.propWithStubAfter"));
  }

  public void testAnnotationInExternStubs3() throws Exception {
    assertNoPureCalls("propWithAnnotatedStubAfter('a');");
  }

  public void testAnnotationInExternStubs4() throws Exception {
    // An externs definition with a stub that differs from the declaration.
    // Verify our assumption is valid about this.
    String externs = lines(
        "/**@constructor*/function externObj5(){}",

        "externObj5.prototype.propWithAnnotatedStubAfter = function(s) {};",

        "/**",
        " * @param {string} s id.",
        " * @return {string}",
        " * @nosideeffects",
        " */",
        "externObj5.prototype.propWithAnnotatedStubAfter;");

    this.mode = TypeInferenceMode.OTI_ONLY;
    testSame(
        externs(externs),
        srcs("o.prototype.propWithAnnotatedStubAfter"),
        warning(TypeValidator.DUP_VAR_DECLARATION_TYPE_MISMATCH));
    assertThat(noSideEffectCalls).isEmpty();

    this.mode = TypeInferenceMode.NTI_ONLY;
    testSame(
        externs(TEST_EXTERNS + externs),
        srcs("o.prototype.propWithAnnotatedStubAfter"),
        warning(GlobalTypeInfoCollector.REDECLARED_PROPERTY));
    assertThat(noSideEffectCalls).isEmpty();
  }

  public void testAnnotationInExternStubs5() throws Exception {
    // An externs definition with a stub that differs from the declaration.
    // Verify our assumption is valid about this.
    String externs = lines(
        "/**@constructor*/function externObj5(){}",

        "/**",
        " * @param {string} s id.",
        " * @return {string}",
        " * @nosideeffects",
        " */",
        "externObj5.prototype.propWithAnnotatedStubAfter = function(s) {};",

        "/**",
        " * @param {string} s id.",
        " * @return {string}",
        " */",
        "externObj5.prototype.propWithAnnotatedStubAfter;");

    this.mode = TypeInferenceMode.OTI_ONLY;
    testSame(
        externs(externs),
        srcs("o.prototype.propWithAnnotatedStubAfter"),
        warning(TypeValidator.DUP_VAR_DECLARATION));
    assertThat(noSideEffectCalls).isEmpty();

    this.mode = TypeInferenceMode.NTI_ONLY;
    testSame(
        externs(TEST_EXTERNS + externs),
        srcs("o.prototype.propWithAnnotatedStubAfter"),
        warning(GlobalTypeInfoCollector.REDECLARED_PROPERTY));
    assertThat(noSideEffectCalls).isEmpty();
  }

  public void testNoSideEffectsSimple() throws Exception {
    String prefix = "function f(){";
    String suffix = "} f()";
    List<String> expected = ImmutableList.of("f");

    assertPureCallsMarked(
        prefix + "" + suffix, expected);
    assertPureCallsMarked(
        prefix + "return 1" + suffix, expected);
    assertPureCallsMarked(
        prefix + "return 1 + 2" + suffix, expected);

    // local var
    assertPureCallsMarked(
        prefix + "var a = 1; return a" + suffix, expected);

    // mutate local var
    assertPureCallsMarked(
        prefix + "var a = 1; a = 2; return a" + suffix, expected);
    assertPureCallsMarked(
        prefix + "var a = 1; a = 2; return a + 1" + suffix, expected);

    // read from obj literal
    assertPureCallsMarked(
        prefix + "var a = {foo : 1}; return a.foo" + suffix, expected);
    assertPureCallsMarked(
        prefix + "var a = {foo : 1}; return a.foo + 1" + suffix, expected);

    // read from extern
    assertPureCallsMarked(
        prefix + "return externObj" + suffix, expected);
    assertPureCallsMarked(
        "function g(x) { x.foo = 3; }" /* to suppress missing property */
        + prefix + "return externObj.foo" + suffix, expected);
  }

  public void testNoSideEffectsSimple2() throws Exception {
    regExpHaveSideEffects = false;
    String source = lines(
        "function f() {",
        "  return ''.replace(/xyz/g, '');",
        "}",
        "f()");
    assertPureCallsMarked(source, ImmutableList.of("STRING  STRING replace", "f"));
  }

  public void testNoSideEffectsSimple3() throws Exception {
    regExpHaveSideEffects = false;
    String source = lines(
        "function f(/** string */ str) {",
        "  return str.replace(/xyz/g, '');",
        "}",
        "f('')");
    assertPureCallsMarked(source, ImmutableList.of("str.replace", "f"));
  }

  public void testResultLocalitySimple() throws Exception {
    String prefix = "var g; function f(){";
    String suffix = "} f()";
    final List<String> fReturnsLocal = ImmutableList.of("f");
    final List<String> fReturnsNonLocal = ImmutableList.<String>of();

    // no return
    checkLocalityOfMarkedCalls(prefix + "" + suffix, fReturnsLocal);
    // simple return expressions
    checkLocalityOfMarkedCalls(prefix + "return 1" + suffix, fReturnsLocal);
    checkLocalityOfMarkedCalls(prefix + "return 1 + 2" + suffix, fReturnsLocal);

    // global result
    checkLocalityOfMarkedCalls(prefix + "return g" + suffix, fReturnsNonLocal);

    // multiple returns
    checkLocalityOfMarkedCalls(prefix + "return 1; return 2" + suffix, fReturnsLocal);
    checkLocalityOfMarkedCalls(prefix + "return 1; return g" + suffix, fReturnsNonLocal);

    // local var, not yet. Note we do not handle locals properly here.
    checkLocalityOfMarkedCalls(prefix + "var a = 1; return a" + suffix, fReturnsNonLocal);

    // mutate local var, not yet. Note we do not handle locals properly here.
    checkLocalityOfMarkedCalls(prefix + "var a = 1; a = 2; return a" + suffix, fReturnsNonLocal);
    checkLocalityOfMarkedCalls(prefix + "var a = 1; a = 2; return a + 1" + suffix, fReturnsLocal);

    // read from obj literal
    checkLocalityOfMarkedCalls(prefix + "return {foo : 1}.foo" + suffix, fReturnsNonLocal);
    checkLocalityOfMarkedCalls(
        prefix + "var a = {foo : 1}; return a.foo" + suffix, fReturnsNonLocal);

    // read from extern
    checkLocalityOfMarkedCalls(prefix + "return externObj" + suffix, ImmutableList.<String>of());
    checkLocalityOfMarkedCalls(
        "function inner(x) { x.foo = 3; }" /* to suppress missing property */
        + prefix + "return externObj.foo" + suffix, ImmutableList.<String>of());
  }

  public void testReturnLocalityTaintObjectLiteralWithGlobal() {
    // return empty object literal.  This is completely local
    String source = lines(
        "function f() { return {} }",
        "f();"
    );
    checkLocalityOfMarkedCalls(source, ImmutableList.of("f"));
    // return obj literal with global property is still local.
    source = lines(
        "var global = new Object();",
        "function f() { return {'asdf': global} }",
        "f();");
    checkLocalityOfMarkedCalls(source, ImmutableList.<String>of("f"));
  }

  public void testReturnLocalityTaintArrayLiteralWithGlobal() {
    String source =
        lines(
            "function f() { return []; }",
            "f();",
            "function g() { return [1, {}]; }",
            "g();");
    checkLocalityOfMarkedCalls(source, ImmutableList.of("f", "g"));
    // return array literal with global value is still a local value.
    source =
        lines(
            "var global = new Object();",
            "function f() { return [2 ,global]; }",
            "f();");
    checkLocalityOfMarkedCalls(source, ImmutableList.<String>of("f"));
  }

  public void testReturnLocalityMultipleDefinitionsSameName() {
    String source = lines(
            "var global = new Object();",
            "A.func = function() {return global}", // return global (taintsReturn)
            "B.func = function() {return 1; }", // returns local
            "C.func();");
    checkLocalityOfMarkedCalls(source, ImmutableList.<String>of());
  }

  public void testExternCalls() throws Exception {
    testExternCallsForTypeInferenceMode(TypeInferenceMode.BOTH);
  }

  public void testExternCallsNoTypeChecking() throws Exception {
    testExternCallsForTypeInferenceMode(TypeInferenceMode.NEITHER);
  }

  private void testExternCallsForTypeInferenceMode(TypeInferenceMode typeInferenceMode) {
    mode = typeInferenceMode;
    String prefix = "function f(){";
    String suffix = "} f()";

    assertPureCallsMarked(prefix + "externNsef1()" + suffix,
                     ImmutableList.of("externNsef1", "f"));
    assertPureCallsMarked(prefix + "externObj.nsef1()" + suffix,
                     ImmutableList.of("externObj.nsef1", "f"));
    checkLocalityOfMarkedCalls("externNsef1(); externObj.nsef1()", ImmutableList.of());

    assertNoPureCalls(prefix + "externSef1()" + suffix);
    assertNoPureCalls(prefix + "externObj.sef1()" + suffix);
  }

  public void testApply() throws Exception {
    String source = lines(
        "function f() {return 42}",
        "f.apply(null)");
    assertPureCallsMarked(source, ImmutableList.of("f.apply"));
  }

  public void testCall() throws Exception {
    String source = lines(
        "function f() {return 42}",
        "f.call(null)");
    assertPureCallsMarked(source, ImmutableList.of("f.call"));
  }

  public void testApplyToUnknownDefinition() throws Exception {
    String source = lines(
        "var dict = {'func': function() {}};",
        "function f() { var s = dict['func'];}",
        "f.apply(null)"
    );
    assertPureCallsMarked(source, ImmutableList.of("f.apply"));

    // Not marked because the definition cannot be found so unknown side effects.
    source = lines(
        "var dict = {'func': function() {}};",
        "function f() { var s = dict['func'].apply(null); }",
        "f.apply(null)"
    );
    assertNoPureCalls(source);

    // Not marked because the definition cannot be found so unknown side effects.
    source = lines(
        "var pure = function() {};",
        "var dict = {'func': function() {}};",
        "function f() { var s = (dict['func'] || pure)();}",
        "f()"
    );
    assertNoPureCalls(source);

    // Not marked because the definition cannot be found so unknown side effects.
    source = lines(
        "var pure = function() {};"
            , "var dict = {'func': function() {}};"
            , "function f() { var s = (condition ? dict['func'] : pure)();}"
            , "f()"
    );
    assertNoPureCalls(source);
  }

  public void testInference1() throws Exception {
    String source = lines(
        "function f() {return g()}",
        "function g() {return 42}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("g", "f"));
  }

  public void testInference2() throws Exception {
    String source = lines(
        "var a = 1;",
        "function f() {g()}",
        "function g() {a=2}",
        "f()"
    );
    assertNoPureCalls(source);
  }

  public void testInference3() throws Exception {
    String source = lines(
        "var f = function() {return g()};",
        "var g = function() {return 42};",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("g", "f"));
  }

  public void testInference4() throws Exception {
    String source = lines(
        "var a = 1; var f = function() {g()};",
        "var g = function() {a=2};",
        "f()"
    );
    assertNoPureCalls(source);
  }

  public void testInference5() throws Exception {
    String source = lines(
        "goog.f = function() {return goog.g()};",
        "goog.g = function() {return 42};",
        "goog.f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("goog.g", "goog.f"));
  }

  public void testInference6() throws Exception {
    String source = lines(
        "var a = 1;",
        "goog.f = function() {goog.g()};",
        "goog.g = function() {a=2};",
        "goog.f()"
    );
    assertNoPureCalls(source);
  }

  public void testLocalizedSideEffects1() throws Exception {
    // Returning a function that contains a modification of a local
    // is not a global side-effect.
    String source = lines(
        "function f() {",
        "  var x = {foo : 0}; return function() {x.foo++};",
        "}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects2() throws Exception {
    // Calling a function that contains a modification of a local
    // is a global side-effect (the value has escaped).
    String source = lines(
        "function f() {",
        "  var x = {foo : 0}; (function() {x.foo++})();",
        "}",
        "f()"
    );
    assertNoPureCalls(source);
  }

  public void testLocalizedSideEffects3() throws Exception {
    // A local that might be assigned a global value and whose properties
    // are modified must be considered a global side-effect.
    String source = lines(
        "var g = {foo:1};",
        "function f() {var x = g; x.foo++};",
        "f();"
    );
    assertNoPureCalls(source);
  }

  public void testLocalizedSideEffects4() throws Exception {
    // An array is a local object, assigning a local array is not a global side-effect.
    assertPureCallsMarked(
        lines(
            "function f() {var x = []; x[0] = 1;}", // preserve newline
            "f()"),
        ImmutableList.of("f"));

    // TODO(bradfordcsmith): Remove NEITHER when type checker understands let/const
    mode = TypeInferenceMode.NEITHER;
    assertPureCallsMarked(
        lines(
            "function f() {const x = []; x[0] = 1;}", // preserve newline
            "f()"),
        ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects5() throws Exception {
    // Assigning a local alias of a global is a global
    // side-effect.
    String source = lines(
        "var g = [];",
        "function f() {var x = g; x[0] = 1;};",
        "f()"
    );
    assertNoPureCalls(source);
  }

  public void testLocalizedSideEffects6() throws Exception {
    // Returning a local object that has been modified
    // is not a global side-effect.
    assertPureCallsMarked(
        lines(
            "function f() {", // preserve newline
            "  var x = {}; x.foo = 1; return x;",
            "}",
            "f()"),
        ImmutableList.of("f"));

    // TODO(bradfordcsmith): Remove NEITHER when type checker understands let/const
    mode = TypeInferenceMode.NEITHER;
    assertPureCallsMarked(
        lines(
            "function f() {", // preserve newline
            "  const x = {}; x.foo = 1; return x;",
            "}",
            "f()"),
        ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects7() throws Exception {
    // Returning a local object that has been modified
    // is not a global side-effect.
    assertPureCallsMarked(
        lines(
            "/** @constructor A */ function A() {};",
            "function f() {",
            "  var a = []; a[1] = 1; return a;",
            "}",
            "f()"),
        ImmutableList.of("f"));

    // TODO(bradfordcsmith): Remove NEITHER when type checker understands let/const
    mode = TypeInferenceMode.NEITHER;
    assertPureCallsMarked(
        lines(
            "/** @constructor A */ function A() {};",
            "function f() {",
            "  const a = []; a[1] = 1; return a;",
            "}",
            "f()"),
        ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects8() throws Exception {
    // Returning a local object that has been modified
    // is not a global side-effect.
    // TODO(tdeegan): Not yet. Propagate local object information.
    String source =
        lines(
            "/** @constructor A */ function A() {};",
            "function f() {",
            "  var a = new A; a.foo = 1; return a;",
            "}",
            "f()");
    assertPureCallsMarked(source, ImmutableList.of("A"));
  }

  public void testLocalizedSideEffects9() throws Exception {
    // Returning a local object that has been modified
    // is not a global side-effect.
    // TODO(johnlenz): Not yet. Propagate local object information.
    String source = lines(
        "/** @constructor A */ function A() {this.x = 1};",
        "function f() {",
        "  var a = new A; a.foo = 1; return a;",
        "}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("A"));
  }

  public void testLocalizedSideEffects10() throws Exception {
    // Returning a local object that has been modified
    // is not a global side-effect.
    String source = lines(
        "/** @constructor A */ function A() {};",
        "A.prototype.g = function() {this.x = 1};",
        "function f() {",
        "  var a = new A; a.g(); return a;",
        "}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("A"));
  }

  public void testLocalizedSideEffects11() throws Exception {
    // TODO(tdeegan): updateA is side effect free.
    // Calling a function of a local object that taints this.
    String source =
        lines(
            "/** @constructor */",
            "function A() {}",
            "A.prototype.update = function() { this.x = 1; };",
            "",
            "/** @constructor */",
            "function B() { ",
            "  this.a_ = new A();",
            "}",
            "B.prototype.updateA = function() {",
            "  var b = this.a_;",
            "  b.update();",
            "};",
            "",
            "var x = new B();",
            "x.updateA();");
    assertPureCallsMarked(source, ImmutableList.of("A", "B"));
  }

  public void testLocalizedSideEffects12() throws Exception {
    // An array is a local object, assigning a local array is not a global
    // side-effect. This tests the behavior if the access is in a block scope.
    assertPureCallsMarked(
        lines(
            "function f() {var x = []; { x[0] = 1; } }", // preserve newline
            "f()"),
        ImmutableList.of("f"));

    // TODO(bradfordcsmith): Remove NEITHER when type checker understands let/const
    mode = TypeInferenceMode.NEITHER;
    assertPureCallsMarked(
        lines(
            "function f() {const x = []; { x[0] = 1; } }", // preserve newline
            "f()"),
        ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects13() {
    this.mode = TypeInferenceMode.NEITHER;
    String source = lines(
        "function f() {var [x, y] = [3, 4]; }",
        "f()");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects14() {
    this.mode = TypeInferenceMode.NEITHER;
    String source = lines(
        "function f() {var x; if (true) { [x] = [5]; } }",
        "f()");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects15() {
    this.mode = TypeInferenceMode.NEITHER;
    String source = lines(
        "function f() {var {length} = 'a string'; }",
        "f()");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects16() {
    this.mode = TypeInferenceMode.NEITHER;
    String source = lines(
        "function f(someArray) {var [a, , b] = someArray; }",
        "f()");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects17() {
    this.mode = TypeInferenceMode.NEITHER;
    String source = lines(
        "function f(someObj) {var { very: { nested: { lhs: pattern }} } = someObj; }",
        "f()");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects18() {
    this.mode = TypeInferenceMode.NEITHER;
    String source = lines(
        "function SomeCtor() { [this.x, this.y] = getCoordinates(); }",
        "new SomeCtor()");
    assertNoPureCalls(source);
  }

  public void testLocalizedSideEffects19() {
    this.mode = TypeInferenceMode.NEITHER;
    String source = lines(
        "function SomeCtor() { [this.x, this.y] = [0, 1]; }",
        "new SomeCtor()");
    assertPureCallsMarked(source, ImmutableList.of("SomeCtor"));
  }

  public void testLocalizedSideEffects20() {
    this.mode = TypeInferenceMode.NEITHER;
    String source = lines(
        "function SomeCtor() { this.x += 1; }",
        "new SomeCtor()");
    assertPureCallsMarked(source, ImmutableList.of("SomeCtor"));
  }

  public void testLocalizedSideEffects21() {
    // TODO(bradfordcsmith): Remove NEITHER when type checkers understand destructuring and
    // let/const.
    this.mode = TypeInferenceMode.NEITHER;
    String source = lines("function f(values) { const x = {}; [x.y, x.z] = values; }", "f()");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects22() {
    this.mode = TypeInferenceMode.NEITHER;
    String source = lines(
        "var x = {}; function f(values) { [x.y, x.z] = values; }",
        "f()");
    assertNoPureCalls(source);
  }

  public void testLocalizedSideEffects23() {
    // TODO(bradfordcsmith): Remove NEITHER when type checkers understand destructuring and
    // let/const.
    this.mode = TypeInferenceMode.NEITHER;
    String source =
        lines(
            "function f(values) { const x = {}; [x.y, x.z = defaultNoSideEffects] = values; }",
            "f()");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects24() {
    this.mode = TypeInferenceMode.NEITHER;
    String source = lines(
        "function f(values) { var x = {}; [x.y, x.z = defaultWithSideEffects()] = values; }",
        "f()");
    assertNoPureCalls(source);
  }

  public void testUnaryOperators1() throws Exception {
    String source = lines(
        "function f() {var x = 1; x++}",
        "f()");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testUnaryOperators2() throws Exception {
    String source = lines(
        "var x = 1;",
        "function f() {x++}",
        "f()");
    assertNoPureCalls(source);
  }

  public void testUnaryOperators3() throws Exception {
    assertPureCallsMarked(
        lines(
            "function f() {var x = {foo : 0}; x.foo++}", // preserve newline
            "f()"),
        ImmutableList.of("f"));

    // TODO(bradfordcsmith): Remove NEITHER when type checker understands let/const
    mode = TypeInferenceMode.NEITHER;
    assertPureCallsMarked(
        lines(
            "function f() {const x = {foo : 0}; x.foo++}", // preserve newline
            "f()"),
        ImmutableList.of("f"));
  }

  public void testUnaryOperators4() throws Exception {
    String source = lines(
        "var x = {foo : 0};",
        "function f() {x.foo++}",
        "f()");
    assertNoPureCalls(source);
  }

  public void testUnaryOperators5() throws Exception {
    assertPureCallsMarked(
        lines(
            "function f(x) {x.foo++}", // preserve newline
            "f({foo : 0})"),
        ImmutableList.of("f"));

    // TODO(bradfordcsmith): Remove NEITHER when type checker understands destructured parameters
    mode = TypeInferenceMode.NEITHER;
    assertPureCallsMarked(
        lines(
            "function f({x}) {x.foo++}", // preserve newline
            "f({x: {foo : 0}})"),
        ImmutableList.of("f"));
  }

  public void testDeleteOperator1() throws Exception {
    String source = lines(
        "var x = {};",
        "function f() {delete x}",
        "f()");
    assertNoPureCalls(source);
  }

  public void testDeleteOperator2() throws Exception {
    String source = lines(
        "function f() {var x = {}; delete x}",
        "f()");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testOrOperator1() throws Exception {
    String source = lines(
        "var f = externNsef1 || externNsef2;",
        "f()");
    assertNoPureCalls(source);
  }

  public void testOrOperator2() throws Exception {
    String source = lines(
        "var f = function(){} || externNsef2;",
        "f()");
    assertNoPureCalls(source);
  }

  public void testOrOperator3() throws Exception {
    String source = lines(
        "var f = externNsef2 || function(){};",
        "f()");
    assertNoPureCalls(source);
  }

  public void testOrOperators4() throws Exception {
    String source = lines(
        "var f = function(){} || function(){};",
        "f()");
    assertNoPureCalls(source);
  }

  public void testAndOperator1() throws Exception {
    String source = lines(
        "var f = externNsef1 && externNsef2;",
        "f()");
    assertNoPureCalls(source);
  }

  public void testAndOperator2() throws Exception {
    String source = lines(
        "var f = function(){} && externNsef2;",
        "f()");
    assertNoPureCalls(source);
  }

  public void testAndOperator3() throws Exception {
    String source = lines(
        "var f = externNsef2 && function(){};",
        "f()");
    assertNoPureCalls(source);
  }

  public void testAndOperators4() throws Exception {
    String source = lines(
        "var f = function(){} && function(){};",
        "f()");
    assertNoPureCalls(source);
  }

  public void testHookOperator1() throws Exception {
    String source = lines(
        "var f = true ? externNsef1 : externNsef2;",
        "f()");
    assertNoPureCalls(source);
  }

  public void testHookOperator2() throws Exception {
    String source = lines(
        "var f = true ? function(){} : externNsef2;",
        "f()");
    assertNoPureCalls(source);
  }

  public void testHookOperator3() throws Exception {
    String source = lines(
        "var f = true ? externNsef2 : function(){};",
        "f()");
    assertNoPureCalls(source);
  }

  public void testHookOperators4() throws Exception {
    String source = lines(
        "var f = true ? function(){} : function(){};",
        "f()");
    assertPureCallsMarked(source, ImmutableList.<String>of("f"));
  }

  public void testHookOperators5() throws Exception {
    String source = lines(
        "var f = String.prototype.trim ? function(str){return str} : function(){};",
        "f()");
    assertPureCallsMarked(source, ImmutableList.<String>of("f"));
  }

  public void testHookOperators6() throws Exception {
    String source = lines(
        "var f = yyy ? function(str){return str} : xxx ? function() {} : function(){};",
        "f()");
    assertPureCallsMarked(source, ImmutableList.<String>of("f"));
  }

  public void testThrow1() throws Exception {
    String source = lines(
        "function f(){throw Error()};",
        "f()");
    assertPureCallsMarked(source, ImmutableList.<String>of("Error"));
  }

  public void testThrow2() throws Exception {
    String source = lines(
        "/**@constructor*/function A(){throw Error()};",
        "function f(){return new A()}",
        "f()");
    assertPureCallsMarked(source, ImmutableList.<String>of("Error"));
  }

  public void testAssignmentOverride() throws Exception {
    String source = lines(
        "/**@constructor*/function A(){}",
        "A.prototype.foo = function(){};",
        "var a = new A;",
        "a.foo();");
    assertPureCallsMarked(source, ImmutableList.of("A", "a.foo"));

    // Ideally inline aliases takes care of this.
    String sourceOverride = lines(
        "/**@constructor*/ function A(){}",
        "A.prototype.foo = function(){};",
        "var x = 1",
        "function f(){x = 10}",
        "var a = new A;",
        "a.foo = f;",
        "a.foo();");
    assertPureCallsMarked(sourceOverride, ImmutableList.of("A"));
  }

  public void testInheritance1() throws Exception {
    String source = CompilerTypeTestCase.CLOSURE_DEFS + lines(
        "/**@constructor*/function I(){}",
        "I.prototype.foo = function(){};",
        "I.prototype.bar = function(){this.foo()};",
        "/**@constructor@extends {I}*/function A(){};",
        "goog.inherits(A, I);",
        "/** @override */A.prototype.foo = function(){var data=24};",
        "var i = new I();i.foo();i.bar();",
        "var a = new A();a.foo();a.bar();"
    );

    assertPureCallsMarked(source,
                     ImmutableList.of("this.foo", "goog.inherits",
                                      "I", "i.foo", "i.bar",
                                      "A", "a.foo", "a.bar"));
  }

  public void testInheritance2() throws Exception {
    String source = CompilerTypeTestCase.CLOSURE_DEFS + lines(
        "/**@constructor*/function I(){}",
        "I.prototype.foo = function(){};",
        "I.prototype.bar = function(){this.foo()};",
        "/**@constructor@extends {I}*/function A(){};",
        "goog.inherits(A, I);",
        "/** @override */A.prototype.foo = function(){this.data=24};",
        "var i = new I();i.foo();i.bar();",
        "var a = new A();a.foo();a.bar();"
    );

    assertPureCallsMarked(source, ImmutableList.of("goog.inherits", "I", "A"));
  }

  public void testAmbiguousDefinitions() throws Exception {
    String source = CompilerTypeTestCase.CLOSURE_DEFS + lines(
        "var globalVar = 1;",
        "A.f = function() {globalVar = 2;};",
        "A.f = function() {};",
        "function sideEffectCaller() { A.f() };",
        "sideEffectCaller();"
    );
    // Can't tell which f is being called so it assumes both.
    assertNoPureCalls(source);
  }

  public void testAmbiguousDefinitionsCall() throws Exception {
    String source = CompilerTypeTestCase.CLOSURE_DEFS + lines(
        "var globalVar = 1;",
        "A.f = function() {globalVar = 2;};",
        "A.f = function() {};",
        "function sideEffectCaller() { A.f.call(null); };",
        "sideEffectCaller();"
    );
    // Can't tell which f is being called so it assumes both.
    assertNoPureCalls(source);
  }

  public void testAmbiguousDefinitionsAllPropagationTypes() throws Exception {
    String source = CompilerTypeTestCase.CLOSURE_DEFS + lines(
        "var globalVar = 1;",
        "/**@constructor*/A.f = function() { this.x = 5; };",
        "/**@constructor*/B.f = function() {};",
        "function sideEffectCaller() { new C.f() };",
        "sideEffectCaller();"
    );
    // Can't tell which f is being called so it assumes both.
    assertPureCallsMarked(source, ImmutableList.<String>of("C.f", "sideEffectCaller"));
  }

  public void testAmbiguousDefinitionsCallWithThis() throws Exception {
    String source = CompilerTypeTestCase.CLOSURE_DEFS + lines(
        "var globalVar = 1;",
        "A.modifiesThis = function() { this.x = 5; };",
        "/**@constructor*/function Constructor() { Constructor.modifiesThis.call(this); };",
        "Constructor.modifiesThis = function() {};",
        "new Constructor();",
        "A.modifiesThis();"
    );

    // Can't tell which modifiesThis is being called so it assumes both.
    assertPureCallsMarked(source, ImmutableList.<String>of("Constructor"));
  }

  public void testAmbiguousDefinitionsBothCallThis() throws Exception {
    String source =
        lines(
            "B.f = function() {",
            "  this.x = 1;",
            "}",
            "/** @constructor */ function C() {",
            "  this.f.apply(this);",
            "}",
            "C.prototype.f = function() {",
            "  this.x = 2;",
            "}",
            "new C();");
    assertPureCallsMarked(source, ImmutableList.of("C"));
  }

  public void testAmbiguousDefinitionsAllCallThis() throws Exception {
    String source =
        lines(
            "A.f = function() { this.y = 1 };",
            "C.f = function() { };",
            "var g = function() {D.f()};",
            "/** @constructor */ var h = function() {E.f.apply(this)};",
            "var i = function() {F.f.apply({})};", // it can't tell {} is local.
            "g();",
            "new h();",
            "i();" // With better locals tracking i could be identified as pure
            );
    assertPureCallsMarked(source, ImmutableList.of("F.f.apply", "h"));
  }

  public void testAmbiguousDefinitionsMutatesGlobalArgument() throws Exception {
    String source =
        lines(
            "// Mutates argument",
            "A.a = function(argument) {",
            "  argument.x = 2;",
            "};",
            "// No side effects",
            "B.a = function() {};",
            "var b = function(x) {C.a(x)};",
            "b({});");
    assertNoPureCalls(source);
  }

  public void testAmbiguousDefinitionsMutatesLocalArgument() throws Exception {
    assertPureCallsMarked(
        lines(
            "// Mutates argument",
            "A.a = function(argument) {",
            "  argument.x = 2;",
            "};",
            "// No side effects",
            "B.a = function() {};",
            "var b = function() {",
            "  C.a({});",
            "};",
            "b();"),
        ImmutableList.of("C.a", "b"));

    // TODO(bradfordcsmith): Remove NEITHER when type checker understands destructuring parameters
    mode = TypeInferenceMode.NEITHER;
    assertPureCallsMarked(
        lines(
            "// Mutates argument",
            "A.a = function([argument]) {",
            "  argument.x = 2;",
            "};",
            "// No side effects",
            "B.a = function() {};",
            "var b = function() {",
            "  C.a([{}]);",
            "};",
            "b();"),
        ImmutableList.of("C.a", "b"));
  }

  public void testAmbiguousExternDefinitions() {
    assertNoPureCalls("x.duplicateExternFunc()");

    // nsef1 is defined as no side effect in the externs.
    String source = lines(
        "var global = 1;",
        // Overwrite the @nosideeffects with this side effect
        "A.nsef1 = function () {global = 2;};",
        "externObj.nsef1();"
    );
    assertNoPureCalls(source);
  }

  /**
   * Test bug where the FunctionInformation for "A.x" and "a" were separate causing
   * .x() calls to appear pure because the global side effect was only registed for the function
   * linked to "a".
   */
  public void testAmbiguousDefinitionsDoubleDefinition() {
    String source = lines(
        "var global = 1;",
        "A.x = function a() { global++; }",
        "B.x = function() {}",
        "B.x();"
    );
    assertNoPureCalls(source);
  }

  public void testAmbiguousDefinitionsDoubleDefinition2() {
    String source = lines(
        "var global = 1;",
        "A.x = function a() { global++; }",
        "a = function() {}",
        "B.x(); a();"
    );
    assertNoPureCalls(source);
  }

  public void testAmbiguousDefinitionsDoubleDefinition3() {
    String source = lines(
        "var global = 1;",
        "A.x = function a() {}",
        "a = function() { global++; }",
        "B.x(); a();"
    );
    assertPureCallsMarked(source, ImmutableList.of("B.x"));
  }

  public void testAmbiguousDefinitionsDoubleDefinition4() {
    String source = lines(
        "var global = 1;",
        "A.x = function a() {}",
        "B.x = function() { global++; }",
        "B.x(); a();"
    );
    assertPureCallsMarked(source, ImmutableList.of("a"));
  }

  public void testAmbiguousDefinitionsDoubleDefinition5() {
    String source = lines(
        "var global = 1;",
        "A.x = cond ? function a() { global++ } : function b() {}",
        "B.x = function() { global++; }",
        "B.x(); a(); b();"
    );
    assertPureCallsMarked(source, ImmutableList.of("b"));
  }

  public void testAmbiguousDefinitionsDoubleDefinition6() {
    String source = lines(
            "var SetCustomData1 = function SetCustomData2(element, dataName, dataValue) {",
            "    var x = element['_customData'];",
            "    x[dataName] = dataValue;",
            "}",
            "SetCustomData1(window, \"foo\", \"bar\");");
    assertNoPureCalls(source);
  }

  public void testCallBeforeDefinition() throws Exception {
    assertPureCallsMarked("f(); function f(){}", ImmutableList.of("f"));
    assertPureCallsMarked("var a = {}; a.f(); a.f = function (){}", ImmutableList.of("a.f"));
  }

  public void testConstructorThatModifiesThis1() throws Exception {
    String source = lines(
        "/**@constructor*/function A(){this.foo = 1}",
        "function f() {return new A}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("A", "f"));
  }

  public void testConstructorThatModifiesThis2() throws Exception {
    String source = lines(
        "/**@constructor*/function A(){this.foo()}",
        "A.prototype.foo = function(){this.data=24};",
        "function f() {return new A}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("A", "f"));
  }

  public void testConstructorThatModifiesThis3() throws Exception {
    // test chained
    String source = lines(
        "/**@constructor*/function A(){this.foo()}",
        "A.prototype.foo = function(){this.bar()};",
        "A.prototype.bar = function(){this.data=24};",
        "function f() {return new A}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("A", "f"));
  }

  public void testConstructorThatModifiesThis4() throws Exception {
    String source = lines(
        "/**@constructor*/function A(){foo.call(this)}",
        "function foo(){this.data=24};",
        "function f() {return new A}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("A", "f"));
  }

  public void testConstructorThatModifiesGlobal1() throws Exception {
    String source = lines(
        "var b = 0;",
        "/**@constructor*/function A(){b=1};",
        "function f() {return new A}",
        "f()"
    );
    assertNoPureCalls(source);
  }

  public void testConstructorThatModifiesGlobal2() throws Exception {
    String source = lines(
        "/**@constructor*/function A(){this.foo()}",
        "A.prototype.foo = function(){b=1};",
        "function f() {return new A}",
        "f()"
    );
    assertNoPureCalls(source);
  }

  public void testCallFunctionThatModifiesThis() throws Exception {
    String source = lines(
        "/**@constructor*/function A(){}" ,
            "A.prototype.foo = function(){this.data=24};" ,
            "function f(){var a = new A; return a}" ,
            "function g(){var a = new A; a.foo(); return a}" ,
            "f(); g()"
    );
    assertPureCallsMarked(source, ImmutableList.of("A", "A", "f"));
  }

  public void testMutatesArguments1() throws Exception {
    assertPureCallsMarked(
        lines(
            "function f(x) { x.y = 1; }", // preserve newline
            "f({});"),
        ImmutableList.of("f"));

    // TODO(bradfordcsmith): Remove NEITHER when type checker understands destructuring parameters
    mode = TypeInferenceMode.NEITHER;
    assertPureCallsMarked(
        lines(
            "function f([x]) { x.y = 1; }", // preserve newline
            "f([{}]);"),
        ImmutableList.of("f"));
  }

  public void testMutatesArguments2() throws Exception {
    String source = lines(
        "function f(x) { x.y = 1; }",
        "f(window);");
    assertNoPureCalls(source);
  }

  public void testMutatesArguments3() throws Exception {
    // We could do better here with better side-effect propagation.
    String source = lines(
        "function f(x) { x.y = 1; }",
        "function g(x) { f(x); }",
        "g({});");
    assertNoPureCalls(source);
  }

  public void testMutatesArguments4() throws Exception {
    assertPureCallsMarked(
        lines(
            "function f(x) { x.y = 1; }", // preserve newline
            "function g(x) { f({}); x.y = 1; }",
            "g({});"),
        ImmutableList.of("f", "g"));

    // TODO(bradfordcsmith): Remove NEITHER when type checker understands destructuring parameters
    mode = TypeInferenceMode.NEITHER;
    assertPureCallsMarked(
        lines(
            "function f([x]) { x.y = 1; }", // preserve newline
            "function g([x]) { f([{}]); x.y = 1; }",
            "g([{}]);"),
        ImmutableList.of("f", "g"));
  }

  public void testMutatesArguments5() throws Exception {
    String source = lines(
        "function f(x) {",
        "  function g() {",
        "    x.prop = 5;",
        "  }",
        "  g();",
        "}",
        "f(window);");
    assertNoPureCalls(source);
  }

  public void testMutatesArgumentsArray1() throws Exception {
    String source = lines(
        "function f(x) { arguments[0] = 1; }",
        "f({});");
    assertPureCallsMarked(source, ImmutableList.<String>of("f"));
  }

  public void testMutatesArgumentsArray2() throws Exception {
    // We could be smarter here.
    String source = lines(
        "function f(x) { arguments[0].y = 1; }",
        "f({});");
    assertNoPureCalls(source);
  }

  public void testMutatesArgumentsArray3() throws Exception {
    String source = lines(
        "function f(x) { arguments[0].y = 1; }",
        "f(x);");
    assertNoPureCalls(source);
  }

  public void testCallGenerator1() {
    this.mode = TypeInferenceMode.NEITHER; // type check for yield not yet implemented
    String source =
        lines(
            "var x = 0;",
            "function* f() {",
            "  x = 2",
            "  while (true) {",
            "    yield x;",
            "  }",
            "}",
            "var g = f();");
    assertNoPureCalls(source);
    Node lastRoot = getLastCompiler().getRoot().getLastChild();
    Node call = findQualifiedNameNode("f", lastRoot).getParent();
    assertThat(call.isNoSideEffectsCall()).isFalse();
    assertEquals(
        new Node.SideEffectFlags().setReturnsTainted().valueOf(), call.getSideEffectFlags());
  }

  public void testCallGenerator2() {
    this.mode = TypeInferenceMode.NEITHER; // type check for yield not yet implemented
    String source = lines(
            "function* f() {",
            "  while (true) {",
            "    yield 1;",
            "  }",
            "}",
            "var g = f();");
    assertNoPureCalls(source);
  }

  public void testCallFunctionFOrG() throws Exception {
    String source = lines(
        "function f(){}",
        "function g(){}",
        "function h(){ (f || g)() }",
        "h()"
    );
    assertPureCallsMarked(source, ImmutableList.of("(f || g)", "h"));
  }

  public void testCallFunctionFOrGViaHook() throws Exception {
    String source = lines(
        "function f(){}",
        "function g(){}",
        "function h(){ (false ? f : g)() }",
        "h()"
    );
    assertPureCallsMarked(source, ImmutableList.of("(f : g)", "h"));
  }

  public void testCallFunctionForGorH() throws Exception {
    String source = lines(
        "function f(){}",
        "function g(){}",
        "function h(){}",
        "function i(){ (false ? f : (g || h))() }",
        "i()"
    );
    assertPureCallsMarked(source, ImmutableList.of("(f : (g || h))", "i"));
  }

  public void testCallFunctionForGWithSideEffects() throws Exception {
    String source = lines(
        "var x = 0;",
        "function f(){x = 10}",
        "function g(){}",
        "function h(){ (f || g)() }",
        "function i(){ (g || f)() }",
        "function j(){ (f || f)() }",
        "function k(){ (g || g)() }",
        "h(); i(); j(); k()"
    );
    assertPureCallsMarked(source, ImmutableList.of("(g || g)", "k"));
  }

  public void testCallFunctionFOrGViaHookWithSideEffects() throws Exception {
    String source = lines(
        "var x = 0;",
        "function f(){x = 10}",
        "function g(){}",
        "function h(){ (false ? f : g)() }",
        "function i(){ (false ? g : f)() }",
        "function j(){ (false ? f : f)() }",
        "function k(){ (false ? g : g)() }",
        "h(); i(); j(); k()"
    );

    assertPureCallsMarked(source, ImmutableList.of("(g : g)", "k"));
  }

  public void testCallRegExpWithSideEffects() throws Exception {
    String source = lines(
        "var x = 0;",
        "function k(){(/a/).exec('')}",
        "k()"
    );

    regExpHaveSideEffects = true;
    assertNoPureCalls(source);
    regExpHaveSideEffects = false;
    assertPureCallsMarked(source, ImmutableList.of(
        "REGEXP STRING exec", "k"));
  }

  public void testAnonymousFunction1() throws Exception {
    assertPureCallsMarked("(function (){})();", ImmutableList.of("FUNCTION"));
  }

  public void testAnonymousFunction2() throws Exception {
    String source = "(Error || function (){})();";

    assertPureCallsMarked(source, ImmutableList.of("(Error || FUNCTION)"));
  }

  public void testAnonymousFunction3() throws Exception {
    String source = "var a = (Error || function (){})();";

    assertPureCallsMarked(source, ImmutableList.of("(Error || FUNCTION)"));
  }

  // Indirect complex function definitions aren't yet supported.
  public void testAnonymousFunction4() throws Exception {
    String source = lines(
        "var a = (Error || function (){});",
        "a();"
    );

    // This should be "(Error || FUNCTION)" but isn't.
    assertNoPureCalls(source);
  }

  public void testClassMethod1() {
    this.mode = TypeInferenceMode.NEITHER;
    String source = "class C { m() { alert(1); } }; (new C).m();";
    assertNoPureCalls(source);
  }

  public void testClassMethod2() {
    this.mode = TypeInferenceMode.NEITHER;
    String source = "class C { m() { } }; (new C).m();";
    assertPureCallsMarked(source, ImmutableList.of("NEW STRING m"));
  }

  public void testClassMethod3() {
    this.mode = TypeInferenceMode.NEITHER;
    String source = "class C { m1() { } m2() { this.m1(); }}; (new C).m2();";
    assertPureCallsMarked(source, ImmutableList.of("this.m1", "NEW STRING m2"));
  }

  public void testFunctionProperties1() throws Exception {
    String source = lines(
        "/** @constructor */",
        "function F() { this.bar; }",
        "function g() {",
        "  this.bar = function() { alert(3); };",
        "}",
        "var x = new F();",
        "g.call(x);",
        "x.bar();"
    );
    assertPureCallsMarked(
        source,
        ImmutableList.of("F"),
        compiler -> {
          Node lastRoot = compiler.getRoot();
          Node call = findQualifiedNameNode("g.call", lastRoot).getParent();
          assertEquals(
              new Node.SideEffectFlags().clearAllFlags().setMutatesArguments().valueOf(),
              call.getSideEffectFlags());
        });
  }

  public void testCallCache() throws Exception {
    String source = lines(
        "var valueFn = function() {};",
        "goog.reflect.cache(externObj, \"foo\", valueFn)"
    );
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

  public void testCallCache_withKeyFn() throws Exception {
    String source = lines(
        "var valueFn = function(v) { return v };",
        "var keyFn = function(v) { return v };",
        "goog.reflect.cache(externObj, \"foo\", valueFn, keyFn)"
    );
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

  public void testCallCache_anonymousFn() throws Exception {
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

  public void testCallCache_anonymousFn_hasSideEffects() throws Exception {
    String source = lines(
        "var x = 0;",
        "goog.reflect.cache(externObj, \"foo\", function(v) { return (x+=1) })"
    );
    assertNoPureCalls(
        source,
        compiler -> {
          Node lastRoot = compiler.getRoot().getLastChild();
          Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
          assertThat(call.isNoSideEffectsCall()).isFalse();
          assertThat(call.mayMutateGlobalStateOrThrow()).isTrue();
        });
  }

  public void testCallCache_hasSideEffects() throws Exception {
    String source = lines(
        "var x = 0;",
        "var valueFn = function() { return (x+=1); };",
        "goog.reflect.cache(externObj, \"foo\", valueFn)"
    );
    assertNoPureCalls(
        source,
        compiler -> {
          Node lastRoot = compiler.getRoot().getLastChild();
          Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
          assertThat(call.isNoSideEffectsCall()).isFalse();
          assertThat(call.mayMutateGlobalStateOrThrow()).isTrue();
        });
  }

  public void testCallCache_withKeyFn_hasSideEffects() throws Exception {
    String source = lines(
        "var x = 0;",
        "var keyFn = function(v) { return (x+=1) };",
        "var valueFn = function(v) { return v };",
        "goog.reflect.cache(externObj, \"foo\", valueFn, keyFn)"
    );
    assertNoPureCalls(
        source,
        compiler -> {
          Node lastRoot = compiler.getRoot().getLastChild();
          Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
          assertThat(call.isNoSideEffectsCall()).isFalse();
          assertThat(call.mayMutateGlobalStateOrThrow()).isTrue();
        });
  }

  public void testCallCache_propagatesSideEffects() throws Exception {
    String source = lines(
        "var valueFn = function(x) { return x * 2; };",
        "var helper = function(x) { return goog.reflect.cache(externObj, x, valueFn); };",
        "helper(10);"
    );
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

  void assertNoPureCalls(String source) {
    assertPureCallsMarked(source, ImmutableList.<String>of(), null);
  }

  void assertNoPureCalls(String source, Postcondition post) {
    assertPureCallsMarked(source, ImmutableList.<String>of(), post);
  }

  void assertPureCallsMarked(String source, List<String> expected) {
    assertPureCallsMarked(source, expected, null);
  }

  void assertPureCallsMarked(String source, final List<String> expected, final Postcondition post) {
    testSame(
        srcs(source),
        postcondition(
            compiler -> {
              assertEquals(expected, noSideEffectCalls);
              if (post != null) {
                post.verify(compiler);
              }
            }));
  }

  void checkLocalityOfMarkedCalls(String source, final List<String> expected) {
    testSame(srcs(source));
    assertEquals(expected, localResultCalls);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new NoSideEffectCallEnumerator(compiler);
  }
}
