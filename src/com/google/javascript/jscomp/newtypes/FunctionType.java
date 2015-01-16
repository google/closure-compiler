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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
  final NominalType nominalType;
  // non-null iff this is a prototype method
  private final NominalType receiverType;
  // non-null iff this function has an @template annotation
  private final ImmutableList<String> typeParameters;
  private static final boolean DEBUGGING = false;

  private FunctionType(
      ImmutableList<JSType> requiredFormals,
      ImmutableList<JSType> optionalFormals,
      JSType restFormals,
      JSType retType,
      NominalType nominalType,
      NominalType receiverType,
      ImmutableMap<String, JSType> outerVars,
      ImmutableList<String> typeParameters,
      boolean isLoose) {
    this.requiredFormals = requiredFormals;
    this.optionalFormals = optionalFormals;
    this.restFormals = restFormals;
    this.returnType = retType;
    this.nominalType = nominalType;
    this.receiverType = receiverType;
    this.outerVarPreconditions = outerVars;
    this.typeParameters = typeParameters;
    this.isLoose = isLoose;
    checkValid();
  }

  // Only used to create TOP_FUNCTION and LOOSE_TOP_FUNCTION
  private FunctionType(boolean isLoose) {
    this.requiredFormals = null;
    this.optionalFormals = null;
    this.restFormals = null;
    this.returnType = null;
    this.nominalType = null;
    this.receiverType = null;
    this.outerVarPreconditions = null;
    this.typeParameters = null;
    this.isLoose = isLoose;
  }

  void checkValid() {
    if (isTopFunction() || isQmarkFunction()) {
      return;
    }
    Preconditions.checkNotNull(requiredFormals,
        "null required formals for function: %s", this);
    for (JSType formal : requiredFormals) {
      Preconditions.checkNotNull(formal);
    }
    Preconditions.checkNotNull(requiredFormals,
        "null optional formals for function: %s", this);
    for (JSType formal : optionalFormals) {
      Preconditions.checkNotNull(formal);
    }
    Preconditions.checkNotNull(returnType);
  }

  public boolean isLoose() {
    return isLoose;
  }

  FunctionType withLoose() {
    if (isLoose()) {
      return this;
    }
    if (isTopFunction()) {
      return LOOSE_TOP_FUNCTION;
    }
    return new FunctionType(
        requiredFormals, optionalFormals, restFormals, returnType, nominalType,
        receiverType, outerVarPreconditions, typeParameters, true);
  }

  static FunctionType normalized(
      List<JSType> requiredFormals,
      List<JSType> optionalFormals,
      JSType restFormals,
      JSType retType,
      NominalType nominalType,
      NominalType receiverType,
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
        restFormals, retType, nominalType, receiverType,
        ImmutableMap.copyOf(outerVars),
        typeParameters,
        isLoose);
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
  static final FunctionType TOP_FUNCTION = new FunctionType(false);
  private static final FunctionType LOOSE_TOP_FUNCTION = new FunctionType(true);

  // Corresponds to Function, which is a subtype and supertype of all functions.
  static final FunctionType QMARK_FUNCTION = FunctionType.normalized(null,
      null, JSType.UNKNOWN, JSType.UNKNOWN, null, null, null, null, true);
  private static final FunctionType BOTTOM_FUNCTION = FunctionType.normalized(
      null, null, null, JSType.BOTTOM, null, null, null, null, false);

  public boolean isTopFunction() {
    return this == TOP_FUNCTION || this == LOOSE_TOP_FUNCTION;
  }

  public boolean isConstructor() {
    return nominalType != null && !nominalType.isInterface();
  }

  public boolean isInterfaceDefinition() {
    return nominalType != null && nominalType.isInterface();
  }

  public JSType getSuperPrototype() {
    Preconditions.checkState(isConstructor());
    NominalType superClass = nominalType.getInstantiatedSuperclass();
    return superClass == null ? null : superClass.getPrototype();
  }

  public boolean isQmarkFunction() {
    return this == QMARK_FUNCTION;
  }

  static boolean isInhabitable(FunctionType f) {
    return f != BOTTOM_FUNCTION;
  }

  public boolean hasRestFormals() {
    return restFormals != null;
  }

  // 0-indexed
  // Returns null if argpos indexes past the arguments
  public JSType getFormalType(int argpos) {
    Preconditions.checkArgument(!isTopFunction());
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
    return returnType;
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

  public boolean isRequiredArg(int i) {
    return i < requiredFormals.size();
  }

  public boolean isOptionalArg(int i) {
    return i >= requiredFormals.size()
        && i < requiredFormals.size() + optionalFormals.size();
  }

  public JSType getInstanceTypeOfCtor() {
    if (!isGeneric()) {
      return nominalType.getInstanceAsJSType();
    }
    ImmutableMap.Builder<String, JSType> builder = ImmutableMap.builder();
    for (String typeVar : getTypeParameters()) {
      builder.put(typeVar, JSType.UNKNOWN);
    }
    return instantiateGenerics(builder.build())
        .nominalType.getInstanceAsJSType();
  }

  public JSType getThisType() {
    if (receiverType != null) {
      return receiverType.getInstanceAsJSType();
    }
    if (nominalType != null) {
      return nominalType.getInstanceAsJSType();
    }
    return null;
  }

  public JSType createConstructorObject(NominalType fnNominal) {
    Preconditions.checkState(nominalType != null);
    return NominalType.createConstructorObject(this, fnNominal);
  }

  // Used to get a declared type for an unannotated function that appears in
  // argument position.
  // Should only be used during GlobalTypeInfo.
  public DeclaredFunctionType toDeclaredFunctionType() {
    if (isQmarkFunction()) {
      return FunctionTypeBuilder.qmarkFunctionBuilder().buildDeclaration();
    }
    Preconditions.checkState(!isLoose());
    // Don't do it for generic types.
    if (typeParameters != null) {
      return null;
    }
    // Don't do it for anonymous constructors
    if (nominalType != null) {
      return null;
    }
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    for (JSType type : requiredFormals) {
      builder.addReqFormal(type);
    }
    for (JSType type : optionalFormals) {
      builder.addOptFormal(type);
    }
    builder.addRestFormals(restFormals);
    builder.addRetType(returnType);
    builder.addNominalType(nominalType);
    builder.addReceiverType(receiverType);
    return builder.buildDeclaration();
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

  // TODO(dimvar): we need to clean up the combination of loose functions with
  // new: and/or this: types. Eg, this.nominalType doesn't appear at all.
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
    if (other.isTopFunction() ||
        other.isQmarkFunction() || this.isQmarkFunction()) {
      return true;
    }
    if (isTopFunction()) {
      return false;
    }
    // NOTE(dimvar): We never happen to call isSubtypeOf for loose functions.
    // If some analyzed program changes this, the preconditions check will tell
    // us so we can handle looseness correctly.
    Preconditions.checkState(!isLoose() && !other.isLoose());
    if (this.isGeneric()) {
      if (this.equals(other)) {
        return true;
      }
      // NOTE(dimvar): This is a bug. The code that triggers this should be rare
      // and the fix is not trivial, so for now we decided to not fix.
      // See unit tests in NewTypeInferenceTest#testGenericsSubtyping
      return instantiateGenericsWithUnknown(this).isSubtypeOf(other);
    }

    // The subtype must have an equal or smaller number of required formals
    if (requiredFormals.size() > other.requiredFormals.size()) {
      return false;
    }
    int otherMaxTotalArity =
        other.requiredFormals.size() + other.optionalFormals.size();
    for (int i = 0; i < otherMaxTotalArity; i++) {
      // contravariance in the arguments
      JSType thisFormal = getFormalType(i);
      JSType otherFormal = other.getFormalType(i);
      if (thisFormal != null
          && !thisFormal.isUnknown() && !otherFormal.isUnknown()
          && !otherFormal.isSubtypeOf(thisFormal)) {
        return false;
      }
    }

    if (other.restFormals != null) {
      int thisMaxTotalArity =
          this.requiredFormals.size() + this.optionalFormals.size();
      if (this.restFormals != null) {
        thisMaxTotalArity++;
      }
      for (int i = otherMaxTotalArity; i < thisMaxTotalArity; i++) {
        JSType thisFormal = getFormalType(i);
        JSType otherFormal = other.getFormalType(i);
        if (thisFormal != null
            && !thisFormal.isUnknown() && !otherFormal.isUnknown()
            && !otherFormal.isSubtypeOf(thisFormal)) {
          return false;
        }
      }
    }

    // covariance for the new: type
    if (nominalType == null && other.nominalType != null
        || nominalType != null && other.nominalType == null
        || nominalType != null && other.nominalType != null
           && !nominalType.isSubclassOf(other.nominalType)) {
      return false;
    }

    // covariance for the this: type
    if (receiverType != null && other.receiverType == null
        || receiverType != null && other.receiverType != null
           && !receiverType.isSubclassOf(other.receiverType)) {
      return false;
    }

    // covariance in the return type
    return returnType.isUnknown() || other.returnType.isUnknown()
        || returnType.isSubtypeOf(other.returnType);
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
    builder.addNominalType(
        NominalType.pickSuperclass(f1.nominalType, f2.nominalType));
    builder.addReceiverType(
        NominalType.pickSuperclass(f1.receiverType, f2.receiverType));
    return builder.buildFunction();
  }

  FunctionType specialize(FunctionType other) {
    if (other == null
        || other.isQmarkFunction()
        || !this.isLoose() && other.isLoose()) {
      return this;
    }
    FunctionType result = FunctionType.meet(this, other);
    if (this.isLoose() && !result.isLoose()) {
      result = result.withLoose();
    }
    return result;
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

    if (f1.isGeneric() && f1.isSubtypeOf(f2)) {
      return f1;
    } else if (f2.isGeneric() && f2.isSubtypeOf(f1)) {
      return f2;
    }

    // We lose precision for generic funs that are not in a subtype relation.
    if (f1.isGeneric()) {
      f1 = instantiateGenericsWithUnknown(f1);
    }
    if (f2.isGeneric()) {
      f2 = instantiateGenericsWithUnknown(f2);
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
    JSType retType = JSType.meet(f1.returnType, f2.returnType);
    if (retType.isBottom()) {
      return BOTTOM_FUNCTION;
    }
    builder.addRetType(retType);
    // TODO(dimvar): these two are not correct. We should be picking the
    // greatest lower bound of the types if they are incomparable.
    // Eg, this case arises when an interface extends multiple interfaces.
    // OTOH, it may be enough to detect that during GTI, and not implement the
    // more expensive methods in NominalType.
    builder.addNominalType(
        NominalType.pickSubclass(f1.nominalType, f2.nominalType));
    builder.addReceiverType(
        NominalType.pickSubclass(f1.receiverType, f2.receiverType));
    return builder.buildFunction();
  }

  // We may consider true subtyping for deferred checks when the formal
  // parameter has a loose function type.
  boolean isLooseSubtypeOf(FunctionType f2) {
    Preconditions.checkState(this.isLoose() || f2.isLoose());
    if (this.isTopFunction() || f2.isTopFunction()) {
      return true;
    }
    int minRequiredArity =
        Math.min(this.requiredFormals.size(), f2.requiredFormals.size());
    for (int i = 0; i < minRequiredArity; i++) {
      if (JSType.meet(this.getFormalType(i), f2.getFormalType(i)).isBottom()) {
        return false;
      }
    }
    return this.getReturnType().isBottom() || f2.getReturnType().isBottom()
        || !JSType.meet(this.getReturnType(), f2.getReturnType()).isBottom();
  }

  public boolean isGeneric() {
    return typeParameters != null;
  }

  public List<String> getTypeParameters() {
    return typeParameters;
  }

  boolean unifyWith(FunctionType other, List<String> typeParameters,
      Multimap<String, JSType> typeMultimap) {
    Preconditions.checkState(this.typeParameters == null);
    Preconditions.checkState(this.outerVarPreconditions.isEmpty());

    if (this == LOOSE_TOP_FUNCTION || other == LOOSE_TOP_FUNCTION) {
      return true;
    }

    Preconditions.checkState(
        this.requiredFormals != null && other.requiredFormals != null,
        "Cannot run unification algorithm on %s and %s", this, other);

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

    if (nominalType == null && other.nominalType != null
        || nominalType != null && other.nominalType == null) {
      return false;
    }
    if (nominalType != null && !nominalType.unifyWith(
        other.nominalType, typeParameters, typeMultimap)) {
      return false;
    }

    // If one of the two functions doesn't use THIS in the body, we can still
    // unify.
    if (this.receiverType != null && other.receiverType != null
        && !this.receiverType.unifyWith(
            other.receiverType, typeParameters, typeMultimap)) {
      return false;
    }

    return returnType.unifyWith(other.returnType, typeParameters, typeMultimap);
  }

  private static FunctionType instantiateGenericsWithUnknown(FunctionType f) {
    if (!f.isGeneric()) {
      return f;
    }
    return f.instantiateGenerics(JSType.MAP_TO_UNKNOWN);
  }

  /**
   * Unify the two types symmetrically, given that we have already instantiated
   * the type variables of interest in {@code f1} and {@code f2}, treating
   * JSType.UNKNOWN as a "hole" to be filled.
   * @return The unified type, or null if unification fails
   */
  static FunctionType unifyUnknowns(FunctionType f1, FunctionType f2) {
    Preconditions.checkState(f1 != null || f2 != null);
    if (f1 == null || f2 == null) {
      return null;
    }
    Preconditions.checkArgument(f1.typeParameters == null);
    Preconditions.checkArgument(f2.typeParameters == null);
    Preconditions.checkArgument(f1.outerVarPreconditions.isEmpty());
    Preconditions.checkArgument(f2.outerVarPreconditions.isEmpty());
    if (f1.equals(f2)) {
      return f1;
    }

    ImmutableList<JSType> formals1 = f1.requiredFormals;
    ImmutableList<JSType> formals2 = f2.requiredFormals;
    if (formals1.size() != formals2.size()) {
      return null;
    }
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    int numReqFormals = formals1.size();
    for (int i = 0; i < numReqFormals; i++) {
      JSType t = JSType.unifyUnknowns(formals1.get(i), formals2.get(i));
      if (t == null) {
        return null;
      }
      builder.addReqFormal(t);
    }

    formals1 = f1.optionalFormals;
    formals2 = f2.optionalFormals;
    if (formals1.size() != formals2.size()) {
      return null;
    }
    int numOptFormals = formals1.size();
    for (int i = 0; i < numOptFormals; i++) {
      JSType t = JSType.unifyUnknowns(formals1.get(i), formals2.get(i));
      if (t == null) {
        return null;
      }
      builder.addOptFormal(t);
    }

    if (f1.restFormals == null && f2.restFormals != null
        || f1.restFormals != null && f2.restFormals == null) {
      return null;
    }
    if (f1.restFormals != null) {
      JSType t = JSType.unifyUnknowns(f1.restFormals, f2.restFormals);
      if (t == null) {
        return null;
      }
      builder.addRestFormals(t);
    }

    JSType t = JSType.unifyUnknowns(f1.returnType, f2.returnType);
    if (t == null) {
      return null;
    }
    builder.addRetType(t);

    // Don't unify unknowns in nominal types; it's going to be rare.
    if (!Objects.equals(f1.nominalType, f2.nominalType)) {
      return null;
    }
    builder.addNominalType(f1.nominalType);

    if (!Objects.equals(f1.receiverType, f2.receiverType)) {
      return null;
    }
    builder.addReceiverType(f1.receiverType);

    return builder.buildFunction();
  }

  private FunctionType substituteNominalGenerics(Map<String, JSType> typeMap) {
    if (typeMap.isEmpty()) {
      return this;
    }
    Map<String, JSType> reducedMap = typeMap;
    if (typeParameters != null) {
      boolean foundShadowedTypeParam = false;
      for (String typeParam : typeParameters) {
        if (typeMap.containsKey(typeParam)) {
          foundShadowedTypeParam = true;
          break;
        }
      }
      if (foundShadowedTypeParam) {
        ImmutableMap.Builder<String, JSType> builder = ImmutableMap.builder();
        for (Map.Entry<String, JSType> entry : typeMap.entrySet()) {
          if (!typeParameters.contains(entry.getKey())) {
            builder.put(entry);
          }
        }
        reducedMap = builder.build();
      }
    }
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    for (JSType reqFormal : requiredFormals) {
      builder.addReqFormal(reqFormal.substituteGenerics(reducedMap));
    }
    for (JSType optFormal : optionalFormals) {
      builder.addOptFormal(optFormal.substituteGenerics(reducedMap));
    }
    if (restFormals != null) {
      builder.addRestFormals(restFormals.substituteGenerics(reducedMap));
    }
    builder.addRetType(returnType.substituteGenerics(reducedMap));
    if (isLoose()) {
      builder.addLoose();
    }
    if (nominalType != null) {
      builder.addNominalType(nominalType.instantiateGenerics(typeMap));
    }
    if (receiverType != null) {
      builder.addReceiverType(receiverType.instantiateGenerics(typeMap));
    }
    // TODO(blickly): Do we need instatiation here?
    for (String var : outerVarPreconditions.keySet()) {
      builder.addOuterVarPrecondition(var, outerVarPreconditions.get(var));
    }
    builder.addTypeParameters(this.typeParameters);
    return builder.buildFunction();
  }

  private FunctionType substituteParametricGenerics(
      Map<String, JSType> typeMap) {
    if (typeMap.isEmpty()) {
      return this;
    }
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
    if (isLoose()) {
      builder.addLoose();
    }
    if (nominalType != null) {
      builder.addNominalType(nominalType.instantiateGenerics(typeMap));
    }
    if (receiverType != null) {
      // NOTE(dimvar):
      // We have no way of knowing if receiverType comes from an @this
      // annotation, or because this type represents a method.
      // In case 1, we would want to substitute in receiverType, in case 2 we
      // don't, it's a different scope for the type variables.
      // To properly track this we would need two separate fields instead of
      // just receiverType.
      // Instead, the IF test is a heuristic that works in most cases.
      // In the else branch, we are substituting incorrectly when receiverType
      // comes from a method declaration, but I have not been able to find a
      // test that exposes the bug.
      if (receiverType.isUninstantiatedGenericType()) {
        builder.addReceiverType(receiverType);
      } else {
        builder.addReceiverType(receiverType.instantiateGenerics(typeMap));
      }
    }
    // TODO(blickly): Do we need instatiation here?
    for (String var : outerVarPreconditions.keySet()) {
      builder.addOuterVarPrecondition(var, outerVarPreconditions.get(var));
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
      for (Map.Entry<String, JSType> concreteTypeEntry
               : concreteTypes.entrySet()) {
        if (!typeParameters.contains(concreteTypeEntry.getKey())) {
          builder.put(concreteTypeEntry);
        }
      }
      typeMap = builder.build();
    }
    return substituteNominalGenerics(typeMap);
  }

  public FunctionType instantiateGenerics(Map<String, JSType> typeMap) {
    Preconditions.checkNotNull(typeParameters);
    return substituteParametricGenerics(typeMap);
  }

  public FunctionType instantiateGenericsFromArgumentTypes(
      List<JSType> argTypes) {
    Preconditions.checkNotNull(typeParameters);
    if (argTypes.size() < getMinArity() || argTypes.size() > getMaxArity()) {
      return null;
    }
    Multimap<String, JSType> typeMultimap = HashMultimap.create();
    for (int i = 0, size = argTypes.size(); i < size; i++) {
      if (!this.getFormalType(i)
          .unifyWith(argTypes.get(i), typeParameters, typeMultimap)) {
        return null;
      }
    }
    ImmutableMap.Builder<String, JSType> builder = ImmutableMap.builder();
    for (String typeParam : typeParameters) {
      Collection<JSType> types = typeMultimap.get(typeParam);
      if (types.size() != 1) {
        return null;
      }
      builder.put(typeParam, Iterables.getOnlyElement(types));
    }
    return substituteParametricGenerics(builder.build());
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    Preconditions.checkArgument(obj instanceof FunctionType, "obj is: %s", obj);
    FunctionType f2 = (FunctionType) obj;
    return Objects.equals(this.requiredFormals, f2.requiredFormals)
        && Objects.equals(this.optionalFormals, f2.optionalFormals)
        && Objects.equals(this.restFormals, f2.restFormals)
        && Objects.equals(this.returnType, f2.returnType)
        && Objects.equals(this.nominalType, f2.nominalType)
        && Objects.equals(this.receiverType, f2.receiverType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requiredFormals, optionalFormals, restFormals,
        returnType, nominalType, receiverType);
  }

  @Override
  public String toString() {
    return appendTo(new StringBuilder()).toString();
  }

  public StringBuilder appendTo(StringBuilder builder) {
    if (this == LOOSE_TOP_FUNCTION) {
      return builder.append("LOOSE_TOP_FUNCTION");
    } else if (this == TOP_FUNCTION) {
      return builder.append("TOP_FUNCTION");
    } else if (this == QMARK_FUNCTION) {
      return builder.append("QMARK_FUNCTION");
    }
    builder.append("function(");
    if (nominalType != null) {
      builder.append("new:");
      builder.append(nominalType);
      builder.append(',');
    } else if (receiverType != null) {
      builder.append("this:");
      builder.append(receiverType);
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
    if (isLoose()) {
      builder.append(" (loose)");
    }
    if (DEBUGGING && !outerVarPreconditions.isEmpty()) {
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
