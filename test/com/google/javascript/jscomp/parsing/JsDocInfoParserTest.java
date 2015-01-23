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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.ParserRunner.ParseResult;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Marker;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.SimpleSourceFile;
import com.google.javascript.rhino.jstype.StaticSourceFile;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.TestErrorReporter;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JsDocInfoParserTest extends BaseJSTypeTestCase {

  private Set<String> extraAnnotations;
  private Set<String> extraSuppressions;
  private Node.FileLevelJsDocBuilder fileLevelJsDocBuilder = null;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    extraAnnotations = new HashSet<>(ParserRunner.createConfig(
        true, LanguageMode.ECMASCRIPT3, false, null)
            .annotationNames.keySet());
    extraSuppressions = new HashSet<>(ParserRunner.createConfig(
        true, LanguageMode.ECMASCRIPT3, false, null).suppressionNames);

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
    assertThat(typeNode).isNull();
  }

  public void testParseInvalidTypeViaStatic2() throws Exception {
    Node typeNode = parseType("");
    assertThat(typeNode).isNull();
  }

  public void testParseNamedType1() throws Exception {
    assertThat(parse("@type null", "Unexpected end of file")).isNull();
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

  public void testParseNamedType5() throws Exception {
    JSDocInfo info = parse("@type {!goog.\nBar}*/");
    assertTypeEquals(
        registry.createNamedType("goog.Bar", null, -1, -1),
        info.getType());
  }

  public void testParseNamedType6() throws Exception {
    JSDocInfo info = parse("@type {!goog.\n * Bar.\n * Baz}*/");
    assertTypeEquals(
        registry.createNamedType("goog.Bar.Baz", null, -1, -1),
        info.getType());
  }

  public void testParseNamedTypeError1() throws Exception {
    // To avoid parsing ambiguities, type names must end in a '.' to
    // get the continuation behavior.
    parse("@type {!goog\n * .Bar} */",
        "Bad type annotation. expected closing }");
  }

  public void testParseNamedTypeError2() throws Exception {
    parse("@type {!goog.\n * Bar\n * .Baz} */",
        "Bad type annotation. expected closing }");
  }

  public void testParseNamespaceType1() throws Exception {
    JSDocInfo info = parse("@type {goog.}*/");
    assertTypeEquals(
        registry.createNamedType("goog.", null, -1, -1),
        info.getType());
  }

  public void testTypedefType1() throws Exception {
    JSDocInfo info = parse("@typedef string */");
    assertThat(info.hasTypedefType()).isTrue();
    assertTypeEquals(STRING_TYPE, info.getTypedefType());
  }

  public void testTypedefType2() throws Exception {
    JSDocInfo info = parse("@typedef \n {string}*/");
    assertThat(info.hasTypedefType()).isTrue();
    assertTypeEquals(STRING_TYPE, info.getTypedefType());
  }

  public void testTypedefType3() throws Exception {
    JSDocInfo info = parse("@typedef \n {(string|number)}*/");
    assertThat(info.hasTypedefType()).isTrue();
    assertTypeEquals(createUnionType(NUMBER_TYPE, STRING_TYPE), info.getTypedefType());
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
    assertTypeEquals(
        BOOLEAN_OBJECT_TYPE, parse("@type {!Boolean}*/").getType());
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
    assertTypeEquals(
        RANGE_ERROR_TYPE, parse("@type {!RangeError}*/").getType());
    assertTypeEquals(
        SYNTAX_ERROR_TYPE, parse("@type {!SyntaxError}*/").getType());
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

  public void testParseTemplatizedTypeAlternateSyntax() throws Exception {
    JSDocInfo info = parse("@type !Array<number> */");
    assertTypeEquals(
        createTemplatizedType(ARRAY_TYPE, NUMBER_TYPE), info.getType());
  }

  public void testParseTemplatizedType1() throws Exception {
    JSDocInfo info = parse("@type !Array.<number> */");
    assertTypeEquals(
        createTemplatizedType(ARRAY_TYPE, NUMBER_TYPE), info.getType());
  }

  public void testParseTemplatizedType2() throws Exception {
    JSDocInfo info = parse("@type {!Array.<number>}*/");
    assertTypeEquals(
        createTemplatizedType(ARRAY_TYPE, NUMBER_TYPE), info.getType());
  }

  public void testParseTemplatizedType3() throws Exception {
    JSDocInfo info = parse("@type !Array.<(number,null)>*/");
    assertTypeEquals(
        createTemplatizedType(ARRAY_TYPE,
            createUnionType(NUMBER_TYPE, NULL_TYPE)),
        info.getType());
  }

  public void testParseTemplatizedType4() throws Exception {
    JSDocInfo info = parse("@type {!Array.<(number|null)>}*/");
    assertTypeEquals(
        createTemplatizedType(ARRAY_TYPE,
            createUnionType(NUMBER_TYPE, NULL_TYPE)),
        info.getType());
  }

  public void testParseTemplatizedType5() throws Exception {
    JSDocInfo info = parse("@type {!Array.<Array.<(number|null)>>}*/");
    assertTypeEquals(
        createTemplatizedType(ARRAY_TYPE,
            createUnionType(NULL_TYPE,
                createTemplatizedType(ARRAY_TYPE,
                    createUnionType(NUMBER_TYPE, NULL_TYPE)))),
        info.getType());
  }

  public void testParseTemplatizedType6() throws Exception {
    JSDocInfo info = parse("@type {!Array.<!Array.<(number|null)>>}*/");
    assertTypeEquals(
        createTemplatizedType(ARRAY_TYPE,
            createTemplatizedType(ARRAY_TYPE,
                createUnionType(NUMBER_TYPE, NULL_TYPE))),
        info.getType());
  }

  public void testParseTemplatizedType7() throws Exception {
    JSDocInfo info = parse("@type {!Array.<function():Date>}*/");
    assertTypeEquals(
        createTemplatizedType(ARRAY_TYPE,
            registry.createFunctionType(
                createUnionType(DATE_TYPE, NULL_TYPE))),
        info.getType());
  }

  public void testParseTemplatizedType8() throws Exception {
    JSDocInfo info = parse("@type {!Array.<function():!Date>}*/");
    assertTypeEquals(
        createTemplatizedType(ARRAY_TYPE,
            registry.createFunctionType(DATE_TYPE)),
        info.getType());
  }

  public void testParseTemplatizedType9() throws Exception {
    JSDocInfo info = parse("@type {!Array.<Date|number>}*/");
    assertTypeEquals(
        createTemplatizedType(ARRAY_TYPE,
            createUnionType(DATE_TYPE, NUMBER_TYPE, NULL_TYPE)),
        info.getType());
  }

  public void testParseTemplatizedType10() throws Exception {
    JSDocInfo info = parse("@type {!Array.<Date|number|boolean>}*/");
    assertTypeEquals(
        createTemplatizedType(ARRAY_TYPE,
            createUnionType(DATE_TYPE, NUMBER_TYPE, BOOLEAN_TYPE, NULL_TYPE)),
        info.getType());
  }

  public void testParseTemplatizedType11() throws Exception {
    JSDocInfo info = parse("@type {!Object.<number>}*/");
    assertTypeEquals(
        createTemplatizedType(
            OBJECT_TYPE, ImmutableList.of(UNKNOWN_TYPE, NUMBER_TYPE)),
        info.getType());
    assertTemplatizedTypeEquals(
        registry.getObjectElementKey(), NUMBER_TYPE, info.getType());
  }

  public void testParseTemplatizedType12() throws Exception {
    JSDocInfo info = parse("@type {!Object.<string,number>}*/");
    assertTypeEquals(
        createTemplatizedType(
            OBJECT_TYPE, ImmutableList.of(STRING_TYPE, NUMBER_TYPE)),
        info.getType());
    assertTemplatizedTypeEquals(
        registry.getObjectElementKey(), NUMBER_TYPE, info.getType());
    assertTemplatizedTypeEquals(
        registry.getObjectIndexKey(), STRING_TYPE, info.getType());
  }

  public void testParseTemplatizedType13() throws Exception {
    JSDocInfo info = parse("@type !Array.<?> */");
    assertTypeEquals(
        createTemplatizedType(ARRAY_TYPE, UNKNOWN_TYPE), info.getType());
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
    assertTypeEquals(createUnionType(
        createTemplatizedType(
            ARRAY_TYPE, BOOLEAN_TYPE), NULL_TYPE), info.getType());
  }

  public void testParseUnionType5() throws Exception {
    JSDocInfo info = parse("@type {(null, Array.<boolean>)}*/");
    assertTypeEquals(createUnionType(
        createTemplatizedType(
            ARRAY_TYPE, BOOLEAN_TYPE), NULL_TYPE), info.getType());
  }

  public void testParseUnionType6() throws Exception {
    JSDocInfo info = parse("@type {Array.<boolean>|null}*/");
    assertTypeEquals(createUnionType(
        createTemplatizedType(
            ARRAY_TYPE, BOOLEAN_TYPE), NULL_TYPE), info.getType());
  }

  public void testParseUnionType7() throws Exception {
    JSDocInfo info = parse("@type {null|Array.<boolean>}*/");
    assertTypeEquals(createUnionType(
        createTemplatizedType(
            ARRAY_TYPE, BOOLEAN_TYPE), NULL_TYPE), info.getType());
  }

  public void testParseUnionType8() throws Exception {
    JSDocInfo info = parse("@type {null||Array.<boolean>}*/");
    assertTypeEquals(createUnionType(
        createTemplatizedType(
            ARRAY_TYPE, BOOLEAN_TYPE), NULL_TYPE), info.getType());
  }

  public void testParseUnionType9() throws Exception {
    JSDocInfo info = parse("@type {Array.<boolean>||null}*/");
    assertTypeEquals(createUnionType(
        createTemplatizedType(
            ARRAY_TYPE, BOOLEAN_TYPE), NULL_TYPE), info.getType());
  }

  public void testParseUnionType10() throws Exception {
    parse("@type {string|}*/",
        "Bad type annotation. type not recognized due to syntax error");
  }

  public void testParseUnionType11() throws Exception {
    parse("@type {(string,)}*/",
        "Bad type annotation. type not recognized due to syntax error");
  }

  public void testParseUnionType12() throws Exception {
    parse("@type {()}*/",
        "Bad type annotation. type not recognized due to syntax error");
  }

  public void testParseUnionType13() throws Exception {
    testParseType(
        "(function(this:Date),function(this:String):number)",
        "Function");
  }

  public void testParseUnionType14() throws Exception {
    testParseType(
        "(function(...(function(number):boolean)):number)|" +
        "function(this:String, string):number",
        "Function");
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

  public void testParseUnionType19() throws Exception {
    JSDocInfo info = parse("@type {(?)} */");
    assertTypeEquals(UNKNOWN_TYPE, info.getType());
  }

  public void testParseUnionTypeError1() throws Exception {
    parse("@type {(string,|number)} */",
        "Bad type annotation. type not recognized due to syntax error");
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
    testParseType("function (...number): boolean");
  }

  public void testParseFunctionalType5() throws Exception {
    testParseType("function (number, ...string): boolean");
  }

  public void testParseFunctionalType6() throws Exception {
    testParseType(
        "function (this:Date, number): (boolean|number|string)");
  }

  public void testParseFunctionalType7() throws Exception {
    testParseType("function()", "function (): ?");
  }

  public void testParseFunctionalType9() throws Exception {
    testParseType(
        "function(this:Array,!Date,...(boolean?))",
        "function (this:Array, Date, ...(boolean|null)): ?");
  }

  public void testParseFunctionalType10() throws Exception {
    testParseType(
        "function(...(Object?)):boolean?",
        "function (...(Object|null)): (boolean|null)");
  }

  public void testParseFunctionalType12() throws Exception {
    testParseType(
        "function(...)",
        "function (...?): ?");
  }

  public void testParseFunctionalType13() throws Exception {
    testParseType(
        "function(...): void",
        "function (...?): undefined");
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
    testParseType(
        "function(...?): void",
        "function (...?): undefined");
  }

  public void testStructuralConstructor() throws Exception {
    JSType type = testParseType(
        "function (new:Object)", "function (new:Object): ?");
    assertThat(type.isConstructor()).isTrue();
    assertThat(type.isNominalConstructor()).isFalse();
  }

  public void testStructuralConstructor2() throws Exception {
    JSType type = testParseType(
        "function (new:?)",
        // toString skips unknowns, but isConstructor reveals the truth.
        "function (): ?");
    assertThat(type.isConstructor()).isTrue();
    assertThat(type.isNominalConstructor()).isFalse();
  }

  public void testStructuralConstructor3() throws Exception {
    resolve(parse("@type {function (new:*)} */").getType(),
        "constructed type must be an object type");
  }

  public void testNominalConstructor() throws Exception {
    ObjectType type = testParseType("Array", "(Array|null)").dereference();
    assertThat(type.getConstructor().isNominalConstructor()).isTrue();
  }

  public void testBug1419535() throws Exception {
    parse("@type {function(Object, string, *)?} */");
    parse("@type {function(Object, string, *)|null} */");
  }

  public void testIssue477() throws Exception {
    parse("@type function */",
        "Bad type annotation. missing opening (");
  }

  public void testMalformedThisAnnotation() throws Exception {
    parse("@this */",
        "Bad type annotation. type not recognized due to syntax error");
  }

  public void testParseFunctionalTypeError1() throws Exception {
    parse("@type {function number):string}*/",
        "Bad type annotation. missing opening (");
  }

  public void testParseFunctionalTypeError2() throws Exception {
    parse("@type {function( number}*/",
        "Bad type annotation. missing closing )");
  }

  public void testParseFunctionalTypeError3() throws Exception {
    parse("@type {function(...[number], string)}*/",
        "Bad type annotation. type not recognized due to syntax error");
  }

  public void testParseFunctionalTypeError4() throws Exception {
    parse("@type {function(string, ...number, boolean):string}*/",
        "Bad type annotation. variable length argument must be last");
  }

  public void testParseFunctionalTypeError5() throws Exception {
    parse("@type {function (thi:Array)}*/",
        "Bad type annotation. missing closing )");
  }

  public void testParseFunctionalTypeError6() throws Exception {
    resolve(parse("@type {function (this:number)}*/").getType(),
        "this type must be an object type");
  }

  public void testParseFunctionalTypeError7() throws Exception {
    parse("@type {function(...[number)}*/",
        "Bad type annotation. type not recognized due to syntax error");
  }

  public void testParseFunctionalTypeError8() throws Exception {
    parse("@type {function(...number])}*/",
        "Bad type annotation. missing closing )");
  }

  public void testParseFunctionalTypeError9() throws Exception {
    parse("@type {function (new:Array, this:Object)} */",
        "Bad type annotation. missing closing )");
  }

  public void testParseFunctionalTypeError10() throws Exception {
    parse("@type {function (this:Array, new:Object)} */",
        "Bad type annotation. missing closing )");
  }

  public void testParseFunctionalTypeError11() throws Exception {
    parse("@type {function (Array, new:Object)} */",
        "Bad type annotation. missing closing )");
  }

  public void testParseFunctionalTypeError12() throws Exception {
    resolve(parse("@type {function (new:number)}*/").getType(),
        "constructed type must be an object type");
  }

  public void testParseFunctionalTypeError13() throws Exception {
    parse("@type {function (...[number]): boolean} */",
        "Bad type annotation. type not recognized due to syntax error");
  }

  public void testParseFunctionalTypeError14() throws Exception {
    parse("@type {function (number, ...[string]): boolean} */",
        "Bad type annotation. type not recognized due to syntax error");
  }

  public void testParseFunctionalType8() throws Exception {
    parse("@type {function(this:Array,...[boolean])} */",
        "Bad type annotation. type not recognized due to syntax error");
  }

  public void testParseArrayTypeError1() throws Exception {
    parse("@type {[number}*/",
        "Bad type annotation. type not recognized due to syntax error");
  }

  public void testParseArrayTypeError2() throws Exception {
    parse("@type {number]}*/",
        "Bad type annotation. expected closing }");
  }

  public void testParseArrayTypeError3() throws Exception {
    parse("@type {[(number,boolean,Object?])]}*/",
        "Bad type annotation. type not recognized due to syntax error");
  }

  public void testParseArrayTypeError4() throws Exception {
    parse("@type {(number,boolean,[Object?)]}*/",
        "Bad type annotation. type not recognized due to syntax error");
  }

  public void testParseArrayTypeError5() throws Exception {
    parse("@type {[Object]}*/",
        "Bad type annotation. type not recognized due to syntax error");
  }

  private JSType testParseType(String type) throws Exception {
    return testParseType(type, type);
  }

  private JSType testParseType(
      String type, String typeExpected) throws Exception {
    JSDocInfo info = parse("@type {" + type + "}*/");

    assertThat(info).isNotNull();
    assertThat(info.hasType()).isTrue();

    JSType actual = resolve(info.getType());
    assertThat(actual.toString()).isEqualTo(typeExpected);
    return actual;
  }

  public void testParseNullableModifiers1() throws Exception {
    JSDocInfo info = parse("@type {string?}*/");
    assertTypeEquals(createNullableType(STRING_TYPE), info.getType());
  }

  public void testParseNullableModifiers2() throws Exception {
    JSDocInfo info = parse("@type {!Array.<string?>}*/");
    assertTypeEquals(
        createTemplatizedType(
            ARRAY_TYPE, createUnionType(STRING_TYPE, NULL_TYPE)),
        info.getType());
  }

  public void testParseNullableModifiers3() throws Exception {
    JSDocInfo info = parse("@type {Array.<boolean>?}*/");
    assertTypeEquals(
        createNullableType(createTemplatizedType(ARRAY_TYPE, BOOLEAN_TYPE)),
        info.getType());
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
            UNKNOWN_TYPE, registry.createOptionalParameters(NUMBER_TYPE)),
        info.getType());
  }

  public void testParseNewline1() throws Exception {
    JSDocInfo info = parse("@type {string\n* }\n*/");
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  public void testParseNewline2() throws Exception {
    JSDocInfo info = parse("@type !Array.<\n* number\n* > */");
    assertTypeEquals(
        createTemplatizedType(ARRAY_TYPE, NUMBER_TYPE), info.getType());
  }

  public void testParseNewline3() throws Exception {
    JSDocInfo info = parse("@type !Array.<(number,\n* null)>*/");
    assertTypeEquals(
        createTemplatizedType(
            ARRAY_TYPE, createUnionType(NUMBER_TYPE, NULL_TYPE)),
        info.getType());
  }

  public void testParseNewline4() throws Exception {
    JSDocInfo info = parse("@type !Array.<(number|\n* null)>*/");
    assertTypeEquals(
        createTemplatizedType(
            ARRAY_TYPE, createUnionType(NUMBER_TYPE, NULL_TYPE)),
        info.getType());
  }

  public void testParseNewline5() throws Exception {
    JSDocInfo info = parse("@type !Array.<function(\n* )\n* :\n* Date>*/");
    assertTypeEquals(
        createTemplatizedType(ARRAY_TYPE,
            registry.createFunctionType(
                createUnionType(DATE_TYPE, NULL_TYPE))),
        info.getType());
  }

  public void testParseReturnType1() throws Exception {
    JSDocInfo info =
        parse("@return {null|string|Array.<boolean>}*/");
    assertTypeEquals(
        createUnionType(createTemplatizedType(ARRAY_TYPE, BOOLEAN_TYPE),
            NULL_TYPE, STRING_TYPE),
        info.getReturnType());
  }

  public void testParseReturnType2() throws Exception {
    JSDocInfo info =
        parse("@returns {null|(string,Array.<boolean>)}*/");
    assertTypeEquals(
        createUnionType(createTemplatizedType(ARRAY_TYPE, BOOLEAN_TYPE),
            NULL_TYPE, STRING_TYPE),
        info.getReturnType());
  }

  public void testParseReturnType3() throws Exception {
    JSDocInfo info =
        parse("@return {((null||Array.<boolean>,string),boolean)}*/");
    assertTypeEquals(
        createUnionType(createTemplatizedType(ARRAY_TYPE, BOOLEAN_TYPE),
            NULL_TYPE, STRING_TYPE, BOOLEAN_TYPE),
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
        "Bad type annotation. type annotation incompatible " +
        "with other annotations");
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
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(NUMBER_TYPE, info.getParameterType("index"));
  }

  public void testParseParam2() throws Exception {
    JSDocInfo info = parse("@param index*/");
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertThat(info.getParameterType("index")).isNull();
  }

  public void testParseParam3() throws Exception {
    JSDocInfo info = parse("@param {number} index useful comments*/");
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(NUMBER_TYPE, info.getParameterType("index"));
  }

  public void testParseParam4() throws Exception {
    JSDocInfo info = parse("@param index useful comments*/");
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertThat(info.getParameterType("index")).isNull();
  }

  public void testParseParam5() throws Exception {
    // Test for multi-line @param.
    JSDocInfo info = parse("@param {number} \n index */");
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(NUMBER_TYPE, info.getParameterType("index"));
  }

  public void testParseParam6() throws Exception {
    // Test for multi-line @param.
    JSDocInfo info = parse("@param {number} \n * index */");
    assertThat(info.getParameterCount()).isEqualTo(1);
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
        "Bad type annotation. expected closing }",
        "Bad type annotation. expecting a variable name in a @param tag");
  }

  public void testParseParam10() throws Exception {
    parse("@param {...number index */",
        "Bad type annotation. expected closing }");
  }

  public void testParseParam11() throws Exception {
    parse("@param {number= index */",
        "Bad type annotation. expected closing }");
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
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(registry.createOptionalType(STRING_TYPE), info.getParameterType("index"));
  }

  public void testParseParam15() throws Exception {
    JSDocInfo info = parse("@param {string} [index */",
        "Bad type annotation. missing closing ]");
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(STRING_TYPE, info.getParameterType("index"));
  }

  public void testParseParam16() throws Exception {
    JSDocInfo info = parse("@param {string} index] */");
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(STRING_TYPE, info.getParameterType("index"));
  }

  public void testParseParam17() throws Exception {
    JSDocInfo info = parse("@param {string=} [index] */");
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(registry.createOptionalType(STRING_TYPE), info.getParameterType("index"));
  }

  public void testParseParam18() throws Exception {
    JSDocInfo info = parse("@param {...string} [index] */");
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(registry.createOptionalType(STRING_TYPE), info.getParameterType("index"));
  }

  public void testParseParam19() throws Exception {
    JSDocInfo info = parse("@param {...} [index] */");
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(registry.createOptionalType(UNKNOWN_TYPE), info.getParameterType("index"));
    assertThat(info.getParameterType("index").isVarArgs()).isTrue();
  }

  public void testParseParam20() throws Exception {
    JSDocInfo info = parse("@param {?=} index */");
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(UNKNOWN_TYPE, info.getParameterType("index"));
  }

  public void testParseParam21() throws Exception {
    JSDocInfo info = parse("@param {...?} index */");
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(UNKNOWN_TYPE, info.getParameterType("index"));
    assertThat(info.getParameterType("index").isVarArgs()).isTrue();
  }

  public void testParseParam22() throws Exception {
    JSDocInfo info = parse("@param {string} .index */", "invalid param name \".index\"");
    assertThat(info).isNull();
  }

  public void testParseParam23() throws Exception {
    JSDocInfo info = parse("@param {string} index. */", "invalid param name \"index.\"");
    assertThat(info).isNull();
  }

  public void testParseParam24() throws Exception {
    JSDocInfo info = parse("@param {string} foo.bar */", "invalid param name \"foo.bar\"");
    assertThat(info).isNull();
  }

  public void testParseParam25() throws Exception {
    JSDocInfo info = parse(
        "@param {string} foo.bar\n * @param {string} baz */", "invalid param name \"foo.bar\"");
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(STRING_TYPE, info.getParameterType("baz"));
  }

  public void testParseThrows1() throws Exception {
    JSDocInfo info = parse("@throws {number} Some number */");
    assertThat(info.getThrownTypes()).hasSize(1);
    assertTypeEquals(NUMBER_TYPE, info.getThrownTypes().get(0));
  }

  public void testParseThrows2() throws Exception {
    JSDocInfo info = parse("@throws {number} Some number\n "
                           + "*@throws {String} A string */");
    assertThat(info.getThrownTypes()).hasSize(2);
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
              "Bad type annotation. expected closing }");
  }

  public void testParseRecordType12() throws Exception {
    parseFull("/** @param {{!hello : hey}} n\n*/",
              "Bad type annotation. type not recognized due to syntax error");
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
        "Bad type annotation. expecting a variable name in a @param tag");
  }

  public void testParseParamError2() throws Exception {
    parseFull("/** @param {Number}*/",
        "Bad type annotation. expecting a variable name in a @param tag");
  }

  public void testParseParamError3() throws Exception {
    parseFull("/** @param {Number}\n*/",
        "Bad type annotation. expecting a variable name in a @param tag");
  }

  public void testParseParamError4() throws Exception {
    parseFull("/** @param {Number}\n* * num */",
        "Bad type annotation. expecting a variable name in a @param tag");
  }

  public void testParseParamError5() throws Exception {
    parse("@param {number} x \n * @param {string} x */",
        "Bad type annotation. duplicate variable name \"x\"");
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
    // For types that are not templatized, <> annotations are ignored.
    List<JSTypeExpression> interfaces =
        parse("@implements {SomeInterface.<*>} */")
        .getImplementedInterfaces();
    assertThat(interfaces).hasSize(1);
    assertTypeEquals(registry.createNamedType("SomeInterface", null, -1, -1), interfaces.get(0));
  }

  public void testParseExtends4() throws Exception {
    assertTypeEquals(STRING_OBJECT_TYPE,
        parse("@extends {String}*/").getBaseType());
  }

  public void testParseExtends5() throws Exception {
    assertTypeEquals(STRING_OBJECT_TYPE,
        parse("@extends {String*/",
              "Bad type annotation. expected closing }").getBaseType());
  }

  public void testParseExtends6() throws Exception {
    // Multi-line extends
    assertTypeEquals(STRING_OBJECT_TYPE,
        parse("@extends \n * {String}*/").getBaseType());
  }

  public void testParseExtendsInvalidName() throws Exception {
    // This looks bad, but for the time being it should be OK, as
    // we will not find a type with this name in the JS parsed tree.
    // If this is fixed in the future, change this test to check for a
    // warning/error message.
    assertTypeEquals(
        registry.createNamedType("some_++#%$%_UglyString", null, -1, -1),
        parse("@extends {some_++#%$%_UglyString} */").getBaseType());
  }

  public void testParseExtendsNullable1() throws Exception {
    parse("@extends {Base?} */", "Bad type annotation. expected closing }");
  }

  public void testParseExtendsNullable2() throws Exception {
    parse("@extends Base? */",
        "Bad type annotation. expected end of line or comment");
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
    assertThat(parse("@desc hello world!*/").getDescription()).isEqualTo("hello world!");
  }

  public void testParseDesc2() throws Exception {
    assertThat(parse("@desc hello world!\n*/").getDescription()).isEqualTo("hello world!");
  }

  public void testParseDesc3() throws Exception {
    assertThat(parse("@desc*/").getDescription()).isEmpty();
  }

  public void testParseDesc4() throws Exception {
    assertThat(parse("@desc\n*/").getDescription()).isEmpty();
  }

  public void testParseDesc5() throws Exception {
    assertThat(parse("@desc hello\nworld!\n*/").getDescription()).isEqualTo("hello world!");
  }

  public void testParseDesc6() throws Exception {
    assertThat(parse("@desc hello\n* world!\n*/").getDescription()).isEqualTo("hello world!");
  }

  public void testParseDesc7() throws Exception {
    assertThat(parse("@desc a\n\nb\nc*/").getDescription()).isEqualTo("a b c");
  }

  public void testParseDesc8() throws Exception {
    assertThat(parse("@desc a\n      *b\n\n  *c\n\nd*/").getDescription()).isEqualTo("a b c d");
  }

  public void testParseDesc9() throws Exception {
    String comment = "@desc\n.\n,\n{\n)\n}\n|\n.<\n>\n<\n?\n~\n+\n-\n;\n:\n*/";

    assertThat(parse(comment).getDescription()).isEqualTo(". , { ) } | < > < ? ~ + - ; :");
  }

  public void testParseDesc10() throws Exception {
    String comment = "@desc\n?\n?\n?\n?*/";

    assertThat(parse(comment).getDescription()).isEqualTo("? ? ? ?");
  }

  public void testParseDesc11() throws Exception {
    String comment = "@desc :[]*/";

    assertThat(parse(comment).getDescription()).isEqualTo(":[]");
  }

  public void testParseDesc12() throws Exception {
    String comment = "@desc\n:\n[\n]\n...*/";

    assertThat(parse(comment).getDescription()).isEqualTo(": [ ] ...");
  }

  public void testParseMeaning1() throws Exception {
    assertThat(parse("@meaning tigers   */").getMeaning()).isEqualTo("tigers");
  }

  public void testParseMeaning2() throws Exception {
    assertThat(parse("@meaning tigers\n * and lions\n * and bears */").getMeaning())
        .isEqualTo("tigers and lions and bears");
  }

  public void testParseMeaning3() throws Exception {
    JSDocInfo info =
        parse("@meaning  tigers\n * and lions\n * @desc  and bears */");
    assertThat(info.getMeaning()).isEqualTo("tigers and lions");
    assertThat(info.getDescription()).isEqualTo("and bears");
  }

  public void testParseMeaning4() throws Exception {
    parse("@meaning  tigers\n * @meaning and lions  */",
        "extra @meaning tag");
  }

  public void testParseLends1() throws Exception {
    JSDocInfo info = parse("@lends {name} */");
    assertThat(info.getLendsName()).isEqualTo("name");
  }

  public void testParseLends2() throws Exception {
    JSDocInfo info = parse("@lends   foo.bar  */");
    assertThat(info.getLendsName()).isEqualTo("foo.bar");
  }

  public void testParseLends3() throws Exception {
    parse("@lends {name */", "Bad type annotation. expected closing }");
  }

  public void testParseLends4() throws Exception {
    parse("@lends {} */",
        "Bad type annotation. missing object name in @lends tag");
  }

  public void testParseLends5() throws Exception {
    parse("@lends } */",
        "Bad type annotation. missing object name in @lends tag");
  }

  public void testParseLends6() throws Exception {
    parse("@lends {string} \n * @lends {string} */",
        "Bad type annotation. @lends tag incompatible with other annotations");
  }

  public void testParseLends7() throws Exception {
    parse("@type {string} \n * @lends {string} */",
        "Bad type annotation. @lends tag incompatible with other annotations");
  }

  public void testStackedAnnotation() throws Exception {
    JSDocInfo info = parse("@const @type {string}*/");
    assertThat(info.isConstant()).isTrue();
    assertThat(info.hasType()).isTrue();
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  public void testStackedAnnotation2() throws Exception {
    JSDocInfo info = parse("@type {string} @const */");
    assertThat(info.isConstant()).isTrue();
    assertThat(info.hasType()).isTrue();
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  public void testStackedAnnotation3() throws Exception {
    JSDocInfo info = parse("@const @see {string}*/");
    assertThat(info.isConstant()).isTrue();
    assertThat(info.hasType()).isFalse();
  }

  public void testStackedAnnotation4() throws Exception {
    JSDocInfo info = parse("@constructor @extends {Foo} @implements {Bar}*/");
    assertThat(info.isConstructor()).isTrue();
    assertThat(info.hasBaseType()).isTrue();
    assertThat(info.getImplementedInterfaceCount()).isEqualTo(1);
  }

  public void testStackedAnnotation5() throws Exception {
    JSDocInfo info = parse("@param {number} x @constructor */");
    assertThat(info.hasParameterType("x")).isTrue();
    assertThat(info.isConstructor()).isTrue();
  }

  public void testStackedAnnotation6() throws Exception {
    JSDocInfo info = parse("@return {number} @constructor */", true);
    assertThat(info.hasReturnType()).isTrue();
    assertThat(info.isConstructor()).isTrue();

    info = parse("@return {number} @constructor */", false);
    assertThat(info.hasReturnType()).isTrue();
    assertThat(info.isConstructor()).isTrue();
  }

  public void testStackedAnnotation7() throws Exception {
    JSDocInfo info = parse("@return @constructor */");
    assertThat(info.hasReturnType()).isTrue();
    assertThat(info.isConstructor()).isTrue();
  }

  public void testStackedAnnotation8() throws Exception {
    JSDocInfo info = parse("@throws {number} @constructor */", true);
    assertThat(info.getThrownTypes()).isNotEmpty();
    assertThat(info.isConstructor()).isTrue();

    info = parse("@return {number} @constructor */", false);
    assertThat(info.hasReturnType()).isTrue();
    assertThat(info.isConstructor()).isTrue();
  }

  public void testStackedAnnotation9() throws Exception {
    JSDocInfo info = parse("@const @private {string} */", true);
    assertThat(info.isConstant()).isTrue();
    assertThat(info.hasType()).isTrue();
    assertTypeEquals(STRING_TYPE, info.getType());
  }

  public void testStackedAnnotation10() throws Exception {
    JSDocInfo info = parse("@private @const {string} */", true);
    assertThat(info.getVisibility()).isEqualTo(Visibility.PRIVATE);
    assertThat(info.hasType()).isTrue();
    assertTypeEquals(STRING_TYPE, info.getType());
    assertThat(info.isConstant()).isTrue();
  }

  public void testStackedAnnotation11() throws Exception {
    JSDocInfo info = parse("@private @const */", true);
    assertThat(info.hasType()).isFalse();
    assertThat(info.getVisibility()).isEqualTo(Visibility.PRIVATE);
    assertThat(info.isConstant()).isTrue();
  }

  public void testStackedAnnotation12() throws Exception {
    JSDocInfo info = parse("@const @private */", true);
    assertThat(info.hasType()).isFalse();
    assertThat(info.getVisibility()).isEqualTo(Visibility.PRIVATE);
    assertThat(info.isConstant()).isTrue();
  }

  public void testParsePreserve() throws Exception {
    Node node = new Node(1);
    this.fileLevelJsDocBuilder = node.getJsDocBuilderForNode();
    String comment = "@preserve Foo\nBar\n\nBaz*/";
    parse(comment);
    assertThat(node.getJSDocInfo().getLicense()).isEqualTo(" Foo\nBar\n\nBaz");
  }

  public void testParseLicense() throws Exception {
    Node node = new Node(1);
    this.fileLevelJsDocBuilder = node.getJsDocBuilderForNode();
    String comment = "@license Foo\nBar\n\nBaz*/";
    parse(comment);
    assertThat(node.getJSDocInfo().getLicense()).isEqualTo(" Foo\nBar\n\nBaz");
  }

  public void testParseLicenseAscii() throws Exception {
    Node node = new Node(1);
    this.fileLevelJsDocBuilder = node.getJsDocBuilderForNode();
    String comment = "@license Foo\n *   Bar\n\n  Baz*/";
    parse(comment);
    assertThat(node.getJSDocInfo().getLicense()).isEqualTo(" Foo\n   Bar\n\n  Baz");
  }

  public void testParseLicenseWithAnnotation() throws Exception {
    Node node = new Node(1);
    this.fileLevelJsDocBuilder = node.getJsDocBuilderForNode();
    String comment = "@license Foo \n * @author Charlie Brown */";
    parse(comment);
    assertThat(node.getJSDocInfo().getLicense()).isEqualTo(" Foo \n @author Charlie Brown ");
  }

  public void testParseDefine1() throws Exception {
    assertTypeEquals(STRING_TYPE,
        parse("@define {string}*/").getType());
  }

  public void testParseDefine2() throws Exception {
    assertTypeEquals(STRING_TYPE,
        parse("@define {string*/",
              "Bad type annotation. expected closing }").getType());
  }

  public void testParseDefine3() throws Exception {
    JSDocInfo info = parse("@define {boolean}*/");
    assertThat(info.isConstant()).isTrue();
    assertThat(info.isDefine()).isTrue();
    assertTypeEquals(BOOLEAN_TYPE, info.getType());
  }

  public void testParseDefine4() throws Exception {
    assertTypeEquals(NUMBER_TYPE, parse("@define {number}*/").getType());
  }

  public void testParseDefine5() throws Exception {
    assertTypeEquals(createUnionType(NUMBER_TYPE, BOOLEAN_TYPE),
        parse("@define {number|boolean}*/").getType());
  }

  public void testParseDefineDescription() throws Exception {
    JSDocInfo doc = parse(
        "@define {string} description of element \n next line*/", true);
    Marker defineMarker = doc.getMarkers().iterator().next();
    assertThat(defineMarker.getAnnotation().getItem()).isEqualTo("define");
    assertThat(defineMarker.getDescription().getItem()).contains("description of element");
    assertThat(defineMarker.getDescription().getItem()).contains("next line");
  }

  public void testParsePrivateDescription() throws Exception {
    JSDocInfo doc =
        parse("@private {string} description \n next line*/", true);
    Marker defineMarker = doc.getMarkers().iterator().next();
    assertThat(defineMarker.getAnnotation().getItem()).isEqualTo("private");
    assertThat(defineMarker.getDescription().getItem()).contains("description ");
    assertThat(defineMarker.getDescription().getItem()).contains("next line");
  }

  public void testParsePackagePrivateDescription() throws Exception {
    JSDocInfo doc = parse("@package {string} description \n next line */", true);
    Marker defineMarker = doc.getMarkers().iterator().next();
    assertThat(defineMarker.getAnnotation().getItem()).isEqualTo("package");
    assertThat(defineMarker.getDescription().getItem()).contains("description ");
    assertThat(defineMarker.getDescription().getItem()).contains("next line");
  }

  public void testParseProtectedDescription() throws Exception {
    JSDocInfo doc =
        parse("@protected {string} description \n next line*/", true);
    Marker defineMarker = doc.getMarkers().iterator().next();
    assertThat(defineMarker.getAnnotation().getItem()).isEqualTo("protected");
    assertThat(defineMarker.getDescription().getItem()).contains("description ");
    assertThat(defineMarker.getDescription().getItem()).contains("next line");
  }

  public void testParseDefineErrors1() throws Exception {
    parse("@enum {string}\n @define {string} */", "conflicting @define tag");
  }

  public void testParseDefineErrors2() throws Exception {
    parse("@define {string}\n @enum {string} */",
        "Bad type annotation. " +
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
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testParseOverride1() throws Exception {
    assertThat(parse("@override*/").isOverride()).isTrue();
  }

  public void testParseOverride2() throws Exception {
    parse("@override\n@override*/",
        "Bad type annotation. extra @override/@inheritDoc tag");
  }

  public void testParseInheritDoc1() throws Exception {
    assertThat(parse("@inheritDoc*/").isOverride()).isTrue();
  }

  public void testParseInheritDoc2() throws Exception {
    parse("@override\n@inheritDoc*/",
        "Bad type annotation. extra @override/@inheritDoc tag");
  }

  public void testParseInheritDoc3() throws Exception {
    parse("@inheritDoc\n@inheritDoc*/",
        "Bad type annotation. extra @override/@inheritDoc tag");
  }

  public void testParseNoAlias1() throws Exception {
    assertThat(parse("@noalias*/").isNoAlias()).isTrue();
  }

  public void testParseNoAlias2() throws Exception {
    parse("@noalias\n@noalias*/", "extra @noalias tag");
  }

  public void testParseDeprecated1() throws Exception {
    assertThat(parse("@deprecated*/").isDeprecated()).isTrue();
  }

  public void testParseDeprecated2() throws Exception {
    parse("@deprecated\n@deprecated*/", "extra @deprecated tag");
  }

  public void testParseExport1() throws Exception {
    assertThat(parse("@export*/").isExport()).isTrue();
  }

  public void testParseExport2() throws Exception {
    parse("@export\n@export*/", "extra @export tag");
  }

  public void testParseExpose1() throws Exception {
    assertThat(parse("@expose*/").isExpose()).isTrue();
  }

  public void testParseExpose2() throws Exception {
    parse("@expose\n@expose*/", "extra @expose tag");
  }

  public void testParseExterns1() throws Exception {
    assertThat(parseFileOverview("@externs*/").isExterns()).isTrue();
  }

  public void testParseExterns2() throws Exception {
    parseFileOverview("@externs\n@externs*/", "extra @externs tag");
  }

  public void testParseExterns3() throws Exception {
    assertThat(parse("@externs*/")).isNull();
  }

  public void testParseNoCompile1() throws Exception {
    assertThat(parseFileOverview("@nocompile*/").isNoCompile()).isTrue();
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
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(NUMBER_TYPE, info.getParameterType("index"));
    assertTypeEquals(BOOLEAN_TYPE, info.getReturnType());
    assertThat(info.getVisibility()).isEqualTo(Visibility.PRIVATE);
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
    assertThat(info.getParameterCount()).isEqualTo(2);
    assertTypeEquals(NUMBER_TYPE, info.getParameterType("index"));
    assertThat(info.getParameterType("name")).isNull();
    assertTypeEquals(BOOLEAN_TYPE, info.getReturnType());
    assertThat(info.getVisibility()).isEqualTo(Visibility.PROTECTED);
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
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertThat(info.getParameterType("mediaTag")).isNull();
    assertThat(info.getVisibility()).isEqualTo(Visibility.PUBLIC);
    assertThat(info.isConstant()).isTrue();
  }

  public void testRegression4() throws Exception {
    String comment =
        " * @const\n" +
        " * @hidden\n" +
        " * @preserveTry\n" +
        " * @constructor\n" +
        " */";

    JSDocInfo info = parse(comment);
    assertThat(info.isConstant()).isTrue();
    assertThat(info.isDefine()).isFalse();
    assertThat(info.isConstructor()).isTrue();
    assertThat(info.isHidden()).isTrue();
    assertThat(info.shouldPreserveTry()).isTrue();
  }

  public void testRegression5() throws Exception {
    String comment = "@const\n@enum {string}\n@public*/";

    JSDocInfo info = parse(comment);
    assertThat(info.isConstant()).isTrue();
    assertThat(info.isDefine()).isFalse();
    assertTypeEquals(STRING_TYPE, info.getEnumParameterType());
    assertThat(info.getVisibility()).isEqualTo(Visibility.PUBLIC);
  }

  public void testRegression6() throws Exception {
    String comment = "@hidden\n@enum\n@public*/";

    JSDocInfo info = parse(comment);
    assertThat(info.isHidden()).isTrue();
    assertTypeEquals(NUMBER_TYPE, info.getEnumParameterType());
    assertThat(info.getVisibility()).isEqualTo(Visibility.PUBLIC);
  }

  public void testRegression7() throws Exception {
    String comment =
        " * @desc description here\n" +
        " * @param {boolean} flag and some more description\n" +
        " *     nicely formatted\n" +
        " */";

    JSDocInfo info = parse(comment);
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(BOOLEAN_TYPE, info.getParameterType("flag"));
    assertThat(info.getDescription()).isEqualTo("description here");
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
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(BOOLEAN_TYPE, info.getParameterType("flag"));
    assertThat(info.getDescription()).isEqualTo("description here");
  }

  public void testRegression9() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @param {string} p0 blah blah blah\n" +
        " */");

    assertThat(jsdoc.getBaseType()).isNull();
    assertThat(jsdoc.isConstant()).isFalse();
    assertThat(jsdoc.getDescription()).isNull();
    assertThat(jsdoc.getEnumParameterType()).isNull();
    assertThat(jsdoc.isHidden()).isFalse();
    assertThat(jsdoc.getParameterCount()).isEqualTo(1);
    assertTypeEquals(STRING_TYPE, jsdoc.getParameterType("p0"));
    assertThat(jsdoc.getReturnType()).isNull();
    assertThat(jsdoc.getType()).isNull();
    assertThat(jsdoc.getVisibility()).isEqualTo(Visibility.INHERITED);
  }

  public void testRegression10() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @param {!String} p0 blah blah blah\n" +
        " * @param {boolean} p1 fobar\n" +
        " * @return {!Date} jksjkash dshad\n" +
        " */");

    assertThat(jsdoc.getBaseType()).isNull();
    assertThat(jsdoc.isConstant()).isFalse();
    assertThat(jsdoc.getDescription()).isNull();
    assertThat(jsdoc.getEnumParameterType()).isNull();
    assertThat(jsdoc.isHidden()).isFalse();
    assertThat(jsdoc.getParameterCount()).isEqualTo(2);
    assertTypeEquals(STRING_OBJECT_TYPE, jsdoc.getParameterType("p0"));
    assertTypeEquals(BOOLEAN_TYPE, jsdoc.getParameterType("p1"));
    assertTypeEquals(DATE_TYPE, jsdoc.getReturnType());
    assertThat(jsdoc.getType()).isNull();
    assertThat(jsdoc.getVisibility()).isEqualTo(Visibility.INHERITED);
  }

  public void testRegression11() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @constructor\n" +
        " */");

    assertThat(jsdoc.getBaseType()).isNull();
    assertThat(jsdoc.isConstant()).isFalse();
    assertThat(jsdoc.getDescription()).isNull();
    assertThat(jsdoc.getEnumParameterType()).isNull();
    assertThat(jsdoc.isHidden()).isFalse();
    assertThat(jsdoc.getParameterCount()).isEqualTo(0);
    assertThat(jsdoc.getReturnType()).isNull();
    assertThat(jsdoc.getType()).isNull();
    assertThat(jsdoc.getVisibility()).isEqualTo(Visibility.INHERITED);
  }

  public void testRegression12() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @extends FooBar\n" +
        " */");

    assertTypeEquals(registry.createNamedType("FooBar", null, 0, 0),
        jsdoc.getBaseType());
    assertThat(jsdoc.isConstant()).isFalse();
    assertThat(jsdoc.getDescription()).isNull();
    assertThat(jsdoc.getEnumParameterType()).isNull();
    assertThat(jsdoc.isHidden()).isFalse();
    assertThat(jsdoc.getParameterCount()).isEqualTo(0);
    assertThat(jsdoc.getReturnType()).isNull();
    assertThat(jsdoc.getType()).isNull();
    assertThat(jsdoc.getVisibility()).isEqualTo(Visibility.INHERITED);
  }

  public void testRegression13() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @type {!RegExp}\n" +
        " * @protected\n" +
        " */");

    assertThat(jsdoc.getBaseType()).isNull();
    assertThat(jsdoc.isConstant()).isFalse();
    assertThat(jsdoc.getDescription()).isNull();
    assertThat(jsdoc.getEnumParameterType()).isNull();
    assertThat(jsdoc.isHidden()).isFalse();
    assertThat(jsdoc.getParameterCount()).isEqualTo(0);
    assertThat(jsdoc.getReturnType()).isNull();
    assertTypeEquals(REGEXP_TYPE, jsdoc.getType());
    assertThat(jsdoc.getVisibility()).isEqualTo(Visibility.PROTECTED);
  }

  public void testRegression14() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @const\n" +
        " * @private\n" +
        " */");

    assertThat(jsdoc.getBaseType()).isNull();
    assertThat(jsdoc.isConstant()).isTrue();
    assertThat(jsdoc.getDescription()).isNull();
    assertThat(jsdoc.getEnumParameterType()).isNull();
    assertThat(jsdoc.isHidden()).isFalse();
    assertThat(jsdoc.getParameterCount()).isEqualTo(0);
    assertThat(jsdoc.getReturnType()).isNull();
    assertThat(jsdoc.getType()).isNull();
    assertThat(jsdoc.getVisibility()).isEqualTo(Visibility.PRIVATE);
  }

  public void testRegression15() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @desc Hello,\n" +
        " * World!\n" +
        " */");

    assertThat(jsdoc.getBaseType()).isNull();
    assertThat(jsdoc.isConstant()).isFalse();
    assertThat(jsdoc.getDescription()).isEqualTo("Hello, World!");
    assertThat(jsdoc.getEnumParameterType()).isNull();
    assertThat(jsdoc.isHidden()).isFalse();
    assertThat(jsdoc.getParameterCount()).isEqualTo(0);
    assertThat(jsdoc.getReturnType()).isNull();
    assertThat(jsdoc.getType()).isNull();
    assertThat(jsdoc.getVisibility()).isEqualTo(Visibility.INHERITED);
    assertThat(jsdoc.isExport()).isFalse();
  }

  public void testRegression16() throws Exception {
    JSDocInfo jsdoc = parse(
        " Email is plp@foo.bar\n" +
        " @type {string}\n" +
        " */");

    assertThat(jsdoc.getBaseType()).isNull();
    assertThat(jsdoc.isConstant()).isFalse();
    assertTypeEquals(STRING_TYPE, jsdoc.getType());
    assertThat(jsdoc.isHidden()).isFalse();
    assertThat(jsdoc.getParameterCount()).isEqualTo(0);
    assertThat(jsdoc.getReturnType()).isNull();
    assertThat(jsdoc.getVisibility()).isEqualTo(Visibility.INHERITED);
  }

  public void testRegression17() throws Exception {
    // verifying that if no @desc is present the description is empty
    assertThat(parse("@private*/").getDescription()).isNull();
  }

  public void testFullRegression1() throws Exception {
    parseFull("/** @param (string,number) foo*/function bar(foo){}",
        "Bad type annotation. expecting a variable name in a @param tag");
  }

  public void testFullRegression2() throws Exception {
    parseFull("/** @param {string,number) foo*/function bar(foo){}",
        "Bad type annotation. expected closing }",
        "Bad type annotation. expecting a variable name in a @param tag");
  }

  public void testFullRegression3() throws Exception {
    parseFull("/**..\n*/");
  }

  public void testBug907488() throws Exception {
    parse("@type {number,null} */",
        "Bad type annotation. expected closing }");
  }

  public void testBug907494() throws Exception {
    parse("@return {Object,undefined} */",
        "Bad type annotation. expected closing }");
  }

  public void testBug909468() throws Exception {
    parse("@extends {(x)}*/",
        "Bad type annotation. expecting a type name");
  }

  public void testParseInterface() throws Exception {
    assertThat(parse("@interface*/").isInterface()).isTrue();
  }

  public void testParseImplicitCast1() throws Exception {
    assertThat(parse("@type {string} \n * @implicitCast*/").isImplicitCast()).isTrue();
  }

  public void testParseImplicitCast2() throws Exception {
    assertThat(parse("@type {string}*/").isImplicitCast()).isFalse();
  }

  public void testParseDuplicateImplicitCast() throws Exception {
    parse("@type {string} \n * @implicitCast \n * @implicitCast*/",
          "Bad type annotation. extra @implicitCast tag");
  }

  public void testParseInterfaceDoubled() throws Exception {
    parse(
        "* @interface\n" +
        "* @interface\n" +
        "*/",
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testParseImplements() throws Exception {
    List<JSTypeExpression> interfaces = parse("@implements {SomeInterface}*/")
        .getImplementedInterfaces();
    assertThat(interfaces).hasSize(1);
    assertTypeEquals(registry.createNamedType("SomeInterface", null, -1, -1), interfaces.get(0));
  }

  public void testParseImplementsTwo() throws Exception {
    List<JSTypeExpression> interfaces =
        parse(
            "* @implements {SomeInterface1}\n" +
            "* @implements {SomeInterface2}\n" +
            "*/")
        .getImplementedInterfaces();
    assertThat(interfaces).hasSize(2);
    assertTypeEquals(registry.createNamedType("SomeInterface1", null, -1, -1), interfaces.get(0));
    assertTypeEquals(registry.createNamedType("SomeInterface2", null, -1, -1),
        interfaces.get(1));
  }

  public void testParseImplementsSameTwice() throws Exception {
    parse(
        "* @implements {Smth}\n" +
        "* @implements {Smth}\n" +
        "*/",
        "Bad type annotation. duplicate @implements tag");
  }

  public void testParseImplementsNoName() throws Exception {
    parse("* @implements {} */",
        "Bad type annotation. expecting a type name");
  }

  public void testParseImplementsMissingRC() throws Exception {
    parse("* @implements {Smth */",
        "Bad type annotation. expected closing }");
  }

  public void testParseImplementsNullable1() throws Exception {
    parse("@implements {Base?} */", "Bad type annotation. expected closing }");
  }

  public void testParseImplementsNullable2() throws Exception {
    parse("@implements Base? */",
        "Bad type annotation. expected end of line or comment");
  }

  public void testInterfaceExtends() throws Exception {
     JSDocInfo jsdoc = parse(
         " * @interface \n" +
         " * @extends {Extended} */");
     assertThat(jsdoc.isInterface()).isTrue();
     assertThat(jsdoc.getExtendedInterfacesCount()).isEqualTo(1);
     List<JSTypeExpression> types = jsdoc.getExtendedInterfaces();
    assertTypeEquals(registry.createNamedType("Extended", null, -1, -1),
        types.get(0));
  }

  public void testInterfaceMultiExtends1() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @interface \n" +
        " * @extends {Extended1} \n" +
        " * @extends {Extended2} */");
    assertThat(jsdoc.isInterface()).isTrue();
    assertThat(jsdoc.getBaseType()).isNull();
    assertThat(jsdoc.getExtendedInterfacesCount()).isEqualTo(2);
    List<JSTypeExpression> types = jsdoc.getExtendedInterfaces();
    assertTypeEquals(registry.createNamedType("Extended1", null, -1, -1),
       types.get(0));
    assertTypeEquals(registry.createNamedType("Extended2", null, -1, -1),
        types.get(1));
  }

  public void testInterfaceMultiExtends2() throws Exception {
    JSDocInfo jsdoc = parse(
        " * @extends {Extended1} \n" +
        " * @interface \n" +
        " * @extends {Extended2} \n" +
        " * @extends {Extended3} */");
    assertThat(jsdoc.isInterface()).isTrue();
    assertThat(jsdoc.getBaseType()).isNull();
    assertThat(jsdoc.getExtendedInterfacesCount()).isEqualTo(3);
    List<JSTypeExpression> types = jsdoc.getExtendedInterfaces();
    assertTypeEquals(registry.createNamedType("Extended1", null, -1, -1),
       types.get(0));
    assertTypeEquals(registry.createNamedType("Extended2", null, -1, -1),
        types.get(1));
    assertTypeEquals(registry.createNamedType("Extended3", null, -1, -1),
        types.get(2));
  }

  public void testBadClassMultiExtends() throws Exception {
    parse(" * @extends {Extended1} \n" +
        " * @constructor \n" +
        " * @extends {Extended2} */",
        "Bad type annotation. type annotation incompatible with other " +
        "annotations");
  }

  public void testBadExtendsWithNullable() throws Exception {
    JSDocInfo jsdoc = parse("@constructor\n * @extends {Object?} */",
        "Bad type annotation. expected closing }");
    assertThat(jsdoc.isConstructor()).isTrue();
    assertTypeEquals(OBJECT_TYPE, jsdoc.getBaseType());
  }

  public void testBadImplementsWithNullable() throws Exception {
  JSDocInfo jsdoc = parse("@implements {Disposable?}\n * @constructor */",
      "Bad type annotation. expected closing }");
  assertThat(jsdoc.isConstructor()).isTrue();
  assertTypeEquals(
      registry.createNamedType("Disposable", null, -1, -1),
      jsdoc.getImplementedInterfaces().get(0));
  }

  public void testBadTypeDefInterfaceAndConstructor1() throws Exception {
    JSDocInfo jsdoc = parse("@interface\n@constructor*/",
        "Bad type annotation. cannot be both an interface and a constructor");
    assertThat(jsdoc.isInterface()).isTrue();
  }

  public void testBadTypeDefInterfaceAndConstructor2() throws Exception {
    JSDocInfo jsdoc = parse("@constructor\n@interface*/",
        "Bad type annotation. cannot be both an interface and a constructor");
    assertThat(jsdoc.isConstructor()).isTrue();
  }

  public void testDocumentationParameter() throws Exception {
    JSDocInfo jsdoc
        = parse("@param {Number} number42 This is a description.*/", true);

    assertThat(jsdoc.hasDescriptionForParameter("number42")).isTrue();
    assertThat(jsdoc.getDescriptionForParameter("number42")).isEqualTo("This is a description.");
  }

  public void testMultilineDocumentationParameter() throws Exception {
    JSDocInfo jsdoc
        = parse("@param {Number} number42 This is a description"
                + "\n* on multiple \n* lines.*/", true);

    assertThat(jsdoc.hasDescriptionForParameter("number42")).isTrue();
    assertThat(jsdoc.getDescriptionForParameter("number42"))
        .isEqualTo("This is a description on multiple lines.");
  }

  public void testDocumentationMultipleParameter() throws Exception {
    JSDocInfo jsdoc
        = parse("@param {Number} number42 This is a description."
                + "\n* @param {Integer} number87 This is another description.*/"
                , true);

    assertThat(jsdoc.hasDescriptionForParameter("number42")).isTrue();
    assertThat(jsdoc.getDescriptionForParameter("number42")).isEqualTo("This is a description.");

    assertThat(jsdoc.hasDescriptionForParameter("number87")).isTrue();
    assertThat(jsdoc.getDescriptionForParameter("number87"))
        .isEqualTo("This is another description.");
  }

  public void testDocumentationMultipleParameter2() throws Exception {
    JSDocInfo jsdoc
        = parse("@param {number} delta = 0 results in a redraw\n" +
                "  != 0 ..... */", true);
    assertThat(jsdoc.hasDescriptionForParameter("delta")).isTrue();
    assertThat(jsdoc.getDescriptionForParameter("delta"))
        .isEqualTo("= 0 results in a redraw != 0 .....");
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

    assertThat(authors).isNotNull();
    assertThat(authors).hasSize(3);

    assertContains(authors, "a@google.com (A Person)");
    assertContains(authors, "b@google.com (B Person)");
    assertContains(authors, "c@google.com (C Person)");
  }

  public void testSuppress1() throws Exception {
    JSDocInfo info = parse("@suppress {x} */");
    assertThat(info.getSuppressions()).isEqualTo(Sets.newHashSet("x"));
  }

  public void testSuppress2() throws Exception {
    JSDocInfo info = parse("@suppress {x|y|x|z} */");
    assertThat(info.getSuppressions()).isEqualTo(Sets.newHashSet("x", "y", "z"));
  }

  public void testSuppress3() throws Exception {
    JSDocInfo info = parse("@suppress {x,y} */");
    assertThat(info.getSuppressions()).isEqualTo(Sets.newHashSet("x", "y"));
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

  public void testBadSuppress6() throws Exception {
    parse("@suppress {x} \n * @suppress {y} */", "duplicate @suppress tag");
  }

  public void testBadSuppress7() throws Exception {
    parse("@suppress {impossible} */",
          "unknown @suppress parameter: impossible");
  }

  public void testBadSuppress8() throws Exception {
    parse("@suppress */", "malformed @suppress tag");
  }

  public void testModifies1() throws Exception {
    JSDocInfo info = parse("@modifies {this} */");
    assertThat(info.getModifies()).isEqualTo(Sets.newHashSet("this"));
  }

  public void testModifies2() throws Exception {
    JSDocInfo info = parse("@modifies {arguments} */");
    assertThat(info.getModifies()).isEqualTo(Sets.newHashSet("arguments"));
  }

  public void testModifies3() throws Exception {
    JSDocInfo info = parse("@modifies {this|arguments} */");
    assertThat(info.getModifies()).isEqualTo(Sets.newHashSet("this", "arguments"));
  }

  public void testModifies4() throws Exception {
    JSDocInfo info = parse("@param {*} x\n * @modifies {x} */");
    assertThat(info.getModifies()).isEqualTo(Sets.newHashSet("x"));
  }

  public void testModifies5() throws Exception {
    JSDocInfo info = parse(
        "@param {*} x\n"
        + " * @param {*} y\n"
        + " * @modifies {x} */");
    assertThat(info.getModifies()).isEqualTo(Sets.newHashSet("x"));
  }

  public void testModifies6() throws Exception {
    JSDocInfo info = parse(
        "@param {*} x\n"
        + " * @param {*} y\n"
        + " * @modifies {x|y} */");
    assertThat(info.getModifies()).isEqualTo(Sets.newHashSet("x", "y"));
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
    assertThat(jsdoc.getFileOverview()).isEqualTo("Hi mom!");
  }

  public void testFileOverviewMultiLine() throws Exception {
    JSDocInfo jsdoc = parseFileOverview("@fileoverview Pie is \n * good! */");
    assertThat(jsdoc.getFileOverview()).isEqualTo("Pie is\n good!");
  }

  public void testFileOverviewDuplicate() throws Exception {
    parseFileOverview(
        "@fileoverview Pie \n * @fileoverview Cake */",
        "extra @fileoverview tag");
  }

  public void testPublicVisibilityAllowedInFileOverview() {
    parseFileOverview("@fileoverview \n * @public */");
  }

  public void testPackageVisibilityAllowedInFileOverview() {
    parseFileOverview("@fileoverview \n * @package */");
  }

  public void testImplicitVisibilityAllowedInFileOverview() {
    parseFileOverview("@fileoverview */");
  }

  public void testProtectedVisibilityNotAllowedInFileOverview() {
    parseFileOverview("@fileoverview \n * @protected */",
        "protected visibility not allowed in @fileoverview block");
  }

  public void testPrivateVisibilityNotAllowedInFileOverview() {
    parseFileOverview("@fileoverview \n @private */",
        "private visibility not allowed in @fileoverview block");
  }

  public void testReferences() throws Exception {
    JSDocInfo jsdoc
        = parse("@see A cool place!"
                + "\n* @see The world."
                + "\n* @see SomeClass#SomeMember"
                + "\n* @see A boring test case*/"
                , true);

    Collection<String> references = jsdoc.getReferences();

    assertThat(references).isNotNull();
    assertThat(references).hasSize(4);

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

    assertThat(jsdoc.isDeprecated()).isTrue();
    assertThat(jsdoc.getDeprecationReason()).isEqualTo("In favor of the new one!");
    assertThat(jsdoc.getVersion()).isEqualTo("Some old version");
    assertThat(jsdoc.getReturnDescription()).isEqualTo("The most important object :-)");
  }

  public void testSingleTags2() throws Exception {
    JSDocInfo jsdoc = parse(
        "@param {SomeType} a The most important object :-)*/", true);

    assertThat(jsdoc.getDescriptionForParameter("a")).isEqualTo("The most important object :-)");
  }

  public void testSingleTagsReordered() throws Exception {
    JSDocInfo jsdoc
        = parse("@deprecated In favor of the new one!"
                + "\n * @return {SomeType} The most important object :-)"
                + "\n * @version Some old version*/"
                , true);

    assertThat(jsdoc.isDeprecated()).isTrue();
    assertThat(jsdoc.getDeprecationReason()).isEqualTo("In favor of the new one!");
    assertThat(jsdoc.getVersion()).isEqualTo("Some old version");
    assertThat(jsdoc.getReturnDescription()).isEqualTo("The most important object :-)");
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
    assertThat(jsdoc.getAssociatedNode().getSourceFileName()).isEqualTo("testcode");
  }

  public void testParseBlockComment() throws Exception {
    JSDocInfo jsdoc = parse("this is a nice comment\n "
                            + "* that is multiline \n"
                            + "* @author abc@google.com */", true);

    assertThat(jsdoc.getBlockDescription()).isEqualTo("this is a nice comment\nthat is multiline");

    assertDocumentationInMarker(
        assertAnnotationMarker(jsdoc, "author", 2, 2), "abc@google.com", 9, 2, 23);
  }

  public void testParseBlockComment2() throws Exception {
    JSDocInfo jsdoc = parse("this is a nice comment\n "
                            + "* that is *** multiline \n"
                            + "* @author abc@google.com */", true);

    assertThat(jsdoc.getBlockDescription())
        .isEqualTo("this is a nice comment\nthat is *** multiline");

    assertDocumentationInMarker(
        assertAnnotationMarker(jsdoc, "author", 2, 2), "abc@google.com", 9, 2, 23);
  }

  public void testParseBlockComment3() throws Exception {
    JSDocInfo jsdoc = parse("\n "
                            + "* hello world \n"
                            + "* @author abc@google.com */", true);

    assertThat(jsdoc.getBlockDescription()).isEqualTo("hello world");

    assertDocumentationInMarker(
        assertAnnotationMarker(jsdoc, "author", 2, 2), "abc@google.com", 9, 2, 23);
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

    JSDocInfo.Marker returnDoc =
        assertAnnotationMarker(jsdoc, "return", 0, 0);
    assertDocumentationInMarker(returnDoc,
        "some long multiline description", 14, 2, 15);
    assertThat(returnDoc.getType().getPositionOnStartLine()).isEqualTo(8);
    assertThat(returnDoc.getType().getPositionOnEndLine()).isEqualTo(12);
  }

  public void testParseWithMarkers4() throws Exception {
    JSDocInfo jsdoc =
        parse("@author foobar \n * @param {Foo} somename abc@google.com */",
              true);

    assertAnnotationMarker(jsdoc, "author", 0, 0);
    assertAnnotationMarker(jsdoc, "param", 1, 3);
  }

  public void testParseWithMarkers5() throws Exception {
    JSDocInfo jsdoc =
        parse("@return some long \n * multiline" +
              " \n * description */", true);

    assertDocumentationInMarker(
        assertAnnotationMarker(jsdoc, "return", 0, 0),
        "some long multiline description", 8, 2, 15);
  }

  public void testParseWithMarkers6() throws Exception {
    JSDocInfo jsdoc =
        parse("@param x some long \n * multiline" +
              " \n * description */", true);

    assertDocumentationInMarker(
        assertAnnotationMarker(jsdoc, "param", 0, 0),
        "some long multiline description", 8, 2, 15);
  }

  public void testParseWithMarkerNames1() throws Exception {
    JSDocInfo jsdoc = parse("@param {SomeType} name somedescription */", true);

    assertNameInMarker(
        assertAnnotationMarker(jsdoc, "param", 0, 0),
        "name", 0, 18);
  }

  public void testParseWithMarkerNames2() throws Exception {
    JSDocInfo jsdoc = parse("@param {SomeType} name somedescription \n" +
                            "* @param {AnotherType} anothername des */", true);

    assertTypeInMarker(
        assertNameInMarker(
            assertAnnotationMarker(jsdoc, "param", 0, 0, 0),
            "name", 0, 18),
        "SomeType", 0, 7, 0, 16, true);

    assertTypeInMarker(
        assertNameInMarker(
            assertAnnotationMarker(jsdoc, "param", 1, 2, 1),
            "anothername", 1, 23),
        "AnotherType", 1, 9, 1, 21, true);
  }

  public void testParseWithMarkerNames3() throws Exception {
    JSDocInfo jsdoc = parse(
        "@param {Some.Long.Type.\n *  Name} name somedescription */", true);

    assertTypeInMarker(
        assertNameInMarker(
            assertAnnotationMarker(jsdoc, "param", 0, 0, 0),
            "name", 1, 10),
        "Some.Long.Type.Name", 0, 7, 1, 8, true);
  }

  @SuppressWarnings("deprecation")
  public void testParseWithoutMarkerName() throws Exception {
    JSDocInfo jsdoc = parse("@author helloworld*/", true);
    assertThat(assertAnnotationMarker(jsdoc, "author", 0, 0).getName()).isNull();
  }

  public void testParseWithMarkerType() throws Exception {
    JSDocInfo jsdoc = parse("@extends {FooBar}*/", true);

    assertTypeInMarker(
        assertAnnotationMarker(jsdoc, "extends", 0, 0),
        "FooBar", 0, 9, 0, 16, true);
  }

  public void testParseWithMarkerType2() throws Exception {
    JSDocInfo jsdoc = parse("@extends FooBar*/", true);

    assertTypeInMarker(
        assertAnnotationMarker(jsdoc, "extends", 0, 0),
        "FooBar", 0, 9, 0, 15, false);
  }

  public void testTypeTagConflict1() throws Exception {
    parse("@constructor \n * @constructor */",
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict2() throws Exception {
    parse("@interface \n * @interface */",
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict3() throws Exception {
    parse("@constructor \n * @interface */",
        "Bad type annotation. cannot be both an interface and a constructor");
  }

  public void testTypeTagConflict4() throws Exception {
    parse("@interface \n * @constructor */",
        "Bad type annotation. cannot be both an interface and a constructor");
  }

  public void testTypeTagConflict5() throws Exception {
    parse("@interface \n * @type {string} */",
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict6() throws Exception {
    parse("@typedef {string} \n * @type {string} */",
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict7() throws Exception {
    parse("@typedef {string} \n * @constructor */",
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict8() throws Exception {
    parse("@typedef {string} \n * @return {boolean} */",
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict9() throws Exception {
    parse("@enum {string} \n * @return {boolean} */",
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict10() throws Exception {
    parse("@this {Object} \n * @enum {boolean} */",
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict11() throws Exception {
    parse("@param {Object} x \n * @type {boolean} */",
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict12() throws Exception {
    parse("@typedef {boolean} \n * @param {Object} x */",
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict13() throws Exception {
    parse("@typedef {boolean} \n * @extends {Object} */",
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict14() throws Exception {
    parse("@return x \n * @return y */",
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict15() throws Exception {
    parse("/**\n" +
          " * @struct\n" +
          " * @struct\n" +
          " */\n" +
          "function StrStr() {}",
          "Bad type annotation. " +
          "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict16() throws Exception {
    parse("/**\n" +
          " * @struct\n" +
          " * @interface\n" +
          " */\n" +
          "function StrIntf() {}",
          "Bad type annotation. " +
          "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict17() throws Exception {
    parse("/**\n" +
          " * @interface\n" +
          " * @struct\n" +
          " */\n" +
          "function StrIntf() {}",
          "Bad type annotation. " +
          "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict18() throws Exception {
    parse("/**\n" +
          " * @dict\n" +
          " * @dict\n" +
          " */\n" +
          "function DictDict() {}",
          "Bad type annotation. " +
          "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict19() throws Exception {
    parse("/**\n" +
          " * @dict\n" +
          " * @interface\n" +
          " */\n" +
          "function DictDict() {}",
          "Bad type annotation. " +
          "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict20() throws Exception {
    parse("/**\n" +
          " * @interface\n" +
          " * @dict\n" +
          " */\n" +
          "function DictDict() {}",
          "Bad type annotation. " +
          "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict21() throws Exception {
    parse("/**\n" +
          " * @private {string}\n" +
          " * @type {number}\n" +
          " */\n" +
          "function DictDict() {}",
          "Bad type annotation. " +
          "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict22() throws Exception {
    parse("/**\n" +
          " * @protected {string}\n" +
          " * @param {string} x\n" +
          " */\n" +
          "function DictDict(x) {}",
          "Bad type annotation. " +
          "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict23() throws Exception {
    parse("/**\n" +
          " * @public {string}\n" +
          " * @return {string} x\n" +
          " */\n" +
          "function DictDict() {}",
          "Bad type annotation. " +
          "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict24() throws Exception {
    parse("/**\n" +
          " * @const {string}\n" +
          " * @return {string} x\n" +
          " */\n" +
          "function DictDict() {}",
          "Bad type annotation. " +
          "type annotation incompatible with other annotations");
  }

  public void testTypeTagConflict25() throws Exception {
    parse("/**\n" +
        " * @package {string}\n" +
        " * @return {string} x\n" +
        " */\n" +
        "function DictDict() {}",
        "Bad type annotation. " +
        "type annotation incompatible with other annotations");
  }

  public void testPackageType() throws Exception {
    JSDocInfo jsdoc = parse("@package {string} */");
    assertTypeEquals(STRING_TYPE, jsdoc.getType());
  }

  public void testPrivateType() throws Exception {
    JSDocInfo jsdoc = parse("@private {string} */");
    assertTypeEquals(STRING_TYPE, jsdoc.getType());
  }

  public void testProtectedType() throws Exception {
    JSDocInfo jsdoc = parse("@protected {string} */");
    assertTypeEquals(STRING_TYPE, jsdoc.getType());
  }

  public void testPublicType() throws Exception {
    JSDocInfo jsdoc = parse("@public {string} */");
    assertTypeEquals(STRING_TYPE, jsdoc.getType());
  }

  public void testConstType() throws Exception {
    JSDocInfo jsdoc = parse("@const {string} */");
    assertTypeEquals(STRING_TYPE, jsdoc.getType());
  }

  public void testExportType() throws Exception {
    JSDocInfo jsdoc = parse("@export {string} descr\n next line */", true);
    assertTypeEquals(STRING_TYPE, jsdoc.getType());

    assertThat(jsdoc.isExport()).isTrue();

    Marker defineMarker = jsdoc.getMarkers().iterator().next();
    assertThat(defineMarker.getAnnotation().getItem()).isEqualTo("export");
    assertThat(defineMarker.getDescription().getItem()).contains("descr");
    assertThat(defineMarker.getDescription().getItem()).contains("next line");
  }

  public void testMixedVisibility() throws Exception {
    parse("@public @private */", "extra visibility tag");
    parse("@public @protected */", "extra visibility tag");
    parse("@export @protected */", "extra visibility tag");
    parse("@export {string}\n * @private */", "extra visibility tag");
    parse("@export {string}\n * @public */", "extra visibility tag");
  }

  public void testStableIdGeneratorConflict() throws Exception {
    parse("/**\n" +
          " * @stableIdGenerator\n" +
          " * @stableIdGenerator\n" +
          " */\n" +
          "function getId() {}",
          "extra @stableIdGenerator tag");
  }

  public void testIdGenerator() throws Exception {
    JSDocInfo info = parse("/**\n" +
          " * @idGenerator\n" +
          " */\n" +
          "function getId() {}");
    assertThat(info.isIdGenerator()).isTrue();
  }

  public void testIdGeneratorConflict() throws Exception {
    parse("/**\n" +
          " * @idGenerator\n" +
          " * @idGenerator\n" +
          " */\n" +
          "function getId() {}",
          "extra @idGenerator tag");
  }

  public void testIdGenerator1() throws Exception {
    JSDocInfo info = parse("@idGenerator {unique} */");
    assertThat(info.isIdGenerator()).isTrue();
  }

  public void testIdGenerator2() throws Exception {
    JSDocInfo info = parse("@idGenerator {consistent} */");
    assertThat(info.isConsistentIdGenerator()).isTrue();
  }

  public void testIdGenerator3() throws Exception {
    JSDocInfo info = parse("@idGenerator {stable} */");
    assertThat(info.isStableIdGenerator()).isTrue();
  }

  public void testIdGenerator4() throws Exception {
    JSDocInfo info = parse("@idGenerator {mapped} */");
    assertThat(info.isMappedIdGenerator()).isTrue();
  }

  public void testBadIdGenerator1() throws Exception {
    parse("@idGenerator {} */", "malformed @idGenerator tag");
  }

  public void testBadIdGenerator2() throws Exception {
    parse("@idGenerator {impossible} */",
        "unknown @idGenerator parameter: impossible");
  }

  public void testBadIdGenerator3() throws Exception {
    parse("@idGenerator {unique */", "malformed @idGenerator tag");
  }

  public void testParserWithTemplateTypeNameMissing() {
    parse("@template */",
        "Bad type annotation. @template tag missing type name");
  }

  public void testParserWithTwoTemplates() {
    parse("@template T,V */");
  }

  public void testParserWithInvalidTemplateType() {
    parse("@template {T} */",
        "Bad type annotation. Invalid type name(s) for @template annotation");
  }

  public void testParserWithValidAndInvalidTemplateType() {
    parse("@template S, {T} */",
        "Bad type annotation. Invalid type name(s) for @template annotation");
  }

  public void testParserWithTemplateDuplicated() {
    parse("@template T\n@template V */");
  }

  public void testParserWithTemplateDuplicated2() {
    parse("@template T,R\n@template V,U */");
  }

  public void testParserWithTemplateDuplicated3() {
    parse("@template T\n@param {string} x\n@template V */");
  }

  public void testParserWithTemplateTypeNameDeclaredTwice() {
    parse("@template T\n@template T */",
        "Bad type annotation. Type name(s) for "
        + "@template annotation declared twice");
  }

  public void testParserWithTemplateTypeNameDeclaredTwice2() {
    parse("@template T := S =: \n @template T := R =:*/",
        "Bad type annotation. Type name(s) for "
        + "@template annotation declared twice");
  }

  public void testParserWithTemplateTypeNameDeclaredTwice3() {
    parse("@template T \n @template T := R =:*/",
        "Bad type annotation. Type name(s) for "
        + "@template annotation declared twice");
  }

  public void testParserWithTemplateTypeNameDeclaredTwice4() {
    parse("@template T := R =: \n @template T*/",
        "Bad type annotation. Type name(s) for "
        + "@template annotation declared twice");
  }

  public void testParserWithDoubleTemplateDeclaration2() {
    parse("@template T,T */",
        "Bad type annotation. Type name(s) for "
        + "@template annotation declared twice");
  }

  public void testParserWithTemplateDuplicatedTypeNameMissing() {
    parse("@template T,R\n@template */",
        "Bad type annotation. @template tag missing type name");
  }

  public void testParserWithTypeTransformationNewline() {
    parse("@template R := \n 'string' =:*/");
  }

  public void testParserWithTypeTransformation() {
    parse("@template T := 'string' =:*/");
  }

  public void testParserWithTypeTransformation2() {
    parse("@template T := 'string' =:\n"
        + "Random text*/");
  }

  public void testParserWithTypeTransformationMultipleNames() {
    parse("@template T, R := 'string' =:*/",
        "Bad type annotation. "
        + "Type transformation must be associated to a single type name");
  }

  public void testParserWithMissingTypeTransformationExpression() {
    parse("@template T := */",
        "Bad type annotation. "
        + "Expected end delimiter for a type transformation");
  }

  public void testParserWithMissingTypeTransformationExpression2() {
    parse("@template T := =:*/",
        "Bad type annotation. Missing type transformation expression");
  }

  public void testBug16129690() {
    parse("@param {T} x\n"
        + "@template T\n"
        + "random documentation text*/");
  }

  public void testParserWithTTLInvalidOperation() {
    parse("@template T := foo() =:*/",
        "Bad type annotation. Invalid type transformation expression");
  }

  public void testParserWithTTLInvalidTypeTransformation() {
    parse("@template T := var a; =:*/",
        "Bad type annotation. Invalid type transformation expression");
  }

  public void testParserWithTTLValidTypename() {
    parse("@template T := foo =:*/");
  }

  public void testParserWithTTLValidTypename2() {
    parse("@template T := R =:*/");
  }

  public void testParserWithTTLValidTypename3() {
    parse("@template T := _Foo =:*/");
  }

  public void testParserWithTTLValidTypename4() {
    parse("@template T := $foo =:*/");
  }

  public void testParserWithTTLBasicType() {
    parse("@template T := 'string' =:*/");
  }

  public void testParserWithTTLValidUnionType() {
    parse("@template T := union('string', 'number') =:*/");
  }

  public void testParserWithTTLValidUnionType2() {
    parse("@template T := union(R, S) =:*/");
  }

  public void testParserWithTTLValidUnionType3() {
    parse("@template T := union(R, 'string', S) =:*/");
  }

  public void testParserWithTTLEmptyUnionType() {
    parse("@template T := union() =:*/",
        "Bad type annotation. Missing parameter in union");
  }

  public void testParserWithTTLSingletonUnionType() {
    parse("@template T := union('string') =:*/",
        "Bad type annotation. Missing parameter in union");
  }

  public void testParserWithTTLInvalidUnionType2() {
    parse("@template T := union(function(a){}, T) =:*/",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside union type");
  }

  public void testParserWithNestedUnionFirstParam() {
    parse("@template T := union(union(N, 'null'), S) =:*/");
  }

  public void testParserWithNestedUnionSecondParam() {
    parse("@template T := union(N, union('null', S)) =:*/");
  }

  public void testParserWithNestedBooleanFirstParam() {
    parse("@template T := "
        + "cond( eq(cond(eq(N, N), 'string', 'number'), 'string'),"
        + "'string',"
        + "'number') =: */");
  }

  public void testParserWithNestedBooleanSecondParam() {
    parse("@template T := "
        + "cond( eq('string', cond(eq(N, N), 'string', 'number')),"
        + "'string',"
        + "'number') =:*/");
  }

  public void testParserWithTTLConditional() {
    parse("@template T := cond(eq(T, R), R, S) =: */");
  }

  public void testParserWithTTLConditional2() {
    parse("@template T := cond(sub(T, R), R, S) =: */");
  }

  public void testParserWithTTLConditionalStringEquivalence() {
    parse("@template T := cond(streq(R, S), R, S) =: */");
  }

  public void testParserWithTTLConditionalStringEquivalence2() {
    parse("@template T := cond(streq(R, 'foo'), R, S) =: */");
  }

  public void testParserWithTTLConditionalStringEquivalence3() {
    parse("@template T := cond(streq('foo', 'bar'), R, S) =: */");
  }

  public void testParserWithTTLConditionalIsConstructor() {
    parse("@template T := cond(isCtor(R), R, S) =: */");
  }

  public void testParserWithTTLConditionalIsConstructor2() {
    parse("@template T := cond(isCtor('foo'), R, S) =: */");
  }

  public void testParserWithTTLConditionalIsTemplatized() {
    parse("@template T := cond(isTemplatized(R), R, S) =: */");
  }

  public void testParserWithTTLConditionalIsTemplatized2() {
    parse("@template T := cond(isTemplatized('foo'), R, S) =: */");
  }

  public void testParserWithTTLConditionalIsRecord() {
    parse("@template T := cond(isRecord(R), R, S) =: */");
  }

  public void testParserWithTTLConditionalAndOperation() {
    parse("@template T := cond(isCtor(R) && isCtor(S), R, S) =: */");
  }

  public void testParserWithTTLConditionalOrOperation() {
    parse("@template T := cond(isCtor(R) || isCtor(S), R, S) =: */");
  }

  public void testParserWithTTLConditionalNotOperation() {
    parse("@template T := cond(!isCtor(R), R, S) =: */");
  }

  public void testParserWithTTLConditionalNestedBoolOperation() {
    parse("@template T := "
        + "cond(((isCtor(R) || isCtor(S)) && !isCtor(R)), R, S) =: */");
  }

  public void testParserWithTTLConditionalIsRecord2() {
    parse("@template T := cond(isRecord('foo'), R, S) =: */");
  }

  public void testParserWithTTLConditionalIsDefined() {
    parse("@template T := cond(isDefined(R), R, S) =: */");
  }

  public void testParserWithTTLConditionalIsUnknown() {
    parse("@template T := cond(isUnknown(R), R, S) =: */");
  }

  public void testParserWithTTLConditionalStringEquivalenceInvalidParam() {
    parse("@template T := cond(streq('foo', foo()), R, S) =: */",
        "Bad type annotation. Invalid string",
        "Bad type annotation. Invalid expression inside boolean",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLConditionalStringEquivalenceInvalidParamEmptyStr() {
    parse("@template T := cond(streq('', S), R, S) =: */",
        "Bad type annotation. Invalid string parameter",
        "Bad type annotation. Invalid expression inside boolean",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLConditionalIsConstructorInvalidParam() {
    parse("@template T := cond(isCtor(foo()), R, S) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside boolean",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLConditionalIsTemplatizedInvalidParam() {
    parse("@template T := cond(isTemplatized(foo()), R, S) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside boolean",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLConditionalIsRecordInvalidParam() {
    parse("@template T := cond(isRecord(foo()), R, S) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside boolean",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLConditionalIsDefinedInvalidParam() {
    parse("@template T := cond(isDefined('foo'), R, S) =: */",
        "Bad type annotation. Invalid name",
        "Bad type annotation. Invalid expression inside boolean",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLConditionalIsUnknownInvalidParam() {
    parse("@template T := cond(isUnknown(foo()), R, S) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside boolean",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLConditionalAndInvalidParam() {
    parse("@template T := cond('foo' && isCtor(R), R, S) =: */",
        "Bad type annotation. Invalid boolean expression",
        "Bad type annotation. Invalid expression inside boolean",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLConditionalAndInvalidParam2() {
    parse("@template T := cond(isCtor(R) && 'foo', R, S) =: */",
        "Bad type annotation. Invalid boolean expression",
        "Bad type annotation. Invalid expression inside boolean",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLConditionalOrInvalidParam() {
    parse("@template T := cond('foo' || isCtor(R), R, S) =: */",
        "Bad type annotation. Invalid boolean expression",
        "Bad type annotation. Invalid expression inside boolean",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLConditionalOrInvalidParam2() {
    parse("@template T := cond(isCtor(R) || 'foo', R, S) =: */",
        "Bad type annotation. Invalid boolean expression",
        "Bad type annotation. Invalid expression inside boolean",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLConditionalNotInvalidParam() {
    parse("@template T := cond(!'foo', R, S) =: */",
        "Bad type annotation. Invalid boolean expression",
        "Bad type annotation. Invalid expression inside boolean",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLExtraParamBoolean() {
    parse("@template T := cond(eq(T, R, S), R, S) =: */",
        "Bad type annotation. Found extra parameter in eq",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLExtraParamStringEq() {
    parse("@template T := cond(streq(T, R, S), R, S) =: */",
        "Bad type annotation. Found extra parameter in streq",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLExtraParamIsConstructor() {
    parse("@template T := cond(isCtor(T, R, S), R, S) =: */",
        "Bad type annotation. Found extra parameter in isCtor",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLExtraParamIsTemplatized() {
    parse("@template T := cond(isTemplatized(T, R, S), R, S) =: */",
        "Bad type annotation. Found extra parameter in isTemplatized",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLExtraParamIsRecord() {
    parse("@template T := cond(isRecord(T, R), R, S) =: */",
        "Bad type annotation. Found extra parameter in isRecord",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLExtraParamIsDefined() {
    parse("@template T := cond(isDefined(T, R), R, S) =: */",
        "Bad type annotation. Found extra parameter in isDefined",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLExtraParamIsUnknown() {
    parse("@template T := cond(isUnknown(T, R), R, S) =: */",
        "Bad type annotation. Found extra parameter in isUnknown",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLMissingParamBoolean() {
    parse("@template T := cond(eq(T), R, S) =: */",
        "Bad type annotation. Missing parameter in eq",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLMissingParamStringEquivalence() {
    parse("@template T := cond(streq(T), R, S) =: */",
        "Bad type annotation. Missing parameter in streq",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLMissingParamIsConstructor() {
    parse("@template T := cond(isCtor(), R, S) =: */",
        "Bad type annotation. Missing parameter in isCtor",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLMissingParamIsTemplatized() {
    parse("@template T := cond(isTemplatized(), R, S) =: */",
        "Bad type annotation. Missing parameter in isTemplatized",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLMissingParamIsRecord() {
    parse("@template T := cond(isRecord(), R, S) =: */",
        "Bad type annotation. Missing parameter in isRecord",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLMissingParamIsDefined() {
    parse("@template T := cond(isDefined(), R, S) =: */",
        "Bad type annotation. Missing parameter in isDefined",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLMissingParamIsUnknown() {
    parse("@template T := cond(isUnknown(), R, S) =: */",
        "Bad type annotation. Missing parameter in isUnknown",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLInvalidBooleanConditional() {
    parse("@template T := cond(aaa, R, S) =: */",
        "Bad type annotation. Invalid boolean expression",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLInvalidBooleanConditional2() {
    parse("@template T := cond(foo(T, R), S, R) =: */",
        "Bad type annotation. Invalid boolean predicate",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLInvalidBooleanConditional3() {
    parse("@template T := cond(eq(T, foo()), R, S) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside boolean",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLInvalidConditionalMissingParam() {
    parse("@template T := cond(sub(T, R), T) =: */",
        "Bad type annotation. Missing parameter in cond");
  }

  public void testParserWithTTLInvalidConditionalExtraParam() {
    parse("@template T := cond(sub(T, R), T, R, R) =: */",
        "Bad type annotation. Found extra parameter in cond");
  }

  public void testParserWithTTLInvalidConditional() {
    parse("@template T := cond(eq(T, R), foo(), S) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLInvalidConditional2() {
    parse("@template T := cond(eq(T, R), S, foo()) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside conditional");
  }

  public void testParserWithTTLValidMapunion() {
    parse("@template T := mapunion(T, (S) => S) =: */");
  }

  public void testParserWithTTLValidMapunion2() {
    parse("@template T := "
        + "mapunion(union('string', 'number'), (S) => S) "
        + "=: */");
  }

  public void testParserWithTTLInvalidMapunionType() {
    parse("@template T := mapunion(foo(), (S) => S) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside mapunion");
  }

  public void testParserWithTTLInvalidMapunionFn() {
    parse("@template T := mapunion(R, S) =: */",
        "Bad type annotation. Invalid map function",
        "Bad type annotation. Invalid expression inside mapunion");
  }

  public void testParserWithTTLInvalidMapunionMissingParams() {
    parse("@template T := mapunion(T) =: */",
        "Bad type annotation. Missing parameter in mapunion");
  }

  public void testParserWithTTLInvalidMapunionExtraParams() {
    parse("@template T := mapunion(T, (S) => S, R) =: */",
        "Bad type annotation. Found extra parameter in mapunion");
  }

  public void testParserWithTTLInvalidMapunionMissingFnParams() {
    parse("@template T := mapunion(T, () => S) =: */",
        "Bad type annotation. Missing parameter in map function",
        "Bad type annotation. Invalid expression inside mapunion");
  }
  public void testParserWithTTLInvalidMapunionExtraFnParams() {
    parse("@template T := mapunion(T, (S, R) => S) =: */",
        "Bad type annotation. Found extra parameter in map function",
        "Bad type annotation. Invalid expression inside mapunion");
  }

  public void testParserWithTTLInvalidMapunionFunctionBody() {
    parse("@template T := mapunion(T, (S) => foo()) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside map function body");
  }

  public void testParserWithTTLUseCaseObject() {
    parse("@template T := "
        + "mapunion(T, (x) => "
        + "cond(eq(x, 'string'), 'String',"
        + "cond(eq(x, 'number'), 'Number',"
        + "cond(eq(x, 'boolean'), 'Boolean',"
        + "cond(eq(x, 'null'), 'Object',"
        + "cond(eq(x, 'undefined'), 'Object',"
        + "x)))))) =: */");
  }

  public void testParserWithTTLNoneType() {
    parse("@template T := none() =: */");
  }

  public void testParserWithTTLNoneType2() {
    parse("@template T := cond(eq(S, none()), S, T) =: */");
  }

  public void testParserWithTTLInvalidNoneType() {
    parse("@template T := none(foo) =: */",
        "Bad type annotation. Found extra parameter in none");
  }

  public void testParserWithTTLInvalidNoneType2() {
    parse("@template T := none(a, b, c) =: */",
        "Bad type annotation. Found extra parameter in none");
  }

  public void testParserWithTTLAllType() {
    parse("@template T := all() =: */");
  }

  public void testParserWithTTLAllType2() {
    parse("@template T := cond(eq(S, all()), S, T) =: */");
  }

  public void testParserWithTTLInvalidAllType() {
    parse("@template T := all(foo) =: */",
        "Bad type annotation. Found extra parameter in all");
  }

  public void testParserWithTTLInvalidAllType2() {
    parse("@template T := all(a, b, c) =: */",
        "Bad type annotation. Found extra parameter in all");
  }

  public void testParserWithTTLUnknownType() {
    parse("@template T := unknown() =: */");
  }

  public void testParserWithTTLUnknownType2() {
    parse("@template T := cond(eq(S, unknown()), S, T) =: */");
  }

  public void testParserWithTTLInvalidUnknownType() {
    parse("@template T := unknown(foo) =: */",
        "Bad type annotation. Found extra parameter in unknown");
  }

  public void testParserWithTTLInvalidUnknownType2() {
    parse("@template T := unknown(a, b, c) =: */",
        "Bad type annotation. Found extra parameter in unknown");
  }

  public void testParserWithTTLTemplateTypeOperation() {
    parse("@template T := type('Map', 'string', 'number') =: */");
  }

  public void testParserWithTTLTemplateTypeOperationGeneric() {
    parse("@template T := type('Array', T) =: */");
  }

  public void testParserWithTTLTemplateTypeOperationGeneric2() {
    parse("@template T := type(T, R) =: */");
  }

  public void testParserWithTTLTemplateTypeOperationNestedGeneric() {
    parse("@template T := type(T, type(R, S)) =: */");
  }

  public void testParserWithTTLTemplateTypeOperationGenericWithUnion() {
    parse("@template T := type(T, union(R, S)) =: */");
  }

  public void testParserWithTTLInvalidTemplateTypeOperationGenericUnion() {
    parse("@template T := type(union(R, S), T) =: */",
        "Bad type annotation. Invalid type name or type variable",
        "Bad type annotation. Invalid expression inside template type operation");
  }

  public void testParserWithTTLInvalidTypeOperationNestedGeneric() {
    parse("@template T := type(T, foo()) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside template type operation");
  }

  public void testParserWithTTLValidRawTypeOperation() {
    parse("@template T := rawTypeOf(T) =: */");
  }

  public void testParserWithTTLValidRawTypeOperation2() {
    parse("@template T := rawTypeOf(type(T, R)) =: */");
  }

  public void testParserWithTTLInvalidRawTypeOperation() {
    parse("@template T := rawTypeOf(foo()) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside rawTypeOf");
  }

  public void testParserWithTTLInvalidRawTypeOperationExtraParam() {
    parse("@template T := rawTypeOf(R, S) =: */",
        "Bad type annotation. Found extra parameter in rawTypeOf");
  }

  public void testParserWithTTLInvalidRawTypeOperationMissingParam() {
    parse("@template T := rawTypeOf() =: */",
        "Bad type annotation. Missing parameter in rawTypeOf");
  }

  public void testParserWithTTLNestedRawTypeOperation() {
    parse("@template T := rawTypeOf(type(T, rawTypeOf(type(R, S)))) =: */");
  }

  public void testParserWithTTLValidTemplateTypeOfOperation() {
    parse("@template T := templateTypeOf(T, 1) =: */");
  }

  public void testParserWithTTLValidTemplateTypeOfOperation2() {
    parse("@template T := templateTypeOf(type(T, R), 1) =: */");
  }

  public void testParserWithTTLInvalidFirstParamTemplateTypeOf() {
    parse("@template T := templateTypeOf(foo(), 1) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside templateTypeOf");
  }

  public void testParserWithTTLInvalidSecondParamTemplateTypeOf() {
    parse("@template T := templateTypeOf(R, foo()) =: */",
        "Bad type annotation. Invalid index",
        "Bad type annotation. Invalid expression inside templateTypeOf");
  }

  public void testParserWithTTLInvalidSecondParamTemplateTypeOf2() {
    parse("@template T := templateTypeOf(R, 1.5) =: */",
        "Bad type annotation. Invalid index",
        "Bad type annotation. Invalid expression inside templateTypeOf");
  }

  public void testParserWithTTLInvalidSecondParamTemplateTypeOf3() {
    parse("@template T := templateTypeOf(R, -1) =: */",
        "Bad type annotation. Invalid index",
        "Bad type annotation. Invalid expression inside templateTypeOf");
  }

  public void testParserWithTTLInvalidTemplateTypeOfExtraParam() {
    parse("@template T := templateTypeOf(R, 1, S) =: */",
        "Bad type annotation. Found extra parameter in templateTypeOf");
  }

  public void testParserWithTTLInvalidTemplateTypeOfMissingParam() {
    parse("@template T := templateTypeOf() =: */",
        "Bad type annotation. Missing parameter in templateTypeOf");
  }

  public void testParserWithTTLInvalidTemplateTypeOfMissingParam2() {
    parse("@template T := templateTypeOf(T) =: */",
        "Bad type annotation. Missing parameter in templateTypeOf");
  }

  public void testParserWithTTLNestedTemplateTypeOfOperation() {
    parse("@template T := templateTypeOf("
        +                   "templateTypeOf(type(T, type(R, S)), 0),"
        +                   "0) =: */");
  }

  public void testParserWithTTLValidPrintType() {
    parse("@template T := printType('msg', R) =: */");
  }

  public void testParserWithTTLInvalidFirstParamPrintType() {
    parse("@template T := printType(foo(), R) =: */",
        "Bad type annotation. Invalid message",
        "Bad type annotation. Invalid expression inside printType");
  }

  public void testParserWithTTLInvalidSecondParamPrintType() {
    parse("@template T := printType('msg', foo()) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside printType");
  }

  public void testParserWithTTLInvalidPrintTypeExtraParam() {
    parse("@template T := printType(R, S, U) =: */",
        "Bad type annotation. Found extra parameter in printType");
  }

  public void testParserWithTTLInvalidPrintTypeOfMissingParam() {
    parse("@template T := printType() =: */",
        "Bad type annotation. Missing parameter in printType");
  }

  public void testParserWithTTLInvalidPrintTypeOfMissingParam2() {
    parse("@template T := printType(R) =: */",
        "Bad type annotation. Missing parameter in printType");
  }

  public void testParserWithTTLValidPropType() {
    parse("@template T := propType('p', R) =: */");
  }

  public void testParserWithTTLInvalidFirstParamPropType() {
    parse("@template T := propType(foo(), R) =: */",
        "Bad type annotation. Invalid property name",
        "Bad type annotation. Invalid expression inside propType");
  }

  public void testParserWithTTLInvalidSecondParamPropType() {
    parse("@template T := propType('msg', foo()) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside propType");
  }

  public void testParserWithTTLInvalidPropTypeExtraParam() {
    parse("@template T := propType(R, S, U) =: */",
        "Bad type annotation. Found extra parameter in propType");
  }

  public void testParserWithTTLInvalidPropTypeOfMissingParam() {
    parse("@template T := propType() =: */",
        "Bad type annotation. Missing parameter in propType");
  }

  public void testParserWithTTLInvalidPropTypeOfMissingParam2() {
    parse("@template T := propType(R) =: */",
        "Bad type annotation. Missing parameter in propType");
  }

  public void testParserWithTTLRecordType() {
    parse("@template T := record({prop:T}) =: */");
  }

  public void testParserWithTTLNestedRecordType() {
    parse("@template T := "
        + "record({prop: record({p1:'number', p2:'boolean'}), x:'string' })"
        + "=: */");
  }

  public void testParserWithTTLInvalidRecordTypeMissingParam() {
    parse("@template T := record() =: */",
        "Bad type annotation. Missing parameter in record");
  }

  public void testParserWithTTLMergeRecords() {
    parse("@template T := record({x:'number'}, {y:'number'}) =: */");
  }

  public void testParserWithTTLInvalidMergeRecords() {
    parse("@template T := record({x:'number'}, foo()) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside record");
  }

  public void testParserWithTTLInvalidRecordTypeWithInvalidTypeInProperty() {
    parse("@template T := record({prop:foo()}) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside record");
  }

  public void testParserWithTTLInvalidRecordTypeMissingTypeInProperty() {
    parse("@template T := record({prop}) =: */",
        "Bad type annotation. Invalid property, missing type",
        "Bad type annotation. Invalid expression inside record");
  }

  public void testParserWithTTLInvalidRecordTypeInvalidRecordExpression() {
    parse("@template T := record(foo()) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside record");
  }

  public void testParserWithTTLRecordTypeTypeVars() {
    parse("@template T := record(T, R) =: */");
  }

  public void testParserWithTTLEmptyRecordType() {
    parse("@template T := record({}) =: */");
  }

  public void testParserWithTTLTypeTransformationInFirstParamMapunion() {
    parse("@template T := "
        + "mapunion(templateTypeOf(type(R, union(S, U)), 0), "
        + "(x) => x) =: */");
  }

  public void testParserWithTTLValidMaprecord() {
    parse("@template T := maprecord(R, (K, V) => V) =: */");
  }

  public void testParserWithTTLValidMaprecord2() {
    parse("@template T := "
        + "maprecord(record({x:'string', y:'number'}), "
        + "(K, V) => V) =: */");
  }

  public void testParserWithTTLInvalidMaprecordFirstParam() {
    parse("@template T := maprecord(foo(), (K, V) => V) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside maprecord");
  }

  public void testParserWithTTLInvalidMaprecordNotAFunction() {
    parse("@template T := maprecord(R, S) =: */",
        "Bad type annotation. Invalid map function",
        "Bad type annotation. Invalid expression inside maprecord");
  }

  public void testParserWithTTLInvalidMaprecordMissingParams() {
    parse("@template T := maprecord(R) =: */",
        "Bad type annotation. Missing parameter in maprecord");
  }

  public void testParserWithTTLInvalidMaprecordExtraParams() {
    parse("@template T := maprecord(R, (K, V) => V, R) =: */",
        "Bad type annotation. Found extra parameter in maprecord");
  }

  public void testParserWithTTLInvalidMaprecordMissingParamsInMapFunction() {
    parse("@template T := maprecord(R, () => S) =: */",
        "Bad type annotation. Missing parameter in map function",
        "Bad type annotation. Invalid expression inside maprecord");
  }

  public void testParserWithTTLInvalidMaprecordMissingParamsInMapFunction2() {
    parse("@template T := maprecord(R, (K) => S) =: */",
        "Bad type annotation. Missing parameter in map function",
        "Bad type annotation. Invalid expression inside maprecord");
  }

  public void testParserWithTTLInvalidMaprecordExtraParamsInMapFunction() {
    parse("@template T := maprecord(R, (K, V, S) => S) =: */",
        "Bad type annotation. Found extra parameter in map function",
        "Bad type annotation. Invalid expression inside maprecord");
  }

  public void testParserWithTTLInvalidMaprecordInvalidFunctionBody() {
    parse("@template T := maprecord(R, (K, V) => foo()) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside map function body");
  }

  public void testParserWithTTLTypeOfVar() {
    parse("@template T := typeOfVar('x') =: */");
  }

  public void testParserWithTTLTypeOfVar2() {
    parse("@template T := typeOfVar('x.y') =: */");
  }

  public void testParserWithTTLTypeOfVarInvalidName() {
    parse("@template T := typeOfVar(foo()) =: */",
        "Bad type annotation. Invalid name",
        "Bad type annotation. Invalid expression inside typeOfVar");
  }

  public void testParserWithTTLTypeOfVarMissingParam() {
    parse("@template T := typeOfVar() =: */",
        "Bad type annotation. Missing parameter in typeOfVar");
  }

  public void testParserWithTTLTypeOfVarExtraParam() {
    parse("@template T := typeOfVar(a, b) =: */",
        "Bad type annotation. Found extra parameter in typeOfVar");
  }

  public void testParserWithTTLInstanceOf() {
    parse("@template T := instanceOf('x') =: */");
  }

  public void testParserWithTTLInstanceOf2() {
    parse("@template T := instanceOf(R) =: */");
  }

  public void testParserWithTTLInstanceOfInvalidName() {
    parse("@template T := instanceOf(foo()) =: */",
        "Bad type annotation. Invalid type transformation expression",
        "Bad type annotation. Invalid expression inside instanceOf");
  }

  public void testParserWithTTLInstanceOfMissingParam() {
    parse("@template T := instanceOf() =: */",
        "Bad type annotation. Missing parameter in instanceOf");
  }

  public void testParserWithTTLInstanceOfExtraParam() {
    parse("@template T := instanceOf(a, b) =: */",
        "Bad type annotation. Found extra parameter in instanceOf");
  }

  public void testParserWithTTLNativeTypeExprBasic() {
    parse("@template T := typeExpr('string') =: */");
  }

  public void testParserWithTTLNativeTypeExprBasic2() {
    parse("@template T := typeExpr('goog.ui.Menu') =: */");
  }

  public void testParserWithTTLNativeTypeExprUnion() {
    parse("@template T := typeExpr('number|boolean') =: */");
  }

  public void testParserWithTTLNativeTypeExprRecord() {
    parse("@template T := typeExpr('{myNum: number, myObject} ') =: */");
  }

  public void testParserWithTTLNativeTypeExprNullable() {
    parse("@template T := typeExpr('?number') =: */");
  }

  public void testParserWithTTLNativeTypeExprNonNullable() {
    parse("@template T := typeExpr('!Object') =: */");
  }

  public void testParserWithTTLNativeTypeExprFunction() {
    parse("@template T := typeExpr('function(string, boolean)') =: */");
  }

  public void testParserWithTTLNativeTypeExprFunctionReturn() {
    parse("@template T := typeExpr('function(): number') =: */");
  }

  public void testParserWithTTLNativeTypeExprFunctionThis() {
    parse("@template T := typeExpr('function(this:goog.ui.Menu, string)') =: */");
  }

  public void testParserWithTTLNativeTypeExprFunctionNew() {
    parse("@template T := typeExpr('function(new:goog.ui.Menu, string)') =: */");
  }

  public void testParserWithTTLNativeTypeExprFunctionVarargs() {
    parse("@template T := typeExpr('function(string, ...number): number') =: */");
  }

  public void testParserWithTTLNativeTypeExprFunctionOptional() {
    parse("@template T := typeExpr('function(?string=, number=)') =: */");
  }

  public void testParserWithTTLNativeTypeExprMissingParam() {
    parse("@template T := typeExpr() =: */",
        "Bad type annotation. Missing parameter in typeExpr");
  }

  public void testParserWithTTLNativeTypeExprExtraParam() {
    parse("@template T := typeExpr('a', 'b') =: */",
        "Bad type annotation. Found extra parameter in typeExpr");
  }

  public void testParserWithTTLNativeInvalidTypeExpr() {
    parse("@template T := typeExpr(foo) =: */",
        "Bad type annotation. Invalid native type expression",
        "Bad type annotation. Invalid expression inside typeExpr");
  }

  public void testParserWithTTLAsynchUseCase() {
    parse("@template R := "
        + "cond(eq(T, 'Object'),\n"
        +       "maprecord(T, \n"
        +       "(K, V) => cond(eq(rawTypeOf(V), 'Promise'),\n"
        +                   "templateTypeOf(V, 0),\n"
        +                   "'undefined') "
        +               "),\n"
        +       "T)"
        + "=: */");
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
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertTypeEquals(
        registry.createOptionalType(STRING_TYPE), info.getParameterType("accessLevel"));
    assertThat(info.getDescriptionForParameter("accessLevel")).isEqualTo("The user level");
  }

  public void testUnsupportedJsDocSyntax2() {
    JSDocInfo info =
        parse("@param userInfo The user info. \n"
                  + " * @param userInfo.name The name of the user */",
              true,
              "invalid param name \"userInfo.name\"");
    assertThat(info.getParameterCount()).isEqualTo(1);
    assertThat(info.getDescriptionForParameter("userInfo")).isEqualTo("The user info.");
  }

  public void testWhitelistedAnnotations() {
    parse(
      "* @addon \n" +
      "* @augments \n" +
      "* @base \n" +
      "* @borrows \n" +
      "* @bug \n" +
      "* @channel \n" +
      "* @class \n" +
      "* @config \n" +
      "* @constructs \n" +
      "* @default \n" +
      "* @description \n" +
      "* @enhance \n" +
      "* @enhanceable \n" +
      "* @event \n" +
      "* @example \n" +
      "* @exception \n" +
      "* @exec \n" +
      "* @externs \n" +
      "* @field \n" +
      "* @function \n" +
      "* @hassoydelcall \n" +
      "* @hassoydeltemplate \n" +
      "* @id \n" +
      "* @ignore \n" +
      "* @inner \n" +
      "* @jaggerInject \n" +
      "* @jaggerModule \n" +
      "* @jaggerProvide \n" +
      "* @jaggerProvidePromise \n" +
      "* @lends {string} \n" +
      "* @link \n" +
      "* @member \n" +
      "* @memberOf \n" +
      "* @modName \n" +
      "* @mods \n" +
      "* @name \n" +
      "* @namespace \n" +
      "* @ngInject \n" +
      "* @nocompile \n" +
      "* @pintomodule \n" +
      "* @property \n" +
      "* @requirecss \n" +
      "* @requires \n" +
      "* @since \n" +
      "* @static \n" +
      "* @supported\n" +
      "* @wizaction \n" +
      "* @wizmodule \n" +
      "*/");
  }

  public void testJsDocInfoPosition() throws IOException {
    SourceFile sourceFile = SourceFile.fromCode("comment-position-test.js",
        "   \n" +
        "  /**\n" +
        "   * A comment\n" +
        "   */\n" +
        "  function f(x) {}");
    Node script = parseFull(sourceFile.getCode());
    Preconditions.checkState(script.isScript());
    Node fn = script.getFirstChild();
    Preconditions.checkState(fn.isFunction());
    JSDocInfo jsdoc = fn.getJSDocInfo();

    assertThat(jsdoc.getOriginalCommentPosition()).isEqualTo(6);
    assertThat(sourceFile.getLineOfOffset(jsdoc.getOriginalCommentPosition())).isEqualTo(2);
    assertThat(sourceFile.getColumnOfOffset(jsdoc.getOriginalCommentPosition())).isEqualTo(2);
  }

  public void testGetOriginalCommentString() throws Exception {
    String comment = "* @desc This is a comment */";
    JSDocInfo info = parse(comment);
    assertThat(info.getOriginalCommentString()).isNull();
    info = parse(comment, true/* parseDocumentation */);
    assertThat(info.getOriginalCommentString()).isEqualTo(comment);
  }

  public void testParseNgInject1() throws Exception {
    assertThat(parse("@ngInject*/").isNgInject()).isTrue();
  }

  public void testParseNgInject2() throws Exception {
    parse("@ngInject \n@ngInject*/", "extra @ngInject tag");
  }

  public void testParseJaggerInject() throws Exception {
    assertThat(parse("@jaggerInject*/").isJaggerInject()).isTrue();
  }

  public void testParseJaggerInjectExtra() throws Exception {
    parse("@jaggerInject \n@jaggerInject*/", "extra @jaggerInject tag");
  }

  public void testParseJaggerModule() throws Exception {
    assertThat(parse("@jaggerModule*/").isJaggerModule()).isTrue();
  }

  public void testParseJaggerModuleExtra() throws Exception {
    parse("@jaggerModule \n@jaggerModule*/", "extra @jaggerModule tag");
  }

  public void testParseJaggerProvide() throws Exception {
    assertThat(parse("@jaggerProvide*/").isJaggerProvide()).isTrue();
  }

  public void testParseJaggerProvideExtra() throws Exception {
    parse("@jaggerProvide \n@jaggerProvide*/", "extra @jaggerProvide tag");
  }

  public void testParseJaggerProvidePromise() throws Exception {
    assertThat(parse("@jaggerProvidePromise*/").isJaggerProvidePromise()).isTrue();
  }

  public void testParseJaggerProvidePromiseExtra() throws Exception {
    parse("@jaggerProvidePromise \n@jaggerProvidePromise*/", "extra @jaggerProvidePromise tag");
  }


  public void testParseWizaction1() throws Exception {
    assertThat(parse("@wizaction*/").isWizaction()).isTrue();
  }

  public void testParseWizaction2() throws Exception {
    parse("@wizaction \n@wizaction*/", "extra @wizaction tag");
  }

  public void testParseDisposes1() throws Exception {
    assertThat(parse("@param x \n * @disposes x */").isDisposes()).isTrue();
  }

  public void testParseDisposes2() throws Exception {
    parse("@param x \n * @disposes */",
        true, "Bad type annotation. @disposes tag missing parameter name");
  }

  public void testParseDisposes3() throws Exception {
    assertThat(parse("@param x \n @param y\n * @disposes x, y */").isDisposes()).isTrue();
  }

  public void testParseDisposesUnknown() throws Exception {
    parse("@param x \n * @disposes x,y */",
        true,
        "Bad type annotation. @disposes parameter unknown or parameter specified multiple times");
  }

  public void testParseDisposesMultiple() throws Exception {
    parse("@param x \n * @disposes x,x */",
        true,
        "Bad type annotation. @disposes parameter unknown or parameter specified multiple times");
  }

  public void testParseDisposesAll1() throws Exception {
    assertThat(parse("@param x \n * @disposes * */").isDisposes()).isTrue();
  }

  public void testParseDisposesAll2() throws Exception {
    assertThat(parse("@param x \n * @disposes x,* */").isDisposes()).isTrue();
  }

  public void testParseDisposesAll3() throws Exception {
    parse("@param x \n * @disposes *, * */",
        true,
        "Bad type annotation. @disposes parameter unknown or parameter specified multiple times");
  }

  public void testTextExtents() {
    parse("@return {@code foo} bar \n *    baz. */",
        true, "Bad type annotation. type not recognized due to syntax error");
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
    assertThat(marker.getDescription()).isNotNull();
    assertThat(marker.getDescription().getItem()).isEqualTo(description);

    // Match positional information.
    assertThat(marker.getDescription().getStartLine())
        .isEqualTo(marker.getAnnotation().getStartLine());
    assertThat(marker.getDescription().getPositionOnStartLine()).isEqualTo(startCharno);
    assertThat(marker.getDescription().getEndLine()).isEqualTo(endLineno);
    assertThat(marker.getDescription().getPositionOnEndLine()).isEqualTo(endCharno);

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
  private JSDocInfo.Marker assertTypeInMarker(
      JSDocInfo.Marker marker, String typeName,
      int startLineno, int startCharno, int endLineno, int endCharno,
      boolean hasBrackets) {
    assertThat(marker.getType()).isNotNull();
    assertThat(marker.getType().getItem().isString()).isTrue();

    // Match the name and brackets information.
    String foundName = marker.getType().getItem().getString();

    assertThat(foundName).isEqualTo(typeName);
    assertThat(marker.getType().hasBrackets()).isEqualTo(hasBrackets);

    // Match position information.
    assertThat(marker.getType().getPositionOnStartLine()).isEqualTo(startCharno);
    assertThat(marker.getType().getPositionOnEndLine()).isEqualTo(endCharno);
    assertThat(marker.getType().getStartLine()).isEqualTo(startLineno);
    assertThat(marker.getType().getEndLine()).isEqualTo(endLineno);

    return marker;
  }

  /**
   * Asserts that a name field exists on the given marker.
   *
   * @param name The name expected in the name field.
   * @param startCharno The starting character of the text.
   * @return The marker, for chaining purposes.
   */
  @SuppressWarnings("deprecation")
  private JSDocInfo.Marker assertNameInMarker(JSDocInfo.Marker marker,
      String name, int startLine, int startCharno) {
    assertThat(marker.getName()).isNotNull();
    assertThat(marker.getName().getItem()).isEqualTo(name);

    assertThat(marker.getName().getPositionOnStartLine()).isEqualTo(startCharno);
    assertThat(marker.getName().getPositionOnEndLine()).isEqualTo(startCharno + name.length());

    assertThat(marker.getName().getStartLine()).isEqualTo(startLine);
    assertThat(marker.getName().getEndLine()).isEqualTo(startLine);

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

    assertThat(markers).isNotEmpty();

    int counter = 0;

    for (JSDocInfo.Marker marker : markers) {
      if (marker.getAnnotation() != null) {
        if (annotationName.equals(marker.getAnnotation().getItem())) {

          if (counter == index) {
            assertThat(marker.getAnnotation().getStartLine()).isEqualTo(startLineno);
            assertThat(marker.getAnnotation().getPositionOnStartLine()).isEqualTo(startCharno);
            assertThat(marker.getAnnotation().getEndLine()).isEqualTo(startLineno);
            assertThat(marker.getAnnotation().getPositionOnEndLine())
                .isEqualTo(startCharno + annotationName.length());

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
    assertThat(collection).contains(item);
  }

  private Node parseFull(String code, String... warnings) {
    TestErrorReporter testErrorReporter = new TestErrorReporter(null, warnings);
    Config config =
        new Config(extraAnnotations, extraSuppressions,
            true, LanguageMode.ECMASCRIPT3, false);

    ParseResult result = ParserRunner.parse(
        new SimpleSourceFile("source", false), code, config, testErrorReporter);

    assertTrue("some expected warnings were not reported",
        testErrorReporter.hasEncounteredAllWarnings());
    return result.ast;
  }

  @SuppressWarnings("unused")
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
        parseDocumentation, LanguageMode.ECMASCRIPT3, false);
    StaticSourceFile file = new SimpleSourceFile("testcode", false);
    Node associatedNode = new Node(Token.SCRIPT);
    associatedNode.setInputId(new InputId(file.getName()));
    associatedNode.setStaticSourceFile(file);

    JsDocInfoParser jsdocParser = new JsDocInfoParser(
        stream(comment),
        comment,
        0,
        associatedNode,
        file,
        config,
        errorReporter);

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

  private void assertTemplatizedTypeEquals(TemplateType key, JSType expected,
                                           JSTypeExpression te) {
    assertThat(resolve(te).getTemplateTypeMap().getTemplateType(key)).isEqualTo(expected);
  }
}
