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

import static com.google.javascript.jscomp.PureFunctionIdentifier.INVALID_NO_SIDE_EFFECT_ANNOTATION;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.List;

/**
 * Tests for {@link PureFunctionIdentifier}
 *
 * @author johnlenz@google.com (John Lenz)
 */

public class PureFunctionIdentifierTest extends CompilerTestCase {
  List<String> noSideEffectCalls = Lists.newArrayList();
  List<String> localResultCalls = Lists.newArrayList();

  boolean regExpHaveSideEffects = true;

  private static String kExterns =
      CompilerTypeTestCase.DEFAULT_EXTERNS +
      "var window; window.setTimeout;" +
      "/**@nosideeffects*/ function externSENone(){}\n" +

      "/**@modifies{this}*/ function externSEThis(){}\n" +

      "/**@constructor\n" +
      " * @modifies{this}*/\n" +
      "function externObjSEThis(){}\n" +

      "/**\n" +
      " * @param {string} s id.\n" +
      " * @return {string}\n" +
      " * @modifies{this}\n" +
      " */\n" +
      "externObjSEThis.prototype.externObjSEThisMethod = function(s) {};" +

      "/**\n" +
      " * @param {string} s id.\n" +
      " * @return {string}\n" +
      " * @modifies{arguments}\n" +
      " */\n" +
      "externObjSEThis.prototype.externObjSEThisMethod2 = function(s) {};" +

      "/**@nosideeffects*/function Error(){}" +

      "function externSef1(){}" +

      "/**@nosideeffects*/function externNsef1(){}" +

      "var externSef2 = function(){};" +

      "/**@nosideeffects*/var externNsef2 = function(){};" +

      "var externNsef3 = /**@nosideeffects*/function(){};" +

      "var externObj;" +

      "externObj.sef1 = function(){};" +

      "/**@nosideeffects*/externObj.nsef1 = function(){};" +

      "externObj.nsef2 = /**@nosideeffects*/function(){};" +

      "externObj.partialFn;" +

      "externObj.partialSharedFn;" +

      "var externObj2;" +

      "externObj2.partialSharedFn = /**@nosideeffects*/function(){};" +

      "/**@constructor*/function externSefConstructor(){}" +

      "externSefConstructor.prototype.sefFnOfSefObj = function(){};" +

      "externSefConstructor.prototype.nsefFnOfSefObj = " +
      "  /**@nosideeffects*/function(){};" +

      "externSefConstructor.prototype.externShared = function(){};" +

      "/**@constructor\n@nosideeffects*/function externNsefConstructor(){}" +

      "externNsefConstructor.prototype.sefFnOfNsefObj = function(){};" +

      "externNsefConstructor.prototype.nsefFnOfNsefObj = " +
      "  /**@nosideeffects*/function(){};" +

      "externNsefConstructor.prototype.externShared = " +
      "  /**@nosideeffects*/function(){};" +

      "/**@constructor\n@nosideeffects*/function externNsefConstructor2(){}" +
      "externNsefConstructor2.prototype.externShared = " +
      "  /**@nosideeffects*/function(){};" +

      "externNsefConstructor.prototype.sharedPartialSef;" +
      "/**@nosideeffects*/externNsefConstructor.prototype.sharedPartialNsef;" +

      // An externs definition with a stub before.

      "/**@constructor*/function externObj3(){}" +

      "externObj3.prototype.propWithStubBefore;" +

      "/**\n" +
      " * @param {string} s id.\n" +
      " * @return {string}\n" +
      " * @nosideeffects\n" +
      " */\n" +
      "externObj3.prototype.propWithStubBefore = function(s) {};" +

      // useless JsDoc
      "/**\n" +
      " * @see {foo}\n" +
      " */\n" +
      "externObj3.prototype.propWithStubBeforeWithJSDoc;" +

      "/**\n" +
      " * @param {string} s id.\n" +
      " * @return {string}\n" +
      " * @nosideeffects\n" +
      " */\n" +
      "externObj3.prototype.propWithStubBeforeWithJSDoc = function(s) {};" +

      // An externs definition with a stub after.

      "/**@constructor*/function externObj4(){}" +

      "/**\n" +
      " * @param {string} s id.\n" +
      " * @return {string}\n" +
      " * @nosideeffects\n" +
      " */\n" +
      "externObj4.prototype.propWithStubAfter = function(s) {};" +

      "externObj4.prototype.propWithStubAfter;" +

      "/**\n" +
      " * @param {string} s id.\n" +
      " * @return {string}\n" +
      " * @nosideeffects\n" +
      " */\n" +
      "externObj4.prototype.propWithStubAfterWithJSDoc = function(s) {};" +

      // useless JsDoc
      "/**\n" +
      " * @see {foo}\n" +
      " */\n" +
      "externObj4.prototype.propWithStubAfterWithJSDoc;";

