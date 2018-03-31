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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.CompilerTestCase.lines;
import static com.google.javascript.jscomp.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.AbstractCompiler.MostRecentTypechecker;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.ExpressionDecomposer.DecompositionType;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import junit.framework.TestCase;

/**
 * Unit tests for {@link ExpressionDecomposer}
 *
 * @author johnlenz@google.com (John Lenz)
 */
// Note: functions "foo" and "goo" are external functions in the helper.
public final class ExpressionDecomposerTest extends TestCase {
  private boolean allowMethodCallDecomposing;
  private final Set<String> knownConstants = new HashSet<>();
  // How many times to run `moveExpression` or `exposeExpression`.
  private int times;
  // Whether we should run type checking and test the type information in the output expression
  private boolean shouldTestTypes;

  @Override
  public void setUp() {
    allowMethodCallDecomposing = false;
    knownConstants.clear();
    times = 1;
    // Tests using ES6+ features not in the typechecker should set this option to false
    shouldTestTypes = true;
  }

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

  public void testCanExposeExpression4a() {
    // 'this' must be preserved in call.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "if (goo.a(1, foo()));", "foo");
  }

  public void testCanExposeExpression4b() {
    allowMethodCallDecomposing = true;
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "if (goo.a(1, foo()));", "foo");
  }

  public void testCanExposeExpression5a() {
    // 'this' must be preserved in call.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "if (goo['a'](foo()));", "foo");
  }

  public void testCanExposeExpression5b() {
    allowMethodCallDecomposing = true;
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "if (goo['a'](foo()));", "foo");
  }

  public void testCanExposeExpression6a() {
    // 'this' must be preserved in call.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "z:if (goo.a(1, foo()));", "foo");
  }

  public void testCanExposeExpression6b() {
    allowMethodCallDecomposing = true;
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "z:if (goo.a(1, foo()));", "foo");
  }

  public void testCanExposeExpression7() {
    // Verify calls to function expressions are movable.
    helperCanExposeFunctionExpression(
        DecompositionType.MOVABLE,
        lines(
            "(function(map){descriptions_=map})(",
            "  function(){",
            "    var ret={};",
            "    ret[INIT]='a';",
            "    ret[MIGRATION_BANNER_DISMISS]='b';",
            "    return ret",
            "  }());"),
        2);
  }

  public void testCanExposeExpression8() {
    // Can it be decompose?
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE,
        lines(
            "HangoutStarter.prototype.launchHangout = function() {",
            "  var self = a.b;",
            "  var myUrl = new goog.Uri(",
            "      getDomServices_(self).getDomHelper().getWindow().location.href);",
            "};"),
        "getDomServices_");

    // Verify it is properly expose the target expression.
    helperExposeExpression(
        lines(
            "HangoutStarter.prototype.launchHangout = function() {",
            "  var self = a.b;",
            "  var myUrl =",
            "      new goog.Uri(getDomServices_(self).getDomHelper().getWindow().location.href);",
            "};"),
        "getDomServices_",
        lines(
            "HangoutStarter.prototype.launchHangout = function() {",
            "  var self = a.b;",
            "  var temp_const$jscomp$0 = goog.Uri;",
            "  var myUrl = new temp_const$jscomp$0(",
            "      getDomServices_(self).getDomHelper().getWindow().location.href);",
            "}"));

    // Verify the results can be properly moved.
    helperMoveExpression(
        lines(
            "HangoutStarter.prototype.launchHangout = function() {",
            "  var self = a.b;",
            "  var temp_const$jscomp$0 = goog.Uri;",
            "  var myUrl = new temp_const$jscomp$0(",
            "      getDomServices_(self).getDomHelper().getWindow().location.href);",
            "}"),
        "getDomServices_",
        lines(
            "HangoutStarter.prototype.launchHangout = function() {",
            "  var self=a.b;",
            "  var temp_const$jscomp$0=goog.Uri;",
            "  var result$jscomp$0=getDomServices_(self);",
            "  var myUrl=new temp_const$jscomp$0(",
            "      result$jscomp$0.getDomHelper().getWindow().location.href);",
            "}"));
  }

  public void testCanExposeExpression9() {
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        "function *f() { for (let x of yield y) {} }",
        "yield");
  }

  public void testCanExposeExpression10() {
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        "function *f() { for (let x in yield y) {} }",
        "yield");
  }

  public void testCanExposeExpression11() {
    // expressions in parameter lists
    helperCanExposeExpression(DecompositionType.UNDECOMPOSABLE, "function f(x = foo()) {}", "foo");

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "function f({[foo()]: x}) {}", "foo");

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "(function (x = foo()) {})()", "foo");

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "(function ({[foo()]: x}) {})()", "foo");
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
    shouldTestTypes = false;
    helperMoveExpression(
        "const x = foo()",
        "foo",
        "var result$jscomp$0 = foo(); const x = result$jscomp$0;");
  }

    public void testMoveExpression5() {
    shouldTestTypes = false;
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
        "var temp$jscomp$0;"
            + " if (1) temp$jscomp$0 = foo(); else temp$jscomp$0 = 0;var x = temp$jscomp$0;");
  }

  public void testExposeExpression4() {
    shouldTestTypes = false;
    helperExposeExpression(
        "const x = 1 ? foo() : 0",
        "foo",
        "var temp$jscomp$0;"
            + " if (1) temp$jscomp$0 = foo(); else temp$jscomp$0 = 0;const x = temp$jscomp$0;");
  }

  public void testExposeExpression5() {
    shouldTestTypes = false;
    helperExposeExpression(
        "let x = 1 ? foo() : 0",
        "foo",
        "var temp$jscomp$0;"
            + " if (1) temp$jscomp$0 = foo(); else temp$jscomp$0 = 0;let x = temp$jscomp$0;");
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
        "var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();"
            + "var x = 1 + temp$jscomp$0;");
  }

  public void testExposeExpression9() {
    shouldTestTypes = false;
    helperExposeExpression(
        "const x = 1 + (goo() && foo())",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();"
            + "const x = 1 + temp$jscomp$0;");
  }

  public void testExposeExpression10() {
    shouldTestTypes = false;
    helperExposeExpression(
        "let x = 1 + (goo() && foo())",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();"
            + "let x = 1 + temp$jscomp$0;");
  }

  public void testExposeExpression11() {
    helperExposeExpression(
        "if(goo() && foo());",
        "foo",
        lines(
            "var temp$jscomp$0;",
            "if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();",
            "if(temp$jscomp$0);"));
  }

  public void testExposeExpression12() {
    helperExposeExpression(
        "switch(goo() && foo()){}",
        "foo",
        lines(
            "var temp$jscomp$0;",
            "if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();",
            "switch(temp$jscomp$0){}"));
  }

  public void testExposeExpression13() {
    helperExposeExpression(
        "switch(1 + goo() + foo()){}",
        "foo",
        "var temp_const$jscomp$0 = 1 + goo(); switch(temp_const$jscomp$0 + foo()){}");
  }

  public void testExposeExpression14() {
    helperExposeExpression(
        "function f(){ return goo() && foo();}",
        "foo",
        lines(
            "function f() {",
            "  var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();",
            "  return temp$jscomp$0;",
            "}"));
  }

  public void testExposeExpression15() {
    // TODO(johnlenz): We really want a constant marking pass.
    // The value "goo" should be constant, but it isn't known to be so.
    helperExposeExpression(
        "if (goo(1, goo(2), (1 ? foo() : 0)));",
        "foo",
        lines(
          "var temp_const$jscomp$1 = goo;",
          "var temp_const$jscomp$0 = goo(2);",
          "var temp$jscomp$2;",
          "if (1) temp$jscomp$2 = foo(); else temp$jscomp$2 = 0;",
          "if (temp_const$jscomp$1(1, temp_const$jscomp$0, temp$jscomp$2));"));
  }

  public void testExposeExpression16() {
    helperExposeExpression(
        "throw bar() && foo();",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = bar()) temp$jscomp$0=foo(); throw temp$jscomp$0;");
  }

  public void testExposeExpression17() {
    allowMethodCallDecomposing = true;
    helperExposeExpression(
        "x.foo(y())",
        "y",
        lines(
            "var temp_const$jscomp$1 = x;",
            "var temp_const$jscomp$0 = temp_const$jscomp$1.foo;",
            "temp_const$jscomp$0.call(temp_const$jscomp$1, y());"));
  }

  public void testExposeExpression18() {
    allowMethodCallDecomposing = true;
    shouldTestTypes = false;
    helperExposeExpression(
        lines(
            "const {a, b, c} = condition ?",
            "  y() :",
            "  {a: 0, b: 0, c: 1};"),
        "y",
        lines(
            "var temp$jscomp$0;",
            "if (condition) {",
            "  temp$jscomp$0 = y();",
            "} else {",
            "  temp$jscomp$0 = {a: 0, b: 0, c: 1};",
            "}",
            "const {a, b, c} = temp$jscomp$0;"));
  }

  public void testMoveClass1() {
    shouldTestTypes = false;
    helperMoveExpression(
        "alert(class X {});",
        ExpressionDecomposerTest::findClass,
        "var result$jscomp$0 = class X {}; alert(result$jscomp$0);");
  }

  public void testMoveClass2() {
    shouldTestTypes = false;
    helperMoveExpression(
        "console.log(1, 2, class X {});",
        ExpressionDecomposerTest::findClass,
        "var result$jscomp$0 = class X {}; console.log(1, 2, result$jscomp$0);");
  }

  public void testMoveYieldExpression1() {
    helperMoveExpression(
        "function *f() { return { a: yield 1, c: foo(yield 2, yield 3) }; }",
        "yield",
        lines(
            "function *f() {",
            "  var result$jscomp$0 = yield 1;",
            "  return { a: result$jscomp$0, c: foo(yield 2, yield 3) };",
            "}"));

    helperMoveExpression(
        "function *f() { return { a: 0, c: foo(yield 2, yield 3) }; }",
        "yield",
        lines(
            "function *f() {",
            "  var result$jscomp$0 = yield 2;",
            "  return { a: 0, c: foo(result$jscomp$0, yield 3) };",
            "}"));

    helperMoveExpression(
        "function *f() { return { a: 0, c: foo(1, yield 3) }; }",
        "yield",
        lines(
            "function *f() {",
            "  var result$jscomp$0 = yield 3;",
            "  return { a: 0, c: foo(1, result$jscomp$0) };",
            "}"));
  }

  public void testMoveYieldExpression2() {
    helperMoveExpression(
        "function *f() { return (yield 1) || (yield 2); }",
        "yield",
        lines(
            "function *f() {",
            "  var result$jscomp$0 = yield 1;",
            "  return result$jscomp$0 || (yield 2);",
            "}"));
  }

  public void testMoveYieldExpression3() {
    helperMoveExpression(
        "function *f() { return x.y(yield 1); }",
        "yield",
        lines(
            "function *f() {",
            "  var result$jscomp$0 = yield 1;",
            "  return x.y(result$jscomp$0);",
            "}"));
  }

  public void testExposeYieldExpression1() {
    helperExposeExpression(
        "function *f(x) { return x || (yield 2); }",
        "yield",
        lines(
            "function *f(x) {",
            "  var temp$jscomp$0;",
            "  if (temp$jscomp$0=x); else temp$jscomp$0 = yield 2;",
            "  return temp$jscomp$0",
            "}"));
  }

  public void testExposeYieldExpression2() {
    allowMethodCallDecomposing = true;
    helperExposeExpression(
        "function *f() { return x.y(yield 1); }",
        "yield",
        lines(
            "function *f() {",
            "  var temp_const$jscomp$1 = x;",
            "  var temp_const$jscomp$0 = temp_const$jscomp$1.y;",
            "  return temp_const$jscomp$0.call(temp_const$jscomp$1, yield 1);",
            "}"));
  }

  public void testExposeYieldExpression3() {
    allowMethodCallDecomposing = true;
    String before = "function *f() { return g.call(yield 1); }";
    String after = lines(
        "function *f() {",
        "  var temp_const$jscomp$1 = g;",
        "  var temp_const$jscomp$0 = temp_const$jscomp$1.call;",
        "  return temp_const$jscomp$0.call(temp_const$jscomp$1, yield 1);",
        "}");
    helperExposeExpression(before, "yield", after);

    // Check that we don't decompose again, which would result in an infinite loop when inlining
    // functions.
    times = 2;
    helperExposeExpression(before, "yield", after);
  }

  public void testExposeYieldExpression4() {
    allowMethodCallDecomposing = true;
    helperExposeExpression(
        "function *f() { return g.apply([yield 1, yield 2]); }",
        "yield",
        lines(
            "function *f() {",
            "  var temp_const$jscomp$1 = g;",
            "  var temp_const$jscomp$0 = temp_const$jscomp$1.apply;",
            "  return temp_const$jscomp$0.call(temp_const$jscomp$1, [yield 1, yield 2]);",
            "}"));
  }

  // Simple name on LHS of assignment-op.
  public void testExposePlusEquals1() {
    helperExposeExpression(
        "var x = 0; x += foo() + 1",
        "foo",
        "var x = 0; var temp_const$jscomp$0 = x; x = temp_const$jscomp$0 + (foo() + 1);");

    helperExposeExpression(
        "var x = 0; y = (x += foo()) + x",
        "foo",
        "var x = 0; var temp_const$jscomp$0 = x; y = (x = temp_const$jscomp$0 + foo()) + x");
  }

  // Structure on LHS of assignment-op.
  public void testExposePlusEquals2() {
    helperExposeExpression(
        "var x = {}; x.a += foo() + 1",
        "foo",
        lines(
            "var x = {}; var temp_const$jscomp$0 = x;",
            "var temp_const$jscomp$1 = temp_const$jscomp$0.a;",
            "temp_const$jscomp$0.a = temp_const$jscomp$1 + (foo() + 1);"));

    helperExposeExpression(
        "var x = {}; y = (x.a += foo()) + x.a",
        "foo",
        lines(
            "var x = {}; var temp_const$jscomp$0 = x;",
            "var temp_const$jscomp$1 = temp_const$jscomp$0.a;",
            "y = (temp_const$jscomp$0.a = temp_const$jscomp$1 + foo()) + x.a"));
  }

  // Constant object on LHS of assignment-op.
  public void testExposePlusEquals3() {
    helperExposeExpression(
        "/** @const */ var XX = {}; XX.a += foo() + 1",
        "foo",
        "var XX = {}; var temp_const$jscomp$0 = XX.a;"
            + "XX.a = temp_const$jscomp$0 + (foo() + 1);");

    helperExposeExpression(
        "var XX = {}; y = (XX.a += foo()) + XX.a",
        "foo",
        "var XX = {}; var temp_const$jscomp$0 = XX.a;"
            + "y = (XX.a = temp_const$jscomp$0 + foo()) + XX.a");
  }

  // Function all on LHS of assignment-op.
  public void testExposePlusEquals4() {
    helperExposeExpression(
        "var x = {}; goo().a += foo() + 1",
        "foo",
        lines(
            "var x = {};",
            "var temp_const$jscomp$0 = goo();",
            "var temp_const$jscomp$1 = temp_const$jscomp$0.a;",
            "temp_const$jscomp$0.a = temp_const$jscomp$1 + (foo() + 1);"));

    helperExposeExpression(
        "var x = {}; y = (goo().a += foo()) + goo().a",
        "foo",
        lines(
            "var x = {};",
            "var temp_const$jscomp$0 = goo();",
            "var temp_const$jscomp$1 = temp_const$jscomp$0.a;",
            "y = (temp_const$jscomp$0.a = temp_const$jscomp$1 + foo()) + goo().a"));
  }

  // Test multiple levels
  public void testExposePlusEquals5() {
    helperExposeExpression(
        "var x = {}; goo().a.b += foo() + 1",
        "foo",
        lines(
            "var x = {};",
            "var temp_const$jscomp$0 = goo().a;",
            "var temp_const$jscomp$1 = temp_const$jscomp$0.b;",
            "temp_const$jscomp$0.b = temp_const$jscomp$1 + (foo() + 1);"));

    helperExposeExpression(
        "var x = {}; y = (goo().a.b += foo()) + goo().a",
        "foo",
        lines(
            "var x = {};",
            "var temp_const$jscomp$0 = goo().a;",
            "var temp_const$jscomp$1 = temp_const$jscomp$0.b;",
            "y = (temp_const$jscomp$0.b = temp_const$jscomp$1 + foo()) + goo().a"));
  }

  public void testExposeObjectLit1() {
    // Validate that getter and setters methods are seen as side-effect
    // free and that values can move past them.  We don't need to be
    // concerned with exposing the getter or setter here but the
    // decomposer does not have a method of exposing properties, only variables.
    helperMoveExpression(
        "var x = {get a() {}, b: foo()};",
        "foo",
        "var result$jscomp$0=foo();var x = {get a() {}, b: result$jscomp$0};");

    helperMoveExpression(
        "var x = {set a(p) {}, b: foo()};",
        "foo",
        "var result$jscomp$0=foo();var x = {set a(p) {}, b: result$jscomp$0};");
  }

  public void testFindExpressionRoot1() {
    assertNode(findExpressionRoot("var x = f()", "f")).hasType(Token.VAR);
  }

  public void testFindExpressionRoot2() {
    assertNode(findExpressionRoot("foo(bar(f()));", "f")).hasType(Token.EXPR_RESULT);
  }

  public void testFindExpressionRoot3() {
    assertThat(findExpressionRoot("for (let x of f()) {}", "f")).isNull();
  }

  public void testFindExpressionRoot4() {
    assertThat(findExpressionRoot("for (let x in f()) {}", "f")).isNull();
  }

  public void testFindExpressionRoot5() {
    assertNode(findExpressionRoot("for (let x = f();;) {}", "f")).hasType(Token.FOR);
  }

  /** Test case helpers. */

  /**
   * @return The result of calling {@link ExpressionDecomposer#findExpressionRoot} on the CALL
   *     node in {@code js} whose callee is a NAME matching {@code name}.
   */
  @Nullable
  private Node findExpressionRoot(String js, String name) {
    Compiler compiler = getCompiler();
    Node tree = parse(compiler, js);
    Node call = findCall(tree, name);
    checkNotNull(call);

    Node root = ExpressionDecomposer.findExpressionRoot(call);
    if (root != null) {
      checkState(NodeUtil.isStatement(root), root);
    }
    return root;
  }

  private void helperCanExposeFunctionExpression(
      DecompositionType expectedResult, String code, int call) {
    Compiler compiler = getCompiler();
    ExpressionDecomposer decomposer = new ExpressionDecomposer(
        compiler, compiler.getUniqueNameIdSupplier(),
        knownConstants, newScope(), allowMethodCallDecomposing);
    Node tree = parse(compiler, code);
    assertNotNull(tree);

    Node externsRoot = parse(compiler, "function goo() {} function foo() {}");
    assertNotNull(externsRoot);

    Node callSite = findCall(tree, null, call);
    assertNotNull("Call " + call + " was not found.", callSite);

    compiler.resetUniqueNameId();
    DecompositionType result = decomposer.canExposeExpression(callSite);
    assertEquals(expectedResult, result);
  }

  private void helperCanExposeExpression(
      DecompositionType expectedResult,
      String code,
      String fnName) {
    Compiler compiler = getCompiler();
    ExpressionDecomposer decomposer = new ExpressionDecomposer(
        compiler, compiler.getUniqueNameIdSupplier(),
        knownConstants, newScope(), allowMethodCallDecomposing);
    Node tree = parse(compiler, code);
    assertNotNull(tree);

    Node externsRoot = parse(compiler, "function goo() {} function foo() {}");
    assertNotNull(externsRoot);

    Node callSite = findCall(tree, fnName);
    assertNotNull("Call to " + fnName + " was not found.", callSite);

    compiler.resetUniqueNameId();
    DecompositionType result = decomposer.canExposeExpression(callSite);
    assertEquals(expectedResult, result);
  }

  private void helperExposeExpression(
      String code,
      String fnName,
      String expectedResult) {
    helperExposeExpression(code, tree -> findCall(tree, fnName), expectedResult);
  }

  private void helperExposeExpression(
      String code,
      Function<Node, Node> nodeFinder,
      String expectedResult) {
    Compiler compiler = getCompiler();
    ExpressionDecomposer decomposer = new ExpressionDecomposer(
        compiler, compiler.getUniqueNameIdSupplier(),
        knownConstants, newScope(), allowMethodCallDecomposing);
    decomposer.setTempNamePrefix("temp");
    decomposer.setResultNamePrefix("result");
    Node expectedRoot = parse(compiler, expectedResult);
    Node tree = parse(compiler, code);
    Node originalTree = tree.cloneTree();
    assertNotNull(tree);

    if (shouldTestTypes) {
      processForTypecheck(compiler, tree);
    }

    Node expr = nodeFinder.apply(tree);
    assertWithMessage("Expected node was not found.").that(expr).isNotNull();

    DecompositionType result = decomposer.canExposeExpression(expr);
    assertEquals(DecompositionType.DECOMPOSABLE, result);

    compiler.resetUniqueNameId();
    for (int i = 0; i < times; i++) {
      decomposer.exposeExpression(expr);
    }
    validateSourceInfo(compiler, tree);
    String explanation = expectedRoot.checkTreeEquals(tree);
    assertNull("\nExpected: " + compiler.toSource(expectedRoot)
        + "\nResult:   " + compiler.toSource(tree)
        + "\n" + explanation, explanation);

    if (shouldTestTypes) {
      Node trueExpr = nodeFinder.apply(originalTree);

      compiler.resetUniqueNameId();
      for (int i = 0; i < times; i++) {
        decomposer.exposeExpression(trueExpr);
      }
      processForTypecheck(compiler, originalTree);

      checkTypeStringsEqualAsTree(originalTree, tree);
    }
  }

  private void helperMoveExpression(
      String code,
      String fnName,
      String expectedResult) {
    helperMoveExpression(code, tree -> findCall(tree, fnName), expectedResult);
  }

  private void helperMoveExpression(
      String code,
      Function<Node, Node> nodeFinder,
      String expectedResult) {
    Compiler compiler = getCompiler();

    ExpressionDecomposer decomposer = new ExpressionDecomposer(
        compiler, compiler.getUniqueNameIdSupplier(),
        knownConstants, newScope(), allowMethodCallDecomposing);
    decomposer.setTempNamePrefix("temp");
    decomposer.setResultNamePrefix("result");
    Node expectedRoot = parse(compiler, expectedResult);
    Node tree = parse(compiler, code);
    Node originalTree = tree.cloneTree();
    assertNotNull(tree);

    if (shouldTestTypes) {
      processForTypecheck(compiler, tree);
    }

    Node expr = nodeFinder.apply(tree);
    assertWithMessage("Expected node was not found.").that(expr).isNotNull();

    compiler.resetUniqueNameId();
    for (int i = 0; i < times; i++) {
      decomposer.moveExpression(expr);
    }
    validateSourceInfo(compiler, tree);
    String explanation = expectedRoot.checkTreeEquals(tree);
    assertNull("\nExpected: " + compiler.toSource(expectedRoot)
        + "\nResult:   " + compiler.toSource(tree)
        + "\n" + explanation, explanation);

    if (shouldTestTypes) {
      // find a basis for comparison:
      Node originalExpr = nodeFinder.apply(originalTree);

      compiler.resetUniqueNameId();
      for (int i = 0; i < times; i++) {
        decomposer.moveExpression(originalExpr);
      }
      processForTypecheck(compiler, originalTree);

      checkTypeStringsEqualAsTree(originalTree, tree);
    }
  }

  private void checkTypeStringsEqualAsTree(Node rootExpected, Node rootActual) {
    TypeI expectedType = rootExpected.getTypeI();
    TypeI actualType = rootActual.getTypeI();

    if (expectedType == null || actualType == null) {
      assertEquals("Expected " + rootExpected + " but got " + rootActual, expectedType, actualType);
    } else if (expectedType.isUnknownType() && actualType.isUnknownType()) {
      // continue
    } else {
      // we can't compare actual equality because the types are from different runs of the
      // type inference, so we just compare the strings.
      assertEquals(
          "Expected " + rootExpected + " but got " + rootActual,
          expectedType.toAnnotationString(TypeI.Nullability.EXPLICIT),
          actualType.toAnnotationString(TypeI.Nullability.EXPLICIT));
    }

    Node child1 = rootExpected.getFirstChild();
    Node child2 = rootActual.getFirstChild();
    while (child1 != null) {
      checkTypeStringsEqualAsTree(child1, child2);
      child1 = child1.getNext();
      child2 = child2.getNext();
    }
  }

  private Compiler getCompiler() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguage(LanguageMode.ECMASCRIPT_2015);
    options.setCodingConvention(new GoogleCodingConvention());
    options.setAllowMethodCallDecomposing(allowMethodCallDecomposing);
    compiler.initOptions(options);
    return compiler;
  }

  private void processForTypecheck(AbstractCompiler compiler, Node jsRoot) {
    Node scriptRoot = IR.root(jsRoot);
    compiler.setMostRecentTypechecker(MostRecentTypechecker.OTI);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    (new TypeCheck(compiler, new SemanticReverseAbstractInterpreter(registry), registry))
        .processForTesting(null, scriptRoot.getFirstChild());
  }

  @Nullable
  private static Node findClass(Node n) {
    if (n.isClass()) {
      return n;
    }
    for (Node child : n.children()) {
      Node maybeClass = findClass(child);
      if (maybeClass != null) {
        return maybeClass;
      }
    }
    return null;
  }

  private static Node findCall(Node n, String name) {
    return findCall(n, name, 1);
  }

  /**
   * @param name The name to look for. If name is null, look for a yield expression instead.
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
              || (n.isYield() && "yield".equals(name))
              || (n.isCall() && n.getFirstChild().matchesQualifiedName(name))) {
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

  private static Node parse(Compiler compiler, String js) {
    Node n = Normalize.parseAndNormalizeTestCode(compiler, js);
    assertThat(compiler.getErrors()).isEmpty();
    return n;
  }

  private Scope newScope() {
    return Scope.createGlobalScope(new Node(Token.ROOT));
  }
}
