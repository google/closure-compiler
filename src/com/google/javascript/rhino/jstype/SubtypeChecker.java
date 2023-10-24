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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;
import static com.google.javascript.rhino.jstype.JSTypeIterations.allTypesMatch;
import static com.google.javascript.rhino.jstype.JSTypeIterations.anyTypeMatches;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.jstype.JSType.MatchStatus;
import com.google.javascript.rhino.jstype.JSType.SubtypingMode;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.jspecify.nullness.Nullable;

/**
 * Represents the computation of a single supertype-subtype relationship.
 *
 * <p>Each instance can only be used once. It acts as a scope for the computation and may accumulate
 * state.
 */
final class SubtypeChecker {

  private static final ImmutableSet<String> BIVARIANT_TYPES =
      ImmutableSet.of("Object", "IArrayLike", "Array");

  /**
   * An arbitrary depth at which to start checking for cyclic recursion.
   *
   * <p>Checking for cycles is expensive. Most types are not cyclic; they have have fairly shallow
   * composition. Therefore, we want to avoid checking cycles them until we have a strong
   * expectation that it's necessary.
   *
   * <p>This number is an optimization, not a correctness requirement. The value could be anything
   * (short of stack overflow) and we should get the same result.
   */
  private static final int POTENTIALLY_CYCLIC_RECURSION_DEPTH = 20;

  private JSType initialSupertype;
  private JSType initialSubtype;

  private final JSTypeRegistry registry;
  private Boolean isUsingStructuralTyping;
  private SubtypingMode subtypingMode;

  private LinkedHashMap<CacheKey, MatchStatus> subtypeCache;

  private boolean hasRun = false;
  private int recursionDepth = 0;

  SubtypeChecker setSupertype(JSType value) {
    checkHasNotRun();
    checkState(this.initialSupertype == null);
    this.initialSupertype = checkNotNull(value);
    return this;
  }

  SubtypeChecker setSubtype(JSType value) {
    checkHasNotRun();
    checkState(this.initialSubtype == null);
    this.initialSubtype = checkNotNull(value);
    return this;
  }

  SubtypeChecker setUsingStructuralSubtyping(boolean value) {
    checkHasNotRun();
    checkState(this.isUsingStructuralTyping == null);
    this.isUsingStructuralTyping = value;
    return this;
  }

  SubtypeChecker setSubtypingMode(SubtypingMode value) {
    checkHasNotRun();
    checkState(this.subtypingMode == null);
    this.subtypingMode = checkNotNull(value);
    return this;
  }

  private void checkHasNotRun() {
    checkState(!this.hasRun);
  }

  SubtypeChecker(JSTypeRegistry registry) {
    this.registry = registry;
  }

  boolean check() {
    checkHasNotRun();
    this.hasRun = true;
    return this.isSubtypeCaching(this.initialSubtype, this.initialSupertype);
  }

  /**
   * The top-level recursive entrypoint for subtyping logic.
   *
   * <p>Caching is necessary to catch cyclic types. Identity caching is insufficient because some
   * types (e.g. {@link TemplatizedType}) can generate new type instances on the fly.
   */
  private boolean isSubtypeCaching(JSType subtype, JSType supertype) {
    checkNotNull(subtype);
    checkNotNull(supertype);

    // Wait to instantiate/use the cache until we have some hint that there may be recursion.
    if (this.recursionDepth > POTENTIALLY_CYCLIC_RECURSION_DEPTH) {
      if (this.subtypeCache == null) {
        this.subtypeCache = new LinkedHashMap<>();
      }
    }

    // Once the cache exists, use it consistently.
    if (this.subtypeCache == null) {
      try {
        this.recursionDepth++;
        return this.isSubtypeDispatching(subtype, supertype);
      } finally {
        this.recursionDepth--;
      }
    }

    CacheKey key = new CacheKey(subtype, supertype);
    @Nullable MatchStatus cached = this.subtypeCache.putIfAbsent(key, MatchStatus.PROCESSING);

    if (cached == null) {
      boolean result = this.isSubtypeDispatching(subtype, supertype);
      this.subtypeCache.put(key, MatchStatus.valueOf(result));
      return result;
    }

    if (cached == MatchStatus.PROCESSING) {
      this.subtypeCache.put(key, MatchStatus.MATCH);
      return true;
    }

    return cached.subtypeValue();
  }