  public PureFunctionIdentifierTest() {
    super(kExterns);
    enableTypeCheck(CheckLevel.ERROR);
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
    checkMarkedCalls(
        "/** @constructor */ function F() {" +
        "  var self = this;" +
        "  window.setTimeout(function() {" +
        "    window.location = self.location;" +
        "  }, 0);" +
        "}" +
        "F.prototype.setLocation = function(x) {" +
        "  this.location = x;" +
        "};" +
        "(new F()).setLocation('http://www.google.com/');",
        ImmutableList.<String>of());
  }

  public void testIssue303b() throws Exception {
    checkMarkedCalls(
        "/** @constructor */ function F() {" +
        "  var self = this;" +
        "  window.setTimeout(function() {" +
        "    window.location = self.location;" +
        "  }, 0);" +
        "}" +
        "F.prototype.setLocation = function(x) {" +
        "  this.location = x;" +
        "};" +
        "function x() {" +
        "  (new F()).setLocation('http://www.google.com/');" +
        "} window['x'] = x;",
        ImmutableList.<String>of());
  }

  public void testAnnotationInExterns_new1() throws Exception {
    checkMarkedCalls("externSENone()",
        ImmutableList.of("externSENone"));
  }

  public void testAnnotationInExterns_new2() throws Exception {
    checkMarkedCalls("externSEThis()",
        ImmutableList.<String>of());
  }

  public void testAnnotationInExterns_new3() throws Exception {
    checkMarkedCalls("new externObjSEThis()",
        ImmutableList.of("externObjSEThis"));
  }

  public void testAnnotationInExterns_new4() throws Exception {
    // The entire expression containing "externObjSEThisMethod" is considered
    // side-effect free in this context.

    checkMarkedCalls("new externObjSEThis().externObjSEThisMethod('')",
        ImmutableList.of(
            "externObjSEThis", "NEW STRING externObjSEThisMethod"));
  }

