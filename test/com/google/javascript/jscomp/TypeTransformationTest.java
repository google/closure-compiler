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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;

public class TypeTransformationTest extends CompilerTypeTestCase {

  private ImmutableMap<String, JSType> typeVars;

  @Override
  public void setUp() {
    super.setUp();
    typeVars = ImmutableMap.of(
        "S", STRING_TYPE, "N", NUMBER_TYPE);
  }

  public void testTransformationWithValidBasicTypePredicate() {
    testTTL(NUMBER_TYPE, "type('number')");
  }

  public void testTransformationWithBasicTypePredicateWithInvalidTypename() {
    testTTL(UNKNOWN_TYPE, "type('foo')");
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
    testTTL(UNKNOWN_TYPE, "union(foo, type('number'))");
  }

  public void testTransformationWithUnknownParameter2() {
    // Returns ? because foo is not defined
    testTTL(UNKNOWN_TYPE, "union(N, type('foo'))");
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
    testTTL(UNKNOWN_TYPE, "foo");
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

  private JSType union(JSType... variants) {
    return createUnionType(variants);
  }

  private void testTTL(JSType expectedType, String ttlExp) {
    // TODO(lpino): Validate the AST using the TypeTransformationParser
    Node ast = compiler.parseTestCode(ttlExp);
    // Cut the SCRIPT and EXPR_RESULT nodes
    ast = ast.getFirstChild().getFirstChild();
    // Evaluate the type transformation
    TypeTransformation typeTransformation = new TypeTransformation(compiler);
    JSType resultType = typeTransformation.eval(ast, typeVars);
    assertTypeEquals(expectedType, resultType);
  }

}