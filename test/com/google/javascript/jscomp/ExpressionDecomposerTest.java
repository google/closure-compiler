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
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.ExpressionDecomposer.DecompositionType;
import com.google.javascript.jscomp.Normalize.NormalizeStatements;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ExpressionDecomposer} */
// Note: functions "foo" and "goo" are external functions in the helper.
@RunWith(JUnit4.class)
public final class ExpressionDecomposerTest {
  private final Set<String> knownConstants = new HashSet<>();

  /** The language out to set in the compiler options. If null, use the default. */
  private @Nullable LanguageMode languageOut;

  // Whether we should run type checking and test the type information in the output expression
  private boolean shouldTestTypes;

  @Before
  public void setUp() {
    knownConstants.clear();
    shouldTestTypes = true;
    languageOut = null;
  }

  @Test
  public void testWindowLocationAssign() {
    // avoid decomposing `window.location.assign` when the output code could
    // end up running on IE11. See more explanation in ExpressionDecomposer.java.
    languageOut = LanguageMode.ECMASCRIPT5;
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "window.location.assign(foo())", exprMatchesStr("foo()"));
    helperMoveExpression(
        "window.location.assign(foo())",
        exprMatchesStr("foo()"),
        """
        var result$jscomp$0 = foo();
        window.location.assign(result$jscomp$0)
        """);

