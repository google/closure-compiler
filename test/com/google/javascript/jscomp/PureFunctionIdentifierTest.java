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
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link PureFunctionIdentifier}
 *
 * @author johnlenz@google.com (John Lenz)
 */

public final class PureFunctionIdentifierTest extends CompilerTestCase {
  private static final List<String> NO_PURE_CALLS = ImmutableList.<String>of();

  List<String> noSideEffectCalls = new ArrayList<>();
  List<String> localResultCalls = new ArrayList<>();

  boolean regExpHaveSideEffects = true;

  private static final String TEST_EXTERNS = LINE_JOINER.join(
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
      "var goog = {reflect: {}};",
      "goog.reflect.cache = function(a, b, c, opt_d) {};",


      "/** @nosideeffects */",
      "externObj.prototype.duplicateExternFunc = function() {}",
      "externObj2.prototype.duplicateExternFunc = function() {}",

      "externObj.prototype['weirdDefinition'] = function() {}"
  );

  public PureFunctionIdentifierTest() {
    super(CompilerTypeTestCase.DEFAULT_EXTERNS + TEST_EXTERNS);
    enableTypeCheck();
  }

  @Override
  protected int getNumRepetitions() {
    // run pass once.
    return 1;
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    noSideEffectCalls.clear();
    localResultCalls.clear();
    regExpHaveSideEffects = true;
  }

