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
import com.google.javascript.rhino.testing.TestErrorReporter;

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
        .build();
  }

  public void testTransformationWithValidBasicTypePredicate() {
    testTTL(NUMBER_TYPE, "type('number')");
  }

  public void testTransformationWithBasicTypePredicateWithInvalidTypename() {
    testTTL(UNKNOWN_TYPE, "type('foo')",
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
        "union(type('number'), type('string'))");
  }

  public void testTransformationWithValidUnionTypeMixed() {
    testTTL(union(NUMBER_TYPE, STRING_TYPE), "union(S, type('number'))");
  }

  public void testTransformationWithUnknownParameter() {
    // Returns ? because foo is not defined
    testTTL(UNKNOWN_TYPE, "union(foo, type('number'))",
        "Reference to an unknown type variable foo");
  }

  public void testTransformationWithUnknownParameter2() {
    // Returns ? because foo is not defined
    testTTL(UNKNOWN_TYPE, "union(N, type('foo'))",
        "Reference to an unknown type name foo");
  }

  public void testTransformationWithNestedUnionInFirstParameter() {
    testTTL(union(NUMBER_TYPE, NULL_TYPE, STRING_TYPE),
        "union(union(N, type('null')), S)");
  }

  public void testTransformationWithNestedUnionInSecondParameter() {
    testTTL(union(NUMBER_TYPE, NULL_TYPE, STRING_TYPE),
        "union(N, union(type('null'), S))");
  }

  public void testTransformationWithRepeatedTypePredicate() {
    testTTL(NUMBER_TYPE, "union(type('number'), type('number'))");
  }

  public void testTransformationWithUndefinedTypeVar() {
    testTTL(UNKNOWN_TYPE, "foo", "Reference to an unknown type variable foo");
  }

  public void testTransformationWithTrueEqtypeConditional() {
    testTTL(STRING_TYPE, "cond(eq(N, N), type('string'), type('number'))");
  }

  public void testTransformationWithFalseEqtypeConditional() {
    testTTL(NUMBER_TYPE, "cond(eq(N, S), type('string'), type('number'))");
  }

  public void testTransformationWithTrueSubtypeConditional() {
    testTTL(STRING_TYPE,
        "cond( sub(type('Number'), type('Object')),"
            + "type('string'),"
            + "type('number'))");
  }

  public void testTransformationWithFalseSubtypeConditional() {
    testTTL(NUMBER_TYPE,
        "cond( sub(type('Number'), type('String')),"
            + "type('string'),"
            + "type('number'))");
  }

  public void testTransformationWithNestedExpressionInBooleanFirstParam() {
    testTTL(STRING_TYPE,
        "cond( eq( cond(eq(N, N), type('string'), type('number')),"
            +     "type('string')"
            +    "),"
            + "type('string'),"
            + "type('number'))");
  }

  public void testTransformationWithNestedExpressionInBooleanSecondParam() {
    testTTL(STRING_TYPE,
        "cond( eq( type('string'),"
        +          "cond(eq(N, N), type('string'), type('number'))"
        +        "),"
        +     "type('string'),"
        +     "type('number'))");
  }

  public void testTransformationWithNestedExpressionInIfBranch() {
    testTTL(STRING_OBJECT_TYPE,
        "cond( eq(N, N),"
        +     "cond(eq(N, S), type('string'), type('String')),"
        +     "type('number'))");
  }

  public void testTransformationWithNestedExpressionInElseBranch() {
    testTTL(STRING_OBJECT_TYPE,
        "cond( eq(N, S),"
        +     "type('number'),"
        +     "cond(eq(N, S), type('string'), type('String')))");
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
        "mapunion(union(N, type('number')), (x) => x)");
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

  private JSType union(JSType... variants) {
    return createUnionType(variants);
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