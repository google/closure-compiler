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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * This class represents the function types for functions that are defined
 * statically in the code.
 *
 * Since these types may have incomplete ATparam and ATreturns JSDoc, this
 * class needs to allow null JSTypes for undeclared formals/return.
 * Used in the Scope class.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class DeclaredFunctionType {
  private final List<JSType> requiredFormals;
  private final List<JSType> optionalFormals;
  private final JSType restFormals;
  private final JSType returnType;
  // Non-null iff this is a constructor/interface
  private final NominalType nominalType;
  // Non-null iff this is a prototype method
  private final NominalType receiverType;
  // Non-null iff this function has an @template annotation
  private ImmutableList<String> typeParameters;

  private DeclaredFunctionType(
      List<JSType> requiredFormals,
      List<JSType> optionalFormals,
      JSType restFormals,
      JSType retType,
      NominalType nominalType,
      NominalType receiverType,
      ImmutableList<String> typeParameters) {
    this.requiredFormals = requiredFormals;
    this.optionalFormals = optionalFormals;
    this.restFormals = restFormals;
    this.returnType = retType;
    this.nominalType = nominalType;
    this.receiverType = receiverType;
    this.typeParameters = typeParameters;
  }

  public FunctionType toFunctionType() {
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    for (JSType formal : requiredFormals) {
      builder.addReqFormal(formal == null ? JSType.UNKNOWN : formal);
    }
    for (JSType formal : optionalFormals) {
      builder.addOptFormal(formal == null ? JSType.UNKNOWN : formal);
    }
    builder.addRestFormals(restFormals);
    builder.addRetType(returnType == null ? JSType.UNKNOWN : returnType);
    builder.addNominalType(nominalType);
    builder.addTypeParameters(typeParameters);
    return builder.buildFunction();
  }

  static DeclaredFunctionType make(
      List<JSType> requiredFormals,
      List<JSType> optionalFormals,
      JSType restFormals,
      JSType retType,
      NominalType nominalType,
      NominalType receiverType,
      ImmutableList<String> typeParameters) {
    if (requiredFormals == null) {
      requiredFormals = Lists.newArrayList();
    }
    if (optionalFormals == null) {
      optionalFormals = Lists.newArrayList();
    }
    return new DeclaredFunctionType(
        requiredFormals, optionalFormals, restFormals, retType,
        nominalType, receiverType, typeParameters);
  }

  // 0-indexed
  public JSType getFormalType(int argpos) {
    int numReqFormals = requiredFormals.size();
    if (argpos < numReqFormals) {
      return requiredFormals.get(argpos);
    } else if (argpos < numReqFormals + optionalFormals.size()) {
      return optionalFormals.get(argpos - numReqFormals);
    } else {
      // TODO(blickly): Distinguish between undeclared varargs and no varargs.
      return restFormals;
    }
  }

  public int getRequiredArity() {
    return requiredFormals.size();
  }

  public int getOptionalArity() {
    return requiredFormals.size() + optionalFormals.size();
  }

  public boolean hasRestFormals() {
    return restFormals != null;
  }

  public JSType getReturnType() {
    return returnType;
  }

  public NominalType getThisType() {
    if (nominalType != null) {
      return nominalType;
    } else {
      return receiverType;
    }
  }

  public NominalType getNominalType() {
    return nominalType;
  }

  public NominalType getReceiverType() {
    return receiverType;
  }

  public boolean isGeneric() {
    return typeParameters != null;
  }

  public ImmutableList<String> getTypeParameters() {
    return typeParameters;
  }

  public DeclaredFunctionType withTypeInfoFromSuper(
      DeclaredFunctionType superType) {
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    int i = 0;
    for (JSType formal : requiredFormals) {
      builder.addReqFormal(
          formal != null ? formal : superType.getFormalType(i));
      i++;
    }
    for (JSType formal : optionalFormals) {
      builder.addOptFormal(
          formal != null ? formal : superType.getFormalType(i));
      i++;
    }
    if (restFormals != null) {
      builder.addRestFormals(restFormals);
    } else if (superType.hasRestFormals()) {
      builder.addRestFormals(superType.restFormals);
    }
    builder.addRetType(returnType != null ? returnType : superType.returnType);
    builder.addNominalType(nominalType);
    builder.addReceiverType(receiverType);
    return builder.buildDeclaration();
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this.getClass())
        .add("Required formals", requiredFormals)
        .add("Optional formals", optionalFormals)
        .add("Varargs formals", restFormals)
        .add("Return", returnType)
        .add("Nominal type", nominalType).toString();
  }
}
