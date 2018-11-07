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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.ScopeSubject.assertScope;
import static com.google.javascript.jscomp.TypeCheck.INSTANTIATE_ABSTRACT_CLASS;
import static com.google.javascript.jscomp.TypeCheck.STRICT_INEXISTENT_PROPERTY;
import static com.google.javascript.jscomp.parsing.JsDocInfoParser.BAD_TYPE_WIKI_LINK;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link TypeCheck}.
 *
 */

@RunWith(JUnit4.class)
public final class TypeCheckTest extends TypeCheckTestCase {

  private static final String SUGGESTION_CLASS =
      "/** @constructor\n */\n"
      + "function Suggest() {}\n"
      + "Suggest.prototype.a = 1;\n"
      + "Suggest.prototype.veryPossible = 1;\n"
      + "Suggest.prototype.veryPossible2 = 1;\n";

  private static final String ILLEGAL_PROPERTY_CREATION_MESSAGE = "Cannot add a property"
      + " to a struct instance after it is constructed. (If you already declared the property,"
      + " make sure to give it a type.)";

  @Override
  protected CompilerOptions getDefaultOptions() {
    CompilerOptions options = super.getDefaultOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    return options;
  }

  @Test
  public void testInitialTypingScope() {
    TypedScope s = new TypedScopeCreator(compiler,
        CodingConventions.getDefault()).createInitialScope(
            new Node(Token.ROOT));

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
    testTypes(
        "/** @private {number} */ var x = false;",
        "initializing variable\n"
        + "found   : boolean\n"
        + "required: number");
  }

  @Test
  public void testTypeCheck1() {
    testTypes("/**@return {void}*/function foo(){ if (foo()) return; }");
  }

  @Test
  public void testTypeCheck2() {
    testTypes(
        "/**@return {void}*/function foo(){ var x=foo(); x--; }",
        "increment/decrement\n"
        + "found   : undefined\n"
        + "required: number");
  }

  @Test
  public void testTypeCheck4() {
    testTypes("/**@return {void}*/function foo(){ !foo(); }");
  }

  @Test
  public void testTypeCheck5() {
    testTypes("/**@return {void}*/function foo(){ var a = +foo(); }");
  }

  @Test
  public void testTypeCheck6() {
    testTypes(
        "/**@return {void}*/function foo(){"
        + "/** @type {undefined|number} */var a;if (a == foo())return;}");
  }

  @Test
  public void testTypeCheck8() {
    testTypes("/**@return {void}*/function foo(){do {} while (foo());}");
  }

  @Test
  public void testTypeCheck9() {
    testTypes("/**@return {void}*/function foo(){while (foo());}");
  }

  @Test
  public void testTypeCheck10() {
    testTypes("/**@return {void}*/function foo(){for (;foo(););}");
  }

  @Test
  public void testTypeCheck11() {
    testTypes("/**@type {!Number} */var a;"
        + "/**@type {!String} */var b;"
        + "a = b;",
        "assignment\n"
        + "found   : String\n"
        + "required: Number");
  }

  @Test
  public void testTypeCheck12() {
    testTypes("/**@return {!Object}*/function foo(){var a = 3^foo();}",
        "bad right operand to bitwise operator\n" +
        "found   : Object\n" +
        "required: (boolean|null|number|string|undefined)");
  }

  @Test
  public void testTypeCheck13() {
    testTypes("/**@type {!Number|!String}*/var i; i=/xx/;",
        "assignment\n" +
        "found   : RegExp\n" +
        "required: (Number|String)");
  }

  @Test
  public void testTypeCheck14() {
    testTypes("/**@param {?} opt_a*/function foo(opt_a){}");
  }

  @Test
  public void testTypeCheck15() {
    testTypes("/**@type {Number|null} */var x;x=null;x=10;",
        "assignment\n" +
        "found   : number\n" +
        "required: (Number|null)");
  }

  @Test
  public void testTypeCheck16() {
    testTypes("/**@type {Number|null} */var x='';",
              "initializing variable\n" +
              "found   : string\n" +
              "required: (Number|null)");
  }

  @Test
  public void testTypeCheck17() {
    testTypes("/**@return {Number}\n@param {Number} opt_foo */\n" +
        "function a(opt_foo){\nreturn /**@type {Number}*/(opt_foo);\n}");
  }

  @Test
  public void testTypeCheck18() {
    testTypes("/**@return {RegExp}\n*/\n function a(){return new RegExp();}");
  }

  @Test
  public void testTypeCheck19() {
    testTypes("/**@return {Array}\n*/\n function a(){return new Array();}");
  }

  @Test
  public void testTypeCheck20() {
    testTypes("/**@return {Date}\n*/\n function a(){return new Date();}");
  }

  @Test
  public void testTypeCheckBasicDowncast() {
    testTypes("/** @constructor */function foo() {}\n" +
                  "/** @type {Object} */ var bar = new foo();\n");
  }

  @Test
  public void testTypeCheckNoDowncastToNumber() {
    testTypes("/** @constructor */function foo() {}\n" +
                  "/** @type {!Number} */ var bar = new foo();\n",
        "initializing variable\n" +
        "found   : foo\n" +
        "required: Number");
  }

  @Test
  public void testTypeCheck21() {
    testTypes("/** @type {Array<String>} */var foo;");
  }

  @Test
  public void testTypeCheck22() {
    testTypes("/** @param {Element|Object} p */\nfunction foo(p){}\n" +
                  "/** @constructor */function Element(){}\n" +
                  "/** @type {Element|Object} */var v;\n" +
                  "foo(v);\n");
  }

  @Test
  public void testTypeCheck23() {
    testTypes("/** @type {(Object|Null)} */var foo; foo = null;");
  }

  @Test
  public void testTypeCheck24() {
    testTypes("/** @constructor */function MyType(){}\n" +
        "/** @type {(MyType|Null)} */var foo; foo = null;");
  }

  @Test
  public void testTypeCheck25() {
    testTypes(
        lines(
            "function foo(/** {a: number} */ obj) {};",
            "foo({b: 'abc'});"),
        lines(
            "actual parameter 1 of foo does not match formal parameter",
            "found   : {a: (number|undefined), b: string}",
            "required: {a: number}",
            "missing : []",
            "mismatch: [a]"));
  }

  @Test
  public void testTypeCheck26() {
    testTypes(
        lines(
            "function foo(/** {a: number} */ obj) {};",
            "foo({a: 'abc'});"),
        lines(
            "actual parameter 1 of foo does not match formal parameter",
            "found   : {a: (number|string)}",
            "required: {a: number}",
            "missing : []",
            "mismatch: [a]"));
  }

  @Test
  public void testTypeCheck27() {
    testTypes("function foo(/** {a: number} */ obj) {};"
        + "foo({a: 123});");
  }

  @Test
  public void testTypeCheck28() {
    testTypes("function foo(/** ? */ obj) {};"
        + "foo({a: 123});");
  }

  @Test
  public void testTypeCheckInlineReturns() {
    testTypes(
        "function /** string */ foo(x) { return x; }" +
        "var /** number */ a = foo('abc');",
        "initializing variable\n"
        + "found   : string\n"
        + "required: number");
  }

  @Test
  public void testTypeCheckDefaultExterns() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        lines(
            "/** @param {string} x */", // preserve newlines
            "function f(x) {}",
            "f([].length);"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testTypeCheckCustomExterns() {
    testTypesWithExterns(
        DEFAULT_EXTERNS + "/** @type {boolean} */ Array.prototype.oogabooga;",
        "/** @param {string} x */ function f(x) {}" +
        "f([].oogabooga);" ,
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: string", false);
  }

  @Test
  public void testTypeCheckCustomExterns2() {
    testTypesWithExterns(
        DEFAULT_EXTERNS + "/** @enum {string} */ var Enum = {FOO: 1, BAR: 1};",
        "/** @param {Enum} x */ function f(x) {} f(Enum.FOO); f(true);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: Enum<string>",
        false);
  }

  @Test
  public void testDontCrashOnRecursiveTemplateReference() {
    testTypesWithExtraExterns(
        lines(
          "/**",
          " * @constructor @struct",
          " * @implements {Iterable<!Array<KEY|VAL>>}",
          " * @template KEY, VAL",
          " */",
          "function Map(opt_iterable) {}"),
        lines(
          "/** @constructor @implements {Iterable<VALUE>} @template VALUE */",
          "function Foo() {",
          "  /** @type {!Map<VALUE, VALUE>} */ this.map = new Map;",
          "}"));
  }

  @Test
  public void testTemplatizedArray1() {
    testTypes("/** @param {!Array<number>} a\n" +
        "* @return {string}\n" +
        "*/ var f = function(a) { return a[0]; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testTemplatizedArray2() {
    testTypes("/** @param {!Array<!Array<number>>} a\n" +
        "* @return {number}\n" +
        "*/ var f = function(a) { return a[0]; };",
        "inconsistent return type\n" +
        "found   : Array<number>\n" +
        "required: number");
  }

  @Test
  public void testTemplatizedArray3() {
    testTypes("/** @param {!Array<number>} a\n" +
        "* @return {number}\n" +
        "*/ var f = function(a) { a[1] = 0; return a[0]; };");
  }

  @Test
  public void testTemplatizedArray4() {
    testTypes("/** @param {!Array<number>} a\n" +
        "*/ var f = function(a) { a[0] = 'a'; };",
        "assignment\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testTemplatizedArray5() {
    testTypes("/** @param {!Array<*>} a\n" +
        "*/ var f = function(a) { a[0] = 'a'; };");
  }

  @Test
  public void testTemplatizedArray6() {
    testTypes("/** @param {!Array<*>} a\n" +
        "* @return {string}\n" +
        "*/ var f = function(a) { return a[0]; };",
        "inconsistent return type\n" +
        "found   : *\n" +
        "required: string");
  }

  @Test
  public void testTemplatizedArray7() {
    testTypes("/** @param {?Array<number>} a\n" +
        "* @return {string}\n" +
        "*/ var f = function(a) { return a[0]; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testTemplatizedObject1() {
    testTypes("/** @param {!Object<number>} a\n" +
        "* @return {string}\n" +
        "*/ var f = function(a) { return a[0]; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testTemplatizedObjectOnWindow() {
    testTypesWithExtraExterns(
        "/** @constructor */ window.Object = Object;",
        lines(
            "/** @param {!window.Object<number>} a",
            " *  @return {string}",
            " */ var f = function(a) { return a[0]; };"),
        lines(
            "inconsistent return type",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testTemplatizedObjectOnWindow2() {
    testTypesWithExtraExterns(
        "/** @const */ window.Object = Object;",
        lines(
            "/** @param {!window.Object<number>} a",
            " *  @return {string}",
            " */ var f = function(a) { return a[0]; };"),
        lines(
            "inconsistent return type",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testTemplatizedObject2() {
    testTypes("/** @param {!Object<string,number>} a\n" +
        "* @return {string}\n" +
        "*/ var f = function(a) { return a['x']; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testTemplatizedObject3() {
    testTypes("/** @param {!Object<number,string>} a\n" +
        "* @return {string}\n" +
        "*/ var f = function(a) { return a['x']; };",
        "restricted index type\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testTemplatizedObject4() {
    testTypes("/** @enum {string} */ var E = {A: 'a', B: 'b'};\n" +
        "/** @param {!Object<E,string>} a\n" +
        "* @return {string}\n" +
        "*/ var f = function(a) { return a['x']; };",
        "restricted index type\n" +
        "found   : string\n" +
        "required: E<string>");
  }

  @Test
  public void testTemplatizedObject5() {
    testTypes("/** @constructor */ function F() {" +
        "  /** @type {Object<number, string>} */ this.numbers = {};" +
        "}" +
        "(new F()).numbers['ten'] = '10';",
        "restricted index type\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testUnionOfFunctionAndType() {
    testTypes("/** @type {null|(function(Number):void)} */ var a;" +
        "/** @type {(function(Number):void)|null} */ var b = null; a = b;");
  }

  @Test
  public void testOptionalParameterComparedToUndefined() {
    testTypes("/** @param  {Number} opt_a */function foo(opt_a)" +
        "{if (opt_a==undefined) var b = 3;}");
  }

  @Test
  public void testOptionalAllType() {
    testTypes("/** @param {*} opt_x */function f(opt_x) { return opt_x }\n" +
        "/** @type {*} */var y;\n" +
        "f(y);");
  }

  @Test
  public void testOptionalUnknownNamedType() {
    testTypes("/** @param {!T} opt_x\n@return {undefined} */\n" +
        "function f(opt_x) { return opt_x; }\n" +
        "/** @constructor */var T = function() {};",
        "inconsistent return type\n" +
        "found   : (T|undefined)\n" +
        "required: undefined");
  }

  @Test
  public void testOptionalArgFunctionParam() {
    testTypes("/** @param {function(number=)} a */" +
        "function f(a) {a()};");
  }

  @Test
  public void testOptionalArgFunctionParam2() {
    testTypes("/** @param {function(number=)} a */" +
        "function f(a) {a(3)};");
  }

  @Test
  public void testOptionalArgFunctionParam3() {
    testTypes("/** @param {function(number=)} a */" +
        "function f(a) {a(undefined)};");
  }

  @Test
  public void testOptionalArgFunctionParam4() {
    String expectedWarning = "Function a: called with 2 argument(s). " +
        "Function requires at least 0 argument(s) and no more than 1 " +
        "argument(s).";

    testTypes("/** @param {function(number=)} a */function f(a) {a(3,4)};",
              expectedWarning, false);
  }

  @Test
  public void testOptionalArgFunctionParamError() {
    String expectedWarning =
        "Bad type annotation. variable length argument must be last." + BAD_TYPE_WIKI_LINK;
    testTypes("/** @param {function(...number, number=)} a */" +
              "function f(a) {};", expectedWarning, false);
  }

  @Test
  public void testOptionalNullableArgFunctionParam() {
    testTypes("/** @param {function(?number=)} a */" +
              "function f(a) {a()};");
  }

  @Test
  public void testOptionalNullableArgFunctionParam2() {
    testTypes("/** @param {function(?number=)} a */" +
              "function f(a) {a(null)};");
  }

  @Test
  public void testOptionalNullableArgFunctionParam3() {
    testTypes("/** @param {function(?number=)} a */" +
              "function f(a) {a(3)};");
  }

  @Test
  public void testOptionalArgFunctionReturn() {
    testTypes("/** @return {function(number=)} */" +
              "function f() { return function(opt_x) { }; };" +
              "f()()");
  }

  @Test
  public void testOptionalArgFunctionReturn2() {
    testTypes("/** @return {function(Object=)} */" +
              "function f() { return function(opt_x) { }; };" +
              "f()({})");
  }

  @Test
  public void testBooleanType() {
    testTypes("/**@type {boolean} */var x = 1 < 2;");
  }

  @Test
  public void testBooleanReduction1() {
    testTypes("/**@type {string} */var x; x = null || \"a\";");
  }

  @Test
  public void testBooleanReduction2() {
    // It's important for the type system to recognize that in no case
    // can the boolean expression evaluate to a boolean value.
    testTypes("/** @param {string} s\n @return {string} */" +
        "(function(s) { return ((s == 'a') && s) || 'b'; })");
  }

  @Test
  public void testBooleanReduction3() {
    testTypes("/** @param {string} s\n @return {string?} */" +
        "(function(s) { return s && null && 3; })");
  }

  @Test
  public void testBooleanReduction4() {
    testTypes("/** @param {Object} x\n @return {Object} */" +
        "(function(x) { return null || x || null ; })");
  }

  @Test
  public void testBooleanReduction5() {
    testTypes("/**\n" +
        "* @param {Array|string} x\n" +
        "* @return {string?}\n" +
        "*/\n" +
        "var f = function(x) {\n" +
        "if (!x || typeof x == 'string') {\n" +
        "return x;\n" +
        "}\n" +
        "return null;\n" +
        "};");
  }

  @Test
  public void testBooleanReduction6() {
    testTypes("/**\n" +
        "* @param {Array|string|null} x\n" +
        "* @return {string?}\n" +
        "*/\n" +
        "var f = function(x) {\n" +
        "if (!(x && typeof x != 'string')) {\n" +
        "return x;\n" +
        "}\n" +
        "return null;\n" +
        "};");
  }

  @Test
  public void testBooleanReduction7() {
    testTypes("/** @constructor */var T = function() {};\n" +
        "/**\n" +
        "* @param {Array|T} x\n" +
        "* @return {null}\n" +
        "*/\n" +
        "var f = function(x) {\n" +
        "if (!x) {\n" +
        "return x;\n" +
        "}\n" +
        "return null;\n" +
        "};");
  }

  @Test
  public void testNullAnd() {
    testTypes("/** @type {null} */var x;\n" +
        "/** @type {number} */var r = x && x;",
        "initializing variable\n" +
        "found   : null\n" +
        "required: number");
  }

  @Test
  public void testNullOr() {
    testTypes("/** @type {null} */var x;\n" +
        "/** @type {number} */var r = x || x;",
        "initializing variable\n" +
        "found   : null\n" +
        "required: number");
  }

  @Test
  public void testBooleanPreservation1() {
    testTypes("/**@type {string} */var x = \"a\";" +
        "x = ((x == \"a\") && x) || x == \"b\";",
        "assignment\n" +
        "found   : (boolean|string)\n" +
        "required: string");
  }

  @Test
  public void testBooleanPreservation2() {
    testTypes("/**@type {string} */var x = \"a\"; x = (x == \"a\") || x;",
        "assignment\n" +
        "found   : (boolean|string)\n" +
        "required: string");
  }

  @Test
  public void testBooleanPreservation3() {
    testTypes("/** @param {Function?} x\n @return {boolean?} */" +
        "function f(x) { return x && x == \"a\"; }",
        "condition always evaluates to false\n" +
        "left : Function\n" +
        "right: string");
  }

  @Test
  public void testBooleanPreservation4() {
    testTypes("/** @param {Function?|boolean} x\n @return {boolean} */" +
        "function f(x) { return x && x == \"a\"; }",
        "inconsistent return type\n" +
        "found   : (boolean|null)\n" +
        "required: boolean");
  }

  @Test
  public void testWellKnownSymbolAccessTransitional1() {
    testTypesWithExtraExterns(
        lines(
            "/** @typedef {?} */ var symbol;",
            "/** @type {symbol} */ Symbol.somethingNew;"),
        lines(
            "/**",
            " * @param {Array<string>} x",
            " */",
            "function f(x) {",
            "  const iter = x[Symbol.somethingNew]();",
            "}"
        ));
  }

  @Test
  public void testWellKnownSymbolAccess1() {
    testTypesWithCommonExterns(
        lines(
            "/**",
            " * @param {Array<string>} x",
            " */",
            "function f(x) {",
            "  const iter = x[Symbol.iterator]();",
            "}"
        ));
  }

  @Test
  public void testWellKnownSymbolAccess2() {
    testTypesWithCommonExterns(
        lines(
            "/**",
            " * @param {IObject<string, number>} x",
            " */",
            "function f(x) {",
            "  const iter = x[Symbol.iterator]();",
            "}"
        ));
  }

  @Test
  public void testSymbolComparison1() {
    testTypes(
        lines(
            "/**",
            " * @param {symbol} x",
            " * @param {symbol} y",
            "*/",
            "function f(x, y) { return x === y; }"));
  }

  @Test
  public void testSymbolComparison2() {
    testTypes(
        lines(
            "/**",
            " * @param {symbol} x",
            " * @param {symbol} y",
            "*/",
            "function f(x, y) { return x == y; }"));
  }

  @Test
  public void testSymbolComparison3() {
    testTypes(
        lines(
            "/**",
            " * @param {symbol} x", // primitive
            " * @param {!Symbol} y", // object
            "*/",
            "function f(x, y) { return x == y; }"));
  }

  @Test
  public void testSymbolComparison4() {
    testTypes(
        lines(
            "/**",
            " * @param {symbol} x", // primitive
            " * @param {!Symbol} y", // object
            "*/",
            "function f(x, y) { return x === y; }"),
        "condition always evaluates to false\n"
            + "left : symbol\n"
            + "right: Symbol");
  }

  @Test
  public void testSymbolComparison5() {
    testTypes(
        lines(
            "/**",
            " * @param {symbol} x", // primitive
            " * @param {(!Symbol|null)} y", // object
            "*/",
            "function f(x, y) { return x == y; }"));
  }

  @Test
  public void testSymbolComparison6() {
    testTypes(
        lines(
            "/**",
            " * @param {symbol} x", // primitive
            " * @param {(!Symbol|null)} y", // object
            "*/",
            "function f(x, y) { return x === y; }"),
        "condition always evaluates to false\n"
            + "left : symbol\n"
            + "right: (Symbol|null)");
  }

  @Test
  public void testSymbolComparison7() {
    testTypes(
        lines(
            "/**",
            " * @param {symbol} x",
            " * @param {*} y",
            "*/",
            "function f(x, y) { return x == y; }"));
  }

  @Test
  public void testSymbolComparison8() {
    testTypes(
        lines(
            "/**",
            " * @param {symbol} x",
            " * @param {*} y",
            "*/",
            "function f(x, y) { return x === y; }"));
  }

  @Test
  public void testSymbolComparison9() {
    testTypes(
        lines(
            "/** @enum {symbol} */ var E = {A:Symbol()};",
            "/**",
            " * @param {symbol} x",
            " * @param {E} y",
            "*/",
            "function f(x, y) { return x == y; }"));
  }

  @Test
  public void testSymbolComparison10() {
    testTypes(
        lines(
            "/** @enum {symbol} */ var E = {A:Symbol()};",
            "/**",
            " * @param {symbol} x",
            " * @param {E} y",
            "*/",
            "function f(x, y) { return x === y; }"));
  }

  @Test
  public void testSymbolComparison11() {
    testTypes(
        lines(
            "/** @enum {!Symbol} */ var E = {A:/** @type {!Symbol} */ (Object(Symbol()))};",
            "/**",
            " * @param {symbol} x",
            " * @param {E} y",
            "*/",
            "function f(x, y) { return x == y; }"));
  }

  @Test
  public void testSymbolComparison12() {
    testTypes(
        lines(
            "/** @enum {!Symbol} */ var E = {A:/** @type {!Symbol} */ (Object(Symbol()))};",
            "/**",
            " * @param {symbol} x",
            " * @param {E} y",
            "*/",
            "function f(x, y) { return x === y; }"),
        "condition always evaluates to false\n"
            + "left : symbol\n"
            + "right: E<Symbol>");
  }

  @Test
  public void testSymbolComparison13() {
    testTypes(
        lines(
            "/**",
            " * @param {symbol} x",
            " * @param {!Object} y",
            "*/",
            "function f(x, y) { return x == y; }"));
  }

  @Test
  public void testSymbolComparison14() {
    testTypes(
        lines(
            "/**",
            " * @param {symbol} x",
            " * @param {!Object} y",
            "*/",
            "function f(x, y) { return x === y; }"),
        "condition always evaluates to false\n"
            + "left : symbol\n"
            + "right: Object");
  }

  @Test
  public void testTypeOfReduction1() {
    testTypes("/** @param {string|number} x\n @return {string} */ " +
        "function f(x) { return typeof x == 'number' ? String(x) : x; }");
  }

  @Test
  public void testTypeOfReduction2() {
    testTypes("/** @param {string|number} x\n @return {string} */ " +
        "function f(x) { return typeof x != 'string' ? String(x) : x; }");
  }

  @Test
  public void testTypeOfReduction3() {
    testTypes("/** @param {number|null} x\n @return {number} */ " +
        "function f(x) { return typeof x == 'object' ? 1 : x; }");
  }

  @Test
  public void testTypeOfReduction4() {
    testTypes("/** @param {Object|undefined} x\n @return {Object} */ " +
        "function f(x) { return typeof x == 'undefined' ? {} : x; }");
  }

  @Test
  public void testTypeOfReduction5() {
    testTypes("/** @enum {string} */ var E = {A: 'a', B: 'b'};\n" +
        "/** @param {!E|number} x\n @return {string} */ " +
        "function f(x) { return typeof x != 'number' ? x : 'a'; }");
  }

  @Test
  public void testTypeOfReduction6() {
    testTypesWithExterns(
        new TestExternsBuilder().addString().build(),
        lines(
            "/**",
            " * @param {number|string} x",
            " * @return {string}",
            " */",
            "function f(x) {",
            "  return typeof x == 'string' && x.length == 3 ? x : 'a';",
            "}"));
  }

  @Test
  public void testTypeOfReduction7() {
    testTypes("/** @return {string} */var f = function(x) { " +
        "return typeof x == 'number' ? x : 'a'; }",
        "inconsistent return type\n" +
        "found   : (number|string)\n" +
        "required: string");
  }

  @Test
  public void testTypeOfReduction8() {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @param {number|string} x\n@return {string} */\n" +
        "function f(x) {\n" +
        "return goog.isString(x) && x.length == 3 ? x : 'a';\n" +
        "}", null);
  }

  @Test
  public void testTypeOfReduction9() {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @param {!Array|string} x\n@return {string} */\n" +
        "function f(x) {\n" +
        "return goog.isArray(x) ? 'a' : x;\n" +
        "}", null);
  }

  @Test
  public void testTypeOfReduction10() {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @param {Array|string} x\n@return {Array} */\n" +
        "function f(x) {\n" +
        "return goog.isArray(x) ? x : [];\n" +
        "}", null);
  }

  @Test
  public void testTypeOfReduction11() {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @param {Array|string} x\n@return {Array} */\n" +
        "function f(x) {\n" +
        "return goog.isObject(x) ? x : [];\n" +
        "}", null);
  }

  @Test
  public void testTypeOfReduction12() {
    testTypes("/** @enum {string} */ var E = {A: 'a', B: 'b'};\n" +
        "/** @param {E|Array} x\n @return {Array} */ " +
        "function f(x) { return typeof x == 'object' ? x : []; }");
  }

  @Test
  public void testTypeOfReduction13() {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @enum {string} */ var E = {A: 'a', B: 'b'};\n" +
        "/** @param {E|Array} x\n@return {Array} */ " +
        "function f(x) { return goog.isObject(x) ? x : []; }", null);
  }

  @Test
  public void testTypeOfReduction14() {
    // Don't do type inference on GETELEMs.
    testClosureTypes(
        CLOSURE_DEFS +
        "function f(x) { " +
        "  return goog.isString(arguments[0]) ? arguments[0] : 0;" +
        "}", null);
  }

  @Test
  public void testTypeOfReduction15() {
    // Don't do type inference on GETELEMs.
    testClosureTypes(
        CLOSURE_DEFS +
        "function f(x) { " +
        "  return typeof arguments[0] == 'string' ? arguments[0] : 0;" +
        "}", null);
  }

  @Test
  public void testTypeOfReduction16() {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @interface */ function I() {}\n" +
        "/**\n" +
        " * @param {*} x\n" +
        " * @return {I}\n" +
        " */\n" +
        "function f(x) { " +
        "  if(goog.isObject(x)) {" +
        "    return /** @type {I} */(x);" +
        "  }" +
        "  return null;" +
        "}", null);
  }

  @Test
  public void testQualifiedNameReduction1() {
    testTypes("var x = {}; /** @type {string?} */ x.a = 'a';\n" +
        "/** @return {string} */ var f = function() {\n" +
        "return x.a ? x.a : 'a'; }");
  }

  @Test
  public void testQualifiedNameReduction2() {
    testTypes("/** @param {string?} a\n@constructor */ var T = " +
        "function(a) {this.a = a};\n" +
        "/** @return {string} */ T.prototype.f = function() {\n" +
        "return this.a ? this.a : 'a'; }");
  }

  @Test
  public void testQualifiedNameReduction3() {
    testTypes("/** @param {string|Array} a\n@constructor */ var T = " +
        "function(a) {this.a = a};\n" +
        "/** @return {string} */ T.prototype.f = function() {\n" +
        "return typeof this.a == 'string' ? this.a : 'a'; }");
  }

  @Test
  public void testQualifiedNameReduction4() {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @param {string|Array} a\n@constructor */ var T = " +
        "function(a) {this.a = a};\n" +
        "/** @return {string} */ T.prototype.f = function() {\n" +
        "return goog.isString(this.a) ? this.a : 'a'; }", null);
  }

  @Test
  public void testQualifiedNameReduction5a() {
    testTypes("var x = {/** @type {string} */ a:'b' };\n" +
        "/** @return {string} */ var f = function() {\n" +
        "return x.a; }");
  }

  @Test
  public void testQualifiedNameReduction5b() {
    testTypes(
        "var x = {/** @type {number} */ a:12 };\n" +
        "/** @return {string} */\n" +
        "var f = function() {\n" +
        "  return x.a;\n" +
        "}",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testQualifiedNameReduction5c() {
    testTypes(
        "/** @return {string} */ var f = function() {\n" +
        "var x = {/** @type {number} */ a:0 };\n" +
        "return (x.a) ? (x.a) : 'a'; }",
        "inconsistent return type\n" +
        "found   : (number|string)\n" +
        "required: string");
  }

  @Test
  public void testQualifiedNameReduction6() {
    testTypes(
        "/** @return {string} */ var f = function() {\n" +
        "var x = {/** @return {string?} */ get a() {return 'a'}};\n" +
        "return x.a ? x.a : 'a'; }");
  }

  @Test
  public void testQualifiedNameReduction7() {
    testTypes(
        "/** @return {string} */ var f = function() {\n" +
        "var x = {/** @return {number} */ get a() {return 12}};\n" +
        "return x.a; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testQualifiedNameReduction7a() {
    // It would be nice to find a way to make this an error.
    testTypes(
        "/** @return {string} */ var f = function() {\n" +
        "var x = {get a() {return 12}};\n" +
        "return x.a; }");
  }

  @Test
  public void testQualifiedNameReduction8() {
    testTypes(
        "/** @return {string} */ var f = function() {\n" +
        "var x = {get a() {return 'a'}};\n" +
        "return x.a ? x.a : 'a'; }");
  }

  @Test
  public void testQualifiedNameReduction9() {
    testTypes(
        "/** @return {string} */ var f = function() {\n" +
        "var x = { /** @param {string} b */ set a(b) {}};\n" +
        "return x.a ? x.a : 'a'; }");
  }

  @Test
  public void testQualifiedNameReduction10() {
    // TODO(johnlenz): separate setter property types from getter property
    // types.
    testTypes(
        "/** @return {string} */ var f = function() {\n" +
        "var x = { /** @param {number} b */ set a(b) {}};\n" +
        "return x.a ? x.a : 'a'; }",
        "inconsistent return type\n" +
        "found   : (number|string)\n" +
        "required: string");
  }

  @Test
  public void testUnknownsDontOverrideDeclaredTypesInLocalScope1() {
    testTypes(
        "/** @constructor */ var C = function() {\n"
        + "  /** @type {string} */ this.a = 'str'};\n"
        + "/** @param {?} a\n @return {number} */\n"
        + "C.prototype.f = function(a) {\n"
        + "  this.a = a;\n"
        + "  return this.a;\n"
        + "}\n",

        "inconsistent return type\n"
        + "found   : string\n"
        + "required: number");
  }

  @Test
  public void testUnknownsDontOverrideDeclaredTypesInLocalScope2() {
    testTypes(
        "/** @constructor */ var C = function() {\n"
        + "  /** @type {string} */ this.a = 'str';\n"
        + "};\n"
        + "/** @type {C} */ var x = new C();"
        + "/** @param {?} a\n @return {number} */\n"
        + "C.prototype.f = function(a) {\n"
        + "  x.a = a;\n"
        + "  return x.a;\n"
        + "}\n",

        "inconsistent return type\n"
        + "found   : string\n"
        + "required: number");
  }

  @Test
  public void testObjLitDef1a() {
    testTypes(
        "var x = {/** @type {number} */ a:12 };\n" +
        "x.a = 'a';",
        "assignment to property a of x\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testObjLitDef1b() {
    testTypes(
        "function f(){" +
          "var x = {/** @type {number} */ a:12 };\n" +
          "x.a = 'a';" +
        "};\n" +
        "f();",
        "assignment to property a of x\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testObjLitDef2a() {
    testTypes(
        "var x = {/** @param {number} b */ set a(b){} };\n" +
        "x.a = 'a';",
        "assignment to property a of x\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testObjLitDef2b() {
    testTypes(
        "function f(){" +
          "var x = {/** @param {number} b */ set a(b){} };\n" +
          "x.a = 'a';" +
        "};\n" +
        "f();",
        "assignment to property a of x\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testObjLitDef3a() {
    testTypes(
        "/** @type {string} */ var y;\n" +
        "var x = {/** @return {number} */ get a(){} };\n" +
        "y = x.a;",
        "assignment\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testObjLitDef3b() {
    testTypes(
      "/** @type {string} */ var y;\n" +
        "function f(){" +
          "var x = {/** @return {number} */ get a(){} };\n" +
          "y = x.a;" +
        "};\n" +
        "f();",
        "assignment\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testObjLitDef4() {
    testTypes(
        "var x = {" +
          "/** @return {number} */ a:12 };\n",
          "assignment to property a of {a: function(): number}\n" +
          "found   : number\n" +
          "required: function(): number");
  }

  @Test
  public void testObjLitDef5() {
    testTypes(
        "var x = {};\n" +
        "/** @return {number} */ x.a = 12;\n",
        "assignment to property a of x\n" +
        "found   : number\n" +
        "required: function(): number");
  }

  @Test
  public void testObjLitDef6() {
    testTypes("var lit = /** @struct */ { 'x': 1 };",
        "Illegal key, the object literal is a struct");
  }

  @Test
  public void testObjLitDef7() {
    testTypes("var lit = /** @dict */ { x: 1 };",
        "Illegal key, the object literal is a dict");
  }

  @Test
  public void testInstanceOfReduction1() {
    testTypes("/** @constructor */ var T = function() {};\n" +
        "/** @param {T|string} x\n@return {T} */\n" +
        "var f = function(x) {\n" +
        "if (x instanceof T) { return x; } else { return new T(); }\n" +
        "};");
  }

  @Test
  public void testInstanceOfReduction2() {
    testTypes("/** @constructor */ var T = function() {};\n" +
        "/** @param {!T|string} x\n@return {string} */\n" +
        "var f = function(x) {\n" +
        "if (x instanceof T) { return ''; } else { return x; }\n" +
        "};");
  }

  @Test
  public void testUndeclaredGlobalProperty1() {
    testTypes("/** @const */ var x = {}; x.y = null;" +
        "function f(a) { x.y = a; }" +
        "/** @param {string} a */ function g(a) { }" +
        "function h() { g(x.y); }");
  }

  @Test
  public void testUndeclaredGlobalProperty2() {
    testTypes("/** @const */ var x = {}; x.y = null;" +
        "function f() { x.y = 3; }" +
        "/** @param {string} a */ function g(a) { }" +
        "function h() { g(x.y); }",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : (null|number)\n" +
        "required: string");
  }

  @Test
  public void testLocallyInferredGlobalProperty1() {
    // We used to have a bug where x.y.z leaked from f into h.
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** @type {number} */ F.prototype.z;" +
        "/** @const */ var x = {}; /** @type {F} */ x.y;" +
        "function f() { x.y.z = 'abc'; }" +
        "/** @param {number} x */ function g(x) {}" +
        "function h() { g(x.y.z); }",
        "assignment to property z of F\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testPropertyInferredPropagation() {
    // Checking sloppy property check behavior
    disableStrictMissingPropertyChecks();
    testTypes(
        lines(
            "/** @return {Object} */",
            "function f() { return {}; }",
            "function g() { var x = f(); if (x.p) x.a = 'a'; else x.a = 'b'; }",
            "function h() { var x = f(); x.a = false; }"));
  }

  @Test
  public void testPropertyInference1() {
    testTypes(
        "/** @constructor */ function F() { this.x_ = true; }" +
        "/** @return {string} */" +
        "F.prototype.bar = function() { if (this.x_) return this.x_; };",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: string");
  }

  @Test
  public void testPropertyInference2() {
    testTypes(
        "/** @constructor */ function F() { this.x_ = true; }" +
        "F.prototype.baz = function() { this.x_ = null; };" +
        "/** @return {string} */" +
        "F.prototype.bar = function() { if (this.x_) return this.x_; };",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: string");
  }

  @Test
  public void testPropertyInference3() {
    testTypes(
        "/** @constructor */ function F() { this.x_ = true; }" +
        "F.prototype.baz = function() { this.x_ = 3; };" +
        "/** @return {string} */" +
        "F.prototype.bar = function() { if (this.x_) return this.x_; };",
        "inconsistent return type\n" +
        "found   : (boolean|number)\n" +
        "required: string");
  }

  @Test
  public void testPropertyInference4() {
    testTypes(
        "/** @constructor */ function F() { }" +
        "F.prototype.x_ = 3;" +
        "/** @return {string} */" +
        "F.prototype.bar = function() { if (this.x_) return this.x_; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testPropertyInference5() {
    disableStrictMissingPropertyChecks();
    // "x_" is a known property of an unknown type.
    testTypes(
        "/** @constructor */ function F() { }" +
        "F.prototype.baz = function() { this.x_ = 3; };" +
        "/** @return {string} */" +
        "F.prototype.bar = function() { if (this.x_) return this.x_; };");
  }

  @Test
  public void testPropertyInference6() {
    // "x_" is a known property of an unknown type.
    testTypes(
        lines(
            "/** @constructor */ function F() { }",
            "(new F).x_ = 3;",
            "/** @return {string} */",
            "F.prototype.bar = function() { return this.x_; };"),
        ImmutableList.of(
            "Property x_ never defined on F",   // definition
            "Property x_ never defined on F")); // reference
  }

  @Test
  public void testPropertyInference7() {
    testTypes(
        "/** @constructor */ function F() { this.x_ = true; }" +
        "(new F).x_ = 3;" +
        "/** @return {string} */" +
        "F.prototype.bar = function() { return this.x_; };",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: string");
  }

  @Test
  public void testPropertyInference8() {
    testTypes(
        "/** @constructor */ function F() { " +
        "  /** @type {string} */ this.x_ = 'x';" +
        "}" +
        "(new F).x_ = 3;" +
        "/** @return {string} */" +
        "F.prototype.bar = function() { return this.x_; };",
        "assignment to property x_ of F\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testPropertyInference9() {
    testTypes(
        "/** @constructor */ function A() {}" +
        "/** @return {function(): ?} */ function f() { " +
        "  return function() {};" +
        "}" +
        "var g = f();" +
        "/** @type {number} */ g.prototype.bar_ = null;",
        "assignment\n" +
        "found   : null\n" +
        "required: number");
  }

  @Test
  public void testPropertyInference10() {
    // NOTE(nicksantos): There used to be a bug where a property
    // on the prototype of one structural function would leak onto
    // the prototype of other variables with the same structural
    // function type.
    testTypes(
        "/** @constructor */ function A() {}" +
        "/** @return {function(): ?} */ function f() { " +
        "  return function() {};" +
        "}" +
        "var g = f();" +
        "/** @type {number} */ g.prototype.bar_ = 1;" +
        "var h = f();" +
        "/** @type {string} */ h.prototype.bar_ = 1;",
        "assignment\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testNoPersistentTypeInferenceForObjectProperties() {
    disableStrictMissingPropertyChecks();
    testTypes("/** @param {Object} o\n@param {string} x */\n" +
        "function s1(o,x) { o.x = x; }\n" +
        "/** @param {Object} o\n@return {string} */\n" +
        "function g1(o) { return typeof o.x == 'undefined' ? '' : o.x; }\n" +
        "/** @param {Object} o\n@param {number} x */\n" +
        "function s2(o,x) { o.x = x; }\n" +
        "/** @param {Object} o\n@return {number} */\n" +
        "function g2(o) { return typeof o.x == 'undefined' ? 0 : o.x; }");
  }

  @Test
  public void testNoPersistentTypeInferenceForFunctionProperties() {
    disableStrictMissingPropertyChecks();
    testTypes("/** @param {Function} o\n@param {string} x */\n" +
        "function s1(o,x) { o.x = x; }\n" +
        "/** @param {Function} o\n@return {string} */\n" +
        "function g1(o) { return typeof o.x == 'undefined' ? '' : o.x; }\n" +
        "/** @param {Function} o\n@param {number} x */\n" +
        "function s2(o,x) { o.x = x; }\n" +
        "/** @param {Function} o\n@return {number} */\n" +
        "function g2(o) { return typeof o.x == 'undefined' ? 0 : o.x; }");
  }

  @Test
  public void testStrictPropertiesOnFunctions2() {
    testTypes(
        lines(
            "/** @param {Function} o\n @param {string} x */",
            "function s1(o,x) { o.x = x; }"),
        "Property x never defined on Function");
  }

  @Test
  public void testStrictPropertiesOnEnum1() {
    testTypesWithExterns(
        new TestExternsBuilder().addString().build(),
        lines(
            "/** @enum {string} */ var E = {S:'s'};",
            "/** @param {E} e\n @return {string}  */",
            "function s1(e) { return e.slice(1); }"));
  }

  @Test
  public void testStrictPropertiesOnEnum2() {
    testTypesWithExterns(
        new TestExternsBuilder().addString().build(),
        lines(
            "/** @enum {?string} */ var E = {S:'s'};",
            "/** @param {E} e\n @return {string}  */",
            "function s1(e) { return e.slice(1); }"));
  }

  @Test
  public void testStrictPropertiesOnEnum3() {
    testTypesWithExterns(
        new TestExternsBuilder().addString().build(),
        lines(
            "/** @enum {string} */ var E = {S:'s'};",
            "/** @param {?E} e\n @return {string}  */",
            "function s1(e) { return e.slice(1); }"));
  }

  @Test
  public void testStrictPropertiesOnEnum4() {
    testTypesWithExterns(
        new TestExternsBuilder().addString().build(),
        lines(
            "/** @enum {?string} */ var E = {S:'s'};",
            "/** @param {?E} e\n @return {string}  */",
            "function s1(e) { return e.slice(1); }"));
  }

  @Test
  public void testDestructuring() {
    testTypes(
        lines(
            "/** @param {{id:(number|undefined)}=} o */",
            "function f(o = {}) {",
            "   const {id} = o;",
            "}"));
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope1a() {
    disableStrictMissingPropertyChecks();
    testTypes(
        lines(
            "/** @param {!Object} o",
            " * @return {string}",
            " */",
            "function f(o) {",
            "  o.x = 1;",
            "  return o.x;",
            "}"),
        lines(
            "inconsistent return type",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope1b() {
    // With strict missing properties assigning a unknown property after type
    // definition causes a warning.
    testTypes(
        lines(
            "/** @param {!Object} o",
            " * @return {string}",
            " */",
            "function f(o) {",
            "  o.x = 1;",
            "}"),
        "Property x never defined on Object");
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope2a() {
    // This test is specifically checking loose property check behavior.
    disableStrictMissingPropertyChecks();

    testTypes(
        lines(
            "/**",
            "  @param {!Object} o",
            "  @param {number?} x",
            "  @return {string}",
            " */",
            "function f(o, x) {",
            "  o.x = 'a';",
            "  if (x) {o.x = x;}",
            "  return o.x;",
            "}"),
        lines(
            "inconsistent return type",
            "found   : (number|string)",
            "required: string"));
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope2b() {
    testTypes(
        lines(
            "/**",
            "  @param {!Object} o",
            "  @param {number?} x",
            " */",
            "function f(o, x) {",
            "  o.x = 'a';",
            "}"),
        "Property x never defined on Object");
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope2c() {
    testTypes(
        lines(
            "/**",
            "  @param {!Object} o",
            "  @param {number?} x",
            " */",
            "function f(o, x) {",
            "  if (x) { o.x = x; }",
            "}"),
        "Property x never defined on Object");
  }

  @Test
  public void testObjectPropertyTypeInferredInLocalScope3() {
    disableStrictMissingPropertyChecks();

    // inferrence of undeclared properties
    testTypes(
        "/**@param {!Object} o\n@param {number?} x\n@return {string}*/"
            + "function f(o, x) { if (x) {o.x = x;} else {o.x = 'a';}\nreturn o.x; }",
        "inconsistent return type\n"
            + "found   : (number|string)\n"
            + "required: string");
  }

  @Test
  public void testMissingPropertyOfUnion1() {
    testTypesWithExterns(
        "",
        lines(
            "/**",
            "  @param {number|string} obj",
            "  @return {?}",
            " */",
            "function f(obj, x) {",
            "  return obj.foo;",
            "}"),
        "Property foo never defined on (Number|String)",
        false);
  }

  @Test
  public void testMissingPropertyOfUnion2() {
    testTypes(
        lines(
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
            "}"),
        "Property toString never defined on *");  // ?
  }

  @Test
  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty1() {
    testTypes("/** @constructor */var T = function() { this.x = ''; };\n" +
        "/** @type {number} */ T.prototype.x = 0;",
        "assignment to property x of T\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty2() {
    testTypes("/** @constructor */var T = function() { this.x = ''; };\n" +
        "/** @type {number} */ T.prototype.x;",
        "assignment to property x of T\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty3() {
    disableStrictMissingPropertyChecks();
    testTypes("/** @type {Object} */ var n = {};\n" +
        "/** @constructor */ n.T = function() { this.x = ''; };\n" +
        "/** @type {number} */ n.T.prototype.x = 0;",
        "assignment to property x of n.T\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty4() {
    testTypes("var n = {};\n" +
        "/** @constructor */ n.T = function() { this.x = ''; };\n" +
        "/** @type {number} */ n.T.prototype.x = 0;",
        "assignment to property x of n.T\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testAbstractMethodInAbstractClass() {
    testTypes(
        lines(
            "/** @abstract @constructor */ var C = function() {};",
            "/** @abstract */ C.prototype.foo = function() {};"));
  }

  @Test
  public void testAbstractMethodInConcreteClass() {
    testTypes(
        lines(
            "/** @constructor */ var C = function() {};",
            "/** @abstract */ C.prototype.foo = function() {};"),
        "Abstract methods can only appear in abstract classes. Please declare the class as "
            + "@abstract");
  }

  @Test
  public void testAbstractMethodInConcreteClassExtendingAbstractClass() {
    testTypes(
        lines(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @abstract */ B.prototype.foo = function() {};"),
        "Abstract methods can only appear in abstract classes. Please declare the class as "
            + "@abstract");
  }

  @Test
  public void testConcreteMethodOverridingAbstractMethod() {
    testTypes(
        lines(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() {};"));
  }

  @Test
  public void testConcreteMethodInAbstractClass1() {
    testTypes(
        lines(
            "/** @abstract @constructor */ var A = function() {};",
            "A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};"));
  }

  @Test
  public void testConcreteMethodInAbstractClass2() {
    // Currently goog.abstractMethod are not considered abstract, so no warning is given when a
    // concrete subclass fails to implement it.
    testTypes(
        lines(
            CLOSURE_DEFS,
            "/** @abstract @constructor */ var A = function() {};",
            "A.prototype.foo = goog.abstractMethod;",
            "/** @constructor @extends {A} */ var B = function() {};"));
  }

  @Test
  public void testAbstractMethodInInterface() {
    // TODO(moz): There's no need to tag methods with @abstract in interfaces, maybe give a warning
    // on this.
    testTypes(
        lines(
            "/** @interface */ var I = function() {};",
            "/** @abstract */ I.prototype.foo = function() {};"));
  }

  @Test
  public void testAbstractMethodNotImplemented1() {
    testTypes(
        lines(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};"),
        "property foo on abstract class A is not implemented by type B");
  }

  @Test
  public void testAbstractMethodNotImplemented2() {
    testTypes(
        lines(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @abstract */ A.prototype.bar = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() {};"),
        "property bar on abstract class A is not implemented by type B");
  }

  @Test
  public void testAbstractMethodNotImplemented3() {
    testTypes(
        lines(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @abstract @constructor @extends {A} */ var B = function() {};",
            "/** @abstract @override */ B.prototype.foo = function() {};",
            "/** @constructor @extends {B} */ var C = function() {};"),
        "property foo on abstract class B is not implemented by type C");
  }

  @Test
  public void testAbstractMethodNotImplemented4() {
    testTypes(
        lines(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @abstract @constructor @extends {A} */ var B = function() {};",
            "/** @constructor @extends {B} */ var C = function() {};"),
        "property foo on abstract class A is not implemented by type C");
  }

  @Test
  public void testAbstractMethodNotImplemented5() {
    testTypes(
        lines(
            "/** @interface */ var I = function() {};",
            "I.prototype.foo = function() {};",
            "/** @abstract @constructor @implements {I} */ var A = function() {};",
            "/** @abstract @override */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};"),
        "property foo on abstract class A is not implemented by type B");
  }

  @Test
  public void testAbstractMethodNotImplemented6() {
    testTypes(
        lines(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override @type {number} */ B.prototype.foo;"),
        "property foo on abstract class A is not implemented by type B");
  }

  @Test
  public void testAbstractMethodImplemented1() {
    testTypes(
        lines(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @abstract */ A.prototype.bar = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() {};",
            "/** @override */ B.prototype.bar = function() {};",
            "/** @constructor @extends {B} */ var C = function() {};"));
  }

  @Test
  public void testAbstractMethodImplemented2() {
    testTypes(
        lines(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @abstract */ A.prototype.bar = function() {};",
            "/** @abstract @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() {};",
            "/** @constructor @extends {B} */ var C = function() {};",
            "/** @override */ C.prototype.bar = function() {};"));
  }

  @Test
  public void testPropertyUsedBeforeDefinition1() {
    testTypes("/** @constructor */ var T = function() {};\n" +
        "/** @return {string} */" +
        "T.prototype.f = function() { return this.g(); };\n" +
        "/** @return {number} */ T.prototype.g = function() { return 1; };\n",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testPropertyUsedBeforeDefinition2() {
    testTypes("var n = {};\n" +
        "/** @constructor */ n.T = function() {};\n" +
        "/** @return {string} */" +
        "n.T.prototype.f = function() { return this.g(); };\n" +
        "/** @return {number} */ n.T.prototype.g = function() { return 1; };\n",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testAdd1() {
    testTypes("/**@return {void}*/function foo(){var a = 'abc'+foo();}");
  }

  @Test
  public void testAdd2() {
    testTypes("/**@return {void}*/function foo(){var a = foo()+4;}");
  }

  @Test
  public void testAdd3() {
    testTypes("/** @type {string} */ var a = 'a';" +
        "/** @type {string} */ var b = 'b';" +
        "/** @type {string} */ var c = a + b;");
  }

  @Test
  public void testAdd4() {
    testTypes("/** @type {number} */ var a = 5;" +
        "/** @type {string} */ var b = 'b';" +
        "/** @type {string} */ var c = a + b;");
  }

  @Test
  public void testAdd5() {
    testTypes("/** @type {string} */ var a = 'a';" +
        "/** @type {number} */ var b = 5;" +
        "/** @type {string} */ var c = a + b;");
  }

  @Test
  public void testAdd6() {
    testTypes("/** @type {number} */ var a = 5;" +
        "/** @type {number} */ var b = 5;" +
        "/** @type {number} */ var c = a + b;");
  }

  @Test
  public void testAdd7() {
    testTypes("/** @type {number} */ var a = 5;" +
        "/** @type {string} */ var b = 'b';" +
        "/** @type {number} */ var c = a + b;",
        "initializing variable\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testAdd8() {
    testTypes("/** @type {string} */ var a = 'a';" +
        "/** @type {number} */ var b = 5;" +
        "/** @type {number} */ var c = a + b;",
        "initializing variable\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testAdd9() {
    testTypes("/** @type {number} */ var a = 5;" +
        "/** @type {number} */ var b = 5;" +
        "/** @type {string} */ var c = a + b;",
        "initializing variable\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testAdd10() {
    // d.e.f will have unknown type.
    testTypes(
        suppressMissingProperty("e", "f") +
        "/** @type {number} */ var a = 5;" +
        "/** @type {string} */ var c = a + d.e.f;");
  }

  @Test
  public void testAdd11() {
    // d.e.f will have unknown type.
    testTypes(
        suppressMissingProperty("e", "f") +
        "/** @type {number} */ var a = 5;" +
        "/** @type {number} */ var c = a + d.e.f;");
  }

  @Test
  public void testAdd12() {
    testTypes("/** @return {(number|string)} */ function a() { return 5; }" +
        "/** @type {number} */ var b = 5;" +
        "/** @type {boolean} */ var c = a() + b;",
        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: boolean");
  }

  @Test
  public void testAdd13() {
    testTypes("/** @type {number} */ var a = 5;" +
        "/** @return {(number|string)} */ function b() { return 5; }" +
        "/** @type {boolean} */ var c = a + b();",
        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: boolean");
  }

  @Test
  public void testAdd14() {
    testTypes("/** @type {(null|string)} */ var a = unknown;" +
        "/** @type {number} */ var b = 5;" +
        "/** @type {boolean} */ var c = a + b;",
        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: boolean");
  }

  @Test
  public void testAdd15() {
    testTypes("/** @type {number} */ var a = 5;" +
        "/** @return {(number|string)} */ function b() { return 5; }" +
        "/** @type {boolean} */ var c = a + b();",
        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: boolean");
  }

  @Test
  public void testAdd16() {
    testTypes("/** @type {(undefined|string)} */ var a = unknown;" +
        "/** @type {number} */ var b = 5;" +
        "/** @type {boolean} */ var c = a + b;",
        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: boolean");
  }

  @Test
  public void testAdd17() {
    testTypes("/** @type {number} */ var a = 5;" +
        "/** @type {(undefined|string)} */ var b = unknown;" +
        "/** @type {boolean} */ var c = a + b;",
        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: boolean");
  }

  @Test
  public void testAdd18() {
    testTypes("function f() {};" +
        "/** @type {string} */ var a = 'a';" +
        "/** @type {number} */ var c = a + f();",
        "initializing variable\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testAdd19() {
    testTypes("/** @param {number} opt_x\n@param {number} opt_y\n" +
        "@return {number} */ function f(opt_x, opt_y) {" +
        "return opt_x + opt_y;}");
  }

  @Test
  public void testAdd20() {
    testTypes("/** @param {!Number} opt_x\n@param {!Number} opt_y\n" +
        "@return {number} */ function f(opt_x, opt_y) {" +
        "return opt_x + opt_y;}");
  }

  @Test
  public void testAdd21() {
    testTypes("/** @param {Number|Boolean} opt_x\n" +
        "@param {number|boolean} opt_y\n" +
        "@return {number} */ function f(opt_x, opt_y) {" +
        "return opt_x + opt_y;}");
  }

  @Test
  public void testNumericComparison1() {
    testTypes("/**@param {number} a*/ function f(a) {return a < 3;}");
  }

  @Test
  public void testNumericComparison2() {
    testTypes("/**@param {!Object} a*/ function f(a) {return a < 3;}",
        "left side of numeric comparison\n" +
        "found   : Object\n" +
        "required: number");
  }

  @Test
  public void testNumericComparison3() {
    compiler.getOptions().setWarningLevel(
        DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    testTypes("/**@param {string} a*/ function f(a) {return a < 3;}");
  }

  @Test
  public void testNumericComparison4() {
    compiler.getOptions().setWarningLevel(
        DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    testTypes("/**@param {(number|undefined)} a*/ " +
              "function f(a) {return a < 3;}");
  }

  @Test
  public void testNumericComparison5() {
    testTypes("/**@param {*} a*/ function f(a) {return a < 3;}",
        "left side of numeric comparison\n" +
        "found   : *\n" +
        "required: number");
  }

  @Test
  public void testNumericComparison6() {
    testTypes("/**@return {void} */ function foo() { if (3 >= foo()) return; }",
        "right side of numeric comparison\n" +
        "found   : undefined\n" +
        "required: number");
  }

  @Test
  public void testStringComparison1() {
    testTypes("/**@param {string} a*/ function f(a) {return a < 'x';}");
  }

  @Test
  public void testStringComparison2() {
    compiler.getOptions().setWarningLevel(
        DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    testTypes("/**@param {Object} a*/ function f(a) {return a < 'x';}");
  }

  @Test
  public void testStringComparison3() {
    compiler.getOptions().setWarningLevel(
        DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    testTypes("/**@param {number} a*/ function f(a) {return a < 'x';}");
  }

  @Test
  public void testStringComparison4() {
    testTypes("/**@param {string|undefined} a*/ " +
                  "function f(a) {return a < 'x';}");
  }

  @Test
  public void testStringComparison5() {
    testTypes("/**@param {*} a*/ " +
                  "function f(a) {return a < 'x';}");
  }

  @Test
  public void testStringComparison6() {
    compiler.getOptions().setWarningLevel(
        DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    testTypes("/**@return {void} */ " +
        "function foo() { if ('a' >= foo()) return; }",
        "right side of comparison\n" +
        "found   : undefined\n" +
        "required: string");
  }

  @Test
  public void testValueOfComparison1() {
    testTypesWithExterns(
        new TestExternsBuilder().addObject().build(),
        lines(
            "/** @constructor */function O() {};",
            "/**@override*/O.prototype.valueOf = function() { return 1; };",
            "/**@param {!O} a\n@param {!O} b*/ function f(a,b) { return a < b; }"));
  }

  @Test
  public void testValueOfComparison2() {
    compiler.getOptions().setWarningLevel(
        DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    testTypesWithExterns(
        new TestExternsBuilder().addObject().build(),
        lines(
            "/** @constructor */function O() {};",
            "/**@override*/O.prototype.valueOf = function() { return 1; };",
            "/**",
            " * @param {!O} a",
            " * @param {number} b",
            " */",
            "function f(a,b) { return a < b; }"));
  }

  @Test
  public void testValueOfComparison3() {
    compiler.getOptions().setWarningLevel(
        DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    testTypesWithExterns(
        new TestExternsBuilder().addObject().build(),
        lines(
            "/** @constructor */function O() {};",
            "/**@override*/O.prototype.toString = function() { return 'o'; };",
            "/**",
            " * @param {!O} a",
            " * @param {string} b",
            " */",
            "function f(a,b) { return a < b; }"));
  }

  @Test
  public void testGenericRelationalExpression() {
    testTypes("/**@param {*} a\n@param {*} b*/ " +
                  "function f(a,b) {return a < b;}");
  }

  @Test
  public void testInstanceof1() {
    testTypes("function foo(){" +
        "if (bar instanceof 3)return;}",
        "instanceof requires an object\n" +
        "found   : number\n" +
        "required: Object");
  }

  @Test
  public void testInstanceof2() {
    testTypes("/**@return {void}*/function foo(){" +
        "if (foo() instanceof Object)return;}",
        "deterministic instanceof yields false\n" +
        "found   : undefined\n" +
        "required: NoObject");
  }

  @Test
  public void testInstanceof3() {
    testTypes("/**@return {*} */function foo(){" +
        "if (foo() instanceof Object)return;}");
  }

  @Test
  public void testInstanceof4() {
    testTypes("/**@return {(Object|number)} */function foo(){" +
        "if (foo() instanceof Object)return 3;}");
  }

  @Test
  public void testInstanceof5() {
    // No warning for unknown types.
    testTypes("/** @return {?} */ function foo(){" +
        "if (foo() instanceof Object)return;}");
  }

  @Test
  public void testInstanceof6() {
    testTypes("/**@return {(Array|number)} */function foo(){" +
        "if (foo() instanceof Object)return 3;}");
  }

  @Test
  public void testInstanceOfReduction3() {
    testTypes(
        "/** \n" +
        " * @param {Object} x \n" +
        " * @param {Function} y \n" +
        " * @return {boolean} \n" +
        " */\n" +
        "var f = function(x, y) {\n" +
        "  return x instanceof y;\n" +
        "};");
  }

  @Test
  public void testScoping1() {
    testTypes(
        "/**@param {string} a*/function foo(a){" +
        "  /**@param {Array|string} a*/function bar(a){" +
        "    if (a instanceof Array)return;" +
        "  }" +
        "}");
  }

  @Test
  public void testScoping2() {
    testTypes(
        "/** @type {number} */ var a;" +
        "function Foo() {" +
        "  /** @type {string} */ var a;" +
        "}");
  }

  @Test
  public void testScoping3() {
    testTypes("\n\n/** @type{Number}*/var b;\n/** @type{!String} */var b;",
        "variable b redefined with type String, original " +
        "definition at [testcode]:3 with type (Number|null)");
  }

  @Test
  public void testScoping4() {
    testTypes("/** @type{Number}*/var b; if (true) /** @type{!String} */var b;",
        "variable b redefined with type String, original " +
        "definition at [testcode]:1 with type (Number|null)");
  }

  @Test
  public void testScoping5() {
    // multiple definitions are not checked by the type checker but by a
    // subsequent pass
    testTypes("if (true) var b; var b;");
  }

  @Test
  public void testScoping6() {
    // multiple definitions are not checked by the type checker but by a
    // subsequent pass
    testTypes("if (true) var b; if (true) var b;");
  }

  @Test
  public void testScoping7() {
    testTypes("/** @constructor */function A() {" +
        "  /** @type {!A} */this.a = null;" +
        "}",
        "assignment to property a of A\n" +
        "found   : null\n" +
        "required: A");
  }

  @Test
  public void testScoping8() {
    testTypes("/** @constructor */function A() {}" +
        "/** @constructor */function B() {" +
        "  /** @type {!A} */this.a = null;" +
        "}",
        "assignment to property a of B\n" +
        "found   : null\n" +
        "required: A");
  }

  @Test
  public void testScoping9() {
    testTypes("/** @constructor */function B() {" +
        "  /** @type {!A} */this.a = null;" +
        "}" +
        "/** @constructor */function A() {}",
        "assignment to property a of B\n" +
        "found   : null\n" +
        "required: A");
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
    testTypes(lines(
        "/** @param {{a: number}|{a:number, b:string}} x */",
        "function f(x) {",
        "  var /** null */ n = x.b;",
        "}"),
        STRICT_INEXISTENT_PROPERTY);
  }

  @Test
  public void testDontDropPropertiesInUnion2() {
    testTypes(lines(
        "/** @param {{a:number, b:string}|{a: number}} x */",
        "function f(x) {",
        "  var /** null */ n = x.b;",
        "}"),
        STRICT_INEXISTENT_PROPERTY);
  }

  @Test
  public void testDontDropPropertiesInUnion3() {
    testTypes(lines(
        "/** @param {{a: number}|{a:number, b:string}} x */",
        "function f(x) {}",
        "/** @param {{a: number}} x */",
        "function g(x) { return x.b; }"),
        STRICT_INEXISTENT_PROPERTY);
  }

  @Test
  public void testDontDropPropertiesInUnion4() {
    testTypes(lines(
        "/** @param {{a: number}|{a:number, b:string}} x */",
        "function f(x) {}",
        "/** @param {{c: number}} x */",
        "function g(x) { return x.b; }"),
        "Property b never defined on x");
  }

  @Test
  public void testDontDropPropertiesInUnion5() {
    testTypes(lines(
        "/** @param {{a: number}|{a: number, b: string}} x */",
        "function f(x) {}",
        "f({a: 123});"));
  }

  @Test
  public void testDontDropPropertiesInUnion6() {
    testTypes(lines(
        "/** @param {{a: number}|{a: number, b: string}} x */",
        "function f(x) {",
        "  var /** null */ n = x;",
        "}"),
        lines(
            "initializing variable",
            "found   : {a: number}",
            "required: null"));
  }

  @Test
  public void testDontDropPropertiesInUnion7() {
    // Only a strict warning because in the registry we map {a, c} to {b, d}
    testTypes(lines(
        "/** @param {{a: number}|{a:number, b:string}} x */",
        "function f(x) {}",
        "/** @param {{c: number}|{c:number, d:string}} x */",
        "function g(x) { return x.b; }"),
        STRICT_INEXISTENT_PROPERTY);
  }

  @Test
  public void testScoping11() {
    // named function expressions create a binding in their body only
    // the return is wrong but the assignment is OK since the type of b is ?
    testTypes(
        "/** @return {number} */var a = function b(){ return b };",
        "inconsistent return type\n" +
        "found   : function(): number\n" +
        "required: number");
  }

  @Test
  public void testScoping12() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** @type {number} */ F.prototype.bar = 3;" +
        "/** @param {!F} f */ function g(f) {" +
        "  /** @return {string} */" +
        "  function h() {" +
        "    return f.bar;" +
        "  }" +
        "}",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testFunctionArguments1() {
    testFunctionType(
        "/** @param {number} a\n@return {string} */" +
        "function f(a) {}",
        "function(number): string");
  }

  @Test
  public void testFunctionArguments2() {
    testFunctionType(
        "/** @param {number} opt_a\n@return {string} */" +
        "function f(opt_a) {}",
        "function(number=): string");
  }

  @Test
  public void testFunctionArguments3() {
    testFunctionType(
        "/** @param {number} b\n@return {string} */" +
        "function f(a,b) {}",
        "function(?, number): string");
  }

  @Test
  public void testFunctionArguments4() {
    testFunctionType(
        "/** @param {number} opt_a\n@return {string} */" +
        "function f(a,opt_a) {}",
        "function(?, number=): string");
  }

  @Test
  public void testFunctionArguments5() {
    testTypes(
        "function a(opt_a,a) {}",
        "optional arguments must be at the end");
  }

  @Test
  public void testFunctionArguments6() {
    testTypes(
        "function a(var_args,a) {}",
        "variable length argument must be last");
  }

  @Test
  public void testFunctionArguments7() {
    testTypes(
        "/** @param {number} opt_a\n@return {string} */" +
        "function a(a,opt_a,var_args) {}");
  }

  @Test
  public void testFunctionArguments8() {
    testTypes(
        "function a(a,opt_a,var_args,b) {}",
        "variable length argument must be last");
  }

  @Test
  public void testFunctionArguments9() {
    // testing that only one error is reported
    testTypes(
        "function a(a,opt_a,var_args,b,c) {}",
        "variable length argument must be last");
  }

  @Test
  public void testFunctionArguments10() {
    // testing that only one error is reported
    testTypes(
        "function a(a,opt_a,b,c) {}",
        "optional arguments must be at the end");
  }

  @Test
  public void testFunctionArguments11() {
    testTypes(
        "function a(a,opt_a,b,c,var_args,d) {}",
        "optional arguments must be at the end");
  }

  @Test
  public void testFunctionArguments12() {
    testTypes("/** @param {String} foo  */function bar(baz){}",
        "parameter foo does not appear in bar's parameter list");
  }

  @Test
  public void testFunctionArguments13() {
    // verifying that the argument type have non-inferable types
    testTypes(
        "/** @return {boolean} */ function u() { return true; }" +
        "/** @param {boolean} b\n@return {?boolean} */" +
        "function f(b) { if (u()) { b = null; } return b; }",
        "assignment\n" +
        "found   : null\n" +
        "required: boolean");
  }

  @Test
  public void testFunctionArguments14() {
    testTypes(
        "/**\n" +
        " * @param {string} x\n" +
        " * @param {number} opt_y\n" +
        " * @param {boolean} var_args\n" +
        " */ function f(x, opt_y, var_args) {}" +
        "f('3'); f('3', 2); f('3', 2, true); f('3', 2, true, false);");
  }

  @Test
  public void testFunctionArguments15() {
    testTypes(
        "/** @param {?function(*)} f */" +
        "function g(f) { f(1, 2); }",
        "Function f: called with 2 argument(s). " +
        "Function requires at least 1 argument(s) " +
        "and no more than 1 argument(s).");
  }

  @Test
  public void testFunctionArguments16() {
    testTypes(
        lines(
            "/** @param {...number} var_args */", // preserve newlines
            "function g(var_args) {}",
            "g(1, true);"),
        lines(
            "actual parameter 2 of g does not match formal parameter",
            "found   : boolean",
            "required: number"));
  }

  @Test
  public void testUndefinedPassedForVarArgs() {
    testTypes(
        lines(
            "/** @param {...number} var_args */", // preserve newlines
            "function g(var_args) {}",
            "g(undefined, 1);"),
        lines(
            "actual parameter 1 of g does not match formal parameter",
            "found   : undefined",
            "required: number"));
  }

  @Test
  public void testFunctionArguments17() {
    testTypes(
        "/** @param {booool|string} x */" +
        "function f(x) { g(x) }" +
        "/** @param {number} x */" +
        "function g(x) {}",
        "Bad type annotation. Unknown type booool");
  }

  @Test
  public void testFunctionArguments18() {
    testTypes(
        "function f(x) {}" +
        "f(/** @param {number} y */ (function() {}));",
        "parameter y does not appear in <anonymous>'s parameter list");
  }

  @Test
  public void testVarArgParameterWithTemplateType() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        lines(
            "/**",
            " * @template T",
            " * @param {...T} var_args",
            " * @return {T}",
            " */",
            "function firstOf(var_args) { return arguments[0]; }",
            "/** @type {null} */ var a = firstOf('hi', 1);",
            ""),
        lines(
            "initializing variable", // preserve newlinues
            "found   : (number|string)",
            "required: null"));
  }

  @Test
  public void testRestParameters() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        lines(
            "/**",
            " * @param {...number} x",
            " */",
            "function f(...x) {",
            "  var /** string */ s = x[0];",
            "}"),
        lines(
            "initializing variable", // preserve newlines
            "found   : number",
            "required: string"));
  }

  @Test
  public void testRestParameterWithTemplateType() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        lines(
            "/**",
            " * @template T",
            " * @param {...T} rest",
            " * @return {T}",
            " */",
            "function firstOf(...rest) {",
            "  return rest[0];",
            "}",
            "/** @type {null} */ var a = firstOf('hi', 1);",
            ""),
        lines(
            "initializing variable", // preserve newlines
            "found   : (number|string)",
            "required: null"));
  }

  // Test that when transpiling we don't use T in the body of f; it would cause a spurious
  // unknown-type warning.
  @Test
  public void testRestParametersWithGenericsNoWarning() {
    testTypesWithExterns(
        new TestExternsBuilder().addObject().addArray().build(),
        lines(
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
            "}"));
  }

  @Test
  public void testPrintFunctionName1() {
    // Ensures that the function name is pretty.
    testTypes(
        "var goog = {}; goog.run = function(f) {};" +
        "goog.run();",
        "Function goog.run: called with 0 argument(s). " +
        "Function requires at least 1 argument(s) " +
        "and no more than 1 argument(s).");
  }

  @Test
  public void testPrintFunctionName2() {
    testTypes(
        "/** @constructor */ var Foo = function() {}; " +
        "Foo.prototype.run = function(f) {};" +
        "(new Foo).run();",
        "Function Foo.prototype.run: called with 0 argument(s). " +
        "Function requires at least 1 argument(s) " +
        "and no more than 1 argument(s).");
  }

  @Test
  public void testFunctionInference1() {
    testFunctionType(
        "function f(a) {}",
        "function(?): undefined");
  }

  @Test
  public void testFunctionInference2() {
    testFunctionType(
        "function f(a,b) {}",
        "function(?, ?): undefined");
  }

  @Test
  public void testFunctionInference3() {
    testFunctionType(
        "function f(var_args) {}",
        "function(...?): undefined");
  }

  @Test
  public void testFunctionInference4() {
    testFunctionType(
        "function f(a,b,c,var_args) {}",
        "function(?, ?, ?, ...?): undefined");
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
    testFunctionType(
        "function f() {}",
        "function(): undefined");
  }

  @Test
  public void testFunctionInference9() {
    testFunctionType(
        "var f = function() {};",
        "function(): undefined");
  }

  @Test
  public void testFunctionInference10() {
    testFunctionType(
        "/** @this {Date}\n@param {boolean} b\n@return {string} */" +
        "var f = function(a,b) {};",
        "function(this:Date, ?, boolean): string");
  }

  @Test
  public void testFunctionInference11() {
    testFunctionType(
        "var goog = {};" +
        "/** @return {number}*/goog.f = function(){};",
        "goog.f",
        "function(): number");
  }

  @Test
  public void testFunctionInference12() {
    testFunctionType(
        "var goog = {};" +
        "goog.f = function(){};",
        "goog.f",
        "function(): undefined");
  }

  @Test
  public void testFunctionInference13() {
    testFunctionType(
        "var goog = {};" +
        "/** @constructor */ goog.Foo = function(){};" +
        "/** @param {!goog.Foo} f */function eatFoo(f){};",
        "eatFoo",
        "function(goog.Foo): undefined");
  }

  @Test
  public void testFunctionInference14() {
    testFunctionType(
        "var goog = {};" +
        "/** @constructor */ goog.Foo = function(){};" +
        "/** @return {!goog.Foo} */function eatFoo(){ return new goog.Foo; };",
        "eatFoo",
        "function(): goog.Foo");
  }

  @Test
  public void testFunctionInference15() {
    testFunctionType(
        "/** @constructor */ function f() {};" +
        "f.prototype.foo = function(){};",
        "f.prototype.foo",
        "function(this:f): undefined");
  }

  @Test
  public void testFunctionInference16() {
    testFunctionType(
        "/** @constructor */ function f() {};" +
        "f.prototype.foo = function(){};",
        "(new f).foo",
        "function(this:f): undefined");
  }

  @Test
  public void testFunctionInference17() {
    testFunctionType(
        "/** @constructor */ function f() {}" +
        "function abstractMethod() {}" +
        "/** @param {number} x */ f.prototype.foo = abstractMethod;",
        "(new f).foo",
        "function(this:f, number): ?");
  }

  @Test
  public void testFunctionInference18() {
    testFunctionType(
        "var goog = {};" +
        "/** @this {Date} */ goog.eatWithDate;",
        "goog.eatWithDate",
        "function(this:Date): ?");
  }

  @Test
  public void testFunctionInference19() {
    testFunctionType(
        "/** @param {string} x */ var f;",
        "f",
        "function(string): ?");
  }

  @Test
  public void testFunctionInference20() {
    testFunctionType(
        "/** @this {Date} */ var f;",
        "f",
        "function(this:Date): ?");
  }

  @Test
  public void testFunctionInference21a() {
    testTypes(
        "var f = function() { throw 'x' };" +
        "/** @return {boolean} */ var g = f;");
  }

  @Test
  public void testFunctionInference21b() {
    testFunctionType(
        "var f = function() { throw 'x' };",
        "f",
        "function(): ?");
  }

  @Test
  public void testFunctionInference22() {
    testTypes(
        "/** @type {!Function} */ var f = function() { g(this); };" +
        "/** @param {boolean} x */ var g = function(x) {};");
  }

  @Test
  public void testFunctionInference23a() {
    // We want to make sure that 'prop' isn't declared on all objects.

    // This test is specifically checking loose property check behavior.
    disableStrictMissingPropertyChecks();

    testTypes(
        lines(
            "/** @type {!Function} */ var f = function() {",
            "  /** @type {number} */ this.prop = 3;",
            "};",
            "/**",
            " * @param {Object} x",
            " * @return {string}",
            " */ var g = function(x) { return x.prop; };"));
  }

  @Test
  public void testFunctionInference23b() {
    // We want to make sure that 'prop' isn't declared on all objects.

    testTypes(
        lines(
            "/** @type {!Function} */ var f = function() {",
            "  /** @type {number} */ this.prop = 3;",
            "};",
            "/**",
            " * @param {Object} x",
            " * @return {string}",
            " */ var g = function(x) { return x.prop; };"),
        "Property prop never defined on Object");
  }

  @Test
  public void testFunctionInference24() {
    testFunctionType(
        "var f = (/** number */ n, /** string= */ s) => null;",
        "function(number, string=): ?");
  }

  @Test
  public void testFunctionInference25() {
    testFunctionType(
        "var f = (/** number */ n, /** ...string */ s) => null;",
        "function(number, ...string): ?");
  }

  @Test
  public void testInnerFunction1() {
    testTypes(
        "function f() {" +
        " /** @type {number} */ var x = 3;\n" +
        " function g() { x = null; }" +
        " return x;" +
        "}",
        "assignment\n" +
        "found   : null\n" +
        "required: number");
  }

  @Test
  public void testInnerFunction2() {
    testTypes(
        "/** @return {number} */\n" +
        "function f() {" +
        " var x = null;\n" +
        " function g() { x = 3; }" +
        " g();" +
        " return x;" +
        "}",
        "inconsistent return type\n" +
        "found   : (null|number)\n" +
        "required: number");
  }

  @Test
  public void testInnerFunction3() {
    testTypes(
        "var x = null;" +
        "/** @return {number} */\n" +
        "function f() {" +
        " x = 3;\n" +
        " /** @return {number} */\n" +
        " function g() { x = true; return x; }" +
        " return x;" +
        "}",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testInnerFunction4() {
    testTypes(
        "var x = null;" +
        "/** @return {number} */\n" +
        "function f() {" +
        " x = '3';\n" +
        " /** @return {number} */\n" +
        " function g() { x = 3; return x; }" +
        " return x;" +
        "}",
        "inconsistent return type\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testInnerFunction5() {
    testTypes(
        "/** @return {number} */\n" +
        "function f() {" +
        " var x = 3;\n" +
        " /** @return {number} */" +
        " function g() { var x = 3;x = true; return x; }" +
        " return x;" +
        "}",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testInnerFunction6() {
    testClosureTypes(
        CLOSURE_DEFS +
        "function f() {" +
        " var x = 0 || function() {};\n" +
        " function g() { if (goog.isFunction(x)) { x(1); } }" +
        " g();" +
        "}",
        "Function x: called with 1 argument(s). " +
        "Function requires at least 0 argument(s) " +
        "and no more than 0 argument(s).");
  }

  @Test
  public void testInnerFunction7() {
    testClosureTypes(
        CLOSURE_DEFS +
        "function f() {" +
        " /** @type {number|function()} */" +
        " var x = 0 || function() {};\n" +
        " function g() { if (goog.isFunction(x)) { x(1); } }" +
        " g();" +
        "}",
        "Function x: called with 1 argument(s). " +
        "Function requires at least 0 argument(s) " +
        "and no more than 0 argument(s).");
  }

  @Test
  public void testInnerFunction8() {
    testClosureTypes(
        CLOSURE_DEFS +
        "function f() {" +
        " function x() {};\n" +
        " function g() { if (goog.isFunction(x)) { x(1); } }" +
        " g();" +
        "}",
        "Function x: called with 1 argument(s). " +
        "Function requires at least 0 argument(s) " +
        "and no more than 0 argument(s).");
  }

  @Test
  public void testInnerFunction9() {
    testTypes(
        "function f() {" +
        " var x = 3;\n" +
        " function g() { x = null; };\n" +
        " function h() { return x == null; }" +
        " return h();" +
        "}");
  }

  @Test
  public void testInnerFunction10() {
    testTypes(
        "function f() {" +
        "  /** @type {?number} */ var x = null;" +
        "  /** @return {string} */" +
        "  function g() {" +
        "    if (!x) {" +
        "      x = 1;" +
        "    }" +
        "    return x;" +
        "  }" +
        "}",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testInnerFunction11() {
    // TODO(nicksantos): This is actually bad inference, because
    // h sets x to null. We should fix this, but for now we do it
    // this way so that we don't break existing binaries. We will
    // need to change TypeInference#isUnflowable to fix this.
    testTypes(
        "function f() {" +
        "  /** @type {?number} */ var x = null;" +
        "  /** @return {number} */" +
        "  function g() {" +
        "    x = 1;" +
        "    h();" +
        "    return x;" +
        "  }" +
        "  function h() {" +
        "    x = null;" +
        "  }" +
        "}");
  }

  @Test
  public void testAbstractMethodHandling1() {
    testTypes(
        "/** @type {Function} */ var abstractFn = function() {};" +
        "abstractFn(1);");
  }

  @Test
  public void testAbstractMethodHandling2() {
    testTypes(
        "var abstractFn = function() {};" +
        "abstractFn(1);",
        "Function abstractFn: called with 1 argument(s). " +
        "Function requires at least 0 argument(s) " +
        "and no more than 0 argument(s).");
  }

  @Test
  public void testAbstractMethodHandling3() {
    testTypes(
        "var goog = {};" +
        "/** @type {Function} */ goog.abstractFn = function() {};" +
        "goog.abstractFn(1);");
  }

  @Test
  public void testAbstractMethodHandling4() {
    testTypes(
        "var goog = {};" +
        "goog.abstractFn = function() {};" +
        "goog.abstractFn(1);",
        "Function goog.abstractFn: called with 1 argument(s). " +
        "Function requires at least 0 argument(s) " +
        "and no more than 0 argument(s).");
  }

  @Test
  public void testAbstractMethodHandling5() {
    testTypes(
        "/** @type {!Function} */ var abstractFn = function() {};" +
        "/** @param {number} x */ var f = abstractFn;" +
        "f('x');",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testAbstractMethodHandling6() {
    testTypes(
        "var goog = {};" +
        "/** @type {Function} */ goog.abstractFn = function() {};" +
        "/** @param {number} x */ goog.f = abstractFn;" +
        "goog.f('x');",
        "actual parameter 1 of goog.f does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testMethodInference1() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** @return {number} */ F.prototype.foo = function() { return 3; };" +
        "/** @constructor \n * @extends {F} */ " +
        "function G() {}" +
        "/** @override */ G.prototype.foo = function() { return true; };",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testMethodInference2() {
    testTypes(
        "var goog = {};" +
        "/** @constructor */ goog.F = function() {};" +
        "/** @return {number} */ goog.F.prototype.foo = " +
        "    function() { return 3; };" +
        "/** @constructor \n * @extends {goog.F} */ " +
        "goog.G = function() {};" +
        "/** @override */ goog.G.prototype.foo = function() { return true; };",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testMethodInference3() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** @param {boolean} x \n * @return {number} */ " +
        "F.prototype.foo = function(x) { return 3; };" +
        "/** @constructor \n * @extends {F} */ " +
        "function G() {}" +
        "/** @override */ " +
        "G.prototype.foo = function(x) { return x; };",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testMethodInference4() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** @param {boolean} x \n * @return {number} */ " +
        "F.prototype.foo = function(x) { return 3; };" +
        "/** @constructor \n * @extends {F} */ " +
        "function G() {}" +
        "/** @override */ " +
        "G.prototype.foo = function(y) { return y; };",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testMethodInference5() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** @param {number} x \n * @return {string} */ " +
        "F.prototype.foo = function(x) { return 'x'; };" +
        "/** @constructor \n * @extends {F} */ " +
        "function G() {}" +
        "/** @type {number} */ G.prototype.num = 3;" +
        "/** @override */ " +
        "G.prototype.foo = function(y) { return this.num + y; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testMethodInference6() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** @param {number} x */ F.prototype.foo = function(x) { };" +
        "/** @constructor \n * @extends {F} */ " +
        "function G() {}" +
        "/** @override */ G.prototype.foo = function() { };" +
        "(new G()).foo(1);");
  }

  @Test
  public void testMethodInference7() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "F.prototype.foo = function() { };" +
        "/** @constructor \n * @extends {F} */ " +
        "function G() {}" +
        "/** @override */ G.prototype.foo = function(x, y) { };",
        "mismatch of the foo property type and the type of the property " +
        "it overrides from superclass F\n" +
        "original: function(this:F): undefined\n" +
        "override: function(this:G, ?, ?): undefined");
  }

  @Test
  public void testMethodInference8() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "F.prototype.foo = function() { };" +
        "/** @constructor \n * @extends {F} */ " +
        "function G() {}" +
        "/** @override */ " +
        "G.prototype.foo = function(opt_b, var_args) { };" +
        "(new G()).foo(1, 2, 3);");
  }

  @Test
  public void testMethodInference9() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "F.prototype.foo = function() { };" +
        "/** @constructor \n * @extends {F} */ " +
        "function G() {}" +
        "/** @override */ " +
        "G.prototype.foo = function(var_args, opt_b) { };",
        "variable length argument must be last");
  }

  @Test
  public void testStaticMethodDeclaration1() {
    testTypes(
        "/** @constructor */ function F() { F.foo(true); }" +
        "/** @param {number} x */ F.foo = function(x) {};",
        "actual parameter 1 of F.foo does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testStaticMethodDeclaration2() {
    testTypes(
        "var goog = goog || {}; function f() { goog.foo(true); }" +
        "/** @param {number} x */ goog.foo = function(x) {};",
        "actual parameter 1 of goog.foo does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testStaticMethodDeclaration3() {
    testTypes(
        "var goog = goog || {}; function f() { goog.foo(true); }" +
        "goog.foo = function() {};",
        "Function goog.foo: called with 1 argument(s). Function requires " +
        "at least 0 argument(s) and no more than 0 argument(s).");
  }

  @Test
  public void testDuplicateStaticMethodDecl1() {
    testTypes(
        "var goog = goog || {};" +
        "/** @param {number} x */ goog.foo = function(x) {};" +
        "/** @param {number} x */ goog.foo = function(x) {};",
        "variable goog.foo redefined, original definition at [testcode]:1");
  }

  @Test
  public void testDuplicateStaticMethodDecl2() {
    testTypes(
        "var goog = goog || {};" +
        "/** @param {number} x */ goog.foo = function(x) {};" +
        "/** @param {number} x \n * @suppress {duplicate} */ " +
        "goog.foo = function(x) {};");
  }

  @Test
  public void testDuplicateStaticMethodDecl3() {
    testTypes(
        "var goog = goog || {};" +
        "goog.foo = function(x) {};" +
        "goog.foo = function(x) {};");
  }

  @Test
  public void testDuplicateStaticMethodDecl4() {
    testTypes(
        "var goog = goog || {};" +
        "/** @type {Function} */ goog.foo = function(x) {};" +
        "goog.foo = function(x) {};");
  }

  @Test
  public void testDuplicateStaticMethodDecl5() {
    testTypes(
        "var goog = goog || {};" +
        "goog.foo = function(x) {};" +
        "/** @return {undefined} */ goog.foo = function(x) {};",
        "variable goog.foo redefined, " +
        "original definition at [testcode]:1");
  }

  @Test
  public void testDuplicateStaticMethodDecl6() {
    // Make sure the CAST node doesn't interfere with the @suppress
    // annotation.
    testTypes(
        "var goog = goog || {};" +
        "goog.foo = function(x) {};" +
        "/**\n" +
        " * @suppress {duplicate}\n" +
        " * @return {undefined}\n" +
        " */\n" +
        "goog.foo = " +
        "   /** @type {!Function} */ (function(x) {});");
  }

  @Test
  public void testDuplicateStaticMethodDecl7() {
    testTypes(
        "/** @type {string} */ var foo;\n" //
            + "var z = function foo() {};\n",
        TypeCheck.FUNCTION_MASKS_VARIABLE);
  }

  @Test
  public void testDuplicateStaticMethodDecl8() {
    testTypes(
        "/** @fileoverview @suppress {duplicate} */\n" //
            + "/** @type {string} */ var foo;\n"
            + "var z = function foo() {};\n");
  }

  @Test
  public void testDuplicateStaticPropertyDecl1() {
    testTypes(
        "var goog = goog || {};" +
        "/** @type {Foo} */ goog.foo;" +
        "/** @type {Foo} */ goog.foo;" +
        "/** @constructor */ function Foo() {}");
  }

  @Test
  public void testDuplicateStaticPropertyDecl2() {
    testTypes(
        "var goog = goog || {};" +
        "/** @type {Foo} */ goog.foo;" +
        "/** @type {Foo} \n * @suppress {duplicate} */ goog.foo;" +
        "/** @constructor */ function Foo() {}");
  }

  @Test
  public void testDuplicateStaticPropertyDecl3() {
    testTypes(
        "var goog = goog || {};" +
        "/** @type {!Foo} */ goog.foo;" +
        "/** @type {string} */ goog.foo;" +
        "/** @constructor */ function Foo() {}",
        "variable goog.foo redefined with type string, " +
        "original definition at [testcode]:1 with type Foo");
  }

  @Test
  public void testDuplicateStaticPropertyDecl4() {
    testClosureTypesMultipleWarnings(
        "var goog = goog || {};" +
        "/** @type {!Foo} */ goog.foo;" +
        "/** @type {string} */ goog.foo = 'x';" +
        "/** @constructor */ function Foo() {}",
        ImmutableList.of(
            "assignment to property foo of goog\n" +
            "found   : string\n" +
            "required: Foo",
            "variable goog.foo redefined with type string, " +
            "original definition at [testcode]:1 with type Foo"));
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
    testTypes(
        "var goog = goog || {};" +
        "/** @type {string} */ goog.foo = 'y';" +
        "/** @type {string}\n * @suppress {duplicate} */ goog.foo = 'x';");
  }

  @Test
  public void testDuplicateStaticPropertyDecl7() {
    testTypes(
        "var goog = goog || {};" +
        "/** @param {string} x */ goog.foo;" +
        "/** @type {function(string)} */ goog.foo;");
  }

  @Test
  public void testDuplicateStaticPropertyDecl8() {
    testTypes(
        "var goog = goog || {};" +
        "/** @return {EventCopy} */ goog.foo;" +
        "/** @constructor */ function EventCopy() {}" +
        "/** @return {EventCopy} */ goog.foo;");
  }

  @Test
  public void testDuplicateStaticPropertyDecl9() {
    testTypes(
        "var goog = goog || {};" +
        "/** @return {EventCopy} */ goog.foo;" +
        "/** @return {EventCopy} */ goog.foo;" +
        "/** @constructor */ function EventCopy() {}");
  }

  @Test
  public void testDuplicateStaticPropertyDec20() {
    testTypes(
        "/**\n" +
        " * @fileoverview\n" +
        " * @suppress {duplicate}\n" +
        " */" +
        "var goog = goog || {};" +
        "/** @type {string} */ goog.foo = 'y';" +
        "/** @type {string} */ goog.foo = 'x';");
  }

  @Test
  public void testDuplicateLocalVarDecl() {
    testClosureTypesMultipleWarnings(
        "/** @param {number} x */\n" +
        "function f(x) { /** @type {string} */ var x = ''; }",
        ImmutableList.of(
            "variable x redefined with type string, original definition" +
            " at [testcode]:2 with type number",
            "initializing variable\n" +
            "found   : string\n" +
            "required: number"));
  }

  @Test
  public void testDuplicateLocalVarDeclSuppressed() {
    // We can't just leave this to VarCheck since otherwise if that warning is suppressed, we'll end
    // up redeclaring it as undefined in the function block, which can cause spurious errors.
    testTypes(
        lines(
            "/** @suppress {duplicate} */",
            "function f(x) {",
            "  var x = x;",
            "  x.y = true;",
            "}"));
  }

  @Test
  public void testShadowBleedingFunctionName() {
    // This is allowed and creates a new binding inside the function shadowing the bled name.
    testTypes(
        lines(
            "var f = function x() {",
            "  var x;",
            "  var /** undefined */ y = x;",
            "};"));
  }

  @Test
  public void testDuplicateInstanceMethod1() {
    // If there's no jsdoc on the methods, then we treat them like
    // any other inferred properties.
    testTypes(
        "/** @constructor */ function F() {}" +
        "F.prototype.bar = function() {};" +
        "F.prototype.bar = function() {};");
  }

  @Test
  public void testDuplicateInstanceMethod2() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** jsdoc */ F.prototype.bar = function() {};" +
        "/** jsdoc */ F.prototype.bar = function() {};",
        "variable F.prototype.bar redefined, " +
        "original definition at [testcode]:1");
  }

  @Test
  public void testDuplicateInstanceMethod3() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "F.prototype.bar = function() {};" +
        "/** jsdoc */ F.prototype.bar = function() {};",
        "variable F.prototype.bar redefined, " +
        "original definition at [testcode]:1");
  }

  @Test
  public void testDuplicateInstanceMethod4() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** jsdoc */ F.prototype.bar = function() {};" +
        "F.prototype.bar = function() {};");
  }

  @Test
  public void testDuplicateInstanceMethod5() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** jsdoc \n * @return {number} */ F.prototype.bar = function() {" +
        "  return 3;" +
        "};" +
        "/** jsdoc \n * @suppress {duplicate} */ " +
        "F.prototype.bar = function() { return ''; };",
        "inconsistent return type\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testDuplicateInstanceMethod6() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** jsdoc \n * @return {number} */ F.prototype.bar = function() {" +
        "  return 3;" +
        "};" +
        "/** jsdoc \n * @return {string} * \n @suppress {duplicate} */ " +
        "F.prototype.bar = function() { return ''; };",
        "assignment to property bar of F.prototype\n" +
        "found   : function(this:F): string\n" +
        "required: function(this:F): number");
  }

  @Test
  public void testStubFunctionDeclaration1() {
    testFunctionType(
        "/** @constructor */ function f() {};" +
        "/** @param {number} x \n * @param {string} y \n" +
        "  * @return {number} */ f.prototype.foo;",
        "(new f).foo",
        "function(this:f, number, string): number");
  }

  @Test
  public void testStubFunctionDeclaration2() {
    testExternFunctionType(
        // externs
        "/** @constructor */ function f() {};" +
        "/** @constructor \n * @extends {f} */ f.subclass;",
        "f.subclass",
        "function(new:f.subclass): ?");
  }

  @Test
  public void testStubFunctionDeclaration3() {
    testFunctionType(
        "/** @constructor */ function f() {};" +
        "/** @return {undefined} */ f.foo;",
        "f.foo",
        "function(): undefined");
  }

  @Test
  public void testStubFunctionDeclaration4() {
    testFunctionType(
        "/** @constructor */ function f() { " +
        "  /** @return {number} */ this.foo;" +
        "}",
        "(new f).foo",
        "function(this:f): number");
  }

  @Test
  public void testStubFunctionDeclaration5() {
    testFunctionType(
        lines(
            "/** @constructor */ function f() { ", // preserve newlines
            "  /** @type {Function} */ this.foo;",
            "}"),
        "(new f).foo",
        createNullableType(getNativeU2UConstructorType()).toString());
  }

  @Test
  public void testStubFunctionDeclaration6() {
    testFunctionType(
        lines(
            "/** @constructor */ function f() {} ", // preserve newlines
            "/** @type {Function} */ f.prototype.foo;"),
        "(new f).foo",
        createNullableType(getNativeU2UConstructorType()).toString());
  }

  @Test
  public void testStubFunctionDeclaration7() {
    testFunctionType(
        lines(
            "/** @constructor */ function f() {} ", // preserve newlines
            "/** @type {Function} */ f.prototype.foo = function() {};"),
        "(new f).foo",
        createNullableType(getNativeU2UConstructorType()).toString());
  }

  @Test
  public void testStubFunctionDeclaration8() {
    testFunctionType(
        "/** @type {Function} */ var f = function() {}; ",
        "f",
        createNullableType(getNativeU2UConstructorType()).toString());
  }

  @Test
  public void testStubFunctionDeclaration9() {
    testFunctionType(
        "/** @type {function():number} */ var f; ",
        "f",
        "function(): number");
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
    testTypes(
        lines(
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
            "var /** null */ n = (new Bar).method();"),
        lines(
            "initializing variable",
            "found   : number",
            "required: null"));
  }

  @Test
  public void testStubMethodDeclarationDoesntBlockTypechecking_2() {
    testTypes(
        lines(
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
            "var /** null */ n = (new Bar).method();"),
        lines(
            "initializing variable",
            "found   : number",
            "required: null"));
  }

  @Test
  public void testStubMethodDeclarationDoesntBlockTypechecking_3() {
    testTypes(
        lines(
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
            "var /** string */ x = (new Bar).num;"));
  }

  @Test
  public void testNestedFunctionInference1() {
    String nestedAssignOfFooAndBar =
        "/** @constructor */ function f() {};" +
        "f.prototype.foo = f.prototype.bar = function(){};";
    testFunctionType(nestedAssignOfFooAndBar, "(new f).bar",
        "function(this:f): undefined");
  }

  /**
   * Tests the type of a function definition. The function defined by
   * {@code functionDef} should be named {@code "f"}.
   */
  private void testFunctionType(String functionDef, String functionType) {
    testFunctionType(functionDef, "f", functionType);
  }

  /**
   * Tests the type of a function definition. The function defined by
   * {@code functionDef} should be named {@code functionName}.
   */
  private void testFunctionType(String functionDef, String functionName,
      String functionType) {
    // using the variable initialization check to verify the function's type
    testTypes(
        functionDef +
        "/** @type {number} */var a=" + functionName + ";",
        "initializing variable\n" +
        "found   : " + functionType + "\n" +
        "required: number");
  }

  /**
   * Tests the type of a function definition in externs.
   * The function defined by {@code functionDef} should be
   * named {@code functionName}.
   */
  private void testExternFunctionType(String functionDef, String functionName,
      String functionType) {
    testTypesWithExterns(
        functionDef,
        "/** @type {number} */var a=" + functionName + ";",
        "initializing variable\n" +
        "found   : " + functionType + "\n" +
        "required: number", false);
  }

  @Test
  public void testTypeRedefinition() {
    testClosureTypesMultipleWarnings("a={};/**@enum {string}*/ a.A = {ZOR:'b'};"
        + "/** @constructor */ a.A = function() {}",
        ImmutableList.of(
            "variable a.A redefined with type function(new:a.A): undefined, " +
            "original definition at [testcode]:1 with type enum{a.A}",
            "assignment to property A of a\n" +
            "found   : function(new:a.A): undefined\n" +
            "required: enum{a.A}"));
  }

  @Test
  public void testIn1() {
    testTypes("'foo' in Object");
  }

  @Test
  public void testIn2() {
    testTypes("3 in Object");
  }

  @Test
  public void testIn3() {
    testTypes("undefined in Object");
  }

  @Test
  public void testIn4() {
    testTypes(
        "Date in Object",
        "left side of 'in'\n"
            + "found   : function(new:Date, ?=, ?=, ?=, ?=, ?=, ?=, ?=): string\n"
            + "required: (string|symbol)");
  }

  @Test
  public void testIn5() {
    testTypes("'x' in null",
        "'in' requires an object\n" +
        "found   : null\n" +
        "required: Object");
  }

  @Test
  public void testIn6() {
    testTypes(
        "/** @param {number} x */" +
        "function g(x) {}" +
        "g(1 in {});",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testIn7() {
    // Make sure we do inference in the 'in' expression.
    testTypes(
        lines(
            "/**",
            " * @param {number} x",
            " * @return {number}",
            " */",
            "function g(x) { return 5; }",
            "function f() {",
            "  var x = {};",
            "  x.foo = '3';",
            "  return g(x.foo) in {};",
            "}"),
        lines(
            "actual parameter 1 of g does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testInWithWellKnownSymbol() {
    testTypesWithCommonExterns("Symbol.iterator in Object");
  }

  @Test
  public void testInWithUniqueSymbol() {
    testTypes("Symbol('foo') in Object");
  }

  @Test
  public void testInWithSymbol() {
    testTypes("function f(/** symbol */ s) { return s in {}; }");
  }

  @Test
  public void testForIn1() {
    testTypes(
        "/** @param {boolean} x */ function f(x) {}" +
        "for (var k in {}) {" +
        "  f(k);" +
        "}",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: boolean");
  }

  @Test
  public void testForIn2() {
    testTypes(
        "/** @param {boolean} x */ function f(x) {}" +
        "/** @enum {string} */ var E = {FOO: 'bar'};" +
        "/** @type {Object<E, string>} */ var obj = {};" +
        "var k = null;" +
        "for (k in obj) {" +
        "  f(k);" +
        "}",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : E<string>\n" +
        "required: boolean");
  }

  @Test
  public void testForIn3() {
    testTypes(
        "/** @param {boolean} x */ function f(x) {}" +
        "/** @type {Object<number>} */ var obj = {};" +
        "for (var k in obj) {" +
        "  f(obj[k]);" +
        "}",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: boolean");
  }

  @Test
  public void testForIn4() {
    testTypes(
        "/** @param {boolean} x */ function f(x) {}" +
        "/** @enum {string} */ var E = {FOO: 'bar'};" +
        "/** @type {Object<E, Array>} */ var obj = {};" +
        "for (var k in obj) {" +
        "  f(obj[k]);" +
        "}",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : (Array|null)\n" +
        "required: boolean");
  }

  @Test
  public void testForIn5() {
    testTypesWithExterns(
        new TestExternsBuilder().addObject().build(),
        lines(
            "/** @param {boolean} x */ function f(x) {}",
            "/** @constructor */ var E = function(){};",
            "/** @override */ E.prototype.toString = function() { return ''; };",
            "/** @type {Object<!E, number>} */ var obj = {};",
            "for (var k in obj) {",
            "  f(k);",
            "}"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : string",
            "required: boolean"));
  }

  @Test
  public void testForOf1() {
    testTypesWithCommonExterns(
        "/** @type {!Iterable<number>} */ var it = [1, 2, 3]; for (let x of it) { alert(x); }");
  }

  @Test
  public void testForOf2() {
    testTypesWithCommonExterns(
        lines(
            "/** @param {boolean} x */ function f(x) {}",
            "/** @type {!Iterable<number>} */ var it = [1, 2, 3];",
            "for (let x of it) { f(x); }"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : number",
            "required: boolean"));
  }

  @Test
  public void testForOf3() {
    testTypesWithCommonExterns(
        lines(
            "/** @type {?Iterable<number>} */ var it = [1, 2, 3];",
            "for (let x of it) {}"),
        lines(
            "Can only iterate over a (non-null) Iterable type",
            "found   : (Iterable<number>|null)",
            "required: Iterable"));
  }

  @Test
  public void testForOf4() {
    testTypesWithCommonExterns(
        lines(
            "function f(/** !Iterator<number> */ it) {",
            "  for (let x of it) {}",
            "}"),
        lines(
            "Can only iterate over a (non-null) Iterable type",
            "found   : Iterator<number>",
            "required: Iterable"));
  }

  @Test
  public void testForOf5() {
    // 'string' is an Iterable<string> so it can be used in a for/of.
    testTypesWithCommonExterns(
        lines(
            "function f(/** string */ ch, /** string */ str) {",
            "  for (ch of str) {}",
            "}"));
  }

  @Test
  public void testForOf6() {
    testTypesWithCommonExterns(
        lines(
            "function f(/** !Array<number> */ a) {",
            "  for (let elem of a) {}",
            "}"));
  }

  @Test
  public void testForOf7() {
    testTypesWithCommonExterns("for (let elem of ['array', 'literal']) {}");
  }

  // TODO(tbreisacher): This should produce a warning: Expected 'null' but got 'string|number'
  @Test
  public void testForOf8() {
    // Union of different types of Iterables.
    testTypesWithCommonExterns(
        lines(
            "function f(/** null */ x) {}",
            "(function(/** !Array<string>|!Iterable<number> */ it) {",
            "  for (let elem of it) {",
            "    f(elem);",
            "  }",
            "})(['']);"));
  }

  // TODO(nicksantos): change this to something that makes sense.
  @Test
  @Ignore
  public void testComparison1() {
    testTypes(
        "/**@type null */var a;" + "/**@type !Date */var b;" + "if (a==b) {}",
        "condition always evaluates to false\n" + "left : null\n" + "right: Date");
  }

  @Test
  public void testComparison2() {
    testTypes("/**@type {number}*/var a;" +
        "/**@type {!Date} */var b;" +
        "if (a!==b) {}",
        "condition always evaluates to true\n" +
        "left : number\n" +
        "right: Date");
  }

  @Test
  public void testComparison3() {
    // Since null == undefined in JavaScript, this code is reasonable.
    testTypes("/** @type {(Object|undefined)} */var a;" +
        "var b = a == null");
  }

  @Test
  public void testComparison4() {
    testTypes("/** @type {(!Object|undefined)} */var a;" +
        "/** @type {!Object} */var b;" +
        "var c = a == b");
  }

  @Test
  public void testComparison5() {
    testTypes("/** @type {null} */var a;" +
        "/** @type {null} */var b;" +
        "a == b",
        "condition always evaluates to true\n" +
        "left : null\n" +
        "right: null");
  }

  @Test
  public void testComparison6() {
    testTypes("/** @type {null} */var a;" +
        "/** @type {null} */var b;" +
        "a != b",
        "condition always evaluates to false\n" +
        "left : null\n" +
        "right: null");
  }

  @Test
  public void testComparison7() {
    testTypes("var a;" +
        "var b;" +
        "a == b",
        "condition always evaluates to true\n" +
        "left : undefined\n" +
        "right: undefined");
  }

  @Test
  public void testComparison8() {
    testTypes("/** @type {Array<string>} */ var a = [];" +
        "a[0] == null || a[1] == undefined");
  }

  @Test
  public void testComparison9() {
    testTypes("/** @type {Array<undefined>} */ var a = [];" +
        "a[0] == null",
        "condition always evaluates to true\n" +
        "left : undefined\n" +
        "right: null");
  }

  @Test
  public void testComparison10() {
    testTypes("/** @type {Array<undefined>} */ var a = [];" +
        "a[0] === null");
  }

  @Test
  public void testComparison11() {
    testTypes(
        "(function(){}) == 'x'",
        "condition always evaluates to false\n" +
        "left : function(): undefined\n" +
        "right: string");
  }

  @Test
  public void testComparison12() {
    testTypes(
        "(function(){}) == 3",
        "condition always evaluates to false\n" +
        "left : function(): undefined\n" +
        "right: number");
  }

  @Test
  public void testComparison13() {
    testTypes(
        "(function(){}) == false",
        "condition always evaluates to false\n" +
        "left : function(): undefined\n" +
        "right: boolean");
  }

  @Test
  public void testComparison14() {
    testTypes("/** @type {function((Array|string), Object): number} */" +
        "function f(x, y) { return x === y; }",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testComparison15() {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @constructor */ function F() {}" +
        "/**\n" +
        " * @param {number} x\n" +
        " * @constructor\n" +
        " * @extends {F}\n" +
        " */\n" +
        "function G(x) {}\n" +
        "goog.inherits(G, F);\n" +
        "/**\n" +
        " * @param {number} x\n" +
        " * @constructor\n" +
        " * @extends {G}\n" +
        " */\n" +
        "function H(x) {}\n" +
        "goog.inherits(H, G);\n" +
        "/** @param {G} x */" +
        "function f(x) { return x.constructor === H; }",
        null);
  }

  @Test
  public void testDeleteOperator1() {
    testTypes(
        "var x = {};" +
        "/** @return {string} */ function f() { return delete x['a']; }",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: string");
  }

  @Test
  public void testDeleteOperator2() {
    testTypes(
        "var obj = {};" +
        "/** \n" +
        " * @param {string} x\n" +
        " * @return {Object} */ function f(x) { return obj; }" +
        "/** @param {?number} x */ function g(x) {" +
        "  if (x) { delete f(x)['a']; }" +
        "}",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testEnumStaticMethod1() {
    testTypes(
        "/** @enum */ var Foo = {AAA: 1};" +
        "/** @param {number} x */ Foo.method = function(x) {};" +
        "Foo.method(true);",
        "actual parameter 1 of Foo.method does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testEnumStaticMethod2() {
    testTypes(
        "/** @enum */ var Foo = {AAA: 1};" +
        "/** @param {number} x */ Foo.method = function(x) {};" +
        "function f() { Foo.method(true); }",
        "actual parameter 1 of Foo.method does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testEnum1() {
    testTypes("/**@enum*/var a={BB:1,CC:2};\n" +
        "/**@type {a}*/var d;d=a.BB;");
  }

  @Test
  public void testEnum3() {
    testTypes("/**@enum*/var a={BB:1,BB:2}",
        "variable a.BB redefined, original definition at [testcode]:1");
  }

  @Test
  public void testEnum4() {
    testTypes("/**@enum*/var a={BB:'string'}",
        "assignment to property BB of enum{a}\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testEnum5() {
    testTypes("/**@enum {?String}*/var a={BB:'string'}",
        "assignment to property BB of enum{a}\n" +
        "found   : string\n" +
        "required: (String|null)");
  }

  @Test
  public void testEnum6() {
    testTypes("/**@enum*/var a={BB:1,CC:2};\n/**@type {!Array}*/var d;d=a.BB;",
        "assignment\n" +
        "found   : a<number>\n" +
        "required: Array");
  }

  @Test
  public void testEnum7() {
    testTypes("/** @enum */var a={AA:1,BB:2,CC:3};" +
        "/** @type {a} */var b=a.D;",
        "element D does not exist on this enum");
  }

  @Test
  public void testEnum8() {
    testClosureTypesMultipleWarnings("/** @enum */var a=8;",
        ImmutableList.of(
            "enum initializer must be an object literal or an enum",
            "initializing variable\n" +
            "found   : number\n" +
            "required: enum{a}"));
  }

  @Test
  public void testEnum9() {
    testClosureTypesMultipleWarnings(
        "var goog = {};" +
        "/** @enum */goog.a=8;",
        ImmutableList.of(
            "assignment to property a of goog\n" +
            "found   : number\n" +
            "required: enum{goog.a}",
            "enum initializer must be an object literal or an enum"));
  }

  @Test
  public void testEnum10() {
    testTypes(
        "/** @enum {number} */" +
        "goog.K = { A : 3 };");
  }

  @Test
  public void testEnum11() {
    testTypes(
        "/** @enum {number} */" +
        "goog.K = { 502 : 3 };");
  }

  @Test
  public void testEnum12() {
    testTypes(
        "/** @enum {number} */ var a = {};" +
        "/** @enum */ var b = a;");
  }

  @Test
  public void testEnum13a() {
    testTypes(
        "/** @enum {number} */ var a = {};" +
        "/** @enum {string} */ var b = a;",
        "incompatible enum element types\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testEnum13b() {
    testTypes(
        lines(
            "/** @enum {number} */ var a = {};",
            "/** @const */ var ns = {};",
            "/** @enum {string} */ ns.b = a;"),
        lines(
            "incompatible enum element types", // preserve newlines
            "found   : number",
            "required: string"));
  }

  @Test
  public void testEnum14() {
    testTypes(
        "/** @enum {number} */ var a = {FOO:5};" +
        "/** @enum */ var b = a;" +
        "var c = b.FOO;");
  }

  @Test
  public void testEnum15() {
    testTypes(
        "/** @enum {number} */ var a = {FOO:5};" +
        "/** @enum */ var b = a;" +
        "var c = b.BAR;",
        "element BAR does not exist on this enum");
  }

  @Test
  public void testEnum16() {
    testTypes("var goog = {};" +
        "/**@enum*/goog .a={BB:1,BB:2}",
        "variable goog.a.BB redefined, original definition at [testcode]:1");
  }

  @Test
  public void testEnum17() {
    testTypes("var goog = {};" +
        "/**@enum*/goog.a={BB:'string'}",
        "assignment to property BB of enum{goog.a}\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testEnum18() {
    testTypes("/**@enum*/ var E = {A: 1, B: 2};" +
        "/** @param {!E} x\n@return {number} */\n" +
        "var f = function(x) { return x; };");
  }

  @Test
  public void testEnum19() {
    testTypes("/**@enum*/ var E = {A: 1, B: 2};" +
        "/** @param {number} x\n@return {!E} */\n" +
        "var f = function(x) { return x; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: E<number>");
  }

  @Test
  public void testEnum20() {
    testTypes("/**@enum*/ var E = {A: 1, B: 2}; var x = []; x[E.A] = 0;");
  }

  @Test
  public void testEnum21() {
    Node n = parseAndTypeCheck(
        "/** @enum {string} */ var E = {A : 'a', B : 'b'};\n" +
        "/** @param {!E} x\n@return {!E} */ function f(x) { return x; }");
    Node nodeX = n.getLastChild().getLastChild().getLastChild().getLastChild();
    JSType typeE = nodeX.getJSType();
    assertThat(typeE.isObject()).isFalse();
    assertThat(typeE.isNullable()).isFalse();
  }

  @Test
  public void testEnum22() {
    testTypes("/**@enum*/ var E = {A: 1, B: 2};" +
        "/** @param {E} x \n* @return {number} */ function f(x) {return x}");
  }

  @Test
  public void testEnum23() {
    testTypes("/**@enum*/ var E = {A: 1, B: 2};" +
        "/** @param {E} x \n* @return {string} */ function f(x) {return x}",
        "inconsistent return type\n" +
        "found   : E<number>\n" +
        "required: string");
  }

  @Test
  public void testEnum24() {
    testTypes("/**@enum {?Object} */ var E = {A: {}};" +
        "/** @param {E} x \n* @return {!Object} */ function f(x) {return x}",
        "inconsistent return type\n" +
        "found   : E<(Object|null)>\n" +
        "required: Object");
  }

  @Test
  public void testEnum25() {
    testTypes("/**@enum {!Object} */ var E = {A: {}};" +
        "/** @param {E} x \n* @return {!Object} */ function f(x) {return x}");
  }

  @Test
  public void testEnum26() {
    testTypes("var a = {}; /**@enum*/ a.B = {A: 1, B: 2};" +
        "/** @param {a.B} x \n* @return {number} */ function f(x) {return x}");
  }

  @Test
  public void testEnum27() {
    // x is unknown
    testTypes("/** @enum */ var A = {B: 1, C: 2}; " +
        "function f(x) { return A == x; }");
  }

  @Test
  public void testEnum28() {
    // x is unknown
    testTypes("/** @enum */ var A = {B: 1, C: 2}; " +
        "function f(x) { return A.B == x; }");
  }

  @Test
  public void testEnum29() {
    testTypes("/** @enum */ var A = {B: 1, C: 2}; " +
        "/** @return {number} */ function f() { return A; }",
        "inconsistent return type\n" +
        "found   : enum{A}\n" +
        "required: number");
  }

  @Test
  public void testEnum30() {
    testTypes("/** @enum */ var A = {B: 1, C: 2}; " +
        "/** @return {number} */ function f() { return A.B; }");
  }

  @Test
  public void testEnum31() {
    testTypes("/** @enum */ var A = {B: 1, C: 2}; " +
        "/** @return {A} */ function f() { return A; }",
        "inconsistent return type\n" +
        "found   : enum{A}\n" +
        "required: A<number>");
  }

  @Test
  public void testEnum32() {
    testTypes("/** @enum */ var A = {B: 1, C: 2}; " +
        "/** @return {A} */ function f() { return A.B; }");
  }

  @Test
  public void testEnum34() {
    testTypes("/** @enum */ var A = {B: 1, C: 2}; " +
        "/** @param {number} x */ function f(x) { return x == A.B; }");
  }

  @Test
  public void testEnum35() {
    testTypes("var a = a || {}; /** @enum */ a.b = {C: 1, D: 2};" +
              "/** @return {a.b} */ function f() { return a.b.C; }");
  }

  @Test
  public void testEnum36() {
    testTypes("var a = a || {}; /** @enum */ a.b = {C: 1, D: 2};" +
              "/** @return {!a.b} */ function f() { return 1; }",
              "inconsistent return type\n" +
              "found   : number\n" +
              "required: a.b<number>");
  }

  @Test
  public void testEnum37() {
    testTypes(
        "var goog = goog || {};" +
        "/** @enum {number} */ goog.a = {};" +
        "/** @enum */ var b = goog.a;");
  }

  @Test
  public void testEnum38() {
    testTypes(
        "/** @enum {MyEnum} */ var MyEnum = {};" +
        "/** @param {MyEnum} x */ function f(x) {}",
        "Parse error. Cycle detected in inheritance chain " +
        "of type MyEnum");
  }

  @Test
  public void testEnum39() {
    testTypes(
        "/** @enum {Number} */ var MyEnum = {FOO: new Number(1)};" +
        "/** @param {MyEnum} x \n * @return {number} */" +
        "function f(x) { return x == MyEnum.FOO && MyEnum.FOO == x; }",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testEnum40() {
    testTypes(
        "/** @enum {Number} */ var MyEnum = {FOO: new Number(1)};" +
        "/** @param {number} x \n * @return {number} */" +
        "function f(x) { return x == MyEnum.FOO && MyEnum.FOO == x; }",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testEnum41() {
    testTypes(
        "/** @enum {number} */ var MyEnum = {/** @const */ FOO: 1};" +
        "/** @return {string} */" +
        "function f() { return MyEnum.FOO; }",
        "inconsistent return type\n" +
        "found   : MyEnum<number>\n" +
        "required: string");
  }

  @Test
  public void testEnum42a() {
    testTypes(lines(
        "/** @param {number} x */ function f(x) {}",
        "/** @enum {Object} */ var MyEnum = {FOO: {a: 1, b: 2}};",
        "f(MyEnum.FOO.a);"),
        "Property a never defined on MyEnum<Object>");
  }

  @Test
  public void testEnum42b() {
    testTypes(
        lines(
            "/** @param {number} x */ function f(x) {}",
            "/** @enum {!Object} */ var MyEnum = {FOO: {a: 1, b: 2}};",
            "f(MyEnum.FOO.a);"),
        "Property a never defined on MyEnum<Object>");
  }

  @Test
  public void testEnum42c() {
    disableStrictMissingPropertyChecks();

    testTypes(lines(
        "/** @param {number} x */ function f(x) {}",
        "/** @enum {Object} */ var MyEnum = {FOO: {a: 1, b: 2}};",
        "f(MyEnum.FOO.a);"));
  }

  @Test
  public void testEnum43() {
    testTypes(
        lines(
            "/** @param {number} x */ function f(x) {}",
            "/** @enum {{a:number, b:number}} */ var MyEnum = {FOO: {a: 1, b: 2}};",
            "f(MyEnum.FOO.a);"));
  }

  @Test
  public void testEnumDefinedInObjectLiteral() {
    testTypes(
        lines(
            "var ns = {",
            "  /** @enum {number} */",
            "  Enum: {A: 1, B: 2},",
            "};",
            "/** @param {!ns.Enum} arg */",
            "function f(arg) {}"));
  }

  @Test
  public void testAliasedEnum1() {
    testTypes(
        "/** @enum */ var YourEnum = {FOO: 3};" +
        "/** @enum */ var MyEnum = YourEnum;" +
        "/** @param {MyEnum} x */ function f(x) {} f(MyEnum.FOO);");
  }

  @Test
  public void testAliasedEnum2() {
    testTypes(
        "/** @enum */ var YourEnum = {FOO: 3};" +
        "/** @enum */ var MyEnum = YourEnum;" +
        "/** @param {YourEnum} x */ function f(x) {} f(MyEnum.FOO);");
  }

  @Test
  public void testAliasedEnum3() {
    testTypes(
        "/** @enum */ var YourEnum = {FOO: 3};" +
        "/** @enum */ var MyEnum = YourEnum;" +
        "/** @param {MyEnum} x */ function f(x) {} f(YourEnum.FOO);");
  }

  @Test
  public void testAliasedEnum4() {
    testTypes(
        "/** @enum */ var YourEnum = {FOO: 3};" +
        "/** @enum */ var MyEnum = YourEnum;" +
        "/** @param {YourEnum} x */ function f(x) {} f(YourEnum.FOO);");
  }

  @Test
  public void testAliasedEnum5() {
    testTypes(
        "/** @enum */ var YourEnum = {FOO: 3};" +
        "/** @enum */ var MyEnum = YourEnum;" +
        "/** @param {string} x */ function f(x) {} f(MyEnum.FOO);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : YourEnum<number>\n" +
        "required: string");
  }

  @Test
  public void testConstAliasedEnum() {
    testTypes(
        lines(
            "/** @enum */ var YourEnum = {FOO: 3};",
            "/** @const */ var MyEnum = YourEnum;",
            "/** @param {MyEnum} x */ function f(x) {} f(MyEnum.FOO);"));
  }

  @Test
  public void testBackwardsEnumUse1() {
    testTypes(
        "/** @return {string} */ function f() { return MyEnum.FOO; }" +
        "/** @enum {string} */ var MyEnum = {FOO: 'x'};");
  }

  @Test
  public void testBackwardsEnumUse2() {
    testTypes(
        "/** @return {number} */ function f() { return MyEnum.FOO; }" +
        "/** @enum {string} */ var MyEnum = {FOO: 'x'};",
        "inconsistent return type\n" +
        "found   : MyEnum<string>\n" +
        "required: number");
  }

  @Test
  public void testBackwardsEnumUse3() {
    testTypes(
        "/** @return {string} */ function f() { return MyEnum.FOO; }" +
        "/** @enum {string} */ var YourEnum = {FOO: 'x'};" +
        "/** @enum {string} */ var MyEnum = YourEnum;");
  }

  @Test
  public void testBackwardsEnumUse4() {
    testTypes(
        "/** @return {number} */ function f() { return MyEnum.FOO; }" +
        "/** @enum {string} */ var YourEnum = {FOO: 'x'};" +
        "/** @enum {string} */ var MyEnum = YourEnum;",
        "inconsistent return type\n" +
        "found   : YourEnum<string>\n" +
        "required: number");
  }

  @Test
  public void testBackwardsEnumUse5() {
    testTypes(
        "/** @return {string} */ function f() { return MyEnum.BAR; }" +
        "/** @enum {string} */ var YourEnum = {FOO: 'x'};" +
        "/** @enum {string} */ var MyEnum = YourEnum;",
        "element BAR does not exist on this enum");
  }

  @Test
  public void testBackwardsTypedefUse2() {
    testTypes(
        "/** @this {MyTypedef} */ function f() {}" +
        "/** @typedef {!(Date|Array)} */ var MyTypedef;");
  }

  @Test
  public void testBackwardsTypedefUse4() {
    testTypes(
        "/** @return {MyTypedef} */ function f() { return null; }" +
        "/** @typedef {string} */ var MyTypedef;",
        "inconsistent return type\n" +
        "found   : null\n" +
        "required: string");
  }

  @Test
  public void testBackwardsTypedefUse6() {
    testTypes(
        "/** @return {goog.MyTypedef} */ function f() { return null; }" +
        "var goog = {};" +
        "/** @typedef {string} */ goog.MyTypedef;",
        "inconsistent return type\n" +
        "found   : null\n" +
        "required: string");
  }

  @Test
  public void testBackwardsTypedefUse7() {
    testTypes(
        "/** @return {goog.MyTypedef} */ function f() { return null; }" +
        "var goog = {};" +
        "/** @typedef {Object} */ goog.MyTypedef;");
  }

  @Test
  public void testBackwardsTypedefUse8() {
    // Technically, this isn't quite right, because the JS runtime
    // will coerce null -> the global object. But we'll punt on that for now.
    testTypes(
        "/** @param {!Array} x */ function g(x) {}" +
        "/** @this {goog.MyTypedef} */ function f() { g(this); }" +
        "var goog = {};" +
        "/** @typedef {(Array|null|undefined)} */ goog.MyTypedef;");
  }

  @Test
  public void testBackwardsTypedefUse9() {
    testTypes(
        lines(
            "/** @param {!Array} x */ function g(x) {}",
            "/** @this {goog.MyTypedef} */ function f() { g(this); }",
            "var goog = {};",
            "/** @typedef {(RegExp|null|undefined)} */ goog.MyTypedef;"),
        lines(
            "actual parameter 1 of g does not match formal parameter",
            "found   : RegExp",
            "required: Array"));
  }

  @Test
  public void testBackwardsTypedefUse10() {
    testTypes(
        "/** @param {goog.MyEnum} x */ function g(x) {}" +
        "var goog = {};" +
        "/** @enum {goog.MyTypedef} */ goog.MyEnum = {FOO: 1};" +
        "/** @typedef {number} */ goog.MyTypedef;" +
        "g(1);",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : number\n" +
        "required: goog.MyEnum<number>");
  }

  @Test
  public void testBackwardsConstructor1() {
    testTypes(
        "function f() { (new Foo(true)); }" +
        "/** \n * @constructor \n * @param {number} x */" +
        "var Foo = function(x) {};",
        "actual parameter 1 of Foo does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testBackwardsConstructor2() {
    testTypes(
        "function f() { (new Foo(true)); }" +
        "/** \n * @constructor \n * @param {number} x */" +
        "var YourFoo = function(x) {};" +
        "/** \n * @constructor \n * @param {number} x */" +
        "var Foo = YourFoo;",
        "actual parameter 1 of Foo does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testMinimalConstructorAnnotation() {
    testTypes("/** @constructor */function Foo(){}");
  }

  @Test
  public void testGoodExtends1() {
    // A minimal @extends example
    testTypes("/** @constructor */function base() {}\n" +
        "/** @constructor\n * @extends {base} */function derived() {}\n");
  }

  @Test
  public void testGoodExtends2() {
    testTypes("/** @constructor\n * @extends base */function derived() {}\n" +
        "/** @constructor */function base() {}\n");
  }

  @Test
  public void testGoodExtends3() {
    testTypes("/** @constructor\n * @extends {Object} */function base() {}\n" +
        "/** @constructor\n * @extends {base} */function derived() {}\n");
  }

  @Test
  public void testGoodExtends4() {
    // Ensure that @extends actually sets the base type of a constructor
    // correctly. Because this isn't part of the human-readable Function
    // definition, we need to crawl the prototype chain (eww).
    Node n = parseAndTypeCheck(
        "var goog = {};\n" +
        "/** @constructor */goog.Base = function(){};\n" +
        "/** @constructor\n" +
        "  * @extends {goog.Base} */goog.Derived = function(){};\n");
    Node subTypeName = n.getLastChild().getLastChild().getFirstChild();
    assertThat(subTypeName.getQualifiedName()).isEqualTo("goog.Derived");

    FunctionType subCtorType =
        (FunctionType) subTypeName.getNext().getJSType();
    assertThat(subCtorType.getInstanceType().toString()).isEqualTo("goog.Derived");

    JSType superType = subCtorType.getPrototype().getImplicitPrototype();
    assertThat(superType.toString()).isEqualTo("goog.Base");
  }

  @Test
  public void testGoodExtends5() {
    // we allow for the extends annotation to be placed first
    testTypes("/** @constructor */function base() {}\n" +
        "/** @extends {base}\n * @constructor */function derived() {}\n");
  }

  @Test
  public void testGoodExtends6() {
    testFunctionType(
        CLOSURE_DEFS +
        "/** @constructor */function base() {}\n" +
        "/** @return {number} */ " +
        "  base.prototype.foo = function() { return 1; };\n" +
        "/** @extends {base}\n * @constructor */function derived() {}\n" +
        "goog.inherits(derived, base);",
        "derived.superClass_.foo",
        "function(this:base): number");
  }

  @Test
  public void testGoodExtends7() {
    testTypes("/** @constructor \n @extends {Base} */ function Sub() {}" +
        "/** @return {number} */ function f() { return (new Sub()).foo; }" +
        "/** @constructor */ function Base() {}" +
        "/** @type {boolean} */ Base.prototype.foo = true;",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testGoodExtends8() {
    testTypes(
        "/** @constructor */ function Super() {}" +
        "Super.prototype.foo = function() {};" +
        "/** @constructor \n * @extends {Super} */ function Sub() {}" +
        "Sub.prototype = new Super();" +
        "/** @override */ Sub.prototype.foo = function() {};");
  }

  @Test
  public void testGoodExtends9() {
    testTypes(
        "/** @constructor */ function Super() {}" +
        "/** @constructor \n * @extends {Super} */ function Sub() {}" +
        "Sub.prototype = new Super();" +
        "/** @return {Super} */ function foo() { return new Sub(); }");
  }

  @Test
  public void testGoodExtends10() {
    testTypes(
        "/** @constructor */ function Super() {}" +
        "/** @param {boolean} x */ Super.prototype.foo = function(x) {};" +
        "/** @constructor \n * @extends {Super} */ function Sub() {}" +
        "Sub.prototype = new Super();" +
        "(new Sub()).foo(0);",
        "actual parameter 1 of Super.prototype.foo " +
        "does not match formal parameter\n" +
        "found   : number\n" +
        "required: boolean");
  }

  @Test
  public void testGoodExtends11() {
    testTypes(
        "/** @constructor \n * @extends {Super} */ function Sub() {}" +
        "/** @constructor \n * @extends {Sub} */ function Sub2() {}" +
        "/** @constructor */ function Super() {}" +
        "/** @param {Super} x */ function foo(x) {}" +
        "foo(new Sub2());");
  }

  @Test
  public void testGoodExtends12() {
    testTypes(
        "/** @constructor \n * @extends {B}  */ function C() {}" +
        "/** @constructor \n * @extends {D}  */ function E() {}" +
        "/** @constructor \n * @extends {C}  */ function D() {}" +
        "/** @constructor \n * @extends {A} */ function B() {}" +
        "/** @constructor */ function A() {}" +
        "/** @param {number} x */ function f(x) {} f(new E());",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : E\n" +
        "required: number");
  }

  @Test
  public void testGoodExtends13() {
    testTypes(
        CLOSURE_DEFS +
        "/** @param {Function} f */ function g(f) {" +
        "  /** @constructor */ function NewType() {};" +
        "  goog.inherits(NewType, f);" +
        "  (new NewType());" +
        "}");
  }

  @Test
  public void testGoodExtends14() {
    testTypesWithCommonExterns(
        CLOSURE_DEFS +
        "/** @constructor */ function OldType() {}" +
        "/** @param {?function(new:OldType)} f */ function g(f) {" +
        "  /**\n" +
        "    * @constructor\n" +
        "    * @extends {OldType}\n" +
        "    */\n" +
        "  function NewType() {};" +
        "  goog.inherits(NewType, f);" +
        "  NewType.prototype.method = function() {" +
        "    NewType.superClass_.foo.call(this);" +
        "  };" +
        "}",
        "Property foo never defined on OldType.prototype");
  }

  @Test
  public void testGoodExtends15() {
    testTypes(
        CLOSURE_DEFS +
        "/** @param {Function} f */ function g(f) {" +
        "  /** @constructor */ function NewType() {};" +
        "  goog.inherits(f, NewType);" +
        "  (new NewType());" +
        "}");
  }

  @Test
  public void testGoodExtends16() {
    testTypes(
        CLOSURE_DEFS +
        "/** @constructor\n" +
        " * @template T */\n" +
        "function C() {}\n" +
        "/** @constructor\n" +
        " * @extends {C<string>} */\n" +
        "function D() {};\n" +
        "goog.inherits(D, C);\n" +
        "(new D())");
  }

  @Test
  public void testGoodExtends17() {
    testTypes(
        CLOSURE_DEFS +
        "/** @constructor */\n" +
        "function C() {}\n" +
        "" +
        "/** @interface\n" +
        " * @template T */\n" +
        "function D() {}\n" +
        "/** @param {T} t */\n" +
        "D.prototype.method;\n" +
        "" +
        "/** @constructor\n" +
        " * @template T\n" +
        " * @extends {C}\n" +
        " * @implements {D<T>} */\n" +
        "function E() {};\n" +
        "goog.inherits(E, C);\n" +
        "/** @override */\n" +
        "E.prototype.method = function(t) {};\n" +
        "" +
        "var e = /** @type {E<string>} */ (new E());\n" +
        "e.method(3);",
        "actual parameter 1 of E.prototype.method does not match formal " +
        "parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testGoodExtends18() {
    testTypes(""
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
        + "}");
  }

  @Test
  public void testGoodExtends19() {
    testTypes(""
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
        + "}");
  }

  @Test
  public void testBadExtends1() {
    testTypes("/** @constructor */function base() {}\n" +
        "/** @constructor\n * @extends {not_base} */function derived() {}\n",
        "Bad type annotation. Unknown type not_base");
  }

  @Test
  public void testBadExtends2() {
    testTypes("/** @constructor */function base() {\n" +
        "/** @type {!Number}*/\n" +
        "this.baseMember = new Number(4);\n" +
        "}\n" +
        "/** @constructor\n" +
        "  * @extends {base} */function derived() {}\n" +
        "/** @param {!String} x*/\n" +
        "function foo(x){ }\n" +
        "/** @type {!derived}*/var y;\n" +
        "foo(y.baseMember);\n",
        "actual parameter 1 of foo does not match formal parameter\n" +
        "found   : Number\n" +
        "required: String");
  }

  @Test
  public void testBadExtends3() {
    testTypes("/** @extends {Object} */function base() {}",
        "@extends used without @constructor or @interface for base");
  }

  @Test
  public void testBadExtends4() {
    // If there's a subclass of a class with a bad extends,
    // we only want to warn about the first one.
    testTypes(
        "/** @constructor \n * @extends {bad} */ function Sub() {}" +
        "/** @constructor \n * @extends {Sub} */ function Sub2() {}" +
        "/** @param {Sub} x */ function foo(x) {}" +
        "foo(new Sub2());",
        "Bad type annotation. Unknown type bad");
  }

  @Test
  public void testBadExtends5() {
    testTypes(""
        + "/** @interface */\n"
        + "var MyInterface = function() {};\n"
        + "MyInterface.prototype = {\n"
        + "  /** @return {number} */\n"
        + "  method: function() {}\n"
        + "}\n"
        + "/** @extends {MyInterface}\n * @interface */\n"
        + "var MyOtherInterface = function() {};\n"
        + "MyOtherInterface.prototype = {\n"
        + "  /** @return {string} \n @override */\n"
        + "  method: function() {}\n"
        + "}",
        ""
        + "mismatch of the method property type and the type of the property "
        + "it overrides from superclass MyInterface\n"
        + "original: function(this:MyInterface): number\n"
        + "override: function(this:MyOtherInterface): string");
  }

  @Test
  public void testBadExtends6() {
    testTypes(""
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
        + "}",
        ""
        + "mismatch of the method property type and the type of the property "
        + "it overrides from superclass MyType\n"
        + "original: function(this:MyType): number\n"
        + "override: function(this:MyOtherType): string");
  }

  @Test
  public void testLateExtends() {
    testTypes(
        CLOSURE_DEFS +
        "/** @constructor */ function Foo() {}\n" +
        "Foo.prototype.foo = function() {};\n" +
        "/** @constructor */function Bar() {}\n" +
        "goog.inherits(Foo, Bar);\n",
        "Missing @extends tag on type Foo");
  }

  @Test
  public void testSuperclassMatch() {
    compiler.getOptions().setCodingConvention(new GoogleCodingConvention());
    testTypes("/** @constructor */ var Foo = function() {};\n" +
        "/** @constructor \n @extends Foo */ var Bar = function() {};\n" +
        "Bar.inherits = function(x){};" +
        "Bar.inherits(Foo);\n");
  }

  @Test
  public void testSuperclassMatchWithMixin() {
    compiler.getOptions().setCodingConvention(new GoogleCodingConvention());
    testTypes("/** @constructor */ var Foo = function() {};\n" +
        "/** @constructor */ var Baz = function() {};\n" +
        "/** @constructor \n @extends Foo */ var Bar = function() {};\n" +
        "Bar.inherits = function(x){};" +
        "Bar.mixin = function(y){};" +
        "Bar.inherits(Foo);\n" +
        "Bar.mixin(Baz);\n");
  }

  @Test
  public void testSuperClassDefinedAfterSubClass1() {
    testTypes(
        "/** @constructor \n * @extends {Base} */ function A() {}" +
        "/** @constructor \n * @extends {Base} */ function B() {}" +
        "/** @constructor */ function Base() {}" +
        "/** @param {A|B} x \n * @return {B|A} */ " +
        "function foo(x) { return x; }");
  }

  @Test
  public void testSuperClassDefinedAfterSubClass2() {
    testTypes(
        "/** @constructor \n * @extends {Base} */ function A() {}" +
        "/** @constructor \n * @extends {Base} */ function B() {}" +
        "/** @param {A|B} x \n * @return {B|A} */ " +
        "function foo(x) { return x; }" +
        "/** @constructor */ function Base() {}");
  }

  // https://github.com/google/closure-compiler/issues/2458
  @Test
  public void testAbstractSpread() {
    testTypesWithCommonExterns(
        lines(
            "/** @abstract */",
            "class X {",
            "  /** @abstract */",
            "  m1() {}",
            "",
            "  m2() {",
            "    return () => this.m1(...[]);",
            "  }",
            "}"));
  }

  @Test
  public void testGoodSuperCall() {
    testTypesWithCommonExterns(
        lines(
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
            ""));
  }

  @Test
  public void testBadSuperCall() {
    testTypesWithCommonExterns(
        lines(
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
            "}"),
        lines(
            "actual parameter 1 of super does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testDirectPrototypeAssignment1() {
    testTypes(
        "/** @constructor */ function Base() {}" +
        "Base.prototype.foo = 3;" +
        "/** @constructor \n * @extends {Base} */ function A() {}" +
        "A.prototype = new Base();" +
        "/** @return {string} */ function foo() { return (new A).foo; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testDirectPrototypeAssignment2() {
    // This ensures that we don't attach property 'foo' onto the Base
    // instance object.
    testTypes(
        "/** @constructor */ function Base() {}" +
        "/** @constructor \n * @extends {Base} */ function A() {}" +
        "A.prototype = new Base();" +
        "A.prototype.foo = 3;" +
        "/** @return {string} */ function foo() { return (new Base).foo; }",
        STRICT_INEXISTENT_PROPERTY); // exists on subtypes, so only reported for strict props
  }

  @Test
  public void testDirectPrototypeAssignment3() {
    // This verifies that the compiler doesn't crash if the user
    // overwrites the prototype of a global variable in a local scope.
    testTypes(
        "/** @constructor */ var MainWidgetCreator = function() {};" +
        "/** @param {Function} ctor */" +
        "function createMainWidget(ctor) {" +
        "  /** @constructor */ function tempCtor() {};" +
        "  tempCtor.prototype = ctor.prototype;" +
        "  MainWidgetCreator.superClass_ = ctor.prototype;" +
        "  MainWidgetCreator.prototype = new tempCtor();" +
        "}");
  }

  @Test
  public void testGoodImplements1() {
    testTypes("/** @interface */function Disposable() {}\n" +
        "/** @implements {Disposable}\n * @constructor */function f() {}");
  }

  @Test
  public void testGoodImplements2() {
    testTypes("/** @interface */function Base1() {}\n" +
        "/** @interface */function Base2() {}\n" +
        "/** @constructor\n" +
        " * @implements {Base1}\n" +
        " * @implements {Base2}\n" +
        " */ function derived() {}");
  }

  @Test
  public void testGoodImplements3() {
    testTypes("/** @interface */function Disposable() {}\n" +
        "/** @constructor \n @implements {Disposable} */function f() {}");
  }

  @Test
  public void testGoodImplements4() {
    testTypes(
        lines(
            "var goog = {};",
            "/** @type {!Function} */",
            "goog.abstractMethod = function() {};",
            "/** @interface */",
            "goog.Disposable = function() {};",
            "goog.Disposable.prototype.dispose = goog.abstractMethod;",
            "/** @implements {goog.Disposable}\n * @constructor */",
            "goog.SubDisposable = function() {};",
            "/** @inheritDoc */",
            "goog.SubDisposable.prototype.dispose = function() {};"));
  }

  @Test
  public void testGoodImplements5() {
    testTypes(
        "/** @interface */\n" +
        "goog.Disposable = function() {};" +
        "/** @type {Function} */" +
        "goog.Disposable.prototype.dispose = function() {};" +
        "/** @implements {goog.Disposable}\n * @constructor */" +
        "goog.SubDisposable = function() {};" +
        "/** @param {number} key \n @override */ " +
        "goog.SubDisposable.prototype.dispose = function(key) {};");
  }

  @Test
  public void testGoodImplements6() {
    testTypes(
        "var myNullFunction = function() {};" +
        "/** @interface */\n" +
        "goog.Disposable = function() {};" +
        "/** @return {number} */" +
        "goog.Disposable.prototype.dispose = myNullFunction;" +
        "/** @implements {goog.Disposable}\n * @constructor */" +
        "goog.SubDisposable = function() {};" +
        "/** @return {number} \n @override */ " +
        "goog.SubDisposable.prototype.dispose = function() { return 0; };");
  }

  @Test
  public void testGoodImplements7() {
    testTypes(
        "var myNullFunction = function() {};" +
        "/** @interface */\n" +
        "goog.Disposable = function() {};" +
        "/** @return {number} */" +
        "goog.Disposable.prototype.dispose = function() {};" +
        "/** @implements {goog.Disposable}\n * @constructor */" +
        "goog.SubDisposable = function() {};" +
        "/** @return {number} \n @override */ " +
        "goog.SubDisposable.prototype.dispose = function() { return 0; };");
  }

  @Test
  public void testGoodImplements8() {
    testTypes(""
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
        + "}");
  }

  @Test
  public void testBadImplements1() {
    testTypes("/** @interface */function Base1() {}\n" +
        "/** @interface */function Base2() {}\n" +
        "/** @constructor\n" +
        " * @implements {nonExistent}\n" +
        " * @implements {Base2}\n" +
        " */ function derived() {}",
        "Bad type annotation. Unknown type nonExistent");
  }

  @Test
  public void testBadImplements2() {
    testTypes("/** @interface */function Disposable() {}\n" +
        "/** @implements {Disposable}\n */function f() {}",
        "@implements used without @constructor for f");
  }

  @Test
  public void testBadImplements3() {
    testTypes(
        lines(
            "var goog = {};",
            "/** @type {!Function} */ goog.abstractMethod = function(){};",
            "/** @interface */ var Disposable = function() {};",
            "Disposable.prototype.method = goog.abstractMethod;",
            "/** @implements {Disposable}\n * @constructor */function f() {}"),
        "property method on interface Disposable is not implemented by type f");
  }

  @Test
  public void testBadImplements4() {
    testTypes("/** @interface */function Disposable() {}\n" +
        "/** @implements {Disposable}\n * @interface */function f() {}",
        "f cannot implement this type; an interface can only extend, " +
        "but not implement interfaces");
  }

  @Test
  public void testBadImplements5() {
    testTypes("/** @interface */function Disposable() {}\n" +
        "/** @type {number} */ Disposable.prototype.bar = function() {};",
        "assignment to property bar of Disposable.prototype\n" +
        "found   : function(): undefined\n" +
        "required: number");
  }

  @Test
  public void testBadImplements6() {
    testClosureTypesMultipleWarnings(
        "/** @interface */function Disposable() {}\n" +
        "/** @type {function()} */ Disposable.prototype.bar = 3;",
        ImmutableList.of(
            "assignment to property bar of Disposable.prototype\n" +
            "found   : number\n" +
            "required: function(): ?",
            "interface members can only be empty property declarations, " +
            "empty functions, or goog.abstractMethod"));
  }

  @Test
  public void testBadImplements7() {
    testTypes(""
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
        + "}",
        ""
        + "mismatch of the method property on type MyClass and the type of the property "
        + "it overrides from interface MyInterface\n"
        + "original: function(): number\n"
        + "override: function(): string");
  }

  @Test
  public void testBadImplements8() {
    testTypes(""
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
        + "}",
        ""
        + "property method already defined on interface MyInterface; "
        + "use @override to override it");
  }

  @Test
  public void testProtoDoesNotRequireOverrideFromInterface() {
    testTypesWithExterns(DEFAULT_EXTERNS + "/** @type {Object} */ Object.prototype.__proto__;",
        "/** @interface */\n"
        + "var MyInterface = function() {};\n"
        + "/** @constructor\n @implements {MyInterface} */\n"
        + "var MySuper = function() {};\n"
        + "/** @constructor\n @extends {MySuper} */\n"
        + "var MyClass = function() {};\n"
        + "MyClass.prototype = {\n"
        + "  __proto__: MySuper.prototype\n"
        + "}",
        (String) null,
        false);
  }

  @Test
  public void testConstructorClassTemplate() {
    testTypes("/** @constructor \n @template S,T */ function A() {}\n");
  }

  @Test
  public void testInterfaceExtends() {
    testTypes("/** @interface */function A() {}\n" +
        "/** @interface \n * @extends {A} */function B() {}\n" +
        "/** @constructor\n" +
        " * @implements {B}\n" +
        " */ function derived() {}");
  }

  @Test
  public void testDontCrashOnDupPropDefinition() {
    testTypes(lines(
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
        "ns.A = function() {};"),
        "variable ns.A redefined, original definition at [testcode]:6");
  }

  @Test
  public void testBadInterfaceExtends1() {
    testTypes("/** @interface \n * @extends {nonExistent} */function A() {}",
        "Bad type annotation. Unknown type nonExistent");
  }

  @Test
  public void testBadInterfaceExtendsNonExistentInterfaces() {
    String js = "/** @interface \n" +
        " * @extends {nonExistent1} \n" +
        " * @extends {nonExistent2} \n" +
        " */function A() {}";
    String[] expectedWarnings = {
      "Bad type annotation. Unknown type nonExistent1",
      "Bad type annotation. Unknown type nonExistent2"
    };
    testTypes(js, expectedWarnings);
  }

  @Test
  public void testBadInterfaceExtends2() {
    testTypes("/** @constructor */function A() {}\n" +
        "/** @interface \n * @extends {A} */function B() {}",
        "B cannot extend this type; interfaces can only extend interfaces");
  }

  @Test
  public void testBadInterfaceExtends3() {
    testTypes("/** @interface */function A() {}\n" +
        "/** @constructor \n * @extends {A} */function B() {}",
        "B cannot extend this type; constructors can only extend constructors");
  }

  @Test
  public void testBadInterfaceExtends4() {
    // TODO(user): This should be detected as an error. Even if we enforce
    // that A cannot be used in the assignment, we should still detect the
    // inheritance chain as invalid.
    testTypes("/** @interface */function A() {}\n" +
        "/** @constructor */function B() {}\n" +
        "B.prototype = A;");
  }

  @Test
  public void testBadInterfaceExtends5() {
    // TODO(user): This should be detected as an error. Even if we enforce
    // that A cannot be used in the assignment, we should still detect the
    // inheritance chain as invalid.
    testTypes("/** @constructor */function A() {}\n" +
        "/** @interface */function B() {}\n" +
        "B.prototype = A;");
  }

  @Test
  public void testBadImplementsAConstructor() {
    testTypes("/** @constructor */function A() {}\n" +
        "/** @constructor \n * @implements {A} */function B() {}",
        "can only implement interfaces");
  }

  @Test
  public void testBadImplementsNonInterfaceType() {
    testTypes("/** @constructor \n * @implements {Boolean} */function B() {}",
        "can only implement interfaces");
  }

  @Test
  public void testBadImplementsNonObjectType() {
    testTypes("/** @constructor \n * @implements {string} */function S() {}",
        "can only implement interfaces");
  }

  @Test
  public void testBadImplementsDuplicateInterface1() {
    // verify that the same base (not templatized) interface cannot be
    // @implemented more than once.
    testTypes(
        "/** @interface \n" +
        " * @template T\n" +
        " */\n" +
        "function Foo() {}\n" +
        "/** @constructor \n" +
        " * @implements {Foo<?>}\n" +
        " * @implements {Foo}\n" +
        " */\n" +
        "function A() {}\n",
        "Cannot @implement the same interface more than once\n" +
        "Repeated interface: Foo");
  }

  @Test
  public void testBadImplementsDuplicateInterface2() {
    // verify that the same base (not templatized) interface cannot be
    // @implemented more than once.
    testTypes(
        "/** @interface \n" +
        " * @template T\n" +
        " */\n" +
        "function Foo() {}\n" +
        "/** @constructor \n" +
        " * @implements {Foo<string>}\n" +
        " * @implements {Foo<number>}\n" +
        " */\n" +
        "function A() {}\n",
        "Cannot @implement the same interface more than once\n" +
        "Repeated interface: Foo");
  }

  @Test
  public void testInterfaceAssignment1() {
    testTypes("/** @interface */var I = function() {};\n" +
        "/** @constructor\n@implements {I} */var T = function() {};\n" +
        "var t = new T();\n" +
        "/** @type {!I} */var i = t;");
  }

  @Test
  public void testInterfaceAssignment2() {
    testTypes("/** @interface */var I = function() {};\n" +
        "/** @constructor */var T = function() {};\n" +
        "var t = new T();\n" +
        "/** @type {!I} */var i = t;",
        "initializing variable\n" +
        "found   : T\n" +
        "required: I");
  }

  @Test
  public void testInterfaceAssignment3() {
    testTypes("/** @interface */var I = function() {};\n" +
        "/** @constructor\n@implements {I} */var T = function() {};\n" +
        "var t = new T();\n" +
        "/** @type {I|number} */var i = t;");
  }

  @Test
  public void testInterfaceAssignment4() {
    testTypes("/** @interface */var I1 = function() {};\n" +
        "/** @interface */var I2 = function() {};\n" +
        "/** @constructor\n@implements {I1} */var T = function() {};\n" +
        "var t = new T();\n" +
        "/** @type {I1|I2} */var i = t;");
  }

  @Test
  public void testInterfaceAssignment5() {
    testTypes("/** @interface */var I1 = function() {};\n" +
        "/** @interface */var I2 = function() {};\n" +
        "/** @constructor\n@implements {I1}\n@implements {I2}*/" +
        "var T = function() {};\n" +
        "var t = new T();\n" +
        "/** @type {I1} */var i1 = t;\n" +
        "/** @type {I2} */var i2 = t;\n");
  }

  @Test
  public void testInterfaceAssignment6() {
    testTypes("/** @interface */var I1 = function() {};\n" +
        "/** @interface */var I2 = function() {};\n" +
        "/** @constructor\n@implements {I1} */var T = function() {};\n" +
        "/** @type {!I1} */var i1 = new T();\n" +
        "/** @type {!I2} */var i2 = i1;\n",
        "initializing variable\n" +
        "found   : I1\n" +
        "required: I2");
  }

  @Test
  public void testInterfaceAssignment7() {
    testTypes("/** @interface */var I1 = function() {};\n" +
        "/** @interface\n@extends {I1}*/var I2 = function() {};\n" +
        "/** @constructor\n@implements {I2}*/var T = function() {};\n" +
        "var t = new T();\n" +
        "/** @type {I1} */var i1 = t;\n" +
        "/** @type {I2} */var i2 = t;\n" +
        "i1 = i2;\n");
  }

  @Test
  public void testInterfaceAssignment8() {
    disableStrictMissingPropertyChecks();

    testTypes("/** @interface */var I = function() {};\n" +
        "/** @type {I} */var i;\n" +
        "/** @type {Object} */var o = i;\n" +
        "new Object().prototype = i.prototype;");
  }

  @Test
  public void testInterfaceAssignment9() {
    testTypes("/** @interface */var I = function() {};\n" +
        "/** @return {I?} */function f() { return null; }\n" +
        "/** @type {!I} */var i = f();\n",
        "initializing variable\n" +
        "found   : (I|null)\n" +
        "required: I");
  }

  @Test
  public void testInterfaceAssignment10() {
    testTypes("/** @interface */var I1 = function() {};\n" +
        "/** @interface */var I2 = function() {};\n" +
        "/** @constructor\n@implements {I2} */var T = function() {};\n" +
        "/** @return {!I1|!I2} */function f() { return new T(); }\n" +
        "/** @type {!I1} */var i1 = f();\n",
        "initializing variable\n" +
        "found   : (I1|I2)\n" +
        "required: I1");
  }

  @Test
  public void testInterfaceAssignment11() {
    testTypes("/** @interface */var I1 = function() {};\n" +
        "/** @interface */var I2 = function() {};\n" +
        "/** @constructor */var T = function() {};\n" +
        "/** @return {!I1|!I2|!T} */function f() { return new T(); }\n" +
        "/** @type {!I1} */var i1 = f();\n",
        "initializing variable\n" +
        "found   : (I1|I2|T)\n" +
        "required: I1");
  }

  @Test
  public void testInterfaceAssignment12() {
    testTypes("/** @interface */var I = function() {};\n" +
              "/** @constructor\n@implements{I}*/var T1 = function() {};\n" +
              "/** @constructor\n@extends {T1}*/var T2 = function() {};\n" +
              "/** @return {I} */function f() { return new T2(); }");
  }

  @Test
  public void testInterfaceAssignment13() {
    testTypes("/** @interface */var I = function() {};\n" +
        "/** @constructor\n@implements {I}*/var T = function() {};\n" +
        "/** @constructor */function Super() {};\n" +
        "/** @return {I} */Super.prototype.foo = " +
        "function() { return new T(); };\n" +
        "/** @constructor\n@extends {Super} */function Sub() {}\n" +
        "/** @override\n@return {T} */Sub.prototype.foo = " +
        "function() { return new T(); };\n");
  }

  @Test
  public void testGetprop1() {
    testTypes("/** @return {void}*/function foo(){foo().bar;}",
        "No properties on this expression\n" +
        "found   : undefined\n" +
        "required: Object");
  }

  @Test
  public void testGetprop2() {
    testTypes("var x = null; x.alert();",
        "No properties on this expression\n" +
        "found   : null\n" +
        "required: Object");
  }

  @Test
  public void testGetprop3() {
    testTypes(
        "/** @constructor */ " +
        "function Foo() { /** @type {?Object} */ this.x = null; }" +
        "Foo.prototype.initX = function() { this.x = {foo: 1}; };" +
        "Foo.prototype.bar = function() {" +
        "  if (this.x == null) { this.initX(); alert(this.x.foo); }" +
        "};");
  }

  @Test
  public void testGetprop4() {
    testTypes("var x = null; x.prop = 3;",
        "No properties on this expression\n" +
        "found   : null\n" +
        "required: Object");
  }

  @Test
  public void testSetprop1() {
    // Create property on struct in the constructor
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() { this.x = 123; }");
  }

  @Test
  public void testSetprop2() {
    // Create property on struct outside the constructor
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "(new Foo()).x = 123;",
              ILLEGAL_PROPERTY_CREATION_MESSAGE);
  }

  @Test
  public void testSetprop3() {
    // Create property on struct outside the constructor
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "(function() { (new Foo()).x = 123; })();",
              ILLEGAL_PROPERTY_CREATION_MESSAGE);
  }

  @Test
  public void testSetprop4() {
    // Assign to existing property of struct outside the constructor
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() { this.x = 123; }\n" +
              "(new Foo()).x = \"asdf\";");
  }

  @Test
  public void testSetprop5() {
    // Create a property on union that includes a struct
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "(true ? new Foo() : {}).x = 123;",
              ILLEGAL_PROPERTY_CREATION_MESSAGE);
  }

  @Test
  public void testSetprop6() {
    // Create property on struct in another constructor
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "/**\n" +
              " * @constructor\n" +
              " * @param{Foo} f\n" +
              " */\n" +
              "function Bar(f) { f.x = 123; }",
             ILLEGAL_PROPERTY_CREATION_MESSAGE);
  }

  @Test
  public void testSetprop7() {
    //Bug b/c we require THIS when creating properties on structs for simplicity
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {\n" +
              "  var t = this;\n" +
              "  t.x = 123;\n" +
              "}",
              ILLEGAL_PROPERTY_CREATION_MESSAGE);
  }

  @Test
  public void testSetprop8() {
    // Create property on struct using DEC
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "(new Foo()).x--;",
              new String[] {
                ILLEGAL_PROPERTY_CREATION_MESSAGE,
                "Property x never defined on Foo"
              });
  }

  @Test
  public void testSetprop9() {
    // Create property on struct using ASSIGN_ADD
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "(new Foo()).x += 123;",
              new String[] {
                ILLEGAL_PROPERTY_CREATION_MESSAGE,
                "Property x never defined on Foo"
              });
  }

  @Test
  public void testSetprop10() {
    // Create property on object literal that is a struct
    testTypes("/** \n" +
              " * @constructor \n" +
              " * @struct \n" +
              " */ \n" +
              "function Square(side) { \n" +
              "  this.side = side; \n" +
              "} \n" +
              "Square.prototype = /** @struct */ {\n" +
              "  area: function() { return this.side * this.side; }\n" +
              "};\n" +
              "Square.prototype.id = function(x) { return x; };");
  }

  @Test
  public void testSetprop11() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "/** @constructor */\n" +
              "function Bar() {}\n" +
              "Bar.prototype = new Foo();\n" +
              "Bar.prototype.someprop = 123;");
  }

  @Test
  public void testSetprop12() {
    // Create property on a constructor of structs (which isn't itself a struct)
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "Foo.someprop = 123;");
  }

  @Test
  public void testSetprop13() {
    // Create static property on struct
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Parent() {}\n" +
              "/**\n" +
              " * @constructor\n" +
              " * @extends {Parent}\n" +
              " */\n" +
              "function Kid() {}\n" +
              "Kid.prototype.foo = 123;\n" +
              "var x = (new Kid()).foo;");
  }

  @Test
  public void testSetprop14() {
    // Create static property on struct
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Top() {}\n" +
              "/**\n" +
              " * @constructor\n" +
              " * @extends {Top}\n" +
              " */\n" +
              "function Mid() {}\n" +
              "/** blah blah */\n" +
              "Mid.prototype.foo = function() { return 1; };\n" +
              "/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " * @extends {Mid}\n" +
              " */\n" +
              "function Bottom() {}\n" +
              "/** @override */\n" +
              "Bottom.prototype.foo = function() { return 3; };");
  }

  @Test
  public void testSetprop15() {
    // Create static property on struct
    testTypes(
        "/** @interface */\n" +
        "function Peelable() {};\n" +
        "/** @return {undefined} */\n" +
        "Peelable.prototype.peel;\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @struct\n" +
        " */\n" +
        "function Fruit() {};\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Fruit}\n" +
        " * @implements {Peelable}\n" +
        " */\n" +
        "function Banana() { };\n" +
        "function f() {};\n" +
        "/** @override */\n" +
        "Banana.prototype.peel = f;");
  }

  @Test
  public void testGetpropDict1() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @dict\n" +
              " */" +
              "function Dict1(){ this['prop'] = 123; }" +
              "/** @param{Dict1} x */" +
              "function takesDict(x) { return x.prop; }",
              "Cannot do '.' access on a dict");
  }

  @Test
  public void testGetpropDict2() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @dict\n" +
              " */" +
              "function Dict1(){ this['prop'] = 123; }" +
              "/**\n" +
              " * @constructor\n" +
              " * @extends {Dict1}\n" +
              " */" +
              "function Dict1kid(){ this['prop'] = 123; }" +
              "/** @param{Dict1kid} x */" +
              "function takesDict(x) { return x.prop; }",
              "Cannot do '.' access on a dict");
  }

  @Test
  public void testGetpropDict3() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @dict\n" +
              " */" +
              "function Dict1() { this['prop'] = 123; }" +
              "/** @constructor */" +
              "function NonDict() { this.prop = 321; }" +
              "/** @param{(NonDict|Dict1)} x */" +
              "function takesDict(x) { return x.prop; }",
              "Cannot do '.' access on a dict");
  }

  @Test
  public void testGetpropDict4() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @dict\n" +
              " */" +
              "function Dict1() { this['prop'] = 123; }" +
              "/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */" +
              "function Struct1() { this.prop = 123; }" +
              "/** @param{(Struct1|Dict1)} x */" +
              "function takesNothing(x) { return x.prop; }",
              "Cannot do '.' access on a dict");
  }

  @Test
  public void testGetpropDict5() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @dict\n" +
              " */" +
              "function Dict1(){ this.prop = 123; }",
              "Cannot do '.' access on a dict");
  }

  @Test
  public void testGetpropDict6() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @dict\n" +
              " */\n" +
              "function Foo() {}\n" +
              "function Bar() {}\n" +
              "Bar.prototype = new Foo();\n" +
              "Bar.prototype.someprop = 123;\n",
              "Cannot do '.' access on a dict");
  }

  @Test
  public void testGetpropDict7() {
    testTypes("(/** @dict */ {'x': 123}).x = 321;",
              "Cannot do '.' access on a dict");
  }

  @Test
  public void testGetelemStruct1() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */" +
              "function Struct1(){ this.prop = 123; }" +
              "/** @param{Struct1} x */" +
              "function takesStruct(x) {" +
              "  var z = x;" +
              "  return z['prop'];" +
              "}",
              "Cannot do '[]' access on a struct");
  }

  @Test
  public void testGetelemStruct2() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */" +
              "function Struct1(){ this.prop = 123; }" +
              "/**\n" +
              " * @constructor\n" +
              " * @extends {Struct1}" +
              " */" +
              "function Struct1kid(){ this.prop = 123; }" +
              "/** @param{Struct1kid} x */" +
              "function takesStruct2(x) { return x['prop']; }",
              "Cannot do '[]' access on a struct");
  }

  @Test
  public void testGetelemStruct3() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */" +
              "function Struct1(){ this.prop = 123; }" +
              "/**\n" +
              " * @constructor\n" +
              " * @extends {Struct1}\n" +
              " */" +
              "function Struct1kid(){ this.prop = 123; }" +
              "var x = (new Struct1kid())['prop'];",
              "Cannot do '[]' access on a struct");
  }

  @Test
  public void testGetelemStruct4() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */" +
              "function Struct1() { this.prop = 123; }" +
              "/** @constructor */" +
              "function NonStruct() { this.prop = 321; }" +
              "/** @param{(NonStruct|Struct1)} x */" +
              "function takesStruct(x) { return x['prop']; }",
              "Cannot do '[]' access on a struct");
  }

  @Test
  public void testGetelemStruct5() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */" +
              "function Struct1() { this.prop = 123; }" +
              "/**\n" +
              " * @constructor\n" +
              " * @dict\n" +
              " */" +
              "function Dict1() { this['prop'] = 123; }" +
              "/** @param{(Struct1|Dict1)} x */" +
              "function takesNothing(x) { return x['prop']; }",
              "Cannot do '[]' access on a struct");
  }

  @Test
  public void testGetelemStruct6() {
    // By casting Bar to Foo, the illegal bracket access is not detected
    testTypes("/** @interface */ function Foo(){}\n" +
              "/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " * @implements {Foo}\n" +
              " */" +
              "function Bar(){ this.x = 123; }\n" +
              "var z = /** @type {Foo} */(new Bar())['x'];");
  }

  @Test
  public void testGetelemStruct7() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "/** @constructor */\n" +
              "function Bar() {}\n" +
              "Bar.prototype = new Foo();\n" +
              "Bar.prototype['someprop'] = 123;\n",
              "Cannot do '[]' access on a struct");
  }

  @Test
  public void testGetelemStruct_noErrorForSettingWellKnownSymbol() {
    testTypesWithCommonExterns("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "Foo.prototype[Symbol.iterator] = 123;\n");
  }

  @Test
  public void testGetelemStruct_noErrorForGettingWellKnownSymbol() {
    testTypesWithCommonExterns("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "/** @param {!Foo} foo */\n" +
              "function getIterator(foo) { return foo[Symbol.iterator](); }\n");
  }

  @Test
  public void testInOnStruct() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */" +
              "function Foo() {}\n" +
              "if ('prop' in (new Foo())) {}",
              "Cannot use the IN operator with structs");
  }

  @Test
  public void testForinOnStruct() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */" +
              "function Foo() {}\n" +
              "for (var prop in (new Foo())) {}",
              "Cannot use the IN operator with structs");
  }

  @Test
  public void testArrayLegacyAccess1() {
    String externs = DEFAULT_EXTERNS.replace(
        " * @implements {IArrayLike<T>}",
        lines(
          " * @implements {IObject<?, T>} ",
          " * @implements {IArrayLike<T>} "));
    checkState(DEFAULT_EXTERNS.length() != externs.length());
    testTypesWithExterns(externs, "var a = []; var b = a['hi'];");
  }

  @Test
  public void testIArrayLikeAccess1() {
    testTypesWithCommonExterns(
        lines(
            "/** ",
            " * @param {!IArrayLike<T>} x",
            " * @return {T}",
            " * @template T",
            "*/",
            "function f(x) { return x[0]; }",
            "function g(/** !Array<string> */ x) {",
            "  var /** null */ y = f(x);",
            "}"),
        "initializing variable\n"
        + "found   : string\n"
        + "required: null");
  }

  @Test
  public void testIArrayLikeAccess2() {
    testTypesWithCommonExterns(
        lines(
            "/** ",
            " * @param {!IArrayLike<T>} x",
            " * @return {T}",
            " * @template T",
            "*/",
            "function f(x) { return x[0]; }",
            "function g(/** !IArrayLike<string> */ x) {",
            "  var /** null */ y = f(x);",
            "}"),
        "initializing variable\n"
        + "found   : string\n"
        + "required: null");
  }

  // These test the template types in the built-in Iterator/Iterable/Generator are set up correctly
  @Test
  public void testIteratorAccess1() {
    testTypesWithCommonExterns(
        lines(
            "/** ",
            " * @param {!Iterator<T>} x",
            " * @return {T}",
            " * @template T",
            "*/",
            "function f(x) { return x[0]; }",
            "function g(/** !Generator<string> */ x) {",
            "  var /** null */ y = f(x);",
            "}"),
        "initializing variable\n" + "found   : string\n" + "required: null");
  }

  @Test
  public void testIterableAccess1() {
    testTypesWithCommonExterns(
        lines(
            "/** ",
            " * @param {!Iterable<T>} x",
            " * @return {T}",
            " * @template T",
            "*/",
            "function f(x) { return x[0]; }",
            "function g(/** !Generator<string> */ x) {",
            "  var /** null */ y = f(x);",
            "}"),
        "initializing variable\n" + "found   : string\n" + "required: null");
  }

  @Test
  public void testIteratorIterableAccess1() {
    testTypesWithCommonExterns(
        lines(
            "/** ",
            " * @param {!IteratorIterable<T>} x",
            " * @return {T}",
            " * @template T",
            "*/",
            "function f(x) { return x[0]; }",
            "function g(/** !Generator<string> */ x) {",
            "  var /** null */ y = f(x);",
            "}"),
        "initializing variable\n" + "found   : string\n" + "required: null");
  }

  @Test
  public void testArrayAccess1() {
    testTypesWithCommonExterns("var a = []; var b = a['hi'];",
        "restricted index type\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testArrayAccess2() {
    testTypesWithCommonExterns("var a = []; var b = a[[1,2]];",
        "restricted index type\n" +
        "found   : Array\n" +
        "required: number");
  }

  @Test
  public void testArrayAccess3() {
    testTypesWithCommonExterns("var bar = [];" +
        "/** @return {void} */function baz(){};" +
        "var foo = bar[baz()];",
        "restricted index type\n" +
        "found   : undefined\n" +
        "required: number");
  }

  @Test
  public void testArrayAccess4() {
    testTypesWithCommonExterns("/**@return {!Array}*/function foo(){};var bar = foo()[foo()];",
        "restricted index type\n" +
        "found   : Array\n" +
        "required: number");
  }

  @Test
  public void testArrayAccess6() {
    testTypesWithCommonExterns("var bar = null[1];",
        "only arrays or objects can be accessed\n" +
        "found   : null\n" +
        "required: Object");
  }

  @Test
  public void testArrayAccess7() {
    testTypesWithCommonExterns("var bar = void 0; bar[0];",
        "only arrays or objects can be accessed\n" +
        "found   : undefined\n" +
        "required: Object");
  }

  @Test
  public void testArrayAccess8() {
    // Verifies that we don't emit two warnings, because
    // the var has been dereferenced after the first one.
    testTypesWithCommonExterns("var bar = void 0; bar[0]; bar[1];",
        "only arrays or objects can be accessed\n" +
        "found   : undefined\n" +
        "required: Object");
  }

  @Test
  public void testArrayAccess9() {
    testTypesWithCommonExterns("/** @return {?Array} */ function f() { return []; }" +
        "f()[{}]",
        "restricted index type\n" +
        "found   : {}\n" +
        "required: number");
  }

  @Test
  public void testPropAccess() {
    testTypes("/** @param {*} x */var f = function(x) {\n" +
        "var o = String(x);\n" +
        "if (typeof o['a'] != 'undefined') { return o['a']; }\n" +
        "return null;\n" +
        "};");
  }

  @Test
  public void testPropAccess2() {
    testTypes("var bar = void 0; bar.baz;",
        "No properties on this expression\n" +
        "found   : undefined\n" +
        "required: Object");
  }

  @Test
  public void testPropAccess3() {
    // Verifies that we don't emit two warnings, because
    // the var has been dereferenced after the first one.
    testTypes("var bar = void 0; bar.baz; bar.bax;",
        "No properties on this expression\n" +
        "found   : undefined\n" +
        "required: Object");
  }

  @Test
  public void testPropAccess4() {
    testTypes("/** @param {*} x */ function f(x) { return x['hi']; }");
  }

  @Test
  public void testSwitchCase1() {
    testTypes(
        "/**@type {number}*/var a;" + "/**@type {string}*/var b;" + "switch(a){case b:;}",
        "case expression doesn't match switch\n" + "found   : string\n" + "required: number");
  }

  @Test
  public void testSwitchCase2() {
    testTypes("var a = null; switch (typeof a) { case 'foo': }");
  }

  @Test
  public void testVar1() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope("/** @type {(string|null)} */var a = null");

    assertTypeEquals(
        createUnionType(getNativeStringType(), getNativeNullType()), p.scope.getVar("a").getType());
  }

  @Test
  public void testVar2() {
    testTypes("/** @type {Function} */ var a = function(){}");
  }

  @Test
  public void testVar3() {
    TypeCheckResult p = parseAndTypeCheckWithScope("var a = 3;");

    assertTypeEquals(getNativeNumberType(), p.scope.getVar("a").getType());
  }

  @Test
  public void testVar4() {
    TypeCheckResult p = parseAndTypeCheckWithScope(
        "var a = 3; a = 'string';");

    assertTypeEquals(
        createUnionType(getNativeStringType(), getNativeNumberType()),
        p.scope.getVar("a").getType());
  }

  @Test
  public void testVar5() {
    testTypes("var goog = {};" +
        "/** @type {string} */goog.foo = 'hello';" +
        "/** @type {number} */var a = goog.foo;",
        "initializing variable\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testVar6() {
    testTypes(
        "function f() {" +
        "  return function() {" +
        "    /** @type {!Date} */" +
        "    var a = 7;" +
        "  };" +
        "}",
        "initializing variable\n" +
        "found   : number\n" +
        "required: Date");
  }

  @Test
  public void testVar7() {
    testTypes("/** @type {number} */var a, b;",
        "declaration of multiple variables with shared type information");
  }

  @Test
  public void testVar8() {
    testTypes("var a, b;");
  }

  @Test
  public void testVar9() {
    testTypes("/** @enum */var a;",
        "enum initializer must be an object literal or an enum");
  }

  @Test
  public void testVar10() {
    testTypes("/** @type {!Number} */var foo = 'abc';",
        "initializing variable\n" +
        "found   : string\n" +
        "required: Number");
  }

  @Test
  public void testVar11() {
    testTypes("var /** @type {!Date} */foo = 'abc';",
        "initializing variable\n" +
        "found   : string\n" +
        "required: Date");
  }

  @Test
  public void testVar12() {
    testTypes("var /** @type {!Date} */foo = 'abc', " +
        "/** @type {!RegExp} */bar = 5;",
        new String[] {
        "initializing variable\n" +
        "found   : string\n" +
        "required: Date",
        "initializing variable\n" +
        "found   : number\n" +
        "required: RegExp"});
  }

  @Test
  public void testVar13() {
    // this caused an NPE
    testTypes("var /** @type {number} */a,a;");
  }

  @Test
  public void testVar14() {
    testTypes("/** @return {number} */ function f() { var x; return x; }",
        "inconsistent return type\n" +
        "found   : undefined\n" +
        "required: number");
  }

  @Test
  public void testVar15() {
    testTypes("/** @return {number} */" +
        "function f() { var x = x || {}; return x; }",
        "inconsistent return type\n" +
        "found   : {}\n" +
        "required: number");
  }

  @Test
  public void testAssign1() {
    testTypes("var goog = {};" +
        "/** @type {number} */goog.foo = 'hello';",
        "assignment to property foo of goog\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testAssign2() {
    testTypes("var goog = {};" +
        "/** @type {number}  */goog.foo = 3;" +
        "goog.foo = 'hello';",
        "assignment to property foo of goog\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testAssign3() {
    testTypes("var goog = {};" +
        "/** @type {number}  */goog.foo = 3;" +
        "goog.foo = 4;");
  }

  @Test
  public void testAssign4() {
    testTypes("var goog = {};" +
        "goog.foo = 3;" +
        "goog.foo = 'hello';");
  }

  @Test
  public void testAssignInference() {
    testTypes(
        "/**" +
        " * @param {Array} x" +
        " * @return {number}" +
        " */" +
        "function f(x) {" +
        "  var y = null;" +
        "  y = x[0];" +
        "  if (y == null) { return 4; } else { return 6; }" +
        "}");
  }

  @Test
  public void testOr1() {
    testTypes("/** @type {number}  */var a;" +
        "/** @type {number}  */var b;" +
        "a + b || undefined;");
  }

  @Test
  public void testOr2() {
    testTypes("/** @type {number}  */var a;" +
        "/** @type {number}  */var b;" +
        "/** @type {number}  */var c = a + b || undefined;",
        "initializing variable\n" +
        "found   : (number|undefined)\n" +
        "required: number");
  }

  @Test
  public void testOr3() {
    testTypes("/** @type {(number|undefined)} */var a;" + "/** @type {number}  */var c = a || 3;");
  }

  /**
   * Test that type inference continues with the right side, when no short-circuiting is possible.
   * See bugid 1205387 for more details.
   */
  @Test
  public void testOr4() {
     testTypes("/**@type {number} */var x;x=null || \"a\";",
         "assignment\n" +
         "found   : string\n" +
         "required: number");
  }

  /** @see #testOr4() */
  @Test
  public void testOr5() {
     testTypes("/**@type {number} */var x;x=undefined || \"a\";",
         "assignment\n" +
         "found   : string\n" +
         "required: number");
  }

  @Test
  public void testOr6() {
    testTypes(
        lines(
            "/** @param {!Array=} opt_x */",
            "function removeDuplicates(opt_x) {",
            "  var x = opt_x || [];",
            "  var /** undefined */ y = x;",
            "}"),
        lines(
            "initializing variable",
            "found   : Array",
            "required: undefined"));
  }

  @Test
  public void testAnd1() {
    testTypes(
        "/** @type {number}  */var a;" + "/** @type {number}  */var b;" + "a + b && undefined;");
  }

  @Test
  public void testAnd2() {
    testTypes(
        "/** @type {number}  */var a;"
            + "/** @type {number}  */var b;"
            + "/** @type {number}  */var c = a + b && undefined;",
        "initializing variable\n" + "found   : (number|undefined)\n" + "required: number");
  }

  @Test
  public void testAnd3() {
    testTypes(
        "/** @type {(!Array|undefined)} */var a;"
            + "/** @type {number}  */var c = a && undefined;",
        "initializing variable\n" + "found   : undefined\n" + "required: number");
  }

  @Test
  public void testAnd4() {
    testTypes(
        "/** @param {number} x */function f(x){};\n"
            + "/** @type {null}  */var x; /** @type {number?} */var y;\n"
            + "if (x && y) { f(y) }");
  }

  @Test
  public void testAnd5() {
    testTypes("/** @param {number} x\n@param {string} y*/function f(x,y){};\n" +
        "/** @type {number?} */var x; /** @type {string?} */var y;\n" +
        "if (x && y) { f(x, y) }");
  }

  @Test
  public void testAnd6() {
    testTypes("/** @param {number} x */function f(x){};\n" +
        "/** @type {number|undefined} */var x;\n" +
        "if (x && f(x)) { f(x) }");
  }

  @Test
  public void testAnd7() {
    // TODO(user): a deterministic warning should be generated for this
    // case since x && x is always false. The implementation of this requires
    // a more precise handling of a null value within a variable's type.
    // Currently, a null value defaults to ? which passes every check.
    testTypes("/** @type {null} */var x; if (x && x) {}");
  }

  @Test
  public void testAnd8() {
    testTypes(
        "function f(/** (null | number | string) */ x) {\n" +
        "  (x && (typeof x === 'number')) && takesNum(x);\n" +
        "}\n" +
        "function takesNum(/** number */ n) {}");
  }

  @Test
  public void testAnd9() {
    testTypes(
        "function f(/** (number|string|null) */ x) {\n" +
        "  if (x && typeof x === 'number') {\n" +
        "    takesNum(x);\n" +
        "  }\n" +
        "}\n" +
        "function takesNum(/** number */ x) {}");
  }

  @Test
  public void testAnd10() {
    testTypes(
        "function f(/** (null | number | string) */ x) {\n" +
        "  (x && (typeof x === 'string')) && takesNum(x);\n" +
        "}\n" +
        "function takesNum(/** number */ n) {}",
        "actual parameter 1 of takesNum does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testHook() {
    testTypes("/**@return {void}*/function foo(){ var x=foo()?a:b; }");
  }

  @Test
  public void testHookRestrictsType1() {
    testTypes("/** @return {(string|null)} */" +
        "function f() { return null;}" +
        "/** @type {(string|null)} */ var a = f();" +
        "/** @type {string} */" +
        "var b = a ? a : 'default';");
  }

  @Test
  public void testHookRestrictsType2() {
    testTypes("/** @type {String} */" +
        "var a = null;" +
        "/** @type {null} */" +
        "var b = a ? null : a;");
  }

  @Test
  public void testHookRestrictsType3() {
    testTypes("/** @type {String} */" +
        "var a;" +
        "/** @type {null} */" +
        "var b = (!a) ? a : null;");
  }

  @Test
  public void testHookRestrictsType4() {
    testTypes("/** @type {(boolean|undefined)} */" +
        "var a;" +
        "/** @type {boolean} */" +
        "var b = a != null ? a : true;");
  }

  @Test
  public void testHookRestrictsType5() {
    testTypes("/** @type {(boolean|undefined)} */" +
        "var a;" +
        "/** @type {(undefined)} */" +
        "var b = a == null ? a : undefined;");
  }

  @Test
  public void testHookRestrictsType6() {
    testTypes("/** @type {(number|null|undefined)} */" +
        "var a;" +
        "/** @type {number} */" +
        "var b = a == null ? 5 : a;");
  }

  @Test
  public void testHookRestrictsType7() {
    testTypes("/** @type {(number|null|undefined)} */" +
        "var a;" +
        "/** @type {number} */" +
        "var b = a == undefined ? 5 : a;");
  }

  @Test
  public void testWhileRestrictsType1() {
    testTypes("/** @param {null} x */ function g(x) {}" +
        "/** @param {number?} x */\n" +
        "function f(x) {\n" +
        "while (x) {\n" +
        "if (g(x)) { x = 1; }\n" +
        "x = x-1;\n}\n}",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : number\n" +
        "required: null");
  }

  @Test
  public void testWhileRestrictsType2() {
    testTypes("/** @param {number?} x\n@return {number}*/\n" +
        "function f(x) {\n/** @type {number} */var y = 0;" +
        "while (x) {\n" +
        "y = x;\n" +
        "x = x-1;\n}\n" +
        "return y;}");
  }

  @Test
  public void testHigherOrderFunctions1() {
    testTypes(
        "/** @type {function(number)} */var f;" +
        "f(true);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testHigherOrderFunctions2() {
    testTypes(
        "/** @type {function():!Date} */var f;" + "/** @type {boolean} */var a = f();",
        "initializing variable\n" + "found   : Date\n" + "required: boolean");
  }

  @Test
  public void testHigherOrderFunctions3() {
    testTypes(
        "/** @type {function(this:Array):Date} */var f; new f",
        "cannot instantiate non-constructor");
  }

  @Test
  public void testHigherOrderFunctions4() {
    testTypes(
        "/** @type {function(this:Array, ...number):Date} */var f; new f",
        "cannot instantiate non-constructor");
  }

  @Test
  public void testHigherOrderFunctions5() {
    testTypes(
        lines(
            "/** @param {number} x */ function g(x) {}",
            "/** @type {function(new:Array, ...number):Date} */ var f;",
            "g(new f());"),
        lines(
            "actual parameter 1 of g does not match formal parameter",
            "found   : Array",
            "required: number"));
  }

  @Test
  public void testConstructorAlias1() {
    testTypes(
        "/** @constructor */ var Foo = function() {};" +
        "/** @type {number} */ Foo.prototype.bar = 3;" +
        "/** @constructor */ var FooAlias = Foo;" +
        "/** @return {string} */ function foo() { " +
        "  return (new FooAlias()).bar; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testConstructorAlias2() {
    testTypes(
        "/** @constructor */ var Foo = function() {};" +
        "/** @constructor */ var FooAlias = Foo;" +
        "/** @type {number} */ FooAlias.prototype.bar = 3;" +
        "/** @return {string} */ function foo() { " +
        "  return (new Foo()).bar; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testConstructorAlias3() {
    testTypes(
        "/** @constructor */ var Foo = function() {};" +
        "/** @type {number} */ Foo.prototype.bar = 3;" +
        "/** @constructor */ var FooAlias = Foo;" +
        "/** @return {string} */ function foo() { " +
        "  return (new FooAlias()).bar; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testConstructorAlias4() {
    testTypes(
        "/** @constructor */ var Foo = function() {};" +
        "var FooAlias = Foo;" +
        "/** @type {number} */ FooAlias.prototype.bar = 3;" +
        "/** @return {string} */ function foo() { " +
        "  return (new Foo()).bar; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testConstructorAlias5() {
    testTypes(
        "/** @constructor */ var Foo = function() {};" +
        "/** @constructor */ var FooAlias = Foo;" +
        "/** @return {FooAlias} */ function foo() { " +
        "  return new Foo(); }");
  }

  @Test
  public void testConstructorAlias6() {
    testTypes(
        "/** @constructor */ var Foo = function() {};" +
        "/** @constructor */ var FooAlias = Foo;" +
        "/** @return {Foo} */ function foo() { " +
        "  return new FooAlias(); }");
  }

  @Test
  public void testConstructorAlias7() {
    testTypes(
        "var goog = {};" +
        "/** @constructor */ goog.Foo = function() {};" +
        "/** @constructor */ goog.FooAlias = goog.Foo;" +
        "/** @return {number} */ function foo() { " +
        "  return new goog.FooAlias(); }",
        "inconsistent return type\n" +
        "found   : goog.Foo\n" +
        "required: number");
  }

  @Test
  public void testConstructorAlias8() {
    testTypes(
        "var goog = {};" +
        "/**\n * @param {number} x \n * @constructor */ " +
        "goog.Foo = function(x) {};" +
        "/**\n * @param {number} x \n * @constructor */ " +
        "goog.FooAlias = goog.Foo;" +
        "/** @return {number} */ function foo() { " +
        "  return new goog.FooAlias(1); }",
        "inconsistent return type\n" +
        "found   : goog.Foo\n" +
        "required: number");
  }

  @Test
  public void testConstructorAlias9() {
    testTypes(
        "var goog = {};" +
        "/**\n * @param {number} x \n * @constructor */ " +
        "goog.Foo = function(x) {};" +
        "/** @constructor */ goog.FooAlias = goog.Foo;" +
        "/** @return {number} */ function foo() { " +
        "  return new goog.FooAlias(1); }",
        "inconsistent return type\n" +
        "found   : goog.Foo\n" +
        "required: number");
  }

  @Test
  public void testConstructorAlias10() {
    testTypes(
        "/**\n * @param {number} x \n * @constructor */ " +
        "var Foo = function(x) {};" +
        "/** @constructor */ var FooAlias = Foo;" +
        "/** @return {number} */ function foo() { " +
        "  return new FooAlias(1); }",
        "inconsistent return type\n" +
        "found   : Foo\n" +
        "required: number");
  }

  @Test
  public void testConstructorAlias11() {
    testTypes(
        "/**\n * @param {number} x \n * @constructor */ " +
        "var Foo = function(x) {};" +
        "/** @const */ var FooAlias = Foo;" +
        "/** @const */ var FooAlias2 = FooAlias;" +
        "/** @return {FooAlias2} */ function foo() { " +
        "  return 1; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: (Foo|null)");
  }

  @Test
  public void testConstructorAliasWithBadAnnotation1() {
    testTypes(
        lines(
            "/** @constructor */ function Foo() {}",
            "/** @record */ var Bar = Foo;"),
        "Annotation @record on Bar incompatible with aliased type.");
  }

  @Test
  public void testConstructorAliasWithBadAnnotation2() {
    testTypes(
        lines(
            "/** @constructor */ function Foo() {}",
            "/** @interface */ var Bar = Foo;"),
        "Annotation @interface on Bar incompatible with aliased type.");
  }

  @Test
  public void testConstructorAliasWithBadAnnotation3() {
    testTypes(
        lines(
            "/** @interface */ function Foo() {}",
            "/** @record */ var Bar = Foo;"),
        "Annotation @record on Bar incompatible with aliased type.");
  }

  @Test
  public void testConstructorAliasWithBadAnnotation4() {
    testTypes(
        lines(
            "/** @interface */ function Foo() {}",
            "/** @constructor */ var Bar = Foo;"),
        "Annotation @constructor on Bar incompatible with aliased type.");
  }

  @Test
  public void testConstAliasedTypeCastInferredCorrectly1() {
    testTypesWithExterns(
        "// no externs",
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "/** @return {number} */",
            "Foo.prototype.foo = function() {};",
            "",
            "/** @const */ var FooAlias = Foo;",
            "",
            "var x = /** @type {!FooAlias} */ ({});",
            "var /** null */ n = x.foo();"),
        lines("initializing variable",
            "found   : number",
            "required: null"),
        /* isError= */ false);
  }

  @Test
  public void testConstAliasedTypeCastInferredCorrectly2() {
    testTypesWithExterns(
        "// no externs",
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "/** @return {number} */",
            "Foo.prototype.foo = function() {};",
            "",
            "var ns = {};",
            "/** @const */ ns.FooAlias = Foo;",
            "",
            "var x = /** @type {!ns.FooAlias} */ ({});",
            "var /** null */ n = x.foo();"),
        lines("initializing variable",
            "found   : number",
            "required: null"),
        /* isError= */ false);
  }

  @Test
  public void testConstructorAliasedTypeCastInferredCorrectly() {
    testTypesWithExterns(
        "// no externs",
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "/** @return {number} */",
            "Foo.prototype.foo = function() {};",
            "",
            "/** @constructor */ var FooAlias = Foo;",
            "",
            "var x = /** @type {!FooAlias} */ ({});",
            "var /** null */ n = x.foo();"),
        lines("initializing variable",
            "found   : number",
            "required: null"),
        /* isError= */ false);
  }

  @Test
  public void testAliasedTypedef1() {
    testTypes(
        lines(
            "/** @typedef {string} */ var original;",
            "/** @const */ var alias = original;",
            "/** @type {alias} */ var x;"));
  }

  @Test
  public void testAliasedTypedef2() {
    testTypes(
        lines(
            "/** @const */ var ns = {};",
            "/** @typedef {string} */ var original;",
            "/** @const */ ns.alias = original;",
            "/** @type {ns.alias} */ var x;"));
  }

  @Test
  public void testAliasedTypedef3() {
    testTypes(
        lines(
            "/** @typedef {string} */ var original;",
            "/** @const */ var alias = original;",
            "/** @return {number} */ var f = function(/** alias */ x) { return x; }"),
        "inconsistent return type\n"
        + "found   : string\n"
        + "required: number");
  }

  @Test
  public void testAliasedTypedef4() {
    testTypes(
        lines(
            "/** @const */ var ns = {};",
            "/** @typedef {string} */ var original;",
            "/** @const */ ns.alias = original;",
            "/** @return {number} */ var f = function(/** ns.alias */ x) { return x; }"),
        "inconsistent return type\n"
        + "found   : string\n"
        + "required: number");
  }

  @Test
  public void testAliasedNonTypedef() {
    testTypes(
        lines(
            "/** @type {string} */ var notTypeName;",
            "/** @const */ var alias = notTypeName;",
            "/** @type {alias} */ var x;"),
        "Bad type annotation. Unknown type alias");
  }

  @Test
  public void testConstStringKeyDoesntCrash() {
    testTypesWithExterns(
        "// no externs",
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "var ns = {",
            "  /** @const */ FooAlias: Foo",
            "};"),
        (String) null,
        /* isError= */ false);
  }

  @Test
  public void testClosure1() {
    testClosureTypes(
        CLOSURE_DEFS
            + "/** @type {string|undefined} */var a;"
            + "/** @type {string} */"
            + "var b = goog.isDef(a) ? a : 'default';",
        null);
  }

  @Test
  public void testClosure2() {
    testClosureTypes(
        CLOSURE_DEFS
            + "/** @type {string?} */var a;"
            + "/** @type {string} */"
            + "var b = goog.isNull(a) ? 'default' : a;",
        null);
  }

  @Test
  public void testClosure3() {
    testClosureTypes(
        CLOSURE_DEFS
            + "/** @type {string|null|undefined} */var a;"
            + "/** @type {string} */"
            + "var b = goog.isDefAndNotNull(a) ? a : 'default';",
        null);
  }

  @Test
  public void testClosure4() {
    testClosureTypes(
        CLOSURE_DEFS
            + "/** @type {string|undefined} */var a;"
            + "/** @type {string} */"
            + "var b = !goog.isDef(a) ? 'default' : a;",
        null);
  }

  @Test
  public void testClosure5() {
    testClosureTypes(
        CLOSURE_DEFS
            + "/** @type {string?} */var a;"
            + "/** @type {string} */"
            + "var b = !goog.isNull(a) ? a : 'default';",
        null);
  }

  @Test
  public void testClosure6() {
    testClosureTypes(
        CLOSURE_DEFS
            + "/** @type {string|null|undefined} */var a;"
            + "/** @type {string} */"
            + "var b = !goog.isDefAndNotNull(a) ? 'default' : a;",
        null);
  }

  @Test
  public void testClosure7() {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @type {string|null|undefined} */ var a = foo();" +
        "/** @type {number} */" +
        "var b = goog.asserts.assert(a);",
        "initializing variable\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testReturn1() {
    testTypes("/**@return {void}*/function foo(){ return 3; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: undefined");
  }

  @Test
  public void testReturn2() {
    testTypes("/**@return {!Number}*/function foo(){ return; }",
        "inconsistent return type\n" +
        "found   : undefined\n" +
        "required: Number");
  }

  @Test
  public void testReturn3() {
    testTypes("/**@return {!Number}*/function foo(){ return 'abc'; }",
        "inconsistent return type\n" +
        "found   : string\n" +
        "required: Number");
  }

  @Test
  public void testReturn4() {
    testTypes("/**@return {!Number}\n*/\n function a(){return new Array();}",
        "inconsistent return type\n" +
        "found   : Array\n" +
        "required: Number");
  }

  @Test
  public void testReturn5() {
    testTypes(
        lines(
            "/**", //
            " * @param {number} n",
            " * @constructor",
            " */",
            "function fn(n){ return }"));
  }

  @Test
  public void testReturn6() {
    testTypes(
        "/** @param {number} opt_a\n@return {string} */" +
        "function a(opt_a) { return opt_a }",
        "inconsistent return type\n" +
        "found   : (number|undefined)\n" +
        "required: string");
  }

  @Test
  public void testReturn7() {
    testTypes("/** @constructor */var A = function() {};\n" +
        "/** @constructor */var B = function() {};\n" +
        "/** @return {!B} */A.f = function() { return 1; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: B");
  }

  @Test
  public void testReturn8() {
    testTypes("/** @constructor */var A = function() {};\n" +
        "/** @constructor */var B = function() {};\n" +
        "/** @return {!B} */A.prototype.f = function() { return 1; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: B");
  }

  @Test
  public void testInferredReturn1() {
    testTypes(
        "function f() {} /** @param {number} x */ function g(x) {}" +
        "g(f());",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : undefined\n" +
        "required: number");
  }

  @Test
  public void testInferredReturn2() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() {}; " +
        "/** @param {number} x */ function g(x) {}" +
        "g((new Foo()).bar());",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : undefined\n" +
        "required: number");
  }

  @Test
  public void testInferredReturn3() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() {}; " +
        "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
        "/** @return {number} \n * @override  */ " +
        "SubFoo.prototype.bar = function() { return 3; }; ",
        "mismatch of the bar property type and the type of the property " +
        "it overrides from superclass Foo\n" +
        "original: function(this:Foo): undefined\n" +
        "override: function(this:SubFoo): number");
  }

  @Test
  public void testInferredReturn4() {
    // By design, this throws a warning. if you want global x to be
    // defined to some other type of function, then you need to declare it
    // as a greater type.
    testTypes(
        "var x = function() {};" +
        "x = /** @type {function(): number} */ (function() { return 3; });",
        "assignment\n" +
        "found   : function(): number\n" +
        "required: function(): undefined");
  }

  @Test
  public void testInferredReturn5() {
    // If x is local, then the function type is not declared.
    testTypes(
        "/** @return {string} */" +
        "function f() {" +
        "  var x = function() {};" +
        "  x = /** @type {function(): number} */ (function() { return 3; });" +
        "  return x();" +
        "}",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testInferredReturn6() {
    testTypes(
        "/** @return {string} */" +
        "function f() {" +
        "  var x = function() {};" +
        "  if (f()) " +
        "    x = /** @type {function(): number} */ " +
        "        (function() { return 3; });" +
        "  return x();" +
        "}",
        "inconsistent return type\n" +
        "found   : (number|undefined)\n" +
        "required: string");
  }

  @Test
  public void testInferredReturn7() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @param {number} x */ Foo.prototype.bar = function(x) {};" +
        "Foo.prototype.bar = function(x) { return 3; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: undefined");
  }

  @Test
  public void testInferredReturn8() {
    testTypes(
        "/** @constructor */ function Foo() {}"
            + "/** @param {number} x */ Foo.prototype.bar = function(x) {};"
            + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
            + "/** @override @param {number} x */ SubFoo.prototype.bar = "
            + "    function(x) { return 3; }",
        "inconsistent return type\n" + "found   : number\n" + "required: undefined");
  }

  @Test
  public void testInferredReturn9() {
    testTypes(
        lines(
            "/** @param {function():string} x */",
            "function f(x) {}",
            "f(/** asdf */ function() { return 123; });"),
        lines(
            "inconsistent return type",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testInferredParam1() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @param {number} x */ Foo.prototype.bar = function(x) {};" +
        "/** @param {string} x */ function f(x) {}" +
        "Foo.prototype.bar = function(y) { f(y); };",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testInferredParam2() {
    testTypes(
        "/** @param {string} x */ function f(x) {}"
            + "/** @constructor */ function Foo() {}"
            + "/** @param {number} x */ Foo.prototype.bar = function(x) {};"
            + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
            + "/** @override @return {void} */ SubFoo.prototype.bar = "
            + "    function(x) { f(x); }",
        "actual parameter 1 of f does not match formal parameter\n"
            + "found   : number\n"
            + "required: string");
  }

  @Test
  public void testInferredParam3() {
    testTypes(
        "/** @param {string} x */ function f(x) {}"
            + "/** @constructor */ function Foo() {}"
            + "/** @param {number=} x */ Foo.prototype.bar = function(x) {};"
            + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
            + "/** @override @return {void} */ SubFoo.prototype.bar = "
            + "    function(x) { f(x); }; (new SubFoo()).bar();",
        "actual parameter 1 of f does not match formal parameter\n"
            + "found   : (number|undefined)\n"
            + "required: string");
  }

  @Test
  public void testInferredParam4() {
    testTypes(
        lines(
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
            "(new SubFoo()).bar();"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testInferredParam5() {
    testTypes(
        "/** @param {string} x */ function f(x) {}"
            + "/** @constructor */ function Foo() {}"
            + "/** @param {...number} x */ Foo.prototype.bar = function(x) {};"
            + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
            + "/** @override @param {number=} x \n * @param {...number} y  */ "
            + "SubFoo.prototype.bar = "
            + "    function(x, y) { f(x); }; (new SubFoo()).bar();",
        "actual parameter 1 of f does not match formal parameter\n"
            + "found   : (number|undefined)\n"
            + "required: string");
  }

  @Test
  public void testInferredParam6() {
    testTypes(
        "/** @param {string} x */ function f(x) {}"
            + "/** @constructor */ function Foo() {}"
            + "/** @param {number=} x */ Foo.prototype.bar = function(x) {};"
            + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
            + "/** @override @param {number=} x \n * @param {number=} y */ "
            + "SubFoo.prototype.bar = "
            + "    function(x, y) { f(y); };",
        "actual parameter 1 of f does not match formal parameter\n"
            + "found   : (number|undefined)\n"
            + "required: string");
  }

  @Test
  public void testInferredParam7() {
    testTypes(
        "/** @param {string} x */ function f(x) {}" +
        "/** @type {function(number=,number=)} */" +
        "var bar = function(x, y) { f(y); };",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : (number|undefined)\n" +
        "required: string");
  }

  @Test
  public void testInferredParam8() {
    testTypes(
        lines(
            "/** @param {function(string)} callback */",
            "function foo(callback) {}",
            "foo(/** random JSDoc */ function(x) { var /** number */ n = x; });"),
        lines(
            "initializing variable",
            "found   : string", // type of "x" is inferred to be string
            "required: number"));
  }

  @Test
  public void testInferredParam9() {
    // TODO(b/77731069) This should warn "parameter 1 of f does not match formal parameter"
    testTypes(
        lines(
            "/** @param {function(string)} callback */",
            "function foo(callback) {}",
            "foo(function(/** number */ x) {});"));
  }

  @Test
  public void testOverriddenParams1() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @param {...?} var_args */" +
        "Foo.prototype.bar = function(var_args) {};" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() {}" +
        "/**\n" +
        " * @param {number} x\n" +
        " * @override\n" +
        " */" +
        "SubFoo.prototype.bar = function(x) {};");
  }

  @Test
  public void testOverriddenParams2() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @type {function(...?)} */" +
        "Foo.prototype.bar = function(var_args) {};" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() {}" +
        "/**\n" +
        " * @type {function(number)}\n" +
        " * @override\n" +
        " */" +
        "SubFoo.prototype.bar = function(x) {};");
  }

  @Test
  public void testOverriddenParams3() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @param {...number} var_args */" +
        "Foo.prototype.bar = function(var_args) { };" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() {}" +
        "/**\n" +
        " * @param {number} x\n" +
        " * @override\n" +
        " */" +
        "SubFoo.prototype.bar = function(x) {};",
        "mismatch of the bar property type and the type of the " +
        "property it overrides from superclass Foo\n" +
        "original: function(this:Foo, ...number): undefined\n" +
        "override: function(this:SubFoo, number): undefined");
  }

  @Test
  public void testOverriddenParams4() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @type {function(...number)} */" +
        "Foo.prototype.bar = function(var_args) {};" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() {}" +
        "/**\n" +
        " * @type {function(number)}\n" +
        " * @override\n" +
        " */" +
        "SubFoo.prototype.bar = function(x) {};",
        "mismatch of the bar property type and the type of the " +
        "property it overrides from superclass Foo\n" +
        "original: function(...number): ?\n" +
        "override: function(number): ?");
  }

  @Test
  public void testOverriddenParams5() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @param {number} x */" +
        "Foo.prototype.bar = function(x) { };" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() {}" +
        "/**\n" +
        " * @override\n" +
        " */" +
        "SubFoo.prototype.bar = function() {};" +
        "(new SubFoo()).bar();");
  }

  @Test
  public void testOverriddenParams6() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @param {number} x */" +
        "Foo.prototype.bar = function(x) { };" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() {}" +
        "/**\n" +
        " * @override\n" +
        " */" +
        "SubFoo.prototype.bar = function() {};" +
        "(new SubFoo()).bar(true);",
        "actual parameter 1 of SubFoo.prototype.bar " +
        "does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testOverriddenParams7() {
    testTypes(
        "/** @constructor\n * @template T */ function Foo() {}" +
        "/** @param {T} x */" +
        "Foo.prototype.bar = function(x) { };" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo<string>}\n" +
        " */ function SubFoo() {}" +
        "/**\n" +
        " * @param {number} x\n" +
        " * @override\n" +
        " */" +
        "SubFoo.prototype.bar = function(x) {};",
        "mismatch of the bar property type and the type of the " +
        "property it overrides from superclass Foo\n" +
        "original: function(this:Foo, string): undefined\n" +
        "override: function(this:SubFoo, number): undefined");
  }

  @Test
  public void testOverriddenReturn1() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @return {Object} */ Foo.prototype.bar = " +
        "    function() { return {}; };" +
        "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
        "/** @return {SubFoo}\n * @override */ SubFoo.prototype.bar = " +
        "    function() { return new Foo(); }",
        "inconsistent return type\n" +
        "found   : Foo\n" +
        "required: (SubFoo|null)");
  }

  @Test
  public void testOverriddenReturn2() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @return {SubFoo} */ Foo.prototype.bar = " +
        "    function() { return new SubFoo(); };" +
        "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
        "/** @return {Foo} x\n * @override */ SubFoo.prototype.bar = " +
        "    function() { return new SubFoo(); }",
        "mismatch of the bar property type and the type of the " +
        "property it overrides from superclass Foo\n" +
        "original: function(this:Foo): (SubFoo|null)\n" +
        "override: function(this:SubFoo): (Foo|null)");
  }

  @Test
  public void testOverriddenReturn3() {
    testTypes(
        "/** @constructor \n * @template T */ function Foo() {}" +
        "/** @return {T} */ Foo.prototype.bar = " +
        "    function() { return null; };" +
        "/** @constructor \n * @extends {Foo<string>} */ function SubFoo() {}" +
        "/** @override */ SubFoo.prototype.bar = " +
        "    function() { return 3; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testOverriddenReturn4() {
    testTypes(
        "/** @constructor \n * @template T */ function Foo() {}" +
        "/** @return {T} */ Foo.prototype.bar = " +
        "    function() { return null; };" +
        "/** @constructor \n * @extends {Foo<string>} */ function SubFoo() {}" +
        "/** @return {number}\n * @override */ SubFoo.prototype.bar = " +
        "    function() { return 3; }",
        "mismatch of the bar property type and the type of the " +
        "property it overrides from superclass Foo\n" +
        "original: function(this:Foo): string\n" +
        "override: function(this:SubFoo): number");
  }

  @Test
  public void testThis1() {
    testTypes("var goog = {};" +
        "/** @constructor */goog.A = function(){};" +
        "/** @return {number} */" +
        "goog.A.prototype.n = function() { return this };",
        "inconsistent return type\n" +
        "found   : goog.A\n" +
        "required: number");
  }

  @Test
  public void testOverriddenProperty1() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @type {Object} */" +
        "Foo.prototype.bar = {};" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() {}" +
        "/**\n" +
        " * @type {Array}\n" +
        " * @override\n" +
        " */" +
        "SubFoo.prototype.bar = [];");
  }

  @Test
  public void testOverriddenProperty2() {
    testTypes(
        "/** @constructor */ function Foo() {" +
        "  /** @type {Object} */" +
        "  this.bar = {};" +
        "}" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() {}" +
        "/**\n" +
        " * @type {Array}\n" +
        " * @override\n" +
        " */" +
        "SubFoo.prototype.bar = [];");
  }

  @Test
  public void testOverriddenProperty3() {
    testTypes(
        "/** @constructor */ function Foo() {" +
        "}" +
        "/** @type {string} */ Foo.prototype.data;" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() {}" +
        "/** @type {string|Object} \n @override */ " +
        "SubFoo.prototype.data = null;",
        "mismatch of the data property type and the type " +
        "of the property it overrides from superclass Foo\n" +
        "original: string\n" +
        "override: (Object|null|string)");
  }

  @Test
  public void testOverriddenProperty4() {
    // These properties aren't declared, so there should be no warning.
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = null;" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() {}" +
        "SubFoo.prototype.bar = 3;");
  }

  @Test
  public void testOverriddenProperty5() {
    // An override should be OK if the superclass property wasn't declared.
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = null;" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() {}" +
        "/** @override */ SubFoo.prototype.bar = 3;");
  }

  @Test
  public void testOverriddenProperty6() {
    // The override keyword shouldn't be necessary if the subclass property
    // is inferred.
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @type {?number} */ Foo.prototype.bar = null;" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() {}" +
        "SubFoo.prototype.bar = 3;");
  }

  @Test
  public void testOverriddenPropertyWithUnknown() {
    // When overriding a declared property with a declared unknown property, we warn for a missing
    // override but not a type mismatch.
    testTypes(
        lines(
            "/** @constructor */ function Foo() {}",
            "/** @type {?number} */ Foo.prototype.bar = null;",
            "",
            "/**",
            " * @constructor",
            " * @extends {Foo}",
            " */",
            "function SubFoo() {}",
            "/** @type {?} */",
            "SubFoo.prototype.bar = 'not a number';"),
        "property bar already defined on superclass Foo; use @override to override it");
  }

  @Test
  public void testThis2() {
    testTypes("var goog = {};" +
        "/** @constructor */goog.A = function(){" +
        "  this.foo = null;" +
        "};" +
        "/** @return {number} */" +
        "goog.A.prototype.n = function() { return this.foo };",
        "inconsistent return type\n" +
        "found   : null\n" +
        "required: number");
  }

  @Test
  public void testThis3() {
    testTypes("var goog = {};" +
        "/** @constructor */goog.A = function(){" +
        "  this.foo = null;" +
        "  this.foo = 5;" +
        "};");
  }

  @Test
  public void testThis4() {
    testTypes("var goog = {};" +
        "/** @constructor */goog.A = function(){" +
        "  /** @type {string?} */this.foo = null;" +
        "};" +
        "/** @return {number} */goog.A.prototype.n = function() {" +
        "  return this.foo };",
        "inconsistent return type\n" +
        "found   : (null|string)\n" +
        "required: number");
  }

  @Test
  public void testThis5() {
    testTypes(
        "/** @this {Date}\n@return {number}*/function h() { return this }",
        "inconsistent return type\n" + "found   : Date\n" + "required: number");
  }

  @Test
  public void testThis6() {
    testTypes("var goog = {};" +
        "/** @constructor\n@return {!Date} */" +
        "goog.A = function(){ return this };",
        "inconsistent return type\n" +
        "found   : goog.A\n" +
        "required: Date");
  }

  @Test
  public void testThis7() {
    testTypes("/** @constructor */function A(){};" +
        "/** @return {number} */A.prototype.n = function() { return this };",
        "inconsistent return type\n" +
        "found   : A\n" +
        "required: number");
  }

  @Test
  public void testThis8() {
    testTypes("/** @constructor */function A(){" +
        "  /** @type {string?} */this.foo = null;" +
        "};" +
        "/** @return {number} */A.prototype.n = function() {" +
        "  return this.foo };",
        "inconsistent return type\n" +
        "found   : (null|string)\n" +
        "required: number");
  }

  @Test
  public void testThis9() {
    // In A.bar, the type of {@code this} is unknown.
    testTypes("/** @constructor */function A(){};" +
        "A.prototype.foo = 3;" +
        "/** @return {string} */ A.bar = function() { return this.foo; };");
  }

  @Test
  public void testThis10() {
    // In A.bar, the type of {@code this} is inferred from the @this tag.
    testTypes("/** @constructor */function A(){};" +
        "A.prototype.foo = 3;" +
        "/** @this {A}\n@return {string} */" +
        "A.bar = function() { return this.foo; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testThis11() {
    testTypes(
        "/** @param {number} x */ function f(x) {}" +
        "/** @constructor */ function Ctor() {" +
        "  /** @this {Date} */" +
        "  this.method = function() {" +
        "    f(this);" +
        "  };" +
        "}",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : Date\n" +
        "required: number");
  }

  @Test
  public void testThis12() {
    testTypes(
        "/** @param {number} x */ function f(x) {}" +
        "/** @constructor */ function Ctor() {}" +
        "Ctor.prototype['method'] = function() {" +
        "  f(this);" +
        "}",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : Ctor\n" +
        "required: number");
  }

  @Test
  public void testThis13() {
    testTypes(
        "/** @param {number} x */ function f(x) {}" +
        "/** @constructor */ function Ctor() {}" +
        "Ctor.prototype = {" +
        "  method: function() {" +
        "    f(this);" +
        "  }" +
        "};",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : Ctor\n" +
        "required: number");
  }

  @Test
  public void testThis14() {
    testTypesWithCommonExterns(
        "/** @param {number} x */ function f(x) {}" +
        "f(this.Object);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : function(new:Object, *=): Object\n" +
        "required: number");
  }

  @Test
  public void testThisTypeOfFunction1() {
    testTypes(
        "/** @type {function(this:Object)} */ function f() {}" +
        "f();");
  }

  @Test
  public void testThisTypeOfFunction2() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** @type {function(this:F)} */ function f() {}" +
        "f();",
        "\"function(this:F): ?\" must be called with a \"this\" type");
  }

  @Test
  public void testThisTypeOfFunction3() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "F.prototype.bar = function() {};" +
        "var f = (new F()).bar; f();",
        "\"function(this:F): undefined\" must be called with a \"this\" type");
  }

  @Test
  public void testThisTypeOfFunction4() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().addObject().build(),
        lines(
            "/** @constructor */ function F() {}",
            "F.prototype.moveTo = function(x, y) {};",
            "F.prototype.lineTo = function(x, y) {};",
            "function demo() {",
            "  var path = new F();",
            "  var points = [[1,1], [2,2]];",
            "  for (var i = 0; i < points.length; i++) {",
            "    (i == 0 ? path.moveTo : path.lineTo)(points[i][0], points[i][1]);",
            "  }",
            "}"),
        "\"function(this:F, ?, ?): undefined\" must be called with a \"this\" type");
  }

  @Test
  public void testThisTypeOfFunction5() {
    testTypes(lines(
        "/** @type {function(this:number)} */",
        "function f() {",
        "  var /** number */ n = this;",
        "}"));
  }

  @Test
  public void testGlobalThis1() {
    testTypes("/** @constructor */ function Window() {}" +
        "/** @param {string} msg */ " +
        "Window.prototype.alert = function(msg) {};" +
        "this.alert(3);",
        "actual parameter 1 of Window.prototype.alert " +
        "does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testGlobalThis2a() {
    // this.alert = 3 doesn't count as a declaration, so this is a warning.
    testTypes(
        lines(
            "/** @constructor */ function Bindow() {}",
            "/** @param {string} msg */ ",
            "Bindow.prototype.alert = function(msg) {};",
            "this.alert = 3;",
            "(new Bindow()).alert(this.alert)"),
        ImmutableList.of(
            "Property alert never defined on global this",
            "Property alert never defined on global this"));
  }

  @Test
  public void testGlobalThis2b() {
    // Only reported if strict property checks are enabled
    disableStrictMissingPropertyChecks();

    testTypes(lines("/** @constructor */ function Bindow() {}",
        "/** @param {string} msg */ ",
        "Bindow.prototype.alert = function(msg) {};",
        "this.alert = 3;",
        "(new Bindow()).alert(this.alert)"));
  }

  @Test
  public void testGlobalThis2c() {
    disableStrictMissingPropertyChecks();
    testTypes("/** @constructor */ function Bindow() {}" +
        "/** @param {string} msg */ " +
        "Bindow.prototype.alert = function(msg) {};" +
        "/** @return {number} */ this.alert = function() { return 3; };" +
        "(new Bindow()).alert(this.alert())",
        "actual parameter 1 of Bindow.prototype.alert " +
        "does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testGlobalThis3() {
    testTypes(
        "/** @param {string} msg */ " +
        "function alert(msg) {};" +
        "this.alert(3);",
        "actual parameter 1 of global this.alert " +
        "does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testGlobalThis4() {
    testTypes(
        "/** @param {string} msg */ " +
        "var alert = function(msg) {};" +
        "this.alert(3);",
        "actual parameter 1 of global this.alert " +
        "does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testGlobalThis5() {
    testTypes(
        "function f() {" +
        "  /** @param {string} msg */ " +
        "  var alert = function(msg) {};" +
        "}" +
        "this.alert(3);",
        "Property alert never defined on global this");
  }

  @Test
  public void testGlobalThis6() {
    testTypes(
        lines(
            "/** @param {string} msg */ ",
            "var alert = function(msg) {};",
            "var x = 3;",
            "x = 'msg';",
            "this.alert(this.x);"));
  }

  @Test
  public void testGlobalThis7() {
    testTypes(
        "/** @constructor */ function Window() {}" +
        "/** @param {Window} msg */ " +
        "var foo = function(msg) {};" +
        "foo(this);");
  }

  @Test
  public void testGlobalThis8() {
    testTypes(
        "/** @constructor */ function Window() {}" +
        "/** @param {number} msg */ " +
        "var foo = function(msg) {};" +
        "foo(this);",
        "actual parameter 1 of foo does not match formal parameter\n" +
        "found   : global this\n" +
        "required: number");
  }

  @Test
  public void testGlobalThis9() {
    testTypes(
        // Window is not marked as a constructor, so the
        // inheritance doesn't happen.
        "function Window() {}" +
        "Window.prototype.alert = function() {};" +
        "this.alert();",
        "Property alert never defined on global this");
  }

  @Test
  public void testGlobalThisDoesNotIncludeVarsDeclaredWithConst() {
    testTypes(
        lines(
            "/** @param {string} msg */ ", // preserve newlines
            "const alert = function(msg) {};",
            "this.alert('boo');"),
        "Property alert never defined on global this");
  }

  @Test
  public void testGlobalThisDoesNotIncludeVarsDeclaredWithLet() {
    testTypes(
        lines(
            "/** @param {string} msg */ ", // preserve newlines
            "let alert = function(msg) {};",
            "this.alert('boo');"),
        "Property alert never defined on global this");
  }

  @Test
  public void testControlFlowRestrictsType1() {
    testTypes(
        "/** @return {String?} */ function f() { return null; }"
            + "/** @type {String?} */ var a = f();"
            + "/** @type {String} */ var b = new String('foo');"
            + "/** @type {null} */ var c = null;"
            + "if (a) {"
            + "  b = a;"
            + "} else {"
            + "  c = a;"
            + "}");
  }

  @Test
  public void testControlFlowRestrictsType2() {
    testTypes(
        "/** @return {(string|null)} */ function f() { return null; }"
            + "/** @type {(string|null)} */ var a = f();"
            + "/** @type {string} */ var b = 'foo';"
            + "/** @type {null} */ var c = null;"
            + "if (a) {"
            + "  b = a;"
            + "} else {"
            + "  c = a;"
            + "}",
        "assignment\n" + "found   : (null|string)\n" + "required: null");
  }

  @Test
  public void testControlFlowRestrictsType3() {
    testTypes(
        "/** @type {(string|void)} */"
            + "var a;"
            + "/** @type {string} */"
            + "var b = 'foo';"
            + "if (a) {"
            + "  b = a;"
            + "}");
  }

  @Test
  public void testControlFlowRestrictsType4() {
    testTypes("/** @param {string} a */ function f(a){}" +
        "/** @type {(string|undefined)} */ var a;" +
        "a && f(a);");
  }

  @Test
  public void testControlFlowRestrictsType5() {
    testTypes("/** @param {undefined} a */ function f(a){}" +
        "/** @type {(!Array|undefined)} */ var a;" +
        "a || f(a);");
  }

  @Test
  public void testControlFlowRestrictsType6() {
    testTypes("/** @param {undefined} x */ function f(x) {}" +
        "/** @type {(string|undefined)} */ var a;" +
        "a && f(a);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: undefined");
  }

  @Test
  public void testControlFlowRestrictsType7() {
    testTypes("/** @param {undefined} x */ function f(x) {}" +
        "/** @type {(string|undefined)} */ var a;" +
        "a && f(a);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: undefined");
  }

  @Test
  public void testControlFlowRestrictsType8() {
    testTypes("/** @param {undefined} a */ function f(a){}" +
        "/** @type {(!Array|undefined)} */ var a;" +
        "if (a || f(a)) {}");
  }

  @Test
  public void testControlFlowRestrictsType9() {
    testTypes("/** @param {number?} x\n * @return {number}*/\n" +
        "var f = function(x) {\n" +
        "if (!x || x == 1) { return 1; } else { return x; }\n" +
        "};");
  }

  @Test
  public void testControlFlowRestrictsType10() {
    // We should correctly infer that y will be (null|{}) because
    // the loop wraps around.
    testTypes("/** @param {number} x */ function f(x) {}" +
        "function g() {" +
        "  var y = null;" +
        "  for (var i = 0; i < 10; i++) {" +
        "    f(y);" +
        "    if (y != null) {" +
        "      // y is None the first time it goes through this branch\n" +
        "    } else {" +
        "      y = {};" +
        "    }" +
        "  }" +
        "};",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : (null|{})\n" +
        "required: number");
  }

  @Test
  public void testControlFlowRestrictsType11() {
    testTypes("/** @param {boolean} x */ function f(x) {}" +
        "function g() {" +
        "  var y = null;" +
        "  if (y != null) {" +
        "    for (var i = 0; i < 10; i++) {" +
        "      f(y);" +
        "    }" +
        "  }" +
        "};",
        "condition always evaluates to false\n" +
        "left : null\n" +
        "right: null");
  }

  @Test
  public void testSwitchCase3() {
    testTypes("/** @type {String} */" +
        "var a = new String('foo');" +
        "switch (a) { case 'A': }");
  }

  @Test
  public void testSwitchCase4() {
    testTypes("/** @type {(string|Null)} */" +
        "var a = unknown;" +
        "switch (a) { case 'A':break; case null:break; }");
  }

  @Test
  public void testSwitchCase5() {
    testTypes("/** @type {(String|Null)} */" +
        "var a = unknown;" +
        "switch (a) { case 'A':break; case null:break; }");
  }

  @Test
  public void testSwitchCase6() {
    testTypes("/** @type {(Number|Null)} */" +
        "var a = unknown;" +
        "switch (a) { case 5:break; case null:break; }");
  }

  @Test
  public void testSwitchCase7() {
    // This really tests the inference inside the case.
    testTypes(
        "/**\n" +
        " * @param {number} x\n" +
        " * @return {number}\n" +
        " */\n" +
        "function g(x) { return 5; }" +
        "function f() {" +
        "  var x = {};" +
        "  x.foo = '3';" +
        "  switch (3) { case g(x.foo): return 3; }" +
        "}",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testSwitchCase8() {
    // This really tests the inference inside the switch clause.
    testTypes(
        "/**\n" +
        " * @param {number} x\n" +
        " * @return {number}\n" +
        " */\n" +
        "function g(x) { return 5; }" +
        "function f() {" +
        "  var x = {};" +
        "  x.foo = '3';" +
        "  switch (g(x.foo)) { case 3: return 3; }" +
        "}",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testImplicitCast1() {
    testTypesWithExterns("/** @constructor */ function Element() {};\n" +
             "/** @type {string}\n" +
             "  * @implicitCast */" +
             "Element.prototype.innerHTML;",
             "(new Element).innerHTML = new Array();");
  }

  @Test
  public void testImplicitCast2() {
    testTypesWithExterns(
        "/** @constructor */ function Element() {};\n" +
        "/**\n" +
        " * @type {string}\n" +
        " * @implicitCast\n" +
        " */\n" +
        "Element.prototype.innerHTML;\n",
        "/** @constructor */ function C(e) {\n" +
        "  /** @type {Element} */ this.el = e;\n" +
        "}\n" +
        "C.prototype.method = function() {\n" +
        "  this.el.innerHTML = new Array();\n" +
        "};\n");
  }

  @Test
  public void testImplicitCast3() {
    testTypesWithExterns(
        lines(
            "/** @constructor */ function Element() {};",
            "/**",
            " * @type {string}",
            " * @implicitCast",
            " */",
            "Element.prototype.innerHTML;"),
        lines(
            "/** @param {?Element} element",
            " * @param {string|number} text",
            " */",
            "function f(element, text) {",
            "  element.innerHTML = text;",
            "}",
            ""));
  }

  @Test
  public void testImplicitCastSubclassAccess() {
    testTypesWithExterns("/** @constructor */ function Element() {};\n" +
             "/** @type {string}\n" +
             "  * @implicitCast */" +
             "Element.prototype.innerHTML;" +
             "/** @constructor \n @extends Element */" +
             "function DIVElement() {};",
             "(new DIVElement).innerHTML = new Array();");
  }

  @Test
  public void testImplicitCastNotInExterns() {
    // We issue a warning in CheckJSDoc for @implicitCast not in externs
    testTypes(
        lines(
            "/** @constructor */ function Element() {};",
            "/**",
            " * @type {string}",
            " * @implicitCast ",
            " */",
            "Element.prototype.innerHTML;",
            "(new Element).innerHTML = new Array();"),
        lines(
            "assignment to property innerHTML of Element", // preserve new line
            "found   : Array",
            "required: string"));
  }

  @Test
  public void testNumberNode() {
    Node n = typeCheck(Node.newNumber(0));

    assertTypeEquals(getNativeNumberType(), n.getJSType());
  }

  @Test
  public void testStringNode() {
    Node n = typeCheck(Node.newString("hello"));

    assertTypeEquals(getNativeStringType(), n.getJSType());
  }

  @Test
  public void testBooleanNodeTrue() {
    Node trueNode = typeCheck(new Node(Token.TRUE));

    assertTypeEquals(getNativeBooleanType(), trueNode.getJSType());
  }

  @Test
  public void testBooleanNodeFalse() {
    Node falseNode = typeCheck(new Node(Token.FALSE));

    assertTypeEquals(getNativeBooleanType(), falseNode.getJSType());
  }

  @Test
  public void testUndefinedNode() {
    Node p = new Node(Token.ADD);
    Node n = Node.newString(Token.NAME, "undefined");
    p.addChildToBack(n);
    p.addChildToBack(Node.newNumber(5));
    typeCheck(p);

    assertTypeEquals(getNativeVoidType(), n.getJSType());
  }

  @Test
  public void testNumberAutoboxing() {
    testTypes("/** @type {Number} */var a = 4;",
        "initializing variable\n" +
        "found   : number\n" +
        "required: (Number|null)");
  }

  @Test
  public void testNumberUnboxing() {
    testTypes("/** @type {number} */var a = new Number(4);",
        "initializing variable\n" +
        "found   : Number\n" +
        "required: number");
  }

  @Test
  public void testStringAutoboxing() {
    testTypes("/** @type {String} */var a = 'hello';",
        "initializing variable\n" +
        "found   : string\n" +
        "required: (String|null)");
  }

  @Test
  public void testStringUnboxing() {
    testTypes("/** @type {string} */var a = new String('hello');",
        "initializing variable\n" +
        "found   : String\n" +
        "required: string");
  }

  @Test
  public void testBooleanAutoboxing() {
    testTypes("/** @type {Boolean} */var a = true;",
        "initializing variable\n" +
        "found   : boolean\n" +
        "required: (Boolean|null)");
  }

  @Test
  public void testBooleanUnboxing() {
    testTypes("/** @type {boolean} */var a = new Boolean(false);",
        "initializing variable\n" +
        "found   : Boolean\n" +
        "required: boolean");
  }

  @Test
  public void testIIFE1() {
    testTypes(
        "var namespace = {};" +
        "/** @type {number} */ namespace.prop = 3;" +
        "(function(ns) {" +
        "  ns.prop = true;" +
        "})(namespace);",
        "assignment to property prop of ns\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testIIFE2() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "(function(ctor) {" +
        "  /** @type {boolean} */ ctor.prop = true;" +
        "})(Foo);" +
        "/** @return {number} */ function f() { return Foo.prop; }",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testIIFE3() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "(function(ctor) {" +
        "  /** @type {boolean} */ ctor.prop = true;" +
        "})(Foo);" +
        "/** @param {number} x */ function f(x) {}" +
        "f(Foo.prop);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testIIFE4() {
    testTypes(
        "/** @const */ var namespace = {};" +
        "(function(ns) {" +
        "  /**\n" +
        "   * @constructor\n" +
        "   * @param {number} x\n" +
        "   */\n" +
        "   ns.Ctor = function(x) {};" +
        "})(namespace);" +
        "new namespace.Ctor(true);",
        "actual parameter 1 of namespace.Ctor " +
        "does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testIIFE5() {
    // TODO(nicksantos): This behavior is currently incorrect.
    // To handle this case properly, we'll need to change how we handle
    // type resolution.
    testTypes(
        "/** @const */ var namespace = {};" +
        "(function(ns) {" +
        "  /**\n" +
        "   * @constructor\n" +
        "   */\n" +
        "   ns.Ctor = function() {};" +
        "   /** @type {boolean} */ ns.Ctor.prototype.bar = true;" +
        "})(namespace);" +
        "/** @param {namespace.Ctor} x\n" +
        "  * @return {number} */ function f(x) { return x.bar; }",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testNotIIFE1() {
    testTypes(
        "/** @param {number} x */ function f(x) {}" +
        "/** @param {...?} x */ function g(x) {}" +
        "g(function(y) { f(y); }, true);");
  }

  @Test
  public void testIssue61a() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "var ns = {};" +
        "(function() {" +
        "  /** @param {string} b */" +
        "  ns.a = function(b) {};" +
        "})();" +
        "function d() {" +
        "  ns.a(123);" +
        "}",
        "actual parameter 1 of ns.a does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testIssue61b() {
    testTypes(
        lines(
            "/** @const */ var ns = {};",
            "(function() {",
            "  /** @param {string} b */",
            "  ns.a = function(b) {};",
            "})();",
            "ns.a(123);"),
        ImmutableList.of(
            "actual parameter 1 of ns.a does not match formal parameter\n"
                + "found   : number\n"
                + "required: string"));
  }

  @Test
  public void testIssue61c() {
    // TODO(johnlenz): I'm not sure why the '@const' and inferred const cases are handled
    // differently.
    testTypes(
        lines(
            "var ns = {};",
            "(function() {",
            "  /** @param {string} b */",
            "  ns.a = function(b) {};",
            "})();",
            "ns.a(123);"),
        ImmutableList.of(
            "Property a never defined on ns",
            "actual parameter 1 of ns.a does not match formal parameter\n"
                + "found   : number\n"
                + "required: string"));
  }

  @Test
  public void testIssue86() {
    testTypes(
        "/** @interface */ function I() {}" +
        "/** @return {number} */ I.prototype.get = function(){};" +
        "/** @constructor \n * @implements {I} */ function F() {}" +
        "/** @override */ F.prototype.get = function() { return true; };",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testIssue124() {
    testTypes(
        "var t = null;" +
        "function test() {" +
        "  if (t != null) { t = null; }" +
        "  t = 1;" +
        "}");
  }

  @Test
  public void testIssue124b() {
    testTypes(
        "var t = null;" +
        "function test() {" +
        "  if (t != null) { t = null; }" +
        "  t = undefined;" +
        "}",
        "condition always evaluates to false\n" +
        "left : (null|undefined)\n" +
        "right: null");
  }

  @Test
  public void testIssue259() {
    testTypes(
        "/** @param {number} x */ function f(x) {}" +
        "/** @constructor */" +
        "var Clock = function() {" +
        "  /** @constructor */" +
        "  this.Date = function() {};" +
        "  f(new this.Date());" +
        "};",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : this.Date\n" +
        "required: number");
  }

  @Test
  public void testIssue301() {
    testTypesWithExterns(
        new TestExternsBuilder().addString().addArray().build(),
        lines(
            "Array.indexOf = function() {};",
            "var s = 'hello';",
            "alert(s.toLowerCase.indexOf('1'));"),
        "Property indexOf never defined on String.prototype.toLowerCase");
  }

  @Test
  public void testIssue368() {
    testTypes(
        "/** @constructor */ function Foo(){}" +
        "/**\n" +
        " * @param {number} one\n" +
        " * @param {string} two\n" +
        " */\n" +
        "Foo.prototype.add = function(one, two) {};" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */\n" +
        "function Bar(){}" +
        "/** @override */\n" +
        "Bar.prototype.add = function(ignored) {};" +
        "(new Bar()).add(1, 2);",
        "actual parameter 2 of Bar.prototype.add does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testIssue380() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().addObject().build(),
        lines(
            "/** @type { function(string): {innerHTML: string} } */",
            "document.getElementById;",
            "var list = /** @type {!Array<string>} */ ['hello', 'you'];",
            "list.push('?');",
            "document.getElementById('node').innerHTML = list.toString();"));
  }

  @Test
  public void testIssue483() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        lines(
            "/** @constructor */ function C() {",
            "  /** @type {?Array} */ this.a = [];",
            "}",
            "C.prototype.f = function() {",
            "  if (this.a.length > 0) {",
            "    g(this.a);",
            "  }",
            "};",
            "/** @param {number} a */ function g(a) {}"),
        lines(
            "actual parameter 1 of g does not match formal parameter",
            "found   : Array",
            "required: number"));
  }

  @Test
  public void testIssue537a() {
    testTypesWithCommonExterns(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype = {method: function() {}};" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */\n" +
        "function Bar() {" +
        "  Foo.call(this);" +
        "  if (this.baz()) this.method(1);" +
        "}" +
        "Bar.prototype = {" +
        "  baz: function() {" +
        "    return true;" +
        "  }" +
        "};" +
        "Bar.prototype.__proto__ = Foo.prototype;",
        "Function Foo.prototype.method: called with 1 argument(s). " +
        "Function requires at least 0 argument(s) " +
        "and no more than 0 argument(s).");
  }

  @Test
  public void testIssue537b() {
    testTypesWithCommonExterns(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype = {method: function() {}};" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */\n" +
        "function Bar() {" +
        "  Foo.call(this);" +
        "  if (this.baz(1)) this.method();" +
        "}" +
        "Bar.prototype = {" +
        "  baz: function() {" +
        "    return true;" +
        "  }" +
        "};" +
        "Bar.prototype.__proto__ = Foo.prototype;",
        "Function Bar.prototype.baz: called with 1 argument(s). " +
        "Function requires at least 0 argument(s) " +
        "and no more than 0 argument(s).");
  }

  @Test
  public void testIssue537c() {
    testTypesWithCommonExterns(
        "/** @constructor */ function Foo() {}" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */\n" +
        "function Bar() {" +
        "  Foo.call(this);" +
        "  if (this.baz2()) alert(1);" +
        "}" +
        "Bar.prototype = {" +
        "  baz: function() {" +
        "    return true;" +
        "  }" +
        "};" +
        "Bar.prototype.__proto__ = Foo.prototype;",
        "Property baz2 never defined on Bar");
  }

  @Test
  public void testIssue537d() {
    testTypesWithCommonExterns(
        "/** @constructor */ function Foo() {}"
            + "Foo.prototype = {"
            + "  /** @return {Bar} */ x: function() { new Bar(); },"
            + "  /** @return {Foo} */ y: function() { new Bar(); }"
            + "};"
            + "/**\n"
            + " * @constructor\n"
            + " * @extends {Foo}\n"
            + " */\n"
            + "function Bar() {"
            + "  this.xy = 3;"
            + "}"
            + "/** @return {Bar} */ function f() { return new Bar(); }"
            + "/** @return {Foo} */ function g() { return new Bar(); }"
            + "Bar.prototype = {"
            + "  /** @override @return {Bar} */ x: function() { new Bar(); },"
            + "  /** @override @return {Foo} */ y: function() { new Bar(); }"
            + "};"
            + "Bar.prototype.__proto__ = Foo.prototype;");
  }

  @Test
  public void testIssue586() {
    testTypes(
        "/** @constructor */" +
        "var MyClass = function() {};" +
        "/** @param {boolean} success */" +
        "MyClass.prototype.fn = function(success) {};" +
        "MyClass.prototype.test = function() {" +
        "  this.fn();" +
        "  this.fn = function() {};" +
        "};",
        "Function MyClass.prototype.fn: called with 0 argument(s). " +
        "Function requires at least 1 argument(s) " +
        "and no more than 1 argument(s).");
  }

  @Test
  public void testIssue635() {
    // TODO(nicksantos): Make this emit a warning, because of the 'this' type.
    testTypes(
        "/** @constructor */" +
        "function F() {}" +
        "F.prototype.bar = function() { this.baz(); };" +
        "F.prototype.baz = function() {};" +
        "/** @constructor */" +
        "function G() {}" +
        "G.prototype.bar = F.prototype.bar;");
  }

  @Test
  public void testIssue635b() {
    testTypes(
        "/** @constructor */" +
        "function F() {}" +
        "/** @constructor */" +
        "function G() {}" +
        "/** @type {function(new:G)} */ var x = F;",
        "initializing variable\n" +
        "found   : function(new:F): undefined\n" +
        "required: function(new:G): ?");
  }

  @Test
  public void testIssue669() {
    testTypes(
        "/** @return {{prop1: (Object|undefined)}} */" +
         "function f(a) {" +
         "  var results;" +
         "  if (a) {" +
         "    results = {};" +
         "    results.prop1 = {a: 3};" +
         "  } else {" +
         "    results = {prop2: 3};" +
         "  }" +
         "  return results;" +
         "}");
  }

  @Test
  public void testIssue688() {
    testTypes(
        "/** @const */ var SOME_DEFAULT =\n" +
        "    /** @type {TwoNumbers} */ ({first: 1, second: 2});\n" +
        "/**\n" +
        "* Class defining an interface with two numbers.\n" +
        "* @interface\n" +
        "*/\n" +
        "function TwoNumbers() {}\n" +
        "/** @type {number} */\n" +
        "TwoNumbers.prototype.first;\n" +
        "/** @type {number} */\n" +
        "TwoNumbers.prototype.second;\n" +
        "/** @return {number} */ function f() { return SOME_DEFAULT; }",
        "inconsistent return type\n" +
        "found   : (TwoNumbers|null)\n" +
        "required: number");
  }

  @Test
  public void testIssue700() {
    testTypes(
        "/**\n" +
        " * @param {{text: string}} opt_data\n" +
        " * @return {string}\n" +
        " */\n" +
        "function temp1(opt_data) {\n" +
        "  return opt_data.text;\n" +
        "}\n" +
        "\n" +
        "/**\n" +
        " * @param {{activity: (boolean|number|string|null|Object)}} opt_data\n" +
        " * @return {string}\n" +
        " */\n" +
        "function temp2(opt_data) {\n" +
        "  /** @suppress {checkTypes} */\n" +
        "  function __inner() {\n" +
        "    return temp1(opt_data.activity);\n" +
        "  }\n" +
        "  return __inner();\n" +
        "}\n" +
        "\n" +
        "/**\n" +
        " * @param {{n: number, text: string, b: boolean}} opt_data\n" +
        " * @return {string}\n" +
        " */\n" +
        "function temp3(opt_data) {\n" +
        "  return 'n: ' + opt_data.n + ', t: ' + opt_data.text + '.';\n" +
        "}\n" +
        "\n" +
        "function callee() {\n" +
        "  var output = temp3({\n" +
        "    n: 0,\n" +
        "    text: 'a string',\n" +
        "    b: true\n" +
        "  })\n" +
        "  alert(output);\n" +
        "}\n" +
        "\n" +
        "callee();");
  }

  @Test
  public void testIssue725() {
    testTypes(
        "/** @typedef {{name: string}} */ var RecordType1;" +
        "/** @typedef {{name2222: string}} */ var RecordType2;" +
        "/** @param {RecordType1} rec */ function f(rec) {" +
        "  alert(rec.name2222);" +
        "}",
        "Property name2222 never defined on rec");
  }

  @Test
  public void testIssue726() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @param {number} x */ Foo.prototype.bar = function(x) {};" +
        "/** @return {!Function} */ " +
        "Foo.prototype.getDeferredBar = function() { " +
        "  var self = this;" +
        "  return function() {" +
        "    self.bar(true);" +
        "  };" +
        "};",
        "actual parameter 1 of Foo.prototype.bar does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testIssue765() {
    testTypes(
        "/** @constructor */" +
        "var AnotherType = function(parent) {" +
        "    /** @param {string} stringParameter Description... */" +
        "    this.doSomething = function(stringParameter) {};" +
        "};" +
        "/** @constructor */" +
        "var YetAnotherType = function() {" +
        "    this.field = new AnotherType(self);" +
        "    this.testfun=function(stringdata) {" +
        "        this.field.doSomething(null);" +
        "    };" +
        "};",
        "actual parameter 1 of AnotherType.doSomething " +
        "does not match formal parameter\n" +
        "found   : null\n" +
        "required: string");
  }

  @Test
  public void testIssue783() {
    testTypes(
        "/** @constructor */" +
        "var Type = function() {" +
        "  /** @type {Type} */" +
        "  this.me_ = this;" +
        "};" +
        "Type.prototype.doIt = function() {" +
        "  var me = this.me_;" +
        "  for (var i = 0; i < me.unknownProp; i++) {}" +
        "};",
        "Property unknownProp never defined on Type");
  }

  @Test
  public void testIssue791() {
    testTypes(
        "/** @param {{func: function()}} obj */" +
        "function test1(obj) {}" +
        "var fnStruc1 = {};" +
        "fnStruc1.func = function() {};" +
        "test1(fnStruc1);");
  }

  @Test
  public void testIssue810() {
    testTypes(
        lines(
            "/** @constructor */",
            "var Type = function() {",
            "  this.prop = x;",
            "};",
            "Type.prototype.doIt = function(obj) {",
            "  this.prop = obj.unknownProp;",
            "};"),
        "Property unknownProp never defined on obj");
  }

  @Test
  public void testIssue1002() {
    testTypes(
        "/** @interface */" +
        "var I = function() {};" +
        "/** @constructor @implements {I} */" +
        "var A = function() {};" +
        "/** @constructor @implements {I} */" +
        "var B = function() {};" +
        "var f = function() {" +
        "  if (A === B) {" +
        "    new B();" +
        "  }" +
        "};");
  }

  @Test
  public void testIssue1023() {
    testTypes(
        "/** @constructor */" +
        "function F() {}" +
        "(function() {" +
        "  F.prototype = {" +
        "    /** @param {string} x */" +
        "    bar: function(x) {  }" +
        "  };" +
        "})();" +
        "(new F()).bar(true)",
        "actual parameter 1 of F.prototype.bar does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: string");
  }

  @Test
  public void testIssue1047() {
    testTypes(
        "/**\n" +
        " * @constructor\n" +
        " */\n" +
        "function C2() {}\n" +
        "\n" +
        "/**\n" +
        " * @constructor\n" +
        " */\n" +
        "function C3(c2) {\n" +
        "  /**\n" +
        "   * @type {C2} \n" +
        "   * @private\n" +
        "   */\n" +
        "  this.c2_;\n" +
        "\n" +
        "  var x = this.c2_.prop;\n" +
        "}",
        "Property prop never defined on C2");
  }

  @Test
  public void testIssue1056() {
    testTypes(
        "/** @type {Array} */ var x = null;" +
        "x.push('hi');",
        "No properties on this expression\n" +
        "found   : null\n" +
        "required: Object");
  }

  @Test
  public void testIssue1072() {
    testTypes(
        "/**\n" +
        " * @param {string} x\n" +
        " * @return {number}\n" +
        " */\n" +
        "var f1 = function(x) {\n" +
        "  return 3;\n" +
        "};\n" +
        "\n" +
        "/** Function */\n" +
        "var f2 = function(x) {\n" +
        "  if (!x) throw new Error()\n" +
        "  return /** @type {number} */ (f1('x'))\n" +
        "}\n" +
        "\n" +
        "/**\n" +
        " * @param {string} x\n" +
        " */\n" +
        "var f3 = function(x) {};\n" +
        "\n" +
        "f1(f3);",
        "actual parameter 1 of f1 does not match formal parameter\n" +
        "found   : function(string): undefined\n" +
        "required: string");
  }

  @Test
  public void testIssue1123() {
    testTypes(
        "/** @param {function(number)} g */ function f(g) {}" +
        "f(function(a, b) {})",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : function(?, ?): undefined\n" +
        "required: function(number): ?");
  }

  @Test
  public void testIssue1201() {
    testTypes(
        "/** @param {function(this:void)} f */ function g(f) {}" +
        "/** @constructor */ function F() {}" +
        "/** desc */ F.prototype.bar = function() {};" +
        "g(new F().bar);",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : function(this:F): undefined\n" +
        "required: function(this:undefined): ?");
  }

  @Test
  public void testIssue1201b() {
    testTypesWithCommonExterns(
        "/** @param {function(this:void)} f */ function g(f) {}" +
        "/** @constructor */ function F() {}" +
        "/** desc */ F.prototype.bar = function() {};" +
        "var f = new F();" +
        "g(f.bar.bind(f));");
  }

  @Test
  public void testIssue1201c() {
    testTypes(
        "/** @param {function(this:void)} f */ function g(f) {}" +
        "g(function() { this.alert() })",
        "No properties on this expression\n" +
        "found   : undefined\n" +
        "required: Object");
  }

  @Test
  public void testIssue926a() {
    testTypes("/** x */ function error() {}" +
              "/**\n" +
              " * @constructor\n" +
              " * @param {string} error\n" +
              " */\n" +
              "function C(error) {\n" +
              " /** @const */ this.e = error;\n" +
              "}" +
              "/** @type {number} */ var x = (new C('x')).e;",
              "initializing variable\n" +
              "found   : string\n" +
              "required: number");
  }

  @Test
  public void testIssue926b() {
    testTypes("/** @constructor */\n" +
              "function A() {\n" +
              " /** @constructor */\n" +
              " function B() {}\n" +
              " /** @type {!B} */ this.foo = new B();" +
              " /** @type {!B} */ var C = new B();" +
              "}" +
              "/** @type {number} */ var x = (new A()).foo;",
              "initializing variable\n" +
              "found   : B\n" +
              "required: number");
  }

  @Test
  public void testEnums() {
    testTypes(
        "var outer = function() {" +
        "  /** @enum {number} */" +
        "  var Level = {" +
        "    NONE: 0," +
        "  };" +
        "  /** @type {!Level} */" +
        "  var l = Level.NONE;" +
        "}");
  }

  /**
   * Tests that the || operator is type checked correctly, that is of the type of the first argument
   * or of the second argument. See bugid 592170 for more details.
   */
  @Test
  public void testBug592170() {
    testTypes(
        "/** @param {Function} opt_f ... */" +
        "function foo(opt_f) {" +
        "  /** @type {Function} */" +
        "  return opt_f || function() {};" +
        "}");
  }

  /**
   * Tests that undefined can be compared shallowly to a value of type (number,undefined) regardless
   * of the side on which the undefined value is.
   */
  @Test
  public void testBug901455a() {
    testTypes("/** @return {(number|undefined)} */ function a() { return 3; }" +
        "var b = undefined === a()");
  }

  /**
   * Tests that undefined can be compared shallowly to a value of type (number,undefined) regardless
   * of the side on which the undefined value is.
   */
  @Test
  public void testBug901455b() {
    testTypes("/** @return {(number|undefined)} */ function a() { return 3; }" +
        "var b = a() === undefined");
  }

  /** Tests that the match method of strings returns nullable arrays. */
  @Test
  public void testBug908701() {
    testTypesWithExterns(
        new TestExternsBuilder().addString().build(),
        lines(
            "/** @type {String} */ var s = new String('foo');", // preserve newlines
            "var b = s.match(/a/) != null;"));
  }

  /** Tests that named types play nicely with subtyping. */
  @Test
  public void testBug908625() {
    testTypes("/** @constructor */function A(){}" +
        "/** @constructor\n * @extends A */function B(){}" +
        "/** @param {B} b" +
        "\n @return {(A|undefined)} */function foo(b){return b}");
  }

  /**
   * Tests that assigning two untyped functions to a variable whose type is inferred and calling
   * this variable is legal.
   */
  @Test
  public void testBug911118a() {
    // verifying the type assigned to function expressions assigned variables
    TypedScope s = parseAndTypeCheckWithScope("var a = function(){};").scope;
    JSType type = s.getVar("a").getType();
    assertThat(type.toString()).isEqualTo("function(): undefined");
  }

  /**
   * Tests that assigning two untyped functions to a variable whose type is inferred and calling
   * this variable is legal.
   */
  @Test
  public void testBug911118b() {
    // verifying the bug example
    testTypes("function nullFunction() {};" +
        "var foo = nullFunction;" +
        "foo = function() {};" +
        "foo();");
  }

  @Test
  public void testBug909000() {
    testTypes("/** @constructor */function A(){}\n" +
        "/** @param {!A} a\n" +
        "@return {boolean}*/\n" +
        "function y(a) { return a }",
        "inconsistent return type\n" +
        "found   : A\n" +
        "required: boolean");
  }

  @Test
  public void testBug930117() {
    testTypes(
        "/** @param {boolean} x */function f(x){}" +
        "f(null);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : null\n" +
        "required: boolean");
  }

  @Test
  public void testBug1484445() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @type {number?} */ Foo.prototype.bar = null;" +
        "/** @type {number?} */ Foo.prototype.baz = null;" +
        "/** @param {Foo} foo */" +
        "function f(foo) {" +
        "  while (true) {" +
        "    if (foo.bar == null && foo.baz == null) {" +
        "      foo.bar;" +
        "    }" +
        "  }" +
        "}");
  }

  @Test
  public void testBug1859535() {
    disableStrictMissingPropertyChecks();
    testTypesWithCommonExterns(
        "/**\n" +
        " * @param {Function} childCtor Child class.\n" +
        " * @param {Function} parentCtor Parent class.\n" +
        " */" +
        "var inherits = function(childCtor, parentCtor) {" +
        "  /** @constructor */" +
        "  function tempCtor() {};" +
        "  tempCtor.prototype = parentCtor.prototype;" +
        "  childCtor.superClass_ = parentCtor.prototype;" +
        "  childCtor.prototype = new tempCtor();" +
        "  /** @override */ childCtor.prototype.constructor = childCtor;" +
        "};" +
        "/**" +
        " * @param {Function} constructor\n" +
        " * @param {Object} var_args\n" +
        " * @return {Object}\n" +
        " */" +
        "var factory = function(constructor, var_args) {" +
        "  /** @constructor */" +
        "  var tempCtor = function() {};" +
        "  tempCtor.prototype = constructor.prototype;" +
        "  var obj = new tempCtor();" +
        "  constructor.apply(obj, arguments);" +
        "  return obj;" +
        "};");
  }

  @Test
  public void testBug1940591() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @type {Object} */" +
        "var a = {};\n" +
        "/** @type {number} */\n" +
        "a.name = 0;\n" +
        "/**\n" +
        " * @param {Function} x anything.\n" +
        " */\n" +
        "a.g = function(x) { x.name = 'a'; }");
  }

  @Test
  public void testBug1942972() {
    testTypes(
        "var google = {\n" +
        "  gears: {\n" +
        "    factory: {},\n" +
        "    workerPool: {}\n" +
        "  }\n" +
        "};\n" +
        "\n" +
        "google.gears = {factory: {}};\n");
  }

  @Test
  public void testBug1943776() {
    testTypes(
        "/** @return  {{foo: Array}} */" +
        "function bar() {" +
        "  return {foo: []};" +
        "}");
  }

  @Test
  public void testBug1987544() {
    testTypes(
        "/** @param {string} x */ function foo(x) {}" +
        "var duration;" +
        "if (true && !(duration = 3)) {" +
        " foo(duration);" +
        "}",
        "actual parameter 1 of foo does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testBug1940769() {
    testTypesWithCommonExterns(
        "/** @return {!Object} */ " +
        "function proto(obj) { return obj.prototype; }" +
        "/** @constructor */ function Map() {}" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Map}\n" +
        " */" +
        "function Map2() { Map.call(this); };" +
        "Map2.prototype = proto(Map);");
  }

  @Test
  public void testBug2335992() {
    disableStrictMissingPropertyChecks();

    testTypes(
        "/** @return {*} */ function f() { return 3; }" +
        "var x = f();" +
        "/** @type {string} */" +
        "x.y = 3;",
        "assignment\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testBug2341812() {
    disableStrictMissingPropertyChecks();

    testTypes(
        "/** @interface */" +
        "function EventTarget() {}" +
        "/** @constructor \n * @implements {EventTarget} */" +
        "function Node() {}" +
        "/** @type {number} */ Node.prototype.index;" +
        "/** @param {EventTarget} x \n * @return {string} */" +
        "function foo(x) { return x.index; }");
  }

  @Test
  public void testStrictInterfaceCheck() {
    testTypes(
        lines(
            "/** @interface */",
            "function EventTarget() {}",
            "/** @constructor \n * @implements {EventTarget} */",
            "function Node() {}",
            "/** @type {number} */ Node.prototype.index;",
            "/** @param {EventTarget} x \n * @return {string} */",
            "function foo(x) { return x.index; }"),
        "Property index never defined on EventTarget");
  }

  @Test
  public void testBug7701884() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        lines(
            "/**",
            " * @param {Array<T>} x",
            " * @param {function(T)} y",
            " * @template T",
            " */",
            "var forEach = function(x, y) {",
            "  for (var i = 0; i < x.length; i++) y(x[i]);",
            "};",
            "/** @param {number} x */",
            "function f(x) {}",
            "/** @param {?} x */",
            "function h(x) {",
            "  var top = null;",
            "  forEach(x, function(z) { top = z; });",
            "  if (top) f(top);",
            "}"));
  }

  @Test
  public void testBug8017789() {
    testTypes(
        "/** @param {(map|function())} isResult */" +
        "var f = function(isResult) {" +
        "    while (true)" +
        "        isResult['t'];" +
        "};" +
        "/** @typedef {Object<string, number>} */" +
        "var map;");
  }

  @Test
  public void testBug12441160() {
    testTypes(
        "/** @param {string} a */ \n" +
        "function use(a) {};\n" +
        "/**\n" +
        " * @param {function(this:THIS)} fn\n" +
        " * @param {THIS} context \n" +
        " * @constructor\n" +
        " * @template THIS\n" +
        " */\n" +
        "var P = function(fn, context) {}\n" +
        "\n" +
        "/** @constructor */\n" +
        "function C() { /** @type {number} */ this.a = 1; }\n" +
        "\n" +
        "/** @return {P} */ \n" +
        "C.prototype.method = function() {\n" +
        "   return new P(function() { use(this.a); }, this);\n" +
        "};\n" +
        "\n",
        "actual parameter 1 of use does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testBug13641083a() {
    testTypes(
        "/** @constructor @struct */ function C() {};" +
        "new C().bar;",
        TypeCheck.INEXISTENT_PROPERTY);
  }

  @Test
  public void testBug13641083b() {
    testTypes(
        "/** @type {?} */ var C;" +
        "C.bar + 1;",
        TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
  }

  @Test
  public void testTypedefBeforeUse() {
    testTypes(
        "/** @typedef {Object<string, number>} */" +
        "var map;" +
        "/** @param {(map|function())} isResult */" +
        "var f = function(isResult) {" +
        "    while (true)" +
        "        isResult['t'];" +
        "};");
  }

  @Test
  public void testScopedConstructors1() {
    testTypes(
        "function foo1() { " +
        "  /** @constructor */ function Bar() { " +
        "    /** @type {number} */ this.x = 3;" +
        "  }" +
        "}" +
        "function foo2() { " +
        "  /** @constructor */ function Bar() { " +
        "    /** @type {string} */ this.x = 'y';" +
        "  }" +
        "  /** " +
        "   * @param {Bar} b\n" +
        "   * @return {number}\n" +
        "   */" +
        "  function baz(b) { return b.x; }" +
        "}",
        "inconsistent return type\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testScopedConstructors2() {
    testTypes(
        "/** @param {Function} f */" +
        "function foo1(f) {" +
        "  /** @param {Function} g */" +
        "  f.prototype.bar = function(g) {};" +
        "}");
  }

  @Test
  public void testQualifiedNameInference1() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @type {number?} */ Foo.prototype.bar = null;" +
        "/** @type {number?} */ Foo.prototype.baz = null;" +
        "/** @param {Foo} foo */" +
        "function f(foo) {" +
        "  while (true) {" +
        "    if (!foo.baz) break; " +
        "    foo.bar = null;" +
        "  }" +
        // Tests a bug where this condition always evaluated to true.
        "  return foo.bar == null;" +
        "}");
  }

  @Test
  public void testQualifiedNameInference2() {
    testTypes(
        "var x = {};" +
        "x.y = c;" +
        "function f(a, b) {" +
        "  if (a) {" +
        "    if (b) " +
        "      x.y = 2;" +
        "    else " +
        "      x.y = 1;" +
        "  }" +
        "  return x.y == null;" +
        "}");
  }

  @Test
  public void testQualifiedNameInference3() {
    testTypes(
        "var x = {};" +
        "x.y = c;" +
        "function f(a, b) {" +
        "  if (a) {" +
        "    if (b) " +
        "      x.y = 2;" +
        "    else " +
        "      x.y = 1;" +
        "  }" +
        "  return x.y == null;" +
        "} function g() { x.y = null; }");
  }

  @Test
  public void testQualifiedNameInference4() {
    testTypes(
        "/** @param {string} x */ function f(x) {}\n" +
        "/**\n" +
        " * @param {?string} x \n" +
        " * @constructor\n" +
        " */" +
        "function Foo(x) { this.x_ = x; }\n" +
        "Foo.prototype.bar = function() {" +
        "  if (this.x_) { f(this.x_); }" +
        "};");
  }

  @Test
  public void testQualifiedNameInference5() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "var ns = {}; " +
        "(function() { " +
        "    /** @param {number} x */ ns.foo = function(x) {}; })();" +
        "(function() { ns.foo(true); })();",
        "actual parameter 1 of ns.foo does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testQualifiedNameInference6() {
    testTypes(
        "/** @const */ var ns = {}; " +
        "/** @param {number} x */ ns.foo = function(x) {};" +
        "(function() { " +
        "    ns.foo = function(x) {};" +
        "    ns.foo(true); " +
        "})();",
        "actual parameter 1 of ns.foo does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testQualifiedNameInference7() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "var ns = {}; " +
        "(function() { " +
        "  /** @constructor \n * @param {number} x */ " +
        "  ns.Foo = function(x) {};" +
        "  /** @param {ns.Foo} x */ function f(x) {}" +
        "  f(new ns.Foo(true));" +
        "})();",
        "actual parameter 1 of ns.Foo does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testQualifiedNameInference8() {
    disableStrictMissingPropertyChecks();
    testClosureTypesMultipleWarnings(
        "var ns = {}; " +
        "(function() { " +
        "  /** @constructor \n * @param {number} x */ " +
        "  ns.Foo = function(x) {};" +
        "})();" +
        "/** @param {ns.Foo} x */ function f(x) {}" +
        "f(new ns.Foo(true));",
        ImmutableList.of(
            "actual parameter 1 of ns.Foo does not match formal parameter\n" +
            "found   : boolean\n" +
            "required: number"));
  }

  @Test
  public void testQualifiedNameInference9() {
    testTypes(
        "var ns = {}; " +
        "ns.ns2 = {}; " +
        "(function() { " +
        "  /** @constructor \n * @param {number} x */ " +
        "  ns.ns2.Foo = function(x) {};" +
        "  /** @param {ns.ns2.Foo} x */ function f(x) {}" +
        "  f(new ns.ns2.Foo(true));" +
        "})();",
        "actual parameter 1 of ns.ns2.Foo does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testQualifiedNameInference10() {
    testTypes(
        "var ns = {}; " +
        "ns.ns2 = {}; " +
        "(function() { " +
        "  /** @interface */ " +
        "  ns.ns2.Foo = function() {};" +
        "  /** @constructor \n * @implements {ns.ns2.Foo} */ " +
        "  function F() {}" +
        "  (new F());" +
        "})();");
  }

  @Test
  public void testQualifiedNameInference11() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "function f() {" +
        "  var x = new Foo();" +
        "  x.onload = function() {" +
        "    x.onload = null;" +
        "  };" +
        "}");
  }

  @Test
  public void testQualifiedNameInference12() {
    disableStrictMissingPropertyChecks();
    // We should be able to tell that the two 'this' properties
    // are different.
    testTypes(
        lines(
            "/** @param {function(this:Object)} x */ function f(x) {}",
            "/** @constructor */ function Foo() {",
            "  /** @type {number} */ this.bar = 3;",
            "  f(function() { this.bar = true; });",
            "}"));
  }

  @Test
  public void testQualifiedNameInference13() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "function f(z) {" +
        "  var x = new Foo();" +
        "  if (z) {" +
        "    x.onload = function() {};" +
        "  } else {" +
        "    x.onload = null;" +
        "  };" +
        "}");
  }

  @Test
  public void testQualifiedNameInference14() {
    // Unconditional blocks don't cause functions to be treated as inferred.
    disableStrictMissingPropertyChecks();
    testTypes(
        lines(
            "/** @constructor */ function Foo() {}",
            "function f(z) {",
            "  var x = new Foo();",
            "  {",
            "    x.onload = function() {};",
            "  }",
            "  {",
            "    x.onload = null;",
            "  };",
            "}"),
        lines(
            "assignment",
            "found   : null",
            "required: function(): undefined"));
  }

  @Test
  public void testScopeQualifiedNamesOnThis() {
    // Ensure that we don't flow-scope qualified names on 'this' too broadly.
    testTypes(
        lines(
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
            "};"));
  }

  @Test
  public void testSheqRefinedScope() {
    Node n = parseAndTypeCheck(
        "/** @constructor */function A() {}\n" +
        "/** @constructor \n @extends A */ function B() {}\n" +
        "/** @return {number} */\n" +
        "B.prototype.p = function() { return 1; }\n" +
        "/** @param {A} a\n @param {B} b */\n" +
        "function f(a, b) {\n" +
        "  b.p();\n" +
        "  if (a === b) {\n" +
        "    b.p();\n" +
        "  }\n" +
        "}");
    Node nodeC = n.getLastChild().getLastChild().getLastChild().getLastChild()
        .getLastChild().getLastChild();
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
    Node n = parseAndTypeCheck(
        "/** @constructor */ function Foo() {}\n" +
        "Foo.prototype.a = 1;" +
        "(new Foo).a;");

    Node node = n.getLastChild().getFirstChild();
    assertThat(node.getJSType().isUnknownType()).isFalse();
    assertThat(node.getJSType().isNumber()).isTrue();
  }

  @Test
  public void testNew1() {
    testTypes("new 4", TypeCheck.NOT_A_CONSTRUCTOR);
  }

  @Test
  public void testNew2() {
    testTypes("var Math = {}; new Math()", TypeCheck.NOT_A_CONSTRUCTOR);
  }

  @Test
  public void testNew3() {
    testTypes("new Date()");
  }

  @Test
  public void testNew4() {
    testTypes("/** @constructor */function A(){}; new A();");
  }

  @Test
  public void testNew5() {
    testTypes("function A(){}; new A();", TypeCheck.NOT_A_CONSTRUCTOR);
  }

  @Test
  public void testNew6() {
    TypeCheckResult p =
      parseAndTypeCheckWithScope("/** @constructor */function A(){};" +
      "var a = new A();");

    JSType aType = p.scope.getVar("a").getType();
    assertThat(aType).isInstanceOf(ObjectType.class);
    ObjectType aObjectType = (ObjectType) aType;
    assertThat(aObjectType.getConstructor().getReferenceName()).isEqualTo("A");
  }

  @Test
  public void testNew7() {
    testTypes("/** @param {Function} opt_constructor */" +
        "function foo(opt_constructor) {" +
        "if (opt_constructor) { new opt_constructor; }" +
        "}");
  }

  @Test
  public void testNew8() {
    testTypes("/** @param {Function} opt_constructor */" +
        "function foo(opt_constructor) {" +
        "new opt_constructor;" +
        "}");
  }

  @Test
  public void testNew9() {
    testTypes("/** @param {Function} opt_constructor */" +
        "function foo(opt_constructor) {" +
        "new (opt_constructor || Array);" +
        "}");
  }

  @Test
  public void testNew10() {
    testTypes("var goog = {};" +
        "/** @param {Function} opt_constructor */" +
        "goog.Foo = function(opt_constructor) {" +
        "new (opt_constructor || Array);" +
        "}");
  }

  @Test
  public void testNew11() {
    testTypes("/** @param {Function} c1 */" +
        "function f(c1) {" +
        "  var c2 = function(){};" +
        "  c1.prototype = new c2;" +
        "}", TypeCheck.NOT_A_CONSTRUCTOR);
  }

  @Test
  public void testNew12() {
    TypeCheckResult p = parseAndTypeCheckWithScope("var a = new Array();");
    TypedVar a = p.scope.getVar("a");

    assertTypeEquals(getNativeArrayType(), a.getType());
  }

  @Test
  public void testNew13() {
    TypeCheckResult p = parseAndTypeCheckWithScope(
        "/** @constructor */function FooBar(){};" +
        "var a = new FooBar();");
    TypedVar a = p.scope.getVar("a");

    assertThat(a.getType()).isInstanceOf(ObjectType.class);
    assertThat(a.getType().toString()).isEqualTo("FooBar");
  }

  @Test
  public void testNew14() {
    TypeCheckResult p = parseAndTypeCheckWithScope(
        "/** @constructor */var FooBar = function(){};" +
        "var a = new FooBar();");
    TypedVar a = p.scope.getVar("a");

    assertThat(a.getType()).isInstanceOf(ObjectType.class);
    assertThat(a.getType().toString()).isEqualTo("FooBar");
  }

  @Test
  public void testNew15() {
    TypeCheckResult p = parseAndTypeCheckWithScope(
        "var goog = {};" +
        "/** @constructor */goog.A = function(){};" +
        "var a = new goog.A();");
    TypedVar a = p.scope.getVar("a");

    assertThat(a.getType()).isInstanceOf(ObjectType.class);
    assertThat(a.getType().toString()).isEqualTo("goog.A");
  }

  @Test
  public void testNew16() {
    testTypes(
        "/** \n" +
        " * @param {string} x \n" +
        " * @constructor \n" +
        " */" +
        "function Foo(x) {}" +
        "function g() { new Foo(1); }",
        "actual parameter 1 of Foo does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testNew17() {
    testTypes("var goog = {}; goog.x = 3; new goog.x",
              "cannot instantiate non-constructor");
  }

  @Test
  public void testNew18() {
    testTypes("var goog = {};" +
              "/** @constructor */ goog.F = function() {};" +
              "/** @constructor */ goog.G = goog.F;");
  }

  @Test
  public void testNew19() {
    testTypes(
        "/** @constructor @abstract */ var Foo = function() {}; var foo = new Foo();",
        INSTANTIATE_ABSTRACT_CLASS);
  }

  @Test
  public void testNew20() {
    testTypes(lines(
        "/** @constructor @abstract */",
        "function Bar() {};",
        "/** @return {function(new:Bar)} */",
        "function foo() {}",
        "var Foo = foo();",
        "var f = new Foo;"),
        INSTANTIATE_ABSTRACT_CLASS);
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

  /**
   * Type checks a NAME node and retrieve its type.
   */
  private JSType testNameNode(String name) {
    Node node = Node.newString(Token.NAME, name);
    Node parent = new Node(Token.SCRIPT, node);
    parent.setInputId(new InputId("code"));

    Node externs = new Node(Token.SCRIPT);
    externs.setInputId(new InputId("externs"));

    IR.root(externs, parent);

    makeTypeCheck().processForTesting(null, parent);
    return node.getJSType();
  }

  @Test
  public void testBitOperation1() {
    testTypes("/**@return {void}*/function foo(){ ~foo(); }",
        "operator ~ cannot be applied to undefined");
  }

  @Test
  public void testBitOperation2() {
    testTypes("/**@return {void}*/function foo(){var a = foo()<<3;}",
        "operator << cannot be applied to undefined");
  }

  @Test
  public void testBitOperation3() {
    testTypes("/**@return {void}*/function foo(){var a = 3<<foo();}",
        "operator << cannot be applied to undefined");
  }

  @Test
  public void testBitOperation4() {
    testTypes("/**@return {void}*/function foo(){var a = foo()>>>3;}",
        "operator >>> cannot be applied to undefined");
  }

  @Test
  public void testBitOperation5() {
    testTypes("/**@return {void}*/function foo(){var a = 3>>>foo();}",
        "operator >>> cannot be applied to undefined");
  }

  @Test
  public void testBitOperation6() {
    testTypes("/**@return {!Object}*/function foo(){var a = foo()&3;}",
        "bad left operand to bitwise operator\n" +
        "found   : Object\n" +
        "required: (boolean|null|number|string|undefined)");
  }

  @Test
  public void testBitOperation7() {
    compiler.getOptions().setWarningLevel(
        DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    testTypes("var x = null; x |= undefined; x &= 3; x ^= '3'; x |= true;");
  }

  @Test
  public void testBitOperation8() {
    compiler.getOptions().setWarningLevel(
        DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    testTypes("var x = void 0; x |= new Number(3);");
  }

  @Test
  public void testBitOperation9() {
    compiler.getOptions().setWarningLevel(
        DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    testTypes("var x = void 0; x |= {};",
        "bad right operand to bitwise operator\n" +
        "found   : {}\n" +
        "required: (boolean|null|number|string|undefined)");
  }

  @Test
  public void testCall1() {
    testTypes("3();", "number expressions are not callable");
  }

  @Test
  public void testCall2() {
    testTypes("/** @param {!Number} foo*/function bar(foo){ bar('abc'); }",
        "actual parameter 1 of bar does not match formal parameter\n" +
        "found   : string\n" +
        "required: Number");
  }

  @Test
  public void testCall3() {
    // We are checking that an unresolved named type can successfully
    // meet with a functional type to produce a callable type.
    testTypes("/** @type {Function|undefined} */var opt_f;" +
        "/** @type {some.unknown.type} */var f1;" +
        "var f2 = opt_f || f1;" +
        "f2();",
        "Bad type annotation. Unknown type some.unknown.type");
  }

  @Test
  public void testCall4() {
    testTypes("/**@param {!RegExp} a*/var foo = function bar(a){ bar('abc'); }",
        "actual parameter 1 of bar does not match formal parameter\n" +
        "found   : string\n" +
        "required: RegExp");
  }

  @Test
  public void testCall5() {
    testTypes("/**@param {!RegExp} a*/var foo = function bar(a){ foo('abc'); }",
        "actual parameter 1 of foo does not match formal parameter\n" +
        "found   : string\n" +
        "required: RegExp");
  }

  @Test
  public void testCall6() {
    testTypes("/** @param {!Number} foo*/function bar(foo){}" +
        "bar('abc');",
        "actual parameter 1 of bar does not match formal parameter\n" +
        "found   : string\n" +
        "required: Number");
  }

  @Test
  public void testCall7() {
    testTypes("/** @param {!RegExp} a*/var foo = function bar(a){};" +
        "foo('abc');",
        "actual parameter 1 of foo does not match formal parameter\n" +
        "found   : string\n" +
        "required: RegExp");
  }

  @Test
  public void testCall8() {
    testTypes("/** @type {Function|number} */var f;f();",
        "(Function|number) expressions are " +
        "not callable");
  }

  @Test
  public void testCall9() {
    testTypes(
        "var goog = {};" +
        "/** @constructor */ goog.Foo = function() {};" +
        "/** @param {!goog.Foo} a */ var bar = function(a){};" +
        "bar('abc');",
        "actual parameter 1 of bar does not match formal parameter\n" +
        "found   : string\n" +
        "required: goog.Foo");
  }

  @Test
  public void testCall10() {
    testTypes("/** @type {Function} */var f;f();");
  }

  @Test
  public void testCall11() {
    testTypes("var f = new Function(); f();");
  }

  @Test
  public void testCall12() {
    testTypes(
        lines(
            "/**",
            " * @param {*} x",
            " * @return {number}",
            " */",
            "function f(x, y) {",
            "  return x && x.foo();",
            "}"),
        new String[] {
          lines(
              "inconsistent return type", // preserve new line
              "found   : *",
              "required: number"),
          "Property foo never defined on *"
        });
  }

  @Test
  public void testCall13() {
    // Test a case where we use inferred types across scopes.
    testTypes(
        lines(
            "var x;",
            "function useX() { var /** string */ str = x(); }",
            "function setX() { x = /** @return {number} */ () => 3; }"),
        lines(
            "initializing variable", // preserve new line
            "found   : number",
            "required: string"));
  }

  @Test
  public void testAbstractMethodCall1() {
    // Converted from Closure style "goog.base" super call
    testTypesWithCommonExterns(
        lines(
            "/** @constructor @abstract */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "B.superClass_ = A.prototype",
            "/** @override */ B.prototype.foo = function() { B.superClass_.foo.call(this); };"),
        "Abstract super method A.prototype.foo cannot be called");
  }

  @Test
  public void testAbstractMethodCall2() {
    // Converted from Closure style "goog.base" super call, with namespace
    testTypesWithCommonExterns(
        lines(
            "/** @const */ var ns = {};",
            "/** @constructor @abstract */ ns.A = function() {};",
            "/** @abstract */ ns.A.prototype.foo = function() {};",
            "/** @constructor @extends {ns.A} */ ns.B = function() {};",
            "ns.B.superClass_ = ns.A.prototype",
            "/** @override */ ns.B.prototype.foo = function() {",
            "  ns.B.superClass_.foo.call(this);",
            "};"),
        "Abstract super method ns.A.prototype.foo cannot be called");
  }

  @Test
  public void testAbstractMethodCall3() {
    // Converted from ES6 super call
    testTypesWithCommonExterns(
        lines(
            "/** @constructor @abstract */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() { A.prototype.foo.call(this); };"),
        "Abstract super method A.prototype.foo cannot be called");
  }

  @Test
  public void testAbstractMethodCall4() {
    testTypesWithCommonExterns(
        lines(
            "/** @const */ var ns = {};",
            "/** @constructor @abstract */ ns.A = function() {};",
            "ns.A.prototype.foo = function() {};",
            "/** @constructor @extends {ns.A} */ ns.B = function() {};",
            "ns.B.superClass_ = ns.A.prototype",
            "/** @override */ ns.B.prototype.foo = function() {",
            "  ns.B.superClass_.foo.call(this);",
            "};"));
  }

  @Test
  public void testAbstractMethodCall5() {
    testTypesWithCommonExterns(
        lines(
            "/** @constructor @abstract */ var A = function() {};",
            "A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() { A.prototype.foo.call(this); };"));
  }

  @Test
  public void testAbstractMethodCall6() {
    testTypesWithCommonExterns(
        lines(
            "/** @const */ var ns = {};",
            "/** @constructor @abstract */ ns.A = function() {};",
            "ns.A.prototype.foo = function() {};",
            "ns.A.prototype.foo.bar = function() {};",
            "/** @constructor @extends {ns.A} */ ns.B = function() {};",
            "ns.B.superClass_ = ns.A.prototype",
            "/** @override */ ns.B.prototype.foo = function() {",
            "  ns.B.superClass_.foo.bar.call(this);",
            "};"));
  }

  @Test
  public void testAbstractMethodCall7() {
    testTypesWithCommonExterns(
        lines(
            "/** @constructor @abstract */ var A = function() {};",
            "A.prototype.foo = function() {};",
            "A.prototype.foo.bar = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() { A.prototype.foo.bar.call(this); };"));
  }

  @Test
  public void testAbstractMethodCall8() {
    testTypesWithCommonExterns(
        lines(
            "/** @constructor @abstract */ var A = function() {};",
            "A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() { A.prototype.foo['call'](this); };"));
  }

  @Test
  public void testAbstractMethodCall9() {
    testTypesWithCommonExterns(
        lines(
            "/** @struct @constructor */ var A = function() {};",
            "A.prototype.foo = function() {};",
            "/** @struct @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() {",
            "  (function() {",
            "    return A.prototype.foo.call($jscomp$this);",
            "  })();",
            "};"));
  }

  @Test
  public void testAbstractMethodCall10() {
    testTypesWithCommonExterns(
        lines(
            "/** @constructor @abstract */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "A.prototype.foo.call(new Subtype);"),
        "Abstract super method A.prototype.foo cannot be called");
  }

  @Test
  public void testAbstractMethodCall11() {
    testTypesWithCommonExterns(
        lines(
            "/** @constructor @abstract */ function A() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ function B() {};",
            "/** @override */ B.prototype.foo = function() {};",
            "var abstractMethod = A.prototype.foo;",
            "abstractMethod.call(new B);"),
        "Abstract super method A.prototype.foo cannot be called");
  }

  @Test
  public void testAbstractMethodCall12() {
    testTypesWithCommonExterns(
        lines(
            "/** @constructor @abstract */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "B.superClass_ = A.prototype",
            "/** @override */ B.prototype.foo = function() { B.superClass_.foo.apply(this); };"),
        "Abstract super method A.prototype.foo cannot be called");
  }

  @Test
  public void testAbstractMethodCall13() {
    // Calling abstract @constructor is allowed
    testTypesWithCommonExterns(
        lines(
            "/** @constructor @abstract */ var A = function() {};",
            "/** @constructor @extends {A} */ var B = function() { A.call(this); };"));
  }

  @Test
  public void testAbstractMethodCall_Indirect1() {
    testTypesWithCommonExterns(
        lines(
            "/** @constructor @abstract */ function A() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ function B() {};",
            "/** @override */ B.prototype.foo = function() {};",
            "var abstractMethod = A.prototype.foo;",
            "(0, abstractMethod).call(new B);"),
        "Abstract super method A.prototype.foo cannot be called");
  }

  @Test
  public void testAbstractMethodCall_Indirect2() {
    testTypesWithCommonExterns(
        lines(
            "/** @constructor @abstract */ function A() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ function B() {};",
            "/** @override */ B.prototype.foo = function() {};",
            "var abstractMethod = A.prototype.foo;",
            "(abstractMethod = abstractMethod).call(new B);"),
        "Abstract super method A.prototype.foo cannot be called");
  }

  @Test
  public void testAbstractMethodCall_Es6Class() {
    testTypesWithCommonExterns(
        lines(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "  bar() {",
            "    this.foo();",
            "  }",
            "}",
            "class Sub extends Base {",
            "  /** @override */",
            "  foo() {}",
            "  /** @override */",
            "  bar() {",
            "    this.foo();",
            "  }",
            "}"));
  }

  @Test
  public void testAbstractMethodCall_Es6Class_prototype() {
    testTypesWithCommonExterns(
        lines(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "class Sub extends Base {",
            "  /** @override */",
            "  foo() {}",
            "  bar() {",
            "    Sub.prototype.foo();",
            "  }",
            "}"));
  }

  @Test
  public void testAbstractMethodCall_Es6Class_prototype_warning() {
    testTypesWithCommonExterns(
        lines(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "class Sub extends Base {",
            "  /** @override */",
            "  foo() {}",
            "  bar() {",
            "    Base.prototype.foo();",
            "  }",
            "}"),
        "Abstract super method Base.prototype.foo cannot be called");
  }

  @Test
  public void testNonAbstractMethodCall_Es6Class_prototype() {
    testTypesWithCommonExterns(
        lines(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "  bar() {}",
            "}",
            "class Sub extends Base {",
            "  /** @override */",
            "  foo() {}",
            "  /** @override */",
            "  bar() {",
            "    Base.prototype.bar();",
            "  }",
            "}"));
  }

  // GitHub issue #2262: https://github.com/google/closure-compiler/issues/2262
  @Test
  public void testAbstractMethodCall_Es6ClassWithSpread() {
    testTypesWithExterns(
        new TestExternsBuilder().addObject().addArray().addArguments().build(),
        lines(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "class Sub extends Base {",
            "  /** @override */",
            "  foo() {}",
            "  /** @param {!Array} arr */",
            "  bar(arr) {",
            "    this.foo.apply(this, [].concat(arr));",
            "  }",
            "}"));
  }

  @Test
  public void testFunctionCall1() {
    testTypesWithCommonExterns(
        "/** @param {number} x */ var foo = function(x) {};" +
        "foo.call(null, 3);");
  }

  @Test
  public void testFunctionCall2() {
    testTypesWithCommonExterns(
        "/** @param {number} x */ var foo = function(x) {};" +
        "foo.call(null, 'bar');",
        "actual parameter 2 of foo.call does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testFunctionCall3() {
    testTypesWithCommonExterns(
        "/** @param {number} x \n * @constructor */ " +
        "var Foo = function(x) { this.bar.call(null, x); };" +
        "/** @type {function(number)} */ Foo.prototype.bar;");
  }

  @Test
  public void testFunctionCall4() {
    testTypesWithCommonExterns(
        "/** @param {string} x \n * @constructor */ " +
        "var Foo = function(x) { this.bar.call(null, x); };" +
        "/** @type {function(number)} */ Foo.prototype.bar;",
        "actual parameter 2 of this.bar.call " +
        "does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testFunctionCall5() {
    testTypesWithCommonExterns(
        "/** @param {Function} handler \n * @constructor */ " +
        "var Foo = function(handler) { handler.call(this, x); };");
  }

  @Test
  public void testFunctionCall6() {
    testTypesWithCommonExterns(
        "/** @param {Function} handler \n * @constructor */ " +
        "var Foo = function(handler) { handler.apply(this, x); };");
  }

  @Test
  public void testFunctionCall7() {
    testTypesWithCommonExterns(
        "/** @param {Function} handler \n * @param {Object} opt_context */ " +
        "var Foo = function(handler, opt_context) { " +
        "  handler.call(opt_context, x);" +
        "};");
  }

  @Test
  public void testFunctionCall8() {
    testTypesWithCommonExterns(
        "/** @param {Function} handler \n * @param {Object} opt_context */ " +
        "var Foo = function(handler, opt_context) { " +
        "  handler.apply(opt_context, x);" +
        "};");
  }

  @Test
  public void testFunctionCall9() {
    testTypesWithCommonExterns(
        "/** @constructor\n * @template T\n **/ function Foo() {}\n" +
        "/** @param {T} x */ Foo.prototype.bar = function(x) {}\n" +
        "var foo = /** @type {Foo<string>} */ (new Foo());\n" +
        "foo.bar(3);",
        "actual parameter 1 of Foo.prototype.bar does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testFunctionBind1() {
    testTypesWithCommonExterns(
        "/** @type {function(string, number): boolean} */" +
        "function f(x, y) { return true; }" +
        "f.bind(null, 3);",
        "actual parameter 2 of f.bind does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testFunctionBind2() {
    testTypesWithCommonExterns(
        "/** @type {function(number): boolean} */" +
        "function f(x) { return true; }" +
        "f(f.bind(null, 3)());",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testFunctionBind3() {
    testTypesWithCommonExterns(
        "/** @type {function(number, string): boolean} */" +
        "function f(x, y) { return true; }" +
        "f.bind(null, 3)(true);",
        "actual parameter 1 of function does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: string");
  }

  @Test
  public void testFunctionBind4() {
    testTypesWithCommonExterns(
        lines(
            "/** @param {...number} x */", // preserve newlines
            "function f(x) {}",
            "f.bind(null, 3, 3, 3)(true);"),
        lines(
            "actual parameter 1 of function does not match formal parameter",
            "found   : boolean",
            "required: number"));
  }

  @Test
  public void testFunctionBind5() {
    testTypesWithCommonExterns(
        lines(
            "/** @param {...number} x */", // preserve newlines
            "function f(x) {}",
            "f.bind(null, true)(3, 3, 3);"),
        lines(
            "actual parameter 2 of f.bind does not match formal parameter",
            "found   : boolean",
            "required: number"));
  }

  @Test
  public void testFunctionBind6() {
    testTypesWithCommonExterns(lines(
        "/** @constructor */",
        "function MyType() {",
        "  /** @type {number} */",
        "  this.x = 0;",
        "  var f = function() {",
        "    this.x = 'str';",
        "  }.bind(this);",
        "}"), lines(
        "assignment to property x of MyType",
        "found   : string",
        "required: number"));
  }

  @Test
  public void testFunctionBind7() {
    testTypesWithCommonExterns(lines(
        "/** @constructor */",
        "function MyType() {",
        "  /** @type {number} */",
        "  this.x = 0;",
        "}",
        "var m = new MyType;",
        "(function f() {this.x = 'str';}).bind(m);"),
        lines(
        "assignment to property x of MyType",
        "found   : string",
        "required: number"));
  }

  @Test
  public void testFunctionBind8() {
    testTypesWithCommonExterns(lines(
        "/** @constructor */",
        "function MyType() {}",
        "",
        "/** @constructor */",
        "function AnotherType() {}",
        "AnotherType.prototype.foo = function() {};",
        "",
        "/** @type {?} */",
        "var m = new MyType;",
        "(function f() {this.foo();}).bind(m);"));
  }

  @Test
  public void testFunctionBind9() {
    testTypes(lines(
        "/** @constructor */",
        "function MyType() {}",
        "",
        "/** @constructor */",
        "function AnotherType() {}",
        "AnotherType.prototype.foo = function() {};",
        "",
        "var m = new MyType;",
        "(function f() {this.foo();}).bind(m);"),
        TypeCheck.INEXISTENT_PROPERTY);
  }

  @Test
  public void testGoogBind1() {
    testClosureTypes(
        "var goog = {}; goog.bind = function(var_args) {};" +
        "/** @type {function(number): boolean} */" +
        "function f(x, y) { return true; }" +
        "f(goog.bind(f, null, 'x')());",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testGoogBind2() {
    // TODO(nicksantos): We do not currently type-check the arguments
    // of the goog.bind.
    testClosureTypes(
        "var goog = {}; goog.bind = function(var_args) {};" +
        "/** @type {function(boolean): boolean} */" +
        "function f(x, y) { return true; }" +
        "f(goog.bind(f, null, 'x')());",
        null);
  }

  @Test
  public void testCast2() {
    // can upcast to a base type.
    testTypes("/** @constructor */function base() {}\n" +
        "/** @constructor\n @extends {base} */function derived() {}\n" +
        "/** @type {base} */ var baz = new derived();\n");
  }

  @Test
  public void testCast3() {
    // cannot downcast
    testTypes("/** @constructor */function base() {}\n" +
        "/** @constructor @extends {base} */function derived() {}\n" +
        "/** @type {!derived} */ var baz = new base();\n",
        "initializing variable\n" +
        "found   : base\n" +
        "required: derived");
  }

  @Test
  public void testCast3a() {
    // cannot downcast
    testTypes("/** @constructor */function Base() {}\n" +
        "/** @constructor @extends {Base} */function Derived() {}\n" +
        "var baseInstance = new Base();" +
        "/** @type {!Derived} */ var baz = baseInstance;\n",
        "initializing variable\n" +
        "found   : Base\n" +
        "required: Derived");
  }

  @Test
  public void testCast4() {
    // downcast must be explicit
    testTypes("/** @constructor */function base() {}\n" +
        "/** @constructor\n * @extends {base} */function derived() {}\n" +
        "/** @type {!derived} */ var baz = " +
        "/** @type {!derived} */(new base());\n");
  }

  @Test
  public void testCast4Types() {
    // downcast must be explicit
    Node root = parseAndTypeCheck(
        "/** @constructor */function base() {}\n" +
        "/** @constructor\n * @extends {base} */function derived() {}\n" +
        "/** @type {!derived} */ var baz = " +
        "/** @type {!derived} */(new base());\n");
    Node castedExprNode = root.getLastChild().getFirstFirstChild().getFirstChild();
    assertThat(castedExprNode.getJSType().toString()).isEqualTo("derived");
    assertThat(castedExprNode.getJSTypeBeforeCast().toString()).isEqualTo("base");
  }

  @Test
  public void testCast5() {
    // cannot explicitly cast to an unrelated type
    testTypes("/** @constructor */function foo() {}\n" +
        "/** @constructor */function bar() {}\n" +
        "var baz = /** @type {!foo} */(new bar);\n",
        "invalid cast - must be a subtype or supertype\n" +
        "from: bar\n" +
        "to  : foo");
  }

  @Test
  public void testCast5a() {
    // cannot explicitly cast to an unrelated type
    testTypes("/** @constructor */function foo() {}\n" +
        "/** @constructor */function bar() {}\n" +
        "var barInstance = new bar;\n" +
        "var baz = /** @type {!foo} */(barInstance);\n",
        "invalid cast - must be a subtype or supertype\n" +
        "from: bar\n" +
        "to  : foo");
  }

  @Test
  public void testCast6() {
    // can explicitly cast to a subtype or supertype
    testTypes("/** @constructor */function foo() {}\n" +
        "/** @constructor \n @extends foo */function bar() {}\n" +
        "var baz = /** @type {!bar} */(new bar);\n" +
        "var baz = /** @type {!foo} */(new foo);\n" +
        "var baz = /** @type {bar} */(new bar);\n" +
        "var baz = /** @type {foo} */(new foo);\n" +
        "var baz = /** @type {!foo} */(new bar);\n" +
        "var baz = /** @type {!bar} */(new foo);\n" +
        "var baz = /** @type {foo} */(new bar);\n" +
        "var baz = /** @type {bar} */(new foo);\n");
  }

  @Test
  public void testCast7() {
    testTypes("var x = /** @type {foo} */ (new Object());",
        "Bad type annotation. Unknown type foo");
  }

  @Test
  public void testCast8() {
    testTypes("function f() { return /** @type {foo} */ (new Object()); }",
        "Bad type annotation. Unknown type foo");
  }

  @Test
  public void testCast9() {
    testTypes("var foo = {};" +
        "function f() { return /** @type {foo} */ (new Object()); }",
        "Bad type annotation. Unknown type foo");
  }

  @Test
  public void testCast10() {
    testTypes("var foo = function() {};" +
        "function f() { return /** @type {foo} */ (new Object()); }",
        "Bad type annotation. Unknown type foo");
  }

  @Test
  public void testCast11() {
    testTypes("var goog = {}; goog.foo = {};" +
        "function f() { return /** @type {goog.foo} */ (new Object()); }",
        "Bad type annotation. Unknown type goog.foo");
  }

  @Test
  public void testCast12() {
    testTypes("var goog = {}; goog.foo = function() {};" +
        "function f() { return /** @type {goog.foo} */ (new Object()); }",
        "Bad type annotation. Unknown type goog.foo");
  }

  @Test
  public void testCast13() {
    // Test to make sure that the forward-declaration still allows for
    // a warning.
    testClosureTypes("var goog = {}; " +
        "goog.addDependency('zzz.js', ['goog.foo'], []);" +
        "goog.foo = function() {};" +
        "function f() { return /** @type {goog.foo} */ (new Object()); }",
        "Bad type annotation. Unknown type goog.foo");
  }

  @Test
  public void testCast14() {
    // Test to make sure that the forward-declaration still prevents
    // some warnings.
    testClosureTypes("var goog = {}; " +
        "goog.addDependency('zzz.js', ['goog.bar'], []);" +
        "function f() { return /** @type {goog.bar} */ (new Object()); }",
        null);
  }

  @Test
  public void testCast15() {
    // This fixes a bug where a type cast on an object literal
    // would cause a run-time cast exception if the node was visited
    // more than once.
    //
    // Some code assumes that an object literal must have a object type,
    // while because of the cast, it could have any type (including
    // a union).
    compiler.getOptions().setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2015);

    // This test is specifically checking loose property check behavior.
    disableStrictMissingPropertyChecks();

    testTypes(
        "for (var i = 0; i < 10; i++) {" +
          "var x = /** @type {Object|number} */ ({foo: 3});" +
          "/** @param {number} x */ function f(x) {}" +
          "f(x.foo);" +
          "f([].foo);" +
        "}",
        "Property foo never defined on Array");
  }

  @Test
  public void testCast15b() {
    // This fixes a bug where a type cast on an object literal
    // would cause a run-time cast exception if the node was visited
    // more than once.
    //
    // Some code assumes that an object literal must have a object type,
    // while because of the cast, it could have any type (including
    // a union).
    compiler.getOptions().setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    testTypes(
        lines(
          "for (var i = 0; i < 10; i++) {",
            "var x = /** @type {{foo:number}}|number} */ ({foo: 3});",
            "/** @param {number} x */ function f(x) {}",
            "f(x.foo);",
            "f([].foo);",
          "}"),
        "Property foo never defined on Array");
  }

  @Test
  public void testCast16() {
    // A type cast should not invalidate the checks on the members
    testTypes(
        "for (var i = 0; i < 10; i++) {" +
          "var x = /** @type {Object|number} */ (" +
          "  {/** @type {string} */ foo: 3});" +
        "}",
        "assignment to property foo of {foo: string}\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testCast17a() {
    // Mostly verifying that rhino actually understands these JsDocs.
    testTypes("/** @constructor */ function Foo() {} \n" +
        "/** @type {Foo} */ var x = /** @type {Foo} */ (y)");
  }

  @Test
  public void testCast17b() {
    // Mostly verifying that rhino actually understands these JsDocs.
    testTypes("/** @constructor */ function Foo() {} \n" +
        "/** @type {Foo} */ var x = /** @type {Foo} */ ({})");
  }

  @Test
  public void testCast19() {
    testTypes(
        "var x = 'string';\n" +
        "/** @type {number} */\n" +
        "var y = /** @type {number} */(x);",
        "invalid cast - must be a subtype or supertype\n" +
        "from: string\n" +
        "to  : number");
  }

  @Test
  public void testCast20() {
    testTypes(
        "/** @enum {boolean|null} */\n" +
        "var X = {" +
        "  AA: true," +
        "  BB: false," +
        "  CC: null" +
        "};\n" +
        "var y = /** @type {X} */(true);");
  }

  @Test
  public void testCast21() {
    testTypes(
        "/** @enum {boolean|null} */\n" +
        "var X = {" +
        "  AA: true," +
        "  BB: false," +
        "  CC: null" +
        "};\n" +
        "var value = true;\n" +
        "var y = /** @type {X} */(value);");
  }

  @Test
  public void testCast22() {
    testTypes(
        "var x = null;\n" +
        "var y = /** @type {number} */(x);",
        "invalid cast - must be a subtype or supertype\n" +
        "from: null\n" +
        "to  : number");
  }

  @Test
  public void testCast23() {
    testTypes(
        "var x = null;\n" +
        "var y = /** @type {Number} */(x);");
  }

  @Test
  public void testCast24() {
    testTypes(
        "var x = undefined;\n" +
        "var y = /** @type {number} */(x);",
        "invalid cast - must be a subtype or supertype\n" +
        "from: undefined\n" +
        "to  : number");
  }

  @Test
  public void testCast25() {
    testTypes(
        "var x = undefined;\n" +
        "var y = /** @type {number|undefined} */(x);");
  }

  @Test
  public void testCast26() {
    testTypes(
        "function fn(dir) {\n" +
        "  var node = dir ? 1 : 2;\n" +
        "  fn(/** @type {number} */ (node));\n" +
        "}");
  }

  @Test
  public void testCast27() {
    // C doesn't implement I but a subtype might.
    testTypes(
        "/** @interface */ function I() {}\n" +
        "/** @constructor */ function C() {}\n" +
        "var x = new C();\n" +
        "var y = /** @type {I} */(x);");
  }

  @Test
  public void testCast27a() {
    // C doesn't implement I but a subtype might.
    testTypes(
        "/** @interface */ function I() {}\n" +
        "/** @constructor */ function C() {}\n" +
        "/** @type {C} */ var x ;\n" +
        "var y = /** @type {I} */(x);");
  }

  @Test
  public void testCast28() {
    // C doesn't implement I but a subtype might.
    testTypes(
        "/** @interface */ function I() {}\n" +
        "/** @constructor */ function C() {}\n" +
        "/** @type {!I} */ var x;\n" +
        "var y = /** @type {C} */(x);");
  }

  @Test
  public void testCast28a() {
    // C doesn't implement I but a subtype might.
    testTypes(
        "/** @interface */ function I() {}\n" +
        "/** @constructor */ function C() {}\n" +
        "/** @type {I} */ var x;\n" +
        "var y = /** @type {C} */(x);");
  }

  @Test
  public void testCast29a() {
    // C doesn't implement the record type but a subtype might.
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "var x = new C();\n" +
        "var y = /** @type {{remoteJids: Array, sessionId: string}} */(x);");
  }

  @Test
  public void testCast29b() {
    // C doesn't implement the record type but a subtype might.
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {C} */ var x;\n" +
        "var y = /** @type {{prop1: Array, prop2: string}} */(x);");
  }

  @Test
  public void testCast29c() {
    // C doesn't implement the record type but a subtype might.
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {{remoteJids: Array, sessionId: string}} */ var x ;\n" +
        "var y = /** @type {C} */(x);");
  }

  @Test
  public void testCast30() {
    // Should be able to cast to a looser return type
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {function():string} */ var x ;\n" +
        "var y = /** @type {function():?} */(x);");
  }

  @Test
  public void testCast31() {
    // Should be able to cast to a tighter parameter type
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {function(*)} */ var x ;\n" +
        "var y = /** @type {function(string)} */(x);");
  }

  @Test
  public void testCast32() {
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {Object} */ var x ;\n" +
        "var y = /** @type {null|{length:number}} */(x);");
  }

  @Test
  public void testCast33a() {
    // null and void should be assignable to any type that accepts one or the
    // other or both.
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {null|undefined} */ var x ;\n" +
        "var y = /** @type {string?|undefined} */(x);");
  }

  @Test
  public void testCast33b() {
    // null and void should be assignable to any type that accepts one or the
    // other or both.
    testTypes(
        "/** @constructor */ function C() {}\n"
            + "/** @type {null|undefined} */ var x ;\n"
            + "var y = /** @type {string|undefined} */(x);");
  }

  @Test
  public void testCast33c() {
    // null and void should be assignable to any type that accepts one or the
    // other or both.
    testTypes(
        "/** @constructor */ function C() {}\n"
            + "/** @type {null|undefined} */ var x ;\n"
            + "var y = /** @type {string?} */(x);");
  }

  @Test
  public void testCast33d() {
    // null and void should be assignable to any type that accepts one or the
    // other or both.
    testTypes(
        "/** @constructor */ function C() {}\n"
            + "/** @type {null|undefined} */ var x ;\n"
            + "var y = /** @type {null} */(x);");
  }

  @Test
  public void testCast34a() {
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {Object} */ var x ;\n" +
        "var y = /** @type {Function} */(x);");
  }

  @Test
  public void testCast34b() {
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {Function} */ var x ;\n" +
        "var y = /** @type {Object} */(x);");
  }

  @Test
  public void testNestedCasts() {
    testTypes("/** @constructor */var T = function() {};\n" +
        "/** @constructor */var V = function() {};\n" +
        "/**\n" +
        "* @param {boolean} b\n" +
        "* @return {T|V}\n" +
        "*/\n" +
        "function f(b) { return b ? new T() : new V(); }\n" +
        "/**\n" +
        "* @param {boolean} b\n" +
        "* @return {boolean|undefined}\n" +
        "*/\n" +
        "function g(b) { return b ? true : undefined; }\n" +
        "/** @return {T} */\n" +
        "function h() {\n" +
        "return /** @type {T} */ (f(/** @type {boolean} */ (g(true))));\n" +
        "}");
  }

  @Test
  public void testNativeCast1() {
    testTypes(
        "/** @param {number} x */ function f(x) {}" +
        "f(String(true));",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testNativeCast2() {
    testTypes(
        "/** @param {string} x */ function f(x) {}" +
        "f(Number(true));",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testNativeCast3() {
    testTypes(
        "/** @param {number} x */ function f(x) {}" +
        "f(Boolean(''));",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  @Test
  public void testNativeCast4() {
    testTypes(
        lines(
            "/** @param {number} x */ function f(x) {}",
            "f(Array(1));"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : Array",
            "required: number"));
  }

  @Test
  public void testBadConstructorCall() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo();",
        "Constructor function(new:Foo): undefined should be called " +
        "with the \"new\" keyword");
  }

  @Test
  public void testTypeof() {
    testTypes("/**@return {void}*/function foo(){ var a = typeof foo(); }");
  }

  @Test
  public void testTypeof2() {
    testTypes("function f(){ if (typeof 123 == 'numbr') return 321; }",
              "unknown type: numbr");
  }

  @Test
  public void testTypeof3() {
    testTypes(
        lines(
            "function f() {",
            "  return (",
            "      typeof 123 == 'number' ||",
            "      typeof 123 == 'string' ||",
            "      typeof 123 == 'boolean' ||",
            "      typeof 123 == 'undefined' ||",
            "      typeof 123 == 'function' ||",
            "      typeof 123 == 'object' ||",
            "      typeof 123 == 'symbol' ||",
            "      typeof 123 == 'unknown'); }"));
  }

  @Test
  public void testConstDecl1() {
    testTypes(
        "/** @param {?number} x \n @return {boolean} */" +
        "function f(x) { " +
        "  if (x) { /** @const */ var y = x; return y } return true; "  +
        "}",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: boolean");
  }

  @Test
  public void testConstDecl2() {
    compiler.getOptions().setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    testTypes(
        "/** @param {?number} x */" +
        "function f(x) { " +
        "  if (x) {" +
        "    /** @const */ var y = x; " +
        "    /** @return {boolean} */ function g() { return y; } " +
        "  }" +
        "}",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: boolean");
  }

  @Test
  public void testConstructorType1() {
    testTypes("/**@constructor*/function Foo(){}" +
        "/**@type{!Foo}*/var f = new Date();",
        "initializing variable\n" +
        "found   : Date\n" +
        "required: Foo");
  }

  @Test
  public void testConstructorType2() {
    testTypes("/**@constructor*/function Foo(){\n" +
        "/**@type{Number}*/this.bar = new Number(5);\n" +
        "}\n" +
        "/**@type{Foo}*/var f = new Foo();\n" +
        "/**@type{Number}*/var n = f.bar;");
  }

  @Test
  public void testConstructorType3() {
    // Reverse the declaration order so that we know that Foo is getting set
    // even on an out-of-order declaration sequence.
    testTypes("/**@type{Foo}*/var f = new Foo();\n" +
        "/**@type{Number}*/var n = f.bar;" +
        "/**@constructor*/function Foo(){\n" +
        "/**@type{Number}*/this.bar = new Number(5);\n" +
        "}\n");
  }

  @Test
  public void testConstructorType4() {
    testTypes("/**@constructor*/function Foo(){\n" +
        "/**@type{!Number}*/this.bar = new Number(5);\n" +
        "}\n" +
        "/**@type{!Foo}*/var f = new Foo();\n" +
        "/**@type{!String}*/var n = f.bar;",
        "initializing variable\n" +
        "found   : Number\n" +
        "required: String");
  }

  @Test
  public void testConstructorType5() {
    testTypes("/**@constructor*/function Foo(){}\n" +
        "if (Foo){}\n");
  }

  @Test
  public void testConstructorType6() {
    testTypes("/** @constructor */\n" +
        "function bar() {}\n" +
        "function _foo() {\n" +
        " /** @param {bar} x */\n" +
        "  function f(x) {}\n" +
        "}");
  }

  @Test
  public void testConstructorType7() {
    TypeCheckResult p =
        parseAndTypeCheckWithScope("/** @constructor */function A(){};");

    JSType type = p.scope.getVar("A").getType();
    assertThat(type).isInstanceOf(FunctionType.class);
    FunctionType fType = (FunctionType) type;
    assertThat(fType.getReferenceName()).isEqualTo("A");
  }

  @Test
  public void testConstructorType8() {
    testTypes(
        "var ns = {};" +
        "ns.create = function() { return function() {}; };" +
        "/** @constructor */ ns.Foo = ns.create();" +
        "ns.Foo.prototype = {x: 0, y: 0};" +
        "/**\n" +
        " * @param {ns.Foo} foo\n" +
        " * @return {string}\n" +
        " */\n" +
        "function f(foo) {" +
        "  return foo.x;" +
        "}",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testConstructorType9() {
    testTypes(
        "var ns = {};" +
        "ns.create = function() { return function() {}; };" +
        "ns.extend = function(x) { return x; };" +
        "/** @constructor */ ns.Foo = ns.create();" +
        "ns.Foo.prototype = ns.extend({x: 0, y: 0});" +
        "/**\n" +
        " * @param {ns.Foo} foo\n" +
        " * @return {string}\n" +
        " */\n" +
        "function f(foo) {" +
        "  return foo.x;" +
        "}");
  }

  @Test
  public void testConstructorType10() {
    testTypes("/** @constructor */" +
              "function NonStr() {}" +
              "/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " * @extends{NonStr}\n" +
              " */" +
              "function NonStrKid() {}");
  }

  @Test
  public void testConstructorType11() {
    testTypes("/** @constructor */" +
              "function NonDict() {}" +
              "/**\n" +
              " * @constructor\n" +
              " * @dict\n" +
              " * @extends{NonDict}\n" +
              " */" +
              "function NonDictKid() {}");
  }

  @Test
  public void testConstructorType12() {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Bar() {}\n" +
              "Bar.prototype = {};\n");
  }

  @Test
  public void testBadStruct() {
    testTypes("/** @struct */function Struct1() {}",
              "@struct used without @constructor for Struct1");
  }

  @Test
  public void testBadDict() {
    testTypes("/** @dict */function Dict1() {}",
              "@dict used without @constructor for Dict1");
  }

  @Test
  public void testAnonymousPrototype1() {
    testTypes(
        "var ns = {};" +
        "/** @constructor */ ns.Foo = function() {" +
        "  this.bar(3, 5);" +
        "};" +
        "ns.Foo.prototype = {" +
        "  bar: function(x) {}" +
        "};",
        "Function ns.Foo.prototype.bar: called with 2 argument(s). " +
        "Function requires at least 1 argument(s) and no more " +
        "than 1 argument(s).");
  }

  @Test
  public void testAnonymousPrototype2() {
    testTypes(
        "/** @interface */ var Foo = function() {};" +
        "Foo.prototype = {" +
        "  foo: function(x) {}" +
        "};" +
        "/**\n" +
        " * @constructor\n" +
        " * @implements {Foo}\n" +
        " */ var Bar = function() {};",
        "property foo on interface Foo is not implemented by type Bar");
  }

  @Test
  public void testAnonymousType1() {
    testTypes(
        lines(
             "function f() { return {}; }",
             "/** @constructor */",
             "f().bar = function() {};"));
  }

  @Test
  public void testAnonymousType2() {
    testTypes("function f() { return {}; }" +
        "/** @interface */\n" +
        "f().bar = function() {};");
  }

  @Test
  public void testAnonymousType3() {
    testTypes("function f() { return {}; }" +
        "/** @enum */\n" +
        "f().bar = {FOO: 1};");
  }

  @Test
  public void testBang1() {
    testTypes("/** @param {Object} x\n@return {!Object} */\n" +
        "function f(x) { return x; }",
        "inconsistent return type\n" +
        "found   : (Object|null)\n" +
        "required: Object");
  }

  @Test
  public void testBang2() {
    testTypes("/** @param {Object} x\n@return {!Object} */\n" +
        "function f(x) { return x ? x : new Object(); }");
  }

  @Test
  public void testBang3() {
    testTypes("/** @param {Object} x\n@return {!Object} */\n" +
        "function f(x) { return /** @type {!Object} */ (x); }");
  }

  @Test
  public void testBang4() {
    testTypes("/**@param {Object} x\n@param {Object} y\n@return {boolean}*/\n" +
        "function f(x, y) {\n" +
        "if (typeof x != 'undefined') { return x == y; }\n" +
        "else { return x != y; }\n}");
  }

  @Test
  public void testBang5() {
    testTypes("/**@param {Object} x\n@param {Object} y\n@return {boolean}*/\n" +
        "function f(x, y) { return !!x && x == y; }");
  }

  @Test
  public void testBang6() {
    testTypes("/** @param {Object?} x\n@return {Object} */\n" +
        "function f(x) { return x; }");
  }

  @Test
  public void testBang7() {
    testTypes("/**@param {(Object|string|null)} x\n" +
        "@return {(Object|string)}*/function f(x) { return x; }");
  }

  @Test
  public void testDefinePropertyOnNullableObject1() {
    // checking loose property behavior
    disableStrictMissingPropertyChecks();
    testTypes(
        lines(
            "/** @type {Object} */ var n = {};",
            "/** @type {number} */ n.x = 1;",
            "/** @return {boolean} */ function f() { return n.x; }"),
        lines(
            "inconsistent return type",
            "found   : number",
            "required: boolean"));
  }

  @Test
  public void testDefinePropertyOnNullableObject1a() {
    testTypes(
         lines(
            "/** @const */ var n = {};",
            "/** @type {number} */ n.x = 1;",
            "/** @return {boolean} */function f() { return n.x; }"),
         lines(
            "inconsistent return type",
            "found   : number",
            "required: boolean"));
  }

  @Test
  public void testDefinePropertyOnObject() {
    // checking loose property behavior
    disableStrictMissingPropertyChecks();
    testTypes(
        lines(
            "/** @type {!Object} */ var n = {};",
            "/** @type {number} */ n.x = 1;",
            "/** @return {boolean} */function f() { return n.x; }"),
        lines(
            "inconsistent return type",
            "found   : number",
            "required: boolean"));
  }

  @Test
  public void testDefinePropertyOnNullableObject2() {
    // checking loose property behavior
    disableStrictMissingPropertyChecks();
    testTypes("/** @constructor */ var T = function() {};\n" +
        "/** @param {T} t\n@return {boolean} */function f(t) {\n" +
        "t.x = 1; return t.x; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: boolean");
  }

  @Test
  public void testDefinePropertyOnNullableObject2b() {
    testTypes(
        lines(
            "/** @constructor */ var T = function() {};",
            "/** @param {T} t */function f(t) { t.x = 1; }"),
        "Property x never defined on T");
  }

  @Test
  public void testUnknownConstructorInstanceType1() {
    testTypes("/** @return {Array} */ function g(f) { return new f(); }");
  }

  @Test
  public void testUnknownConstructorInstanceType2() {
    testTypes("function g(f) { return /** @type {Array} */(new f()); }");
  }

  @Test
  public void testUnknownConstructorInstanceType3() {
    testTypes("function g(f) { var x = new f(); x.a = 1; return x; }");
  }

  @Test
  public void testUnknownPrototypeChain1() {
    testTypes(
        lines("/**",
              "* @param {Object} co",
              " * @return {Object}",
              " */",
              "function inst(co) {",
              " /** @constructor */",
              " var c = function() {};",
              " c.prototype = co.prototype;",
              " return new c;",
              "}"),
        "Property prototype never defined on Object");
  }

  @Test
  public void testUnknownPrototypeChain2() {
    testTypes(
        lines("/**",
              " * @param {Function} co",
              " * @return {Object}",
              " */",
              "function inst(co) {",
              " /** @constructor */",
              " var c = function() {};",
              " c.prototype = co.prototype;",
              " return new c;",
              "}"));
  }

  @Test
  public void testNamespacedConstructor() {
    Node root = parseAndTypeCheck(
        "var goog = {};" +
        "/** @constructor */ goog.MyClass = function() {};" +
        "/** @return {!goog.MyClass} */ " +
        "function foo() { return new goog.MyClass(); }");

    JSType typeOfFoo = root.getLastChild().getJSType();
    assertType(typeOfFoo).isInstanceOf(FunctionType.class);

    JSType retType = ((FunctionType) typeOfFoo).getReturnType();
    assertType(retType).isInstanceOf(ObjectType.class);
    assertThat(((ObjectType) retType).getReferenceName()).isEqualTo("goog.MyClass");
  }

  @Test
  public void testComplexNamespace() {
    String js =
      "var goog = {};" +
      "goog.foo = {};" +
      "goog.foo.bar = 5;";

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
    assertThat(googScopeType).isSameAs(googNodeType);

    // goog type on the left of the GETPROP node (under fist ASSIGN)
    Node getpropFoo1 = varNode.getNext().getFirstFirstChild();
    assertNode(getpropFoo1).hasToken(Token.GETPROP);
    assertThat(getpropFoo1.getFirstChild().getString()).isEqualTo("goog");
    JSType googGetpropFoo1Type = getpropFoo1.getFirstChild().getJSType();
    assertThat(googGetpropFoo1Type).isInstanceOf(ObjectType.class);

    // still the same type as the one on the variable
    assertThat(googGetpropFoo1Type).isSameAs(googScopeType);

    // the foo property should be defined on goog
    JSType googFooType = ((ObjectType) googScopeType).getPropertyType("foo");
    assertThat(googFooType).isInstanceOf(ObjectType.class);

    // goog type on the left of the GETPROP lower level node
    // (under second ASSIGN)
    Node getpropFoo2 = varNode.getNext().getNext()
        .getFirstFirstChild().getFirstChild();
    assertNode(getpropFoo2).hasToken(Token.GETPROP);
    assertThat(getpropFoo2.getFirstChild().getString()).isEqualTo("goog");
    JSType googGetpropFoo2Type = getpropFoo2.getFirstChild().getJSType();
    assertThat(googGetpropFoo2Type).isInstanceOf(ObjectType.class);

    // still the same type as the one on the variable
    assertThat(googGetpropFoo2Type).isSameAs(googScopeType);

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
    Node js1Node = parseAndTypeCheck(
        DEFAULT_EXTERNS,
        "/** @constructor */function A() {}" +
        "A.prototype.m1 = 5");

    ObjectType instanceType = getInstanceType(js1Node);
    assertHasXMorePropertiesThanNativeObject(instanceType, 1);
    checkObjectType(instanceType, "m1", getNativeNumberType());
  }

  @Test
  public void testAddingMethodsUsingPrototypeIdiomComplexNamespace1() {
    TypeCheckResult p = parseAndTypeCheckWithScope(
        DEFAULT_EXTERNS,
        "var goog = {};" +
        "goog.A = /** @constructor */function() {};" +
        "/** @type {number} */goog.A.prototype.m1 = 5");

    testAddingMethodsUsingPrototypeIdiomComplexNamespace(p);
  }

  @Test
  public void testAddingMethodsUsingPrototypeIdiomComplexNamespace2() {
    TypeCheckResult p = parseAndTypeCheckWithScope(
        DEFAULT_EXTERNS,
        "var goog = {};" +
        "/** @constructor */goog.A = function() {};" +
        "/** @type {number} */goog.A.prototype.m1 = 5");

    testAddingMethodsUsingPrototypeIdiomComplexNamespace(p);
  }

  private void testAddingMethodsUsingPrototypeIdiomComplexNamespace(
      TypeCheckResult p) {
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
    Node js1Node = parseAndTypeCheck(
        DEFAULT_EXTERNS,
        "/** @constructor */function A() {}" +
        "A.prototype = {m1: 5, m2: true}");

    ObjectType instanceType = getInstanceType(js1Node);
    assertHasXMorePropertiesThanNativeObject(instanceType, 2);
    checkObjectType(instanceType, "m1", getNativeNumberType());
    checkObjectType(instanceType, "m2", getNativeBooleanType());
  }

  @Test
  public void testDontAddMethodsIfNoConstructor() {
    Node js1Node = parseAndTypeCheck(
        "function A() {}" +
        "A.prototype = {m1: 5, m2: true}");

    JSType functionAType = js1Node.getFirstChild().getJSType();
    assertThat(functionAType.toString()).isEqualTo("function(): undefined");
    assertTypeEquals(getNativeUnknownType(), getNativeU2UFunctionType().getPropertyType("m1"));
    assertTypeEquals(getNativeUnknownType(), getNativeU2UFunctionType().getPropertyType("m2"));
  }

  @Test
  public void testFunctionAssignement() {
    testTypes("/**" +
        "* @param {string} ph0" +
        "* @param {string} ph1" +
        "* @return {string}" +
        "*/" +
        "function MSG_CALENDAR_ACCESS_ERROR(ph0, ph1) {return ''}" +
        "/** @type {Function} */" +
        "var MSG_CALENDAR_ADD_ERROR = MSG_CALENDAR_ACCESS_ERROR;");
  }

  @Test
  public void testAddMethodsPrototypeTwoWays() {
    Node js1Node = parseAndTypeCheck(
        DEFAULT_EXTERNS,
        "/** @constructor */function A() {}" +
        "A.prototype = {m1: 5, m2: true};" +
        "A.prototype.m3 = 'third property!';");

    ObjectType instanceType = getInstanceType(js1Node);
    assertThat(instanceType.toString()).isEqualTo("A");
    assertHasXMorePropertiesThanNativeObject(instanceType, 3);
    checkObjectType(instanceType, "m1", getNativeNumberType());
    checkObjectType(instanceType, "m2", getNativeBooleanType());
    checkObjectType(instanceType, "m3", getNativeStringType());
  }

  @Test
  public void testPrototypePropertyTypes() {
    Node js1Node = parseAndTypeCheck(
        DEFAULT_EXTERNS,
        "/** @constructor */function A() {\n" +
        "  /** @type {string} */ this.m1;\n" +
        "  /** @type {Object?} */ this.m2 = {};\n" +
        "  /** @type {boolean} */ this.m3;\n" +
        "}\n" +
        "/** @type {string} */ A.prototype.m4;\n" +
        "/** @type {number} */ A.prototype.m5 = 0;\n" +
        "/** @type {boolean} */ A.prototype.m6;\n");

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
        "/** @constructor */ var String = function(opt_str) {};\n" +
        "/**\n" +
        "* @param {number} start\n" +
        "* @param {number} opt_length\n"  +
        "* @return {string}\n" +
        "*/\n" +
        "String.prototype.substr = function(start, opt_length) {};\n";
    Node n1 = parseAndTypeCheck(externs + "(new String(\"x\")).substr(0,1);");
    assertTypeEquals(getNativeStringType(), n1.getLastChild().getFirstChild().getJSType());
  }

  @Test
  public void testExtendBuiltInType2() {
    String externs =
        "/** @constructor */ var String = function(opt_str) {};\n" +
        "/**\n" +
        "* @param {number} start\n" +
        "* @param {number} opt_length\n"  +
        "* @return {string}\n" +
        "*/\n" +
        "String.prototype.substr = function(start, opt_length) {};\n";
    Node n2 = parseAndTypeCheck(externs + "\"x\".substr(0,1);");
    assertTypeEquals(getNativeStringType(), n2.getLastChild().getFirstChild().getJSType());
  }

  @Test
  public void testExtendFunction1() {
    Node n = parseAndTypeCheck("/**@return {number}*/Function.prototype.f = " +
        "function() { return 1; };\n" +
        "(new Function()).f();");
    JSType type = n.getLastChild().getLastChild().getJSType();
    assertTypeEquals(getNativeNumberType(), type);
  }

  @Test
  public void testExtendFunction2() {
    Node n = parseAndTypeCheck("/**@return {number}*/Function.prototype.f = " +
        "function() { return 1; };\n" +
        "(function() {}).f();");
    JSType type = n.getLastChild().getLastChild().getJSType();
    assertTypeEquals(getNativeNumberType(), type);
  }

  @Test
  public void testInheritanceCheck1() {
    testTypes(
        "/** @constructor */function Super() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "Sub.prototype.foo = function() {};");
  }

  @Test
  public void testInheritanceCheck2() {
    testTypes(
        "/** @constructor */function Super() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo = function() {};",
        "property foo not defined on any superclass of Sub");
  }

  @Test
  public void testInheritanceCheck3() {
    testTypes(
        "/** @constructor */function Super() {};" +
        "Super.prototype.foo = function() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "Sub.prototype.foo = function() {};",
        "property foo already defined on superclass Super; " +
        "use @override to override it");
  }

  @Test
  public void testInheritanceCheck4() {
    testTypes(
        "/** @constructor */function Super() {};" +
        "Super.prototype.foo = function() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo = function() {};");
  }

  @Test
  public void testInheritanceCheck5() {
    testTypes(
        "/** @constructor */function Root() {};" +
        "Root.prototype.foo = function() {};" +
        "/** @constructor\n @extends {Root} */function Super() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "Sub.prototype.foo = function() {};",
        "property foo already defined on superclass Root; " +
        "use @override to override it");
  }

  @Test
  public void testInheritanceCheck6() {
    testTypes(
        "/** @constructor */function Root() {};" +
        "Root.prototype.foo = function() {};" +
        "/** @constructor\n @extends {Root} */function Super() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo = function() {};");
  }

  @Test
  public void testInheritanceCheck7() {
    testTypes(
        "var goog = {};" +
        "/** @constructor */goog.Super = function() {};" +
        "goog.Super.prototype.foo = 3;" +
        "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};" +
        "goog.Sub.prototype.foo = 5;");
  }

  @Test
  public void testInheritanceCheck8() {
    testTypes(
        "var goog = {};" +
        "/** @constructor */goog.Super = function() {};" +
        "goog.Super.prototype.foo = 3;" +
        "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};" +
        "/** @override */goog.Sub.prototype.foo = 5;");
  }

  @Test
  public void testInheritanceCheck9_1() {
    testTypes(
        "/** @constructor */function Super() {};" +
        "Super.prototype.foo = function() { return 3; };" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override\n @return {number} */Sub.prototype.foo =\n" +
        "function() { return 1; };");
  }

  @Test
  public void testInheritanceCheck9_2() {
    testTypes(
        "/** @constructor */function Super() {};" +
        "/** @return {number} */" +
        "Super.prototype.foo = function() { return 1; };" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo =\n" +
        "function() {};");
  }

  @Test
  public void testInheritanceCheck9_3() {
    testTypes(
        "/** @constructor */function Super() {};" +
        "/** @return {number} */" +
        "Super.prototype.foo = function() { return 1; };" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override\n @return {string} */Sub.prototype.foo =\n" +
        "function() { return \"some string\" };",
        "mismatch of the foo property type and the type of the property it " +
        "overrides from superclass Super\n" +
        "original: function(this:Super): number\n" +
        "override: function(this:Sub): string");
  }

  @Test
  public void testInheritanceCheck10_1() {
    testTypes(
        "/** @constructor */function Root() {};" +
        "Root.prototype.foo = function() { return 3; };" +
        "/** @constructor\n @extends {Root} */function Super() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override\n @return {number} */Sub.prototype.foo =\n" +
        "function() { return 1; };");
  }

  @Test
  public void testInheritanceCheck10_2() {
    testTypes(
        "/** @constructor */function Root() {};" +
        "/** @return {number} */" +
        "Root.prototype.foo = function() { return 1; };" +
        "/** @constructor\n @extends {Root} */function Super() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo =\n" +
        "function() {};");
  }

  @Test
  public void testInheritanceCheck10_3() {
    testTypes(
        "/** @constructor */function Root() {};" +
        "/** @return {number} */" +
        "Root.prototype.foo = function() { return 1; };" +
        "/** @constructor\n @extends {Root} */function Super() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override\n @return {string} */Sub.prototype.foo =\n" +
        "function() { return \"some string\" };",
        "mismatch of the foo property type and the type of the property it " +
        "overrides from superclass Root\n" +
        "original: function(this:Root): number\n" +
        "override: function(this:Sub): string");
  }

  @Test
  public void testInterfaceInheritanceCheck11() {
    testTypes(
        "/** @constructor */function Super() {};" +
        "/** @param {number} bar */Super.prototype.foo = function(bar) {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override\n  @param {string} bar */Sub.prototype.foo =\n" +
        "function(bar) {};",
        "mismatch of the foo property type and the type of the property it " +
        "overrides from superclass Super\n" +
        "original: function(this:Super, number): undefined\n" +
        "override: function(this:Sub, string): undefined");
  }

  @Test
  public void testInheritanceCheck12() {
    testTypes(
        "var goog = {};" +
        "/** @constructor */goog.Super = function() {};" +
        "goog.Super.prototype.foo = 3;" +
        "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};" +
        "/** @override */goog.Sub.prototype.foo = \"some string\";");
  }

  @Test
  public void testInheritanceCheck13() {
    testTypes(
        "var goog = {};\n" +
        "/** @constructor\n @extends {goog.Missing} */function Sub() {};" +
        "/** @override */Sub.prototype.foo = function() {};",
        "Bad type annotation. Unknown type goog.Missing");
  }

  @Test
  public void testInheritanceCheck14() {
    testClosureTypes(
        "var goog = {};\n" +
        "/** @constructor\n @extends {goog.Missing} */\n" +
        "goog.Super = function() {};\n" +
        "/** @constructor\n @extends {goog.Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo = function() {};",
        "Bad type annotation. Unknown type goog.Missing");
  }

  @Test
  public void testInheritanceCheck15() {
    testTypes(
        "/** @constructor */function Super() {};" +
        "/** @param {number} bar */Super.prototype.foo;" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override\n  @param {number} bar */Sub.prototype.foo =\n" +
        "function(bar) {};");
  }

  @Test
  public void testInheritanceCheck16() {
    testTypes(
        "var goog = {};" +
        "/** @constructor */goog.Super = function() {};" +
        "/** @type {number} */ goog.Super.prototype.foo = 3;" +
        "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};" +
        "/** @type {number} */ goog.Sub.prototype.foo = 5;",
        "property foo already defined on superclass goog.Super; " +
        "use @override to override it");
  }

  @Test
  public void testInheritanceCheck17() {
    // Make sure this warning still works, even when there's no
    // @override tag.
    testTypes(
        "var goog = {};"
            + "/** @constructor */goog.Super = function() {};"
            + "/** @param {number} x */ goog.Super.prototype.foo = function(x) {};"
            + "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};"
            + "/** @override @param {string} x */ goog.Sub.prototype.foo = function(x) {};",
        "mismatch of the foo property type and the type of the property it "
            + "overrides from superclass goog.Super\n"
            + "original: function(this:goog.Super, number): undefined\n"
            + "override: function(this:goog.Sub, string): undefined");
  }

  @Test
  public void testInterfacePropertyOverride1() {
    testTypes(
        "/** @interface */function Super() {};" +
        "Super.prototype.foo = function() {};" +
        "/** @interface\n @extends {Super} */function Sub() {};" +
        "Sub.prototype.foo = function() {};");
  }

  @Test
  public void testInterfacePropertyOverride2() {
    testTypes(
        "/** @interface */function Root() {};" +
        "Root.prototype.foo = function() {};" +
        "/** @interface\n @extends {Root} */function Super() {};" +
        "/** @interface\n @extends {Super} */function Sub() {};" +
        "Sub.prototype.foo = function() {};");
  }

  @Test
  public void testInterfaceInheritanceCheck1() {
    testTypes(
        "/** @interface */function Super() {};" +
        "Super.prototype.foo = function() {};" +
        "/** @constructor\n @implements {Super} */function Sub() {};" +
        "Sub.prototype.foo = function() {};",
        "property foo already defined on interface Super; use @override to " +
        "override it");
  }

  @Test
  public void testInterfaceInheritanceCheck2() {
    testTypes(
        "/** @interface */function Super() {};" +
        "Super.prototype.foo = function() {};" +
        "/** @constructor\n @implements {Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo = function() {};");
  }

  @Test
  public void testInterfaceInheritanceCheck3() {
    testTypes(
        "/** @interface */function Root() {};" +
        "/** @return {number} */Root.prototype.foo = function() {};" +
        "/** @interface\n @extends {Root} */function Super() {};" +
        "/** @constructor\n @implements {Super} */function Sub() {};" +
        "/** @return {number} */Sub.prototype.foo = function() { return 1;};",
        "property foo already defined on interface Root; use @override to " +
        "override it");
  }

  @Test
  public void testInterfaceInheritanceCheck4() {
    testTypes(
        "/** @interface */function Root() {};" +
        "/** @return {number} */Root.prototype.foo = function() {};" +
        "/** @interface\n @extends {Root} */function Super() {};" +
        "/** @constructor\n @implements {Super} */function Sub() {};" +
        "/** @override\n * @return {number} */Sub.prototype.foo =\n" +
        "function() { return 1;};");
  }

  @Test
  public void testInterfaceInheritanceCheck5() {
    testTypes(
        "/** @interface */function Super() {};"
            + "/** @return {string} */Super.prototype.foo = function() {};"
            + "/** @constructor\n @implements {Super} */function Sub() {};"
            + "/** @override\n @return {number} */Sub.prototype.foo = function() { return 1; };",
        "mismatch of the foo property on type Sub and the type of the property it "
            + "overrides from interface Super\n"
            + "original: function(this:Super): string\n"
            + "override: function(this:Sub): number");
  }

  @Test
  public void testInterfaceInheritanceCheck6() {
    testTypes(
        "/** @interface */function Root() {};"
            + "/** @return {string} */Root.prototype.foo = function() {};"
            + "/** @interface\n @extends {Root} */function Super() {};"
            + "/** @constructor\n @implements {Super} */function Sub() {};"
            + "/** @override\n @return {number} */Sub.prototype.foo = function() { return 1; };",
        "mismatch of the foo property on type Sub and the type of the property it "
            + "overrides from interface Root\n"
            + "original: function(this:Root): string\n"
            + "override: function(this:Sub): number");
  }

  @Test
  public void testInterfaceInheritanceCheck7() {
    testTypes(
        "/** @interface */function Super() {};"
            + "/** @param {number} bar */Super.prototype.foo = function(bar) {};"
            + "/** @constructor\n @implements {Super} */function Sub() {};"
            + "/** @override\n  @param {string} bar */Sub.prototype.foo =\n"
            + "function(bar) {};",
        "mismatch of the foo property on type Sub and the type of the property it "
            + "overrides from interface Super\n"
            + "original: function(this:Super, number): undefined\n"
            + "override: function(this:Sub, string): undefined");
  }

  @Test
  public void testInterfaceInheritanceCheck8() {
    testTypes(
        "/** @constructor\n @implements {Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo = function() {};",
        new String[] {
          "Bad type annotation. Unknown type Super",
          "property foo not defined on any superclass of Sub"
        });
  }

  @Test
  public void testInterfaceInheritanceCheck9() {
    testTypes(
        "/** @interface */ function I() {}" +
        "/** @return {number} */ I.prototype.bar = function() {};" +
        "/** @constructor */ function F() {}" +
        "/** @return {number} */ F.prototype.bar = function() {return 3; };" +
        "/** @return {number} */ F.prototype.foo = function() {return 3; };" +
        "/** @constructor \n * @extends {F} \n * @implements {I} */ " +
        "function G() {}" +
        "/** @return {string} */ function f() { return new G().bar(); }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testInterfaceInheritanceCheck10() {
    testTypes(
        "/** @interface */ function I() {}" +
        "/** @return {number} */ I.prototype.bar = function() {};" +
        "/** @constructor */ function F() {}" +
        "/** @return {number} */ F.prototype.foo = function() {return 3; };" +
        "/** @constructor \n * @extends {F} \n * @implements {I} */ " +
        "function G() {}" +
        "/** @return {number} \n * @override */ " +
        "G.prototype.bar = G.prototype.foo;" +
        "/** @return {string} */ function f() { return new G().bar(); }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testInterfaceInheritanceCheck12() {
    testTypes(
        "/** @interface */ function I() {};\n"
            + "/** @type {string} */ I.prototype.foobar;\n"
            + "/** \n * @constructor \n * @implements {I} */\n"
            + "function C() {\n"
            + "/** \n * @type {number} */ this.foobar = 2;};\n"
            + "/** @type {I} */ \n var test = new C(); alert(test.foobar);",
        "mismatch of the foobar property on type C and the type of the property"
            + " it overrides from interface I\n"
            + "original: string\n"
            + "override: number");
  }

  @Test
  public void testInterfaceInheritanceCheck13() {
    testTypes(
        "function abstractMethod() {};\n" +
        "/** @interface */var base = function() {};\n" +
        "/** @extends {base} \n @interface */ var Int = function() {}\n" +
        "/** @type {{bar : !Function}} */ var x; \n" +
        "/** @type {!Function} */ base.prototype.bar = abstractMethod; \n" +
        "/** @type {Int} */ var foo;\n" +
        "foo.bar();");
  }

  /** Verify that templatized interfaces can extend one another and share template values. */
  @Test
  public void testInterfaceInheritanceCheck14() {
    testTypes(
        "/** @interface\n @template T */function A() {};" +
        "/** @return {T} */A.prototype.foo = function() {};" +
        "/** @interface\n @template U\n @extends {A<U>} */function B() {};" +
        "/** @return {U} */B.prototype.bar = function() {};" +
        "/** @constructor\n @implements {B<string>} */function C() {};" +
        "/** @return {string}\n @override */C.prototype.foo = function() {};" +
        "/** @return {string}\n @override */C.prototype.bar = function() {};");
  }

  /** Verify that templatized instances can correctly implement templatized interfaces. */
  @Test
  public void testInterfaceInheritanceCheck15() {
    testTypes(
        "/** @interface\n @template T */function A() {};" +
        "/** @return {T} */A.prototype.foo = function() {};" +
        "/** @interface\n @template U\n @extends {A<U>} */function B() {};" +
        "/** @return {U} */B.prototype.bar = function() {};" +
        "/** @constructor\n @template V\n @implements {B<V>}\n */function C() {};" +
        "/** @return {V}\n @override */C.prototype.foo = function() {};" +
        "/** @return {V}\n @override */C.prototype.bar = function() {};");
  }

  /**
   * Verify that using @override to declare the signature for an implementing class works correctly
   * when the interface is generic.
   */
  @Test
  public void testInterfaceInheritanceCheck16() {
    testTypes(
        "/** @interface\n @template T */function A() {};" +
        "/** @return {T} */A.prototype.foo = function() {};" +
        "/** @return {T} */A.prototype.bar = function() {};" +
        "/** @constructor\n @implements {A<string>} */function B() {};" +
        "/** @override */B.prototype.foo = function() { return 'string'};" +
        "/** @override */B.prototype.bar = function() { return 3 };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testInterfacePropertyNotImplemented() {
    testTypes(
        "/** @interface */function Int() {};" +
        "Int.prototype.foo = function() {};" +
        "/** @constructor\n @implements {Int} */function Foo() {};",
        "property foo on interface Int is not implemented by type Foo");
  }

  @Test
  public void testInterfacePropertyNotImplemented2() {
    testTypes(
        "/** @interface */function Int() {};" +
        "Int.prototype.foo = function() {};" +
        "/** @interface \n @extends {Int} */function Int2() {};" +
        "/** @constructor\n @implements {Int2} */function Foo() {};",
        "property foo on interface Int is not implemented by type Foo");
  }

  /** Verify that templatized interfaces enforce their template type values. */
  @Test
  public void testInterfacePropertyNotImplemented3() {
    testTypes(
        "/** @interface\n @template T */function Int() {};"
            + "/** @return {T} */Int.prototype.foo = function() {};"
            + "/** @constructor\n @implements {Int<string>} */function Foo() {};"
            + "/** @return {number}\n @override */Foo.prototype.foo = function() {};",
        "mismatch of the foo property on type Foo and the type of the property it "
            + "overrides from interface Int\n"
            + "original: function(this:Int): string\n"
            + "override: function(this:Foo): number");
  }

  @Test
  public void testStubConstructorImplementingInterface() {
    // This does not throw a warning for unimplemented property because Foo is
    // just a stub.
    testTypesWithExterns(
        // externs
        "/** @interface */ function Int() {}\n" +
        "Int.prototype.foo = function() {};" +
        "/** @constructor \n @implements {Int} */ var Foo;\n",
        "");
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
    ObjectType objectType =
        (ObjectType) objectNode.getJSType();
    assertTypeEquals(getNativeNumberType(), objectType.getPropertyType("m1"));
    assertTypeEquals(getNativeStringType(), objectType.getPropertyType("m2"));

    // variable's type
    assertTypeEquals(objectType, nameNode.getJSType());
  }

  @Test
  public void testObjectLiteralDeclaration1() {
    testTypes(
        "var x = {" +
        "/** @type {boolean} */ abc: true," +
        "/** @type {number} */ 'def': 0," +
        "/** @type {string} */ 3: 'fgh'" +
        "};");
  }

  @Test
  public void testObjectLiteralDeclaration2() {
    testTypes(
        "var x = {" +
        "  /** @type {boolean} */ abc: true" +
        "};" +
        "x.abc = 0;",
        "assignment to property abc of x\n" +
        "found   : number\n" +
        "required: boolean");
  }

  @Test
  public void testObjectLiteralDeclaration3() {
    testTypes(
        "/** @param {{foo: !Function}} x */ function f(x) {}" +
        "f({foo: function() {}});");
  }

  @Test
  public void testObjectLiteralDeclaration4() {
    testClosureTypes(
        "var x = {" +
        "  /** @param {boolean} x */ abc: function(x) {}" +
        "};" +
        "/**\n" +
        " * @param {string} x\n" +
        " * @suppress {duplicate}\n" +
        " */ x.abc = function(x) {};",
        "assignment to property abc of x\n" +
        "found   : function(string): undefined\n" +
        "required: function(boolean): undefined");
    // TODO(user): suppress {duplicate} currently also silence the
    // redefining type error in the TypeValidator. Maybe it needs
    // a new suppress name instead?
  }

  @Test
  public void testObjectLiteralDeclaration5() {
    testTypes(
        "var x = {" +
        "  /** @param {boolean} x */ abc: function(x) {}" +
        "};" +
        "/**\n" +
        " * @param {boolean} x\n" +
        " * @suppress {duplicate}\n" +
        " */ x.abc = function(x) {};");
  }

  @Test
  public void testObjectLiteralDeclaration6() {
    testTypes(
        "var x = {};" +
        "/**\n" +
        " * @param {boolean} x\n" +
        " * @suppress {duplicate}\n" +
        " */ x.abc = function(x) {};" +
        "x = {" +
        "  /**\n" +
        "   * @param {boolean} x\n" +
        "   * @suppress {duplicate}\n" +
        "   */" +
        "  abc: function(x) {}" +
        "};");
  }

  @Test
  public void testObjectLiteralDeclaration7() {
    testTypes(
        "var x = {};" +
        "/**\n" +
        " * @type {function(boolean): undefined}\n" +
        " */ x.abc = function(x) {};" +
        "x = {" +
        "  /**\n" +
        "   * @param {boolean} x\n" +
        "   * @suppress {duplicate}\n" +
        "   */" +
        "  abc: function(x) {}" +
        "};");
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
    String externs = lines(
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
    testTypes("var a = {};" +
        "/** @constructor */ a.N = function() {};\n" +
        "a.N.prototype.p = 1;\n" +
        "/** @constructor */ a.S = function() {};\n" +
        "a.S.prototype.p = 'a';\n" +
        "/** @param {!a.N|!a.S} x\n@return {string} */\n" +
        "var f = function(x) { return x.p; };",
        "inconsistent return type\n" +
        "found   : (number|string)\n" +
        "required: string");
  }

  @Test
  public void testGetPropertyTypeOfUnionType_withMatchingTemplates() {
    testTypes(
        lines(
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
            "var f = function(x) { return x.p; };"),
        lines(
            "inconsistent return type", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testGetPropertyTypeOfUnionType_withDifferingTemplates() {
    testTypes(
        lines(
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
            "var f = function(x) { return x.p; };"),
        lines(
            "inconsistent return type", //
            "found   : (number|string)",
            "required: string"));
  }

  @Test
  public void testInvalidAssignToPropertyTypeOfUnionType_withMatchingTemplates_doesntWarn() {
    // We don't warn for this assignment because we treat the type of `x.p` as inferred...
    testTypes(
        lines(
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
            "var f = function(x) { x.p = 'not a number'; };"));
  }

  // TODO(user): We should flag these as invalid. This will probably happen
  // when we make sure the interface is never referenced outside of its
  // definition. We might want more specific and helpful error messages.
  @Test
  @Ignore
  public void testWarningOnInterfacePrototype() {
    testTypes(
        "/** @interface */ u.T = function() {};\n"
            + "/** @return {number} */ u.T.prototype = function() { };",
        "e of its definition");
  }

  @Test
  @Ignore
  public void testBadPropertyOnInterface1() {
    testTypes(
        "/** @interface */ u.T = function() {};\n"
            + "/** @return {number} */ u.T.f = function() { return 1;};",
        "cannot reference an interface outside of its definition");
  }

  @Test
  @Ignore
  public void testBadPropertyOnInterface2() {
    testTypes(
        "/** @interface */ function T() {};\n"
            + "/** @return {number} */ T.f = function() { return 1;};",
        "cannot reference an interface outside of its definition");
  }

  @Test
  @Ignore
  public void testBadPropertyOnInterface3() {
    testTypes(
        "/** @interface */ u.T = function() {}; u.T.x",
        "cannot reference an interface outside of its definition");
  }

  @Test
  @Ignore
  public void testBadPropertyOnInterface4() {
    testTypes(
        "/** @interface */ function T() {}; T.x;",
        "cannot reference an interface outside of its definition");
  }

  @Test
  public void testAnnotatedPropertyOnInterface1() {
    // For interfaces we must allow function definitions that don't have a
    // return statement, even though they declare a returned type.
    testTypes("/** @interface */ u.T = function() {};\n" +
        "/** @return {number} */ u.T.prototype.f = function() {};");
  }

  @Test
  public void testAnnotatedPropertyOnInterface2() {
    testTypes("/** @interface */ u.T = function() {};\n" +
        "/** @return {number} */ u.T.prototype.f = function() { };");
  }

  @Test
  public void testAnnotatedPropertyOnInterface3() {
    testTypes("/** @interface */ function T() {};\n" +
        "/** @return {number} */ T.prototype.f = function() { };");
  }

  @Test
  public void testAnnotatedPropertyOnInterface4() {
    testTypes(
        CLOSURE_DEFS +
        "/** @interface */ function T() {};\n" +
        "/** @return {number} */ T.prototype.f = goog.abstractMethod;");
  }

  // TODO(user): If we want to support this syntax we have to warn about
  // missing annotations.
  @Test
  @Ignore
  public void testWarnUnannotatedPropertyOnInterface1() {
    testTypes(
        "/** @interface */ u.T = function() {}; u.T.prototype.x;",
        "interface property x is not annotated");
  }

  @Test
  @Ignore
  public void testWarnUnannotatedPropertyOnInterface2() {
    testTypes(
        "/** @interface */ function T() {}; T.prototype.x;",
        "interface property x is not annotated");
  }

  @Test
  public void testWarnUnannotatedPropertyOnInterface5() {
    testTypes("/** @interface */ u.T = function() {};\n" +
        "u.T.prototype.x = function() {};");
  }

  @Test
  public void testWarnUnannotatedPropertyOnInterface6() {
    testTypes("/** @interface */ function T() {};\n" +
        "T.prototype.x = function() {};");
  }

  // TODO(user): If we want to support this syntax we have to warn about
  // the invalid type of the interface member.
  @Test
  @Ignore
  public void testWarnDataPropertyOnInterface1() {
    testTypes(
        "/** @interface */ u.T = function() {};\n" + "/** @type {number} */u.T.prototype.x;",
        "interface members can only be plain functions");
  }

  @Test
  public void testDataPropertyOnInterface1() {
    testTypes("/** @interface */ function T() {};\n" +
        "/** @type {number} */T.prototype.x;");
  }

  @Test
  public void testDataPropertyOnInterface2() {
    testTypes(
        "/** @interface */ function T() {};\n"
            + "/** @type {number} */T.prototype.x;\n"
            + "/** @constructor \n"
            + " *  @implements {T} \n"
            + " */\n"
            + "function C() {}\n"
            + "/** @override */\n"
            + "C.prototype.x = 'foo';",
        "mismatch of the x property on type C and the type of the property it "
            + "overrides from interface T\n"
            + "original: number\n"
            + "override: string");
  }

  @Test
  public void testDataPropertyOnInterface3() {
    testTypes(
        "/** @interface */ function T() {};\n"
            + "/** @type {number} */T.prototype.x;\n"
            + "/** @constructor \n"
            + " *  @implements {T} \n"
            + " */\n"
            + "function C() {}\n"
            + "/** @override */\n"
            + "C.prototype.x = 'foo';",
        "mismatch of the x property on type C and the type of the property it "
            + "overrides from interface T\n"
            + "original: number\n"
            + "override: string");
  }

  @Test
  public void testDataPropertyOnInterface4() {
    testTypes(
        "/** @interface */ function T() {};\n"
            + "/** @type {number} */T.prototype.x;\n"
            + "/** @constructor \n"
            + " *  @implements {T} \n"
            + " */\n"
            + "function C() { /** @type {string} */ \n this.x = 'foo'; }\n",
        "mismatch of the x property on type C and the type of the property it "
            + "overrides from interface T\n"
            + "original: number\n"
            + "override: string");
  }

  @Test
  public void testWarnDataPropertyOnInterface3() {
    testTypes("/** @interface */ u.T = function() {};\n" +
        "/** @type {number} */u.T.prototype.x = 1;",
        "interface members can only be empty property declarations, "
        + "empty functions, or goog.abstractMethod");
  }

  @Test
  public void testWarnDataPropertyOnInterface4() {
    testTypes("/** @interface */ function T() {};\n" +
        "/** @type {number} */T.prototype.x = 1;",
        "interface members can only be empty property declarations, "
        + "empty functions, or goog.abstractMethod");
  }

  // TODO(user): If we want to support this syntax we should warn about the
  // mismatching types in the two tests below.
  @Test
  @Ignore
  public void testErrorMismatchingPropertyOnInterface1() {
    testTypes(
        "/** @interface */ u.T = function() {};\n"
            + "/** @param {Number} foo */u.T.prototype.x =\n"
            + "/** @param {String} foo */function(foo) {};",
        "found   : \n" + "required: ");
  }

  @Test
  @Ignore
  public void testErrorMismatchingPropertyOnInterface2() {
    testTypes(
        "/** @interface */ function T() {};\n"
            + "/** @return {number} */T.prototype.x =\n"
            + "/** @return {string} */function() {};",
        "found   : \n" + "required: ");
  }

  // TODO(user): We should warn about this (bar is missing an annotation). We
  // probably don't want to warn about all missing parameter annotations, but
  // we should be as strict as possible regarding interfaces.
  @Test
  @Ignore
  public void testErrorMismatchingPropertyOnInterface3() {
    testTypes(
        "/** @interface */ u.T = function() {};\n"
            + "/** @param {Number} foo */u.T.prototype.x =\n"
            + "function(foo, bar) {};",
        "found   : \n" + "required: ");
  }

  @Test
  public void testErrorMismatchingPropertyOnInterface4() {
    testTypes("/** @interface */ u.T = function() {};\n" +
        "/** @param {Number} foo */u.T.prototype.x =\n" +
        "function() {};",
        "parameter foo does not appear in u.T.prototype.x's parameter list");
  }

  @Test
  public void testErrorMismatchingPropertyOnInterface5() {
    testTypes("/** @interface */ function T() {};\n" +
        "/** @type {number} */T.prototype.x = function() { };",
        "assignment to property x of T.prototype\n" +
        "found   : function(): undefined\n" +
        "required: number");
  }

  @Test
  public void testErrorMismatchingPropertyOnInterface6() {
    testClosureTypesMultipleWarnings(
        "/** @interface */ function T() {};\n" +
        "/** @return {number} */T.prototype.x = 1",
        ImmutableList.of(
            "assignment to property x of T.prototype\n" +
            "found   : number\n" +
            "required: function(this:T): number",
            "interface members can only be empty property declarations, " +
            "empty functions, or goog.abstractMethod"));
  }

  @Test
  public void testInterfaceNonEmptyFunction() {
    testTypes("/** @interface */ function T() {};\n" +
        "T.prototype.x = function() { return 'foo'; }",
        "interface member functions must have an empty body"
        );
  }

  @Test
  public void testDoubleNestedInterface() {
    testTypes("/** @interface */ var I1 = function() {};\n" +
              "/** @interface */ I1.I2 = function() {};\n" +
              "/** @interface */ I1.I2.I3 = function() {};\n");
  }

  @Test
  public void testStaticDataPropertyOnNestedInterface() {
    testTypes("/** @interface */ var I1 = function() {};\n" +
              "/** @interface */ I1.I2 = function() {};\n" +
              "/** @type {number} */ I1.I2.x = 1;\n");
  }

  @Test
  public void testInterfaceInstantiation() {
    testTypes("/** @interface */var f = function(){}; new f",
              "cannot instantiate non-constructor");
  }

  @Test
  public void testPrototypeLoop() {
    disableStrictMissingPropertyChecks();

    testClosureTypesMultipleWarnings(
        suppressMissingProperty("foo") +
        "/** @constructor \n * @extends {T} */var T = function() {};" +
        "alert((new T).foo);",
        ImmutableList.of(
            "Parse error. Cycle detected in inheritance chain of type T",
            "Could not resolve type in @extends tag of T"));
  }

  @Test
  public void testImplementsLoop() {
    testClosureTypesMultipleWarnings(
        lines(
            "/** @constructor \n * @implements {T} */var T = function() {};",
            suppressMissingPropertyFor("T", "foo"),
            "alert((new T).foo);"),
        ImmutableList.of(
            "Parse error. Cycle detected in inheritance chain of type T"));
  }

  @Test
  public void testImplementsExtendsLoop() {
    disableStrictMissingPropertyChecks();

    testClosureTypesMultipleWarnings(
        suppressMissingProperty("foo") +
            "/** @constructor \n * @implements {F} */var G = function() {};" +
            "/** @constructor \n * @extends {G} */var F = function() {};" +
        "alert((new F).foo);",
        ImmutableList.of(
            "Parse error. Cycle detected in inheritance chain of type F"));
  }

  // TODO(johnlenz): This test causes an infinite loop,
  public void disable_testInterfaceExtendsLoop() {
    testClosureTypesMultipleWarnings(
        lines(
            "/** @interface \n * @extends {F} */var G = function() {};",
            "/** @interface \n * @extends {G} */var F = function() {};",
            "/** @constructor \n * @implements {F} */var H = function() {};",
            suppressMissingPropertyFor("H", "foo"),
            "alert((new H).foo);"),
        ImmutableList.of(
            "extends loop involving F, "
            + "loop: F -> G -> F",
            "extends loop involving G, "
            + "loop: G -> F -> G"));
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
        "Parse error. Cycle detected in inheritance chain of type F");
  }

  @Test
  public void testInheritPropFromMultipleInterfaces1() {
    // Low#prop gets the type of whichever property is declared last,
    // even if that type is not the most specific.
    testTypes(
        lines(
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
            "function f(/** !Low */ x) { var /** null */ n = x.prop; }"),
        lines(
            "initializing variable",
            "found   : (number|string)",
            "required: null"));
  }

  @Test
  public void testInheritPropFromMultipleInterfaces2() {
    // Low#prop gets the type of whichever property is declared last,
    // even if that type is not the most specific.
    testTypes(
        lines(
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
            "function f(/** !Low */ x) { var /** null */ n = x.prop; }"),
        lines(
            "initializing variable",
            "found   : number",
            "required: null"));
  }

  @Test
  public void testInheritPropFromMultipleInterfaces3() {
    testTypes(
        lines(
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
            "function MyFilteredSetMultimap() {}"));
  }

  @Test
  public void testInheritSameGenericInterfaceFromDifferentPaths() {
    testTypes(
        lines(
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
            "ns.Low = function() {};"));
  }

  @Test
  public void testConversionFromInterfaceToRecursiveConstructor() {
    testClosureTypesMultipleWarnings(
        suppressMissingProperty("foo") +
            "/** @interface */ var OtherType = function() {}\n" +
            "/** @implements {MyType} \n * @constructor */\n" +
            "var MyType = function() {}\n" +
            "/** @type {MyType} */\n" +
            "var x = /** @type {!OtherType} */ (new Object());",
        ImmutableList.of(
            "Parse error. Cycle detected in inheritance chain of type MyType",
            "initializing variable\n" +
            "found   : OtherType\n" +
            "required: (MyType|null)"));
  }

  @Test
  public void testDirectPrototypeAssign() {
    // For now, we just ignore @type annotations on the prototype.
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @constructor */ function Bar() {}" +
        "/** @type {Array} */ Bar.prototype = new Foo()");
  }

  // In all testResolutionViaRegistry* tests, since u is unknown, u.T can only
  // be resolved via the registry and not via properties.

  @Test
  public void testResolutionViaRegistry1() {
    testTypes("/** @constructor */ u.T = function() {};\n" +
        "/** @type {(number|string)} */ u.T.prototype.a;\n" +
        "/**\n" +
        "* @param {u.T} t\n" +
        "* @return {string}\n" +
        "*/\n" +
        "var f = function(t) { return t.a; };",
        "inconsistent return type\n" +
        "found   : (number|string)\n" +
        "required: string");
  }

  @Test
  public void testResolutionViaRegistry2() {
    testTypes(
        "/** @constructor */ u.T = function() {" +
        "  this.a = 0; };\n" +
        "/**\n" +
        "* @param {u.T} t\n" +
        "* @return {string}\n" +
        "*/\n" +
        "var f = function(t) { return t.a; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testResolutionViaRegistry3() {
    testTypes("/** @constructor */ u.T = function() {};\n" +
        "/** @type {(number|string)} */ u.T.prototype.a = 0;\n" +
        "/**\n" +
        "* @param {u.T} t\n" +
        "* @return {string}\n" +
        "*/\n" +
        "var f = function(t) { return t.a; };",
        "inconsistent return type\n" +
        "found   : (number|string)\n" +
        "required: string");
  }

  @Test
  public void testResolutionViaRegistry4() {
    testTypes("/** @constructor */ u.A = function() {};\n" +
        "/**\n* @constructor\n* @extends {u.A}\n*/\nu.A.A = function() {}\n;" +
        "/**\n* @constructor\n* @extends {u.A}\n*/\nu.A.B = function() {};\n" +
        "var ab = new u.A.B();\n" +
        "/** @type {!u.A} */ var a = ab;\n" +
        "/** @type {!u.A.A} */ var aa = ab;\n",
        "initializing variable\n" +
        "found   : u.A.B\n" +
        "required: u.A.A");
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
    Node n = parseAndTypeCheck("/** @constructor */ var T = function() {};" +
        "/** @type {!T} */var t; t.x; t;");
    JSType type = n.getLastChild().getLastChild().getJSType();
    assertThat(type.isUnknownType()).isFalse();
    assertThat(type).isInstanceOf(ObjectType.class);
    ObjectType objectType = (ObjectType) type;
    assertThat(objectType.hasProperty("x")).isFalse();
  }

  @Test
  public void testGatherProperyWithoutAnnotation2() {
    TypeCheckResult ns =
        parseAndTypeCheckWithScope("/** @type {!Object} */var t; t.x; t;");
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
    testTypes("var x = 4; var f = function x(b) { return b ? 1 : x(true); };",
        "function x masks variable (IE bug)");
  }

  @Test
  public void testDfa1() {
    testTypes("var x = null;\n x = 1;\n /** @type {number} */ var y = x;");
  }

  @Test
  public void testDfa2() {
    testTypes("function u() {}\n" +
        "/** @return {number} */ function f() {\nvar x = 'todo';\n" +
        "if (u()) { x = 1; } else { x = 2; } return x;\n}");
  }

  @Test
  public void testDfa3() {
    testTypes("function u() {}\n" +
        "/** @return {number} */ function f() {\n" +
        "/** @type {number|string} */ var x = 'todo';\n" +
        "if (u()) { x = 1; } else { x = 2; } return x;\n}");
  }

  @Test
  public void testDfa4() {
    testTypes("/** @param {Date?} d */ function f(d) {\n" +
        "if (!d) { return; }\n" +
        "/** @type {!Date} */ var e = d;\n}");
  }

  @Test
  public void testDfa5() {
    testTypes("/** @return {string?} */ function u() {return 'a';}\n" +
        "/** @param {string?} x\n@return {string} */ function f(x) {\n" +
        "while (!x) { x = u(); }\nreturn x;\n}");
  }

  @Test
  public void testDfa6() {
    testTypes("/** @return {Object?} */ function u() {return {};}\n" +
        "/** @param {Object?} x */ function f(x) {\n" +
        "while (x) { x = u(); if (!x) { x = u(); } }\n}");
  }

  @Test
  public void testDfa7() {
    testTypes("/** @constructor */ var T = function() {};\n" +
        "/** @type {Date?} */ T.prototype.x = null;\n" +
        "/** @param {!T} t */ function f(t) {\n" +
        "if (!t.x) { return; }\n" +
        "/** @type {!Date} */ var e = t.x;\n}");
  }

  @Test
  public void testDfa8() {
    testTypes("/** @constructor */ var T = function() {};\n" +
        "/** @type {number|string} */ T.prototype.x = '';\n" +
        "function u() {}\n" +
        "/** @param {!T} t\n@return {number} */ function f(t) {\n" +
        "if (u()) { t.x = 1; } else { t.x = 2; } return t.x;\n}");
  }

  @Test
  public void testDfa9() {
    testTypes("function f() {\n/** @type {string?} */var x;\nx = null;\n" +
        "if (x == null) { return 0; } else { return 1; } }",
        "condition always evaluates to true\n" +
        "left : null\n" +
        "right: null");
  }

  @Test
  public void testDfa10() {
    testTypes("/** @param {null} x */ function g(x) {}" +
        "/** @param {string?} x */function f(x) {\n" +
        "if (!x) { x = ''; }\n" +
        "if (g(x)) { return 0; } else { return 1; } }",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : string\n" +
        "required: null");
  }

  @Test
  public void testDfa11() {
    testTypes("/** @param {string} opt_x\n@return {string} */\n" +
        "function f(opt_x) { if (!opt_x) { " +
        "throw new Error('x cannot be empty'); } return opt_x; }");
  }

  @Test
  public void testDfa12() {
    testTypes("/** @param {string} x \n * @constructor \n */" +
        "var Bar = function(x) {};" +
        "/** @param {string} x */ function g(x) { return true; }" +
        "/** @param {string|number} opt_x */ " +
        "function f(opt_x) { " +
        "  if (opt_x) { new Bar(g(opt_x) && 'x'); }" +
        "}",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : (number|string)\n" +
        "required: string");
  }

  @Test
  public void testDfa13() {
    testTypes(
        "/**\n" +
        " * @param {string} x \n" +
        " * @param {number} y \n" +
        " * @param {number} z \n" +
        " */" +
        "function g(x, y, z) {}" +
        "function f() { " +
        "  var x = 'a'; g(x, x = 3, x);" +
        "}");
  }

  @Test
  public void testTypeInferenceWithCast1() {
    testTypes(
        "/**@return {(number|null|undefined)}*/function u(x) {return null;}" +
        "/**@param {number?} x\n@return {number?}*/function f(x) {return x;}" +
        "/**@return {number?}*/function g(x) {" +
        "var y = /**@type {number?}*/(u(x)); return f(y);}");
  }

  @Test
  public void testTypeInferenceWithCast2() {
    testTypes(
        "/**@return {(number|null|undefined)}*/function u(x) {return null;}" +
        "/**@param {number?} x\n@return {number?}*/function f(x) {return x;}" +
        "/**@return {number?}*/function g(x) {" +
        "var y; y = /**@type {number?}*/(u(x)); return f(y);}");
  }

  @Test
  public void testTypeInferenceWithCast3() {
    testTypes(
        "/**@return {(number|null|undefined)}*/function u(x) {return 1;}" +
        "/**@return {number}*/function g(x) {" +
        "return /**@type {number}*/(u(x));}");
  }

  @Test
  public void testTypeInferenceWithCast4() {
    testTypes(
        "/**@return {(number|null|undefined)}*/function u(x) {return 1;}" +
        "/**@return {number}*/function g(x) {" +
        "return /**@type {number}*/(u(x)) && 1;}");
  }

  @Test
  public void testTypeInferenceWithCast5() {
    testTypes(
        "/** @param {number} x */ function foo(x) {}" +
        "/** @param {{length:*}} y */ function bar(y) {" +
        "  /** @type {string} */ y.length;" +
        "  foo(y.length);" +
        "}",
        "actual parameter 1 of foo does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testTypeInferenceWithClosure1() {
    testTypes(
        "/** @return {boolean} */" +
        "function f() {" +
        "  /** @type {?string} */ var x = null;" +
        "  function g() { x = 'y'; } g(); " +
        "  return x == null;" +
        "}");
  }

  @Test
  public void testTypeInferenceWithClosure2() {
    testTypes(
        "/** @return {boolean} */" +
        "function f() {" +
        "  /** @type {?string} */ var x = null;" +
        "  function g() { x = 'y'; } g(); " +
        "  return x === 3;" +
        "}",
        "condition always evaluates to false\n" +
        "left : (null|string)\n" +
        "right: number");
  }

  @Test
  public void testTypeInferenceWithNoEntry1() {
    testTypes(
        "/** @param {number} x */ function f(x) {}" +
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.init = function() {" +
        "  /** @type {?{baz: number}} */ this.bar = {baz: 3};" +
        "};" +
        "/**\n" +
        " * @extends {Foo}\n" +
        " * @constructor\n" +
        " */" +
        "function SubFoo() {}" +
        "/** Method */" +
        "SubFoo.prototype.method = function() {" +
        "  for (var i = 0; i < 10; i++) {" +
        "    f(this.bar);" +
        "    f(this.bar.baz);" +
        "  }" +
        "};",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : (null|{baz: number})\n" +
        "required: number");
  }

  @Test
  public void testTypeInferenceWithNoEntry2() {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @param {number} x */ function f(x) {}" +
        "/** @param {!Object} x */ function g(x) {}" +
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.init = function() {" +
        "  /** @type {?{baz: number}} */ this.bar = {baz: 3};" +
        "};" +
        "/**\n" +
        " * @extends {Foo}\n" +
        " * @constructor\n" +
        " */" +
        "function SubFoo() {}" +
        "/** Method */" +
        "SubFoo.prototype.method = function() {" +
        "  for (var i = 0; i < 10; i++) {" +
        "    f(this.bar);" +
        "    goog.asserts.assert(this.bar);" +
        "    g(this.bar);" +
        "  }" +
        "};",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : (null|{baz: number})\n" +
        "required: number");
  }

  @Test
  public void testForwardPropertyReference() {
    testTypes("/** @constructor */ var Foo = function() { this.init(); };" +
        "/** @return {string} */" +
        "Foo.prototype.getString = function() {" +
        "  return this.number_;" +
        "};" +
        "Foo.prototype.init = function() {" +
        "  /** @type {number} */" +
        "  this.number_ = 3;" +
        "};",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testNoForwardTypeDeclaration() {
    testTypes(
        "/** @param {MyType} x */ function f(x) {}",
        "Bad type annotation. Unknown type MyType");
  }

  @Test
  public void testNoForwardTypeDeclarationAndNoBraces() {
    testTypes("/** @return The result. */ function f() {}",
        RhinoErrorReporter.JSDOC_MISSING_TYPE_WARNING);
  }

  @Test
  public void testForwardTypeDeclaration1() {
    testClosureTypes(
        // malformed addDependency calls shouldn't cause a crash
        "goog.addDependency();" +
        "goog.addDependency('y', [goog]);" +

        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType} x \n * @return {number} */" +
        "function f(x) { return 3; }", null);
  }

  @Test
  public void testForwardTypeDeclaration2() {
    String f = "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType} x */ function f(x) { }";
    testClosureTypes(f, null);
    testClosureTypes(f + "f(3);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: (MyType|null)");
  }

  @Test
  public void testForwardTypeDeclaration3() {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType} x */ function f(x) { return x; }" +
        "/** @constructor */ var MyType = function() {};" +
        "f(3);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: (MyType|null)");
  }

  @Test
  public void testForwardTypeDeclaration4() {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType} x */ function f(x) { return x; }" +
        "/** @constructor */ var MyType = function() {};" +
        "f(new MyType());",
        null);
  }

  @Test
  public void testForwardTypeDeclaration5() {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MyType'], []);"
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
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/**\n" +
        " * @constructor\n" +
        " * @implements {MyType}\n" +
        " */ var YourType = function() {};" +
        "/** @override */ YourType.prototype.method = function() {};",
        ImmutableList.of(
            "Could not resolve type in @implements tag of YourType",
            "property method not defined on any superclass of YourType"));
  }

  @Test
  public void testForwardTypeDeclaration7() {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType=} x */" +
        "function f(x) { return x == undefined; }", null);
  }

  @Test
  public void testForwardTypeDeclaration8() {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType} x */" +
        "function f(x) { return x.name == undefined; }", null);
  }

  @Test
  public void testForwardTypeDeclaration9() {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType} x */" +
        "function f(x) { x.name = 'Bob'; }", null);
  }

  @Test
  public void testForwardTypeDeclaration10() {
    String f = "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType|number} x */ function f(x) { }";
    testClosureTypes(f, null);
    testClosureTypes(f + "f(3);", null);
    testClosureTypes(f + "f('3');",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: (MyType|null|number)");
  }

  @Test
  public void testForwardTypeDeclaration12() {
    // We assume that {Function} types can produce anything, and don't
    // want to type-check them.
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/**\n" +
        " * @param {!Function} ctor\n" +
        " * @return {MyType}\n" +
        " */\n" +
        "function f(ctor) { return new ctor(); }", null);
  }

  @Test
  public void testForwardTypeDeclaration13() {
    // Some projects use {Function} registries to register constructors
    // that aren't in their binaries. We want to make sure we can pass these
    // around, but still do other checks on them.
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/**\n" +
        " * @param {!Function} ctor\n" +
        " * @return {MyType}\n" +
        " */\n" +
        "function f(ctor) { return (new ctor()).impossibleProp; }",
        "Property impossibleProp never defined on ?");
  }

  @Test
  public void testDuplicateTypeDef() {
    testTypes(
        "var goog = {};" +
        "/** @constructor */ goog.Bar = function() {};" +
        "/** @typedef {number} */ goog.Bar;",
        "variable goog.Bar redefined with type None, " +
        "original definition at [testcode]:1 " +
        "with type function(new:goog.Bar): undefined");
  }

  @Test
  public void testTypeDef1() {
    testTypes(
        "var goog = {};" +
        "/** @typedef {number} */ goog.Bar;" +
        "/** @param {goog.Bar} x */ function f(x) {}" +
        "f(3);");
  }

  @Test
  public void testTypeDef2() {
    testTypes(
        "var goog = {};" +
        "/** @typedef {number} */ goog.Bar;" +
        "/** @param {goog.Bar} x */ function f(x) {}" +
        "f('3');",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testTypeDef3() {
    testTypes(
        "var goog = {};" +
        "/** @typedef {number} */ var Bar;" +
        "/** @param {Bar} x */ function f(x) {}" +
        "f('3');",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testTypeDef4() {
    testTypes(
        "/** @constructor */ function A() {}" +
        "/** @constructor */ function B() {}" +
        "/** @typedef {(A|B)} */ var AB;" +
        "/** @param {AB} x */ function f(x) {}" +
        "f(new A()); f(new B()); f(1);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: (A|B|null)");
  }

  @Test
  public void testTypeDef5() {
    // Notice that the error message is slightly different than
    // the one for testTypeDef4, even though they should be the same.
    // This is an implementation detail necessary for NamedTypes work out
    // OK, and it should change if NamedTypes ever go away.
    testTypes(
        "/** @param {AB} x */ function f(x) {}" +
        "/** @constructor */ function A() {}" +
        "/** @constructor */ function B() {}" +
        "/** @typedef {(A|B)} */ var AB;" +
        "f(new A()); f(new B()); f(1);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: (A|B|null)");
  }

  @Test
  public void testCircularTypeDef() {
    testTypes(
        "var goog = {};" +
        "/** @typedef {number|Array<goog.Bar>} */ goog.Bar;" +
        "/** @param {goog.Bar} x */ function f(x) {}" +
        "f(3); f([3]); f([[3]]);");
  }

  @Test
  public void testGetTypedPercent1() {
    String js = "var id = function(x) { return x; }\n" +
                "var id2 = function(x) { return id(x); }";
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
    assertThat(getTypedPercent(js)).isWithin(0.1).of(50.0);
  }

  @Test
  public void testGetTypedPercent4() {
    String js = "var n = {};\n /** @constructor */ n.T = function() {};\n" +
        "/** @type {n.T} */ var x = new n.T();";
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
    TypeCheckResult p = parseAndTypeCheckWithScope(
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
    testTypes(
        lines(
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.bar = function() { return this.a; };",
            "Foo.prototype.baz = function() { this.a = 3; };"),
        ImmutableList.of("Property a never defined on Foo", "Property a never defined on Foo"));
  }

  @Test
  public void testMissingProperty1b() {
    disableStrictMissingPropertyChecks();

    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() { return this.a; };" +
        "Foo.prototype.baz = function() { this.a = 3; };");
  }

  @Test
  public void testMissingProperty2a() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() { return this.a; };" +
        "Foo.prototype.baz = function() { this.b = 3; };",
        "Property a never defined on Foo");
  }

  @Test
  public void testMissingProperty2b() {
    testTypes(
        lines(
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.baz = function() { this.b = 3; };"),
        "Property b never defined on Foo");
  }

  @Test
  public void testMissingProperty3a() {
    testTypes(
        lines(
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.bar = function() { return this.a; };",
            "(new Foo).a = 3;"),
        ImmutableList.of(
            "Property a never defined on Foo", // method
            "Property a never defined on Foo")); // global assignment
  }

  @Test
  public void testMissingProperty3b() {
    disableStrictMissingPropertyChecks();

    testTypes(lines(
        "/** @constructor */ function Foo() {}",
        "Foo.prototype.bar = function() { return this.a; };",
        "(new Foo).a = 3;"));
  }

  @Test
  public void testMissingProperty4a() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() { return this.a; };" +
        "(new Foo).b = 3;",
        "Property a never defined on Foo");
  }

  @Test
  public void testMissingProperty4b() {
    testTypes(
        lines(
            "/** @constructor */ function Foo() {}",
            "(new Foo).b = 3;"),
        "Property b never defined on Foo");
  }

  @Test
  public void testMissingProperty5() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() { return this.a; };" +
        "/** @constructor */ function Bar() { this.a = 3; };",
        "Property a never defined on Foo");
  }

  @Test
  public void testMissingProperty6a() {
    testTypes(lines(
        "/** @constructor */ function Foo() {}",
        "Foo.prototype.bar = function() { return this.a; };",
        "/** @constructor \n * @extends {Foo} */ ",
        "function Bar() { this.a = 3; };"),
        "Property a never defined on Foo");
  }

  @Test
  public void testMissingProperty6b() {
    disableStrictMissingPropertyChecks();

    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() { return this.a; };" +
        "/** @constructor \n * @extends {Foo} */ " +
        "function Bar() { this.a = 3; };");
  }

  @Test
  public void testMissingProperty7() {
    testTypes(
        "/** @param {Object} obj */" +
        "function foo(obj) { return obj.impossible; }",
        "Property impossible never defined on Object");
  }

  @Test
  public void testMissingProperty8() {
    testTypes(
        "/** @param {Object} obj */" +
        "function foo(obj) { return typeof obj.impossible; }");
  }

  @Test
  public void testMissingProperty9() {
    disableStrictMissingPropertyChecks();

    testTypes(
        "/** @param {Object} obj */" +
        "function foo(obj) { if (obj.impossible) { return true; } }");
  }

  @Test
  public void testMissingProperty10() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @param {Object} obj */" +
        "function foo(obj) { while (obj.impossible) { return true; } }");
  }

  @Test
  public void testMissingProperty11() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @param {Object} obj */" +
        "function foo(obj) { for (;obj.impossible;) { return true; } }");
  }

  @Test
  public void testMissingProperty12() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @param {Object} obj */" +
        "function foo(obj) { do { } while (obj.impossible); }");
  }

  @Test
  public void testMissingProperty13() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "var goog = {}; goog.isDef = function(x) { return false; };" +
        "/** @param {Object} obj */" +
        "function foo(obj) { return goog.isDef(obj.impossible); }");
  }

  @Test
  public void testMissingProperty14() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "var goog = {}; goog.isDef = function(x) { return false; };" +
        "/** @param {Object} obj */" +
        "function foo(obj) { return goog.isNull(obj.impossible); }",
        "Property isNull never defined on goog");
  }

  @Test
  public void testMissingProperty15() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { if (x.foo) { x.foo(); } }");
  }

  @Test
  public void testMissingProperty16() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { x.foo(); if (x.foo) {} }",
        "Property foo never defined on Object");
  }

  @Test
  public void testMissingProperty17() {
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { if (typeof x.foo == 'function') { x.foo(); } }");
  }

  @Test
  public void testMissingProperty18() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { if (x.foo instanceof Function) { x.foo(); } }");
  }

  @Test
  public void testMissingProperty19() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { if (x.bar) { if (x.foo) {} } else { x.foo(); } }",
        "Property foo never defined on Object");
  }

  @Test
  public void testMissingProperty20() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { if (x.foo) { } else { x.foo(); } }",
        "Property foo never defined on Object");
  }

  @Test
  public void testMissingProperty21() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { x.foo && x.foo(); }");
  }

  @Test
  public void testMissingProperty22() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @param {Object} x \n * @return {boolean} */" +
        "function f(x) { return x.foo ? x.foo() : true; }");
  }

  @Test
  public void testMissingProperty23() {
    testTypes(
        "function f(x) { x.impossible(); }",
        "Property impossible never defined on x");
  }

  @Test
  public void testMissingProperty24() {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MissingType'], []);" +
        "/** @param {MissingType} x */" +
        "function f(x) { x.impossible(); }", null);
  }

  @Test
  public void testMissingProperty25() {
    testTypes(
        "/** @constructor */ var Foo = function() {};" +
        "Foo.prototype.bar = function() {};" +
        "/** @constructor */ var FooAlias = Foo;" +
        "(new FooAlias()).bar();");
  }

  @Test
  public void testMissingProperty26() {
    testTypes(
        "/** @constructor */ var Foo = function() {};" +
        "/** @constructor */ var FooAlias = Foo;" +
        "FooAlias.prototype.bar = function() {};" +
        "(new Foo()).bar();");
  }

  @Test
  public void testMissingProperty27() {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MissingType'], []);" +
        "/** @param {?MissingType} x */" +
        "function f(x) {" +
        "  for (var parent = x; parent; parent = parent.getParent()) {}" +
        "}", null);
  }

  @Test
  public void testMissingProperty28a() {
    testTypes(
        "function f(obj) {" +
        "  /** @type {*} */ obj.foo;" +
        "  return obj.foo;" +
        "}");
  }

  @Test
  public void testMissingProperty28b() {
    testTypes(
        "function f(obj) {" +
        "  /** @type {*} */ obj.foo;" +
        "  return obj.foox;" +
        "}",
        "Property foox never defined on obj");
  }

  @Test
  public void testMissingProperty29() {
    // This used to emit a warning.
    testTypesWithExterns(
        // externs
        "/** @constructor */ var Foo;" +
        "Foo.prototype.opera;" +
        "Foo.prototype.opera.postError;",
        "");
  }

  @Test
  public void testMissingProperty30a() {
    testTypes(
        lines(
            "/** @return {*} */",
            "function f() {",
            " return {};",
            "}",
            "f().a = 3;",
            "/** @param {Object} y */ function g(y) { return y.a; }"),
        ImmutableList.of("Property a never defined on *", "Property a never defined on Object"));
  }

  @Test
  public void testMissingProperty30b() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @return {*} */"
            + "function f() {"
            + " return {};"
            + "}"
            + "f().a = 3;"
            + "/** @param {Object} y */ function g(y) { return y.a; }");
  }

  @Test
  public void testMissingProperty31a() {
    testTypes(
        lines("/** @return {Array|number} */", "function f() {", " return [];", "}", "f().a = 3;"),
        "Property a never defined on (Array|Number)");
  }

  @Test
  public void testMissingProperty31b() {
    disableStrictMissingPropertyChecks();

    testTypes(
        "/** @return {Array|number} */" +
        "function f() {" +
        " return [];" +
        "}" +
        "f().a = 3;" +
        "/** @param {Array} y */ function g(y) { return y.a; }");
  }

  @Test
  public void testMissingProperty32() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/** @return {Array|number} */" +
        "function f() {" +
        " return [];" +
        "}" +
        "f().a = 3;" +
        "/** @param {Date} y */ function g(y) { return y.a; }",
        "Property a never defined on Date");
  }

  @Test
  public void testMissingProperty33() {
    disableStrictMissingPropertyChecks();
    testTypes(
      "/** @param {Object} x */" +
      "function f(x) { !x.foo || x.foo(); }");
  }

  @Test
  public void testMissingProperty34() {
    testTypes(
        "/** @fileoverview \n * @suppress {missingProperties} */" +
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() { return this.a; };" +
        "Foo.prototype.baz = function() { this.b = 3; };");
  }

  @Test
  public void testMissingProperty35a() {
    // Bar has specialProp defined, so Bar|Baz may have specialProp defined.
    testTypes(
        lines(
            "/** @constructor */ function Foo() {}",
            "/** @constructor */ function Bar() {}",
            "/** @constructor */ function Baz() {}",
            "/** @param {Foo|Bar} x */ function f(x) { x.specialProp = 1; }",
            "/** @param {Bar|Baz} x */ function g(x) { return x.specialProp; }"),
        ImmutableList.of(
            "Property specialProp never defined on (Foo|Bar)",
            "Property specialProp never defined on (Bar|Baz)"));
  }

  @Test
  public void testMissingProperty35b() {
    disableStrictMissingPropertyChecks();

    // Bar has specialProp defined, so Bar|Baz may have specialProp defined.
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @constructor */ function Bar() {}" +
        "/** @constructor */ function Baz() {}" +
        "/** @param {Foo|Bar} x */ function f(x) { x.specialProp = 1; }" +
        "/** @param {Bar|Baz} x */ function g(x) { return x.specialProp; }");
  }

  @Test
  public void testMissingProperty36a() {
    // Foo has baz defined, and SubFoo has bar defined, so some objects with
    // bar may have baz.
    testTypes(lines(
        "/** @constructor */ function Foo() {}",
        "Foo.prototype.baz = 0;",
        "/** @constructor \n * @extends {Foo} */ function SubFoo() {}",
        "SubFoo.prototype.bar = 0;",
        "/** @param {{bar: number}} x */ function f(x) { return x.baz; }"),
        "Property baz never defined on x");
  }

  @Test
  public void testMissingProperty36b() {
    disableStrictMissingPropertyChecks();

    // Foo has baz defined, and SubFoo has bar defined, so some objects with
    // bar may have baz.
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.baz = 0;" +
        "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
        "SubFoo.prototype.bar = 0;" +
        "/** @param {{bar: number}} x */ function f(x) { return x.baz; }");
  }

  @Test
  public void testMissingProperty37a() {
    // This used to emit a missing property warning because we couldn't
    // determine that the inf(Foo, {isVisible:boolean}) == SubFoo.
    testTypes(lines(
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
        "function g(x) { return x.isVisible; }"),
        "Property isVisible never defined on Foo");
  }

  @Test
  public void testMissingProperty37b() {
    disableStrictMissingPropertyChecks();

    // This used to emit a missing property warning because we couldn't
    // determine that the inf(Foo, {isVisible:boolean}) == SubFoo.
    testTypes(
        "/** @param {{isVisible: boolean}} x */ function f(x){" +
        "  x.isVisible = false;" +
        "}" +
        "/** @constructor */ function Foo() {}" +
        "/**\n" +
        " * @constructor \n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() {}" +
        "/** @type {boolean} */ SubFoo.prototype.isVisible = true;" +
        "/**\n" +
        " * @param {Foo} x\n" +
        " * @return {boolean}\n" +
        " */\n" +
        "function g(x) { return x.isVisible; }");
  }

  @Test
  public void testMissingProperty38() {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @constructor */ function Bar() {}" +
        "/** @return {Foo|Bar} */ function f() { return new Foo(); }" +
        "f().missing;",
        "Property missing never defined on (Foo|Bar)");
  }

  @Test
  public void testMissingProperty39a() {
    disableStrictMissingPropertyChecks();
    testTypesWithExterns(
        new TestExternsBuilder().addString().build(),
        lines(
            "/** @return {string|number} */ function f() { return 3; }", // preserve newlines
            "f().length;"));
  }

  @Test
  public void testMissingProperty39b() {
    testTypesWithExterns(
        new TestExternsBuilder().addString().build(),
        lines(
            "/** @return {string|number} */ function f() { return 3; }", // preserve newlines
            "f().length;")
        // TODO(johnlenz): enable this.
        // "Property length not defined on all member types of (String|Number)"
        );
  }

  @Test
  public void testMissingProperty40a() {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MissingType'], []);"
            + "/** @param {MissingType} x */"
            + "function f(x) { x.impossible(); }",
        null);
  }

  @Test
  public void testMissingProperty40b() {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MissingType'], []);"
            + "/** @param {(Array|MissingType)} x */"
            + "function f(x) { x.impossible(); }",
        // TODO(johnlenz): enable this.
        // "Property impossible not defined on all member types of x"
        null);
  }

  @Test
  public void testMissingProperty41a() {
    testTypes(
        lines(
            "/** @param {(Array|Date)} x */",
            "function f(x) { if (x.impossible) x.impossible(); }"),
        ImmutableList.of(
            "Property impossible never defined on (Array|Date)",
            "Property impossible never defined on (Array|Date)"));
  }

  @Test
  public void testMissingProperty41b() {
    disableStrictMissingPropertyChecks();
    testTypes(
        lines(
            "/** @param {(Array|Date)} x */",
            "function f(x) { if (x.impossible) x.impossible(); }"));
  }

  @Test
  public void testMissingProperty42() {
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { " +
        "  if (typeof x.impossible == 'undefined') throw Error();" +
        "  return x.impossible;" +
        "}");
  }

  @Test
  public void testMissingProperty43() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "function f(x) { " +
        " return /** @type {number} */ (x.impossible) && 1;" +
        "}");
  }

  @Test
  public void testReflectObject1() {
    testClosureTypes(
        "var goog = {}; goog.reflect = {}; " +
        "goog.reflect.object = function(x, y){};" +
        "/** @constructor */ function A() {}" +
        "goog.reflect.object(A, {x: 3});",
        null);
  }

  @Test
  public void testReflectObject2() {
    testClosureTypes(
        "var goog = {}; goog.reflect = {}; " +
        "goog.reflect.object = function(x, y){};" +
        "/** @param {string} x */ function f(x) {}" +
        "/** @constructor */ function A() {}" +
        "goog.reflect.object(A, {x: f(1 + 1)});",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testLends1() {
    testTypes(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, /** @lends */ ({bar: 1}));",
        "Bad type annotation. missing object name in @lends tag." + BAD_TYPE_WIKI_LINK);
  }

  @Test
  public void testLends2() {
    testTypes(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, /** @lends {Foob} */ ({bar: 1}));",
        "Variable Foob not declared before @lends annotation.");
  }

  @Test
  public void testLends3() {
    testTypes(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, {bar: 1});" +
        "alert(Foo.bar);",
        "Property bar never defined on Foo");
  }

  @Test
  public void testLends4() {
    testTypes(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, /** @lends {Foo} */ ({bar: 1}));" +
        "alert(Foo.bar);");
  }

  @Test
  public void testLends5() {
    testTypes(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, {bar: 1});" +
        "alert((new Foo()).bar);",
        "Property bar never defined on Foo");
  }

  @Test
  public void testLends6() {
    testTypes(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, /** @lends {Foo.prototype} */ ({bar: 1}));" +
        "alert((new Foo()).bar);");
  }

  @Test
  public void testLends7() {
    testTypes(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, /** @lends {Foo.prototype|Foo} */ ({bar: 1}));",
        "Bad type annotation. expected closing }" + BAD_TYPE_WIKI_LINK);
  }

  @Test
  public void testLends8() {
    testTypes(
        "function extend(x, y) {}" +
        "/** @type {number} */ var Foo = 3;" +
        "extend(Foo, /** @lends {Foo} */ ({bar: 1}));",
        "May only lend properties to object types. Foo has type number.");
  }

  @Test
  public void testLends9() {
    testClosureTypesMultipleWarnings(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, /** @lends {!Foo} */ ({bar: 1}));",
        ImmutableList.of(
            "Bad type annotation. expected closing }" + BAD_TYPE_WIKI_LINK,
            "Bad type annotation. missing object name in @lends tag." + BAD_TYPE_WIKI_LINK));
  }

  @Test
  public void testLends10() {
    testTypes(
        "function defineClass(x) { return function() {}; } " +
        "/** @constructor */" +
        "var Foo = defineClass(" +
        "    /** @lends {Foo.prototype} */ ({/** @type {number} */ bar: 1}));" +
        "/** @return {string} */ function f() { return (new Foo()).bar; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testLends11() {
    testTypes(
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
            + "/** @return {string} */ function f() { return (new SubFoo()).bar(); }",
        "inconsistent return type\n" + "found   : number\n" + "required: string");
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
    Node n = parseAndTypeCheck("/** @param {number} a \n"
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
    Node n = parseAndTypeCheck("/** @constructor */ function Foo() {};\n"
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
    Node n = parseAndTypeCheck(
        "/** @constructor */ function Foo() {};\n" +
        "goog.addSingletonGetter(Foo);");
    ObjectType o = (ObjectType) n.getFirstChild().getJSType();
    assertThat(o.getPropertyType("getInstance").toString()).isEqualTo("function(): Foo");
    assertThat(o.getPropertyType("instance_").toString()).isEqualTo("Foo");
  }

  @Test
  public void testTypeCheckStandaloneAST() {
    Node n = compiler.parseTestCode("function Foo() { }");
    typeCheck(n);
    TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);
    TypedScope topScope = scopeCreator.createScope(n, null);

    Node second = compiler.parseTestCode("new Foo");

    Node externs = IR.root();
    IR.root(externs, second);

    new TypeCheck(
            compiler,
            new SemanticReverseAbstractInterpreter(registry),
            registry,
            topScope,
            scopeCreator)
        .process(null, second);

    assertThat(compiler.getWarningCount()).isEqualTo(1);
    assertThat(compiler.getWarnings()[0].description)
        .isEqualTo("cannot instantiate non-constructor");
  }

  @Test
  public void testUpdateParameterTypeOnClosure() {
    testTypesWithExterns(
        "/**\n" +
        "* @constructor\n" +
        "* @param {*=} opt_value\n" +
        "* @return {!Object}\n" +
        "*/\n" +
        "function Object(opt_value) {}\n" +
        "/**\n" +
        "* @constructor\n" +
        "* @param {...*} var_args\n" +
        "*/\n" +
        "function Function(var_args) {}\n" +
        "/**\n" +
        "* @type {Function}\n" +
        "*/\n" +
        // The line below sets JSDocInfo on Object so that the type of the
        // argument to function f has JSDoc through its prototype chain.
        "Object.prototype.constructor = function() {};\n",
        "/**\n" +
        "* @param {function(): boolean} fn\n" +
        "*/\n" +
        "function f(fn) {}\n" +
        "f(function() { });\n");
  }

  @Test
  public void testTemplatedThisType1() {
    testTypes(
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "/**\n" +
        " * @this {T}\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "Foo.prototype.method = function() {};\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */\n" +
        "function Bar() {}\n" +
        "var g = new Bar().method();\n" +
        "/**\n" +
        " * @param {number} a\n" +
        " */\n" +
        "function compute(a) {};\n" +
        "compute(g);\n",

        "actual parameter 1 of compute does not match formal parameter\n" +
        "found   : Bar\n" +
        "required: number");
  }

  @Test
  public void testTemplatedThisType2() {
    testTypesWithCommonExterns(
        "/**\n" +
        " * @this {Array<T>|{length:number}}\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "Array.prototype.method = function() {};\n" +
        "(function(){\n" +
        "  Array.prototype.method.call(arguments);" +
        "})();");
  }

  @Test
  public void testTemplateType1() {
    testTypes(
        "/**\n" +
        "* @param {T} x\n" +
        "* @param {T} y\n" +
        "* @param {function(this:T, ...)} z\n" +
        "* @template T\n" +
        "*/\n" +
        "function f(x, y, z) {}\n" +
        "f(this, this, function() { this });");
  }

  @Test
  public void testTemplateType2() {
    // "this" types need to be coerced for ES3 style function or left
    // allow for ES5-strict methods.
    testTypes(
        "/**\n" +
        "* @param {T} x\n" +
        "* @param {function(this:T, ...)} y\n" +
        "* @template T\n" +
        "*/\n" +
        "function f(x, y) {}\n" +
        "f(0, function() {});");
  }

  @Test
  public void testTemplateType3() {
    testTypesWithCommonExterns(
        "/**" +
        " * @param {T} v\n" +
        " * @param {function(T)} f\n" +
        " * @template T\n" +
        " */\n" +
        "function call(v, f) { f.call(null, v); }" +
        "/** @type {string} */ var s;" +
        "call(3, function(x) {" +
        " x = true;" +
        " s = x;" +
        "});",
        "assignment\n" +
        "found   : boolean\n" +
        "required: string");
  }

  @Test
  public void testTemplateType4() {
    testTypes(
        "/**" +
        " * @param {...T} p\n" +
        " * @return {T} \n" +
        " * @template T\n" +
        " */\n" +
        "function fn(p) { return p; }\n" +
        "/** @type {!Object} */ var x;" +
        "x = fn(3, null);",
        "assignment\n" +
        "found   : (null|number)\n" +
        "required: Object");
  }

  @Test
  public void testTemplateType5() {
    compiler.getOptions().setCodingConvention(new GoogleCodingConvention());
    testTypes(
        "var CGI_PARAM_RETRY_COUNT = 'rc';" +
        "" +
        "/**" +
        " * @param {...T} p\n" +
        " * @return {T} \n" +
        " * @template T\n" +
        " */\n" +
        "function fn(p) { return p; }\n" +
        "/** @type {!Object} */ var x;" +
        "" +
        "/** @return {void} */\n" +
        "function aScope() {\n" +
        "  x = fn(CGI_PARAM_RETRY_COUNT, 1);\n" +
        "}",
        "assignment\n" +
        "found   : (number|string)\n" +
        "required: Object");
  }

  @Test
  public void testTemplateType6() {
    testTypesWithCommonExterns(
        "/**" +
        " * @param {Array<T>} arr \n" +
        " * @param {?function(T)} f \n" +
        " * @return {T} \n" +
        " * @template T\n" +
        " */\n" +
        "function fn(arr, f) { return arr[0]; }\n" +
        "/** @param {Array<number>} arr */ function g(arr) {" +
        "  /** @type {!Object} */ var x = fn.call(null, arr, null);" +
        "}",
        "initializing variable\n" +
        "found   : number\n" +
        "required: Object");
  }

  @Test
  public void testTemplateType7() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        lines(
            "/** @type {!Array<string>} */", // preserve newlines
            "var query = [];",
            "query.push(1);"),
        lines(
            "actual parameter 1 of Array.prototype.push does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testTemplateType8() {
    testTypes(
        "/** @constructor \n" +
        " * @template S,T\n" +
        " */\n" +
        "function Bar() {}\n" +
        "/**" +
        " * @param {Bar<T>} bar \n" +
        " * @return {T} \n" +
        " * @template T\n" +
        " */\n" +
        "function fn(bar) {}\n" +
        "/** @param {Bar<number>} bar */ function g(bar) {" +
        "  /** @type {!Object} */ var x = fn(bar);" +
        "}",
        "initializing variable\n" +
        "found   : number\n" +
        "required: Object");
  }

  @Test
  public void testTemplateType9() {
    // verify interface type parameters are recognized.
    testTypes(
        "/** @interface \n" +
        " * @template S,T\n" +
        " */\n" +
        "function Bar() {}\n" +
        "/**" +
        " * @param {Bar<T>} bar \n" +
        " * @return {T} \n" +
        " * @template T\n" +
        " */\n" +
        "function fn(bar) {}\n" +
        "/** @param {Bar<number>} bar */ function g(bar) {" +
        "  /** @type {!Object} */ var x = fn(bar);" +
        "}",
        "initializing variable\n" +
        "found   : number\n" +
        "required: Object");
  }

  @Test
  public void testTemplateType10() {
    // verify a type parameterized with unknown can be assigned to
    // the same type with any other type parameter.
    testTypes(
        "/** @constructor \n" +
        " * @template T\n" +
        " */\n" +
        "function Bar() {}\n" +
        "\n" +
        "" +
        "/** @type {!Bar<?>} */ var x;" +
        "/** @type {!Bar<number>} */ var y;" +
        "y = x;");
  }

  @Test
  public void testTemplateType11() {
    // verify that assignment/subtype relationships work when extending
    // templatized types.
    testTypes(
        "/** @constructor \n" +
        " * @template T\n" +
        " */\n" +
        "function Foo() {}\n" +
        "" +
        "/** @constructor \n" +
        " * @extends {Foo<string>}\n" +
        " */\n" +
        "function A() {}\n" +
        "" +
        "/** @constructor \n" +
        " * @extends {Foo<number>}\n" +
        " */\n" +
        "function B() {}\n" +
        "" +
        "/** @type {!Foo<string>} */ var a = new A();\n" +
        "/** @type {!Foo<string>} */ var b = new B();",
        "initializing variable\n" +
        "found   : B\n" +
        "required: Foo<string>");
  }

  @Test
  public void testTemplateType12() {
    // verify that assignment/subtype relationships work when implementing
    // templatized types.
    testTypes(
        "/** @interface \n" +
        " * @template T\n" +
        " */\n" +
        "function Foo() {}\n" +
        "" +
        "/** @constructor \n" +
        " * @implements {Foo<string>}\n" +
        " */\n" +
        "function A() {}\n" +
        "" +
        "/** @constructor \n" +
        " * @implements {Foo<number>}\n" +
        " */\n" +
        "function B() {}\n" +
        "" +
        "/** @type {!Foo<string>} */ var a = new A();\n" +
        "/** @type {!Foo<string>} */ var b = new B();",
        "initializing variable\n" +
        "found   : B\n" +
        "required: Foo<string>");
  }

  @Test
  public void testTemplateType13() {
    // verify that assignment/subtype relationships work when extending
    // templatized types.
    testTypes(
        "/** @constructor \n" +
        " * @template T\n" +
        " */\n" +
        "function Foo() {}\n" +
        "" +
        "/** @constructor \n" +
        " * @template T\n" +
        " * @extends {Foo<T>}\n" +
        " */\n" +
        "function A() {}\n" +
        "" +
        "var a1 = new A();\n" +
        "var a2 = /** @type {!A<string>} */ (new A());\n" +
        "var a3 = /** @type {!A<number>} */ (new A());\n" +
        "/** @type {!Foo<string>} */ var f1 = a1;\n" +
        "/** @type {!Foo<string>} */ var f2 = a2;\n" +
        "/** @type {!Foo<string>} */ var f3 = a3;",
        "initializing variable\n" +
        "found   : A<number>\n" +
        "required: Foo<string>");
  }

  @Test
  public void testTemplateType14() {
    // verify that assignment/subtype relationships work when implementing
    // templatized types.
    testTypes(
        "/** @interface \n" +
        " * @template T\n" +
        " */\n" +
        "function Foo() {}\n" +
        "" +
        "/** @constructor \n" +
        " * @template T\n" +
        " * @implements {Foo<T>}\n" +
        " */\n" +
        "function A() {}\n" +
        "" +
        "var a1 = new A();\n" +
        "var a2 = /** @type {!A<string>} */ (new A());\n" +
        "var a3 = /** @type {!A<number>} */ (new A());\n" +
        "/** @type {!Foo<string>} */ var f1 = a1;\n" +
        "/** @type {!Foo<string>} */ var f2 = a2;\n" +
        "/** @type {!Foo<string>} */ var f3 = a3;",
        "initializing variable\n" +
        "found   : A<number>\n" +
        "required: Foo<string>");
  }

  @Test
  public void testTemplateType15() {
    testTypes(
        "/**" +
        " * @param {{foo:T}} p\n" +
        " * @return {T} \n" +
        " * @template T\n" +
        " */\n" +
        "function fn(p) { return p.foo; }\n" +
        "/** @type {!Object} */ var x;" +
        "x = fn({foo:3});",
        "assignment\n" +
        "found   : number\n" +
        "required: Object");
  }

  @Test
  public void testTemplateType16() {
    testTypes(
        "/** @constructor */ function C() {\n" +
        "  /** @type {number} */ this.foo = 1\n" +
        "}\n" +
        "/**\n" +
        " * @param {{foo:T}} p\n" +
        " * @return {T} \n" +
        " * @template T\n" +
        " */\n" +
        "function fn(p) { return p.foo; }\n" +
        "/** @type {!Object} */ var x;" +
        "x = fn(new C());",
        "assignment\n" +
        "found   : number\n" +
        "required: Object");
  }

  @Test
  public void testTemplateType17() {
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "C.prototype.foo = 1;\n" +
        "/**\n" +
        " * @param {{foo:T}} p\n" +
        " * @return {T} \n" +
        " * @template T\n" +
        " */\n" +
        "function fn(p) { return p.foo; }\n" +
        "/** @type {!Object} */ var x;" +
        "x = fn(new C());",
        "assignment\n" +
        "found   : number\n" +
        "required: Object");
  }

  @Test
  public void testTemplateType18() {
    // Until template types can be restricted to exclude undefined, they
    // are always optional.
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "C.prototype.foo = 1;\n" +
        "/**\n" +
        " * @param {{foo:T}} p\n" +
        " * @return {T} \n" +
        " * @template T\n" +
        " */\n" +
        "function fn(p) { return p.foo; }\n" +
        "/** @type {!Object} */ var x;" +
        "x = fn({});");
  }

  @Test
  public void testTemplateType19() {
    testTypes(
        "/**\n" +
        " * @param {T} t\n" +
        " * @param {U} u\n" +
        " * @return {{t:T, u:U}} \n" +
        " * @template T,U\n" +
        " */\n" +
        "function fn(t, u) { return {t:t, u:u}; }\n" +
        "/** @type {null} */ var x = fn(1, 'str');",
        "initializing variable\n" +
        "found   : {t: number, u: string}\n" +
        "required: null");
  }

  @Test
  public void testTemplateType20() {
    // "this" types is inferred when the parameters are declared.
    testTypes(
        "/** @constructor */ function C() {\n" +
        "  /** @type {void} */ this.x;\n" +
        "}\n" +
        "/**\n" +
        "* @param {T} x\n" +
        "* @param {function(this:T, ...)} y\n" +
        "* @template T\n" +
        "*/\n" +
        "function f(x, y) {}\n" +
        "f(new C, /** @param {number} a */ function(a) {this.x = a;});",
        "assignment to property x of C\n" +
        "found   : number\n" +
        "required: undefined");
  }

  @Test
  public void testTemplateType21() {
    // "this" types is inferred when the parameters are declared.
    testTypes(
        "/** @interface @template T */ function A() {}\n" +
        "/** @constructor @implements {A<Foo>} */\n" +
        "function Foo() {}\n" +
        "/** @constructor @implements {A<Bar>} */\n" +
        "function Bar() {}\n" +
        "/** @type {!Foo} */\n" +
        "var x = new Bar();\n",
        "initializing variable\n" +
        "found   : Bar\n" +
        "required: Foo");
  }

  @Test
  public void testTemplateType22() {
    // "this" types is inferred when the parameters are declared.
    testTypes(
        "/** @interface @template T */ function A() {}\n" +
        "/** @interface @template T */ function B() {}\n" +
        "/** @constructor @implements {A<Foo>} */\n" +
        "function Foo() {}\n" +
        "/** @constructor @implements {B<Foo>} */\n" +
        "function Bar() {}\n" +
        "/** @constructor @implements {B<Foo>} */\n" +
        "function Qux() {}\n" +
        "/** @type {!Qux} */\n" +
        "var x = new Bar();\n",
        "initializing variable\n" +
        "found   : Bar\n" +
        "required: Qux");
  }

  @Test
  public void testTemplateType23() {
    // "this" types is inferred when the parameters are declared.
    testTypes(
        "/** @interface @template T */ function A() {}\n" +
        "/** @constructor @implements {A<Foo>} */\n" +
        "function Foo() {}\n" +
        "/** @type {!Foo} */\n" +
        "var x = new Foo();\n");
  }

  @Test
  public void testTemplateType24() {
    // Recursive templated type definition.
    testTypes(lines(
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
            "var /** null */ n = new Foo(new Object).m().get();"),
        "initializing variable\n"
            + "found   : (Foo<Object>|null)\n"
            + "required: null");
  }

  @Test
  public void testTemplateType25() {
    // Non-nullable recursive templated type definition.
    testTypes(lines(
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
            "var /** null */ n = new Foo(new Object).m().get();"),
        "initializing variable\n"
            + "found   : Foo<Object>\n"
            + "required: null");
  }

  @Test
  public void testTemplateType26() {
    // Class hierarchies which use the same template parameter name should not be treated as
    // infinite recursion.
    testTypes(
        lines(
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
            "var /** null */ n = new Foo(new Object).getBar();"),
        "initializing variable\n" + "found   : Array<Object>\n" + "required: null");
  }

  @Test
  public void testTemplateTypeCollidesWithParameter() {
    // Function templates are in the same scope as parameters, so cannot collide.
    testTypes(
        lines(
            "/**", //
            " * @param {T} T",
            " * @template T",
            " */",
            "function f(T) {}"),
        "variable T redefined with type undefined, "
            + "original definition at [testcode]:5 with type T");
  }

  @Test
  public void testTemplateTypeForwardReference() {
    // TODO(martinprobst): the test below asserts incorrect behavior for backwards compatibility.
    testTypes(
        lines(
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
            "}"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : Foo<number>",
            "required: Foo<string>"));
  }

  @Test
  public void testTemplateTypeForwardReference_declared() {
    compiler.forwardDeclareType("Foo");
    testTypes(
        lines(
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
            "}"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : Foo<number>",
            "required: Foo<string>"));
  }

  @Test
  public void testTemplateTypeForwardReferenceFunctionWithExtra() {
    // TODO(johnlenz): report an error when forward references contain extraneous
    // type arguments.
    testTypes(
        lines(
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
            "}"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : Foo<number>",
            "required: Foo<string>"));
  }

  @Test
  public void testTemplateTypeForwardReferenceVar() {
    testTypes(
        lines(
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
            "}"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : Foo<number>",
            "required: Foo<string>"));
  }

  @Test
  public void testTemplateTypeForwardReference_declaredMissing() {
    compiler.forwardDeclareType("Foo");
    testTypes(
        lines(
            "/** @param {!Foo<DoesNotExist>} x */",
            "function f(x) {}"));
  }

  @Test
  public void testTemplateTypeForwardReference_extends() {
    compiler.forwardDeclareType("Bar");
    compiler.forwardDeclareType("Baz");
    testTypes(
        lines(
            "/** @constructor @extends {Bar<Baz>} */",
            "function Foo() {}",
            "/** @constructor */",
            "function Bar() {}"
            ));
  }

  @Test
  public void testSubtypeNotTemplated1() {
    testTypes(
        lines(
            "/** @interface @template T */ function A() {}",
            "/** @constructor @implements {A<U>} @template U */ function Foo() {}",
            "function f(/** (!Object|!Foo<string>) */ x) {",
            "  var /** null */ n = x;",
            "}"),
        "initializing variable\n"
        + "found   : Object\n"
        + "required: null");
  }

  @Test
  public void testSubtypeNotTemplated2() {
    testTypes(
        lines(
            "/** @interface @template T */ function A() {}",
            "/** @constructor @implements {A<U>} @template U */ function Foo() {}",
            "function f(/** (!Object|!Foo) */ x) {",
            "  var /** null */ n = x;",
            "}"),
        "initializing variable\n"
        + "found   : Object\n"
        + "required: null");
  }

  @Test
  public void testTemplateTypeWithUnresolvedType() {
    testClosureTypes(
        "var goog = {};\n" +
        "goog.addDependency = function(a,b,c){};\n" +
        "goog.addDependency('a.js', ['Color'], []);\n" +

        "/** @interface @template T */ function C() {}\n" +
        "/** @return {!Color} */ C.prototype.method;\n" +

        "/** @constructor @implements {C} */ function D() {}\n" +
        "/** @override */ D.prototype.method = function() {};", null);  // no warning expected.
  }

  @Test
  public void testTemplateTypeWithTypeDef1a() {
    testTypes(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " * @param {T} x\n" +
        " */\n" +
        "function Generic(x) {}\n" +
        "\n" +
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "" +
        "/** @typedef {!Foo} */\n" +
        "var Bar;\n" +
        "" +
        "/** @type {Generic<!Foo>} */ var x;\n" +
        "/** @type {Generic<!Bar>} */ var y;\n" +
        "" +
        "x = y;\n" + // no warning
        "/** @type {null} */ var z1 = y;\n" +
        "",
        "initializing variable\n" +
        "found   : (Generic<Foo>|null)\n" +
        "required: null");
  }

  @Test
  public void testTemplateTypeWithTypeDef1b() {
    testTypes(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " * @param {T} x\n" +
        " */\n" +
        "function Generic(x) {}\n" +
        "\n" +
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "" +
        "/** @typedef {!Foo} */\n" +
        "var Bar;\n" +
        "" +
        "/** @type {Generic<!Foo>} */ var x;\n" +
        "/** @type {Generic<!Bar>} */ var y;\n" +
        "" +
        "y = x;\n" + // no warning.
        "/** @type {null} */ var z1 = x;\n" +
        "",
        "initializing variable\n" +
        "found   : (Generic<Foo>|null)\n" +
        "required: null");
  }

  @Test
  public void testTemplateTypeWithTypeDef2a() {
    testTypes(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " * @param {T} x\n" +
        " */\n" +
        "function Generic(x) {}\n" +
        "\n" +
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "\n" +
        "/** @typedef {!Foo} */\n" +
        "var Bar;\n" +
        "\n" +
        "function f(/** Generic<!Bar> */ x) {}\n" +
        "/** @type {Generic<!Foo>} */ var x;\n" +
        "f(x);\n");  // no warning expected.
  }

  @Test
  public void testTemplateTypeWithTypeDef2b() {
    testTypes(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " * @param {T} x\n" +
        " */\n" +
        "function Generic(x) {}\n" +
        "\n" +
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "\n" +
        "/** @typedef {!Foo} */\n" +
        "var Bar;\n" +
        "\n" +
        "function f(/** Generic<!Bar> */ x) {}\n" +
        "/** @type {Generic<!Bar>} */ var x;\n" +
        "f(x);\n");  // no warning expected.
  }

  @Test
  public void testTemplateTypeWithTypeDef2c() {
    testTypes(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " * @param {T} x\n" +
        " */\n" +
        "function Generic(x) {}\n" +
        "\n" +
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "\n" +
        "/** @typedef {!Foo} */\n" +
        "var Bar;\n" +
        "\n" +
        "function f(/** Generic<!Foo> */ x) {}\n" +
        "/** @type {Generic<!Foo>} */ var x;\n" +
        "f(x);\n");  // no warning expected.
  }

  @Test
  public void testTemplateTypeWithTypeDef2d() {
    testTypes(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " * @param {T} x\n" +
        " */\n" +
        "function Generic(x) {}\n" +
        "\n" +
        "/** @constructor */\n" +
        "function Foo() {}\n" +
        "\n" +
        "/** @typedef {!Foo} */\n" +
        "var Bar;\n" +
        "\n" +
        "function f(/** Generic<!Foo> */ x) {}\n" +
        "/** @type {Generic<!Bar>} */ var x;\n" +
        "f(x);\n");  // no warning expected.
  }

  @Test
  public void testTemplatedFunctionInUnion1() {
    testTypes(
        "/**\n" +
        "* @param {T} x\n" +
        "* @param {function(this:T, ...)|{fn:Function}} z\n" +
        "* @template T\n" +
        "*/\n" +
        "function f(x, z) {}\n" +
        "f([], function() { /** @type {string} */ var x = this });",
        "initializing variable\n" +
        "found   : Array\n" +
        "required: string");
  }

  @Test
  public void testTemplateTypeRecursion1() {
    testTypes(
        "/** @typedef {{a: D2}} */\n" +
        "var D1;\n" +
        "\n" +
        "/** @typedef {{b: D1}} */\n" +
        "var D2;\n" +
        "\n" +
        "fn(x);\n" +
        "\n" +
        "\n" +
        "/**\n" +
        " * @param {!D1} s\n" +
        " * @template T\n" +
        " */\n" +
        "var fn = function(s) {};"
        );
  }

  @Test
  public void testTemplateTypeRecursion2() {
    testTypes(
        "/** @typedef {{a: D2}} */\n" +
        "var D1;\n" +
        "\n" +
        "/** @typedef {{b: D1}} */\n" +
        "var D2;\n" +
        "\n" +
        "/** @type {D1} */ var x;" +
        "fn(x);\n" +
        "\n" +
        "\n" +
        "/**\n" +
        " * @param {!D1} s\n" +
        " * @template T\n" +
        " */\n" +
        "var fn = function(s) {};"
        );
  }

  @Test
  public void testTemplateTypeRecursion3() {
    testTypes(
        "/** @typedef {{a: function(D2)}} */\n" +
        "var D1;\n" +
        "\n" +
        "/** @typedef {{b: D1}} */\n" +
        "var D2;\n" +
        "\n" +
        "/** @type {D1} */ var x;" +
        "fn(x);\n" +
        "\n" +
        "\n" +
        "/**\n" +
        " * @param {!D1} s\n" +
        " * @template T\n" +
        " */\n" +
        "var fn = function(s) {};"
        );
  }

  public void disable_testBadTemplateType4() {
    // TODO(johnlenz): Add a check for useless of template types.
    // Unless there are at least two references to a Template type in
    // a definition it isn't useful.
    testTypes(
        "/**\n" +
        "* @template T\n" +
        "*/\n" +
        "function f() {}\n" +
        "f();",
        FunctionTypeBuilder.TEMPLATE_TYPE_EXPECTED.format());
  }

  public void disable_testBadTemplateType5() {
    // TODO(johnlenz): Add a check for useless of template types.
    // Unless there are at least two references to a Template type in
    // a definition it isn't useful.
    testTypes(
        "/**\n" +
        "* @template T\n" +
        "* @return {T}\n" +
        "*/\n" +
        "function f() {}\n" +
        "f();",
        FunctionTypeBuilder.TEMPLATE_TYPE_EXPECTED.format());
  }

  public void disable_testFunctionLiteralUndefinedThisArgument() {
    // TODO(johnlenz): this was a weird error.  We should add a general
    // restriction on what is accepted for T. Something like:
    // "@template T of {Object|string}" or some such.
    testTypes(""
        + "/**\n"
        + " * @param {function(this:T, ...)?} fn\n"
        + " * @param {?T} opt_obj\n"
        + " * @template T\n"
        + " */\n"
        + "function baz(fn, opt_obj) {}\n"
        + "baz(function() { this; });",
        "Function literal argument refers to undefined this argument");
  }

  @Test
  public void testFunctionLiteralDefinedThisArgument() {
    testTypes(""
        + "/**\n"
        + " * @param {function(this:T, ...)?} fn\n"
        + " * @param {?T} opt_obj\n"
        + " * @template T\n"
        + " */\n"
        + "function baz(fn, opt_obj) {}\n"
        + "baz(function() { this; }, {});");
  }

  @Test
  public void testFunctionLiteralDefinedThisArgument2() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        lines(
            "/** @param {string} x */ function f(x) {}",
            "/**",
            " * @param {?function(this:T, ...)} fn",
            " * @param {T=} opt_obj",
            " * @template T",
            " */",
            "function baz(fn, opt_obj) {}",
            "function g() { baz(function() { f(this.length); }, []); }"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testFunctionLiteralUnreadNullThisArgument() {
    testTypes(""
        + "/**\n"
        + " * @param {function(this:T, ...)?} fn\n"
        + " * @param {?T} opt_obj\n"
        + " * @template T\n"
        + " */\n"
        + "function baz(fn, opt_obj) {}\n"
        + "baz(function() {}, null);");
  }

  @Test
  public void testUnionTemplateThisType() {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** @return {F|Array} */ function g() { return []; }" +
        "/** @param {F} x */ function h(x) { }" +
        "/**\n" +
        "* @param {T} x\n" +
        "* @param {function(this:T, ...)} y\n" +
        "* @template T\n" +
        "*/\n" +
        "function f(x, y) {}\n" +
        "f(g(), function() { h(this); });",
        "actual parameter 1 of h does not match formal parameter\n" +
        "found   : (Array|F|null)\n" +
        "required: (F|null)");
  }

  @Test
  public void testRecordType1() {
    testTypes(
        lines(
            "/** @param {{prop: number}} x */",
            "function f(x) {}",
            "f({});"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : {prop: (number|undefined)}",
            "required: {prop: number}",
            "missing : []",
            "mismatch: [prop]"));
  }

  @Test
  public void testRecordType2() {
    testTypes(
        "/** @param {{prop: (number|undefined)}} x */" +
        "function f(x) {}" +
        "f({});");
  }

  @Test
  public void testRecordType3() {
    testTypes(
        lines(
            "/** @param {{prop: number}} x */",
            "function f(x) {}",
            "f({prop: 'x'});"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : {prop: (number|string)}",
            "required: {prop: number}",
            "missing : []",
            "mismatch: [prop]"));
  }

  @Test
  public void testRecordType4() {
    // Notice that we do not do flow-based inference on the object type:
    // We don't try to prove that x.prop may not be string until x
    // gets passed to g.
    testClosureTypesMultipleWarnings(
        "/** @param {{prop: (number|undefined)}} x */" +
        "function f(x) {}" +
        "/** @param {{prop: (string|undefined)}} x */" +
        "function g(x) {}" +
        "var x = {}; f(x); g(x);",
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
    testTypes(
        "/** @param {{prop: (number|undefined)}} x */" +
        "function f(x) {}" +
        "/** @param {{otherProp: (string|undefined)}} x */" +
        "function g(x) {}" +
        "var x = {}; f(x); g(x);");
  }

  @Test
  public void testRecordType6() {
    testTypes(
        "/** @return {{prop: (number|undefined)}} x */" +
        "function f() { return {}; }");
  }

  @Test
  public void testRecordType7() {
    testTypes(
        "/** @return {{prop: (number|undefined)}} x */" +
        "function f() { var x = {}; g(x); return x; }" +
        "/** @param {number} x */" +
        "function g(x) {}",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : {prop: (number|undefined)}\n" +
        "required: number");
  }

  @Test
  public void testRecordType8() {
    testTypes(
        "/** @return {{prop: (number|string)}} x */" +
        "function f() { var x = {prop: 3}; g(x.prop); return x; }" +
        "/** @param {string} x */" +
        "function g(x) {}",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testDuplicateRecordFields1() {
    testTypes("/**"
         + "* @param {{x:string, x:number}} a"
         + "*/"
         + "function f(a) {};",
         "Bad type annotation. Duplicate record field x." + BAD_TYPE_WIKI_LINK);
  }

  @Test
  public void testDuplicateRecordFields2() {
    testTypes("/**"
         + "* @param {{name:string,number:x,number:y}} a"
         + " */"
         + "function f(a) {};",
         new String[] {"Bad type annotation. Unknown type x",
           "Bad type annotation. Duplicate record field number." + BAD_TYPE_WIKI_LINK});
  }

  @Test
  public void testMultipleExtendsInterface1() {
    testTypes("/** @interface */ function base1() {}\n"
        + "/** @interface */ function base2() {}\n"
        + "/** @interface\n"
        + "* @extends {base1}\n"
        + "* @extends {base2}\n"
        + "*/\n"
        + "function derived() {}");
  }

  @Test
  public void testMultipleExtendsInterface2() {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @interface */function Int1() {};" +
        "Int0.prototype.foo = function() {};" +
        "/** @interface \n @extends {Int0} \n @extends {Int1} */" +
        "function Int2() {};" +
        "/** @constructor\n @implements {Int2} */function Foo() {};",
        "property foo on interface Int0 is not implemented by type Foo");
  }

  @Test
  public void testMultipleExtendsInterface3() {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @interface */function Int1() {};" +
        "Int1.prototype.foo = function() {};" +
        "/** @interface \n @extends {Int0} \n @extends {Int1} */" +
        "function Int2() {};" +
        "/** @constructor\n @implements {Int2} */function Foo() {};",
        "property foo on interface Int1 is not implemented by type Foo");
  }

  @Test
  public void testMultipleExtendsInterface4() {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @interface */function Int1() {};" +
        "/** @interface \n @extends {Int0} \n @extends {Int1} \n" +
        " @extends {number} */" +
        "function Int2() {};" +
        "/** @constructor\n @implements {Int2} */function Foo() {};",
        "Int2 @extends non-object type number");
  }

  @Test
  public void testMultipleExtendsInterface5() {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @constructor */function Int1() {};" +
        "/** @return {string} x */" +
        "/** @interface \n @extends {Int0} \n @extends {Int1} */" +
        "function Int2() {};",
        "Int2 cannot extend this type; interfaces can only extend interfaces");
  }

  @Test
  public void testMultipleExtendsInterface6() {
    testTypes(
        "/** @interface */function Super1() {};" +
        "/** @interface */function Super2() {};" +
        "/** @param {number} bar */Super2.prototype.foo = function(bar) {};" +
        "/** @interface\n @extends {Super1}\n " +
        "@extends {Super2} */function Sub() {};" +
        "/** @override\n @param {string} bar */Sub.prototype.foo =\n" +
        "function(bar) {};",
        "mismatch of the foo property type and the type of the property it " +
        "overrides from superclass Super2\n" +
        "original: function(this:Super2, number): undefined\n" +
        "override: function(this:Sub, string): undefined");
  }

  @Test
  public void testMultipleExtendsInterfaceAssignment() {
    testTypes("/** @interface */var I1 = function() {};\n" +
        "/** @interface */ var I2 = function() {}\n" +
        "/** @interface\n@extends {I1}\n@extends {I2}*/" +
        "var I3 = function() {};\n" +
        "/** @constructor\n@implements {I3}*/var T = function() {};\n" +
        "var t = new T();\n" +
         "/** @type {I1} */var i1 = t;\n" +
         "/** @type {I2} */var i2 = t;\n" +
         "/** @type {I3} */var i3 = t;\n" +
         "i1 = i3;\n" +
         "i2 = i3;\n");
  }

  @Test
  public void testMultipleExtendsInterfaceParamPass() {
    testTypes(lines(
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
        "foo(t,t,t)"));
  }

  @Test
  public void testBadMultipleExtendsClass() {
    testTypes("/** @constructor */ function base1() {}\n"
        + "/** @constructor */ function base2() {}\n"
        + "/** @constructor\n"
        + "* @extends {base1}\n"
        + "* @extends {base2}\n"
        + "*/\n"
        + "function derived() {}",
        "Bad type annotation. type annotation incompatible with other annotations."
            + BAD_TYPE_WIKI_LINK);
  }

  @Test
  public void testInterfaceExtendsResolution() {
    testTypes("/** @interface \n @extends {A} */ function B() {};\n" +
        "/** @constructor \n @implements {B} */ function C() {};\n" +
        "/** @interface */ function A() {};");
  }

  @Test
  public void testPropertyCanBeDefinedInObject() {
    // This test is specifically checking loose property check behavior.
    disableStrictMissingPropertyChecks();
    testTypes(
        lines(
            "/** @interface */ function I() {};",
            "I.prototype.bar = function() {};",
            "/** @type {Object} */ var foo;",
            "foo.bar();"));
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility1() {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @interface */function Int1() {};" +
        "/** @type {number} */" +
        "Int0.prototype.foo;" +
        "/** @type {string} */" +
        "Int1.prototype.foo;" +
        "/** @interface \n @extends {Int0} \n @extends {Int1} */" +
        "function Int2() {};",
        "Interface Int2 has a property foo with incompatible types in its " +
        "super interfaces Int0 and Int1");
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility2() {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @interface */function Int1() {};" +
        "/** @interface */function Int2() {};" +
        "/** @type {number} */" +
        "Int0.prototype.foo;" +
        "/** @type {string} */" +
        "Int1.prototype.foo;" +
        "/** @type {Object} */" +
        "Int2.prototype.foo;" +
        "/** @interface \n @extends {Int0} \n @extends {Int1} \n" +
        "@extends {Int2}*/" +
        "function Int3() {};",
        new String[] {
            "Interface Int3 has a property foo with incompatible types in " +
            "its super interfaces Int0 and Int1",
            "Interface Int3 has a property foo with incompatible types in " +
            "its super interfaces Int1 and Int2"
        });
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility3() {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @interface */function Int1() {};" +
        "/** @type {number} */" +
        "Int0.prototype.foo;" +
        "/** @type {string} */" +
        "Int1.prototype.foo;" +
        "/** @interface \n @extends {Int1} */ function Int2() {};" +
        "/** @interface \n @extends {Int0} \n @extends {Int2} */" +
        "function Int3() {};",
        "Interface Int3 has a property foo with incompatible types in its " +
        "super interfaces Int0 and Int1");
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility4() {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @interface \n @extends {Int0} */ function Int1() {};" +
        "/** @type {number} */" +
        "Int0.prototype.foo;" +
        "/** @interface */function Int2() {};" +
        "/** @interface \n @extends {Int2} */ function Int3() {};" +
        "/** @type {string} */" +
        "Int2.prototype.foo;" +
        "/** @interface \n @extends {Int1} \n @extends {Int3} */" +
        "function Int4() {};",
        "Interface Int4 has a property foo with incompatible types in its " +
        "super interfaces Int0 and Int2");
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility5() {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @interface */function Int1() {};" +
        "/** @type {number} */" +
        "Int0.prototype.foo;" +
        "/** @type {string} */" +
        "Int1.prototype.foo;" +
        "/** @interface \n @extends {Int1} */ function Int2() {};" +
        "/** @interface \n @extends {Int0} \n @extends {Int2} */" +
        "function Int3() {};" +
        "/** @interface */function Int4() {};" +
        "/** @type {number} */" +
        "Int4.prototype.foo;" +
        "/** @interface \n @extends {Int3} \n @extends {Int4} */" +
        "function Int5() {};",
        new String[] {
            "Interface Int3 has a property foo with incompatible types in its" +
            " super interfaces Int0 and Int1",
            "Interface Int5 has a property foo with incompatible types in its" +
            " super interfaces Int1 and Int4"});
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility6() {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @interface */function Int1() {};" +
        "/** @type {number} */" +
        "Int0.prototype.foo;" +
        "/** @type {string} */" +
        "Int1.prototype.foo;" +
        "/** @interface \n @extends {Int1} */ function Int2() {};" +
        "/** @interface \n @extends {Int0} \n @extends {Int2} */" +
        "function Int3() {};" +
        "/** @interface */function Int4() {};" +
        "/** @type {string} */" +
        "Int4.prototype.foo;" +
        "/** @interface \n @extends {Int3} \n @extends {Int4} */" +
        "function Int5() {};",
        "Interface Int3 has a property foo with incompatible types in its" +
        " super interfaces Int0 and Int1");
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility7() {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @interface */function Int1() {};" +
        "/** @type {number} */" +
        "Int0.prototype.foo;" +
        "/** @type {string} */" +
        "Int1.prototype.foo;" +
        "/** @interface \n @extends {Int1} */ function Int2() {};" +
        "/** @interface \n @extends {Int0} \n @extends {Int2} */" +
        "function Int3() {};" +
        "/** @interface */function Int4() {};" +
        "/** @type {Object} */" +
        "Int4.prototype.foo;" +
        "/** @interface \n @extends {Int3} \n @extends {Int4} */" +
        "function Int5() {};",
        new String[] {
            "Interface Int3 has a property foo with incompatible types in its" +
            " super interfaces Int0 and Int1",
            "Interface Int5 has a property foo with incompatible types in its" +
            " super interfaces Int1 and Int4"});
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility8() {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @interface */function Int1() {};" +
        "/** @type {number} */" +
        "Int0.prototype.foo;" +
        "/** @type {string} */" +
        "Int1.prototype.bar;" +
        "/** @interface \n @extends {Int1} */ function Int2() {};" +
        "/** @interface \n @extends {Int0} \n @extends {Int2} */" +
        "function Int3() {};" +
        "/** @interface */function Int4() {};" +
        "/** @type {Object} */" +
        "Int4.prototype.foo;" +
        "/** @type {Null} */" +
        "Int4.prototype.bar;" +
        "/** @interface \n @extends {Int3} \n @extends {Int4} */" +
        "function Int5() {};",
        new String[] {
            "Interface Int5 has a property bar with incompatible types in its" +
            " super interfaces Int1 and Int4",
            "Interface Int5 has a property foo with incompatible types in its" +
            " super interfaces Int0 and Int4"});
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibility9() {
    testTypes(
        "/** @interface\n * @template T */function Int0() {};" +
        "/** @interface\n * @template T */function Int1() {};" +
        "/** @type {T} */" +
        "Int0.prototype.foo;" +
        "/** @type {T} */" +
        "Int1.prototype.foo;" +
        "/** @interface \n @extends {Int0<number>} \n @extends {Int1<string>} */" +
        "function Int2() {};",
        "Interface Int2 has a property foo with incompatible types in its " +
        "super interfaces Int0<number> and Int1<string>");
  }

  @Test
  public void testExtendedInterfacePropertiesCompatibilityNoError() {
    testTypes(""
        + "/** @interface */function Int0() {};"
        + "/** @interface */function Int1() {};"
        + "/** @param {number} x */"
        + "Int0.prototype.foo;"
        + "/** @param {number} x */"
        + "Int1.prototype.foo;"
        + "/** @interface \n * @extends {Int0} \n * @extends {Int1} */"
        + "function Int2() {};");
  }

  @Test
  public void testGenerics1a() {
    String fnDecl = "/** \n" +
        " * @param {T} x \n" +
        " * @param {function(T):T} y \n" +
        " * @template T\n" +
        " */ \n" +
        "function f(x,y) { return y(x); }\n";

    testTypes(
        fnDecl +
        "/** @type {string} */" +
        "var out;" +
        "/** @type {string} */" +
        "var result = f('hi', function(x){ out = x; return x; });");
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

    testTypes(
        fnDecl
            + "/** @type {string} */"
            + "var out;"
            + "var result = f(0, function(x){ out = x; return x; });",
        "assignment\n" + "found   : number\n" + "required: string");
  }

  @Test
  public void testFilter0() {
    testTypes(
        "/**\n" +
        " * @param {T} arr\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "var filter = function(arr){};\n" +

        "/** @type {!Array<string>} */" +
        "var arr;\n" +
        "/** @type {!Array<string>} */" +
        "var result = filter(arr);");
  }

  @Test
  public void testFilter1() {
    testTypes(
        "/**\n" +
        " * @param {!Array<T>} arr\n" +
        " * @return {!Array<T>}\n" +
        " * @template T\n" +
        " */\n" +
        "var filter = function(arr){};\n" +

        "/** @type {!Array<string>} */" +
        "var arr;\n" +
        "/** @type {!Array<string>} */" +
        "var result = filter(arr);");
  }

  @Test
  public void testFilter2() {
    testTypes(
        "/**\n" +
        " * @param {!Array<T>} arr\n" +
        " * @return {!Array<T>}\n" +
        " * @template T\n" +
        " */\n" +
        "var filter = function(arr){};\n" +

        "/** @type {!Array<string>} */" +
        "var arr;\n" +
        "/** @type {!Array<number>} */" +
        "var result = filter(arr);",
        "initializing variable\n" +
        "found   : Array<string>\n" +
        "required: Array<number>");
  }

  @Test
  public void testFilter3() {
    testTypes(
        "/**\n" +
        " * @param {Array<T>} arr\n" +
        " * @return {Array<T>}\n" +
        " * @template T\n" +
        " */\n" +
        "var filter = function(arr){};\n" +

        "/** @type {Array<string>} */" +
        "var arr;\n" +
        "/** @type {Array<number>} */" +
        "var result = filter(arr);",
        "initializing variable\n" +
        "found   : (Array<string>|null)\n" +
        "required: (Array<number>|null)");
  }

  @Test
  public void testBackwardsInferenceGoogArrayFilter1() {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @type {Array<string>} */" +
        "var arr;\n" +
        "/** @type {!Array<number>} */" +
        "var result = goog.array.filter(" +
        "   arr," +
        "   function(item,index,src) {return false;});",
        "initializing variable\n" +
        "found   : Array<string>\n" +
        "required: Array<number>");
  }

  @Test
  public void testBackwardsInferenceGoogArrayFilter2() {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @type {number} */" +
        "var out;" +
        "/** @type {Array<string>} */" +
        "var arr;\n" +
        "var out4 = goog.array.filter(" +
        "   arr," +
        "   function(item,index,src) {out = item; return false});",
        "assignment\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testBackwardsInferenceGoogArrayFilter3() {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @type {string} */" +
        "var out;" +
        "/** @type {Array<string>} */ var arr;\n" +
        "var result = goog.array.filter(" +
        "   arr," +
        "   function(item,index,src) {out = index;});",
        "assignment\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testBackwardsInferenceGoogArrayFilter4() {
    testClosureTypes(
        lines(
            CLOSURE_DEFS,
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
    testTypes(
        "function fn() {" +
        "  /** @type {number} */" +
        "  var out = 0;" +
        "  try {\n" +
        "    foo();\n" +
        "  } catch (/** @type {string} */ e) {\n" +
        "    out = e;" +
        "  }" +
        "}\n",
        "assignment\n" +
        "found   : string\n" +
        "required: number");
  }

  @Test
  public void testCatchExpression2() {
    testTypes(
        "function fn() {" +
        "  /** @type {number} */" +
        "  var out = 0;" +
        "  /** @type {string} */" +
        "  var e;" +
        "  try {\n" +
        "    foo();\n" +
        "  } catch (e) {\n" +
        "    out = e;" +
        "  }" +
        "}\n");
  }

  @Test
  public void testTemplatized1() {
    testTypes(
        "/** @type {!Array<string>} */" +
        "var arr1 = [];\n" +
        "/** @type {!Array<number>} */" +
        "var arr2 = [];\n" +
        "arr1 = arr2;",
        "assignment\n" +
        "found   : Array<number>\n" +
        "required: Array<string>");
  }

  @Test
  public void testTemplatized2() {
    testTypes(
        "/** @type {!Array<string>} */" +
        "var arr1 = /** @type {!Array<number>} */([]);\n",
        "initializing variable\n" +
        "found   : Array<number>\n" +
        "required: Array<string>");
  }

  @Test
  public void testTemplatized3() {
    testTypes(
        "/** @type {Array<string>} */" +
        "var arr1 = /** @type {!Array<number>} */([]);\n",
        "initializing variable\n" +
        "found   : Array<number>\n" +
        "required: (Array<string>|null)");
  }

  @Test
  public void testTemplatized4() {
    testTypes(
        "/** @type {Array<string>} */" +
        "var arr1 = [];\n" +
        "/** @type {Array<number>} */" +
        "var arr2 = arr1;\n",
        "initializing variable\n" +
        "found   : (Array<string>|null)\n" +
        "required: (Array<number>|null)");
  }

  @Test
  public void testTemplatized5() {
    testTypes(
        "/**\n" +
        " * @param {Object<T>} obj\n" +
        " * @return {boolean|undefined}\n" +
        " * @template T\n" +
        " */\n" +
        "var some = function(obj) {" +
        "  for (var key in obj) if (obj[key]) return true;" +
        "};" +
        "/** @return {!Array} */ function f() { return []; }" +
        "/** @return {!Array<string>} */ function g() { return []; }" +
        "some(f());\n" +
        "some(g());\n");
  }

  @Test
  public void testTemplatized6() {
    testTypes(
        "/** @interface */ function I(){}\n" +
        "/** @param {T} a\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        "*/\n" +
        "I.prototype.method;\n" +
        "" +
        "/** @constructor \n" +
        " * @implements {I}\n" +
        " */ function C(){}\n" +
        "/** @override*/ C.prototype.method = function(a) {}\n" +
        "" +
        "/** @type {null} */ var some = new C().method('str');",
        "initializing variable\n" +
        "found   : string\n" +
        "required: null");
  }

  @Test
  public void testTemplatized7() {
    testTypes(
        "/** @interface\n" +
        " *  @template Q\n " +
        " */ function I(){}\n" +

        "/** @param {T} a\n" +
        " * @return {T|Q}\n" +
        " * @template T\n" +
        "*/\n" +
        "I.prototype.method;\n" +

        "/** @constructor \n" +
        " * @implements {I<number>}\n" +
        " */ function C(){}\n" +
        "/** @override*/ C.prototype.method = function(a) {}\n" +

        "/** @type {null} */ var some = new C().method('str');",

        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: null");
  }

  public void disable_testTemplatized8() {
    // TODO(johnlenz): this should generate a warning but does not.
    testTypes(
        "/** @interface\n" +
        " *  @template Q\n " +
        " */ function I(){}\n" +

        "/** @param {T} a\n" +
        " * @return {T|Q}\n" +
        " * @template T\n" +
        "*/\n" +
        "I.prototype.method;\n" +

        "/** @constructor \n" +
        " *  @implements {I<R>}\n" +
        " *  @template R\n " +
        " */ function C(){}\n" +
        "/** @override*/ C.prototype.method = function(a) {}\n" +

        "/** @type {C<number>} var x = new C();" +
        "/** @type {null} */ var some = x.method('str');",

        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: null");
  }

  @Test
  public void testTemplatized9() {
    testTypes(
        "/** @interface\n" +
        " *  @template Q\n " +
        " */ function I(){}\n" +

        "/** @param {T} a\n" +
        " * @return {T|Q}\n" +
        " * @template T\n" +
        "*/\n" +
        "I.prototype.method;\n" +

        "/** @constructor \n" +
        " *  @param {R} a\n" +
        " *  @implements {I<R>}\n" +
        " *  @template R\n " +
        " */ function C(a){}\n" +
        "/** @override*/ C.prototype.method = function(a) {}\n" +

        "/** @type {null} */ var some = new C(1).method('str');",

        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: null");
  }

  @Test
  public void testTemplatized10() {
    testTypes(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "function Parent() {};\n" +
        "\n" +
        "/** @param {T} x */\n" +
        "Parent.prototype.method = function(x) {};\n" +
        "\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Parent<string>}\n" +
        " */\n" +
        "function Child() {};\n" +
        "Child.prototype = new Parent();\n" +
        "\n" +
        "(new Child()).method(123); \n",

        "actual parameter 1 of Parent.prototype.method does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  @Test
  public void testTemplatized11() {
    testTypes(
        "/** \n" +
        " * @template T\n" +
        " * @constructor\n" +
        " */\n" +
        "function C() {}\n" +
        "\n" +
        "/**\n" +
        " * @param {T|K} a\n" +
        " * @return {T}\n" +
        " * @template K\n" +
        " */\n" +
        "C.prototype.method = function(a) {};\n" +
        "\n" +
        // method returns "?"
        "/** @type {void} */ var x = new C().method(1);");
  }

  @Test
  public void testIssue1058() {
    testTypes(
        "/**\n" +
        "  * @constructor\n" +
        "  * @template CLASS\n" +
        "  */\n" +
        "var Class = function() {};\n" +
        "\n" +
        "/**\n" +
        "  * @param {function(CLASS):CLASS} a\n" +
        "  * @template T\n" +
        "  */\n" +
        "Class.prototype.foo = function(a) {\n" +
        "  return 'string';\n" +
        "};\n" +
        "\n" +
        "/** @param {number} a\n" +
        "  * @return {string} */\n" +
        "var a = function(a) { return '' };\n" +
        "\n" +
        "new Class().foo(a);");
  }

  @Test
  public void testDeterminacyIssue() {
    testTypes(
        "(function() {\n" +
        "    /** @constructor */\n" +
        "    var ImageProxy = function() {};\n" +
        "    /** @constructor */\n" +
        "    var FeedReader = function() {};\n" +
        "    /** @type {ImageProxy} */\n" +
        "    FeedReader.x = new ImageProxy();\n" +
        "})();");
  }

  @Test
  public void testUnknownTypeReport() {
    enableReportUnknownTypes();
    testTypes("function id(x) { return x; }",
        "could not determine the type of this expression");
  }

  @Test
  public void testUnknownForIn() {
    enableReportUnknownTypes();
    testTypes("var x = {'a':1}; var y; \n for(\ny\n in x) {}");
  }

  @Test
  public void testUnknownTypeDisabledByDefault() {
    testTypes("function id(x) { return x; }");
  }

  @Test
  public void testTemplatizedTypeSubtypes2() {
    JSType arrayOfNumber = createTemplatizedType(getNativeArrayType(), getNativeNumberType());
    JSType arrayOfString = createTemplatizedType(getNativeArrayType(), getNativeStringType());
    assertThat(arrayOfString.isSubtypeOf(createUnionType(arrayOfNumber, getNativeNullVoidType())))
        .isFalse();
  }

  @Test
  public void testNonexistentPropertyAccessOnStruct() {
    testTypes(
        "/**\n" +
        " * @constructor\n" +
        " * @struct\n" +
        " */\n" +
        "var A = function() {};\n" +
        "/** @param {A} a */\n" +
        "function foo(a) {\n" +
        "  if (a.bar) { a.bar(); }\n" +
        "}",
        "Property bar never defined on A");
  }

  @Test
  public void testNonexistentPropertyAccessOnStructOrObject() {
    disableStrictMissingPropertyChecks();
    testTypes(
        "/**\n" +
        " * @constructor\n" +
        " * @struct\n" +
        " */\n" +
        "var A = function() {};\n" +
        "/** @param {A|Object} a */\n" +
        "function foo(a) {\n" +
        "  if (a.bar) { a.bar(); }\n" +
        "}");
  }

  @Test
  public void testNonexistentPropertyAccessOnExternStruct() {
    testTypesWithExterns(
        "/**\n" +
        " * @constructor\n" +
        " * @struct\n" +
        " */\n" +
        "var A = function() {};",
        "/** @param {A} a */\n" +
        "function foo(a) {\n" +
        "  if (a.bar) { a.bar(); }\n" +
        "}",
        "Property bar never defined on A", false);
  }

  @Test
  public void testNonexistentPropertyAccessStructSubtype() {
    testTypes(
        "/**\n" +
        " * @constructor\n" +
        " * @struct\n" +
        " */\n" +
        "var A = function() {};" +
        "" +
        "/**\n" +
        " * @constructor\n" +
        " * @struct\n" +
        " * @extends {A}\n" +
        " */\n" +
        "var B = function() { this.bar = function(){}; };" +
        "" +
        "/** @param {A} a */\n" +
        "function foo(a) {\n" +
        "  if (a.bar) { a.bar(); }\n" +
        "}",
        "Property bar never defined on A", false);
  }

  @Test
  public void testNonexistentPropertyAccessStructInterfaceSubtype() {
    testTypes(lines(
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
        "}"),
        "Property bar never defined on A", false);
  }

  @Test
  public void testNonexistentPropertyAccessStructRecordSubtype() {
    disableStrictMissingPropertyChecks();

    testTypes(
        lines(
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
            "}"),
        "Property bar never defined on A",
        false);
  }

  @Test
  public void testNonexistentPropertyAccessStructSubtype2() {
    testTypes(
        "/**\n" +
        " * @constructor\n" +
        " * @struct\n" +
        " */\n" +
        "function Foo() {\n" +
        "  this.x = 123;\n" +
        "}\n" +
        "var objlit = /** @struct */ { y: 234 };\n" +
        "Foo.prototype = objlit;\n" +
        "var n = objlit.x;\n",
        "Property x never defined on Foo.prototype", false);
  }

  @Test
  public void testIssue1024a() {
    // This test is specifically checking loose property check behavior.
    disableStrictMissingPropertyChecks();
    testTypes(
        lines(
            "/** @param {Object} a */",
            "function f(a) {",
            "  a.prototype = '__proto'",
            "}",
            "/** @param {Object} b",
            " *  @return {!Object}",
            " */",
            "function g(b) {",
            "  return b.prototype",
            "}"));
  }

  @Test
  public void testIssue1024b() {
    testTypes(
        lines(
            "/** @param {Object} a */",
            "function f(a) {",
            "  a.prototype = {foo:3};",
            "}",
            "/** @param {Object} b",
            " */",
            "function g(b) {",
            "  b.prototype = function(){};",
            "}"));
  }

  @Test
  public void testBug12722936() {
    // Verify we don't use a weaker type when a
    // stronger type is known for a slot.
    testTypes(
        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "function X() {}\n" +
        "/** @constructor */ function C() {\n" +
        "  /** @type {!X<boolean>}*/\n" +
        "  this.a = new X();\n" +
        "  /** @type {null} */ var x = this.a;\n" +
        "};\n" +
        "\n",
        "initializing variable\n" +
        "found   : X<boolean>\n" +
        "required: null", false);
  }

  @Test
  public void testModuleReferenceNotAllowed() {
    testTypes(
        "/** @param {./Foo} z */ function f(z) {}",
        "Bad type annotation. Unknown type ./Foo");
  }

  @Test
  public void testCheckObjectKeysBadKey1() {
    testTypes("/** @type {!Object<!Object, number>} */ var k;",
        TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysBadKey2() {
  testTypes("/** @type {!Object<function(), number>} */ var k;",
      TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysBadKey3() {
    testTypes("/** @type {!Object<!Array<!Object>, number>} */ var k;",
        TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysBadKey4() {
    testTypes("/** @type {!Object<*, number>} */ var k;",
        TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysBadKey5() {
    testTypes("/** @type {(string|Object<Object, number>)} */ var k;",
        TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysBadKey6() {
    testTypes("/** @type {!Object<number, !Object<Object, number>>} */ var k;",
        TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysBadKey7() {
    testTypes(
        "/** @constructor */\n" +
        "var MyClass = function() {};\n" +
        "/** @type {!Object<MyClass, number>} */\n" +
        "var k;",
        TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysBadKey8() {
    testTypes(
        "/** @enum{!Object} */\n" +
        "var Enum = {};\n" +
        "/** @type {!Object<Enum, number>} */\n" +
        "var k;",
        TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysBadKey9() {
    testTypes("/** @type {function(!Object<!Object, number>)} */ var k;",
        TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysBadKey10() {
    testTypes("/** @type {function(): !Object<!Object, number>} */ var k;",
        TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysBadKey11() {
    testTypes(
        "/** @constructor */\n" +
        "function X() {}\n" +
        "/** @constructor @extends {X} */\n" +
        "function X2() {}\n" +
        "/** @enum {!X} */\n" +
        "var XE = {A:new X};\n" +
        "/** @type {Object<(!XE|!X2), string>} */\n" +
        "var Y = {};",
        TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysVariousTags1() {
    testTypes("/** @type {!Object<!Object, number>} */ var k;",
        TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysVariousTags2() {
    testTypes("/** @param {!Object<!Object, number>} a */ var f = function(a) {};",
        TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysVariousTags3() {
    testTypes("/** @return {!Object<!Object, number>} */ var f = function() {return {}};",
        TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysVariousTags4() {
    testTypes("/** @typedef {!Object<!Object, number>} */ var MyType;",
        TypeCheck.NON_STRINGIFIABLE_OBJECT_KEY);
  }

  @Test
  public void testCheckObjectKeysGoodKey1() {
    testTypes("/** @type {!Object<number, number>} */ var k;");
  }

  @Test
  public void testCheckObjectKeysGoodKey2() {
    testTypes("/** @type {!Object<string, number>} */ var k;");
  }

  @Test
  public void testCheckObjectKeysGoodKey3() {
    testTypes("/** @type {!Object<boolean, number>} */ var k;");
  }

  @Test
  public void testCheckObjectKeysGoodKey4() {
    testTypes("/** @type {!Object<null, number>} */ var k;");
  }

  @Test
  public void testCheckObjectKeysGoodKey5() {
    testTypes("/** @type {!Object<undefined, number>} */ var k;");
  }

  @Test
  public void testCheckObjectKeysGoodKey6() {
    testTypes("/** @type {!Object<!Date, number>} */ var k;");
  }

  @Test
  public void testCheckObjectKeysGoodKey7() {
    testTypes("/** @type {!Object<!RegExp, number>} */ var k;");
  }

  @Test
  public void testCheckObjectKeysGoodKey8() {
    testTypes("/** @type {!Object<!Array, number>} */ var k;");
  }

  @Test
  public void testCheckObjectKeysGoodKey9() {
    testTypes("/** @type {!Object<!Array<number>, number>} */ var k;");
  }

  @Test
  public void testCheckObjectKeysGoodKey10() {
    testTypes("/** @type {!Object<?, number>} */ var k;");
  }

  @Test
  public void testCheckObjectKeysGoodKey11() {
    testTypes("/** @type {!Object<(string|number), number>} */ var k");
  }

  @Test
  public void testCheckObjectKeysGoodKey12() {
    testTypes("/** @type {!Object<Object>} */ var k;");
  }

  @Test
  public void testCheckObjectKeysGoodKey13() {
    testTypes(
        "/** @interface */\n" +
        "var MyInterface = function() {};\n" +
        "/** @type {!Object<!MyInterface, number>} */\n" +
        "var k;");
  }

  @Test
  public void testCheckObjectKeysGoodKey14() {
    testTypes(
        "/** @typedef {{a: number}} */ var MyRecord;\n" +
        "/** @type {!Object<MyRecord, number>} */ var k;");
  }

  @Test
  public void testCheckObjectKeysGoodKey15() {
    testTypes(
        "/** @enum{number} */\n" +
        "var Enum = {};\n" +
        "/** @type {!Object<Enum, number>} */\n" +
        "var k;");
  }

  @Test
  public void testCheckObjectKeysClassWithToString() {
    testTypesWithExterns(
        new TestExternsBuilder().addObject().build(),
        lines(
            "/** @constructor */",
            "var MyClass = function() {};",
            "/** @override*/",
            "MyClass.prototype.toString = function() { return ''; };",
            "/** @type {!Object<!MyClass, number>} */",
            "var k;"));
  }

  @Test
  public void testCheckObjectKeysClassInheritsToString() {
    testTypesWithExterns(
        new TestExternsBuilder().addObject().build(),
        lines(
            "/** @constructor */",
            "var Parent = function() {};",
            "/** @override */",
            "Parent.prototype.toString = function() { return ''; };",
            "/** @constructor @extends {Parent} */",
            "var Child = function() {};",
            "/** @type {!Object<!Child, number>} */",
            "var k;"));
  }

  @Test
  public void testCheckObjectKeysForEnumUsingClassWithToString() {
    testTypesWithExterns(
        new TestExternsBuilder().addObject().build(),
        lines(
            "/** @constructor */",
            "var MyClass = function() {};",
            "/** @override*/",
            "MyClass.prototype.toString = function() { return ''; };",
            "/** @enum{!MyClass} */",
            "var Enum = {};",
            "/** @type {!Object<Enum, number>} */",
            "var k;"));
  }

  @Test
  public void testBadSuperclassInheritance1() {
    testTypes(lines(
        "/** @constructor */",
        "function Foo() {}",
        "/** @type {number} */",
        "Foo.prototype.myprop = 2;",
        "",
        "/** @constructor @extends {Foo} */",
        "function Bar() {}",
        "/** @type {number} */",
        "Bar.prototype.myprop = 1;"),
        TypeCheck.HIDDEN_SUPERCLASS_PROPERTY);
  }

  @Test
  public void testBadSuperclassInheritance2() {
    testTypes(lines(
        "/** @constructor */",
        "function Foo() {}",
        "/** @type {number} */",
        "Foo.prototype.myprop = 2;",
        "",
        "/** @constructor @extends {Foo} */",
        "function Bar() {}",
        "/** @override @type {string} */",
        "Bar.prototype.myprop = 'qwer';"),
        TypeCheck.HIDDEN_SUPERCLASS_PROPERTY_MISMATCH);
  }

  // If the property has no initializer, the HIDDEN_SUPERCLASS_PROPERTY_MISMATCH warning is missed.
  @Test
  public void testBadSuperclassInheritance3() {
    testTypes(lines(
        "/** @constructor */",
        "function Foo() {}",
        "/** @type {number} */",
        "Foo.prototype.myprop = 2;",
        "",
        "/** @constructor @extends {Foo} */",
        "function Bar() {}",
        "/** @override @type {string} */",
        "Bar.prototype.myprop;"));
  }

  @Test
  public void testCheckObjectKeysWithNamedType() {
    testTypes(
        "/** @type {!Object<!PseudoId, number>} */\n" +
        "var k;\n" +

        "/** @typedef {number|string} */\n" +
        "var PseudoId;");
  }

  @Test
  public void testCheckObjectKeyRecursiveType() {
    testTypes(
        "/** @typedef {!Object<string, !Predicate>} */ var Schema;\n" +
        "/** @typedef {function(*): boolean|!Schema} */ var Predicate;\n" +
        "/** @type {!Schema} */ var k;");
  }

  @Test
  public void testDontOverrideNativeScalarTypes() {
    testTypes(
        "string = 123;\n"
        + "var /** string */ s = 123;",
        "initializing variable\n"
        + "found   : number\n"
        + "required: string");

    testTypes(
        "var string = goog.require('goog.string');\n"
        + "var /** string */ s = 123;",
        new String[] {
          "Property require never defined on goog",
          "initializing variable\n"
          + "found   : number\n"
          + "required: string"
        });
  }

  @Test
  public void testTemplateMap1() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        "function f() {\n"
        + "  /** @type {Int8Array} */\n"
        + "  var x = new Int8Array(10);\n"
        + "  /** @type {IArrayLike<string>} */\n"
        + "  var y;\n"
        + "  y = x;\n"
        + "}",
        "assignment\n"
        + "found   : (Int8Array|null)\n"
        + "required: (IArrayLike<string>|null)");
  }

  @Test
  public void testTemplateMap2() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        "function f() {\n"
        + "  /** @type {Int8Array} */\n"
        + "  var x = new Int8Array(10);\n"
        + "\n"
        + "  /** @type {IObject<number, string>} */\n"
        + "  var z;\n"
        + "  z = x;\n"
        + "}",
        "assignment\n"
        + "found   : (Int8Array|null)\n"
        + "required: (IObject<number,string>|null)");
  }

  @Test
  public void testTemplateMap3() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        "function f() {\n"
        + "  var x = new Int8Array(10);\n"
        + "\n"
        + "  /** @type {IArrayLike<string>} */\n"
        + "  var y;\n"
        + "  y = x;\n"
        + "}",
        "assignment\n"
        + "found   : Int8Array\n"
        + "required: (IArrayLike<string>|null)");
  }

  @Test
  public void testTemplateMap4() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        "function f() {\n"
        + "  var x = new Int8Array(10);\n"
        + "\n"
        + "  /** @type {IObject<number, string>} */\n"
        + "  var z;\n"
        + "  z = x;\n"
        + "}",
        "assignment\n"
        + "found   : Int8Array\n"
        + "required: (IObject<number,string>|null)");
  }

  @Test
  public void testTemplateMap5() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        "function f() {\n"
        + "  var x = new Int8Array(10);\n"
        + "  /** @type {IArrayLike<number>} */\n"
        + "  var y;\n"
        + "  y = x;\n"
        + "}");
  }

  @Test
  public void testTemplateMap6() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        "function f() {\n"
        + "  var x = new Int8Array(10);\n"
        + "  /** @type {IObject<number, number>} */\n"
        + "  var z;\n"
        + "  z = x;\n"
        + "}");
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
      + "/**\n" + " * @interface\n"
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
    testTypes(
        "/** @type {!Array<number>} */ var arr = [];\n"
        + "var /** null */ n = arr[0];\n",
        "initializing variable\n"
        + "found   : number\n"
        + "required: null");
  }

  @Test
  public void testIArrayLike1() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        "var arr = new Int8Array(7);\n"
        + "// no warning\n"
        + "arr[0] = 1;\n"
        + "arr[1] = 2;\n");
  }

  @Test
  public void testIArrayLike2() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        "var arr = new Int8Array(7);\n"
        + "// have warnings\n"
        + "arr[3] = false;\n",
        "assignment\n"
        + "found   : boolean\n"
        + "required: number");
  }

  @Test
  public void testIArrayLike3() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        "var arr = new Int8Array2(10);\n"
        + "// have warnings\n"
        + "arr[3] = false;\n",
        "assignment\n"
        + "found   : boolean\n"
        + "required: number");
  }

  @Test
  public void testIArrayLike4() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        "var arr = new Int8Array2(10);\n"
        + "// have warnings\n"
        + "arr[3] = false;\n",
        "assignment\n"
        + "found   : boolean\n"
        + "required: number");
  }

  @Test
  public void testIArrayLike5() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        "var arr = new Int8Array3(10);\n"
        + "// have warnings\n"
        + "arr[3] = false;\n",
        "assignment\n"
        + "found   : boolean\n"
        + "required: number");
  }

  @Test
  public void testIArrayLike6() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        "var arr = new Int8Array4(10);\n"
        + "// have warnings\n"
        + "arr[3] = false;\n",
        "assignment\n"
        + "found   : boolean\n"
        + "required: number");
  }

  @Test
  public void testIArrayLike7() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        "var arr5 = new BooleanArray5(10);\n"
        + "arr5[2] = true;\n"
        + "arr5[3] = \"\";",
        "assignment\n"
        + "found   : string\n"
        + "required: boolean");
  }

  @Test
  public void testIArrayLike8() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        lines(
            "var arr2 = new Int8Array(10);",
            "arr2[true] = 1;"),
        lines(
            "restricted index type",
            "found   : boolean",
            "required: number"));
  }

  @Test
  public void testIArrayLike9() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        lines(
            "var arr2 = new Int8Array2(10);",
            "arr2[true] = 1;"),
        lines(
            "restricted index type",
            "found   : boolean",
            "required: number"));
  }

  @Test
  public void testIArrayLike10() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        lines(
            "var arr2 = new Int8Array3(10);",
            "arr2[true] = 1;"),
        lines(
            "restricted index type",
            "found   : boolean",
            "required: number"));
  }

  @Test
  public void testIArrayLike11() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        lines(
            "var arr2 = new Int8Array4(10);",
            "arr2[true] = 1;"),
        lines(
            "restricted index type",
            "found   : boolean",
            "required: number"));
  }

  @Test
  public void testIArrayLike12() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        lines(
            "var arr2 = new BooleanArray5(10);",
            "arr2['prop'] = true;"),
        lines(
            "restricted index type",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testIArrayLike13() {
    testTypesWithExtraExterns(EXTERNS_WITH_IARRAYLIKE_DECLS,
        lines(
            "var numOrStr = null ? 0 : 'prop';",
            "var arr2 = new BooleanArray5(10);",
            "arr2[numOrStr] = true;"),
        lines(
            "restricted index type",
            "found   : (number|string)",
            "required: number"));
  }

  @Test
  public void testIArrayLikeCovariant1() {
    testTypesWithCommonExterns(
        lines(
            "function f(/** !IArrayLike<(string|number)>*/ x){};",
            "function g(/** !IArrayLike<number> */ arr) {",
            "    f(arr);",
            "}"));
  }

  @Test
  public void testIArrayLikeCovariant2() {
    testTypesWithCommonExterns(
        lines(
            "function f(/** !IArrayLike<(string|number)>*/ x){};",
            "function g(/** !Array<number> */ arr) {",
            "    f(arr);",
            "}"));
  }

  @Test
  public void testIArrayLikeStructuralMatch1() {
    testTypesWithCommonExterns(
        lines(
            "function f(/** !IArrayLike */ x){};",
            "/** @constructor */",
            "function Foo() {}",
            "/** @type {number} */ Foo.prototype.length",
            "f(new Foo)"));
  }

  @Test
  public void testIArrayLikeStructuralMatch2() {
    testTypesWithCommonExterns(
        lines(
            "function f(/** !IArrayLike */ x){};",
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {number} */ this.length = 5;",
            "}",
            "f(new Foo)"));
  }

  @Test
  public void testIArrayLikeStructuralMatch3() {
    testTypesWithCommonExterns(
        lines(
            "function f(/** !IArrayLike */ x){};",
            "f({length: 5})"));
  }

  @Test
  public void testIArrayLikeStructuralMatch4() {
    testTypesWithCommonExterns(
        lines(
            "function f(/** !IArrayLike */ x){};",
            "/** @const */ var ns = {};",
            "/** @type {number} */ ns.length",
            "f(ns)"));
  }

  @Test
  public void testIArrayLikeStructuralMatch5() {
    testTypesWithCommonExterns(
        lines(
            "function f(/** !IArrayLike */ x){};",
            "var ns = function() {};",
            "/** @type {number} */ ns.length",
            "f(ns)"));
  }

  @Test
  public void testIArrayLikeStructuralMatch6() {
    // Even though Foo's [] element type may not be string, we treat the lack
    // of explicit type like ? and allow this.
    testTypesWithCommonExterns(
        lines(
            "function f(/** !IArrayLike<string> */ x){};",
            "/** @constructor */",
            "function Foo() {}",
            "/** @type {number} */ Foo.prototype.length",
            "f(new Foo)"));
  }

  @Test
  public void testTemplatizedStructuralMatch1() {
    testTypes(
        lines(
            "/** @record @template T */",
            "function WithPropT() {}",
            "/** @type {T} */ WithPropT.prototype.prop;",
            "function f(/** !WithPropT<number> */ x){}",
            "/** @constructor */ function Foo() {}",
            "/** @type {number} */ Foo.prototype.prop;",
            "f(new Foo);"));
  }

  @Test
  public void testTemplatizedStructuralMatch2() {
    testTypes(
        lines(
            "/** @record @template T */",
            "function WithPropT() {}",
            "/** @type {T} */ WithPropT.prototype.prop",
            "function f(/** !WithPropT<number> */ x){};",
            "/** @constructor @template U */ function Foo() {}",
            "/** @type {number} */ Foo.prototype.prop",
            "f(new Foo)"));
  }

  @Test
  public void testTemplatizedStructuralMatch3() {
    testTypes(
        lines(
            "/** @record @template T */",
            "function WithPropT() {}",
            "/** @type {T} */ WithPropT.prototype.prop",
            "function f(/** !WithPropT<string> */ x){};",
            "/** @constructor @template U */ function Foo() {}",
            "/** @type {U} */ Foo.prototype.prop",
            "f(new Foo)"));
  }

  @Test
  public void testTemplatizedStructuralMismatch1() {
    testTypes(
        lines(
            "/** @record @template T */",
            "function WithPropT() {}",
            "/** @type {T} */ WithPropT.prototype.prop",
            "function f(/** !WithPropT<number> */ x){};",
            "/** @constructor */ function Foo() {}",
            "/** @type {string} */ Foo.prototype.prop = 'str'",
            "f(new Foo)"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : Foo",
            "required: WithPropT<number>",
        "missing : []",
        "mismatch: [prop]"));
  }

  @Test
  public void testTemplatizedStructuralMismatch2() {
    testTypes(
        lines(
            "/** @record @template T */",
            "function WithPropT() {}",
            "/** @type {T} */ WithPropT.prototype.prop",
            "function f(/** !WithPropT<number> */ x){};",
            "/** @constructor @template U */ function Foo() {}",
            "/** @type {string} */ Foo.prototype.prop = 'str'",
            "f(new Foo)"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : Foo",
            "required: WithPropT<number>",
        "missing : []",
        "mismatch: [prop]"));
  }

  @Test
  public void testTemplatizedStructuralMismatch3() {
    testTypes(
        lines(
            "/** @record @template T */",
            "function WithPropT() {}",
            "/** @type {T} */ WithPropT.prototype.prop",
            "function f(/** !WithPropT<number> */ x){};",
            "/**",
            " * @constructor",
            " * @template U",
            " * @param {U} x",
            " */",
            "function Foo(x) {",
            "  /** @type {U} */ this.prop = x",
            "}",
            "f(new Foo('str'))"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : Foo<string>",
            "required: WithPropT<number>",
        "missing : []",
        "mismatch: [prop]"));
  }

  @Test
  public void testTemplatizedStructuralMismatch4() {
    testTypes(
        lines(
            "/** @record @template T */",
            "function WithProp() {}",
            "/** @type {T} */ WithProp.prototype.prop;",
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {number} */ this.prop = 4;",
            "}",
            "/**",
            " * @template U",
            " * @param {!WithProp<U>} x",
            " * @param {U} y",
            " */",
            "function f(x, y){};",
            "f(new Foo, 'str')"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : Foo",
            "required: WithProp<string>",
            "missing : []",
            "mismatch: [prop]"));
  }

  @Test
  public void testTemplatizedStructuralMismatchNotFound() {
    // TODO(blickly): We would like to find the parameter mismatch here.
    // Currently they match with type WithProp<?>, which is somewhat unsatisfying.
    testTypes(
        lines(
            "/** @record @template T */",
            "function WithProp() {}",
            "/** @type {T} */ WithProp.prototype.prop;",
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {number} */ this.prop = 4;",
            "}",
            "/** @constructor */",
            "function Bar() {",
            "  /** @type {string} */ this.prop = 'str';",
            "}",
            "/**",
            " * @template U",
            " * @param {!WithProp<U>} x",
            " * @param {!WithProp<U>} y",
            " */",
            "function f(x, y){};",
            "f(new Foo, new Bar)"));
  }

  private static final String EXTERNS_WITH_IOBJECT_DECLS = lines(
      "/**",
      " * @constructor",
      " * @implements IObject<(string|number), number>",
      " */",
      "function Object2() {}",
      "/**",
      " * @constructor",
      " * @implements IObject<number, number>",
      " */",
      "function Object3() {}");

  @Test
  public void testIObject1() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "var arr2 = new Object2();",
            "arr2[0] = 1;"));
  }

  @Test
  public void testIObject2() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "var arr2 = new Object2();",
            "arr2['str'] = 1;"));
  }

  @Test
  public void testIObject3() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "var arr2 = new Object2();",
            "arr2[true] = 1;"),
        lines(
            "restricted index type",
            "found   : boolean",
            "required: (number|string)"));
  }

  @Test
  public void testIObject4() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "var arr2 = new Object2();",
            "arr2[function(){}] = 1;"),
        lines(
            "restricted index type",
            "found   : function(): undefined",
            "required: (number|string)"));
  }

  @Test
  public void testIObject5() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "var arr2 = new Object2();",
            "arr2[{}] = 1;"),
        lines(
            "restricted index type",
            "found   : {}",
            "required: (number|string)"));
  }

  @Test
  public void testIObject6() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "var arr2 = new Object2();",
            "arr2[undefined] = 1;"),
        lines(
            "restricted index type",
            "found   : undefined",
            "required: (number|string)"));
  }

  @Test
  public void testIObject7() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "var arr2 = new Object2();",
            "arr2[null] = 1;"),
        lines(
            "restricted index type",
            "found   : null",
            "required: (number|string)"));
  }

  @Test
  public void testIObject8() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "var arr = new Object2();",
            "/** @type {boolean} */",
            "var x = arr[3];"),
        lines(
            "initializing variable",
            "found   : number",
            "required: boolean"));
  }

  @Test
  public void testIObject9() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "var arr = new Object2();",
            "/** @type {(number|string)} */",
            "var x = arr[3];"));
  }

  @Test
  public void testIObject10() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "var arr = new Object3();",
            "/** @type {number} */",
            "var x = arr[3];"));
  }

  @Test
  public void testIObject11() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "var arr = new Object3();",
            "/** @type {boolean} */",
            "var x = arr[3];"),
        lines(
            "initializing variable",
            "found   : number",
            "required: boolean"));
  }

  @Test
  public void testIObject12() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "var arr = new Object3();",
            "/** @type {string} */",
            "var x = arr[3];"),
        lines(
            "initializing variable",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testIObject13() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "var arr = new Object3();",
            "arr[3] = false;"),
        lines(
            "assignment",
            "found   : boolean",
            "required: number"));
  }

  @Test
  public void testIObject14() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "var arr = new Object3();",
            "arr[3] = 'value';"),
        lines(
            "assignment",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testIObject15() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "function f(/** !Object<string, string> */ x) {}",
            "var /** !IObject<string, string> */ y;",
            "f(y);"));
  }

  @Test
  public void testIObject16() {
    testTypesWithExtraExterns(
        EXTERNS_WITH_IOBJECT_DECLS,
        lines(
            "function f(/** !Object<string, string> */ x) {}",
            "var /** !IObject<string, number> */ y;",
            "f(y);"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : IObject<string,number>",
            "required: Object<string,string>"));
  }

  /**
   * although C1 does not declare to extend Interface1, obj2 : C1 still structurally matches obj1 :
   * Interface1 because of the structural interface matching (Interface1 is declared with @record
   * tag)
   */
  @Test
  public void testStructuralInterfaceMatching1() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function Interface1() {}",
            "/** @type {number} */",
            "Interface1.prototype.length;",
            "",
            "/** @constructor */",
            "function C1() {}",
            "/** @type {number} */",
            "C1.prototype.length;"),
        lines(
            "/** @type{Interface1} */",
            "var obj1;",
            "/** @type{C1} */",
            "var obj2 = new C1();",
            "obj1 = obj2;"));
  }

  @Test
  public void testStructuralInterfaceMatching2() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function Interface1() {}",
            "/** @type {number} */",
            "Interface1.prototype.length;",
            "",
            "/** @constructor */",
            "function C1() {}",
            "/** @type {number} */",
            "C1.prototype.length;"),
        lines(
            "/** @type{Interface1} */",
            "var obj1;",
            "var obj2 = new C1();",
            "obj1 = obj2;"));
  }

  @Test
  public void testStructuralInterfaceMatching3() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function I1() {}",
            "",
            "/** @record */",
            "function I2() {}"),
        lines(
            "/** @type {I1} */",
            "var i1;",
            "/** @type {I2} */",
            "var i2;",
            "i1 = i2;",
            "i2 = i1;"));
  }

  @Test
  public void testStructuralInterfaceMatching4_1() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function I1() {}",
            "",
            "/** @record */",
            "function I2() {}"),
        lines(
            "/** @type {I1} */",
            "var i1;",
            "/** @type {I2} */",
            "var i2;",
            "i2 = i1;",
            "i1 = i2;"));
  }

  @Test
  public void testStructuralInterfaceMatching5_1() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function I1() {}",
            "",
            "/** @interface */",
            "function I3() {}",
            "/** @type {number} */",
            "I3.prototype.length;"),
        lines(
            "/** @type {I1} */",
            "var i1;",
            "/** @type {I3} */",
            "var i3;",
            "i1 = i3;"));
  }

  @Test
  public void testStructuralInterfaceMatching7_1() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function I1() {}",
            "",
            "/** @constructor */",
            "function C1() {}",
            "/** @type {number} */",
            "C1.prototype.length;"),
        lines(
            "/** @type {I1} */",
            "var i1;" +
            "/** @type {C1} */",
            "var c1;",
            "i1 = c1;   // no warning"));
  }

  @Test
  public void testStructuralInterfaceMatching9() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C1() {}",
            "/** @type {number} */",
            "C1.prototype.length;",
            "",
            "/** @constructor */",
            "function C2() {}",
            "/** @type {number} */",
            "C2.prototype.length;"),
        lines(
            "/** @type {C1} */",
            "var c1;" +
            "/** @type {C2} */",
            "var c2;",
            "c1 = c2;"),
        lines(
            "assignment",
            "found   : (C2|null)",
            "required: (C1|null)"));
  }

  @Test
  public void testStructuralInterfaceMatching11_1() {
    testTypesWithExtraExterns(
        lines(
            "/** @interface */",
            "function I3() {}",
            "/** @type {number} */",
            "I3.prototype.length;",
            "",
            "/** ",
            " * @record",
            " * @extends I3",
            " */",
            "function I4() {}",
            "/** @type {boolean} */",
            "I4.prototype.prop;",
            "",
            "/** @constructor */",
            "function C4() {}",
            "/** @type {number} */",
            "C4.prototype.length;",
            "/** @type {boolean} */",
            "C4.prototype.prop;"),
        lines(
            "/** @type {I4} */",
            "var i4;" +
            "/** @type {C4} */",
            "var c4;",
            "i4 = c4;"));
  }

  @Test
  public void testStructuralInterfaceMatching13() {
    testTypesWithExtraExterns(
        lines(
            "/**",
            "   * @record",
            "   */",
            "  function I5() {}",
            "  /** @type {I5} */",
            "  I5.prototype.next;",
            "",
            "  /**",
            "   * @interface",
            "   */",
            "  function C5() {}",
            "  /** @type {C5} */",
            "  C5.prototype.next;"),
        lines(
            "/** @type {I5} */",
            "var i5;" +
            "/** @type {C5} */",
            "var c5;",
            "i5 = c5;"));
  }

  @Test
  public void testStructuralInterfaceMatching13_2() {
    testTypesWithExtraExterns(
        lines(
            "/**",
            "   * @record",
            "   */",
            "  function I5() {}",
            "  /** @type {I5} */",
            "  I5.prototype.next;",
            "",
            "  /**",
            "   * @record",
            "   */",
            "  function C5() {}",
            "  /** @type {C5} */",
            "  C5.prototype.next;"),
        lines(
            "/** @type {I5} */",
            "var i5;" +
            "/** @type {C5} */",
            "var c5;",
            "i5 = c5;"));
  }

  @Test
  public void testStructuralInterfaceMatching13_3() {
    testTypesWithExtraExterns(
        lines(
            "/**",
            "   * @interface",
            "   */",
            "  function I5() {}",
            "  /** @type {I5} */",
            "  I5.prototype.next;",
            "",
            "  /**",
            "   * @record",
            "   */",
            "  function C5() {}",
            "  /** @type {C5} */",
            "  C5.prototype.next;"),
        lines(
            "/** @type {I5} */",
            "var i5;" +
            "/** @type {C5} */",
            "var c5;",
            "i5 = c5;"),
        lines(
            "assignment",
            "found   : (C5|null)",
            "required: (I5|null)"));
  }

  @Test
  public void testStructuralInterfaceMatching15() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function I5() {}",
            "/** @type {I5} */",
            "I5.prototype.next;",
            "",
            "/** @constructor */",
            "function C6() {}",
            "/** @type {C6} */",
            "C6.prototype.next;",
            "",
            "/** @constructor */",
            "function C5() {}",
            "/** @type {C6} */",
            "C5.prototype.next;"),
        lines(
            "/** @type {I5} */",
            "var i5;" +
            "/** @type {C5} */",
            "var c5;",
            "i5 = c5;"));
  }

  /**
   * a very long structural chain, all property types from I5 and C5
   * are structurally the same, I5 is declared as @record
   * so structural interface matching will be performed
   */
  private static final String EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD =
      lines(
          "/** @record */",
          "function I5() {}",
          "/** @type {I5} */",
          "I5.prototype.next;",
          "",
          "/** @constructor */",
          "function C6() {}",
          "/** @type {C6} */",
          "C6.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_1() {}",
          "/** @type {C6} */",
          "C6_1.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_2() {}",
          "/** @type {C6_1} */",
          "C6_2.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_3() {}",
          "/** @type {C6_2} */",
          "C6_3.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_4() {}",
          "/** @type {C6_3} */",
          "C6_4.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_5() {}",
          "/** @type {C6_4} */",
          "C6_5.prototype.next;",
          "",
          "/** @constructor */",
          "function C5() {}",
          "/** @type {C6_5} */",
          "C5.prototype.next;");

  @Test
  public void testStructuralInterfaceMatching16_1() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
        lines(
            "/** @type {I5} */",
            "var i5;" +
            "/** @type {C5} */",
            "var c5;",
            "i5 = c5;"));
  }

  @Test
  public void testStructuralInterfaceMatching17_1() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
        lines(
            "/** @type {C5} */",
            "var c5;",
            "/**",
            " * @param {I5} i5",
            " */",
            "function f(i5) {}",
            "",
            "f(c5);"));
  }

  @Test
  public void testStructuralInterfaceMatching18_1() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
        lines(
            "/** @type {I5} */",
            "var i5;" +
            "/** @type {C5} */",
            "var c5;",
            "i5.next = c5;"));
  }

  /**
   * a very long non-structural chain, there is a slight difference between
   * the property type structural of I5 and that of C5:
   * I5.next.next.next.next.next has type I5
   * while
   * C5.next.next.next.next.next has type number
   */
  private static final String EXTERNS_FOR_LONG_NONMATCHING_CHAIN =
      lines(
          "/** @record */",
          "function I5() {}",
          "/** @type {I5} */",
          "I5.prototype.next;",
          "",
          "/** @constructor */",
          "function C6() {}",
          "/** @type {number} */",
          "C6.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_1() {}",
          "/** @type {C6} */",
          "C6_1.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_2() {}",
          "/** @type {C6_1} */",
          "C6_2.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_3() {}",
          "/** @type {C6_2} */",
          "C6_3.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_4() {}",
          "/** @type {C6_3} */",
          "C6_4.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_5() {}",
          "/** @type {C6_4} */",
          "C6_5.prototype.next;",
          "",
          "/** @interface */",
          "function C5() {}",
          "/** @type {C6_5} */",
          "C5.prototype.next;");

  @Test
  public void testStructuralInterfaceMatching19() {
    testTypesWithExtraExterns(
        // the type structure of I5 and C5 are different
        EXTERNS_FOR_LONG_NONMATCHING_CHAIN,
        lines(
            "/** @type {I5} */",
            "var i5;",
            "/** @type {C5} */",
            "var c5;",
            "i5 = c5;"),
        lines(
            "assignment",
            "found   : (C5|null)",
            "required: (I5|null)"));
  }

  @Test
  public void testStructuralInterfaceMatching20() {
    testTypesWithExtraExterns(
        // the type structure of I5 and C5 are different
        EXTERNS_FOR_LONG_NONMATCHING_CHAIN,
        lines(
            "/** @type {C5} */",
            "var c5;",
            "/**",
            " * @param {I5} i5",
            " */",
            "function f(i5) {}",
            "",
            "f(c5);"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : (C5|null)",
            "required: (I5|null)"));
  }

  @Test
  public void testStructuralInterfaceMatching21() {
    testTypesWithExtraExterns(
        // the type structure of I5 and C5 are different
        EXTERNS_FOR_LONG_NONMATCHING_CHAIN,
        lines(
            "/** @type {I5} */",
            "var i5;",
            "/** @type {C5} */",
            "var c5;",
            "i5.next = c5;"),
        lines(
            "assignment to property next of I5",
            "found   : (C5|null)",
            "required: (I5|null)"));
  }

  /**
   * structural interface matching will also be able to structurally match ordinary function types
   * check if the return types of the ordinary function types match (should match, since declared
   * with @record)
   */
  @Test
  public void testStructuralInterfaceMatching22_1() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"));
  }

  /**
   * structural interface matching will also be able to structurally match ordinary function types
   * check if the return types of the ordinary function types match (should not match)
   */
  @Test
  public void testStructuralInterfaceMatching23() {
    testTypesWithExtraExterns(
        // the type structure of I5 and C5 are different
        lines(
            EXTERNS_FOR_LONG_NONMATCHING_CHAIN,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"),
        lines(
            "assignment",
            "found   : (C7|null)",
            "required: (I7|null)"));
  }

  /**
   * structural interface matching will also be able to structurally match ordinary function types
   * check if the parameter types of the ordinary function types match (should match, since declared
   * with @record)
   */
  @Test
  public void testStructuralInterfaceMatching24_1() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(C5): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(I5): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"));
  }

  /**
   * structural interface matching will also be able to structurally match ordinary function types
   * check if the parameter types of the ordinary function types match (should match, since declared
   * with @record)
   */
  @Test
  public void testStructuralInterfaceMatching26_1() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(C5, C5, I5): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(I5, C5): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"));
  }

  /**
   * structural interface matching will also be able to structurally match ordinary function types
   * check if the parameter types of the ordinary function types match (should match)
   */
  @Test
  public void testStructuralInterfaceMatching29_1() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"));
  }

  /** the "this" of I5 and C5 are covariants, so should match */
  @Test
  public void testStructuralInterfaceMatching30_1_1() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:I5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:C5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"));
  }

  /** the "this" of I5 and C5 are covariants, so should match */
  @Test
  public void testStructuralInterfaceMatching30_2_1() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"));
  }

  /** the "this" of I5 and C5 are covariants, so should match */
  @Test
  public void testStructuralInterfaceMatching30_3_1() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */ function I5() {}",
            "/** @constructor @implements {I5} */ function C5() {}",
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"));
  }

  /** I7 is declared with @record tag, so it will match */
  @Test
  public void testStructuralInterfaceMatching30_3_2() {
    testTypesWithExtraExterns(
        lines(
            "/** @interface */ function I5() {}",
            "/** @constructor @implements {I5} */ function C5() {}",
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"));
  }

  /**
   * Although I7 is declared with @record tag, note that I5 is declared with @interface and C5 does
   * not extend I5, so it will not match
   */
  @Test
  public void testStructuralInterfaceMatching30_3_3() {
    testTypesWithExtraExterns(
        lines(
            "/** @interface */ function I5() {}",
            "/** @constructor */ function C5() {}",
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"),
        lines(
            "assignment",
            "found   : (C7|null)",
            "required: (I7|null)"));
  }

  @Test
  public void testStructuralInterfaceMatching30_3_4() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            "/** @record */ function I5() {}",
            "/** @constructor */ function C5() {}",
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"));
  }

  /** the "this" of I5 and C5 are covariants, so should match */
  @Test
  public void testStructuralInterfaceMatching30_4_1() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            "/** @record */ function I5() {}",
            "/** @constructor @implements {I5} */ function C5() {}",
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:I5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:C5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"));
  }

  /**
   * although I7 is declared with @record tag I5 is declared with @interface tag, so no structural
   * interface matching
   */
  @Test
  public void testStructuralInterfaceMatching30_4_2() {
    testTypesWithExtraExterns(
        lines(
            "/** @interface */ function I5() {}",
            "/** @constructor */ function C5() {}",
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:I5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:C5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"),
        lines(
            "assignment",
            "found   : (C7|null)",
            "required: (I7|null)"));
  }

  /**
   * structural interface matching will also be able to structurally match ordinary function types
   * check if the this types of the ordinary function types match (should match)
   */
  @Test
  public void testStructuralInterfaceMatching31_1() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"));
  }

  /** test structural interface matching for record types */
  @Test
  public void testStructuralInterfaceMatching32_2() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {{prop: I7, prop2: C7}}*/",
            "var r1;",
            "/** @type {{prop: C7, prop2: C7}} */",
            "var r2;",
            "r1 = r2;"));
  }

  /** test structural interface matching for record types */
  @Test
  public void testStructuralInterfaceMatching33_3() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {{prop: I7, prop2: C7}}*/",
            "var r1;",
            "/** @type {{prop: C7, prop2: C7, prop3: C7}} */",
            "var r2;",
            "r1 = r2;"));
  }

  /**
   * test structural interface matching for a combination of ordinary function types and record
   * types
   */
  @Test
  public void testStructuralInterfaceMatching36_2() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {{fun: function(C7):I7, prop: {prop: I7}}} */",
            " var com1;",
            "/** @type {{fun: function(I7):C7, prop: {prop: C7}}} */",
            "var com2;",
            "",
            "com1 = com2;"));
  }

  /**
   * test structural interface matching for a combination of ordinary function types and record
   * types
   */
  @Test
  public void testStructuralInterfaceMatching36_3() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {{fun: function(C7):I7, prop: {prop: I7}}} */",
            " var com1;",
            "/** @type {{fun: function(I7):C7, prop: {prop: C7}}} */",
            "var com2;",
            "",
            "com1 = com2;"));
  }

  /**
   * test structural interface matching for a combination of ordinary function types and record
   * types here C7 does not structurally match I7
   */
  @Test
  public void testStructuralInterfaceMatching37() {
    testTypesWithExtraExterns(
        // the type structure of I5 and C5 are different
        lines(
            EXTERNS_FOR_LONG_NONMATCHING_CHAIN,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {{fun: function(C7):I7, prop: {prop: I7}}} */",
            "var com1;",
            "/** @type {{fun: function(I7):C7, prop: {prop: C7}}} */",
            "var com2;",
            "",
            "com1 = com2;"),
        lines(
            "assignment",
            "found   : {fun: function((I7|null)): (C7|null), prop: {prop: (C7|null)}}",
            "required: {fun: function((C7|null)): (I7|null), prop: {prop: (I7|null)}}",
            "missing : []",
        "mismatch: [fun,prop]"));
  }

  /** test structural interface matching for object literals */
  @Test
  public void testStructuralInterfaceMatching39() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;"),
        lines(
            "/** @type {I2} */",
            "var o1 = {length : 'test'};"),
        lines(
            "initializing variable",
            "found   : {length: string}",
            "required: (I2|null)"));
  }

  /** test structural interface matching for object literals */
  @Test
  public void testStructuralInterfaceMatching40() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;"),
        lines(
            "/** @type {I2} */",
            "var o1 = {length : 123};"));
  }

  /** test structural interface matching for object literals */
  @Test
  public void testStructuralInterfaceMatching40_1() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;"),
        lines(
            "/** @type {I2} */",
            "var o1 = {length : 123};"));
  }

  /** test structural interface matching for object literals */
  @Test
  public void testStructuralInterfaceMatching41() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;"),
        lines(
            "/** @type {I2} */",
            "var o1 = {length : 123};",
            "/** @type {I2} */",
            "var i;",
            "i = o1;"));
  }

  /** test structural interface matching for object literals */
  @Test
  public void testStructuralInterfaceMatching41_1() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;"),
        lines(
            "/** @type {I2} */",
            "var o1 = {length : 123};",
            "/** @type {I2} */",
            "var i;",
            "i = o1;"));
  }

  /** test structural interface matching for object literals */
  @Test
  public void testStructuralInterfaceMatching42() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;"),
        lines(
            "/** @type {{length: number}} */",
            "var o1 = {length : 123};",
            "/** @type {I2} */",
            "var i;",
            "i = o1;"));
  }

  /** test structural interface matching for object literals */
  @Test
  public void testStructuralInterfaceMatching43() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;"),
        lines(
            "var o1 = {length : 123};",
            "/** @type {I2} */",
            "var i;",
            "i = o1;"));
  }

  @Test
  public void testStructuralInterfaceMatching44() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */ function I() {}",
            "/** @type {!Function} */ I.prototype.removeEventListener;",
            "/** @type {!Function} */ I.prototype.addEventListener;",
            "/** @constructor */ function C() {}",
            "/** @type {!Function} */ C.prototype.addEventListener;"),
        lines(
            "/** @param {C|I} x */",
            "function f(x) { x.addEventListener(); }",
            "f(new C());"));
  }

  /**
   * Currently, the structural interface matching does not support structural matching for template
   * types Using @template @interfaces requires @implements them explicitly.
   */
  @Test
  public void testStructuralInterfaceMatching45() {
    testTypes(
        lines(
            "/**",
            " * @record",
            " * @template X",
            " */",
            "function I() {}",
            "/** @constructor */",
            "function C() {}",
            "var /** !I */ i = new C;"));
  }

  @Test
  public void testStructuralInterfaceMatching46() {
    testTypes(
        lines(
            "/** @interface */",
            "function I2() {}",
            "/**",
            " * @interface",
            " * @extends {I2}",
            " */",
            "function I3() {}",
            "/**",
            " * @record",
            " * @extends {I3}",
            " */",
            "function I4() {}",
            "/** @type {I4} */",
            "var i4;",
            "/** @type {I2} */",
            "var i2;",
            "i4 = i2;"));
  }

  @Test
  public void testStructuralInterfaceMatching47() {
    testTypesWithExtraExterns(
        lines(
            "/** @interface */",
            "function I2() {}",
            "/**",
            " * @interface",
            " * @extends {I2}",
            " */",
            "function I3() {}",
            "/**",
            " * @record",
            " * @extends {I3}",
            " */",
            "function I4() {}"),
        lines(
            "/** @type {I4} */",
            "var i4;",
            "/** @type {I2} */",
            "var i2;",
            "i4 = i2;"));
  }

  @Test
  public void testStructuralInterfaceMatching48() {
    testTypesWithExtraExterns(
        "",
        lines(
            "/** @interface */",
            "function I2() {}",
            "/**",
            " * @record",
            " * @extends {I2}",
            " */",
            "function I3() {}",
            "/** @type {I3} */",
            "var i3;",
            "/** @type {I2} */",
            "var i2;",
            "i3 = i2;"));
  }

  @Test
  public void testStructuralInterfaceMatching49() {
    testTypesWithExtraExterns(
        lines(
            "/** @interface */",
            "function I2() {}",
            "/**",
            " * @record",
            " * @extends {I2}",
            " */",
            "function I3() {}"),
        lines(
            "/** @type {I3} */",
            "var i3;",
            "/** @type {I2} */",
            "var i2;",
            "i3 = i2;"));
  }

  @Test
  public void testStructuralInterfaceMatching49_2() {
    testTypesWithExtraExterns(
        lines(
            "/** @record */",
            "function I2() {}",
            "/**",
            " * @record",
            " * @extends {I2}",
            " */",
            "function I3() {}"),
        lines(
            "/** @type {I3} */",
            "var i3;",
            "/** @type {I2} */",
            "var i2;",
            "i3 = i2;"));
  }

  @Test
  public void testStructuralInterfaceMatching50() {
    testTypesWithExtraExterns(
        lines(
            "/** @interface */",
            "function I2() {}",
            "/**",
            " * @record",
            " * @extends {I2}",
            " */",
            "function I3() {}"),
        lines(
            "/** @type {I3} */",
            "var i3;",
            "/** @type {{length : number}} */",
            "var r = {length: 123};",
            "i3 = r;"));
  }

  @Test
  public void testEmptySubtypeRecord1() {
    // Verify that empty @record subtypes don't cause circular definition warnings.
    testTypes(
        lines(
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
            "/** @param {I3} a */ function fn(a) {}"));
  }

  @Test
  public void testEmptySubtypeRecord2() {
    testTypes(
        lines(
            "/** @type {!SubInterface} */ var value;",
            "/** @record */ var SuperInterface = function () {};",
            "/** @record @extends {SuperInterface} */ var SubInterface = function() {};"));
  }

  @Test
  public void testEmptySubtypeRecord3() {
    testTypes(
        lines(
            "/** @record */ var SuperInterface = function () {};",
            "/** @record @extends {SuperInterface} */ var SubInterface = function() {};",
            "/** @type {!SubInterface} */ var value;"));
  }

  @Test
  public void testEmptySubtypeInterface1() {
    testTypes(
        lines(
            "/** @type {!SubInterface} */ var value;",
            "/** @interface */ var SuperInterface = function () {};",
            "/** @interface @extends {SuperInterface} */ var SubInterface = function() {};"));
  }

  @Test
  public void testStructuralInterfaceMatching1_1() {
    testTypesWithExtraExterns(
        lines(
            "/** @interface */",
            "function Interface1() {}",
            "/** @type {number} */",
            "Interface1.prototype.length;",
            "",
            "/** @constructor */",
            "function C1() {}",
            "/** @type {number} */",
            "C1.prototype.length;"),
        lines(
            "/** @type{Interface1} */",
            "var obj1;",
            "/** @type{C1} */",
            "var obj2 = new C1();",
            "obj1 = obj2;"),
        lines(
            "assignment",
            "found   : (C1|null)",
            "required: (Interface1|null)"));
  }

  /**
   * structural interface matching will also be able to structurally match ordinary function types
   * check if the return types of the ordinary function types match (should not match, since I7 is
   * declared with @interface)
   */
  @Test
  public void testStructuralInterfaceMatching22_2() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @interface */",
            "function I7() {}",
            "/** @type{function(): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"),
        lines(
            "assignment",
            "found   : (C7|null)",
            "required: (I7|null)"));
  }

  /** declared with @interface, no structural interface matching */
  @Test
  public void testStructuralInterfaceMatching30_3() {
    testTypesWithExtraExterns(
        // I5 and C5 shares the same type structure
        lines(
            "/** @interface */ function I5() {}",
            "/** @constructor @implements {I5} */ function C5() {}",
            "/** @interface */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};"),
        lines(
            "/** @type {I7} */",
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;"),
        lines(
            "assignment",
            "found   : (C7|null)",
            "required: (I7|null)"));
  }

  @Test
  public void testRecordWithOptionalProperty() {
    testTypes(
        lines(
            "/**  @constructor */ function Foo() {};",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** {str: string, opt_num: (undefined|number)} */ x = new Foo;"));
  }

  @Test
  public void testRecordWithUnknownProperty() {
    testTypes(
        lines(
            "/**  @constructor */ function Foo() {};",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** {str: string, unknown: ?} */ x = new Foo;"),
        lines(
            "initializing variable",
            "found   : Foo",
            "required: {str: string, unknown: ?}",
            "missing : [unknown]",
            "mismatch: []"
        ));
  }

  @Test
  public void testRecordWithOptionalUnknownProperty() {
    testTypes(
        lines(
            "/**  @constructor */ function Foo() {};",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** {str: string, opt_unknown: (?|undefined)} */ x = new Foo;"));
  }

  @Test
  public void testRecordWithTopProperty() {
    testTypes(
        lines(
            "/**  @constructor */ function Foo() {};",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** {str: string, top: *} */ x = new Foo;"),
        lines(
            "initializing variable",
            "found   : Foo",
            "required: {str: string, top: *}",
            "missing : [top]",
            "mismatch: []"));
  }

  @Test
  public void testStructuralInterfaceWithOptionalProperty() {
    testTypes(
        lines(
            "/** @record */ function Rec() {}",
            "/** @type {string} */ Rec.prototype.str;",
            "/** @type {(number|undefined)} */ Rec.prototype.opt_num;",
            "",
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** !Rec */ x = new Foo;"));
  }

  @Test
  public void testStructuralInterfaceWithUnknownProperty() {
    testTypes(
        lines(
            "/** @record */ function Rec() {}",
            "/** @type {string} */ Rec.prototype.str;",
            "/** @type {?} */ Rec.prototype.unknown;",
            "",
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** !Rec */ x = new Foo;"),
        lines(
            "initializing variable",
            "found   : Foo",
            "required: Rec",
            "missing : [unknown]",
            "mismatch: []"));
  }

  @Test
  public void testStructuralInterfaceWithOptionalUnknownProperty() {
    testTypes(
        lines(
            "/** @record */ function Rec() {}",
            "/** @type {string} */ Rec.prototype.str;",
            "/** @type {?|undefined} */ Rec.prototype.opt_unknown;",
            "",
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** !Rec */ x = new Foo;"));
  }

  @Test
  public void testOptionalUnknownIsAssignableToUnknown() {
    testTypes(
        lines(
            "function f(/** (undefined|?) */ opt_unknown) {",
            "  var /** ? */ unknown = opt_unknown;",
            "}"));
  }

  @Test
  public void testStructuralInterfaceWithTopProperty() {
    testTypes(
        lines(
            "/** @record */ function Rec() {}",
            "/** @type {string} */ Rec.prototype.str;",
            "/** @type {*} */ Rec.prototype.top;",
            "",
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** !Rec */ x = new Foo;"),
        lines(
            "initializing variable",
            "found   : Foo",
            "required: Rec",
            "missing : [top]",
            "mismatch: []"));
  }

  @Test
  public void testStructuralInterfaceCycleDoesntCrash() {
    testTypes(
        lines(
            "/**  @record */ function Foo() {};",
            "/**  @return {MutableFoo} */ Foo.prototype.toMutable;",
            "/**  @record */ function MutableFoo() {};",
            "/**  @param {Foo} from */ MutableFoo.prototype.copyFrom;",
            "",
            "/**  @record */ function Bar() {};",
            "/**  @return {MutableBar} */ Bar.prototype.toMutable;",
            "/**  @record */ function MutableBar() {};",
            "/**  @param {Bar} from */ MutableBar.prototype.copyFrom;",
            "",
            "/** @constructor @implements {MutableBar} */ function MutableBarImpl() {};",
            "/** @override */ MutableBarImpl.prototype.copyFrom = function(from) {};",
            "/** @constructor  @implements {MutableFoo} */ function MutableFooImpl() {};",
            "/** @override */ MutableFooImpl.prototype.copyFrom = function(from) {};"));
  }

  @Test
  public void testStructuralInterfacesMatchOwnProperties1() {
    testTypes(
        lines(
            "/** @record */ function WithProp() {}",
            "/** @type {number} */ WithProp.prototype.prop;",
            "",
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {number} */ this.prop = 5;",
            "}",
            "var /** !WithProp */ wp = new Foo;"));
  }

  @Test
  public void testStructuralInterfacesMatchOwnProperties2() {
    testTypes(
        lines(
            "/** @record */ function WithProp() {}",
            "/** @type {number} */ WithProp.prototype.prop;",
            "",
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {number} */ this.oops = 5;",
            "}",
            "var /** !WithProp */ wp = new Foo;"),
        lines(
            "initializing variable",
            "found   : Foo",
            "required: WithProp",
            "missing : [prop]",
            "mismatch: []"));
  }

  @Test
  public void testStructuralInterfacesMatchOwnProperties3() {
    testTypes(
        lines(
            "/** @record */ function WithProp() {}",
            "/** @type {number} */ WithProp.prototype.prop;",
            "",
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {string} */ this.prop = 'str';",
            "}",
            "var /** !WithProp */ wp = new Foo;"),
        lines(
            "initializing variable",
            "found   : Foo",
            "required: WithProp",
            "missing : []",
            "mismatch: [prop]"));
  }

  @Test
  public void testStructuralInterfacesMatchFunctionNamespace1() {
    testTypes(
        lines(
            "/** @record */ function WithProp() {}",
            "/** @type {number} */ WithProp.prototype.prop;",
            "",
            "var ns = function() {};",
            "/** @type {number} */ ns.prop;",
            "var /** !WithProp */ wp = ns;"));
  }

  @Test
  public void testStructuralInterfacesMatchFunctionNamespace2() {
    testTypes(
        lines(
            "/** @record */ function WithProp() {}",
            "/** @type {number} */ WithProp.prototype.prop;",
            "",
            "var ns = function() {};",
            "/** @type {number} */ ns.oops;",
            "var /** !WithProp */ wp = ns;"),
        lines(
            "initializing variable",
            "found   : function(): undefined",
            "required: WithProp",
            "missing : [prop]",
            "mismatch: []"));
  }

  @Test
  public void testStructuralInterfacesMatchFunctionNamespace3() {
    testTypes(
        lines(
            "/** @record */ function WithProp() {}",
            "/** @type {number} */ WithProp.prototype.prop;",
            "",
            "var ns = function() {};",
            "/** @type {string} */ ns.prop;",
            "var /** !WithProp */ wp = ns;"),
        lines(
            "initializing variable",
            "found   : function(): undefined",
            "required: WithProp",
        "missing : []",
        "mismatch: [prop]"));
  }

  @Test
  public void testRecursiveTemplatizedStructuralInterface() {
    testTypes(
        lines(
            "/**",
            " * @record",
            " * @template T",
            " */",
            "var Rec = function() { };",
            "/** @type {!Rec<T>} */",
            "Rec.prototype.p;",
            "",
            "/**",
            " * @constructor @implements {Rec<T>}",
            " * @template T",
            " */",
            "var Foo = function() {};",
            "/** @override */",
            "Foo.prototype.p = new Foo;"));
  }

  @Test
  public void testCovarianceForRecordType1() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor ",
            "  * @extends {C} ",
            "  */",
            "function C2() {}"),
        lines(
            "/** @type {{prop: C}} */",
            "var r1;",
            "/** @type {{prop: C2}} */",
            "var r2;",
            "r1 = r2;"));
  }

  @Test
  public void testCovarianceForRecordType2() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor ",
            "  * @extends {C} ",
            "  */",
            "function C2() {}"),
        lines(
            "/** @type {{prop: C, prop2: C}} */",
            "var r1;",
            "/** @type {{prop: C2, prop2: C}} */",
            "var r2;",
            "r1 = r2;"));
  }

  @Test
  public void testCovarianceForRecordType3() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor @extends {C} */",
            "function C2() {}"),
        lines(
            "/** @type {{prop: C}} */",
            "var r1;",
            "/** @type {{prop: C2, prop2: C}} */",
            "var r2;",
            "r1 = r2;"));
  }

  @Test
  public void testCovarianceForRecordType4() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor @extends {C} */",
            "function C2() {}"),
        lines(
            "/** @type {{prop: C, prop2: C}} */",
            "var r1;",
            "/** @type {{prop: C2}} */",
            "var r2;",
            "r1 = r2;"),
        lines(
            "assignment",
            "found   : {prop: (C2|null)}",
            "required: {prop: (C|null), prop2: (C|null)}",
            "missing : [prop2]",
            "mismatch: []"));
  }

  @Test
  public void testCovarianceForRecordType5() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor */",
            "function C2() {}"),
        lines(
            "/** @type {{prop: C}} */",
            "var r1;",
            "/** @type {{prop: C2}} */",
            "var r2;",
            "r1 = r2;"),
        lines(
            "assignment",
            "found   : {prop: (C2|null)}",
            "required: {prop: (C|null)}",
            "missing : []",
            "mismatch: [prop]"));
  }

  @Test
  public void testCovarianceForRecordType6() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor @extends {C} */",
            "function C2() {}"),
        lines(
            "/** @type {{prop: C2}} */",
            "var r1;",
            "/** @type {{prop: C}} */",
            "var r2;",
            "r1 = r2;"),
        lines(
            "assignment",
            "found   : {prop: (C|null)}",
            "required: {prop: (C2|null)}",
            "missing : []",
            "mismatch: [prop]"));
  }

  @Test
  public void testCovarianceForRecordType7() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor @extends {C} */",
            "function C2() {}"),
        lines(
            "/** @type {{prop: C2, prop2: C2}} */",
            "var r1;",
            "/** @type {{prop: C2, prop2: C}} */",
            "var r2;",
            "r1 = r2;"),
        lines(
            "assignment",
            "found   : {prop: (C2|null), prop2: (C|null)}",
            "required: {prop: (C2|null), prop2: (C2|null)}",
            "missing : []",
            "mismatch: [prop2]"));
  }

  @Test
  public void testCovarianceForRecordType8() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function Foo(){}",
            "/** @type {number} */",
            "Foo.prototype.x = 5",
            "/** @type {string} */",
            "Foo.prototype.y = 'str'"),
        lines(
            "/** @type {{x: number, y: string}} */",
            "var r1 = {x: 1, y: 'value'};",
            "",
            "/** @type {!Foo} */",
            "var f = new Foo();",
            "r1 = f;"));
  }

  @Test
  public void testCovarianceForRecordType9() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function Foo(){}",
            "/** @type {number} */",
            "Foo.prototype.x1 = 5",
            "/** @type {string} */",
            "Foo.prototype.y = 'str'"),
        lines(
            "/** @type {{x: number, y: string}} */",
            "var r1 = {x: 1, y: 'value'};",
            "",
            "/** @type {!Foo} */",
            "var f = new Foo();",
            "f = r1;"),
        lines(
            "assignment",
            "found   : {x: number, y: string}",
            "required: Foo"));
  }

  @Test
  public void testCovarianceForRecordType10() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "/** @type {{x: !Foo}} */",
            "Foo.prototype.x = {x: new Foo()};"),
        lines(
            "/** @type {!Foo} */",
            "var o = new Foo();",
            "",
            "/** @type {{x: !Foo}} */",
            "var r = {x : new Foo()};",
            "r = o;"),
        lines(
            "assignment",
            "found   : Foo",
            "required: {x: Foo}",
            "missing : []",
            "mismatch: [x]"));
  }

  @Test
  public void testCovarianceForRecordType11() {
    testTypesWithExtraExterns(
        lines(
            "/** @interface */",
            "function Foo() {}",
            "/** @constructor @implements {Foo} */",
            "function Bar1() {}",
            "/** @return {number} */",
            "Bar1.prototype.y = function(){return 1;};",
            "/** @constructor @implements {Foo} */",
            "function Bar() {}",
            "/** @return {string} */",
            "Bar.prototype.y = function(){return 'test';};"),
        lines(
            "function fun(/** Foo */f) {",
            "  f.y();",
            "}",
            "fun(new Bar1())",
            "fun(new Bar());"),
        STRICT_INEXISTENT_PROPERTY);
  }

  @Test
  public void testCovarianceForRecordType12() {
    testTypesWithExtraExterns(
        lines(
            "/** @interface */",
            "function Foo() {}",
            "/** @constructor @implements {Foo} */",
            "function Bar1() {}",
            "/** @constructor @implements {Foo} */",
            "function Bar() {}",
            "/** @return {undefined} */",
            "Bar.prototype.y = function(){};"),
        lines(
            "/** @type{Foo} */",
            "var f = new Bar1();",
            "f.y();"),
        STRICT_INEXISTENT_PROPERTY); // Only if strict warnings are enabled.
  }

  @Test
  public void testCovarianceForRecordType13() {
    testTypesWithExtraExterns(
        lines(
            "/** @interface */",
            "function I() {}",
            "/** @constructor @implements {I} */",
            "function C() {}",
            "/** @return {undefined} */",
            "C.prototype.y = function(){};"),
        lines(
            "/** @type{{x: {obj: I}}} */",
            "var ri;",
            "ri.x.obj.y();"),
        STRICT_INEXISTENT_PROPERTY); // Only if strict warnings are enabled.
  }

  @Test
  public void testCovarianceForRecordType14a() {
    // Verify loose property check behavior
    disableStrictMissingPropertyChecks();
    testTypesWithExtraExterns(
        lines(
            "/** @interface */",
            "function I() {}",
            "/** @constructor */",
            "function C() {}",
            "/** @return {undefined} */",
            "C.prototype.y = function(){};"),
        lines(
            "/** @type{({x: {obj: I}}|{x: {obj: C}})} */",
            "var ri;",
            "ri.x.obj.y();"));
  }

  @Test
  public void testCovarianceForRecordType14b() {
    testTypesWithExtraExterns(
        lines(
            "/** @interface */",
            "function I() {}",
            "/** @constructor */",
            "function C() {}",
            "/** @return {undefined} */",
            "C.prototype.y = function(){};"),
        lines("/** @type{({x: {obj: I}}|{x: {obj: C}})} */", "var ri;", "ri.x.obj.y();")
        // TODO(johnlenz): enable this.
        // "Property y not defined on all member types of (I|C)"
        );
  }

  @Test
  public void testCovarianceForRecordType15() {
    // Verify loose property check behavior
    disableStrictMissingPropertyChecks();
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C() {}",
            "/** @return {undefined} */",
            "C.prototype.y1 = function(){};",
            "/** @constructor */",
            "function C1() {}",
            "/** @return {undefined} */",
            "C1.prototype.y = function(){};"),
        lines(
            "/** @type{({x: {obj: C}}|{x: {obj: C1}})} */",
            "var ri;",
            "ri.x.obj.y1();",
            "ri.x.obj.y();"));
  }

  @Test
  public void testCovarianceForRecordType16() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C() {}",
            "/** @return {number} */",
            "C.prototype.y = function(){return 1;};",
            "/** @constructor */",
            "function C1() {}",
            "/** @return {string} */",
            "C1.prototype.y = function(){return 'test';};"),
        lines(
            "/** @type{({x: {obj: C}}|{x: {obj: C1}})} */",
            "var ri;",
            "ri.x.obj.y();"));
  }

  @Test
  public void testCovarianceForRecordType17() {
    testTypesWithExtraExterns(
        lines(
            "/** @interface */",
            "function Foo() {}",
            "/** @constructor @implements {Foo} */",
            "function Bar1() {}",
            "Bar1.prototype.y = function(){return {};};",
            "/** @constructor @implements {Foo} */",
            "function Bar() {}",
            "/** @return {number} */",
            "Bar.prototype.y = function(){return 1;};"),
        lines(
            "/** @type {Foo} */ var f;",
            "f.y();"),
        STRICT_INEXISTENT_PROPERTY);
  }

  @Test
  public void testCovarianceForRecordType18() {
    disableStrictMissingPropertyChecks();
    testTypesWithExtraExterns(
        lines(
            "/** @constructor*/",
            "function Bar1() {}",
            "/** @type {{x: number}} */",
            "Bar1.prototype.prop;",
            "/** @constructor */",
            "function Bar() {}",
            "/** @type {{x: number, y: number}} */",
            "Bar.prototype.prop;"),
        lines(
            "/** @type {{x: number}} */ var f;",
            "f.z;"));
  }

  @Test
  public void testCovarianceForRecordType19a() {
    // Verify loose property check behavior
    disableStrictMissingPropertyChecks();
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function Bar1() {}",
            "/** @type {number} */",
            "Bar1.prototype.prop;",
            "/** @type {number} */",
            "Bar1.prototype.prop1;",
            "/** @constructor */",
            "function Bar2() {}",
            "/** @type {number} */",
            "Bar2.prototype.prop;"),
        lines(
            "/** @type {(Bar1|Bar2)} */ var b;",
            "var x = b.prop1"));
  }

  @Test
  public void testCovarianceForRecordType19b() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function Bar1() {}",
            "/** @type {number} */",
            "Bar1.prototype.prop;",
            "/** @type {number} */",
            "Bar1.prototype.prop1;",
            "/** @constructor */",
            "function Bar2() {}",
            "/** @type {number} */",
            "Bar2.prototype.prop;"),
        lines("/** @type {(Bar1|Bar2)} */ var b;", "var x = b.prop1")
        // TODO(johnlenz): enable this.
        // "Property prop1 not defined on all member types of (Bar1|Bar2)"
        );
  }

  @Test
  public void testCovarianceForRecordType20() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function Bar1() {}",
            "/** @type {number} */",
            "Bar1.prototype.prop;",
            "/** @type {number} */",
            "Bar1.prototype.prop1;",
            "/** @type {number} */",
            "Bar1.prototype.prop2;"),
        lines(
            "/** @type {{prop2:number}} */ var c;",
            "/** @type {(Bar1|{prop:number, prop2: number})} */ var b;",
            // there should be no warning saying that
            // prop2 is not defined on b;
            "var x = b.prop2"));
  }

  @Test
  public void testCovarianceForRecordType20_2() {
    testTypesWithExtraExterns(
        "",
        lines(
            "/** @type {{prop2:number}} */ var c;",
            "/** @type {({prop:number, prop1: number, prop2: number}|",
            "{prop:number, prop2: number})} */ var b;",
            // there should be no warning saying that
            // prop2 is not defined on b;
            "var x = b.prop2"));
  }

  @Test
  public void testOptimizePropertyMap1() {
    // For non object-literal types such as Function, the behavior doesn't change.
    // The stray property is added as unknown.
    testTypes(lines(
        "/** @return {!Function} */",
        "function f() {",
        "  var x = function() {};",
        "  /** @type {number} */",
        "  x.prop = 123;",
        "  return x;",
        "}",
        "function g(/** !Function */ x) {",
        "  var /** null */ n = x.prop;",
        "}"),
        "Property prop never defined on Function");
  }

  @Test
  public void testOptimizePropertyMap1b() {
    disableStrictMissingPropertyChecks();

    // For non object-literal types such as Function, the behavior doesn't change.
    // The stray property is added as unknown.
    testTypes(lines(
        "/** @return {!Function} */",
        "function f() {",
        "  var x = function() {};",
        "  /** @type {number} */",
        "  x.prop = 123;",
        "  return x;",
        "}",
        "function g(/** !Function */ x) {",
        "  var /** null */ n = x.prop;",
        "}"));
  }

  @Test
  public void testOptimizePropertyMap2() {
    disableStrictMissingPropertyChecks();

    // Don't add the inferred property to all Foo values.
    testTypes(lines(
        "/** @typedef {{a:number}} */",
        "var Foo;",
        "function f(/** !Foo */ x) {",
        "  var y = x;",
        "  /** @type {number} */",
        "  y.b = 123;",
        "}",
        "function g(/** !Foo */ x) {",
        "  var /** null */ n = x.b;",
        "}"),
        "Property b never defined on x");
  }

  @Test
  public void testOptimizePropertyMap2b() {
    // Here strict missing properties warns, do we want it to?
    testTypes(
        lines(
            "/** @typedef {{a:number}} */",
            "var Foo;",
            "function f(/** !Foo */ x) {",
            "  var y = x;",
            "  /** @type {number} */",
            "  y.b = 123;",
            "}"),
        "Property b never defined on y");
  }

  @Test
  public void testOptimizePropertyMap3a() {
    // For @record types, add the stray property to the index as before.
    testTypes(
        lines(
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
            "}"),
        ImmutableList.of(
            "Property b never defined on Foo", // definition
            "Property b never defined on Foo")); // reference
  }

  @Test
  public void testOptimizePropertyMap3b() {
    disableStrictMissingPropertyChecks();
    // For @record types, add the stray property to the index as before.
    testTypes(lines(
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
        "}"));
  }

  @Test
  public void testOptimizePropertyMap4() {
    testTypes(lines(
        "function f(x) {",
        "  var y = { a: 1, b: 2 };",
        "}",
        "function g(x) {",
        "  return x.b + 1;",
        "}"));
  }

  @Test
  public void testOptimizePropertyMap5() {
    // Tests that we don't declare the properties on Object (so they don't appear on
    // all object types).
    testTypes(lines(
        "function f(x) {",
        "  var y = { a: 1, b: 2 };",
        "}",
        "function g() {",
        "  var x = { c: 123 };",
        "  return x.a + 1;",
        "}"),
        "Property a never defined on x");
  }

  @Test
  public void testOptimizePropertyMap6() {
    // Checking loose property behavior
    disableStrictMissingPropertyChecks();

    // The stray property doesn't appear on other inline record types.
    testTypes(lines(
        "function f(/** {a:number} */ x) {",
        "  var y = x;",
        "  /** @type {number} */",
        "  y.b = 123;",
        "}",
        "function g(/** {c:number} */ x) {",
        "  var /** null */ n = x.b;",
        "}"),
        "Property b never defined on x");
  }

  @Test
  public void testOptimizePropertyMap7() {
    testTypes(lines(
        "function f() {",
        "  var x = {a:1};",
        "  x.b = 2;",
        "}",
        "function g() {",
        "  var y = {a:1};",
        "  return y.b + 1;",
        "}"),
        "Property b never defined on y");
  }

  @Test
  public void testOptimizePropertyMap8() {
    testTypes(lines(
        "function f(/** {a:number, b:number} */ x) {}",
        "function g(/** {c:number} */ x) {",
        "  var /** null */ n = x.b;",
        "}"),
        "Property b never defined on x");
  }

  @Test
  public void testOptimizePropertyMap9() {
    // Checking loose property checks behavior
    disableStrictMissingPropertyChecks();

    // Don't add the stray property to all types that meet with {a: number, c: string}.
    testTypes(lines(
        "/** @constructor */",
        "function Foo() {",
        "  this.a = 123;",
        "}",
        "function f(/** {a: number, c: string} */ x) {",
        "  x.b = 234;",
        "}",
        "function g(/** !Foo */ x) {",
        "  return x.b + 5;",
        "}"),
        "Property b never defined on Foo");
  }

  @Test
  public void testCovarianceForRecordType21() {
    testTypesWithExtraExterns(
        "",
        lines(
            "/** @constructor */",
            "function Bar1() {};",
            "/** @type {number} */",
            "Bar1.prototype.propName;",
            "/** @type {number} */",
            "Bar1.prototype.propName1;",
            "/** @type {{prop2:number}} */ var c;",
            "/** @type {(Bar1|{propName:number, propName1: number})} */ var b;",
            "var x = b.prop2;"),
        "Property prop2 never defined on b");
  }

  @Test
  public void testCovarianceForRecordType23() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function A() {}",
            "/** @constructor @extends{A} */",
            "function B() {}",
            "",
            "/** @constructor */",
            "function C() {}",
            "/** @type {B} */",
            "C.prototype.prop2;",
            "/** @type {number} */",
            "C.prototype.prop3;",
            "",
            "/** @constructor */",
            "function D() {}",
            "/** @type {number} */",
            "D.prototype.prop;",
            "/** @type {number} */",
            "D.prototype.prop1;",
            "/** @type {B} */",
            "D.prototype.prop2;"),
        lines(
            "/** @type {{prop2: A}} */ var record;",
            "var xhr = new C();",
            "if (true) { xhr = new D(); }",
            // there should be no warning saying that
            // prop2 is not defined on b;
            "var x = xhr.prop2"));
  }

  @Test
  public void testCovarianceForRecordType24() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C() {}",
            "",
            "/** @type {!Function} */",
            "C.prototype.abort = function() {};",
            "",
            "/** @type{number} */",
            "C.prototype.test2 = 1;"),
        lines(
            "function f() {",
            "  /** @type{{abort: !Function, count: number}} */",
            "  var x;",
            "}",
            "",
            "function f2() {",
            "  /** @type{(C|{abort: Function})} */",
            "  var y;",
            "  y.abort();",
            "}"));
  }

  @Test
  public void testCovarianceForRecordType25() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C() {}",
            "",
            "/** @type {!Function} */",
            "C.prototype.abort = function() {};",
            "",
            "/** @type{number} */",
            "C.prototype.test2 = 1;"),
        lines(
            "function f() {",
            "  /** @type{!Function} */ var f;",
            "  var x = {abort: f, count: 1}",
            "  return x;",
            "}",
            "",
            "function f2() {",
            "  /** @type{(C|{test2: number})} */",
            "  var y;",
            "  y.abort();",
            "}"),
        STRICT_INEXISTENT_PROPERTY);
  }

  @Test
  public void testCovarianceForRecordType26() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C() {}",
            "",
            "C.prototype.abort = function() {};",
            "",
            "/** @type{number} */",
            "C.prototype.test2 = 1;"),
        lines(
            "function f() {",
            "  /** @type{{abort: !Function}} */",
            "  var x;",
            "}",
            "",
            "function f2() {",
            "  /** @type{(C|{test2: number})} */",
            "  var y;",
            "  /** @type {C} */ (y).abort();",
            "}"));
  }

  @Test
  public void testCovarianceForRecordType26AndAHalf() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C() {}",
            "",
            "C.prototype.abort = function() {};",
            "",
            "/** @type{number} */",
            "C.prototype.test2 = 1;",
            "var g = function /** !C */(){};"),
        lines(
            "function f() {",
            "  /** @type{{abort: !Function}} */",
            "  var x;",
            "}",
            "function f2() {",
            "  var y = g();",
            "  y.abort();",
            "}"));
  }

  @Test
  public void testCovarianceForRecordType27() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function C(){}",
            "/** @constructor @extends {C} */",
            "function C2() {}"),
        lines(
            "/** @type {{prop2:C}} */ var c;",
            "/** @type {({prop:number, prop1: number, prop2: C}|",
            "{prop:number, prop1: number, prop2: number})} */ var b;",
            "var x = b.prop2;"));
  }

  @Test
  public void testCovarianceForRecordType28() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function XMLHttpRequest() {}",
            "/**",
            " * @return {undefined}",
            " */",
            "XMLHttpRequest.prototype.abort = function() {};",
            "",
            "/** @constructor */",
            "function XDomainRequest() {}",
            "",
            "XDomainRequest.prototype.abort = function() {};"),
        lines(
            "/**",
            " * @typedef {{abort: !Function, close: !Function}}",
            " */",
            "var WritableStreamSink;",
            "function sendCrossOrigin() {",
            "  var xhr = new XMLHttpRequest;",
            "  xhr = new XDomainRequest;",
            "  return function() {",
            "    xhr.abort();",
            "  };",
            "}"));
  }

  @Test
  public void testCovarianceForRecordType29() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function XMLHttpRequest() {}",
            "/**",
            " * @type {!Function}",
            " */",
            "XMLHttpRequest.prototype.abort = function() {};",
            "",
            "/** @constructor */",
            "function XDomainRequest() {}",
            "/**",
            " * @type {!Function}",
            " */",
            "XDomainRequest.prototype.abort = function() {};"),
        lines(
            "/**",
            " * @typedef {{close: !Function, abort: !Function}}",
            " */",
            "var WritableStreamSink;",
            "function sendCrossOrigin() {",
            "  var xhr = new XMLHttpRequest;",
            "  xhr = new XDomainRequest;",
            "  return function() {",
            "    xhr.abort();",
            "  };",
            "}"));
  }

  @Test
  public void testCovarianceForRecordType30() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function A() {}"
            ),
        lines(
            "/**",
            " * @type {{prop1: (A)}}",
            " */",
            "var r1;",
            "/**",
            " * @type {{prop1: (A|undefined)}}",
            " */",
            "var r2;",
            "r1 = r2"),
        lines(
            "assignment",
            "found   : {prop1: (A|null|undefined)}",
            "required: {prop1: (A|null)}",
            "missing : []",
            "mismatch: [prop1]"));
  }

  @Test
  public void testCovarianceForRecordType31() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */",
            "function A() {}"
            ),
        lines(
            "/**",
            " * @type {{prop1: (A|null)}}",
            " */",
            "var r1;",
            "/**",
            " * @type {{prop1: (A|null|undefined)}}",
            " */",
            "var r2;",
            "r1 = r2"),
        lines(
            "assignment",
            "found   : {prop1: (A|null|undefined)}",
            "required: {prop1: (A|null)}",
            "missing : []",
            "mismatch: [prop1]"));
  }

  @Test
  public void testDuplicateVariableDefinition1() {
    testTypes(
        lines(
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
            "function fun () {}"),
        "variable fun redefined, original definition at [testcode]:14");
  }

  @Test
  public void testDuplicateVariableDefinition3() {
    testTypes(
        lines(
            "var ns = {};",
            "/** @type {{x:number}} */ ns.x;",
            "/** @type {{x:number}} */ ns.x;"));
  }

  @Test
  public void testDuplicateVariableDefinition3_1() {
    testTypes(
        lines(
            "var ns = {};",
            "/** @type {{x:number}} */ ns.x;",
            "/** @type {{x:string}} */ ns.x;"),
        "variable ns.x redefined with type {x: string}, original definition "
        + "at [testcode]:2 with type {x: number}");
  }

  @Test
  public void testDuplicateVariableDefinition3_2() {
    testTypes(
        lines(
            "var ns = {};",
            "/** @type {{x:number}} */ ns.x;",
            "/** @type {{x:number, y:boolean}} */ ns.x;"),
        "variable ns.x redefined with type {x: number, y: boolean}, "
        + "original definition at [testcode]:2 with type {x: number}");
  }

  @Test
  public void testDuplicateVariableDefinition4() {
    testTypes(
        lines(
            "var ns = {};",
            "/** @record */ function rec3(){}",
            "/** @record */ function rec4(){}",
            "/** @type {!rec3} */ ns.x;",
            "/** @type {!rec4} */ ns.x;"));
  }

  @Test
  public void testDuplicateVariableDefinition5() {
    testTypes(
        lines(
            "var ns = {};",
            "/** @record */ function rec3(){}",
            "/** @record */ function rec4(){}",
            "/** @type {number} */ rec4.prototype.prop;",
            "/** @type {!rec3} */ ns.x;",
            "/** @type {!rec4} */ ns.x;"),
        "variable ns.x redefined with type rec4, original definition at "
        + "[testcode]:5 with type rec3");
  }

  @Test
  public void testDuplicateVariableDefinition6() {
    testTypes(
        lines(
            "var ns = {};",
            "/** @record */ function rec3(){}",
            "/** @type {number} */ rec3.prototype.prop;",
            "/** @record */ function rec4(){}",
            "/** @type {!rec3} */ ns.x;",
            "/** @type {!rec4} */ ns.x;"),
        "variable ns.x redefined with type rec4, original definition at "
        + "[testcode]:5 with type rec3");
  }

  /** check bug fix 22713201 (the first case) */
  @Test
  public void testDuplicateVariableDefinition7() {
    testTypes(
        lines(
            "/** @typedef {{prop:TD2}} */",
            "  var TD1;",
            "",
            "  /** @typedef {{prop:TD1}} */",
            "  var TD2;",
            "",
            "  var /** TD1 */ td1;",
            "  var /** TD2 */ td2;",
            "",
            "  td1 = td2;"));
  }

  @Test
  public void testDuplicateVariableDefinition8() {
    testTypes(
        lines(
            "var ns = {}",
            "/** @record */ function rec(){}",
            "/** @type {number} */ rec.prototype.prop;",
            "",
            "/** @type {!rec} */ ns.x;",
            "/** @type {{prop:number}} */ ns.x;",
            "",
            "/** @type {{prop:number}} */ ns.y;",
            "/** @type {!rec} */ ns.y;"));
  }

  @Test
  public void testDuplicateVariableDefinition8_2() {
    testTypes(
        lines(
            "var ns = {}",
            "/** @record */ function rec(){}",
            "/** @type {number} */ rec.prototype.prop;",
            "",
            "/** @type {!rec} */ ns.x;",
            "/** @type {{prop:string}} */ ns.x;",
            "",
            "/** @type {{prop:number}} */ ns.y;",
            "/** @type {!rec} */ ns.y;"),
        "variable ns.x redefined with type {prop: string}, original "
        + "definition at [testcode]:5 with type rec");
  }

  @Test
  public void testDuplicateVariableDefinition8_3() {
    testTypes(
        lines(
            "var ns = {}",
            "/** @record */ function rec(){}",
            "/** @type {string} */ rec.prototype.prop;",
            "",
            "/** @type {!rec} */ ns.x;",
            "/** @type {{prop:string}} */ ns.x;",
            "",
            "/** @type {{prop:number}} */ ns.y;",
            "/** @type {!rec} */ ns.y;"),
        "variable ns.y redefined with type rec, original definition at "
        + "[testcode]:8 with type {prop: number}");
  }

  @Test
  public void testDuplicateVariableDefinition8_4() {
    testTypes(
        lines(
            "/** @record @template T */ function I() {}",
            "/** @type {T} */ I.prototype.prop;",
            "var ns = {}",
            "/** @record */ function rec(){}",
            "/** @type {I} */ rec.prototype.prop;",
            "",
            "/** @type {!rec} */ ns.x;",
            "/** @type {{prop:I}} */ ns.x;"));
  }

  @Test
  public void testDuplicateVariableDefinition8_5() {
    testTypes(
        lines(
            "/** @record @template T */ function I() {}",
            "/** @type {T} */ I.prototype.prop;",
            "var ns = {}",
            "/** @record */ function rec(){}",
            "/** @type {I<number>} */ rec.prototype.prop;",
            "",
            "/** @type {!rec} */ ns.x;",
            "/** @type {{prop:I<number>}} */ ns.x;"));
  }

  @Test
  public void testDuplicateVariableDefinition8_6() {
    testTypes(
        lines(
            "/** @record @template T */ function I() {}",
            "/** @type {T} */ I.prototype.prop;",
            "var ns = {}",
            "/** @record */ function rec(){}",
            "/** @type {I<number>} */ rec.prototype.prop;",
            "",
            "/** @type {!rec} */ ns.x;",
            "/** @type {{prop:I<string>}} */ ns.x;"),
        "variable ns.x redefined with type {prop: (I<string>|null)}, "
        + "original definition at [testcode]:7 with type rec");
  }

  // should have no warning, need to handle equivalence checking for
  // structural types with template types
  @Test
  public void testDuplicateVariableDefinition8_7() {
    testTypes(
        lines(
            "/** @record @template T */",
            "function rec(){}",
            "/** @type {T} */ rec.prototype.value;",
            "",
            "/** @type {rec<string>} */ ns.x;",
            "/** @type {{value: string}} */ ns.x;"),
        "variable ns.x redefined with type {value: string}, "
        + "original definition at [testcode]:5 with type (null|rec<string>)");
  }

  @Test
  public void testModuloNullUndefThatWorkedWithoutSpecialSubtypingRules1() {
    testTypes(lines(
        "/** @constructor */",
        "function Foo() {}",
        "function f(/** function(?Foo, !Foo) */ x) {",
        "  return /** @type {function(!Foo, ?Foo)} */ (x);",
        "}"));
  }

  @Test
  public void testModuloNullUndefThatWorkedWithoutSpecialSubtypingRules2() {
    testTypes(lines(
        "/** @constructor */",
        "function Foo() {}",
        "function f(/** !Array<!Foo> */ to, /** !Array<?Foo> */ from) {",
        "  to = from;",
        "}"));
  }

  @Test
  public void testModuloNullUndefThatWorkedWithoutSpecialSubtypingRules3() {
    testTypes(lines(
        "function f(/** ?Object */ x) {",
        "  return {} instanceof x;",
        "}"));
  }

  @Test
  public void testModuloNullUndefThatWorkedWithoutSpecialSubtypingRules4() {
    testTypes(lines(
        "function f(/** ?Function */ x) {",
        "  return x();",
        "}"));
  }

  @Test
  public void testEs5ClassExtendingEs6Class() {
    testTypes(
        lines(
            "class Foo {}",
            "/** @constructor @extends {Foo} */ var Bar = function() {};"),
        "ES5 class Bar cannot extend ES6 class Foo");
  }

  @Test
  public void testEs5ClassExtendingEs6Class_noWarning() {
    testTypes(
        lines(
            "class A {}",
            "/** @constructor @extends {A} */",
            "const B = createSubclass(A);"));
  }

  @Test
  public void testNonNullTemplatedThis() {
    testTypes(
       lines(
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
           "/** @type {string} */ var s = f().method();"),
       "initializing variable\n" +
       "found   : C\n" +
       "required: string");
  }

  // Github issue #2222: https://github.com/google/closure-compiler/issues/2222
  @Test
  public void testSetPrototypeToNewInstance() {
    testTypes(
        lines(
            "/** @constructor */",
            "function C() {}",
            "C.prototype = new C;"));
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
            "initializing variable",
            "found   : (NoResolvedType|null)",
            "required: number"));
  }

  @Test
  public void testNoResolvedTypeDoesntCauseInfiniteLoop() {
    testClosureTypes(lines(
        "goog.forwardDeclare('Foo');",
        "goog.forwardDeclare('Bar');",
        "/** @const */",
        "var goog = {};",
        "goog.forwardDeclare = function(x) {};",
        "/** @const */",
        "goog.asserts = {};",
        "goog.asserts.assert = function(x, y) {};",
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
    testTypes(
        lines("",
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
            "var /** null */ n = getValueFromNameAndMap(m);"),
        lines(
            "initializing variable",
            "found   : number",
            "required: null"));
  }

  @Test
  public void testLocalType1() {
    testTypes(
        lines(
            "/** @constructor */ function C(){ /** @const */ this.a = true;}",
            "function f() {",
            "  // shadow",
            "  /** @constructor */ function C(){ /** @const */ this.a = 1;}",
            "}"
        )
    );
  }

  @Test
  public void testLocalType2() {
    testTypes(
        lines(
            "/** @constructor */ function C(){ /** @const */ this.a = true;}",
            "function f() {",
            "  // shadow",
            "  /** @constructor */ function C(){ /** @const */ this.a = 1;}",
            "  /** @type {null} */ var x = new C().a;",
            "}"
        ),
        lines(
            "initializing variable",
            "found   : number",
            "required: null")
    );
  }

  @Test
  public void testForwardDecl1() {
    testTypes(
        lines(
            "/** @const */ var ns = {};",
            "/** @constructor */ ns.Outer = function C() {};",
            "/** @return {!ns.Outer.Inner} */",
            "ns.Outer.prototype.method = function() {",
            "  return new ns.Outer.Inner();",
            "};",
            "/** @constructor */ ns.Outer.Inner = function () {};"
        )
    );
  }

  @Test
  public void testForwardDecl2() {
    testTypes(
        lines(
            "/** @const */ var ns1 = {};",
            "/** @const */ ns1.ns2 = {};",
            "/** @constructor */ ns1.ns2.Outer = function C() {};",
            "/** @return {!ns1.ns2.Outer.Inner} */",
            "ns1.ns2.Outer.prototype.method = function() {",
            "  return new ns1.ns2.Outer.Inner();",
            "};",
            "/** @constructor */ ns1.ns2.Outer.Inner = function () {};"
        )
    );
  }

  @Test
  public void testMissingPropertiesWarningOnObject1() {
    testTypes(lines(
        "/** @constructor */",
        "function Foo() {",
        "  this.prop = 123;",
        "}",
        "/** @param {!Object} x */",
        "function f(x) {",
        "  return x.prop;",
        "}"),
        "Property prop never defined on Object");
  }

  @Test
  public void testStrictNumericOperators1() {
    testTypes(
        "var x = 'asdf' - 1;",
        lines(
            "left operand",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testStrictNumericOperators2() {
    testTypes(
        "var x = 1 - 'asdf';",
        lines(
            "right operand",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testStrictNumericOperators3() {
    testTypes(
        "var x = 'asdf'; x++;",
        lines(
            "increment/decrement",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testStrictNumericOperators4a() {
    testTypes(
        "var x = -'asdf';",
        lines(
            "sign operator",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testStrictNumericOperators4b() {
    testTypes("var x = +'asdf';");
  }

  @Test
  public void testStrictNumericOperators5() {
    testTypes(
        "var x = 1 < 'asdf';",
        lines(
            "right side of numeric comparison",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testStrictNumericOperators6() {
    testTypes(
        "var x = 'asdf'; x *= 2;",
        lines(
            "left operand",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testStrictNumericOperators7() {
    testTypes(
        "var x = ~ 'asdf';",
        lines(
            "bitwise NOT",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testStrictNumericOperators8() {
    testTypes(
        "var x = 'asdf' | 1;",
        lines(
            "bad left operand to bitwise operator",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testStrictNumericOperators9() {
    testTypes(
        "var x = 'asdf' << 1;",
        lines(
            "operator <<",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testStrictNumericOperators10() {
    testTypes(
        "var x = 1 >>> 'asdf';",
        lines(
            "operator >>>",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testStrictComparison1() {
    testTypes(
        "var x = true < 'asdf';",
        lines(
            "expected matching types in comparison",
            "found   : string",
            "required: boolean"));
  }

  @Test
  public void testStrictComparison2() {
    testTypes(
        lines(
            "function f(/** (number|string) */ x, /** string */ y) {",
            "  return x < y;",
            "}"));
  }

  @Test
  public void testComparisonInStrictModeNoSpuriousWarning() {
    testTypes(
        lines(
            "function f(x) {",
            "  var y = 'asdf';",
            "  var z = y < x;",
            "}"));
  }

  @Test
  public void testComparisonTreatingUnknownAsNumber() {
    // Because 'x' is unknown, we allow either of the comparible types: number or string.
    compiler.getOptions().setWarningLevel(
        DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    testTypes(
        lines(
        "/** @constructor */",
        "function Foo() {}",
        "function f(/** ? */ x) {",
        "  return (new Foo) < x;",
        "}"),
        lines(
            "left side of comparison",
            "found   : Foo",
            "required: (number|string)"));
  }

  @Test
  public void testComparisonTreatingUnknownAsNumber_leftTypeUnknown() {
    // Because 'x' is unknown, we allow either of the comparible types: number or string.
    compiler.getOptions().setWarningLevel(
        DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.OFF);
    testTypes(
        lines(
        "/** @constructor */",
        "function Bar() {}",
        "function f(/** ? */ x) {",
        "  return x < (new Bar);",
        "}"),
        lines(
            "right side of comparison",
            "found   : Bar",
            "required: (number|string)"));
  }

  @Test
  public void testEnumOfSymbol1() {
    testTypes(
        lines("",
            "/** @enum {symbol} */",
            "var ES = {A: Symbol('a'), B: Symbol('b')};",
            "",
            "/** @type {!Object<ES, number>} */",
            "var o = {};",
            "",
            "o[ES.A] = 1;"));
  }

  @Test
  public void testEnumOfSymbol2() {
    testTypes(
        lines("",
            "/** @enum {symbol} */",
            "var ES = {A: Symbol('a'), B: Symbol('b')};",
            "",
            "/** @type {!Object<number, number>} */",
            "var o = {};",
            "",
            "o[ES.A] = 1;"),
        lines(
            "restricted index type",
            "found   : ES<symbol>",
            "required: number"));
  }

  @Test
  public void testEnumOfSymbol4() {
    testTypes(
        lines("",
            "/** @enum {symbol} */",
            "var ES = {A: Symbol('a'), B: Symbol('b')};",
            "",
            "/** @const */",
            "var o = {};",
            "",
            "o[ES.A] = 1;"));
  }

  @Test
  public void testSymbol1() {
    testTypesWithCommonExterns(
        lines("",
            "/** @const */",
            "var o = {};",
            "",
            "if (o[Symbol.iterator]) { /** ok */ };"));
  }

  @Test
  public void testSymbol2() {
    testTypesWithCommonExterns(
        lines("",
            "/** @const */",
            "var o = new Symbol();"),
        "cannot instantiate non-constructor");
  }

  @Test
  public void testUnknownExtends1() {
    testTypes(
        lines(
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
            "}"));
  }

  @Test
  public void testUnknownExtends2() {
    testTypes(
        lines(
            "function f(/** function(new: ?) */ clazz) {",
            "  /**",
            "   * @constructor",
            "   * @extends {clazz}",
            "   */",
            "  function Foo() {}",
            "}"),
        "Could not resolve type in @extends tag of Foo");
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
    testTypes(
        lines(
            MIXIN_DEFINITIONS,
            "/**",
            " * @constructor",
            " * @extends {MyElement}",
            " * @implements {Toggle}",
            " */",
            "var MyElementWithToggle = addToggle(MyElement);",
            "(new MyElementWithToggle).foobar(123);"),
        lines(
            "actual parameter 1 of MyElementWithToggle.prototype.foobar"
                + " does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testMixinApplication2() {
    testTypes(
        lines(
            MIXIN_DEFINITIONS,
            "/**",
            " * @constructor",
            " * @extends {MyElement}",
            " * @implements {Toggle}",
            " */",
            "var MyElementWithToggle = addToggle(MyElement);",
            "(new MyElementWithToggle).elemprop = 123;"),
        lines(
            "assignment to property elemprop of MyElementWithToggle",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testMixinApplication3() {
    testTypes(
        lines(
            MIXIN_DEFINITIONS,
            "var MyElementWithToggle = addToggle(MyElement);",
            "/** @type {MyElementWithToggle} */ var x = 123;"),
        "Bad type annotation. Unknown type MyElementWithToggle");
  }

  @Test
  public void testReassignedSymbolAccessedInClosureIsFlowInsensitive() {
    // This code is technically correct, but the compiler will probably never be able to prove it.
    testTypesWithExterns(
        new TestExternsBuilder().addString().build(),
        lines(
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
            "}"),
        lines(
            "sign operator", // preserve newlines
            "found   : (number|string)",
            "required: number"));
  }

  @Test
  public void testMethodOverriddenDirectlyOnThis() {
    testTypes(
        lines(
            "/** @constructor */",
            "function Foo() {",
            "  this.bar = function() { return 'str'; };",
            "}",
            "/** @return {number} */",
            "Foo.prototype.bar = function() {};"),
        lines(
            "inconsistent return type",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testSuperclassDefinedInBlockUsingVar() {
    testTypes(
        lines(
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
            "};"),
        lines(
            "initializing variable",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testSuperclassDefinedInBlockOnNamespace() {
    testTypes(
        lines(
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
            "};"),
        lines(
            "initializing variable",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testCovariantIThenable1() {
    testTypes(
        lines(
            "/** @type {!IThenable<string|number>} */ var x;",
            "function fn(/** !IThenable<string> */ a ) {",
            "  x = a;",
            "}"));
  }

  @Test
  public void testCovariantIThenable2() {
    testTypes(
        lines(
            "/** @type {!IThenable<string>} */ var x;",
            "function fn(/** !IThenable<string|number> */ a ) {",
            "  x = a;",
            "}"),
        lines(
            "assignment",
            "found   : IThenable<(number|string)>",
            "required: IThenable<string>"));
  }

  @Test
  public void testCovariantIThenable3() {
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Promise<string|number>} */ var x;",
            "function fn(/** !Promise<string> */ a ) {",
            "  x = a;",
            "}"));
  }

  @Test
  public void testCovariantIThenable4() {
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Promise<string>} */ var x;",
            "function fn(/** !Promise<string|number> */ a ) {",
            "  x = a;",
            "}"),
        lines(
            "assignment",
            "found   : Promise<(number|string)>",
            "required: Promise<string>"));
  }

  @Test
  public void testCovariantIThenableNonThenable1() {
    testTypes(
        lines(
            "/** @type {!Array<string>} */ var x;",
            "function fn(/** !IThenable<string> */ a ) {",
            "  x = a;",
            "}"),
        lines(
            "assignment", //
            "found   : IThenable<string>",
            "required: Array<string>"));
  }

  @Test
  public void testCovariantIThenableNonThenable2() {
    testTypes(
        lines(
            "/** @type {!IThenable<string>} */ var x;",
            "function fn(/** !Array<string> */ a ) {",
            "  x = a;",
            "}"),
        lines(
            "assignment", //
            "found   : Array<string>",
            "required: IThenable<string>"));
  }

  @Test
  public void testCovariantIThenableNonThenable3() {
    testTypes(
        lines(
            "/** ",
            "  @constructor",
            "  @template T",
            " */",
            "function C() {}",
            "/** @type {!C<string>} */ var x;",
            "function fn(/** !IThenable<string> */ a ) {",
            "  x = a;",
            "}"),
        lines(
            "assignment", //
            "found   : IThenable<string>",
            "required: C<string>"));
  }

  @Test
  public void testCovariantIThenableNonThenable4() {
    testTypes(
        lines(
            "/** ",
            "  @constructor",
            "  @template T",
            " */",
            "function C() {}",
            "/** @type {!IThenable<string>} */ var x;",
            "function fn(/** !C<string> */ a ) {",
            "  x = a;",
            "}"),
        lines(
            "assignment", //
            "found   : C<string>",
            "required: IThenable<string>"));
  }

  @Test
  public void testShadowedForwardReferenceHoisted() {
    testTypes(
        lines(
            "/** @constructor */ var C = function() { /** @type {string} */ this.prop = 's'; };",
            "var fn = function() {",
            "  /** @type {C} */ var x = f();",
            "  /** @type {number} */ var n1 = x.prop;",
            "  /** @type {number} */ var n2 = new C().prop;",
            "  /** @constructor */ function C() { /** @type {number} */ this.prop = 1; };",
            "  /** @return {C} */ function fn() { return new C(); };",
            "",
            "}"));
  }

  @Test
  public void testTypeDeclarationsShadowOneAnotherWithFunctionScoping() {
    // TODO(b/110538992): Accuracy of shadowing is unclear here. b/110741413 may be confusing the
    // issue.

    // `C` at (2) should refer to `C` at (3) and not `C` at (1). Otherwise the assignment at (4)
    // would be invalid.
    testTypes(
        lines(
            "/** @constructor */",
            "var A = function() { };",
            "",
            "/**",
            " * @constructor",
            " * @extends {A}",
            " */",
            "var C = function() { };", // (1)
            "",
            "var fn = function() {",
            "  /** @type {?C} */ var innerC;", // (2)
            "",
            "  /** @constructor */",
            "  var C = function() { };", // (3)
            "",
            "  innerC = new C();", // (4)
            "}"));
  }

  @Test
  public void testTypeDeclarationsShadowOneAnotherWithFunctionScopingConsideringHoisting() {
    // TODO(b/110538992): Accuracy of shadowing is unclear here. b/110741413 may be confusing the
    // issue.

    // `C` at (3) should refer to `C` at (2) and not `C` at (1). Otherwise return the value at (4)
    // would be invalid.
    testTypes(
        lines(
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
            "}"));
  }

  @Test
  public void testRefinedTypeInNestedShortCircuitingAndOr() {
    // Ensure we don't have a strict property warning, and do get the right type.
    // In particular, ensure we don't forget about tracking refinements in between the two.
    testTypes(
        lines(
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
            "}"),
        lines(
            "inconsistent return type", //
            "found   : (number|string)",
            "required: null"));
  }

  @Test
  public void testTypeofType_notInScope() {
    testTypes("var /** typeof ns */ x;", "Parse error. Not in scope: ns");
  }

  @Test
  public void testTypeofType_namespace() {
    testTypes(
        lines(
            "/** @const */ var ns = {};", //
            "var /** typeof ns */ x = ns;"));
  }

  @Test
  public void testTypeofType_namespaceMismatch() {
    testTypes(
        lines(
            "/** @const */ var ns = {};", //
            "var /** typeof ns */ x = {};"),
        lines(
            "initializing variable", //
            "found   : {}",
            "required: {}"));
  }

  @Test
  public void testTypeofType_constructor() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        lines(
            "/** @constructor */ function Foo() {}", //
            "var /** !Array<typeof Foo> */ x = [];",
            "x.push(Foo);"));
  }

  @Test
  public void testTypeofType_constructorMismatch1() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        lines(
            "/** @constructor */ function Foo() {}", //
            "var /** !Array<(typeof Foo)> */ x = [];",
            "x.push(new Foo());"),
        lines(
            "actual parameter 1 of Array.prototype.push does not match formal parameter",
            "found   : Foo",
            "required: function(new:Foo): undefined"));
  }

  @Test
  public void testTypeofType_constructorMismatch2() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        lines(
            "/** @constructor */ function Foo() {}",
            "var /** !Array<!Foo> */ x = [];",
            "var /** typeof Foo */ y = Foo;",
            "x.push(y);"),
        lines(
            "actual parameter 1 of Array.prototype.push does not match formal parameter",
            "found   : function(new:Foo): undefined",
            "required: Foo"));
  }

  @Test
  public void testTypeofType_enum() {
    testTypes(
        lines(
            "/** @enum */ var Foo = {A: 1}", //
            "var /** typeof Foo */ x = Foo;"));
  }

  @Test
  public void testTypeofType_enumMismatch1() {
    testTypes(
        lines("/** @enum */ var Foo = {A: 1}", "var /** typeof Foo */ x = Foo.A;"),
        lines(
            "initializing variable", //
            "found   : Foo<number>",
            "required: enum{Foo}"));
  }

  @Test
  public void testTypeofType_enumMismatch2() {
    testTypes(
        lines(
            "/** @enum */ var Foo = {A: 1}",
            "/** @enum */ var Bar = {A: 1}",
            "var /** typeof Foo */ x = Bar;"),
        lines(
            "initializing variable", //
            "found   : enum{Bar}",
            "required: enum{Foo}"));
  }

  @Test
  public void testTypeofType_castNamespaceIncludesPropertiesFromTypeofType() {
    testTypes(
        lines(
            "/** @const */ var ns1 = {};",
            "/** @type {string} */ ns1.foo;",
            "/** @const */ var ns2 = {};",
            "/** @type {number} */ ns2.bar;",
            "",
            "/** @const {typeof ns2} */ var ns = /** @type {?} */ (ns1);",
            "/** @type {null} */ var x = ns.bar;"),
        lines(
            "initializing variable", //
            "found   : number",
            "required: null"));
  }

  @Test
  public void testTypeofType_castNamespaceDoesNotIncludeOwnProperties() {
    testTypes(
        lines(
            "/** @const */ var ns1 = {};",
            "/** @type {string} */ ns1.foo;",
            "/** @const */ var ns2 = {};",
            "/** @type {number} */ ns2.bar;",
            "",
            "/** @const {typeof ns2} */ var ns = /** @type {?} */ (ns1);",
            "/** @type {null} */ var x = ns.foo;"),
        "Property foo never defined on ns");
  }

  @Test
  public void testTypeofType_namespaceTypeIsAnAliasNotACopy() {
    testTypes(
        lines(
            "/** @const */ var ns1 = {};",
            "/** @type {string} */ ns1.foo;",
            "",
            "/** @const {typeof ns1} */ var ns = /** @type {?} */ (x);",
            "",
            "/** @type {string} */ ns1.bar;",
            "",
            "/** @type {null} */ var x = ns.bar;"),
        lines(
            "initializing variable", //
            "found   : string",
            "required: null"));
  }

  @Test
  public void testTypeofType_namespacedTypeNameResolves() {
    testTypes(
        lines(
            "/** @const */ var ns1 = {};",
            "/** @constructor */ ns1.Foo = function() {};",
            "/** @const {typeof ns1} */ var ns = /** @type {?} */ (x);",
            "/** @type {!ns.Foo} */ var x = null;"),
        lines(
            "initializing variable", //
            "found   : null",
            "required: ns1.Foo"));
  }

  @Test
  public void testTypeofType_withGlobalDeclaredVariableWithHoistedFunction() {
    // TODO(sdh): fix this.  This is another form of problems with partially constructed scopes.
    // "x" is in scope but because "g" is hoisted its type is created before "x" is introduced
    // to the scope.
    testTypes(
        lines(
            "/** @type {string|number} */",
            "var x = 1;",
            "function g(/** typeof x */ a) {}",
            "x = 'str';",
            "g(null);"),
        lines("Parse error. Not in scope: x"));
  }

  @Test
  public void testTypeofType_withGlobalDeclaredVariableWithFunction() {
    testTypes(
        lines(
            "/** @type {string|number} */",
            "var x = 1;",
            "let g = function(/** typeof x */ a) {}",
            "x = 'str';",
            "g(null);"),
        lines(
            "actual parameter 1 of g does not match formal parameter", //
            "found   : null",
            "required: (number|string)"));
  }

  @Test
  public void testTypeofType_withGlobalInferredVariableWithFunctionExpression() {
    testTypes(
        lines(
            "var x = 1;", //
            "var g = function (/** typeof x */ a) {}",
            "x = 'str';",
            "g(null);"),
        lines("Parse error. No type for: x"));
  }

  @Test
  public void testTypeofType_withLocalInferredVariableInHoistedFunction() {
    // TODO(sdh): fix this.  This is another form of problems with partially constructed scopes.
    // "x" is in scope but because "g" is hoisted its type is created before "x" is introduced
    // to the scope.
    testTypes(
        lines(
            "function f() {",
            "  var x = 1;",
            "  function g(/** typeof x */ a) {}",
            "  x = 'str';",
            "  g(null);",
            "}"),
        lines("Parse error. Not in scope: x"));
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariableWithHoistedFunction() {
    // TODO(sdh): fix this.  This is another form of problems with partially constructed scopes.
    // "x" is in scope but because "g" is hoisted its type is created before "x" is introduced
    // to the scope.
    testTypes(
        lines(
            "function f() {",
            "  /** @type {string|number} */",
            "  var x = 1;",
            "  function g(/** typeof x */ a) {}",
            "  x = 'str';",
            "  g(null);",
            "}"),
        lines("Parse error. Not in scope: x"));
  }

  @Test
  public void testTypeofType_withLocalInferredVariableWithFunctionExpression() {
    testTypes(
        lines(
            "function f() {",
            "  var x = 1;",
            "  var g = function (/** typeof x */ a) {}",
            "  x = 'str';",
            "  g(null);",
            "}"),
        lines("Parse error. No type for: x"));
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariableWithFunctionExpression() {
    testTypes(
        lines(
            "function f() {",
            "  /** @type {string|number} */",
            "  var x = 1;",
            "  var g = function (/** typeof x */ a) {}",
            "  x = 'str';",
            "  g(null);",
            "}"),
        lines(
            "actual parameter 1 of g does not match formal parameter", //
            "found   : null",
            "required: (number|string)"));
  }

  @Test
  public void testTypeofType_withLocalInferredVariable() {
    testTypes(
        lines(
            "function f() {",
            "  var x = 1;",
            "  /** @type {typeof x} */",
            "  var g = null",
            "  x = 'str';",
            "}"),
        lines("Parse error. No type for: x"));
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariable1() {
    testTypes(
        lines(
            "function f() {",
            "  /** @type {string|number} */",
            "  var x = 1;",
            "  /** @type {typeof x} */",
            "  var g = null",
            "  x = 'str';",
            "}"),
        lines(
            "initializing variable", //
            "found   : null",
            "required: (number|string)"));
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariableAfterReassignment() {
    testTypes(
        lines(
            "function f() {",
            "  /** @type {string|number} */",
            "  var x = 1;",
            "  x = 'str';",
            "  /** @type {typeof x} */",
            "  var g = null",
            "}"),
        lines(
            "initializing variable", //
            "found   : null",
            "required: (number|string)"));
  }

  @Test
  public void testTypeofType_withLocalDeclaredVariableAfterTightening() {
    testTypes(
        lines(
            "function f() {",
            "  /** @type {string|number} */",
            "  var x = 'str';",
            "  if (typeof x == 'string') {",
            "    /** @type {typeof x} */",
            "    let g = null",
            "  }",
            "}"),
        lines(
            "initializing variable", //
            "found   : null",
            "required: (number|string)"));
  }

  @Test
  public void testTypeNameAliasOnAliasedNamespace() {
    testTypes(
        lines(
            "class Foo {};",
            "/** @enum {number} */ Foo.E = {A: 1};",
            "const F = Foo;",
            "const E = F.E;",
            "/** @type {E} */ let e = undefined;",
            ""),
        lines(
            "initializing variable", //
            "found   : undefined",
            // TODO(johnlenz): this should not be nullable
            "required: (Foo.E<number>|null)"));
  }

  @Test
  public void testAssignOp() {
    testTypes(
        lines(
            "function fn(someUnknown) {",
            "  var x = someUnknown;",
            "  x *= 2;", // infer 'x' to now be 'number'
            "  var /** null */ y = x;",
            "}"),
        lines(
            "initializing variable",
            "found   : number",
            "required: null"));
  }

  @Test
  public void testAnnotatedObjectLiteralInBlocklessArrow1() {
    testTypes(
        lines(
            "function f(/** {g: function(number): undefined} */ x) {}",
            "() => f({/** @param {string} x */ g(x) {}});"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : {g: function(string): undefined}",
            "required: {g: function(number): undefined}",
            "missing : []",
            "mismatch: [g]"));
  }

  @Test
  public void testAnnotatedObjectLiteralInBlocklessArrow2() {
    testTypes(
        lines(
            "function f(/** {g: function(): string} */ x) {}",
            "() => f({/** @constructor */ g: function() {}});"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : {g: function(new:<anonymous@[testcode]:2>): undefined}",
            "required: {g: function(): string}",
            "missing : []",
            "mismatch: [g]"));
  }

  @Test
  public void testAnnotatedObjectLiteralInBlocklessArrow3() {
    testTypes(
        lines(
            "/** @constructor */ function G() {}",
            "function f(/** {g: function(): string} */ x) {}",
            "() => f({/** @constructor */ g: G});"),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : {g: function(new:G): undefined}",
            "required: {g: function(): string}",
            "missing : []",
            "mismatch: [g]"));
  }

  @Test
  public void testNativePromiseTypeWithExterns() {
    // Test that we add Promise prototype properties defined in externs to the native Promise type
    testTypesWithCommonExterns(
        lines(
            "var p = new Promise(function(resolve, reject) { resolve(3); });",
            "p.then(result => {}, error => {});"));
  }

  @Test
  public void testNativeThenableType() {
    // Test that the native Thenable type is not nullable even without externs
    testTypes(
        "var /** Thenable */ t = null;",
        lines(
            "initializing variable", // preserve newline
            "found   : null",
            "required: {then: ?}"));
  }

  @Test
  public void testFunctionOnObjLitWithAngleBracketKey() {
    testTypes(
        lines(
            "/** @const {!Object<function()>} */", //
            "var x = {'<': function() {}};"));
  }

  @Test
  public void testFunctionOnObjLitWithAngleBracketKeyChecked() {
    testTypes(
        lines(
            "/** @const {!Object<function(): number>} */", //
            "var x = {'<': function() { return 'str'; }};"));
  }

  @Test
  public void testFunctionOnObjLitWithAngleBracketKeyAndJsdoc() {
    testTypes(
        lines(
            "/** @const */", //
            "var x = {",
            "  /** @param {number} arg */",
            "  '<': function(arg) {},",
            "};"));
  }

  @Test
  public void testClassOnObjLitWithAngleBracketKey() {
    testTypes(
        lines(
            "/** @const */", //
            "var x = {",
            "  /** @constructor */",
            "  '<': function() {},",
            "};"));
  }

  @Test
  public void testQuotedPropertyWithDotInType() {
    testTypes(
        lines(
            "/** @const */", //
            "var x = {",
            "  /** @constructor */",
            "  'y.A': function() {},",
            "};",
            "var /** x.y.A */ a;"),
        "Bad type annotation. Unknown type x.y.A");
  }

  @Test
  public void testQuotedPropertyWithDotInCode() {
    testTypes(
        lines(
            "/** @const */", //
            "var x = {",
            "  /** @constructor */",
            "  'y.A': function() {},",
            "};",
            "var a = new x.y.A();"),
        new String[] {
          "Property y never defined on x",
          "Property A never defined on x.y",
        });
  }

  @Test
  public void testClassTemplateParamsInMethodBody() {
    testTypes(
        lines(
            "/** @constructor @template T */",
            "function Foo() {}",
            "Foo.prototype.foo = function() {",
            "  var /** T */ x;",
            "};"));
  }

  @Test
  public void testClassTtlParamsInMethodBody() {
    testTypes(
        lines(
            "/** @constructor @template T := 'number' =: */",
            "function Foo() {}",
            "Foo.prototype.foo = function() {",
            "  var /** T */ x;",
            "};"),
        new String[] {
          "Template type transformation T not allowed on classes or interfaces",
          "Bad type annotation. Unknown type T",
        });
  }

  @Test
  public void testClassTtlParamsInMethodSignature() {
    testTypes(
        lines(
            "/** @constructor @template T := 'number' =: */",
            "function Foo() {}",
            "/** @return {T} */",
            "Foo.prototype.foo = function() {};"),
        new String[] {
          "Template type transformation T not allowed on classes or interfaces",
          "Bad type annotation. Unknown type T",
        });
  }

  @Test
  public void testFunctionTemplateParamsInBody() {
    testTypes(
        lines(
            "/** @template T */",
            "function f(/** !Array<T> */ arr) {",
            "  var /** T */ first = arr[0];",
            "}"));
  }

  @Test
  public void testFunctionTtlParamsInBody() {
    testTypes(
        lines(
            "/** @template T := 'number' =: */",
            "function f(/** !Array<T> */ arr) {",
            "  var /** T */ first = arr[0];",
            "}"));
  }

  @Test
  public void testFunctionTemplateParamsInBodyMismatch() {
    testTypes(
        lines(
            "/** @template T, V */",
            "function f(/** !Array<T> */ ts, /** !Array<V> */ vs) {",
            "  var /** T */ t = vs[0];",
            "}"));
    // TODO(b/35241823): This should be an error, but we currently treat generic types
    // as unknown. Once we have bounded generics we can treat templates as unique types.
    // lines(
    //     "actual parameter 1 of f does not match formal parameter",
    //     "found   : V",
    //     "required: T"));
  }

  @Test
  public void testClassAndMethodTemplateParamsInMethodBody() {
    testTypes(
        lines(
            "/** @constructor @template T */",
            "function Foo() {}",
            "/** @template U */",
            "Foo.prototype.foo = function() {",
            "  var /** T */ x;",
            "  var /** U */ y;",
            "};"));
  }

  @Test
  public void testNestedFunctionTemplates() {
    testTypes(
        lines(
            "/** @constructor @template A, B */",
            "function Foo(/** A */ a, /** B */ b) {}",
            "/** @template T */",
            "function f(/** T */ t) {",
            "  /** @template S */",
            "  function g(/** S */ s) {",
            "    var /** !Foo<T, S> */ foo = new Foo(t, s);",
            "  }",
            "}"));
  }

  @Test
  public void testNestedFunctionTemplatesMismatch() {
    testTypes(
        lines(
            "/** @constructor @template A, B */",
            "function Foo(/** A */ a, /** B */ b) {}",
            "/** @template T */",
            "function f(/** T */ t) {",
            "  /** @template S */",
            "  function g(/** S */ s) {",
            "    var /** !Foo<T, S> */ foo = new Foo(s, t);",
            "  }",
            "}"));
    // TODO(b/35241823): This should be an error, but we currently treat generic types
    // as unknown. Once we have bounded generics we can treat templates as unique types.
    // lines(
    //     "initializing variable",
    //     "found   : Foo<S, T>",
    //     "required: Foo<T, S>"));
  }

  @Test
  public void testTypeCheckingDoesntCrashOnDebuggerStatement() {
    testTypes("var x = 1; debugger; x = 2;");
  }

  @Test
  public void testCastOnLhsOfAssignBlocksBadAssignmentWarning() {
    testTypes("var /** number */ x = 1; (/** @type {?} */ (x)) = 'foobar';");
  }

  @Test
  public void testInvalidComparisonsInStrictOperatorsMode() {
    testTypes(
        "function f(/** (void|string) */ x, /** void */ y) { return x < y; }",
        lines(
            "right side of comparison", //
            "found   : undefined",
            "required: string"));
  }

  @Test
  public void testStrayTypedefOnRecordInstanceDoesNotModifyRecord() {
    // This is a common pattern for angular.  This test is checking that adding the property to one
    // instance does not require all other instances to specify it as well.
    testTypes(
        lines(
            "/** @record */",
            "function Component() {}",
            "",
            "/** @const {!Component} */",
            "var MyComponent = {};",
            "/** @typedef {string} */",
            "MyComponent.MyString;",
            "",
            "/** @const {!Component} */",
            "var OtherComponent = {};"));
  }

  @Test
  public void testStrayPropertyOnRecordInstanceDoesNotModifyRecord() {
    // This is a common pattern for angular.  This test is checking that adding the property to one
    // instance does not require all other instances to specify it as well.
    testTypes(
        lines(
            "/** @record */",
            "function Component() {}",
            "",
            "/** @const {!Component} */",
            "var MyComponent = {};",
            "/** @const {string} */",
            "MyComponent.NAME = 'MyComponent';", // strict inexistent property is usually suppressed
            "",
            "/** @const {!Component} */",
            "var OtherComponent = {};"),
        STRICT_INEXISTENT_PROPERTY);
  }

  @Test
  public void testStrayTypedefOnRecordTypedefDoesNotModifyRecord() {
    // This is a common pattern for angular.  This test is checking that adding the property to one
    // instance does not require all other instances to specify it as well.
    testTypes(
        lines(
            "/** @typedef {{foo: number}} */",
            "var Component = {};",
            "",
            "/** @const {!Component} */",
            "var MyComponent = {foo: 1};",
            "/** @typedef {string} */",
            "MyComponent.MyString;",
            "",
            "/** @const {!Component} */",
            "var OtherComponent = {foo: 2};"));
  }

  @Test
  public void testStrayPropertyOnRecordTypedefDoesNotModifyRecord() {
    // This is a common pattern for angular.  This test is checking that adding the property to one
    // instance does not require all other instances to specify it as well.
    testTypes(
        lines(
            "/** @typedef {{foo: number}} */",
            "var Component = {};",
            "",
            "/** @const {!Component} */",
            "var MyComponent = {foo: 1 };",
            "/** @const {string} */",
            "MyComponent.NAME = 'MyComponent';",
            "",
            "/** @const {!Component} */",
            "var OtherComponent = {foo: 2};"),
        STRICT_INEXISTENT_PROPERTY);
  }

  private void testClosureTypes(String js, String description) {
    testClosureTypesMultipleWarnings(js,
        description == null ? null : ImmutableList.of(description));
  }

  private void testClosureTypesMultipleWarnings(
      String js, List<String> descriptions) {
    compiler.initOptions(compiler.getOptions());
    Node n = compiler.parseTestCode(js);
    Node externs = compiler.parseTestCode(new TestExternsBuilder().addString().build());
    IR.root(externs, n);

    assertWithMessage("parsing error: " + Joiner.on(", ").join(compiler.getErrors()))
        .that(compiler.getErrorCount())
        .isEqualTo(0);

    // For processing goog.addDependency for forward typedefs.
    new ProcessClosurePrimitives(compiler, null, CheckLevel.ERROR, false).process(externs, n);

    new TypeCheck(compiler,
        new ClosureReverseAbstractInterpreter(registry).append(
                new SemanticReverseAbstractInterpreter(registry))
            .getFirst(),
        registry)
        .processForTesting(null, n);

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
        actualWarningDescriptions.add(compiler.getWarnings()[i].description);
      }
      assertThat(actualWarningDescriptions).isEqualTo(new HashSet<>(descriptions));
    }
  }
}
