/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.parsing.TypeTransformationParser;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.RecordTypeBuilder;
import com.google.javascript.rhino.testing.TestErrorReporter;
import java.util.Map.Entry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TypeTransformationTest extends CompilerTypeTestCase {

  private ImmutableMap<String, JSType> typeVars;
  private ImmutableMap<String, String> nameVars;
  private static JSType recordTypeTest, nestedRecordTypeTest, asynchRecord;

  static final String EXTRA_TYPE_DEFS =
      lines(
          "/** @type {number} */ Array.prototype.length;",
          "/** @typedef {!Array<?>} */ var ArrayAlias;",
          "",
          "/** @constructor */",
          "function Bar() {}",
          "",
          "/** @type {number} */",
          "var n = 10;");

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    errorReporter = new TestErrorReporter(null, null);
    initRecordTypeTests();
    typeVars =
        new ImmutableMap.Builder<String, JSType>()
            .put("S", getNativeStringType())
            .put("N", getNativeNumberType())
            .put("B", getNativeBooleanType())
            .put("BOT", getNativeNoType())
            .put("TOP", getNativeAllType())
            .put("UNK", getNativeUnknownType())
            .put("CHKUNK", getNativeCheckedUnknownType())
            .put("SO", getNativeStringObjectType())
            .put("NO", getNativeNumberObjectType())
            .put("BO", getNativeBooleanObjectType())
            .put("NULL", getNativeNullType())
            .put("OBJ", getNativeObjectType())
            .put("UNDEF", getNativeVoidType())
            .put("ARR", getNativeArrayType())
            .put("ARRNUM", type(getNativeArrayType(), getNativeNumberType()))
            .put("REC", recordTypeTest)
            .put("NESTEDREC", nestedRecordTypeTest)
            .put("ASYNCH", asynchRecord)
            .build();
    nameVars = new ImmutableMap.Builder<String, String>()
        .put("s", "string")
        .put("n", "number")
        .put("b", "boolean")
        .build();
  }

  @Test
  public void testTransformationWithValidBasicTypePredicate() {
    testTTL(getNativeNumberType(), "'number'");
  }

  @Test
  public void testTransformationWithBasicTypePredicateWithInvalidTypename() {
    testTTL(getNativeUnknownType(), "'foo'", "Reference to an unknown type name foo");
  }

  @Test
  public void testTransformationWithSingleTypeVar() {
    testTTL(getNativeStringType(), "S");
  }

  @Test
  public void testTransformationWithMultipleTypeVars() {
    testTTL(getNativeStringType(), "S");
    testTTL(getNativeNumberType(), "N");
  }

  @Test
  public void testTransformationWithValidUnionTypeOnlyVars() {
    testTTL(union(getNativeNumberType(), getNativeStringType()), "union(N, S)");
  }

  @Test
  public void testTransformationWithValidUnionTypeOnlyTypePredicates() {
    testTTL(union(getNativeNumberType(), getNativeStringType()), "union('number', 'string')");
  }

  @Test
  public void testTransformationWithValidUnionTypeMixed() {
    testTTL(union(getNativeNumberType(), getNativeStringType()), "union(S, 'number')");
  }

  @Test
  public void testTransformationWithUnknownParameter() {
    // Returns ? because foo is not defined
    testTTL(
        getNativeUnknownType(),
        "union(foo, 'number')",
        "Reference to an unknown type variable foo");
  }

  @Test
  public void testTransformationWithUnknownParameter2() {
    // Returns ? because foo is not defined
    testTTL(getNativeUnknownType(), "union(N, 'foo')", "Reference to an unknown type name foo");
  }

  @Test
  public void testTransformationWithNestedUnionInFirstParameter() {
    testTTL(
        union(getNativeNumberType(), getNativeNullType(), getNativeStringType()),
        "union(union(N, 'null'), S)");
  }

  @Test
  public void testTransformationWithNestedUnionInSecondParameter() {
    testTTL(
        union(getNativeNumberType(), getNativeNullType(), getNativeStringType()),
        "union(N, union('null', S))");
  }

  @Test
  public void testTransformationWithRepeatedTypePredicate() {
    testTTL(getNativeNumberType(), "union('number', 'number')");
  }

  @Test
  public void testTransformationWithUndefinedTypeVar() {
    testTTL(getNativeUnknownType(), "foo", "Reference to an unknown type variable foo");
  }

  @Test
  public void testTransformationWithTrueEqtypeConditional() {
    testTTL(getNativeStringType(), "cond(eq(N, N), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseEqtypeConditional() {
    testTTL(getNativeNumberType(), "cond(eq(N, S), 'string', 'number')");
  }

  @Test
  public void testTransformationWithTrueSubtypeConditional() {
    testTTL(getNativeStringType(), "cond( sub('Number', 'Object'), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseSubtypeConditional() {
    testTTL(getNativeNumberType(), "cond( sub('Number', 'String'), 'string', 'number')");
  }

  @Test
  public void testTransformationWithTrueStreqConditional() {
    testTTL(getNativeStringType(), "cond(streq(n, 'number'), 'string', 'number')");
  }

  @Test
  public void testTransformationWithTrueStreqConditional2() {
    testTTL(getNativeStringType(), "cond(streq(n, n), 'string', 'number')");
  }

  @Test
  public void testTransformationWithTrueStreqConditional3() {
    testTTL(getNativeStringType(), "cond(streq('number', 'number'), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseStreqConditional() {
    testTTL(getNativeNumberType(), "cond(streq('number', 'foo'), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseStreqConditional2() {
    testTTL(getNativeNumberType(), "cond(streq(n, 'foo'), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseStreqConditional3() {
    testTTL(getNativeNumberType(), "cond(streq(n, s), 'string', 'number')");
  }

  @Test
  public void testTransformationWithInvalidEqConditional() {
    // It returns number since a failing boolean expression returns false
    testTTL(
        getNativeNumberType(),
        "cond(eq(foo, S), 'string', 'number')",
        "Reference to an unknown type variable foo");
  }

  @Test
  public void testTransformationWithInvalidStreqConditional() {
    // It returns number since a failing boolean expression returns false
    testTTL(
        getNativeNumberType(),
        "cond(streq(foo, s), 'string', 'number')",
        "Reference to an unknown string variable foo");
  }

  @Test
  public void testTransformationWithNestedExpressionInBooleanFirstParam() {
    testTTL(
        getNativeStringType(),
        "cond( eq( cond(eq(N, N), 'string', 'number'), 'string')," + "'string', " + "'number')");
  }

  @Test
  public void testTransformationWithNestedExpressionInBooleanSecondParam() {
    testTTL(
        getNativeStringType(),
        "cond( eq( 'string', cond(eq(N, N), 'string', 'number'))," + "'string', " + "'number')");
  }

  @Test
  public void testTransformationWithNestedExpressionInIfBranch() {
    testTTL(
        getNativeStringObjectType(),
        "cond(eq(N, N), cond(eq(N, S), 'string', 'String'), 'number')");
  }

  @Test
  public void testTransformationWithNestedExpressionInElseBranch() {
    testTTL(
        getNativeStringObjectType(),
        "cond(eq(N, S), 'number', cond(eq(N, S), 'string', 'String'))");
  }

  @Test
  public void testTransformationWithMapunionMappingEverythingToString() {
    testTTL(getNativeStringType(), "mapunion(union(S, N), (x) => S)");
  }

  @Test
  public void testTransformationWithMapunionIdentity() {
    testTTL(union(getNativeNumberType(), getNativeStringType()), "mapunion(union(N, S), (x) => x)");
  }

  @Test
  public void testTransformationWithMapunionWithUnionEvaluatedToANonUnion() {
    testTTL(getNativeNumberType(), "mapunion(union(N, 'number'), (x) => x)");
  }

  @Test
  public void testTransformationWithMapunionFilterWithOnlyString() {
    testTTL(getNativeStringType(), "mapunion(union(S, B, N), (x) => cond(eq(x, S), x, BOT))");
  }

  @Test
  public void testTransformationWithMapunionOnSingletonStringToNumber() {
    testTTL(getNativeNumberType(), "mapunion(S, (x) => cond(eq(x, S), N, BOT))");
  }

  @Test
  public void testTransformationWithNestedUnionInMapunionFilterString() {
    testTTL(
        union(getNativeNumberType(), getNativeBooleanType()),
        "mapunion(union(union(S, B), union(N, S)), (x) => cond(eq(x, S), BOT, x))");
  }

  @Test
  public void testTransformationWithNestedMapunionInMapFunctionBody() {
    testTTL(
        getNativeStringType(),
        "mapunion(union(S, B),"
            + "(x) => mapunion(union(S, N), "
            + "(y) => cond(eq(x, y), x, BOT)))");
  }

  @Test
  public void testTransformationWithObjectUseCase() {
    testTTL(
        getNativeObjectType(),
        "mapunion("
            + "union(S, N, B, NULL, UNDEF, ARR),"
            + "(x) => "
            + "cond(eq(x, S), SO,"
            + "cond(eq(x, N), NO,"
            + "cond(eq(x, B), BO,"
            + "cond(eq(x, NULL), OBJ,"
            + "cond(eq(x, UNDEF), OBJ,"
            + "x ))))))");
  }

  // none() is evaluated to bottom in TTL expressions, but if the overall expression evaluates
  // to bottom, we return unknown to the context.
  @Test
  public void testTransformatioWithNoneType() {
    testTTL(getNativeUnknownType(), "none()");
  }

  // The conditional is true, so we follow the THEN branch, and the bottom result is returned
  // to the context as unknown.
  @Test
  public void testTransformatioWithNoneTypeInConditional() {
    testTTL(getNativeUnknownType(), "cond(eq(BOT, none()), none(), N)");
  }

  @Test
  public void testTransformatioWithNoneTypeInMapunionFilterString() {
    testTTL(getNativeStringType(), "mapunion(union(S, B, N), (x) => cond(eq(x, S), x, none()))");
  }

  @Test
  public void testTransformatioWithAllType() {
    testTTL(getNativeAllType(), "all()");
  }

  @Test
  public void testTransformatioWithAllTypeInConditional() {
    testTTL(getNativeAllType(), "cond(eq(TOP, all()), all(), N)");
  }

  @Test
  public void testTransformatioWithAllTypeMixUnion() {
    testTTL(getNativeAllType(), "mapunion(union(S, B, N), (x) => cond(eq(x, S), x, all()))");
  }

  @Test
  public void testTransformatioWithUnknownType() {
    testTTL(getNativeUnknownType(), "unknown()");
  }

  @Test
  public void testTransformatioWithUnknownTypeInConditional() {
    testTTL(getNativeNumberType(), "cond(eq(UNK, unknown()), N, S)");
  }

  @Test
  public void testTransformatioWithUnknownTypeInMapunionStringToUnknown() {
    testTTL(
        getNativeUnknownType(), "mapunion(union(S, B, N), (x) => cond(eq(x, S), x, unknown()))");
  }

  @Test
  public void testTransformationWithTemplatizedType() {
    testTTL(type(getNativeArrayType(), getNativeNumberType()), "type('Array', 'number')");
  }

  @Test
  public void testTransformationWithTemplatizedType2() {
    testTTL(type(getNativeArrayType(), getNativeNumberType()), "type(ARR, 'number')");
  }

  @Test
  public void testTransformationWithTemplatizedType3() {
    testTTL(type(getNativeArrayType(), getNativeNumberType()), "type(ARR, N)");
  }

  @Test
  public void testTransformationWithTemplatizedTypeInvalidBaseType() {
    testTTL(
        getNativeUnknownType(),
        "type('string', 'number')",
        "The type string cannot be templatized");
  }

  @Test
  public void testTransformationWithTemplatizedTypeInvalidBaseType2() {
    testTTL(getNativeUnknownType(), "type(S, 'number')", "The type string cannot be templatized");
  }

  @Test
  public void testTransformationWithTemplatizedTypeInvalidBaseType3() {
    testTTL(
        getNativeUnknownType(),
        "type('ArrayAlias', number)",
        "The type Array<?> cannot be templatized");
  }

  @Test
  public void testTransformationWithRawTypeOf() {
    testTTL(getNativeArrayType(), "rawTypeOf(type('Array', 'number'))");
  }

  @Test
  public void testTransformationWithRawTypeOf2() {
    testTTL(getNativeArrayType(), "rawTypeOf(ARRNUM)");
  }

  @Test
  public void testTransformationWithNestedRawTypeOf() {
    testTTL(getNativeArrayType(), "rawTypeOf(type('Array', rawTypeOf(ARRNUM)))");
  }

  @Test
  public void testTransformationWithInvalidRawTypeOf() {
    testTTL(
        getNativeUnknownType(),
        "rawTypeOf(N)",
        "Expected templatized type in rawTypeOf found number");
  }

  @Test
  public void testTransformationWithTemplateTypeOf() {
    testTTL(getNativeNumberType(), "templateTypeOf(type('Array', 'number'), 0)");
  }

  @Test
  public void testTransformationWithTemplateTypeOf2() {
    testTTL(getNativeNumberType(), "templateTypeOf(ARRNUM, 0)");
  }

  @Test
  public void testTransformationWithNestedTemplateTypeOf() {
    testTTL(
        getNativeNumberType(),
        "templateTypeOf(templateTypeOf(type('Array', type('Array', 'number')), 0), 0)");
  }

  @Test
  public void testTransformationWithInvalidTypeTemplateTypeOf() {
    testTTL(
        getNativeUnknownType(),
        "templateTypeOf(N, 0)",
        "Expected templatized type in templateTypeOf found number");
  }

  @Test
  public void testTransformationWithInvalidIndexTemplateTypeOf() {
    testTTL(
        getNativeUnknownType(),
        "templateTypeOf(ARRNUM, 2)",
        "Index out of bounds in templateTypeOf: expected a number less than 1, found 2");
  }

  @Test
  public void testTransformationWithRecordType() {
    testTTL(record("x", getNativeNumberType()), "record({x:'number'})");
  }

  @Test
  public void testTransformationWithRecordType2() {
    testTTL(record("0", getNativeNumberType()), "record({0:'number'})");
  }

  @Test
  public void testTransformationWithRecordTypeMultipleProperties() {
    testTTL(
        record("x", getNativeNumberType(), "y", getNativeStringType()),
        "record({x:'number', y:S})");
  }

  @Test
  public void testTransformationWithNestedRecordType() {
    testTTL(
        record("x", record("z", getNativeBooleanType()), "y", getNativeStringType()),
        "record({x:record({z:B}), y:S})");
  }

  @Test
  public void testTransformationWithNestedRecordType2() {
    testTTL(record("x", getNativeNumberType()), "record(record({x:N}))");
  }

  @Test
  public void testTransformationWithEmptyRecordType() {
    testTTL(record(), "record({})");
  }

  @Test
  public void testTransformationWithMergeRecord() {
    testTTL(
        record("x", getNativeNumberType(), "y", getNativeStringType(), "z", getNativeBooleanType()),
        "record({x:N}, {y:S}, {z:B})");
  }

  @Test
  public void testTransformationWithMergeDuplicatedRecords() {
    testTTL(record("x", getNativeNumberType()), "record({x:N}, {x:N}, {x:N})");
  }

  @Test
  public void testTransformationWithMergeRecordTypeWithEmpty() {
    testTTL(record("x", getNativeNumberType()), "record({x:N}, {})");
  }

  @Test
  public void testTransformationWithInvalidRecordType() {
    testTTL(getNativeUnknownType(), "record(N)", "Expected a record type, found number");
  }

  @Test
  public void testTransformationWithInvalidMergeRecordType() {
    testTTL(getNativeUnknownType(), "record({x:N}, N)", "Expected a record type, found number");
  }

  @Test
  public void testTransformationWithTTLTypeTransformationInFirstParamMapunion() {
    testTTL(
        union(getNativeNumberType(), getNativeStringType()),
        "mapunion(templateTypeOf(type(ARR, union(N, S)), 0), (x) => x)");
  }

  @Test
  public void testTransformationWithInvalidNestedMapunion() {
    testTTL(
        getNativeUnknownType(),
        "mapunion(union(S, B),"
            + "(x) => mapunion(union(S, N), "
            + "(x) => cond(eq(x, x), x, BOT)))",
        "The variable x is already defined");
  }

  @Test
  public void testTransformationWithTTLRecordWithReference() {
    testTTL(
        record(
            "number",
            getNativeNumberType(),
            "string",
            getNativeStringType(),
            "boolean",
            getNativeBooleanType()),
        "record({[n]:N, [s]:S, [b]:B})");
  }

  @Test
  public void testTransformationWithTTLRecordWithInvalidReference() {
    testTTL(
        getNativeUnknownType(),
        "record({[Foo]:N})",
        "Expected a record type, found ?",
        "Reference to an unknown name variable Foo");
  }

  @Test
  public void testTransformationWithMaprecordMappingEverythingToString() {
    // {n:number, s:string, b:boolean}
    // is transformed to
    // {n:string, s:string, b:string}
    testTTL(
        record("n", getNativeStringType(), "s", getNativeStringType(), "b", getNativeStringType()),
        "maprecord(REC, (k, v) => record({[k]:S}))");
  }

  @Test
  public void testTransformationWithMaprecordIdentity() {
    // {n:number, s:string, b:boolean} remains the same
    testTTL(recordTypeTest, "maprecord(REC, (k, v) => record({[k]:v}))");
  }

  @Test
  public void testTransformationWithMaprecordDeleteEverything() {
    // {n:number, s:string, b:boolean}
    // is transformed to object type
    testTTL(getNativeObjectType(), "maprecord(REC, (k, v) => BOT)");
  }

  @Test
  public void testTransformationWithInvalidMaprecord() {
    testTTL(
        getNativeUnknownType(),
        "maprecord(REC, (k, v) => 'number')",
        "The body of a maprecord function must evaluate to a record type "
            + "or a no type, found number");
  }

  @Test
  public void testTransformationWithMaprecordFilterWithOnlyString() {
    // {n:number, s:string, b:boolean}
    // is transformed to
    // {s:string}
    testTTL(
        record("s", getNativeStringType()),
        "maprecord(REC, (k, v) => cond(eq(v, S), record({[k]:v}), BOT))");
  }

  @Test
  public void testTransformationWithInvalidMaprecordFirstParam() {
    testTTL(
        getNativeUnknownType(),
        "maprecord(N, (k, v) => BOT)",
        "The first parameter of a maprecord must be a record type, found number");
  }

  @Test
  public void testTransformationWithObjectInMaprecord() {
    testTTL(getNativeObjectType(), "maprecord(OBJ, (k, v) => record({[k]:v}))");
  }

  @Test
  public void testTransformationWithUnionInMaprecord() {
    testTTL(
        getNativeUnknownType(),
        "maprecord(union(record({n:N}), S), (k, v) => record({[k]:v}))",
        "The first parameter of a maprecord must be a record type, "
            + "found (string|{n: number})");
  }

  @Test
  public void testTransformationWithUnionOfRecordsInMaprecord() {
    testTTL(
        getNativeUnknownType(),
        "maprecord(union(record({n:N}), record({s:S})),  (k, v) => record({[k]:v}))",
        "The first parameter of a maprecord must be a record type, "
            + "found ({n: number}|{s: string})");
  }

  @Test
  public void testTransformationWithNestedRecordInMaprecordFilterOneLevelString() {
    // {s:string, r:{s:string, b:boolean}}
    // is transformed to
    // {r:{s:string, b:boolean}}
    testTTL(
        record("r", record("s", getNativeStringType(), "b", getNativeBooleanType())),
        "maprecord(NESTEDREC, (k, v) => cond(eq(v, S), BOT, record({[k]:v})))");
  }

  @Test
  public void testTransformationWithNestedRecordInMaprecordFilterTwoLevelsString() {
    // {s:string, r:{s:string, b:boolean}}
    // is transformed to
    // {r:{b:boolean}}
    testTTL(
        record("r", record("b", getNativeBooleanType())),
        lines(
            "maprecord(NESTEDREC,",
            "    (k1, v1) => ",
            "        cond(sub(v1, 'Object'), ",
            "            maprecord(v1, (k2, v2) => ",
            "                cond(eq(v2, S), BOT, record({[k1]:record({[k2]:v2})}))),",
            "            cond(eq(v1, S), BOT, record({[k1]:v1}))))"));
  }

  @Test
  public void testTransformationWithNestedIdentityOneLevel() {
    // {r:{n:number, s:string}}
    testTTL(
        record("r", record("b", getNativeBooleanType(), "s", getNativeStringType())),
        lines(
            "maprecord(record({r:record({b:B, s:S})}),",
            "    (k1, v1) => ",
            "        maprecord(v1, ",
            "            (k2, v2) => ",
            "                record({[k1]:record({[k2]:v2})})))"));
  }

  @Test
  public void testTransformationWithNestedIdentityOneLevel2() {
    // {r:{n:number, s:string}}
    testTTL(
        record("r", record("b", getNativeBooleanType(), "s", getNativeStringType())),
        lines(
            "maprecord(record({r:record({b:B, s:S})}),",
            "    (k1, v1) =>",
            "        record({[k1]:",
            "            maprecord(v1, ",
            "                (k2, v2) => record({[k2]:v2}))}))"));
  }

  @Test
  public void testTransformationWithNestedIdentityTwoLevels() {
    // {r:{r2:{n:number, s:string}}}
    testTTL(
        record("r", record("r2", record("n", getNativeNumberType(), "s", getNativeStringType()))),
        lines(
            "maprecord(record({r:record({r2:record({n:N, s:S})})}),",
            "    (k1, v1) => ",
            "        maprecord(v1, ",
            "            (k2, v2) => ",
            "                maprecord(v2, ",
            "                    (k3, v3) =>",
            "                        record({[k1]:",
            "                            record({[k2]:",
            "                                record({[k3]:v3})})}))))"));
  }

  @Test
  public void testTransformationWithNestedIdentityTwoLevels2() {
    // {r:{r2:{n:number, s:string}}}
    testTTL(
        record("r", record("r2", record("n", getNativeNumberType(), "s", getNativeStringType()))),
        lines(
            "maprecord(record({r:record({r2:record({n:N, s:S})})}),",
            "    (k1, v1) => ",
            "        record({[k1]:",
            "            maprecord(v1, ",
            "                (k2, v2) => ",
            "                    record({[k2]:",
            "                        maprecord(v2, ",
            "                            (k3, v3) =>",
            "                                record({[k3]:v3}))}))}))"));
  }

  @Test
  public void testTransformationWithNestedIdentityThreeLevels() {
    // {r:{r2:{r3:{n:number, s:string}}}}
    testTTL(
        record(
            "r",
            record(
                "r2",
                record("r3", record("n", getNativeNumberType(), "s", getNativeStringType())))),
        lines(
            "maprecord(record({r:record({r2:record({r3:record({n:N, s:S})})})}),",
            "    (k1, v1) => ",
            "        maprecord(v1, ",
            "            (k2, v2) => ",
            "                maprecord(v2, ",
            "                    (k3, v3) =>",
            "                        maprecord(v3,",
            "                            (k4, v4) =>",
            "                                record({[k1]:",
            "                                    record({[k2]:",
            "                                        record({[k3]:",
            "                                            record({[k4]:v4})})})})))))"));
  }

  @Test
  public void testTransformationWithNestedIdentityThreeLevels2() {
    // {r:{r2:{r3:{n:number, s:string}}}}
    testTTL(
        record(
            "r",
            record(
                "r2",
                record("r3", record("n", getNativeNumberType(), "s", getNativeStringType())))),
        lines(
            "maprecord(record({r:record({r2:record({r3:record({n:N, s:S})})})}),",
            "    (k1, v1) => ",
            "        record({[k1]:",
            "            maprecord(v1, ",
            "                (k2, v2) => ",
            "                    record({[k2]:",
            "                        maprecord(v2, ",
            "                            (k3, v3) =>",
            "                                record({[k3]:",
            "                                    maprecord(v3,",
            "                                        (k4, v4) =>",
            "                                            record({[k4]:v4}))}))}))}))"));
  }

  @Test
  public void testTransformationWithNestedRecordDeleteLevelTwoAndThree() {
    // {r:{r2:{r3:{n:number, s:string}}}}
    // is transformed into
    // {r:{n:number, s:string}}
    testTTL(
        record("r", record("n", getNativeNumberType(), "s", getNativeStringType())),
        lines(
            "maprecord(record({r:record({r2:record({r3:record({n:N, s:S})})})}),",
            "    (k1, v1) => ",
            "        maprecord(v1, ",
            "            (k2, v2) => ",
            "                maprecord(v2, ",
            "                    (k3, v3) =>",
            "                        maprecord(v3,",
            "                            (k4, v4) =>",
            "                                record({[k1]:",
            "                                    record({[k4]:v4})})))))"));
  }

  @Test
  public void testTransformationWithNestedRecordDeleteLevelTwoAndThree2() {
    // {r:{r2:{r3:{n:number, s:string}}}}
    // is transformed into
    // {r:{n:number, s:string}}
    testTTL(
        record("r", record("n", getNativeNumberType(), "s", getNativeStringType())),
        lines(
            "maprecord(record({r:record({r2:record({r3:record({n:N, s:S})})})}),",
            "    (k1, v1) => ",
            "        record({[k1]:",
            "            maprecord(v1, ",
            "                (k2, v2) => ",
            "                    maprecord(v2, ",
            "                        (k3, v3) =>",
            "                            maprecord(v3,",
            "                                (k4, v4) =>",
            "                                    record({[k4]:v4}))))}))"));
  }

  @Test
  public void testTransformationWithNestedRecordCollapsePropertiesToRecord() {
    // {a:Array, b:{n:number}}
    // is transformed to
    // {foo:{n:number}}
    testTTL(
        record("foo", record("n", getNativeNumberType())),
        "maprecord(record({a:ARR, b:record({n:N})}), (k, v) => record({foo:v}))");
  }

  @Test
  public void testTransformationWithNestedRecordCollapsePropertiesToType() {
    // {a:{n:number}, b:Array}
    // is transformed to
    // {foo:Array}
    testTTL(
        record("foo", getNativeArrayType()),
        "maprecord(record({a:record({n:N}), b:ARR}), (k, v) => record({foo:v}))");
  }

  @Test
  public void testTransformationWithNestedRecordCollapsePropertiesJoinRecords() {
    // {a:{n:number}, b:{s:Array}}
    // is transformed to
    // {foo:{n:number, s:Array}}
    testTTL(
        record("foo", record("n", getNativeNumberType(), "s", getNativeArrayType())),
        "maprecord(record({a:record({n:N}), b:record({s:ARR})}), (k, v) => record({foo:v}))");
  }

  @Test
  public void testTransformationWithNestedRecordCollapsePropertiesJoinRecords2() {
    // {a:{n:number, {x:number}}, b:{s:Array, {y:number}}}
    // is transformed to
    // {foo:{n:number, s:Array, r:{x:number, y:number}}}
    testTTL(
        record(
            "foo",
            record(
                "n",
                getNativeNumberType(),
                "s",
                getNativeArrayType(),
                "r",
                record("x", getNativeNumberType(), "y", getNativeNumberType()))),
        lines(
            "maprecord(",
            "    record({a:record({n:N, r:record({x:N})}), b:record({s:ARR, r:record({y:N})})}),",
            "    (k, v) => record({foo:v}))"));
  }

  @Test
  public void testTransformationWithAsynchUseCase() {
    // TODO(lpino): Use the type Promise instead of Array
    // {service:Array<number>}
    // is transformed to
    // {service:number}
    testTTL(
        record("service", getNativeNumberType()),
        lines(
            "cond(",
            "    sub(ASYNCH, 'Object'),",
            "    maprecord(",
            "        ASYNCH,",
            "        (k, v) =>",
            "            cond(",
            "                eq(rawTypeOf(v), 'Array'),",
            "                record({[k]:templateTypeOf(v, 0)}),",
            "                record({[k]:'undefined'}))),",
            "    ASYNCH)"));
  }

  @Test
  public void testTransformationWithInvalidNestedMaprecord() {
    testTTL(
        getNativeUnknownType(),
        "maprecord(NESTEDREC, (k, v) => maprecord(v, (k, v) => BOT))",
        "The variable k is already defined");
  }

  @Test
  public void testTransformationWithMaprecordAndStringEquivalence() {
    testTTL(
        record("bool", getNativeNumberType(), "str", getNativeStringType()),
        "maprecord(record({bool:B, str:S}),"
            + "(k, v) => record({[k]:cond(streq(k, 'bool'), N, v)}))");
  }

  @Test
  public void testTransformationWithTypeOfVar() {
    testTTL(getNativeNumberType(), "typeOfVar('n')");
  }

  @Test
  public void testTransformationWithUnknownTypeOfVar() {
    testTTL(getNativeUnknownType(), "typeOfVar('foo')", "Variable foo is undefined in the scope");
  }

  @Test
  public void testTransformationWithTrueIsConstructorConditional() {
    testTTL(getNativeStringType(), "cond(isCtor(typeOfVar('Bar')), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseIsConstructorConditional() {
    testTTL(getNativeNumberType(), "cond(isCtor(N), 'string', 'number')");
  }

  @Test
  public void testTransformationWithTrueIsTemplatizedConditional() {
    testTTL(getNativeStringType(), "cond(isTemplatized(type(ARR, N)), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseIsTemplatizedConditional() {
    testTTL(getNativeNumberType(), "cond(isTemplatized(ARR), 'string', 'number')");
  }

  @Test
  public void testTransformationWithTrueIsRecordConditional() {
    testTTL(getNativeStringType(), "cond(isRecord(REC), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseIsRecordConditional() {
    testTTL(getNativeNumberType(), "cond(isRecord(N), 'string', 'number')");
  }

  @Test
  public void testTransformationWithTrueIsDefinedConditional() {
    testTTL(getNativeStringType(), "cond(isDefined(N), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseIsDefinedConditional() {
    testTTL(getNativeNumberType(), "cond(isDefined(Foo), 'string', 'number')");
  }

  @Test
  public void testTransformationWithTrueIsUnknownConditional() {
    testTTL(getNativeStringType(), "cond(isUnknown(UNK), 'string', 'number')");
  }

  @Test
  public void testTransformationWithTrueIsUnknownConditional2() {
    testTTL(getNativeStringType(), "cond(isUnknown(CHKUNK), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseIsUnknownConditional() {
    testTTL(getNativeNumberType(), "cond(isUnknown(N), 'string', 'number')");
  }

  @Test
  public void testTransformationWithTrueAndConditional() {
    testTTL(getNativeStringType(), "cond(isDefined(N) && isDefined(N), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseAndConditional() {
    testTTL(getNativeNumberType(), "cond(isDefined(N) && isDefined(Foo), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseAndConditional2() {
    testTTL(getNativeNumberType(), "cond(isDefined(Foo) && isDefined(N), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseAndConditional3() {
    testTTL(getNativeNumberType(), "cond(isDefined(Foo) && isDefined(Foo), 'string', 'number')");
  }

  @Test
  public void testTransformationWithTrueOrConditional() {
    testTTL(getNativeStringType(), "cond(isDefined(N) || isDefined(N), 'string', 'number')");
  }

  @Test
  public void testTransformationWithTrueOrConditional2() {
    testTTL(getNativeStringType(), "cond(isDefined(Foo) || isDefined(N), 'string', 'number')");
  }

  @Test
  public void testTransformationWithTrueOrConditional3() {
    testTTL(getNativeStringType(), "cond(isDefined(N) || isDefined(Foo), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseOrConditional() {
    testTTL(getNativeNumberType(), "cond(isDefined(Foo) || isDefined(Foo), 'string', 'number')");
  }

  @Test
  public void testTransformationWithTrueNotConditional3() {
    testTTL(getNativeStringType(), "cond(!isDefined(Foo), 'string', 'number')");
  }

  @Test
  public void testTransformationWithFalseNotConditional() {
    testTTL(getNativeNumberType(), "cond(!isDefined(N), 'string', 'number')");
  }

  @Test
  public void testTransformationWithInstanceOf() {
    testTTL(getNativeNumberObjectType(), "instanceOf(typeOfVar('Number'))");
  }

  @Test
  public void testTransformationWithInvalidInstanceOf() {
    testTTL(getNativeUnknownType(), "instanceOf(N)", "Expected a constructor type, found number");
  }

  @Test
  public void testTransformationWithInvalidInstanceOf2() {
    testTTL(
        getNativeUnknownType(),
        "instanceOf(foo)",
        "Expected a constructor type, found Unknown",
        "Reference to an unknown type variable foo");
  }

  @Test
  public void testTransformationWithTypeExpr() {
    testTTL(getNativeNumberType(), "typeExpr('number')");
  }

  @Test
  public void testParserWithTTLNativeTypeExprUnion() {
    testTTL(union(getNativeNumberType(), getNativeBooleanType()), "typeExpr('number|boolean')");
  }

  @Test
  public void testParserWithTTLNativeTypeExprRecord() {
    testTTL(
        record("foo", getNativeNumberType(), "bar", getNativeBooleanType()),
        "typeExpr('{foo:number, bar:boolean}')");
  }

  @Test
  public void testParserWithTTLNativeTypeExprNullable() {
    testTTL(union(getNativeNumberType(), getNativeNullType()), "typeExpr('?number')");
  }

  @Test
  public void testParserWithTTLNativeTypeExprNonNullable() {
    testTTL(getNativeNumberType(), "typeExpr('!number')");
  }

  @Test
  public void testTransformationPrintType() {
    testTTL(getNativeNumberType(), "printType('Test message: ', N)");
  }

  @Test
  public void testTransformationPrintType2() {
    testTTL(recordTypeTest, "printType('Test message: ', REC)");
  }

  @Test
  public void testTransformationPropType() {
    testTTL(getNativeNumberType(), "propType('a', record({a:N, b:record({x:B})}))");
  }

  @Test
  public void testTransformationPropType2() {
    testTTL(record("x", getNativeBooleanType()), "propType('b', record({a:N, b:record({x:B})}))");
  }

  @Test
  public void testTransformationPropTypeNotFound() {
    testTTL(getNativeUnknownType(), "propType('c', record({a:N, b:record({x:B})}))");
  }

  @Test
  public void testTransformationPropTypeInvalid() {
    testTTL(getNativeUnknownType(), "propType('c', N)", "Expected object type, found number");
  }

  @Test
  public void testTransformationInstanceObjectToRecord() {
    testTTL(getNativeObjectType(), "record(type(OBJ, N))");
  }

  @Test
  public void testTransformationInstanceObjectToRecord2() {
    // TODO(bradfordcsmith): Define Array.prototype.length using externs instead.
    getNativeArrayType()
        .defineDeclaredProperty("length", getNativeNumberType(), /* propertyNode */ null);
    testTTL(record("length", getNativeNumberType()), "record(type(ARR, N))");
  }

  @Test
  public void testTransformationInstanceObjectToRecordInvalid() {
    testTTL(
        getNativeUnknownType(),
        "record(union(OBJ, NULL))",
        "Expected a record type, found (Object|null)");
  }

  private void initRecordTypeTests() {
    // {n:number, s:string, b:boolean}
    recordTypeTest =
        record("n", getNativeNumberType(), "s", getNativeStringType(), "b", getNativeBooleanType());
    // {n:number, r:{s:string, b:boolean}}
    nestedRecordTypeTest =
        record(
            "s",
            getNativeStringType(),
            "r",
            record("s", getNativeStringType(), "b", getNativeBooleanType()));
    // {service:Array<number>}
    asynchRecord = record("service", type(getNativeArrayType(), getNativeNumberType()));
  }

  private JSType union(JSType... variants) {
    JSType type = createUnionType(variants);
    assertThat(type.isUnionType()).isTrue();
    return type;
  }

  private JSType type(ObjectType baseType, JSType... templatizedTypes) {
    return createTemplatizedType(baseType, templatizedTypes);
  }

  private JSType record() {
    return record(ImmutableMap.<String, JSType>of());
  }

  private JSType record(String p1, JSType t1) {
    return record(ImmutableMap.<String, JSType>of(p1, t1));
  }

  private JSType record(String p1, JSType t1, String p2, JSType t2) {
    return record(ImmutableMap.<String, JSType>of(p1, t1, p2, t2));
  }

  private JSType record(String p1, JSType t1, String p2, JSType t2,
      String p3, JSType t3) {
    return record(ImmutableMap.<String, JSType>of(p1, t1, p2, t2, p3, t3));
  }

  private JSType record(ImmutableMap<String, JSType> props) {
    RecordTypeBuilder builder = createRecordTypeBuilder();
    for (Entry<String, JSType> e : props.entrySet()) {
      builder.addProperty(e.getKey(), e.getValue(), null);
    }
    return builder.build();
  }

  private void testTTL(JSType expectedType, String ttlExp,
      String... expectedWarnings) {
    TypeTransformationParser ttlParser = new TypeTransformationParser(ttlExp,
        SourceFile.fromCode("[testcode]", ttlExp), errorReporter, 0, 0);
    // Run the test if the parsing was successful
    if (ttlParser.parseTypeTransformation()) {
      Node ast = ttlParser.getTypeTransformationAst();
      // Create the scope using the extra definitions
      Node extraTypeDefs = compiler.parseTestCode(EXTRA_TYPE_DEFS);
      TypedScope scope = new TypedScopeCreator(compiler).createScope(
          extraTypeDefs, null);
      // Evaluate the type transformation
      TypeTransformation typeTransformation =
          new TypeTransformation(compiler, scope);
      JSType resultType = typeTransformation.eval(ast, typeVars, nameVars);
      checkReportedWarningsHelper(expectedWarnings);
      assertTypeEquals(expectedType, resultType);
    }
  }
}
