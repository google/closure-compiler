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

import static com.google.javascript.rhino.jstype.TernaryValue.UNKNOWN;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.ErrorReporter;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The {@code UnionType} implements a common JavaScript idiom in which the
 * code is specifically designed to work with multiple input types.  Because
 * JavaScript always knows the run-time type of an object value, this is safer
 * than a C union.<p>
 *
 * For instance, values of the union type {@code (String,boolean)} can be of
 * type {@code String} or of type {@code boolean}. The commutativity of the
 * statement is captured by making {@code (String,boolean)} and
 * {@code (boolean,String)} equal.<p>
 *
 *
 * The implementation of this class prevents the creation of nested
 * unions.<p>
 */
public class UnionType extends JSType {
  private static final long serialVersionUID = 2L;

  // NOTE: to avoid allocating iterators, all the loops below iterate over alternates by index
  // instead of using the for-each loop idiom.

  // We currently keep two separate lists because `DisambiguateProperties` needs to keep track
  // of exactly how types are conflated. If we forget that a nominal type and its strucutral
  // subtype were members of the same union, we over-conflate for some reason, and knee-cap that
  // optimization.
  // TODO(nickreid): Find a less complex/expensize way to prevent this conflation. It's a high cost
  // for a pretty uncommon case.
  private ImmutableList<JSType> alternatesRetainingStructuralSubtypes;
  private ImmutableList<JSType> alternatesCollapsingStructuralSubtypes;

  /**
   * Creates a union type.
   *
   * @param alternatesRetainingStructuralSubtypes the alternates of the union without structural
   *     typing subtype
   */
  UnionType(JSTypeRegistry registry, ImmutableList<JSType> alternatesRetainingStructuralSubtypes) {
    super(registry);

    // TODO(nickreid): This assignment is load bearing and should be cleaned-up. It, and the loop
    // below, duplicate `rebuildAlternates()`. However, if that method were called eagerly it would
    // break some assumptions of `JSTypeRegisty` by using a builder with a default configuration to
    // do the rebuild. The registry just seems to trust this will never happen. The design of
    // `UnionType(Builder)` should be changed to ensure that rebuild uses a builder with the same
    // configuration.
    this.alternatesRetainingStructuralSubtypes = alternatesRetainingStructuralSubtypes;

    UnionTypeBuilder builder = UnionTypeBuilder.createForCollapsingStructuralSubtypes(registry);
    for (JSType alternate : alternatesRetainingStructuralSubtypes) {
      builder.addAlternate(alternate);
    }
    this.alternatesCollapsingStructuralSubtypes = builder.getAlternates();
  }

  /**
   * Gets the alternate types of this union type.
   *
   * @return The alternate types of this union type. The returned set is immutable.
   */
  public ImmutableList<JSType> getAlternates() {
    if (anyMatch(JSType::isUnionType, alternatesRetainingStructuralSubtypes)) {
      rebuildAlternates();
    }
    return alternatesCollapsingStructuralSubtypes;
  }

  /**
   * Gets the alternate types of this union type, including structural interfaces and implicit
   * implementations as distinct alternatesCollapsingStructuralSubtypes.
   *
   * @return The alternate types of this union type. The returned set is immutable.
   */
  public ImmutableList<JSType> getAlternatesWithoutStructuralTyping() {
    if (anyMatch(JSType::isUnionType, alternatesRetainingStructuralSubtypes)) {
      rebuildAlternates();
    }
    return alternatesRetainingStructuralSubtypes;
  }

