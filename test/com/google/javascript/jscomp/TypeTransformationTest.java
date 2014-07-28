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
import com.google.javascript.jscomp.parsing.TypeTransformationParser.Keywords;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;

public class TypeTransformationTest extends CompilerTypeTestCase {

  public void testTransformationWithValidBasicTypePredicate() {
    assertTypeEquals(NUMBER_TYPE,
        transform(buildTypePredicate("number")));
  }

  public void testTransformationWithBasicTypePredicateWithInvalidTypename() {
    assertTypeEquals(UNKNOWN_TYPE,
        transform(buildTypePredicate("foo")));
  }

  public void testTransformationWithSingleTypeVar() {
    ImmutableMap<String, JSType> typeVars = ImmutableMap.of("T", STRING_TYPE);
    assertTypeEquals(STRING_TYPE, transform(buildTypeVariable("T"), typeVars));
  }

  public void testTransformationWithMulipleTypeVars() {
    ImmutableMap<String, JSType> typeVars = ImmutableMap.of(
        "T", STRING_TYPE, "R", STRING_OBJECT_TYPE);
    assertTypeEquals(STRING_TYPE,
        transform(buildTypeVariable("T"), typeVars));
    assertTypeEquals(STRING_OBJECT_TYPE,
        transform(buildTypeVariable("R"), typeVars));
  }

  public void testTransformationWithValidUnionTypeOnlyVars() {
    // Builds union(T, R) returns (number|string)
    Node ttlExp = buildUnionType(
        buildTypeVariable("T"), buildTypeVariable("R"));
    ImmutableMap<String, JSType> typeVars =
        ImmutableMap.of("T", NUMBER_TYPE, "R", STRING_TYPE);
    assertTypeEquals(createUnionType(NUMBER_TYPE, STRING_TYPE),
        transform(ttlExp, typeVars));
  }

  public void testTransformationWithValidUnionTypeOnlyTypePredicates() {
    // Builds union(type('number'), type('string')) returns (number|string)
    Node ttlExp = buildUnionType(
        buildTypePredicate("number"), buildTypePredicate("string"));
    assertTypeEquals(createUnionType(NUMBER_TYPE, STRING_TYPE),
        transform(ttlExp));
  }

  public void testTransformationWithValidUnionTypeMixed() {
    // Builds union(T, type('number')) returns (number|string)
    Node ttlExp = buildUnionType(
        buildTypeVariable("T"), buildTypePredicate("number"));
    ImmutableMap<String, JSType> typeVars = ImmutableMap.of("T", STRING_TYPE);
    assertTypeEquals(createUnionType(NUMBER_TYPE, STRING_TYPE),
        transform(ttlExp, typeVars));
  }

  public void testTransformationWithUnknownParameter() {
    // Builds union(T, type('number')) returns ? because T is not defined
    Node ttlExp = buildUnionType(
        buildTypeVariable("T"), buildTypePredicate("number"));
    assertTypeEquals(UNKNOWN_TYPE, transform(ttlExp));
  }

  public void testTransformationWithUnknownParameter2() {
    // Builds union(T, type('foo')) returns ? because foo is not defined
    Node ttlExp = buildUnionType(
        buildTypeVariable("T"), buildTypePredicate("foo"));
    ImmutableMap<String, JSType> typeVars = ImmutableMap.of("T", STRING_TYPE);
    assertTypeEquals(UNKNOWN_TYPE, transform(ttlExp, typeVars));
  }

  public void testTransformationWithNestedUnionInFirstParameter() {
    // Builds union(union(T, type('null')), R) returns (number|null|string)
    Node ttlExp = buildUnionType(
        buildUnionType(buildTypeVariable("T"), buildTypePredicate("null")),
        buildTypeVariable("R"));
    ImmutableMap<String, JSType> typeVars =
        ImmutableMap.of("T", NUMBER_TYPE, "R", STRING_TYPE);
    assertTypeEquals(createUnionType(NUMBER_TYPE, NULL_TYPE, STRING_TYPE),
        transform(ttlExp, typeVars));
  }