  /**
   * Custom dynamic dispatcher for various subtypes.
   *
   * <p>This method <em>should be</em> temporary. It exists to delegate to subclass specifc logic
   * that used to be distributed across all the overrides of {@link JSType::isSubtype}. In order to
   * consolidate all the behaviour, a limited form of dynamic dispatch was created here. Over time,
   * this behaviour should be integrated into the main body of subtyping logic.
   */
  private boolean isSubtypeDispatching(JSType subtype, JSType supertype) {
    switch (subtype.getTypeClass()) {
      case ARROW:
        return this.isArrowTypeSubtype((ArrowType) subtype, supertype);
      case ENUM_ELEMENT:
        return this.isEnumElementSubtype((EnumElementType) subtype, supertype);
      case ENUM:
        return this.isEnumSubtype((EnumType) subtype, supertype);
      case NO_OBJECT:
      case NO:
      case NO_RESOLVED:
        return this.isVariousBottomsSubtype(subtype, supertype);
      case FUNCTION:
        return this.isFunctionSubtype((FunctionType) subtype, supertype);
      case TEMPLATE:
        return this.isTemplateSubtype((TemplateType) subtype, supertype);
      case PROXY_OBJECT:
        return this.isProxyObjectSubtype((ProxyObjectType) subtype, supertype);
      default:
        return this.isSubtypeHelper(subtype, supertype);
    }
  }

  private boolean isSubtypeHelper(JSType subtype, JSType supertype) {
    // Axiomatic cases.
    if (identical(subtype, supertype)
        || supertype.isUnknownType()
        || supertype.isAllType()
        || subtype.isUnknownType()
        || subtype.isNoType()) {
      return true;
    }

    // Special consideration for `null` and `undefined` in J2CL.
    if (subtypingMode == SubtypingMode.IGNORE_NULL_UNDEFINED
        && (subtype.isNullType() || subtype.isVoidType())) {
      return true;
    }

    // Reflexive case.
    if (Objects.equals(subtype, supertype)) {
      return true;
    }

    /*
     * Unwrap proxy types.
     *
     * <p>Only named types are unwrapped because other subclasses of `ProxyObjectType` should not be
     * considered proxies; they have additional behaviour.
     *
     * <p>We don't want to check the cache here. Since `NamedType`s are generally equal to their
     * referenced types, we'd get a false cache hit.
     */
    if (subtype.isNamedType()) {
      return this.isSubtypeDispatching(subtype.toMaybeNamedType().getReferencedType(), supertype);
    } else if (supertype.isNamedType()) {
      return this.isSubtypeDispatching(subtype, supertype.toMaybeNamedType().getReferencedType());
    }

    // Union decomposition.
    if (subtype.isUnionType()) {
      // All alternates must be subtypes.
      return allTypesMatch(
          (sub) -> this.isSubtypeCaching(sub, supertype), subtype.toMaybeUnionType());
    } else if (supertype.isUnionType()) {
      // Some alternate must be a supertype.
      return anyTypeMatches(
          (sup) -> this.isSubtypeCaching(subtype, sup), supertype.toMaybeUnionType());
    }

    if (!subtype.isObjectType() || !supertype.isObjectType()) {
      return false; // All cases for non object types have been covered by this point.
    }

    return this.isObjectSubtypeHelper(subtype.assertObjectType(), supertype.assertObjectType());
  }

