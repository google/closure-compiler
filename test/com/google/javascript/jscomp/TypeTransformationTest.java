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

public class TypeTransformationTest extends CompilerTypeTestCase {

  private ImmutableMap<String, JSType> typeVars;

  @Override
  public void setUp() {
    super.setUp();
    errorReporter = new TestErrorReporter(null, null);
    typeVars = new ImmutableMap.Builder<String, JSType>()
        .put("S", STRING_TYPE)
        .put("N", NUMBER_TYPE)
        .put("B", BOOLEAN_TYPE)
        .put("BOT", NO_TYPE)
        .put("SO", STRING_OBJECT_TYPE)
        .put("NO", NUMBER_OBJECT_TYPE)
        .put("BO", BOOLEAN_OBJECT_TYPE)
        .put("NULL", NULL_TYPE)
        .put("OBJ", OBJECT_TYPE)
        .put("UNDEF", VOID_TYPE)
        .put("ARR", ARRAY_TYPE)
        .put("ARRNUM", type(ARRAY_TYPE, NUMBER_TYPE))
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

  public void testTransformationWithMulipleTypeVars() {
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
    testTTL(union(NUMBER_TYPE),
        "mapunion(S, (x) => cond(eq(x, S), N, BOT))");
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
    testTTL(
        union(STRING_OBJECT_TYPE, NUMBER_OBJECT_TYPE, BOOLEAN_OBJECT_TYPE,
        OBJECT_TYPE, ARRAY_TYPE),
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
    testTTL(record(ImmutableMap.<String, JSType>of("x", NUMBER_TYPE)),
        "record({x:'number'})");
  }

  public void testTransformationWithRecordType2() {
    testTTL(record(ImmutableMap.<String, JSType>of("0", NUMBER_TYPE)),
        "record({0:'number'})");
  }

  public void testTransformationWithRecordTypeMultipleProperties() {
    testTTL(record(
        ImmutableMap.<String, JSType>of("x", NUMBER_TYPE, "y", STRING_TYPE)),
        "record({x:'number', y:S})");
  }

  public void testTransformationWithNestedRecordType() {
    testTTL(record(ImmutableMap.<String, JSType>of(
        "x", record(ImmutableMap.<String, JSType>of("z", BOOLEAN_TYPE)),
        "y", STRING_TYPE)),
        "record({x:record({z:B}), y:S})");
  }

  public void testParserWithTTLTypeTransformationInFirstParamMapunion() {
    testTTL(union(NUMBER_TYPE, STRING_TYPE),
        "mapunion(templateTypeOf(type(ARR, union(N, S)), 0),"
        + "(x) => x)");
  }

  private JSType union(JSType... variants) {
    return createUnionType(variants);
  }

  private JSType type(ObjectType baseType, JSType... templatizedTypes) {
    return createTemplatizedType(baseType, templatizedTypes);
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
      // Evaluate the type transformation
      TypeTransformation typeTransformation = new TypeTransformation(compiler);
      JSType resultType = typeTransformation.eval(ast, typeVars);
      checkReportedWarningsHelper(expectedWarnings);
      assertTypeEquals(expectedType, resultType);
    }
  }

}
