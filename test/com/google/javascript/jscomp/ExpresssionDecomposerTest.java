/*
 * Copyright 2009 Google Inc.
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

import com.google.common.collect.Sets;
import com.google.javascript.jscomp.ExpressionDecomposer.DecompositionType;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.util.Set;

/**
 * Unit tests for ExpressionDecomposer
 * @author johnlenz@google.com (John Lenz)
 */
public class ExpresssionDecomposerTest extends TestCase {
  // Note: functions "foo" and "goo" are external functions
  // in the helper.

  public void testCanExposeExpression1() {
    // Can't move or decompose some classes of expressions.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "while(foo());", "foo");
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "while(x = goo()&&foo()){}", "foo");
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "while(x += goo()&&foo()){}", "foo");
    
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "do{}while(foo());", "foo");
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "for(;foo(););", "foo");
    // This case could be supported for loops without conditional continues
    // by moving the increment into the loop body.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "for(;;foo());", "foo");
    // FOR initializer could be supported but they never occur
    // as they are normalized away.

    // This is potentially doable but a bit too complex currently.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "switch(1){case foo():;}", "foo");
  }

  public void testCanExposeExpression2() {
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "x = foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "var x = foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "if(foo()){}", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "switch(foo()){}", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "switch(foo()){}", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "function (){ return foo();}", "foo");

    helperCanExposeExpression(
        DecompositionType.MOVABLE, "x = foo() && 1", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "x = foo() || 1", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "x = foo() ? 0 : 1", "foo");
  }

  public void testCanExposeExpression3() {
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x = 0 && foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x = 1 || foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "var x = 1 ? foo() : 0", "foo");

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "goo() && foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x = goo() && foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x += goo() && foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "var x = goo() && foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "if(goo() && foo()){}", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "switch(goo() && foo()){}", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "switch(goo() && foo()){}", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "switch(x = goo() && foo()){}", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE,
        "function (){ return goo() && foo();}", "foo");
  }

  public void testCanExposeExpression4() {
    // 'this' must be preserved in call.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "if (goo.a(1, foo()));", "foo");    
  }

  public void testCanExposeExpression5() {
    // 'this' must be preserved in call.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "if (goo['a'](foo()));", "foo");    
  }

  public void testCanExposeExpression6() {
    // 'this' must be preserved in call.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "z:if (goo.a(1, foo()));", "foo");    
  }
  

  public void testMoveExpression1() {
    // There isn't a reason to do this, but it works.
    helperMoveExpression("foo()", "foo", "var temp_0 = foo(); temp_0;");
  }

  public void testMoveExpression2() {
    helperMoveExpression(
        "x = foo()",
        "foo",
        "var temp_0 = foo(); x = temp_0;");
  }

  public void testMoveExpression3() {
    helperMoveExpression(
        "var x = foo()",
        "foo",
        "var temp_0 = foo(); var x = temp_0;");
  }

  public void testMoveExpression4() {
    helperMoveExpression(
        "if(foo()){}",
        "foo",
        "var temp_0 = foo(); if (temp_0);");
  }

  public void testMoveExpression5() {
    helperMoveExpression(
        "switch(foo()){}",
        "foo",
        "var temp_0 = foo(); switch(temp_0){}");
  }

  public void testMoveExpression6() {
    helperMoveExpression(
        "switch(1 + foo()){}",
        "foo",
        "var temp_0 = foo(); switch(1 + temp_0){}");
  }

  public void testMoveExpression7() {
    helperMoveExpression(
        "function (){ return foo();}",
        "foo",
        "function (){ var temp_0 = foo(); return temp_0;}");
  }

  public void testMoveExpression8() {
    helperMoveExpression(
        "x = foo() && 1",
        "foo",
        "var temp_0 = foo(); x = temp_0 && 1");
  }

  public void testMoveExpression9() {
    helperMoveExpression(
        "x = foo() || 1",
        "foo",
        "var temp_0 = foo(); x = temp_0 || 1");
  }

  public void testMoveExpression10() {
    helperMoveExpression(
        "x = foo() ? 0 : 1",
        "foo",
        "var temp_0 = foo(); x = temp_0 ? 0 : 1");
  }

  /* Decomposition tests. */

  public void testExposeExpression1() {
    helperExposeExpression(
        "x = 0 && foo()",
        "foo",
        "var temp_0; if (temp_0 = 0) temp_0 = foo(); x = temp_0;");
  }

  public void testExposeExpression2() {
    helperExposeExpression(
        "x = 1 || foo()",
        "foo",
        "var temp_0; if (temp_0 = 1); else temp_0 = foo(); x = temp_0;");
  }

  public void testExposeExpression3() {
    helperExposeExpression(
        "var x = 1 ? foo() : 0",
        "foo",
        "var temp_0; if (1) temp_0 = foo(); else temp_0 = 0;var x = temp_0;");
  }

  public void testExposeExpression4() {
    helperExposeExpression(
        "goo() && foo()",
        "foo",
        "if (goo()) foo();");
  }

  public void testExposeExpression5() {
    helperExposeExpression(
        "x = goo() && foo()",
        "foo",
        "var temp_0; if (temp_0 = goo()) temp_0 = foo(); x = temp_0;");
  }

  public void testExposeExpression6() {
    helperExposeExpression(
        "var x = 1 + (goo() && foo())",
        "foo",
        "var temp_0; if (temp_0 = goo()) temp_0 = foo();" +
        "var x = 1 + temp_0;");
  }

  public void testExposeExpression7() {
    helperExposeExpression(
        "if(goo() && foo());",
        "foo",
        "var temp_0;" +
        "if (temp_0 = goo()) temp_0 = foo();" +
        "if(temp_0);");
  }

  public void testExposeExpression8() {
    helperExposeExpression(
        "switch(goo() && foo()){}",
        "foo",
        "var temp_0;" +
        "if (temp_0 = goo()) temp_0 = foo();" +
        "switch(temp_0){}");
  }

  public void testExposeExpression9() {
    helperExposeExpression(
        "switch(1 + goo() + foo()){}",
        "foo",
        "var temp_const_0 = 1 + goo();" +
        "switch(temp_const_0 + foo()){}");
  }

  public void testExposeExpression10() {
    helperExposeExpression(
        "function (){ return goo() && foo();}",
        "foo",
        "function (){" +
          "var temp_0; if (temp_0 = goo()) temp_0 = foo();" +
          "return temp_0;" +
         "}");
  }

  public void testExposeExpression11() {
    // TODO(johnlenz): We really want a constant marking pass.
    // The value "goo" should be constant, but it isn't known to be so.
    helperExposeExpression(
        "if (goo(1, goo(2), (1 ? foo() : 0)));",
        "foo",
        "var temp_const_1 = goo;" +
        "var temp_const_0 = goo(2);" +
        "var temp_2;" +
        "if (1) temp_2 = foo(); else temp_2 = 0;" +
        "if (temp_const_1(1, temp_const_0, temp_2));");
  }
  
  public void testExposePlusEquals() {
    helperExposeExpression(
        "var x = 0; x += foo() + 1",
        "foo",
        "var x = 0; var temp_const_0 = x;" +
        "temp_const_0 += foo() + 1;" +
        "x = temp_const_0;");

    helperExposeExpression(
        "var x = 0; y = (x += foo()) + x",
        "foo",
        "var x = 0; var temp_const_0 = x;" +
        "y = (temp_const_0 += foo(), x=temp_const_0) + x");
  }

  /** Test case helpers. */

  private void helperCanExposeExpression(
      DecompositionType expectedResult,
      String code,
      String fnName
      ) {
    helperCanExposeExpression(expectedResult, code, fnName, null);
  }

  private void helperCanExposeExpression(
      DecompositionType expectedResult,
      String code,
      String fnName,
      Set<String> knownConstants
      ) {
    Compiler compiler = new Compiler();
    if (knownConstants == null) {
      knownConstants = Sets.newHashSet();
    }
    ExpressionDecomposer decomposer = new ExpressionDecomposer(
        compiler, compiler.getUniqueNameIdSupplier(), knownConstants);
    Node tree = parse(compiler, code);
    assertNotNull(tree);

    Node externsRoot = parse(compiler,
        "function goo() {}" +
        "function foo() {}");
    assertNotNull(externsRoot);
    Node mainRoot = tree;

    Node callSite = findCall(tree, fnName);
    assertNotNull("Call to " + fnName + " was not found.", callSite);

    compiler.resetUniqueNameId();
    DecompositionType result = decomposer.canExposeExpression(
        callSite);
    assertEquals(expectedResult, result);
  }

  private void helperExposeExpression(
      String code,
      String fnName,
      String expectedResult
      ) {
    helperExposeExpression(
        code, fnName, expectedResult, null);
  }

  private void helperExposeExpression(
      String code,
      String fnName,
      String expectedResult,
      Set<String> knownConstants
      ) {
    Compiler compiler = new Compiler();
    if (knownConstants == null) {
      knownConstants = Sets.newHashSet();
    }
    ExpressionDecomposer decomposer = new ExpressionDecomposer(
        compiler, compiler.getUniqueNameIdSupplier(), knownConstants);
    decomposer.setTempNamePrefix("temp_");
    Node expectedRoot = parse(compiler, expectedResult);
    Node tree = parse(compiler, code);
    assertNotNull(tree);

    Node externsRoot = new Node(Token.EMPTY);
    Node mainRoot = tree;

    Node callSite = findCall(tree, fnName);
    assertNotNull("Call to " + fnName + " was not found.", callSite);

    DecompositionType result = decomposer.canExposeExpression(callSite);
    assertTrue(result == DecompositionType.DECOMPOSABLE);

    compiler.resetUniqueNameId();
    decomposer.exposeExpression(callSite);
    String explanation = expectedRoot.checkTreeEquals(tree);
    assertNull("\nExpected: " + compiler.toSource(expectedRoot) +
        "\nResult: " + compiler.toSource(tree) +
        "\n" + explanation, explanation);
  }

  private void helperMoveExpression(
      String code,
      String fnName,
      String expectedResult
      ) {
    helperMoveExpression(
        code, fnName, expectedResult, null);
  }

  private void helperMoveExpression(
      String code,
      String fnName,
      String expectedResult,
      Set<String> knownConstants
      ) {
    Compiler compiler = new Compiler();
    if (knownConstants == null) {
      knownConstants = Sets.newHashSet();
    }

    ExpressionDecomposer decomposer = new ExpressionDecomposer(
        compiler, compiler.getUniqueNameIdSupplier(), knownConstants);
    decomposer.setTempNamePrefix("temp_");
    Node expectedRoot = parse(compiler, expectedResult);
    Node tree = parse(compiler, code);
    assertNotNull(tree);

    Node externsRoot = new Node(Token.EMPTY);
    Node mainRoot = tree;

    Node callSite = findCall(tree, fnName);
    assertNotNull("Call to " + fnName + " was not found.", callSite);

    compiler.resetUniqueNameId();
    decomposer.moveExpression(callSite);
    String explanation = expectedRoot.checkTreeEquals(tree);
    assertNull("\nExpected: " + compiler.toSource(expectedRoot) +
        "\nResult: " + compiler.toSource(tree) +
        "\n" + explanation, explanation);
  }


  private static Node findCall(Node n, String name) {
    if (n.getType() == Token.CALL) {
      Node callee = n.getFirstChild();
      if (callee.getType() == Token.NAME
          && callee.getString().equals(name)) {
        return n;
      }
    }

    for (Node c : n.children()) {
      Node result = findCall(c, name);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  private static Node parse(Compiler compiler, String js) {
    Node n = compiler.parseTestCode(js);
    assertEquals(0, compiler.getErrorCount());
    return n;
  }
}
