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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class FunctionType {
  private final ImmutableList<JSType> requiredFormals;
  private final ImmutableList<JSType> optionalFormals;
  private final JSType restFormals;
  private final JSType returnType;
  private final boolean isLoose;
  private final ImmutableMap<String, JSType> outerVarPreconditions;
  // non-null iff this is a constructor/interface
  private final NominalType nominalType;
  // Non-null iff this function has an @template annotation
  private final ImmutableList<String> typeParameters;

  private FunctionType(
      ImmutableList<JSType> requiredFormals,
      ImmutableList<JSType> optionalFormals,
      JSType restFormals,
      JSType retType,
      NominalType nominalType,
      ImmutableMap<String, JSType> outerVars,
      ImmutableList<String> typeParameters,
      boolean isLoose) {
    this.requiredFormals = requiredFormals;
    this.optionalFormals = optionalFormals;
    this.restFormals = restFormals;
    this.returnType = retType;
    this.nominalType = nominalType;
    this.outerVarPreconditions = outerVars;
    this.typeParameters = typeParameters;
    this.isLoose = isLoose;
  }

  void checkValid() {
    if (isTopFunction()) {
      return;
    }
    for (JSType formal : requiredFormals) {
      Preconditions.checkState(formal != null);
    }
    for (JSType formal : optionalFormals) {
      Preconditions.checkState(formal != null);
    }
    Preconditions.checkState(returnType != null);
  }

  public boolean isLoose() {
    return isLoose;
  }

  FunctionType withLoose() {
    if (isTopFunction()) {
      return LOOSE_TOP_FUNCTION;
    }
    return new FunctionType(
        requiredFormals, optionalFormals, restFormals, returnType, nominalType,
        outerVarPreconditions, typeParameters, true);
  }

  static FunctionType normalized(
      List<JSType> requiredFormals,
      List<JSType> optionalFormals,
      JSType restFormals,
      JSType retType,
      NominalType nominalType,
      Map<String, JSType> outerVars,
      ImmutableList<String> typeParameters,
      boolean isLoose) {
    if (requiredFormals == null) {
      requiredFormals = ImmutableList.of();
    }
    if (optionalFormals == null) {
      optionalFormals = ImmutableList.of();
    }
    if (outerVars == null) {
      outerVars = ImmutableMap.of();
    }
    if (restFormals != null) {
      // Remove trailing optional params w/ type equal to restFormals
      for (int i = optionalFormals.size() - 1; i >= 0; i--) {
        if (restFormals.equals(optionalFormals.get(i))) {
          optionalFormals.remove(i);
        } else {
          break;
        }
      }
    }
    return new FunctionType(
        ImmutableList.copyOf(requiredFormals),
        ImmutableList.copyOf(optionalFormals),
        restFormals, retType, nominalType,
        ImmutableMap.copyOf(outerVars),
        typeParameters,
        isLoose);
  }

  @VisibleForTesting
  static JSType makeJSType(
      ImmutableList<JSType> requiredFormals,
      ImmutableList<JSType> optionalFormals,
      JSType restFormals,
      JSType retType) {
    return JSType.fromFunctionType(FunctionType.normalized(
        requiredFormals, optionalFormals, restFormals, retType,
        null, null, null, false));
  }

  // We want to warn about argument mismatch, so we don't consider a function
  // with N required arguments to have restFormals of type TOP.
  // But we allow joins (eg after an IF) to change arity, eg,
  // number->number \/ number,number->number = number,number->number

  // Theoretically, the top function takes an infinite number of required
  // arguments of type BOTTOM and returns TOP. If this function is ever called,
  // it's a type error. Despite that, we want to represent it and not go
  // directly to JSType.TOP, to avoid spurious warnings.
  // Eg, after an IF, we may see a type (number | top_function); this type could
  // get specialized to number and used legitimately.

  // We can't represent the theoretical top function, so we special-case
  // TOP_FUNCTION below. However, the outcome is the same; if our top function
  // is ever called, a warning is inevitable.
  public static final FunctionType TOP_FUNCTION = new FunctionType(
      // Call the constructor directly to set fields to null
      null, null, null, null, null, null, null, false);
  public static final FunctionType LOOSE_TOP_FUNCTION = new FunctionType(
      // Call the constructor directly to set fields to null
      null, null, null, null, null, null, null, true);
  // Corresponds to Function, which is a subtype and supertype of all functions.
  static final FunctionType QMARK_FUNCTION = FunctionType.normalized(
      null, null, JSType.UNKNOWN, JSType.UNKNOWN, null, null, null, false);

  public boolean isTopFunction() {
    if (requiredFormals == null) {
      Preconditions.checkState(
          this == TOP_FUNCTION || this == LOOSE_TOP_FUNCTION);
    }
    return this == TOP_FUNCTION || this == LOOSE_TOP_FUNCTION;
  }

  public boolean isConstructor() {
    return nominalType != null && !nominalType.isInterface();
  }

  public boolean isQmarkFunction() {
    return this == QMARK_FUNCTION;
  }

  boolean isInterfaceDefinition() {
    return nominalType != null && nominalType.isInterface();
  }

  // 0-indexed
  // Returns null if argpos indexes past the arguments
  public JSType getFormalType(int argpos) {
    Preconditions.checkArgument(!isTopFunction());
    checkValid();
    int numReqFormals = requiredFormals.size();
    if (argpos < numReqFormals) {
      Preconditions.checkState(null != requiredFormals.get(argpos));
      return requiredFormals.get(argpos);
    } else if (argpos < numReqFormals + optionalFormals.size()) {
      Preconditions.checkState(
          null != optionalFormals.get(argpos - numReqFormals));
      return optionalFormals.get(argpos - numReqFormals);
    } else {
      return restFormals;
    }
  }

  public JSType getReturnType() {
    Preconditions.checkArgument(!isTopFunction());
    if (isConstructor()) {
      return getTypeOfThis();
    } else {
      return returnType;
    }
  }

  public JSType getOuterVarPrecondition(String name) {
    Preconditions.checkArgument(!isTopFunction());
    return outerVarPreconditions.get(name);
  }

  public int getMinArity() {
    Preconditions.checkArgument(!isTopFunction());
    return requiredFormals.size();
  }

  public int getMaxArity() {
    Preconditions.checkArgument(!isTopFunction());
    if (restFormals != null) {
      return Integer.MAX_VALUE; // "Infinite" arity
    } else {
      return requiredFormals.size() + optionalFormals.size();
    }
  }

  public JSType getTypeOfThis() {
    Preconditions.checkNotNull(nominalType);
    return JSType.fromObjectType(ObjectType.fromNominalType(nominalType));
  }

  public JSType createConstructorObject() {
    Preconditions.checkState(nominalType != null);
    return nominalType.createConstructorObject(this);
  }

  // Returns non-null JSType
  private static JSType nullAcceptingMeet(JSType t1, JSType t2) {
    Preconditions.checkArgument(t1 != null || t2 != null);
    if (t1 == null) {
      return t2;
    } else if (t2 == null) {
      return t1;
    }
    return JSType.meet(t1, t2);
  }

  private static FunctionType looseJoin(FunctionType f1, FunctionType f2) {
    Preconditions.checkArgument(f1.isLoose() || f2.isLoose());

    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    int minRequiredArity = Math.min(f1.getMinArity(), f2.getMinArity());
    for (int i = 0; i < minRequiredArity; i++) {
      builder.addReqFormal(JSType.nullAcceptingJoin(
          f1.getFormalType(i), f2.getFormalType(i)));
    }
    int maxTotalArity = Math.max(
        f1.requiredFormals.size() + f1.optionalFormals.size(),
        f2.requiredFormals.size() + f2.optionalFormals.size());
    for (int i = minRequiredArity; i < maxTotalArity; i++) {
      builder.addOptFormal(JSType.nullAcceptingJoin(
          f1.getFormalType(i), f2.getFormalType(i)));
    }
    // Loose types never have varargs, because there is no way for that
    // information to make it to a function summary
    return builder.addRetType(
        JSType.nullAcceptingJoin(f1.returnType, f2.returnType))
        .addLoose().buildFunction();
  }

  public boolean isSubtypeOf(FunctionType other) {
    // t1 <= t2 iff t2 = t1 \/ t2 doesn't hold always,
    // so we first create a new type by replacing ? in the right places.
    if (other.isTopFunction() ||
        other.isQmarkFunction() || this.isQmarkFunction()) {
      return true;
    }
    if (isTopFunction()) {
      return false;
    }
    if (this.isGeneric()) {
      // This can only happen when typechecking an assignment that "defines" a
      // polymorphic function, eg,
      // /**
      //  * @template {T}
      //  * @param {T} x
      //  */
      // Foo.prototype.method = function(x) {};

      // TODO(dimvar): This also comes up in inheritance of classes with
      // polymorphic methods; fix for that.
      return true;
    }
    Preconditions.checkState(!other.isGeneric()); // TODO(dimvar): implement it
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    int i = 0;
    for (; i < other.requiredFormals.size(); i++) {
      JSType formalType = other.getFormalType(i);
      builder.addReqFormal(formalType.isUnknown() ? JSType.BOTTOM : formalType);
    }
    for (int j = 0; j < other.optionalFormals.size(); j++) {
      JSType formalType = other.getFormalType(i + j);
      builder.addOptFormal(formalType.isUnknown() ? JSType.BOTTOM : formalType);
    }
    if (other.restFormals != null) {
      JSType formalType = other.restFormals;
      builder.addOptFormal(formalType.isUnknown() ? JSType.BOTTOM : formalType);
    }
    if (this.returnType.isUnknown()) {
      builder.addRetType(JSType.UNKNOWN);
    } else {
      builder.addRetType(other.returnType);
    }
    if (other.isLoose()) {
      builder.addLoose();
    }
    builder.addNominalType(other.nominalType);
    FunctionType newOther = builder.buildFunction();
    return newOther.equals(join(this, newOther));
  }

  static FunctionType join(FunctionType f1, FunctionType f2) {
    if (f1 == null) {
      return f2;
    } else if (f2 == null || f1.equals(f2)) {
      return f1;
    } else if (f1.isTopFunction() || f2.isTopFunction()) {
      return TOP_FUNCTION;
    }

    if (f1.isLoose() || f2.isLoose()) {
      return FunctionType.looseJoin(f1, f2);
    }

    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    int maxRequiredArity = Math.max(
        f1.requiredFormals.size(), f2.requiredFormals.size());
    for (int i = 0; i < maxRequiredArity; i++) {
      JSType reqFormal = nullAcceptingMeet(
          f1.getFormalType(i), f2.getFormalType(i));
      builder.addReqFormal(reqFormal);
    }
    int maxTotalArity = Math.max(
        f1.requiredFormals.size() + f1.optionalFormals.size(),
        f2.requiredFormals.size() + f2.optionalFormals.size());
    for (int i = maxRequiredArity; i < maxTotalArity; i++) {
      JSType optFormal = nullAcceptingMeet(
          f1.getFormalType(i), f2.getFormalType(i));
      builder.addOptFormal(optFormal);
    }
    if (f1.restFormals != null && f2.restFormals != null) {
      builder.addRestFormals(
          nullAcceptingMeet(f1.restFormals, f2.restFormals));
    }
    builder.addRetType(JSType.join(f1.returnType, f2.returnType));
    return builder.buildFunction();
  }

  FunctionType specialize(FunctionType other) {
    if (other == null ||
        !this.isLoose() && other.isLoose()) {
      return this;
    } else {
      return FunctionType.meet(this, other);
    }
  }

  static FunctionType meet(FunctionType f1, FunctionType f2) {
    if (f1 == null || f2 == null) {
      return null;
    } else if (f2.isTopFunction() || f1.equals(f2)) {
      return f1;
    } else if (f1.isTopFunction()) {
      return f2;
    }

    // War is peace, freedom is slavery, meet is join
    if (f1.isLoose() || f2.isLoose()) {
      return FunctionType.looseJoin(f1, f2);
    }

    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    int minRequiredArity = Math.min(
        f1.requiredFormals.size(), f2.requiredFormals.size());
    for (int i = 0; i < minRequiredArity; i++) {
      builder.addReqFormal(
          JSType.nullAcceptingJoin(f1.getFormalType(i), f2.getFormalType(i)));
    }
    int maxTotalArity = Math.max(
        f1.requiredFormals.size() + f1.optionalFormals.size(),
        f2.requiredFormals.size() + f2.optionalFormals.size());
    for (int i = minRequiredArity; i < maxTotalArity; i++) {
      builder.addOptFormal(JSType.nullAcceptingJoin(
          f1.getFormalType(i), f2.getFormalType(i)));
    }
    if (f1.restFormals != null || f2.restFormals != null) {
      builder.addRestFormals(
          JSType.nullAcceptingJoin(f1.restFormals, f2.restFormals));
    }
    builder.addRetType(JSType.meet(f1.returnType, f2.returnType));
    return builder.buildFunction();
  }

  // We may consider true subtyping for deferred checks when the formal
  // parameter has a loose function type.
  boolean isLooseSubtypeOf(FunctionType f2) {
    Preconditions.checkState(this.isLoose() || f2.isLoose());
    if (this.isTopFunction() || f2.isTopFunction()) {
      return true;
    }

    int maxRequiredArity = Math.max(
        this.requiredFormals.size(), f2.requiredFormals.size());
    for (int i = 0; i < maxRequiredArity; i++) {
      if (JSType.meet(this.getFormalType(i), f2.getFormalType(i)).isBottom()) {
        return false;
      }
    }
    if (!this.getReturnType().isBottom() &&
        !f2.getReturnType().isBottom() &&
        JSType.meet(this.getReturnType(), f2.getReturnType()).isBottom()) {
      return false;
    }
    return true;
  }

  public boolean isGeneric() {
    return typeParameters != null;
  }

  public List<String> getTypeParameters() {
    return typeParameters;
  }

  boolean unifyWith(FunctionType other, List<String> typeParameters,
      Multimap<String, JSType> typeMultimap) {
    Preconditions.checkState(this.nominalType == null);
    Preconditions.checkState(this.typeParameters == null);
    Preconditions.checkState(this.outerVarPreconditions.isEmpty());

    if (requiredFormals.size() != other.requiredFormals.size()) {
      return false;
    }
    Iterator<JSType> thisReqFormals = requiredFormals.iterator();
    Iterator<JSType> otherReqFormals = other.requiredFormals.iterator();
    while (thisReqFormals.hasNext()) {
      JSType reqFormal = thisReqFormals.next();
      JSType otherReqFormal = otherReqFormals.next();
      if (!reqFormal.unifyWith(otherReqFormal, typeParameters, typeMultimap)) {
        return false;
      }
    }

    if (optionalFormals.size() != other.optionalFormals.size()) {
      return false;
    }
    Iterator<JSType> thisOptFormals = optionalFormals.iterator();
    Iterator<JSType> otherOptFormals = other.optionalFormals.iterator();
    while (thisOptFormals.hasNext()) {
      JSType optFormal = thisOptFormals.next();
      JSType otherOptFormal = otherOptFormals.next();
      if (!optFormal.unifyWith(otherOptFormal, typeParameters, typeMultimap)) {
        return false;
      }
    }

    if (restFormals == null && other.restFormals != null ||
        restFormals != null && other.restFormals == null) {
      return false;
    }
    if (restFormals != null && !restFormals.unifyWith(
        other.restFormals, typeParameters, typeMultimap)) {
      return false;
    }
    return returnType.unifyWith(other.returnType, typeParameters, typeMultimap);
  }

  /**
   * @param keepTypeParams See the two cases in substituteGenerics and
   * instantiateGenerics.
   */
  private FunctionType applyInstantiation(
      boolean keepTypeParams, Map<String, JSType> typeMap) {
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    for (JSType reqFormal : requiredFormals) {
      builder.addReqFormal(reqFormal.substituteGenerics(typeMap));
    }
    for (JSType optFormal : optionalFormals) {
      builder.addOptFormal(optFormal.substituteGenerics(typeMap));
    }
    if (restFormals != null) {
      builder.addRestFormals(restFormals.substituteGenerics(typeMap));
    }
    builder.addRetType(returnType.substituteGenerics(typeMap));
    if (isLoose) {
      builder.addLoose();
    }
    if (nominalType != null) {
      builder.addNominalType(nominalType.instantiateGenerics(typeMap));
    }
    // TODO(blickly): Do we need instatiation here?
    for (String var : outerVarPreconditions.keySet()) {
      builder.addOuterVarPrecondition(var, outerVarPreconditions.get(var));
    }
    if (keepTypeParams) {
      builder.addTypeParameters(this.typeParameters);
    }
    return builder.buildFunction();
  }

  /**
   * FunctionType#substituteGenerics is called while instantiating prototype
   * methods of generic nominal types.
   */
  FunctionType substituteGenerics(Map<String, JSType> concreteTypes) {
    Preconditions.checkState(outerVarPreconditions.isEmpty());
    Map<String, JSType> typeMap = concreteTypes;
    if (typeParameters != null) {
      ImmutableMap.Builder<String, JSType> builder = ImmutableMap.builder();
      for (Map.Entry<String, JSType> concreteTypeEntry : concreteTypes.entrySet()) {
        if (!typeParameters.contains(concreteTypeEntry.getKey())) {
          builder.put(concreteTypeEntry.getKey(), concreteTypeEntry.getValue());
        }
      }
      typeMap = builder.build();
    }
    return applyInstantiation(true, typeMap);
  }

  public FunctionType instantiateGenerics(Map<String, JSType> typeMap) {
    for (String typeParam : typeMap.keySet()) {
      Preconditions.checkState(typeParameters.contains(typeParam));
    }
    return applyInstantiation(false, typeMap);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    Preconditions.checkArgument(obj instanceof FunctionType, "obj is: %s", obj);
    FunctionType f2 = (FunctionType) obj;
    return Objects.equal(this.requiredFormals, f2.requiredFormals) &&
        Objects.equal(this.optionalFormals, f2.optionalFormals) &&
        Objects.equal(this.restFormals, f2.restFormals) &&
        Objects.equal(this.returnType, f2.returnType);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        requiredFormals, optionalFormals, restFormals, returnType);
  }

  @Override
  public String toString() {
    return appendTo(new StringBuilder()).toString();
  }

  public StringBuilder appendTo(StringBuilder builder) {
    if (isTopFunction()) {
      if (isLoose) {
        return builder.append("LOOSE_TOP_FUNCTION");
      }
      return builder.append("TOP_FUNCTION");
    }
    builder.append("function(");
    if (nominalType != null) {
      builder.append("new:");
      builder.append(nominalType.getName());
      builder.append(',');
    }
    for (int i = 0; i < requiredFormals.size(); ++i) {
      requiredFormals.get(i).appendTo(builder);
      builder.append(',');
    }
    for (int i = 0; i < optionalFormals.size(); ++i) {
      optionalFormals.get(i).appendTo(builder);
      builder.append("=,");
    }
    if (restFormals != null) {
      builder.append("...");
      restFormals.appendTo(builder);
    }
    // Delete the trailing comma, if present
    if (builder.charAt(builder.length() - 1) == ',') {
      builder.deleteCharAt(builder.length() - 1);
    }
    builder.append(')');
    if (returnType != null) {
      builder.append(':');
      returnType.appendTo(builder);
    }
    if (isLoose) {
      builder.append(" (loose)");
    }
    if (!outerVarPreconditions.isEmpty()) {
      builder.append("\tFV: {");
      boolean firstIteration = true;
      for (Map.Entry<String, JSType> entry : outerVarPreconditions.entrySet()) {
        if (!firstIteration) {
          builder.append(',');
        }
        builder.append(entry.getKey());
        builder.append('=');
        entry.getValue().appendTo(builder);
      }
      builder.append('}');
    }
    return builder;
  }
}
