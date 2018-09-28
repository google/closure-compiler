/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.javascript.rhino.Node;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CollectFunctionNames}
 *
 */
@RunWith(JUnit4.class)
public final class CollectFunctionNamesTest extends CompilerTestCase {
  private FunctionNames functionNames;

  public CollectFunctionNamesTest() {
    this.functionNames = null;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    CollectFunctionNames pass = new CollectFunctionNames(compiler);
    functionNames = pass.getFunctionNames();
    return pass;
  }

  @Test
  public void testAnonymous1() {
    testFunctionNamesAndIds("(function() {})", "<anonymous>");
  }

  @Test
  public void testAnonymous2() {
    testFunctionNamesAndIds("goog.array.map(arr, function(){});", "<anonymous>");
  }

  @Test
  public void testNestedFunctions() {
    testFunctionNamesAndIds(
        lines(
            "goog.widget = function(str) {",
            "  this.member_fn = function() {};",
            "  local_fn = function() {};",
            "  (function(a){})(1);",
            "}",
            "function foo() {",
            "  function bar() {}",
            "}"),
        new String[] {
          "goog.widget.member_fn",
          "goog.widget::local_fn",
          "goog.widget::<anonymous>",
          "goog.widget",
          "foo::bar",
          "foo"
        });
  }

  @Test
  public void testObjectLiteral1() {
    testFunctionNamesAndIds(
        "literal = {f1 : function(){}, f2 : function(){}};",
        new String[] {"literal.f1", "literal.f2"});
  }

  @Test
  public void testObjectLiteral2() {
    // TODO(lharker): should we output an actual name?
    testFunctionNamesAndIds("var declaredLiteral = {f: function() {}};", "<anonymous>");
  }

  @Test
  public void testNestedObjectLiteral() {
    testFunctionNamesAndIds(
        lines(
            "recliteral = {l1 : {l2 : function(){}}};",
            "namedliteral = {n1 : function litnamed(){}};",
            "namedrecliteral = {n1 : {n2 : function reclitnamed(){}}};"),
        new String[] {
          "recliteral.l1.l2", "litnamed", "reclitnamed",
        });
  }

  @Test
  public void testObjectLiteralWithNumericKey1() {
    testFunctionNamesAndIds("numliteral = {1 : function(){}};", "numliteral.__0");
  }

  @Test
  public void testObjectLiteralWithNumericKey2() {
    testFunctionNamesAndIds(
        lines(
            "numliteral1 = {1 : function(){}};",
            "numliteral2 = {67 : function(){}};",
            "recnumliteral = {1 : {a : function(){}}};"),
        new String[] {"numliteral1.__0", "numliteral2.__1", "recnumliteral.__2.a"});
  }

  @Test
  public void testNamedFunctionExpression1() {
    testFunctionNamesAndIds("goog.array.map(arr, function named(){});", "named");
  }

  @Test
  public void testNamedFunctionExpression2() {
    testFunctionNamesAndIds("named_twice = function quax(){};", "quax");
  }

  @Test
  public void testComputedProperty() {
    testFunctionNamesAndIds("computedPropLiteral = {['c1']: function() {}}", "<anonymous>");
  }

  @Test
  public void testClassDeclaration() {
    testFunctionNamesAndIds(
        "class Klass{ constructor(){} method(){}}",
        new String[] {"Klass.constructor", "Klass.method"});
  }

  @Test
  public void testClassExpression1() {
    testFunctionNamesAndIds(
        "KlassExpression = class{ constructor(){} method(){} }",
        new String[] {"KlassExpression.constructor", "KlassExpression.method"});
  }

  @Test
  public void testClassExpression2() {
    testFunctionNamesAndIds(
        "var KlassExpressionToVar = class{ constructor(){} method(){} }",
        new String[] {"KlassExpressionToVar.constructor", "KlassExpressionToVar.method"});
  }

  @Test
  public void testClassWithStaticMethod() {
    testFunctionNamesAndIds(
        "class KlassWithStaticMethod{ static staticMethod(){} }",
        "KlassWithStaticMethod.staticMethod");
  }

  @Test
  public void testArrowFunctions1() {
    testFunctionNamesAndIds("() => {};", "<anonymous>");
  }

  @Test
  public void testArrowFunctions2() {
    testFunctionNamesAndIds("var arrowFn1 = () => {};", "arrowFn1");
  }

  @Test
  public void testArrowFunctions3() {
    testFunctionNamesAndIds(
        lines(
            "function foo1() {",
            "  var arrowFn2 = () => {};",
            "  () => {};",
            "}"),
        new String[] {
          "foo1::arrowFn2",
          "foo1::<anonymous>",
          "foo1"
        });
  }

  @Test
  public void testObjectLiteralWithMethodShorthand() {
    // TODO(lharker) should we output an actual name?
    testFunctionNamesAndIds(
        "var literalWithShorthand = {shorthandF1(){}, shorthandF2(){}};",
        new String[] {"<anonymous>", "<anonymous>"});

    testFunctionNamesAndIds(
        "literalWithShorthand = {shorthandF1(){}, shorthandF2(){}};",
        new String[] {"literalWithShorthand.shorthandF1", "literalWithShorthand.shorthandF2"});
  }

  /**
   * Runs CollectFunctionNames on the given source and tests that it outputs the correct
   * id -> function name map.
   *
   * @param jsSource Javascript code.
   * @param expectedFunctionNames List of expected function name output, ordered by expected id.
   */
  private void testFunctionNamesAndIds(String jsSource, String[] expectedFunctionNames) {
    testSame(jsSource);

    final Map<Integer, String> idNameMap = new LinkedHashMap<>();
    for (Node f : functionNames.getFunctionNodeList()) {
      int id = functionNames.getFunctionId(f);
      String name = functionNames.getFunctionName(f);
      idNameMap.put(id, name);
    }

    final Map<Integer, String> expectedMap = new LinkedHashMap<>();
    for (int id = 0; id < expectedFunctionNames.length; id++) {
      expectedMap.put(id, expectedFunctionNames[id]);
    }

    assertWithMessage("Function id/name mismatch").that(idNameMap).isEqualTo(expectedMap);
  }

  private void testFunctionNamesAndIds(String jsSource, String expectedFunctionName) {
    testFunctionNamesAndIds(jsSource, new String[] {expectedFunctionName});
  }
}
