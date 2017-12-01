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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TypeI;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Additional type information for methods and stand-alone functions.
 * Stored as a field in appropriate {@link ObjectType} instances.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class FunctionType implements Serializable {
  private final JSTypes commonTypes;
  private final ImmutableList<JSType> requiredFormals;
  private final ImmutableList<JSType> optionalFormals;
  private final JSType restFormals;
  private final JSType returnType;
  private final boolean isLoose;
  private final boolean isAbstract;
  private final ImmutableMap<String, JSType> outerVarPreconditions;
  // If this FunctionType is a constructor/interface, this field stores the
  // type of the instance.
  private final JSType nominalType;
  // If this FunctionType is a prototype method, this field stores the
  // type of the instance.
  private final JSType receiverType;
  // Set to TypeParameters.EMPTY for a function without a @template annotation.
  // Note that a function type can have type variables as formal parameters and still have empty
  // typeParameters, e.g., a method without @template defined on a generic class.
  private final TypeParameters typeParameters;
  private static final boolean DEBUGGING = false;

  private FunctionType(
      JSTypes commonTypes,
      ImmutableList<JSType> requiredFormals,
      ImmutableList<JSType> optionalFormals,
      JSType restFormals,
      JSType retType,
      JSType nominalType,
      JSType receiverType,
      ImmutableMap<String, JSType> outerVars,
      TypeParameters typeParameters,
      boolean isLoose,
      boolean isAbstract) {
    checkNotNull(commonTypes);
    this.commonTypes = commonTypes;
    this.requiredFormals = requiredFormals;
    this.optionalFormals = optionalFormals;
    this.restFormals = restFormals;
    this.returnType = retType;
    this.nominalType = nominalType;
    this.receiverType = receiverType;
    this.outerVarPreconditions = outerVars;
    this.typeParameters = typeParameters;
    this.isLoose = isLoose;
    this.isAbstract = isAbstract;
    checkValid();
  }

  // This constructor is only used to create TOP_FUNCTION and LOOSE_TOP_FUNCTION.
  // We create only one TOP_FUNCTION and one LOOSE_TOP_FUNCTION, and check
  // for "topness" using reference equality. Most fields of these two types are set to null,
  // and are not allowed to be null for other function types. This is on purpose;
  // we do not want to accidentally make a top function be equals() to some other
  // function type. The return type is unknown for convenience.
  private FunctionType(JSTypes commonTypes, boolean isLoose) {
    checkNotNull(commonTypes);
    this.commonTypes = commonTypes;
    this.requiredFormals = null;
    this.optionalFormals = null;
    this.restFormals = null;
    this.returnType = checkNotNull(this.commonTypes.UNKNOWN);
    this.nominalType = null;
    this.receiverType = null;
    this.outerVarPreconditions = null;
    this.typeParameters = TypeParameters.EMPTY;
    this.isLoose = isLoose;
    this.isAbstract = false;
  }

  /**
   * Checks a number of preconditions on the function, ensuring that it has
   * been properly initialized. This is called automatically by all public
   * factory methods. Only TOP_FUNCTION and LOOSE_TOP_FUNCTION are not
   * validated.
   */
  @SuppressWarnings("ReferenceEquality")
  private void checkValid() {
    if (isTopFunction() || isQmarkFunction()) {
      return;
    }
    Preconditions.checkNotNull(requiredFormals,
        "null required formals for function: %s", this);
    for (JSType formal : requiredFormals) {
      checkNotNull(formal);
      // A loose function has bottom formals in the bwd direction of NTI.
      // See NTI#analyzeLooseCallNodeBwd.
      checkState(isLoose || !formal.isBottom());
    }
    Preconditions.checkNotNull(optionalFormals,
        "null optional formals for function: %s", this);
    for (JSType formal : optionalFormals) {
      checkNotNull(formal);
      checkState(!formal.isBottom());
    }
    checkState(restFormals == null || !restFormals.isBottom());
    checkNotNull(returnType);
    checkNotNull(typeParameters);
  }

  /** Returns the JSTypes instance stored by this object. */
  JSTypes getCommonTypes() {
    return this.commonTypes;
  }

  /**
   * Returns true if this function type is "loose". When an unannotated formal parameter of
   * some function is itself used as a function, we infer a loose function type for it.
   * For example: {@code function f(g) { return g(5) - 2; }}. Here, g is a loose number→number
   * function. A loose function type may have required formal parameters that are bottom.
   */
  public boolean isLoose() {
    return isLoose;
  }

  /**
   * Returns a loose version of this function type, for example, as the result of
   * specializing or joining this function with a "loose top" or "question mark"
   * function (in both of these cases, no formal parameters need to be combined).
   */
  FunctionType withLoose() {
    if (isLoose()) {
      return this;
    }
    if (isTopFunction()) {
      return this.commonTypes.LOOSE_TOP_FUNCTION;
    }
    return new FunctionType(
        this.commonTypes,
        requiredFormals, optionalFormals, restFormals, returnType, nominalType,
        receiverType, outerVarPreconditions, typeParameters, true, isAbstract);
  }

  public boolean isAbstract() {
    return isAbstract;
  }

  public boolean isConstructorOfAbstractClass() {
    return isUniqueConstructor()
        && this.nominalType.getNominalTypeIfSingletonObj().isAbstractClass();
  }

  /**
   * Builds a function type, adjusting its inputs to a "canonical" form if necessary, e.g.,
   * replacing null arguments with empty collections. Optional parameters whose types are equal to
   * the rest type are also removed.
   */
  static FunctionType normalized(
      JSTypes commonTypes,
      List<JSType> requiredFormals,
      List<JSType> optionalFormals,
      JSType restFormals,
      JSType retType,
      JSType nominalType,
      JSType receiverType,
      Map<String, JSType> outerVars,
      TypeParameters typeParameters,
      boolean isLoose,
      boolean isAbstract) {
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
        commonTypes,
        ImmutableList.copyOf(requiredFormals),
        ImmutableList.copyOf(optionalFormals),
        restFormals, retType, nominalType, receiverType,
        ImmutableMap.copyOf(outerVars),
        firstNonNull(typeParameters, TypeParameters.EMPTY),
        isLoose,
        isAbstract);
  }

  /**
   * Called in the beginning of type checking, when the JSTypes object is being initialized.
   * Creates a few basic function types:
   *   TOP_FUNCTION, a supertype of all functions;
   *   LOOSE_TOP_FUNCTION, same as top but loose;
   *   QMARK_FUNCTION, the "any" function; a subtype and supertype of all function types; and
   *   BOTTOM_FUNCTION, a subtype of all functions.
   */
  static Map<String, FunctionType> createInitialFunctionTypes(JSTypes commonTypes) {
    LinkedHashMap<String, FunctionType> functions = new LinkedHashMap<>();
    functions.put(
        "QMARK_FUNCTION",
        FunctionType.normalized(
            commonTypes, null, null, commonTypes.UNKNOWN, commonTypes.UNKNOWN,
            null, null, null, null, true, false));
    functions.put(
        "BOTTOM_FUNCTION",
        FunctionType.normalized(
            commonTypes, null, null, null, commonTypes.BOTTOM, null, null, null, null, false,
            false));
    functions.put("TOP_FUNCTION", new FunctionType(commonTypes, false));
    functions.put("LOOSE_TOP_FUNCTION", new FunctionType(commonTypes, true));
    return functions;
  }

  /**
   * Returns true if this function is either version (loose or not) of the
   * top function (which takes all bottoms and returns top).
   */
  @SuppressWarnings("ReferenceEquality")
  public boolean isTopFunction() {
    return this == this.commonTypes.TOP_FUNCTION || this == this.commonTypes.LOOSE_TOP_FUNCTION;
  }

  /** Null-safe version of {@link JSType#getNominalTypeIfSingletonObj}. */
  private static NominalType getNominalTypeIfSingletonObj(JSType t) {
    return t == null ? null : t.getNominalTypeIfSingletonObj();
  }

  /**
   * Returns true if this type is some sort of constructor, i.e. function(new: T)
   * for any value of T (whether it be a class, interface, type variable, or union
   * thereof). This is a looser version of {@link #isUniqueConstructor} and
   * {@link #isInterfaceDefinition}.
   */
  public boolean isSomeConstructorOrInterface() {
    return this.nominalType != null;
  }

  /**
   * Returns true if this is a constructor for a single class, i.e.
   * function(new: Foo} where Foo is a concrete class.
   */
  public boolean isUniqueConstructor() {
    NominalType nt = getNominalTypeIfSingletonObj(this.nominalType);
    return nt != null && nt.isClass();
  }

  /**
   * Returns true if this is a constructor for a single interface, i.e.
   * function(new: Foo} where Foo is an interface definition.
   */
  public boolean isInterfaceDefinition() {
    NominalType nt = getNominalTypeIfSingletonObj(this.nominalType);
    return nt != null && nt.isInterface();
  }

  /**
   * Returns the prototype object of the superclass, or null if there is
   * no superclass.
   *
   * @throws IllegalStateException if this is not a unique constructor.
   */
  public JSType getSuperPrototype() {
    checkState(isUniqueConstructor());
    NominalType nt = getNominalTypeIfSingletonObj(this.nominalType);
    NominalType superClass = nt.getInstantiatedSuperclass();
    return superClass == null ? null : superClass.getPrototypeObject();
  }

  /**
   * Returns true if this function is the common QMARK_FUNCTION type
   * (function(...?): ?), determined by reference equality.
   */
  @SuppressWarnings("ReferenceEquality")
  public boolean isQmarkFunction() {
    return this == this.commonTypes.QMARK_FUNCTION;
  }

  /**
   * Returns true if there exist any function values that can inhabit the given function type.
   * (In our type system, this is true for all non-bottom function types.)
   */
  @SuppressWarnings("ReferenceEquality")
  static boolean isInhabitable(FunctionType f) {
    return f == null || f != f.commonTypes.BOTTOM_FUNCTION;
  }

  public boolean hasRestFormals() {
    return restFormals != null;
  }

  /**
   * Returns the type of this function's rest parameter.
   *
   * @throws IllegalStateException if there is no rest parameter.
   */
  public JSType getRestFormalsType() {
    checkNotNull(restFormals);
    return restFormals;
  }

  /**
   * Returns the formal parameter in the given (0-indexed) position,
   * or null if the position is past the end of the parameter list.
   */
  public JSType getFormalType(int argpos) {
    if (isTopFunction()) {
      return this.commonTypes.UNKNOWN;
    }
    int numReqFormals = requiredFormals.size();
    if (argpos < numReqFormals) {
      return requiredFormals.get(argpos);
    } else if (argpos < numReqFormals + optionalFormals.size()) {
      return optionalFormals.get(argpos - numReqFormals);
    } else {
      // Note: as requiredFormals and optionalFormals are both ImmutableLists,
      // they can never return null. This is the only codepath that can return
      // null, and only if there is no rest parameter.
      return restFormals;
    }
  }

  public JSType getReturnType() {
    return returnType;
  }

  /**
   * Returns the type of the closed-over variable of the given name,
   * or null if the named variable is not in the closure.
   *
   * @throws IllegalStateException if this is the top function.
   */
  public JSType getOuterVarPrecondition(String name) {
    checkState(!isTopFunction());
    return outerVarPreconditions.get(name);
  }

  /**
   * Returns the minimum number of parameters accepted by this function.
   *
   * @throws IllegalStateException if this is the top function, which has
   *     effectively-infinite minimum arity.
   */
  public int getMinArity() {
    checkState(!isTopFunction());
    return requiredFormals.size();
  }

  /**
   * Returns the maximum number of parameters accepted by this function,
   * or Integer.MAX_VALUE for a function with rest parameters (effectively
   * infinite).
   *
   * @throws IllegalStateException if this is the top function.
   */
  public int getMaxArity() {
    checkArgument(!isTopFunction());
    if (restFormals != null) {
      return Integer.MAX_VALUE; // "Infinite" arity
    } else {
      return requiredFormals.size() + optionalFormals.size();
    }
  }

  /**
   * Returns the maximum number of parameters accepted by this function,
   * not counting rest parameters.
   *
   * @throws IllegalStateException if this is the top function.
   */
  public int getMaxArityWithoutRestFormals() {
    return requiredFormals.size() + optionalFormals.size();
  }

  /** Returns true if the {@code i}th parameter is required. */
  public boolean isRequiredArg(int i) {
    return i < requiredFormals.size();
  }

  /** Returns true if the {@code i}th parameter is an optional parameter. */
  public boolean isOptionalArg(int i) {
    return i >= requiredFormals.size()
        && i < requiredFormals.size() + optionalFormals.size();
  }

  /**
   * If this type is a constructor (e.g. function(new: Foo)), returns the
   * type Foo. If the nominal type is generic, then type parameters are
   * set to unknown. Returns null for non-constructors.
   */
  public JSType getInstanceTypeOfCtor() {
    if (!isGeneric()) {
      return this.nominalType;
    }
    NominalType nominal = getNominalTypeIfSingletonObj(this.nominalType);
    return nominal == null
        ? null
        : nominal.substituteGenerics(this.commonTypes.MAP_TO_UNKNOWN).getInstanceAsJSType();
  }

  /**
   * If this type is a constructor, this method returns the prototype object of the
   * new instances.
   */
  JSType getPrototypeOfNewInstances() {
    checkState(isSomeConstructorOrInterface());
    return this.nominalType.getPrototypeObject();
  }

  /**
   * Returns the 'this' type of the function, or the 'new' type for a
   * constructor.
   */
  public JSType getThisType() {
    return this.receiverType != null ? this.receiverType : this.nominalType;
  }

  /**
   * Say a method f is defined on a generic type {@code Foo<T>}.
   * When doing Foo.prototype.f.call (or also .apply), we transform the method type to a
   * function F, which includes the type variables of Foo, in this case T.
   */
  private FunctionTypeBuilder transformCallApplyHelper() {
    FunctionTypeBuilder builder = new FunctionTypeBuilder(this.commonTypes);
    if (this.receiverType == null) {
      builder.addReqFormal(this.commonTypes.UNKNOWN);
      return builder;
    }
    NominalType nt = this.receiverType.getNominalTypeIfSingletonObj();
    if (nt != null && nt.isUninstantiatedGenericType()) {
      builder.addTypeParameters(TypeParameters.make(nt.getTypeParameters()));
      NominalType ntWithIdentity = nt.instantiateGenericsWithIdentity();
      builder.addReqFormal(JSType.fromObjectType(ObjectType.fromNominalType(ntWithIdentity)));
    } else {
      builder.addReqFormal(this.receiverType);
    }
    return builder;
  }

  /**
   * Returns a FunctionType representing the 'call' property of this
   * function (i.e. the receiver type is prepended to the parameter list).
   */
  public FunctionType transformByCallProperty() {
    if (isTopFunction() || isQmarkFunction() || isLoose) {
      return this.commonTypes.QMARK_FUNCTION;
    }
    FunctionTypeBuilder builder = transformCallApplyHelper();
    for (JSType type : this.requiredFormals) {
      builder.addReqFormal(type);
    }
    for (JSType type : this.optionalFormals) {
      builder.addOptFormal(type);
    }
    builder.addRestFormals(this.restFormals);
    builder.addRetType(this.returnType);
    builder.appendTypeParameters(this.typeParameters);
    builder.addAbstract(this.isAbstract);
    return builder.buildFunction();
  }

  /**
   * Returns a FunctionType representing the 'apply' property of this
   * function. In most cases, only the receiver type is checked, since
   * heterogeneous parameter types are not representable without tuples
   * in the type system (or other special handling). If the only
   * parameter is a rest parameter, then the array argument is typed
   * correctly.
   */
  public FunctionType transformByApplyProperty() {
    if (isTopFunction() || isQmarkFunction() || isLoose) {
      return this.commonTypes.QMARK_FUNCTION;
    }
    if (isGeneric()) {
      return instantiateGenericsWithUnknown().transformByApplyProperty();
    }
    FunctionTypeBuilder builder = transformCallApplyHelper();
    JSType arrayContents;
    if (getMaxArityWithoutRestFormals() == 0 && hasRestFormals()) {
      arrayContents = getRestFormalsType();
    } else {
      arrayContents = this.commonTypes.UNKNOWN;
    }
    JSType varargsArray = this.commonTypes.getIArrayLikeInstance(arrayContents);
    builder.addOptFormal(JSType.join(this.commonTypes.NULL, varargsArray));
    builder.addRetType(this.returnType);
    builder.addAbstract(this.isAbstract);
    return builder.buildFunction();
  }

  /**
   * Returns this function type as a {@link DeclaredFunctionType}. While
   * DeclaredFunctionType allows incomplete types (i.e. null formals or
   * returns), this function always returns a complete type. This should
   * only be called from GlobalTypeInfo.
   *
   * @throws IllegalStateException if this function is loose.
   */
  public DeclaredFunctionType toDeclaredFunctionType() {
    if (isQmarkFunction()) {
      return DeclaredFunctionType.qmarkFunctionDeclaration(this.commonTypes);
    }
    Preconditions.checkState(!isLoose(), "Loose function: %s", this);
    FunctionTypeBuilder builder = new FunctionTypeBuilder(this.commonTypes);
    if (isGeneric()) {
      builder.addTypeParameters(this.typeParameters);
    }
    for (JSType type : this.requiredFormals) {
      builder.addReqFormal(type);
    }
    for (JSType type : this.optionalFormals) {
      builder.addOptFormal(type);
    }
    builder.addRestFormals(this.restFormals);
    builder.addRetType(this.returnType);
    builder.addNominalType(this.nominalType);
    builder.addReceiverType(this.receiverType);
    builder.addAbstract(this.isAbstract);
    return builder.buildDeclaration();
  }

  /**
   * Null-safe version of {@link JSType#meet}, treating nulls as top.
   * Returns null if both inputs are null, or if the result would be
   * bottom.
   */
  private static JSType nullAcceptingMeet(JSType t1, JSType t2) {
    if (t1 == null) {
      return t2;
    }
    if (t2 == null) {
      return t1;
    }
    JSType tmp = JSType.meet(t1, t2);
    return tmp.isBottom() ? null : tmp;
  }

  /**
   * Merges a loose function type with another function. This is strictly
   * neither a join nor a meet, but instead joins both the formals and
   * the return. This behavior is appropriate since loose functions are
   * the result of inferring facts about an unannotated function type,
   * so seeing it accept or return a particular type only allows adding
   * types to the union of types alread seen in that position.
   */
  // TODO(dimvar): we need to clean up the combination of loose functions with
  // new: and/or this: types. Eg, this.nominalType doesn't appear at all.
  private static FunctionType looseMerge(FunctionType f1, FunctionType f2) {
    checkArgument(f1.isLoose() || f2.isLoose());

    FunctionTypeBuilder builder = new FunctionTypeBuilder(f1.commonTypes);
    int minRequiredArity = Math.min(f1.getMinArity(), f2.getMinArity());
    for (int i = 0; i < minRequiredArity; i++) {
      builder.addReqFormal(JSType.nullAcceptingJoin(
          f1.getFormalType(i), f2.getFormalType(i)));
    }
    int maxTotalArity = Math.max(
        f1.requiredFormals.size() + f1.optionalFormals.size(),
        f2.requiredFormals.size() + f2.optionalFormals.size());
    for (int i = minRequiredArity; i < maxTotalArity; i++) {
      JSType t = JSType.nullAcceptingJoin(f1.getFormalType(i), f2.getFormalType(i));
      if (t != null && t.isBottom()) {
        // We will add the optional formal of the loose function in the fwd
        // direction, when we have better type information.
        break;
      }
      builder.addOptFormal(t);
    }
    // Loose types never have varargs, because there is no way for that
    // information to make it to a function summary
    return builder.addRetType(
            JSType.nullAcceptingJoin(f1.returnType, f2.returnType))
        .addLoose().buildFunction();
  }

  /**
   * Returns true if this function is a valid override of other.
   * Specifically, this requires that this function be a subtype of other.
   */
  public boolean isValidOverride(FunctionType other) {
    // Note: SubtypeCache.create() is cheap, since the data structure is persistent.
    // The cache is used to handle cycles in types' dependencies.
    return isSubtypeOfHelper(other, true, SubtypeCache.create(), null);
  }

  // We want to warn about argument mismatch, so we don't consider a function
  // with N required arguments to have restFormals of type TOP.
  // But we allow joins (eg after an IF) to change arity, eg,
  // number->number ∨ number,number->number = number,number->number

  /** Returns true if this function is a subtype of {@code other}. */
  boolean isSubtypeOf(FunctionType other, SubtypeCache subSuperMap) {
    return isSubtypeOfHelper(other, false, subSuperMap, null);
  }

  /**
   * Fills boxedInfo with the reason why f1 is not a subtype of f2,
   * for the purpose of building informative error messages.
   */
  static void whyNotSubtypeOf(FunctionType f1, FunctionType f2,
      SubtypeCache subSuperMap, MismatchInfo[] boxedInfo) {
    checkArgument(boxedInfo.length == 1);
    f1.isSubtypeOfHelper(f2, false, subSuperMap, boxedInfo);
  }

  /**
   * Returns true if this function's formal parameter list is exactly
   * {@code ...?}. This notation has a special meaning: it is NOT a
   * variable-arity function with arguments of ? type; it means that
   * we should skip type-checking the arguments. We can therefore use
   * it to represent, for example, a constructor of Foos with whatever
   * arguments.
   */
  public boolean acceptsAnyArguments() {
    return this.requiredFormals.isEmpty() && this.optionalFormals.isEmpty()
        && this.restFormals != null && this.restFormals.isUnknown();
  }

  /**
   * Recursively checks that this is a subtype of other: this's parameter
   * types are supertypes of other's corresponding parameters (contravariant),
   * and this's return type is a subtype of other's return type (covariant).
   * Additionally, any 'new' type must be covariant.
   * A cache is used to resolve cycles, and details about some mismatched types
   * are written to boxedInfo[0].
   */
  private boolean isSubtypeOfHelper(FunctionType other, boolean isMethodOverrideCheck,
      SubtypeCache subSuperMap, MismatchInfo[] boxedInfo) {
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
    checkState(!isLoose() && !other.isLoose());
    if (this.isGeneric()) {
      if (this.equals(other)) {
        return true;
      }
      // NOTE(dimvar): This is a bug. The code that triggers this should be rare
      // and the fix is not trivial, so for now we decided to not fix.
      // See unit tests in NewTypeInferenceTest#testGenericsSubtyping
      return instantiateGenericsWithUnknown()
          .isSubtypeOfHelper(other, isMethodOverrideCheck, subSuperMap, boxedInfo);
    }

    if (!other.acceptsAnyArguments()) {
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
            && !otherFormal.isSubtypeOf(thisFormal, subSuperMap)) {
          if (boxedInfo != null) {
            boxedInfo[0] =
                MismatchInfo.makeArgTypeMismatch(i, otherFormal, thisFormal);
          }
          return false;
        }
      }

      if (other.restFormals != null) {
        int thisMaxTotalArity = this.requiredFormals.size() + this.optionalFormals.size();
        if (this.restFormals != null) {
          thisMaxTotalArity++;
        }
        for (int i = otherMaxTotalArity; i < thisMaxTotalArity; i++) {
          JSType thisFormal = getFormalType(i);
          JSType otherFormal = other.getFormalType(i);
          if (thisFormal != null
              && !thisFormal.isUnknown() && !otherFormal.isUnknown()
              && !otherFormal.isSubtypeOf(thisFormal, subSuperMap)) {
            return false;
          }
        }
      }
    }

    // covariance for the new: type
    if ((this.nominalType == null && other.nominalType != null)
        || (this.nominalType != null && other.nominalType != null
            && !this.nominalType.isSubtypeOf(other.nominalType, subSuperMap))) {
      return false;
    }

    // A function without @this can be a subtype of a function with @this.
    if (!this.commonTypes.allowMethodsAsFunctions
        // For method overrides, we allow a function with @this to be a subtype of a function
        // without @this, but we don't allow it for general function subtyping.
        && !isMethodOverrideCheck
        && this.receiverType != null
        && other.receiverType == null) {
      return false;
    }
    if (this.receiverType != null && other.receiverType != null
        // Checking of @this is unfortunately loose, because it covers two different cases.
        // 1) When a method overrides another method from a supertype, we only require
        //    that the new @this meets with the @this from the supertype, e.g., see the typing
        //    of toString in the externs. This is unsafe, but that's how we have typed it.
        // 2) When checking for subtyping of two arbitrary function types, the correct semantics
        //    would be to check @this in a contravariant way, but we allow looseness in order to
        //    handle cases like using a Foo that overrides toString as an IObject<?,?>.
        // It would be possible to add special-case code here and in ObjectType#isSubtypeOf
        // to allow us to be loose for #1 but stricter for #2, but it's awkward and doesn't seem
        // worth doing.
        && !JSType.haveCommonSubtype(this.receiverType, other.receiverType)) {
      return false;
    }

    // covariance in the return type
    boolean areRetTypesSubtypes = this.returnType.isUnknown()
        || other.returnType.isUnknown()
        || this.returnType.isSubtypeOf(other.returnType, subSuperMap);
    if (boxedInfo != null) {
      boxedInfo[0] =
          MismatchInfo.makeRetTypeMismatch(other.returnType, this.returnType);
    }
    return areRetTypesSubtypes;
  }

  /**
   * Returns the join of two nominal types. Optimized to avoid using
   * {@link JSType#join} if possible to prevent creating new types.
   * Null arguments (and returns) are treated as top. This is called
   * by {@link #join} and {@link #meet}.
   */
  private static JSType joinNominalTypes(JSType nt1, JSType nt2) {
    if (nt1 == null || nt2 == null) {
      return null;
    }
    NominalType n1 = getNominalTypeIfSingletonObj(nt1);
    NominalType n2 = getNominalTypeIfSingletonObj(nt2);
    if (n1 != null && n2 != null) {
      NominalType tmp = NominalType.join(n1, n2);
      if (tmp != null) {
        return tmp.getInstanceAsJSType();
      }
    }
    // One of the nominal types is non-standard; can't avoid the join
    return JSType.join(nt1, nt2);
  }

  /**
   * Returns the meet of two nominal types. Optimized to avoid using
   * {@link JSType#meet} if possible to prevent creating new types.
   * Null arguments (and returns) are treated as top. This is called
   * by {@link #join} and {@link #meet}.
   */
  private static JSType meetNominalTypes(JSType nt1, JSType nt2) {
    if (nt1 == null) {
      return nt2;
    }
    if (nt2 == null) {
      return nt1;
    }
    NominalType n1 = getNominalTypeIfSingletonObj(nt1);
    NominalType n2 = getNominalTypeIfSingletonObj(nt2);
    if (n1 != null && n2 != null) {
      NominalType tmp = NominalType.pickSubclass(n1, n2);
      return tmp == null ? null : tmp.getInstanceAsJSType();
    }
    // One of the nominal types is non-standard; can't avoid the meet
    return JSType.meet(nt1, nt2);
  }

  /**
   * Returns the join (union) of two function types. The return type is
   * the join of the input return types. The formal parameters are the
   * meets of the input functions' parameters (where required ∧ optional
   * is required). Generic functions will lose precision if they are
   * not in a direct subclass relationship. If any parameter meets to
   * bottom, then BOTTOM_FUNCTION will be returned. Nominal ("new")
   * types are joined (they are in "output" position), while receiver
   * ("this") types are intersected (they are in "input" position).
   */
  static FunctionType join(FunctionType f1, FunctionType f2) {
    if (f1 == null) {
      return f2;
    } else if (f2 == null || f1.equals(f2)) {
      return f1;
    } else if (f1.isQmarkFunction() || f2.isQmarkFunction()) {
      return f1.commonTypes.QMARK_FUNCTION;
    } else if (f1.isTopFunction() || f2.isTopFunction()) {
      return f1.commonTypes.TOP_FUNCTION;
    }

    if (f1.isLoose() || f2.isLoose()) {
      return looseMerge(f1, f2);
    }

    if (f1.isGeneric() && f2.isSubtypeOf(f1, SubtypeCache.create())) {
      return f1;
    } else if (f2.isGeneric() && f1.isSubtypeOf(f2, SubtypeCache.create())) {
      return f2;
    }

    // We lose precision for generic funs that are not in a subtype relation.
    if (f1.isGeneric()) {
      f1 = f1.instantiateGenericsWithUnknown();
    }
    if (f2.isGeneric()) {
      f2 = f2.instantiateGenericsWithUnknown();
    }

    JSTypes commonTypes = f1.commonTypes;
    FunctionTypeBuilder builder = new FunctionTypeBuilder(commonTypes);
    int maxRequiredArity = Math.max(
        f1.requiredFormals.size(), f2.requiredFormals.size());
    for (int i = 0; i < maxRequiredArity; i++) {
      JSType reqFormal = nullAcceptingMeet(f1.getFormalType(i), f2.getFormalType(i));
      if (reqFormal == null) {
        return commonTypes.BOTTOM_FUNCTION;
      }
      builder.addReqFormal(reqFormal);
    }
    int maxTotalArity = Math.max(
        f1.requiredFormals.size() + f1.optionalFormals.size(),
        f2.requiredFormals.size() + f2.optionalFormals.size());
    for (int i = maxRequiredArity; i < maxTotalArity; i++) {
      JSType optFormal = nullAcceptingMeet(f1.getFormalType(i), f2.getFormalType(i));
      if (optFormal == null) {
        return commonTypes.BOTTOM_FUNCTION;
      }
      builder.addOptFormal(optFormal);
    }
    if (f1.restFormals != null && f2.restFormals != null) {
      JSType newRestFormals = nullAcceptingMeet(f1.restFormals, f2.restFormals);
      if (newRestFormals == null) {
        return commonTypes.BOTTOM_FUNCTION;
      }
      builder.addRestFormals(newRestFormals);
    }
    builder.addRetType(JSType.join(f1.returnType, f2.returnType));
    builder.addNominalType(joinNominalTypes(f1.nominalType, f2.nominalType));
    builder.addReceiverType(meetNominalTypes(f1.receiverType, f2.receiverType));
    return builder.buildFunction();
  }

  /**
   * Specializes this function with the {@code other} function type. It's often
   * a no-op, because when this type is not loose, no specialization is required.
   */
  FunctionType specialize(FunctionType other) {
    if (other == null
        || other.isQmarkFunction() || other.isTopFunction() || equals(other)
        || !isLoose()) {
      return this;
    }
    return isTopFunction() || isQmarkFunction()
        ? other.withLoose() : looseMerge(this, other);
  }

  /**
   * Returns the meet (intersect) of two function types. The return
   * type is the meet of the input returns. The formal parameters
   * are the joins of the input functions' parameters (where required
   * ∨ optional is optional). Generic functions will lose precision
   * if they are not in a direct subclass relationship. If any
   * parameter or return results in bottom, then BOTTOM_FUNCTION will
   * be returned. Nominal ("new") types are intersected, while
   * receiver ("this") types are joined.
   */
  static FunctionType meet(FunctionType f1, FunctionType f2) {
    if (f1 == null || f2 == null) {
      return null;
    } else if (f2.isTopFunction() || f1.equals(f2)) {
      return f1;
    } else if (f1.isTopFunction()) {
      return f2;
    }

    if (f1.isLoose() || f2.isLoose()) {
      return looseMerge(f1, f2);
    }

    if (f1.isGeneric() && f1.isSubtypeOf(f2, SubtypeCache.create())) {
      return f1;
    } else if (f2.isGeneric() && f2.isSubtypeOf(f1, SubtypeCache.create())) {
      return f2;
    }

    // We lose precision for generic funs that are not in a subtype relation.
    if (f1.isGeneric()) {
      f1 = f1.instantiateGenericsWithUnknown();
    }
    if (f2.isGeneric()) {
      f2 = f2.instantiateGenericsWithUnknown();
    }

    JSTypes commonTypes = f1.commonTypes;
    FunctionTypeBuilder builder = new FunctionTypeBuilder(commonTypes);
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
      JSType optFormalType =
          JSType.nullAcceptingJoin(f1.getFormalType(i), f2.getFormalType(i));
      if (optFormalType.isBottom()) {
        return commonTypes.BOTTOM_FUNCTION;
      }
      builder.addOptFormal(optFormalType);
    }
    if (f1.restFormals != null || f2.restFormals != null) {
      JSType restFormalsType = JSType.nullAcceptingJoin(f1.restFormals, f2.restFormals);
      if (restFormalsType.isBottom()) {
        return commonTypes.BOTTOM_FUNCTION;
      }
      builder.addRestFormals(restFormalsType);
    }
    JSType retType = JSType.meet(f1.returnType, f2.returnType);
    if (retType.isBottom()) {
      return commonTypes.BOTTOM_FUNCTION;
    }
    builder.addRetType(retType);
    // NOTE(dimvar): these two are not correct. We should be picking the
    // greatest lower bound of the types if they are incomparable.
    // Eg, this case arises when an interface extends multiple interfaces.
    // OTOH, it may be enough to detect that during GTI, and not implement the
    // more expensive methods (in NominalType or ObjectType).
    builder.addNominalType(meetNominalTypes(f1.nominalType, f2.nominalType));
    builder.addReceiverType(joinNominalTypes(f1.receiverType, f2.receiverType));
    return builder.buildFunction();
  }

  /**
   * Returns true if this function or {@code f2} is a possibly subtype of the
   * other. This requires that all required parameters and the return type
   * share a common subtype.
   *
   * <p>We may consider true subtyping for deferred checks when the formal
   * parameter has a loose function type.
   *
   * @throws IllegalStateException if neither function is loose.
   */
  // TODO(sdh): make this method static to emphasize parameter symmetry.
  boolean isLooseSubtypeOf(FunctionType f2) {
    checkState(this.isLoose() || f2.isLoose());
    if (this.isTopFunction() || f2.isTopFunction()) {
      return true;
    }
    int minRequiredArity =
        Math.min(this.requiredFormals.size(), f2.requiredFormals.size());
    for (int i = 0; i < minRequiredArity; i++) {
      if (!JSType.haveCommonSubtype(this.getFormalType(i), f2.getFormalType(i))) {
        return false;
      }
    }
    return JSType.haveCommonSubtype(this.getReturnType(), f2.getReturnType());
  }

  /**
   * Returns true if this function has any non-instantiated type parameters.
   * (Note: if this type is the result of a generic function type that has been
   * instantiated, the type parameters have already been substituted away.)
   */
  public boolean isGeneric() {
    return !this.typeParameters.isEmpty();
  }

  /**
   * Returns all the non-instantiated type parameter names. (Note: if this type is the result of a
   * generic function type that has been instantiated, the type parameters have already been
   * substituted away.)
   */
  public ImmutableList<String> getTypeParameters() {
    return this.typeParameters.asList();
  }

  /** Always returns a non-null map. */
  public ImmutableMap<String, Node> getTypeTransformations() {
    return this.typeParameters.getTypeTransformations();
  }

  /**
   * Returns a list of parameter types. Any rest parameter type is
   * added once at the end of the list.
   */
  List<TypeI> getParameterTypes() {
    int howmanyTypes = getMaxArityWithoutRestFormals() + (hasRestFormals() ? 1 : 0);
    ArrayList<TypeI> types = new ArrayList<>(howmanyTypes);
    types.addAll(this.requiredFormals);
    types.addAll(this.optionalFormals);
    if (hasRestFormals()) {
      types.add(this.restFormals);
    }
    return types;
  }

  /**
   * Unifies this function type, which may contain free type variables, with other,
   * a concrete subtype, modifying the supplied typeMultimap to add any new template
   * variable type bindings. Returns true if the unification succeeded.
   *
   * <p>Note that "generic" is not the same as "contains free type variables":
   * the former is a type with a "for all" quantifier, while the latter is a
   * component nested within a generic type. For example, in the function
   * (forall T. (T → T) → T), the outer function is generic, while the
   * inner (T → T) is a non-generic function with a free type variable. This
   * function is called to unify the inner type in order to determine a concrete
   * instantiation for the outer type.
   *
   * @throws IllegalStateException if this function is generic, is TOP_FUNCTION,
   *     or is LOOSE_TOP_FUNCTION with any closed-over variables.
   */
  @SuppressWarnings("ReferenceEquality")
  boolean unifyWithSubtype(FunctionType other, List<String> typeParameters,
      Multimap<String, JSType> typeMultimap, SubtypeCache subSuperMap) {
    Preconditions.checkState(this.typeParameters.isEmpty(),
        "Non-empty type parameters %s", this.typeParameters);
    checkState(this == this.commonTypes.LOOSE_TOP_FUNCTION || this.outerVarPreconditions.isEmpty());
    checkState(this != this.commonTypes.TOP_FUNCTION);

    if (this == this.commonTypes.LOOSE_TOP_FUNCTION || other.isTopFunction() || other.isLoose()) {
      return true;
    }
    if (other.isGeneric()) {
      other = other.instantiateGenericsWithUnknown();
    }
    if (!acceptsAnyArguments()) {
      if (other.requiredFormals.size() > this.requiredFormals.size()) {
        return false;
      }
      int maxNonInfiniteArity = getMaxArityWithoutRestFormals();
      for (int i = 0; i < maxNonInfiniteArity; i++) {
        JSType thisFormal = getFormalType(i);
        JSType otherFormal = other.getFormalType(i);
        // NOTE(dimvar): The correct handling here would be to implement
        // unifyWithSupertype for JSType, ObjectType, etc, to handle the
        // contravariance here.
        // But it's probably an overkill to do, so instead we just do a subtype
        // check if unification fails. Same for restFormals and receiverType.
        if (otherFormal != null
            && !thisFormal.unifyWithSubtype(
                otherFormal, typeParameters, typeMultimap, subSuperMap)
            && !thisFormal.substituteGenericsWithUnknown().isSubtypeOf(
                otherFormal, SubtypeCache.create())) {
          return false;
        }
      }
      if (this.restFormals != null) {
        JSType otherRestFormals = other.getFormalType(maxNonInfiniteArity);
        if (otherRestFormals != null
            && !this.restFormals.unifyWithSubtype(
                otherRestFormals, typeParameters, typeMultimap, subSuperMap)
            && !this.restFormals.substituteGenericsWithUnknown().isSubtypeOf(
                otherRestFormals, SubtypeCache.create())) {
          return false;
        }
      }
    }

    if ((nominalType == null && other.nominalType != null)
        || (nominalType != null && other.nominalType == null)) {
      return false;
    }
    if (nominalType != null
        && !nominalType.unifyWithSubtype(
            other.nominalType, typeParameters, typeMultimap, subSuperMap)) {
      return false;
    }

    // If one of the two functions doesn't use THIS in the body, we can still unify.
    if (this.receiverType != null && other.receiverType != null
        && !this.receiverType.unifyWithSubtype(
            other.receiverType, typeParameters, typeMultimap, subSuperMap)
        && !this.receiverType.substituteGenericsWithUnknown().isSubtypeOf(
            other.receiverType, SubtypeCache.create())) {
      return false;
    }

    return this.returnType.unifyWithSubtype(
        other.returnType, typeParameters, typeMultimap, subSuperMap);
  }

  public FunctionType instantiateGenericsWithUnknown() {
    if (!isGeneric()) {
      return this;
    }
    return instantiateGenerics(this.commonTypes.MAP_TO_UNKNOWN);
  }

  /**
   * Unify the two types symmetrically, given that we have already instantiated
   * the type variables of interest in {@code f1} and {@code f2}, treating
   * JSType.UNKNOWN as a "hole" to be filled.
   * @return The unified type, or null if unification fails
   */
  static FunctionType unifyUnknowns(FunctionType f1, FunctionType f2) {
    checkState(f1 != null || f2 != null);
    if (f1 == null || f2 == null) {
      return null;
    }
    if (!f1.typeParameters.isEmpty()) {
      f1 = f1.instantiateGenericsWithUnknown();
    }
    if (!f2.typeParameters.isEmpty()) {
      f2 = f2.instantiateGenericsWithUnknown();
    }
    checkState(!f1.isLoose() && !f2.isLoose());
    if (f1.equals(f2)) {
      return f1;
    }

    ImmutableList<JSType> formals1 = f1.requiredFormals;
    ImmutableList<JSType> formals2 = f2.requiredFormals;
    if (formals1.size() != formals2.size()) {
      return null;
    }
    FunctionTypeBuilder builder = new FunctionTypeBuilder(f1.commonTypes);
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

    if ((f1.restFormals == null && f2.restFormals != null)
        || (f1.restFormals != null && f2.restFormals == null)) {
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

  /**
   * Returns a version of t with generics substituted from typeMap. This is a more efficient
   * alternative to {@link JSType#substituteGenerics} in the case of singleton objects,
   * since it can avoid creating new types.
   *
   * <p>TODO(sdh): is there a reason not to build this optimization directly
   * into JSType#substituteGenerics?
   */
  private static JSType substGenericsInNomType(JSType t, Map<String, JSType> typeMap) {
    if (t == null) {
      return null;
    }
    NominalType nt = t.getNominalTypeIfSingletonObj();
    if (nt == null) {
      return t.substituteGenerics(typeMap);
    }
    if (!nt.isGeneric()) {
      return nt.getInstanceAsJSType();
    }
    if (typeMap.isEmpty()) {
      return t;
    }
    return JSType.fromObjectType(ObjectType.fromNominalType(nt.substituteGenerics(typeMap)));
  }

  /**
   * Returns a FunctionType with free type variables substituted using typeMap.
   * There must be no overlap between this function's generic type parameters
   * and the types in the map (i.e. this is not instantiating a generic
   * function: that's done in {@link #instantiateGenerics}).
   * @throws IllegalStateException if typeMap's keys overlap with type parameters.
   */
  private FunctionType substituteNominalGenerics(Map<String, JSType> typeMap) {
    if (typeMap.isEmpty() || this.isTopFunction()) {
      return this;
    }
    if (!this.commonTypes.MAP_TO_UNKNOWN.equals(typeMap)) {
      // Before we switched to unique generated names for type variables, a method's type variables
      // could shadow type variables defined on the class. Check that this no longer happens.
      for (String typeParam : this.typeParameters.asList()) {
        checkState(!typeMap.containsKey(typeParam));
      }
    }
    FunctionTypeBuilder builder = new FunctionTypeBuilder(this.commonTypes);
    for (JSType reqFormal : this.requiredFormals) {
      builder.addReqFormal(reqFormal.substituteGenerics(typeMap));
    }
    for (JSType optFormal : this.optionalFormals) {
      builder.addOptFormal(optFormal.substituteGenerics(typeMap));
    }
    if (this.restFormals != null) {
      builder.addRestFormals(restFormals.substituteGenerics(typeMap));
    }
    builder.addRetType(this.returnType.substituteGenerics(typeMap));
    if (isLoose()) {
      builder.addLoose();
    }
    builder.addNominalType(substGenericsInNomType(this.nominalType, typeMap));
    builder.addReceiverType(substGenericsInNomType(this.receiverType, typeMap));
    // TODO(blickly): Do we need instantiation here?
    for (String var : this.outerVarPreconditions.keySet()) {
      builder.addOuterVarPrecondition(var, this.outerVarPreconditions.get(var));
    }
    builder.addTypeParameters(this.typeParameters);
    return builder.buildFunction();
  }

  /**
   * FunctionType#substituteGenerics is called while instantiating prototype
   * methods of generic nominal types.
   */
  FunctionType substituteGenerics(Map<String, JSType> concreteTypes) {
    if (!isGeneric() || this.commonTypes.MAP_TO_UNKNOWN.equals(concreteTypes)) {
      return substituteNominalGenerics(concreteTypes);
    }
    ImmutableMap.Builder<String, JSType> builder = ImmutableMap.builder();
    for (Map.Entry<String, JSType> concreteTypeEntry : concreteTypes.entrySet()) {
      if (!typeParameters.contains(concreteTypeEntry.getKey())) {
        builder.put(concreteTypeEntry);
      }
    }
    return substituteNominalGenerics(builder.build());
  }

  /**
   * Returns a FunctionType with generic type parameters instantiated as
   * concrete types using typeMap. This is an orthogonal operation to
   * {@link #substituteNominalGenerics}, which operates on the free type
   * variables found in formal parameters and returns.
   */
  public FunctionType instantiateGenerics(Map<String, JSType> typeMap) {
    checkState(isGeneric());
    if (typeMap.isEmpty()) {
      return this;
    }
    FunctionTypeBuilder builder = new FunctionTypeBuilder(this.commonTypes);
    for (JSType reqFormal : this.requiredFormals) {
      builder.addReqFormal(reqFormal.substituteGenerics(typeMap));
    }
    for (JSType optFormal : this.optionalFormals) {
      builder.addOptFormal(optFormal.substituteGenerics(typeMap));
    }
    if (this.restFormals != null) {
      builder.addRestFormals(restFormals.substituteGenerics(typeMap));
    }
    builder.addRetType(this.returnType.substituteGenerics(typeMap));
    if (isLoose()) {
      builder.addLoose();
    }
    builder.addNominalType(substGenericsInNomType(this.nominalType, typeMap));
    builder.addReceiverType(substGenericsInNomType(this.receiverType, typeMap));
    // TODO(blickly): Do we need instatiation here?
    for (String var : outerVarPreconditions.keySet()) {
      builder.addOuterVarPrecondition(var, outerVarPreconditions.get(var));
    }
    return builder.buildFunction();
  }

  /**
   * Given concrete types for the arguments, unify with the formals to create
   * a type map, and then instantiate this function as usual, by calling
   * {@link #instantiateGenerics}.
   * @throws IllegalStateException if this is not a generic function.
   */
  public FunctionType instantiateGenericsFromArgumentTypes(JSType recvtype, List<JSType> argTypes) {
    checkState(isGeneric());
    if (argTypes.size() < getMinArity() || argTypes.size() > getMaxArity()) {
      return null;
    }
    Multimap<String, JSType> typeMultimap = LinkedHashMultimap.create();
    if (recvtype != null
        && !getThisType().unifyWithSubtype(
            recvtype, typeParameters.asList(), typeMultimap, SubtypeCache.create())) {
      return null;
    }
    for (int i = 0, size = argTypes.size(); i < size; i++) {
      JSType argType = argTypes.get(i);
      if (argType.isBottom()) {
        continue;
      }
      if (!this.getFormalType(i).unifyWithSubtype(
          argType, typeParameters.asList(), typeMultimap, SubtypeCache.create())) {
        return null;
      }
    }
    ImmutableMap.Builder<String, JSType> builder = ImmutableMap.builder();
    for (String typeParam : typeParameters.asList()) {
      Collection<JSType> types = typeMultimap.get(typeParam);
      if (types.size() > 1) {
        return null;
      } else if (types.isEmpty()) {
        builder.put(typeParam, this.commonTypes.UNKNOWN);
      } else {
        builder.put(typeParam, Iterables.getOnlyElement(types));
      }
    }
    return instantiateGenerics(builder.build());
  }

  /** Returns a new FunctionType with the receiverType promoted to the first argument type. */
  FunctionType devirtualize() {
    JSType firstArg = receiverType != null ? receiverType : commonTypes.UNKNOWN;
    return new FunctionType(
        commonTypes,
        ImmutableList.<JSType>builder().add(firstArg).addAll(requiredFormals).build(),
        optionalFormals,
        restFormals,
        returnType,
        nominalType,
        null,
        outerVarPreconditions,
        typeParameters,
        isLoose,
        isAbstract);
  }

  /**
   * Returns a function that is the same as this one, but whose receiver is ?.
   */
  FunctionType withUnknownReceiver() {
    return new FunctionType(
        this.commonTypes,
        this.requiredFormals,
        this.optionalFormals,
        this.restFormals,
        this.returnType,
        this.nominalType,
        this.commonTypes.UNKNOWN,
        this.outerVarPreconditions,
        this.typeParameters,
        this.isLoose,
        this.isAbstract);
  }

  /** Returns a function that is the same as this one, but whose return type is returnType. */
  FunctionType withReturnType(JSType returnType) {
    return new FunctionType(
        this.commonTypes,
        this.requiredFormals,
        this.optionalFormals,
        this.restFormals,
        returnType,
        this.nominalType,
        this.receiverType,
        this.outerVarPreconditions,
        this.typeParameters,
        this.isLoose,
        this.isAbstract);
  }

  /** Returns a function that is the same as this one, but with no parameters. */
  FunctionType withNoParameters() {
    return new FunctionType(
        this.commonTypes,
        ImmutableList.<JSType>of(),
        ImmutableList.<JSType>of(),
        null,
        this.returnType,
        this.nominalType,
        this.receiverType,
        this.outerVarPreconditions,
        this.typeParameters,
        this.isLoose,
        this.isAbstract);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FunctionType)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
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
    return appendTo(new StringBuilder(), ToStringContext.TO_STRING).toString();
  }

  /**
   * Returns a transformed collection of type parameter names, mapped back
   * to original (pre-uniquified) names.
   */
  private static Collection<String> getPrettyTypeParams(
      List<String> typeParams, final ToStringContext ctx) {
    return Collections2.transform(
        typeParams,
        new Function<String, String>() {
          @Override
          public String apply(String typeParam) {
            return ctx.formatTypeVar(typeParam);
          }
        });
  }

  @SuppressWarnings("ReferenceEquality")
  public StringBuilder appendTo(StringBuilder builder, ToStringContext ctx) {
    if (isLoose() && ctx.forAnnotation()) {
      return builder.append("!Function");
    } else if (this == this.commonTypes.LOOSE_TOP_FUNCTION) {
      return builder.append("LOOSE_TOP_FUNCTION");
    } else if (this == this.commonTypes.TOP_FUNCTION) {
      return builder.append("TOP_FUNCTION");
    } else if (isQmarkFunction()) {
      return builder.append(ctx.forAnnotation() ? "!Function" : "Function");
    }
    if (!this.typeParameters.isEmpty()) {
      builder.append("<");
      Joiner.on(",").appendTo(builder, getPrettyTypeParams(this.typeParameters.asList(), ctx));
      builder.append(">");
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
      requiredFormals.get(i).appendTo(builder, ctx);
      builder.append(',');
    }
    for (int i = 0; i < optionalFormals.size(); ++i) {
      optionalFormals.get(i).appendTo(builder, ctx);
      builder.append("=,");
    }
    if (restFormals != null) {
      builder.append("...");
      restFormals.appendTo(builder, ctx);
    }
    // Delete the trailing comma, if present
    if (builder.charAt(builder.length() - 1) == ',') {
      builder.deleteCharAt(builder.length() - 1);
    }
    builder.append(')');
    if (returnType != null) {
      builder.append(": ");
      returnType.appendTo(builder, ctx);
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
        entry.getValue().appendTo(builder, ctx);
      }
      builder.append('}');
    }
    return builder;
  }
}
