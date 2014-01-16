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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;

import java.util.List;
import java.util.Set;

/**
 * A visitor implementation that enables type substitutions.
 *
 * @author johnlenz@google.com (John Lenz)
 */
public class ModificationVisitor implements Visitor<JSType> {

  private final JSTypeRegistry registry;
  private final boolean visitProperties;
  private final Set<JSType> seenTypes = Sets.newIdentityHashSet();

  public ModificationVisitor(JSTypeRegistry registry, boolean visitProperties) {
    this.registry = registry;
    this.visitProperties = visitProperties;
  }

  @Override
  public JSType caseNoType(NoType type) {
    return type;
  }

  @Override
  public JSType caseEnumElementType(EnumElementType type) {
    return type;
  }

  @Override
  public JSType caseAllType() {
    return getNativeType(JSTypeNative.ALL_TYPE);
  }

  @Override
  public JSType caseBooleanType() {
    return getNativeType(JSTypeNative.BOOLEAN_TYPE);
  }

  @Override
  public JSType caseNoObjectType() {
    return getNativeType(JSTypeNative.NO_OBJECT_TYPE);
  }

  @Override
  public JSType caseFunctionType(FunctionType type) {
    if (isNativeFunctionType(type)) {
      return type;
    }

    if (!type.isOrdinaryFunction() && !type.isConstructor()) {
      return type;
    }

    boolean changed = false;

    JSType beforeThis = type.getTypeOfThis();
    JSType afterThis = coerseToThisType(beforeThis.visit(this));
    if (beforeThis != afterThis) {
      changed = true;
    }

    JSType beforeReturn = type.getReturnType();
    JSType afterReturn = beforeReturn.visit(this);
    if (beforeReturn != afterReturn) {
      changed = true;
    }

    FunctionParamBuilder paramBuilder = new FunctionParamBuilder(registry);
    for (Node paramNode : type.getParameters()) {
      JSType beforeParamType = paramNode.getJSType();
      JSType afterParamType = beforeParamType.visit(this);
      if (beforeParamType != afterParamType) {
        changed = true;
      }
      if (paramNode.isOptionalArg()) {
        paramBuilder.addOptionalParams(afterParamType);
      } else if (paramNode.isVarArgs()) {
        paramBuilder.addVarArgs(afterParamType);
      } else {
        paramBuilder.addRequiredParams(afterParamType);
      }
    }

    if (changed) {
      FunctionBuilder builder = new FunctionBuilder(registry)
          .setIsConstructor(type.isConstructor())
          .withParams(paramBuilder)
          .withReturnType(afterReturn)
          .withTypeOfThis(afterThis)
          .withTemplateKeys(
              type.getTemplateTypeMap().getUnfilledTemplateKeys());
      return builder.build();
    }

    return type;
  }

  private JSType coerseToThisType(JSType type) {
    return type != null ? type : registry.getNativeObjectType(
        JSTypeNative.UNKNOWN_TYPE);
  }

  @Override
  public JSType caseObjectType(ObjectType objType) {
    if (!visitProperties
        || objType.isNominalType()
        || objType instanceof ProxyObjectType
        || !objType.isRecordType()) {
      return objType;
    }

    if (seenTypes.contains(objType)) {
      return objType;
    }
    seenTypes.add(objType);

    boolean changed = false;
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    for (String prop : objType.getOwnPropertyNames()) {
      Node propertyNode = objType.getPropertyNode(prop);
      JSType beforeType = objType.getPropertyType(prop);
      JSType afterType = beforeType.visit(this);
      if (beforeType != afterType) {
        changed = true;
      }
      builder.addProperty(prop, afterType, propertyNode);
    }

    seenTypes.remove(objType);

    if (changed) {
      return builder.build();
    }

    return objType;
  }

  @Override
  public JSType caseTemplatizedType(TemplatizedType type) {
    boolean changed = false;
    ObjectType beforeBaseType = type.getReferencedType();
    ObjectType afterBaseType = ObjectType.cast(beforeBaseType.visit(this));
    if (beforeBaseType != afterBaseType) {
      changed = true;
    }

    ImmutableList.Builder<JSType> builder = ImmutableList.builder();
    for (JSType beforeTemplateType : type.getTemplateTypes()) {
      JSType afterTemplateType = beforeTemplateType.visit(this);
      if (beforeTemplateType != afterTemplateType) {
        changed = true;
      }
      builder.add(afterTemplateType);
    }

    if (changed) {
      type = registry.createTemplatizedType(afterBaseType, builder.build());
    }
    return type;
  }

  @Override
  public JSType caseUnknownType() {
    return getNativeType(JSTypeNative.UNKNOWN_TYPE);
  }

  @Override
  public JSType caseNullType() {
    return getNativeType(JSTypeNative.NULL_TYPE);
  }

  @Override
  public JSType caseNumberType() {
    return getNativeType(JSTypeNative.NUMBER_TYPE);
  }

  @Override
  public JSType caseStringType() {
    return getNativeType(JSTypeNative.STRING_TYPE);
  }

  @Override
  public JSType caseVoidType() {
    return getNativeType(JSTypeNative.VOID_TYPE);
  }

  @Override
  public JSType caseUnionType(UnionType type) {
    boolean changed = false;
    List<JSType> results = Lists.newArrayList();
    for (JSType alternative : type.getAlternates()) {
      JSType replacement = alternative.visit(this);
      if (replacement != alternative) {
        changed = true;
      }
      results.add(replacement);
    }

    if (changed) {
      UnionTypeBuilder builder = new UnionTypeBuilder(registry);
      for (JSType alternate : results) {
        builder.addAlternate(alternate);
      }
      return builder.build();  // maybe not a union
    }

    return type;
  }

  @Override
  public JSType caseTemplateType(TemplateType type) {
    return type;
  }

  private JSType getNativeType(JSTypeNative nativeType) {
    return registry.getNativeType(nativeType);
  }

  private boolean isNativeFunctionType(FunctionType type) {
    return type.isNativeObjectType();
  }

  @Override
  public JSType caseNamedType(NamedType type) {
    // The internals of a named type aren't interesting.
    return type;
  }

  @Override
  public JSType caseProxyObjectType(ProxyObjectType type) {
    // Be careful not to unwrap a type unless it has changed.
    JSType beforeType = type.getReferencedTypeInternal();
    JSType replacement = beforeType.visit(this);
    if (replacement != beforeType) {
      return replacement;
    }
    return type;
  }
}
