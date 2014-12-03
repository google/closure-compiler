/*
 * Copyright 2013 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.newtypes;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A builder for {@link FunctionType} and {@link DeclaredFunctionType}.
 *
 * The builder is called during both JSDoc parsing and type inference, and
 * these parts use different warning systems, so expect the context to handle
 * the exception appropriately.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class FunctionTypeBuilder {
  static class WrongParameterOrderException extends RuntimeException {
    WrongParameterOrderException(String message) {
      super(message);
    }
  }

  private final List<JSType> requiredFormals = new ArrayList<>();
  private final List<JSType> optionalFormals = new ArrayList<>();
  private final Map<String, JSType> outerVars = new HashMap<>();
  private JSType restFormals = null;
  private JSType returnType = null;
  private boolean loose = false;
  private NominalType nominalType;
  // Only used to build DeclaredFunctionType for prototype methods
  private NominalType receiverType;
  // Non-null iff this function has an @template annotation
  private ImmutableList<String> typeParameters;

  static FunctionTypeBuilder qmarkFunctionBuilder() {
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    builder.addRestFormals(JSType.UNKNOWN);
    builder.addRetType(JSType.UNKNOWN);
    return builder;
  }

  public FunctionTypeBuilder addReqFormal(JSType t)
      throws WrongParameterOrderException {
    if (!optionalFormals.isEmpty() || restFormals != null) {
      throw new WrongParameterOrderException(
          "Cannot add required formal after optional or rest args");
    }
    requiredFormals.add(t);
    return this;
  }

  public FunctionTypeBuilder addOptFormal(JSType t)
      throws WrongParameterOrderException {
    if (restFormals != null) {
      throw new WrongParameterOrderException(
          "Cannot add optional formal after rest args");
    }
    if (t == null) {
      optionalFormals.add(null);
    } else if (t.isBottom()) {
      // We keep bottom to warn about CALL_FUNCTION_WITH_BOTTOM_FORMAL.
      optionalFormals.add(JSType.BOTTOM);
    } else {
      optionalFormals.add(JSType.join(t, JSType.UNDEFINED));
    }
    return this;
  }

  public FunctionTypeBuilder addOuterVarPrecondition(String name, JSType t) {
    outerVars.put(name, t);
    return this;
  }

  public FunctionTypeBuilder addRestFormals(JSType t) {
    Preconditions.checkState(restFormals == null);
    restFormals = t;
    return this;
  }

  public FunctionTypeBuilder addRetType(JSType t) {
    Preconditions.checkState(returnType == null);
    returnType = t;
    return this;
  }

  public FunctionTypeBuilder addLoose() {
    loose = true;
    return this;
  }

  public FunctionTypeBuilder addNominalType(NominalType cl) {
    Preconditions.checkState(nominalType == null);
    nominalType = cl;
    return this;
  }

  public FunctionTypeBuilder addTypeParameters(
      ImmutableList<String> typeParameters) {
    Preconditions.checkState(this.typeParameters == null);
    this.typeParameters = typeParameters;
    return this;
  }

  public FunctionTypeBuilder addReceiverType(NominalType cl) {
    Preconditions.checkState(receiverType == null);
    receiverType = cl;
    return this;
  }

  public DeclaredFunctionType buildDeclaration() {
    Preconditions.checkState(!loose);
    Preconditions.checkState(outerVars.isEmpty());
    return DeclaredFunctionType.make(
        requiredFormals, optionalFormals, restFormals, returnType,
        nominalType, receiverType, typeParameters);
  }

  public FunctionType buildFunction() {
    FunctionType result = FunctionType.normalized(
        requiredFormals, optionalFormals, restFormals, returnType,
        nominalType, receiverType, outerVars, typeParameters, loose);
    result.checkValid();
    return result;
  }
}
