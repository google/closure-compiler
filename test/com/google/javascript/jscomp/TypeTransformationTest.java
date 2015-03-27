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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.parsing.TypeTransformationParser;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.RecordTypeBuilder;
import com.google.javascript.rhino.testing.TestErrorReporter;

import java.util.Map.Entry;

public final class TypeTransformationTest extends CompilerTypeTestCase {

  private ImmutableMap<String, JSType> typeVars;
  private ImmutableMap<String, String> nameVars;
  private static JSType recordTypeTest, nestedRecordTypeTest, asynchRecord;

  static final String EXTRA_TYPE_DEFS =
      "/** @constructor */\n"
          + "function Bar() {}"
          + "/** @type {number} */"
          + "var n = 10;";

  @Override
  public void setUp() {
    super.setUp();
    errorReporter = new TestErrorReporter(null, null);
    initRecordTypeTests();
    typeVars = new ImmutableMap.Builder<String, JSType>()
        .put("S", STRING_TYPE)
        .put("N", NUMBER_TYPE)
        .put("B", BOOLEAN_TYPE)
        .put("BOT", NO_TYPE)
        .put("TOP", ALL_TYPE)
        .put("UNK", UNKNOWN_TYPE)
        .put("CHKUNK", CHECKED_UNKNOWN_TYPE)
        .put("SO", STRING_OBJECT_TYPE)
        .put("NO", NUMBER_OBJECT_TYPE)
        .put("BO", BOOLEAN_OBJECT_TYPE)
        .put("NULL", NULL_TYPE)
        .put("OBJ", OBJECT_TYPE)
        .put("UNDEF", VOID_TYPE)
        .put("ARR", ARRAY_TYPE)
        .put("ARRNUM", type(ARRAY_TYPE, NUMBER_TYPE))
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

  public void testTransformationWithValidBasicTypePredicate() {
    testTTL(NUMBER_TYPE, "'number'");
  }

  public void testTransformationWithBasicTypePredicateWithInvalidTypename() {
    testTTL(UNKNOWN_TYPE, "'foo'",
        "Reference to an unknown type name foo");
  }

  public void testTransformationWithSingleTypeVar() {
    testTTL(STRING_TYPE, "S");
  }

  public void testTransformationWithMultipleTypeVars() {
    testTTL(STRING_TYPE, "S");
    testTTL(NUMBER_TYPE, "N");
  }

  public void testTransformationWithValidUnionTypeOnlyVars() {
    testTTL(union(NUMBER_TYPE, STRING_TYPE), "union(N, S)");
  }


  public void testTransformationWithValidUnionTypeOnlyTypePredicates() {
    testTTL(union(NUMBER_TYPE, STRING_TYPE),
        "union('number', 'string')");
  }

  public void testTransformationWithValidUnionTypeMixed() {
    testTTL(union(NUMBER_TYPE, STRING_TYPE), "union(S, 'number')");
  }

  public void testTransformationWithUnknownParameter() {
    // Returns ? because foo is not defined
    testTTL(UNKNOWN_TYPE, "union(foo, 'number')",
        "Reference to an unknown type variable foo");
  }

  public void testTransformationWithUnknownParameter2() {
    // Returns ? because foo is not defined
    testTTL(UNKNOWN_TYPE, "union(N, 'foo')",
        "Reference to an unknown type name foo");
  }

  public void testTransformationWithNestedUnionInFirstParameter() {
    testTTL(union(NUMBER_TYPE, NULL_TYPE, STRING_TYPE),
        "union(union(N, 'null'), S)");
  }

  public void testTransformationWithNestedUnionInSecondParameter() {
    testTTL(union(NUMBER_TYPE, NULL_TYPE, STRING_TYPE),
        "union(N, union('null', S))");
  }

  public void testTransformationWithRepeatedTypePredicate() {
    testTTL(NUMBER_TYPE, "union('number', 'number')");
  }

  public void testTransformationWithUndefinedTypeVar() {
    testTTL(UNKNOWN_TYPE, "foo", "Reference to an unknown type variable foo");
  }

  public void testTransformationWithTrueEqtypeConditional() {
    testTTL(STRING_TYPE, "cond(eq(N, N), 'string', 'number')");
  }

  public void testTransformationWithFalseEqtypeConditional() {
    testTTL(NUMBER_TYPE, "cond(eq(N, S), 'string', 'number')");
  }

  public void testTransformationWithTrueSubtypeConditional() {
    testTTL(STRING_TYPE,
        "cond( sub('Number', 'Object'), 'string', 'number')");
  }

  public void testTransformationWithFalseSubtypeConditional() {
    testTTL(NUMBER_TYPE,
        "cond( sub('Number', 'String'), 'string', 'number')");
  }

  public void testTransformationWithTrueStreqConditional() {
    testTTL(STRING_TYPE, "cond(streq(n, 'number'), 'string', 'number')");
  }

  public void testTransformationWithTrueStreqConditional2() {
    testTTL(STRING_TYPE, "cond(streq(n, n), 'string', 'number')");
  }

  public void testTransformationWithTrueStreqConditional3() {
    testTTL(STRING_TYPE, "cond(streq('number', 'number'), 'string', 'number')");
  }

  public void testTransformationWithFalseStreqConditional() {
    testTTL(NUMBER_TYPE, "cond(streq('number', 'foo'), 'string', 'number')");
  }

  public void testTransformationWithFalseStreqConditional2() {
    testTTL(NUMBER_TYPE, "cond(streq(n, 'foo'), 'string', 'number')");
  }

  public void testTransformationWithFalseStreqConditional3() {
    testTTL(NUMBER_TYPE, "cond(streq(n, s), 'string', 'number')");
  }

  public void testTransformationWithInvalidEqConditional() {
    // It returns number since a failing boolean expression returns false
    testTTL(NUMBER_TYPE, "cond(eq(foo, S), 'string', 'number')",
        "Reference to an unknown type variable foo");
  }

  public void testTransformationWithInvalidStreqConditional() {
    // It returns number since a failing boolean expression returns false
    testTTL(NUMBER_TYPE, "cond(streq(foo, s), 'string', 'number')",
        "Reference to an unknown string variable foo");
  }

  public void testTransformationWithNestedExpressionInBooleanFirstParam() {
    testTTL(STRING_TYPE,
        "cond( eq( cond(eq(N, N), 'string', 'number'), 'string'),"
            + "'string', "
            + "'number')");
  }

  public void testTransformationWithNestedExpressionInBooleanSecondParam() {
    testTTL(STRING_TYPE,
        "cond( eq( 'string', cond(eq(N, N), 'string', 'number')),"
            + "'string', "
            + "'number')");
  }

  public void testTransformationWithNestedExpressionInIfBranch() {
    testTTL(STRING_OBJECT_TYPE,
        "cond( eq(N, N),"
            + "cond(eq(N, S), 'string', 'String'),"
            + "'number')");
  }

  public void testTransformationWithNestedExpressionInElseBranch() {
    testTTL(STRING_OBJECT_TYPE,
        "cond( eq(N, S),"
        +     "'number',"
        +     "cond(eq(N, S), 'string', 'String'))");
  }

  public void testTransformationWithMapunionMappingEverythingToString() {
    testTTL(STRING_TYPE, "mapunion(union(S, N), (x) => S)");
  }

  public void testTransformationWithMapunionIdentity() {
    testTTL(union(NUMBER_TYPE, STRING_TYPE),
        "mapunion(union(N, S), (x) => x)");
  }

  public void testTransformationWithMapunionWithUnionEvaluatedToANonUnion() {
    testTTL(NUMBER_TYPE,
        "mapunion(union(N, 'number'), (x) => x)");
  }

  public void testTransformationWithMapunionFilterWithOnlyString() {
    testTTL(STRING_TYPE,
        "mapunion(union(S, B, N), (x) => cond(eq(x, S), x, BOT))");
  }

  public void testTransformationWithMapunionOnSingletonStringToNumber() {
    testTTL(NUMBER_TYPE, "mapunion(S, (x) => cond(eq(x, S), N, BOT))");
  }

  public void testTransformationWithNestedUnionInMapunionFilterString() {
    testTTL(union(NUMBER_TYPE, BOOLEAN_TYPE),
        "mapunion(union(union(S, B), union(N, S)),"
        + "(x) => cond(eq(x, S), BOT, x))");
  }

  public void testTransformationWithNestedMapunionInMapFunctionBody() {
    testTTL(STRING_TYPE,
        "mapunion(union(S, B),"
        + "(x) => mapunion(union(S, N), "
        +          "(y) => cond(eq(x, y), x, BOT)))");
  }

  public void testTransformationWithObjectUseCase() {
    testTTL(OBJECT_TYPE,
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

  public void testTransformatioWithNoneType() {
    testTTL(NO_TYPE, "none()");
  }

  public void testTransformatioWithNoneTypeInConditional() {
    testTTL(NO_TYPE, "cond(eq(BOT, none()), none(), N)");
  }

  public void testTransformatioWithNoneTypeInMapunionFilterString() {
    testTTL(STRING_TYPE,
        "mapunion(union(S, B, N), (x) => cond(eq(x, S), x, none()))");
  }

  public void testTransformatioWithAllType() {
    testTTL(ALL_TYPE, "all()");
  }

  public void testTransformatioWithAllTypeInConditional() {
    testTTL(ALL_TYPE, "cond(eq(TOP, all()), all(), N)");
  }

  public void testTransformatioWithAllTypeMixUnion() {
    testTTL(ALL_TYPE,
        "mapunion(union(S, B, N), (x) => cond(eq(x, S), x, all()))");
  }

  public void testTransformatioWithUnknownType() {
    testTTL(UNKNOWN_TYPE, "unknown()");
  }

  public void testTransformatioWithUnknownTypeInConditional() {
    testTTL(NUMBER_TYPE, "cond(eq(UNK, unknown()), N, S)");
  }

  public void testTransformatioWithUnknownTypeInMapunionStringToUnknown() {
    testTTL(UNKNOWN_TYPE,
        "mapunion(union(S, B, N), (x) => cond(eq(x, S), x, unknown()))");
  }

  public void testTransformationWithTemplatizedType() {
    testTTL(type(ARRAY_TYPE, NUMBER_TYPE), "type('Array', 'number')");
  }

  public void testTransformationWithTemplatizedType2() {
    testTTL(type(ARRAY_TYPE, NUMBER_TYPE), "type(ARR, 'number')");
  }

  public void testTransformationWithTemplatizedType3() {
    testTTL(type(ARRAY_TYPE, NUMBER_TYPE), "type(ARR, N)");
  }

  public void testTransformationWithTemplatizedTypeInvalidBaseType() {
    testTTL(UNKNOWN_TYPE, "type('string', 'number')",
        "The type string cannot be templatized");
  }

  public void testTransformationWithTemplatizedTypeInvalidBaseType2() {
    testTTL(UNKNOWN_TYPE, "type(S, 'number')",
        "The type string cannot be templatized");
  }

  public void testTransformationWithRawTypeOf() {
    testTTL(ARRAY_TYPE, "rawTypeOf(type('Array', 'number'))");
  }

  public void testTransformationWithRawTypeOf2() {
    testTTL(ARRAY_TYPE, "rawTypeOf(ARRNUM)");
  }

  public void testTransformationWithNestedRawTypeOf() {
    testTTL(ARRAY_TYPE, "rawTypeOf(type('Array', rawTypeOf(ARRNUM)))");
  }

  public void testTransformationWithInvalidRawTypeOf() {
    testTTL(UNKNOWN_TYPE, "rawTypeOf(N)",
        "Expected templatized type in rawTypeOf found number");
  }

  public void testTransformationWithTemplateTypeOf() {
    testTTL(NUMBER_TYPE, "templateTypeOf(type('Array', 'number'), 0)");
  }

  public void testTransformationWithTemplateTypeOf2() {
    testTTL(NUMBER_TYPE, "templateTypeOf(ARRNUM, 0)");
  }

  public void testTransformationWithNestedTemplateTypeOf() {
    testTTL(NUMBER_TYPE,
        "templateTypeOf("
        + "templateTypeOf(type('Array', type('Array', 'number')), 0),"
        + "0)");
  }

  public void testTransformationWithInvalidTypeTemplateTypeOf() {
    testTTL(UNKNOWN_TYPE, "templateTypeOf(N, 0)",
        "Expected templatized type in templateTypeOf found number");
  }

  public void testTransformationWithInvalidIndexTemplateTypeOf() {
    testTTL(UNKNOWN_TYPE, "templateTypeOf(ARRNUM, 2)",
        "Index out of bounds in templateTypeOf: 2 > 1");
  }

  public void testTransformationWithRecordType() {
    testTTL(record("x", NUMBER_TYPE),
        "record({x:'number'})");
  }

  public void testTransformationWithRecordType2() {
    testTTL(record("0", NUMBER_TYPE),
        "record({0:'number'})");
  }

  public void testTransformationWithRecordTypeMultipleProperties() {
    testTTL(record("x", NUMBER_TYPE, "y", STRING_TYPE),
        "record({x:'number', y:S})");
  }

  public void testTransformationWithNestedRecordType() {
    testTTL(record("x", record("z", BOOLEAN_TYPE), "y", STRING_TYPE),
        "record({x:record({z:B}), y:S})");
  }

  public void testTransformationWithNestedRecordType2() {
    testTTL(record("x", NUMBER_TYPE),
        "record(record({x:N}))");
  }

  public void testTransformationWithEmptyRecordType() {
    testTTL(record(), "record({})");
  }

  public void testTransformationWithMergeRecord() {
    testTTL(record("x", NUMBER_TYPE, "y", STRING_TYPE, "z", BOOLEAN_TYPE),
        "record({x:N}, {y:S}, {z:B})");
  }

  public void testTransformationWithMergeDuplicatedRecords() {
    testTTL(record("x", NUMBER_TYPE),
        "record({x:N}, {x:N}, {x:N})");
  }

  public void testTransformationWithMergeRecordTypeWithEmpty() {
    testTTL(record("x", NUMBER_TYPE),
        "record({x:N}, {})");
  }

  public void testTransformationWithInvalidRecordType() {
    testTTL(UNKNOWN_TYPE, "record(N)",
        "Expected a record type, found number");
  }

  public void testTransformationWithInvalidMergeRecordType() {
    testTTL(UNKNOWN_TYPE, "record({x:N}, N)",
        "Expected a record type, found number");
  }

  public void testTransformationWithTTLTypeTransformationInFirstParamMapunion() {
    testTTL(union(NUMBER_TYPE, STRING_TYPE),
        "mapunion(templateTypeOf(type(ARR, union(N, S)), 0),"
        + "(x) => x)");
  }

  public void testTransformationWithInvalidNestedMapunion() {
    testTTL(UNKNOWN_TYPE,
        "mapunion(union(S, B),"
        + "(x) => mapunion(union(S, N), "
        +          "(x) => cond(eq(x, x), x, BOT)))",
        "The variable x is already defined");
  }

  public void testTransformationWithTTLRecordWithReference() {
    testTTL(record("number", NUMBER_TYPE, "string", STRING_TYPE,
        "boolean", BOOLEAN_TYPE),
        "record({[n]:N, [s]:S, [b]:B})");
  }

  public void testTransformationWithTTLRecordWithInvalidReference() {
    testTTL(UNKNOWN_TYPE, "record({[Foo]:N})",
        "Expected a record type, found ?",
        "Reference to an unknown name variable Foo");
  }

  public void testTransformationWithMaprecordMappingEverythingToString() {
    // {n:number, s:string, b:boolean}
    // is transformed to
    // {n:string, s:string, b:string}
    testTTL(record("n", STRING_TYPE, "s", STRING_TYPE, "b", STRING_TYPE),
        "maprecord(REC, (k, v) => record({[k]:S}))");
  }

  public void testTransformationWithMaprecordIdentity() {
    // {n:number, s:string, b:boolean} remains the same
    testTTL(recordTypeTest, "maprecord(REC, (k, v) => record({[k]:v}))");
  }

  public void testTransformationWithMaprecordDeleteEverything() {
    // {n:number, s:string, b:boolean}
    // is transformed to
    // OBJECT_TYPE
    testTTL(OBJECT_TYPE, "maprecord(REC, (k, v) => BOT)");
  }

  public void testTransformationWithInvalidMaprecord() {
    testTTL(UNKNOWN_TYPE, "maprecord(REC, (k, v) => 'number')",
        "The body of a maprecord function must evaluate to a record type "
            + "or a no type, found number");
  }

  public void testTransformationWithMaprecordFilterWithOnlyString() {
    // {n:number, s:string, b:boolean}
    // is transformed to
    // {s:string}
    testTTL(record("s", STRING_TYPE),
        "maprecord(REC, (k, v) => cond(eq(v, S), record({[k]:v}), BOT))");
  }

  public void testTransformationWithInvalidMaprecordFirstParam() {
    testTTL(UNKNOWN_TYPE, "maprecord(N, (k, v) => BOT)",
        "The first parameter of a maprecord must be a record type, found number");
  }

  public void testTransformationWithObjectInMaprecord() {
    testTTL(OBJECT_TYPE, "maprecord(OBJ, (k, v) => record({[k]:v}))");
  }

  public void testTransformationWithUnionInMaprecord() {
    testTTL(UNKNOWN_TYPE,
        "maprecord(union(record({n:N}), S), (k, v) => record({[k]:v}))",
        "The first parameter of a maprecord must be a record type, "
            + "found (string|{n: number})");
  }

  public void testTransformationWithUnionOfRecordsInMaprecord() {
    testTTL(UNKNOWN_TYPE, "maprecord(union(record({n:N}), record({s:S})), "
        + "(k, v) => record({[k]:v}))",
        "The first parameter of a maprecord must be a record type, "
            + "found ({n: number}|{s: string})");
  }

  public void testTransformationWithNestedRecordInMaprecordFilterOneLevelString() {
    // {s:string, r:{s:string, b:boolean}}
    // is transformed to
    // {r:{s:string, b:boolean}}
    testTTL(record("r", record("s", STRING_TYPE, "b", BOOLEAN_TYPE)),
        "maprecord(NESTEDREC,"
            + "(k, v) => cond(eq(v, S), BOT, record({[k]:v})))");
  }

  public void testTransformationWithNestedRecordInMaprecordFilterTwoLevelsString() {
    // {s:string, r:{s:string, b:boolean}}
    // is transformed to
    // {r:{b:boolean}}
    testTTL(record("r", record("b", BOOLEAN_TYPE)),
        "maprecord(NESTEDREC,"
            + "(k1, v1) => "
            +  "cond(sub(v1, 'Object'), "
            +        "maprecord(v1, (k2, v2) => "
            +             "cond(eq(v2, S), BOT, record({[k1]:record({[k2]:v2})}))),"
            +        "cond(eq(v1, S), BOT, record({[k1]:v1}))))");
  }

  public void testTransformationWithNestedIdentityOneLevel() {
    // {r:{n:number, s:string}}
    testTTL(record("r",
        record("b", BOOLEAN_TYPE, "s", STRING_TYPE)),
        "maprecord(record({r:record({b:B, s:S})}),"
            + "(k1, v1) => "
            +   "maprecord(v1, "
            +     "(k2, v2) => "
            +       "record({[k1]:record({[k2]:v2})})))");
  }

  public void testTransformationWithNestedIdentityOneLevel2() {
    // {r:{n:number, s:string}}
    testTTL(record("r",
        record("b", BOOLEAN_TYPE, "s", STRING_TYPE)),
        "maprecord(record({r:record({b:B, s:S})}),\n"
            + "(k1, v1) =>\n"
            +   "record({[k1]:\n"
            +     "maprecord(v1, "
            +       "(k2, v2) => record({[k2]:v2}))}))");
  }

  public void testTransformationWithNestedIdentityTwoLevels() {
    // {r:{r2:{n:number, s:string}}}
    testTTL(record("r", record("r2",
        record("n", NUMBER_TYPE, "s", STRING_TYPE))),
        "maprecord(record({r:record({r2:record({n:N, s:S})})}),"
            + "(k1, v1) => "
            +    "maprecord(v1, "
            +      "(k2, v2) => "
            +        "maprecord(v2, "
            +          "(k3, v3) =>"
            +             "record({[k1]:"
            +               "record({[k2]:"
            +                 "record({[k3]:v3})})}))))");
  }

  public void testTransformationWithNestedIdentityTwoLevels2() {
    // {r:{r2:{n:number, s:string}}}
    testTTL(record("r", record("r2",
        record("n", NUMBER_TYPE, "s", STRING_TYPE))),
        "maprecord(record({r:record({r2:record({n:N, s:S})})}),"
            + "(k1, v1) => "
            +    "record({[k1]:"
            +      "maprecord(v1, "
            +        "(k2, v2) => "
            +          "record({[k2]:"
            +            "maprecord(v2, "
            +              "(k3, v3) =>"
            +                "record({[k3]:v3}))}))}))");
  }

  public void testTransformationWithNestedIdentityThreeLevels() {
    // {r:{r2:{r3:{n:number, s:string}}}}
    testTTL(record("r", record("r2", record("r3",
        record("n", NUMBER_TYPE, "s", STRING_TYPE)))),
        "maprecord(record({r:record({r2:record({r3:record({n:N, s:S})})})}),"
            + "(k1, v1) => "
            +    "maprecord(v1, "
            +      "(k2, v2) => "
            +        "maprecord(v2, "
            +          "(k3, v3) =>"
            +            "maprecord(v3,"
            +              "(k4, v4) =>"
            +               "record({[k1]:"
            +                 "record({[k2]:"
            +                   "record({[k3]:"
            +                     "record({[k4]:v4})})})})))))");
  }

  public void testTransformationWithNestedIdentityThreeLevels2() {
    // {r:{r2:{r3:{n:number, s:string}}}}
    testTTL(record("r", record("r2", record("r3",
        record("n", NUMBER_TYPE, "s", STRING_TYPE)))),
        "maprecord(record({r:record({r2:record({r3:record({n:N, s:S})})})}),"
            + "(k1, v1) => "
            +   "record({[k1]:"
            +     "maprecord(v1, "
            +       "(k2, v2) => "
            +         "record({[k2]:"
            +           "maprecord(v2, "
            +             "(k3, v3) =>"
            +               "record({[k3]:"
            +                 "maprecord(v3,"
            +                   "(k4, v4) =>"
            +                     "record({[k4]:v4}))}))}))}))");
  }

  public void testTransformationWithNestedRecordDeleteLevelTwoAndThree() {
    // {r:{r2:{r3:{n:number, s:string}}}}
    // is transformed into
    // {r:{n:number, s:string}}
    testTTL(record("r", record("n", NUMBER_TYPE, "s", STRING_TYPE)),
        "maprecord(record({r:record({r2:record({r3:record({n:N, s:S})})})}),"
            + "(k1, v1) => "
            +    "maprecord(v1, "
            +      "(k2, v2) => "
            +        "maprecord(v2, "
            +          "(k3, v3) =>"
            +            "maprecord(v3,"
            +              "(k4, v4) =>"
            +               "record({[k1]:"
            +                 "record({[k4]:v4})})))))");
  }

  public void testTransformationWithNestedRecordDeleteLevelTwoAndThree2() {
    // {r:{r2:{r3:{n:number, s:string}}}}
    // is transformed into
    // {r:{n:number, s:string}}
    testTTL(record("r", record("n", NUMBER_TYPE, "s", STRING_TYPE)),
        "maprecord(record({r:record({r2:record({r3:record({n:N, s:S})})})}),"
            + "(k1, v1) => "
            +   "record({[k1]:"
            +     "maprecord(v1, "
            +       "(k2, v2) => "
            +         "maprecord(v2, "
            +           "(k3, v3) =>"
            +             "maprecord(v3,"
            +               "(k4, v4) =>"
            +                 "record({[k4]:v4}))))}))");
  }

  public void testTransformationWithNestedRecordCollapsePropertiesToRecord() {
    // {a:Array, b:{n:number}}
    // is transformed to
    // {foo:{n:number}}
    testTTL(record("foo", record("n", NUMBER_TYPE)),
        "maprecord(record({a:ARR, b:record({n:N})}),"
            + "(k, v) => record({foo:v}))");
  }

  public void testTransformationWithNestedRecordCollapsePropertiesToType() {
    // {a:{n:number}, b:Array}
    // is transformed to
    // {foo:Array}
    testTTL(record("foo", ARRAY_TYPE),
        "maprecord(record({a:record({n:N}), b:ARR}),"
            + "(k, v) =>  record({foo:v}))");
  }

  public void testTransformationWithNestedRecordCollapsePropertiesJoinRecords() {
    // {a:{n:number}, b:{s:Array}}
    // is transformed to
    // {foo:{n:number, s:Array}}
    testTTL(record("foo", record("n", NUMBER_TYPE, "s", ARRAY_TYPE)),
        "maprecord(record({a:record({n:N}), b:record({s:ARR})}),"
            + "(k, v) => record({foo:v}))");
  }

  public void testTransformationWithNestedRecordCollapsePropertiesJoinRecords2() {
    // {a:{n:number, {x:number}}, b:{s:Array, {y:number}}}
    // is transformed to
    // {foo:{n:number, s:Array, r:{x:number, y:number}}}
    testTTL(record("foo", record("n", NUMBER_TYPE, "s", ARRAY_TYPE,
        "r", record("x", NUMBER_TYPE, "y", NUMBER_TYPE))),
        "maprecord(record({a:record({n:N, r:record({x:N})}), "
        + "b:record({s:ARR, r:record({y:N})})}),"
            + "(k, v) => record({foo:v}))");
  }

  public void testTransformationWithAsynchUseCase() {
    // TODO(lpino): Use the type Promise instead of Array
    // {service:Array<number>}
    // is transformed to
    // {service:number}
    testTTL(record("service", NUMBER_TYPE),
        "cond(sub(ASYNCH, 'Object'),\n"
            +       "maprecord(ASYNCH, \n"
            +       "(k, v) => cond(eq(rawTypeOf(v), 'Array'),\n"
            +                   "record({[k]:templateTypeOf(v, 0)}),\n"
            +                   "record({[k]:'undefined'})) "
            +               "),\n"
            +       "ASYNCH)");
  }

  public void testTransformationWithInvalidNestedMaprecord() {
    testTTL(UNKNOWN_TYPE,
        "maprecord(NESTEDREC, (k, v) => maprecord(v, (k, v) => BOT))",
        "The variable k is already defined");

  }

  public void testTransformationWithMaprecordAndStringEquivalence() {
    testTTL(record("bool", NUMBER_TYPE, "str", STRING_TYPE),
        "maprecord(record({bool:B, str:S}),"
        + "(k, v) => record({[k]:cond(streq(k, 'bool'), N, v)}))");
  }

  public void testTransformationWithTypeOfVar() {
    testTTL(NUMBER_TYPE, "typeOfVar('n')");
  }

  public void testTransformationWithUnknownTypeOfVar() {
    testTTL(UNKNOWN_TYPE, "typeOfVar('foo')",
        "Variable foo is undefined in the scope");
  }

  public void testTransformationWithTrueIsConstructorConditional() {
    testTTL(STRING_TYPE, "cond(isCtor(typeOfVar('Bar')), 'string', 'number')");
  }

  public void testTransformationWithFalseIsConstructorConditional() {
    testTTL(NUMBER_TYPE, "cond(isCtor(N), 'string', 'number')");
  }

  public void testTransformationWithTrueIsTemplatizedConditional() {
    testTTL(STRING_TYPE, "cond(isTemplatized(type(ARR, N)), 'string', 'number')");
  }

  public void testTransformationWithFalseIsTemplatizedConditional() {
    testTTL(NUMBER_TYPE, "cond(isTemplatized(ARR), 'string', 'number')");
  }

  public void testTransformationWithTrueIsRecordConditional() {
    testTTL(STRING_TYPE, "cond(isRecord(REC), 'string', 'number')");
  }

  public void testTransformationWithFalseIsRecordConditional() {
    testTTL(NUMBER_TYPE, "cond(isRecord(N), 'string', 'number')");
  }

  public void testTransformationWithTrueIsDefinedConditional() {
    testTTL(STRING_TYPE, "cond(isDefined(N), 'string', 'number')");
  }

  public void testTransformationWithFalseIsDefinedConditional() {
    testTTL(NUMBER_TYPE, "cond(isDefined(Foo), 'string', 'number')");
  }

  public void testTransformationWithTrueIsUnknownConditional() {
    testTTL(STRING_TYPE, "cond(isUnknown(UNK), 'string', 'number')");
  }

  public void testTransformationWithTrueIsUnknownConditional2() {
    testTTL(STRING_TYPE, "cond(isUnknown(CHKUNK), 'string', 'number')");
  }

  public void testTransformationWithFalseIsUnknownConditional() {
    testTTL(NUMBER_TYPE, "cond(isUnknown(N), 'string', 'number')");
  }

  public void testTransformationWithTrueAndConditional() {
    testTTL(STRING_TYPE,
        "cond(isDefined(N) && isDefined(N), 'string', 'number')");
  }

  public void testTransformationWithFalseAndConditional() {
    testTTL(NUMBER_TYPE,
        "cond(isDefined(N) && isDefined(Foo), 'string', 'number')");
  }

  public void testTransformationWithFalseAndConditional2() {
    testTTL(NUMBER_TYPE,
        "cond(isDefined(Foo) && isDefined(N), 'string', 'number')");
  }

  public void testTransformationWithFalseAndConditional3() {
    testTTL(NUMBER_TYPE,
        "cond(isDefined(Foo) && isDefined(Foo), 'string', 'number')");
  }

  public void testTransformationWithTrueOrConditional() {
    testTTL(STRING_TYPE,
        "cond(isDefined(N) || isDefined(N), 'string', 'number')");
  }

  public void testTransformationWithTrueOrConditional2() {
    testTTL(STRING_TYPE,
        "cond(isDefined(Foo) || isDefined(N), 'string', 'number')");
  }

  public void testTransformationWithTrueOrConditional3() {
    testTTL(STRING_TYPE,
        "cond(isDefined(N) || isDefined(Foo), 'string', 'number')");
  }

  public void testTransformationWithFalseOrConditional() {
    testTTL(NUMBER_TYPE,
        "cond(isDefined(Foo) || isDefined(Foo), 'string', 'number')");
  }

  public void testTransformationWithTrueNotConditional3() {
    testTTL(STRING_TYPE,
        "cond(!isDefined(Foo), 'string', 'number')");
  }

  public void testTransformationWithFalseNotConditional() {
    testTTL(NUMBER_TYPE,
        "cond(!isDefined(N), 'string', 'number')");
  }

  public void testTransformationWithInstanceOf() {
    testTTL(NUMBER_OBJECT_TYPE, "instanceOf(typeOfVar('Number'))");
  }

  public void testTransformationWithInvalidInstanceOf() {
    testTTL(UNKNOWN_TYPE, "instanceOf(N)",
        "Expected a constructor type, found number");
  }

  public void testTransformationWithInvalidInstanceOf2() {
    testTTL(UNKNOWN_TYPE, "instanceOf(foo)",
        "Expected a constructor type, found Unknown",
        "Reference to an unknown type variable foo");
  }

  public void testTransformationWithTypeExpr() {
    testTTL(NUMBER_TYPE, "typeExpr('number')");
  }

  public void testParserWithTTLNativeTypeExprUnion() {
    testTTL(union(NUMBER_TYPE, BOOLEAN_TYPE), "typeExpr('number|boolean')");
  }

  public void testParserWithTTLNativeTypeExprRecord() {
    testTTL(record("foo", NUMBER_TYPE, "bar", BOOLEAN_TYPE),
        "typeExpr('{foo:number, bar:boolean}')");
  }

  public void testParserWithTTLNativeTypeExprNullable() {
    testTTL(union(NUMBER_TYPE, NULL_TYPE), "typeExpr('?number')");
  }

  public void testParserWithTTLNativeTypeExprNonNullable() {
    testTTL(NUMBER_TYPE, "typeExpr('!number')");
  }

  public void testTransformationPrintType() {
    testTTL(NUMBER_TYPE, "printType('Test message: ', N)");
  }

  public void testTransformationPrintType2() {
    testTTL(recordTypeTest, "printType('Test message: ', REC)");
  }

  public void testTransformationPropType() {
    testTTL(NUMBER_TYPE, "propType('a', record({a:N, b:record({x:B})}))");
  }

  public void testTransformationPropType2() {
    testTTL(record("x", BOOLEAN_TYPE),
        "propType('b', record({a:N, b:record({x:B})}))");
  }

  public void testTransformationPropTypeNotFound() {
    testTTL(UNKNOWN_TYPE, "propType('c', record({a:N, b:record({x:B})}))");
  }

  public void testTransformationPropTypeInvalid() {
    testTTL(UNKNOWN_TYPE, "propType('c', N)",
        "Expected object type, found number");
  }

  public void testTransformationInstanceObjectToRecord() {
    testTTL(OBJECT_TYPE, "record(type(OBJ, N))");
  }

  public void testTransformationInstanceObjectToRecord2() {
    testTTL(record("length", NUMBER_TYPE), "record(type(ARR, N))");
  }

  public void testTransformationInstanceObjectToRecordInvalid() {
    testTTL(UNKNOWN_TYPE, "record(union(OBJ, NULL))",
        "Expected a record type, found (Object|null)");
  }

  private void initRecordTypeTests() {
    // {n:number, s:string, b:boolean}
    recordTypeTest = record("n", NUMBER_TYPE, "s", STRING_TYPE,
        "b", BOOLEAN_TYPE);
    // {n:number, r:{s:string, b:boolean}}
    nestedRecordTypeTest = record("s", STRING_TYPE,
        "r", record("s", STRING_TYPE, "b", BOOLEAN_TYPE));
    // {service:Array<number>}
    asynchRecord = record("service", type(ARRAY_TYPE, NUMBER_TYPE));

  }

  private JSType union(JSType... variants) {
    JSType type = createUnionType(variants);
    assertTrue(type.isUnionType());
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
