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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tests for {@link FunctionNames}
 *
 */
public final class FunctionNamesTest extends CompilerTestCase {
  private FunctionNames functionNames;

  public FunctionNamesTest() {
    this.functionNames = null;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    functionNames = new FunctionNames(compiler);
    return functionNames;
  }

  public void testFunctionsNamesAndIds() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    final String jsSource = LINE_JOINER.join(
        "goog.widget = function(str) {",
        "  this.member_fn = function() {};",
        "  local_fn = function() {};",
        "  (function(a){})(1);",
        "}",
        "function foo() {",
        "  function bar() {}",
        "}",
        "literal = {f1 : function(){}, f2 : function(){}};",
        "goog.array.map(arr, function named(){});",
        "goog.array.map(arr, function(){});",
        "named_twice = function quax(){};",
        "recliteral = {l1 : {l2 : function(){}}};",
        "namedliteral = {n1 : function litnamed(){}};",
        "namedrecliteral = {n1 : {n2 : function reclitnamed(){}}};",
        "numliteral = {1 : function(){}};",
        "recnumliteral = {1 : {a : function(){}}};",
        "literalWithShorthand = {shorthandF1(){}, shorthandF2(){}};",
        "class Klass{ constructor(){} method(){}}",
        "KlassExpression = class{ constructor(){} method(){}}",
        "var KlassExpressionToVar = class{ constructor(){} method(){}}",
        "class KlassWithStaticMethod{ static staticMethod(){}}");

    testSame(jsSource);

    final Map<Integer, String> idNameMap = new LinkedHashMap<>();
    int count = 0;
    for (Node f : functionNames.getFunctionNodeList()) {
      int id = functionNames.getFunctionId(f);
      String name = functionNames.getFunctionName(f);
      idNameMap.put(id, name);
      count++;
    }

    assertEquals("Unexpected number of functions", 25, count);

    final Map<Integer, String> expectedMap = new LinkedHashMap<>();

    expectedMap.put(0, "goog.widget.member_fn");
    expectedMap.put(1, "goog.widget::local_fn");
    expectedMap.put(2, "goog.widget::<anonymous>");
    expectedMap.put(3, "goog.widget");
    expectedMap.put(4, "foo::bar");
    expectedMap.put(5, "foo");
    expectedMap.put(6, "literal.f1");
    expectedMap.put(7, "literal.f2");
    expectedMap.put(8, "named");
    expectedMap.put(9, "<anonymous>");
    expectedMap.put(10, "quax");
    expectedMap.put(11, "recliteral.l1.l2");
    expectedMap.put(12, "litnamed");
    expectedMap.put(13, "reclitnamed");
    expectedMap.put(14, "numliteral.__2");
    expectedMap.put(15, "recnumliteral.__3.a");
    expectedMap.put(16, "literalWithShorthand.shorthandF1");
    expectedMap.put(17, "literalWithShorthand.shorthandF2");
    expectedMap.put(18, "Klass.constructor");
    expectedMap.put(19, "Klass.method");
    expectedMap.put(20, "KlassExpression.constructor");
    expectedMap.put(21, "KlassExpression.method");
    expectedMap.put(22, "KlassExpressionToVar.constructor");
    expectedMap.put(23, "KlassExpressionToVar.method");
    expectedMap.put(24, "KlassWithStaticMethod.staticMethod");
    assertEquals("Function id/name mismatch",
                 expectedMap, idNameMap);
  }
}
