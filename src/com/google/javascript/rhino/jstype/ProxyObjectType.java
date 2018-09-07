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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Predicate;
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
    setReferencedType(checkNotNull(referencedType));
  }

  @Override
  public final HasPropertyKind getPropertyKind(String propertyName, boolean autobox) {
    return referencedType.getPropertyKind(propertyName, autobox);
  }

  @Override
  final PropertyMap getPropertyMap() {
    return referencedObjType == null
        ? PropertyMap.immutableEmptyMap() : referencedObjType.getPropertyMap();
  }

  final JSType getReferencedTypeInternal() {
    return referencedType;
  }

  final ObjectType getReferencedObjTypeInternal() {
    return referencedObjType;
  }

  final void setReferencedType(JSType referencedType) {
    this.referencedType = referencedType;
    if (referencedType instanceof ObjectType) {
      this.referencedObjType = (ObjectType) referencedType;
    } else {
      this.referencedObjType = null;
    }
  }

  @Override
  public boolean setValidator(Predicate<JSType> validator) {
    // The referenced type might have specialized behavior for validation, e.g. {@link NamedType}
    // defers validation until after named type resolution.
    return referencedType.setValidator(validator);
  }

  @Override
  public String getReferenceName() {
    return referencedObjType == null ? "" : referencedObjType.getReferenceName();
  }

  @Override
  public final boolean matchesNumberContext() {
    return referencedType.matchesNumberContext();
  }

  @Override
  public final boolean matchesStringContext() {
    return referencedType.matchesStringContext();
  }

  @Override
  public final boolean matchesSymbolContext() {
    return referencedType.matchesSymbolContext();
  }

  @Override
  public final boolean matchesObjectContext() {
    return referencedType.matchesObjectContext();
  }

  @Override
  public final boolean canBeCalled() {
    return referencedType.canBeCalled();
  }

  @Override
  public final boolean isStructuralType() {
    return referencedType.isStructuralType();
  }

  @Override
  public final boolean isNoType() {
    return referencedType.isNoType();
  }

  @Override
  public final boolean isNoObjectType() {
    return referencedType.isNoObjectType();
  }

  @Override
  public final boolean isNoResolvedType() {
    return referencedType.isNoResolvedType();
  }

  @Override
  public final boolean isUnknownType() {
    return referencedType.isUnknownType();
  }

  @Override
  public final boolean isCheckedUnknownType() {
    return referencedType.isCheckedUnknownType();
  }

  @Override
  public final boolean isNullable() {
    return referencedType.isNullable();
  }

  @Override
  public final boolean isVoidable() {
    return referencedType.isVoidable();
  }

  @Override
  public final EnumType toMaybeEnumType() {
    return referencedType.toMaybeEnumType();
  }

  @Override
  public final boolean isConstructor() {
    return referencedType.isConstructor();
  }

  @Override
  public boolean isNominalType() {
    return referencedType.isNominalType();
  }

  @Override
  public final boolean isInstanceType() {
    return referencedType.isInstanceType();
  }

  @Override
  public final boolean isInterface() {
    return referencedType.isInterface();
  }

  @Override
  public final boolean isOrdinaryFunction() {
    return referencedType.isOrdinaryFunction();
  }

  @Override
  public final boolean isAllType() {
    return referencedType.isAllType();
  }

  @Override
  public final boolean isStruct() {
    return referencedType.isStruct();
  }

  @Override
  public final boolean isDict() {
    return referencedType.isDict();
  }

  @Override
  public final boolean isNativeObjectType() {
    return referencedObjType == null
        ? false : referencedObjType.isNativeObjectType();
  }

  @Override
  public final RecordType toMaybeRecordType() {
    return referencedType.toMaybeRecordType();
  }

  @Override
  public final UnionType toMaybeUnionType() {
    return referencedType.toMaybeUnionType();
  }

  @Override
  public final FunctionType toMaybeFunctionType() {
    return referencedType.toMaybeFunctionType();
  }

  @Override
  public final EnumElementType toMaybeEnumElementType() {
    return referencedType.toMaybeEnumElementType();
  }

  @Override
  public final TernaryValue testForEquality(JSType that) {
    return referencedType.testForEquality(that);
  }

  @Override
  public boolean isSubtype(JSType that) {
    return referencedType.isSubtype(that, ImplCache.create(), SubtypingMode.NORMAL);
  }

  @Override
  protected boolean isSubtype(JSType that,
      ImplCache implicitImplCache, SubtypingMode subtypingMode) {
    return referencedType.isSubtype(that, implicitImplCache, subtypingMode);
  }

  @Override
  public final FunctionType getOwnerFunction() {
    return referencedObjType == null
        ? null : referencedObjType.getOwnerFunction();
  }

  @Override
  public Iterable<ObjectType> getCtorImplementedInterfaces() {
    return referencedObjType == null ? Collections.<ObjectType>emptyList() :
        referencedObjType.getCtorImplementedInterfaces();
  }

  @Override
  public Iterable<ObjectType> getCtorExtendedInterfaces() {
    return this.referencedObjType == null
        ? Collections.<ObjectType>emptyList()
        : this.referencedObjType.getCtorExtendedInterfaces();
  }

  @Override
  int recursionUnsafeHashCode() {
    return referencedType.hashCode();
  }

  @Override
  StringBuilder appendTo(StringBuilder sb, boolean forAnnotations) {
    return referencedType.appendTo(sb, forAnnotations);
  }

  @Override
  public final ObjectType getImplicitPrototype() {
    return referencedObjType == null ? null :
        referencedObjType.getImplicitPrototype();
  }

  @Override
  boolean defineProperty(String propertyName, JSType type, boolean inferred, Node propertyNode) {
    return referencedObjType == null
        || referencedObjType.defineProperty(propertyName, type, inferred, propertyNode);
  }

  @Override
  public final boolean removeProperty(String name) {
    return referencedObjType == null ? false :
        referencedObjType.removeProperty(name);
  }

  @Override
  public JSType findPropertyType(String propertyName) {
    return referencedType.findPropertyType(propertyName);
  }

  @Override
  public final JSDocInfo getJSDocInfo() {
    return referencedType.getJSDocInfo();
  }

  @Override
  public final void setJSDocInfo(JSDocInfo info) {
    if (referencedObjType != null) {
      referencedObjType.setJSDocInfo(info);
    }
  }

  @Override
  public final void setPropertyJSDocInfo(String propertyName, JSDocInfo info) {
    if (referencedObjType != null) {
      referencedObjType.setPropertyJSDocInfo(propertyName, info);
    }
  }

  @Override
  public final FunctionType getConstructor() {
    return referencedObjType == null ? null :
        referencedObjType.getConstructor();
  }

  @Override
  public ImmutableList<JSType> getTemplateTypes() {
    return referencedObjType == null ? null :
        referencedObjType.getTemplateTypes();
  }

  public final <T> T visitReferenceType(Visitor<T> visitor) {
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
  JSType resolveInternal(ErrorReporter reporter) {
    setReferencedType(referencedType.resolve(reporter));
    return this;
  }

  @Override
  public final String toDebugHashCodeString() {
    return "{proxy:" + referencedType.toDebugHashCodeString() + "}";
  }

  @Override
  public final JSType getTypeOfThis() {
    if (referencedObjType != null) {
      return referencedObjType.getTypeOfThis();
    }
    return super.getTypeOfThis();
  }

  @Override
  public final JSType collapseUnion() {
    if (referencedType.isUnionType()) {
      return referencedType.collapseUnion();
    }
    return this;
  }

  @Override
  public final void matchConstraint(JSType constraint) {
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
