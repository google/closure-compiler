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

package com.google.javascript.rhino.jstype;

import com.google.common.base.Preconditions;

/**
 * An object type with a declared default element type, such as
 * <code>Array.<string></code>.
 *
 * // TODO(user): Define the subtyping relation for parameterized types. Also,
 * take parameterized type into account for equality.
 *
 */
public final class ParameterizedType extends ProxyObjectType {
  private static final long serialVersionUID = 1L;

  final JSType parameterType;

  ParameterizedType(
      JSTypeRegistry registry, ObjectType objectType, JSType parameterType) {
    super(registry, objectType);
    this.parameterType = parameterType;
  }

  @Override
  public JSType getParameterType() {
    return parameterType;
  }

  @Override
  String toStringHelper(boolean forAnnotations) {
    String result = super.toStringHelper(forAnnotations);
    return result + ".<" + parameterType.toStringHelper(forAnnotations) + ">";
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseParameterizedType(this);
  }

  @Override <T> T visit(RelationshipVisitor<T> visitor, JSType that) {
    return visitor.caseParameterizedType(this, that);
  }

  @Override
  public ParameterizedType toMaybeParameterizedType() {
    return this;
  }

  @Override
  public boolean hasAnyTemplateTypesInternal() {
    return super.hasAnyTemplateTypes() || parameterType.hasAnyTemplateTypes();
  }

  @Override
  public boolean isSubtype(JSType that) {
    return isSubtypeHelper(this, that);
  }

  boolean isParameterizeSubtypeOf(JSType thatType) {
    if (thatType.isParameterizedType()) {
      JSType thisParameter = this.parameterType;
      JSType thatParameter = thatType.toMaybeParameterizedType().parameterType;
      // Currently, there is no way to declare a parameterized type so we have
      // no way to determine if the type parameters are in anyway related.
      //
      // Right now we fallback to the raw type relationship if the raw types
      // are different. This is not great, and we'll figure out a better
      // solution later.
      if (this.wrapsSameRawType(thatType)) {
        return (thisParameter.isSubtype(thatParameter)
            || thatParameter.isSubtype(thisParameter));
      }
    }

    return this.getReferencedTypeInternal().isSubtype(thatType);
  }

  boolean wrapsSameRawType(JSType that) {
    return that.isParameterizedType() && this.getReferencedTypeInternal()
        .isEquivalentTo(
            that.toMaybeParameterizedType().getReferencedTypeInternal());
  }

  boolean wrapsRawType(JSType that) {
    return this.getReferencedTypeInternal().isEquivalentTo(that);
  }

  /**
   * Computes the greatest subtype of two related parameterized types.
   * @return The greatest subtype.
   */
  JSType getGreatestSubtypeHelper(JSType rawThat) {
    Preconditions.checkNotNull(rawThat);

    if (!wrapsSameRawType(rawThat)) {
      if (!rawThat.isParameterizedType()) {
        if (this.isSubtype(rawThat)) {
          return this;
        } else if (rawThat.isSubtype(this)) {
          return filterNoResolvedType(rawThat);
        }
      }
      if (this.isObject() && rawThat.isObject()) {
        return this.getNativeType(JSTypeNative.NO_OBJECT_TYPE);
      }
      return this.getNativeType(JSTypeNative.NO_TYPE);
    }

    ParameterizedType that = rawThat.toMaybeParameterizedType();
    Preconditions.checkNotNull(that);

    if (this.parameterType.isEquivalentTo(that.parameterType)) {
      return this;
    }

    // For types that have the same raw type but different type parameters,
    // we simply create a type has a "unknown" type parameter.  This is
    // equivalent to the raw type.
    return getReferencedObjTypeInternal();
  }
}
