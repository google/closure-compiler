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

import com.google.javascript.rhino.JSDocInfo;

import java.util.List;
import java.util.Set;

/**
 * An object type which uses composition to delegate all calls.
 *
 * @see NamedType
 * @see ParameterizedType
 *
*
 */
class ProxyObjectType extends ObjectType {
  private static final long serialVersionUID = 1L;

  ObjectType referencedType;

  ProxyObjectType(JSTypeRegistry registry, ObjectType referencedType) {
    super(registry);
    this.referencedType = referencedType;
  }

  @Override
  public String getReferenceName() {
    return referencedType.getReferenceName();
  }

  @Override
  public boolean hasReferenceName() {
    return referencedType.hasReferenceName();
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
    return referencedType.getCtorImplementedInterfaces();
  }

  @Override
  public boolean canAssignTo(JSType that) {
    return referencedType.canAssignTo(that);
  }

  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }
    return referencedType.equals(that);
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
    return referencedType.getImplicitPrototype();
  }

  @Override
  boolean defineProperty(String propertyName, JSType type,
      boolean inferred, boolean inExterns) {
    return referencedType.defineProperty(propertyName, type, inferred,
                                         inExterns);
  }

  @Override
  public boolean isPropertyTypeDeclared(String propertyName) {
    return referencedType.isPropertyTypeDeclared(propertyName);
  }

  @Override
  public boolean isPropertyTypeInferred(String propertyName) {
    return referencedType.isPropertyTypeInferred(propertyName);
  }

  @Override
  public boolean isPropertyInExterns(String propertyName) {
    return referencedType.isPropertyInExterns(propertyName);
  }

  @Override
  public int getPropertiesCount() {
    return referencedType.getPropertiesCount();
  }

  @Override
  protected void collectPropertyNames(Set<String> props) {
    referencedType.collectPropertyNames(props);
  }

  @Override
  public JSType findPropertyType(String propertyName) {
    return referencedType.findPropertyType(propertyName);
  }

  @Override
  public JSType getPropertyType(String propertyName) {
    return referencedType.getPropertyType(propertyName);
  }

  @Override
  public JSDocInfo getJSDocInfo() {
    return referencedType.getJSDocInfo();
  }

  @Override
  public void setJSDocInfo(JSDocInfo info) {
    referencedType.setJSDocInfo(info);
  }

  @Override
  public JSDocInfo getOwnPropertyJSDocInfo(String propertyName) {
    return referencedType.getOwnPropertyJSDocInfo(propertyName);
  }

  @Override
  public void setPropertyJSDocInfo(String propertyName, JSDocInfo info,
      boolean inExterns) {
    referencedType.setPropertyJSDocInfo(propertyName, info, inExterns);
  }

  @Override
  public boolean hasProperty(String propertyName) {
    return referencedType.hasProperty(propertyName);
  }

  @Override
  public boolean hasOwnProperty(String propertyName) {
    return referencedType.hasOwnProperty(propertyName);
  }

  @Override
  public Set<String> getOwnPropertyNames() {
    return referencedType.getOwnPropertyNames();
  }

  @Override
  public FunctionType getConstructor() {
    return referencedType.getConstructor();
  }

  @Override
  public JSType getParameterType() {
    return referencedType.getParameterType();
  }

  @Override
  public JSType getIndexType() {
    return referencedType.getIndexType();
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return referencedType.visit(visitor);
  }
}
