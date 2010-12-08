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

import com.google.common.base.Predicate;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.jstype.JSTypeRegistry.ResolveMode;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Represents JavaScript value types.<p>
 *
 * Types are split into two separate families: value types and object types.
 *
 * A special {@link UnknownType} exists to represent a wildcard type on which
 * no information can be gathered. In particular, it can assign to everyone,
 * is a subtype of everyone (and everyone is a subtype of it).<p>
 *
 * If you remove the {@link UnknownType}, the set of types in the type system
 * forms a lattice with the {@link #isSubtype} relation defining the partial
 * order of types. All types are united at the top of the lattice by the
 * {@link AllType} and at the bottom by the {@link NoType}.<p>
 *
 */
public abstract class JSType implements Serializable {
  private static final long serialVersionUID = 1L;

  private boolean resolved = false;
  private JSType resolveResult = null;

  public static final String UNKNOWN_NAME =
      "Unknown class name";

  public static final String NOT_A_CLASS =
      "Not declared as a constructor";

  public static final String NOT_A_TYPE =
      "Not declared as a type name";

  public static final String EMPTY_TYPE_COMPONENT =
      "Named type with empty name component";

  /**
   * Total ordering on types based on their textual representation.
   * This is used to have a deterministic output of the toString
   * method of the union type since this output is used in tests.
   */
  static final Comparator<JSType> ALPHA = new Comparator<JSType>() {
    public int compare(JSType t1, JSType t2) {
      return t1.toString().compareTo(t2.toString());
    }
  };

  // A flag set on enum definition tree nodes
  public static final int ENUMDECL = 1;
  public static final int NOT_ENUMDECL = 0;

  final JSTypeRegistry registry;

  JSType(JSTypeRegistry registry) {
    this.registry = registry;
  }

  /**
   * Utility method for less verbose code.
   */
  JSType getNativeType(JSTypeNative typeId) {
    return registry.getNativeType(typeId);
  }

  /**
   * Gets the docInfo for this type. By default, documentation cannot be
   * attached to arbitrary types. This must be overridden for
   * programmer-defined types.
   */
  public JSDocInfo getJSDocInfo() {
    return null;
  }

  /**
   * Returns a user meaningful label for the JSType instance.  For example,
   * Functions and Enums will return their declaration name (if they have one).
   * Some types will not have a meaningful display name.  Calls to
   * hasDisplayName() will return true IFF getDisplayName() will return null
   * or a zero length string.
   *
   * @return the display name of the type, or null if one is not available
   */
  public String getDisplayName() {
    return null;
  }

  /**
   * @return true if the JSType has a user meaningful label.
   */
  public boolean hasDisplayName() {
    String displayName = getDisplayName();
    return displayName != null && !displayName.isEmpty();
  }

  /**
   * If we see a type name without braces, it might be legacy jsdoc.
   * So we shouldn't emit warnings about it. This method is how we skip
   * those warnings.
   */
  void forgiveUnknownNames() {}

  public boolean isNoType() {
    return false;
  }

  public boolean isNoObjectType() {
    return false;
  }

  public final boolean isEmptyType() {
    return isNoType() || isNoObjectType();
  }

  public boolean isNumberObjectType() {
    return false;
  }

  public boolean isNumberValueType() {
    return false;
  }

  /** Whether this is the prototype of a function. */
  public boolean isFunctionPrototypeType() {
    return false;
  }

  public boolean isStringObjectType() {
    return false;
  }

  boolean isTheObjectType() {
    return false;
  }

  public boolean isStringValueType() {
    return false;
  }

  /**
   * Tests whether the type is a string (value or Object).
   * @return {@code this &lt;: (String, string)}
   */
  public final boolean isString() {
    return this.isSubtype(
        getNativeType(JSTypeNative.STRING_VALUE_OR_OBJECT_TYPE));
  }

  /**
   * Tests whether the type is a number (value or Object).
   * @return {@code this &lt;: (Number, number)}
   */
  public final boolean isNumber() {
    return this.isSubtype(
        getNativeType(JSTypeNative.NUMBER_VALUE_OR_OBJECT_TYPE));
  }

  public boolean isArrayType() {
    return false;
  }

  public boolean isBooleanObjectType() {
    return false;
  }

  public boolean isBooleanValueType() {
    return false;
  }

  public boolean isRegexpType() {
    return false;
  }

  public boolean isDateType() {
    return false;
  }

  public boolean isNullType() {
    return false;
  }

  public boolean isVoidType() {
    return false;
  }

  public boolean isAllType() {
    return false;
  }

  public boolean isUnknownType() {
    return false;
  }

  public boolean isCheckedUnknownType() {
    return false;
  }

  public boolean isUnionType() {
    return false;
  }

  public boolean isFunctionType() {
    return false;
  }

  public boolean isEnumElementType() {
    return false;
  }

  public boolean isEnumType() {
    return false;
  }

  boolean isNamedType() {
    return false;
  }

  public boolean isRecordType() {
    return false;
  }

  public boolean isTemplateType() {
    return false;
  }

  /**
   * Tests whether this type is an {@code Object}, or any subtype thereof.
   * @return {@code this &lt;: Object}
   */
  public boolean isObject() {
    return false;
  }

  /**
   * Whether this type is a {@link FunctionType} that is a constructor or a
   * named type that points to such a type.
   */
  public boolean isConstructor() {
    return false;
  }

  /**
   * Whether this type is a nominal type (a named instance object or
   * a named enum).
   */
  public boolean isNominalType() {
    return false;
  }

  /**
   * Whether this type is an Instance object of some constructor.
   */
  public boolean isInstanceType() {
    return false;
  }

  /**
   * Whether this type is a {@link FunctionType} that is an interface or a named
   * type that points to such a type.
   */
  public boolean isInterface() {
    return false;
  }

  /**
   * Whether this type is a {@link FunctionType} that is an ordinary function or
   * a named type that points to such a type.
   */
  public boolean isOrdinaryFunction() {
    return false;
  }

  /**
   * Checks if two types are equivalent.
   */
  public boolean isEquivalentTo(JSType jsType) {
    if (jsType instanceof ProxyObjectType) {
      return jsType.isEquivalentTo(this);
    }
    // Relies on the fact that for the base {@link JSType}, only one
    // instance of each sub-type will ever be created in a given registry, so
    // there is no need to verify members. If the object pointers are not
    // identical, then the type member must be different.
    return this == jsType;
  }

  public static boolean isEquivalent(JSType typeA, JSType typeB) {
    return (typeA == null || typeB == null) ?
        typeA == typeB : typeA.isEquivalentTo(typeB);
  }

  @Override
  public boolean equals(Object jsType) {
    return (jsType instanceof JSType) ?
        isEquivalentTo((JSType) jsType) : false;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  /**
   * This predicate is used to test whether a given type can appear in a
   * 'Int32' context.  This context includes, for example, the operands of a
   * bitwise or operator.  Since we do not currently support integer types,
   * this is a synonym for {@code Number}.
   */
  public final boolean matchesInt32Context() {
    return matchesNumberContext();
  }

  /**
   * This predicate is used to test whether a given type can appear in a
   * 'Uint32' context.  This context includes the right-hand operand of a shift
   * operator.
   */
  public final boolean matchesUint32Context() {
    return matchesNumberContext();
  }

  /**
   * This predicate is used to test whether a given type can appear in a
   * numeric context, such as an operand of a multiply operator.
   */
  public boolean matchesNumberContext() {
    return false;
  }

  /**
   * This predicate is used to test whether a given type can appear in a
   * {@code String} context, such as an operand of a string concat (+) operator.
   *
   * All types have at least the potential for converting to {@code String}.
   * When we add externally defined types, such as a browser OM, we may choose
   * to add types that do not automatically convert to {@code String}.
   */
  public boolean matchesStringContext() {
    return false;
  }

  /**
   * This predicate is used to test whether a given type can appear in an
   * {@code Object} context, such as the expression in a with statement.
   *
   * Most types we will encounter, except notably {@code null}, have at least
   * the potential for converting to {@code Object}.  Host defined objects can
   * get peculiar.
   */
  public boolean matchesObjectContext() {
    return false;
  }

  /**
   * Coerces this type to an Object type, then gets the type of the property
   * whose name is given.
   *
   * Unlike {@link ObjectType#getPropertyType}, returns null if the property
   * is not found.
   *
   * @return The property's type. {@code null} if the current type cannot
   *     have properties, or if the type is not found.
   */
  public JSType findPropertyType(String propertyName) {
    ObjectType autoboxObjType = ObjectType.cast(autoboxesTo());
    if (autoboxObjType != null) {
      return autoboxObjType.findPropertyType(propertyName);
    }

    return null;
  }

  /**
   * This predicate is used to test whether a given type can be used as the
   * 'function' in a function call.
   *
   * @return {@code true} if this type might be callable.
   */
  public boolean canBeCalled() {
    return false;
  }

  /**
   * Tests whether values of {@code this} type can be safely assigned
   * to values of {@code that} type.<p>
   *
   * The default implementation verifies that {@code this} is a subtype
   * of {@code that}.<p>
   */
  public boolean canAssignTo(JSType that) {
    if (this.isSubtype(that)) {
      return true;
    }
    return false;
  }

  /**
   * Gets the type to which this type auto-boxes.
   *
   * @return the auto-boxed type or {@code null} if this type does not auto-box
   */
  public JSType autoboxesTo() {
    return null;
  }

  /**
   * Gets the type to which this type unboxes.
   *
   * @return the unboxed type or {@code null} if this type does not unbox.
   */
  public JSType unboxesTo() {
    return null;
  }

  /**
   * Casts this to an ObjectType, or returns null if this is not an ObjectType.
   *
   * Does not change the underlying JS type. If you want to simulate JS
   * autoboxing or dereferencing, you should use autoboxesTo() or dereference().
   * Those methods may change the underlying JS type.
   */
  public ObjectType toObjectType() {
    return this instanceof ObjectType ? (ObjectType) this : null;
  }

  /**
   * Dereference a type for property access.
   *
   * Autoboxes the type, filters null/undefined, and returns the result
   * iff it's an object.
   */
  public final ObjectType dereference() {
    JSType restricted = restrictByNotNullOrUndefined();
    JSType autobox = restricted.autoboxesTo();
    return ObjectType.cast(autobox == null ? restricted : autobox);
  }

  /**
   * Tests whether {@code this} and {@code that} are meaningfully
   * comparable. By meaningfully, we mean compatible types that do not lead
   * to step 22 of the definition of the Abstract Equality Comparison
   * Algorithm (11.9.3, page 55&ndash;56) of the ECMA-262 specification.<p>
   */
  public final boolean canTestForEqualityWith(JSType that) {
    return this.testForEquality(that).equals(UNKNOWN);
  }

  /**
   * Compares {@code this} and {@code that}.
   * @return <ul>
   * <li>{@link TernaryValue#TRUE} if the comparison of values of
   *   {@code this} type and {@code that} always succeed (such as
   *   {@code undefined} compared to {@code null})</li>
   * <li>{@link TernaryValue#FALSE} if the comparison of values of
   *   {@code this} type and {@code that} always fails (such as
   *   {@code undefined} compared to {@code number})</li>
   * <li>{@link TernaryValue#UNKNOWN} if the comparison can succeed or
   *   fail depending on the concrete values</li>
   * </ul>
   */
  public TernaryValue testForEquality(JSType that) {
    return testForEqualityHelper(this, that);
  }

  TernaryValue testForEqualityHelper(JSType aType, JSType bType) {
    if (bType.isAllType() || bType.isEmptyType() || bType.isUnknownType()) {
      return UNKNOWN;
    }
    if (aType.isFunctionType() || bType.isFunctionType()) {
      JSType otherType = aType.isFunctionType() ? bType : aType;
      // In theory, functions are comparable to anything except
      // null/undefined. For example, on FF3:
      // function() {} == 'function () {\n}'
      // In practice, how a function serializes to a string is
      // implementation-dependent, so it does not really make sense to test
      // for equality with a string.
      if (otherType.isSubtype(getNativeType(JSTypeNative.OBJECT_TYPE))) {
        return TernaryValue.UNKNOWN;
      } else {
        return TernaryValue.FALSE;
      }
    }
    if (bType.isEnumElementType() || bType.isUnionType()) {
      return bType.testForEquality(aType);
    }
    return null;
  }

  /**
   * Tests whether {@code this} and {@code that} are meaningfully
   * comparable using shallow comparison. By meaningfully, we mean compatible
   * types that are not rejected by step 1 of the definition of the Strict
   * Equality Comparison Algorithm (11.9.6, page 56&ndash;57) of the
   * ECMA-262 specification.<p>
   */
  public final boolean canTestForShallowEqualityWith(JSType that) {
    return this.isSubtype(that) || that.isSubtype(this);
  }

  /**
   * Tests whether this type is nullable.
   */
  public boolean isNullable() {
    return this.isSubtype(getNativeType(JSTypeNative.NULL_TYPE));
  }

  /**
   * Gets the least supertype of {@code this} and {@code that}.
   * The least supertype is the join (&#8744;) or supremum of both types in the
   * type lattice.<p>
   * Examples:
   * <ul>
   * <li>{@code number &#8744; *} = {@code *}</li>
   * <li>{@code number &#8744; Object} = {@code (number, Object)}</li>
   * <li>{@code Number &#8744; Object} = {@code Object}</li>
   * </ul>
   * @return {@code this &#8744; that}
   */
  public JSType getLeastSupertype(JSType that) {
    if (that.isUnionType()) {
      // Union types have their own implementation of getLeastSupertype.
      return that.getLeastSupertype(this);
    }
    return getLeastSupertype(this, that);
  }

  /**
   * A generic implementation meant to be used as a helper for common
   * getLeastSupertype implementations.
   */
  static JSType getLeastSupertype(JSType thisType, JSType thatType) {
    if (thatType.isEmptyType() || thatType.isAllType()) {
      // Defer to the implementations of the end lattice elements when
      // possible.
      return thatType.getLeastSupertype(thisType);
    }

    return thisType.registry.createUnionType(thisType, thatType);
  }

  /**
   * Gets the greatest subtype of {@code this} and {@code that}.
   * The greatest subtype is the meet (&#8743;) or infimum of both types in the
   * type lattice.<p>
   * Examples
   * <ul>
   * <li>{@code Number &#8743; Any} = {@code Any}</li>
   * <li>{@code number &#8743; Object} = {@code Any}</li>
   * <li>{@code Number &#8743; Object} = {@code Number}</li>
   * </ul>
   * @return {@code this &#8744; that}
   */
  public JSType getGreatestSubtype(JSType that) {
     if (that.isRecordType()) {
      // Record types have their own implementation of getGreatestSubtype.
      return that.getGreatestSubtype(this);
    }
    return getGreatestSubtype(this, that);
  }

  /**
   * A generic implementation meant to be used as a helper for common
   * getGreatestSubtype implementations.
   */
  static JSType getGreatestSubtype(JSType thisType, JSType thatType) {
    if (thatType.isEmptyType() || thatType.isAllType()) {
      // Defer to the implementations of the end lattice elements when
      // possible.
      return thatType.getGreatestSubtype(thisType);
    } else if (thisType.isUnknownType() || thatType.isUnknownType()) {
      // The greatest subtype with any unknown type is the universal
      // unknown type, unless the two types are equal.
      return thisType.isEquivalentTo(thatType) ? thisType :
          thisType.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    } else if (thisType.isSubtype(thatType)) {
      return thisType;
    } else if (thatType.isSubtype(thisType)) {
      return thatType;
    } else if (thisType.isUnionType()) {
      return ((UnionType) thisType).meet(thatType);
    } else if (thatType.isUnionType()) {
      return ((UnionType) thatType).meet(thisType);
    } else if (thisType.isObject() && thatType.isObject()) {
      return thisType.getNativeType(JSTypeNative.NO_OBJECT_TYPE);
    }
    return thisType.getNativeType(JSTypeNative.NO_TYPE);
  }

  /**
   * Computes the restricted type of this type knowing that the
   * {@code ToBoolean} predicate has a specific value. For more information
   * about the {@code ToBoolean} predicate, see
   * {@link #getPossibleToBooleanOutcomes}.
   *
   * @param outcome the value of the {@code ToBoolean} predicate
   *
   * @return the restricted type, or the Any Type if the underlying type could
   *         not have yielded this ToBoolean value
   *
   * TODO(user): Move this method to the SemanticRAI and use the visit
   * method of types to get the restricted type.
   */
  public JSType getRestrictedTypeGivenToBooleanOutcome(boolean outcome) {
    BooleanLiteralSet literals = getPossibleToBooleanOutcomes();
    if (literals.contains(outcome)) {
      return this;
    } else {
      return getNativeType(JSTypeNative.NO_TYPE);
    }
  }

  /**
   * Computes the set of possible outcomes of the {@code ToBoolean} predicate
   * for this type. The {@code ToBoolean} predicate is defined by the ECMA-262
   * standard, 3<sup>rd</sup> edition. Its behavior for simple types can be
   * summarized by the following table:
   * <table>
   * <tr><th>type</th><th>result</th></tr>
   * <tr><td>{@code undefined}</td><td>{false}</td></tr>
   * <tr><td>{@code null}</td><td>{false}</td></tr>
   * <tr><td>{@code boolean}</td><td>{true, false}</td></tr>
   * <tr><td>{@code number}</td><td>{true, false}</td></tr>
   * <tr><td>{@code string}</td><td>{true, false}</td></tr>
   * <tr><td>{@code Object}</td><td>{true}</td></tr>
   * </table>
   * @return the set of boolean literals for this type
   */
  public abstract BooleanLiteralSet getPossibleToBooleanOutcomes();

  /**
   * Computes the subset of {@code this} and {@code that} types if equality
   * is observed. If a value {@code v1} of type {@code null} is equal to a value
   * {@code v2} of type {@code (undefined,number)}, we can infer that the
   * type of {@code v1} is {@code null} and the type of {@code v2} is
   * {@code undefined}.
   *
   * @return a pair containing the restricted type of {@code this} as the first
   *         component and the restricted type of {@code that} as the second
   *         element. The returned pair is never {@code null} even though its
   *         components may be {@code null}
   */
  public TypePair getTypesUnderEquality(JSType that) {
    // unions types
    if (that instanceof UnionType) {
      TypePair p = that.getTypesUnderEquality(this);
      return new TypePair(p.typeB, p.typeA);
    }

    // other types
    switch (this.testForEquality(that)) {
      case FALSE:
        return new TypePair(null, null);

      case TRUE:
      case UNKNOWN:
        return new TypePair(this, that);
    }

    // switch case is exhaustive
    throw new IllegalStateException();
  }

  /**
   * Computes the subset of {@code this} and {@code that} types if inequality
   * is observed. If a value {@code v1} of type {@code number} is not equal to a
   * value {@code v2} of type {@code (undefined,number)}, we can infer that the
   * type of {@code v1} is {@code number} and the type of {@code v2} is
   * {@code number} as well.
   *
   * @return a pair containing the restricted type of {@code this} as the first
   *         component and the restricted type of {@code that} as the second
   *         element. The returned pair is never {@code null} even though its
   *         components may be {@code null}
   */
  public TypePair getTypesUnderInequality(JSType that) {
    // unions types
    if (that instanceof UnionType) {
      TypePair p = that.getTypesUnderInequality(this);
      return new TypePair(p.typeB, p.typeA);
    }

    // other types
    switch (this.testForEquality(that)) {
      case TRUE:
        JSType noType = getNativeType(JSTypeNative.NO_TYPE);
        return new TypePair(noType, noType);

      case FALSE:
      case UNKNOWN:
        return new TypePair(this, that);
    }

    // switch case is exhaustive
    throw new IllegalStateException();
  }

  /**
   * Computes the subset of {@code this} and {@code that} types under shallow
   * equality.
   *
   * @return a pair containing the restricted type of {@code this} as the first
   *         component and the restricted type of {@code that} as the second
   *         element. The returned pair is never {@code null} even though its
   *         components may be {@code null}.
   */
  public TypePair getTypesUnderShallowEquality(JSType that) {
    JSType commonType = getGreatestSubtype(that);
    return new TypePair(commonType, commonType);
  }

  /**
   * Computes the subset of {@code this} and {@code that} types under
   * shallow inequality.
   *
   * @return A pair containing the restricted type of {@code this} as the first
   *         component and the restricted type of {@code that} as the second
   *         element. The returned pair is never {@code null} even though its
   *         components may be {@code null}
   */
  public TypePair getTypesUnderShallowInequality(JSType that) {
    // union types
    if (that instanceof UnionType) {
      TypePair p = that.getTypesUnderShallowInequality(this);
      return new TypePair(p.typeB, p.typeA);
    }

    // Other types.
    // There are only two types whose shallow inequality is deterministically
    // true -- null and undefined. We can just enumerate them.
    if (this.isNullType() && that.isNullType() ||
        this.isVoidType() && that.isVoidType()) {
      return new TypePair(null, null);
    } else {
      return new TypePair(this, that);
    }
  }

  /**
   * If this is a union type, returns a union type that does not include
   * the null or undefined type.
   */
  public JSType restrictByNotNullOrUndefined() {
    return this;
  }

  /**
   * Checks whether {@code this} is a subtype of {@code that}.<p>
   *
   * Subtyping rules:
   * <ul>
   * <li>(unknown) &mdash; every type is a subtype of the Unknown type.</li>
   * <li>(no) &mdash; the No type is a subtype of every type.</li>
   * <li>(no-object) &mdash; the NoObject type is a subtype of every object
   * type (i.e. subtypes of the Object type).</li>
   * <li>(ref) &mdash; a type is a subtype of itself.</li>
   * <li>(union-l) &mdash; A union type is a subtype of a type U if all the
   * union type's constituents are a subtype of U. Formally<br>
   * {@code (T<sub>1</sub>, &hellip;, T<sub>n</sub>) &lt;: U} if and only
   * {@code T<sub>k</sub> &lt;: U} for all {@code k &isin; 1..n}.</li>
   * <li>(union-r) &mdash; A type U is a subtype of a union type if it is a
   * subtype of one of the union type's constituents. Formally<br>
   * {@code U &lt;: (T<sub>1</sub>, &hellip;, T<sub>n</sub>)} if and only
   * if {@code U &lt;: T<sub>k</sub>} for some index {@code k}.</li>
   * <li>(objects) &mdash; an Object {@code O<sub>1</sub>} is a subtype
   * of an object {@code O<sub>2</sub>} if it has more properties
   * than {@code O<sub>2</sub>} and all common properties are
   * pairwise subtypes.</li>
   * </ul>
   *
   * @return {@code this &lt;: that}
   */
  public abstract boolean isSubtype(JSType that);

  /**
   * Whether this type is meaningfully different from {@code that} type.
   * This is a trickier check than pure equality, because it has to properly
   * handle unknown types.
   *
   * @see <a href="http://www.youtube.com/watch?v=_RpSv3HjpEw">Unknown
   *     unknowns</a>
   */
  public boolean differsFrom(JSType that) {
    // if there are no unknowns, just use normal equality.
    if (!this.isUnknownType() && !that.isUnknownType()) {
      return !this.isEquivalentTo(that);
    }
    // otherwise, they're different iff one is unknown and the other is not.
    return this.isUnknownType() ^ that.isUnknownType();
  }

  /**
   * A generic implementation meant to be used as a helper for common subtyping
   * cases.
   */
  static boolean isSubtype(JSType thisType, JSType thatType) {
    // unknown
    if (thatType.isUnknownType()) {
      return true;
    }
    // equality
    if (thisType.isEquivalentTo(thatType)) {
      return true;
    }
    // all type
    if (thatType.isAllType()) {
      return true;
    }
    // unions
    if (thatType instanceof UnionType) {
      UnionType union = (UnionType)thatType;
      for (JSType element : union.alternates) {
        if (thisType.isSubtype(element)) {
          return true;
        }
      }
    }
    // named types
    if (thatType instanceof NamedType) {
      return thisType.isSubtype(((NamedType)thatType).getReferencedType());
    }
    return false;
  }

  /**
   * Visit this type with the given visitor.
   * @see com.google.javascript.rhino.jstype.Visitor
   * @return the value returned by the visitor
   */
  public abstract <T> T visit(Visitor<T> visitor);

  /**
   * Force this type to resolve, even if the registry is in a lazy
   * resolving mode.
   * @see #resolve
   */
  public final JSType forceResolve(ErrorReporter t, StaticScope<JSType> scope) {
    ResolveMode oldResolveMode = registry.getResolveMode();
    registry.setResolveMode(ResolveMode.IMMEDIATE);
    JSType result = resolve(t, scope);
    registry.setResolveMode(oldResolveMode);
    return result;
  }


  /**
   * Resolve this type in the given scope.
   *
   * The returned value must be equal to {@code this}, as defined by
   * {@link #isEquivalentTo}. It may or may not be the same object. This method
   * may modify the internal state of {@code this}, as long as it does
   * so in a way that preserves Object equality.
   *
   * For efficiency, we should only resolve a type once per compilation job.
   * For incremental compilations, one compilation job may need the
   * artifacts from a previous generation, so we will eventually need
   * a generational flag instead of a boolean one.
   */
  public final JSType resolve(ErrorReporter t, StaticScope<JSType> scope) {
    if (resolved) {
      // TODO(nicksantos): Check to see if resolve() looped back on itself.
      // Preconditions.checkNotNull(resolveResult);
      if (resolveResult == null) {
        return registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
      }
      return resolveResult;
    }
    resolved = true;
    resolveResult = resolveInternal(t, scope);
    resolveResult.setResolvedTypeInternal(resolveResult);
    return resolveResult;
  }

  /**
   * @see #resolve
   */
  abstract JSType resolveInternal(ErrorReporter t, StaticScope<JSType> scope);

  void setResolvedTypeInternal(JSType type) {
    resolveResult = type;
    resolved = true;
  }

  /** Whether the type has been resolved. */
  public final boolean isResolved() {
    return resolved;
  }

  /** Clears the resolved field. */
  public final void clearResolved() {
    resolved = false;
    resolveResult = null;
  }

  /**
   * A null-safe resolve.
   * @see #resolve
   */
  static final JSType safeResolve(
      JSType type, ErrorReporter t, StaticScope<JSType> scope) {
    return type == null ? null : type.resolve(t, scope);
  }

  /**
   * Certain types have constraints on them at resolution-time.
   * For example, a type in an {@code @extends} annotation must be an
   * object. Clients should inject a validator that emits a warning
   * if the type does not validate, and return false.
   */
  public boolean setValidator(Predicate<JSType> validator) {
    return validator.apply(this);
  }

  public static class TypePair {
    public final JSType typeA;
    public final JSType typeB;

    public TypePair(JSType typeA, JSType typeB) {
      this.typeA = typeA;
      this.typeB = typeB;
    }
  }

  /**
   * A hash code function for diagnosing complicated issues
   * around type-identity.
   */
  public String toDebugHashCodeString() {
    return "{" + this.hashCode() + "}";
  }
}
