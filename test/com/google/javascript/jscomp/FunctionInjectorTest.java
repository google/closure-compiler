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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.CompilerTestCase.lines;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.FunctionInjector.CanInlineResult;
import com.google.javascript.jscomp.FunctionInjector.InliningMode;
import com.google.javascript.jscomp.FunctionInjector.Reference;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Inline function tests.
 *
 * @author johnlenz@google.com (John Lenz)
 */
@RunWith(JUnit4.class)
public final class FunctionInjectorTest {
  static final InliningMode INLINE_DIRECT = InliningMode.DIRECT;
  static final InliningMode INLINE_BLOCK = InliningMode.BLOCK;
  private boolean assumeStrictThis = false;
  private final boolean assumeMinimumCapture = false;
  private boolean allowDecomposition;

  @Before
  public void setUp() throws Exception {
    assumeStrictThis = false;
    allowDecomposition = false;
  }

  private FunctionInjector getInjector() {
    Compiler compiler = new Compiler();
    return new FunctionInjector(
        compiler, compiler.getUniqueNameIdSupplier(), true,
        assumeStrictThis, assumeMinimumCapture);
  }

  @Test
  public void testIsSimpleFunction1() {
    assertThat(getInjector().isDirectCallNodeReplacementPossible(prep("function f(){}"))).isTrue();
  }

  @Test
  public void testIsSimpleFunction2() {
    assertThat(getInjector().isDirectCallNodeReplacementPossible(prep("function f(){return 0;}")))
        .isTrue();
  }

  @Test
  public void testIsSimpleFunction3() {
    assertThat(
            getInjector()
                .isDirectCallNodeReplacementPossible(prep("function f(){return x ? 0 : 1}")))
        .isTrue();
  }

  @Test
  public void testIsSimpleFunction4() {
    assertThat(getInjector().isDirectCallNodeReplacementPossible(prep("function f(){return;}")))
        .isFalse();
  }

  @Test
  public void testIsSimpleFunction5() {
    assertThat(
            getInjector()
                .isDirectCallNodeReplacementPossible(prep("function f(){return 0; return 0;}")))
        .isFalse();
  }

  @Test
  public void testIsSimpleFunction6() {
    assertThat(
            getInjector()
                .isDirectCallNodeReplacementPossible(
                    prep("function f(){var x=true;return x ? 0 : 1}")))
        .isFalse();
  }

  @Test
  public void testIsSimpleFunction7() {
    assertThat(
            getInjector()
                .isDirectCallNodeReplacementPossible(
                    prep("function f(){if (x) return 0; else return 1}")))
        .isFalse();
  }