  public void testIssue303() throws Exception {
    String source = LINE_JOINER.join(
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
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testIssue303b() throws Exception {
    String source = LINE_JOINER.join(
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
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testAnnotationInExterns_new1() throws Exception {
    assertPureCallsMarked("externSENone()", ImmutableList.of("externSENone"));
  }

  public void testAnnotationInExterns_new2() throws Exception {
    assertPureCallsMarked("externSEThis()", NO_PURE_CALLS);
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
        "function f() { new externObjSEThis() };" +
        "f();",
        ImmutableList.of("externObjSEThis", "f"));
  }

  public void testAnnotationInExterns_new6() throws Exception {
    // While "externObjSEThisMethod" has modifies "this"
    // it does not have global side-effects with "this" is
    // a known local value.
    // TODO(johnlenz): "f" is side-effect free but we need
    // to propagate that "externObjSEThisMethod" is modifying
    // a local object.
    String source = LINE_JOINER.join(
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
    String source = LINE_JOINER.join(
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
    String source = LINE_JOINER.join(
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
    String source = LINE_JOINER.join(
        "function f(x) {",
        "  x = new externObjSEThis(); ",
        "  x.externObjSEThisMethod('') ",
        "};",
        "f(g);"
    );
    assertPureCallsMarked(source, ImmutableList.of("externObjSEThis"));
  }

  public void testAnnotationInExterns_new10() throws Exception {
    String source = LINE_JOINER.join(
        "function f() {",
        "  new externObjSEThis().externObjSEThisMethod2('') ",
        "};",
        "f();"
    );
    assertPureCallsMarked(source,
        ImmutableList.of("externObjSEThis", "NEW STRING externObjSEThisMethod2", "f"));
  }

  public void testAnnotationInExterns1() throws Exception {
    assertPureCallsMarked("externSef1()", NO_PURE_CALLS);
  }

  public void testAnnotationInExterns2() throws Exception {
    assertPureCallsMarked("externSef2()", NO_PURE_CALLS);
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
    assertPureCallsMarked("externObj.sef1()", NO_PURE_CALLS);
  }

  public void testNamespaceAnnotationInExterns2() throws Exception {
    assertPureCallsMarked("externObj.nsef1()", ImmutableList.of("externObj.nsef1"));
  }

  public void testNamespaceAnnotationInExterns3() throws Exception {
    assertPureCallsMarked("externObj.nsef2()", ImmutableList.of("externObj.nsef2"));
  }

  public void testNamespaceAnnotationInExterns4() throws Exception {
    assertPureCallsMarked("externObj.partialFn()", NO_PURE_CALLS);
  }

  public void testNamespaceAnnotationInExterns5() throws Exception {
    // Test that adding a second definition for a partially defined
    // function doesn't make us think that the function has no side
    // effects.
    String templateSrc = "var o = {}; o.<fnName> = function(){}; o.<fnName>()";

    // Ensure that functions with name != "partialFn" get marked.
    assertPureCallsMarked(
        templateSrc.replace("<fnName>", "notPartialFn"), ImmutableList.of("o.notPartialFn"));

    assertPureCallsMarked(templateSrc.replace("<fnName>", "partialFn"), NO_PURE_CALLS);
  }

  public void testNamespaceAnnotationInExterns6() throws Exception {
    assertPureCallsMarked("externObj.partialSharedFn()", NO_PURE_CALLS);
  }

  public void testConstructorAnnotationInExterns1() throws Exception {
    assertPureCallsMarked("new externSefConstructor()", NO_PURE_CALLS);
  }

  public void testConstructorAnnotationInExterns2() throws Exception {
    String source = LINE_JOINER.join(
        "var a = new externSefConstructor();",
        "a.sefFnOfSefObj()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testConstructorAnnotationInExterns3() throws Exception {
    String source = LINE_JOINER.join(
        "var a = new externSefConstructor();",
        "a.nsefFnOfSefObj()");
    assertPureCallsMarked(source, ImmutableList.of("a.nsefFnOfSefObj"));
  }

  public void testConstructorAnnotationInExterns4() throws Exception {
    String source = LINE_JOINER.join(
        "var a = new externSefConstructor();",
        "a.externShared()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testConstructorAnnotationInExterns5() throws Exception {
    assertPureCallsMarked("new externNsefConstructor()", ImmutableList.of("externNsefConstructor"));
  }

  public void testConstructorAnnotationInExterns6() throws Exception {
    String source = LINE_JOINER.join(
        "var a = new externNsefConstructor();",
        "a.sefFnOfNsefObj()");
    assertPureCallsMarked(source, ImmutableList.of("externNsefConstructor"));
  }

  public void testConstructorAnnotationInExterns7() throws Exception {
    String source = LINE_JOINER.join(
        "var a = new externNsefConstructor();",
        "a.nsefFnOfNsefObj()");
    assertPureCallsMarked(source, ImmutableList.of("externNsefConstructor", "a.nsefFnOfNsefObj"));
  }

  public void testConstructorAnnotationInExterns8() throws Exception {
    String source = LINE_JOINER.join(
        "var a = new externNsefConstructor();",
        "a.externShared()");
    assertPureCallsMarked(source, ImmutableList.of("externNsefConstructor"));
  }

  public void testSharedFunctionName1() throws Exception {
    String source = LINE_JOINER.join(
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
      assertPureCallsMarked("var a; " +
                       "if (true) {" +
                       "  a = new externNsefConstructor()" +
                       "} else {" +
                       "  a = new externNsefConstructor2()" +
                       "}" +
                       "a.externShared()",
                       ImmutableList.of("externNsefConstructor",
                                        "externNsefConstructor2"));
    } else {
      assertPureCallsMarked("var a; " +
                       "if (true) {" +
                       "  a = new externNsefConstructor()" +
                       "} else {" +
                       "  a = new externNsefConstructor2()" +
                       "}" +
                       "a.externShared()",
                       ImmutableList.of("externNsefConstructor",
                                        "externNsefConstructor2",
                                        "a.externShared"));
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
    assertPureCallsMarked("propWithAnnotatedStubAfter('a');", NO_PURE_CALLS);
  }

  public void testAnnotationInExternStubs4() throws Exception {
    // An externs definition with a stub that differs from the declaration.
    // Verify our assumption is valid about this.
    String externs = LINE_JOINER.join(
        "/**@constructor*/function externObj5(){}",

        "externObj5.prototype.propWithAnnotatedStubAfter = function(s) {};",

        "/**",
        " * @param {string} s id.",
        " * @return {string}",
        " * @nosideeffects",
        " */",
        "externObj5.prototype.propWithAnnotatedStubAfter;");

    testSame(externs,
        "o.prototype.propWithAnnotatedStubAfter",
        TypeValidator.DUP_VAR_DECLARATION_TYPE_MISMATCH, false);
    assertThat(noSideEffectCalls).isEmpty();
    noSideEffectCalls.clear();
  }

  public void testAnnotationInExternStubs5() throws Exception {
    // An externs definition with a stub that differs from the declaration.
    // Verify our assumption is valid about this.
    String externs = LINE_JOINER.join(
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

    testSame(externs,
        "o.prototype.propWithAnnotatedStubAfter",
        TypeValidator.DUP_VAR_DECLARATION, false);
    assertEquals(NO_PURE_CALLS, noSideEffectCalls);
    noSideEffectCalls.clear();
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
        "function g(x) { x.foo = 3; }" /* to suppress missing property */ +
        prefix + "return externObj.foo" + suffix, expected);
  }

  public void testNoSideEffectsSimple2() throws Exception {
    regExpHaveSideEffects = false;
    String source = LINE_JOINER.join(
        "function f() {",
        "  return ''.replace(/xyz/g, '');",
        "}",
        "f()");
    assertPureCallsMarked(source, ImmutableList.of("STRING  STRING replace", "f"));
  }

  public void testNoSideEffectsSimple3() throws Exception {
    regExpHaveSideEffects = false;
    String source = LINE_JOINER.join(
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
    checkLocalityOfMarkedCalls(prefix + "return externObj" + suffix, NO_PURE_CALLS);
    checkLocalityOfMarkedCalls(
        "function inner(x) { x.foo = 3; }" /* to suppress missing property */ +
        prefix + "return externObj.foo" + suffix, NO_PURE_CALLS);
  }

  /**
   * Note that this works because object literals are always seen as local according to {@link
   * NodeUtil#evaluatesToLocalValue}
   */
  public void testReturnLocalityTaintLiteralWithGlobal() {
    // return empty object literal.  This is completely local
    String source = LINE_JOINER.join(
        "function f() { return {} }",
        "f();"
    );
    checkLocalityOfMarkedCalls(source, ImmutableList.of("f"));
    // return obj literal with global taint.
    source = LINE_JOINER.join(
            "var global = new Object();",
            "function f() { return {'asdf': global} }",
            "f();"
        );
    checkLocalityOfMarkedCalls(source, ImmutableList.of("f"));
  }

  public void testReturnLocalityMultipleDefinitionsSameName() {
    String source = LINE_JOINER.join(
            "var global = new Object();",
            "A.func = function() {return global}", // return global (taintsReturn)
            "B.func = function() {return 1; }", // returns local
            "C.func();");
    checkLocalityOfMarkedCalls(source, ImmutableList.<String>of());
  }

  public void testExternCalls() throws Exception {
    String prefix = "function f(){";
    String suffix = "} f()";

    assertPureCallsMarked(prefix + "externNsef1()" + suffix,
                     ImmutableList.of("externNsef1", "f"));
    assertPureCallsMarked(prefix + "externObj.nsef1()" + suffix,
                     ImmutableList.of("externObj.nsef1", "f"));

    assertPureCallsMarked(prefix + "externSef1()" + suffix, NO_PURE_CALLS);
    assertPureCallsMarked(prefix + "externObj.sef1()" + suffix, NO_PURE_CALLS);
  }

  public void testApply() throws Exception {
    String source = LINE_JOINER.join(
        "function f() {return 42}",
        "f.apply()");
    assertPureCallsMarked(source, ImmutableList.of("f.apply"));
  }

  public void testCall() throws Exception {
    String source = LINE_JOINER.join(
        "function f() {return 42}",
        "f.call()");
    assertPureCallsMarked(source, ImmutableList.of("f.call"));
  }

  public void testApplyToUnknownDefinition() throws Exception {
    String source = LINE_JOINER.join(
        "var dict = {'func': function() {}};",
        "function f() { var s = dict['func'];}",
        "f.apply()"
    );
    assertPureCallsMarked(source, ImmutableList.of("f.apply"));

    // Not marked becuase the definition cannot be found so unknown side effects.
    source = LINE_JOINER.join(
        "var dict = {'func': function() {}};",
        "function f() { var s = dict['func'].apply();}",
        "f.apply()"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);

    // Not marked becuase the definition cannot be found so unknown side effects.
    source = LINE_JOINER.join(
        "var pure = function() {};",
        "var dict = {'func': function() {}};",
        "function f() { var s = (dict['func'] || pure)();}",
        "f()"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);

    // Not marked becuase the definition cannot be found so unknown side effects.
    source = LINE_JOINER.join(
        "var pure = function() {};"
            , "var dict = {'func': function() {}};"
            , "function f() { var s = (condition ? dict['func'] : pure)();}"
            , "f()"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testInference1() throws Exception {
    String source = LINE_JOINER.join(
        "function f() {return g()}",
        "function g() {return 42}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("g", "f"));
  }

  public void testInference2() throws Exception {
    String source = LINE_JOINER.join(
        "var a = 1;",
        "function f() {g()}",
        "function g() {a=2}",
        "f()"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testInference3() throws Exception {
    String source = LINE_JOINER.join(
        "var f = function() {return g()};",
        "var g = function() {return 42};",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("g", "f"));
  }

  public void testInference4() throws Exception {
    String source = LINE_JOINER.join(
        "var a = 1;" +
            "var f = function() {g()};",
        "var g = function() {a=2};",
        "f()"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testInference5() throws Exception {
    String source = LINE_JOINER.join(
        "var goog = {};",
        "goog.f = function() {return goog.g()};",
        "goog.g = function() {return 42};",
        "goog.f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("goog.g", "goog.f"));
  }

  public void testInference6() throws Exception {
    String source = LINE_JOINER.join(
        "var a = 1;",
        "var goog = {};",
        "goog.f = function() {goog.g()};",
        "goog.g = function() {a=2};",
        "goog.f()"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testLocalizedSideEffects1() throws Exception {
    // Returning a function that contains a modification of a local
    // is not a global side-effect.
    String source = LINE_JOINER.join(
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
    String source = LINE_JOINER.join(
        "function f() {",
        "  var x = {foo : 0}; (function() {x.foo++})();",
        "}",
        "f()"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testLocalizedSideEffects3() throws Exception {
    // A local that might be assigned a global value and whose properties
    // are modified must be considered a global side-effect.
    String source = LINE_JOINER.join(
        "var g = {foo:1};",
        "function f() {var x = g; x.foo++};",
        "f();"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testLocalizedSideEffects4() throws Exception {
    // An array is an local object, assigning a local array is not a global
    // side-effect.
    String source = LINE_JOINER.join(
        "function f() {var x = []; x[0] = 1;}",
        "f()");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects5() throws Exception {
    // Assigning a local alias of a global is a global
    // side-effect.
    String source = LINE_JOINER.join(
        "var g = [];",
        "function f() {var x = g; x[0] = 1;};",
        "f()"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testLocalizedSideEffects6() throws Exception {
    // Returning a local object that has been modified
    // is not a global side-effect.
    String source = LINE_JOINER.join(
        "function f() {",
        "  var x = {}; x.foo = 1; return x;",
        "}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects7() throws Exception {
    // Returning a local object that has been modified
    // is not a global side-effect.
    String source = LINE_JOINER.join(
        "/** @constructor A */ function A() {};",
        "function f() {",
        "  var a = []; a[1] = 1; return a;",
        "}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects8() throws Exception {
    // Returning a local object that has been modified
    // is not a global side-effect.
    // TODO(johnlenz): Not yet. Propagate local object information.
    String source = LINE_JOINER.join(
        "/** @constructor A */ function A() {};",
        "function f() {",
        "  var a = new A; a.foo = 1; return a;",
        "}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("A"));
  }

  public void testLocalizedSideEffects9() throws Exception {
    // Returning a local object that has been modified
    // is not a global side-effect.
    // TODO(johnlenz): Not yet. Propagate local object information.
    String source = LINE_JOINER.join(
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
    String source = LINE_JOINER.join(
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
    // Calling a function of a local object that taints this.
    String source = LINE_JOINER.join(
        "/** @constructor */ function A() {}",
        "A.prototype.update = function() { this.x = 1; };",
        "/** @constructor */ function B() { ",
        "  this.a_ = new A();",
        "}",
        "B.prototype.updateA = function() {",
        "  var b = this.a_;",
        "  b.update();",
        "};",
        "var x = new B();",
        "x.updateA();"
    );
    assertPureCallsMarked(source, ImmutableList.of("A", "B"));
  }

  public void testLocalizedSideEffects12() throws Exception {
    // An array is an local object, assigning a local array is not a global
    // side-effect. This tests the behavior if the access is in a block scope.
    String source = LINE_JOINER.join(
        "function f() {var x = []; { x[0] = 1; } }",
        "f()");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testUnaryOperators1() throws Exception {
    String source = LINE_JOINER.join(
        "function f() {var x = 1; x++}",
        "f()");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testUnaryOperators2() throws Exception {
    String source = LINE_JOINER.join(
        "var x = 1;",
        "function f() {x++}",
        "f()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testUnaryOperators3() throws Exception {
    String source = LINE_JOINER.join(
        "function f() {var x = {foo : 0}; x.foo++}",
        "f()");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testUnaryOperators4() throws Exception {
    String source = LINE_JOINER.join(
        "var x = {foo : 0};",
        "function f() {x.foo++}",
        "f()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testUnaryOperators5() throws Exception {
    String source = LINE_JOINER.join(
        "function f(x) {x.foo++}",
        "f({foo : 0})");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testDeleteOperator1() throws Exception {
    String source = LINE_JOINER.join(
        "var x = {};",
        "function f() {delete x}",
        "f()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testDeleteOperator2() throws Exception {
    String source = LINE_JOINER.join(
        "function f() {var x = {}; delete x}",
        "f()");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testOrOperator1() throws Exception {
    String source = LINE_JOINER.join(
        "var f = externNsef1 || externNsef2;",
        "f()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testOrOperator2() throws Exception {
    String source = LINE_JOINER.join(
        "var f = function(){} || externNsef2;",
        "f()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testOrOperator3() throws Exception {
    String source = LINE_JOINER.join(
        "var f = externNsef2 || function(){};",
        "f()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testOrOperators4() throws Exception {
    String source = LINE_JOINER.join(
        "var f = function(){} || function(){};",
        "f()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testAndOperator1() throws Exception {
    String source = LINE_JOINER.join(
        "var f = externNsef1 && externNsef2;",
        "f()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testAndOperator2() throws Exception {
    String source = LINE_JOINER.join(
        "var f = function(){} && externNsef2;",
        "f()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testAndOperator3() throws Exception {
    String source = LINE_JOINER.join(
        "var f = externNsef2 && function(){};",
        "f()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testAndOperators4() throws Exception {
    String source = LINE_JOINER.join(
        "var f = function(){} && function(){};",
        "f()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testHookOperator1() throws Exception {
    String source = LINE_JOINER.join(
        "var f = true ? externNsef1 : externNsef2;",
        "f()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testHookOperator2() throws Exception {
    String source = LINE_JOINER.join(
        "var f = true ? function(){} : externNsef2;",
        "f()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testHookOperator3() throws Exception {
    String source = LINE_JOINER.join(
        "var f = true ? externNsef2 : function(){};",
        "f()");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testHookOperators4() throws Exception {
    String source = LINE_JOINER.join(
        "var f = true ? function(){} : function(){};",
        "f()");
    assertPureCallsMarked(source, ImmutableList.<String>of("f"));
  }

  public void testHookOperators5() throws Exception {
    String source = LINE_JOINER.join(
        "var f = String.prototype.trim ? function(str){return str} : function(){};",
        "f()");
    assertPureCallsMarked(source, ImmutableList.<String>of("f"));
  }

  public void testHookOperators6() throws Exception {
    String source = LINE_JOINER.join(
        "var f = yyy ? function(str){return str} : xxx ? function() {} : function(){};",
        "f()");
    assertPureCallsMarked(source, ImmutableList.<String>of("f"));
  }

  public void testThrow1() throws Exception {
    String source = LINE_JOINER.join(
        "function f(){throw Error()};",
        "f()");
    assertPureCallsMarked(source, ImmutableList.<String>of("Error"));
  }

  public void testThrow2() throws Exception {
    String source = LINE_JOINER.join(
        "/**@constructor*/function A(){throw Error()};",
        "function f(){return new A()}",
        "f()");
    assertPureCallsMarked(source, ImmutableList.<String>of("Error"));
  }

  public void testAssignmentOverride() throws Exception {
    String source = LINE_JOINER.join(
        "/**@constructor*/function A(){}",
        "A.prototype.foo = function(){};",
        "var a = new A;",
        "a.foo();");
    assertPureCallsMarked(source, ImmutableList.of("A", "a.foo"));

    // Ideally inline aliases takes care of this.
    String sourceOverride = LINE_JOINER.join(
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
    String source = CompilerTypeTestCase.CLOSURE_DEFS + LINE_JOINER.join(
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
    String source = CompilerTypeTestCase.CLOSURE_DEFS + LINE_JOINER.join(
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
    String source = CompilerTypeTestCase.CLOSURE_DEFS + LINE_JOINER.join(
        "var globalVar = 1;",
        "A.f = function() {globalVar = 2;};",
        "A.f = function() {};",
        "function sideEffectCaller() { A.f() };",
        "sideEffectCaller();"
    );
    // Can't tell which f is being called so it assumes both.
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testAmbiguousDefinitionsCall() throws Exception {
    String source = CompilerTypeTestCase.CLOSURE_DEFS + LINE_JOINER.join(
        "var globalVar = 1;",
        "A.f = function() {globalVar = 2;};",
        "A.f = function() {};",
        "function sideEffectCaller() { A.f.call(null); };",
        "sideEffectCaller();"
    );
    // Can't tell which f is being called so it assumes both.
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testAmbiguousDefinitionsAllPropagationTypes() throws Exception {
    String source = CompilerTypeTestCase.CLOSURE_DEFS + LINE_JOINER.join(
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
    String source = CompilerTypeTestCase.CLOSURE_DEFS + LINE_JOINER.join(
        "var globalVar = 1;",
        "A.modifiesThis = function() { this.x = 5; };",
        "/**@constructor*/function Constructor() { Constructor.modifiesThis.call(this); };",
        "Constructor.prototype.modifiesThis = function() {};",
        "new Constructor();",
        "A.modifiesThis();"
    );
    // Can't tell which modifiesThis is being called so it assumes both.
    assertPureCallsMarked(source, ImmutableList.<String>of("Constructor"));
  }

  public void testAmbiguousDefinitionsBothCallThis() throws Exception {
    String source =
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "// Mutates argument",
            "A.a = function(argument) {",
            "  argument.x = 2;",
            "};",
            "// No side effects",
            "B.a = function() {};",
            "var b = function(x) {C.a(x)};",
            "b({});");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testAmbiguousDefinitionsMutatesLocalArgument() throws Exception {
    String source =
        LINE_JOINER.join(
            "// Mutates argument",
            "A.a = function(argument) {",
            "  argument.x = 2;",
            "};",
            "// No side effects",
            "B.a = function() {};",
            "var b = function() {",
            "  C.a({});",
            "};",
            "b();");
    assertPureCallsMarked(source, ImmutableList.of("C.a", "b"));
  }

  public void testAmbiguousExternDefinitions() {
    assertPureCallsMarked("x.duplicateExternFunc()", NO_PURE_CALLS);

    // nsef1 is defined as no side effect in the externs.
    String source = LINE_JOINER.join(
        "var global = 1;",
        // Overwrite the @nosideeffects with this side effect
        "A.nsef1 = function () {global = 2;};",
        "externObj.nsef1();"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  /**
   * Test bug where the FunctionInformation for "A.x" and "a" were separate causing
   * .x() calls to appear pure because the global side effect was only registed for the function
   * linked to "a".
   */
  public void testAmbiguousDefinitionsDoubleDefinition() {
    String source = LINE_JOINER.join(
        "var global = 1;",
        "A.x = function a() { global++; }",
        "B.x = function() {}",
        "B.x();"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testAmbiguousDefinitionsDoubleDefinition2() {
    String source = LINE_JOINER.join(
        "var global = 1;",
        "A.x = function a() { global++; }",
        "a = function() {}",
        "B.x(); a();"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testAmbiguousDefinitionsDoubleDefinition3() {
    String source = LINE_JOINER.join(
        "var global = 1;",
        "A.x = function a() {}",
        "a = function() { global++; }",
        "B.x(); a();"
    );
    assertPureCallsMarked(source, ImmutableList.of("B.x"));
  }

  public void testAmbiguousDefinitionsDoubleDefinition4() {
    String source = LINE_JOINER.join(
        "var global = 1;",
        "A.x = function a() {}",
        "B.x = function() { global++; }",
        "B.x(); a();"
    );
    assertPureCallsMarked(source, ImmutableList.of("a"));
  }

  public void testAmbiguousDefinitionsDoubleDefinition5() {
    String source = LINE_JOINER.join(
        "var global = 1;",
        "A.x = cond ? function a() { global++ } : function b() {}",
        "B.x = function() { global++; }",
        "B.x(); a(); b();"
    );
    assertPureCallsMarked(source, ImmutableList.of("b"));
  }

  public void testCallBeforeDefinition() throws Exception {
    assertPureCallsMarked("f(); function f(){}", ImmutableList.of("f"));
    assertPureCallsMarked("var a = {}; a.f(); a.f = function (){}", ImmutableList.of("a.f"));
  }

  public void testConstructorThatModifiesThis1() throws Exception {
    String source = LINE_JOINER.join(
        "/**@constructor*/function A(){this.foo = 1}",
        "function f() {return new A}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("A", "f"));
  }

  public void testConstructorThatModifiesThis2() throws Exception {
    String source = LINE_JOINER.join(
        "/**@constructor*/function A(){this.foo()}",
        "A.prototype.foo = function(){this.data=24};",
        "function f() {return new A}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("A", "f"));
  }

  public void testConstructorThatModifiesThis3() throws Exception {
    // test chained
    String source = LINE_JOINER.join(
        "/**@constructor*/function A(){this.foo()}",
        "A.prototype.foo = function(){this.bar()};",
        "A.prototype.bar = function(){this.data=24};",
        "function f() {return new A}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("A", "f"));
  }

  public void testConstructorThatModifiesThis4() throws Exception {
    String source = LINE_JOINER.join(
        "/**@constructor*/function A(){foo.call(this)}",
        "function foo(){this.data=24};",
        "function f() {return new A}",
        "f()"
    );
    assertPureCallsMarked(source, ImmutableList.of("A", "f"));
  }

  public void testConstructorThatModifiesGlobal1() throws Exception {
    String source = LINE_JOINER.join(
        "var b = 0;",
        "/**@constructor*/function A(){b=1};",
        "function f() {return new A}",
        "f()"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testConstructorThatModifiesGlobal2() throws Exception {
    String source = LINE_JOINER.join(
        "/**@constructor*/function A(){this.foo()}",
        "A.prototype.foo = function(){b=1};",
        "function f() {return new A}",
        "f()"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testCallFunctionThatModifiesThis() throws Exception {
    String source = LINE_JOINER.join(
        "/**@constructor*/function A(){}" ,
            "A.prototype.foo = function(){this.data=24};" ,
            "function f(){var a = new A; return a}" ,
            "function g(){var a = new A; a.foo(); return a}" ,
            "f(); g()"
    );
    assertPureCallsMarked(source, ImmutableList.of("A", "A", "f"));
  }

  public void testMutatesArguments1() throws Exception {
    String source = LINE_JOINER.join(
        "function f(x) { x.y = 1; }",
        "f({});");
    assertPureCallsMarked(source, ImmutableList.of("f"));
  }

  public void testMutatesArguments2() throws Exception {
    String source = LINE_JOINER.join(
        "function f(x) { x.y = 1; }",
        "f(window);");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testMutatesArguments3() throws Exception {
    // We could do better here with better side-effect propagation.
    String source = LINE_JOINER.join(
        "function f(x) { x.y = 1; }",
        "function g(x) { f(x); }",
        "g({});");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testMutatesArguments4() throws Exception {
    String source = LINE_JOINER.join(
        "function f(x) { x.y = 1; }",
        "function g(x) { f({}); x.y = 1; }",
        "g({});");
    assertPureCallsMarked(source, ImmutableList.of("f", "g"));
  }

  public void testMutatesArguments5() throws Exception {
    String source = LINE_JOINER.join(
        "function f(x) {",
        "  function g() {",
        "    x.prop = 5;",
        "  }",
        "  g();",
        "}",
        "f(window);");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testMutatesArgumentsArray1() throws Exception {
    String source = LINE_JOINER.join(
        "function f(x) { arguments[0] = 1; }",
        "f({});");
    assertPureCallsMarked(source, ImmutableList.<String>of("f"));
  }

  public void testMutatesArgumentsArray2() throws Exception {
    // We could be smarter here.
    String source = LINE_JOINER.join(
        "function f(x) { arguments[0].y = 1; }",
        "f({});");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testMutatesArgumentsArray3() throws Exception {
    String source = LINE_JOINER.join(
        "function f(x) { arguments[0].y = 1; }",
        "f(x);");
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testCallFunctionFOrG() throws Exception {
    String source = LINE_JOINER.join(
        "function f(){}",
        "function g(){}",
        "function h(){ (f || g)() }",
        "h()"
    );
    assertPureCallsMarked(source, ImmutableList.of("(f || g)", "h"));
  }

  public void testCallFunctionFOrGViaHook() throws Exception {
    String source = LINE_JOINER.join(
        "function f(){}",
        "function g(){}",
        "function h(){ (false ? f : g)() }",
        "h()"
    );
    assertPureCallsMarked(source, ImmutableList.of("(f : g)", "h"));
  }

  public void testCallFunctionForGorH() throws Exception {
    String source = LINE_JOINER.join(
        "function f(){}",
        "function g(){}",
        "function h(){}",
        "function i(){ (false ? f : (g || h))() }",
        "i()"
    );
    assertPureCallsMarked(source, ImmutableList.of("(f : (g || h))", "i"));
  }

  public void testCallFunctionForGWithSideEffects() throws Exception {
    String source = LINE_JOINER.join(
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
    String source = LINE_JOINER.join(
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
    String source = LINE_JOINER.join(
        "var x = 0;",
        "function k(){(/a/).exec('')}",
        "k()"
    );

    regExpHaveSideEffects = true;
    assertPureCallsMarked(source, NO_PURE_CALLS);
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
    String source = LINE_JOINER.join(
        "var a = (Error || function (){});",
        "a();"
    );

    // This should be "(Error || FUNCTION)" but isn't.
    assertPureCallsMarked(source, NO_PURE_CALLS);
  }

  public void testFunctionProperties1() throws Exception {
    String source = LINE_JOINER.join(
        "/** @constructor */",
        "function F() {}",
        "function g() {",
        "  this.bar = function() { alert(3); };",
        "}",
        "var x = new F();",
        "g.call(x);",
        "x.bar();"
    );
    assertPureCallsMarked(source, ImmutableList.of("F"));

    Node lastRoot = getLastCompiler().getRoot();
    Node call = findQualifiedNameNode("g.call", lastRoot).getParent();
    assertEquals(
        new Node.SideEffectFlags()
        .clearAllFlags().setMutatesArguments().valueOf(),
        call.getSideEffectFlags());
  }

  public void testCallCache() throws Exception {
    String source = LINE_JOINER.join(
        "var valueFn = function() {};",
        "goog.reflect.cache(externObj, \"foo\", valueFn)"
    );
    assertPureCallsMarked(source, ImmutableList.of("goog.reflect.cache"));
    Node lastRoot = getLastCompiler().getRoot().getLastChild();
    Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
    assertThat(call.isNoSideEffectsCall()).isTrue();
    assertThat(call.mayMutateGlobalStateOrThrow()).isFalse();
  }

  public void testCallCache_withKeyFn() throws Exception {
    String source = LINE_JOINER.join(
        "var valueFn = function(v) { return v };",
        "var keyFn = function(v) { return v };",
        "goog.reflect.cache(externObj, \"foo\", valueFn, keyFn)"
    );
    assertPureCallsMarked(source, ImmutableList.of("goog.reflect.cache"));
    Node lastRoot = getLastCompiler().getRoot().getLastChild();
    Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
    assertThat(call.isNoSideEffectsCall()).isTrue();
    assertThat(call.mayMutateGlobalStateOrThrow()).isFalse();
  }

  public void testCallCache_anonymousFn() throws Exception {
    String source = "goog.reflect.cache(externObj, \"foo\", function(v) { return v })";
    assertPureCallsMarked(source, ImmutableList.of("goog.reflect.cache"));
    Node lastRoot = getLastCompiler().getRoot().getLastChild();
    Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
    assertThat(call.isNoSideEffectsCall()).isTrue();
    assertThat(call.mayMutateGlobalStateOrThrow()).isFalse();
  }

  public void testCallCache_anonymousFn_hasSideEffects() throws Exception {
    String source = LINE_JOINER.join(
        "var x = 0;",
        "goog.reflect.cache(externObj, \"foo\", function(v) { return (x+=1) })"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
    Node lastRoot = getLastCompiler().getRoot().getLastChild();
    Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
    assertThat(call.isNoSideEffectsCall()).isFalse();
    assertThat(call.mayMutateGlobalStateOrThrow()).isTrue();
  }

  public void testCallCache_hasSideEffects() throws Exception {
    String source = LINE_JOINER.join(
        "var x = 0;",
        "var valueFn = function() { return (x+=1); };",
        "goog.reflect.cache(externObj, \"foo\", valueFn)"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
    Node lastRoot = getLastCompiler().getRoot().getLastChild();
    Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
    assertThat(call.isNoSideEffectsCall()).isFalse();
    assertThat(call.mayMutateGlobalStateOrThrow()).isTrue();
  }

  public void testCallCache_withKeyFn_hasSideEffects() throws Exception {
    String source = LINE_JOINER.join(
        "var x = 0;",
        "var keyFn = function(v) { return (x+=1) };",
        "var valueFn = function(v) { return v };",
        "goog.reflect.cache(externObj, \"foo\", valueFn, keyFn)"
    );
    assertPureCallsMarked(source, NO_PURE_CALLS);
    Node lastRoot = getLastCompiler().getRoot().getLastChild();
    Node call = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
    assertThat(call.isNoSideEffectsCall()).isFalse();
    assertThat(call.mayMutateGlobalStateOrThrow()).isTrue();
  }

  public void testCallCache_propagatesSideEffects() throws Exception {
    String source = LINE_JOINER.join(
        "var valueFn = function(x) { return x * 2; };",
        "var helper = function(x) { return goog.reflect.cache(externObj, x, valueFn); };",
        "helper(10);"
    );
    assertPureCallsMarked(source, ImmutableList.of("goog.reflect.cache", "helper"));
    Node lastRoot = getLastCompiler().getRoot().getLastChild();
    Node cacheCall = findQualifiedNameNode("goog.reflect.cache", lastRoot).getParent();
    assertThat(cacheCall.isNoSideEffectsCall()).isTrue();
    assertThat(cacheCall.mayMutateGlobalStateOrThrow()).isFalse();

    Node helperCall = Iterables.getLast(findQualifiedNameNodes("helper", lastRoot)).getParent();
    assertThat(helperCall.isNoSideEffectsCall()).isTrue();
    assertThat(helperCall.mayMutateGlobalStateOrThrow()).isFalse();
  }

  void assertPureCallsMarked(String source, List<String> expected) {
    assertPureCallsMarked(source, expected, LanguageMode.ECMASCRIPT6);
    assertPureCallsMarked(source, expected, LanguageMode.ECMASCRIPT5);
  }

  void assertPureCallsMarked(String source, List<String> expected, LanguageMode mode) {
    setAcceptedLanguage(mode);
    testSame(source);
    assertEquals(expected, noSideEffectCalls);
    noSideEffectCalls.clear();
  }

  void checkLocalityOfMarkedCalls(String source, List<String> expected) {
    checkLocalityOfMarkedCalls(source, expected, LanguageMode.ECMASCRIPT6);
    checkLocalityOfMarkedCalls(source, expected, LanguageMode.ECMASCRIPT5);
  }

  void checkLocalityOfMarkedCalls(String source, List<String> expected, LanguageMode mode) {
    setAcceptedLanguage(mode);
    testSame(source);
    assertEquals(expected, localResultCalls);
    localResultCalls.clear();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new NoSideEffectCallEnumerator(compiler);
  }

  /**
   * Run PureFunctionIdentifier, then gather a list of calls that are
   * marked as having no side effects.
   */
  private class NoSideEffectCallEnumerator
      extends AbstractPostOrderCallback implements CompilerPass {
    private final Compiler compiler;

    NoSideEffectCallEnumerator(Compiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
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
        return "(" + generateNameString(node.getFirstChild()) +
            " || " + generateNameString(node.getLastChild()) + ")";
      } else if (node.isHook()) {
        return "(" + generateNameString(node.getSecondChild()) +
            " : " + generateNameString(node.getLastChild()) + ")";
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
}
