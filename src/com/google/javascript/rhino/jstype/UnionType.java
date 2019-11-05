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

import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.rhino.jstype.JSTypeIterations.allTypesMatch;
import static com.google.javascript.rhino.jstype.JSTypeIterations.anyTypeMatches;
import static com.google.javascript.rhino.jstype.JSTypeIterations.mapTypes;
import static com.google.javascript.rhino.jstype.JSTypeNative.ALL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.CHECKED_UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;
import static com.google.javascript.rhino.jstype.TernaryValue.UNKNOWN;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.ErrorReporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A type that may be any one of a set of types, and thus has the intersection of the properties of
 * those types.
 *
 * <p>The {@code UnionType} implements a common JavaScript idiom in which the code is specifically
 * designed to work with multiple input types. Because JavaScript always knows the run-time type of
 * an object value, this is safer than a C union.
 *
 * <p>For instance, values of the union type {@code (String,boolean)} can be of type {@code String}
 * or of type {@code boolean}. The commutativity of the statement is captured by making {@code
 * (String,boolean)} and {@code (boolean,String)} equal.
 *
 * <p>The implementation of this class prevents the creation of nested unions.
 */
public class UnionType extends JSType {
  private static final long serialVersionUID = 2L;

  /**
   * Generally, if the best we can do is say "this object is one of thirty things", then we should
   * just give up and admit that we have no clue.
   */
  private static final int DEFAULT_MAX_UNION_SIZE = 30;

  /**
   * A special case maximum size for use in the type registry.
   *
   * <p>The registry uses a union type to track all the types that have a given property. In this
   * scenario, there can be <em>many</em> alternates but it's still valuable to differentiate them.
   *
   * <p>This value was semi-randomly selected based on the Google+ FE project.
   */
  private static final int PROPERTY_CHECKING_MAX_UNION_SIZE = 3000;

  // NOTE: to avoid allocating iterators, all the loops below iterate over alternates by index
  // instead of using the for-each loop idiom.


  private ImmutableList<JSType> alternates;

  /**
   * Creates a union.
   *
   * <p>This ctor is private because all instances are created using a {@link Builder}. The builder
   * is also responsible for setting the alternates, which is why they aren't passed as a parameter.
   */
  private UnionType(JSTypeRegistry registry) {
    super(registry);
  }

  /** Creates a {@link Builder} for a new {@link UnionType}. */
  public static Builder builder(JSTypeRegistry registry) {
    return new Builder(registry, DEFAULT_MAX_UNION_SIZE);
  }

  /**
   * Creates a {@link Builder} for a new {@link UnionType}.
   *
   * <p>This is only supposed to be used within `JSTypeRegistry`.
   */
  static Builder builderForPropertyChecking(JSTypeRegistry registry) {
    return new Builder(registry, PROPERTY_CHECKING_MAX_UNION_SIZE);
  }

  /**
   * Gets the alternate types of this union type.
   *
   * @return The alternate types of this union type. The returned set is immutable.
   */
  public ImmutableList<JSType> getAlternates() {
    if (!this.isResolved() && anyTypeMatches(JSType::isUnionType, this.alternates)) {
      rebuildAlternates();
    }
    return alternates;
  }

  /** Use a {@link Builder} to rebuild the list of alternates. */
  private void rebuildAlternates() {
    setAlternates(
        new Builder(this, DEFAULT_MAX_UNION_SIZE).addAlternates(this.alternates).buildInternal());
  }

  private UnionType setAlternates(ImmutableList<JSType> alternates) {
    checkState(!alternates.isEmpty());
    this.alternates = alternates;
    return this;
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
    return anyTypeMatches(JSType::matchesNumberContext, this);
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
    return anyTypeMatches(JSType::matchesStringContext, this);
  }

  /**
   * This predicate is used to test whether a given type can appear in a {@code Symbol} context
   *
   * @return {@code true} if not it maybe a symbol or Symbol object
   */
  @Override
  public boolean matchesSymbolContext() {
    // TODO(user): Reverse this logic to make it correct instead of generous.
    return anyTypeMatches(JSType::matchesSymbolContext, this);
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
    return anyTypeMatches(JSType::matchesObjectContext, this);
  }