    // confirm that the default behavior does not treat window.location.assign
    // specially
    languageOut = null;
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "window.location.assign(foo())", exprMatchesStr("foo()"));
    helperExposeExpression(
        "window.location.assign(foo())",
        exprMatchesStr("foo()"),
        """
        var temp_const$jscomp$1 = window.location;
        var temp_const$jscomp$0 = temp_const$jscomp$1.assign;
        temp_const$jscomp$0.call(temp_const$jscomp$1, foo());
        """);
  }

  @Test
  public void testObjectDestructuring_withComputedKey_doesNotCrash() {
    // computed prop is found to be decomposable
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "var a; ({ [foo()]: a} = obj);", exprMatchesStr("foo()"));

    // TODO(b/339040894): Fix this crash.
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () ->
                helperExposeExpression(
                    "var a; ({ [foo()]: a} = obj);", //
                    exprMatchesStr("foo()"),
                    """
                    var a;
                    var temp_const$jscomp$0 = obj;
                    var temp_const$jscomp$1 = foo();
                    ({ [temp_const$jscomp$1]: a} = temp_const$jscomp$0);
                    """));
    assertThat(ex).hasMessageThat().contains("exposeExpression exposed nothing");
  }

  @Test
  public void testCannotExpose_expression1() {
    // Can't move or decompose some classes of expressions.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "while(foo());", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "while(x = goo()&&foo()){}", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "while(x += goo()&&foo()){}", exprMatchesStr("foo()"));

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "do{}while(foo());", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "for(;foo(););", exprMatchesStr("foo()"));
    // This case could be supported for loops without conditional continues
    // by moving the increment into the loop body.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "for(;;foo());", exprMatchesStr("foo()"));
    helperCanExposeExpression(DecompositionType.MOVABLE, "for(foo();;);", exprMatchesStr("foo()"));

    // This is potentially doable but a bit too complex currently.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "switch(1){case foo():;}", exprMatchesStr("foo()"));
  }

  @Test
  public void testCanExposeExpression2() {
    helperCanExposeExpression(DecompositionType.MOVABLE, "foo()", exprMatchesStr("foo()"));
    helperCanExposeExpression(DecompositionType.MOVABLE, "x = foo()", exprMatchesStr("foo()"));
    helperCanExposeExpression(DecompositionType.MOVABLE, "var x = foo()", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "const x = foo()", exprMatchesStr("foo()"));
    helperCanExposeExpression(DecompositionType.MOVABLE, "let x = foo()", exprMatchesStr("foo()"));
    helperCanExposeExpression(DecompositionType.MOVABLE, "if(foo()){}", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "switch(foo()){}", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "switch(foo()){}", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "function f(){ return foo();}", exprMatchesStr("foo()"));

    helperCanExposeExpression(DecompositionType.MOVABLE, "x = foo() && 1", exprMatchesStr("foo()"));
    helperCanExposeExpression(DecompositionType.MOVABLE, "x = foo() || 1", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "x = foo() ? 0 : 1", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "(function(a){b = a})(foo())", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "function f(){ throw foo();}", exprMatchesStr("foo()"));
  }

  @Test
  public void nullishCoalesceMovable() {
    helperCanExposeExpression(DecompositionType.MOVABLE, "x = foo() ?? 1", exprMatchesStr("foo()"));
  }

  @Test
  public void nullishCoalesceDecomposable() {
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "var x = null ?? foo()", exprMatchesStr("foo()"));
  }

  @Test
  public void nullishCoalesceUnDecomposable() {
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "while(x = goo()??foo()){}", exprMatchesStr("foo()"));
  }

  @Test
  public void assignCoalesceMovable() {
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x ??= goo() + foo()", exprMatchesStr("foo()"));
  }

  @Test
  public void optChainMovable() {
    helperCanExposeExpression(DecompositionType.MOVABLE, "foo()?.x", exprMatchesStr("foo()"));
    helperCanExposeExpression(DecompositionType.MOVABLE, "foo()?.[x]", exprMatchesStr("foo()"));
    helperCanExposeExpression(DecompositionType.MOVABLE, "foo()?.()", exprMatchesStr("foo()"));
  }

  @Test
  public void optChainDecomposable() {
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x?.[foo()]", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x?.(foo())", exprMatchesStr("foo()"));
  }

  @Test
  public void optChainAllowMethodCallDecomposable() {
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x?.y(foo())", exprMatchesStr("foo()"));
  }

  @Test
  public void optChainUnDecomposable() {
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "while(x = y?.[foo()]){}", exprMatchesStr("foo()"));
  }

  @Test
  public void testCanExposeExpression3() {
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x = 0 && foo()", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x = 1 || foo()", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "var x = 1 ? foo() : 0", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "const x = 1 ? foo() : 0", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "let x = 1 ? foo() : 0", exprMatchesStr("foo()"));

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "goo() && foo()", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x = goo() && foo()", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "x += goo() && foo()", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "var x = goo() && foo()", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "const x = goo() && foo()", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "let x = goo() && foo()", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "if(goo() && foo()){}", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "switch(goo() && foo()){}", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "switch(goo() && foo()){}", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "switch(x = goo() && foo()){}", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE,
        "function f(){ return goo() && foo();}",
        exprMatchesStr("foo()"));
  }

  @Test
  public void testCanExposeExpression_compoundDeclaration_inForInitializer_firstElement() {
    // VAR will already be hoisted by `Normalize`.
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "for (var x = foo(), y = 5;;) {}", exprMatchesStr("foo()"));

    helperCanExposeExpression(
        DecompositionType.MOVABLE, "for (let x = foo(), y = 5;;) {}", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "for (const x = foo(), y = 5;;) {}", exprMatchesStr("foo()"));
  }

  @Test
  public void testCanExposeExpression_compoundDeclaration_inForInitializer_nthElement() {
    // TODO(b/121157467) FOR introduces complex scoping that isn't currently `Normalize`d.
    // Since in some cases we'd effectively end up having to `Normalize` these, decomposition just
    // bails for now.

    // VAR will already be hoisted by `Normalize`.
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "for (var x = 8, y = foo();;) {}", exprMatchesStr("foo()"));

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        "for (let x = 8, y = foo();;) {}",
        exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        "for (const x = 8, y = foo();;) {}",
        exprMatchesStr("foo()"));
  }

  @Test
  public void testCannotExpose_expression4() {
    // 'this' must be preserved in call.
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "if (goo.a(1, foo()));", exprMatchesStr("foo()"));
  }

  @Test
  public void testCannotExpose_expression5() {
    // 'this' must be preserved in call.
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "if (goo['a'](foo()));", exprMatchesStr("foo()"));
  }

  @Test
  public void testCannotExpose_expression6() {
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "z:if (goo.a(1, foo()));", exprMatchesStr("foo()"));
  }

  @Test
  public void testCanExposeExpression7() {
    // Verify calls to function expressions are movable.
    helperCanExposeExpression(
        DecompositionType.MOVABLE,
        """
        (function(map){descriptions_=map})(
          function(){
            var ret={};
            ret[INIT]='a';
            ret[MIGRATION_BANNER_DISMISS]='b';
            return ret
          }());
        """,
        compiler ->
            rootNode -> {
              // Dig out the inner IIFE call and return it as the expression we want to ensure is
              // movable.
              assertNode(rootNode).hasToken(Token.SCRIPT);
              final Node exprResult = rootNode.getOnlyChild();
              assertNode(exprResult).hasToken(Token.EXPR_RESULT);
              final Node outerCall = exprResult.getOnlyChild();
              assertNode(outerCall).isCall();
              final Node innerIifeCall = outerCall.getSecondChild();
              assertNode(innerIifeCall).isCall();
              return innerIifeCall;
            });
  }

  @Test
  public void testCanExposeExpression8() {
    // Can it be decompose?
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE,
        """
        HangoutStarter.prototype.launchHangout = function() {
          var self = a.b;
          var myUrl = new goog.Uri(
              getDomServices_(self).getDomHelper().getWindow().location.href);
        };
        """,
        exprMatchesStr("getDomServices_(self)"));

    // Verify it is properly expose the target expression.
    helperExposeExpression(
        """
        HangoutStarter.prototype.launchHangout = function() {
          var self = a.b;
          var myUrl =
              new goog.Uri(getDomServices_(self).getDomHelper().getWindow().location.href);
        };
        """,
        exprMatchesStr("getDomServices_(self)"),
        """
        HangoutStarter.prototype.launchHangout = function() {
          var self = a.b;
          var temp_const$jscomp$0 = goog.Uri;
          var myUrl = new temp_const$jscomp$0(
              getDomServices_(self).getDomHelper().getWindow().location.href);
        }
        """);

    // Verify the results can be properly moved.
    helperMoveExpression(
        """
        HangoutStarter.prototype.launchHangout = function() {
          var self = a.b;
          var temp_const$jscomp$0 = goog.Uri;
          var myUrl = new temp_const$jscomp$0(
              getDomServices_(self).getDomHelper().getWindow().location.href);
        }
        """,
        exprMatchesStr("getDomServices_(self)"),
        """
        HangoutStarter.prototype.launchHangout = function() {
          var self=a.b;
          var temp_const$jscomp$0=goog.Uri;
          var result$jscomp$0=getDomServices_(self);
          var myUrl=new temp_const$jscomp$0(
              result$jscomp$0.getDomHelper().getWindow().location.href);
        }
        """);
  }

  @Test
  public void testCannotExpose_expression9() {
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        "function *f() { for (let x of yield y) {} }",
        exprMatchesStr("yield y"));
  }

  @Test
  public void testCannotExpose_forAwaitOf() {
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        "async function *f() { for await (let x of yield y) {} }",
        exprMatchesStr("yield y"));
  }

  @Test
  public void testCanExposeExpression10() {
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        "function *f() { for (let x in yield y) {} }",
        exprMatchesStr("yield y"));
  }

  @Test
  public void testCannotExpose_expression11() {
    // expressions in parameter lists
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "function f(x = foo()) {}", exprMatchesStr("foo()"));

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "function f({[foo()]: x}) {}", exprMatchesStr("foo()"));

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "(function (x = foo()) {})()", exprMatchesStr("foo()"));

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        "(function ({[foo()]: x}) {})()",
        exprMatchesStr("foo()"));
  }

  @Test
  public void testCanExpose_aCall_withSpreadSibling() {
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "f(...x, y());", exprMatchesStr("y()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "f(y(), ...x);", exprMatchesStr("y()"));

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "new D(...x, y());", exprMatchesStr("y()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "new D(y(), ...x);", exprMatchesStr("y()"));

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "[...x, y()];", exprMatchesStr("y()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "({...x, z: y()});", exprMatchesStr("y()"));

    // Array- and object-literal instantiations cannot be side-effected.
    helperCanExposeExpression(DecompositionType.MOVABLE, "[y(), ...x];", exprMatchesStr("y()"));
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "({z: y(), ...x});", exprMatchesStr("y()"));

    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "f(...y());", exprMatchesStr("y()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "f(...y(), x);", exprMatchesStr("y()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "f(x, ...y());", exprMatchesStr("y()"));

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "f(...x, x, y());", exprMatchesStr("y()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "f(...x, ...x, y());", exprMatchesStr("y()"));
  }

  @Test
  public void testCanExpose_anExpression_withSpreadRelative_ifInDifferentFunction() {
    // TODO(b/121004488): There are potential decompositions that weren't implemented.
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "f(function() { [...x]; }, y());", exprMatchesStr("y()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "f(function() { ({...x}); }, y());", exprMatchesStr("y()"));

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "f(y(), () => [...x]);", exprMatchesStr("y()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "f(y(), () => ({...x}));", exprMatchesStr("y()"));

    helperCanExposeExpression(
        DecompositionType.MOVABLE, "[() => f(...x), y()];", exprMatchesStr("y()"));

    helperCanExposeExpression(
        DecompositionType.MOVABLE,
        """
        [
           class {
             f(x) { return [...x]; }
           },
          y()
        ];
        """,
        exprMatchesStr("y()"));
  }

  @Test
  public void testCanExposeExpression12() {
    // Test destructuring rhs is evaluated before the lhs
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "const {a, b = goo()} = foo();", exprMatchesStr("foo()"));

    helperCanExposeExpression(
        DecompositionType.MOVABLE, "const [a, b = goo()] = foo();", exprMatchesStr("foo()"));

    helperCanExposeExpression(
        DecompositionType.MOVABLE, "({a, b = goo()} = foo());", exprMatchesStr("foo()"));

    // Default value expressions are conditional, which would make the expressions complex.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        // default value inside array pattern
        "[{ [foo()]: a } = goo()] = arr;",
        exprMatchesStr("foo()"));

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, //
        // computed property inside object pattern; decomposed
        "({ [foo()]: a = goo()} = arr);",
        exprMatchesStr("foo()"));

    // default value expressions are conditional, which would make the expressions complex
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, //
        // default value inside object pattern
        "({ [foo()]: a = goo()} = arr);",
        exprMatchesStr("goo()"));

    helperExposeExpression(
        """
        var Di = I(() => {
          function zv() {
            JSCOMPILER_PRESERVE(e), [getObj().propName] = CN();
          }
          function CN() {
            return [1];
          }
        });
        """,
        exprMatchesStr("CN()"),
        """
        var Di = I(() => {
          function zv() {
            var temp_const$jscomp$0 = JSCOMPILER_PRESERVE(e);
        // TODO(b/339701959): We decided to back off here because of b/338660589. But we should
        // optimize this to:
        //   var temp_const$jscomp$1 = CN();
        //   var temp_const$jscomp$2 = getObj();
        //   temp_const$jscomp$0, [temp_const$jscomp$2.propName] = temp_const$jscomp$1;
            temp_const$jscomp$0, [getObj().propName] = CN();
          }
          function CN() {
            return [1];
          }
        });
        """);

    helperExposeExpression(
        """
        var Di = I(() => {
          function zv() {
            JSCOMPILER_PRESERVE(e), ({x: getObj().propName} = CN());
          }
          function CN() {
            return {x: 1};
          }
        });
        """,
        exprMatchesStr("CN()"),
        """
        var Di = I(() => {
          function zv() {
            var temp_const$jscomp$0 = JSCOMPILER_PRESERVE(e);
        // TODO(b/339701959): We decided to back off here because of b/338246627. But we should
        // optimize this to:
        //   var temp_const$jscomp$1 = CN();
        //   var temp_const$jscomp$2 = getObj();
        //   temp_const$jscomp$0, {x: temp_const$jscomp$2.propName} = temp_const$jscomp$1;
            temp_const$jscomp$0, {x:getObj().propName} = CN();
          }
          function CN() {
            return {x: 1};
          }
        });
        """);

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE,
        """
        var Di = I(() => {
          function zv() {
            JSCOMPILER_PRESERVE(e), [f] = CN();
          }
          function CN() {
            return [1];
          }
        });
        """,
        exprMatchesStr("CN()"));

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        """
        var Di = I(() => {
          function zv() {
            JSCOMPILER_PRESERVE(e), [f = foo()] = CN();
          }
          function CN() {
            return [1];
          }
        });
        """,
        exprMatchesStr("foo()"));

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE,
        """
        var Di = I(() => {
          function zv() {
            JSCOMPILER_PRESERVE(e), ({f: g} = CN());
          }
          function CN() {
            return {f: 1};
          }
        });
        """,
        exprMatchesStr("CN()"));

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        """
        var Di = I(() => {
          function zv() {
            JSCOMPILER_PRESERVE(e), ({f: g = goo()} = CN());
          }
          function CN() {
            return {f: 1};
          }
        });
        """,
        exprMatchesStr("goo()"));
  }

  @Test
  public void testObjectDestructuring_withDefaultValue_generatesValidAST() {
    helperExposeExpression(
        "var d; ({c: d = 4} = condition ? y() :  {c: 1});",
        exprMatchesStr("y()"),
        """
        var d;
        var temp$jscomp$0;
        if (condition) {
          temp$jscomp$0 = y();
        } else {
          temp$jscomp$0 = {c: 1};
        }
        ({c: d = 4} = temp$jscomp$0);
        """);
  }

  @Test
  public void testObjectDestructuring_withDefaultValue_withComputedKey() {
    // default value expressions are conditional, which would make the expressions complex
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE,
        "var a; ({ [foo()]: a = bar()} = baz());",
        exprMatchesStr("bar()"));

    helperCanExposeExpression(
        DecompositionType.MOVABLE,
        "var a; ({ [foo()]: a = bar()} = baz());",
        exprMatchesStr("baz()"));

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE,
        "var a; ({ [foo()]: a = bar()} = baz());",
        exprMatchesStr("foo()"));
  }

  @Test
  public void testArrayDestructuring_withDefaultValue_generatesValidAST() {
    helperExposeExpression(
        "var [c = 4] = condition ? y() :  [c = 2];",
        exprMatchesStr("y()"),
        """
        var c;
        var temp$jscomp$0;
        if (condition) {
          temp$jscomp$0 = y();
        } else {
          temp$jscomp$0 = [c = 2];
        }
        [c = 4] = temp$jscomp$0;
        """);
  }

  @Test
  public void testCanExposeExpressionInTemplateLiteralSubstitution() {
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "const result = `${foo()}`;", exprMatchesStr("foo()"));

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE,
        "const obj = {f(x) {}}; obj.f(`${foo()}`);",
        exprMatchesStr("foo()"));

    helperCanExposeExpression(
        DecompositionType.MOVABLE, "const result = `${foo()} ${goo()}`;", exprMatchesStr("foo()"));

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE,
        "const result = `${foo()} ${goo()}`;",
        exprMatchesStr("goo()"));
  }

  @Test
  public void testCannotExpose_defaultValueInParamList() {
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "function fn(a = g()) {}", exprMatchesStr("g()"));
  }

  @Test
  public void testCannotExpose_defaultValueInDestructuring() {
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "let {x = fn()} = y;", exprMatchesStr("fn()"));

    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "let [x = fn()] = y;", exprMatchesStr("fn()"));
  }

  @Test
  public void testMoveExpression1() {
    // There isn't a reason to do this, but it works.
    helperMoveExpression(
        "foo()", exprMatchesStr("foo()"), "var result$jscomp$0 = foo(); result$jscomp$0;");
  }

  @Test
  public void testMoveExpression2() {
    helperMoveExpression(
        "x = foo()", exprMatchesStr("foo()"), "var result$jscomp$0 = foo(); x = result$jscomp$0;");
  }

  @Test
  public void testMoveExpression3() {
    helperMoveExpression(
        "var x = foo()",
        exprMatchesStr("foo()"),
        "var result$jscomp$0 = foo(); var x = result$jscomp$0;");
  }

  @Test
  public void testMoveExpression4() {
    helperMoveExpression(
        "const x = foo()",
        exprMatchesStr("foo()"),
        "var result$jscomp$0 = foo(); const x = result$jscomp$0;");
  }

  @Test
  public void testMoveExpression5() {
    helperMoveExpression(
        "let x = foo()",
        exprMatchesStr("foo()"),
        "var result$jscomp$0 = foo(); let x = result$jscomp$0;");
  }

  @Test
  public void testMoveExpression6() {
    helperMoveExpression(
        "if(foo()){}",
        exprMatchesStr("foo()"),
        "var result$jscomp$0 = foo(); if (result$jscomp$0);");
  }

  @Test
  public void testMoveExpression7() {
    helperMoveExpression(
        "switch(foo()){}",
        exprMatchesStr("foo()"),
        "var result$jscomp$0 = foo(); switch(result$jscomp$0){}");
  }

  @Test
  public void testMoveExpression8() {
    helperMoveExpression(
        "switch(1 + foo()){}",
        exprMatchesStr("foo()"),
        "var result$jscomp$0 = foo(); switch(1 + result$jscomp$0){}");
  }

  @Test
  public void testMoveExpression9() {
    helperMoveExpression(
        "function f(){ return foo();}",
        exprMatchesStr("foo()"),
        "function f(){ var result$jscomp$0 = foo(); return result$jscomp$0;}");
  }

  @Test
  public void testMoveExpression10() {
    helperMoveExpression(
        "x = foo() && 1",
        exprMatchesStr("foo()"),
        "var result$jscomp$0 = foo(); x = result$jscomp$0 && 1");
  }

  @Test
  public void testMoveExpression11() {
    helperMoveExpression(
        "x = foo() || 1",
        exprMatchesStr("foo()"),
        "var result$jscomp$0 = foo(); x = result$jscomp$0 || 1");
  }

  @Test
  public void testMoveExpression12() {
    helperMoveExpression(
        "x = foo() ? 0 : 1",
        exprMatchesStr("foo()"),
        "var result$jscomp$0 = foo(); x = result$jscomp$0 ? 0 : 1");
  }

  @Test
  public void testMoveExpressionNullishCoalesce() {
    helperMoveExpression(
        "x = foo() ?? 0",
        exprMatchesStr("foo()"),
        "var result$jscomp$0 = foo(); x = result$jscomp$0 ?? 0");
  }

  @Test
  public void testMoveExpressionOptionalChain() {
    helperMoveExpression(
        "foo()?.x", exprMatchesStr("foo()"), "var result$jscomp$0 = foo(); result$jscomp$0?.x");
  }

  @Test
  public void testMoveExpression13() {
    helperMoveExpression(
        "const {a, b} = foo();",
        exprMatchesStr("foo()"),
        "var result$jscomp$0 = foo(); const {a, b} = result$jscomp$0;");
  }

  @Test
  public void testMoveExpression14() {
    helperMoveExpression(
        "({a, b} = foo());",
        exprMatchesStr("foo()"),
        "var result$jscomp$0 = foo(); ({a, b} = result$jscomp$0);");
  }

  /* Decomposition tests. */

  @Test
  public void testExposeExpression1() {
    helperExposeExpression(
        "x = 0 && foo()",
        exprMatchesStr("foo()"),
        "var temp$jscomp$0; if (temp$jscomp$0 = 0) temp$jscomp$0 = foo(); x = temp$jscomp$0;");
  }

  @Test
  public void testExposeExpression2() {
    helperExposeExpression(
        "x = 1 || foo()",
        exprMatchesStr("foo()"),
        "var temp$jscomp$0; if (temp$jscomp$0 = 1); else temp$jscomp$0=foo(); x = temp$jscomp$0;");
  }

  @Test
  public void testExposeExpression3() {
    helperExposeExpression(
        "var x = 1 ? foo() : 0",
        exprMatchesStr("foo()"),
        """
        var temp$jscomp$0;
        if (1) temp$jscomp$0 = foo(); else temp$jscomp$0 = 0;
        var x = temp$jscomp$0;
        """);
  }

  @Test
  public void testExposeExpression4() {
    helperExposeExpression(
        "const x = 1 ? foo() : 0",
        exprMatchesStr("foo()"),
        """
        var temp$jscomp$0;
        if (1) temp$jscomp$0 = foo(); else temp$jscomp$0 = 0;
        const x = temp$jscomp$0;
        """);
  }

  @Test
  public void testExposeExpression5() {
    helperExposeExpression(
        "let x = 1 ? foo() : 0",
        exprMatchesStr("foo()"),
        """
        var temp$jscomp$0;
        if (1) temp$jscomp$0 = foo(); else temp$jscomp$0 = 0;
        let x = temp$jscomp$0;
        """);
  }

  @Test
  public void testExposeExpression6() {
    helperExposeExpression("goo() && foo()", exprMatchesStr("foo()"), "if (goo()) foo();");
  }

  @Test
  public void exposeExpressionNullishCoalesceNoResult() {
    helperExposeExpression(
        "goo() ?? foo()",
        exprMatchesStr("foo()"),
        """
        var temp$jscomp$1;
        if((temp$jscomp$1 = goo()) != null) temp$jscomp$1;
        else foo()
        """);
  }

  @Test
  public void exposeExpressionOptionalGetElem() {
    helperExposeExpression(
        "a = x?.[foo()]",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        var temp$jscomp$1;
        if ((temp_const$jscomp$0 = x) == null) {
          temp$jscomp$1 = void 0;
        } else {
          temp$jscomp$1 = temp_const$jscomp$0[foo()];
        }
        a = temp$jscomp$1;
        """);
  }

  @Test
  public void exposeExpressionOptChainCallChain() {
    helperExposeExpression(
        "a = x?.(a).y.z[foo()]",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        var temp$jscomp$1;
        if ((temp_const$jscomp$0 = x) == null) {
          temp$jscomp$1 = void 0;
        } else {
          var temp_const$jscomp$2 = temp_const$jscomp$0(a).y.z;
          temp$jscomp$1 = temp_const$jscomp$2[foo()];
        }
        a = temp$jscomp$1;
        """);
  }

  @Test
  public void exposeExpressionOptChainCallChainNoResult() {
    helperExposeExpression(
        "x?.(a)[y].z[foo()]",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        if ((temp_const$jscomp$0 = x) == null) {
          void 0;
        } else {
          var temp_const$jscomp$2 = temp_const$jscomp$0(a)[y].z;
          temp_const$jscomp$2[foo()];
        }
        """);
  }

  @Test
  public void exposeExpressionOptionalGetPropChain() {
    helperExposeExpression(
        "a = x?.y.z[foo()]",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        var temp$jscomp$1;
        if ((temp_const$jscomp$0 = x) == null) {
          temp$jscomp$1 = void 0;
        } else {
          var temp_const$jscomp$2 = temp_const$jscomp$0.y.z;
          temp$jscomp$1 = temp_const$jscomp$2[foo()];
        }
        a = temp$jscomp$1;
        """);
  }

  @Test
  public void exposeExpressionOptionalGetPropChainNoResult() {
    helperExposeExpression(
        "x?.y.z[foo()]",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        if ((temp_const$jscomp$0 = x) == null) {
          void 0;
        } else {
          var temp_const$jscomp$2 = temp_const$jscomp$0.y.z;
          temp_const$jscomp$2[foo()];
        }
        """);
  }

  @Test
  public void exposeExpressionOptionalGetElemChain() {
    helperExposeExpression(
        "a = x?.[y].z[foo()];",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        var temp$jscomp$1;
        if ((temp_const$jscomp$0 = x) == null) {
          temp$jscomp$1 = void 0;
        } else {
          var temp_const$jscomp$2 = temp_const$jscomp$0[y].z;
          temp$jscomp$1 = temp_const$jscomp$2[foo()];
        }
        a = temp$jscomp$1;
        """);
  }

  @Test
  public void exposeExpressionOptionalGetElemChainNoResult() {
    helperExposeExpression(
        "x?.[y].z[foo()]",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        if ((temp_const$jscomp$0 = x) == null) {
          void 0;
        } else {
          var temp_const$jscomp$2 = temp_const$jscomp$0[y].z;
          temp_const$jscomp$2[foo()];
        }
        """);
  }

  @Test
  public void exposeExpressionOptionalGetElemWithCall() {
    helperExposeExpression(
        "a = x.y?.[z](foo())",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        var temp$jscomp$1;
        if ((temp_const$jscomp$0 = x.y) == null) {
          temp$jscomp$1 = void 0;
        } else {
          var temp_const$jscomp$3 = temp_const$jscomp$0;
          var temp_const$jscomp$2 = temp_const$jscomp$3[z];
          temp$jscomp$1 = temp_const$jscomp$2.call(temp_const$jscomp$3, foo());
        }
        a = temp$jscomp$1;
        """);
  }

  @Test
  public void exposeExpressionGetElemWithOptChainCall() {
    helperExposeExpression(
        "a = x.y[z]?.(foo(), d)",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        let temp_const$jscomp$1;
        var temp$jscomp$2;
        if ((temp_const$jscomp$1 = (temp_const$jscomp$0 = x.y)[z]) == null) {
          temp$jscomp$2 = void 0;
        } else {
          temp$jscomp$2 = temp_const$jscomp$1.call(temp_const$jscomp$0, foo(), d);
        }
        a = temp$jscomp$2;
        """);
  }

  @Test
  public void exposeExpressionOptionalGetPropWithCall() {
    helperExposeExpression(
        "a = x.y?.z(foo(1))",
        exprMatchesStr("foo(1)"),
        """
        let temp_const$jscomp$0;
        var temp$jscomp$1;
        if ((temp_const$jscomp$0 = x.y) == null) {
          temp$jscomp$1 = void 0;
        } else {
          var temp_const$jscomp$3 = temp_const$jscomp$0;
          var temp_const$jscomp$2 = temp_const$jscomp$3.z;
          temp$jscomp$1 = temp_const$jscomp$2.call(temp_const$jscomp$3, foo(1));
        }
        a = temp$jscomp$1;
        """);
  }

  @Test
  public void exposeExpressionOptionalGetPropWithCallTwiceRewriteCall() {
    helperExposeExpression(
        "a = x.y?.z(foo(1))",
        exprMatchesStr("foo(1)"),
        """
        let temp_const$jscomp$0;
        var temp$jscomp$1;
        if ((temp_const$jscomp$0 = x.y) == null) {
          temp$jscomp$1 = void 0;
        } else {
          var temp_const$jscomp$3 = temp_const$jscomp$0;
          var temp_const$jscomp$2 = temp_const$jscomp$3.z;
          temp$jscomp$1 = temp_const$jscomp$2.call(temp_const$jscomp$3, foo(1));
        }
        a = temp$jscomp$1;
        """);
  }

  @Test
  public void exposeExpressionGetPropWithOptChainCall() {
    helperExposeExpression(
        "a = x.y.z?.(foo())",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        let temp_const$jscomp$1;
        var temp$jscomp$2;
        if ((temp_const$jscomp$1 = (temp_const$jscomp$0 = x.y).z) == null) {
          temp$jscomp$2 = void 0;
        } else {
          temp$jscomp$2 = temp_const$jscomp$1.call(temp_const$jscomp$0, foo());
        }
        a = temp$jscomp$2;
        """);
  }

  @Test
  public void exposeExpressionNewOptChainAfterRewriteCall() {
    helperExposeExpression(
        "a = x?.y(foo())?.z.q",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        let temp_const$jscomp$1;
        var temp$jscomp$2;
        if ((temp_const$jscomp$0 = x) == null) {
          temp$jscomp$2 = void 0;
        } else {
          var temp_const$jscomp$4 = temp_const$jscomp$0;
          var temp_const$jscomp$3 = temp_const$jscomp$4.y;
          temp$jscomp$2 =
              (temp_const$jscomp$1 =
                  temp_const$jscomp$3.call(temp_const$jscomp$4, foo())) == null
                      ? void 0 : temp_const$jscomp$1.z.q;
        }
        a = temp$jscomp$2;
        """);
  }

  @Test
  public void exposeExpressionNewOptChainAfter() {
    helperExposeExpression(
        "a = x?.y[foo()]?.z.q",
        exprMatchesStr("foo()"),
"""
let temp_const$jscomp$0;
let temp_const$jscomp$1;
var temp$jscomp$2;
if ((temp_const$jscomp$0 = x) == null) {
  temp$jscomp$2 = void 0;
} else {
  var temp_const$jscomp$3 = temp_const$jscomp$0.y;
  temp$jscomp$2 = (temp_const$jscomp$1 = temp_const$jscomp$3[foo()]) == null ? void 0 : temp_const$jscomp$1.z.q;
}
a = temp$jscomp$2;
""");
  }

  @Test
  public void exposeExpressionNotImmediatelyFollowedByNewChain() {
    helperExposeExpression(
        "a = x?.y[foo()].z.q?.b.c",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        let temp_const$jscomp$1;
        var temp$jscomp$2;
        if ((temp_const$jscomp$0 = x) == null) {
          temp$jscomp$2 = void 0;
        } else {
          var temp_const$jscomp$3 = temp_const$jscomp$0.y;
          temp$jscomp$2 = (temp_const$jscomp$1 = temp_const$jscomp$3[foo()].z.q) == null
              ? void 0 : temp_const$jscomp$1.b.c;
        }
        a = temp$jscomp$2;
        """);
  }

  @Test
  public void exposeExpressionBreakingOutOfOptionalChain() {
    helperExposeExpression(
        "a = (x?.y[foo()]).z.q",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        var temp$jscomp$1;
        if ((temp_const$jscomp$0 = x) == null) {
          temp$jscomp$1 = void 0;
        } else {
          var temp_const$jscomp$2 = temp_const$jscomp$0.y;
          temp$jscomp$1 = temp_const$jscomp$2[foo()];
        }
        a = temp$jscomp$1.z.q;
        """);
  }

  @Test
  public void exposeExpressionCallBreakingOutOfOptionalChain() {
    shouldTestTypes = false;
    // Performing a non-optional call on an optional chain is not good coding, because you could
    // end up trying to call `undefined` as a function, but it is allowed and must be supported.
    helperExposeExpression(
        "a = (x?.y.z)(foo())",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        let temp_const$jscomp$1;
        var temp_const$jscomp$3 =
            (temp_const$jscomp$0 = x) == null
                ? void 0 : (temp_const$jscomp$1 = temp_const$jscomp$0.y).z;
        // This double .call is unfortunate but not incorrect.
        // Maybe we should make the ExpressionDecomposer recognize `.call` and avoid creating
        // another one? We'd run the risk of breaking "real" methods called `.call`, which
        // are allowed.
        var temp_const$jscomp$2 = temp_const$jscomp$3.call;
        // The temp_const$jscomp$1 argument gets type '?' when we decompose,
        // which is correct, but TypeInference on this expected code appears to give it
        // `undefined`. This is why we've set `shouldTestTypes = false;` above
        a = temp_const$jscomp$2.call(temp_const$jscomp$3, temp_const$jscomp$1, foo());
        """);
  }

  @Test
  public void exposeExpressionFreeCallBreakingOutOfOptionalChain() {
    // Performing a non-optional call on an optional chain is not good coding, because you could
    // end up trying to call `undefined` as a function, but it is allowed and must be supported.
    helperExposeExpression(
        "a = (x?.y.z())(foo())",
        exprMatchesStr("foo()"),
        """
        var temp_const$jscomp$0 = x?.y.z();
        a = temp_const$jscomp$0(foo());
        """);
  }

  @Test
  public void exposeExpressionCallAtEndOfOptionalChain() {
    helperExposeExpression(
        "a = x?.y.z(foo())",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        var temp$jscomp$1;
        if ((temp_const$jscomp$0 = x) == null) {
          temp$jscomp$1 = void 0;
        } else {
          var temp_const$jscomp$3 = temp_const$jscomp$0.y;
          var temp_const$jscomp$2 = temp_const$jscomp$3.z;
          temp$jscomp$1 = temp_const$jscomp$2.call(temp_const$jscomp$3, foo());
        }
        a = temp$jscomp$1;
        """);
  }

  @Test
  public void testBug117935266_expose_call_target() {
    helperExposeExpression(
        """
        function first() {
          alert('first');
          return '';
        }
        // alert must be preserved before the first side-effect
        alert(first().method(alert('second')).method(alert('third')));
        """,
        exprMatchesStr("first()"),
        """
        function first() {
          alert('first');
              return '';
        }
        var temp_const$jscomp$0 = alert;
        temp_const$jscomp$0(first().method(
            alert('second')).method(alert('third')));
        """);
  }

  @Test
  public void testBug117935266_move_call_target() {
    helperMoveExpression(
        """
        function first() {
          alert('first');
              return '';
        }
        var temp_const$jscomp$0 = alert;
        temp_const$jscomp$0(first().toString(
            alert('second')).toString(alert('third')));
        """,
        exprMatchesStr("first()"),
        """
        function first() {
          alert('first');
              return '';
        }
        var temp_const$jscomp$0 = alert;
        var result$jscomp$0 = first();
        temp_const$jscomp$0(result$jscomp$0.toString(
            alert('second')).toString(alert('third')));
        """);
  }

  @Test
  public void testBug117935266_expose_call_parameters() {
    helperExposeExpression(
        "alert(fn(first(), second(), third()));",
        exprMatchesStr("first()"),
        """
        var temp_const$jscomp$1 = alert;
        var temp_const$jscomp$0 = fn;
        temp_const$jscomp$1(temp_const$jscomp$0(first(), second(), third()));
        """);

    helperExposeExpression(
        "alert(fn(first(), second(), third()));",
        exprMatchesStr("second()"),
        """
        var temp_const$jscomp$2 = alert;
        var temp_const$jscomp$1 = fn;
        var temp_const$jscomp$0 = first();
        temp_const$jscomp$2(temp_const$jscomp$1(temp_const$jscomp$0, second(), third()));
        """);
  }

  @Test
  public void exposeExpressionAfterTwoOptionalChains() {
    helperExposeExpression(
        "a = x?.y.z?.q(foo());",
        exprMatchesStr("foo()"),
        """
        let temp_const$jscomp$0;
        let temp_const$jscomp$1;
        var temp$jscomp$2;
        if ((temp_const$jscomp$0 = x) == null) {
          temp$jscomp$2 = void 0;
        } else {
          var temp$jscomp$3;
          if ((temp_const$jscomp$1 = temp_const$jscomp$0.y.z) == null) {
            temp$jscomp$3 = void 0;
          } else {
            var temp_const$jscomp$5 = temp_const$jscomp$1;
            var temp_const$jscomp$4 = temp_const$jscomp$5.q;
            temp$jscomp$3 = temp_const$jscomp$4.call(temp_const$jscomp$5, foo());
          }
          temp$jscomp$2 = temp$jscomp$3;
        }
        a = temp$jscomp$2;
        """);
  }

  @Test
  public void exposeAnOptionalChain() {
    helperExposeExpression(
        "a = foo(arg1, opt?.chain())",
        exprMatchesStr("opt?.chain()"),
        """
        var temp_const$jscomp$1 = foo;
        var temp_const$jscomp$0 = arg1;
        a = temp_const$jscomp$1(temp_const$jscomp$0, opt?.chain());
        """);
  }

  @Test
  public void exposePartOfAnOptionalChain() {
    helperCanExposeExpression(
        DecompositionType.MOVABLE,
        // The non-optional part is fine to move.
        "nonOptional.part?.optional.chain?.continues()",
        exprMatchesStr("nonOptional.part"));

    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE,
        "nonOptional.part?.optional.chain?.continues()",
        // Cannot just move part of an optional chain.
        exprMatchesStr("nonOptional.part?.optional"));

    helperExposeExpression(
        "nonOptional.part?.optional.chain?.continues()",
        exprMatchesStr("nonOptional.part?.optional"),
        """
        let temp_const$jscomp$0;
        let temp_const$jscomp$1;
        if ((temp_const$jscomp$0 = nonOptional.part) == null) {
          void 0;
        } else {
        // `temp_const$jscomp$0.optional` is the expression we were trying to expose, and it
        // is now movable.
          (temp_const$jscomp$1 = temp_const$jscomp$0.optional.chain) == null
              ? void 0 : temp_const$jscomp$1.continues();
        }
        """);
  }

  @Test
  public void canExposeMethodCallee() {
    // TODO(b/161802885): This should probably return DECOMPOSABLE, because it is not safe to
    // replace `foo.bar` with a temporary variable containing its value.
    helperCanExposeExpression(DecompositionType.MOVABLE, "foo.bar()", exprMatchesStr("foo.bar"));

    // TODO(b/161802885): This should probably return DECOMPOSABLE, because it is not safe to
    // replace `foo?.bar` with a temporary variable containing its value.
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "(foo?.bar)()", exprMatchesStr("foo?.bar"));
  }

  @Test
  public void testExposeExpression7() {
    helperExposeExpression(
        "x = goo() && foo()",
        exprMatchesStr("foo()"),
        "var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo(); x = temp$jscomp$0;");
  }

  @Test
  public void exposeExpressionNullishCoalesce() {
    helperExposeExpression(
        "x = goo() ?? foo()",
        exprMatchesStr("foo()"),
        """
        var temp$jscomp$1;var temp$jscomp$0;
        if((temp$jscomp$1 = goo()) != null) temp$jscomp$0 = temp$jscomp$1;
        else temp$jscomp$0=foo(); x = temp$jscomp$0;
        """);
  }

  @Test
  public void testExposeExpression8() {
    helperExposeExpression(
        "var x = 1 + (goo() && foo())",
        exprMatchesStr("foo()"),
        """
        var temp$jscomp$0;
        if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();
        var x = 1 + temp$jscomp$0;
        """);
  }

  @Test
  public void testExposeExpression9() {
    helperExposeExpression(
        "const x = 1 + (goo() && foo())",
        exprMatchesStr("foo()"),
        """
        var temp$jscomp$0;
        if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();
        const x = 1 + temp$jscomp$0;
        """);
  }

  @Test
  public void testExposeExpression10() {
    helperExposeExpression(
        "let x = 1 + (goo() && foo())",
        exprMatchesStr("foo()"),
        """
        var temp$jscomp$0;
        if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();
        let x = 1 + temp$jscomp$0;
        """);
  }

  @Test
  public void testExposeExpression11() {
    helperExposeExpression(
        "if(goo() && foo());",
        exprMatchesStr("foo()"),
        """
        var temp$jscomp$0;
        if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();
        if(temp$jscomp$0);
        """);
  }

  @Test
  public void testExposeExpression12() {
    helperExposeExpression(
        "switch(goo() && foo()){}",
        exprMatchesStr("foo()"),
        """
        var temp$jscomp$0;
        if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();
        switch(temp$jscomp$0){}
        """);
  }

  @Test
  public void exposeExpressionNullishCoalesceSwitch() {
    helperExposeExpression(
        "switch(goo() ?? foo()){}",
        exprMatchesStr("foo()"),
        """
        var temp$jscomp$1;var temp$jscomp$0;
        if((temp$jscomp$1 = goo()) != null) temp$jscomp$0 = temp$jscomp$1;
        else temp$jscomp$0 = foo(); switch(temp$jscomp$0){}
        """);
  }

  @Test
  public void testExposeExpression13() {
    helperExposeExpression(
        "switch(1 + goo() + foo()){}",
        exprMatchesStr("foo()"),
        "var temp_const$jscomp$0 = 1 + goo(); switch(temp_const$jscomp$0 + foo()){}");
  }

  @Test
  public void testExposeExpression_inVanillaForInitializer_simpleExpression() {
    helperExposeExpression(
        "for (x = goo() + foo();;) {}",
        exprMatchesStr("foo()"),
        """
        var temp_const$jscomp$0 = goo();
        for (x = temp_const$jscomp$0 + foo();;) {}
        """);
  }

  @Test
  public void testExposeExpression_inVanillaForInitializer_usingLabel() {
    helperExposeExpression(
        "LABEL: for (x = goo() + foo();;) {}",
        exprMatchesStr("foo()"),
        """
        var temp_const$jscomp$0 = goo();
        LABEL: for (x = temp_const$jscomp$0 + foo();;) {}
        """);
  }

  @Test
  public void testExposeExpression_inVanillaForInitializer_singleDeclaration_withLetOrConst() {
    for (String declarationKeyword : ImmutableList.of("let", "const")) {
      helperExposeExpression(
          "for (" + declarationKeyword + " x = goo() + foo();;) {}",
          exprMatchesStr("foo()"),
          """
          var temp_const$jscomp$0 = goo();
          for (DECLATION_KEYWORD x = temp_const$jscomp$0 + foo();;) {}
          """
              .replace("DECLATION_KEYWORD", declarationKeyword));
    }
  }

  @Test
  public void testExposeExpression_inVanillaForInitializer_firstDeclaration_withLetOrConst() {
    for (String declarationKeyword : ImmutableList.of("let", "const")) {
      helperExposeExpression(
          "for (" + declarationKeyword + " x = goo() + foo(), y = 5;;) {}",
          exprMatchesStr("foo()"),
          """
          var temp_const$jscomp$0 = goo();
          for (DECLATION_KEYWORD x = temp_const$jscomp$0 + foo(), y = 5;;) {}
          """
              .replace("DECLATION_KEYWORD", declarationKeyword));
    }
  }

  @Test
  public void testExposeExpression14() {
    helperExposeExpression(
        "function f(){ return goo() && foo();}",
        exprMatchesStr("foo()"),
        """
        function f() {
          var temp$jscomp$0; if (temp$jscomp$0 = goo()) temp$jscomp$0 = foo();
          return temp$jscomp$0;
        }
        """);
  }

  @Test
  public void testExposeExpression15() {
    // TODO(johnlenz): We really want a constant marking pass.
    // The value "goo" should be constant, but it isn't known to be so.
    helperExposeExpression(
        "if (goo(1, goo(2), (1 ? foo() : 0)));",
        exprMatchesStr("foo()"),
        """
        var temp_const$jscomp$1 = goo;
        var temp_const$jscomp$0 = goo(2);
        var temp$jscomp$2;
        if (1) temp$jscomp$2 = foo(); else temp$jscomp$2 = 0;
        if (temp_const$jscomp$1(1, temp_const$jscomp$0, temp$jscomp$2));
        """);
  }

  @Test
  public void testExposeExpression16() {
    helperExposeExpression(
        "throw bar() && foo();",
        exprMatchesStr("foo()"),
        "var temp$jscomp$0; if (temp$jscomp$0 = bar()) temp$jscomp$0=foo(); throw temp$jscomp$0;");
  }

  @Test
  public void testExposeExpression17() {
    helperExposeExpression(
        "x.foo(y())",
        exprMatchesStr("y()"),
        """
        var temp_const$jscomp$1 = x;
        var temp_const$jscomp$0 = temp_const$jscomp$1.foo;
        temp_const$jscomp$0.call(temp_const$jscomp$1, y());
        """);
  }

  @Test
  public void testExposeFreeCall() {
    helperExposeExpression(
        "(0,x.foo)(y())",
        exprMatchesStr("y()"),
        """
        var temp_const$jscomp$0 = x.foo;
        temp_const$jscomp$0(y());
        """);
  }

  @Test
  public void testExposeTemplateLiteralFreeCall() {
    helperExposeExpression(
        "foo`${x()}${y()}`",
        exprMatchesStr("y()"),
        """
        var temp_const$jscomp$1 = foo;
        var temp_const$jscomp$0 = x();
        temp_const$jscomp$1`${temp_const$jscomp$0}${y()}`;
        """);
  }

  @Test
  public void testCanExposeTaggedTemplateLiteralInterpolation() {
    helperCanExposeExpression(DecompositionType.DECOMPOSABLE, "x`${y()}`", exprMatchesStr("y()"));
    // TODO(b/251958225): Implement decomposition for this case.
    helperCanExposeExpression(
        DecompositionType.UNDECOMPOSABLE, "x.foo`${y()}`", exprMatchesStr("y()"));
  }

  @Test
  public void testExposeExpression18() {
    helperExposeExpression(
        """
        const {a, b, c} = condition ?
          y() :
          {a: 0, b: 0, c: 1};
        """,
        exprMatchesStr("y()"),
        """
        var temp$jscomp$0;
        if (condition) {
          temp$jscomp$0 = y();
        } else {
          temp$jscomp$0 = {a: 0, b: 0, c: 1};
        }
        const {a, b, c} = temp$jscomp$0;
        """);
  }

  @Test
  public void testMoveClass1() {
    shouldTestTypes = false;
    // types don't come out quite the same before and after decomposition
    // TODO(bradfordcsmith): See TODO in helperMoveExpression()
    helperMoveExpression(
        "alert(class X {});",
        compiler -> ExpressionDecomposerTest::findClass,
        "var result$jscomp$0 = class X {}; alert(result$jscomp$0);");
  }

  @Test
  public void testMoveClass2() {
    shouldTestTypes = false;
    // types don't come out quite the same before and after decomposition
    // TODO(bradfordcsmith): See TODO in helperMoveExpression()
    helperMoveExpression(
        "console.log(1, 2, class X {});",
        compiler -> ExpressionDecomposerTest::findClass,
        "var result$jscomp$0 = class X {}; console.log(1, 2, result$jscomp$0);");
  }

  @Test
  public void testMoveYieldExpression1() {
    helperMoveExpression(
        "function *f() { return { a: yield 1, c: foo(yield 2, yield 3) }; }",
        exprMatchesStr("yield 1"),
        """
        function *f() {
          var result$jscomp$0 = yield 1;
          return { a: result$jscomp$0, c: foo(yield 2, yield 3) };
        }
        """);

    helperMoveExpression(
        "function *f() { return { a: 0, c: foo(yield 2, yield 3) }; }",
        exprMatchesStr("yield 2"),
        """
        function *f() {
          var result$jscomp$0 = yield 2;
          return { a: 0, c: foo(result$jscomp$0, yield 3) };
        }
        """);

    helperMoveExpression(
        "function *f() { return { a: 0, c: foo(1, yield 3) }; }",
        exprMatchesStr("yield 3"),
        """
        function *f() {
          var result$jscomp$0 = yield 3;
          return { a: 0, c: foo(1, result$jscomp$0) };
        }
        """);
  }

  @Test
  public void testMoveYieldExpression2() {
    helperMoveExpression(
        "function *f() { return (yield 1) || (yield 2); }",
        exprMatchesStr("yield 1"),
        """
        function *f() {
          var result$jscomp$0 = yield 1;
          return result$jscomp$0 || (yield 2);
        }
        """);
  }

  @Test
  public void testMoveYieldExpression3() {
    helperMoveExpression(
        "function *f() { return x.y(yield 1); }",
        exprMatchesStr("yield 1"),
        """
        function *f() {
          var result$jscomp$0 = yield 1;
          return x.y(result$jscomp$0);
        }
        """);
  }

  @Test
  public void testExposeYieldExpression1() {
    helperExposeExpression(
        "function *f(x) { return x || (yield 2); }",
        exprMatchesStr("yield 2"),
        """
        function *f(x) {
          var temp$jscomp$0;
          if (temp$jscomp$0=x); else temp$jscomp$0 = yield 2;
          return temp$jscomp$0
        }
        """);
  }

  @Test
  public void testExposeYieldExpression2() {
    helperExposeExpression(
        "function *f() { return x.y(yield 1); }",
        exprMatchesStr("yield 1"),
        """
        function *f() {
          var temp_const$jscomp$1 = x;
          var temp_const$jscomp$0 = temp_const$jscomp$1.y;
          return temp_const$jscomp$0.call(temp_const$jscomp$1, yield 1);
        }
        """);
  }

  @Test
  public void testExposeYieldExpression3() {
    helperExposeExpression(
        "function *f() { return g.call(yield 1); }",
        exprMatchesStr("yield 1"),
        """
        function *f() {
          var temp_const$jscomp$1 = g;
          var temp_const$jscomp$0 = temp_const$jscomp$1.call;
          return temp_const$jscomp$0.call(temp_const$jscomp$1, yield 1);
        }
        """);
  }

  @Test
  public void testExposeYieldExpression4() {
    helperExposeExpression(
        "function *f() { return g.apply([yield 1, yield 2]); }",
        exprMatchesStr("yield 1"),
        """
        function *f() {
          var temp_const$jscomp$1 = g;
          var temp_const$jscomp$0 = temp_const$jscomp$1.apply;
          return temp_const$jscomp$0.call(temp_const$jscomp$1, [yield 1, yield 2]);
        }
        """);
  }

  // Simple name on LHS of assignment-op.
  @Test
  public void testExposePlusEquals1() {
    helperExposeExpression(
        "var x = 0; x += foo() + 1",
        exprMatchesStr("foo()"),
        "var x = 0; var temp_const$jscomp$0 = x; x = temp_const$jscomp$0 + (foo() + 1);");

    helperExposeExpression(
        "var x = 0; y = (x += foo()) + x",
        exprMatchesStr("foo()"),
        "var x = 0; var temp_const$jscomp$0 = x; y = (x = temp_const$jscomp$0 + foo()) + x");
  }

  // Structure on LHS of assignment-op.
  @Test
  public void testExposePlusEquals2() {
    helperExposeExpression(
        "var x = {}; x.a += foo() + 1",
        exprMatchesStr("foo()"),
        """
        var x = {}; var temp_const$jscomp$0 = x;
        var temp_const$jscomp$1 = temp_const$jscomp$0.a;
        temp_const$jscomp$0.a = temp_const$jscomp$1 + (foo() + 1);
        """);

    helperExposeExpression(
        "var x = {}; y = (x.a += foo()) + x.a",
        exprMatchesStr("foo()"),
        """
        var x = {}; var temp_const$jscomp$0 = x;
        var temp_const$jscomp$1 = temp_const$jscomp$0.a;
        y = (temp_const$jscomp$0.a = temp_const$jscomp$1 + foo()) + x.a
        """);
  }

  // Constant object on LHS of assignment-op.
  @Test
  public void testExposePlusEquals3() {
    helperExposeExpression(
        "/** @const */ var XX = {}; XX.a += foo() + 1",
        exprMatchesStr("foo()"),
        """
        var XX = {};
        var temp_const$jscomp$0 = XX.a;
        XX.a = temp_const$jscomp$0 + (foo() + 1);
        """);

    helperExposeExpression(
        "var XX = {}; y = (XX.a += foo()) + XX.a",
        exprMatchesStr("foo()"),
        """
        var XX = {};
        var temp_const$jscomp$0 = XX.a;
        y = (XX.a = temp_const$jscomp$0 + foo()) + XX.a
        """);
  }

  // Function all on LHS of assignment-op.
  @Test
  public void testExposePlusEquals4() {
    helperExposeExpression(
        "var x = {}; goo().a += foo() + 1",
        exprMatchesStr("foo()"),
        """
        var x = {};
        var temp_const$jscomp$0 = goo();
        var temp_const$jscomp$1 = temp_const$jscomp$0.a;
        temp_const$jscomp$0.a = temp_const$jscomp$1 + (foo() + 1);
        """);

    helperExposeExpression(
        "var x = {}; y = (goo().a += foo()) + goo().a",
        exprMatchesStr("foo()"),
        """
        var x = {};
        var temp_const$jscomp$0 = goo();
        var temp_const$jscomp$1 = temp_const$jscomp$0.a;
        y = (temp_const$jscomp$0.a = temp_const$jscomp$1 + foo()) + goo().a
        """);
  }

  // Test multiple levels
  @Test
  public void testExposePlusEquals5() {
    helperExposeExpression(
        "var x = {}; goo().a.b += foo() + 1",
        exprMatchesStr("foo()"),
        """
        var x = {};
        var temp_const$jscomp$0 = goo().a;
        var temp_const$jscomp$1 = temp_const$jscomp$0.b;
        temp_const$jscomp$0.b = temp_const$jscomp$1 + (foo() + 1);
        """);

    helperExposeExpression(
        "var x = {}; y = (goo().a.b += foo()) + goo().a",
        exprMatchesStr("foo()"),
        """
        var x = {};
        var temp_const$jscomp$0 = goo().a;
        var temp_const$jscomp$1 = temp_const$jscomp$0.b;
        y = (temp_const$jscomp$0.b = temp_const$jscomp$1 + foo()) + goo().a
        """);
  }

  // Simple name on LHS of logical assignment-op.
  @Test
  public void testExposeLogicalAssignment1() {
    // Part of the work here is being done by Normalize, which converts all
    // instances of logical assignment operators into an larger expression
    // that separates the logical operation from the assignment.
    helperExposeExpression(
        "let x = 0; x ||= foo() + 1",
        exprMatchesStr("foo()"),
        """
        let x = 0;
        if (x) {
        } else {
           x = foo() + 1;
        }
        """);

    helperExposeExpression(
        "let x = 0; x &&= foo() + 1",
        exprMatchesStr("foo()"),
        """
        let x = 0;
        if (x) {
           x = foo() + 1;
        }
        """);

    helperExposeExpression(
        "let x = 0; x ??= foo() + 1",
        exprMatchesStr("foo()"),
        """
        let x = 0;
        var temp$jscomp$1;
        if ((temp$jscomp$1 = x) != null) {
           temp$jscomp$1;
        } else {
           x = foo() + 1;
        }
        """);
  }

  // Property reference on LHS of logical assignment-op.
  @Test
  public void testExposeLogicalAssignment2() {
    // Part of the work here is being done by Normalize, which converts all
    // instances of logical assignment operators into an larger expression
    // that separates the logical operation from the assignment.
    helperExposeExpression(
        "let x = {}; x.a ||= foo() + 1",
        exprMatchesStr("foo()"),
        """
        let x = {};
        let $jscomp$logical$assign$tmpm1146332801$0;
        if (($jscomp$logical$assign$tmpm1146332801$0 = x).a) {
        } else {
           var temp_const$jscomp$1 = $jscomp$logical$assign$tmpm1146332801$0;
           temp_const$jscomp$1.a = foo() + 1;
        }
        """);
    helperExposeExpression(
        "let x = {}; x[a] &&= foo() + 1",
        exprMatchesStr("foo()"),
        """
        let x = {};
        let $jscomp$logical$assign$tmpm1146332801$0;
        let $jscomp$logical$assign$tmpindexm1146332801$0;
        if (($jscomp$logical$assign$tmpm1146332801$0 = x)
            [$jscomp$logical$assign$tmpindexm1146332801$0 = a]) {
            var temp_const$jscomp$2 = $jscomp$logical$assign$tmpm1146332801$0;
            var temp_const$jscomp$1 = $jscomp$logical$assign$tmpindexm1146332801$0;
            temp_const$jscomp$2[temp_const$jscomp$1] = foo() + 1;
        }
        """);
  }

  @Test
  public void testExposeObjectLit1() {
    // Validate that getter and setters methods are seen as side-effect
    // free and that values can move past them.  We don't need to be
    // concerned with exposing the getter or setter here but the
    // decomposer does not have a method of exposing properties, only variables.
    helperMoveExpression(
        "var x = {get a() {}, b: foo()};",
        exprMatchesStr("foo()"),
        "var result$jscomp$0=foo();var x = {get a() {}, b: result$jscomp$0};");

    helperMoveExpression(
        "var x = {set a(p) {}, b: foo()};",
        exprMatchesStr("foo()"),
        "var result$jscomp$0=foo();var x = {set a(p) {}, b: result$jscomp$0};");
  }

  @Test
  public void testMoveSpread_siblingOfCall_outOfArrayLiteral_usesTempArray() {
    shouldTestTypes = false;
    // types don't come out quite the same before and after decomposition
    // TODO(bradfordcsmith): See TODO in helperMoveExpression()
    helperExposeExpression(
        "[...x, foo()];",
        exprMatchesStr("foo()"),
        """
        var temp_const$jscomp$0 = [...x];
        [...temp_const$jscomp$0, foo()];
        """);
  }

  @Test
  public void testMoveSpread_siblingOfCall_outOfObjectLiteral_usesTempObject() {
    shouldTestTypes = false;
    // types don't come out quite the same before and after decomposition
    // TODO(bradfordcsmith): See TODO in helperMoveExpression()
    helperExposeExpression(
        "({...x, y: foo()});",
        exprMatchesStr("foo()"),
        """
        var temp_const$jscomp$0 = {...x};
        ({...temp_const$jscomp$0, y: foo()});
        """);
  }

  @Test
  public void testMoveSpread_siblingOfCall_outOfFunctionCall_usesTempArray() {
    shouldTestTypes = false;
    // types don't come out quite the same before and after decomposition
    // TODO(bradfordcsmith): See TODO in helperMoveExpression()
    helperExposeExpression(
        """
        function f() { }
        f(...x, foo());
        """,
        exprMatchesStr("foo()"),
        """
        function f() { }
        var temp_const$jscomp$1 = f;
        var temp_const$jscomp$0 = [...x];
        temp_const$jscomp$1(...temp_const$jscomp$0, foo());
        """);
  }

  @Test
  public void testMoveSpreadParent_siblingOfCall_outOfFunctionCall_usesNoTempArray() {
    helperExposeExpression(
        """
        function f() { }
        f([...x], foo());
        """,
        exprMatchesStr("foo()"),
        """
        function f() { }
        var temp_const$jscomp$1 = f;
        var temp_const$jscomp$0 = [...x];
        temp_const$jscomp$1(temp_const$jscomp$0, foo());
        """);
  }

  @Test
  public void testMoveSpreadParent_siblingOfCall_outOfFunctionCall_usesNoTempObject() {
    helperExposeExpression(
        """
        function f() { }
        f({...x}, foo());
        """,
        exprMatchesStr("foo()"),
        """
        function f() { }
        var temp_const$jscomp$1 = f;
        var temp_const$jscomp$0 = {...x};
        temp_const$jscomp$1(temp_const$jscomp$0, foo());
        """);
  }

  @Test
  public void testExposeExpressionInTemplateLibSub() {
    helperExposeExpression(
        "` ${ foo() }  ${ goo() } `;",
        exprMatchesStr("goo()"),
        "var temp_const$jscomp$0 = foo(); ` ${ temp_const$jscomp$0 }  ${ goo() } `;");
  }

  @Test
  public void testExposeSubExpressionInTemplateLibSub() {
    helperExposeExpression(
        "` ${ foo() + goo() } `;",
        exprMatchesStr("goo()"),
        "var temp_const$jscomp$0 = foo(); ` ${ temp_const$jscomp$0 + goo() } `;");
  }

  @Test
  public void testMoveExpressionInTemplateLibSub() {
    helperMoveExpression(
        "` ${ foo() }  ${ goo() } `;",
        exprMatchesStr("foo()"),
        "var result$jscomp$0 = foo(); ` ${ result$jscomp$0 }  ${ goo() } `;");
  }

  @Test
  public void testExposeExpression_computedProp_withPureKey() {
    helperCanExposeExpression(
        DecompositionType.MOVABLE,
        """
        ({
          ['a' + 'b']: foo(),
        });
        """,
        exprMatchesStr("foo()"));
  }

  @Test
  public void testExposeObjectLitValue_computedProp_withImpureKey() {
    helperExposeExpression(
        """
        ({
          [goo()]: foo(),
        });
        """,
        exprMatchesStr("foo()"),
        """
        var temp_const$jscomp$0 = goo();
        ({
          [temp_const$jscomp$0]: foo(),
        });
        """);
  }

  @Test
  public void testExposeObjectLitValue_computedProp_asEarlierSibling_withImpureKeyAndValue() {
    helperExposeExpression(
        """
        ({
          [goo()]: qux(),
          bar: foo(),
        });
        """,
        exprMatchesStr("foo()"),
        """
        var temp_const$jscomp$1 = goo();
        var temp_const$jscomp$0 = qux();
        ({
          [temp_const$jscomp$1]: temp_const$jscomp$0,
          bar: foo(),
        });
        """);
  }

  @Test
  public void testExposeObjectLitValue_memberFunctions_asEarlierSiblings_arePure() {
    helperCanExposeExpression(
        DecompositionType.MOVABLE,
        """
        ({
          a() { },
          get b() { },
          set b(v) { },

          bar: foo(),
        });
        """,
        exprMatchesStr("foo()"));
  }

  @Test
  public void testMoveSuperCall() {
    helperMoveExpression(
        "class A { constructor() { super(foo()) } }",
        exprMatchesStr("foo()"),
        "class A{constructor(){var result$jscomp$0=foo();super(result$jscomp$0)}}");
  }

  @Test
  public void testMoveSuperCall_noSideEffects() {
    // String() is being used since it's known to not have side-effects.
    helperMoveExpression(
        "class A { constructor() { super(String()) } }",
        exprMatchesStr("String()"),
        "class A{constructor(){var result$jscomp$0=String();super(result$jscomp$0)}}");
  }

  @Test
  public void testExposeSuperCall() {
    helperExposeExpression(
        "class A { constructor() { super(goo(), foo()) } }",
        exprMatchesStr("foo()"),
        """
        class A{ constructor(){
           var temp_const$jscomp$0=goo();
           super(temp_const$jscomp$0, foo())
        }}
        """);
  }

  @Test
  public void testExposeSuperCall_noSideEffects() {
    // String() is being used since it's known to not have side-effects.
    helperExposeExpression(
        "class A { constructor() { super(goo(), String()) } }",
        exprMatchesStr("String()"),
        """
        class A{ constructor(){
           var temp_const$jscomp$0=goo();
           super(temp_const$jscomp$0, String())
        }}
        """);
  }

  @Test
  public void testCannotDecomposeSuperMethodCall() {
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE,
        "class A extends B { fn() { super.method(foo()) } }",
        exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE,
        "class A extends B { fn() { super['method'](foo()) } }",
        exprMatchesStr("foo()"));
  }

  @Test
  public void canMovePastFnDotCall() {
    knownConstants.add("fn");
    helperCanExposeExpression(
        DecompositionType.MOVABLE, "fn.call(foo());", exprMatchesStr("foo()"));
    helperCanExposeExpression(
        DecompositionType.DECOMPOSABLE, "unknownIfFn.call(foo());", exprMatchesStr("foo()"));
    helperMoveExpression(
        "fn.call(foo());",
        exprMatchesStr("foo()"),
        "var result$jscomp$0 = foo(); fn.call(result$jscomp$0);");
  }

  private void helperCanExposeExpression(
      DecompositionType expectedResult,
      String code,
      Function<AbstractCompiler, Function<Node, Node>> nodeFinderFn) {
    Compiler compiler = getCompiler();
    ExpressionDecomposer decomposer = createDecomposer(compiler);
    Node tree = parse(compiler, code);
    assertThat(tree).isNotNull();

    Node externsRoot = parse(compiler, "function goo() {} function foo() {}");
    assertThat(externsRoot).isNotNull();

    Node expresionNode = nodeFinderFn.apply(compiler).apply(tree);

    DecompositionType result = decomposer.canExposeExpression(expresionNode);
    assertThat(result).isEqualTo(expectedResult);
  }

  /**
   * Provides a {@code toString()} method that contains the source code a node represents where
   * possible, or some explanatory text when not possible.
   */
  private static final class NodeToSource {
    private final AbstractCompiler compiler;
    private final Node node;

    public NodeToSource(AbstractCompiler compiler, Node node) {
      this.compiler = compiler;
      this.node = node;
    }

    @Override
    public String toString() {
      if (node.isTemplateLitString()) {
        // A string part of a template literal cannot be printed as code on its own.
        return String.format("[Template literal string: '%s']", node.getRawString());
      } else if (node.isTemplateLitSub()) {
        // The template literal substitution node cannot itself be turned into source code,
        // but we can do that for the expression inside of it.
        return String.format(
            "[Template literal substitution: '%s']", compiler.toSource(node.getOnlyChild()));
      } else {
        return compiler.toSource(node);
      }
    }
  }

  private static String nodeToSource(AbstractCompiler compiler, Node node) {
    return new NodeToSource(compiler, node).toString();
  }

  private static Function<AbstractCompiler, Function<Node, Node>> exprMatchesStr(
      final String exprString) {
    return (AbstractCompiler compiler) ->
        (Node root) -> {
          // When matching trim off the trailing newline added by the compiler's pretty-print
          // option.
          Predicate<Node> isAMatch =
              (Node node) -> exprString.equals(nodeToSource(compiler, node).trim());
          Predicate<Node> containsAMatch =
              (Node node) -> nodeToSource(compiler, node).contains(exprString);
          Node matchingNode = NodeUtil.findPreorder(root, isAMatch, containsAMatch);
          assertWithMessage(
                  "Expected node `%s` was not found in `%s`", exprString, compiler.toSource(root))
              .that(matchingNode)
              .isNotNull();
          return matchingNode;
        };
  }

  private Node helperExposeExpressionThenTypeCheck(String code, Function<Node, Node> nodeFinder) {
    Compiler compiler = getCompiler();
    Node tree = parse(compiler, code);
    assertThat(tree).isNotNull();

    ExpressionDecomposer decomposer = createDecomposer(compiler);
    decomposer.setTempNamePrefix("temp");
    decomposer.setResultNamePrefix("result");

    Node expr = nodeFinder.apply(tree);

    decomposer.maybeExposeExpression(expr);
    processForTypecheck(compiler, tree);

    return tree;
  }

  private void helperExposeExpression(
      String code,
      Function<AbstractCompiler, Function<Node, Node>> compilerToNodeFinder,
      String expectedResult) {
    Compiler compiler = getCompiler();

    Node expectedRoot = parse(compiler, expectedResult);
    Node tree = parse(compiler, code);
    assertThat(tree).isNotNull();

    if (shouldTestTypes) {
      processForTypecheck(compiler, tree);
    }

    ExpressionDecomposer decomposer = createDecomposer(compiler);
    decomposer.setTempNamePrefix("temp");
    decomposer.setResultNamePrefix("result");

    final Function<Node, Node> nodeFinder = compilerToNodeFinder.apply(compiler);
    Node expr = nodeFinder.apply(tree);
    assertWithMessage("Expected node was not found.").that(expr).isNotNull();

    DecompositionType result = decomposer.canExposeExpression(expr);
    assertThat(result).isEqualTo(DecompositionType.DECOMPOSABLE);

    decomposer.maybeExposeExpression(expr);
    validateSourceInfo(compiler, tree);
    assertNode(tree).usingSerializer(compiler::toSource).isEqualTo(expectedRoot);

    if (shouldTestTypes) {
      Node decomposeThenTypeCheck = helperExposeExpressionThenTypeCheck(code, nodeFinder);
      checkTypeStringsEqualAsTree(decomposeThenTypeCheck, tree);
    }
  }

  private ExpressionDecomposer createDecomposer(Compiler compiler) {
    return compiler.createExpressionDecomposer(
        compiler.getUniqueNameIdSupplier(), ImmutableSet.copyOf(knownConstants), newScope());
  }

  private void helperMoveExpression(
      String code,
      Function<AbstractCompiler, Function<Node, Node>> compilerToNodeFinder,
      String expectedResult) {
    Compiler compiler = getCompiler();
    Function<Node, Node> nodeFinder = compilerToNodeFinder.apply(compiler);

    Node expectedRoot = parse(compiler, expectedResult);
    Node tree = parse(compiler, code);
    Node originalTree = tree.cloneTree();
    assertThat(tree).isNotNull();

    if (shouldTestTypes) {
      processForTypecheck(compiler, tree);
    }

    ExpressionDecomposer decomposer = createDecomposer(compiler);
    decomposer.setTempNamePrefix("temp");
    decomposer.setResultNamePrefix("result");

    Node expr = nodeFinder.apply(tree);
    assertWithMessage("Expected node was not found.").that(expr).isNotNull();

    decomposer.moveExpression(expr);
    validateSourceInfo(compiler, tree);
    assertNode(tree).usingSerializer(compiler::toSource).isEqualTo(expectedRoot);

    if (shouldTestTypes) {
      // find a basis for comparison:
      Node originalExpr = nodeFinder.apply(originalTree);

      decomposer.moveExpression(originalExpr);
      processForTypecheck(compiler, originalTree);

      // TODO(bradfordcsmith): Don't assume type check + decompose gives the same results as
      // decompose + type check.
      // There are legitimate cases where the types will be different from one order to another,
      // but not actually wrong.
      checkTypeStringsEqualAsTree(originalTree, tree);
    }
  }

  private void checkTypeStringsEqualAsTree(Node rootExpected, Node rootActual) {
    JSType expectedType = rootExpected.getJSType();
    JSType actualType = rootActual.getJSType();

    if (expectedType == null || actualType == null) {
      assertWithMessage("Expected %s but got %s", rootExpected, rootActual)
          .that(actualType)
          .isEqualTo(expectedType);
    } else if (expectedType.isUnknownType() && actualType.isUnknownType()) {
      // continue
    } else {
      // we can't compare actual equality because the types are from different runs of the
      // type inference, so we just compare the strings.
      assertWithMessage("Expected %s but got %s", rootExpected, rootActual)
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
    // If the specific test case requested an output language level,
    // use it. Otherwise, keep the default.
    if (languageOut != null) {
      options.setLanguageOut(languageOut);
    }
    options.setCodingConvention(new GoogleCodingConvention());
    options.setPrettyPrint(true);
    // Don't prefix the compiler output with `"use strict";`.
    // It's noise for these tests, and it interferes with tests that
    // want to use compiler.toSource() to string match expressions.
    options.setEmitUseStrict(false);
    options.setLanguageIn(LanguageMode.UNSUPPORTED);
    compiler.initOptions(options);
    return compiler;
  }

  private void processForTypecheck(AbstractCompiler compiler, Node jsRoot) {
    Node root = IR.root(IR.root(), IR.root(jsRoot));
    JSTypeRegistry registry = compiler.getTypeRegistry();
    (new TypeCheck(compiler, new SemanticReverseAbstractInterpreter(registry), registry))
        .processForTesting(root.getFirstChild(), root.getSecondChild());
    compiler.setTypeCheckingHasRun(true);
  }

  private static @Nullable Node findClass(Node n) {
    if (n.isClass()) {
      return n;
    }
    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      Node maybeClass = findClass(child);
      if (maybeClass != null) {
        return maybeClass;
      }
    }
    return null;
  }

  private void validateSourceInfo(Compiler compiler, Node subtree) {
    new SourceInfoCheck(compiler).setCheckSubTree(subtree);
    // Source information problems are reported as compiler errors.
    assertThat(compiler.getErrors()).isEmpty();
  }

  private static Node parse(Compiler compiler, String js) {
    Node n = compiler.parseTestCode(js);
    NodeTraversal.traverse(compiler, n, new NormalizeStatements(compiler, false, null));
    assertThat(compiler.getErrors()).isEmpty();
    return n;
  }

  private Scope newScope() {
    return Scope.createGlobalScope(new Node(Token.ROOT));
  }
}