  /**
   * Use UnionTypeBuilder to rebuild the list of alternatesCollapsingStructuralSubtypes and hashcode
   * of the current UnionType.
   */
  private void rebuildAlternates() {
    UnionTypeBuilder nonCollapsingBuilder = UnionTypeBuilder.create(registry);
    UnionTypeBuilder collapsingBuilder =
        UnionTypeBuilder.createForCollapsingStructuralSubtypes(registry);

    for (JSType alternate : alternatesRetainingStructuralSubtypes) {
      nonCollapsingBuilder.addAlternate(alternate);
      collapsingBuilder.addAlternate(alternate);
    }

    alternatesRetainingStructuralSubtypes = nonCollapsingBuilder.getAlternates();
    alternatesCollapsingStructuralSubtypes = collapsingBuilder.getAlternates();
  }

  /**
   * This predicate is used to test whether a given type can appear in a
   * numeric context, such as an operand of a multiply operator.
   *
   * @return true if the type can appear in a numeric context.
   */
  @Override
  public boolean matchesNumberContext() {
    // TODO(user): Reverse this logic to make it correct instead of generous.
    return anyMatch(JSType::matchesNumberContext, alternatesRetainingStructuralSubtypes);
  }

  /**
   * This predicate is used to test whether a given type can appear in a
   * {@code String} context, such as an operand of a string concat ({@code +})
   * operator.<p>
   *
   * All types have at least the potential for converting to {@code String}.
   * When we add externally defined types, such as a browser OM, we may choose
   * to add types that do not automatically convert to {@code String}.
   *
   * @return {@code true} if not {@link VoidType}
   */
  @Override
  public boolean matchesStringContext() {
    // TODO(user): Reverse this logic to make it correct instead of generous.
    return anyMatch(JSType::matchesStringContext, alternatesRetainingStructuralSubtypes);
  }

  /**
   * This predicate is used to test whether a given type can appear in a {@code Symbol} context
   *
   * @return {@code true} if not it maybe a symbol or Symbol object
   */
  @Override
  public boolean matchesSymbolContext() {
    // TODO(user): Reverse this logic to make it correct instead of generous.
    return anyMatch(JSType::matchesSymbolContext, alternatesRetainingStructuralSubtypes);
  }

  /**
   * This predicate is used to test whether a given type can appear in an
   * {@code Object} context, such as the expression in a {@code with}
   * statement.<p>
   *
   * Most types we will encounter, except notably {@code null}, have at least
   * the potential for converting to {@code Object}.  Host defined objects can
   * get peculiar.<p>
   *
   * VOID type is included here because while it is not part of the JavaScript
   * language, functions returning 'void' type can't be used as operands of
   * any operator or statement.<p>
   *
   * @return {@code true} if the type is not {@link NullType} or
   *         {@link VoidType}
   */
  @Override
  public boolean matchesObjectContext() {
    // TODO(user): Reverse this logic to make it correct instead of generous.
    return anyMatch(JSType::matchesObjectContext, alternatesRetainingStructuralSubtypes);
  }

  @Override
  public JSType findPropertyType(String propertyName) {
    JSType propertyType = null;

    for (JSType alternate : getAlternates()) {
      // Filter out the null/undefined type.
      if (alternate.isNullType() || alternate.isVoidType()) {
        continue;
      }

      JSType altPropertyType = alternate.findPropertyType(propertyName);
      if (altPropertyType == null) {
        continue;
      }

      if (propertyType == null) {
        propertyType = altPropertyType;
      } else {
        propertyType = propertyType.getLeastSupertype(altPropertyType);
      }
    }

    return propertyType;
  }

  @Override
  public boolean canBeCalled() {
    return allMatch(JSType::canBeCalled, alternatesRetainingStructuralSubtypes);
  }