  public void testTransformationWithNestedUnionInSecondParameter() {
    // Builds union(union(T, (type('null'), R)) returns (number|null|string)
    Node ttlExp = buildUnionType(
        buildTypeVariable("T"),
        buildUnionType(buildTypePredicate("null"), buildTypeVariable("R")));
    ImmutableMap<String, JSType> typeVars =
        ImmutableMap.of("T", NUMBER_TYPE, "R", STRING_TYPE);
    assertTypeEquals(createUnionType(NUMBER_TYPE, NULL_TYPE, STRING_TYPE),
        transform(ttlExp, typeVars));
  }

  public void testTransformationWithRepeatedTypePredicate() {
    // Builds union(type('number'), type('number')) returns number
    Node ttlExp = buildUnionType(
        buildTypePredicate("number"), buildTypePredicate("number"));
    assertTypeEquals(NUMBER_TYPE, transform(ttlExp));
  }

  public void testTransformationWithUndefinedTypeVar() {
    assertTypeEquals(UNKNOWN_TYPE, transform(buildTypeVariable("T")));
  }

  public void testTransformationWithTrueEqtypeConditional() {
    // Builds cond(eq(T, T), type('string'), type('number')) returns string
    Node ttlExp = buildConditional(
        buildEqtypePredicate(buildTypeVariable("T"), buildTypeVariable("T")),
        buildTypePredicate("string"),
        buildTypePredicate("number"));
    ImmutableMap<String, JSType> typeVars =
        ImmutableMap.of("T", NUMBER_TYPE);
    assertTypeEquals(STRING_TYPE, transform(ttlExp, typeVars));
  }

  public void testTransformationWithFalseEqtypeConditional() {
    // Builds cond(eq(T, R), type('string'), type('number')) returns number
    Node ttlExp = buildConditional(
        buildEqtypePredicate(buildTypeVariable("T"), buildTypeVariable("R")),
        buildTypePredicate("string"),
        buildTypePredicate("number"));
    ImmutableMap<String, JSType> typeVars =
        ImmutableMap.of("T", NUMBER_TYPE, "R", STRING_TYPE);
    assertTypeEquals(NUMBER_TYPE, transform(ttlExp, typeVars));
  }

  public void testTransformationWithTrueSubtypeConditional() {
    // Builds cond( sub(type('Number'), type('Object')),
    //              type('string'),
    //              type('number'))
    // returns string
    Node ttlExp = buildConditional(
        buildSubtypePredicate(buildTypePredicate("Number"),
            buildTypePredicate("Object")),
        buildTypePredicate("string"),
        buildTypePredicate("number"));
    assertTypeEquals(STRING_TYPE, transform(ttlExp));
  }

  public void testTransformationWithFalseSubtypeConditional() {
    // Builds cond( sub(type('Number'), type('String')),
    //              type('string'),
    //              type('number'))
    // returns number
    Node ttlExp = buildConditional(
        buildSubtypePredicate(buildTypePredicate("Number"),
            buildTypePredicate("String")),
        buildTypePredicate("string"),
        buildTypePredicate("number"));
    assertTypeEquals(NUMBER_TYPE, transform(ttlExp));
  }

  public void testTransformationWithNestedExpressionInBooleanFirstParam() {
    // Builds cond( eq( cond(eq(T, T), type('string'), type('number')),
    //                  type('string')
    //                ),
    //              type('string'),
    //              type('number'))
    // returns string
    Node nested = buildConditional(
        buildEqtypePredicate(buildTypeVariable("T"), buildTypeVariable("T")),
        buildTypePredicate("string"),
        buildTypePredicate("number"));
    Node ttlExp = buildConditional(
        buildEqtypePredicate(nested, buildTypePredicate("string")),
        buildTypePredicate("string"),
        buildTypePredicate("number"));
    ImmutableMap<String, JSType> typeVars =
        ImmutableMap.of("T", NUMBER_TYPE);
    assertTypeEquals(STRING_TYPE, transform(ttlExp, typeVars));
  }

