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
import com.google.errorprone.annotations.ForOverride;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
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
public final class TypeSubject extends Subject<TypeSubject, JSType> {
  @CheckReturnValue
  public static TypeSubject assertType(JSType type) {
    return assertAbout(types()).that(type);
  }

  public static Subject.Factory<TypeSubject, JSType> types() {
    return TypeSubject::new;
  }

  private TypeSubject(FailureMetadata failureMetadata, JSType type) {
    super(failureMetadata, type);
  }

  @Override
  public void isEqualTo(@Nullable Object provided) {
    if (provided != null) {
      assertThat(provided).isInstanceOf(JSType.class);
    }

    checkEqualityAgainst((JSType) provided, true, NATURAL_EQUIVALENCE);
  }

  public void isStructurallyEqualTo(@Nullable JSType provided) {
    checkEqualityAgainst(provided, true, STRUCTURAL_EQUIVALENCE);
  }

  public void isNotEqualTo(@Nullable JSType provided) {
    checkEqualityAgainst(provided, false, NATURAL_EQUIVALENCE);
  }

  public void isNotStructurallyEqualTo(@Nullable JSType provided) {
    checkEqualityAgainst(provided, false, STRUCTURAL_EQUIVALENCE);
  }

  public void isNumber() {
    check("isNumberValueType()").that(actualNonNull().isNumberValueType()).isTrue();
  }

  public void isString() {
    check("isStringValueType()").that(actualNonNull().isStringValueType()).isTrue();
  }

  public void isBoolean() {
    check("isBooleanValueType()").that(actualNonNull().isBooleanValueType()).isTrue();
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
    return actual();
  }

  private void checkEqualityAgainst(
      @Nullable JSType provided, boolean expectation, Equivalence equivalence) {
    String providedString = debugStringOf(provided);
    String actualString = debugStringOf(actual());

    boolean actualEqualsProvided = equivalence.test(actual(), provided);
    if (actualEqualsProvided != expectation) {
      failWithActual(
          fact("Types expected to be equal", expectation), //
          fact(equivalence.stringify(actualString, providedString), actualEqualsProvided), //
          fact("provided", providedString));
    }

    boolean providedEqualsActual = equivalence.test(provided, actual());
    if (actualEqualsProvided != providedEqualsActual) {
      failWithActual(
          simpleFact("Equality should be symmetric"), //
          fact(equivalence.stringify(actualString, providedString), actualEqualsProvided),
          fact(equivalence.stringify(providedString, actualString), providedEqualsActual),
          fact("provided", providedString));
    }

    if (expectation) {
      if (equivalence.hash(actual()) != equivalence.hash(provided)) {
        failWithActual(
            simpleFact("If two types are equal their hashcodes must also be equal"), //
            fact("definition of equality", equivalence.stringify("actual", "provided")),
            fact("hash of actual", equivalence.hash(actual())),
            fact("hash of provided", equivalence.hash(provided)),
            fact("provided", providedString));
      }
    }
  }

  private abstract static class Equivalence {
    public final boolean test(@Nullable JSType receiver, @Nullable JSType parameter) {
      // As long as a real value is provided for `receiver` we want to see how its methods handle
      // any value of `parameter`, including `null`.
      return (receiver == null) ? (parameter == null) : nullUnsafeTest(receiver, parameter);
    }

    /** Calls a method on {@code receiver}, passing {@code parameter}, that defines an equality. */
    @ForOverride
    protected abstract boolean nullUnsafeTest(JSType receiver, @Nullable JSType parameter);

    public final int hash(@Nullable JSType type) {
      return (type == null) ? 0 : nullUnsafeHash(type);
    }

    /** Generates a hashcode for {@code type} consistent with this definition of equality. */
    @ForOverride
    protected abstract int nullUnsafeHash(JSType type);

    /**
     * Returns a representation of {@link #test(JSType, JSType)} on {@code receiver} and {@code
     * parameter}.
     */
    public abstract String stringify(@Nullable String receiver, @Nullable String parameter);
  }

  private static final Equivalence NATURAL_EQUIVALENCE =
      new Equivalence() {
        @Override
        protected boolean nullUnsafeTest(JSType receiver, @Nullable JSType parameter) {
          return receiver.equals(parameter);
        }

        @Override
        protected int nullUnsafeHash(JSType type) {
          return type.hashCode();
        }

        @Override
        public String stringify(@Nullable String receiver, @Nullable String parameter) {
          return "(" + receiver + ").equals(" + parameter + ")";
        }
      };

  private static final Equivalence STRUCTURAL_EQUIVALENCE =
      new Equivalence() {
        @Override
        protected boolean nullUnsafeTest(JSType receiver, @Nullable JSType parameter) {
          return receiver.isEquivalentTo(parameter, true);
        }

        @Override
        protected int nullUnsafeHash(JSType type) {
          // TODO(nickreid): Give this a real implementation if hashcodes for structural equivalence
          // become useful in production code.
          return 1;
        }

        @Override
        public String stringify(@Nullable String receiver, @Nullable String parameter) {
          return "(" + receiver + ").isEquivalentTo((" + parameter + "), true)";
        }
      };

  @Override
  protected String actualCustomStringRepresentation() {
    return debugStringOf(actual());
  }

  private static String debugStringOf(JSType type) {
    return (type == null)
        ? "[Java null]"
        : type.toString() + " [instanceof " + type.getClass().getName() + "]";
  }

  /** Implements test functions specific to function types. */
  public class FunctionTypeSubject {
    private FunctionType actualFunctionType() {
      return checkNotNull(actualNonNull().toMaybeFunctionType(), actual());
    }

    public TypeSubject hasTypeOfThisThat() {
      return assertType(actualFunctionType().getTypeOfThis());
    }
  }
}