  @Override
  protected JSType findPropertyTypeWithoutConsideringTemplateTypes(String propertyName) {
    JSType propertyType = null;

    for (JSType alternate : alternates) {
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
    return allTypesMatch(JSType::canBeCalled, this);
  }

  @Override
  public JSType autobox() {
    return mapTypes(JSType::autobox, this);
  }

  @Override
  public JSType restrictByNotNullOrUndefined() {
    return mapTypes(JSType::restrictByNotNullOrUndefined, this);
  }

  @Override
  public JSType restrictByNotUndefined() {
    return mapTypes(JSType::restrictByNotUndefined, this);
  }

  @Override
  public JSType restrictByNotNull() {
    return mapTypes(JSType::restrictByNotNull, this);
  }

  @Override
  public TernaryValue testForEquality(JSType that) {
    TernaryValue result = null;
    for (int i = 0; i < alternates.size(); i++) {
      JSType t = alternates.get(i);
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
    return anyTypeMatches(JSType::isNullable, this);
  }

  /**
   * Tests whether this type is voidable.
   */
  @Override
  public boolean isVoidable() {
    return anyTypeMatches(JSType::isVoidable, this);
  }

  /** Tests whether this type explicitly allows undefined (as opposed to ? or *). */
  @Override
  public boolean isExplicitlyVoidable() {
    return anyTypeMatches(JSType::isExplicitlyVoidable, this);
  }

  @Override
  public boolean isUnknownType() {
    return anyTypeMatches(JSType::isUnknownType, this);
  }

  @Override
  public boolean isStruct() {
    return anyTypeMatches(JSType::isStruct, this);
  }

  @Override
  public boolean isDict() {
    return anyTypeMatches(JSType::isDict, this);
  }

  @Override
  public JSType getLeastSupertype(JSType that) {
    if (!that.isUnknownType() && !that.isUnionType()) {
      for (int i = 0; i < alternates.size(); i++) {
        JSType alternate = alternates.get(i);
        if (!alternate.isUnknownType() && that.isSubtypeOf(alternate)) {
          return this;
        }
      }
    }

    return JSType.getLeastSupertype(this, that);

  }

  static JSType getGreatestSubtype(UnionType union, JSType that) {
    // This method is implemented as a static because we don't want polymorphism. Ideally all the
    // `greatestSubtype` code would be in one place. Until then, using static calls minimizes
    // confusion.

    JSTypeRegistry registry = union.registry;
    Builder builder = builder(registry);

    for (int i = 0; i < union.alternates.size(); i++) {
      JSType alternate = union.alternates.get(i);
      if (alternate.isSubtypeOf(that)) {
        builder.addAlternate(alternate);
      }
    }

    if (that.isUnionType()) {
      List<JSType> thoseAlternates = that.toMaybeUnionType().getAlternates();
      for (int i = 0; i < thoseAlternates.size(); i++) {
        JSType otherAlternate = thoseAlternates.get(i);
        if (otherAlternate.isSubtypeOf(union)) {
          builder.addAlternate(otherAlternate);
        }
      }
    } else if (that.isSubtypeOf(union)) {
      builder.addAlternate(that);
    }

    JSType result = builder.build();
    if (!result.isNoType()) {
      return result;
    } else if (union.isObject() && (that.isObject() && !that.isNoType())) {
      return registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE);
    } else {
      return registry.getNativeType(JSTypeNative.NO_TYPE);
    }
  }

  /**
   * Two union types are equal if, after flattening nested union types, they have the same number of
   * alternates and all alternates are equal.
   */
  boolean checkUnionEquivalenceHelper(UnionType that, EquivalenceMethod eqMethod, EqCache eqCache) {
    List<JSType> thatAlternates = that.getAlternates();
    if (eqMethod == EquivalenceMethod.IDENTITY && alternates.size() != thatAlternates.size()) {
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

  private boolean hasAlternate(JSType type, EquivalenceMethod eqMethod, EqCache eqCache) {
    for (int i = 0; i < alternates.size(); i++) {
      JSType alternate = alternates.get(i);
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
    for (int i = 0; i < alternates.size(); i++) {
      JSType alternate = alternates.get(i);
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
    int hashCode = alternates.size();
    for (int i = 0; i < alternates.size(); i++) {
      // To be determinisitic this aggregation must be order-independent. Using a commutative
      // operatator (multiplication) allows us to achieve that without sorting. Multiplication also
      // has some nice properties about reducing collisions compared to addition or xor.
      hashCode *= alternates.get(i).hashCode();
    }
    return hashCode;
  }

  @Override
  public UnionType toMaybeUnionType() {
    return this;
  }

  @Override
  public boolean isObject() {
    return allTypesMatch(JSType::isObject, this);
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
    return anyTypeMatches(type::isEquivalentTo, this);
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
    Builder restricted = builder(registry);
    for (int i = 0; i < alternates.size(); i++) {
      JSType t = alternates.get(i);
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
    for (JSType jsType : alternates) {
      sortedTypeNames.add(jsType.appendTo(new StringBuilder(), forAnnotations).toString());
    }
    Joiner.on('|').appendTo(sb, sortedTypeNames);
    return sb.append(")");
  }

  @Override
  public JSType getRestrictedTypeGivenToBooleanOutcome(boolean outcome) {
    return mapTypes((t) -> t.getRestrictedTypeGivenToBooleanOutcome(outcome), this);
  }

  @Override
  public BooleanLiteralSet getPossibleToBooleanOutcomes() {
    BooleanLiteralSet literals = BooleanLiteralSet.EMPTY;
    for (int i = 0; i < alternates.size(); i++) {
      JSType element = alternates.get(i);
      literals = literals.union(element.getPossibleToBooleanOutcomes());
      if (literals == BooleanLiteralSet.BOTH) {
        break;
      }
    }
    return literals;
  }

  @Override
  public TypePair getTypesUnderEquality(JSType that) {
    Builder thisRestricted = builder(registry);
    Builder thatRestricted = builder(registry);
    for (int i = 0; i < alternates.size(); i++) {
      JSType element = alternates.get(i);
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
    Builder thisRestricted = builder(registry);
    Builder thatRestricted = builder(registry);
    for (int i = 0; i < alternates.size(); i++) {
      JSType element = alternates.get(i);
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
    Builder thisRestricted = builder(registry);
    Builder thatRestricted = builder(registry);
    for (int i = 0; i < alternates.size(); i++) {
      JSType element = alternates.get(i);
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

    for (int i = 0; i < alternates.size(); i++) {
      JSType alternate = alternates.get(i);
      alternate.resolve(reporter);
    }
    // Ensure the union is in a normalized state.
    rebuildAlternates();

    if (alternates.size() == 1) {
      return alternates.get(0);
    }
    return this;
  }

  @Override
  public String toDebugHashCodeString() {
    List<String> hashCodes = new ArrayList<>();
    for (JSType a : alternates) {
      hashCodes.add(a.toDebugHashCodeString());
    }
    return "{(" + Joiner.on(",").join(hashCodes) + ")}";
  }

  @Override
  public boolean setValidator(Predicate<JSType> validator) {
    for (int i = 0; i < alternates.size(); i++) {
      JSType a = alternates.get(i);
      a.setValidator(validator);
    }
    return true;
  }

  @Override
  public JSType collapseUnion() {
    JSType currentValue = null;
    ObjectType currentCommonSuper = null;
    for (int i = 0; i < alternates.size(); i++) {
      JSType a = alternates.get(i);
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
    for (int i = 0; i < alternates.size(); i++) {
      JSType alternate = alternates.get(i);
      alternate.matchConstraint(constraint);
    }
  }

  @Override
  public boolean hasAnyTemplateTypesInternal() {
    return anyTypeMatches(JSType::hasAnyTemplateTypes, this);
  }

  /**
   * Implements type unioning logic, since {@link UnionType}s only actually need to perform unioning
   * operations when being (re)built.
   *
   * <p>{@link Builder}s exist in two forms. One for assembing a new union and one for updating an
   * existing union. Only the former is exposed.
   *
   * <p>Most users of this class should prefer {@link JSTypeRegistry#createUnionType} instead.
   */
  public static final class Builder {
    private final UnionType rebuildTarget;
    private final JSTypeRegistry registry;
    private final int maxUnionSize;

    private final List<JSType> alternates = new ArrayList<>();
    // If a union has ? or *, we do not care about any other types, except for undefined (for
    // optional
    // properties).
    private boolean containsVoidType = false;
    private boolean isAllType = false;
    private boolean isNativeUnknownType = false;
    private boolean areAllUnknownsChecked = true;

    // Every UnionType may have at most one structural function in it.
    //
    // NOTE(nicksantos): I've read some literature that says that type-inferenced
    // languages are fundamentally incompatible with union types. I refuse
    // to believe this. But they do make the type lattice much more complicated.
    //
    // For this reason, when we deal with function types, we actually merge some
    // nodes on the lattice, and treat them as fundamentally equivalent.
    // For example, we treat
    // function(): string | function(): number
    // as equivalent to
    // function(): (string|number)
    // and normalize the first type into the second type.
    //
    // To perform this normalization, we've modified Builder to disallow
    // multiple structural functions in a union. We always delegate to
    // FunctionType::getLeastSupertype, which either merges the functions into
    // one structural function, or just bails out and uses the top function type.
    private int functionTypePosition = -1;

    private boolean hasBuilt = false;

    /** Creates a builder for a new union. */
    private Builder(JSTypeRegistry registry, int maxUnionSize) {
      this.rebuildTarget = null;
      this.registry = registry;
      this.maxUnionSize = maxUnionSize;
    }

    /** Creates a re-builder for an existing union. */
    private Builder(UnionType rebuildTarget, int maxUnionSize) {
      this.rebuildTarget = rebuildTarget;
      this.registry = rebuildTarget.registry;
      this.maxUnionSize = maxUnionSize;
    }

    private static boolean isSubtype(JSType rightType, JSType leftType) {
      return rightType.isSubtypeWithoutStructuralTyping(leftType);
    }

    public Builder addAlternates(Collection<? extends JSType> c) {
      for (JSType type : c) {
        addAlternate(type);
      }
      return this;
    }

    // A specific override that avoid creating an iterator.  This version is currently used when
    // adding a union as an alternate.
    public Builder addAlternates(List<? extends JSType> list) {
      for (int i = 0; i < list.size(); i++) {
        addAlternate(list.get(i));
      }
      return this;
    }

    /**
     * Adds an alternate to the union type under construction.
     *
     * <p>Returns this for easy chaining.
     */
    public Builder addAlternate(JSType alternate) {
      checkHasNotBuilt();

      // build() returns the bottom type by default, so we can
      // just bail out early here.
      if (alternate.isNoType()) {
        return this;
      }

      isAllType = isAllType || alternate.isAllType();
      containsVoidType = containsVoidType || alternate.isVoidType();

      boolean isAlternateUnknown = alternate instanceof UnknownType;
      isNativeUnknownType = isNativeUnknownType || isAlternateUnknown;
      if (isAlternateUnknown) {
        areAllUnknownsChecked = areAllUnknownsChecked && alternate.isCheckedUnknownType();
      }

      if (isAllType || isNativeUnknownType) {
        return this;
      }
      if (alternate.isUnionType()) {
        addAlternates(alternate.toMaybeUnionType().getAlternates());
        return this;
      }
      if (alternates.size() > maxUnionSize) {
        return this;
      }

      // Function types are special, because they have their
      // own bizarre sub-lattice. See the comments on
      // FunctionType#supAndInf helper and above at functionTypePosition.
      if (alternate.isFunctionType() && functionTypePosition != -1) {
        // See the comments on functionTypePosition above.
        FunctionType other = alternates.get(functionTypePosition).toMaybeFunctionType();
        FunctionType supremum = alternate.toMaybeFunctionType().supAndInfHelper(other, true);
        alternates.set(functionTypePosition, supremum);
        return this;
      }

      // Look through the alternates we've got so far,
      // and check if any of them are duplicates of
      // one another.
      for (int index = 0; index < alternates.size(); index++) {
        boolean removeCurrent = false;
        JSType current = alternates.get(index);

        // Unknown and NoResolved types may just be names that haven't
        // been resolved yet. So keep these in the union, and just use
        // equality checking for simple de-duping.
        if (alternate.isUnknownType()
            || current.isUnknownType()
            || alternate.isNoResolvedType()
            || current.isNoResolvedType()
            || alternate.hasAnyTemplateTypes()
            || current.hasAnyTemplateTypes()) {
          if (alternate.isEquivalentTo(current, false)) {
            // Alternate is unnecessary.
            return this;
          }
        } else if (alternate.isTemplatizedType() || current.isTemplatizedType()) {
          // Because "Foo" and "Foo<?>" are roughly equivalent
          // templatized types, special care is needed when building the
          // union. For example:
          //   Object is consider a subtype of Object<string>
          // but we want to leave "Object" not "Object<string>" when
          // building the subtype.
          //

          // Cases:
          // 1) alternate:Array<string> and current:Object ==> Object
          // 2) alternate:Array<string> and current:Array ==> Array
          // 3) alternate:Object<string> and
          //    current:Array ==> Array|Object<string>
          // 4) alternate:Object and current:Array<string> ==> Object
          // 5) alternate:Array and current:Array<string> ==> Array
          // 6) alternate:Array and
          //    current:Object<string> ==> Array|Object<string>
          // 7) alternate:Array<string> and
          //    current:Array<number> ==> Array<?>
          // 8) alternate:Array<string> and
          //    current:Array<string> ==> Array<string>
          // 9) alternate:Array<string> and
          //    current:Object<string> ==> Object<string>|Array<string>

          if (!current.isTemplatizedType()) {
            if (isSubtype(alternate, current)) {
              // case 1, 2
              return this;
              }
            // case 3: leave current, add alternate
          } else if (!alternate.isTemplatizedType()) {
            if (isSubtype(current, alternate)) {
              // case 4, 5
              removeCurrent = true;
            }
            // case 6: leave current, add alternate
          } else {
            checkState(current.isTemplatizedType() && alternate.isTemplatizedType());
            TemplatizedType templatizedAlternate = alternate.toMaybeTemplatizedType();
            TemplatizedType templatizedCurrent = current.toMaybeTemplatizedType();

            if (templatizedCurrent.wrapsSameRawType(templatizedAlternate)) {
              if (alternate
                  .getTemplateTypeMap()
                  .checkEquivalenceHelper(
                      current.getTemplateTypeMap(),
                      EquivalenceMethod.IDENTITY,
                      SubtypingMode.NORMAL)) {
                // case 8
                return this;
              } else {
                // case 7: replace with a merged alternate specialized on `?`.
                ObjectType rawType = templatizedCurrent.getReferencedObjTypeInternal();
                // Providing no type-parameter values specializes `rawType` on `?` by default.
                alternate = registry.createTemplatizedType(rawType, ImmutableList.of());
                    removeCurrent = true;
                  }
            }
            // case 9: leave current, add alternate
          }
          // Otherwise leave both templatized types.
        } else if (isSubtype(alternate, current)) {
          // Alternate is unnecessary.
          mayRegisterDroppedProperties(alternate, current);
          return this;
        } else if (isSubtype(current, alternate)) {
          // Alternate makes current obsolete
          mayRegisterDroppedProperties(current, alternate);
          removeCurrent = true;
        }

        if (removeCurrent) {
          alternates.remove(index);

          if (index == functionTypePosition) {
            functionTypePosition = -1;
          } else if (index < functionTypePosition) {
            functionTypePosition--;
          }

          index--;
        }
      }

      if (alternate.isFunctionType()) {
        // See the comments on functionTypePosition above.
        checkState(functionTypePosition == -1);
        functionTypePosition = alternates.size();
      }

      alternates.add(alternate);
      return this;
    }

    private void mayRegisterDroppedProperties(JSType subtype, JSType supertype) {
      if (subtype.toMaybeRecordType() != null && supertype.toMaybeRecordType() != null) {
        registry.registerDroppedPropertiesInUnion(
            subtype.toMaybeRecordType(), supertype.toMaybeRecordType());
      }
    }

    /**
     * Returns a type, not necessarily a {@link UnionType}, that represents the union of the inputs.
     *
     * <p>The {@link Builder} cannot be used again once this method is called.
     */
    public JSType build() {
      checkState(rebuildTarget == null);

      ImmutableList<JSType> alternates = buildInternal();
      if (alternates.size() == 1) {
        return alternates.get(0);
      } else {
        return new UnionType(registry).setAlternates(alternates);
      }
    }

    /** Create the final set of alternates for either a new union or a union being rebuilt. */
    private ImmutableList<JSType> buildInternal() {
      checkHasNotBuilt();
      this.hasBuilt = true;

      JSType wildcard = getNativeWildcardType();
      if (wildcard != null) {
        if (containsVoidType) {
          return ImmutableList.of(wildcard, registry.getNativeType(VOID_TYPE));
        } else {
          return ImmutableList.of(wildcard);
        }
      }

      if (alternates.isEmpty()) {
        // To simplify the typesystem, empty union types are forbidden. Using a single `bottom`
        // makes it essentially a proxy instead.
        return ImmutableList.of(registry.getNativeType(NO_TYPE));
      } else if (alternates.size() > maxUnionSize) {
        return ImmutableList.of(registry.getNativeType(UNKNOWN_TYPE));
      } else {
        return ImmutableList.copyOf(alternates);
      }
    }

    /** Returns ALL_TYPE, UNKNOWN_TYPE, CHECKED_UNKNOWN_TYPE, or null as specified by the flags. */
    @Nullable
    private JSType getNativeWildcardType() {
      if (isAllType) {
        return registry.getNativeType(ALL_TYPE);
      } else if (isNativeUnknownType) {
        if (areAllUnknownsChecked) {
          return registry.getNativeType(CHECKED_UNKNOWN_TYPE);
        } else {
          return registry.getNativeType(UNKNOWN_TYPE);
        }
      }
      return null;
    }

    private void checkHasNotBuilt() {
      checkState(!this.hasBuilt, "Cannot reuse a `UnionType.Builder` that has already filled.");
    }
  }
}