  @Test
  public void testCanInlineReferenceToFunction1() {
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){}; foo();", "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction2() {
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){}; foo();", "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction3() {
    // NOTE: FoldConstants will convert this to a empty function,
    // so there is no need to explicitly support it.
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(){return;}; foo();", "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction4() {
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){return;}; foo();", "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction5() {
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){return true;}; foo();", "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction6() {
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){return true;}; foo();", "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction7() {
    // In var initialization.
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){return true;}; var x=foo();", "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction8() {
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){return true;}; var x=foo();", "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction9() {
    // In assignment.
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){return true;}; var x; x=foo();", "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction10() {
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){return true;}; var x; x=foo();", "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction11() {
    // In expression.
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){return true;}; var x; x=x+foo();", "foo",
        INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction12() {
    // "foo" is not known to be side-effect free, it might change the value
    // of "x", so it can't be inlined.
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(){return true;}; var x; x=x+foo();", "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction12b() {
    // "foo" is not known to be side-effect free, it might change the value
    // of "x", so it can't be inlined.
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(){return true;}; var x; x=x+foo();",
        "foo",
        INLINE_BLOCK);
  }

  // TODO(nicksantos): Re-enable with side-effect detection.
  @Test
  @Ignore
  public void testCanInlineReferenceToFunction13() {
    // ... if foo is side-effect free we can inline here.
    helperCanInlineReferenceToFunction(
        CanInlineResult.YES,
        "/** @nosideeffects */ function foo(){return true;};" + "var x; x=x+foo();",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction14() {
    // Simple call with parameters
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(a){return true;}; foo(x);", "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction15() {
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(a){return true;}; foo(x);", "foo", INLINE_BLOCK);
  }

  // TODO(johnlenz): remove this constant once this has been proven in
  // production code.
  static final CanInlineResult NEW_VARS_IN_GLOBAL_SCOPE =
      CanInlineResult.YES;

  @Test
  public void testCanInlineReferenceToFunction16() {
    // Function "foo" as it contains "var b" which
    // must be brought into the global scope.
    helperCanInlineReferenceToFunction(NEW_VARS_IN_GLOBAL_SCOPE,
        "function foo(a){var b;return a;}; foo(goo());", "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction17() {
    // This doesn't bring names into the global name space.
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(a){return a;}; " +
        "function x() { foo(goo()); }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction18() {
    // Parameter has side-effects.
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(a){return a;} foo(x++);", "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction18b() {
    // Parameter has side-effects.
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a,b){return b,a;} foo(x++,use(x));", "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction19() {
    // Parameter has mutable parameter referenced more than once.
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return a+a} foo([]);", "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction20() {
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return a+a} foo({});", "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction21() {
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return a+a} foo(new Date);", "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction22() {
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return a+a} foo(true && new Date);", "foo",
        INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction23() {
    // variables to global scope.
    helperCanInlineReferenceToFunction(NEW_VARS_IN_GLOBAL_SCOPE,
        "function foo(a){return a;}; foo(x++);", "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction24() {
    // ... this is OK, because it doesn't introduce a new global name.
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(a){return a;}; " +
        "function x() { foo(x++); }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction25() {
    // Parameter has side-effects.
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return a+a;}; foo(x++);", "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction26() {
    helperCanInlineReferenceToFunction(NEW_VARS_IN_GLOBAL_SCOPE,
        "function foo(a){return a+a;}; foo(x++);", "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction27() {
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(a){return a+a;}; " +
        "function x() { foo(x++); }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction28() {
    // Parameter has side-effects.
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return true;}; foo(goo());", "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction29() {
    helperCanInlineReferenceToFunction(NEW_VARS_IN_GLOBAL_SCOPE,
        "function foo(a){return true;}; foo(goo());", "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction30() {
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(a){return true;}; " +
        "function x() { foo(goo()); }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction31() {
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(a) {return true;}; " +
        "function x() {foo.call(this, 1);}",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction32() {
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return true;}; " +
        "function x() { foo.apply(this, [1]); }",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction33() {
    // No special handling is required for method calls passing this.
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(a){return true;}; " +
        "function x() { foo.bar(this, 1); }",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction34() {
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(a){return true;}; " +
        "function x() { foo.call(this, goo()); }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction35() {
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return true;}; " +
        "function x() { foo.apply(this, goo()); }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction36() {
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(a){return true;}; " +
        "function x() { foo.bar(this, goo()); }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction37() {
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return true;}; " +
        "function x() { foo.call(null, 1); }",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction38() {
    assumeStrictThis = false;

    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return true;}; " +
        "function x() { foo.call(null, goo()); }",
        "foo", INLINE_BLOCK);

    assumeStrictThis = true;

    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(a){return true;}; " +
        "function x() { foo.call(null, goo()); }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction39() {
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return true;}; " +
        "function x() { foo.call(bar, 1); }",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction40() {
    assumeStrictThis = false;
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return true;}; " +
        "function x() { foo.call(bar, goo()); }",
        "foo", INLINE_BLOCK);

    assumeStrictThis = true;
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(a){return true;}; " +
        "function x() { foo.call(bar, goo()); }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction41() {
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return true;}; " +
        "function x() { foo.call(new bar(), 1); }",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction42() {
    assumeStrictThis = false;
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return true;}; " +
        "function x() { foo.call(new bar(), goo()); }",
        "foo", INLINE_BLOCK);

    assumeStrictThis = true;
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(a){return true;}; " +
        "function x() { foo.call(new bar(), goo()); }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction43() {
    // Handle the case of a missing 'this' value in a call.
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(){return true;}; " +
        "function x() { foo.call(); }",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction44() {
    assumeStrictThis = false;
    // Handle the case of a missing 'this' value in a call.
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(){return true;}; " +
        "function x() { foo.call(); }",
        "foo", INLINE_BLOCK);

    assumeStrictThis = true;
    // Handle the case of a missing 'this' value in a call.
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){return true;}; " +
        "function x() { foo.call(); }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction45() {
    // Call with inner function expression.
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){return function() {return true;}}; foo();",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction46() {
    // Call with inner function expression.
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){return function() {return true;}}; foo();",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction47() {
    // Call with inner function expression and variable decl.
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(){var a; return function() {return true;}}; foo();",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction48() {
    // Call with inner function expression and variable decl.
    // TODO(johnlenz): should we validate no values in scope?
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){var a; return function() {return true;}}; foo();",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction49() {
    // Call with inner function expression.
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){return function() {var a; return true;}}; foo();",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testCanInlineReferenceToFunction50() {
    // Call with inner function expression.
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){return function() {var a; return true;}}; foo();",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction51() {
    // Call with inner function statement.
    helperCanInlineReferenceToFunction(CanInlineResult.YES,
        "function foo(){function x() {var a; return true;} return x}; foo();",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunction52() {
    // Don't inline functions with var declarations into a scope with inner functions
    helperCanInlineReferenceToFunction(
        CanInlineResult.NO,
        "function foo() { var a = 3; return a; }"
        + "function bar() { function baz() {} if (true) { foo(); } }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression1() {
    // Call in if condition
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(a){return true;}; function x() { if (foo(1)) throw 'test'; }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression2() {
    // Call in return expression
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(a){return true;}; function x() { return foo(1); }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression3() {
    // Call in switch expression
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(a){return true;}; function x() { switch(foo(1)) { default:break; } }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression4() {
    // Call in hook condition
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(a){return true;}; function x() {foo(1)?0:1 }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression5() {
    // Call in hook side-effect free condition
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return true;}; " +
        "function x() {true?foo(1):1 }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression5a() {
    // Call in hook side-effect free condition
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(a){return true;}; function x() {true?foo(1):1 }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression6() {
    // Call in expression statement "condition"
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(a){return true;}; function x() {foo(1) && 1 }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression7() {
    // Call in expression statement after side-effect free "condition"
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return true;}; " +
        "function x() {1 && foo(1) }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression7a() {
    // Call in expression statement after side-effect free "condition"
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(a){return true;}; function x() {1 && foo(1) }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression8() {
    // Call in expression statement after side-effect free operator
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(a){return true;}; function x() {1 + foo(1) }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression9() {
    // Call in VAR expression.
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(a){return true;}; function x() {var b = 1 + foo(1)}",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression10() {
    // Call in assignment expression.
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return true;}; " +
        "function x() {var b; b += 1 + foo(1) }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression10a() {
    // Call in assignment expression.
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(a){return true;}; function x() {var b; b += 1 + foo(1) }",
        "foo",
        INLINE_BLOCK);
  }

  // TODO(nicksantos): Re-enable with side-effect detection.
  @Test
  @Ignore
  public void testCanInlineReferenceToFunctionInExpression11() {
    helperCanInlineReferenceToFunction(
        CanInlineResult.YES,
        "/** @nosideeffects */ function foo(a){return true;}; "
            + "function x() {var b; b += 1 + foo(1) }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression12() {
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(a){return true;}; function x() {var a,b,c; a = b = c = foo(1) }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression13() {
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(a){return true;}; function x() {var a,b,c; a = b = c = 1 + foo(1) }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression14() {
    // ... foo can not be inlined because of possible changes to "c".
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "var a = {}, b = {}, c;" +
        "a.test = 'a';" +
        "b.test = 'b';" +
        "c = a;" +
        "function foo(){c = b; return 'foo'};" +
        "c.test=foo();",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression14a() {
    // ... foo can be inlined despite possible changes to "c".
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "var a = {}, b = {}, c;"
            + "a.test = 'a';"
            + "b.test = 'b';"
            + "c = a;"
            + "function foo(){c = b; return 'foo'};"
            + "c.test=foo();",
        "foo",
        INLINE_BLOCK);
  }

  // TODO(nicksantos): Re-enable with side-effect detection.
  @Test
  @Ignore
  public void testCanInlineReferenceToFunctionInExpression15() {
    // ... foo can be inlined as it is side-effect free.
    helperCanInlineReferenceToFunction(
        CanInlineResult.YES,
        "var a = {}, b = {}, c;"
            + "a.test = 'a';"
            + "b.test = 'b';"
            + "c = a;"
            + "/** @nosideeffects */ function foo(){return 'foo'};"
            + "c.test=foo();",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  @Ignore
  public void testCanInlineReferenceToFunctionInExpression16() {
    // ... foo can not be inlined because of possible side-effects of x()
    helperCanInlineReferenceToFunction(
        CanInlineResult.NO,
        "var a = {}, b = {}, c;"
            + "a.test = 'a';"
            + "b.test = 'b';"
            + "c = a;"
            + "function x(){return c};"
            + "/** @nosideeffects */ function foo(){return 'foo'};"
            + "x().test=foo();",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  @Ignore
  public void testCanInlineReferenceToFunctionInExpression17() {
    // ... foo can be inlined because of x() is side-effect free.
    helperCanInlineReferenceToFunction(
        CanInlineResult.YES,
        "var a = {}, b = {}, c;"
            + "a.test = 'a';"
            + "b.test = 'b';"
            + "c = a;"
            + "/** @nosideeffects */ function x(){return c};"
            + "/** @nosideeffects */ function foo(){return 'foo'};"
            + "x().test=foo();",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression18() {
    // Call in within a call
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(){return _g();}; function x() {1 + foo()() }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression19() {
    // ... unless foo is known to be side-effect free, it might actually
    // change the value of "_g" which would unfortunately change the behavior,
    // so we can't inline here.
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(){return a;}; " +
        "function x() {1 + _g(foo()) }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression19a() {
    // ... unless foo is known to be side-effect free, it might actually
    // change the value of "_g" which would unfortunately change the behavior,
    // so we can't inline here.
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(){return a;}; function x() {1 + _g(foo()) }",
        "foo",
        INLINE_BLOCK);
  }

  // TODO(nicksantos): Re-enable with side-effect detection.
  @Test
  @Ignore
  public void testCanInlineReferenceToFunctionInExpression20() {
    helperCanInlineReferenceToFunction(
        CanInlineResult.YES,
        "/** @nosideeffects */ function foo(){return a;}; " + "function x() {1 + _g(foo()) }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression21() {
    // Assignments to object are problematic if the call has side-effects,
    // as the object that is being referred to can change.
    // Note: This could be changed be inlined if we in some way make "z"
    // as not escaping from the local scope.
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "var z = {};" +
        "function foo(a){z = {};return true;}; " +
        "function x() { z.gack = foo(1) }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression21a() {
    // Assignments to object are problematic if the call has side-effects,
    // as the object that is being referred to can change.
    // Note: This could be changed be inlined if we in some way make "z"
    // as not escaping from the local scope.
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "var z = {};"
            + "function foo(a){z = {};return true;}; "
            + "function x() { z.gack = foo(1) }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression22() {
    // ... foo() is after a side-effect
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(){return a;}; " +
        "function x() {1 + _g(_a(), foo()) }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression22a() {
    // ... foo() is after a side-effect
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(){return a;}; function x() {1 + _g(_a(), foo()) }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression23() {
    // ... foo() is after a side-effect
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(){return a;}; " +
        "function x() {1 + _g(_a(), foo.call(this)) }",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInExpression23a() {
    // ... foo() is after a side-effect
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.AFTER_PREPARATION,
        "function foo(){return a;}; function x() {1 + _g(_a(), foo.call(this)) }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInLoop1() {
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.YES,
        "function foo(){return a;}; while(1) { foo(); }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineReferenceToFunctionInLoop2() {
    // If function contains function, don't inline it into a loop.
    // TODO(johnlenz): this can be improved by looking to see
    // if the inner function contains any references to values defined
    // in the outer function.
    allowDecomposition = true;
    helperCanInlineReferenceToFunction(
        CanInlineResult.NO,
        "function foo(){return function() {};}; while(1) { foo(); }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineFunctionWithInnerArrowFunction1() {
    helperCanInlineReferenceToFunction(
        CanInlineResult.YES,
        "function foo(){ () => { alert(1); }; } foo();",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testCanInlineFunctionWithInnerArrowFunction2() {
    helperCanInlineReferenceToFunction(
        CanInlineResult.NO,
        "function foo(){ () => { this; }; } foo();",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInline1() {
    helperInlineReferenceToFunction(
        "function foo(){}; foo();",
        "function foo(){}; void 0",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testInline2() {
    helperInlineReferenceToFunction(
        "function foo(){}; foo();",
        "function foo(){}; {}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInline3() {
    helperInlineReferenceToFunction(
        "function foo(){return;}; foo();",
        "function foo(){return;}; {}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInline4() {
    helperInlineReferenceToFunction(
        "function foo(){return true;}; foo();",
        "function foo(){return true;}; true;",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testInline5() {
    helperInlineReferenceToFunction(
        "function foo(){return true;}; foo();",
        "function foo(){return true;}; {true;}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInline6() {
    // In var initialization.
    helperInlineReferenceToFunction(
        "function foo(){return true;}; var x=foo();",
        "function foo(){return true;}; var x=true;",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testInline7() {
    helperInlineReferenceToFunction(
        "function foo(){return true;}; var x=foo();",
        "function foo(){return true;}; var x;" +
            "{x=true}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInline8() {
    // In assignment.
    helperInlineReferenceToFunction(
        "function foo(){return true;}; var x; x=foo();",
        "function foo(){return true;}; var x; x=true;",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testInline9() {
    helperInlineReferenceToFunction(
        "function foo(){return true;}; var x; x=foo();",
        "function foo(){return true;}; var x;{x=true}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInline10() {
    // In expression.
    helperInlineReferenceToFunction(
        "function foo(){return true;}; var x; x=x+foo();",
        "function foo(){return true;}; var x; x=x+true;",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testInline11() {
    // Simple call with parameters
    helperInlineReferenceToFunction(
        "function foo(a){return true;}; foo(x);",
        "function foo(a){return true;}; true;",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testInline12() {
    helperInlineReferenceToFunction(
        "function foo(a){return true;}; foo(x);",
        "function foo(a){return true;}; {true}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInline13() {
    // Parameter has side-effects.
    helperInlineReferenceToFunction(
        "function foo(a){return a;}; " +
        "function x() { foo(x++); }",
        "function foo(a){return a;}; " +
        "function x() {{x++;}}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInline14() {
    // Parameter has side-effects.
    helperInlineReferenceToFunction(
        "function foo(a){return a+a;}; foo(x++);",
        "function foo(a){return a+a;}; " +
            "{var a$jscomp$inline_0=x++;" +
            " a$jscomp$inline_0+" +
            "a$jscomp$inline_0;}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInline15() {
    // Parameter has mutable, references more than once.
    helperInlineReferenceToFunction(
        "function foo(a){return a+a;}; foo(new Date());",
        "function foo(a){return a+a;}; " +
            "{var a$jscomp$inline_0=new Date();" +
            " a$jscomp$inline_0+" +
            "a$jscomp$inline_0;}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInline16() {
    // Parameter is large, references more than once.
    helperInlineReferenceToFunction(
        "function foo(a){return a+a;}; foo(function(){});",
        "function foo(a){return a+a;}; " +
            "{var a$jscomp$inline_0=function(){};" +
            " a$jscomp$inline_0+" +
            "a$jscomp$inline_0;}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInline17() {
    // Parameter has side-effects.
    helperInlineReferenceToFunction(
        "function foo(a){return true;}; foo(goo());",
        "function foo(a){return true;};" +
            "{var a$jscomp$inline_0=goo();true}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInline18() {
    // This doesn't bring names into the global name space.
    helperInlineReferenceToFunction(
        "function foo(a){var b;return a;} function x() { foo(goo()); }",
        lines(
            "function foo(a) {",
            "  var b;",
            "  return a;",
            "}",
            "function x() {",
            "  {",
            "    var a$jscomp$inline_0 = goo();",
            "    var b$jscomp$inline_1;",
            "    a$jscomp$inline_0;",
            "  }",
            "}"),
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInline19() {
    // Properly alias.
    helperInlineReferenceToFunction(
        "var x = 1; var y = 2;" +
        "function foo(a,b){x = b; y = a;}; " +
        "function bar() { foo(x,y); }",
        "var x = 1; var y = 2;" +
        "function foo(a,b){x = b; y = a;}; " +
        "function bar() {" +
           "{var a$jscomp$inline_0=x;" +
            "x = y;" +
            "y = a$jscomp$inline_0;}" +
        "}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInline19b() {
    helperInlineReferenceToFunction(
        "var x = 1; var y = 2;" +
        "function foo(a,b){y = a; x = b;}; " +
        "function bar() { foo(x,y); }",
        "var x = 1; var y = 2;" +
        "function foo(a,b){y = a; x = b;}; " +
        "function bar() {" +
           "{var b$jscomp$inline_1=y;" +
            "y = x;" +
            "x = b$jscomp$inline_1;}" +
        "}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInline20() {
    // cloned FUNCTION node must be reported as being added.
    helperInlineReferenceToFunction(
        "function foo(a){return a}; foo(function(){});",
        "function foo(a){return a}; (function(){})",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testInline21() {
    // cloned FUNCTION node must be reported as being added.
    helperInlineReferenceToFunction(
        "function foo(a){return a}; foo(function(){});",
        "function foo(a){return a}; {(function(){})}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInlineIntoLoop() {
    helperInlineReferenceToFunction(
        "function foo(a){var b;return a;}; for(;1;){ foo(1); }",
        "function foo(a){var b;return a;}; for(;1;){ { var b$jscomp$inline_1=void 0;1} }",
        "foo",
        INLINE_BLOCK);

    helperInlineReferenceToFunction(
        "function foo(a){var b;return a;}; " +
        "do{ foo(1); } while(1)",
        "function foo(a){var b;return a;}; " +
        "do{ {" +
            "var b$jscomp$inline_1=void 0;1}}while(1)",
        "foo", INLINE_BLOCK);

    helperInlineReferenceToFunction(
        "function foo(a){for(var b in c)return a;}; " +
        "for(;1;){ foo(1); }",
        "function foo(a){var b;for(b in c)return a;}; " +
        "for(;1;){ {JSCompiler_inline_label_foo_2:{" +
            "var b$jscomp$inline_1=void 0;for(b$jscomp$inline_1 in c){" +
              "1;break JSCompiler_inline_label_foo_2" +
            "}}}}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInlineFunctionWithInnerFunction1() {
    // Call with inner function expression.
    helperInlineReferenceToFunction(
        "function foo(){return function() {return true;}}; foo();",
        "function foo(){return function() {return true;}};" +
            "(function() {return true;})",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testInlineFunctionWithInnerFunction2() {
    // Call with inner function expression.
    helperInlineReferenceToFunction(
        "function foo(){return function() {return true;}}; foo();",
        "function foo(){return function() {return true;}};" +
            "{(function() {return true;})}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInlineFunctionWithInnerFunction3() {
    // Call with inner function expression.
    helperInlineReferenceToFunction(
        "function foo(){return function() {var a; return true;}}; foo();",
        "function foo(){return function() {var a; return true;}};" +
            "(function() {var a; return true;});",
        "foo", INLINE_DIRECT);
  }

  @Test
  public void testInlineFunctionWithInnerFunction4() {
    // Call with inner function expression.
    helperInlineReferenceToFunction(
        "function foo(){return function() {var a; return true;}}; foo();",
        lines(
            "function foo(){return function() {var a; return true;}};",
            "{(function() {var a$jscomp$inline_0; return true;});}"),
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInlineFunctionWithInnerFunction5() {
    // Call with inner function statement.
    helperInlineReferenceToFunction(
        "function foo(){function x() {var a; return true;} return x} foo();",
        lines(
            "function foo() {",
            "  function x() { var a; return true; }",
            "  return x;",
            "}",
            "{",
            "  var x$jscomp$inline_0 = function(){",
            "    var a$jscomp$inline_1;",
            "    return true;",
            "  };",
            "  x$jscomp$inline_0;",
            "}"),
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testInlineFunctionWithInnerArrowFunction1() {
    helperInlineReferenceToFunction(
        "function foo(){ () => { alert(1); }; } foo();",
        lines(
            "function foo(){ () => { alert(1); }; }",
            "{ () => { alert(1); }; }"),
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression1() {
    // Call in if condition
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "function foo(a){return true;}; function x() { if (foo(1)) throw 'test'; }",
        "function foo(a){return true;}; "
            + "function x() { var JSCompiler_inline_result$jscomp$0; "
            + "{JSCompiler_inline_result$jscomp$0=true;}"
            + "if (JSCompiler_inline_result$jscomp$0) throw 'test'; }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression2() {
    // Call in return expression
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "function foo(a){return true;}; function x() { return foo(1); }",
        "function foo(a){return true;}; "
            + "function x() { var JSCompiler_inline_result$jscomp$0; "
            + "{JSCompiler_inline_result$jscomp$0=true;}"
            + "return JSCompiler_inline_result$jscomp$0; }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression3() {
    // Call in switch expression
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "function foo(a){return true;}; function x() { switch(foo(1)) { default:break; } }",
        "function foo(a){return true;}; "
            + "function x() { var JSCompiler_inline_result$jscomp$0; "
            + "{JSCompiler_inline_result$jscomp$0=true;}"
            + "switch(JSCompiler_inline_result$jscomp$0) { default:break; } }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression4() {
    // Call in hook condition
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "function foo(a){return true;}; function x() {foo(1)?0:1 }",
        "function foo(a){return true;}; "
            + "function x() { var JSCompiler_inline_result$jscomp$0; "
            + "{JSCompiler_inline_result$jscomp$0=true;}"
            + "JSCompiler_inline_result$jscomp$0?0:1 }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression5() {
    // Call in expression statement "condition"
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "function foo(a){return true;}; function x() {foo(1)&&1 }",
        "function foo(a){return true;}; "
            + "function x() { var JSCompiler_inline_result$jscomp$0; "
            + "{JSCompiler_inline_result$jscomp$0=true;}"
            + "JSCompiler_inline_result$jscomp$0&&1 }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression6() {
    // Call in expression statement after side-effect free "condition"
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "function foo(a){return true;}; function x() {1 + foo(1) }",
        "function foo(a){return true;}; "
            + "function x() { var JSCompiler_inline_result$jscomp$0; "
            + "{JSCompiler_inline_result$jscomp$0=true;}"
            + "1 + JSCompiler_inline_result$jscomp$0 }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression7() {
    // Call in expression statement "condition"
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "function foo(a){return true;}; function x() {foo(1) && 1 }",
        "function foo(a){return true;}; "
            + "function x() { var JSCompiler_inline_result$jscomp$0; "
            + "{JSCompiler_inline_result$jscomp$0=true;}"
            + "JSCompiler_inline_result$jscomp$0&&1 }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression8() {
    // Call in expression statement after side-effect free operator
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "function foo(a){return true;}; function x() {1 + foo(1) }",
        "function foo(a){return true;}; "
            + "function x() { var JSCompiler_inline_result$jscomp$0;"
            + "{JSCompiler_inline_result$jscomp$0=true;}"
            + "1 + JSCompiler_inline_result$jscomp$0 }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression9() {
    // Call in VAR expression.
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "function foo(a){return true;}; function x() {var b = 1 + foo(1)}",
        "function foo(a){return true;}; "
            + "function x() { "
            + "var JSCompiler_inline_result$jscomp$0;"
            + "{JSCompiler_inline_result$jscomp$0=true;}"
            + "var b = 1 + JSCompiler_inline_result$jscomp$0 "
            + "}",
        "foo",
        INLINE_BLOCK);
  }

  // TODO(nicksantos): Re-enable with side-effect detection.
  @Test
  @Ignore
  public void testInlineReferenceInExpression10() {
    // Call in assignment expression.
    helperInlineReferenceToFunction(
        "/** @nosideeffects */ function foo(a){return true;}; "
            + "function x() {var b; b += 1 + foo(1) }",
        "function foo(a){return true;}; "
            + "function x() {var b;"
            + "{var JSCompiler_inline_result$jscomp$0; "
            + "JSCompiler_inline_result$jscomp$0=true;}"
            + "b += 1 + JSCompiler_inline_result$jscomp$0 }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression11() {
    // Call under label
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "function foo(a){return true;}; function x() {a:foo(1)?0:1 }",
        "function foo(a){return true;}; "
            + "function x() {"
            + "  a:{"
            + "    var JSCompiler_inline_result$jscomp$0; "
            + "    {JSCompiler_inline_result$jscomp$0=true;}"
            + "    JSCompiler_inline_result$jscomp$0?0:1 "
            + "  }"
            + "}",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression12() {
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "function foo(a){return true;} function x() { 1?foo(1):1; }",
        "function foo(a){return true}"
            + "function x() {"
            + "  if(1) {"
            + "    {true;}"
            + "  } else {"
            + "    1;"
            + "  }"
            + "}",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression13() {
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "function foo(a){return true;}; function x() { goo() + (1?foo(1):1) }",
        "function foo(a){return true;}; "
            + "function x() { var JSCompiler_temp_const$jscomp$0=goo();"
            + "var JSCompiler_temp$jscomp$1;"
            + "if(1) {"
            + "  {JSCompiler_temp$jscomp$1=true;} "
            + "} else {"
            + "  JSCompiler_temp$jscomp$1=1;"
            + "}"
            + "JSCompiler_temp_const$jscomp$0 + JSCompiler_temp$jscomp$1"
            + "}",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression14() {
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "var z = {};"
            + "function foo(a){z = {};return true;}; "
            + "function x() { z.gack = foo(1) }",
        "var z = {};"
            + "function foo(a){z = {};return true;}; "
            + "function x() {"
            + "var JSCompiler_temp_const$jscomp$0=z;"
            + "var JSCompiler_inline_result$jscomp$1;"
            + "{"
            + "z= {};"
            + "JSCompiler_inline_result$jscomp$1 = true;"
            + "}"
            + "JSCompiler_temp_const$jscomp$0.gack = JSCompiler_inline_result$jscomp$1;"
            + "}",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression15() {
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "var z = {};"
            + "function foo(a){z = {};return true;}; "
            + "function x() { z.gack = foo.call(this,1) }",
        "var z = {};"
            + "function foo(a){z = {};return true;}; "
            + "function x() {"
            + "var JSCompiler_temp_const$jscomp$0=z;"
            + "var JSCompiler_inline_result$jscomp$1;"
            + "{"
            + "z= {};"
            + "JSCompiler_inline_result$jscomp$1 = true;"
            + "}"
            + "JSCompiler_temp_const$jscomp$0.gack = JSCompiler_inline_result$jscomp$1;"
            + "}",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression16() {
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "var z = {};"
            + "function foo(a){z = {};return true;}; "
            + "function x() { z[bar()] = foo(1) }",
        "var z = {};"
            + "function foo(a){z = {};return true;}; "
            + "function x() {"
            + "var JSCompiler_temp_const$jscomp$1=z;"
            + "var JSCompiler_temp_const$jscomp$0=bar();"
            + "var JSCompiler_inline_result$jscomp$2;"
            + "{"
            + "z= {};"
            + "JSCompiler_inline_result$jscomp$2 = true;"
            + "}"
            + "JSCompiler_temp_const$jscomp$1[JSCompiler_temp_const$jscomp$0] = "
            + "JSCompiler_inline_result$jscomp$2;"
            + "}",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineReferenceInExpression17() {
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "var z = {};"
            + "function foo(a){z = {};return true;}; "
            + "function x() { z.y.x.gack = foo(1) }",
        "var z = {};"
            + "function foo(a){z = {};return true;}; "
            + "function x() {"
            + "var JSCompiler_temp_const$jscomp$0=z.y.x;"
            + "var JSCompiler_inline_result$jscomp$1;"
            + "{"
            + "z= {};"
            + "JSCompiler_inline_result$jscomp$1 = true;"
            + "}"
            + "JSCompiler_temp_const$jscomp$0.gack = JSCompiler_inline_result$jscomp$1;"
            + "}",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineWithinCalls1() {
    // Call in within a call
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "function foo(){return _g;}; function x() {1 + foo()() }",
        "function foo(){return _g;}; "
            + "function x() { var JSCompiler_inline_result$jscomp$0;"
            + "{JSCompiler_inline_result$jscomp$0=_g;}"
            + "1 + JSCompiler_inline_result$jscomp$0() }",
        "foo",
        INLINE_BLOCK);
  }

  // TODO(nicksantos): Re-enable with side-effect detection.
  @Test
  @Ignore
  public void testInlineWithinCalls2() {
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "/** @nosideeffects */ function foo(){return true;}; " + "function x() {1 + _g(foo()) }",
        "function foo(){return true;}; "
            + "function x() { {var JSCompiler_inline_result$jscomp$0; "
            + "JSCompiler_inline_result$jscomp$0=true;}"
            + "1 + _g(JSCompiler_inline_result$jscomp$0) }",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testInlineAssignmentToConstant() {
    // Call in within a call
    allowDecomposition = true;
    helperInlineReferenceToFunction(
        "function foo(){return _g;}; function x(){var CONSTANT_RESULT = foo(); }",
        "function foo(){return _g;}; "
            + "function x() {"
            + "  var JSCompiler_inline_result$jscomp$0;"
            + "  {JSCompiler_inline_result$jscomp$0=_g;}"
            + "  var CONSTANT_RESULT = JSCompiler_inline_result$jscomp$0;"
            + "}",
        "foo",
        INLINE_BLOCK);
  }

  @Test
  public void testBug1897706() {
    helperInlineReferenceToFunction(
        "function foo(a){}; foo(x())",
        "function foo(a){}; {var a$jscomp$inline_0=x()}",
        "foo", INLINE_BLOCK);

    helperInlineReferenceToFunction(
        "function foo(a){bar()}; foo(x())",
        "function foo(a){bar()}; {var a$jscomp$inline_0=x();bar()}",
        "foo", INLINE_BLOCK);

    helperInlineReferenceToFunction(
        "function foo(a,b){bar()}; foo(x(),y())",
        "function foo(a,b){bar()};" +
        "{var a$jscomp$inline_0=x();var b$jscomp$inline_1=y();bar()}",
        "foo", INLINE_BLOCK);
  }

  @Test
  public void testIssue1101a() {
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return modifiyX() + a;} foo(x);", "foo",
        INLINE_DIRECT);
  }

  @Test
  public void testIssue1101b() {
    helperCanInlineReferenceToFunction(CanInlineResult.NO,
        "function foo(a){return (x.prop = 2),a;} foo(x.prop);", "foo",
        INLINE_DIRECT);
  }

  @Test
  public void testArgumentsReferenceInArrowFunction() {
    assertThat(
            doesFunctionMeetMinimumRequirements(
                "function foo() { return () => arguments; }", "foo"))
        .isFalse();
  }

  @Test
  public void testArgumentsReferenceInNestedVanillaFunction() {
    assertThat(
            doesFunctionMeetMinimumRequirements(
                "function foo() { return function() { return arguments; }; }", "foo"))
        .isTrue();
  }

  /**
   * Test case
   *
   * var a = {}, b = {}
   * a.test = "a", b.test = "b"
   * c = a;
   * foo() { c=b; return "a" }
   * c.teste
   *
   */

  public void helperCanInlineReferenceToFunction(
      final CanInlineResult expectedResult,
      final String code,
      final String fnName,
      final InliningMode mode) {
    final Compiler compiler = new Compiler();
    final FunctionInjector injector = new FunctionInjector(
        compiler, compiler.getUniqueNameIdSupplier(), allowDecomposition,
        assumeStrictThis,
        assumeMinimumCapture);
    final Node tree = parse(compiler, code);

    final Node fnNode = findFunction(tree, fnName);
    final ImmutableSet<String> unsafe =
        ImmutableSet.copyOf(FunctionArgumentInjector.findModifiedParameters(fnNode));

    // can-inline tester
    Method tester =
        new Method() {
          @Override
          public boolean call(NodeTraversal t, Node n, Node parent) {
            Reference ref = new Reference(n, t.getScope(), t.getModule(), mode);
            CanInlineResult result =
                injector.canInlineReferenceToFunction(
                    ref,
                    fnNode,
                    unsafe,
                    NodeUtil.referencesThis(fnNode),
                    NodeUtil.containsFunction(NodeUtil.getFunctionBody(fnNode)));
            assertThat(result).isEqualTo(expectedResult);
            return true;
          }
        };

    compiler.resetUniqueNameId();
    TestCallback test = new TestCallback(fnName, tester);
    NodeTraversal.traverse(compiler, tree, test);
  }

  private void validateSourceInfo(Compiler compiler, Node subtree) {
    (new LineNumberCheck(compiler)).setCheckSubTree(subtree);
    // Source information problems are reported as compiler errors.
    if (compiler.getErrorCount() != 0) {
      String msg = "Error encountered: ";
      for (JSError err : compiler.getErrors()) {
        msg += err + "\n";
      }
      assertWithMessage(msg).that(compiler.getErrorCount()).isEqualTo(0);
    }
  }

  public void helperInlineReferenceToFunction(
      String code, final String expectedResult,
      final String fnName, final InliningMode mode) {
    final Compiler compiler = new Compiler();
    final FunctionInjector injector = new FunctionInjector(
        compiler, compiler.getUniqueNameIdSupplier(), allowDecomposition,
        assumeStrictThis,
        assumeMinimumCapture);

    List<SourceFile> externsInputs = ImmutableList.of(
        SourceFile.fromCode("externs", ""));

    CompilerOptions options = new CompilerOptions();
    options.setCodingConvention(new GoogleCodingConvention());
    compiler.init(externsInputs, ImmutableList.of(
        SourceFile.fromCode("code", code)), options);
    Node parseRoot = compiler.parseInputs();
    Node externsRoot = parseRoot.getFirstChild();
    final Node tree = parseRoot.getLastChild();
    assertThat(tree).isNotNull();
    assertThat(tree != externsRoot).isTrue();

    final Node expectedRoot = parseExpected(new Compiler(), expectedResult);

    Node mainRoot = tree;
    MarkNoSideEffectCalls mark = new MarkNoSideEffectCalls(compiler);
    mark.process(externsRoot, mainRoot);

    Normalize normalize = new Normalize(compiler, false);
    normalize.process(externsRoot, mainRoot);
    compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED);

    final Node fnNode = findFunction(tree, fnName);
    assertThat(fnNode).isNotNull();
    final ImmutableSet<String> unsafe =
        ImmutableSet.copyOf(FunctionArgumentInjector.findModifiedParameters(fnNode));
    assertThat(fnNode).isNotNull();

    // inline tester
    Method tester =
        new Method() {
          @Override
          public boolean call(NodeTraversal t, Node n, Node parent) {
            Reference ref = new Reference(n, t.getScope(), t.getModule(), mode);
            CanInlineResult canInline =
                injector.canInlineReferenceToFunction(
                    ref,
                    fnNode,
                    unsafe,
                    NodeUtil.referencesThis(fnNode),
                    NodeUtil.containsFunction(NodeUtil.getFunctionBody(fnNode)));
            assertWithMessage("canInlineReferenceToFunction should not be CAN_NOT_INLINE")
                .that(canInline)
                .isNotEqualTo(CanInlineResult.NO);
            if (allowDecomposition) {
              assertWithMessage(
                      "canInlineReferenceToFunction should be CAN_INLINE_AFTER_DECOMPOSITION")
                  .that(CanInlineResult.AFTER_PREPARATION)
                  .isSameAs(canInline);

              Set<String> knownConstants = new HashSet<>();
              injector.setKnownConstants(knownConstants);
              injector.maybePrepareCall(ref);

              assertWithMessage("canInlineReferenceToFunction should be CAN_INLINE")
                  .that(canInline)
                  .isNotEqualTo(CanInlineResult.YES);
            }

            Node result = injector.inline(ref, fnName, fnNode);
            validateSourceInfo(compiler, result);
            String explanation = expectedRoot.checkTreeEquals(tree.getFirstChild());
            assertWithMessage(
                    ""
                        + "\nExpected: "
                        + toSource(expectedRoot)
                        + "\nResult:   "
                        + toSource(tree.getFirstChild())
                        + "\n"
                        + explanation)
                .that(explanation)
                .isNull();
            return true;
          }
        };

    compiler.resetUniqueNameId();

    ChangeVerifier verifier = new ChangeVerifier(compiler).snapshot(mainRoot);
    TestCallback test = new TestCallback(fnName, tester);
    NodeTraversal.traverse(compiler, tree, test);
    verifier.checkRecordedChanges("helperInlineReferenceToFunction", mainRoot);
  }

  /**
   * Calls {@link FunctionInjector#doesFunctionMeetMinimumRequirements(String, Node)}
   *
   * <p>This method is called as a prerequisite to checking if a particular reference is inlinable
   */
  public boolean doesFunctionMeetMinimumRequirements(final String code, final String fnName) {
    final Compiler compiler = new Compiler();
    final FunctionInjector injector =
        new FunctionInjector(
            compiler,
            compiler.getUniqueNameIdSupplier(),
            allowDecomposition,
            assumeStrictThis,
            assumeMinimumCapture);
    final Node tree = parse(compiler, code);

    final Node fnNode = findFunction(tree, fnName);
    return injector.doesFunctionMeetMinimumRequirements(fnName, fnNode);
  }

  interface Method {
    boolean call(NodeTraversal t, Node n, Node parent);
  }

  static class TestCallback implements Callback {

    private final String callname;
    private final Method method;
    private boolean complete = false;

    TestCallback(String callname, Method method) {
      this.callname = callname;
      this.method = method;
    }

    @Override
    public boolean shouldTraverse(
        NodeTraversal nodeTraversal, Node n, Node parent) {
      return !complete;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall()) {
        Node callee;
        if (NodeUtil.isGet(n.getFirstChild())) {
          callee = n.getFirstFirstChild();
        } else {
          callee = n.getFirstChild();
        }

        if (callee.isName() && callee.getString().equals(callname)) {
          complete = method.call(t, n, parent);
        }
      }

      if (parent == null) {
        assertThat(complete).isTrue();
      }
    }
  }

  private static Node findFunction(Node n, String name) {
    if (n.isFunction()) {
      if (n.getFirstChild().getString().equals(name)) {
        return n;
      }
    }

    for (Node c : n.children()) {
      Node result = findFunction(c, name);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  private static Node prep(String js) {
    Compiler compiler = new Compiler();
    Node n = compiler.parseTestCode(js);
    assertThat(compiler.getErrorCount()).isEqualTo(0);
    return n.getFirstChild();
  }

  private static Node parse(Compiler compiler, String js) {
    Node n = compiler.parseTestCode(js);
    assertThat(compiler.getErrorCount()).isEqualTo(0);
    return n;
  }

  private static Node parseExpected(Compiler compiler, String js) {
    Node n = compiler.parseTestCode(js);
    String message = "Unexpected errors: ";
    JSError[] errs = compiler.getErrors();
    for (JSError element : errs) {
      message += "\n" + element;
    }
    assertWithMessage(message).that(compiler.getErrorCount()).isEqualTo(0);
    return n;
  }

  private static String toSource(Node n) {
    return new CodePrinter.Builder(n)
        .setPrettyPrint(false)
        .setLineBreak(false)
        .setSourceMap(null)
        .build();
  }

}
