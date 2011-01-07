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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.util.Collections;
import java.util.Set;

/**
 * An object type which uses composition to delegate all calls.
 *
 * @see NamedType
 * @see ParameterizedType
 *
 */
class ProxyObjectType extends ObjectType {
  private static final long serialVersionUID = 1L;

  private JSType referencedType;
  private ObjectType referencedObjType;

  ProxyObjectType(JSTypeRegistry registry, JSType referencedType) {
    super(registry);
    setReferencedType(referencedType);
  }

  JSType getReferencedTypeInternal() {
    return referencedType;
  }

  void setReferencedType(JSType referencedType) {
    this.referencedType = referencedType;
    if (referencedType instanceof ObjectType) {
      this.referencedObjType = (ObjectType) referencedType;
    } else {
      this.referencedObjType = null;
    }
  }

  @Override
  public String getReferenceName() {
    return referencedObjType == null ?
        "" : referencedObjType.getReferenceName();
  }

  @Override
  public boolean hasReferenceName() {
    return referencedObjType == null ?
        null : referencedObjType.hasReferenceName();
  }

  @Override public boolean matchesNumberContext() {
    return referencedType.matchesNumberContext();
  }

  @Override
  public boolean matchesStringContext() {
    return referencedType.matchesStringContext();
  }

  @Override public boolean matchesObjectContext() {
    return referencedType.matchesObjectContext();
  }

  @Override
  public boolean canBeCalled() {
    return referencedType.canBeCalled();
  }

  @Override
  public boolean isUnknownType() {
    return referencedType.isUnknownType();
  }

  @Override
  public boolean isCheckedUnknownType() {
    return referencedType.isCheckedUnknownType();
  }

  @Override
  public boolean isNullable() {
    return referencedType.isNullable();
  }

  @Override
  public boolean isFunctionPrototypeType() {
    return referencedType.isFunctionPrototypeType();
  }

  @Override
  public boolean isEnumType() {
    return referencedType.isEnumType();
  }

  @Override
  public boolean isEnumElementType() {
    return referencedType.isEnumElementType();
  }

  @Override
  public boolean isConstructor() {
    return referencedType.isConstructor();
  }

  @Override
  public boolean isNominalType() {
    return referencedType.isNominalType();
  }

  @Override
  public boolean isInstanceType() {
    return referencedType.isInstanceType();
  }

  @Override
  public boolean isInterface() {
    return referencedType.isInterface();
  }

  @Override
  public boolean isOrdinaryFunction() {
    return referencedType.isOrdinaryFunction();
  }

  @Override
  public TernaryValue testForEquality(JSType that) {
    return referencedType.testForEquality(that);
  }

  @Override
  public boolean isSubtype(JSType that) {
    return referencedType.isSubtype(that);
  }

  @Override
  public Iterable<ObjectType> getCtorImplementedInterfaces() {
    return referencedObjType == null ? Collections.<ObjectType>emptyList() :
        referencedObjType.getCtorImplementedInterfaces();
  }

  @Override
  public boolean canAssignTo(JSType that) {
    return referencedType.canAssignTo(that);
  }

  @Override
  public boolean isEquivalentTo(JSType that) {
    if (this == that) {
      return true;
    }
    return referencedType.isEquivalentTo(that);
  }

  @Override
  public int hashCode() {
    return referencedType.hashCode();
  }

  @Override
  public String toString() {
    return referencedType.toString();
  }

  @Override
  public ObjectType getImplicitPrototype() {
    return referencedObjType == null ? null :
        referencedObjType.getImplicitPrototype();
  }

  @Override
  boolean defineProperty(String propertyName, JSType type,
      boolean inferred, boolean inExterns, Node propertyNode) {
    return referencedObjType == null ? true :
        referencedObjType.defineProperty(
            propertyName, type, inferred, inExterns, propertyNode);
  }

  @Override
  public boolean isPropertyTypeDeclared(String propertyName) {
    return referencedObjType == null ? false :
        referencedObjType.isPropertyTypeDeclared(propertyName);
  }

  @Override
  public Node getPropertyNode(String propertyName) {
    return referencedObjType == null ? null :
        referencedObjType.getPropertyNode(propertyName);
  }

  @Override
  public boolean isPropertyTypeInferred(String propertyName) {
    return referencedObjType == null ? false :
        referencedObjType.isPropertyTypeInferred(propertyName);
  }

  @Override
  public boolean isPropertyInExterns(String propertyName) {
    return referencedObjType == null ? false :
        referencedObjType.isPropertyInExterns(propertyName);
  }

  @Override
  public int getPropertiesCount() {
    return referencedObjType == null ? 0 :
        referencedObjType.getPropertiesCount();
  }

  @Override
  protected void collectPropertyNames(Set<String> props) {
    if (referencedObjType != null) {
      referencedObjType.collectPropertyNames(props);
    }
  }

  @Override
  public JSType findPropertyType(String propertyName) {
    return referencedType.findPropertyType(propertyName);
  }

  @Override
  public JSType getPropertyType(String propertyName) {
    return referencedObjType == null ?
        getNativeType(JSTypeNative.UNKNOWN_TYPE) :
        referencedObjType.getPropertyType(propertyName);
  }

  @Override
  public JSDocInfo getJSDocInfo() {
    return referencedType.getJSDocInfo();
  }

  @Override
  public void setJSDocInfo(JSDocInfo info) {
    if (referencedObjType != null) {
      referencedObjType.setJSDocInfo(info);
    }
  }

  @Override
  public JSDocInfo getOwnPropertyJSDocInfo(String propertyName) {
    return referencedObjType == null ? null :
        referencedObjType.getOwnPropertyJSDocInfo(propertyName);
  }

  @Override
  public void setPropertyJSDocInfo(String propertyName, JSDocInfo info,
      boolean inExterns) {
    if (referencedObjType != null) {
      referencedObjType.setPropertyJSDocInfo(propertyName, info, inExterns);
    }
  }

  @Override
  public boolean hasProperty(String propertyName) {
    return referencedObjType == null ? false :
        referencedObjType.hasProperty(propertyName);
  }

  @Override
  public boolean hasOwnProperty(String propertyName) {
    return referencedObjType == null ? false :
        referencedObjType.hasOwnProperty(propertyName);
  }

  @Override
  public Set<String> getOwnPropertyNames() {
    return referencedObjType == null ? ImmutableSet.<String>of() :
        referencedObjType.getOwnPropertyNames();
  }

  @Override
  public FunctionType getConstructor() {
    return referencedObjType == null ? null :
        referencedObjType.getConstructor();
  }

  @Override
  public JSType getParameterType() {
    return referencedObjType == null ? null :
        referencedObjType.getParameterType();
  }

  @Override
  public JSType getIndexType() {
    return referencedObjType == null ? null :
        referencedObjType.getIndexType();
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return referencedType.visit(visitor);
  }

  @Override
  JSType resolveInternal(ErrorReporter t, StaticScope<JSType> scope) {
    setReferencedType(referencedType.resolve(t, scope));
    return this;
  }

  @Override
  public String toDebugHashCodeString() {
    return "{proxy:" + referencedType.toDebugHashCodeString() + "}";
  }
}
