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
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType.Kind;
import java.util.Set;

/**
 * A builder class for function and arrow types.
 *
 * If you need to build an interface constructor,
 * use {@link JSTypeRegistry#createInterfaceType}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class FunctionBuilder {

  // Bit masks for various boolean properties
  private static final int IS_ABSTRACT = 0x1;
  private static final int IS_NATIVE = 0x2;
  private static final int INFERRED_RETURN_TYPE = 0x4;
  private static final int RETURNS_OWN_INSTANCE_TYPE = 0x8;

  private final JSTypeRegistry registry;
  private String name = null;
  private Node sourceNode = null;
  private Node parametersNode = null;
  private JSType returnType = null;
  private JSType typeOfThis = null;
  private ObjectType setPrototypeBasedOn = null;
  private TemplateTypeMap templateTypeMap = null;
  private Set<TemplateType> constructorOnlyKeys = ImmutableSet.of();
  private Kind kind = Kind.ORDINARY;
  private int properties = 0;

  public FunctionBuilder(JSTypeRegistry registry) {
    this.registry = registry;
  }

  /** Set the name of the function type. */
  public FunctionBuilder withName(String name) {
    this.name = name;
    return this;
  }

  /** Set the source node of the function type. */
  public FunctionBuilder withSourceNode(Node sourceNode) {
    this.sourceNode = sourceNode;
    return this;
  }

  /**
   * Set the parameters of the function type with a specially-formatted node.
   */
  public FunctionBuilder withParamsNode(Node parametersNode) {
    this.parametersNode = parametersNode;
    return this;
  }

  /**
   * Set the parameters of the function type with a specially-formatted node.
   */
  FunctionBuilder withEmptyParams() {
    this.parametersNode = registry.createEmptyParams();
    return this;
  }

  /** Set the return type. */
  public FunctionBuilder withReturnType(JSType returnType) {
    this.returnType = returnType;
    return this;
  }

  /** Set the return type and whether it's inferred. */
  public FunctionBuilder withReturnType(JSType returnType, boolean inferred) {
    this.returnType = returnType;
    this.properties =
        inferred ? this.properties | INFERRED_RETURN_TYPE : this.properties & ~INFERRED_RETURN_TYPE;
    return this;
  }

  /** Set the return type to be a constructor's own instance type. */
  FunctionBuilder withReturnsOwnInstanceType() {
    this.properties = this.properties | RETURNS_OWN_INSTANCE_TYPE;
    return this;
  }

  /** Sets an inferred return type. */
  public FunctionBuilder withInferredReturnType(JSType returnType) {
    this.returnType = returnType;
    this.properties = this.properties | INFERRED_RETURN_TYPE;
    return this;
  }

  /** Set the "this" type. */
  public FunctionBuilder withTypeOfThis(JSType typeOfThis) {
    this.typeOfThis = typeOfThis;
    return this;
  }

  /** Set the template name. */
  public FunctionBuilder withTemplateKeys(ImmutableList<TemplateType> templateKeys) {
    this.templateTypeMap = registry.createTemplateTypeMap(templateKeys, null);
    return this;
  }

  /** Set the template name. */
  public FunctionBuilder withTemplateKeys(TemplateType... templateKeys) {
    this.templateTypeMap = registry.createTemplateTypeMap(ImmutableList.copyOf(templateKeys), null);
    return this;
  }

  FunctionBuilder withExtendedTemplate(TemplateType key, JSType value) {
    this.templateTypeMap =
        templateTypeMap.extend(
            registry.createTemplateTypeMap(ImmutableList.of(key), ImmutableList.of(value)));
    return this;
  }

  FunctionBuilder withTemplateTypeMap(TemplateTypeMap templateTypeMap) {
    this.templateTypeMap = templateTypeMap;
    return this;
  }

  /**
   * Specifies a subset of the template keys that only apply to the constructor, and should be
   * removed from the instance type.  These keys must still be passed to {@link #withTemplateKeys}.
   */
  public FunctionBuilder withConstructorTemplateKeys(Iterable<TemplateType> constructorOnlyKeys) {
    this.constructorOnlyKeys = ImmutableSet.copyOf(constructorOnlyKeys);
    return this;
  }

  /** Set the function kind. */
  FunctionBuilder withKind(FunctionType.Kind kind) {
    this.kind = kind;
    return this;
  }

  /** Make this a constructor. */
  public FunctionBuilder forConstructor() {
    this.kind = FunctionType.Kind.CONSTRUCTOR;
    return this;
  }

  /** Make this an interface. */
  public FunctionBuilder forInterface() {
    this.kind = FunctionType.Kind.INTERFACE;
    this.parametersNode = registry.createEmptyParams();
    return this;
  }

  /** Make this a native type. */
  FunctionBuilder forNativeType() {
    this.properties = this.properties | IS_NATIVE;
    return this;
  }

  /** Mark abstract method. */
  public FunctionBuilder withIsAbstract(boolean isAbstract) {
    this.properties = isAbstract ? this.properties | IS_ABSTRACT : this.properties & ~IS_ABSTRACT;
    return this;
  }

  /** Set the prototype property of a constructor. */
  public FunctionBuilder withPrototypeBasedOn(ObjectType setPrototypeBasedOn) {
    this.setPrototypeBasedOn = setPrototypeBasedOn;
    return this;
  }

  /** Copies all the information from another function type. */
  public FunctionBuilder copyFromOtherFunction(FunctionType otherType) {
    int isNative = otherType.isNativeObjectType() ? IS_NATIVE : 0;
    int isAbstract = otherType.isAbstract() ? IS_ABSTRACT : 0;
    int inferredReturnType = otherType.isReturnTypeInferred() ? INFERRED_RETURN_TYPE : 0;
    this.name = otherType.getReferenceName();
    this.sourceNode = otherType.getSource();
    this.parametersNode = otherType.getParametersNode();
    this.returnType = otherType.getReturnType();
    this.typeOfThis = otherType.getTypeOfThis();
    this.templateTypeMap = otherType.getTemplateTypeMap();
    this.kind = otherType.getKind();
    this.properties = isNative | isAbstract | inferredReturnType;
    return this;
  }

  /** Construct a new function type. */
  public FunctionType build() {
    // TODO(sdh): Should we do any validation here?
    boolean inferredReturnType = (properties & INFERRED_RETURN_TYPE) != 0;
    boolean isNative = (properties & IS_NATIVE) != 0;
    boolean isAbstract = (properties & IS_ABSTRACT) != 0;
    boolean returnsOwnInstanceType = (properties & RETURNS_OWN_INSTANCE_TYPE) != 0;
    boolean hasConstructorOnlyKeys = !constructorOnlyKeys.isEmpty();
    if (hasConstructorOnlyKeys) {
      // We can't pass in the correct this type yet because it depends on the finished constructor.
      // Instead, just pass in unknown so that it doesn't try to instantiate a new instance type.
      typeOfThis = registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
    }
    FunctionType ft = new FunctionType(
        registry,
        name,
        sourceNode,
        new ArrowType(registry, parametersNode, returnType, inferredReturnType),
        typeOfThis,
        templateTypeMap,
        kind,
        isNative,
        isAbstract);
    if (setPrototypeBasedOn != null) {
      ft.setPrototypeBasedOn(setPrototypeBasedOn);
    }
    if (returnsOwnInstanceType) {
      ft.getInternalArrowType().returnType = ft.getInstanceType();
    }
    if (hasConstructorOnlyKeys) {
      ft.setInstanceType(
          new InstanceObjectType(
              registry, ft, isNative, templateTypeMap.remove(constructorOnlyKeys)));
    }
    return ft;
  }
}