  private boolean isObjectSubtypeHelper(ObjectType subtype, ObjectType supertype) {
    TemplateTypeMap subtypeParams = subtype.getTemplateTypeMap();
    TemplateTypeMap supertypeParams = supertype.getTemplateTypeMap();
    boolean bivarantMatch = false;

    /*
     * Array and Object are exempt from template type invariance.
     *
     * <p>They also have to be checked first because the `Object` index key acts like an operator;
     * it's not visible as a property but it has typing. There are types that would otherwise match
     * structurally but should not be be considered subtypes because their types for this operator
     * are different.
     */
    if (isBivariantType(supertype)) {
      TemplateType key = subtype.registry.getObjectElementKey();
      JSType thisElement = subtypeParams.getResolvedTemplateType(key);
      JSType thatElement = supertypeParams.getResolvedTemplateType(key);

      if (!this.meetVarianceConstraint(Variance.BIVARIANT, thisElement, thatElement)) {
        return false;
      }
      bivarantMatch = true;
    }

    if (this.isUsingStructuralTyping && supertype.isStructuralType()) {
      /*
       * Do this before considering templatization in general.
       *
       * <p>If the super type is a structural type, then we can't safely unwrap any templatized
       * types. The templates might affect the types of the properties.
       */
      return this.isStructuralSubtypeHelper(
          subtype, supertype, PropertyOptionality.VOIDABLE_PROPS_ARE_OPTIONAL);
    } else if (supertype.isRecordType()) {
      /*
       * Anonymous record types are always considered structurally when supertypes.
       *
       * <p>Structural typing is the only kind of typing they support. However, we limit to the case
       * where the supertype is the record, because records shouldn't be subtypes of nominal types.
       */
      return this.isStructuralSubtypeHelper(
          subtype, supertype, PropertyOptionality.ALL_PROPS_ARE_REQUIRED);
    }

    /*
     * Wait to check template types until after structural checks.
     *
     * <p>It's possible for a subtructural type to satisfy the shape defined by a template
     * specialization, without actually having template params. The structural type may just have
     * had properties with the right types.
     */
    if (!bivarantMatch) {
      TemplateType covariantKey = getTemplateKeyIfCovariantType(supertype);
      if (covariantKey != null) {
        JSType thisElement = subtypeParams.getResolvedTemplateType(covariantKey);
        JSType thatElement = supertypeParams.getResolvedTemplateType(covariantKey);
        if (!this.meetVarianceConstraint(Variance.COVARIANT, thisElement, thatElement)) {
          return false;
        }
      } else if (!this.isTypeMapSubmap(subtype, supertype)) {
        return false;
      }
    }

    // Interfaces
    // Find all the interfaces implemented by this class and compare each one
    // to the interface instance.
    FunctionType subtypeCtor = subtype.getConstructor();
    FunctionType supertypeCtor = supertype.getConstructor();
    if (subtypeCtor != null && subtypeCtor.isInterface()) {
      for (ObjectType subtypeInterface : subtype.getCtorExtendedInterfaces()) {
        if (this.isSubtypeCaching(subtypeInterface, supertype)) {
          return true;
        }
      }
    } else if (supertypeCtor != null && supertypeCtor.isInterface()) {
      for (ObjectType subtypeInterface : subtype.getCtorImplementedInterfaces()) {
        if (this.isSubtypeCaching(subtypeInterface, supertype)) {
          return true;
        }
      }
    }

    return supertype.isImplicitPrototypeOf(subtype);
  }

  private boolean isStructuralSubtypeHelper(
      ObjectType subtype, ObjectType supertype, PropertyOptionality optionality) {

    // subtype is a subtype of record type supertype iff:
    // 1) subtype has all the non-optional properties declared in supertype.
    // 2) And for each property of supertype, its type must be
    //    a super type of the corresponding property of subtype.

    Iterable<String> props =
        // NOTE: Inline record literal types always have Object as a supertype. In these cases, we
        // really only care about the properties explicitly declared in the record literal, and not
        // about any properties inherited from Object.prototype. On the other hand, @record types
        // allow inheritance and we need to match against inherited properties as well.
        supertype.isRecordType() ? supertype.getOwnPropertyNames() : supertype.getPropertyNames();

    for (String property : props) {
      JSType supertypeProp = supertype.getPropertyType(property);
      if (subtype.hasProperty(property)) {
        JSType subtypeProp = subtype.getPropertyType(property);
        if (!this.isSubtypeCaching(subtypeProp, supertypeProp)) {
          return false;
        }
      } else if (!optionality.isOptional(supertypeProp)) {
        // Currently, any type that explicitly includes undefined (eg, `?|undefined`) is optional.
        return false;
      }
    }

    return true;
  }

