/*
 * Copyright 2017 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.testing;

import static com.google.common.truth.Truth.assertAbout;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.javascript.rhino.ObjectTypeI;
import com.google.javascript.rhino.TypeI;
import javax.annotation.CheckReturnValue;

/**
 * A Truth Subject for the TypeI interface. Usage:
 * <pre>
 *   import static com.google.javascript.jscomp.testing.TypeSubject.assertType;
 *   ...
 *   assertType(type1).isLiteralObject();
 *   assertType(type2).isObjectTypeWithProperty("propName").withTypeOfProp("propName").isNumber();
 * </pre>
 */
public final class TypeSubject extends Subject<TypeSubject, TypeI> {
  @CheckReturnValue
  public static TypeSubject assertType(TypeI type) {
    return assertAbout(types()).that(type);
  }

  public static Subject.Factory<TypeSubject, TypeI> types() {
    return TypeSubject::new;
  }

  public TypeSubject(FailureMetadata failureMetadata, TypeI type) {
    super(failureMetadata, type);
  }

  public void isNumber() {
    String message = "Type is of type " + actualAsString() + " not a number.";
    assertTrue(message, actual().isNumberValueType());
  }

  public void isString() {
    String message = "Type is of type " + actualAsString() + " not a string.";
    assertTrue(message, actual().isStringValueType());
  }

  public void isBoolean() {
    String message = "Type is of type " + actualAsString() + " not a boolean.";
    assertTrue(message, actual().isBooleanValueType());
  }

  public void isUnknown() {
    String message = "Type is of type " + actualAsString() + " not unknown.";
    assertTrue(message, actual().isUnknownType());
  }

  public void isLiteralObject() {
    String message = "Type is of type " + actualAsString() + " not a literal object.";
    assertTrue(message, actual().isLiteralObject());
  }

  public TypeSubject isObjectTypeWithProperty(String propName) {
    isLiteralObject();
    ObjectTypeI objType = actual().toMaybeObjectType();
    TypeI actualPropType = objType.getPropertyType(propName);
    assertNotNull(
        "Type " + actualAsString() + " does not have property " + propName, actualPropType);
    return this;
  }

  /**
   * Returns a {@code TypeSubject} that is the type of the property with name propName,
   * to make assertions about the objectType's property Type message.
   * Assumes that {@code actual()} is an object type with property propName,
   * so it should be run after {@link #isObjectTypeWithProperty}.
   */
  public TypeSubject withTypeOfProp(String propName) {
    TypeI actualPropType = actual().toMaybeObjectType().getPropertyType(propName);
    return check().about(types()).that(actualPropType);
  }

  public void isObjectTypeWithoutProperty(String propName) {
    isLiteralObject();
    ObjectTypeI objType = actual().toMaybeObjectType();
    TypeI actualPropType = objType.getPropertyType(propName);
    assertNull(
        "Type " + actualAsString() + " should not have property " + propName, actualPropType);
  }

  public void toStringIsEqualTo(String typeString) {
    assertEquals(typeString, actual().toString());
  }
}
