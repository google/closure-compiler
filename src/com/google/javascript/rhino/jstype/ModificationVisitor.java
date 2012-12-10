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
import com.google.javascript.rhino.Node;

import java.util.List;

/**
 * A visitor implementation that enables type substitutions.
 *
 * @author johnlenz@google.com (John Lenz)
 */
public class ModificationVisitor implements Visitor<JSType> {

  private final JSTypeRegistry registry;

  public ModificationVisitor(JSTypeRegistry registry) {
    this.registry = registry;
  }

  @Override
  public JSType caseNoType() {
    return getNativeType(JSTypeNative.NO_TYPE);
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

    // TODO(johnlenz): remove this simplifying assumption...
    if (!type.isOrdinaryFunction()) {
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
      // TODO(johnlenz): should we support preserving template keys?
      FunctionBuilder builder = new FunctionBuilder(registry);
      builder.withParams(paramBuilder);
      builder.withReturnType(afterReturn);
      builder.withTypeOfThis(afterThis);
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
    if (objType.isTemplatized()) {
      ImmutableList.Builder<JSType> builder = ImmutableList.builder();
      for (JSType templatizedType : objType.getTemplatizedTypes()) {
        builder.add(templatizedType.visit(this));
      }
      return registry.createTemplatizedType(objType, builder.build());
    } else {
      return objType;
    }
  }

  @Override
  public JSType caseParameterizedType(ParameterizedType type) {
    ObjectType genericType = ObjectType.cast(
        type.getReferencedTypeInternal().visit(this));
    JSType paramType = type.getParameterType().visit(this);
    if (type.getReferencedTypeInternal() != genericType
        || type.getParameterType() != paramType) {
      type = registry.createParameterizedType(genericType, paramType);
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
}