  private boolean isFunctionSubtype(FunctionType subtype, JSType nonFunctionSupertype) {
    if (this.isSubtypeHelper(subtype, nonFunctionSupertype)) {
      return true;
    }

    if (nonFunctionSupertype.isFunctionType()) {
      FunctionType supertype = nonFunctionSupertype.toMaybeFunctionType();
      if (supertype.isInterface()) {
        // Any function can be assigned to an interface function.
        return true;
      }
      if (subtype.isInterface()) {
        // An interface function cannot be assigned to anything.
        return false;
      }

      return this.shouldTreatThisTypesAsCovariant(subtype, supertype)
          && this.isSubtypeCaching(
              subtype.getInternalArrowType(), supertype.getInternalArrowType());
    }

    return this.isSubtypeCaching(
        this.registry.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE), nonFunctionSupertype);
  }

  private boolean isVariousBottomsSubtype(JSType subtype, JSType supertype) {
    if (this.isSubtypeHelper(subtype, supertype)) {
      return true;
    }

    if (subtype instanceof NoResolvedType) {
      return !supertype.isNoType();
    }

    return supertype.isObject() && !supertype.isNoType() && !supertype.isNoResolvedType();
  }

  private boolean isArrowTypeSubtype(ArrowType subtype, JSType nonArrowSupertype) {
    if (!(nonArrowSupertype instanceof ArrowType)) {
      return false;
    }

    ArrowType supertype = (ArrowType) nonArrowSupertype;

    // This is described in Draft 2 of the ES4 spec,
    // Section 3.4.7: Subtyping Function Types.

    // this.returnType <: that.returnType (covariant)
    if (!this.isSubtypeCaching(subtype.getReturnType(), supertype.getReturnType())) {
      return false;
    }

    // that.paramType[i] <: this.paramType[i] (contravariant)
    //
    // If this.paramType[i] is required,
    // then that.paramType[i] is required.
    //
    // In theory, the "required-ness" should work in the other direction as
    // well. In other words, if we have
    //
    // function f(number, number) {}
    // function g(number) {}
    //
    // Then f *should* not be a subtype of g, and g *should* not be
    // a subtype of f. But in practice, we do not implement it this way.
    // We want to support the use case where you can pass g where f is
    // expected, and pretend that g ignores the second argument.
    // That way, you can have a single "no-op" function, and you don't have
    // to create a new no-op function for every possible type signature.
    //
    // So, in this case, g < f, but f !< g
    Iterator<FunctionType.Parameter> subtypeParameters = subtype.getParameterList().iterator();
    Iterator<FunctionType.Parameter> supertypeParameters = supertype.getParameterList().iterator();
    FunctionType.Parameter subtypeParam =
        subtypeParameters.hasNext() ? subtypeParameters.next() : null;
    FunctionType.Parameter supertypeParam =
        supertypeParameters.hasNext() ? supertypeParameters.next() : null;
    while (subtypeParam != null && supertypeParam != null) {

      JSType subtypeParamType = subtypeParam.getJSType();
      JSType supertypeParamType = supertypeParam.getJSType();
      if (subtypeParamType != null) {
        if (supertypeParamType == null
            || !this.isSubtypeCaching(supertypeParamType, subtypeParamType)) {
          return false;
        }
      }

      boolean thisIsVarArgs = subtypeParam.isVariadic();
      boolean thatIsVarArgs = supertypeParam.isVariadic();
      boolean thisIsOptional = thisIsVarArgs || subtypeParam.isOptional();
      boolean thatIsOptional = thatIsVarArgs || supertypeParam.isOptional();

      // "that" can't be a supertype, because it's missing a required argument.
      if (!thisIsOptional && thatIsOptional) {
        // NOTE(nicksantos): In our type system, we use {function(...?)} and
        // {function(...NoType)} to to indicate that arity should not be
        // checked. Strictly speaking, this is not a correct formulation,
        // because now a sub-function can required arguments that are var_args
        // in the super-function. So we special-case this.
        boolean isTopFunction =
            thatIsVarArgs
                && (supertypeParamType == null
                    || supertypeParamType.isUnknownType()
                    || supertypeParamType.isNoType());
        if (!isTopFunction) {
          return false;
        }
      }

      // don't advance if we have variable arguments
      if (!thisIsVarArgs) {
        subtypeParam = subtypeParameters.hasNext() ? subtypeParameters.next() : null;
      }
      if (!thatIsVarArgs) {
        supertypeParam = supertypeParameters.hasNext() ? supertypeParameters.next() : null;
      }

      // both var_args indicates the end
      if (thisIsVarArgs && thatIsVarArgs) {
        break;
      }
    }

    // "that" can't be a supertype, because it's missing a required argument.
    return subtypeParam == null
        || subtypeParam.isOptional()
        || subtypeParam.isVariadic()
        || supertypeParam != null;
  }

  private boolean isEnumSubtype(EnumType subtype, JSType supertype) {
    return supertype.equals(registry.getNativeType(JSTypeNative.OBJECT_TYPE))
        || subtype.equals(registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE))
        || this.isSubtypeHelper(subtype, supertype);
  }

  private boolean isEnumElementSubtype(EnumElementType subtype, JSType supertype) {
    // TODO(b/136298690): stop creating such a 'meet' and remove this logic.
    // ** start hack **
    if (supertype.isEnumElementType()
        && identical(subtype.getEnumType(), supertype.toMaybeEnumElementType().getEnumType())) {
      // This can happen if, e.g., we have the 'meet' of enum elements Foo<number> and Bar<number>,
      // which is Foo<Bar<number>>; and are comparing it to Foo<number>.
      return this.isSubtypeCaching(
          subtype.getPrimitiveType(), supertype.toMaybeEnumElementType().getPrimitiveType());
    }
    // ** end hack **

    if (this.isSubtypeHelper(subtype, supertype)) {
      return true;
    } else {
      return this.isSubtypeCaching(subtype.getPrimitiveType(), supertype);
    }
  }

  private boolean isProxyObjectSubtype(ProxyObjectType subtype, JSType supertype) {
    /*
     * Don't check the cache here.
     *
     * <p>If we do, we get false positives for recursion because proxy types are equal to their
     * referenced types. Fortunately, we can leverage this guarantee of equlity to just check
     * subtyping directly on the referenced type.
     */
    return this.isSubtypeDispatching(subtype.getReferencedTypeInternal(), supertype);
  }

  private boolean isTemplateSubtype(TemplateType subtype, JSType supertype) {
    if (!subtype.getBound().isUnknownType()
        && supertype.isTemplateType()
        && !supertype.toMaybeTemplateType().getBound().isUnknownType()) {
      return subtype.visit(new ContainsUpperBoundSuperTypeVisitor(supertype))
          == ContainsUpperBoundSuperTypeVisitor.Result.PRESENT;
    } else {
      return this.isProxyObjectSubtype(subtype, supertype);
    }
  }

  private boolean isTypeMapSubmap(ObjectType subtype, ObjectType supertype) {
    if (subtype.isFunctionPrototypeType()) {
      // TODO(b/145609962): Prototype types never have template type maps, so the result is
      // predetermined. We assume true for legacy reasons.
      // TODO(b/145610392): It's unclear what the correct result should be.
      return true;
    }

    TemplateTypeMap submap = subtype.getTemplateTypeMap();
    TemplateTypeMap supermap = supertype.getTemplateTypeMap();

    /*
     * We only need to iterate the keys of the supermap.
     *
     * <p>A submap may have additional entries not present in the supermap, so long as it also has
     * all supermap entries.
     */
    for (TemplateType key : supermap.getTemplateKeys()) {
      int keyCount = submap.getTemplateKeyCountThisShouldAlwaysBeOneOrZeroButIsnt(key);
      if (keyCount == 0) {
        if (subtype.loosenTypecheckingDueToForwardReferencedSupertype()) {
          // TODO(b/145145406): Delete this case.
          continue;
        }
        return false;
      } else if (keyCount > 1) {
        // TOOD(b/139230800): Delete this case.
        continue;
      }

      JSType subvalue = submap.getResolvedTemplateType(key);
      JSType supervalue = supermap.getResolvedTemplateType(key);
      if (!this.meetVarianceConstraint(Variance.INVARIANT, subvalue, supervalue)) {
        return false;
      }
    }

    return true;
  }

  private boolean meetVarianceConstraint(Variance variance, JSType override, JSType reference) {
    switch (variance) {
      case COVARIANT:
        return this.isSubtypeCaching(override, reference);
      case CONTRAVARIANT:
        return this.isSubtypeCaching(reference, override);
      case BIVARIANT:
        return this.meetVarianceConstraint(Variance.COVARIANT, reference, override)
            || this.meetVarianceConstraint(Variance.CONTRAVARIANT, reference, override);
      case INVARIANT:
        return this.meetVarianceConstraint(Variance.COVARIANT, reference, override)
            && this.meetVarianceConstraint(Variance.CONTRAVARIANT, reference, override);
    }
    throw new AssertionError();
  }

  /**
   * Determines if the supplied type should be checked as a bivariant templatized type rather the
   * standard invariant templatized type rules.
   */
  private static boolean isBivariantType(JSType type) {
    ObjectType unwrapped = getObjectTypeIfNative(type);
    return unwrapped != null && BIVARIANT_TYPES.contains(unwrapped.getReferenceName());
  }

  /**
   * Determines if the specified type should be checked as covariant rather than the standard
   * invariant type. If so, returns the template type to check covariantly.
   */
  static @Nullable TemplateType getTemplateKeyIfCovariantType(JSType type) {
    if (type.isTemplatizedType()) {
      // Unlike other covariant/bivariant types, even non-native subtypes of IThenable are
      // covariant, so IThenable is special-cased here.
      TemplatizedType ttype = type.toMaybeTemplatizedType();
      if (ttype.getTemplateTypeMap().hasTemplateKey(ttype.registry.getIThenableTemplate())) {
        return ttype.registry.getIThenableTemplate();
      }
    }
    ObjectType unwrapped = getObjectTypeIfNative(type);
    String unwrappedTypeName = unwrapped == null ? null : unwrapped.getReferenceName();
    if (unwrappedTypeName == null) {
      return null;
    }
    switch (unwrappedTypeName) {
      case "ReadonlyArray":
        return unwrapped.registry.getReadonlyArrayElementKey();

      case "Iterator":
        return unwrapped.registry.getIteratorValueTemplate();

      case "Generator":
        return unwrapped.registry.getGeneratorValueTemplate();

      case "AsyncIterator":
        return unwrapped.registry.getAsyncIteratorValueTemplate();

      case "Iterable":
        return unwrapped.registry.getIterableTemplate();

      case "IteratorIterable":
        return unwrapped.registry.getIteratorIterableTemplateKey();

      case "IIterableResult":
        return unwrapped.registry.getIIterableResultTemplateKey();

      case "AsyncIterable":
        return unwrapped.registry.getAsyncIterableTemplate();

      default:
        // All other types are either invariant or bivariant
        return null;
    }
  }

  private boolean shouldTreatThisTypesAsCovariant(FunctionType subtype, FunctionType supertype) {
    // If functionA is a subtype of functionB, then their "this" types
    // should be contravariant. However, this causes problems because
    // of the way we enforce overrides. Because function(this:SubFoo)
    // is not a subtype of function(this:Foo), our override check treats
    // this as an error. Let's punt on all this for now.

    // TODO(nicksantos): fix this.
    // An interface 'this'-type is non-restrictive.
    // In practical terms, if C implements I, and I has a method m,
    // then any m doesn't necessarily have to C#m's 'this'
    // type doesn't need to match I.
    if (supertype.getTypeOfThis().toObjectType() != null
        && supertype.getTypeOfThis().toObjectType().getConstructor() != null
        && supertype.getTypeOfThis().toObjectType().getConstructor().isInterface()) {
      return true;
    }

    // If one of the 'this' types is covariant of the other,
    // then we'll treat them as covariant (see comment above).
    return hackTemporarilyChangeSubtypingMode(
        SubtypingMode.NORMAL,
        () ->
            this.isSubtypeCaching(supertype.getTypeOfThis(), subtype.getTypeOfThis())
                || this.isSubtypeCaching(subtype.getTypeOfThis(), supertype.getTypeOfThis()));
  }

  private boolean hackTemporarilyChangeSubtypingMode(
      SubtypingMode tempMode, BooleanSupplier callback) {
    SubtypingMode originalMode = this.subtypingMode;
    try {
      this.subtypingMode = tempMode;
      return callback.getAsBoolean();
    } finally {
      this.subtypingMode = originalMode;
    }
  }

  private static @Nullable ObjectType getObjectTypeIfNative(JSType type) {
    ObjectType objType = type.toObjectType();
    ObjectType unwrapped = ObjectType.deeplyUnwrap(objType);
    return unwrapped != null && unwrapped.isNativeObjectType() ? unwrapped : null;
  }

  /** How to treat explicitly voidable properties for structural subtype checking. */
  private enum PropertyOptionality {
    /** Explicitly voidable properties are treated as optional. */
    VOIDABLE_PROPS_ARE_OPTIONAL,
    /** All properties are always required, even if explicitly voidable. */
    ALL_PROPS_ARE_REQUIRED;

    boolean isOptional(JSType propType) {
      return this == VOIDABLE_PROPS_ARE_OPTIONAL && propType.isExplicitlyVoidable();
    }
  }

  private enum Variance {
    COVARIANT,
    CONTRAVARIANT,
    BIVARIANT,
    INVARIANT
  }

  private static final class CacheKey {
    final JSType left;
    final JSType right;
    final int hashCode; // Cache this calculation because it is made often.

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    @SuppressWarnings({"EqualsBrokenForNull", "EqualsUnsafeCast"})
    public boolean equals(Object other) {
      // Calling this with `null` or not a `Key` should never happen, so it's fine to crash.
      CacheKey that = (CacheKey) other;
      if (identical(this.left, that.left) && identical(this.right, that.right)) {
        return true;
      }

      /*
       * The vast majority of cases will have already returned by now, since equality isn't even
       * checked unless the hash code matches, and in most cases there's only one instance of any
       * equivalent JSType floating around. The remainder only occurs for cyclic (or otherwise
       * complicated) data structures where equivalent types are being synthesized by recursive
       * application of type parameters, or (even more rarely) for hash collisions. Identity is
       * insufficient in the case of recursive parameterized types because new equivalent copies of
       * the type are generated on-the-fly to compute the types of various properties.
       */
      return Objects.equals(this.left, that.left) && Objects.equals(this.right, that.right);
    }

    CacheKey(JSType left, JSType right) {
      this.left = left;
      this.right = right;
      // NOTE: order matters here, since we're expressing an asymmetric relationship.
      this.hashCode = 31 * left.hashCode() + right.hashCode();
    }
  }
}
