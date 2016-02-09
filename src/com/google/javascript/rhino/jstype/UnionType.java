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
import com.google.javascript.rhino.ErrorReporter;

import java.util.ArrayList;
import java.util.Collection;
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
 * The implementation of this class prevents the creation of nested
 * unions.<p>
 */
public class UnionType extends JSType {
  private static final long serialVersionUID = 1L;

  // alternates without merging structural interfaces and their subtypes
  Collection<JSType> alternatesWithoutStucturalTyping;
  // alternates under structural typing
  Collection<JSType> alternates;
  private int hashcode;

  /**
   * Creates a union type.
   *
   * @param alternatesWithoutStructuralTyping the alternates of the union without
   * structural typing subtype
   */
  UnionType(JSTypeRegistry registry,
      Collection<JSType> alternatesWithoutStructuralTyping) {
    super(registry);
    this.alternatesWithoutStucturalTyping = alternatesWithoutStructuralTyping;

    UnionTypeBuilder builder = new UnionTypeBuilder(registry);
    for (JSType alternate : alternatesWithoutStructuralTyping) {
      builder.addAlternate(alternate, true);
    }
    this.alternates = builder.getAlternates();
    this.hashcode = this.alternatesWithoutStucturalTyping.hashCode();
  }

  /**
   * Gets the alternate types of this union type.
   * @return The alternate types of this union type. The returned set is
   *     immutable.
   */
  public Collection<JSType> getAlternates() {
    for (JSType t : alternatesWithoutStucturalTyping) {
      if (t.isUnionType()) {
        rebuildAlternates();
        break;
      }
    }
    return alternates;
  }

  /**
   * Gets the alternate types of this union type, including structural interfaces
   *  and implicit implementations as are distinct alternates.
   * @return The alternate types of this union type. The returned set is
   *     immutable.
   */
  public Collection<JSType> getAlternatesWithoutStructuralTyping() {
    for (JSType t : alternatesWithoutStucturalTyping) {
      if (t.isUnionType()) {
        rebuildAlternates();
        break;
      }
    }
    return alternatesWithoutStucturalTyping;
  }

