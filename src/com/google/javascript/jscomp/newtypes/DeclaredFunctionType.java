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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
public final class DeclaredFunctionType implements Serializable {
  private final List<JSType> requiredFormals;
  private final List<JSType> optionalFormals;
  private final JSType restFormals;
  private final JSType returnType;
  // If this DeclaredFunctionType is a constructor/interface, this field stores
  // the type of the instance.
  private final JSType nominalType;
  // If this DeclaredFunctionType is a prototype method, this field stores the
  // type of the instance.
  private final JSType receiverType;
  private final TypeParameters typeParameters;

  private final JSTypes commonTypes;
  private final boolean isAbstract;

  private DeclaredFunctionType(
      JSTypes commonTypes,
      List<JSType> requiredFormals,
      List<JSType> optionalFormals,
      JSType restFormals,
      JSType retType,
      JSType nominalType,
      JSType receiverType,
      TypeParameters typeParameters,
      boolean isAbstract) {
    checkArgument(retType == null || !retType.isBottom());
    checkNotNull(commonTypes);
    this.commonTypes = commonTypes;
    this.requiredFormals = requiredFormals;
    this.optionalFormals = optionalFormals;
    this.restFormals = restFormals;
    this.returnType = retType;
    this.nominalType = nominalType;
    this.receiverType = receiverType;
    this.typeParameters = typeParameters;
    this.isAbstract = isAbstract;
  }

  public FunctionType toFunctionType() {
    FunctionTypeBuilder builder = new FunctionTypeBuilder(this.commonTypes);
    for (JSType formal : this.requiredFormals) {
      builder.addReqFormal(formal == null ? this.commonTypes.UNKNOWN : formal);
    }
    for (JSType formal : this.optionalFormals) {
      builder.addOptFormal(formal == null ? this.commonTypes.UNKNOWN : formal);
    }
    builder.addRestFormals(this.restFormals);
    builder.addRetType(this.returnType == null ? this.commonTypes.UNKNOWN : this.returnType);
    builder.addNominalType(this.nominalType);
    builder.addReceiverType(this.receiverType);
    builder.addTypeParameters(this.typeParameters);
    builder.addAbstract(this.isAbstract);
    return builder.buildFunction();
  }

  static DeclaredFunctionType make(
      JSTypes commonTypes,
      List<JSType> requiredFormals,
      List<JSType> optionalFormals,
      JSType restFormals,
      JSType retType,
      JSType nominalType,
      JSType receiverType,
      TypeParameters typeParameters,
      boolean isAbstract) {
    if (requiredFormals == null) {
      requiredFormals = new ArrayList<>();
    }
    if (optionalFormals == null) {
      optionalFormals = new ArrayList<>();
    }
    return new DeclaredFunctionType(
        commonTypes,
        requiredFormals, optionalFormals, restFormals, retType,
        nominalType, receiverType, typeParameters, isAbstract);
  }

