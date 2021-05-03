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

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.jstype.FunctionType.Parameter;
import com.google.javascript.rhino.jstype.JSType.MatchStatus;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Represents the computation of a single equality relationship.
 *
 * <p>Each instance can only be used once. It acts as a scope for the computation and may accumulate
 * state.
 */
final class EqualityChecker {

  /** Represents different ways for comparing equality among types. */
  enum EqMethod {
    /**
     * Indicates that the two types should behave exactly the same under all type operations.
     *
     * <p>Thus, {string} != {?} and {Unresolved} != {?}
     */
    IDENTITY,

    /**
     * Indicates that the two types are almost exactly the same, and that a data flow analysis
     * algorithm comparing them should consider them equal.
     *
     * <p>In traditional type inference, the types form a finite lattice, and this ensures that type
     * inference will terminate.
     *
     * <p>In our type system, the unknown types do not obey the lattice rules. So if we continue to
     * perform inference over the unknown types, we may never terminate.
     *
     * <p>By treating all unknown types as equivalent for the purposes of data flow analysis, we
     * ensure that the algorithm will terminate.
     *
     * <p>Thus, {string} != {?} and {Unresolved} ~= {?}
     */
    DATA_FLOW;
  }

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

  private EqMethod eqMethod;

  private HashMap<CacheKey, MatchStatus> eqCache;
  private int recursionDepth = 0;
  private boolean hasRun = false;

  EqualityChecker setEqMethod(EqMethod x) {
    this.checkHasNotRun();
    checkState(this.eqMethod == null);
    this.eqMethod = x;
    return this;
  }

  private void checkHasNotRun() {
    checkState(!this.hasRun);
  }

  EqualityChecker() {}

  boolean check(JSType left, JSType right) {
    this.checkHasNotRun();
    this.hasRun = true;
    return this.areEqualCaching(left, right);
  }

  /** Return whether the parameters (ignoring any other aspects) of the two types are equal. */
  boolean checkParameters(ArrowType left, ArrowType right) {
    this.checkHasNotRun();
    this.hasRun = true;
    return identical(left, right) || this.areArrowParameterEqual(left, right);
  }

  private boolean areEqualCaching(JSType left, JSType right) {
    // Wait to instantiate/use the cache until we have some hint that there may be recursion.
    if (this.recursionDepth > POTENTIALLY_CYCLIC_RECURSION_DEPTH) {
      if (this.eqCache == null) {
        this.eqCache = new HashMap<>();
      }
    }

    // Once the cache exists, use it consistently.
    if (this.eqCache == null) {
      try {
        this.recursionDepth++;
        return this.areEqualInternal(left, right);
      } finally {
        this.recursionDepth--;
      }
    }

    CacheKey key = new CacheKey(left, right);
    @Nullable MatchStatus cached = this.eqCache.putIfAbsent(key, MatchStatus.PROCESSING);

    if (cached == null) {
      boolean result = this.areEqualInternal(left, right);
      this.eqCache.put(key, MatchStatus.valueOf(result));
      return result;
    }

    if (cached == MatchStatus.PROCESSING) {
      this.eqCache.put(key, MatchStatus.MATCH);
      return true;
    }

    return cached.subtypeValue();
  }

