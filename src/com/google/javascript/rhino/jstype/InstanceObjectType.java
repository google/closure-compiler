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


import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;

/**
 * An object type that is an instance of some function constructor.
 */
class InstanceObjectType extends PrototypeObjectType {
  private static final long serialVersionUID = 1L;

  private final FunctionType constructor;

  InstanceObjectType(JSTypeRegistry registry, FunctionType constructor) {
    this(registry, constructor, false, null);
  }

  InstanceObjectType(JSTypeRegistry registry, FunctionType constructor,
                     boolean isNativeType,
                     ImmutableList<JSType> templatizedTypes) {
    super(registry, null, null, isNativeType, constructor.getTemplateKeys(),
        templatizedTypes);
    Preconditions.checkNotNull(constructor);
    this.constructor = constructor;
  }

  @Override
  public String getReferenceName() {
    return getConstructor().getReferenceName();
  }

  @Override
  public boolean hasReferenceName() {
    return getConstructor().hasReferenceName();
  }

  @Override
  public ObjectType getImplicitPrototype() {
    return getConstructor().getPrototype();
  }

  @Override
  public FunctionType getConstructor() {
    return constructor;
  }

  @Override
  boolean defineProperty(String name, JSType type, boolean inferred,
      Node propertyNode) {
    ObjectType proto = getImplicitPrototype();
    if (proto != null && proto.hasOwnDeclaredProperty(name)) {
      return false;
    }
    return super.defineProperty(name, type, inferred, propertyNode);
  }

  @Override
  String toStringHelper(boolean forAnnotations) {
    if (constructor.hasReferenceName()) {
      String typeString = constructor.getReferenceName();

      ImmutableList<JSType> templatizedTypes = getTemplatizedTypes();
      if (!templatizedTypes.isEmpty()) {
        typeString += ".<" + Joiner.on(",").join(templatizedTypes) + ">";
      }

      return typeString;
    } else {
      return super.toStringHelper(forAnnotations);
    }
  }

  @Override
  boolean isTheObjectType() {
    return getConstructor().isNativeObjectType()
        && "Object".equals(getReferenceName());
  }

  @Override
  public boolean isInstanceType() {
    return true;
  }

  @Override
  public boolean isArrayType() {
    return getConstructor().isNativeObjectType()
        && "Array".equals(getReferenceName());
  }

  @Override
  public boolean isStringObjectType() {
    return getConstructor().isNativeObjectType()
        && "String".equals(getReferenceName());
  }

  @Override
  public boolean isBooleanObjectType() {
    return getConstructor().isNativeObjectType()
        && "Boolean".equals(getReferenceName());
  }

  @Override
  public boolean isNumberObjectType() {
    return getConstructor().isNativeObjectType()
        && "Number".equals(getReferenceName());
  }

  @Override
  public boolean isDateType() {
    return getConstructor().isNativeObjectType()
        && "Date".equals(getReferenceName());
  }

  @Override
  public boolean isRegexpType() {
    return getConstructor().isNativeObjectType()
        && "RegExp".equals(getReferenceName());
  }

  @Override
  public boolean isNominalType() {
    return hasReferenceName();
  }

  /**
   * If this is equal to a NamedType object, its hashCode must be equal
   * to the hashCode of the NamedType object.
   */
  @Override
  public int hashCode() {
    if (hasReferenceName()) {
      return getReferenceName().hashCode();
    } else {
      return super.hashCode();
    }
  }

  @Override
  public Iterable<ObjectType> getCtorImplementedInterfaces() {
    return getConstructor().getImplementedInterfaces();
  }

  @Override
  public Iterable<ObjectType> getCtorExtendedInterfaces() {
    return getConstructor().getExtendedInterfaces();
  }

  // The owner will always be a resolved type, so there's no need to set
  // the constructor in resolveInternal.
  // (it would lead to infinite loops if we did).
  // JSType resolveInternal(ErrorReporter t, StaticScope<JSType> scope);
}