  static DeclaredFunctionType qmarkFunctionDeclaration(JSTypes commonTypes) {
    FunctionTypeBuilder builder = new FunctionTypeBuilder(commonTypes);
    builder.addRestFormals(commonTypes.UNKNOWN);
    builder.addRetType(commonTypes.UNKNOWN);
    return builder.buildDeclaration();
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

  public int getMaxArity() {
    if (this.restFormals != null) {
      return Integer.MAX_VALUE; // "Infinite" arity
    } else {
      return this.getOptionalArity();
    }
  }

  private int getSyntacticArity() {
    return this.getOptionalArity() + (this.restFormals == null ? 0 : 1);
  }

  public boolean hasRestFormals() {
    return restFormals != null;
  }

  public JSType getRestFormalsType() {
    checkState(restFormals != null);
    return restFormals;
  }

  public JSType getReturnType() {
    return returnType;
  }

  public JSType getThisType() {
    if (this.nominalType != null) {
      return this.nominalType;
    } else {
      return this.receiverType;
    }
  }

  public JSType getNominalType() {
    return this.nominalType;
  }

  public JSType getReceiverType() {
    return this.receiverType;
  }

  public boolean isGeneric() {
    return !typeParameters.isEmpty();
  }

  public TypeParameters getTypeParameters() {
    return this.typeParameters;
  }

  public boolean isAbstract() {
    return this.isAbstract;
  }

  public boolean isTypeVariableDefinedLocally(String tvar) {
    return getTypeVariableDefinedLocally(tvar) != null;
  }

  public String getTypeVariableDefinedLocally(String tvar) {
    String tmp = UniqueNameGenerator.findGeneratedName(tvar, this.typeParameters.asList());
    if (tmp != null) {
      return tmp;
    }
    // We don't look at this.nominalType, b/c if this function is a generic
    // constructor, then typeParameters contains the relevant type variables.
    if (this.receiverType != null) {
      NominalType recvType = this.receiverType.getNominalTypeIfSingletonObj();
      if (recvType != null && recvType.isUninstantiatedGenericType()) {
        RawNominalType rawType = recvType.getRawNominalType();
        tmp = UniqueNameGenerator.findGeneratedName(tvar, rawType.getTypeParameters());
        if (tmp != null) {
          return tmp;
        }
      }
    }
    return null;
  }

  public DeclaredFunctionType withReceiverType(JSType newReceiverType) {
    return new DeclaredFunctionType(
        this.commonTypes,
        this.requiredFormals, this.optionalFormals,
        this.restFormals, this.returnType, this.nominalType,
        newReceiverType, this.typeParameters, this.isAbstract);
  }

  public DeclaredFunctionType withTypeInfoFromSuper(
      DeclaredFunctionType superType, boolean getsTypeInfoFromParentMethod) {
    // getsTypeInfoFromParentMethod is true when a method w/out jsdoc overrides
    // a parent method. In this case, the parent may be declaring some formals
    // as optional and we want to preserve that type information here.
    if (getsTypeInfoFromParentMethod
        && getSyntacticArity() == superType.getSyntacticArity()) {
      NominalType nt = superType.nominalType == null
          ? null : superType.nominalType.getNominalTypeIfSingletonObj();
      // Only keep this.receiverType from the current type
      NominalType rt = this.receiverType == null
          ? null : this.receiverType.getNominalTypeIfSingletonObj();
      return new DeclaredFunctionType(
          this.commonTypes,
          superType.requiredFormals, superType.optionalFormals,
          superType.restFormals, superType.returnType,
          nt == null ? null : nt.getInstanceAsJSType(),
          rt == null ? null : rt.getInstanceAsJSType(),
          superType.typeParameters, this.isAbstract);
    }

    FunctionTypeBuilder builder = new FunctionTypeBuilder(this.commonTypes);
    int i = 0;
    for (JSType formal : this.requiredFormals) {
      builder.addReqFormal(formal != null ? formal : superType.getFormalType(i));
      i++;
    }
    for (JSType formal : this.optionalFormals) {
      builder.addOptFormal(formal != null ? formal : superType.getFormalType(i));
      i++;
    }
    if (this.restFormals != null) {
      builder.addRestFormals(this.restFormals);
    } else if (superType.hasRestFormals()) {
      builder.addRestFormals(superType.restFormals);
    }
    builder.addRetType(
        this.returnType != null ? this.returnType : superType.returnType);
    builder.addNominalType(this.nominalType);
    builder.addReceiverType(this.receiverType);
    if (!this.typeParameters.isEmpty()) {
      builder.addTypeParameters(this.typeParameters);
    } else if (!superType.typeParameters.isEmpty()) {
      builder.addTypeParameters(superType.typeParameters);
    }
    builder.addAbstract(this.isAbstract);
    return builder.buildDeclaration();
  }

  // Analogous to FunctionType#substituteNominalGenerics
  public DeclaredFunctionType substituteNominalGenerics(NominalType nt) {
    if (!nt.isGeneric()) {
      return this;
    }
    FunctionTypeBuilder builder = substituteGenerics(nt.getTypeMap());
    // Explicitly forget nominalType and receiverType. This method is used when
    // calculating the declared type of a method using the inherited types.
    // In withTypeInfoFromSuper, we ignore super's nominalType and receiverType.
    builder.addReceiverType(null);
    builder.addNominalType(null);
    return builder.buildDeclaration();
  }

  public DeclaredFunctionType instantiateGenericsWithUnknown() {
    return substituteGenerics(this.commonTypes.MAP_TO_UNKNOWN).buildDeclaration();
  }

  private FunctionTypeBuilder substituteGenerics(Map<String, JSType> typeMap) {
    checkState(!typeMap.isEmpty());
    // Before we switched to unique generated names for type variables, a method's type variables
    // could shadow type variables defined on the class. Check that this no longer happens.
    if (!this.commonTypes.MAP_TO_UNKNOWN.equals(typeMap)) {
      for (String typeParam : this.typeParameters.asList()) {
        checkState(!typeMap.containsKey(typeParam));
      }
    }
    FunctionTypeBuilder builder = new FunctionTypeBuilder(this.commonTypes);
    for (JSType reqFormal : requiredFormals) {
      builder.addReqFormal(reqFormal == null ? null : reqFormal.substituteGenerics(typeMap));
    }
    for (JSType optFormal : optionalFormals) {
      builder.addOptFormal(optFormal == null ? null : optFormal.substituteGenerics(typeMap));
    }
    if (restFormals != null) {
      builder.addRestFormals(restFormals.substituteGenerics(typeMap));
    }
    if (returnType != null) {
      builder.addRetType(returnType.substituteGenerics(typeMap));
    }
    if (this.receiverType != null) {
      builder.addReceiverType(this.receiverType.substituteGenerics(typeMap));
    }
    if (this.nominalType != null) {
      builder.addNominalType(this.nominalType.substituteGenerics(typeMap));
    }
    // Technically, we should only be adding the type parameters that haven't been substituted
    // away, but AFAICT, it makes no difference to just add them all.
    builder.addTypeParameters(this.typeParameters);
    return builder;
  }

  public static DeclaredFunctionType meet(Collection<DeclaredFunctionType> toMeet) {
    DeclaredFunctionType result = null;
    for (DeclaredFunctionType declType : toMeet) {
      if (result == null) {
        result = declType;
      } else {
        result = DeclaredFunctionType.meet(result, declType);
      }
    }
    return result;
  }

  private static DeclaredFunctionType meet(DeclaredFunctionType f1, DeclaredFunctionType f2) {
    if (f1.equals(f2)) {
      return f1;
    }
    JSTypes commonTypes = f1.commonTypes;
    FunctionTypeBuilder builder = new FunctionTypeBuilder(f1.commonTypes);
    int minRequiredArity = Math.min(f1.requiredFormals.size(), f2.requiredFormals.size());
    for (int i = 0; i < minRequiredArity; i++) {
      builder.addReqFormal(nullAcceptingJoin(f1.getFormalType(i), f2.getFormalType(i)));
    }
    int maxTotalArity = Math.max(
        f1.requiredFormals.size() + f1.optionalFormals.size(),
        f2.requiredFormals.size() + f2.optionalFormals.size());
    for (int i = minRequiredArity; i < maxTotalArity; i++) {
      builder.addOptFormal(nullAcceptingJoin(f1.getFormalType(i), f2.getFormalType(i)));
    }
    if (f1.restFormals != null || f2.restFormals != null) {
      builder.addRestFormals(nullAcceptingJoin(f1.restFormals, f2.restFormals));
    }
    JSType retType = nullAcceptingMeet(f1.returnType, f2.returnType);
    if (commonTypes.BOTTOM.equals(retType)) {
      return null;
    }
    builder.addRetType(retType);
    return builder.buildDeclaration();
  }

  // Returns possibly-null JSType
  private static JSType nullAcceptingJoin(JSType t1, JSType t2) {
    if (t1 == null) {
      return t2;
    } else if (t2 == null) {
      return t1;
    }
    return JSType.join(t1, t2);
  }

  // Returns possibly-null JSType
  private static JSType nullAcceptingMeet(JSType t1, JSType t2) {
    if (t1 == null) {
      return t2;
    } else if (t2 == null) {
      return t1;
    }
    return JSType.meet(t1, t2);
  }

  @Override
  public String toString() {
    return toFunctionType().toString();
  }
}
