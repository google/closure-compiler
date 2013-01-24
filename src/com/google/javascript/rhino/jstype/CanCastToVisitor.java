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
 *   John Lenz
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

/**
 * A "can cast to" relationship visitor.
 */
class CanCastToVisitor implements RelationshipVisitor<Boolean> {

  @Override
  public Boolean caseUnknownType(JSType thisType, JSType thatType) {
    return true;
  }

  @Override
  public Boolean caseNoType(JSType thatType) {
    return true;
  }

  @Override
  public Boolean caseNoObjectType(JSType thatType) {
    return true; // TODO(johnlenz): restrict to objects
  }

  @Override
  public Boolean caseAllType(JSType thatType) {
    return true;
  }

  boolean canCastToUnion(JSType thisType, UnionType unionType) {
    for (JSType type : unionType.getAlternates()) {
      if (thisType.visit(this, type)) {
        return true;
      }
    }
    return false;
  }

  boolean canCastToFunction(JSType thisType, FunctionType functionType) {
    if (thisType.isFunctionType()) {
      // TODO(johnlenz): visit function parts
      return true;
    } else {
      return thisType.isSubtype(functionType)
          || functionType.isSubtype(thisType);
    }
  }

  private boolean isInterface(JSType type) {
    ObjectType objType = type.toObjectType();
    if (objType != null) {
      JSType constructor = objType.getConstructor();
      return constructor != null && constructor.isInterface();
    }
    return false;
  }

  Boolean castCastToHelper(JSType thisType, JSType thatType) {
    if (thatType.isUnknownType()
        || thatType.isAllType()
        || thatType.isNoObjectType() // TODO(johnlenz): restrict to objects
        || thatType.isNoType()) {
      return true;
    } else if (thisType.isRecordType() || thatType.isRecordType()) {
      return true;  // TODO(johnlenz): are there any misuses we can catch?
    } else if (isInterface(thisType) || isInterface(thatType)) {
      return true;  // TODO(johnlenz): are there any misuses we can catch?
    } else if (thatType.isEnumElementType()) {
      return thisType.visit(this,
          thatType.toMaybeEnumElementType().getPrimitiveType());
    } else if (thatType.isUnionType()) {
      return canCastToUnion(thisType, thatType.toMaybeUnionType());
    } else if (thatType.isFunctionType()) {
      return canCastToFunction(thisType, thatType.toMaybeFunctionType());
    } else if (thatType.isTemplatizedType()) {
      // TODO(johnlenz): once the templated type work is finished,
      // restrict the type parameters.
      return thisType.visit(this,
          thatType.toMaybeTemplatizedType().getReferencedTypeInternal());
    }

    return thisType.isSubtype(thatType) || thatType.isSubtype(thisType);
  }

  @Override
  public Boolean caseValueType(ValueType thisType, JSType thatType) {
    return castCastToHelper(thisType, thatType);
  }

  @Override
  public Boolean caseObjectType(ObjectType thisType, JSType thatType) {
    return castCastToHelper(thisType, thatType);
  }

  @Override
  public Boolean caseFunctionType(FunctionType thisType, JSType thatType) {
    return castCastToHelper(thisType, thatType);
  }

  @Override
  public Boolean caseUnionType(UnionType thisType, JSType thatType) {
    boolean visited = false;
    for (JSType type : thisType.getAlternates()) {
      if (type.isVoidType() || type.isNullType()) {
        // Don't allow if the only match between the types is null or void,
        // otherwise any nullable type would be castable to any other nullable
        // type and we don't want that.
      } else {
        visited = true;
        if (type.visit(this, thatType)) {
          return true;
        }
      }
    }

    // Special case the "null|undefined" union and allow it to be cast
    // to any cast to any type containing allowing either null|undefined.
    if (!visited) {
      JSType NULL_TYPE = thisType.getNativeType(JSTypeNative.NULL_TYPE);
      JSType VOID_TYPE = thisType.getNativeType(JSTypeNative.VOID_TYPE);
      return NULL_TYPE.visit(this, thatType) || VOID_TYPE.visit(this, thatType);
    }

    return false;
  }

  @Override
  public Boolean caseTemplatizedType(
      TemplatizedType thisType, JSType thatType) {
    // TODO(johnlenz): once the templated type work is finished,
    // restrict the type parameters.
    return thisType.getReferencedTypeInternal().visit(this, thatType);
  }

  @Override
  public Boolean caseTemplateType(TemplateType thisType, JSType thatType) {
    return true;
  }

  @Override
  public Boolean caseEnumElementType(
      EnumElementType typeType, JSType thatType) {
    return typeType.getPrimitiveType().visit(this, thatType);
  }
}
