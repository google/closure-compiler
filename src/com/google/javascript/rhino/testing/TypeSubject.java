/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Bob Jervis
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino.testing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.javascript.rhino.ClosurePrimitive;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import java.util.Objects;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * A Truth Subject for the JSType interface. Usage:
 *
 * <pre>
 *   import static com.google.javascript.rhino.testing.TypeSubject.assertType;
 *   ...
 *   assertType(type1).isLiteralObject();
 *   assertType(type2).isObjectTypeWithProperty("propName").withTypeOfProp("propName").isNumber();
 * </pre>
 */
public final class TypeSubject extends Subject {
  @CheckReturnValue
  public static TypeSubject assertType(JSType type) {
    return assertAbout(types()).that(type);
  }

  public static Subject.Factory<TypeSubject, JSType> types() {
    return TypeSubject::new;
  }

  private final JSType actual;

  private TypeSubject(FailureMetadata failureMetadata, JSType type) {
    super(failureMetadata, type);
    this.actual = type;
  }

  @Override
  public void isEqualTo(@Nullable Object provided) {
    if (provided != null) {
      assertThat(provided).isInstanceOf(JSType.class);
    }

    this.checkEqualityAgainst((JSType) provided, true);
  }

  public void isNotEqualTo(@Nullable JSType provided) {
    this.checkEqualityAgainst(provided, false);
  }

  public void isNumber() {
    check("isNumberValueType()").that(actualNonNull().isNumberValueType()).isTrue();
  }

  public void isOnlyBigInt() {
    check("isOnlyBigInt()").that(actualNonNull().isOnlyBigInt()).isTrue();
  }

  public void isNotOnlyBigInt() {
    check("isOnlyBigInt()").that(actualNonNull().isOnlyBigInt()).isFalse();
  }

  public void isString() {
    check("isStringValueType()").that(actualNonNull().isStringValueType()).isTrue();
  }

  public void isBoolean() {
    check("isBooleanValueType()").that(actualNonNull().isBooleanValueType()).isTrue();
  }

  public void isVoid() {
    check("isVoidType()").that(actualNonNull().isVoidType()).isTrue();
  }

  public void isNoType() {
    check("isNoType()").that(actualNonNull().isNoType()).isTrue();
  }

  public void isUnknown() {
    check("isUnknownType()").that(actualNonNull().isUnknownType()).isTrue();
  }

  public void isNotUnknown() {
    check("isUnknownType()").that(actualNonNull().isUnknownType()).isFalse();
  }

  public void isNotEmpty() {
    check("isEmptyType()").that(actualNonNull().isEmptyType()).isFalse();
  }

  public void isLiteralObject() {
    check("isLiteralObject()").that(actualNonNull().isLiteralObject()).isTrue();
  }

  public FunctionTypeSubject isFunctionTypeThat() {
    check("isFunctionType()").that(actualNonNull().isFunctionType()).isTrue();
    return new FunctionTypeSubject();
  }

  public TypeSubject isObjectTypeWithProperty(String propName) {
    isLiteralObject();
    withTypeOfProp(propName).isNotNull();
    return this;
  }

  /**
   * Returns a {@code TypeSubject} that is the type of the property with name propName,
   * to make assertions about the objectType's property Type message.
   * Assumes that {@code actual()} is an object type with property propName,
   * so it should be run after {@link #isObjectTypeWithProperty}.
   */
  public TypeSubject withTypeOfProp(String propName) {
    check("isObjectType()").that(actualNonNull().isObjectType()).isTrue();

    return check("toMaybeObjectType().getPropertyType(%s)", propName)
        .about(types())
        .that(actualNonNull().toMaybeObjectType().getPropertyType(propName));
  }

  public void hasDeclaredProperty(String propName) {
    check("isObjectType()").that(actualNonNull().isObjectType()).isTrue();

    check("toMaybeObjectType().isPropertyTypeDeclared(%s)", propName)
        .that(actualNonNull().toMaybeObjectType().isPropertyTypeDeclared(propName))
        .isTrue();
  }

