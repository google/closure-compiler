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
import com.google.javascript.rhino.Node;

/**
 * A builder class for function and arrow types.
 *
 * If you need to build an interface constructor,
 * use {@link JSTypeRegistry#createInterfaceType}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class FunctionBuilder {
  private final JSTypeRegistry registry;
  private String name = null;
  private Node sourceNode = null;
  private Node parametersNode = null;
  private JSType returnType = null;
  private JSType typeOfThis = null;
  private TemplateTypeMap templateTypeMap = null;
  private boolean inferredReturnType = false;
  private boolean isConstructor = false;
  private boolean isNativeType = false;

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

  /** Set the parameters of the function type from a FunctionParamBuilder. */
  public FunctionBuilder withParams(FunctionParamBuilder params) {
    this.parametersNode = params.build();
    return this;
  }

  /**
   * Set the parameters of the function type with a specially-formatted node.
   */
  public FunctionBuilder withParamsNode(Node parametersNode) {
    this.parametersNode = parametersNode;
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
    this.inferredReturnType = inferred;
    return this;
  }

  /** Sets an inferred return type. */
  public FunctionBuilder withInferredReturnType(JSType returnType) {
    this.returnType = returnType;
    this.inferredReturnType = true;
    return this;
  }

  /** Set the "this" type. */
  public FunctionBuilder withTypeOfThis(JSType typeOfThis) {
    this.typeOfThis = typeOfThis;
    return this;
  }

  /** Set the template name. */
  public FunctionBuilder withTemplateKeys(
      ImmutableList<TemplateType> templateKeys) {
    this.templateTypeMap = registry.createTemplateTypeMap(templateKeys, null);
    return this;
  }

  /** Make this a constructor. */
  public FunctionBuilder forConstructor() {
    this.isConstructor = true;
    return this;
  }

  /** Set whether this is a constructor. */
  public FunctionBuilder setIsConstructor(boolean isConstructor) {
    this.isConstructor = isConstructor;
    return this;
  }

  /** Make this a native type. */
  FunctionBuilder forNativeType() {
    this.isNativeType = true;
    return this;
  }

  /** Copies all the information from another function type. */
  public FunctionBuilder copyFromOtherFunction(FunctionType otherType) {
    this.name = otherType.getReferenceName();
    this.sourceNode = otherType.getSource();
    this.parametersNode = otherType.getParametersNode();
    this.returnType = otherType.getReturnType();
    this.typeOfThis = otherType.getTypeOfThis();
    this.templateTypeMap = otherType.getTemplateTypeMap();
    this.isConstructor = otherType.isConstructor();
    this.isNativeType = otherType.isNativeObjectType();
    return this;
  }

  /** Construct a new function type. */
  public FunctionType build() {
    return new FunctionType(registry, name, sourceNode,
        new ArrowType(registry, parametersNode, returnType, inferredReturnType),
        typeOfThis, templateTypeMap, isConstructor, isNativeType);
  }
}