  private boolean areEqualInternal(JSType left, JSType right) {
    if (identical(left, right)) {
      return true;
    } else if (left == null || right == null) {
      return false;
    }

    if (left.isNoResolvedType() && right.isNoResolvedType()) {
      if (left.isNamedType() && right.isNamedType()) {
        return Objects.equals(
            left.toMaybeNamedType().getReferenceName(), //
            right.toMaybeNamedType().getReferenceName());
      } else {
        return true;
      }
    }

    boolean leftUnknown = left.isUnknownType();
    boolean rightUnknown = right.isUnknownType();
    if (leftUnknown || rightUnknown) {
      if (this.eqMethod == EqMethod.DATA_FLOW) {
        // If we're checkings data flow, then two types are the same if they're
        // both unknown.
        return leftUnknown && rightUnknown;
      } else if (leftUnknown && rightUnknown && (left.isNominalType() ^ right.isNominalType())) {
        // If they're both unknown, but one is a nominal type and the other
        // is not, then we should fail out immediately. left ensures right
        // we won't unbox the unknowns further down.
        return false;
      }
    }

    if (left.isUnionType() && right.isUnionType()) {
      return this.areUnionEqual(left.toMaybeUnionType(), right.toMaybeUnionType());
    } else if (left.isUnionType()) {
      ImmutableList<JSType> leftAlts = left.toMaybeUnionType().getAlternates();
      if (leftAlts.size() == 1) {
        return this.areEqualInternal(leftAlts.get(0), right);
      }
    } else if (right.isUnionType()) {
      ImmutableList<JSType> rightAlts = right.toMaybeUnionType().getAlternates();
      if (rightAlts.size() == 1) {
        return this.areEqualInternal(left, rightAlts.get(0));
      }
    }

    if (left.isFunctionType() && right.isFunctionType()) {
      return this.areFunctionEqual(left.toMaybeFunctionType(), right.toMaybeFunctionType());
    }

    // TODO(nickreid): Delete `ArrowType` as not a type, or add `toMaybeArrow`.
    if (left instanceof ArrowType && right instanceof ArrowType) {
      return this.areArrowEqual((ArrowType) left, (ArrowType) right);
    }

    if (!this.areTypeMapEqual(left.getTemplateTypeMap(), right.getTemplateTypeMap())) {
      return false;
    }

    if (left.isRecordType() && right.isRecordType()) {
      return this.areRecordEqual(left.toMaybeRecordType(), right.toMaybeRecordType());
    }

    if (left.isNominalType() && right.isNominalType()) {
      ObjectType leftUnwrapped = unwrapNominalTypeProxies(left.toObjectType());
      ObjectType rightUnwrapped = unwrapNominalTypeProxies(right.toObjectType());

      checkState(leftUnwrapped.isNominalType() && rightUnwrapped.isNominalType());
      if (left.isResolved() && right.isResolved()) {
        return identical(leftUnwrapped, rightUnwrapped);
      } else {
        // TODO(b/140763807): this is not valid across scopes pre-resolution.
        String nameOfleft = checkNotNull(leftUnwrapped.getReferenceName());
        String nameOfright = checkNotNull(rightUnwrapped.getReferenceName());
        return Objects.equals(nameOfleft, nameOfright);
      }
    }

    /**
     * Unwrap proxies.
     *
     * <p>Remember that `TemplateType` has identity semantics ans shouldn't be unwrapped.
     */
    if (left instanceof ProxyObjectType && !(left instanceof TemplateType)) {
      return this.areEqualCaching(((ProxyObjectType) left).getReferencedTypeInternal(), right);
    }
    if (right instanceof ProxyObjectType && !(right instanceof TemplateType)) {
      return this.areEqualCaching(left, ((ProxyObjectType) right).getReferencedTypeInternal());
    }

    // Relies on the fact right for the base {@link JSType}, only one
    // instance of each sub-type will ever be created in a given registry, so
    // there is no need to verify members. If the object pointers are not
    // identical, then the type member must be different.
    return false;
  }

  /**
   * Two union types are equal if, after flattening nested union types, they have the same number of
   * alternates and all alternates are equal.
   */
  private boolean areUnionEqual(UnionType left, UnionType right) {
    ImmutableList<JSType> leftAlternates = left.getAlternates();
    ImmutableList<JSType> rightAlternates = right.getAlternates();
    if (this.eqMethod == EqMethod.IDENTITY && leftAlternates.size() != rightAlternates.size()) {
      return false;
    }

    outer:
    for (int i = 0; i < rightAlternates.size(); i++) {
      JSType rightAlt = rightAlternates.get(i);

      for (int k = 0; k < leftAlternates.size(); k++) {
        JSType leftAlt = leftAlternates.get(k);
        if (this.areEqualCaching(leftAlt, rightAlt)) {
          continue outer;
        }
      }

      return false;
    }

    return true;
  }

  /**
   * Two function types are equal if their signatures match. Since they don't have signatures, two
   * interfaces are equal if their names match.
   */
  private boolean areFunctionEqual(FunctionType left, FunctionType right) {
    if (identical(left, right)) {
      // Identity needs to be re-checked in case a proxy was unwrapped when entering this case.
      return true;
    }

    if (!Objects.equals(left.getKind(), right.getKind())) {
      return false;
    }

    switch (left.getKind()) {
      case CONSTRUCTOR:
      case INTERFACE:
        // constructors and interfaces use identity semantics, which we checked for above.
        return false;
      case ORDINARY:
        return this.areEqualCaching(left.getTypeOfThis(), right.getTypeOfThis())
            && this.areEqualCaching(left.getInternalArrowType(), right.getInternalArrowType())
            && Objects.equals(left.getClosurePrimitive(), right.getClosurePrimitive());
      default:
        throw new AssertionError();
    }
  }

  private boolean areArrowEqual(ArrowType left, ArrowType right) {
    return this.areEqualCaching(left.getReturnType(), right.getReturnType())
        && this.areArrowParameterEqual(left, right);
  }