  public void testTransformationWithNestedExpressionInBooleanSecondParam() {
    // Builds cond( eq( type('string'),
    //                  cond(eq(T, T), type('string'), type('number'))
    //                ),
    //              type('string'),
    //              type('number'))
    // returns string
    Node nested = buildConditional(
        buildEqtypePredicate(buildTypeVariable("T"), buildTypeVariable("T")),
        buildTypePredicate("string"),
        buildTypePredicate("number"));
    Node ttlExp = buildConditional(
        buildEqtypePredicate(buildTypePredicate("string"), nested),
        buildTypePredicate("string"),
        buildTypePredicate("number"));
    ImmutableMap<String, JSType> typeVars =
        ImmutableMap.of("T", NUMBER_TYPE);
    assertTypeEquals(STRING_TYPE, transform(ttlExp, typeVars));
  }

  public void testTransformationWithNestedExpressionInIfBranch() {
    // Builds cond( eq(T, T),
    //              cond(eq(T, R), type('string'), type('String')),
    //              type('number'))
    // returns String
    Node nested = buildConditional(
        buildEqtypePredicate(buildTypeVariable("T"), buildTypeVariable("R")),
        buildTypePredicate("string"),
        buildTypePredicate("String"));
    Node ttlExp = buildConditional(
        buildEqtypePredicate(buildTypeVariable("T"), buildTypeVariable("T")),
        nested,
        buildTypePredicate("number"));
    ImmutableMap<String, JSType> typeVars =
        ImmutableMap.of("T", NUMBER_TYPE);
    assertTypeEquals(STRING_OBJECT_TYPE, transform(ttlExp, typeVars));
  }

  public void testTransformationWithNestedExpressionInElseBranch() {
    // Builds cond( eq(T, R),
    //              type('number'),
    //              cond(eq(T, R), type('string'), type('String')))
    // returns String
    Node nested = buildConditional(
        buildEqtypePredicate(buildTypeVariable("T"), buildTypeVariable("R")),
        buildTypePredicate("string"),
        buildTypePredicate("String"));
    Node ttlExp = buildConditional(
        buildEqtypePredicate(buildTypeVariable("T"), buildTypeVariable("R")),
        buildTypePredicate("number"),
        nested);
    ImmutableMap<String, JSType> typeVars =
        ImmutableMap.of("T", NUMBER_TYPE);
    assertTypeEquals(STRING_OBJECT_TYPE, transform(ttlExp, typeVars));
  }

  /**
   * Builds a Node representing a type variable
   * @param name The name of the variable
   */
  private Node buildTypeVariable(String name) {
    return IR.name(name);
  }

  /**
   * Builds a Node representing a type expression such as type('typename')
   * @param typename
   */
  private Node buildTypePredicate(String typename) {
    return IR.call(
        IR.name(Keywords.TYPE.name),
        IR.name(typename));
  }

  private Node buildUnionType(Node... params) {
    return IR.call(IR.name(Keywords.UNION.name),
        params);
   }

  private Node buildEqtypePredicate(Node param0, Node param1) {
    return buildBooleanPredicate(Keywords.EQTYPE, param0, param1);
  }

  private Node buildSubtypePredicate(Node param0, Node param1) {
    return buildBooleanPredicate(Keywords.SUBTYPE, param0, param1);
  }

  private Node buildBooleanPredicate(Keywords predicate, Node param0,
      Node param1) {
    return IR.call(IR.name(predicate.name), param0, param1);
  }

  private Node buildConditional(Node boolExp, Node ifBranch, Node elseBranch) {
    return IR.call(IR.name(Keywords.COND.name), boolExp, ifBranch, elseBranch);
  }

  private JSType transform(Node ttlExp) {
    return transform(ttlExp, ImmutableMap.<String, JSType>of());
  }

  private JSType transform(Node ttlExp, ImmutableMap<String, JSType> typeVars) {
    TypeTransformation typeTransformation = new TypeTransformation(compiler);
    return typeTransformation.eval(ttlExp, typeVars);
  }
}