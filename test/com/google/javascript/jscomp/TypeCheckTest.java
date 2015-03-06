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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.type.ClosureReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.testing.Asserts;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Tests {@link TypeCheck}.
 *
 */

public class TypeCheckTest extends CompilerTypeTestCase {

  private CheckLevel reportMissingOverrides = CheckLevel.WARNING;

  private static final String SUGGESTION_CLASS =
      "/** @constructor\n */\n" +
      "function Suggest() {}\n" +
      "Suggest.prototype.a = 1;\n" +
      "Suggest.prototype.veryPossible = 1;\n" +
      "Suggest.prototype.veryPossible2 = 1;\n";

  @Override
  public void setUp() {
    super.setUp();
    reportMissingOverrides = CheckLevel.WARNING;
  }

  public void testInitialTypingScope() {
    TypedScope s = new TypedScopeCreator(compiler,
        CodingConventions.getDefault()).createInitialScope(
            new Node(Token.BLOCK));

    assertTypeEquals(ARRAY_FUNCTION_TYPE, s.getVar("Array").getType());
    assertTypeEquals(BOOLEAN_OBJECT_FUNCTION_TYPE,
        s.getVar("Boolean").getType());
    assertTypeEquals(DATE_FUNCTION_TYPE, s.getVar("Date").getType());
    assertTypeEquals(ERROR_FUNCTION_TYPE, s.getVar("Error").getType());
    assertTypeEquals(EVAL_ERROR_FUNCTION_TYPE,
        s.getVar("EvalError").getType());
    assertTypeEquals(NUMBER_OBJECT_FUNCTION_TYPE,
        s.getVar("Number").getType());
    assertTypeEquals(OBJECT_FUNCTION_TYPE, s.getVar("Object").getType());
    assertTypeEquals(RANGE_ERROR_FUNCTION_TYPE,
        s.getVar("RangeError").getType());
    assertTypeEquals(REFERENCE_ERROR_FUNCTION_TYPE,
        s.getVar("ReferenceError").getType());
    assertTypeEquals(REGEXP_FUNCTION_TYPE, s.getVar("RegExp").getType());
    assertTypeEquals(STRING_OBJECT_FUNCTION_TYPE,
        s.getVar("String").getType());
    assertTypeEquals(SYNTAX_ERROR_FUNCTION_TYPE,
        s.getVar("SyntaxError").getType());
    assertTypeEquals(TYPE_ERROR_FUNCTION_TYPE,
        s.getVar("TypeError").getType());
    assertTypeEquals(URI_ERROR_FUNCTION_TYPE,
        s.getVar("URIError").getType());
  }

