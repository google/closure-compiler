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

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.javascript.rhino.jstype.JSType;
import javax.annotation.CheckReturnValue;

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

  public void isNumber() {
    check("isNumberValueType()").that(actual().isNumberValueType()).isTrue();
  }

  public void isString() {
    check("isStringValueType()").that(actual().isStringValueType()).isTrue();
  }

  public void isBoolean() {
    check("isBooleanValueType()").that(actual().isBooleanValueType()).isTrue();
  }

  public void isUnknown() {
    check("isUnknownType()").that(actual().isUnknownType()).isTrue();
  }

  public void isNotUnknown() {
    check("isNotUnknownType()").that(actual().isUnknownType()).isFalse();
  }

  public void isNotEmpty() {
    check("isNotEmptyType()").that(actual().isEmptyType()).isFalse();
  }

  public void isLiteralObject() {
    check("isLiteralObject()").that(actual().isLiteralObject()).isTrue();
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
    check("isObjectType()").that(actual().isObjectType()).isTrue();

    return check("toMaybeObjectType().getPropertyType(%s)", propName)
        .about(types())
        .that(actual().toMaybeObjectType().getPropertyType(propName));
  }

  public void isObjectTypeWithoutProperty(String propName) {
    isLiteralObject();
    withTypeOfProp(propName).isNull();
  }

  public void isSubtypeOf(JSType superType) {
    check("isSubtypeOf(%s)", superType).that(actual().isSubtypeOf(superType)).isTrue();
  }

  public void toStringIsEqualTo(String typeString) {
    check("toString()").that(actual().toString()).isEqualTo(typeString);
  }
}
