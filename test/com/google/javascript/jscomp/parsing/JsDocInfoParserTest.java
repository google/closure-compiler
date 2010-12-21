/*
 * Copyright 2007 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing;

import com.google.common.collect.Sets;
import com.google.javascript.jscomp.mozilla.rhino.CompilerEnvirons;
import com.google.javascript.jscomp.mozilla.rhino.Parser;
import com.google.javascript.jscomp.mozilla.rhino.Token.CommentType;
import com.google.javascript.jscomp.mozilla.rhino.ast.AstRoot;
import com.google.javascript.jscomp.mozilla.rhino.ast.Comment;
import com.google.javascript.jscomp.testing.TestErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class JsDocInfoParserTest extends BaseJSTypeTestCase {

  private Set<String> extraAnnotations;
  private Set<String> extraSuppressions;
  private Node.FileLevelJsDocBuilder fileLevelJsDocBuilder = null;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    extraAnnotations =
        Sets.newHashSet(
            ParserRunner.createConfig(true, false).annotationNames.keySet());
    extraSuppressions =
        Sets.newHashSet(
            ParserRunner.createConfig(true, false).suppressionNames);

    extraSuppressions.add("x");
    extraSuppressions.add("y");
    extraSuppressions.add("z");
  }

  public void testParseTypeViaStatic1() throws Exception {
    Node typeNode = parseType("null");
    assertTypeEquals(NULL_TYPE, typeNode);
  }

  public void testParseTypeViaStatic2() throws Exception {
    Node typeNode = parseType("string");
    assertTypeEquals(STRING_TYPE, typeNode);
  }

  public void testParseTypeViaStatic3() throws Exception {
    Node typeNode = parseType("!Date");
    assertTypeEquals(DATE_TYPE, typeNode);
  }

  public void testParseTypeViaStatic4() throws Exception {
    Node typeNode = parseType("boolean|string");
    assertTypeEquals(createUnionType(BOOLEAN_TYPE, STRING_TYPE), typeNode);
  }

  public void testParseInvalidTypeViaStatic() throws Exception {
    Node typeNode = parseType("sometype.<anothertype");
    assertNull(typeNode);
  }

  public void testParseInvalidTypeViaStatic2() throws Exception {
    Node typeNode = parseType("");
    assertNull(typeNode);
  }

  public void testParseNamedType1() throws Exception {
    assertNull(parse("@type null", "Unexpected end of file"));
  }

  public void testParseNamedType2() throws Exception {
    JSDocInfo info = parse("@type null*/");
    assertTypeEquals(NULL_TYPE, info.getType());
  }

  public void testParseNamedType3() throws Exception {
    JSDocInfo info = parse("@type {string}*/");
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  public void testParseNamedType4() throws Exception {
    // Multi-line @type.
    JSDocInfo info = parse("@type \n {string}*/");
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  public void testTypedefType1() throws Exception {
    JSDocInfo info = parse("@typedef string */");
    assertTrue(info.hasTypedefType());
    assertTypeEquals(STRING_TYPE, info.getTypedefType());
  }

  public void testTypedefType2() throws Exception {
    JSDocInfo info = parse("@typedef \n {string}*/");
    assertTrue(info.hasTypedefType());
    assertTypeEquals(STRING_TYPE, info.getTypedefType());
  }

  public void testTypedefType3() throws Exception {
    JSDocInfo info = parse("@typedef \n {(string|number)}*/");
    assertTrue(info.hasTypedefType());
    assertTypeEquals(
        createUnionType(NUMBER_TYPE, STRING_TYPE),
        info.getTypedefType());
  }

  public void testParseStringType1() throws Exception {
    assertTypeEquals(STRING_TYPE, parse("@type {string}*/").getType());
  }

  public void testParseStringType2() throws Exception {
    assertTypeEquals(STRING_OBJECT_TYPE, parse("@type {!String}*/").getType());
  }

  public void testParseBooleanType1() throws Exception {
    assertTypeEquals(BOOLEAN_TYPE, parse("@type {boolean}*/").getType());
  }

  public void testParseBooleanType2() throws Exception {
    assertTypeEquals(BOOLEAN_OBJECT_TYPE, parse("@type {!Boolean}*/").getType());
  }

  public void testParseNumberType1() throws Exception {
    assertTypeEquals(NUMBER_TYPE, parse("@type {number}*/").getType());
  }

  public void testParseNumberType2() throws Exception {
    assertTypeEquals(NUMBER_OBJECT_TYPE, parse("@type {!Number}*/").getType());
  }

  public void testParseNullType1() throws Exception {
    assertTypeEquals(NULL_TYPE, parse("@type {null}*/").getType());
  }

  public void testParseNullType2() throws Exception {
    assertTypeEquals(NULL_TYPE, parse("@type {Null}*/").getType());
  }

  public void testParseAllType1() throws Exception {
    testParseType("*");
  }

  public void testParseAllType2() throws Exception {
    testParseType("*?", "*");
  }

  public void testParseObjectType() throws Exception {
    assertTypeEquals(OBJECT_TYPE, parse("@type {!Object}*/").getType());
  }

  public void testParseDateType() throws Exception {
    assertTypeEquals(DATE_TYPE, parse("@type {!Date}*/").getType());
  }

  public void testParseFunctionType() throws Exception {
    assertTypeEquals(
        createNullableType(U2U_CONSTRUCTOR_TYPE),
        parse("@type {Function}*/").getType());
  }

  public void testParseRegExpType() throws Exception {
    assertTypeEquals(REGEXP_TYPE, parse("@type {!RegExp}*/").getType());
  }

  public void testParseErrorTypes() throws Exception {
    assertTypeEquals(ERROR_TYPE, parse("@type {!Error}*/").getType());
    assertTypeEquals(URI_ERROR_TYPE, parse("@type {!URIError}*/").getType());
    assertTypeEquals(EVAL_ERROR_TYPE, parse("@type {!EvalError}*/").getType());
    assertTypeEquals(REFERENCE_ERROR_TYPE,
        parse("@type {!ReferenceError}*/").getType());
    assertTypeEquals(TYPE_ERROR_TYPE, parse("@type {!TypeError}*/").getType());
    assertTypeEquals(RANGE_ERROR_TYPE, parse("@type {!RangeError}*/").getType());
    assertTypeEquals(SYNTAX_ERROR_TYPE, parse("@type {!SyntaxError}*/").getType());
  }

  public void testParseUndefinedType1() throws Exception {
    assertTypeEquals(VOID_TYPE, parse("@type {undefined}*/").getType());
  }

  public void testParseUndefinedType2() throws Exception {
    assertTypeEquals(VOID_TYPE, parse("@type {Undefined}*/").getType());
  }

  public void testParseUndefinedType3() throws Exception {
    assertTypeEquals(VOID_TYPE, parse("@type {void}*/").getType());
  }

  public void testParseParametrizedType1() throws Exception {
    JSDocInfo info = parse("@type !Array.<number> */");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseParametrizedType2() throws Exception {
    JSDocInfo info = parse("@type {!Array.<number>}*/");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseParametrizedType3() throws Exception {
    JSDocInfo info = parse("@type !Array.<(number,null)>*/");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseParametrizedType4() throws Exception {
    JSDocInfo info = parse("@type {!Array.<(number|null)>}*/");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseParametrizedType5() throws Exception {
    JSDocInfo info = parse("@type {!Array.<Array.<(number|null)>>}*/");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseParametrizedType6() throws Exception {
    JSDocInfo info = parse("@type {!Array.<!Array.<(number|null)>>}*/");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseParametrizedType7() throws Exception {
    JSDocInfo info = parse("@type {!Array.<function():Date>}*/");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseParametrizedType8() throws Exception {
    JSDocInfo info = parse("@type {!Array.<function():!Date>}*/");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseParametrizedType9() throws Exception {
    JSDocInfo info = parse("@type {!Array.<Date|number>}*/");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseParametrizedType10() throws Exception {
    JSDocInfo info = parse("@type {!Array.<Date|number|boolean>}*/");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseParamterizedType11() throws Exception {
    JSDocInfo info = parse("@type {!Object.<number>}*/");
    assertTypeEquals(OBJECT_TYPE, info.getType());
    assertParameterTypeEquals(NUMBER_TYPE, info.getType());
  }

  public void testParseParamterizedType12() throws Exception {
    JSDocInfo info = parse("@type {!Object.<string,number>}*/");
    assertTypeEquals(OBJECT_TYPE, info.getType());
    assertParameterTypeEquals(NUMBER_TYPE, info.getType());
    assertIndexTypeEquals(STRING_TYPE, info.getType());
  }

  public void testParseUnionType1() throws Exception {
    JSDocInfo info = parse("@type {(boolean,null)}*/");
    assertTypeEquals(createUnionType(BOOLEAN_TYPE, NULL_TYPE), info.getType());
  }

  public void testParseUnionType2() throws Exception {
    JSDocInfo info = parse("@type {boolean|null}*/");
    assertTypeEquals(createUnionType(BOOLEAN_TYPE, NULL_TYPE), info.getType());
  }

  public void testParseUnionType3() throws Exception {
    JSDocInfo info = parse("@type {boolean||null}*/");
    assertTypeEquals(createUnionType(BOOLEAN_TYPE, NULL_TYPE), info.getType());
  }

  public void testParseUnionType4() throws Exception {
    JSDocInfo info = parse("@type {(Array.<boolean>,null)}*/");
    assertTypeEquals(createUnionType(ARRAY_TYPE, NULL_TYPE), info.getType());
  }

  public void testParseUnionType5() throws Exception {
    JSDocInfo info = parse("@type {(null, Array.<boolean>)}*/");
    assertTypeEquals(createUnionType(ARRAY_TYPE, NULL_TYPE), info.getType());
  }

  public void testParseUnionType6() throws Exception {
    JSDocInfo info = parse("@type {Array.<boolean>|null}*/");
    assertTypeEquals(createUnionType(ARRAY_TYPE, NULL_TYPE), info.getType());
  }

  public void testParseUnionType7() throws Exception {
    JSDocInfo info = parse("@type {null|Array.<boolean>}*/");
    assertTypeEquals(createUnionType(ARRAY_TYPE, NULL_TYPE), info.getType());
  }

  public void testParseUnionType8() throws Exception {
    JSDocInfo info = parse("@type {null||Array.<boolean>}*/");
    assertTypeEquals(createUnionType(ARRAY_TYPE, NULL_TYPE), info.getType());
  }

  public void testParseUnionType9() throws Exception {
    JSDocInfo info = parse("@type {Array.<boolean>||null}*/");
    assertTypeEquals(createUnionType(ARRAY_TYPE, NULL_TYPE), info.getType());
  }

  public void testParseUnionType10() throws Exception {
    parse("@type {string|}*/", "type not recognized due to syntax error");
  }

  public void testParseUnionType11() throws Exception {
    parse("@type {(string,)}*/", "type not recognized due to syntax error");
  }

  public void testParseUnionType12() throws Exception {
    parse("@type {()}*/", "type not recognized due to syntax error");
  }

  public void testParseUnionType13() throws Exception {
    testParseType(
        "(function(this:Date),function(this:String):number)",
        "(function (this:Date): ?|function (this:String): number)");
  }

  public void testParseUnionType14() throws Exception {
    testParseType(
        "(function(...[function(number):boolean]):number)|" +
        "function(this:String, string):number",
        "(function (...[function (number): boolean]): number|" +
        "function (this:String, string): number)");
  }

  public void testParseUnionType15() throws Exception {
    testParseType("*|number", "*");
  }

  public void testParseUnionType16() throws Exception {
    testParseType("number|*", "*");
  }

  public void testParseUnionType17() throws Exception {
    testParseType("string|number|*", "*");
  }

  public void testParseUnionType18() throws Exception {
    testParseType("(string,*,number)", "*");
  }

  public void testParseUnionTypeError1() throws Exception {
    parse("@type {(string,|number)} */",
        "type not recognized due to syntax error");
  }

  public void testParseUnknownType1() throws Exception {
    testParseType("?");
  }

  public void testParseUnknownType2() throws Exception {
    testParseType("(?|number)", "?");
  }

  public void testParseUnknownType3() throws Exception {
    testParseType("(number|?)", "?");
  }

  public void testParseFunctionalType1() throws Exception {
    testParseType("function (): number");
  }

  public void testParseFunctionalType2() throws Exception {
    testParseType("function (number, string): boolean");
  }

  public void testParseFunctionalType3() throws Exception {
    testParseType(
        "function(this:Array)", "function (this:Array): ?");
  }

  public void testParseFunctionalType4() throws Exception {
    testParseType("function (...[number]): boolean");
  }

  public void testParseFunctionalType5() throws Exception {
    testParseType("function (number, ...[string]): boolean");
  }

  public void testParseFunctionalType6() throws Exception {
    testParseType(
        "function (this:Date, number): (boolean|number|string)");
  }

  public void testParseFunctionalType7() throws Exception {
    testParseType("function()", "function (): ?");
  }

  public void testParseFunctionalType8() throws Exception {
    testParseType(
        "function(this:Array,...[boolean])",
        "function (this:Array, ...[boolean]): ?");
  }

  public void testParseFunctionalType9() throws Exception {
    testParseType(
        "function(this:Array,!Date,...[boolean?])",
        "function (this:Array, Date, ...[(boolean|null)]): ?");
  }

  public void testParseFunctionalType10() throws Exception {
    testParseType(
        "function(...[Object?]):boolean?",
        "function (...[(Object|null)]): (boolean|null)");
  }

  public void testParseFunctionalType11() throws Exception {
    testParseType(
        "function(...[[number]]):[number?]",
        "function (...[Array]): Array");
  }

  public void testParseFunctionalType12() throws Exception {
    testParseType(
        "function(...)",
        "function (...[?]): ?");
  }

  public void testParseFunctionalType13() throws Exception {
    testParseType(
        "function(...): void",
        "function (...[?]): undefined");
  }

  public void testParseFunctionalType14() throws Exception {
    testParseType("function (*, string, number): boolean");
  }

  public void testParseFunctionalType15() throws Exception {
    testParseType("function (?, string): boolean");
  }

  public void testParseFunctionalType16() throws Exception {
    testParseType("function (string, ?): ?");
  }

  public void testParseFunctionalType17() throws Exception {
    testParseType("(function (?): ?|number)");
  }

  public void testParseFunctionalType18() throws Exception {
    testParseType("function (?): (?|number)", "function (?): ?");
  }

  public void testParseFunctionalType19() throws Exception {
    testParseType("function (new:Object)", "function (new:Object): ?");
  }

  public void testBug1419535() throws Exception {
    parse("@type {function(Object, string, *)?} */");
    parse("@type {function(Object, string, *)|null} */");
  }

  public void testParseFunctionalTypeError1() throws Exception {
    parse("@type {function number):string}*/", "missing opening (");
  }

  public void testParseFunctionalTypeError2() throws Exception {
    parse("@type {function( number}*/", "missing closing )");
  }

  public void testParseFunctionalTypeError3() throws Exception {
    parse("@type {function(...[number], string)}*/",
        "variable length argument must be last");
  }

  public void testParseFunctionalTypeError4() throws Exception {
    parse("@type {function(string, ...[number], boolean):string}*/",
        "variable length argument must be last");
  }

  public void testParseFunctionalTypeError5() throws Exception {
    parse("@type {function (thi:Array)}*/", "missing closing )");
  }

  public void testParseFunctionalTypeError6() throws Exception {
    resolve(parse("@type {function (this:number)}*/").getType(),
        "this type must be an object type");
  }

  public void testParseFunctionalTypeError7() throws Exception {
    parse("@type {function(...[number)}*/", "missing closing ]");
  }

  public void testParseFunctionalTypeError8() throws Exception {
    parse("@type {function(...number])}*/", "missing opening [");
  }

  public void testParseFunctionalTypeError9() throws Exception {
    parse("@type {function (new:Array, this:Object)} */", "missing closing )");
  }

  public void testParseFunctionalTypeError10() throws Exception {
    parse("@type {function (this:Array, new:Object)} */", "missing closing )");
  }

  public void testParseFunctionalTypeError11() throws Exception {
    parse("@type {function (Array, new:Object)} */", "missing closing )");
  }

  public void testParseFunctionalTypeError12() throws Exception {
    resolve(parse("@type {function (new:number)}*/").getType(),
        "constructed type must be an object type");
  }

  public void testParseArrayType1() throws Exception {
    testParseType("[number]", "Array");
  }

  public void testParseArrayType2() throws Exception {
    testParseType("[(number,boolean,[Object?])]", "Array");
  }

  public void testParseArrayType3() throws Exception {
    testParseType("[[number],[string]]?", "(Array|null)");
  }

  public void testParseArrayTypeError1() throws Exception {
    parse("@type {[number}*/", "missing closing ]");
  }

  public void testParseArrayTypeError2() throws Exception {
    parse("@type {number]}*/", "expected closing }");
  }

  public void testParseArrayTypeError3() throws Exception {
    parse("@type {[(number,boolean,Object?])]}*/", "missing closing )");
  }

  public void testParseArrayTypeError4() throws Exception {
    parse("@type {(number,boolean,[Object?)]}*/",
        "missing closing ]");
  }

  private void testParseType(String type) throws Exception {
    testParseType(type, type);
  }

  private void testParseType(
      String type, String typeExpected) throws Exception {
    JSDocInfo info = parse("@type {" + type + "}*/");

    assertNotNull(info);
    assertTrue(info.hasType());
    assertEquals(typeExpected, resolve(info.getType()).toString());
  }

  public void testParseNullableModifiers1() throws Exception {
    JSDocInfo info = parse("@type {string?}*/");
    assertTypeEquals(createNullableType(STRING_TYPE), info.getType());
  }

  public void testParseNullableModifiers2() throws Exception {
    JSDocInfo info = parse("@type {!Array.<string?>}*/");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseNullableModifiers3() throws Exception {
    JSDocInfo info = parse("@type {Array.<boolean>?}*/");
    assertTypeEquals(createNullableType(ARRAY_TYPE), info.getType());
  }

  public void testParseNullableModifiers4() throws Exception {
    JSDocInfo info = parse("@type {(string,boolean)?}*/");
    assertTypeEquals(
        createNullableType(createUnionType(STRING_TYPE, BOOLEAN_TYPE)),
        info.getType());
  }

  public void testParseNullableModifiers5() throws Exception {
    JSDocInfo info = parse("@type {(string?,boolean)}*/");
    assertTypeEquals(
        createUnionType(createNullableType(STRING_TYPE), BOOLEAN_TYPE),
        info.getType());
  }

  public void testParseNullableModifiers6() throws Exception {
    JSDocInfo info = parse("@type {(string,boolean?)}*/");
    assertTypeEquals(
        createUnionType(STRING_TYPE, createNullableType(BOOLEAN_TYPE)),
        info.getType());
  }

  public void testParseNullableModifiers7() throws Exception {
    JSDocInfo info = parse("@type {string?|boolean}*/");
    assertTypeEquals(
        createUnionType(createNullableType(STRING_TYPE), BOOLEAN_TYPE),
        info.getType());
  }

  public void testParseNullableModifiers8() throws Exception {
    JSDocInfo info = parse("@type {string|boolean?}*/");
    assertTypeEquals(
        createUnionType(STRING_TYPE, createNullableType(BOOLEAN_TYPE)),
        info.getType());
  }

  public void testParseNullableModifiers9() throws Exception {
    JSDocInfo info = parse("@type {foo.Hello.World?}*/");
    assertTypeEquals(
        createNullableType(
            registry.createNamedType(
                "foo.Hello.World", null, -1, -1)),
        info.getType());
  }

  public void testParseOptionalModifier() throws Exception {
    JSDocInfo info = parse("@type {function(number=)}*/");
    assertTypeEquals(
        registry.createFunctionType(
            UNKNOWN_TYPE, createUnionType(VOID_TYPE, NUMBER_TYPE)),
        info.getType());
  }

  public void testParseNewline1() throws Exception {
    JSDocInfo info = parse("@type {string\n* }\n*/");
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  public void testParseNewline2() throws Exception {
    JSDocInfo info = parse("@type !Array.<\n* number\n* > */");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseNewline3() throws Exception {
    JSDocInfo info = parse("@type !Array.<(number,\n* null)>*/");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseNewline4() throws Exception {
    JSDocInfo info = parse("@type !Array.<(number|\n* null)>*/");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseNewline5() throws Exception {
    JSDocInfo info = parse("@type !Array.<function(\n* )\n* :\n* Date>*/");
    assertTypeEquals(ARRAY_TYPE, info.getType());
  }

  public void testParseReturnType1() throws Exception {
    JSDocInfo info =
        parse("@return {null|string|Array.<boolean>}*/");
    assertTypeEquals(
        createUnionType(ARRAY_TYPE, NULL_TYPE, STRING_TYPE),
        info.getReturnType());
  }

  public void testParseReturnType2() throws Exception {
    JSDocInfo info =
        parse("@returns {null|(string,Array.<boolean>)}*/");
    assertTypeEquals(
        createUnionType(ARRAY_TYPE, NULL_TYPE, STRING_TYPE),
        info.getReturnType());
  }

  public void testParseReturnType3() throws Exception {
    JSDocInfo info =
        parse("@return {((null||Array.<boolean>,string),boolean)}*/");
    assertTypeEquals(
        createUnionType(ARRAY_TYPE, NULL_TYPE, STRING_TYPE, BOOLEAN_TYPE),
        info.getReturnType());
  }

  public void testParseThisType1() throws Exception {
    JSDocInfo info =
        parse("@this {goog.foo.Bar}*/");
    assertTypeEquals(
        registry.createNamedType("goog.foo.Bar", null, -1, -1),
        info.getThisType());
  }

  public void testParseThisType2() throws Exception {
    JSDocInfo info =
        parse("@this goog.foo.Bar*/");
    assertTypeEquals(
        registry.createNamedType("goog.foo.Bar", null, -1, -1),
        info.getThisType());
  }

  public void testParseThisType3() throws Exception {
    parse("@type {number}\n@this goog.foo.Bar*/",
        "type annotation incompatible with other annotations");
  }

  public void testParseThisType4() throws Exception {
    resolve(parse("@this number*/").getThisType(),
        "@this must specify an object type");
  }

  public void testParseThisType5() throws Exception {
    parse("@this {Date|Error}*/");
  }

  public void testParseThisType6() throws Exception {
    resolve(parse("@this {Date|number}*/").getThisType(),
        "@this must specify an object type");
  }

  public void testParseParam1() throws Exception {
    JSDocInfo info = parse("@param {number} index*/");
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(NUMBER_TYPE, info.getParameterType("index"));
  }

  public void testParseParam2() throws Exception {
    JSDocInfo info = parse("@param index*/");
    assertEquals(1, info.getParameterCount());
    assertEquals(null, info.getParameterType("index"));
  }

  public void testParseParam3() throws Exception {
    JSDocInfo info = parse("@param {number} index useful comments*/");
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(NUMBER_TYPE, info.getParameterType("index"));
  }

  public void testParseParam4() throws Exception {
    JSDocInfo info = parse("@param index useful comments*/");
    assertEquals(1, info.getParameterCount());
    assertEquals(null, info.getParameterType("index"));
  }

  public void testParseParam5() throws Exception {
    // Test for multi-line @param.
    JSDocInfo info = parse("@param {number} \n index */");
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(NUMBER_TYPE, info.getParameterType("index"));
  }

  public void testParseParam6() throws Exception {
    // Test for multi-line @param.
    JSDocInfo info = parse("@param {number} \n * index */");
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(NUMBER_TYPE, info.getParameterType("index"));
  }

  public void testParseParam7() throws Exception {
    // Optional @param
    JSDocInfo info = parse("@param {number=} index */");
    assertTypeEquals(
        registry.createOptionalType(NUMBER_TYPE),
        info.getParameterType("index"));
  }

  public void testParseParam8() throws Exception {
    // Var args @param
    JSDocInfo info = parse("@param {...number} index */");
    assertTypeEquals(
        registry.createOptionalType(NUMBER_TYPE),
        info.getParameterType("index"));
  }

  public void testParseParam9() throws Exception {
    parse("@param {...number=} index */",
        "expected closing }", "expecting a variable name in a @param tag");
  }

  public void testParseParam10() throws Exception {
    parse("@param {...number index */", "expected closing }");
  }

  public void testParseParam11() throws Exception {
    parse("@param {number= index */", "expected closing }");
  }

  public void testParseParam12() throws Exception {
    JSDocInfo info = parse("@param {...number|string} index */");
    assertTypeEquals(
        registry.createOptionalType(
            registry.createUnionType(STRING_TYPE, NUMBER_TYPE)),
        info.getParameterType("index"));
  }

  public void testParseParam13() throws Exception {
    JSDocInfo info = parse("@param {...(number|string)} index */");
    assertTypeEquals(
        registry.createOptionalType(
            registry.createUnionType(STRING_TYPE, NUMBER_TYPE)),
        info.getParameterType("index"));
  }

  public void testParseParam14() throws Exception {
    JSDocInfo info = parse("@param {string} [index] */");
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(
        registry.createOptionalType(STRING_TYPE),
        info.getParameterType("index"));
  }

  public void testParseParam15() throws Exception {
    JSDocInfo info = parse("@param {string} [index */", "missing closing ]");
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(STRING_TYPE, info.getParameterType("index"));
  }

  public void testParseParam16() throws Exception {
    JSDocInfo info = parse("@param {string} index] */");
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(STRING_TYPE, info.getParameterType("index"));
  }

  public void testParseParam17() throws Exception {
    JSDocInfo info = parse("@param {string=} [index] */");
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(
        registry.createOptionalType(STRING_TYPE),
        info.getParameterType("index"));
  }

  public void testParseParam18() throws Exception {
    JSDocInfo info = parse("@param {...string} [index] */");
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(
        registry.createOptionalType(STRING_TYPE),
        info.getParameterType("index"));
  }

  public void testParseParam19() throws Exception {
    JSDocInfo info = parse("@param {...} [index] */");
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(
        registry.createOptionalType(UNKNOWN_TYPE),
        info.getParameterType("index"));
    assertTrue(info.getParameterType("index").isVarArgs());
  }

  public void testParseParam20() throws Exception {
    JSDocInfo info = parse("@param {?=} index */");
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(
        UNKNOWN_TYPE, info.getParameterType("index"));
  }

  public void testParseParam21() throws Exception {
    JSDocInfo info = parse("@param {...?} index */");
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(
        UNKNOWN_TYPE, info.getParameterType("index"));
    assertTrue(info.getParameterType("index").isVarArgs());
  }

  public void testParseThrows1() throws Exception {
    JSDocInfo info = parse("@throws {number} Some number */");
    assertEquals(1, info.getThrownTypes().size());
    assertTypeEquals(NUMBER_TYPE, info.getThrownTypes().get(0));
  }

  public void testParseThrows2() throws Exception {
    JSDocInfo info = parse("@throws {number} Some number\n "
                           + "*@throws {String} A string */");
    assertEquals(2, info.getThrownTypes().size());
    assertTypeEquals(NUMBER_TYPE, info.getThrownTypes().get(0));
  }

  public void testParseRecordType1() throws Exception {
    parseFull("/** @param {{x}} n\n*/");
  }

  public void testParseRecordType2() throws Exception {
    parseFull("/** @param {{z, y}} n\n*/");
  }

  public void testParseRecordType3() throws Exception {
    parseFull("/** @param {{z, y, x, q, hello, thisisatest}} n\n*/");
  }

  public void testParseRecordType4() throws Exception {
    parseFull("/** @param {{a, 'a', 'hello', 2, this, do, while, for}} n\n*/");
  }

  public void testParseRecordType5() throws Exception {
    parseFull("/** @param {{x : hello}} n\n*/");
  }

  public void testParseRecordType6() throws Exception {
    parseFull("/** @param {{'x' : hello}} n\n*/");
  }

  public void testParseRecordType7() throws Exception {
    parseFull("/** @param {{'x' : !hello}} n\n*/");
  }

  public void testParseRecordType8() throws Exception {
    parseFull("/** @param {{'x' : !hello, y : bar}} n\n*/");
  }

  public void testParseRecordType9() throws Exception {
    parseFull("/** @param {{'x' : !hello, y : {z : bar, 3 : meh}}} n\n*/");
  }

  public void testParseRecordType10() throws Exception {
    parseFull("/** @param {{__proto__ : moo}} n\n*/");
  }

  public void testParseRecordType11() throws Exception {
    parseFull("/** @param {{a : b} n\n*/",
              "expected closing }");
  }

  public void testParseRecordType12() throws Exception {
    parseFull("/** @param {{!hello : hey}} n\n*/",
              "type not recognized due to syntax error");
  }

  public void testParseRecordType13() throws Exception {
    parseFull("/** @param {{x}|number} n\n*/");
  }

  public void testParseRecordType14() throws Exception {
    parseFull("/** @param {{x : y}|number} n\n*/");
  }

  public void testParseRecordType15() throws Exception {
    parseFull("/** @param {{'x' : y}|number} n\n*/");
  }

  public void testParseRecordType16() throws Exception {
    parseFull("/** @param {{x, y}|number} n\n*/");
  }

  public void testParseRecordType17() throws Exception {
    parseFull("/** @param {{x : hello, 'y'}|number} n\n*/");
  }

  public void testParseRecordType18() throws Exception {
    parseFull("/** @param {number|{x : hello, 'y'}} n\n*/");
  }

  public void testParseRecordType19() throws Exception {
    parseFull("/** @param {?{x : hello, 'y'}} n\n*/");
  }

  public void testParseRecordType20() throws Exception {
    parseFull("/** @param {!{x : hello, 'y'}} n\n*/");
  }

  public void testParseRecordType21() throws Exception {
    parseFull("/** @param {{x : hello, 'y'}|boolean} n\n*/");
  }

  public void testParseRecordType22() throws Exception {
    parseFull("/** @param {{x : hello, 'y'}|function()} n\n*/");
  }

  public void testParseRecordType23() throws Exception {
    parseFull("/** @param {{x : function(), 'y'}|function()} n\n*/");
  }

  public void testParseParamError1() throws Exception {
    parseFull("/** @param\n*/",
        "expecting a variable name in a @param tag");
  }

  public void testParseParamError2() throws Exception {
    parseFull("/** @param {Number}*/",
        "expecting a variable name in a @param tag");
  }

  public void testParseParamError3() throws Exception {
    parseFull("/** @param {Number}\n*/",
        "expecting a variable name in a @param tag");
  }

  public void testParseParamError4() throws Exception {
    parseFull("/** @param {Number}\n* * num */",
        "expecting a variable name in a @param tag");
  }

  public void testParseParamError5() throws Exception {
    parse("@param {number} x \n * @param {string} x */",
        "duplicate variable name \"x\"");
  }

  public void testParseExtends1() throws Exception {
    assertTypeEquals(STRING_OBJECT_TYPE,
                     parse("@extends String*/").getBaseType());
  }

  public void testParseExtends2() throws Exception {
    JSDocInfo info = parse("@extends com.google.Foo.Bar.Hello.World*/");
    assertTypeEquals(
        registry.createNamedType(
            "com.google.Foo.Bar.Hello.World", null, -1, -1),
        info.getBaseType());
  }

  public void testParseExtendsGenerics() throws Exception {
    JSDocInfo info =
        parse("@extends com.google.Foo.Bar.Hello.World.<Boolean,number>*/");
    assertTypeEquals(
        registry.createNamedType(
            "com.google.Foo.Bar.Hello.World", null, -1, -1),
        info.getBaseType());
  }

  public void testParseImplementsGenerics() throws Exception {
    // we ignore things inside <> for now
    List<JSTypeExpression> interfaces =
        parse("@implements {SomeInterface.<*>} */")
        .getImplementedInterfaces();
    assertEquals(1, interfaces.size());
    assertTypeEquals(registry.createNamedType("SomeInterface", null, -1, -1),
        interfaces.get(0));
  }

  public void testParseExtends4() throws Exception {
    assertTypeEquals(STRING_OBJECT_TYPE,
        parse("@extends {String}*/").getBaseType());
  }

  public void testParseExtends5() throws Exception {
    assertTypeEquals(STRING_OBJECT_TYPE,
        parse("@extends {String*/", "expected closing }").getBaseType());
  }

  public void testParseExtends6() throws Exception {
    // Multi-line extends
    assertTypeEquals(STRING_OBJECT_TYPE,
        parse("@extends \n * {String}*/").getBaseType());
  }

  public void testParseExtendsInvalidName() throws Exception {
    // This looks bad, but for the time being it should be ok, as
    // we will not find a type with this name in the js parsed tree.
    // If this is fixed in the future, change this test to check for a
    // warning/error message.
    assertTypeEquals(
        registry.createNamedType("some_++#%$%_UglyString", null, -1, -1),
        parse("@extends {some_++#%$%_UglyString} */").getBaseType());
  }

  public void testParseExtendsNullable1() throws Exception {
    parse("@extends {Base?} */", "expected closing }");
  }

  public void testParseExtendsNullable2() throws Exception {
    parse("@extends Base? */", "expected end of line or comment");
  }

  public void testParseEnum1() throws Exception {
    assertTypeEquals(NUMBER_TYPE, parse("@enum*/").getEnumParameterType());
  }

  public void testParseEnum2() throws Exception {
    assertTypeEquals(STRING_TYPE,
        parse("@enum {string}*/").getEnumParameterType());
  }

  public void testParseEnum3() throws Exception {
    assertTypeEquals(STRING_TYPE,
        parse("@enum string*/").getEnumParameterType());
  }

  public void testParseDesc1() throws Exception {
    assertEquals("hello world!",
        parse("@desc hello world!*/").getDescription());
  }

  public void testParseDesc2() throws Exception {
    assertEquals("hello world!",
        parse("@desc hello world!\n*/").getDescription());
  }

  public void testParseDesc3() throws Exception {
    assertEquals("", parse("@desc*/").getDescription());
  }

  public void testParseDesc4() throws Exception {
    assertEquals("", parse("@desc\n*/").getDescription());
  }

  public void testParseDesc5() throws Exception {
    assertEquals("hello world!",
                 parse("@desc hello\nworld!\n*/").getDescription());
  }

  public void testParseDesc6() throws Exception {
    assertEquals("hello world!",
        parse("@desc hello\n* world!\n*/").getDescription());
  }

  public void testParseDesc7() throws Exception {
    assertEquals("a b c", parse("@desc a\n\nb\nc*/").getDescription());
  }

  public void testParseDesc8() throws Exception {
    assertEquals("a b c d",
        parse("@desc a\n      *b\n\n  *c\n\nd*/").getDescription());
  }

  public void testParseDesc9() throws Exception {
    String comment = "@desc\n.\n,\n{\n)\n}\n|\n.<\n>\n<\n?\n~\n+\n-\n;\n:\n*/";

    assertEquals(". , { ) } | .< > < ? ~ + - ; :",
        parse(comment).getDescription());
  }

  public void testParseDesc10() throws Exception {
    String comment = "@desc\n?\n?\n?\n?*/";

    assertEquals("? ? ? ?", parse(comment).getDescription());
  }

  public void testParseDesc11() throws Exception {
    String comment = "@desc :[]*/";

    assertEquals(":[]", parse(comment).getDescription());
  }

  public void testParseDesc12() throws Exception {
    String comment = "@desc\n:\n[\n]\n...*/";

    assertEquals(": [ ] ...", parse(comment).getDescription());
  }

  public void testParseMeaning1() throws Exception {
    assertEquals("tigers",
        parse("@meaning tigers   */").getMeaning());
  }

  public void testParseMeaning2() throws Exception {
    assertEquals("tigers and lions and bears",
        parse("@meaning tigers\n * and lions\n * and bears */").getMeaning());
  }

  public void testParseMeaning3() throws Exception {
    JSDocInfo info =
        parse("@meaning  tigers\n * and lions\n * @desc  and bears */");
    assertEquals("tigers and lions", info.getMeaning());
    assertEquals("and bears", info.getDescription());
  }

  public void testParseMeaning4() throws Exception {
    parse("@meaning  tigers\n * @meaning and lions  */",
        "extra @meaning tag");
  }

  public void testParseLends1() throws Exception {
    JSDocInfo info = parse("@lends {name} */");
    assertEquals("name", info.getLendsName());
  }

  public void testParseLends2() throws Exception {
    JSDocInfo info = parse("@lends   foo.bar  */");
    assertEquals("foo.bar", info.getLendsName());
  }

  public void testParseLends3() throws Exception {
    parse("@lends {name */", "expected closing }");
  }

  public void testParseLends4() throws Exception {
    parse("@lends {} */", "missing object name in @lends tag");
  }

  public void testParseLends5() throws Exception {
    parse("@lends } */", "missing object name in @lends tag");
  }

  public void testParseLends6() throws Exception {
    parse("@lends {string} \n * @lends {string} */",
        "@lends tag incompatible with other annotations");
  }

  public void testParseLends7() throws Exception {
    parse("@type {string} \n * @lends {string} */",
        "@lends tag incompatible with other annotations");
  }

  public void testParsePreserve() throws Exception {
    Node node = new Node(1);
    this.fileLevelJsDocBuilder = node.getJsDocBuilderForNode();
    String comment = "@preserve Foo\nBar\n\nBaz*/";
    parse(comment);
    assertEquals(" Foo\n Bar\n\n Baz", node.getJSDocInfo().getLicense());
  }

  public void testParseLicense() throws Exception {
    Node node = new Node(1);
    this.fileLevelJsDocBuilder = node.getJsDocBuilderForNode();
    String comment = "@license Foo\nBar\n\nBaz*/";
    parse(comment);
    assertEquals(" Foo\n Bar\n\n Baz", node.getJSDocInfo().getLicense());
  }

  public void testParseLicenseWithAnnotation() throws Exception {
    Node node = new Node(1);
    this.fileLevelJsDocBuilder = node.getJsDocBuilderForNode();
    String comment = "@license Foo \n * @author Charlie Brown */";
    parse(comment);
    assertEquals(" Foo \n @author Charlie Brown ",
        node.getJSDocInfo().getLicense());
  }

  public void testParseDefine1() throws Exception {
    assertTypeEquals(STRING_TYPE,
        parse("@define {string}*/").getType());
  }

  public void testParseDefine2() throws Exception {
    assertTypeEquals(STRING_TYPE,
        parse("@define {string*/", "expected closing }").getType());
  }

  public void testParseDefine3() throws Exception {
    JSDocInfo info = parse("@define {boolean}*/");
    assertTrue(info.isConstant());
    assertTrue(info.isDefine());
    assertTypeEquals(BOOLEAN_TYPE, info.getType());
  }

  public void testParseDefine4() throws Exception {
    assertTypeEquals(NUMBER_TYPE, parse("@define {number}*/").getType());
  }

  public void testParseDefine5() throws Exception {
    assertTypeEquals(createUnionType(NUMBER_TYPE, BOOLEAN_TYPE),
        parse("@define {number|boolean}*/").getType());
  }

  public void testParseDefineErrors1() throws Exception {
    parse("@enum {string}\n @define {string} */", "conflicting @define tag");
  }

  public void testParseDefineErrors2() throws Exception {
    parse("@define {string}\n @enum {string} */",
        "type annotation incompatible with other annotations");
  }

  public void testParseDefineErrors3() throws Exception {
    parse("@const\n @define {string} */", "conflicting @define tag");
  }

  public void testParseDefineErrors4() throws Exception {
    parse("@type string \n @define {string} */", "conflicting @define tag");
  }

  public void testParseDefineErrors5() throws Exception {
    parse("@return {string}\n @define {string} */", "conflicting @define tag");
  }

  public void testParseDefineErrors7() throws Exception {
    parse("@define {string}\n @const */", "conflicting @const tag");
  }

  public void testParseDefineErrors8() throws Exception {
    parse("@define {string}\n @type string */",
        "type annotation incompatible with other annotations");
  }

  public void testParseNoCheck1() throws Exception {
    assertTrue(parse("@notypecheck*/").isNoTypeCheck());
  }

  public void testParseNoCheck2() throws Exception {
    parse("@notypecheck\n@notypecheck*/", "extra @notypecheck tag");
  }

  public void testParseOverride1() throws Exception {
    assertTrue(parse("@override*/").isOverride());
  }

  public void testParseOverride2() throws Exception {
    parse("@override\n@override*/", "extra @override/@inheritDoc tag");
  }

  public void testParseInheritDoc1() throws Exception {
    assertTrue(parse("@inheritDoc*/").isOverride());
  }

  public void testParseInheritDoc2() throws Exception {
    parse("@override\n@inheritDoc*/", "extra @override/@inheritDoc tag");
  }

  public void testParseInheritDoc3() throws Exception {
    parse("@inheritDoc\n@inheritDoc*/", "extra @override/@inheritDoc tag");
  }

  public void testParseNoAlias1() throws Exception {
    assertTrue(parse("@noalias*/").isNoAlias());
  }

  public void testParseNoAlias2() throws Exception {
    parse("@noalias\n@noalias*/", "extra @noalias tag");
  }

  public void testParseDeprecated1() throws Exception {
    assertTrue(parse("@deprecated*/").isDeprecated());
  }

  public void testParseDeprecated2() throws Exception {
    parse("@deprecated\n@deprecated*/", "extra @deprecated tag");
  }

  public void testParseExport1() throws Exception {
    assertTrue(parse("@export*/").isExport());
  }

  public void testParseExport2() throws Exception {
    parse("@export\n@export*/", "extra @export tag");
  }

  public void testParseExterns1() throws Exception {
    assertTrue(parseFileOverview("@externs*/").isExterns());
  }

  public void testParseExterns2() throws Exception {
    parseFileOverview("@externs\n@externs*/", "extra @externs tag");
  }

  public void testParseExterns3() throws Exception {
    assertNull(parse("@externs*/"));
  }

  public void testParseJavaDispatch1() throws Exception {
    assertTrue(parse("@javadispatch*/").isJavaDispatch());
  }

  public void testParseJavaDispatch2() throws Exception {
    parse("@javadispatch\n@javadispatch*/",
        "extra @javadispatch tag");
  }

  public void testParseJavaDispatch3() throws Exception {
    assertNull(parseFileOverview("@javadispatch*/"));
  }

  public void testParseNoCompile1() throws Exception {
    assertTrue(parseFileOverview("@nocompile*/").isNoCompile());
  }

  public void testParseNoCompile2() throws Exception {
    parseFileOverview("@nocompile\n@nocompile*/", "extra @nocompile tag");
  }

  public void testBugAnnotation() throws Exception {
    parse("@bug */");
  }

  public void testDescriptionAnnotation() throws Exception {
    parse("@description */");
  }

  public void testRegression1() throws Exception {
    String comment =
        " * @param {number} index the index of blah\n" +
        " * @return {boolean} whatever\n" +
        " * @private\n" +
        " */";

    JSDocInfo info = parse(comment);
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(NUMBER_TYPE, info.getParameterType("index"));
    assertTypeEquals(BOOLEAN_TYPE, info.getReturnType());
    assertEquals(Visibility.PRIVATE, info.getVisibility());
  }

  public void testRegression2() throws Exception {
    String comment =
        " * @return {boolean} whatever\n" +
        " * but important\n" +
        " *\n" +
        " * @param {number} index the index of blah\n" +
        " * some more comments here\n" +
        " * @param name the name of the guy\n" +
        " *\n" +
        " * @protected\n" +
        " */";

    JSDocInfo info = parse(comment);
    assertEquals(2, info.getParameterCount());
    assertTypeEquals(NUMBER_TYPE, info.getParameterType("index"));
    assertEquals(null, info.getParameterType("name"));
    assertTypeEquals(BOOLEAN_TYPE, info.getReturnType());
    assertEquals(Visibility.PROTECTED, info.getVisibility());
  }

  public void testRegression3() throws Exception {
    String comment =
        " * @param mediaTag this specified whether the @media tag is ....\n" +
        " *\n" +
        "\n" +
        "@public\n" +
        " *\n" +
        "\n" +
        " **********\n" +
        " * @final\n" +
        " */";

    JSDocInfo info = parse(comment);
    assertEquals(1, info.getParameterCount());
    assertEquals(null, info.getParameterType("mediaTag"));
    assertEquals(Visibility.PUBLIC, info.getVisibility());
    assertTrue(info.isConstant());
  }

  public void testRegression4() throws Exception {
    String comment =
        " * @const\n" +
        " * @hidden\n" +
        " * @preserveTry\n" +
        " * @constructor\n" +
        " */";

    JSDocInfo info = parse(comment);
    assertTrue(info.isConstant());
    assertFalse(info.isDefine());
    assertTrue(info.isConstructor());
    assertTrue(info.isHidden());
    assertTrue(info.shouldPreserveTry());
  }

  public void testRegression5() throws Exception {
    String comment = "@const\n@enum {string}\n@public*/";

    JSDocInfo info = parse(comment);
    assertTrue(info.isConstant());
    assertFalse(info.isDefine());
    assertTypeEquals(STRING_TYPE, info.getEnumParameterType());
    assertEquals(Visibility.PUBLIC, info.getVisibility());
  }

  public void testRegression6() throws Exception {
    String comment = "@hidden\n@enum\n@public*/";

    JSDocInfo info = parse(comment);
    assertTrue(info.isHidden());
    assertTypeEquals(NUMBER_TYPE, info.getEnumParameterType());
    assertEquals(Visibility.PUBLIC, info.getVisibility());
  }

  public void testRegression7() throws Exception {
    String comment =
        " * @desc description here\n" +
        " * @param {boolean} flag and some more description\n" +
        " *     nicely formatted\n" +
        " */";

    JSDocInfo info = parse(comment);
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(BOOLEAN_TYPE, info.getParameterType("flag"));
    assertEquals("description here", info.getDescription());
  }

  public void testRegression8() throws Exception {
    String comment =
        " * @name random tag here\n" +
        " * @desc description here\n" +
        " *\n" +
        " * @param {boolean} flag and some more description\n" +
        " *     nicely formatted\n" +
        " */";

    JSDocInfo info = parse(comment);
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(BOOLEAN_TYPE, info.getParameterType("flag"));
    assertEquals("description here", info.getDescription());
  }

  public void testRegression9() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @param {string} p0 blah blah blah\n" +
        " */");

    assertNull(jsdoc.getBaseType());
    assertFalse(jsdoc.isConstant());
    assertNull(jsdoc.getDescription());
    assertNull(jsdoc.getEnumParameterType());
    assertFalse(jsdoc.isHidden());
    assertEquals(1, jsdoc.getParameterCount());
    assertTypeEquals(STRING_TYPE, jsdoc.getParameterType("p0"));
    assertNull(jsdoc.getReturnType());
    assertNull(jsdoc.getType());
    assertEquals(Visibility.INHERITED, jsdoc.getVisibility());
  }

  public void testRegression10() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @param {!String} p0 blah blah blah\n" +
        " * @param {boolean} p1 fobar\n" +
        " * @return {!Date} jksjkash dshad\n" +
        " */");

    assertNull(jsdoc.getBaseType());
    assertFalse(jsdoc.isConstant());
    assertNull(jsdoc.getDescription());
    assertNull(jsdoc.getEnumParameterType());
    assertFalse(jsdoc.isHidden());
    assertEquals(2, jsdoc.getParameterCount());
    assertTypeEquals(STRING_OBJECT_TYPE, jsdoc.getParameterType("p0"));
    assertTypeEquals(BOOLEAN_TYPE, jsdoc.getParameterType("p1"));
    assertTypeEquals(DATE_TYPE, jsdoc.getReturnType());
    assertNull(jsdoc.getType());
    assertEquals(Visibility.INHERITED, jsdoc.getVisibility());
  }

  public void testRegression11() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @constructor\n" +
        " */");

    assertNull(jsdoc.getBaseType());
    assertFalse(jsdoc.isConstant());
    assertNull(jsdoc.getDescription());
    assertNull(jsdoc.getEnumParameterType());
    assertFalse(jsdoc.isHidden());
    assertEquals(0, jsdoc.getParameterCount());
    assertNull(jsdoc.getReturnType());
    assertNull(jsdoc.getType());
    assertEquals(Visibility.INHERITED, jsdoc.getVisibility());
  }

  public void testRegression12() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @extends FooBar\n" +
        " */");

    assertTypeEquals(registry.createNamedType("FooBar", null, 0, 0),
        jsdoc.getBaseType());
    assertFalse(jsdoc.isConstant());
    assertNull(jsdoc.getDescription());
    assertNull(jsdoc.getEnumParameterType());
    assertFalse(jsdoc.isHidden());
    assertEquals(0, jsdoc.getParameterCount());
    assertNull(jsdoc.getReturnType());
    assertNull(jsdoc.getType());
    assertEquals(Visibility.INHERITED, jsdoc.getVisibility());
  }

  public void testRegression13() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @type {!RegExp}\n" +
        " * @protected\n" +
        " */");

    assertNull(jsdoc.getBaseType());
    assertFalse(jsdoc.isConstant());
    assertNull(jsdoc.getDescription());
    assertNull(jsdoc.getEnumParameterType());
    assertFalse(jsdoc.isHidden());
    assertEquals(0, jsdoc.getParameterCount());
    assertNull(jsdoc.getReturnType());
    assertTypeEquals(REGEXP_TYPE, jsdoc.getType());
    assertEquals(Visibility.PROTECTED, jsdoc.getVisibility());
  }

  public void testRegression14() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @const\n" +
        " * @private\n" +
        " */");

    assertNull(jsdoc.getBaseType());
    assertTrue(jsdoc.isConstant());
    assertNull(jsdoc.getDescription());
    assertNull(jsdoc.getEnumParameterType());
    assertFalse(jsdoc.isHidden());
    assertEquals(0, jsdoc.getParameterCount());
    assertNull(jsdoc.getReturnType());
    assertNull(jsdoc.getType());
    assertEquals(Visibility.PRIVATE, jsdoc.getVisibility());
  }

  public void testRegression15() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @desc Hello,\n" +
        " * World!\n" +
        " */");

    assertNull(jsdoc.getBaseType());
    assertFalse(jsdoc.isConstant());
    assertEquals("Hello, World!", jsdoc.getDescription());
    assertNull(jsdoc.getEnumParameterType());
    assertFalse(jsdoc.isHidden());
    assertEquals(0, jsdoc.getParameterCount());
    assertNull(jsdoc.getReturnType());
    assertNull(jsdoc.getType());
    assertEquals(Visibility.INHERITED, jsdoc.getVisibility());
    assertFalse(jsdoc.isExport());
  }

  public void testRegression16() throws Exception {
    JSDocInfo jsdoc = parse(
        " Email is plp@foo.bar\n" +
        " @type {string}\n" +
        " */");

    assertNull(jsdoc.getBaseType());
    assertFalse(jsdoc.isConstant());
    assertTypeEquals(STRING_TYPE, jsdoc.getType());
    assertFalse(jsdoc.isHidden());
    assertEquals(0, jsdoc.getParameterCount());
    assertNull(jsdoc.getReturnType());
    assertEquals(Visibility.INHERITED, jsdoc.getVisibility());
  }

  public void testRegression17() throws Exception {
    // verifying that if no @desc is present the description is empty
    assertNull(parse("@private*/").getDescription());
  }

  public void testFullRegression1() throws Exception {
    parseFull("/** @param (string,number) foo*/function bar(foo){}",
        "expecting a variable name in a @param tag");
  }

  public void testFullRegression2() throws Exception {
    parseFull("/** @param {string,number) foo*/function bar(foo){}",
        "expected closing }",
        "expecting a variable name in a @param tag");
  }

  public void testFullRegression3() throws Exception {
    parseFull("/**..\n*/");
  }

  public void testBug907488() throws Exception {
    parse("@type {number,null} */",
        "expected closing }");
  }

  public void testBug907494() throws Exception {
    parse("@return {Object,undefined} */",
        "expected closing }");
  }

  public void testBug909468() throws Exception {
    parse("@extends {(x)}*/",
        "expecting a type name");
  }

  public void testParseInterface() throws Exception {
    assertTrue(parse("@interface*/").isInterface());
  }

  public void testParseImplicitCast1() throws Exception {
    assertTrue(parse("@type {string} \n * @implicitCast*/").isImplicitCast());
  }

  public void testParseImplicitCast2() throws Exception {
    assertFalse(parse("@type {string}*/").isImplicitCast());
  }

  public void testParseDuplicateImplicitCast() throws Exception {
    parse("@type {string} \n * @implicitCast \n * @implicitCast*/",
          "extra @implicitCast tag");
  }

  public void testParseInterfaceDoubled() throws Exception {
    parse(
        "* @interface\n" +
        "* @interface\n" +
        "*/",
        "type annotation incompatible with other annotations");
  }

  public void testParseImplements() throws Exception {
    List<JSTypeExpression> interfaces = parse("@implements {SomeInterface}*/")
        .getImplementedInterfaces();
    assertEquals(1, interfaces.size());
    assertTypeEquals(registry.createNamedType("SomeInterface", null, -1, -1),
        interfaces.get(0));
  }

  public void testParseImplementsTwo() throws Exception {
    List<JSTypeExpression> interfaces =
        parse(
            "* @implements {SomeInterface1}\n" +
            "* @implements {SomeInterface2}\n" +
            "*/")
        .getImplementedInterfaces();
    assertEquals(2, interfaces.size());
    assertTypeEquals(registry.createNamedType("SomeInterface1", null, -1, -1),
        interfaces.get(0));
    assertTypeEquals(registry.createNamedType("SomeInterface2", null, -1, -1),
        interfaces.get(1));
  }

  public void testParseImplementsSameTwice() throws Exception {
    parse(
        "* @implements {Smth}\n" +
        "* @implements {Smth}\n" +
        "*/",
        "duplicate @implements tag");
  }

  public void testParseImplementsNoName() throws Exception {
    parse("* @implements {} */",
        "expecting a type name");
  }

  public void testParseImplementsMissingRC() throws Exception {
    parse("* @implements {Smth */",
        "expected closing }");
  }

  public void testParseImplementsNullable1() throws Exception {
    parse("@implements {Base?} */", "expected closing }");
  }

  public void testParseImplementsNullable2() throws Exception {
    parse("@implements Base? */", "expected end of line or comment");
  }

  public void testInterfaceExtends() throws Exception {
     JSDocInfo jsdoc = parse(
         " * @interface \n" +
         " * @extends {Extended} */");
    assertTrue(jsdoc.isInterface());
    assertTypeEquals(registry.createNamedType("Extended", null, -1, -1),
        jsdoc.getBaseType());
  }

  public void testBadExtendsWithNullable() throws Exception {
    JSDocInfo jsdoc = parse("@constructor\n * @extends {Object?} */",
        "expected closing }");
    assertTrue(jsdoc.isConstructor());
    assertTypeEquals(OBJECT_TYPE, jsdoc.getBaseType());
  }

  public void testBadImplementsWithNullable() throws Exception {
  JSDocInfo jsdoc = parse("@implements {Disposable?}\n * @constructor */",
      "expected closing }");
    assertTrue(jsdoc.isConstructor());
    assertTypeEquals(registry.createNamedType("Disposable", null, -1, -1),
        jsdoc.getImplementedInterfaces().get(0));
  }

  public void testBadTypeDefInterfaceAndConstructor1() throws Exception {
    JSDocInfo jsdoc = parse("@interface\n@constructor*/",
        "cannot be both an interface and a constructor");
    assertTrue(jsdoc.isInterface());
  }

  public void testBadTypeDefInterfaceAndConstructor2() throws Exception {
    JSDocInfo jsdoc = parse("@constructor\n@interface*/",
        "cannot be both an interface and a constructor");
    assertTrue(jsdoc.isConstructor());
  }

  public void testDocumentationParameter() throws Exception {
    JSDocInfo jsdoc
        = parse("@param {Number} number42 This is a description.*/", true);

    assertTrue(jsdoc.hasDescriptionForParameter("number42"));
    assertEquals("This is a description.",
                 jsdoc.getDescriptionForParameter("number42"));
  }

  public void testMultilineDocumentationParameter() throws Exception {
    JSDocInfo jsdoc
        = parse("@param {Number} number42 This is a description"
                + "\n* on multiple \n* lines.*/", true);

    assertTrue(jsdoc.hasDescriptionForParameter("number42"));
    assertEquals("This is a description on multiple lines.",
                 jsdoc.getDescriptionForParameter("number42"));

  }

  public void testDocumentationMultipleParameter() throws Exception {
    JSDocInfo jsdoc
        = parse("@param {Number} number42 This is a description."
                + "\n* @param {Integer} number87 This is another description.*/"
                , true);

    assertTrue(jsdoc.hasDescriptionForParameter("number42"));
    assertEquals("This is a description.",
                 jsdoc.getDescriptionForParameter("number42"));

    assertTrue(jsdoc.hasDescriptionForParameter("number87"));
    assertEquals("This is another description.",
                 jsdoc.getDescriptionForParameter("number87"));
  }

  public void testDocumentationMultipleParameter2() throws Exception {
    JSDocInfo jsdoc
        = parse("@param {number} delta = 0 results in a redraw\n" +
                "  != 0 ..... */", true);
    assertTrue(jsdoc.hasDescriptionForParameter("delta"));
    assertEquals("= 0 results in a redraw != 0 .....",
                 jsdoc.getDescriptionForParameter("delta"));
  }


  public void testAuthors() throws Exception {
    JSDocInfo jsdoc
        = parse("@param {Number} number42 This is a description."
                + "\n* @param {Integer} number87 This is another description."
                + "\n* @author a@google.com (A Person)"
                + "\n* @author b@google.com (B Person)"
                + "\n* @author c@google.com (C Person)*/"
                , true);

    Collection<String> authors = jsdoc.getAuthors();

    assertTrue(authors != null);
    assertTrue(authors.size() == 3);

    assertContains(authors, "a@google.com (A Person)");
    assertContains(authors, "b@google.com (B Person)");
    assertContains(authors, "c@google.com (C Person)");
  }

  public void testSuppress1() throws Exception {
    JSDocInfo info = parse("@suppress {x} */");
    assertEquals(Sets.newHashSet("x"), info.getSuppressions());
  }

  public void testSuppress2() throws Exception {
    JSDocInfo info = parse("@suppress {x|y|x|z} */");
    assertEquals(Sets.newHashSet("x", "y", "z"), info.getSuppressions());
  }

  public void testBadSuppress1() throws Exception {
    parse("@suppress {} */", "malformed @suppress tag");
  }

  public void testBadSuppress2() throws Exception {
    parse("@suppress {x|} */", "malformed @suppress tag");
  }

  public void testBadSuppress3() throws Exception {
    parse("@suppress {|x} */", "malformed @suppress tag");
  }

  public void testBadSuppress4() throws Exception {
    parse("@suppress {x|y */", "malformed @suppress tag");
  }

  public void testBadSuppress5() throws Exception {
    parse("@suppress {x,y} */", "malformed @suppress tag");
  }

  public void testBadSuppress6() throws Exception {
    parse("@suppress {x} \n * @suppress {y} */", "duplicate @suppress tag");
  }

  public void testBadSuppress7() throws Exception {
    parse("@suppress {impossible} */",
          "unknown @suppress parameter: impossible");
  }

  public void testModifies1() throws Exception {
    JSDocInfo info = parse("@modifies {this} */");
    assertEquals(Sets.newHashSet("this"), info.getModifies());
  }

  public void testModifies2() throws Exception {
    JSDocInfo info = parse("@modifies {arguments} */");
    assertEquals(Sets.newHashSet("arguments"), info.getModifies());
  }

  public void testModifies3() throws Exception {
    JSDocInfo info = parse("@modifies {this|arguments} */");
    assertEquals(Sets.newHashSet("this", "arguments"), info.getModifies());
  }

  public void testModifies4() throws Exception {
    JSDocInfo info = parse("@param {*} x\n * @modifies {x} */");
    assertEquals(Sets.newHashSet("x"), info.getModifies());
  }

  public void testModifies5() throws Exception {
    JSDocInfo info = parse(
        "@param {*} x\n"
        + " * @param {*} y\n"
        + " * @modifies {x} */");
    assertEquals(Sets.newHashSet("x"), info.getModifies());
  }

  public void testModifies6() throws Exception {
    JSDocInfo info = parse(
        "@param {*} x\n"
        + " * @param {*} y\n"
        + " * @modifies {x|y} */");
    assertEquals(Sets.newHashSet("x", "y"), info.getModifies());
  }


  public void testBadModifies1() throws Exception {
    parse("@modifies {} */", "malformed @modifies tag");
  }

  public void testBadModifies2() throws Exception {
    parse("@modifies {this|} */", "malformed @modifies tag");
  }

  public void testBadModifies3() throws Exception {
    parse("@modifies {|this} */", "malformed @modifies tag");
  }

  public void testBadModifies4() throws Exception {
    parse("@modifies {this|arguments */", "malformed @modifies tag");
  }

  public void testBadModifies5() throws Exception {
    parse("@modifies {this,arguments} */", "malformed @modifies tag");
  }

  public void testBadModifies6() throws Exception {
    parse("@modifies {this} \n * @modifies {this} */",
        "conflicting @modifies tag");
  }

  public void testBadModifies7() throws Exception {
    parse("@modifies {impossible} */",
          "unknown @modifies parameter: impossible");
  }

  public void testBadModifies8() throws Exception {
    parse("@modifies {this}\n"
        + "@nosideeffects */", "conflicting @nosideeffects tag");
  }

  public void testBadModifies9() throws Exception {
    parse("@nosideeffects\n"
        + "@modifies {this} */", "conflicting @modifies tag");
  }

  //public void testNoParseFileOverview() throws Exception {
  //  JSDocInfo jsdoc = parseFileOverviewWithoutDoc("@fileoverview Hi mom! */");
  //  assertNull(jsdoc.getFileOverview());
  //  assertTrue(jsdoc.hasFileOverview());
  //}

  public void testFileOverviewSingleLine() throws Exception {
    JSDocInfo jsdoc = parseFileOverview("@fileoverview Hi mom! */");
    assertEquals("Hi mom!", jsdoc.getFileOverview());
  }

  public void testFileOverviewMultiLine() throws Exception {
    JSDocInfo jsdoc = parseFileOverview("@fileoverview Pie is \n * good! */");
    assertEquals("Pie is\n good!", jsdoc.getFileOverview());
  }

  public void testFileOverviewDuplicate() throws Exception {
    JSDocInfo jsdoc = parseFileOverview(
        "@fileoverview Pie \n * @fileoverview Cake */",
        "extra @fileoverview tag");
  }

  public void testReferences() throws Exception {
    JSDocInfo jsdoc
        = parse("@see A cool place!"
                + "\n* @see The world."
                + "\n* @see SomeClass#SomeMember"
                + "\n* @see A boring test case*/"
                , true);

    Collection<String> references = jsdoc.getReferences();

    assertTrue(references != null);
    assertTrue(references.size() == 4);

    assertContains(references, "A cool place!");
    assertContains(references, "The world.");
    assertContains(references, "SomeClass#SomeMember");
    assertContains(references, "A boring test case");
  }

  public void testSingleTags() throws Exception {
    JSDocInfo jsdoc
        = parse("@version Some old version"
                + "\n* @deprecated In favor of the new one!"
                + "\n* @return {SomeType} The most important object :-)*/"
                , true);

    assertTrue(jsdoc.isDeprecated());
    assertEquals("In favor of the new one!", jsdoc.getDeprecationReason());
    assertEquals("Some old version", jsdoc.getVersion());
    assertEquals("The most important object :-)", jsdoc.getReturnDescription());
  }

  public void testSingleTagsReordered() throws Exception {
    JSDocInfo jsdoc
        = parse("@deprecated In favor of the new one!"
                + "\n * @return {SomeType} The most important object :-)"
                + "\n * @version Some old version*/"
                , true);

    assertTrue(jsdoc.isDeprecated());
    assertEquals("In favor of the new one!", jsdoc.getDeprecationReason());
    assertEquals("Some old version", jsdoc.getVersion());
    assertEquals("The most important object :-)", jsdoc.getReturnDescription());
  }

  public void testVersionDuplication() throws Exception {
    parse("* @version Some old version"
          + "\n* @version Another version*/", true,
          "conflicting @version tag");
  }

  public void testVersionMissing() throws Exception {
    parse("* @version */", true,
          "@version tag missing version information");
  }

  public void testAuthorMissing() throws Exception {
    parse("* @author */", true,
          "@author tag missing author");
  }

  public void testSeeMissing() throws Exception {
    parse("* @see */", true,
          "@see tag missing description");
  }

  public void testSourceName() throws Exception {
    JSDocInfo jsdoc = parse("@deprecated */", true);
    assertEquals("testcode", jsdoc.getSourceName());
  }

  public void testParseBlockComment() throws Exception {
    JSDocInfo jsdoc = parse("this is a nice comment\n "
                            + "* that is multiline \n"
                            + "* @author abc@google.com */", true);

    assertEquals("this is a nice comment\nthat is multiline",
                 jsdoc.getBlockDescription());

    assertDocumentationInMarker(
        assertAnnotationMarker(jsdoc, "author", 2, 2),
        "abc@google.com", 9, 2, 23);
  }

  public void testParseBlockComment2() throws Exception {
    JSDocInfo jsdoc = parse("this is a nice comment\n "
                            + "* that is *** multiline \n"
                            + "* @author abc@google.com */", true);

    assertEquals("this is a nice comment\nthat is *** multiline",
                 jsdoc.getBlockDescription());

    assertDocumentationInMarker(
        assertAnnotationMarker(jsdoc, "author", 2, 2),
        "abc@google.com", 9, 2, 23);
  }

  public void testParseBlockComment3() throws Exception {
    JSDocInfo jsdoc = parse("\n "
                            + "* hello world \n"
                            + "* @author abc@google.com */", true);

    assertEquals("hello world", jsdoc.getBlockDescription());

    assertDocumentationInMarker(
        assertAnnotationMarker(jsdoc, "author", 2, 2),
        "abc@google.com", 9, 2, 23);
  }

  public void testParseWithMarkers1() throws Exception {
    JSDocInfo jsdoc = parse("@author abc@google.com */", true);

    assertDocumentationInMarker(
        assertAnnotationMarker(jsdoc, "author", 0, 0),
        "abc@google.com", 7, 0, 21);
  }

  public void testParseWithMarkers2() throws Exception {
    JSDocInfo jsdoc = parse("@param {Foo} somename abc@google.com */", true);

    assertDocumentationInMarker(
        assertAnnotationMarker(jsdoc, "param", 0, 0),
        "abc@google.com", 21, 0, 37);
  }

  public void testParseWithMarkers3() throws Exception {
    JSDocInfo jsdoc =
        parse("@return {Foo} some long \n * multiline" +
              " \n * description */", true);

    assertDocumentationInMarker(
        assertAnnotationMarker(jsdoc, "return", 0, 0),
        "some long multiline description", 13, 2, 15);
  }

  public void testParseWithMarkers4() throws Exception {
    JSDocInfo jsdoc =
        parse("@author foobar \n * @param {Foo} somename abc@google.com */",
              true);

    assertAnnotationMarker(jsdoc, "author", 0, 0);
    assertAnnotationMarker(jsdoc, "param", 1, 3);
  }

  public void testParseWithMarkerNames1() throws Exception {
    JSDocInfo jsdoc = parse("@param {SomeType} name somedescription */", true);

    assertNameInMarker(
        assertAnnotationMarker(jsdoc, "param", 0, 0),
        "name", 18);
  }

  public void testParseWithMarkerNames2() throws Exception {
    JSDocInfo jsdoc = parse("@param {SomeType} name somedescription \n" +
                            "* @param {AnotherType} anothername des */", true);

    assertTypeInMarker(
        assertNameInMarker(
            assertAnnotationMarker(jsdoc, "param", 0, 0, 0),
            "name", 18),
        "SomeType", 7, true);

    assertTypeInMarker(
        assertNameInMarker(
            assertAnnotationMarker(jsdoc, "param", 1, 2, 1),
            "anothername", 23),
        "AnotherType", 9, true);
  }

  public void testParseWithoutMarkerName() throws Exception {
    JSDocInfo jsdoc = parse("@author helloworld*/", true);
    assertNull(assertAnnotationMarker(jsdoc, "author", 0, 0).name);
  }

  public void testParseWithMarkerType() throws Exception {
    JSDocInfo jsdoc = parse("@extends {FooBar}*/", true);

    assertTypeInMarker(
        assertAnnotationMarker(jsdoc, "extends", 0, 0),
        "FooBar", 9, true);
  }

  public void testParseWithMarkerType2() throws Exception {
    JSDocInfo jsdoc = parse("@extends FooBar*/", true);

    assertTypeInMarker(
        assertAnnotationMarker(jsdoc, "extends", 0, 0),
        "FooBar", 9, false);
  }

  public void testTypeTagConflict1() throws Exception {
    parse("@constructor \n * @constructor */",
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict2() throws Exception {
    parse("@interface \n * @interface */",
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict3() throws Exception {
    parse("@constructor \n * @interface */",
        "cannot be both an interface and a constructor");
  }

  public void testTypeTagConflict4() throws Exception {
    parse("@interface \n * @constructor */",
        "cannot be both an interface and a constructor");
  }

  public void testTypeTagConflict5() throws Exception {
    parse("@interface \n * @type {string} */",
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict6() throws Exception {
    parse("@typedef {string} \n * @type {string} */",
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict7() throws Exception {
    parse("@typedef {string} \n * @constructor */",
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict8() throws Exception {
    parse("@typedef {string} \n * @return {boolean} */",
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict9() throws Exception {
    parse("@enum {string} \n * @return {boolean} */",
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict10() throws Exception {
    parse("@this {Object} \n * @enum {boolean} */",
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict11() throws Exception {
    parse("@param {Object} x \n * @type {boolean} */",
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict12() throws Exception {
    parse("@typedef {boolean} \n * @param {Object} x */",
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict13() throws Exception {
    parse("@typedef {boolean} \n * @extends {Object} */",
        "type annotation incompatible with other annotations");
  }

  public void testParserWithTemplateTypeNameMisisng() {
    parse("@template */", "@template tag missing type name");
  }

  public void testParserWithTemplateTypeNameMissing() {
    parse("@template T\n@template V */", "@template tag at most once");
  }

  public void testWhitelistedNewAnnotations() {
    parse("@foobar */",
        "illegal use of unknown JSDoc tag \"foobar\"; ignoring it");
    extraAnnotations.add("foobar");
    parse("@foobar */");
  }

  public void testWhitelistedConflictingAnnotation() {
    extraAnnotations.add("param");
    JSDocInfo info = parse("@param {number} index */");
    assertTypeEquals(NUMBER_TYPE, info.getParameterType("index"));
  }

  public void testNonIdentifierAnnotation() {
    // Try to whitelist an annotation that is not a valid JS identifier.
    // It should not work.
    extraAnnotations.add("123");
    parse("@123 */", "illegal use of unknown JSDoc tag \"\"; ignoring it");
  }

  public void testUnsupportedJsDocSyntax1() {
    JSDocInfo info =
        parse("@param {string} [accessLevel=\"author\"] The user level */",
            true);
    assertEquals(1, info.getParameterCount());
    assertTypeEquals(
        registry.createOptionalType(STRING_TYPE),
        info.getParameterType("accessLevel"));
    assertEquals("The user level",
        info.getDescriptionForParameter("accessLevel"));
  }

  public void testUnsupportedJsDocSyntax2() {
    JSDocInfo info =
        parse("@param userInfo The user info. \n" +
              " * @param userInfo.name The name of the user */", true);
    assertEquals(1, info.getParameterCount());
    assertEquals("The user info.",
        info.getDescriptionForParameter("userInfo"));
  }

  public void testWhitelistedAnnotations() {
    parse(
      "* @addon \n" +
      "* @augments \n" +
      "* @base \n" +
      "* @borrows \n" +
      "* @bug \n" +
      "* @class \n" +
      "* @config \n" +
      "* @constructs \n" +
      "* @default \n" +
      "* @description \n" +
      "* @event \n" +
      "* @example \n" +
      "* @exception \n" +
      "* @exec \n" +
      "* @externs \n" +
      "* @field \n" +
      "* @function \n" +
      "* @id \n" +
      "* @ignore \n" +
      "* @inner \n" +
      "* @lends {string} \n" +
      "* @link \n" +
      "* @member \n" +
      "* @memberOf \n" +
      "* @modName \n" +
      "* @mods \n" +
      "* @name \n" +
      "* @namespace \n" +
      "* @nocompile \n" +
      "* @property \n" +
      "* @requires \n" +
      "* @since \n" +
      "* @static \n" +
      "* @supported */");
  }

  public void testGetOriginalCommentString() throws Exception {
    String comment = "* @desc This is a comment */";
    JSDocInfo info = parse(comment);
    assertNull(info.getOriginalCommentString());
    info = parse(comment, true /* parseDocumentation */);
    assertEquals(comment, info.getOriginalCommentString());
  }

  /**
   * Asserts that a documentation field exists on the given marker.
   *
   * @param description The text of the documentation field expected.
   * @param startCharno The starting character of the text.
   * @param endLineno The ending line of the text.
   * @param endCharno The ending character of the text.
   * @return The marker, for chaining purposes.
   */
  private JSDocInfo.Marker assertDocumentationInMarker(JSDocInfo.Marker marker,
                                                       String description,
                                                       int startCharno,
                                                       int endLineno,
                                                       int endCharno) {
    assertTrue(marker.description != null);
    assertEquals(description, marker.description.getItem());

    // Match positional information.
    assertEquals(marker.annotation.getStartLine(),
                 marker.description.getStartLine());
    assertEquals(startCharno, marker.description.getPositionOnStartLine());
    assertEquals(endLineno, marker.description.getEndLine());
    assertEquals(endCharno, marker.description.getPositionOnEndLine());

    return marker;
  }

  /**
   * Asserts that a type field exists on the given marker.
   *
   * @param typeName The name of the type expected in the type field.
   * @param startCharno The starting character of the type declaration.
   * @param hasBrackets Whether the type in the type field is expected
   *     to have brackets.
   * @return The marker, for chaining purposes.
   */
  private JSDocInfo.Marker assertTypeInMarker(JSDocInfo.Marker marker,
                                            String typeName, int startCharno,
                                            boolean hasBrackets) {

    assertTrue(marker.type != null);
    assertTrue(marker.type.getItem().getType() == Token.STRING);

    // Match the name and brackets information.
    String foundName = marker.type.getItem().getString();

    assertEquals(typeName, foundName);
    assertEquals(hasBrackets, marker.type.hasBrackets);

    // Match position information.
    assertEquals(startCharno, marker.type.getPositionOnStartLine());

    int endCharno = startCharno + foundName.length();

    if (hasBrackets) {
      endCharno += 1;
    }

    assertEquals(endCharno, marker.type.getPositionOnEndLine());
    assertEquals(marker.annotation.getStartLine(), marker.type.getStartLine());
    assertEquals(marker.annotation.getStartLine(), marker.type.getEndLine());

    return marker;
  }

  /**
   * Asserts that a name field exists on the given marker.
   *
   * @param name The name expected in the name field.
   * @param startCharno The starting character of the text.
   * @return The marker, for chaining purposes.
   */
  private JSDocInfo.Marker assertNameInMarker(JSDocInfo.Marker marker,
                                            String name, int startCharno) {
    assertTrue(marker.name != null);
    assertEquals(name, marker.name.getItem());

    assertEquals(startCharno, marker.name.getPositionOnStartLine());
    assertEquals(startCharno + name.length(),
                 marker.name.getPositionOnEndLine());

    assertEquals(marker.annotation.getStartLine(), marker.name.getStartLine());
    assertEquals(marker.annotation.getStartLine(), marker.name.getEndLine());

    return marker;
  }

  /**
   * Asserts that an annotation marker of a given annotation name
   * is found in the given JSDocInfo.
   *
   * @param jsdoc The JSDocInfo in which to search for the annotation marker.
   * @param annotationName The name/type of the annotation for which to
   *   search. Example: "author" for an "@author" annotation.
   * @param startLineno The expected starting line number of the marker.
   * @param startCharno The expected character on the starting line.
   * @return The marker found, for further testing.
   */
  private JSDocInfo.Marker assertAnnotationMarker(JSDocInfo jsdoc,
                                                  String annotationName,
                                                  int startLineno,
                                                  int startCharno) {
    return assertAnnotationMarker(jsdoc, annotationName, startLineno,
                                  startCharno, 0);
  }

  /**
   * Asserts that the index-th annotation marker of a given annotation name
   * is found in the given JSDocInfo.
   *
   * @param jsdoc The JSDocInfo in which to search for the annotation marker.
   * @param annotationName The name/type of the annotation for which to
   *   search. Example: "author" for an "@author" annotation.
   * @param startLineno The expected starting line number of the marker.
   * @param startCharno The expected character on the starting line.
   * @param index The index of the marker.
   * @return The marker found, for further testing.
   */
  private JSDocInfo.Marker assertAnnotationMarker(JSDocInfo jsdoc,
                                                  String annotationName,
                                                  int startLineno,
                                                  int startCharno,
                                                  int index) {

    Collection<JSDocInfo.Marker> markers = jsdoc.getMarkers();

    assertTrue(markers.size() > 0);

    int counter = 0;

    for (JSDocInfo.Marker marker : markers) {
      if (marker.annotation != null) {
        if (annotationName.equals(marker.annotation.getItem())) {

          if (counter == index) {
            assertEquals(startLineno, marker.annotation.getStartLine());
            assertEquals(startCharno,
                         marker.annotation.getPositionOnStartLine());
            assertEquals(startLineno, marker.annotation.getEndLine());
            assertEquals(startCharno + annotationName.length(),
                         marker.annotation.getPositionOnEndLine());

            return marker;
          }

          counter++;
        }
      }
    }

    fail("No marker found");
    return null;
  }

  private <T> void assertContains(Collection<T> collection, T item) {
    assertTrue(collection.contains(item));
  }

  private void parseFull(String code, String... warnings) {
    CompilerEnvirons environment = new CompilerEnvirons();

    TestErrorReporter testErrorReporter = new TestErrorReporter(null, warnings);
    environment.setErrorReporter(testErrorReporter);

    environment.setRecordingComments(true);
    environment.setRecordingLocalJsDocComments(true);

    Parser p = new Parser(environment, testErrorReporter);
    AstRoot script = p.parse(code, null, 0);

    Config config =
        new Config(extraAnnotations, extraSuppressions, true, false);
    for (Comment comment : script.getComments()) {
      JsDocInfoParser jsdocParser =
        new JsDocInfoParser(
            new JsDocTokenStream(comment.getValue().substring(3),
                comment.getLineno()),
            comment,
            script.getSourceName(),
            config,
            testErrorReporter);
      jsdocParser.parse();
      jsdocParser.retrieveAndResetParsedJSDocInfo();
    }

    assertTrue("some expected warnings were not reported",
        testErrorReporter.hasEncounteredAllWarnings());
  }

  private JSDocInfo parseFileOverviewWithoutDoc(String comment,
                                                String... warnings) {
    return parse(comment, false, true, warnings);
  }

  private JSDocInfo parseFileOverview(String comment, String... warnings) {
    return parse(comment, true, true, warnings);
  }

  private JSDocInfo parse(String comment, String... warnings) {
    return parse(comment, false, warnings);
  }

  private JSDocInfo parse(String comment, boolean parseDocumentation,
                          String... warnings) {
    return parse(comment, parseDocumentation, false, warnings);
  }

  private JSDocInfo parse(String comment, boolean parseDocumentation,
      boolean parseFileOverview, String... warnings) {
    TestErrorReporter errorReporter = new TestErrorReporter(null, warnings);

    Config config = new Config(extraAnnotations, extraSuppressions,
        parseDocumentation, false);
    JsDocInfoParser jsdocParser = new JsDocInfoParser(
        stream(comment),
        new Comment(0, 0, CommentType.JSDOC, comment),
        "testcode", config, errorReporter);

    if (fileLevelJsDocBuilder != null) {
      jsdocParser.setFileLevelJsDocBuilder(fileLevelJsDocBuilder);
    }

    jsdocParser.parse();

    assertTrue("expected warnings were not reported",
        errorReporter.hasEncounteredAllWarnings());

    if (parseFileOverview) {
      return jsdocParser.getFileOverviewJSDocInfo();
    } else {
      return jsdocParser.retrieveAndResetParsedJSDocInfo();
    }
  }

  private Node parseType(String typeComment) {
    return JsDocInfoParser.parseTypeString(typeComment);
  }

  private JsDocTokenStream stream(String source) {
    return new JsDocTokenStream(source, 0);
  }

  private void assertParameterTypeEquals(JSType expected, JSTypeExpression te) {
    assertEquals(expected, ((ObjectType) resolve(te)).getParameterType());
  }

  private void assertIndexTypeEquals(JSType expected, JSTypeExpression te) {
    assertEquals(expected, ((ObjectType) resolve(te)).getIndexType());
  }
}
