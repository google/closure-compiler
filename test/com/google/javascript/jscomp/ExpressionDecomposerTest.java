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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.CompilerTestCase.lines;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.ExpressionDecomposer.DecompositionType;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link ExpressionDecomposer}
 *
 * @author johnlenz@google.com (John Lenz)
 */
// Note: functions "foo" and "goo" are external functions in the helper.
@RunWith(JUnit4.class)
public final class ExpressionDecomposerTest {
  private boolean allowMethodCallDecomposing;
  private final Set<String> knownConstants = new HashSet<>();
  // How many times to run `moveExpression` or `exposeExpression`.
  private int times;
  // Whether we should run type checking and test the type information in the output expression
  private boolean shouldTestTypes;

  @Before
  public void setUp() {
    allowMethodCallDecomposing = false;
    knownConstants.clear();
    times = 1;
    // Tests using ES6+ features not in the typechecker should set this option to false
    // TODO(lharker): stop setting this flag to false, since the typechecker should now understand
    // all features in ES2017
    shouldTestTypes = true;
  }

  @Test
  public void testCannotExpose_expression1() {
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

  @Test
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

  @Test
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

  @Test
  public void testCanExposeExpression_compoundDeclaration_inForInitializer_firstElement() {
    // VAR will already be hoisted by `Normalize`.
    helperCanExposeExpression(DecompositionType.MOVABLE, "for (var x = foo(), y = 5;;) {}", "foo");

    helperCanExposeExpression(DecompositionType.MOVABLE, "for (let x = foo(), y = 5;;) {}", "foo");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "for (const x = foo(), y = 5;;) {}", "foo");
  }