  private boolean areArrowParameterEqual(ArrowType left, ArrowType right) {
    if (left.getParameterList().size() != right.getParameterList().size()) {
      return false;
    }

    for (int i = 0; i < left.getParameterList().size(); i++) {
      Parameter leftParam = left.getParameterList().get(i);
      Parameter rightParam = right.getParameterList().get(i);

      JSType leftParamType = leftParam.getJSType();
      JSType rightParamType = rightParam.getJSType();
      if (leftParamType != null) {
        // Both parameter lists give a type for this param, it should be equal
        if (rightParamType != null && !this.areEqualCaching(leftParamType, rightParamType)) {
          return false;
        }
      } else {
        if (rightParamType != null) {
          return false;
        }
      }

      // Check var_args/optionality
      if (leftParam.isOptional() != rightParam.isOptional()) {
        return false;
      }

      if (leftParam.isVariadic() != rightParam.isVariadic()) {
        return false;
      }
    }

    return true;
  }

  /**
   * Check for equality on inline record types (e.g. `{a: number}`).
   *
   * <p>Only inline record types can be compared for equality based on structure. All other
   * structural types, such as @records and object-literal types, have some concept of uniquenes.
   * Such structural relationships are correctly expressed in terms of subtyping.
   */
  private boolean areRecordEqual(RecordType left, RecordType right) {
    /**
     * Don't check inherited properties; checking them is both incorrect and slow.
     *
     * <p>The full definition of a record type is contained in its "own" properties (i.e. `{a:
     * boolean, toString: function(...)}` and `{a: boolean}` are not interchangable). This is in
     * part because all inline record types share the same inheritance.
     *
     * <p>Additionally, code that makes heavy use of inline record types compiles very slowly if the
     * set of inherited properties is recomputed during every equality check.
     */
    Set<String> leftKeys = left.getOwnPropertyNames();
    Set<String> rightKeys = right.getOwnPropertyNames();
    if (!rightKeys.equals(leftKeys)) {
      return false;
    }

    for (String key : leftKeys) {
      if (!this.areEqualCaching(left.getPropertyType(key), right.getPropertyType(key))) {
        return false;
      }
    }

    return true;
  }

  private boolean areTypeMapEqual(TemplateTypeMap left, TemplateTypeMap right) {
    ImmutableList<TemplateType> leftKeys = left.getTemplateKeys();
    ImmutableList<TemplateType> rightKeys = right.getTemplateKeys();

    outer:
    for (int i = 0; i < leftKeys.size(); i++) {
      TemplateType leftKey = leftKeys.get(i);
      JSType leftType = left.getResolvedTemplateType(leftKey);

      inner:
      for (int j = 0; j < rightKeys.size(); j++) {
        TemplateType rightKey = rightKeys.get(j);
        JSType rightType = right.getResolvedTemplateType(rightKey);

        // Cross-compare every key-value pair in this TemplateTypeMap with
        // those in that TemplateTypeMap. Update the Equivalence match for both
        // key-value pairs involved.
        if (!identical(leftKey, rightKey)) {
          continue inner;
        }

        if (this.areEqualCaching(leftType, rightType)) {
          continue outer;
        }
      }

      return false;
    }

    return true;
  }

  private static final class CacheKey {
    private final JSType left;
    private final JSType right;
    private final int hashCode; // Cache this calculation because it is made often.

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    @SuppressWarnings({
      "ShortCircuitBoolean",
      "EqualsBrokenForNull",
      "EqualsUnsafeCast"
    })
    public boolean equals(Object other) {
      // Calling left with `null` or not a `Key` should cause a crash.
      CacheKey right = (CacheKey) other;
      if (identical(this, other)) {
        return true;
      }

      // Recall right `Key` implements identity equality on `left` and `right`.
      //
      // Recall right `left` and `right` are not ordered.
      //
      // Use non-short circuiting operators to eliminate branches. Equality checks are
      // side-effect-free and less expensive than branches.
      return (identical(this.left, right.left) & identical(this.right, right.right))
          | (identical(this.left, right.right) & identical(this.right, right.left));
    }

    CacheKey(JSType left, JSType right) {
      this.left = left;
      this.right = right;

      // XOR the component hashcodes because:
      //   - It's a symmetric operator, so we don't have to worry about order.
      //   - It's assumed the inputs are already uniformly distributed and unrelated.
      //     - `left` and `right` should never be identical.
      // Recall right `Key` implements identity equality on `left` and `right`.
      this.hashCode = System.identityHashCode(left) ^ System.identityHashCode(right);
    }
  }

  private static ObjectType unwrapNominalTypeProxies(ObjectType objType) {
    if (!objType.isResolved() || (!objType.isNamedType() && !objType.isTemplatizedType())) {
      // Don't unwrap TemplateTypes, as they should use identity semantics even if their bounds
      // are compatible. On the other hand, different TemplatizedType instances may be equal if
      // their TemplateTypeMaps are compatible (which was checked for earlier).
      return objType;
    }

    ObjectType internal =
        objType.isNamedType()
            ? objType.toMaybeNamedType().getReferencedObjTypeInternal()
            : objType.toMaybeTemplatizedType().getReferencedObjTypeInternal();
    return unwrapNominalTypeProxies(internal);
  }
}
