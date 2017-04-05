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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.ExpressionDecomposer.DecompositionType;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Unit tests for ExpressionDecomposer
 * @author johnlenz@google.com (John Lenz)
 */
public final class ExpressionDecomposerTest extends TestCase {
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
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "for(foo();;);", "foo");

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
        DecompositionType.MOVABLE, "const x = foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "let x = foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "if(foo()){}", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "switch(foo()){}", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "switch(foo()){}", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "function f(){ return foo();}", "foo");

    helperCanExposeExpression(
        DecompositionType.MOVABLE, "x = foo() && 1", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "x = foo() || 1", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "x = foo() ? 0 : 1", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "(function(a){b = a})(foo())", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE,
        "function f(){ throw foo();}", "foo");
  }

  public void testCanExposeExpression3() {
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x = 0 && foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x = 1 || foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "var x = 1 ? foo() : 0", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "const x = 1 ? foo() : 0", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "let x = 1 ? foo() : 0", "foo");

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "goo() && foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x = goo() && foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x += goo() && foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "var x = goo() && foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "const x = goo() && foo()", "foo");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "let x = goo() && foo()", "foo");
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
        "function f(){ return goo() && foo();}", "foo");
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

  public void testCanExposeExpression7() {
    // Verify calls to function expressions are movable.
    helperCanExposeFunctionExpression(
        DecompositionType.MOVABLE,
        "(function(map){descriptions_=map})(\n" +
            "function(){\n" +
                "var ret={};\n" +
                "ret[INIT]='a';\n" +
                "ret[MIGRATION_BANNER_DISMISS]='b';\n" +
                "return ret\n" +
            "}()\n" +
        ");", 2);
  }

  public void testCanExposeExpression8() {
    // Can it be decompose?
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE,
        "HangoutStarter.prototype.launchHangout = function() {\n" +
        "  var self = a.b;\n" +
        "  var myUrl = new goog.Uri(getDomServices_(self).getDomHelper()." +
        "getWindow().location.href);\n" +
        "};",
        "getDomServices_");

    // Verify it is properly expose the target expression.
    helperExposeExpression(
        "HangoutStarter.prototype.launchHangout = function() {\n" +
        "  var self = a.b;\n" +
        "  var myUrl = new goog.Uri(getDomServices_(self).getDomHelper()." +
        "getWindow().location.href);\n" +
        "};",
        "getDomServices_",
        "HangoutStarter.prototype.launchHangout = function() {" +
        "  var self = a.b;" +
        "  var temp_const$jscomp$0 = goog.Uri;" +
        "  var myUrl = new temp_const$jscomp$0(getDomServices_(self)." +
        "      getDomHelper().getWindow().location.href)}");

    // Verify the results can be properly moved.
    helperMoveExpression(
        "HangoutStarter.prototype.launchHangout = function() {" +
        "  var self = a.b;" +
        "  var temp_const$jscomp$0 = goog.Uri;" +
        "  var myUrl = new temp_const$jscomp$0(getDomServices_(self)." +
        "      getDomHelper().getWindow().location.href)}",
        "getDomServices_",
        "HangoutStarter.prototype.launchHangout = function() {" +
        "  var self=a.b;" +
        "  var temp_const$jscomp$0=goog.Uri;" +
        "  var result$jscomp$0=getDomServices_(self);" +
        "  var myUrl=new temp_const$jscomp$0(result$jscomp$0.getDomHelper()." +
        "      getWindow().location.href)}");
  }

  public void testMoveExpression1() {
    // There isn't a reason to do this, but it works.
    helperMoveExpression("foo()", "foo", "var result$jscomp$0 = foo(); result$jscomp$0;");
  }

  public void testMoveExpression2() {
    helperMoveExpression(
        "x = foo()",
        "foo",
        "var result$jscomp$0 = foo(); x = result$jscomp$0;");
  }

  public void testMoveExpression3() {
    helperMoveExpression(
        "var x = foo()",
        "foo",
        "var result$jscomp$0 = foo(); var x = result$jscomp$0;");
  }

  public void testMoveExpression4() {
    helperMoveExpression(
        "const x = foo()",
        "foo",
        "var result$jscomp$0 = foo(); const x = result$jscomp$0;");
  }

    public void testMoveExpression5() {
    helperMoveExpression(
        "let x = foo()",
        "foo",
        "var result$jscomp$0 = foo(); let x = result$jscomp$0;");
  }

  public void testMoveExpression6() {
    helperMoveExpression(
        "if(foo()){}",
        "foo",
        "var result$jscomp$0 = foo(); if (result$jscomp$0);");
  }

  public void testMoveExpression7() {
    helperMoveExpression(
        "switch(foo()){}",
        "foo",
        "var result$jscomp$0 = foo(); switch(result$jscomp$0){}");
  }

  public void testMoveExpression8() {
    helperMoveExpression(
        "switch(1 + foo()){}",
        "foo",
        "var result$jscomp$0 = foo(); switch(1 + result$jscomp$0){}");
  }

  public void testMoveExpression9() {
    helperMoveExpression(
        "function f(){ return foo();}",
        "foo",
        "function f(){ var result$jscomp$0 = foo(); return result$jscomp$0;}");
  }

  public void testMoveExpression10() {
    helperMoveExpression(
        "x = foo() && 1",
        "foo",
        "var result$jscomp$0 = foo(); x = result$jscomp$0 && 1");
  }

  public void testMoveExpression11() {
    helperMoveExpression(
        "x = foo() || 1",
        "foo",
        "var result$jscomp$0 = foo(); x = result$jscomp$0 || 1");
  }

  public void testMoveExpression12() {
    helperMoveExpression(
        "x = foo() ? 0 : 1",
        "foo",
        "var result$jscomp$0 = foo(); x = result$jscomp$0 ? 0 : 1");
  }

  /* Decomposition tests. */

  public void testExposeExpression1() {
    helperExposeExpression(
        "x = 0 && foo()",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = 0) temp$jscomp$0 = foo(); x = temp$jscomp$0;");
  }

  public void testExposeExpression2() {
    helperExposeExpression(
        "x = 1 || foo()",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = 1); else temp$jscomp$0=foo(); x = temp$jscomp$0;");
  }

  public void testExposeExpression3() {
    helperExposeExpression(
        "var x = 1 ? foo() : 0",
        "foo",
        "var temp$jscomp$0;" +
        " if (1) temp$jscomp$0 = foo(); else temp$jscomp$0 = 0;var x = temp$jscomp$0;");
  }

  public void testExposeExpression4() {
    helperExposeExpression(
        "const x = 1 ? foo() : 0",
        "foo",
        "var temp$jscomp$0;" +
        " if (1) temp$jscomp$0 = foo(); else temp$jscomp$0 = 0;const x = temp$jscomp$0;");
  }

  public void testExposeExpression5() {
    helperExposeExpression(
        "let x = 1 ? foo() : 0",
        "foo",
        "var temp$jscomp$0;" +
        " if (1) temp$jscomp$0 = foo(); else temp$jscomp$0 = 0;let x = temp$jscomp$0;");
  }

  public void testExposeExpression6() {
    helperExposeExpression(
        "goo() && foo()",
        "foo",
        "if (goo()) foo();");
  }

  public void testExposeExpression7() {
    helperExposeExpression(
        "x = goo() && foo()",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo(); x = temp$jscomp$0;");
  }

  public void testExposeExpression8() {
    helperExposeExpression(
        "var x = 1 + (goo() && foo())",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();" +
        "var x = 1 + temp$jscomp$0;");
  }

  public void testExposeExpression9() {
    helperExposeExpression(
        "const x = 1 + (goo() && foo())",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();" +
        "const x = 1 + temp$jscomp$0;");
  }

  public void testExposeExpression10() {
    helperExposeExpression(
        "let x = 1 + (goo() && foo())",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();" +
        "let x = 1 + temp$jscomp$0;");
  }

  public void testExposeExpression11() {
    helperExposeExpression(
        "if(goo() && foo());",
        "foo",
        "var temp$jscomp$0;" +
        "if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();" +
        "if(temp$jscomp$0);");
  }

  public void testExposeExpression12() {
    helperExposeExpression(
        "switch(goo() && foo()){}",
        "foo",
        "var temp$jscomp$0;" +
        "if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();" +
        "switch(temp$jscomp$0){}");
  }

  public void testExposeExpression13() {
    helperExposeExpression(
        "switch(1 + goo() + foo()){}",
        "foo",
        "var temp_const$jscomp$0 = 1 + goo();" +
        "switch(temp_const$jscomp$0 + foo()){}");
  }

  public void testExposeExpression14() {
    helperExposeExpression(
        "function f(){ return goo() && foo();}",
        "foo",
        "function f(){" +
          "var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();" +
          "return temp$jscomp$0;" +
         "}");
  }

  public void testExposeExpression15() {
    // TODO(johnlenz): We really want a constant marking pass.
    // The value "goo" should be constant, but it isn't known to be so.
    helperExposeExpression(
        "if (goo(1, goo(2), (1 ? foo() : 0)));",
        "foo",
        "var temp_const$jscomp$1 = goo;" +
        "var temp_const$jscomp$0 = goo(2);" +
        "var temp$jscomp$2;" +
        "if (1) temp$jscomp$2 = foo(); else temp$jscomp$2 = 0;" +
        "if (temp_const$jscomp$1(1, temp_const$jscomp$0, temp$jscomp$2));");
  }

  public void testExposeExpression16() {
    helperExposeExpression(
        "throw bar() && foo();",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = bar()) temp$jscomp$0=foo(); throw temp$jscomp$0;");
  }

  public void testExposeYieldExpression1() {
    helperMoveExpression(
        "function *f() { return { a: yield 1, c: foo(yield 2, yield 3) }; }",
        "yield",
        "function *f() {" +
        "  var result$jscomp$0 = yield 1;" +
        "  return { a: result$jscomp$0, c: foo(yield 2, yield 3) };" +
        "}");

    helperMoveExpression(
        "function *f() {" +
        "  return { a: 0, c: foo(yield 2, yield 3) };" +
        "}",
        "yield",
        "function *f() {" +
        "  var result$jscomp$0 = yield 2;" +
        "  return { a: 0, c: foo(result$jscomp$0, yield 3) };" +
        "}");

    helperMoveExpression(
        "function *f() {" +
        "  return { a: 0, c: foo(1, yield 3) };" +
        "}",
        "yield",
        "function *f() {" +
        "  var result$jscomp$0 = yield 3;" +
        "  return { a: 0, c: foo(1, result$jscomp$0) };" +
        "}");
  }

  public void testExposeYieldExpression2() {
    helperMoveExpression(
        "function *f() { return (yield 1) || (yield 2); }",
        "yield",
        "function *f() {" +
        "  var result$jscomp$0 = yield 1;" +
        "  return result$jscomp$0 || (yield 2);" +
        "}");

    helperExposeExpression(
        "function *f(x) {" +
        "  return x || (yield 2);" +
        "}",
        "yield",
        "function *f(x) {" +
        "  var temp$jscomp$0;" +
        "  if (temp$jscomp$0=x); else temp$jscomp$0 = yield 2;" +
        "  return temp$jscomp$0" +
        "}");
  }

  // Simple name on LHS of assignment-op.
  public void testExposePlusEquals1() {
    helperExposeExpression(
        "var x = 0; x += foo() + 1",
        "foo",
        "var x = 0; var temp_const$jscomp$0 = x;" +
        "x = temp_const$jscomp$0 + (foo() + 1);");

    helperExposeExpression(
        "var x = 0; y = (x += foo()) + x",
        "foo",
        "var x = 0; var temp_const$jscomp$0 = x;" +
        "y = (x = temp_const$jscomp$0 + foo()) + x");
  }

  // Structure on LHS of assignment-op.
  public void testExposePlusEquals2() {
    helperExposeExpression(
        "var x = {}; x.a += foo() + 1",
        "foo",
        "var x = {}; var temp_const$jscomp$0 = x;" +
        "var temp_const$jscomp$1 = temp_const$jscomp$0.a;" +
        "temp_const$jscomp$0.a = temp_const$jscomp$1 + (foo() + 1);");

    helperExposeExpression(
        "var x = {}; y = (x.a += foo()) + x.a",
        "foo",
        "var x = {}; var temp_const$jscomp$0 = x;" +
        "var temp_const$jscomp$1 = temp_const$jscomp$0.a;" +
        "y = (temp_const$jscomp$0.a = temp_const$jscomp$1 + foo()) + x.a");
  }

  // Constant object on LHS of assignment-op.
  public void testExposePlusEquals3() {
    helperExposeExpression(
        "/** @const */ var XX = {};\n" +
        "XX.a += foo() + 1",
        "foo",
        "var XX = {}; var temp_const$jscomp$0 = XX.a;" +
        "XX.a = temp_const$jscomp$0 + (foo() + 1);");

    helperExposeExpression(
        "var XX = {}; y = (XX.a += foo()) + XX.a",
        "foo",
        "var XX = {}; var temp_const$jscomp$0 = XX.a;" +
        "y = (XX.a = temp_const$jscomp$0 + foo()) + XX.a");
  }

  // Function all on LHS of assignment-op.
  public void testExposePlusEquals4() {
    helperExposeExpression(
        "var x = {}; goo().a += foo() + 1",
        "foo",
        "var x = {};" +
        "var temp_const$jscomp$0 = goo();" +
        "var temp_const$jscomp$1 = temp_const$jscomp$0.a;" +
        "temp_const$jscomp$0.a = temp_const$jscomp$1 + (foo() + 1);");

    helperExposeExpression(
        "var x = {}; y = (goo().a += foo()) + goo().a",
        "foo",
        "var x = {};" +
        "var temp_const$jscomp$0 = goo();" +
        "var temp_const$jscomp$1 = temp_const$jscomp$0.a;" +
        "y = (temp_const$jscomp$0.a = temp_const$jscomp$1 + foo()) + goo().a");
  }

  // Test multiple levels
  public void testExposePlusEquals5() {
    helperExposeExpression(
        "var x = {}; goo().a.b += foo() + 1",
        "foo",
        "var x = {};" +
        "var temp_const$jscomp$0 = goo().a;" +
        "var temp_const$jscomp$1 = temp_const$jscomp$0.b;" +
        "temp_const$jscomp$0.b = temp_const$jscomp$1 + (foo() + 1);");

    helperExposeExpression(
        "var x = {}; y = (goo().a.b += foo()) + goo().a",
        "foo",
        "var x = {};" +
        "var temp_const$jscomp$0 = goo().a;" +
        "var temp_const$jscomp$1 = temp_const$jscomp$0.b;" +
        "y = (temp_const$jscomp$0.b = temp_const$jscomp$1 + foo()) + goo().a");
  }

  public void testExposeObjectLit1() {
    // Validate that getter and setters methods are see as side-effect
    // free and that values can move past them.  We don't need to be
    // concerned with exposing the getter or setter here but the
    // decomposer does not have a method of exposing properties only variables.
    helperMoveExpression(
        "var x = {get a() {}, b: foo()};",
        "foo",
        "var result$jscomp$0=foo();var x = {get a() {}, b: result$jscomp$0};");

    helperMoveExpression(
        "var x = {set a(p) {}, b: foo()};",
        "foo",
        "var result$jscomp$0=foo();var x = {set a(p) {}, b: result$jscomp$0};");
  }

  /** Test case helpers. */

  private void helperCanExposeExpression(
      DecompositionType expectedResult,
      String code,
      String fnName
      ) {
    helperCanExposeExpression(expectedResult, code, fnName, null);
  }

  private void helperCanExposeFunctionExpression(
      DecompositionType expectedResult, String code, int call) {
    Compiler compiler = getCompiler();
    Set<String> knownConstants = new HashSet<>();
    ExpressionDecomposer decomposer = new ExpressionDecomposer(
        compiler, compiler.getUniqueNameIdSupplier(),
        knownConstants, newScope());
    Node tree = parse(compiler, code);
    assertNotNull(tree);

    Node externsRoot = parse(compiler,
        "function goo() {}" +
        "function foo() {}");
    assertNotNull(externsRoot);

    Node callSite = findCall(tree, null, 2);
    assertNotNull("Call " + call + " was not found.", callSite);

    compiler.resetUniqueNameId();
    DecompositionType result = decomposer.canExposeExpression(
        callSite);
    assertEquals(expectedResult, result);
  }

  private void helperCanExposeExpression(
      DecompositionType expectedResult,
      String code,
      String fnName,
      Set<String> knownConstants
      ) {
    Compiler compiler = getCompiler();
    if (knownConstants == null) {
      knownConstants = new HashSet<>();
    }
    ExpressionDecomposer decomposer = new ExpressionDecomposer(
        compiler, compiler.getUniqueNameIdSupplier(),
        knownConstants, newScope());
    Node tree = parse(compiler, code);
    assertNotNull(tree);

    Node externsRoot = parse(compiler,
        "function goo() {}" +
        "function foo() {}");
    assertNotNull(externsRoot);

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

  private void validateSourceInfo(Compiler compiler, Node subtree) {
    (new LineNumberCheck(compiler)).setCheckSubTree(subtree);
    // Source information problems are reported as compiler errors.
    if (compiler.getErrorCount() != 0) {
      String msg = "Error encountered: ";
      for (JSError err : compiler.getErrors()) {
        msg += err + "\n";
      }
      assertEquals(msg, 0, compiler.getErrorCount());
    }
  }

  private void helperExposeExpression(
      String code,
      String fnName,
      String expectedResult,
      Set<String> knownConstants
      ) {
    Compiler compiler = getCompiler();
    if (knownConstants == null) {
      knownConstants = new HashSet<>();
    }
    ExpressionDecomposer decomposer = new ExpressionDecomposer(
        compiler, compiler.getUniqueNameIdSupplier(),
        knownConstants, newScope());
    decomposer.setTempNamePrefix("temp");
    decomposer.setResultNamePrefix("result");
    Node expectedRoot = parse(compiler, expectedResult);
    Node tree = parse(compiler, code);
    assertNotNull(tree);

    Node callSite = findCall(tree, fnName);
    assertNotNull("Call to " + fnName + " was not found.", callSite);

    DecompositionType result = decomposer.canExposeExpression(callSite);
    assertEquals(DecompositionType.DECOMPOSABLE, result);

    compiler.resetUniqueNameId();
    decomposer.exposeExpression(callSite);
    validateSourceInfo(compiler, tree);
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
    Compiler compiler = getCompiler();
    if (knownConstants == null) {
      knownConstants = new HashSet<>();
    }

    ExpressionDecomposer decomposer = new ExpressionDecomposer(
        compiler, compiler.getUniqueNameIdSupplier(),
        knownConstants, newScope());
    decomposer.setTempNamePrefix("temp");
    decomposer.setResultNamePrefix("result");
    Node expectedRoot = parse(compiler, expectedResult);
    Node tree = parse(compiler, code);
    assertNotNull(tree);

    Node callSite = findCall(tree, fnName);
    assertNotNull("Call to " + fnName + " was not found.", callSite);

    compiler.resetUniqueNameId();
    decomposer.moveExpression(callSite);
    validateSourceInfo(compiler, tree);
    String explanation = expectedRoot.checkTreeEquals(tree);
    assertNull("\nExpected: " + compiler.toSource(expectedRoot) +
        "\nResult: " + compiler.toSource(tree) +
        "\n" + explanation, explanation);
  }

  private Compiler getCompiler() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguage(LanguageMode.ECMASCRIPT_2015);
    options.setCodingConvention(new GoogleCodingConvention());
    compiler.initOptions(options);
    return compiler;
  }

  private static Node findCall(Node n, String name) {
    return findCall(n, name, 1);
  }

  /**
   * @param name The name to look for.
   * @param call The call to look for.
   * @return The return the Nth instance of the CALL/YIELD node
   * matching name found in a pre-order traversal.
   */
  private static Node findCall(
      Node root, @Nullable final String name, final int call) {
    class Find {
      int found = 0;
      Node find(Node n) {
        if (n.isCall() || n.isYield()) {
          if (name == null
              || n.isYield() && "yield".equals(name)
              || (n.isCall() && n.getFirstChild().isName()
                  && n.getFirstChild().getString().equals(name))) {
            found++;
            if (found == call) {
              return n;
            }
          }
        }

        for (Node c : n.children()) {
          Node result = find(c);
          if (result != null) {
            return result;
          }
        }

        return null;
      }
    }

    return (new Find()).find(root);
  }

  private static Node parse(Compiler compiler, String js) {
    Node n = Normalize.parseAndNormalizeTestCode(compiler, js);
    assertEquals(Arrays.toString(compiler.getErrors()),
        0, compiler.getErrorCount());
    return n;
  }

  private Scope newScope() {
    return Scope.createGlobalScope(new Node(Token.SCRIPT));
  }
}