  public void hasInferredProperty(String propName) {
    check("isObjectType()").that(actualNonNull().isObjectType()).isTrue();

    check("toMaybeObjectType().isPropertyTypeInferred(%s)", propName)
        .that(actualNonNull().toMaybeObjectType().isPropertyTypeInferred(propName))
        .isTrue();
  }

  public void isObjectTypeWithoutProperty(String propName) {
    isLiteralObject();
    withTypeOfProp(propName).isNull();
  }

  public void isSubtypeOf(JSType superType) {
    check("isSubtypeOf(%s)", superType).that(actualNonNull().isSubtypeOf(superType)).isTrue();
  }

  public void isNotSubtypeOf(JSType superType) {
    check("isSubtypeOf(%s)", superType).that(actualNonNull().isSubtypeOf(superType)).isFalse();
  }

  public void toStringIsEqualTo(String typeString) {
    check("toString()").that(actualNonNull().toString()).isEqualTo(typeString);
  }

  public void getReferenceNameIsEqualTo(String referenceName) {
    check("getReferenceName()")
        .that(actualNonNull().toMaybeObjectType().getReferenceName())
        .isEqualTo(referenceName);
    check("hasReferenceName()")
        .that(actualNonNull().toMaybeObjectType().hasReferenceName())
        .isTrue();
  }

  public void getReferenceNameIsNull() {
    check("getReferenceName()")
        .that(actualNonNull().toMaybeObjectType().getReferenceName())
        .isNull();
    check("hasReferenceName()")
        .that(actualNonNull().toMaybeObjectType().hasReferenceName())
        .isFalse();
  }

  private JSType actualNonNull() {
    isNotNull();
    return actual;
  }

  private void checkEqualityAgainst(@Nullable JSType provided, boolean expectation) {
    String providedString = debugStringOf(provided);
    String actualString = debugStringOf(this.actual);

    boolean actualEqualsProvided = Objects.equals(this.actual, provided);
    if (actualEqualsProvided != expectation) {
      failWithActual(
          fact("Types expected to be equal", expectation), //
          fact(equalsExpressionOf(actualString, providedString), actualEqualsProvided), //
          fact("provided", providedString));
    }

    boolean providedEqualsActual = Objects.equals(provided, this.actual);
    if (actualEqualsProvided != providedEqualsActual) {
      failWithActual(
          simpleFact("Equality should be symmetric"), //
          fact(equalsExpressionOf(actualString, providedString), actualEqualsProvided),
          fact(equalsExpressionOf(providedString, actualString), providedEqualsActual),
          fact("provided", providedString));
    }

    if (expectation) {
      if (Objects.hashCode(this.actual) != Objects.hashCode(provided)) {
        failWithActual(
            simpleFact("If two types are equal their hashcodes must also be equal"), //
            fact("hash of actual", Objects.hashCode(this.actual)),
            fact("hash of provided", Objects.hashCode(provided)),
            fact("provided", providedString));
      }
    }
  }

  @Override
  protected String actualCustomStringRepresentation() {
    return debugStringOf(actual);
  }

  private static String debugStringOf(JSType type) {
    return (type == null)
        ? "[Java null]"
        : type.toString() + " [instanceof " + type.getClass().getName() + "]";
  }

  private static String equalsExpressionOf(@Nullable Object receiver, @Nullable Object parameter) {
    return "(" + receiver + ").equals(" + parameter + ")";
  }

  /** Implements test functions specific to function types. */
  public class FunctionTypeSubject {
    private FunctionType actualFunctionType() {
      return checkNotNull(actualNonNull().toMaybeFunctionType(), actual);
    }

    public TypeSubject hasTypeOfThisThat() {
      return assertType(actualFunctionType().getTypeOfThis());
    }

    public TypeSubject hasReturnTypeThat() {
      return assertType(actualFunctionType().getReturnType());
    }

    public void isConstructorFor(String name) {
      check("isConstructor()").that(actual.isConstructor()).isTrue();
      check("getInstanceType().getDisplayName()")
          .that(actualFunctionType().getInstanceType().getDisplayName())
          .isEqualTo(name);
    }

    public void hasPrimitiveId(ClosurePrimitive id) {
      check("getClosurePrimitive()")
          .that(actualNonNull().toMaybeFunctionType().getClosurePrimitive())
          .isEqualTo(id);
    }
  }
}