  public void testPrivateType() throws Exception {
    testTypes(
        "/** @private {number} */ var x = false;",
        "initializing variable\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testTypeCheck1() throws Exception {
    testTypes("/**@return {void}*/function foo(){ if (foo()) return; }");
  }

  public void testTypeCheck2() throws Exception {
    testTypes("/**@return {void}*/function foo(){ var x=foo(); x--; }",
        "increment/decrement\n" +
        "found   : undefined\n" +
        "required: number");
  }

  public void testTypeCheck4() throws Exception {
    testTypes("/**@return {void}*/function foo(){ !foo(); }");
  }

  public void testTypeCheck5() throws Exception {
    testTypes("/**@return {void}*/function foo(){ var a = +foo(); }",
        "sign operator\n" +
        "found   : undefined\n" +
        "required: number");
  }

  public void testTypeCheck6() throws Exception {
    testTypes(
        "/**@return {void}*/function foo(){" +
        "/** @type {undefined|number} */var a;if (a == foo())return;}");
  }

  public void testTypeCheck8() throws Exception {
    testTypes("/**@return {void}*/function foo(){do {} while (foo());}");
  }

  public void testTypeCheck9() throws Exception {
    testTypes("/**@return {void}*/function foo(){while (foo());}");
  }

  public void testTypeCheck10() throws Exception {
    testTypes("/**@return {void}*/function foo(){for (;foo(););}");
  }

  public void testTypeCheck11() throws Exception {
    testTypes("/**@type !Number */var a;" +
        "/**@type !String */var b;" +
        "a = b;",
        "assignment\n" +
        "found   : String\n" +
        "required: Number");
  }

  public void testTypeCheck12() throws Exception {
    testTypes("/**@return {!Object}*/function foo(){var a = 3^foo();}",
        "bad right operand to bitwise operator\n" +
        "found   : Object\n" +
        "required: (boolean|null|number|string|undefined)");
  }

  public void testTypeCheck13() throws Exception {
    testTypes("/**@type {!Number|!String}*/var i; i=/xx/;",
        "assignment\n" +
        "found   : RegExp\n" +
        "required: (Number|String)");
  }

  public void testTypeCheck14() throws Exception {
    testTypes("/**@param opt_a*/function foo(opt_a){}");
  }


  public void testTypeCheck15() throws Exception {
    testTypes("/**@type {Number|null} */var x;x=null;x=10;",
        "assignment\n" +
        "found   : number\n" +
        "required: (Number|null)");
  }

  public void testTypeCheck16() throws Exception {
    testTypes("/**@type {Number|null} */var x='';",
              "initializing variable\n" +
              "found   : string\n" +
              "required: (Number|null)");
  }


  public void testTypeCheck17() throws Exception {
    testTypes("/**@return {Number}\n@param {Number} opt_foo */\n" +
        "function a(opt_foo){\nreturn /**@type {Number}*/(opt_foo);\n}");
  }


  public void testTypeCheck18() throws Exception {
    testTypes("/**@return {RegExp}\n*/\n function a(){return new RegExp();}");
  }

  public void testTypeCheck19() throws Exception {
    testTypes("/**@return {Array}\n*/\n function a(){return new Array();}");
  }

  public void testTypeCheck20() throws Exception {
    testTypes("/**@return {Date}\n*/\n function a(){return new Date();}");
  }

  public void testTypeCheckBasicDowncast() throws Exception {
    testTypes("/** @constructor */function foo() {}\n" +
                  "/** @type {Object} */ var bar = new foo();\n");
  }

  public void testTypeCheckNoDowncastToNumber() throws Exception {
    testTypes("/** @constructor */function foo() {}\n" +
                  "/** @type {!Number} */ var bar = new foo();\n",
        "initializing variable\n" +
        "found   : foo\n" +
        "required: Number");
  }

  public void testTypeCheck21() throws Exception {
    testTypes("/** @type Array<String> */var foo;");
  }

  public void testTypeCheck22() throws Exception {
    testTypes("/** @param {Element|Object} p */\nfunction foo(p){}\n" +
                  "/** @constructor */function Element(){}\n" +
                  "/** @type {Element|Object} */var v;\n" +
                  "foo(v);\n");
  }

  public void testTypeCheck23() throws Exception {
    testTypes("/** @type {(Object,Null)} */var foo; foo = null;");
  }

  public void testTypeCheck24() throws Exception {
    testTypes("/** @constructor */function MyType(){}\n" +
        "/** @type {(MyType,Null)} */var foo; foo = null;");
  }


  public void testTypeCheck25() throws Exception {
    testTypes("function foo(/** {a: number} */ obj) {};"
        + "foo({b: 'abc'});",
        "actual parameter 1 of foo does not match formal parameter\n" +
            "found   : {a: (number|undefined), b: string}\n" +
            "required: {a: number}");
  }

  public void testTypeCheck26() throws Exception {
    testTypes("function foo(/** {a: number} */ obj) {};"
        + "foo({a: 'abc'});",
        "actual parameter 1 of foo does not match formal parameter\n"
        + "found   : {a: (number|string)}\n"
        + "required: {a: number}");

  }

  public void testTypeCheck27() throws Exception {
    testTypes("function foo(/** {a: number} */ obj) {};"
        + "foo({a: 123});");
  }

  public void testTypeCheck28() throws Exception {
    testTypes("function foo(/** ? */ obj) {};"
        + "foo({a: 123});");
  }

  public void testTypeCheckInlineReturns() throws Exception {
    testTypes(
        "function /** string */ foo(x) { return x; }" +
        "var /** number */ a = foo('abc');",
        "initializing variable\n"
        + "found   : string\n"
        + "required: number");
  }

  public void testTypeCheckDefaultExterns() throws Exception {
    testTypes("/** @param {string} x */ function f(x) {}" +
        "f([].length);" ,
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testTypeCheckCustomExterns() throws Exception {
    testTypes(
        DEFAULT_EXTERNS + "/** @type {boolean} */ Array.prototype.oogabooga;",
        "/** @param {string} x */ function f(x) {}" +
        "f([].oogabooga);" ,
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: string", false);
  }


  public void testTypeCheckCustomExterns2() throws Exception {
    testTypes(
        DEFAULT_EXTERNS + "/** @enum {string} */ var Enum = {FOO: 1, BAR: 1};",
        "/** @param {Enum} x */ function f(x) {} f(Enum.FOO); f(true);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: Enum<string>",
        false);
  }

  public void testTemplatizedArray1() throws Exception {
    testTypes("/** @param {!Array<number>} a\n" +
        "* @return {string}\n" +
        "*/ var f = function(a) { return a[0]; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testTemplatizedArray2() throws Exception {
    testTypes("/** @param {!Array<!Array<number>>} a\n" +
        "* @return {number}\n" +
        "*/ var f = function(a) { return a[0]; };",
        "inconsistent return type\n" +
        "found   : Array<number>\n" +
        "required: number");
  }

  public void testTemplatizedArray3() throws Exception {
    testTypes("/** @param {!Array<number>} a\n" +
        "* @return {number}\n" +
        "*/ var f = function(a) { a[1] = 0; return a[0]; };");
  }

  public void testTemplatizedArray4() throws Exception {
    testTypes("/** @param {!Array<number>} a\n" +
        "*/ var f = function(a) { a[0] = 'a'; };",
        "assignment\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testTemplatizedArray5() throws Exception {
    testTypes("/** @param {!Array<*>} a\n" +
        "*/ var f = function(a) { a[0] = 'a'; };");
  }

  public void testTemplatizedArray6() throws Exception {
    testTypes("/** @param {!Array<*>} a\n" +
        "* @return {string}\n" +
        "*/ var f = function(a) { return a[0]; };",
        "inconsistent return type\n" +
        "found   : *\n" +
        "required: string");
  }

  public void testTemplatizedArray7() throws Exception {
    testTypes("/** @param {?Array<number>} a\n" +
        "* @return {string}\n" +
        "*/ var f = function(a) { return a[0]; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testTemplatizedObject1() throws Exception {
    testTypes("/** @param {!Object<number>} a\n" +
        "* @return {string}\n" +
        "*/ var f = function(a) { return a[0]; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testTemplatizedObject2() throws Exception {
    testTypes("/** @param {!Object<string,number>} a\n" +
        "* @return {string}\n" +
        "*/ var f = function(a) { return a['x']; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testTemplatizedObject3() throws Exception {
    testTypes("/** @param {!Object<number,string>} a\n" +
        "* @return {string}\n" +
        "*/ var f = function(a) { return a['x']; };",
        "restricted index type\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testTemplatizedObject4() throws Exception {
    testTypes("/** @enum {string} */ var E = {A: 'a', B: 'b'};\n" +
        "/** @param {!Object<E,string>} a\n" +
        "* @return {string}\n" +
        "*/ var f = function(a) { return a['x']; };",
        "restricted index type\n" +
        "found   : string\n" +
        "required: E<string>");
  }

  public void testTemplatizedObject5() throws Exception {
    testTypes("/** @constructor */ function F() {" +
        "  /** @type {Object<number, string>} */ this.numbers = {};" +
        "}" +
        "(new F()).numbers['ten'] = '10';",
        "restricted index type\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testUnionOfFunctionAndType() throws Exception {
    testTypes("/** @type {null|(function(Number):void)} */ var a;" +
        "/** @type {(function(Number):void)|null} */ var b = null; a = b;");
  }

  public void testOptionalParameterComparedToUndefined() throws Exception {
    testTypes("/**@param opt_a {Number}*/function foo(opt_a)" +
        "{if (opt_a==undefined) var b = 3;}");
  }

  public void testOptionalAllType() throws Exception {
    testTypes("/** @param {*} opt_x */function f(opt_x) { return opt_x }\n" +
        "/** @type {*} */var y;\n" +
        "f(y);");
  }

  public void testOptionalUnknownNamedType() throws Exception {
    testTypes("/** @param {!T} opt_x\n@return {undefined} */\n" +
        "function f(opt_x) { return opt_x; }\n" +
        "/** @constructor */var T = function() {};",
        "inconsistent return type\n" +
        "found   : (T|undefined)\n" +
        "required: undefined");
  }

  public void testOptionalArgFunctionParam() throws Exception {
    testTypes("/** @param {function(number=)} a */" +
        "function f(a) {a()};");
  }

  public void testOptionalArgFunctionParam2() throws Exception {
    testTypes("/** @param {function(number=)} a */" +
        "function f(a) {a(3)};");
  }

  public void testOptionalArgFunctionParam3() throws Exception {
    testTypes("/** @param {function(number=)} a */" +
        "function f(a) {a(undefined)};");
  }

  public void testOptionalArgFunctionParam4() throws Exception {
    String expectedWarning = "Function a: called with 2 argument(s). " +
        "Function requires at least 0 argument(s) and no more than 1 " +
        "argument(s).";

    testTypes("/** @param {function(number=)} a */function f(a) {a(3,4)};",
              expectedWarning, false);
  }

  public void testOptionalArgFunctionParamError() throws Exception {
    String expectedWarning =
        "Bad type annotation. variable length argument must be last";
    testTypes("/** @param {function(...number, number=)} a */" +
              "function f(a) {};", expectedWarning, false);
  }

  public void testOptionalNullableArgFunctionParam() throws Exception {
    testTypes("/** @param {function(?number=)} a */" +
              "function f(a) {a()};");
  }

  public void testOptionalNullableArgFunctionParam2() throws Exception {
    testTypes("/** @param {function(?number=)} a */" +
              "function f(a) {a(null)};");
  }

  public void testOptionalNullableArgFunctionParam3() throws Exception {
    testTypes("/** @param {function(?number=)} a */" +
              "function f(a) {a(3)};");
  }

  public void testOptionalArgFunctionReturn() throws Exception {
    testTypes("/** @return {function(number=)} */" +
              "function f() { return function(opt_x) { }; };" +
              "f()()");
  }

  public void testOptionalArgFunctionReturn2() throws Exception {
    testTypes("/** @return {function(Object=)} */" +
              "function f() { return function(opt_x) { }; };" +
              "f()({})");
  }

  public void testBooleanType() throws Exception {
    testTypes("/**@type {boolean} */var x = 1 < 2;");
  }

  public void testBooleanReduction1() throws Exception {
    testTypes("/**@type {string} */var x; x = null || \"a\";");
  }

  public void testBooleanReduction2() throws Exception {
    // It's important for the type system to recognize that in no case
    // can the boolean expression evaluate to a boolean value.
    testTypes("/** @param {string} s\n @return {string} */" +
        "(function(s) { return ((s == 'a') && s) || 'b'; })");
  }

  public void testBooleanReduction3() throws Exception {
    testTypes("/** @param {string} s\n @return {string?} */" +
        "(function(s) { return s && null && 3; })");
  }

  public void testBooleanReduction4() throws Exception {
    testTypes("/** @param {Object} x\n @return {Object} */" +
        "(function(x) { return null || x || null ; })");
  }

  public void testBooleanReduction5() throws Exception {
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

  public void testBooleanReduction6() throws Exception {
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

   public void testBooleanReduction7() throws Exception {
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

  public void testNullAnd() throws Exception {
    testTypes("/** @type null */var x;\n" +
        "/** @type number */var r = x && x;",
        "initializing variable\n" +
        "found   : null\n" +
        "required: number");
  }

  public void testNullOr() throws Exception {
    testTypes("/** @type null */var x;\n" +
        "/** @type number */var r = x || x;",
        "initializing variable\n" +
        "found   : null\n" +
        "required: number");
  }

  public void testBooleanPreservation1() throws Exception {
    testTypes("/**@type {string} */var x = \"a\";" +
        "x = ((x == \"a\") && x) || x == \"b\";",
        "assignment\n" +
        "found   : (boolean|string)\n" +
        "required: string");
  }

  public void testBooleanPreservation2() throws Exception {
    testTypes("/**@type {string} */var x = \"a\"; x = (x == \"a\") || x;",
        "assignment\n" +
        "found   : (boolean|string)\n" +
        "required: string");
  }

  public void testBooleanPreservation3() throws Exception {
    testTypes("/** @param {Function?} x\n @return {boolean?} */" +
        "function f(x) { return x && x == \"a\"; }",
        "condition always evaluates to false\n" +
        "left : Function\n" +
        "right: string");
  }

  public void testBooleanPreservation4() throws Exception {
    testTypes("/** @param {Function?|boolean} x\n @return {boolean} */" +
        "function f(x) { return x && x == \"a\"; }",
        "inconsistent return type\n" +
        "found   : (boolean|null)\n" +
        "required: boolean");
  }

  public void testTypeOfReduction1() throws Exception {
    testTypes("/** @param {string|number} x\n @return {string} */ " +
        "function f(x) { return typeof x == 'number' ? String(x) : x; }");
  }

  public void testTypeOfReduction2() throws Exception {
    testTypes("/** @param {string|number} x\n @return {string} */ " +
        "function f(x) { return typeof x != 'string' ? String(x) : x; }");
  }

  public void testTypeOfReduction3() throws Exception {
    testTypes("/** @param {number|null} x\n @return {number} */ " +
        "function f(x) { return typeof x == 'object' ? 1 : x; }");
  }

  public void testTypeOfReduction4() throws Exception {
    testTypes("/** @param {Object|undefined} x\n @return {Object} */ " +
        "function f(x) { return typeof x == 'undefined' ? {} : x; }");
  }

  public void testTypeOfReduction5() throws Exception {
    testTypes("/** @enum {string} */ var E = {A: 'a', B: 'b'};\n" +
        "/** @param {!E|number} x\n @return {string} */ " +
        "function f(x) { return typeof x != 'number' ? x : 'a'; }");
  }

  public void testTypeOfReduction6() throws Exception {
    testTypes("/** @param {number|string} x\n@return {string} */\n" +
        "function f(x) {\n" +
        "return typeof x == 'string' && x.length == 3 ? x : 'a';\n" +
        "}");
  }

  public void testTypeOfReduction7() throws Exception {
    testTypes("/** @return {string} */var f = function(x) { " +
        "return typeof x == 'number' ? x : 'a'; }",
        "inconsistent return type\n" +
        "found   : (number|string)\n" +
        "required: string");
  }

  public void testTypeOfReduction8() throws Exception {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @param {number|string} x\n@return {string} */\n" +
        "function f(x) {\n" +
        "return goog.isString(x) && x.length == 3 ? x : 'a';\n" +
        "}", null);
  }

  public void testTypeOfReduction9() throws Exception {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @param {!Array|string} x\n@return {string} */\n" +
        "function f(x) {\n" +
        "return goog.isArray(x) ? 'a' : x;\n" +
        "}", null);
  }

  public void testTypeOfReduction10() throws Exception {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @param {Array|string} x\n@return {Array} */\n" +
        "function f(x) {\n" +
        "return goog.isArray(x) ? x : [];\n" +
        "}", null);
  }

  public void testTypeOfReduction11() throws Exception {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @param {Array|string} x\n@return {Array} */\n" +
        "function f(x) {\n" +
        "return goog.isObject(x) ? x : [];\n" +
        "}", null);
  }

  public void testTypeOfReduction12() throws Exception {
    testTypes("/** @enum {string} */ var E = {A: 'a', B: 'b'};\n" +
        "/** @param {E|Array} x\n @return {Array} */ " +
        "function f(x) { return typeof x == 'object' ? x : []; }");
  }

  public void testTypeOfReduction13() throws Exception {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @enum {string} */ var E = {A: 'a', B: 'b'};\n" +
        "/** @param {E|Array} x\n@return {Array} */ " +
        "function f(x) { return goog.isObject(x) ? x : []; }", null);
  }

  public void testTypeOfReduction14() throws Exception {
    // Don't do type inference on GETELEMs.
    testClosureTypes(
        CLOSURE_DEFS +
        "function f(x) { " +
        "  return goog.isString(arguments[0]) ? arguments[0] : 0;" +
        "}", null);
  }

  public void testTypeOfReduction15() throws Exception {
    // Don't do type inference on GETELEMs.
    testClosureTypes(
        CLOSURE_DEFS +
        "function f(x) { " +
        "  return typeof arguments[0] == 'string' ? arguments[0] : 0;" +
        "}", null);
  }

  public void testTypeOfReduction16() throws Exception {
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

  public void testQualifiedNameReduction1() throws Exception {
    testTypes("var x = {}; /** @type {string?} */ x.a = 'a';\n" +
        "/** @return {string} */ var f = function() {\n" +
        "return x.a ? x.a : 'a'; }");
  }

  public void testQualifiedNameReduction2() throws Exception {
    testTypes("/** @param {string?} a\n@constructor */ var T = " +
        "function(a) {this.a = a};\n" +
        "/** @return {string} */ T.prototype.f = function() {\n" +
        "return this.a ? this.a : 'a'; }");
  }

  public void testQualifiedNameReduction3() throws Exception {
    testTypes("/** @param {string|Array} a\n@constructor */ var T = " +
        "function(a) {this.a = a};\n" +
        "/** @return {string} */ T.prototype.f = function() {\n" +
        "return typeof this.a == 'string' ? this.a : 'a'; }");
  }

  public void testQualifiedNameReduction4() throws Exception {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @param {string|Array} a\n@constructor */ var T = " +
        "function(a) {this.a = a};\n" +
        "/** @return {string} */ T.prototype.f = function() {\n" +
        "return goog.isString(this.a) ? this.a : 'a'; }", null);
  }

  public void testQualifiedNameReduction5a() throws Exception {
    testTypes("var x = {/** @type {string} */ a:'b' };\n" +
        "/** @return {string} */ var f = function() {\n" +
        "return x.a; }");
  }

  public void testQualifiedNameReduction5b() throws Exception {
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

  public void testQualifiedNameReduction5c() throws Exception {
    testTypes(
        "/** @return {string} */ var f = function() {\n" +
        "var x = {/** @type {number} */ a:0 };\n" +
        "return (x.a) ? (x.a) : 'a'; }",
        "inconsistent return type\n" +
        "found   : (number|string)\n" +
        "required: string");
  }

  public void testQualifiedNameReduction6() throws Exception {
    testTypes(
        "/** @return {string} */ var f = function() {\n" +
        "var x = {/** @return {string?} */ get a() {return 'a'}};\n" +
        "return x.a ? x.a : 'a'; }");
  }

  public void testQualifiedNameReduction7() throws Exception {
    testTypes(
        "/** @return {string} */ var f = function() {\n" +
        "var x = {/** @return {number} */ get a() {return 12}};\n" +
        "return x.a; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testQualifiedNameReduction7a() throws Exception {
    // It would be nice to find a way to make this an error.
    testTypes(
        "/** @return {string} */ var f = function() {\n" +
        "var x = {get a() {return 12}};\n" +
        "return x.a; }");
  }

  public void testQualifiedNameReduction8() throws Exception {
    testTypes(
        "/** @return {string} */ var f = function() {\n" +
        "var x = {get a() {return 'a'}};\n" +
        "return x.a ? x.a : 'a'; }");
  }

  public void testQualifiedNameReduction9() throws Exception {
    testTypes(
        "/** @return {string} */ var f = function() {\n" +
        "var x = { /** @param {string} b */ set a(b) {}};\n" +
        "return x.a ? x.a : 'a'; }");
  }

  public void testQualifiedNameReduction10() throws Exception {
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

  public void testObjLitDef1a() throws Exception {
    testTypes(
        "var x = {/** @type {number} */ a:12 };\n" +
        "x.a = 'a';",
        "assignment to property a of x\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testObjLitDef1b() throws Exception {
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

  public void testObjLitDef2a() throws Exception {
    testTypes(
        "var x = {/** @param {number} b */ set a(b){} };\n" +
        "x.a = 'a';",
        "assignment to property a of x\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testObjLitDef2b() throws Exception {
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

  public void testObjLitDef3a() throws Exception {
    testTypes(
        "/** @type {string} */ var y;\n" +
        "var x = {/** @return {number} */ get a(){} };\n" +
        "y = x.a;",
        "assignment\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testObjLitDef3b() throws Exception {
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

  public void testObjLitDef4() throws Exception {
    testTypes(
        "var x = {" +
          "/** @return {number} */ a:12 };\n",
          "assignment to property a of {a: function (): number}\n" +
          "found   : number\n" +
          "required: function (): number");
  }

  public void testObjLitDef5() throws Exception {
    testTypes(
        "var x = {};\n" +
        "/** @return {number} */ x.a = 12;\n",
        "assignment to property a of x\n" +
        "found   : number\n" +
        "required: function (): number");
  }

  public void testObjLitDef6() throws Exception {
    testTypes("var lit = /** @struct */ { 'x': 1 };",
        "Illegal key, the object literal is a struct");
  }

  public void testObjLitDef7() throws Exception {
    testTypes("var lit = /** @dict */ { x: 1 };",
        "Illegal key, the object literal is a dict");
  }

  public void testInstanceOfReduction1() throws Exception {
    testTypes("/** @constructor */ var T = function() {};\n" +
        "/** @param {T|string} x\n@return {T} */\n" +
        "var f = function(x) {\n" +
        "if (x instanceof T) { return x; } else { return new T(); }\n" +
        "};");
  }

  public void testInstanceOfReduction2() throws Exception {
    testTypes("/** @constructor */ var T = function() {};\n" +
        "/** @param {!T|string} x\n@return {string} */\n" +
        "var f = function(x) {\n" +
        "if (x instanceof T) { return ''; } else { return x; }\n" +
        "};");
  }

  public void testUndeclaredGlobalProperty1() throws Exception {
    testTypes("/** @const */ var x = {}; x.y = null;" +
        "function f(a) { x.y = a; }" +
        "/** @param {string} a */ function g(a) { }" +
        "function h() { g(x.y); }");
  }

  public void testUndeclaredGlobalProperty2() throws Exception {
    testTypes("/** @const */ var x = {}; x.y = null;" +
        "function f() { x.y = 3; }" +
        "/** @param {string} a */ function g(a) { }" +
        "function h() { g(x.y); }",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : (null|number)\n" +
        "required: string");
  }

  public void testLocallyInferredGlobalProperty1() throws Exception {
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

  public void testPropertyInferredPropagation() throws Exception {
    testTypes("/** @return {Object} */function f() { return {}; }\n" +
         "function g() { var x = f(); if (x.p) x.a = 'a'; else x.a = 'b'; }\n" +
         "function h() { var x = f(); x.a = false; }");
  }

  public void testPropertyInference1() throws Exception {
    testTypes(
        "/** @constructor */ function F() { this.x_ = true; }" +
        "/** @return {string} */" +
        "F.prototype.bar = function() { if (this.x_) return this.x_; };",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: string");
  }

  public void testPropertyInference2() throws Exception {
    testTypes(
        "/** @constructor */ function F() { this.x_ = true; }" +
        "F.prototype.baz = function() { this.x_ = null; };" +
        "/** @return {string} */" +
        "F.prototype.bar = function() { if (this.x_) return this.x_; };",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: string");
  }

  public void testPropertyInference3() throws Exception {
    testTypes(
        "/** @constructor */ function F() { this.x_ = true; }" +
        "F.prototype.baz = function() { this.x_ = 3; };" +
        "/** @return {string} */" +
        "F.prototype.bar = function() { if (this.x_) return this.x_; };",
        "inconsistent return type\n" +
        "found   : (boolean|number)\n" +
        "required: string");
  }

  public void testPropertyInference4() throws Exception {
    testTypes(
        "/** @constructor */ function F() { }" +
        "F.prototype.x_ = 3;" +
        "/** @return {string} */" +
        "F.prototype.bar = function() { if (this.x_) return this.x_; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testPropertyInference5() throws Exception {
    testTypes(
        "/** @constructor */ function F() { }" +
        "F.prototype.baz = function() { this.x_ = 3; };" +
        "/** @return {string} */" +
        "F.prototype.bar = function() { if (this.x_) return this.x_; };");
  }

  public void testPropertyInference6() throws Exception {
    testTypes(
        "/** @constructor */ function F() { }" +
        "(new F).x_ = 3;" +
        "/** @return {string} */" +
        "F.prototype.bar = function() { return this.x_; };");
  }

  public void testPropertyInference7() throws Exception {
    testTypes(
        "/** @constructor */ function F() { this.x_ = true; }" +
        "(new F).x_ = 3;" +
        "/** @return {string} */" +
        "F.prototype.bar = function() { return this.x_; };",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: string");
  }

  public void testPropertyInference8() throws Exception {
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

  public void testPropertyInference9() throws Exception {
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

  public void testPropertyInference10() throws Exception {
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

  public void testNoPersistentTypeInferenceForObjectProperties()
      throws Exception {
    testTypes("/** @param {Object} o\n@param {string} x */\n" +
        "function s1(o,x) { o.x = x; }\n" +
        "/** @param {Object} o\n@return {string} */\n" +
        "function g1(o) { return typeof o.x == 'undefined' ? '' : o.x; }\n" +
        "/** @param {Object} o\n@param {number} x */\n" +
        "function s2(o,x) { o.x = x; }\n" +
        "/** @param {Object} o\n@return {number} */\n" +
        "function g2(o) { return typeof o.x == 'undefined' ? 0 : o.x; }");
  }

  public void testNoPersistentTypeInferenceForFunctionProperties()
      throws Exception {
    testTypes("/** @param {Function} o\n@param {string} x */\n" +
        "function s1(o,x) { o.x = x; }\n" +
        "/** @param {Function} o\n@return {string} */\n" +
        "function g1(o) { return typeof o.x == 'undefined' ? '' : o.x; }\n" +
        "/** @param {Function} o\n@param {number} x */\n" +
        "function s2(o,x) { o.x = x; }\n" +
        "/** @param {Function} o\n@return {number} */\n" +
        "function g2(o) { return typeof o.x == 'undefined' ? 0 : o.x; }");
  }

  public void testObjectPropertyTypeInferredInLocalScope1() throws Exception {
    testTypes("/** @param {!Object} o\n@return {string} */\n" +
        "function f(o) { o.x = 1; return o.x; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testObjectPropertyTypeInferredInLocalScope2() throws Exception {
    testTypes("/**@param {!Object} o\n@param {number?} x\n@return {string}*/" +
        "function f(o, x) { o.x = 'a';\nif (x) {o.x = x;}\nreturn o.x; }",
        "inconsistent return type\n" +
        "found   : (number|string)\n" +
        "required: string");
  }

  public void testObjectPropertyTypeInferredInLocalScope3() throws Exception {
    testTypes("/**@param {!Object} o\n@param {number?} x\n@return {string}*/" +
        "function f(o, x) { if (x) {o.x = x;} else {o.x = 'a';}\nreturn o.x; }",
        "inconsistent return type\n" +
        "found   : (number|string)\n" +
        "required: string");
  }

  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty1()
      throws Exception {
    testTypes("/** @constructor */var T = function() { this.x = ''; };\n" +
        "/** @type {number} */ T.prototype.x = 0;",
        "assignment to property x of T\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty2()
      throws Exception {
    testTypes("/** @constructor */var T = function() { this.x = ''; };\n" +
        "/** @type {number} */ T.prototype.x;",
        "assignment to property x of T\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty3()
      throws Exception {
    testTypes("/** @type {Object} */ var n = {};\n" +
        "/** @constructor */ n.T = function() { this.x = ''; };\n" +
        "/** @type {number} */ n.T.prototype.x = 0;",
        "assignment to property x of n.T\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testMismatchingOverridingInferredPropertyBeforeDeclaredProperty4()
      throws Exception {
    testTypes("var n = {};\n" +
        "/** @constructor */ n.T = function() { this.x = ''; };\n" +
        "/** @type {number} */ n.T.prototype.x = 0;",
        "assignment to property x of n.T\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testPropertyUsedBeforeDefinition1() throws Exception {
    testTypes("/** @constructor */ var T = function() {};\n" +
        "/** @return {string} */" +
        "T.prototype.f = function() { return this.g(); };\n" +
        "/** @return {number} */ T.prototype.g = function() { return 1; };\n",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testPropertyUsedBeforeDefinition2() throws Exception {
    testTypes("var n = {};\n" +
        "/** @constructor */ n.T = function() {};\n" +
        "/** @return {string} */" +
        "n.T.prototype.f = function() { return this.g(); };\n" +
        "/** @return {number} */ n.T.prototype.g = function() { return 1; };\n",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testAdd1() throws Exception {
    testTypes("/**@return {void}*/function foo(){var a = 'abc'+foo();}");
  }

  public void testAdd2() throws Exception {
    testTypes("/**@return {void}*/function foo(){var a = foo()+4;}");
  }

  public void testAdd3() throws Exception {
    testTypes("/** @type {string} */ var a = 'a';" +
        "/** @type {string} */ var b = 'b';" +
        "/** @type {string} */ var c = a + b;");
  }

  public void testAdd4() throws Exception {
    testTypes("/** @type {number} */ var a = 5;" +
        "/** @type {string} */ var b = 'b';" +
        "/** @type {string} */ var c = a + b;");
  }

  public void testAdd5() throws Exception {
    testTypes("/** @type {string} */ var a = 'a';" +
        "/** @type {number} */ var b = 5;" +
        "/** @type {string} */ var c = a + b;");
  }

  public void testAdd6() throws Exception {
    testTypes("/** @type {number} */ var a = 5;" +
        "/** @type {number} */ var b = 5;" +
        "/** @type {number} */ var c = a + b;");
  }

  public void testAdd7() throws Exception {
    testTypes("/** @type {number} */ var a = 5;" +
        "/** @type {string} */ var b = 'b';" +
        "/** @type {number} */ var c = a + b;",
        "initializing variable\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testAdd8() throws Exception {
    testTypes("/** @type {string} */ var a = 'a';" +
        "/** @type {number} */ var b = 5;" +
        "/** @type {number} */ var c = a + b;",
        "initializing variable\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testAdd9() throws Exception {
    testTypes("/** @type {number} */ var a = 5;" +
        "/** @type {number} */ var b = 5;" +
        "/** @type {string} */ var c = a + b;",
        "initializing variable\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testAdd10() throws Exception {
    // d.e.f will have unknown type.
    testTypes(
        suppressMissingProperty("e", "f") +
        "/** @type {number} */ var a = 5;" +
        "/** @type {string} */ var c = a + d.e.f;");
  }

  public void testAdd11() throws Exception {
    // d.e.f will have unknown type.
    testTypes(
        suppressMissingProperty("e", "f") +
        "/** @type {number} */ var a = 5;" +
        "/** @type {number} */ var c = a + d.e.f;");
  }

  public void testAdd12() throws Exception {
    testTypes("/** @return {(number,string)} */ function a() { return 5; }" +
        "/** @type {number} */ var b = 5;" +
        "/** @type {boolean} */ var c = a() + b;",
        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: boolean");
  }

  public void testAdd13() throws Exception {
    testTypes("/** @type {number} */ var a = 5;" +
        "/** @return {(number,string)} */ function b() { return 5; }" +
        "/** @type {boolean} */ var c = a + b();",
        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: boolean");
  }

  public void testAdd14() throws Exception {
    testTypes("/** @type {(null,string)} */ var a = unknown;" +
        "/** @type {number} */ var b = 5;" +
        "/** @type {boolean} */ var c = a + b;",
        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: boolean");
  }

  public void testAdd15() throws Exception {
    testTypes("/** @type {number} */ var a = 5;" +
        "/** @return {(number,string)} */ function b() { return 5; }" +
        "/** @type {boolean} */ var c = a + b();",
        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: boolean");
  }

  public void testAdd16() throws Exception {
    testTypes("/** @type {(undefined,string)} */ var a = unknown;" +
        "/** @type {number} */ var b = 5;" +
        "/** @type {boolean} */ var c = a + b;",
        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: boolean");
  }

  public void testAdd17() throws Exception {
    testTypes("/** @type {number} */ var a = 5;" +
        "/** @type {(undefined,string)} */ var b = unknown;" +
        "/** @type {boolean} */ var c = a + b;",
        "initializing variable\n" +
        "found   : (number|string)\n" +
        "required: boolean");
  }

  public void testAdd18() throws Exception {
    testTypes("function f() {};" +
        "/** @type {string} */ var a = 'a';" +
        "/** @type {number} */ var c = a + f();",
        "initializing variable\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testAdd19() throws Exception {
    testTypes("/** @param {number} opt_x\n@param {number} opt_y\n" +
        "@return {number} */ function f(opt_x, opt_y) {" +
        "return opt_x + opt_y;}");
  }

  public void testAdd20() throws Exception {
    testTypes("/** @param {!Number} opt_x\n@param {!Number} opt_y\n" +
        "@return {number} */ function f(opt_x, opt_y) {" +
        "return opt_x + opt_y;}");
  }

  public void testAdd21() throws Exception {
    testTypes("/** @param {Number|Boolean} opt_x\n" +
        "@param {number|boolean} opt_y\n" +
        "@return {number} */ function f(opt_x, opt_y) {" +
        "return opt_x + opt_y;}");
  }

  public void testNumericComparison1() throws Exception {
    testTypes("/**@param {number} a*/ function f(a) {return a < 3;}");
  }

  public void testNumericComparison2() throws Exception {
    testTypes("/**@param {!Object} a*/ function f(a) {return a < 3;}",
        "left side of numeric comparison\n" +
        "found   : Object\n" +
        "required: number");
  }

  public void testNumericComparison3() throws Exception {
    testTypes("/**@param {string} a*/ function f(a) {return a < 3;}");
  }

  public void testNumericComparison4() throws Exception {
    testTypes("/**@param {(number,undefined)} a*/ " +
              "function f(a) {return a < 3;}");
  }

  public void testNumericComparison5() throws Exception {
    testTypes("/**@param {*} a*/ function f(a) {return a < 3;}",
        "left side of numeric comparison\n" +
        "found   : *\n" +
        "required: number");
  }

  public void testNumericComparison6() throws Exception {
    testTypes("/**@return {void} */ function foo() { if (3 >= foo()) return; }",
        "right side of numeric comparison\n" +
        "found   : undefined\n" +
        "required: number");
  }

  public void testStringComparison1() throws Exception {
    testTypes("/**@param {string} a*/ function f(a) {return a < 'x';}");
  }

  public void testStringComparison2() throws Exception {
    testTypes("/**@param {Object} a*/ function f(a) {return a < 'x';}");
  }

  public void testStringComparison3() throws Exception {
    testTypes("/**@param {number} a*/ function f(a) {return a < 'x';}");
  }

  public void testStringComparison4() throws Exception {
    testTypes("/**@param {string|undefined} a*/ " +
                  "function f(a) {return a < 'x';}");
  }

  public void testStringComparison5() throws Exception {
    testTypes("/**@param {*} a*/ " +
                  "function f(a) {return a < 'x';}");
  }

  public void testStringComparison6() throws Exception {
    testTypes("/**@return {void} */ " +
        "function foo() { if ('a' >= foo()) return; }",
        "right side of comparison\n" +
        "found   : undefined\n" +
        "required: string");
  }

  public void testValueOfComparison1() throws Exception {
    testTypes("/** @constructor */function O() {};" +
        "/**@override*/O.prototype.valueOf = function() { return 1; };" +
        "/**@param {!O} a\n@param {!O} b*/ function f(a,b) { return a < b; }");
  }

  public void testValueOfComparison2() throws Exception {
    testTypes("/** @constructor */function O() {};" +
        "/**@override*/O.prototype.valueOf = function() { return 1; };" +
        "/**@param {!O} a\n@param {number} b*/" +
        "function f(a,b) { return a < b; }");
  }

  public void testValueOfComparison3() throws Exception {
    testTypes("/** @constructor */function O() {};" +
        "/**@override*/O.prototype.toString = function() { return 'o'; };" +
        "/**@param {!O} a\n@param {string} b*/" +
        "function f(a,b) { return a < b; }");
  }

  public void testGenericRelationalExpression() throws Exception {
    testTypes("/**@param {*} a\n@param {*} b*/ " +
                  "function f(a,b) {return a < b;}");
  }

  public void testInstanceof1() throws Exception {
    testTypes("function foo(){" +
        "if (bar instanceof 3)return;}",
        "instanceof requires an object\n" +
        "found   : number\n" +
        "required: Object");
  }

  public void testInstanceof2() throws Exception {
    testTypes("/**@return {void}*/function foo(){" +
        "if (foo() instanceof Object)return;}",
        "deterministic instanceof yields false\n" +
        "found   : undefined\n" +
        "required: NoObject");
  }

  public void testInstanceof3() throws Exception {
    testTypes("/**@return {*} */function foo(){" +
        "if (foo() instanceof Object)return;}");
  }

  public void testInstanceof4() throws Exception {
    testTypes("/**@return {(Object|number)} */function foo(){" +
        "if (foo() instanceof Object)return 3;}");
  }

  public void testInstanceof5() throws Exception {
    // No warning for unknown types.
    testTypes("/** @return {?} */ function foo(){" +
        "if (foo() instanceof Object)return;}");
  }

  public void testInstanceof6() throws Exception {
    testTypes("/**@return {(Array|number)} */function foo(){" +
        "if (foo() instanceof Object)return 3;}");
  }

  public void testInstanceOfReduction3() throws Exception {
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

  public void testScoping1() throws Exception {
    testTypes(
        "/**@param {string} a*/function foo(a){" +
        "  /**@param {Array|string} a*/function bar(a){" +
        "    if (a instanceof Array)return;" +
        "  }" +
        "}");
  }

  public void testScoping2() throws Exception {
    testTypes(
        "/** @type number */ var a;" +
        "function Foo() {" +
        "  /** @type string */ var a;" +
        "}");
  }

  public void testScoping3() throws Exception {
    testTypes("\n\n/** @type{Number}*/var b;\n/** @type{!String} */var b;",
        "variable b redefined with type String, original " +
        "definition at [testcode]:3 with type (Number|null)");
  }

  public void testScoping4() throws Exception {
    testTypes("/** @type{Number}*/var b; if (true) /** @type{!String} */var b;",
        "variable b redefined with type String, original " +
        "definition at [testcode]:1 with type (Number|null)");
  }

  public void testScoping5() throws Exception {
    // multiple definitions are not checked by the type checker but by a
    // subsequent pass
    testTypes("if (true) var b; var b;");
  }

  public void testScoping6() throws Exception {
    // multiple definitions are not checked by the type checker but by a
    // subsequent pass
    testTypes("if (true) var b; if (true) var b;");
  }

  public void testScoping7() throws Exception {
    testTypes("/** @constructor */function A() {" +
        "  /** @type !A */this.a = null;" +
        "}",
        "assignment to property a of A\n" +
        "found   : null\n" +
        "required: A");
  }

  public void testScoping8() throws Exception {
    testTypes("/** @constructor */function A() {}" +
        "/** @constructor */function B() {" +
        "  /** @type !A */this.a = null;" +
        "}",
        "assignment to property a of B\n" +
        "found   : null\n" +
        "required: A");
  }

  public void testScoping9() throws Exception {
    testTypes("/** @constructor */function B() {" +
        "  /** @type !A */this.a = null;" +
        "}" +
        "/** @constructor */function A() {}",
        "assignment to property a of B\n" +
        "found   : null\n" +
        "required: A");
  }

  public void testScoping10() throws Exception {
    TypeCheckResult p = parseAndTypeCheckWithScope("var a = function b(){};");

    // a declared, b is not
    assertTrue(p.scope.isDeclared("a", false));
    assertFalse(p.scope.isDeclared("b", false));

    // checking that a has the correct assigned type
    assertEquals("function (): undefined",
        p.scope.getVar("a").getType().toString());
  }

  public void testScoping11() throws Exception {
    // named function expressions create a binding in their body only
    // the return is wrong but the assignment is OK since the type of b is ?
    testTypes(
        "/** @return {number} */var a = function b(){ return b };",
        "inconsistent return type\n" +
        "found   : function (): number\n" +
        "required: number");
  }

  public void testScoping12() throws Exception {
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

  public void testFunctionArguments1() throws Exception {
    testFunctionType(
        "/** @param {number} a\n@return {string} */" +
        "function f(a) {}",
        "function (number): string");
  }

  public void testFunctionArguments2() throws Exception {
    testFunctionType(
        "/** @param {number} opt_a\n@return {string} */" +
        "function f(opt_a) {}",
        "function (number=): string");
  }

  public void testFunctionArguments3() throws Exception {
    testFunctionType(
        "/** @param {number} b\n@return {string} */" +
        "function f(a,b) {}",
        "function (?, number): string");
  }

  public void testFunctionArguments4() throws Exception {
    testFunctionType(
        "/** @param {number} opt_a\n@return {string} */" +
        "function f(a,opt_a) {}",
        "function (?, number=): string");
  }

  public void testFunctionArguments5() throws Exception {
    testTypes(
        "function a(opt_a,a) {}",
        "optional arguments must be at the end");
  }

  public void testFunctionArguments6() throws Exception {
    testTypes(
        "function a(var_args,a) {}",
        "variable length argument must be last");
  }

  public void testFunctionArguments7() throws Exception {
    testTypes(
        "/** @param {number} opt_a\n@return {string} */" +
        "function a(a,opt_a,var_args) {}");
  }

  public void testFunctionArguments8() throws Exception {
    testTypes(
        "function a(a,opt_a,var_args,b) {}",
        "variable length argument must be last");
  }

  public void testFunctionArguments9() throws Exception {
    // testing that only one error is reported
    testTypes(
        "function a(a,opt_a,var_args,b,c) {}",
        "variable length argument must be last");
  }

  public void testFunctionArguments10() throws Exception {
    // testing that only one error is reported
    testTypes(
        "function a(a,opt_a,b,c) {}",
        "optional arguments must be at the end");
  }

  public void testFunctionArguments11() throws Exception {
    testTypes(
        "function a(a,opt_a,b,c,var_args,d) {}",
        "optional arguments must be at the end");
  }

  public void testFunctionArguments12() throws Exception {
    testTypes("/** @param foo {String} */function bar(baz){}",
        "parameter foo does not appear in bar's parameter list");
  }

  public void testFunctionArguments13() throws Exception {
    // verifying that the argument type have non-inferable types
    testTypes(
        "/** @return {boolean} */ function u() { return true; }" +
        "/** @param {boolean} b\n@return {?boolean} */" +
        "function f(b) { if (u()) { b = null; } return b; }",
        "assignment\n" +
        "found   : null\n" +
        "required: boolean");
  }

  public void testFunctionArguments14() throws Exception {
    testTypes(
        "/**\n" +
        " * @param {string} x\n" +
        " * @param {number} opt_y\n" +
        " * @param {boolean} var_args\n" +
        " */ function f(x, opt_y, var_args) {}" +
        "f('3'); f('3', 2); f('3', 2, true); f('3', 2, true, false);");
  }

  public void testFunctionArguments15() throws Exception {
    testTypes(
        "/** @param {?function(*)} f */" +
        "function g(f) { f(1, 2); }",
        "Function f: called with 2 argument(s). " +
        "Function requires at least 1 argument(s) " +
        "and no more than 1 argument(s).");
  }

  public void testFunctionArguments16() throws Exception {
    testTypes(
        "/** @param {...number} var_args */" +
        "function g(var_args) {} g(1, true);",
        "actual parameter 2 of g does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: (number|undefined)");
  }

  public void testFunctionArguments17() throws Exception {
    testClosureTypesMultipleWarnings(
        "/** @param {booool|string} x */" +
        "function f(x) { g(x) }" +
        "/** @param {number} x */" +
        "function g(x) {}",
        Lists.newArrayList(
            "Bad type annotation. Unknown type booool",
            "actual parameter 1 of g does not match formal parameter\n" +
            "found   : (booool|null|string)\n" +
            "required: number"));
  }

  public void testFunctionArguments18() throws Exception {
    testTypes(
        "function f(x) {}" +
        "f(/** @param {number} y */ (function() {}));",
        "parameter y does not appear in <anonymous>'s parameter list");
  }

  public void testPrintFunctionName1() throws Exception {
    // Ensures that the function name is pretty.
    testTypes(
        "var goog = {}; goog.run = function(f) {};" +
        "goog.run();",
        "Function goog.run: called with 0 argument(s). " +
        "Function requires at least 1 argument(s) " +
        "and no more than 1 argument(s).");
  }

  public void testPrintFunctionName2() throws Exception {
    testTypes(
        "/** @constructor */ var Foo = function() {}; " +
        "Foo.prototype.run = function(f) {};" +
        "(new Foo).run();",
        "Function Foo.prototype.run: called with 0 argument(s). " +
        "Function requires at least 1 argument(s) " +
        "and no more than 1 argument(s).");
  }

  public void testFunctionInference1() throws Exception {
    testFunctionType(
        "function f(a) {}",
        "function (?): undefined");
  }

  public void testFunctionInference2() throws Exception {
    testFunctionType(
        "function f(a,b) {}",
        "function (?, ?): undefined");
  }

  public void testFunctionInference3() throws Exception {
    testFunctionType(
        "function f(var_args) {}",
        "function (...?): undefined");
  }

  public void testFunctionInference4() throws Exception {
    testFunctionType(
        "function f(a,b,c,var_args) {}",
        "function (?, ?, ?, ...?): undefined");
  }

  public void testFunctionInference5() throws Exception {
    testFunctionType(
        "/** @this Date\n@return {string} */function f(a) {}",
        "function (this:Date, ?): string");
  }

  public void testFunctionInference6() throws Exception {
    testFunctionType(
        "/** @this Date\n@return {string} */function f(opt_a) {}",
        "function (this:Date, ?=): string");
  }

  public void testFunctionInference7() throws Exception {
    testFunctionType(
        "/** @this Date */function f(a,b,c,var_args) {}",
        "function (this:Date, ?, ?, ?, ...?): undefined");
  }

  public void testFunctionInference8() throws Exception {
    testFunctionType(
        "function f() {}",
        "function (): undefined");
  }

  public void testFunctionInference9() throws Exception {
    testFunctionType(
        "var f = function() {};",
        "function (): undefined");
  }

  public void testFunctionInference10() throws Exception {
    testFunctionType(
        "/** @this Date\n@param {boolean} b\n@return {string} */" +
        "var f = function(a,b) {};",
        "function (this:Date, ?, boolean): string");
  }

  public void testFunctionInference11() throws Exception {
    testFunctionType(
        "var goog = {};" +
        "/** @return {number}*/goog.f = function(){};",
        "goog.f",
        "function (): number");
  }

  public void testFunctionInference12() throws Exception {
    testFunctionType(
        "var goog = {};" +
        "goog.f = function(){};",
        "goog.f",
        "function (): undefined");
  }

  public void testFunctionInference13() throws Exception {
    testFunctionType(
        "var goog = {};" +
        "/** @constructor */ goog.Foo = function(){};" +
        "/** @param {!goog.Foo} f */function eatFoo(f){};",
        "eatFoo",
        "function (goog.Foo): undefined");
  }

  public void testFunctionInference14() throws Exception {
    testFunctionType(
        "var goog = {};" +
        "/** @constructor */ goog.Foo = function(){};" +
        "/** @return {!goog.Foo} */function eatFoo(){ return new goog.Foo; };",
        "eatFoo",
        "function (): goog.Foo");
  }

  public void testFunctionInference15() throws Exception {
    testFunctionType(
        "/** @constructor */ function f() {};" +
        "f.prototype.foo = function(){};",
        "f.prototype.foo",
        "function (this:f): undefined");
  }

  public void testFunctionInference16() throws Exception {
    testFunctionType(
        "/** @constructor */ function f() {};" +
        "f.prototype.foo = function(){};",
        "(new f).foo",
        "function (this:f): undefined");
  }

  public void testFunctionInference17() throws Exception {
    testFunctionType(
        "/** @constructor */ function f() {}" +
        "function abstractMethod() {}" +
        "/** @param {number} x */ f.prototype.foo = abstractMethod;",
        "(new f).foo",
        "function (this:f, number): ?");
  }

  public void testFunctionInference18() throws Exception {
    testFunctionType(
        "var goog = {};" +
        "/** @this {Date} */ goog.eatWithDate;",
        "goog.eatWithDate",
        "function (this:Date): ?");
  }

  public void testFunctionInference19() throws Exception {
    testFunctionType(
        "/** @param {string} x */ var f;",
        "f",
        "function (string): ?");
  }

  public void testFunctionInference20() throws Exception {
    testFunctionType(
        "/** @this {Date} */ var f;",
        "f",
        "function (this:Date): ?");
  }

  public void testFunctionInference21() throws Exception {
    testTypes(
        "var f = function() { throw 'x' };" +
        "/** @return {boolean} */ var g = f;");
    testFunctionType(
        "var f = function() { throw 'x' };",
        "f",
        "function (): ?");
  }

  public void testFunctionInference22() throws Exception {
    testTypes(
        "/** @type {!Function} */ var f = function() { g(this); };" +
        "/** @param {boolean} x */ var g = function(x) {};");
  }

  public void testFunctionInference23() throws Exception {
    // We want to make sure that 'prop' isn't declared on all objects.
    testTypes(
        "/** @type {!Function} */ var f = function() {\n" +
        "  /** @type {number} */ this.prop = 3;\n" +
        "};" +
        "/**\n" +
        " * @param {Object} x\n" +
        " * @return {string}\n" +
        " */ var g = function(x) { return x.prop; };");
  }

  public void testInnerFunction1() throws Exception {
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

  public void testInnerFunction2() throws Exception {
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

  public void testInnerFunction3() throws Exception {
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

  public void testInnerFunction4() throws Exception {
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

  public void testInnerFunction5() throws Exception {
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

  public void testInnerFunction6() throws Exception {
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

  public void testInnerFunction7() throws Exception {
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

  public void testInnerFunction8() throws Exception {
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

  public void testInnerFunction9() throws Exception {
    testTypes(
        "function f() {" +
        " var x = 3;\n" +
        " function g() { x = null; };\n" +
        " function h() { return x == null; }" +
        " return h();" +
        "}");
  }

  public void testInnerFunction10() throws Exception {
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

  public void testInnerFunction11() throws Exception {
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

  public void testAbstractMethodHandling1() throws Exception {
    testTypes(
        "/** @type {Function} */ var abstractFn = function() {};" +
        "abstractFn(1);");
  }

  public void testAbstractMethodHandling2() throws Exception {
    testTypes(
        "var abstractFn = function() {};" +
        "abstractFn(1);",
        "Function abstractFn: called with 1 argument(s). " +
        "Function requires at least 0 argument(s) " +
        "and no more than 0 argument(s).");
  }

  public void testAbstractMethodHandling3() throws Exception {
    testTypes(
        "var goog = {};" +
        "/** @type {Function} */ goog.abstractFn = function() {};" +
        "goog.abstractFn(1);");
  }

  public void testAbstractMethodHandling4() throws Exception {
    testTypes(
        "var goog = {};" +
        "goog.abstractFn = function() {};" +
        "goog.abstractFn(1);",
        "Function goog.abstractFn: called with 1 argument(s). " +
        "Function requires at least 0 argument(s) " +
        "and no more than 0 argument(s).");
  }

  public void testAbstractMethodHandling5() throws Exception {
    testTypes(
        "/** @type {!Function} */ var abstractFn = function() {};" +
        "/** @param {number} x */ var f = abstractFn;" +
        "f('x');",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testAbstractMethodHandling6() throws Exception {
    testTypes(
        "var goog = {};" +
        "/** @type {Function} */ goog.abstractFn = function() {};" +
        "/** @param {number} x */ goog.f = abstractFn;" +
        "goog.f('x');",
        "actual parameter 1 of goog.f does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testMethodInference1() throws Exception {
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

  public void testMethodInference2() throws Exception {
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

  public void testMethodInference3() throws Exception {
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

  public void testMethodInference4() throws Exception {
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

  public void testMethodInference5() throws Exception {
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

  public void testMethodInference6() throws Exception {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** @param {number} x */ F.prototype.foo = function(x) { };" +
        "/** @constructor \n * @extends {F} */ " +
        "function G() {}" +
        "/** @override */ G.prototype.foo = function() { };" +
        "(new G()).foo(1);");
  }

  public void testMethodInference7() throws Exception {
    testTypes(
        "/** @constructor */ function F() {}" +
        "F.prototype.foo = function() { };" +
        "/** @constructor \n * @extends {F} */ " +
        "function G() {}" +
        "/** @override */ G.prototype.foo = function(x, y) { };",
        "mismatch of the foo property type and the type of the property " +
        "it overrides from superclass F\n" +
        "original: function (this:F): undefined\n" +
        "override: function (this:G, ?, ?): undefined");
  }

  public void testMethodInference8() throws Exception {
    testTypes(
        "/** @constructor */ function F() {}" +
        "F.prototype.foo = function() { };" +
        "/** @constructor \n * @extends {F} */ " +
        "function G() {}" +
        "/** @override */ " +
        "G.prototype.foo = function(opt_b, var_args) { };" +
        "(new G()).foo(1, 2, 3);");
  }

  public void testMethodInference9() throws Exception {
    testTypes(
        "/** @constructor */ function F() {}" +
        "F.prototype.foo = function() { };" +
        "/** @constructor \n * @extends {F} */ " +
        "function G() {}" +
        "/** @override */ " +
        "G.prototype.foo = function(var_args, opt_b) { };",
        "variable length argument must be last");
  }

  public void testStaticMethodDeclaration1() throws Exception {
    testTypes(
        "/** @constructor */ function F() { F.foo(true); }" +
        "/** @param {number} x */ F.foo = function(x) {};",
        "actual parameter 1 of F.foo does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testStaticMethodDeclaration2() throws Exception {
    testTypes(
        "var goog = goog || {}; function f() { goog.foo(true); }" +
        "/** @param {number} x */ goog.foo = function(x) {};",
        "actual parameter 1 of goog.foo does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testStaticMethodDeclaration3() throws Exception {
    testTypes(
        "var goog = goog || {}; function f() { goog.foo(true); }" +
        "goog.foo = function() {};",
        "Function goog.foo: called with 1 argument(s). Function requires " +
        "at least 0 argument(s) and no more than 0 argument(s).");
  }

  public void testDuplicateStaticMethodDecl1() throws Exception {
    testTypes(
        "var goog = goog || {};" +
        "/** @param {number} x */ goog.foo = function(x) {};" +
        "/** @param {number} x */ goog.foo = function(x) {};",
        "variable goog.foo redefined, original definition at [testcode]:1");
  }

  public void testDuplicateStaticMethodDecl2() throws Exception {
    testTypes(
        "var goog = goog || {};" +
        "/** @param {number} x */ goog.foo = function(x) {};" +
        "/** @param {number} x \n * @suppress {duplicate} */ " +
        "goog.foo = function(x) {};");
  }

  public void testDuplicateStaticMethodDecl3() throws Exception {
    testTypes(
        "var goog = goog || {};" +
        "goog.foo = function(x) {};" +
        "goog.foo = function(x) {};");
  }

  public void testDuplicateStaticMethodDecl4() throws Exception {
    testTypes(
        "var goog = goog || {};" +
        "/** @type {Function} */ goog.foo = function(x) {};" +
        "goog.foo = function(x) {};");
  }

  public void testDuplicateStaticMethodDecl5() throws Exception {
    testTypes(
        "var goog = goog || {};" +
        "goog.foo = function(x) {};" +
        "/** @return {undefined} */ goog.foo = function(x) {};",
        "variable goog.foo redefined, " +
        "original definition at [testcode]:1");
  }

  public void testDuplicateStaticMethodDecl6() throws Exception {
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

  public void testDuplicateStaticPropertyDecl1() throws Exception {
    testTypes(
        "var goog = goog || {};" +
        "/** @type {Foo} */ goog.foo;" +
        "/** @type {Foo} */ goog.foo;" +
        "/** @constructor */ function Foo() {}");
  }

  public void testDuplicateStaticPropertyDecl2() throws Exception {
    testTypes(
        "var goog = goog || {};" +
        "/** @type {Foo} */ goog.foo;" +
        "/** @type {Foo} \n * @suppress {duplicate} */ goog.foo;" +
        "/** @constructor */ function Foo() {}");
  }

  public void testDuplicateStaticPropertyDecl3() throws Exception {
    testTypes(
        "var goog = goog || {};" +
        "/** @type {!Foo} */ goog.foo;" +
        "/** @type {string} */ goog.foo;" +
        "/** @constructor */ function Foo() {}",
        "variable goog.foo redefined with type string, " +
        "original definition at [testcode]:1 with type Foo");
  }

  public void testDuplicateStaticPropertyDecl4() throws Exception {
    testClosureTypesMultipleWarnings(
        "var goog = goog || {};" +
        "/** @type {!Foo} */ goog.foo;" +
        "/** @type {string} */ goog.foo = 'x';" +
        "/** @constructor */ function Foo() {}",
        Lists.newArrayList(
            "assignment to property foo of goog\n" +
            "found   : string\n" +
            "required: Foo",
            "variable goog.foo redefined with type string, " +
            "original definition at [testcode]:1 with type Foo"));
  }

  public void testDuplicateStaticPropertyDecl5() throws Exception {
    testClosureTypesMultipleWarnings(
        "var goog = goog || {};" +
        "/** @type {!Foo} */ goog.foo;" +
        "/** @type {string}\n * @suppress {duplicate} */ goog.foo = 'x';" +
        "/** @constructor */ function Foo() {}",
        Lists.newArrayList(
            "assignment to property foo of goog\n" +
            "found   : string\n" +
            "required: Foo",
            "variable goog.foo redefined with type string, " +
            "original definition at [testcode]:1 with type Foo"));
  }

  public void testDuplicateStaticPropertyDecl6() throws Exception {
    testTypes(
        "var goog = goog || {};" +
        "/** @type {string} */ goog.foo = 'y';" +
        "/** @type {string}\n * @suppress {duplicate} */ goog.foo = 'x';");
  }

  public void testDuplicateStaticPropertyDecl7() throws Exception {
    testTypes(
        "var goog = goog || {};" +
        "/** @param {string} x */ goog.foo;" +
        "/** @type {function(string)} */ goog.foo;");
  }

  public void testDuplicateStaticPropertyDecl8() throws Exception {
    testTypes(
        "var goog = goog || {};" +
        "/** @return {EventCopy} */ goog.foo;" +
        "/** @constructor */ function EventCopy() {}" +
        "/** @return {EventCopy} */ goog.foo;");
  }

  public void testDuplicateStaticPropertyDecl9() throws Exception {
    testTypes(
        "var goog = goog || {};" +
        "/** @return {EventCopy} */ goog.foo;" +
        "/** @return {EventCopy} */ goog.foo;" +
        "/** @constructor */ function EventCopy() {}");
  }

  public void testDuplicateStaticPropertyDec20() throws Exception {
    testTypes(
        "/**\n" +
        " * @fileoverview\n" +
        " * @suppress {duplicate}\n" +
        " */" +
        "var goog = goog || {};" +
        "/** @type {string} */ goog.foo = 'y';" +
        "/** @type {string} */ goog.foo = 'x';");
  }

  public void testDuplicateLocalVarDecl() throws Exception {
    testClosureTypesMultipleWarnings(
        "/** @param {number} x */\n" +
        "function f(x) { /** @type {string} */ var x = ''; }",
        Lists.newArrayList(
            "variable x redefined with type string, original definition" +
            " at [testcode]:2 with type number",
            "initializing variable\n" +
            "found   : string\n" +
            "required: number"));
  }

  public void testDuplicateInstanceMethod1() throws Exception {
    // If there's no jsdoc on the methods, then we treat them like
    // any other inferred properties.
    testTypes(
        "/** @constructor */ function F() {}" +
        "F.prototype.bar = function() {};" +
        "F.prototype.bar = function() {};");
  }

  public void testDuplicateInstanceMethod2() throws Exception {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** jsdoc */ F.prototype.bar = function() {};" +
        "/** jsdoc */ F.prototype.bar = function() {};",
        "variable F.prototype.bar redefined, " +
        "original definition at [testcode]:1");
  }

  public void testDuplicateInstanceMethod3() throws Exception {
    testTypes(
        "/** @constructor */ function F() {}" +
        "F.prototype.bar = function() {};" +
        "/** jsdoc */ F.prototype.bar = function() {};",
        "variable F.prototype.bar redefined, " +
        "original definition at [testcode]:1");
  }

  public void testDuplicateInstanceMethod4() throws Exception {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** jsdoc */ F.prototype.bar = function() {};" +
        "F.prototype.bar = function() {};");
  }

  public void testDuplicateInstanceMethod5() throws Exception {
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

  public void testDuplicateInstanceMethod6() throws Exception {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** jsdoc \n * @return {number} */ F.prototype.bar = function() {" +
        "  return 3;" +
        "};" +
        "/** jsdoc \n * @return {string} * \n @suppress {duplicate} */ " +
        "F.prototype.bar = function() { return ''; };",
        "assignment to property bar of F.prototype\n" +
        "found   : function (this:F): string\n" +
        "required: function (this:F): number");
  }

  public void testStubFunctionDeclaration1() throws Exception {
    testFunctionType(
        "/** @constructor */ function f() {};" +
        "/** @param {number} x \n * @param {string} y \n" +
        "  * @return {number} */ f.prototype.foo;",
        "(new f).foo",
        "function (this:f, number, string): number");
  }

  public void testStubFunctionDeclaration2() throws Exception {
    testExternFunctionType(
        // externs
        "/** @constructor */ function f() {};" +
        "/** @constructor \n * @extends {f} */ f.subclass;",
        "f.subclass",
        "function (new:f.subclass): ?");
  }

  public void testStubFunctionDeclaration3() throws Exception {
    testFunctionType(
        "/** @constructor */ function f() {};" +
        "/** @return {undefined} */ f.foo;",
        "f.foo",
        "function (): undefined");
  }

  public void testStubFunctionDeclaration4() throws Exception {
    testFunctionType(
        "/** @constructor */ function f() { " +
        "  /** @return {number} */ this.foo;" +
        "}",
        "(new f).foo",
        "function (this:f): number");
  }

  public void testStubFunctionDeclaration5() throws Exception {
    testFunctionType(
        "/** @constructor */ function f() { " +
        "  /** @type {Function} */ this.foo;" +
        "}",
        "(new f).foo",
        createNullableType(U2U_CONSTRUCTOR_TYPE).toString());
  }

  public void testStubFunctionDeclaration6() throws Exception {
    testFunctionType(
        "/** @constructor */ function f() {} " +
        "/** @type {Function} */ f.prototype.foo;",
        "(new f).foo",
        createNullableType(U2U_CONSTRUCTOR_TYPE).toString());
  }

  public void testStubFunctionDeclaration7() throws Exception {
    testFunctionType(
        "/** @constructor */ function f() {} " +
        "/** @type {Function} */ f.prototype.foo = function() {};",
        "(new f).foo",
        createNullableType(U2U_CONSTRUCTOR_TYPE).toString());
  }

  public void testStubFunctionDeclaration8() throws Exception {
    testFunctionType(
        "/** @type {Function} */ var f = function() {}; ",
        "f",
        createNullableType(U2U_CONSTRUCTOR_TYPE).toString());
  }

  public void testStubFunctionDeclaration9() throws Exception {
    testFunctionType(
        "/** @type {function():number} */ var f; ",
        "f",
        "function (): number");
  }

  public void testStubFunctionDeclaration10() throws Exception {
    testFunctionType(
        "/** @type {function(number):number} */ var f = function(x) {};",
        "f",
        "function (number): number");
  }

  public void testNestedFunctionInference1() throws Exception {
    String nestedAssignOfFooAndBar =
        "/** @constructor */ function f() {};" +
        "f.prototype.foo = f.prototype.bar = function(){};";
    testFunctionType(nestedAssignOfFooAndBar, "(new f).bar",
        "function (this:f): undefined");
  }

  /**
   * Tests the type of a function definition. The function defined by
   * {@code functionDef} should be named {@code "f"}.
   */
  private void testFunctionType(String functionDef, String functionType)
      throws Exception {
    testFunctionType(functionDef, "f", functionType);
  }

  /**
   * Tests the type of a function definition. The function defined by
   * {@code functionDef} should be named {@code functionName}.
   */
  private void testFunctionType(String functionDef, String functionName,
      String functionType) throws Exception {
    // using the variable initialization check to verify the function's type
    testTypes(
        functionDef +
        "/** @type number */var a=" + functionName + ";",
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
      String functionType) throws Exception {
    testTypes(
        functionDef,
        "/** @type number */var a=" + functionName + ";",
        "initializing variable\n" +
        "found   : " + functionType + "\n" +
        "required: number", false);
  }

  public void testTypeRedefinition() throws Exception {
    testClosureTypesMultipleWarnings("a={};/**@enum {string}*/ a.A = {ZOR:'b'};"
        + "/** @constructor */ a.A = function() {}",
        Lists.newArrayList(
            "variable a.A redefined with type function (new:a.A): undefined, " +
            "original definition at [testcode]:1 with type enum{a.A}",
            "assignment to property A of a\n" +
            "found   : function (new:a.A): undefined\n" +
            "required: enum{a.A}"));
  }

  public void testIn1() throws Exception {
    testTypes("'foo' in Object");
  }

  public void testIn2() throws Exception {
    testTypes("3 in Object");
  }

  public void testIn3() throws Exception {
    testTypes("undefined in Object");
  }

  public void testIn4() throws Exception {
    testTypes("Date in Object",
        "left side of 'in'\n" +
        "found   : function (new:Date, ?=, ?=, ?=, ?=, ?=, ?=, ?=): string\n" +
        "required: string");
  }

  public void testIn5() throws Exception {
    testTypes("'x' in null",
        "'in' requires an object\n" +
        "found   : null\n" +
        "required: Object");
  }

  public void testIn6() throws Exception {
    testTypes(
        "/** @param {number} x */" +
        "function g(x) {}" +
        "g(1 in {});",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testIn7() throws Exception {
    // Make sure we do inference in the 'in' expression.
    testTypes(
        "/**\n" +
        " * @param {number} x\n" +
        " * @return {number}\n" +
        " */\n" +
        "function g(x) { return 5; }" +
        "function f() {" +
        "  var x = {};" +
        "  x.foo = '3';" +
        "  return g(x.foo) in {};" +
        "}",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testForIn1() throws Exception {
    testTypes(
        "/** @param {boolean} x */ function f(x) {}" +
        "for (var k in {}) {" +
        "  f(k);" +
        "}",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: boolean");
  }

  public void testForIn2() throws Exception {
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

  public void testForIn3() throws Exception {
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

  public void testForIn4() throws Exception {
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

  public void testForIn5() throws Exception {
    testTypes(
        "/** @param {boolean} x */ function f(x) {}" +
        "/** @constructor */ var E = function(){};" +
        "/** @type {Object<E, number>} */ var obj = {};" +
        "for (var k in obj) {" +
        "  f(k);" +
        "}",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: boolean");
  }

  // TODO(nicksantos): change this to something that makes sense.
//   public void testComparison1() throws Exception {
//     testTypes("/**@type null */var a;" +
//         "/**@type !Date */var b;" +
//         "if (a==b) {}",
//         "condition always evaluates to false\n" +
//         "left : null\n" +
//         "right: Date");
//   }

  public void testComparison2() throws Exception {
    testTypes("/**@type number*/var a;" +
        "/**@type !Date */var b;" +
        "if (a!==b) {}",
        "condition always evaluates to true\n" +
        "left : number\n" +
        "right: Date");
  }

  public void testComparison3() throws Exception {
    // Since null == undefined in JavaScript, this code is reasonable.
    testTypes("/** @type {(Object,undefined)} */var a;" +
        "var b = a == null");
  }

  public void testComparison4() throws Exception {
    testTypes("/** @type {(!Object,undefined)} */var a;" +
        "/** @type {!Object} */var b;" +
        "var c = a == b");
  }

  public void testComparison5() throws Exception {
    testTypes("/** @type null */var a;" +
        "/** @type null */var b;" +
        "a == b",
        "condition always evaluates to true\n" +
        "left : null\n" +
        "right: null");
  }

  public void testComparison6() throws Exception {
    testTypes("/** @type null */var a;" +
        "/** @type null */var b;" +
        "a != b",
        "condition always evaluates to false\n" +
        "left : null\n" +
        "right: null");
  }

  public void testComparison7() throws Exception {
    testTypes("var a;" +
        "var b;" +
        "a == b",
        "condition always evaluates to true\n" +
        "left : undefined\n" +
        "right: undefined");
  }

  public void testComparison8() throws Exception {
    testTypes("/** @type {Array<string>} */ var a = [];" +
        "a[0] == null || a[1] == undefined");
  }

  public void testComparison9() throws Exception {
    testTypes("/** @type {Array<undefined>} */ var a = [];" +
        "a[0] == null",
        "condition always evaluates to true\n" +
        "left : undefined\n" +
        "right: null");
  }

  public void testComparison10() throws Exception {
    testTypes("/** @type {Array<undefined>} */ var a = [];" +
        "a[0] === null");
  }

  public void testComparison11() throws Exception {
    testTypes(
        "(function(){}) == 'x'",
        "condition always evaluates to false\n" +
        "left : function (): undefined\n" +
        "right: string");
  }

  public void testComparison12() throws Exception {
    testTypes(
        "(function(){}) == 3",
        "condition always evaluates to false\n" +
        "left : function (): undefined\n" +
        "right: number");
  }

  public void testComparison13() throws Exception {
    testTypes(
        "(function(){}) == false",
        "condition always evaluates to false\n" +
        "left : function (): undefined\n" +
        "right: boolean");
  }

  public void testComparison14() throws Exception {
    testTypes("/** @type {function((Array|string), Object): number} */" +
        "function f(x, y) { return x === y; }",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testComparison15() throws Exception {
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

  public void testDeleteOperator1() throws Exception {
    testTypes(
        "var x = {};" +
        "/** @return {string} */ function f() { return delete x['a']; }",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: string");
  }

  public void testDeleteOperator2() throws Exception {
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

  public void testEnumStaticMethod1() throws Exception {
    testTypes(
        "/** @enum */ var Foo = {AAA: 1};" +
        "/** @param {number} x */ Foo.method = function(x) {};" +
        "Foo.method(true);",
        "actual parameter 1 of Foo.method does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testEnumStaticMethod2() throws Exception {
    testTypes(
        "/** @enum */ var Foo = {AAA: 1};" +
        "/** @param {number} x */ Foo.method = function(x) {};" +
        "function f() { Foo.method(true); }",
        "actual parameter 1 of Foo.method does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testEnum1() throws Exception {
    testTypes("/**@enum*/var a={BB:1,CC:2};\n" +
        "/**@type {a}*/var d;d=a.BB;");
  }

  public void testEnum2() throws Exception {
    testTypes("/**@enum*/var a={b:1}",
        "enum key b must be in ALL_CAPS");
  }

  public void testEnum3() throws Exception {
    testTypes("/**@enum*/var a={BB:1,BB:2}",
        "variable a.BB redefined, original definition at [testcode]:1");
  }

  public void testEnum4() throws Exception {
    testTypes("/**@enum*/var a={BB:'string'}",
        "assignment to property BB of enum{a}\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testEnum5() throws Exception {
    testTypes("/**@enum {String}*/var a={BB:'string'}",
        "assignment to property BB of enum{a}\n" +
        "found   : string\n" +
        "required: (String|null)");
  }

  public void testEnum6() throws Exception {
    testTypes("/**@enum*/var a={BB:1,CC:2};\n/**@type {!Array}*/var d;d=a.BB;",
        "assignment\n" +
        "found   : a<number>\n" +
        "required: Array");
  }

  public void testEnum7() throws Exception {
    testTypes("/** @enum */var a={AA:1,BB:2,CC:3};" +
        "/** @type a */var b=a.D;",
        "element D does not exist on this enum");
  }

  public void testEnum8() throws Exception {
    testClosureTypesMultipleWarnings("/** @enum */var a=8;",
        Lists.newArrayList(
            "enum initializer must be an object literal or an enum",
            "initializing variable\n" +
            "found   : number\n" +
            "required: enum{a}"));
  }

  public void testEnum9() throws Exception {
    testClosureTypesMultipleWarnings(
        "var goog = {};" +
        "/** @enum */goog.a=8;",
        Lists.newArrayList(
            "assignment to property a of goog\n" +
            "found   : number\n" +
            "required: enum{goog.a}",
            "enum initializer must be an object literal or an enum"));
  }

  public void testEnum10() throws Exception {
    testTypes(
        "/** @enum {number} */" +
        "goog.K = { A : 3 };");
  }

  public void testEnum11() throws Exception {
    testTypes(
        "/** @enum {number} */" +
        "goog.K = { 502 : 3 };");
  }

  public void testEnum12() throws Exception {
    testTypes(
        "/** @enum {number} */ var a = {};" +
        "/** @enum */ var b = a;");
  }

  public void testEnum13() throws Exception {
    testTypes(
        "/** @enum {number} */ var a = {};" +
        "/** @enum {string} */ var b = a;",
        "incompatible enum element types\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testEnum14() throws Exception {
    testTypes(
        "/** @enum {number} */ var a = {FOO:5};" +
        "/** @enum */ var b = a;" +
        "var c = b.FOO;");
  }

  public void testEnum15() throws Exception {
    testTypes(
        "/** @enum {number} */ var a = {FOO:5};" +
        "/** @enum */ var b = a;" +
        "var c = b.BAR;",
        "element BAR does not exist on this enum");
  }

  public void testEnum16() throws Exception {
    testTypes("var goog = {};" +
        "/**@enum*/goog .a={BB:1,BB:2}",
        "variable goog.a.BB redefined, original definition at [testcode]:1");
  }

  public void testEnum17() throws Exception {
    testTypes("var goog = {};" +
        "/**@enum*/goog.a={BB:'string'}",
        "assignment to property BB of enum{goog.a}\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testEnum18() throws Exception {
    testTypes("/**@enum*/ var E = {A: 1, B: 2};" +
        "/** @param {!E} x\n@return {number} */\n" +
        "var f = function(x) { return x; };");
  }

  public void testEnum19() throws Exception {
    testTypes("/**@enum*/ var E = {A: 1, B: 2};" +
        "/** @param {number} x\n@return {!E} */\n" +
        "var f = function(x) { return x; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: E<number>");
  }

  public void testEnum20() throws Exception {
    testTypes("/**@enum*/ var E = {A: 1, B: 2}; var x = []; x[E.A] = 0;");
  }

  public void testEnum21() throws Exception {
    Node n = parseAndTypeCheck(
        "/** @enum {string} */ var E = {A : 'a', B : 'b'};\n" +
        "/** @param {!E} x\n@return {!E} */ function f(x) { return x; }");
    Node nodeX = n.getLastChild().getLastChild().getLastChild().getLastChild();
    JSType typeE = nodeX.getJSType();
    assertFalse(typeE.isObject());
    assertFalse(typeE.isNullable());
  }

  public void testEnum22() throws Exception {
    testTypes("/**@enum*/ var E = {A: 1, B: 2};" +
        "/** @param {E} x \n* @return {number} */ function f(x) {return x}");
  }

  public void testEnum23() throws Exception {
    testTypes("/**@enum*/ var E = {A: 1, B: 2};" +
        "/** @param {E} x \n* @return {string} */ function f(x) {return x}",
        "inconsistent return type\n" +
        "found   : E<number>\n" +
        "required: string");
  }

  public void testEnum24() throws Exception {
    testTypes("/**@enum {Object} */ var E = {A: {}};" +
        "/** @param {E} x \n* @return {!Object} */ function f(x) {return x}",
        "inconsistent return type\n" +
        "found   : E<(Object|null)>\n" +
        "required: Object");
  }

  public void testEnum25() throws Exception {
    testTypes("/**@enum {!Object} */ var E = {A: {}};" +
        "/** @param {E} x \n* @return {!Object} */ function f(x) {return x}");
  }

  public void testEnum26() throws Exception {
    testTypes("var a = {}; /**@enum*/ a.B = {A: 1, B: 2};" +
        "/** @param {a.B} x \n* @return {number} */ function f(x) {return x}");
  }

  public void testEnum27() throws Exception {
    // x is unknown
    testTypes("/** @enum */ var A = {B: 1, C: 2}; " +
        "function f(x) { return A == x; }");
  }

  public void testEnum28() throws Exception {
    // x is unknown
    testTypes("/** @enum */ var A = {B: 1, C: 2}; " +
        "function f(x) { return A.B == x; }");
  }

  public void testEnum29() throws Exception {
    testTypes("/** @enum */ var A = {B: 1, C: 2}; " +
        "/** @return {number} */ function f() { return A; }",
        "inconsistent return type\n" +
        "found   : enum{A}\n" +
        "required: number");
  }

  public void testEnum30() throws Exception {
    testTypes("/** @enum */ var A = {B: 1, C: 2}; " +
        "/** @return {number} */ function f() { return A.B; }");
  }

  public void testEnum31() throws Exception {
    testTypes("/** @enum */ var A = {B: 1, C: 2}; " +
        "/** @return {A} */ function f() { return A; }",
        "inconsistent return type\n" +
        "found   : enum{A}\n" +
        "required: A<number>");
  }

  public void testEnum32() throws Exception {
    testTypes("/** @enum */ var A = {B: 1, C: 2}; " +
        "/** @return {A} */ function f() { return A.B; }");
  }

  public void testEnum34() throws Exception {
    testTypes("/** @enum */ var A = {B: 1, C: 2}; " +
        "/** @param {number} x */ function f(x) { return x == A.B; }");
  }

  public void testEnum35() throws Exception {
    testTypes("var a = a || {}; /** @enum */ a.b = {C: 1, D: 2};" +
              "/** @return {a.b} */ function f() { return a.b.C; }");
  }

  public void testEnum36() throws Exception {
    testTypes("var a = a || {}; /** @enum */ a.b = {C: 1, D: 2};" +
              "/** @return {!a.b} */ function f() { return 1; }",
              "inconsistent return type\n" +
              "found   : number\n" +
              "required: a.b<number>");
  }

  public void testEnum37() throws Exception {
    testTypes(
        "var goog = goog || {};" +
        "/** @enum {number} */ goog.a = {};" +
        "/** @enum */ var b = goog.a;");
  }

  public void testEnum38() throws Exception {
    testTypes(
        "/** @enum {MyEnum} */ var MyEnum = {};" +
        "/** @param {MyEnum} x */ function f(x) {}",
        "Parse error. Cycle detected in inheritance chain " +
        "of type MyEnum");
  }

  public void testEnum39() throws Exception {
    testTypes(
        "/** @enum {Number} */ var MyEnum = {FOO: new Number(1)};" +
        "/** @param {MyEnum} x \n * @return {number} */" +
        "function f(x) { return x == MyEnum.FOO && MyEnum.FOO == x; }",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testEnum40() throws Exception {
    testTypes(
        "/** @enum {Number} */ var MyEnum = {FOO: new Number(1)};" +
        "/** @param {number} x \n * @return {number} */" +
        "function f(x) { return x == MyEnum.FOO && MyEnum.FOO == x; }",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testEnum41() throws Exception {
    testTypes(
        "/** @enum {number} */ var MyEnum = {/** @const */ FOO: 1};" +
        "/** @return {string} */" +
        "function f() { return MyEnum.FOO; }",
        "inconsistent return type\n" +
        "found   : MyEnum<number>\n" +
        "required: string");
  }

  public void testEnum42() throws Exception {
    testTypes(
        "/** @param {number} x */ function f(x) {}" +
        "/** @enum {Object} */ var MyEnum = {FOO: {newProperty: 1, b: 2}};" +
        "f(MyEnum.FOO.newProperty);");
  }

  public void testAliasedEnum1() throws Exception {
    testTypes(
        "/** @enum */ var YourEnum = {FOO: 3};" +
        "/** @enum */ var MyEnum = YourEnum;" +
        "/** @param {MyEnum} x */ function f(x) {} f(MyEnum.FOO);");
  }

  public void testAliasedEnum2() throws Exception {
    testTypes(
        "/** @enum */ var YourEnum = {FOO: 3};" +
        "/** @enum */ var MyEnum = YourEnum;" +
        "/** @param {YourEnum} x */ function f(x) {} f(MyEnum.FOO);");
  }

  public void testAliasedEnum3() throws Exception {
    testTypes(
        "/** @enum */ var YourEnum = {FOO: 3};" +
        "/** @enum */ var MyEnum = YourEnum;" +
        "/** @param {MyEnum} x */ function f(x) {} f(YourEnum.FOO);");
  }

  public void testAliasedEnum4() throws Exception {
    testTypes(
        "/** @enum */ var YourEnum = {FOO: 3};" +
        "/** @enum */ var MyEnum = YourEnum;" +
        "/** @param {YourEnum} x */ function f(x) {} f(YourEnum.FOO);");
  }

  public void testAliasedEnum5() throws Exception {
    testTypes(
        "/** @enum */ var YourEnum = {FOO: 3};" +
        "/** @enum */ var MyEnum = YourEnum;" +
        "/** @param {string} x */ function f(x) {} f(MyEnum.FOO);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : YourEnum<number>\n" +
        "required: string");
  }

  public void testBackwardsEnumUse1() throws Exception {
    testTypes(
        "/** @return {string} */ function f() { return MyEnum.FOO; }" +
        "/** @enum {string} */ var MyEnum = {FOO: 'x'};");
  }

  public void testBackwardsEnumUse2() throws Exception {
    testTypes(
        "/** @return {number} */ function f() { return MyEnum.FOO; }" +
        "/** @enum {string} */ var MyEnum = {FOO: 'x'};",
        "inconsistent return type\n" +
        "found   : MyEnum<string>\n" +
        "required: number");
  }

  public void testBackwardsEnumUse3() throws Exception {
    testTypes(
        "/** @return {string} */ function f() { return MyEnum.FOO; }" +
        "/** @enum {string} */ var YourEnum = {FOO: 'x'};" +
        "/** @enum {string} */ var MyEnum = YourEnum;");
  }

  public void testBackwardsEnumUse4() throws Exception {
    testTypes(
        "/** @return {number} */ function f() { return MyEnum.FOO; }" +
        "/** @enum {string} */ var YourEnum = {FOO: 'x'};" +
        "/** @enum {string} */ var MyEnum = YourEnum;",
        "inconsistent return type\n" +
        "found   : YourEnum<string>\n" +
        "required: number");
  }

  public void testBackwardsEnumUse5() throws Exception {
    testTypes(
        "/** @return {string} */ function f() { return MyEnum.BAR; }" +
        "/** @enum {string} */ var YourEnum = {FOO: 'x'};" +
        "/** @enum {string} */ var MyEnum = YourEnum;",
        "element BAR does not exist on this enum");
  }

  public void testBackwardsTypedefUse2() throws Exception {
    testTypes(
        "/** @this {MyTypedef} */ function f() {}" +
        "/** @typedef {!(Date|Array)} */ var MyTypedef;");
  }

  public void testBackwardsTypedefUse4() throws Exception {
    testTypes(
        "/** @return {MyTypedef} */ function f() { return null; }" +
        "/** @typedef {string} */ var MyTypedef;",
        "inconsistent return type\n" +
        "found   : null\n" +
        "required: string");
  }

  public void testBackwardsTypedefUse6() throws Exception {
    testTypes(
        "/** @return {goog.MyTypedef} */ function f() { return null; }" +
        "var goog = {};" +
        "/** @typedef {string} */ goog.MyTypedef;",
        "inconsistent return type\n" +
        "found   : null\n" +
        "required: string");
  }

  public void testBackwardsTypedefUse7() throws Exception {
    testTypes(
        "/** @return {goog.MyTypedef} */ function f() { return null; }" +
        "var goog = {};" +
        "/** @typedef {Object} */ goog.MyTypedef;");
  }

  public void testBackwardsTypedefUse8() throws Exception {
    // Technically, this isn't quite right, because the JS runtime
    // will coerce null -> the global object. But we'll punt on that for now.
    testTypes(
        "/** @param {!Array} x */ function g(x) {}" +
        "/** @this {goog.MyTypedef} */ function f() { g(this); }" +
        "var goog = {};" +
        "/** @typedef {(Array|null|undefined)} */ goog.MyTypedef;");
  }

  public void testBackwardsTypedefUse9() throws Exception {
    testTypes(
        "/** @param {!Array} x */ function g(x) {}" +
        "/** @this {goog.MyTypedef} */ function f() { g(this); }" +
        "var goog = {};" +
        "/** @typedef {(Error|null|undefined)} */ goog.MyTypedef;",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : Error\n" +
        "required: Array");
  }

  public void testBackwardsTypedefUse10() throws Exception {
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

  public void testBackwardsConstructor1() throws Exception {
    testTypes(
        "function f() { (new Foo(true)); }" +
        "/** \n * @constructor \n * @param {number} x */" +
        "var Foo = function(x) {};",
        "actual parameter 1 of Foo does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testBackwardsConstructor2() throws Exception {
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

  public void testMinimalConstructorAnnotation() throws Exception {
    testTypes("/** @constructor */function Foo(){}");
  }

  public void testGoodExtends1() throws Exception {
    // A minimal @extends example
    testTypes("/** @constructor */function base() {}\n" +
        "/** @constructor\n * @extends {base} */function derived() {}\n");
  }

  public void testGoodExtends2() throws Exception {
    testTypes("/** @constructor\n * @extends base */function derived() {}\n" +
        "/** @constructor */function base() {}\n");
  }

  public void testGoodExtends3() throws Exception {
    testTypes("/** @constructor\n * @extends {Object} */function base() {}\n" +
        "/** @constructor\n * @extends {base} */function derived() {}\n");
  }

  public void testGoodExtends4() throws Exception {
    // Ensure that @extends actually sets the base type of a constructor
    // correctly. Because this isn't part of the human-readable Function
    // definition, we need to crawl the prototype chain (eww).
    Node n = parseAndTypeCheck(
        "var goog = {};\n" +
        "/** @constructor */goog.Base = function(){};\n" +
        "/** @constructor\n" +
        "  * @extends {goog.Base} */goog.Derived = function(){};\n");
    Node subTypeName = n.getLastChild().getLastChild().getFirstChild();
    assertEquals("goog.Derived", subTypeName.getQualifiedName());

    FunctionType subCtorType =
        (FunctionType) subTypeName.getNext().getJSType();
    assertEquals("goog.Derived", subCtorType.getInstanceType().toString());

    JSType superType = subCtorType.getPrototype().getImplicitPrototype();
    assertEquals("goog.Base", superType.toString());
  }

  public void testGoodExtends5() throws Exception {
    // we allow for the extends annotation to be placed first
    testTypes("/** @constructor */function base() {}\n" +
        "/** @extends {base}\n * @constructor */function derived() {}\n");
  }

  public void testGoodExtends6() throws Exception {
    testFunctionType(
        CLOSURE_DEFS +
        "/** @constructor */function base() {}\n" +
        "/** @return {number} */ " +
        "  base.prototype.foo = function() { return 1; };\n" +
        "/** @extends {base}\n * @constructor */function derived() {}\n" +
        "goog.inherits(derived, base);",
        "derived.superClass_.foo",
        "function (this:base): number");
  }

  public void testGoodExtends7() throws Exception {
    testFunctionType(
        "Function.prototype.inherits = function(x) {};" +
        "/** @constructor */function base() {}\n" +
        "/** @extends {base}\n * @constructor */function derived() {}\n" +
        "derived.inherits(base);",
        "(new derived).constructor",
        "function (new:derived, ...?): ?");
  }

  public void testGoodExtends8() throws Exception {
    testTypes("/** @constructor \n @extends {Base} */ function Sub() {}" +
        "/** @return {number} */ function f() { return (new Sub()).foo; }" +
        "/** @constructor */ function Base() {}" +
        "/** @type {boolean} */ Base.prototype.foo = true;",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testGoodExtends9() throws Exception {
    testTypes(
        "/** @constructor */ function Super() {}" +
        "Super.prototype.foo = function() {};" +
        "/** @constructor \n * @extends {Super} */ function Sub() {}" +
        "Sub.prototype = new Super();" +
        "/** @override */ Sub.prototype.foo = function() {};");
  }

  public void testGoodExtends10() throws Exception {
    testTypes(
        "/** @constructor */ function Super() {}" +
        "/** @constructor \n * @extends {Super} */ function Sub() {}" +
        "Sub.prototype = new Super();" +
        "/** @return {Super} */ function foo() { return new Sub(); }");
  }

  public void testGoodExtends11() throws Exception {
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

  public void testGoodExtends12() throws Exception {
    testTypes(
        "/** @constructor \n * @extends {Super} */ function Sub() {}" +
        "/** @constructor \n * @extends {Sub} */ function Sub2() {}" +
        "/** @constructor */ function Super() {}" +
        "/** @param {Super} x */ function foo(x) {}" +
        "foo(new Sub2());");
  }

  public void testGoodExtends13() throws Exception {
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

  public void testGoodExtends14() throws Exception {
    testTypes(
        CLOSURE_DEFS +
        "/** @param {Function} f */ function g(f) {" +
        "  /** @constructor */ function NewType() {};" +
        "  goog.inherits(NewType, f);" +
        "  (new NewType());" +
        "}");
  }

  public void testGoodExtends15() throws Exception {
    testTypes(
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

  public void testGoodExtends16() throws Exception {
    testTypes(
        CLOSURE_DEFS +
        "/** @param {Function} f */ function g(f) {" +
        "  /** @constructor */ function NewType() {};" +
        "  goog.inherits(f, NewType);" +
        "  (new NewType());" +
        "}");
  }

  public void testGoodExtends17() throws Exception {
    testFunctionType(
        "Function.prototype.inherits = function(x) {};" +
        "/** @constructor */function base() {}\n" +
        "/** @param {number} x */ base.prototype.bar = function(x) {};\n" +
        "/** @extends {base}\n * @constructor */function derived() {}\n" +
        "derived.inherits(base);",
        "(new derived).constructor.prototype.bar",
        "function (this:base, number): undefined");
  }

  public void testGoodExtends18() throws Exception {
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

  public void testGoodExtends19() throws Exception {
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

  public void testGoodExtends20() throws Exception {
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

  public void testGoodExtends21() throws Exception {
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

  public void testBadExtends1() throws Exception {
    testTypes("/** @constructor */function base() {}\n" +
        "/** @constructor\n * @extends {not_base} */function derived() {}\n",
        "Bad type annotation. Unknown type not_base");
  }

  public void testBadExtends2() throws Exception {
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

  public void testBadExtends3() throws Exception {
    testTypes("/** @extends {Object} */function base() {}",
        "@extends used without @constructor or @interface for base");
  }

  public void testBadExtends4() throws Exception {
    // If there's a subclass of a class with a bad extends,
    // we only want to warn about the first one.
    testTypes(
        "/** @constructor \n * @extends {bad} */ function Sub() {}" +
        "/** @constructor \n * @extends {Sub} */ function Sub2() {}" +
        "/** @param {Sub} x */ function foo(x) {}" +
        "foo(new Sub2());",
        "Bad type annotation. Unknown type bad");
  }

  public void testBadExtends5() throws Exception {
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
        + "original: function (this:MyInterface): number\n"
        + "override: function (this:MyOtherInterface): string");
  }


  public void testBadExtends6() throws Exception {
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
        + "original: function (this:MyType): number\n"
        + "override: function (this:MyOtherType): string");
  }

  public void testLateExtends() throws Exception {
    testTypes(
        CLOSURE_DEFS +
        "/** @constructor */ function Foo() {}\n" +
        "Foo.prototype.foo = function() {};\n" +
        "/** @constructor */function Bar() {}\n" +
        "goog.inherits(Foo, Bar);\n",
        "Missing @extends tag on type Foo");
  }

  public void testSuperclassMatch() throws Exception {
    compiler.getOptions().setCodingConvention(new GoogleCodingConvention());
    testTypes("/** @constructor */ var Foo = function() {};\n" +
        "/** @constructor \n @extends Foo */ var Bar = function() {};\n" +
        "Bar.inherits = function(x){};" +
        "Bar.inherits(Foo);\n");
  }

  public void testSuperclassMatchWithMixin() throws Exception {
    compiler.getOptions().setCodingConvention(new GoogleCodingConvention());
    testTypes("/** @constructor */ var Foo = function() {};\n" +
        "/** @constructor */ var Baz = function() {};\n" +
        "/** @constructor \n @extends Foo */ var Bar = function() {};\n" +
        "Bar.inherits = function(x){};" +
        "Bar.mixin = function(y){};" +
        "Bar.inherits(Foo);\n" +
        "Bar.mixin(Baz);\n");
  }

  public void testSuperclassMismatch1() throws Exception {
    compiler.getOptions().setCodingConvention(new GoogleCodingConvention());
    testTypes("/** @constructor */ var Foo = function() {};\n" +
        "/** @constructor \n @extends Object */ var Bar = function() {};\n" +
        "Bar.inherits = function(x){};" +
        "Bar.inherits(Foo);\n",
        "Missing @extends tag on type Bar");
  }

  public void testSuperclassMismatch2() throws Exception {
    compiler.getOptions().setCodingConvention(new GoogleCodingConvention());
    testTypes("/** @constructor */ var Foo = function(){};\n" +
        "/** @constructor */ var Bar = function(){};\n" +
        "Bar.inherits = function(x){};" +
        "Bar.inherits(Foo);",
        "Missing @extends tag on type Bar");
  }

  public void testSuperClassDefinedAfterSubClass1() throws Exception {
    testTypes(
        "/** @constructor \n * @extends {Base} */ function A() {}" +
        "/** @constructor \n * @extends {Base} */ function B() {}" +
        "/** @constructor */ function Base() {}" +
        "/** @param {A|B} x \n * @return {B|A} */ " +
        "function foo(x) { return x; }");
  }

  public void testSuperClassDefinedAfterSubClass2() throws Exception {
    testTypes(
        "/** @constructor \n * @extends {Base} */ function A() {}" +
        "/** @constructor \n * @extends {Base} */ function B() {}" +
        "/** @param {A|B} x \n * @return {B|A} */ " +
        "function foo(x) { return x; }" +
        "/** @constructor */ function Base() {}");
  }

  public void testDirectPrototypeAssignment1() throws Exception {
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

  public void testDirectPrototypeAssignment2() throws Exception {
    // This ensures that we don't attach property 'foo' onto the Base
    // instance object.
    testTypes(
        "/** @constructor */ function Base() {}" +
        "/** @constructor \n * @extends {Base} */ function A() {}" +
        "A.prototype = new Base();" +
        "A.prototype.foo = 3;" +
        "/** @return {string} */ function foo() { return (new Base).foo; }");
  }

  public void testDirectPrototypeAssignment3() throws Exception {
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

  public void testGoodImplements1() throws Exception {
    testTypes("/** @interface */function Disposable() {}\n" +
        "/** @implements {Disposable}\n * @constructor */function f() {}");
  }

  public void testGoodImplements2() throws Exception {
    testTypes("/** @interface */function Base1() {}\n" +
        "/** @interface */function Base2() {}\n" +
        "/** @constructor\n" +
        " * @implements {Base1}\n" +
        " * @implements {Base2}\n" +
        " */ function derived() {}");
  }

  public void testGoodImplements3() throws Exception {
    testTypes("/** @interface */function Disposable() {}\n" +
        "/** @constructor \n @implements {Disposable} */function f() {}");
  }

  public void testGoodImplements4() throws Exception {
    testTypes("var goog = {};" +
        "/** @type {!Function} */" +
        "goog.abstractMethod = function() {};" +
        "/** @interface */\n" +
        "goog.Disposable = goog.abstractMethod;" +
        "goog.Disposable.prototype.dispose = goog.abstractMethod;" +
        "/** @implements {goog.Disposable}\n * @constructor */" +
        "goog.SubDisposable = function() {};" +
        "/** @inheritDoc */ " +
        "goog.SubDisposable.prototype.dispose = function() {};");
  }

  public void testGoodImplements5() throws Exception {
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

  public void testGoodImplements6() throws Exception {
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

  public void testGoodImplements7() throws Exception {
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

  public void testGoodImplements8() throws Exception {
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

  public void testBadImplements1() throws Exception {
    testTypes("/** @interface */function Base1() {}\n" +
        "/** @interface */function Base2() {}\n" +
        "/** @constructor\n" +
        " * @implements {nonExistent}\n" +
        " * @implements {Base2}\n" +
        " */ function derived() {}",
        "Bad type annotation. Unknown type nonExistent");
  }

  public void testBadImplements2() throws Exception {
    testTypes("/** @interface */function Disposable() {}\n" +
        "/** @implements {Disposable}\n */function f() {}",
        "@implements used without @constructor for f");
  }

  public void testBadImplements3() throws Exception {
    testTypes(
        "var goog = {};" +
        "/** @type {!Function} */ goog.abstractMethod = function(){};" +
        "/** @interface */ var Disposable = goog.abstractMethod;" +
        "Disposable.prototype.method = goog.abstractMethod;" +
        "/** @implements {Disposable}\n * @constructor */function f() {}",
        "property method on interface Disposable is not implemented by type f");
  }

  public void testBadImplements4() throws Exception {
    testTypes("/** @interface */function Disposable() {}\n" +
        "/** @implements {Disposable}\n * @interface */function f() {}",
        "f cannot implement this type; an interface can only extend, " +
        "but not implement interfaces");
  }

  public void testBadImplements5() throws Exception {
    testTypes("/** @interface */function Disposable() {}\n" +
        "/** @type {number} */ Disposable.prototype.bar = function() {};",
        "assignment to property bar of Disposable.prototype\n" +
        "found   : function (): undefined\n" +
        "required: number");
  }

  public void testBadImplements6() throws Exception {
    testClosureTypesMultipleWarnings(
        "/** @interface */function Disposable() {}\n" +
        "/** @type {function()} */ Disposable.prototype.bar = 3;",
        Lists.newArrayList(
            "assignment to property bar of Disposable.prototype\n" +
            "found   : number\n" +
            "required: function (): ?",
            "interface members can only be empty property declarations, " +
            "empty functions, or goog.abstractMethod"));
  }

  public void testBadImplements7() throws Exception {
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
        + "mismatch of the method property type and the type of the property "
        + "it overrides from interface MyInterface\n"
        + "original: function (): number\n"
        + "override: function (): string");
  }

  public void testBadImplements8() throws Exception {
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

  public void testProtoDoesNotRequireOverrideFromInterface() throws Exception {
    testTypes(DEFAULT_EXTERNS + "/** @type {Object} */ Object.prototype.__proto__;",
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

  public void testConstructorClassTemplate() throws Exception {
    testTypes("/** @constructor \n @template S,T */ function A() {}\n");
  }

  public void testInterfaceExtends() throws Exception {
    testTypes("/** @interface */function A() {}\n" +
        "/** @interface \n * @extends {A} */function B() {}\n" +
        "/** @constructor\n" +
        " * @implements {B}\n" +
        " */ function derived() {}");
  }

  public void testBadInterfaceExtends1() throws Exception {
    testTypes("/** @interface \n * @extends {nonExistent} */function A() {}",
        "Bad type annotation. Unknown type nonExistent");
  }

  public void testBadInterfaceExtendsNonExistentInterfaces() throws Exception {
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

  public void testBadInterfaceExtends2() throws Exception {
    testTypes("/** @constructor */function A() {}\n" +
        "/** @interface \n * @extends {A} */function B() {}",
        "B cannot extend this type; interfaces can only extend interfaces");
  }

  public void testBadInterfaceExtends3() throws Exception {
    testTypes("/** @interface */function A() {}\n" +
        "/** @constructor \n * @extends {A} */function B() {}",
        "B cannot extend this type; constructors can only extend constructors");
  }

  public void testBadInterfaceExtends4() throws Exception {
    // TODO(user): This should be detected as an error. Even if we enforce
    // that A cannot be used in the assignment, we should still detect the
    // inheritance chain as invalid.
    testTypes("/** @interface */function A() {}\n" +
        "/** @constructor */function B() {}\n" +
        "B.prototype = A;");
  }

  public void testBadInterfaceExtends5() throws Exception {
    // TODO(user): This should be detected as an error. Even if we enforce
    // that A cannot be used in the assignment, we should still detect the
    // inheritance chain as invalid.
    testTypes("/** @constructor */function A() {}\n" +
        "/** @interface */function B() {}\n" +
        "B.prototype = A;");
  }

  public void testBadImplementsAConstructor() throws Exception {
    testTypes("/** @constructor */function A() {}\n" +
        "/** @constructor \n * @implements {A} */function B() {}",
        "can only implement interfaces");
  }

  public void testBadImplementsNonInterfaceType() throws Exception {
    testTypes("/** @constructor \n * @implements {Boolean} */function B() {}",
        "can only implement interfaces");
  }

  public void testBadImplementsNonObjectType() throws Exception {
    testTypes("/** @constructor \n * @implements {string} */function S() {}",
        "can only implement interfaces");
  }

  public void testBadImplementsDuplicateInterface1() throws Exception {
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

  public void testBadImplementsDuplicateInterface2() throws Exception {
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

  public void testInterfaceAssignment1() throws Exception {
    testTypes("/** @interface */var I = function() {};\n" +
        "/** @constructor\n@implements {I} */var T = function() {};\n" +
        "var t = new T();\n" +
        "/** @type {!I} */var i = t;");
  }

  public void testInterfaceAssignment2() throws Exception {
    testTypes("/** @interface */var I = function() {};\n" +
        "/** @constructor */var T = function() {};\n" +
        "var t = new T();\n" +
        "/** @type {!I} */var i = t;",
        "initializing variable\n" +
        "found   : T\n" +
        "required: I");
  }

  public void testInterfaceAssignment3() throws Exception {
    testTypes("/** @interface */var I = function() {};\n" +
        "/** @constructor\n@implements {I} */var T = function() {};\n" +
        "var t = new T();\n" +
        "/** @type {I|number} */var i = t;");
  }

  public void testInterfaceAssignment4() throws Exception {
    testTypes("/** @interface */var I1 = function() {};\n" +
        "/** @interface */var I2 = function() {};\n" +
        "/** @constructor\n@implements {I1} */var T = function() {};\n" +
        "var t = new T();\n" +
        "/** @type {I1|I2} */var i = t;");
  }

  public void testInterfaceAssignment5() throws Exception {
    testTypes("/** @interface */var I1 = function() {};\n" +
        "/** @interface */var I2 = function() {};\n" +
        "/** @constructor\n@implements {I1}\n@implements {I2}*/" +
        "var T = function() {};\n" +
        "var t = new T();\n" +
        "/** @type {I1} */var i1 = t;\n" +
        "/** @type {I2} */var i2 = t;\n");
  }

  public void testInterfaceAssignment6() throws Exception {
    testTypes("/** @interface */var I1 = function() {};\n" +
        "/** @interface */var I2 = function() {};\n" +
        "/** @constructor\n@implements {I1} */var T = function() {};\n" +
        "/** @type {!I1} */var i1 = new T();\n" +
        "/** @type {!I2} */var i2 = i1;\n",
        "initializing variable\n" +
        "found   : I1\n" +
        "required: I2");
  }

  public void testInterfaceAssignment7() throws Exception {
    testTypes("/** @interface */var I1 = function() {};\n" +
        "/** @interface\n@extends {I1}*/var I2 = function() {};\n" +
        "/** @constructor\n@implements {I2}*/var T = function() {};\n" +
        "var t = new T();\n" +
        "/** @type {I1} */var i1 = t;\n" +
        "/** @type {I2} */var i2 = t;\n" +
        "i1 = i2;\n");
  }

  public void testInterfaceAssignment8() throws Exception {
    testTypes("/** @interface */var I = function() {};\n" +
        "/** @type {I} */var i;\n" +
        "/** @type {Object} */var o = i;\n" +
        "new Object().prototype = i.prototype;");
  }

  public void testInterfaceAssignment9() throws Exception {
    testTypes("/** @interface */var I = function() {};\n" +
        "/** @return {I?} */function f() { return null; }\n" +
        "/** @type {!I} */var i = f();\n",
        "initializing variable\n" +
        "found   : (I|null)\n" +
        "required: I");
  }

  public void testInterfaceAssignment10() throws Exception {
    testTypes("/** @interface */var I1 = function() {};\n" +
        "/** @interface */var I2 = function() {};\n" +
        "/** @constructor\n@implements {I2} */var T = function() {};\n" +
        "/** @return {!I1|!I2} */function f() { return new T(); }\n" +
        "/** @type {!I1} */var i1 = f();\n",
        "initializing variable\n" +
        "found   : (I1|I2)\n" +
        "required: I1");
  }

  public void testInterfaceAssignment11() throws Exception {
    testTypes("/** @interface */var I1 = function() {};\n" +
        "/** @interface */var I2 = function() {};\n" +
        "/** @constructor */var T = function() {};\n" +
        "/** @return {!I1|!I2|!T} */function f() { return new T(); }\n" +
        "/** @type {!I1} */var i1 = f();\n",
        "initializing variable\n" +
        "found   : (I1|I2|T)\n" +
        "required: I1");
  }

  public void testInterfaceAssignment12() throws Exception {
    testTypes("/** @interface */var I = function() {};\n" +
              "/** @constructor\n@implements{I}*/var T1 = function() {};\n" +
              "/** @constructor\n@extends {T1}*/var T2 = function() {};\n" +
              "/** @return {I} */function f() { return new T2(); }");
  }

  public void testInterfaceAssignment13() throws Exception {
    testTypes("/** @interface */var I = function() {};\n" +
        "/** @constructor\n@implements {I}*/var T = function() {};\n" +
        "/** @constructor */function Super() {};\n" +
        "/** @return {I} */Super.prototype.foo = " +
        "function() { return new T(); };\n" +
        "/** @constructor\n@extends {Super} */function Sub() {}\n" +
        "/** @override\n@return {T} */Sub.prototype.foo = " +
        "function() { return new T(); };\n");
  }

  public void testGetprop1() throws Exception {
    testTypes("/** @return {void}*/function foo(){foo().bar;}",
        "No properties on this expression\n" +
        "found   : undefined\n" +
        "required: Object");
  }

  public void testGetprop2() throws Exception {
    testTypes("var x = null; x.alert();",
        "No properties on this expression\n" +
        "found   : null\n" +
        "required: Object");
  }

  public void testGetprop3() throws Exception {
    testTypes(
        "/** @constructor */ " +
        "function Foo() { /** @type {?Object} */ this.x = null; }" +
        "Foo.prototype.initX = function() { this.x = {foo: 1}; };" +
        "Foo.prototype.bar = function() {" +
        "  if (this.x == null) { this.initX(); alert(this.x.foo); }" +
        "};");
  }

  public void testGetprop4() throws Exception {
    testTypes("var x = null; x.prop = 3;",
        "No properties on this expression\n" +
        "found   : null\n" +
        "required: Object");
  }

  public void testSetprop1() throws Exception {
    // Create property on struct in the constructor
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() { this.x = 123; }");
  }

  public void testSetprop2() throws Exception {
    // Create property on struct outside the constructor
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "(new Foo()).x = 123;",
              "Cannot add a property to a struct instance " +
              "after it is constructed.");
  }

  public void testSetprop3() throws Exception {
    // Create property on struct outside the constructor
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "(function() { (new Foo()).x = 123; })();",
              "Cannot add a property to a struct instance " +
              "after it is constructed.");
  }

  public void testSetprop4() throws Exception {
    // Assign to existing property of struct outside the constructor
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() { this.x = 123; }\n" +
              "(new Foo()).x = \"asdf\";");
  }

  public void testSetprop5() throws Exception {
    // Create a property on union that includes a struct
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "(true ? new Foo() : {}).x = 123;",
              "Cannot add a property to a struct instance " +
              "after it is constructed.");
  }

  public void testSetprop6() throws Exception {
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
              "Cannot add a property to a struct instance " +
              "after it is constructed.");
  }

  public void testSetprop7() throws Exception {
    //Bug b/c we require THIS when creating properties on structs for simplicity
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {\n" +
              "  var t = this;\n" +
              "  t.x = 123;\n" +
              "}",
              "Cannot add a property to a struct instance " +
              "after it is constructed.");
  }

  public void testSetprop8() throws Exception {
    // Create property on struct using DEC
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "(new Foo()).x--;",
              new String[] {
                "Cannot add a property to a struct instance " +
                "after it is constructed.",
                "Property x never defined on Foo"
              });
  }

  public void testSetprop9() throws Exception {
    // Create property on struct using ASSIGN_ADD
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "(new Foo()).x += 123;",
              new String[] {
                "Cannot add a property to a struct instance " +
                "after it is constructed.",
                "Property x never defined on Foo"
              });
  }

  public void testSetprop10() throws Exception {
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

  public void testSetprop11() throws Exception {
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

  public void testSetprop12() throws Exception {
    // Create property on a constructor of structs (which isn't itself a struct)
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Foo() {}\n" +
              "Foo.someprop = 123;");
  }

  public void testSetprop13() throws Exception {
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

  public void testSetprop14() throws Exception {
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

  public void testSetprop15() throws Exception {
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

  public void testGetpropDict1() throws Exception {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @dict\n" +
              " */" +
              "function Dict1(){ this['prop'] = 123; }" +
              "/** @param{Dict1} x */" +
              "function takesDict(x) { return x.prop; }",
              "Cannot do '.' access on a dict");
  }

  public void testGetpropDict2() throws Exception {
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

  public void testGetpropDict3() throws Exception {
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

  public void testGetpropDict4() throws Exception {
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

  public void testGetpropDict5() throws Exception {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @dict\n" +
              " */" +
              "function Dict1(){ this.prop = 123; }",
              "Cannot do '.' access on a dict");
  }

  public void testGetpropDict6() throws Exception {
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

  public void testGetpropDict7() throws Exception {
    testTypes("(/** @dict */ {'x': 123}).x = 321;",
              "Cannot do '.' access on a dict");
  }

  public void testGetelemStruct1() throws Exception {
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

  public void testGetelemStruct2() throws Exception {
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

  public void testGetelemStruct3() throws Exception {
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

  public void testGetelemStruct4() throws Exception {
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

  public void testGetelemStruct5() throws Exception {
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

  public void testGetelemStruct6() throws Exception {
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

  public void testGetelemStruct7() throws Exception {
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

  public void testInOnStruct() throws Exception {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */" +
              "function Foo() {}\n" +
              "if ('prop' in (new Foo())) {}",
              "Cannot use the IN operator with structs");
  }

  public void testForinOnStruct() throws Exception {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */" +
              "function Foo() {}\n" +
              "for (var prop in (new Foo())) {}",
              "Cannot use the IN operator with structs");
  }

  public void testArrayAccess1() throws Exception {
    testTypes("var a = []; var b = a['hi'];");
  }

  public void testArrayAccess2() throws Exception {
    testTypes("var a = []; var b = a[[1,2]];",
        "array access\n" +
        "found   : Array\n" +
        "required: number");
  }

  public void testArrayAccess3() throws Exception {
    testTypes("var bar = [];" +
        "/** @return {void} */function baz(){};" +
        "var foo = bar[baz()];",
        "array access\n" +
        "found   : undefined\n" +
        "required: number");
  }

  public void testArrayAccess4() throws Exception {
    testTypes("/**@return {!Array}*/function foo(){};var bar = foo()[foo()];",
        "array access\n" +
        "found   : Array\n" +
        "required: number");
  }

  public void testArrayAccess6() throws Exception {
    testTypes("var bar = null[1];",
        "only arrays or objects can be accessed\n" +
        "found   : null\n" +
        "required: Object");
  }

  public void testArrayAccess7() throws Exception {
    testTypes("var bar = void 0; bar[0];",
        "only arrays or objects can be accessed\n" +
        "found   : undefined\n" +
        "required: Object");
  }

  public void testArrayAccess8() throws Exception {
    // Verifies that we don't emit two warnings, because
    // the var has been dereferenced after the first one.
    testTypes("var bar = void 0; bar[0]; bar[1];",
        "only arrays or objects can be accessed\n" +
        "found   : undefined\n" +
        "required: Object");
  }

  public void testArrayAccess9() throws Exception {
    testTypes("/** @return {?Array} */ function f() { return []; }" +
        "f()[{}]",
        "array access\n" +
        "found   : {}\n" +
        "required: number");
  }

  public void testPropAccess() throws Exception {
    testTypes("/** @param {*} x */var f = function(x) {\n" +
        "var o = String(x);\n" +
        "if (typeof o['a'] != 'undefined') { return o['a']; }\n" +
        "return null;\n" +
        "};");
  }

  public void testPropAccess2() throws Exception {
    testTypes("var bar = void 0; bar.baz;",
        "No properties on this expression\n" +
        "found   : undefined\n" +
        "required: Object");
  }

  public void testPropAccess3() throws Exception {
    // Verifies that we don't emit two warnings, because
    // the var has been dereferenced after the first one.
    testTypes("var bar = void 0; bar.baz; bar.bax;",
        "No properties on this expression\n" +
        "found   : undefined\n" +
        "required: Object");
  }

  public void testPropAccess4() throws Exception {
    testTypes("/** @param {*} x */ function f(x) { return x['hi']; }");
  }

  public void testSwitchCase1() throws Exception {
    testTypes("/**@type number*/var a;" +
        "/**@type string*/var b;" +
        "switch(a){case b:;}",
        "case expression doesn't match switch\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testSwitchCase2() throws Exception {
    testTypes("var a = null; switch (typeof a) { case 'foo': }");
  }

  public void testVar1() throws Exception {
    TypeCheckResult p =
        parseAndTypeCheckWithScope("/** @type {(string,null)} */var a = null");

    assertTypeEquals(createUnionType(STRING_TYPE, NULL_TYPE),
        p.scope.getVar("a").getType());
  }

  public void testVar2() throws Exception {
    testTypes("/** @type {Function} */ var a = function(){}");
  }

  public void testVar3() throws Exception {
    TypeCheckResult p = parseAndTypeCheckWithScope("var a = 3;");

    assertTypeEquals(NUMBER_TYPE, p.scope.getVar("a").getType());
  }

  public void testVar4() throws Exception {
    TypeCheckResult p = parseAndTypeCheckWithScope(
        "var a = 3; a = 'string';");

    assertTypeEquals(createUnionType(STRING_TYPE, NUMBER_TYPE),
        p.scope.getVar("a").getType());
  }

  public void testVar5() throws Exception {
    testTypes("var goog = {};" +
        "/** @type string */goog.foo = 'hello';" +
        "/** @type number */var a = goog.foo;",
        "initializing variable\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testVar6() throws Exception {
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

  public void testVar7() throws Exception {
    testTypes("/** @type number */var a, b;",
        "declaration of multiple variables with shared type information");
  }

  public void testVar8() throws Exception {
    testTypes("var a, b;");
  }

  public void testVar9() throws Exception {
    testTypes("/** @enum */var a;",
        "enum initializer must be an object literal or an enum");
  }

  public void testVar10() throws Exception {
    testTypes("/** @type !Number */var foo = 'abc';",
        "initializing variable\n" +
        "found   : string\n" +
        "required: Number");
  }

  public void testVar11() throws Exception {
    testTypes("var /** @type !Date */foo = 'abc';",
        "initializing variable\n" +
        "found   : string\n" +
        "required: Date");
  }

  public void testVar12() throws Exception {
    testTypes("var /** @type !Date */foo = 'abc', " +
        "/** @type !RegExp */bar = 5;",
        new String[] {
        "initializing variable\n" +
        "found   : string\n" +
        "required: Date",
        "initializing variable\n" +
        "found   : number\n" +
        "required: RegExp"});
  }

  public void testVar13() throws Exception {
    // this caused an NPE
    testTypes("var /** @type number */a,a;");
  }

  public void testVar14() throws Exception {
    testTypes("/** @return {number} */ function f() { var x; return x; }",
        "inconsistent return type\n" +
        "found   : undefined\n" +
        "required: number");
  }

  public void testVar15() throws Exception {
    testTypes("/** @return {number} */" +
        "function f() { var x = x || {}; return x; }",
        "inconsistent return type\n" +
        "found   : {}\n" +
        "required: number");
  }

  public void testAssign1() throws Exception {
    testTypes("var goog = {};" +
        "/** @type number */goog.foo = 'hello';",
        "assignment to property foo of goog\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testAssign2() throws Exception {
    testTypes("var goog = {};" +
        "/** @type number */goog.foo = 3;" +
        "goog.foo = 'hello';",
        "assignment to property foo of goog\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testAssign3() throws Exception {
    testTypes("var goog = {};" +
        "/** @type number */goog.foo = 3;" +
        "goog.foo = 4;");
  }

  public void testAssign4() throws Exception {
    testTypes("var goog = {};" +
        "goog.foo = 3;" +
        "goog.foo = 'hello';");
  }

  public void testAssignInference() throws Exception {
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

  public void testOr1() throws Exception {
    testTypes("/** @type number */var a;" +
        "/** @type number */var b;" +
        "a + b || undefined;");
  }

  public void testOr2() throws Exception {
    testTypes("/** @type number */var a;" +
        "/** @type number */var b;" +
        "/** @type number */var c = a + b || undefined;",
        "initializing variable\n" +
        "found   : (number|undefined)\n" +
        "required: number");
  }

  public void testOr3() throws Exception {
    testTypes("/** @type {(number, undefined)} */var a;" +
        "/** @type number */var c = a || 3;");
  }

  /**
   * Test that type inference continues with the right side,
   * when no short-circuiting is possible.
   * See bugid 1205387 for more details.
   */
  public void testOr4() throws Exception {
     testTypes("/**@type {number} */var x;x=null || \"a\";",
         "assignment\n" +
         "found   : string\n" +
         "required: number");
  }

  /**
   * @see #testOr4()
   */
  public void testOr5() throws Exception {
     testTypes("/**@type {number} */var x;x=undefined || \"a\";",
         "assignment\n" +
         "found   : string\n" +
         "required: number");
  }

  public void testAnd1() throws Exception {
    testTypes("/** @type number */var a;" +
        "/** @type number */var b;" +
        "a + b && undefined;");
  }

  public void testAnd2() throws Exception {
    testTypes("/** @type number */var a;" +
        "/** @type number */var b;" +
        "/** @type number */var c = a + b && undefined;",
        "initializing variable\n" +
        "found   : (number|undefined)\n" +
        "required: number");
  }

  public void testAnd3() throws Exception {
    testTypes("/** @type {(!Array, undefined)} */var a;" +
        "/** @type number */var c = a && undefined;",
        "initializing variable\n" +
        "found   : undefined\n" +
        "required: number");
  }

  public void testAnd4() throws Exception {
    testTypes("/** @param {number} x */function f(x){};\n" +
        "/** @type null */var x; /** @type {number?} */var y;\n" +
        "if (x && y) { f(y) }");
  }

  public void testAnd5() throws Exception {
    testTypes("/** @param {number} x\n@param {string} y*/function f(x,y){};\n" +
        "/** @type {number?} */var x; /** @type {string?} */var y;\n" +
        "if (x && y) { f(x, y) }");
  }

  public void testAnd6() throws Exception {
    testTypes("/** @param {number} x */function f(x){};\n" +
        "/** @type {number|undefined} */var x;\n" +
        "if (x && f(x)) { f(x) }");
  }

  public void testAnd7() throws Exception {
    // TODO(user): a deterministic warning should be generated for this
    // case since x && x is always false. The implementation of this requires
    // a more precise handling of a null value within a variable's type.
    // Currently, a null value defaults to ? which passes every check.
    testTypes("/** @type null */var x; if (x && x) {}");
  }

  public void testAnd8() throws Exception {
    testTypes(
        "function f(/** (null | number | string) */ x) {\n" +
        "  (x && (typeof x === 'number')) && takesNum(x);\n" +
        "}\n" +
        "function takesNum(/** number */ n) {}");
  }

  public void testAnd9() throws Exception {
    testTypes(
        "function f(/** (number|string|null) */ x) {\n" +
        "  if (x && typeof x === 'number') {\n" +
        "    takesNum(x);\n" +
        "  }\n" +
        "}\n" +
        "function takesNum(/** number */ x) {}");
  }

  public void testAnd10() throws Exception {
    testTypes(
        "function f(/** (null | number | string) */ x) {\n" +
        "  (x && (typeof x === 'string')) && takesNum(x);\n" +
        "}\n" +
        "function takesNum(/** number */ n) {}",
        "actual parameter 1 of takesNum does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testHook() throws Exception {
    testTypes("/**@return {void}*/function foo(){ var x=foo()?a:b; }");
  }

  public void testHookRestrictsType1() throws Exception {
    testTypes("/** @return {(string,null)} */" +
        "function f() { return null;}" +
        "/** @type {(string,null)} */ var a = f();" +
        "/** @type string */" +
        "var b = a ? a : 'default';");
  }

  public void testHookRestrictsType2() throws Exception {
    testTypes("/** @type {String} */" +
        "var a = null;" +
        "/** @type null */" +
        "var b = a ? null : a;");
  }

  public void testHookRestrictsType3() throws Exception {
    testTypes("/** @type {String} */" +
        "var a;" +
        "/** @type null */" +
        "var b = (!a) ? a : null;");
  }

  public void testHookRestrictsType4() throws Exception {
    testTypes("/** @type {(boolean,undefined)} */" +
        "var a;" +
        "/** @type boolean */" +
        "var b = a != null ? a : true;");
  }

  public void testHookRestrictsType5() throws Exception {
    testTypes("/** @type {(boolean,undefined)} */" +
        "var a;" +
        "/** @type {(undefined)} */" +
        "var b = a == null ? a : undefined;");
  }

  public void testHookRestrictsType6() throws Exception {
    testTypes("/** @type {(number,null,undefined)} */" +
        "var a;" +
        "/** @type {number} */" +
        "var b = a == null ? 5 : a;");
  }

  public void testHookRestrictsType7() throws Exception {
    testTypes("/** @type {(number,null,undefined)} */" +
        "var a;" +
        "/** @type {number} */" +
        "var b = a == undefined ? 5 : a;");
  }

  public void testWhileRestrictsType1() throws Exception {
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

  public void testWhileRestrictsType2() throws Exception {
    testTypes("/** @param {number?} x\n@return {number}*/\n" +
        "function f(x) {\n/** @type {number} */var y = 0;" +
        "while (x) {\n" +
        "y = x;\n" +
        "x = x-1;\n}\n" +
        "return y;}");
  }

  public void testHigherOrderFunctions1() throws Exception {
    testTypes(
        "/** @type {function(number)} */var f;" +
        "f(true);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testHigherOrderFunctions2() throws Exception {
    testTypes(
        "/** @type {function():!Date} */var f;" +
        "/** @type boolean */var a = f();",
        "initializing variable\n" +
        "found   : Date\n" +
        "required: boolean");
  }

  public void testHigherOrderFunctions3() throws Exception {
    testTypes(
        "/** @type {function(this:Error):Date} */var f; new f",
        "cannot instantiate non-constructor");
  }

  public void testHigherOrderFunctions4() throws Exception {
    testTypes(
        "/** @type {function(this:Error, ...number):Date} */var f; new f",
        "cannot instantiate non-constructor");
  }

  public void testHigherOrderFunctions5() throws Exception {
    testTypes(
        "/** @param {number} x */ function g(x) {}" +
        "/** @type {function(new:Error, ...number):Date} */ var f;" +
        "g(new f());",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : Error\n" +
        "required: number");
  }

  public void testConstructorAlias1() throws Exception {
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

  public void testConstructorAlias2() throws Exception {
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

  public void testConstructorAlias3() throws Exception {
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

  public void testConstructorAlias4() throws Exception {
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

  public void testConstructorAlias5() throws Exception {
    testTypes(
        "/** @constructor */ var Foo = function() {};" +
        "/** @constructor */ var FooAlias = Foo;" +
        "/** @return {FooAlias} */ function foo() { " +
        "  return new Foo(); }");
  }

  public void testConstructorAlias6() throws Exception {
    testTypes(
        "/** @constructor */ var Foo = function() {};" +
        "/** @constructor */ var FooAlias = Foo;" +
        "/** @return {Foo} */ function foo() { " +
        "  return new FooAlias(); }");
  }

  public void testConstructorAlias7() throws Exception {
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

  public void testConstructorAlias8() throws Exception {
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

  public void testConstructorAlias9() throws Exception {
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

  public void testConstructorAlias10() throws Exception {
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

  public void testConstructorAlias11() throws Exception {
    testTypes(
        "/**\n * @param {number} x \n * @constructor */ " +
        "var Foo = function(x) {};" +
        "/** @const */ var FooAlias = Foo;" +
        "/** @const */ var FooAlias2 = FooAlias;" +
        "/** @return {FooAlias2} */ function foo() { " +
        "  return 1; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: (FooAlias2|null)");
  }

  public void testClosure1() throws Exception {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @type {string|undefined} */var a;" +
        "/** @type string */" +
        "var b = goog.isDef(a) ? a : 'default';",
        null);
  }

  public void testClosure2() throws Exception {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @type {string?} */var a;" +
        "/** @type string */" +
        "var b = goog.isNull(a) ? 'default' : a;",
        null);
  }

  public void testClosure3() throws Exception {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @type {string|null|undefined} */var a;" +
        "/** @type string */" +
        "var b = goog.isDefAndNotNull(a) ? a : 'default';",
        null);
  }

  public void testClosure4() throws Exception {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @type {string|undefined} */var a;" +
        "/** @type string */" +
        "var b = !goog.isDef(a) ? 'default' : a;",
        null);
  }

  public void testClosure5() throws Exception {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @type {string?} */var a;" +
        "/** @type string */" +
        "var b = !goog.isNull(a) ? a : 'default';",
        null);
  }

  public void testClosure6() throws Exception {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @type {string|null|undefined} */var a;" +
        "/** @type string */" +
        "var b = !goog.isDefAndNotNull(a) ? 'default' : a;",
        null);
  }

  public void testClosure7() throws Exception {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @type {string|null|undefined} */ var a = foo();" +
        "/** @type {number} */" +
        "var b = goog.asserts.assert(a);",
        "initializing variable\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testReturn1() throws Exception {
    testTypes("/**@return {void}*/function foo(){ return 3; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: undefined");
  }

  public void testReturn2() throws Exception {
    testTypes("/**@return {!Number}*/function foo(){ return; }",
        "inconsistent return type\n" +
        "found   : undefined\n" +
        "required: Number");
  }

  public void testReturn3() throws Exception {
    testTypes("/**@return {!Number}*/function foo(){ return 'abc'; }",
        "inconsistent return type\n" +
        "found   : string\n" +
        "required: Number");
  }

  public void testReturn4() throws Exception {
    testTypes("/**@return {!Number}\n*/\n function a(){return new Array();}",
        "inconsistent return type\n" +
        "found   : Array\n" +
        "required: Number");
  }

  public void testReturn5() throws Exception {
    testTypes("/** @param {number} n\n" +
        "@constructor */function n(n){return};");
  }

  public void testReturn6() throws Exception {
    testTypes(
        "/** @param {number} opt_a\n@return {string} */" +
        "function a(opt_a) { return opt_a }",
        "inconsistent return type\n" +
        "found   : (number|undefined)\n" +
        "required: string");
  }

  public void testReturn7() throws Exception {
    testTypes("/** @constructor */var A = function() {};\n" +
        "/** @constructor */var B = function() {};\n" +
        "/** @return {!B} */A.f = function() { return 1; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: B");
  }

  public void testReturn8() throws Exception {
    testTypes("/** @constructor */var A = function() {};\n" +
        "/** @constructor */var B = function() {};\n" +
        "/** @return {!B} */A.prototype.f = function() { return 1; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: B");
  }

  public void testInferredReturn1() throws Exception {
    testTypes(
        "function f() {} /** @param {number} x */ function g(x) {}" +
        "g(f());",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : undefined\n" +
        "required: number");
  }

  public void testInferredReturn2() throws Exception {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() {}; " +
        "/** @param {number} x */ function g(x) {}" +
        "g((new Foo()).bar());",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : undefined\n" +
        "required: number");
  }

  public void testInferredReturn3() throws Exception {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() {}; " +
        "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
        "/** @return {number} \n * @override  */ " +
        "SubFoo.prototype.bar = function() { return 3; }; ",
        "mismatch of the bar property type and the type of the property " +
        "it overrides from superclass Foo\n" +
        "original: function (this:Foo): undefined\n" +
        "override: function (this:SubFoo): number");
  }

  public void testInferredReturn4() throws Exception {
    // By design, this throws a warning. if you want global x to be
    // defined to some other type of function, then you need to declare it
    // as a greater type.
    testTypes(
        "var x = function() {};" +
        "x = /** @type {function(): number} */ (function() { return 3; });",
        "assignment\n" +
        "found   : function (): number\n" +
        "required: function (): undefined");
  }

  public void testInferredReturn5() throws Exception {
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

  public void testInferredReturn6() throws Exception {
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

  public void testInferredReturn7() throws Exception {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @param {number} x */ Foo.prototype.bar = function(x) {};" +
        "Foo.prototype.bar = function(x) { return 3; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: undefined");
  }

  public void testInferredReturn8() throws Exception {
    reportMissingOverrides = CheckLevel.OFF;
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @param {number} x */ Foo.prototype.bar = function(x) {};" +
        "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
        "/** @param {number} x */ SubFoo.prototype.bar = " +
        "    function(x) { return 3; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: undefined");
  }

  public void testInferredParam1() throws Exception {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @param {number} x */ Foo.prototype.bar = function(x) {};" +
        "/** @param {string} x */ function f(x) {}" +
        "Foo.prototype.bar = function(y) { f(y); };",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testInferredParam2() throws Exception {
    reportMissingOverrides = CheckLevel.OFF;
    testTypes(
        "/** @param {string} x */ function f(x) {}" +
        "/** @constructor */ function Foo() {}" +
        "/** @param {number} x */ Foo.prototype.bar = function(x) {};" +
        "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
        "/** @return {void} */ SubFoo.prototype.bar = " +
        "    function(x) { f(x); }",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testInferredParam3() throws Exception {
    reportMissingOverrides = CheckLevel.OFF;
    testTypes(
        "/** @param {string} x */ function f(x) {}" +
        "/** @constructor */ function Foo() {}" +
        "/** @param {number=} x */ Foo.prototype.bar = function(x) {};" +
        "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
        "/** @return {void} */ SubFoo.prototype.bar = " +
        "    function(x) { f(x); }; (new SubFoo()).bar();",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : (number|undefined)\n" +
        "required: string");
  }

  public void testInferredParam4() throws Exception {
    reportMissingOverrides = CheckLevel.OFF;
    testTypes(
        "/** @param {string} x */ function f(x) {}" +
        "/** @constructor */ function Foo() {}" +
        "/** @param {...number} x */ Foo.prototype.bar = function(x) {};" +
        "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
        "/** @return {void} */ SubFoo.prototype.bar = " +
        "    function(x) { f(x); }; (new SubFoo()).bar();",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : (number|undefined)\n" +
        "required: string");
  }

  public void testInferredParam5() throws Exception {
    reportMissingOverrides = CheckLevel.OFF;
    testTypes(
        "/** @param {string} x */ function f(x) {}" +
        "/** @constructor */ function Foo() {}" +
        "/** @param {...number} x */ Foo.prototype.bar = function(x) {};" +
        "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
        "/** @param {number=} x \n * @param {...number} y  */ " +
        "SubFoo.prototype.bar = " +
        "    function(x, y) { f(x); }; (new SubFoo()).bar();",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : (number|undefined)\n" +
        "required: string");
  }

  public void testInferredParam6() throws Exception {
    reportMissingOverrides = CheckLevel.OFF;
    testTypes(
        "/** @param {string} x */ function f(x) {}" +
        "/** @constructor */ function Foo() {}" +
        "/** @param {number=} x */ Foo.prototype.bar = function(x) {};" +
        "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
        "/** @param {number=} x \n * @param {number=} y */ " +
        "SubFoo.prototype.bar = " +
        "    function(x, y) { f(y); };",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : (number|undefined)\n" +
        "required: string");
  }

  public void testInferredParam7() throws Exception {
    testTypes(
        "/** @param {string} x */ function f(x) {}" +
        "var bar = /** @type {function(number=,number=)} */ (" +
        "    function(x, y) { f(y); });",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : (number|undefined)\n" +
        "required: string");
  }

  public void testOverriddenParams1() throws Exception {
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

  public void testOverriddenParams2() throws Exception {
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

  public void testOverriddenParams3() throws Exception {
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
        "original: function (this:Foo, ...number): undefined\n" +
        "override: function (this:SubFoo, number): undefined");
  }

  public void testOverriddenParams4() throws Exception {
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
        "original: function (...number): ?\n" +
        "override: function (number): ?");
  }

  public void testOverriddenParams5() throws Exception {
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

  public void testOverriddenParams6() throws Exception {
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

  public void testOverriddenParams7() throws Exception {
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
        "original: function (this:Foo, string): undefined\n" +
        "override: function (this:SubFoo, number): undefined");
  }

  public void testOverriddenReturn1() throws Exception {
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

  public void testOverriddenReturn2() throws Exception {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @return {SubFoo} */ Foo.prototype.bar = " +
        "    function() { return new SubFoo(); };" +
        "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
        "/** @return {Foo} x\n * @override */ SubFoo.prototype.bar = " +
        "    function() { return new SubFoo(); }",
        "mismatch of the bar property type and the type of the " +
        "property it overrides from superclass Foo\n" +
        "original: function (this:Foo): (SubFoo|null)\n" +
        "override: function (this:SubFoo): (Foo|null)");
  }

  public void testOverriddenReturn3() throws Exception {
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

  public void testOverriddenReturn4() throws Exception {
    testTypes(
        "/** @constructor \n * @template T */ function Foo() {}" +
        "/** @return {T} */ Foo.prototype.bar = " +
        "    function() { return null; };" +
        "/** @constructor \n * @extends {Foo<string>} */ function SubFoo() {}" +
        "/** @return {number}\n * @override */ SubFoo.prototype.bar = " +
        "    function() { return 3; }",
        "mismatch of the bar property type and the type of the " +
        "property it overrides from superclass Foo\n" +
        "original: function (this:Foo): string\n" +
        "override: function (this:SubFoo): number");
  }

  public void testThis1() throws Exception {
    testTypes("var goog = {};" +
        "/** @constructor */goog.A = function(){};" +
        "/** @return {number} */" +
        "goog.A.prototype.n = function() { return this };",
        "inconsistent return type\n" +
        "found   : goog.A\n" +
        "required: number");
  }

  public void testOverriddenProperty1() throws Exception {
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

  public void testOverriddenProperty2() throws Exception {
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

  public void testOverriddenProperty3() throws Exception {
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

  public void testOverriddenProperty4() throws Exception {
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

  public void testOverriddenProperty5() throws Exception {
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

  public void testOverriddenProperty6() throws Exception {
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

  public void testThis2() throws Exception {
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

  public void testThis3() throws Exception {
    testTypes("var goog = {};" +
        "/** @constructor */goog.A = function(){" +
        "  this.foo = null;" +
        "  this.foo = 5;" +
        "};");
  }

  public void testThis4() throws Exception {
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

  public void testThis5() throws Exception {
    testTypes("/** @this Date\n@return {number}*/function h() { return this }",
        "inconsistent return type\n" +
        "found   : Date\n" +
        "required: number");
  }

  public void testThis6() throws Exception {
    testTypes("var goog = {};" +
        "/** @constructor\n@return {!Date} */" +
        "goog.A = function(){ return this };",
        "inconsistent return type\n" +
        "found   : goog.A\n" +
        "required: Date");
  }

  public void testThis7() throws Exception {
    testTypes("/** @constructor */function A(){};" +
        "/** @return {number} */A.prototype.n = function() { return this };",
        "inconsistent return type\n" +
        "found   : A\n" +
        "required: number");
  }

  public void testThis8() throws Exception {
    testTypes("/** @constructor */function A(){" +
        "  /** @type {string?} */this.foo = null;" +
        "};" +
        "/** @return {number} */A.prototype.n = function() {" +
        "  return this.foo };",
        "inconsistent return type\n" +
        "found   : (null|string)\n" +
        "required: number");
  }

  public void testThis9() throws Exception {
    // In A.bar, the type of {@code this} is unknown.
    testTypes("/** @constructor */function A(){};" +
        "A.prototype.foo = 3;" +
        "/** @return {string} */ A.bar = function() { return this.foo; };");
  }

  public void testThis10() throws Exception {
    // In A.bar, the type of {@code this} is inferred from the @this tag.
    testTypes("/** @constructor */function A(){};" +
        "A.prototype.foo = 3;" +
        "/** @this {A}\n@return {string} */" +
        "A.bar = function() { return this.foo; };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testThis11() throws Exception {
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

  public void testThis12() throws Exception {
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

  public void testThis13() throws Exception {
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

  public void testThis14() throws Exception {
    testTypes(
        "/** @param {number} x */ function f(x) {}" +
        "f(this.Object);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : function (new:Object, *=): Object\n" +
        "required: number");
  }

  public void testThisTypeOfFunction1() throws Exception {
    testTypes(
        "/** @type {function(this:Object)} */ function f() {}" +
        "f();");
  }

  public void testThisTypeOfFunction2() throws Exception {
    testTypes(
        "/** @constructor */ function F() {}" +
        "/** @type {function(this:F)} */ function f() {}" +
        "f();",
        "\"function (this:F): ?\" must be called with a \"this\" type");
  }

  public void testThisTypeOfFunction3() throws Exception {
    testTypes(
        "/** @constructor */ function F() {}" +
        "F.prototype.bar = function() {};" +
        "var f = (new F()).bar; f();",
        "\"function (this:F): undefined\" must be called with a \"this\" type");
  }

  public void testThisTypeOfFunction4() throws Exception {
    testTypes(
        "/** @constructor */ function F() {}" +
        "F.prototype.moveTo = function(x, y) {};" +
        "F.prototype.lineTo = function(x, y) {};" +
        "function demo() {" +
        "  var path = new F();" +
        "  var points = [[1,1], [2,2]];" +
        "  for (var i = 0; i < points.length; i++) {" +
        "    (i == 0 ? path.moveTo : path.lineTo)(" +
        "       points[i][0], points[i][1]);" +
        "  }" +
        "}",
        "\"function (this:F, ?, ?): undefined\" " +
        "must be called with a \"this\" type");
  }

  public void testGlobalThis1() throws Exception {
    testTypes("/** @constructor */ function Window() {}" +
        "/** @param {string} msg */ " +
        "Window.prototype.alert = function(msg) {};" +
        "this.alert(3);",
        "actual parameter 1 of Window.prototype.alert " +
        "does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testGlobalThis2() throws Exception {
    // this.alert = 3 doesn't count as a declaration, so this isn't a warning.
    testTypes("/** @constructor */ function Bindow() {}" +
        "/** @param {string} msg */ " +
        "Bindow.prototype.alert = function(msg) {};" +
        "this.alert = 3;" +
        "(new Bindow()).alert(this.alert)");
  }


  public void testGlobalThis2b() throws Exception {
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

  public void testGlobalThis3() throws Exception {
    testTypes(
        "/** @param {string} msg */ " +
        "function alert(msg) {};" +
        "this.alert(3);",
        "actual parameter 1 of global this.alert " +
        "does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testGlobalThis4() throws Exception {
    testTypes(
        "/** @param {string} msg */ " +
        "var alert = function(msg) {};" +
        "this.alert(3);",
        "actual parameter 1 of global this.alert " +
        "does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testGlobalThis5() throws Exception {
    testTypes(
        "function f() {" +
        "  /** @param {string} msg */ " +
        "  var alert = function(msg) {};" +
        "}" +
        "this.alert(3);",
        "Property alert never defined on global this");
  }

  public void testGlobalThis6() throws Exception {
    testTypes(
        "/** @param {string} msg */ " +
        "var alert = function(msg) {};" +
        "var x = 3;" +
        "x = 'msg';" +
        "this.alert(this.x);");
  }

  public void testGlobalThis7() throws Exception {
    testTypes(
        "/** @constructor */ function Window() {}" +
        "/** @param {Window} msg */ " +
        "var foo = function(msg) {};" +
        "foo(this);");
  }

  public void testGlobalThis8() throws Exception {
    testTypes(
        "/** @constructor */ function Window() {}" +
        "/** @param {number} msg */ " +
        "var foo = function(msg) {};" +
        "foo(this);",
        "actual parameter 1 of foo does not match formal parameter\n" +
        "found   : global this\n" +
        "required: number");
  }

  public void testGlobalThis9() throws Exception {
    testTypes(
        // Window is not marked as a constructor, so the
        // inheritance doesn't happen.
        "function Window() {}" +
        "Window.prototype.alert = function() {};" +
        "this.alert();",
        "Property alert never defined on global this");
  }

  public void testControlFlowRestrictsType1() throws Exception {
    testTypes("/** @return {String?} */ function f() { return null; }" +
        "/** @type {String?} */ var a = f();" +
        "/** @type String */ var b = new String('foo');" +
        "/** @type null */ var c = null;" +
        "if (a) {" +
        "  b = a;" +
        "} else {" +
        "  c = a;" +
        "}");
  }

  public void testControlFlowRestrictsType2() throws Exception {
    testTypes("/** @return {(string,null)} */ function f() { return null; }" +
        "/** @type {(string,null)} */ var a = f();" +
        "/** @type string */ var b = 'foo';" +
        "/** @type null */ var c = null;" +
        "if (a) {" +
        "  b = a;" +
        "} else {" +
        "  c = a;" +
        "}",
        "assignment\n" +
        "found   : (null|string)\n" +
        "required: null");
  }

  public void testControlFlowRestrictsType3() throws Exception {
    testTypes("/** @type {(string,void)} */" +
        "var a;" +
        "/** @type string */" +
        "var b = 'foo';" +
        "if (a) {" +
        "  b = a;" +
        "}");
  }

  public void testControlFlowRestrictsType4() throws Exception {
    testTypes("/** @param {string} a */ function f(a){}" +
        "/** @type {(string,undefined)} */ var a;" +
        "a && f(a);");
  }

  public void testControlFlowRestrictsType5() throws Exception {
    testTypes("/** @param {undefined} a */ function f(a){}" +
        "/** @type {(!Array,undefined)} */ var a;" +
        "a || f(a);");
  }

  public void testControlFlowRestrictsType6() throws Exception {
    testTypes("/** @param {undefined} x */ function f(x) {}" +
        "/** @type {(string,undefined)} */ var a;" +
        "a && f(a);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: undefined");
  }

  public void testControlFlowRestrictsType7() throws Exception {
    testTypes("/** @param {undefined} x */ function f(x) {}" +
        "/** @type {(string,undefined)} */ var a;" +
        "a && f(a);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: undefined");
  }

  public void testControlFlowRestrictsType8() throws Exception {
    testTypes("/** @param {undefined} a */ function f(a){}" +
        "/** @type {(!Array,undefined)} */ var a;" +
        "if (a || f(a)) {}");
  }

  public void testControlFlowRestrictsType9() throws Exception {
    testTypes("/** @param {number?} x\n * @return {number}*/\n" +
        "var f = function(x) {\n" +
        "if (!x || x == 1) { return 1; } else { return x; }\n" +
        "};");
  }

  public void testControlFlowRestrictsType10() throws Exception {
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

  public void testControlFlowRestrictsType11() throws Exception {
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

  public void testSwitchCase3() throws Exception {
    testTypes("/** @type String */" +
        "var a = new String('foo');" +
        "switch (a) { case 'A': }");
  }

  public void testSwitchCase4() throws Exception {
    testTypes("/** @type {(string,Null)} */" +
        "var a = unknown;" +
        "switch (a) { case 'A':break; case null:break; }");
  }

  public void testSwitchCase5() throws Exception {
    testTypes("/** @type {(String,Null)} */" +
        "var a = unknown;" +
        "switch (a) { case 'A':break; case null:break; }");
  }

  public void testSwitchCase6() throws Exception {
    testTypes("/** @type {(Number,Null)} */" +
        "var a = unknown;" +
        "switch (a) { case 5:break; case null:break; }");
  }

  public void testSwitchCase7() throws Exception {
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

  public void testSwitchCase8() throws Exception {
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

  public void testImplicitCast() throws Exception {
    testTypesWithExterns("/** @constructor */ function Element() {};\n" +
             "/** @type {string}\n" +
             "  * @implicitCast */" +
             "Element.prototype.innerHTML;",
             "(new Element).innerHTML = new Array();");
  }

  public void testImplicitCastSubclassAccess() throws Exception {
    testTypesWithExterns("/** @constructor */ function Element() {};\n" +
             "/** @type {string}\n" +
             "  * @implicitCast */" +
             "Element.prototype.innerHTML;" +
             "/** @constructor \n @extends Element */" +
             "function DIVElement() {};",
             "(new DIVElement).innerHTML = new Array();");
  }

  public void testImplicitCastNotInExterns() throws Exception {
    testTypes("/** @constructor */ function Element() {};\n" +
             "/** @type {string}\n" +
             "  * @implicitCast */" +
             "Element.prototype.innerHTML;" +
             "(new Element).innerHTML = new Array();",
             new String[] {
               "Illegal annotation on innerHTML. @implicitCast may only be " +
               "used in externs.",
               "assignment to property innerHTML of Element\n" +
               "found   : Array\n" +
               "required: string"});
  }

  public void testNumberNode() throws Exception {
    Node n = typeCheck(Node.newNumber(0));

    assertTypeEquals(NUMBER_TYPE, n.getJSType());
  }

  public void testStringNode() throws Exception {
    Node n = typeCheck(Node.newString("hello"));

    assertTypeEquals(STRING_TYPE, n.getJSType());
  }

  public void testBooleanNodeTrue() throws Exception {
    Node trueNode = typeCheck(new Node(Token.TRUE));

    assertTypeEquals(BOOLEAN_TYPE, trueNode.getJSType());
  }

  public void testBooleanNodeFalse() throws Exception {
    Node falseNode = typeCheck(new Node(Token.FALSE));

    assertTypeEquals(BOOLEAN_TYPE, falseNode.getJSType());
  }

  public void testUndefinedNode() throws Exception {
    Node p = new Node(Token.ADD);
    Node n = Node.newString(Token.NAME, "undefined");
    p.addChildToBack(n);
    p.addChildToBack(Node.newNumber(5));
    typeCheck(p);

    assertTypeEquals(VOID_TYPE, n.getJSType());
  }

  public void testNumberAutoboxing() throws Exception {
    testTypes("/** @type Number */var a = 4;",
        "initializing variable\n" +
        "found   : number\n" +
        "required: (Number|null)");
  }

  public void testNumberUnboxing() throws Exception {
    testTypes("/** @type number */var a = new Number(4);",
        "initializing variable\n" +
        "found   : Number\n" +
        "required: number");
  }

  public void testStringAutoboxing() throws Exception {
    testTypes("/** @type String */var a = 'hello';",
        "initializing variable\n" +
        "found   : string\n" +
        "required: (String|null)");
  }

  public void testStringUnboxing() throws Exception {
    testTypes("/** @type string */var a = new String('hello');",
        "initializing variable\n" +
        "found   : String\n" +
        "required: string");
  }

  public void testBooleanAutoboxing() throws Exception {
    testTypes("/** @type Boolean */var a = true;",
        "initializing variable\n" +
        "found   : boolean\n" +
        "required: (Boolean|null)");
  }

  public void testBooleanUnboxing() throws Exception {
    testTypes("/** @type boolean */var a = new Boolean(false);",
        "initializing variable\n" +
        "found   : Boolean\n" +
        "required: boolean");
  }

  public void testIIFE1() throws Exception {
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

  public void testIIFE2() throws Exception {
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

  public void testIIFE3() throws Exception {
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

  public void testIIFE4() throws Exception {
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

  public void testIIFE5() throws Exception {
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

  public void testNotIIFE1() throws Exception {
    testTypes(
        "/** @param {number} x */ function f(x) {}" +
        "/** @param {...?} x */ function g(x) {}" +
        "g(function(y) { f(y); }, true);");
  }

  public void testNamespaceType1() throws Exception {
    testTypes(
        "/** @namespace */ var x = {};" +
        "/** @param {x.} y */ function f(y) {};",
        "Parse error. Namespaces not supported yet (x.)");
  }

  public void testNamespaceType2() throws Exception {
    testTypes(
        "/** @namespace */ var x = {};" +
        "/** @namespace */ x.y = {};" +
        "/** @param {x.y.} y */ function f(y) {}",
        "Parse error. Namespaces not supported yet (x.y.)");
  }

  public void testIssue61() throws Exception {
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

  public void testIssue61b() throws Exception {
    testTypes(
        "var ns = {};" +
        "(function() {" +
        "  /** @param {string} b */" +
        "  ns.a = function(b) {};" +
        "})();" +
        "ns.a(123);",
        "actual parameter 1 of ns.a does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testIssue86() throws Exception {
    testTypes(
        "/** @interface */ function I() {}" +
        "/** @return {number} */ I.prototype.get = function(){};" +
        "/** @constructor \n * @implements {I} */ function F() {}" +
        "/** @override */ F.prototype.get = function() { return true; };",
        "inconsistent return type\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testIssue124() throws Exception {
    testTypes(
        "var t = null;" +
        "function test() {" +
        "  if (t != null) { t = null; }" +
        "  t = 1;" +
        "}");
  }

  public void testIssue124b() throws Exception {
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

  public void testIssue259() throws Exception {
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

  public void testIssue301() throws Exception {
    testTypes(
        "Array.indexOf = function() {};" +
        "var s = 'hello';" +
        "alert(s.toLowerCase.indexOf('1'));",
        "Property indexOf never defined on String.prototype.toLowerCase");
  }

  public void testIssue368() throws Exception {
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

  public void testIssue380() throws Exception {
    testTypes(
        "/** @type { function(string): {innerHTML: string} } */\n" +
        "document.getElementById;\n" +
        "var list = /** @type {!Array<string>} */ ['hello', 'you'];\n" +
        "list.push('?');\n" +
        "document.getElementById('node').innerHTML = list.toString();",
        // Parse warning, but still applied.
        "Type annotations are not allowed here. " +
        "Are you missing parentheses?");
  }

  public void testIssue483() throws Exception {
    testTypes(
        "/** @constructor */ function C() {" +
        "  /** @type {?Array} */ this.a = [];" +
        "}" +
        "C.prototype.f = function() {" +
        "  if (this.a.length > 0) {" +
        "    g(this.a);" +
        "  }" +
        "};" +
        "/** @param {number} a */ function g(a) {}",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : Array\n" +
        "required: number");
  }

  public void testIssue537a() throws Exception {
    testTypes(
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

  public void testIssue537b() throws Exception {
    testTypes(
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

  public void testIssue537c() throws Exception {
    testTypes(
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

  public void testIssue537d() throws Exception {
    reportMissingOverrides = CheckLevel.OFF;
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype = {" +
        "  /** @return {Bar} */ x: function() { new Bar(); }," +
        "  /** @return {Foo} */ y: function() { new Bar(); }" +
        "};" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */\n" +
        "function Bar() {" +
        "  this.xy = 3;" +
        "}" +
        "/** @return {Bar} */ function f() { return new Bar(); }" +
        "/** @return {Foo} */ function g() { return new Bar(); }" +
        "Bar.prototype = {" +
        "  /** @return {Bar} */ x: function() { new Bar(); }," +
        "  /** @return {Foo} */ y: function() { new Bar(); }" +
        "};" +
        "Bar.prototype.__proto__ = Foo.prototype;");
  }

  public void testIssue586() throws Exception {
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

  public void testIssue635() throws Exception {
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

  public void testIssue635b() throws Exception {
    testTypes(
        "/** @constructor */" +
        "function F() {}" +
        "/** @constructor */" +
        "function G() {}" +
        "/** @type {function(new:G)} */ var x = F;",
        "initializing variable\n" +
        "found   : function (new:F): undefined\n" +
        "required: function (new:G): ?");
  }

  public void testIssue669() throws Exception {
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

  public void testIssue688() throws Exception {
    testTypes(
        "/** @const */ var SOME_DEFAULT =\n" +
        "    /** @type {TwoNumbers} */ ({first: 1, second: 2});\n" +
        "/**\n" +
        "* Class defining an interface with two numbers.\n" +
        "* @interface\n" +
        "*/\n" +
        "function TwoNumbers() {}\n" +
        "/** @type number */\n" +
        "TwoNumbers.prototype.first;\n" +
        "/** @type number */\n" +
        "TwoNumbers.prototype.second;\n" +
        "/** @return {number} */ function f() { return SOME_DEFAULT; }",
        "inconsistent return type\n" +
        "found   : (TwoNumbers|null)\n" +
        "required: number");
  }

  public void testIssue700() throws Exception {
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

  public void testIssue725() throws Exception {
    testTypes(
        "/** @typedef {{name: string}} */ var RecordType1;" +
        "/** @typedef {{name2222: string}} */ var RecordType2;" +
        "/** @param {RecordType1} rec */ function f(rec) {" +
        "  alert(rec.name2222);" +
        "}",
        "Property name2222 never defined on rec");
  }

  public void testIssue726() throws Exception {
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

  public void testIssue765() throws Exception {
    testTypes(
        "/** @constructor */" +
        "var AnotherType = function (parent) {" +
        "    /** @param {string} stringParameter Description... */" +
        "    this.doSomething = function (stringParameter) {};" +
        "};" +
        "/** @constructor */" +
        "var YetAnotherType = function () {" +
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

  public void testIssue783() throws Exception {
    testTypes(
        "/** @constructor */" +
        "var Type = function () {" +
        "  /** @type {Type} */" +
        "  this.me_ = this;" +
        "};" +
        "Type.prototype.doIt = function() {" +
        "  var me = this.me_;" +
        "  for (var i = 0; i < me.unknownProp; i++) {}" +
        "};",
        "Property unknownProp never defined on Type");
  }

  public void testIssue791() throws Exception {
    testTypes(
        "/** @param {{func: function()}} obj */" +
        "function test1(obj) {}" +
        "var fnStruc1 = {};" +
        "fnStruc1.func = function() {};" +
        "test1(fnStruc1);");
  }

  public void testIssue810() throws Exception {
    testTypes(
        "/** @constructor */" +
        "var Type = function () {" +
        "};" +
        "Type.prototype.doIt = function(obj) {" +
        "  this.prop = obj.unknownProp;" +
        "};",
        "Property unknownProp never defined on obj");
  }

  public void testIssue1002() throws Exception {
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

  public void testIssue1023() throws Exception {
    testTypes(
        "/** @constructor */" +
        "function F() {}" +
        "(function () {" +
        "  F.prototype = {" +
        "    /** @param {string} x */" +
        "    bar: function (x) {  }" +
        "  };" +
        "})();" +
        "(new F()).bar(true)",
        "actual parameter 1 of F.prototype.bar does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: string");
  }

  public void testIssue1047() throws Exception {
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

  public void testIssue1056() throws Exception {
    testTypes(
        "/** @type {Array} */ var x = null;" +
        "x.push('hi');",
        "No properties on this expression\n" +
        "found   : null\n" +
        "required: Object");
  }

  public void testIssue1072() throws Exception {
    testTypes(
        "/**\n" +
        " * @param {string} x\n" +
        " * @return {number}\n" +
        " */\n" +
        "var f1 = function (x) {\n" +
        "  return 3;\n" +
        "};\n" +
        "\n" +
        "/** Function */\n" +
        "var f2 = function (x) {\n" +
        "  if (!x) throw new Error()\n" +
        "  return /** @type {number} */ (f1('x'))\n" +
        "}\n" +
        "\n" +
        "/**\n" +
        " * @param {string} x\n" +
        " */\n" +
        "var f3 = function (x) {};\n" +
        "\n" +
        "f1(f3);",
        "actual parameter 1 of f1 does not match formal parameter\n" +
        "found   : function (string): undefined\n" +
        "required: string");
  }

  public void testIssue1123() throws Exception {
    testTypes(
        "/** @param {function(number)} g */ function f(g) {}" +
        "f(function(a, b) {})",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : function (?, ?): undefined\n" +
        "required: function (number): ?");
  }

  public void testIssue1201() throws Exception {
    testTypes(
        "/** @param {function(this:void)} f */ function g(f) {}" +
        "/** @constructor */ function F() {}" +
        "/** desc */ F.prototype.bar = function() {};" +
        "g(new F().bar);",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : function (this:F): undefined\n" +
        "required: function (this:undefined): ?");
  }

  public void testIssue1201b() throws Exception {
    testTypes(
        "/** @param {function(this:void)} f */ function g(f) {}" +
        "/** @constructor */ function F() {}" +
        "/** desc */ F.prototype.bar = function() {};" +
        "var f = new F();" +
        "g(f.bar.bind(f));");
  }

  public void testIssue1201c() throws Exception {
    testTypes(
        "/** @param {function(this:void)} f */ function g(f) {}" +
        "g(function() { this.alert() })",
        "No properties on this expression\n" +
        "found   : undefined\n" +
        "required: Object");
  }

  public void testIssue926a() throws Exception {
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

  public void testIssue926b() throws Exception {
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

  public void testEnums() throws Exception {
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
   * Tests that the || operator is type checked correctly, that is of
   * the type of the first argument or of the second argument. See
   * bugid 592170 for more details.
   */
  public void testBug592170() throws Exception {
    testTypes(
        "/** @param {Function} opt_f ... */" +
        "function foo(opt_f) {" +
        "  /** @type {Function} */" +
        "  return opt_f || function () {};" +
        "}",
        "Type annotations are not allowed here. Are you missing parentheses?");
  }

  /**
   * Tests that undefined can be compared shallowly to a value of type
   * (number,undefined) regardless of the side on which the undefined
   * value is.
   */
  public void testBug901455() throws Exception {
    testTypes("/** @return {(number,undefined)} */ function a() { return 3; }" +
        "var b = undefined === a()");
    testTypes("/** @return {(number,undefined)} */ function a() { return 3; }" +
        "var b = a() === undefined");
  }

  /**
   * Tests that the match method of strings returns nullable arrays.
   */
  public void testBug908701() throws Exception {
    testTypes("/** @type {String} */var s = new String('foo');" +
        "var b = s.match(/a/) != null;");
  }

  /**
   * Tests that named types play nicely with subtyping.
   */
  public void testBug908625() throws Exception {
    testTypes("/** @constructor */function A(){}" +
        "/** @constructor\n * @extends A */function B(){}" +
        "/** @param {B} b" +
        "\n @return {(A,undefined)} */function foo(b){return b}");
  }

  /**
   * Tests that assigning two untyped functions to a variable whose type is
   * inferred and calling this variable is legal.
   */
  public void testBug911118() throws Exception {
    // verifying the type assigned to function expressions assigned variables
    TypedScope s = parseAndTypeCheckWithScope("var a = function(){};").scope;
    JSType type = s.getVar("a").getType();
    assertEquals("function (): undefined", type.toString());

    // verifying the bug example
    testTypes("function nullFunction() {};" +
        "var foo = nullFunction;" +
        "foo = function() {};" +
        "foo();");
  }

  public void testBug909000() throws Exception {
    testTypes("/** @constructor */function A(){}\n" +
        "/** @param {!A} a\n" +
        "@return {boolean}*/\n" +
        "function y(a) { return a }",
        "inconsistent return type\n" +
        "found   : A\n" +
        "required: boolean");
  }

  public void testBug930117() throws Exception {
    testTypes(
        "/** @param {boolean} x */function f(x){}" +
        "f(null);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : null\n" +
        "required: boolean");
  }

  public void testBug1484445() throws Exception {
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

  public void testBug1859535() throws Exception {
    testTypes(
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

  public void testBug1940591() throws Exception {
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

  public void testBug1942972() throws Exception {
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

  public void testBug1943776() throws Exception {
    testTypes(
        "/** @return  {{foo: Array}} */" +
        "function bar() {" +
        "  return {foo: []};" +
        "}");
  }

  public void testBug1987544() throws Exception {
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

  public void testBug1940769() throws Exception {
    testTypes(
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

  public void testBug2335992() throws Exception {
    testTypes(
        "/** @return {*} */ function f() { return 3; }" +
        "var x = f();" +
        "/** @type {string} */" +
        "x.y = 3;",
        "assignment\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testBug2341812() throws Exception {
    testTypes(
        "/** @interface */" +
        "function EventTarget() {}" +
        "/** @constructor \n * @implements {EventTarget} */" +
        "function Node() {}" +
        "/** @type {number} */ Node.prototype.index;" +
        "/** @param {EventTarget} x \n * @return {string} */" +
        "function foo(x) { return x.index; }");
  }

  public void testBug7701884() throws Exception {
    testTypes(
        "/**\n" +
        " * @param {Array<T>} x\n" +
        " * @param {function(T)} y\n" +
        " * @template T\n" +
        " */\n" +
        "var forEach = function(x, y) {\n" +
        "  for (var i = 0; i < x.length; i++) y(x[i]);\n" +
        "};" +
        "/** @param {number} x */" +
        "function f(x) {}" +
        "/** @param {?} x */" +
        "function h(x) {" +
        "  var top = null;" +
        "  forEach(x, function(z) { top = z; });" +
        "  if (top) f(top);" +
        "}");
  }

  public void testBug8017789() throws Exception {
    testTypes(
        "/** @param {(map|function())} isResult */" +
        "var f = function(isResult) {" +
        "    while (true)" +
        "        isResult['t'];" +
        "};" +
        "/** @typedef {Object<string, number>} */" +
        "var map;");
  }

  public void testBug12441160() throws Exception {
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

  public void testBug13641083a() throws Exception {
    testTypes(
        "/** @constructor @struct */ function C() {};" +
        "new C().bar;",
        TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testBug13641083b() throws Exception {
    testTypes(
        "/** @type {?} */ var C;" +
        "C.bar + 1;",
        TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
  }

  public void testTypedefBeforeUse() throws Exception {
    testTypes(
        "/** @typedef {Object<string, number>} */" +
        "var map;" +
        "/** @param {(map|function())} isResult */" +
        "var f = function(isResult) {" +
        "    while (true)" +
        "        isResult['t'];" +
        "};");
  }

  public void testScopedConstructors1() throws Exception {
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

  public void testScopedConstructors2() throws Exception {
    testTypes(
        "/** @param {Function} f */" +
        "function foo1(f) {" +
        "  /** @param {Function} g */" +
        "  f.prototype.bar = function(g) {};" +
        "}");
  }

  public void testQualifiedNameInference1() throws Exception {
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

  public void testQualifiedNameInference2() throws Exception {
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

  public void testQualifiedNameInference3() throws Exception {
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

  public void testQualifiedNameInference4() throws Exception {
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

  public void testQualifiedNameInference5() throws Exception {
    testTypes(
        "var ns = {}; " +
        "(function() { " +
        "    /** @param {number} x */ ns.foo = function(x) {}; })();" +
        "(function() { ns.foo(true); })();",
        "actual parameter 1 of ns.foo does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testQualifiedNameInference6() throws Exception {
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

  public void testQualifiedNameInference7() throws Exception {
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

  public void testQualifiedNameInference8() throws Exception {
    testClosureTypesMultipleWarnings(
        "var ns = {}; " +
        "(function() { " +
        "  /** @constructor \n * @param {number} x */ " +
        "  ns.Foo = function(x) {};" +
        "})();" +
        "/** @param {ns.Foo} x */ function f(x) {}" +
        "f(new ns.Foo(true));",
        Lists.newArrayList(
            "actual parameter 1 of ns.Foo does not match formal parameter\n" +
            "found   : boolean\n" +
            "required: number"));
  }

  public void testQualifiedNameInference9() throws Exception {
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

  public void testQualifiedNameInference10() throws Exception {
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

  public void testQualifiedNameInference11() throws Exception {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "function f() {" +
        "  var x = new Foo();" +
        "  x.onload = function() {" +
        "    x.onload = null;" +
        "  };" +
        "}");
  }

  public void testQualifiedNameInference12() throws Exception {
    // We should be able to tell that the two 'this' properties
    // are different.
    testTypes(
        "/** @param {function(this:Object)} x */ function f(x) {}" +
        "/** @constructor */ function Foo() {" +
        "  /** @type {number} */ this.bar = 3;" +
        "  f(function() { this.bar = true; });" +
        "}");
  }

  public void testQualifiedNameInference13() throws Exception {
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

  public void testSheqRefinedScope() throws Exception {
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
    assertTrue(typeC.isNumber());

    Node nodeB = nodeC.getFirstChild().getFirstChild();
    JSType typeB = nodeB.getJSType();
    assertEquals("B", typeB.toString());
  }

  public void testAssignToUntypedVariable() throws Exception {
    Node n = parseAndTypeCheck("var z; z = 1;");

    Node assign = n.getLastChild().getFirstChild();
    Node node = assign.getFirstChild();
    assertFalse(node.getJSType().isUnknownType());
    assertEquals("number", node.getJSType().toString());
  }

  public void testAssignToUntypedProperty() throws Exception {
    Node n = parseAndTypeCheck(
        "/** @constructor */ function Foo() {}\n" +
        "Foo.prototype.a = 1;" +
        "(new Foo).a;");

    Node node = n.getLastChild().getFirstChild();
    assertFalse(node.getJSType().isUnknownType());
    assertTrue(node.getJSType().isNumber());
  }

  public void testNew1() throws Exception {
    testTypes("new 4", TypeCheck.NOT_A_CONSTRUCTOR);
  }

  public void testNew2() throws Exception {
    testTypes("var Math = {}; new Math()", TypeCheck.NOT_A_CONSTRUCTOR);
  }

  public void testNew3() throws Exception {
    testTypes("new Date()");
  }

  public void testNew4() throws Exception {
    testTypes("/** @constructor */function A(){}; new A();");
  }

  public void testNew5() throws Exception {
    testTypes("function A(){}; new A();", TypeCheck.NOT_A_CONSTRUCTOR);
  }

  public void testNew6() throws Exception {
    TypeCheckResult p =
      parseAndTypeCheckWithScope("/** @constructor */function A(){};" +
      "var a = new A();");

    JSType aType = p.scope.getVar("a").getType();
    assertTrue(aType instanceof ObjectType);
    ObjectType aObjectType = (ObjectType) aType;
    assertEquals("A", aObjectType.getConstructor().getReferenceName());
  }

  public void testNew7() throws Exception {
    testTypes("/** @param {Function} opt_constructor */" +
        "function foo(opt_constructor) {" +
        "if (opt_constructor) { new opt_constructor; }" +
        "}");
  }

  public void testNew8() throws Exception {
    testTypes("/** @param {Function} opt_constructor */" +
        "function foo(opt_constructor) {" +
        "new opt_constructor;" +
        "}");
  }

  public void testNew9() throws Exception {
    testTypes("/** @param {Function} opt_constructor */" +
        "function foo(opt_constructor) {" +
        "new (opt_constructor || Array);" +
        "}");
  }

  public void testNew10() throws Exception {
    testTypes("var goog = {};" +
        "/** @param {Function} opt_constructor */" +
        "goog.Foo = function (opt_constructor) {" +
        "new (opt_constructor || Array);" +
        "}");
  }

  public void testNew11() throws Exception {
    testTypes("/** @param {Function} c1 */" +
        "function f(c1) {" +
        "  var c2 = function(){};" +
        "  c1.prototype = new c2;" +
        "}", TypeCheck.NOT_A_CONSTRUCTOR);
  }

  public void testNew12() throws Exception {
    TypeCheckResult p = parseAndTypeCheckWithScope("var a = new Array();");
    TypedVar a = p.scope.getVar("a");

    assertTypeEquals(ARRAY_TYPE, a.getType());
  }

  public void testNew13() throws Exception {
    TypeCheckResult p = parseAndTypeCheckWithScope(
        "/** @constructor */function FooBar(){};" +
        "var a = new FooBar();");
    TypedVar a = p.scope.getVar("a");

    assertTrue(a.getType() instanceof ObjectType);
    assertEquals("FooBar", a.getType().toString());
  }

  public void testNew14() throws Exception {
    TypeCheckResult p = parseAndTypeCheckWithScope(
        "/** @constructor */var FooBar = function(){};" +
        "var a = new FooBar();");
    TypedVar a = p.scope.getVar("a");

    assertTrue(a.getType() instanceof ObjectType);
    assertEquals("FooBar", a.getType().toString());
  }

  public void testNew15() throws Exception {
    TypeCheckResult p = parseAndTypeCheckWithScope(
        "var goog = {};" +
        "/** @constructor */goog.A = function(){};" +
        "var a = new goog.A();");
    TypedVar a = p.scope.getVar("a");

    assertTrue(a.getType() instanceof ObjectType);
    assertEquals("goog.A", a.getType().toString());
  }

  public void testNew16() throws Exception {
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

  public void testNew17() throws Exception {
    testTypes("var goog = {}; goog.x = 3; new goog.x",
              "cannot instantiate non-constructor");
  }

  public void testNew18() throws Exception {
    testTypes("var goog = {};" +
              "/** @constructor */ goog.F = function() {};" +
              "/** @constructor */ goog.G = goog.F;");
  }

  public void testName1() throws Exception {
    assertTypeEquals(VOID_TYPE, testNameNode("undefined"));
  }

  public void testName2() throws Exception {
    assertTypeEquals(OBJECT_FUNCTION_TYPE, testNameNode("Object"));
  }

  public void testName3() throws Exception {
    assertTypeEquals(ARRAY_FUNCTION_TYPE, testNameNode("Array"));
  }

  public void testName4() throws Exception {
    assertTypeEquals(DATE_FUNCTION_TYPE, testNameNode("Date"));
  }

  public void testName5() throws Exception {
    assertTypeEquals(REGEXP_FUNCTION_TYPE, testNameNode("RegExp"));
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

    Node externAndJsRoot = new Node(Token.BLOCK, externs, parent);
    externAndJsRoot.setIsSyntheticBlock(true);

    makeTypeCheck().processForTesting(null, parent);
    return node.getJSType();
  }

  public void testBitOperation1() throws Exception {
    testTypes("/**@return {void}*/function foo(){ ~foo(); }",
        "operator ~ cannot be applied to undefined");
  }

  public void testBitOperation2() throws Exception {
    testTypes("/**@return {void}*/function foo(){var a = foo()<<3;}",
        "operator << cannot be applied to undefined");
  }

  public void testBitOperation3() throws Exception {
    testTypes("/**@return {void}*/function foo(){var a = 3<<foo();}",
        "operator << cannot be applied to undefined");
  }

  public void testBitOperation4() throws Exception {
    testTypes("/**@return {void}*/function foo(){var a = foo()>>>3;}",
        "operator >>> cannot be applied to undefined");
  }

  public void testBitOperation5() throws Exception {
    testTypes("/**@return {void}*/function foo(){var a = 3>>>foo();}",
        "operator >>> cannot be applied to undefined");
  }

  public void testBitOperation6() throws Exception {
    testTypes("/**@return {!Object}*/function foo(){var a = foo()&3;}",
        "bad left operand to bitwise operator\n" +
        "found   : Object\n" +
        "required: (boolean|null|number|string|undefined)");
  }

  public void testBitOperation7() throws Exception {
    testTypes("var x = null; x |= undefined; x &= 3; x ^= '3'; x |= true;");
  }

  public void testBitOperation8() throws Exception {
    testTypes("var x = void 0; x |= new Number(3);");
  }

  public void testBitOperation9() throws Exception {
    testTypes("var x = void 0; x |= {};",
        "bad right operand to bitwise operator\n" +
        "found   : {}\n" +
        "required: (boolean|null|number|string|undefined)");
  }

  public void testCall1() throws Exception {
    testTypes("3();", "number expressions are not callable");
  }

  public void testCall2() throws Exception {
    testTypes("/** @param {!Number} foo*/function bar(foo){ bar('abc'); }",
        "actual parameter 1 of bar does not match formal parameter\n" +
        "found   : string\n" +
        "required: Number");
  }

  public void testCall3() throws Exception {
    // We are checking that an unresolved named type can successfully
    // meet with a functional type to produce a callable type.
    testTypes("/** @type {Function|undefined} */var opt_f;" +
        "/** @type {some.unknown.type} */var f1;" +
        "var f2 = opt_f || f1;" +
        "f2();",
        "Bad type annotation. Unknown type some.unknown.type");
  }

  public void testCall4() throws Exception {
    testTypes("/**@param {!RegExp} a*/var foo = function bar(a){ bar('abc'); }",
        "actual parameter 1 of bar does not match formal parameter\n" +
        "found   : string\n" +
        "required: RegExp");
  }

  public void testCall5() throws Exception {
    testTypes("/**@param {!RegExp} a*/var foo = function bar(a){ foo('abc'); }",
        "actual parameter 1 of foo does not match formal parameter\n" +
        "found   : string\n" +
        "required: RegExp");
  }

  public void testCall6() throws Exception {
    testTypes("/** @param {!Number} foo*/function bar(foo){}" +
        "bar('abc');",
        "actual parameter 1 of bar does not match formal parameter\n" +
        "found   : string\n" +
        "required: Number");
  }

  public void testCall7() throws Exception {
    testTypes("/** @param {!RegExp} a*/var foo = function bar(a){};" +
        "foo('abc');",
        "actual parameter 1 of foo does not match formal parameter\n" +
        "found   : string\n" +
        "required: RegExp");
  }

  public void testCall8() throws Exception {
    testTypes("/** @type {Function|number} */var f;f();",
        "(Function|number) expressions are " +
        "not callable");
  }

  public void testCall9() throws Exception {
    testTypes(
        "var goog = {};" +
        "/** @constructor */ goog.Foo = function() {};" +
        "/** @param {!goog.Foo} a */ var bar = function(a){};" +
        "bar('abc');",
        "actual parameter 1 of bar does not match formal parameter\n" +
        "found   : string\n" +
        "required: goog.Foo");
  }

  public void testCall10() throws Exception {
    testTypes("/** @type {Function} */var f;f();");
  }

  public void testCall11() throws Exception {
    testTypes("var f = new Function(); f();");
  }

  public void testFunctionCall1() throws Exception {
    testTypes(
        "/** @param {number} x */ var foo = function(x) {};" +
        "foo.call(null, 3);");
  }

  public void testFunctionCall2() throws Exception {
    testTypes(
        "/** @param {number} x */ var foo = function(x) {};" +
        "foo.call(null, 'bar');",
        "actual parameter 2 of foo.call does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testFunctionCall3() throws Exception {
    testTypes(
        "/** @param {number} x \n * @constructor */ " +
        "var Foo = function(x) { this.bar.call(null, x); };" +
        "/** @type {function(number)} */ Foo.prototype.bar;");
  }

  public void testFunctionCall4() throws Exception {
    testTypes(
        "/** @param {string} x \n * @constructor */ " +
        "var Foo = function(x) { this.bar.call(null, x); };" +
        "/** @type {function(number)} */ Foo.prototype.bar;",
        "actual parameter 2 of this.bar.call " +
        "does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testFunctionCall5() throws Exception {
    testTypes(
        "/** @param {Function} handler \n * @constructor */ " +
        "var Foo = function(handler) { handler.call(this, x); };");
  }

  public void testFunctionCall6() throws Exception {
    testTypes(
        "/** @param {Function} handler \n * @constructor */ " +
        "var Foo = function(handler) { handler.apply(this, x); };");
  }

  public void testFunctionCall7() throws Exception {
    testTypes(
        "/** @param {Function} handler \n * @param {Object} opt_context */ " +
        "var Foo = function(handler, opt_context) { " +
        "  handler.call(opt_context, x);" +
        "};");
  }

  public void testFunctionCall8() throws Exception {
    testTypes(
        "/** @param {Function} handler \n * @param {Object} opt_context */ " +
        "var Foo = function(handler, opt_context) { " +
        "  handler.apply(opt_context, x);" +
        "};");
  }

  public void testFunctionCall9() throws Exception {
    testTypes(
        "/** @constructor\n * @template T\n **/ function Foo() {}\n" +
        "/** @param {T} x */ Foo.prototype.bar = function(x) {}\n" +
        "var foo = /** @type {Foo<string>} */ (new Foo());\n" +
        "foo.bar(3);",
        "actual parameter 1 of Foo.prototype.bar does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testFunctionBind1() throws Exception {
    testTypes(
        "/** @type {function(string, number): boolean} */" +
        "function f(x, y) { return true; }" +
        "f.bind(null, 3);",
        "actual parameter 2 of f.bind does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testFunctionBind2() throws Exception {
    testTypes(
        "/** @type {function(number): boolean} */" +
        "function f(x) { return true; }" +
        "f(f.bind(null, 3)());",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testFunctionBind3() throws Exception {
    testTypes(
        "/** @type {function(number, string): boolean} */" +
        "function f(x, y) { return true; }" +
        "f.bind(null, 3)(true);",
        "actual parameter 1 of function does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: string");
  }

  public void testFunctionBind4() throws Exception {
    testTypes(
        "/** @param {...number} x */" +
        "function f(x) {}" +
        "f.bind(null, 3, 3, 3)(true);",
        "actual parameter 1 of function does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: (number|undefined)");
  }

  public void testFunctionBind5() throws Exception {
    testTypes(
        "/** @param {...number} x */" +
        "function f(x) {}" +
        "f.bind(null, true)(3, 3, 3);",
        "actual parameter 2 of f.bind does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: (number|undefined)");
  }

  public void testFunctionBind6() throws Exception {
    testTypes(Joiner.on('\n').join(
        "/** @constructor */",
        "function MyType() {",
        "  /** @type {number} */",
        "  this.x = 0;",
        "  var f = function() {",
        "    this.x = 'str';",
        "  }.bind(this);",
        "}"), Joiner.on('\n').join(
        "assignment to property x of MyType",
        "found   : string",
        "required: number"));
  }

  public void testFunctionBind7() throws Exception {
    testTypes(Joiner.on('\n').join(
        "/** @constructor */",
        "function MyType() {",
        "  /** @type {number} */",
        "  this.x = 0;",
        "}",
        "var m = new MyType;",
        "(function f() {this.x = 'str';}).bind(m);"),
        Joiner.on('\n').join(
        "assignment to property x of MyType",
        "found   : string",
        "required: number"));
  }

  public void testFunctionBind8() throws Exception {
    testTypes(Joiner.on('\n').join(
        "/** @constructor */",
        "function MyType() {}",
        "",
        "/** @constructor */",
        "function AnotherType() {}",
        "AnotherType.prototype.foo = function() {};",
        "",
        "/** @type {?} */",
        "var m = new MyType;",
        "(function f() {this.foo();}).bind(m);"),
        (DiagnosticType) null);
  }

  public void testFunctionBind9() throws Exception {
    testTypes(Joiner.on('\n').join(
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

  public void testGoogBind1() throws Exception {
    testClosureTypes(
        "var goog = {}; goog.bind = function(var_args) {};" +
        "/** @type {function(number): boolean} */" +
        "function f(x, y) { return true; }" +
        "f(goog.bind(f, null, 'x')());",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testGoogBind2() throws Exception {
    // TODO(nicksantos): We do not currently type-check the arguments
    // of the goog.bind.
    testClosureTypes(
        "var goog = {}; goog.bind = function(var_args) {};" +
        "/** @type {function(boolean): boolean} */" +
        "function f(x, y) { return true; }" +
        "f(goog.bind(f, null, 'x')());",
        null);
  }

  public void testCast2() throws Exception {
    // can upcast to a base type.
    testTypes("/** @constructor */function base() {}\n" +
        "/** @constructor\n @extends {base} */function derived() {}\n" +
        "/** @type {base} */ var baz = new derived();\n");
  }

  public void testCast3() throws Exception {
    // cannot downcast
    testTypes("/** @constructor */function base() {}\n" +
        "/** @constructor @extends {base} */function derived() {}\n" +
        "/** @type {!derived} */ var baz = new base();\n",
        "initializing variable\n" +
        "found   : base\n" +
        "required: derived");
  }

  public void testCast3a() throws Exception {
    // cannot downcast
    testTypes("/** @constructor */function Base() {}\n" +
        "/** @constructor @extends {Base} */function Derived() {}\n" +
        "var baseInstance = new Base();" +
        "/** @type {!Derived} */ var baz = baseInstance;\n",
        "initializing variable\n" +
        "found   : Base\n" +
        "required: Derived");
  }

  public void testCast4() throws Exception {
    // downcast must be explicit
    testTypes("/** @constructor */function base() {}\n" +
        "/** @constructor\n * @extends {base} */function derived() {}\n" +
        "/** @type {!derived} */ var baz = " +
        "/** @type {!derived} */(new base());\n");
  }

  public void testCast4Types() throws Exception {
    // downcast must be explicit
    Node root = parseAndTypeCheck(
        "/** @constructor */function base() {}\n" +
        "/** @constructor\n * @extends {base} */function derived() {}\n" +
        "/** @type {!derived} */ var baz = " +
        "/** @type {!derived} */(new base());\n");
    Node castedExprNode = root.getLastChild().getFirstChild().getFirstChild().getFirstChild();
    assertEquals("derived", castedExprNode.getJSType().toString());
    assertEquals("base", castedExprNode.getJSTypeBeforeCast().toString());
  }

  public void testCast5() throws Exception {
    // cannot explicitly cast to an unrelated type
    testTypes("/** @constructor */function foo() {}\n" +
        "/** @constructor */function bar() {}\n" +
        "var baz = /** @type {!foo} */(new bar);\n",
        "invalid cast - must be a subtype or supertype\n" +
        "from: bar\n" +
        "to  : foo");
  }

  public void testCast5a() throws Exception {
    // cannot explicitly cast to an unrelated type
    testTypes("/** @constructor */function foo() {}\n" +
        "/** @constructor */function bar() {}\n" +
        "var barInstance = new bar;\n" +
        "var baz = /** @type {!foo} */(barInstance);\n",
        "invalid cast - must be a subtype or supertype\n" +
        "from: bar\n" +
        "to  : foo");
  }

  public void testCast6() throws Exception {
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

  public void testCast7() throws Exception {
    testTypes("var x = /** @type {foo} */ (new Object());",
        "Bad type annotation. Unknown type foo");
  }

  public void testCast8() throws Exception {
    testTypes("function f() { return /** @type {foo} */ (new Object()); }",
        "Bad type annotation. Unknown type foo");
  }

  public void testCast9() throws Exception {
    testTypes("var foo = {};" +
        "function f() { return /** @type {foo} */ (new Object()); }",
        "Bad type annotation. Unknown type foo");
  }

  public void testCast10() throws Exception {
    testTypes("var foo = function() {};" +
        "function f() { return /** @type {foo} */ (new Object()); }",
        "Bad type annotation. Unknown type foo");
  }

  public void testCast11() throws Exception {
    testTypes("var goog = {}; goog.foo = {};" +
        "function f() { return /** @type {goog.foo} */ (new Object()); }",
        "Bad type annotation. Unknown type goog.foo");
  }

  public void testCast12() throws Exception {
    testTypes("var goog = {}; goog.foo = function() {};" +
        "function f() { return /** @type {goog.foo} */ (new Object()); }",
        "Bad type annotation. Unknown type goog.foo");
  }

  public void testCast13() throws Exception {
    // Test to make sure that the forward-declaration still allows for
    // a warning.
    testClosureTypes("var goog = {}; " +
        "goog.addDependency('zzz.js', ['goog.foo'], []);" +
        "goog.foo = function() {};" +
        "function f() { return /** @type {goog.foo} */ (new Object()); }",
        "Bad type annotation. Unknown type goog.foo");
  }

  public void testCast14() throws Exception {
    // Test to make sure that the forward-declaration still prevents
    // some warnings.
    testClosureTypes("var goog = {}; " +
        "goog.addDependency('zzz.js', ['goog.bar'], []);" +
        "function f() { return /** @type {goog.bar} */ (new Object()); }",
        null);
  }

  public void testCast15() throws Exception {
    // This fixes a bug where a type cast on an object literal
    // would cause a run-time cast exception if the node was visited
    // more than once.
    //
    // Some code assumes that an object literal must have a object type,
    // while because of the cast, it could have any type (including
    // a union).
    testTypes(
        "for (var i = 0; i < 10; i++) {" +
          "var x = /** @type {Object|number} */ ({foo: 3});" +
          "/** @param {number} x */ function f(x) {}" +
          "f(x.foo);" +
          "f([].foo);" +
        "}",
        "Property foo never defined on Array");
  }

  public void testCast16() throws Exception {
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

  public void testCast17a() throws Exception {
    // Mostly verifying that rhino actually understands these JsDocs.
    testTypes("/** @constructor */ function Foo() {} \n" +
        "/** @type {Foo} */ var x = /** @type {Foo} */ (y)");

    testTypes("/** @constructor */ function Foo() {} \n" +
        "/** @type {Foo} */ var x = /** @type {Foo} */ (y)");
  }

  public void testCast17b() throws Exception {
    // Mostly verifying that rhino actually understands these JsDocs.
    testTypes("/** @constructor */ function Foo() {} \n" +
        "/** @type {Foo} */ var x = /** @type {Foo} */ ({})");
  }

  public void testCast19() throws Exception {
    testTypes(
        "var x = 'string';\n" +
        "/** @type {number} */\n" +
        "var y = /** @type {number} */(x);",
        "invalid cast - must be a subtype or supertype\n" +
        "from: string\n" +
        "to  : number");
  }

  public void testCast20() throws Exception {
    testTypes(
        "/** @enum {boolean|null} */\n" +
        "var X = {" +
        "  AA: true," +
        "  BB: false," +
        "  CC: null" +
        "};\n" +
        "var y = /** @type {X} */(true);");
  }

  public void testCast21() throws Exception {
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

  public void testCast22() throws Exception {
    testTypes(
        "var x = null;\n" +
        "var y = /** @type {number} */(x);",
        "invalid cast - must be a subtype or supertype\n" +
        "from: null\n" +
        "to  : number");
  }

  public void testCast23() throws Exception {
    testTypes(
        "var x = null;\n" +
        "var y = /** @type {Number} */(x);");
  }

  public void testCast24() throws Exception {
    testTypes(
        "var x = undefined;\n" +
        "var y = /** @type {number} */(x);",
        "invalid cast - must be a subtype or supertype\n" +
        "from: undefined\n" +
        "to  : number");
  }

  public void testCast25() throws Exception {
    testTypes(
        "var x = undefined;\n" +
        "var y = /** @type {number|undefined} */(x);");
  }

  public void testCast26() throws Exception {
    testTypes(
        "function fn(dir) {\n" +
        "  var node = dir ? 1 : 2;\n" +
        "  fn(/** @type {number} */ (node));\n" +
        "}");
  }

  public void testCast27() throws Exception {
    // C doesn't implement I but a subtype might.
    testTypes(
        "/** @interface */ function I() {}\n" +
        "/** @constructor */ function C() {}\n" +
        "var x = new C();\n" +
        "var y = /** @type {I} */(x);");
  }

  public void testCast27a() throws Exception {
    // C doesn't implement I but a subtype might.
    testTypes(
        "/** @interface */ function I() {}\n" +
        "/** @constructor */ function C() {}\n" +
        "/** @type {C} */ var x ;\n" +
        "var y = /** @type {I} */(x);");
  }

  public void testCast28() throws Exception {
    // C doesn't implement I but a subtype might.
    testTypes(
        "/** @interface */ function I() {}\n" +
        "/** @constructor */ function C() {}\n" +
        "/** @type {!I} */ var x;\n" +
        "var y = /** @type {C} */(x);");
  }

  public void testCast28a() throws Exception {
    // C doesn't implement I but a subtype might.
    testTypes(
        "/** @interface */ function I() {}\n" +
        "/** @constructor */ function C() {}\n" +
        "/** @type {I} */ var x;\n" +
        "var y = /** @type {C} */(x);");
  }

  public void testCast29a() throws Exception {
    // C doesn't implement the record type but a subtype might.
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "var x = new C();\n" +
        "var y = /** @type {{remoteJids: Array, sessionId: string}} */(x);");
  }

  public void testCast29b() throws Exception {
    // C doesn't implement the record type but a subtype might.
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {C} */ var x;\n" +
        "var y = /** @type {{prop1: Array, prop2: string}} */(x);");
  }

  public void testCast29c() throws Exception {
    // C doesn't implement the record type but a subtype might.
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {{remoteJids: Array, sessionId: string}} */ var x ;\n" +
        "var y = /** @type {C} */(x);");
  }

  public void testCast30() throws Exception {
    // Should be able to cast to a looser return type
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {function():string} */ var x ;\n" +
        "var y = /** @type {function():?} */(x);");
  }

  public void testCast31() throws Exception {
    // Should be able to cast to a tighter parameter type
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {function(*)} */ var x ;\n" +
        "var y = /** @type {function(string)} */(x);");
  }

  public void testCast32() throws Exception {
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {Object} */ var x ;\n" +
        "var y = /** @type {null|{length:number}} */(x);");
  }

  public void testCast33() throws Exception {
    // null and void should be assignable to any type that accepts one or the
    // other or both.
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {null|undefined} */ var x ;\n" +
        "var y = /** @type {string?|undefined} */(x);");
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {null|undefined} */ var x ;\n" +
        "var y = /** @type {string|undefined} */(x);");
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {null|undefined} */ var x ;\n" +
        "var y = /** @type {string?} */(x);");
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {null|undefined} */ var x ;\n" +
        "var y = /** @type {null} */(x);");
  }

  public void testCast34a() throws Exception {
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {Object} */ var x ;\n" +
        "var y = /** @type {Function} */(x);");
  }

  public void testCast34b() throws Exception {
    testTypes(
        "/** @constructor */ function C() {}\n" +
        "/** @type {Function} */ var x ;\n" +
        "var y = /** @type {Object} */(x);");
  }

  public void testUnnecessaryCastToSuperType() throws Exception {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.UNNECESSARY_CASTS, CheckLevel.WARNING);
    testTypes(
        "/** @constructor */\n" +
        "function Base() {}\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Base}\n" +
        " */\n" +
        "function Derived() {}\n" +
        "var d = new Derived();\n" +
        "var b = /** @type {!Base} */ (d);",
        "unnecessary cast\n" +
        "from: Derived\n" +
        "to  : Base"
    );
  }

  public void testUnnecessaryCastToSameType() throws Exception {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.UNNECESSARY_CASTS, CheckLevel.WARNING);
    testTypes(
        "/** @constructor */\n" +
        "function Base() {}\n" +
        "var b = new Base();\n" +
        "var c = /** @type {!Base} */ (b);",
        "unnecessary cast\n" +
        "from: Base\n" +
        "to  : Base"
    );
  }

  /**
   * Checks that casts to unknown ({?}) are not marked as unnecessary.
   */
  public void testUnnecessaryCastToUnknown() throws Exception {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.UNNECESSARY_CASTS, CheckLevel.WARNING);
    testTypes(
        "/** @constructor */\n" +
        "function Base() {}\n" +
        "var b = new Base();\n" +
        "var c = /** @type {?} */ (b);");
  }

  /**
   * Checks that casts from unknown ({?}) are not marked as unnecessary.
   */
  public void testUnnecessaryCastFromUnknown() throws Exception {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.UNNECESSARY_CASTS, CheckLevel.WARNING);
    testTypes(
        "/** @constructor */\n" +
        "function Base() {}\n" +
        "/** @type {?} */ var x;\n" +
        "var y = /** @type {Base} */ (x);");
  }

  public void testUnnecessaryCastToAndFromUnknown() throws Exception {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.UNNECESSARY_CASTS, CheckLevel.WARNING);
    testTypes(
        "/** @constructor */ function A() {}\n" +
        "/** @constructor */ function B() {}\n" +
        "/** @type {!Array<!A>} */ var x = " +
        "/** @type {!Array<?>} */( /** @type {!Array<!B>} */([]) );");
  }

  /**
   * Checks that a cast from {?Base} to {!Base} is not considered unnecessary.
   */
  public void testUnnecessaryCastToNonNullType() throws Exception {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.UNNECESSARY_CASTS, CheckLevel.WARNING);
    testTypes(
        "/** @constructor */\n" +
        "function Base() {}\n" +
        "var c = /** @type {!Base} */ (random ? new Base() : null);"
    );
  }

  public void testUnnecessaryCastToStar() throws Exception {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.UNNECESSARY_CASTS, CheckLevel.WARNING);
    testTypes(
        "/** @constructor */\n" +
        "function Base() {}\n" +
        "var c = /** @type {*} */ (new Base());",
        "unnecessary cast\n" +
        "from: Base\n" +
        "to  : *"
    );
  }

  public void testNoUnnecessaryCastNoResolvedType() throws Exception {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.UNNECESSARY_CASTS, CheckLevel.WARNING);
    testClosureTypes(
        "var goog = {};\n" +
        "goog.addDependency = function(a,b,c){};\n" +
        // A is NoResolvedType.
        "goog.addDependency('a.js', ['A'], []);\n" +

        // B is a normal type.
        "/** @constructor @struct */ function B() {}\n" +

        "/**\n" +
        " * @constructor\n" +
        " * @template T\n" +
        " */\n" +
        "function C() { this.t; }\n" +

        "/**\n" +
        " * @param {!C<T>} c\n" +
        " * @return {T}\n" +
        " * @template T\n" +
        " */\n" +
        "function getT(c) { return c.t; }\n" +

        "/** @type {!C<!A>} */\n" +
        "var c = new C();\n" +

        // Casting from NoResolvedType.
        "var b = /** @type {!B} */ (getT(c));\n" +

        // Casting to NoResolvedType.
        "var a = /** @type {!A} */ (new B());\n",
        null);  // No warning expected.
  }

  public void testNestedCasts() throws Exception {
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

  public void testNativeCast1() throws Exception {
    testTypes(
        "/** @param {number} x */ function f(x) {}" +
        "f(String(true));",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testNativeCast2() throws Exception {
    testTypes(
        "/** @param {string} x */ function f(x) {}" +
        "f(Number(true));",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testNativeCast3() throws Exception {
    testTypes(
        "/** @param {number} x */ function f(x) {}" +
        "f(Boolean(''));",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : boolean\n" +
        "required: number");
  }

  public void testNativeCast4() throws Exception {
    testTypes(
        "/** @param {number} x */ function f(x) {}" +
        "f(Error(''));",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : Error\n" +
        "required: number");
  }

  public void testBadConstructorCall() throws Exception {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo();",
        "Constructor function (new:Foo): undefined should be called " +
        "with the \"new\" keyword");
  }

  public void testTypeof() throws Exception {
    testTypes("/**@return {void}*/function foo(){ var a = typeof foo(); }");
  }

  public void testTypeof2() throws Exception {
    testTypes("function f(){ if (typeof 123 == 'numbr') return 321; }",
              "unknown type: numbr");
  }

  public void testTypeof3() throws Exception {
    testTypes("function f() {" +
              "return (typeof 123 == 'number' ||" +
              "typeof 123 == 'string' ||" +
              "typeof 123 == 'boolean' ||" +
              "typeof 123 == 'undefined' ||" +
              "typeof 123 == 'function' ||" +
              "typeof 123 == 'object' ||" +
              "typeof 123 == 'unknown'); }");
  }

  public void testConstDecl1() throws Exception {
    testTypes(
        "/** @param {?number} x \n @return {boolean} */" +
        "function f(x) { " +
        "  if (x) { /** @const */ var y = x; return y } return true; "  +
        "}",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: boolean");
  }

  public void testConstDecl2() throws Exception {
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

  public void testConstructorType1() throws Exception {
    testTypes("/**@constructor*/function Foo(){}" +
        "/**@type{!Foo}*/var f = new Date();",
        "initializing variable\n" +
        "found   : Date\n" +
        "required: Foo");
  }

  public void testConstructorType2() throws Exception {
    testTypes("/**@constructor*/function Foo(){\n" +
        "/**@type{Number}*/this.bar = new Number(5);\n" +
        "}\n" +
        "/**@type{Foo}*/var f = new Foo();\n" +
        "/**@type{Number}*/var n = f.bar;");
  }

  public void testConstructorType3() throws Exception {
    // Reverse the declaration order so that we know that Foo is getting set
    // even on an out-of-order declaration sequence.
    testTypes("/**@type{Foo}*/var f = new Foo();\n" +
        "/**@type{Number}*/var n = f.bar;" +
        "/**@constructor*/function Foo(){\n" +
        "/**@type{Number}*/this.bar = new Number(5);\n" +
        "}\n");
  }

  public void testConstructorType4() throws Exception {
    testTypes("/**@constructor*/function Foo(){\n" +
        "/**@type{!Number}*/this.bar = new Number(5);\n" +
        "}\n" +
        "/**@type{!Foo}*/var f = new Foo();\n" +
        "/**@type{!String}*/var n = f.bar;",
        "initializing variable\n" +
        "found   : Number\n" +
        "required: String");
  }

  public void testConstructorType5() throws Exception {
    testTypes("/**@constructor*/function Foo(){}\n" +
        "if (Foo){}\n");
  }

  public void testConstructorType6() throws Exception {
    testTypes("/** @constructor */\n" +
        "function bar() {}\n" +
        "function _foo() {\n" +
        " /** @param {bar} x */\n" +
        "  function f(x) {}\n" +
        "}");
  }

  public void testConstructorType7() throws Exception {
    TypeCheckResult p =
        parseAndTypeCheckWithScope("/** @constructor */function A(){};");

    JSType type = p.scope.getVar("A").getType();
    assertTrue(type instanceof FunctionType);
    FunctionType fType = (FunctionType) type;
    assertEquals("A", fType.getReferenceName());
  }

  public void testConstructorType8() throws Exception {
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

  public void testConstructorType9() throws Exception {
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

  public void testConstructorType10() throws Exception {
    testTypes("/** @constructor */" +
              "function NonStr() {}" +
              "/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " * @extends{NonStr}\n" +
              " */" +
              "function NonStrKid() {}",
              "NonStrKid cannot extend this type; " +
              "structs can only extend structs");
  }

  public void testConstructorType11() throws Exception {
    testTypes("/** @constructor */" +
              "function NonDict() {}" +
              "/**\n" +
              " * @constructor\n" +
              " * @dict\n" +
              " * @extends{NonDict}\n" +
              " */" +
              "function NonDictKid() {}",
              "NonDictKid cannot extend this type; " +
              "dicts can only extend dicts");
  }

  public void testConstructorType12() throws Exception {
    testTypes("/**\n" +
              " * @constructor\n" +
              " * @struct\n" +
              " */\n" +
              "function Bar() {}\n" +
              "Bar.prototype = {};\n",
              "Bar cannot extend this type; " +
              "structs can only extend structs");
  }

  public void testBadStruct() throws Exception {
    testTypes("/** @struct */function Struct1() {}",
              "@struct used without @constructor for Struct1");
  }

  public void testBadDict() throws Exception {
    testTypes("/** @dict */function Dict1() {}",
              "@dict used without @constructor for Dict1");
  }

  public void testAnonymousPrototype1() throws Exception {
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

  public void testAnonymousPrototype2() throws Exception {
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

  public void testAnonymousType1() throws Exception {
    testTypes("function f() { return {}; }" +
        "/** @constructor */\n" +
        "f().bar = function() {};");
  }

  public void testAnonymousType2() throws Exception {
    testTypes("function f() { return {}; }" +
        "/** @interface */\n" +
        "f().bar = function() {};");
  }

  public void testAnonymousType3() throws Exception {
    testTypes("function f() { return {}; }" +
        "/** @enum */\n" +
        "f().bar = {FOO: 1};");
  }

  public void testBang1() throws Exception {
    testTypes("/** @param {Object} x\n@return {!Object} */\n" +
        "function f(x) { return x; }",
        "inconsistent return type\n" +
        "found   : (Object|null)\n" +
        "required: Object");
  }

  public void testBang2() throws Exception {
    testTypes("/** @param {Object} x\n@return {!Object} */\n" +
        "function f(x) { return x ? x : new Object(); }");
  }

  public void testBang3() throws Exception {
    testTypes("/** @param {Object} x\n@return {!Object} */\n" +
        "function f(x) { return /** @type {!Object} */ (x); }");
  }

  public void testBang4() throws Exception {
    testTypes("/**@param {Object} x\n@param {Object} y\n@return {boolean}*/\n" +
        "function f(x, y) {\n" +
        "if (typeof x != 'undefined') { return x == y; }\n" +
        "else { return x != y; }\n}");
  }

  public void testBang5() throws Exception {
    testTypes("/**@param {Object} x\n@param {Object} y\n@return {boolean}*/\n" +
        "function f(x, y) { return !!x && x == y; }");
  }

  public void testBang6() throws Exception {
    testTypes("/** @param {Object?} x\n@return {Object} */\n" +
        "function f(x) { return x; }");
  }

  public void testBang7() throws Exception {
    testTypes("/**@param {(Object,string,null)} x\n" +
        "@return {(Object,string)}*/function f(x) { return x; }");
  }

  public void testDefinePropertyOnNullableObject1() throws Exception {
    testTypes("/** @type {Object} */ var n = {};\n" +
        "/** @type {number} */ n.x = 1;\n" +
        "/** @return {boolean} */function f() { return n.x; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: boolean");
  }

  public void testDefinePropertyOnNullableObject2() throws Exception {
    testTypes("/** @constructor */ var T = function() {};\n" +
        "/** @param {T} t\n@return {boolean} */function f(t) {\n" +
        "t.x = 1; return t.x; }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: boolean");
  }

  public void testUnknownConstructorInstanceType1() throws Exception {
    testTypes("/** @return {Array} */ function g(f) { return new f(); }");
  }

  public void testUnknownConstructorInstanceType2() throws Exception {
    testTypes("function g(f) { return /** @type Array */(new f()); }");
  }

  public void testUnknownConstructorInstanceType3() throws Exception {
    testTypes("function g(f) { var x = new f(); x.a = 1; return x; }");
  }

  public void testUnknownPrototypeChain() throws Exception {
    testTypes("/**\n" +
              "* @param {Object} co\n" +
              " * @return {Object}\n" +
              " */\n" +
              "function inst(co) {\n" +
              " /** @constructor */\n" +
              " var c = function() {};\n" +
              " c.prototype = co.prototype;\n" +
              " return new c;\n" +
              "}");
  }

  public void testNamespacedConstructor() throws Exception {
    Node root = parseAndTypeCheck(
        "var goog = {};" +
        "/** @constructor */ goog.MyClass = function() {};" +
        "/** @return {!goog.MyClass} */ " +
        "function foo() { return new goog.MyClass(); }");

    JSType typeOfFoo = root.getLastChild().getJSType();
    assert(typeOfFoo instanceof FunctionType);

    JSType retType = ((FunctionType) typeOfFoo).getReturnType();
    assert(retType instanceof ObjectType);
    assertEquals("goog.MyClass", ((ObjectType) retType).getReferenceName());
  }

  public void testComplexNamespace() throws Exception {
    String js =
      "var goog = {};" +
      "goog.foo = {};" +
      "goog.foo.bar = 5;";

    TypeCheckResult p = parseAndTypeCheckWithScope(js);

    // goog type in the scope
    JSType googScopeType = p.scope.getVar("goog").getType();
    assertTrue(googScopeType instanceof ObjectType);
    assertTrue("foo property not present on goog type",
        googScopeType.hasProperty("foo"));
    assertFalse("bar property present on goog type",
        googScopeType.hasProperty("bar"));

    // goog type on the VAR node
    Node varNode = p.root.getFirstChild();
    assertEquals(Token.VAR, varNode.getType());
    JSType googNodeType = varNode.getFirstChild().getJSType();
    assertTrue(googNodeType instanceof ObjectType);

    // goog scope type and goog type on VAR node must be the same
    assertSame(googNodeType, googScopeType);

    // goog type on the left of the GETPROP node (under fist ASSIGN)
    Node getpropFoo1 = varNode.getNext().getFirstChild().getFirstChild();
    assertEquals(Token.GETPROP, getpropFoo1.getType());
    assertEquals("goog", getpropFoo1.getFirstChild().getString());
    JSType googGetpropFoo1Type = getpropFoo1.getFirstChild().getJSType();
    assertTrue(googGetpropFoo1Type instanceof ObjectType);

    // still the same type as the one on the variable
    assertSame(googScopeType, googGetpropFoo1Type);

    // the foo property should be defined on goog
    JSType googFooType = ((ObjectType) googScopeType).getPropertyType("foo");
    assertTrue(googFooType instanceof ObjectType);

    // goog type on the left of the GETPROP lower level node
    // (under second ASSIGN)
    Node getpropFoo2 = varNode.getNext().getNext()
        .getFirstChild().getFirstChild().getFirstChild();
    assertEquals(Token.GETPROP, getpropFoo2.getType());
    assertEquals("goog", getpropFoo2.getFirstChild().getString());
    JSType googGetpropFoo2Type = getpropFoo2.getFirstChild().getJSType();
    assertTrue(googGetpropFoo2Type instanceof ObjectType);

    // still the same type as the one on the variable
    assertSame(googScopeType, googGetpropFoo2Type);

    // goog.foo type on the left of the top-level GETPROP node
    // (under second ASSIGN)
    JSType googFooGetprop2Type = getpropFoo2.getJSType();
    assertTrue("goog.foo incorrectly annotated in goog.foo.bar selection",
        googFooGetprop2Type instanceof ObjectType);
    ObjectType googFooGetprop2ObjectType = (ObjectType) googFooGetprop2Type;
    assertFalse("foo property present on goog.foo type",
        googFooGetprop2ObjectType.hasProperty("foo"));
    assertTrue("bar property not present on goog.foo type",
        googFooGetprop2ObjectType.hasProperty("bar"));
    assertTypeEquals("bar property on goog.foo type incorrectly inferred",
        NUMBER_TYPE, googFooGetprop2ObjectType.getPropertyType("bar"));
  }

  public void testAddingMethodsUsingPrototypeIdiomSimpleNamespace()
      throws Exception {
    Node js1Node = parseAndTypeCheck(
        "/** @constructor */function A() {}" +
        "A.prototype.m1 = 5");

    ObjectType instanceType = getInstanceType(js1Node);
    assertEquals(NATIVE_PROPERTIES_COUNT + 1,
        instanceType.getPropertiesCount());
    checkObjectType(instanceType, "m1", NUMBER_TYPE);
  }

  public void testAddingMethodsUsingPrototypeIdiomComplexNamespace1()
      throws Exception {
    TypeCheckResult p = parseAndTypeCheckWithScope(
        "var goog = {};" +
        "goog.A = /** @constructor */function() {};" +
        "/** @type number */goog.A.prototype.m1 = 5");

    testAddingMethodsUsingPrototypeIdiomComplexNamespace(p);
  }

  public void testAddingMethodsUsingPrototypeIdiomComplexNamespace2()
      throws Exception {
    TypeCheckResult p = parseAndTypeCheckWithScope(
        "var goog = {};" +
        "/** @constructor */goog.A = function() {};" +
        "/** @type number */goog.A.prototype.m1 = 5");

    testAddingMethodsUsingPrototypeIdiomComplexNamespace(p);
  }

  private void testAddingMethodsUsingPrototypeIdiomComplexNamespace(
      TypeCheckResult p) {
    ObjectType goog = (ObjectType) p.scope.getVar("goog").getType();
    assertEquals(NATIVE_PROPERTIES_COUNT + 1, goog.getPropertiesCount());
    JSType googA = goog.getPropertyType("A");
    assertNotNull(googA);
    assertTrue(googA instanceof FunctionType);
    FunctionType googAFunction = (FunctionType) googA;
    ObjectType classA = googAFunction.getInstanceType();
    assertEquals(NATIVE_PROPERTIES_COUNT + 1, classA.getPropertiesCount());
    checkObjectType(classA, "m1", NUMBER_TYPE);
  }

  public void testAddingMethodsPrototypeIdiomAndObjectLiteralSimpleNamespace()
      throws Exception {
    Node js1Node = parseAndTypeCheck(
        "/** @constructor */function A() {}" +
        "A.prototype = {m1: 5, m2: true}");

    ObjectType instanceType = getInstanceType(js1Node);
    assertEquals(NATIVE_PROPERTIES_COUNT + 2,
        instanceType.getPropertiesCount());
    checkObjectType(instanceType, "m1", NUMBER_TYPE);
    checkObjectType(instanceType, "m2", BOOLEAN_TYPE);
  }

  public void testDontAddMethodsIfNoConstructor()
      throws Exception {
    Node js1Node = parseAndTypeCheck(
        "function A() {}" +
        "A.prototype = {m1: 5, m2: true}");

    JSType functionAType = js1Node.getFirstChild().getJSType();
    assertEquals("function (): undefined", functionAType.toString());
    assertTypeEquals(UNKNOWN_TYPE,
        U2U_FUNCTION_TYPE.getPropertyType("m1"));
    assertTypeEquals(UNKNOWN_TYPE,
        U2U_FUNCTION_TYPE.getPropertyType("m2"));
  }

  public void testFunctionAssignement() throws Exception {
    testTypes("/**" +
        "* @param {string} ph0" +
        "* @param {string} ph1" +
        "* @return {string}" +
        "*/" +
        "function MSG_CALENDAR_ACCESS_ERROR(ph0, ph1) {return ''}" +
        "/** @type {Function} */" +
        "var MSG_CALENDAR_ADD_ERROR = MSG_CALENDAR_ACCESS_ERROR;");
  }

  public void testAddMethodsPrototypeTwoWays() throws Exception {
    Node js1Node = parseAndTypeCheck(
        "/** @constructor */function A() {}" +
        "A.prototype = {m1: 5, m2: true};" +
        "A.prototype.m3 = 'third property!';");

    ObjectType instanceType = getInstanceType(js1Node);
    assertEquals("A", instanceType.toString());
    assertEquals(NATIVE_PROPERTIES_COUNT + 3,
        instanceType.getPropertiesCount());
    checkObjectType(instanceType, "m1", NUMBER_TYPE);
    checkObjectType(instanceType, "m2", BOOLEAN_TYPE);
    checkObjectType(instanceType, "m3", STRING_TYPE);
  }

  public void testPrototypePropertyTypes() throws Exception {
    Node js1Node = parseAndTypeCheck(
        "/** @constructor */function A() {\n" +
        "  /** @type string */ this.m1;\n" +
        "  /** @type Object? */ this.m2 = {};\n" +
        "  /** @type boolean */ this.m3;\n" +
        "}\n" +
        "/** @type string */ A.prototype.m4;\n" +
        "/** @type number */ A.prototype.m5 = 0;\n" +
        "/** @type boolean */ A.prototype.m6;\n");

    ObjectType instanceType = getInstanceType(js1Node);
    assertEquals(NATIVE_PROPERTIES_COUNT + 6,
        instanceType.getPropertiesCount());
    checkObjectType(instanceType, "m1", STRING_TYPE);
    checkObjectType(instanceType, "m2",
        createUnionType(OBJECT_TYPE, NULL_TYPE));
    checkObjectType(instanceType, "m3", BOOLEAN_TYPE);
    checkObjectType(instanceType, "m4", STRING_TYPE);
    checkObjectType(instanceType, "m5", NUMBER_TYPE);
    checkObjectType(instanceType, "m6", BOOLEAN_TYPE);
  }

  public void testValueTypeBuiltInPrototypePropertyType() throws Exception {
    Node node = parseAndTypeCheck("\"x\".charAt(0)");
    assertTypeEquals(STRING_TYPE, node.getFirstChild().getFirstChild().getJSType());
  }

  public void testDeclareBuiltInConstructor() throws Exception {
    // Built-in prototype properties should be accessible
    // even if the built-in constructor is declared.
    Node node = parseAndTypeCheck(
        "/** @constructor */ var String = function(opt_str) {};\n" +
        "(new String(\"x\")).charAt(0)");
    assertTypeEquals(STRING_TYPE, node.getLastChild().getFirstChild().getJSType());
  }

  public void testExtendBuiltInType1() throws Exception {
    String externs =
        "/** @constructor */ var String = function(opt_str) {};\n" +
        "/**\n" +
        "* @param {number} start\n" +
        "* @param {number} opt_length\n"  +
        "* @return {string}\n" +
        "*/\n" +
        "String.prototype.substr = function(start, opt_length) {};\n";
    Node n1 = parseAndTypeCheck(externs + "(new String(\"x\")).substr(0,1);");
    assertTypeEquals(STRING_TYPE, n1.getLastChild().getFirstChild().getJSType());
  }

  public void testExtendBuiltInType2() throws Exception {
    String externs =
        "/** @constructor */ var String = function(opt_str) {};\n" +
        "/**\n" +
        "* @param {number} start\n" +
        "* @param {number} opt_length\n"  +
        "* @return {string}\n" +
        "*/\n" +
        "String.prototype.substr = function(start, opt_length) {};\n";
    Node n2 = parseAndTypeCheck(externs + "\"x\".substr(0,1);");
    assertTypeEquals(STRING_TYPE, n2.getLastChild().getFirstChild().getJSType());
  }

  public void testExtendFunction1() throws Exception {
    Node n = parseAndTypeCheck("/**@return {number}*/Function.prototype.f = " +
        "function() { return 1; };\n" +
        "(new Function()).f();");
    JSType type = n.getLastChild().getLastChild().getJSType();
    assertTypeEquals(NUMBER_TYPE, type);
  }

  public void testExtendFunction2() throws Exception {
    Node n = parseAndTypeCheck("/**@return {number}*/Function.prototype.f = " +
        "function() { return 1; };\n" +
        "(function() {}).f();");
    JSType type = n.getLastChild().getLastChild().getJSType();
    assertTypeEquals(NUMBER_TYPE, type);
  }

  public void testInheritanceCheck1() throws Exception {
    testTypes(
        "/** @constructor */function Super() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "Sub.prototype.foo = function() {};");
  }

  public void testInheritanceCheck2() throws Exception {
    testTypes(
        "/** @constructor */function Super() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo = function() {};",
        "property foo not defined on any superclass of Sub");
  }

  public void testInheritanceCheck3() throws Exception {
    testTypes(
        "/** @constructor */function Super() {};" +
        "Super.prototype.foo = function() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "Sub.prototype.foo = function() {};",
        "property foo already defined on superclass Super; " +
        "use @override to override it");
  }

  public void testInheritanceCheck4() throws Exception {
    testTypes(
        "/** @constructor */function Super() {};" +
        "Super.prototype.foo = function() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo = function() {};");
  }

  public void testInheritanceCheck5() throws Exception {
    testTypes(
        "/** @constructor */function Root() {};" +
        "Root.prototype.foo = function() {};" +
        "/** @constructor\n @extends {Root} */function Super() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "Sub.prototype.foo = function() {};",
        "property foo already defined on superclass Root; " +
        "use @override to override it");
  }

  public void testInheritanceCheck6() throws Exception {
    testTypes(
        "/** @constructor */function Root() {};" +
        "Root.prototype.foo = function() {};" +
        "/** @constructor\n @extends {Root} */function Super() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo = function() {};");
  }

  public void testInheritanceCheck7() throws Exception {
    testTypes(
        "var goog = {};" +
        "/** @constructor */goog.Super = function() {};" +
        "goog.Super.prototype.foo = 3;" +
        "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};" +
        "goog.Sub.prototype.foo = 5;");
  }

  public void testInheritanceCheck8() throws Exception {
    testTypes(
        "var goog = {};" +
        "/** @constructor */goog.Super = function() {};" +
        "goog.Super.prototype.foo = 3;" +
        "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};" +
        "/** @override */goog.Sub.prototype.foo = 5;");
  }

  public void testInheritanceCheck9_1() throws Exception {
    testTypes(
        "/** @constructor */function Super() {};" +
        "Super.prototype.foo = function() { return 3; };" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override\n @return {number} */Sub.prototype.foo =\n" +
        "function() { return 1; };");
  }

  public void testInheritanceCheck9_2() throws Exception {
    testTypes(
        "/** @constructor */function Super() {};" +
        "/** @return {number} */" +
        "Super.prototype.foo = function() { return 1; };" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo =\n" +
        "function() {};");
  }

  public void testInheritanceCheck9_3() throws Exception {
    testTypes(
        "/** @constructor */function Super() {};" +
        "/** @return {number} */" +
        "Super.prototype.foo = function() { return 1; };" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override\n @return {string} */Sub.prototype.foo =\n" +
        "function() { return \"some string\" };",
        "mismatch of the foo property type and the type of the property it " +
        "overrides from superclass Super\n" +
        "original: function (this:Super): number\n" +
        "override: function (this:Sub): string");
  }

  public void testInheritanceCheck10_1() throws Exception {
    testTypes(
        "/** @constructor */function Root() {};" +
        "Root.prototype.foo = function() { return 3; };" +
        "/** @constructor\n @extends {Root} */function Super() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override\n @return {number} */Sub.prototype.foo =\n" +
        "function() { return 1; };");
  }

  public void testInheritanceCheck10_2() throws Exception {
    testTypes(
        "/** @constructor */function Root() {};" +
        "/** @return {number} */" +
        "Root.prototype.foo = function() { return 1; };" +
        "/** @constructor\n @extends {Root} */function Super() {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo =\n" +
        "function() {};");
  }

  public void testInheritanceCheck10_3() throws Exception {
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
        "original: function (this:Root): number\n" +
        "override: function (this:Sub): string");
  }

  public void testInterfaceInheritanceCheck11() throws Exception {
    testTypes(
        "/** @constructor */function Super() {};" +
        "/** @param {number} bar */Super.prototype.foo = function(bar) {};" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override\n  @param {string} bar */Sub.prototype.foo =\n" +
        "function(bar) {};",
        "mismatch of the foo property type and the type of the property it " +
        "overrides from superclass Super\n" +
        "original: function (this:Super, number): undefined\n" +
        "override: function (this:Sub, string): undefined");
  }

  public void testInheritanceCheck12() throws Exception {
    testTypes(
        "var goog = {};" +
        "/** @constructor */goog.Super = function() {};" +
        "goog.Super.prototype.foo = 3;" +
        "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};" +
        "/** @override */goog.Sub.prototype.foo = \"some string\";");
  }

  public void testInheritanceCheck13() throws Exception {
    testTypes(
        "var goog = {};\n" +
        "/** @constructor\n @extends {goog.Missing} */function Sub() {};" +
        "/** @override */Sub.prototype.foo = function() {};",
        "Bad type annotation. Unknown type goog.Missing");
  }

  public void testInheritanceCheck14() throws Exception {
    testClosureTypes(
        "var goog = {};\n" +
        "/** @constructor\n @extends {goog.Missing} */\n" +
        "goog.Super = function() {};\n" +
        "/** @constructor\n @extends {goog.Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo = function() {};",
        "Bad type annotation. Unknown type goog.Missing");
  }

  public void testInheritanceCheck15() throws Exception {
    testTypes(
        "/** @constructor */function Super() {};" +
        "/** @param {number} bar */Super.prototype.foo;" +
        "/** @constructor\n @extends {Super} */function Sub() {};" +
        "/** @override\n  @param {number} bar */Sub.prototype.foo =\n" +
        "function(bar) {};");
  }

  public void testInheritanceCheck16() throws Exception {
    testTypes(
        "var goog = {};" +
        "/** @constructor */goog.Super = function() {};" +
        "/** @type {number} */ goog.Super.prototype.foo = 3;" +
        "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};" +
        "/** @type {number} */ goog.Sub.prototype.foo = 5;",
        "property foo already defined on superclass goog.Super; " +
        "use @override to override it");
  }

  public void testInheritanceCheck17() throws Exception {
    // Make sure this warning still works, even when there's no
    // @override tag.
    reportMissingOverrides = CheckLevel.OFF;
    testTypes(
        "var goog = {};" +
        "/** @constructor */goog.Super = function() {};" +
        "/** @param {number} x */ goog.Super.prototype.foo = function(x) {};" +
        "/** @constructor\n @extends {goog.Super} */goog.Sub = function() {};" +
        "/** @param {string} x */ goog.Sub.prototype.foo = function(x) {};",
        "mismatch of the foo property type and the type of the property it " +
        "overrides from superclass goog.Super\n" +
        "original: function (this:goog.Super, number): undefined\n" +
        "override: function (this:goog.Sub, string): undefined");
  }

  public void testInterfacePropertyOverride1() throws Exception {
    testTypes(
        "/** @interface */function Super() {};" +
        "/** @desc description */Super.prototype.foo = function() {};" +
        "/** @interface\n @extends {Super} */function Sub() {};" +
        "/** @desc description */Sub.prototype.foo = function() {};");
  }

  public void testInterfacePropertyOverride2() throws Exception {
    testTypes(
        "/** @interface */function Root() {};" +
        "/** @desc description */Root.prototype.foo = function() {};" +
        "/** @interface\n @extends {Root} */function Super() {};" +
        "/** @interface\n @extends {Super} */function Sub() {};" +
        "/** @desc description */Sub.prototype.foo = function() {};");
  }

  public void testInterfaceInheritanceCheck1() throws Exception {
    testTypes(
        "/** @interface */function Super() {};" +
        "/** @desc description */Super.prototype.foo = function() {};" +
        "/** @constructor\n @implements {Super} */function Sub() {};" +
        "Sub.prototype.foo = function() {};",
        "property foo already defined on interface Super; use @override to " +
        "override it");
  }

  public void testInterfaceInheritanceCheck2() throws Exception {
    testTypes(
        "/** @interface */function Super() {};" +
        "/** @desc description */Super.prototype.foo = function() {};" +
        "/** @constructor\n @implements {Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo = function() {};");
  }

  public void testInterfaceInheritanceCheck3() throws Exception {
    testTypes(
        "/** @interface */function Root() {};" +
        "/** @return {number} */Root.prototype.foo = function() {};" +
        "/** @interface\n @extends {Root} */function Super() {};" +
        "/** @constructor\n @implements {Super} */function Sub() {};" +
        "/** @return {number} */Sub.prototype.foo = function() { return 1;};",
        "property foo already defined on interface Root; use @override to " +
        "override it");
  }

  public void testInterfaceInheritanceCheck4() throws Exception {
    testTypes(
        "/** @interface */function Root() {};" +
        "/** @return {number} */Root.prototype.foo = function() {};" +
        "/** @interface\n @extends {Root} */function Super() {};" +
        "/** @constructor\n @implements {Super} */function Sub() {};" +
        "/** @override\n * @return {number} */Sub.prototype.foo =\n" +
        "function() { return 1;};");
  }

  public void testInterfaceInheritanceCheck5() throws Exception {
    testTypes(
        "/** @interface */function Super() {};" +
        "/** @return {string} */Super.prototype.foo = function() {};" +
        "/** @constructor\n @implements {Super} */function Sub() {};" +
        "/** @override\n @return {number} */Sub.prototype.foo =\n" +
        "function() { return 1; };",
        "mismatch of the foo property type and the type of the property it " +
        "overrides from interface Super\n" +
        "original: function (this:Super): string\n" +
        "override: function (this:Sub): number");
  }

  public void testInterfaceInheritanceCheck6() throws Exception {
    testTypes(
        "/** @interface */function Root() {};" +
        "/** @return {string} */Root.prototype.foo = function() {};" +
        "/** @interface\n @extends {Root} */function Super() {};" +
        "/** @constructor\n @implements {Super} */function Sub() {};" +
        "/** @override\n @return {number} */Sub.prototype.foo =\n" +
        "function() { return 1; };",
        "mismatch of the foo property type and the type of the property it " +
        "overrides from interface Root\n" +
        "original: function (this:Root): string\n" +
        "override: function (this:Sub): number");
  }

  public void testInterfaceInheritanceCheck7() throws Exception {
    testTypes(
        "/** @interface */function Super() {};" +
        "/** @param {number} bar */Super.prototype.foo = function(bar) {};" +
        "/** @constructor\n @implements {Super} */function Sub() {};" +
        "/** @override\n  @param {string} bar */Sub.prototype.foo =\n" +
        "function(bar) {};",
        "mismatch of the foo property type and the type of the property it " +
        "overrides from interface Super\n" +
        "original: function (this:Super, number): undefined\n" +
        "override: function (this:Sub, string): undefined");
  }

  public void testInterfaceInheritanceCheck8() throws Exception {
    testTypes(
        "/** @constructor\n @implements {Super} */function Sub() {};" +
        "/** @override */Sub.prototype.foo = function() {};",
        new String[] {
          "Bad type annotation. Unknown type Super",
          "property foo not defined on any superclass of Sub"
        });
  }

  public void testInterfaceInheritanceCheck9() throws Exception {
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

  public void testInterfaceInheritanceCheck10() throws Exception {
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

  public void testInterfaceInheritanceCheck12() throws Exception {
    testTypes(
        "/** @interface */ function I() {};\n" +
        "/** @type {string} */ I.prototype.foobar;\n" +
        "/** \n * @constructor \n * @implements {I} */\n" +
        "function C() {\n" +
        "/** \n * @type {number} */ this.foobar = 2;};\n" +
        "/** @type {I} */ \n var test = new C(); alert(test.foobar);",
        "mismatch of the foobar property type and the type of the property" +
        " it overrides from interface I\n" +
        "original: string\n" +
        "override: number");
  }

  public void testInterfaceInheritanceCheck13() throws Exception {
    testTypes(
        "function abstractMethod() {};\n" +
        "/** @interface */var base = function() {};\n" +
        "/** @extends {base} \n @interface */ var Int = function() {}\n" +
        "/** @type {{bar : !Function}} */ var x; \n" +
        "/** @type {!Function} */ base.prototype.bar = abstractMethod; \n" +
        "/** @type {Int} */ var foo;\n" +
        "foo.bar();");
  }

  /**
   * Verify that templatized interfaces can extend one another and share
   * template values.
   */
  public void testInterfaceInheritanceCheck14() throws Exception {
    testTypes(
        "/** @interface\n @template T */function A() {};" +
        "/** @desc description\n @return {T} */A.prototype.foo = function() {};" +
        "/** @interface\n @template U\n @extends {A<U>} */function B() {};" +
        "/** @desc description\n @return {U} */B.prototype.bar = function() {};" +
        "/** @constructor\n @implements {B<string>} */function C() {};" +
        "/** @return {string}\n @override */C.prototype.foo = function() {};" +
        "/** @return {string}\n @override */C.prototype.bar = function() {};");
  }

  /**
   * Verify that templatized instances can correctly implement templatized
   * interfaces.
   */
  public void testInterfaceInheritanceCheck15() throws Exception {
    testTypes(
        "/** @interface\n @template T */function A() {};" +
        "/** @desc description\n @return {T} */A.prototype.foo = function() {};" +
        "/** @interface\n @template U\n @extends {A<U>} */function B() {};" +
        "/** @desc description\n @return {U} */B.prototype.bar = function() {};" +
        "/** @constructor\n @template V\n @implements {B<V>}\n */function C() {};" +
        "/** @return {V}\n @override */C.prototype.foo = function() {};" +
        "/** @return {V}\n @override */C.prototype.bar = function() {};");
  }

  /**
   * Verify that using @override to declare the signature for an implementing
   * class works correctly when the interface is generic.
   */
  public void testInterfaceInheritanceCheck16() throws Exception {
    testTypes(
        "/** @interface\n @template T */function A() {};" +
        "/** @desc description\n @return {T} */A.prototype.foo = function() {};" +
        "/** @desc description\n @return {T} */A.prototype.bar = function() {};" +
        "/** @constructor\n @implements {A<string>} */function B() {};" +
        "/** @override */B.prototype.foo = function() { return 'string'};" +
        "/** @override */B.prototype.bar = function() { return 3 };",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testInterfacePropertyNotImplemented() throws Exception {
    testTypes(
        "/** @interface */function Int() {};" +
        "/** @desc description */Int.prototype.foo = function() {};" +
        "/** @constructor\n @implements {Int} */function Foo() {};",
        "property foo on interface Int is not implemented by type Foo");
  }

  public void testInterfacePropertyNotImplemented2() throws Exception {
    testTypes(
        "/** @interface */function Int() {};" +
        "/** @desc description */Int.prototype.foo = function() {};" +
        "/** @interface \n @extends {Int} */function Int2() {};" +
        "/** @constructor\n @implements {Int2} */function Foo() {};",
        "property foo on interface Int is not implemented by type Foo");
  }

  /**
   * Verify that templatized interfaces enforce their template type values.
   */
  public void testInterfacePropertyNotImplemented3() throws Exception {
    testTypes(
        "/** @interface\n @template T */function Int() {};" +
        "/** @desc description\n @return {T} */Int.prototype.foo = function() {};" +
        "/** @constructor\n @implements {Int<string>} */function Foo() {};" +
        "/** @return {number}\n @override */Foo.prototype.foo = function() {};",
        "mismatch of the foo property type and the type of the property it " +
        "overrides from interface Int\n" +
        "original: function (this:Int): string\n" +
        "override: function (this:Foo): number");
  }

  public void testStubConstructorImplementingInterface() throws Exception {
    // This does not throw a warning for unimplemented property because Foo is
    // just a stub.
    testTypesWithExterns(
        // externs
        "/** @interface */ function Int() {}\n" +
        "/** @desc description */Int.prototype.foo = function() {};" +
        "/** @constructor \n @implements {Int} */ var Foo;\n",
        "");
  }

  public void testObjectLiteral() throws Exception {
    Node n = parseAndTypeCheck("var a = {m1: 7, m2: 'hello'}");

    Node nameNode = n.getFirstChild().getFirstChild();
    Node objectNode = nameNode.getFirstChild();

    // node extraction
    assertEquals(Token.NAME, nameNode.getType());
    assertEquals(Token.OBJECTLIT, objectNode.getType());

    // value's type
    ObjectType objectType =
        (ObjectType) objectNode.getJSType();
    assertTypeEquals(NUMBER_TYPE, objectType.getPropertyType("m1"));
    assertTypeEquals(STRING_TYPE, objectType.getPropertyType("m2"));

    // variable's type
    assertTypeEquals(objectType, nameNode.getJSType());
  }

  public void testObjectLiteralDeclaration1() throws Exception {
    testTypes(
        "var x = {" +
        "/** @type {boolean} */ abc: true," +
        "/** @type {number} */ 'def': 0," +
        "/** @type {string} */ 3: 'fgh'" +
        "};");
  }

  public void testObjectLiteralDeclaration2() throws Exception {
    testTypes(
        "var x = {" +
        "  /** @type {boolean} */ abc: true" +
        "};" +
        "x.abc = 0;",
        "assignment to property abc of x\n" +
        "found   : number\n" +
        "required: boolean");
  }

  public void testObjectLiteralDeclaration3() throws Exception {
    testTypes(
        "/** @param {{foo: !Function}} x */ function f(x) {}" +
        "f({foo: function() {}});");
  }

  public void testObjectLiteralDeclaration4() throws Exception {
    testClosureTypes(
        "var x = {" +
        "  /** @param {boolean} x */ abc: function(x) {}" +
        "};" +
        "/**\n" +
        " * @param {string} x\n" +
        " * @suppress {duplicate}\n" +
        " */ x.abc = function(x) {};",
        "assignment to property abc of x\n" +
        "found   : function (string): undefined\n" +
        "required: function (boolean): undefined");
    // TODO(user): suppress {duplicate} currently also silence the
    // redefining type error in the TypeValidator. Maybe it needs
    // a new suppress name instead?
  }

  public void testObjectLiteralDeclaration5() throws Exception {
    testTypes(
        "var x = {" +
        "  /** @param {boolean} x */ abc: function(x) {}" +
        "};" +
        "/**\n" +
        " * @param {boolean} x\n" +
        " * @suppress {duplicate}\n" +
        " */ x.abc = function(x) {};");
  }

  public void testObjectLiteralDeclaration6() throws Exception {
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

  public void testObjectLiteralDeclaration7() throws Exception {
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

  public void testCallDateConstructorAsFunction() throws Exception {
    // ECMA-262 15.9.2: When Date is called as a function rather than as a
    // constructor, it returns a string.
    Node n = parseAndTypeCheck("Date()");
    assertTypeEquals(STRING_TYPE, n.getFirstChild().getFirstChild().getJSType());
  }

  // According to ECMA-262, Error & Array function calls are equivalent to
  // constructor calls.

  public void testCallErrorConstructorAsFunction() throws Exception {
    Node n = parseAndTypeCheck("Error('x')");
    assertTypeEquals(ERROR_TYPE,
                 n.getFirstChild().getFirstChild().getJSType());
  }

  public void testCallArrayConstructorAsFunction() throws Exception {
    Node n = parseAndTypeCheck("Array()");
    assertTypeEquals(ARRAY_TYPE,
                 n.getFirstChild().getFirstChild().getJSType());
  }

  public void testPropertyTypeOfUnionType() throws Exception {
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

  // TODO(user): We should flag these as invalid. This will probably happen
  // when we make sure the interface is never referenced outside of its
  // definition. We might want more specific and helpful error messages.
  //public void testWarningOnInterfacePrototype() throws Exception {
  //  testTypes("/** @interface */ u.T = function() {};\n" +
  //      "/** @return {number} */ u.T.prototype = function() { };",
  //      "e of its definition");
  //}
  //
  //public void testBadPropertyOnInterface1() throws Exception {
  //  testTypes("/** @interface */ u.T = function() {};\n" +
  //      "/** @return {number} */ u.T.f = function() { return 1;};",
  //      "cannot reference an interface outside of its definition");
  //}
  //
  //public void testBadPropertyOnInterface2() throws Exception {
  //  testTypes("/** @interface */ function T() {};\n" +
  //      "/** @return {number} */ T.f = function() { return 1;};",
  //      "cannot reference an interface outside of its definition");
  //}
  //
  //public void testBadPropertyOnInterface3() throws Exception {
  //  testTypes("/** @interface */ u.T = function() {}; u.T.x",
  //      "cannot reference an interface outside of its definition");
  //}
  //
  //public void testBadPropertyOnInterface4() throws Exception {
  //  testTypes("/** @interface */ function T() {}; T.x;",
  //      "cannot reference an interface outside of its definition");
  //}

  public void testAnnotatedPropertyOnInterface1() throws Exception {
    // For interfaces we must allow function definitions that don't have a
    // return statement, even though they declare a returned type.
    testTypes("/** @interface */ u.T = function() {};\n" +
        "/** @return {number} */ u.T.prototype.f = function() {};");
  }

  public void testAnnotatedPropertyOnInterface2() throws Exception {
    testTypes("/** @interface */ u.T = function() {};\n" +
        "/** @return {number} */ u.T.prototype.f = function() { };");
  }

  public void testAnnotatedPropertyOnInterface3() throws Exception {
    testTypes("/** @interface */ function T() {};\n" +
        "/** @return {number} */ T.prototype.f = function() { };");
  }

  public void testAnnotatedPropertyOnInterface4() throws Exception {
    testTypes(
        CLOSURE_DEFS +
        "/** @interface */ function T() {};\n" +
        "/** @return {number} */ T.prototype.f = goog.abstractMethod;");
  }

  // TODO(user): If we want to support this syntax we have to warn about
  // missing annotations.
  //public void testWarnUnannotatedPropertyOnInterface1() throws Exception {
  //  testTypes("/** @interface */ u.T = function () {}; u.T.prototype.x;",
  //      "interface property x is not annotated");
  //}
  //
  //public void testWarnUnannotatedPropertyOnInterface2() throws Exception {
  //  testTypes("/** @interface */ function T() {}; T.prototype.x;",
  //      "interface property x is not annotated");
  //}

  public void testWarnUnannotatedPropertyOnInterface5() throws Exception {
    testTypes("/** @interface */ u.T = function () {};\n" +
        "/** @desc x does something */u.T.prototype.x = function() {};");
  }

  public void testWarnUnannotatedPropertyOnInterface6() throws Exception {
    testTypes("/** @interface */ function T() {};\n" +
        "/** @desc x does something */T.prototype.x = function() {};");
  }

  // TODO(user): If we want to support this syntax we have to warn about
  // the invalid type of the interface member.
  //public void testWarnDataPropertyOnInterface1() throws Exception {
  //  testTypes("/** @interface */ u.T = function () {};\n" +
  //      "/** @type {number} */u.T.prototype.x;",
  //      "interface members can only be plain functions");
  //}

  public void testDataPropertyOnInterface1() throws Exception {
    testTypes("/** @interface */ function T() {};\n" +
        "/** @type {number} */T.prototype.x;");
  }

  public void testDataPropertyOnInterface2() throws Exception {
    reportMissingOverrides = CheckLevel.OFF;
    testTypes("/** @interface */ function T() {};\n" +
        "/** @type {number} */T.prototype.x;\n" +
        "/** @constructor \n" +
        " *  @implements {T} \n" +
        " */\n" +
        "function C() {}\n" +
        "C.prototype.x = 'foo';",
        "mismatch of the x property type and the type of the property it " +
        "overrides from interface T\n" +
        "original: number\n" +
        "override: string");
  }

  public void testDataPropertyOnInterface3() throws Exception {
    testTypes("/** @interface */ function T() {};\n" +
        "/** @type {number} */T.prototype.x;\n" +
        "/** @constructor \n" +
        " *  @implements {T} \n" +
        " */\n" +
        "function C() {}\n" +
        "/** @override */\n" +
        "C.prototype.x = 'foo';",
        "mismatch of the x property type and the type of the property it " +
        "overrides from interface T\n" +
        "original: number\n" +
        "override: string");
  }

  public void testDataPropertyOnInterface4() throws Exception {
    testTypes("/** @interface */ function T() {};\n" +
        "/** @type {number} */T.prototype.x;\n" +
        "/** @constructor \n" +
        " *  @implements {T} \n" +
        " */\n" +
        "function C() { /** @type {string} */ \n this.x = 'foo'; }\n",
        "mismatch of the x property type and the type of the property it " +
        "overrides from interface T\n" +
        "original: number\n" +
        "override: string");
  }

  public void testWarnDataPropertyOnInterface3() throws Exception {
    testTypes("/** @interface */ u.T = function () {};\n" +
        "/** @type {number} */u.T.prototype.x = 1;",
        "interface members can only be empty property declarations, "
        + "empty functions, or goog.abstractMethod");
  }

  public void testWarnDataPropertyOnInterface4() throws Exception {
    testTypes("/** @interface */ function T() {};\n" +
        "/** @type {number} */T.prototype.x = 1;",
        "interface members can only be empty property declarations, "
        + "empty functions, or goog.abstractMethod");
  }

  // TODO(user): If we want to support this syntax we should warn about the
  // mismatching types in the two tests below.
  //public void testErrorMismatchingPropertyOnInterface1() throws Exception {
  //  testTypes("/** @interface */ u.T = function () {};\n" +
  //      "/** @param {Number} foo */u.T.prototype.x =\n" +
  //      "/** @param {String} foo */function(foo) {};",
  //      "found   : \n" +
  //      "required: ");
  //}
  //
  //public void testErrorMismatchingPropertyOnInterface2() throws Exception {
  //  testTypes("/** @interface */ function T() {};\n" +
  //      "/** @return {number} */T.prototype.x =\n" +
  //      "/** @return {string} */function() {};",
  //      "found   : \n" +
  //      "required: ");
  //}

  // TODO(user): We should warn about this (bar is missing an annotation). We
  // probably don't want to warn about all missing parameter annotations, but
  // we should be as strict as possible regarding interfaces.
  //public void testErrorMismatchingPropertyOnInterface3() throws Exception {
  //  testTypes("/** @interface */ u.T = function () {};\n" +
  //      "/** @param {Number} foo */u.T.prototype.x =\n" +
  //      "function(foo, bar) {};",
  //      "found   : \n" +
  //      "required: ");
  //}

  public void testErrorMismatchingPropertyOnInterface4() throws Exception {
    testTypes("/** @interface */ u.T = function () {};\n" +
        "/** @param {Number} foo */u.T.prototype.x =\n" +
        "function() {};",
        "parameter foo does not appear in u.T.prototype.x's parameter list");
  }

  public void testErrorMismatchingPropertyOnInterface5() throws Exception {
    testTypes("/** @interface */ function T() {};\n" +
        "/** @type {number} */T.prototype.x = function() { };",
        "assignment to property x of T.prototype\n" +
        "found   : function (): undefined\n" +
        "required: number");
  }

  public void testErrorMismatchingPropertyOnInterface6() throws Exception {
    testClosureTypesMultipleWarnings(
        "/** @interface */ function T() {};\n" +
        "/** @return {number} */T.prototype.x = 1",
        Lists.newArrayList(
            "assignment to property x of T.prototype\n" +
            "found   : number\n" +
            "required: function (this:T): number",
            "interface members can only be empty property declarations, " +
            "empty functions, or goog.abstractMethod"));
  }

  public void testInterfaceNonEmptyFunction() throws Exception {
    testTypes("/** @interface */ function T() {};\n" +
        "T.prototype.x = function() { return 'foo'; }",
        "interface member functions must have an empty body"
        );
  }

  public void testDoubleNestedInterface() throws Exception {
    testTypes("/** @interface */ var I1 = function() {};\n" +
              "/** @interface */ I1.I2 = function() {};\n" +
              "/** @interface */ I1.I2.I3 = function() {};\n");
  }

  public void testStaticDataPropertyOnNestedInterface() throws Exception {
    testTypes("/** @interface */ var I1 = function() {};\n" +
              "/** @interface */ I1.I2 = function() {};\n" +
              "/** @type {number} */ I1.I2.x = 1;\n");
  }

  public void testInterfaceInstantiation() throws Exception {
    testTypes("/** @interface */var f = function(){}; new f",
              "cannot instantiate non-constructor");
  }

  public void testPrototypeLoop() throws Exception {
    testClosureTypesMultipleWarnings(
        suppressMissingProperty("foo") +
        "/** @constructor \n * @extends {T} */var T = function() {};" +
        "alert((new T).foo);",
        Lists.newArrayList(
            "Parse error. Cycle detected in inheritance chain of type T",
            "Could not resolve type in @extends tag of T"));
  }

  public void testImplementsLoop() throws Exception {
    testClosureTypesMultipleWarnings(
        suppressMissingProperty("foo") +
        "/** @constructor \n * @implements {T} */var T = function() {};" +
        "alert((new T).foo);",
        Lists.newArrayList(
            "Parse error. Cycle detected in inheritance chain of type T"));
  }

  public void testImplementsExtendsLoop() throws Exception {
    testClosureTypesMultipleWarnings(
        suppressMissingProperty("foo") +
            "/** @constructor \n * @implements {F} */var G = function() {};" +
            "/** @constructor \n * @extends {G} */var F = function() {};" +
        "alert((new F).foo);",
        Lists.newArrayList(
            "Parse error. Cycle detected in inheritance chain of type F"));
  }

  public void testInterfaceExtendsLoop() throws Exception {
    // TODO(nicksantos): This should emit a warning. This test is still
    // useful to ensure the compiler doesn't crash.
    testClosureTypesMultipleWarnings(
        suppressMissingProperty("foo") +
            "/** @interface \n * @extends {F} */var G = function() {};" +
            "/** @interface \n * @extends {G} */var F = function() {};" +
            "/** @constructor \n * @implements {F} */var H = function() {};" +
        "alert((new H).foo);",
        Lists.<String>newArrayList());
  }

  public void testConversionFromInterfaceToRecursiveConstructor()
      throws Exception {
    testClosureTypesMultipleWarnings(
        suppressMissingProperty("foo") +
            "/** @interface */ var OtherType = function() {}\n" +
            "/** @implements {MyType} \n * @constructor */\n" +
            "var MyType = function() {}\n" +
            "/** @type {MyType} */\n" +
            "var x = /** @type {!OtherType} */ (new Object());",
        Lists.newArrayList(
            "Parse error. Cycle detected in inheritance chain of type MyType",
            "initializing variable\n" +
            "found   : OtherType\n" +
            "required: (MyType|null)"));
  }

  public void testDirectPrototypeAssign() throws Exception {
    // For now, we just ignore @type annotations on the prototype.
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @constructor */ function Bar() {}" +
        "/** @type {Array} */ Bar.prototype = new Foo()");
  }

  // In all testResolutionViaRegistry* tests, since u is unknown, u.T can only
  // be resolved via the registry and not via properties.

  public void testResolutionViaRegistry1() throws Exception {
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

  public void testResolutionViaRegistry2() throws Exception {
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

  public void testResolutionViaRegistry3() throws Exception {
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

  public void testResolutionViaRegistry4() throws Exception {
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

  public void testResolutionViaRegistry5() throws Exception {
    Node n = parseAndTypeCheck("/** @constructor */ u.T = function() {}; u.T");
    JSType type = n.getLastChild().getLastChild().getJSType();
    assertFalse(type.isUnknownType());
    assertTrue(type instanceof FunctionType);
    assertEquals("u.T",
        ((FunctionType) type).getInstanceType().getReferenceName());
  }

  public void testGatherProperyWithoutAnnotation1() throws Exception {
    Node n = parseAndTypeCheck("/** @constructor */ var T = function() {};" +
        "/** @type {!T} */var t; t.x; t;");
    JSType type = n.getLastChild().getLastChild().getJSType();
    assertFalse(type.isUnknownType());
    assertTrue(type instanceof ObjectType);
    ObjectType objectType = (ObjectType) type;
    assertFalse(objectType.hasProperty("x"));
    Asserts.assertTypeCollectionEquals(
        Lists.newArrayList(objectType),
        registry.getTypesWithProperty("x"));
  }

  public void testGatherProperyWithoutAnnotation2() throws Exception {
    TypeCheckResult ns =
        parseAndTypeCheckWithScope("/** @type {!Object} */var t; t.x; t;");
    Node n = ns.root;
    JSType type = n.getLastChild().getLastChild().getJSType();
    assertFalse(type.isUnknownType());
    assertTypeEquals(type, OBJECT_TYPE);
    assertTrue(type instanceof ObjectType);
    ObjectType objectType = (ObjectType) type;
    assertFalse(objectType.hasProperty("x"));
    Asserts.assertTypeCollectionEquals(
        Lists.newArrayList(OBJECT_TYPE),
        registry.getTypesWithProperty("x"));
  }

  public void testFunctionMasksVariableBug() throws Exception {
    testTypes("var x = 4; var f = function x(b) { return b ? 1 : x(true); };",
        "function x masks variable (IE bug)");
  }

  public void testDfa1() throws Exception {
    testTypes("var x = null;\n x = 1;\n /** @type number */ var y = x;");
  }

  public void testDfa2() throws Exception {
    testTypes("function u() {}\n" +
        "/** @return {number} */ function f() {\nvar x = 'todo';\n" +
        "if (u()) { x = 1; } else { x = 2; } return x;\n}");
  }

  public void testDfa3() throws Exception {
    testTypes("function u() {}\n" +
        "/** @return {number} */ function f() {\n" +
        "/** @type {number|string} */ var x = 'todo';\n" +
        "if (u()) { x = 1; } else { x = 2; } return x;\n}");
  }

  public void testDfa4() throws Exception {
    testTypes("/** @param {Date?} d */ function f(d) {\n" +
        "if (!d) { return; }\n" +
        "/** @type {!Date} */ var e = d;\n}");
  }

  public void testDfa5() throws Exception {
    testTypes("/** @return {string?} */ function u() {return 'a';}\n" +
        "/** @param {string?} x\n@return {string} */ function f(x) {\n" +
        "while (!x) { x = u(); }\nreturn x;\n}");
  }

  public void testDfa6() throws Exception {
    testTypes("/** @return {Object?} */ function u() {return {};}\n" +
        "/** @param {Object?} x */ function f(x) {\n" +
        "while (x) { x = u(); if (!x) { x = u(); } }\n}");
  }

  public void testDfa7() throws Exception {
    testTypes("/** @constructor */ var T = function() {};\n" +
        "/** @type {Date?} */ T.prototype.x = null;\n" +
        "/** @param {!T} t */ function f(t) {\n" +
        "if (!t.x) { return; }\n" +
        "/** @type {!Date} */ var e = t.x;\n}");
  }

  public void testDfa8() throws Exception {
    testTypes("/** @constructor */ var T = function() {};\n" +
        "/** @type {number|string} */ T.prototype.x = '';\n" +
        "function u() {}\n" +
        "/** @param {!T} t\n@return {number} */ function f(t) {\n" +
        "if (u()) { t.x = 1; } else { t.x = 2; } return t.x;\n}");
  }

  public void testDfa9() throws Exception {
    testTypes("function f() {\n/** @type {string?} */var x;\nx = null;\n" +
        "if (x == null) { return 0; } else { return 1; } }",
        "condition always evaluates to true\n" +
        "left : null\n" +
        "right: null");
  }

  public void testDfa10() throws Exception {
    testTypes("/** @param {null} x */ function g(x) {}" +
        "/** @param {string?} x */function f(x) {\n" +
        "if (!x) { x = ''; }\n" +
        "if (g(x)) { return 0; } else { return 1; } }",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : string\n" +
        "required: null");
  }

  public void testDfa11() throws Exception {
    testTypes("/** @param {string} opt_x\n@return {string} */\n" +
        "function f(opt_x) { if (!opt_x) { " +
        "throw new Error('x cannot be empty'); } return opt_x; }");
  }

  public void testDfa12() throws Exception {
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

  public void testDfa13() throws Exception {
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

  public void testTypeInferenceWithCast1() throws Exception {
    testTypes(
        "/**@return {(number,null,undefined)}*/function u(x) {return null;}" +
        "/**@param {number?} x\n@return {number?}*/function f(x) {return x;}" +
        "/**@return {number?}*/function g(x) {" +
        "var y = /**@type {number?}*/(u(x)); return f(y);}");
  }

  public void testTypeInferenceWithCast2() throws Exception {
    testTypes(
        "/**@return {(number,null,undefined)}*/function u(x) {return null;}" +
        "/**@param {number?} x\n@return {number?}*/function f(x) {return x;}" +
        "/**@return {number?}*/function g(x) {" +
        "var y; y = /**@type {number?}*/(u(x)); return f(y);}");
  }

  public void testTypeInferenceWithCast3() throws Exception {
    testTypes(
        "/**@return {(number,null,undefined)}*/function u(x) {return 1;}" +
        "/**@return {number}*/function g(x) {" +
        "return /**@type {number}*/(u(x));}");
  }

  public void testTypeInferenceWithCast4() throws Exception {
    testTypes(
        "/**@return {(number,null,undefined)}*/function u(x) {return 1;}" +
        "/**@return {number}*/function g(x) {" +
        "return /**@type {number}*/(u(x)) && 1;}");
  }

  public void testTypeInferenceWithCast5() throws Exception {
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

  public void testTypeInferenceWithClosure1() throws Exception {
    testTypes(
        "/** @return {boolean} */" +
        "function f() {" +
        "  /** @type {?string} */ var x = null;" +
        "  function g() { x = 'y'; } g(); " +
        "  return x == null;" +
        "}");
  }

  public void testTypeInferenceWithClosure2() throws Exception {
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

  public void testTypeInferenceWithNoEntry1() throws Exception {
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

  public void testTypeInferenceWithNoEntry2() throws Exception {
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

  public void testForwardPropertyReference() throws Exception {
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

  public void testNoForwardTypeDeclaration() throws Exception {
    testTypes(
        "/** @param {MyType} x */ function f(x) {}",
        "Bad type annotation. Unknown type MyType");
  }

  public void testNoForwardTypeDeclarationAndNoBraces() throws Exception {
    testTypes("/** @return The result. */ function f() {}");
  }

  public void testForwardTypeDeclaration1() throws Exception {
    testClosureTypes(
        // malformed addDependency calls shouldn't cause a crash
        "goog.addDependency();" +
        "goog.addDependency('y', [goog]);" +

        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType} x \n * @return {number} */" +
        "function f(x) { return 3; }", null);
  }

  public void testForwardTypeDeclaration2() throws Exception {
    String f = "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType} x */ function f(x) { }";
    testClosureTypes(f, null);
    testClosureTypes(f + "f(3);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: (MyType|null)");
  }

  public void testForwardTypeDeclaration3() throws Exception {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType} x */ function f(x) { return x; }" +
        "/** @constructor */ var MyType = function() {};" +
        "f(3);",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : number\n" +
        "required: (MyType|null)");
  }

  public void testForwardTypeDeclaration4() throws Exception {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType} x */ function f(x) { return x; }" +
        "/** @constructor */ var MyType = function() {};" +
        "f(new MyType());",
        null);
  }

  public void testForwardTypeDeclaration5() throws Exception {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {MyType}\n" +
        " */ var YourType = function() {};" +
        "/** @override */ YourType.prototype.method = function() {};",
        "Could not resolve type in @extends tag of YourType");
  }

  public void testForwardTypeDeclaration6() throws Exception {
    testClosureTypesMultipleWarnings(
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/**\n" +
        " * @constructor\n" +
        " * @implements {MyType}\n" +
        " */ var YourType = function() {};" +
        "/** @override */ YourType.prototype.method = function() {};",
        Lists.newArrayList(
            "Could not resolve type in @implements tag of YourType",
            "property method not defined on any superclass of YourType"));
  }

  public void testForwardTypeDeclaration7() throws Exception {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType=} x */" +
        "function f(x) { return x == undefined; }", null);
  }

  public void testForwardTypeDeclaration8() throws Exception {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType} x */" +
        "function f(x) { return x.name == undefined; }", null);
  }

  public void testForwardTypeDeclaration9() throws Exception {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType} x */" +
        "function f(x) { x.name = 'Bob'; }", null);
  }

  public void testForwardTypeDeclaration10() throws Exception {
    String f = "goog.addDependency('zzz.js', ['MyType'], []);" +
        "/** @param {MyType|number} x */ function f(x) { }";
    testClosureTypes(f, null);
    testClosureTypes(f + "f(3);", null);
    testClosureTypes(f + "f('3');",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: (MyType|null|number)");
  }

  public void testForwardTypeDeclaration12() throws Exception {
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

  public void testForwardTypeDeclaration13() throws Exception {
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

  public void testDuplicateTypeDef() throws Exception {
    testTypes(
        "var goog = {};" +
        "/** @constructor */ goog.Bar = function() {};" +
        "/** @typedef {number} */ goog.Bar;",
        "variable goog.Bar redefined with type None, " +
        "original definition at [testcode]:1 " +
        "with type function (new:goog.Bar): undefined");
  }

  public void testTypeDef1() throws Exception {
    testTypes(
        "var goog = {};" +
        "/** @typedef {number} */ goog.Bar;" +
        "/** @param {goog.Bar} x */ function f(x) {}" +
        "f(3);");
  }

  public void testTypeDef2() throws Exception {
    testTypes(
        "var goog = {};" +
        "/** @typedef {number} */ goog.Bar;" +
        "/** @param {goog.Bar} x */ function f(x) {}" +
        "f('3');",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testTypeDef3() throws Exception {
    testTypes(
        "var goog = {};" +
        "/** @typedef {number} */ var Bar;" +
        "/** @param {Bar} x */ function f(x) {}" +
        "f('3');",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : string\n" +
        "required: number");
  }

  public void testTypeDef4() throws Exception {
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

  public void testTypeDef5() throws Exception {
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

  public void testCircularTypeDef() throws Exception {
    testTypes(
        "var goog = {};" +
        "/** @typedef {number|Array<goog.Bar>} */ goog.Bar;" +
        "/** @param {goog.Bar} x */ function f(x) {}" +
        "f(3); f([3]); f([[3]]);");
  }

  public void testGetTypedPercent1() throws Exception {
    String js = "var id = function(x) { return x; }\n" +
                "var id2 = function(x) { return id(x); }";
    assertEquals(50.0, getTypedPercent(js), 0.1);
  }

  public void testGetTypedPercent2() throws Exception {
    String js = "var x = {}; x.y = 1;";
    assertEquals(100.0, getTypedPercent(js), 0.1);
  }

  public void testGetTypedPercent3() throws Exception {
    String js = "var f = function(x) { x.a = x.b; }";
    assertEquals(50.0, getTypedPercent(js), 0.1);
  }

  public void testGetTypedPercent4() throws Exception {
    String js = "var n = {};\n /** @constructor */ n.T = function() {};\n" +
        "/** @type n.T */ var x = new n.T();";
    assertEquals(100.0, getTypedPercent(js), 0.1);
  }

  public void testGetTypedPercent5() throws Exception {
    String js = "/** @enum {number} */ keys = {A: 1,B: 2,C: 3};";
    assertEquals(100.0, getTypedPercent(js), 0.1);
  }

  public void testGetTypedPercent6() throws Exception {
    String js = "a = {TRUE: 1, FALSE: 0};";
    assertEquals(100.0, getTypedPercent(js), 0.1);
  }

  private double getTypedPercent(String js) throws Exception {
    Node n = compiler.parseTestCode(js);

    Node externs = new Node(Token.BLOCK);
    Node externAndJsRoot = new Node(Token.BLOCK, externs, n);
    externAndJsRoot.setIsSyntheticBlock(true);

    TypeCheck t = makeTypeCheck();
    t.processForTesting(null, n);
    return t.getTypedPercent();
  }

  private static ObjectType getInstanceType(Node js1Node) {
    JSType type = js1Node.getFirstChild().getJSType();
    assertNotNull(type);
    assertTrue(type instanceof FunctionType);
    FunctionType functionType = (FunctionType) type;
    assertTrue(functionType.isConstructor());
    return functionType.getInstanceType();
  }

  public void testPrototypePropertyReference() throws Exception {
    TypeCheckResult p = parseAndTypeCheckWithScope(""
        + "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @param {number} a */\n"
        + "Foo.prototype.bar = function(a){};\n"
        + "/** @param {Foo} f */\n"
        + "function baz(f) {\n"
        + "  Foo.prototype.bar.call(f, 3);\n"
        + "}");
    assertEquals(0, compiler.getErrorCount());
    assertEquals(0, compiler.getWarningCount());

    assertTrue(p.scope.getVar("Foo").getType() instanceof FunctionType);
    FunctionType fooType = (FunctionType) p.scope.getVar("Foo").getType();
    assertEquals("function (this:Foo, number): undefined",
                 fooType.getPrototype().getPropertyType("bar").toString());
  }

  public void testResolvingNamedTypes() throws Exception {
    String js = ""
        + "/** @constructor */\n"
        + "var Foo = function() {}\n"
        + "/** @param {number} a */\n"
        + "Foo.prototype.foo = function(a) {\n"
        + "  return this.baz().toString();\n"
        + "};\n"
        + "/** @return {Baz} */\n"
        + "Foo.prototype.baz = function() { return new Baz(); };\n"
        + "/** @constructor\n"
        + "  * @extends Foo */\n"
        + "var Bar = function() {};"
        + "/** @constructor */\n"
        + "var Baz = function() {};";
    assertEquals(100.0, getTypedPercent(js), 0.1);
  }

  public void testMissingProperty1() throws Exception {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() { return this.a; };" +
        "Foo.prototype.baz = function() { this.a = 3; };");
  }

  public void testMissingProperty2() throws Exception {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() { return this.a; };" +
        "Foo.prototype.baz = function() { this.b = 3; };",
        "Property a never defined on Foo");
  }

  public void testMissingProperty3() throws Exception {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() { return this.a; };" +
        "(new Foo).a = 3;");
  }

  public void testMissingProperty4() throws Exception {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() { return this.a; };" +
        "(new Foo).b = 3;",
        "Property a never defined on Foo");
  }

  public void testMissingProperty5() throws Exception {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() { return this.a; };" +
        "/** @constructor */ function Bar() { this.a = 3; };",
        "Property a never defined on Foo");
  }

  public void testMissingProperty6() throws Exception {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() { return this.a; };" +
        "/** @constructor \n * @extends {Foo} */ " +
        "function Bar() { this.a = 3; };");
  }

  public void testMissingProperty7() throws Exception {
    testTypes(
        "/** @param {Object} obj */" +
        "function foo(obj) { return obj.impossible; }",
        "Property impossible never defined on Object");
  }

  public void testMissingProperty8() throws Exception {
    testTypes(
        "/** @param {Object} obj */" +
        "function foo(obj) { return typeof obj.impossible; }");
  }

  public void testMissingProperty9() throws Exception {
    testTypes(
        "/** @param {Object} obj */" +
        "function foo(obj) { if (obj.impossible) { return true; } }");
  }

  public void testMissingProperty10() throws Exception {
    testTypes(
        "/** @param {Object} obj */" +
        "function foo(obj) { while (obj.impossible) { return true; } }");
  }

  public void testMissingProperty11() throws Exception {
    testTypes(
        "/** @param {Object} obj */" +
        "function foo(obj) { for (;obj.impossible;) { return true; } }");
  }

  public void testMissingProperty12() throws Exception {
    testTypes(
        "/** @param {Object} obj */" +
        "function foo(obj) { do { } while (obj.impossible); }");
  }

  public void testMissingProperty13() throws Exception {
    testTypes(
        "var goog = {}; goog.isDef = function(x) { return false; };" +
        "/** @param {Object} obj */" +
        "function foo(obj) { return goog.isDef(obj.impossible); }");
  }

  public void testMissingProperty14() throws Exception {
    testTypes(
        "var goog = {}; goog.isDef = function(x) { return false; };" +
        "/** @param {Object} obj */" +
        "function foo(obj) { return goog.isNull(obj.impossible); }",
        "Property isNull never defined on goog");
  }

  public void testMissingProperty15() throws Exception {
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { if (x.foo) { x.foo(); } }");
  }

  public void testMissingProperty16() throws Exception {
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { x.foo(); if (x.foo) {} }",
        "Property foo never defined on Object");
  }

  public void testMissingProperty17() throws Exception {
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { if (typeof x.foo == 'function') { x.foo(); } }");
  }

  public void testMissingProperty18() throws Exception {
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { if (x.foo instanceof Function) { x.foo(); } }");
  }

  public void testMissingProperty19() throws Exception {
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { if (x.bar) { if (x.foo) {} } else { x.foo(); } }",
        "Property foo never defined on Object");
  }

  public void testMissingProperty20() throws Exception {
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { if (x.foo) { } else { x.foo(); } }",
        "Property foo never defined on Object");
  }

  public void testMissingProperty21() throws Exception {
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { x.foo && x.foo(); }");
  }

  public void testMissingProperty22() throws Exception {
    testTypes(
        "/** @param {Object} x \n * @return {boolean} */" +
        "function f(x) { return x.foo ? x.foo() : true; }");
  }

  public void testMissingProperty23() throws Exception {
    testTypes(
        "function f(x) { x.impossible(); }",
        "Property impossible never defined on x");
  }

  public void testMissingProperty24() throws Exception {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MissingType'], []);" +
        "/** @param {MissingType} x */" +
        "function f(x) { x.impossible(); }", null);
  }

  public void testMissingProperty25() throws Exception {
    testTypes(
        "/** @constructor */ var Foo = function() {};" +
        "Foo.prototype.bar = function() {};" +
        "/** @constructor */ var FooAlias = Foo;" +
        "(new FooAlias()).bar();");
  }

  public void testMissingProperty26() throws Exception {
    testTypes(
        "/** @constructor */ var Foo = function() {};" +
        "/** @constructor */ var FooAlias = Foo;" +
        "FooAlias.prototype.bar = function() {};" +
        "(new Foo()).bar();");
  }

  public void testMissingProperty27() throws Exception {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MissingType'], []);" +
        "/** @param {?MissingType} x */" +
        "function f(x) {" +
        "  for (var parent = x; parent; parent = parent.getParent()) {}" +
        "}", null);
  }

  public void testMissingProperty28() throws Exception {
    testTypes(
        "function f(obj) {" +
        "  /** @type {*} */ obj.foo;" +
        "  return obj.foo;" +
        "}");
    testTypes(
        "function f(obj) {" +
        "  /** @type {*} */ obj.foo;" +
        "  return obj.foox;" +
        "}",
        "Property foox never defined on obj");
  }

  public void testMissingProperty29() throws Exception {
    // This used to emit a warning.
    testTypesWithExterns(
        // externs
        "/** @constructor */ var Foo;" +
        "Foo.prototype.opera;" +
        "Foo.prototype.opera.postError;",
        "");
  }

  public void testMissingProperty30() throws Exception {
    testTypes(
        "/** @return {*} */" +
        "function f() {" +
        " return {};" +
        "}" +
        "f().a = 3;" +
        "/** @param {Object} y */ function g(y) { return y.a; }");
  }

  public void testMissingProperty31() throws Exception {
    testTypes(
        "/** @return {Array|number} */" +
        "function f() {" +
        " return [];" +
        "}" +
        "f().a = 3;" +
        "/** @param {Array} y */ function g(y) { return y.a; }");
  }

  public void testMissingProperty32() throws Exception {
    testTypes(
        "/** @return {Array|number} */" +
        "function f() {" +
        " return [];" +
        "}" +
        "f().a = 3;" +
        "/** @param {Date} y */ function g(y) { return y.a; }",
        "Property a never defined on Date");
  }

  public void testMissingProperty33() throws Exception {
    testTypes(
      "/** @param {Object} x */" +
      "function f(x) { !x.foo || x.foo(); }");
  }

  public void testMissingProperty34() throws Exception {
    testTypes(
        "/** @fileoverview \n * @suppress {missingProperties} */" +
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.bar = function() { return this.a; };" +
        "Foo.prototype.baz = function() { this.b = 3; };");
  }

  public void testMissingProperty35() throws Exception {
    // Bar has specialProp defined, so Bar|Baz may have specialProp defined.
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @constructor */ function Bar() {}" +
        "/** @constructor */ function Baz() {}" +
        "/** @param {Foo|Bar} x */ function f(x) { x.specialProp = 1; }" +
        "/** @param {Bar|Baz} x */ function g(x) { return x.specialProp; }");
  }

  public void testMissingProperty36() throws Exception {
    // Foo has baz defined, and SubFoo has bar defined, so some objects with
    // bar may have baz.
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype.baz = 0;" +
        "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
        "SubFoo.prototype.bar = 0;" +
        "/** @param {{bar: number}} x */ function f(x) { return x.baz; }");
  }

  public void testMissingProperty37() throws Exception {
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

  public void testMissingProperty38() throws Exception {
    testTypes(
        "/** @constructor */ function Foo() {}" +
        "/** @constructor */ function Bar() {}" +
        "/** @return {Foo|Bar} */ function f() { return new Foo(); }" +
        "f().missing;",
        "Property missing never defined on (Bar|Foo|null)");
  }

  public void testMissingProperty39() throws Exception {
    testTypes(
        "/** @return {string|number} */ function f() { return 3; }" +
        "f().length;");
  }

  public void testMissingProperty40() throws Exception {
    testClosureTypes(
        "goog.addDependency('zzz.js', ['MissingType'], []);" +
        "/** @param {(Array|MissingType)} x */" +
        "function f(x) { x.impossible(); }", null);
  }

  public void testMissingProperty41() throws Exception {
    testTypes(
        "/** @param {(Array|Date)} x */" +
        "function f(x) { if (x.impossible) x.impossible(); }");
  }


  public void testMissingProperty42() throws Exception {
    testTypes(
        "/** @param {Object} x */" +
        "function f(x) { " +
        "  if (typeof x.impossible == 'undefined') throw Error();" +
        "  return x.impossible;" +
        "}");
  }

  public void testMissingProperty43() throws Exception {
    testTypes(
        "function f(x) { " +
        " return /** @type {number} */ (x.impossible) && 1;" +
        "}");
  }

  public void testReflectObject1() throws Exception {
    testClosureTypes(
        "var goog = {}; goog.reflect = {}; " +
        "goog.reflect.object = function(x, y){};" +
        "/** @constructor */ function A() {}" +
        "goog.reflect.object(A, {x: 3});",
        null);
  }

  public void testReflectObject2() throws Exception {
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

  public void testLends1() throws Exception {
    testTypes(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, /** @lends */ ({bar: 1}));",
        "Bad type annotation. missing object name in @lends tag");
  }

  public void testLends2() throws Exception {
    testTypes(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, /** @lends {Foob} */ ({bar: 1}));",
        "Variable Foob not declared before @lends annotation.");
  }

  public void testLends3() throws Exception {
    testTypes(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, {bar: 1});" +
        "alert(Foo.bar);",
        "Property bar never defined on Foo");
  }

  public void testLends4() throws Exception {
    testTypes(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, /** @lends {Foo} */ ({bar: 1}));" +
        "alert(Foo.bar);");
  }

  public void testLends5() throws Exception {
    testTypes(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, {bar: 1});" +
        "alert((new Foo()).bar);",
        "Property bar never defined on Foo");
  }

  public void testLends6() throws Exception {
    testTypes(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, /** @lends {Foo.prototype} */ ({bar: 1}));" +
        "alert((new Foo()).bar);");
  }

  public void testLends7() throws Exception {
    testTypes(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, /** @lends {Foo.prototype|Foo} */ ({bar: 1}));",
        "Bad type annotation. expected closing }");
  }

  public void testLends8() throws Exception {
    testTypes(
        "function extend(x, y) {}" +
        "/** @type {number} */ var Foo = 3;" +
        "extend(Foo, /** @lends {Foo} */ ({bar: 1}));",
        "May only lend properties to object types. Foo has type number.");
  }

  public void testLends9() throws Exception {
    testClosureTypesMultipleWarnings(
        "function extend(x, y) {}" +
        "/** @constructor */ function Foo() {}" +
        "extend(Foo, /** @lends {!Foo} */ ({bar: 1}));",
        Lists.newArrayList(
            "Bad type annotation. expected closing }",
            "Bad type annotation. missing object name in @lends tag"));
  }

  public void testLends10() throws Exception {
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

  public void testLends11() throws Exception {
    reportMissingOverrides = CheckLevel.OFF;
    testTypes(
        "function defineClass(x, y) { return function() {}; } " +
        "/** @constructor */" +
        "var Foo = function() {};" +
        "/** @return {*} */ Foo.prototype.bar = function() { return 3; };" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */\n" +
        "var SubFoo = defineClass(Foo, " +
        "    /** @lends {SubFoo.prototype} */ ({\n" +
        "      /** @return {number} */ bar: function() { return 3; }}));" +
        "/** @return {string} */ function f() { return (new SubFoo()).bar(); }",
        "inconsistent return type\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testDeclaredNativeTypeEquality() throws Exception {
    Node n = parseAndTypeCheck("/** @constructor */ function Object() {};");
    assertTypeEquals(registry.getNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE),
                 n.getFirstChild().getJSType());
  }

  public void testUndefinedVar() throws Exception {
    Node n = parseAndTypeCheck("var undefined;");
    assertTypeEquals(registry.getNativeType(JSTypeNative.VOID_TYPE),
                 n.getFirstChild().getFirstChild().getJSType());
  }

  public void testFlowScopeBug1() throws Exception {
    Node n = parseAndTypeCheck("/** @param {number} a \n"
        + "* @param {number} b */\n"
        + "function f(a, b) {\n"
        + "/** @type number */"
        + "var i = 0;"
        + "for (; (i + a) < b; ++i) {}}");

    // check the type of the add node for i + f
    assertTypeEquals(registry.getNativeType(JSTypeNative.NUMBER_TYPE),
        n.getFirstChild().getLastChild().getLastChild().getFirstChild()
        .getNext().getFirstChild().getJSType());
  }

  public void testFlowScopeBug2() throws Exception {
    Node n = parseAndTypeCheck("/** @constructor */ function Foo() {};\n"
        + "Foo.prototype.hi = false;"
        + "function foo(a, b) {\n"
        + "  /** @type Array */"
        + "  var arr;"
        + "  /** @type number */"
        + "  var iter;"
        + "  for (iter = 0; iter < arr.length; ++ iter) {"
        + "    /** @type Foo */"
        + "    var afoo = arr[iter];"
        + "    afoo;"
        + "  }"
        + "}");

    // check the type of afoo when referenced
    assertTypeEquals(registry.createNullableType(registry.getType("Foo")),
        n.getLastChild().getLastChild().getLastChild().getLastChild()
        .getLastChild().getLastChild().getJSType());
  }

  public void testAddSingletonGetter() {
    Node n = parseAndTypeCheck(
        "/** @constructor */ function Foo() {};\n" +
        "goog.addSingletonGetter(Foo);");
    ObjectType o = (ObjectType) n.getFirstChild().getJSType();
    assertEquals("function (): Foo",
        o.getPropertyType("getInstance").toString());
    assertEquals("Foo", o.getPropertyType("instance_").toString());
  }

  public void testTypeCheckStandaloneAST() throws Exception {
    Node n = compiler.parseTestCode("function Foo() { }");
    typeCheck(n);
    MemoizedScopeCreator scopeCreator = new MemoizedScopeCreator(
        new TypedScopeCreator(compiler));
    TypedScope topScope = scopeCreator.createScope(n, null);

    Node second = compiler.parseTestCode("new Foo");

    Node externs = new Node(Token.BLOCK);
    Node externAndJsRoot = new Node(Token.BLOCK, externs, second);
    externAndJsRoot.setIsSyntheticBlock(true);

    new TypeCheck(
        compiler,
        new SemanticReverseAbstractInterpreter(registry),
        registry, topScope, scopeCreator, CheckLevel.WARNING)
        .process(null, second);

    assertEquals(1, compiler.getWarningCount());
    assertEquals("cannot instantiate non-constructor",
        compiler.getWarnings()[0].description);
  }

  public void testUpdateParameterTypeOnClosure() throws Exception {
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

  public void testTemplatedThisType1() throws Exception {
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

  public void testTemplatedThisType2() throws Exception {
    testTypes(
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

  public void testTemplateType1() throws Exception {
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

  public void testTemplateType2() throws Exception {
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

  public void testTemplateType3() throws Exception {
    testTypes(
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

  public void testTemplateType4() throws Exception {
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

  public void testTemplateType5() throws Exception {
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

  public void testTemplateType6() throws Exception {
    testTypes(
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

  public void testTemplateType7() throws Exception {
    // TODO(johnlenz): As the @this type for Array.prototype.push includes
    // "{length:number}" (and this includes "Array<number>") we don't
    // get a type warning here. Consider special-casing array methods.
    testTypes(
        "/** @type {!Array<string>} */\n" +
        "var query = [];\n" +
        "query.push(1);\n");
  }

  public void testTemplateType8() throws Exception {
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

  public void testTemplateType9() throws Exception {
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

  public void testTemplateType10() throws Exception {
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

  public void testTemplateType11() throws Exception {
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

  public void testTemplateType12() throws Exception {
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

  public void testTemplateType13() throws Exception {
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

  public void testTemplateType14() throws Exception {
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

  public void testTemplateType15() throws Exception {
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

  public void testTemplateType16() throws Exception {
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

  public void testTemplateType17() throws Exception {
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

  public void testTemplateType18() throws Exception {
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


  public void testTemplateType19() throws Exception {
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

  public void testTemplateType20() throws Exception {
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

  public void testTemplateType21() throws Exception {
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

  public void testTemplateType22() throws Exception {
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

  public void testTemplateType23() throws Exception {
    // "this" types is inferred when the parameters are declared.
    testTypes(
        "/** @interface @template T */ function A() {}\n" +
        "/** @constructor @implements {A<Foo>} */\n" +
        "function Foo() {}\n" +
        "/** @type {!Foo} */\n" +
        "var x = new Foo();\n");
  }

  public void testTemplateTypeWithUnresolvedType() throws Exception {
    testClosureTypes(
        "var goog = {};\n" +
        "goog.addDependency = function(a,b,c){};\n" +
        "goog.addDependency('a.js', ['Color'], []);\n" +

        "/** @interface @template T */ function C() {}\n" +
        "/** @return {!Color} */ C.prototype.method;\n" +

        "/** @constructor @implements {C} */ function D() {}\n" +
        "/** @override */ D.prototype.method = function() {};", null);  // no warning expected.
  }

  public void testTemplateTypeWithTypeDef1a() throws Exception {
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
        "/** @type null */ var z1 = y;\n" +
        "",
        "initializing variable\n" +
        "found   : (Generic<Foo>|null)\n" +
        "required: null");
  }

  public void testTemplateTypeWithTypeDef1b() throws Exception {
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
        "/** @type null */ var z1 = x;\n" +
        "",
        "initializing variable\n" +
        "found   : (Generic<Foo>|null)\n" +
        "required: null");
  }


  public void testTemplateTypeWithTypeDef2a() throws Exception {
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

  public void testTemplateTypeWithTypeDef2b() throws Exception {
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

  public void testTemplateTypeWithTypeDef2c() throws Exception {
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

  public void testTemplateTypeWithTypeDef2d() throws Exception {
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

  public void testTemplatedFunctionInUnion1() throws Exception {
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

  public void testTemplateTypeRecursion1() throws Exception {
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

  public void testTemplateTypeRecursion2() throws Exception {
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

  public void testTemplateTypeRecursion3() throws Exception {
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

  public void disable_testBadTemplateType4() throws Exception {
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

  public void disable_testBadTemplateType5() throws Exception {
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

  public void disable_testFunctionLiteralUndefinedThisArgument()
      throws Exception {
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

  public void testFunctionLiteralDefinedThisArgument() throws Exception {
    testTypes(""
        + "/**\n"
        + " * @param {function(this:T, ...)?} fn\n"
        + " * @param {?T} opt_obj\n"
        + " * @template T\n"
        + " */\n"
        + "function baz(fn, opt_obj) {}\n"
        + "baz(function() { this; }, {});");
  }

  public void testFunctionLiteralDefinedThisArgument2() throws Exception {
    testTypes(""
        + "/** @param {string} x */ function f(x) {}"
        + "/**\n"
        + " * @param {?function(this:T, ...)} fn\n"
        + " * @param {T=} opt_obj\n"
        + " * @template T\n"
        + " */\n"
        + "function baz(fn, opt_obj) {}\n"
        + "function g() { baz(function() { f(this.length); }, []); }",
        "actual parameter 1 of f does not match formal parameter\n"
        + "found   : number\n"
        + "required: string");
  }

  public void testFunctionLiteralUnreadNullThisArgument() throws Exception {
    testTypes(""
        + "/**\n"
        + " * @param {function(this:T, ...)?} fn\n"
        + " * @param {?T} opt_obj\n"
        + " * @template T\n"
        + " */\n"
        + "function baz(fn, opt_obj) {}\n"
        + "baz(function() {}, null);");
  }

  public void testUnionTemplateThisType() throws Exception {
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

  public void testActiveXObject() throws Exception {
    testTypes(
        "/** @type {Object} */ var x = new ActiveXObject();" +
        "/** @type { {impossibleProperty} } */ var y = new ActiveXObject();");
  }

  public void testRecordType1() throws Exception {
    testTypes(
        "/** @param {{prop: number}} x */" +
        "function f(x) {}" +
        "f({});",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : {prop: (number|undefined)}\n" +
        "required: {prop: number}");
  }

  public void testRecordType2() throws Exception {
    testTypes(
        "/** @param {{prop: (number|undefined)}} x */" +
        "function f(x) {}" +
        "f({});");
  }

  public void testRecordType3() throws Exception {
    testTypes(
        "/** @param {{prop: number}} x */" +
        "function f(x) {}" +
        "f({prop: 'x'});",
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : {prop: (number|string)}\n" +
        "required: {prop: number}");
  }

  public void testRecordType4() throws Exception {
    // Notice that we do not do flow-based inference on the object type:
    // We don't try to prove that x.prop may not be string until x
    // gets passed to g.
    testClosureTypesMultipleWarnings(
        "/** @param {{prop: (number|undefined)}} x */" +
        "function f(x) {}" +
        "/** @param {{prop: (string|undefined)}} x */" +
        "function g(x) {}" +
        "var x = {}; f(x); g(x);",
        Lists.newArrayList(
            "actual parameter 1 of f does not match formal parameter\n" +
            "found   : {prop: (number|string|undefined)}\n" +
            "required: {prop: (number|undefined)}",
            "actual parameter 1 of g does not match formal parameter\n" +
            "found   : {prop: (number|string|undefined)}\n" +
            "required: {prop: (string|undefined)}"));
  }

  public void testRecordType5() throws Exception {
    testTypes(
        "/** @param {{prop: (number|undefined)}} x */" +
        "function f(x) {}" +
        "/** @param {{otherProp: (string|undefined)}} x */" +
        "function g(x) {}" +
        "var x = {}; f(x); g(x);");
  }

  public void testRecordType6() throws Exception {
    testTypes(
        "/** @return {{prop: (number|undefined)}} x */" +
        "function f() { return {}; }");
  }

  public void testRecordType7() throws Exception {
    testTypes(
        "/** @return {{prop: (number|undefined)}} x */" +
        "function f() { var x = {}; g(x); return x; }" +
        "/** @param {number} x */" +
        "function g(x) {}",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : {prop: (number|undefined)}\n" +
        "required: number");
  }

  public void testRecordType8() throws Exception {
    testTypes(
        "/** @return {{prop: (number|string)}} x */" +
        "function f() { var x = {prop: 3}; g(x.prop); return x; }" +
        "/** @param {string} x */" +
        "function g(x) {}",
        "actual parameter 1 of g does not match formal parameter\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testDuplicateRecordFields1() throws Exception {
    testTypes("/**"
         + "* @param {{x:string, x:number}} a"
         + "*/"
         + "function f(a) {};",
         "Bad type annotation. Duplicate record field x");
  }

  public void testDuplicateRecordFields2() throws Exception {
    testTypes("/**"
         + "* @param {{name:string,number:x,number:y}} a"
         + " */"
         + "function f(a) {};",
         new String[] {"Bad type annotation. Unknown type x",
           "Bad type annotation. Duplicate record field number"});
  }

  public void testMultipleExtendsInterface1() throws Exception {
    testTypes("/** @interface */ function base1() {}\n"
        + "/** @interface */ function base2() {}\n"
        + "/** @interface\n"
        + "* @extends {base1}\n"
        + "* @extends {base2}\n"
        + "*/\n"
        + "function derived() {}");
  }

  public void testMultipleExtendsInterface2() throws Exception {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @interface */function Int1() {};" +
        "/** @desc description */Int0.prototype.foo = function() {};" +
        "/** @interface \n @extends {Int0} \n @extends {Int1} */" +
        "function Int2() {};" +
        "/** @constructor\n @implements {Int2} */function Foo() {};",
        "property foo on interface Int0 is not implemented by type Foo");
  }

  public void testMultipleExtendsInterface3() throws Exception {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @interface */function Int1() {};" +
        "/** @desc description */Int1.prototype.foo = function() {};" +
        "/** @interface \n @extends {Int0} \n @extends {Int1} */" +
        "function Int2() {};" +
        "/** @constructor\n @implements {Int2} */function Foo() {};",
        "property foo on interface Int1 is not implemented by type Foo");
  }

  public void testMultipleExtendsInterface4() throws Exception {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @interface */function Int1() {};" +
        "/** @interface \n @extends {Int0} \n @extends {Int1} \n" +
        " @extends {number} */" +
        "function Int2() {};" +
        "/** @constructor\n @implements {Int2} */function Foo() {};",
        "Int2 @extends non-object type number");
  }

  public void testMultipleExtendsInterface5() throws Exception {
    testTypes(
        "/** @interface */function Int0() {};" +
        "/** @constructor */function Int1() {};" +
        "/** @desc description @ return {string} x */" +
        "/** @interface \n @extends {Int0} \n @extends {Int1} */" +
        "function Int2() {};",
        "Int2 cannot extend this type; interfaces can only extend interfaces");
  }

  public void testMultipleExtendsInterface6() throws Exception {
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
        "original: function (this:Super2, number): undefined\n" +
        "override: function (this:Sub, string): undefined");
  }

  public void testMultipleExtendsInterfaceAssignment() throws Exception {
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

  public void testMultipleExtendsInterfaceParamPass() throws Exception {
    testTypes("/** @interface */var I1 = function() {};\n" +
        "/** @interface */ var I2 = function() {}\n" +
        "/** @interface\n@extends {I1}\n@extends {I2}*/" +
        "var I3 = function() {};\n" +
        "/** @constructor\n@implements {I3}*/var T = function() {};\n" +
        "var t = new T();\n" +
        "/** @param x I1 \n@param y I2\n@param z I3*/function foo(x,y,z){};\n" +
        "foo(t,t,t)\n");
  }

  public void testBadMultipleExtendsClass() throws Exception {
    testTypes("/** @constructor */ function base1() {}\n"
        + "/** @constructor */ function base2() {}\n"
        + "/** @constructor\n"
        + "* @extends {base1}\n"
        + "* @extends {base2}\n"
        + "*/\n"
        + "function derived() {}",
        "Bad type annotation. type annotation incompatible "
        + "with other annotations");
  }

  public void testInterfaceExtendsResolution() throws Exception {
    testTypes("/** @interface \n @extends {A} */ function B() {};\n" +
        "/** @constructor \n @implements {B} */ function C() {};\n" +
        "/** @interface */ function A() {};");
  }

  public void testPropertyCanBeDefinedInObject() throws Exception {
    testTypes("/** @interface */ function I() {};" +
        "I.prototype.bar = function() {};" +
        "/** @type {Object} */ var foo;" +
        "foo.bar();");
  }

  private void checkObjectType(ObjectType objectType, String propertyName,
        JSType expectedType) {
    assertTrue("Expected " + objectType.getReferenceName() +
        " to have property " +
        propertyName, objectType.hasProperty(propertyName));
    assertTypeEquals("Expected " + objectType.getReferenceName() +
        "'s property " +
        propertyName + " to have type " + expectedType,
        expectedType, objectType.getPropertyType(propertyName));
  }

  public void testExtendedInterfacePropertiesCompatibility1() throws Exception {
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

  public void testExtendedInterfacePropertiesCompatibility2() throws Exception {
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

  public void testExtendedInterfacePropertiesCompatibility3() throws Exception {
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

  public void testExtendedInterfacePropertiesCompatibility4() throws Exception {
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

  public void testExtendedInterfacePropertiesCompatibility5() throws Exception {
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

  public void testExtendedInterfacePropertiesCompatibility6() throws Exception {
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

  public void testExtendedInterfacePropertiesCompatibility7() throws Exception {
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

  public void testExtendedInterfacePropertiesCompatibility8() throws Exception {
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

  public void testExtendedInterfacePropertiesCompatibility9() throws Exception {
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

  public void testExtendedInterfacePropertiesCompatibilityNoError() throws Exception {
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

  public void testGenerics1() throws Exception {
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

    testTypes(
        fnDecl +
        "/** @type {string} */" +
        "var out;" +
        "var result = f(0, function(x){ out = x; return x; });",
        "assignment\n" +
        "found   : number\n" +
        "required: string");

    testTypes(
        fnDecl +
        "var out;" +
        "/** @type {string} */" +
        "var result = f(0, function(x){ out = x; return x; });",
        "assignment\n" +
        "found   : number\n" +
        "required: string");
  }

  public void testFilter0()
      throws Exception {
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

  public void testFilter1()
      throws Exception {
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

  public void testFilter2()
      throws Exception {
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

  public void testFilter3()
      throws Exception {
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

  public void testBackwardsInferenceGoogArrayFilter1()
      throws Exception {
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

  public void testBackwardsInferenceGoogArrayFilter2() throws Exception {
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

  public void testBackwardsInferenceGoogArrayFilter3() throws Exception {
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

  public void testBackwardsInferenceGoogArrayFilter4() throws Exception {
    testClosureTypes(
        CLOSURE_DEFS +
        "/** @type {string} */" +
        "var out;" +
        "/** @type {Array<string>} */ var arr;\n" +
        "var out4 = goog.array.filter(" +
        "   arr," +
        "   function(item,index,srcArr) {out = srcArr;});",
        "assignment\n" +
        "found   : (null|{length: number})\n" +
        "required: string");
  }

  public void testCatchExpression1() throws Exception {
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

  public void testCatchExpression2() throws Exception {
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

  public void testTemplatized1() throws Exception {
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

  public void testTemplatized2() throws Exception {
    testTypes(
        "/** @type {!Array<string>} */" +
        "var arr1 = /** @type {!Array<number>} */([]);\n",
        "initializing variable\n" +
        "found   : Array<number>\n" +
        "required: Array<string>");
  }

  public void testTemplatized3() throws Exception {
    testTypes(
        "/** @type {Array<string>} */" +
        "var arr1 = /** @type {!Array<number>} */([]);\n",
        "initializing variable\n" +
        "found   : Array<number>\n" +
        "required: (Array<string>|null)");
  }

  public void testTemplatized4() throws Exception {
    testTypes(
        "/** @type {Array<string>} */" +
        "var arr1 = [];\n" +
        "/** @type {Array<number>} */" +
        "var arr2 = arr1;\n",
        "initializing variable\n" +
        "found   : (Array<string>|null)\n" +
        "required: (Array<number>|null)");
  }

  public void testTemplatized5() throws Exception {
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

  public void testTemplatized6() throws Exception {
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

  public void testTemplatized7() throws Exception {
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

  public void disable_testTemplatized8() throws Exception {
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

  public void testTemplatized9() throws Exception {
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

  public void testTemplatized10() throws Exception {
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

  public void testTemplatized11() throws Exception {
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
        "C.prototype.method = function (a) {};\n" +
        "\n" +
        // method returns "?"
        "/** @type {void} */ var x = new C().method(1);");
  }

  public void testIssue1058() throws Exception {
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

  public void testDeterminacyIssue() throws Exception {
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


  public void testUnknownTypeReport() throws Exception {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.REPORT_UNKNOWN_TYPES,
        CheckLevel.WARNING);
    testTypes("function id(x) { return x; }",
        "could not determine the type of this expression");
  }

  public void testUnknownForIn() throws Exception  {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.REPORT_UNKNOWN_TYPES,
        CheckLevel.WARNING);
    testTypes("var x = {'a':1}; var y; \n for(\ny\n in x) {}");
  }

  public void testUnknownTypeDisabledByDefault() throws Exception {
    testTypes("function id(x) { return x; }");
  }

  public void testTemplatizedTypeSubtypes2() throws Exception {
    JSType arrayOfNumber = createTemplatizedType(
        ARRAY_TYPE, NUMBER_TYPE);
    JSType arrayOfString = createTemplatizedType(
        ARRAY_TYPE, STRING_TYPE);
    assertFalse(arrayOfString.isSubtype(createUnionType(arrayOfNumber, NULL_VOID)));

  }

  public void testNonexistentPropertyAccessOnStruct() throws Exception {
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

  public void testNonexistentPropertyAccessOnStructOrObject() throws Exception {
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

  public void testNonexistentPropertyAccessOnExternStruct() throws Exception {
    testTypes(
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

  public void testNonexistentPropertyAccessStructSubtype() throws Exception {
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

  public void testNonexistentPropertyAccessStructSubtype2() throws Exception {
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

  public void testIssue1024() throws Exception {
     testTypes(
        "/** @param {Object} a */\n" +
        "function f(a) {\n" +
        "  a.prototype = '__proto'\n" +
        "}\n" +
        "/** @param {Object} b\n" +
        " *  @return {!Object}\n" +
        " */\n" +
        "function g(b) {\n" +
        "  return b.prototype\n" +
        "}\n");
     /* TODO(blickly): Make this warning go away.
      * This is old behavior, but it doesn't make sense to warn about since
      * both assignments are inferred.
      */
     testTypes(
        "/** @param {Object} a */\n" +
        "function f(a) {\n" +
        "  a.prototype = {foo:3};\n" +
        "}\n" +
        "/** @param {Object} b\n" +
        " */\n" +
        "function g(b) {\n" +
        "  b.prototype = function(){};\n" +
        "}\n",
        "assignment to property prototype of Object\n" +
        "found   : {foo: number}\n" +
        "required: function (): undefined");
  }

  public void testBug12722936() throws Exception {
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

  public void testModuleReferenceNotAllowed() throws Exception {
    testTypes(
        "/** @param {./Foo} z */ function f(z) {}",
        "Bad type annotation. Unknown type ./Foo");
  }

  private void testTypes(String js) throws Exception {
    testTypes(js, (String) null);
  }

  private void testTypes(String js, String description) throws Exception {
    testTypes(js, description, false);
  }

  private void testTypes(String js, DiagnosticType type) throws Exception {
    testTypes(js, type, false);
  }

  private void testClosureTypes(String js, String description)
      throws Exception {
    testClosureTypesMultipleWarnings(js,
        description == null ? null : Lists.newArrayList(description));
  }

  private void testClosureTypesMultipleWarnings(
      String js, List<String> descriptions) throws Exception {
    compiler.initOptions(compiler.getOptions());
    Node n = compiler.parseTestCode(js);
    Node externs = new Node(Token.BLOCK);
    Node externAndJsRoot = new Node(Token.BLOCK, externs, n);
    externAndJsRoot.setIsSyntheticBlock(true);

    assertEquals("parsing error: " +
        Joiner.on(", ").join(compiler.getErrors()),
        0, compiler.getErrorCount());

    // For processing goog.addDependency for forward typedefs.
    new ProcessClosurePrimitives(compiler, null, CheckLevel.ERROR, false)
        .process(null, n);

    new TypeCheck(compiler,
        new ClosureReverseAbstractInterpreter(registry).append(
                new SemanticReverseAbstractInterpreter(registry))
            .getFirst(),
        registry)
        .processForTesting(null, n);

    assertEquals(
        "unexpected error(s) : " +
        Joiner.on(", ").join(compiler.getErrors()),
        0, compiler.getErrorCount());

    if (descriptions == null) {
      assertEquals(
          "unexpected warning(s) : " +
          Joiner.on(", ").join(compiler.getWarnings()),
          0, compiler.getWarningCount());
    } else {
      assertEquals(
          "unexpected warning(s) : " +
          Joiner.on(", ").join(compiler.getWarnings()),
          descriptions.size(), compiler.getWarningCount());
      Set<String> actualWarningDescriptions = Sets.newHashSet();
      for (int i = 0; i < descriptions.size(); i++) {
        actualWarningDescriptions.add(compiler.getWarnings()[i].description);
      }
      assertEquals(
          Sets.newHashSet(descriptions), actualWarningDescriptions);
    }
  }

  void testTypes(String js, String description, boolean isError)
      throws Exception {
    testTypes(DEFAULT_EXTERNS, js, description, isError);
  }

  void testTypes(String externs, String js, String description, boolean isError)
      throws Exception {
    parseAndTypeCheck(externs, js);

    JSError[] errors = compiler.getErrors();
    if (description != null && isError) {
      assertTrue("expected an error", errors.length > 0);
      assertEquals(description, errors[0].description);
      errors = Arrays.asList(errors).subList(1, errors.length).toArray(
          new JSError[errors.length - 1]);
    }
    if (errors.length > 0) {
      fail("unexpected error(s):\n" + Joiner.on("\n").join(errors));
    }

    JSError[] warnings = compiler.getWarnings();
    if (description != null && !isError) {
      assertTrue("expected a warning", warnings.length > 0);
      assertEquals(description, warnings[0].description);
      warnings = Arrays.asList(warnings).subList(1, warnings.length).toArray(
          new JSError[warnings.length - 1]);
    }
    if (warnings.length > 0) {
      fail("unexpected warnings(s):\n" + Joiner.on("\n").join(warnings));
    }
  }

  void testTypes(String js, DiagnosticType diagnosticType, boolean isError)
      throws Exception {
    testTypes(DEFAULT_EXTERNS, js, diagnosticType, isError);
  }

  void testTypesWithExterns(String externs, String js) throws Exception {
    testTypes(externs, js, (String) null, false);
  }

  void testTypes(
      String externs, String js, DiagnosticType diagnosticType, boolean isError)
      throws Exception {
    parseAndTypeCheck(externs, js);

    JSError[] errors = compiler.getErrors();
    if (diagnosticType != null && isError) {
      assertTrue("expected an error", errors.length > 0);
      assertEquals(diagnosticType, errors[0].getType());
      errors = Arrays.asList(errors).subList(1, errors.length).toArray(
          new JSError[errors.length - 1]);
    }
    if (errors.length > 0) {
      fail("unexpected error(s):\n" + Joiner.on("\n").join(errors));
    }

    JSError[] warnings = compiler.getWarnings();
    if (diagnosticType != null && !isError) {
      assertTrue("expected a warning", warnings.length > 0);
      assertEquals(diagnosticType, warnings[0].getType());
      warnings = Arrays.asList(warnings).subList(1, warnings.length).toArray(
          new JSError[warnings.length - 1]);
    }
    if (warnings.length > 0) {
      fail("unexpected warnings(s):\n" + Joiner.on("\n").join(warnings));
    }
  }

  /**
   * Parses and type checks the JavaScript code.
   */
  private Node parseAndTypeCheck(String js) {
    return parseAndTypeCheck(DEFAULT_EXTERNS, js);
  }

  private Node parseAndTypeCheck(String externs, String js) {
    return parseAndTypeCheckWithScope(externs, js).root;
  }

  /**
   * Parses and type checks the JavaScript code and returns the TypedScope used
   * whilst type checking.
   */
  private TypeCheckResult parseAndTypeCheckWithScope(String js) {
    return parseAndTypeCheckWithScope(DEFAULT_EXTERNS, js);
  }

  private TypeCheckResult parseAndTypeCheckWithScope(
      String externs, String js) {
    compiler.init(
        Lists.newArrayList(SourceFile.fromCode("[externs]", externs)),
        Lists.newArrayList(SourceFile.fromCode("[testcode]", js)),
        compiler.getOptions());

    Node n = compiler.getInput(new InputId("[testcode]")).getAstRoot(compiler);
    Node externsNode = compiler.getInput(new InputId("[externs]"))
        .getAstRoot(compiler);
    Node externAndJsRoot = new Node(Token.BLOCK, externsNode, n);
    externAndJsRoot.setIsSyntheticBlock(true);

    assertEquals("parsing error: " +
        Joiner.on(", ").join(compiler.getErrors()),
        0, compiler.getErrorCount());

    TypedScope s = makeTypeCheck().processForTesting(externsNode, n);
    return new TypeCheckResult(n, s);
  }

  private Node typeCheck(Node n) {
    Node externsNode = new Node(Token.BLOCK);
    Node externAndJsRoot = new Node(Token.BLOCK, externsNode, n);
    externAndJsRoot.setIsSyntheticBlock(true);

    makeTypeCheck().processForTesting(null, n);
    return n;
  }

  private TypeCheck makeTypeCheck() {
    return new TypeCheck(
        compiler,
        new SemanticReverseAbstractInterpreter(registry),
        registry,
        reportMissingOverrides);
  }

  void testTypes(String js, String[] warnings) throws Exception {
    Node n = compiler.parseTestCode(js);
    assertEquals(0, compiler.getErrorCount());
    Node externsNode = new Node(Token.BLOCK);
    // create a parent node for the extern and source blocks
    new Node(Token.BLOCK, externsNode, n);

    makeTypeCheck().processForTesting(null, n);
    assertEquals(0, compiler.getErrorCount());
    if (warnings != null) {
      assertEquals(warnings.length, compiler.getWarningCount());
      JSError[] messages = compiler.getWarnings();
      for (int i = 0; i < warnings.length && i < compiler.getWarningCount();
           i++) {
        assertEquals(warnings[i], messages[i].description);
      }
    } else {
      assertEquals(0, compiler.getWarningCount());
    }
  }

  String suppressMissingProperty(String ... props) {
    String result = "function dummy(x) { ";
    for (String prop : props) {
      result += "x." + prop + " = 3;";
    }
    return result + "}";
  }

  private static class TypeCheckResult {
    private final Node root;
    private final TypedScope scope;

    private TypeCheckResult(Node root, TypedScope scope) {
      this.root = root;
      this.scope = scope;
    }
  }
}