  public void testAnnotationInExterns_new5() throws Exception {
    checkMarkedCalls(
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
    checkMarkedCalls(
        "function f() {" +
        "  new externObjSEThis().externObjSEThisMethod('') " +
        "};" +
        "f();",
         ImmutableList.of(
             "externObjSEThis", "NEW STRING externObjSEThisMethod"));
  }

  public void testAnnotationInExterns_new7() throws Exception {
    // While "externObjSEThisMethod" has modifies "this"
    // it does not have global side-effects with "this" is
    // a known local value.
    checkMarkedCalls(
        "function f() {" +
        "  var x = new externObjSEThis(); " +
        "  x.externObjSEThisMethod('') " +
        "};" +
        "f();",
        ImmutableList.of("externObjSEThis"));
  }

  public void testAnnotationInExterns_new8() throws Exception {
    // "externObjSEThisMethod" modifies "this", the 'this'
    // is not a known local value, so it must be assumed it is to
    // have global side-effects.
    checkMarkedCalls(
        "function f(x) {" +
        "  x.externObjSEThisMethod('') " +
        "};" +
        "f(new externObjSEThis());",
        ImmutableList.of("externObjSEThis"));
  }

  public void testAnnotationInExterns_new9() throws Exception {
    // "externObjSEThisMethod" modifies "this", the 'this'
    // is not a known local value, so it must be assumed it is to
    // have global side-effects.  All possible values of "x" are considered
    // as no intraprocedural data flow is done.
    checkMarkedCalls(
        "function f(x) {" +
        "  x = new externObjSEThis(); " +
        "  x.externObjSEThisMethod('') " +
        "};" +
        "f(g);",
        ImmutableList.of("externObjSEThis"));
  }

  public void testAnnotationInExterns_new10() throws Exception {
    checkMarkedCalls(
        "function f() {" +
        "  new externObjSEThis().externObjSEThisMethod2('') " +
        "};" +
        "f();",
        ImmutableList.of(
            "externObjSEThis", "NEW STRING externObjSEThisMethod2", "f"));
  }

  public void testAnnotationInExterns1() throws Exception {
    checkMarkedCalls("externSef1()", ImmutableList.<String>of());
  }

  public void testAnnotationInExterns2() throws Exception {
    checkMarkedCalls("externSef2()", ImmutableList.<String>of());
  }

  public void testAnnotationInExterns3() throws Exception {
    checkMarkedCalls("externNsef1()", ImmutableList.of("externNsef1"));
  }

  public void testAnnotationInExterns4() throws Exception {
    checkMarkedCalls("externNsef2()", ImmutableList.of("externNsef2"));
  }

  public void testAnnotationInExterns5() throws Exception {
    checkMarkedCalls("externNsef3()", ImmutableList.of("externNsef3"));
  }

  public void testNamespaceAnnotationInExterns1() throws Exception {
    checkMarkedCalls("externObj.sef1()", ImmutableList.<String>of());
  }

  public void testNamespaceAnnotationInExterns2() throws Exception {
    checkMarkedCalls("externObj.nsef1()", ImmutableList.of("externObj.nsef1"));
  }

  public void testNamespaceAnnotationInExterns3() throws Exception {
    checkMarkedCalls("externObj.nsef2()", ImmutableList.of("externObj.nsef2"));
  }

  public void testNamespaceAnnotationInExterns4() throws Exception {
    checkMarkedCalls("externObj.partialFn()",
                     ImmutableList.<String>of());
  }

  public void testNamespaceAnnotationInExterns5() throws Exception {
    // Test that adding a second definition for a partially defined
    // function doesn't make us think that the function has no side
    // effects.
    String templateSrc = "var o = {}; o.<fnName> = function(){}; o.<fnName>()";

    // Ensure that functions with name != "partialFn" get marked.
    checkMarkedCalls(templateSrc.replaceAll("<fnName>", "notPartialFn"),
                     ImmutableList.of("o.notPartialFn"));

    checkMarkedCalls(templateSrc.replaceAll("<fnName>", "partialFn"),
                     ImmutableList.<String>of());
  }

  public void testNamespaceAnnotationInExterns6() throws Exception {
    checkMarkedCalls("externObj.partialSharedFn()",
                     ImmutableList.<String>of());
  }

  public void testConstructorAnnotationInExterns1() throws Exception {
    checkMarkedCalls("new externSefConstructor()",
                     ImmutableList.<String>of());
  }

  public void testConstructorAnnotationInExterns2() throws Exception {
    checkMarkedCalls("var a = new externSefConstructor();" +
                     "a.sefFnOfSefObj()",
                     ImmutableList.<String>of());
  }

  public void testConstructorAnnotationInExterns3() throws Exception {
    checkMarkedCalls("var a = new externSefConstructor();" +
                     "a.nsefFnOfSefObj()",
                     ImmutableList.of("a.nsefFnOfSefObj"));
  }

  public void testConstructorAnnotationInExterns4() throws Exception {
    checkMarkedCalls("var a = new externSefConstructor();" +
                     "a.externShared()",
                     ImmutableList.<String>of());
  }

  public void testConstructorAnnotationInExterns5() throws Exception {
    checkMarkedCalls("new externNsefConstructor()",
                     ImmutableList.of("externNsefConstructor"));
  }

  public void testConstructorAnnotationInExterns6() throws Exception {
    checkMarkedCalls("var a = new externNsefConstructor();" +
                     "a.sefFnOfNsefObj()",
                     ImmutableList.of("externNsefConstructor"));
  }

  public void testConstructorAnnotationInExterns7() throws Exception {
    checkMarkedCalls("var a = new externNsefConstructor();" +
                     "a.nsefFnOfNsefObj()",
                     ImmutableList.of("externNsefConstructor",
                                      "a.nsefFnOfNsefObj"));
  }

  public void testConstructorAnnotationInExterns8() throws Exception {
    checkMarkedCalls("var a = new externNsefConstructor();" +
                     "a.externShared()",
                     ImmutableList.of("externNsefConstructor"));
  }

  public void testSharedFunctionName1() throws Exception {
    checkMarkedCalls("var a; " +
                     "if (true) {" +
                     "  a = new externNsefConstructor()" +
                     "} else {" +
                     "  a = new externSefConstructor()" +
                     "}" +
                     "a.externShared()",
                     ImmutableList.of("externNsefConstructor"));
  }

  public void testSharedFunctionName2() throws Exception {
    // Implementation for both externNsefConstructor and externNsefConstructor2
    // have no side effects.
    boolean broken = true;
    if (broken) {
      checkMarkedCalls("var a; " +
                       "if (true) {" +
                       "  a = new externNsefConstructor()" +
                       "} else {" +
                       "  a = new externNsefConstructor2()" +
                       "}" +
                       "a.externShared()",
                       ImmutableList.of("externNsefConstructor",
                                        "externNsefConstructor2"));
    } else {
      checkMarkedCalls("var a; " +
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
    checkMarkedCalls("o.propWithStubBefore('a');",
        ImmutableList.of("o.propWithStubBefore"));
  }

  public void testAnnotationInExternStubs1b() throws Exception {
    checkMarkedCalls("o.propWithStubBeforeWithJSDoc('a');",
        ImmutableList.of("o.propWithStubBeforeWithJSDoc"));
  }

  public void testAnnotationInExternStubs2() throws Exception {
    checkMarkedCalls("o.propWithStubAfter('a');",
        ImmutableList.of("o.propWithStubAfter"));
  }

  public void testAnnotationInExternStubs2b() throws Exception {
    checkMarkedCalls("o.propWithStubAfter('a');",
        ImmutableList.of("o.propWithStubAfter"));
  }

  public void testAnnotationInExternStubs3() throws Exception {
    checkMarkedCalls("propWithAnnotatedStubAfter('a');",
        ImmutableList.<String>of());
  }

  public void testAnnotationInExternStubs4() throws Exception {
    // An externs definition with a stub that differs from the declaration.
    // Verify our assumption is valid about this.
    String externs =
      "/**@constructor*/function externObj5(){}\n" +

      "externObj5.prototype.propWithAnnotatedStubAfter = function(s) {};\n" +

      "/**\n" +
      " * @param {string} s id.\n" +
      " * @return {string}\n" +
      " * @nosideeffects\n" +
      " */\n" +
      "externObj5.prototype.propWithAnnotatedStubAfter;\n";

    testSame(externs,
        "o.prototype.propWithAnnotatedStubAfter",
        TypeValidator.DUP_VAR_DECLARATION_TYPE_MISMATCH, false);
    assertTrue(noSideEffectCalls.isEmpty());
    noSideEffectCalls.clear();
  }

  public void testAnnotationInExternStubs5() throws Exception {
    // An externs definition with a stub that differs from the declaration.
    // Verify our assumption is valid about this.
    String externs =
      "/**@constructor*/function externObj5(){}\n" +

      "/**\n" +
      " * @param {string} s id.\n" +
      " * @return {string}\n" +
      " * @nosideeffects\n" +
      " */\n" +
      "externObj5.prototype.propWithAnnotatedStubAfter = function(s) {};\n" +

      "/**\n" +
      " * @param {string} s id.\n" +
      " * @return {string}\n" +
      " */\n" +
      "externObj5.prototype.propWithAnnotatedStubAfter;\n";

    List<String> expected = ImmutableList.of();
    testSame(externs,
        "o.prototype.propWithAnnotatedStubAfter",
        TypeValidator.DUP_VAR_DECLARATION, false);
    assertEquals(expected, noSideEffectCalls);
    noSideEffectCalls.clear();
  }

  public void testNoSideEffectsSimple() throws Exception {
    String prefix = "function f(){";
    String suffix = "} f()";
    List<String> expected = ImmutableList.of("f");

    checkMarkedCalls(
        prefix + "" + suffix, expected);
    checkMarkedCalls(
        prefix + "return 1" + suffix, expected);
    checkMarkedCalls(
        prefix + "return 1 + 2" + suffix, expected);

    // local var
    checkMarkedCalls(
        prefix + "var a = 1; return a" + suffix, expected);

    // mutate local var
    checkMarkedCalls(
        prefix + "var a = 1; a = 2; return a" + suffix, expected);
    checkMarkedCalls(
        prefix + "var a = 1; a = 2; return a + 1" + suffix, expected);

    // read from obj literal
    checkMarkedCalls(
        prefix + "var a = {foo : 1}; return a.foo" + suffix, expected);
    checkMarkedCalls(
        prefix + "var a = {foo : 1}; return a.foo + 1" + suffix, expected);

    // read from extern
    checkMarkedCalls(
        prefix + "return externObj" + suffix, expected);
    checkMarkedCalls(
        "function g(x) { x.foo = 3; }" /* to suppress missing property */ +
        prefix + "return externObj.foo" + suffix, expected);
  }

  public void testResultLocalitySimple() throws Exception {
    String prefix = "var g; function f(){";
    String suffix = "} f()";
    List<String> expected = ImmutableList.of("f");
    List<String> notExpected = ImmutableList.of();

    // no return
    checkLocalityOfMarkedCalls(
        prefix + "" + suffix, expected);
    // simple return expressions
    checkLocalityOfMarkedCalls(
        prefix + "return 1" + suffix, expected);
    checkLocalityOfMarkedCalls(
        prefix + "return 1 + 2" + suffix, expected);

    // global result
    checkLocalityOfMarkedCalls(
        prefix + "return g" + suffix, notExpected);

    // multiple returns
    checkLocalityOfMarkedCalls(
        prefix + "return 1; return 2" + suffix, expected);
    checkLocalityOfMarkedCalls(
        prefix + "return 1; return g" + suffix, notExpected);

    // local var, not yet.
    checkLocalityOfMarkedCalls(
        prefix + "var a = 1; return a" + suffix, notExpected);

    // mutate local var, not yet.
    checkLocalityOfMarkedCalls(
        prefix + "var a = 1; a = 2; return a" + suffix, notExpected);
    checkLocalityOfMarkedCalls(
        prefix + "var a = 1; a = 2; return a + 1" + suffix, expected);

    // read from obj literal
    checkLocalityOfMarkedCalls(
        prefix + "return {foo : 1}.foo" + suffix,
        notExpected);
    checkLocalityOfMarkedCalls(
        prefix + "var a = {foo : 1}; return a.foo" + suffix,
        notExpected);

    // read from extern
    checkLocalityOfMarkedCalls(
        prefix + "return externObj" + suffix, notExpected);
    checkLocalityOfMarkedCalls(
        "function inner(x) { x.foo = 3; }" /* to suppress missing property */ +
        prefix + "return externObj.foo" + suffix, notExpected);
  }

  public void testExternCalls() throws Exception {
    String prefix = "function f(){";
    String suffix = "} f()";

    checkMarkedCalls(prefix + "externNsef1()" + suffix,
                     ImmutableList.of("externNsef1", "f"));
    checkMarkedCalls(prefix + "externObj.nsef1()" + suffix,
                     ImmutableList.of("externObj.nsef1", "f"));

    checkMarkedCalls(prefix + "externSef1()" + suffix,
                     ImmutableList.<String>of());
    checkMarkedCalls(prefix + "externObj.sef1()" + suffix,
                     ImmutableList.<String>of());
  }

  public void testApply() throws Exception {
    checkMarkedCalls("function f() {return 42}" +
                     "f.apply()",
                     ImmutableList.of("f.apply"));
  }

  public void testCall() throws Exception {
    checkMarkedCalls("function f() {return 42}" +
                     "f.call()",
                     ImmutableList.of("f.call"));
  }

  public void testInference1() throws Exception {
    checkMarkedCalls("function f() {return g()}" +
                     "function g() {return 42}" +
                     "f()",
                     ImmutableList.of("g", "f"));
  }

  public void testInference2() throws Exception {
    checkMarkedCalls("var a = 1;" +
                     "function f() {g()}" +
                     "function g() {a=2}" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testInference3() throws Exception {
    checkMarkedCalls("var f = function() {return g()};" +
                     "var g = function() {return 42};" +
                     "f()",
                     ImmutableList.of("g", "f"));
  }

  public void testInference4() throws Exception {
    checkMarkedCalls("var a = 1;" +
                     "var f = function() {g()};" +
                     "var g = function() {a=2};" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testInference5() throws Exception {
    checkMarkedCalls("var goog = {};" +
                     "goog.f = function() {return goog.g()};" +
                     "goog.g = function() {return 42};" +
                     "goog.f()",
                     ImmutableList.of("goog.g", "goog.f"));
  }

  public void testInference6() throws Exception {
    checkMarkedCalls("var a = 1;" +
                     "var goog = {};" +
                     "goog.f = function() {goog.g()};" +
                     "goog.g = function() {a=2};" +
                     "goog.f()",
                     ImmutableList.<String>of());
  }

  public void testLocalizedSideEffects1() throws Exception {
    // Returning a function that contains a modification of a local
    // is not a global side-effect.
    checkMarkedCalls("function f() {" +
                     "  var x = {foo : 0}; return function() {x.foo++};" +
                     "}" +
                     "f()",
                     ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects2() throws Exception {
    // Calling a function that contains a modification of a local
    // is a global side-effect (the value has escaped).
    checkMarkedCalls("function f() {" +
                     "  var x = {foo : 0}; (function() {x.foo++})();" +
                     "}" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testLocalizedSideEffects3() throws Exception {
    // A local that might be assigned a global value and whose properties
    // are modified must be considered a global side-effect.
    checkMarkedCalls("var g = {foo:1}; function f() {var x = g; x.foo++}" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testLocalizedSideEffects4() throws Exception {
    // An array is an local object, assigning a local array is not a global
    // side-effect.
    checkMarkedCalls("function f() {var x = []; x[0] = 1;}" +
                     "f()",
                     ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects5() throws Exception {
    // Assigning a local alias of a global is a global
    // side-effect.
    checkMarkedCalls("var g = [];function f() {var x = g; x[0] = 1;}" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testLocalizedSideEffects6() throws Exception {
    // Returning a local object that has been modified
    // is not a global side-effect.
    checkMarkedCalls("function f() {" +
                     "  var x = {}; x.foo = 1; return x;" +
                     "}" +
                     "f()",
                     ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects7() throws Exception {
    // Returning a local object that has been modified
    // is not a global side-effect.
    checkMarkedCalls("/** @constructor A */ function A() {};" +
                     "function f() {" +
                     "  var a = []; a[1] = 1; return a;" +
                     "}" +
                     "f()",
                     ImmutableList.of("f"));
  }

  public void testLocalizedSideEffects8() throws Exception {
    // Returning a local object that has been modified
    // is not a global side-effect.
    // TODO(johnlenz): Not yet. Propagate local object information.
    checkMarkedCalls("/** @constructor A */ function A() {};" +
                     "function f() {" +
                     "  var a = new A; a.foo = 1; return a;" +
                     "}" +
                     "f()",
                     ImmutableList.of("A"));
  }

  public void testLocalizedSideEffects9() throws Exception {
    // Returning a local object that has been modified
    // is not a global side-effect.
    // TODO(johnlenz): Not yet. Propagate local object information.
    checkMarkedCalls("/** @constructor A */ function A() {this.x = 1};" +
                     "function f() {" +
                     "  var a = new A; a.foo = 1; return a;" +
                     "}" +
                     "f()",
                     ImmutableList.of("A"));
  }

  public void testLocalizedSideEffects10() throws Exception {
    // Returning a local object that has been modified
    // is not a global side-effect.
    checkMarkedCalls("/** @constructor A */ function A() {};" +
                     "A.prototype.g = function() {this.x = 1};" +
                     "function f() {" +
                     "  var a = new A; a.g(); return a;" +
                     "}" +
                     "f()",
                     ImmutableList.of("A"));
  }

  public void testLocalizedSideEffects11() throws Exception {
    // Calling a function of a local object that taints this.
    checkMarkedCalls(
        "/** @constructor */ function A() {}" +
        "A.prototype.update = function() { this.x = 1; };" +
        "/** @constructor */ function B() { " +
        "  this.a_ = new A();" +
        "}" +
        "B.prototype.updateA = function() {" +
        "  var b = this.a_;" +
        "  b.update();" +
        "};" +
        "var x = new B();" +
        "x.updateA();",
        ImmutableList.of("A", "B"));
  }

  public void testUnaryOperators1() throws Exception {
    checkMarkedCalls("function f() {var x = 1; x++}" +
                     "f()",
                     ImmutableList.of("f"));
  }

  public void testUnaryOperators2() throws Exception {
    checkMarkedCalls("var x = 1;" +
                     "function f() {x++}" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testUnaryOperators3() throws Exception {
    checkMarkedCalls("function f() {var x = {foo : 0}; x.foo++}" +
                     "f()",
                     ImmutableList.of("f"));
  }

  public void testUnaryOperators4() throws Exception {
    checkMarkedCalls("var x = {foo : 0};" +
                     "function f() {x.foo++}" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testUnaryOperators5() throws Exception {
    checkMarkedCalls("function f(x) {x.foo++}" +
                     "f({foo : 0})",
                     ImmutableList.of("f"));
  }

  public void testDeleteOperator1() throws Exception {
    checkMarkedCalls("var x = {};" +
                     "function f() {delete x}" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testDeleteOperator2() throws Exception {
    checkMarkedCalls("function f() {var x = {}; delete x}" +
                     "f()",
                     ImmutableList.of("f"));
  }

  public void testOrOperator1() throws Exception {
    checkMarkedCalls("var f = externNsef1 || externNsef2;\n" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testOrOperator2() throws Exception {
    checkMarkedCalls("var f = function(){} || externNsef2;\n" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testOrOperator3() throws Exception {
    checkMarkedCalls("var f = externNsef2 || function(){};\n" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testOrOperators4() throws Exception {
    checkMarkedCalls("var f = function(){} || function(){};\n" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testAndOperator1() throws Exception {
    checkMarkedCalls("var f = externNsef1 && externNsef2;\n" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testAndOperator2() throws Exception {
    checkMarkedCalls("var f = function(){} && externNsef2;\n" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testAndOperator3() throws Exception {
    checkMarkedCalls("var f = externNsef2 && function(){};\n" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testAndOperators4() throws Exception {
    checkMarkedCalls("var f = function(){} && function(){};\n" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testHookOperator1() throws Exception {
    checkMarkedCalls("var f = true ? externNsef1 : externNsef2;\n" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testHookOperator2() throws Exception {
    checkMarkedCalls("var f = true ? function(){} : externNsef2;\n" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testHookOperator3() throws Exception {
    checkMarkedCalls("var f = true ? externNsef2 : function(){};\n" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testHookOperators4() throws Exception {
    checkMarkedCalls("var f = true ? function(){} : function(){};\n" +
                     "f()",
                     ImmutableList.<String>of());
  }

  public void testThrow1() throws Exception {
    checkMarkedCalls("function f(){throw Error()};\n" +
                     "f()",
                     ImmutableList.of("Error"));
  }

  public void testThrow2() throws Exception {
    checkMarkedCalls("/**@constructor*/function A(){throw Error()};\n" +
                     "function f(){return new A()}\n" +
                     "f()",
                     ImmutableList.of("Error"));
  }

  public void testAssignmentOverride() throws Exception {
    checkMarkedCalls("/**@constructor*/function A(){}\n" +
                     "A.prototype.foo = function(){};\n" +
                     "var a = new A;\n" +
                     "a.foo();\n",
                     ImmutableList.of("A", "a.foo"));

    checkMarkedCalls("/**@constructor*/function A(){}\n" +
                     "A.prototype.foo = function(){};\n" +
                     "var x = 1\n" +
                     "function f(){x = 10}\n" +
                     "var a = new A;\n" +
                     "a.foo = f;\n" +
                     "a.foo();\n",
                     ImmutableList.of("A"));
  }

  public void testInheritance1() throws Exception {
    String source =
        CompilerTypeTestCase.CLOSURE_DEFS +
        "/**@constructor*/function I(){}\n" +
        "I.prototype.foo = function(){};\n" +
        "I.prototype.bar = function(){this.foo()};\n" +
        "/**@constructor\n@extends {I}*/function A(){};\n" +
        "goog.inherits(A, I)\n;" +
        "/** @override */A.prototype.foo = function(){var data=24};\n" +
        "var i = new I();i.foo();i.bar();\n" +
        "var a = new A();a.foo();a.bar();";

    checkMarkedCalls(source,
                     ImmutableList.of("this.foo", "goog.inherits",
                                      "I", "i.foo", "i.bar",
                                      "A", "a.foo", "a.bar"));
  }

  public void testInheritance2() throws Exception {
    String source =
        CompilerTypeTestCase.CLOSURE_DEFS +
        "/**@constructor*/function I(){}\n" +
        "I.prototype.foo = function(){};\n" +
        "I.prototype.bar = function(){this.foo()};\n" +
        "/**@constructor\n@extends {I}*/function A(){};\n" +
        "goog.inherits(A, I)\n;" +
        "/** @override */A.prototype.foo = function(){this.data=24};\n" +
        "var i = new I();i.foo();i.bar();\n" +
        "var a = new A();a.foo();a.bar();";

    checkMarkedCalls(source, ImmutableList.of("goog.inherits", "I", "A"));
  }

  public void testCallBeforeDefinition() throws Exception {
    checkMarkedCalls("f(); function f(){}",
                     ImmutableList.of("f"));

    checkMarkedCalls("var a = {}; a.f(); a.f = function (){}",
                     ImmutableList.of("a.f"));
  }

  public void testConstructorThatModifiesThis1() throws Exception {
    String source = "/**@constructor*/function A(){this.foo = 1}\n" +
        "function f() {return new A}" +
        "f()";

    checkMarkedCalls(source, ImmutableList.of("A", "f"));
  }

  public void testConstructorThatModifiesThis2() throws Exception {
    String source = "/**@constructor*/function A(){this.foo()}\n" +
        "A.prototype.foo = function(){this.data=24};\n" +
        "function f() {return new A}" +
        "f()";

    checkMarkedCalls(source, ImmutableList.of("A", "f"));
  }

  public void testConstructorThatModifiesThis3() throws Exception {

    // test chained
    String source = "/**@constructor*/function A(){this.foo()}\n" +
        "A.prototype.foo = function(){this.bar()};\n" +
        "A.prototype.bar = function(){this.data=24};\n" +
        "function f() {return new A}" +
        "f()";

    checkMarkedCalls(source, ImmutableList.of("A", "f"));
  }

  public void testConstructorThatModifiesThis4() throws Exception {

    // test ".call" notation.
    String source = "/**@constructor*/function A(){foo.call(this)}\n" +
        "function foo(){this.data=24};\n" +
        "function f() {return new A}" +
        "f()";

    checkMarkedCalls(source, ImmutableList.of("A", "f"));
  }

  public void testConstructorThatModifiesGlobal1() throws Exception {
    String source = "var b = 0;" +
        "/**@constructor*/function A(){b=1};\n" +
        "function f() {return new A}" +
        "f()";

    checkMarkedCalls(source, ImmutableList.<String>of());
  }

  public void testConstructorThatModifiesGlobal2() throws Exception {
    String source = "var b = 0;" +
        "/**@constructor*/function A(){this.foo()}\n" +
        "A.prototype.foo = function(){b=1};\n" +
        "function f() {return new A}" +
        "f()";

    checkMarkedCalls(source, ImmutableList.<String>of());
  }

  public void testCallFunctionThatModifiesThis() throws Exception {
    String source = "/**@constructor*/function A(){}\n" +
        "A.prototype.foo = function(){this.data=24};\n" +
        "function f(){var a = new A; return a}\n" +
        "function g(){var a = new A; a.foo(); return a}\n" +
        "f(); g()";

    checkMarkedCalls(source, ImmutableList.of("A", "A", "f"));
  }

  public void testMutatesArguments1() throws Exception {
    String source = "function f(x) { x.y = 1; }\n" +
        "f({});";
    checkMarkedCalls(source, ImmutableList.of("f"));
  }

  public void testMutatesArguments2() throws Exception {
    String source = "function f(x) { x.y = 1; }\n" +
        "f(window);";
    checkMarkedCalls(source, ImmutableList.<String>of());
  }

  public void testMutatesArguments3() throws Exception {
    // We could do better here with better side-effect propagation.
    String source = "function f(x) { x.y = 1; }\n" +
        "function g(x) { f(x); }\n" +
        "g({});";
    checkMarkedCalls(source, ImmutableList.<String>of());
  }

  public void testMutatesArguments4() throws Exception {
    String source = "function f(x) { x.y = 1; }\n" +
        "function g(x) { f({}); x.y = 1; }\n" +
        "g({});";
    checkMarkedCalls(source, ImmutableList.of("f", "g"));
  }

  public void testMutatesArgumentsArray1() throws Exception {
    // We could be smarter here.
    String source = "function f(x) { arguments[0] = 1; }\n" +
        "f({});";
    checkMarkedCalls(source, ImmutableList.<String>of());
  }

  public void testMutatesArgumentsArray2() throws Exception {
    // We could be smarter here.
    String source = "function f(x) { arguments[0].y = 1; }\n" +
        "f({});";
    checkMarkedCalls(source, ImmutableList.<String>of());
  }

  public void testMutatesArgumentsArray3() throws Exception {
    String source = "function f(x) { arguments[0].y = 1; }\n" +
        "f(x);";
    checkMarkedCalls(source, ImmutableList.<String>of());
  }

  public void testCallFunctionFOrG() throws Exception {
    String source = "function f(){}\n" +
        "function g(){}\n" +
        "function h(){ (f || g)() }\n" +
        "h()";

    checkMarkedCalls(source, ImmutableList.of("(f || g)", "h"));
  }

  public void testCallFunctionFOrGViaHook() throws Exception {
    String source = "function f(){}\n" +
        "function g(){}\n" +
        "function h(){ (false ? f : g)() }\n" +
        "h()";

    checkMarkedCalls(source, ImmutableList.of("(f : g)", "h"));
  }

  public void testCallFunctionForGorH() throws Exception {
    String source = "function f(){}\n" +
        "function g(){}\n" +
        "function h(){}\n" +
        "function i(){ (false ? f : (g || h))() }\n" +
        "i()";

    checkMarkedCalls(source, ImmutableList.of("(f : (g || h))", "i"));
  }

  public void testCallFunctionFOrGWithSideEffects() throws Exception {
    String source = "var x = 0;\n" +
        "function f(){x = 10}\n" +
        "function g(){}\n" +
        "function h(){ (f || g)() }\n" +
        "function i(){ (g || f)() }\n" +
        "function j(){ (f || f)() }\n" +
        "function k(){ (g || g)() }\n" +
        "h(); i(); j(); k()";

    checkMarkedCalls(source, ImmutableList.of("(g || g)", "k"));
  }

  public void testCallFunctionFOrGViaHookWithSideEffects() throws Exception {
    String source = "var x = 0;\n" +
        "function f(){x = 10}\n" +
        "function g(){}\n" +
        "function h(){ (false ? f : g)() }\n" +
        "function i(){ (false ? g : f)() }\n" +
        "function j(){ (false ? f : f)() }\n" +
        "function k(){ (false ? g : g)() }\n" +
        "h(); i(); j(); k()";

    checkMarkedCalls(source, ImmutableList.of("(g : g)", "k"));
  }

  public void testCallRegExpWithSideEffects() throws Exception {
    String source = "var x = 0;\n" +
        "function k(){(/a/).exec('')}\n" +
        "k()";

    regExpHaveSideEffects = true;
    checkMarkedCalls(source, ImmutableList.<String>of());
    regExpHaveSideEffects = false;
    checkMarkedCalls(source, ImmutableList.of(
        "REGEXP STRING exec", "k"));
  }

  public void testAnonymousFunction1() throws Exception {
    String source = "(function (){})();";

    checkMarkedCalls(source, ImmutableList.of(
        "FUNCTION"));
  }

  public void testAnonymousFunction2() throws Exception {
    String source = "(Error || function (){})();";

    checkMarkedCalls(source, ImmutableList.of(
        "(Error || FUNCTION)"));
  }

  public void testAnonymousFunction3() throws Exception {
    String source = "var a = (Error || function (){})();";

    checkMarkedCalls(source, ImmutableList.of(
        "(Error || FUNCTION)"));
  }

  // Indirect complex function definitions aren't yet supported.
  public void testAnonymousFunction4() throws Exception {
    String source = "var a = (Error || function (){});" +
                    "a();";

    // This should be "(Error || FUNCTION)" but isn't.
    checkMarkedCalls(source, ImmutableList.<String>of());
  }

  public void testFunctionProperties1() throws Exception {
    String source =
        "/** @constructor */" +
        "function F() {}" +
        "function g() {" +
        "  this.bar = function() { alert(3); };" +
        "}" +
        "var x = new F();" +
        "g.call(x);" +
        "x.bar();";
    checkMarkedCalls(source, ImmutableList.of("F"));

    Node lastRoot = getLastCompiler().getRoot();
    Node call = findQualifiedNameNode("g.call", lastRoot).getParent();
    assertEquals(
        new Node.SideEffectFlags()
        .clearAllFlags().setMutatesArguments().valueOf(),
        call.getSideEffectFlags());
  }

  public void testInvalidAnnotation1() throws Exception {
    test("/** @nosideeffects */ function foo() {}",
         null, INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testInvalidAnnotation2() throws Exception {
    test("var f = /** @nosideeffects */ function() {}",
         null, INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testInvalidAnnotation3() throws Exception {
    test("/** @nosideeffects */ var f = function() {}",
         null, INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testInvalidAnnotation4() throws Exception {
    test("var f = function() {};" +
         "/** @nosideeffects */ f.x = function() {}",
         null, INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testInvalidAnnotation5() throws Exception {
    test("var f = function() {};" +
         "f.x = /** @nosideeffects */ function() {}",
         null, INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  void checkMarkedCalls(String source, List<String> expected) {
    testSame(source);
    assertEquals(expected, noSideEffectCalls);
    noSideEffectCalls.clear();
  }

  void checkLocalityOfMarkedCalls(String source, List<String> expected) {
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
      SimpleDefinitionFinder defFinder = new SimpleDefinitionFinder(compiler);
      defFinder.process(externs, root);
      PureFunctionIdentifier passUnderTest =
          new PureFunctionIdentifier(compiler, defFinder);
      passUnderTest.process(externs, root);

      // Ensure that debug report computation doesn't crash.
      passUnderTest.getDebugReport();

      NodeTraversal.traverse(compiler, externs, this);
      NodeTraversal.traverse(compiler, root, this);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isNew()) {
        if (!NodeUtil.constructorCallHasSideEffects(n)) {
          noSideEffectCalls.add(generateNameString(n.getFirstChild()));
        }
      } else if (n.isCall()) {
        if (!NodeUtil.functionCallHasSideEffects(n)) {
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
        return "(" + generateNameString(node.getFirstChild().getNext()) +
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