  /**
   * Use UnionTypeBuilder to rebuild the list of alternates and hashcode
   * of the current UnionType.
   */
  private void rebuildAlternates() {
    UnionTypeBuilder builder = new UnionTypeBuilder(registry);
    for (JSType alternate : alternatesWithoutStucturalTyping) {
      builder.addAlternate(alternate);
    }
    alternatesWithoutStucturalTyping = builder.getAlternates();
    builder = new UnionTypeBuilder(registry);
    for (JSType alternate : alternatesWithoutStucturalTyping) {
      builder.addAlternate(alternate, true);
    }
    alternates = builder.getAlternates();
    hashcode = alternatesWithoutStucturalTyping.hashCode();
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
    for (JSType t : alternatesWithoutStucturalTyping) {
      if (t.matchesNumberContext()) {
        return true;
      }
    }
    return false;
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
    for (JSType t : alternatesWithoutStucturalTyping) {
      if (t.matchesStringContext()) {
        return true;
      }
    }
    return false;
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
    for (JSType t : alternatesWithoutStucturalTyping) {
      if (t.matchesObjectContext()) {
        return true;
      }
    }
    return false;
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
    for (JSType t : alternatesWithoutStucturalTyping) {
      if (!t.canBeCalled()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public JSType autobox() {
    UnionTypeBuilder restricted = new UnionTypeBuilder(registry);
    for (JSType t : alternatesWithoutStucturalTyping) {
      restricted.addAlternate(t.autobox());
    }
    return restricted.build();
  }

  @Override
  public JSType restrictByNotNullOrUndefined() {
    UnionTypeBuilder restricted = new UnionTypeBuilder(registry);
    for (JSType t : alternatesWithoutStucturalTyping) {
      restricted.addAlternate(t.restrictByNotNullOrUndefined());
    }
    return restricted.build();
  }

  @Override
  public TernaryValue testForEquality(JSType that) {
    TernaryValue result = null;
    for (JSType t : alternatesWithoutStucturalTyping) {
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
    for (JSType t : alternatesWithoutStucturalTyping) {
      if (t.isNullable()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Tests whether this type is voidable.
   */
  @Override
  public boolean isVoidable() {
    for (JSType t : alternatesWithoutStucturalTyping) {
      if (t.isVoidable()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Tests whether this type explicitly allows undefined.  (as opposed to ? or *)
   */
  @Override
  public boolean isExplicitlyVoidable() {
    for (JSType t : alternatesWithoutStucturalTyping) {
      if (t.isExplicitlyVoidable()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isUnknownType() {
    for (JSType t : alternatesWithoutStucturalTyping) {
      if (t.isUnknownType()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isStruct() {
    for (JSType typ : getAlternates()) {
      if (typ.isStruct()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isDict() {
    for (JSType typ : getAlternates()) {
      if (typ.isDict()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public JSType getLeastSupertype(JSType that) {
    if (!that.isUnknownType() && !that.isUnionType()) {
      for (JSType alternate : alternatesWithoutStucturalTyping) {
        if (!alternate.isUnknownType() && that.isSubtype(alternate)) {
          return this;
        }
      }
    }

    return getLeastSupertype(this, that);
  }

  JSType meet(JSType that) {
    UnionTypeBuilder builder = new UnionTypeBuilder(registry);
    for (JSType alternate : alternatesWithoutStucturalTyping) {
      if (alternate.isSubtype(that)) {
        builder.addAlternate(alternate);
      }
    }

    if (that.isUnionType()) {
      for (JSType otherAlternate : that.toMaybeUnionType().alternatesWithoutStucturalTyping) {
        if (otherAlternate.isSubtype(this)) {
          builder.addAlternate(otherAlternate);
        }
      }
    } else if (that.isSubtype(this)) {
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
   * Two union types are equal if, after flattening nested union types,
   * they have the same number of alternates and all alternates are equal.
   */
  boolean checkUnionEquivalenceHelper(
      UnionType that, EquivalenceMethod eqMethod, EqCache eqCache) {
    Collection<JSType> thatAlternates = that.getAlternatesWithoutStructuralTyping();
    if (eqMethod == EquivalenceMethod.IDENTITY
        && getAlternatesWithoutStructuralTyping().size() != thatAlternates.size()) {
      return false;
    }
    for (JSType alternate : thatAlternates) {
      if (!hasAlternate(alternate, eqMethod, eqCache)) {
        return false;
      }
    }
    return true;
  }

  private boolean hasAlternate(JSType type, EquivalenceMethod eqMethod,
      EqCache eqCache) {
    for (JSType alternate : getAlternatesWithoutStructuralTyping()) {
      if (alternate.checkEquivalenceHelper(type, eqMethod, eqCache)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasProperty(String pname) {
    for (JSType alternate : alternatesWithoutStucturalTyping) {
      if (alternate.hasProperty(pname)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    return this.hashcode;
  }

  @Override
  public UnionType toMaybeUnionType() {
    return this;
  }

  @Override
  public boolean isObject() {
    for (JSType alternate : alternatesWithoutStucturalTyping) {
      if (!alternate.isObject()) {
        return false;
      }
    }
    return true;
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
    for (JSType alt : alternatesWithoutStucturalTyping) {
      if (alt.isEquivalentTo(type)) {
        return true;
      }
    }
    return false;
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
    UnionTypeBuilder restricted = new UnionTypeBuilder(registry);
    for (JSType t : alternatesWithoutStucturalTyping) {
      // Keep all unknown/unresolved types.
      if (t.isUnknownType() || t.isNoResolvedType() || !t.isSubtype(type)) {
        restricted.addAlternate(t);
      }
    }
    return restricted.build();
  }

  @Override String toStringHelper(boolean forAnnotations) {
    StringBuilder result = new StringBuilder();
    boolean firstAlternate = true;

    result.append("(");
    SortedSet<JSType> sorted = new TreeSet<>(ALPHA);
    sorted.addAll(alternatesWithoutStucturalTyping);
    for (JSType t : sorted) {
      if (!firstAlternate) {
        result.append("|");
      }
      result.append(t.toStringHelper(forAnnotations));
      firstAlternate = false;
    }
    result.append(")");
    return result.toString();
  }

  @Override
  public boolean isSubtype(JSType that) {
    return isSubtype(that, ImplCache.create());
  }

  @Override
  protected boolean isSubtype(JSType that,
      ImplCache implicitImplCache) {
    // unknown
    if (that.isUnknownType() || this.isUnknownType()) {
      return true;
    }
    // all type
    if (that.isAllType()) {
      return true;
    }
    for (JSType element : alternatesWithoutStucturalTyping) {
      if (!element.isSubtype(that, implicitImplCache)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public JSType getRestrictedTypeGivenToBooleanOutcome(boolean outcome) {
    // gather elements after restriction
    UnionTypeBuilder restricted = new UnionTypeBuilder(registry);
    for (JSType element : alternatesWithoutStucturalTyping) {
      restricted.addAlternate(
          element.getRestrictedTypeGivenToBooleanOutcome(outcome));
    }
    return restricted.build();
  }

  @Override
  public BooleanLiteralSet getPossibleToBooleanOutcomes() {
    BooleanLiteralSet literals = BooleanLiteralSet.EMPTY;
    for (JSType element : alternatesWithoutStucturalTyping) {
      literals = literals.union(element.getPossibleToBooleanOutcomes());
      if (literals == BooleanLiteralSet.BOTH) {
        break;
      }
    }
    return literals;
  }

  @Override
  public TypePair getTypesUnderEquality(JSType that) {
    UnionTypeBuilder thisRestricted = new UnionTypeBuilder(registry);
    UnionTypeBuilder thatRestricted = new UnionTypeBuilder(registry);
    for (JSType element : alternatesWithoutStucturalTyping) {
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
    UnionTypeBuilder thisRestricted = new UnionTypeBuilder(registry);
    UnionTypeBuilder thatRestricted = new UnionTypeBuilder(registry);
    for (JSType element : alternatesWithoutStucturalTyping) {
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
    UnionTypeBuilder thisRestricted = new UnionTypeBuilder(registry);
    UnionTypeBuilder thatRestricted = new UnionTypeBuilder(registry);
    for (JSType element : alternatesWithoutStucturalTyping) {
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
  JSType resolveInternal(ErrorReporter t, StaticTypedScope<JSType> scope) {
    setResolvedTypeInternal(this); // for circularly defined types.

    // Just resolve the alternates, but do not update as that breaks some error
    // reporting cases.
    for (JSType alternate : alternatesWithoutStucturalTyping) {
      alternate.resolve(t, scope);
    }
    // Ensure the union is in a normalized state.
    rebuildAlternates();
    return this;
  }

  @Override
  public String toDebugHashCodeString() {
    List<String> hashCodes = new ArrayList<>();
    for (JSType a : alternatesWithoutStucturalTyping) {
      hashCodes.add(a.toDebugHashCodeString());
    }
    return "{(" + Joiner.on(",").join(hashCodes) + ")}";
  }

  @Override
  public boolean setValidator(Predicate<JSType> validator) {
    for (JSType a : alternatesWithoutStucturalTyping) {
      a.setValidator(validator);
    }
    return true;
  }

  @Override
  public JSType collapseUnion() {
    JSType currentValue = null;
    ObjectType currentCommonSuper = null;
    for (JSType a : alternatesWithoutStucturalTyping) {
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
    for (JSType alternate : alternatesWithoutStucturalTyping) {
      alternate.matchConstraint(constraint);
    }
  }

  @Override
  public boolean hasAnyTemplateTypesInternal() {
    for (JSType alternate : alternatesWithoutStucturalTyping) {
      if (alternate.hasAnyTemplateTypes()) {
        return true;
      }
    }
    return false;
  }
}
