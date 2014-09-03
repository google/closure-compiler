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

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.util.Collections;

/**
 * An object type which uses composition to delegate all calls.
 *
 * @see NamedType
 * @see TemplatizedType
 *
 */
public class ProxyObjectType extends ObjectType {
  private static final long serialVersionUID = 1L;

  private JSType referencedType;
  private ObjectType referencedObjType;

  ProxyObjectType(JSTypeRegistry registry, JSType referencedType) {
    this(registry, referencedType, null);
  }

  ProxyObjectType(JSTypeRegistry registry, JSType referencedType,
                  TemplateTypeMap templateTypeMap) {
    super(registry, templateTypeMap);
    setReferencedType(referencedType);
  }

  @Override
  PropertyMap getPropertyMap() {
    return referencedObjType == null
        ? PropertyMap.immutableEmptyMap() : referencedObjType.getPropertyMap();
  }

  JSType getReferencedTypeInternal() {
    return referencedType;
  }

  ObjectType getReferencedObjTypeInternal() {
    return referencedObjType;
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

  @Override
  public boolean matchesNumberContext() {
    return referencedType.matchesNumberContext();
  }

  @Override
  public boolean matchesStringContext() {
    return referencedType.matchesStringContext();
  }

  @Override
  public boolean matchesObjectContext() {
    return referencedType.matchesObjectContext();
  }

  @Override
  public boolean canBeCalled() {
    return referencedType.canBeCalled();
  }

  @Override
  public boolean isNoType() {
    return referencedType.isNoType();
  }

  @Override
  public boolean isNoObjectType() {
    return referencedType.isNoObjectType();
  }

  @Override
  public boolean isNoResolvedType() {
    return referencedType.isNoResolvedType();
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
  public EnumType toMaybeEnumType() {
    return referencedType.toMaybeEnumType();
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
  public boolean isAllType() {
    return referencedType.isAllType();
  }

  @Override
  public boolean isStruct() {
    return referencedType.isStruct();
  }

  @Override
  public boolean isDict() {
    return referencedType.isDict();
  }

  @Override
  public boolean isNativeObjectType() {
    return referencedObjType == null
        ? false : referencedObjType.isNativeObjectType();
  }

  @Override
  public RecordType toMaybeRecordType() {
    return referencedType.toMaybeRecordType();
  }

  @Override
  public UnionType toMaybeUnionType() {
    return referencedType.toMaybeUnionType();
  }

  @Override
  public FunctionType toMaybeFunctionType() {
    return referencedType.toMaybeFunctionType();
  }

  @Override
  public EnumElementType toMaybeEnumElementType() {
    return referencedType.toMaybeEnumElementType();
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
  public FunctionType getOwnerFunction() {
    return referencedObjType == null
        ? null : referencedObjType.getOwnerFunction();
  }

  @Override
  public Iterable<ObjectType> getCtorImplementedInterfaces() {
    return referencedObjType == null ? Collections.<ObjectType>emptyList() :
        referencedObjType.getCtorImplementedInterfaces();
  }

  @Override
  public int hashCode() {
    return referencedType.hashCode();
  }

  @Override
  String toStringHelper(boolean forAnnotations) {
    return referencedType.toStringHelper(forAnnotations);
  }

  @Override
  public ObjectType getImplicitPrototype() {
    return referencedObjType == null ? null :
        referencedObjType.getImplicitPrototype();
  }

  @Override
  boolean defineProperty(String propertyName, JSType type,
      boolean inferred, Node propertyNode) {
    return referencedObjType == null ? true :
        referencedObjType.defineProperty(
            propertyName, type, inferred, propertyNode);
  }

  @Override
  public boolean removeProperty(String name) {
    return referencedObjType == null ? false :
        referencedObjType.removeProperty(name);
  }

  @Override
  public JSType findPropertyType(String propertyName) {
    return referencedType.findPropertyType(propertyName);
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
  public void setPropertyJSDocInfo(String propertyName, JSDocInfo info) {
    if (referencedObjType != null) {
      referencedObjType.setPropertyJSDocInfo(propertyName, info);
    }
  }

  @Override
  public FunctionType getConstructor() {
    return referencedObjType == null ? null :
        referencedObjType.getConstructor();
  }

  @Override
  public ImmutableList<JSType> getTemplateTypes() {
    return referencedObjType == null ? null :
        referencedObjType.getTemplateTypes();
  }

  public <T> T visitReferenceType(Visitor<T> visitor) {
    return referencedType.visit(visitor);
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseProxyObjectType(this);
  }

  @Override <T> T visit(RelationshipVisitor<T> visitor, JSType that) {
    return referencedType.visit(visitor, that);
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

  @Override
  public JSType getTypeOfThis() {
    if (referencedObjType != null) {
      return referencedObjType.getTypeOfThis();
    }
    return super.getTypeOfThis();
  }

  @Override
  public JSType collapseUnion() {
    if (referencedType.isUnionType()) {
      return referencedType.collapseUnion();
    }
    return this;
  }

  @Override
  public void matchConstraint(JSType constraint) {
    referencedType.matchConstraint(constraint);
  }

  @Override
  public TemplatizedType toMaybeTemplatizedType() {
    return referencedType.toMaybeTemplatizedType();
  }

  @Override
  public TemplateType toMaybeTemplateType() {
    return referencedType.toMaybeTemplateType();
  }

  @Override
  public boolean hasAnyTemplateTypesInternal() {
    return referencedType.hasAnyTemplateTypes();
  }

  @Override
  public TemplateTypeMap getTemplateTypeMap() {
    return referencedType.getTemplateTypeMap();
  }
}
