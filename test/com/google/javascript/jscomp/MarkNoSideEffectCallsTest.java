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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link MarkNoSideEffectCalls}
 *
 */
public final class MarkNoSideEffectCallsTest extends CompilerTestCase {
  List<String> noSideEffectCalls = new ArrayList<>();

  private static String kExterns =
      "function externSef1(){}" +
      "/**@nosideeffects*/function externNsef1(){}" +
      "var externSef2 = function(){};" +
      "/**@nosideeffects*/var externNsef2 = function(){};" +
      "var externNsef3 = /**@nosideeffects*/function(){};" +
      "var externObj;" +
      "externObj.sef1 = function(){};" +
      "/**@nosideeffects*/externObj.nsef1 = function(){};" +
      "externObj.nsef2 = /**@nosideeffects*/function(){};" +
      "externObj.sef2;" +
      "/**@nosideeffects*/externObj.nsef3;";

  public MarkNoSideEffectCallsTest() {
    super(kExterns);
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
  }

  public void testFunctionAnnotation() throws Exception {
    testMarkCalls("/**@nosideeffects*/function f(){}", "f()",
                  ImmutableList.of("f"));
    testMarkCalls("/**@nosideeffects*/var f = function(){};", "f()",
                  ImmutableList.of("f"));
    testMarkCalls("var f = /**@nosideeffects*/function(){};", "f()",
                  ImmutableList.of("f"));
    testMarkCalls("f = /**@nosideeffects*/function(){};", "f()",
                  ImmutableList.of("f"));
    testMarkCalls("/**@nosideeffects*/ f = function(){};", "f()",
                  ImmutableList.of("f"));

    // no annotation
    testMarkCalls("function f(){}", Collections.<String>emptyList());
    testMarkCalls("function f(){} f()", Collections.<String>emptyList());

    // 2 annotations
    testMarkCalls("/**@nosideeffects*/var f = " +
                  "/**@nosideeffects*/function(){};",
                  "f()",
                  ImmutableList.of("f"));
  }

  public void testNamespaceAnnotation() throws Exception {
    testMarkCalls("var o = {}; o.f = /**@nosideeffects*/function(){};",
        "o.f()", ImmutableList.of("o.f"));
    testMarkCalls("var o = {}; o.f = /**@nosideeffects*/function(){};",
        "o.f()", ImmutableList.of("o.f"));
    testMarkCalls("var o = {}; o.f = function(){}; o.f()",
                  Collections.<String>emptyList());
  }

  public void testConstructorAnnotation() throws Exception {
    testMarkCalls("/**@nosideeffects*/function c(){};", "new c",
                  ImmutableList.of("c"));
    testMarkCalls("var c = /**@nosideeffects*/function(){};", "new c",
                  ImmutableList.of("c"));
    testMarkCalls("/**@nosideeffects*/var c = function(){};", "new c",
                  ImmutableList.of("c"));
    testMarkCalls("function c(){}; new c", Collections.<String>emptyList());
  }

  public void testMultipleDefinition() throws Exception {
    testMarkCalls("/**@nosideeffects*/function f(){}" +
                  "/**@nosideeffects*/f = function(){};",
                  "f()",
                  ImmutableList.of("f"));
    testMarkCalls("function f(){}" +
                  "/**@nosideeffects*/f = function(){};",
                  "f()",
                  Collections.<String>emptyList());
    testMarkCalls("/**@nosideeffects*/function f(){}",
                  "f = function(){};" +
                  "f()",
                  Collections.<String>emptyList());
  }

  public void testAssignNoFunction() throws Exception {
    testMarkCalls("/**@nosideeffects*/function f(){}", "f = 1; f()",
                  ImmutableList.of("f"));
    testMarkCalls("/**@nosideeffects*/function f(){}", "f = 1 || 2; f()",
                  Collections.<String>emptyList());
  }

  public void testPrototype() throws Exception {
    testMarkCalls("function c(){};" +
                  "/**@nosideeffects*/c.prototype.g = function(){};",
                  "var o = new c; o.g()",
                  ImmutableList.of("o.g"));
    testMarkCalls("function c(){};" +
                  "/**@nosideeffects*/c.prototype.g = function(){};",
                  "function f(){}" +
                  "var o = new c; o.g(); f()",
                  ImmutableList.of("o.g"));

    // replace o.f with a function that has side effects
    testMarkCalls("function c(){};" +
                  "/**@nosideeffects*/c.prototype.g = function(){};",
                  "var o = new c;" +
                  "o.g = function(){};" +
                  "o.g()",
                  ImmutableList.<String>of());
    // two classes with same property; neither has side effects
    testMarkCalls("function c1(){};" +
                  "/**@nosideeffects*/c1.prototype.f = function(){};" +
                  "function c2(){};" +
                  "/**@nosideeffects*/c2.prototype.f = function(){};",
                  "var o = new c1;" +
                  "o.f()",
                  ImmutableList.of("o.f"));

    // two classes with same property; one has side effects
    testMarkCalls("function c1(){};" +
                  "/**@nosideeffects*/c1.prototype.f = function(){};",
                  "function c2(){};" +
                  "c2.prototype.f = function(){};" +
                  "var o = new c1;" +
                  "o.f()",
                  Collections.<String>emptyList());
  }

