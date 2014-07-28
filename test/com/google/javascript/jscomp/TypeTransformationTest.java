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
        IR.name(TypeTransformationParser.Keywords.TYPE.name),
        IR.name(typename));
  }

  private Node buildUnionType(Node... params) {
    return IR.call(IR.name(TypeTransformationParser.Keywords.UNION.name),
        params);
   }

  private JSType transform(Node ttlExp) {
    return transform(ttlExp, ImmutableMap.<String, JSType>of());
  }

  private JSType transform(Node ttlExp, ImmutableMap<String, JSType> typeVars) {
    TypeTransformation typeTransformation = new TypeTransformation(compiler);
    return typeTransformation.eval(ttlExp, typeVars);
  }
}