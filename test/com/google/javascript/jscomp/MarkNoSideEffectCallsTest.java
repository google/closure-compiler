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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link MarkNoSideEffectCalls}
 *
 */
@RunWith(JUnit4.class)
public final class MarkNoSideEffectCallsTest extends CompilerTestCase {
  List<String> noSideEffectCalls = new ArrayList<>();

  private static final String EXTERNS =
      "function externSef1(){}"
          + "/**@nosideeffects*/function externNsef1(){}"
          + "var externSef2 = function(){};"
          + "/**@nosideeffects*/var externNsef2 = function(){};"
          + "var externNsef3 = /**@nosideeffects*/function(){};"
          + "var externObj;"
          + "externObj.sef1 = function(){};"
          + "/**@nosideeffects*/externObj.nsef1 = function(){};"
          + "externObj.nsef2 = /**@nosideeffects*/function(){};"
          + "externObj.sef2;"
          + "/**@nosideeffects*/externObj.nsef3;";

  public MarkNoSideEffectCallsTest() {
    super(EXTERNS);
  }

  @Override
  protected int getNumRepetitions() {
    // run pass once.
    return 1;
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    noSideEffectCalls.clear();
  }

  @Test
  public void testFunctionAnnotation() {
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
    testMarkCalls("function f(){}", ImmutableList.of());
    testMarkCalls("function f(){} f()", ImmutableList.of());

    // 2 annotations
    testMarkCalls("/**@nosideeffects*/var f = " +
                  "/**@nosideeffects*/function(){};",
                  "f()",
                  ImmutableList.of("f"));
  }

  @Test
  public void testNamespaceAnnotation() {
    testMarkCalls("var o = {}; o.f = /**@nosideeffects*/function(){};",
        "o.f()", ImmutableList.of("o.f"));
    testMarkCalls("var o = {}; o.f = /**@nosideeffects*/function(){};",
        "o.f()", ImmutableList.of("o.f"));
    testMarkCalls("var o = {}; o.f = function(){}; o.f()", ImmutableList.of());
  }

  @Test
  public void testConstructorAnnotation() {
    testMarkCalls("/**@nosideeffects*/function c(){};", "new c",
                  ImmutableList.of("c"));
    testMarkCalls("var c = /**@nosideeffects*/function(){};", "new c",
                  ImmutableList.of("c"));
    testMarkCalls("/**@nosideeffects*/var c = function(){};", "new c",
                  ImmutableList.of("c"));
    testMarkCalls("function c(){}; new c", ImmutableList.of());
  }

  @Test
  public void testMultipleDefinition() {
    testMarkCalls("/**@nosideeffects*/function f(){}" +
                  "/**@nosideeffects*/f = function(){};",
                  "f()",
                  ImmutableList.of("f"));
    testMarkCalls(
        "function f(){}" + "/**@nosideeffects*/f = function(){};", "f()", ImmutableList.of());
    testMarkCalls(
        "/**@nosideeffects*/function f(){}", "f = function(){};" + "f()", ImmutableList.of());
  }

  @Test
  public void testAssignNoFunction() {
    testMarkCalls("/**@nosideeffects*/function f(){}", "f = 1; f()",
                  ImmutableList.of("f"));
    testMarkCalls("/**@nosideeffects*/function f(){}", "f = 1 || 2; f()", ImmutableList.of());
  }

  @Test
  public void testPrototype() {
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
    testMarkCalls(
        "function c1(){};" + "/**@nosideeffects*/c1.prototype.f = function(){};",
        "function c2(){};" + "c2.prototype.f = function(){};" + "var o = new c1;" + "o.f()",
        ImmutableList.of());
  }

  @Test
  public void testAnnotationInExterns() {
    testMarkCalls("externSef1()", ImmutableList.of());
    testMarkCalls("externSef2()", ImmutableList.of());
    testMarkCalls("externNsef1()", ImmutableList.of("externNsef1"));
    testMarkCalls("externNsef2()", ImmutableList.of("externNsef2"));
    testMarkCalls("externNsef3()", ImmutableList.of("externNsef3"));
  }

  @Test
  public void testNamespaceAnnotationInExterns() {
    testMarkCalls("externObj.sef1()", ImmutableList.of());
    testMarkCalls("externObj.sef2()", ImmutableList.of());
    testMarkCalls("externObj.nsef1()", ImmutableList.of("externObj.nsef1"));
    testMarkCalls("externObj.nsef2()", ImmutableList.of("externObj.nsef2"));

    testMarkCalls("externObj.nsef3()", ImmutableList.of("externObj.nsef3"));
  }

  @Test
  public void testOverrideDefinitionInSource() {
    // both have side effects.
    testMarkCalls("var obj = {}; obj.sef1 = function(){}; obj.sef1()", ImmutableList.of());

    // extern has side effects.
    testMarkCalls(
        "var obj = {};" + "/**@nosideeffects*/obj.sef1 = function(){};",
        "obj.sef1()",
        ImmutableList.of());

    // override in source also has side effects.
    testMarkCalls("var obj = {}; obj.nsef1 = function(){}; obj.nsef1()", ImmutableList.of());

    // override in source also has no side effects.
    testMarkCalls("var obj = {};" +
                  "/**@nosideeffects*/obj.nsef1 = function(){};",
                  "obj.nsef1()",
                  ImmutableList.of("obj.nsef1"));
  }

  @Test
  public void testApply1() {
    testMarkCalls("/**@nosideeffects*/ var f = function() {}",
                  "f.apply()",
                  ImmutableList.of("f.apply"));
  }

  @Test
  public void testApply2() {
    testMarkCalls("var f = function() {}",
                  "f.apply()",
                  ImmutableList.<String>of());
  }

  @Test
  public void testCall1() {
    testMarkCalls("/**@nosideeffects*/ var f = function() {}",
                  "f.call()",
                  ImmutableList.of("f.call"));
  }

  @Test
  public void testCall2() {
    testMarkCalls("var f = function() {}",
                  "f.call()",
                  ImmutableList.<String>of());
  }

  @Test
  public void testCallNumber() {
    testMarkCalls("", "var x = 1; x();",
                  ImmutableList.<String>of());
  }

  void testMarkCalls(String source, List<String> expected) {
    testMarkCalls("", source, expected);
  }

  void testMarkCalls(
      String extraExterns, String source, List<String> expected) {
    testSame(externs(EXTERNS + extraExterns), srcs(source));
    assertThat(noSideEffectCalls).isEqualTo(expected);
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
      NodeTraversal.traverse(compiler, externs, this);
      NodeTraversal.traverse(compiler, root, this);
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