  public void testAnnotationInExterns() throws Exception {
    testMarkCalls("externSef1()", Collections.<String>emptyList());
    testMarkCalls("externSef2()", Collections.<String>emptyList());
    testMarkCalls("externNsef1()", ImmutableList.of("externNsef1"));
    testMarkCalls("externNsef2()", ImmutableList.of("externNsef2"));
    testMarkCalls("externNsef3()", ImmutableList.of("externNsef3"));
  }

  public void testNamespaceAnnotationInExterns() throws Exception {
    testMarkCalls("externObj.sef1()", Collections.<String>emptyList());
    testMarkCalls("externObj.sef2()", Collections.<String>emptyList());
    testMarkCalls("externObj.nsef1()", ImmutableList.of("externObj.nsef1"));
    testMarkCalls("externObj.nsef2()", ImmutableList.of("externObj.nsef2"));

    testMarkCalls("externObj.nsef3()", ImmutableList.of("externObj.nsef3"));
  }

  public void testOverrideDefinitionInSource() throws Exception {
    // both have side effects.
    testMarkCalls("var obj = {}; obj.sef1 = function(){}; obj.sef1()",
                  Collections.<String>emptyList());

    // extern has side effects.
    testMarkCalls("var obj = {};" +
                  "/**@nosideeffects*/obj.sef1 = function(){};",
                  "obj.sef1()",
                  Collections.<String>emptyList());

    // override in source also has side effects.
    testMarkCalls("var obj = {}; obj.nsef1 = function(){}; obj.nsef1()",
                  Collections.<String>emptyList());

    // override in source also has no side effects.
    testMarkCalls("var obj = {};" +
                  "/**@nosideeffects*/obj.nsef1 = function(){};",
                  "obj.nsef1()",
                  ImmutableList.of("obj.nsef1"));
  }

  public void testApply1() throws Exception {
    testMarkCalls("/**@nosideeffects*/ var f = function() {}",
                  "f.apply()",
                  ImmutableList.of("f.apply"));
  }

  public void testApply2() throws Exception {
    testMarkCalls("var f = function() {}",
                  "f.apply()",
                  ImmutableList.<String>of());
  }

  public void testCall1() throws Exception {
    testMarkCalls("/**@nosideeffects*/ var f = function() {}",
                  "f.call()",
                  ImmutableList.of("f.call"));
  }

  public void testCall2() throws Exception {
    testMarkCalls("var f = function() {}",
                  "f.call()",
                  ImmutableList.<String>of());
  }

  public void testInvalidAnnotation1() throws Exception {
    testError("/** @nosideeffects */ function foo() {}", INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testInvalidAnnotation2() throws Exception {
    testError("var f = /** @nosideeffects */ function() {}", INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testInvalidAnnotation3() throws Exception {
    testError("/** @nosideeffects */ var f = function() {}", INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testInvalidAnnotation4() throws Exception {
    testError("var f = function() {};" +
         "/** @nosideeffects */ f.x = function() {}",
         INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testInvalidAnnotation5() throws Exception {
    testError("var f = function() {};" +
         "f.x = /** @nosideeffects */ function() {}",
         INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testCallNumber() throws Exception {
    testMarkCalls("", "var x = 1; x();",
                  ImmutableList.<String>of());
  }

  void testMarkCalls(String source, List<String> expected) {
    testMarkCalls("", source, expected);
  }

  void testMarkCalls(
      String extraExterns, String source, List<String> expected) {
    testSame(kExterns + extraExterns, source, null);
    assertEquals(expected, noSideEffectCalls);
    noSideEffectCalls.clear();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new NoSideEffectCallEnumerator(compiler);
  }

  /**
   * Run MarkNoSideEffectCalls, then gather a list of calls that are
   * marked as having no side effects.
   */
  private class NoSideEffectCallEnumerator
      extends AbstractPostOrderCallback implements CompilerPass {
    private final MarkNoSideEffectCalls passUnderTest;
    private final Compiler compiler;

    NoSideEffectCallEnumerator(Compiler compiler) {
      this.passUnderTest = new MarkNoSideEffectCalls(compiler);
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      passUnderTest.process(externs, root);
      NodeTraversal.traverseEs6(compiler, externs, this);
      NodeTraversal.traverseEs6(compiler, root, this);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isNew()) {
        if (!NodeUtil.constructorCallHasSideEffects(n)) {
          noSideEffectCalls.add(n.getFirstChild().getQualifiedName());
        }
      } else if (n.isCall()) {
        if (!NodeUtil.functionCallHasSideEffects(n)) {
          noSideEffectCalls.add(n.getFirstChild().getQualifiedName());
        }
      }
    }
  }
}