  @Override
  public JSType autobox() {
    UnionTypeBuilder restricted = UnionTypeBuilder.create(registry);
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType t = alternatesRetainingStructuralSubtypes.get(i);
      restricted.addAlternate(t.autobox());
    }
    return restricted.build();
  }

  @Override
  public JSType restrictByNotNullOrUndefined() {
    UnionTypeBuilder restricted = UnionTypeBuilder.create(registry);
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType t = alternatesRetainingStructuralSubtypes.get(i);
      restricted.addAlternate(t.restrictByNotNullOrUndefined());
    }
    return restricted.build();
  }

  @Override
  public JSType restrictByNotUndefined() {
    UnionTypeBuilder restricted = UnionTypeBuilder.create(registry);
    for (JSType t : alternatesRetainingStructuralSubtypes) {
      restricted.addAlternate(t.restrictByNotUndefined());
    }
    return restricted.build();
  }

  @Override
  public TernaryValue testForEquality(JSType that) {
    TernaryValue result = null;
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType t = alternatesRetainingStructuralSubtypes.get(i);
      TernaryValue test = t.testForEquality(that);
      if (result == null) {
        result = test;
      } else if (!result.equals(test)) {
        return UNKNOWN;
      }
    }
    return result;
  }

  /**
   * This predicate determines whether objects of this type can have the
   * {@code null} value, and therefore can appear in contexts where
   * {@code null} is expected.
   *
   * @return {@code true} for everything but {@code Number} and
   *         {@code Boolean} types.
   */
  @Override
  public boolean isNullable() {
    return anyMatch(JSType::isNullable, alternatesRetainingStructuralSubtypes);
  }

  /**
   * Tests whether this type is voidable.
   */
  @Override
  public boolean isVoidable() {
    return anyMatch(JSType::isVoidable, alternatesRetainingStructuralSubtypes);
  }

  /** Tests whether this type explicitly allows undefined (as opposed to ? or *). */
  @Override
  public boolean isExplicitlyVoidable() {
    return anyMatch(JSType::isExplicitlyVoidable, alternatesRetainingStructuralSubtypes);
  }

  @Override
  public boolean isUnknownType() {
    return anyMatch(JSType::isUnknownType, alternatesRetainingStructuralSubtypes);
  }

  @Override
  public boolean isStruct() {
    return anyMatch(JSType::isStruct, getAlternates());
  }

  @Override
  public boolean isDict() {
    return anyMatch(JSType::isDict, getAlternates());
  }

  @Override
  public JSType getLeastSupertype(JSType that) {
    if (!that.isUnknownType() && !that.isUnionType()) {
      for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
        JSType alternate = alternatesRetainingStructuralSubtypes.get(i);
        if (!alternate.isUnknownType() && that.isSubtypeOf(alternate)) {
          return this;
        }
      }
    }

    return JSType.getLeastSupertype(this, that);
  }

  JSType meet(JSType that) {
    UnionTypeBuilder builder = UnionTypeBuilder.create(registry);
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType alternate = alternatesRetainingStructuralSubtypes.get(i);
      if (alternate.isSubtypeOf(that)) {
        builder.addAlternate(alternate);
      }
    }

    if (that.isUnionType()) {
      List<JSType> thoseAlternatesWithoutStucturalTyping =
          that.toMaybeUnionType().alternatesRetainingStructuralSubtypes;
      for (int i = 0; i < thoseAlternatesWithoutStucturalTyping.size(); i++) {
        JSType otherAlternate = thoseAlternatesWithoutStucturalTyping.get(i);
        if (otherAlternate.isSubtypeOf(this)) {
          builder.addAlternate(otherAlternate);
        }
      }
    } else if (that.isSubtypeOf(this)) {
      builder.addAlternate(that);
    }
    JSType result = builder.build();
    if (!result.isNoType()) {
      return result;
    } else if (this.isObject() && (that.isObject() && !that.isNoType())) {
      return getNativeType(JSTypeNative.NO_OBJECT_TYPE);
    } else {
      return getNativeType(JSTypeNative.NO_TYPE);
    }
  }

  /**
   * Two union types are equal if, after flattening nested union types, they have the same number of
   * alternatesCollapsingStructuralSubtypes and all alternatesCollapsingStructuralSubtypes are
   * equal.
   */
  boolean checkUnionEquivalenceHelper(UnionType that, EquivalenceMethod eqMethod, EqCache eqCache) {
    List<JSType> thatAlternates = that.getAlternatesWithoutStructuralTyping();
    if (eqMethod == EquivalenceMethod.IDENTITY
        && getAlternatesWithoutStructuralTyping().size() != thatAlternates.size()) {
      return false;
    }
    for (int i = 0; i < thatAlternates.size(); i++) {
      JSType thatAlternate = thatAlternates.get(i);
      if (!hasAlternate(thatAlternate, eqMethod, eqCache)) {
        return false;
      }
    }
    return true;
  }

  private boolean hasAlternate(JSType type, EquivalenceMethod eqMethod,
      EqCache eqCache) {
    List<JSType> alternatesRetainingStructuralSubtypes = getAlternatesWithoutStructuralTyping();
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType alternate = alternatesRetainingStructuralSubtypes.get(i);
      if (alternate.checkEquivalenceHelper(type, eqMethod, eqCache)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public HasPropertyKind getPropertyKind(String pname, boolean autobox) {
    boolean found = false;
    boolean always = true;
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType alternate = alternatesRetainingStructuralSubtypes.get(i);
      if (alternate.isNullType() || alternate.isVoidType()) {
        continue;
      }
      switch (alternate.getPropertyKind(pname, autobox)) {
        case KNOWN_PRESENT:
          found = true;
          break;
        case ABSENT:
          always = false;
          break;
        case MAYBE_PRESENT:
          found = true;
          always = false;
          break;
      }
      if (found && !always) {
        break;
      }
    }
    return found
        ? (always ? HasPropertyKind.KNOWN_PRESENT : HasPropertyKind.MAYBE_PRESENT)
        : HasPropertyKind.ABSENT;
  }

  @Override
  final int recursionUnsafeHashCode() {
    int hashCode = alternatesRetainingStructuralSubtypes.size();
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      // To be determinisitic this aggregation must be order-independent. Using a commutative
      // operatator (multiplication) allows us to achieve that without sorting. Multiplication also
      // has some nice properties about reducing collisions compared to addition or xor.
      hashCode *= alternatesRetainingStructuralSubtypes.get(i).hashCode();
    }
    return hashCode;
  }

  @Override
  public UnionType toMaybeUnionType() {
    return this;
  }

  @Override
  public boolean isObject() {
    return allMatch(JSType::isObject, alternatesRetainingStructuralSubtypes);
  }

  /**
   * A {@link UnionType} contains a given type (alternate) iff the member
   * vector contains it.
   *
   * @param type The alternate which might be in this union.
   *
   * @return {@code true} if the alternate is in the union
   */
  public boolean contains(JSType type) {
    return anyMatch(type::isEquivalentTo, alternatesRetainingStructuralSubtypes);
  }

  /**
   * Returns a more restricted union type than {@code this} one, in which all
   * subtypes of {@code type} have been removed.<p>
   *
   * Examples:
   * <ul>
   * <li>{@code (number,string)} restricted by {@code number} is
   *     {@code string}</li>
   * <li>{@code (null, EvalError, URIError)} restricted by
   *     {@code Error} is {@code null}</li>
   * </ul>
   *
   * @param type the supertype of the types to remove from this union type
   */
  public JSType getRestrictedUnion(JSType type) {
    UnionTypeBuilder restricted = UnionTypeBuilder.create(registry);
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType t = alternatesRetainingStructuralSubtypes.get(i);
      // Keep all unknown/unresolved types.
      if (t.isUnknownType() || t.isNoResolvedType() || !t.isSubtypeOf(type)) {
        restricted.addAlternate(t);
      }
    }
    return restricted.build();
  }

  @Override
  StringBuilder appendTo(StringBuilder sb, boolean forAnnotations) {
    sb.append("(");
    // Sort types in character value order in order to get consistent results.
    // This is important for deterministic behavior for testing.
    SortedSet<String> sortedTypeNames = new TreeSet<>();
    for (JSType jsType : alternatesRetainingStructuralSubtypes) {
      sortedTypeNames.add(jsType.appendTo(new StringBuilder(), forAnnotations).toString());
    }
    Joiner.on('|').appendTo(sb, sortedTypeNames);
    return sb.append(")");
  }

  @Override
  public boolean isSubtype(JSType that) {
    return isSubtype(that, ImplCache.create(), SubtypingMode.NORMAL);
  }

  @Override
  protected boolean isSubtype(JSType that,
      ImplCache implicitImplCache, SubtypingMode subtypingMode) {
    // unknown
    if (that.isUnknownType() || this.isUnknownType()) {
      return true;
    }
    // all type
    if (that.isAllType()) {
      return true;
    }
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType element = alternatesRetainingStructuralSubtypes.get(i);
      if (subtypingMode == SubtypingMode.IGNORE_NULL_UNDEFINED
          && (element.isNullType() || element.isVoidType())) {
        continue;
      }
      if (!element.isSubtype(that, implicitImplCache, subtypingMode)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public JSType getRestrictedTypeGivenToBooleanOutcome(boolean outcome) {
    // gather elements after restriction
    UnionTypeBuilder restricted = UnionTypeBuilder.create(registry);
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType element = alternatesRetainingStructuralSubtypes.get(i);
      restricted.addAlternate(
          element.getRestrictedTypeGivenToBooleanOutcome(outcome));
    }
    return restricted.build();
  }

  @Override
  public BooleanLiteralSet getPossibleToBooleanOutcomes() {
    BooleanLiteralSet literals = BooleanLiteralSet.EMPTY;
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType element = alternatesRetainingStructuralSubtypes.get(i);
      literals = literals.union(element.getPossibleToBooleanOutcomes());
      if (literals == BooleanLiteralSet.BOTH) {
        break;
      }
    }
    return literals;
  }

  @Override
  public TypePair getTypesUnderEquality(JSType that) {
    UnionTypeBuilder thisRestricted = UnionTypeBuilder.create(registry);
    UnionTypeBuilder thatRestricted = UnionTypeBuilder.create(registry);
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType element = alternatesRetainingStructuralSubtypes.get(i);
      TypePair p = element.getTypesUnderEquality(that);
      if (p.typeA != null) {
        thisRestricted.addAlternate(p.typeA);
      }
      if (p.typeB != null) {
        thatRestricted.addAlternate(p.typeB);
      }
    }
    return new TypePair(
        thisRestricted.build(),
        thatRestricted.build());
  }

  @Override
  public TypePair getTypesUnderInequality(JSType that) {
    UnionTypeBuilder thisRestricted = UnionTypeBuilder.create(registry);
    UnionTypeBuilder thatRestricted = UnionTypeBuilder.create(registry);
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType element = alternatesRetainingStructuralSubtypes.get(i);
      TypePair p = element.getTypesUnderInequality(that);
      if (p.typeA != null) {
        thisRestricted.addAlternate(p.typeA);
      }
      if (p.typeB != null) {
        thatRestricted.addAlternate(p.typeB);
      }
    }
    return new TypePair(
        thisRestricted.build(),
        thatRestricted.build());
  }

  @Override
  public TypePair getTypesUnderShallowInequality(JSType that) {
    UnionTypeBuilder thisRestricted = UnionTypeBuilder.create(registry);
    UnionTypeBuilder thatRestricted = UnionTypeBuilder.create(registry);
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType element = alternatesRetainingStructuralSubtypes.get(i);
      TypePair p = element.getTypesUnderShallowInequality(that);
      if (p.typeA != null) {
        thisRestricted.addAlternate(p.typeA);
      }
      if (p.typeB != null) {
        thatRestricted.addAlternate(p.typeB);
      }
    }
    return new TypePair(
        thisRestricted.build(),
        thatRestricted.build());
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseUnionType(this);
  }

  @Override <T> T visit(RelationshipVisitor<T> visitor, JSType that) {
    return visitor.caseUnionType(this, that);
  }

  @Override
  JSType resolveInternal(ErrorReporter reporter) {
    setResolvedTypeInternal(this); // for circularly defined types.

    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType alternate = alternatesRetainingStructuralSubtypes.get(i);
      alternate.resolve(reporter);
    }
    // Ensure the union is in a normalized state.
    rebuildAlternates();
    return this;
  }

  @Override
  public String toDebugHashCodeString() {
    List<String> hashCodes = new ArrayList<>();
    for (JSType a : alternatesRetainingStructuralSubtypes) {
      hashCodes.add(a.toDebugHashCodeString());
    }
    return "{(" + Joiner.on(",").join(hashCodes) + ")}";
  }

  @Override
  public boolean setValidator(Predicate<JSType> validator) {
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType a = alternatesRetainingStructuralSubtypes.get(i);
      a.setValidator(validator);
    }
    return true;
  }

  @Override
  public JSType collapseUnion() {
    JSType currentValue = null;
    ObjectType currentCommonSuper = null;
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType a = alternatesRetainingStructuralSubtypes.get(i);
      if (a.isUnknownType()) {
        return getNativeType(JSTypeNative.UNKNOWN_TYPE);
      }

      ObjectType obj = a.toObjectType();
      if (obj == null) {
        if (currentValue == null && currentCommonSuper == null) {
          // If obj is not an object, then it must be a value.
          currentValue = a;
        } else {
          // Multiple values and objects will always collapse to the ALL_TYPE.
          return getNativeType(JSTypeNative.ALL_TYPE);
        }
      } else if (currentValue != null) {
        // Values and objects will always collapse to the ALL_TYPE.
        return getNativeType(JSTypeNative.ALL_TYPE);
      } else if (currentCommonSuper == null) {
        currentCommonSuper = obj;
      } else {
        currentCommonSuper =
            registry.findCommonSuperObject(currentCommonSuper, obj);
      }
    }
    return currentCommonSuper;
  }

  @Override
  public void matchConstraint(JSType constraint) {
    for (int i = 0; i < alternatesRetainingStructuralSubtypes.size(); i++) {
      JSType alternate = alternatesRetainingStructuralSubtypes.get(i);
      alternate.matchConstraint(constraint);
    }
  }

  @Override
  public boolean hasAnyTemplateTypesInternal() {
    return anyMatch(JSType::hasAnyTemplateTypes, alternatesRetainingStructuralSubtypes);
  }

  /**
   * Returns whether anything in {@code universe} matches {@code predicate}.
   *
   * <p>This method is designed to minimize allocations since it is expected to be called
   * <em>very</em> often. That's why is doesn't:
   *
   * <ul>
   *   <li>instantiate {@link Iterator}s
   *   <li>instantiate {@link Stream}s
   *   <li>(un)box primitives
   *   <li>expect closure generating lambdas
   * </ul>
   */
  private static boolean anyMatch(Predicate<JSType> predicate, ImmutableList<JSType> universe) {
    for (int i = 0; i < universe.size(); i++) {
      if (predicate.test(universe.get(i))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns whether everything in {@code universe} matches {@code predicate}.
   *
   * <p>This method is designed to minimize allocations since it is expected to be called
   * <em>very</em> often. That's why is doesn't:
   *
   * <ul>
   *   <li>instantiate {@link Iterator}s
   *   <li>instantiate {@link Stream}s
   *   <li>(un)box primitives
   *   <li>expect closure generating lambdas
   * </ul>
   */
  private static boolean allMatch(Predicate<JSType> predicate, ImmutableList<JSType> universe) {
    for (int i = 0; i < universe.size(); i++) {
      if (!predicate.test(universe.get(i))) {
        return false;
      }
    }
    return true;
  }
}
