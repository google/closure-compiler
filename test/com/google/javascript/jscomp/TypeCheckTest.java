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
import org.jspecify.nullness.Nullable;
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
      "/** @constructor\n */\n"
          + "function Suggest() {}\n"
          + "Suggest.prototype.a = 1;\n"
          + "Suggest.prototype.veryPossible = 1;\n"
          + "Suggest.prototype.veryPossible2 = 1;\n";

  private static final String ILLEGAL_PROPERTY_CREATION_MESSAGE =
      "Cannot add a property"
          + " to a struct instance after it is constructed. (If you already declared the property,"
          + " make sure to give it a type.)";

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
        .addDiagnostic("initializing variable\n" + "found   : boolean\n" + "required: number")
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
        .addDiagnostic("increment/decrement\n" + "found   : undefined\n" + "required: number")
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
            "/**@return {void}*/function foo(){"
                + "/** @type {undefined|number} */var a;if (a == foo())return;}")
        .run();
  }

  @Test
  public void testTypeCheck8() {
    newTest().addSource("/**@return {void}*/function foo(){do {} while (foo());}").run();
  }

  @Test
  public void testTypeCheck9() {
    newTest().addSource("/**@return {void}*/function foo(){while (foo());}").run();
  }

  @Test
  public void testTypeCheck10() {
    newTest().addSource("/**@return {void}*/function foo(){for (;foo(););}").run();
  }

  @Test
  public void testTypeCheck11() {
    newTest()
        .addSource("/**@type {!Number} */var a;" + "/**@type {!String} */var b;" + "a = b;")
        .addDiagnostic("assignment\n" + "found   : String\n" + "required: Number")
        .run();
  }

  @Test
  public void testTypeCheck12() {
    newTest()
        .addSource("/**@return {!Object}*/function foo(){var a = 3^foo();}")
        .addDiagnostic(
            "bad right operand to bitwise operator\n"
                + "found   : Object\n"
                + "required: (boolean|null|number|string|undefined)")
        .run();
  }

  @Test
  public void testTypeCheck13() {
    newTest()
        .addSource("/**@type {!Number|!String}*/var i; i=/xx/;")
        .addDiagnostic("assignment\n" + "found   : RegExp\n" + "required: (Number|String)")
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
        .addDiagnostic("assignment\n" + "found   : number\n" + "required: (Number|null)")
        .run();
  }

  @Test
  public void testTypeCheck16() {
    newTest()
        .addSource("/**@type {Number|null} */var x='';")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: (Number|null)")
        .run();
  }

  @Test
  public void testTypeCheck17() {
    newTest()
        .addSource(
            "/**@return {Number}\n@param {Number} opt_foo */\n"
                + "function a(opt_foo){\nreturn /**@type {Number}*/(opt_foo);\n}")
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
            "/** @constructor */function foo() {}\n"
                + "/** @type {Object} */ var bar = new foo();\n")
        .run();
  }

  @Test
  public void testTypeCheckNoDowncastToNumber() {
    newTest()
        .addSource(
            "/** @constructor */function foo() {}\n"
                + "/** @type {!Number} */ var bar = new foo();\n")
        .addDiagnostic("initializing variable\n" + "found   : foo\n" + "required: Number")
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
            "/** @param {Element|Object} p */\nfunction foo(p){}\n"
                + "/** @constructor */function Element(){}\n"
                + "/** @type {Element|Object} */var v;\n"
                + "foo(v);\n")
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
            "/** @constructor */function MyType(){}\n"
                + "/** @type {(MyType|Null)} */var foo; foo = null;")
        .run();
  }

  @Test
  public void testTypeCheck25() {
    newTest()
        .addSource(
            "function foo(/** {a: number} */ obj) {};", //
            "foo({b: 'abc'});")
        .addDiagnostic(
            lines(
                "actual parameter 1 of foo does not match formal parameter",
                "found   : {\n  a: (number|undefined),\n  b: string\n}",
                "required: {a: number}",
                "missing : []",
                "mismatch: [a]"))
        .run();
  }

  @Test
  public void testTypeCheck26() {
    newTest()
        .addSource(
            "function foo(/** {a: number} */ obj) {};", //
            "foo({a: 'abc'});")
        .addDiagnostic(
            lines(
                "actual parameter 1 of foo does not match formal parameter",
                "found   : {a: (number|string)}",
                "required: {a: number}",
                "missing : []",
                "mismatch: [a]"))
        .run();
  }

  @Test
  public void testTypeCheck27() {
    newTest().addSource("function foo(/** {a: number} */ obj) {};" + "foo({a: 123});").run();
  }

  @Test
  public void testTypeCheck28() {
    newTest().addSource("function foo(/** ? */ obj) {};" + "foo({a: 123});").run();
  }

  @Test
  public void testPropertyOnATypedefGetsResolved() {
    newTest()
        .addSource(
            "const a = {};",
            "/** @typedef {{prop: string}} */",
            "  a.b;",
            "/** @typedef {string} */",
            "  a.b.c;",
            "const abAlias = a.b;",
            "var /** !abAlias.whatIsThis */ y = 'foo';")
        .addDiagnostic(RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR)
        .run();
  }

  @Test
  public void testPropertyOnTypedefAliasesGetsRecognized() {
    // TODO(b/144187784): Handle making property b.c recognized when referenced via aAlias
    newTest()
        .addSource(
            "const a = {};",
            "/** @typedef {{prop: string}} */",
            "  a.b;",
            "/** @typedef {string} */",
            "  a.b.c;",
            "const aAlias = a;",
            "var /** aAlias.b.c */ x;")
        .addDiagnostic(RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR)
        .run();
  }

  @Test
  public void testPropertyOnTypedefAliasesGetsRecognized2() {
    newTest()
        .addSource(
            "const a = {};",
            "/** @typedef {{prop: string}} */",
            "  a.b;",
            "/** @typedef {string} */",
            "  a.b.c;",
            "const aAlias = a;",
            "var /** aAlias.b */ x = {prop: 'foo'};")
        .run();
  }

  @Test
  public void testTypeCheckInlineReturns() {
    newTest()
        .addSource(
            "function /** string */ foo(x) { return x; }" + "var /** number */ a = foo('abc');")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testTypeCheckDefaultExterns() {
    this.newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            "/** @param {string} x */", //
            "function f(x) {}",
            "f([].length);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testTypeCheckCustomExterns() {
    newTest()
        .addExterns("/** @type {boolean} */ Array.prototype.oogabooga;")
        .includeDefaultExterns()
        .addSource("/** @param {string} x */ function f(x) {}" + "f([].oogabooga);")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: string")
        .run();
  }

  @Test
  public void testTypeCheckCustomExterns2() {
    newTest()
        .addExterns("/** @enum {string} */ var Enum = {FOO: 1, BAR: 1};")
        .includeDefaultExterns()
        .addSource("/** @param {Enum} x */ function f(x) {} f(Enum.FOO); f(true);")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: Enum<string>")
        .run();
  }

  @Test
  public void testDontCrashOnRecursiveTemplateReference() {
    newTest()
        .addSource(
            "/** @constructor @implements {Iterable<VALUE>} @template VALUE */",
            "function Foo() {",
            "  /** @type {!Map<VALUE, VALUE>} */ this.map = new Map;",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testUnionOfFunctionAndType() {
    newTest()
        .addSource(
            "/** @type {null|(function(Number):void)} */ var a;"
                + "/** @type {(function(Number):void)|null} */ var b = null; a = b;")
        .run();
  }

  @Test
  public void testOptionalParameterComparedToUndefined() {
    newTest()
        .addSource(
            "/** @param  {Number} opt_a */function foo(opt_a)"
                + "{if (opt_a==undefined) var b = 3;}")
        .run();
  }

  @Test
  public void testOptionalAllType() {
    newTest()
        .addSource(
            "/** @param {*} opt_x */function f(opt_x) { return opt_x }\n"
                + "/** @type {*} */var y;\n"
                + "f(y);")
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
            "/** @param {!T} opt_x\n@return {undefined} */\n"
                + "function f(opt_x) { return opt_x; }\n"
                + "/** @constructor */var T = function() {};")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (T|undefined)\n" + "required: undefined")
        .run();
  }

  @Test
  public void testOptionalArgFunctionParam() {
    newTest().addSource("/** @param {function(number=)} a */" + "function f(a) {a()};").run();
  }

  @Test
  public void testOptionalArgFunctionParam2() {
    newTest().addSource("/** @param {function(number=)} a */" + "function f(a) {a(3)};").run();
  }

  @Test
  public void testOptionalArgFunctionParam3() {
    newTest()
        .addSource("/** @param {function(number=)} a */" + "function f(a) {a(undefined)};")
        .run();
  }

  @Test
  public void testOptionalArgFunctionParam4() {
    String expectedWarning =
        "Function a: called with 2 argument(s). "
            + "Function requires at least 0 argument(s) and no more than 1 "
            + "argument(s).";

    newTest()
        .addSource("/** @param {function(number=)} a */function f(a) {a(3,4)};")
        .addDiagnostic(expectedWarning)
        .run();
  }

  @Test
  public void testOptionalArgFunctionParamError() {
    String expectedWarning =
        "Bad type annotation. variable length argument must be last." + BAD_TYPE_WIKI_LINK;
    newTest()
        .addSource("/** @param {function(...number, number=)} a */" + "function f(a) {};")
        .addDiagnostic(expectedWarning)
        .run();
  }

  @Test
  public void testOptionalNullableArgFunctionParam() {
    newTest().addSource("/** @param {function(?number=)} a */" + "function f(a) {a()};").run();
  }

  @Test
  public void testOptionalNullableArgFunctionParam2() {
    newTest().addSource("/** @param {function(?number=)} a */" + "function f(a) {a(null)};").run();
  }

  @Test
  public void testOptionalNullableArgFunctionParam3() {
    newTest().addSource("/** @param {function(?number=)} a */" + "function f(a) {a(3)};").run();
  }

  @Test
  public void testOptionalArgFunctionReturn() {
    newTest()
        .addSource(
            "/** @return {function(number=)} */"
                + "function f() { return function(opt_x) { }; };"
                + "f()()")
        .run();
  }

  @Test
  public void testOptionalArgFunctionReturn2() {
    newTest()
        .addSource(
            "/** @return {function(Object=)} */"
                + "function f() { return function(opt_x) { }; };"
                + "f()({})")
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
            "/** @param {boolean} x */", //
            " function assignOr(x) {",
            "   x ||= 'a';",
            " };")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : string",
                "required: boolean"))
        .run();
  }

  @Test
  public void testAssignOrMayOrMayNotAssign() {
    newTest()
        .addSource(
            "/** @param {string} x */", //
            " function assignOr(x) {",
            "   x ||= 5;",
            " };")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testAssignAndCheckRHSValid() {
    newTest()
        .addSource(
            "/** @type {string|undefined} */", //
            "var a; a &&= 0;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : number",
                "required: (string|undefined)"))
        .run();
  }

  @Test
  public void testAssignAndRHSNotExecuted() {
    newTest()
        .addSource(
            "let /** null */", //
            "n = null;",
            "n &&= 'str';")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : string",
                "required: null"))
        .run();
  }

  @Test
  public void testAssignCoalesceNoAssign() {
    newTest()
        .addSource(
            "/**", //
            " * @param {string} x */",
            " function assignCoalesce(x) {",
            "   x ??= 5;",
            " };")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testAssignCoalesceCheckRHSValid() {
    newTest()
        .addSource("/** @type {string|undefined} */ var a; a ??= 0;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : number",
                "required: (string|undefined)"))
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
            "/** @param {string} s\n @return {string} */"
                + "(function(s) { return ((s == 'a') && s) || 'b'; })")
        .run();
  }

  @Test
  public void testBooleanReduction3() {
    newTest()
        .addSource(
            "/** @param {string} s\n @return {string?} */"
                + "(function(s) { return s && null && 3; })")
        .run();
  }

  @Test
  public void testBooleanReduction4() {
    newTest()
        .addSource(
            "/** @param {?Object} x\n @return {?Object} */"
                + "(function(x) { return null || x || null ; })")
        .run();
  }

  @Test
  public void testBooleanReduction4NullishCoalesce() {
    newTest()
        .addSource(
            "/** @param {?Object} x\n @return {!Object} */",
            "(function(x) { return null ?? x ?? {} ; })")
        .run();
  }

  @Test
  public void testBooleanReduction5() {
    newTest()
        .addSource(
            "/**\n"
                + "* @param {Array|string} x\n"
                + "* @return {string?}\n"
                + "*/\n"
                + "var f = function(x) {\n"
                + "if (!x || typeof x == 'string') {\n"
                + "return x;\n"
                + "}\n"
                + "return null;\n"
                + "};")
        .run();
  }

  @Test
  public void testBooleanReduction6() {
    newTest()
        .addSource(
            "/**\n"
                + "* @param {Array|string|null} x\n"
                + "* @return {string?}\n"
                + "*/\n"
                + "var f = function(x) {\n"
                + "if (!(x && typeof x != 'string')) {\n"
                + "return x;\n"
                + "}\n"
                + "return null;\n"
                + "};")
        .run();
  }

  @Test
  public void testBooleanReduction7() {
    newTest()
        .addSource(
            "/** @constructor */var T = function() {};\n"
                + "/**\n"
                + "* @param {Array|T} x\n"
                + "* @return {null}\n"
                + "*/\n"
                + "var f = function(x) {\n"
                + "if (!x) {\n"
                + "return x;\n"
                + "}\n"
                + "return null;\n"
                + "};")
        .run();
  }

  @Test
  public void testNullishCoalesceWithAndExpressions() {
    // `(s && undefined)` = `undefined` because `s` is typed as `!Array`, triggering RHS
    // `(s && "a")` = `"a"` because `s` is typed as `!Array`
    newTest()
        .addSource(
            "/** @param {!Array} s",
            "    @return {string} */",
            "(function(s) { return (s && undefined) ?? (s && \"a\"); })")
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
            "/**",
            "* @param {string|null} x",
            "* @return {string|number}",
            "*/",
            "var f = function(x) {",
            "return x ?? 3;",
            "};")
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
            "/**",
            " * @param {?Object=} optionalNullableObject",
            " * @return {!Object}",
            " */",
            "function f(optionalNullableObject = undefined) {",
            " return optionalNullableObject ?? {};", // always returns !Object type
            "};")
        .run();
  }

  @Test
  public void testNullishCoalesceUseNonNullFunction() {
    // if we are trying to evaluate the LHS then x must be null and useNonNull only takes non null
    // objects as inputs
    newTest()
        .addSource(
            "/**",
            " * @param {!Object} x",
            " * @return {!Object}",
            " */",
            " function useNonNull(x) {",
            "   return x;",
            " };",
            "/** @type {?Object} */ var x;",
            "x ?? useNonNull(x);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of useNonNull does not match formal parameter",
                "found   : null",
                "required: Object"))
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
        .addSource("/** @type {null} */var x;\n" + "/** @type {number} */var r = x && x;")
        .addDiagnostic("initializing variable\n" + "found   : null\n" + "required: number")
        .run();
  }

  @Test
  public void testNullOr() {
    newTest()
        .addSource("/** @type {null} */var x;\n" + "/** @type {number} */var r = x || x;")
        .addDiagnostic("initializing variable\n" + "found   : null\n" + "required: number")
        .run();
  }

  @Test
  public void testBooleanPreservation1() {
    newTest()
        .addSource("/**@type {string} */var x = \"a\";" + "x = ((x == \"a\") && x) || x == \"b\";")
        .addDiagnostic("assignment\n" + "found   : (boolean|string)\n" + "required: string")
        .run();
  }

  @Test
  public void testBooleanPreservation2() {
    newTest()
        .addSource("/**@type {string} */var x = \"a\"; x = (x == \"a\") || x;")
        .addDiagnostic("assignment\n" + "found   : (boolean|string)\n" + "required: string")
        .run();
  }

  @Test
  public void testBooleanPreservation3() {
    newTest()
        .addSource(
            "/** @param {Function?} x\n @return {boolean?} */"
                + "function f(x) { return x && x == \"a\"; }")
        .addDiagnostic(
            "condition always evaluates to false\n" + "left : Function\n" + "right: string")
        .run();
  }

  @Test
  public void testBooleanPreservation4() {
    newTest()
        .addSource(
            "/** @param {Function?|boolean} x\n @return {boolean} */"
                + "function f(x) { return x && x == \"a\"; }")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (boolean|null)\n" + "required: boolean")
        .run();
  }

  @Test
  public void testWellKnownSymbolAccess1() {
    newTest()
        .addSource(
            "/**",
            " * @param {Array<string>} x",
            " */",
            "function f(x) {",
            "  const iter = x[Symbol.iterator]();",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testWellKnownSymbolAccess2() {
    newTest()
        .addSource(
            "/**",
            " * @param {IObject<string, number>} x",
            " */",
            "function f(x) {",
            "  const iter = x[Symbol.iterator]();",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testSymbolComparison1() {
    newTest()
        .addSource(
            "/**",
            " * @param {symbol} x",
            " * @param {symbol} y",
            "*/",
            "function f(x, y) { return x === y; }")
        .run();
  }

  @Test
  public void testSymbolComparison2() {
    newTest()
        .addSource(
            "/**",
            " * @param {symbol} x",
            " * @param {symbol} y",
            "*/",
            "function f(x, y) { return x == y; }")
        .run();
  }

  @Test
  public void testSymbolComparison3() {
    newTest()
        .addSource(
            "/**",
            " * @param {symbol} x", // primitive
            " * @param {!Symbol} y", // object
            "*/",
            "function f(x, y) { return x == y; }")
        .run();
  }

  @Test
  public void testSymbolComparison4() {
    newTest()
        .addSource(
            "/**",
            " * @param {symbol} x", // primitive
            " * @param {!Symbol} y", // object
            "*/",
            "function f(x, y) { return x === y; }")
        .addDiagnostic(
            "condition always evaluates to false\n" + "left : symbol\n" + "right: Symbol")
        .run();
  }

  @Test
  public void testSymbolComparison5() {
    newTest()
        .addSource(
            "/**",
            " * @param {symbol} x", // primitive
            " * @param {(!Symbol|null)} y", // object
            "*/",
            "function f(x, y) { return x == y; }")
        .run();
  }

  @Test
  public void testSymbolComparison6() {
    newTest()
        .addSource(
            "/**",
            " * @param {symbol} x", // primitive
            " * @param {(!Symbol|null)} y", // object
            "*/",
            "function f(x, y) { return x === y; }")
        .addDiagnostic(
            "condition always evaluates to false\n" + "left : symbol\n" + "right: (Symbol|null)")
        .run();
  }

  @Test
  public void testSymbolComparison7() {
    newTest()
        .addSource(
            "/**",
            " * @param {symbol} x",
            " * @param {*} y",
            "*/",
            "function f(x, y) { return x == y; }")
        .run();
  }

  @Test
  public void testSymbolComparison8() {
    newTest()
        .addSource(
            "/**",
            " * @param {symbol} x",
            " * @param {*} y",
            "*/",
            "function f(x, y) { return x === y; }")
        .run();
  }

  @Test
  public void testSymbolComparison9() {
    newTest()
        .addSource(
            "/** @enum {symbol} */ var E = {A:Symbol()};",
            "/**",
            " * @param {symbol} x",
            " * @param {E} y",
            "*/",
            "function f(x, y) { return x == y; }")
        .run();
  }

  @Test
  public void testSymbolComparison10() {
    newTest()
        .addSource(
            "/** @enum {symbol} */ var E = {A:Symbol()};",
            "/**",
            " * @param {symbol} x",
            " * @param {E} y",
            "*/",
            "function f(x, y) { return x === y; }")
        .run();
  }

  @Test
  public void testSymbolComparison11() {
    newTest()
        .addSource(
            "/** @enum {!Symbol} */ var E = {A:/** @type {!Symbol} */ (Object(Symbol()))};",
            "/**",
            " * @param {symbol} x",
            " * @param {E} y",
            "*/",
            "function f(x, y) { return x == y; }")
        .run();
  }

  @Test
  public void testSymbolComparison12() {
    newTest()
        .addSource(
            "/** @enum {!Symbol} */ var E = {A:/** @type {!Symbol} */ (Object(Symbol()))};",
            "/**",
            " * @param {symbol} x",
            " * @param {E} y",
            "*/",
            "function f(x, y) { return x === y; }")
        .addDiagnostic(
            "condition always evaluates to false\n" + "left : symbol\n" + "right: E<Symbol>")
        .run();
  }

  @Test
  public void testSymbolComparison13() {
    newTest()
        .addSource(
            "/**",
            " * @param {symbol} x",
            " * @param {!Object} y",
            "*/",
            "function f(x, y) { return x == y; }")
        .run();
  }

  @Test
  public void testSymbolComparison14() {
    newTest()
        .addSource(
            "/**",
            " * @param {symbol} x",
            " * @param {!Object} y",
            "*/",
            "function f(x, y) { return x === y; }")
        .addDiagnostic(
            "condition always evaluates to false\n" + "left : symbol\n" + "right: Object")
        .run();
  }

  @Test
  public void testTypeOfReduction1() {
    newTest()
        .addSource(
            "/** @param {string|number} x\n @return {string} */ "
                + "function f(x) { return typeof x == 'number' ? String(x) : x; }")
        .run();
  }

  @Test
  public void testTypeOfReduction2() {
    newTest()
        .addSource(
            "/** @param {string|number} x\n @return {string} */ "
                + "function f(x) { return typeof x != 'string' ? String(x) : x; }")
        .run();
  }

  @Test
  public void testTypeOfReduction3() {
    newTest()
        .addSource(
            "/** @param {number|null} x\n @return {number} */ "
                + "function f(x) { return typeof x == 'object' ? 1 : x; }")
        .run();
  }

  @Test
  public void testTypeOfReduction4() {
    newTest()
        .addSource(
            "/** @param {Object|undefined} x\n @return {Object} */ "
                + "function f(x) { return typeof x == 'undefined' ? {} : x; }")
        .run();
  }

  @Test
  public void testTypeOfReduction5() {
    newTest()
        .addSource(
            "/** @enum {string} */ var E = {A: 'a', B: 'b'};\n"
                + "/** @param {!E|number} x\n @return {string} */ "
                + "function f(x) { return typeof x != 'number' ? x : 'a'; }")
        .run();
  }

  @Test
  public void testTypeOfReduction6() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            "/**",
            " * @param {number|string} x",
            " * @return {string}",
            " */",
            "function f(x) {",
            "  return typeof x == 'string' && x.length == 3 ? x : 'a';",
            "}")
        .run();
  }

  @Test
  public void testTypeOfReduction7() {
    newTest()
        .addSource(
            "/** @return {string} */var f = function(x) { "
                + "return typeof x == 'number' ? x : 'a'; }")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (number|string)\n" + "required: string")
        .run();
  }

  @Test
  public void testTypeOfReduction11() {
    testClosureTypes(
        "/** @param {Array|string} x\n@return {Array} */\n"
            + "function f(x) {\n"
            + "return goog.isObject(x) ? x : [];\n"
            + "}",
        null);
  }

  @Test
  public void testTypeOfReduction12() {
    newTest()
        .addSource(
            "/** @enum {string} */ var E = {A: 'a', B: 'b'};\n"
                + "/** @param {E|Array} x\n @return {Array} */ "
                + "function f(x) { return typeof x == 'object' ? x : []; }")
        .run();
  }

  @Test
  public void testTypeOfReduction13() {
    testClosureTypes(
        "/** @enum {string} */ var E = {A: 'a', B: 'b'};\n"
            + "/** @param {E|Array} x\n@return {Array} */ "
            + "function f(x) { return goog.isObject(x) ? x : []; }",
        null);
  }

  @Test
  public void testTypeOfReduction15() {
    // Don't do type inference on GETELEMs.
    testClosureTypes(
        "function f(x) { " + "  return typeof arguments[0] == 'string' ? arguments[0] : 0;" + "}",
        null);
  }

  @Test
  public void testTypeOfReduction16() {
    testClosureTypes(
        "/** @interface */ function I() {}\n"
            + "/**\n"
            + " * @param {*} x\n"
            + " * @return {I}\n"
            + " */\n"
            + "function f(x) { "
            + "  if(goog.isObject(x)) {"
            + "    return /** @type {I} */(x);"
            + "  }"
            + "  return null;"
            + "}",
        null);
  }

  @Test
  public void testQualifiedNameReduction1() {
    newTest()
        .addSource(
            "var x = {}; /** @type {string?} */ x.a = 'a';\n"
                + "/** @return {string} */ var f = function() {\n"
                + "return x.a ? x.a : 'a'; }")
        .run();
  }

  @Test
  public void testQualifiedNameReduction2() {
    newTest()
        .addSource(
            "/** @param {string?} a\n@constructor */ var T = "
                + "function(a) {this.a = a};\n"
                + "/** @return {string} */ T.prototype.f = function() {\n"
                + "return this.a ? this.a : 'a'; }")
        .run();
  }

  @Test
  public void testQualifiedNameReduction3() {
    newTest()
        .addSource(
            "/** @param {string|Array} a\n@constructor */ var T = "
                + "function(a) {this.a = a};\n"
                + "/** @return {string} */ T.prototype.f = function() {\n"
                + "return typeof this.a == 'string' ? this.a : 'a'; }")
        .run();
  }

  @Test
  public void testQualifiedNameReduction5a() {
    newTest()
        .addSource(
            "var x = {/** @type {string} */ a:'b' };\n"
                + "/** @return {string} */ var f = function() {\n"
                + "return x.a; }")
        .run();
  }

  @Test
  public void testQualifiedNameReduction5b() {
    newTest()
        .addSource(
            "var x = {/** @type {number} */ a:12 };\n"
                + "/** @return {string} */\n"
                + "var f = function() {\n"
                + "  return x.a;\n"
                + "}")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testQualifiedNameReduction5c() {
    newTest()
        .addSource(
            "/** @return {string} */ var f = function() {\n"
                + "var x = {/** @type {number} */ a:0 };\n"
                + "return (x.a) ? (x.a) : 'a'; }")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (number|string)\n" + "required: string")
        .run();
  }

  @Test
  public void testQualifiedNameReduction6() {
    newTest()
        .addSource(
            "/** @return {string} */ var f = function() {\n"
                + "var x = {/** @return {string?} */ get a() {return 'a'}};\n"
                + "return x.a ? x.a : 'a'; }")
        .run();
  }

  @Test
  public void testQualifiedNameReduction7() {
    newTest()
        .addSource(
            "/** @return {string} */ var f = function() {\n"
                + "var x = {/** @return {number} */ get a() {return 12}};\n"
                + "return x.a; }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testQualifiedNameReduction7a() {
    // It would be nice to find a way to make this an error.
    newTest()
        .addSource(
            "/** @return {string} */ var f = function() {\n"
                + "var x = {get a() {return 12}};\n"
                + "return x.a; }")
        .run();
  }

  @Test
  public void testQualifiedNameReduction8() {
    newTest()
        .addSource(
            "/** @return {string} */ var f = function() {\n"
                + "var x = {get a() {return 'a'}};\n"
                + "return x.a ? x.a : 'a'; }")
        .run();
  }

  @Test
  public void testQualifiedNameReduction9() {
    newTest()
        .addSource(
            "/** @return {string} */ var f = function() {\n"
                + "var x = { /** @param {string} b */ set a(b) {}};\n"
                + "return x.a ? x.a : 'a'; }")
        .run();
  }

  @Test
  public void testQualifiedNameReduction10() {
    // TODO(johnlenz): separate setter property types from getter property
    // types.
    newTest()
        .addSource(
            "/** @return {string} */ var f = function() {\n"
                + "var x = { /** @param {number} b */ set a(b) {}};\n"
                + "return x.a ? x.a : 'a'; }")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (number|string)\n" + "required: string")
        .run();
  }

  @Test
  public void testUnknownsDontOverrideDeclaredTypesInLocalScope1() {
    newTest()
        .addSource(
            "/** @constructor */ var C = function() {\n"
                + "  /** @type {string} */ this.a = 'str'};\n"
                + "/** @param {?} a\n @return {number} */\n"
                + "C.prototype.f = function(a) {\n"
                + "  this.a = a;\n"
                + "  return this.a;\n"
                + "}\n")
        .addDiagnostic("inconsistent return type\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testUnknownsDontOverrideDeclaredTypesInLocalScope2() {
    newTest()
        .addSource(
            "/** @constructor */ var C = function() {\n"
                + "  /** @type {string} */ this.a = 'str';\n"
                + "};\n"
                + "/** @type {C} */ var x = new C();"
                + "/** @param {?} a\n @return {number} */\n"
                + "C.prototype.f = function(a) {\n"
                + "  x.a = a;\n"
                + "  return x.a;\n"
                + "}\n")
        .addDiagnostic("inconsistent return type\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testObjLitDef1a() {
    newTest()
        .addSource("var x = {/** @type {number} */ a:12 };\n" + "x.a = 'a';")
        .addDiagnostic(
            "assignment to property a of x\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testObjLitDef1b() {
    newTest()
        .addSource(
            "function f(){"
                + "var x = {/** @type {number} */ a:12 };\n"
                + "x.a = 'a';"
                + "};\n"
                + "f();")
        .addDiagnostic(
            "assignment to property a of x\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testObjLitDef2a() {
    newTest()
        .addSource("var x = {/** @param {number} b */ set a(b){} };\n" + "x.a = 'a';")
        .addDiagnostic(
            "assignment to property a of x\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testObjLitDef2b() {
    newTest()
        .addSource(
            "function f(){"
                + "var x = {/** @param {number} b */ set a(b){} };\n"
                + "x.a = 'a';"
                + "};\n"
                + "f();")
        .addDiagnostic(
            "assignment to property a of x\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testObjLitDef3a() {
    newTest()
        .addSource(
            "/** @type {string} */ var y;\n"
                + "var x = {/** @return {number} */ get a(){} };\n"
                + "y = x.a;")
        .addDiagnostic("assignment\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testObjLitDef3b() {
    newTest()
        .addSource(
            "/** @type {string} */ var y;\n"
                + "function f(){"
                + "var x = {/** @return {number} */ get a(){} };\n"
                + "y = x.a;"
                + "};\n"
                + "f();")
        .addDiagnostic("assignment\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testObjLitDef4() {
    newTest()
        .addSource("var x = {" + "/** @return {number} */ a:12 };\n")
        .addDiagnostic(
            "assignment to property a of {a: function(): number}\n"
                + "found   : number\n"
                + "required: function(): number")
        .run();
  }

  @Test
  public void testObjLitDef5() {
    newTest()
        .addSource("var x = {};\n" + "/** @return {number} */ x.a = 12;\n")
        .addDiagnostic(
            "assignment to property a of x\n"
                + "found   : number\n"
                + "required: function(): number")
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
            "/** @constructor */ var T = function() {};\n"
                + "/** @param {T|string} x\n@return {T} */\n"
                + "var f = function(x) {\n"
                + "if (x instanceof T) { return x; } else { return new T(); }\n"
                + "};")
        .run();
  }

  @Test
  public void testInstanceOfReduction2() {
    newTest()
        .addSource(
            "/** @constructor */ var T = function() {};\n"
                + "/** @param {!T|string} x\n@return {string} */\n"
                + "var f = function(x) {\n"
                + "if (x instanceof T) { return ''; } else { return x; }\n"
                + "};")
        .run();
  }

  @Test
  public void testUndeclaredGlobalProperty1() {
    newTest()
        .addSource(
            "/** @const */ var x = {}; x.y = null;"
                + "function f(a) { x.y = a; }"
                + "/** @param {string} a */ function g(a) { }"
                + "function h() { g(x.y); }")
        .run();
  }

  @Test
  public void testUndeclaredGlobalProperty2() {
    newTest()
        .addSource(
            "/** @const */ var x = {}; x.y = null;"
                + "function f() { x.y = 3; }"
                + "/** @param {string} a */ function g(a) { }"
                + "function h() { g(x.y); }")
        .addDiagnostic(
            "actual parameter 1 of g does not match formal parameter\n"
                + "found   : (null|number)\n"
                + "required: string")
        .run();
  }

  @Test
  public void testLocallyInferredGlobalProperty1() {
    // We used to have a bug where x.y.z leaked from f into h.
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "/** @type {number} */ F.prototype.z;"
                + "/** @const */ var x = {}; /** @type {F} */ x.y;"
                + "function f() { x.y.z = 'abc'; }"
                + "/** @param {number} x */ function g(x) {}"
                + "function h() { g(x.y.z); }")
        .addDiagnostic(
            "assignment to property z of F\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testPropertyInferredPropagation() {
    // Checking sloppy property check behavior
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @return {Object} */",
            "function f() { return {}; }",
            "function g() { var x = f(); if (x.p) x.a = 'a'; else x.a = 'b'; }",
            "function h() { var x = f(); x.a = false; }")
        .run();
  }

  @Test
  public void testPropertyInference1() {
    newTest()
        .addSource(
            "/** @constructor */ function F() { this.x_ = true; }"
                + "/** @return {string} */"
                + "F.prototype.bar = function() { if (this.x_) return this.x_; };")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: string")
        .run();
  }

  @Test
  public void testPropertyInference2() {
    newTest()
        .addSource(
            "/** @constructor */ function F() { this.x_ = true; }"
                + "F.prototype.baz = function() { this.x_ = null; };"
                + "/** @return {string} */"
                + "F.prototype.bar = function() { if (this.x_) return this.x_; };")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: string")
        .run();
  }

  @Test
  public void testPropertyInference3() {
    newTest()
        .addSource(
            "/** @constructor */ function F() { this.x_ = true; }"
                + "F.prototype.baz = function() { this.x_ = 3; };"
                + "/** @return {string} */"
                + "F.prototype.bar = function() { if (this.x_) return this.x_; };")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (boolean|number)\n" + "required: string")
        .run();
  }

  @Test
  public void testPropertyInference4() {
    newTest()
        .addSource(
            "/** @constructor */ function F() { }"
                + "F.prototype.x_ = 3;"
                + "/** @return {string} */"
                + "F.prototype.bar = function() { if (this.x_) return this.x_; };")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testPropertyInference5() {
    disableStrictMissingPropertyChecks();
    // "x_" is a known property of an unknown type.
    newTest()
        .addSource(
            "/** @constructor */ function F() { }"
                + "F.prototype.baz = function() { this.x_ = 3; };"
                + "/** @return {string} */"
                + "F.prototype.bar = function() { if (this.x_) return this.x_; };")
        .run();
  }

  @Test
  public void testPropertyInference6() {
    // "x_" is a known property of an unknown type.
    newTest()
        .addSource(
            "/** @constructor */ function F() { }",
            "(new F).x_ = 3;",
            "/** @return {string} */",
            "F.prototype.bar = function() { return this.x_; };")
        .addDiagnostic("Property x_ never defined on F") // definition
        .addDiagnostic("Property x_ never defined on F") // reference
        .run();
  }

  @Test
  public void testPropertyInference7() {
    newTest()
        .addSource(
            "/** @constructor */ function F() { this.x_ = true; }"
                + "(new F).x_ = 3;"
                + "/** @return {string} */"
                + "F.prototype.bar = function() { return this.x_; };")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: string")
        .run();
  }

  @Test
  public void testPropertyInference8() {
    newTest()
        .addSource(
            "/** @constructor */ function F() { "
                + "  /** @type {string} */ this.x_ = 'x';"
                + "}"
                + "(new F).x_ = 3;"
                + "/** @return {string} */"
                + "F.prototype.bar = function() { return this.x_; };")
        .addDiagnostic(
            "assignment to property x_ of F\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testPropertyInference9() {
    newTest()
        .addSource(
            "/** @constructor */ function A() {}"
                + "/** @return {function(): ?} */ function f() { "
                + "  return function() {};"
                + "}"
                + "var g = f();"
                + "/** @type {number} */ g.prototype.bar_ = null;")
        .addDiagnostic("assignment\n" + "found   : null\n" + "required: number")
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
            "/** @constructor */ function A() {}"
                + "/** @return {function(): ?} */ function f() { "
                + "  return function() {};"
                + "}"
                + "var g = f();"
                + "/** @type {number} */ g.prototype.bar_ = 1;"
                + "var h = f();"
                + "/** @type {string} */ h.prototype.bar_ = 1;")
        .addDiagnostic("assignment\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testNoPersistentTypeInferenceForObjectProperties() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {Object} o\n@param {string} x */\n"
                + "function s1(o,x) { o.x = x; }\n"
                + "/** @param {Object} o\n@return {string} */\n"
                + "function g1(o) { return typeof o.x == 'undefined' ? '' : o.x; }\n"
                + "/** @param {Object} o\n@param {number} x */\n"
                + "function s2(o,x) { o.x = x; }\n"
                + "/** @param {Object} o\n@return {number} */\n"
                + "function g2(o) { return typeof o.x == 'undefined' ? 0 : o.x; }")
        .run();
  }

  @Test
  public void testNoPersistentTypeInferenceForFunctionProperties() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {Function} o\n@param {string} x */\n"
                + "function s1(o,x) { o.x = x; }\n"
                + "/** @param {Function} o\n@return {string} */\n"
                + "function g1(o) { return typeof o.x == 'undefined' ? '' : o.x; }\n"
                + "/** @param {Function} o\n@param {number} x */\n"
                + "function s2(o,x) { o.x = x; }\n"
                + "/** @param {Function} o\n@return {number} */\n"
                + "function g2(o) { return typeof o.x == 'undefined' ? 0 : o.x; }")
        .run();
  }

  @Test
  public void testStrictPropertiesOnFunctions2() {
    newTest()
        .addSource(
            "/** @param {Function} o\n @param {string} x */", //
            "function s1(o,x) { o.x = x; }")
        .addDiagnostic("Property x never defined on Function")
        .run();
  }

  @Test
  public void testStrictPropertiesOnEnum1() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            "/** @enum {string} */ var E = {S:'s'};",
            "/** @param {E} e\n @return {string}  */",
            "function s1(e) { return e.slice(1); }")
        .run();
  }

  @Test
  public void testStrictPropertiesOnEnum2() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            "/** @enum {?string} */ var E = {S:'s'};",
            "/** @param {E} e\n @return {string}  */",
            "function s1(e) { return e.slice(1); }")
        .run();
  }

  @Test
  public void testStrictPropertiesOnEnum3() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            "/** @enum {string} */ var E = {S:'s'};",
            "/** @param {?E} e\n @return {string}  */",
            "function s1(e) { return e.slice(1); }")
        .run();
  }

  @Test
  public void testStrictPropertiesOnEnum4() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            "/** @enum {?string} */ var E = {S:'s'};",
            "/** @param {?E} e\n @return {string}  */",
            "function s1(e) { return e.slice(1); }")
        .run();
  }

  @Test
  public void testDestructuring() {
    newTest()
        .addSource(
            "/** @param {{id:(number|undefined)}=} o */",
            "function f(o = {}) {",
            "   const {id} = o;",
            "}")
        .run();
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope1a() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {!Object} o",
            " * @return {string}",
            " */",
            "function f(o) {",
            "  o.x = 1;",
            "  return o.x;",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope1b() {
    // With strict missing properties assigning a unknown property after type
    // definition causes a warning.
    newTest()
        .addSource(
            "/** @param {!Object} o",
            " * @return {string}",
            " */",
            "function f(o) {",
            "  o.x = 1;",
            "}")
        .addDiagnostic("Property x never defined on Object")
        .run();
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope2a() {
    // This test is specifically checking loose property check behavior.
    disableStrictMissingPropertyChecks();

    newTest()
        .addSource(
            "/**",
            "  @param {!Object} o",
            "  @param {number?} x",
            "  @return {string}",
            " */",
            "function f(o, x) {",
            "  o.x = 'a';",
            "  if (x) {o.x = x;}",
            "  return o.x;",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : (number|string)",
                "required: string"))
        .run();
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope2b() {
    newTest()
        .addSource(
            "/**",
            "  @param {!Object} o",
            "  @param {number?} x",
            " */",
            "function f(o, x) {",
            "  o.x = 'a';",
            "}")
        .addDiagnostic("Property x never defined on Object")
        .run();
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope2c() {
    newTest()
        .addSource(
            "/**",
            "  @param {!Object} o",
            "  @param {number?} x",
            " */",
            "function f(o, x) {",
            "  if (x) { o.x = x; }",
            "}")
        .addDiagnostic("Property x never defined on Object")
        .run();
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope3() {
    disableStrictMissingPropertyChecks();

    // inferrence of undeclared properties
    newTest()
        .addSource(
            "/**@param {!Object} o\n@param {number?} x\n@return {string}*/"
                + "function f(o, x) { if (x) {o.x = x;} else {o.x = 'a';}\nreturn o.x; }")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (number|string)\n" + "required: string")
        .run();
  }

  @Test
  public void testMissingPropertyOfUnion1() {
    newTest()
        .addSource(
            "/**",
            "  @param {number|string} obj",
            "  @return {?}",
            " */",
            "function f(obj, x) {",
            "  return obj.foo;",
            "}")
        .addDiagnostic("Property foo never defined on (Number|String)")
        .run();
  }

  @Test
  public void testMissingPropertyOfUnion2() {
    newTest()
        .addSource(
            "/**",
            "  @param {*=} opt_o",
            "  @return {string|null}",
            " */",
            "function f(opt_o) {",
            "  if (opt_o) {",
            "    if (typeof opt_o !== 'string') {",
            "      return opt_o.toString();",
            "    }",
            "  }",
            "  return null; ",
            "}")
        .addDiagnostic(
            "Property toString never defined on *" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run(); // ?
  }

  @Test
  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty1() {
    newTest()
        .addSource(
            "/** @constructor */var T = function() { this.x = ''; };\n"
                + "/** @type {number} */ T.prototype.x = 0;")
        .addDiagnostic(
            "assignment to property x of T\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty2() {
    newTest()
        .addSource(
            "/** @constructor */var T = function() { this.x = ''; };\n"
                + "/** @type {number} */ T.prototype.x;")
        .addDiagnostic(
            "assignment to property x of T\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty3() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @type {Object} */ var n = {};\n"
                + "/** @constructor */ n.T = function() { this.x = ''; };\n"
                + "/** @type {number} */ n.T.prototype.x = 0;")
        .addDiagnostic(
            "assignment to property x of n.T\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty4() {
    newTest()
        .addSource(
            "var n = {};\n"
                + "/** @constructor */ n.T = function() { this.x = ''; };\n"
                + "/** @type {number} */ n.T.prototype.x = 0;")
        .addDiagnostic(
            "assignment to property x of n.T\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testPropertyUsedBeforeDefinition1() {
    newTest()
        .addSource(
            "/** @constructor */ var T = function() {};\n"
                + "/** @return {string} */"
                + "T.prototype.f = function() { return this.g(); };\n"
                + "/** @return {number} */ T.prototype.g = function() { return 1; };\n")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testPropertyUsedBeforeDefinition2() {
    newTest()
        .addSource(
            "var n = {};\n"
                + "/** @constructor */ n.T = function() {};\n"
                + "/** @return {string} */"
                + "n.T.prototype.f = function() { return this.g(); };\n"
                + "/** @return {number} */ n.T.prototype.g = function() { return 1; };\n")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
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
            "/** @type {string} */ var a = 'a';"
                + "/** @type {string} */ var b = 'b';"
                + "/** @type {string} */ var c = a + b;")
        .run();
  }

  @Test
  public void testAdd4() {
    newTest()
        .addSource(
            "/** @type {number} */ var a = 5;"
                + "/** @type {string} */ var b = 'b';"
                + "/** @type {string} */ var c = a + b;")
        .run();
  }

  @Test
  public void testAdd5() {
    newTest()
        .addSource(
            "/** @type {string} */ var a = 'a';"
                + "/** @type {number} */ var b = 5;"
                + "/** @type {string} */ var c = a + b;")
        .run();
  }

  @Test
  public void testAdd6() {
    newTest()
        .addSource(
            "/** @type {number} */ var a = 5;"
                + "/** @type {number} */ var b = 5;"
                + "/** @type {number} */ var c = a + b;")
        .run();
  }

  @Test
  public void testAdd7() {
    newTest()
        .addSource(
            "/** @type {number} */ var a = 5;"
                + "/** @type {string} */ var b = 'b';"
                + "/** @type {number} */ var c = a + b;")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testAdd8() {
    newTest()
        .addSource(
            "/** @type {string} */ var a = 'a';"
                + "/** @type {number} */ var b = 5;"
                + "/** @type {number} */ var c = a + b;")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testAdd9() {
    newTest()
        .addSource(
            "/** @type {number} */ var a = 5;"
                + "/** @type {number} */ var b = 5;"
                + "/** @type {string} */ var c = a + b;")
        .addDiagnostic("initializing variable\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testAdd10() {
    // d.e.f will have unknown type.
    newTest()
        .addSource(
            suppressMissingProperty("e", "f")
                + "/** @type {number} */ var a = 5;"
                + "/** @type {string} */ var c = a + d.e.f;")
        .run();
  }

  @Test
  public void testAdd11() {
    // d.e.f will have unknown type.
    newTest()
        .addSource(
            suppressMissingProperty("e", "f")
                + "/** @type {number} */ var a = 5;"
                + "/** @type {number} */ var c = a + d.e.f;")
        .run();
  }

  @Test
  public void testAdd12() {
    newTest()
        .addSource(
            "/** @return {(number|string)} */ function a() { return 5; }"
                + "/** @type {number} */ var b = 5;"
                + "/** @type {boolean} */ var c = a() + b;")
        .addDiagnostic(
            "initializing variable\n" + "found   : (number|string)\n" + "required: boolean")
        .run();
  }

  @Test
  public void testAdd13() {
    newTest()
        .addSource(
            "/** @type {number} */ var a = 5;"
                + "/** @return {(number|string)} */ function b() { return 5; }"
                + "/** @type {boolean} */ var c = a + b();")
        .addDiagnostic(
            "initializing variable\n" + "found   : (number|string)\n" + "required: boolean")
        .run();
  }

  @Test
  public void testAdd14() {
    newTest()
        .addSource(
            "/** @type {(null|string)} */ var a = unknown;"
                + "/** @type {number} */ var b = 5;"
                + "/** @type {boolean} */ var c = a + b;")
        .addDiagnostic(
            "initializing variable\n" + "found   : (number|string)\n" + "required: boolean")
        .run();
  }

  @Test
  public void testAdd15() {
    newTest()
        .addSource(
            "/** @type {number} */ var a = 5;"
                + "/** @return {(number|string)} */ function b() { return 5; }"
                + "/** @type {boolean} */ var c = a + b();")
        .addDiagnostic(
            "initializing variable\n" + "found   : (number|string)\n" + "required: boolean")
        .run();
  }

  @Test
  public void testAdd16() {
    newTest()
        .addSource(
            "/** @type {(undefined|string)} */ var a = unknown;"
                + "/** @type {number} */ var b = 5;"
                + "/** @type {boolean} */ var c = a + b;")
        .addDiagnostic(
            "initializing variable\n" + "found   : (number|string)\n" + "required: boolean")
        .run();
  }

  @Test
  public void testAdd17() {
    newTest()
        .addSource(
            "/** @type {number} */ var a = 5;"
                + "/** @type {(undefined|string)} */ var b = unknown;"
                + "/** @type {boolean} */ var c = a + b;")
        .addDiagnostic(
            "initializing variable\n" + "found   : (number|string)\n" + "required: boolean")
        .run();
  }

  @Test
  public void testAdd18() {
    newTest()
        .addSource(
            "function f() {};"
                + "/** @type {string} */ var a = 'a';"
                + "/** @type {number} */ var c = a + f();")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testAdd19() {
    newTest()
        .addSource(
            "/** @param {number} opt_x\n@param {number} opt_y\n"
                + "@return {number} */ function f(opt_x, opt_y) {"
                + "return opt_x + opt_y;}")
        .run();
  }

  @Test
  public void testAdd20() {
    newTest()
        .addSource(
            "/** @param {!Number} opt_x\n@param {!Number} opt_y\n"
                + "@return {number} */ function f(opt_x, opt_y) {"
                + "return opt_x + opt_y;}")
        .run();
  }

  @Test
  public void testAdd21() {
    newTest()
        .addSource(
            "/** @param {Number|Boolean} opt_x\n"
                + "@param {number|boolean} opt_y\n"
                + "@return {number} */ function f(opt_x, opt_y) {"
                + "return opt_x + opt_y;}")
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
            lines(
                "left side of numeric comparison", //
                "found   : Object",
                "required: (bigint|number)"))
        .run();
  }

  @Test
  public void testNumericComparison3() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    newTest().addSource("/**@param {string} a*/ function f(a) {return a < 3;}").run();
  }

  @Test
  public void testNumericComparison4() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    newTest()
        .addSource("/**@param {(number|undefined)} a*/ " + "function f(a) {return a < 3;}")
        .run();
  }

  @Test
  public void testNumericComparison5() {
    newTest()
        .addSource("/**@param {*} a*/ function f(a) {return a < 3;}")
        .addDiagnostic(
            lines(
                "left side of numeric comparison", //
                "found   : *",
                "required: (bigint|number)"))
        .run();
  }

  @Test
  public void testNumericComparison6() {
    newTest()
        .addSource("/**@return {void} */ function foo() { if (3 >= foo()) return; }")
        .addDiagnostic(
            lines(
                "right side of numeric comparison",
                "found   : undefined",
                "required: (bigint|number)"))
        .run();
  }

  @Test
  public void testStringComparison1() {
    newTest().addSource("/**@param {string} a*/ function f(a) {return a < 'x';}").run();
  }

  @Test
  public void testStringComparison2() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    newTest().addSource("/**@param {Object} a*/ function f(a) {return a < 'x';}").run();
  }

  @Test
  public void testStringComparison3() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    newTest().addSource("/**@param {number} a*/ function f(a) {return a < 'x';}").run();
  }

  @Test
  public void testStringComparison4() {
    newTest()
        .addSource("/**@param {string|undefined} a*/ " + "function f(a) {return a < 'x';}")
        .run();
  }

  @Test
  public void testStringComparison5() {
    newTest().addSource("/**@param {*} a*/ " + "function f(a) {return a < 'x';}").run();
  }

  @Test
  public void testStringComparison6() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    newTest()
        .addSource("/**@return {void} */ " + "function foo() { if ('a' >= foo()) return; }")
        .addDiagnostic("right side of comparison\n" + "found   : undefined\n" + "required: string")
        .run();
  }

  @Test
  public void testValueOfComparison1() {
    newTest()
        .addExterns(new TestExternsBuilder().addObject().build())
        .addSource(
            "/** @constructor */function O() {};",
            "/**@override*/O.prototype.valueOf = function() { return 1; };",
            "/**@param {!O} a\n@param {!O} b*/ function f(a,b) { return a < b; }")
        .run();
  }

  @Test
  public void testValueOfComparison2() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    newTest()
        .addExterns(new TestExternsBuilder().addObject().build())
        .addSource(
            "/** @constructor */function O() {};",
            "/**@override*/O.prototype.valueOf = function() { return 1; };",
            "/**",
            " * @param {!O} a",
            " * @param {number} b",
            " */",
            "function f(a,b) { return a < b; }")
        .run();
  }

  @Test
  public void testValueOfComparison3() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    newTest()
        .addExterns(new TestExternsBuilder().addObject().build())
        .addSource(
            "/** @constructor */function O() {};",
            "/**@override*/O.prototype.toString = function() { return 'o'; };",
            "/**",
            " * @param {!O} a",
            " * @param {string} b",
            " */",
            "function f(a,b) { return a < b; }")
        .run();
  }

  @Test
  public void testGenericRelationalExpression() {
    newTest()
        .addSource("/**@param {*} a\n@param {*} b*/ " + "function f(a,b) {return a < b;}")
        .run();
  }

  @Test
  public void testInstanceof1() {
    newTest()
        .addSource("function foo(){" + "if (bar instanceof 3)return;}")
        .addDiagnostic(
            "instanceof requires an object\n" + "found   : number\n" + "required: Object")
        .run();
  }

  @Test
  public void testInstanceof2() {
    newTest()
        .addSource("/**@return {void}*/function foo(){" + "if (foo() instanceof Object)return;}")
        .addDiagnostic(
            "deterministic instanceof yields false\n"
                + "found   : undefined\n"
                + "required: NoObject")
        .run();
  }

  @Test
  public void testInstanceof3() {
    newTest()
        .addSource("/**@return {*} */function foo(){" + "if (foo() instanceof Object)return;}")
        .run();
  }

  @Test
  public void testInstanceof4() {
    newTest()
        .addSource(
            "/**@return {(Object|number)} */function foo(){"
                + "if (foo() instanceof Object)return 3;}")
        .run();
  }

  @Test
  public void testInstanceof5() {
    // No warning for unknown types.
    newTest()
        .addSource("/** @return {?} */ function foo(){" + "if (foo() instanceof Object)return;}")
        .run();
  }

  @Test
  public void testInstanceof6() {
    newTest()
        .addSource(
            "/**@return {(Array|number)} */function foo(){"
                + "if (foo() instanceof Object)return 3;}")
        .run();
  }

  @Test
  public void testInstanceOfReduction3() {
    newTest()
        .addSource(
            "/** \n"
                + " * @param {Object} x \n"
                + " * @param {Function} y \n"
                + " * @return {boolean} \n"
                + " */\n"
                + "var f = function(x, y) {\n"
                + "  return x instanceof y;\n"
                + "};")
        .run();
  }

  @Test
  public void testScoping1() {
    newTest()
        .addSource(
            "/**@param {string} a*/function foo(a){"
                + "  /**@param {Array|string} a*/function bar(a){"
                + "    if (a instanceof Array)return;"
                + "  }"
                + "}")
        .run();
  }

  @Test
  public void testScoping2() {
    newTest()
        .addSource(
            "/** @type {number} */ var a;"
                + "function Foo() {"
                + "  /** @type {string} */ var a;"
                + "}")
        .run();
  }

  @Test
  public void testScoping3() {
    newTest()
        .addSource("\n\n/** @type{Number}*/var b;\n/** @type{!String} */var b;")
        .addDiagnostic(
            "variable b redefined with type String, original "
                + "definition at [testcode]:3 with type (Number|null)")
        .run();
  }

  @Test
  public void testScoping4() {
    newTest()
        .addSource("/** @type{Number}*/var b; if (true) /** @type{!String} */var b;")
        .addDiagnostic(
            "variable b redefined with type String, original "
                + "definition at [testcode]:1 with type (Number|null)")
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
        .addSource("/** @constructor */function A() {" + "  /** @type {!A} */this.a = null;" + "}")
        .addDiagnostic("assignment to property a of A\n" + "found   : null\n" + "required: A")
        .run();
  }

  @Test
  public void testScoping8() {
    newTest()
        .addSource(
            "/** @constructor */function A() {}"
                + "/** @constructor */function B() {"
                + "  /** @type {!A} */this.a = null;"
                + "}")
        .addDiagnostic("assignment to property a of B\n" + "found   : null\n" + "required: A")
        .run();
  }

  @Test
  public void testScoping9() {
    newTest()
        .addSource(
            "/** @constructor */function B() {"
                + "  /** @type {!A} */this.a = null;"
                + "}"
                + "/** @constructor */function A() {}")
        .addDiagnostic("assignment to property a of B\n" + "found   : null\n" + "required: A")
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
            "/** @param {{a: number}|{a:number, b:string}} x */",
            "function f(x) {",
            "  var /** null */ n = x.b;",
            "}")
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testDontDropPropertiesInUnion2() {
    newTest()
        .addSource(
            "/** @param {{a:number, b:string}|{a: number}} x */",
            "function f(x) {",
            "  var /** null */ n = x.b;",
            "}")
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testDontDropPropertiesInUnion3() {
    newTest()
        .addSource(
            "/** @param {{a: number}|{a:number, b:string}} x */",
            "function f(x) {}",
            "/** @param {{a: number}} x */",
            "function g(x) { return x.b; }")
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testDontDropPropertiesInUnion4() {
    newTest()
        .addSource(
            "/** @param {{a: number}|{a:number, b:string}} x */",
            "function f(x) {}",
            "/** @param {{c: number}} x */",
            "function g(x) { return x.b; }")
        .addDiagnostic("Property b never defined on x")
        .run();
  }

  @Test
  public void testDontDropPropertiesInUnion5() {
    newTest()
        .addSource(
            "/** @param {{a: number}|{a: number, b: string}} x */",
            "function f(x) {}",
            "f({a: 123});")
        .run();
  }

  @Test
  public void testDontDropPropertiesInUnion6() {
    newTest()
        .addSource(
            "/** @param {{a: number}|{a: number, b: string}} x */",
            "function f(x) {",
            "  var /** null */ n = x;",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : {a: number}",
                "required: null"))
        .run();
  }

  @Test
  public void testDontDropPropertiesInUnion7() {
    // Only a strict warning because in the registry we map {a, c} to {b, d}
    newTest()
        .addSource(
            "/** @param {{a: number}|{a:number, b:string}} x */",
            "function f(x) {}",
            "/** @param {{c: number}|{c:number, d:string}} x */",
            "function g(x) { return x.b; }")
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testScoping11() {
    // named function expressions create a binding in their body only
    // the return is wrong but the assignment is OK since the type of b is ?
    newTest()
        .addSource("/** @return {number} */var a = function b(){ return b };")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : function(): number\n" + "required: number")
        .run();
  }

  @Test
  public void testScoping12() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "/** @type {number} */ F.prototype.bar = 3;"
                + "/** @param {!F} f */ function g(f) {"
                + "  /** @return {string} */"
                + "  function h() {"
                + "    return f.bar;"
                + "  }"
                + "}")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testFunctionArguments1() {
    testFunctionType(
        "/** @param {number} a\n@return {string} */" + "function f(a) {}",
        "function(number): string");
  }

  @Test
  public void testFunctionArguments2() {
    testFunctionType(
        "/** @param {number} opt_a\n@return {string} */" + "function f(opt_a) {}",
        "function(number=): string");
  }

  @Test
  public void testFunctionArguments3() {
    testFunctionType(
        "/** @param {number} b\n@return {string} */" + "function f(a,b) {}",
        "function(?, number): string");
  }

  @Test
  public void testFunctionArguments4() {
    testFunctionType(
        "/** @param {number} opt_a\n@return {string} */" + "function f(a,opt_a) {}",
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
            "/** @param {number} opt_a\n@return {string} */" + "function a(a,opt_a,var_args) {}")
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
            "/** @return {boolean} */ function u() { return true; }"
                + "/** @param {boolean} b\n@return {?boolean} */"
                + "function f(b) { if (u()) { b = null; } return b; }")
        .addDiagnostic("assignment\n" + "found   : null\n" + "required: boolean")
        .run();
  }

  @Test
  public void testFunctionArguments14() {
    newTest()
        .addSource(
            "/**\n"
                + " * @param {string} x\n"
                + " * @param {number} opt_y\n"
                + " * @param {boolean} var_args\n"
                + " */ function f(x, opt_y, var_args) {}"
                + "f('3'); f('3', 2); f('3', 2, true); f('3', 2, true, false);")
        .run();
  }

  @Test
  public void testFunctionArguments15() {
    newTest()
        .addSource("/** @param {?function(*)} f */" + "function g(f) { f(1, 2); }")
        .addDiagnostic(
            "Function f: called with 2 argument(s). "
                + "Function requires at least 1 argument(s) "
                + "and no more than 1 argument(s).")
        .run();
  }

  @Test
  public void testFunctionArguments16() {
    newTest()
        .addSource(
            "/** @param {...number} var_args */", //
            "function g(var_args) {}",
            "g(1, true);")
        .addDiagnostic(
            lines(
                "actual parameter 2 of g does not match formal parameter",
                "found   : boolean",
                "required: number"))
        .run();
  }

  @Test
  public void testUndefinedPassedForVarArgs() {
    newTest()
        .addSource(
            "/** @param {...number} var_args */", //
            "function g(var_args) {}",
            "g(undefined, 1);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of g does not match formal parameter",
                "found   : undefined",
                "required: number"))
        .run();
  }

  @Test
  public void testFunctionArguments17() {
    newTest()
        .addSource(
            "/** @param {booool|string} x */"
                + "function f(x) { g(x) }"
                + "/** @param {number} x */"
                + "function g(x) {}")
        .addDiagnostic("Bad type annotation. Unknown type booool")
        .run();
  }

  @Test
  public void testFunctionArguments18() {
    newTest()
        .addSource("function f(x) {}" + "f(/** @param {number} y */ (function() {}));")
        .addDiagnostic("parameter y does not appear in <anonymous>'s parameter list")
        .run();
  }

  @Test
  public void testVarArgParameterWithTemplateType() {
    this.newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            "/**",
            " * @template T",
            " * @param {...T} var_args",
            " * @return {T}",
            " */",
            "function firstOf(var_args) { return arguments[0]; }",
            "/** @type {null} */ var a = firstOf('hi', 1);",
            "")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : (number|string)",
                "required: null"))
        .run();
  }

  @Test
  public void testRestParameters() {
    this.newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            "/**",
            " * @param {...number} x",
            " */",
            "function f(...x) {",
            "  var /** string */ s = x[0];",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testRestParameterWithTemplateType() {
    this.newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            "/**",
            " * @template T",
            " * @param {...T} rest",
            " * @return {T}",
            " */",
            "function firstOf(...rest) {",
            "  return rest[0];",
            "}",
            "/** @type {null} */ var a = firstOf('hi', 1);",
            "")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : (number|string)",
                "required: null"))
        .run();
  }

  // Test that when transpiling we don't use T in the body of f; it would cause a spurious
  // unknown-type warning.
  @Test
  public void testRestParametersWithGenericsNoWarning() {
    newTest()
        .addExterns(new TestExternsBuilder().addObject().addArray().build())
        .addSource(
            "/**",
            " * @constructor",
            " * @template T",
            " */",
            "function Foo() {}",
            "/**",
            " * @template T",
            " * @param {...!Foo<T>} x",
            " */",
            "function f(...x) {",
            "  return 123;",
            "}")
        .run();
  }

  @Test
  public void testPrintFunctionName1() {
    // Ensures that the function name is pretty.
    newTest()
        .addSource("var goog = {}; goog.run = function(f) {};" + "goog.run();")
        .addDiagnostic(
            "Function goog.run: called with 0 argument(s). "
                + "Function requires at least 1 argument(s) "
                + "and no more than 1 argument(s).")
        .run();
  }

  @Test
  public void testPrintFunctionName2() {
    newTest()
        .addSource(
            "/** @constructor */ var Foo = function() {}; "
                + "Foo.prototype.run = function(f) {};"
                + "(new Foo).run();")
        .addDiagnostic(
            "Function Foo.prototype.run: called with 0 argument(s). "
                + "Function requires at least 1 argument(s) "
                + "and no more than 1 argument(s).")
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
        "/** @this {Date}\n@return {string} */function f(a) {}", "function(this:Date, ?): string");
  }

  @Test
  public void testFunctionInference6() {
    testFunctionType(
        "/** @this {Date}\n@return {string} */function f(opt_a) {}",
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
        "/** @this {Date}\n@param {boolean} b\n@return {string} */" + "var f = function(a,b) {};",
        "function(this:Date, ?, boolean): string");
  }

  @Test
  public void testFunctionInference11() {
    testFunctionType(
        "var goog = {};" + "/** @return {number}*/goog.f = function(){};",
        "goog.f",
        "function(): number");
  }

  @Test
  public void testFunctionInference12() {
    testFunctionType(
        "var goog = {};" + "goog.f = function(){};", "goog.f", "function(): undefined");
  }

  @Test
  public void testFunctionInference13() {
    testFunctionType(
        "var goog = {};"
            + "/** @constructor */ goog.Foo = function(){};"
            + "/** @param {!goog.Foo} f */function eatFoo(f){};",
        "eatFoo",
        "function(goog.Foo): undefined");
  }

  @Test
  public void testFunctionInference14() {
    testFunctionType(
        "var goog = {};"
            + "/** @constructor */ goog.Foo = function(){};"
            + "/** @return {!goog.Foo} */function eatFoo(){ return new goog.Foo; };",
        "eatFoo",
        "function(): goog.Foo");
  }

  @Test
  public void testFunctionInference15() {
    testFunctionType(
        "/** @constructor */ function f() {};" + "f.prototype.foo = function(){};",
        "f.prototype.foo",
        "function(this:f): undefined");
  }

  @Test
  public void testFunctionInference16() {
    testFunctionType(
        "/** @constructor */ function f() {};" + "f.prototype.foo = function(){};",
        "(new f).foo",
        "function(this:f): undefined");
  }

  @Test
  public void testFunctionInference17() {
    testFunctionType(
        "/** @constructor */ function f() {}"
            + "function abstractMethod() {}"
            + "/** @param {number} x */ f.prototype.foo = abstractMethod;",
        "(new f).foo",
        "function(this:f, number): ?");
  }

  @Test
  public void testFunctionInference18() {
    testFunctionType(
        "var goog = {};" + "/** @this {Date} */ goog.eatWithDate;",
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
        .addSource("var f = function() { throw 'x' };" + "/** @return {boolean} */ var g = f;")
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
            "/** @type {!Function} */ var f = function() { g(this); };"
                + "/** @param {boolean} x */ var g = function(x) {};")
        .run();
  }

  @Test
  public void testFunctionInference23a() {
    // We want to make sure that 'prop' isn't declared on all objects.

    // This test is specifically checking loose property check behavior.
    disableStrictMissingPropertyChecks();

    newTest()
        .addSource(
            "/** @type {!Function} */ var f = function() {",
            "  /** @type {number} */ this.prop = 3;",
            "};",
            "/**",
            " * @param {Object} x",
            " * @return {string}",
            " */ var g = function(x) { return x.prop; };")
        .run();
  }

  @Test
  public void testFunctionInference23b() {
    // We want to make sure that 'prop' isn't declared on all objects.

    newTest()
        .addSource(
            "/** @type {!Function} */ var f = function() {",
            "  /** @type {number} */ this.prop = 3;",
            "};",
            "/**",
            " * @param {Object} x",
            " * @return {string}",
            " */ var g = function(x) { return x.prop; };")
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
            "function f() {"
                + " /** @type {number} */ var x = 3;\n"
                + " function g() { x = null; }"
                + " return x;"
                + "}")
        .addDiagnostic("assignment\n" + "found   : null\n" + "required: number")
        .run();
  }

  @Test
  public void testInnerFunction2() {
    newTest()
        .addSource(
            "/** @return {number} */\n"
                + "function f() {"
                + " var x = null;\n"
                + " function g() { x = 3; }"
                + " g();"
                + " return x;"
                + "}")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (null|number)\n" + "required: number")
        .run();
  }

  @Test
  public void testInnerFunction3() {
    newTest()
        .addSource(
            "var x = null;"
                + "/** @return {number} */\n"
                + "function f() {"
                + " x = 3;\n"
                + " /** @return {number} */\n"
                + " function g() { x = true; return x; }"
                + " return x;"
                + "}")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testInnerFunction4() {
    newTest()
        .addSource(
            "var x = null;"
                + "/** @return {number} */\n"
                + "function f() {"
                + " x = '3';\n"
                + " /** @return {number} */\n"
                + " function g() { x = 3; return x; }"
                + " return x;"
                + "}")
        .addDiagnostic("inconsistent return type\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testInnerFunction5() {
    newTest()
        .addSource(
            "/** @return {number} */\n"
                + "function f() {"
                + " var x = 3;\n"
                + " /** @return {number} */"
                + " function g() { var x = 3;x = true; return x; }"
                + " return x;"
                + "}")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testInnerFunction6NullishCoalesce() {
    testClosureTypes(
        lines(
            "function f() {",
            " var x = null ?? function() {};",
            " function g() { if (goog.isFunction(x)) { x(1); } }",
            " g();",
            "}"),
        "Function x: called with 1 argument(s). "
            + "Function requires at least 0 argument(s) "
            + "and no more than 0 argument(s).");
  }

  @Test
  public void testInnerFunction7NullishCoalesce() {
    testClosureTypes(
        lines(
            "function f() {",
            " /** @type {function()} */",
            " var x = null ?? function() {};",
            " function g() { if (goog.isFunction(x)) { x(1); } }",
            " g();",
            "}"),
        "Function x: called with 1 argument(s). "
            + "Function requires at least 0 argument(s) "
            + "and no more than 0 argument(s).");
  }

  @Test
  public void testInnerFunction8() {
    testClosureTypes(
        "function f() {"
            + " function x() {};\n"
            + " function g() { if (goog.isFunction(x)) { x(1); } }"
            + " g();"
            + "}",
        "Function x: called with 1 argument(s). "
            + "Function requires at least 0 argument(s) "
            + "and no more than 0 argument(s).");
  }

  @Test
  public void testInnerFunction9() {
    newTest()
        .addSource(
            "function f() {"
                + " var x = 3;\n"
                + " function g() { x = null; };\n"
                + " function h() { return x == null; }"
                + " return h();"
                + "}")
        .run();
  }

  @Test
  public void testInnerFunction10() {
    newTest()
        .addSource(
            "function f() {"
                + "  /** @type {?number} */ var x = null;"
                + "  /** @return {string} */"
                + "  function g() {"
                + "    if (!x) {"
                + "      x = 1;"
                + "    }"
                + "    return x;"
                + "  }"
                + "}")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
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
            "function f() {"
                + "  /** @type {?number} */ var x = null;"
                + "  /** @return {number} */"
                + "  function g() {"
                + "    x = 1;"
                + "    h();"
                + "    return x;"
                + "  }"
                + "  function h() {"
                + "    x = null;"
                + "  }"
                + "}")
        .run();
  }

  @Test
  public void testMethodInference1() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "/** @return {number} */ F.prototype.foo = function() { return 3; };"
                + "/** @constructor \n * @extends {F} */ "
                + "function G() {}"
                + "/** @override */ G.prototype.foo = function() { return true; };")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testMethodInference2() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @constructor */ goog.F = function() {};"
                + "/** @return {number} */ goog.F.prototype.foo = "
                + "    function() { return 3; };"
                + "/** @constructor \n * @extends {goog.F} */ "
                + "goog.G = function() {};"
                + "/** @override */ goog.G.prototype.foo = function() { return true; };")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testMethodInference3() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "/** @param {boolean} x \n * @return {number} */ "
                + "F.prototype.foo = function(x) { return 3; };"
                + "/** @constructor \n * @extends {F} */ "
                + "function G() {}"
                + "/** @override */ "
                + "G.prototype.foo = function(x) { return x; };")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testMethodInference4() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "/** @param {boolean} x \n * @return {number} */ "
                + "F.prototype.foo = function(x) { return 3; };"
                + "/** @constructor \n * @extends {F} */ "
                + "function G() {}"
                + "/** @override */ "
                + "G.prototype.foo = function(y) { return y; };")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testMethodInference5() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "/** @param {number} x \n * @return {string} */ "
                + "F.prototype.foo = function(x) { return 'x'; };"
                + "/** @constructor \n * @extends {F} */ "
                + "function G() {}"
                + "/** @type {number} */ G.prototype.num = 3;"
                + "/** @override */ "
                + "G.prototype.foo = function(y) { return this.num + y; };")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testMethodInference6() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "/** @param {number} x */ F.prototype.foo = function(x) { };"
                + "/** @constructor \n * @extends {F} */ "
                + "function G() {}"
                + "/** @override */ G.prototype.foo = function() { };"
                + "(new G()).foo(1);")
        .run();
  }

  @Test
  public void testMethodInference7() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "F.prototype.foo = function() { };"
                + "/** @constructor \n * @extends {F} */ "
                + "function G() {}"
                + "/** @override */ G.prototype.foo = function(x, y) { };")
        .addDiagnostic(
            "mismatch of the foo property type and the type of the property "
                + "it overrides from superclass F\n"
                + "original: function(this:F): undefined\n"
                + "override: function(this:G, ?, ?): undefined")
        .run();
  }

  @Test
  public void testMethodInference8() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "F.prototype.foo = function() { };"
                + "/** @constructor \n * @extends {F} */ "
                + "function G() {}"
                + "/** @override */ "
                + "G.prototype.foo = function(opt_b, var_args) { };"
                + "(new G()).foo(1, 2, 3);")
        .run();
  }

  @Test
  public void testMethodInference9() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "F.prototype.foo = function() { };"
                + "/** @constructor \n * @extends {F} */ "
                + "function G() {}"
                + "/** @override */ "
                + "G.prototype.foo = function(var_args, opt_b) { };")
        .addDiagnostic("variable length argument must be last")
        .run();
  }

  @Test
  public void testStaticMethodDeclaration1() {
    newTest()
        .addSource(
            "/** @constructor */ function F() { F.foo(true); }"
                + "/** @param {number} x */ F.foo = function(x) {};")
        .addDiagnostic(
            "actual parameter 1 of F.foo does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testStaticMethodDeclaration2() {
    newTest()
        .addSource(
            "var goog = goog || {}; function f() { goog.foo(true); }"
                + "/** @param {number} x */ goog.foo = function(x) {};")
        .addDiagnostic(
            "actual parameter 1 of goog.foo does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testStaticMethodDeclaration3() {
    newTest()
        .addSource(
            "var goog = goog || {}; function f() { goog.foo(true); }" + "goog.foo = function() {};")
        .addDiagnostic(
            "Function goog.foo: called with 1 argument(s). Function requires "
                + "at least 0 argument(s) and no more than 0 argument(s).")
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl1() {
    newTest()
        .addSource(
            "var goog = goog || {};"
                + "/** @param {number} x */ goog.foo = function(x) {};"
                + "/** @param {number} x */ goog.foo = function(x) {};")
        .addDiagnostic("variable goog.foo redefined, original definition at [testcode]:1")
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl2() {
    newTest()
        .addSource(
            "var goog = goog || {};"
                + "/** @param {number} x */ goog.foo = function(x) {};"
                + "/** @param {number} x \n * @suppress {duplicate} */ "
                + "goog.foo = function(x) {};")
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl3() {
    newTest()
        .addSource(
            "var goog = goog || {};" + "goog.foo = function(x) {};" + "goog.foo = function(x) {};")
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl4() {
    newTest()
        .addSource(
            "var goog = goog || {};"
                + "/** @type {Function} */ goog.foo = function(x) {};"
                + "goog.foo = function(x) {};")
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl5() {
    newTest()
        .addSource(
            "var goog = goog || {};"
                + "goog.foo = function(x) {};"
                + "/** @return {undefined} */ goog.foo = function(x) {};")
        .addDiagnostic("variable goog.foo redefined, " + "original definition at [testcode]:1")
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl6() {
    // Make sure the CAST node doesn't interfere with the @suppress
    // annotation.
    newTest()
        .addSource(
            "var goog = goog || {};"
                + "goog.foo = function(x) {};"
                + "/**\n"
                + " * @suppress {duplicate}\n"
                + " * @return {undefined}\n"
                + " */\n"
                + "goog.foo = "
                + "   /** @type {!Function} */ (function(x) {});")
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl7() {
    newTest()
        .addSource("/** @type {string} */ var foo;\n" + "var z = function foo() {};\n")
        .addDiagnostic(TypeCheck.FUNCTION_MASKS_VARIABLE)
        .run();
  }

  @Test
  public void testDuplicateStaticMethodDecl8() {
    newTest()
        .addSource(
            "/** @fileoverview @suppress {duplicate} */\n"
                + "/** @type {string} */ var foo;\n"
                + "var z = function foo() {};\n")
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDecl1() {
    newTest()
        .addSource(
            "var goog = goog || {};"
                + "/** @type {Foo} */ goog.foo;"
                + "/** @type {Foo} */ goog.foo;"
                + "/** @constructor */ function Foo() {}")
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDecl2() {
    newTest()
        .addSource(
            "var goog = goog || {};"
                + "/** @type {Foo} */ goog.foo;"
                + "/** @type {Foo} \n * @suppress {duplicate} */ goog.foo;"
                + "/** @constructor */ function Foo() {}")
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDecl3() {
    newTest()
        .addSource(
            "var goog = goog || {};"
                + "/** @type {!Foo} */ goog.foo;"
                + "/** @type {string} */ goog.foo;"
                + "/** @constructor */ function Foo() {}")
        .addDiagnostic(
            "variable goog.foo redefined with type string, "
                + "original definition at [testcode]:1 with type Foo")
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDecl4() {
    testClosureTypesMultipleWarnings(
        "/** @type {!Foo} */ goog.foo;"
            + "/** @type {string} */ goog.foo = 'x';"
            + "/** @constructor */ function Foo() {}",
        ImmutableList.of(
            "assignment to property foo of goog\nfound   : string\nrequired: Foo",
            // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
            "variable goog.foo redefined with type string, "
                + "original definition at testcode:1 with type Foo"));
  }

  @Test
  public void testDuplicateStaticPropertyDecl5() {
    testClosureTypesMultipleWarnings(
        "var goog = goog || {};"
            + "/** @type {!Foo} */ goog.foo;"
            + "/** @type {string}\n * @suppress {duplicate} */ goog.foo = 'x';"
            + "/** @constructor */ function Foo() {}",
        ImmutableList.of(
            "assignment to property foo of goog\n" + "found   : string\n" + "required: Foo"));
  }

  @Test
  public void testDuplicateStaticPropertyDecl6() {
    newTest()
        .addSource(
            "var goog = goog || {};"
                + "/** @type {string} */ goog.foo = 'y';"
                + "/** @type {string}\n * @suppress {duplicate} */ goog.foo = 'x';")
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDecl7() {
    newTest()
        .addSource(
            "var goog = goog || {};"
                + "/** @param {string} x */ goog.foo;"
                + "/** @type {function(string)} */ goog.foo;")
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDecl8() {
    newTest()
        .addSource(
            "var goog = goog || {};"
                + "/** @return {EventCopy} */ goog.foo;"
                + "/** @constructor */ function EventCopy() {}"
                + "/** @return {EventCopy} */ goog.foo;")
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDecl9() {
    newTest()
        .addSource(
            "var goog = goog || {};"
                + "/** @return {EventCopy} */ goog.foo;"
                + "/** @return {EventCopy} */ goog.foo;"
                + "/** @constructor */ function EventCopy() {}")
        .run();
  }

  @Test
  public void testDuplicateStaticPropertyDec20() {
    newTest()
        .addSource(
            "/**\n"
                + " * @fileoverview\n"
                + " * @suppress {duplicate}\n"
                + " */"
                + "var goog = goog || {};"
                + "/** @type {string} */ goog.foo = 'y';"
                + "/** @type {string} */ goog.foo = 'x';")
        .run();
  }

  @Test
  public void testDuplicateLocalVarDecl() {
    testClosureTypesMultipleWarnings(
        "/** @param {number} x */\n" + "function f(x) { /** @type {string} */ var x = ''; }",
        ImmutableList.of(
            // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
            "variable x redefined with type string, original definition"
                + " at testcode:2 with type number",
            "initializing variable\n" + "found   : string\n" + "required: number"));
  }

  @Test
  public void testDuplicateLocalVarDeclSuppressed() {
    // We can't just leave this to VarCheck since otherwise if that warning is suppressed, we'll end
    // up redeclaring it as undefined in the function block, which can cause spurious errors.
    newTest()
        .addSource(
            "/** @suppress {duplicate} */", //
            "function f(x) {",
            "  var x = x;",
            "  x.y = true;",
            "}")
        .run();
  }

  @Test
  public void testShadowBleedingFunctionName() {
    // This is allowed and creates a new binding inside the function shadowing the bled name.
    newTest()
        .addSource(
            "var f = function x() {", //
            "  var x;",
            "  var /** undefined */ y = x;",
            "};")
        .run();
  }

  @Test
  public void testDuplicateInstanceMethod1() {
    // If there's no jsdoc on the methods, then we treat them like
    // any other inferred properties.
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "F.prototype.bar = function() {};"
                + "F.prototype.bar = function() {};")
        .run();
  }

  @Test
  public void testDuplicateInstanceMethod2() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "/** jsdoc */ F.prototype.bar = function() {};"
                + "/** jsdoc */ F.prototype.bar = function() {};")
        .run();
  }

  @Test
  public void testDuplicateInstanceMethod3() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "F.prototype.bar = function() {};"
                + "/** jsdoc */ F.prototype.bar = function() {};")
        .run();
  }

  @Test
  public void testDuplicateInstanceMethod4() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "/** jsdoc */ F.prototype.bar = function() {};"
                + "F.prototype.bar = function() {};")
        .run();
  }

  @Test
  public void testDuplicateInstanceMethod5() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "/** jsdoc \n * @return {number} */ F.prototype.bar = function() {"
                + "  return 3;"
                + "};"
                + "/** jsdoc \n * @suppress {duplicate} */ "
                + "F.prototype.bar = function() { return ''; };")
        .addDiagnostic("inconsistent return type\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testDuplicateInstanceMethod6() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "/** jsdoc \n * @return {number} */ F.prototype.bar = function() {"
                + "  return 3;"
                + "};"
                + "/** jsdoc \n * @return {string} * \n @suppress {duplicate} */ "
                + "F.prototype.bar = function() { return ''; };")
        .addDiagnostic(
            "assignment to property bar of F.prototype\n"
                + "found   : function(this:F): string\n"
                + "required: function(this:F): number")
        .run();
  }

  @Test
  public void testStubFunctionDeclaration1() {
    testFunctionType(
        "/** @constructor */ function f() {};"
            + "/** @param {number} x \n * @param {string} y \n"
            + "  * @return {number} */ f.prototype.foo;",
        "(new f).foo",
        "function(this:f, number, string): number");
  }

  @Test
  public void testStubFunctionDeclaration2() {
    testExternFunctionType(
        // externs
        "/** @constructor */ function f() {};"
            + "/** @constructor \n * @extends {f} */ f.subclass;",
        "f.subclass",
        "function(new:f.subclass): ?");
  }

  @Test
  public void testStubFunctionDeclaration_static_onEs5Class() {
    testFunctionType(
        lines(
            "/** @constructor */ function f() {};", //
            "/** @return {undefined} */ f.foo;"),
        "f.foo",
        "function(): undefined");
  }

  @Test
  public void testStubFunctionDeclaration4() {
    testFunctionType(
        "/** @constructor */ function f() { " + "  /** @return {number} */ this.foo;" + "}",
        "(new f).foo",
        "function(this:f): number");
  }

  @Test
  public void testStubFunctionDeclaration5() {
    testFunctionType(
        lines(
            "/** @constructor */ function f() { ", //
            "  /** @type {Function} */ this.foo;",
            "}"),
        "(new f).foo",
        createNullableType(getNativeFunctionType()).toString());
  }

  @Test
  public void testStubFunctionDeclaration6() {
    testFunctionType(
        lines(
            "/** @constructor */ function f() {} ", //
            "/** @type {Function} */ f.prototype.foo;"),
        "(new f).foo",
        createNullableType(getNativeFunctionType()).toString());
  }

  @Test
  public void testStubFunctionDeclaration7() {
    testFunctionType(
        lines(
            "/** @constructor */ function f() {} ",
            "/** @type {Function} */ f.prototype.foo = function() {};"),
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
            "/** @interface */",
            "function Foo() {}",
            "/** @return {number} */",
            "Foo.prototype.method = function() {};",
            "/**",
            " * @constructor",
            " * @implements {Foo}",
            " */",
            "function Bar() {}",
            "Bar.prototype.method;",
            "var /** null */ n = (new Bar).method();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testStubMethodDeclarationDoesntBlockTypechecking_2() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {}",
            "/** @return {number} */",
            "Foo.prototype.method = function() {};",
            "/**",
            " * @constructor",
            " * @extends {Foo}",
            " */",
            "function Bar() {}",
            "Bar.prototype.method;",
            "var /** null */ n = (new Bar).method();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testStubMethodDeclarationDoesntBlockTypechecking_3() {
    newTest()
        .addSource(
            "/** @interface */",
            "var Foo = function() {};",
            "/** @type {number} */",
            "Foo.prototype.num;",
            "/**",
            " * @constructor",
            " * @implements {Foo}",
            " */",
            "var Bar = function() {};",
            "/** @type {?} */",
            "Bar.prototype.num;",
            "var /** string */ x = (new Bar).num;")
        .run();
  }

  @Test
  public void testNestedFunctionInference1() {
    String nestedAssignOfFooAndBar =
        "/** @constructor */ function f() {};"
            + "f.prototype.foo = f.prototype.bar = function(){};";
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
            "initializing variable\n" + "found   : " + functionType + "\n" + "required: number")
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
            "initializing variable\n" + "found   : " + functionType + "\n" + "required: number")
        .run();
  }

  @Test
  public void testTypeRedefinition() {
    testClosureTypesMultipleWarnings(
        "a={};/**@enum {string}*/ a.A = {ZOR:'b'};" + "/** @constructor */ a.A = function() {}",
        ImmutableList.of(
            // NOTE: "testcode" is the file name used by compiler.parseTestCode(code)
            "variable a.A redefined with type (typeof a.A), "
                + "original definition at testcode:1 with type enum{a.A}",
            lines(
                "assignment to property A of a", //
                "found   : (typeof a.A)",
                "required: enum{a.A}")));
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
            "left side of 'in'\n"
                + "found   : function(new:Date, ?=, ?=, ?=, ?=, ?=, ?=, ?=): string\n"
                + "required: (string|symbol)")
        .run();
  }

  @Test
  public void testIn5() {
    newTest()
        .addSource("'x' in null")
        .addDiagnostic("'in' requires an object\n" + "found   : null\n" + "required: Object")
        .run();
  }

  @Test
  public void testIn6() {
    newTest()
        .addSource("/** @param {number} x */" + "function g(x) {}" + "g(1 in {});")
        .addDiagnostic(
            "actual parameter 1 of g does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testIn7() {
    // Make sure we do inference in the 'in' expression.
    newTest()
        .addSource(
            "/**",
            " * @param {number} x",
            " * @return {number}",
            " */",
            "function g(x) { return 5; }",
            "function f() {",
            "  var x = {};",
            "  x.foo = '3';",
            "  return g(x.foo) in {};",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of g does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testInWithThis_narrowsPropertyType() {
    // TODO(lharker): should we stop doing this narrowing? this code would break under property
    // renaming.
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prototype.method = function() {",
            "  if ('x' in this) {",
            "    return this.x;", // this access is allowed
            "  }",
            "  return this.y;", // this access causes a warning
            "}")
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
            "/** @param {boolean} x */ function f(x) {}" + "for (var k in {}) {" + "  f(k);" + "}")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : string\n"
                + "required: boolean")
        .run();
  }

  @Test
  public void testForIn2() {
    newTest()
        .addSource(
            "/** @param {boolean} x */ function f(x) {}"
                + "/** @enum {string} */ var E = {FOO: 'bar'};"
                + "/** @type {Object<E, string>} */ var obj = {};"
                + "var k = null;"
                + "for (k in obj) {"
                + "  f(k);"
                + "}")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : E<string>\n"
                + "required: boolean")
        .run();
  }

  @Test
  public void testForIn3() {
    newTest()
        .addSource(
            "/** @param {boolean} x */ function f(x) {}"
                + "/** @type {Object<number>} */ var obj = {};"
                + "for (var k in obj) {"
                + "  f(obj[k]);"
                + "}")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : number\n"
                + "required: boolean")
        .run();
  }

  @Test
  public void testForIn4() {
    newTest()
        .addSource(
            "/** @param {boolean} x */ function f(x) {}"
                + "/** @enum {string} */ var E = {FOO: 'bar'};"
                + "/** @type {Object<E, Array>} */ var obj = {};"
                + "for (var k in obj) {"
                + "  f(obj[k]);"
                + "}")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : (Array|null)\n"
                + "required: boolean")
        .run();
  }

  @Test
  public void testForIn5() {
    newTest()
        .addExterns(new TestExternsBuilder().addObject().build())
        .addSource(
            "/** @param {boolean} x */ function f(x) {}",
            "/** @constructor */ var E = function(){};",
            "/** @override */ E.prototype.toString = function() { return ''; };",
            "/** @type {Object<!E, number>} */ var obj = {};",
            "for (var k in obj) {",
            "  f(k);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : string",
                "required: boolean"))
        .run();
  }

  @Test
  public void testForOf1() {
    newTest()
        .addSource(
            "/** @type {!Iterable<number>} */ var it = [1, 2, 3]; for (let x of it) { alert(x); }")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf2() {
    newTest()
        .addSource(
            "/** @param {boolean} x */ function f(x) {}",
            "/** @type {!Iterable<number>} */ var it = [1, 2, 3];",
            "for (let x of it) { f(x); }")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : number",
                "required: boolean"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf3() {
    newTest()
        .addSource(
            "/** @type {?Iterable<number>} */ var it = [1, 2, 3];", //
            "for (let x of it) {}")
        .addDiagnostic(
            lines(
                "Can only iterate over a (non-null) Iterable type",
                "found   : (Iterable<number>|null)",
                "required: Iterable"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf4() {
    newTest()
        .addSource(
            "function f(/** !Iterator<number> */ it) {", //
            "  for (let x of it) {}",
            "}")
        .addDiagnostic(
            lines(
                "Can only iterate over a (non-null) Iterable type",
                "found   : Iterator<number,?,?>",
                "required: Iterable"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf5() {
    // 'string' is an Iterable<string> so it can be used in a for/of.
    newTest()
        .addSource(
            "function f(/** string */ ch, /** string */ str) {", //
            "  for (ch of str) {}",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf6() {
    newTest()
        .addSource(
            "function f(/** !Array<number> */ a) {", //
            "  for (let elem of a) {}",
            "}")
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
            "function f(/** null */ x) {}",
            "(function(/** !Array<string>|!Iterable<number> */ it) {",
            "  for (let elem of it) {",
            "    f(elem);",
            "  }",
            "})(['']);")
        .includeDefaultExterns()
        .addDiagnostic(
            DiagnosticType.error(
                "JSC_TYPE_MISMATCH",
                "actual parameter 1 of f does not match formal parameter\n"
                    + "found   : (number|string)\n"
                    + "required: null at [testcode] line 4 : 6"))
        .run();
  }

  @Test
  public void testUnionIndexAccessStringElem() {
    newTest()
        .addSource(
            "/** @const {!ReadonlyArray<string>|!Array<string>} */ const a = [];"
                + "/** @const {!null} */ const b = a[0];")
        .includeDefaultExterns()
        .addDiagnostic(
            DiagnosticType.error(
                "JSC_TYPE_MISMATCH",
                "initializing variable\n"
                    + "found   : string\n"
                    + "required: None at [testcode] line 1 : 114"))
        .run();
  }

  @Test
  public void testUnionIndexAccessOverlappingElem() {
    newTest()
        .addSource(
            "/** @const {!ReadonlyArray<string|undefined>|!Array<string>} */ const a = [];"
                + "/** @const {!undefined} */ const b = a[0];")
        .includeDefaultExterns()
        .addDiagnostic(
            DiagnosticType.error(
                "JSC_TYPE_MISMATCH",
                "initializing variable\n"
                    + "found   : (string|undefined)\n"
                    + "required: None at [testcode] line 1 : 114"))
        .run();
  }

  @Test
  public void testUnionIndexAccessDisjointElem() {
    newTest()
        .addSource(
            "/** @const {!ReadonlyArray<string|number>|!Array<boolean>} */ const a = [];"
                + "/** @const {!undefined} */ const b = a[0];")
        .includeDefaultExterns()
        .addDiagnostic(
            DiagnosticType.error(
                "JSC_TYPE_MISMATCH",
                "initializing variable\n"
                    + "found   : (boolean|number|string)\n"
                    + "required: None at [testcode] line 1 : 114"))
        .run();
  }

  @Test
  public void testUnionIndexAccessIncompatibleBecomesUnknown() {
    newTest()
        .addSource(
            "/** @const {!ReadonlyArray<string>|number} */ const a = [];"
                + "/** @const {!undefined} */ const b = a[0];")
        .includeDefaultExterns()
        .run();
  }

  // TODO(nicksantos): change this to something that makes sense.
  @Test
  @Ignore
  public void testComparison1() {
    newTest()
        .addSource("/**@type null */var a;" + "/**@type !Date */var b;" + "if (a==b) {}")
        .addDiagnostic("condition always evaluates to false\n" + "left : null\n" + "right: Date")
        .run();
  }

  @Test
  public void testComparison2() {
    newTest()
        .addSource("/**@type {number}*/var a;" + "/**@type {!Date} */var b;" + "if (a!==b) {}")
        .addDiagnostic("condition always evaluates to true\n" + "left : number\n" + "right: Date")
        .run();
  }

  @Test
  public void testComparison3() {
    // Since null == undefined in JavaScript, this code is reasonable.
    newTest().addSource("/** @type {(Object|undefined)} */var a;" + "var b = a == null").run();
  }

  @Test
  public void testComparison4() {
    newTest()
        .addSource(
            "/** @type {(!Object|undefined)} */var a;"
                + "/** @type {!Object} */var b;"
                + "var c = a == b")
        .run();
  }

  @Test
  public void testComparison5() {
    newTest()
        .addSource("/** @type {null} */var a;" + "/** @type {null} */var b;" + "a == b")
        .addDiagnostic("condition always evaluates to true\n" + "left : null\n" + "right: null")
        .run();
  }

  @Test
  public void testComparison6() {
    newTest()
        .addSource("/** @type {null} */var a;" + "/** @type {null} */var b;" + "a != b")
        .addDiagnostic("condition always evaluates to false\n" + "left : null\n" + "right: null")
        .run();
  }

  @Test
  public void testComparison7() {
    newTest()
        .addSource("var a;" + "var b;" + "a == b")
        .addDiagnostic(
            "condition always evaluates to true\n" + "left : undefined\n" + "right: undefined")
        .run();
  }

  @Test
  public void testComparison8() {
    newTest()
        .addSource("/** @type {Array<string>} */ var a = [];" + "a[0] == null || a[1] == undefined")
        .run();
  }

  @Test
  public void testComparison9() {
    newTest()
        .addSource("/** @type {Array<undefined>} */ var a = [];" + "a[0] == null")
        .addDiagnostic(
            "condition always evaluates to true\n" + "left : undefined\n" + "right: null")
        .run();
  }

  @Test
  public void testComparison10() {
    newTest().addSource("/** @type {Array<undefined>} */ var a = [];" + "a[0] === null").run();
  }

  @Test
  public void testComparison11() {
    newTest()
        .addSource("(function(){}) == 'x'")
        .addDiagnostic(
            "condition always evaluates to false\n"
                + "left : function(): undefined\n"
                + "right: string")
        .run();
  }

  @Test
  public void testComparison12() {
    newTest()
        .addSource("(function(){}) == 3")
        .addDiagnostic(
            "condition always evaluates to false\n"
                + "left : function(): undefined\n"
                + "right: number")
        .run();
  }

  @Test
  public void testComparison13() {
    newTest()
        .addSource("(function(){}) == false")
        .addDiagnostic(
            "condition always evaluates to false\n"
                + "left : function(): undefined\n"
                + "right: boolean")
        .run();
  }

  @Test
  public void testComparison14() {
    newTest()
        .addSource(
            "/** @type {function((Array|string), Object): number} */"
                + "function f(x, y) { return x === y; }")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testComparison15() {
    testClosureTypes(
        "/** @constructor */ function F() {}"
            + "/**\n"
            + " * @param {number} x\n"
            + " * @constructor\n"
            + " * @extends {F}\n"
            + " */\n"
            + "function G(x) {}\n"
            + "goog.inherits(G, F);\n"
            + "/**\n"
            + " * @param {number} x\n"
            + " * @constructor\n"
            + " * @extends {G}\n"
            + " */\n"
            + "function H(x) {}\n"
            + "goog.inherits(H, G);\n"
            + "/** @param {G} x */"
            + "function f(x) { return x.constructor === H; }",
        null);
  }

  @Test
  public void testDeleteOperator1() {
    newTest()
        .addSource("var x = {};" + "/** @return {string} */ function f() { return delete x['a']; }")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: string")
        .run();
  }

  @Test
  public void testDeleteOperator2() {
    newTest()
        .addSource(
            "var obj = {};"
                + "/** \n"
                + " * @param {string} x\n"
                + " * @return {Object} */ function f(x) { return obj; }"
                + "/** @param {?number} x */ function g(x) {"
                + "  if (x) { delete f(x)['a']; }"
                + "}")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testEnumStaticMethod1() {
    newTest()
        .addSource(
            "/** @enum */ var Foo = {AAA: 1};"
                + "/** @param {number} x */ Foo.method = function(x) {};"
                + "Foo.method(true);")
        .addDiagnostic(
            "actual parameter 1 of Foo.method does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testEnumStaticMethod2() {
    newTest()
        .addSource(
            "/** @enum */ var Foo = {AAA: 1};"
                + "/** @param {number} x */ Foo.method = function(x) {};"
                + "function f() { Foo.method(true); }")
        .addDiagnostic(
            "actual parameter 1 of Foo.method does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testEnum1() {
    newTest().addSource("/**@enum*/var a={BB:1,CC:2};\n" + "/**@type {a}*/var d;d=a.BB;").run();
  }

  @Test
  public void testEnum3() {
    newTest()
        .addSource("/**@enum*/var a={BB:1,BB:2}")
        .addDiagnostic("variable a.BB redefined, original definition at [testcode]:1")
        .run();
  }

  @Test
  public void testEnum4() {
    newTest()
        .addSource("/**@enum*/var a={BB:'string'}")
        .addDiagnostic(
            "assignment to property BB of enum{a}\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testEnum5() {
    newTest()
        .addSource("/**@enum {?String}*/var a={BB:'string'}")
        .addDiagnostic(
            "assignment to property BB of enum{a}\n"
                + "found   : string\n"
                + "required: (String|null)")
        .run();
  }

  @Test
  public void testEnum6() {
    newTest()
        .addSource("/**@enum*/var a={BB:1,CC:2};\n/**@type {!Array}*/var d;d=a.BB;")
        .addDiagnostic("assignment\n" + "found   : a<number>\n" + "required: Array")
        .run();
  }

  @Test
  public void testEnum7() {
    newTest()
        .addSource("/** @enum */var a={AA:1,BB:2,CC:3};" + "/** @type {a} */var b=a.D;")
        .addDiagnostic("element D does not exist on this enum")
        .run();
  }

  @Test
  public void testEnum8() {
    testClosureTypesMultipleWarnings(
        "/** @enum */var a=8;",
        ImmutableList.of(
            "enum initializer must be an object literal or an enum",
            "initializing variable\n" + "found   : number\n" + "required: enum{a}"));
  }

  @Test
  public void testEnum9() {
    testClosureTypesMultipleWarnings(
        "/** @enum */ goog.a=8;",
        ImmutableList.of(
            "assignment to property a of goog\n" + "found   : number\n" + "required: enum{goog.a}",
            "enum initializer must be an object literal or an enum"));
  }

  @Test
  public void testEnum10() {
    newTest().addSource("/** @enum {number} */" + "goog.K = { A : 3 };").run();
  }

  @Test
  public void testEnum11() {
    newTest().addSource("/** @enum {number} */" + "goog.K = { 502 : 3 };").run();
  }

  @Test
  public void testEnum12() {
    newTest().addSource("/** @enum {number} */ var a = {};" + "/** @enum */ var b = a;").run();
  }

  @Test
  public void testEnum13a() {
    newTest()
        .addSource("/** @enum {number} */ var a = {};" + "/** @enum {string} */ var b = a;")
        .addDiagnostic(
            "incompatible enum element types\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testEnum13b() {
    newTest()
        .addSource(
            "/** @enum {number} */ var a = {};",
            "/** @const */ var ns = {};",
            "/** @enum {string} */ ns.b = a;")
        .addDiagnostic(
            lines(
                "incompatible enum element types", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testEnum14() {
    newTest()
        .addSource(
            "/** @enum {number} */ var a = {FOO:5};" + "/** @enum */ var b = a;" + "var c = b.FOO;")
        .run();
  }

  @Test
  public void testEnum15() {
    newTest()
        .addSource(
            "/** @enum {number} */ var a = {FOO:5};" + "/** @enum */ var b = a;" + "var c = b.BAR;")
        .addDiagnostic("element BAR does not exist on this enum")
        .run();
  }

  @Test
  public void testEnum16() {
    newTest()
        .addSource("var goog = {};" + "/**@enum*/goog .a={BB:1,BB:2}")
        .addDiagnostic("variable goog.a.BB redefined, original definition at [testcode]:1")
        .run();
  }

  @Test
  public void testEnum17() {
    newTest()
        .addSource("var goog = {};" + "/**@enum*/goog.a={BB:'string'}")
        .addDiagnostic(
            "assignment to property BB of enum{goog.a}\n"
                + "found   : string\n"
                + "required: number")
        .run();
  }

  @Test
  public void testEnum18() {
    newTest()
        .addSource(
            "/**@enum*/ var E = {A: 1, B: 2};"
                + "/** @param {!E} x\n@return {number} */\n"
                + "var f = function(x) { return x; };")
        .run();
  }

  @Test
  public void testEnum19() {
    newTest()
        .addSource(
            "/**@enum*/ var E = {A: 1, B: 2};"
                + "/** @param {number} x\n@return {!E} */\n"
                + "var f = function(x) { return x; };")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: E<number>")
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
            "/** @enum {string} */ var E = {A : 'a', B : 'b'};\n"
                + "/** @param {!E} x\n@return {!E} */ function f(x) { return x; }");
    Node nodeX = n.getLastChild().getLastChild().getLastChild().getLastChild();
    JSType typeE = nodeX.getJSType();
    assertThat(typeE.isObject()).isFalse();
    assertThat(typeE.isNullable()).isFalse();
  }

  @Test
  public void testEnum22() {
    newTest()
        .addSource(
            "/**@enum*/ var E = {A: 1, B: 2};"
                + "/** @param {E} x \n* @return {number} */ function f(x) {return x}")
        .run();
  }

  @Test
  public void testEnum23() {
    newTest()
        .addSource(
            "/**@enum*/ var E = {A: 1, B: 2};"
                + "/** @param {E} x \n* @return {string} */ function f(x) {return x}")
        .addDiagnostic("inconsistent return type\n" + "found   : E<number>\n" + "required: string")
        .run();
  }

  @Test
  public void testEnum24() {
    newTest()
        .addSource(
            "/**@enum {?Object} */ var E = {A: {}};"
                + "/** @param {E} x \n* @return {!Object} */ function f(x) {return x}")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : E<(Object|null)>\n" + "required: Object")
        .run();
  }

  @Test
  public void testEnum25() {
    newTest()
        .addSource(
            "/**@enum {!Object} */ var E = {A: {}};"
                + "/** @param {E} x \n* @return {!Object} */ function f(x) {return x}")
        .run();
  }

  @Test
  public void testEnum26() {
    newTest()
        .addSource(
            "var a = {}; /**@enum*/ a.B = {A: 1, B: 2};"
                + "/** @param {a.B} x \n* @return {number} */ function f(x) {return x}")
        .run();
  }

  @Test
  public void testEnum27() {
    // x is unknown
    newTest()
        .addSource("/** @enum */ var A = {B: 1, C: 2}; " + "function f(x) { return A == x; }")
        .run();
  }

  @Test
  public void testEnum28() {
    // x is unknown
    newTest()
        .addSource("/** @enum */ var A = {B: 1, C: 2}; " + "function f(x) { return A.B == x; }")
        .run();
  }

  @Test
  public void testEnum29() {
    newTest()
        .addSource(
            "/** @enum */ var A = {B: 1, C: 2}; "
                + "/** @return {number} */ function f() { return A; }")
        .addDiagnostic("inconsistent return type\n" + "found   : enum{A}\n" + "required: number")
        .run();
  }

  @Test
  public void testEnum30() {
    newTest()
        .addSource(
            "/** @enum */ var A = {B: 1, C: 2}; "
                + "/** @return {number} */ function f() { return A.B; }")
        .run();
  }

  @Test
  public void testEnum31() {
    newTest()
        .addSource(
            "/** @enum */ var A = {B: 1, C: 2}; " + "/** @return {A} */ function f() { return A; }")
        .addDiagnostic("inconsistent return type\n" + "found   : enum{A}\n" + "required: A<number>")
        .run();
  }

  @Test
  public void testEnum32() {
    newTest()
        .addSource(
            "/** @enum */ var A = {B: 1, C: 2}; "
                + "/** @return {A} */ function f() { return A.B; }")
        .run();
  }

  @Test
  public void testEnum34() {
    newTest()
        .addSource(
            "/** @enum */ var A = {B: 1, C: 2}; "
                + "/** @param {number} x */ function f(x) { return x == A.B; }")
        .run();
  }

  @Test
  public void testEnum35() {
    newTest()
        .addSource(
            "var a = a || {}; /** @enum */ a.b = {C: 1, D: 2};"
                + "/** @return {a.b} */ function f() { return a.b.C; }")
        .run();
  }

  @Test
  public void testEnum36() {
    newTest()
        .addSource(
            "var a = a || {}; /** @enum */ a.b = {C: 1, D: 2};"
                + "/** @return {!a.b} */ function f() { return 1; }")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : number\n" + "required: a.b<number>")
        .run();
  }

  @Test
  public void testEnum37() {
    newTest()
        .addSource(
            "var goog = goog || {};"
                + "/** @enum {number} */ goog.a = {};"
                + "/** @enum */ var b = goog.a;")
        .run();
  }

  @Test
  public void testEnum38() {
    newTest()
        .addSource(
            "/** @enum {MyEnum} */ var MyEnum = {};" + "/** @param {MyEnum} x */ function f(x) {}")
        .addDiagnostic("Cycle detected in inheritance chain of type MyEnum")
        .run();
  }

  @Test
  public void testEnum39() {
    newTest()
        .addSource(
            "/** @enum {Number} */ var MyEnum = {FOO: new Number(1)};"
                + "/** @param {MyEnum} x \n * @return {number} */"
                + "function f(x) { return x == MyEnum.FOO && MyEnum.FOO == x; }")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testEnum40() {
    newTest()
        .addSource(
            "/** @enum {Number} */ var MyEnum = {FOO: new Number(1)};"
                + "/** @param {number} x \n * @return {number} */"
                + "function f(x) { return x == MyEnum.FOO && MyEnum.FOO == x; }")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testEnum41() {
    newTest()
        .addSource(
            "/** @enum {number} */ var MyEnum = {/** @const */ FOO: 1};"
                + "/** @return {string} */"
                + "function f() { return MyEnum.FOO; }")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : MyEnum<number>\n" + "required: string")
        .run();
  }

  @Test
  public void testEnum42a() {
    newTest()
        .addSource(
            "/** @param {number} x */ function f(x) {}",
            "/** @enum {Object} */ var MyEnum = {FOO: {a: 1, b: 2}};",
            "f(MyEnum.FOO.a);")
        .addDiagnostic("Property a never defined on MyEnum<Object>")
        .run();
  }

  @Test
  public void testEnum42b() {
    newTest()
        .addSource(
            "/** @param {number} x */ function f(x) {}",
            "/** @enum {!Object} */ var MyEnum = {FOO: {a: 1, b: 2}};",
            "f(MyEnum.FOO.a);")
        .addDiagnostic("Property a never defined on MyEnum<Object>")
        .run();
  }

  @Test
  public void testEnum42c() {
    disableStrictMissingPropertyChecks();

    newTest()
        .addSource(
            "/** @param {number} x */ function f(x) {}",
            "/** @enum {Object} */ var MyEnum = {FOO: {a: 1, b: 2}};",
            "f(MyEnum.FOO.a);")
        .run();
  }

  @Test
  public void testEnum43() {
    newTest()
        .addSource(
            "/** @param {number} x */ function f(x) {}",
            "/** @enum {{a:number, b:number}} */ var MyEnum = {FOO: {a: 1, b: 2}};",
            "f(MyEnum.FOO.a);")
        .run();
  }

  @Test
  public void testEnumDefinedInObjectLiteral() {
    newTest()
        .addSource(
            "var ns = {",
            "  /** @enum {number} */",
            "  Enum: {A: 1, B: 2},",
            "};",
            "/** @param {!ns.Enum} arg */",
            "function f(arg) {}")
        .run();
  }

  @Test
  public void testAliasedEnum1() {
    newTest()
        .addSource(
            "/** @enum */ var YourEnum = {FOO: 3};"
                + "/** @enum */ var MyEnum = YourEnum;"
                + "/** @param {MyEnum} x */ function f(x) {} f(MyEnum.FOO);")
        .run();
  }

  @Test
  public void testAliasedEnum2() {
    newTest()
        .addSource(
            "/** @enum */ var YourEnum = {FOO: 3};"
                + "/** @enum */ var MyEnum = YourEnum;"
                + "/** @param {YourEnum} x */ function f(x) {} f(MyEnum.FOO);")
        .run();
  }

  @Test
  public void testAliasedEnum3() {
    newTest()
        .addSource(
            "/** @enum */ var YourEnum = {FOO: 3};"
                + "/** @enum */ var MyEnum = YourEnum;"
                + "/** @param {MyEnum} x */ function f(x) {} f(YourEnum.FOO);")
        .run();
  }

  @Test
  public void testAliasedEnum4() {
    newTest()
        .addSource(
            "/** @enum */ var YourEnum = {FOO: 3};"
                + "/** @enum */ var MyEnum = YourEnum;"
                + "/** @param {YourEnum} x */ function f(x) {} f(YourEnum.FOO);")
        .run();
  }

  @Test
  public void testAliasedEnum5() {
    newTest()
        .addSource(
            "/** @enum */ var YourEnum = {FOO: 3};"
                + "/** @enum */ var MyEnum = YourEnum;"
                + "/** @param {string} x */ function f(x) {} f(MyEnum.FOO);")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : YourEnum<number>\n"
                + "required: string")
        .run();
  }

  @Test
  public void testAliasedEnum_rhsIsStubDeclaration() {
    newTest()
        .addSource(
            "let YourEnum;", //
            "/** @enum */ const MyEnum = YourEnum;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : undefined",
                "required: enum{MyEnum}"))
        .run();
  }

  @Test
  public void testAliasedEnum_rhsIsNonEnum() {
    newTest()
        .addSource(
            "let YourEnum = 0;", //
            "/** @enum */ const MyEnum = YourEnum;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: enum{MyEnum}"))
        .run();
  }

  @Test
  public void testConstAliasedEnum() {
    newTest()
        .addSource(
            "/** @enum */ var YourEnum = {FOO: 3};",
            "const MyEnum = YourEnum;",
            "/** @param {MyEnum} x */ function f(x) {} f(MyEnum.FOO);")
        .run();
  }

  @Test
  public void testConstAliasedEnum_constJSDoc() {
    newTest()
        .addSource(
            "/** @enum */ var YourEnum = {FOO: 3};",
            "/** @const */ var MyEnum = YourEnum;",
            "/** @param {MyEnum} x */ function f(x) {} f(MyEnum.FOO);")
        .run();
  }

  @Test
  public void testConstAliasedEnum_qnameConstJSDoc() {
    newTest()
        .addSource(
            "/** @enum */ var YourEnum = {FOO: 3};",
            "const ns = {};",
            "/** @const */",
            "ns.MyEnum = YourEnum;",
            "/** @param {ns.MyEnum} x */ function f(x) {} f(ns.MyEnum.FOO);")
        .run();
  }

  @Test
  public void testConstAliasedEnum_inObjectLit() {
    newTest()
        .addSource(
            "/** @enum */ var YourEnum = {FOO: 3};",
            "const ns = {/** @const */ MyEnum: YourEnum}",
            "/** @param {ns.MyEnum} x */ function f(x) {} f(ns.MyEnum.FOO);")
        .run();
  }

  @Test
  public void testConstAliasedEnum_nestedInObjectLit() {
    newTest()
        .addSource(
            "/** @enum */ var YourEnum = {FOO: 3};",
            "const ns = {/** @const */ x: {/** @const */ MyEnum: YourEnum}}",
            "/** @param {ns.x.MyEnum} x */ function f(x) {} f(ns.x.MyEnum.FOO);")
        .run();
  }

  @Test
  public void testBackwardsEnumUse1() {
    newTest()
        .addSource(
            "/** @return {string} */ function f() { return MyEnum.FOO; }"
                + "/** @enum {string} */ var MyEnum = {FOO: 'x'};")
        .run();
  }

  @Test
  public void testBackwardsEnumUse2() {
    newTest()
        .addSource(
            "/** @return {number} */ function f() { return MyEnum.FOO; }"
                + "/** @enum {string} */ var MyEnum = {FOO: 'x'};")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : MyEnum<string>\n" + "required: number")
        .run();
  }

  @Test
  public void testBackwardsEnumUse3() {
    newTest()
        .addSource(
            "/** @return {string} */ function f() { return MyEnum.FOO; }"
                + "/** @enum {string} */ var YourEnum = {FOO: 'x'};"
                + "/** @enum {string} */ var MyEnum = YourEnum;")
        .run();
  }

  @Test
  public void testBackwardsEnumUse4() {
    newTest()
        .addSource(
            "/** @return {number} */ function f() { return MyEnum.FOO; }"
                + "/** @enum {string} */ var YourEnum = {FOO: 'x'};"
                + "/** @enum {string} */ var MyEnum = YourEnum;")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : YourEnum<string>\n" + "required: number")
        .run();
  }

  @Test
  public void testBackwardsEnumUse5() {
    newTest()
        .addSource(
            "/** @return {string} */ function f() { return MyEnum.BAR; }"
                + "/** @enum {string} */ var YourEnum = {FOO: 'x'};"
                + "/** @enum {string} */ var MyEnum = YourEnum;")
        .addDiagnostic("element BAR does not exist on this enum")
        .run();
  }

  @Test
  public void testBackwardsTypedefUse2() {
    newTest()
        .addSource(
            "/** @this {MyTypedef} */ function f() {}"
                + "/** @typedef {!(Date|Array)} */ var MyTypedef;")
        .run();
  }

  @Test
  public void testBackwardsTypedefUse4() {
    newTest()
        .addSource(
            "/** @return {MyTypedef} */ function f() { return null; }"
                + "/** @typedef {string} */ var MyTypedef;")
        .addDiagnostic("inconsistent return type\n" + "found   : null\n" + "required: string")
        .run();
  }

  @Test
  public void testBackwardsTypedefUse6() {
    newTest()
        .addSource(
            "/** @return {goog.MyTypedef} */ function f() { return null; }"
                + "var goog = {};"
                + "/** @typedef {string} */ goog.MyTypedef;")
        .addDiagnostic("inconsistent return type\n" + "found   : null\n" + "required: string")
        .run();
  }

  @Test
  public void testBackwardsTypedefUse7() {
    newTest()
        .addSource(
            "/** @return {goog.MyTypedef} */ function f() { return null; }"
                + "var goog = {};"
                + "/** @typedef {Object} */ goog.MyTypedef;")
        .run();
  }

  @Test
  public void testBackwardsTypedefUse8() {
    // Technically, this isn't quite right, because the JS runtime
    // will coerce null -> the global object. But we'll punt on that for now.
    newTest()
        .addSource(
            "/** @param {!Array} x */ function g(x) {}"
                + "/** @this {goog.MyTypedef} */ function f() { g(this); }"
                + "var goog = {};"
                + "/** @typedef {(Array|null|undefined)} */ goog.MyTypedef;")
        .run();
  }

  @Test
  public void testBackwardsTypedefUse9() {
    newTest()
        .addSource(
            "/** @param {!Array} x */ function g(x) {}",
            "/** @this {goog.MyTypedef} */ function f() { g(this); }",
            "var goog = {};",
            "/** @typedef {(RegExp|null|undefined)} */ goog.MyTypedef;")
        .addDiagnostic(
            lines(
                "actual parameter 1 of g does not match formal parameter",
                "found   : RegExp",
                "required: Array"))
        .run();
  }

  @Test
  public void testBackwardsTypedefUse10() {
    newTest()
        .addSource(
            "/** @param {goog.MyEnum} x */ function g(x) {}"
                + "var goog = {};"
                + "/** @enum {goog.MyTypedef} */ goog.MyEnum = {FOO: 1};"
                + "/** @typedef {number} */ goog.MyTypedef;"
                + "g(1);")
        .addDiagnostic(
            "actual parameter 1 of g does not match formal parameter\n"
                + "found   : number\n"
                + "required: goog.MyEnum<number>")
        .run();
  }

  @Test
  public void testBackwardsConstructor1() {
    newTest()
        .addSource(
            "function f() { (new Foo(true)); }"
                + "/** \n * @constructor \n * @param {number} x */"
                + "var Foo = function(x) {};")
        .addDiagnostic(
            "actual parameter 1 of Foo does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testBackwardsConstructor2() {
    newTest()
        .addSource(
            "function f() { (new Foo(true)); }"
                + "/** \n * @constructor \n * @param {number} x */"
                + "var YourFoo = function(x) {};"
                + "/** \n * @constructor \n * @param {number} x */"
                + "var Foo = YourFoo;")
        .addDiagnostic(
            "actual parameter 1 of Foo does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
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
            "/** @constructor */function base() {}\n"
                + "/** @constructor\n * @extends {base} */function derived() {}\n")
        .run();
  }

  @Test
  public void testGoodExtends2() {
    newTest()
        .addSource(
            "/** @constructor\n * @extends base */function derived() {}\n"
                + "/** @constructor */function base() {}\n")
        .run();
  }

  @Test
  public void testGoodExtends3() {
    newTest()
        .addSource(
            "/** @constructor\n * @extends {Object} */function base() {}\n"
                + "/** @constructor\n * @extends {base} */function derived() {}\n")
        .run();
  }

  @Test
  public void testGoodExtends4() {
    // Ensure that @extends actually sets the base type of a constructor
    // correctly. Because this isn't part of the human-readable Function
    // definition, we need to crawl the prototype chain (eww).
    Node n =
        parseAndTypeCheck(
            "var goog = {};\n"
                + "/** @constructor */goog.Base = function(){};\n"
                + "/** @constructor\n"
                + "  * @extends {goog.Base} */goog.Derived = function(){};\n");
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
            "/** @constructor */function base() {}\n"
                + "/** @extends {base}\n * @constructor */function derived() {}\n")
        .run();
  }

  @Test
  public void testGoodExtends6() {
    testFunctionType(
        CLOSURE_DEFS
            + "/** @constructor */function base() {}\n"
            + "/** @return {number} */ "
            + "  base.prototype.foo = function() { return 1; };\n"
            + "/** @extends {base}\n * @constructor */function derived() {}\n"
            + "goog.inherits(derived, base);",
        "derived.superClass_.foo",
        "function(this:base): number");
  }

  @Test
  public void testGoodExtends7() {
    newTest()
        .addSource(
            "/** @constructor \n @extends {Base} */ function Sub() {}"
                + "/** @return {number} */ function f() { return (new Sub()).foo; }"
                + "/** @constructor */ function Base() {}"
                + "/** @type {boolean} */ Base.prototype.foo = true;")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testGoodExtends8() {
    newTest()
        .addSource(
            "/** @constructor */ function Super() {}"
                + "Super.prototype.foo = function() {};"
                + "/** @constructor \n * @extends {Super} */ function Sub() {}"
                + "Sub.prototype = new Super();"
                + "/** @override */ Sub.prototype.foo = function() {};")
        .run();
  }

  @Test
  public void testGoodExtends9() {
    newTest()
        .addSource(
            "/** @constructor */ function Super() {}"
                + "/** @constructor \n * @extends {Super} */ function Sub() {}"
                + "Sub.prototype = new Super();"
                + "/** @return {Super} */ function foo() { return new Sub(); }")
        .run();
  }

  @Test
  public void testGoodExtends10() {
    newTest()
        .addSource(
            "/** @constructor */ function Super() {}"
                + "/** @param {boolean} x */ Super.prototype.foo = function(x) {};"
                + "/** @constructor \n * @extends {Super} */ function Sub() {}"
                + "Sub.prototype = new Super();"
                + "(new Sub()).foo(0);")
        .addDiagnostic(
            "actual parameter 1 of Super.prototype.foo "
                + "does not match formal parameter\n"
                + "found   : number\n"
                + "required: boolean")
        .run();
  }

  @Test
  public void testGoodExtends11() {
    newTest()
        .addSource(
            "/** @constructor \n * @extends {Super} */ function Sub() {}"
                + "/** @constructor \n * @extends {Sub} */ function Sub2() {}"
                + "/** @constructor */ function Super() {}"
                + "/** @param {Super} x */ function foo(x) {}"
                + "foo(new Sub2());")
        .run();
  }

  @Test
  public void testGoodExtends12() {
    newTest()
        .addSource(
            "/** @constructor \n * @extends {B}  */ function C() {}"
                + "/** @constructor \n * @extends {D}  */ function E() {}"
                + "/** @constructor \n * @extends {C}  */ function D() {}"
                + "/** @constructor \n * @extends {A} */ function B() {}"
                + "/** @constructor */ function A() {}"
                + "/** @param {number} x */ function f(x) {} f(new E());")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : E\n"
                + "required: number")
        .run();
  }

  @Test
  public void testGoodExtends13() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + "/** @param {Function} f */ function g(f) {"
                + "  /** @constructor */ function NewType() {};"
                + "  goog.inherits(NewType, f);"
                + "  (new NewType());"
                + "}")
        .run();
  }

  @Test
  public void testGoodExtends14() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + "/** @constructor */ function OldType() {}"
                + "/** @param {?function(new:OldType)} f */ function g(f) {"
                + "  /**\n"
                + "    * @constructor\n"
                + "    * @extends {OldType}\n"
                + "    */\n"
                + "  function NewType() {};"
                + "  goog.inherits(NewType, f);"
                + "  NewType.prototype.method = function() {"
                + "    NewType.superClass_.foo.call(this);"
                + "  };"
                + "}")
        .addDiagnostic("Property foo never defined on OldType.prototype")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGoodExtends_withAliasOfSuperclass() {
    newTest()
        .includeDefaultExterns()
        .addSource(
            CLOSURE_DEFS,
            "/** @constructor */ const OldType = function () {};",
            "const OldTypeAlias = OldType;",
            "",
            "/**",
            "  * @constructor",
            "  * @extends {OldTypeAlias}",
            "  */",
            "function NewType() {};",
            // Verify that we recognize the inheritance even when goog.inherits references
            // OldTypeAlias, not OldType.
            "goog.inherits(NewType, OldTypeAlias);",
            "NewType.prototype.method = function() {",
            "  NewType.superClass_.foo.call(this);",
            "};")
        .addDiagnostic("Property foo never defined on OldType.prototype")
        .run();
  }

  @Test
  public void testBadExtends_withAliasOfSuperclass() {
    newTest()
        .addSource(
            CLOSURE_DEFS,
            "const ns = {};",
            "/** @constructor */ ns.OldType = function () {};",
            "const nsAlias = ns;",
            "",
            "/**",
            "  * @constructor",
            "  * // no @extends here, NewType should not have a goog.inherits",
            "  */",
            "function NewType() {};",
            // Verify that we recognize the inheritance even when goog.inherits references
            // nsAlias.OldType, not ns.OldType.
            "goog.inherits(NewType, ns.OldType);")
        .addDiagnostic("Missing @extends tag on type NewType")
        .run();
  }

  @Test
  public void testBadExtends_withNamespacedAliasOfSuperclass() {
    newTest()
        .addSource(
            CLOSURE_DEFS,
            "const ns = {};",
            "/** @constructor */ ns.OldType = function () {};",
            "const nsAlias = ns;",
            "",
            "/**",
            "  * @constructor",
            "  * // no @extends here, NewType should not have a goog.inhertis",
            "  */",
            "function NewType() {};",
            // Verify that we recognize the inheritance even when goog.inherits references
            // nsAlias.OldType, not ns.OldType.
            "goog.inherits(NewType, nsAlias.OldType);")
        .addDiagnostic("Missing @extends tag on type NewType")
        .run();
  }

  @Test
  public void testBadExtends_withUnionType() {
    // Regression test for b/146562659, crash when extending a union type.
    newTest()
        .addSource(
            "/** @interface */",
            "class Foo {}",
            "/** @interface */",
            "class Bar {}",
            "/** @typedef {!Foo|!Bar} */",
            "let Baz;",
            "/**",
            " * @interface",
            " * @extends {Baz}",
            " */",
            "class Blah {}")
        .addDiagnostic("Blah @extends non-object type (Bar|Foo)")
        .run();
  }

  @Test
  public void testGoodExtends_withNamespacedAliasOfSuperclass() {
    newTest()
        .addSource(
            CLOSURE_DEFS,
            "const ns = {};",
            "/** @constructor */ ns.OldType = function () {};",
            "const nsAlias = ns;",
            "",
            "/**",
            "  * @constructor",
            "  * @extends {nsAlias.OldType}",
            "  */",
            "function NewType() {};",
            // Verify that we recognize the inheritance even when goog.inherits references
            // nsAlias.OldType, not ns.OldType.
            "goog.inherits(NewType, nsAlias.OldType);",
            "NewType.prototype.method = function() {",
            "  NewType.superClass_.foo.call(this);",
            "};")
        .includeDefaultExterns()
        .addDiagnostic("Property foo never defined on ns.OldType.prototype")
        .run();
  }

  @Test
  public void testGoodExtends15() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + "/** @param {Function} f */ function g(f) {"
                + "  /** @constructor */ function NewType() {};"
                + "  goog.inherits(f, NewType);"
                + "  (new NewType());"
                + "}")
        .run();
  }

  @Test
  public void testGoodExtends16() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + "/** @constructor\n"
                + " * @template T */\n"
                + "function C() {}\n"
                + "/** @constructor\n"
                + " * @extends {C<string>} */\n"
                + "function D() {};\n"
                + "goog.inherits(D, C);\n"
                + "(new D())")
        .run();
  }

  @Test
  public void testGoodExtends17() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + "/** @constructor */\n"
                + "function C() {}\n"
                + ""
                + "/** @interface\n"
                + " * @template T */\n"
                + "function D() {}\n"
                + "/** @param {T} t */\n"
                + "D.prototype.method;\n"
                + ""
                + "/** @constructor\n"
                + " * @template T\n"
                + " * @extends {C}\n"
                + " * @implements {D<T>} */\n"
                + "function E() {};\n"
                + "goog.inherits(E, C);\n"
                + "/** @override */\n"
                + "E.prototype.method = function(t) {};\n"
                + ""
                + "var e = /** @type {E<string>} */ (new E());\n"
                + "e.method(3);")
        .addDiagnostic(
            "actual parameter 1 of E.prototype.method does not match formal "
                + "parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testGoodExtends18() {
    newTest()
        .addSource(
            ""
                + "/** @interface */\n"
                + "var MyInterface = function() {};\n"
                + "MyInterface.prototype = {\n"
                + "  /** @return {number} */\n"
                + "  method: function() {}\n"
                + "}\n"
                + "/** @extends {MyInterface}\n * @interface */\n"
                + "var MyOtherInterface = function() {};\n"
                + "MyOtherInterface.prototype = {\n"
                + "  /** @return {number} \n @override */\n"
                + "  method: function() {}\n"
                + "}")
        .run();
  }

  @Test
  public void testGoodExtends19() {
    newTest()
        .addSource(
            ""
                + "/** @constructor */\n"
                + "var MyType = function() {};\n"
                + "MyType.prototype = {\n"
                + "  /** @return {number} */\n"
                + "  method: function() {}\n"
                + "}\n"
                + "/** @constructor \n"
                + " *  @extends {MyType}\n"
                + " */\n"
                + "var MyOtherType = function() {};\n"
                + "MyOtherType.prototype = {\n"
                + "  /** @return {number}\n"
                + "   * @override */\n"
                + "  method: function() {}\n"
                + "}")
        .run();
  }

  @Test
  public void testBadExtends1() {
    newTest()
        .addSource(
            "/** @constructor */function base() {}\n"
                + "/** @constructor\n * @extends {not_base} */function derived() {}\n")
        .addDiagnostic("Bad type annotation. Unknown type not_base")
        .run();
  }

  @Test
  public void testBadExtends2() {
    newTest()
        .addSource(
            "/** @constructor */function base() {\n"
                + "/** @type {!Number}*/\n"
                + "this.baseMember = new Number(4);\n"
                + "}\n"
                + "/** @constructor\n"
                + "  * @extends {base} */function derived() {}\n"
                + "/** @param {!String} x*/\n"
                + "function foo(x){ }\n"
                + "/** @type {!derived}*/var y;\n"
                + "foo(y.baseMember);\n")
        .addDiagnostic(
            "actual parameter 1 of foo does not match formal parameter\n"
                + "found   : Number\n"
                + "required: String")
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
            "/** @constructor \n * @extends {bad} */ function Sub() {}"
                + "/** @constructor \n * @extends {Sub} */ function Sub2() {}"
                + "/** @param {Sub} x */ function foo(x) {}"
                + "foo(new Sub2());")
        .addDiagnostic("Bad type annotation. Unknown type bad")
        .run();
  }

  @Test
  public void testBadExtends5() {
    newTest()
        .addSource(
            "/** @interface */",
            "var MyInterface = function() {};",
            "MyInterface.prototype = {",
            "  /** @return {number} */",
            "  method: function() {}",
            "}",
            "/** @extends {MyInterface}\n * @interface */",
            "var MyOtherInterface = function() {};",
            "MyOtherInterface.prototype = {",
            "  /** @return {string} \n @override */",
            "  method: function() {}",
            "}")
        .addDiagnostic(
            lines(
                "mismatch of the method property on type MyOtherInterface and the type of the"
                    + " property it overrides from interface MyInterface",
                "original: function(this:MyInterface): number",
                "override: function(this:MyOtherInterface): string"))
        .run();
  }

  @Test
  public void testBadExtends6() {
    newTest()
        .addSource(
            ""
                + "/** @constructor */\n"
                + "var MyType = function() {};\n"
                + "MyType.prototype = {\n"
                + "  /** @return {number} */\n"
                + "  method: function() {}\n"
                + "}\n"
                + "/** @constructor \n"
                + " *  @extends {MyType}\n"
                + " */\n"
                + "var MyOtherType = function() {};\n"
                + "MyOtherType.prototype = {\n"
                + "  /** @return {string}\n"
                + "   * @override */\n"
                + "  method: function() { return ''; }\n"
                + "}")
        .addDiagnostic(
            ""
                + "mismatch of the method property type and the type of the property "
                + "it overrides from superclass MyType\n"
                + "original: function(this:MyType): number\n"
                + "override: function(this:MyOtherType): string")
        .run();
  }

  @Test
  public void testLateExtends() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + "/** @constructor */ function Foo() {}\n"
                + "Foo.prototype.foo = function() {};\n"
                + "/** @constructor */function Bar() {}\n"
                + "goog.inherits(Foo, Bar);\n")
        .addDiagnostic("Missing @extends tag on type Foo")
        .run();
  }

  @Test
  public void testSuperclassMatch() {
    compiler.getOptions().setCodingConvention(new GoogleCodingConvention());
    newTest()
        .addSource(
            "/** @constructor */ var Foo = function() {};\n"
                + "/** @constructor \n @extends Foo */ var Bar = function() {};\n"
                + "Bar.inherits = function(x){};"
                + "Bar.inherits(Foo);\n")
        .run();
  }

  @Test
  public void testSuperclassMatchWithMixin() {
    compiler.getOptions().setCodingConvention(new GoogleCodingConvention());
    newTest()
        .addSource(
            "/** @constructor */ var Foo = function() {};\n"
                + "/** @constructor */ var Baz = function() {};\n"
                + "/** @constructor \n @extends Foo */ var Bar = function() {};\n"
                + "Bar.inherits = function(x){};"
                + "Bar.mixin = function(y){};"
                + "Bar.inherits(Foo);\n"
                + "Bar.mixin(Baz);\n")
        .run();
  }

  @Test
  public void testSuperClassDefinedAfterSubClass1() {
    newTest()
        .addSource(
            "/** @constructor \n * @extends {Base} */ function A() {}"
                + "/** @constructor \n * @extends {Base} */ function B() {}"
                + "/** @constructor */ function Base() {}"
                + "/** @param {A|B} x \n * @return {B|A} */ "
                + "function foo(x) { return x; }")
        .run();
  }

  @Test
  public void testSuperClassDefinedAfterSubClass2() {
    newTest()
        .addSource(
            "/** @constructor \n * @extends {Base} */ function A() {}"
                + "/** @constructor \n * @extends {Base} */ function B() {}"
                + "/** @param {A|B} x \n * @return {B|A} */ "
                + "function foo(x) { return x; }"
                + "/** @constructor */ function Base() {}")
        .run();
  }

  @Test
  public void testGoodSuperCall() {
    newTest()
        .addSource(
            "class A {",
            "  /**",
            "   * @param {string} a",
            "   */",
            "  constructor(a) {",
            "    this.a = a;",
            "  }",
            "}",
            "class B extends A {",
            "  constructor() {",
            "    super('b');",
            "  }",
            "}",
            "")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testBadSuperCall() {
    newTest()
        .addSource(
            "class A {",
            "  /**",
            "   * @param {string} a",
            "   */",
            "  constructor(a) {",
            "    this.a = a;",
            "  }",
            "}",
            "class B extends A {",
            "  constructor() {",
            "    super(5);",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of super does not match formal parameter",
                "found   : number",
                "required: string"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testDirectPrototypeAssignment1() {
    newTest()
        .addSource(
            "/** @constructor */ function Base() {}"
                + "Base.prototype.foo = 3;"
                + "/** @constructor \n * @extends {Base} */ function A() {}"
                + "A.prototype = new Base();"
                + "/** @return {string} */ function foo() { return (new A).foo; }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testDirectPrototypeAssignment2() {
    // This ensures that we don't attach property 'foo' onto the Base
    // instance object.
    newTest()
        .addSource(
            "/** @constructor */ function Base() {}"
                + "/** @constructor \n * @extends {Base} */ function A() {}"
                + "A.prototype = new Base();"
                + "A.prototype.foo = 3;"
                + "/** @return {string} */ function foo() { return (new Base).foo; }")
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run(); // exists on subtypes, so only reported for strict props
  }

  @Test
  public void testDirectPrototypeAssignment3() {
    // This verifies that the compiler doesn't crash if the user
    // overwrites the prototype of a global variable in a local scope.
    newTest()
        .addSource(
            "/** @constructor */ var MainWidgetCreator = function() {};"
                + "/** @param {Function} ctor */"
                + "function createMainWidget(ctor) {"
                + "  /** @constructor */ function tempCtor() {};"
                + "  tempCtor.prototype = ctor.prototype;"
                + "  MainWidgetCreator.superClass_ = ctor.prototype;"
                + "  MainWidgetCreator.prototype = new tempCtor();"
                + "}")
        .run();
  }

  @Test
  public void testGoodImplements1() {
    newTest()
        .addSource(
            "/** @interface */function Disposable() {}\n"
                + "/** @implements {Disposable}\n * @constructor */function f() {}")
        .run();
  }

  @Test
  public void testGoodImplements2() {
    newTest()
        .addSource(
            "/** @interface */function Base1() {}\n"
                + "/** @interface */function Base2() {}\n"
                + "/** @constructor\n"
                + " * @implements {Base1}\n"
                + " * @implements {Base2}\n"
                + " */ function derived() {}")
        .run();
  }

  @Test
  public void testGoodImplements3() {
    newTest()
        .addSource(
            "/** @interface */function Disposable() {}\n"
                + "/** @constructor \n @implements {Disposable} */function f() {}")
        .run();
  }

  @Test
  public void testGoodImplements4() {
    newTest()
        .addSource(
            "var goog = {};",
            "/** @type {!Function} */",
            "goog.abstractMethod = function() {};",
            "/** @interface */",
            "goog.Disposable = function() {};",
            "goog.Disposable.prototype.dispose = goog.abstractMethod;",
            "/** @implements {goog.Disposable}\n * @constructor */",
            "goog.SubDisposable = function() {};",
            "/** @inheritDoc */",
            "goog.SubDisposable.prototype.dispose = function() {};")
        .run();
  }

  @Test
  public void testGoodImplements5() {
    newTest()
        .addSource(
            "/** @interface */\n"
                + "goog.Disposable = function() {};"
                + "/** @type {Function} */"
                + "goog.Disposable.prototype.dispose = function() {};"
                + "/** @implements {goog.Disposable}\n * @constructor */"
                + "goog.SubDisposable = function() {};"
                + "/** @param {number} key \n @override */ "
                + "goog.SubDisposable.prototype.dispose = function(key) {};")
        .run();
  }

  @Test
  public void testGoodImplements6() {
    newTest()
        .addSource(
            "var myNullFunction = function() {};"
                + "/** @interface */\n"
                + "goog.Disposable = function() {};"
                + "/** @return {number} */"
                + "goog.Disposable.prototype.dispose = myNullFunction;"
                + "/** @implements {goog.Disposable}\n * @constructor */"
                + "goog.SubDisposable = function() {};"
                + "/** @return {number} \n @override */ "
                + "goog.SubDisposable.prototype.dispose = function() { return 0; };")
        .run();
  }

  @Test
  public void testGoodImplements7() {
    newTest()
        .addSource(
            "var myNullFunction = function() {};"
                + "/** @interface */\n"
                + "goog.Disposable = function() {};"
                + "/** @return {number} */"
                + "goog.Disposable.prototype.dispose = function() {};"
                + "/** @implements {goog.Disposable}\n * @constructor */"
                + "goog.SubDisposable = function() {};"
                + "/** @return {number} \n @override */ "
                + "goog.SubDisposable.prototype.dispose = function() { return 0; };")
        .run();
  }

  @Test
  public void testGoodImplements8() {
    newTest()
        .addSource(
            ""
                + "/** @interface */\n"
                + "MyInterface = function() {};\n"
                + "MyInterface.prototype = {\n"
                + "  /** @return {number} */\n"
                + "  method: function() {}\n"
                + "}\n"
                + "/** @implements {MyInterface}\n * @constructor */\n"
                + "MyClass = function() {};\n"
                + "MyClass.prototype = {\n"
                + "  /** @return {number} \n @override */\n"
                + "  method: function() { return 0; }\n"
                + "}")
        .run();
  }

  @Test
  public void testBadImplements1() {
    newTest()
        .addSource(
            "/** @interface */function Base1() {}\n"
                + "/** @interface */function Base2() {}\n"
                + "/** @constructor\n"
                + " * @implements {nonExistent}\n"
                + " * @implements {Base2}\n"
                + " */ function derived() {}")
        .addDiagnostic("Bad type annotation. Unknown type nonExistent")
        .run();
  }

  @Test
  public void testBadImplements2() {
    newTest()
        .addSource(
            "/** @interface */function Disposable() {}\n"
                + "/** @implements {Disposable}\n */function f() {}")
        .addDiagnostic("@implements used without @constructor for f")
        .run();
  }

  @Test
  public void testBadImplements3() {
    newTest()
        .addSource(
            "var goog = {};",
            "/** @type {!Function} */ goog.abstractMethod = function(){};",
            "/** @interface */ var Disposable = function() {};",
            "Disposable.prototype.method = goog.abstractMethod;",
            "/** @implements {Disposable}\n * @constructor */function f() {}")
        .addDiagnostic("property method on interface Disposable is not implemented by type f")
        .run();
  }

  @Test
  public void testBadImplements4() {
    newTest()
        .addSource(
            "/** @interface */function Disposable() {}\n"
                + "/** @implements {Disposable}\n * @interface */function f() {}")
        .addDiagnostic(
            "f cannot implement this type; an interface can only extend, "
                + "but not implement interfaces")
        .run();
  }

  @Test
  public void testBadImplements5() {
    newTest()
        .addSource(
            "/** @interface */function Disposable() {}\n"
                + "/** @type {number} */ Disposable.prototype.bar = function() {};")
        .addDiagnostic(
            "assignment to property bar of Disposable.prototype\n"
                + "found   : function(): undefined\n"
                + "required: number")
        .run();
  }

  @Test
  public void testBadImplements6() {
    testClosureTypesMultipleWarnings(
        "/** @interface */function Disposable() {}\n"
            + "/** @type {function()} */ Disposable.prototype.bar = 3;",
        ImmutableList.of(
            "assignment to property bar of Disposable.prototype\n"
                + "found   : number\n"
                + "required: function(): ?",
            "interface members can only be empty property declarations, "
                + "empty functions, or goog.abstractMethod"));
  }

  @Test
  public void testBadImplements7() {
    newTest()
        .addSource(
            ""
                + "/** @interface */\n"
                + "MyInterface = function() {};\n"
                + "MyInterface.prototype = {\n"
                + "  /** @return {number} */\n"
                + "  method: function() {}\n"
                + "}\n"
                + "/** @implements {MyInterface}\n * @constructor */\n"
                + "MyClass = function() {};\n"
                + "MyClass.prototype = {\n"
                + "  /** @return {string} \n @override */\n"
                + "  method: function() { return ''; }\n"
                + "}")
        .addDiagnostic(
            ""
                + "mismatch of the method property on type MyClass and the type of the property "
                + "it overrides from interface MyInterface\n"
                + "original: function(): number\n"
                + "override: function(): string")
        .run();
  }

  @Test
  public void testBadImplements8() {
    newTest()
        .addSource(
            ""
                + "/** @interface */\n"
                + "MyInterface = function() {};\n"
                + "MyInterface.prototype = {\n"
                + "  /** @return {number} */\n"
                + "  method: function() {}\n"
                + "}\n"
                + "/** @implements {MyInterface}\n * @constructor */\n"
                + "MyClass = function() {};\n"
                + "MyClass.prototype = {\n"
                + "  /** @return {number} */\n"
                + "  method: function() { return 0; }\n"
                + "}")
        .addDiagnostic(
            ""
                + "property method already defined on interface MyInterface; "
                + "use @override to override it")
        .run();
  }

  @Test
  public void testProtoDoesNotRequireOverrideFromInterface() {
    newTest()
        .includeDefaultExterns()
        .addExterns("/** @type {Object} */ Object.prototype.__proto__;")
        .addSource(
            "/** @interface */\n"
                + "var MyInterface = function() {};\n"
                + "/** @constructor\n @implements {MyInterface} */\n"
                + "var MySuper = function() {};\n"
                + "/** @constructor\n @extends {MySuper} */\n"
                + "var MyClass = function() {};\n"
                + "MyClass.prototype = {\n"
                + "  __proto__: MySuper.prototype\n"
                + "}")
        .run();
  }

  @Test
  public void testConstructorClassTemplate() {
    newTest().addSource("/** @constructor \n @template S,T */ function A() {}\n").run();
  }

  @Test
  public void testGenericBoundExplicitUnknown() {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.BOUNDED_GENERICS, CheckLevel.OFF);

    newTest()
        .addSource(
            "/**", //
            " * @param {T} x",
            " * @template {?} T",
            " */",
            "function f(x) {}")
        .addDiagnostic("Illegal upper bound '?' on template type parameter T")
        .diagnosticsAreErrors()
        .run();
  }

  @Test
  public void testGenericBoundArgAppError() {
    newTest()
        .addSource(
            "/**",
            " * @param {T} x",
            " * @template {number} T",
            " */",
            "function f(x) {}",
            "var a = f('a');")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testGenericBoundArgApp() {
    newTest()
        .addSource(
            "/**",
            " * @param {T} x",
            " * @template {number} T",
            " */",
            "function f(x) {}",
            "var a = f(3);")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testGenericBoundReturnError() {
    // NOTE: This signature is unsafe, but it's an effective minimal test case.
    newTest()
        .addSource(
            "/**",
            " * @return {T}",
            " * @template {number} T",
            " */",
            "function f(x) { return 'a'; }",
            "var a = f(0);")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : string",
                "required: T extends number"))
        .run();
  }

  @Test
  public void testGenericBoundArgAppNullError() {
    newTest()
        .addSource(
            "/**",
            " * @param {T} x",
            " * @template {number} T",
            " */",
            "function f(x) { return x; }",
            "var a = f(null);")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : null",
                "required: number"))
        .run();
  }

  @Test
  public void testGenericBoundArgAppNullable() {
    newTest()
        .addSource(
            "/**",
            " * @param {T} x",
            " * @template {?number} T",
            " */",
            "function f(x) { return x; }",
            "var a = f(null);")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testGenericBoundArgInnerCallAppError() {
    newTest()
        .addSource(
            "/**",
            " * @param {string} x",
            " */",
            "function stringID(x) { return x; }",
            "/**",
            " * @param {T} x",
            " * @template {number|boolean} T",
            " */",
            "function foo(x) { return stringID(x); }")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "actual parameter 1 of stringID does not match formal parameter",
                "found   : T extends (boolean|number)",
                "required: string"))
        .run();
  }

  @Test
  public void testGenericBoundArgInnerCallApp() {
    newTest()
        .addSource(
            "/**",
            " * @param {number} x",
            " */",
            "function numID(x) { return x; }",
            "/**",
            " * @param {T} x",
            " * @template {number} T",
            " */",
            "function foo(x) { return numID(x); }")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testGenericBoundArgInnerCallAppSubtypeError() {
    newTest()
        .addSource(
            "/**",
            " * @param {number} x",
            " */",
            "function numID(x) { return x; }",
            "/**",
            " * @param {T} x",
            " * @template {number|boolean|string} T",
            " */",
            "function foo(x) { return numID(x); }")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "actual parameter 1 of numID does not match formal parameter",
                "found   : T extends (boolean|number|string)",
                "required: number"))
        .run();
  }

  @Test
  public void testGenericBoundArgInnerAssignSubtype() {
    newTest()
        .addSource(
            "/**",
            " * @param {T} x",
            " * @param {number|string|boolean} y",
            " * @template {number|string} T",
            " */",
            "function foo(x,y) { y=x; }")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testGenericBoundArgInnerAssignBoundedInvariantError() {
    newTest()
        .addSource(
            "/**",
            " * @param {number} x",
            " * @param {T} y",
            " * @template {number|string} T",
            " */",
            "function foo(x,y) { y=x; }")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : number",
                "required: T extends (number|string)"))
        .run();
  }

  @Test
  public void testGenericBoundArgInnerAssignSubtypeError() {
    newTest()
        .addSource(
            "/**",
            " * @param {number} x",
            " * @param {T} y",
            " * @template {number|string} T",
            " */",
            "function foo(x,y) { x=y; }")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : T extends (number|string)",
                "required: number"))
        .run();
  }

  @Test
  public void testDoubleGenericBoundArgInnerAssignSubtype() {
    newTest()
        .addSource(
            "/**",
            " * @param {T} x",
            " * @param {T} y",
            " * @template {number|string} T",
            " */",
            "function foo(x,y) { x=y; }")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testBoundedGenericForwardDeclaredParameterError() {
    newTest()
        .addSource(
            "/**",
            " * @template {number} T",
            " */",
            "class Foo {}",
            "var /** !Foo<Str> */ a;",
            "/** @typedef {string} */",
            "let Str;")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            "Bounded generic type error. string assigned to template type T is not a subtype of"
                + " bound number")
        .run();
  }

  @Test
  public void testBoundedGenericParametrizedTypeVarError() {
    newTest()
        .addSource(
            "/**",
            " * @constructor",
            " * @template {number|boolean} T",
            " */",
            "function F() {}",
            "var /** F<string> */ a;")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            "Bounded generic type error. string assigned to template type T is not a subtype of"
                + " bound (boolean|number)")
        .run();
  }

  @Test
  public void testBoundedGenericParametrizedTypeVar() {
    newTest()
        .addSource(
            "/**",
            " * @constructor",
            " * @param {T} x",
            " * @template {number|boolean} T",
            " */",
            "function F(x) {}",
            "var /** F<number> */ a;")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testDoubleGenericBoundArgInnerAssignSubtypeError() {
    newTest()
        .addSource(
            "/**",
            " * @param {T} x",
            " * @param {U} y",
            " * @template {number|string} T",
            " * @template {number|string} U",
            " */",
            "function foo(x,y) { x=y; }")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "assignment",
                "found   : U extends (number|string)",
                "required: T extends (number|string)"))
        .run();
  }

  @Test
  public void testGenericBoundBivariantTemplatized() {
    newTest()
        .addSource(
            "/**",
            " * @param {!Array<T>} x",
            " * @param {!Array<number|boolean>} y",
            " * @template {number} T",
            " */",
            "function foo(x,y) {",
            "  var /** !Array<T> */ z = y;",
            "  y = x;",
            "}")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testConstructGenericBoundGenericBound() {
    newTest()
        .addSource(
            "/**",
            " * @param {T} x",
            " * @param {U} y",
            " * @template {boolean|string} T",
            " * @template {T} U",
            " */",
            "function foo(x,y) { x=y; }")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testConstructGenericBoundGenericBoundError() {
    newTest()
        .addSource(
            "/**",
            " * @param {T} x",
            " * @param {U} y",
            " * @template {boolean|string} T",
            " * @template {T} U",
            " */",
            "function foo(x,y) {",
            "  var /** U */ z = x;",
            "  x = y;",
            "}")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : T extends (boolean|string)",
                "required: U extends T extends (boolean|string)"))
        .run();
  }

  @Test
  public void testDoubleDeclareTemplateNameInFunctions() {
    newTest()
        .addSource(
            "/**",
            " * @param {T} x",
            " * @template {boolean} T",
            " */",
            "function foo(x) { return x; }",
            "/**",
            " * @param {T} x",
            " * @template {string} T",
            " */",
            "function bar(x) { return x; }",
            "foo(true);",
            "bar('Hi');")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testDoubleDeclareTemplateNameInFunctionsError() {
    newTest()
        .addSource(
            "/**",
            " * @param {T} x",
            " * @template {boolean} T",
            " */",
            "function foo(x) { return x; }",
            "/**",
            " * @param {T} x",
            " * @template {string} T",
            " */",
            "function bar(x) { return x; }",
            "foo('Hi');",
            "bar(true);")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "actual parameter 1 of foo does not match formal parameter",
                "found   : string",
                "required: boolean"))
        .addDiagnostic(
            lines(
                "actual parameter 1 of bar does not match formal parameter",
                "found   : boolean",
                "required: string"))
        .run();
  }

  @Test
  public void testShadowTemplateNameInFunctionAndClass() {
    newTest()
        .addSource(
            "/**",
            " * @param {T} x",
            " * @template {boolean} T",
            " */",
            "function foo(x) { return x; }",
            "class Foo {",
            "  /**",
            "   * @param {T} x",
            "   * @template {string} T",
            "   */",
            "  foo(x) { return x; }",
            "}",
            "var F = new Foo();",
            "F.foo('Hi');",
            "foo(true);")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testShadowTemplateNameInFunctionAndClassError() {
    newTest()
        .addSource(
            "/**",
            " * @param {T} x",
            " * @template {boolean} T",
            " */",
            "function foo(x) { return x; }",
            "class Foo {",
            "  /**",
            "   * @param {T} x",
            "   * @template {string} T",
            "   */",
            "  foo(x) { return x; }",
            "}",
            "var F = new Foo();",
            "F.foo(true);",
            "foo('Hi');")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "actual parameter 1 of Foo.prototype.foo does not match formal parameter",
                "found   : boolean",
                "required: string"))
        .addDiagnostic(
            lines(
                "actual parameter 1 of foo does not match formal parameter",
                "found   : string",
                "required: boolean"))
        .run();
  }

  @Test
  public void testTemplateTypeBounds_passingBoundedTemplateType_toTemplatedFunction() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() { }",
            "",
            "/**",
            " * @template {!Foo} X",
            " * @param {X} x",
            " * @return {X}",
            " */",
            "function clone(x) {",
            // The focus of this test is that `X` (already a template variable) is bound to `Y` at
            // this callsite. We confirm that by ensuring an `X` is returned from `cloneInternal`.
            "  return cloneInternal(x);",
            "}",
            "",
            "/**",
            " * @template {!Foo} Y",
            " * @param {Y} x",
            " * @return {Y}",
            " */",
            "function cloneInternal(x) {",
            "  return x;",
            "}")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testTemplateTypeBounds_onFreeFunctions_areAlwaysSpecialized() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() { }",
            "",
            "/**",
            " * @template {!Foo} X",
            " * @param {X} x",
            " * @return {X}",
            " */",
            "function identity(x) {",
            "  return x;",
            "}",
            "",
            "var /** function(!Foo): !Foo */ y = identity;")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  // TODO(b/139192655): The function template should shadow the class template type and not error
  public void testFunctionTemplateShadowClassTemplate() {
    newTest()
        .addSource(
            "/**",
            " * @template T",
            " */",
            "class Foo {",
            "  /**",
            "   * @param {T} x",
            "   * @template T",
            "   */",
            "  foo(x) { return x; }",
            "}",
            "const /** !Foo<string> */ f = new Foo();",
            "f.foo(3);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Foo.prototype.foo does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  // TODO(b/139192655): The function template should shadow the class template type and not error
  public void testFunctionTemplateShadowClassTemplateBounded() {
    newTest()
        .addSource(
            "/**",
            " * @template {string} T",
            " */",
            "class Foo {",
            "  /**",
            "   * @param {T} x",
            "   * @template {number} T",
            "   */",
            "  foo(x) { return x; }",
            "}",
            "const /** !Foo<string> */ f = new Foo();",
            "f.foo(3);")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "actual parameter 1 of Foo.prototype.foo does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testCyclicGenericBoundGenericError() {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.BOUNDED_GENERICS, CheckLevel.OFF);

    newTest()
        .addSource(
            "/**",
            " * @template {S} T",
            " * @template {T} U",
            " * @template {U} S",
            " */",
            "class Foo { }")
        .addDiagnostic("Cycle detected in inheritance chain of type S")
        .addDiagnostic("Cycle detected in inheritance chain of type T")
        .addDiagnostic("Cycle detected in inheritance chain of type U")
        .run();
  }

  @Test
  public void testUnitCyclicGenericBoundGenericError() {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.BOUNDED_GENERICS, CheckLevel.OFF);

    newTest()
        .addSource(
            "/**", //
            " * @template {T} T",
            " */",
            "class Foo { }")
        .addDiagnostic("Cycle detected in inheritance chain of type T")
        .run();
  }

  @Test
  public void testSecondUnitCyclicGenericBoundGenericError() {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.BOUNDED_GENERICS, CheckLevel.OFF);

    newTest()
        .addSource(
            "/**", //
            " * @template {T} T",
            " * @template {T} U",
            " */",
            "class Foo { }")
        .addDiagnostic("Cycle detected in inheritance chain of type T")
        .addDiagnostic("Cycle detected in inheritance chain of type U")
        .run();
  }

  @Test
  public void testConstructCyclicGenericBoundGenericBoundGraph() {
    newTest()
        .addSource(
            "/**",
            " * @template V, E",
            " */",
            "class Vertex {}",
            "/**",
            " * @template V, E",
            " */",
            "class Edge {}",
            "/**",
            " * @template {Vertex<V, E>} V",
            " * @template {Edge<V, E>} E",
            " */",
            "class Graph {}")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testBoundedGenericParametrizedTypeReturnError() {
    newTest()
        .addSource(
            "/**",
            " * @constructor",
            " * @template T",
            " */",
            "function C(x) {}",
            "/**",
            " * @template {number|string} U",
            " * @param {C<boolean>} x",
            " * @return {C<U>}",
            " */",
            "function f(x) { return x; }")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "inconsistent return type",
                "found   : (C<boolean>|null)",
                "required: (C<U extends (number|string)>|null)"))
        .run();
  }

  @Test
  public void testBoundedGenericParametrizedTypeError() {
    newTest()
        .addSource(
            "/**",
            " * @constructor",
            " * @param {T} x",
            " * @template T",
            " */",
            "function C(x) {}",
            "/**",
            " * @template {number|string} U",
            " * @param {C<U>} x",
            " */",
            "function f(x) {}",
            "var /** C<boolean> */ c;",
            "f(c);")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : (C<boolean>|null)",
                "required: (C<(number|string)>|null)"))
        .run();
  }

  @Test
  public void testPartialNarrowingBoundedGenericParametrizedTypeError() {
    newTest()
        .addSource(
            "/**",
            " * @constructor",
            " * @param {T} x",
            " * @template T",
            " */",
            "function C(x) {}",
            "/**",
            " * @template {number|string} U",
            " * @param {C<U>} x",
            " * @param {U} y",
            " */",
            "function f(x,y) {}",
            "var /** C<string> */ c;",
            "f(c,false);")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : (C<string>|null)",
                "required: (C<(number|string)>|null)"))
        .addDiagnostic(
            lines(
                "actual parameter 2 of f does not match formal parameter",
                "found   : boolean",
                "required: (number|string)"))
        .run();
  }

  @Test
  public void testPartialNarrowingBoundedGenericParametrizedType() {
    newTest()
        .addSource(
            "/**",
            " * @constructor",
            " * @param {T} x",
            " * @template T",
            " */",
            "function C(x) {}",
            "/**",
            " * @template {number|string} U",
            " * @param {C<U>} x",
            " * @param {U} y",
            " */",
            "function f(x,y) {}",
            "var /** C<number|string> */ c;",
            "f(c,0);")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .run();
  }

  @Test
  public void testUnmappedBoundedGenericTypeError() {
    newTest()
        .addSource(
            "/**",
            " * @template {number|string} T",
            " * @template {number|null} S",
            " */",
            "class Foo {",
            "  /**",
            "   * @param {T} x",
            "   */",
            "  bar(x) { }",
            "  /**",
            "   * @param {S} x",
            "   */",
            "  baz(x) { }",
            "}",
            "var /** Foo<number> */ foo;",
            "foo.baz(3);",
            "foo.baz(null);",
            "foo.baz('3');")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "actual parameter 1 of Foo.prototype.baz does not match formal parameter",
                "found   : string",
                "required: S extends (null|number)"))
        .run();
  }

  @Test
  public void testFunctionBodyBoundedGenericError() {
    newTest()
        .addSource(
            "class C {}",
            "/**",
            " * @template {C} T",
            " * @param {T} t",
            " */",
            "function f(t) { t = new C(); }")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : C",
                "required: T extends (C|null)"))
        .run();
  }

  @Test
  public void testFunctionBodyBoundedPropertyGenericError() {
    newTest()
        .addSource(
            "/**",
            " * @template {number|string} T",
            " */",
            "class Foo {",
            "  constructor() {",
            "    /** @type {T} */",
            "    this.x;",
            "  }",
            "  m() {",
            "  this.x = 0;",
            "  }",
            "}",
            "function f(t) { t = new C(); }")
        .addDiagnostic(BOUNDED_GENERICS_USE_MSG)
        .addDiagnostic(
            lines(
                "assignment to property x of Foo",
                "found   : number",
                "required: T extends (number|string)"))
        .run();
  }

  @Test
  public void testInterfaceExtends() {
    newTest()
        .addSource(
            "/** @interface */function A() {}\n"
                + "/** @interface \n * @extends {A} */function B() {}\n"
                + "/** @constructor\n"
                + " * @implements {B}\n"
                + " */ function derived() {}")
        .run();
  }

  @Test
  public void testDontCrashOnDupPropDefinition() {
    newTest()
        .addSource(
            "/** @const */",
            "var ns = {};",
            "/** @interface */",
            "ns.I = function() {};",
            "/** @interface */",
            "ns.A = function() {};",
            "/**",
            " * @constructor",
            " * @implements {ns.I}",
            " */",
            "ns.A = function() {};")
        .addDiagnostic("variable ns.A redefined, original definition at [testcode]:6")
        .run();
  }

  @Test
  public void testBadInterfaceExtends1() {
    newTest()
        .addSource("/** @interface \n * @extends {nonExistent} */function A() {}")
        .addDiagnostic("Bad type annotation. Unknown type nonExistent")
        .run();
  }

  @Test
  public void testBadInterfaceExtendsNonExistentInterfaces() {
    newTest()
        .addSource(
            "/** @interface \n"
                + " * @extends {nonExistent1} \n"
                + " * @extends {nonExistent2} \n"
                + " */function A() {}")
        .addDiagnostic("Bad type annotation. Unknown type nonExistent1")
        .addDiagnostic("Bad type annotation. Unknown type nonExistent2")
        .run();
  }

  @Test
  public void testBadInterfaceExtends2() {
    newTest()
        .addSource(
            "/** @constructor */function A() {}\n"
                + "/** @interface \n * @extends {A} */function B() {}")
        .addDiagnostic("B cannot extend this type; interfaces can only extend interfaces")
        .run();
  }

  @Test
  public void testBadInterfaceExtends3() {
    newTest()
        .addSource(
            "/** @interface */function A() {}\n"
                + "/** @constructor \n * @extends {A} */function B() {}")
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
            "/** @interface */function A() {}",
            "/** @constructor */function B() {}",
            "B.prototype = A;")
        .run();
  }

  @Test
  public void testBadInterfaceExtends5() {
    // TODO(user): This should be detected as an error. Even if we enforce
    // that A cannot be used in the assignment, we should still detect the
    // inheritance chain as invalid.
    newTest()
        .addSource(
            "/** @constructor */function A() {}",
            "/** @interface */function B() {}",
            "B.prototype = A;")
        .run();
  }

  @Test
  public void testBadImplementsAConstructor() {
    newTest()
        .addSource(
            "/** @constructor */function A() {}\n"
                + "/** @constructor \n * @implements {A} */function B() {}")
        .addDiagnostic("can only implement interfaces")
        .run();
  }

  @Test
  public void testBadImplementsAConstructorWithSubclass() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function A() {}",
            "/** @implements {A} */",
            "class B {}",
            "class C extends B {}")
        .addDiagnostic("can only implement interfaces")
        .run();
  }

  @Test
  public void testBadImplementsNonInterfaceType() {
    newTest()
        .addSource("/** @constructor \n * @implements {Boolean} */function B() {}")
        .addDiagnostic("can only implement interfaces")
        .run();
  }

  @Test
  public void testBadImplementsNonObjectType() {
    newTest()
        .addSource("/** @constructor \n * @implements {string} */function S() {}")
        .addDiagnostic("can only implement interfaces")
        .run();
  }

  @Test
  public void testBadImplementsDuplicateInterface1() {
    // verify that the same base (not templatized) interface cannot be
    // @implemented more than once.
    newTest()
        .addSource(
            "/** @interface \n"
                + " * @template T\n"
                + " */\n"
                + "function Foo() {}\n"
                + "/** @constructor \n"
                + " * @implements {Foo<?>}\n"
                + " * @implements {Foo}\n"
                + " */\n"
                + "function A() {}\n")
        .addDiagnostic(
            "Cannot @implement the same interface more than once\n" + "Repeated interface: Foo")
        .run();
  }

  @Test
  public void testBadImplementsDuplicateInterface2() {
    // verify that the same base (not templatized) interface cannot be
    // @implemented more than once.
    newTest()
        .addSource(
            "/** @interface \n"
                + " * @template T\n"
                + " */\n"
                + "function Foo() {}\n"
                + "/** @constructor \n"
                + " * @implements {Foo<string>}\n"
                + " * @implements {Foo<number>}\n"
                + " */\n"
                + "function A() {}\n")
        .addDiagnostic(
            "Cannot @implement the same interface more than once\n" + "Repeated interface: Foo")
        .run();
  }

  @Test
  public void testInterfaceAssignment1() {
    newTest()
        .addSource(
            "/** @interface */var I = function() {};\n"
                + "/** @constructor\n@implements {I} */var T = function() {};\n"
                + "var t = new T();\n"
                + "/** @type {!I} */var i = t;")
        .run();
  }

  @Test
  public void testInterfaceAssignment2() {
    newTest()
        .addSource(
            "/** @interface */var I = function() {};\n"
                + "/** @constructor */var T = function() {};\n"
                + "var t = new T();\n"
                + "/** @type {!I} */var i = t;")
        .addDiagnostic("initializing variable\n" + "found   : T\n" + "required: I")
        .run();
  }

  @Test
  public void testInterfaceAssignment3() {
    newTest()
        .addSource(
            "/** @interface */var I = function() {};\n"
                + "/** @constructor\n@implements {I} */var T = function() {};\n"
                + "var t = new T();\n"
                + "/** @type {I|number} */var i = t;")
        .run();
  }

  @Test
  public void testInterfaceAssignment4() {
    newTest()
        .addSource(
            "/** @interface */var I1 = function() {};\n"
                + "/** @interface */var I2 = function() {};\n"
                + "/** @constructor\n@implements {I1} */var T = function() {};\n"
                + "var t = new T();\n"
                + "/** @type {I1|I2} */var i = t;")
        .run();
  }

  @Test
  public void testInterfaceAssignment5() {
    newTest()
        .addSource(
            "/** @interface */var I1 = function() {};\n"
                + "/** @interface */var I2 = function() {};\n"
                + "/** @constructor\n@implements {I1}\n@implements {I2}*/"
                + "var T = function() {};\n"
                + "var t = new T();\n"
                + "/** @type {I1} */var i1 = t;\n"
                + "/** @type {I2} */var i2 = t;\n")
        .run();
  }

  @Test
  public void testInterfaceAssignment6() {
    newTest()
        .addSource(
            "/** @interface */var I1 = function() {};\n"
                + "/** @interface */var I2 = function() {};\n"
                + "/** @constructor\n@implements {I1} */var T = function() {};\n"
                + "/** @type {!I1} */var i1 = new T();\n"
                + "/** @type {!I2} */var i2 = i1;\n")
        .addDiagnostic("initializing variable\n" + "found   : I1\n" + "required: I2")
        .run();
  }

  @Test
  public void testInterfaceAssignment7() {
    newTest()
        .addSource(
            "/** @interface */var I1 = function() {};\n"
                + "/** @interface\n@extends {I1}*/var I2 = function() {};\n"
                + "/** @constructor\n@implements {I2}*/var T = function() {};\n"
                + "var t = new T();\n"
                + "/** @type {I1} */var i1 = t;\n"
                + "/** @type {I2} */var i2 = t;\n"
                + "i1 = i2;\n")
        .run();
  }

  @Test
  public void testInterfaceAssignment8() {
    disableStrictMissingPropertyChecks();

    newTest()
        .addSource(
            "/** @interface */var I = function() {};\n"
                + "/** @type {I} */var i;\n"
                + "/** @type {Object} */var o = i;\n"
                + "new Object().prototype = i.prototype;")
        .run();
  }

  @Test
  public void testInterfaceAssignment9() {
    newTest()
        .addSource(
            "/** @interface */var I = function() {};\n"
                + "/** @return {I?} */function f() { return null; }\n"
                + "/** @type {!I} */var i = f();\n")
        .addDiagnostic("initializing variable\n" + "found   : (I|null)\n" + "required: I")
        .run();
  }

  @Test
  public void testInterfaceAssignment10() {
    newTest()
        .addSource(
            "/** @interface */var I1 = function() {};\n"
                + "/** @interface */var I2 = function() {};\n"
                + "/** @constructor\n@implements {I2} */var T = function() {};\n"
                + "/** @return {!I1|!I2} */function f() { return new T(); }\n"
                + "/** @type {!I1} */var i1 = f();\n")
        .addDiagnostic("initializing variable\n" + "found   : (I1|I2)\n" + "required: I1")
        .run();
  }

  @Test
  public void testInterfaceAssignment11() {
    newTest()
        .addSource(
            "/** @interface */var I1 = function() {};\n"
                + "/** @interface */var I2 = function() {};\n"
                + "/** @constructor */var T = function() {};\n"
                + "/** @return {!I1|!I2|!T} */function f() { return new T(); }\n"
                + "/** @type {!I1} */var i1 = f();\n")
        .addDiagnostic("initializing variable\n" + "found   : (I1|I2|T)\n" + "required: I1")
        .run();
  }

  @Test
  public void testInterfaceAssignment12() {
    newTest()
        .addSource(
            "/** @interface */var I = function() {};\n"
                + "/** @constructor\n@implements{I}*/var T1 = function() {};\n"
                + "/** @constructor\n@extends {T1}*/var T2 = function() {};\n"
                + "/** @return {I} */function f() { return new T2(); }")
        .run();
  }

  @Test
  public void testInterfaceAssignment13() {
    newTest()
        .addSource(
            "/** @interface */var I = function() {};\n"
                + "/** @constructor\n@implements {I}*/var T = function() {};\n"
                + "/** @constructor */function Super() {};\n"
                + "/** @return {I} */Super.prototype.foo = "
                + "function() { return new T(); };\n"
                + "/** @constructor\n@extends {Super} */function Sub() {}\n"
                + "/** @override\n@return {T} */Sub.prototype.foo = "
                + "function() { return new T(); };\n")
        .run();
  }

  @Test
  public void testGetprop1() {
    newTest()
        .addSource("/** @return {void}*/function foo(){foo().bar;}")
        .addDiagnostic(
            "No properties on this expression\n" + "found   : undefined\n" + "required: Object")
        .run();
  }

  @Test
  public void testGetprop2() {
    newTest()
        .addSource("var x = null; x.alert();")
        .addDiagnostic(
            "No properties on this expression\n" + "found   : null\n" + "required: Object")
        .run();
  }

  @Test
  public void testGetprop3() {
    newTest()
        .addSource(
            "/** @constructor */ "
                + "function Foo() { /** @type {?Object} */ this.x = null; }"
                + "Foo.prototype.initX = function() { this.x = {foo: 1}; };"
                + "Foo.prototype.bar = function() {"
                + "  if (this.x == null) { this.initX(); alert(this.x.foo); }"
                + "};")
        .run();
  }

  @Test
  public void testGetprop4() {
    newTest()
        .addSource("var x = null; x.prop = 3;")
        .addDiagnostic(
            "No properties on this expression\n" + "found   : null\n" + "required: Object")
        .run();
  }

  @Test
  public void testGetrop_interfaceWithoutTypeDeclaration() {
    newTest()
        .addSource(
            "/** @interface */var I = function() {};",
            // Note that we didn't declare the type but we still expect JsCompiler to recognize the
            // property.
            "I.prototype.foo;",
            "var v = /** @type {I} */ (null); ",
            "v.foo = 5;")
        .run();
  }

  @Test
  public void testGetrop_interfaceEs6WithoutTypeDeclaration() {
    newTest()
        .addSource(
            "/** @interface */",
            "class I {",
            "  constructor() {",
            "    this.foo;",
            "  }",
            "}",
            "var v = /** @type {I} */ (null); ",
            "v.foo = 5;")
        // TODO(b/131257037): Support ES6 style instance properties on interfaces.
        .addDiagnostic("Property foo never defined on I")
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_MESSAGE)
        .run();
  }

  @Test
  public void testGetrop_interfaceEs6WithTypeDeclaration() {
    newTest()
        .addSource(
            "/** @interface */",
            "class I {",
            "  constructor() {",
            "    /** @type {number} */ this.foo;",
            "  }",
            "}",
            "var v = /** @type {I} */ (null); ",
            "v.foo = 5;")
        .run();
  }

  @Test
  public void testSetprop1() {
    // Create property on struct in the constructor
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() { this.x = 123; }")
        .run();
  }

  @Test
  public void testSetprop2() {
    // Create property on struct outside the constructor
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() {}\n"
                + "(new Foo()).x = 123;")
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_MESSAGE)
        .run();
  }

  @Test
  public void testSetprop3() {
    // Create property on struct outside the constructor
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() {}\n"
                + "(function() { (new Foo()).x = 123; })();")
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_MESSAGE)
        .run();
  }

  @Test
  public void testSetprop4() {
    // Assign to existing property of struct outside the constructor
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() { this.x = 123; }\n"
                + "(new Foo()).x = \"asdf\";")
        .run();
  }

  @Test
  public void testSetprop5() {
    // Create a property on union that includes a struct
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() {}\n"
                + "(true ? new Foo() : {}).x = 123;")
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_ON_UNION_TYPE)
        .run();
  }

  @Test
  public void testSetprop6() {
    // Create property on struct in another constructor
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() {}\n"
                + "/**\n"
                + " * @constructor\n"
                + " * @param{Foo} f\n"
                + " */\n"
                + "function Bar(f) { f.x = 123; }")
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_MESSAGE)
        .run();
  }

  @Test
  public void testSetprop7() {
    // Bug b/c we require THIS when creating properties on structs for simplicity
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() {\n"
                + "  var t = this;\n"
                + "  t.x = 123;\n"
                + "}")
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_MESSAGE)
        .run();
  }

  @Test
  public void testSetprop8() {
    // Create property on struct using DEC
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() {}\n"
                + "(new Foo()).x--;")
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_MESSAGE)
        .addDiagnostic("Property x never defined on Foo")
        .run();
  }

  @Test
  public void testSetprop9() {
    // Create property on struct using ASSIGN_ADD
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() {}\n"
                + "(new Foo()).x += 123;")
        .addDiagnostic(ILLEGAL_PROPERTY_CREATION_MESSAGE)
        .addDiagnostic("Property x never defined on Foo")
        .run();
  }

  @Test
  public void testSetprop10() {
    // Create property on object literal that is a struct
    newTest()
        .addSource(
            "/** \n"
                + " * @constructor \n"
                + " * @struct \n"
                + " */ \n"
                + "function Square(side) { \n"
                + "  this.side = side; \n"
                + "} \n"
                + "Square.prototype = /** @struct */ {\n"
                + "  area: function() { return this.side * this.side; }\n"
                + "};\n"
                + "Square.prototype.id = function(x) { return x; };")
        .run();
  }

  @Test
  public void testSetprop11() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() {}\n"
                + "/** @constructor */\n"
                + "function Bar() {}\n"
                + "Bar.prototype = new Foo();\n"
                + "Bar.prototype.someprop = 123;")
        .run();
  }

  @Test
  public void testSetprop12() {
    // Create property on a constructor of structs (which isn't itself a struct)
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() {}\n"
                + "Foo.someprop = 123;")
        .run();
  }

  @Test
  public void testSetprop13() {
    // Create static property on struct
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Parent() {}\n"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Parent}\n"
                + " */\n"
                + "function Kid() {}\n"
                + "Kid.prototype.foo = 123;\n"
                + "var x = (new Kid()).foo;")
        .run();
  }

  @Test
  public void testSetprop14() {
    // Create static property on struct
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Top() {}\n"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Top}\n"
                + " */\n"
                + "function Mid() {}\n"
                + "/** blah blah */\n"
                + "Mid.prototype.foo = function() { return 1; };\n"
                + "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " * @extends {Mid}\n"
                + " */\n"
                + "function Bottom() {}\n"
                + "/** @override */\n"
                + "Bottom.prototype.foo = function() { return 3; };")
        .run();
  }

  @Test
  public void testSetprop15() {
    // Create static property on struct
    newTest()
        .addSource(
            "/** @interface */\n"
                + "function Peelable() {};\n"
                + "/** @return {undefined} */\n"
                + "Peelable.prototype.peel;\n"
                + "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Fruit() {};\n"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Fruit}\n"
                + " * @implements {Peelable}\n"
                + " */\n"
                + "function Banana() { };\n"
                + "function f() {};\n"
                + "/** @override */\n"
                + "Banana.prototype.peel = f;")
        .run();
  }

  @Test
  public void testGetpropDict1() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @dict\n"
                + " */"
                + "function Dict1(){ this['prop'] = 123; }"
                + "/** @param{Dict1} x */"
                + "function takesDict(x) { return x.prop; }")
        .addDiagnostic("Cannot do '.' access on a dict")
        .run();
  }

  @Test
  public void testGetpropDict2() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @dict\n"
                + " */"
                + "function Dict1(){ this['prop'] = 123; }"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Dict1}\n"
                + " */"
                + "function Dict1kid(){ this['prop'] = 123; }"
                + "/** @param{Dict1kid} x */"
                + "function takesDict(x) { return x.prop; }")
        .addDiagnostic("Cannot do '.' access on a dict")
        .run();
  }

  @Test
  public void testGetpropDict3() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @dict\n"
                + " */"
                + "function Dict1() { this['prop'] = 123; }"
                + "/** @constructor */"
                + "function NonDict() { this.prop = 321; }"
                + "/** @param{(NonDict|Dict1)} x */"
                + "function takesDict(x) { return x.prop; }")
        .addDiagnostic("Cannot do '.' access on a dict")
        .run();
  }

  @Test
  public void testGetpropDict4() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @dict\n"
                + " */"
                + "function Dict1() { this['prop'] = 123; }"
                + "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */"
                + "function Struct1() { this.prop = 123; }"
                + "/** @param{(Struct1|Dict1)} x */"
                + "function takesNothing(x) { return x.prop; }")
        .addDiagnostic("Cannot do '.' access on a dict")
        .run();
  }

  @Test
  public void testGetpropDict5() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @dict\n"
                + " */"
                + "function Dict1(){ this.prop = 123; }")
        .addDiagnostic("Cannot do '.' access on a dict")
        .run();
  }

  @Test
  public void testGetpropDict6() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @dict\n"
                + " */\n"
                + "function Foo() {}\n"
                + "function Bar() {}\n"
                + "Bar.prototype = new Foo();\n"
                + "Bar.prototype.someprop = 123;\n")
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
  public void testGetelemStruct1() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */"
                + "function Struct1(){ this.prop = 123; }"
                + "/** @param{Struct1} x */"
                + "function takesStruct(x) {"
                + "  var z = x;"
                + "  return z['prop'];"
                + "}")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testGetelemStruct2() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */"
                + "function Struct1(){ this.prop = 123; }"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Struct1}"
                + " */"
                + "function Struct1kid(){ this.prop = 123; }"
                + "/** @param{Struct1kid} x */"
                + "function takesStruct2(x) { return x['prop']; }")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testGetelemStruct3() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */"
                + "function Struct1(){ this.prop = 123; }"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Struct1}\n"
                + " */"
                + "function Struct1kid(){ this.prop = 123; }"
                + "var x = (new Struct1kid())['prop'];")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testGetelemStruct4() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */"
                + "function Struct1() { this.prop = 123; }"
                + "/** @constructor */"
                + "function NonStruct() { this.prop = 321; }"
                + "/** @param{(NonStruct|Struct1)} x */"
                + "function takesStruct(x) { return x['prop']; }")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testGetelemStruct5() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */"
                + "function Struct1() { this.prop = 123; }"
                + "/**\n"
                + " * @constructor\n"
                + " * @dict\n"
                + " */"
                + "function Dict1() { this['prop'] = 123; }"
                + "/** @param{(Struct1|Dict1)} x */"
                + "function takesNothing(x) { return x['prop']; }")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testGetelemStruct6() {
    // By casting Bar to Foo, the illegal bracket access is not detected
    newTest()
        .addSource(
            "/** @interface */ function Foo(){}\n"
                + "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " * @implements {Foo}\n"
                + " */"
                + "function Bar(){ this.x = 123; }\n"
                + "var z = /** @type {Foo} */(new Bar())['x'];")
        .run();
  }

  @Test
  public void testGetelemStruct7() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() {}\n"
                + "/** @constructor */\n"
                + "function Bar() {}\n"
                + "Bar.prototype = new Foo();\n"
                + "Bar.prototype['someprop'] = 123;\n")
        .run();
  }

  @Test
  public void testGetelemStruct_noErrorForSettingWellKnownSymbol() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() {}\n"
                + "Foo.prototype[Symbol.iterator] = 123;\n")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGetelemStruct_noErrorForGettingWellKnownSymbol() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() {}\n"
                + "/** @param {!Foo} foo */\n"
                + "function getIterator(foo) { return foo[Symbol.iterator](); }\n")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testInOnStruct() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */"
                + "function Foo() {}\n"
                + "if ('prop' in (new Foo())) {}")
        .addDiagnostic("Cannot use the IN operator with structs")
        .run();
  }

  @Test
  public void testForinOnStruct() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */"
                + "function Foo() {}\n"
                + "for (var prop in (new Foo())) {}")
        .addDiagnostic("Cannot use the IN operator with structs")
        .run();
  }

  @Test
  public void testIArrayLikeAccess1() {
    newTest()
        .addSource(
            "/** ",
            " * @param {!IArrayLike<T>} x",
            " * @return {T}",
            " * @template T",
            "*/",
            "function f(x) { return x[0]; }",
            "function g(/** !Array<string> */ x) {",
            "  var /** null */ y = f(x);",
            "}")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: null")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLikeAccess2() {
    newTest()
        .addSource(
            "/** ",
            " * @param {!IArrayLike<T>} x",
            " * @return {T}",
            " * @template T",
            "*/",
            "function f(x) { return x[0]; }",
            "function g(/** !IArrayLike<string> */ x) {",
            "  var /** null */ y = f(x);",
            "}")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: null")
        .includeDefaultExterns()
        .run();
  }

  // These test the template types in the built-in Iterator/Iterable/Generator are set up correctly
  @Test
  public void testIteratorAccess1() {
    newTest()
        .addSource(
            "/** ",
            " * @param {!Iterator<T>} x",
            " * @return {T}",
            " * @template T",
            "*/",
            "function f(x) { return x[0]; }",
            "function g(/** !Generator<string> */ x) {",
            "  var /** null */ y = f(x);",
            "}")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: null")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIterableAccess1() {
    newTest()
        .addSource(
            "/** ",
            " * @param {!Iterable<T>} x",
            " * @return {T}",
            " * @template T",
            "*/",
            "function f(x) { return x[0]; }",
            "function g(/** !Generator<string> */ x) {",
            "  var /** null */ y = f(x);",
            "}")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: null")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIteratorIterableAccess1() {
    newTest()
        .addSource(
            "/** ",
            " * @param {!IteratorIterable<T>} x",
            " * @return {T}",
            " * @template T",
            "*/",
            "function f(x) { return x[0]; }",
            "function g(/** !Generator<string> */ x) {",
            "  var /** null */ y = f(x);",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: null"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess1() {
    newTest()
        .addSource("var a = []; var b = a['hi'];")
        .addDiagnostic("restricted index type\n" + "found   : string\n" + "required: number")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess2() {
    newTest()
        .addSource("var a = []; var b = a[[1,2]];")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : Array<?>",
                "required: number"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess3() {
    newTest()
        .addSource(
            "var bar = [];" + "/** @return {void} */function baz(){};" + "var foo = bar[baz()];")
        .addDiagnostic("restricted index type\n" + "found   : undefined\n" + "required: number")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess4() {
    newTest()
        .addSource("/**@return {!Array}*/function foo(){};var bar = foo()[foo()];")
        .addDiagnostic("restricted index type\n" + "found   : Array\n" + "required: number")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess6() {
    newTest()
        .addSource("var bar = null[1];")
        .addDiagnostic(
            "only arrays or objects can be accessed\n" + "found   : null\n" + "required: Object")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess7() {
    newTest()
        .addSource("var bar = void 0; bar[0];")
        .addDiagnostic(
            "only arrays or objects can be accessed\n"
                + "found   : undefined\n"
                + "required: Object")
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
            "only arrays or objects can be accessed\n"
                + "found   : undefined\n"
                + "required: Object")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayAccess9() {
    newTest()
        .addSource("/** @return {?Array} */ function f() { return []; }" + "f()[{}]")
        .addDiagnostic("restricted index type\n" + "found   : {}\n" + "required: number")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testPropAccess() {
    newTest()
        .addSource(
            "/** @param {*} x */var f = function(x) {\n"
                + "var o = String(x);\n"
                + "if (typeof o['a'] != 'undefined') { return o['a']; }\n"
                + "return null;\n"
                + "};")
        .run();
  }

  @Test
  public void testPropAccess2() {
    newTest()
        .addSource("var bar = void 0; bar.baz;")
        .addDiagnostic(
            "No properties on this expression\n" + "found   : undefined\n" + "required: Object")
        .run();
  }

  @Test
  public void testPropAccess3() {
    // Verifies that we don't emit two warnings, because
    // the var has been dereferenced after the first one.
    newTest()
        .addSource("var bar = void 0; bar.baz; bar.bax;")
        .addDiagnostic(
            "No properties on this expression\n" + "found   : undefined\n" + "required: Object")
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
            "/**@type {number}*/var a;" + "/**@type {string}*/var b;" + "switch(a){case b:;}")
        .addDiagnostic(
            "case expression doesn't match switch\n" + "found   : string\n" + "required: number")
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
            "var goog = {};"
                + "/** @type {string} */goog.foo = 'hello';"
                + "/** @type {number} */var a = goog.foo;")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testVar6() {
    newTest()
        .addSource(
            "function f() {"
                + "  return function() {"
                + "    /** @type {!Date} */"
                + "    var a = 7;"
                + "  };"
                + "}")
        .addDiagnostic("initializing variable\n" + "found   : number\n" + "required: Date")
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
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: Number")
        .run();
  }

  @Test
  public void testVar11() {
    newTest()
        .addSource("var /** @type {!Date} */foo = 'abc';")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: Date")
        .run();
  }

  @Test
  public void testVar12() {
    newTest()
        .addSource("var /** @type {!Date} */foo = 'abc', " + "/** @type {!RegExp} */bar = 5;")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: Date")
        .addDiagnostic("initializing variable\n" + "found   : number\n" + "required: RegExp")
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
        .addDiagnostic("inconsistent return type\n" + "found   : undefined\n" + "required: number")
        .run();
  }

  @Test
  public void testVar15() {
    newTest()
        .addSource("/** @return {number} */" + "function f() { var x = x || {}; return x; }")
        .addDiagnostic("inconsistent return type\n" + "found   : {}\n" + "required: number")
        .run();
  }

  @Test
  public void testVar15NullishCoalesce() {
    newTest()
        .addSource(
            "/** @return {number} */", //
            "function f() { var x = x ?? {}; return x; }")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : {}",
                "required: number"))
        .run();
  }

  @Test
  public void testAssign1() {
    newTest()
        .addSource("var goog = {};" + "/** @type {number} */goog.foo = 'hello';")
        .addDiagnostic(
            "assignment to property foo of goog\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testAssign2() {
    newTest()
        .addSource("var goog = {};" + "/** @type {number}  */goog.foo = 3;" + "goog.foo = 'hello';")
        .addDiagnostic(
            "assignment to property foo of goog\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testAssign3() {
    newTest()
        .addSource("var goog = {};" + "/** @type {number}  */goog.foo = 3;" + "goog.foo = 4;")
        .run();
  }

  @Test
  public void testAssign4() {
    newTest().addSource("var goog = {};" + "goog.foo = 3;" + "goog.foo = 'hello';").run();
  }

  @Test
  public void testAssignInference() {
    newTest()
        .addSource(
            "/**"
                + " * @param {Array} x"
                + " * @return {number}"
                + " */"
                + "function f(x) {"
                + "  var y = null;"
                + "  y = x[0];"
                + "  if (y == null) { return 4; } else { return 6; }"
                + "}")
        .run();
  }

  @Test
  public void testAssignReadonlyArrayValueFails() {
    newTest()
        .includeDefaultExterns()
        .addSource("const foo = /** @type {!ReadonlyArray<number>} */ ([5]); " + "foo[0] = 3; ")
        .diagnosticsAreErrors()
        .addDiagnostic(TypeCheck.PROPERTY_ASSIGNMENT_TO_READONLY_VALUE)
        .run();
  }

  @Test
  public void testAssignReadonlyArrayValueFailsWithoutTypeParameter() {
    newTest()
        .includeDefaultExterns()
        .addSource("const foo = /** @type {!ReadonlyArray} */ ([5]); " + "foo[0] = 3; ")
        .diagnosticsAreErrors()
        .addDiagnostic(TypeCheck.PROPERTY_ASSIGNMENT_TO_READONLY_VALUE)
        .run();
  }

  @Test
  public void testAssignReadonlyArrayLengthFails() {
    newTest()
        .includeDefaultExterns()
        .addSource("const foo = /** @type {!ReadonlyArray<number>} */ ([5]); " + "foo.length = 0; ")
        .diagnosticsAreErrors()
        .addDiagnostic(TypeCheck.PROPERTY_ASSIGNMENT_TO_READONLY_VALUE)
        .run();
  }

  @Test
  public void testOr1() {
    newTest()
        .addSource(
            "/** @type {number}  */var a;" + "/** @type {number}  */var b;" + "a + b || undefined;")
        .run();
  }

  @Test
  public void testNullishCoalesceNumber() {
    newTest()
        .addSource("/** @type {number}  */var a; /** @type {number}  */var b; a + b ?? undefined;")
        .run();
  }

  @Test
  public void testOr2() {
    newTest()
        .addSource(
            "/** @type {number}  */var a;"
                + "/** @type {number}  */var b;"
                + "/** @type {number}  */var c = a + b || undefined;")
        .addDiagnostic(
            "initializing variable\n" + "found   : (number|undefined)\n" + "required: number")
        .run();
  }

  @Test
  public void testNullishCoalesceNumberVar() {
    // Making sure that ?? returns LHS as long as it is not null/undefined
    // 0 is falsy but not null/undefined so c should always be a
    newTest()
        .addSource(
            "/** @type {number}  */var a;", //
            "/** @type {number}  */var c = a ?? undefined;")
        .run();
  }

  @Test
  public void testOr3() {
    newTest()
        .addSource(
            "/** @type {(number|undefined)} */var a;" + "/** @type {number}  */var c = a || 3;")
        .run();
  }

  @Test
  public void testNullishCoalesceNumberUndefined() {
    newTest()
        .addSource("/** @type {(number|undefined)} */var a; /** @type {number}  */var c = a ?? 3;")
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
        .addDiagnostic("assignment\n" + "found   : string\n" + "required: number")
        .run();
  }

  /**
   * @see #testOr4()
   */
  @Test
  public void testOr5() {
    newTest()
        .addSource("/**@type {number} */var x;x=undefined || \"a\";")
        .addDiagnostic("assignment\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testNullishCoalesceAssignment2() {
    newTest()
        .addSource("/**@type {number} */var x;x=undefined ?? \"a\";")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testOr6() {
    newTest()
        .addSource(
            "/** @param {!Array=} opt_x */",
            "function removeDuplicates(opt_x) {",
            "  var x = opt_x || [];",
            "  var /** undefined */ y = x;",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Array",
                "required: undefined"))
        .run();
  }

  @Test
  public void testNullishCoaleceAssignment3() {
    newTest()
        .addSource(
            "/** @param {!Array=} opt_x */",
            "function removeDuplicates(opt_x) {",
            "  var x = opt_x ?? [];",
            "  var /** undefined */ y = x;",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Array",
                "required: undefined"))
        .run();
  }

  @Test
  public void testAnd1() {
    newTest()
        .addSource(
            "/** @type {number}  */var a;" + "/** @type {number}  */var b;" + "a + b && undefined;")
        .run();
  }

  @Test
  public void testAnd2() {
    newTest()
        .addSource(
            "/** @type {number}  */var a;"
                + "/** @type {number}  */var b;"
                + "/** @type {number}  */var c = a + b && undefined;")
        .addDiagnostic(
            "initializing variable\n" + "found   : (number|undefined)\n" + "required: number")
        .run();
  }

  @Test
  public void testAnd3() {
    newTest()
        .addSource(
            "/** @type {(!Array|undefined)} */var a;"
                + "/** @type {number}  */var c = a && undefined;")
        .addDiagnostic("initializing variable\n" + "found   : undefined\n" + "required: number")
        .run();
  }

  @Test
  public void testAnd4() {
    newTest()
        .addSource(
            "/** @param {number} x */function f(x){};\n"
                + "/** @type {null}  */var x; /** @type {number?} */var y;\n"
                + "if (x && y) { f(y) }")
        .run();
  }

  @Test
  public void testAnd5() {
    newTest()
        .addSource(
            "/** @param {number} x\n@param {string} y*/function f(x,y){};\n"
                + "/** @type {number?} */var x; /** @type {string?} */var y;\n"
                + "if (x && y) { f(x, y) }")
        .run();
  }

  @Test
  public void testAnd6() {
    newTest()
        .addSource(
            "/** @param {number} x */function f(x){};\n"
                + "/** @type {number|undefined} */var x;\n"
                + "if (x && f(x)) { f(x) }")
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
            "function f(/** (null | number | string) */ x) {\n"
                + "  (x && (typeof x === 'number')) && takesNum(x);\n"
                + "}\n"
                + "function takesNum(/** number */ n) {}")
        .run();
  }

  @Test
  public void testAnd9() {
    newTest()
        .addSource(
            "function f(/** (number|string|null) */ x) {\n"
                + "  if (x && typeof x === 'number') {\n"
                + "    takesNum(x);\n"
                + "  }\n"
                + "}\n"
                + "function takesNum(/** number */ x) {}")
        .run();
  }

  @Test
  public void testAnd10() {
    newTest()
        .addSource(
            "function f(/** (null | number | string) */ x) {\n"
                + "  (x && (typeof x === 'string')) && takesNum(x);\n"
                + "}\n"
                + "function takesNum(/** number */ n) {}")
        .addDiagnostic(
            "actual parameter 1 of takesNum does not match formal parameter\n"
                + "found   : string\n"
                + "required: number")
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
            "/** @return {(string|null)} */"
                + "function f() { return null;}"
                + "/** @type {(string|null)} */ var a = f();"
                + "/** @type {string} */"
                + "var b = a ? a : 'default';")
        .run();
  }

  @Test
  public void testHookRestrictsType2() {
    newTest()
        .addSource(
            "/** @type {String} */"
                + "var a = null;"
                + "/** @type {null} */"
                + "var b = a ? null : a;")
        .run();
  }

  @Test
  public void testHookRestrictsType3() {
    newTest()
        .addSource(
            "/** @type {String} */" + "var a;" + "/** @type {null} */" + "var b = (!a) ? a : null;")
        .run();
  }

  @Test
  public void testHookRestrictsType4() {
    newTest()
        .addSource(
            "/** @type {(boolean|undefined)} */"
                + "var a;"
                + "/** @type {boolean} */"
                + "var b = a != null ? a : true;")
        .run();
  }

  @Test
  public void testHookRestrictsType5() {
    newTest()
        .addSource(
            "/** @type {(boolean|undefined)} */"
                + "var a;"
                + "/** @type {(undefined)} */"
                + "var b = a == null ? a : undefined;")
        .run();
  }

  @Test
  public void testHookRestrictsType6() {
    newTest()
        .addSource(
            "/** @type {(number|null|undefined)} */"
                + "var a;"
                + "/** @type {number} */"
                + "var b = a == null ? 5 : a;")
        .run();
  }

  @Test
  public void testHookRestrictsType7() {
    newTest()
        .addSource(
            "/** @type {(number|null|undefined)} */"
                + "var a;"
                + "/** @type {number} */"
                + "var b = a == undefined ? 5 : a;")
        .run();
  }

  @Test
  public void testWhileRestrictsType1() {
    newTest()
        .addSource(
            "/** @param {null} x */ function g(x) {}"
                + "/** @param {number?} x */\n"
                + "function f(x) {\n"
                + "while (x) {\n"
                + "if (g(x)) { x = 1; }\n"
                + "x = x-1;\n}\n}")
        .addDiagnostic(
            "actual parameter 1 of g does not match formal parameter\n"
                + "found   : number\n"
                + "required: null")
        .run();
  }

  @Test
  public void testWhileRestrictsType2() {
    newTest()
        .addSource(
            "/** @param {number?} x\n@return {number}*/\n"
                + "function f(x) {\n/** @type {number} */var y = 0;"
                + "while (x) {\n"
                + "y = x;\n"
                + "x = x-1;\n}\n"
                + "return y;}")
        .run();
  }

  @Test
  public void testHigherOrderFunctions1() {
    newTest()
        .addSource("/** @type {function(number)} */var f;" + "f(true);")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testHigherOrderFunctions2() {
    newTest()
        .addSource("/** @type {function():!Date} */var f;" + "/** @type {boolean} */var a = f();")
        .addDiagnostic("initializing variable\n" + "found   : Date\n" + "required: boolean")
        .run();
  }

  @Test
  public void testHigherOrderFunctions3() {
    newTest()
        .addSource("/** @type {function(this:Array):Date} */var f; new f")
        .addDiagnostic("cannot instantiate non-constructor")
        .run();
  }

  @Test
  public void testHigherOrderFunctions4() {
    newTest()
        .addSource("/** @type {function(this:Array, ...number):Date} */var f; new f")
        .addDiagnostic("cannot instantiate non-constructor")
        .run();
  }

  @Test
  public void testHigherOrderFunctions5() {
    newTest()
        .addSource(
            "/** @param {number} x */ function g(x) {}",
            "/** @type {function(new:Array, ...number):Date} */ var f;",
            "g(new f());")
        .addDiagnostic(
            lines(
                "actual parameter 1 of g does not match formal parameter",
                "found   : Array",
                "required: number"))
        .run();
  }

  @Test
  public void testConstructorAlias1() {
    newTest()
        .addSource(
            "/** @constructor */ var Foo = function() {};"
                + "/** @type {number} */ Foo.prototype.bar = 3;"
                + "/** @constructor */ var FooAlias = Foo;"
                + "/** @return {string} */ function foo() { "
                + "  return (new FooAlias()).bar; }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testConstructorAlias2() {
    newTest()
        .addSource(
            "/** @constructor */ var Foo = function() {};"
                + "/** @constructor */ var FooAlias = Foo;"
                + "/** @type {number} */ FooAlias.prototype.bar = 3;"
                + "/** @return {string} */ function foo() { "
                + "  return (new Foo()).bar; }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testConstructorAlias3() {
    newTest()
        .addSource(
            "/** @constructor */ var Foo = function() {};"
                + "/** @type {number} */ Foo.prototype.bar = 3;"
                + "/** @constructor */ var FooAlias = Foo;"
                + "/** @return {string} */ function foo() { "
                + "  return (new FooAlias()).bar; }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testConstructorAlias4() {
    newTest()
        .addSource(
            "/** @constructor */ var Foo = function() {};"
                + "var FooAlias = Foo;"
                + "/** @type {number} */ FooAlias.prototype.bar = 3;"
                + "/** @return {string} */ function foo() { "
                + "  return (new Foo()).bar; }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testConstructorAlias5() {
    newTest()
        .addSource(
            "/** @constructor */ var Foo = function() {};"
                + "/** @constructor */ var FooAlias = Foo;"
                + "/** @return {FooAlias} */ function foo() { "
                + "  return new Foo(); }")
        .run();
  }

  @Test
  public void testConstructorAlias6() {
    newTest()
        .addSource(
            "/** @constructor */ var Foo = function() {};"
                + "/** @constructor */ var FooAlias = Foo;"
                + "/** @return {Foo} */ function foo() { "
                + "  return new FooAlias(); }")
        .run();
  }

  @Test
  public void testConstructorAlias7() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @constructor */ goog.Foo = function() {};"
                + "/** @constructor */ goog.FooAlias = goog.Foo;"
                + "/** @return {number} */ function foo() { "
                + "  return new goog.FooAlias(); }")
        .addDiagnostic("inconsistent return type\n" + "found   : goog.Foo\n" + "required: number")
        .run();
  }

  @Test
  public void testConstructorAlias8() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/**\n * @param {number} x \n * @constructor */ "
                + "goog.Foo = function(x) {};"
                + "/**\n * @param {number} x \n * @constructor */ "
                + "goog.FooAlias = goog.Foo;"
                + "/** @return {number} */ function foo() { "
                + "  return new goog.FooAlias(1); }")
        .addDiagnostic("inconsistent return type\n" + "found   : goog.Foo\n" + "required: number")
        .run();
  }

  @Test
  public void testConstructorAlias9() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/**\n * @param {number} x \n * @constructor */ "
                + "goog.Foo = function(x) {};"
                + "/** @constructor */ goog.FooAlias = goog.Foo;"
                + "/** @return {number} */ function foo() { "
                + "  return new goog.FooAlias(1); }")
        .addDiagnostic("inconsistent return type\n" + "found   : goog.Foo\n" + "required: number")
        .run();
  }

  @Test
  public void testConstructorAlias10() {
    newTest()
        .addSource(
            "/**\n * @param {number} x \n * @constructor */ "
                + "var Foo = function(x) {};"
                + "/** @constructor */ var FooAlias = Foo;"
                + "/** @return {number} */ function foo() { "
                + "  return new FooAlias(1); }")
        .addDiagnostic("inconsistent return type\n" + "found   : Foo\n" + "required: number")
        .run();
  }

  @Test
  public void testConstructorAlias11() {
    newTest()
        .addSource(
            "/**\n * @param {number} x \n * @constructor */ "
                + "var Foo = function(x) {};"
                + "/** @const */ var FooAlias = Foo;"
                + "/** @const */ var FooAlias2 = FooAlias;"
                + "/** @return {FooAlias2} */ function foo() { "
                + "  return 1; }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: (Foo|null)")
        .run();
  }

  @Test
  public void testConstructorAliasWithBadAnnotation1() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}", //
            "/** @record */ var Bar = Foo;")
        .addDiagnostic("Annotation @record on Bar incompatible with aliased type.")
        .run();
  }

  @Test
  public void testConstructorAliasWithBadAnnotation2() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}", //
            "/** @interface */ var Bar = Foo;")
        .addDiagnostic("Annotation @interface on Bar incompatible with aliased type.")
        .run();
  }

  @Test
  public void testConstructorAliasWithBadAnnotation3() {
    newTest()
        .addSource(
            "/** @interface */ function Foo() {}", //
            "/** @record */ var Bar = Foo;")
        .addDiagnostic("Annotation @record on Bar incompatible with aliased type.")
        .run();
  }

  @Test
  public void testConstructorAliasWithBadAnnotation4() {
    newTest()
        .addSource(
            "/** @interface */ function Foo() {}", //
            "/** @constructor */ var Bar = Foo;")
        .addDiagnostic("Annotation @constructor on Bar incompatible with aliased type.")
        .run();
  }

  @Test
  public void testConstAliasedTypeCastInferredCorrectly1() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {}",
            "/** @return {number} */",
            "Foo.prototype.foo = function() {};",
            "",
            "/** @const */ var FooAlias = Foo;",
            "",
            "var x = /** @type {!FooAlias} */ ({});",
            "var /** null */ n = x.foo();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testConstAliasedTypeCastInferredCorrectly2() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {}",
            "/** @return {number} */",
            "Foo.prototype.foo = function() {};",
            "",
            "var ns = {};",
            "/** @const */ ns.FooAlias = Foo;",
            "",
            "var x = /** @type {!ns.FooAlias} */ ({});",
            "var /** null */ n = x.foo();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testConstructorAliasedTypeCastInferredCorrectly() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {}",
            "/** @return {number} */",
            "Foo.prototype.foo = function() {};",
            "",
            "/** @constructor */ var FooAlias = Foo;",
            "",
            "var x = /** @type {!FooAlias} */ ({});",
            "var /** null */ n = x.foo();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testAliasedTypedef1() {
    newTest()
        .addSource(
            "/** @typedef {string} */ var original;",
            "/** @const */ var alias = original;",
            "/** @type {alias} */ var x;")
        .run();
  }

  @Test
  public void testAliasedTypedef2() {
    newTest()
        .addSource(
            "/** @const */ var ns = {};",
            "/** @typedef {string} */ var original;",
            "/** @const */ ns.alias = original;",
            "/** @type {ns.alias} */ var x;")
        .run();
  }

  @Test
  public void testAliasedTypedef3() {
    newTest()
        .addSource(
            "/** @typedef {string} */ var original;",
            "/** @const */ var alias = original;",
            "/** @return {number} */ var f = function(/** alias */ x) { return x; }")
        .addDiagnostic("inconsistent return type\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testAliasedTypedef4() {
    newTest()
        .addSource(
            "/** @const */ var ns = {};",
            "/** @typedef {string} */ var original;",
            "/** @const */ ns.alias = original;",
            "/** @return {number} */ var f = function(/** ns.alias */ x) { return x; }")
        .addDiagnostic("inconsistent return type\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testAliasedNonTypedef() {
    newTest()
        .addSource(
            "/** @type {string} */ var notTypeName;",
            "/** @const */ var alias = notTypeName;",
            "/** @type {alias} */ var x;")
        .addDiagnostic("Bad type annotation. Unknown type alias")
        .run();
  }

  @Test
  public void testConstStringKeyDoesntCrash() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {}",
            "var ns = {",
            "  /** @const */ FooAlias: Foo",
            "};")
        .run();
  }

  @Test
  public void testClosure7() {
    testClosureTypes(
        "/** @type {string|null|undefined} */ var a = foo();"
            + "/** @type {number} */"
            + "var b = goog.asserts.assert(a);",
        "initializing variable\n" + "found   : string\n" + "required: number");
  }

  private static final String PRIMITIVE_ASSERT_DEFS =
      lines(
          "/**",
          " * @param {T} p",
          " * @return {U}",
          " * @template T",
          " * @template U :=",
          "       mapunion(T, (E) => cond(sub(E, union('null', 'undefined')), none(), E)) =:",
          " * @closurePrimitive {asserts.truthy}",
          " */",
          "function assertTruthy(p) { return p; }",
          "/**",
          " * @param {*} p",
          " * @return {string}",
          " * @closurePrimitive {asserts.matchesReturn}",
          " */",
          "function assertString(p) { return /** @type {string} */ (p); }");

  @Test
  public void testPrimitiveAssertTruthy_removesNullAndUndefinedFromString() {
    newTest()
        .addSource(
            PRIMITIVE_ASSERT_DEFS
                + lines(
                    "function f(/** ?string|undefined */ str) {",
                    "  assertTruthy(str);",
                    "  const /** number */ n = str;",
                    "}"))
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testPrimitiveAssertString_narrowsAllTypeToString() {
    newTest()
        .addSource(
            PRIMITIVE_ASSERT_DEFS
                + lines(
                    "function f(/** * */ str) {",
                    "  assertString(str);",
                    "  const /** number */ n = str;",
                    "}"))
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testReturn1() {
    newTest()
        .addSource("/**@return {void}*/function foo(){ return 3; }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: undefined")
        .run();
  }

  @Test
  public void testReturn2() {
    newTest()
        .addSource("/**@return {!Number}*/function foo(){ return; }")
        .addDiagnostic("inconsistent return type\n" + "found   : undefined\n" + "required: Number")
        .run();
  }

  @Test
  public void testReturn3() {
    newTest()
        .addSource("/**@return {!Number}*/function foo(){ return 'abc'; }")
        .addDiagnostic("inconsistent return type\n" + "found   : string\n" + "required: Number")
        .run();
  }

  @Test
  public void testReturn4() {
    newTest()
        .addSource("/**@return {!Number}\n*/\n function a(){return new Array();}")
        .addDiagnostic("inconsistent return type\n" + "found   : Array<?>\n" + "required: Number")
        .run();
  }

  @Test
  public void testReturn5() {
    newTest()
        .addSource(
            "/**", //
            " * @param {number} n",
            " * @constructor",
            " */",
            "function fn(n){ return }")
        .run();
  }

  @Test
  public void testReturn6() {
    newTest()
        .addSource(
            "/** @param {number} opt_a\n@return {string} */" + "function a(opt_a) { return opt_a }")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (number|undefined)\n" + "required: string")
        .run();
  }

  @Test
  public void testReturn7() {
    newTest()
        .addSource(
            "/** @constructor */var A = function() {};\n"
                + "/** @constructor */var B = function() {};\n"
                + "/** @return {!B} */A.f = function() { return 1; };")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: B")
        .run();
  }

  @Test
  public void testReturn8() {
    newTest()
        .addSource(
            "/** @constructor */var A = function() {};\n"
                + "/** @constructor */var B = function() {};\n"
                + "/** @return {!B} */A.prototype.f = function() { return 1; };")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: B")
        .run();
  }

  @Test
  public void testInferredReturn1() {
    newTest()
        .addSource("function f() {} /** @param {number} x */ function g(x) {}" + "g(f());")
        .addDiagnostic(
            "actual parameter 1 of g does not match formal parameter\n"
                + "found   : undefined\n"
                + "required: number")
        .run();
  }

  @Test
  public void testInferredReturn2() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype.bar = function() {}; "
                + "/** @param {number} x */ function g(x) {}"
                + "g((new Foo()).bar());")
        .addDiagnostic(
            "actual parameter 1 of g does not match formal parameter\n"
                + "found   : undefined\n"
                + "required: number")
        .run();
  }

  @Test
  public void testInferredReturn3() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype.bar = function() {}; "
                + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
                + "/** @return {number} \n * @override  */ "
                + "SubFoo.prototype.bar = function() { return 3; }; ")
        .addDiagnostic(
            "mismatch of the bar property type and the type of the property "
                + "it overrides from superclass Foo\n"
                + "original: function(this:Foo): undefined\n"
                + "override: function(this:SubFoo): number")
        .run();
  }

  @Test
  public void testInferredReturn4() {
    // By design, this throws a warning. if you want global x to be
    // defined to some other type of function, then you need to declare it
    // as a greater type.
    newTest()
        .addSource(
            "var x = function() {};"
                + "x = /** @type {function(): number} */ (function() { return 3; });")
        .addDiagnostic(
            "assignment\n" + "found   : function(): number\n" + "required: function(): undefined")
        .run();
  }

  @Test
  public void testInferredReturn5() {
    // If x is local, then the function type is not declared.
    newTest()
        .addSource(
            "/** @return {string} */"
                + "function f() {"
                + "  var x = function() {};"
                + "  x = /** @type {function(): number} */ (function() { return 3; });"
                + "  return x();"
                + "}")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testInferredReturn6() {
    newTest()
        .addSource(
            "/** @return {string} */"
                + "function f() {"
                + "  var x = function() {};"
                + "  if (f()) "
                + "    x = /** @type {function(): number} */ "
                + "        (function() { return 3; });"
                + "  return x();"
                + "}")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (number|undefined)\n" + "required: string")
        .run();
  }

  @Test
  public void testInferredReturn7() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @param {number} x */ Foo.prototype.bar = function(x) {};"
                + "Foo.prototype.bar = function(x) { return 3; };")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: undefined")
        .run();
  }

  @Test
  public void testInferredReturn8() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @param {number} x */ Foo.prototype.bar = function(x) {};"
                + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
                + "/** @override @param {number} x */ SubFoo.prototype.bar = "
                + "    function(x) { return 3; }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: undefined")
        .run();
  }

  @Test
  public void testInferredReturn9() {
    newTest()
        .addSource(
            "/** @param {function():string} x */",
            "function f(x) {}",
            "f(/** asdf */ function() { return 123; });")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testInferredParam1() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @param {number} x */ Foo.prototype.bar = function(x) {};"
                + "/** @param {string} x */ function f(x) {}"
                + "Foo.prototype.bar = function(y) { f(y); };")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testInferredParam2() {
    newTest()
        .addSource(
            "/** @param {string} x */ function f(x) {}"
                + "/** @constructor */ function Foo() {}"
                + "/** @param {number} x */ Foo.prototype.bar = function(x) {};"
                + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
                + "/** @override @return {void} */ SubFoo.prototype.bar = "
                + "    function(x) { f(x); }")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testInferredParam3() {
    newTest()
        .addSource(
            "/** @param {string} x */ function f(x) {}"
                + "/** @constructor */ function Foo() {}"
                + "/** @param {number=} x */ Foo.prototype.bar = function(x) {};"
                + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
                + "/** @override @return {void} */ SubFoo.prototype.bar = "
                + "    function(x) { f(x); }; (new SubFoo()).bar();")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : (number|undefined)\n"
                + "required: string")
        .run();
  }

  @Test
  public void testInferredParam4() {
    newTest()
        .addSource(
            "/** @param {string} x */ function f(x) {}",
            "",
            "/** @constructor */",
            "function Foo() {}",
            "/** @param {...number} x */",
            "Foo.prototype.bar = function(x) {};",
            "",
            "/**",
            " * @constructor",
            " * @extends {Foo}",
            " */",
            "function SubFoo() {}",
            "/**",
            " * @override",
            " * @return {void}",
            " */",
            "SubFoo.prototype.bar = function(x) { f(x); };",
            "(new SubFoo()).bar();")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testInferredParam5() {
    newTest()
        .addSource(
            "/** @param {string} x */ function f(x) {}"
                + "/** @constructor */ function Foo() {}"
                + "/** @param {...number} x */ Foo.prototype.bar = function(x) {};"
                + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
                + "/** @override @param {number=} x \n * @param {...number} y  */ "
                + "SubFoo.prototype.bar = "
                + "    function(x, y) { f(x); }; (new SubFoo()).bar();")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : (number|undefined)\n"
                + "required: string")
        .run();
  }

  @Test
  public void testInferredParam6() {
    newTest()
        .addSource(
            "/** @param {string} x */ function f(x) {}"
                + "/** @constructor */ function Foo() {}"
                + "/** @param {number=} x */ Foo.prototype.bar = function(x) {};"
                + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
                + "/** @override @param {number=} x \n * @param {number=} y */ "
                + "SubFoo.prototype.bar = "
                + "    function(x, y) { f(y); };")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : (number|undefined)\n"
                + "required: string")
        .run();
  }

  @Test
  public void testInferredParam7() {
    newTest()
        .addSource(
            "/** @param {string} x */ function f(x) {}"
                + "/** @type {function(number=,number=)} */"
                + "var bar = function(x, y) { f(y); };")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : (number|undefined)\n"
                + "required: string")
        .run();
  }

  @Test
  public void testInferredParam8() {
    newTest()
        .addSource(
            "/** @param {function(string)} callback */",
            "function foo(callback) {}",
            "foo(/** random JSDoc */ function(x) { var /** number */ n = x; });")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : string", // type of "x" is inferred to be string
                "required: number"))
        .run();
  }

  @Test
  public void testFunctionLiteralParamWithInlineJSDocNotInferred() {
    newTest()
        .addSource(
            "/** @param {function(string)} x */",
            "function f(x) {}",
            "f(function(/** number */ x) {});")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : function(number): undefined",
                "required: function(string): ?"))
        .run();
  }

  @Test
  public void testFunctionLiteralParamWithInlineJSDocNotInferredWithTwoParams() {
    newTest()
        .addSource(
            "/** @param {function(string, number)} x */",
            "function f(x) {}",
            "f((/** string */ str, num) => {",
            // TODO(b/123583824): this should be a type mismatch warning.
            //    found   : number
            //    expected: string
            //  but the JSDoc on `str` blocks inference of `num`.
            "  const /** string */ newStr = num;",
            "});")
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
            "/** @param {function(string)} callback */",
            "function foo(callback) {}",
            // The `/** number */` JSDoc is attached to the entire arrow  function, not the
            // parameter `x`
            "foo(/** number */ x =>  { var /** number */ y = x; });")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : string", // type of "x" is inferred to be string
                "required: number"))
        .run();
  }

  @Test
  public void testOverriddenParams1() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @param {...?} var_args */"
                + "Foo.prototype.bar = function(var_args) {};"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */ function SubFoo() {}"
                + "/**\n"
                + " * @param {number} x\n"
                + " * @override\n"
                + " */"
                + "SubFoo.prototype.bar = function(x) {};")
        .run();
  }

  @Test
  public void testOverriddenParams2() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @type {function(...?)} */"
                + "Foo.prototype.bar = function(var_args) {};"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */ function SubFoo() {}"
                + "/**\n"
                + " * @type {function(number)}\n"
                + " * @override\n"
                + " */"
                + "SubFoo.prototype.bar = function(x) {};")
        .run();
  }

  @Test
  public void testOverriddenParams3() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @param {...number} var_args */"
                + "Foo.prototype.bar = function(var_args) { };"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */ function SubFoo() {}"
                + "/**\n"
                + " * @param {number} x\n"
                + " * @override\n"
                + " */"
                + "SubFoo.prototype.bar = function(x) {};")
        .addDiagnostic(
            "mismatch of the bar property type and the type of the "
                + "property it overrides from superclass Foo\n"
                + "original: function(this:Foo, ...number): undefined\n"
                + "override: function(this:SubFoo, number): undefined")
        .run();
  }

  @Test
  public void testOverriddenParams4() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @type {function(...number)} */"
                + "Foo.prototype.bar = function(var_args) {};"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */ function SubFoo() {}"
                + "/**\n"
                + " * @type {function(number)}\n"
                + " * @override\n"
                + " */"
                + "SubFoo.prototype.bar = function(x) {};")
        .addDiagnostic(
            "mismatch of the bar property type and the type of the "
                + "property it overrides from superclass Foo\n"
                + "original: function(...number): ?\n"
                + "override: function(number): ?")
        .run();
  }

  @Test
  public void testOverriddenParams5() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @param {number} x */"
                + "Foo.prototype.bar = function(x) { };"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */ function SubFoo() {}"
                + "/**\n"
                + " * @override\n"
                + " */"
                + "SubFoo.prototype.bar = function() {};"
                + "(new SubFoo()).bar();")
        .run();
  }

  @Test
  public void testOverriddenParams6() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @param {number} x */"
                + "Foo.prototype.bar = function(x) { };"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */ function SubFoo() {}"
                + "/**\n"
                + " * @override\n"
                + " */"
                + "SubFoo.prototype.bar = function() {};"
                + "(new SubFoo()).bar(true);")
        .addDiagnostic(
            "actual parameter 1 of SubFoo.prototype.bar "
                + "does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testOverriddenParams7() {
    newTest()
        .addSource(
            "/** @interface */ function Foo() {}",
            "/** @param {number} x */",
            "Foo.prototype.bar = function(x) { };",
            "/**",
            " * @interface",
            " * @extends {Foo}",
            " */ function SubFoo() {}",
            "/**",
            " * @override",
            " */",
            "SubFoo.prototype.bar = function() {};",
            "var subFoo = /** @type {SubFoo} */ (null);",
            "subFoo.bar(true);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of SubFoo.prototype.bar does not match formal parameter",
                "found   : boolean",
                "required: number"))
        .run();
  }

  @Test
  public void testOverriddenParams8() {
    newTest()
        .addSource(
            "/** @constructor\n * @template T */ function Foo() {}",
            "/** @param {T} x */",
            "Foo.prototype.bar = function(x) { };",
            "/**",
            " * @constructor",
            " * @extends {Foo<string>}",
            " */ function SubFoo() {}",
            "/**",
            " * @param {number} x",
            " * @override",
            " */",
            "SubFoo.prototype.bar = function(x) {};")
        .addDiagnostic(
            lines(
                "mismatch of the bar property type and the type of the "
                    + "property it overrides from superclass Foo",
                "original: function(this:Foo, string): undefined",
                "override: function(this:SubFoo, number): undefined"))
        .run();
  }

  @Test
  public void testOverriddenReturn1() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @return {Object} */ Foo.prototype.bar = "
                + "    function() { return {}; };"
                + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
                + "/** @return {SubFoo}\n * @override */ SubFoo.prototype.bar = "
                + "    function() { return new Foo(); }")
        .addDiagnostic("inconsistent return type\n" + "found   : Foo\n" + "required: (SubFoo|null)")
        .run();
  }

  @Test
  public void testOverriddenReturn2() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @return {SubFoo} */ Foo.prototype.bar = "
                + "    function() { return new SubFoo(); };"
                + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
                + "/** @return {Foo} x\n * @override */ SubFoo.prototype.bar = "
                + "    function() { return new SubFoo(); }")
        .addDiagnostic(
            "mismatch of the bar property type and the type of the "
                + "property it overrides from superclass Foo\n"
                + "original: function(this:Foo): (SubFoo|null)\n"
                + "override: function(this:SubFoo): (Foo|null)")
        .run();
  }

  @Test
  public void testOverriddenReturn3() {
    newTest()
        .addSource(
            "/** @constructor \n * @template T */ function Foo() {}"
                + "/** @return {T} */ Foo.prototype.bar = "
                + "    function() { return null; };"
                + "/** @constructor \n * @extends {Foo<string>} */ function SubFoo() {}"
                + "/** @override */ SubFoo.prototype.bar = "
                + "    function() { return 3; }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testOverriddenReturn4() {
    newTest()
        .addSource(
            "/** @constructor \n * @template T */ function Foo() {}"
                + "/** @return {T} */ Foo.prototype.bar = "
                + "    function() { return null; };"
                + "/** @constructor \n * @extends {Foo<string>} */ function SubFoo() {}"
                + "/** @return {number}\n * @override */ SubFoo.prototype.bar = "
                + "    function() { return 3; }")
        .addDiagnostic(
            "mismatch of the bar property type and the type of the "
                + "property it overrides from superclass Foo\n"
                + "original: function(this:Foo): string\n"
                + "override: function(this:SubFoo): number")
        .run();
  }

  @Test
  public void testThis1() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @constructor */goog.A = function(){};"
                + "/** @return {number} */"
                + "goog.A.prototype.n = function() { return this };")
        .addDiagnostic("inconsistent return type\n" + "found   : goog.A\n" + "required: number")
        .run();
  }

  @Test
  public void testOverriddenProperty1() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @type {Object} */"
                + "Foo.prototype.bar = {};"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */ function SubFoo() {}"
                + "/**\n"
                + " * @type {Array}\n"
                + " * @override\n"
                + " */"
                + "SubFoo.prototype.bar = [];")
        .run();
  }

  @Test
  public void testOverriddenProperty2() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {"
                + "  /** @type {Object} */"
                + "  this.bar = {};"
                + "}"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */ function SubFoo() {}"
                + "/**\n"
                + " * @type {Array}\n"
                + " * @override\n"
                + " */"
                + "SubFoo.prototype.bar = [];")
        .run();
  }

  @Test
  public void testOverriddenProperty3() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {"
                + "}"
                + "/** @type {string} */ Foo.prototype.data;"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */ function SubFoo() {}"
                + "/** @type {string|Object} \n @override */ "
                + "SubFoo.prototype.data = null;")
        .addDiagnostic(
            "mismatch of the data property type and the type "
                + "of the property it overrides from superclass Foo\n"
                + "original: string\n"
                + "override: (Object|null|string)")
        .run();
  }

  @Test
  public void testOverriddenProperty4() {
    // These properties aren't declared, so there should be no warning.
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype.bar = null;"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */ function SubFoo() {}"
                + "SubFoo.prototype.bar = 3;")
        .run();
  }

  @Test
  public void testOverriddenProperty5() {
    // An override should be OK if the superclass property wasn't declared.
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype.bar = null;"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */ function SubFoo() {}"
                + "/** @override */ SubFoo.prototype.bar = 3;")
        .run();
  }

  @Test
  public void testOverriddenProperty6() {
    // The override keyword shouldn't be necessary if the subclass property
    // is inferred.
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @type {?number} */ Foo.prototype.bar = null;"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */ function SubFoo() {}"
                + "SubFoo.prototype.bar = 3;")
        .run();
  }

  @Test
  public void testOverriddenPropertyWithUnknown() {
    // When overriding a declared property with a declared unknown property, we warn for a missing
    // override but not a type mismatch.
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}",
            "/** @type {?number} */ Foo.prototype.bar = null;",
            "",
            "/**",
            " * @constructor",
            " * @extends {Foo}",
            " */",
            "function SubFoo() {}",
            "/** @type {?} */",
            "SubFoo.prototype.bar = 'not a number';")
        .addDiagnostic(
            "property bar already defined on superclass Foo; use @override to override it")
        .run();
  }

  @Test
  public void testOverriddenPropertyWithUnknownInterfaceAncestor() {
    newTest()
        .addSource(
            "/** @interface @extends {Unknown}  */ function Foo() {}",
            "/** @type {string} */ Foo.prototype.data;",
            "/** @constructor @implements {Foo} */ function SubFoo() {}",
            "/** @type {string|Object} */ ",
            "SubFoo.prototype.data = null;")
        .addDiagnostic("Bad type annotation. Unknown type Unknown")
        .addDiagnostic(
            lines(
                "mismatch of the data property on type SubFoo and the type "
                    + "of the property it overrides from interface Foo",
                "original: string",
                "override: (Object|null|string)"))
        .run();
  }

  @Test
  public void testThis2() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @constructor */goog.A = function(){"
                + "  this.foo = null;"
                + "};"
                + "/** @return {number} */"
                + "goog.A.prototype.n = function() { return this.foo };")
        .addDiagnostic("inconsistent return type\n" + "found   : null\n" + "required: number")
        .run();
  }

  @Test
  public void testThis3() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @constructor */goog.A = function(){"
                + "  this.foo = null;"
                + "  this.foo = 5;"
                + "};")
        .run();
  }

  @Test
  public void testThis4() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @constructor */goog.A = function(){"
                + "  /** @type {string?} */this.foo = null;"
                + "};"
                + "/** @return {number} */goog.A.prototype.n = function() {"
                + "  return this.foo };")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (null|string)\n" + "required: number")
        .run();
  }

  @Test
  public void testThis5() {
    newTest()
        .addSource("/** @this {Date}\n@return {number}*/function h() { return this }")
        .addDiagnostic("inconsistent return type\n" + "found   : Date\n" + "required: number")
        .run();
  }

  @Test
  public void testThis6() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @constructor\n@return {!Date} */"
                + "goog.A = function(){ return this };")
        .addDiagnostic("inconsistent return type\n" + "found   : goog.A\n" + "required: Date")
        .run();
  }

  @Test
  public void testThis7() {
    newTest()
        .addSource(
            "/** @constructor */function A(){};"
                + "/** @return {number} */A.prototype.n = function() { return this };")
        .addDiagnostic("inconsistent return type\n" + "found   : A\n" + "required: number")
        .run();
  }

  @Test
  public void testThis8() {
    newTest()
        .addSource(
            "/** @constructor */function A(){"
                + "  /** @type {string?} */this.foo = null;"
                + "};"
                + "/** @return {number} */A.prototype.n = function() {"
                + "  return this.foo };")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (null|string)\n" + "required: number")
        .run();
  }

  @Test
  public void testTypeOfThis_inStaticMethod_onEs5Ctor_isUnknown() {
    newTest()
        .addSource(
            "/** @constructor */function A(){};",
            "A.foo = 3;",
            "/** @return {string} */ A.bar = function() { return this.foo; };")
        .run();
  }

  @Test
  public void testThis10() {
    // In A.bar, the type of {@code this} is inferred from the @this tag.
    newTest()
        .addSource(
            "/** @constructor */function A(){};"
                + "A.prototype.foo = 3;"
                + "/** @this {A}\n@return {string} */"
                + "A.bar = function() { return this.foo; };")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testThis11() {
    newTest()
        .addSource(
            "/** @param {number} x */ function f(x) {}"
                + "/** @constructor */ function Ctor() {"
                + "  /** @this {Date} */"
                + "  this.method = function() {"
                + "    f(this);"
                + "  };"
                + "}")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : Date\n"
                + "required: number")
        .run();
  }

  @Test
  public void testThis12() {
    newTest()
        .addSource(
            "/** @param {number} x */ function f(x) {}"
                + "/** @constructor */ function Ctor() {}"
                + "Ctor.prototype['method'] = function() {"
                + "  f(this);"
                + "}")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : Ctor\n"
                + "required: number")
        .run();
  }

  @Test
  public void testThis13() {
    newTest()
        .addSource(
            "/** @param {number} x */ function f(x) {}"
                + "/** @constructor */ function Ctor() {}"
                + "Ctor.prototype = {"
                + "  method: function() {"
                + "    f(this);"
                + "  }"
                + "};")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : Ctor\n"
                + "required: number")
        .run();
  }

  @Test
  public void testThis14() {
    newTest()
        .addSource("/** @param {number} x */ function f(x) {}" + "f(this.Object);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : (typeof Object)",
                "required: number"))
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
            "/** @constructor */ function F() {}"
                + "/** @type {function(this:F)} */ function f() {}"
                + "f();")
        .addDiagnostic("\"function(this:F): ?\" must be called with a \"this\" type")
        .run();
  }

  @Test
  public void testThisTypeOfFunction3() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "F.prototype.bar = function() {};"
                + "var f = (new F()).bar; f();")
        .addDiagnostic("\"function(this:F): undefined\" must be called with a \"this\" type")
        .run();
  }

  @Test
  public void testThisTypeOfFunction4() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().addObject().build())
        .addSource(
            "/** @constructor */ function F() {}",
            "F.prototype.moveTo = function(x, y) {};",
            "F.prototype.lineTo = function(x, y) {};",
            "function demo() {",
            "  var path = new F();",
            "  var points = [[1,1], [2,2]];",
            "  for (var i = 0; i < points.length; i++) {",
            "    (i == 0 ? path.moveTo : path.lineTo)(points[i][0], points[i][1]);",
            "  }",
            "}")
        .addDiagnostic("\"function(this:F, ?, ?): undefined\" must be called with a \"this\" type")
        .run();
  }

  @Test
  public void testThisTypeOfFunction5() {
    newTest()
        .addSource(
            "/** @type {function(this:number)} */",
            "function f() {",
            "  var /** number */ n = this;",
            "}")
        .run();
  }

  @Test
  public void testGlobalThis1() {
    newTest()
        .addSource(
            "/** @constructor */ function Window() {}"
                + "/** @param {string} msg */ "
                + "Window.prototype.alert = function(msg) {};"
                + "this.alert(3);")
        .addDiagnostic(
            "actual parameter 1 of Window.prototype.alert "
                + "does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testGlobalThis2a() {
    // this.alert = 3 doesn't count as a declaration, so this is a warning.
    newTest()
        .addSource(
            "/** @constructor */ function Bindow() {}",
            "/** @param {string} msg */ ",
            "Bindow.prototype.alert = function(msg) {};",
            "this.alert = 3;",
            "(new Bindow()).alert(this.alert)")
        .addDiagnostic("Property alert never defined on global this")
        .addDiagnostic("Property alert never defined on global this")
        .run();
  }

  @Test
  public void testGlobalThis2b() {
    // Only reported if strict property checks are enabled
    disableStrictMissingPropertyChecks();

    newTest()
        .addSource(
            "/** @constructor */ function Bindow() {}",
            "/** @param {string} msg */ ",
            "Bindow.prototype.alert = function(msg) {};",
            "this.alert = 3;",
            "(new Bindow()).alert(this.alert)")
        .run();
  }

  @Test
  public void testGlobalThis2c() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @constructor */ function Bindow() {}"
                + "/** @param {string} msg */ "
                + "Bindow.prototype.alert = function(msg) {};"
                + "/** @return {number} */ this.alert = function() { return 3; };"
                + "(new Bindow()).alert(this.alert())")
        .addDiagnostic(
            "actual parameter 1 of Bindow.prototype.alert "
                + "does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testGlobalThis3() {
    newTest()
        .addSource("/** @param {string} msg */ " + "function alert(msg) {};" + "this.alert(3);")
        .addDiagnostic(
            "actual parameter 1 of global this.alert "
                + "does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testGlobalThis4() {
    newTest()
        .addSource(
            "/** @param {string} msg */ " + "var alert = function(msg) {};" + "this.alert(3);")
        .addDiagnostic(
            "actual parameter 1 of global this.alert "
                + "does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testGlobalThis5() {
    newTest()
        .addSource(
            "function f() {"
                + "  /** @param {string} msg */ "
                + "  var alert = function(msg) {};"
                + "}"
                + "this.alert(3);")
        .addDiagnostic("Property alert never defined on global this")
        .run();
  }

  @Test
  public void testGlobalThis6() {
    newTest()
        .addSource(
            "/** @param {string} msg */ ",
            "var alert = function(msg) {};",
            "var x = 3;",
            "x = 'msg';",
            "this.alert(this.x);")
        .run();
  }

  @Test
  public void testGlobalThis7() {
    newTest()
        .addSource(
            "/** @constructor */ function Window() {}"
                + "/** @param {Window} msg */ "
                + "var foo = function(msg) {};"
                + "foo(this);")
        .run();
  }

  @Test
  public void testGlobalThis8() {
    newTest()
        .addSource(
            "/** @constructor */ function Window() {}"
                + "/** @param {number} msg */ "
                + "var foo = function(msg) {};"
                + "foo(this);")
        .addDiagnostic(
            "actual parameter 1 of foo does not match formal parameter\n"
                + "found   : global this\n"
                + "required: number")
        .run();
  }

  @Test
  public void testGlobalThis9() {
    newTest()
        .addSource(
            // Window is not marked as a constructor, so the
            // inheritance doesn't happen.
            "function Window() {}" + "Window.prototype.alert = function() {};" + "this.alert();")
        .addDiagnostic("Property alert never defined on global this")
        .run();
  }

  @Test
  public void testGlobalThisDoesNotIncludeVarsDeclaredWithConst() {
    newTest()
        .addSource(
            "/** @param {string} msg */ ", //
            "const alert = function(msg) {};",
            "this.alert('boo');")
        .addDiagnostic("Property alert never defined on global this")
        .run();
  }

  @Test
  public void testGlobalThisDoesNotIncludeVarsDeclaredWithLet() {
    newTest()
        .addSource(
            "/** @param {string} msg */ ", //
            "let alert = function(msg) {};",
            "this.alert('boo');")
        .addDiagnostic("Property alert never defined on global this")
        .run();
  }

  @Test
  public void testControlFlowRestrictsType1() {
    newTest()
        .addSource(
            "/** @return {String?} */ function f() { return null; }"
                + "/** @type {String?} */ var a = f();"
                + "/** @type {String} */ var b = new String('foo');"
                + "/** @type {null} */ var c = null;"
                + "if (a) {"
                + "  b = a;"
                + "} else {"
                + "  c = a;"
                + "}")
        .run();
  }

  @Test
  public void testControlFlowRestrictsType2() {
    newTest()
        .addSource(
            "/** @return {(string|null)} */ function f() { return null; }"
                + "/** @type {(string|null)} */ var a = f();"
                + "/** @type {string} */ var b = 'foo';"
                + "/** @type {null} */ var c = null;"
                + "if (a) {"
                + "  b = a;"
                + "} else {"
                + "  c = a;"
                + "}")
        .addDiagnostic("assignment\n" + "found   : (null|string)\n" + "required: null")
        .run();
  }

  @Test
  public void testControlFlowRestrictsType3() {
    newTest()
        .addSource(
            "/** @type {(string|void)} */"
                + "var a;"
                + "/** @type {string} */"
                + "var b = 'foo';"
                + "if (a) {"
                + "  b = a;"
                + "}")
        .run();
  }

  @Test
  public void testControlFlowRestrictsType4() {
    newTest()
        .addSource(
            "/** @param {string} a */ function f(a){}"
                + "/** @type {(string|undefined)} */ var a;"
                + "a && f(a);")
        .run();
  }

  @Test
  public void testControlFlowRestrictsType5() {
    newTest()
        .addSource(
            "/** @param {undefined} a */ function f(a){}"
                + "/** @type {(!Array|undefined)} */ var a;"
                + "a || f(a);")
        .run();
  }

  @Test
  public void testControlFlowRestrictsType6() {
    newTest()
        .addSource(
            "/** @param {undefined} x */ function f(x) {}"
                + "/** @type {(string|undefined)} */ var a;"
                + "a && f(a);")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : string\n"
                + "required: undefined")
        .run();
  }

  @Test
  public void testControlFlowRestrictsType7() {
    newTest()
        .addSource(
            "/** @param {undefined} x */ function f(x) {}"
                + "/** @type {(string|undefined)} */ var a;"
                + "a && f(a);")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : string\n"
                + "required: undefined")
        .run();
  }

  @Test
  public void testControlFlowRestrictsType8() {
    newTest()
        .addSource(
            "/** @param {undefined} a */ function f(a){}"
                + "/** @type {(!Array|undefined)} */ var a;"
                + "if (a || f(a)) {}")
        .run();
  }

  @Test
  public void testControlFlowRestrictsType9() {
    newTest()
        .addSource(
            "/** @param {number?} x\n * @return {number}*/\n"
                + "var f = function(x) {\n"
                + "if (!x || x == 1) { return 1; } else { return x; }\n"
                + "};")
        .run();
  }

  @Test
  public void testControlFlowRestrictsType10() {
    // We should correctly infer that y will be (null|{}) because
    // the loop wraps around.
    newTest()
        .addSource(
            "/** @param {number} x */ function f(x) {}"
                + "function g() {"
                + "  var y = null;"
                + "  for (var i = 0; i < 10; i++) {"
                + "    f(y);"
                + "    if (y != null) {"
                + "      // y is None the first time it goes through this branch\n"
                + "    } else {"
                + "      y = {};"
                + "    }"
                + "  }"
                + "};")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : (null|{})\n"
                + "required: number")
        .run();
  }

  @Test
  public void testControlFlowRestrictsType11() {
    newTest()
        .addSource(
            "/** @param {boolean} x */ function f(x) {}"
                + "function g() {"
                + "  var y = null;"
                + "  if (y != null) {"
                + "    for (var i = 0; i < 10; i++) {"
                + "      f(y);"
                + "    }"
                + "  }"
                + "};")
        .addDiagnostic("condition always evaluates to false\n" + "left : null\n" + "right: null")
        .run();
  }

  @Test
  public void testSwitchCase_primitiveDoesNotAutobox() {
    newTest()
        .addSource(
            "/** @type {!String} */", //
            "var a = new String('foo');",
            "switch (a) { case 'A': }")
        .addDiagnostic(
            lines(
                "case expression doesn't match switch", //
                "found   : string",
                "required: String"))
        .run();
  }

  @Test
  public void testSwitchCase_unknownSwitchExprMatchesAnyCase() {
    newTest()
        .addSource(
            "var a = unknown;", //
            "switch (a) { case 'A':break; case null:break; }")
        .run();
  }

  @Test
  public void testSwitchCase_doesNotAutoboxStringToMatchNullableUnion() {
    newTest()
        .addSource(
            "/** @type {?String} */",
            "var a = unknown;",
            "switch (a) { case 'A':break; case null:break; }")
        .addDiagnostic(
            lines(
                "case expression doesn't match switch",
                "found   : string",
                "required: (String|null)"))
        .run();
  }

  @Test
  public void testSwitchCase_doesNotAutoboxNumberToMatchNullableUnion() {
    newTest()
        .addSource(
            "/** @type {?Number} */",
            "var a = unknown;",
            "switch (a) { case 5:break; case null:break; }")
        .addDiagnostic(
            lines(
                "case expression doesn't match switch",
                "found   : number",
                "required: (Number|null)"))
        .run();
  }

  @Test
  public void testSwitchCase7() {
    // This really tests the inference inside the case.
    newTest()
        .addSource(
            "/**\n"
                + " * @param {number} x\n"
                + " * @return {number}\n"
                + " */\n"
                + "function g(x) { return 5; }"
                + "function f() {"
                + "  var x = {};"
                + "  x.foo = '3';"
                + "  switch (3) { case g(x.foo): return 3; }"
                + "}")
        .addDiagnostic(
            "actual parameter 1 of g does not match formal parameter\n"
                + "found   : string\n"
                + "required: number")
        .run();
  }

  @Test
  public void testSwitchCase8() {
    // This really tests the inference inside the switch clause.
    newTest()
        .addSource(
            "/**\n"
                + " * @param {number} x\n"
                + " * @return {number}\n"
                + " */\n"
                + "function g(x) { return 5; }"
                + "function f() {"
                + "  var x = {};"
                + "  x.foo = '3';"
                + "  switch (g(x.foo)) { case 3: return 3; }"
                + "}")
        .addDiagnostic(
            "actual parameter 1 of g does not match formal parameter\n"
                + "found   : string\n"
                + "required: number")
        .run();
  }

  @Test
  public void testSwitchCase_allowsStructuralMatching() {
    newTest()
        .addSource(
            "/** @record */",
            "class R {",
            "  constructor() {",
            "    /** @type {string} */",
            "    this.str;",
            "  }",
            "}",
            "/** @record */",
            "class S {",
            "  constructor() {",
            "    /** @type {string} */",
            "    this.str;",
            "    /** @type {number} */",
            "    this.num;",
            "  }",
            "}",
            " /**",
            " * @param {!R} r",
            " * @param {!S} s",
            " */",
            "function f(r, s) {",
            "  switch (r) {",
            "    case s:",
            "      return true;",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testImplicitCast1() {
    newTest()
        .addExterns(
            "/** @constructor */ function Element() {};\n"
                + "/** @type {string}\n"
                + "  * @implicitCast */"
                + "Element.prototype.innerHTML;")
        .addSource("(new Element).innerHTML = new Array();")
        .run();
  }

  @Test
  public void testImplicitCast2() {
    newTest()
        .addExterns(
            "/** @constructor */ function Element() {};\n"
                + "/**\n"
                + " * @type {string}\n"
                + " * @implicitCast\n"
                + " */\n"
                + "Element.prototype.innerHTML;\n")
        .addSource(
            "/** @constructor */ function C(e) {\n"
                + "  /** @type {Element} */ this.el = e;\n"
                + "}\n"
                + "C.prototype.method = function() {\n"
                + "  this.el.innerHTML = new Array();\n"
                + "};\n")
        .run();
  }

  @Test
  public void testImplicitCast3() {
    newTest()
        .addExterns(
            "/** @constructor */ function Element() {};",
            "/**",
            " * @type {string}",
            " * @implicitCast",
            " */",
            "Element.prototype.innerHTML;")
        .addSource(
            "/** @param {?Element} element",
            " * @param {string|number} text",
            " */",
            "function f(element, text) {",
            "  element.innerHTML = text;",
            "}",
            "")
        .run();
  }

  @Test
  public void testImplicitCastSubclassAccess() {
    newTest()
        .addExterns(
            "/** @constructor */ function Element() {};\n"
                + "/** @type {string}\n"
                + "  * @implicitCast */"
                + "Element.prototype.innerHTML;"
                + "/** @constructor \n @extends Element */"
                + "function DIVElement() {};")
        .addSource("(new DIVElement).innerHTML = new Array();")
        .run();
  }

  @Test
  public void testImplicitCastNotInExterns() {
    // We issue a warning in CheckJSDoc for @implicitCast not in externs
    newTest()
        .addSource(
            "/** @constructor */ function Element() {};",
            "/**",
            " * @type {string}",
            " * @implicitCast ",
            " */",
            "Element.prototype.innerHTML;",
            "(new Element).innerHTML = new Array();")
        .addDiagnostic(
            lines(
                "assignment to property innerHTML of Element", // preserve new line
                "found   : Array<?>",
                "required: string"))
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
        .addDiagnostic("initializing variable\n" + "found   : number\n" + "required: (Number|null)")
        .run();
  }

  @Test
  public void testNumberUnboxing() {
    newTest()
        .addSource("/** @type {number} */var a = new Number(4);")
        .addDiagnostic("initializing variable\n" + "found   : Number\n" + "required: number")
        .run();
  }

  @Test
  public void testStringAutoboxing() {
    newTest()
        .addSource("/** @type {String} */var a = 'hello';")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: (String|null)")
        .run();
  }

  @Test
  public void testStringUnboxing() {
    newTest()
        .addSource("/** @type {string} */var a = new String('hello');")
        .addDiagnostic("initializing variable\n" + "found   : String\n" + "required: string")
        .run();
  }

  @Test
  public void testBooleanAutoboxing() {
    newTest()
        .addSource("/** @type {Boolean} */var a = true;")
        .addDiagnostic(
            "initializing variable\n" + "found   : boolean\n" + "required: (Boolean|null)")
        .run();
  }

  @Test
  public void testBooleanUnboxing() {
    newTest()
        .addSource("/** @type {boolean} */var a = new Boolean(false);")
        .addDiagnostic("initializing variable\n" + "found   : Boolean\n" + "required: boolean")
        .run();
  }

  @Test
  public void testIIFE1() {
    newTest()
        .addSource(
            "var namespace = {};"
                + "/** @type {number} */ namespace.prop = 3;"
                + "(function(ns) {"
                + "  ns.prop = true;"
                + "})(namespace);")
        .addDiagnostic(
            "assignment to property prop of ns\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testIIFE2() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "(function(ctor) {"
                + "  /** @type {boolean} */ ctor.prop = true;"
                + "})(Foo);"
                + "/** @return {number} */ function f() { return Foo.prop; }")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testIIFE3() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "(function(ctor) {"
                + "  /** @type {boolean} */ ctor.prop = true;"
                + "})(Foo);"
                + "/** @param {number} x */ function f(x) {}"
                + "f(Foo.prop);")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testIIFE4() {
    newTest()
        .addSource(
            "/** @const */ var namespace = {};"
                + "(function(ns) {"
                + "  /**\n"
                + "   * @constructor\n"
                + "   * @param {number} x\n"
                + "   */\n"
                + "   ns.Ctor = function(x) {};"
                + "})(namespace);"
                + "new namespace.Ctor(true);")
        .addDiagnostic(
            "actual parameter 1 of namespace.Ctor "
                + "does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testIIFE5() {
    // TODO(nicksantos): This behavior is currently incorrect.
    // To handle this case properly, we'll need to change how we handle
    // type resolution.
    newTest()
        .addSource(
            "/** @const */ var namespace = {};"
                + "(function(ns) {"
                + "  /**\n"
                + "   * @constructor\n"
                + "   */\n"
                + "   ns.Ctor = function() {};"
                + "   /** @type {boolean} */ ns.Ctor.prototype.bar = true;"
                + "})(namespace);"
                + "/** @param {namespace.Ctor} x\n"
                + "  * @return {number} */ function f(x) { return x.bar; }")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testNotIIFE1() {
    newTest()
        .addSource(
            "/** @param {number} x */ function f(x) {}"
                + "/** @param {...?} x */ function g(x) {}"
                + "g(function(y) { f(y); }, true);")
        .run();
  }

  @Test
  public void testEnums() {
    newTest()
        .addSource(
            "var outer = function() {"
                + "  /** @enum {number} */"
                + "  var Level = {"
                + "    NONE: 0,"
                + "  };"
                + "  /** @type {!Level} */"
                + "  var l = Level.NONE;"
                + "}")
        .run();
  }

  @Test
  public void testStrictInterfaceCheck() {
    newTest()
        .addSource(
            "/** @interface */",
            "function EventTarget() {}",
            "/** @constructor \n * @implements {EventTarget} */",
            "function Node() {}",
            "/** @type {number} */ Node.prototype.index;",
            "/** @param {EventTarget} x \n * @return {string} */",
            "function foo(x) { return x.index; }")
        .addDiagnostic("Property index never defined on EventTarget")
        .run();
  }

  @Test
  public void testTemplateSubtyping_0() {
    // TODO(b/145145406): This is testing that things work despite this bug.
    newTest()
        .addSource(
            // IFoo is a NamedType here.
            "/** @implements {IFoo<number>} */",
            "class Foo { }",
            "",
            "/**",
            " * @template T",
            " * @interface",
            " */",
            "class IFoo { }",
            "",
            "const /** !IFoo<number> */ x = new Foo();")
        .run();
  }

  @Test
  public void testTemplateSubtyping_1() {
    // TOOD(b/139230800): This is testing things work despite this bug.
    newTest()
        .addSource(
            "/**",
            " * @template T",
            " * @interface",
            " */",
            "class IFoo { }",
            "",
            "/** @implements {IFoo<number>} */",
            "class FooA { }",
            "",
            "/** @implements {IFoo<string>} */",
            "class FooB extends FooA { }",
            "",
            "const /** !IFoo<number> */ x = new FooB();",
            "const /** !IFoo<string> */ y = new FooB();")
        .run();
  }

  @Test
  public void testTypedefBeforeUse() {
    newTest()
        .addSource(
            "/** @typedef {Object<string, number>} */"
                + "var map;"
                + "/** @param {(map|function())} isResult */"
                + "var f = function(isResult) {"
                + "    while (true)"
                + "        isResult['t'];"
                + "};")
        .run();
  }

  @Test
  public void testScopedConstructors1() {
    newTest()
        .addSource(
            "function foo1() { "
                + "  /** @constructor */ function Bar() { "
                + "    /** @type {number} */ this.x = 3;"
                + "  }"
                + "}"
                + "function foo2() { "
                + "  /** @constructor */ function Bar() { "
                + "    /** @type {string} */ this.x = 'y';"
                + "  }"
                + "  /** "
                + "   * @param {Bar} b\n"
                + "   * @return {number}\n"
                + "   */"
                + "  function baz(b) { return b.x; }"
                + "}")
        .addDiagnostic("inconsistent return type\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testScopedConstructors2() {
    newTest()
        .addSource(
            "/** @param {Function} f */"
                + "function foo1(f) {"
                + "  /** @param {Function} g */"
                + "  f.prototype.bar = function(g) {};"
                + "}")
        .run();
  }

  @Test
  public void testQualifiedNameInference1() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @type {number?} */ Foo.prototype.bar = null;"
                + "/** @type {number?} */ Foo.prototype.baz = null;"
                + "/** @param {Foo} foo */"
                + "function f(foo) {"
                + "  while (true) {"
                + "    if (!foo.baz) break; "
                + "    foo.bar = null;"
                + "  }"
                +
                // Tests a bug where this condition always evaluated to true.
                "  return foo.bar == null;"
                + "}")
        .run();
  }

  @Test
  public void testQualifiedNameInference2() {
    newTest()
        .addSource(
            "var x = {};"
                + "x.y = c;"
                + "function f(a, b) {"
                + "  if (a) {"
                + "    if (b) "
                + "      x.y = 2;"
                + "    else "
                + "      x.y = 1;"
                + "  }"
                + "  return x.y == null;"
                + "}")
        .run();
  }

  @Test
  public void testQualifiedNameInference3() {
    newTest()
        .addSource(
            "var x = {};"
                + "x.y = c;"
                + "function f(a, b) {"
                + "  if (a) {"
                + "    if (b) "
                + "      x.y = 2;"
                + "    else "
                + "      x.y = 1;"
                + "  }"
                + "  return x.y == null;"
                + "} function g() { x.y = null; }")
        .run();
  }

  @Test
  public void testQualifiedNameInference4() {
    newTest()
        .addSource(
            "/** @param {string} x */ function f(x) {}\n"
                + "/**\n"
                + " * @param {?string} x \n"
                + " * @constructor\n"
                + " */"
                + "function Foo(x) { this.x_ = x; }\n"
                + "Foo.prototype.bar = function() {"
                + "  if (this.x_) { f(this.x_); }"
                + "};")
        .run();
  }

  @Test
  public void testQualifiedNameInference5() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "var ns = {}; "
                + "(function() { "
                + "    /** @param {number} x */ ns.foo = function(x) {}; })();"
                + "(function() { ns.foo(true); })();")
        .addDiagnostic(
            "actual parameter 1 of ns.foo does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testQualifiedNameInference6() {
    newTest()
        .addSource(
            "/** @const */ var ns = {}; "
                + "/** @param {number} x */ ns.foo = function(x) {};"
                + "(function() { "
                + "    ns.foo = function(x) {};"
                + "    ns.foo(true); "
                + "})();")
        .addDiagnostic(
            "actual parameter 1 of ns.foo does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testQualifiedNameInference7() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "var ns = {}; "
                + "(function() { "
                + "  /** @constructor \n * @param {number} x */ "
                + "  ns.Foo = function(x) {};"
                + "  /** @param {ns.Foo} x */ function f(x) {}"
                + "  f(new ns.Foo(true));"
                + "})();")
        .addDiagnostic(
            "actual parameter 1 of ns.Foo does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testQualifiedNameInference8() {
    disableStrictMissingPropertyChecks();
    testClosureTypesMultipleWarnings(
        "var ns = {}; "
            + "(function() { "
            + "  /** @constructor \n * @param {number} x */ "
            + "  ns.Foo = function(x) {};"
            + "})();"
            + "/** @param {ns.Foo} x */ function f(x) {}"
            + "f(new ns.Foo(true));",
        ImmutableList.of(
            "actual parameter 1 of ns.Foo does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number"));
  }

  @Test
  public void testQualifiedNameInference9() {
    newTest()
        .addSource(
            "var ns = {}; "
                + "ns.ns2 = {}; "
                + "(function() { "
                + "  /** @constructor \n * @param {number} x */ "
                + "  ns.ns2.Foo = function(x) {};"
                + "  /** @param {ns.ns2.Foo} x */ function f(x) {}"
                + "  f(new ns.ns2.Foo(true));"
                + "})();")
        .addDiagnostic(
            "actual parameter 1 of ns.ns2.Foo does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testQualifiedNameInference10() {
    newTest()
        .addSource(
            "var ns = {}; "
                + "ns.ns2 = {}; "
                + "(function() { "
                + "  /** @interface */ "
                + "  ns.ns2.Foo = function() {};"
                + "  /** @constructor \n * @implements {ns.ns2.Foo} */ "
                + "  function F() {}"
                + "  (new F());"
                + "})();")
        .run();
  }

  @Test
  public void testQualifiedNameInference11() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "function f() {"
                + "  var x = new Foo();"
                + "  x.onload = function() {"
                + "    x.onload = null;"
                + "  };"
                + "}")
        .run();
  }

  @Test
  public void testQualifiedNameInference12() {
    disableStrictMissingPropertyChecks();
    // We should be able to tell that the two 'this' properties
    // are different.
    newTest()
        .addSource(
            "/** @param {function(this:Object)} x */ function f(x) {}",
            "/** @constructor */ function Foo() {",
            "  /** @type {number} */ this.bar = 3;",
            "  f(function() { this.bar = true; });",
            "}")
        .run();
  }

  @Test
  public void testQualifiedNameInference13() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "function f(z) {"
                + "  var x = new Foo();"
                + "  if (z) {"
                + "    x.onload = function() {};"
                + "  } else {"
                + "    x.onload = null;"
                + "  };"
                + "}")
        .run();
  }

  @Test
  public void testQualifiedNameInference14() {
    // Unconditional blocks don't cause functions to be treated as inferred.
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}",
            "function f(z) {",
            "  var x = new Foo();",
            "  {",
            "    x.onload = function() {};",
            "  }",
            "  {",
            "    x.onload = null;",
            "  };",
            "}")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : null",
                "required: function(): undefined"))
        .run();
  }

  @Test
  public void testScopeQualifiedNamesOnThis() {
    // Ensure that we don't flow-scope qualified names on 'this' too broadly.
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {",
            "  /** @type {!Bar} */",
            "  this.baz = new Bar();",
            "}",
            "Foo.prototype.foo = function() {",
            "  this.baz.bar();",
            "};",
            "/** @constructor */ function Bar() {",
            "  /** @type {!Foo} */",
            "  this.baz = new Foo();",
            "}",
            "Bar.prototype.bar = function() {",
            "  this.baz.foo();",
            "};")
        .run();
  }

  @Test
  public void testSheqRefinedScope() {
    Node n =
        parseAndTypeCheck(
            "/** @constructor */function A() {}\n"
                + "/** @constructor \n @extends A */ function B() {}\n"
                + "/** @return {number} */\n"
                + "B.prototype.p = function() { return 1; }\n"
                + "/** @param {A} a\n @param {B} b */\n"
                + "function f(a, b) {\n"
                + "  b.p();\n"
                + "  if (a === b) {\n"
                + "    b.p();\n"
                + "  }\n"
                + "}");
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
            "/** @constructor */ function Foo() {}\n" + "Foo.prototype.a = 1;" + "(new Foo).a;");

    Node node = n.getLastChild().getFirstChild();
    assertThat(node.getJSType().isUnknownType()).isFalse();
    assertThat(node.getJSType().isNumber()).isTrue();
  }

  @Test
  public void testNew1() {
    newTest().addSource("new 4").addDiagnostic(TypeCheck.NOT_A_CONSTRUCTOR).run();
  }

  @Test
  public void testNew2() {
    newTest()
        .addSource("var Math = {}; new Math()")
        .addDiagnostic(TypeCheck.NOT_A_CONSTRUCTOR)
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
        .addDiagnostic(TypeCheck.NOT_A_CONSTRUCTOR)
        .run();
  }

  @Test
  public void testNew6() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope("/** @constructor */function A(){};" + "var a = new A();");

    JSType aType = p.scope.getVar("a").getType();
    assertThat(aType).isInstanceOf(ObjectType.class);
    ObjectType aObjectType = (ObjectType) aType;
    assertThat(aObjectType.getConstructor().getReferenceName()).isEqualTo("A");
  }

  @Test
  public void testNew7() {
    newTest()
        .addSource(
            "/** @param {Function} opt_constructor */"
                + "function foo(opt_constructor) {"
                + "if (opt_constructor) { new opt_constructor; }"
                + "}")
        .run();
  }

  @Test
  public void testNew8() {
    newTest()
        .addSource(
            "/** @param {Function} opt_constructor */"
                + "function foo(opt_constructor) {"
                + "new opt_constructor;"
                + "}")
        .run();
  }

  @Test
  public void testNew9() {
    newTest()
        .addSource(
            "/** @param {Function} opt_constructor */"
                + "function foo(opt_constructor) {"
                + "new (opt_constructor || Array);"
                + "}")
        .run();
  }

  @Test
  public void testNew10() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @param {Function} opt_constructor */"
                + "goog.Foo = function(opt_constructor) {"
                + "new (opt_constructor || Array);"
                + "}")
        .run();
  }

  @Test
  public void testNew11() {
    newTest()
        .addSource(
            "/** @param {Function} c1 */"
                + "function f(c1) {"
                + "  var c2 = function(){};"
                + "  c1.prototype = new c2;"
                + "}")
        .addDiagnostic(TypeCheck.NOT_A_CONSTRUCTOR)
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
            "/** @constructor */function FooBar(){};" + "var a = new FooBar();");
    TypedVar a = p.scope.getVar("a");

    assertThat(a.getType()).isInstanceOf(ObjectType.class);
    assertThat(a.getType().toString()).isEqualTo("FooBar");
  }

  @Test
  public void testNew14() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope(
            "/** @constructor */var FooBar = function(){};" + "var a = new FooBar();");
    TypedVar a = p.scope.getVar("a");

    assertThat(a.getType()).isInstanceOf(ObjectType.class);
    assertThat(a.getType().toString()).isEqualTo("FooBar");
  }

  @Test
  public void testNew15() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope(
            "var goog = {};"
                + "/** @constructor */goog.A = function(){};"
                + "var a = new goog.A();");
    TypedVar a = p.scope.getVar("a");

    assertThat(a.getType()).isInstanceOf(ObjectType.class);
    assertThat(a.getType().toString()).isEqualTo("goog.A");
  }

  @Test
  public void testNew16() {
    newTest()
        .addSource(
            "/** \n"
                + " * @param {string} x \n"
                + " * @constructor \n"
                + " */"
                + "function Foo(x) {}"
                + "function g() { new Foo(1); }")
        .addDiagnostic(
            "actual parameter 1 of Foo does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testNew17() {
    newTest()
        .addSource("var goog = {}; goog.x = 3; new goog.x")
        .addDiagnostic("cannot instantiate non-constructor")
        .run();
  }

  @Test
  public void testNew18() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @constructor */ goog.F = function() {};"
                + "/** @constructor */ goog.G = goog.F;")
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
            "/** @constructor @abstract */",
            "function Bar() {};",
            "/** @return {function(new:Bar)} */",
            "function foo() {}",
            "var Foo = foo();",
            "var f = new Foo;")
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
            "bad left operand to bitwise operator\n"
                + "found   : Object\n"
                + "required: (boolean|null|number|string|undefined)")
        .run();
  }

  @Test
  public void testBitOperation7() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    newTest().addSource("var x = null; x |= undefined; x &= 3; x ^= '3'; x |= true;").run();
  }

  @Test
  public void testBitOperation8() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    newTest().addSource("var x = void 0; x |= new Number(3);").run();
  }

  @Test
  public void testBitOperation9() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    newTest()
        .addSource("var x = void 0; x |= {};")
        .addDiagnostic(
            "bad right operand to bitwise operator\n"
                + "found   : {}\n"
                + "required: (boolean|null|number|string|undefined)")
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
            "actual parameter 1 of bar does not match formal parameter\n"
                + "found   : string\n"
                + "required: Number")
        .run();
  }

  @Test
  public void testCall3() {
    // We are checking that an unresolved named type can successfully
    // meet with a functional type to produce a callable type.
    newTest()
        .addSource(
            "/** @type {Function|undefined} */var opt_f;"
                + "/** @type {some.unknown.type} */var f1;"
                + "var f2 = opt_f || f1;"
                + "f2();")
        .addDiagnostic("Bad type annotation. Unknown type some.unknown.type")
        .run();
  }

  @Test
  public void testCall3NullishCoalesce() {
    // We are checking that an unresolved named type can successfully
    // meet with a functional type to produce a callable type.
    newTest()
        .addSource(
            "/** @type {Function|undefined} */var opt_f;",
            "/** @type {some.unknown.type} */var f1;",
            "var f2 = opt_f ?? f1;",
            "f2();")
        .addDiagnostic("Bad type annotation. Unknown type some.unknown.type")
        .run();
  }

  @Test
  public void testCall4() {
    newTest()
        .addSource("/**@param {!RegExp} a*/var foo = function bar(a){ bar('abc'); }")
        .addDiagnostic(
            "actual parameter 1 of bar does not match formal parameter\n"
                + "found   : string\n"
                + "required: RegExp")
        .run();
  }

  @Test
  public void testCall5() {
    newTest()
        .addSource("/**@param {!RegExp} a*/var foo = function bar(a){ foo('abc'); }")
        .addDiagnostic(
            "actual parameter 1 of foo does not match formal parameter\n"
                + "found   : string\n"
                + "required: RegExp")
        .run();
  }

  @Test
  public void testCall6() {
    newTest()
        .addSource("/** @param {!Number} foo*/function bar(foo){}" + "bar('abc');")
        .addDiagnostic(
            "actual parameter 1 of bar does not match formal parameter\n"
                + "found   : string\n"
                + "required: Number")
        .run();
  }

  @Test
  public void testCall7() {
    newTest()
        .addSource("/** @param {!RegExp} a*/var foo = function bar(a){};" + "foo('abc');")
        .addDiagnostic(
            "actual parameter 1 of foo does not match formal parameter\n"
                + "found   : string\n"
                + "required: RegExp")
        .run();
  }

  @Test
  public void testCall8() {
    newTest()
        .addSource("/** @type {Function|number} */var f;f();")
        .addDiagnostic("(Function|number) expressions are " + "not callable")
        .run();
  }

  @Test
  public void testCall9() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @constructor */ goog.Foo = function() {};"
                + "/** @param {!goog.Foo} a */ var bar = function(a){};"
                + "bar('abc');")
        .addDiagnostic(
            "actual parameter 1 of bar does not match formal parameter\n"
                + "found   : string\n"
                + "required: goog.Foo")
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
            "/**",
            " * @param {*} x",
            " * @return {number}",
            " */",
            "function f(x, y) {",
            "  return x && x.foo();",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type", // preserve new line
                "found   : *",
                "required: number"))
        .addDiagnostic("Property foo never defined on *" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testCall13() {
    // Test a case where we use inferred types across scopes.
    newTest()
        .addSource(
            "var x;",
            "function useX() { var /** string */ str = x(); }",
            "function setX() { x = /** @return {number} */ () => 3; }")
        .addDiagnostic(
            lines(
                "initializing variable", // preserve new line
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testFunctionCall1() {
    newTest()
        .addSource("/** @param {number} x */ var foo = function(x) {};" + "foo.call(null, 3);")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall2() {
    newTest()
        .addSource("/** @param {number} x */ var foo = function(x) {};" + "foo.call(null, 'bar');")
        .addDiagnostic(
            "actual parameter 2 of foo.call does not match formal parameter\n"
                + "found   : string\n"
                + "required: number")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall3() {
    newTest()
        .addSource(
            "/** @param {number} x \n * @constructor */ "
                + "var Foo = function(x) { this.bar.call(null, x); };"
                + "/** @type {function(number)} */ Foo.prototype.bar;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall4() {
    newTest()
        .addSource(
            "/** @param {string} x \n * @constructor */ "
                + "var Foo = function(x) { this.bar.call(null, x); };"
                + "/** @type {function(number)} */ Foo.prototype.bar;")
        .addDiagnostic(
            "actual parameter 2 of this.bar.call "
                + "does not match formal parameter\n"
                + "found   : string\n"
                + "required: number")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall5() {
    newTest()
        .addSource(
            "/** @param {Function} handler \n * @constructor */ "
                + "var Foo = function(handler) { handler.call(this, x); };")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall6() {
    newTest()
        .addSource(
            "/** @param {Function} handler \n * @constructor */ "
                + "var Foo = function(handler) { handler.apply(this, x); };")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall7() {
    newTest()
        .addSource(
            "/** @param {Function} handler \n * @param {Object} opt_context */ "
                + "var Foo = function(handler, opt_context) { "
                + "  handler.call(opt_context, x);"
                + "};")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall8() {
    newTest()
        .addSource(
            "/** @param {Function} handler \n * @param {Object} opt_context */ "
                + "var Foo = function(handler, opt_context) { "
                + "  handler.apply(opt_context, x);"
                + "};")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionCall9() {
    newTest()
        .addSource(
            "/** @constructor\n * @template T\n **/ function Foo() {}\n"
                + "/** @param {T} x */ Foo.prototype.bar = function(x) {}\n"
                + "var foo = /** @type {Foo<string>} */ (new Foo());\n"
                + "foo.bar(3);")
        .addDiagnostic(
            "actual parameter 1 of Foo.prototype.bar does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind1() {
    newTest()
        .addSource(
            "/** @type {function(string, number): boolean} */"
                + "function f(x, y) { return true; }"
                + "f.bind(null, 3);")
        .addDiagnostic(
            "actual parameter 2 of f.bind does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind2() {
    newTest()
        .addSource(
            "/** @type {function(number): boolean} */"
                + "function f(x) { return true; }"
                + "f(f.bind(null, 3)());")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind3() {
    newTest()
        .addSource(
            "/** @type {function(number, string): boolean} */"
                + "function f(x, y) { return true; }"
                + "f.bind(null, 3)(true);")
        .addDiagnostic(
            "actual parameter 1 of function does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: string")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind4() {
    this.newTest()
        .addSource(
            "/** @param {...number} x */", //
            "function f(x) {}",
            "f.bind(null, 3, 3, 3)(true);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of function does not match formal parameter",
                "found   : boolean",
                "required: number"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind5() {
    this.newTest()
        .addSource(
            "/** @param {...number} x */", //
            "function f(x) {}",
            "f.bind(null, true)(3, 3, 3);")
        .addDiagnostic(
            lines(
                "actual parameter 2 of f.bind does not match formal parameter",
                "found   : boolean",
                "required: number"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind6() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function MyType() {",
            "  /** @type {number} */",
            "  this.x = 0;",
            "  var f = function() {",
            "    this.x = 'str';",
            "  }.bind(this);",
            "}")
        .addDiagnostic(
            lines(
                "assignment to property x of MyType", //
                "found   : string",
                "required: number"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind7() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function MyType() {",
            "  /** @type {number} */",
            "  this.x = 0;",
            "}",
            "var m = new MyType;",
            "(function f() {this.x = 'str';}).bind(m);")
        .addDiagnostic(
            lines(
                "assignment to property x of MyType", //
                "found   : string",
                "required: number"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind8() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function MyType() {}",
            "",
            "/** @constructor */",
            "function AnotherType() {}",
            "AnotherType.prototype.foo = function() {};",
            "",
            "/** @type {?} */",
            "var m = new MyType;",
            "(function f() {this.foo();}).bind(m);")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testFunctionBind9() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function MyType() {}",
            "",
            "/** @constructor */",
            "function AnotherType() {}",
            "AnotherType.prototype.foo = function() {};",
            "",
            "var m = new MyType;",
            "(function f() {this.foo();}).bind(m);")
        .addDiagnostic(TypeCheck.INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testGoogBind1() {
    testClosureTypes(
        "goog.bind = function(var_args) {};"
            + "/** @type {function(number): boolean} */"
            + "function f(x, y) { return true; }"
            + "f(goog.bind(f, null, 'x')());",
        "actual parameter 1 of f does not match formal parameter\n"
            + "found   : boolean\n"
            + "required: number");
  }

  @Test
  public void testGoogBind2() {
    // TODO(nicksantos): We do not currently type-check the arguments
    // of the goog.bind.
    testClosureTypes(
        "goog.bind = function(var_args) {};"
            + "/** @type {function(boolean): boolean} */"
            + "function f(x, y) { return true; }"
            + "f(goog.bind(f, null, 'x')());",
        null);
  }

  @Test
  public void testCast2() {
    // can upcast to a base type.
    newTest()
        .addSource(
            "/** @constructor */function base() {}\n"
                + "/** @constructor\n @extends {base} */function derived() {}\n"
                + "/** @type {base} */ var baz = new derived();\n")
        .run();
  }

  @Test
  public void testCast3() {
    // cannot downcast
    newTest()
        .addSource(
            "/** @constructor */function base() {}\n"
                + "/** @constructor @extends {base} */function derived() {}\n"
                + "/** @type {!derived} */ var baz = new base();\n")
        .addDiagnostic("initializing variable\n" + "found   : base\n" + "required: derived")
        .run();
  }

  @Test
  public void testCast3a() {
    // cannot downcast
    newTest()
        .addSource(
            "/** @constructor */function Base() {}\n"
                + "/** @constructor @extends {Base} */function Derived() {}\n"
                + "var baseInstance = new Base();"
                + "/** @type {!Derived} */ var baz = baseInstance;\n")
        .addDiagnostic("initializing variable\n" + "found   : Base\n" + "required: Derived")
        .run();
  }

  @Test
  public void testCast4() {
    // downcast must be explicit
    newTest()
        .addSource(
            "/** @constructor */function base() {}\n"
                + "/** @constructor\n * @extends {base} */function derived() {}\n"
                + "/** @type {!derived} */ var baz = "
                + "/** @type {!derived} */(new base());\n")
        .run();
  }

  @Test
  public void testCast4Types() {
    // downcast must be explicit
    Node root =
        parseAndTypeCheck(
            "/** @constructor */function base() {}\n"
                + "/** @constructor\n * @extends {base} */function derived() {}\n"
                + "/** @type {!derived} */ var baz = "
                + "/** @type {!derived} */(new base());\n");
    Node castedExprNode = root.getLastChild().getFirstFirstChild().getFirstChild();
    assertThat(castedExprNode.getJSType().toString()).isEqualTo("derived");
    assertThat(castedExprNode.getJSTypeBeforeCast().toString()).isEqualTo("base");
  }

  @Test
  public void testCast5() {
    // cannot explicitly cast to an unrelated type
    newTest()
        .addSource(
            "/** @constructor */function foo() {}\n"
                + "/** @constructor */function bar() {}\n"
                + "var baz = /** @type {!foo} */(new bar);\n")
        .addDiagnostic(
            "invalid cast - must be a subtype or supertype\n" + "from: bar\n" + "to  : foo")
        .run();
  }

  @Test
  public void testCast5a() {
    // cannot explicitly cast to an unrelated type
    newTest()
        .addSource(
            "/** @constructor */function foo() {}\n"
                + "/** @constructor */function bar() {}\n"
                + "var barInstance = new bar;\n"
                + "var baz = /** @type {!foo} */(barInstance);\n")
        .addDiagnostic(
            "invalid cast - must be a subtype or supertype\n" + "from: bar\n" + "to  : foo")
        .run();
  }

  @Test
  public void testCast6() {
    // can explicitly cast to a subtype or supertype
    newTest()
        .addSource(
            "/** @constructor */function foo() {}\n"
                + "/** @constructor \n @extends foo */function bar() {}\n"
                + "var baz = /** @type {!bar} */(new bar);\n"
                + "var baz = /** @type {!foo} */(new foo);\n"
                + "var baz = /** @type {bar} */(new bar);\n"
                + "var baz = /** @type {foo} */(new foo);\n"
                + "var baz = /** @type {!foo} */(new bar);\n"
                + "var baz = /** @type {!bar} */(new foo);\n"
                + "var baz = /** @type {foo} */(new bar);\n"
                + "var baz = /** @type {bar} */(new foo);\n")
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
        .addSource("var foo = {};" + "function f() { return /** @type {foo} */ (new Object()); }")
        .addDiagnostic("Bad type annotation. Unknown type foo")
        .run();
  }

  @Test
  public void testCast10() {
    newTest()
        .addSource(
            "var foo = function() {};"
                + "function f() { return /** @type {foo} */ (new Object()); }")
        .addDiagnostic("Bad type annotation. Unknown type foo")
        .run();
  }

  @Test
  public void testCast11() {
    newTest()
        .addSource(
            "var goog = {}; goog.foo = {};"
                + "function f() { return /** @type {goog.foo} */ (new Object()); }")
        .addDiagnostic("Bad type annotation. Unknown type goog.foo")
        .run();
  }

  @Test
  public void testCast12() {
    newTest()
        .addSource(
            "var goog = {}; goog.foo = function() {};"
                + "function f() { return /** @type {goog.foo} */ (new Object()); }")
        .addDiagnostic("Bad type annotation. Unknown type goog.foo")
        .run();
  }

  @Test
  public void testCast13() {
    // In a typespace world, types and values may collide on the same symbol.
    testClosureTypes(
        "goog.forwardDeclare('goog.foo');"
            + "goog.foo = function() {};"
            + "function f() { return /** @type {goog.foo} */ (new Object()); }",
        null);
  }

  @Test
  public void testCast14() {
    // Test to make sure that the forward-declaration still prevents
    // some warnings.
    testClosureTypes(
        "goog.forwardDeclare('goog.bar');"
            + "function f() { return /** @type {goog.bar} */ (new Object()); }",
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
    disableStrictMissingPropertyChecks();

    newTest()
        .addSource(
            "for (var i = 0; i < 10; i++) {",
            "var x = /** @type {Object|number} */ ({foo: 3});",
            "/** @param {number} x */ function f(x) {}",
            "f(x.foo);",
            "f([].foo);",
            "}")
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
            "for (var i = 0; i < 10; i++) {",
            "var x = /** @type {{foo:number}}|number} */ ({foo: 3});",
            "/** @param {number} x */ function f(x) {}",
            "f(x.foo);",
            "f([].foo);",
            "}")
        .addDiagnostic("Property foo never defined on Array<?>")
        .run();
  }

  @Test
  public void testCast16() {
    // A type cast should not invalidate the checks on the members
    newTest()
        .addSource(
            "for (var i = 0; i < 10; i++) {"
                + "var x = /** @type {Object|number} */ ("
                + "  {/** @type {string} */ foo: 3});"
                + "}")
        .addDiagnostic(
            "assignment to property foo of {foo: string}\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testCast17a() {
    // Mostly verifying that rhino actually understands these JsDocs.
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {} \n"
                + "/** @type {Foo} */ var x = /** @type {Foo} */ (y)")
        .run();
  }

  @Test
  public void testCast17b() {
    // Mostly verifying that rhino actually understands these JsDocs.
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {} \n"
                + "/** @type {Foo} */ var x = /** @type {Foo} */ ({})")
        .run();
  }

  @Test
  public void testCast19() {
    newTest()
        .addSource(
            "var x = 'string';\n" + "/** @type {number} */\n" + "var y = /** @type {number} */(x);")
        .addDiagnostic(
            "invalid cast - must be a subtype or supertype\n" + "from: string\n" + "to  : number")
        .run();
  }

  @Test
  public void testCast20() {
    newTest()
        .addSource(
            "/** @enum {boolean|null} */\n"
                + "var X = {"
                + "  AA: true,"
                + "  BB: false,"
                + "  CC: null"
                + "};\n"
                + "var y = /** @type {X} */(true);")
        .run();
  }

  @Test
  public void testCast21() {
    newTest()
        .addSource(
            "/** @enum {boolean|null} */\n"
                + "var X = {"
                + "  AA: true,"
                + "  BB: false,"
                + "  CC: null"
                + "};\n"
                + "var value = true;\n"
                + "var y = /** @type {X} */(value);")
        .run();
  }

  @Test
  public void testCast22() {
    newTest()
        .addSource("var x = null;\n" + "var y = /** @type {number} */(x);")
        .addDiagnostic(
            "invalid cast - must be a subtype or supertype\n" + "from: null\n" + "to  : number")
        .run();
  }

  @Test
  public void testCast23() {
    newTest().addSource("var x = null;\n" + "var y = /** @type {Number} */(x);").run();
  }

  @Test
  public void testCast24() {
    newTest()
        .addSource("var x = undefined;\n" + "var y = /** @type {number} */(x);")
        .addDiagnostic(
            "invalid cast - must be a subtype or supertype\n"
                + "from: undefined\n"
                + "to  : number")
        .run();
  }

  @Test
  public void testCast25() {
    newTest()
        .addSource("var x = undefined;\n" + "var y = /** @type {number|undefined} */(x);")
        .run();
  }

  @Test
  public void testCast26() {
    newTest()
        .addSource(
            "function fn(dir) {\n"
                + "  var node = dir ? 1 : 2;\n"
                + "  fn(/** @type {number} */ (node));\n"
                + "}")
        .run();
  }

  @Test
  public void testCast27() {
    // C doesn't implement I but a subtype might.
    newTest()
        .addSource(
            "/** @interface */ function I() {}\n"
                + "/** @constructor */ function C() {}\n"
                + "var x = new C();\n"
                + "var y = /** @type {I} */(x);")
        .run();
  }

  @Test
  public void testCast27a() {
    // C doesn't implement I but a subtype might.
    newTest()
        .addSource(
            "/** @interface */ function I() {}\n"
                + "/** @constructor */ function C() {}\n"
                + "/** @type {C} */ var x ;\n"
                + "var y = /** @type {I} */(x);")
        .run();
  }

  @Test
  public void testCast28() {
    // C doesn't implement I but a subtype might.
    newTest()
        .addSource(
            "/** @interface */ function I() {}\n"
                + "/** @constructor */ function C() {}\n"
                + "/** @type {!I} */ var x;\n"
                + "var y = /** @type {C} */(x);")
        .run();
  }

  @Test
  public void testCast28a() {
    // C doesn't implement I but a subtype might.
    newTest()
        .addSource(
            "/** @interface */ function I() {}\n"
                + "/** @constructor */ function C() {}\n"
                + "/** @type {I} */ var x;\n"
                + "var y = /** @type {C} */(x);")
        .run();
  }

  @Test
  public void testCast29a() {
    // C doesn't implement the record type but a subtype might.
    newTest()
        .addSource(
            "/** @constructor */ function C() {}\n"
                + "var x = new C();\n"
                + "var y = /** @type {{remoteJids: Array, sessionId: string}} */(x);")
        .run();
  }

  @Test
  public void testCast29b() {
    // C doesn't implement the record type but a subtype might.
    newTest()
        .addSource(
            "/** @constructor */ function C() {}\n"
                + "/** @type {C} */ var x;\n"
                + "var y = /** @type {{prop1: Array, prop2: string}} */(x);")
        .run();
  }

  @Test
  public void testCast29c() {
    // C doesn't implement the record type but a subtype might.
    newTest()
        .addSource(
            "/** @constructor */ function C() {}\n"
                + "/** @type {{remoteJids: Array, sessionId: string}} */ var x ;\n"
                + "var y = /** @type {C} */(x);")
        .run();
  }

  @Test
  public void testCast30() {
    // Should be able to cast to a looser return type
    newTest()
        .addSource(
            "/** @constructor */ function C() {}\n"
                + "/** @type {function():string} */ var x ;\n"
                + "var y = /** @type {function():?} */(x);")
        .run();
  }

  @Test
  public void testCast31() {
    // Should be able to cast to a tighter parameter type
    newTest()
        .addSource(
            "/** @constructor */ function C() {}\n"
                + "/** @type {function(*)} */ var x ;\n"
                + "var y = /** @type {function(string)} */(x);")
        .run();
  }

  @Test
  public void testCast32() {
    newTest()
        .addSource(
            "/** @constructor */ function C() {}\n"
                + "/** @type {Object} */ var x ;\n"
                + "var y = /** @type {null|{length:number}} */(x);")
        .run();
  }

  @Test
  public void testCast33a() {
    // null and void should be assignable to any type that accepts one or the
    // other or both.
    newTest()
        .addSource(
            "/** @constructor */ function C() {}\n"
                + "/** @type {null|undefined} */ var x ;\n"
                + "var y = /** @type {string?|undefined} */(x);")
        .run();
  }

  @Test
  public void testCast33b() {
    // null and void should be assignable to any type that accepts one or the
    // other or both.
    newTest()
        .addSource(
            "/** @constructor */ function C() {}\n"
                + "/** @type {null|undefined} */ var x ;\n"
                + "var y = /** @type {string|undefined} */(x);")
        .run();
  }

  @Test
  public void testCast33c() {
    // null and void should be assignable to any type that accepts one or the
    // other or both.
    newTest()
        .addSource(
            "/** @constructor */ function C() {}\n"
                + "/** @type {null|undefined} */ var x ;\n"
                + "var y = /** @type {string?} */(x);")
        .run();
  }

  @Test
  public void testCast33d() {
    // null and void should be assignable to any type that accepts one or the
    // other or both.
    newTest()
        .addSource(
            "/** @constructor */ function C() {}\n"
                + "/** @type {null|undefined} */ var x ;\n"
                + "var y = /** @type {null} */(x);")
        .run();
  }

  @Test
  public void testCast34a() {
    newTest()
        .addSource(
            "/** @constructor */ function C() {}\n"
                + "/** @type {Object} */ var x ;\n"
                + "var y = /** @type {Function} */(x);")
        .run();
  }

  @Test
  public void testCast34b() {
    newTest()
        .addSource(
            "/** @constructor */ function C() {}\n"
                + "/** @type {Function} */ var x ;\n"
                + "var y = /** @type {Object} */(x);")
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
            "const foo = {bar: {}};",
            "const bar = foo.bar;",
            "bar.Class = class {",
            "  /** @return {number} */",
            "  id() { return 0; }",
            "};",
            // Because `foo.bar.Class = ...` was never directly assigned, the type 'foo.bar.Class'
            // is not in the JSTypeRegistry. It's resolved through NamedType#resolveViaProperty.
            // The same thing would have occurred if we assigned 'foo.bar.Class = ...' then
            // referred to '!bar.Class' in the JSDoc.
            // Verify that type inference correctly infers the id property's type.

            "const /** null */ n = /** @type {!foo.bar.Class} */ (unknownVar).id;")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : function(this:bar.Class): number",
                "required: null"))
        .run();
  }

  @Test
  public void testNestedCasts() {
    newTest()
        .addSource(
            "/** @constructor */var T = function() {};\n"
                + "/** @constructor */var V = function() {};\n"
                + "/**\n"
                + "* @param {boolean} b\n"
                + "* @return {T|V}\n"
                + "*/\n"
                + "function f(b) { return b ? new T() : new V(); }\n"
                + "/**\n"
                + "* @param {boolean} b\n"
                + "* @return {boolean|undefined}\n"
                + "*/\n"
                + "function g(b) { return b ? true : undefined; }\n"
                + "/** @return {T} */\n"
                + "function h() {\n"
                + "return /** @type {T} */ (f(/** @type {boolean} */ (g(true))));\n"
                + "}")
        .run();
  }

  @Test
  public void testNativeCast1() {
    newTest()
        .addSource("/** @param {number} x */ function f(x) {}" + "f(String(true));")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : string\n"
                + "required: number")
        .run();
  }

  @Test
  public void testNativeCast2() {
    newTest()
        .addSource("/** @param {string} x */ function f(x) {}" + "f(Number(true));")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testNativeCast3() {
    newTest()
        .addSource("/** @param {number} x */ function f(x) {}" + "f(Boolean(''));")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testNativeCast4() {
    newTest()
        .addSource(
            "/** @param {number} x */ function f(x) {}", //
            "f(Array(1));")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : Array",
                "required: number"))
        .run();
  }

  @Test
  public void testBadConstructorCall() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}", //
            "Foo();")
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
            "function f() {",
            "  return (",
            "      typeof 123 == 'number' ||",
            "      typeof 123 == 'string' ||",
            "      typeof 123 == 'boolean' ||",
            "      typeof 123 == 'undefined' ||",
            "      typeof 123 == 'function' ||",
            "      typeof 123 == 'object' ||",
            "      typeof 123 == 'symbol' ||",
            "      typeof 123 == 'unknown'); }")
        .run();
  }

  @Test
  public void testConstDecl1() {
    newTest()
        .addSource(
            "/** @param {?number} x \n @return {boolean} */"
                + "function f(x) { "
                + "  if (x) { /** @const */ var y = x; return y } return true; "
                + "}")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: boolean")
        .run();
  }

  @Test
  public void testConstDecl2() {
    newTest()
        .addSource(
            "/** @param {?number} x */"
                + "function f(x) { "
                + "  if (x) {"
                + "    /** @const */ var y = x; "
                + "    /** @return {boolean} */ function g() { return y; } "
                + "  }"
                + "}")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: boolean")
        .run();
  }

  @Test
  public void testConstructorType1() {
    newTest()
        .addSource("/**@constructor*/function Foo(){}" + "/**@type{!Foo}*/var f = new Date();")
        .addDiagnostic("initializing variable\n" + "found   : Date\n" + "required: Foo")
        .run();
  }

  @Test
  public void testConstructorType2() {
    newTest()
        .addSource(
            "/**@constructor*/function Foo(){\n"
                + "/**@type{Number}*/this.bar = new Number(5);\n"
                + "}\n"
                + "/**@type{Foo}*/var f = new Foo();\n"
                + "/**@type{Number}*/var n = f.bar;")
        .run();
  }

  @Test
  public void testConstructorType3() {
    // Reverse the declaration order so that we know that Foo is getting set
    // even on an out-of-order declaration sequence.
    newTest()
        .addSource(
            "/**@type{Foo}*/var f = new Foo();\n"
                + "/**@type{Number}*/var n = f.bar;"
                + "/**@constructor*/function Foo(){\n"
                + "/**@type{Number}*/this.bar = new Number(5);\n"
                + "}\n")
        .run();
  }

  @Test
  public void testConstructorType4() {
    newTest()
        .addSource(
            "/**@constructor*/function Foo(){\n"
                + "/**@type{!Number}*/this.bar = new Number(5);\n"
                + "}\n"
                + "/**@type{!Foo}*/var f = new Foo();\n"
                + "/**@type{!String}*/var n = f.bar;")
        .addDiagnostic("initializing variable\n" + "found   : Number\n" + "required: String")
        .run();
  }

  @Test
  public void testConstructorType5() {
    newTest().addSource("/**@constructor*/function Foo(){}\n" + "if (Foo){}\n").run();
  }

  @Test
  public void testConstructorType6() {
    newTest()
        .addSource(
            "/** @constructor */\n"
                + "function bar() {}\n"
                + "function _foo() {\n"
                + " /** @param {bar} x */\n"
                + "  function f(x) {}\n"
                + "}")
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
            "var ns = {};"
                + "ns.create = function() { return function() {}; };"
                + "/** @constructor */ ns.Foo = ns.create();"
                + "ns.Foo.prototype = {x: 0, y: 0};"
                + "/**\n"
                + " * @param {ns.Foo} foo\n"
                + " * @return {string}\n"
                + " */\n"
                + "function f(foo) {"
                + "  return foo.x;"
                + "}")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testConstructorType9() {
    newTest()
        .addSource(
            "var ns = {};"
                + "ns.create = function() { return function() {}; };"
                + "ns.extend = function(x) { return x; };"
                + "/** @constructor */ ns.Foo = ns.create();"
                + "ns.Foo.prototype = ns.extend({x: 0, y: 0});"
                + "/**\n"
                + " * @param {ns.Foo} foo\n"
                + " * @return {string}\n"
                + " */\n"
                + "function f(foo) {"
                + "  return foo.x;"
                + "}")
        .run();
  }

  @Test
  public void testConstructorType10() {
    newTest()
        .addSource(
            "/** @constructor */"
                + "function NonStr() {}"
                + "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " * @extends{NonStr}\n"
                + " */"
                + "function NonStrKid() {}")
        .run();
  }

  @Test
  public void testConstructorType11() {
    newTest()
        .addSource(
            "/** @constructor */"
                + "function NonDict() {}"
                + "/**\n"
                + " * @constructor\n"
                + " * @dict\n"
                + " * @extends{NonDict}\n"
                + " */"
                + "function NonDictKid() {}")
        .run();
  }

  @Test
  public void testConstructorType12() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Bar() {}\n"
                + "Bar.prototype = {};\n")
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
            "var ns = {};"
                + "/** @constructor */ ns.Foo = function() {"
                + "  this.bar(3, 5);"
                + "};"
                + "ns.Foo.prototype = {"
                + "  bar: function(x) {}"
                + "};")
        .addDiagnostic(
            "Function ns.Foo.prototype.bar: called with 2 argument(s). "
                + "Function requires at least 1 argument(s) and no more "
                + "than 1 argument(s).")
        .run();
  }

  @Test
  public void testAnonymousPrototype2() {
    newTest()
        .addSource(
            "/** @interface */ var Foo = function() {};"
                + "Foo.prototype = {"
                + "  foo: function(x) {}"
                + "};"
                + "/**\n"
                + " * @constructor\n"
                + " * @implements {Foo}\n"
                + " */ var Bar = function() {};")
        .addDiagnostic("property foo on interface Foo is not implemented by type Bar")
        .run();
  }

  @Test
  public void testAnonymousType1() {
    newTest()
        .addSource(
            "function f() { return {}; }", //
            "/** @constructor */",
            "f().bar = function() {};")
        .run();
  }

  @Test
  public void testAnonymousType2() {
    newTest()
        .addSource(
            "function f() { return {}; }" + "/** @interface */\n" + "f().bar = function() {};")
        .run();
  }

  @Test
  public void testAnonymousType3() {
    newTest()
        .addSource("function f() { return {}; }" + "/** @enum */\n" + "f().bar = {FOO: 1};")
        .run();
  }

  @Test
  public void testBang1() {
    newTest()
        .addSource("/** @param {Object} x\n@return {!Object} */\n" + "function f(x) { return x; }")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (Object|null)\n" + "required: Object")
        .run();
  }

  @Test
  public void testBang2() {
    newTest()
        .addSource(
            "/** @param {Object} x\n@return {!Object} */\n"
                + "function f(x) { return x ? x : new Object(); }")
        .run();
  }

  @Test
  public void testBang3() {
    newTest()
        .addSource(
            "/** @param {Object} x\n@return {!Object} */\n"
                + "function f(x) { return /** @type {!Object} */ (x); }")
        .run();
  }

  @Test
  public void testBang4() {
    newTest()
        .addSource(
            "/**@param {Object} x\n@param {Object} y\n@return {boolean}*/\n"
                + "function f(x, y) {\n"
                + "if (typeof x != 'undefined') { return x == y; }\n"
                + "else { return x != y; }\n}")
        .run();
  }

  @Test
  public void testBang5() {
    newTest()
        .addSource(
            "/**@param {Object} x\n@param {Object} y\n@return {boolean}*/\n"
                + "function f(x, y) { return !!x && x == y; }")
        .run();
  }

  @Test
  public void testBang6() {
    newTest()
        .addSource("/** @param {Object?} x\n@return {Object} */\n" + "function f(x) { return x; }")
        .run();
  }

  @Test
  public void testBang7() {
    newTest()
        .addSource(
            "/**@param {(Object|string|null)} x\n"
                + "@return {(Object|string)}*/function f(x) { return x; }")
        .run();
  }

  @Test
  public void testDefinePropertyOnNullableObject1() {
    // checking loose property behavior
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @type {Object} */ var n = {};",
            "/** @type {number} */ n.x = 1;",
            "/** @return {boolean} */ function f() { return n.x; }")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: boolean"))
        .run();
  }

  @Test
  public void testDefinePropertyOnNullableObject1a() {
    newTest()
        .addSource(
            "/** @const */ var n = {};",
            "/** @type {number} */ n.x = 1;",
            "/** @return {boolean} */function f() { return n.x; }")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: boolean"))
        .run();
  }

  @Test
  public void testDefinePropertyOnObject() {
    // checking loose property behavior
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @type {!Object} */ var n = {};",
            "/** @type {number} */ n.x = 1;",
            "/** @return {boolean} */function f() { return n.x; }")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: boolean"))
        .run();
  }

  @Test
  public void testDefinePropertyOnNullableObject2() {
    // checking loose property behavior
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @constructor */ var T = function() {};\n"
                + "/** @param {T} t\n@return {boolean} */function f(t) {\n"
                + "t.x = 1; return t.x; }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: boolean")
        .run();
  }

  @Test
  public void testDefinePropertyOnNullableObject2b() {
    newTest()
        .addSource(
            "/** @constructor */ var T = function() {};",
            "/** @param {T} t */function f(t) { t.x = 1; }")
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
            "/**",
            "* @param {Object} co",
            " * @return {Object}",
            " */",
            "function inst(co) {",
            " /** @constructor */",
            " var c = function() {};",
            " c.prototype = co.prototype;",
            " return new c;",
            "}")
        .addDiagnostic("Property prototype never defined on Object")
        .run();
  }

  @Test
  public void testUnknownPrototypeChain2() {
    newTest()
        .addSource(
            "/**",
            " * @param {Function} co",
            " * @return {Object}",
            " */",
            "function inst(co) {",
            " /** @constructor */",
            " var c = function() {};",
            " c.prototype = co.prototype;",
            " return new c;",
            "}")
        .run();
  }

  @Test
  public void testNamespacedConstructor() {
    Node root =
        parseAndTypeCheck(
            "var goog = {};"
                + "/** @constructor */ goog.MyClass = function() {};"
                + "/** @return {!goog.MyClass} */ "
                + "function foo() { return new goog.MyClass(); }");

    JSType typeOfFoo = root.getLastChild().getJSType();
    assertType(typeOfFoo).isInstanceOf(FunctionType.class);

    JSType retType = ((FunctionType) typeOfFoo).getReturnType();
    assertType(retType).isInstanceOf(ObjectType.class);
    assertThat(((ObjectType) retType).getReferenceName()).isEqualTo("goog.MyClass");
  }

  @Test
  public void testComplexNamespace() {
    String js = "var goog = {};" + "goog.foo = {};" + "goog.foo.bar = 5;";

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
    disableStrictMissingPropertyChecks();
    Node js1Node =
        parseAndTypeCheck(
            DEFAULT_EXTERNS, "/** @constructor */function A() {}" + "A.prototype.m1 = 5");

    ObjectType instanceType = getInstanceType(js1Node);
    assertHasXMorePropertiesThanNativeObject(instanceType, 1);
    checkObjectType(instanceType, "m1", getNativeNumberType());
  }

  @Test
  public void testAddingMethodsUsingPrototypeIdiomComplexNamespace1() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope(
            DEFAULT_EXTERNS,
            "var goog = {};"
                + "goog.A = /** @constructor */function() {};"
                + "/** @type {number} */goog.A.prototype.m1 = 5");

    testAddingMethodsUsingPrototypeIdiomComplexNamespace(p);
  }

  @Test
  public void testAddingMethodsUsingPrototypeIdiomComplexNamespace2() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope(
            DEFAULT_EXTERNS,
            "var goog = {};"
                + "/** @constructor */goog.A = function() {};"
                + "/** @type {number} */goog.A.prototype.m1 = 5");

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
            "/** @constructor */function A() {}" + "A.prototype = {m1: 5, m2: true}");

    ObjectType instanceType = getInstanceType(js1Node);
    assertHasXMorePropertiesThanNativeObject(instanceType, 2);
    checkObjectType(instanceType, "m1", getNativeNumberType());
    checkObjectType(instanceType, "m2", getNativeBooleanType());
  }

  @Test
  public void testDontAddMethodsIfNoConstructor() {
    Node js1Node = parseAndTypeCheck("function A() {}" + "A.prototype = {m1: 5, m2: true}");

    JSType functionAType = js1Node.getFirstChild().getJSType();
    assertThat(functionAType.toString()).isEqualTo("function(): undefined");
    assertTypeEquals(getNativeUnknownType(), getNativeFunctionType().getPropertyType("m1"));
    assertTypeEquals(getNativeUnknownType(), getNativeFunctionType().getPropertyType("m2"));
  }

  @Test
  public void testFunctionAssignement() {
    newTest()
        .addSource(
            "/**"
                + "* @param {string} ph0"
                + "* @param {string} ph1"
                + "* @return {string}"
                + "*/"
                + "function MSG_CALENDAR_ACCESS_ERROR(ph0, ph1) {return ''}"
                + "/** @type {Function} */"
                + "var MSG_CALENDAR_ADD_ERROR = MSG_CALENDAR_ACCESS_ERROR;")
        .run();
  }

  @Test
  public void testAddMethodsPrototypeTwoWays() {
    Node js1Node =
        parseAndTypeCheck(
            DEFAULT_EXTERNS,
            "/** @constructor */function A() {}"
                + "A.prototype = {m1: 5, m2: true};"
                + "A.prototype.m3 = 'third property!';");

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
            "/** @constructor */function A() {\n"
                + "  /** @type {string} */ this.m1;\n"
                + "  /** @type {Object?} */ this.m2 = {};\n"
                + "  /** @type {boolean} */ this.m3;\n"
                + "}\n"
                + "/** @type {string} */ A.prototype.m4;\n"
                + "/** @type {number} */ A.prototype.m5 = 0;\n"
                + "/** @type {boolean} */ A.prototype.m6;\n");

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
            lines(
                "/** @constructor */ var String = function(opt_str) {};",
                "(new String(\"x\")).charAt(0)"));
    assertTypeEquals(getNativeStringType(), node.getLastChild().getFirstChild().getJSType());
  }

  @Test
  public void testExtendBuiltInType1() {
    String externs =
        "/** @constructor */ var String = function(opt_str) {};\n"
            + "/**\n"
            + "* @param {number} start\n"
            + "* @param {number} opt_length\n"
            + "* @return {string}\n"
            + "*/\n"
            + "String.prototype.substr = function(start, opt_length) {};\n";
    Node n1 = parseAndTypeCheck(externs + "(new String(\"x\")).substr(0,1);");
    assertTypeEquals(getNativeStringType(), n1.getLastChild().getFirstChild().getJSType());
  }

  @Test
  public void testExtendBuiltInType2() {
    String externs =
        "/** @constructor */ var String = function(opt_str) {};\n"
            + "/**\n"
            + "* @param {number} start\n"
            + "* @param {number} opt_length\n"
            + "* @return {string}\n"
            + "*/\n"
            + "String.prototype.substr = function(start, opt_length) {};\n";
    Node n2 = parseAndTypeCheck(externs + "\"x\".substr(0,1);");
    assertTypeEquals(getNativeStringType(), n2.getLastChild().getFirstChild().getJSType());
  }

  @Test
  public void testExtendFunction1() {
    Node n =
        parseAndTypeCheck(
            "/**@return {number}*/Function.prototype.f = "
                + "function() { return 1; };\n"
                + "(new Function()).f();");
    JSType type = n.getLastChild().getLastChild().getJSType();
    assertTypeEquals(getNativeNumberType(), type);
  }

  @Test
  public void testExtendFunction2() {
    Node n =
        parseAndTypeCheck(
            "/**@return {number}*/Function.prototype.f = "
                + "function() { return 1; };\n"
                + "(function() {}).f();");
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
            "/** @constructor */function Super() {};"
                + "/** @constructor\n @extends {Super} */function Sub() {};"
                + "Sub.prototype.foo = function() {};")
        .run();
  }

  @Test
  public void testInheritanceCheck2() {
    newTest()
        .addSource(
            "/** @constructor */function Super() {};"
                + "/** @constructor\n @extends {Super} */function Sub() {};"
                + "/** @override */Sub.prototype.foo = function() {};")
        .addDiagnostic("property foo not defined on any superclass of Sub")
        .run();
  }

  @Test
  public void testInheritanceCheck3() {
    newTest()
        .addSource(
            "/** @constructor */function Super() {};"
                + "Super.prototype.foo = function() {};"
                + "/** @constructor\n @extends {Super} */function Sub() {};"
                + "Sub.prototype.foo = function() {};")
        .addDiagnostic(
            "property foo already defined on superclass Super; " + "use @override to override it")
        .run();
  }

  @Test
  public void testInheritanceCheck4() {
    newTest()
        .addSource(
            "/** @constructor */function Super() {};"
                + "Super.prototype.foo = function() {};"
                + "/** @constructor\n @extends {Super} */function Sub() {};"
                + "/** @override */Sub.prototype.foo = function() {};")
        .run();
  }

  @Test
  public void testInheritanceCheck5() {
    newTest()
        .addSource(
            "/** @constructor */function Root() {};"
                + "Root.prototype.foo = function() {};"
                + "/** @constructor\n @extends {Root} */function Super() {};"
                + "/** @constructor\n @extends {Super} */function Sub() {};"
                + "Sub.prototype.foo = function() {};")
        .addDiagnostic(
            "property foo already defined on superclass Root; " + "use @override to override it")
        .run();
  }

  @Test
  public void testInheritanceCheck6() {
    newTest()
        .addSource(
            "/** @constructor */function Root() {};"
                + "Root.prototype.foo = function() {};"
                + "/** @constructor\n @extends {Root} */function Super() {};"
                + "/** @constructor\n @extends {Super} */function Sub() {};"
                + "/** @override */Sub.prototype.foo = function() {};")
        .run();
  }

  @Test
  public void testInheritanceCheck7() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @constructor */goog.Super = function() {};"
                + "goog.Super.prototype.foo = 3;"
                + "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};"
                + "goog.Sub.prototype.foo = 5;")
        .run();
  }

  @Test
  public void testInheritanceCheck8() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @constructor */goog.Super = function() {};"
                + "goog.Super.prototype.foo = 3;"
                + "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};"
                + "/** @override */goog.Sub.prototype.foo = 5;")
        .run();
  }

  @Test
  public void testInheritanceCheck9_1() {
    newTest()
        .addSource(
            "/** @constructor */function Super() {};"
                + "Super.prototype.foo = function() { return 3; };"
                + "/** @constructor\n @extends {Super} */function Sub() {};"
                + "/** @override\n @return {number} */Sub.prototype.foo =\n"
                + "function() { return 1; };")
        .run();
  }

  @Test
  public void testInheritanceCheck9_2() {
    newTest()
        .addSource(
            "/** @constructor */function Super() {};"
                + "/** @return {number} */"
                + "Super.prototype.foo = function() { return 1; };"
                + "/** @constructor\n @extends {Super} */function Sub() {};"
                + "/** @override */Sub.prototype.foo =\n"
                + "function() {};")
        .run();
  }

  @Test
  public void testInheritanceCheck9_3() {
    newTest()
        .addSource(
            "/** @constructor */function Super() {};"
                + "/** @return {number} */"
                + "Super.prototype.foo = function() { return 1; };"
                + "/** @constructor\n @extends {Super} */function Sub() {};"
                + "/** @override\n @return {string} */Sub.prototype.foo =\n"
                + "function() { return \"some string\" };")
        .addDiagnostic(
            "mismatch of the foo property type and the type of the property it "
                + "overrides from superclass Super\n"
                + "original: function(this:Super): number\n"
                + "override: function(this:Sub): string")
        .run();
  }

  @Test
  public void testInheritanceCheck10_1() {
    newTest()
        .addSource(
            "/** @constructor */function Root() {};"
                + "Root.prototype.foo = function() { return 3; };"
                + "/** @constructor\n @extends {Root} */function Super() {};"
                + "/** @constructor\n @extends {Super} */function Sub() {};"
                + "/** @override\n @return {number} */Sub.prototype.foo =\n"
                + "function() { return 1; };")
        .run();
  }

  @Test
  public void testInheritanceCheck10_2() {
    newTest()
        .addSource(
            "/** @constructor */function Root() {};"
                + "/** @return {number} */"
                + "Root.prototype.foo = function() { return 1; };"
                + "/** @constructor\n @extends {Root} */function Super() {};"
                + "/** @constructor\n @extends {Super} */function Sub() {};"
                + "/** @override */Sub.prototype.foo =\n"
                + "function() {};")
        .run();
  }

  @Test
  public void testInheritanceCheck10_3() {
    newTest()
        .addSource(
            "/** @constructor */function Root() {};"
                + "/** @return {number} */"
                + "Root.prototype.foo = function() { return 1; };"
                + "/** @constructor\n @extends {Root} */function Super() {};"
                + "/** @constructor\n @extends {Super} */function Sub() {};"
                + "/** @override\n @return {string} */Sub.prototype.foo =\n"
                + "function() { return \"some string\" };")
        .addDiagnostic(
            "mismatch of the foo property type and the type of the property it "
                + "overrides from superclass Root\n"
                + "original: function(this:Root): number\n"
                + "override: function(this:Sub): string")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck11() {
    newTest()
        .addSource(
            "/** @constructor */function Super() {};"
                + "/** @param {number} bar */Super.prototype.foo = function(bar) {};"
                + "/** @constructor\n @extends {Super} */function Sub() {};"
                + "/** @override\n  @param {string} bar */Sub.prototype.foo =\n"
                + "function(bar) {};")
        .addDiagnostic(
            "mismatch of the foo property type and the type of the property it "
                + "overrides from superclass Super\n"
                + "original: function(this:Super, number): undefined\n"
                + "override: function(this:Sub, string): undefined")
        .run();
  }

  @Test
  public void testInheritanceCheck12() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @constructor */goog.Super = function() {};"
                + "goog.Super.prototype.foo = 3;"
                + "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};"
                + "/** @override */goog.Sub.prototype.foo = \"some string\";")
        .run();
  }

  @Test
  public void testInheritanceCheck13() {
    newTest()
        .addSource(
            "var goog = {};\n"
                + "/** @constructor\n @extends {goog.Missing} */function Sub() {};"
                + "/** @override */Sub.prototype.foo = function() {};")
        .addDiagnostic("Bad type annotation. Unknown type goog.Missing")
        .run();
  }

  @Test
  public void testInheritanceCheck14() {
    testClosureTypes(
        lines(
            "/** @constructor\n @extends {goog.Missing} */",
            "goog.Super = function() {};",
            "/** @constructor\n @extends {goog.Super} */function Sub() {};",
            "/** @override */ Sub.prototype.foo = function() {};"),
        "Bad type annotation. Unknown type goog.Missing");
  }

  @Test
  public void testInheritanceCheck15() {
    newTest()
        .addSource(
            "/** @constructor */function Super() {};"
                + "/** @param {number} bar */Super.prototype.foo;"
                + "/** @constructor\n @extends {Super} */function Sub() {};"
                + "/** @override\n  @param {number} bar */Sub.prototype.foo =\n"
                + "function(bar) {};")
        .run();
  }

  @Test
  public void testInheritanceCheck16() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @constructor */goog.Super = function() {};"
                + "/** @type {number} */ goog.Super.prototype.foo = 3;"
                + "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};"
                + "/** @type {number} */ goog.Sub.prototype.foo = 5;")
        .addDiagnostic(
            "property foo already defined on superclass goog.Super; "
                + "use @override to override it")
        .run();
  }

  @Test
  public void testInheritanceCheck17() {
    // Make sure this warning still works, even when there's no
    // @override tag.
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @constructor */goog.Super = function() {};"
                + "/** @param {number} x */ goog.Super.prototype.foo = function(x) {};"
                + "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};"
                + "/** @override @param {string} x */ goog.Sub.prototype.foo = function(x) {};")
        .addDiagnostic(
            "mismatch of the foo property type and the type of the property it "
                + "overrides from superclass goog.Super\n"
                + "original: function(this:goog.Super, number): undefined\n"
                + "override: function(this:goog.Sub, string): undefined")
        .run();
  }

  @Test
  public void testInterfacePropertyOverride1() {
    newTest()
        .addSource(
            "/** @interface */function Super() {};"
                + "Super.prototype.foo = function() {};"
                + "/** @interface\n @extends {Super} */function Sub() {};"
                + "Sub.prototype.foo = function() {};")
        .run();
  }

  @Test
  public void testInterfacePropertyOverride2() {
    newTest()
        .addSource(
            "/** @interface */function Root() {};"
                + "Root.prototype.foo = function() {};"
                + "/** @interface\n @extends {Root} */function Super() {};"
                + "/** @interface\n @extends {Super} */function Sub() {};"
                + "Sub.prototype.foo = function() {};")
        .run();
  }

  @Test
  public void testInterfacePropertyBadOverrideFails() {
    newTest()
        .addSource(
            "/** @interface */function Super() {};",
            "/** @type {number} */",
            "Super.prototype.foo;",
            "/** @interface @extends {Super} */function Sub() {};",
            "/** @type {string} */",
            "Sub.prototype.foo;")
        .addDiagnostic(
            lines(
                "mismatch of the foo property on type Sub and the type of the property it "
                    + "overrides from interface Super",
                "original: number",
                "override: string"))
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck1() {
    newTest()
        .addSource(
            "/** @interface */function Super() {};"
                + "Super.prototype.foo = function() {};"
                + "/** @constructor\n @implements {Super} */function Sub() {};"
                + "Sub.prototype.foo = function() {};")
        .addDiagnostic(
            "property foo already defined on interface Super; use @override to " + "override it")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck2() {
    newTest()
        .addSource(
            "/** @interface */function Super() {};"
                + "Super.prototype.foo = function() {};"
                + "/** @constructor\n @implements {Super} */function Sub() {};"
                + "/** @override */Sub.prototype.foo = function() {};")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck3() {
    newTest()
        .addSource(
            "/** @interface */function Root() {};"
                + "/** @return {number} */Root.prototype.foo = function() {};"
                + "/** @interface\n @extends {Root} */function Super() {};"
                + "/** @constructor\n @implements {Super} */function Sub() {};"
                + "/** @return {number} */Sub.prototype.foo = function() { return 1;};")
        .addDiagnostic(
            "property foo already defined on interface Root; use @override to " + "override it")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck4() {
    newTest()
        .addSource(
            "/** @interface */function Root() {};"
                + "/** @return {number} */Root.prototype.foo = function() {};"
                + "/** @interface\n @extends {Root} */function Super() {};"
                + "/** @constructor\n @implements {Super} */function Sub() {};"
                + "/** @override\n * @return {number} */Sub.prototype.foo =\n"
                + "function() { return 1;};")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck5() {
    newTest()
        .addSource(
            "/** @interface */function Super() {};/** @return {string} */Super.prototype.foo ="
                + " function() {};/** @constructor\n"
                + " @implements {Super} */function Sub() {};/** @override\n"
                + " @return {number} */Sub.prototype.foo = function() { return 1; };")
        .addDiagnostic(
            "mismatch of the foo property on type Sub and the type of the property it "
                + "overrides from interface Super\n"
                + "original: function(this:Super): string\n"
                + "override: function(this:Sub): number")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck6() {
    newTest()
        .addSource(
            "/** @interface */function Root() {};/** @return {string} */Root.prototype.foo ="
                + " function() {};/** @interface\n"
                + " @extends {Root} */function Super() {};/** @constructor\n"
                + " @implements {Super} */function Sub() {};/** @override\n"
                + " @return {number} */Sub.prototype.foo = function() { return 1; };")
        .addDiagnostic(
            "mismatch of the foo property on type Sub and the type of the property it "
                + "overrides from interface Root\n"
                + "original: function(this:Root): string\n"
                + "override: function(this:Sub): number")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck7() {
    newTest()
        .addSource(
            "/** @interface */function Super() {};"
                + "/** @param {number} bar */Super.prototype.foo = function(bar) {};"
                + "/** @constructor\n @implements {Super} */function Sub() {};"
                + "/** @override\n  @param {string} bar */Sub.prototype.foo =\n"
                + "function(bar) {};")
        .addDiagnostic(
            "mismatch of the foo property on type Sub and the type of the property it "
                + "overrides from interface Super\n"
                + "original: function(this:Super, number): undefined\n"
                + "override: function(this:Sub, string): undefined")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck8() {
    newTest()
        .addSource(
            "/** @constructor\n @implements {Super} */function Sub() {};"
                + "/** @override */Sub.prototype.foo = function() {};")
        .addDiagnostic("Bad type annotation. Unknown type Super")
        .addDiagnostic("property foo not defined on any superclass of Sub")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck9() {
    newTest()
        .addSource(
            "/** @interface */ function I() {}"
                + "/** @return {number} */ I.prototype.bar = function() {};"
                + "/** @constructor */ function F() {}"
                + "/** @return {number} */ F.prototype.bar = function() {return 3; };"
                + "/** @return {number} */ F.prototype.foo = function() {return 3; };"
                + "/** @constructor \n * @extends {F} \n * @implements {I} */ "
                + "function G() {}"
                + "/** @return {string} */ function f() { return new G().bar(); }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck10() {
    newTest()
        .addSource(
            "/** @interface */ function I() {}"
                + "/** @return {number} */ I.prototype.bar = function() {};"
                + "/** @constructor */ function F() {}"
                + "/** @return {number} */ F.prototype.foo = function() {return 3; };"
                + "/** @constructor \n * @extends {F} \n * @implements {I} */ "
                + "function G() {}"
                + "/** @return {number} \n * @override */ "
                + "G.prototype.bar = G.prototype.foo;"
                + "/** @return {string} */ function f() { return new G().bar(); }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck12() {
    newTest()
        .addSource(
            "/** @interface */ function I() {};\n"
                + "/** @type {string} */ I.prototype.foobar;\n"
                + "/** \n * @constructor \n * @implements {I} */\n"
                + "function C() {\n"
                + "/** \n * @type {number} */ this.foobar = 2;};\n"
                + "/** @type {I} */ \n var test = new C(); alert(test.foobar);")
        .addDiagnostic(
            "mismatch of the foobar property on type C and the type of the property"
                + " it overrides from interface I\n"
                + "original: string\n"
                + "override: number")
        .run();
  }

  @Test
  public void testInterfaceInheritanceCheck13() {
    newTest()
        .addSource(
            "function abstractMethod() {};\n"
                + "/** @interface */var base = function() {};\n"
                + "/** @extends {base} \n @interface */ var Int = function() {}\n"
                + "/** @type {{bar : !Function}} */ var x; \n"
                + "/** @type {!Function} */ base.prototype.bar = abstractMethod; \n"
                + "/** @type {Int} */ var foo;\n"
                + "foo.bar();")
        .run();
  }

  /** Verify that templatized interfaces can extend one another and share template values. */
  @Test
  public void testInterfaceInheritanceCheck14() {
    newTest()
        .addSource(
            "/** @interface\n @template T */function A() {};"
                + "/** @return {T} */A.prototype.foo = function() {};"
                + "/** @interface\n @template U\n @extends {A<U>} */function B() {};"
                + "/** @return {U} */B.prototype.bar = function() {};"
                + "/** @constructor\n @implements {B<string>} */function C() {};"
                + "/** @return {string}\n @override */C.prototype.foo = function() {};"
                + "/** @return {string}\n @override */C.prototype.bar = function() {};")
        .run();
  }

  /** Verify that templatized instances can correctly implement templatized interfaces. */
  @Test
  public void testInterfaceInheritanceCheck15() {
    newTest()
        .addSource(
            "/** @interface\n @template T */function A() {};"
                + "/** @return {T} */A.prototype.foo = function() {};"
                + "/** @interface\n @template U\n @extends {A<U>} */function B() {};"
                + "/** @return {U} */B.prototype.bar = function() {};"
                + "/** @constructor\n @template V\n @implements {B<V>}\n */function C() {};"
                + "/** @return {V}\n @override */C.prototype.foo = function() {};"
                + "/** @return {V}\n @override */C.prototype.bar = function() {};")
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
            "/** @interface\n @template T */function A() {};"
                + "/** @return {T} */A.prototype.foo = function() {};"
                + "/** @return {T} */A.prototype.bar = function() {};"
                + "/** @constructor\n @implements {A<string>} */function B() {};"
                + "/** @override */B.prototype.foo = function() { return 'string'};"
                + "/** @override */B.prototype.bar = function() { return 3 };")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testInterfacePropertyNotImplemented() {
    newTest()
        .addSource(
            "/** @interface */function Int() {};"
                + "Int.prototype.foo = function() {};"
                + "/** @constructor\n @implements {Int} */function Foo() {};")
        .addDiagnostic("property foo on interface Int is not implemented by type Foo")
        .run();
  }

  @Test
  public void testInterfacePropertyNotImplemented2() {
    newTest()
        .addSource(
            "/** @interface */function Int() {};"
                + "Int.prototype.foo = function() {};"
                + "/** @interface \n @extends {Int} */function Int2() {};"
                + "/** @constructor\n @implements {Int2} */function Foo() {};")
        .addDiagnostic("property foo on interface Int is not implemented by type Foo")
        .run();
  }

  /** Verify that templatized interfaces enforce their template type values. */
  @Test
  public void testInterfacePropertyNotImplemented3() {
    newTest()
        .addSource(
            "/** @interface  @template T */ function Int() {};",
            "/** @return {T} */ Int.prototype.foo = function() {};",
            "",
            "/** @constructor @implements {Int<string>} */ function Foo() {};",
            "/** @return {number}  @override */ Foo.prototype.foo = function() {};")
        .addDiagnostic(
            lines(
                "mismatch of the foo property on type Foo and the type of the property it "
                    + "overrides from interface Int",
                "original: function(this:Int): string",
                "override: function(this:Foo): number"))
        .run();
  }

  @Test
  public void testStubConstructorImplementingInterface() {
    // This does not throw a warning for unimplemented property because Foo is
    // just a stub.
    newTest()
        .addExterns(
            "/** @interface */ function Int() {}\n"
                + "Int.prototype.foo = function() {};"
                + "/** @constructor \n @implements {Int} */ var Foo;\n")
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
            "var x = {"
                + "/** @type {boolean} */ abc: true,"
                + "/** @type {number} */ 'def': 0,"
                + "/** @type {string} */ 3: 'fgh'"
                + "};")
        .run();
  }

  @Test
  public void testObjectLiteralDeclaration2() {
    newTest()
        .addSource("var x = {" + "  /** @type {boolean} */ abc: true" + "};" + "x.abc = 0;")
        .addDiagnostic(
            "assignment to property abc of x\n" + "found   : number\n" + "required: boolean")
        .run();
  }

  @Test
  public void testObjectLiteralDeclaration3() {
    newTest()
        .addSource(
            "/** @param {{foo: !Function}} x */ function f(x) {}" + "f({foo: function() {}});")
        .run();
  }

  @Test
  public void testObjectLiteralDeclaration4() {
    testClosureTypes(
        "var x = {"
            + "  /** @param {boolean} x */ abc: function(x) {}"
            + "};"
            + "/**\n"
            + " * @param {string} x\n"
            + " * @suppress {duplicate}\n"
            + " */ x.abc = function(x) {};",
        "assignment to property abc of x\n"
            + "found   : function(string): undefined\n"
            + "required: function(boolean): undefined");
    // TODO(user): suppress {duplicate} currently also silence the
    // redefining type error in the TypeValidator. Maybe it needs
    // a new suppress name instead?
  }

  @Test
  public void testObjectLiteralDeclaration5() {
    newTest()
        .addSource(
            "var x = {"
                + "  /** @param {boolean} x */ abc: function(x) {}"
                + "};"
                + "/**\n"
                + " * @param {boolean} x\n"
                + " * @suppress {duplicate}\n"
                + " */ x.abc = function(x) {};")
        .run();
  }

  @Test
  public void testObjectLiteralDeclaration6() {
    newTest()
        .addSource(
            "var x = {};"
                + "/**\n"
                + " * @param {boolean} x\n"
                + " * @suppress {duplicate}\n"
                + " */ x.abc = function(x) {};"
                + "x = {"
                + "  /**\n"
                + "   * @param {boolean} x\n"
                + "   * @suppress {duplicate}\n"
                + "   */"
                + "  abc: function(x) {}"
                + "};")
        .run();
  }

  @Test
  public void testObjectLiteralDeclaration7() {
    newTest()
        .addSource(
            "var x = {};"
                + "/**\n"
                + " * @type {function(boolean): undefined}\n"
                + " */ x.abc = function(x) {};"
                + "x = {"
                + "  /**\n"
                + "   * @param {boolean} x\n"
                + "   * @suppress {duplicate}\n"
                + "   */"
                + "  abc: function(x) {}"
                + "};")
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
        lines(
            "/** @constructor",
            "    @param {string} message",
            "    @return {!Error} */",
            "function Error(message) {}");
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
            "var a = {};"
                + "/** @constructor */ a.N = function() {};\n"
                + "a.N.prototype.p = 1;\n"
                + "/** @constructor */ a.S = function() {};\n"
                + "a.S.prototype.p = 'a';\n"
                + "/** @param {!a.N|!a.S} x\n@return {string} */\n"
                + "var f = function(x) { return x.p; };")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (number|string)\n" + "required: string")
        .run();
  }

  @Test
  public void testGetPropertyTypeOfUnionType_withMatchingTemplates() {
    newTest()
        .addSource(
            "/** @interface @template T */ function Foo() {};",
            "/** @type {T} */",
            "Foo.prototype.p;",
            "/** @interface @template U */ function Bar() {};",
            "/** @type {U} */",
            "Bar.prototype.p;",
            "",
            "/**",
            " * @param {!Foo<number>|!Bar<number>} x",
            " * @return {string} ",
            " */",
            "var f = function(x) { return x.p; };")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testGetPropertyTypeOfUnionType_withDifferingTemplates() {
    newTest()
        .addSource(
            "/** @interface @template T */ function Foo() {};",
            "/** @type {T} */",
            "Foo.prototype.p;",
            "/** @interface @template U */ function Bar() {};",
            "/** @type {U} */",
            "Bar.prototype.p;",
            "",
            "/**",
            " * @param {!Foo<number>|!Bar<string>} x",
            " * @return {string} ",
            " */",
            "var f = function(x) { return x.p; };")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : (number|string)",
                "required: string"))
        .run();
  }

  @Test
  public void testGetPropertyTypeOfUnionType_withMembersThatExtendATemplatizedType() {
    newTest()
        .addSource(
            "/** @interface @template T */ function Foo() {};",
            "/** @type {T} */",
            "Foo.prototype.p;",
            "",
            "/** @interface @extends {Foo<number>} */ function Bar() {};",
            "/** @interface @extends {Foo<number>} */ function Baz() {}",
            "",
            "/**",
            " * @param {!Bar|!Baz} x",
            " * @return {string} ",
            " */",
            "var f = function(x) { return x.p; };")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testInvalidAssignToPropertyTypeOfUnionType_withMatchingTemplates_doesntWarn() {
    // We don't warn for this assignment because we treat the type of `x.p` as inferred...
    newTest()
        .addSource(
            "/** @interface @template T */ function Foo() {};",
            "/** @type {T} */",
            "Foo.prototype.p;",
            "/** @interface @template U */ function Bar() {};",
            "/** @type {U} */",
            "Bar.prototype.p;",
            "",
            "/**",
            " * @param {!Foo<number>|!Bar<number>} x",
            " */",
            "var f = function(x) { x.p = 'not a number'; };")
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
            "/** @interface */ u.T = function() {};\n"
                + "/** @return {number} */ u.T.prototype = function() { };")
        .addDiagnostic("e of its definition")
        .run();
  }

  @Test
  @Ignore
  public void testBadPropertyOnInterface1() {
    newTest()
        .addSource(
            "/** @interface */ u.T = function() {};\n"
                + "/** @return {number} */ u.T.f = function() { return 1;};")
        .addDiagnostic("cannot reference an interface outside of its definition")
        .run();
  }

  @Test
  @Ignore
  public void testBadPropertyOnInterface2() {
    newTest()
        .addSource(
            "/** @interface */ function T() {};\n"
                + "/** @return {number} */ T.f = function() { return 1;};")
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
            "/** @interface */ u.T = function() {};\n"
                + "/** @return {number} */ u.T.prototype.f = function() {};")
        .run();
  }

  @Test
  public void testAnnotatedPropertyOnInterface2() {
    newTest()
        .addSource(
            "/** @interface */ u.T = function() {};\n"
                + "/** @return {number} */ u.T.prototype.f = function() { };")
        .run();
  }

  @Test
  public void testAnnotatedPropertyOnInterface3() {
    newTest()
        .addSource(
            "/** @interface */ function T() {};\n"
                + "/** @return {number} */ T.prototype.f = function() { };")
        .run();
  }

  @Test
  public void testAnnotatedPropertyOnInterface4() {
    newTest()
        .addSource(
            CLOSURE_DEFS
                + "/** @interface */ function T() {};\n"
                + "/** @return {number} */ T.prototype.f = goog.abstractMethod;")
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
        .addSource("/** @interface */ u.T = function() {};\n" + "u.T.prototype.x = function() {};")
        .run();
  }

  @Test
  public void testWarnUnannotatedPropertyOnInterface6() {
    newTest()
        .addSource("/** @interface */ function T() {};\n" + "T.prototype.x = function() {};")
        .run();
  }

  // TODO(user): If we want to support this syntax we have to warn about
  // the invalid type of the interface member.
  @Test
  @Ignore
  public void testWarnDataPropertyOnInterface1() {
    newTest()
        .addSource(
            "/** @interface */ u.T = function() {};\n" + "/** @type {number} */u.T.prototype.x;")
        .addDiagnostic("interface members can only be plain functions")
        .run();
  }

  @Test
  public void testDataPropertyOnInterface1() {
    newTest()
        .addSource("/** @interface */ function T() {};\n" + "/** @type {number} */T.prototype.x;")
        .run();
  }

  @Test
  public void testDataPropertyOnInterface2() {
    newTest()
        .addSource(
            "/** @interface */ function T() {};\n"
                + "/** @type {number} */T.prototype.x;\n"
                + "/** @constructor \n"
                + " *  @implements {T} \n"
                + " */\n"
                + "function C() {}\n"
                + "/** @override */\n"
                + "C.prototype.x = 'foo';")
        .addDiagnostic(
            "mismatch of the x property on type C and the type of the property it "
                + "overrides from interface T\n"
                + "original: number\n"
                + "override: string")
        .run();
  }

  @Test
  public void testDataPropertyOnInterface3() {
    newTest()
        .addSource(
            "/** @interface */ function T() {};\n"
                + "/** @type {number} */T.prototype.x;\n"
                + "/** @constructor \n"
                + " *  @implements {T} \n"
                + " */\n"
                + "function C() {}\n"
                + "/** @override */\n"
                + "C.prototype.x = 'foo';")
        .addDiagnostic(
            "mismatch of the x property on type C and the type of the property it "
                + "overrides from interface T\n"
                + "original: number\n"
                + "override: string")
        .run();
  }

  @Test
  public void testDataPropertyOnInterface4() {
    newTest()
        .addSource(
            "/** @interface */ function T() {};\n"
                + "/** @type {number} */T.prototype.x;\n"
                + "/** @constructor \n"
                + " *  @implements {T} \n"
                + " */\n"
                + "function C() { /** @type {string} */ \n this.x = 'foo'; }\n")
        .addDiagnostic(
            "mismatch of the x property on type C and the type of the property it "
                + "overrides from interface T\n"
                + "original: number\n"
                + "override: string")
        .run();
  }

  @Test
  public void testWarnDataPropertyOnInterface3() {
    newTest()
        .addSource(
            "/** @interface */ u.T = function() {};\n"
                + "/** @type {number} */u.T.prototype.x = 1;")
        .addDiagnostic(
            "interface members can only be empty property declarations, "
                + "empty functions, or goog.abstractMethod")
        .run();
  }

  @Test
  public void testWarnDataPropertyOnInterface4() {
    newTest()
        .addSource(
            "/** @interface */ function T() {};\n" + "/** @type {number} */T.prototype.x = 1;")
        .addDiagnostic(
            "interface members can only be empty property declarations, "
                + "empty functions, or goog.abstractMethod")
        .run();
  }

  // TODO(user): If we want to support this syntax we should warn about the
  // mismatching types in the two tests below.
  @Test
  @Ignore
  public void testErrorMismatchingPropertyOnInterface1() {
    newTest()
        .addSource(
            "/** @interface */ u.T = function() {};\n"
                + "/** @param {Number} foo */u.T.prototype.x =\n"
                + "/** @param {String} foo */function(foo) {};")
        .addDiagnostic("found   : \n" + "required: ")
        .run();
  }

  @Test
  @Ignore
  public void testErrorMismatchingPropertyOnInterface2() {
    newTest()
        .addSource(
            "/** @interface */ function T() {};\n"
                + "/** @return {number} */T.prototype.x =\n"
                + "/** @return {string} */function() {};")
        .addDiagnostic("found   : \n" + "required: ")
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
            "/** @interface */ u.T = function() {};\n"
                + "/** @param {Number} foo */u.T.prototype.x =\n"
                + "function(foo, bar) {};")
        .addDiagnostic("found   : \n" + "required: ")
        .run();
  }

  @Test
  public void testErrorMismatchingPropertyOnInterface4() {
    newTest()
        .addSource(
            "/** @interface */ u.T = function() {};\n"
                + "/** @param {Number} foo */u.T.prototype.x =\n"
                + "function() {};")
        .addDiagnostic("parameter foo does not appear in u.T.prototype.x's parameter list")
        .run();
  }

  @Test
  public void testErrorMismatchingPropertyOnInterface5() {
    newTest()
        .addSource(
            "/** @interface */ function T() {};\n"
                + "/** @type {number} */T.prototype.x = function() { };")
        .addDiagnostic(
            "assignment to property x of T.prototype\n"
                + "found   : function(): undefined\n"
                + "required: number")
        .run();
  }

  @Test
  public void testErrorMismatchingPropertyOnInterface6() {
    testClosureTypesMultipleWarnings(
        "/** @interface */ function T() {};\n" + "/** @return {number} */T.prototype.x = 1",
        ImmutableList.of(
            "assignment to property x of T.prototype\n"
                + "found   : number\n"
                + "required: function(this:T): number",
            "interface members can only be empty property declarations, "
                + "empty functions, or goog.abstractMethod"));
  }

  @Test
  public void testInterfaceNonEmptyFunction() {
    newTest()
        .addSource(
            "/** @interface */ function T() {};\n" + "T.prototype.x = function() { return 'foo'; }")
        .addDiagnostic("interface member functions must have an empty body")
        .run();
  }

  @Test
  public void testDoubleNestedInterface() {
    newTest()
        .addSource(
            "/** @interface */ var I1 = function() {};\n"
                + "/** @interface */ I1.I2 = function() {};\n"
                + "/** @interface */ I1.I2.I3 = function() {};\n")
        .run();
  }

  @Test
  public void testStaticDataPropertyOnNestedInterface() {
    newTest()
        .addSource(
            "/** @interface */ var I1 = function() {};\n"
                + "/** @interface */ I1.I2 = function() {};\n"
                + "/** @type {number} */ I1.I2.x = 1;\n")
        .run();
  }

  @Test
  public void testInterfaceInstantiation() {
    newTest()
        .addSource("/** @interface */var f = function(){}; new f")
        .addDiagnostic("cannot instantiate non-constructor")
        .run();
  }

  @Test
  public void testPrototypeLoop() {
    disableStrictMissingPropertyChecks();

    testClosureTypesMultipleWarnings(
        suppressMissingProperty("foo")
            + "/** @constructor \n * @extends {T} */var T = function() {};"
            + "alert((new T).foo);",
        ImmutableList.of(
            "Cycle detected in inheritance chain of type T",
            "Could not resolve type in @extends tag of T"));
  }

  @Test
  public void testImplementsLoop() {
    testClosureTypesMultipleWarnings(
        lines(
            "/** @constructor \n * @implements {T} */var T = function() {};",
            suppressMissingPropertyFor("T", "foo"),
            "alert((new T).foo);"),
        ImmutableList.of("Cycle detected in inheritance chain of type T"));
  }

  @Test
  public void testImplementsExtendsLoop() {
    disableStrictMissingPropertyChecks();

    testClosureTypesMultipleWarnings(
        suppressMissingProperty("foo")
            + "/** @constructor \n * @implements {F} */var G = function() {};"
            + "/** @constructor \n * @extends {G} */var F = function() {};"
            + "alert((new F).foo);",
        ImmutableList.of("Cycle detected in inheritance chain of type F"));
  }

  // TODO(johnlenz): This test causes an infinite loop,
  @Test
  @Ignore
  public void testInterfaceExtendsLoop() {
    testClosureTypesMultipleWarnings(
        lines(
            "/** @interface \n * @extends {F} */var G = function() {};",
            "/** @interface \n * @extends {G} */var F = function() {};",
            "/** @constructor \n * @implements {F} */var H = function() {};",
            suppressMissingPropertyFor("H", "foo"),
            "alert((new H).foo);"),
        ImmutableList.of(
            "extends loop involving F, " + "loop: F -> G -> F",
            "extends loop involving G, " + "loop: G -> F -> G"));
  }

  @Test
  public void testInterfaceExtendsLoop2() {
    testClosureTypes(
        lines(
            "/** @record \n * @extends {F} */var G = function() {};",
            "/** @record \n * @extends {G} */var F = function() {};",
            "/** @constructor \n * @implements {F} */var H = function() {};",
            suppressMissingPropertyFor("H", "foo"),
            "alert((new H).foo);"),
        "Cycle detected in inheritance chain of type F");
  }

  @Test
  public void testInheritPropFromMultipleInterfaces1() {
    // Low#prop gets the type of whichever property is declared last,
    // even if that type is not the most specific.
    newTest()
        .addSource(
            "/** @interface */",
            "function High1() {}",
            "/** @type {number|string} */",
            "High1.prototype.prop;",
            "/** @interface */",
            "function High2() {}",
            "/** @type {number} */",
            "High2.prototype.prop;",
            "/**",
            " * @interface",
            " * @extends {High1}",
            " * @extends {High2}",
            " */",
            "function Low() {}",
            "function f(/** !Low */ x) { var /** null */ n = x.prop; }")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : (number|string)",
                "required: null"))
        .run();
  }

  @Test
  public void testInheritPropFromMultipleInterfaces2() {
    // Low#prop gets the type of whichever property is declared last,
    // even if that type is not the most specific.
    newTest()
        .addSource(
            "/** @interface */",
            "function High1() {}",
            "/** @type {number} */",
            "High1.prototype.prop;",
            "/** @interface */",
            "function High2() {}",
            "/** @type {number|string} */",
            "High2.prototype.prop;",
            "/**",
            " * @interface",
            " * @extends {High1}",
            " * @extends {High2}",
            " */",
            "function Low() {}",
            "function f(/** !Low */ x) { var /** null */ n = x.prop; }")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testInheritPropFromMultipleInterfaces3() {
    newTest()
        .addSource(
            "/**",
            " * @interface",
            " * @template T1",
            " */",
            "function MyCollection() {}",
            "/**",
            " * @interface",
            " * @template T2",
            " * @extends {MyCollection<T2>}",
            " */",
            "function MySet() {}",
            "/**",
            " * @interface",
            " * @template T3,T4",
            " */",
            "function MyMapEntry() {}",
            "/**",
            " * @interface",
            " * @template T5,T6",
            " */",
            "function MyMultimap() {}",
            "/** @return {MyCollection<MyMapEntry<T5, T6>>} */",
            "MyMultimap.prototype.entries = function() {};",
            "/**",
            " * @interface",
            " * @template T7,T8",
            " * @extends {MyMultimap<T7, T8>}",
            " */",
            "function MySetMultimap() {}",
            "/** @return {MySet<MyMapEntry<T7, T8>>} */",
            "MySetMultimap.prototype.entries = function() {};",
            "/**",
            " * @interface",
            " * @template T9,T10",
            " * @extends {MyMultimap<T9, T10>}",
            " */",
            "function MyFilteredMultimap() {}",
            "/**",
            " * @interface",
            " * @template T11,T12",
            " * @extends {MyFilteredMultimap<T11, T12>}",
            " * @extends {MySetMultimap<T11, T12>}",
            " */",
            "function MyFilteredSetMultimap() {}")
        .run();
  }

  @Test
  public void testInheritSameGenericInterfaceFromDifferentPaths() {
    newTest()
        .addSource(
            "/** @const */ var ns = {};",
            "/**",
            " * @constructor",
            " * @template T1",
            " */",
            "ns.Foo = function() {};",
            "/**",
            " * @interface",
            " * @template T2",
            " */",
            "ns.High = function() {};",
            "/** @type {!ns.Foo<T2>} */",
            "ns.High.prototype.myprop;",
            "/**",
            " * @interface",
            " * @template T3",
            " * @extends {ns.High<T3>}",
            " */",
            "ns.Med1 = function() {};",
            "/**",
            " * @interface",
            " * @template T4",
            " * @extends {ns.High<T4>}",
            " */",
            "ns.Med2 = function() {};",
            "/**",
            " * @interface",
            " * @template T5",
            " * @extends {ns.Med1<T5>}",
            " * @extends {ns.Med2<T5>}",
            " */",
            "ns.Low = function() {};")
        .run();
  }

  @Test
  public void testConversionFromInterfaceToRecursiveConstructor() {
    testClosureTypesMultipleWarnings(
        suppressMissingProperty("foo")
            + "/** @interface */ var OtherType = function() {}\n"
            + "/** @implements {MyType} \n * @constructor */\n"
            + "var MyType = function() {}\n"
            + "/** @type {MyType} */\n"
            + "var x = /** @type {!OtherType} */ (new Object());",
        ImmutableList.of(
            "Cycle detected in inheritance chain of type MyType",
            "initializing variable\n" + "found   : OtherType\n" + "required: (MyType|null)"));
  }

  @Test
  public void testDirectPrototypeAssign() {
    // For now, we just ignore @type annotations on the prototype.
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @constructor */ function Bar() {}"
                + "/** @type {Array} */ Bar.prototype = new Foo()")
        .run();
  }

  // In all testResolutionViaRegistry* tests, since u is unknown, u.T can only
  // be resolved via the registry and not via properties.

  @Test
  public void testResolutionViaRegistry1() {
    newTest()
        .addSource(
            "/** @constructor */ u.T = function() {};\n"
                + "/** @type {(number|string)} */ u.T.prototype.a;\n"
                + "/**\n"
                + "* @param {u.T} t\n"
                + "* @return {string}\n"
                + "*/\n"
                + "var f = function(t) { return t.a; };")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (number|string)\n" + "required: string")
        .run();
  }

  @Test
  public void testResolutionViaRegistry2() {
    newTest()
        .addSource(
            "/** @constructor */ u.T = function() {"
                + "  this.a = 0; };\n"
                + "/**\n"
                + "* @param {u.T} t\n"
                + "* @return {string}\n"
                + "*/\n"
                + "var f = function(t) { return t.a; };")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testResolutionViaRegistry3() {
    newTest()
        .addSource(
            "/** @constructor */ u.T = function() {};\n"
                + "/** @type {(number|string)} */ u.T.prototype.a = 0;\n"
                + "/**\n"
                + "* @param {u.T} t\n"
                + "* @return {string}\n"
                + "*/\n"
                + "var f = function(t) { return t.a; };")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (number|string)\n" + "required: string")
        .run();
  }

  @Test
  public void testResolutionViaRegistry4() {
    newTest()
        .addSource(
            "/** @constructor */ u.A = function() {};\n"
                + "/**\n* @constructor\n* @extends {u.A}\n*/\nu.A.A = function() {}\n;"
                + "/**\n* @constructor\n* @extends {u.A}\n*/\nu.A.B = function() {};\n"
                + "var ab = new u.A.B();\n"
                + "/** @type {!u.A} */ var a = ab;\n"
                + "/** @type {!u.A.A} */ var aa = ab;\n")
        .addDiagnostic("initializing variable\n" + "found   : u.A.B\n" + "required: u.A.A")
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
            "/** @constructor */ var T = function() {};" + "/** @type {!T} */var t; t.x; t;");
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
            "function u() {}\n"
                + "/** @return {number} */ function f() {\nvar x = 'todo';\n"
                + "if (u()) { x = 1; } else { x = 2; } return x;\n}")
        .run();
  }

  @Test
  public void testDfa3() {
    newTest()
        .addSource(
            "function u() {}\n"
                + "/** @return {number} */ function f() {\n"
                + "/** @type {number|string} */ var x = 'todo';\n"
                + "if (u()) { x = 1; } else { x = 2; } return x;\n}")
        .run();
  }

  @Test
  public void testDfa4() {
    newTest()
        .addSource(
            "/** @param {Date?} d */ function f(d) {\n"
                + "if (!d) { return; }\n"
                + "/** @type {!Date} */ var e = d;\n}")
        .run();
  }

  @Test
  public void testDfa5() {
    newTest()
        .addSource(
            "/** @return {string?} */ function u() {return 'a';}\n"
                + "/** @param {string?} x\n@return {string} */ function f(x) {\n"
                + "while (!x) { x = u(); }\nreturn x;\n}")
        .run();
  }

  @Test
  public void testDfa6() {
    newTest()
        .addSource(
            "/** @return {Object?} */ function u() {return {};}\n"
                + "/** @param {Object?} x */ function f(x) {\n"
                + "while (x) { x = u(); if (!x) { x = u(); } }\n}")
        .run();
  }

  @Test
  public void testDfa7() {
    newTest()
        .addSource(
            "/** @constructor */ var T = function() {};\n"
                + "/** @type {Date?} */ T.prototype.x = null;\n"
                + "/** @param {!T} t */ function f(t) {\n"
                + "if (!t.x) { return; }\n"
                + "/** @type {!Date} */ var e = t.x;\n}")
        .run();
  }

  @Test
  public void testDfa8() {
    newTest()
        .addSource(
            "/** @constructor */ var T = function() {};\n"
                + "/** @type {number|string} */ T.prototype.x = '';\n"
                + "function u() {}\n"
                + "/** @param {!T} t\n@return {number} */ function f(t) {\n"
                + "if (u()) { t.x = 1; } else { t.x = 2; } return t.x;\n}")
        .run();
  }

  @Test
  public void testDfa9() {
    newTest()
        .addSource(
            "function f() {\n/** @type {string?} */var x;\nx = null;\n"
                + "if (x == null) { return 0; } else { return 1; } }")
        .addDiagnostic("condition always evaluates to true\n" + "left : null\n" + "right: null")
        .run();
  }

  @Test
  public void testDfa10() {
    newTest()
        .addSource(
            "/** @param {null} x */ function g(x) {}"
                + "/** @param {string?} x */function f(x) {\n"
                + "if (!x) { x = ''; }\n"
                + "if (g(x)) { return 0; } else { return 1; } }")
        .addDiagnostic(
            "actual parameter 1 of g does not match formal parameter\n"
                + "found   : string\n"
                + "required: null")
        .run();
  }

  @Test
  public void testDfa11() {
    newTest()
        .addSource(
            "/** @param {string} opt_x\n@return {string} */\n"
                + "function f(opt_x) { if (!opt_x) { "
                + "throw new Error('x cannot be empty'); } return opt_x; }")
        .run();
  }

  @Test
  public void testDfa12() {
    newTest()
        .addSource(
            "/** @param {string} x \n * @constructor \n */"
                + "var Bar = function(x) {};"
                + "/** @param {string} x */ function g(x) { return true; }"
                + "/** @param {string|number} opt_x */ "
                + "function f(opt_x) { "
                + "  if (opt_x) { new Bar(g(opt_x) && 'x'); }"
                + "}")
        .addDiagnostic(
            "actual parameter 1 of g does not match formal parameter\n"
                + "found   : (number|string)\n"
                + "required: string")
        .run();
  }

  @Test
  public void testDfa13() {
    newTest()
        .addSource(
            "/**\n"
                + " * @param {string} x \n"
                + " * @param {number} y \n"
                + " * @param {number} z \n"
                + " */"
                + "function g(x, y, z) {}"
                + "function f() { "
                + "  var x = 'a'; g(x, x = 3, x);"
                + "}")
        .run();
  }

  @Test
  public void testTypeInferenceWithCast1() {
    newTest()
        .addSource(
            "/**@return {(number|null|undefined)}*/function u(x) {return null;}"
                + "/**@param {number?} x\n@return {number?}*/function f(x) {return x;}"
                + "/**@return {number?}*/function g(x) {"
                + "var y = /**@type {number?}*/(u(x)); return f(y);}")
        .run();
  }

  @Test
  public void testTypeInferenceWithCast2() {
    newTest()
        .addSource(
            "/**@return {(number|null|undefined)}*/function u(x) {return null;}"
                + "/**@param {number?} x\n@return {number?}*/function f(x) {return x;}"
                + "/**@return {number?}*/function g(x) {"
                + "var y; y = /**@type {number?}*/(u(x)); return f(y);}")
        .run();
  }

  @Test
  public void testTypeInferenceWithCast3() {
    newTest()
        .addSource(
            "/**@return {(number|null|undefined)}*/function u(x) {return 1;}"
                + "/**@return {number}*/function g(x) {"
                + "return /**@type {number}*/(u(x));}")
        .run();
  }

  @Test
  public void testTypeInferenceWithCast4() {
    newTest()
        .addSource(
            "/**@return {(number|null|undefined)}*/function u(x) {return 1;}"
                + "/**@return {number}*/function g(x) {"
                + "return /**@type {number}*/(u(x)) && 1;}")
        .run();
  }

  @Test
  public void testTypeInferenceWithCast5() {
    newTest()
        .addSource(
            "/** @param {number} x */ function foo(x) {}"
                + "/** @param {{length:*}} y */ function bar(y) {"
                + "  /** @type {string} */ y.length;"
                + "  foo(y.length);"
                + "}")
        .addDiagnostic(
            "actual parameter 1 of foo does not match formal parameter\n"
                + "found   : string\n"
                + "required: number")
        .run();
  }

  @Test
  public void testTypeInferenceWithClosure1() {
    newTest()
        .addSource(
            "/** @return {boolean} */"
                + "function f() {"
                + "  /** @type {?string} */ var x = null;"
                + "  function g() { x = 'y'; } g(); "
                + "  return x == null;"
                + "}")
        .run();
  }

  @Test
  public void testTypeInferenceWithClosure2() {
    newTest()
        .addSource(
            "/** @return {boolean} */"
                + "function f() {"
                + "  /** @type {?string} */ var x = null;"
                + "  function g() { x = 'y'; } g(); "
                + "  return x === 3;"
                + "}")
        .addDiagnostic(
            "condition always evaluates to false\n" + "left : (null|string)\n" + "right: number")
        .run();
  }

  @Test
  public void testTypeInferenceWithNoEntry1() {
    newTest()
        .addSource(
            "/** @param {number} x */ function f(x) {}"
                + "/** @constructor */ function Foo() {}"
                + "Foo.prototype.init = function() {"
                + "  /** @type {?{baz: number}} */ this.bar = {baz: 3};"
                + "};"
                + "/**\n"
                + " * @extends {Foo}\n"
                + " * @constructor\n"
                + " */"
                + "function SubFoo() {}"
                + "/** Method */"
                + "SubFoo.prototype.method = function() {"
                + "  for (var i = 0; i < 10; i++) {"
                + "    f(this.bar);"
                + "    f(this.bar.baz);"
                + "  }"
                + "};")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : (null|{baz: number})\n"
                + "required: number")
        .run();
  }

  @Test
  public void testTypeInferenceWithNoEntry2() {
    testClosureTypes(
        "/** @param {number} x */ function f(x) {}"
            + "/** @param {!Object} x */ function g(x) {}"
            + "/** @constructor */ function Foo() {}"
            + "Foo.prototype.init = function() {"
            + "  /** @type {?{baz: number}} */ this.bar = {baz: 3};"
            + "};"
            + "/**\n"
            + " * @extends {Foo}\n"
            + " * @constructor\n"
            + " */"
            + "function SubFoo() {}"
            + "/** Method */"
            + "SubFoo.prototype.method = function() {"
            + "  for (var i = 0; i < 10; i++) {"
            + "    f(this.bar);"
            + "    goog.asserts.assert(this.bar);"
            + "    g(this.bar);"
            + "  }"
            + "};",
        "actual parameter 1 of f does not match formal parameter\n"
            + "found   : (null|{baz: number})\n"
            + "required: number");
  }

  @Test
  public void testForwardPropertyReference() {
    newTest()
        .addSource(
            "/** @constructor */ var Foo = function() { this.init(); };"
                + "/** @return {string} */"
                + "Foo.prototype.getString = function() {"
                + "  return this.number_;"
                + "};"
                + "Foo.prototype.init = function() {"
                + "  /** @type {number} */"
                + "  this.number_ = 3;"
                + "};")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
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
  public void testForwardTypeDeclaration2() {
    String f = "goog.forwardDeclare('MyType');" + "/** @param {MyType} x */ function f(x) { }";
    testClosureTypes(f, null);
    testClosureTypes(
        f + "f(3);",
        "actual parameter 1 of f does not match formal parameter\n"
            + "found   : number\n"
            + "required: (MyType|null)");
  }

  @Test
  public void testForwardTypeDeclaration3() {
    testClosureTypes(
        "goog.forwardDeclare('MyType');"
            + "/** @param {MyType} x */ function f(x) { return x; }"
            + "/** @constructor */ var MyType = function() {};"
            + "f(3);",
        "actual parameter 1 of f does not match formal parameter\n"
            + "found   : number\n"
            + "required: (MyType|null)");
  }

  @Test
  public void testForwardTypeDeclaration4() {
    testClosureTypes(
        "goog.forwardDeclare('MyType');"
            + "/** @param {MyType} x */ function f(x) { return x; }"
            + "/** @constructor */ var MyType = function() {};"
            + "f(new MyType());",
        null);
  }

  @Test
  public void testForwardTypeDeclaration5() {
    testClosureTypes(
        "goog.forwardDeclare('MyType');"
            + "/**\n"
            + " * @constructor\n"
            + " * @extends {MyType}\n"
            + " */ var YourType = function() {};"
            + "/** @override */ YourType.prototype.method = function() {};",
        "Could not resolve type in @extends tag of YourType");
  }

  @Test
  public void testForwardTypeDeclaration6() {
    testClosureTypesMultipleWarnings(
        "goog.forwardDeclare('MyType');"
            + "/**\n"
            + " * @constructor\n"
            + " * @implements {MyType}\n"
            + " */ var YourType = function() {};"
            + "/** @override */ YourType.prototype.method = function() {};",
        ImmutableList.of(
            "Could not resolve type in @implements tag of YourType",
            "property method not defined on any superclass of YourType"));
  }

  @Test
  public void testForwardTypeDeclaration7() {
    testClosureTypes(
        "goog.forwardDeclare('MyType');"
            + "/** @param {MyType=} x */"
            + "function f(x) { return x == undefined; }",
        null);
  }

  @Test
  public void testForwardTypeDeclaration8() {
    testClosureTypes(
        "goog.forwardDeclare('MyType');"
            + "/** @param {MyType} x */"
            + "function f(x) { return x.name == undefined; }",
        null);
  }

  @Test
  public void testForwardTypeDeclaration9() {
    testClosureTypes(
        "goog.forwardDeclare('MyType');"
            + "/** @param {MyType} x */"
            + "function f(x) { x.name = 'Bob'; }",
        null);
  }

  @Test
  public void testForwardTypeDeclaration10() {
    String f =
        "goog.forwardDeclare('MyType');" + "/** @param {MyType|number} x */ function f(x) { }";
    testClosureTypes(f, null);
    testClosureTypes(f + "f(3);", null);
    testClosureTypes(
        f + "f('3');",
        "actual parameter 1 of f does not match formal parameter\n"
            + "found   : string\n"
            + "required: (MyType|null|number)");
  }

  @Test
  public void testForwardTypeDeclaration12() {
    // We assume that {Function} types can produce anything, and don't
    // want to type-check them.
    testClosureTypes(
        "goog.forwardDeclare('MyType');"
            + "/**\n"
            + " * @param {!Function} ctor\n"
            + " * @return {MyType}\n"
            + " */\n"
            + "function f(ctor) { return new ctor(); }",
        null);
  }

  @Test
  public void testForwardTypeDeclaration13() {
    // Some projects use {Function} registries to register constructors
    // that aren't in their binaries. We want to make sure we can pass these
    // around, but still do other checks on them.
    testClosureTypes(
        "goog.forwardDeclare('MyType');"
            + "/**\n"
            + " * @param {!Function} ctor\n"
            + " * @return {MyType}\n"
            + " */\n"
            + "function f(ctor) { return (new ctor()).impossibleProp; }",
        "Property impossibleProp never defined on ?" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION);
  }

  @Test
  public void testDuplicateTypeDef() {
    newTest()
        .addSource(
            "var goog = {};",
            "/** @constructor */ goog.Bar = function() {};",
            "/** @typedef {number} */ goog.Bar;")
        .addDiagnostic(
            "variable goog.Bar redefined with type None, "
                + "original definition at [testcode]:2 "
                + "with type (typeof goog.Bar)")
        .run();
  }

  @Test
  public void testTypeDef1() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @typedef {number} */ goog.Bar;"
                + "/** @param {goog.Bar} x */ function f(x) {}"
                + "f(3);")
        .run();
  }

  @Test
  public void testTypeDef2() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @typedef {number} */ goog.Bar;"
                + "/** @param {goog.Bar} x */ function f(x) {}"
                + "f('3');")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : string\n"
                + "required: number")
        .run();
  }

  @Test
  public void testTypeDef3() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @typedef {number} */ var Bar;"
                + "/** @param {Bar} x */ function f(x) {}"
                + "f('3');")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : string\n"
                + "required: number")
        .run();
  }

  @Test
  public void testTypeDef4() {
    newTest()
        .addSource(
            "/** @constructor */ function A() {}"
                + "/** @constructor */ function B() {}"
                + "/** @typedef {(A|B)} */ var AB;"
                + "/** @param {AB} x */ function f(x) {}"
                + "f(new A()); f(new B()); f(1);")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : number\n"
                + "required: (A|B|null)")
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
            "/** @param {AB} x */ function f(x) {}"
                + "/** @constructor */ function A() {}"
                + "/** @constructor */ function B() {}"
                + "/** @typedef {(A|B)} */ var AB;"
                + "f(new A()); f(new B()); f(1);")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : number\n"
                + "required: (A|B|null)")
        .run();
  }

  @Test
  public void testCircularTypeDef() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @typedef {number|Array<goog.Bar>} */ goog.Bar;"
                + "/** @param {goog.Bar} x */ function f(x) {}"
                + "f(3); f([3]); f([[3]]);")
        .run();
  }

  @Test
  public void testGetTypedPercent1() {
    String js = "var id = function(x) { return x; }\n" + "var id2 = function(x) { return id(x); }";
    assertThat(getTypedPercent(js)).isWithin(0.1).of(50.0);
  }

  @Test
  public void testGetTypedPercent2() {
    String js = "var x = {}; x.y = 1;";
    assertThat(getTypedPercent(js)).isWithin(0.1).of(100.0);
  }

  @Test
  public void testGetTypedPercent3() {
    String js = "var f = function(x) { x.a = x.b; }";
    assertThat(getTypedPercent(js)).isWithin(0.1).of(25.0);
  }

  @Test
  public void testGetTypedPercent4() {
    String js =
        "var n = {};\n /** @constructor */ n.T = function() {};\n"
            + "/** @type {n.T} */ var x = new n.T();";
    assertThat(getTypedPercent(js)).isWithin(0.1).of(100.0);
  }

  @Test
  public void testGetTypedPercent5() {
    String js = "/** @enum {number} */ keys = {A: 1,B: 2,C: 3};";
    assertThat(getTypedPercent(js)).isWithin(0.1).of(100.0);
  }

  @Test
  public void testGetTypedPercent6() {
    String js = "a = {TRUE: 1, FALSE: 0};";
    assertThat(getTypedPercent(js)).isWithin(0.1).of(100.0);
  }

  @Test
  public void testPrototypePropertyReference() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope(
            DEFAULT_EXTERNS,
            ""
                + "/** @constructor */\n"
                + "function Foo() {}\n"
                + "/** @param {number} a */\n"
                + "Foo.prototype.bar = function(a){};\n"
                + "/** @param {Foo} f */\n"
                + "function baz(f) {\n"
                + "  Foo.prototype.bar.call(f, 3);\n"
                + "}");
    assertThat(compiler.getErrorCount()).isEqualTo(0);
    assertThat(compiler.getWarningCount()).isEqualTo(0);

    assertThat(p.scope.getVar("Foo").getType()).isInstanceOf(FunctionType.class);
    FunctionType fooType = (FunctionType) p.scope.getVar("Foo").getType();
    assertThat(fooType.getPrototype().getPropertyType("bar").toString())
        .isEqualTo("function(this:Foo, number): undefined");
  }

  @Test
  public void testResolvingNamedTypes() {
    String externs = new TestExternsBuilder().addObject().build();
    String js =
        lines(
            "/** @constructor */",
            "var Foo = function() {}",
            "/** @param {number} a */",
            "Foo.prototype.foo = function(a) {",
            "  return this.baz().toString();",
            "};",
            "/** @return {Baz} */",
            "Foo.prototype.baz = function() { return new Baz(); };",
            "/** @constructor",
            "  * @extends Foo */",
            "var Bar = function() {};",
            "/** @constructor */",
            "var Baz = function() {};");
    assertThat(getTypedPercentWithExterns(externs, js)).isWithin(0.1).of(100.0);
  }

  @Test
  public void testMissingProperty1a() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.bar = function() { return this.a; };",
            "Foo.prototype.baz = function() { this.a = 3; };")
        .addDiagnostic("Property a never defined on Foo")
        .addDiagnostic("Property a never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty1b() {
    disableStrictMissingPropertyChecks();

    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype.bar = function() { return this.a; };"
                + "Foo.prototype.baz = function() { this.a = 3; };")
        .run();
  }

  @Test
  public void testMissingProperty2a() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype.bar = function() { return this.a; };"
                + "Foo.prototype.baz = function() { this.b = 3; };")
        .addDiagnostic("Property a never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty2b() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.baz = function() { this.b = 3; };")
        .addDiagnostic("Property b never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty3a() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.bar = function() { return this.a; };",
            "(new Foo).a = 3;")
        .addDiagnostic("Property a never defined on Foo") // method
        .addDiagnostic("Property a never defined on Foo")
        .run(); // global assignment
  }

  @Test
  public void testMissingProperty3b() {
    disableStrictMissingPropertyChecks();

    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.bar = function() { return this.a; };",
            "(new Foo).a = 3;")
        .run();
  }

  @Test
  public void testMissingProperty4a() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype.bar = function() { return this.a; };"
                + "(new Foo).b = 3;")
        .addDiagnostic("Property a never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty4b() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}", //
            "(new Foo).b = 3;")
        .addDiagnostic("Property b never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty5() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype.bar = function() { return this.a; };"
                + "/** @constructor */ function Bar() { this.a = 3; };")
        .addDiagnostic("Property a never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty6a() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.bar = function() { return this.a; };",
            "/** @constructor \n * @extends {Foo} */ ",
            "function Bar() { this.a = 3; };")
        .addDiagnostic("Property a never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty6b() {
    disableStrictMissingPropertyChecks();

    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype.bar = function() { return this.a; };"
                + "/** @constructor \n * @extends {Foo} */ "
                + "function Bar() { this.a = 3; };")
        .run();
  }

  @Test
  public void testMissingProperty7() {
    newTest()
        .addSource("/** @param {Object} obj */" + "function foo(obj) { return obj.impossible; }")
        .addDiagnostic(
            "Property impossible never defined on Object"
                + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testMissingProperty8() {
    newTest()
        .addSource(
            "/** @param {Object} obj */" + "function foo(obj) { return typeof obj.impossible; }")
        .run();
  }

  @Test
  public void testMissingProperty9() {
    disableStrictMissingPropertyChecks();

    newTest()
        .addSource(
            "/** @param {Object} obj */"
                + "function foo(obj) { if (obj.impossible) { return true; } }")
        .run();
  }

  @Test
  public void testMissingProperty10() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {Object} obj */"
                + "function foo(obj) { while (obj.impossible) { return true; } }")
        .run();
  }

  @Test
  public void testMissingProperty11() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {Object} obj */"
                + "function foo(obj) { for (;obj.impossible;) { return true; } }")
        .run();
  }

  @Test
  public void testMissingProperty12() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {Object} obj */" + "function foo(obj) { do { } while (obj.impossible); }")
        .run();
  }

  // Note: testMissingProperty{13,14} pertained to a deleted coding convention.

  @Test
  public void testMissingProperty15() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource("/** @param {Object} x */" + "function f(x) { if (x.foo) { x.foo(); } }")
        .run();
  }

  @Test
  public void testMissingProperty16() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource("/** @param {Object} x */" + "function f(x) { x.foo(); if (x.foo) {} }")
        .addDiagnostic(
            "Property foo never defined on Object" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testMissingProperty17() {
    newTest()
        .addSource(
            "/** @param {Object} x */"
                + "function f(x) { if (typeof x.foo == 'function') { x.foo(); } }")
        .run();
  }

  @Test
  public void testMissingProperty18() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {Object} x */"
                + "function f(x) { if (x.foo instanceof Function) { x.foo(); } }")
        .run();
  }

  @Test
  public void testMissingProperty19() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {Object} x */"
                + "function f(x) { if (x.bar) { if (x.foo) {} } else { x.foo(); } }")
        .addDiagnostic(
            "Property foo never defined on Object" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testMissingProperty20() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {Object} x */" + "function f(x) { if (x.foo) { } else { x.foo(); } }")
        .addDiagnostic(
            "Property foo never defined on Object" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testMissingProperty21() {
    disableStrictMissingPropertyChecks();
    newTest().addSource("/** @param {Object} x */" + "function f(x) { x.foo && x.foo(); }").run();
  }

  @Test
  public void testMissingProperty22() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {Object} x \n * @return {boolean} */"
                + "function f(x) { return x.foo ? x.foo() : true; }")
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
        "goog.forwardDeclare('MissingType');"
            + "/** @param {MissingType} x */"
            + "function f(x) { x.impossible(); }",
        null);
  }

  @Test
  public void testMissingProperty25() {
    newTest()
        .addSource(
            "/** @constructor */ var Foo = function() {};"
                + "Foo.prototype.bar = function() {};"
                + "/** @constructor */ var FooAlias = Foo;"
                + "(new FooAlias()).bar();")
        .run();
  }

  @Test
  public void testMissingProperty26() {
    newTest()
        .addSource(
            "/** @constructor */ var Foo = function() {};"
                + "/** @constructor */ var FooAlias = Foo;"
                + "FooAlias.prototype.bar = function() {};"
                + "(new Foo()).bar();")
        .run();
  }

  @Test
  public void testMissingProperty27() {
    testClosureTypes(
        "goog.forwardDeclare('MissingType');"
            + "/** @param {?MissingType} x */"
            + "function f(x) {"
            + "  for (var parent = x; parent; parent = parent.getParent()) {}"
            + "}",
        null);
  }

  @Test
  public void testMissingProperty28a() {
    newTest()
        .addSource("function f(obj) {" + "  /** @type {*} */ obj.foo;" + "  return obj.foo;" + "}")
        .run();
  }

  @Test
  public void testMissingProperty28b() {
    newTest()
        .addSource("function f(obj) {" + "  /** @type {*} */ obj.foo;" + "  return obj.foox;" + "}")
        .addDiagnostic(
            "Property foox never defined on obj" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testMissingProperty29() {
    // This used to emit a warning.
    newTest()
        .addExterns(
            "/** @constructor */ var Foo;"
                + "Foo.prototype.opera;"
                + "Foo.prototype.opera.postError;")
        .addSource("")
        .run();
  }

  @Test
  public void testMissingProperty30a() {
    newTest()
        .addSource(
            "/** @return {*} */",
            "function f() {",
            " return {};",
            "}",
            "f().a = 3;",
            "/** @param {Object} y */ function g(y) { return y.a; }")
        .addDiagnostic("Property a never defined on *")
        .addDiagnostic("Property a never defined on Object")
        .run();
  }

  @Test
  public void testMissingProperty30b() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @return {*} */"
                + "function f() {"
                + " return {};"
                + "}"
                + "f().a = 3;"
                + "/** @param {Object} y */ function g(y) { return y.a; }")
        .run();
  }

  @Test
  public void testMissingProperty31a() {
    newTest()
        .addSource(
            "/** @return {Array|number} */", //
            "function f() {",
            " return [];",
            "}",
            "f().a = 3;")
        .addDiagnostic("Property a never defined on (Array|Number)")
        .run();
  }

  @Test
  public void testMissingProperty31b() {
    disableStrictMissingPropertyChecks();

    newTest()
        .addSource(
            "/** @return {Array|number} */"
                + "function f() {"
                + " return [];"
                + "}"
                + "f().a = 3;"
                + "/** @param {Array} y */ function g(y) { return y.a; }")
        .run();
  }

  @Test
  public void testMissingProperty32() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @return {Array|number} */"
                + "function f() {"
                + " return [];"
                + "}"
                + "f().a = 3;"
                + "/** @param {Date} y */ function g(y) { return y.a; }")
        .addDiagnostic("Property a never defined on Date")
        .run();
  }

  @Test
  public void testMissingProperty33() {
    disableStrictMissingPropertyChecks();
    newTest().addSource("/** @param {Object} x */" + "function f(x) { !x.foo || x.foo(); }").run();
  }

  @Test
  public void testMissingProperty34() {
    newTest()
        .addSource(
            "/** @fileoverview \n * @suppress {missingProperties} */"
                + "/** @constructor */ function Foo() {}"
                + "Foo.prototype.bar = function() { return this.a; };"
                + "Foo.prototype.baz = function() { this.b = 3; };")
        .run();
  }

  @Test
  public void testMissingProperty35a() {
    // Bar has specialProp defined, so Bar|Baz may have specialProp defined.
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}",
            "/** @constructor */ function Bar() {}",
            "/** @constructor */ function Baz() {}",
            "/** @param {Foo|Bar} x */ function f(x) { x.specialProp = 1; }",
            "/** @param {Bar|Baz} x */ function g(x) { return x.specialProp; }")
        .addDiagnostic("Property specialProp never defined on (Foo|Bar)")
        .addDiagnostic("Property specialProp never defined on (Bar|Baz)")
        .run();
  }

  @Test
  public void testMissingProperty35b() {
    disableStrictMissingPropertyChecks();

    // Bar has specialProp defined, so Bar|Baz may have specialProp defined.
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @constructor */ function Bar() {}"
                + "/** @constructor */ function Baz() {}"
                + "/** @param {Foo|Bar} x */ function f(x) { x.specialProp = 1; }"
                + "/** @param {Bar|Baz} x */ function g(x) { return x.specialProp; }")
        .run();
  }

  @Test
  public void testMissingProperty36a() {
    // Foo has baz defined, and SubFoo has bar defined, so some objects with
    // bar may have baz.
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.baz = 0;",
            "/** @constructor \n * @extends {Foo} */ function SubFoo() {}",
            "SubFoo.prototype.bar = 0;",
            "/** @param {{bar: number}} x */ function f(x) { return x.baz; }")
        .addDiagnostic("Property baz never defined on x")
        .run();
  }

  @Test
  public void testMissingProperty36b() {
    disableStrictMissingPropertyChecks();

    // Foo has baz defined, and SubFoo has bar defined, so some objects with
    // bar may have baz.
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype.baz = 0;"
                + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
                + "SubFoo.prototype.bar = 0;"
                + "/** @param {{bar: number}} x */ function f(x) { return x.baz; }")
        .run();
  }

  @Test
  public void testMissingProperty37a() {
    // This used to emit a missing property warning because we couldn't
    // determine that the inf(Foo, {isVisible:boolean}) == SubFoo.
    newTest()
        .addSource(
            "/** @param {{isVisible: boolean}} x */",
            "function f(x){",
            "  x.isVisible = false;",
            "}",
            "/** @constructor */",
            "function Foo() {}",
            "/**",
            " * @constructor",
            " * @extends {Foo}",
            " */",
            "function SubFoo() {}",
            "/** @type {boolean} */",
            "SubFoo.prototype.isVisible = true;",
            "/**",
            " * @param {Foo} x",
            " * @return {boolean}",
            " */",
            "function g(x) { return x.isVisible; }")
        .addDiagnostic("Property isVisible never defined on Foo")
        .run();
  }

  @Test
  public void testMissingProperty37b() {
    disableStrictMissingPropertyChecks();

    // This used to emit a missing property warning because we couldn't
    // determine that the inf(Foo, {isVisible:boolean}) == SubFoo.
    newTest()
        .addSource(
            "/** @param {{isVisible: boolean}} x */ function f(x){"
                + "  x.isVisible = false;"
                + "}"
                + "/** @constructor */ function Foo() {}"
                + "/**\n"
                + " * @constructor \n"
                + " * @extends {Foo}\n"
                + " */ function SubFoo() {}"
                + "/** @type {boolean} */ SubFoo.prototype.isVisible = true;"
                + "/**\n"
                + " * @param {Foo} x\n"
                + " * @return {boolean}\n"
                + " */\n"
                + "function g(x) { return x.isVisible; }")
        .run();
  }

  @Test
  public void testMissingProperty38() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @constructor */ function Bar() {}"
                + "/** @return {Foo|Bar} */ function f() { return new Foo(); }"
                + "f().missing;")
        .addDiagnostic("Property missing never defined on (Foo|Bar)")
        .run();
  }

  @Test
  public void testMissingProperty39a() {
    disableStrictMissingPropertyChecks();
    this.newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            "/** @return {string|number} */ function f() { return 3; }", //
            "f().length;")
        .run();
  }

  @Test
  public void testMissingProperty39b() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            "/** @return {string|number} */ function f() { return 3; }", //
            "f().length;")
        .addDiagnostic("Property length not defined on all member types of (String|Number)")
        .run();
  }

  @Test
  public void testMissingProperty40a() {
    testClosureTypes(
        "goog.forwardDeclare('MissingType');"
            + "/** @param {MissingType} x */"
            + "function f(x) { x.impossible(); }",
        null);
  }

  @Test
  public void testMissingProperty40b() {
    testClosureTypes(
        "goog.forwardDeclare('MissingType');"
            + "/** @param {(Array|MissingType)} x */"
            + "function f(x) { x.impossible(); }",
        "Property impossible not defined on all member types of x");
  }

  @Test
  public void testMissingProperty41a() {
    newTest()
        .addSource(
            "/** @param {(Array|Date)} x */", //
            "function f(x) { if (x.impossible) x.impossible(); }")
        .addDiagnostic("Property impossible never defined on (Array|Date)")
        .addDiagnostic("Property impossible never defined on (Array|Date)")
        .run();
  }

  @Test
  public void testMissingProperty41b() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {(Array|Date)} x */", //
            "function f(x) { if (x.impossible) x.impossible(); }")
        .run();
  }

  @Test
  public void testMissingProperty42() {
    newTest()
        .addSource(
            "/** @param {Object} x */"
                + "function f(x) { "
                + "  if (typeof x.impossible == 'undefined') throw Error();"
                + "  return x.impossible;"
                + "}")
        .run();
  }

  @Test
  public void testMissingProperty43() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource("function f(x) { " + " return /** @type {number} */ (x.impossible) && 1;" + "}")
        .run();
  }

  @Test
  public void testMissingProperty_notReportedInPropertyAbsenceCheck() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "function f(/** !Object */ x) {", //
            "  if (x.y == null) throw new Error();",
            "}")
        .run();
  }

  // since optional chaining is a property test (tests for the existence of x.y), no warnings
  // about missing properties are emitted
  @Test
  public void optChainGetPropAllowLoosePropertyAccess() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {?Object} x */", //
            "function f(x) {",
            "  x.y?.z;",
            "}")
        .run();
  }

  // this is the same test as above except that it does not use optional chaining so it should
  // emit a warning about missing properties
  @Test
  public void normalGetPropNotAllowLoosePropertyAccess() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {?Object} x */", //
            "function f(x) {",
            "  x.y.z;",
            "}")
        .addDiagnostic(
            "Property y never defined on Object" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  // since optional chaining is a property test (tests for the existence of x.y), no warnings
  // about missing properties are emitted
  @Test
  public void optChainGetElemAllowLoosePropertyAccess() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {?Object} x */", //
            "function f(x) {",
            "  x.y?.[z];",
            "}")
        .run();
  }

  // this is the same test as above except that it does not use optional chaining so it should emit
  // a warning about missing properties
  @Test
  public void normalGetElemNotAllowLoosePropertyAccess() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {?Object} x */", //
            "function f(x) {",
            "  x.y[z];",
            "}")
        .addDiagnostic(
            "Property y never defined on Object" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  // since optional chaining is a property test (tests for the existence of x.y), no warnings
  // about missing properties are emitted
  @Test
  public void optChainCallAllowLoosePropertyAccess() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {?Object} x */", //
            "function f(x) {",
            "  x.y?.();",
            "}")
        .run();
  }

  // this is the same test as above except that it does not use optional chaining so it should emit
  // a warning about missing properties
  @Test
  public void normalCallNotAllowLoosePropertyAccess() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {?Object} x */", //
            "function f(x) {",
            "  x.y();",
            "}")
        .addDiagnostic(
            "Property y never defined on Object" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  // prop.access?.() is property test and should allow loose property access
  // but x?.(prop.access) is not
  @Test
  public void getNotFirstChildOfOptChainCallNotAllowLoosePropertyAccess() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {?Object} x */", //
            "function f(x) {",
            "  return false;",
            "}",
            "f?.(x.y)")
        .addDiagnostic("Property y never defined on x" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  // prop.access?.[x] is property test and should allow loose property access
  // but x?.[prop.access] is not
  @Test
  public void getNotFirstChildOfOptionalGetElemNotAllowLoosePropertyAccess() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {?Object} x */", //
            "function f(x) {",
            "  x?.[y.z];",
            "}")
        .addDiagnostic("Property z never defined on y" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testOptChainGetPropProvidesThisForMethodCall() {
    newTest()
        .addSource(
            "class A {",
            "  foo() {}",
            "}",
            "/** @param {?A} a */",
            "function f(a) {",
            // TypeCheck should not complain that foo() is getting called without a correctly typed
            // `this` value.
            "  a?.foo();",
            "}",
            "",
            "")
        .run();
  }

  @Test
  public void testReflectObject1() {
    testClosureTypes(
        "goog.reflect = {}; "
            + "goog.reflect.object = function(x, y){};"
            + "/** @constructor */ function A() {}"
            + "goog.reflect.object(A, {x: 3});",
        null);
  }

  @Test
  public void testReflectObject2() {
    testClosureTypes(
        "goog.reflect = {}; "
            + "goog.reflect.object = function(x, y){};"
            + "/** @param {string} x */ function f(x) {}"
            + "/** @constructor */ function A() {}"
            + "goog.reflect.object(A, {x: f(1 + 1)});",
        "actual parameter 1 of f does not match formal parameter\n"
            + "found   : number\n"
            + "required: string");
  }

  @Test
  public void testLends1() {
    newTest()
        .addSource(
            "function extend(x, y) {}"
                + "/** @constructor */ function Foo() {}"
                + "extend(Foo, /** @lends */ ({bar: 1}));")
        .addDiagnostic(
            "Bad type annotation. missing object name in @lends tag." + BAD_TYPE_WIKI_LINK)
        .run();
  }

  @Test
  public void testLends2() {
    newTest()
        .addSource(
            "function extend(x, y) {}"
                + "/** @constructor */ function Foo() {}"
                + "extend(Foo, /** @lends {Foob} */ ({bar: 1}));")
        .addDiagnostic("Variable Foob not declared before @lends annotation.")
        .run();
  }

  @Test
  public void testLends3() {
    newTest()
        .addSource(
            "function extend(x, y) {}"
                + "/** @constructor */ function Foo() {}"
                + "extend(Foo, {bar: 1});"
                + "alert(Foo.bar);")
        .addDiagnostic("Property bar never defined on Foo")
        .run();
  }

  @Test
  public void testLends4() {
    newTest()
        .addSource(
            "function extend(x, y) {}"
                + "/** @constructor */ function Foo() {}"
                + "extend(Foo, /** @lends {Foo} */ ({bar: 1}));"
                + "alert(Foo.bar);")
        .run();
  }

  @Test
  public void testLends5() {
    newTest()
        .addSource(
            "function extend(x, y) {}"
                + "/** @constructor */ function Foo() {}"
                + "extend(Foo, {bar: 1});"
                + "alert((new Foo()).bar);")
        .addDiagnostic("Property bar never defined on Foo")
        .run();
  }

  @Test
  public void testLends6() {
    newTest()
        .addSource(
            "function extend(x, y) {}"
                + "/** @constructor */ function Foo() {}"
                + "extend(Foo, /** @lends {Foo.prototype} */ ({bar: 1}));"
                + "alert((new Foo()).bar);")
        .run();
  }

  @Test
  public void testLends7() {
    newTest()
        .addSource(
            "function extend(x, y) {}"
                + "/** @constructor */ function Foo() {}"
                + "extend(Foo, /** @lends {Foo.prototype|Foo} */ ({bar: 1}));")
        .addDiagnostic("Bad type annotation. expected closing }" + BAD_TYPE_WIKI_LINK)
        .run();
  }

  @Test
  public void testLends8() {
    newTest()
        .addSource(
            "function extend(x, y) {}"
                + "/** @type {number} */ var Foo = 3;"
                + "extend(Foo, /** @lends {Foo} */ ({bar: 1}));")
        .addDiagnostic("May only lend properties to object types. Foo has type number.")
        .run();
  }

  @Test
  public void testLends9() {
    testClosureTypesMultipleWarnings(
        "function extend(x, y) {}"
            + "/** @constructor */ function Foo() {}"
            + "extend(Foo, /** @lends {!Foo} */ ({bar: 1}));",
        ImmutableList.of(
            "Bad type annotation. expected closing }" + BAD_TYPE_WIKI_LINK,
            "Bad type annotation. missing object name in @lends tag." + BAD_TYPE_WIKI_LINK));
  }

  @Test
  public void testLends10() {
    newTest()
        .addSource(
            "function defineClass(x) { return function() {}; } "
                + "/** @constructor */"
                + "var Foo = defineClass("
                + "    /** @lends {Foo.prototype} */ ({/** @type {number} */ bar: 1}));"
                + "/** @return {string} */ function f() { return (new Foo()).bar; }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testLends11() {
    newTest()
        .addSource(
            "function defineClass(x, y) { return function() {}; } "
                + "/** @constructor */"
                + "var Foo = function() {};"
                + "/** @return {*} */ Foo.prototype.bar = function() { return 3; };"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */\n"
                + "var SubFoo = defineClass(Foo, "
                + "    /** @lends {SubFoo.prototype} */ ({\n"
                + "      /** @override @return {number} */ bar: function() { return 3; }}));"
                + "/** @return {string} */ function f() { return (new SubFoo()).bar(); }")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
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
            "/** @param {number} a \n"
                + "* @param {number} b */\n"
                + "function f(a, b) {\n"
                + "/** @type {number} */"
                + "var i = 0;"
                + "for (; (i + a) < b; ++i) {}}");

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
            "/** @constructor */ function Foo() {};\n"
                + "Foo.prototype.hi = false;"
                + "function foo(a, b) {\n"
                + "  /** @type {Array} */"
                + "  var arr;"
                + "  /** @type {number} */"
                + "  var iter;"
                + "  for (iter = 0; iter < arr.length; ++ iter) {"
                + "    /** @type {Foo} */"
                + "    var afoo = arr[iter];"
                + "    afoo;"
                + "  }"
                + "}");

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
            "/** @constructor */ function Foo() {};\n" + "goog.addSingletonGetter(Foo);");
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
    assertThat(compiler.getWarnings().get(0).getDescription())
        .isEqualTo("cannot instantiate non-constructor");
  }

  @Test
  public void testUpdateParameterTypeOnClosure() {
    newTest()
        .addExterns(
            "/**\n"
                + "* @constructor\n"
                + "* @param {*=} opt_value\n"
                + "* @return {!Object}\n"
                + "*/\n"
                + "function Object(opt_value) {}\n"
                + "/**\n"
                + "* @constructor\n"
                + "* @param {...*} var_args\n"
                + "*/\n"
                + "function Function(var_args) {}\n"
                + "/**\n"
                + "* @type {Function}\n"
                + "*/\n"
                +
                // The line below sets JSDocInfo on Object so that the type of the
                // argument to function f has JSDoc through its prototype chain.
                "Object.prototype.constructor = function() {};\n")
        .addSource(
            "/**\n"
                + "* @param {function(): boolean} fn\n"
                + "*/\n"
                + "function f(fn) {}\n"
                + "f(function() { });\n")
        .run();
  }

  @Test
  public void testTemplatedThisType1() {
    newTest()
        .addSource(
            "/** @constructor */\n"
                + "function Foo() {}\n"
                + "/**\n"
                + " * @this {T}\n"
                + " * @return {T}\n"
                + " * @template T\n"
                + " */\n"
                + "Foo.prototype.method = function() {};\n"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */\n"
                + "function Bar() {}\n"
                + "var g = new Bar().method();\n"
                + "/**\n"
                + " * @param {number} a\n"
                + " */\n"
                + "function compute(a) {};\n"
                + "compute(g);\n")
        .addDiagnostic(
            "actual parameter 1 of compute does not match formal parameter\n"
                + "found   : Bar\n"
                + "required: number")
        .run();
  }

  @Test
  public void testTemplatedThisType2() {
    newTest()
        .addSource(
            "/**\n"
                + " * @this {Array<T>|{length:number}}\n"
                + " * @return {T}\n"
                + " * @template T\n"
                + " */\n"
                + "Array.prototype.method = function() {};\n"
                + "(function(){\n"
                + "  Array.prototype.method.call(arguments);"
                + "})();")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testTemplateType1() {
    newTest()
        .addSource(
            "/**\n"
                + "* @param {T} x\n"
                + "* @param {T} y\n"
                + "* @param {function(this:T, ...)} z\n"
                + "* @template T\n"
                + "*/\n"
                + "function f(x, y, z) {}\n"
                + "f(this, this, function() { this });")
        .run();
  }

  @Test
  public void testTemplateType2() {
    // "this" types need to be coerced for ES3 style function or left
    // allow for ES5-strict methods.
    newTest()
        .addSource(
            "/**\n"
                + "* @param {T} x\n"
                + "* @param {function(this:T, ...)} y\n"
                + "* @template T\n"
                + "*/\n"
                + "function f(x, y) {}\n"
                + "f(0, function() {});")
        .run();
  }

  @Test
  public void testTemplateType3() {
    newTest()
        .addSource(
            "/**"
                + " * @param {T} v\n"
                + " * @param {function(T)} f\n"
                + " * @template T\n"
                + " */\n"
                + "function call(v, f) { f.call(null, v); }"
                + "/** @type {string} */ var s;"
                + "call(3, function(x) {"
                + " x = true;"
                + " s = x;"
                + "});")
        .addDiagnostic("assignment\n" + "found   : boolean\n" + "required: string")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testTemplateType4() {
    newTest()
        .addSource(
            "/**"
                + " * @param {...T} p\n"
                + " * @return {T} \n"
                + " * @template T\n"
                + " */\n"
                + "function fn(p) { return p; }\n"
                + "/** @type {!Object} */ var x;"
                + "x = fn(3, null);")
        .addDiagnostic("assignment\n" + "found   : (null|number)\n" + "required: Object")
        .run();
  }

  @Test
  public void testTemplateType5() {
    newTest()
        .addSource(
            "const CGI_PARAM_RETRY_COUNT = 'rc';",
            "",
            "/**",
            " * @param {...T} p",
            " * @return {T} ",
            " * @template T",
            " */",
            "function fn(p) { return p; }",
            "/** @type {!Object} */ var x;",
            "",
            "/** @return {void} */",
            "function aScope() {",
            "  x = fn(CGI_PARAM_RETRY_COUNT, 1);",
            "}")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : (number|string)",
                "required: Object"))
        .run();
  }

  @Test
  public void testTemplateType6() {
    newTest()
        .addSource(
            "/**"
                + " * @param {Array<T>} arr \n"
                + " * @param {?function(T)} f \n"
                + " * @return {T} \n"
                + " * @template T\n"
                + " */\n"
                + "function fn(arr, f) { return arr[0]; }\n"
                + "/** @param {Array<number>} arr */ function g(arr) {"
                + "  /** @type {!Object} */ var x = fn.call(null, arr, null);"
                + "}")
        .addDiagnostic("initializing variable\n" + "found   : number\n" + "required: Object")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testTemplateType7() {
    this.newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            "/** @type {!Array<string>} */", //
            "var query = [];",
            "query.push(1);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Array.prototype.push does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testTemplateType8() {
    newTest()
        .addSource(
            "/** @constructor \n"
                + " * @template S,T\n"
                + " */\n"
                + "function Bar() {}\n"
                + "/**"
                + " * @param {Bar<T>} bar \n"
                + " * @return {T} \n"
                + " * @template T\n"
                + " */\n"
                + "function fn(bar) {}\n"
                + "/** @param {Bar<number>} bar */ function g(bar) {"
                + "  /** @type {!Object} */ var x = fn(bar);"
                + "}")
        .addDiagnostic("initializing variable\n" + "found   : number\n" + "required: Object")
        .run();
  }

  @Test
  public void testTemplateType9() {
    // verify interface type parameters are recognized.
    newTest()
        .addSource(
            "/** @interface \n"
                + " * @template S,T\n"
                + " */\n"
                + "function Bar() {}\n"
                + "/**"
                + " * @param {Bar<T>} bar \n"
                + " * @return {T} \n"
                + " * @template T\n"
                + " */\n"
                + "function fn(bar) {}\n"
                + "/** @param {Bar<number>} bar */ function g(bar) {"
                + "  /** @type {!Object} */ var x = fn(bar);"
                + "}")
        .addDiagnostic("initializing variable\n" + "found   : number\n" + "required: Object")
        .run();
  }

  @Test
  public void testTemplateType10() {
    // verify a type parameterized with unknown can be assigned to
    // the same type with any other type parameter.
    newTest()
        .addSource(
            "/** @constructor \n"
                + " * @template T\n"
                + " */\n"
                + "function Bar() {}\n"
                + "\n"
                + ""
                + "/** @type {!Bar<?>} */ var x;"
                + "/** @type {!Bar<number>} */ var y;"
                + "y = x;")
        .run();
  }

  @Test
  public void testTemplateType11() {
    // verify that assignment/subtype relationships work when extending
    // templatized types.
    newTest()
        .addSource(
            "/** @constructor \n"
                + " * @template T\n"
                + " */\n"
                + "function Foo() {}\n"
                + ""
                + "/** @constructor \n"
                + " * @extends {Foo<string>}\n"
                + " */\n"
                + "function A() {}\n"
                + ""
                + "/** @constructor \n"
                + " * @extends {Foo<number>}\n"
                + " */\n"
                + "function B() {}\n"
                + ""
                + "/** @type {!Foo<string>} */ var a = new A();\n"
                + "/** @type {!Foo<string>} */ var b = new B();")
        .addDiagnostic("initializing variable\n" + "found   : B\n" + "required: Foo<string>")
        .run();
  }

  @Test
  public void testTemplateType12() {
    // verify that assignment/subtype relationships work when implementing
    // templatized types.
    newTest()
        .addSource(
            "/** @interface \n"
                + " * @template T\n"
                + " */\n"
                + "function Foo() {}\n"
                + ""
                + "/** @constructor \n"
                + " * @implements {Foo<string>}\n"
                + " */\n"
                + "function A() {}\n"
                + ""
                + "/** @constructor \n"
                + " * @implements {Foo<number>}\n"
                + " */\n"
                + "function B() {}\n"
                + ""
                + "/** @type {!Foo<string>} */ var a = new A();\n"
                + "/** @type {!Foo<string>} */ var b = new B();")
        .addDiagnostic("initializing variable\n" + "found   : B\n" + "required: Foo<string>")
        .run();
  }

  @Test
  public void testTemplateType13() {
    // verify that assignment/subtype relationships work when extending
    // templatized types.
    newTest()
        .addSource(
            "/** @constructor \n"
                + " * @template T\n"
                + " */\n"
                + "function Foo() {}\n"
                + ""
                + "/** @constructor \n"
                + " * @template T\n"
                + " * @extends {Foo<T>}\n"
                + " */\n"
                + "function A() {}\n"
                + ""
                + "var a1 = new A();\n"
                + "var a2 = /** @type {!A<string>} */ (new A());\n"
                + "var a3 = /** @type {!A<number>} */ (new A());\n"
                + "/** @type {!Foo<string>} */ var f1 = a1;\n"
                + "/** @type {!Foo<string>} */ var f2 = a2;\n"
                + "/** @type {!Foo<string>} */ var f3 = a3;")
        .addDiagnostic(
            "initializing variable\n" + "found   : A<number>\n" + "required: Foo<string>")
        .run();
  }

  @Test
  public void testTemplateType14() {
    // verify that assignment/subtype relationships work when implementing
    // templatized types.
    newTest()
        .addSource(
            "/** @interface \n"
                + " * @template T\n"
                + " */\n"
                + "function Foo() {}\n"
                + ""
                + "/** @constructor \n"
                + " * @template T\n"
                + " * @implements {Foo<T>}\n"
                + " */\n"
                + "function A() {}\n"
                + ""
                + "var a1 = new A();\n"
                + "var a2 = /** @type {!A<string>} */ (new A());\n"
                + "var a3 = /** @type {!A<number>} */ (new A());\n"
                + "/** @type {!Foo<string>} */ var f1 = a1;\n"
                + "/** @type {!Foo<string>} */ var f2 = a2;\n"
                + "/** @type {!Foo<string>} */ var f3 = a3;")
        .addDiagnostic(
            "initializing variable\n" + "found   : A<number>\n" + "required: Foo<string>")
        .run();
  }

  @Test
  public void testTemplateType15() {
    newTest()
        .addSource(
            "/**"
                + " * @param {{foo:T}} p\n"
                + " * @return {T} \n"
                + " * @template T\n"
                + " */\n"
                + "function fn(p) { return p.foo; }\n"
                + "/** @type {!Object} */ var x;"
                + "x = fn({foo:3});")
        .addDiagnostic("assignment\n" + "found   : number\n" + "required: Object")
        .run();
  }

  @Test
  public void testTemplateType16() {
    newTest()
        .addSource(
            "/** @constructor */ function C() {\n"
                + "  /** @type {number} */ this.foo = 1\n"
                + "}\n"
                + "/**\n"
                + " * @param {{foo:T}} p\n"
                + " * @return {T} \n"
                + " * @template T\n"
                + " */\n"
                + "function fn(p) { return p.foo; }\n"
                + "/** @type {!Object} */ var x;"
                + "x = fn(new C());")
        .addDiagnostic("assignment\n" + "found   : number\n" + "required: Object")
        .run();
  }

  @Test
  public void testTemplateType17() {
    newTest()
        .addSource(
            "/** @constructor */ function C() {}\n"
                + "C.prototype.foo = 1;\n"
                + "/**\n"
                + " * @param {{foo:T}} p\n"
                + " * @return {T} \n"
                + " * @template T\n"
                + " */\n"
                + "function fn(p) { return p.foo; }\n"
                + "/** @type {!Object} */ var x;"
                + "x = fn(new C());")
        .addDiagnostic("assignment\n" + "found   : number\n" + "required: Object")
        .run();
  }

  @Test
  public void testTemplateType18() {
    // Until template types can be restricted to exclude undefined, they
    // are always optional.
    newTest()
        .addSource(
            "/** @constructor */ function C() {}\n"
                + "C.prototype.foo = 1;\n"
                + "/**\n"
                + " * @param {{foo:T}} p\n"
                + " * @return {T} \n"
                + " * @template T\n"
                + " */\n"
                + "function fn(p) { return p.foo; }\n"
                + "/** @type {!Object} */ var x;"
                + "x = fn({});")
        .run();
  }

  @Test
  public void testTemplateType19() {
    newTest()
        .addSource(
            "/**\n"
                + " * @param {T} t\n"
                + " * @param {U} u\n"
                + " * @return {{t:T, u:U}} \n"
                + " * @template T,U\n"
                + " */\n"
                + "function fn(t, u) { return {t:t, u:u}; }\n"
                + "/** @type {null} */ var x = fn(1, 'str');")
        .addDiagnostic(
            "initializing variable\n"
                + "found   : {\n  t: number,\n  u: string\n}\n"
                + "required: null")
        .run();
  }

  @Test
  public void testTemplateType20() {
    // "this" types is inferred when the parameters are declared.
    newTest()
        .addSource(
            "/** @constructor */ function C() {\n"
                + "  /** @type {void} */ this.x;\n"
                + "}\n"
                + "/**\n"
                + "* @param {T} x\n"
                + "* @param {function(this:T, ...)} y\n"
                + "* @template T\n"
                + "*/\n"
                + "function f(x, y) {}\n"
                + "f(new C, /** @param {number} a */ function(a) {this.x = a;});")
        .addDiagnostic(
            "assignment to property x of C\n" + "found   : number\n" + "required: undefined")
        .run();
  }

  @Test
  public void testTemplateType21() {
    // "this" types is inferred when the parameters are declared.
    newTest()
        .addSource(
            "/** @interface @template T */ function A() {}\n"
                + "/** @constructor @implements {A<Foo>} */\n"
                + "function Foo() {}\n"
                + "/** @constructor @implements {A<Bar>} */\n"
                + "function Bar() {}\n"
                + "/** @type {!Foo} */\n"
                + "var x = new Bar();\n")
        .addDiagnostic("initializing variable\n" + "found   : Bar\n" + "required: Foo")
        .run();
  }

  @Test
  public void testTemplateType22() {
    // "this" types is inferred when the parameters are declared.
    newTest()
        .addSource(
            "/** @interface @template T */ function A() {}\n"
                + "/** @interface @template T */ function B() {}\n"
                + "/** @constructor @implements {A<Foo>} */\n"
                + "function Foo() {}\n"
                + "/** @constructor @implements {B<Foo>} */\n"
                + "function Bar() {}\n"
                + "/** @constructor @implements {B<Foo>} */\n"
                + "function Qux() {}\n"
                + "/** @type {!Qux} */\n"
                + "var x = new Bar();\n")
        .addDiagnostic("initializing variable\n" + "found   : Bar\n" + "required: Qux")
        .run();
  }

  @Test
  public void testTemplateType23() {
    // "this" types is inferred when the parameters are declared.
    newTest()
        .addSource(
            "/** @interface @template T */ function A() {}\n"
                + "/** @constructor @implements {A<Foo>} */\n"
                + "function Foo() {}\n"
                + "/** @type {!Foo} */\n"
                + "var x = new Foo();\n")
        .run();
  }

  @Test
  public void testTemplateType24() {
    // Recursive templated type definition.
    newTest()
        .addSource(
            "/**",
            " * @constructor",
            " * @template T",
            " * @param {T} x",
            " */",
            "function Foo(x) {",
            "  /** @type {T} */",
            "  this.p = x;",
            "}",
            "/** @return {Foo<Foo<T>>} */",
            "Foo.prototype.m = function() {",
            "  return null;",
            "};",
            "/** @return {T} */",
            "Foo.prototype.get = function() {",
            "  return this.p;",
            "};",
            "var /** null */ n = new Foo(new Object).m().get();")
        .addDiagnostic(
            "initializing variable\n" + "found   : (Foo<Object>|null)\n" + "required: null")
        .run();
  }

  @Test
  public void testTemplateType25() {
    // Non-nullable recursive templated type definition.
    newTest()
        .addSource(
            "/**",
            " * @constructor",
            " * @template T",
            " * @param {T} x",
            " */",
            "function Foo(x) {",
            "  /** @type {T} */",
            "  this.p = x;",
            "}",
            "/** @return {!Foo<!Foo<T>>} */",
            "Foo.prototype.m = function() {",
            "  return new Foo(new Foo(new Object));",
            "};",
            "/** @return {T} */",
            "Foo.prototype.get = function() {",
            "  return this.p;",
            "};",
            "var /** null */ n = new Foo(new Object).m().get();")
        .addDiagnostic("initializing variable\n" + "found   : Foo<Object>\n" + "required: null")
        .run();
  }

  @Test
  public void testTemplateType26() {
    // Class hierarchies which use the same template parameter name should not be treated as
    // infinite recursion.
    newTest()
        .addSource(
            "/**",
            " * @param {T} bar",
            " * @constructor",
            " * @template T",
            " */",
            "function Bar(bar) {",
            "  /** @type {T} */",
            "  this.bar = bar;",
            "}",
            "/** @return {T} */",
            "Bar.prototype.getBar = function() {",
            "  return this.bar;",
            "};",
            "/**",
            " * @param {T} foo",
            " * @constructor",
            " * @template T",
            " * @extends {Bar<!Array<T>>}",
            " */",
            "function Foo(foo) {",
            "  /** @type {T} */",
            "  this.foo = foo;",
            "}",
            "var /** null */ n = new Foo(new Object).getBar();")
        .addDiagnostic("initializing variable\n" + "found   : Array<Object>\n" + "required: null")
        .run();
  }

  @Test
  public void testTemplateTypeCollidesWithParameter() {
    // Function templates are in the same scope as parameters, so cannot collide.
    newTest()
        .addSource(
            "/**", //
            " * @param {T} T",
            " * @template T",
            " */",
            "function f(T) {}")
        .addDiagnostic(
            "variable T redefined with type undefined, original definition at [testcode]:5 with"
                + " type T")
        .run();
  }

  @Test
  public void testTemplateTypeForwardReference() {
    // TODO(martinprobst): the test below asserts incorrect behavior for backwards compatibility.
    newTest()
        .addSource(
            "/** @param {!Foo<string>} x */",
            "function f(x) {}",
            "",
            "/**",
            " * @template T",
            " * @constructor",
            " */",
            "function Foo() {}",
            "",
            "/** @param {!Foo<number>} x */",
            "function g(x) {",
            "  f(x);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : Foo<number>",
                "required: Foo<string>"))
        .run();
  }

  @Test
  public void testTemplateTypeForwardReference_declared() {
    compiler.forwardDeclareType("Foo");
    newTest()
        .addSource(
            "/** @param {!Foo<string>} x */",
            "function f(x) {}",
            "",
            "/**",
            " * @template T",
            " * @constructor",
            " */",
            "function Foo() {}",
            "",
            "/** @param {!Foo<number>} x */",
            "function g(x) {",
            "  f(x);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : Foo<number>",
                "required: Foo<string>"))
        .run();
  }

  @Test
  public void testTemplateTypeForwardReferenceFunctionWithExtra() {
    // TODO(johnlenz): report an error when forward references contain extraneous
    // type arguments.
    newTest()
        .addSource(
            "/** @param {!Foo<string, boolean>} x */",
            "function f(x) {}",
            "",
            "/**",
            " * @constructor",
            " * @template T",
            " */",
            "function Foo() {}",
            "",
            "/** @param {!Foo<number>} x */",
            "function g(x) {",
            "  f(x);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : Foo<number>",
                "required: Foo<string>"))
        .run();
  }

  @Test
  public void testTemplateTypeForwardReferenceVar() {
    newTest()
        .addSource(
            "/** @param {!Foo<string>} x */",
            "function f(x) {}",
            "",
            "/**",
            " * @template T",
            " * @constructor",
            " */",
            "var Foo = function() {}",
            "",
            "/** @param {!Foo<number>} x */",
            "function g(x) {",
            "  f(x);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : Foo<number>",
                "required: Foo<string>"))
        .run();
  }

  @Test
  public void testTemplateTypeForwardReference_declaredMissing() {
    compiler.forwardDeclareType("Foo");
    compiler.forwardDeclareType("DoesNotExist");
    newTest()
        .addSource(
            "/** @param {!Foo<DoesNotExist>} x */", //
            "function f(x) {}")
        .run();
  }

  @Test
  public void testTemplateTypeForwardReference_extends() {
    compiler.forwardDeclareType("Bar");
    compiler.forwardDeclareType("Baz");
    newTest()
        .addSource(
            "/** @constructor @extends {Bar<Baz>} */",
            "function Foo() {}",
            "/** @constructor */",
            "function Bar() {}")
        .run();
  }

  @Test
  public void testSubtypeNotTemplated1() {
    newTest()
        .addSource(
            "/** @interface @template T */ function A() {}",
            "/** @constructor @implements {A<U>} @template U */ function Foo() {}",
            "function f(/** (!Object|!Foo<string>) */ x) {",
            "  var /** null */ n = x;",
            "}")
        .addDiagnostic("initializing variable\n" + "found   : Object\n" + "required: null")
        .run();
  }

  @Test
  public void testSubtypeNotTemplated2() {
    newTest()
        .addSource(
            "/** @interface @template T */ function A() {}",
            "/** @constructor @implements {A<U>} @template U */ function Foo() {}",
            "function f(/** (!Object|!Foo) */ x) {",
            "  var /** null */ n = x;",
            "}")
        .addDiagnostic("initializing variable\n" + "found   : Object\n" + "required: null")
        .run();
  }

  @Test
  public void testTemplateTypeWithUnresolvedType() {
    testClosureTypes(
        "goog.forwardDeclare('Color');\n"
            + "/** @interface @template T */ function C() {}\n"
            + "/** @return {!Color} */ C.prototype.method;\n"
            + "/** @constructor @implements {C} */ function D() {}\n"
            + "/** @override */ D.prototype.method = function() {};",
        null); // no warning expected.
  }

  @Test
  public void testTemplateTypeWithTypeDef1a() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @template T\n"
                + " * @param {T} x\n"
                + " */\n"
                + "function Generic(x) {}\n"
                + "\n"
                + "/** @constructor */\n"
                + "function Foo() {}\n"
                + ""
                + "/** @typedef {!Foo} */\n"
                + "var Bar;\n"
                + ""
                + "/** @type {Generic<!Foo>} */ var x;\n"
                + "/** @type {Generic<!Bar>} */ var y;\n"
                + ""
                + "x = y;\n"
                + // no warning
                "/** @type {null} */ var z1 = y;\n")
        .addDiagnostic(
            "initializing variable\n" + "found   : (Generic<Foo>|null)\n" + "required: null")
        .run();
  }

  @Test
  public void testTemplateTypeWithTypeDef1b() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @template T\n"
                + " * @param {T} x\n"
                + " */\n"
                + "function Generic(x) {}\n"
                + "\n"
                + "/** @constructor */\n"
                + "function Foo() {}\n"
                + ""
                + "/** @typedef {!Foo} */\n"
                + "var Bar;\n"
                + ""
                + "/** @type {Generic<!Foo>} */ var x;\n"
                + "/** @type {Generic<!Bar>} */ var y;\n"
                + ""
                + "y = x;\n"
                + // no warning.
                "/** @type {null} */ var z1 = x;\n")
        .addDiagnostic(
            "initializing variable\n" + "found   : (Generic<Foo>|null)\n" + "required: null")
        .run();
  }

  @Test
  public void testTemplateTypeWithTypeDef2a() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @template T\n"
                + " * @param {T} x\n"
                + " */\n"
                + "function Generic(x) {}\n"
                + "\n"
                + "/** @constructor */\n"
                + "function Foo() {}\n"
                + "\n"
                + "/** @typedef {!Foo} */\n"
                + "var Bar;\n"
                + "\n"
                + "function f(/** Generic<!Bar> */ x) {}\n"
                + "/** @type {Generic<!Foo>} */ var x;\n"
                + "f(x);\n")
        .run(); // no warning expected.
  }

  @Test
  public void testTemplateTypeWithTypeDef2b() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @template T\n"
                + " * @param {T} x\n"
                + " */\n"
                + "function Generic(x) {}\n"
                + "\n"
                + "/** @constructor */\n"
                + "function Foo() {}\n"
                + "\n"
                + "/** @typedef {!Foo} */\n"
                + "var Bar;\n"
                + "\n"
                + "function f(/** Generic<!Bar> */ x) {}\n"
                + "/** @type {Generic<!Bar>} */ var x;\n"
                + "f(x);\n")
        .run(); // no warning expected.
  }

  @Test
  public void testTemplateTypeWithTypeDef2c() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @template T\n"
                + " * @param {T} x\n"
                + " */\n"
                + "function Generic(x) {}\n"
                + "\n"
                + "/** @constructor */\n"
                + "function Foo() {}\n"
                + "\n"
                + "/** @typedef {!Foo} */\n"
                + "var Bar;\n"
                + "\n"
                + "function f(/** Generic<!Foo> */ x) {}\n"
                + "/** @type {Generic<!Foo>} */ var x;\n"
                + "f(x);\n")
        .run(); // no warning expected.
  }

  @Test
  public void testTemplateTypeWithTypeDef2d() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @template T\n"
                + " * @param {T} x\n"
                + " */\n"
                + "function Generic(x) {}\n"
                + "\n"
                + "/** @constructor */\n"
                + "function Foo() {}\n"
                + "\n"
                + "/** @typedef {!Foo} */\n"
                + "var Bar;\n"
                + "\n"
                + "function f(/** Generic<!Foo> */ x) {}\n"
                + "/** @type {Generic<!Bar>} */ var x;\n"
                + "f(x);\n")
        .run(); // no warning expected.
  }

  @Test
  public void testTemplatedFunctionInUnion1() {
    newTest()
        .addSource(
            "/**",
            "* @param {T} x",
            "* @param {function(this:T, ...)|{fn:Function}} z",
            "* @template T",
            "*/",
            "function f(x, z) {}",
            "f([], function() { /** @type {string} */ var x = this });")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Array<?>",
                "required: string"))
        .run();
  }

  @Test
  public void testTemplateTypeRecursion1() {
    newTest()
        .addSource(
            "/** @typedef {{a: D2}} */\n"
                + "var D1;\n"
                + "\n"
                + "/** @typedef {{b: D1}} */\n"
                + "var D2;\n"
                + "\n"
                + "fn(x);\n"
                + "\n"
                + "\n"
                + "/**\n"
                + " * @param {!D1} s\n"
                + " * @template T\n"
                + " */\n"
                + "var fn = function(s) {};")
        .run();
  }

  @Test
  public void testTemplateTypeRecursion2() {
    newTest()
        .addSource(
            "/** @typedef {{a: D2}} */\n"
                + "var D1;\n"
                + "\n"
                + "/** @typedef {{b: D1}} */\n"
                + "var D2;\n"
                + "\n"
                + "/** @type {D1} */ var x;"
                + "fn(x);\n"
                + "\n"
                + "\n"
                + "/**\n"
                + " * @param {!D1} s\n"
                + " * @template T\n"
                + " */\n"
                + "var fn = function(s) {};")
        .run();
  }

  @Test
  public void testTemplateTypeRecursion3() {
    newTest()
        .addSource(
            "/** @typedef {{a: function(D2)}} */\n"
                + "var D1;\n"
                + "\n"
                + "/** @typedef {{b: D1}} */\n"
                + "var D2;\n"
                + "\n"
                + "/** @type {D1} */ var x;"
                + "fn(x);\n"
                + "\n"
                + "\n"
                + "/**\n"
                + " * @param {!D1} s\n"
                + " * @template T\n"
                + " */\n"
                + "var fn = function(s) {};")
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
            ""
                + "/**\n"
                + " * @param {function(this:T, ...)?} fn\n"
                + " * @param {?T} opt_obj\n"
                + " * @template T\n"
                + " */\n"
                + "function baz(fn, opt_obj) {}\n"
                + "baz(function() { this; });")
        .addDiagnostic("Function literal argument refers to undefined this argument")
        .run();
  }

  @Test
  public void testFunctionLiteralDefinedThisArgument() {
    newTest()
        .addSource(
            ""
                + "/**\n"
                + " * @param {function(this:T, ...)?} fn\n"
                + " * @param {?T} opt_obj\n"
                + " * @template T\n"
                + " */\n"
                + "function baz(fn, opt_obj) {}\n"
                + "baz(function() { this; }, {});")
        .run();
  }

  @Test
  public void testFunctionLiteralDefinedThisArgument2() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            "/** @param {string} x */ function f(x) {}",
            "/**",
            " * @param {?function(this:T, ...)} fn",
            " * @param {T=} opt_obj",
            " * @template T",
            " */",
            "function baz(fn, opt_obj) {}",
            "function g() { baz(function() { f(this.length); }, []); }")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testFunctionLiteralUnreadNullThisArgument() {
    newTest()
        .addSource(
            ""
                + "/**\n"
                + " * @param {function(this:T, ...)?} fn\n"
                + " * @param {?T} opt_obj\n"
                + " * @template T\n"
                + " */\n"
                + "function baz(fn, opt_obj) {}\n"
                + "baz(function() {}, null);")
        .run();
  }

  @Test
  public void testUnionTemplateThisType() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {}"
                + "/** @return {F|Array} */ function g() { return []; }"
                + "/** @param {F} x */ function h(x) { }"
                + "/**\n"
                + "* @param {T} x\n"
                + "* @param {function(this:T, ...)} y\n"
                + "* @template T\n"
                + "*/\n"
                + "function f(x, y) {}\n"
                + "f(g(), function() { h(this); });")
        .addDiagnostic(
            "actual parameter 1 of h does not match formal parameter\n"
                + "found   : (Array|F|null)\n"
                + "required: (F|null)")
        .run();
  }

  @Test
  public void testRecordType1() {
    newTest()
        .addSource(
            "/** @param {{prop: number}} x */", //
            "function f(x) {}",
            "f({});")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : {prop: (number|undefined)}",
                "required: {prop: number}",
                "missing : []",
                "mismatch: [prop]"))
        .run();
  }

  @Test
  public void testRecordType2() {
    newTest()
        .addSource("/** @param {{prop: (number|undefined)}} x */" + "function f(x) {}" + "f({});")
        .run();
  }

  @Test
  public void testRecordType3() {
    newTest()
        .addSource(
            "/** @param {{prop: number}} x */", //
            "function f(x) {}",
            "f({prop: 'x'});")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : {prop: (number|string)}",
                "required: {prop: number}",
                "missing : []",
                "mismatch: [prop]"))
        .run();
  }

  @Test
  public void testRecordType4() {
    // Notice that we do not do flow-based inference on the object type:
    // We don't try to prove that x.prop may not be string until x
    // gets passed to g.
    testClosureTypesMultipleWarnings(
        "/** @param {{prop: (number|undefined)}} x */"
            + "function f(x) {}"
            + "/** @param {{prop: (string|undefined)}} x */"
            + "function g(x) {}"
            + "var x = {}; f(x); g(x);",
        ImmutableList.of(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : {prop: (number|string|undefined)}",
                "required: {prop: (number|undefined)}",
                "missing : []",
                "mismatch: [prop]"),
            lines(
                "actual parameter 1 of g does not match formal parameter",
                "found   : {prop: (number|string|undefined)}",
                "required: {prop: (string|undefined)}",
                "missing : []",
                "mismatch: [prop]")));
  }

  @Test
  public void testRecordType5() {
    newTest()
        .addSource(
            "/** @param {{prop: (number|undefined)}} x */"
                + "function f(x) {}"
                + "/** @param {{otherProp: (string|undefined)}} x */"
                + "function g(x) {}"
                + "var x = {}; f(x); g(x);")
        .run();
  }

  @Test
  public void testRecordType6() {
    newTest()
        .addSource("/** @return {{prop: (number|undefined)}} x */" + "function f() { return {}; }")
        .run();
  }

  @Test
  public void testRecordType7() {
    newTest()
        .addSource(
            "/** @return {{prop: (number|undefined)}} x */"
                + "function f() { var x = {}; g(x); return x; }"
                + "/** @param {number} x */"
                + "function g(x) {}")
        .addDiagnostic(
            "actual parameter 1 of g does not match formal parameter\n"
                + "found   : {prop: (number|undefined)}\n"
                + "required: number")
        .run();
  }

  @Test
  public void testRecordType8() {
    newTest()
        .addSource(
            "/** @return {{prop: (number|string)}} x */"
                + "function f() { var x = {prop: 3}; g(x.prop); return x; }"
                + "/** @param {string} x */"
                + "function g(x) {}")
        .addDiagnostic(
            "actual parameter 1 of g does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testDuplicateRecordFields1() {
    newTest()
        .addSource("/**" + "* @param {{x:string, x:number}} a" + "*/" + "function f(a) {};")
        .addDiagnostic("Bad type annotation. Duplicate record field x." + BAD_TYPE_WIKI_LINK)
        .run();
  }

  @Test
  public void testDuplicateRecordFields2() {
    newTest()
        .addSource(
            "/**" + "* @param {{name:string,number:x,number:y}} a" + " */" + "function f(a) {};")
        .addDiagnostic("Bad type annotation. Unknown type x")
        .addDiagnostic("Bad type annotation. Duplicate record field number." + BAD_TYPE_WIKI_LINK)
        .run();
  }

  @Test
  public void testMultipleExtendsInterface1() {
    newTest()
        .addSource(
            "/** @interface */ function base1() {}\n"
                + "/** @interface */ function base2() {}\n"
                + "/** @interface\n"
                + "* @extends {base1}\n"
                + "* @extends {base2}\n"
                + "*/\n"
                + "function derived() {}")
        .run();
  }

  @Test
  public void testMultipleExtendsInterface2() {
    newTest()
        .addSource(
            "/** @interface */function Int0() {};"
                + "/** @interface */function Int1() {};"
                + "Int0.prototype.foo = function() {};"
                + "/** @interface \n @extends {Int0} \n @extends {Int1} */"
                + "function Int2() {};"
                + "/** @constructor\n @implements {Int2} */function Foo() {};")
        .addDiagnostic("property foo on interface Int0 is not implemented by type Foo")
        .run();
  }

  @Test
  public void testMultipleExtendsInterface3() {
    newTest()
        .addSource(
            "/** @interface */function Int0() {};"
                + "/** @interface */function Int1() {};"
                + "Int1.prototype.foo = function() {};"
                + "/** @interface \n @extends {Int0} \n @extends {Int1} */"
                + "function Int2() {};"
                + "/** @constructor\n @implements {Int2} */function Foo() {};")
        .addDiagnostic("property foo on interface Int1 is not implemented by type Foo")
        .run();
  }

  @Test
  public void testMultipleExtendsInterface4() {
    newTest()
        .addSource(
            "/** @interface */function Int0() {};"
                + "/** @interface */function Int1() {};"
                + "/** @interface \n @extends {Int0} \n @extends {Int1} \n"
                + " @extends {number} */"
                + "function Int2() {};"
                + "/** @constructor\n @implements {Int2} */function Foo() {};")
        .addDiagnostic("Int2 @extends non-object type number")
        .run();
  }

  @Test
  public void testMultipleExtendsInterface5() {
    newTest()
        .addSource(
            "/** @interface */function Int0() {};"
                + "/** @constructor */function Int1() {};"
                + "/** @return {string} x */"
                + "/** @interface \n @extends {Int0} \n @extends {Int1} */"
                + "function Int2() {};")
        .addDiagnostic("Int2 cannot extend this type; interfaces can only extend interfaces")
        .run();
  }

  @Test
  public void testMultipleExtendsInterface6() {
    newTest()
        .addSource(
            "/** @interface */function Super1() {};",
            "/** @interface */function Super2() {};",
            "/** @param {number} bar */Super2.prototype.foo = function(bar) {};",
            "/** @interface @extends {Super1} @extends {Super2} */function Sub() {};",
            "/** @override @param {string} bar */Sub.prototype.foo =",
            "function(bar) {};")
        .addDiagnostic(
            lines(
                "mismatch of the foo property on type Sub and the type of the property it "
                    + "overrides from interface Super2",
                "original: function(this:Super2, number): undefined",
                "override: function(this:Sub, string): undefined"))
        .run();
  }

  @Test
  public void testMultipleExtendsInterfaceAssignment() {
    newTest()
        .addSource(
            "/** @interface */var I1 = function() {};\n"
                + "/** @interface */ var I2 = function() {}\n"
                + "/** @interface\n@extends {I1}\n@extends {I2}*/"
                + "var I3 = function() {};\n"
                + "/** @constructor\n@implements {I3}*/var T = function() {};\n"
                + "var t = new T();\n"
                + "/** @type {I1} */var i1 = t;\n"
                + "/** @type {I2} */var i2 = t;\n"
                + "/** @type {I3} */var i3 = t;\n"
                + "i1 = i3;\n"
                + "i2 = i3;\n")
        .run();
  }

  @Test
  public void testMultipleExtendsInterfaceParamPass() {
    newTest()
        .addSource(
            "/** @interface */",
            "var I1 = function() {};",
            "/** @interface */",
            "var I2 = function() {}",
            "/** @interface @extends {I1} @extends {I2} */",
            "var I3 = function() {};",
            "/** @constructor @implements {I3} */",
            "var T = function() {};",
            "var t = new T();",
            "/**",
            " * @param {I1} x",
            " * @param {I2} y",
            " * @param {I3} z",
            " */",
            "function foo(x,y,z){};",
            "foo(t,t,t)")
        .run();
  }

  @Test
  public void testBadMultipleExtendsClass() {
    newTest()
        .addSource(
            "/** @constructor */ function base1() {}\n"
                + "/** @constructor */ function base2() {}\n"
                + "/** @constructor\n"
                + "* @extends {base1}\n"
                + "* @extends {base2}\n"
                + "*/\n"
                + "function derived() {}")
        .addDiagnostic(
            "Bad type annotation. type annotation incompatible with other annotations."
                + BAD_TYPE_WIKI_LINK)
        .run();
  }

  @Test
  public void testInterfaceExtendsResolution() {
    newTest()
        .addSource(
            "/** @interface \n @extends {A} */ function B() {};\n"
                + "/** @constructor \n @implements {B} */ function C() {};\n"
                + "/** @interface */ function A() {};")
        .run();
  }

  @Test
  public void testPropertyCanBeDefinedInObject() {
    // This test is specifically checking loose property check behavior.
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @interface */ function I() {};",
            "I.prototype.bar = function() {};",
            "/** @type {Object} */ var foo;",
            "foo.bar();")
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility1() {
    newTest()
        .addSource(
            "/** @interface */function Int0() {};"
                + "/** @interface */function Int1() {};"
                + "/** @type {number} */"
                + "Int0.prototype.foo;"
                + "/** @type {string} */"
                + "Int1.prototype.foo;"
                + "/** @interface \n @extends {Int0} \n @extends {Int1} */"
                + "function Int2() {};")
        .addDiagnostic(
            "Interface Int2 has a property foo with incompatible types in its "
                + "super interfaces Int0 and Int1")
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility2() {
    newTest()
        .addSource(
            "/** @interface */function Int0() {};"
                + "/** @interface */function Int1() {};"
                + "/** @interface */function Int2() {};"
                + "/** @type {number} */"
                + "Int0.prototype.foo;"
                + "/** @type {string} */"
                + "Int1.prototype.foo;"
                + "/** @type {Object} */"
                + "Int2.prototype.foo;"
                + "/** @interface \n @extends {Int0} \n @extends {Int1} \n"
                + "@extends {Int2}*/"
                + "function Int3() {};")
        .addDiagnostic(
            "Interface Int3 has a property foo with incompatible types in "
                + "its super interfaces Int0 and Int1")
        .addDiagnostic(
            "Interface Int3 has a property foo with incompatible types in "
                + "its super interfaces Int1 and Int2")
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility3() {
    newTest()
        .addSource(
            "/** @interface */function Int0() {};"
                + "/** @interface */function Int1() {};"
                + "/** @type {number} */"
                + "Int0.prototype.foo;"
                + "/** @type {string} */"
                + "Int1.prototype.foo;"
                + "/** @interface \n @extends {Int1} */ function Int2() {};"
                + "/** @interface \n @extends {Int0} \n @extends {Int2} */"
                + "function Int3() {};")
        .addDiagnostic(
            "Interface Int3 has a property foo with incompatible types in its "
                + "super interfaces Int0 and Int1")
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility4() {
    newTest()
        .addSource(
            "/** @interface */function Int0() {};"
                + "/** @interface \n @extends {Int0} */ function Int1() {};"
                + "/** @type {number} */"
                + "Int0.prototype.foo;"
                + "/** @interface */function Int2() {};"
                + "/** @interface \n @extends {Int2} */ function Int3() {};"
                + "/** @type {string} */"
                + "Int2.prototype.foo;"
                + "/** @interface \n @extends {Int1} \n @extends {Int3} */"
                + "function Int4() {};")
        .addDiagnostic(
            "Interface Int4 has a property foo with incompatible types in its "
                + "super interfaces Int0 and Int2")
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility5() {
    newTest()
        .addSource(
            "/** @interface */function Int0() {};"
                + "/** @interface */function Int1() {};"
                + "/** @type {number} */"
                + "Int0.prototype.foo;"
                + "/** @type {string} */"
                + "Int1.prototype.foo;"
                + "/** @interface \n @extends {Int1} */ function Int2() {};"
                + "/** @interface \n @extends {Int0} \n @extends {Int2} */"
                + "function Int3() {};"
                + "/** @interface */function Int4() {};"
                + "/** @type {number} */"
                + "Int4.prototype.foo;"
                + "/** @interface \n @extends {Int3} \n @extends {Int4} */"
                + "function Int5() {};")
        .addDiagnostic(
            "Interface Int3 has a property foo with incompatible types in its"
                + " super interfaces Int0 and Int1")
        .addDiagnostic(
            "Interface Int5 has a property foo with incompatible types in its"
                + " super interfaces Int1 and Int4")
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility6() {
    newTest()
        .addSource(
            "/** @interface */function Int0() {};"
                + "/** @interface */function Int1() {};"
                + "/** @type {number} */"
                + "Int0.prototype.foo;"
                + "/** @type {string} */"
                + "Int1.prototype.foo;"
                + "/** @interface \n @extends {Int1} */ function Int2() {};"
                + "/** @interface \n @extends {Int0} \n @extends {Int2} */"
                + "function Int3() {};"
                + "/** @interface */function Int4() {};"
                + "/** @type {string} */"
                + "Int4.prototype.foo;"
                + "/** @interface \n @extends {Int3} \n @extends {Int4} */"
                + "function Int5() {};")
        .addDiagnostic(
            "Interface Int3 has a property foo with incompatible types in its"
                + " super interfaces Int0 and Int1")
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility7() {
    newTest()
        .addSource(
            "/** @interface */function Int0() {};"
                + "/** @interface */function Int1() {};"
                + "/** @type {number} */"
                + "Int0.prototype.foo;"
                + "/** @type {string} */"
                + "Int1.prototype.foo;"
                + "/** @interface \n @extends {Int1} */ function Int2() {};"
                + "/** @interface \n @extends {Int0} \n @extends {Int2} */"
                + "function Int3() {};"
                + "/** @interface */function Int4() {};"
                + "/** @type {Object} */"
                + "Int4.prototype.foo;"
                + "/** @interface \n @extends {Int3} \n @extends {Int4} */"
                + "function Int5() {};")
        .addDiagnostic(
            "Interface Int3 has a property foo with incompatible types in its"
                + " super interfaces Int0 and Int1")
        .addDiagnostic(
            "Interface Int5 has a property foo with incompatible types in its"
                + " super interfaces Int1 and Int4")
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility8() {
    newTest()
        .addSource(
            "/** @interface */function Int0() {};"
                + "/** @interface */function Int1() {};"
                + "/** @type {number} */"
                + "Int0.prototype.foo;"
                + "/** @type {string} */"
                + "Int1.prototype.bar;"
                + "/** @interface \n @extends {Int1} */ function Int2() {};"
                + "/** @interface \n @extends {Int0} \n @extends {Int2} */"
                + "function Int3() {};"
                + "/** @interface */function Int4() {};"
                + "/** @type {Object} */"
                + "Int4.prototype.foo;"
                + "/** @type {Null} */"
                + "Int4.prototype.bar;"
                + "/** @interface \n @extends {Int3} \n @extends {Int4} */"
                + "function Int5() {};")
        .addDiagnostic(
            "Interface Int5 has a property bar with incompatible types in its"
                + " super interfaces Int1 and Int4")
        .addDiagnostic(
            "Interface Int5 has a property foo with incompatible types in its"
                + " super interfaces Int0 and Int4")
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility9() {
    newTest()
        .addSource(
            "/** @interface\n * @template T */function Int0() {};"
                + "/** @interface\n * @template T */function Int1() {};"
                + "/** @type {T} */"
                + "Int0.prototype.foo;"
                + "/** @type {T} */"
                + "Int1.prototype.foo;"
                + "/** @interface \n @extends {Int0<number>} \n @extends {Int1<string>} */"
                + "function Int2() {};")
        .addDiagnostic(
            "Interface Int2 has a property foo with incompatible types in its "
                + "super interfaces Int0<number> and Int1<string>")
        .run();
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibilityNoError() {
    newTest()
        .addSource(
            ""
                + "/** @interface */function Int0() {};"
                + "/** @interface */function Int1() {};"
                + "/** @param {number} x */"
                + "Int0.prototype.foo;"
                + "/** @param {number} x */"
                + "Int1.prototype.foo;"
                + "/** @interface \n * @extends {Int0} \n * @extends {Int1} */"
                + "function Int2() {};")
        .run();
  }

  @Test
  public void testImplementedInterfacePropertiesShouldFailOnConflict() {
    // TODO(b/132718172): Provide a better error message.
    newTest()
        .addSource(
            "/** @interface */function Int0() {};",
            "/** @interface */function Int1() {};",
            "/** @type {number} */",
            "Int0.prototype.foo;",
            "/** @type {string} */",
            "Int1.prototype.foo;",
            "/** @constructor @implements {Int0} @implements {Int1} */",
            "function Foo() {};",
            "Foo.prototype.foo;")
        .addDiagnostic(
            lines(
                "mismatch of the foo property on type Foo and the type of the property it"
                    + " overrides from interface Int1",
                "original: string",
                "override: number"))
        .run();
  }

  @Test
  public void testGenerics1a() {
    String fnDecl =
        "/** \n"
            + " * @param {T} x \n"
            + " * @param {function(T):T} y \n"
            + " * @template T\n"
            + " */ \n"
            + "function f(x,y) { return y(x); }\n";

    newTest()
        .addSource(
            fnDecl
                + "/** @type {string} */"
                + "var out;"
                + "/** @type {string} */"
                + "var result = f('hi', function(x){ out = x; return x; });")
        .run();
  }

  @Test
  public void testGenerics1b() {
    String fnDecl =
        "/** \n"
            + " * @param {T} x \n"
            + " * @param {function(T):T} y \n"
            + " * @template T\n"
            + " */ \n"
            + "function f(x,y) { return y(x); }\n";

    newTest()
        .addSource(
            fnDecl
                + "/** @type {string} */"
                + "var out;"
                + "var result = f(0, function(x){ out = x; return x; });")
        .addDiagnostic("assignment\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testFilter0() {
    newTest()
        .addSource(
            "/**\n"
                + " * @param {T} arr\n"
                + " * @return {T}\n"
                + " * @template T\n"
                + " */\n"
                + "var filter = function(arr){};\n"
                + "/** @type {!Array<string>} */"
                + "var arr;\n"
                + "/** @type {!Array<string>} */"
                + "var result = filter(arr);")
        .run();
  }

  @Test
  public void testFilter1() {
    newTest()
        .addSource(
            "/**\n"
                + " * @param {!Array<T>} arr\n"
                + " * @return {!Array<T>}\n"
                + " * @template T\n"
                + " */\n"
                + "var filter = function(arr){};\n"
                + "/** @type {!Array<string>} */"
                + "var arr;\n"
                + "/** @type {!Array<string>} */"
                + "var result = filter(arr);")
        .run();
  }

  @Test
  public void testFilter2() {
    newTest()
        .addSource(
            "/**\n"
                + " * @param {!Array<T>} arr\n"
                + " * @return {!Array<T>}\n"
                + " * @template T\n"
                + " */\n"
                + "var filter = function(arr){};\n"
                + "/** @type {!Array<string>} */"
                + "var arr;\n"
                + "/** @type {!Array<number>} */"
                + "var result = filter(arr);")
        .addDiagnostic(
            "initializing variable\n" + "found   : Array<string>\n" + "required: Array<number>")
        .run();
  }

  @Test
  public void testFilter3() {
    newTest()
        .addSource(
            "/**\n"
                + " * @param {Array<T>} arr\n"
                + " * @return {Array<T>}\n"
                + " * @template T\n"
                + " */\n"
                + "var filter = function(arr){};\n"
                + "/** @type {Array<string>} */"
                + "var arr;\n"
                + "/** @type {Array<number>} */"
                + "var result = filter(arr);")
        .addDiagnostic(
            "initializing variable\n"
                + "found   : (Array<string>|null)\n"
                + "required: (Array<number>|null)")
        .run();
  }

  @Test
  public void testBackwardsInferenceGoogArrayFilter1() {
    testClosureTypes(
        "/** @type {Array<string>} */"
            + "var arr;\n"
            + "/** @type {!Array<number>} */"
            + "var result = goog.array.filter("
            + "   arr,"
            + "   function(item,index,src) {return false;});",
        "initializing variable\n" + "found   : Array<string>\n" + "required: Array<number>");
  }

  @Test
  public void testBackwardsInferenceGoogArrayFilter2() {
    testClosureTypes(
        "/** @type {number} */"
            + "var out;"
            + "/** @type {Array<string>} */"
            + "var arr;\n"
            + "var out4 = goog.array.filter("
            + "   arr,"
            + "   function(item,index,src) {out = item; return false});",
        "assignment\n" + "found   : string\n" + "required: number");
  }

  @Test
  public void testBackwardsInferenceGoogArrayFilter3() {
    testClosureTypes(
        "/** @type {string} */"
            + "var out;"
            + "/** @type {Array<string>} */ var arr;\n"
            + "var result = goog.array.filter("
            + "   arr,"
            + "   function(item,index,src) {out = index;});",
        "assignment\n" + "found   : number\n" + "required: string");
  }

  @Test
  public void testBackwardsInferenceGoogArrayFilter4() {
    testClosureTypes(
        lines(
            "/** @type {string} */",
            "var out;",
            "/** @type {Array<string>} */ var arr;",
            "var out4 = goog.array.filter(",
            "   arr,",
            "   function(item,index,srcArr) {out = srcArr;});"),
        lines(
            "assignment", // keep newlines
            "found   : (Array|null|{length: number})",
            "required: string"));
  }

  @Test
  public void testCatchExpression1() {
    newTest()
        .addSource(
            "function fn() {"
                + "  /** @type {number} */"
                + "  var out = 0;"
                + "  try {\n"
                + "    foo();\n"
                + "  } catch (/** @type {string} */ e) {\n"
                + "    out = e;"
                + "  }"
                + "}\n")
        .addDiagnostic("assignment\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testCatchExpression2() {
    newTest()
        .addSource(
            "function fn() {"
                + "  /** @type {number} */"
                + "  var out = 0;"
                + "  /** @type {string} */"
                + "  var e;"
                + "  try {\n"
                + "    foo();\n"
                + "  } catch (e) {\n"
                + "    out = e;"
                + "  }"
                + "}\n")
        .run();
  }

  @Test
  public void testIssue1058() {
    newTest()
        .addSource(
            "/**\n"
                + "  * @constructor\n"
                + "  * @template CLASS\n"
                + "  */\n"
                + "var Class = function() {};\n"
                + "\n"
                + "/**\n"
                + "  * @param {function(CLASS):CLASS} a\n"
                + "  * @template T\n"
                + "  */\n"
                + "Class.prototype.foo = function(a) {\n"
                + "  return 'string';\n"
                + "};\n"
                + "\n"
                + "/** @param {number} a\n"
                + "  * @return {string} */\n"
                + "var a = function(a) { return '' };\n"
                + "\n"
                + "new Class().foo(a);")
        .run();
  }

  @Test
  public void testDeterminacyIssue() {
    newTest()
        .addSource(
            "(function() {\n"
                + "    /** @constructor */\n"
                + "    var ImageProxy = function() {};\n"
                + "    /** @constructor */\n"
                + "    var FeedReader = function() {};\n"
                + "    /** @type {ImageProxy} */\n"
                + "    FeedReader.x = new ImageProxy();\n"
                + "})();")
        .run();
  }

  @Test
  public void testUnknownTypeReport() {
    enableReportUnknownTypes();
    newTest()
        .addSource("function id(x) { return x; }")
        .addDiagnostic("could not determine the type of this expression")
        .run();
  }

  @Test
  public void testUnknownTypeReport_allowsUnknownIfStatement() {
    enableReportUnknownTypes();
    newTest().addSource("function id(x) { x; }").run();
  }

  @Test
  public void testUnknownForIn() {
    enableReportUnknownTypes();
    newTest().addSource("var x = {'a':1}; var y; \n for(\ny\n in x) {}").run();
  }

  @Test
  public void testUnknownTypeDisabledByDefault() {
    newTest().addSource("function id(x) { return x; }").run();
  }

  @Test
  public void testNonexistentPropertyAccessOnStruct() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "var A = function() {};\n"
                + "/** @param {A} a */\n"
                + "function foo(a) {\n"
                + "  if (a.bar) { a.bar(); }\n"
                + "}")
        .addDiagnostic("Property bar never defined on A")
        .run();
  }

  @Test
  public void testNonexistentPropertyAccessOnStructOrObject() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "var A = function() {};\n"
                + "/** @param {A|Object} a */\n"
                + "function foo(a) {\n"
                + "  if (a.bar) { a.bar(); }\n"
                + "}")
        .run();
  }

  @Test
  public void testNonexistentPropertyAccessOnExternStruct() {
    newTest()
        .addExterns(
            "/**\n" + " * @constructor\n" + " * @struct\n" + " */\n" + "var A = function() {};")
        .addSource(
            "/** @param {A} a */\n" + "function foo(a) {\n" + "  if (a.bar) { a.bar(); }\n" + "}")
        .addDiagnostic("Property bar never defined on A")
        .run();
  }

  @Test
  public void testNonexistentPropertyAccessStructSubtype() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "var A = function() {};"
                + ""
                + "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " * @extends {A}\n"
                + " */\n"
                + "var B = function() { this.bar = function(){}; };"
                + ""
                + "/** @param {A} a */\n"
                + "function foo(a) {\n"
                + "  if (a.bar) { a.bar(); }\n"
                + "}")
        .addDiagnostic("Property bar never defined on A")
        .run();
  }

  @Test
  public void testNonexistentPropertyAccessStructInterfaceSubtype() {
    newTest()
        .addSource(
            "/**",
            " * @interface",
            " * @struct",
            " */",
            "var A = function() {};",
            "",
            "/**",
            " * @interface",
            " * @struct",
            " * @extends {A}",
            " */",
            "var B = function() {};",
            "/** @return {void} */ B.prototype.bar = function(){};",
            "",
            "/** @param {A} a */",
            "function foo(a) {",
            "  if (a.bar) { a.bar(); }",
            "}")
        .addDiagnostic("Property bar never defined on A")
        .run();
  }

  @Test
  public void testNonexistentPropertyAccessStructRecordSubtype() {
    disableStrictMissingPropertyChecks();

    newTest()
        .addSource(
            "/**",
            " * @record",
            " * @struct",
            " */",
            "var A = function() {};",
            "",
            "/**",
            " * @record",
            " * @struct",
            " * @extends {A}",
            " */",
            "var B = function() {};",
            "/** @return {void} */ B.prototype.bar = function(){};",
            "",
            "/** @param {A} a */",
            "function foo(a) {",
            "  if (a.bar) {",
            "    a.bar();",
            "  }",
            "}")
        .addDiagnostic("Property bar never defined on A")
        .run();
  }

  @Test
  public void testNonexistentPropertyAccessStructSubtype2() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @struct\n"
                + " */\n"
                + "function Foo() {\n"
                + "  this.x = 123;\n"
                + "}\n"
                + "var objlit = /** @struct */ { y: 234 };\n"
                + "Foo.prototype = objlit;\n"
                + "var n = objlit.x;\n")
        .addDiagnostic("Property x never defined on Foo.prototype")
        .run();
  }

  @Test
  public void testIssue1024a() {
    // This test is specifically checking loose property check behavior.
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @param {Object} a */",
            "function f(a) {",
            "  a.prototype = '__proto'",
            "}",
            "/** @param {Object} b",
            " *  @return {!Object}",
            " */",
            "function g(b) {",
            "  return b.prototype",
            "}")
        .run();
  }

  @Test
  public void testIssue1024b() {
    newTest()
        .addSource(
            "/** @param {Object} a */",
            "function f(a) {",
            "  a.prototype = {foo:3};",
            "}",
            "/** @param {Object} b",
            " */",
            "function g(b) {",
            "  b.prototype = function(){};",
            "}")
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
            "/** @constructor */\n"
                + "var MyClass = function() {};\n"
                + "/** @type {!Object<MyClass, number>} */\n"
                + "var k;")
        .addDiagnostic(TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY)
        .run();
  }

  @Test
  public void testCheckObjectKeysBadKey8() {
    newTest()
        .addSource(
            "/** @enum{!Object} */\n"
                + "var Enum = {};\n"
                + "/** @type {!Object<Enum, number>} */\n"
                + "var k;")
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
            "/** @constructor */\n"
                + "function X() {}\n"
                + "/** @constructor @extends {X} */\n"
                + "function X2() {}\n"
                + "/** @enum {!X} */\n"
                + "var XE = {A:new X};\n"
                + "/** @type {Object<(!XE|!X2), string>} */\n"
                + "var Y = {};")
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
            "/** @interface */\n"
                + "var MyInterface = function() {};\n"
                + "/** @type {!Object<!MyInterface, number>} */\n"
                + "var k;")
        .run();
  }

  @Test
  public void testCheckObjectKeysGoodKey14() {
    newTest()
        .addSource(
            "/** @typedef {{a: number}} */ var MyRecord;\n"
                + "/** @type {!Object<MyRecord, number>} */ var k;")
        .run();
  }

  @Test
  public void testCheckObjectKeysGoodKey15() {
    newTest()
        .addSource(
            "/** @enum{number} */\n"
                + "var Enum = {};\n"
                + "/** @type {!Object<Enum, number>} */\n"
                + "var k;")
        .run();
  }

  @Test
  public void testCheckObjectKeysClassWithToString() {
    newTest()
        .addExterns(new TestExternsBuilder().addObject().build())
        .addSource(
            "/** @constructor */",
            "var MyClass = function() {};",
            "/** @override*/",
            "MyClass.prototype.toString = function() { return ''; };",
            "/** @type {!Object<!MyClass, number>} */",
            "var k;")
        .run();
  }

  @Test
  public void testCheckObjectKeysClassInheritsToString() {
    newTest()
        .addExterns(new TestExternsBuilder().addObject().build())
        .addSource(
            "/** @constructor */",
            "var Parent = function() {};",
            "/** @override */",
            "Parent.prototype.toString = function() { return ''; };",
            "/** @constructor @extends {Parent} */",
            "var Child = function() {};",
            "/** @type {!Object<!Child, number>} */",
            "var k;")
        .run();
  }

  @Test
  public void testCheckObjectKeysForEnumUsingClassWithToString() {
    newTest()
        .addExterns(new TestExternsBuilder().addObject().build())
        .addSource(
            "/** @constructor */",
            "var MyClass = function() {};",
            "/** @override*/",
            "MyClass.prototype.toString = function() { return ''; };",
            "/** @enum{!MyClass} */",
            "var Enum = {};",
            "/** @type {!Object<Enum, number>} */",
            "var k;")
        .run();
  }

  @Test
  public void testBadSuperclassInheritance1() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {}",
            "/** @type {number} */",
            "Foo.prototype.myprop = 2;",
            "",
            "/** @constructor @extends {Foo} */",
            "function Bar() {}",
            "/** @type {number} */",
            "Bar.prototype.myprop = 1;")
        .addDiagnostic(TypeCheck.HIDDEN_SUPERCLASS_PROPERTY)
        .run();
  }

  @Test
  public void testBadSuperclassInheritance2() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {}",
            "/** @type {number} */",
            "Foo.prototype.myprop = 2;",
            "",
            "/** @constructor @extends {Foo} */",
            "function Bar() {}",
            "/** @override @type {string} */",
            "Bar.prototype.myprop = 'qwer';")
        .addDiagnostic(TypeValidator.HIDDEN_SUPERCLASS_PROPERTY_MISMATCH)
        .run();
  }

  // If the property has no initializer, the HIDDEN_SUPERCLASS_PROPERTY_MISMATCH warning is missed.
  @Test
  public void testBadSuperclassInheritance3() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {}",
            "/** @type {number} */",
            "Foo.prototype.myprop = 2;",
            "",
            "/** @constructor @extends {Foo} */",
            "function Bar() {}",
            "/** @override @type {string} */",
            "Bar.prototype.myprop;")
        .run();
  }

  @Test
  public void testCheckObjectKeysWithNamedType() {
    newTest()
        .addSource(
            "/** @type {!Object<!PseudoId, number>} */\n"
                + "var k;\n"
                + "/** @typedef {number|string} */\n"
                + "var PseudoId;")
        .run();
  }

  @Test
  public void testCheckObjectKeyRecursiveType() {
    newTest()
        .addSource(
            "/** @typedef {!Object<string, !Predicate>} */ var Schema;\n"
                + "/** @typedef {function(*): boolean|!Schema} */ var Predicate;\n"
                + "/** @type {!Schema} */ var k;")
        .run();
  }

  @Test
  public void testDontOverrideNativeScalarTypes() {
    newTest()
        .addSource("string = 123;\n" + "var /** string */ s = 123;")
        .addDiagnostic("initializing variable\n" + "found   : number\n" + "required: string")
        .run();

    newTest()
        .addSource("var string = goog.require('goog.string');\n" + "var /** string */ s = 123;")
        .addDiagnostic(
            "Property require never defined on goog" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .addDiagnostic("initializing variable\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testTemplateMap1() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            "function f() {\n"
                + "  /** @type {Int8Array} */\n"
                + "  var x = new Int8Array(10);\n"
                + "  /** @type {IArrayLike<string>} */\n"
                + "  var y;\n"
                + "  y = x;\n"
                + "}")
        .addDiagnostic(
            "assignment\n" + "found   : (Int8Array|null)\n" + "required: (IArrayLike<string>|null)")
        .run();
  }

  @Test
  public void testTemplateMap2() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            "function f() {\n"
                + "  /** @type {Int8Array} */\n"
                + "  var x = new Int8Array(10);\n"
                + "\n"
                + "  /** @type {IObject<number, string>} */\n"
                + "  var z;\n"
                + "  z = x;\n"
                + "}")
        .addDiagnostic(
            "assignment\n"
                + "found   : (Int8Array|null)\n"
                + "required: (IObject<number,string>|null)")
        .run();
  }

  @Test
  public void testTemplateMap3() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            "function f() {\n"
                + "  var x = new Int8Array(10);\n"
                + "\n"
                + "  /** @type {IArrayLike<string>} */\n"
                + "  var y;\n"
                + "  y = x;\n"
                + "}")
        .addDiagnostic(
            "assignment\n" + "found   : Int8Array\n" + "required: (IArrayLike<string>|null)")
        .run();
  }

  @Test
  public void testTemplateMap4() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            "function f() {\n"
                + "  var x = new Int8Array(10);\n"
                + "\n"
                + "  /** @type {IObject<number, string>} */\n"
                + "  var z;\n"
                + "  z = x;\n"
                + "}")
        .addDiagnostic(
            "assignment\n" + "found   : Int8Array\n" + "required: (IObject<number,string>|null)")
        .run();
  }

  @Test
  public void testTemplateMap5() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            "function f() {\n"
                + "  var x = new Int8Array(10);\n"
                + "  /** @type {IArrayLike<number>} */\n"
                + "  var y;\n"
                + "  y = x;\n"
                + "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testTemplateMap6() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            "function f() {\n"
                + "  var x = new Int8Array(10);\n"
                + "  /** @type {IObject<number, number>} */\n"
                + "  var z;\n"
                + "  z = x;\n"
                + "}")
        .includeDefaultExterns()
        .run();
  }

  private static final String EXTERNS_WITH_IARRAYLIKE_DECLS =
      "/**\n"
          + " * @constructor @implements IArrayLike<number>\n"
          + " */\n"
          + "function Int8Array(length, opt_byteOffset, opt_length) {}\n"
          + "/** @type {number} */\n"
          + "Int8Array.prototype.length;\n"
          + "/**\n"
          + "* @constructor\n"
          + "* @extends {Int8Array}\n"
          + "*/\n"
          + "function Int8Array2(len) {};\n"
          + "/**\n"
          + " * @interface\n"
          + " * @extends {IArrayLike<number>}\n"
          + " */\n"
          + "function IArrayLike2(){}\n"
          + "\n"
          + "/**\n"
          + " * @constructor\n"
          + " * @implements {IArrayLike2}\n"
          + " */\n"
          + "function Int8Array3(len) {};\n"
          + "/** @type {number} */\n"
          + "Int8Array3.prototype.length;\n"
          + "/**\n"
          + " * @interface\n"
          + " * @extends {IArrayLike<VALUE3>}\n"
          + " * @template VALUE3\n"
          + " */\n"
          + "function IArrayLike3(){}\n"
          + "/**\n"
          + " * @constructor\n"
          + " * @implements {IArrayLike3<number>}\n"
          + " */\n"
          + "function Int8Array4(length) {};\n"
          + "/** @type {number} */\n"
          + "Int8Array4.prototype.length;\n"
          + "/**\n"
          + " * @interface\n"
          + " * @extends {IArrayLike<VALUE2>}\n"
          + " * @template VALUE2\n"
          + " */\n"
          + "function IArrayLike4(){}\n"
          + "/**\n"
          + " * @interface\n"
          + " * @extends {IArrayLike4<boolean>}\n"
          + " */\n"
          + "function IArrayLike5(){}\n"
          + "/**\n"
          + " * @constructor\n"
          + " * @implements {IArrayLike5}\n"
          + " */\n"
          + "function BooleanArray5(length) {};\n"
          + "/** @type {number} */\n"
          + "BooleanArray5.prototype.length;";

  @Test
  public void testArrayImplementsIArrayLike() {
    newTest()
        .addSource(
            "/** @type {!Array<number>} */ var arr = [];\n" + "var /** null */ n = arr[0];\n")
        .addDiagnostic("initializing variable\n" + "found   : number\n" + "required: null")
        .run();
  }

  @Test
  public void testIArrayLike1() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            "var arr = new Int8Array(7);\n" + "// no warning\n" + "arr[0] = 1;\n" + "arr[1] = 2;\n")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLike2() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource("var arr = new Int8Array(7);\n" + "// have warnings\n" + "arr[3] = false;\n")
        .addDiagnostic("assignment\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testIArrayLike3() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource("var arr = new Int8Array2(10);\n" + "// have warnings\n" + "arr[3] = false;\n")
        .addDiagnostic("assignment\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testIArrayLike4() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource("var arr = new Int8Array2(10);\n" + "// have warnings\n" + "arr[3] = false;\n")
        .addDiagnostic("assignment\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testIArrayLike5() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource("var arr = new Int8Array3(10);\n" + "// have warnings\n" + "arr[3] = false;\n")
        .addDiagnostic("assignment\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testIArrayLike6() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource("var arr = new Int8Array4(10);\n" + "// have warnings\n" + "arr[3] = false;\n")
        .addDiagnostic("assignment\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testIArrayLike7() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource("var arr5 = new BooleanArray5(10);\n" + "arr5[2] = true;\n" + "arr5[3] = \"\";")
        .addDiagnostic("assignment\n" + "found   : string\n" + "required: boolean")
        .run();
  }

  @Test
  public void testIArrayLike8() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            "var arr2 = new Int8Array(10);", //
            "arr2[true] = 1;")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : boolean",
                "required: number"))
        .run();
  }

  @Test
  public void testIArrayLike9() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            "var arr2 = new Int8Array2(10);", //
            "arr2[true] = 1;")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : boolean",
                "required: number"))
        .run();
  }

  @Test
  public void testIArrayLike10() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            "var arr2 = new Int8Array3(10);", //
            "arr2[true] = 1;")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : boolean",
                "required: number"))
        .run();
  }

  @Test
  public void testIArrayLike11() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            "var arr2 = new Int8Array4(10);", //
            "arr2[true] = 1;")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : boolean",
                "required: number"))
        .run();
  }

  @Test
  public void testIArrayLike12() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            "var arr2 = new BooleanArray5(10);", //
            "arr2['prop'] = true;")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testIArrayLike13() {
    newTest()
        .addExterns(EXTERNS_WITH_IARRAYLIKE_DECLS)
        .addSource(
            "var numOrStr = null ? 0 : 'prop';",
            "var arr2 = new BooleanArray5(10);",
            "arr2[numOrStr] = true;")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : (number|string)",
                "required: number"))
        .run();
  }

  @Test
  public void testIArrayLikeStructuralMatch1() {
    newTest()
        .addSource(
            "function f(/** !IArrayLike */ x){};",
            "/** @constructor */",
            "function Foo() {}",
            "/** @type {number} */ Foo.prototype.length",
            "f(new Foo)")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLikeStructuralMatch2() {
    newTest()
        .addSource(
            "function f(/** !IArrayLike */ x){};",
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {number} */ this.length = 5;",
            "}",
            "f(new Foo)")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLikeStructuralMatch3() {
    newTest()
        .addSource(
            "function f(/** !IArrayLike */ x){};", //
            "f({length: 5})")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLikeStructuralMatch4() {
    newTest()
        .addSource(
            "function f(/** !IArrayLike */ x){};",
            "/** @const */ var ns = {};",
            "/** @type {number} */ ns.length",
            "f(ns)")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLikeStructuralMatch5() {
    newTest()
        .addSource(
            "function f(/** !IArrayLike */ x){};",
            "var ns = function() {};",
            "/** @type {number} */ ns.length",
            "f(ns)")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLikeStructuralMatch6() {
    // Even though Foo's [] element type may not be string, we treat the lack
    // of explicit type like ? and allow this.
    newTest()
        .addSource(
            "function f(/** !IArrayLike<string> */ x){};",
            "/** @constructor */",
            "function Foo() {}",
            "/** @type {number} */ Foo.prototype.length",
            "f(new Foo)")
        .includeDefaultExterns()
        .run();
  }

  private static final String EXTERNS_WITH_IOBJECT_DECLS =
      lines(
          "/**",
          " * @constructor",
          " * @implements IObject<(string|number), number>",
          " */",
          "function Object2() {}",
          "/**",
          " * @constructor @struct",
          " * @implements IObject<number, number>",
          " */",
          "function Object3() {}");

  @Test
  public void testIObject1() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "var arr2 = new Object2();", //
            "arr2[0] = 1;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIObject2() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "var arr2 = new Object2();", //
            "arr2['str'] = 1;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIObject3() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "var arr2 = new Object2();", //
            "arr2[true] = 1;")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : boolean",
                "required: (number|string)"))
        .run();
  }

  @Test
  public void testIObject4() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "var arr2 = new Object2();", //
            "arr2[function(){}] = 1;")
        .addDiagnostic(
            lines(
                "restricted index type",
                "found   : function(): undefined",
                "required: (number|string)"))
        .run();
  }

  @Test
  public void testIObject5() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "var arr2 = new Object2();", //
            "arr2[{}] = 1;")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : {}",
                "required: (number|string)"))
        .run();
  }

  @Test
  public void testIObject6() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "var arr2 = new Object2();", //
            "arr2[undefined] = 1;")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : undefined",
                "required: (number|string)"))
        .run();
  }

  @Test
  public void testIObject7() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "var arr2 = new Object2();", //
            "arr2[null] = 1;")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : null",
                "required: (number|string)"))
        .run();
  }

  @Test
  public void testIObject8() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "var arr = new Object2();", //
            "/** @type {boolean} */",
            "var x = arr[3];")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: boolean"))
        .run();
  }

  @Test
  public void testIObject9() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "var arr = new Object2();", //
            "/** @type {(number|string)} */",
            "var x = arr[3];")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIObject10() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "var arr = new Object3();", //
            "/** @type {number} */",
            "var x = arr[3];")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIObject11() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "var arr = new Object3();", //
            "/** @type {boolean} */",
            "var x = arr[3];")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: boolean"))
        .run();
  }

  @Test
  public void testIObject12() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "var arr = new Object3();", //
            "/** @type {string} */",
            "var x = arr[3];")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testIObject13() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "var arr = new Object3();", //
            "arr[3] = false;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : boolean",
                "required: number"))
        .run();
  }

  @Test
  public void testIObject14() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "var arr = new Object3();", //
            "arr[3] = 'value';")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testIObject15() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "function f(/** !Object<string, string> */ x) {}",
            "var /** !IObject<string, string> */ y;",
            "f(y);")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIObject16() {
    newTest()
        .addExterns(EXTERNS_WITH_IOBJECT_DECLS)
        .addSource(
            "function f(/** !Object<string, string> */ x) {}",
            "var /** !IObject<string, number> */ y;",
            "f(y);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : IObject<string,number>",
                "required: Object<string,string>"))
        .run();
  }

  @Test
  public void testEmptySubtypeRecord1() {
    // Verify that empty @record subtypes don't cause circular definition warnings.
    newTest()
        .addSource(
            "/** @record */ function I1() {};",
            "/** @type {number|undefined} */ I1.prototype.prop;",
            "",
            "/** @record @extends {I1} */ function I2() {}",
            "/** @record @extends {I2} */ function I3() {}",
            "/** @type {string} */ I3.prototype.prop2;",
            "/** @constructor @implements {I3} */ function C() {",
            "    /** @type {number} */ this.prop = 1;",
            "    /** @type {string} */ this.prop2 = 'str';",
            "}",
            "",
            "/** @param {I3} a */ function fn(a) {}")
        .run();
  }

  @Test
  public void testEmptySubtypeRecord2() {
    newTest()
        .addSource(
            "/** @type {!SubInterface} */ var value;",
            "/** @record */ var SuperInterface = function () {};",
            "/** @record @extends {SuperInterface} */ var SubInterface = function() {};")
        .run();
  }

  @Test
  public void testEmptySubtypeRecord3() {
    newTest()
        .addSource(
            "/** @record */ var SuperInterface = function () {};",
            "/** @record @extends {SuperInterface} */ var SubInterface = function() {};",
            "/** @type {!SubInterface} */ var value;")
        .run();
  }

  @Test
  public void testEmptySubtypeInterface1() {
    newTest()
        .addSource(
            "/** @type {!SubInterface} */ var value;",
            "/** @interface */ var SuperInterface = function () {};",
            "/** @interface @extends {SuperInterface} */ var SubInterface = function() {};")
        .run();
  }

  @Test
  public void testRecordWithOptionalProperty() {
    newTest()
        .addSource(
            "/**  @constructor */ function Foo() {};",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** {str: string, opt_num: (undefined|number)} */ x = new Foo;")
        .run();
  }

  @Test
  public void testRecordWithUnknownProperty() {
    newTest()
        .addSource(
            "/**  @constructor */ function Foo() {};",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** {str: string, unknown: ?} */ x = new Foo;")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : Foo",
                "required: {\n  str: string,\n  unknown: ?\n}",
                "missing : [unknown]",
                "mismatch: []"))
        .run();
  }

  @Test
  public void testRecordWithOptionalUnknownProperty() {
    newTest()
        .addSource(
            "/**  @constructor */ function Foo() {};",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** {str: string, opt_unknown: (?|undefined)} */ x = new Foo;")
        .run();
  }

  @Test
  public void testRecordWithTopProperty() {
    newTest()
        .addSource(
            "/**  @constructor */ function Foo() {};",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** {str: string, top: *} */ x = new Foo;")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : Foo",
                "required: {\n  str: string,\n  top: *\n}",
                "missing : [top]",
                "mismatch: []"))
        .run();
  }

  @Test
  public void testOptionalUnknownIsAssignableToUnknown() {
    newTest()
        .addSource(
            "function f(/** (undefined|?) */ opt_unknown) {",
            "  var /** ? */ unknown = opt_unknown;",
            "}")
        .run();
  }

  @Test
  public void testOptimizePropertyMap1() {
    // For non object-literal types such as Function, the behavior doesn't change.
    // The stray property is added as unknown.
    newTest()
        .addSource(
            "/** @return {!Function} */",
            "function f() {",
            "  var x = function() {};",
            "  /** @type {number} */",
            "  x.prop = 123;",
            "  return x;",
            "}",
            "function g(/** !Function */ x) {",
            "  var /** null */ n = x.prop;",
            "}")
        .addDiagnostic("Property prop never defined on Function")
        .run();
  }

  @Test
  public void testOptimizePropertyMap1b() {
    disableStrictMissingPropertyChecks();

    // For non object-literal types such as Function, the behavior doesn't change.
    // The stray property is added as unknown.
    newTest()
        .addSource(
            "/** @return {!Function} */",
            "function f() {",
            "  var x = function() {};",
            "  /** @type {number} */",
            "  x.prop = 123;",
            "  return x;",
            "}",
            "function g(/** !Function */ x) {",
            "  var /** null */ n = x.prop;",
            "}")
        .run();
  }

  @Test
  public void testOptimizePropertyMap2() {
    disableStrictMissingPropertyChecks();

    // Don't add the inferred property to all Foo values.
    newTest()
        .addSource(
            "/** @typedef {{a:number}} */",
            "var Foo;",
            "function f(/** !Foo */ x) {",
            "  var y = x;",
            "  /** @type {number} */",
            "  y.b = 123;",
            "}",
            "function g(/** !Foo */ x) {",
            "  var /** null */ n = x.b;",
            "}")
        .addDiagnostic("Property b never defined on x")
        .run();
  }

  @Test
  public void testOptimizePropertyMap2b() {
    // Here strict missing properties warns, do we want it to?
    newTest()
        .addSource(
            "/** @typedef {{a:number}} */",
            "var Foo;",
            "function f(/** !Foo */ x) {",
            "  var y = x;",
            "  /** @type {number} */",
            "  y.b = 123;",
            "}")
        .addDiagnostic("Property b never defined on y")
        .run();
  }

  @Test
  public void testOptimizePropertyMap3a() {
    // For @record types, add the stray property to the index as before.
    newTest()
        .addSource(
            "/** @record */",
            "function Foo() {}",
            "/** @type {number} */",
            "Foo.prototype.a;",
            "function f(/** !Foo */ x) {",
            "  var y = x;",
            "  /** @type {number} */",
            "  y.b = 123;",
            "}",
            "function g(/** !Foo */ x) {",
            "  var /** null */ n = x.b;",
            "}")
        .addDiagnostic("Property b never defined on Foo") // definition
        .addDiagnostic("Property b never defined on Foo")
        .run(); // reference
  }

  @Test
  public void testOptimizePropertyMap3b() {
    disableStrictMissingPropertyChecks();
    // For @record types, add the stray property to the index as before.
    newTest()
        .addSource(
            "/** @record */",
            "function Foo() {}",
            "/** @type {number} */",
            "Foo.prototype.a;",
            "function f(/** !Foo */ x) {",
            "  var y = x;",
            "  /** @type {number} */",
            "  y.b = 123;",
            "}",
            "function g(/** !Foo */ x) {",
            "  var /** null */ n = x.b;",
            "}")
        .run();
  }

  @Test
  public void testOptimizePropertyMap4() {
    newTest()
        .addSource(
            "function f(x) {",
            "  var y = { a: 1, b: 2 };",
            "}",
            "function g(x) {",
            "  return x.b + 1;",
            "}")
        .run();
  }

  @Test
  public void testOptimizePropertyMap5() {
    // Tests that we don't declare the properties on Object (so they don't appear on
    // all object types).
    newTest()
        .addSource(
            "function f(x) {",
            "  var y = { a: 1, b: 2 };",
            "}",
            "function g() {",
            "  var x = { c: 123 };",
            "  return x.a + 1;",
            "}")
        .addDiagnostic("Property a never defined on x")
        .run();
  }

  @Test
  public void testOptimizePropertyMap6() {
    // Checking loose property behavior
    disableStrictMissingPropertyChecks();

    // The stray property doesn't appear on other inline record types.
    newTest()
        .addSource(
            "function f(/** {a:number} */ x) {",
            "  var y = x;",
            "  /** @type {number} */",
            "  y.b = 123;",
            "}",
            "function g(/** {c:number} */ x) {",
            "  var /** null */ n = x.b;",
            "}")
        .addDiagnostic("Property b never defined on x")
        .run();
  }

  @Test
  public void testOptimizePropertyMap7() {
    newTest()
        .addSource(
            "function f() {",
            "  var x = {a:1};",
            "  x.b = 2;",
            "}",
            "function g() {",
            "  var y = {a:1};",
            "  return y.b + 1;",
            "}")
        .addDiagnostic("Property b never defined on y")
        .run();
  }

  @Test
  public void testOptimizePropertyMap8() {
    newTest()
        .addSource(
            "function f(/** {a:number, b:number} */ x) {}",
            "function g(/** {c:number} */ x) {",
            "  var /** null */ n = x.b;",
            "}")
        .addDiagnostic("Property b never defined on x")
        .run();
  }

  @Test
  public void testOptimizePropertyMap9() {
    // Checking loose property checks behavior
    disableStrictMissingPropertyChecks();

    // Don't add the stray property to all types that meet with {a: number, c: string}.
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {",
            "  this.a = 123;",
            "}",
            "function f(/** {a: number, c: string} */ x) {",
            "  x.b = 234;",
            "}",
            "function g(/** !Foo */ x) {",
            "  return x.b + 5;",
            "}")
        .addDiagnostic("Property b never defined on Foo")
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition1() {
    newTest()
        .addSource(
            "/** @record */",
            "function A() {}",
            "/** @type {number} */",
            "A.prototype.prop;",
            "/** @record */",
            "function B() {}",
            "/** @type {number} */",
            "B.prototype.prop;",
            "/** @constructor */",
            "function C() {}",
            "/** @type {number} */",
            "C.prototype.prop;",
            "/** @return {(A|B|C)} */",
            "function fun () {}",
            "/** @return {(B|A|C)} */",
            "function fun () {}")
        .addDiagnostic("variable fun redefined, original definition at [testcode]:14")
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition3() {
    newTest()
        .addSource(
            "var ns = {};", //
            "/** @type {{x:number}} */ ns.x;",
            "/** @type {{x:number}} */ ns.x;")
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition3_1() {
    newTest()
        .addSource(
            "var ns = {};", //
            "/** @type {{x:number}} */ ns.x;",
            "/** @type {{x:string}} */ ns.x;")
        .addDiagnostic(
            "variable ns.x redefined with type {x: string}, original definition "
                + "at [testcode]:2 with type {x: number}")
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition3_2() {
    newTest()
        .addSource(
            "var ns = {};",
            "/** @type {{x:number}} */ ns.x;",
            "/** @type {{x:number, y:boolean}} */ ns.x;")
        .addDiagnostic(
            "variable ns.x redefined with type {\n  x: number,\n  y: boolean\n}, "
                + "original definition at [testcode]:2 with type {x: number}")
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition4() {
    newTest()
        .addSource(
            "var ns = {};",
            "/** @record */ function rec3(){}",
            "/** @record */ function rec4(){}",
            "/** @type {!rec3} */ ns.x;",
            "/** @type {!rec4} */ ns.x;")
        .addDiagnostic(
            "variable ns.x redefined with type rec4, original definition at [testcode]:4 with type"
                + " rec3")
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition5() {
    newTest()
        .addSource(
            "var ns = {};",
            "/** @record */ function rec3(){}",
            "/** @record */ function rec4(){}",
            "/** @type {number} */ rec4.prototype.prop;",
            "/** @type {!rec3} */ ns.x;",
            "/** @type {!rec4} */ ns.x;")
        .addDiagnostic(
            "variable ns.x redefined with type rec4, original definition at "
                + "[testcode]:5 with type rec3")
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition6() {
    newTest()
        .addSource(
            "var ns = {};",
            "/** @record */ function rec3(){}",
            "/** @type {number} */ rec3.prototype.prop;",
            "/** @record */ function rec4(){}",
            "/** @type {!rec3} */ ns.x;",
            "/** @type {!rec4} */ ns.x;")
        .addDiagnostic(
            "variable ns.x redefined with type rec4, original definition at "
                + "[testcode]:5 with type rec3")
        .run();
  }

  /** check bug fix 22713201 (the first case) */
  @Test
  public void testDuplicateVariableDefinition7() {
    newTest()
        .addSource(
            "/** @typedef {{prop:TD2}} */",
            "  var TD1;",
            "",
            "  /** @typedef {{prop:TD1}} */",
            "  var TD2;",
            "",
            "  var /** TD1 */ td1;",
            "  var /** TD2 */ td2;",
            "",
            "  td1 = td2;")
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition8() {
    newTest()
        .addSource(
            "var ns = {}",
            "/** @record */ function rec(){}",
            "/** @type {number} */ rec.prototype.prop;",
            "",
            "/** @type {!rec} */ ns.x;",
            "/** @type {{prop:number}} */ ns.x;",
            "",
            "/** @type {{prop:number}} */ ns.y;",
            "/** @type {!rec} */ ns.y;")
        .addDiagnostic(
            "variable ns.x redefined with type {prop: number}, original definition at [testcode]:5"
                + " with type rec")
        .addDiagnostic(
            "variable ns.y redefined with type rec, original definition at [testcode]:8 with type"
                + " {prop: number}")
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition8_2() {
    newTest()
        .addSource(
            "var ns = {}",
            "/** @record */ function rec(){}",
            "/** @type {number} */ rec.prototype.prop;",
            "",
            "/** @type {!rec} */ ns.x;",
            "/** @type {{prop:string}} */ ns.x;",
            "",
            "/** @type {{prop:number}} */ ns.y;",
            "/** @type {!rec} */ ns.y;")
        .addDiagnostic(
            "variable ns.x redefined with type {prop: string}, original "
                + "definition at [testcode]:5 with type rec")
        .addDiagnostic(
            "variable ns.y redefined with type rec, original definition at [testcode]:8 with type"
                + " {prop: number}")
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition8_3() {
    newTest()
        .addSource(
            "var ns = {}",
            "/** @record */ function rec(){}",
            "/** @type {string} */ rec.prototype.prop;",
            "",
            "/** @type {!rec} */ ns.x;",
            "/** @type {{prop:string}} */ ns.x;",
            "",
            "/** @type {{prop:number}} */ ns.y;",
            "/** @type {!rec} */ ns.y;")
        .addDiagnostic(
            "variable ns.x redefined with type {prop: string}, original definition at [testcode]:5"
                + " with type rec")
        .addDiagnostic(
            "variable ns.y redefined with type rec, original definition at "
                + "[testcode]:8 with type {prop: number}")
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition8_4() {
    newTest()
        .addSource(
            "/** @record @template T */ function I() {}",
            "/** @type {T} */ I.prototype.prop;",
            "var ns = {}",
            "/** @record */ function rec(){}",
            "/** @type {I} */ rec.prototype.prop;",
            "",
            "/** @type {!rec} */ ns.x;",
            "/** @type {{prop:I}} */ ns.x;")
        .addDiagnostic(
            "variable ns.x redefined with type {prop: (I|null)}, original definition at"
                + " [testcode]:7 with type rec")
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition8_5() {
    newTest()
        .addSource(
            "/** @record @template T */ function I() {}",
            "/** @type {T} */ I.prototype.prop;",
            "var ns = {}",
            "/** @record */ function rec(){}",
            "/** @type {I<number>} */ rec.prototype.prop;",
            "",
            "/** @type {!rec} */ ns.x;",
            "/** @type {{prop:I<number>}} */ ns.x;")
        .addDiagnostic(
            "variable ns.x redefined with type {prop: (I<number>|null)}, original definition at"
                + " [testcode]:7 with type rec")
        .run();
  }

  @Test
  public void testDuplicateVariableDefinition8_6() {
    newTest()
        .addSource(
            "/** @record @template T */ function I() {}",
            "/** @type {T} */ I.prototype.prop;",
            "var ns = {}",
            "/** @record */ function rec(){}",
            "/** @type {I<number>} */ rec.prototype.prop;",
            "",
            "/** @type {!rec} */ ns.x;",
            "/** @type {{prop:I<string>}} */ ns.x;")
        .addDiagnostic(
            "variable ns.x redefined with type {prop: (I<string>|null)}, "
                + "original definition at [testcode]:7 with type rec")
        .run();
  }

  // should have no warning, need to handle equivalence checking for
  // structural types with template types
  @Test
  public void testDuplicateVariableDefinition8_7() {
    newTest()
        .addSource(
            "/** @record @template T */",
            "function rec(){}",
            "/** @type {T} */ rec.prototype.value;",
            "",
            "/** @type {rec<string>} */ ns.x;",
            "/** @type {{value: string}} */ ns.x;")
        .addDiagnostic(
            "variable ns.x redefined with type {value: string}, "
                + "original definition at [testcode]:5 with type (null|rec<string>)")
        .run();
  }

  @Test
  public void testModuloNullUndefThatWorkedWithoutSpecialSubtypingRules1() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {}",
            "function f(/** function(?Foo, !Foo) */ x) {",
            "  return /** @type {function(!Foo, ?Foo)} */ (x);",
            "}")
        .run();
  }

  @Test
  public void testModuloNullUndefThatWorkedWithoutSpecialSubtypingRules2() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {}",
            "function f(/** !Array<!Foo> */ to, /** !Array<?Foo> */ from) {",
            "  to = from;",
            "}")
        .run();
  }

  @Test
  public void testModuloNullUndefThatWorkedWithoutSpecialSubtypingRules3() {
    newTest()
        .addSource(
            "function f(/** ?Object */ x) {", //
            "  return {} instanceof x;",
            "}")
        .run();
  }

  @Test
  public void testModuloNullUndefThatWorkedWithoutSpecialSubtypingRules4() {
    newTest()
        .addSource(
            "function f(/** ?Function */ x) {", //
            "  return x();",
            "}")
        .run();
  }

  @Test
  public void testEs5ClassExtendingEs6Class() {
    newTest()
        .addSource(
            "class Foo {}", //
            "/** @constructor @extends {Foo} */ var Bar = function() {};")
        .addDiagnostic("ES5 class Bar cannot extend ES6 class Foo")
        .run();
  }

  @Test
  public void testEs5ClassExtendingEs6Class_noWarning() {
    newTest()
        .addSource(
            "class A {}", //
            "/** @constructor @extends {A} */",
            "const B = createSubclass(A);")
        .run();
  }

  @Test
  public void testNonNullTemplatedThis() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function C() {}",
            "",
            "/** ",
            "  @return {THIS} ",
            "  @this {THIS}",
            "  @template THIS",
            "*/",
            "C.prototype.method = function() {};",
            "",
            "/** @return {C|null} */",
            "function f() {",
            "  return x;",
            "};",
            "",
            "/** @type {string} */ var s = f().method();")
        .addDiagnostic("initializing variable\n" + "found   : C\n" + "required: string")
        .run();
  }

  // Github issue #2222: https://github.com/google/closure-compiler/issues/2222
  @Test
  public void testSetPrototypeToNewInstance() {
    newTest()
        .addSource(
            "/** @constructor */", //
            "function C() {}",
            "C.prototype = new C;")
        .run();
  }

  @Test
  public void testFilterNoResolvedType() {
    testClosureTypes(
        lines(
            "goog.forwardDeclare('Foo');",
            "/**",
            " * @param {boolean} pred",
            " * @param {?Foo} x",
            " */",
            "function f(pred, x) {",
            "  var y;",
            "  if (pred) {",
            "    y = null;",
            "  } else {",
            "    y = x;",
            "  }",
            "  var /** number */ z = y;",
            "}"),
        // Tests that the type of y is (NoResolvedType|null) and not (Foo|null)
        lines(
            "initializing variable", //
            "found   : (NoResolvedType|null)",
            "required: number"));
  }

  @Test
  public void testNoResolvedTypeDoesntCauseInfiniteLoop() {
    testClosureTypes(
        lines(
            "goog.forwardDeclare('Foo');",
            "goog.forwardDeclare('Bar');",
            "",
            "/** @interface */",
            "var Baz = function() {};",
            "/** @return {!Bar} */",
            "Baz.prototype.getBar = function() {};",
            "/** @constructor */",
            "var Qux = function() {",
            "  /** @type {?Foo} */",
            "  this.jobRuntimeTracker_ = null;",
            "};",
            "/** @param {!Baz} job */",
            "Qux.prototype.runRenderJobs_ = function(job) {",
            "  for (var i = 0; i < 10; i++) {",
            "    if (this.jobRuntimeTracker_) {",
            "      goog.asserts.assert(job.getBar, '');",
            "    }",
            "  }",
            "};"),
        null);
  }

  @Test
  public void testb38182645() {
    newTest()
        .addSource(
            "",
            "/**",
            " * @interface",
            " * @template VALUE",
            " */",
            "function MyI() {}",
            "",
            "",
            "/**",
            " * @constructor",
            " * @implements {MyI<K|V>}",
            " * @template K, V",
            " */",
            "function MyMap() {}",
            "",
            "",
            "/**",
            " * @param {!MyMap<string, T>} map",
            " * @return {T}",
            " * @template T",
            " */",
            "function getValueFromNameAndMap(map) {",
            "  return /** @type {?} */ (123);",
            "}",
            "var m = /** @type {!MyMap<string,number>} */ (new MyMap());",
            "var /** null */ n = getValueFromNameAndMap(m);")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testLocalType1() {
    newTest()
        .addSource(
            "/** @constructor */ function C(){ /** @const */ this.a = true;}",
            "function f() {",
            "  // shadow",
            "  /** @constructor */ function C(){ /** @const */ this.a = 1;}",
            "}")
        .run();
  }

  @Test
  public void testLocalType2() {
    newTest()
        .addSource(
            "/** @constructor */ function C(){ /** @const */ this.a = true;}",
            "function f() {",
            "  // shadow",
            "  /** @constructor */ function C(){ /** @const */ this.a = 1;}",
            "  /** @type {null} */ var x = new C().a;",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testForwardDecl1() {
    newTest()
        .addSource(
            "/** @const */ var ns = {};",
            "/** @constructor */ ns.Outer = function C() {};",
            "/** @return {!ns.Outer.Inner} */",
            "ns.Outer.prototype.method = function() {",
            "  return new ns.Outer.Inner();",
            "};",
            "/** @constructor */ ns.Outer.Inner = function () {};")
        .run();
  }

  @Test
  public void testForwardDecl2() {
    newTest()
        .addSource(
            "/** @const */ var ns1 = {};",
            "/** @const */ ns1.ns2 = {};",
            "/** @constructor */ ns1.ns2.Outer = function C() {};",
            "/** @return {!ns1.ns2.Outer.Inner} */",
            "ns1.ns2.Outer.prototype.method = function() {",
            "  return new ns1.ns2.Outer.Inner();",
            "};",
            "/** @constructor */ ns1.ns2.Outer.Inner = function () {};")
        .run();
  }

  @Test
  public void testMissingPropertiesWarningOnObject1() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {",
            "  this.prop = 123;",
            "}",
            "/** @param {!Object} x */",
            "function f(x) {",
            "  return x.prop;",
            "}")
        .addDiagnostic("Property prop never defined on Object")
        .run();
  }

  @Test
  public void testStrictNumericOperators1() {
    newTest()
        .addSource("var x = 'asdf' - 1;")
        .addDiagnostic(
            lines(
                "left operand", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testStrictNumericOperators2() {
    newTest()
        .addSource("var x = 1 - 'asdf';")
        .addDiagnostic(
            lines(
                "right operand", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testStrictNumericOperators3() {
    newTest()
        .addSource("var x = 'asdf'; x++;")
        .addDiagnostic(
            lines(
                "increment/decrement", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testStrictNumericOperators4a() {
    newTest()
        .addSource("var x = -'asdf';")
        .addDiagnostic(
            lines(
                "sign operator", //
                "found   : string",
                "required: number"))
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
            lines(
                "right side of numeric comparison",
                "found   : string",
                "required: (bigint|number)"))
        .run();
  }

  @Test
  public void testStrictNumericOperators6() {
    newTest()
        .addSource("var x = 'asdf'; x *= 2;")
        .addDiagnostic(
            lines(
                "left operand", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testStrictNumericOperators7() {
    newTest()
        .addSource("var x = ~ 'asdf';")
        .addDiagnostic(
            lines(
                "bitwise NOT", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testStrictNumericOperators8() {
    newTest()
        .addSource("var x = 'asdf' | 1;")
        .addDiagnostic(
            lines(
                "bad left operand to bitwise operator", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testStrictNumericOperators9() {
    newTest()
        .addSource("var x = 'asdf' << 1;")
        .addDiagnostic(
            lines(
                "operator <<", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testStrictNumericOperators10() {
    newTest()
        .addSource("var x = 1 >>> 'asdf';")
        .addDiagnostic(
            lines(
                "operator >>>", //
                "found   : string",
                "required: number"))
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
            "class C {", //
            "  /** @type {string} */ ",
            "  x = 2;",
            "}")
        .addDiagnostic(
            lines(
                "assignment to property x of C", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassFieldStaticTypeError1() {
    newTest()
        .addSource(
            "class C {", //
            "  /** @type {string} */ ",
            "  static x = 2;",
            "}")
        .addDiagnostic(
            lines(
                "assignment to property x of C", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassFieldTypeError2() {
    newTest()
        .addSource(
            "class C {",
            "  /** @type {string} */",
            "  x = '';",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.x = 1;",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "assignment to property x of C", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassFieldTypeError3() {
    newTest()
        .addSource(
            "const obj = {};", //
            "obj.C = class {",
            "  /** @type {string} */ ",
            "  x = 2;",
            "}")
        .addDiagnostic(
            lines(
                "assignment to property x of obj.C", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassFieldStaticTypeError3() {
    newTest()
        .addSource(
            "const obj = {};",
            "obj.C = class {",
            "  /** @type {string} */ ",
            "  static x = 2;",
            "}")
        .addDiagnostic(
            lines(
                "assignment to property x of obj.C", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassInitializerTypeInitializer() {
    newTest()
        .addSource(
            "/** @param {number|undefined} y */ ",
            "function f(y) {",
            "class C { /** @type {string} */ x = y ?? 0; }",
            "}")
        .addDiagnostic(
            lines(
                "assignment to property x of C", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassInitializerTypeInference() {
    newTest()
        .addSource(
            "/**",
            " * @param {string} s",
            " * @return {string}",
            " */",
            "const stringIdentity = (s) => s;",
            "class C { x = stringIdentity(0); }")
        .addDiagnostic(
            lines(
                "actual parameter 1 of stringIdentity does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassComputedField() {
    newTest().addSource("/** @dict */ class C { [x]=2; }").run();
    newTest().addSource("/** @dict */ class C { 'x' = 2; }").run();
    newTest().addSource("/** @dict */ class C { 1 = 2; }").run();
    newTest()
        .addSource(
            "/** @param {number} x */ function takesNum(x) {}",
            "/** @dict */",
            "class C {",
            "  /** @type {string} @suppress {checkTypes} */",
            "  [takesNum('string')] = 2;",
            "}")
        .run();
    newTest().addSource("/** @unrestricted */ class C { [x] = 2; }").run();
    newTest().addSource("/** @unrestricted */ class C { 'x' = 2; }").run();
    newTest().addSource("/** @unrestricted */ class C { 1 = 2; }").run();
    newTest()
        .addSource(
            "/** @unrestricted */", //
            "class C {",
            "  /** @type {string} */",
            "  [x] = 2;",
            "}")
        .run();
  }

  @Test
  public void testClassComputedFieldStatic() {
    newTest().addSource("/** @dict */ class C { static [x]=2; }").run();
    newTest().addSource("/** @dict */ class C { static 'x' = 2; }").run();
    newTest().addSource("/** @dict */ class C { static 1 = 2; }").run();
    newTest()
        .addSource(
            "/** @dict */", //
            "class C {",
            "  /** @type {string}*/",
            "  static [x] = 2;",
            "}")
        .run();

    newTest().addSource("/** @unrestricted */ class C { static [x]=2; }").run();
    newTest().addSource("/** @unrestricted */ class C { static 'x' = 2; }").run();
    newTest().addSource("/** @unrestricted */ class C { static 1 = 2; }").run();
    newTest()
        .addSource(
            "/** @unrestricted */",
            "class C {",
            "  /** @type {string} */",
            "  static [x] = 2;",
            "}")
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
            lines(
                "expected matching types in comparison", //
                "found   : string",
                "required: boolean"))
        .run();
  }

  @Test
  public void testStrictComparison2() {
    newTest()
        .addSource(
            "function f(/** (number|string) */ x, /** string */ y) {", //
            "  return x < y;",
            "}")
        .run();
  }

  @Test
  public void testComparisonWithUnknownAndSymbol() {
    newTest()
        .addSource("/** @type {symbol} */ var x; /** @type {?} */ var y; x < y")
        .addDiagnostic(
            lines(
                "left side of comparison", //
                "found   : symbol",
                "required: (bigint|number|string)"))
        .run();
  }

  @Test
  public void testComparisonInStrictModeNoSpuriousWarning() {
    newTest()
        .addSource(
            "function f(x) {", //
            "  var y = 'asdf';",
            "  var z = y < x;",
            "}")
        .run();
  }

  @Test
  public void testComparisonTreatingUnknownAsNumber() {
    // Because 'x' is unknown, we allow either of the comparible types: number or string.
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {}",
            "function f(/** ? */ x) {",
            "  return (new Foo) < x;",
            "}")
        .addDiagnostic(
            lines(
                "left side of comparison", //
                "found   : Foo",
                "required: (bigint|number|string)"))
        .run();
  }

  @Test
  public void testComparisonTreatingUnknownAsNumber_leftTypeUnknown() {
    // Because 'x' is unknown, we allow either of the comparible types: number or string.
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    newTest()
        .addSource(
            "/** @constructor */",
            "function Bar() {}",
            "function f(/** ? */ x) {",
            "  return x < (new Bar);",
            "}")
        .addDiagnostic(
            lines(
                "right side of comparison", //
                "found   : Bar",
                "required: (bigint|number|string)"))
        .run();
  }

  @Test
  public void testEnumOfSymbol1() {
    newTest()
        .addSource(
            "",
            "/** @enum {symbol} */",
            "var ES = {A: Symbol('a'), B: Symbol('b')};",
            "",
            "/** @type {!Object<ES, number>} */",
            "var o = {};",
            "",
            "o[ES.A] = 1;")
        .run();
  }

  @Test
  public void testEnumOfSymbol2() {
    newTest()
        .addSource(
            "",
            "/** @enum {symbol} */",
            "var ES = {A: Symbol('a'), B: Symbol('b')};",
            "",
            "/** @type {!Object<number, number>} */",
            "var o = {};",
            "",
            "o[ES.A] = 1;")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : ES<symbol>",
                "required: number"))
        .run();
  }

  @Test
  public void testEnumOfSymbol4() {
    newTest()
        .addSource(
            "",
            "/** @enum {symbol} */",
            "var ES = {A: Symbol('a'), B: Symbol('b')};",
            "",
            "/** @const */",
            "var o = {};",
            "",
            "o[ES.A] = 1;")
        .run();
  }

  @Test
  public void testSymbol1() {
    newTest()
        .addSource(
            "/** @const */", //
            "var o = {};",
            "",
            "if (o[Symbol.iterator]) { /** ok */ };")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testSymbol2() {
    newTest()
        .addSource(
            "/** @const */", //
            "var o = new Symbol();")
        .addDiagnostic("cannot instantiate non-constructor")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testUnknownExtends1() {
    newTest()
        .addSource(
            "/**",
            " * @template T",
            " * @param {function(new: T)} clazz",
            " */",
            "function f(clazz) {",
            "  /**",
            "   * @constructor",
            "   * @extends {clazz}",
            "   */",
            "  function Foo() {}",
            "}")
        .run();
  }

  @Test
  public void testUnknownExtends2() {
    newTest()
        .addSource(
            "function f(/** function(new: ?) */ clazz) {",
            "  /**",
            "   * @constructor",
            "   * @extends {clazz}",
            "   */",
            "  function Foo() {}",
            "}")
        .addDiagnostic("Could not resolve type in @extends tag of Foo")
        .run();
  }

  private static final String MIXIN_DEFINITIONS =
      lines(
          "/** @constructor */",
          "function MyElement() {",
          "  /** @type {string} */",
          "  this.elemprop = 'asdf';",
          "}",
          "/** @record */",
          "function Toggle() {}",
          "/**",
          " * @param {string} x",
          " * @return {string}",
          " */",
          "Toggle.prototype.foobar = function(x) {};",
          "/**",
          " * @template T",
          " * @param {function(new:T)} superclass",
          " * @suppress {checkTypes}", // TODO(b/74120976): fix bug and remove suppression
          " */",
          "function addToggle(superclass) {",
          "  /**",
          "   * @constructor",
          "   * @extends {superclass}",
          "   * @implements {Toggle}",
          "   */",
          "  function Clazz() {",
          "    superclass.apply(this, arguments);",
          "  }",
          "  Clazz.prototype = Object.create(superclass.prototype);",
          "  /** @override */",
          "  Clazz.prototype.foobar = function(x) { return 'foobar ' + x; };",
          "  return Clazz;",
          "}");

  @Test
  public void testMixinApplication1() {
    newTest()
        .addSource(
            MIXIN_DEFINITIONS,
            "/**",
            " * @constructor",
            " * @extends {MyElement}",
            " * @implements {Toggle}",
            " */",
            "var MyElementWithToggle = addToggle(MyElement);",
            "(new MyElementWithToggle).foobar(123);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of MyElementWithToggle.prototype.foobar"
                    + " does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testMixinApplication1_inTypeSummaryWithVar() {
    newTest()
        .addExterns(
            "/** @typeSummary */",
            MIXIN_DEFINITIONS,
            "/**",
            " * @constructor",
            " * @extends {MyElement}",
            " * @implements {Toggle}",
            " */",
            "var MyElementWithToggle;")
        .addSource(
            "class SubToggle extends MyElementWithToggle {}", //
            "(new SubToggle).foobar(123);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of MyElementWithToggle.prototype.foobar"
                    + " does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testMixinApplication1_inTypeSummaryWithLet() {
    newTest()
        .addExterns(
            "/** @typeSummary */",
            MIXIN_DEFINITIONS,
            "/**",
            " * @constructor",
            " * @extends {MyElement}",
            " * @implements {Toggle}",
            " */",
            "let MyElementWithToggle;")
        .addSource(
            "class SubToggle extends MyElementWithToggle {}", //
            "(new SubToggle).foobar(123);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of MyElementWithToggle.prototype.foobar"
                    + " does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testMixinApplication2() {
    newTest()
        .addSource(
            MIXIN_DEFINITIONS,
            "/**",
            " * @constructor",
            " * @extends {MyElement}",
            " * @implements {Toggle}",
            " */",
            "var MyElementWithToggle = addToggle(MyElement);",
            "(new MyElementWithToggle).elemprop = 123;")
        .addDiagnostic(
            lines(
                "assignment to property elemprop of MyElementWithToggle",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testMixinApplication3() {
    newTest()
        .addSource(
            MIXIN_DEFINITIONS,
            "var MyElementWithToggle = addToggle(MyElement);",
            "/** @type {MyElementWithToggle} */ var x = 123;")
        .addDiagnostic("Bad type annotation. Unknown type MyElementWithToggle")
        .run();
  }

  @Test
  public void testReassignedSymbolAccessedInClosureIsFlowInsensitive() {
    // This code is technically correct, but the compiler will probably never be able to prove it.
    this.newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            "function f(){",
            "  for (var x = {}; ; x = {y: x.y}) {",
            "    x.y = 'str';",
            "    x.y = x.y.length",
            "    var g = (function(x) {",
            "      return function() {",
            "        if (x.y) -x.y;",
            "      };",
            "    })(x);",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "sign operator", //
                "found   : (number|string)",
                "required: number"))
        .run();
  }

  @Test
  public void testMethodOverriddenDirectlyOnThis() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function Foo() {",
            "  this.bar = function() { return 'str'; };",
            "}",
            "/** @return {number} */",
            "Foo.prototype.bar = function() {};")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testSuperclassDefinedInBlockUsingVar() {
    newTest()
        .addSource(
            "{",
            "  /** @constructor */",
            "  var Base = function() {};",
            "  /** @param {number} x */",
            "  Base.prototype.baz = function(x) {};",
            "}",
            "/** @constructor @extends {Base} */",
            "var Foo = function() {};",
            "/** @override */",
            "Foo.prototype.baz = function(x) {",
            "  var /** string */ y = x;",
            "};")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testSuperclassDefinedInBlockOnNamespace() {
    newTest()
        .addSource(
            "/** @const */",
            "var ns = {};",
            "{",
            "  /** @constructor */",
            "  ns.Base = function() {};",
            "  /** @param {number} x */",
            "  ns.Base.prototype.baz = function(x) {};",
            "}",
            "/** @constructor @extends {ns.Base} */",
            "ns.Foo = function() {};",
            "/** @override */",
            "ns.Foo.prototype.baz = function(x) {",
            "  var /** string */ y = x;",
            "};")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testPropertyReferenceOnShadowingParameter() {
    newTest()
        .addSource(
            "/** @const */",
            "var ns = {};",
            "ns.fn = function(/** number */ a){};",
            "class Namespace {",
            "  fn(/** string */ a){}",
            "}",
            "function test(/** !Namespace */ ns) {", // The parameter 'ns' shadows the global 'ns'.
            // Verify that ns.fn resolves to Namespace.prototype.fn, not the global ns.fn.
            "  ns.fn(0);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Namespace.prototype.fn does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testPropertyReferenceOnShadowingParameter_whenGlobalVariableIsUndeclared() {
    // Unlike the above case, this case never explicitly declares `ns` in the global scope.
    newTest()
        .includeDefaultExterns()
        .addExterns("ns.fn = function(/** number */ a){};")
        .addSource(
            "class Namespace {",
            "  fn(/** string */ a){}",
            "}",
            "function test(/** !Namespace */ ns) {", // The parameter 'ns' shadows the global 'ns'.
            // Verify that ns.fn resolves to Namespace.prototype.fn, not the global ns.fn.
            "  ns.fn(0);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Namespace.prototype.fn does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testPropertyReferenceOnShadowingVariable() {
    newTest()
        .addSource(
            "/** @const */",
            "var ns = {};",
            "ns.fn = function(/** number */ a){};",
            "class Namespace {",
            "  fn(/** string */ a){}",
            "}",
            "function test() {",
            // The local 'ns' shadows the global 'ns'.
            "  const /** !Namespace */ ns = /** @type {!Namespace} */ (unknown);",
            // Verify that ns.fn resolves to Namespace.prototype.fn, not the global ns.fn.
            "  ns.fn(0);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Namespace.prototype.fn does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testPropertyReferenceOnShadowingVariable_inferredLocalSlotOverridesGlobal() {
    newTest()
        .addSource(
            "/** @const */",
            "var ns = {};",
            "ns.fn = function(/** number */ a){};",
            "/** @unrestricted */",
            "class Namespace {}",
            "function test(/** !Namespace */ ns) {", // The parameter 'ns' shadows the global 'ns'.
            "  ns.fn = function(/** string */ a) {};",
            // Verify that ns.fn resolves to the locally assigned ns.fn, not the global ns.fn.
            "  ns.fn(0);",
            "}")
        .addDiagnostic("Property fn never defined on Namespace")
        .addDiagnostic(
            lines(
                "actual parameter 1 of ns.fn does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testReassigningPropertyOnClassTypeInFunction_localSlotOverridesPrototype() {
    newTest()
        .addSource(
            "/** @unrestricted */",
            "class Namespace {",
            "  fn(/** string */ a) {}",
            "}",
            "function test(/** !Namespace */ ns) {",
            // Assign ns.fn to a narrow type than Namespace.prototype.fn.
            "  ns.fn = function(/** string|number */ a) {};",
            // Verify that ns.fn resolves to the narrower local type, which can accept `number`.
            "  ns.fn(0);",
            "}")
        .run();
  }

  @Test
  public void testPropertyReferenceOnShadowingThisProperty() {
    newTest()
        .addSource(
            "class C {",
            "  constructor() {",
            "    /** @type {?number} */",
            "    this.size = null;",
            "  }",
            "  method() {",
            // TODO(b/132283774): Redeclaring 'this.size' should cause an error.
            "    /** @type {number} */",
            "    this.size = unknown;",
            "    const /** number */ num = this.size;",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testThisReferenceTighteningInInnerScope() {
    newTest()
        .addSource(
            "class C {",
            "  constructor() {",
            "    /** @type {?number} */",
            "    this.size = 0;",
            // Verify that the inferred type of `this.size`, which is `number`, propagates into
            // block scopes.
            "    { const /** number */ n = this.size; }",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testShadowedForwardReferenceHoisted() {
    newTest()
        .addSource(
            "/** @constructor */ var C = function() { /** @type {string} */ this.prop = 's';"
                + " };",
            "var fn = function() {",
            "  /** @type {C} */ var x = f();",
            "  /** @type {number} */ var n1 = x.prop;",
            "  /** @type {number} */ var n2 = new C().prop;",
            "  /** @constructor */ function C() { /** @type {number} */ this.prop = 1; };",
            "  /** @return {C} */ function fn() { return new C(); };",
            "",
            "}")
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
            "/** @constructor */",
            "var C = function() { };", // (1)
            "",
            "var fn = function() {",
            "  /** @type {?C} */ var innerC;", // (2)
            "",
            "  /** @constructor */",
            "  var C = function() { };", // (3)
            "",
            "  innerC = new C();", // (4)
            "}")
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
            "/** @type {!C} */ let innerC;", // (2)
            "",
            // NB: without the @template this test would pass due to b/110741413.
            "/** @template T */",
            "class C {};", // (3)
            "",
            "innerC = new C();", // (4)
            "export {}")
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
            "/** @type {!C} */ let innerC;", // (2)
            "",
            "class Parent {}",
            "class C extends Parent {};", // (3)
            "",
            "/** @type {!Parent} */ const p = innerC;", // (4)
            "export {}")
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
            "/** @constructor */",
            "var C = function() { };", // (1)
            "",
            "var fn = function() {",
            "  /** @constructor */",
            "  var C = function() { };", // (2)
            "",
            // This function will be hoisted above, and therefore type-analysed before, (2).
            "  /** @return {!C} */", // (3)
            "  function hoisted() { return new C(); };", // (4)
            "}")
        .run();
  }

  @Test
  public void testRefinedTypeInNestedShortCircuitingAndOr() {
    // Ensure we don't have a strict property warning, and do get the right type.
    // In particular, ensure we don't forget about tracking refinements in between the two.
    newTest()
        .addSource(
            "/** @constructor */ function B() {}",
            "/** @constructor @extends {B} */ function C() {}",
            "/** @return {string} */ C.prototype.foo = function() {};",
            "/** @return {boolean} */ C.prototype.bar = function() {};",
            "/** @constructor @extends {B} */ function D() {}",
            "/** @return {number} */ D.prototype.foo = function() {};",
            "/** @param {!B} arg",
            "    @return {null} */",
            "function f(arg) {",
            "  if ((arg instanceof C && arg.bar()) || arg instanceof D) {",
            "    return arg.foo();",
            "  }",
            "  return null;",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : (number|string)",
                "required: null"))
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
            "/** @const */ var ns = {};", //
            "var /** typeof ns */ x = ns;")
        .run();
  }

  @Test
  public void testTypeofType_namespaceMismatch() {
    newTest()
        .addSource(
            "/** @const */ var ns = {};", //
            "var /** typeof ns */ x = {};")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : {}",
                "required: {}"))
        .run();
  }

  @Test
  public void testTypeofType_namespaceForwardReferenceMismatch() {
    newTest()
        .addSource(
            "var /** typeof ns */ x = {};", //
            "/** @const */ var ns = {};")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : {}",
                "required: {}"))
        .run();
  }

  @Test
  public void testTypeofType_constructor() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            "/** @constructor */ function Foo() {}",
            "var /** !Array<typeof Foo> */ x = [];",
            "x.push(Foo);")
        .run();
  }

  @Test
  public void testTypeofType_constructorMismatch1() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            "/** @constructor */ function Foo() {}",
            "var /** !Array<(typeof Foo)> */ x = [];",
            "x.push(new Foo());")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Array.prototype.push does not match formal parameter",
                "found   : Foo",
                "required: (typeof Foo)"))
        .run();
  }

  @Test
  public void testTypeofType_constructorMismatch2() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            "/** @constructor */ function Foo() {}",
            "var /** !Array<!Foo> */ x = [];",
            "var /** typeof Foo */ y = Foo;",
            "x.push(y);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Array.prototype.push does not match formal parameter",
                "found   : (typeof Foo)",
                "required: Foo"))
        .run();
  }

  @Test
  public void testTypeofType_enum() {
    newTest()
        .addSource(
            "/** @enum */ var Foo = {A: 1}", //
            "var /** typeof Foo */ x = Foo;")
        .run();
  }

  @Test
  public void testTypeofType_enumFromCall() {
    newTest()
        .addSource(
            "/** @enum */ var Foo = {A: 1}",
            "/** @return {*} */ function getFoo() { return Foo; }",
            "var x = /** @type {typeof Foo} */ (getFoo());")
        .run();
  }

  @Test
  public void testTypeofType_enumMismatch1() {
    newTest()
        .addSource(
            "/** @enum */ var Foo = {A: 1}", //
            "var /** typeof Foo */ x = Foo.A;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Foo<number>",
                "required: enum{Foo}"))
        .run();
  }

  @Test
  public void testTypeofType_enumMismatch2() {
    newTest()
        .addSource(
            "/** @enum */ var Foo = {A: 1}",
            "/** @enum */ var Bar = {A: 1}",
            "var /** typeof Foo */ x = Bar;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : enum{Bar}",
                "required: enum{Foo}"))
        .run();
  }

  @Test
  public void testTypeofType_castNamespaceIncludesPropertiesFromTypeofType() {
    newTest()
        .addSource(
            "/** @const */ var ns1 = {};",
            "/** @type {string} */ ns1.foo;",
            "/** @const */ var ns2 = {};",
            "/** @type {number} */ ns2.bar;",
            "",
            "/** @const {typeof ns2} */ var ns = /** @type {?} */ (ns1);",
            "/** @type {null} */ var x = ns.bar;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testTypeofType_castNamespaceDoesNotIncludeOwnProperties() {
    newTest()
        .addSource(
            "/** @const */ var ns1 = {};",
            "/** @type {string} */ ns1.foo;",
            "/** @const */ var ns2 = {};",
            "/** @type {number} */ ns2.bar;",
            "",
            "/** @const {typeof ns2} */ var ns = /** @type {?} */ (ns1);",
            "/** @type {null} */ var x = ns.foo;")
        .addDiagnostic("Property foo never defined on ns")
        .run();
  }

  @Test
  public void testTypeofType_namespaceTypeIsAnAliasNotACopy() {
    newTest()
        .addSource(
            "/** @const */ var ns1 = {};",
            "/** @type {string} */ ns1.foo;",
            "",
            "/** @const {typeof ns1} */ var ns = /** @type {?} */ (x);",
            "",
            "/** @type {string} */ ns1.bar;",
            "",
            "/** @type {null} */ var x = ns.bar;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: null"))
        .run();
  }

  @Test
  public void testTypeofType_namespacedTypeNameResolves() {
    newTest()
        .addSource(
            "/** @const */ var ns1 = {};",
            "/** @constructor */ ns1.Foo = function() {};",
            "/** @const {typeof ns1} */ var ns = /** @type {?} */ (x);",
            "/** @type {!ns.Foo} */ var x = null;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : null",
                "required: ns1.Foo"))
        .run();
  }

  @Test
  public void testTypeofType_unknownType() {
    newTest()
        .addSource(
            "var /** ? */ x;", //
            "/** @type {typeof x} */ var y;")
        .addDiagnostic("Missing type for `typeof` value. The value must be declared and const.")
        .run();
  }

  @Test
  public void testTypeofType_namespacedTypeOnIndirectAccessNameResolves() {
    newTest()
        .addSource(
            "/** @const */ var ns1 = {};",
            "/** @constructor */ ns1.Foo = function() {};",
            "const ns2 = ns1;",
            // Verify this works although we have never seen any assignments to `ns2.Foo`
            "/** @const {typeof ns2.Foo} */ var Foo2 = /** @type {?} */ (x);",
            "/** @type {null} */ var x = new Foo2();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : ns1.Foo",
                "required: null"))
        .run();
  }

  @Test
  public void testTypeofType_namespacedTypeMissing() {
    newTest()
        .addSource(
            "/** @const */ var ns = {};",
            "/** @const {typeof ns.Foo} */ var Foo = /** @type {?} */ (x);")
        .addDiagnostic("Missing type for `typeof` value. The value must be declared and const.")
        .run();
  }

  @Test
  public void testTypeofType_withGlobalDeclaredVariableWithHoistedFunction() {
    newTest()
        .addSource(
            "/** @type {string|number} */",
            "var x = 1;",
            "function g(/** typeof x */ a) {}",
            "x = 'str';",
            "g(null);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of g does not match formal parameter",
                "found   : null",
                "required: (number|string)"))
        .run();
  }

  @Test
  public void testTypeofType_typeofForwardReferencedShadowingLocal() {
    newTest()
        .addSource(
            "/** @const {string} */",
            "var x = 'x';",
            "function f() {",
            "  function g(/** typeof x */ a) {}",
            "  /** @const {number} */",
            "  var x = 1;",
            "  g(null);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of g does not match formal parameter",
                "found   : null",
                "required: number"))
        .run();
  }

  @Test
  public void testTypeofType_withGlobalDeclaredVariableWithFunction() {
    newTest()
        .addSource(
            "/** @type {string|number} */",
            "var x = 1;",
            "let g = function(/** typeof x */ a) {}",
            "x = 'str';",
            "g(null);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of g does not match formal parameter",
                "found   : null",
                "required: (number|string)"))
        .run();
  }

  @Test
  public void testTypeofType_withGlobalInferredVariableWithFunctionExpression() {
    newTest()
        .addSource(
            "var x = 1;", //
            "var g = function (/** typeof x */ a) {}",
            "x = 'str';",
            "g(null);")
        .addDiagnostic(
            lines("Missing type for `typeof` value. The value must be declared and const."))
        .run();
  }

  @Test
  public void testTypeofType_withLocalInferredVariableInHoistedFunction() {
    newTest()
        .addSource(
            "function f() {",
            "  var x = 1;",
            "  function g(/** typeof x */ a) {}",
            "  x = 'str';",
            "  g(null);",
            "}")
        .addDiagnostic(
            lines("Missing type for `typeof` value. The value must be declared and const."))
        .run();
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariableWithHoistedFunction() {
    newTest()
        .addSource(
            "function f() {",
            "  /** @type {string|number} */",
            "  var x = 1;",
            "  function g(/** typeof x */ a) {}",
            "  x = 'str';",
            "  g(null);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of g does not match formal parameter",
                "found   : null",
                "required: (number|string)"))
        .run();
  }

  @Test
  public void testTypeofType_withLocalInferredVariableWithFunctionExpression() {
    newTest()
        .addSource(
            "function f() {",
            "  var x = 1;",
            "  var g = function (/** typeof x */ a) {}",
            "  x = 'str';",
            "  g(null);",
            "}")
        .addDiagnostic(
            lines("Missing type for `typeof` value. The value must be declared and const."))
        .run();
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariableWithFunctionExpression() {
    newTest()
        .addSource(
            "function f() {",
            "  /** @type {string|number} */",
            "  var x = 1;",
            "  var g = function (/** typeof x */ a) {}",
            "  x = 'str';",
            "  g(null);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of g does not match formal parameter",
                "found   : null",
                "required: (number|string)"))
        .run();
  }

  @Test
  public void testTypeofType_withLocalInferredVariable() {
    newTest()
        .addSource(
            "function f() {",
            "  var x = 1;",
            "  /** @type {typeof x} */",
            "  var g = null",
            "  x = 'str';",
            "}")
        .addDiagnostic(
            lines("Missing type for `typeof` value. The value must be declared and const."))
        .run();
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariable1() {
    newTest()
        .addSource(
            "function f() {",
            "  /** @type {string|number} */",
            "  var x = 1;",
            "  /** @type {typeof x} */",
            "  var g = null",
            "  x = 'str';",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : null",
                "required: (number|string)"))
        .run();
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariableAfterReassignment() {
    newTest()
        .addSource(
            "function f() {",
            "  /** @type {string|number} */",
            "  var x = 1;",
            "  x = 'str';",
            "  /** @type {typeof x} */",
            "  var g = null",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : null",
                "required: (number|string)"))
        .run();
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariableAfterTightening() {
    newTest()
        .addSource(
            "function f() {",
            "  /** @type {string|number} */",
            "  var x = 'str';",
            "  if (typeof x == 'string') {",
            "    /** @type {typeof x} */",
            "    let g = null",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : null",
                "required: (number|string)"))
        .run();
  }

  @Test
  public void testTypeNameAliasOnAliasedNamespace() {
    newTest()
        .addSource(
            "class Foo {};",
            "/** @enum {number} */ Foo.E = {A: 1};",
            "const F = Foo;",
            "const E = F.E;",
            "/** @type {E} */ let e = undefined;",
            "")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : undefined",
                "required: Foo.E<number>"))
        .run();
  }

  @Test
  public void testAssignOp() {
    newTest()
        .addSource(
            "function fn(someUnknown) {",
            "  var x = someUnknown;",
            "  x *= 2;", // infer 'x' to now be 'number'
            "  var /** null */ y = x;",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testAnnotatedObjectLiteralInBlocklessArrow1() {
    newTest()
        .addSource(
            "function f(/** {g: function(number): undefined} */ x) {}",
            "() => f({/** @param {string} x */ g(x) {}});")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : {g: function(string): undefined}",
                "required: {g: function(number): undefined}",
                "missing : []",
                "mismatch: [g]"))
        .run();
  }

  @Test
  public void testAnnotatedObjectLiteralInBlocklessArrow2() {
    newTest()
        .addSource(
            "function f(/** {g: function(): string} */ x) {}",
            "() => f({/** @constructor */ g: function() {}});")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : {g: (typeof <anonymous@[testcode]:2>)}",
                "required: {g: function(): string}",
                "missing : []",
                "mismatch: [g]"))
        .run();
  }

  @Test
  public void testAnnotatedObjectLiteralInBlocklessArrow3() {
    newTest()
        .addSource(
            "/** @constructor */ function G() {}",
            "function f(/** {g: function(): string} */ x) {}",
            "() => f({/** @constructor */ g: G});")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : {g: (typeof G)}",
                "required: {g: function(): string}",
                "missing : []",
                "mismatch: [g]"))
        .run();
  }

  @Test
  public void testNativePromiseTypeWithExterns() {
    // Test that we add Promise prototype properties defined in externs to the native Promise type
    newTest()
        .addSource(
            "var p = new Promise(function(resolve, reject) { resolve(3); });",
            "p.then(result => {}, error => {});")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testNativeThenableType() {
    // Test that the native Thenable type is not nullable even without externs
    newTest()
        .addSource("var /** Thenable */ t = null;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : null",
                "required: {then: ?}"))
        .run();
  }

  @Test
  public void testFunctionOnObjLitWithAngleBracketKey() {
    newTest()
        .addSource(
            "/** @const {!Object<function()>} */", //
            "var x = {'<': function() {}};")
        .run();
  }

  @Test
  public void testFunctionOnObjLitWithAngleBracketKeyChecked() {
    newTest()
        .addSource(
            "/** @const {!Object<function(): number>} */",
            "var x = {'<': function() { return 'str'; }};")
        .run();
  }

  @Test
  public void testFunctionOnObjLitWithAngleBracketKeyAndJsdoc() {
    newTest()
        .addSource(
            "/** @const */",
            "var x = {",
            "  /** @param {number} arg */",
            "  '<': function(arg) {},",
            "};")
        .run();
  }

  @Test
  public void testClassOnObjLitWithAngleBracketKey() {
    newTest()
        .addSource(
            "/** @const */", //
            "var x = {",
            "  /** @constructor */",
            "  '<': function() {},",
            "};")
        .run();
  }

  @Test
  public void testQuotedPropertyWithDotInType() {
    newTest()
        .addSource(
            "/** @const */",
            "var x = {",
            "  /** @constructor */",
            "  'y.A': function() {},",
            "};",
            "var /** x.y.A */ a;")
        .addDiagnostic("Bad type annotation. Unknown type x.y.A")
        .run();
  }

  @Test
  public void testQuotedPropertyWithDotInCode() {
    newTest()
        .addSource(
            "/** @const */",
            "var x = {",
            "  /** @constructor */",
            "  'y.A': function() {},",
            "};",
            "var a = new x.y.A();")
        .addDiagnostic("Property y never defined on x")
        .addDiagnostic("Property A never defined on x.y" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testClassTemplateParamsInMethodBody() {
    newTest()
        .addSource(
            "/** @constructor @template T */",
            "function Foo() {}",
            "Foo.prototype.foo = function() {",
            "  var /** T */ x;",
            "};")
        .run();
  }

  @Test
  public void testClassTtlParamsInMethodBody() {
    newTest()
        .addSource(
            "/** @constructor @template T := 'number' =: */",
            "function Foo() {}",
            "Foo.prototype.foo = function() {",
            "  var /** T */ x;",
            "};")
        .addDiagnostic("Template type transformation T not allowed on classes or interfaces")
        .addDiagnostic("Bad type annotation. Unknown type T")
        .run();
  }

  @Test
  public void testClassTtlParamsInMethodSignature() {
    newTest()
        .addSource(
            "/** @constructor @template T := 'number' =: */",
            "function Foo() {}",
            "/** @return {T} */",
            "Foo.prototype.foo = function() {};")
        .addDiagnostic("Template type transformation T not allowed on classes or interfaces")
        .addDiagnostic("Bad type annotation. Unknown type T")
        .run();
  }

  @Test
  public void testFunctionTemplateParamsInBody() {
    newTest()
        .addSource(
            "/** @template T */",
            "function f(/** !Array<T> */ arr) {",
            "  var /** T */ first = arr[0];",
            "}")
        .run();
  }

  @Test
  public void testFunctionTtlParamsInBody() {
    newTest()
        .addSource(
            "/** @template T := 'number' =: */",
            "function f(/** !Array<T> */ arr) {",
            "  var /** T */ first = arr[0];",
            "}")
        .run();
  }

  @Test
  public void testFunctionTemplateParamsInBodyMismatch() {
    newTest()
        .addSource(
            "/** @template T, V */",
            "function f(/** !Array<T> */ ts, /** !Array<V> */ vs) {",
            "  var /** T */ t = vs[0];",
            "}")
        .run();
    // TODO(b/35241823): This should be an error, but we currently treat generic types
    // as unknown. Once we have bounded generics we can treat templates as unique types.
    // lines(
    //     "actual parameter 1 of f does not match formal parameter",
    //     "found   : V",
    //     "required: T"));
  }

  @Test
  public void testClassAndMethodTemplateParamsInMethodBody() {
    newTest()
        .addSource(
            "/** @constructor @template T */",
            "function Foo() {}",
            "/** @template U */",
            "Foo.prototype.foo = function() {",
            "  var /** T */ x;",
            "  var /** U */ y;",
            "};")
        .run();
  }

  @Test
  public void testNestedFunctionTemplates() {
    newTest()
        .addSource(
            "/** @constructor @template A, B */",
            "function Foo(/** A */ a, /** B */ b) {}",
            "/** @template T */",
            "function f(/** T */ t) {",
            "  /** @template S */",
            "  function g(/** S */ s) {",
            "    var /** !Foo<T, S> */ foo = new Foo(t, s);",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testNestedFunctionTemplatesMismatch() {
    newTest()
        .addSource(
            "/** @constructor @template A, B */",
            "function Foo(/** A */ a, /** B */ b) {}",
            "/** @template T */",
            "function f(/** T */ t) {",
            "  /** @template S */",
            "  function g(/** S */ s) {",
            "    var /** !Foo<T, S> */ foo = new Foo(s, t);",
            "  }",
            "}")
        .run();
    // TODO(b/35241823): This should be an error, but we currently treat generic types
    // as unknown. Once we have bounded generics we can treat templates as unique types.
    // lines(
    //     "initializing variable",
    //     "found   : Foo<S, T>",
    //     "required: Foo<T, S>"));
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
            "class Foo {",
            "  takesString(/** string */ stringParameter) {}",
            "}",
            "/** @param {*} all */",
            "function f(all) {",
            "  /** some jsdoc */",
            "  (/** @type {!Foo} */ (all)).takesString(0);",
            "}")
        .run();
  }

  @Test
  public void testInvalidComparisonsInStrictOperatorsMode() {
    newTest()
        .addSource("function f(/** (void|string) */ x, /** void */ y) { return x < y; }")
        .addDiagnostic(
            lines(
                "right side of comparison", //
                "found   : undefined",
                "required: string"))
        .run();
  }

  @Test
  public void testStrayTypedefOnRecordInstanceDoesNotModifyRecord() {
    // This is a common pattern for angular.  This test is checking that adding the property to one
    // instance does not require all other instances to specify it as well.
    newTest()
        .addSource(
            "/** @record */",
            "function Component() {}",
            "",
            "/** @const {!Component} */",
            "var MyComponent = {};",
            "/** @typedef {string} */",
            "MyComponent.MyString;",
            "",
            "/** @const {!Component} */",
            "var OtherComponent = {};")
        .run();
  }

  @Test
  public void testStrayPropertyOnRecordInstanceDoesNotModifyRecord() {
    // This is a common pattern for angular.  This test is checking that adding the property to one
    // instance does not require all other instances to specify it as well.
    newTest()
        .addSource(
            "/** @record */",
            "function Component() {}",
            "",
            "/** @const {!Component} */",
            "var MyComponent = {};",
            "/** @const {string} */",
            "MyComponent.NAME = 'MyComponent';", // strict inexistent property is usually suppressed
            "",
            "/** @const {!Component} */",
            "var OtherComponent = {};")
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testStrayTypedefOnRecordTypedefDoesNotModifyRecord() {
    // This is a common pattern for angular.  This test is checking that adding the property to one
    // instance does not require all other instances to specify it as well.
    newTest()
        .addSource(
            "/** @typedef {{foo: number}} */",
            "var Component;",
            "",
            "/** @const {!Component} */",
            "var MyComponent = {foo: 1};",
            "/** @typedef {string} */",
            "MyComponent.MyString;",
            "",
            "/** @const {!Component} */",
            "var OtherComponent = {foo: 2};")
        .run();
  }

  @Test
  public void testCanUseConstToAssignValueToTypedef() {
    newTest()
        .addSource(
            "/** @typedef {!Object<string, number>} */", //
            "const Type = {};")
        .run();
  }

  @Test
  public void testLetTypedefWithAssignedValue_reassigned() {
    newTest()
        .addSource(
            "/** @typedef {!Object<string, number>} */", //
            "let Type = {};",
            "Type = {x: 3};")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : {}",
                "required: None"))
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : {x: number}",
                "required: None"))
        .run();
  }

  @Test
  public void testLetTypedefWithAssignedValue_notReassigned() {
    newTest()
        .addSource(
            "/** @typedef {!Object<string, number>} */", //
            "let Type = {};")
        .run();
  }

  @Test
  public void testStrayPropertyOnRecordTypedefDoesNotModifyRecord() {
    // This is a common pattern for angular.  This test is checking that adding the property to one
    // instance does not require all other instances to specify it as well.
    newTest()
        .addSource(
            "/** @typedef {{foo: number}} */",
            "var Component;",
            "",
            "/** @const {!Component} */",
            "var MyComponent = {foo: 1 };",
            "/** @const {string} */",
            "MyComponent.NAME = 'MyComponent';",
            "",
            "/** @const {!Component} */",
            "var OtherComponent = {foo: 2};")
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testCheckRecursiveTypedefSubclassOfNominalClass() {
    newTest()
        .addSource(
            "/** @typedef {{self: !Bar}} */",
            "var Foo;",
            "/** @typedef {!Foo} */",
            "var Bar;",
            "",
            "/** @type {!Foo} */",
            "var foo;",
            "",
            "class Baz {",
            "  constructor() {",
            "    /** @type {!Baz} */ this.self = this;",
            "  }",
            "}",
            "",
            "const /** !Baz */ x = foo;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : {self: {...}}",
                "required: Baz"))
        .run();
  }

  @Test
  public void testCheckNominalClassSubclassOfRecursiveTypedef() {
    newTest()
        .addSource(
            "/** @typedef {{self: !Bar}} */",
            "var Foo;",
            "/** @typedef {!Foo} */",
            "var Bar;",
            "",
            "class Baz {",
            "  constructor() {",
            "    /** @type {!Baz} */ this.self = this;",
            "  }",
            "}",
            "",
            "const /** !Foo */ x = new Baz();")
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
            "/** @struct @constructor */",
            "function MyClass() {}",
            "",
            "/**",
            " * @override",
            " * @return {?string}",
            " * @suppress {checkTypes}", // J2CL allows this, so we need to test it.
            " */",
            "MyClass.prototype.toString = function() {};",
            "",
            // To do a strucutural match, this type needs at least one additional property.
            // There is no empty `{}` type.
            "/** @return {number} */",
            "MyClass.prototype.x = function() {};",
            "",
            "var /** {x: !Function} */ instance = new MyClass();")
        .run();
  }

  @Test
  public void testForwardReferencedRecordTypeUnionedWithConcreteType() {
    // These types should be disjoint and both should be preserved both before and after type
    // resolution.  This is not particularly special, but it covers a previously-uncovered case.
    newTest()
        .addSource(
            "class Concrete {}",
            "",
            "/**",
            " * @param {!ForwardReferenced|!Concrete} arg",
            " */",
            "var bar = (arg) => {};",
            "",
            "/**",
            " * @typedef {{baz}}",
            " */",
            "var ForwardReferenced;",
            "",
            "/**",
            " * @param {!Concrete} arg",
            " */",
            "var foo = (arg) => bar(arg);",
            "")
        .run();
  }

  @Test
  public void testUnionCollapse_recordWithOnlyOptionalPropertiesDoesNotSupercedeArrayOrFunction() {
    // Ensure the full union type is preserved
    newTest()
        .addSource(
            "/** @typedef {{x: (string|undefined)}} */",
            "var Options;",
            "/** @constructor */",
            "function Foo() {}",
            "/** @typedef {function(new: Foo)|!Array<function(new: Foo)>} */",
            "var FooCtor;",
            "/** @type {FooCtor|Options} */",
            "var x;",
            "/** @type {null} */",
            "var y = x;")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : (Array<function(new:Foo): ?>|function(new:Foo): ?|{x:"
                    + " (string|undefined)})",
                "required: null"))
        .run();
  }

  @Test
  public void testCyclicUnionTypedefs() {
    // TODO(b/112964849): This case should not throw anything.
    assertThrows(
        StackOverflowError.class,
        newTest()
                .addSource(
                    "/** @typedef {Foo|string} */",
                    "var Bar;",
                    "/** @typedef {Bar|number} */",
                    "var Foo;",
                    "var /** Foo */ foo;",
                    "var /** null */ x = foo;",
                    "")
                .addDiagnostic(
                    lines(
                        "initializing variable", //
                        "found   : (number|string)",
                        "required: null"))
            ::run);
  }

  @Test
  public void testAtRecordOnVarObjectLiteral_warns() {
    newTest()
        .addSource("/** @record */ var X = {};")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : {}",
                "required: function(this:X): ?"))
        .run();
  }

  @Test
  public void testAtRecordOnConstObjectLiteral_warns() {
    newTest()
        .addSource("/** @record */ const X = {};")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : {}",
                "required: function(this:X): ?"))
        .run();
  }

  @Test
  public void testAtConstructorOnConstObjectLiteral_warns() {
    newTest()
        .addSource("/** @constructor */ const X = {};")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : {}",
                "required: function(new:X): ?"))
        .run();
  }

  @Test
  public void testDeclarationAnnotatedEnum_warnsIfAssignedNonEnumRhs() {
    newTest()
        .addSource(
            // Y looks similar to an enum but is not actually annotated as such
            "const Y = {A: 0, B: 1}; /** @enum {number} */ const X = Y;")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : {\n  A: number,\n  B: number\n}",
                "required: enum{X}"))
        .run();
  }

  @Test
  public void testEs6ExtendCannotUseGoogInherits() {
    testClosureTypes(
        lines(
            "class Super {}",
            "/** @extends {Super} */",
            "class Sub {}",
            "goog.inherits(Sub, Super);"),
        "Do not use goog.inherits with ES6 classes. Use the ES6 `extends` keyword to inherit"
            + " instead.");
  }

  @Test
  public void testParameterShadowsNamespace() {
    // NOTE: This is a pattern used to work around the fact that @ngInjected constructors very
    // frequently (and unavoidably) shadow global namespaces (i.e. angular.modules) with constructor
    // parameters.
    newTest()
        .addSource(
            "/** @constructor @param {!service.Service} service */",
            "var Controller = function(service) {",
            "  /** @private {!service.Service} */",
            "  this.service = service;",
            "};",
            "/** @const */",
            "var service = {};",
            "/** @constructor */",
            "service.Service = function() {};")
        .addDiagnostic(
            lines(
                "Bad type annotation. Unknown type service.Service",
                "It's possible that a local variable called 'service' is shadowing "
                    + "the intended global namespace."))
        .run();
  }

  @Test
  public void testBangOperatorOnForwardReferencedType() {
    newTest()
        .addSource(
            "/** @typedef {?number} */",
            "var Foo;",
            "/** @return {!Foo} */",
            "function f() {}",
            "/** @const {!Foo} */",
            "var x = f();")
        .run();
  }

  @Test
  public void testBangOperatorOnForwardReferencedType_mismatch() {
    newTest()
        .addSource(
            "/** @type {!Foo} */",
            "var x;",
            "/** @typedef {?number} */",
            "var Foo;",
            "/** @type {?Foo} */",
            "var y;",
            "x = y;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : (null|number)",
                "required: number"))
        .run();
  }

  @Test
  public void testBangOperatorOnIndirectlyForwardReferencedType() {
    newTest()
        .addSource(
            "/** @return {!b.Foo} */",
            "function f() {}",
            "/** @const {!b.Foo} */",
            "var x = f();",
            "",
            "const a = {};",
            "/** @typedef {?number} */",
            "a.Foo;",
            "const b = a;",
            "")
        .run();
  }

  @Test
  public void testBangOperatorOnIndirectlyForwardReferencedType_mismatch() {
    newTest()
        .addSource(
            "/** @return {?b.Foo} */",
            "function f() {}",
            "/** @const {!b.Foo} */",
            "var x = f();",
            "",
            "const a = {};",
            "/** @typedef {?number} */",
            "a.Foo;",
            "const b = a;",
            "")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : (null|number)",
                "required: number"))
        .run();
  }

  @Test
  public void testBangOperatorOnTypedefForShadowedNamespace() {
    // NOTE: This is a pattern used to work around the fact that @ngInjected constructors very
    // frequently (and unavoidably) shadow global namespaces (i.e. angular.modules) with constructor
    // parameters.
    newTest()
        .addSource(
            "/** @typedef {!service.Service} */",
            "var serviceService;",
            "/** @constructor @param {!service.Service} service */",
            "var Controller = function(service) {",
            "  /** @private {!serviceService} */",
            "  this.service = service;",
            "};",
            "/** @const */",
            "var service = {};",
            "/** @constructor */",
            "service.Service = function() {};")
        .run();
  }

  @Test
  public void testNullCheckAvoidsInexistentPropertyError() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "function use(x) {}",
            "/** @constructor */ function Foo() {}",
            "var /** !Foo */ foo = new Foo();",
            "if (foo.bar != null) use(foo);")
        .run();
  }

  @Test
  public void testTripleEqualsNonNullCheckGivesInexistentPropertyError() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "function use(x) {}",
            "/** @constructor */ function Foo() {}",
            "var /** !Foo */ foo = new Foo();",
            "if (foo.bar !== null) use(foo);")
        .addDiagnostic("Property bar never defined on Foo")
        .run();
  }

  @Test
  public void testGenericConstructorCrash() {
    newTest()
        .addSource(
            "/** @template T */",
            "class Base {",
            " /**",
            "  * @param {(function(function(new: T, ...?)): T)=} instantiate",
            "  * @return {!Array<T>}",
            "  */",
            " delegates(instantiate = undefined) {",
            "   return [instantiate];",
            " };",
            "}",
            "class Bar {}",
            "class Foo {}",
            "",
            "/** @type {Base<Foo|Bar>} */",
            "const c = new Base();",
            "",
            "c.delegates(ctor => new ctor(42));")
        .run();
  }

  @Test
  public void testLegacyGoogModuleExportTypecheckedAgainstGlobal_simpleModuleId() {
    newTest()
        .addExterns(
            "/** @const @suppress {duplicate} */", //
            "var globalNs = {};")
        .addSource(
            "goog.module('globalNs');", //
            "goog.module.declareLegacyNamespace();",
            "exports = 0;")
        .addDiagnostic(
            lines(
                "legacy goog.module export", //
                "found   : number",
                "required: {}"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testLegacyGoogModuleExportTypecheckedAgainstGlobal_simpleModuleIdWithProperties() {
    newTest()
        .addExterns(
            "/** @const @suppress {duplicate} */", //
            "var globalNs = {};")
        .addSource(
            "goog.module('globalNs');",
            "goog.module.declareLegacyNamespace();",
            "exports.prop = 0;")
        .addDiagnostic(
            lines(
                "legacy goog.module export", //
                "found   : {prop: number}",
                "required: {}"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testLegacyGoogModuleExportTypecheckedAgainstGlobal_dottedModuleId() {
    newTest()
        .addExterns(
            "/** @const @suppress {duplicate} */",
            "var globalNs = {};",
            "/** @suppress {duplicate} */",
            "globalNs.Ctor = class {};")
        .includeDefaultExterns()
        .addSource(
            "goog.module('globalNs.Ctor');",
            "goog.module.declareLegacyNamespace();",
            "class Ctor {}",
            "exports = Ctor;")
        .addDiagnostic(
            lines(
                "assignment to property Ctor of globalNs",
                "found   : (typeof Ctor)",
                "required: (typeof globalNs.Ctor)"))
        .run();
  }

  @Test
  public void testDynamicImportSpecifier() {
    newTest()
        .addSource("var foo = undefined; import(foo);")
        .addDiagnostic(
            lines(
                "dynamic import specifier", //
                "found   : undefined",
                "required: string"))
        .run();
  }

  @Test
  public void testDynamicImport1() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroup.forType(ModuleLoader.INVALID_MODULE_PATH), CheckLevel.OFF);
    newTest()
        .addSource("/** @type {number} */ var foo = import('foo.js');")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Promise<?>",
                "required: number"))
        .run();
  }

  @Test
  public void testDynamicImport2() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroup.forType(ModuleLoader.INVALID_MODULE_PATH), CheckLevel.OFF);
    newTest().addSource("/** @type {Promise} */ var foo2 = import('foo.js');").run();
  }

  @Test
  public void testDynamicImport3() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroup.forType(ModuleLoader.INVALID_MODULE_PATH), CheckLevel.OFF);
    newTest()
        .addSource("/** @type {Promise<{default: number}>} */ var foo = import('foo.js');")
        .run();
  }

  @Test
  public void testConflictingGetterSetterType() {
    newTest()
        .addSource(
            "class C {",
            "  /** @return {string} */",
            "  get value() { }",
            "",
            "  /** @param {number} v */",
            "  set value(v) { }",
            "}")
        .addDiagnostic(CONFLICTING_GETTER_SETTER_TYPE)
        .run();
  }

  @Test
  public void testConflictingGetterSetterTypeSuppressed() {
    newTest()
        .addSource(
            "/** @suppress {checkTypes} */",
            "class C {",
            "  /** @return {string} */",
            "  get value() { }",
            "",
            "  /** @param {number} v */",
            "  set value(v) { }",
            "}")
        .run();
  }

  @Test
  public void testSuppressCheckTypesOnStatements() {
    newTest()
        .addSource(
            "/** @param {string} s */",
            "function takesString(s) {}",
            "",
            "takesString(0);", // verify this is an error
            "/** @suppress {checkTypes} */",
            "takesString(null);",
            "/** @suppress {checkTypes} */",
            "(0, takesString(undefined));")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesString does not match formal parameter",
                "found   : number",
                "required: string"))
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

  private void testClosureTypes(String js, @Nullable String description) {
    testClosureTypesMultipleWarnings(
        js, description == null ? null : ImmutableList.of(description));
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
        actualWarningDescriptions.add(compiler.getWarnings().get(i).getDescription());
      }
      assertThat(actualWarningDescriptions).isEqualTo(new HashSet<>(descriptions));
    }
  }
}