  @Test
  public void testCanExposeExpression_compoundDeclaration_inForInitializer_nthElement() {
    // TODO(b/121157467) FOR introduces complex scoping that isn't currently `Normalize`d.
    // Since in some cases we'd effectively end up having to `Normalize` these, decomposition just
    // bails for now.

    // VAR will already be hoisted by `Normalize`.
    helperCanExposeExpression(DecompositionType.MOVABLE, "for (var x = 8, y = foo();;) {}", "foo");

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "for (let x = 8, y = foo();;) {}", "foo");
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "for (const x = 8, y = foo();;) {}", "foo");
  }

  @Test
  public void testCannotExpose_expression4a() {
    // 'this' must be preserved in call.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "if (goo.a(1, foo()));", "foo");
  }

  @Test
  public void testCanExposeExpression4b() {
    allowMethodCallDecomposing = true;
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "if (goo.a(1, foo()));", "foo");
  }

  @Test
  public void testCannotExpose_expression5a() {
    // 'this' must be preserved in call.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "if (goo['a'](foo()));", "foo");
  }

  @Test
  public void testCanExposeExpression5b() {
    allowMethodCallDecomposing = true;
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "if (goo['a'](foo()));", "foo");
  }

  @Test
  public void testCannotExpose_expression6a() {
    // 'this' must be preserved in call.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "z:if (goo.a(1, foo()));", "foo");
  }

  @Test
  public void testCanExposeExpression6b() {
    allowMethodCallDecomposing = true;
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "z:if (goo.a(1, foo()));", "foo");
  }

  @Test
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

  @Test
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

  @Test
  public void testCannotExpose_expression9() {
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        "function *f() { for (let x of yield y) {} }",
        "yield");
  }

  @Test
  public void testCannotExpose_forAwaitOf() {
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        "async function *f() { for await (let x of yield y) {} }",
        "yield");
  }

  @Test
  public void testCanExposeExpression10() {
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        "function *f() { for (let x in yield y) {} }",
        "yield");
  }

  @Test
  public void testCannotExpose_expression11() {
    // expressions in parameter lists
    helperCanExposeExpression(DecompositionType.UNDECOMPOSABLE, "function f(x = foo()) {}", "foo");

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "function f({[foo()]: x}) {}", "foo");

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "(function (x = foo()) {})()", "foo");

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "(function ({[foo()]: x}) {})()", "foo");
  }

  @Test
  public void testCanExpose_aCall_withSpreadSibling() {
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "f(...x, y());", "y");
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "f(y(), ...x);", "y");

    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "new D(...x, y());", "y");
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "new D(y(), ...x);", "y");

    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "[...x, y()];", "y");
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "({...x, z: y()});", "y");

    // Array- and object-literal instantiations cannot be side-effected.
    helperCanExposeExpression(DecompositionType.MOVABLE, "[y(), ...x];", "y");
    helperCanExposeExpression(DecompositionType.MOVABLE, "({z: y(), ...x});", "y");

    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "f(...y());", "y");
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "f(...y(), x);", "y");
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "f(x, ...y());", "y");

    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "f(...x, x, y());", "y");
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "f(...x, ...x, y());", "y");
  }

  @Test
  public void testCanExpose_anExpression_withSpreadRelative_ifInDifferentFunction() {
    // TODO(b/121004488): There are potential decompositions that weren't implemented.
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "f(function() { [...x]; }, y());", "y");
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "f(function() { ({...x}); }, y());", "y");

    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "f(y(), () => [...x]);", "y");
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "f(y(), () => ({...x}));", "y");

    helperCanExposeExpression(DecompositionType.MOVABLE, "[() => f(...x), y()];", "y");

    helperCanExposeExpression(
        DecompositionType.MOVABLE,
        lines(
            "[", //
            "   class {",
            "     f(x) { return [...x]; }",
            "   },",
            "  y()",
            "];"),
        "y");
  }

  @Test
  public void testCanExposeExpression12() {
    // Test destructuring rhs is evaluated before the lhs
    shouldTestTypes = false;
    helperCanExposeExpression(DecompositionType.MOVABLE, "const {a, b = goo()} = foo();", "foo");

    helperCanExposeExpression(DecompositionType.MOVABLE, "const [a, b = goo()] = foo();", "foo");

    helperCanExposeExpression(DecompositionType.MOVABLE, "({a, b = goo()} = foo());", "foo");

    // Default value expressions are conditional, which would make the expressions complex.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "[{ [foo()]: a } = goo()] = arr;", "foo");
  }

  @Test
  public void testCanExposeExpressionInTemplateLiteralSubstitution() {
    helperCanExposeExpression(DecompositionType.MOVABLE, "const result = `${foo()}`;", "foo");

    allowMethodCallDecomposing = true;
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "const obj = {f(x) {}}; obj.f(`${foo()}`);", "foo");

    helperCanExposeExpression(
        DecompositionType.MOVABLE, "const result = `${foo()} ${goo()}`;", "foo");

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "const result = `${foo()} ${goo()}`;", "goo");
  }

  @Test
  public void testCannotExpose_defaultValueInParamList() {
    helperCanExposeExpression(DecompositionType.UNDECOMPOSABLE, "function fn(a = g()) {}", "g");
  }

  @Test
  public void testCannotExpose_defaultValueInDestructuring() {
    helperCanExposeExpression(DecompositionType.UNDECOMPOSABLE, "let {x = fn()} = y;", "fn");

    helperCanExposeExpression(DecompositionType.UNDECOMPOSABLE, "let [x = fn()] = y;", "fn");
  }

  @Test
  public void testMoveExpression1() {
    // There isn't a reason to do this, but it works.
    helperMoveExpression("foo()", "foo", "var result$jscomp$0 = foo(); result$jscomp$0;");
  }

  @Test
  public void testMoveExpression2() {
    helperMoveExpression(
        "x = foo()",
        "foo",
        "var result$jscomp$0 = foo(); x = result$jscomp$0;");
  }

  @Test
  public void testMoveExpression3() {
    helperMoveExpression(
        "var x = foo()",
        "foo",
        "var result$jscomp$0 = foo(); var x = result$jscomp$0;");
  }

  @Test
  public void testMoveExpression4() {
    shouldTestTypes = false;
    helperMoveExpression(
        "const x = foo()",
        "foo",
        "var result$jscomp$0 = foo(); const x = result$jscomp$0;");
  }

  @Test
  public void testMoveExpression5() {
    shouldTestTypes = false;
    helperMoveExpression(
        "let x = foo()",
        "foo",
        "var result$jscomp$0 = foo(); let x = result$jscomp$0;");
  }

  @Test
  public void testMoveExpression6() {
    helperMoveExpression(
        "if(foo()){}",
        "foo",
        "var result$jscomp$0 = foo(); if (result$jscomp$0);");
  }

  @Test
  public void testMoveExpression7() {
    helperMoveExpression(
        "switch(foo()){}",
        "foo",
        "var result$jscomp$0 = foo(); switch(result$jscomp$0){}");
  }

  @Test
  public void testMoveExpression8() {
    helperMoveExpression(
        "switch(1 + foo()){}",
        "foo",
        "var result$jscomp$0 = foo(); switch(1 + result$jscomp$0){}");
  }

  @Test
  public void testMoveExpression9() {
    helperMoveExpression(
        "function f(){ return foo();}",
        "foo",
        "function f(){ var result$jscomp$0 = foo(); return result$jscomp$0;}");
  }

  @Test
  public void testMoveExpression10() {
    helperMoveExpression(
        "x = foo() && 1",
        "foo",
        "var result$jscomp$0 = foo(); x = result$jscomp$0 && 1");
  }

  @Test
  public void testMoveExpression11() {
    helperMoveExpression(
        "x = foo() || 1",
        "foo",
        "var result$jscomp$0 = foo(); x = result$jscomp$0 || 1");
  }

  @Test
  public void testMoveExpression12() {
    helperMoveExpression(
        "x = foo() ? 0 : 1",
        "foo",
        "var result$jscomp$0 = foo(); x = result$jscomp$0 ? 0 : 1");
  }

  @Test
  public void testMoveExpression13() {
    shouldTestTypes = false;
    helperMoveExpression(
        "const {a, b} = foo();",
        "foo",
        "var result$jscomp$0 = foo(); const {a, b} = result$jscomp$0;");
  }

  @Test
  public void testMoveExpression14() {
    shouldTestTypes = false;
    helperMoveExpression(
        "({a, b} = foo());",
        "foo",
        "var result$jscomp$0 = foo(); ({a, b} = result$jscomp$0);");
  }

  /* Decomposition tests. */

  @Test
  public void testExposeExpression1() {
    helperExposeExpression(
        "x = 0 && foo()",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = 0) temp$jscomp$0 = foo(); x = temp$jscomp$0;");
  }

  @Test
  public void testExposeExpression2() {
    helperExposeExpression(
        "x = 1 || foo()",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = 1); else temp$jscomp$0=foo(); x = temp$jscomp$0;");
  }

  @Test
  public void testExposeExpression3() {
    helperExposeExpression(
        "var x = 1 ? foo() : 0",
        "foo",
        "var temp$jscomp$0;"
            + " if (1) temp$jscomp$0 = foo(); else temp$jscomp$0 = 0;var x = temp$jscomp$0;");
  }

  @Test
  public void testExposeExpression4() {
    shouldTestTypes = false;
    helperExposeExpression(
        "const x = 1 ? foo() : 0",
        "foo",
        "var temp$jscomp$0;"
            + " if (1) temp$jscomp$0 = foo(); else temp$jscomp$0 = 0;const x = temp$jscomp$0;");
  }

  @Test
  public void testExposeExpression5() {
    shouldTestTypes = false;
    helperExposeExpression(
        "let x = 1 ? foo() : 0",
        "foo",
        "var temp$jscomp$0;"
            + " if (1) temp$jscomp$0 = foo(); else temp$jscomp$0 = 0;let x = temp$jscomp$0;");
  }

  @Test
  public void testExposeExpression6() {
    helperExposeExpression(
        "goo() && foo()",
        "foo",
        "if (goo()) foo();");
  }

  @Test
  public void testExposeExpression7() {
    helperExposeExpression(
        "x = goo() && foo()",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo(); x = temp$jscomp$0;");
  }

  @Test
  public void testExposeExpression8() {
    helperExposeExpression(
        "var x = 1 + (goo() && foo())",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();"
            + "var x = 1 + temp$jscomp$0;");
  }

  @Test
  public void testExposeExpression9() {
    shouldTestTypes = false;
    helperExposeExpression(
        "const x = 1 + (goo() && foo())",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();"
            + "const x = 1 + temp$jscomp$0;");
  }

  @Test
  public void testExposeExpression10() {
    shouldTestTypes = false;
    helperExposeExpression(
        "let x = 1 + (goo() && foo())",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();"
            + "let x = 1 + temp$jscomp$0;");
  }

  @Test
  public void testExposeExpression11() {
    helperExposeExpression(
        "if(goo() && foo());",
        "foo",
        lines(
            "var temp$jscomp$0;",
            "if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();",
            "if(temp$jscomp$0);"));
  }

  @Test
  public void testExposeExpression12() {
    helperExposeExpression(
        "switch(goo() && foo()){}",
        "foo",
        lines(
            "var temp$jscomp$0;",
            "if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();",
            "switch(temp$jscomp$0){}"));
  }

  @Test
  public void testExposeExpression13() {
    helperExposeExpression(
        "switch(1 + goo() + foo()){}",
        "foo",
        "var temp_const$jscomp$0 = 1 + goo(); switch(temp_const$jscomp$0 + foo()){}");
  }

  @Test
  public void testExposeExpression_inVanillaForInitializer_simpleExpression() {
    helperExposeExpression(
        "for (x = goo() + foo();;) {}",
        "foo",
        lines(
            "var temp_const$jscomp$0 = goo();", //
            "for (x = temp_const$jscomp$0 + foo();;) {}"));
  }

  @Test
  public void testExposeExpression_inVanillaForInitializer_usingLabel() {
    helperExposeExpression(
        "LABEL: for (x = goo() + foo();;) {}",
        "foo",
        lines(
            "var temp_const$jscomp$0 = goo();", //
            "LABEL: for (x = temp_const$jscomp$0 + foo();;) {}"));
  }

  @Test
  public void testExposeExpression_inVanillaForInitializer_singleDeclaration_withLetOrConst() {
    for (String dec : ImmutableList.of("let", "const")) {
      helperExposeExpression(
          "for (" + dec + " x = goo() + foo();;) {}",
          "foo",
          lines(
              "var temp_const$jscomp$0 = goo();", //
              "for (" + dec + " x = temp_const$jscomp$0 + foo();;) {}"));
    }
  }

  @Test
  public void testExposeExpression_inVanillaForInitializer_firstDeclaration_withLetOrConst() {
    for (String dec : ImmutableList.of("let", "const")) {
      helperExposeExpression(
          "for (" + dec + " x = goo() + foo(), y = 5;;) {}",
          "foo",
          lines(
              "var temp_const$jscomp$0 = goo();", //
              "for (" + dec + " x = temp_const$jscomp$0 + foo(), y = 5;;) {}"));
    }
  }

  @Test
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

  @Test
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

  @Test
  public void testExposeExpression16() {
    helperExposeExpression(
        "throw bar() && foo();",
        "foo",
        "var temp$jscomp$0; if (temp$jscomp$0 = bar()) temp$jscomp$0=foo(); throw temp$jscomp$0;");
  }

  @Test
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

  @Test
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

  @Test
  public void testMoveClass1() {
    shouldTestTypes = false;
    helperMoveExpression(
        "alert(class X {});",
        ExpressionDecomposerTest::findClass,
        "var result$jscomp$0 = class X {}; alert(result$jscomp$0);");
  }

  @Test
  public void testMoveClass2() {
    shouldTestTypes = false;
    helperMoveExpression(
        "console.log(1, 2, class X {});",
        ExpressionDecomposerTest::findClass,
        "var result$jscomp$0 = class X {}; console.log(1, 2, result$jscomp$0);");
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
  @Test
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
  @Test
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
  @Test
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
  @Test
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
  @Test
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

  @Test
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

  @Test
  public void testMoveSpread_siblingOfCall_outOfArrayLiteral_usesTempArray() {
    shouldTestTypes = false; // TODO(nickreid): Enable this when tests support typed `AstFactory`.
    helperExposeExpression(
        "[...x, foo()];",
        "foo",
        lines(
            "var temp_const$jscomp$0 = [...x];", //
            "[...temp_const$jscomp$0, foo()];"));
  }

  @Test
  public void testMoveSpread_siblingOfCall_outOfObjectLiteral_usesTempObject() {
    shouldTestTypes = false; // TODO(nickreid): Enable this when tests support typed `AstFactory`.
    helperExposeExpression(
        "({...x, y: foo()});",
        "foo",
        lines(
            "var temp_const$jscomp$0 = {...x};", //
            "({...temp_const$jscomp$0, y: foo()});"));
  }

  @Test
  public void testMoveSpread_siblingOfCall_outOfFunctionCall_usesTempArray() {
    shouldTestTypes = false; // TODO(nickreid): Enable this when tests support typed `AstFactory`.
    helperExposeExpression(
        lines(
            "function f() { }", //
            "f(...x, foo());"),
        "foo",
        lines(
            "function f() { }", //
            "var temp_const$jscomp$1 = f;",
            "var temp_const$jscomp$0 = [...x];",
            "temp_const$jscomp$1(...temp_const$jscomp$0, foo());"));
  }

  @Test
  public void testMoveSpreadParent_siblingOfCall_outOfFunctionCall_usesNoTempArray() {
    helperExposeExpression(
        lines(
            "function f() { }", //
            "f([...x], foo());"),
        "foo",
        lines(
            "function f() { }", //
            "var temp_const$jscomp$1 = f;",
            "var temp_const$jscomp$0 = [...x];",
            "temp_const$jscomp$1(temp_const$jscomp$0, foo());"));
  }

  @Test
  public void testMoveSpreadParent_siblingOfCall_outOfFunctionCall_usesNoTempObject() {
    helperExposeExpression(
        lines(
            "function f() { }", //
            "f({...x}, foo());"),
        "foo",
        lines(
            "function f() { }", //
            "var temp_const$jscomp$1 = f;",
            "var temp_const$jscomp$0 = {...x};",
            "temp_const$jscomp$1(temp_const$jscomp$0, foo());"));
  }

  @Test
  public void testExposeExpressionInTemplateLibSub() {
    helperExposeExpression(
        "` ${ foo() }  ${ goo() } `;",
        "goo",
        "var temp_const$jscomp$0 = foo(); ` ${ temp_const$jscomp$0 }  ${ goo() } `;");
  }

  @Test
  public void testExposeSubExpressionInTemplateLibSub() {
    helperExposeExpression(
        "` ${ foo() + goo() } `;",
        "goo",
        "var temp_const$jscomp$0 = foo(); ` ${ temp_const$jscomp$0 + goo() } `;");
  }

  @Test
  public void testMoveExpressionInTemplateLibSub() {
    helperMoveExpression(
        "` ${ foo() }  ${ goo() } `;",
        "foo",
        "var result$jscomp$0 = foo(); ` ${ result$jscomp$0 }  ${ goo() } `;");
  }

  @Test
  public void testExposeExpression_computedProp_withPureKey() {
    helperCanExposeExpression(
        DecompositionType.MOVABLE,
        lines(
            "({", //
            "  ['a' + 'b']: foo(),",
            "});"),
        "foo");
  }

  @Test
  public void testExposeObjectLitValue_computedProp_withImpureKey() {
    helperExposeExpression(
        lines(
            "({", //
            "  [goo()]: foo(),",
            "});"),
        "foo",
        lines(
            "var temp_const$jscomp$0 = goo();", //
            "({",
            "  [temp_const$jscomp$0]: foo(),",
            "});"));
  }

  @Test
  public void testExposeObjectLitValue_computedProp_asEarlierSibling_withImpureKeyAndValue() {
    helperExposeExpression(
        lines(
            "({", //
            "  [goo()]: qux(),",
            "  bar: foo(),",
            "});"),
        "foo",
        lines(
            "var temp_const$jscomp$1 = goo();", //
            "var temp_const$jscomp$0 = qux();",
            "({",
            "  [temp_const$jscomp$1]: temp_const$jscomp$0,",
            "  bar: foo(),",
            "});"));
  }

  @Test
  public void testExposeObjectLitValue_memberFunctions_asEarlierSiblings_arePure() {
    helperCanExposeExpression(
        DecompositionType.MOVABLE,
        lines(
            "({", //
            "  a() { },",
            "  get b() { },",
            "  set b(v) { },",
            "",
            "  bar: foo(),",
            "});"),
        "foo");
  }

  @Test
  public void testMoveSuperCall() {
    helperMoveExpression(
        "class A { constructor() { super(foo()) } }",
        "foo",
        "class A{constructor(){var result$jscomp$0=foo();super(result$jscomp$0)}}");
  }

  @Test
  public void testMoveSuperCall_noSideEffects() {
    // String() is being used since it's known to not have side-effects.
    helperMoveExpression(
        "class A { constructor() { super(String()) } }",
        "String",
        "class A{constructor(){var result$jscomp$0=String();super(result$jscomp$0)}}");
  }

  @Test
  public void testExposeSuperCall() {
    times = 2;
    helperExposeExpression(
        "class A { constructor() { super(goo(), foo()) } }",
        "foo",
        lines(
            "class A{ constructor(){", //
            "   var temp_const$jscomp$0=goo();",
            "   super(temp_const$jscomp$0, foo())",
            "}}"));
  }

  @Test
  public void testExposeSuperCall_noSideEffects() {
    times = 2;
    // String() is being used since it's known to not have side-effects.
    helperExposeExpression(
        "class A { constructor() { super(goo(), String()) } }",
        "String",
        lines(
            "class A{ constructor(){", //
            "   var temp_const$jscomp$0=goo();",
            "   super(temp_const$jscomp$0, String())",
            "}}"));
  }

  /** Test case helpers. */

  private void helperCanExposeFunctionExpression(
      DecompositionType expectedResult, String code, int call) {
    Compiler compiler = getCompiler();
    ExpressionDecomposer decomposer = new ExpressionDecomposer(
        compiler, compiler.getUniqueNameIdSupplier(),
        knownConstants, newScope(), allowMethodCallDecomposing);
    Node tree = parse(compiler, code);
    assertThat(tree).isNotNull();

    Node externsRoot = parse(compiler, "function goo() {} function foo() {}");
    assertThat(externsRoot).isNotNull();

    Node callSite = findCall(tree, null, call);
    assertWithMessage("Call " + call + " was not found.").that(callSite).isNotNull();

    compiler.resetUniqueNameId();
    DecompositionType result = decomposer.canExposeExpression(callSite);
    assertThat(result).isEqualTo(expectedResult);
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
    assertThat(tree).isNotNull();

    Node externsRoot = parse(compiler, "function goo() {} function foo() {}");
    assertThat(externsRoot).isNotNull();

    Node callSite = findCall(tree, fnName);
    assertWithMessage("Call to " + fnName + " was not found.").that(callSite).isNotNull();

    compiler.resetUniqueNameId();
    DecompositionType result = decomposer.canExposeExpression(callSite);
    assertThat(result).isEqualTo(expectedResult);
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
    assertThat(tree).isNotNull();

    if (shouldTestTypes) {
      processForTypecheck(compiler, tree);
    }

    Node expr = nodeFinder.apply(tree);
    assertWithMessage("Expected node was not found.").that(expr).isNotNull();

    DecompositionType result = decomposer.canExposeExpression(expr);
    assertThat(result).isEqualTo(DecompositionType.DECOMPOSABLE);

    compiler.resetUniqueNameId();
    for (int i = 0; i < times; i++) {
      decomposer.exposeExpression(expr);
    }
    validateSourceInfo(compiler, tree);
    assertNode(tree).usingSerializer(compiler::toSource).isEqualTo(expectedRoot);

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
    assertThat(tree).isNotNull();

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
    assertNode(tree).usingSerializer(compiler::toSource).isEqualTo(expectedRoot);

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
    JSType expectedType = rootExpected.getJSType();
    JSType actualType = rootActual.getJSType();

    if (expectedType == null || actualType == null) {
      assertWithMessage("Expected " + rootExpected + " but got " + rootActual)
          .that(actualType)
          .isEqualTo(expectedType);
    } else if (expectedType.isUnknownType() && actualType.isUnknownType()) {
      // continue
    } else {
      // we can't compare actual equality because the types are from different runs of the
      // type inference, so we just compare the strings.
      assertWithMessage("Expected " + rootExpected + " but got " + rootActual)
          .that(actualType.toAnnotationString(JSType.Nullability.EXPLICIT))
          .isEqualTo(expectedType.toAnnotationString(JSType.Nullability.EXPLICIT));
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
    options.setLanguage(LanguageMode.ECMASCRIPT_2018);
    options.setCodingConvention(new GoogleCodingConvention());
    compiler.initOptions(options);
    return compiler;
  }

  private void processForTypecheck(AbstractCompiler compiler, Node jsRoot) {
    Node root = IR.root(IR.root(), IR.root(jsRoot));
    compiler.setTypeCheckingHasRun(true);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    (new TypeCheck(compiler, new SemanticReverseAbstractInterpreter(registry), registry))
        .processForTesting(root.getFirstChild(), root.getSecondChild());
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

    return new Find().find(root);
  }

  private void validateSourceInfo(Compiler compiler, Node subtree) {
    new SourceInfoCheck(compiler).setCheckSubTree(subtree);
    // Source information problems are reported as compiler errors.
    assertThat(compiler.getErrors()).isEmpty();
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
