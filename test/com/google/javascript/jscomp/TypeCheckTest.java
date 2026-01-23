/*
 * Copyright 2006 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.TypeCheck.CONFLICTING_GETTER_SETTER_TYPE;
import static com.google.javascript.jscomp.TypeCheck.ILLEGAL_PROPERTY_CREATION_ON_UNION_TYPE;
import static com.google.javascript.jscomp.TypeCheck.INSTANTIATE_ABSTRACT_CLASS;
import static com.google.javascript.jscomp.TypeCheck.POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION;
import static com.google.javascript.jscomp.TypeCheck.STRICT_INEXISTENT_PROPERTY;
import static com.google.javascript.jscomp.TypeCheck.STRICT_INEXISTENT_UNION_PROPERTY;
import static com.google.javascript.jscomp.TypeCheckTestCase.TypeTestBuilder.newTest;
import static com.google.javascript.jscomp.parsing.JsDocInfoParser.BAD_TYPE_WIKI_LINK;
import static com.google.javascript.jscomp.testing.ScopeSubject.assertScope;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.jscomp.type.ClosureReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link TypeCheck}. */
@RunWith(JUnit4.class)
public final class TypeCheckTest extends TypeCheckTestCase {

  private static final String BOUNDED_GENERICS_USE_MSG =
      "Bounded generic semantics are currently still in development";

  private static final String SUGGESTION_CLASS =
      """
      /** @constructor
       */
      function Suggest() {}
      Suggest.prototype.a = 1;
      Suggest.prototype.veryPossible = 1;
      Suggest.prototype.veryPossible2 = 1;
      """;

  private static final String ILLEGAL_PROPERTY_CREATION_MESSAGE =
      """
      Cannot add a property to a struct instance after it is constructed. (If you already declared the property, make sure to give it a type.)
      """;

  @Test
  public void testInitialTypingScope() {
    TypedScope s =
        new TypedScopeCreator(compiler, CodingConventions.getDefault())
            .createInitialScope(new Node(Token.ROOT, new Node(Token.ROOT), new Node(Token.ROOT)));

    assertTypeEquals(getNativeArrayConstructorType(), s.getVar("Array").getType());
    assertTypeEquals(getNativeBooleanObjectConstructorType(), s.getVar("Boolean").getType());
    assertTypeEquals(getNativeDateConstructorType(), s.getVar("Date").getType());
    assertTypeEquals(getNativeNumberObjectConstructorType(), s.getVar("Number").getType());
    assertTypeEquals(getNativeObjectConstructorType(), s.getVar("Object").getType());
    assertTypeEquals(getNativeRegexpConstructorType(), s.getVar("RegExp").getType());
    assertTypeEquals(getNativeStringObjectConstructorType(), s.getVar("String").getType());
  }

  @Test
  public void testPrivateType() {
    newTest()
        .addSource("/** @private {number} */ var x = false;")
        .addDiagnostic(
            """
            initializing variable
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testTypeCheck1() {
    newTest().addSource("/**@return {void}*/function foo(){ if (foo()) return; }").run();
  }

  @Test
  public void testTypeCheck2() {
    newTest()
        .addSource("/**@return {void}*/function foo(){ var x=foo(); x--; }")
        .addDiagnostic(
            """
            increment/decrement
            found   : undefined
            required: number
            """)
        .run();
  }

  @Test
  public void testTypeCheck4() {
    newTest().addSource("/**@return {void}*/function foo(){ !foo(); }").run();
  }

  @Test
  public void testTypeCheck5() {
    newTest().addSource("/**@return {void}*/function foo(){ var a = +foo(); }").run();
  }

  @Test
  public void testTypeCheck6() {
    newTest()
        .addSource(
            """
            /** @return {void} */
            function foo(){
              /** @type {undefined|number} */
              var a;
              if (a == foo())
                return;
            }
            """)
        .run();
  }

  @Test
  public void testTypeCheck8() {
    newTest()
        .addSource(
            """
            /** @return {void} */
            function foo() {
              do {} while (foo());
            }
            """)
        .run();
  }

  @Test
  public void testTypeCheck9() {
    newTest()
        .addSource(
            """
            /** @return {void} */
            function foo() {
              while (foo());
            }
            """)
        .run();
  }

  @Test
  public void testTypeCheck10() {
    newTest()
        .addSource(
            """
            /** @return {void} */
            function foo() {
              for (;foo(););
            }
            """)
        .run();
  }

  @Test
  public void testTypeCheck11() {
    newTest()
        .addSource(
            """
            /** @type {!Number} */
            var a;
            /** @type {!String} */
            var b;
            a = b;
            """)
        .addDiagnostic(
            """
            assignment
            found   : String
            required: Number
            """)
        .run();
  }

  @Test
  public void testTypeCheck12() {
    newTest()
        .addSource("/**@return {!Object}*/function foo(){var a = 3^foo();}")
        .addDiagnostic(
            """
            bad right operand to bitwise operator
            found   : Object
            required: (boolean|null|number|string|undefined)
            """)
        .run();
  }

  @Test
  public void testTypeCheck13() {
    newTest()
        .addSource("/**@type {!Number|!String}*/var i; i=/xx/;")
        .addDiagnostic(
            """
            assignment
            found   : RegExp
            required: (Number|String)
            """)
        .run();
  }

  @Test
  public void testTypeCheck14() {
    newTest().addSource("/**@param {?} opt_a*/function foo(opt_a){}").run();
  }

  @Test
  public void testTypeCheck15() {
    newTest()
        .addSource("/**@type {Number|null} */var x;x=null;x=10;")
        .addDiagnostic(
            """
            assignment
            found   : number
            required: (Number|null)
            """)
        .run();
  }

  @Test
  public void testTypeCheck16() {
    newTest()
        .addSource("/**@type {Number|null} */var x='';")
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: (Number|null)
            """)
        .run();
  }

  @Test
  public void testTypeCheck17() {
    newTest()
        .addSource(
            """
            /**
             * @return {Number}
             * @param {Number} opt_foo
             */
            function a(opt_foo){
              return /** @type {Number} */ (opt_foo);
            }
            """)
        .run();
  }

  @Test
  public void testTypeCheck18() {
    newTest().addSource("/**@return {RegExp}\n*/\n function a(){return new RegExp();}").run();
  }

  @Test
  public void testTypeCheck19() {
    newTest().addSource("/**@return {Array}\n*/\n function a(){return new Array();}").run();
  }

  @Test
  public void testTypeCheck20() {
    newTest().addSource("/**@return {Date}\n*/\n function a(){return new Date();}").run();
  }

  @Test
  public void testTypeCheckBasicDowncast() {
    newTest()
        .addSource(
            """
            /** @constructor */function foo() {}
            /** @type {Object} */ var bar = new foo();
            """)
        .run();
  }

  @Test
  public void testTypeCheckNoDowncastToNumber() {
    newTest()
        .addSource(
            """
            /** @constructor */function foo() {}
            /** @type {!Number} */ var bar = new foo();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : foo
            required: Number
            """)
        .run();
  }

  @Test
  public void testTypeCheck21() {
    newTest().addSource("/** @type {Array<String>} */var foo;").run();
  }

  @Test
  public void testTypeCheck22() {
    newTest()
        .addSource(
            """
            /** @param {Element|Object} p */
            function foo(p){}
            /** @constructor */function Element(){}
            /** @type {Element|Object} */var v;
            foo(v);
            """)
        .run();
  }

  @Test
  public void testTypeCheck23() {
    newTest().addSource("/** @type {(Object|Null)} */var foo; foo = null;").run();
  }

  @Test
  public void testTypeCheck24() {
    newTest()
        .addSource(
            """
            /** @constructor */function MyType(){}
            /** @type {(MyType|Null)} */var foo; foo = null;
            """)
        .run();
  }

  @Test
  public void testTypeCheck25() {
    newTest()
        .addSource(
            """
            function foo(/** {a: number} */ obj) {};
            foo({b: 'abc'});
            """)
        .addDiagnostic(
            """
            actual parameter 1 of foo does not match formal parameter
            found   : {
              a: (number|undefined),
              b: string
            }
            required: {a: number}
            missing : []
            mismatch: [a]
            """)
        .run();
  }

  @Test
  public void testTypeCheck26() {
    newTest()
        .addSource(
            """
            function foo(/** {a: number} */ obj) {};
            foo({a: 'abc'});
            """)
        .addDiagnostic(
            """
            actual parameter 1 of foo does not match formal parameter
            found   : {a: (number|string)}
            required: {a: number}
            missing : []
            mismatch: [a]
            """)
        .run();
  }

  @Test
  public void testTypeCheck27() {
    newTest()
        .addSource(
            """
            function foo(/** {a: number} */ obj) {};
            foo({a: 123});
            """)
        .run();
  }

  @Test
  public void testTypeCheck28() {
    newTest()
        .addSource(
            """
            function foo(/** ? */ obj) {};
            foo({a: 123});
            """)
        .run();
  }

  @Test
  public void testPropertyOnATypedefGetsResolved() {
    newTest()
        .addSource(
            """
            const a = {};
            /** @typedef {{prop: string}} */
              a.b;
            /** @typedef {string} */
              a.b.c;
            const abAlias = a.b;
            var /** !abAlias.whatIsThis */ y = 'foo';
            """)
        .addDiagnostic(RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR)
        .run();
  }

  @Test
  public void testPropertyOnTypedefAliasesGetsRecognized() {
    // TODO(b/144187784): Handle making property b.c recognized when referenced via aAlias
    newTest()
        .addSource(
            """
            const a = {};
            /** @typedef {{prop: string}} */
              a.b;
            /** @typedef {string} */
              a.b.c;
            const aAlias = a;
            var /** aAlias.b.c */ x;
            """)
        .addDiagnostic(RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR)
        .run();
  }

  @Test
  public void testPropertyOnTypedefAliasesGetsRecognized2() {
    newTest()
        .addSource(
            """
            const a = {};
            /** @typedef {{prop: string}} */
              a.b;
            /** @typedef {string} */
              a.b.c;
            const aAlias = a;
            var /** aAlias.b */ x = {prop: 'foo'};
            """)
        .run();
  }

  @Test
  public void testTypeCheckInlineReturns() {
    newTest()
        .addSource(
            """
            function /** string */ foo(x) { return x; }
            var /** number */ a = foo('abc');
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testTypeCheckDefaultExterns() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            """
            /** @param {string} x */
            function f(x) {}
            f([].length);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testTypeCheckCustomExterns() {
    newTest()
        .addExterns("/** @type {boolean} */ Array.prototype.oogabooga;")
        .includeDefaultExterns()
        .addSource(
            """
            /** @param {string} x */ function f(x) {}
            f([].oogabooga);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : boolean
            required: string
            """)
        .run();
  }

  @Test
  public void testTypeCheckCustomExterns2() {
    newTest()
        .addExterns("/** @enum {string} */ var Enum = {FOO: 1, BAR: 1};")
        .includeDefaultExterns()
        .addSource("/** @param {Enum} x */ function f(x) {} f(Enum.FOO); f(true);")
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : boolean
            required: Enum<string>
            """)
        .run();
  }

  @Test
  public void testDontCrashOnRecursiveTemplateReference() {
    newTest()
        .addSource(
            """
            /** @constructor @implements {Iterable<VALUE>} @template VALUE */
            function Foo() {
              /** @type {!Map<VALUE, VALUE>} */ this.map = new Map;
            }
            /** @override @return {!Iterator<VALUE>} */
            Foo.prototype[Symbol.iterator] = function() {};
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testUnionOfFunctionAndType() {
    newTest()
        .addSource(
            """
            /** @type {null|(function(Number):void)} */ var a;
            /** @type {(function(Number):void)|null} */ var b = null; a = b;
            """)
        .run();
  }

  @Test
  public void testOptionalParameterComparedToUndefined() {
    newTest()
        .addSource(
            """
            /** @param  {Number} opt_a */function foo(opt_a)
            {if (opt_a==undefined) var b = 3;}
            """)
        .run();
  }

  @Test
  public void testOptionalAllType() {
    newTest()
        .addSource(
            """
            /** @param {*} opt_x */function f(opt_x) { return opt_x }
            /** @type {*} */var y;
            f(y);
            """)
        .run();
  }

  @Test
  public void testUnaryPlusWithAllType() {
    newTest().addSource("/** @type {*} */var x; +x;").run();
  }

  @Test
  public void testUnaryPlusWithNoType() {
    // TODO(bradfordcsmith): Should we be reporting an error for operating on a NO_TYPE?
    newTest().addSource("/** @type {!null} */var x; +x;").run();
  }

  @Test
  public void testUnaryPlusWithUnknownType() {
    newTest().addSource("/** @type {?} */var x; +x;").run();
  }

  @Test
  public void testOptionalUnknownNamedType() {
    newTest()
        .addSource(
            """
            /** @param {!T} opt_x
            @return {undefined} */
            function f(opt_x) { return opt_x; }
            /** @constructor */var T = function() {};
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (T|undefined)
            required: undefined
            """)
        .run();
  }

  @Test
  public void testOptionalArgFunctionParam() {
    newTest()
        .addSource(
            """
            /** @param {function(number=)} a */
            function f(a) {a()};
            """)
        .run();
  }

  @Test
  public void testOptionalArgFunctionParam2() {
    newTest()
        .addSource(
            """
            /** @param {function(number=)} a */
            function f(a) {a(3)};
            """)
        .run();
  }

  @Test
  public void testOptionalArgFunctionParam3() {
    newTest()
        .addSource(
            """
            /** @param {function(number=)} a */
            function f(a) {a(undefined)};
            """)
        .run();
  }

  @Test
  public void testOptionalArgFunctionParam4() {
    newTest()
        .addSource(
            """
            /** @param {function(number=)} a */
            function f(a) {a(3,4)};
            """)
        .addDiagnostic(
            """
            Function a: called with 2 argument(s). Function requires at least 0 argument(s) and no more than 1 argument(s).
            """)
        .run();
  }

  @Test
  public void testOptionalArgFunctionParamError() {
    String expectedWarning =
        "Bad type annotation. variable length argument must be last." + BAD_TYPE_WIKI_LINK;
    newTest()
        .addSource(
            """
            /** @param {function(...number, number=)} a */
            function f(a) {};
            """)
        .addDiagnostic(expectedWarning)
        .run();
  }

  @Test
  public void testOptionalNullableArgFunctionParam() {
    newTest()
        .addSource(
            """
            /** @param {function(?number=)} a */
            function f(a) {a()};
            """)
        .run();
  }

  @Test
  public void testOptionalNullableArgFunctionParam2() {
    newTest()
        .addSource(
            """
            /** @param {function(?number=)} a */
            function f(a) {a(null)};
            """)
        .run();
  }

  @Test
  public void testOptionalNullableArgFunctionParam3() {
    newTest()
        .addSource(
            """
            /** @param {function(?number=)} a */
            function f(a) {a(3)};
            """)
        .run();
  }

  @Test
  public void testOptionalArgFunctionReturn() {
    newTest()
        .addSource(
            """
            /** @return {function(number=)} */
            function f() { return function(opt_x) { }; };
            f()()
            """)
        .run();
  }

  @Test
  public void testOptionalArgFunctionReturn2() {
    newTest()
        .addSource(
            """
            /** @return {function(Object=)} */
            function f() { return function(opt_x) { }; };
            f()({})
            """)
        .run();
  }

  @Test
  public void testBooleanType() {
    newTest().addSource("/**@type {boolean} */var x = 1 < 2;").run();
  }

  @Test
  public void testBooleanReduction1() {
    newTest().addSource("/**@type {string} */var x; x = null || \"a\";").run();
  }

  @Test
  public void testBooleanReduction1NullishCoalesce() {
    newTest().addSource("/**@type {string} */var x; x = null ?? \"a\";").run();
  }

  @Test
  public void testLogicalAssignment() {
    newTest().addSource("/**@type {?} */var a; /** @type {?} */ var b; a||=b;").run();
    newTest().addSource("/**@type {?} */var a; /** @type {?} */ var b; a&&=b;").run();
    newTest().addSource("/**@type {?} */var a; /** @type {?} */ var b; a??=b;").run();
  }

  @Test
  public void testAssignOrToNonNumeric() {
    // The precision of this test can be improved.
    // Boolean type is treated as {true, false}.
    newTest()
        .addSource(
            """
            /** @param {boolean} x */
             function assignOr(x) {
               x ||= 'a';
             };
            """)
        .addDiagnostic(
            """
            assignment
            found   : string
            required: boolean
            """)
        .run();
  }

  @Test
  public void testAssignOrMayOrMayNotAssign() {
    newTest()
        .addSource(
            """
            /** @param {string} x */
             function assignOr(x) {
               x ||= 5;
             };
            """)
        .addDiagnostic(
            """
            assignment
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testAssignAndCheckRHSValid() {
    newTest()
        .addSource(
            """
            /** @type {string|undefined} */
            var a; a &&= 0;
            """)
        .addDiagnostic(
            """
            assignment
            found   : number
            required: (string|undefined)
            """)
        .run();
  }

  @Test
  public void testAssignAndRHSNotExecuted() {
    newTest()
        .addSource(
            """
            let /** null */
            n = null;
            n &&= 'str';
            """)
        .addDiagnostic(
            """
            assignment
            found   : string
            required: null
            """)
        .run();
  }

  @Test
  public void testAssignCoalesceNoAssign() {
    newTest()
        .addSource(
            """
            /**
             * @param {string} x */
             function assignCoalesce(x) {
               x ??= 5;
             };
            """)
        .addDiagnostic(
            """
            assignment
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testAssignCoalesceCheckRHSValid() {
    newTest()
        .addSource("/** @type {string|undefined} */ var a; a ??= 0;")
        .addDiagnostic(
            """
            assignment
            found   : number
            required: (string|undefined)
            """)
        .run();
  }

  @Test
  public void optChain() {
    // TODO(b/151248857): Calculate the appropriate type here
    newTest().addSource("/**@type {?} */var x = a?.b;").run();
  }

  @Test
  public void testBooleanReduction2() {
    // It's important for the type system to recognize that in no case
    // can the boolean expression evaluate to a boolean value.
    newTest()
        .addSource(
            """
            /**
             * @param {string} s
             * @return {string}
             */
            (function(s) {
              return ((s == 'a') && s) || 'b';
            })
            """)
        .run();
  }

  @Test
  public void testBooleanReduction3() {
    newTest()
        .addSource(
            """
            /**
             * @param {string} s
             * @return {string?}
             */
            (function(s) {
              return s && null && 3;
            })
            """)
        .run();
  }

  @Test
  public void testBooleanReduction4() {
    newTest()
        .addSource(
            """
            /**
             * @param {?Object} x
             * @return {?Object}
             */
            (function(x) {
              return null || x || null;
            })
            """)
        .run();
  }

  @Test
  public void testBooleanReduction4NullishCoalesce() {
    newTest()
        .addSource(
            """
            /** @param {?Object} x
             @return {!Object} */
            (function(x) { return null ?? x ?? {} ; })
            """)
        .run();
  }

  @Test
  public void testBooleanReduction5() {
    newTest()
        .addSource(
            """
            /**
            * @param {Array|string} x
            * @return {string?}
            */
            var f = function(x) {
            if (!x || typeof x == 'string') {
            return x;
            }
            return null;
            };
            """)
        .run();
  }

  @Test
  public void testBooleanReduction6() {
    newTest()
        .addSource(
            """
            /**
            * @param {Array|string|null} x
            * @return {string?}
            */
            var f = function(x) {
            if (!(x && typeof x != 'string')) {
            return x;
            }
            return null;
            };
            """)
        .run();
  }

  @Test
  public void testBooleanReduction7() {
    newTest()
        .addSource(
            """
            /** @constructor */var T = function() {};
            /**
            * @param {Array|T} x
            * @return {null}
            */
            var f = function(x) {
            if (!x) {
            return x;
            }
            return null;
            };
            """)
        .run();
  }

  @Test
  public void testNullishCoalesceWithAndExpressions() {
    // `(s && undefined)` = `undefined` because `s` is typed as `!Array`, triggering RHS
    // `(s && "a")` = `"a"` because `s` is typed as `!Array`
    newTest()
        .addSource(
            """
            /** @param {!Array} s
                @return {string} */
            (function(s) { return (s && undefined) ?? (s && "a"); })
            """)
        .run();
  }

  @Test
  public void testNullishCoalesceShortCutsOnZero() {
    newTest().addSource("/**@type {number} */var x; x = 0 ?? \"a\";").run();
  }

  @Test
  public void testNullishCoalesceUnionsOperatorTypesWithNullRemoved() {
    newTest()
        .addSource(
            """
            /**
            * @param {string|null} x
            * @return {string|number}
            */
            var f = function(x) {
            return x ?? 3;
            };
            """)
        .run();
  }

  @Test
  public void testNullishCoalesceShortCutsOnFalse() {
    newTest().addSource("/**@type {boolean} */var x; x = false ?? undefined ?? [];").run();
  }

  @Test
  public void testNullishCoalesceChaining() {
    newTest().addSource("/**@type {number} */var x; x = null ?? undefined ?? 3;").run();
  }

  @Test
  public void testNullishCoalesceOptionalNullable() {
    newTest()
        .addSource(
            """
            /**
             * @param {?Object=} optionalNullableObject
             * @return {!Object}
             */
            function f(optionalNullableObject = undefined) {
             return optionalNullableObject ?? {}; // always returns !Object type
            };
            """)
        .run();
  }

  @Test
  public void testNullishCoalesceUseNonNullFunction() {
    // if we are trying to evaluate the LHS then x must be null and useNonNull only takes non null
    // objects as inputs
    newTest()
        .addSource(
            """
            /**
             * @param {!Object} x
             * @return {!Object}
             */
             function useNonNull(x) {
               return x;
             };
            /** @type {?Object} */ var x;
            x ?? useNonNull(x);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of useNonNull does not match formal parameter
            found   : null
            required: Object
            """)
        .run();
  }

  @Test
  public void testNullishCoalesceLeftTypeNotDefined() {
    // making sure we don't throw a NPE
    newTest().addSource("/** @type {number} */ var a = x ?? 1;").run();
  }

  @Test
  public void testNullAnd() {
    newTest()
        .addSource(
            """
            /** @type {null} */var x;
            /** @type {number} */var r = x && x;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : null
            required: number
            """)
        .run();
  }

  @Test
  public void testNullOr() {
    newTest()
        .addSource(
            """
            /** @type {null} */var x;
            /** @type {number} */var r = x || x;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : null
            required: number
            """)
        .run();
  }

  @Test
  public void testBooleanPreservation1() {
    newTest()
        .addSource(
            """
            /**@type {string} */var x = "a";
            x = ((x == "a") && x) || x == "b";
            """)
        .addDiagnostic(
            """
            assignment
            found   : (boolean|string)
            required: string
            """)
        .run();
  }

  @Test
  public void testBooleanPreservation2() {
    newTest()
        .addSource(
            """
            /**@type {string} */var x = "a";
            x = (x == "a") || x;
            """)
        .addDiagnostic(
            """
            assignment
            found   : (boolean|string)
            required: string
            """)
        .run();
  }

  @Test
  public void testBooleanPreservation3() {
    newTest()
        .addSource(
            """
            /** @param {Function?} x
             @return {boolean?} */
            function f(x) { return x && x == "a"; }
            """)
        .addDiagnostic(
            """
            condition always evaluates to false
            left : Function
            right: string
            """)
        .run();
  }

  @Test
  public void testBooleanPreservation4() {
    newTest()
        .addSource(
            """
            /** @param {Function?|boolean} x
             @return {boolean} */
            function f(x) { return x && x == "a"; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (boolean|null)
            required: boolean
            """)
        .run();
  }

  @Test
  public void testWellKnownSymbolAccess1() {
    newTest()
        .addSource(
            """
            /**
             * @param {Array<string>} x
             */
            function f(x) {
              const iter = x[Symbol.iterator]();
            }
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testWellKnownSymbolAccess2() {
    newTest()
        .addSource(
            """
            /**
             * @param {IObject<string, number>} x
             */
            function f(x) {
              const iter = x[Symbol.iterator]();
            }
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testSymbolComparison1() {
    newTest()
        .addSource(
            """
            /**
             * @param {symbol} x
             * @param {symbol} y
            */
            function f(x, y) { return x === y; }
            """)
        .run();
  }

  @Test
  public void testSymbolComparison2() {
    newTest()
        .addSource(
            """
            /**
             * @param {symbol} x
             * @param {symbol} y
            */
            function f(x, y) { return x == y; }
            """)
        .run();
  }

  @Test
  public void testSymbolComparison3() {
    newTest()
        .addSource(
            """
            /**
             * @param {symbol} x // primitive
             * @param {!Symbol} y // object
            */
            function f(x, y) { return x == y; }
            """)
        .run();
  }

  @Test
  public void testSymbolComparison4() {
    newTest()
        .addSource(
            """
            /**
             * @param {symbol} x // primitive
             * @param {!Symbol} y // object
            */
            function f(x, y) { return x === y; }
            """)
        .addDiagnostic(
            """
            condition always evaluates to false
            left : symbol
            right: Symbol
            """)
        .run();
  }

  @Test
  public void testSymbolComparison5() {
    newTest()
        .addSource(
            """
            /**
             * @param {symbol} x // primitive
             * @param {(!Symbol|null)} y // object
            */
            function f(x, y) { return x == y; }
            """)
        .run();
  }

  @Test
  public void testSymbolComparison6() {
    newTest()
        .addSource(
            """
            /**
             * @param {symbol} x // primitive
             * @param {(!Symbol|null)} y // object
            */
            function f(x, y) { return x === y; }
            """)
        .addDiagnostic(
            """
            condition always evaluates to false
            left : symbol
            right: (Symbol|null)
            """)
        .run();
  }

  @Test
  public void testSymbolComparison7() {
    newTest()
        .addSource(
            """
            /**
             * @param {symbol} x
             * @param {*} y
            */
            function f(x, y) { return x == y; }
            """)
        .run();
  }

  @Test
  public void testSymbolComparison8() {
    newTest()
        .addSource(
            """
            /**
             * @param {symbol} x
             * @param {*} y
            */
            function f(x, y) { return x === y; }
            """)
        .run();
  }

  @Test
  public void testSymbolComparison9() {
    newTest()
        .addSource(
            """
            /** @enum {symbol} */ var E = {A:Symbol()};
            /**
             * @param {symbol} x
             * @param {E} y
            */
            function f(x, y) { return x == y; }
            """)
        .run();
  }

  @Test
  public void testSymbolComparison10() {
    newTest()
        .addSource(
            """
            /** @enum {symbol} */ var E = {A:Symbol()};
            /**
             * @param {symbol} x
             * @param {E} y
            */
            function f(x, y) { return x === y; }
            """)
        .run();
  }

  @Test
  public void testSymbolComparison11() {
    newTest()
        .addSource(
            """
            /** @enum {!Symbol} */ var E = {A:/** @type {!Symbol} */ (Object(Symbol()))};
            /**
             * @param {symbol} x
             * @param {E} y
            */
            function f(x, y) { return x == y; }
            """)
        .run();
  }

  @Test
  public void testSymbolComparison12() {
    newTest()
        .addSource(
            """
            /** @enum {!Symbol} */ var E = {A:/** @type {!Symbol} */ (Object(Symbol()))};
            /**
             * @param {symbol} x
             * @param {E} y
            */
            function f(x, y) { return x === y; }
            """)
        .addDiagnostic(
            """
            condition always evaluates to false
            left : symbol
            right: E<Symbol>
            """)
        .run();
  }

  @Test
  public void testSymbolComparison13() {
    newTest()
        .addSource(
            """
            /**
             * @param {symbol} x
             * @param {!Object} y
            */
            function f(x, y) { return x == y; }
            """)
        .run();
  }

  @Test
  public void testSymbolComparison14() {
    newTest()
        .addSource(
            """
            /**
             * @param {symbol} x
             * @param {!Object} y
            */
            function f(x, y) { return x === y; }
            """)
        .addDiagnostic(
            """
            condition always evaluates to false
            left : symbol
            right: Object
            """)
        .run();
  }

  @Test
  public void testTypeOfReduction1() {
    newTest()
        .addSource(
            """
            /**
             * @param {string|number} x
             * @return {string}
             */
            function f(x) {
              return typeof x == 'number' ? String(x) : x;
            }
            """)
        .run();
  }

  @Test
  public void testTypeOfReduction2() {
    newTest()
        .addSource(
            """
            /**
             * @param {string|number} x
             * @return {string}
             */
            function f(x) {
              return typeof x != 'string' ? String(x) : x;
            }
            """)
        .run();
  }

  @Test
  public void testTypeOfReduction3() {
    newTest()
        .addSource(
            """
            /**
             * @param {number|null} x
             * @return {number}
             */
            function f(x) {
              return typeof x == 'object' ? 1 : x;
            }
            """)
        .run();
  }

  @Test
  public void testTypeOfReduction4() {
    newTest()
        .addSource(
            """
            /**
             * @param {Object|undefined} x
             * @return {Object}
             */
            function f(x) {
              return typeof x == 'undefined' ? {} : x;
            }
            """)
        .run();
  }

  @Test
  public void testTypeOfReduction5() {
    newTest()
        .addSource(
            """
            /** @enum {string} */ var E = {A: 'a', B: 'b'};
            /** @param {!E|number} x
             @return {string} */
            function f(x) { return typeof x != 'number' ? x : 'a'; }
            """)
        .run();
  }

  @Test
  public void testTypeOfReduction6() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            """
            /**
             * @param {number|string} x
             * @return {string}
             */
            function f(x) {
              return typeof x == 'string' && x.length == 3 ? x : 'a';
            }
            """)
        .run();
  }

  @Test
  public void testTypeOfReduction7() {
    newTest()
        .addSource(
            """
            /** @return {string} */
            var f = function(x) {
              return typeof x == 'number' ? x : 'a';
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (number|string)
            required: string
            """)
        .run();
  }

  @Test
  public void testTypeOfReduction11() {
    testClosureTypes(
        """
        /** @param {Array|string} x
        @return {Array} */
        function f(x) {
        return goog.isObject(x) ? x : [];
        }
        """,
        null);
  }

  @Test
  public void testTypeOfReduction12() {
    newTest()
        .addSource(
            """
            /** @enum {string} */ var E = {A: 'a', B: 'b'};
            /** @param {E|Array} x
             @return {Array} */
            function f(x) { return typeof x == 'object' ? x : []; }
            """)
        .run();
  }

  @Test
  public void testTypeOfReduction13() {
    testClosureTypes(
        """
        /** @enum {string} */ var E = {A: 'a', B: 'b'};
        /** @param {E|Array} x
        @return {Array} */
        function f(x) { return goog.isObject(x) ? x : []; }
        """,
        null);
  }

  @Test
  public void testTypeOfReduction15() {
    // Don't do type inference on GETELEMs.
    testClosureTypes(
        """
        function f(x) {
          return typeof arguments[0] == 'string' ? arguments[0] : 0;
        }
        """,
        null);
  }

  @Test
  public void testTypeOfReduction16() {
    testClosureTypes(
        """
        /** @interface */ function I() {}
        /**
         * @param {*} x
         * @return {I}
         */
        function f(x) {
          if(goog.isObject(x)) {
            return /** @type {I} */ (x);
          }
          return null;
        }
        """,
        null);
  }

  @Test
  public void testQualifiedNameReduction1() {
    newTest()
        .addSource(
            """
            var x = {}; /** @type {string?} */ x.a = 'a';
            /** @return {string} */ var f = function() {
            return x.a ? x.a : 'a'; }
            """)
        .run();
  }

  @Test
  public void testQualifiedNameReduction2() {
    newTest()
        .addSource(
            """
            /** @param {string?} a
            @constructor */ var T = function(a) {this.a = a};
            /** @return {string} */ T.prototype.f = function() {
            return this.a ? this.a : 'a'; }
            """)
        .run();
  }

  @Test
  public void testQualifiedNameReduction3() {
    newTest()
        .addSource(
            """
            /** @param {string|Array} a
            @constructor */ var T = function(a) {this.a = a};
            /** @return {string} */ T.prototype.f = function() {
            return typeof this.a == 'string' ? this.a : 'a'; }
            """)
        .run();
  }

  @Test
  public void testQualifiedNameReduction5a() {
    newTest()
        .addSource(
            """
            var x = {/** @type {string} */ a:'b' };
            /** @return {string} */ var f = function() {
            return x.a; }
            """)
        .run();
  }

  @Test
  public void testQualifiedNameReduction5b() {
    newTest()
        .addSource(
            """
            var x = {/** @type {number} */ a:12 };
            /** @return {string} */
            var f = function() {
              return x.a;
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testQualifiedNameReduction5c() {
    newTest()
        .addSource(
            """
            /** @return {string} */ var f = function() {
            var x = {/** @type {number} */ a:0 };
            return (x.a) ? (x.a) : 'a'; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (number|string)
            required: string
            """)
        .run();
  }

  @Test
  public void testQualifiedNameReduction6() {
    newTest()
        .addSource(
            """
            /** @return {string} */ var f = function() {
            var x = {/** @return {string?} */ get a() {return 'a'}};
            return x.a ? x.a : 'a'; }
            """)
        .run();
  }

  @Test
  public void testQualifiedNameReduction7() {
    newTest()
        .addSource(
            """
            /** @return {string} */ var f = function() {
            var x = {/** @return {number} */ get a() {return 12}};
            return x.a; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testQualifiedNameReduction7a() {
    // It would be nice to find a way to make this an error.
    newTest()
        .addSource(
            """
            /** @return {string} */ var f = function() {
            var x = {get a() {return 12}};
            return x.a; }
            """)
        .run();
  }

  @Test
  public void testQualifiedNameReduction8() {
    newTest()
        .addSource(
            """
            /** @return {string} */ var f = function() {
            var x = {get a() {return 'a'}};
            return x.a ? x.a : 'a'; }
            """)
        .run();
  }

  @Test
  public void testQualifiedNameReduction9() {
    newTest()
        .addSource(
            """
            /** @return {string} */ var f = function() {
            var x = { /** @param {string} b */ set a(b) {}};
            return x.a ? x.a : 'a'; }
            """)
        .run();
  }

  @Test
  public void testQualifiedNameReduction10() {
    // TODO(johnlenz): separate setter property types from getter property
    // types.
    newTest()
        .addSource(
            """
            /** @return {string} */ var f = function() {
            var x = { /** @param {number} b */ set a(b) {}};
            return x.a ? x.a : 'a'; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (number|string)
            required: string
            """)
        .run();
  }

  @Test
  public void testUnknownsDontOverrideDeclaredTypesInLocalScope1() {
    newTest()
        .addSource(
            """
            /** @constructor */ var C = function() {
              /** @type {string} */ this.a = 'str'};
            /** @param {?} a
             @return {number} */
            C.prototype.f = function(a) {
              this.a = a;
              return this.a;
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testUnknownsDontOverrideDeclaredTypesInLocalScope2() {
    newTest()
        .addSource(
            """
            /** @constructor */ var C = function() {
              /** @type {string} */ this.a = 'str';
            };
            /** @type {C} */ var x = new C();
            /** @param {?} a
             @return {number} */
            C.prototype.f = function(a) {
              x.a = a;
              return x.a;
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testObjLitDef1a() {
    newTest()
        .addSource(
            """
            var x = {/** @type {number} */ a:12 };
            x.a = 'a';
            """)
        .addDiagnostic(
            """
            assignment to property a of x
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testObjLitDef1b() {
    newTest()
        .addSource(
            """
            function f(){
            var x = {/** @type {number} */ a:12 };
            x.a = 'a';
            };
            f();
            """)
        .addDiagnostic(
            """
            assignment to property a of x
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testObjLitDef2a() {
    newTest()
        .addSource(
            """
            var x = {/** @param {number} b */ set a(b){} };
            x.a = 'a';
            """)
        .addDiagnostic(
            """
            assignment to property a of x
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testObjLitDef2b() {
    newTest()
        .addSource(
            """
            function f(){
            var x = {/** @param {number} b */ set a(b){} };
            x.a = 'a';
            };
            f();
            """)
        .addDiagnostic(
            """
            assignment to property a of x
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testObjLitDef3a() {
    newTest()
        .addSource(
            """
            /** @type {string} */ var y;
            var x = {/** @return {number} */ get a(){} };
            y = x.a;
            """)
        .addDiagnostic(
            """
            assignment
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testObjLitDef3b() {
    newTest()
        .addSource(
            """
            /** @type {string} */ var y;
            function f(){
            var x = {/** @return {number} */ get a(){} };
            y = x.a;
            };
            f();
            """)
        .addDiagnostic(
            """
            assignment
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testObjLitDef4() {
    newTest()
        .addSource(
            """
            var x = {
              /** @return {number} */ a:12
            };
            """)
        .addDiagnostic(
            """
            assignment to property a of {a: function(): number}
            found   : number
            required: function(): number
            """)
        .run();
  }

  @Test
  public void testMismatchInObjectKeyShouldShowBothTypes() {
    newTest()
        .includeDefaultExterns()
        .addSource(
            "a.js",
            """
            goog.module('my.a');

            /** @enum {string} */
            const MyEnum = {
              KEY1: 'VALUE1',
              KEY2: 'VALUE2',
            };

            exports = {MyEnum};
            """)
        .addSource(
            "b.js",
            """
            goog.module('my.b');

            /** @enum {string} */
            const MyEnum = {
              KEY1: 'VALUE1',
              KEY2: 'VALUE2',
            };

            exports = {MyEnum};
            """)
        .addSource(
            "c.js",
            """
            goog.module('my.c');

            const a = goog.require('my.a');
            const b = goog.require('my.b');

            /**
             * @typedef {!Object<!a.MyEnum, *>}
             */
            let MyObjectTypedef;

            /** @type {MyObjectTypedef} */
            const myObject = {};

            myObject[b.MyEnum.KEY1] = 'some value';
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : string (enum definition: b.js:4:15)
            required: string (enum definition: a.js:4:15)
            """)
        .run();
  }

  @Test
  public void testObjLitDef5() {
    newTest()
        .addSource(
            """
            var x = {};
            /** @return {number} */ x.a = 12;
            """)
        .addDiagnostic(
            """
            assignment to property a of x
            found   : number
            required: function(): number
            """)
        .run();
  }

  @Test
  public void testObjLitDef6() {
    newTest()
        .addSource("var lit = /** @struct */ { 'x': 1 };")
        .addDiagnostic("Illegal key, the object literal is a struct")
        .run();
  }

  @Test
  public void testObjLitDef7() {
    newTest()
        .addSource("var lit = /** @dict */ { x: 1 };")
        .addDiagnostic("Illegal key, the object literal is a dict")
        .run();
  }

  @Test
  public void testInstanceOfReduction1() {
    newTest()
        .addSource(
            """
            /** @constructor */ var T = function() {};
            /** @param {T|string} x
            @return {T} */
            var f = function(x) {
            if (x instanceof T) { return x; } else { return new T(); }
            };
            """)
        .run();
  }

  @Test
  public void testInstanceOfReduction2() {
    newTest()
        .addSource(
            """
            /** @constructor */ var T = function() {};
            /** @param {!T|string} x
            @return {string} */
            var f = function(x) {
            if (x instanceof T) { return ''; } else { return x; }
            };
            """)
        .run();
  }

  @Test
  public void testUndeclaredGlobalProperty1() {
    newTest()
        .addSource(
            """
            /** @const */ var x = {}; x.y = null;
            function f(a) { x.y = a; }
            /** @param {string} a */ function g(a) { }
            function h() { g(x.y); }
            """)
        .run();
  }

  @Test
  public void testUndeclaredGlobalProperty2() {
    newTest()
        .addSource(
            """
            /** @const */ var x = {}; x.y = null;
            function f() { x.y = 3; }
            /** @param {string} a */ function g(a) { }
            function h() { g(x.y); }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : (null|number)
            required: string
            """)
        .run();
  }

  @Test
  public void testLocallyInferredGlobalProperty1() {
    // We used to have a bug where x.y.z leaked from f into h.
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            /** @type {number} */ F.prototype.z;
            /** @const */ var x = {}; /** @type {F} */ x.y;
            function f() { x.y.z = 'abc'; }
            /** @param {number} x */ function g(x) {}
            function h() { g(x.y.z); }
            """)
        .addDiagnostic(
            """
            assignment to property z of F
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testPropertyInferredPropagation() {
    // Checking sloppy property check behavior
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @return {Object} */
            function f() { return {}; }
            function g() { var x = f(); if (x.p) x.a = 'a'; else x.a = 'b'; }
            function h() { var x = f(); x.a = false; }
            """)
        .run();
  }

  @Test
  public void testPropertyInference1() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() { this.x_ = true; }
            /** @return {string} */
            F.prototype.bar = function() { if (this.x_) return this.x_; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: string
            """)
        .run();
  }

  @Test
  public void testPropertyInference2() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() { this.x_ = true; }
            F.prototype.baz = function() { this.x_ = null; };
            /** @return {string} */
            F.prototype.bar = function() { if (this.x_) return this.x_; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: string
            """)
        .run();
  }

  @Test
  public void testPropertyInference3() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() { this.x_ = true; }
            F.prototype.baz = function() { this.x_ = 3; };
            /** @return {string} */
            F.prototype.bar = function() { if (this.x_) return this.x_; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (boolean|number)
            required: string
            """)
        .run();
  }

  @Test
  public void testPropertyInference4() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() { }
            F.prototype.x_ = 3;
            /** @return {string} */
            F.prototype.bar = function() { if (this.x_) return this.x_; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testPropertyInference5() {
    // "x_" is a known property of an unknown type.
    newTest()
        .addSource(
            """
            /** @constructor */ function F() { }
            F.prototype.baz = function() { this.x_ = 3; };
            /** @return {string} */
            F.prototype.bar = function() { if (this.x_) return this.x_; };
            """)
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .run();
  }

  @Test
  public void testPropertyInference6() {
    // "x_" is a known property of an unknown type.
    newTest()
        .addSource(
            """
            /** @constructor */ function F() { }
            (new F).x_ = 3;
            /** @return {string} */
            F.prototype.bar = function() { return this.x_; };
            """)
        .addDiagnostic("Property x_ never defined on F") // definition
        .addDiagnostic("Property x_ never defined on F") // reference
        .run();
  }

  @Test
  public void testPropertyInference7() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() { this.x_ = true; }
            (new F).x_ = 3;
            /** @return {string} */
            F.prototype.bar = function() { return this.x_; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: string
            """)
        .run();
  }

  @Test
  public void testPropertyInference8() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {
              /** @type {string} */ this.x_ = 'x';
            }
            (new F).x_ = 3;
            /** @return {string} */
            F.prototype.bar = function() { return this.x_; };
            """)
        .addDiagnostic(
            """
            assignment to property x_ of F
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testPropertyInference9() {
    newTest()
        .addSource(
            """
            /** @constructor */ function A() {}
            /** @return {function(): ?} */ function f() {
              return function() {};
            }
            var g = f();
            /** @type {number} */ g.prototype.bar_ = null;
            """)
        .addDiagnostic(
            """
            assignment
            found   : null
            required: number
            """)
        .run();
  }

  @Test
  public void testPropertyInference10() {
    // NOTE(nicksantos): There used to be a bug where a property
    // on the prototype of one structural function would leak onto
    // the prototype of other variables with the same structural
    // function type.
    newTest()
        .addSource(
            """
            /** @constructor */ function A() {}
            /** @return {function(): ?} */ function f() {
              return function() {};
            }
            var g = f();
            /** @type {number} */ g.prototype.bar_ = 1;
            var h = f();
            /** @type {string} */ h.prototype.bar_ = 1;
            """)
        .addDiagnostic(
            """
            assignment
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testNoPersistentTypeInferenceForObjectProperties() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Object} o
            @param {string} x */
            function s1(o,x) { o.x = x; }
            /** @param {Object} o
            @return {string} */
            function g1(o) { return typeof o.x == 'undefined' ? '' : o.x; }
            /** @param {Object} o
            @param {number} x */
            function s2(o,x) { o.x = x; }
            /** @param {Object} o
            @return {number} */
            function g2(o) { return typeof o.x == 'undefined' ? 0 : o.x; }
            """)
        .run();
  }

  @Test
  public void testNoPersistentTypeInferenceForFunctionProperties() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Function} o
            @param {string} x */
            function s1(o,x) { o.x = x; }
            /** @param {Function} o
            @return {string} */
            function g1(o) { return typeof o.x == 'undefined' ? '' : o.x; }
            /** @param {Function} o
            @param {number} x */
            function s2(o,x) { o.x = x; }
            /** @param {Function} o
            @return {number} */
            function g2(o) { return typeof o.x == 'undefined' ? 0 : o.x; }
            """)
        .run();
  }

  @Test
  public void testStrictPropertiesOnFunctions2() {
    newTest()
        .addSource(
            """
            /** @param {Function} o
             @param {string} x */
            function s1(o,x) { o.x = x; }
            """)
        .addDiagnostic("Property x never defined on Function")
        .run();
  }

  @Test
  public void testStrictPropertiesOnEnum1() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            """
            /** @enum {string} */ var E = {S:'s'};
            /** @param {E} e
             @return {string}  */
            function s1(e) { return e.slice(1); }
            """)
        .run();
  }

  @Test
  public void testStrictPropertiesOnEnum2() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            """
            /** @enum {?string} */ var E = {S:'s'};
            /** @param {E} e
             @return {string}  */
            function s1(e) { return e.slice(1); }
            """)
        .run();
  }

  @Test
  public void testStrictPropertiesOnEnum3() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            """
            /** @enum {string} */ var E = {S:'s'};
            /** @param {?E} e
             @return {string}  */
            function s1(e) { return e.slice(1); }
            """)
        .run();
  }

  @Test
  public void testStrictPropertiesOnEnum4() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            """
            /** @enum {?string} */ var E = {S:'s'};
            /** @param {?E} e
             @return {string}  */
            function s1(e) { return e.slice(1); }
            """)
        .run();
  }

  @Test
  public void testDestructuring() {
    newTest()
        .addSource(
            """
            /** @param {{id:(number|undefined)}=} o */
            function f(o = {}) {
               const {id} = o;
            }
            """)
        .run();
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope1a() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {!Object} o
             * @return {string}
             */
            function f(o) {
              o.x = 1;
              return o.x;
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope1b() {
    // With strict missing properties assigning a unknown property after type
    // definition causes a warning.
    newTest()
        .addSource(
            """
            /** @param {!Object} o
             * @return {string}
             */
            function f(o) {
              o.x = 1;
            }
            """)
        .addDiagnostic("Property x never defined on Object")
        .run();
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope2a() {
    // This test is specifically checking loose property check behavior.
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /**
              @param {!Object} o
              @param {number?} x
              @return {string}
             */
            function f(o, x) {
              o.x = 'a';
              if (x) {o.x = x;}
              return o.x;
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (number|string)
            required: string
            """)
        .run();
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope2b() {
    newTest()
        .addSource(
            """
            /**
              @param {!Object} o
              @param {number?} x
             */
            function f(o, x) {
              o.x = 'a';
            }
            """)
        .addDiagnostic("Property x never defined on Object")
        .run();
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope2c() {
    newTest()
        .addSource(
            """
            /**
              @param {!Object} o
              @param {number?} x
             */
            function f(o, x) {
              if (x) { o.x = x; }
            }
            """)
        .addDiagnostic("Property x never defined on Object")
        .run();
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope3() {
    // inferrence of undeclared properties
    newTest()
        .addSource(
            """
            /**
             * @param {!Object} o
             * @param {number?} x
             * @return {string}
             */
            function f(o, x) {
              if (x) {
                o.x = x;
              } else {
                o.x = 'a';
              }
              return o.x;
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (number|string)
            required: string
            """)
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .run();
  }

  @Test
  public void testMissingPropertyOfUnion1() {
    newTest()
        .addSource(
            """
            /**
              @param {number|string} obj
              @return {?}
             */
            function f(obj, x) {
              return obj.foo;
            }
            """)
        .addDiagnostic("Property foo never defined on (Number|String)")
        .run();
  }

  @Test
  public void testMissingPropertyOfUnion2() {
    newTest()
        .addSource(
            """
            /**
              @param {*=} opt_o
              @return {string|null}
             */
            function f(opt_o) {
              if (opt_o) {
                if (typeof opt_o !== 'string') {
                  return opt_o.toString();
                }
              }
              return null;
            }
            """)
        .addDiagnostic(
            "Property toString never defined on *" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run(); // ?
  }

  @Test
  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty1() {
    newTest()
        .addSource(
            """
            /** @constructor */var T = function() { this.x = ''; };
            /** @type {number} */ T.prototype.x = 0;
            """)
        .addDiagnostic(
            """
            assignment to property x of T
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty2() {
    newTest()
        .addSource(
            """
            /** @constructor */var T = function() { this.x = ''; };
            /** @type {number} */ T.prototype.x;
            """)
        .addDiagnostic(
            """
            assignment to property x of T
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty3() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @type {Object} */ var n = {};
            /** @constructor */ n.T = function() { this.x = ''; };
            /** @type {number} */ n.T.prototype.x = 0;
            """)
        .addDiagnostic(
            """
            assignment to property x of n.T
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty4() {
    newTest()
        .addSource(
            """
            var n = {};
            /** @constructor */ n.T = function() { this.x = ''; };
            /** @type {number} */ n.T.prototype.x = 0;
            """)
        .addDiagnostic(
            """
            assignment to property x of n.T
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testPropertyUsedBeforeDefinition1() {
    newTest()
        .addSource(
            """
            /** @constructor */ var T = function() {};
            /** @return {string} */
            T.prototype.f = function() { return this.g(); };
            /** @return {number} */ T.prototype.g = function() { return 1; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testPropertyUsedBeforeDefinition2() {
    newTest()
        .addSource(
            """
            var n = {};
            /** @constructor */ n.T = function() {};
            /** @return {string} */
            n.T.prototype.f = function() { return this.g(); };
            /** @return {number} */ n.T.prototype.g = function() { return 1; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testAdd1() {
    newTest().addSource("/**@return {void}*/function foo(){var a = 'abc'+foo();}").run();
  }

  @Test
  public void testAdd2() {
    newTest().addSource("/**@return {void}*/function foo(){var a = foo()+4;}").run();
  }

  @Test
  public void testAdd3() {
    newTest()
        .addSource(
            """
            /** @type {string} */ var a = 'a';
            /** @type {string} */ var b = 'b';
            /** @type {string} */ var c = a + b;
            """)
        .run();
  }

  @Test
  public void testAdd4() {
    newTest()
        .addSource(
            """
            /** @type {number} */ var a = 5;
            /** @type {string} */ var b = 'b';
            /** @type {string} */ var c = a + b;
            """)
        .run();
  }

  @Test
  public void testAdd5() {
    newTest()
        .addSource(
            """
            /** @type {string} */ var a = 'a';
            /** @type {number} */ var b = 5;
            /** @type {string} */ var c = a + b;
            """)
        .run();
  }

  @Test
  public void testAdd6() {
    newTest()
        .addSource(
            """
            /** @type {number} */ var a = 5;
            /** @type {number} */ var b = 5;
            /** @type {number} */ var c = a + b;
            """)
        .run();
  }

  @Test
  public void testAdd7() {
    newTest()
        .addSource(
            """
            /** @type {number} */ var a = 5;
            /** @type {string} */ var b = 'b';
            /** @type {number} */ var c = a + b;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testAdd8() {
    newTest()
        .addSource(
            """
            /** @type {string} */ var a = 'a';
            /** @type {number} */ var b = 5;
            /** @type {number} */ var c = a + b;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testAdd9() {
    newTest()
        .addSource(
            """
            /** @type {number} */ var a = 5;
            /** @type {number} */ var b = 5;
            /** @type {string} */ var c = a + b;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testAdd10() {
    // d.e.f will have unknown type.
    newTest()
        .addSource(
            suppressMissingProperty("e", "f")
                + """
                /** @type {number} */ var a = 5;
                /** @type {string} */ var c = a + d.e.f;
                """)
        .run();
  }

  @Test
  public void testAdd11() {
    // d.e.f will have unknown type.
    newTest()
        .addSource(
            suppressMissingProperty("e", "f")
                + """
                /** @type {number} */ var a = 5;
                /** @type {number} */ var c = a + d.e.f;
                """)
        .run();
  }

  @Test
  public void testAdd12() {
    newTest()
        .addSource(
            """
            /** @return {(number|string)} */ function a() { return 5; }
            /** @type {number} */ var b = 5;
            /** @type {boolean} */ var c = a() + b;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (number|string)
            required: boolean
            """)
        .run();
  }

  @Test
  public void testAdd13() {
    newTest()
        .addSource(
            """
            /** @type {number} */ var a = 5;
            /** @return {(number|string)} */ function b() { return 5; }
            /** @type {boolean} */ var c = a + b();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (number|string)
            required: boolean
            """)
        .run();
  }

  @Test
  public void testAdd14() {
    newTest()
        .addSource(
            """
            /** @type {(null|string)} */ var a = unknown;
            /** @type {number} */ var b = 5;
            /** @type {boolean} */ var c = a + b;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (number|string)
            required: boolean
            """)
        .run();
  }

  @Test
  public void testAdd15() {
    newTest()
        .addSource(
            """
            /** @type {number} */ var a = 5;
            /** @return {(number|string)} */ function b() { return 5; }
            /** @type {boolean} */ var c = a + b();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (number|string)
            required: boolean
            """)
        .run();
  }

  @Test
  public void testAdd16() {
    newTest()
        .addSource(
            """
            /** @type {(undefined|string)} */ var a = unknown;
            /** @type {number} */ var b = 5;
            /** @type {boolean} */ var c = a + b;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (number|string)
            required: boolean
            """)
        .run();
  }

  @Test
  public void testAdd17() {
    newTest()
        .addSource(
            """
            /** @type {number} */ var a = 5;
            /** @type {(undefined|string)} */ var b = unknown;
            /** @type {boolean} */ var c = a + b;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (number|string)
            required: boolean
            """)
        .run();
  }

  @Test
  public void testAdd18() {
    newTest()
        .addSource(
            """
            function f() {};
            /** @type {string} */ var a = 'a';
            /** @type {number} */ var c = a + f();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testAdd19() {
    newTest()
        .addSource(
            """
            /** @param {number} opt_x
            @param {number} opt_y
            @return {number} */ function f(opt_x, opt_y) {
            return opt_x + opt_y;}
            """)
        .run();
  }

  @Test
  public void testAdd20() {
    newTest()
        .addSource(
            """
            /** @param {!Number} opt_x
            @param {!Number} opt_y
            @return {number} */ function f(opt_x, opt_y) {
            return opt_x + opt_y;}
            """)
        .run();
  }

  @Test
  public void testAdd21() {
    newTest()
        .addSource(
            """
            /** @param {Number|Boolean} opt_x
            @param {number|boolean} opt_y
            @return {number} */ function f(opt_x, opt_y) {
            return opt_x + opt_y;}
            """)
        .run();
  }

  @Test
  public void testNumericComparison1() {
    newTest().addSource("/**@param {number} a*/ function f(a) {return a < 3;}").run();
  }

  @Test
  public void testNumericComparison2() {
    newTest()
        .addSource("/**@param {!Object} a*/ function f(a) {return a < 3;}")
        .addDiagnostic(
            """
            left side of numeric comparison
            found   : Object
            required: (bigint|number)
            """)
        .run();
  }

  @Test
  public void testNumericComparison3() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS)
        .addSource("/**@param {string} a*/ function f(a) {return a < 3;}")
        .run();
  }

  @Test
  public void testNumericComparison4() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS)
        .addSource(
            """
            /**@param {(number|undefined)} a*/
            function f(a) {return a < 3;}
            """)
        .run();
  }

  @Test
  public void testNumericComparison5() {
    newTest()
        .addSource("/**@param {*} a*/ function f(a) {return a < 3;}")
        .addDiagnostic(
            """
            left side of numeric comparison
            found   : *
            required: (bigint|number)
            """)
        .run();
  }

  @Test
  public void testNumericComparison6() {
    newTest()
        .addSource("/**@return {void} */ function foo() { if (3 >= foo()) return; }")
        .addDiagnostic(
            """
            right side of numeric comparison
            found   : undefined
            required: (bigint|number)
            """)
        .run();
  }

  @Test
  public void testStringComparison1() {
    newTest().addSource("/**@param {string} a*/ function f(a) {return a < 'x';}").run();
  }

  @Test
  public void testStringComparison2() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS)
        .addSource("/**@param {Object} a*/ function f(a) {return a < 'x';}")
        .run();
  }

  @Test
  public void testStringComparison3() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS)
        .addSource("/**@param {number} a*/ function f(a) {return a < 'x';}")
        .run();
  }

  @Test
  public void testStringComparison4() {
    newTest()
        .addSource(
            """
            /**@param {string|undefined} a*/
            function f(a) {return a < 'x';}
            """)
        .run();
  }

  @Test
  public void testStringComparison5() {
    newTest()
        .addSource(
            """
            /**@param {*} a*/
            function f(a) {return a < 'x';}
            """)
        .run();
  }

  @Test
  public void testStringComparison6() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS)
        .addSource(
            """
            /**@return {void} */
            function foo() { if ('a' >= foo()) return; }
            """)
        .addDiagnostic(
            """
            right side of comparison
            found   : undefined
            required: string
            """)
        .run();
  }

  @Test
  public void testValueOfComparison1() {
    newTest()
        .addExterns(new TestExternsBuilder().addObject().build())
        .addSource(
            """
            /** @constructor */function O() {};
            /**@override*/O.prototype.valueOf = function() { return 1; };
            /**@param {!O} a
            @param {!O} b*/ function f(a,b) { return a < b; }
            """)
        .run();
  }

  @Test
  public void testValueOfComparison2() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS)
        .addExterns(new TestExternsBuilder().addObject().build())
        .addSource(
            """
            /** @constructor */function O() {};
            /**@override*/O.prototype.valueOf = function() { return 1; };
            /**
             * @param {!O} a
             * @param {number} b
             */
            function f(a,b) { return a < b; }
            """)
        .run();
  }

  @Test
  public void testValueOfComparison3() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS)
        .addExterns(new TestExternsBuilder().addObject().build())
        .addSource(
            """
            /** @constructor */function O() {};
            /**@override*/O.prototype.toString = function() { return 'o'; };
            /**
             * @param {!O} a
             * @param {string} b
             */
            function f(a,b) { return a < b; }
            """)
        .run();
  }

  @Test
  public void testGenericRelationalExpression() {
    newTest()
        .addSource(
            """
            /**@param {*} a
            @param {*} b*/
            function f(a,b) {return a < b;}
            """)
        .run();
  }

  @Test
  public void testInstanceof1() {
    newTest()
        .addSource(
            """
            function foo(){
            if (bar instanceof 3)return;}
            """)
        .addDiagnostic(
            """
            instanceof requires an object
            found   : number
            required: Object
            """)
        .run();
  }

  @Test
  public void testInstanceof2() {
    newTest()
        .addSource(
            """
            /**@return {void}*/function foo(){
            if (foo() instanceof Object)return;}
            """)
        .addDiagnostic(
            """
            deterministic instanceof yields false
            found   : undefined
            required: NoObject
            """)
        .run();
  }

  @Test
  public void testInstanceof3() {
    newTest()
        .addSource(
            """
            /**@return {*} */function foo(){
            if (foo() instanceof Object)return;}
            """)
        .run();
  }

  @Test
  public void testInstanceof4() {
    newTest()
        .addSource(
            """
            /**@return {(Object|number)} */function foo(){
            if (foo() instanceof Object)return 3;}
            """)
        .run();
  }

  @Test
  public void testInstanceof5() {
    // No warning for unknown types.
    newTest()
        .addSource(
            """
            /** @return {?} */ function foo(){
            if (foo() instanceof Object)return;}
            """)
        .run();
  }

  @Test
  public void testInstanceof6() {
    newTest()
        .addSource(
            """
            /**@return {(Array|number)} */function foo(){
            if (foo() instanceof Object)return 3;}
            """)
        .run();
  }

  @Test
  public void testInstanceOfReduction3() {
    newTest()
        .addSource(
            """
            /**\s
             * @param {Object} x\s
             * @param {Function} y\s
             * @return {boolean}\s
             */
            var f = function(x, y) {
              return x instanceof y;
            };
            """)
        .run();
  }

  @Test
  public void testScoping1() {
    newTest()
        .addSource(
            """
            /**@param {string} a*/function foo(a){
              /**@param {Array|string} a*/function bar(a){
                if (a instanceof Array)return;
              }
            }
            """)
        .run();
  }

  @Test
  public void testScoping2() {
    newTest()
        .addSource(
            """
            /** @type {number} */ var a;
            function Foo() {
              /** @type {string} */ var a;
            }
            """)
        .run();
  }

  @Test
  public void testScoping3() {
    newTest()
        .addSource(
            """
            /** @type {Number} */ var b;
            /** @type {!String} */ var b;
            """)
        .addDiagnostic(
            """
            variable b redefined with type String, original definition at testcode0:1 with type (Number|null)
            """)
        .run();
  }

  @Test
  public void testScoping4() {
    newTest()
        .addSource("/** @type{Number}*/var b; if (true) /** @type{!String} */var b;")
        .addDiagnostic(
            """
            variable b redefined with type String, original definition at testcode0:1 with type (Number|null)
            """)
        .run();
  }

  @Test
  public void testScoping5() {
    // multiple definitions are not checked by the type checker but by a
    // subsequent pass
    newTest().addSource("if (true) var b; var b;").run();
  }

  @Test
  public void testScoping6() {
    // multiple definitions are not checked by the type checker but by a
    // subsequent pass
    newTest().addSource("if (true) var b; if (true) var b;").run();
  }

  @Test
  public void testScoping7() {
    newTest()
        .addSource(
            """
            /** @constructor */function A() {
              /** @type {!A} */this.a = null;
            }
            """)
        .addDiagnostic(
            """
            assignment to property a of A
            found   : null
            required: A
            """)
        .run();
  }

  @Test
  public void testScoping8() {
    newTest()
        .addSource(
            """
            /** @constructor */function A() {}
            /** @constructor */function B() {
              /** @type {!A} */this.a = null;
            }
            """)
        .addDiagnostic(
            """
            assignment to property a of B
            found   : null
            required: A
            """)
        .run();
  }

  @Test
  public void testScoping9() {
    newTest()
        .addSource(
            """
            /** @constructor */function B() {
              /** @type {!A} */this.a = null;
            }
            /** @constructor */function A() {}
            """)
        .addDiagnostic(
            """
            assignment to property a of B
            found   : null
            required: A
            """)
        .run();
  }

  @Test
  public void testScoping10() {
    TypeCheckResult p = parseAndTypeCheckWithScope("var a = function b(){};");

    // a declared, b is not
    assertScope(p.scope).declares("a");
    assertScope(p.scope).doesNotDeclare("b");

    // checking that a has the correct assigned type
    assertThat(p.scope.getVar("a").getType().toString()).isEqualTo("function(): undefined");
  }

  @Test
  public void testDontDropPropertiesInUnion1() {
    newTest()
        .addSource(
            """
            /** @param {{a: number}|{a:number, b:string}} x */
            function f(x) {
              var /** null */ n = x.b;
            }
            """)
        .addDiagnostic(STRICT_INEXISTENT_UNION_PROPERTY)
        .addDiagnostic(TypeValidator.TYPE_MISMATCH_WARNING)
        .run();
  }

  @Test
  public void testDontDropPropertiesInUnion2() {
    newTest()
        .addSource(
            """
            /** @param {{a:number, b:string}|{a: number}} x */
            function f(x) {
              var /** null */ n = x.b;
            }
            """)
        .addDiagnostic(STRICT_INEXISTENT_UNION_PROPERTY)
        .addDiagnostic(TypeValidator.TYPE_MISMATCH_WARNING)
        .run();
  }

  @Test
  public void testDontDropPropertiesInUnion3() {
    newTest()
        .addSource(
            """
            /** @param {{a: number}|{a:number, b:string}} x */
            function f(x) {}
            /** @param {{a: number}} x */
            function g(x) { return x.b; }
            """)
        .addDiagnostic(TypeCheck.INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testDontDropPropertiesInUnion4() {
    newTest()
        .addSource(
            """
            /** @param {{a: number}|{a:number, b:string}} x */
            function f(x) {}
            /** @param {{c: number}} x */
            function g(x) { return x.b; }
            """)
        .addDiagnostic("Property b never defined on x")
        .run();
  }

  @Test
  public void testDontDropPropertiesInUnion5() {
    newTest()
        .addSource(
            """
            /** @param {{a: number}|{a: number, b: string}} x */
            function f(x) {}
            f({a: 123});
            """)
        .run();
  }

  @Test
  public void testDontDropPropertiesInUnion6() {
    newTest()
        .addSource(
            """
            /** @param {{a: number}|{a: number, b: string}} x */
            function f(x) {
              var /** null */ n = x;
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : ({
              a: number,
              b: string
            }|{a: number})
            required: null
            """)
        .run();
  }

  @Test
  public void testDontDropPropertiesInUnion7() {
    newTest()
        .addSource(
            """
            /** @param {{a: number}|{a:number, b:string}} x */
            function f(x) {}
            /** @param {{c: number}|{c:number, d:string}} x */
            function g(x) { return x.b; }
            """)
        .addDiagnostic(TypeCheck.INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testScoping11() {
    // named function expressions create a binding in their body only
    // the return is wrong but the assignment is OK since the type of b is ?
    newTest()
        .addSource("/** @return {number} */var a = function b(){ return b };")
        .addDiagnostic(
            """
            inconsistent return type
            found   : function(): number
            required: number
            """)
        .run();
  }

  @Test
  public void testScoping12() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            /** @type {number} */ F.prototype.bar = 3;
            /** @param {!F} f */ function g(f) {
              /** @return {string} */
              function h() {
                return f.bar;
              }
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testFunctionArguments1() {
    testFunctionType(
        """
        /** @param {number} a
        @return {string} */
        function f(a) {}
        """,
        "function(number): string");
  }

  @Test
  public void testFunctionArguments2() {
    testFunctionType(
        """
        /** @param {number} opt_a
        @return {string} */
        function f(opt_a) {}
        """,
        "function(number=): string");
  }

  @Test
  public void testFunctionArguments3() {
    testFunctionType(
        """
        /** @param {number} b
        @return {string} */
        function f(a,b) {}
        """,
        "function(?, number): string");
  }

  @Test
  public void testFunctionArguments4() {
    testFunctionType(
        """
        /** @param {number} opt_a
        @return {string} */
        function f(a,opt_a) {}
        """,
        "function(?, number=): string");
  }

  @Test
  public void testFunctionArguments5() {
    newTest()
        .addSource("function a(opt_a,a) {}")
        .addDiagnostic("optional arguments must be at the end")
        .run();
  }

  @Test
  public void testFunctionArguments6() {
    newTest()
        .addSource("function a(var_args,a) {}")
        .addDiagnostic("variable length argument must be last")
        .run();
  }

  @Test
  public void testFunctionArguments7() {
    newTest()
        .addSource(
            """
            /** @param {number} opt_a
            @return {string} */
            function a(a,opt_a,var_args) {}
            """)
        .run();
  }

  @Test
  public void testFunctionArguments8() {
    newTest()
        .addSource("function a(a,opt_a,var_args,b) {}")
        .addDiagnostic("variable length argument must be last")
        .run();
  }

  @Test
  public void testFunctionArguments9() {
    // testing that only one error is reported
    newTest()
        .addSource("function a(a,opt_a,var_args,b,c) {}")
        .addDiagnostic("variable length argument must be last")
        .run();
  }

  @Test
  public void testFunctionArguments10() {
    // testing that only one error is reported
    newTest()
        .addSource("function a(a,opt_a,b,c) {}")
        .addDiagnostic("optional arguments must be at the end")
        .run();
  }

  @Test
  public void testFunctionArguments11() {
    newTest()
        .addSource("function a(a,opt_a,b,c,var_args,d) {}")
        .addDiagnostic("optional arguments must be at the end")
        .run();
  }

  @Test
  public void testFunctionArguments12() {
    newTest()
        .addSource("/** @param {String} foo  */function bar(baz){}")
        .addDiagnostic("parameter foo does not appear in bar's parameter list")
        .run();
  }

  @Test
  public void testFunctionArguments13() {
    // verifying that the argument type have non-inferable types
    newTest()
        .addSource(
            """
            /** @return {boolean} */ function u() { return true; }
            /** @param {boolean} b
            @return {?boolean} */
            function f(b) { if (u()) { b = null; } return b; }
            """)
        .addDiagnostic(
            """
            assignment
            found   : null
            required: boolean
            """)
        .run();
  }

  @Test
  public void testFunctionArguments14() {
    newTest()
        .addSource(
            """
            /**
             * @param {string} x
             * @param {number} opt_y
             * @param {boolean} var_args
             */ function f(x, opt_y, var_args) {}
            f('3'); f('3', 2); f('3', 2, true); f('3', 2, true, false);
            """)
        .run();
  }

  @Test
  public void testFunctionArguments15() {
    newTest()
        .addSource(
            """
            /** @param {?function(*)} f */
            function g(f) { f(1, 2); }
            """)
        .addDiagnostic(
            """
            Function f: called with 2 argument(s). Function requires at least 1 argument(s) and no more than 1 argument(s).
            """)
        .run();
  }

  @Test
  public void testFunctionArguments16() {
    newTest()
        .addSource(
            """
            /** @param {...number} var_args */
            function g(var_args) {}
            g(1, true);
            """)
        .addDiagnostic(
            """
            actual parameter 2 of g does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testUndefinedPassedForVarArgs() {
    newTest()
        .addSource(
            """
            /** @param {...number} var_args */
            function g(var_args) {}
            g(undefined, 1);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : undefined
            required: number
            """)
        .run();
  }

  @Test
  public void testFunctionArguments17() {
    newTest()
        .addSource(
            """
            /** @param {booool|string} x */
            function f(x) { g(x) }
            /** @param {number} x */
            function g(x) {}
            """)
        .addDiagnostic("Bad type annotation. Unknown type booool")
        .run();
  }

  @Test
  public void testFunctionArguments18() {
    newTest()
        .addSource(
            """
            function f(x) {}
            f(/** @param {number} y */ (function() {}));
            """)
        .addDiagnostic("parameter y does not appear in <anonymous>'s parameter list")
        .run();
  }

  @Test
  public void testVarArgParameterWithTemplateType() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            """
            /**
             * @template T
             * @param {...T} var_args
             * @return {T}
             */
            function firstOf(var_args) { return arguments[0]; }
            /** @type {null} */ var a = firstOf('hi', 1);
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (number|string)
            required: null
            """)
        .run();
  }

  @Test
  public void testRestParameters() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            """
            /**
             * @param {...number} x
             */
            function f(...x) {
              var /** string */ s = x[0];
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testRestParameterWithTemplateType() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            """
            /**
             * @template T
             * @param {...T} rest
             * @return {T}
             */
            function firstOf(...rest) {
              return rest[0];
            }
            /** @type {null} */ var a = firstOf('hi', 1);
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (number|string)
            required: null
            """)
        .run();
  }

  // Test that when transpiling we don't use T in the body of f; it would cause a spurious
  // unknown-type warning.
  @Test
  public void testRestParametersWithGenericsNoWarning() {
    newTest()
        .addExterns(new TestExternsBuilder().addObject().addArray().build())
        .addSource(
            """
            /**
             * @constructor
             * @template T
             */
            function Foo() {}
            /**
             * @template T
             * @param {...!Foo<T>} x
             */
            function f(...x) {
              return 123;
            }
            """)
        .run();
  }

  @Test
  public void testPrintFunctionName1() {
    // Ensures that the function name is pretty.
    newTest()
        .addSource(
            """
            var goog = {}; goog.run = function(f) {};
            goog.run();
            """)
        .addDiagnostic(
            """
            Function goog.run: called with 0 argument(s). Function requires at least 1 argument(s) and no more than 1 argument(s).
            """)
        .run();
  }

  @Test
  public void testPrintFunctionName2() {
    newTest()
        .addSource(
            """
            /** @constructor */ var Foo = function() {};
            Foo.prototype.run = function(f) {};
            (new Foo).run();
            """)
        .addDiagnostic(
            """
            Function Foo.prototype.run: called with 0 argument(s). Function requires at least 1 argument(s) and no more than 1 argument(s).
            """)
        .run();
  }

  @Test
  public void testFunctionInference1() {
    testFunctionType("function f(a) {}", "function(?): undefined");
  }

  @Test
  public void testFunctionInference2() {
    testFunctionType("function f(a,b) {}", "function(?, ?): undefined");
  }

  @Test
  public void testFunctionInference3() {
    testFunctionType("function f(var_args) {}", "function(...?): undefined");
  }

  @Test
  public void testFunctionInference4() {
    testFunctionType("function f(a,b,c,var_args) {}", "function(?, ?, ?, ...?): undefined");
  }

  @Test
  public void testFunctionInference5() {
    testFunctionType(
        """
        /** @this {Date}
        @return {string} */function f(a) {}
        """,
        "function(this:Date, ?): string");
  }

  @Test
  public void testFunctionInference6() {
    testFunctionType(
        """
        /** @this {Date}
        @return {string} */function f(opt_a) {}
        """,
        "function(this:Date, ?=): string");
  }

  @Test
  public void testFunctionInference7() {
    testFunctionType(
        "/** @this {Date} */function f(a,b,c,var_args) {}",
        "function(this:Date, ?, ?, ?, ...?): undefined");
  }

  @Test
  public void testFunctionInference8() {
    testFunctionType("function f() {}", "function(): undefined");
  }

  @Test
  public void testFunctionInference9() {
    testFunctionType("var f = function() {};", "function(): undefined");
  }

  @Test
  public void testFunctionInference10() {
    testFunctionType(
        """
        /** @this {Date}
        @param {boolean} b
        @return {string} */var f = function(a,b) {};
        """,
        "function(this:Date, ?, boolean): string");
  }

  @Test
  public void testFunctionInference11() {
    testFunctionType(
        """
        var goog = {};
        /** @return {number}*/goog.f = function(){};
        """,
        "goog.f",
        "function(): number");
  }

  @Test
  public void testFunctionInference12() {
    testFunctionType(
        """
        var goog = {};
        goog.f = function(){};
        """,
        "goog.f",
        "function(): undefined");
  }

  @Test
  public void testFunctionInference13() {
    testFunctionType(
        """
        var goog = {};
        /** @constructor */ goog.Foo = function(){};
        /** @param {!goog.Foo} f */function eatFoo(f){};
        """,
        "eatFoo",
        "function(goog.Foo): undefined");
  }

  @Test
  public void testFunctionInference14() {
    testFunctionType(
        """
        var goog = {};
        /** @constructor */ goog.Foo = function(){};
        /** @return {!goog.Foo} */function eatFoo(){ return new goog.Foo; };
        """,
        "eatFoo",
        "function(): goog.Foo");
  }

  @Test
  public void testFunctionInference15() {
    testFunctionType(
        """
        /** @constructor */ function f() {};
        f.prototype.foo = function(){};
        """,
        "f.prototype.foo",
        "function(this:f): undefined");
  }

  @Test
  public void testFunctionInference16() {
    testFunctionType(
        """
        /** @constructor */ function f() {};
        f.prototype.foo = function(){};
        """,
        "(new f).foo",
        "function(this:f): undefined");
  }

  @Test
  public void testFunctionInference17() {
    testFunctionType(
        """
        /** @constructor */ function f() {}
        function abstractMethod() {}
        /** @param {number} x */ f.prototype.foo = abstractMethod;
        """,
        "(new f).foo",
        "function(this:f, number): ?");
  }

  @Test
  public void testFunctionInference18() {
    testFunctionType(
        """
        var goog = {};
        /** @this {Date} */ goog.eatWithDate;
        """,
        "goog.eatWithDate",
        "function(this:Date): ?");
  }

  @Test
  public void testFunctionInference19() {
    testFunctionType("/** @param {string} x */ var f;", "f", "function(string): ?");
  }

  @Test
  public void testFunctionInference20() {
    testFunctionType("/** @this {Date} */ var f;", "f", "function(this:Date): ?");
  }

  @Test
  public void testFunctionInference21a() {
    newTest()
        .addSource(
            """
            var f = function() { throw 'x' };
            /** @return {boolean} */ var g = f;
            """)
        .run();
  }

  @Test
  public void testFunctionInference21b() {
    testFunctionType("var f = function() { throw 'x' };", "f", "function(): ?");
  }

  @Test
  public void testFunctionInference22() {
    newTest()
        .addSource(
            """
            /** @type {!Function} */ var f = function() { g(this); };
            /** @param {boolean} x */ var g = function(x) {};
            """)
        .run();
  }

  @Test
  public void testFunctionInference23a() {
    // We want to make sure that 'prop' isn't declared on all objects.

    // This test is specifically checking loose property check behavior.
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @type {!Function} */ var f = function() {
              /** @type {number} */ this.prop = 3;
            };
            /**
             * @param {Object} x
             * @return {string}
             */ var g = function(x) { return x.prop; };
            """)
        .run();
  }

  @Test
  public void testFunctionInference23b() {
    // We want to make sure that 'prop' isn't declared on all objects.

    newTest()
        .addSource(
            """
            /** @type {!Function} */ var f = function() {
              /** @type {number} */ this.prop = 3;
            };
            /**
             * @param {Object} x
             * @return {string}
             */ var g = function(x) { return x.prop; };
            """)
        .addDiagnostic("Property prop never defined on Object")
        .run();
  }

  @Test
  public void testFunctionInference24() {
    testFunctionType(
        "var f = (/** number */ n, /** string= */ s) => null;", "function(number, string=): ?");
  }

  @Test
  public void testFunctionInference25() {
    testFunctionType(
        "var f = (/** number */ n, /** ...string */ s) => null;", "function(number, ...string): ?");
  }

  @Test
  public void testInnerFunction1() {
    newTest()
        .addSource(
            """
            function f() {
              /** @type {number} */ var x = 3;
              function g() { x = null; }
              return x;
            }
            """)
        .addDiagnostic(
            """
            assignment
            found   : null
            required: number
            """)
        .run();
  }

  @Test
  public void testInnerFunction2() {
    newTest()
        .addSource(
            """
            /** @return {number} */
            function f() {
              var x = null;
              function g() { x = 3; }
              g();
              return x;
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (null|number)
            required: number
            """)
        .run();
  }

  @Test
  public void testInnerFunction3() {
    newTest()
        .addSource(
            """
            var x = null;
            /** @return {number} */
            function f() {
              x = 3;
              /** @return {number} */
              function g() { x = true; return x; }
              return x;
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testInnerFunction4() {
    newTest()
        .addSource(
            """
            var x = null;
            /** @return {number} */
            function f() {
              x = '3';
              /** @return {number} */
              function g() { x = 3; return x; }
              return x;
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testInnerFunction5() {
    newTest()
        .addSource(
            """
            /** @return {number} */
            function f() {
              var x = 3;
              /** @return {number} */
              function g() { var x = 3;x = true; return x; }
              return x;
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testInnerFunction6NullishCoalesce() {
    testClosureTypes(
        """
        function f() {
         var x = null ?? function() {};
         function g() { if (goog.isFunction(x)) { x(1); } }
         g();
        }
        """,
        """
        Function x: called with 1 argument(s). Function requires at least 0 argument(s) and no more than 0 argument(s).
        """);
  }

  @Test
  public void testInnerFunction7NullishCoalesce() {
    testClosureTypes(
        """
        function f() {
         /** @type {function()} */
         var x = null ?? function() {};
         function g() { if (goog.isFunction(x)) { x(1); } }
         g();
        }
        """,
        """
        Function x: called with 1 argument(s). Function requires at least 0 argument(s) and no more than 0 argument(s).
        """);
  }

  @Test
  public void testInnerFunction8() {
    testClosureTypes(
        """
        function f() {
          function x() {};
          function g() { if (goog.isFunction(x)) { x(1); } }
          g();
        }
        """,
        """
        Function x: called with 1 argument(s). Function requires at least 0 argument(s) and no more than 0 argument(s).
        """);
  }

  @Test
  public void testInnerFunction9() {
    newTest()
        .addSource(
            """
            function f() {
              var x = 3;
              function g() { x = null; };
              function h() { return x == null; }
              return h();
            }
            """)
        .run();
  }

  @Test
  public void testInnerFunction10() {
    newTest()
        .addSource(
            """
            function f() {
              /** @type {?number} */ var x = null;
              /** @return {string} */
              function g() {
                if (!x) {
                  x = 1;
                }
                return x;
              }
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testInnerFunction11() {
    // TODO(nicksantos): This is actually bad inference, because
    // h sets x to null. We should fix this, but for now we do it
    // this way so that we don't break existing binaries. We will
    // need to change TypeInference#isUnflowable to fix this.
    newTest()
        .addSource(
            """
            function f() {
              /** @type {?number} */ var x = null;
              /** @return {number} */
              function g() {
                x = 1;
                h();
                return x;
              }
              function h() {
                x = null;
              }
            }
            """)
        .run();
  }

  @Test
  public void testMethodInference1() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            /** @return {number} */ F.prototype.foo = function() { return 3; };
            /** @constructor
             * @extends {F} */
            function G() {}
            /** @override */ G.prototype.foo = function() { return true; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testMethodInference2() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor */ goog.F = function() {};
            /** @return {number} */ goog.F.prototype.foo = function() { return 3; };
            /** @constructor
             * @extends {goog.F} */
            goog.G = function() {};
            /** @override */ goog.G.prototype.foo = function() { return true; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testMethodInference3() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            /** @param {boolean} x
             * @return {number} */
            F.prototype.foo = function(x) { return 3; };
            /** @constructor
             * @extends {F} */
            function G() {}
            /** @override */
            G.prototype.foo = function(x) { return x; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testMethodInference4() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            /** @param {boolean} x
             * @return {number} */
            F.prototype.foo = function(x) { return 3; };
            /** @constructor
             * @extends {F} */
            function G() {}
            /** @override */
            G.prototype.foo = function(y) { return y; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testMethodInference5() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            /** @param {number} x
             * @return {string} */
            F.prototype.foo = function(x) { return 'x'; };
            /** @constructor
             * @extends {F} */
            function G() {}
            /** @type {number} */ G.prototype.num = 3;
            /** @override */
            G.prototype.foo = function(y) { return this.num + y; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testMethodInference6() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            /** @param {number} x */ F.prototype.foo = function(x) { };
            /** @constructor
             * @extends {F} */
            function G() {}
            /** @override */ G.prototype.foo = function() { };
            (new G()).foo(1);
            """)
        .run();
  }

  @Test
  public void testMethodInference7() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            F.prototype.foo = function() { };
            /** @constructor
             * @extends {F} */
            function G() {}
            /** @override */ G.prototype.foo = function(x, y) { };
            """)
        .addDiagnostic(
            """
            mismatch of the foo property type and the type of the property it overrides from superclass F
            original: function(this:F): undefined
            override: function(this:G, ?, ?): undefined
            """)
        .run();
  }

  @Test
  public void testMethodInference8() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            F.prototype.foo = function() { };
            /** @constructor
             * @extends {F} */
            function G() {}
            /** @override */
            G.prototype.foo = function(opt_b, var_args) { };
            (new G()).foo(1, 2, 3);
            """)
        .run();
  }

  @Test
  public void testMethodInference9() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            F.prototype.foo = function() { };
            /** @constructor
             * @extends {F} */
            function G() {}
            /** @override */
            G.prototype.foo = function(var_args, opt_b) { };
            """)
        .addDiagnostic("variable length argument must be last")
        .run();
  }

  @Test
  public void testStaticMethodDeclaration1() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() { F.foo(true); }
            /** @param {number} x */ F.foo = function(x) {};
            """)
        .addDiagnostic(
            """
            actual parameter 1 of F.foo does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testStaticMethodDeclaration2() {
    newTest()
        .addSource(
            """
            var goog = goog || {}; function f() { goog.foo(true); }
            /** @param {number} x */ goog.foo = function(x) {};
            """)
        .addDiagnostic(
            """
            actual parameter 1 of goog.foo does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testStaticMethodDeclaration3() {
    newTest()
        .addSource(
            """
            var goog = goog || {}; function f() { goog.foo(true); }
            goog.foo = function() {};
            """)
        .addDiagnostic(
            """
            Function goog.foo: called with 1 argument(s). Function requires at least 0 argument(s) and no more than 0 argument(s).
            """)
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl1() {
    newTest()
        .addSource(
            """
            var goog = goog || {};
            /** @param {number} x */ goog.foo = function(x) {};
            /** @param {number} x */ goog.foo = function(x) {};
            """)
        .addDiagnostic("variable goog.foo redefined, original definition at testcode0:2")
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl2() {
    newTest()
        .addSource(
            """
            var goog = goog || {};
            /** @param {number} x */ goog.foo = function(x) {};
            /** @param {number} x
             * @suppress {duplicate} */
            goog.foo = function(x) {};
            """)
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl3() {
    newTest()
        .addSource(
            """
            var goog = goog || {};
            goog.foo = function(x) {};
            goog.foo = function(x) {};
            """)
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl4() {
    newTest()
        .addSource(
            """
            var goog = goog || {};
            /** @type {Function} */ goog.foo = function(x) {};
            goog.foo = function(x) {};
            """)
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl5() {
    newTest()
        .addSource(
            """
            var goog = goog || {};
            goog.foo = function(x) {};
            /** @return {undefined} */ goog.foo = function(x) {};
            """)
        .addDiagnostic("variable goog.foo redefined, original definition at testcode0:2")
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl6() {
    // Make sure the CAST node doesn't interfere with the @suppress
    // annotation.
    newTest()
        .addSource(
            """
            var goog = goog || {};
            goog.foo = function(x) {};
            /**
             * @suppress {duplicate}
             * @return {undefined}
             */
            goog.foo =
               /** @type {!Function} */ (function(x) {});
            """)
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl7() {
    newTest()
        .addSource(
            """
            /** @type {string} */ var foo;
            var z = function foo() {};
            """)
        .addDiagnostic(TypeCheck.FUNCTION_MASKS_VARIABLE)
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl8() {
    newTest()
        .addSource(
            """
            /** @fileoverview @suppress {duplicate} */
            /** @type {string} */ var foo;
            var z = function foo() {};
            """)
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDecl1() {
    newTest()
        .addSource(
            """
            var goog = goog || {};
            /** @type {Foo} */ goog.foo;
            /** @type {Foo} */ goog.foo;
            /** @constructor */ function Foo() {}
            """)
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDecl2() {
    newTest()
        .addSource(
            """
            var goog = goog || {};
            /** @type {Foo} */ goog.foo;
            /** @type {Foo}
             * @suppress {duplicate} */ goog.foo;
            /** @constructor */ function Foo() {}
            """)
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDecl3() {
    newTest()
        .addSource(
            """
            var goog = goog || {};
            /** @type {!Foo} */ goog.foo;
            /** @type {string} */ goog.foo;
            /** @constructor */ function Foo() {}
            """)
        .addDiagnostic(
            """
            variable goog.foo redefined with type string, original definition at testcode0:2 with type Foo
            """)
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDecl4() {
    testClosureTypesMultipleWarnings(
        """
        /** @type {!Foo} */ goog.foo;
        /** @type {string} */ goog.foo = 'x';
        /** @constructor */ function Foo() {}
        """,
        ImmutableList.of(
            """
            assignment to property foo of goog
            found   : string
            required: Foo\
            """,
            // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
            """
            variable goog.foo redefined with type string, original definition at testcode:1 with type Foo\
            """));
  }

  @Test
  public void testDuplicateStaticPropertyDecl5() {
    testClosureTypesMultipleWarnings(
        """
        var goog = goog || {};
        /** @type {!Foo} */ goog.foo;
        /** @type {string}
         * @suppress {duplicate} */ goog.foo = 'x';
        /** @constructor */ function Foo() {}
        """,
        ImmutableList.of(
            """
            assignment to property foo of goog
            found   : string
            required: Foo\
            """));
  }

  @Test
  public void testDuplicateStaticPropertyDecl6() {
    newTest()
        .addSource(
            """
            var goog = goog || {};
            /** @type {string} */ goog.foo = 'y';
            /** @type {string}
             * @suppress {duplicate} */ goog.foo = 'x';
            """)
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDecl7() {
    newTest()
        .addSource(
            """
            var goog = goog || {};
            /** @param {string} x */ goog.foo;
            /** @type {function(string)} */ goog.foo;
            """)
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDecl8() {
    newTest()
        .addSource(
            """
            var goog = goog || {};
            /** @return {EventCopy} */ goog.foo;
            /** @constructor */ function EventCopy() {}
            /** @return {EventCopy} */ goog.foo;
            """)
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDecl9() {
    newTest()
        .addSource(
            """
            var goog = goog || {};
            /** @return {EventCopy} */ goog.foo;
            /** @return {EventCopy} */ goog.foo;
            /** @constructor */ function EventCopy() {}
            """)
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDec20() {
    newTest()
        .addSource(
            """
            /**
             * @fileoverview
             * @suppress {duplicate}
             */
            var goog = goog || {};
            /** @type {string} */ goog.foo = 'y';
            /** @type {string} */ goog.foo = 'x';
            """)
        .run();
  }

  @Test
  public void testDuplicateLocalVarDecl() {
    testClosureTypesMultipleWarnings(
        """
        /** @param {number} x */
        function f(x) { /** @type {string} */ var x = ''; }
        """,
        ImmutableList.of(
            // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
            """
            variable x redefined with type string, original definition at testcode:2 with type number\
            """,
            """
            initializing variable
            found   : string
            required: number\
            """));
  }

  @Test
  public void testDuplicateLocalVarDeclSuppressed() {
    // We can't just leave this to VarCheck since otherwise if that warning is suppressed, we'll end
    // up redeclaring it as undefined in the function block, which can cause spurious errors.
    newTest()
        .addSource(
            """
            /** @suppress {duplicate} */
            function f(x) {
              var x = x;
              x.y = true;
            }
            """)
        .run();
  }

  @Test
  public void testShadowBleedingFunctionName() {
    // This is allowed and creates a new binding inside the function shadowing the bled name.
    newTest()
        .addSource(
            """
            var f = function x() {
              var x;
              var /** undefined */ y = x;
            };
            """)
        .run();
  }

  @Test
  public void testDuplicateInstanceMethod1() {
    // If there's no jsdoc on the methods, then we treat them like
    // any other inferred properties.
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            F.prototype.bar = function() {};
            F.prototype.bar = function() {};
            """)
        .run();
  }

  @Test
  public void testDuplicateInstanceMethod2() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            /** jsdoc */ F.prototype.bar = function() {};
            /** jsdoc */ F.prototype.bar = function() {};
            """)
        .run();
  }

  @Test
  public void testDuplicateInstanceMethod3() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            F.prototype.bar = function() {};
            /** jsdoc */ F.prototype.bar = function() {};
            """)
        .run();
  }

  @Test
  public void testDuplicateInstanceMethod4() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            /** jsdoc */ F.prototype.bar = function() {};
            F.prototype.bar = function() {};
            """)
        .run();
  }

  @Test
  public void testDuplicateInstanceMethod5() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            /** jsdoc
             * @return {number} */ F.prototype.bar = function() {
              return 3;
            };
            /** jsdoc
             * @suppress {duplicate} */
            F.prototype.bar = function() { return ''; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testDuplicateInstanceMethod6() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            /** jsdoc
             * @return {number} */ F.prototype.bar = function() {
              return 3;
            };
            /** jsdoc
             * @return {string}
             * @suppress {duplicate} */
            F.prototype.bar = function() { return ''; };
            """)
        .addDiagnostic(
            """
            assignment to property bar of F.prototype
            found   : function(this:F): string
            required: function(this:F): number
            """)
        .run();
  }

  @Test
  public void testStubFunctionDeclaration1() {
    testFunctionType(
        """
        /** @constructor */ function f() {};
        /** @param {number} x
         * @param {string} y
         * @return {number} */ f.prototype.foo;
        """,
        "(new f).foo",
        "function(this:f, number, string): number");
  }

  @Test
  public void testStubFunctionDeclaration2() {
    testExternFunctionType(
        // externs
        """
        /** @constructor */ function f() {};
        /** @constructor
         * @extends {f} */ f.subclass;
        """,
        "f.subclass",
        "function(new:f.subclass): ?");
  }

  @Test
  public void testStubFunctionDeclaration_static_onEs5Class() {
    testFunctionType(
        """
        /** @constructor */ function f() {};
        /** @return {undefined} */ f.foo;
        """,
        "f.foo",
        "function(): undefined");
  }

  @Test
  public void testStubFunctionDeclaration4() {
    testFunctionType(
        """
        /** @constructor */ function f() {
          /** @return {number} */ this.foo;
        }
        """,
        "(new f).foo",
        "function(this:f): number");
  }

  @Test
  public void testStubFunctionDeclaration5() {
    testFunctionType(
        """
        /** @constructor */ function f() {
          /** @type {Function} */ this.foo;
        }
        """,
        "(new f).foo",
        createNullableType(getNativeFunctionType()).toString());
  }

  @Test
  public void testStubFunctionDeclaration6() {
    testFunctionType(
        """
        /** @constructor */ function f() {}
        /** @type {Function} */ f.prototype.foo;
        """,
        "(new f).foo",
        createNullableType(getNativeFunctionType()).toString());
  }

  @Test
  public void testStubFunctionDeclaration7() {
    testFunctionType(
        """
        /** @constructor */ function f() {}
        /** @type {Function} */ f.prototype.foo = function() {};
        """,
        "(new f).foo",
        createNullableType(getNativeFunctionType()).toString());
  }

  @Test
  public void testStubFunctionDeclaration8() {
    testFunctionType(
        "/** @type {Function} */ var f = function() {}; ",
        "f",
        createNullableType(getNativeFunctionType()).toString());
  }

  @Test
  public void testStubFunctionDeclaration9() {
    testFunctionType("/** @type {function():number} */ var f; ", "f", "function(): number");
  }

  @Test
  public void testStubFunctionDeclaration10() {
    testFunctionType(
        "/** @type {function(number):number} */ var f = function(x) {};",
        "f",
        "function(number): number");
  }

  @Test
  public void testStubMethodDeclarationDoesntBlockTypechecking_1() {
    newTest()
        .addSource(
            """
            /** @interface */
            function Foo() {}
            /** @return {number} */
            Foo.prototype.method = function() {};
            /**
             * @constructor
             * @implements {Foo}
             */
            function Bar() {}
            Bar.prototype.method;
            var /** null */ n = (new Bar).method();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: null
            """)
        .run();
  }

  @Test
  public void testStubMethodDeclarationDoesntBlockTypechecking_2() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {}
            /** @return {number} */
            Foo.prototype.method = function() {};
            /**
             * @constructor
             * @extends {Foo}
             */
            function Bar() {}
            Bar.prototype.method;
            var /** null */ n = (new Bar).method();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: null
            """)
        .run();
  }

  @Test
  public void testStubMethodDeclarationDoesntBlockTypechecking_3() {
    newTest()
        .addSource(
            """
            /** @interface */
            var Foo = function() {};
            /** @type {number} */
            Foo.prototype.num;
            /**
             * @constructor
             * @implements {Foo}
             */
            var Bar = function() {};
            /** @type {?} */
            Bar.prototype.num;
            var /** string */ x = (new Bar).num;
            """)
        .run();
  }

  @Test
  public void testNestedFunctionInference1() {
    String nestedAssignOfFooAndBar =
        """
        /** @constructor */
        function f() {};
        f.prototype.foo = f.prototype.bar = function(){};
        """;
    testFunctionType(nestedAssignOfFooAndBar, "(new f).bar", "function(this:f): undefined");
  }

  /**
   * Tests the type of a function definition. The function defined by {@code functionDef} should be
   * named {@code "f"}.
   */
  private void testFunctionType(String functionDef, String functionType) {
    testFunctionType(functionDef, "f", functionType);
  }

  /**
   * Tests the type of a function definition. The function defined by {@code functionDef} should be
   * named {@code functionName}.
   */
  private void testFunctionType(String functionDef, String functionName, String functionType) {
    // using the variable initialization check to verify the function's type
    newTest()
        .addSource(functionDef + "/** @type {number} */var a=" + functionName + ";")
        .addDiagnostic(
            "initializing variable\n" //
                + ("found   : " + functionType + "\n")
                + "required: number")
        .run();
  }

  /**
   * Tests the type of a function definition in externs. The function defined by {@code functionDef}
   * should be named {@code functionName}.
   */
  private void testExternFunctionType(
      String functionDef, String functionName, String functionType) {
    newTest()
        .addExterns(functionDef)
        .addSource("/** @type {number} */var a=" + functionName + ";")
        .addDiagnostic(
            "initializing variable\n" //
                + ("found   : " + functionType + "\n")
                + "required: number")
        .run();
  }

  @Test
  public void testTypeRedefinition() {
    testClosureTypesMultipleWarnings(
        """
        a={};/**@enum {string}*/ a.A = {ZOR:'b'};
        /** @constructor */ a.A = function() {}
        """,
        ImmutableList.of(
            // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
            """
            variable a.A redefined with type (typeof a.A), original definition at testcode:1 with type enum{a.A}\
            """,
            """
            assignment to property A of a
            found   : (typeof a.A)
            required: enum{a.A}\
            """));
  }

  @Test
  public void testIn1() {
    newTest().addSource("'foo' in Object").run();
  }

  @Test
  public void testIn2() {
    newTest().addSource("3 in Object").run();
  }

  @Test
  public void testIn3() {
    newTest().addSource("undefined in Object").run();
  }

  @Test
  public void testIn4() {
    newTest()
        .addSource("Date in Object")
        .addDiagnostic(
            """
            left side of 'in'
            found   : function(new:Date, ?=, ?=, ?=, ?=, ?=, ?=, ?=): string
            required: (string|symbol)
            """)
        .run();
  }

  @Test
  public void testIn5() {
    newTest()
        .addSource("'x' in null")
        .addDiagnostic(
            """
            'in' requires an object
            found   : null
            required: Object
            """)
        .run();
  }

  @Test
  public void testIn6() {
    newTest()
        .addSource(
            """
            /** @param {number} x */
            function g(x) {}
            g(1 in {});
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIn7() {
    // Make sure we do inference in the 'in' expression.
    newTest()
        .addSource(
            """
            /**
             * @param {number} x
             * @return {number}
             */
            function g(x) { return 5; }
            function f() {
              var x = {};
              x.foo = '3';
              return g(x.foo) in {};
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testInWithThis_narrowsPropertyType() {
    // TODO(lharker): should we stop doing this narrowing? this code would break under property
    // renaming.
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {}
            Foo.prototype.method = function() {
              if ('x' in this) {
                return this.x; // this access is allowed
              }
              return this.y; // this access causes a warning
            }
            """)
        .addDiagnostic("Property y never defined on Foo")
        .run();
  }

  @Test
  public void testInWithWellKnownSymbol() {
    newTest().addSource("Symbol.iterator in Object").includeDefaultExterns().run();
  }

  @Test
  public void testInWithUniqueSymbol() {
    newTest().addSource("Symbol('foo') in Object").run();
  }

  @Test
  public void testInWithSymbol() {
    newTest().addSource("function f(/** symbol */ s) { return s in {}; }").run();
  }

  @Test
  public void testForIn1() {
    newTest()
        .addSource(
            """
            /** @param {boolean} x */ function f(x) {}
            for (var k in {}) {
              f(k);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : string
            required: boolean
            """)
        .run();
  }

  @Test
  public void testForIn2() {
    newTest()
        .addSource(
            """
            /** @param {boolean} x */ function f(x) {}
            /** @enum {string} */ var E = {FOO: 'bar'};
            /** @type {Object<E, string>} */ var obj = {};
            var k = null;
            for (k in obj) {
              f(k);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : E<string>
            required: boolean
            """)
        .run();
  }

  @Test
  public void testForIn3() {
    newTest()
        .addSource(
            """
            /** @param {boolean} x */ function f(x) {}
            /** @type {Object<number>} */ var obj = {};
            for (var k in obj) {
              f(obj[k]);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : number
            required: boolean
            """)
        .run();
  }

  @Test
  public void testForIn4() {
    newTest()
        .addSource(
            """
            /** @param {boolean} x */ function f(x) {}
            /** @enum {string} */ var E = {FOO: 'bar'};
            /** @type {Object<E, Array>} */ var obj = {};
            for (var k in obj) {
              f(obj[k]);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : (Array|null)
            required: boolean
            """)
        .run();
  }

  @Test
  public void testForIn5() {
    newTest()
        .addExterns(new TestExternsBuilder().addObject().build())
        .addSource(
            """
            /** @param {boolean} x */ function f(x) {}
            /** @constructor */ var E = function(){};
            /** @override */ E.prototype.toString = function() { return ''; };
            /** @type {Object<!E, number>} */ var obj = {};
            for (var k in obj) {
              f(k);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : string
            required: boolean
            """)
        .run();
  }

  @Test
  public void testForOf1() {
    newTest()
        .addSource(
            """
            /** @type {!Iterable<number>} */
            var it = [1, 2, 3];
            for (let x of it) {
              alert(x);
            }
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf2() {
    newTest()
        .addSource(
            """
            /** @param {boolean} x */ function f(x) {}
            /** @type {!Iterable<number>} */ var it = [1, 2, 3];
            for (let x of it) { f(x); }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : number
            required: boolean
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf3() {
    newTest()
        .addSource(
            """
            /** @type {?Iterable<number>} */ var it = [1, 2, 3];
            for (let x of it) {}
            """)
        .addDiagnostic(
            """
            Can only iterate over a (non-null) Iterable type
            found   : (Iterable<number,?,?>|null)
            required: Iterable
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf4() {
    newTest()
        .addSource(
            """
            function f(/** !Iterator<number> */ it) {
              for (let x of it) {}
            }
            """)
        .addDiagnostic(
            """
            Can only iterate over a (non-null) Iterable type
            found   : Iterator<number,?,?>
            required: Iterable
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf5() {
    // 'string' is an Iterable<string> so it can be used in a for/of.
    newTest()
        .addSource(
            """
            function f(/** string */ ch, /** string */ str) {
              for (ch of str) {}
            }
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf6() {
    newTest()
        .addSource(
            """
            function f(/** !Array<number> */ a) {
              for (let elem of a) {}
            }
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf7() {
    newTest().addSource("for (let elem of ['array', 'literal']) {}").includeDefaultExterns().run();
  }

  // TODO(tbreisacher): This should produce a warning: Expected 'null' but got 'string|number'
  @Test
  public void testForOf8() {
    // Union of different types of Iterables.
    newTest()
        .addSource(
            """
            function f(/** null */ x) {}
            (function(/** !Array<string>|!Iterable<number> */ it) {
              for (let elem of it) {
                f(elem);
              }
            })(['']);
            """)
        .includeDefaultExterns()
        .addDiagnostic(
            DiagnosticType.error(
                "JSC_TYPE_MISMATCH",
                """
                actual parameter 1 of f does not match formal parameter
                found   : (number|string)
                required: null at testcode0 line 4 : 6
                """))
        .run();
  }

  @Test
  public void testUnionIndexAccessStringElem() {
    newTest()
        .addSource(
            """
            /** @const {!ReadonlyArray<string>|!Array<string>} */ const a = [];
            /** @const {!null} */ const b = a[0];
            """)
        .includeDefaultExterns()
        .addDiagnostic(
            DiagnosticType.error(
                "JSC_TYPE_MISMATCH",
                """
                initializing variable
                found   : string
                required: None at testcode0 line 1 : 114
                """))
        .run();
  }

  @Test
  public void testUnionIndexAccessOverlappingElem() {
    newTest()
        .addSource(
            """
            /** @const {!ReadonlyArray<string|undefined>|!Array<string>} */ const a = [];
            /** @const {!undefined} */ const b = a[0];
            """)
        .includeDefaultExterns()
        .addDiagnostic(
            DiagnosticType.error(
                "JSC_TYPE_MISMATCH",
                """
                initializing variable
                found   : (string|undefined)
                required: None at testcode0 line 1 : 114
                """))
        .run();
  }

  @Test
  public void testUnionIndexAccessDisjointElem() {
    newTest()
        .addSource(
            """
            /** @const {!ReadonlyArray<string|number>|!Array<boolean>} */ const a = [];
            /** @const {!undefined} */ const b = a[0];
            """)
        .includeDefaultExterns()
        .addDiagnostic(
            DiagnosticType.error(
                "JSC_TYPE_MISMATCH",
                """
                initializing variable
                found   : (boolean|number|string)
                required: None at testcode0 line 1 : 114
                """))
        .run();
  }

  @Test
  public void testReadonlyArrayUnionIndexAccessStringElem() {
    newTest()
        .addSource(
            """
            /** @const {!ReadonlyArray<string>|!ReadonlyArray<string>} */ const a = [];
            /** @const {!null} */ const b = a[0];
            """)
        .includeDefaultExterns()
        .addDiagnostic(
            DiagnosticType.error(
                "JSC_TYPE_MISMATCH",
                """
                initializing variable
                found   : string
                required: None at testcode0 line 1 : 114
                """))
        .run();
  }

  @Test
  public void testReadonlyArrayUnionIndexAccessOverlappingElem() {
    newTest()
        .addSource(
            """
            /** @const {!ReadonlyArray<string|undefined>|!ReadonlyArray<string>} */ const a = [];
            /** @const {!undefined} */ const b = a[0];
            """)
        .includeDefaultExterns()
        .addDiagnostic(
            DiagnosticType.error(
                "JSC_TYPE_MISMATCH",
                """
                initializing variable
                found   : (string|undefined)
                required: None at testcode0 line 1 : 114
                """))
        .run();
  }

  @Test
  public void testReadonlyArrayUnionIndexAccessDisjointElem() {
    newTest()
        .addSource(
            """
            /** @const {!ReadonlyArray<string|number>|!ReadonlyArray<boolean>} */ const a = [];
            /** @const {!undefined} */ const b = a[0];
            """)
        .includeDefaultExterns()
        .addDiagnostic(
            DiagnosticType.error(
                "JSC_TYPE_MISMATCH",
                """
                initializing variable
                found   : (boolean|number|string)
                required: None at testcode0 line 1 : 114
                """))
        .run();
  }

  @Test
  public void testUnionIndexAccessIncompatibleBecomesUnknown() {
    newTest()
        .addSource(
            """
            /** @const {!ReadonlyArray<string>|number} */ const a = [];
            /** @const {!undefined} */ const b = a[0];
            """)
        .includeDefaultExterns()
        .run();
  }

  // TODO(nicksantos): change this to something that makes sense.
  @Test
  @Ignore
  public void testComparison1() {
    newTest()
        .addSource(
            """
            /**@type null */var a;
            /**@type !Date */var b;
            if (a==b) {}
            """)
        .addDiagnostic(
            """
            condition always evaluates to false
            left : null
            right: Date
            """)
        .run();
  }

  @Test
  public void testComparison2() {
    newTest()
        .addSource(
            """
            /**@type {number}*/var a;
            /**@type {!Date} */var b;
            if (a!==b) {}
            """)
        .addDiagnostic(
            """
            condition always evaluates to true
            left : number
            right: Date
            """)
        .run();
  }

  @Test
  public void testComparison3() {
    // Since null == undefined in JavaScript, this code is reasonable.
    newTest()
        .addSource(
            """
            /** @type {(Object|undefined)} */var a;
            var b = a == null
            """)
        .run();
  }

  @Test
  public void testComparison4() {
    newTest()
        .addSource(
            """
            /** @type {(!Object|undefined)} */var a;
            /** @type {!Object} */var b;
            var c = a == b
            """)
        .run();
  }

  @Test
  public void testComparison5() {
    newTest()
        .addSource(
            """
            /** @type {null} */var a;
            /** @type {null} */var b;
            a == b
            """)
        .addDiagnostic(
            """
            condition always evaluates to true
            left : null
            right: null
            """)
        .run();
  }

  @Test
  public void testComparison6() {
    newTest()
        .addSource(
            """
            /** @type {null} */var a;
            /** @type {null} */var b;
            a != b
            """)
        .addDiagnostic(
            """
            condition always evaluates to false
            left : null
            right: null
            """)
        .run();
  }

  @Test
  public void testComparison7() {
    newTest()
        .addSource(
            """
            var a;
            var b;
            a == b
            """)
        .addDiagnostic(
            """
            condition always evaluates to true
            left : undefined
            right: undefined
            """)
        .run();
  }

  @Test
  public void testComparison8() {
    newTest()
        .addSource(
            """
            /** @type {Array<string>} */ var a = [];
            a[0] == null || a[1] == undefined
            """)
        .run();
  }

  @Test
  public void testComparison9() {
    newTest()
        .addSource(
            """
            /** @type {Array<undefined>} */ var a = [];
            a[0] == null
            """)
        .addDiagnostic(
            """
            condition always evaluates to true
            left : undefined
            right: null
            """)
        .run();
  }

  @Test
  public void testComparison10() {
    newTest()
        .addSource(
            """
            /** @type {Array<undefined>} */ var a = [];
            a[0] === null
            """)
        .run();
  }

  @Test
  public void testComparison11() {
    newTest()
        .addSource("(function(){}) == 'x'")
        .addDiagnostic(
            """
            condition always evaluates to false
            left : function(): undefined
            right: string
            """)
        .run();
  }

  @Test
  public void testComparison12() {
    newTest()
        .addSource("(function(){}) == 3")
        .addDiagnostic(
            """
            condition always evaluates to false
            left : function(): undefined
            right: number
            """)
        .run();
  }

  @Test
  public void testComparison13() {
    newTest()
        .addSource("(function(){}) == false")
        .addDiagnostic(
            """
            condition always evaluates to false
            left : function(): undefined
            right: boolean
            """)
        .run();
  }

  @Test
  public void testComparison14() {
    newTest()
        .addSource(
            """
            /** @type {function((Array|string), Object): number} */
            function f(x, y) { return x === y; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testComparison15() {
    testClosureTypes(
        """
        /** @constructor */ function F() {}
        /**
         * @param {number} x
         * @constructor
         * @extends {F}
         */
        function G(x) {}
        goog.inherits(G, F);
        /**
         * @param {number} x
         * @constructor
         * @extends {G}
         */
        function H(x) {}
        goog.inherits(H, G);
        /** @param {G} x */
        function f(x) { return x.constructor === H; }
        """,
        null);
  }

  @Test
  public void testDeleteOperator1() {
    newTest()
        .addSource(
            """
            var x = {};
            /** @return {string} */ function f() { return delete x['a']; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: string
            """)
        .run();
  }

  @Test
  public void testDeleteOperator2() {
    newTest()
        .addSource(
            """
            var obj = {};
            /**
             * @param {string} x
             * @return {Object} */ function f(x) { return obj; }
            /** @param {?number} x */ function g(x) {
              if (x) { delete f(x)['a']; }
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testEnumStaticMethod1() {
    newTest()
        .addSource(
            """
            /** @enum */ var Foo = {AAA: 1};
            /** @param {number} x */ Foo.method = function(x) {};
            Foo.method(true);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Foo.method does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testEnumStaticMethod2() {
    newTest()
        .addSource(
            """
            /** @enum */ var Foo = {AAA: 1};
            /** @param {number} x */ Foo.method = function(x) {};
            function f() { Foo.method(true); }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Foo.method does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testEnum1() {
    newTest()
        .addSource(
            """
            /**@enum*/var a={BB:1,CC:2};
            /**@type {a}*/var d;d=a.BB;
            """)
        .run();
  }

  @Test
  public void testEnum3() {
    newTest()
        .addSource("/**@enum*/var a={BB:1,BB:2}")
        .addDiagnostic("variable a.BB redefined, original definition at testcode0:1")
        .run();
  }

  @Test
  public void testEnum4() {
    newTest()
        .addSource("/**@enum*/var a={BB:'string'}")
        .addDiagnostic(
            """
            assignment to property BB of enum{a}
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testEnum5() {
    newTest()
        .addSource("/**@enum {?String}*/var a={BB:'string'}")
        .addDiagnostic(
            """
            assignment to property BB of enum{a}
            found   : string
            required: (String|null)
            """)
        .run();
  }

  @Test
  public void testEnum6() {
    newTest()
        .addSource(
            """
            /**@enum*/var a={BB:1,CC:2};
            /**@type {!Array}*/var d;d=a.BB;
            """)
        .addDiagnostic(
            """
            assignment
            found   : a<number>
            required: Array\
            """)
        .run();
  }

  @Test
  public void testEnum7() {
    newTest()
        .addSource(
            """
            /** @enum */var a={AA:1,BB:2,CC:3};
            /** @type {a} */var b=a.D;
            """)
        .addDiagnostic("element D does not exist on this enum")
        .run();
  }

  @Test
  public void testEnum8() {
    testClosureTypesMultipleWarnings(
        "/** @enum */var a=8;",
        ImmutableList.of(
            "enum initializer must be an object literal or an enum",
            """
            initializing variable
            found   : number
            required: enum{a}\
            """));
  }

  @Test
  public void testEnum9() {
    testClosureTypesMultipleWarnings(
        "/** @enum */ goog.a=8;",
        ImmutableList.of(
            """
            assignment to property a of goog
            found   : number
            required: enum{goog.a}\
            """,
            "enum initializer must be an object literal or an enum"));
  }

  @Test
  public void testEnum10() {
    newTest()
        .addSource(
            """
            /** @enum {number} */
            goog.K = { A : 3 };
            """)
        .run();
  }

  @Test
  public void testEnum11() {
    newTest()
        .addSource(
            """
            /** @enum {number} */
            goog.K = { 502 : 3 };
            """)
        .run();
  }

  @Test
  public void testEnum12() {
    newTest()
        .addSource(
            """
            /** @enum {number} */ var a = {};
            /** @enum */ var b = a;
            """)
        .run();
  }

  @Test
  public void testEnum13a() {
    newTest()
        .addSource(
            """
            /** @enum {number} */ var a = {};
            /** @enum {string} */ var b = a;
            """)
        .addDiagnostic(
            """
            incompatible enum element types
            found   : number
            required: string\
            """)
        .run();
  }

  @Test
  public void testEnum13b() {
    newTest()
        .addSource(
            """
            /** @enum {number} */ var a = {};
            /** @const */ var ns = {};
            /** @enum {string} */ ns.b = a;
            """)
        .addDiagnostic(
            """
            incompatible enum element types
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testEnum14() {
    newTest()
        .addSource(
            """
            /** @enum {number} */ var a = {FOO:5};
            /** @enum */ var b = a;
            var c = b.FOO;
            """)
        .run();
  }

  @Test
  public void testEnum15() {
    newTest()
        .addSource(
            """
            /** @enum {number} */ var a = {FOO:5};
            /** @enum */ var b = a;
            var c = b.BAR;
            """)
        .addDiagnostic("element BAR does not exist on this enum")
        .run();
  }

  @Test
  public void testEnum16() {
    newTest()
        .addSource(
            """
            var goog = {};
            /**@enum*/goog .a={BB:1,BB:2}
            """)
        .addDiagnostic("variable goog.a.BB redefined, original definition at testcode0:2")
        .run();
  }

  @Test
  public void testEnum17() {
    newTest()
        .addSource(
            """
            var goog = {};
            /**@enum*/goog.a={BB:'string'}
            """)
        .addDiagnostic(
            """
            assignment to property BB of enum{goog.a}
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testEnum18() {
    newTest()
        .addSource(
            """
            /**@enum*/ var E = {A: 1, B: 2};
            /** @param {!E} x
            @return {number} */
            var f = function(x) { return x; };
            """)
        .run();
  }

  @Test
  public void testEnum19() {
    newTest()
        .addSource(
            """
            /**@enum*/ var E = {A: 1, B: 2};
            /** @param {number} x
            @return {!E} */
            var f = function(x) { return x; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: E<number>
            """)
        .run();
  }

  @Test
  public void testEnum20() {
    newTest().addSource("/**@enum*/ var E = {A: 1, B: 2}; var x = []; x[E.A] = 0;").run();
  }

  @Test
  public void testEnum21() {
    Node n =
        parseAndTypeCheck(
            """
            /** @enum {string} */ var E = {A : 'a', B : 'b'};
            /** @param {!E} x
            @return {!E} */ function f(x) { return x; }
            """);
    Node nodeX = n.getLastChild().getLastChild().getLastChild().getLastChild();
    JSType typeE = nodeX.getJSType();
    assertThat(typeE.isObject()).isFalse();
    assertThat(typeE.isNullable()).isFalse();
  }

  @Test
  public void testEnum22() {
    newTest()
        .addSource(
            """
            /**@enum*/ var E = {A: 1, B: 2};
            /** @param {E} x
            * @return {number} */ function f(x) {return x}
            """)
        .run();
  }

  @Test
  public void testEnum23() {
    newTest()
        .addSource(
            """
            /**@enum*/ var E = {A: 1, B: 2};
            /** @param {E} x
            * @return {string} */ function f(x) {return x}
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : E<number>
            required: string
            """)
        .run();
  }

  @Test
  public void testEnum24() {
    newTest()
        .addSource(
            """
            /**@enum {?Object} */ var E = {A: {}};
            /** @param {E} x
            * @return {!Object} */ function f(x) {return x}
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : E<(Object|null)>
            required: Object
            """)
        .run();
  }

  @Test
  public void testEnum25() {
    newTest()
        .addSource(
            """
            /**@enum {!Object} */ var E = {A: {}};
            /** @param {E} x
            * @return {!Object} */ function f(x) {return x}
            """)
        .run();
  }

  @Test
  public void testEnum26() {
    newTest()
        .addSource(
            """
            var a = {}; /**@enum*/ a.B = {A: 1, B: 2};
            /** @param {a.B} x
            * @return {number} */ function f(x) {return x}
            """)
        .run();
  }

  @Test
  public void testEnum27() {
    // x is unknown
    newTest()
        .addSource(
            """
            /** @enum */ var A = {B: 1, C: 2};
            function f(x) { return A == x; }
            """)
        .run();
  }

  @Test
  public void testEnum28() {
    // x is unknown
    newTest()
        .addSource(
            """
            /** @enum */ var A = {B: 1, C: 2};
            function f(x) { return A.B == x; }
            """)
        .run();
  }

  @Test
  public void testEnum29() {
    newTest()
        .addSource(
            """
            /** @enum */ var A = {B: 1, C: 2};
            /** @return {number} */ function f() { return A; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : enum{A}
            required: number
            """)
        .run();
  }

  @Test
  public void testEnum30() {
    newTest()
        .addSource(
            """
            /** @enum */ var A = {B: 1, C: 2};
            /** @return {number} */ function f() { return A.B; }
            """)
        .run();
  }

  @Test
  public void testEnum31() {
    newTest()
        .addSource(
            """
            /** @enum */ var A = {B: 1, C: 2};
            /** @return {A} */ function f() { return A; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : enum{A}
            required: A<number>
            """)
        .run();
  }

  @Test
  public void testEnum32() {
    newTest()
        .addSource(
            """
            /** @enum */ var A = {B: 1, C: 2};
            /** @return {A} */ function f() { return A.B; }
            """)
        .run();
  }

  @Test
  public void testEnum34() {
    newTest()
        .addSource(
            """
            /** @enum */ var A = {B: 1, C: 2};
            /** @param {number} x */ function f(x) { return x == A.B; }
            """)
        .run();
  }

  @Test
  public void testEnum35() {
    newTest()
        .addSource(
            """
            var a = a || {}; /** @enum */ a.b = {C: 1, D: 2};
            /** @return {a.b} */ function f() { return a.b.C; }
            """)
        .run();
  }

  @Test
  public void testEnum36() {
    newTest()
        .addSource(
            """
            var a = a || {}; /** @enum */ a.b = {C: 1, D: 2};
            /** @return {!a.b} */ function f() { return 1; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: a.b<number>
            """)
        .run();
  }

  @Test
  public void testEnum37() {
    newTest()
        .addSource(
            """
            var goog = goog || {};
            /** @enum {number} */ goog.a = {};
            /** @enum */ var b = goog.a;
            """)
        .run();
  }

  @Test
  public void testEnum38() {
    newTest()
        .addSource(
            """
            /** @enum {MyEnum} */ var MyEnum = {};
            /** @param {MyEnum} x */ function f(x) {}
            """)
        .addDiagnostic("Cycle detected in inheritance chain of type MyEnum")
        .run();
  }

  @Test
  public void testEnum39() {
    newTest()
        .addSource(
            """
            /** @enum {Number} */ var MyEnum = {FOO: new Number(1)};
            /** @param {MyEnum} x
             * @return {number} */
            function f(x) { return x == MyEnum.FOO && MyEnum.FOO == x; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testEnum40() {
    newTest()
        .addSource(
            """
            /** @enum {Number} */ var MyEnum = {FOO: new Number(1)};
            /** @param {number} x
             * @return {number} */
            function f(x) { return x == MyEnum.FOO && MyEnum.FOO == x; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testEnum41() {
    newTest()
        .addSource(
            """
            /** @enum {number} */ var MyEnum = {/** @const */ FOO: 1};
            /** @return {string} */
            function f() { return MyEnum.FOO; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : MyEnum<number>
            required: string
            """)
        .run();
  }

  @Test
  public void testEnum42a() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ function f(x) {}
            /** @enum {Object} */ var MyEnum = {FOO: {a: 1, b: 2}};
            f(MyEnum.FOO.a);
            """)
        .addDiagnostic("Property a never defined on MyEnum<Object>")
        .run();
  }

  @Test
  public void testEnum42b() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ function f(x) {}
            /** @enum {!Object} */ var MyEnum = {FOO: {a: 1, b: 2}};
            f(MyEnum.FOO.a);
            """)
        .addDiagnostic("Property a never defined on MyEnum<Object>")
        .run();
  }

  @Test
  public void testEnum42c() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {number} x */ function f(x) {}
            /** @enum {Object} */ var MyEnum = {FOO: {a: 1, b: 2}};
            f(MyEnum.FOO.a);
            """)
        .run();
  }

  @Test
  public void testEnum43() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ function f(x) {}
            /** @enum {{a:number, b:number}} */ var MyEnum = {FOO: {a: 1, b: 2}};
            f(MyEnum.FOO.a);
            """)
        .run();
  }

  @Test
  public void testEnumDefinedInObjectLiteral() {
    newTest()
        .addSource(
            """
            var ns = {
              /** @enum {number} */
              Enum: {A: 1, B: 2},
            };
            /** @param {!ns.Enum} arg */
            function f(arg) {}
            """)
        .run();
  }

  @Test
  public void testAliasedEnum1() {
    newTest()
        .addSource(
            """
            /** @enum */ var YourEnum = {FOO: 3};
            /** @enum */ var MyEnum = YourEnum;
            /** @param {MyEnum} x */ function f(x) {} f(MyEnum.FOO);
            """)
        .run();
  }

  @Test
  public void testAliasedEnum2() {
    newTest()
        .addSource(
            """
            /** @enum */ var YourEnum = {FOO: 3};
            /** @enum */ var MyEnum = YourEnum;
            /** @param {YourEnum} x */ function f(x) {} f(MyEnum.FOO);
            """)
        .run();
  }

  @Test
  public void testAliasedEnum3() {
    newTest()
        .addSource(
            """
            /** @enum */ var YourEnum = {FOO: 3};
            /** @enum */ var MyEnum = YourEnum;
            /** @param {MyEnum} x */ function f(x) {} f(YourEnum.FOO);
            """)
        .run();
  }

  @Test
  public void testAliasedEnum4() {
    newTest()
        .addSource(
            """
            /** @enum */ var YourEnum = {FOO: 3};
            /** @enum */ var MyEnum = YourEnum;
            /** @param {YourEnum} x */ function f(x) {} f(YourEnum.FOO);
            """)
        .run();
  }

  @Test
  public void testAliasedEnum5() {
    newTest()
        .addSource(
            """
            /** @enum */ var YourEnum = {FOO: 3};
            /** @enum */ var MyEnum = YourEnum;
            /** @param {string} x */ function f(x) {} f(MyEnum.FOO);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : YourEnum<number>
            required: string
            """)
        .run();
  }

  @Test
  public void testAliasedEnum_rhsIsStubDeclaration() {
    newTest()
        .addSource(
            """
            let YourEnum;
            /** @enum */ const MyEnum = YourEnum;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : undefined
            required: enum{MyEnum}
            """)
        .run();
  }

  @Test
  public void testAliasedEnum_rhsIsNonEnum() {
    newTest()
        .addSource(
            """
            let YourEnum = 0;
            /** @enum */ const MyEnum = YourEnum;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: enum{MyEnum}
            """)
        .run();
  }

  @Test
  public void testConstAliasedEnum() {
    newTest()
        .addSource(
            """
            /** @enum */ var YourEnum = {FOO: 3};
            const MyEnum = YourEnum;
            /** @param {MyEnum} x */ function f(x) {} f(MyEnum.FOO);
            """)
        .run();
  }

  @Test
  public void testConstAliasedEnum_constJSDoc() {
    newTest()
        .addSource(
            """
            /** @enum */ var YourEnum = {FOO: 3};
            /** @const */ var MyEnum = YourEnum;
            /** @param {MyEnum} x */ function f(x) {} f(MyEnum.FOO);
            """)
        .run();
  }

  @Test
  public void testConstAliasedEnum_qnameConstJSDoc() {
    newTest()
        .addSource(
            """
            /** @enum */ var YourEnum = {FOO: 3};
            const ns = {};
            /** @const */
            ns.MyEnum = YourEnum;
            /** @param {ns.MyEnum} x */ function f(x) {} f(ns.MyEnum.FOO);
            """)
        .run();
  }

  @Test
  public void testConstAliasedEnum_inObjectLit() {
    newTest()
        .addSource(
            """
            /** @enum */ var YourEnum = {FOO: 3};
            const ns = {/** @const */ MyEnum: YourEnum}
            /** @param {ns.MyEnum} x */ function f(x) {} f(ns.MyEnum.FOO);
            """)
        .run();
  }

  @Test
  public void testConstAliasedEnum_nestedInObjectLit() {
    newTest()
        .addSource(
            """
            /** @enum */ var YourEnum = {FOO: 3};
            const ns = {/** @const */ x: {/** @const */ MyEnum: YourEnum}}
            /** @param {ns.x.MyEnum} x */ function f(x) {} f(ns.x.MyEnum.FOO);
            """)
        .run();
  }

  @Test
  public void testBackwardsEnumUse1() {
    newTest()
        .addSource(
            """
            /** @return {string} */ function f() { return MyEnum.FOO; }
            /** @enum {string} */ var MyEnum = {FOO: 'x'};
            """)
        .run();
  }

  @Test
  public void testBackwardsEnumUse2() {
    newTest()
        .addSource(
            """
            /** @return {number} */ function f() { return MyEnum.FOO; }
            /** @enum {string} */ var MyEnum = {FOO: 'x'};
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : MyEnum<string>
            required: number
            """)
        .run();
  }

  @Test
  public void testBackwardsEnumUse3() {
    newTest()
        .addSource(
            """
            /** @return {string} */ function f() { return MyEnum.FOO; }
            /** @enum {string} */ var YourEnum = {FOO: 'x'};
            /** @enum {string} */ var MyEnum = YourEnum;
            """)
        .run();
  }

  @Test
  public void testBackwardsEnumUse4() {
    newTest()
        .addSource(
            """
            /** @return {number} */ function f() { return MyEnum.FOO; }
            /** @enum {string} */ var YourEnum = {FOO: 'x'};
            /** @enum {string} */ var MyEnum = YourEnum;
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : YourEnum<string>
            required: number
            """)
        .run();
  }

  @Test
  public void testBackwardsEnumUse5() {
    newTest()
        .addSource(
            """
            /** @return {string} */ function f() { return MyEnum.BAR; }
            /** @enum {string} */ var YourEnum = {FOO: 'x'};
            /** @enum {string} */ var MyEnum = YourEnum;
            """)
        .addDiagnostic("element BAR does not exist on this enum")
        .run();
  }

  @Test
  public void testBackwardsTypedefUse2() {
    newTest()
        .addSource(
            """
            /** @this {MyTypedef} */ function f() {}
            /** @typedef {!(Date|Array)} */ var MyTypedef;
            """)
        .run();
  }

  @Test
  public void testBackwardsTypedefUse4() {
    newTest()
        .addSource(
            """
            /** @return {MyTypedef} */ function f() { return null; }
            /** @typedef {string} */ var MyTypedef;
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : null
            required: string
            """)
        .run();
  }

  @Test
  public void testBackwardsTypedefUse6() {
    newTest()
        .addSource(
            """
            /** @return {goog.MyTypedef} */ function f() { return null; }
            var goog = {};
            /** @typedef {string} */ goog.MyTypedef;
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : null
            required: string
            """)
        .run();
  }

  @Test
  public void testBackwardsTypedefUse7() {
    newTest()
        .addSource(
            """
            /** @return {goog.MyTypedef} */ function f() { return null; }
            var goog = {};
            /** @typedef {Object} */ goog.MyTypedef;
            """)
        .run();
  }

  @Test
  public void testBackwardsTypedefUse8() {
    // Technically, this isn't quite right, because the JS runtime
    // will coerce null -> the global object. But we'll punt on that for now.
    newTest()
        .addSource(
            """
            /** @param {!Array} x */ function g(x) {}
            /** @this {goog.MyTypedef} */ function f() { g(this); }
            var goog = {};
            /** @typedef {(Array|null|undefined)} */ goog.MyTypedef;
            """)
        .run();
  }

  @Test
  public void testBackwardsTypedefUse9() {
    newTest()
        .addSource(
            """
            /** @param {!Array} x */ function g(x) {}
            /** @this {goog.MyTypedef} */ function f() { g(this); }
            var goog = {};
            /** @typedef {(RegExp|null|undefined)} */ goog.MyTypedef;
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : RegExp
            required: Array
            """)
        .run();
  }

  @Test
  public void testBackwardsTypedefUse10() {
    newTest()
        .addSource(
            """
            /** @param {goog.MyEnum} x */ function g(x) {}
            var goog = {};
            /** @enum {goog.MyTypedef} */ goog.MyEnum = {FOO: 1};
            /** @typedef {number} */ goog.MyTypedef;
            g(1);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : number
            required: goog.MyEnum<number>
            """)
        .run();
  }

  @Test
  public void testBackwardsConstructor1() {
    newTest()
        .addSource(
            """
            function f() { (new Foo(true)); }
            /**
             * @constructor
             * @param {number} x */
            var Foo = function(x) {};
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Foo does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testBackwardsConstructor2() {
    newTest()
        .addSource(
            """
            function f() { (new Foo(true)); }
            /**
             * @constructor
             * @param {number} x */
            var YourFoo = function(x) {};
            /**
             * @constructor
             * @param {number} x */
            var Foo = YourFoo;
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Foo does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testMinimalConstructorAnnotation() {
    newTest().addSource("/** @constructor */function Foo(){}").run();
  }

  @Test
  public void testGoodExtends1() {
    // A minimal @extends example
    newTest()
        .addSource(
            """
            /** @constructor */function base() {}
            /** @constructor
             * @extends {base} */function derived() {}
            """)
        .run();
  }

  @Test
  public void testGoodExtends2() {
    newTest()
        .addSource(
            """
            /** @constructor
             * @extends base */function derived() {}
            /** @constructor */function base() {}
            """)
        .run();
  }

  @Test
  public void testGoodExtends3() {
    newTest()
        .addSource(
            """
            /** @constructor
             * @extends {Object} */function base() {}
            /** @constructor
             * @extends {base} */function derived() {}
            """)
        .run();
  }

  @Test
  public void testGoodExtends4() {
    // Ensure that @extends actually sets the base type of a constructor
    // correctly. Because this isn't part of the human-readable Function
    // definition, we need to crawl the prototype chain (eww).
    Node n =
        parseAndTypeCheck(
            """
            var goog = {};
            /** @constructor */goog.Base = function(){};
            /** @constructor
              * @extends {goog.Base} */goog.Derived = function(){};
            """);
    Node subTypeName = n.getLastChild().getLastChild().getFirstChild();
    assertThat(subTypeName.getQualifiedName()).isEqualTo("goog.Derived");

    FunctionType subCtorType = (FunctionType) subTypeName.getNext().getJSType();
    assertThat(subCtorType.getInstanceType().toString()).isEqualTo("goog.Derived");

    JSType superType = subCtorType.getPrototype().getImplicitPrototype();
    assertThat(superType.toString()).isEqualTo("goog.Base");
  }

  @Test
  public void testGoodExtends5() {
    // we allow for the extends annotation to be placed first
    newTest()
        .addSource(
            """
            /** @constructor */function base() {}
            /** @extends {base}
             * @constructor */function derived() {}
            """)
        .run();
  }

  @Test
  public void testGoodExtends6() {
    testFunctionType(
        CLOSURE_DEFS
            + """
            /** @constructor */function base() {}
            /** @return {number} */
              base.prototype.foo = function() { return 1; };
            /** @extends {base}
             * @constructor */function derived() {}
            goog.inherits(derived, base);
            """,
        "derived.superClass_.foo",
        "function(this:base): number");
  }

  @Test
  public void testGoodExtends7() {
    newTest()
        .addSource(
            """
            /** @constructor
             * @extends {Base} */ function Sub() {}
            /** @return {number} */ function f() { return (new Sub()).foo; }
            /** @constructor */ function Base() {}
            /** @type {boolean} */ Base.prototype.foo = true;
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testGoodExtends8() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Super() {}
            Super.prototype.foo = function() {};
            /** @constructor
             * @extends {Super} */ function Sub() {}
            Sub.prototype = new Super();
            /** @override */ Sub.prototype.foo = function() {};
            """)
        .run();
  }

  @Test
  public void testGoodExtends9() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Super() {}
            /** @constructor
             * @extends {Super} */ function Sub() {}
            Sub.prototype = new Super();
            /** @return {Super} */ function foo() { return new Sub(); }
            """)
        .run();
  }

  @Test
  public void testGoodExtends10() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Super() {}
            /** @param {boolean} x */ Super.prototype.foo = function(x) {};
            /** @constructor
             * @extends {Super} */ function Sub() {}
            Sub.prototype = new Super();
            (new Sub()).foo(0);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Super.prototype.foo does not match formal parameter
            found   : number
            required: boolean
            """)
        .run();
  }

  @Test
  public void testGoodExtends11() {
    newTest()
        .addSource(
            """
            /** @constructor
             * @extends {Super} */ function Sub() {}
            /** @constructor
             * @extends {Sub} */ function Sub2() {}
            /** @constructor */ function Super() {}
            /** @param {Super} x */ function foo(x) {}
            foo(new Sub2());
            """)
        .run();
  }

  @Test
  public void testGoodExtends12() {
    newTest()
        .addSource(
            """
            /** @constructor
             * @extends {B}  */ function C() {}
            /** @constructor
             * @extends {D}  */ function E() {}
            /** @constructor
             * @extends {C}  */ function D() {}
            /** @constructor
             * @extends {A} */ function B() {}
            /** @constructor */ function A() {}
            /** @param {number} x */ function f(x) {} f(new E());
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : E
            required: number
            """)
        .run();
  }

  @Test
  public void testGoodExtends13() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + """
                /** @param {Function} f */ function g(f) {
                  /** @constructor */ function NewType() {};
                  goog.inherits(NewType, f);
                  (new NewType());
                }
                """)
        .run();
  }

  @Test
  public void testGoodExtends14() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + """
                /** @constructor */ function OldType() {}
                /** @param {?function(new:OldType)} f */ function g(f) {
                  /**
                    * @constructor
                    * @extends {OldType}
                    */
                  function NewType() {};
                  goog.inherits(NewType, f);
                  NewType.prototype.method = function() {
                    NewType.superClass_.foo.call(this);
                  };
                }
                """)
        .addDiagnostic("Property foo never defined on OldType.prototype")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGoodExtends_withAliasOfSuperclass() {
    newTest()
        .includeDefaultExterns()
        .addSource(
            CLOSURE_DEFS
                + """
                /** @constructor */ const OldType = function () {};
                const OldTypeAlias = OldType;

                /**
                  * @constructor
                  * @extends {OldTypeAlias}
                  */
                function NewType() {};
                // Verify that we recognize the inheritance even when goog.inherits references
                // OldTypeAlias, not OldType.
                goog.inherits(NewType, OldTypeAlias);
                NewType.prototype.method = function() {
                  NewType.superClass_.foo.call(this);
                };
                """)
        .addDiagnostic("Property foo never defined on OldType.prototype")
        .run();
  }

  @Test
  public void testBadExtends_withAliasOfSuperclass() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + """
                const ns = {};
                /** @constructor */ ns.OldType = function () {};
                const nsAlias = ns;

                /**
                  * @constructor
                  * // no @extends here, NewType should not have a goog.inherits
                  */
                function NewType() {};
                // Verify that we recognize the inheritance even when goog.inherits references
                // nsAlias.OldType, not ns.OldType.
                goog.inherits(NewType, ns.OldType);
                """)
        .addDiagnostic("Missing @extends tag on type NewType")
        .run();
  }

  @Test
  public void testBadExtends_withNamespacedAliasOfSuperclass() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + """
                const ns = {};
                /** @constructor */ ns.OldType = function () {};
                const nsAlias = ns;

                /**
                  * @constructor
                  * // no @extends here, NewType should not have a goog.inhertis
                  */
                function NewType() {};
                // Verify that we recognize the inheritance even when goog.inherits references
                // nsAlias.OldType, not ns.OldType.
                goog.inherits(NewType, nsAlias.OldType);
                """)
        .addDiagnostic("Missing @extends tag on type NewType")
        .run();
  }

  @Test
  public void testBadExtends_withUnionType() {
    // Regression test for b/146562659, crash when extending a union type.
    newTest()
        .addSource(
            """
            /** @interface */
            class Foo {}
            /** @interface */
            class Bar {}
            /** @typedef {!Foo|!Bar} */
            let Baz;
            /**
             * @interface
             * @extends {Baz}
             */
            class Blah {}
            """)
        .addDiagnostic("Blah @extends non-object type (Bar|Foo)")
        .run();
  }

  @Test
  public void testGoodExtends_withNamespacedAliasOfSuperclass() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + """
                const ns = {};
                /** @constructor */ ns.OldType = function () {};
                const nsAlias = ns;

                /**
                  * @constructor
                  * @extends {nsAlias.OldType}
                  */
                function NewType() {};
                // Verify that we recognize the inheritance even when goog.inherits references
                // nsAlias.OldType, not ns.OldType.
                goog.inherits(NewType, nsAlias.OldType);
                NewType.prototype.method = function() {
                  NewType.superClass_.foo.call(this);
                };
                """)
        .includeDefaultExterns()
        .addDiagnostic("Property foo never defined on ns.OldType.prototype")
        .run();
  }

  @Test
  public void testGoodExtends15() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + """
                /** @param {Function} f */ function g(f) {
                  /** @constructor */ function NewType() {};
                  goog.inherits(f, NewType);
                  (new NewType());
                }
                """)
        .run();
  }

  @Test
  public void testGoodExtends16() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + """
                /** @constructor
                 * @template T */
                function C() {}
                /** @constructor
                 * @extends {C<string>} */
                function D() {};
                goog.inherits(D, C);
                (new D())
                """)
        .run();
  }

  @Test
  public void testGoodExtends17() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + """
                /** @constructor */
                function C() {}

                /** @interface
                 * @template T */
                function D() {}
                /** @param {T} t */
                D.prototype.method;

                /** @constructor
                 * @template T
                 * @extends {C}
                 * @implements {D<T>} */
                function E() {};
                goog.inherits(E, C);
                /** @override */
                E.prototype.method = function(t) {};

                var e = /** @type {E<string>} */ (new E());
                e.method(3);
                """)
        .addDiagnostic(
            """
            actual parameter 1 of E.prototype.method does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testGoodExtends18() {
    newTest()
        .addSource(
            """
            /** @interface */
            var MyInterface = function() {};
            MyInterface.prototype = {
              /** @return {number} */
              method: function() {}
            }
            /** @extends {MyInterface}
             * @interface */
            var MyOtherInterface = function() {};
            MyOtherInterface.prototype = {
              /** @return {number}
               * @override */
              method: function() {}
            }
            """)
        .run();
  }

  @Test
  public void testGoodExtends19() {
    newTest()
        .addSource(
            """
            /** @constructor */
            var MyType = function() {};
            MyType.prototype = {
              /** @return {number} */
              method: function() {}
            }
            /** @constructor
             * @extends {MyType}
             */
            var MyOtherType = function() {};
            MyOtherType.prototype = {
              /** @return {number}
               * @override */
              method: function() {}
            }
            """)
        .run();
  }

  @Test
  public void testBadExtends1() {
    newTest()
        .addSource(
            """
            /** @constructor */function base() {}
            /** @constructor
             * @extends {not_base} */function derived() {}
            """)
        .addDiagnostic("Bad type annotation. Unknown type not_base")
        .run();
  }

  @Test
  public void testBadExtends2() {
    newTest()
        .addSource(
            """
            /** @constructor */function base() {
            /** @type {!Number}*/
            this.baseMember = new Number(4);
            }
            /** @constructor
              * @extends {base} */function derived() {}
            /** @param {!String} x*/
            function foo(x){ }
            /** @type {!derived}*/var y;
            foo(y.baseMember);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of foo does not match formal parameter
            found   : Number
            required: String
            """)
        .run();
  }

  @Test
  public void testBadExtends3() {
    newTest()
        .addSource("/** @extends {Object} */function base() {}")
        .addDiagnostic("@extends used without @constructor or @interface for base")
        .run();
  }

  @Test
  public void testBadExtends4() {
    // If there's a subclass of a class with a bad extends,
    // we only want to warn about the first one.
    newTest()
        .addSource(
            """
            /** @constructor
             * @extends {bad} */ function Sub() {}
            /** @constructor
             * @extends {Sub} */ function Sub2() {}
            /** @param {Sub} x */ function foo(x) {}
            foo(new Sub2());
            """)
        .addDiagnostic("Bad type annotation. Unknown type bad")
        .run();
  }

  @Test
  public void testBadExtends5() {
    newTest()
        .addSource(
            """
            /** @interface */
            var MyInterface = function() {};
            MyInterface.prototype = {
              /** @return {number} */
              method: function() {}
            }
            /** @extends {MyInterface}
             * @interface */
            var MyOtherInterface = function() {};
            MyOtherInterface.prototype = {
              /** @return {string}
             @override */
              method: function() {}
            }
            """)
        .addDiagnostic(
"""
mismatch of the method property on type MyOtherInterface and the type of the property it overrides from interface MyInterface
original: function(this:MyInterface): number
override: function(this:MyOtherInterface): string
""")
        .run();
  }

  @Test
  public void testBadExtends6() {
    newTest()
        .addSource(
            """
            /** @constructor */
            var MyType = function() {};
            MyType.prototype = {
              /** @return {number} */
              method: function() {}
            }
            /** @constructor
             * @extends {MyType}
             */
            var MyOtherType = function() {};
            MyOtherType.prototype = {
              /** @return {string}
               * @override */
              method: function() { return ''; }
            }
            """)
        .addDiagnostic(
"""
mismatch of the method property type and the type of the property it overrides from superclass MyType
original: function(this:MyType): number
override: function(this:MyOtherType): string
""")
        .run();
  }

  @Test
  public void testLateExtends() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + """
                /** @constructor */ function Foo() {}
                Foo.prototype.foo = function() {};
                /** @constructor */function Bar() {}
                goog.inherits(Foo, Bar);
                """)
        .addDiagnostic("Missing @extends tag on type Foo")
        .run();
  }

  @Test
  public void testSuperclassMatch() {
    compiler.getOptions().setCodingConvention(new GoogleCodingConvention());
    newTest()
        .addSource(
            """
            /** @constructor */ var Foo = function() {};
            /** @constructor
             * @extends Foo */ var Bar = function() {};
            Bar.inherits = function(x){};
            Bar.inherits(Foo);
            """)
        .run();
  }

  @Test
  public void testSuperclassMatchWithMixin() {
    compiler.getOptions().setCodingConvention(new GoogleCodingConvention());
    newTest()
        .addSource(
            """
            /** @constructor */ var Foo = function() {};
            /** @constructor */ var Baz = function() {};
            /** @constructor
             * @extends Foo */ var Bar = function() {};
            Bar.inherits = function(x){};
            Bar.mixin = function(y){};
            Bar.inherits(Foo);
            Bar.mixin(Baz);
            """)
        .run();
  }

  @Test
  public void testSuperClassDefinedAfterSubClass1() {
    newTest()
        .addSource(
            """
            /** @constructor
             * @extends {Base} */ function A() {}
            /** @constructor
             * @extends {Base} */ function B() {}
            /** @constructor */ function Base() {}
            /** @param {A|B} x
             * @return {B|A} */
            function foo(x) { return x; }
            """)
        .run();
  }

  @Test
  public void testSuperClassDefinedAfterSubClass2() {
    newTest()
        .addSource(
            """
            /** @constructor
             * @extends {Base} */ function A() {}
            /** @constructor
             * @extends {Base} */ function B() {}
            /** @param {A|B} x
             * @return {B|A} */
            function foo(x) { return x; }
            /** @constructor */ function Base() {}
            """)
        .run();
  }

  @Test
  public void testGoodSuperCall() {
    newTest()
        .addSource(
            """
            class A {
              /**
               * @param {string} a
               */
              constructor(a) {
                this.a = a;
              }
            }
            class B extends A {
              constructor() {
                super('b');
              }
            }
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testBadSuperCall() {
    newTest()
        .addSource(
            """
            class A {
              /**
               * @param {string} a
               */
              constructor(a) {
                this.a = a;
              }
            }
            class B extends A {
              constructor() {
                super(5);
              }
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of super does not match formal parameter
            found   : number
            required: string
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testDirectPrototypeAssignment1() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Base() {}
            Base.prototype.foo = 3;
            /** @constructor
             * @extends {Base} */ function A() {}
            A.prototype = new Base();
            /** @return {string} */ function foo() { return (new A).foo; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testDirectPrototypeAssignment2() {
    // This ensures that we don't attach property 'foo' onto the Base
    // instance object.
    newTest()
        .addSource(
            """
            /** @constructor */ function Base() {}
            /** @constructor
             * @extends {Base} */ function A() {}
            A.prototype = new Base();
            A.prototype.foo = 3;
            /** @return {string} */ function foo() { return (new Base).foo; }
            """)
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run(); // exists on subtypes, so only reported for strict props
  }

  @Test
  public void testDirectPrototypeAssignment3() {
    // This verifies that the compiler doesn't crash if the user
    // overwrites the prototype of a global variable in a local scope.
    newTest()
        .addSource(
            """
            /** @constructor */ var MainWidgetCreator = function() {};
            /** @param {Function} ctor */
            function createMainWidget(ctor) {
              /** @constructor */ function tempCtor() {};
              tempCtor.prototype = ctor.prototype;
              MainWidgetCreator.superClass_ = ctor.prototype;
              MainWidgetCreator.prototype = new tempCtor();
            }
            """)
        .run();
  }

  @Test
  public void testGoodImplements1() {
    newTest()
        .addSource(
            """
            /** @interface */function Disposable() {}
            /** @implements {Disposable}
             * @constructor */function f() {}
            """)
        .run();
  }

  @Test
  public void testGoodImplements2() {
    newTest()
        .addSource(
            """
            /** @interface */function Base1() {}
            /** @interface */function Base2() {}
            /** @constructor
             * @implements {Base1}
             * @implements {Base2}
             */ function derived() {}
            """)
        .run();
  }

  @Test
  public void testGoodImplements3() {
    newTest()
        .addSource(
            """
            /** @interface */function Disposable() {}
            /** @constructor
             * @implements {Disposable} */function f() {}
            """)
        .run();
  }

  @Test
  public void testGoodImplements4() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @type {!Function} */
            goog.abstractMethod = function() {};
            /** @interface */
            goog.Disposable = function() {};
            goog.Disposable.prototype.dispose = goog.abstractMethod;
            /** @implements {goog.Disposable}
             * @constructor */
            goog.SubDisposable = function() {};
            /** @inheritDoc */
            goog.SubDisposable.prototype.dispose = function() {};
            """)
        .run();
  }

  @Test
  public void testGoodImplements5() {
    newTest()
        .addSource(
            """
            /** @interface */
            goog.Disposable = function() {};
            /** @type {Function} */
            goog.Disposable.prototype.dispose = function() {};
            /** @implements {goog.Disposable}
             * @constructor */
            goog.SubDisposable = function() {};
            /** @param {number} key
             * @override */
            goog.SubDisposable.prototype.dispose = function(key) {};
            """)
        .run();
  }

  @Test
  public void testGoodImplements6() {
    newTest()
        .addSource(
            """
            var myNullFunction = function() {};
            /** @interface */
            goog.Disposable = function() {};
            /** @return {number} */
            goog.Disposable.prototype.dispose = myNullFunction;
            /** @implements {goog.Disposable}
             * @constructor */
            goog.SubDisposable = function() {};
            /** @return {number}
             * @override */
            goog.SubDisposable.prototype.dispose = function() { return 0; };
            """)
        .run();
  }

  @Test
  public void testGoodImplements7() {
    newTest()
        .addSource(
            """
            var myNullFunction = function() {};
            /** @interface */
            goog.Disposable = function() {};
            /** @return {number} */
            goog.Disposable.prototype.dispose = function() {};
            /** @implements {goog.Disposable}
             * @constructor */
            goog.SubDisposable = function() {};
            /** @return {number}
             * @override */
            goog.SubDisposable.prototype.dispose = function() { return 0; };
            """)
        .run();
  }

  @Test
  public void testGoodImplements8() {
    newTest()
        .addSource(
            """
            /** @interface */
            MyInterface = function() {};
            MyInterface.prototype = {
              /** @return {number} */
              method: function() {}
            }
            /** @implements {MyInterface}
             * @constructor */
            MyClass = function() {};
            MyClass.prototype = {
              /** @return {number}
               * @override */
              method: function() { return 0; }
            }
            """)
        .run();
  }

  @Test
  public void testBadImplements1() {
    newTest()
        .addSource(
            """
            /** @interface */function Base1() {}
            /** @interface */function Base2() {}
            /** @constructor
             * @implements {nonExistent}
             * @implements {Base2}
             */ function derived() {}
            """)
        .addDiagnostic("Bad type annotation. Unknown type nonExistent")
        .run();
  }

  @Test
  public void testBadImplements2() {
    newTest()
        .addSource(
            """
            /** @interface */function Disposable() {}
            /** @implements {Disposable}
             */function f() {}
            """)
        .addDiagnostic("@implements used without @constructor for f")
        .run();
  }

  @Test
  public void testBadImplements3() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @type {!Function} */ goog.abstractMethod = function(){};
            /** @interface */ var Disposable = function() {};
            Disposable.prototype.method = goog.abstractMethod;
            /** @implements {Disposable}
             * @constructor */function f() {}
            """)
        .addDiagnostic("property method on interface Disposable is not implemented by type f")
        .run();
  }

  @Test
  public void testBadImplements4() {
    newTest()
        .addSource(
            """
            /** @interface */function Disposable() {}
            /** @implements {Disposable}
             * @interface */function f() {}
            """)
        .addDiagnostic(
            """
            f cannot implement this type; an interface can only extend, but not implement interfaces
            """)
        .run();
  }

  @Test
  public void testBadImplements5() {
    newTest()
        .addSource(
            """
            /** @interface */function Disposable() {}
            /** @type {number} */ Disposable.prototype.bar = function() {};
            """)
        .addDiagnostic(
            """
            assignment to property bar of Disposable.prototype
            found   : function(): undefined
            required: number
            """)
        .run();
  }

  @Test
  public void testBadImplements6() {
    testClosureTypesMultipleWarnings(
        """
        /** @interface */function Disposable() {}
        /** @type {function()} */ Disposable.prototype.bar = 3;
        """,
        ImmutableList.of(
            """
            assignment to property bar of Disposable.prototype
            found   : number
            required: function(): ?\
            """,
            """
            interface members can only be empty property declarations, empty functions, or goog.abstractMethod\
            """));
  }

  @Test
  public void testBadImplements7() {
    newTest()
        .addSource(
            """
            /** @interface */
            MyInterface = function() {};
            MyInterface.prototype = {
              /** @return {number} */
              method: function() {}
            }
            /** @implements {MyInterface}
             * @constructor */
            MyClass = function() {};
            MyClass.prototype = {
              /** @return {string}
               * @override */
              method: function() { return ''; }
            }
            """)
        .addDiagnostic(
"""
mismatch of the method property on type MyClass and the type of the property it overrides from interface MyInterface
original: function(): number
override: function(): string
""")
        .run();
  }

  @Test
  public void testBadImplements8() {
    newTest()
        .addSource(
            """
            /** @interface */
            MyInterface = function() {};
            MyInterface.prototype = {
              /** @return {number} */
              method: function() {}
            }
            /** @implements {MyInterface}
             * @constructor */
            MyClass = function() {};
            MyClass.prototype = {
              /** @return {number} */
              method: function() { return 0; }
            }
            """)
        .addDiagnostic(
            """
            property method already defined on interface MyInterface; use @override to override it
            """)
        .run();
  }

  @Test
  public void testProtoDoesNotRequireOverrideFromInterface() {
    newTest()
        .includeDefaultExterns()
        .addExterns("/** @type {Object} */ Object.prototype.__proto__;")
        .addSource(
            """
            /** @interface */
            var MyInterface = function() {};
            /** @constructor
             @implements {MyInterface} */
            var MySuper = function() {};
            /** @constructor
             @extends {MySuper} */
            var MyClass = function() {};
            MyClass.prototype = {
              __proto__: MySuper.prototype
            }
            """)
        .run();
  }

  @Test
  public void testConstructorClassTemplate() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @template S,T
             */
            function A() {}
            """)
        .run();
  }

  @Test
  public void testGenericBoundExplicitUnknown() {
    newTest()
        .suppress(DiagnosticGroups.BOUNDED_GENERICS)
        .addSource(
            """
            /**
             * @param {T} x
             * @template {?} T
             */
            function f(x) {}
            """)
        .addDiagnostic("Illegal upper bound '?' on template type parameter T")
        .diagnosticsAreErrors()
        .run();
  }

  @Test
  public void testGenericBoundArgAppError() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} x
             * @template {number} T
             */
            function f(x) {}
            var a = f('a');
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testGenericBoundArgApp() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} x
             * @template {number} T
             */
            function f(x) {}
            var a = f(3);
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testGenericBoundReturnError() {
    // NOTE: This signature is unsafe, but it's an effective minimal test case.
    newTest()
        .addSource(
            """
            /**
             * @return {T}
             * @template {number} T
             */
            function f(x) { return 'a'; }
            var a = f(0);
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            inconsistent return type
            found   : string
            required: T extends number
            """)
        .run();
  }

  @Test
  public void testGenericBoundArgAppNullError() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} x
             * @template {number} T
             */
            function f(x) { return x; }
            var a = f(null);
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : null
            required: number
            """)
        .run();
  }

  @Test
  public void testGenericBoundArgAppNullable() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} x
             * @template {?number} T
             */
            function f(x) { return x; }
            var a = f(null);
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testGenericBoundArgInnerCallAppError() {
    newTest()
        .addSource(
            """
            /**
             * @param {string} x
             */
            function stringID(x) { return x; }
            /**
             * @param {T} x
             * @template {number|boolean} T
             */
            function foo(x) { return stringID(x); }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            actual parameter 1 of stringID does not match formal parameter
            found   : T extends (boolean|number)
            required: string
            """)
        .run();
  }

  @Test
  public void testGenericBoundArgInnerCallApp() {
    newTest()
        .addSource(
            """
            /**
             * @param {number} x
             */
            function numID(x) { return x; }
            /**
             * @param {T} x
             * @template {number} T
             */
            function foo(x) { return numID(x); }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testGenericBoundArgInnerCallAppSubtypeError() {
    newTest()
        .addSource(
            """
            /**
             * @param {number} x
             */
            function numID(x) { return x; }
            /**
             * @param {T} x
             * @template {number|boolean|string} T
             */
            function foo(x) { return numID(x); }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            actual parameter 1 of numID does not match formal parameter
            found   : T extends (boolean|number|string)
            required: number
            """)
        .run();
  }

  @Test
  public void testGenericBoundArgInnerAssignSubtype() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} x
             * @param {number|string|boolean} y
             * @template {number|string} T
             */
            function foo(x,y) { y=x; }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testGenericBoundArgInnerAssignBoundedInvariantError() {
    newTest()
        .addSource(
            """
            /**
             * @param {number} x
             * @param {T} y
             * @template {number|string} T
             */
            function foo(x,y) { y=x; }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            assignment
            found   : number
            required: T extends (number|string)
            """)
        .run();
  }

  @Test
  public void testGenericBoundArgInnerAssignSubtypeError() {
    newTest()
        .addSource(
            """
            /**
             * @param {number} x
             * @param {T} y
             * @template {number|string} T
             */
            function foo(x,y) { x=y; }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            assignment
            found   : T extends (number|string)
            required: number
            """)
        .run();
  }

  @Test
  public void testDoubleGenericBoundArgInnerAssignSubtype() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} x
             * @param {T} y
             * @template {number|string} T
             */
            function foo(x,y) { x=y; }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testBoundedGenericForwardDeclaredParameterError() {
    newTest()
        .addSource(
            """
            /**
             * @template {number} T
             */
            class Foo {}
            var /** !Foo<Str> */ a;
            /** @typedef {string} */
            let Str;
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            Bounded generic type error. string assigned to template type T is not a subtype of bound number
            """)
        .run();
  }

  @Test
  public void testBoundedGenericParametrizedTypeVarError() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @template {number|boolean} T
             */
            function F() {}
            var /** F<string> */ a;
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            Bounded generic type error. string assigned to template type T is not a subtype of bound (boolean|number)
            """)
        .run();
  }

  @Test
  public void testBoundedGenericParametrizedTypeVar() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @param {T} x
             * @template {number|boolean} T
             */
            function F(x) {}
            var /** F<number> */ a;
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testDoubleGenericBoundArgInnerAssignSubtypeError() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} x
             * @param {U} y
             * @template {number|string} T
             * @template {number|string} U
             */
            function foo(x,y) { x=y; }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            assignment
            found   : U extends (number|string)
            required: T extends (number|string)
            """)
        .run();
  }

  @Test
  public void testGenericBoundBivariantTemplatized() {
    newTest()
        .addSource(
            """
            /**
             * @param {!Array<T>} x
             * @param {!Array<number|boolean>} y
             * @template {number} T
             */
            function foo(x,y) {
              var /** !Array<T> */ z = y;
              y = x;
            }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testConstructGenericBoundGenericBound() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} x
             * @param {U} y
             * @template {boolean|string} T
             * @template {T} U
             */
            function foo(x,y) { x=y; }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testConstructGenericBoundGenericBoundError() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} x
             * @param {U} y
             * @template {boolean|string} T
             * @template {T} U
             */
            function foo(x,y) {
              var /** U */ z = x;
              x = y;
            }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            initializing variable
            found   : T extends (boolean|string)
            required: U extends T extends (boolean|string)
            """)
        .run();
  }

  @Test
  public void testDoubleDeclareTemplateNameInFunctions() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} x
             * @template {boolean} T
             */
            function foo(x) { return x; }
            /**
             * @param {T} x
             * @template {string} T
             */
            function bar(x) { return x; }
            foo(true);
            bar('Hi');
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testDoubleDeclareTemplateNameInFunctionsError() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} x
             * @template {boolean} T
             */
            function foo(x) { return x; }
            /**
             * @param {T} x
             * @template {string} T
             */
            function bar(x) { return x; }
            foo('Hi');
            bar(true);
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            actual parameter 1 of foo does not match formal parameter
            found   : string
            required: boolean
            """)
        .addDiagnostic(
            """
            actual parameter 1 of bar does not match formal parameter
            found   : boolean
            required: string
            """)
        .run();
  }

  @Test
  public void testShadowTemplateNameInFunctionAndClass() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} x
             * @template {boolean} T
             */
            function foo(x) { return x; }
            class Foo {
              /**
               * @param {T} x
               * @template {string} T
               */
              foo(x) { return x; }
            }
            var F = new Foo();
            F.foo('Hi');
            foo(true);
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testShadowTemplateNameInFunctionAndClassError() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} x
             * @template {boolean} T
             */
            function foo(x) { return x; }
            class Foo {
              /**
               * @param {T} x
               * @template {string} T
               */
              foo(x) { return x; }
            }
            var F = new Foo();
            F.foo(true);
            foo('Hi');
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            actual parameter 1 of Foo.prototype.foo does not match formal parameter
            found   : boolean
            required: string
            """)
        .addDiagnostic(
            """
            actual parameter 1 of foo does not match formal parameter
            found   : string
            required: boolean
            """)
        .run();
  }

  @Test
  public void testTemplateTypeBounds_passingBoundedTemplateType_toTemplatedFunction() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() { }

            /**
             * @template {!Foo} X
             * @param {X} x
             * @return {X}
             */
            function clone(x) {
            // The focus of this test is that `X` (already a template variable) is bound to `Y` at
            // this callsite. We confirm that by ensuring an `X` is returned from `cloneInternal`.
              return cloneInternal(x);
            }

            /**
             * @template {!Foo} Y
             * @param {Y} x
             * @return {Y}
             */
            function cloneInternal(x) {
              return x;
            }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testTemplateTypeBounds_onFreeFunctions_areAlwaysSpecialized() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() { }

            /**
             * @template {!Foo} X
             * @param {X} x
             * @return {X}
             */
            function identity(x) {
              return x;
            }

            var /** function(!Foo): !Foo */ y = identity;
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  // TODO(b/139192655): The function template should shadow the class template type and not error
  public void testFunctionTemplateShadowClassTemplate() {
    newTest()
        .addSource(
            """
            /**
             * @template T
             */
            class Foo {
              /**
               * @param {T} x
               * @template T
               */
              foo(x) { return x; }
            }
            const /** !Foo<string> */ f = new Foo();
            f.foo(3);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Foo.prototype.foo does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  // TODO(b/139192655): The function template should shadow the class template type and not error
  public void testFunctionTemplateShadowClassTemplateBounded() {
    newTest()
        .addSource(
            """
            /**
             * @template {string} T
             */
            class Foo {
              /**
               * @param {T} x
               * @template {number} T
               */
              foo(x) { return x; }
            }
            const /** !Foo<string> */ f = new Foo();
            f.foo(3);
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            actual parameter 1 of Foo.prototype.foo does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testCyclicGenericBoundGenericError() {
    newTest()
        .suppress(DiagnosticGroups.BOUNDED_GENERICS)
        .addSource(
            """
            /**
             * @template {S} T
             * @template {T} U
             * @template {U} S
             */
            class Foo { }
            """)
        .addDiagnostic("Cycle detected in inheritance chain of type S")
        .addDiagnostic("Cycle detected in inheritance chain of type T")
        .addDiagnostic("Cycle detected in inheritance chain of type U")
        .run();
  }

  @Test
  public void testUnitCyclicGenericBoundGenericError() {
    newTest()
        .suppress(DiagnosticGroups.BOUNDED_GENERICS)
        .addSource(
            """
            /**
             * @template {T} T
             */
            class Foo { }
            """)
        .addDiagnostic("Cycle detected in inheritance chain of type T")
        .run();
  }

  @Test
  public void testSecondUnitCyclicGenericBoundGenericError() {
    newTest()
        .suppress(DiagnosticGroups.BOUNDED_GENERICS)
        .addSource(
            """
            /**
             * @template {T} T
             * @template {T} U
             */
            class Foo { }
            """)
        .addDiagnostic("Cycle detected in inheritance chain of type T")
        .addDiagnostic("Cycle detected in inheritance chain of type U")
        .run();
  }

  @Test
  public void testConstructCyclicGenericBoundGenericBoundGraph() {
    newTest()
        .addSource(
            """
            /**
             * @template V, E
             */
            class Vertex {}
            /**
             * @template V, E
             */
            class Edge {}
            /**
             * @template {Vertex<V, E>} V
             * @template {Edge<V, E>} E
             */
            class Graph {}
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testBoundedGenericParametrizedTypeReturnError() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @template T
             */
            function C(x) {}
            /**
             * @template {number|string} U
             * @param {C<boolean>} x
             * @return {C<U>}
             */
            function f(x) { return x; }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (C<boolean>|null)
            required: (C<U extends (number|string)>|null)
            """)
        .run();
  }

  @Test
  public void testBoundedGenericParametrizedTypeError() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @param {T} x
             * @template T
             */
            function C(x) {}
            /**
             * @template {number|string} U
             * @param {C<U>} x
             */
            function f(x) {}
            var /** C<boolean> */ c;
            f(c);
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : (C<boolean>|null)
            required: (C<(number|string)>|null)
            """)
        .run();
  }

  @Test
  public void testPartialNarrowingBoundedGenericParametrizedTypeError() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @param {T} x
             * @template T
             */
            function C(x) {}
            /**
             * @template {number|string} U
             * @param {C<U>} x
             * @param {U} y
             */
            function f(x,y) {}
            var /** C<string> */ c;
            f(c,false);
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : (C<string>|null)
            required: (C<(number|string)>|null)
            """)
        .addDiagnostic(
            """
            actual parameter 2 of f does not match formal parameter
            found   : boolean
            required: (number|string)
            """)
        .run();
  }

  @Test
  public void testPartialNarrowingBoundedGenericParametrizedType() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @param {T} x
             * @template T
             */
            function C(x) {}
            /**
             * @template {number|string} U
             * @param {C<U>} x
             * @param {U} y
             */
            function f(x,y) {}
            var /** C<number|string> */ c;
            f(c,0);
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testUnmappedBoundedGenericTypeError() {
    newTest()
        .addSource(
            """
            /**
             * @template {number|string} T
             * @template {number|null} S
             */
            class Foo {
              /**
               * @param {T} x
               */
              bar(x) { }
              /**
               * @param {S} x
               */
              baz(x) { }
            }
            var /** Foo<number> */ foo;
            foo.baz(3);
            foo.baz(null);
            foo.baz('3');
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            actual parameter 1 of Foo.prototype.baz does not match formal parameter
            found   : string
            required: S extends (null|number)
            """)
        .run();
  }

  @Test
  public void testFunctionBodyBoundedGenericError() {
    newTest()
        .addSource(
            """
            class C {}
            /**
             * @template {C} T
             * @param {T} t
             */
            function f(t) { t = new C(); }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            assignment
            found   : C
            required: T extends (C|null)
            """)
        .run();
  }

  @Test
  public void testFunctionBodyBoundedPropertyGenericError() {
    newTest()
        .addSource(
            """
            /**
             * @template {number|string} T
             */
            class Foo {
              constructor() {
                /** @type {T} */
                this.x;
              }
              m() {
              this.x = 0;
              }
            }
            function f(t) { t = new C(); }
            """)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            """
            assignment to property x of Foo
            found   : number
            required: T extends (number|string)
            """)
        .run();
  }

  @Test
  public void testInterfaceExtends() {
    newTest()
        .addSource(
            """
            /** @interface */function A() {}
            /** @interface\s
             * @extends {A} */function B() {}
            /** @constructor
             * @implements {B}
             */ function derived() {}
            """)
        .run();
  }

  @Test
  public void testDontCrashOnDupPropDefinition() {
    newTest()
        .addSource(
            """
            /** @const */
            var ns = {};
            /** @interface */
            ns.I = function() {};
            /** @interface */
            ns.A = function() {};
            /**
             * @constructor
             * @implements {ns.I}
             */
            ns.A = function() {};
            """)
        .addDiagnostic("variable ns.A redefined, original definition at testcode0:6")
        .run();
  }

  @Test
  public void testBadInterfaceExtends1() {
    newTest()
        .addSource(
            """
            /**
             * @interface
             * @extends {nonExistent}
             */
            function A() {}
            """)
        .addDiagnostic("Bad type annotation. Unknown type nonExistent")
        .run();
  }

  @Test
  public void testBadInterfaceExtendsNonExistentInterfaces() {
    newTest()
        .addSource(
            """
            /** @interface\s
             * @extends {nonExistent1}\s
             * @extends {nonExistent2}\s
             */function A() {}
            """)
        .addDiagnostic("Bad type annotation. Unknown type nonExistent1")
        .addDiagnostic("Bad type annotation. Unknown type nonExistent2")
        .run();
  }

  @Test
  public void testBadInterfaceExtends2() {
    newTest()
        .addSource(
            """
            /** @constructor */function A() {}
            /** @interface
             * @extends {A} */function B() {}
            """)
        .addDiagnostic("B cannot extend this type; interfaces can only extend interfaces")
        .run();
  }

  @Test
  public void testBadInterfaceExtends3() {
    newTest()
        .addSource(
            """
            /** @interface */function A() {}
            /** @constructor
             * @extends {A} */function B() {}
            """)
        .addDiagnostic("B cannot extend this type; constructors can only extend constructors")
        .run();
  }

  @Test
  public void testBadInterfaceExtends4() {
    // TODO(user): This should be detected as an error. Even if we enforce
    // that A cannot be used in the assignment, we should still detect the
    // inheritance chain as invalid.
    newTest()
        .addSource(
            """
            /** @interface */function A() {}
            /** @constructor */function B() {}
            B.prototype = A;
            """)
        .run();
  }

  @Test
  public void testBadInterfaceExtends5() {
    // TODO(user): This should be detected as an error. Even if we enforce
    // that A cannot be used in the assignment, we should still detect the
    // inheritance chain as invalid.
    newTest()
        .addSource(
            """
            /** @constructor */function A() {}
            /** @interface */function B() {}
            B.prototype = A;
            """)
        .run();
  }

  @Test
  public void testBadImplementsAConstructor() {
    newTest()
        .addSource(
            """
            /** @constructor */function A() {}
            /** @constructor
             * @implements {A} */function B() {}
            """)
        .addDiagnostic("can only implement interfaces")
        .run();
  }

  @Test
  public void testBadImplementsAConstructorWithSubclass() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function A() {}
            /** @implements {A} */
            class B {}
            class C extends B {}
            """)
        .addDiagnostic("can only implement interfaces")
        .run();
  }

  @Test
  public void testBadImplementsNonInterfaceType() {
    newTest()
        .addSource(
            """
            /** @constructor
             * @implements {Boolean} */function B() {}
            """)
        .addDiagnostic("can only implement interfaces")
        .run();
  }

  @Test
  public void testBadImplementsNonObjectType() {
    newTest()
        .addSource(
            """
            /** @constructor
             * @implements {string} */function S() {}
            """)
        .addDiagnostic("can only implement interfaces")
        .run();
  }

  @Test
  public void testBadImplementsDuplicateInterface1() {
    // verify that the same base (not templatized) interface cannot be
    // @implemented more than once.
    newTest()
        .addSource(
            """
            /** @interface\s
             * @template T
             */
            function Foo() {}
            /** @constructor\s
             * @implements {Foo<?>}
             * @implements {Foo}
             */
            function A() {}
            """)
        .addDiagnostic(
            """
            Cannot @implement the same interface more than once
            Repeated interface: Foo
            """)
        .run();
  }

  @Test
  public void testBadImplementsDuplicateInterface2() {
    // verify that the same base (not templatized) interface cannot be
    // @implemented more than once.
    newTest()
        .addSource(
            """
            /** @interface\s
             * @template T
             */
            function Foo() {}
            /** @constructor\s
             * @implements {Foo<string>}
             * @implements {Foo<number>}
             */
            function A() {}
            """)
        .addDiagnostic(
            """
            Cannot @implement the same interface more than once
            Repeated interface: Foo
            """)
        .run();
  }

  @Test
  public void testInterfaceAssignment1() {
    newTest()
        .addSource(
            """
            /** @interface */var I = function() {};
            /** @constructor
            @implements {I} */var T = function() {};
            var t = new T();
            /** @type {!I} */var i = t;
            """)
        .run();
  }

  @Test
  public void testInterfaceAssignment2() {
    newTest()
        .addSource(
            """
            /** @interface */var I = function() {};
            /** @constructor */var T = function() {};
            var t = new T();
            /** @type {!I} */var i = t;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : T
            required: I
            """)
        .run();
  }

  @Test
  public void testInterfaceAssignment3() {
    newTest()
        .addSource(
            """
            /** @interface */var I = function() {};
            /** @constructor
            @implements {I} */var T = function() {};
            var t = new T();
            /** @type {I|number} */var i = t;
            """)
        .run();
  }

  @Test
  public void testInterfaceAssignment4() {
    newTest()
        .addSource(
            """
            /** @interface */var I1 = function() {};
            /** @interface */var I2 = function() {};
            /** @constructor
            @implements {I1} */var T = function() {};
            var t = new T();
            /** @type {I1|I2} */var i = t;
            """)
        .run();
  }

  @Test
  public void testInterfaceAssignment5() {
    newTest()
        .addSource(
            """
            /** @interface */var I1 = function() {};
            /** @interface */var I2 = function() {};
            /** @constructor
             * @implements {I1}
             * @implements {I2}*/
            var T = function() {};
            var t = new T();
            /** @type {I1} */var i1 = t;
            /** @type {I2} */var i2 = t;
            """)
        .run();
  }

  @Test
  public void testInterfaceAssignment6() {
    newTest()
        .addSource(
            """
            /** @interface */var I1 = function() {};
            /** @interface */var I2 = function() {};
            /** @constructor
            @implements {I1} */var T = function() {};
            /** @type {!I1} */var i1 = new T();
            /** @type {!I2} */var i2 = i1;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : I1
            required: I2
            """)
        .run();
  }

  @Test
  public void testInterfaceAssignment7() {
    newTest()
        .addSource(
            """
            /** @interface */var I1 = function() {};
            /** @interface
            @extends {I1}*/var I2 = function() {};
            /** @constructor
            @implements {I2}*/var T = function() {};
            var t = new T();
            /** @type {I1} */var i1 = t;
            /** @type {I2} */var i2 = t;
            i1 = i2;
            """)
        .run();
  }

  @Test
  public void testInterfaceAssignment8() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @interface */var I = function() {};
            /** @type {I} */var i;
            /** @type {Object} */var o = i;
            new Object().prototype = i.prototype;
            """)
        .run();
  }

  @Test
  public void testInterfaceAssignment9() {
    newTest()
        .addSource(
            """
            /** @interface */var I = function() {};
            /** @return {I?} */function f() { return null; }
            /** @type {!I} */var i = f();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (I|null)
            required: I
            """)
        .run();
  }

  @Test
  public void testInterfaceAssignment10() {
    newTest()
        .addSource(
            """
            /** @interface */var I1 = function() {};
            /** @interface */var I2 = function() {};
            /** @constructor
            @implements {I2} */var T = function() {};
            /** @return {!I1|!I2} */function f() { return new T(); }
            /** @type {!I1} */var i1 = f();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (I1|I2)
            required: I1
            """)
        .run();
  }

  @Test
  public void testInterfaceAssignment11() {
    newTest()
        .addSource(
            """
            /** @interface */var I1 = function() {};
            /** @interface */var I2 = function() {};
            /** @constructor */var T = function() {};
            /** @return {!I1|!I2|!T} */function f() { return new T(); }
            /** @type {!I1} */var i1 = f();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (I1|I2|T)
            required: I1
            """)
        .run();
  }

  @Test
  public void testInterfaceAssignment12() {
    newTest()
        .addSource(
            """
            /** @interface */var I = function() {};
            /** @constructor
            @implements{I}*/var T1 = function() {};
            /** @constructor
            @extends {T1}*/var T2 = function() {};
            /** @return {I} */function f() { return new T2(); }
            """)
        .run();
  }

  @Test
  public void testInterfaceAssignment13() {
    newTest()
        .addSource(
            """
            /** @interface */var I = function() {};
            /** @constructor
             * @implements {I}*/var T = function() {};
            /** @constructor */function Super() {};
            /** @return {I} */Super.prototype.foo = function() { return new T(); };
            /** @constructor
             * @extends {Super} */function Sub() {}
            /** @override
             * @return {T} */Sub.prototype.foo = function() { return new T(); };
            """)
        .run();
  }

  @Test
  public void testGetprop1() {
    newTest()
        .addSource("/** @return {void}*/function foo(){foo().bar;}")
        .addDiagnostic(
            """
            No properties on this expression
            found   : undefined
            required: Object
            """)
        .run();
  }

  @Test
  public void testGetprop2() {
    newTest()
        .addSource("var x = null; x.alert();")
        .addDiagnostic(
            """
            No properties on this expression
            found   : null
            required: Object
            """)
        .run();
  }

  @Test
  public void testGetprop3() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() { /** @type {?Object} */ this.x = null; }
            Foo.prototype.initX = function() { this.x = {foo: 1}; };
            Foo.prototype.bar = function() {
              if (this.x == null) { this.initX(); alert(this.x.foo); }
            };
            """)
        .run();
  }

  @Test
  public void testGetprop4() {
    newTest()
        .addSource("var x = null; x.prop = 3;")
        .addDiagnostic(
            """
            No properties on this expression
            found   : null
            required: Object
            """)
        .run();
  }

  @Test
  public void testGetrop_interfaceWithoutTypeDeclaration() {
    newTest()
        .addSource(
            """
            /** @interface */var I = function() {};
            // Note that we didn't declare the type but we still expect JsCompiler to recognize the
            // property.
            I.prototype.foo;
            var v = /** @type {I} */ (null);
            v.foo = 5;
            """)
        .run();
  }

  @Test
  public void testGetrop_interfaceEs6WithoutTypeDeclaration() {
    newTest()
        .addSource(
            """
            /** @interface */
            class I {
              constructor() {
                this.foo;
              }
            }
            var v = /** @type {I} */ (null);
            v.foo = 5;
            """)
        // TODO(b/131257037): Support ES6 style instance properties on interfaces.
        .addDiagnostic("Property foo never defined on I")
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_MESSAGE)
        .run();
  }

  @Test
  public void testGetrop_interfaceEs6WithTypeDeclaration() {
    newTest()
        .addSource(
            """
            /** @interface */
            class I {
              constructor() {
                /** @type {number} */ this.foo;
              }
            }
            var v = /** @type {I} */ (null);
            v.foo = 5;
            """)
        .run();
  }

  @Test
  public void testSetprop1() {
    // Create property on struct in the constructor
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() { this.x = 123; }
            """)
        .run();
  }

  @Test
  public void testSetprop2() {
    // Create property on struct outside the constructor
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {}
            (new Foo()).x = 123;
            """)
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_MESSAGE)
        .run();
  }

  @Test
  public void testSetprop3() {
    // Create property on struct outside the constructor
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {}
            (function() { (new Foo()).x = 123; })();
            """)
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_MESSAGE)
        .run();
  }

  @Test
  public void testSetprop4() {
    // Assign to existing property of struct outside the constructor
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() { this.x = 123; }
            (new Foo()).x = "asdf";
            """)
        .run();
  }

  @Test
  public void testSetprop5() {
    // Create a property on union that includes a struct
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {}
            (true ? new Foo() : {}).x = 123;
            """)
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_ON_UNION_TYPE)
        .run();
  }

  @Test
  public void testSetprop6() {
    // Create property on struct in another constructor
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {}
            /**
             * @constructor
             * @param{Foo} f
             */
            function Bar(f) { f.x = 123; }
            """)
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_MESSAGE)
        .run();
  }

  @Test
  public void testSetprop7() {
    // Bug b/c we require THIS when creating properties on structs for simplicity
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {
              var t = this;
              t.x = 123;
            }
            """)
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_MESSAGE)
        .run();
  }

  @Test
  public void testSetprop8() {
    // Create property on struct using DEC
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {}
            (new Foo()).x--;
            """)
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_MESSAGE)
        .addDiagnostic("Property x never defined on Foo")
        .run();
  }

  @Test
  public void testSetprop9() {
    // Create property on struct using ASSIGN_ADD
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {}
            (new Foo()).x += 123;
            """)
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_MESSAGE)
        .addDiagnostic("Property x never defined on Foo")
        .run();
  }

  @Test
  public void testSetprop10() {
    // Create property on object literal that is a struct
    newTest()
        .addSource(
            """
            /**\s
             * @constructor\s
             * @struct\s
             */\s
            function Square(side) {\s
              this.side = side;\s
            }\s
            Square.prototype = /** @struct */ {
              area: function() { return this.side * this.side; }
            };
            Square.prototype.id = function(x) { return x; };
            """)
        .run();
  }

  @Test
  public void testSetprop11() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {}
            /** @constructor */
            function Bar() {}
            Bar.prototype = new Foo();
            Bar.prototype.someprop = 123;
            """)
        .run();
  }

  @Test
  public void testSetprop12() {
    // Create property on a constructor of structs (which isn't itself a struct)
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {}
            Foo.someprop = 123;
            """)
        .run();
  }

  @Test
  public void testSetprop13() {
    // Create static property on struct
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Parent() {}
            /**
             * @constructor
             * @extends {Parent}
             */
            function Kid() {}
            Kid.prototype.foo = 123;
            var x = (new Kid()).foo;
            """)
        .run();
  }

  @Test
  public void testSetprop14() {
    // Create static property on struct
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Top() {}
            /**
             * @constructor
             * @extends {Top}
             */
            function Mid() {}
            /** blah blah */
            Mid.prototype.foo = function() { return 1; };
            /**
             * @constructor
             * @struct
             * @extends {Mid}
             */
            function Bottom() {}
            /** @override */
            Bottom.prototype.foo = function() { return 3; };
            """)
        .run();
  }

  @Test
  public void testSetprop15() {
    // Create static property on struct
    newTest()
        .addSource(
            """
            /** @interface */
            function Peelable() {};
            /** @return {undefined} */
            Peelable.prototype.peel;
            /**
             * @constructor
             * @struct
             */
            function Fruit() {};
            /**
             * @constructor
             * @extends {Fruit}
             * @implements {Peelable}
             */
            function Banana() { };
            function f() {};
            /** @override */
            Banana.prototype.peel = f;
            """)
        .run();
  }

  @Test
  public void testGetpropDict1() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @dict
             */
            function Dict1(){ this['prop'] = 123; }
            /** @param{Dict1} x */
            function takesDict(x) { return x.prop; }
            """)
        .addDiagnostic("Cannot do '.' access on a dict")
        .run();
  }

  @Test
  public void testGetpropDict2() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @dict
             */
            function Dict1(){ this['prop'] = 123; }
            /**
             * @constructor
             * @extends {Dict1}
             */
            function Dict1kid(){ this['prop'] = 123; }
            /** @param{Dict1kid} x */
            function takesDict(x) { return x.prop; }
            """)
        .addDiagnostic("Cannot do '.' access on a dict")
        .run();
  }

  @Test
  public void testGetpropDict3() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @dict
             */
            function Dict1() { this['prop'] = 123; }
            /** @constructor */
            function NonDict() { this.prop = 321; }
            /** @param{(NonDict|Dict1)} x */
            function takesDict(x) { return x.prop; }
            """)
        .addDiagnostic("Cannot do '.' access on a dict")
        .run();
  }

  @Test
  public void testGetpropDict4() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @dict
             */
            function Dict1() { this['prop'] = 123; }
            /**
             * @constructor
             * @struct
             */
            function Struct1() { this.prop = 123; }
            /** @param{(Struct1|Dict1)} x */
            function takesNothing(x) { return x.prop; }
            """)
        .addDiagnostic("Cannot do '.' access on a dict")
        .run();
  }

  @Test
  public void testGetpropDict5() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @dict
             */
            function Dict1(){ this.prop = 123; }
            """)
        .addDiagnostic("Cannot do '.' access on a dict")
        .run();
  }

  @Test
  public void testGetpropDict6() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @dict
             */
            function Foo() {}
            function Bar() {}
            Bar.prototype = new Foo();
            Bar.prototype.someprop = 123;
            """)
        .run();
  }

  @Test
  public void testGetpropDict7() {
    newTest()
        .addSource("(/** @dict */ {'x': 123}).x = 321;")
        .addDiagnostic("Cannot do '.' access on a dict")
        .run();
  }

  @Test
  public void testDictEs5ClassCannotExtendStruct() {
    newTest()
        .addSource(
            """
            /** @struct @constructor*/
            function StructParent() {}
            /** @constructor @dict @extends {StructParent} */
            function DictChild() {}
            DictChild.prototype['prop'] = function() {};
            """)
        .addDiagnostic("@dict class DictChild cannot extend @struct class StructParent")
        .run();
  }

  @Test
  public void testDictEs6ClassCannotExtendStruct() {
    newTest()
        .addSource(
            """
            class StructParent {}
            /** @dict */
            class DictChild extends StructParent {
              ['prop']() {}
            }
            """)
        .addDiagnostic("@dict class DictChild cannot extend @struct class StructParent")
        .run();
  }

  @Test
  public void testStructEs6ClassCannotExtendDict() {
    newTest()
        .addSource(
            """
            /** @dict */
            class DictParent {}
            /** @struct */
            class StructChild extends DictParent {
              ['prop']() {}
            }
            """)
        .addDiagnostic("@struct class StructChild cannot extend @dict class DictParent")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testStructEs6ClassCanExtendDict_transitive() {
    newTest()
        .addSource(
            """
            /** @dict */
            class DictParent {}
            /** @unrestricted */
            class UnannotatedMiddleClass extends DictParent {}
            /** @struct */
            class StructChild extends UnannotatedMiddleClass {
              ['prop']() {}
            }
            """)
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testGetelemStruct1() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Struct1(){ this.prop = 123; }
            /** @param{Struct1} x */
            function takesStruct(x) {
              var z = x;
              return z['prop'];
            }
            """)
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testGetelemStruct2() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Struct1(){ this.prop = 123; }
            /**
             * @constructor
             * @extends {Struct1}
             */
            function Struct1kid(){ this.prop = 123; }
            /** @param{Struct1kid} x */
            function takesStruct2(x) { return x['prop']; }
            """)
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testGetelemStruct3() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Struct1(){ this.prop = 123; }
            /**
             * @constructor
             * @extends {Struct1}
             */
            function Struct1kid(){ this.prop = 123; }
            var x = (new Struct1kid())['prop'];
            """)
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testGetelemStruct4() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Struct1() { this.prop = 123; }
            /** @constructor */
            function NonStruct() { this.prop = 321; }
            /** @param{(NonStruct|Struct1)} x */
            function takesStruct(x) { return x['prop']; }
            """)
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testGetelemStruct5() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Struct1() { this.prop = 123; }
            /**
             * @constructor
             * @dict
             */
            function Dict1() { this['prop'] = 123; }
            /** @param{(Struct1|Dict1)} x */
            function takesNothing(x) { return x['prop']; }
            """)
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testGetelemStruct6() {
    // By casting Bar to Foo, the illegal bracket access is not detected
    newTest()
        .addSource(
            """
            /** @interface */ function Foo(){}
            /**
             * @constructor
             * @struct
             * @implements {Foo}
             */
            function Bar(){ this.x = 123; }
            var z = /** @type {Foo} */(new Bar())['x'];
            """)
        .run();
  }

  @Test
  public void testGetelemStruct7() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {}
            /** @constructor */
            function Bar() {}
            Bar.prototype = new Foo();
            Bar.prototype['someprop'] = 123;
            """)
        .run();
  }

  @Test
  public void testGetelemStruct_noErrorForSettingWellKnownSymbol() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {}
            Foo.prototype[Symbol.iterator] = 123;
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGetelemStruct_noErrorForGettingWellKnownSymbol() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {}
            /** @param {!Foo} foo */
            function getIterator(foo) { return foo[Symbol.iterator](); }
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testInOnStruct() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {}
            if ('prop' in (new Foo())) {}
            """)
        .addDiagnostic("Cannot use the IN operator with structs")
        .run();
  }

  @Test
  public void testForinOnStruct() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {}
            for (var prop in (new Foo())) {}
            """)
        .addDiagnostic("Cannot use the IN operator with structs")
        .run();
  }

  @Test
  public void testIArrayLikeAccess1() {
    newTest()
        .addSource(
            """
            /**
             * @param {!IArrayLike<T>} x
             * @return {T}
             * @template T
            */
            function f(x) { return x[0]; }
            function g(/** !Array<string> */ x) {
              var /** null */ y = f(x);
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: null
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLikeAccess2() {
    newTest()
        .addSource(
            """
            /**
             * @param {!IArrayLike<T>} x
             * @return {T}
             * @template T
            */
            function f(x) { return x[0]; }
            function g(/** !IArrayLike<string> */ x) {
              var /** null */ y = f(x);
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: null
            """)
        .includeDefaultExterns()
        .run();
  }

  // These test the template types in the built-in Iterator/Iterable/Generator are set up correctly
  @Test
  public void testIteratorAccess1() {
    newTest()
        .addSource(
            """
            /**
             * @param {!Iterator<T>} x
             * @return {T}
             * @template T
            */
            function f(x) { return x[0]; }
            function g(/** !Generator<string> */ x) {
              var /** null */ y = f(x);
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: null
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIterableAccess1() {
    newTest()
        .addSource(
            """
            /**
             * @param {!Iterable<T>} x
             * @return {T}
             * @template T
            */
            function f(x) { return x[0]; }
            function g(/** !Generator<string> */ x) {
              var /** null */ y = f(x);
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: null
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIteratorIterableAccess1() {
    newTest()
        .addSource(
            """
            /**
             * @param {!IteratorIterable<T>} x
             * @return {T}
             * @template T
            */
            function f(x) { return x[0]; }
            function g(/** !Generator<string> */ x) {
              var /** null */ y = f(x);
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: null
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess1() {
    newTest()
        .addSource("var a = []; var b = a['hi'];")
        .addDiagnostic(
            """
            restricted index type
            found   : string
            required: number
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess2() {
    newTest()
        .addSource("var a = []; var b = a[[1,2]];")
        .addDiagnostic(
            """
            restricted index type
            found   : Array<?>
            required: number
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess3() {
    newTest()
        .addSource(
            """
            var bar = [];
            /** @return {void} */function baz(){};
            var foo = bar[baz()];
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : undefined
            required: number
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess4() {
    newTest()
        .addSource("/**@return {!Array}*/function foo(){};var bar = foo()[foo()];")
        .addDiagnostic(
            """
            restricted index type
            found   : Array
            required: number
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess6() {
    newTest()
        .addSource("var bar = null[1];")
        .addDiagnostic(
            """
            only arrays or objects can be accessed
            found   : null
            required: Object
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess7() {
    newTest()
        .addSource("var bar = void 0; bar[0];")
        .addDiagnostic(
            """
            only arrays or objects can be accessed
            found   : undefined
            required: Object
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess8() {
    // Verifies that we don't emit two warnings, because
    // the var has been dereferenced after the first one.
    newTest()
        .addSource("var bar = void 0; bar[0]; bar[1];")
        .addDiagnostic(
            """
            only arrays or objects can be accessed
            found   : undefined
            required: Object
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess9() {
    newTest()
        .addSource(
            """
            /** @return {?Array} */ function f() { return []; }
            f()[{}]
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : {}
            required: number
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testPropAccess() {
    newTest()
        .addSource(
            """
            /** @param {*} x */var f = function(x) {
            var o = String(x);
            if (typeof o['a'] != 'undefined') { return o['a']; }
            return null;
            };
            """)
        .run();
  }

  @Test
  public void testPropAccess2() {
    newTest()
        .addSource("var bar = void 0; bar.baz;")
        .addDiagnostic(
            """
            No properties on this expression
            found   : undefined
            required: Object
            """)
        .run();
  }

  @Test
  public void testPropAccess3() {
    // Verifies that we don't emit two warnings, because
    // the var has been dereferenced after the first one.
    newTest()
        .addSource("var bar = void 0; bar.baz; bar.bax;")
        .addDiagnostic(
            """
            No properties on this expression
            found   : undefined
            required: Object
            """)
        .run();
  }

  @Test
  public void testPropAccess4() {
    newTest().addSource("/** @param {*} x */ function f(x) { return x['hi']; }").run();
  }

  @Test
  public void testSwitchCase1() {
    newTest()
        .addSource(
            """
            /**@type {number}*/var a;
            /**@type {string}*/var b;
            switch(a){case b:;}
            """)
        .addDiagnostic(
            """
            case expression doesn't match switch
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testSwitchCase2() {
    newTest().addSource("var a = null; switch (typeof a) { case 'foo': }").run();
  }

  @Test
  public void testVar1() {
    TypeCheckResult p = parseAndTypeCheckWithScope("/** @type {(string|null)} */var a = null");

    assertTypeEquals(
        createUnionType(getNativeStringType(), getNativeNullType()), p.scope.getVar("a").getType());
  }

  @Test
  public void testVar2() {
    newTest().addSource("/** @type {Function} */ var a = function(){}").run();
  }

  @Test
  public void testVar3() {
    TypeCheckResult p = parseAndTypeCheckWithScope("var a = 3;");

    assertTypeEquals(getNativeNumberType(), p.scope.getVar("a").getType());
  }

  @Test
  public void testVar4() {
    TypeCheckResult p = parseAndTypeCheckWithScope("var a = 3; a = 'string';");

    assertTypeEquals(
        createUnionType(getNativeStringType(), getNativeNumberType()),
        p.scope.getVar("a").getType());
  }

  @Test
  public void testVar5() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @type {string} */goog.foo = 'hello';
            /** @type {number} */var a = goog.foo;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testVar6() {
    newTest()
        .addSource(
            """
            function f() {
              return function() {
                /** @type {!Date} */
                var a = 7;
              };
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: Date
            """)
        .run();
  }

  @Test
  public void testVar7() {
    newTest()
        .addSource("/** @type {number} */var a, b;")
        .addDiagnostic("declaration of multiple variables with shared type information")
        .run();
  }

  @Test
  public void testVar8() {
    newTest().addSource("var a, b;").run();
  }

  @Test
  public void testVar9() {
    newTest()
        .addSource("/** @enum */var a;")
        .addDiagnostic("enum initializer must be an object literal or an enum")
        .run();
  }

  @Test
  public void testVar10() {
    newTest()
        .addSource("/** @type {!Number} */var foo = 'abc';")
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: Number
            """)
        .run();
  }

  @Test
  public void testVar11() {
    newTest()
        .addSource("var /** @type {!Date} */foo = 'abc';")
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: Date
            """)
        .run();
  }

  @Test
  public void testVar12() {
    newTest()
        .addSource(
            """
            var /** @type {!Date} */foo = 'abc',
            /** @type {!RegExp} */bar = 5;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: Date
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: RegExp
            """)
        .run();
  }

  @Test
  public void testVar13() {
    // this caused an NPE
    newTest().addSource("var /** @type {number} */a,a;").run();
  }

  @Test
  public void testVar14() {
    newTest()
        .addSource("/** @return {number} */ function f() { var x; return x; }")
        .addDiagnostic(
            """
            inconsistent return type
            found   : undefined
            required: number
            """)
        .run();
  }

  @Test
  public void testVar15() {
    newTest()
        .addSource(
            """
            /** @return {number} */
            function f() { var x = x || {}; return x; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : {}
            required: number
            """)
        .run();
  }

  @Test
  public void testVar15NullishCoalesce() {
    newTest()
        .addSource(
            """
            /** @return {number} */
            function f() { var x = x ?? {}; return x; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : {}
            required: number
            """)
        .run();
  }

  @Test
  public void testAssign1() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @type {number} */goog.foo = 'hello';
            """)
        .addDiagnostic(
            """
            assignment to property foo of goog
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testAssign2() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @type {number}  */goog.foo = 3;
            goog.foo = 'hello';
            """)
        .addDiagnostic(
            """
            assignment to property foo of goog
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testAssign3() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @type {number}  */goog.foo = 3;
            goog.foo = 4;
            """)
        .run();
  }

  @Test
  public void testAssign4() {
    newTest()
        .addSource(
            """
            var goog = {};
            goog.foo = 3;
            goog.foo = 'hello';
            """)
        .run();
  }

  @Test
  public void testAssignInference() {
    newTest()
        .addSource(
            """
            /**
             * @param {Array} x
             * @return {number}
             */
            function f(x) {
              var y = null;
              y = x[0];
              if (y == null) { return 4; } else { return 6; }
            }
            """)
        .run();
  }

  @Test
  public void testAssignReadonlyArrayValueFails() {
    newTest()
        .includeDefaultExterns()
        .addSource(
            """
            const foo = /** @type {!ReadonlyArray<number>} */ ([5]);
            foo[0] = 3;
            """)
        .diagnosticsAreErrors()
        .addDiagnostic(TypeCheck.PROPERTY_ASSIGNMENT_TO_READONLY_VALUE)
        .run();
  }

  @Test
  public void testAssignReadonlyArrayValueFailsWithoutTypeParameter() {
    newTest()
        .includeDefaultExterns()
        .addSource(
            """
            const foo = /** @type {!ReadonlyArray} */ ([5]);
            foo[0] = 3;
            """)
        .diagnosticsAreErrors()
        .addDiagnostic(TypeCheck.PROPERTY_ASSIGNMENT_TO_READONLY_VALUE)
        .run();
  }

  @Test
  public void testAssignReadonlyArrayLengthFails() {
    newTest()
        .includeDefaultExterns()
        .addSource(
            """
            const foo = /** @type {!ReadonlyArray<number>} */ ([5]);
            foo.length = 0;
            """)
        .diagnosticsAreErrors()
        .addDiagnostic(TypeCheck.PROPERTY_ASSIGNMENT_TO_READONLY_VALUE)
        .run();
  }

  @Test
  public void testOr1() {
    newTest()
        .addSource(
            """
            /** @type {number}  */var a;
            /** @type {number}  */var b;
            a + b || undefined;
            """)
        .run();
  }

  @Test
  public void testNullishCoalesceNumber() {
    newTest()
        .addSource(
            """
            /** @type {number}  */var a;
            /** @type {number}  */var b;
            a + b ?? undefined;
            """)
        .run();
  }

  @Test
  public void testOr2() {
    newTest()
        .addSource(
            """
            /** @type {number}  */var a;
            /** @type {number}  */var b;
            /** @type {number}  */var c = a + b || undefined;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (number|undefined)
            required: number
            """)
        .run();
  }

  @Test
  public void testNullishCoalesceNumberVar() {
    // Making sure that ?? returns LHS as long as it is not null/undefined
    // 0 is falsy but not null/undefined so c should always be a
    newTest()
        .addSource(
            """
            /** @type {number}  */var a;
            /** @type {number}  */var c = a ?? undefined;
            """)
        .run();
  }

  @Test
  public void testOr3() {
    newTest()
        .addSource(
            """
            /** @type {(number|undefined)} */var a;
            /** @type {number}  */var c = a || 3;
            """)
        .run();
  }

  @Test
  public void testNullishCoalesceNumberUndefined() {
    newTest()
        .addSource(
            """
            /** @type {(number|undefined)} */var a;
            /** @type {number}  */var c = a ?? 3;
            """)
        .run();
  }

  /**
   * Test that type inference continues with the right side, when no short-circuiting is possible.
   * See bugid 1205387 for more details.
   */
  @Test
  public void testOr4() {
    newTest()
        .addSource("/**@type {number} */var x;x=null || \"a\";")
        .addDiagnostic(
            """
            assignment
            found   : string
            required: number
            """)
        .run();
  }

  /**
   * @see #testOr4()
   */
  @Test
  public void testOr5() {
    newTest()
        .addSource("/**@type {number} */var x;x=undefined || \"a\";")
        .addDiagnostic(
            """
            assignment
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testNullishCoalesceAssignment2() {
    newTest()
        .addSource("/**@type {number} */var x;x=undefined ?? \"a\";")
        .addDiagnostic(
            """
            assignment
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testOr6() {
    newTest()
        .addSource(
            """
            /** @param {!Array=} opt_x */
            function removeDuplicates(opt_x) {
              var x = opt_x || [];
              var /** undefined */ y = x;
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Array
            required: undefined
            """)
        .run();
  }

  @Test
  public void testNullishCoaleceAssignment3() {
    newTest()
        .addSource(
            """
            /** @param {!Array=} opt_x */
            function removeDuplicates(opt_x) {
              var x = opt_x ?? [];
              var /** undefined */ y = x;
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Array
            required: undefined
            """)
        .run();
  }

  @Test
  public void testAnd1() {
    newTest()
        .addSource(
            """
            /** @type {number}  */var a;
            /** @type {number}  */var b;
            a + b && undefined;
            """)
        .run();
  }

  @Test
  public void testAnd2() {
    newTest()
        .addSource(
            """
            /** @type {number}  */var a;
            /** @type {number}  */var b;
            /** @type {number}  */var c = a + b && undefined;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (number|undefined)
            required: number
            """)
        .run();
  }

  @Test
  public void testAnd3() {
    newTest()
        .addSource(
            """
            /** @type {(!Array|undefined)} */var a;
            /** @type {number}  */var c = a && undefined;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : undefined
            required: number
            """)
        .run();
  }

  @Test
  public void testAnd4() {
    newTest()
        .addSource(
            """
            /** @param {number} x */function f(x){};
            /** @type {null}  */var x; /** @type {number?} */var y;
            if (x && y) { f(y) }
            """)
        .run();
  }

  @Test
  public void testAnd5() {
    newTest()
        .addSource(
            """
            /** @param {number} x
            @param {string} y*/function f(x,y){};
            /** @type {number?} */var x; /** @type {string?} */var y;
            if (x && y) { f(x, y) }
            """)
        .run();
  }

  @Test
  public void testAnd6() {
    newTest()
        .addSource(
            """
            /** @param {number} x */function f(x){};
            /** @type {number|undefined} */var x;
            if (x && f(x)) { f(x) }
            """)
        .run();
  }

  @Test
  public void testAnd7() {
    // TODO(user): a deterministic warning should be generated for this
    // case since x && x is always false. The implementation of this requires
    // a more precise handling of a null value within a variable's type.
    // Currently, a null value defaults to ? which passes every check.
    newTest().addSource("/** @type {null} */var x; if (x && x) {}").run();
  }

  @Test
  public void testAnd8() {
    newTest()
        .addSource(
            """
            function f(/** (null | number | string) */ x) {
              (x && (typeof x === 'number')) && takesNum(x);
            }
            function takesNum(/** number */ n) {}
            """)
        .run();
  }

  @Test
  public void testAnd9() {
    newTest()
        .addSource(
            """
            function f(/** (number|string|null) */ x) {
              if (x && typeof x === 'number') {
                takesNum(x);
              }
            }
            function takesNum(/** number */ x) {}
            """)
        .run();
  }

  @Test
  public void testAnd10() {
    newTest()
        .addSource(
            """
            function f(/** (null | number | string) */ x) {
              (x && (typeof x === 'string')) && takesNum(x);
            }
            function takesNum(/** number */ n) {}
            """)
        .addDiagnostic(
            """
            actual parameter 1 of takesNum does not match formal parameter
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testHook() {
    newTest().addSource("/**@return {void}*/function foo(){ var x=foo()?a:b; }").run();
  }

  @Test
  public void testHookRestrictsType1() {
    newTest()
        .addSource(
            """
            /** @return {(string|null)} */
            function f() { return null;}
            /** @type {(string|null)} */ var a = f();
            /** @type {string} */
            var b = a ? a : 'default';
            """)
        .run();
  }

  @Test
  public void testHookRestrictsType2() {
    newTest()
        .addSource(
            """
            /** @type {String} */
            var a = null;
            /** @type {null} */
            var b = a ? null : a;
            """)
        .run();
  }

  @Test
  public void testHookRestrictsType3() {
    newTest()
        .addSource(
            """
            /** @type {String} */
            var a;
            /** @type {null} */
            var b = (!a) ? a : null;
            """)
        .run();
  }

  @Test
  public void testHookRestrictsType4() {
    newTest()
        .addSource(
            """
            /** @type {(boolean|undefined)} */
            var a;
            /** @type {boolean} */
            var b = a != null ? a : true;
            """)
        .run();
  }

  @Test
  public void testHookRestrictsType5() {
    newTest()
        .addSource(
            """
            /** @type {(boolean|undefined)} */
            var a;
            /** @type {(undefined)} */
            var b = a == null ? a : undefined;
            """)
        .run();
  }

  @Test
  public void testHookRestrictsType6() {
    newTest()
        .addSource(
            """
            /** @type {(number|null|undefined)} */
            var a;
            /** @type {number} */
            var b = a == null ? 5 : a;
            """)
        .run();
  }

  @Test
  public void testHookRestrictsType7() {
    newTest()
        .addSource(
            """
            /** @type {(number|null|undefined)} */
            var a;
            /** @type {number} */
            var b = a == undefined ? 5 : a;
            """)
        .run();
  }

  @Test
  public void testWhileRestrictsType1() {
    newTest()
        .addSource(
            """
            /** @param {null} x */ function g(x) {}
            /** @param {number?} x */
            function f(x) {
            while (x) {
            if (g(x)) { x = 1; }
            x = x-1;
            }
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : number
            required: null
            """)
        .run();
  }

  @Test
  public void testWhileRestrictsType2() {
    newTest()
        .addSource(
            """
            /** @param {number?} x
            @return {number}*/
            function f(x) {
            /** @type {number} */var y = 0;
            while (x) {
            y = x;
            x = x-1;
            }
            return y;
            }
            """)
        .run();
  }

  @Test
  public void testHigherOrderFunctions1() {
    newTest()
        .addSource(
            """
            /** @type {function(number)} */var f;
            f(true);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testHigherOrderFunctions2() {
    newTest()
        .addSource(
            """
            /** @type {function():!Date} */var f;
            /** @type {boolean} */var a = f();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Date
            required: boolean
            """)
        .run();
  }

  @Test
  public void testHigherOrderFunctions3() {
    newTest()
        .addSource("/** @type {function(this:Array):Date} */var f; new f")
        .addDiagnostic(
            "cannot instantiate non-constructor, found type: function(this:Array): (Date|null)")
        .run();
  }

  @Test
  public void testHigherOrderFunctions4() {
    newTest()
        .addSource("/** @type {function(this:Array, ...number):Date} */var f; new f")
        .addDiagnostic(
            """
            cannot instantiate non-constructor, found type: function(this:Array, ...number): (Date|null)
            """)
        .run();
  }

  @Test
  public void testHigherOrderFunctions5() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ function g(x) {}
            /** @type {function(new:Array, ...number):Date} */ var f;
            g(new f());
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : Array
            required: number
            """)
        .run();
  }

  @Test
  public void testConstructorAlias1() {
    newTest()
        .addSource(
            """
            /** @constructor */ var Foo = function() {};
            /** @type {number} */ Foo.prototype.bar = 3;
            /** @constructor */ var FooAlias = Foo;
            /** @return {string} */ function foo() {
              return (new FooAlias()).bar; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testConstructorAlias2() {
    newTest()
        .addSource(
            """
            /** @constructor */ var Foo = function() {};
            /** @constructor */ var FooAlias = Foo;
            /** @type {number} */ FooAlias.prototype.bar = 3;
            /** @return {string} */ function foo() {
              return (new Foo()).bar; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testConstructorAlias3() {
    newTest()
        .addSource(
            """
            /** @constructor */ var Foo = function() {};
            /** @type {number} */ Foo.prototype.bar = 3;
            /** @constructor */ var FooAlias = Foo;
            /** @return {string} */ function foo() {
              return (new FooAlias()).bar; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testConstructorAlias4() {
    newTest()
        .addSource(
            """
            /** @constructor */ var Foo = function() {};
            var FooAlias = Foo;
            /** @type {number} */ FooAlias.prototype.bar = 3;
            /** @return {string} */ function foo() {
              return (new Foo()).bar; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testConstructorAlias5() {
    newTest()
        .addSource(
            """
            /** @constructor */ var Foo = function() {};
            /** @constructor */ var FooAlias = Foo;
            /** @return {FooAlias} */ function foo() {
              return new Foo(); }
            """)
        .run();
  }

  @Test
  public void testConstructorAlias6() {
    newTest()
        .addSource(
            """
            /** @constructor */ var Foo = function() {};
            /** @constructor */ var FooAlias = Foo;
            /** @return {Foo} */ function foo() {
              return new FooAlias(); }
            """)
        .run();
  }

  @Test
  public void testConstructorAlias7() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor */ goog.Foo = function() {};
            /** @constructor */ goog.FooAlias = goog.Foo;
            /** @return {number} */ function foo() {
              return new goog.FooAlias(); }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : goog.Foo
            required: number
            """)
        .run();
  }

  @Test
  public void testConstructorAlias8() {
    newTest()
        .addSource(
            """
            var goog = {};
            /**
             * @param {number} x
             * @constructor */
            goog.Foo = function(x) {};
            /**
             * @param {number} x
             * @constructor */
            goog.FooAlias = goog.Foo;
            /** @return {number} */ function foo() {
              return new goog.FooAlias(1); }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : goog.Foo
            required: number
            """)
        .run();
  }

  @Test
  public void testConstructorAlias9() {
    newTest()
        .addSource(
            """
            var goog = {};
            /**
             * @param {number} x
             * @constructor */
            goog.Foo = function(x) {};
            /** @constructor */ goog.FooAlias = goog.Foo;
            /** @return {number} */ function foo() {
              return new goog.FooAlias(1); }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : goog.Foo
            required: number
            """)
        .run();
  }

  @Test
  public void testConstructorAlias10() {
    newTest()
        .addSource(
            """
            /**
             * @param {number} x
             * @constructor */
            var Foo = function(x) {};
            /** @constructor */ var FooAlias = Foo;
            /** @return {number} */ function foo() {
              return new FooAlias(1); }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : Foo
            required: number
            """)
        .run();
  }

  @Test
  public void testConstructorAlias11() {
    newTest()
        .addSource(
            """
            /**
             * @param {number} x
             * @constructor */
            var Foo = function(x) {};
            /** @const */ var FooAlias = Foo;
            /** @const */ var FooAlias2 = FooAlias;
            /** @return {FooAlias2} */ function foo() {
              return 1; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: (Foo|null)
            """)
        .run();
  }

  @Test
  public void testConstructorAliasWithBadAnnotation1() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @record */ var Bar = Foo;
            """)
        .addDiagnostic("Annotation @record on Bar incompatible with aliased type.")
        .run();
  }

  @Test
  public void testConstructorAliasWithBadAnnotation2() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @interface */ var Bar = Foo;
            """)
        .addDiagnostic("Annotation @interface on Bar incompatible with aliased type.")
        .run();
  }

  @Test
  public void testConstructorAliasWithBadAnnotation3() {
    newTest()
        .addSource(
            """
            /** @interface */ function Foo() {}
            /** @record */ var Bar = Foo;
            """)
        .addDiagnostic("Annotation @record on Bar incompatible with aliased type.")
        .run();
  }

  @Test
  public void testConstructorAliasWithBadAnnotation4() {
    newTest()
        .addSource(
            """
            /** @interface */ function Foo() {}
            /** @constructor */ var Bar = Foo;
            """)
        .addDiagnostic("Annotation @constructor on Bar incompatible with aliased type.")
        .run();
  }

  @Test
  public void testConstAliasedTypeCastInferredCorrectly1() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {}
            /** @return {number} */
            Foo.prototype.foo = function() {};

            /** @const */ var FooAlias = Foo;

            var x = /** @type {!FooAlias} */ ({});
            var /** null */ n = x.foo();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: null
            """)
        .run();
  }

  @Test
  public void testConstAliasedTypeCastInferredCorrectly2() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {}
            /** @return {number} */
            Foo.prototype.foo = function() {};

            var ns = {};
            /** @const */ ns.FooAlias = Foo;

            var x = /** @type {!ns.FooAlias} */ ({});
            var /** null */ n = x.foo();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: null
            """)
        .run();
  }

  @Test
  public void testConstructorAliasedTypeCastInferredCorrectly() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {}
            /** @return {number} */
            Foo.prototype.foo = function() {};

            /** @constructor */ var FooAlias = Foo;

            var x = /** @type {!FooAlias} */ ({});
            var /** null */ n = x.foo();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: null
            """)
        .run();
  }

  @Test
  public void testAliasedTypedef1() {
    newTest()
        .addSource(
            """
            /** @typedef {string} */ var original;
            /** @const */ var alias = original;
            /** @type {alias} */ var x;
            """)
        .run();
  }

  @Test
  public void testAliasedTypedef2() {
    newTest()
        .addSource(
            """
            /** @const */ var ns = {};
            /** @typedef {string} */ var original;
            /** @const */ ns.alias = original;
            /** @type {ns.alias} */ var x;
            """)
        .run();
  }

  @Test
  public void testAliasedTypedef3() {
    newTest()
        .addSource(
            """
            /** @typedef {string} */ var original;
            /** @const */ var alias = original;
            /** @return {number} */ var f = function(/** alias */ x) { return x; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testAliasedTypedef4() {
    newTest()
        .addSource(
            """
            /** @const */ var ns = {};
            /** @typedef {string} */ var original;
            /** @const */ ns.alias = original;
            /** @return {number} */ var f = function(/** ns.alias */ x) { return x; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testAliasedNonTypedef() {
    newTest()
        .addSource(
            """
            /** @type {string} */ var notTypeName;
            /** @const */ var alias = notTypeName;
            /** @type {alias} */ var x;
            """)
        .addDiagnostic(
            """
            Bad type annotation. Unknown type alias
            It's possible that 'alias' refers to a value, not a type.
            """)
        .run();
  }

  @Test
  public void testConstStringKeyDoesntCrash() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {}
            var ns = {
              /** @const */ FooAlias: Foo
            };
            """)
        .run();
  }

  @Test
  public void testClosure7() {
    testClosureTypes(
        """
        /** @type {string|null|undefined} */ var a = foo();
        /** @type {number} */
        var b = goog.asserts.assert(a);
        """,
        """
        initializing variable
        found   : string
        required: number
        """);
  }

  private static final String PRIMITIVE_ASSERT_DEFS =
      """
      /**
       * @param {T} p
       * @return {U}
       * @template T
       * @template U :=
             mapunion(T, (E) => cond(sub(E, union('null', 'undefined')), none(), E)) =:
       * @closurePrimitive {asserts.truthy}
       */
      function assertTruthy(p) { return p; }
      /**
       * @param {*} p
       * @return {string}
       * @closurePrimitive {asserts.matchesReturn}
       */
      function assertString(p) { return /** @type {string} */ (p); }
      """;

  @Test
  public void testPrimitiveAssertTruthy_removesNullAndUndefinedFromString() {
    newTest()
        .addSource(
            PRIMITIVE_ASSERT_DEFS
                + """
                function f(/** ?string|undefined */ str) {
                  assertTruthy(str);
                  const /** number */ n = str;
                }
                """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testPrimitiveAssertString_narrowsAllTypeToString() {
    newTest()
        .addSource(
            PRIMITIVE_ASSERT_DEFS
                + """
                function f(/** * */ str) {
                  assertString(str);
                  const /** number */ n = str;
                }
                """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testReturn1() {
    newTest()
        .addSource("/**@return {void}*/function foo(){ return 3; }")
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: undefined
            """)
        .run();
  }

  @Test
  public void testReturn2() {
    newTest()
        .addSource("/**@return {!Number}*/function foo(){ return; }")
        .addDiagnostic(
            """
            inconsistent return type
            found   : undefined
            required: Number
            """)
        .run();
  }

  @Test
  public void testReturn3() {
    newTest()
        .addSource("/**@return {!Number}*/function foo(){ return 'abc'; }")
        .addDiagnostic(
            """
            inconsistent return type
            found   : string
            required: Number
            """)
        .run();
  }

  @Test
  public void testReturn4() {
    newTest()
        .addSource("/**@return {!Number}\n*/\n function a(){return new Array();}")
        .addDiagnostic(
            """
            inconsistent return type
            found   : Array<?>
            required: Number
            """)
        .run();
  }

  @Test
  public void testReturn5() {
    newTest()
        .addSource(
            """
            /**
             * @param {number} n
             * @constructor
             */
            function fn(n){ return }
            """)
        .run();
  }

  @Test
  public void testReturn6() {
    newTest()
        .addSource(
            """
            /** @param {number} opt_a
            @return {string} */
            function a(opt_a) { return opt_a }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (number|undefined)
            required: string
            """)
        .run();
  }

  @Test
  public void testReturn7() {
    newTest()
        .addSource(
            """
            /** @constructor */var A = function() {};
            /** @constructor */var B = function() {};
            /** @return {!B} */A.f = function() { return 1; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: B
            """)
        .run();
  }

  @Test
  public void testReturn8() {
    newTest()
        .addSource(
            """
            /** @constructor */var A = function() {};
            /** @constructor */var B = function() {};
            /** @return {!B} */A.prototype.f = function() { return 1; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: B
            """)
        .run();
  }

  @Test
  public void testInferredReturn1() {
    newTest()
        .addSource(
            """
            function f() {} /** @param {number} x */ function g(x) {}
            g(f());
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : undefined
            required: number
            """)
        .run();
  }

  @Test
  public void testInferredReturn2() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.bar = function() {};
            /** @param {number} x */ function g(x) {}
            g((new Foo()).bar());
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : undefined
            required: number
            """)
        .run();
  }

  @Test
  public void testInferredReturn3() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.bar = function() {};
            /** @constructor
             * @extends {Foo} */ function SubFoo() {}
            /** @return {number}
             * @override  */
            SubFoo.prototype.bar = function() { return 3; };
            """)
        .addDiagnostic(
            """
            mismatch of the bar property type and the type of the property it overrides from superclass Foo
            original: function(this:Foo): undefined
            override: function(this:SubFoo): number
            """)
        .run();
  }

  @Test
  public void testInferredReturn4() {
    // By design, this throws a warning. if you want global x to be
    // defined to some other type of function, then you need to declare it
    // as a greater type.
    newTest()
        .addSource(
            """
            var x = function() {};
            x = /** @type {function(): number} */ (function() { return 3; });
            """)
        .addDiagnostic(
            """
            assignment
            found   : function(): number
            required: function(): undefined
            """)
        .run();
  }

  @Test
  public void testInferredReturn5() {
    // If x is local, then the function type is not declared.
    newTest()
        .addSource(
            """
            /** @return {string} */
            function f() {
              var x = function() {};
              x = /** @type {function(): number} */ (function() { return 3; });
              return x();
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testInferredReturn6() {
    newTest()
        .addSource(
            """
            /** @return {string} */
            function f() {
              var x = function() {};
              if (f())
                x = /** @type {function(): number} */
                    (function() { return 3; });
              return x();
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (number|undefined)
            required: string
            """)
        .run();
  }

  @Test
  public void testInferredReturn7() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @param {number} x */ Foo.prototype.bar = function(x) {};
            Foo.prototype.bar = function(x) { return 3; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: undefined
            """)
        .run();
  }

  @Test
  public void testInferredReturn8() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @param {number} x */ Foo.prototype.bar = function(x) {};
            /** @constructor
             * @extends {Foo} */ function SubFoo() {}
            /** @override @param {number} x */ SubFoo.prototype.bar =
                function(x) { return 3; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: undefined
            """)
        .run();
  }

  @Test
  public void testInferredReturn9() {
    newTest()
        .addSource(
            """
            /** @param {function():string} x */
            function f(x) {}
            f(/** asdf */ function() { return 123; });
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testInferredParam1() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @param {number} x */ Foo.prototype.bar = function(x) {};
            /** @param {string} x */ function f(x) {}
            Foo.prototype.bar = function(y) { f(y); };
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testInferredParam2() {
    newTest()
        .addSource(
            """
            /** @param {string} x */ function f(x) {}
            /** @constructor */ function Foo() {}
            /** @param {number} x */ Foo.prototype.bar = function(x) {};
            /** @constructor
             * @extends {Foo} */ function SubFoo() {}
            /** @override @return {void} */ SubFoo.prototype.bar =
                function(x) { f(x); }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testInferredParam3() {
    newTest()
        .addSource(
            """
            /** @param {string} x */ function f(x) {}
            /** @constructor */ function Foo() {}
            /** @param {number=} x */ Foo.prototype.bar = function(x) {};
            /** @constructor
             * @extends {Foo} */ function SubFoo() {}
            /** @override @return {void} */ SubFoo.prototype.bar =
                function(x) { f(x); }; (new SubFoo()).bar();
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : (number|undefined)
            required: string
            """)
        .run();
  }

  @Test
  public void testInferredParam4() {
    newTest()
        .addSource(
            """
            /** @param {string} x */ function f(x) {}

            /** @constructor */
            function Foo() {}
            /** @param {...number} x */
            Foo.prototype.bar = function(x) {};

            /**
             * @constructor
             * @extends {Foo}
             */
            function SubFoo() {}
            /**
             * @override
             * @return {void}
             */
            SubFoo.prototype.bar = function(x) { f(x); };
            (new SubFoo()).bar();
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testInferredParam5() {
    newTest()
        .addSource(
            """
            /** @param {string} x */ function f(x) {}
            /** @constructor */ function Foo() {}
            /** @param {...number} x */ Foo.prototype.bar = function(x) {};
            /** @constructor
             * @extends {Foo} */ function SubFoo() {}
            /** @override @param {number=} x
             * @param {...number} y  */
            SubFoo.prototype.bar =
                function(x, y) { f(x); }; (new SubFoo()).bar();
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : (number|undefined)
            required: string
            """)
        .run();
  }

  @Test
  public void testInferredParam6() {
    newTest()
        .addSource(
            """
            /** @param {string} x */ function f(x) {}
            /** @constructor */ function Foo() {}
            /** @param {number=} x */ Foo.prototype.bar = function(x) {};
            /** @constructor
             * @extends {Foo} */ function SubFoo() {}
            /** @override @param {number=} x
             * @param {number=} y */
            SubFoo.prototype.bar =
                function(x, y) { f(y); };
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : (number|undefined)
            required: string
            """)
        .run();
  }

  @Test
  public void testInferredParam7() {
    newTest()
        .addSource(
            """
            /** @param {string} x */ function f(x) {}
            /** @type {function(number=,number=)} */
            var bar = function(x, y) { f(y); };
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : (number|undefined)
            required: string
            """)
        .run();
  }

  @Test
  public void testInferredParam8() {
    newTest()
        .addSource(
            """
            /** @param {function(string)} callback */
            function foo(callback) {}
            foo(/** random JSDoc */ function(x) { var /** number */ n = x; });
            """)
        .addDiagnostic(
            // Regarding `found`: type of "x" is inferred to be string
            """
            initializing variable
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testFunctionLiteralParamWithInlineJSDocNotInferred() {
    newTest()
        .addSource(
            """
            /** @param {function(string)} x */
            function f(x) {}
            f(function(/** number */ x) {});
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : function(number): undefined
            required: function(string): ?
            """)
        .run();
  }

  @Test
  public void testFunctionLiteralParamWithInlineJSDocNotInferredWithTwoParams() {
    newTest()
        .addSource(
            """
            /** @param {function(string, number)} x */
            function f(x) {}
            f((/** string */ str, num) => {
            // TODO(b/123583824): this should be a type mismatch warning.
            //    found   : number
            //    expected: string
            //  but the JSDoc on `str` blocks inference of `num`.
              const /** string */ newStr = num;
            });
            """)
        .run();
  }

  @Test
  public void testJSDocOnNoParensArrowFnParameterIsIgnored() {
    // This case is to document a potential bit of confusion: what happens when writing
    // inline-like JSDoc on a 'naked' arrow function parameter (no parentheses)
    // The actual behavior is that the JSDoc does nothing: this is equivalent to writing
    //   /** number */ function(x) { ...
    newTest()
        .addSource(
            """
            /** @param {function(string)} callback */
            function foo(callback) {}
            // The `/** number */` JSDoc is attached to the entire arrow  function, not the
            // parameter `x`
            foo(/** number */ x =>  { var /** number */ y = x; });
            """)
        .addDiagnostic(
            // Regarding `found`: type of "x" is inferred to be string
            """
            initializing variable
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testOverriddenParams1() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @param {...?} var_args */
            Foo.prototype.bar = function(var_args) {};
            /**
             * @constructor
             * @extends {Foo}
             */ function SubFoo() {}
            /**
             * @param {number} x
             * @override
             */
            SubFoo.prototype.bar = function(x) {};
            """)
        .run();
  }

  @Test
  public void testOverriddenParams2() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @type {function(...?)} */
            Foo.prototype.bar = function(var_args) {};
            /**
             * @constructor
             * @extends {Foo}
             */ function SubFoo() {}
            /**
             * @type {function(number)}
             * @override
             */
            SubFoo.prototype.bar = function(x) {};
            """)
        .run();
  }

  @Test
  public void testOverriddenParams3() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @param {...number} var_args */
            Foo.prototype.bar = function(var_args) { };
            /**
             * @constructor
             * @extends {Foo}
             */ function SubFoo() {}
            /**
             * @param {number} x
             * @override
             */
            SubFoo.prototype.bar = function(x) {};
            """)
        .addDiagnostic(
            """
            mismatch of the bar property type and the type of the property it overrides from superclass Foo
            original: function(this:Foo, ...number): undefined
            override: function(this:SubFoo, number): undefined
            """)
        .run();
  }

  @Test
  public void testOverriddenParams4() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @type {function(...number)} */
            Foo.prototype.bar = function(var_args) {};
            /**
             * @constructor
             * @extends {Foo}
             */ function SubFoo() {}
            /**
             * @type {function(number)}
             * @override
             */
            SubFoo.prototype.bar = function(x) {};
            """)
        .addDiagnostic(
            """
            mismatch of the bar property type and the type of the property it overrides from superclass Foo
            original: function(...number): ?
            override: function(number): ?
            """)
        .run();
  }

  @Test
  public void testOverriddenParams5() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @param {number} x */
            Foo.prototype.bar = function(x) { };
            /**
             * @constructor
             * @extends {Foo}
             */ function SubFoo() {}
            /**
             * @override
             */
            SubFoo.prototype.bar = function() {};
            (new SubFoo()).bar();
            """)
        .run();
  }

  @Test
  public void testOverriddenParams6() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @param {number} x */
            Foo.prototype.bar = function(x) { };
            /**
             * @constructor
             * @extends {Foo}
             */ function SubFoo() {}
            /**
             * @override
             */
            SubFoo.prototype.bar = function() {};
            (new SubFoo()).bar(true);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of SubFoo.prototype.bar does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testOverriddenParams7() {
    newTest()
        .addSource(
            """
            /** @interface */ function Foo() {}
            /** @param {number} x */
            Foo.prototype.bar = function(x) { };
            /**
             * @interface
             * @extends {Foo}
             */ function SubFoo() {}
            /**
             * @override
             */
            SubFoo.prototype.bar = function() {};
            var subFoo = /** @type {SubFoo} */ (null);
            subFoo.bar(true);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of SubFoo.prototype.bar does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testOverriddenParams8() {
    newTest()
        .addSource(
            """
            /** @constructor
             * @template T */ function Foo() {}
            /** @param {T} x */
            Foo.prototype.bar = function(x) { };
            /**
             * @constructor
             * @extends {Foo<string>}
             */ function SubFoo() {}
            /**
             * @param {number} x
             * @override
             */
            SubFoo.prototype.bar = function(x) {};
            """)
        .addDiagnostic(
"""
mismatch of the bar property type and the type of the property it overrides from superclass Foo
original: function(this:Foo, string): undefined
override: function(this:SubFoo, number): undefined
""")
        .run();
  }

  @Test
  public void testOverriddenReturn1() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @return {Object} */ Foo.prototype.bar =
                function() { return {}; };
            /** @constructor
             * @extends {Foo} */ function SubFoo() {}
            /** @return {SubFoo}
             * @override */ SubFoo.prototype.bar =
                function() { return new Foo(); }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : Foo
            required: (SubFoo|null)
            """)
        .run();
  }

  @Test
  public void testOverriddenReturn2() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @return {SubFoo} */ Foo.prototype.bar =
                function() { return new SubFoo(); };
            /** @constructor
             * @extends {Foo} */ function SubFoo() {}
            /** @return {Foo} x
             * @override */ SubFoo.prototype.bar =
                function() { return new SubFoo(); }
            """)
        .addDiagnostic(
            """
            mismatch of the bar property type and the type of the property it overrides from superclass Foo
            original: function(this:Foo): (SubFoo|null)
            override: function(this:SubFoo): (Foo|null)
            """)
        .run();
  }

  @Test
  public void testOverriddenReturn3() {
    newTest()
        .addSource(
            """
            /** @constructor
             * @template T */ function Foo() {}
            /** @return {T} */ Foo.prototype.bar =
                function() { return null; };
            /** @constructor
             * @extends {Foo<string>} */ function SubFoo() {}
            /** @override */ SubFoo.prototype.bar =
                function() { return 3; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testOverriddenReturn4() {
    newTest()
        .addSource(
            """
            /** @constructor
             * @template T */ function Foo() {}
            /** @return {T} */ Foo.prototype.bar =
                function() { return null; };
            /** @constructor
             * @extends {Foo<string>} */ function SubFoo() {}
            /** @return {number}
             * @override */ SubFoo.prototype.bar =
                function() { return 3; }
            """)
        .addDiagnostic(
            """
            mismatch of the bar property type and the type of the property it overrides from superclass Foo
            original: function(this:Foo): string
            override: function(this:SubFoo): number
            """)
        .run();
  }

  @Test
  public void testThis1() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor */goog.A = function(){};
            /** @return {number} */
            goog.A.prototype.n = function() { return this };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : goog.A
            required: number
            """)
        .run();
  }

  @Test
  public void testOverriddenProperty1() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @type {Object} */
            Foo.prototype.bar = {};
            /**
             * @constructor
             * @extends {Foo}
             */ function SubFoo() {}
            /**
             * @type {Array}
             * @override
             */
            SubFoo.prototype.bar = [];
            """)
        .run();
  }

  @Test
  public void testOverriddenProperty2() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {
              /** @type {Object} */
              this.bar = {};
            }
            /**
             * @constructor
             * @extends {Foo}
             */ function SubFoo() {}
            /**
             * @type {Array}
             * @override
             */
            SubFoo.prototype.bar = [];
            """)
        .run();
  }

  @Test
  public void testOverriddenProperty3() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {
            }
            /** @type {string} */ Foo.prototype.data;
            /**
             * @constructor
             * @extends {Foo}
             */ function SubFoo() {}
            /** @type {string|Object}
             * @override */
            SubFoo.prototype.data = null;
            """)
        .addDiagnostic(
            """
            mismatch of the data property type and the type of the property it overrides from superclass Foo
            original: string
            override: (Object|null|string)
            """)
        .run();
  }

  @Test
  public void testOverriddenProperty4() {
    // These properties aren't declared, so there should be no warning.
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.bar = null;
            /**
             * @constructor
             * @extends {Foo}
             */ function SubFoo() {}
            SubFoo.prototype.bar = 3;
            """)
        .run();
  }

  @Test
  public void testOverriddenProperty5() {
    // An override should be OK if the superclass property wasn't declared.
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.bar = null;
            /**
             * @constructor
             * @extends {Foo}
             */ function SubFoo() {}
            /** @override */ SubFoo.prototype.bar = 3;
            """)
        .run();
  }

  @Test
  public void testOverriddenProperty6() {
    // The override keyword shouldn't be necessary if the subclass property
    // is inferred.
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @type {?number} */ Foo.prototype.bar = null;
            /**
             * @constructor
             * @extends {Foo}
             */ function SubFoo() {}
            SubFoo.prototype.bar = 3;
            """)
        .run();
  }

  @Test
  public void testOverriddenPropertyWithUnknown() {
    // When overriding a declared property with a declared unknown property, we warn for a missing
    // override but not a type mismatch.
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @type {?number} */ Foo.prototype.bar = null;

            /**
             * @constructor
             * @extends {Foo}
             */
            function SubFoo() {}
            /** @type {?} */
            SubFoo.prototype.bar = 'not a number';
            """)
        .addDiagnostic(
            "property bar already defined on superclass Foo; use @override to override it")
        .run();
  }

  @Test
  public void testOverriddenPropertyWithUnknownInterfaceAncestor() {
    newTest()
        .addSource(
            """
            /** @interface @extends {Unknown}  */ function Foo() {}
            /** @type {string} */ Foo.prototype.data;
            /** @constructor @implements {Foo} */ function SubFoo() {}
            /** @type {string|Object} */
            SubFoo.prototype.data = null;
            """)
        .addDiagnostic("Bad type annotation. Unknown type Unknown")
        .addDiagnostic(
"""
mismatch of the data property on type SubFoo and the type of the property it overrides from interface Foo
original: string
override: (Object|null|string)
""")
        .run();
  }

  @Test
  public void testThis2() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor */goog.A = function(){
              this.foo = null;
            };
            /** @return {number} */
            goog.A.prototype.n = function() { return this.foo };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : null
            required: number
            """)
        .run();
  }

  @Test
  public void testThis3() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor */goog.A = function(){
              this.foo = null;
              this.foo = 5;
            };
            """)
        .run();
  }

  @Test
  public void testThis4() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor */goog.A = function(){
              /** @type {string?} */this.foo = null;
            };
            /** @return {number} */goog.A.prototype.n = function() {
              return this.foo };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (null|string)
            required: number
            """)
        .run();
  }

  @Test
  public void testThis5() {
    newTest()
        .addSource(
            """
            /** @this {Date}
            @return {number}*/function h() { return this }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : Date
            required: number
            """)
        .run();
  }

  @Test
  public void testThis6() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor
            @return {!Date} */
            goog.A = function(){ return this };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : goog.A
            required: Date
            """)
        .run();
  }

  @Test
  public void testThis7() {
    newTest()
        .addSource(
            """
            /** @constructor */function A(){};
            /** @return {number} */A.prototype.n = function() { return this };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : A
            required: number
            """)
        .run();
  }

  @Test
  public void testThis8() {
    newTest()
        .addSource(
            """
            /** @constructor */function A(){
              /** @type {string?} */this.foo = null;
            };
            /** @return {number} */A.prototype.n = function() {
              return this.foo };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (null|string)
            required: number
            """)
        .run();
  }

  @Test
  public void testTypeOfThis_inStaticMethod_onEs5Ctor_isUnknown() {
    newTest()
        .addSource(
            """
            /** @constructor */function A(){};
            A.foo = 3;
            /** @return {string} */ A.bar = function() { return this.foo; };
            """)
        .run();
  }

  @Test
  public void testThis10() {
    // In A.bar, the type of {@code this} is inferred from the @this tag.
    newTest()
        .addSource(
            """
            /** @constructor */function A(){};
            A.prototype.foo = 3;
            /** @this {A}
            @return {string} */
            A.bar = function() { return this.foo; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testThis11() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ function f(x) {}
            /** @constructor */ function Ctor() {
              /** @this {Date} */
              this.method = function() {
                f(this);
              };
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : Date
            required: number
            """)
        .run();
  }

  @Test
  public void testThis12() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ function f(x) {}
            /** @constructor */ function Ctor() {}
            Ctor.prototype['method'] = function() {
              f(this);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : Ctor
            required: number
            """)
        .run();
  }

  @Test
  public void testThis13() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ function f(x) {}
            /** @constructor */ function Ctor() {}
            Ctor.prototype = {
              method: function() {
                f(this);
              }
            };
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : Ctor
            required: number
            """)
        .run();
  }

  @Test
  public void testThis14() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ function f(x) {}
            f(this.Object);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : (typeof Object)
            required: number
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testThisType_whichTypesSupport_freeCallsOfFunction() {
    newTest().addSource("/** @type {function(this:*)} */ function f() {}; f();").run();
    newTest().addSource("/** @type {function(this:?)} */ function f() {}; f();").run();
    newTest().addSource("/** @type {function(this:undefined)} */ function f() {}; f();").run();
    newTest().addSource("/** @type {function(this:Object)} */ function f() {}; f();").run();
  }

  @Test
  public void testThisTypeOfFunction2() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            /** @type {function(this:F)} */ function f() {}
            f();
            """)
        .addDiagnostic("\"function(this:F): ?\" must be called with a \"this\" type")
        .run();
  }

  @Test
  public void testThisTypeOfFunction3() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            F.prototype.bar = function() {};
            var f = (new F()).bar; f();
            """)
        .addDiagnostic("\"function(this:F): undefined\" must be called with a \"this\" type")
        .run();
  }

  @Test
  public void testThisTypeOfFunction4() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().addObject().build())
        .addSource(
            """
            /** @constructor */ function F() {}
            F.prototype.moveTo = function(x, y) {};
            F.prototype.lineTo = function(x, y) {};
            function demo() {
              var path = new F();
              var points = [[1,1], [2,2]];
              for (var i = 0; i < points.length; i++) {
                (i == 0 ? path.moveTo : path.lineTo)(points[i][0], points[i][1]);
              }
            }
            """)
        .addDiagnostic("\"function(this:F, ?, ?): undefined\" must be called with a \"this\" type")
        .run();
  }

  @Test
  public void testThisTypeOfFunction5() {
    newTest()
        .addSource(
            """
            /** @type {function(this:number)} */
            function f() {
              var /** number */ n = this;
            }
            """)
        .run();
  }

  @Test
  public void testGlobalThis1() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Window() {}
            /** @param {string} msg */
            Window.prototype.alert = function(msg) {};
            this.alert(3);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Window.prototype.alert does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testGlobalThis2a() {
    // this.alert = 3 doesn't count as a declaration, so this is a warning.
    newTest()
        .addSource(
            """
            /** @constructor */ function Bindow() {}
            /** @param {string} msg */
            Bindow.prototype.alert = function(msg) {};
            this.alert = 3;
            (new Bindow()).alert(this.alert)
            """)
        .addDiagnostic("Property alert never defined on global this")
        .addDiagnostic("Property alert never defined on global this")
        .run();
  }

  @Test
  public void testGlobalThis2b() {
    // Only reported if strict property checks are enabled
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @constructor */ function Bindow() {}
            /** @param {string} msg */
            Bindow.prototype.alert = function(msg) {};
            this.alert = 3;
            (new Bindow()).alert(this.alert)
            """)
        .run();
  }

  @Test
  public void testGlobalThis2c() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @constructor */ function Bindow() {}
            /** @param {string} msg */
            Bindow.prototype.alert = function(msg) {};
            /** @return {number} */ this.alert = function() { return 3; };
            (new Bindow()).alert(this.alert())
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Bindow.prototype.alert does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testGlobalThis3() {
    newTest()
        .addSource(
            """
            /** @param {string} msg */
            function alert(msg) {};
            this.alert(3);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of global this.alert does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testGlobalThis4() {
    newTest()
        .addSource(
            """
            /** @param {string} msg */
            var alert = function(msg) {};
            this.alert(3);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of global this.alert does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testGlobalThis5() {
    newTest()
        .addSource(
            """
            function f() {
              /** @param {string} msg */
              var alert = function(msg) {};
            }
            this.alert(3);
            """)
        .addDiagnostic("Property alert never defined on global this")
        .run();
  }

  @Test
  public void testGlobalThis6() {
    newTest()
        .addSource(
            """
            /** @param {string} msg */
            var alert = function(msg) {};
            var x = 3;
            x = 'msg';
            this.alert(this.x);
            """)
        .run();
  }

  @Test
  public void testGlobalThis7() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Window() {}
            /** @param {Window} msg */
            var foo = function(msg) {};
            foo(this);
            """)
        .run();
  }

  @Test
  public void testGlobalThis8() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Window() {}
            /** @param {number} msg */
            var foo = function(msg) {};
            foo(this);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of foo does not match formal parameter
            found   : global this
            required: number
            """)
        .run();
  }

  @Test
  public void testGlobalThis9() {
    newTest()
        .addSource(
            """
            // Window is not marked as a constructor, so the
            // inheritance doesn't happen.
            function Window() {}
            Window.prototype.alert = function() {};
            this.alert();
            """)
        .addDiagnostic("Property alert never defined on global this")
        .run();
  }

  @Test
  public void testGlobalThisDoesNotIncludeVarsDeclaredWithConst() {
    newTest()
        .addSource(
            """
            /** @param {string} msg */
            const alert = function(msg) {};
            this.alert('boo');
            """)
        .addDiagnostic("Property alert never defined on global this")
        .run();
  }

  @Test
  public void testGlobalThisDoesNotIncludeVarsDeclaredWithLet() {
    newTest()
        .addSource(
            """
            /** @param {string} msg */
            let alert = function(msg) {};
            this.alert('boo');
            """)
        .addDiagnostic("Property alert never defined on global this")
        .run();
  }

  @Test
  public void testControlFlowRestrictsType1() {
    newTest()
        .addSource(
            """
            /** @return {String?} */ function f() { return null; }
            /** @type {String?} */ var a = f();
            /** @type {String} */ var b = new String('foo');
            /** @type {null} */ var c = null;
            if (a) {
              b = a;
            } else {
              c = a;
            }
            """)
        .run();
  }

  @Test
  public void testControlFlowRestrictsType2() {
    newTest()
        .addSource(
            """
            /** @return {(string|null)} */ function f() { return null; }
            /** @type {(string|null)} */ var a = f();
            /** @type {string} */ var b = 'foo';
            /** @type {null} */ var c = null;
            if (a) {
              b = a;
            } else {
              c = a;
            }
            """)
        .addDiagnostic(
            """
            assignment
            found   : (null|string)
            required: null
            """)
        .run();
  }

  @Test
  public void testControlFlowRestrictsType3() {
    newTest()
        .addSource(
            """
            /** @type {(string|void)} */
            var a;
            /** @type {string} */
            var b = 'foo';
            if (a) {
              b = a;
            }
            """)
        .run();
  }

  @Test
  public void testControlFlowRestrictsType4() {
    newTest()
        .addSource(
            """
            /** @param {string} a */ function f(a){}
            /** @type {(string|undefined)} */ var a;
            a && f(a);
            """)
        .run();
  }

  @Test
  public void testControlFlowRestrictsType5() {
    newTest()
        .addSource(
            """
            /** @param {undefined} a */ function f(a){}
            /** @type {(!Array|undefined)} */ var a;
            a || f(a);
            """)
        .run();
  }

  @Test
  public void testControlFlowRestrictsType6() {
    newTest()
        .addSource(
            """
            /** @param {undefined} x */ function f(x) {}
            /** @type {(string|undefined)} */ var a;
            a && f(a);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : string
            required: undefined
            """)
        .run();
  }

  @Test
  public void testControlFlowRestrictsType7() {
    newTest()
        .addSource(
            """
            /** @param {undefined} x */ function f(x) {}
            /** @type {(string|undefined)} */ var a;
            a && f(a);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : string
            required: undefined
            """)
        .run();
  }

  @Test
  public void testControlFlowRestrictsType8() {
    newTest()
        .addSource(
            """
            /** @param {undefined} a */ function f(a){}
            /** @type {(!Array|undefined)} */ var a;
            if (a || f(a)) {}
            """)
        .run();
  }

  @Test
  public void testControlFlowRestrictsType9() {
    newTest()
        .addSource(
            """
            /** @param {number?} x
             * @return {number}*/
            var f = function(x) {
            if (!x || x == 1) { return 1; } else { return x; }
            };
            """)
        .run();
  }

  @Test
  public void testControlFlowRestrictsType10() {
    // We should correctly infer that y will be (null|{}) because
    // the loop wraps around.
    newTest()
        .addSource(
            """
            /** @param {number} x */ function f(x) {}
            function g() {
              var y = null;
              for (var i = 0; i < 10; i++) {
                f(y);
                if (y != null) {
                  // y is None the first time it goes through this branch
                } else {
                  y = {};
                }
              }
            };
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : (null|{})
            required: number
            """)
        .run();
  }

  @Test
  public void testControlFlowRestrictsType11() {
    newTest()
        .addSource(
            """
            /** @param {boolean} x */ function f(x) {}
            function g() {
              var y = null;
              if (y != null) {
                for (var i = 0; i < 10; i++) {
                  f(y);
                }
              }
            };
            """)
        .addDiagnostic(
            """
            condition always evaluates to false
            left : null
            right: null
            """)
        .run();
  }

  @Test
  public void testSwitchCase_primitiveDoesNotAutobox() {
    newTest()
        .addSource(
            """
            /** @type {!String} */
            var a = new String('foo');
            switch (a) { case 'A': }
            """)
        .addDiagnostic(
            """
            case expression doesn't match switch
            found   : string
            required: String
            """)
        .run();
  }

  @Test
  public void testSwitchCase_unknownSwitchExprMatchesAnyCase() {
    newTest()
        .addSource(
            """
            var a = unknown;
            switch (a) { case 'A':break; case null:break; }
            """)
        .run();
  }

  @Test
  public void testSwitchCase_doesNotAutoboxStringToMatchNullableUnion() {
    newTest()
        .addSource(
            """
            /** @type {?String} */
            var a = unknown;
            switch (a) { case 'A':break; case null:break; }
            """)
        .addDiagnostic(
            """
            case expression doesn't match switch
            found   : string
            required: (String|null)
            """)
        .run();
  }

  @Test
  public void testSwitchCase_doesNotAutoboxNumberToMatchNullableUnion() {
    newTest()
        .addSource(
            """
            /** @type {?Number} */
            var a = unknown;
            switch (a) { case 5:break; case null:break; }
            """)
        .addDiagnostic(
            """
            case expression doesn't match switch
            found   : number
            required: (Number|null)
            """)
        .run();
  }

  @Test
  public void testSwitchCase7() {
    // This really tests the inference inside the case.
    newTest()
        .addSource(
            """
            /**
             * @param {number} x
             * @return {number}
             */
            function g(x) { return 5; }
            function f() {
              var x = {};
              x.foo = '3';
              switch (3) { case g(x.foo): return 3; }
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testSwitchCase8() {
    // This really tests the inference inside the switch clause.
    newTest()
        .addSource(
            """
            /**
             * @param {number} x
             * @return {number}
             */
            function g(x) { return 5; }
            function f() {
              var x = {};
              x.foo = '3';
              switch (g(x.foo)) { case 3: return 3; }
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testSwitchCase_allowsStructuralMatching() {
    newTest()
        .addSource(
            """
            /** @record */
            class R {
              constructor() {
                /** @type {string} */
                this.str;
              }
            }
            /** @record */
            class S {
              constructor() {
                /** @type {string} */
                this.str;
                /** @type {number} */
                this.num;
              }
            }
             /**
             * @param {!R} r
             * @param {!S} s
             */
            function f(r, s) {
              switch (r) {
                case s:
                  return true;
              }
            }
            """)
        .run();
  }

  @Test
  public void testImplicitCast1() {
    newTest()
        .addExterns(
            """
            /** @constructor */ function Element() {};
            /** @type {string}
              * @implicitCast */
            Element.prototype.innerHTML;
            """)
        .addSource("(new Element).innerHTML = new Array();")
        .run();
  }

  @Test
  public void testImplicitCast2() {
    newTest()
        .addExterns(
            """
            /** @constructor */ function Element() {};
            /**
             * @type {string}
             * @implicitCast
             */
            Element.prototype.innerHTML;
            """)
        .addSource(
            """
            /** @constructor */ function C(e) {
              /** @type {Element} */ this.el = e;
            }
            C.prototype.method = function() {
              this.el.innerHTML = new Array();
            };
            """)
        .run();
  }

  @Test
  public void testImplicitCast3() {
    newTest()
        .addExterns(
            """
            /** @constructor */ function Element() {};
            /**
             * @type {string}
             * @implicitCast
             */
            Element.prototype.innerHTML;
            """)
        .addSource(
            """
            /** @param {?Element} element
             * @param {string|number} text
             */
            function f(element, text) {
              element.innerHTML = text;
            }
            """)
        .run();
  }

  @Test
  public void testImplicitCastSubclassAccess() {
    newTest()
        .addExterns(
            """
            /** @constructor */ function Element() {};
            /** @type {string}
              * @implicitCast */
            Element.prototype.innerHTML;
            /** @constructor
             * @extends Element */
            function DIVElement() {};
            """)
        .addSource("(new DIVElement).innerHTML = new Array();")
        .run();
  }

  @Test
  public void testImplicitCastNotInExterns() {
    // We issue a warning in CheckJSDoc for @implicitCast not in externs
    newTest()
        .addSource(
            """
            /** @constructor */ function Element() {};
            /**
             * @type {string}
             * @implicitCast
             */
            Element.prototype.innerHTML;
            (new Element).innerHTML = new Array();
            """)
        .addDiagnostic(
            """
            assignment to property innerHTML of Element
            found   : Array<?>
            required: string
            """)
        .run();
  }

  @Test
  public void testNumberNode() {
    Node n = IR.number(0);
    typeCheck(IR.exprResult(n));

    assertTypeEquals(getNativeNumberType(), n.getJSType());
  }

  @Test
  public void testStringNode() {
    Node n = IR.string("hello");
    typeCheck(IR.exprResult(n));

    assertTypeEquals(getNativeStringType(), n.getJSType());
  }

  @Test
  public void testBooleanNodeTrue() {
    Node trueNode = IR.trueNode();
    typeCheck(IR.exprResult(trueNode));

    assertTypeEquals(getNativeBooleanType(), trueNode.getJSType());
  }

  @Test
  public void testBooleanNodeFalse() {
    Node falseNode = IR.falseNode();
    typeCheck(IR.exprResult(falseNode));

    assertTypeEquals(getNativeBooleanType(), falseNode.getJSType());
  }

  @Test
  public void testUndefinedNode() {
    Node p = new Node(Token.ADD);
    Node n = Node.newString(Token.NAME, "undefined");
    p.addChildToBack(n);
    p.addChildToBack(Node.newNumber(5));
    typeCheck(IR.exprResult(p));

    assertTypeEquals(getNativeVoidType(), n.getJSType());
  }

  @Test
  public void testNumberAutoboxing() {
    newTest()
        .addSource("/** @type {Number} */var a = 4;")
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: (Number|null)
            """)
        .run();
  }

  @Test
  public void testNumberUnboxing() {
    newTest()
        .addSource("/** @type {number} */var a = new Number(4);")
        .addDiagnostic(
            """
            initializing variable
            found   : Number
            required: number
            """)
        .run();
  }

  @Test
  public void testStringAutoboxing() {
    newTest()
        .addSource("/** @type {String} */var a = 'hello';")
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: (String|null)
            """)
        .run();
  }

  @Test
  public void testStringUnboxing() {
    newTest()
        .addSource("/** @type {string} */var a = new String('hello');")
        .addDiagnostic(
            """
            initializing variable
            found   : String
            required: string
            """)
        .run();
  }

  @Test
  public void testBooleanAutoboxing() {
    newTest()
        .addSource("/** @type {Boolean} */var a = true;")
        .addDiagnostic(
            """
            initializing variable
            found   : boolean
            required: (Boolean|null)
            """)
        .run();
  }

  @Test
  public void testBooleanUnboxing() {
    newTest()
        .addSource("/** @type {boolean} */var a = new Boolean(false);")
        .addDiagnostic(
            """
            initializing variable
            found   : Boolean
            required: boolean
            """)
        .run();
  }

  @Test
  public void testIIFE1() {
    newTest()
        .addSource(
            """
            var namespace = {};
            /** @type {number} */ namespace.prop = 3;
            (function(ns) {
              ns.prop = true;
            })(namespace);
            """)
        .addDiagnostic(
            """
            assignment to property prop of ns
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIIFE2() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            (function(ctor) {
              /** @type {boolean} */ ctor.prop = true;
            })(Foo);
            /** @return {number} */ function f() { return Foo.prop; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIIFE3() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            (function(ctor) {
              /** @type {boolean} */ ctor.prop = true;
            })(Foo);
            /** @param {number} x */ function f(x) {}
            f(Foo.prop);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIIFE4() {
    newTest()
        .addSource(
            """
            /** @const */ var namespace = {};
            (function(ns) {
              /**
               * @constructor
               * @param {number} x
               */
               ns.Ctor = function(x) {};
            })(namespace);
            new namespace.Ctor(true);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of namespace.Ctor does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIIFE5() {
    // TODO(nicksantos): This behavior is currently incorrect.
    // To handle this case properly, we'll need to change how we handle
    // type resolution.
    newTest()
        .addSource(
            """
            /** @const */ var namespace = {};
            (function(ns) {
              /**
               * @constructor
               */
               ns.Ctor = function() {};
               /** @type {boolean} */ ns.Ctor.prototype.bar = true;
            })(namespace);
            /** @param {namespace.Ctor} x
              * @return {number} */ function f(x) { return x.bar; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testNotIIFE1() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ function f(x) {}
            /** @param {...?} x */ function g(x) {}
            g(function(y) { f(y); }, true);
            """)
        .run();
  }

  @Test
  public void testEnums() {
    newTest()
        .addSource(
            """
            var outer = function() {
              /** @enum {number} */
              var Level = {
                NONE: 0,
              };
              /** @type {!Level} */
              var l = Level.NONE;
            }
            """)
        .run();
  }

  @Test
  public void testStrictInterfaceCheck() {
    newTest()
        .addSource(
            """
            /** @interface */
            function EventTarget() {}
            /** @constructor
             * @implements {EventTarget} */
            function Node() {}
            /** @type {number} */ Node.prototype.index;
            /** @param {EventTarget} x
             * @return {string} */
            function foo(x) { return x.index; }
            """)
        .addDiagnostic("Property index never defined on EventTarget")
        .run();
  }

  @Test
  public void testTemplateSubtyping_0() {
    // TODO(b/145145406): This is testing that things work despite this bug.
    newTest()
        .addSource(
            // IFoo is a NamedType here.
            """
            /** @implements {IFoo<number>} */
            class Foo { }

            /**
             * @template T
             * @interface
             */
            class IFoo { }

            const /** !IFoo<number> */ x = new Foo();
            """)
        .run();
  }

  @Test
  public void testTemplateSubtyping_1() {
    // TOOD(b/139230800): This is testing things work despite this bug.
    newTest()
        .addSource(
            """
            /**
             * @template T
             * @interface
             */
            class IFoo { }

            /** @implements {IFoo<number>} */
            class FooA { }

            /** @implements {IFoo<string>} */
            class FooB extends FooA { }

            const /** !IFoo<number> */ x = new FooB();
            const /** !IFoo<string> */ y = new FooB();
            """)
        .run();
  }

  @Test
  public void testTypedefBeforeUse() {
    newTest()
        .addSource(
            """
            /** @typedef {Object<string, number>} */
            var map;
            /** @param {(map|function())} isResult */
            var f = function(isResult) {
                while (true)
                    isResult['t'];
            };
            """)
        .run();
  }

  @Test
  public void testScopedConstructors1() {
    newTest()
        .addSource(
            """
            function foo1() {
              /** @constructor */ function Bar() {
                /** @type {number} */ this.x = 3;
              }
            }
            function foo2() {
              /** @constructor */ function Bar() {
                /** @type {string} */ this.x = 'y';
              }
              /** * @param {Bar} b
               * @return {number}
               */
              function baz(b) { return b.x; }
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testScopedConstructors2() {
    newTest()
        .addSource(
            """
            /** @param {Function} f */
            function foo1(f) {
              /** @param {Function} g */
              f.prototype.bar = function(g) {};
            }
            """)
        .run();
  }

  @Test
  public void testQualifiedNameInference1() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @type {number?} */ Foo.prototype.bar = null;
            /** @type {number?} */ Foo.prototype.baz = null;
            /** @param {Foo} foo */
            function f(foo) {
              while (true) {
                if (!foo.baz) break;
                foo.bar = null;
              }
              // Tests a bug where this condition always evaluated to true.
              return foo.bar == null;
            }
            """)
        .run();
  }

  @Test
  public void testQualifiedNameInference2() {
    newTest()
        .addSource(
            """
            var x = {};
            x.y = c;
            function f(a, b) {
              if (a) {
                if (b)
                  x.y = 2;
                else
                  x.y = 1;
              }
              return x.y == null;
            }
            """)
        .run();
  }

  @Test
  public void testQualifiedNameInference3() {
    newTest()
        .addSource(
            """
            var x = {};
            x.y = c;
            function f(a, b) {
              if (a) {
                if (b)
                  x.y = 2;
                else
                  x.y = 1;
              }
              return x.y == null;
            } function g() { x.y = null; }
            """)
        .run();
  }

  @Test
  public void testQualifiedNameInference4() {
    newTest()
        .addSource(
            """
            /** @param {string} x */ function f(x) {}
            /**
             * @param {?string} x
             * @constructor
             */
            function Foo(x) { this.x_ = x; }
            Foo.prototype.bar = function() {
              if (this.x_) { f(this.x_); }
            };
            """)
        .run();
  }

  @Test
  public void testQualifiedNameInference5() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            var ns = {};
            (function() {
                /** @param {number} x */ ns.foo = function(x) {}; })();
            (function() { ns.foo(true); })();
            """)
        .addDiagnostic(
            """
            actual parameter 1 of ns.foo does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testQualifiedNameInference6() {
    newTest()
        .addSource(
            """
            /** @const */ var ns = {};
            /** @param {number} x */ ns.foo = function(x) {};
            (function() {
                ns.foo = function(x) {};
                ns.foo(true);
            })();
            """)
        .addDiagnostic(
            """
            actual parameter 1 of ns.foo does not match formal parameter
            found   : boolean
            required: number\
            """)
        .run();
  }

  @Test
  public void testQualifiedNameInference7() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            var ns = {};
            (function() {
              /** @constructor
              * @param {number} x */
              ns.Foo = function(x) {};
              /** @param {ns.Foo} x */ function f(x) {}
              f(new ns.Foo(true));
            })();
            """)
        .addDiagnostic(
            """
            actual parameter 1 of ns.Foo does not match formal parameter
            found   : boolean
            required: number\
            """)
        .run();
  }

  @Test
  public void testQualifiedNameInference8() {
    testClosureTypesMultipleWarnings(
        """
        var ns = {};
        (function() {
          /** @constructor
          * @param {number} x */
          ns.Foo = function(x) {};
        })();
        /** @param {ns.Foo} x */ function f(x) {}
        f(new ns.Foo(true));
        """,
        ImmutableList.of(
            "Property Foo never defined on ns",
            """
            actual parameter 1 of ns.Foo does not match formal parameter
            found   : boolean
            required: number\
            """));
  }

  @Test
  public void testQualifiedNameInference9() {
    newTest()
        .addSource(
            """
            var ns = {};
            ns.ns2 = {};
            (function() {
              /** @constructor
              * @param {number} x */
              ns.ns2.Foo = function(x) {};
              /** @param {ns.ns2.Foo} x */ function f(x) {}
              f(new ns.ns2.Foo(true));
            })();
            """)
        .addDiagnostic(
            """
            actual parameter 1 of ns.ns2.Foo does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testQualifiedNameInference10() {
    newTest()
        .addSource(
            """
            var ns = {};
            ns.ns2 = {};
            (function() {
              /** @interface */
              ns.ns2.Foo = function() {};
              /** @constructor
              * @implements {ns.ns2.Foo} */
              function F() {}
              (new F());
            })();
            """)
        .run();
  }

  @Test
  public void testQualifiedNameInference11() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @constructor */ function Foo() {}
            function f() {
              var x = new Foo();
              x.onload = function() {
                x.onload = null;
              };
            }
            """)
        .run();
  }

  @Test
  public void testQualifiedNameInference12() {
    // We should be able to tell that the two 'this' properties
    // are different.
    newTest()
        .addSource(
            """
            /** @param {function(this:Object)} x */ function f(x) {}
            /** @constructor */ function Foo() {
              /** @type {number} */ this.bar = 3;
              f(function() { this.bar = true; });
            }
            """)
        // Checking loose property behavior
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .run();
  }

  @Test
  public void testQualifiedNameInference13() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @constructor */ function Foo() {}
            function f(z) {
              var x = new Foo();
              if (z) {
                x.onload = function() {};
              } else {
                x.onload = null;
              };
            }
            """)
        .run();
  }

  @Test
  public void testQualifiedNameInference14() {
    // Unconditional blocks don't cause functions to be treated as inferred.
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @constructor */ function Foo() {}
            function f(z) {
              var x = new Foo();
              {
                x.onload = function() {};
              }
              {
                x.onload = null;
              };
            }
            """)
        .addDiagnostic(
            """
            assignment
            found   : null
            required: function(): undefined
            """)
        .run();
  }

  @Test
  public void testScopeQualifiedNamesOnThis() {
    // Ensure that we don't flow-scope qualified names on 'this' too broadly.
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {
              /** @type {!Bar} */
              this.baz = new Bar();
            }
            Foo.prototype.foo = function() {
              this.baz.bar();
            };
            /** @constructor */ function Bar() {
              /** @type {!Foo} */
              this.baz = new Foo();
            }
            Bar.prototype.bar = function() {
              this.baz.foo();
            };
            """)
        .run();
  }

  @Test
  public void testSheqRefinedScope() {
    Node n =
        parseAndTypeCheck(
            """
            /** @constructor */function A() {}
            /** @constructor\s
             @extends A */ function B() {}
            /** @return {number} */
            B.prototype.p = function() { return 1; }
            /** @param {A} a
             @param {B} b */
            function f(a, b) {
              b.p();
              if (a === b) {
                b.p();
              }
            }
            """);
    Node nodeC =
        n.getLastChild().getLastChild().getLastChild().getLastChild().getLastChild().getLastChild();
    JSType typeC = nodeC.getJSType();
    assertThat(typeC.isNumber()).isTrue();

    Node nodeB = nodeC.getFirstFirstChild();
    JSType typeB = nodeB.getJSType();
    assertThat(typeB.toString()).isEqualTo("B");
  }

  @Test
  public void testAssignToUntypedVariable() {
    Node n = parseAndTypeCheck("var z; z = 1;");

    Node assign = n.getLastChild().getFirstChild();
    Node node = assign.getFirstChild();
    assertThat(node.getJSType().isUnknownType()).isFalse();
    assertThat(node.getJSType().toString()).isEqualTo("number");
  }

  @Test
  public void testAssignToUntypedProperty() {
    Node n =
        parseAndTypeCheck(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.a = 1;
            (new Foo).a;
            """);

    Node node = n.getLastChild().getFirstChild();
    assertThat(node.getJSType().isUnknownType()).isFalse();
    assertThat(node.getJSType().isNumber()).isTrue();
  }

  @Test
  public void testNew1() {
    newTest()
        .addSource("new 4")
        .addDiagnostic("cannot instantiate non-constructor, found type: number")
        .run();
    newTest()
        .addSource("new 4")
        .addDiagnostic("cannot instantiate non-constructor, found type: number")
        .run();
  }

  @Test
  public void testNew2() {
    newTest()
        .addSource("var Math = {}; new Math()")
        .addDiagnostic("cannot instantiate non-constructor, found type: {}")
        .run();
  }

  @Test
  public void testNew3() {
    newTest().addSource("new Date()").run();
  }

  @Test
  public void testNew4() {
    newTest().addSource("/** @constructor */function A(){}; new A();").run();
  }

  @Test
  public void testNew5() {
    newTest()
        .addSource("function A(){}; new A();")
        .addDiagnostic("cannot instantiate non-constructor, found type: function(): undefined")
        .run();
  }

  @Test
  public void testNew6() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope(
            """
            /** @constructor */function A(){};
            var a = new A();
            """);

    JSType aType = p.scope.getVar("a").getType();
    assertThat(aType).isInstanceOf(ObjectType.class);
    ObjectType aObjectType = (ObjectType) aType;
    assertThat(aObjectType.getConstructor().getReferenceName()).isEqualTo("A");
  }

  @Test
  public void testNew7() {
    newTest()
        .addSource(
            """
            /** @param {Function} opt_constructor */
            function foo(opt_constructor) {
            if (opt_constructor) { new opt_constructor; }
            }
            """)
        .run();
  }

  @Test
  public void testNew8() {
    newTest()
        .addSource(
            """
            /** @param {Function} opt_constructor */
            function foo(opt_constructor) {
            new opt_constructor;
            }
            """)
        .run();
  }

  @Test
  public void testNew9() {
    newTest()
        .addSource(
            """
            /** @param {Function} opt_constructor */
            function foo(opt_constructor) {
            new (opt_constructor || Array);
            }
            """)
        .run();
  }

  @Test
  public void testNew10() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @param {Function} opt_constructor */
            goog.Foo = function(opt_constructor) {
            new (opt_constructor || Array);
            }
            """)
        .run();
  }

  @Test
  public void testNew11() {
    newTest()
        .addSource(
            """
            /** @param {Function} c1 */
            function f(c1) {
              var c2 = function(){};
              c1.prototype = new c2;
            }
            """)
        .addDiagnostic("cannot instantiate non-constructor, found type: function(): undefined")
        .run();
  }

  @Test
  public void testNew12() {
    TypeCheckResult p = parseAndTypeCheckWithScope("var a = new Array();");
    TypedVar a = p.scope.getVar("a");

    assertTypeEquals(getNativeArrayType(), a.getType());
  }

  @Test
  public void testNew13() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope(
            """
            /** @constructor */function FooBar(){};
            var a = new FooBar();
            """);
    TypedVar a = p.scope.getVar("a");

    assertThat(a.getType()).isInstanceOf(ObjectType.class);
    assertThat(a.getType().toString()).isEqualTo("FooBar");
  }

  @Test
  public void testNew14() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope(
            """
            /** @constructor */var FooBar = function(){};
            var a = new FooBar();
            """);
    TypedVar a = p.scope.getVar("a");

    assertThat(a.getType()).isInstanceOf(ObjectType.class);
    assertThat(a.getType().toString()).isEqualTo("FooBar");
  }

  @Test
  public void testNew15() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope(
            """
            var goog = {};
            /** @constructor */goog.A = function(){};
            var a = new goog.A();
            """);
    TypedVar a = p.scope.getVar("a");

    assertThat(a.getType()).isInstanceOf(ObjectType.class);
    assertThat(a.getType().toString()).isEqualTo("goog.A");
  }

  @Test
  public void testNew16() {
    newTest()
        .addSource(
            """
            /** * @param {string} x
             * @constructor
             */
            function Foo(x) {}
            function g() { new Foo(1); }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Foo does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testNew17() {
    newTest()
        .addSource("var goog = {}; goog.x = 3; new goog.x")
        .addDiagnostic("cannot instantiate non-constructor, found type: number")
        .run();
  }

  @Test
  public void testNew18() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor */ goog.F = function() {};
            /** @constructor */ goog.G = goog.F;
            """)
        .run();
  }

  @Test
  public void testNew19() {
    newTest()
        .addSource("/** @constructor @abstract */ var Foo = function() {}; var foo = new Foo();")
        .addDiagnostic(INSTANTIATE_ABSTRACT_CLASS)
        .run();
  }

  @Test
  public void testNew20() {
    newTest()
        .addSource(
            """
            /** @constructor @abstract */
            function Bar() {};
            /** @return {function(new:Bar)} */
            function foo() {}
            var Foo = foo();
            var f = new Foo;
            """)
        .run();
  }

  @Test
  public void testName1() {
    assertTypeEquals(getNativeVoidType(), testNameNode("undefined"));
  }

  @Test
  public void testName2() {
    assertTypeEquals(getNativeObjectConstructorType(), testNameNode("Object"));
  }

  @Test
  public void testName3() {
    assertTypeEquals(getNativeArrayConstructorType(), testNameNode("Array"));
  }

  @Test
  public void testName4() {
    assertTypeEquals(getNativeDateConstructorType(), testNameNode("Date"));
  }

  @Test
  public void testName5() {
    assertTypeEquals(getNativeRegexpConstructorType(), testNameNode("RegExp"));
  }

  /** Type checks a NAME node and retrieve its type. */
  private JSType testNameNode(String name) {
    Node node = Node.newString(Token.NAME, name);
    Node parent = new Node(Token.SCRIPT, node);
    parent.setInputId(new InputId("code"));

    Node externs = new Node(Token.SCRIPT);
    externs.setInputId(new InputId("externs"));

    Node root = IR.root(IR.root(externs), IR.root(parent));

    makeTypeCheck().processForTesting(root.getFirstChild(), root.getSecondChild());
    return node.getJSType();
  }

  @Test
  public void testBitOperation1() {
    newTest()
        .addSource("/**@return {void}*/function foo(){ ~foo(); }")
        .addDiagnostic("operator ~ cannot be applied to undefined")
        .run();
  }

  @Test
  public void testBitOperation2() {
    newTest()
        .addSource("/**@return {void}*/function foo(){var a = foo()<<3;}")
        .addDiagnostic("operator << cannot be applied to undefined")
        .run();
  }

  @Test
  public void testBitOperation3() {
    newTest()
        .addSource("/**@return {void}*/function foo(){var a = 3<<foo();}")
        .addDiagnostic("operator << cannot be applied to undefined")
        .run();
  }

  @Test
  public void testBitOperation4() {
    newTest()
        .addSource("/**@return {void}*/function foo(){var a = foo()>>>3;}")
        .addDiagnostic("operator >>> cannot be applied to undefined")
        .run();
  }

  @Test
  public void testBitOperation5() {
    newTest()
        .addSource("/**@return {void}*/function foo(){var a = 3>>>foo();}")
        .addDiagnostic("operator >>> cannot be applied to undefined")
        .run();
  }

  @Test
  public void testBitOperation6() {
    newTest()
        .addSource("/**@return {!Object}*/function foo(){var a = foo()&3;}")
        .addDiagnostic(
            """
            bad left operand to bitwise operator
            found   : Object
            required: (boolean|null|number|string|undefined)
            """)
        .run();
  }

  @Test
  public void testBitOperation7() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS)
        .addSource("var x = null; x |= undefined; x &= 3; x ^= '3'; x |= true;")
        .run();
  }

  @Test
  public void testBitOperation8() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS)
        .addSource("var x = void 0; x |= new Number(3);")
        .run();
  }

  @Test
  public void testBitOperation9() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS)
        .addSource("var x = void 0; x |= {};")
        .addDiagnostic(
            """
            bad right operand to bitwise operator
            found   : {}
            required: (boolean|null|number|string|undefined)
            """)
        .run();
  }

  @Test
  public void testCall1() {
    newTest().addSource("3();").addDiagnostic("number expressions are not callable").run();
  }

  @Test
  public void testCall2() {
    newTest()
        .addSource("/** @param {!Number} foo*/function bar(foo){ bar('abc'); }")
        .addDiagnostic(
            """
            actual parameter 1 of bar does not match formal parameter
            found   : string
            required: Number
            """)
        .run();
  }

  @Test
  public void testCall3() {
    // We are checking that an unresolved named type can successfully
    // meet with a functional type to produce a callable type.
    newTest()
        .addSource(
            """
            /** @type {Function|undefined} */var opt_f;
            /** @type {some.unknown.type} */var f1;
            var f2 = opt_f || f1;
            f2();
            """)
        .addDiagnostic("Bad type annotation. Unknown type some.unknown.type")
        .run();
  }

  @Test
  public void testCall3NullishCoalesce() {
    // We are checking that an unresolved named type can successfully
    // meet with a functional type to produce a callable type.
    newTest()
        .addSource(
            """
            /** @type {Function|undefined} */var opt_f;
            /** @type {some.unknown.type} */var f1;
            var f2 = opt_f ?? f1;
            f2();
            """)
        .addDiagnostic("Bad type annotation. Unknown type some.unknown.type")
        .run();
  }

  @Test
  public void testCall4() {
    newTest()
        .addSource("/**@param {!RegExp} a*/var foo = function bar(a){ bar('abc'); }")
        .addDiagnostic(
            """
            actual parameter 1 of bar does not match formal parameter
            found   : string
            required: RegExp
            """)
        .run();
  }

  @Test
  public void testCall5() {
    newTest()
        .addSource("/**@param {!RegExp} a*/var foo = function bar(a){ foo('abc'); }")
        .addDiagnostic(
            """
            actual parameter 1 of foo does not match formal parameter
            found   : string
            required: RegExp
            """)
        .run();
  }

  @Test
  public void testCall6() {
    newTest()
        .addSource(
            """
            /** @param {!Number} foo*/function bar(foo){}
            bar('abc');
            """)
        .addDiagnostic(
            """
            actual parameter 1 of bar does not match formal parameter
            found   : string
            required: Number
            """)
        .run();
  }

  @Test
  public void testCall7() {
    newTest()
        .addSource(
            """
            /** @param {!RegExp} a*/var foo = function bar(a){};
            foo('abc');
            """)
        .addDiagnostic(
            """
            actual parameter 1 of foo does not match formal parameter
            found   : string
            required: RegExp
            """)
        .run();
  }

  @Test
  public void testCall8() {
    newTest()
        .addSource(
            """
            /** @type {Function|number} */var f;
            f();
            """)
        .addDiagnostic("(Function|number) expressions are not callable")
        .run();
  }

  @Test
  public void testCall9() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor */ goog.Foo = function() {};
            /** @param {!goog.Foo} a */ var bar = function(a){};
            bar('abc');
            """)
        .addDiagnostic(
            """
            actual parameter 1 of bar does not match formal parameter
            found   : string
            required: goog.Foo
            """)
        .run();
  }

  @Test
  public void testCall10() {
    newTest().addSource("/** @type {Function} */var f;f();").run();
  }

  @Test
  public void testCall11() {
    newTest().addSource("var f = new Function(); f();").run();
  }

  @Test
  public void testCall12() {
    newTest()
        .addSource(
            """
            /**
             * @param {*} x
             * @return {number}
             */
            function f(x, y) {
              return x && x.foo();
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : *
            required: number
            """)
        .addDiagnostic("Property foo never defined on *" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testCall13() {
    // Test a case where we use inferred types across scopes.
    newTest()
        .addSource(
            """
            var x;
            function useX() { var /** string */ str = x(); }
            function setX() { x = /** @return {number} */ () => 3; }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testFunctionCall1() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ var foo = function(x) {};
            foo.call(null, 3);
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall2() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ var foo = function(x) {};
            foo.call(null, 'bar');
            """)
        .addDiagnostic(
            """
            actual parameter 2 of foo.call does not match formal parameter
            found   : string
            required: number
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall3() {
    newTest()
        .addSource(
            """
            /** @param {number} x
             * @constructor */
            var Foo = function(x) { this.bar.call(null, x); };
            /** @type {function(number)} */ Foo.prototype.bar;
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall4() {
    newTest()
        .addSource(
            """
            /** @param {string} x
             * @constructor */
            var Foo = function(x) { this.bar.call(null, x); };
            /** @type {function(number)} */ Foo.prototype.bar;
            """)
        .addDiagnostic(
            """
            actual parameter 2 of this.bar.call does not match formal parameter
            found   : string
            required: number
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall5() {
    newTest()
        .addSource(
            """
            /** @param {Function} handler
             * @constructor */
            var Foo = function(handler) { handler.call(this, x); };
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall6() {
    newTest()
        .addSource(
            """
            /** @param {Function} handler
             * @constructor */
            var Foo = function(handler) { handler.apply(this, x); };
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall7() {
    newTest()
        .addSource(
            """
            /** @param {Function} handler
             * @param {Object} opt_context */
            var Foo = function(handler, opt_context) {
              handler.call(opt_context, x);
            };
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall8() {
    newTest()
        .addSource(
            """
            /** @param {Function} handler
             * @param {Object} opt_context */
            var Foo = function(handler, opt_context) {
              handler.apply(opt_context, x);
            };
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall9() {
    newTest()
        .addSource(
            """
            /** @constructor
             * @template T
             **/ function Foo() {}
            /** @param {T} x */ Foo.prototype.bar = function(x) {}
            var foo = /** @type {Foo<string>} */ (new Foo());
            foo.bar(3);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Foo.prototype.bar does not match formal parameter
            found   : number
            required: string
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind1() {
    newTest()
        .addSource(
            """
            /** @type {function(string, number): boolean} */
            function f(x, y) { return true; }
            f.bind(null, 3);
            """)
        .addDiagnostic(
            """
            actual parameter 2 of f.bind does not match formal parameter
            found   : number
            required: string
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind2() {
    newTest()
        .addSource(
            """
            /** @type {function(number): boolean} */
            function f(x) { return true; }
            f(f.bind(null, 3)());
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : boolean
            required: number
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind3() {
    newTest()
        .addSource(
            """
            /** @type {function(number, string): boolean} */
            function f(x, y) { return true; }
            f.bind(null, 3)(true);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of function does not match formal parameter
            found   : boolean
            required: string
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind4() {
    newTest()
        .addSource(
            """
            /** @param {...number} x */
            function f(x) {}
            f.bind(null, 3, 3, 3)(true);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of function does not match formal parameter
            found   : boolean
            required: number
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind5() {
    newTest()
        .addSource(
            """
            /** @param {...number} x */
            function f(x) {}
            f.bind(null, true)(3, 3, 3);
            """)
        .addDiagnostic(
            """
            actual parameter 2 of f.bind does not match formal parameter
            found   : boolean
            required: number
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind6() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function MyType() {
              /** @type {number} */
              this.x = 0;
              var f = function() {
                this.x = 'str';
              }.bind(this);
            }
            """)
        .addDiagnostic(
            """
            assignment to property x of MyType
            found   : string
            required: number
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind7() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function MyType() {
              /** @type {number} */
              this.x = 0;
            }
            var m = new MyType;
            (function f() {this.x = 'str';}).bind(m);
            """)
        .addDiagnostic(
            """
            assignment to property x of MyType
            found   : string
            required: number
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind8() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function MyType() {}

            /** @constructor */
            function AnotherType() {}
            AnotherType.prototype.foo = function() {};

            /** @type {?} */
            var m = new MyType;
            (function f() {this.foo();}).bind(m);
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind9() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function MyType() {}

            /** @constructor */
            function AnotherType() {}
            AnotherType.prototype.foo = function() {};

            var m = new MyType;
            (function f() {this.foo();}).bind(m);
            """)
        .addDiagnostic(TypeCheck.INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testGoogBind1() {
    testClosureTypes(
        """
        goog.bind = function(var_args) {};
        /** @type {function(number): boolean} */
        function f(x, y) { return true; }
        f(goog.bind(f, null, 'x')());
        """,
        """
        actual parameter 1 of f does not match formal parameter
        found   : boolean
        required: number
        """);
  }

  @Test
  public void testGoogBind2() {
    // TODO(nicksantos): We do not currently type-check the arguments
    // of the goog.bind.
    testClosureTypes(
        """
        goog.bind = function(var_args) {};
        /** @type {function(boolean): boolean} */
        function f(x, y) { return true; }
        f(goog.bind(f, null, 'x')());
        """,
        null);
  }

  @Test
  public void testCast2() {
    // can upcast to a base type.
    newTest()
        .addSource(
            """
            /** @constructor */function base() {}
            /** @constructor
             @extends {base} */function derived() {}
            /** @type {base} */ var baz = new derived();
            """)
        .run();
  }

  @Test
  public void testCast3() {
    // cannot downcast
    newTest()
        .addSource(
            """
            /** @constructor */function base() {}
            /** @constructor @extends {base} */function derived() {}
            /** @type {!derived} */ var baz = new base();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : base
            required: derived
            """)
        .run();
  }

  @Test
  public void testCast3a() {
    // cannot downcast
    newTest()
        .addSource(
            """
            /** @constructor */function Base() {}
            /** @constructor @extends {Base} */function Derived() {}
            var baseInstance = new Base();
            /** @type {!Derived} */ var baz = baseInstance;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Base
            required: Derived
            """)
        .run();
  }

  @Test
  public void testCast4() {
    // downcast must be explicit
    newTest()
        .addSource(
            """
            /** @constructor */function base() {}
            /** @constructor
             * @extends {base} */function derived() {}
            /** @type {!derived} */ var baz =
            /** @type {!derived} */(new base());
            """)
        .run();
  }

  @Test
  public void testCast4Types() {
    // downcast must be explicit
    Node root =
        parseAndTypeCheck(
            """
            /** @constructor */function base() {}
            /** @constructor
             * @extends {base} */function derived() {}
            /** @type {!derived} */ var baz =
            /** @type {!derived} */(new base());
            """);
    Node castedExprNode = root.getLastChild().getFirstFirstChild().getFirstChild();
    assertThat(castedExprNode.getJSType().toString()).isEqualTo("derived");
    assertThat(castedExprNode.getJSTypeBeforeCast().toString()).isEqualTo("base");
  }

  @Test
  public void testCast5() {
    // cannot explicitly cast to an unrelated type
    newTest()
        .addSource(
            """
            /** @constructor */function foo() {}
            /** @constructor */function bar() {}
            var baz = /** @type {!foo} */(new bar);
            """)
        .addDiagnostic(
            """
            invalid cast - must be a subtype or supertype
            from: bar
            to  : foo
            """)
        .run();
  }

  @Test
  public void testCast5a() {
    // cannot explicitly cast to an unrelated type
    newTest()
        .addSource(
            """
            /** @constructor */function foo() {}
            /** @constructor */function bar() {}
            var barInstance = new bar;
            var baz = /** @type {!foo} */(barInstance);
            """)
        .addDiagnostic(
            """
            invalid cast - must be a subtype or supertype
            from: bar
            to  : foo
            """)
        .run();
  }

  @Test
  public void testCast6() {
    // can explicitly cast to a subtype or supertype
    newTest()
        .addSource(
            """
            /** @constructor */function foo() {}
            /** @constructor\s
             @extends foo */function bar() {}
            var baz = /** @type {!bar} */(new bar);
            var baz = /** @type {!foo} */(new foo);
            var baz = /** @type {bar} */(new bar);
            var baz = /** @type {foo} */(new foo);
            var baz = /** @type {!foo} */(new bar);
            var baz = /** @type {!bar} */(new foo);
            var baz = /** @type {foo} */(new bar);
            var baz = /** @type {bar} */(new foo);
            """)
        .run();
  }

  @Test
  public void testCast7() {
    newTest()
        .addSource("var x = /** @type {foo} */ (new Object());")
        .addDiagnostic("Bad type annotation. Unknown type foo")
        .run();
  }

  @Test
  public void testCast8() {
    newTest()
        .addSource("function f() { return /** @type {foo} */ (new Object()); }")
        .addDiagnostic("Bad type annotation. Unknown type foo")
        .run();
  }

  @Test
  public void testCast9() {
    newTest()
        .addSource(
            """
            var foo = {};
            function f() { return /** @type {foo} */ (new Object()); }
            """)
        .addDiagnostic(
            """
            Bad type annotation. Unknown type foo
            It's possible that 'foo' refers to a value, not a type.
            """)
        .run();
  }

  @Test
  public void testCast10() {
    newTest()
        .addSource(
            """
            var foo = function() {};
            function f() { return /** @type {foo} */ (new Object()); }
            """)
        .addDiagnostic(
            """
            Bad type annotation. Unknown type foo
            It's possible that 'foo' refers to a value, not a type.
            """)
        .run();
  }

  @Test
  public void testCast11() {
    newTest()
        .addSource(
            """
            var goog = {}; goog.foo = {};
            function f() { return /** @type {goog.foo} */ (new Object()); }
            """)
        .addDiagnostic(
            """
            Bad type annotation. Unknown type goog.foo
            It's possible that 'goog.foo' refers to a value, not a type.
            """)
        .run();
  }

  @Test
  public void testCast12() {
    newTest()
        .addSource(
            """
            var goog = {}; goog.foo = function() {};
            function f() { return /** @type {goog.foo} */ (new Object()); }
            """)
        .addDiagnostic(
            """
            Bad type annotation. Unknown type goog.foo
            It's possible that 'goog.foo' refers to a value, not a type.
            """)
        .run();
  }

  @Test
  public void testCast13() {
    // In a typespace world, types and values may collide on the same symbol.
    testClosureTypes(
        """
        goog.forwardDeclare('goog.foo');
        goog.foo = function() {};
        function f() { return /** @type {goog.foo} */ (new Object()); }
        """,
        null);
  }

  @Test
  public void testCast14() {
    // Test to make sure that the forward-declaration still prevents
    // some warnings.
    testClosureTypes(
        """
        goog.forwardDeclare('goog.bar');
        function f() { return /** @type {goog.bar} */ (new Object()); }
        """,
        null);
  }

  @Test
  public void testCast15() {
    // This fixes a bug where a type cast on an object literal
    // would cause a run-time cast exception if the node was visited
    // more than once.

    // Some code assumes that an object literal must have a object type,
    // while because of the cast, it could have any type (including
    // a union).

    // This test is specifically checking loose property check behavior.
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            for (var i = 0; i < 10; i++) {
            var x = /** @type {Object|number} */ ({foo: 3});
            /** @param {number} x */ function f(x) {}
            f(x.foo);
            f([].foo);
            }
            """)
        .addDiagnostic("Property foo never defined on Array<?>")
        .run();
  }

  @Test
  public void testCast15b() {
    // This fixes a bug where a type cast on an object literal
    // would cause a run-time cast exception if the node was visited
    // more than once.

    // Some code assumes that an object literal must have a object type,
    // while because of the cast, it could have any type (including
    // a union).
    newTest()
        .addSource(
            """
            for (var i = 0; i < 10; i++) {
            var x = /** @type {{foo:number}}|number} */ ({foo: 3});
            /** @param {number} x */ function f(x) {}
            f(x.foo);
            f([].foo);
            }
            """)
        .addDiagnostic("Property foo never defined on Array<?>")
        .run();
  }

  @Test
  public void testCast16() {
    newTest()
        .addSource(
            """
            for (var i = 0; i < 10; i++) {
            var x = /** @type {Object|number} */ (
              {/** @type {string} */ foo: 3});
            }
            """)
        .addDiagnostic(
            """
            assignment to property foo of {foo: string}
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testCast17a() {
    // Mostly verifying that rhino actually understands these JsDocs.
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @type {Foo} */ var x = /** @type {Foo} */ (y)
            """)
        .run();
  }

  @Test
  public void testCast17b() {
    // Mostly verifying that rhino actually understands these JsDocs.
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @type {Foo} */ var x = /** @type {Foo} */ ({})
            """)
        .run();
  }

  @Test
  public void testCast19() {
    newTest()
        .addSource(
            """
            var x = 'string';
            /** @type {number} */
            var y = /** @type {number} */(x);
            """)
        .addDiagnostic(
            """
            invalid cast - must be a subtype or supertype
            from: string
            to  : number
            """)
        .run();
  }

  @Test
  public void testCast20() {
    newTest()
        .addSource(
            """
            /** @enum {boolean|null} */
            var X = {
              AA: true,
              BB: false,
              CC: null
            };
            var y = /** @type {X} */(true);
            """)
        .run();
  }

  @Test
  public void testCast21() {
    newTest()
        .addSource(
            """
            /** @enum {boolean|null} */
            var X = {
              AA: true,
              BB: false,
              CC: null
            };
            var value = true;
            var y = /** @type {X} */(value);
            """)
        .run();
  }

  @Test
  public void testCast22() {
    newTest()
        .addSource(
            """
            var x = null;
            var y = /** @type {number} */(x);
            """)
        .addDiagnostic(
            """
            invalid cast - must be a subtype or supertype
            from: null
            to  : number
            """)
        .run();
  }

  @Test
  public void testCast23() {
    newTest()
        .addSource(
            """
            var x = null;
            var y = /** @type {Number} */(x);
            """)
        .run();
  }

  @Test
  public void testCast24() {
    newTest()
        .addSource(
            """
            var x = undefined;
            var y = /** @type {number} */(x);
            """)
        .addDiagnostic(
            """
            invalid cast - must be a subtype or supertype
            from: undefined
            to  : number
            """)
        .run();
  }

  @Test
  public void testCast25() {
    newTest()
        .addSource(
            """
            var x = undefined;
            var y = /** @type {number|undefined} */(x);
            """)
        .run();
  }

  @Test
  public void testCast26() {
    newTest()
        .addSource(
            """
            function fn(dir) {
              var node = dir ? 1 : 2;
              fn(/** @type {number} */ (node));
            }
            """)
        .run();
  }

  @Test
  public void testCast27() {
    // C doesn't implement I but a subtype might.
    newTest()
        .addSource(
            """
            /** @interface */ function I() {}
            /** @constructor */ function C() {}
            var x = new C();
            var y = /** @type {I} */(x);
            """)
        .run();
  }

  @Test
  public void testCast27a() {
    // C doesn't implement I but a subtype might.
    newTest()
        .addSource(
            """
            /** @interface */ function I() {}
            /** @constructor */ function C() {}
            /** @type {C} */ var x ;
            var y = /** @type {I} */(x);
            """)
        .run();
  }

  @Test
  public void testCast28() {
    // C doesn't implement I but a subtype might.
    newTest()
        .addSource(
            """
            /** @interface */ function I() {}
            /** @constructor */ function C() {}
            /** @type {!I} */ var x;
            var y = /** @type {C} */(x);
            """)
        .run();
  }

  @Test
  public void testCast28a() {
    // C doesn't implement I but a subtype might.
    newTest()
        .addSource(
            """
            /** @interface */ function I() {}
            /** @constructor */ function C() {}
            /** @type {I} */ var x;
            var y = /** @type {C} */(x);
            """)
        .run();
  }

  @Test
  public void testCast29a() {
    // C doesn't implement the record type but a subtype might.
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {}
            var x = new C();
            var y = /** @type {{remoteJids: Array, sessionId: string}} */(x);
            """)
        .run();
  }

  @Test
  public void testCast29b() {
    // C doesn't implement the record type but a subtype might.
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {}
            /** @type {C} */ var x;
            var y = /** @type {{prop1: Array, prop2: string}} */(x);
            """)
        .run();
  }

  @Test
  public void testCast29c() {
    // C doesn't implement the record type but a subtype might.
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {}
            /** @type {{remoteJids: Array, sessionId: string}} */ var x ;
            var y = /** @type {C} */(x);
            """)
        .run();
  }

  @Test
  public void testCast30() {
    // Should be able to cast to a looser return type
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {}
            /** @type {function():string} */ var x ;
            var y = /** @type {function():?} */(x);
            """)
        .run();
  }

  @Test
  public void testCast31() {
    // Should be able to cast to a tighter parameter type
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {}
            /** @type {function(*)} */ var x ;
            var y = /** @type {function(string)} */(x);
            """)
        .run();
  }

  @Test
  public void testCast32() {
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {}
            /** @type {Object} */ var x ;
            var y = /** @type {null|{length:number}} */(x);
            """)
        .run();
  }

  @Test
  public void testCast33a() {
    // null and void should be assignable to any type that accepts one or the
    // other or both.
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {}
            /** @type {null|undefined} */ var x ;
            var y = /** @type {string?|undefined} */(x);
            """)
        .run();
  }

  @Test
  public void testCast33b() {
    // null and void should be assignable to any type that accepts one or the
    // other or both.
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {}
            /** @type {null|undefined} */ var x ;
            var y = /** @type {string|undefined} */(x);
            """)
        .run();
  }

  @Test
  public void testCast33c() {
    // null and void should be assignable to any type that accepts one or the
    // other or both.
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {}
            /** @type {null|undefined} */ var x ;
            var y = /** @type {string?} */(x);
            """)
        .run();
  }

  @Test
  public void testCast33d() {
    // null and void should be assignable to any type that accepts one or the
    // other or both.
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {}
            /** @type {null|undefined} */ var x ;
            var y = /** @type {null} */(x);
            """)
        .run();
  }

  @Test
  public void testCast34a() {
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {}
            /** @type {Object} */ var x ;
            var y = /** @type {Function} */(x);
            """)
        .run();
  }

  @Test
  public void testCast34b() {
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {}
            /** @type {Function} */ var x ;
            var y = /** @type {Object} */(x);
            """)
        .run();
  }

  @Test
  public void testCastToNameRequiringPropertyResolution() {
    // regression test for correctly typing properties off of types in CASTs.
    // The type JSDoc in a cast is currently evaluated during TypeInference. In the past any
    // 'unresolved' types in cast JSDoc were not resolved until after type inference completed. This
    // caused type inference to infer properties off of those unresolved types as unknown.
    newTest()
        .addExterns("var unknownVar;")
        .addSource(
            """
            const foo = {bar: {}};
            const bar = foo.bar;
            bar.Class = class {
              /** @return {number} */
              id() { return 0; }
            };
            // Because `foo.bar.Class = ...` was never directly assigned, the type 'foo.bar.Class'
            // is not in the JSTypeRegistry. It's resolved through NamedType#resolveViaProperty.
            // The same thing would have occurred if we assigned 'foo.bar.Class = ...' then
            // referred to '!bar.Class' in the JSDoc.
            // Verify that type inference correctly infers the id property's type.
            const /** null */ n = /** @type {!foo.bar.Class} */ (unknownVar).id;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : function(this:bar.Class): number
            required: null
            """)
        .run();
  }

  @Test
  public void testNestedCasts() {
    newTest()
        .addSource(
            """
            /** @constructor */var T = function() {};
            /** @constructor */var V = function() {};
            /**
            * @param {boolean} b
            * @return {T|V}
            */
            function f(b) { return b ? new T() : new V(); }
            /**
            * @param {boolean} b
            * @return {boolean|undefined}
            */
            function g(b) { return b ? true : undefined; }
            /** @return {T} */
            function h() {
            return /** @type {T} */ (f(/** @type {boolean} */ (g(true))));
            }
            """)
        .run();
  }

  @Test
  public void testNativeCast1() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ function f(x) {}
            f(String(true));
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testNativeCast2() {
    newTest()
        .addSource(
            """
            /** @param {string} x */ function f(x) {}
            f(Number(true));
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testNativeCast3() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ function f(x) {}
            f(Boolean(''));
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testNativeCast4() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ function f(x) {}
            f(Array(1));
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : Array
            required: number
            """)
        .run();
  }

  @Test
  public void testBadConstructorCall() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo();
            """)
        .addDiagnostic("Constructor (typeof Foo) should be called with the \"new\" keyword")
        .run();
  }

  @Test
  public void testTypeof() {
    newTest().addSource("/**@return {void}*/function foo(){ var a = typeof foo(); }").run();
  }

  @Test
  public void testTypeof2() {
    newTest()
        .addSource("function f(){ if (typeof 123 == 'numbr') return 321; }")
        .addDiagnostic("unknown type: numbr")
        .run();
  }

  @Test
  public void testTypeof3() {
    newTest()
        .addSource(
            """
            function f() {
              return (
                  typeof 123 == 'number' ||
                  typeof 123 == 'string' ||
                  typeof 123 == 'boolean' ||
                  typeof 123 == 'undefined' ||
                  typeof 123 == 'function' ||
                  typeof 123 == 'object' ||
                  typeof 123 == 'symbol' ||
                  typeof 123 == 'unknown'); }
            """)
        .run();
  }

  @Test
  public void testConstDecl1() {
    newTest()
        .addSource(
            """
            /** @param {?number} x
            * @return {boolean} */
            function f(x) {
              if (x) { /** @const */ var y = x; return y } return true;
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: boolean
            """)
        .run();
  }

  @Test
  public void testConstDecl2() {
    newTest()
        .addSource(
            """
            /** @param {?number} x */
            function f(x) {
              if (x) {
                /** @const */ var y = x;
                /** @return {boolean} */ function g() { return y; }
              }
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: boolean
            """)
        .run();
  }

  @Test
  public void testConstructorType1() {
    newTest()
        .addSource(
            """
            /**@constructor*/function Foo(){}
            /**@type{!Foo}*/var f = new Date();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Date
            required: Foo
            """)
        .run();
  }

  @Test
  public void testConstructorType2() {
    newTest()
        .addSource(
            """
            /**@constructor*/function Foo(){
            /**@type{Number}*/this.bar = new Number(5);
            }
            /**@type{Foo}*/var f = new Foo();
            /**@type{Number}*/var n = f.bar;
            """)
        .run();
  }

  @Test
  public void testConstructorType3() {
    // Reverse the declaration order so that we know that Foo is getting set
    // even on an out-of-order declaration sequence.
    newTest()
        .addSource(
            """
            /**@type{Foo}*/var f = new Foo();
            /**@type{Number}*/var n = f.bar;
            /**@constructor*/function Foo(){
            /**@type{Number}*/this.bar = new Number(5);
            }
            """)
        .run();
  }

  @Test
  public void testConstructorType4() {
    newTest()
        .addSource(
            """
            /**@constructor*/function Foo(){
            /**@type{!Number}*/this.bar = new Number(5);
            }
            /**@type{!Foo}*/var f = new Foo();
            /**@type{!String}*/var n = f.bar;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Number
            required: String
            """)
        .run();
  }

  @Test
  public void testConstructorType5() {
    newTest()
        .addSource(
            """
            /**@constructor*/function Foo(){}
            if (Foo){}
            """)
        .run();
  }

  @Test
  public void testConstructorType6() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function bar() {}
            function _foo() {
             /** @param {bar} x */
              function f(x) {}
            }
            """)
        .run();
  }

  @Test
  public void testConstructorType7() {
    TypeCheckResult p = parseAndTypeCheckWithScope("/** @constructor */function A(){};");

    JSType type = p.scope.getVar("A").getType();
    assertThat(type).isInstanceOf(FunctionType.class);
    FunctionType fType = (FunctionType) type;
    assertThat(fType.getReferenceName()).isEqualTo("A");
  }

  @Test
  public void testConstructorType8() {
    newTest()
        .addSource(
            """
            var ns = {};
            ns.create = function() { return function() {}; };
            /** @constructor */ ns.Foo = ns.create();
            ns.Foo.prototype = {x: 0, y: 0};
            /**
             * @param {ns.Foo} foo
             * @return {string}
             */
            function f(foo) {
              return foo.x;
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testConstructorType9() {
    newTest()
        .addSource(
            """
            var ns = {};
            ns.create = function() { return function() {}; };
            ns.extend = function(x) { return x; };
            /** @constructor */ ns.Foo = ns.create();
            ns.Foo.prototype = ns.extend({x: 0, y: 0});
            /**
             * @param {ns.Foo} foo
             * @return {string}
             */
            function f(foo) {
              return foo.x;
            }
            """)
        .run();
  }

  @Test
  public void testConstructorType10() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function NonStr() {}
            /**
             * @constructor
             * @struct
             * @extends{NonStr}
             */
            function NonStrKid() {}
            """)
        .run();
  }

  @Test
  public void testConstructorType11() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function NonDict() {}
            /**
             * @constructor
             * @dict
             * @extends{NonDict}
             */
            function NonDictKid() {}
            """)
        .run();
  }

  @Test
  public void testConstructorType12() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Bar() {}
            Bar.prototype = {};
            """)
        .run();
  }

  @Test
  public void testBadStruct() {
    newTest()
        .addSource("/** @struct */function Struct1() {}")
        .addDiagnostic("@struct used without @constructor for Struct1")
        .run();
  }

  @Test
  public void testBadDict() {
    newTest()
        .addSource("/** @dict */function Dict1() {}")
        .addDiagnostic("@dict used without @constructor for Dict1")
        .run();
  }

  @Test
  public void testAnonymousPrototype1() {
    newTest()
        .addSource(
            """
            var ns = {};
            /** @constructor */ ns.Foo = function() {
              this.bar(3, 5);
            };
            ns.Foo.prototype = {
              bar: function(x) {}
            };
            """)
        .addDiagnostic(
            """
            Function ns.Foo.prototype.bar: called with 2 argument(s). Function requires at least 1 argument(s) and no more than 1 argument(s).
            """)
        .run();
  }

  @Test
  public void testAnonymousPrototype2() {
    newTest()
        .addSource(
            """
            /** @interface */ var Foo = function() {};
            Foo.prototype = {
              foo: function(x) {}
            };
            /**
             * @constructor
             * @implements {Foo}
             */ var Bar = function() {};
            """)
        .addDiagnostic("property foo on interface Foo is not implemented by type Bar")
        .run();
  }

  @Test
  public void testAnonymousType1() {
    newTest()
        .addSource(
            """
            function f() { return {}; }
            /** @constructor */
            f().bar = function() {};
            """)
        .run();
  }

  @Test
  public void testAnonymousType2() {
    newTest()
        .addSource(
            """
            function f() { return {}; }
            /** @interface */
            f().bar = function() {};
            """)
        .run();
  }

  @Test
  public void testAnonymousType3() {
    newTest()
        .addSource(
            """
            function f() { return {}; }
            /** @enum */
            f().bar = {FOO: 1};
            """)
        .run();
  }

  @Test
  public void testBang1() {
    newTest()
        .addSource(
            """
            /** @param {Object} x
            * @return {!Object} */
            function f(x) { return x; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (Object|null)
            required: Object
            """)
        .run();
  }

  @Test
  public void testBang2() {
    newTest()
        .addSource(
            """
            /** @param {Object} x
            * @return {!Object} */
            function f(x) { return x ? x : new Object(); }
            """)
        .run();
  }

  @Test
  public void testBang3() {
    newTest()
        .addSource(
            """
            /** @param {Object} x
            * @return {!Object} */
            function f(x) { return /** @type {!Object} */ (x); }
            """)
        .run();
  }

  @Test
  public void testBang4() {
    newTest()
        .addSource(
            """
            /**@param {Object} x
            @param {Object} y
            @return {boolean}*/
            function f(x, y) {
            if (typeof x != 'undefined') { return x == y; }
            else { return x != y; }
            }
            """)
        .run();
  }

  @Test
  public void testBang5() {
    newTest()
        .addSource(
            """
            /**@param {Object} x
            @param {Object} y
            @return {boolean}*/
            function f(x, y) { return !!x && x == y; }
            """)
        .run();
  }

  @Test
  public void testBang6() {
    newTest()
        .addSource(
            """
            /** @param {Object?} x
            * @return {Object} */
            function f(x) { return x; }
            """)
        .run();
  }

  @Test
  public void testBang7() {
    newTest()
        .addSource(
            """
            /**@param {(Object|string|null)} x
            * @return {(Object|string)}*/function f(x) { return x; }
            """)
        .run();
  }

  @Test
  public void testDefinePropertyOnNullableObject1() {
    // checking loose property behavior
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @type {Object} */ var n = {};
            /** @type {number} */ n.x = 1;
            /** @return {boolean} */ function f() { return n.x; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: boolean
            """)
        .run();
  }

  @Test
  public void testDefinePropertyOnNullableObject1a() {
    newTest()
        .addSource(
            """
            /** @const */ var n = {};
            /** @type {number} */ n.x = 1;
            /** @return {boolean} */function f() { return n.x; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: boolean
            """)
        .run();
  }

  @Test
  public void testDefinePropertyOnObject() {
    // checking loose property behavior
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @type {!Object} */ var n = {};
            /** @type {number} */ n.x = 1;
            /** @return {boolean} */function f() { return n.x; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: boolean
            """)
        .run();
  }

  @Test
  public void testDefinePropertyOnNullableObject2() {
    // checking loose property behavior
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @constructor */ var T = function() {};
            /** @param {T} t
            @return {boolean} */function f(t) {
            t.x = 1; return t.x; }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: boolean
            """)
        .run();
  }

  @Test
  public void testDefinePropertyOnNullableObject2b() {
    newTest()
        .addSource(
            """
            /** @constructor */ var T = function() {};
            /** @param {T} t */function f(t) { t.x = 1; }
            """)
        .addDiagnostic("Property x never defined on T")
        .run();
  }

  @Test
  public void testUnknownConstructorInstanceType1() {
    newTest().addSource("/** @return {Array} */ function g(f) { return new f(); }").run();
  }

  @Test
  public void testUnknownConstructorInstanceType2() {
    newTest().addSource("function g(f) { return /** @type {Array} */(new f()); }").run();
  }

  @Test
  public void testUnknownConstructorInstanceType3() {
    newTest().addSource("function g(f) { var x = new f(); x.a = 1; return x; }").run();
  }

  @Test
  public void testUnknownPrototypeChain1() {
    newTest()
        .addSource(
            """
            /**
            * @param {Object} co
             * @return {Object}
             */
            function inst(co) {
             /** @constructor */
             var c = function() {};
             c.prototype = co.prototype;
             return new c;
            }
            """)
        .addDiagnostic("Property prototype never defined on Object")
        .run();
  }

  @Test
  public void testUnknownPrototypeChain2() {
    newTest()
        .addSource(
            """
            /**
             * @param {Function} co
             * @return {Object}
             */
            function inst(co) {
             /** @constructor */
             var c = function() {};
             c.prototype = co.prototype;
             return new c;
            }
            """)
        .run();
  }

  @Test
  public void testNamespacedConstructor() {
    Node root =
        parseAndTypeCheck(
            """
            var goog = {};
            /** @constructor */ goog.MyClass = function() {};
            /** @return {!goog.MyClass} */
            function foo() { return new goog.MyClass(); }
            """);

    JSType typeOfFoo = root.getLastChild().getJSType();
    assertType(typeOfFoo).isInstanceOf(FunctionType.class);

    JSType retType = ((FunctionType) typeOfFoo).getReturnType();
    assertType(retType).isInstanceOf(ObjectType.class);
    assertThat(((ObjectType) retType).getReferenceName()).isEqualTo("goog.MyClass");
  }

  @Test
  public void testComplexNamespace() {
    String js =
        """
        var goog = {};
        goog.foo = {};
        goog.foo.bar = 5;
        """;

    TypeCheckResult p = parseAndTypeCheckWithScope(js);

    // goog type in the scope
    JSType googScopeType = p.scope.getVar("goog").getType();
    assertThat(googScopeType).isInstanceOf(ObjectType.class);
    assertWithMessage("foo property not present on goog type")
        .that(googScopeType.hasProperty("foo"))
        .isTrue();
    assertWithMessage("bar property present on goog type")
        .that(googScopeType.hasProperty("bar"))
        .isFalse();

    // goog type on the VAR node
    Node varNode = p.root.getFirstChild();
    assertNode(varNode).hasToken(Token.VAR);
    JSType googNodeType = varNode.getFirstChild().getJSType();
    assertThat(googNodeType).isInstanceOf(ObjectType.class);

    // goog scope type and goog type on VAR node must be the same
    assertThat(googScopeType).isSameInstanceAs(googNodeType);

    // goog type on the left of the GETPROP node (under fist ASSIGN)
    Node getpropFoo1 = varNode.getNext().getFirstFirstChild();
    assertNode(getpropFoo1).hasToken(Token.GETPROP);
    assertThat(getpropFoo1.getFirstChild().getString()).isEqualTo("goog");
    JSType googGetpropFoo1Type = getpropFoo1.getFirstChild().getJSType();
    assertThat(googGetpropFoo1Type).isInstanceOf(ObjectType.class);

    // still the same type as the one on the variable
    assertThat(googGetpropFoo1Type).isSameInstanceAs(googScopeType);

    // the foo property should be defined on goog
    JSType googFooType = ((ObjectType) googScopeType).getPropertyType("foo");
    assertThat(googFooType).isInstanceOf(ObjectType.class);

    // goog type on the left of the GETPROP lower level node
    // (under second ASSIGN)
    Node getpropFoo2 = varNode.getNext().getNext().getFirstFirstChild().getFirstChild();
    assertNode(getpropFoo2).hasToken(Token.GETPROP);
    assertThat(getpropFoo2.getFirstChild().getString()).isEqualTo("goog");
    JSType googGetpropFoo2Type = getpropFoo2.getFirstChild().getJSType();
    assertThat(googGetpropFoo2Type).isInstanceOf(ObjectType.class);

    // still the same type as the one on the variable
    assertThat(googGetpropFoo2Type).isSameInstanceAs(googScopeType);

    // goog.foo type on the left of the top-level GETPROP node
    // (under second ASSIGN)
    JSType googFooGetprop2Type = getpropFoo2.getJSType();
    assertWithMessage("goog.foo incorrectly annotated in goog.foo.bar selection")
        .that(googFooGetprop2Type)
        .isInstanceOf(ObjectType.class);
    ObjectType googFooGetprop2ObjectType = (ObjectType) googFooGetprop2Type;
    assertWithMessage("foo property present on goog.foo type")
        .that(googFooGetprop2ObjectType.hasProperty("foo"))
        .isFalse();
    assertWithMessage("bar property not present on goog.foo type")
        .that(googFooGetprop2ObjectType.hasProperty("bar"))
        .isTrue();
    assertTypeEquals(
        "bar property on goog.foo type incorrectly inferred",
        getNativeNumberType(),
        googFooGetprop2ObjectType.getPropertyType("bar"));
  }

  @Test
  public void testAddingMethodsUsingPrototypeIdiomSimpleNamespace() {
    Node js1Node =
        parseAndTypeCheck(
            DEFAULT_EXTERNS,
            """
            /** @constructor */function A() {}
            A.prototype.m1 = 5
            """);

    ObjectType instanceType = getInstanceType(js1Node);
    assertHasXMorePropertiesThanNativeObject(instanceType, 1);
    checkObjectType(instanceType, "m1", getNativeNumberType());
  }

  @Test
  public void testAddingMethodsUsingPrototypeIdiomComplexNamespace1() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope(
            DEFAULT_EXTERNS,
            """
            var goog = {};
            goog.A = /** @constructor */function() {};
            /** @type {number} */goog.A.prototype.m1 = 5
            """);

    testAddingMethodsUsingPrototypeIdiomComplexNamespace(p);
  }

  @Test
  public void testAddingMethodsUsingPrototypeIdiomComplexNamespace2() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope(
            DEFAULT_EXTERNS,
            """
            var goog = {};
            /** @constructor */goog.A = function() {};
            /** @type {number} */goog.A.prototype.m1 = 5
            """);

    testAddingMethodsUsingPrototypeIdiomComplexNamespace(p);
  }

  private void testAddingMethodsUsingPrototypeIdiomComplexNamespace(TypeCheckResult p) {
    ObjectType goog = (ObjectType) p.scope.getVar("goog").getType();
    assertHasXMorePropertiesThanNativeObject(goog, 1);
    JSType googA = goog.getPropertyType("A");
    assertThat(googA).isNotNull();
    assertThat(googA).isInstanceOf(FunctionType.class);
    FunctionType googAFunction = (FunctionType) googA;
    ObjectType classA = googAFunction.getInstanceType();
    assertHasXMorePropertiesThanNativeObject(classA, 1);
    checkObjectType(classA, "m1", getNativeNumberType());
  }

  @Test
  public void testAddingMethodsPrototypeIdiomAndObjectLiteralSimpleNamespace() {
    Node js1Node =
        parseAndTypeCheck(
            DEFAULT_EXTERNS,
            """
            /** @constructor */function A() {}
            A.prototype = {m1: 5, m2: true}
            """);

    ObjectType instanceType = getInstanceType(js1Node);
    assertHasXMorePropertiesThanNativeObject(instanceType, 2);
    checkObjectType(instanceType, "m1", getNativeNumberType());
    checkObjectType(instanceType, "m2", getNativeBooleanType());
  }

  @Test
  public void testDontAddMethodsIfNoConstructor() {
    Node js1Node =
        parseAndTypeCheck(
            """
            function A() {}
            A.prototype = {m1: 5, m2: true}
            """);
    JSType functionAType = js1Node.getFirstChild().getJSType();
    assertThat(functionAType.toString()).isEqualTo("function(): undefined");
    assertTypeEquals(getNativeUnknownType(), getNativeFunctionType().getPropertyType("m1"));
    assertTypeEquals(getNativeUnknownType(), getNativeFunctionType().getPropertyType("m2"));
  }

  @Test
  public void testFunctionAssignement() {
    newTest()
        .addSource(
            """
            /**
            * @param {string} ph0
            * @param {string} ph1
            * @return {string}
            */
            function MSG_CALENDAR_ACCESS_ERROR(ph0, ph1) {return ''}
            /** @type {Function} */
            var MSG_CALENDAR_ADD_ERROR = MSG_CALENDAR_ACCESS_ERROR;
            """)
        .run();
  }

  @Test
  public void testAddMethodsPrototypeTwoWays() {
    Node js1Node =
        parseAndTypeCheck(
            DEFAULT_EXTERNS,
            """
            /** @constructor */function A() {}
            A.prototype = {m1: 5, m2: true};
            A.prototype.m3 = 'third property!';
            """);

    ObjectType instanceType = getInstanceType(js1Node);
    assertThat(instanceType.toString()).isEqualTo("A");
    assertHasXMorePropertiesThanNativeObject(instanceType, 3);
    checkObjectType(instanceType, "m1", getNativeNumberType());
    checkObjectType(instanceType, "m2", getNativeBooleanType());
    checkObjectType(instanceType, "m3", getNativeStringType());
  }

  @Test
  public void testPrototypePropertyTypes() {
    Node js1Node =
        parseAndTypeCheck(
            DEFAULT_EXTERNS,
            """
            /** @constructor */function A() {
              /** @type {string} */ this.m1;
              /** @type {Object?} */ this.m2 = {};
              /** @type {boolean} */ this.m3;
            }
            /** @type {string} */ A.prototype.m4;
            /** @type {number} */ A.prototype.m5 = 0;
            /** @type {boolean} */ A.prototype.m6;
            """);

    ObjectType instanceType = getInstanceType(js1Node);
    assertHasXMorePropertiesThanNativeObject(instanceType, 6);
    checkObjectType(instanceType, "m1", getNativeStringType());
    checkObjectType(
        instanceType, "m2", createUnionType(getNativeObjectType(), getNativeNullType()));
    checkObjectType(instanceType, "m3", getNativeBooleanType());
    checkObjectType(instanceType, "m4", getNativeStringType());
    checkObjectType(instanceType, "m5", getNativeNumberType());
    checkObjectType(instanceType, "m6", getNativeBooleanType());
  }

  @Test
  public void testValueTypeBuiltInPrototypePropertyType() {
    Node node = parseAndTypeCheck(new TestExternsBuilder().addString().build(), "\"x\".charAt(0)");
    assertTypeEquals(getNativeStringType(), node.getFirstFirstChild().getJSType());
  }

  @Test
  public void testDeclareBuiltInConstructor() {
    // Built-in prototype properties should be accessible
    // even if the built-in constructor is declared.
    Node node =
        parseAndTypeCheck(
            new TestExternsBuilder().addString().build(),
            """
            /** @constructor */ var String = function(opt_str) {};
            (new String("x")).charAt(0)
            """);
    assertTypeEquals(getNativeStringType(), node.getLastChild().getFirstChild().getJSType());
  }

  @Test
  public void testExtendBuiltInType1() {
    String externs =
        """
        /** @constructor */ var String = function(opt_str) {};
        /**
        * @param {number} start
        * @param {number} opt_length
        * @return {string}
        */
        String.prototype.substr = function(start, opt_length) {};
        """;
    Node n1 = parseAndTypeCheck(externs + "(new String(\"x\")).substr(0,1);");
    assertTypeEquals(getNativeStringType(), n1.getLastChild().getFirstChild().getJSType());
  }

  @Test
  public void testExtendBuiltInType2() {
    String externs =
        """
        /** @constructor */ var String = function(opt_str) {};
        /**
        * @param {number} start
        * @param {number} opt_length
        * @return {string}
        */
        String.prototype.substr = function(start, opt_length) {};
        """;
    Node n2 = parseAndTypeCheck(externs + "\"x\".substr(0,1);");
    assertTypeEquals(getNativeStringType(), n2.getLastChild().getFirstChild().getJSType());
  }

  @Test
  public void testExtendFunction1() {
    Node n =
        parseAndTypeCheck(
            """
            /**@return {number}*/Function.prototype.f = function() { return 1; };
            (new Function()).f();
            """);
    JSType type = n.getLastChild().getLastChild().getJSType();
    assertTypeEquals(getNativeNumberType(), type);
  }

  @Test
  public void testExtendFunction2() {
    Node n =
        parseAndTypeCheck(
            """
            /**@return {number}*/Function.prototype.f = function() { return 1; };
            (function() {}).f();
            """);
    JSType type = n.getLastChild().getLastChild().getJSType();
    assertTypeEquals(getNativeNumberType(), type);
  }

  @Test
  public void testClassExtendPrimitive() {
    newTest()
        .addSource("/** @extends {number} */ class C extends number {}")
        .addDiagnostic("C @extends non-object type number")
        .run();
  }

  @Test
  public void testInheritanceCheck1() {
    newTest()
        .addSource(
            """
            /** @constructor */function Super() {};
            /** @constructor
             @extends {Super} */function Sub() {};
            Sub.prototype.foo = function() {};
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck2() {
    newTest()
        .addSource(
            """
            /** @constructor */function Super() {};
            /** @constructor
             @extends {Super} */function Sub() {};
            /** @override */Sub.prototype.foo = function() {};
            """)
        .addDiagnostic("property foo not defined on any superclass of Sub")
        .run();
  }

  @Test
  public void testInheritanceCheck3() {
    newTest()
        .addSource(
            """
            /** @constructor */function Super() {};
            Super.prototype.foo = function() {};
            /** @constructor
             @extends {Super} */function Sub() {};
            Sub.prototype.foo = function() {};
            """)
        .addDiagnostic(
            "property foo already defined on superclass Super; use @override to override it")
        .run();
  }

  @Test
  public void testInheritanceCheck4() {
    newTest()
        .addSource(
            """
            /** @constructor */function Super() {};
            Super.prototype.foo = function() {};
            /** @constructor
             @extends {Super} */function Sub() {};
            /** @override */Sub.prototype.foo = function() {};
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck5() {
    newTest()
        .addSource(
            """
            /** @constructor */function Root() {};
            Root.prototype.foo = function() {};
            /** @constructor
             @extends {Root} */function Super() {};
            /** @constructor
             @extends {Super} */function Sub() {};
            Sub.prototype.foo = function() {};
            """)
        .addDiagnostic(
            "property foo already defined on superclass Root; use @override to override it")
        .run();
  }

  @Test
  public void testInheritanceCheck6() {
    newTest()
        .addSource(
            """
            /** @constructor */function Root() {};
            Root.prototype.foo = function() {};
            /** @constructor
             @extends {Root} */function Super() {};
            /** @constructor
             @extends {Super} */function Sub() {};
            /** @override */Sub.prototype.foo = function() {};
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck7() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor */goog.Super = function() {};
            goog.Super.prototype.foo = 3;
            /** @constructor
             @extends {goog.Super} */goog.Sub = function() {};
            goog.Sub.prototype.foo = 5;
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck8() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor */goog.Super = function() {};
            goog.Super.prototype.foo = 3;
            /** @constructor
             @extends {goog.Super} */goog.Sub = function() {};
            /** @override */goog.Sub.prototype.foo = 5;
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck9_1() {
    newTest()
        .addSource(
            """
            /** @constructor */function Super() {};
            Super.prototype.foo = function() { return 3; };
            /** @constructor
             @extends {Super} */function Sub() {};
            /** @override
             @return {number} */Sub.prototype.foo =
            function() { return 1; };
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck9_2() {
    newTest()
        .addSource(
            """
            /** @constructor */function Super() {};
            /** @return {number} */
            Super.prototype.foo = function() { return 1; };
            /** @constructor
             @extends {Super} */function Sub() {};
            /** @override */Sub.prototype.foo =
            function() {};
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck9_3() {
    newTest()
        .addSource(
            """
            /** @constructor */function Super() {};
            /** @return {number} */
            Super.prototype.foo = function() { return 1; };
            /** @constructor
             @extends {Super} */function Sub() {};
            /** @override
             @return {string} */Sub.prototype.foo =
            function() { return "some string" };
            """)
        .addDiagnostic(
            """
            mismatch of the foo property type and the type of the property it overrides from superclass Super
            original: function(this:Super): number
            override: function(this:Sub): string
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck10_1() {
    newTest()
        .addSource(
            """
            /** @constructor */function Root() {};
            Root.prototype.foo = function() { return 3; };
            /** @constructor
             @extends {Root} */function Super() {};
            /** @constructor
             @extends {Super} */function Sub() {};
            /** @override
             @return {number} */Sub.prototype.foo =
            function() { return 1; };
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck10_2() {
    newTest()
        .addSource(
            """
            /** @constructor */function Root() {};
            /** @return {number} */
            Root.prototype.foo = function() { return 1; };
            /** @constructor
             @extends {Root} */function Super() {};
            /** @constructor
             @extends {Super} */function Sub() {};
            /** @override */Sub.prototype.foo =
            function() {};
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck10_3() {
    newTest()
        .addSource(
            """
            /** @constructor */function Root() {};
            /** @return {number} */
            Root.prototype.foo = function() { return 1; };
            /** @constructor
             @extends {Root} */function Super() {};
            /** @constructor
             @extends {Super} */function Sub() {};
            /** @override
             @return {string} */Sub.prototype.foo =
            function() { return "some string" };
            """)
        .addDiagnostic(
            """
            mismatch of the foo property type and the type of the property it overrides from superclass Root
            original: function(this:Root): number
            override: function(this:Sub): string
            """)
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck11() {
    newTest()
        .addSource(
            """
            /** @constructor */function Super() {};
            /** @param {number} bar */Super.prototype.foo = function(bar) {};
            /** @constructor
             @extends {Super} */function Sub() {};
            /** @override
              @param {string} bar */Sub.prototype.foo =
            function(bar) {};
            """)
        .addDiagnostic(
            """
            mismatch of the foo property type and the type of the property it overrides from superclass Super
            original: function(this:Super, number): undefined
            override: function(this:Sub, string): undefined
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck12() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor */goog.Super = function() {};
            goog.Super.prototype.foo = 3;
            /** @constructor
             @extends {goog.Super} */goog.Sub = function() {};
            /** @override */goog.Sub.prototype.foo = "some string";
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck13() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor
             @extends {goog.Missing} */function Sub() {};
            /** @override */Sub.prototype.foo = function() {};
            """)
        .addDiagnostic(
            """
            Bad type annotation. Unknown type goog.Missing
            It's possible that 'goog.Missing' refers to a value, not a type.
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck14() {
    testClosureTypes(
        """
        /** @constructor
         @extends {goog.Missing} */
        goog.Super = function() {};
        /** @constructor
         @extends {goog.Super} */function Sub() {};
        /** @override */ Sub.prototype.foo = function() {};
        """,
        """
        Bad type annotation. Unknown type goog.Missing
        It's possible that 'goog.Missing' refers to a value, not a type.
        """);
  }

  @Test
  public void testInheritanceCheck15() {
    newTest()
        .addSource(
            """
            /** @constructor */function Super() {};
            /** @param {number} bar */Super.prototype.foo;
            /** @constructor
             @extends {Super} */function Sub() {};
            /** @override
              @param {number} bar */Sub.prototype.foo =
            function(bar) {};
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck16() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor */goog.Super = function() {};
            /** @type {number} */ goog.Super.prototype.foo = 3;
            /** @constructor
             @extends {goog.Super} */goog.Sub = function() {};
            /** @type {number} */ goog.Sub.prototype.foo = 5;
            """)
        .addDiagnostic(
            """
            property foo already defined on superclass goog.Super; use @override to override it
            """)
        .run();
  }

  @Test
  public void testInheritanceCheck17() {
    // Make sure this warning still works, even when there's no
    // @override tag.
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor */goog.Super = function() {};
            /** @param {number} x */ goog.Super.prototype.foo = function(x) {};
            /** @constructor
             @extends {goog.Super} */goog.Sub = function() {};
            /** @override @param {string} x */ goog.Sub.prototype.foo = function(x) {};
            """)
        .addDiagnostic(
            """
            mismatch of the foo property type and the type of the property it overrides from superclass goog.Super
            original: function(this:goog.Super, number): undefined
            override: function(this:goog.Sub, string): undefined
            """)
        .run();
  }

  @Test
  public void testInterfacePropertyOverride1() {
    newTest()
        .addSource(
            """
            /** @interface */function Super() {};
            Super.prototype.foo = function() {};
            /** @interface
             @extends {Super} */function Sub() {};
            Sub.prototype.foo = function() {};
            """)
        .run();
  }

  @Test
  public void testInterfacePropertyOverride2() {
    newTest()
        .addSource(
            """
            /** @interface */function Root() {};
            Root.prototype.foo = function() {};
            /** @interface
             @extends {Root} */function Super() {};
            /** @interface
             @extends {Super} */function Sub() {};
            Sub.prototype.foo = function() {};
            """)
        .run();
  }

  @Test
  public void testInterfacePropertyBadOverrideFails() {
    newTest()
        .addSource(
            """
            /** @interface */function Super() {};
            /** @type {number} */
            Super.prototype.foo;
            /** @interface @extends {Super} */function Sub() {};
            /** @type {string} */
            Sub.prototype.foo;
            """)
        .addDiagnostic(
"""
mismatch of the foo property on type Sub and the type of the property it overrides from interface Super
original: number
override: string
""")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck1() {
    newTest()
        .addSource(
            """
            /** @interface */function Super() {};
            Super.prototype.foo = function() {};
            /** @constructor
             @implements {Super} */function Sub() {};
            Sub.prototype.foo = function() {};
            """)
        .addDiagnostic(
            "property foo already defined on interface Super; use @override to override it")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck2() {
    newTest()
        .addSource(
            """
            /** @interface */function Super() {};
            Super.prototype.foo = function() {};
            /** @constructor
             @implements {Super} */function Sub() {};
            /** @override */Sub.prototype.foo = function() {};
            """)
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck3() {
    newTest()
        .addSource(
            """
            /** @interface */function Root() {};
            /** @return {number} */Root.prototype.foo = function() {};
            /** @interface
             @extends {Root} */function Super() {};
            /** @constructor
             @implements {Super} */function Sub() {};
            /** @return {number} */Sub.prototype.foo = function() { return 1;};
            """)
        .addDiagnostic(
            "property foo already defined on interface Root; use @override to override it")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck4() {
    newTest()
        .addSource(
            """
            /** @interface */function Root() {};
            /** @return {number} */Root.prototype.foo = function() {};
            /** @interface
             @extends {Root} */function Super() {};
            /** @constructor
             @implements {Super} */function Sub() {};
            /** @override
             * @return {number} */Sub.prototype.foo =
            function() { return 1;};
            """)
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck5() {
    newTest()
        .addSource(
"""
/** @interface */function Super() {};/** @return {string} */Super.prototype.foo = function() {};/** @constructor
 @implements {Super} */function Sub() {};/** @override
 @return {number} */Sub.prototype.foo = function() { return 1; };
""")
        .addDiagnostic(
            """
            mismatch of the foo property on type Sub and the type of the property it overrides from interface Super
            original: function(this:Super): string
            override: function(this:Sub): number
            """)
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck6() {
    newTest()
        .addSource(
"""
/** @interface */function Root() {};/** @return {string} */Root.prototype.foo = function() {};/** @interface
 @extends {Root} */function Super() {};/** @constructor
 @implements {Super} */function Sub() {};/** @override
 @return {number} */Sub.prototype.foo = function() { return 1; };
""")
        .addDiagnostic(
            """
            mismatch of the foo property on type Sub and the type of the property it overrides from interface Root
            original: function(this:Root): string
            override: function(this:Sub): number
            """)
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck7() {
    newTest()
        .addSource(
            """
            /** @interface */function Super() {};
            /** @param {number} bar */Super.prototype.foo = function(bar) {};
            /** @constructor
             @implements {Super} */function Sub() {};
            /** @override
              @param {string} bar */Sub.prototype.foo =
            function(bar) {};
            """)
        .addDiagnostic(
            """
            mismatch of the foo property on type Sub and the type of the property it overrides from interface Super
            original: function(this:Super, number): undefined
            override: function(this:Sub, string): undefined
            """)
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck8() {
    newTest()
        .addSource(
            """
            /** @constructor
             @implements {Super} */function Sub() {};
            /** @override */Sub.prototype.foo = function() {};
            """)
        .addDiagnostic("Bad type annotation. Unknown type Super")
        .addDiagnostic("property foo not defined on any superclass of Sub")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck9() {
    newTest()
        .addSource(
            """
            /** @interface */ function I() {}
            /** @return {number} */ I.prototype.bar = function() {};
            /** @constructor */ function F() {}
            /** @return {number} */ F.prototype.bar = function() {return 3; };
            /** @return {number} */ F.prototype.foo = function() {return 3; };
            /** @constructor
             * @extends {F}
             * @implements {I} */
            function G() {}
            /** @return {string} */ function f() { return new G().bar(); }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck10() {
    newTest()
        .addSource(
            """
            /** @interface */ function I() {}
            /** @return {number} */ I.prototype.bar = function() {};
            /** @constructor */ function F() {}
            /** @return {number} */ F.prototype.foo = function() {return 3; };
            /** @constructor
             * @extends {F}
             * @implements {I} */
            function G() {}
            /** @return {number}
             * @override */
            G.prototype.bar = G.prototype.foo;
            /** @return {string} */ function f() { return new G().bar(); }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck12() {
    newTest()
        .addSource(
            """
            /** @interface */ function I() {};
            /** @type {string} */ I.prototype.foobar;
            /**\s
             * @constructor\s
             * @implements {I} */
            function C() {
            /**\s
             * @type {number} */ this.foobar = 2;};
            /** @type {I} */\s
             var test = new C(); alert(test.foobar);
            """)
        .addDiagnostic(
            """
            mismatch of the foobar property on type C and the type of the property it overrides from interface I
            original: string
            override: number
            """)
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck13() {
    newTest()
        .addSource(
            """
            function abstractMethod() {};
            /** @interface */var base = function() {};
            /** @extends {base}\s
             @interface */ var Int = function() {}
            /** @type {{bar : !Function}} */ var x;\s
            /** @type {!Function} */ base.prototype.bar = abstractMethod;\s
            /** @type {Int} */ var foo;
            foo.bar();
            """)
        .run();
  }

  /** Verify that templatized interfaces can extend one another and share template values. */
  @Test
  public void testInterfaceInheritanceCheck14() {
    newTest()
        .addSource(
            """
            /** @interface
             @template T */function A() {};
            /** @return {T} */A.prototype.foo = function() {};
            /** @interface
             @template U
             @extends {A<U>} */function B() {};
            /** @return {U} */B.prototype.bar = function() {};
            /** @constructor
             @implements {B<string>} */function C() {};
            /** @return {string}
             @override */C.prototype.foo = function() {};
            /** @return {string}
             @override */C.prototype.bar = function() {};
            """)
        .run();
  }

  /** Verify that templatized instances can correctly implement templatized interfaces. */
  @Test
  public void testInterfaceInheritanceCheck15() {
    newTest()
        .addSource(
            """
            /** @interface
             @template T */function A() {};
            /** @return {T} */A.prototype.foo = function() {};
            /** @interface
             @template U
             @extends {A<U>} */function B() {};
            /** @return {U} */B.prototype.bar = function() {};
            /** @constructor
             @template V
             @implements {B<V>}
             */function C() {};
            /** @return {V}
             @override */C.prototype.foo = function() {};
            /** @return {V}
             @override */C.prototype.bar = function() {};
            """)
        .run();
  }

  /**
   * Verify that using @override to declare the signature for an implementing class works correctly
   * when the interface is generic.
   */
  @Test
  public void testInterfaceInheritanceCheck16() {
    newTest()
        .addSource(
            """
            /** @interface
             @template T */function A() {};
            /** @return {T} */A.prototype.foo = function() {};
            /** @return {T} */A.prototype.bar = function() {};
            /** @constructor
             @implements {A<string>} */function B() {};
            /** @override */B.prototype.foo = function() { return 'string'};
            /** @override */B.prototype.bar = function() { return 3 };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testInterfacePropertyNotImplemented() {
    newTest()
        .addSource(
            """
            /** @interface */function Int() {};
            Int.prototype.foo = function() {};
            /** @constructor
             @implements {Int} */function Foo() {};
            """)
        .addDiagnostic("property foo on interface Int is not implemented by type Foo")
        .run();
  }

  @Test
  public void testInterfacePropertyNotImplemented2() {
    newTest()
        .addSource(
            """
            /** @interface */function Int() {};
            Int.prototype.foo = function() {};
            /** @interface
             @extends {Int} */function Int2() {};
            /** @constructor
             @implements {Int2} */function Foo() {};
            """)
        .addDiagnostic("property foo on interface Int is not implemented by type Foo")
        .run();
  }

  /** Verify that templatized interfaces enforce their template type values. */
  @Test
  public void testInterfacePropertyNotImplemented3() {
    newTest()
        .addSource(
            """
            /** @interface  @template T */ function Int() {};
            /** @return {T} */ Int.prototype.foo = function() {};

            /** @constructor @implements {Int<string>} */ function Foo() {};
            /** @return {number}  @override */ Foo.prototype.foo = function() {};
            """)
        .addDiagnostic(
"""
mismatch of the foo property on type Foo and the type of the property it overrides from interface Int
original: function(this:Int): string
override: function(this:Foo): number
""")
        .run();
  }

  @Test
  public void testStubConstructorImplementingInterface() {
    // This does not throw a warning for unimplemented property because Foo is
    // just a stub.
    newTest()
        .addExterns(
            """
            /** @interface */ function Int() {}
            Int.prototype.foo = function() {};
            /** @constructor
             @implements {Int} */ var Foo;
            """)
        .addSource("")
        .run();
  }

  @Test
  public void testObjectLiteral() {
    Node n = parseAndTypeCheck("var a = {m1: 7, m2: 'hello'}");

    Node nameNode = n.getFirstFirstChild();
    Node objectNode = nameNode.getFirstChild();

    // node extraction
    assertNode(nameNode).hasToken(Token.NAME);
    assertNode(objectNode).hasToken(Token.OBJECTLIT);

    // value's type
    ObjectType objectType = (ObjectType) objectNode.getJSType();
    assertTypeEquals(getNativeNumberType(), objectType.getPropertyType("m1"));
    assertTypeEquals(getNativeStringType(), objectType.getPropertyType("m2"));

    // variable's type
    assertTypeEquals(objectType, nameNode.getJSType());
  }

  @Test
  public void testObjectLiteralDeclaration1() {
    newTest()
        .addSource(
            """
            var x = {
              /** @type {boolean} */ abc: true,
              /** @type {number} */ 'def': 0,
              /** @type {string} */ 3: 'fgh'
            };
            """)
        .run();
  }

  @Test
  public void testObjectLiteralDeclaration2() {
    newTest()
        .addSource(
            """
            var x = {
              /** @type {boolean} */ abc: true
            };
            x.abc = 0;
            """)
        .addDiagnostic(
            """
            assignment to property abc of x
            found   : number
            required: boolean
            """)
        .run();
  }

  @Test
  public void testObjectLiteralDeclaration3() {
    newTest()
        .addSource(
            """
            /** @param {{foo: !Function}} x */ function f(x) {}
            f({foo: function() {}});
            """)
        .run();
  }

  @Test
  public void testObjectLiteralDeclaration4() {
    testClosureTypes(
        """
        var x = {
          /** @param {boolean} x */ abc: function(x) {}
        };
        /**
         * @param {string} x
         * @suppress {duplicate}
         */ x.abc = function(x) {};
        """,
        """
        assignment to property abc of x
        found   : function(string): undefined
        required: function(boolean): undefined
        """);
    // TODO(user): suppress {duplicate} currently also silence the
    // redefining type error in the TypeValidator. Maybe it needs
    // a new suppress name instead?
  }

  @Test
  public void testObjectLiteralDeclaration5() {
    newTest()
        .addSource(
            """
            var x = {
              /** @param {boolean} x */ abc: function(x) {}
            };
            /**
             * @param {boolean} x
             * @suppress {duplicate}
             */ x.abc = function(x) {};
            """)
        .run();
  }

  @Test
  public void testObjectLiteralDeclaration6() {
    newTest()
        .addSource(
            """
            var x = {};
            /**
             * @param {boolean} x
             * @suppress {duplicate}
             */ x.abc = function(x) {};
            x = {
              /**
               * @param {boolean} x
               * @suppress {duplicate}
               */
              abc: function(x) {}
            };
            """)
        .run();
  }

  @Test
  public void testObjectLiteralDeclaration7() {
    newTest()
        .addSource(
            """
            var x = {};
            /**
             * @type {function(boolean): undefined}
             */ x.abc = function(x) {};
            x = {
              /**
               * @param {boolean} x
               * @suppress {duplicate}
               */
              abc: function(x) {}
            };
            """)
        .run();
  }

  @Test
  public void testCallDateConstructorAsFunction() {
    // ECMA-262 15.9.2: When Date is called as a function rather than as a
    // constructor, it returns a string.
    Node n = parseAndTypeCheck("Date()");
    assertTypeEquals(getNativeStringType(), n.getFirstFirstChild().getJSType());
  }

  // According to ECMA-262, Error & Array function calls are equivalent to
  // constructor calls.

  @Test
  public void testCallErrorConstructorAsFunction() {
    String externs =
        """
        /** @constructor
            @param {string} message
            @return {!Error} */
        function Error(message) {}
        """;
    Node n = parseAndTypeCheck(externs, "Error('x')");
    Node call = n.getFirstFirstChild();
    assertThat(call.isCall()).isTrue();
    assertTypeEquals(
        call.getFirstChild().getJSType().toMaybeFunctionType().getInstanceType(), call.getJSType());
  }

  @Test
  public void testCallArrayConstructorAsFunction() {
    Node n = parseAndTypeCheck("Array()");
    assertTypeEquals(getNativeArrayType(), n.getFirstFirstChild().getJSType());
  }

  @Test
  public void testPropertyTypeOfUnionType() {
    newTest()
        .addSource(
            """
            var a = {};
            /** @constructor */ a.N = function() {};
            a.N.prototype.p = 1;
            /** @constructor */ a.S = function() {};
            a.S.prototype.p = 'a';
            /** @param {!a.N|!a.S} x
            @return {string} */
            var f = function(x) { return x.p; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (number|string)
            required: string
            """)
        .run();
  }

  @Test
  public void testGetPropertyTypeOfUnionType_withMatchingTemplates() {
    newTest()
        .addSource(
            """
            /** @interface @template T */ function Foo() {};
            /** @type {T} */
            Foo.prototype.p;
            /** @interface @template U */ function Bar() {};
            /** @type {U} */
            Bar.prototype.p;

            /**
             * @param {!Foo<number>|!Bar<number>} x
             * @return {string}
             */
            var f = function(x) { return x.p; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testGetPropertyTypeOfUnionType_withDifferingTemplates() {
    newTest()
        .addSource(
            """
            /** @interface @template T */ function Foo() {};
            /** @type {T} */
            Foo.prototype.p;
            /** @interface @template U */ function Bar() {};
            /** @type {U} */
            Bar.prototype.p;

            /**
             * @param {!Foo<number>|!Bar<string>} x
             * @return {string}
             */
            var f = function(x) { return x.p; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (number|string)
            required: string
            """)
        .run();
  }

  @Test
  public void testGetPropertyTypeOfUnionType_withMembersThatExtendATemplatizedType() {
    newTest()
        .addSource(
            """
            /** @interface @template T */ function Foo() {};
            /** @type {T} */
            Foo.prototype.p;

            /** @interface @extends {Foo<number>} */ function Bar() {};
            /** @interface @extends {Foo<number>} */ function Baz() {}

            /**
             * @param {!Bar|!Baz} x
             * @return {string}
             */
            var f = function(x) { return x.p; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testInvalidAssignToPropertyTypeOfUnionType_withMatchingTemplates_doesntWarn() {
    // We don't warn for this assignment because we treat the type of `x.p` as inferred...
    newTest()
        .addSource(
            """
            /** @interface @template T */ function Foo() {};
            /** @type {T} */
            Foo.prototype.p;
            /** @interface @template U */ function Bar() {};
            /** @type {U} */
            Bar.prototype.p;

            /**
             * @param {!Foo<number>|!Bar<number>} x
             */
            var f = function(x) { x.p = 'not a number'; };
            """)
        .run();
  }

  // TODO(user): We should flag these as invalid. This will probably happen
  // when we make sure the interface is never referenced outside of its
  // definition. We might want more specific and helpful error messages.
  @Test
  @Ignore
  public void testWarningOnInterfacePrototype() {
    newTest()
        .addSource(
            """
            /** @interface */ u.T = function() {};
            /** @return {number} */ u.T.prototype = function() { };
            """)
        .addDiagnostic("e of its definition")
        .run();
  }

  @Test
  @Ignore
  public void testBadPropertyOnInterface1() {
    newTest()
        .addSource(
            """
            /** @interface */ u.T = function() {};
            /** @return {number} */ u.T.f = function() { return 1;};
            """)
        .addDiagnostic("cannot reference an interface outside of its definition")
        .run();
  }

  @Test
  @Ignore
  public void testBadPropertyOnInterface2() {
    newTest()
        .addSource(
            """
            /** @interface */ function T() {};
            /** @return {number} */ T.f = function() { return 1;};
            """)
        .addDiagnostic("cannot reference an interface outside of its definition")
        .run();
  }

  @Test
  @Ignore
  public void testBadPropertyOnInterface3() {
    newTest()
        .addSource("/** @interface */ u.T = function() {}; u.T.x")
        .addDiagnostic("cannot reference an interface outside of its definition")
        .run();
  }

  @Test
  @Ignore
  public void testBadPropertyOnInterface4() {
    newTest()
        .addSource("/** @interface */ function T() {}; T.x;")
        .addDiagnostic("cannot reference an interface outside of its definition")
        .run();
  }

  @Test
  public void testAnnotatedPropertyOnInterface1() {
    // For interfaces we must allow function definitions that don't have a
    // return statement, even though they declare a returned type.
    newTest()
        .addSource(
            """
            /** @interface */ u.T = function() {};
            /** @return {number} */ u.T.prototype.f = function() {};
            """)
        .run();
  }

  @Test
  public void testAnnotatedPropertyOnInterface2() {
    newTest()
        .addSource(
            """
            /** @interface */ u.T = function() {};
            /** @return {number} */ u.T.prototype.f = function() { };
            """)
        .run();
  }

  @Test
  public void testAnnotatedPropertyOnInterface3() {
    newTest()
        .addSource(
            """
            /** @interface */ function T() {};
            /** @return {number} */ T.prototype.f = function() { };
            """)
        .run();
  }

  @Test
  public void testAnnotatedPropertyOnInterface4() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + """
                /** @interface */ function T() {};
                /** @return {number} */ T.prototype.f = goog.abstractMethod;
                """)
        .run();
  }

  // TODO(user): If we want to support this syntax we have to warn about
  // missing annotations.
  @Test
  @Ignore
  public void testWarnUnannotatedPropertyOnInterface1() {
    newTest()
        .addSource("/** @interface */ u.T = function() {}; u.T.prototype.x;")
        .addDiagnostic("interface property x is not annotated")
        .run();
  }

  @Test
  @Ignore
  public void testWarnUnannotatedPropertyOnInterface2() {
    newTest()
        .addSource("/** @interface */ function T() {}; T.prototype.x;")
        .addDiagnostic("interface property x is not annotated")
        .run();
  }

  @Test
  public void testWarnUnannotatedPropertyOnInterface5() {
    newTest()
        .addSource(
            """
            /** @interface */ u.T = function() {};
            u.T.prototype.x = function() {};
            """)
        .run();
  }

  @Test
  public void testWarnUnannotatedPropertyOnInterface6() {
    newTest()
        .addSource(
            """
            /** @interface */ function T() {};
            T.prototype.x = function() {};
            """)
        .run();
  }

  // TODO(user): If we want to support this syntax we have to warn about
  // the invalid type of the interface member.
  @Test
  @Ignore
  public void testWarnDataPropertyOnInterface1() {
    newTest()
        .addSource(
            """
            /** @interface */ u.T = function() {};
            /** @type {number} */u.T.prototype.x;
            """)
        .addDiagnostic("interface members can only be plain functions")
        .run();
  }

  @Test
  public void testDataPropertyOnInterface1() {
    newTest()
        .addSource(
            """
            /** @interface */ function T() {};
            /** @type {number} */T.prototype.x;
            """)
        .run();
  }

  @Test
  public void testDataPropertyOnInterface2() {
    newTest()
        .addSource(
            """
            /** @interface */ function T() {};
            /** @type {number} */T.prototype.x;
            /** @constructor\s
             * @implements {T}\s
             */
            function C() {}
            /** @override */
            C.prototype.x = 'foo';
            """)
        .addDiagnostic(
            """
            mismatch of the x property on type C and the type of the property it overrides from interface T
            original: number
            override: string
            """)
        .run();
  }

  @Test
  public void testDataPropertyOnInterface3() {
    newTest()
        .addSource(
            """
            /** @interface */ function T() {};
            /** @type {number} */T.prototype.x;
            /** @constructor\s
             * @implements {T}\s
             */
            function C() {}
            /** @override */
            C.prototype.x = 'foo';
            """)
        .addDiagnostic(
            """
            mismatch of the x property on type C and the type of the property it overrides from interface T
            original: number
            override: string
            """)
        .run();
  }

  @Test
  public void testDataPropertyOnInterface4() {
    newTest()
        .addSource(
            """
            /** @interface */ function T() {};
            /** @type {number} */T.prototype.x;
            /** @constructor\s
             * @implements {T}\s
             */
            function C() { /** @type {string} */\s
             this.x = 'foo'; }
            """)
        .addDiagnostic(
            """
            mismatch of the x property on type C and the type of the property it overrides from interface T
            original: number
            override: string
            """)
        .run();
  }

  @Test
  public void testWarnDataPropertyOnInterface3() {
    newTest()
        .addSource(
            """
            /** @interface */ u.T = function() {};
            /** @type {number} */u.T.prototype.x = 1;
            """)
        .addDiagnostic(
            """
            interface members can only be empty property declarations, empty functions, or goog.abstractMethod
            """)
        .run();
  }

  @Test
  public void testWarnDataPropertyOnInterface4() {
    newTest()
        .addSource(
            """
            /** @interface */ function T() {};
            /** @type {number} */T.prototype.x = 1;
            """)
        .addDiagnostic(
            """
            interface members can only be empty property declarations, empty functions, or goog.abstractMethod
            """)
        .run();
  }

  // TODO(user): If we want to support this syntax we should warn about the
  // mismatching types in the two tests below.
  @Test
  @Ignore
  public void testErrorMismatchingPropertyOnInterface1() {
    newTest()
        .addSource(
            """
            /** @interface */ u.T = function() {};
            /** @param {Number} foo */u.T.prototype.x =
            /** @param {String} foo */function(foo) {};
            """)
        .addDiagnostic(
            """
            found   :\s
            required:\s
            """)
        .run();
  }

  @Test
  @Ignore
  public void testErrorMismatchingPropertyOnInterface2() {
    newTest()
        .addSource(
            """
            /** @interface */ function T() {};
            /** @return {number} */T.prototype.x =
            /** @return {string} */function() {};
            """)
        .addDiagnostic(
            """
            found   :\s
            required:\s
            """)
        .run();
  }

  // TODO(user): We should warn about this (bar is missing an annotation). We
  // probably don't want to warn about all missing parameter annotations, but
  // we should be as strict as possible regarding interfaces.
  @Test
  @Ignore
  public void testErrorMismatchingPropertyOnInterface3() {
    newTest()
        .addSource(
            """
            /** @interface */ u.T = function() {};
            /** @param {Number} foo */u.T.prototype.x =
            function(foo, bar) {};
            """)
        .addDiagnostic(
            """
            found   :\s
            required:\s
            """)
        .run();
  }

  @Test
  public void testErrorMismatchingPropertyOnInterface4() {
    newTest()
        .addSource(
            """
            /** @interface */ u.T = function() {};
            /** @param {Number} foo */u.T.prototype.x =
            function() {};
            """)
        .addDiagnostic("parameter foo does not appear in u.T.prototype.x's parameter list")
        .run();
  }

  @Test
  public void testErrorMismatchingPropertyOnInterface5() {
    newTest()
        .addSource(
            """
            /** @interface */ function T() {};
            /** @type {number} */T.prototype.x = function() { };
            """)
        .addDiagnostic(
            """
            assignment to property x of T.prototype
            found   : function(): undefined
            required: number\
            """)
        .run();
  }

  @Test
  public void testErrorMismatchingPropertyOnInterface6() {
    testClosureTypesMultipleWarnings(
        """
        /** @interface */ function T() {};
        /** @return {number} */T.prototype.x = 1
        """,
        ImmutableList.of(
            """
            assignment to property x of T.prototype
            found   : number
            required: function(this:T): number\
            """,
            """
            interface members can only be empty property declarations, empty functions, or goog.abstractMethod\
            """));
  }

  @Test
  public void testInterfaceNonEmptyFunction() {
    newTest()
        .addSource(
            """
            /** @interface */ function T() {};
            T.prototype.x = function() { return 'foo'; }
            """)
        .addDiagnostic("interface member functions must have an empty body")
        .run();
  }

  @Test
  public void testDoubleNestedInterface() {
    newTest()
        .addSource(
            """
            /** @interface */ var I1 = function() {};
            /** @interface */ I1.I2 = function() {};
            /** @interface */ I1.I2.I3 = function() {};
            """)
        .run();
  }

  @Test
  public void testStaticDataPropertyOnNestedInterface() {
    newTest()
        .addSource(
            """
            /** @interface */ var I1 = function() {};
            /** @interface */ I1.I2 = function() {};
            /** @type {number} */ I1.I2.x = 1;
            """)
        .run();
  }

  @Test
  public void testInterfaceInstantiation() {
    newTest()
        .addSource("/** @interface */var f = function(){}; new f")
        .addDiagnostic("cannot instantiate non-constructor, found type: (typeof f)")
        .run();
  }

  @Test
  public void testPrototypeLoop() {
    testClosureTypesMultipleWarnings(
        suppressMissingProperty("foo")
            + """
            /** @constructor
             * @extends {T} */var T = function() {};
            alert((new T).foo);
            """,
        ImmutableList.of(
            "Cycle detected in inheritance chain of type T",
            "Could not resolve type in @extends tag of T"));
  }

  @Test
  public void testImplementsLoop() {
    testClosureTypesMultipleWarnings(
        """
        /** @constructor
         * @implements {T} */var T = function() {};
        SUPPRESSION
        alert((new T).foo);
        """
            .replace("SUPPRESSION", suppressMissingPropertyFor("T", "foo")),
        ImmutableList.of("Cycle detected in inheritance chain of type T"));
  }

  @Test
  public void testImplementsExtendsLoop() {
    testClosureTypesMultipleWarnings(
        """
        /**
         * @constructor
         * @implements {F}
         */
        var G = function() {};
        /**
         * @constructor
         * @extends {G}
         */
        var F = function() {};
        alert((new F).foo);
        """,
        ImmutableList.of(
            "Cycle detected in inheritance chain of type F", "Property foo never defined on F"));
  }

  // TODO(johnlenz): This test causes an infinite loop,
  @Test
  @Ignore
  public void testInterfaceExtendsLoop() {
    testClosureTypesMultipleWarnings(
        """
        /** @interface
         * @extends {F} */var G = function() {};
        /** @interface
         * @extends {G} */var F = function() {};
        /** @constructor
         * @implements {F} */var H = function() {};
        SUPPRESSION
        alert((new H).foo);
        """
            .replace("SUPPRESSION", suppressMissingPropertyFor("H", "foo")),
        ImmutableList.of(
            "extends loop involving F, loop: F -> G -> F",
            "extends loop involving G, loop: G -> F -> G"));
  }

  @Test
  public void testInterfaceExtendsLoop2() {
    testClosureTypes(
        """
        /** @record
         * @extends {F} */var G = function() {};
        /** @record
         * @extends {G} */var F = function() {};
        /** @constructor
         * @implements {F} */var H = function() {};
        SUPPRESSION
        alert((new H).foo);
        """
            .replace("SUPPRESSION", suppressMissingPropertyFor("H", "foo")),
        "Cycle detected in inheritance chain of type F");
  }

  @Test
  public void testInheritPropFromMultipleInterfaces1() {
    // Low#prop gets the type of whichever property is declared last,
    // even if that type is not the most specific.
    newTest()
        .addSource(
            """
            /** @interface */
            function High1() {}
            /** @type {number|string} */
            High1.prototype.prop;
            /** @interface */
            function High2() {}
            /** @type {number} */
            High2.prototype.prop;
            /**
             * @interface
             * @extends {High1}
             * @extends {High2}
             */
            function Low() {}
            function f(/** !Low */ x) { var /** null */ n = x.prop; }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (number|string)
            required: null
            """)
        .run();
  }

  @Test
  public void testInheritPropFromMultipleInterfaces2() {
    // Low#prop gets the type of whichever property is declared last,
    // even if that type is not the most specific.
    newTest()
        .addSource(
            """
            /** @interface */
            function High1() {}
            /** @type {number} */
            High1.prototype.prop;
            /** @interface */
            function High2() {}
            /** @type {number|string} */
            High2.prototype.prop;
            /**
             * @interface
             * @extends {High1}
             * @extends {High2}
             */
            function Low() {}
            function f(/** !Low */ x) { var /** null */ n = x.prop; }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: null
            """)
        .run();
  }

  @Test
  public void testInheritPropFromMultipleInterfaces3() {
    newTest()
        .addSource(
            """
            /**
             * @interface
             * @template T1
             */
            function MyCollection() {}
            /**
             * @interface
             * @template T2
             * @extends {MyCollection<T2>}
             */
            function MySet() {}
            /**
             * @interface
             * @template T3,T4
             */
            function MyMapEntry() {}
            /**
             * @interface
             * @template T5,T6
             */
            function MyMultimap() {}
            /** @return {MyCollection<MyMapEntry<T5, T6>>} */
            MyMultimap.prototype.entries = function() {};
            /**
             * @interface
             * @template T7,T8
             * @extends {MyMultimap<T7, T8>}
             */
            function MySetMultimap() {}
            /** @return {MySet<MyMapEntry<T7, T8>>} */
            MySetMultimap.prototype.entries = function() {};
            /**
             * @interface
             * @template T9,T10
             * @extends {MyMultimap<T9, T10>}
             */
            function MyFilteredMultimap() {}
            /**
             * @interface
             * @template T11,T12
             * @extends {MyFilteredMultimap<T11, T12>}
             * @extends {MySetMultimap<T11, T12>}
             */
            function MyFilteredSetMultimap() {}
            """)
        .run();
  }

  @Test
  public void testInheritSameGenericInterfaceFromDifferentPaths() {
    newTest()
        .addSource(
            """
            /** @const */ var ns = {};
            /**
             * @constructor
             * @template T1
             */
            ns.Foo = function() {};
            /**
             * @interface
             * @template T2
             */
            ns.High = function() {};
            /** @type {!ns.Foo<T2>} */
            ns.High.prototype.myprop;
            /**
             * @interface
             * @template T3
             * @extends {ns.High<T3>}
             */
            ns.Med1 = function() {};
            /**
             * @interface
             * @template T4
             * @extends {ns.High<T4>}
             */
            ns.Med2 = function() {};
            /**
             * @interface
             * @template T5
             * @extends {ns.Med1<T5>}
             * @extends {ns.Med2<T5>}
             */
            ns.Low = function() {};
            """)
        .run();
  }

  @Test
  public void testConversionFromInterfaceToRecursiveConstructor() {
    testClosureTypesMultipleWarnings(
        suppressMissingProperty("foo")
            + """
            /** @interface */ var OtherType = function() {}
            /** @implements {MyType}
             * @constructor */
            var MyType = function() {}
            /** @type {MyType} */
            var x = /** @type {!OtherType} */ (new Object());
            """,
        ImmutableList.of(
            "Cycle detected in inheritance chain of type MyType",
            """
            initializing variable
            found   : OtherType
            required: (MyType|null)\
            """));
  }

  @Test
  public void testDirectPrototypeAssign() {
    // For now, we just ignore @type annotations on the prototype.
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @constructor */ function Bar() {}
            /** @type {Array} */ Bar.prototype = new Foo()
            """)
        .run();
  }

  // In all testResolutionViaRegistry* tests, since u is unknown, u.T can only
  // be resolved via the registry and not via properties.

  @Test
  public void testResolutionViaRegistry1() {
    newTest()
        .addSource(
            """
            /** @constructor */ u.T = function() {};
            /** @type {(number|string)} */ u.T.prototype.a;
            /**
            * @param {u.T} t
            * @return {string}
            */
            var f = function(t) { return t.a; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (number|string)
            required: string
            """)
        .run();
  }

  @Test
  public void testResolutionViaRegistry2() {
    newTest()
        .addSource(
            """
            /** @constructor */ u.T = function() {
              this.a = 0; };
            /**
            * @param {u.T} t
            * @return {string}
            */
            var f = function(t) { return t.a; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testResolutionViaRegistry3() {
    newTest()
        .addSource(
            """
            /** @constructor */ u.T = function() {};
            /** @type {(number|string)} */ u.T.prototype.a = 0;
            /**
            * @param {u.T} t
            * @return {string}
            */
            var f = function(t) { return t.a; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (number|string)
            required: string
            """)
        .run();
  }

  @Test
  public void testResolutionViaRegistry4() {
    newTest()
        .addSource(
            """
            /** @constructor */ u.A = function() {};
            /**
            * @constructor
            * @extends {u.A}
            */
            u.A.A = function() {};
            /**
            * @constructor
            * @extends {u.A}
            */
            u.A.B = function() {};
            var ab = new u.A.B();
            /** @type {!u.A} */ var a = ab;
            /** @type {!u.A.A} */ var aa = ab;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : u.A.B
            required: u.A.A
            """)
        .run();
  }

  @Test
  public void testResolutionViaRegistry5() {
    Node n = parseAndTypeCheck("/** @constructor */ u.T = function() {}; u.T");
    JSType type = n.getLastChild().getLastChild().getJSType();
    assertThat(type.isUnknownType()).isFalse();
    assertThat(type).isInstanceOf(FunctionType.class);
    assertThat(((FunctionType) type).getInstanceType().getReferenceName()).isEqualTo("u.T");
  }

  @Test
  public void testGatherProperyWithoutAnnotation1() {
    Node n =
        parseAndTypeCheck(
            """
            /** @constructor */ var T = function() {};
            /** @type {!T} */var t; t.x; t;
            """);
    JSType type = n.getLastChild().getLastChild().getJSType();
    assertThat(type.isUnknownType()).isFalse();
    assertThat(type).isInstanceOf(ObjectType.class);
    ObjectType objectType = (ObjectType) type;
    assertThat(objectType.hasProperty("x")).isFalse();
  }

  @Test
  public void testGatherProperyWithoutAnnotation2() {
    TypeCheckResult ns = parseAndTypeCheckWithScope("/** @type {!Object} */var t; t.x; t;");
    Node n = ns.root;
    JSType type = n.getLastChild().getLastChild().getJSType();
    assertThat(type.isUnknownType()).isFalse();
    assertTypeEquals(type, getNativeObjectType());
    assertThat(type).isInstanceOf(ObjectType.class);
    ObjectType objectType = (ObjectType) type;
    assertThat(objectType.hasProperty("x")).isFalse();
  }

  @Test
  public void testFunctionMasksVariableBug() {
    newTest()
        .addSource("var x = 4; var f = function x(b) { return b ? 1 : x(true); };")
        .addDiagnostic("function x masks variable (IE bug)")
        .run();
  }

  @Test
  public void testDfa1() {
    newTest().addSource("var x = null;\n x = 1;\n /** @type {number} */ var y = x;").run();
  }

  @Test
  public void testDfa2() {
    newTest()
        .addSource(
            """
            function u() {}
            /** @return {number} */ function f() {
            var x = 'todo';
            if (u()) { x = 1; } else { x = 2; } return x;
            }
            """)
        .run();
  }

  @Test
  public void testDfa3() {
    newTest()
        .addSource(
            """
            function u() {}
            /** @return {number} */ function f() {
            /** @type {number|string} */ var x = 'todo';
            if (u()) { x = 1; } else { x = 2; } return x;
            }
            """)
        .run();
  }

  @Test
  public void testDfa4() {
    newTest()
        .addSource(
            """
            /** @param {Date?} d */ function f(d) {
            if (!d) { return; }
            /** @type {!Date} */ var e = d;
            }
            """)
        .run();
  }

  @Test
  public void testDfa5() {
    newTest()
        .addSource(
            """
            /** @return {string?} */ function u() {return 'a';}
            /** @param {string?} x
            @return {string} */ function f(x) {
            while (!x) { x = u(); }
            return x;
            }
            """)
        .run();
  }

  @Test
  public void testDfa6() {
    newTest()
        .addSource(
            """
            /** @return {Object?} */ function u() {return {};}
            /** @param {Object?} x */ function f(x) {
            while (x) { x = u(); if (!x) { x = u(); } }
            }
            """)
        .run();
  }

  @Test
  public void testDfa7() {
    newTest()
        .addSource(
            """
            /** @constructor */ var T = function() {};
            /** @type {Date?} */ T.prototype.x = null;
            /** @param {!T} t */ function f(t) {
            if (!t.x) { return; }
            /** @type {!Date} */ var e = t.x;
            }
            """)
        .run();
  }

  @Test
  public void testDfa8() {
    newTest()
        .addSource(
            """
            /** @constructor */ var T = function() {};
            /** @type {number|string} */ T.prototype.x = '';
            function u() {}
            /** @param {!T} t
            @return {number} */ function f(t) {
            if (u()) { t.x = 1; } else { t.x = 2; } return t.x;
            }
            """)
        .run();
  }

  @Test
  public void testDfa9() {
    newTest()
        .addSource(
            """
            function f() {
            /** @type {string?} */var x;
            x = null;
            if (x == null) { return 0; } else { return 1; } }
            """)
        .addDiagnostic(
            """
            condition always evaluates to true
            left : null
            right: null
            """)
        .run();
  }

  @Test
  public void testDfa10() {
    newTest()
        .addSource(
            """
            /** @param {null} x */ function g(x) {}
            /** @param {string?} x */function f(x) {
            if (!x) { x = ''; }
            if (g(x)) { return 0; } else { return 1; } }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : string
            required: null
            """)
        .run();
  }

  @Test
  public void testDfa11() {
    newTest()
        .addSource(
            """
            /** @param {string} opt_x
            @return {string} */
            function f(opt_x) { if (!opt_x) {
            throw new Error('x cannot be empty'); } return opt_x; }
            """)
        .run();
  }

  @Test
  public void testDfa12() {
    newTest()
        .addSource(
            """
            /** @param {string} x
             * @constructor
             */
            var Bar = function(x) {};
            /** @param {string} x */ function g(x) { return true; }
            /** @param {string|number} opt_x */
            function f(opt_x) {
              if (opt_x) { new Bar(g(opt_x) && 'x'); }
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : (number|string)
            required: string
            """)
        .run();
  }

  @Test
  public void testDfa13() {
    newTest()
        .addSource(
            """
            /**
             * @param {string} x
             * @param {number} y
             * @param {number} z
             */
            function g(x, y, z) {}
            function f() {
              var x = 'a'; g(x, x = 3, x);
            }
            """)
        .run();
  }

  @Test
  public void testTypeInferenceWithCast1() {
    newTest()
        .addSource(
            """
            /**@return {(number|null|undefined)}*/function u(x) {return null;}
            /**@param {number?} x
            @return {number?}*/function f(x) {return x;}
            /**@return {number?}*/function g(x) {
            var y = /**@type {number?}*/(u(x)); return f(y);}
            """)
        .run();
  }

  @Test
  public void testTypeInferenceWithCast2() {
    newTest()
        .addSource(
            """
            /**@return {(number|null|undefined)}*/function u(x) {return null;}
            /**@param {number?} x
            @return {number?}*/function f(x) {return x;}
            /**@return {number?}*/function g(x) {
            var y; y = /**@type {number?}*/(u(x)); return f(y);}
            """)
        .run();
  }

  @Test
  public void testTypeInferenceWithCast3() {
    newTest()
        .addSource(
            """
            /**@return {(number|null|undefined)}*/function u(x) {return 1;}
            /**@return {number}*/function g(x) {
            return /**@type {number}*/(u(x));}
            """)
        .run();
  }

  @Test
  public void testTypeInferenceWithCast4() {
    newTest()
        .addSource(
            """
            /**@return {(number|null|undefined)}*/function u(x) {return 1;}
            /**@return {number}*/function g(x) {
            return /**@type {number}*/(u(x)) && 1;}
            """)
        .run();
  }

  @Test
  public void testTypeInferenceWithCast5() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ function foo(x) {}
            /** @param {{length:*}} y */ function bar(y) {
              /** @type {string} */ y.length;
              foo(y.length);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of foo does not match formal parameter
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testTypeInferenceWithClosure1() {
    newTest()
        .addSource(
            """
            /** @return {boolean} */
            function f() {
              /** @type {?string} */ var x = null;
              function g() { x = 'y'; } g();
              return x == null;
            }
            """)
        .run();
  }

  @Test
  public void testTypeInferenceWithClosure2() {
    newTest()
        .addSource(
            """
            /** @return {boolean} */
            function f() {
              /** @type {?string} */ var x = null;
              function g() { x = 'y'; } g();
              return x === 3;
            }
            """)
        .addDiagnostic(
            """
            condition always evaluates to false
            left : (null|string)
            right: number
            """)
        .run();
  }

  @Test
  public void testTypeInferenceWithNoEntry1() {
    newTest()
        .addSource(
            """
            /** @param {number} x */ function f(x) {}
            /** @constructor */ function Foo() {}
            Foo.prototype.init = function() {
              /** @type {?{baz: number}} */ this.bar = {baz: 3};
            };
            /**
             * @extends {Foo}
             * @constructor
             */
            function SubFoo() {}
            /** Method */
            SubFoo.prototype.method = function() {
              for (var i = 0; i < 10; i++) {
                f(this.bar);
                f(this.bar.baz);
              }
            };
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : (null|{baz: number})
            required: number
            """)
        .run();
  }

  @Test
  public void testTypeInferenceWithNoEntry2() {
    testClosureTypes(
        """
        /** @param {number} x */ function f(x) {}
        /** @param {!Object} x */ function g(x) {}
        /** @constructor */ function Foo() {}
        Foo.prototype.init = function() {
          /** @type {?{baz: number}} */ this.bar = {baz: 3};
        };
        /**
         * @extends {Foo}
         * @constructor
         */
        function SubFoo() {}
        /** Method */
        SubFoo.prototype.method = function() {
          for (var i = 0; i < 10; i++) {
            f(this.bar);
            goog.asserts.assert(this.bar);
            g(this.bar);
          }
        };
        """,
        """
        actual parameter 1 of f does not match formal parameter
        found   : (null|{baz: number})
        required: number
        """);
  }

  @Test
  public void testForwardPropertyReference() {
    newTest()
        .addSource(
            """
            /** @constructor */ var Foo = function() { this.init(); };
            /** @return {string} */
            Foo.prototype.getString = function() {
              return this.number_;
            };
            Foo.prototype.init = function() {
              /** @type {number} */
              this.number_ = 3;
            };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testNoForwardTypeDeclaration() {
    newTest()
        .addSource("/** @param {MyType} x */ function f(x) {}")
        .addDiagnostic("Bad type annotation. Unknown type MyType")
        .run();
  }

  @Test
  public void testNoForwardTypeDeclarationAndNoBraces() {
    newTest()
        .addSource("/** @return The result. */ function f() {}")
        .addDiagnostic(RhinoErrorReporter.JSDOC_MISSING_TYPE_WARNING)
        .run();
  }

  @Test
  public void testForwardTypeDeclaration1() {
    String f =
        """
        goog.forwardDeclare('MyType');
        /** @param {!MyType} x */ function f(x) { }
        """;
    testClosureTypes(f, null);
    testClosureTypes(
        f + "f(3);",
        """
        actual parameter 1 of f does not match formal parameter
        found   : number
        required: MyType
        """);
  }

  @Test
  public void testForwardTypeDeclaration2() {
    String f =
        """
        goog.forwardDeclare('MyType');
        /** @param {MyType} x */ function f(x) { }
        """;
    testClosureTypes(f, null);
    testClosureTypes(
        f + "f(3);",
        """
        actual parameter 1 of f does not match formal parameter
        found   : number
        required: (MyType|null)
        """);
  }

  @Test
  public void testForwardTypeDeclaration3() {
    testClosureTypes(
        """
        goog.forwardDeclare('MyType');
        /** @param {MyType} x */ function f(x) { return x; }
        /** @constructor */ var MyType = function() {};
        f(3);
        """,
        """
        actual parameter 1 of f does not match formal parameter
        found   : number
        required: (MyType|null)
        """);
  }

  @Test
  public void testForwardTypeDeclaration4() {
    testClosureTypes(
        """
        goog.forwardDeclare('MyType');
        /** @param {MyType} x */ function f(x) { return x; }
        /** @constructor */ var MyType = function() {};
        f(new MyType());
        """,
        null);
  }

  @Test
  public void testForwardTypeDeclaration5() {
    testClosureTypes(
        """
        goog.forwardDeclare('MyType');
        /**
         * @constructor
         * @extends {MyType}
         */ var YourType = function() {};
        /** @override */ YourType.prototype.method = function() {};
        """,
        "Could not resolve type in @extends tag of YourType");
  }

  @Test
  public void testForwardTypeDeclaration6() {
    testClosureTypesMultipleWarnings(
        """
        goog.forwardDeclare('MyType');
        /**
         * @constructor
         * @implements {MyType}
         */ var YourType = function() {};
        /** @override */ YourType.prototype.method = function() {};
        """,
        ImmutableList.of(
            "Could not resolve type in @implements tag of YourType",
            "property method not defined on any superclass of YourType"));
  }

  @Test
  public void testForwardTypeDeclaration7() {
    testClosureTypes(
        """
        goog.forwardDeclare('MyType');
        /** @param {MyType=} x */
        function f(x) { return x == undefined; }
        """,
        null);
  }

  @Test
  public void testForwardTypeDeclaration8() {
    testClosureTypes(
        """
        goog.forwardDeclare('MyType');
        /** @param {MyType} x */
        function f(x) { return x.name == undefined; }
        """,
        null);
  }

  @Test
  public void testForwardTypeDeclaration9() {
    testClosureTypes(
        """
        goog.forwardDeclare('MyType');
        /** @param {MyType} x */
        function f(x) { x.name = 'Bob'; }
        """,
        null);
  }

  @Test
  public void testForwardTypeDeclaration10() {
    String f =
        """
        goog.forwardDeclare('MyType');
        /** @param {MyType|number} x */ function f(x) { }
        """;
    testClosureTypes(f, null);
    testClosureTypes(f + "f(3);", null);
    testClosureTypes(
        f + "f('3');",
        """
        actual parameter 1 of f does not match formal parameter
        found   : string
        required: (MyType|null|number)
        """);
  }

  @Test
  public void testForwardTypeDeclaration12() {
    // We assume that {Function} types can produce anything, and don't
    // want to type-check them.
    testClosureTypes(
        """
        goog.forwardDeclare('MyType');
        /**
         * @param {!Function} ctor
         * @return {MyType}
         */
        function f(ctor) { return new ctor(); }
        """,
        null);
  }

  @Test
  public void testForwardTypeDeclaration13() {
    // Some projects use {Function} registries to register constructors
    // that aren't in their binaries. We want to make sure we can pass these
    // around, but still do other checks on them.
    testClosureTypes(
        """
        goog.forwardDeclare('MyType');
        /**
         * @param {!Function} ctor
         * @return {MyType}
         */
        function f(ctor) { return (new ctor()).impossibleProp; }
        """,
        "Property impossibleProp never defined on ?" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION);
  }

  @Test
  public void testDuplicateTypeDef() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @constructor */ goog.Bar = function() {};
            /** @typedef {number} */ goog.Bar;
            """)
        .addDiagnostic(
            """
            variable goog.Bar redefined with type None, original definition at testcode0:2 with type (typeof goog.Bar)
            """)
        .run();
  }

  @Test
  public void testTypeDef1() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @typedef {number} */ goog.Bar;
            /** @param {goog.Bar} x */ function f(x) {}
            f(3);
            """)
        .run();
  }

  @Test
  public void testTypeDef2() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @typedef {number} */ goog.Bar;
            /** @param {goog.Bar} x */ function f(x) {}
            f('3');
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testTypeDef3() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @typedef {number} */ var Bar;
            /** @param {Bar} x */ function f(x) {}
            f('3');
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testTypeDef4() {
    newTest()
        .addSource(
            """
            /** @constructor */ function A() {}
            /** @constructor */ function B() {}
            /** @typedef {(A|B)} */ var AB;
            /** @param {AB} x */ function f(x) {}
            f(new A()); f(new B()); f(1);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : number
            required: (A|B|null)
            """)
        .run();
  }

  @Test
  public void testTypeDef5() {
    // Notice that the error message is slightly different than
    // the one for testTypeDef4, even though they should be the same.
    // This is an implementation detail necessary for NamedTypes work out
    // OK, and it should change if NamedTypes ever go away.
    newTest()
        .addSource(
            """
            /** @param {AB} x */ function f(x) {}
            /** @constructor */ function A() {}
            /** @constructor */ function B() {}
            /** @typedef {(A|B)} */ var AB;
            f(new A()); f(new B()); f(1);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : number
            required: (A|B|null)
            """)
        .run();
  }

  @Test
  public void testCircularTypeDef() {
    newTest()
        .addSource(
            """
            var goog = {};
            /** @typedef {number|Array<goog.Bar>} */ goog.Bar;
            /** @param {goog.Bar} x */ function f(x) {}
            f(3); f([3]); f([[3]]);
            """)
        .run();
  }

  @Test
  public void testPrototypePropertyReference() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope(
            DEFAULT_EXTERNS,
            """
            /** @constructor */
            function Foo() {}
            /** @param {number} a */
            Foo.prototype.bar = function(a){};
            /** @param {Foo} f */
            function baz(f) {
              Foo.prototype.bar.call(f, 3);
            }
            """);
    assertThat(compiler.getErrorCount()).isEqualTo(0);
    assertThat(compiler.getWarningCount()).isEqualTo(0);

    assertThat(p.scope.getVar("Foo").getType()).isInstanceOf(FunctionType.class);
    FunctionType fooType = (FunctionType) p.scope.getVar("Foo").getType();
    assertThat(fooType.getPrototype().getPropertyType("bar").toString())
        .isEqualTo("function(this:Foo, number): undefined");
  }

  @Test
  public void testMissingProperty1a() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.bar = function() { return this.a; };
            Foo.prototype.baz = function() { this.a = 3; };
            """)
        .addDiagnostic("Property a never defined on Foo")
        .addDiagnostic("Property a never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty1b() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.bar = function() { return this.a; };
            Foo.prototype.baz = function() { this.a = 3; };
            """)
        .run();
  }

  @Test
  public void testMissingProperty2a() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.bar = function() { return this.a; };
            Foo.prototype.baz = function() { this.b = 3; };
            """)
        .addDiagnostic("Property a never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty2b() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.baz = function() { this.b = 3; };
            """)
        .addDiagnostic("Property b never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty3a() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.bar = function() { return this.a; };
            (new Foo).a = 3;
            """)
        .addDiagnostic("Property a never defined on Foo") // method
        .addDiagnostic("Property a never defined on Foo")
        .run(); // global assignment
  }

  @Test
  public void testMissingProperty3b() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.bar = function() { return this.a; };
            (new Foo).a = 3;
            """)
        .run();
  }

  @Test
  public void testMissingProperty4a() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.bar = function() { return this.a; };
            (new Foo).b = 3;
            """)
        .addDiagnostic("Property a never defined on Foo")
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .run();
  }

  @Test
  public void testMissingProperty4b() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            (new Foo).b = 3;
            """)
        .addDiagnostic("Property b never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty5() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.bar = function() { return this.a; };
            /** @constructor */ function Bar() { this.a = 3; };
            """)
        .addDiagnostic("Property a never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty6a() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.bar = function() { return this.a; };
            /** @constructor
             * @extends {Foo} */
            function Bar() { this.a = 3; };
            """)
        .addDiagnostic("Property a never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty6b() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.bar = function() { return this.a; };
            /** @constructor
             * @extends {Foo} */
            function Bar() { this.a = 3; };
            """)
        .run();
  }

  @Test
  public void testMissingProperty7() {
    newTest()
        .addSource(
            """
            /** @param {Object} obj */
            function foo(obj) { return obj.impossible; }
            """)
        .addDiagnostic(
            "Property impossible never defined on Object"
                + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testMissingProperty8() {
    newTest()
        .addSource(
            """
            /** @param {Object} obj */
            function foo(obj) { return typeof obj.impossible; }
            """)
        .run();
  }

  @Test
  public void testMissingProperty9() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Object} obj */
            function foo(obj) { if (obj.impossible) { return true; } }
            """)
        .run();
  }

  @Test
  public void testMissingProperty10() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Object} obj */
            function foo(obj) { while (obj.impossible) { return true; } }
            """)
        .run();
  }

  @Test
  public void testMissingProperty11() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Object} obj */
            function foo(obj) { for (;obj.impossible;) { return true; } }
            """)
        .run();
  }

  @Test
  public void testMissingProperty12() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Object} obj */
            function foo(obj) { do { } while (obj.impossible); }
            """)
        .run();
  }

  // Note: testMissingProperty{13,14} pertained to a deleted coding convention.

  @Test
  public void testMissingProperty15() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Object} x */
            function f(x) { if (x.foo) { x.foo(); } }
            """)
        .run();
  }

  @Test
  public void testMissingProperty16() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Object} x */
            function f(x) { x.foo(); if (x.foo) {} }
            """)
        .addDiagnostic(
            "Property foo never defined on Object" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testMissingProperty17() {
    newTest()
        .addSource(
            """
            /** @param {Object} x */
            function f(x) { if (typeof x.foo == 'function') { x.foo(); } }
            """)
        .run();
  }

  @Test
  public void testMissingProperty18() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Object} x */
            function f(x) { if (x.foo instanceof Function) { x.foo(); } }
            """)
        .run();
  }

  @Test
  public void testMissingProperty19() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Object} x */
            function f(x) { if (x.bar) { if (x.foo) {} } else { x.foo(); } }
            """)
        .addDiagnostic(
            "Property foo never defined on Object" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testMissingProperty20() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Object} x */
            function f(x) { if (x.foo) { } else { x.foo(); } }
            """)
        .addDiagnostic(
            "Property foo never defined on Object" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testMissingProperty21() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Object} x */
            function f(x) { x.foo && x.foo(); }
            """)
        .run();
  }

  @Test
  public void testMissingProperty22() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Object} x
             * @return {boolean} */
            function f(x) { return x.foo ? x.foo() : true; }
            """)
        .run();
  }

  @Test
  public void testMissingProperty23() {
    newTest()
        .addSource("function f(x) { x.impossible(); }")
        .addDiagnostic(
            "Property impossible never defined on x" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testMissingProperty24() {
    testClosureTypes(
        """
        goog.forwardDeclare('MissingType');
        /** @param {MissingType} x */
        function f(x) { x.impossible(); }
        """,
        null);
  }

  @Test
  public void testMissingProperty25() {
    newTest()
        .addSource(
            """
            /** @constructor */ var Foo = function() {};
            Foo.prototype.bar = function() {};
            /** @constructor */ var FooAlias = Foo;
            (new FooAlias()).bar();
            """)
        .run();
  }

  @Test
  public void testMissingProperty26() {
    newTest()
        .addSource(
            """
            /** @constructor */ var Foo = function() {};
            /** @constructor */ var FooAlias = Foo;
            FooAlias.prototype.bar = function() {};
            (new Foo()).bar();
            """)
        .run();
  }

  @Test
  public void testMissingProperty27() {
    testClosureTypes(
        """
        goog.forwardDeclare('MissingType');
        /** @param {?MissingType} x */
        function f(x) {
          for (var parent = x; parent; parent = parent.getParent()) {}
        }
        """,
        null);
  }

  @Test
  public void testMissingProperty28a() {
    newTest()
        .addSource(
            """
            function f(obj) {
              /** @type {*} */ obj.foo;
              return obj.foo;
            }
            """)
        .run();
  }

  @Test
  public void testMissingProperty28b() {
    newTest()
        .addSource(
            """
            function f(obj) {
              /** @type {*} */ obj.foo;
              return obj.foox;
            }
            """)
        .addDiagnostic(
            "Property foox never defined on obj" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testMissingProperty29() {
    // This used to emit a warning.
    newTest()
        .addExterns(
            """
            /** @constructor */ var Foo;
            Foo.prototype.opera;
            Foo.prototype.opera.postError;
            """)
        .addSource("")
        .run();
  }

  @Test
  public void testMissingProperty30a() {
    newTest()
        .addSource(
            """
            /** @return {*} */
            function f() {
             return {};
            }
            f().a = 3;
            /** @param {Object} y */ function g(y) { return y.a; }
            """)
        .addDiagnostic("Property a never defined on *")
        .addDiagnostic("Property a never defined on Object")
        .run();
  }

  @Test
  public void testMissingProperty30b() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @return {*} */
            function f() {
             return {};
            }
            f().a = 3;
            /** @param {Object} y */ function g(y) { return y.a; }
            """)
        .run();
  }

  @Test
  public void testMissingProperty31a() {
    newTest()
        .addSource(
            """
            /** @return {Array|number} */
            function f() {
             return [];
            }
            f().a = 3;
            """)
        .addDiagnostic("Property a never defined on (Array|Number)")
        .run();
  }

  @Test
  public void testMissingProperty31b() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @return {Array|number} */
            function f() {
             return [];
            }
            f().a = 3;
            /** @param {Array} y */ function g(y) { return y.a; }
            """)
        .run();
  }

  @Test
  public void testMissingProperty32() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @return {Array|number} */
            function f() {
             return [];
            }
            f().a = 3;
            /** @param {Date} y */ function g(y) { return y.a; }
            """)
        .addDiagnostic("Property a never defined on Date")
        .run();
  }

  @Test
  public void testMissingProperty33() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Object} x */
            function f(x) { !x.foo || x.foo(); }
            """)
        .run();
  }

  @Test
  public void testMissingProperty34() {
    newTest()
        .addSource(
            """
            /** @fileoverview
             * @suppress {missingProperties} */
            /** @constructor */ function Foo() {}
            Foo.prototype.bar = function() { return this.a; };
            Foo.prototype.baz = function() { this.b = 3; };
            """)
        .run();
  }

  @Test
  public void testMissingProperty35a() {
    // Bar has specialProp defined, so Bar|Baz may have specialProp defined.
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @constructor */ function Bar() {}
            /** @constructor */ function Baz() {}
            /** @param {Foo|Bar} x */ function f(x) { x.specialProp = 1; }
            /** @param {Bar|Baz} x */ function g(x) { return x.specialProp; }
            """)
        .addDiagnostic("Property specialProp never defined on (Foo|Bar)")
        .addDiagnostic("Property specialProp never defined on (Bar|Baz)")
        .run();
  }

  @Test
  public void testMissingProperty35b() {
    // Bar has specialProp defined, so Bar|Baz may have specialProp defined.
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @constructor */ function Bar() {}
            /** @constructor */ function Baz() {}
            /** @param {Foo|Bar} x */ function f(x) { x.specialProp = 1; }
            /** @param {Bar|Baz} x */ function g(x) { return x.specialProp; }
            """)
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .run();
  }

  @Test
  public void testMissingProperty36a() {
    // Foo has baz defined, and SubFoo has bar defined, so some objects with
    // bar may have baz.
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.baz = 0;
            /** @constructor
             * @extends {Foo} */ function SubFoo() {}
            SubFoo.prototype.bar = 0;
            /** @param {{bar: number}} x */ function f(x) { return x.baz; }
            """)
        .addDiagnostic("Property baz never defined on x")
        .run();
  }

  @Test
  public void testMissingProperty36b() {
    // Foo has baz defined, and SubFoo has bar defined, so some objects with
    // bar may have baz.
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.baz = 0;
            /** @constructor
             * @extends {Foo} */ function SubFoo() {}
            SubFoo.prototype.bar = 0;
            /** @param {{bar: number}} x */ function f(x) { return x.baz; }
            """)
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .run();
  }

  @Test
  public void testMissingProperty37a() {
    // This used to emit a missing property warning because we couldn't
    // determine that the inf(Foo, {isVisible:boolean}) == SubFoo.
    newTest()
        .addSource(
            """
            /** @param {{isVisible: boolean}} x */
            function f(x){
              x.isVisible = false;
            }
            /** @constructor */
            function Foo() {}
            /**
             * @constructor
             * @extends {Foo}
             */
            function SubFoo() {}
            /** @type {boolean} */
            SubFoo.prototype.isVisible = true;
            /**
             * @param {Foo} x
             * @return {boolean}
             */
            function g(x) { return x.isVisible; }
            """)
        .addDiagnostic("Property isVisible never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty37b() {
    // This used to emit a missing property warning because we couldn't
    // determine that the inf(Foo, {isVisible:boolean}) == SubFoo.
    newTest()
        .addSource(
            """
            /** @param {{isVisible: boolean}} x */ function f(x){
              x.isVisible = false;
            }
            /** @constructor */ function Foo() {}
            /**
             * @constructor
             * @extends {Foo}
             */ function SubFoo() {}
            /** @type {boolean} */ SubFoo.prototype.isVisible = true;
            /**
             * @param {Foo} x
             * @return {boolean}
             */
            function g(x) { return x.isVisible; }
            """)
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .run();
  }

  @Test
  public void testMissingProperty38() {
    newTest()
        .addSource(
            """
            /** @constructor */ function Foo() {}
            /** @constructor */ function Bar() {}
            /** @return {Foo|Bar} */ function f() { return new Foo(); }
            f().missing;
            """)
        .addDiagnostic("Property missing never defined on (Foo|Bar)")
        .run();
  }

  @Test
  public void testMissingProperty39a() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            """
            /** @return {string|number} */ function f() { return 3; }
            f().length;
            """)
        .run();
  }

  @Test
  public void testMissingProperty39b() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            """
            /** @return {string|number} */ function f() { return 3; }
            f().length;
            """)
        .addDiagnostic("Property length not defined on all member types of (String|Number)")
        .run();
  }

  @Test
  public void testMissingProperty40a() {
    testClosureTypes(
        """
        goog.forwardDeclare('MissingType');
        /** @param {MissingType} x */
        function f(x) { x.impossible(); }
        """,
        null);
  }

  @Test
  public void testMissingProperty40b() {
    testClosureTypes(
        """
        goog.forwardDeclare('MissingType');
        /** @param {(Array|MissingType)} x */
        function f(x) { x.impossible(); }
        """,
        "Property impossible not defined on all member types of x");
  }

  @Test
  public void testMissingProperty41a() {
    newTest()
        .addSource(
            """
            /** @param {(Array|Date)} x */
            function f(x) { if (x.impossible) x.impossible(); }
            """)
        .addDiagnostic("Property impossible never defined on (Array|Date)")
        .addDiagnostic("Property impossible never defined on (Array|Date)")
        .run();
  }

  @Test
  public void testMissingProperty41b() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {(Array|Date)} x */
            function f(x) { if (x.impossible) x.impossible(); }
            """)
        .run();
  }

  @Test
  public void testMissingProperty42() {
    newTest()
        .addSource(
            """
            /** @param {Object} x */
            function f(x) {
              if (typeof x.impossible == 'undefined') throw Error();
              return x.impossible;
            }
            """)
        .run();
  }

  @Test
  public void testMissingProperty43() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            function f(x) {
             return /** @type {number} */ (x.impossible) && 1;
            }
            """)
        .run();
  }

  @Test
  public void testMissingProperty_notReportedInPropertyAbsenceCheck() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            function f(/** !Object */ x) {
              if (x.y == null) throw new Error();
            }
            """)
        .run();
  }

  // since optional chaining is a property test (tests for the existence of x.y), no warnings
  // about missing properties are emitted
  @Test
  public void optChainGetPropAllowLoosePropertyAccess() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {?Object} x */
            function f(x) {
              x.y?.z;
            }
            """)
        .run();
  }

  // this is the same test as above except that it does not use optional chaining so it should
  // emit a warning about missing properties
  @Test
  public void normalGetPropNotAllowLoosePropertyAccess() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {?Object} x */
            function f(x) {
              x.y.z;
            }
            """)
        .addDiagnostic(
            "Property y never defined on Object" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  // since optional chaining is a property test (tests for the existence of x.y), no warnings
  // about missing properties are emitted
  @Test
  public void optChainGetElemAllowLoosePropertyAccess() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {?Object} x */
            function f(x) {
              x.y?.[z];
            }
            """)
        .run();
  }

  // this is the same test as above except that it does not use optional chaining so it should emit
  // a warning about missing properties
  @Test
  public void normalGetElemNotAllowLoosePropertyAccess() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {?Object} x */
            function f(x) {
              x.y[z];
            }
            """)
        .addDiagnostic(
            "Property y never defined on Object" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  // since optional chaining is a property test (tests for the existence of x.y), no warnings
  // about missing properties are emitted
  @Test
  public void optChainCallAllowLoosePropertyAccess() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {?Object} x */
            function f(x) {
              x.y?.();
            }
            """)
        .run();
  }

  // this is the same test as above except that it does not use optional chaining so it should emit
  // a warning about missing properties
  @Test
  public void normalCallNotAllowLoosePropertyAccess() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {?Object} x */
            function f(x) {
              x.y();
            }
            """)
        .addDiagnostic(
            "Property y never defined on Object" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  // prop.access?.() is property test and should allow loose property access
  // but x?.(prop.access) is not
  @Test
  public void getNotFirstChildOfOptChainCallNotAllowLoosePropertyAccess() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {?Object} x */
            function f(x) {
              return false;
            }
            f?.(x.y)
            """)
        .addDiagnostic("Property y never defined on x" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  // prop.access?.[x] is property test and should allow loose property access
  // but x?.[prop.access] is not
  @Test
  public void getNotFirstChildOfOptionalGetElemNotAllowLoosePropertyAccess() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {?Object} x */
            function f(x) {
              x?.[y.z];
            }
            """)
        .addDiagnostic("Property z never defined on y" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testOptChainGetPropProvidesThisForMethodCall() {
    newTest()
        .addSource(
            """
            class A {
              foo() {}
            }
            /** @param {?A} a */
            function f(a) {
            // TypeCheck should not complain that foo() is getting called without a correctly typed
            // `this` value.
              a?.foo();
            }
            """)
        .run();
  }

  @Test
  public void testReflectObject1() {
    testClosureTypes(
        """
        goog.reflect = {};
        goog.reflect.object = function(x, y){};
        /** @constructor */ function A() {}
        goog.reflect.object(A, {x: 3});
        """,
        null);
  }

  @Test
  public void testReflectObject2() {
    testClosureTypes(
        """
        goog.reflect = {};
        goog.reflect.object = function(x, y){};
        /** @param {string} x */ function f(x) {}
        /** @constructor */ function A() {}
        goog.reflect.object(A, {x: f(1 + 1)});
        """,
        """
        actual parameter 1 of f does not match formal parameter
        found   : number
        required: string
        """);
  }

  @Test
  public void testLends1() {
    newTest()
        .addSource(
            """
            function extend(x, y) {}
            /** @constructor */ function Foo() {}
            extend(Foo, /** @lends */ ({bar: 1}));
            """)
        .addDiagnostic(
            "Bad type annotation. missing object name in @lends tag." + BAD_TYPE_WIKI_LINK)
        .run();
  }

  @Test
  public void testLends2() {
    newTest()
        .addSource(
            """
            function extend(x, y) {}
            /** @constructor */ function Foo() {}
            extend(Foo, /** @lends {Foob} */ ({bar: 1}));
            """)
        .addDiagnostic("Variable Foob not declared before @lends annotation.")
        .run();
  }

  @Test
  public void testLends3() {
    newTest()
        .addSource(
            """
            function extend(x, y) {}
            /** @constructor */ function Foo() {}
            extend(Foo, {bar: 1});
            alert(Foo.bar);
            """)
        .addDiagnostic("Property bar never defined on Foo")
        .run();
  }

  @Test
  public void testLends4() {
    newTest()
        .addSource(
            """
            function extend(x, y) {}
            /** @constructor */ function Foo() {}
            extend(Foo, /** @lends {Foo} */ ({bar: 1}));
            alert(Foo.bar);
            """)
        .run();
  }

  @Test
  public void testLends5() {
    newTest()
        .addSource(
            """
            function extend(x, y) {}
            /** @constructor */ function Foo() {}
            extend(Foo, {bar: 1});
            alert((new Foo()).bar);
            """)
        .addDiagnostic("Property bar never defined on Foo")
        .run();
  }

  @Test
  public void testLends6() {
    newTest()
        .addSource(
            """
            function extend(x, y) {}
            /** @constructor */ function Foo() {}
            extend(Foo, /** @lends {Foo.prototype} */ ({bar: 1}));
            alert((new Foo()).bar);
            """)
        .run();
  }

  @Test
  public void testLends7() {
    newTest()
        .addSource(
            """
            function extend(x, y) {}
            /** @constructor */ function Foo() {}
            extend(Foo, /** @lends {Foo.prototype|Foo} */ ({bar: 1}));
            """)
        .addDiagnostic("Bad type annotation. expected closing }" + BAD_TYPE_WIKI_LINK)
        .run();
  }

  @Test
  public void testLends8() {
    newTest()
        .addSource(
            """
            function extend(x, y) {}
            /** @type {number} */ var Foo = 3;
            extend(Foo, /** @lends {Foo} */ ({bar: 1}));
            """)
        .addDiagnostic("May only lend properties to object types. Foo has type number.")
        .run();
  }

  @Test
  public void testLends9() {
    testClosureTypesMultipleWarnings(
        """
        function extend(x, y) {}
        /** @constructor */ function Foo() {}
        extend(Foo, /** @lends {!Foo} */ ({bar: 1}));
        """,
        ImmutableList.of(
            "Bad type annotation. expected closing }" + BAD_TYPE_WIKI_LINK,
            "Bad type annotation. missing object name in @lends tag." + BAD_TYPE_WIKI_LINK));
  }

  @Test
  public void testDeclaredNativeTypeEquality() {
    Node n = parseAndTypeCheck("/** @constructor */ function Object() {};");
    assertTypeEquals(
        registry.getNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE), n.getFirstChild().getJSType());
  }

  @Test
  public void testUndefinedVar() {
    Node n = parseAndTypeCheck("var undefined;");
    assertTypeEquals(
        registry.getNativeType(JSTypeNative.VOID_TYPE), n.getFirstFirstChild().getJSType());
  }

  @Test
  public void testFlowScopeBug1() {
    Node n =
        parseAndTypeCheck(
            """
            /** @param {number} a
            * @param {number} b */
            function f(a, b) {
            /** @type {number} */
            var i = 0;
            for (; (i + a) < b; ++i) {}}
            """);

    // check the type of the add node for i + f
    assertTypeEquals(
        registry.getNativeType(JSTypeNative.NUMBER_TYPE),
        n.getFirstChild()
            .getLastChild()
            .getLastChild()
            .getFirstChild()
            .getNext()
            .getFirstChild()
            .getJSType());
  }

  @Test
  public void testFlowScopeBug2() {
    Node n =
        parseAndTypeCheck(
            """
            /** @constructor */ function Foo() {};
            Foo.prototype.hi = false;
            function foo(a, b) {
              /** @type {Array} */
              var arr;
              /** @type {number} */
              var iter;
              for (iter = 0; iter < arr.length; ++ iter) {
                /** @type {Foo} */
                var afoo = arr[iter];
                afoo;
              }
            }
            """);

    // check the type of afoo when referenced
    assertTypeEquals(
        registry.createNullableType(registry.getGlobalType("Foo")),
        n.getLastChild()
            .getLastChild()
            .getLastChild()
            .getLastChild()
            .getLastChild()
            .getLastChild()
            .getJSType());
  }

  @Test
  public void testAddSingletonGetter() {
    Node n =
        parseAndTypeCheck(
            """
            /** @constructor */ function Foo() {};
            goog.addSingletonGetter(Foo);
            """);
    ObjectType o = (ObjectType) n.getFirstChild().getJSType();
    assertThat(o.getPropertyType("getInstance").toString()).isEqualTo("function(): Foo");
    assertThat(o.getPropertyType("instance_").toString()).isEqualTo("Foo");
  }

  @Test
  public void testTypeCheckStandaloneAST() {
    Node externs = IR.root();
    Node firstScript = compiler.parseTestCode("function Foo() { }");
    typeCheck(firstScript);
    Node root = IR.root(externs, IR.root(firstScript.detach()));
    TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);
    TypedScope topScope = scopeCreator.createScope(root, null);

    Node secondScript = compiler.parseTestCode("new Foo");

    firstScript.replaceWith(secondScript);

    new TypeCheck(
            compiler,
            new SemanticReverseAbstractInterpreter(registry),
            registry,
            topScope,
            scopeCreator)
        .process(externs, secondScript.getParent());

    assertThat(compiler.getWarningCount()).isEqualTo(1);
    assertThat(compiler.getWarnings().get(0).description())
        .isEqualTo("cannot instantiate non-constructor, found type: function(): undefined");
  }

  @Test
  public void testUpdateParameterTypeOnClosure() {
    newTest()
        .addExterns(
            """
            /**
            * @constructor
            * @param {*=} opt_value
            * @return {!Object}
            */
            function Object(opt_value) {}
            /**
            * @constructor
            * @param {...*} var_args
            */
            function Function(var_args) {}
            /**
            * @type {Function}
            */
            Object.prototype.constructor = function() {};
            """)
        .addSource(
            """
            /**
            * @param {function(): boolean} fn
            */
            function f(fn) {}
            f(function() { });
            """)
        .run();
  }

  @Test
  public void testTemplatedThisType1() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {}
            /**
             * @this {T}
             * @return {T}
             * @template T
             */
            Foo.prototype.method = function() {};
            /**
             * @constructor
             * @extends {Foo}
             */
            function Bar() {}
            var g = new Bar().method();
            /**
             * @param {number} a
             */
            function compute(a) {};
            compute(g);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of compute does not match formal parameter
            found   : Bar
            required: number
            """)
        .run();
  }

  @Test
  public void testTemplatedThisType2() {
    newTest()
        .addSource(
            """
            /**
             * @this {Array<T>|{length:number}}
             * @return {T}
             * @template T
             */
            Array.prototype.method = function() {};
            (function(){
              Array.prototype.method.call(arguments);
            })();
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testTemplateType1() {
    newTest()
        .addSource(
            """
            /**
            * @param {T} x
            * @param {T} y
            * @param {function(this:T, ...)} z
            * @template T
            */
            function f(x, y, z) {}
            f(this, this, function() { this });
            """)
        .run();
  }

  @Test
  public void testTemplateType2() {
    // "this" types need to be coerced for ES3 style function or left
    // allow for ES5-strict methods.
    newTest()
        .addSource(
            """
            /**
            * @param {T} x
            * @param {function(this:T, ...)} y
            * @template T
            */
            function f(x, y) {}
            f(0, function() {});
            """)
        .run();
  }

  @Test
  public void testTemplateType3() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} v
             * @param {function(T)} f
             * @template T
             */
            function call(v, f) { f.call(null, v); }
            /** @type {string} */ var s;
            call(3, function(x) {
             x = true;
             s = x;
            });
            """)
        .addDiagnostic(
            """
            assignment
            found   : boolean
            required: string
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testTemplateType4() {
    newTest()
        .addSource(
            """
            /**
             * @param {...T} p
             * @return {T}
             * @template T
             */
            function fn(p) { return p; }
            /** @type {!Object} */ var x;
            x = fn(3, null);
            """)
        .addDiagnostic(
            """
            assignment
            found   : (null|number)
            required: Object
            """)
        .run();
  }

  @Test
  public void testTemplateType5() {
    newTest()
        .addSource(
            """
            const CGI_PARAM_RETRY_COUNT = 'rc';

            /**
             * @param {...T} p
             * @return {T}
             * @template T
             */
            function fn(p) { return p; }
            /** @type {!Object} */ var x;

            /** @return {void} */
            function aScope() {
              x = fn(CGI_PARAM_RETRY_COUNT, 1);
            }
            """)
        .addDiagnostic(
            """
            assignment
            found   : (number|string)
            required: Object
            """)
        .run();
  }

  @Test
  public void testTemplateType6() {
    newTest()
        .addSource(
            """
            /**
             * @param {Array<T>} arr
             * @param {?function(T)} f
             * @return {T}
             * @template T
             */
            function fn(arr, f) { return arr[0]; }
            /** @param {Array<number>} arr */ function g(arr) {
              /** @type {!Object} */ var x = fn.call(null, arr, null);
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: Object
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testTemplateType7() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            """
            /** @type {!Array<string>} */
            var query = [];
            query.push(1);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Array.prototype.push does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testTemplateType8() {
    newTest()
        .addSource(
            """
            /** @constructor
             * @template S,T
             */
            function Bar() {}
            /**
             * @param {Bar<T>} bar
             * @return {T}
             * @template T
             */
            function fn(bar) {}
            /** @param {Bar<number>} bar */ function g(bar) {
              /** @type {!Object} */ var x = fn(bar);
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: Object
            """)
        .run();
  }

  @Test
  public void testTemplateType9() {
    // verify interface type parameters are recognized.
    newTest()
        .addSource(
            """
            /** @interface
             * @template S,T
             */
            function Bar() {}
            /**
             * @param {Bar<T>} bar
             * @return {T}
             * @template T
             */
            function fn(bar) {}
            /** @param {Bar<number>} bar */ function g(bar) {
              /** @type {!Object} */ var x = fn(bar);
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: Object
            """)
        .run();
  }

  @Test
  public void testTemplateType10() {
    // verify a type parameterized with unknown can be assigned to
    // the same type with any other type parameter.
    newTest()
        .addSource(
            """
            /** @constructor
             * @template T
             */
            function Bar() {}

            /** @type {!Bar<?>} */ var x;
            /** @type {!Bar<number>} */ var y;
            y = x;
            """)
        .run();
  }

  @Test
  public void testTemplateType11() {
    // verify that assignment/subtype relationships work when extending
    // templatized types.
    newTest()
        .addSource(
            """
            /** @constructor
             * @template T
             */
            function Foo() {}

            /** @constructor
             * @extends {Foo<string>}
             */
            function A() {}

            /** @constructor
             * @extends {Foo<number>}
             */
            function B() {}

            /** @type {!Foo<string>} */ var a = new A();
            /** @type {!Foo<string>} */ var b = new B();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : B
            required: Foo<string>
            """)
        .run();
  }

  @Test
  public void testTemplateType12() {
    // verify that assignment/subtype relationships work when implementing
    // templatized types.
    newTest()
        .addSource(
            """
            /** @interface
             * @template T
             */
            function Foo() {}

            /** @constructor
             * @implements {Foo<string>}
             */
            function A() {}

            /** @constructor
             * @implements {Foo<number>}
             */
            function B() {}

            /** @type {!Foo<string>} */ var a = new A();
            /** @type {!Foo<string>} */ var b = new B();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : B
            required: Foo<string>
            """)
        .run();
  }

  @Test
  public void testTemplateType13() {
    // verify that assignment/subtype relationships work when extending
    // templatized types.
    newTest()
        .addSource(
            """
            /** @constructor
             * @template T
             */
            function Foo() {}

            /** @constructor
             * @template T
             * @extends {Foo<T>}
             */
            function A() {}

            var a1 = new A();
            var a2 = /** @type {!A<string>} */ (new A());
            var a3 = /** @type {!A<number>} */ (new A());
            /** @type {!Foo<string>} */ var f1 = a1;
            /** @type {!Foo<string>} */ var f2 = a2;
            /** @type {!Foo<string>} */ var f3 = a3;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : A<number>
            required: Foo<string>
            """)
        .run();
  }

  @Test
  public void testTemplateType14() {
    // verify that assignment/subtype relationships work when implementing
    // templatized types.
    newTest()
        .addSource(
            """
            /** @interface
             * @template T
             */
            function Foo() {}

            /** @constructor
             * @template T
             * @implements {Foo<T>}
             */
            function A() {}

            var a1 = new A();
            var a2 = /** @type {!A<string>} */ (new A());
            var a3 = /** @type {!A<number>} */ (new A());
            /** @type {!Foo<string>} */ var f1 = a1;
            /** @type {!Foo<string>} */ var f2 = a2;
            /** @type {!Foo<string>} */ var f3 = a3;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : A<number>
            required: Foo<string>
            """)
        .run();
  }

  @Test
  public void testTemplateType15() {
    newTest()
        .addSource(
            """
            /**
             * @param {{foo:T}} p
             * @return {T}
             * @template T
             */
            function fn(p) { return p.foo; }
            /** @type {!Object} */ var x;
            x = fn({foo:3});
            """)
        .addDiagnostic(
            """
            assignment
            found   : number
            required: Object
            """)
        .run();
  }

  @Test
  public void testTemplateType16() {
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {
              /** @type {number} */ this.foo = 1
            }
            /**
             * @param {{foo:T}} p
             * @return {T}
             * @template T
             */
            function fn(p) { return p.foo; }
            /** @type {!Object} */ var x;
            x = fn(new C());
            """)
        .addDiagnostic(
            """
            assignment
            found   : number
            required: Object
            """)
        .run();
  }

  @Test
  public void testTemplateType17() {
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {}
            C.prototype.foo = 1;
            /**
             * @param {{foo:T}} p
             * @return {T}
             * @template T
             */
            function fn(p) { return p.foo; }
            /** @type {!Object} */ var x;
            x = fn(new C());
            """)
        .addDiagnostic(
            """
            assignment
            found   : number
            required: Object
            """)
        .run();
  }

  @Test
  public void testTemplateType18() {
    // Until template types can be restricted to exclude undefined, they
    // are always optional.
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {}
            C.prototype.foo = 1;
            /**
             * @param {{foo:T}} p
             * @return {T}
             * @template T
             */
            function fn(p) { return p.foo; }
            /** @type {!Object} */ var x;
            x = fn({});
            """)
        .run();
  }

  @Test
  public void testTemplateType19() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} t
             * @param {U} u
             * @return {{t:T, u:U}}\s
             * @template T,U
             */
            function fn(t, u) { return {t:t, u:u}; }
            /** @type {null} */ var x = fn(1, 'str');
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : {
              t: number,
              u: string
            }
            required: null
            """)
        .run();
  }

  @Test
  public void testTemplateType20() {
    // "this" types is inferred when the parameters are declared.
    newTest()
        .addSource(
            """
            /** @constructor */ function C() {
              /** @type {void} */ this.x;
            }
            /**
            * @param {T} x
            * @param {function(this:T, ...)} y
            * @template T
            */
            function f(x, y) {}
            f(new C, /** @param {number} a */ function(a) {this.x = a;});
            """)
        .addDiagnostic(
            """
            assignment to property x of C
            found   : number
            required: undefined
            """)
        .run();
  }

  @Test
  public void testTemplateType21() {
    // "this" types is inferred when the parameters are declared.
    newTest()
        .addSource(
            """
            /** @interface @template T */ function A() {}
            /** @constructor @implements {A<Foo>} */
            function Foo() {}
            /** @constructor @implements {A<Bar>} */
            function Bar() {}
            /** @type {!Foo} */
            var x = new Bar();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Bar
            required: Foo
            """)
        .run();
  }

  @Test
  public void testTemplateType22() {
    // "this" types is inferred when the parameters are declared.
    newTest()
        .addSource(
            """
            /** @interface @template T */ function A() {}
            /** @interface @template T */ function B() {}
            /** @constructor @implements {A<Foo>} */
            function Foo() {}
            /** @constructor @implements {B<Foo>} */
            function Bar() {}
            /** @constructor @implements {B<Foo>} */
            function Qux() {}
            /** @type {!Qux} */
            var x = new Bar();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Bar
            required: Qux
            """)
        .run();
  }

  @Test
  public void testTemplateType23() {
    // "this" types is inferred when the parameters are declared.
    newTest()
        .addSource(
            """
            /** @interface @template T */ function A() {}
            /** @constructor @implements {A<Foo>} */
            function Foo() {}
            /** @type {!Foo} */
            var x = new Foo();
            """)
        .run();
  }

  @Test
  public void testTemplateType24() {
    // Recursive templated type definition.
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @template T
             * @param {T} x
             */
            function Foo(x) {
              /** @type {T} */
              this.p = x;
            }
            /** @return {Foo<Foo<T>>} */
            Foo.prototype.m = function() {
              return null;
            };
            /** @return {T} */
            Foo.prototype.get = function() {
              return this.p;
            };
            var /** null */ n = new Foo(new Object).m().get();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (Foo<Object>|null)
            required: null
            """)
        .run();
  }

  @Test
  public void testTemplateType25() {
    // Non-nullable recursive templated type definition.
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @template T
             * @param {T} x
             */
            function Foo(x) {
              /** @type {T} */
              this.p = x;
            }
            /** @return {!Foo<!Foo<T>>} */
            Foo.prototype.m = function() {
              return new Foo(new Foo(new Object));
            };
            /** @return {T} */
            Foo.prototype.get = function() {
              return this.p;
            };
            var /** null */ n = new Foo(new Object).m().get();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Foo<Object>
            required: null
            """)
        .run();
  }

  @Test
  public void testTemplateType26() {
    // Class hierarchies which use the same template parameter name should not be treated as
    // infinite recursion.
    newTest()
        .addSource(
            """
            /**
             * @param {T} bar
             * @constructor
             * @template T
             */
            function Bar(bar) {
              /** @type {T} */
              this.bar = bar;
            }
            /** @return {T} */
            Bar.prototype.getBar = function() {
              return this.bar;
            };
            /**
             * @param {T} foo
             * @constructor
             * @template T
             * @extends {Bar<!Array<T>>}
             */
            function Foo(foo) {
              /** @type {T} */
              this.foo = foo;
            }
            var /** null */ n = new Foo(new Object).getBar();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Array<Object>
            required: null
            """)
        .run();
  }

  @Test
  public void testTemplateTypeCollidesWithParameter() {
    // Function templates are in the same scope as parameters, so cannot collide.
    newTest()
        .addSource(
            """
            /**
             * @param {T} T
             * @template T
             */
            function f(T) {}
            """)
        .addDiagnostic(
            """
            variable T redefined with type undefined, original definition at testcode0:5 with type T
            """)
        .run();
  }

  @Test
  public void testTemplateTypeForwardReference() {
    // TODO(martinprobst): the test below asserts incorrect behavior for backwards compatibility.
    newTest()
        .addSource(
            """
            /** @param {!Foo<string>} x */
            function f(x) {}

            /**
             * @template T
             * @constructor
             */
            function Foo() {}

            /** @param {!Foo<number>} x */
            function g(x) {
              f(x);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : Foo<number>
            required: Foo<string>
            """)
        .run();
  }

  @Test
  public void testTemplateTypeForwardReference_declared() {
    compiler.forwardDeclareType("Foo");
    newTest()
        .addSource(
            """
            /** @param {!Foo<string>} x */
            function f(x) {}

            /**
             * @template T
             * @constructor
             */
            function Foo() {}

            /** @param {!Foo<number>} x */
            function g(x) {
              f(x);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : Foo<number>
            required: Foo<string>
            """)
        .run();
  }

  @Test
  public void testTemplateTypeForwardReferenceFunctionWithExtra() {
    // TODO(johnlenz): report an error when forward references contain extraneous
    // type arguments.
    newTest()
        .addSource(
            """
            /** @param {!Foo<string, boolean>} x */
            function f(x) {}

            /**
             * @constructor
             * @template T
             */
            function Foo() {}

            /** @param {!Foo<number>} x */
            function g(x) {
              f(x);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : Foo<number>
            required: Foo<string>
            """)
        .run();
  }

  @Test
  public void testTemplateTypeForwardReferenceVar() {
    newTest()
        .addSource(
            """
            /** @param {!Foo<string>} x */
            function f(x) {}

            /**
             * @template T
             * @constructor
             */
            var Foo = function() {}

            /** @param {!Foo<number>} x */
            function g(x) {
              f(x);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : Foo<number>
            required: Foo<string>
            """)
        .run();
  }

  @Test
  public void testTemplateTypeForwardReference_declaredMissing() {
    compiler.forwardDeclareType("Foo");
    compiler.forwardDeclareType("DoesNotExist");
    newTestLegacy() // using the legacy test method to support compiler.forwardDeclareType
        .addSource(
            """
            /** @param {!Foo<DoesNotExist>} x */
            function f(x) {}
            """)
        .run();
  }

  @Test
  public void testTemplateTypeForwardReference_extends() {
    compiler.forwardDeclareType("Bar");
    compiler.forwardDeclareType("Baz");
    newTestLegacy() // using the legacy test method to support compiler.forwardDeclareType
        .addSource(
            """
            /** @constructor @extends {Bar<Baz>} */
            function Foo() {}
            /** @constructor */
            function Bar() {}
            """)
        .run();
  }

  @Test
  public void testSubtypeNotTemplated1() {
    newTest()
        .addSource(
            """
            /** @interface @template T */ function A() {}
            /** @constructor @implements {A<U>} @template U */ function Foo() {}
            function f(/** (!Object|!Foo<string>) */ x) {
              var /** null */ n = x;
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Object
            required: null
            """)
        .run();
  }

  @Test
  public void testSubtypeNotTemplated2() {
    newTest()
        .addSource(
            """
            /** @interface @template T */ function A() {}
            /** @constructor @implements {A<U>} @template U */ function Foo() {}
            function f(/** (!Object|!Foo) */ x) {
              var /** null */ n = x;
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Object
            required: null
            """)
        .run();
  }

  @Test
  public void testTemplateTypeWithUnresolvedType() {
    testClosureTypes(
        """
        goog.forwardDeclare('Color');
        /** @interface @template T */ function C() {}
        /** @return {!Color} */ C.prototype.method;
        /** @constructor @implements {C} */ function D() {}
        /** @override */ D.prototype.method = function() {};
        """,
        null); // no warning expected.
  }

  @Test
  public void testTemplateTypeWithTypeDef1a() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @template T
             * @param {T} x
             */
            function Generic(x) {}

            /** @constructor */
            function Foo() {}

            /** @typedef {!Foo} */
            var Bar;

            /** @type {Generic<!Foo>} */ var x;
            /** @type {Generic<!Bar>} */ var y;

            x = y;
            /** @type {null} */ var z1 = y;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (Generic<Foo>|null)
            required: null
            """)
        .run();
  }

  @Test
  public void testTemplateTypeWithTypeDef1b() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @template T
             * @param {T} x
             */
            function Generic(x) {}

            /** @constructor */
            function Foo() {}

            /** @typedef {!Foo} */
            var Bar;

            /** @type {Generic<!Foo>} */ var x;
            /** @type {Generic<!Bar>} */ var y;

            y = x;
            /** @type {null} */ var z1 = x;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (Generic<Foo>|null)
            required: null
            """)
        .run();
  }

  @Test
  public void testTemplateTypeWithTypeDef2a() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @template T
             * @param {T} x
             */
            function Generic(x) {}

            /** @constructor */
            function Foo() {}

            /** @typedef {!Foo} */
            var Bar;

            function f(/** Generic<!Bar> */ x) {}
            /** @type {Generic<!Foo>} */ var x;
            f(x);
            """)
        .run(); // no warning expected.
  }

  @Test
  public void testTemplateTypeWithTypeDef2b() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @template T
             * @param {T} x
             */
            function Generic(x) {}

            /** @constructor */
            function Foo() {}

            /** @typedef {!Foo} */
            var Bar;

            function f(/** Generic<!Bar> */ x) {}
            /** @type {Generic<!Bar>} */ var x;
            f(x);
            """)
        .run(); // no warning expected.
  }

  @Test
  public void testTemplateTypeWithTypeDef2c() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @template T
             * @param {T} x
             */
            function Generic(x) {}

            /** @constructor */
            function Foo() {}

            /** @typedef {!Foo} */
            var Bar;

            function f(/** Generic<!Foo> */ x) {}
            /** @type {Generic<!Foo>} */ var x;
            f(x);
            """)
        .run(); // no warning expected.
  }

  @Test
  public void testTemplateTypeWithTypeDef2d() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @template T
             * @param {T} x
             */
            function Generic(x) {}

            /** @constructor */
            function Foo() {}

            /** @typedef {!Foo} */
            var Bar;

            function f(/** Generic<!Foo> */ x) {}
            /** @type {Generic<!Bar>} */ var x;
            f(x);
            """)
        .run(); // no warning expected.
  }

  @Test
  public void testTemplatedFunctionInUnion1() {
    newTest()
        .addSource(
            """
            /**
            * @param {T} x
            * @param {function(this:T, ...)|{fn:Function}} z
            * @template T
            */
            function f(x, z) {}
            f([], function() { /** @type {string} */ var x = this });
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Array<?>
            required: string
            """)
        .run();
  }

  @Test
  public void testTemplateTypeRecursion1() {
    newTest()
        .addSource(
            """
            /** @typedef {{a: D2}} */
            var D1;

            /** @typedef {{b: D1}} */
            var D2;

            fn(x);


            /**
             * @param {!D1} s
             * @template T
             */
            var fn = function(s) {};
            """)
        .run();
  }

  @Test
  public void testTemplateTypeRecursion2() {
    newTest()
        .addSource(
            """
            /** @typedef {{a: D2}} */
            var D1;

            /** @typedef {{b: D1}} */
            var D2;

            /** @type {D1} */ var x;
            fn(x);


            /**
             * @param {!D1} s
             * @template T
             */
            var fn = function(s) {};
            """)
        .run();
  }

  @Test
  public void testTemplateTypeRecursion3() {
    newTest()
        .addSource(
            """
            /** @typedef {{a: function(D2)}} */
            var D1;

            /** @typedef {{b: D1}} */
            var D2;

            /** @type {D1} */ var x;
            fn(x);


            /**
             * @param {!D1} s
             * @template T
             */
            var fn = function(s) {};
            """)
        .run();
  }

  @Test
  @Ignore
  public void testFunctionLiteralUndefinedThisArgument() {
    // TODO(johnlenz): this was a weird error.  We should add a general
    // restriction on what is accepted for T. Something like:
    // "@template T of {Object|string}" or some such.
    newTest()
        .addSource(
            """
            /**
             * @param {function(this:T, ...)?} fn
             * @param {?T} opt_obj
             * @template T
             */
            function baz(fn, opt_obj) {}
            baz(function() { this; });
            """)
        .addDiagnostic("Function literal argument refers to undefined this argument")
        .run();
  }

  @Test
  public void testFunctionLiteralDefinedThisArgument() {
    newTest()
        .addSource(
            """
            /**
             * @param {function(this:T, ...)?} fn
             * @param {?T} opt_obj
             * @template T
             */
            function baz(fn, opt_obj) {}
            baz(function() { this; }, {});
            """)
        .run();
  }

  @Test
  public void testFunctionLiteralDefinedThisArgument2() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            """
            /** @param {string} x */ function f(x) {}
            /**
             * @param {?function(this:T, ...)} fn
             * @param {T=} opt_obj
             * @template T
             */
            function baz(fn, opt_obj) {}
            function g() { baz(function() { f(this.length); }, []); }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testFunctionLiteralUnreadNullThisArgument() {
    newTest()
        .addSource(
            """
            /**
             * @param {function(this:T, ...)?} fn
             * @param {?T} opt_obj
             * @template T
             */
            function baz(fn, opt_obj) {}
            baz(function() {}, null);
            """)
        .run();
  }

  @Test
  public void testUnionTemplateThisType() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {}
            /** @return {F|Array} */ function g() { return []; }
            /** @param {F} x */ function h(x) { }
            /**
            * @param {T} x
            * @param {function(this:T, ...)} y
            * @template T
            */
            function f(x, y) {}
            f(g(), function() { h(this); });
            """)
        .addDiagnostic(
            """
            actual parameter 1 of h does not match formal parameter
            found   : (Array|F|null)
            required: (F|null)
            """)
        .run();
  }

  @Test
  public void testRecordType1() {
    newTest()
        .addSource(
            """
            /** @param {{prop: number}} x */
            function f(x) {}
            f({});
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : {prop: (number|undefined)}
            required: {prop: number}
            missing : []
            mismatch: [prop]
            """)
        .run();
  }

  @Test
  public void testRecordType2() {
    newTest()
        .addSource(
            """
            /** @param {{prop: (number|undefined)}} x */
            function f(x) {}
            f({});
            """)
        .run();
  }

  @Test
  public void testRecordType3() {
    newTest()
        .addSource(
            """
            /** @param {{prop: number}} x */
            function f(x) {}
            f({prop: 'x'});
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : {prop: (number|string)}
            required: {prop: number}
            missing : []
            mismatch: [prop]
            """)
        .run();
  }

  @Test
  public void testRecordType4() {
    // Notice that we do not do flow-based inference on the object type:
    // We don't try to prove that x.prop may not be string until x
    // gets passed to g.
    testClosureTypesMultipleWarnings(
        """
        /** @param {{prop: (number|undefined)}} x */
        function f(x) {}
        /** @param {{prop: (string|undefined)}} x */
        function g(x) {}
        var x = {};
        f(x);
        g(x);
        """,
        ImmutableList.of(
            """
            actual parameter 1 of f does not match formal parameter
            found   : {prop: (number|string|undefined)}
            required: {prop: (number|undefined)}
            missing : []
            mismatch: [prop]\
            """,
            """
            actual parameter 1 of g does not match formal parameter
            found   : {prop: (number|string|undefined)}
            required: {prop: (string|undefined)}
            missing : []
            mismatch: [prop]\
            """));
  }

  @Test
  public void testRecordType5() {
    newTest()
        .addSource(
            """
            /** @param {{prop: (number|undefined)}} x */
            function f(x) {}
            /** @param {{otherProp: (string|undefined)}} x */
            function g(x) {}
            var x = {}; f(x); g(x);
            """)
        .run();
  }

  @Test
  public void testRecordType6() {
    newTest()
        .addSource(
            """
            /** @return {{prop: (number|undefined)}} x */
            function f() { return {}; }
            """)
        .run();
  }

  @Test
  public void testRecordType7() {
    newTest()
        .addSource(
            """
            /** @return {{prop: (number|undefined)}} x */
            function f() { var x = {}; g(x); return x; }
            /** @param {number} x */
            function g(x) {}
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : {prop: (number|undefined)}
            required: number
            """)
        .run();
  }

  @Test
  public void testRecordType8() {
    newTest()
        .addSource(
            """
            /** @return {{prop: (number|string)}} x */
            function f() { var x = {prop: 3}; g(x.prop); return x; }
            /** @param {string} x */
            function g(x) {}
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testDuplicateRecordFields1() {
    newTest()
        .addSource(
            """
            /**
             * @param {{x:string, x:number}} a
             */
            function f(a) {};
            """)
        .addDiagnostic("Bad type annotation. Duplicate record field x." + BAD_TYPE_WIKI_LINK)
        .run();
  }

  @Test
  public void testDuplicateRecordFields2() {
    newTest()
        .addSource(
            """
            /**
             * @param {{name:string,number:x,number:y}} a
             */
            function f(a) {};
            """)
        .addDiagnostic("Bad type annotation. Unknown type x")
        .addDiagnostic("Bad type annotation. Duplicate record field number." + BAD_TYPE_WIKI_LINK)
        .run();
  }

  @Test
  public void testMultipleExtendsInterface1() {
    newTest()
        .addSource(
            """
            /** @interface */ function base1() {}
            /** @interface */ function base2() {}
            /** @interface
            * @extends {base1}
            * @extends {base2}
            */
            function derived() {}
            """)
        .run();
  }

  @Test
  public void testMultipleExtendsInterface2() {
    newTest()
        .addSource(
            """
            /** @interface */function Int0() {};
            /** @interface */function Int1() {};
            Int0.prototype.foo = function() {};
            /** @interface
             * @extends {Int0}
             * @extends {Int1} */
            function Int2() {};
            /** @constructor
             * @implements {Int2} */function Foo() {};
            """)
        .addDiagnostic("property foo on interface Int0 is not implemented by type Foo")
        .run();
  }

  @Test
  public void testMultipleExtendsInterface3() {
    newTest()
        .addSource(
            """
            /** @interface */function Int0() {};
            /** @interface */function Int1() {};
            Int1.prototype.foo = function() {};
            /** @interface
             * @extends {Int0}
             * @extends {Int1} */
            function Int2() {};
            /** @constructor
             * @implements {Int2} */function Foo() {};
            """)
        .addDiagnostic("property foo on interface Int1 is not implemented by type Foo")
        .run();
  }

  @Test
  public void testMultipleExtendsInterface4() {
    newTest()
        .addSource(
            """
            /** @interface */function Int0() {};
            /** @interface */function Int1() {};
            /** @interface
             * @extends {Int0}
             * @extends {Int1}
             * @extends {number} */
            function Int2() {};
            /** @constructor
             * @implements {Int2} */function Foo() {};
            """)
        .addDiagnostic("Int2 @extends non-object type number")
        .run();
  }

  @Test
  public void testMultipleExtendsInterface5() {
    newTest()
        .addSource(
            """
            /** @interface */function Int0() {};
            /** @constructor */function Int1() {};
            /** @return {string} x */
            /** @interface
             * @extends {Int0}
             * @extends {Int1} */
            function Int2() {};
            """)
        .addDiagnostic("Int2 cannot extend this type; interfaces can only extend interfaces")
        .run();
  }

  @Test
  public void testMultipleExtendsInterface6() {
    newTest()
        .addSource(
            """
            /** @interface */function Super1() {};
            /** @interface */function Super2() {};
            /** @param {number} bar */Super2.prototype.foo = function(bar) {};
            /** @interface @extends {Super1} @extends {Super2} */function Sub() {};
            /** @override @param {string} bar */Sub.prototype.foo =
            function(bar) {};
            """)
        .addDiagnostic(
"""
mismatch of the foo property on type Sub and the type of the property it overrides from interface Super2
original: function(this:Super2, number): undefined
override: function(this:Sub, string): undefined
""")
        .run();
  }

  @Test
  public void testMultipleExtendsInterfaceAssignment() {
    newTest()
        .addSource(
            """
            /** @interface */var I1 = function() {};
            /** @interface */ var I2 = function() {}
            /** @interface
            @extends {I1}
            @extends {I2}*/
            var I3 = function() {};
            /** @constructor
            @implements {I3}*/var T = function() {};
            var t = new T();
            /** @type {I1} */var i1 = t;
            /** @type {I2} */var i2 = t;
            /** @type {I3} */var i3 = t;
            i1 = i3;
            i2 = i3;
            """)
        .run();
  }

  @Test
  public void testMultipleExtendsInterfaceParamPass() {
    newTest()
        .addSource(
            """
            /** @interface */
            var I1 = function() {};
            /** @interface */
            var I2 = function() {}
            /** @interface @extends {I1} @extends {I2} */
            var I3 = function() {};
            /** @constructor @implements {I3} */
            var T = function() {};
            var t = new T();
            /**
             * @param {I1} x
             * @param {I2} y
             * @param {I3} z
             */
            function foo(x,y,z){};
            foo(t,t,t)
            """)
        .run();
  }

  @Test
  public void testBadMultipleExtendsClass() {
    newTest()
        .addSource(
            """
            /** @constructor */ function base1() {}
            /** @constructor */ function base2() {}
            /** @constructor
            * @extends {base1}
            * @extends {base2}
            */
            function derived() {}
            """)
        .addDiagnostic(
            "Bad type annotation. type annotation incompatible with other annotations."
                + BAD_TYPE_WIKI_LINK)
        .run();
  }

  @Test
  public void testInterfaceExtendsResolution() {
    newTest()
        .addSource(
            """
            /** @interface\s
             @extends {A} */ function B() {};
            /** @constructor\s
             @implements {B} */ function C() {};
            /** @interface */ function A() {};
            """)
        .run();
  }

  @Test
  public void testPropertyCanBeDefinedInObject() {
    // This test is specifically checking loose property check behavior.
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @interface */ function I() {};
            I.prototype.bar = function() {};
            /** @type {Object} */ var foo;
            foo.bar();
            """)
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility1() {
    newTest()
        .addSource(
            """
            /** @interface */function Int0() {};
            /** @interface */function Int1() {};
            /** @type {number} */
            Int0.prototype.foo;
            /** @type {string} */
            Int1.prototype.foo;
            /** @interface
             * @extends {Int0}
             * @extends {Int1} */
            function Int2() {};
            """)
        .addDiagnostic(
            """
            Interface Int2 has a property foo with incompatible types in its super interfaces Int0 and Int1
            """)
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility2() {
    newTest()
        .addSource(
            """
            /** @interface */function Int0() {};
            /** @interface */function Int1() {};
            /** @interface */function Int2() {};
            /** @type {number} */
            Int0.prototype.foo;
            /** @type {string} */
            Int1.prototype.foo;
            /** @type {Object} */
            Int2.prototype.foo;
            /** @interface
             * @extends {Int0}
             * @extends {Int1}
             * @extends {Int2}*/
            function Int3() {};
            """)
        .addDiagnostic(
            """
            Interface Int3 has a property foo with incompatible types in its super interfaces Int0 and Int1
            """)
        .addDiagnostic(
            """
            Interface Int3 has a property foo with incompatible types in its super interfaces Int1 and Int2
            """)
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility3() {
    newTest()
        .addSource(
            """
            /** @interface */function Int0() {};
            /** @interface */function Int1() {};
            /** @type {number} */
            Int0.prototype.foo;
            /** @type {string} */
            Int1.prototype.foo;
            /** @interface
             * @extends {Int1} */ function Int2() {};
            /** @interface
             * @extends {Int0}
             * @extends {Int2} */
            function Int3() {};
            """)
        .addDiagnostic(
            """
            Interface Int3 has a property foo with incompatible types in its super interfaces Int0 and Int1
            """)
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility4() {
    newTest()
        .addSource(
            """
            /** @interface */function Int0() {};
            /** @interface
             * @extends {Int0} */ function Int1() {};
            /** @type {number} */
            Int0.prototype.foo;
            /** @interface */function Int2() {};
            /** @interface
             * @extends {Int2} */ function Int3() {};
            /** @type {string} */
            Int2.prototype.foo;
            /** @interface
             * @extends {Int1}
             * @extends {Int3} */
            function Int4() {};
            """)
        .addDiagnostic(
            """
            Interface Int4 has a property foo with incompatible types in its super interfaces Int0 and Int2
            """)
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility5() {
    newTest()
        .addSource(
            """
            /** @interface */function Int0() {};
            /** @interface */function Int1() {};
            /** @type {number} */
            Int0.prototype.foo;
            /** @type {string} */
            Int1.prototype.foo;
            /** @interface
             * @extends {Int1} */ function Int2() {};
            /** @interface
             * @extends {Int0}
             * @extends {Int2} */
            function Int3() {};
            /** @interface */function Int4() {};
            /** @type {number} */
            Int4.prototype.foo;
            /** @interface
             * @extends {Int3}
             * @extends {Int4} */
            function Int5() {};
            """)
        .addDiagnostic(
            """
            Interface Int3 has a property foo with incompatible types in its super interfaces Int0 and Int1
            """)
        .addDiagnostic(
            """
            Interface Int5 has a property foo with incompatible types in its super interfaces Int1 and Int4
            """)
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility6() {
    newTest()
        .addSource(
            """
            /** @interface */function Int0() {};
            /** @interface */function Int1() {};
            /** @type {number} */
            Int0.prototype.foo;
            /** @type {string} */
            Int1.prototype.foo;
            /** @interface
             * @extends {Int1} */ function Int2() {};
            /** @interface
             * @extends {Int0}
             * @extends {Int2} */
            function Int3() {};
            /** @interface */function Int4() {};
            /** @type {string} */
            Int4.prototype.foo;
            /** @interface
             * @extends {Int3}
             * @extends {Int4} */
            function Int5() {};
            """)
        .addDiagnostic(
            """
            Interface Int3 has a property foo with incompatible types in its super interfaces Int0 and Int1
            """)
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility7() {
    newTest()
        .addSource(
            """
            /** @interface */function Int0() {};
            /** @interface */function Int1() {};
            /** @type {number} */
            Int0.prototype.foo;
            /** @type {string} */
            Int1.prototype.foo;
            /** @interface
             * @extends {Int1} */ function Int2() {};
            /** @interface
             * @extends {Int0}
             * @extends {Int2} */
            function Int3() {};
            /** @interface */function Int4() {};
            /** @type {Object} */
            Int4.prototype.foo;
            /** @interface
             * @extends {Int3}
             * @extends {Int4} */
            function Int5() {};
            """)
        .addDiagnostic(
            """
            Interface Int3 has a property foo with incompatible types in its super interfaces Int0 and Int1
            """)
        .addDiagnostic(
            """
            Interface Int5 has a property foo with incompatible types in its super interfaces Int1 and Int4
            """)
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility8() {
    newTest()
        .addSource(
            """
            /** @interface */function Int0() {};
            /** @interface */function Int1() {};
            /** @type {number} */
            Int0.prototype.foo;
            /** @type {string} */
            Int1.prototype.bar;
            /** @interface
             * @extends {Int1} */ function Int2() {};
            /** @interface
             * @extends {Int0}
             * @extends {Int2} */
            function Int3() {};
            /** @interface */function Int4() {};
            /** @type {Object} */
            Int4.prototype.foo;
            /** @type {Null} */
            Int4.prototype.bar;
            /** @interface
             * @extends {Int3}
             * @extends {Int4} */
            function Int5() {};
            """)
        .addDiagnostic(
            """
            Interface Int5 has a property bar with incompatible types in its super interfaces Int1 and Int4
            """)
        .addDiagnostic(
            """
            Interface Int5 has a property foo with incompatible types in its super interfaces Int0 and Int4
            """)
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility9() {
    newTest()
        .addSource(
            """
            /** @interface
             * @template T */function Int0() {};
            /** @interface
             * @template T */function Int1() {};
            /** @type {T} */
            Int0.prototype.foo;
            /** @type {T} */
            Int1.prototype.foo;
            /** @interface
             * @extends {Int0<number>}
             * @extends {Int1<string>} */
            function Int2() {};
            """)
        .addDiagnostic(
            """
            Interface Int2 has a property foo with incompatible types in its super interfaces Int0<number> and Int1<string>
            """)
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibilityNoError() {
    newTest()
        .addSource(
            """
            /** @interface */function Int0() {};
            /** @interface */function Int1() {};
            /** @param {number} x */
            Int0.prototype.foo;
            /** @param {number} x */
            Int1.prototype.foo;
            /** @interface
             * @extends {Int0}
             * @extends {Int1} */
            function Int2() {};
            """)
        .run();
  }

  @Test
  public void testImplementedInterfacePropertiesShouldFailOnConflict() {
    // TODO(b/132718172): Provide a better error message.
    newTest()
        .addSource(
            """
            /** @interface */function Int0() {};
            /** @interface */function Int1() {};
            /** @type {number} */
            Int0.prototype.foo;
            /** @type {string} */
            Int1.prototype.foo;
            /** @constructor @implements {Int0} @implements {Int1} */
            function Foo() {};
            Foo.prototype.foo;
            """)
        .addDiagnostic(
"""
mismatch of the foo property on type Foo and the type of the property it overrides from interface Int1
original: string
override: number
""")
        .run();
  }

  @Test
  public void testGenerics1a() {
    String fnDecl =
        """
        /**\s
         * @param {T} x\s
         * @param {function(T):T} y\s
         * @template T
         */\s
        function f(x,y) { return y(x); }
        """;

    newTest()
        .addSource(
            fnDecl
                + """
                /** @type {string} */
                var out;
                /** @type {string} */
                var result = f('hi', function(x) {
                  out = x;
                  return x;
                });
                """)
        .run();
  }

  @Test
  public void testGenerics1b() {
    String fnDecl =
        """
        /**
         * @param {T} x
         * @param {function(T):T} y
         * @template T
         */
        function f(x,y) { return y(x); }
        """;

    newTest()
        .addSource(
            fnDecl
                + """
                /** @type {string} */
                var out;
                var result = f(0, function(x){ out = x; return x; });
                """)
        .addDiagnostic(
            """
            assignment
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testFilter0() {
    newTest()
        .addSource(
            """
            /**
             * @param {T} arr
             * @return {T}
             * @template T
             */
            var filter = function(arr){};
            /** @type {!Array<string>} */
            var arr;
            /** @type {!Array<string>} */
            var result = filter(arr);
            """)
        .run();
  }

  @Test
  public void testFilter1() {
    newTest()
        .addSource(
            """
            /**
             * @param {!Array<T>} arr
             * @return {!Array<T>}
             * @template T
             */
            var filter = function(arr){};
            /** @type {!Array<string>} */
            var arr;
            /** @type {!Array<string>} */
            var result = filter(arr);
            """)
        .run();
  }

  @Test
  public void testFilter2() {
    newTest()
        .addSource(
            """
            /**
             * @param {!Array<T>} arr
             * @return {!Array<T>}
             * @template T
             */
            var filter = function(arr){};
            /** @type {!Array<string>} */
            var arr;
            /** @type {!Array<number>} */
            var result = filter(arr);
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Array<string>
            required: Array<number>
            """)
        .run();
  }

  @Test
  public void testFilter3() {
    newTest()
        .addSource(
            """
            /**
             * @param {Array<T>} arr
             * @return {Array<T>}
             * @template T
             */
            var filter = function(arr){};
            /** @type {Array<string>} */
            var arr;
            /** @type {Array<number>} */
            var result = filter(arr);
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (Array<string>|null)
            required: (Array<number>|null)
            """)
        .run();
  }

  @Test
  public void testBackwardsInferenceGoogArrayFilter1() {
    testClosureTypes(
        """
        /** @type {Array<string>} */
        var arr;
        /** @type {!Array<number>} */
        var result = goog.array.filter(
           arr,
           function(item,index,src) {return false;});
        """,
        """
        initializing variable
        found   : Array<string>
        required: Array<number>
        """);
  }

  @Test
  public void testBackwardsInferenceGoogArrayFilter2() {
    testClosureTypes(
        """
        /** @type {number} */
        var out;
        /** @type {Array<string>} */
        var arr;
        var out4 = goog.array.filter(
           arr,
           function(item,index,src) {out = item; return false});
        """,
        """
        assignment
        found   : string
        required: number
        """);
  }

  @Test
  public void testBackwardsInferenceGoogArrayFilter3() {
    testClosureTypes(
        """
        /** @type {string} */
        var out;
        /** @type {Array<string>} */ var arr;
        var result = goog.array.filter(
           arr,
           function(item,index,src) {out = index;});
        """,
        """
        assignment
        found   : number
        required: string
        """);
  }

  @Test
  public void testBackwardsInferenceGoogArrayFilter4() {
    testClosureTypes(
        """
        /** @type {string} */
        var out;
        /** @type {Array<string>} */ var arr;
        var out4 = goog.array.filter(
           arr,
           function(item,index,srcArr) {out = srcArr;});
        """,
        """
        assignment
        found   : (Array|null|{length: number})
        required: string
        """);
  }

  @Test
  public void testCatchExpression1() {
    newTest()
        .addSource(
            """
            function fn() {
              /** @type {number} */
              var out = 0;
              try {
                foo();
              } catch (/** @type {string} */ e) {
                out = e;
              }
            }
            """)
        .addDiagnostic(
            """
            assignment
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testCatchExpression2() {
    newTest()
        .addSource(
            """
            function fn() {
              /** @type {number} */
              var out = 0;
              /** @type {string} */
              var e;
              try {
                foo();
              } catch (e) {
                out = e;
              }
            }
            """)
        .run();
  }

  @Test
  public void testIssue1058() {
    newTest()
        .addSource(
            """
            /**
              * @constructor
              * @template CLASS
              */
            var Class = function() {};

            /**
              * @param {function(CLASS):CLASS} a
              * @template T
              */
            Class.prototype.foo = function(a) {
              return 'string';
            };

            /** @param {number} a
              * @return {string} */
            var a = function(a) { return '' };

            new Class().foo(a);
            """)
        .run();
  }

  @Test
  public void testDeterminacyIssue() {
    newTest()
        .addSource(
            """
            (function() {
                /** @constructor */
                var ImageProxy = function() {};
                /** @constructor */
                var FeedReader = function() {};
                /** @type {ImageProxy} */
                FeedReader.x = new ImageProxy();
            })();
            """)
        .run();
  }

  @Test
  public void testUnknownTypeReport() {
    newTest()
        .addSource("function id(x) { return x; }")
        .enableReportUnknownTypes()
        .addDiagnostic("could not determine the type of this expression")
        .run();
  }

  @Test
  public void testUnknownTypeReport_allowsUnknownIfStatement() {
    newTest().addSource("function id(x) { x; }").enableReportUnknownTypes().run();
  }

  @Test
  public void testUnknownForIn() {
    newTest()
        .addSource(
            """
            var x = {'a':1}; var y;
            for(
            y
            in x) {}
            """)
        .enableReportUnknownTypes()
        .run();
  }

  @Test
  public void testUnknownTypeDisabledByDefault() {
    newTest().addSource("function id(x) { return x; }").run();
  }

  @Test
  public void testNonexistentPropertyAccessOnStruct() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            var A = function() {};
            /** @param {A} a */
            function foo(a) {
              if (a.bar) { a.bar(); }
            }
            """)
        .addDiagnostic("Property bar never defined on A")
        .run();
  }

  @Test
  public void testNonexistentPropertyAccessOnStructOrObject() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            var A = function() {};
            /** @param {A|Object} a */
            function foo(a) {
              if (a.bar) { a.bar(); }
            }
            """)
        .run();
  }

  @Test
  public void testNonexistentPropertyAccessOnExternStruct() {
    newTest()
        .addExterns(
            """
            /**
             * @constructor
             * @struct
             */
            var A = function() {};
            """)
        .addSource(
            """
            /** @param {A} a */
            function foo(a) {
              if (a.bar) { a.bar(); }
            }
            """)
        .addDiagnostic("Property bar never defined on A")
        .run();
  }

  @Test
  public void testNonexistentPropertyAccessStructSubtype() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            var A = function() {};
            /**
             * @constructor
             * @struct
             * @extends {A}
             */
            var B = function() { this.bar = function(){}; };
            /** @param {A} a */
            function foo(a) {
              if (a.bar) { a.bar(); }
            }
            """)
        .addDiagnostic("Property bar never defined on A")
        .run();
  }

  @Test
  public void testNonexistentPropertyAccessStructInterfaceSubtype() {
    newTest()
        .addSource(
            """
            /**
             * @interface
             * @struct
             */
            var A = function() {};

            /**
             * @interface
             * @struct
             * @extends {A}
             */
            var B = function() {};
            /** @return {void} */ B.prototype.bar = function(){};

            /** @param {A} a */
            function foo(a) {
              if (a.bar) { a.bar(); }
            }
            """)
        .addDiagnostic("Property bar never defined on A")
        .run();
  }

  @Test
  public void testNonexistentPropertyAccessStructRecordSubtype() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /**
             * @record
             * @struct
             */
            var A = function() {};

            /**
             * @record
             * @struct
             * @extends {A}
             */
            var B = function() {};
            /** @return {void} */ B.prototype.bar = function(){};

            /** @param {A} a */
            function foo(a) {
              if (a.bar) {
                a.bar();
              }
            }
            """)
        .addDiagnostic("Property bar never defined on A")
        .run();
  }

  @Test
  public void testNonexistentPropertyAccessStructSubtype2() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @struct
             */
            function Foo() {
              this.x = 123;
            }
            var objlit = /** @struct */ { y: 234 };
            Foo.prototype = objlit;
            var n = objlit.x;
            """)
        .addDiagnostic("Property x never defined on Foo.prototype")
        .run();
  }

  @Test
  public void testIssue1024a() {
    // This test is specifically checking loose property check behavior.
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            /** @param {Object} a */
            function f(a) {
              a.prototype = '__proto'
            }
            /** @param {Object} b
             *  @return {!Object}
             */
            function g(b) {
              return b.prototype
            }
            """)
        .run();
  }

  @Test
  public void testIssue1024b() {
    newTest()
        .addSource(
            """
            /** @param {Object} a */
            function f(a) {
              a.prototype = {foo:3};
            }
            /** @param {Object} b
             */
            function g(b) {
              b.prototype = function(){};
            }
            """)
        .run();
  }

  @Test
  public void testModuleReferenceNotAllowed() {
    newTest()
        .addSource("/** @param {./Foo} z */ function f(z) {}")
        .addDiagnostic("Bad type annotation. Unknown type ./Foo")
        .run();
  }

  @Test
  public void testCheckObjectKeysBadKey1() {
    newTest()
        .addSource("/** @type {!Object<!Object, number>} */ var k;")
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysBadKey2() {
    newTest()
        .addSource("/** @type {!Object<function(), number>} */ var k;")
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysBadKey3() {
    newTest()
        .addSource("/** @type {!Object<!Array<!Object>, number>} */ var k;")
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysBadKey4() {
    newTest()
        .addSource("/** @type {!Object<*, number>} */ var k;")
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysBadKey5() {
    newTest()
        .addSource("/** @type {(string|Object<Object, number>)} */ var k;")
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysBadKey6() {
    newTest()
        .addSource("/** @type {!Object<number, !Object<Object, number>>} */ var k;")
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysBadKey7() {
    newTest()
        .addSource(
            """
            /** @constructor */
            var MyClass = function() {};
            /** @type {!Object<MyClass, number>} */
            var k;
            """)
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysBadKey8() {
    newTest()
        .addSource(
            """
            /** @enum{!Object} */
            var Enum = {};
            /** @type {!Object<Enum, number>} */
            var k;
            """)
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysBadKey9() {
    newTest()
        .addSource("/** @type {function(!Object<!Object, number>)} */ var k;")
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysBadKey10() {
    newTest()
        .addSource("/** @type {function(): !Object<!Object, number>} */ var k;")
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysBadKey11() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function X() {}
            /** @constructor @extends {X} */
            function X2() {}
            /** @enum {!X} */
            var XE = {A:new X};
            /** @type {Object<(!XE|!X2), string>} */
            var Y = {};
            """)
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysVariousTags1() {
    newTest()
        .addSource("/** @type {!Object<!Object, number>} */ var k;")
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysVariousTags2() {
    newTest()
        .addSource("/** @param {!Object<!Object, number>} a */ var f = function(a) {};")
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysVariousTags3() {
    newTest()
        .addSource("/** @return {!Object<!Object, number>} */ var f = function() {return {}};")
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysVariousTags4() {
    newTest()
        .addSource("/** @typedef {!Object<!Object, number>} */ var MyType;")
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysGoodKey1() {
    newTest().addSource("/** @type {!Object<number, number>} */ var k;").run();
  }

  @Test
  public void testCheckObjectKeysGoodKey2() {
    newTest().addSource("/** @type {!Object<string, number>} */ var k;").run();
  }

  @Test
  public void testCheckObjectKeysGoodKey3() {
    newTest().addSource("/** @type {!Object<boolean, number>} */ var k;").run();
  }

  @Test
  public void testCheckObjectKeysGoodKey4() {
    newTest().addSource("/** @type {!Object<null, number>} */ var k;").run();
  }

  @Test
  public void testCheckObjectKeysGoodKey5() {
    newTest().addSource("/** @type {!Object<undefined, number>} */ var k;").run();
  }

  @Test
  public void testCheckObjectKeysGoodKey6() {
    newTest().addSource("/** @type {!Object<!Date, number>} */ var k;").run();
  }

  @Test
  public void testCheckObjectKeysGoodKey7() {
    newTest().addSource("/** @type {!Object<!RegExp, number>} */ var k;").run();
  }

  @Test
  public void testCheckObjectKeysGoodKey8() {
    newTest().addSource("/** @type {!Object<!Array, number>} */ var k;").run();
  }

  @Test
  public void testCheckObjectKeysGoodKey9() {
    newTest().addSource("/** @type {!Object<!Array<number>, number>} */ var k;").run();
  }

  @Test
  public void testCheckObjectKeysGoodKey10() {
    newTest().addSource("/** @type {!Object<?, number>} */ var k;").run();
  }

  @Test
  public void testCheckObjectKeysGoodKey11() {
    newTest().addSource("/** @type {!Object<(string|number), number>} */ var k").run();
  }

  @Test
  public void testCheckObjectKeysGoodKey12() {
    newTest().addSource("/** @type {!Object<Object>} */ var k;").run();
  }

  @Test
  public void testCheckObjectKeysGoodKey13() {
    newTest()
        .addSource(
            """
            /** @interface */
            var MyInterface = function() {};
            /** @type {!Object<!MyInterface, number>} */
            var k;
            """)
        .run();
  }

  @Test
  public void testCheckObjectKeysGoodKey14() {
    newTest()
        .addSource(
            """
            /** @typedef {{a: number}} */ var MyRecord;
            /** @type {!Object<MyRecord, number>} */ var k;
            """)
        .run();
  }

  @Test
  public void testCheckObjectKeysGoodKey15() {
    newTest()
        .addSource(
            """
            /** @enum{number} */
            var Enum = {};
            /** @type {!Object<Enum, number>} */
            var k;
            """)
        .run();
  }

  @Test
  public void testCheckObjectKeysClassWithToString() {
    newTest()
        .addExterns(new TestExternsBuilder().addObject().build())
        .addSource(
            """
            /** @constructor */
            var MyClass = function() {};
            /** @override*/
            MyClass.prototype.toString = function() { return ''; };
            /** @type {!Object<!MyClass, number>} */
            var k;
            """)
        .run();
  }

  @Test
  public void testCheckObjectKeysClassInheritsToString() {
    newTest()
        .addExterns(new TestExternsBuilder().addObject().build())
        .addSource(
            """
            /** @constructor */
            var Parent = function() {};
            /** @override */
            Parent.prototype.toString = function() { return ''; };
            /** @constructor @extends {Parent} */
            var Child = function() {};
            /** @type {!Object<!Child, number>} */
            var k;
            """)
        .run();
  }

  @Test
  public void testCheckObjectKeysForEnumUsingClassWithToString() {
    newTest()
        .addExterns(new TestExternsBuilder().addObject().build())
        .addSource(
            """
            /** @constructor */
            var MyClass = function() {};
            /** @override*/
            MyClass.prototype.toString = function() { return ''; };
            /** @enum{!MyClass} */
            var Enum = {};
            /** @type {!Object<Enum, number>} */
            var k;
            """)
        .run();
  }

  @Test
  public void testBadSuperclassInheritance1() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {}
            /** @type {number} */
            Foo.prototype.myprop = 2;

            /** @constructor @extends {Foo} */
            function Bar() {}
            /** @type {number} */
            Bar.prototype.myprop = 1;
            """)
        .addDiagnostic(TypeCheck.HIDDEN_SUPERCLASS_PROPERTY)
        .run();
  }

  @Test
  public void testBadSuperclassInheritance2() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {}
            /** @type {number} */
            Foo.prototype.myprop = 2;

            /** @constructor @extends {Foo} */
            function Bar() {}
            /** @override @type {string} */
            Bar.prototype.myprop = 'qwer';
            """)
        .addDiagnostic(TypeValidator.HIDDEN_SUPERCLASS_PROPERTY_MISMATCH)
        .run();
  }

  // If the property has no initializer, the HIDDEN_SUPERCLASS_PROPERTY_MISMATCH warning is missed.
  @Test
  public void testBadSuperclassInheritance3() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {}
            /** @type {number} */
            Foo.prototype.myprop = 2;

            /** @constructor @extends {Foo} */
            function Bar() {}
            /** @override @type {string} */
            Bar.prototype.myprop;
            """)
        .run();
  }

  @Test
  public void testCheckObjectKeysWithNamedType() {
    newTest()
        .addSource(
            """
            /** @type {!Object<!PseudoId, number>} */
            var k;
            /** @typedef {number|string} */
            var PseudoId;
            """)
        .run();
  }

  @Test
  public void testCheckObjectKeyRecursiveType() {
    newTest()
        .addSource(
            """
            /** @typedef {!Object<string, !Predicate>} */ var Schema;
            /** @typedef {function(*): boolean|!Schema} */ var Predicate;
            /** @type {!Schema} */ var k;
            """)
        .run();
  }

  @Test
  public void testDontOverrideNativeScalarTypes() {
    newTest()
        .addSource(
            """
            string = 123;
            var /** string */ s = 123;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: string
            """)
        .run();

    newTest()
        .addSource(
            """
            var string = goog.require('goog.string');
            var /** string */ s = 123;
            """)
        .addDiagnostic(
            "Property require never defined on goog" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testTemplateMap1() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            function f() {
              /** @type {Int8Array} */
              var x = new Int8Array(10);
              /** @type {IArrayLike<string>} */
              var y;
              y = x;
            }
            """)
        .addDiagnostic(
            """
            assignment
            found   : (Int8Array|null)
            required: (IArrayLike<string>|null)
            """)
        .run();
  }

  @Test
  public void testTemplateMap2() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            function f() {
              /** @type {Int8Array} */
              var x = new Int8Array(10);

              /** @type {IObject<number, string>} */
              var z;
              z = x;
            }
            """)
        .addDiagnostic(
            """
            assignment
            found   : (Int8Array|null)
            required: (IObject<number,string>|null)
            """)
        .run();
  }

  @Test
  public void testTemplateMap3() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            function f() {
              var x = new Int8Array(10);

              /** @type {IArrayLike<string>} */
              var y;
              y = x;
            }
            """)
        .addDiagnostic(
            """
            assignment
            found   : Int8Array
            required: (IArrayLike<string>|null)
            """)
        .run();
  }

  @Test
  public void testTemplateMap4() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            function f() {
              var x = new Int8Array(10);

              /** @type {IObject<number, string>} */
              var z;
              z = x;
            }
            """)
        .addDiagnostic(
            """
            assignment
            found   : Int8Array
            required: (IObject<number,string>|null)
            """)
        .run();
  }

  @Test
  public void testTemplateMap5() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            function f() {
              var x = new Int8Array(10);
              /** @type {IArrayLike<number>} */
              var y;
              y = x;
            }
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testTemplateMap6() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            function f() {
              var x = new Int8Array(10);
              /** @type {IObject<number, number>} */
              var z;
              z = x;
            }
            """)
        .includeDefaultExterns()
        .run();
  }

  private static final String EXTERNS_WITH_IARRAYLIKE_DECLS =
      """
      /**
       * @constructor @implements IArrayLike<number>
       */
      function Int8Array(length, opt_byteOffset, opt_length) {}
      /** @type {number} */
      Int8Array.prototype.length;
      /**
      * @constructor
      * @extends {Int8Array}
      */
      function Int8Array2(len) {};
      /**
       * @interface
       * @extends {IArrayLike<number>}
       */
      function IArrayLike2(){}

      /**
       * @constructor
       * @implements {IArrayLike2}
       */
      function Int8Array3(len) {};
      /** @type {number} */
      Int8Array3.prototype.length;
      /**
       * @interface
       * @extends {IArrayLike<VALUE3>}
       * @template VALUE3
       */
      function IArrayLike3(){}
      /**
       * @constructor
       * @implements {IArrayLike3<number>}
       */
      function Int8Array4(length) {};
      /** @type {number} */
      Int8Array4.prototype.length;
      /**
       * @interface
       * @extends {IArrayLike<VALUE2>}
       * @template VALUE2
       */
      function IArrayLike4(){}
      /**
       * @interface
       * @extends {IArrayLike4<boolean>}
       */
      function IArrayLike5(){}
      /**
       * @constructor
       * @implements {IArrayLike5}
       */
      function BooleanArray5(length) {};
      /** @type {number} */
      BooleanArray5.prototype.length;
      """;

  @Test
  public void testArrayImplementsIArrayLike() {
    newTest()
        .addSource(
            """
            /** @type {!Array<number>} */ var arr = [];
            var /** null */ n = arr[0];
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: null
            """)
        .run();
  }

  @Test
  public void testIArrayLike1() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            var arr = new Int8Array(7);
            // no warning
            arr[0] = 1;
            arr[1] = 2;
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLike2() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            var arr = new Int8Array(7);
            // have warnings
            arr[3] = false;
            """)
        .addDiagnostic(
            """
            assignment
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIArrayLike3() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            var arr = new Int8Array2(10);
            // have warnings
            arr[3] = false;
            """)
        .addDiagnostic(
            """
            assignment
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIArrayLike4() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            var arr = new Int8Array2(10);
            // have warnings
            arr[3] = false;
            """)
        .addDiagnostic(
            """
            assignment
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIArrayLike5() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            var arr = new Int8Array3(10);
            // have warnings
            arr[3] = false;
            """)
        .addDiagnostic(
            """
            assignment
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIArrayLike6() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            var arr = new Int8Array4(10);
            // have warnings
            arr[3] = false;
            """)
        .addDiagnostic(
            """
            assignment
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIArrayLike7() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            var arr5 = new BooleanArray5(10);
            arr5[2] = true;
            arr5[3] = "";
            """)
        .addDiagnostic(
            """
            assignment
            found   : string
            required: boolean
            """)
        .run();
  }

  @Test
  public void testIArrayLike8() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            var arr2 = new Int8Array(10);
            arr2[true] = 1;
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIArrayLike9() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            var arr2 = new Int8Array2(10);
            arr2[true] = 1;
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIArrayLike10() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            var arr2 = new Int8Array3(10);
            arr2[true] = 1;
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIArrayLike11() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            var arr2 = new Int8Array4(10);
            arr2[true] = 1;
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIArrayLike12() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            var arr2 = new BooleanArray5(10);
            arr2['prop'] = true;
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testIArrayLike13() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            """
            var numOrStr = null ? 0 : 'prop';
            var arr2 = new BooleanArray5(10);
            arr2[numOrStr] = true;
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : (number|string)
            required: number
            """)
        .run();
  }

  @Test
  public void testIArrayLikeStructuralMatch1() {
    newTest()
        .addSource(
            """
            function f(/** !IArrayLike */ x){};
            /** @constructor */
            function Foo() {}
            /** @type {number} */ Foo.prototype.length
            f(new Foo)
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLikeStructuralMatch2() {
    newTest()
        .addSource(
            """
            function f(/** !IArrayLike */ x){};
            /** @constructor */
            function Foo() {
              /** @type {number} */ this.length = 5;
            }
            f(new Foo)
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLikeStructuralMatch3() {
    newTest()
        .addSource(
            """
            function f(/** !IArrayLike */ x){};
            f({length: 5})
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLikeStructuralMatch4() {
    newTest()
        .addSource(
            """
            function f(/** !IArrayLike */ x){};
            /** @const */ var ns = {};
            /** @type {number} */ ns.length
            f(ns)
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLikeStructuralMatch5() {
    newTest()
        .addSource(
            """
            function f(/** !IArrayLike */ x){};
            var ns = function() {};
            /** @type {number} */ ns.length
            f(ns)
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLikeStructuralMatch6() {
    // Even though Foo's [] element type may not be string, we treat the lack
    // of explicit type like ? and allow this.
    newTest()
        .addSource(
            """
            function f(/** !IArrayLike<string> */ x){};
            /** @constructor */
            function Foo() {}
            /** @type {number} */ Foo.prototype.length
            f(new Foo)
            """)
        .includeDefaultExterns()
        .run();
  }

  private static final String EXTERNS_WITH_IOBJECT_DECLS =
      """
      /**
       * @constructor
       * @implements IObject<(string|number), number>
       */
      function Object2() {}
      /**
       * @constructor @struct
       * @implements IObject<number, number>
       */
      function Object3() {}
      """;

  @Test
  public void testIObject1() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            var arr2 = new Object2();
            arr2[0] = 1;
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIObject2() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            var arr2 = new Object2();
            arr2['str'] = 1;
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIObject3() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            var arr2 = new Object2();
            arr2[true] = 1;
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : boolean
            required: (number|string)
            """)
        .run();
  }

  @Test
  public void testIObject4() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            var arr2 = new Object2();
            arr2[function(){}] = 1;
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : function(): undefined
            required: (number|string)
            """)
        .run();
  }

  @Test
  public void testIObject5() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            var arr2 = new Object2();
            arr2[{}] = 1;
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : {}
            required: (number|string)
            """)
        .run();
  }

  @Test
  public void testIObject6() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            var arr2 = new Object2();
            arr2[undefined] = 1;
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : undefined
            required: (number|string)
            """)
        .run();
  }

  @Test
  public void testIObject7() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            var arr2 = new Object2();
            arr2[null] = 1;
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : null
            required: (number|string)
            """)
        .run();
  }

  @Test
  public void testIObject8() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            var arr = new Object2();
            /** @type {boolean} */
            var x = arr[3];
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: boolean
            """)
        .run();
  }

  @Test
  public void testIObject9() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            var arr = new Object2();
            /** @type {(number|string)} */
            var x = arr[3];
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIObject10() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            var arr = new Object3();
            /** @type {number} */
            var x = arr[3];
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIObject11() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            var arr = new Object3();
            /** @type {boolean} */
            var x = arr[3];
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: boolean
            """)
        .run();
  }

  @Test
  public void testIObject12() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            var arr = new Object3();
            /** @type {string} */
            var x = arr[3];
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testIObject13() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            var arr = new Object3();
            arr[3] = false;
            """)
        .addDiagnostic(
            """
            assignment
            found   : boolean
            required: number
            """)
        .run();
  }

  @Test
  public void testIObject14() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            var arr = new Object3();
            arr[3] = 'value';
            """)
        .addDiagnostic(
            """
            assignment
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testIObject15() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            function f(/** !Object<string, string> */ x) {}
            var /** !IObject<string, string> */ y;
            f(y);
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIObject16() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            """
            function f(/** !Object<string, string> */ x) {}
            var /** !IObject<string, number> */ y;
            f(y);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : IObject<string,number>
            required: Object<string,string>
            """)
        .run();
  }

  @Test
  public void testEmptySubtypeRecord1() {
    // Verify that empty @record subtypes don't cause circular definition warnings.
    newTest()
        .addSource(
            """
            /** @record */ function I1() {};
            /** @type {number|undefined} */ I1.prototype.prop;

            /** @record @extends {I1} */ function I2() {}
            /** @record @extends {I2} */ function I3() {}
            /** @type {string} */ I3.prototype.prop2;
            /** @constructor @implements {I3} */ function C() {
                /** @type {number} */ this.prop = 1;
                /** @type {string} */ this.prop2 = 'str';
            }

            /** @param {I3} a */ function fn(a) {}
            """)
        .run();
  }

  @Test
  public void testEmptySubtypeRecord2() {
    newTest()
        .addSource(
            """
            /** @type {!SubInterface} */ var value;
            /** @record */ var SuperInterface = function () {};
            /** @record @extends {SuperInterface} */ var SubInterface = function() {};
            """)
        .run();
  }

  @Test
  public void testEmptySubtypeRecord3() {
    newTest()
        .addSource(
            """
            /** @record */ var SuperInterface = function () {};
            /** @record @extends {SuperInterface} */ var SubInterface = function() {};
            /** @type {!SubInterface} */ var value;
            """)
        .run();
  }

  @Test
  public void testEmptySubtypeInterface1() {
    newTest()
        .addSource(
            """
            /** @type {!SubInterface} */ var value;
            /** @interface */ var SuperInterface = function () {};
            /** @interface @extends {SuperInterface} */ var SubInterface = function() {};
            """)
        .run();
  }

  @Test
  public void testRecordWithOptionalProperty() {
    newTest()
        .addSource(
            """
            /**  @constructor */ function Foo() {};
            Foo.prototype.str = 'foo';

            var /** {str: string, opt_num: (undefined|number)} */ x = new Foo;
            """)
        .run();
  }

  @Test
  public void testRecordWithUnknownProperty() {
    newTest()
        .addSource(
            """
            /**  @constructor */ function Foo() {};
            Foo.prototype.str = 'foo';

            var /** {str: string, unknown: ?} */ x = new Foo;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Foo
            required: {
              str: string,
              unknown: ?
            }
            missing : [unknown]
            mismatch: []
            """)
        .run();
  }

  @Test
  public void testRecordWithOptionalUnknownProperty() {
    newTest()
        .addSource(
            """
            /**  @constructor */ function Foo() {};
            Foo.prototype.str = 'foo';

            var /** {str: string, opt_unknown: (?|undefined)} */ x = new Foo;
            """)
        .run();
  }

  @Test
  public void testRecordWithTopProperty() {
    newTest()
        .addSource(
            """
            /**  @constructor */ function Foo() {};
            Foo.prototype.str = 'foo';

            var /** {str: string, top: *} */ x = new Foo;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Foo
            required: {
              str: string,
              top: *
            }
            missing : [top]
            mismatch: []
            """)
        .run();
  }

  @Test
  public void testOptionalUnknownIsAssignableToUnknown() {
    newTest()
        .addSource(
            """
            function f(/** (undefined|?) */ opt_unknown) {
              var /** ? */ unknown = opt_unknown;
            }
            """)
        .run();
  }

  @Test
  public void testOptimizePropertyMap1() {
    // For non object-literal types such as Function, the behavior doesn't change.
    // The stray property is added as unknown.
    newTest()
        .addSource(
            """
            /** @return {!Function} */
            function f() {
              var x = function() {};
              /** @type {number} */
              x.prop = 123;
              return x;
            }
            function g(/** !Function */ x) {
              var /** null */ n = x.prop;
            }
            """)
        .addDiagnostic("Property prop never defined on Function")
        .run();
  }

  @Test
  public void testOptimizePropertyMap1b() {
    // For non object-literal types such as Function, the behavior doesn't change.
    // The stray property is added as unknown.
    newTest()
        .addSource(
            """
            /** @return {!Function} */
            function f() {
              var x = function() {};
              /** @type {number} */
              x.prop = 123;
              return x;
            }
            function g(/** !Function */ x) {
              var /** null */ n = x.prop;
            }
            """)
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .run();
  }

  @Test
  public void testOptimizePropertyMap2() {
    // Don't add the inferred property to all Foo values.
    newTest()
        .addSource(
            """
            /** @typedef {{a:number}} */
            var Foo;
            function f(/** !Foo */ x) {
              var y = x;
              /** @type {number} */
              y.b = 123;
            }
            function g(/** !Foo */ x) {
              var /** null */ n = x.b;
            }
            """)
        .addDiagnostic("Property b never defined on x")
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .run();
  }

  @Test
  public void testOptimizePropertyMap2b() {
    // Here strict missing properties warns, do we want it to?
    newTest()
        .addSource(
            """
            /** @typedef {{a:number}} */
            var Foo;
            function f(/** !Foo */ x) {
              var y = x;
              /** @type {number} */
              y.b = 123;
            }
            """)
        .addDiagnostic("Property b never defined on y")
        .run();
  }

  @Test
  public void testOptimizePropertyMap3a() {
    // For @record types, add the stray property to the index as before.
    newTest()
        .addSource(
            """
            /** @record */
            function Foo() {}
            /** @type {number} */
            Foo.prototype.a;
            function f(/** !Foo */ x) {
              var y = x;
              /** @type {number} */
              y.b = 123;
            }
            function g(/** !Foo */ x) {
              var /** null */ n = x.b;
            }
            """)
        .addDiagnostic("Property b never defined on Foo") // definition
        .addDiagnostic("Property b never defined on Foo")
        .run(); // reference
  }

  @Test
  public void testOptimizePropertyMap3b() {
    // For @record types, add the stray property to the index as before.
    newTest()
        .addSource(
            """
            /** @record */
            function Foo() {}
            /** @type {number} */
            Foo.prototype.a;
            function f(/** !Foo */ x) {
              var y = x;
              /** @type {number} */
              y.b = 123;
            }
            function g(/** !Foo */ x) {
              var /** null */ n = x.b;
            }
            """)
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .run();
  }

  @Test
  public void testOptimizePropertyMap4() {
    newTest()
        .addSource(
            """
            function f(x) {
              var y = { a: 1, b: 2 };
            }
            function g(x) {
              return x.b + 1;
            }
            """)
        .run();
  }

  @Test
  public void testOptimizePropertyMap5() {
    // Tests that we don't declare the properties on Object (so they don't appear on
    // all object types).
    newTest()
        .addSource(
            """
            function f(x) {
              var y = { a: 1, b: 2 };
            }
            function g() {
              var x = { c: 123 };
              return x.a + 1;
            }
            """)
        .addDiagnostic("Property a never defined on x")
        .run();
  }

  @Test
  public void testOptimizePropertyMap6() {
    // The stray property doesn't appear on other inline record types.
    newTest()
        .addSource(
            """
            function f(/** {a:number} */ x) {
              var y = x;
              /** @type {number} */
              y.b = 123;
            }
            function g(/** {c:number} */ x) {
              var /** null */ n = x.b;
            }
            """)
        .addDiagnostic("Property b never defined on x")
        // Checking loose property behavior
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .run();
  }

  @Test
  public void testOptimizePropertyMap7() {
    newTest()
        .addSource(
            """
            function f() {
              var x = {a:1};
              x.b = 2;
            }
            function g() {
              var y = {a:1};
              return y.b + 1;
            }
            """)
        .addDiagnostic("Property b never defined on y")
        .run();
  }

  @Test
  public void testOptimizePropertyMap8() {
    newTest()
        .addSource(
            """
            function f(/** {a:number, b:number} */ x) {}
            function g(/** {c:number} */ x) {
              var /** null */ n = x.b;
            }
            """)
        .addDiagnostic("Property b never defined on x")
        .run();
  }

  @Test
  public void testOptimizePropertyMap9() {
    // Don't add the stray property to all types that meet with {a: number, c: string}.
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {
              this.a = 123;
            }
            function f(/** {a: number, c: string} */ x) {
              x.b = 234;
            }
            function g(/** !Foo */ x) {
              return x.b + 5;
            }
            """)
        .addDiagnostic("Property b never defined on Foo")
        // Checking loose property behavior
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition1() {
    newTest()
        .addSource(
            """
            /** @record */
            function A() {}
            /** @type {number} */
            A.prototype.prop;
            /** @record */
            function B() {}
            /** @type {number} */
            B.prototype.prop;
            /** @constructor */
            function C() {}
            /** @type {number} */
            C.prototype.prop;
            /** @return {(A|B|C)} */
            function fun () {}
            /** @return {(B|A|C)} */
            function fun () {}
            """)
        .addDiagnostic("variable fun redefined, original definition at testcode0:14")
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition3() {
    newTest()
        .addSource(
            """
            var ns = {};
            /** @type {{x:number}} */ ns.x;
            /** @type {{x:number}} */ ns.x;
            """)
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition3_1() {
    newTest()
        .addSource(
            """
            var ns = {};
            /** @type {{x:number}} */ ns.x;
            /** @type {{x:string}} */ ns.x;
            """)
        .addDiagnostic(
            """
            variable ns.x redefined with type {x: string}, original definition at testcode0:2 with type {x: number}
            """)
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition3_2() {
    newTest()
        .addSource(
            """
            var ns = {};
            /** @type {{x:number}} */ ns.x;
            /** @type {{x:number, y:boolean}} */ ns.x;
            """)
        .addDiagnostic(
            """
            variable ns.x redefined with type {
              x: number,
              y: boolean
            }, original definition at testcode0:2 with type {x: number}
            """)
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition4() {
    newTest()
        .addSource(
            """
            var ns = {};
            /** @record */ function rec3(){}
            /** @record */ function rec4(){}
            /** @type {!rec3} */ ns.x;
            /** @type {!rec4} */ ns.x;
            """)
        .addDiagnostic(
            """
            variable ns.x redefined with type rec4, original definition at testcode0:4 with type rec3
            """)
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition5() {
    newTest()
        .addSource(
            """
            var ns = {};
            /** @record */ function rec3(){}
            /** @record */ function rec4(){}
            /** @type {number} */ rec4.prototype.prop;
            /** @type {!rec3} */ ns.x;
            /** @type {!rec4} */ ns.x;
            """)
        .addDiagnostic(
            """
            variable ns.x redefined with type rec4, original definition at testcode0:5 with type rec3
            """)
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition6() {
    newTest()
        .addSource(
            """
            var ns = {};
            /** @record */ function rec3(){}
            /** @type {number} */ rec3.prototype.prop;
            /** @record */ function rec4(){}
            /** @type {!rec3} */ ns.x;
            /** @type {!rec4} */ ns.x;
            """)
        .addDiagnostic(
            """
            variable ns.x redefined with type rec4, original definition at testcode0:5 with type rec3
            """)
        .run();
  }

  /** check bug fix 22713201 (the first case) */
  @Test
  public void testDuplicateVariableDefinition7() {
    newTest()
        .addSource(
            """
            /** @typedef {{prop:TD2}} */
              var TD1;

              /** @typedef {{prop:TD1}} */
              var TD2;

              var /** TD1 */ td1;
              var /** TD2 */ td2;

              td1 = td2;
            """)
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition8() {
    newTest()
        .addSource(
            """
            var ns = {}
            /** @record */ function rec(){}
            /** @type {number} */ rec.prototype.prop;

            /** @type {!rec} */ ns.x;
            /** @type {{prop:number}} */ ns.x;

            /** @type {{prop:number}} */ ns.y;
            /** @type {!rec} */ ns.y;
            """)
        .addDiagnostic(
            """
            variable ns.x redefined with type {prop: number}, original definition at testcode0:5 with type rec
            """)
        .addDiagnostic(
            """
            variable ns.y redefined with type rec, original definition at testcode0:8 with type {prop: number}
            """)
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition8_2() {
    newTest()
        .addSource(
            """
            var ns = {}
            /** @record */ function rec(){}
            /** @type {number} */ rec.prototype.prop;

            /** @type {!rec} */ ns.x;
            /** @type {{prop:string}} */ ns.x;

            /** @type {{prop:number}} */ ns.y;
            /** @type {!rec} */ ns.y;
            """)
        .addDiagnostic(
            """
            variable ns.x redefined with type {prop: string}, original definition at testcode0:5 with type rec
            """)
        .addDiagnostic(
            """
            variable ns.y redefined with type rec, original definition at testcode0:8 with type {prop: number}
            """)
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition8_3() {
    newTest()
        .addSource(
            """
            var ns = {}
            /** @record */ function rec(){}
            /** @type {string} */ rec.prototype.prop;

            /** @type {!rec} */ ns.x;
            /** @type {{prop:string}} */ ns.x;

            /** @type {{prop:number}} */ ns.y;
            /** @type {!rec} */ ns.y;
            """)
        .addDiagnostic(
            """
            variable ns.x redefined with type {prop: string}, original definition at testcode0:5 with type rec
            """)
        .addDiagnostic(
            """
            variable ns.y redefined with type rec, original definition at testcode0:8 with type {prop: number}
            """)
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition8_4() {
    newTest()
        .addSource(
            """
            /** @record @template T */ function I() {}
            /** @type {T} */ I.prototype.prop;
            var ns = {}
            /** @record */ function rec(){}
            /** @type {I} */ rec.prototype.prop;

            /** @type {!rec} */ ns.x;
            /** @type {{prop:I}} */ ns.x;
            """)
        .addDiagnostic(
            """
            variable ns.x redefined with type {prop: (I|null)}, original definition at testcode0:7 with type rec
            """)
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition8_5() {
    newTest()
        .addSource(
            """
            /** @record @template T */ function I() {}
            /** @type {T} */ I.prototype.prop;
            var ns = {}
            /** @record */ function rec(){}
            /** @type {I<number>} */ rec.prototype.prop;

            /** @type {!rec} */ ns.x;
            /** @type {{prop:I<number>}} */ ns.x;
            """)
        .addDiagnostic(
            """
            variable ns.x redefined with type {prop: (I<number>|null)}, original definition at testcode0:7 with type rec
            """)
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition8_6() {
    newTest()
        .addSource(
            """
            /** @record @template T */ function I() {}
            /** @type {T} */ I.prototype.prop;
            var ns = {}
            /** @record */ function rec(){}
            /** @type {I<number>} */ rec.prototype.prop;

            /** @type {!rec} */ ns.x;
            /** @type {{prop:I<string>}} */ ns.x;
            """)
        .addDiagnostic(
            """
            variable ns.x redefined with type {prop: (I<string>|null)}, original definition at testcode0:7 with type rec
            """)
        .run();
  }

  // should have no warning, need to handle equivalence checking for
  // structural types with template types
  @Test
  public void testDuplicateVariableDefinition8_7() {
    newTest()
        .addSource(
            """
            /** @record @template T */
            function rec(){}
            /** @type {T} */ rec.prototype.value;

            /** @type {rec<string>} */ ns.x;
            /** @type {{value: string}} */ ns.x;
            """)
        .addDiagnostic(
            """
            variable ns.x redefined with type {value: string}, original definition at testcode0:5 with type (null|rec<string>)
            """)
        .run();
  }

  @Test
  public void testModuloNullUndefThatWorkedWithoutSpecialSubtypingRules1() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {}
            function f(/** function(?Foo, !Foo) */ x) {
              return /** @type {function(!Foo, ?Foo)} */ (x);
            }
            """)
        .run();
  }

  @Test
  public void testModuloNullUndefThatWorkedWithoutSpecialSubtypingRules2() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {}
            function f(/** !Array<!Foo> */ to, /** !Array<?Foo> */ from) {
              to = from;
            }
            """)
        .run();
  }

  @Test
  public void testModuloNullUndefThatWorkedWithoutSpecialSubtypingRules3() {
    newTest()
        .addSource(
            """
            function f(/** ?Object */ x) {
              return {} instanceof x;
            }
            """)
        .run();
  }

  @Test
  public void testModuloNullUndefThatWorkedWithoutSpecialSubtypingRules4() {
    newTest()
        .addSource(
            """
            function f(/** ?Function */ x) {
              return x();
            }
            """)
        .run();
  }

  @Test
  public void testEs5ClassExtendingEs6Class() {
    newTest()
        .addSource(
            """
            class Foo {}
            /** @constructor @extends {Foo} */ var Bar = function() {};
            """)
        .addDiagnostic("ES5 class Bar cannot extend ES6 class Foo")
        .run();
  }

  @Test
  public void testEs5ClassExtendingEs6Class_noWarning() {
    newTest()
        .addSource(
            """
            class A {}
            /** @constructor @extends {A} */
            const B = createSubclass(A);
            """)
        .run();
  }

  @Test
  public void testNonNullTemplatedThis() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function C() {}

            /**
              @return {THIS}
              @this {THIS}
              @template THIS
            */
            C.prototype.method = function() {};

            /** @return {C|null} */
            function f() {
              return x;
            };

            /** @type {string} */ var s = f().method();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : C
            required: string
            """)
        .run();
  }

  // Github issue #2222: https://github.com/google/closure-compiler/issues/2222
  @Test
  public void testSetPrototypeToNewInstance() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function C() {}
            C.prototype = new C;
            """)
        .run();
  }

  @Test
  public void testNoResolvedTypeAndGreatestSubtypeInference() {
    testClosureTypes(
        """
        goog.forwardDeclare('Foo');
        /**
         * @param {boolean} pred
         * @param {?Foo} x
         */
        function f(pred, x) {
          var y;
          if (pred) {
            y = null;
          } else {
            y = x;
          }
          var /** number */ z = y;
        }
        """,
        """
        initializing variable
        found   : (Foo|null)
        required: number
        """);
  }

  @Test
  public void testNoResolvedTypeDoesntCauseInfiniteLoop() {
    testClosureTypes(
        """
        goog.forwardDeclare('Foo');
        goog.forwardDeclare('Bar');

        /** @interface */
        var Baz = function() {};
        /** @return {!Bar} */
        Baz.prototype.getBar = function() {};
        /** @constructor */
        var Qux = function() {
          /** @type {?Foo} */
          this.jobRuntimeTracker_ = null;
        };
        /** @param {!Baz} job */
        Qux.prototype.runRenderJobs_ = function(job) {
          for (var i = 0; i < 10; i++) {
            if (this.jobRuntimeTracker_) {
              goog.asserts.assert(job.getBar, '');
            }
          }
        };
        """,
        null);
  }

  @Test
  public void testb38182645() {
    newTest()
        .addSource(
            """
            /**
             * @interface
             * @template VALUE
             */
            function MyI() {}


            /**
             * @constructor
             * @implements {MyI<K|V>}
             * @template K, V
             */
            function MyMap() {}


            /**
             * @param {!MyMap<string, T>} map
             * @return {T}
             * @template T
             */
            function getValueFromNameAndMap(map) {
              return /** @type {?} */ (123);
            }
            var m = /** @type {!MyMap<string,number>} */ (new MyMap());
            var /** null */ n = getValueFromNameAndMap(m);
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: null
            """)
        .run();
  }

  @Test
  public void testLocalType1() {
    newTest()
        .addSource(
            """
            /** @constructor */ function C(){ /** @const */ this.a = true;}
            function f() {
              // shadow
              /** @constructor */ function C(){ /** @const */ this.a = 1;}
            }
            """)
        .run();
  }

  @Test
  public void testLocalType2() {
    newTest()
        .addSource(
            """
            /** @constructor */ function C(){ /** @const */ this.a = true;}
            function f() {
              // shadow
              /** @constructor */ function C(){ /** @const */ this.a = 1;}
              /** @type {null} */ var x = new C().a;
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: null
            """)
        .run();
  }

  @Test
  public void testForwardDecl1() {
    newTest()
        .addSource(
            """
            /** @const */ var ns = {};
            /** @constructor */ ns.Outer = function C() {};
            /** @return {!ns.Outer.Inner} */
            ns.Outer.prototype.method = function() {
              return new ns.Outer.Inner();
            };
            /** @constructor */ ns.Outer.Inner = function () {};
            """)
        .run();
  }

  @Test
  public void testForwardDecl2() {
    newTest()
        .addSource(
            """
            /** @const */ var ns1 = {};
            /** @const */ ns1.ns2 = {};
            /** @constructor */ ns1.ns2.Outer = function C() {};
            /** @return {!ns1.ns2.Outer.Inner} */
            ns1.ns2.Outer.prototype.method = function() {
              return new ns1.ns2.Outer.Inner();
            };
            /** @constructor */ ns1.ns2.Outer.Inner = function () {};
            """)
        .run();
  }

  @Test
  public void testMissingPropertiesWarningOnObject1() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {
              this.prop = 123;
            }
            /** @param {!Object} x */
            function f(x) {
              return x.prop;
            }
            """)
        .addDiagnostic("Property prop never defined on Object")
        .run();
  }

  @Test
  public void testStrictNumericOperators1() {
    newTest()
        .addSource("var x = 'asdf' - 1;")
        .addDiagnostic(
            """
            left operand
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testStrictNumericOperators2() {
    newTest()
        .addSource("var x = 1 - 'asdf';")
        .addDiagnostic(
            """
            right operand
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testStrictNumericOperators3() {
    newTest()
        .addSource("var x = 'asdf'; x++;")
        .addDiagnostic(
            """
            increment/decrement
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testStrictNumericOperators4a() {
    newTest()
        .addSource("var x = -'asdf';")
        .addDiagnostic(
            """
            sign operator
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testStrictNumericOperators4b() {
    newTest().addSource("var x = +'asdf';").run();
  }

  @Test
  public void testStrictNumericOperators5() {
    newTest()
        .addSource("var x = 1 < 'asdf';")
        .addDiagnostic(
            """
            right side of numeric comparison
            found   : string
            required: (bigint|number)
            """)
        .run();
  }

  @Test
  public void testStrictNumericOperators6() {
    newTest()
        .addSource("var x = 'asdf'; x *= 2;")
        .addDiagnostic(
            """
            left operand
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testStrictNumericOperators7() {
    newTest()
        .addSource("var x = ~ 'asdf';")
        .addDiagnostic(
            """
            bitwise NOT
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testStrictNumericOperators8() {
    newTest()
        .addSource("var x = 'asdf' | 1;")
        .addDiagnostic(
            """
            bad left operand to bitwise operator
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testStrictNumericOperators9() {
    newTest()
        .addSource("var x = 'asdf' << 1;")
        .addDiagnostic(
            """
            operator <<
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testStrictNumericOperators10() {
    newTest()
        .addSource("var x = 1 >>> 'asdf';")
        .addDiagnostic(
            """
            operator >>>
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testClassFieldDictError() {
    newTest()
        .addSource("/** @dict */ class C { x=2; }")
        .addDiagnostic("Illegal key, the class is a dict")
        .run();
  }

  @Test
  public void testClassFieldStaticDictError() {
    newTest()
        .addSource("/** @dict */ class C { static x=2; }")
        .addDiagnostic("Illegal key, the class is a dict")
        .run();
  }

  @Test
  public void testClassFieldUnrestricted() {
    newTest().addSource("/** @unrestricted */ class C { x = 2; }").run();
  }

  @Test
  public void testClassFieldStaticUnrestricted() {
    newTest().addSource("/** @unrestricted */ class C { static x = 2; }").run();
  }

  @Test
  public void testClassFieldTypeError1() {
    newTest()
        .addSource(
            """
            class C {
              /** @type {string} */
              x = 2;
            }
            """)
        .addDiagnostic(
            """
            assignment to property x of C
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testClassFieldStaticTypeError1() {
    newTest()
        .addSource(
            """
            class C {
              /** @type {string} */
              static x = 2;
            }
            """)
        .addDiagnostic(
            """
            assignment to property x of C
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testClassFieldTypeError2() {
    newTest()
        .addSource(
            """
            class C {
              /** @type {string} */
              x = '';
              constructor() {
                /** @type {number} */
                this.x = 1;
              }
            }
            """)
        .addDiagnostic(
            """
            assignment to property x of C
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testClassFieldTypeError3() {
    newTest()
        .addSource(
            """
            const obj = {};
            obj.C = class {
              /** @type {string} */
              x = 2;
            }
            """)
        .addDiagnostic(
            """
            assignment to property x of obj.C
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testClassFieldStaticTypeError3() {
    newTest()
        .addSource(
            """
            const obj = {};
            obj.C = class {
              /** @type {string} */
              static x = 2;
            }
            """)
        .addDiagnostic(
            """
            assignment to property x of obj.C
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testClassInitializerTypeInitializer() {
    newTest()
        .addSource(
            """
            /** @param {number|undefined} y */
            function f(y) {
            class C { /** @type {string} */ x = y ?? 0; }
            }
            """)
        .addDiagnostic(
            """
            assignment to property x of C
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testClassInitializerTypeInference() {
    newTest()
        .addSource(
            """
            /**
             * @param {string} s
             * @return {string}
             */
            const stringIdentity = (s) => s;
            class C { x = stringIdentity(0); }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of stringIdentity does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testClassComputedField() {
    newTest().addSource("/** @dict */ class C { [x]=2; }").run();
    newTest().addSource("/** @dict */ class C { 'x' = 2; }").run();
    newTest().addSource("/** @dict */ class C { 1 = 2; }").run();
    newTest()
        .addSource(
            """
            /** @param {number} x */ function takesNum(x) {}
            /** @dict */
            class C {
              /** @type {string} @suppress {checkTypes} */
              [takesNum('string')] = 2;
            }
            """)
        .run();
    newTest().addSource("/** @unrestricted */ class C { [x] = 2; }").run();
    newTest().addSource("/** @unrestricted */ class C { 'x' = 2; }").run();
    newTest().addSource("/** @unrestricted */ class C { 1 = 2; }").run();
    newTest()
        .addSource(
            """
            /** @unrestricted */
            class C {
              /** @type {string} */
              [x] = 2;
            }
            """)
        .run();
  }

  @Test
  public void testClassComputedFieldStatic() {
    newTest().addSource("/** @dict */ class C { static [x]=2; }").run();
    newTest().addSource("/** @dict */ class C { static 'x' = 2; }").run();
    newTest().addSource("/** @dict */ class C { static 1 = 2; }").run();
    newTest()
        .addSource(
            """
            /** @dict */
            class C {
              /** @type {string}*/
              static [x] = 2;
            }
            """)
        .run();

    newTest().addSource("/** @unrestricted */ class C { static [x]=2; }").run();
    newTest().addSource("/** @unrestricted */ class C { static 'x' = 2; }").run();
    newTest().addSource("/** @unrestricted */ class C { static 1 = 2; }").run();
    newTest()
        .addSource(
            """
            /** @unrestricted */
            class C {
              /** @type {string} */
              static [x] = 2;
            }
            """)
        .run();
  }

  @Test
  public void testClassComputedFieldNoInitializer() {
    newTest().addSource("/** @dict */ class C { [x]; }").run();
    newTest().addSource("/** @dict */ class C { 'x' }").run();
    newTest().addSource("/** @dict */ class C { 1 }").run();
  }

  @Test
  public void testClassComputedFieldNoInitializerStatic() {
    newTest().addSource("/** @dict */ class C { static [x]; }").run();
    newTest().addSource("/** @dict */ class C { static 'x' }").run();
    newTest().addSource("/** @dict */ class C { static 1 }").run();
  }

  @Test
  public void testClassComputedFieldError() {
    newTest()
        .addSource("class C { [x] = 2; }")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testClassComputedFieldErrorStatic() {
    newTest()
        .addSource("class C { static [x] = 2; }")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testStrictComparison1() {
    newTest()
        .addSource("var x = true < 'asdf';")
        .addDiagnostic(
            """
            expected matching types in comparison
            found   : string
            required: boolean
            """)
        .run();
  }

  @Test
  public void testStrictComparison2() {
    newTest()
        .addSource(
            """
            function f(/** (number|string) */ x, /** string */ y) {
              return x < y;
            }
            """)
        .run();
  }

  @Test
  public void testComparisonWithUnknownAndSymbol() {
    newTest()
        .addSource("/** @type {symbol} */ var x; /** @type {?} */ var y; x < y")
        .addDiagnostic(
            """
            left side of comparison
            found   : symbol
            required: (bigint|number|string)
            """)
        .run();
  }

  @Test
  public void testComparisonInStrictModeNoSpuriousWarning() {
    newTest()
        .addSource(
            """
            function f(x) {
              var y = 'asdf';
              var z = y < x;
            }
            """)
        .run();
  }

  @Test
  public void testComparisonTreatingUnknownAsNumber() {
    // Because 'x' is unknown, we allow either of the comparible types: number or string.
    newTest()
        .suppress(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS)
        .addSource(
            """
            /** @constructor */
            function Foo() {}
            function f(/** ? */ x) {
              return (new Foo) < x;
            }
            """)
        .addDiagnostic(
            """
            left side of comparison
            found   : Foo
            required: (bigint|number|string)
            """)
        .run();
  }

  @Test
  public void testComparisonTreatingUnknownAsNumber_leftTypeUnknown() {
    // Because 'x' is unknown, we allow either of the comparible types: number or string.
    newTest()
        .suppress(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS)
        .addSource(
            """
            /** @constructor */
            function Bar() {}
            function f(/** ? */ x) {
              return x < (new Bar);
            }
            """)
        .addDiagnostic(
            """
            right side of comparison
            found   : Bar
            required: (bigint|number|string)
            """)
        .run();
  }

  @Test
  public void testEnumOfSymbol1() {
    newTest()
        .addSource(
            """
            /** @enum {symbol} */
            var ES = {A: Symbol('a'), B: Symbol('b')};

            /** @type {!Object<ES, number>} */
            var o = {};

            o[ES.A] = 1;
            """)
        .run();
  }

  @Test
  public void testEnumOfSymbol2() {
    newTest()
        .addSource(
            """
            /** @enum {symbol} */
            var ES = {A: Symbol('a'), B: Symbol('b')};

            /** @type {!Object<number, number>} */
            var o = {};

            o[ES.A] = 1;
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : ES<symbol>
            required: number
            """)
        .run();
  }

  @Test
  public void testEnumOfSymbol4() {
    newTest()
        .addSource(
            """
            /** @enum {symbol} */
            var ES = {A: Symbol('a'), B: Symbol('b')};

            /** @const */
            var o = {};

            o[ES.A] = 1;
            """)
        .run();
  }

  @Test
  public void testSymbol1() {
    newTest()
        .addSource(
            """
            /** @const */
            var o = {};

            if (o[Symbol.iterator]) { /** ok */ };
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testSymbol2() {
    newTest()
        .addSource(
            """
            /** @const */
            var o = new Symbol();
            """)
        .addDiagnostic("cannot instantiate non-constructor, found type: (typeof Symbol)")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testUnknownExtends1() {
    newTest()
        .addSource(
            """
            /**
             * @template T
             * @param {function(new: T)} clazz
             */
            function f(clazz) {
              /**
               * @constructor
               * @extends {clazz}
               */
              function Foo() {}
            }
            """)
        .run();
  }

  @Test
  public void testUnknownExtends2() {
    newTest()
        .addSource(
            """
            function f(/** function(new: ?) */ clazz) {
              /**
               * @constructor
               * @extends {clazz}
               */
              function Foo() {}
            }
            """)
        .addDiagnostic("Could not resolve type in @extends tag of Foo")
        .run();
  }

  private static final String MIXIN_DEFINITIONS =
      """
      /** @constructor */
      function MyElement() {
        /** @type {string} */
        this.elemprop = 'asdf';
      }
      /** @record */
      function Toggle() {}
      /**
       * @param {string} x
       * @return {string}
       */
      Toggle.prototype.foobar = function(x) {};
      /**
       * @template T
       * @param {function(new:T)} superclass
       * @suppress {checkTypes} // TODO(b/74120976): fix bug and remove suppression
       */
      function addToggle(superclass) {
        /**
         * @constructor
         * @extends {superclass}
         * @implements {Toggle}
         */
        function Clazz() {
          superclass.apply(this, arguments);
        }
        Clazz.prototype = Object.create(superclass.prototype);
        /** @override */
        Clazz.prototype.foobar = function(x) { return 'foobar ' + x; };
        return Clazz;
      }
      """;

  @Test
  public void testMixinApplication1() {
    newTest()
        .addSource(
            MIXIN_DEFINITIONS
                + """
                /**
                 * @constructor
                 * @extends {MyElement}
                 * @implements {Toggle}
                 */
                var MyElementWithToggle = addToggle(MyElement);
                (new MyElementWithToggle).foobar(123)
                """)
        .addDiagnostic(
"""
actual parameter 1 of MyElementWithToggle.prototype.foobar does not match formal parameter
found   : number
required: string
""")
        .run();
  }

  @Test
  public void testMixinApplication1_inTypeSummaryWithVar() {
    newTest()
        .addExterns(
            """
            /** @typeSummary */
            """
                + MIXIN_DEFINITIONS
                + """
                /**
                 * @constructor
                 * @extends {MyElement}
                 * @implements {Toggle}
                 */
                var MyElementWithToggle;
                """)
        .addSource(
            """
            class SubToggle extends MyElementWithToggle {}
            (new SubToggle).foobar(123);
            """)
        .addDiagnostic(
"""
actual parameter 1 of MyElementWithToggle.prototype.foobar does not match formal parameter
found   : number
required: string
""")
        .run();
  }

  @Test
  public void testMixinApplication1_inTypeSummaryWithLet() {
    newTest()
        .addExterns(
            """
            /** @typeSummary */
            """
                + MIXIN_DEFINITIONS
                + """
                /**
                 * @constructor
                 * @extends {MyElement}
                 * @implements {Toggle}
                 */
                let MyElementWithToggle;
                """)
        .addSource(
            """
            class SubToggle extends MyElementWithToggle {}
            (new SubToggle).foobar(123);
            """)
        .addDiagnostic(
"""
actual parameter 1 of MyElementWithToggle.prototype.foobar does not match formal parameter
found   : number
required: string
""")
        .run();
  }

  @Test
  public void testMixinApplication2() {
    newTest()
        .addSource(
            MIXIN_DEFINITIONS
                + """
                /**
                 * @constructor
                 * @extends {MyElement}
                 * @implements {Toggle}
                 */
                var MyElementWithToggle = addToggle(MyElement);
                (new MyElementWithToggle).elemprop = 123;
                """)
        .addDiagnostic(
            """
            assignment to property elemprop of MyElementWithToggle
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testMixinApplication3() {
    newTest()
        .addSource(
            MIXIN_DEFINITIONS
                + """
                var MyElementWithToggle = addToggle(MyElement);
                /** @type {MyElementWithToggle} */ var x = 123;
                """)
        .addDiagnostic(
            """
            Bad type annotation. Unknown type MyElementWithToggle
            It's possible that 'MyElementWithToggle' refers to a value, not a type.
            """)
        .run();
  }

  @Test
  public void testReassignedSymbolAccessedInClosureIsFlowInsensitive() {
    // This code is technically correct, but the compiler will probably never be able to prove it.
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            """
            function f(){
              for (var x = {}; ; x = {y: x.y}) {
                x.y = 'str';
                x.y = x.y.length
                var g = (function(x) {
                  return function() {
                    if (x.y) -x.y;
                  };
                })(x);
              }
            }
            """)
        .addDiagnostic(
            """
            sign operator
            found   : (number|string)
            required: number
            """)
        .run();
  }

  @Test
  public void testMethodOverriddenDirectlyOnThis() {
    newTest()
        .addSource(
            """
            /** @constructor */
            function Foo() {
              this.bar = function() { return 'str'; };
            }
            /** @return {number} */
            Foo.prototype.bar = function() {};
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testSuperclassDefinedInBlockUsingVar() {
    newTest()
        .addSource(
            """
            {
              /** @constructor */
              var Base = function() {};
              /** @param {number} x */
              Base.prototype.baz = function(x) {};
            }
            /** @constructor @extends {Base} */
            var Foo = function() {};
            /** @override */
            Foo.prototype.baz = function(x) {
              var /** string */ y = x;
            };
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testSuperclassDefinedInBlockOnNamespace() {
    newTest()
        .addSource(
            """
            /** @const */
            var ns = {};
            {
              /** @constructor */
              ns.Base = function() {};
              /** @param {number} x */
              ns.Base.prototype.baz = function(x) {};
            }
            /** @constructor @extends {ns.Base} */
            ns.Foo = function() {};
            /** @override */
            ns.Foo.prototype.baz = function(x) {
              var /** string */ y = x;
            };
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testPropertyReferenceOnShadowingParameter() {
    newTest()
        .addSource(
            """
            /** @const */
            var ns = {};
            ns.fn = function(/** number */ a){};
            class Namespace {
              fn(/** string */ a){}
            }
            function test(/** !Namespace */ ns) { // The parameter 'ns' shadows the global 'ns'.
            // Verify that ns.fn resolves to Namespace.prototype.fn, not the global ns.fn.
              ns.fn(0);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Namespace.prototype.fn does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testPropertyReferenceOnShadowingParameter_whenGlobalVariableIsUndeclared() {
    // Unlike the above case, this case never explicitly declares `ns` in the global scope.
    newTest()
        .includeDefaultExterns()
        .addExterns("ns.fn = function(/** number */ a){};")
        .addSource(
            """
            class Namespace {
              fn(/** string */ a){}
            }
            function test(/** !Namespace */ ns) { // The parameter 'ns' shadows the global 'ns'.
            // Verify that ns.fn resolves to Namespace.prototype.fn, not the global ns.fn.
              ns.fn(0);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Namespace.prototype.fn does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testPropertyReferenceOnShadowingVariable() {
    newTest()
        .addSource(
            """
            /** @const */
            var ns = {};
            ns.fn = function(/** number */ a){};
            class Namespace {
              fn(/** string */ a){}
            }
            function test() {
            // The local 'ns' shadows the global 'ns'.
              const /** !Namespace */ ns = /** @type {!Namespace} */ (unknown);
            // Verify that ns.fn resolves to Namespace.prototype.fn, not the global ns.fn.
              ns.fn(0);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Namespace.prototype.fn does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testPropertyReferenceOnShadowingVariable_inferredLocalSlotOverridesGlobal() {
    newTest()
        .addSource(
            """
            /** @const */
            var ns = {};
            ns.fn = function(/** number */ a){};
            /** @unrestricted */
            class Namespace {}
            function test(/** !Namespace */ ns) { // The parameter 'ns' shadows the global 'ns'.
              ns.fn = function(/** string */ a) {};
            // Verify that ns.fn resolves to the locally assigned ns.fn, not the global ns.fn.
              ns.fn(0);
            }
            """)
        .addDiagnostic("Property fn never defined on Namespace")
        .addDiagnostic(
            """
            actual parameter 1 of ns.fn does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testReassigningPropertyOnClassTypeInFunction_localSlotOverridesPrototype() {
    newTest()
        .addSource(
            """
            /** @unrestricted */
            class Namespace {
              fn(/** string */ a) {}
            }
            function test(/** !Namespace */ ns) {
            // Assign ns.fn to a narrow type than Namespace.prototype.fn.
              ns.fn = function(/** string|number */ a) {};
            // Verify that ns.fn resolves to the narrower local type, which can accept `number`.
              ns.fn(0);
            }
            """)
        .run();
  }

  @Test
  public void testPropertyReferenceOnShadowingThisProperty() {
    newTest()
        .addSource(
            """
            class C {
              constructor() {
                /** @type {?number} */
                this.size = null;
              }
              method() {
            // TODO(b/132283774): Redeclaring 'this.size' should cause an error.
                /** @type {number} */
                this.size = unknown;
                const /** number */ num = this.size;
              }
            }
            """)
        .run();
  }

  @Test
  public void testThisReferenceTighteningInInnerScope() {
    newTest()
        .addSource(
            """
            class C {
              constructor() {
                /** @type {?number} */
                this.size = 0;
            // Verify that the inferred type of `this.size`, which is `number`, propagates into
            // block scopes.
                { const /** number */ n = this.size; }
              }
            }
            """)
        .run();
  }

  @Test
  public void testShadowedForwardReferenceHoisted() {
    newTest()
        .addSource(
            """
            /** @constructor */ var C = function() { /** @type {string} */ this.prop = 's'; };
            var fn = function() {
              /** @type {C} */ var x = f();
              /** @type {number} */ var n1 = x.prop;
              /** @type {number} */ var n2 = new C().prop;
              /** @constructor */ function C() { /** @type {number} */ this.prop = 1; };
              /** @return {C} */ function fn() { return new C(); };

            }
            """)
        .run();
  }

  @Test
  public void testTypeDeclarationsShadowOneAnotherWithFunctionScoping() {
    // `C` at (2) should refer to `C` at (3) and not `C` at (1). Otherwise the assignment at (4)
    // would be invalid.
    // NOTE: This test passes only because of b/110741413, which causes the C at (1) and (3) to be
    // considered 'equal'.
    newTest()
        .addSource(
            """
            /** @constructor */
            var C = function() { }; // (1)

            var fn = function() {
              /** @type {?C} */ var innerC; // (2)

              /** @constructor */
              var C = function() { }; // (3)

              innerC = new C(); // (4)
            }
            """)
        .run();
  }

  @Test
  public void testTypeDeclarationsShadowOneAnotherWithModuleScoping_withTemplate() {
    // `C` at (2) should refer to `C` at (3) and not `C` at (1). Otherwise the assignment at (4)
    // would be invalid.
    newTest()
        .includeDefaultExterns()
        .addExterns("class C {}") // (1)
        .addSource(
            """
            /** @type {!C} */ let innerC; // (2)

            // NB: without the @template this test would pass due to b/110741413.
            /** @template T */
            class C {}; // (3)

            innerC = new C(); // (4)
            export {}
            """)
        .run(); // Make this an ES module.
  }

  @Test
  public void testTypeDeclarationsShadowOneAnotherWithModuleScoping_withSuperclass() {
    // `C` at (2) should refer to `C` at (3) and not `C` at (1). Otherwise the assignment at (4)
    // would be invalid.
    newTest()
        .includeDefaultExterns()
        .addExterns("class C {}") // (1)
        .addSource(
            """
            /** @type {!C} */ let innerC; // (2)

            class Parent {}
            class C extends Parent {}; // (3)

            /** @type {!Parent} */ const p = innerC; // (4)
            export {}
            """)
        .run(); // Make this an ES module.
  }

  @Test
  public void testTypeDeclarationsShadowOneAnotherWithFunctionScopingConsideringHoisting() {
    // TODO(b/110538992): Accuracy of shadowing is unclear here. b/110741413 may be confusing the
    // issue.

    // `C` at (3) should refer to `C` at (2) and not `C` at (1). Otherwise return the value at (4)
    // would be invalid.
    newTest()
        .addSource(
            """
            /** @constructor */
            var C = function() { }; // (1)

            var fn = function() {
              /** @constructor */
              var C = function() { }; // (2)

            // This function will be hoisted above, and therefore type-analysed before, (2).
              /** @return {!C} */ // (3)
              function hoisted() { return new C(); }; // (4)
            }
            """)
        .run();
  }

  @Test
  public void testRefinedTypeInNestedShortCircuitingAndOr() {
    // Ensure we don't have a strict property warning, and do get the right type.
    // In particular, ensure we don't forget about tracking refinements in between the two.
    newTest()
        .addSource(
            """
            /** @constructor */ function B() {}
            /** @constructor @extends {B} */ function C() {}
            /** @return {string} */ C.prototype.foo = function() {};
            /** @return {boolean} */ C.prototype.bar = function() {};
            /** @constructor @extends {B} */ function D() {}
            /** @return {number} */ D.prototype.foo = function() {};
            /** @param {!B} arg
                @return {null} */
            function f(arg) {
              if ((arg instanceof C && arg.bar()) || arg instanceof D) {
                return arg.foo();
              }
              return null;
            }
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : (number|string)
            required: null
            """)
        .run();
  }

  @Test
  public void testTypeofType_notInScope() {
    newTest()
        .addSource("var /** typeof ns */ x;")
        .addDiagnostic("Missing type for `typeof` value. The value must be declared and const.")
        .run();
  }

  @Test
  public void testTypeofType_namespace() {
    newTest()
        .addSource(
            """
            /** @const */ var ns = {};
            var /** typeof ns */ x = ns;
            """)
        .run();
  }

  @Test
  public void testTypeofType_namespaceMismatch() {
    newTest()
        .addSource(
            """
            /** @const */ var ns = {};
            var /** typeof ns */ x = {};
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : {}
            required: {}
            """)
        .run();
  }

  @Test
  public void testTypeofType_namespaceForwardReferenceMismatch() {
    newTest()
        .addSource(
            """
            var /** typeof ns */ x = {};
            /** @const */ var ns = {};
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : {}
            required: {}
            """)
        .run();
  }

  @Test
  public void testTypeofType_constructor() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            """
            /** @constructor */ function Foo() {}
            var /** !Array<typeof Foo> */ x = [];
            x.push(Foo);
            """)
        .run();
  }

  @Test
  public void testTypeofType_constructorMismatch1() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            """
            /** @constructor */ function Foo() {}
            var /** !Array<(typeof Foo)> */ x = [];
            x.push(new Foo());
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Array.prototype.push does not match formal parameter
            found   : Foo
            required: (typeof Foo)
            """)
        .run();
  }

  @Test
  public void testTypeofType_constructorMismatch2() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            """
            /** @constructor */ function Foo() {}
            var /** !Array<!Foo> */ x = [];
            var /** typeof Foo */ y = Foo;
            x.push(y);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Array.prototype.push does not match formal parameter
            found   : (typeof Foo)
            required: Foo
            """)
        .run();
  }

  @Test
  public void testTypeofType_enum() {
    newTest()
        .addSource(
            """
            /** @enum */ var Foo = {A: 1}
            var /** typeof Foo */ x = Foo;
            """)
        .run();
  }

  @Test
  public void testTypeofType_enumFromCall() {
    newTest()
        .addSource(
            """
            /** @enum */ var Foo = {A: 1}
            /** @return {*} */ function getFoo() { return Foo; }
            var x = /** @type {typeof Foo} */ (getFoo());
            """)
        .run();
  }

  @Test
  public void testTypeofType_enumMismatch1() {
    newTest()
        .addSource(
            """
            /** @enum */ var Foo = {A: 1}
            var /** typeof Foo */ x = Foo.A;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Foo<number>
            required: enum{Foo}
            """)
        .run();
  }

  @Test
  public void testTypeofType_enumMismatch2() {
    newTest()
        .addSource(
            """
            /** @enum */ var Foo = {A: 1}
            /** @enum */ var Bar = {A: 1}
            var /** typeof Foo */ x = Bar;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : enum{Bar}
            required: enum{Foo}
            """)
        .run();
  }

  @Test
  public void testTypeofType_castNamespaceIncludesPropertiesFromTypeofType() {
    newTest()
        .addSource(
            """
            /** @const */ var ns1 = {};
            /** @type {string} */ ns1.foo;
            /** @const */ var ns2 = {};
            /** @type {number} */ ns2.bar;

            /** @const {typeof ns2} */ var ns = /** @type {?} */ (ns1);
            /** @type {null} */ var x = ns.bar;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: null
            """)
        .run();
  }

  @Test
  public void testTypeofType_castNamespaceDoesNotIncludeOwnProperties() {
    newTest()
        .addSource(
            """
            /** @const */ var ns1 = {};
            /** @type {string} */ ns1.foo;
            /** @const */ var ns2 = {};
            /** @type {number} */ ns2.bar;

            /** @const {typeof ns2} */ var ns = /** @type {?} */ (ns1);
            /** @type {null} */ var x = ns.foo;
            """)
        .addDiagnostic("Property foo never defined on ns")
        .run();
  }

  @Test
  public void testTypeofType_namespaceTypeIsAnAliasNotACopy() {
    newTest()
        .addSource(
            """
            /** @const */ var ns1 = {};
            /** @type {string} */ ns1.foo;

            /** @const {typeof ns1} */ var ns = /** @type {?} */ (x);

            /** @type {string} */ ns1.bar;

            /** @type {null} */ var x = ns.bar;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: null
            """)
        .run();
  }

  @Test
  public void testTypeofType_namespacedTypeNameResolves() {
    newTest()
        .addSource(
            """
            /** @const */ var ns1 = {};
            /** @constructor */ ns1.Foo = function() {};
            /** @const {typeof ns1} */ var ns = /** @type {?} */ (x);
            /** @type {!ns.Foo} */ var x = null;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : null
            required: ns1.Foo
            """)
        .run();
  }

  @Test
  public void testTypeofType_unknownType() {
    newTest()
        .addSource(
            """
            var /** ? */ x;
            /** @type {typeof x} */ var y;
            """)
        .addDiagnostic("Missing type for `typeof` value. The value must be declared and const.")
        .run();
  }

  @Test
  public void testTypeofType_namespacedTypeOnIndirectAccessNameResolves() {
    newTest()
        .addSource(
            """
            /** @const */ var ns1 = {};
            /** @constructor */ ns1.Foo = function() {};
            const ns2 = ns1;
            // Verify this works although we have never seen any assignments to `ns2.Foo`
            /** @const {typeof ns2.Foo} */ var Foo2 = /** @type {?} */ (x);
            /** @type {null} */ var x = new Foo2();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : ns1.Foo
            required: null
            """)
        .run();
  }

  @Test
  public void testTypeofType_namespacedTypeMissing() {
    newTest()
        .addSource(
            """
            /** @const */ var ns = {};
            /** @const {typeof ns.Foo} */ var Foo = /** @type {?} */ (x);
            """)
        .addDiagnostic("Missing type for `typeof` value. The value must be declared and const.")
        .run();
  }

  @Test
  public void testTypeofType_withGlobalDeclaredVariableWithHoistedFunction() {
    newTest()
        .addSource(
            """
            /** @type {string|number} */
            var x = 1;
            function g(/** typeof x */ a) {}
            x = 'str';
            g(null);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : null
            required: (number|string)
            """)
        .run();
  }

  @Test
  public void testTypeofType_typeofForwardReferencedShadowingLocal() {
    newTest()
        .addSource(
            """
            /** @const {string} */
            var x = 'x';
            function f() {
              function g(/** typeof x */ a) {}
              /** @const {number} */
              var x = 1;
              g(null);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : null
            required: number
            """)
        .run();
  }

  @Test
  public void testTypeofType_withGlobalDeclaredVariableWithFunction() {
    newTest()
        .addSource(
            """
            /** @type {string|number} */
            var x = 1;
            let g = function(/** typeof x */ a) {}
            x = 'str';
            g(null);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : null
            required: (number|string)
            """)
        .run();
  }

  @Test
  public void testTypeofType_withGlobalInferredVariableWithFunctionExpression() {
    newTest()
        .addSource(
            """
            var x = 1;
            var g = function (/** typeof x */ a) {}
            x = 'str';
            g(null);
            """)
        .addDiagnostic("Missing type for `typeof` value. The value must be declared and const.")
        .run();
  }

  @Test
  public void testTypeofType_withLocalInferredVariableInHoistedFunction() {
    newTest()
        .addSource(
            """
            function f() {
              var x = 1;
              function g(/** typeof x */ a) {}
              x = 'str';
              g(null);
            }
            """)
        .addDiagnostic("Missing type for `typeof` value. The value must be declared and const.")
        .run();
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariableWithHoistedFunction() {
    newTest()
        .addSource(
            """
            function f() {
              /** @type {string|number} */
              var x = 1;
              function g(/** typeof x */ a) {}
              x = 'str';
              g(null);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : null
            required: (number|string)
            """)
        .run();
  }

  @Test
  public void testTypeofType_withLocalInferredVariableWithFunctionExpression() {
    newTest()
        .addSource(
            """
            function f() {
              var x = 1;
              var g = function (/** typeof x */ a) {}
              x = 'str';
              g(null);
            }
            """)
        .addDiagnostic("Missing type for `typeof` value. The value must be declared and const.")
        .run();
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariableWithFunctionExpression() {
    newTest()
        .addSource(
            """
            function f() {
              /** @type {string|number} */
              var x = 1;
              var g = function (/** typeof x */ a) {}
              x = 'str';
              g(null);
            }
            """)
        .addDiagnostic(
            """
            actual parameter 1 of g does not match formal parameter
            found   : null
            required: (number|string)
            """)
        .run();
  }

  @Test
  public void testTypeofType_withLocalInferredVariable() {
    newTest()
        .addSource(
            """
            function f() {
              var x = 1;
              /** @type {typeof x} */
              var g = null
              x = 'str';
            }
            """)
        .addDiagnostic("Missing type for `typeof` value. The value must be declared and const.")
        .run();
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariable1() {
    newTest()
        .addSource(
            """
            function f() {
              /** @type {string|number} */
              var x = 1;
              /** @type {typeof x} */
              var g = null
              x = 'str';
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : null
            required: (number|string)
            """)
        .run();
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariableAfterReassignment() {
    newTest()
        .addSource(
            """
            function f() {
              /** @type {string|number} */
              var x = 1;
              x = 'str';
              /** @type {typeof x} */
              var g = null
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : null
            required: (number|string)
            """)
        .run();
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariableAfterTightening() {
    newTest()
        .addSource(
            """
            function f() {
              /** @type {string|number} */
              var x = 'str';
              if (typeof x == 'string') {
                /** @type {typeof x} */
                let g = null
              }
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : null
            required: (number|string)
            """)
        .run();
  }

  @Test
  public void testTypeNameAliasOnAliasedNamespace() {
    newTest()
        .addSource(
            """
            class Foo {};
            /** @enum {number} */ Foo.E = {A: 1};
            const F = Foo;
            const E = F.E;
            /** @type {E} */ let e = undefined;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : undefined
            required: Foo.E<number>
            """)
        .run();
  }

  @Test
  public void testAssignOp() {
    newTest()
        .addSource(
            """
            function fn(someUnknown) {
              var x = someUnknown;
              x *= 2; // infer 'x' to now be 'number'
              var /** null */ y = x;
            }
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : number
            required: null
            """)
        .run();
  }

  @Test
  public void testAnnotatedObjectLiteralInBlocklessArrow1() {
    newTest()
        .addSource(
            """
            function f(/** {g: function(number): undefined} */ x) {}
            () => f({/** @param {string} x */ g(x) {}});
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : {g: function(string): undefined}
            required: {g: function(number): undefined}
            missing : []
            mismatch: [g]
            """)
        .run();
  }

  @Test
  public void testAnnotatedObjectLiteralInBlocklessArrow2() {
    newTest()
        .addSource(
            """
            function f(/** {g: function(): string} */ x) {}
            () => f({/** @constructor */ g: function() {}});
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : {g: (typeof <anonymous@testcode0:2>)}
            required: {g: function(): string}
            missing : []
            mismatch: [g]
            """)
        .run();
  }

  @Test
  public void testAnnotatedObjectLiteralInBlocklessArrow3() {
    newTest()
        .addSource(
            """
            /** @constructor */ function G() {}
            function f(/** {g: function(): string} */ x) {}
            () => f({/** @constructor */ g: G});
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : {g: (typeof G)}
            required: {g: function(): string}
            missing : []
            mismatch: [g]
            """)
        .run();
  }

  @Test
  public void testNativePromiseTypeWithExterns() {
    // Test that we add Promise prototype properties defined in externs to the native Promise type
    newTest()
        .addSource(
            """
            var p = new Promise(function(resolve, reject) { resolve(3); });
            p.then(result => {}, error => {});
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testNativeThenableType() {
    // Test that the native Thenable type is not nullable even without externs
    newTest()
        .addSource("var /** Thenable */ t = null;")
        .addDiagnostic(
            """
            initializing variable
            found   : null
            required: {then: ?}
            """)
        .run();
  }

  @Test
  public void testFunctionOnObjLitWithAngleBracketKey() {
    newTest()
        .addSource(
            """
            /** @const {!Object<function()>} */
            var x = {'<': function() {}};
            """)
        .run();
  }

  @Test
  public void testFunctionOnObjLitWithAngleBracketKeyChecked() {
    newTest()
        .addSource(
            """
            /** @const {!Object<function(): number>} */
            var x = {'<': function() { return 'str'; }};
            """)
        .run();
  }

  @Test
  public void testFunctionOnObjLitWithAngleBracketKeyAndJsdoc() {
    newTest()
        .addSource(
            """
            /** @const */
            var x = {
              /** @param {number} arg */
              '<': function(arg) {},
            };
            """)
        .run();
  }

  @Test
  public void testClassOnObjLitWithAngleBracketKey() {
    newTest()
        .addSource(
            """
            /** @const */
            var x = {
              /** @constructor */
              '<': function() {},
            };
            """)
        .run();
  }

  @Test
  public void testQuotedPropertyWithDotInType() {
    newTest()
        .addSource(
            """
            /** @const */
            var x = {
              /** @constructor */
              'y.A': function() {},
            };
            var /** x.y.A */ a;
            """)
        .addDiagnostic(
            """
            Bad type annotation. Unknown type x.y.A
            It's possible that 'x.y.A' refers to a value, not a type.
            """)
        .run();
  }

  @Test
  public void testQuotedPropertyWithDotInCode() {
    newTest()
        .addSource(
            """
            /** @const */
            var x = {
              /** @constructor */
              'y.A': function() {},
            };
            var a = new x.y.A();
            """)
        .addDiagnostic("Property y never defined on x")
        .addDiagnostic("Property A never defined on x.y" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testClassTemplateParamsInMethodBody() {
    newTest()
        .addSource(
            """
            /** @constructor @template T */
            function Foo() {}
            Foo.prototype.foo = function() {
              var /** T */ x;
            };
            """)
        .run();
  }

  @Test
  public void testClassTtlParamsInMethodBody() {
    newTest()
        .addSource(
            """
            /** @constructor @template T := 'number' =: */
            function Foo() {}
            Foo.prototype.foo = function() {
              var /** T */ x;
            };
            """)
        .addDiagnostic("Template type transformation T not allowed on classes or interfaces")
        .addDiagnostic("Bad type annotation. Unknown type T")
        .run();
  }

  @Test
  public void testClassTtlParamsInMethodSignature() {
    newTest()
        .addSource(
            """
            /** @constructor @template T := 'number' =: */
            function Foo() {}
            /** @return {T} */
            Foo.prototype.foo = function() {};
            """)
        .addDiagnostic("Template type transformation T not allowed on classes or interfaces")
        .addDiagnostic("Bad type annotation. Unknown type T")
        .run();
  }

  @Test
  public void testFunctionTemplateParamsInBody() {
    newTest()
        .addSource(
            """
            /** @template T */
            function f(/** !Array<T> */ arr) {
              var /** T */ first = arr[0];
            }
            """)
        .run();
  }

  @Test
  public void testFunctionTtlParamsInBody() {
    newTest()
        .addSource(
            """
            /** @template T := 'number' =: */
            function f(/** !Array<T> */ arr) {
              var /** T */ first = arr[0];
            }
            """)
        .run();
  }

  @Test
  public void testFunctionTemplateParamsInBodyMismatch() {
    newTest()
        .addSource(
            """
            /** @template T, V */
            function f(/** !Array<T> */ ts, /** !Array<V> */ vs) {
              var /** T */ t = vs[0];
            }
            """)
        .run();
    // TODO(b/35241823): This should be an error, but we currently treat generic types
    // as unknown. Once we have bounded generics we can treat templates as unique types.
    //   """
    //   actual parameter 1 of f does not match formal parameter
    //   found   : V
    //   required: T
    //   """
  }

  @Test
  public void testClassAndMethodTemplateParamsInMethodBody() {
    newTest()
        .addSource(
            """
            /** @constructor @template T */
            function Foo() {}
            /** @template U */
            Foo.prototype.foo = function() {
              var /** T */ x;
              var /** U */ y;
            };
            """)
        .run();
  }

  @Test
  public void testNestedFunctionTemplates() {
    newTest()
        .addSource(
            """
            /** @constructor @template A, B */
            function Foo(/** A */ a, /** B */ b) {}
            /** @template T */
            function f(/** T */ t) {
              /** @template S */
              function g(/** S */ s) {
                var /** !Foo<T, S> */ foo = new Foo(t, s);
              }
            }
            """)
        .run();
  }

  @Test
  public void testNestedFunctionTemplatesMismatch() {
    newTest()
        .addSource(
            """
            /** @constructor @template A, B */
            function Foo(/** A */ a, /** B */ b) {}
            /** @template T */
            function f(/** T */ t) {
              /** @template S */
              function g(/** S */ s) {
                var /** !Foo<T, S> */ foo = new Foo(s, t);
              }
            }
            """)
        .run();
    // TODO(b/35241823): This should be an error, but we currently treat generic types
    // as unknown. Once we have bounded generics we can treat templates as unique types.
    //   """
    //     initializing variable
    //     found   : Foo<S, T>
    //     required: Foo<T, S>
    //   """
  }

  @Test
  public void testTypeCheckingDoesntCrashOnDebuggerStatement() {
    newTest().addSource("var x = 1; debugger; x = 2;").run();
  }

  @Test
  public void testCastOnLhsOfAssignBlocksBadAssignmentWarning() {
    newTest().addSource("var /** number */ x = 1; (/** @type {?} */ (x)) = 'foobar';").run();
  }

  @Test
  public void testCastOnLhsWithAdditionalJSDoc() {
    // TODO(b/123955687): this should issue a type error because Foo.prototype.takesString is passed
    // a number
    newTest()
        .addSource(
            """
            class Foo {
              takesString(/** string */ stringParameter) {}
            }
            /** @param {*} all */
            function f(all) {
              /** some jsdoc */
              (/** @type {!Foo} */ (all)).takesString(0);
            }
            """)
        .run();
  }

  @Test
  public void testInvalidComparisonsInStrictOperatorsMode() {
    newTest()
        .addSource("function f(/** (void|string) */ x, /** void */ y) { return x < y; }")
        .addDiagnostic(
            """
            right side of comparison
            found   : undefined
            required: string
            """)
        .run();
  }

  @Test
  public void testStrayTypedefOnRecordInstanceDoesNotModifyRecord() {
    // This is a common pattern for angular.  This test is checking that adding the property to one
    // instance does not require all other instances to specify it as well.
    newTest()
        .addSource(
            """
            /** @record */
            function Component() {}

            /** @const {!Component} */
            var MyComponent = {};
            /** @typedef {string} */
            MyComponent.MyString;

            /** @const {!Component} */
            var OtherComponent = {};
            """)
        .run();
  }

  @Test
  public void testStrayPropertyOnRecordInstanceDoesNotModifyRecord() {
    // This is a common pattern for angular.  This test is checking that adding the property to one
    // instance does not require all other instances to specify it as well.
    newTest()
        .addSource(
            """
            /** @record */
            function Component() {}

            /** @const {!Component} */
            var MyComponent = {};
            /** @const {string} */
            MyComponent.NAME = 'MyComponent'; // strict inexistent property is usually suppressed

            /** @const {!Component} */
            var OtherComponent = {};
            """)
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testStrayTypedefOnRecordTypedefDoesNotModifyRecord() {
    // This is a common pattern for angular.  This test is checking that adding the property to one
    // instance does not require all other instances to specify it as well.
    newTest()
        .addSource(
            """
            /** @typedef {{foo: number}} */
            var Component;

            /** @const {!Component} */
            var MyComponent = {foo: 1};
            /** @typedef {string} */
            MyComponent.MyString;

            /** @const {!Component} */
            var OtherComponent = {foo: 2};
            """)
        .run();
  }

  @Test
  public void testCanUseConstToAssignValueToTypedef() {
    newTest()
        .addSource(
            """
            /** @typedef {!Object<string, number>} */
            const Type = {};
            """)
        .run();
  }

  @Test
  public void testLetTypedefWithAssignedValue_reassigned() {
    newTest()
        .addSource(
            """
            /** @typedef {!Object<string, number>} */
            let Type = {};
            Type = {x: 3};
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : {}
            required: None
            """)
        .addDiagnostic(
            """
            assignment
            found   : {x: number}
            required: None
            """)
        .run();
  }

  @Test
  public void testLetTypedefWithAssignedValue_notReassigned() {
    newTest()
        .addSource(
            """
            /** @typedef {!Object<string, number>} */
            let Type = {};
            """)
        .run();
  }

  @Test
  public void testStrayPropertyOnRecordTypedefDoesNotModifyRecord() {
    // This is a common pattern for angular.  This test is checking that adding the property to one
    // instance does not require all other instances to specify it as well.
    newTest()
        .addSource(
            """
            /** @typedef {{foo: number}} */
            var Component;

            /** @const {!Component} */
            var MyComponent = {foo: 1 };
            /** @const {string} */
            MyComponent.NAME = 'MyComponent';

            /** @const {!Component} */
            var OtherComponent = {foo: 2};
            """)
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testCheckRecursiveTypedefSubclassOfNominalClass() {
    newTest()
        .addSource(
            """
            /** @typedef {{self: !Bar}} */
            var Foo;
            /** @typedef {!Foo} */
            var Bar;

            /** @type {!Foo} */
            var foo;

            class Baz {
              constructor() {
                /** @type {!Baz} */ this.self = this;
              }
            }

            const /** !Baz */ x = foo;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : {self: {...}}
            required: Baz
            """)
        .run();
  }

  @Test
  public void testCheckNominalClassSubclassOfRecursiveTypedef() {
    newTest()
        .addSource(
            """
            /** @typedef {{self: !Bar}} */
            var Foo;
            /** @typedef {!Foo} */
            var Bar;

            class Baz {
              constructor() {
                /** @type {!Baz} */ this.self = this;
              }
            }

            const /** !Foo */ x = new Baz();
            """)
        .run();
  }

  @Test
  public void testNullableToStringSubclassOfRecordLiteral() {
    // Note: this is an interesting case because inline record literal types are implicit subtypes
    // of Object, and thus inherit Object's prototype methods (toString and valueOf).  But once
    // the warning is suppressed in the class definition, classes that break these methods'
    // contracts should still be usable.
    newTest()
        .addExterns(new TestExternsBuilder().addString().addObject().build())
        .addSource(
            """
            /** @struct @constructor */
            function MyClass() {}

            /**
             * @override
             * @return {?string}
             * @suppress {checkTypes} // J2CL allows this, so we need to test it.
             */
            MyClass.prototype.toString = function() {};

            // To do a strucutural match, this type needs at least one additional property.
            // There is no empty `{}` type.
            /** @return {number} */
            MyClass.prototype.x = function() {};

            var /** {x: !Function} */ instance = new MyClass();
            """)
        .run();
  }

  @Test
  public void testForwardReferencedRecordTypeUnionedWithConcreteType() {
    // These types should be disjoint and both should be preserved both before and after type
    // resolution.  This is not particularly special, but it covers a previously-uncovered case.
    newTest()
        .addSource(
            """
            class Concrete {}

            /**
             * @param {!ForwardReferenced|!Concrete} arg
             */
            var bar = (arg) => {};

            /**
             * @typedef {{baz}}
             */
            var ForwardReferenced;

            /**
             * @param {!Concrete} arg
             */
            var foo = (arg) => bar(arg);
            """)
        .run();
  }

  @Test
  public void testUnionCollapse_recordWithOnlyOptionalPropertiesDoesNotSupercedeArrayOrFunction() {
    // Ensure the full union type is preserved
    newTest()
        .addSource(
            """
            /** @typedef {{x: (string|undefined)}} */
            var Options;
            /** @constructor */
            function Foo() {}
            /** @typedef {function(new: Foo)|!Array<function(new: Foo)>} */
            var FooCtor;
            /** @type {FooCtor|Options} */
            var x;
            /** @type {null} */
            var y = x;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (Array<function(new:Foo): ?>|function(new:Foo): ?|{x: (string|undefined)})
            required: null
            """)
        .run();
  }

  @Test
  public void testCyclicUnionTypedefs() {
    // TODO(b/112964849): This case should not throw anything.
    assertThrows(
        StackOverflowError.class,
        newTest()
                .addSource(
                    """
                    /** @typedef {Foo|string} */
                    var Bar;
                    /** @typedef {Bar|number} */
                    var Foo;
                    var /** Foo */ foo;
                    var /** null */ x = foo;
                    """)
                .addDiagnostic(
                    """
                    initializing variable
                    found   : (number|string)
                    required: null
                    """)
            ::run);
  }

  @Test
  public void testAtRecordOnVarObjectLiteral_warns() {
    newTest()
        .addSource("/** @record */ var X = {};")
        .addDiagnostic(
            """
            initializing variable
            found   : {}
            required: function(this:X): ?
            """)
        .run();
  }

  @Test
  public void testAtRecordOnConstObjectLiteral_warns() {
    newTest()
        .addSource("/** @record */ const X = {};")
        .addDiagnostic(
            """
            initializing variable
            found   : {}
            required: function(this:X): ?
            """)
        .run();
  }

  @Test
  public void testAtConstructorOnConstObjectLiteral_warns() {
    newTest()
        .addSource("/** @constructor */ const X = {};")
        .addDiagnostic(
            """
            initializing variable
            found   : {}
            required: function(new:X): ?
            """)
        .run();
  }

  @Test
  public void testDeclarationAnnotatedEnum_warnsIfAssignedNonEnumRhs() {
    newTest()
        .addSource(
            // Y looks similar to an enum but is not actually annotated as such
            "const Y = {A: 0, B: 1}; /** @enum {number} */ const X = Y;")
        .addDiagnostic(
            """
            initializing variable
            found   : {
              A: number,
              B: number
            }
            required: enum{X}
            """)
        .run();
  }

  @Test
  public void testEs6ExtendCannotUseGoogInherits() {
    testClosureTypes(
        """
        class Super {}
        /** @extends {Super} */
        class Sub {}
        goog.inherits(Sub, Super);
        """,
        """
        Do not use goog.inherits with ES6 classes. Use the ES6 `extends` keyword to inherit instead.
        """);
  }

  @Test
  public void testParameterShadowsNamespace() {
    // NOTE: This is a pattern used to work around the fact that @ngInjected constructors very
    // frequently (and unavoidably) shadow global namespaces (i.e. angular.modules) with constructor
    // parameters.
    newTest()
        .addSource(
            """
            /** @constructor @param {!service.Service} service */
            var Controller = function(service) {
              /** @private {!service.Service} */
              this.service = service;
            };
            /** @const */
            var service = {};
            /** @constructor */
            service.Service = function() {};
            """)
        .addDiagnostic(
"""
Bad type annotation. Unknown type service.Service
It's possible that a local variable called 'service' is shadowing the intended global namespace.
""")
        .run();
  }

  @Test
  public void testValueUsedAsType() {
    newTest()
        .addSource(
            """
            /** @type {?} */ var Foo = 'Foo';
            /** @type {!Foo} */ var foo = 'foo';
            """)
        .addDiagnostic(
            """
            Bad type annotation. Unknown type Foo
            It's possible that 'Foo' refers to a value, not a type.
            """)
        .run();
  }

  @Test
  public void testBangOperatorOnForwardReferencedType() {
    newTest()
        .addSource(
            """
            /** @typedef {?number} */
            var Foo;
            /** @return {!Foo} */
            function f() {}
            /** @const {!Foo} */
            var x = f();
            """)
        .run();
  }

  @Test
  public void testBangOperatorOnForwardReferencedType_mismatch() {
    newTest()
        .addSource(
            """
            /** @type {!Foo} */
            var x;
            /** @typedef {?number} */
            var Foo;
            /** @type {?Foo} */
            var y;
            x = y;
            """)
        .addDiagnostic(
            """
            assignment
            found   : (null|number)
            required: number
            """)
        .run();
  }

  @Test
  public void testBangOperatorOnIndirectlyForwardReferencedType() {
    newTest()
        .addSource(
            """
            /** @return {!b.Foo} */
            function f() {}
            /** @const {!b.Foo} */
            var x = f();

            const a = {};
            /** @typedef {?number} */
            a.Foo;
            const b = a;
            """)
        .run();
  }

  @Test
  public void testBangOperatorOnIndirectlyForwardReferencedType_mismatch() {
    newTest()
        .addSource(
            """
            /** @return {?b.Foo} */
            function f() {}
            /** @const {!b.Foo} */
            var x = f();

            const a = {};
            /** @typedef {?number} */
            a.Foo;
            const b = a;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (null|number)
            required: number
            """)
        .run();
  }

  @Test
  public void testBangOperatorOnTypedefForShadowedNamespace() {
    // NOTE: This is a pattern used to work around the fact that @ngInjected constructors very
    // frequently (and unavoidably) shadow global namespaces (i.e. angular.modules) with constructor
    // parameters.
    newTest()
        .addSource(
            """
            /** @typedef {!service.Service} */
            var serviceService;
            /** @constructor @param {!service.Service} service */
            var Controller = function(service) {
              /** @private {!serviceService} */
              this.service = service;
            };
            /** @const */
            var service = {};
            /** @constructor */
            service.Service = function() {};
            """)
        .run();
  }

  @Test
  public void testNullCheckAvoidsInexistentPropertyError() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            function use(x) {}
            /** @constructor */ function Foo() {}
            var /** !Foo */ foo = new Foo();
            if (foo.bar != null) use(foo);
            """)
        .run();
  }

  @Test
  public void testTripleEqualsNonNullCheckGivesInexistentPropertyError() {
    newTest()
        .suppress(DiagnosticGroups.STRICT_MISSING_PROPERTIES)
        .addSource(
            """
            function use(x) {}
            /** @constructor */ function Foo() {}
            var /** !Foo */ foo = new Foo();
            if (foo.bar !== null) use(foo);
            """)
        .addDiagnostic("Property bar never defined on Foo")
        .run();
  }

  @Test
  public void testGenericConstructorCrash() {
    newTest()
        .addSource(
            """
            /** @template T */
            class Base {
             /**
              * @param {(function(function(new: T, ...?)): T)=} instantiate
              * @return {!Array<T>}
              */
             delegates(instantiate = undefined) {
               return [instantiate];
             };
            }
            class Bar {}
            class Foo {}

            /** @type {Base<Foo|Bar>} */
            const c = new Base();

            c.delegates(ctor => new ctor(42));
            """)
        .run();
  }

  @Test
  public void testLegacyGoogModuleExportTypecheckedAgainstGlobal_simpleModuleId() {
    newTest()
        .addExterns(
            """
            /** @const @suppress {duplicate} */
            var globalNs = {};
            """)
        .addSource(
            """
            goog.module('globalNs');
            goog.module.declareLegacyNamespace();
            exports = 0;
            """)
        .addDiagnostic(
            """
            legacy goog.module export
            found   : number
            required: {}
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testLegacyGoogModuleExportTypecheckedAgainstGlobal_simpleModuleIdWithProperties() {
    newTest()
        .addExterns(
            """
            /** @const @suppress {duplicate} */
            var globalNs = {};
            """)
        .addSource(
            """
            goog.module('globalNs');
            goog.module.declareLegacyNamespace();
            exports.prop = 0;
            """)
        .addDiagnostic(
            """
            legacy goog.module export
            found   : {prop: number}
            required: {}
            """)
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testLegacyGoogModuleExportTypecheckedAgainstGlobal_dottedModuleId() {
    newTest()
        .addExterns(
            """
            /** @const @suppress {duplicate} */
            var globalNs = {};
            /** @suppress {duplicate} */
            globalNs.Ctor = class {};
            """)
        .includeDefaultExterns()
        .addSource(
            """
            goog.module('globalNs.Ctor');
            goog.module.declareLegacyNamespace();
            class Ctor {}
            exports = Ctor;
            """)
        .addDiagnostic(
            """
            assignment to property Ctor of globalNs
            found   : (typeof Ctor)
            required: (typeof globalNs.Ctor)
            """)
        .run();
  }

  @Test
  public void testDynamicImportSpecifier() {
    newTest()
        .addSource("var foo = undefined; import(foo);")
        .addDiagnostic(
            """
            dynamic import specifier
            found   : undefined
            required: string
            """)
        .run();
  }

  @Test
  public void testDynamicImport1() {
    newTest()
        .suppress(DiagnosticGroup.forType(ModuleLoader.INVALID_MODULE_PATH))
        .addSource("/** @type {number} */ var foo = import('foo.js');")
        .addDiagnostic(
            """
            initializing variable
            found   : Promise<?>
            required: number
            """)
        .run();
  }

  @Test
  public void testDynamicImport2() {
    newTest()
        .suppress(DiagnosticGroup.forType(ModuleLoader.INVALID_MODULE_PATH))
        .addSource("/** @type {Promise} */ var foo2 = import('foo.js');")
        .run();
  }

  @Test
  public void testDynamicImport3() {
    newTest()
        .suppress(DiagnosticGroup.forType(ModuleLoader.INVALID_MODULE_PATH))
        .addSource("/** @type {Promise<{default: number}>} */ var foo = import('foo.js');")
        .run();
  }

  @Test
  public void testConflictingGetterSetterType() {
    newTest()
        .addSource(
            """
            class C {
              /** @return {string} */
              get value() { }

              /** @param {number} v */
              set value(v) { }
            }
            """)
        .addDiagnostic(CONFLICTING_GETTER_SETTER_TYPE)
        .run();
  }

  @Test
  public void testConflictingGetterSetterTypeSuppressed() {
    newTest()
        .addSource(
            """
            /** @suppress {checkTypes} */
            class C {
              /** @return {string} */
              get value() { }

              /** @param {number} v */
              set value(v) { }
            }
            """)
        .run();
  }

  @Test
  public void testSuppressCheckTypesOnStatements() {
    newTest()
        .addSource(
            """
            /** @param {string} s */
            function takesString(s) {}

            takesString(0); // verify this is an error
            /** @suppress {checkTypes} */
            takesString(null);
            /** @suppress {checkTypes} */
            (0, takesString(undefined));
            """)
        .addDiagnostic(
            """
            actual parameter 1 of takesString does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  @Ignore("b/221480261")
  public void testSymbolIteratorMethod() {
    newTest()
        .includeDefaultExterns()
        .addSource("const /** number */ num = [][Symbol.iterator];")
        .addDiagnostic(TypeValidator.TYPE_MISMATCH_WARNING)
        .run();
  }

  @Test
  public void testGoogRequireDynamic_missingSources() {
    // regression test for case that used to throw an exception
    newTest()
        // reference to dynamically loaded namespace.
        .suppress(DiagnosticGroups.MISSING_SOURCES_WARNINGS)
        .addSource(
            """
            class Test {
             /**
              * @return {!Object}
              *
              * @suppress {checkTypes}
              */
            testGoogRequireDynamicStubbedAndWithLoadedModule() {
               goog.setImportHandlerInternalDoNotCallOrElse(() => Promise.resolve(null));
               goog.setUncompiledChunkIdHandlerInternalDoNotCallOrElse(s => s);

               goog.loadModule('goog.module("a.loaded.module"); exports.foo = 12;');

               return goog.requireDynamic('a.loaded.module')
                   .then(({foo}) => assertEquals(foo, 12));
             } }
            """)
        .run();
  }

  @Test
  public void testClassMultipleExtends_fromClosureJs() {
    // Tests a workaround for b/325489639
    // This is a hacky fix for a Closure type system vs. TypeScript compatibility problem:
    // TypeScript allows extending classes, while Closure does not. This results in tsickle
    // sometimes outputting classes with multiple @extends clauses. The Closure type system doesn't
    // support this, but does allow suppressing it via checkTypes in .closure.js files, and then
    // treats the resulting subtype as "unknown" as not to mislead type-based optimizations into
    // thinking it can handle this type.
    newTestLegacy() // using the legacy method so we can access compiler.getTopScope() later.
        .addSource(
            "testcode0.closure.js",
            """
            /** @fileoverview @suppress {checkTypes} */
            class Foo {}
            class Bar {}
            /**
             * @extends {Foo}
             * @extends {Bar}
             */
            class FooBar {}
            """)
        .run();

    FunctionType fooBar = compiler.getTopScope().getVar("FooBar").getType().assertFunctionType();
    assertThat(fooBar.isAmbiguousConstructor()).isTrue();
  }

  @Test
  public void testReassignClassPrototype() {
    newTest()
        .addSource(
            """
            class Foo {}
            Foo.prototype = {};
            """)
        .addDiagnostic(TypeInference.REASSIGN_CLASS_PROTOTYPE)
        .diagnosticsAreErrors()
        .run();
  }

  @Test
  public void testReassignClassPrototypeObjectType() {
    newTest()
        .addSource(
            """
            class Foo {}
            const x = /** @type {!Object} */ ({});
            Foo.prototype = x;
            """)
        .addDiagnostic(TypeInference.REASSIGN_CLASS_PROTOTYPE)
        .diagnosticsAreErrors()
        .run();
  }

  private void testClosureTypes(String js, @Nullable String description) {
    testClosureTypesMultipleWarnings(
        js, description == null ? null : ImmutableList.of(description.trim()));
  }

  private void testClosureTypesMultipleWarnings(String js, @Nullable List<String> descriptions) {
    compiler.initOptions(compiler.getOptions());
    Node jsRoot = IR.root(compiler.parseTestCode(js));
    Node externs =
        IR.root(
            compiler.parseTestCode(
                new TestExternsBuilder()
                    .addString()
                    .addClosureExterns()
                    .addExtra(CLOSURE_DEFS)
                    .build()));
    IR.root(externs, jsRoot);

    assertWithMessage("parsing error: " + Joiner.on(", ").join(compiler.getErrors()))
        .that(compiler.getErrorCount())
        .isEqualTo(0);

    new GatherModuleMetadata(compiler, false, ResolutionMode.BROWSER).process(externs, jsRoot);

    // For processing goog.forwardDeclare for forward typedefs.
    new ProcessClosurePrimitives(compiler).process(externs, jsRoot);

    new TypeCheck(
            compiler,
            new ClosureReverseAbstractInterpreter(registry)
                .append(new SemanticReverseAbstractInterpreter(registry))
                .getFirst(),
            registry)
        .processForTesting(externs, jsRoot);

    assertWithMessage("unexpected error(s) : " + Joiner.on(", ").join(compiler.getErrors()))
        .that(compiler.getErrorCount())
        .isEqualTo(0);

    if (descriptions == null) {
      assertWithMessage("unexpected warning(s) : " + Joiner.on(", ").join(compiler.getWarnings()))
          .that(compiler.getWarningCount())
          .isEqualTo(0);
    } else {
      assertWithMessage("unexpected warning(s) : " + Joiner.on(", ").join(compiler.getWarnings()))
          .that(compiler.getWarningCount())
          .isEqualTo(descriptions.size());
      Set<String> actualWarningDescriptions = new HashSet<>();
      for (int i = 0; i < descriptions.size(); i++) {
        actualWarningDescriptions.add(compiler.getWarnings().get(i).description());
      }
      assertThat(actualWarningDescriptions).isEqualTo(new HashSet<>(descriptions));
    }
  }
}
