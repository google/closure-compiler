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

import com.google.common.base.Predicate;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import java.io.Serializable;
import java.util.IdentityHashMap;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents JavaScript value types.
 *
 * <p>Types are split into two separate families: value types and object types.
 *
 * <p>A special {@link UnknownType} exists to represent a wildcard type on which no information can
 * be gathered. In particular, it can assign to everyone, is a subtype of everyone (and everyone is
 * a subtype of it).
 *
 * <p>If you remove the {@link UnknownType}, the set of types in the type system forms a lattice
 * with the {@link #isSubtype} relation defining the partial order of types. All types are united at
 * the top of the lattice by the {@link AllType} and at the bottom by the {@link NoType}.
 *
 * <p>
 *
 */
public abstract class JSType implements Serializable {
  private static final long serialVersionUID = 1L;

  @SuppressWarnings("ReferenceEquality")
  static final boolean areIdentical(JSType a, JSType b) {
    return a == b;
  }

  private boolean resolved = false;
  private JSType resolveResult = null;
  protected TemplateTypeMap templateTypeMap;

  private boolean hashCodeInProgress = false;

  private boolean inTemplatedCheckVisit = false;
  private static final CanCastToVisitor CAN_CAST_TO_VISITOR =
      new CanCastToVisitor();

  private static final ImmutableSet<String> BIVARIANT_TYPES =
      ImmutableSet.of("Object", "IArrayLike", "Array");

  final JSTypeRegistry registry;

  JSType(JSTypeRegistry registry) {
    this(registry, null);
  }

  JSType(JSTypeRegistry registry, TemplateTypeMap templateTypeMap) {
    this.registry = registry;

    this.templateTypeMap = templateTypeMap == null ?
        registry.createTemplateTypeMap(null, null) : templateTypeMap;
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

  /** A tristate value returned from canPropertyBeDefined. */
  public enum HasPropertyKind {
    ABSENT, // The property is not known to be part of this type
    KNOWN_PRESENT, // The properties is known to be defined on a type or its super types
    MAYBE_PRESENT; // The property is loosely associated with a type, typically one of its subtypes

    public static HasPropertyKind of(boolean has) {
      return has ? KNOWN_PRESENT : ABSENT;
    }
  }

  /**
   * Checks whether the property is present on the object.
   * @param pname The property name.
   */
  public HasPropertyKind getPropertyKind(String pname) {
    return getPropertyKind(pname, true);
  }

  /**
   * Checks whether the property is present on the object.
   * @param pname The property name.
   * @param autobox Whether to check for the presents on an autoboxed type
   */
  public HasPropertyKind getPropertyKind(String pname, boolean autobox) {
    return HasPropertyKind.ABSENT;
  }

  /**
   * Checks whether the property is present on the object.
   * @param pname The property name.
   */
  public final boolean hasProperty(String pname) {
    return !getPropertyKind(pname, false).equals(HasPropertyKind.ABSENT);
  }

  public boolean isNoType() {
    return false;
  }

  public boolean isNoResolvedType() {
    return false;
  }

  public final boolean isUnresolved() {
    return isNoResolvedType();
  }

  public final boolean isUnresolvedOrResolvedUnknown() {
    return isNoResolvedType() || (isNamedType() && isUnknownType());
  }

  public boolean isNoObjectType() {
    return false;
  }

  public final boolean isEmptyType() {
    return isNoType()
        || isNoObjectType()
        || isNoResolvedType()
        || areIdentical(this, registry.getNativeFunctionType(JSTypeNative.LEAST_FUNCTION_TYPE));
  }

  public boolean isNumberObjectType() {
    return false;
  }

  public boolean isNumberValueType() {
    return false;
  }

  /** Whether this is the prototype of a function. */
  // TODO(sdh): consider renaming this to isPrototypeObject.
  public boolean isFunctionPrototypeType() {
    return false;
  }

  public boolean isStringObjectType() {
    return false;
  }

  public boolean isSymbolObjectType() {
    return false;
  }

  boolean isTheObjectType() {
    return false;
  }

  public boolean isStringValueType() {
    return false;
  }

  public boolean isSymbolValueType() {
    return false;
  }

  /**
   * Tests whether the type is a string (value or Object).
   * @return <code>this &lt;: (String, string)</code>
   */
  public final boolean isString() {
    return isSubtypeOf(
        getNativeType(JSTypeNative.STRING_VALUE_OR_OBJECT_TYPE));
  }

  /**
   * Tests whether the type is a number (value or Object).
   * @return <code>this &lt;: (Number, number)</code>
   */
  public final boolean isNumber() {
    return isSubtypeOf(
        getNativeType(JSTypeNative.NUMBER_VALUE_OR_OBJECT_TYPE));
  }

  public final boolean isSymbol() {
    return isSubtypeOf(
        getNativeType(JSTypeNative.SYMBOL_VALUE_OR_OBJECT_TYPE));
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

  public final boolean isSomeUnknownType() {
    // OTI's notion of isUnknownType already accounts for looseness (see override in ObjectType).
    return isUnknownType();
  }

  public boolean isCheckedUnknownType() {
    return false;
  }

  public final boolean isUnionType() {
    return toMaybeUnionType() != null;
  }

  public boolean isFullyInstantiated() {
    return getTemplateTypeMap().isFull();
  }

  public boolean isPartiallyInstantiated() {
    return getTemplateTypeMap().isPartiallyFull();
  }

  /**
   * Returns true iff {@code this} can be a {@code struct}.
   * UnionType overrides the method, assume {@code this} is not a union here.
   */
  public boolean isStruct() {
    if (isObject()) {
      ObjectType objType = toObjectType();
      ObjectType iproto = objType.getImplicitPrototype();
      // For the case when a @struct constructor is assigned to a function's
      // prototype property
      if (iproto != null && iproto.isStruct()) {
        return true;
      }
      FunctionType ctor = objType.getConstructor();
      // This test is true for object literals
      if (ctor == null) {
        JSDocInfo info = objType.getJSDocInfo();
        return info != null && info.makesStructs();
      } else {
        return ctor.makesStructs();
      }
    }
    return false;
  }

  /**
   * Returns true iff {@code this} can be a {@code dict}.
   * UnionType overrides the method, assume {@code this} is not a union here.
   */
  public boolean isDict() {
    if (isObject()) {
      ObjectType objType = toObjectType();
      ObjectType iproto = objType.getImplicitPrototype();
      // For the case when a @dict constructor is assigned to a function's
      // prototype property
      if (iproto != null && iproto.isDict()) {
        return true;
      }
      FunctionType ctor = objType.getConstructor();
      // This test is true for object literals
      if (ctor == null) {
        JSDocInfo info = objType.getJSDocInfo();
        return info != null && info.makesDicts();
      } else {
        return ctor.makesDicts();
      }
    }
    return false;
  }

  public final boolean isLiteralObject() {
    if (this instanceof PrototypeObjectType) {
      return ((PrototypeObjectType) this).isAnonymous();
    }
    return false;
  }

  public JSType getGreatestSubtypeWithProperty(String propName) {
    return this.registry.getGreatestSubtypeWithProperty(this, propName);
  }

  /**
   * Downcasts this to a UnionType, or returns null if this is not a UnionType.
   *
   * Named in honor of Haskell's Maybe type constructor.
   */
  public UnionType toMaybeUnionType() {
    return null;
  }

  /** Returns true if this is a global this type. */
  public final boolean isGlobalThisType() {
    return areIdentical(this, registry.getNativeType(JSTypeNative.GLOBAL_THIS));
  }

  /** Returns true if toMaybeFunctionType returns a non-null FunctionType. */
  public final boolean isFunctionType() {
    return toMaybeFunctionType() != null;
  }

  /**
   * Downcasts this to a FunctionType, or returns null if this is not a function.
   *
   * <p>For the purposes of this function, we define a MaybeFunctionType as any type in the
   * sub-lattice { x | LEAST_FUNCTION_TYPE &lt;= x &lt;= GREATEST_FUNCTION_TYPE } This definition
   * excludes bottom types like NoType and NoObjectType.
   *
   * <p>This definition is somewhat arbitrary and axiomatic, but this is the definition that makes
   * the most sense for the most callers.
   */
  @SuppressWarnings("AmbiguousMethodReference")
  public FunctionType toMaybeFunctionType() {
    return null;
  }

  /** Returns this object cast to FunctionType or throws an exception if it isn't a FunctionType. */
  public FunctionType assertFunctionType() {
    FunctionType result = checkNotNull(toMaybeFunctionType(), "not a FunctionType: %s", this);
    return result;
  }

  /** Returns this object cast to ObjectType or throws an exception if it isn't an ObjectType. */
  public ObjectType assertObjectType() {
    ObjectType result = checkNotNull(toMaybeObjectType(), "Not an ObjectType: %s", this);
    return result;
  }

  /** Null-safe version of toMaybeFunctionType(). */
  @SuppressWarnings("AmbiguousMethodReference")
  public static FunctionType toMaybeFunctionType(JSType type) {
    return type == null ? null : type.toMaybeFunctionType();
  }

  public final boolean isEnumElementType() {
    return toMaybeEnumElementType() != null;
  }

  public final JSType getEnumeratedTypeOfEnumElement() {
    EnumElementType e = toMaybeEnumElementType();
    return e == null ? null : e.getPrimitiveType();
  }

  /**
   * Downcasts this to an EnumElementType, or returns null if this is not an EnumElementType.
   */
  public EnumElementType toMaybeEnumElementType() {
    return null;
  }

  // TODO(sdh): Consider changing this to isEnumObjectType(), though this would be inconsistent with
  // the EnumType class and toMaybeEnumType(), so we would need to consider changing them, too.
  public boolean isEnumType() {
    return toMaybeEnumType() != null;
  }

  /**
   * Downcasts this to an EnumType, or returns null if this is not an EnumType.
   */
  public EnumType toMaybeEnumType() {
    return null;
  }

  public boolean isNamedType() {
    return toMaybeNamedType() != null;
  }

  public boolean isLegacyNamedType() {
    return isNamedType();
  }

  public NamedType toMaybeNamedType() {
    return null;
  }

  public boolean isRecordType() {
    return toMaybeRecordType() != null;
  }

  public boolean isStructuralInterface() {
    return false;
  }

  public boolean isStructuralType() {
    return false;
  }

  /**
   * Downcasts this to a RecordType, or returns null if this is not
   * a RecordType.
   */
  public RecordType toMaybeRecordType() {
    return null;
  }

  public final boolean isTemplatizedType() {
    return toMaybeTemplatizedType() != null;
  }

  public final boolean isGenericObjectType() {
    return isTemplatizedType();
  }

  /**
   * Downcasts this to a TemplatizedType, or returns null if this is not
   * a function.
   */
  public TemplatizedType toMaybeTemplatizedType() {
    return null;
  }

  public final boolean isTemplateType() {
    return toMaybeTemplateType() != null;
  }

  public final boolean isTypeVariable() {
    return isTemplateType();
  }

  /**
   * Downcasts this to a TemplateType, or returns null if this is not
   * a function.
   */
  public TemplateType toMaybeTemplateType() {
    return null;
  }

  public boolean hasAnyTemplateTypes() {
    if (!this.inTemplatedCheckVisit) {
      this.inTemplatedCheckVisit = true;
      boolean result = hasAnyTemplateTypesInternal();
      this.inTemplatedCheckVisit = false;
      return result;
    } else {
      // prevent infinite recursion, this is "not yet".
      return false;
    }
  }

  boolean hasAnyTemplateTypesInternal() {
    return templateTypeMap.hasAnyTemplateTypesInternal();
  }

  /**
   * Returns the template type map associated with this type.
   */
  public TemplateTypeMap getTemplateTypeMap() {
    return templateTypeMap;
  }

  public final ImmutableSet<JSType> getTypeParameters() {
    ImmutableSet.Builder<JSType> params = ImmutableSet.builder();
    for (TemplateType type : getTemplateTypeMap().getTemplateKeys()) {
      params.add(type);
    }
    return params.build();
  }

  /**
   * Extends the template type map associated with this type, merging in the
   * keys and values of the specified map.
   */
  public void extendTemplateTypeMap(TemplateTypeMap otherMap) {
    templateTypeMap = templateTypeMap.extend(otherMap);
  }

  /**
   * Tests whether this type is an {@code Object}, or any subtype thereof.
   * @return <code>this &lt;: Object</code>
   */
  public boolean isObject() {
    return false;
  }

  /**
   * Tests whether this type is an {@code Object}, or any subtype thereof.
   *
   * @return <code>this &lt;: Object</code>
   */
  public final boolean isObjectType() {
    return isObject();
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
   * Whether this type is the original constructor of a nominal type.
   * Does not include structural constructors.
   */
  public final boolean isNominalConstructor() {
    if (isConstructor() || isInterface()) {
      FunctionType fn = toMaybeFunctionType();
      if (fn == null) {
        return false;
      }

      // Programmer-defined constructors will have a link
      // back to the original function in the source tree.
      // Structural constructors will not.
      if (fn.getSource() != null) {
        return true;
      }

      // Native constructors are always nominal.
      return fn.isNativeObjectType();
    }
    return false;
  }

  public boolean isNativeObjectType() {
    return false;
  }

  /**
   * Whether this type is an Instance object of some constructor.
   * Does not necessarily mean this is an {@link InstanceObjectType}.
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
   * Whether this type is a {@link FunctionType} that is an ordinary function (i.e. not a
   * constructor, nominal interface, or record interface), or a named type that points to such a
   * type.
   */
  public boolean isOrdinaryFunction() {
    return false;
  }

  @Override
  public boolean equals(@Nullable Object jsType) {
    return (jsType instanceof JSType) && isEquivalentTo((JSType) jsType);
  }

  /** Checks if two types are equivalent. */
  public final boolean isEquivalentTo(@Nullable JSType that) {
    return isEquivalentTo(that, false);
  }

  public final boolean isEquivalentTo(@Nullable JSType that, boolean isStructural) {
    EqCache eqCache = isStructural ? EqCache.create() : EqCache.createWithoutStructuralTyping();
    return checkEquivalenceHelper(that, EquivalenceMethod.IDENTITY, eqCache);
  }

  public static final boolean isEquivalent(@Nullable JSType typeA, @Nullable JSType typeB) {
    return (typeA == null) ? (typeB == null) : typeA.isEquivalentTo(typeB);
  }

  /**
   * Whether this type is meaningfully different from {@code that} type for
   * the purposes of data flow analysis.
   *
   * This is a trickier check than pure equality, because it has to properly
   * handle unknown types. See {@code EquivalenceMethod} for more info.
   *
   * @see <a href="http://www.youtube.com/watch?v=_RpSv3HjpEw">Unknown unknowns</a>
   */
  public final boolean differsFrom(JSType that) {
    return !checkEquivalenceHelper(that, EquivalenceMethod.DATA_FLOW, EqCache.create());
  }

  /**
   * An equivalence visitor.
   */
  @Deprecated
  boolean checkEquivalenceHelper(final JSType that, EquivalenceMethod eqMethod) {
    return checkEquivalenceHelper(
        that,
        eqMethod,
        EqCache.create());
  }

  boolean checkEquivalenceHelper(
      final @Nullable JSType that, EquivalenceMethod eqMethod, EqCache eqCache) {
    if (that == null) {
      return false;
    } else if (areIdentical(this, that)) {
      return true;
    }

    if (this.isNoResolvedType() && that.isNoResolvedType()) {
      if (this.isNamedType() && that.isNamedType()) {
        return Objects.equals(
            this.toMaybeNamedType().getReferenceName(), //
            that.toMaybeNamedType().getReferenceName());
      } else {
        return true;
      }
    }

    boolean thisUnknown = isUnknownType();
    boolean thatUnknown = that.isUnknownType();
    if (thisUnknown || thatUnknown) {
      if (eqMethod == EquivalenceMethod.INVARIANT) {
        // If we're checking for invariance, the unknown type is invariant
        // with everyone.
        return true;
      } else if (eqMethod == EquivalenceMethod.DATA_FLOW) {
        // If we're checking data flow, then two types are the same if they're
        // both unknown.
        return thisUnknown && thatUnknown;
      } else if (thisUnknown && thatUnknown &&
          (isNominalType() ^ that.isNominalType())) {
        // If they're both unknown, but one is a nominal type and the other
        // is not, then we should fail out immediately. This ensures that
        // we won't unbox the unknowns further down.
        return false;
      }
    }

    if (isUnionType() && that.isUnionType()) {
      return toMaybeUnionType().checkUnionEquivalenceHelper(
          that.toMaybeUnionType(), eqMethod, eqCache);
    }

    if (isFunctionType() && that.isFunctionType()) {
      return toMaybeFunctionType().checkFunctionEquivalenceHelper(
          that.toMaybeFunctionType(), eqMethod, eqCache);
    }

    if (!getTemplateTypeMap().checkEquivalenceHelper(
        that.getTemplateTypeMap(), eqMethod, eqCache, SubtypingMode.NORMAL)) {
      return false;
    }

    // Whether or not we use structural typing to compare object types is based on:
    // 1. if eqCache.isStructuralTyping() is true, we always use structural typing
    // 2. if we are comparing to anonymous record types (e.g. `{a: 3}`), always use structural types
    // Ideally we would also use structural typing to compare anything declared @record, but
    // that is harder to do with our current representation of types.
    if ((eqCache.isStructuralTyping() && this.isStructuralType() && that.isStructuralType())
        || (this.isRecordType() && that.isRecordType())) {
      return toMaybeObjectType()
          .checkStructuralEquivalenceHelper(that.toMaybeObjectType(), eqMethod, eqCache);
    }

    if (isNominalType() && that.isNominalType()) {
      // TODO(johnlenz): is this valid across scopes?
      @Nullable String nameOfThis = deepestResolvedTypeNameOf(this.toObjectType());
      @Nullable String nameOfThat = deepestResolvedTypeNameOf(that.toObjectType());

      if ((nameOfThis == null) && (nameOfThat == null)) {
        // These are two anonymous types that were masquerading as nominal, so don't compare names.
      } else {
        return Objects.equals(nameOfThis, nameOfThat);
      }
    }

    if (isTemplateType() && that.isTemplateType()) {
      // TemplateType are they same only if they are object identical,
      // which we check at the start of this function.
      return false;
    }

    // Unbox other proxies.
    if (this instanceof ProxyObjectType) {
      return ((ProxyObjectType) this)
          .getReferencedTypeInternal().checkEquivalenceHelper(
              that, eqMethod, eqCache);
    }

    if (that instanceof ProxyObjectType) {
      return checkEquivalenceHelper(
          ((ProxyObjectType) that).getReferencedTypeInternal(),
          eqMethod, eqCache);
    }

    // Relies on the fact that for the base {@link JSType}, only one
    // instance of each sub-type will ever be created in a given registry, so
    // there is no need to verify members. If the object pointers are not
    // identical, then the type member must be different.
    return false;
  }

  // Named types may be proxies of concrete types.
  @Nullable
  private String deepestResolvedTypeNameOf(ObjectType objType) {
    if (!objType.isResolved() || !(objType instanceof ProxyObjectType)) {
      return objType.getReferenceName();
    }

    ObjectType internal = ((ProxyObjectType) objType).getReferencedObjTypeInternal();
    return (internal != null && internal.isNominalType())
        ? deepestResolvedTypeNameOf(internal)
        : null;
  }

  /**
   * Calculates a hash of the object as per {@link Object#hashCode()}.
   *
   * <p>This method is <em>unsafe</em> for multi-threaded use. The implementation mutates instance
   * state to prevent recursion and therefore expects sole access.
   */
  @Override
  public final int hashCode() {
    if (hashCodeInProgress) {
      return -1; // Recursive base-case.
    }

    this.hashCodeInProgress = true;
    int hashCode = recursionUnsafeHashCode();
    this.hashCodeInProgress = false;
    return hashCode;
  }

  /**
   * Calculates {@code #hashCode()} with the assumption that it will never be called recursively.
   *
   * <p>To work correctly this method should only called under the following conditions:
   *
   * <ul>
   *   <li>by a subclass of {@link JSType};
   *   <li>within the body of an override;
   *   <li>when delegating to a superclass implementation.
   * </ul>
   */
  abstract int recursionUnsafeHashCode();

  /**
   * This predicate is used to test whether a given type can appear in a numeric context, such as an
   * operand of a multiply operator.
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
   * This predicate is used to test whether a given type can appear in a
   * {@code symbol} context such as property access.
   */
  public boolean matchesSymbolContext() {
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
  public final boolean canCastTo(JSType that) {
    return this.visit(CAN_CAST_TO_VISITOR, that);
  }

  /**
   * Turn a scalar type to the corresponding object type.
   *
   * @return the auto-boxed type or {@code null} if this type is not a scalar.
   */
  public JSType autoboxesTo() {
    return null;
  }

  public boolean isBoxableScalar() {
    return autoboxesTo() != null;
  }

  // TODO(johnlenz): this method is only used for testing, consider removing this.
  /**
   * Turn an object type to its corresponding scalar type.
   *
   * @return the unboxed type or {@code null} if this type does not unbox.
   */
  public JSType unboxesTo() {
    return null;
  }

  /**
   * Casts this to an ObjectType, or returns null if this is not an ObjectType.
   * If this is a scalar type, it will *not* be converted to an object type.
   * If you want to simulate JS autoboxing or dereferencing, you should use
   * autoboxesTo() or dereference().
   */
  public ObjectType toObjectType() {
    return this instanceof ObjectType ? (ObjectType) this : null;
  }

  /**
   * Dereferences a type for property access.
   *
   * Filters null/undefined and autoboxes the resulting type.
   * Never returns null.
   */
  public JSType autobox() {
    JSType restricted = restrictByNotNullOrUndefined();
    JSType autobox = restricted.autoboxesTo();
    return autobox == null ? restricted : autobox;
  }

  /**
   * Dereferences a type for property access.
   *
   * Filters null/undefined, autoboxes the resulting type, and returns it
   * iff it's an object. If not an object, returns null.
   */
  @Nullable
  public final ObjectType dereference() {
    return autobox().toObjectType();
  }

  /**
   * Tests whether {@code this} and {@code that} are meaningfully
   * comparable. By meaningfully, we mean compatible types that do not lead
   * to step 22 of the definition of the Abstract Equality Comparison
   * Algorithm (11.9.3, page 55&ndash;56) of the ECMA-262 specification.<p>
   */
  public final boolean canTestForEqualityWith(JSType that) {
    return testForEquality(that).equals(TernaryValue.UNKNOWN);
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

  final TernaryValue testForEqualityHelper(JSType aType, JSType bType) {
    if (bType.isAllType() || bType.isUnknownType() ||
        bType.isNoResolvedType() ||
        aType.isAllType() || aType.isUnknownType() ||
        aType.isNoResolvedType()) {
      return TernaryValue.UNKNOWN;
    }

    boolean aIsEmpty = aType.isEmptyType();
    boolean bIsEmpty = bType.isEmptyType();
    if (aIsEmpty || bIsEmpty) {
      if (aIsEmpty && bIsEmpty) {
        return TernaryValue.TRUE;
      } else {
        return TernaryValue.UNKNOWN;
      }
    }

    if (aType.isFunctionType() || bType.isFunctionType()) {
      JSType otherType = aType.isFunctionType() ? bType : aType;

      // TODO(johnlenz): tighten function type comparisons in general.
      if (otherType.isSymbol()) {
        return TernaryValue.FALSE;
      }

      // In theory, functions are comparable to anything except
      // null/undefined. For example, on FF3:
      // function() {} == 'function () {\n}'
      // In practice, how a function serializes to a string is
      // implementation-dependent, so it does not really make sense to test
      // for equality with a string.
      JSType meet = otherType.getGreatestSubtype(
          getNativeType(JSTypeNative.OBJECT_TYPE));
      if (meet.isNoType() || meet.isNoObjectType()) {
        return TernaryValue.FALSE;
      } else {
        return TernaryValue.UNKNOWN;
      }
    }

    if (bType.isEnumElementType() || bType.isUnionType()) {
      return bType.testForEquality(aType);
    }

    // If this is a "Symbol" or that is "symbol" or "Symbol"
    if (aType.isSymbol()) {
      return bType.canCastTo(getNativeType(JSTypeNative.SYMBOL_VALUE_OR_OBJECT_TYPE))
          ? TernaryValue.UNKNOWN
          : TernaryValue.FALSE;
    }

    if (bType.isSymbol()) {
      return aType.canCastTo(getNativeType(JSTypeNative.SYMBOL_VALUE_OR_OBJECT_TYPE))
          ? TernaryValue.UNKNOWN
          : TernaryValue.FALSE;
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
    if (isEmptyType() || that.isEmptyType()) {
      return isSubtypeOf(that) || that.isSubtypeOf(this);
    }

    JSType inf = getGreatestSubtype(that);
    return !inf.isEmptyType()
        ||
        // Our getGreatestSubtype relation on functions is pretty bad.
        // Let's just say it's always ok to compare two functions.
        // Once the TODO in FunctionType is fixed, we should be able to
        // remove this.
        areIdentical(inf, registry.getNativeType(JSTypeNative.LEAST_FUNCTION_TYPE));
  }

  /**
   * Tests whether this type is nullable.
   */
  public boolean isNullable() {
    return false;
  }

  /**
   * Tests whether this type is voidable.
   */
  public boolean isVoidable() {
    return false;
  }

  /**
   * Tests whether this type explicitly allows undefined, as opposed to ? or *. This is required for
   * a property to be optional.
   */
  public boolean isExplicitlyVoidable() {
    return false;
  }

  /**
   * Gets the least supertype of this that's not a union.
   */
  public JSType collapseUnion() {
    return this;
  }

  /**
   * Gets the least supertype of {@code this} and {@code that}. The least supertype is the join
   * (&#8744;) or supremum of both types in the type lattice.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li><code>number &#8744; *</code> = {@code *}
   *   <li><code>number &#8744; Object</code> = {@code (number, Object)}
   *   <li><code>Number &#8744; Object</code> = {@code Object}
   * </ul>
   *
   * @return <code>this &#8744; that</code>
   */
  @SuppressWarnings("AmbiguousMethodReference")
  public JSType getLeastSupertype(JSType that) {
    if (areIdentical(this, that)) {
      return this;
    }

    that = filterNoResolvedType(that);
    if (that.isUnionType()) {
      // Union types have their own implementation of getLeastSupertype.
      return that.toMaybeUnionType().getLeastSupertype(this);
    }
    return getLeastSupertype(this, that);
  }

  /**
   * A generic implementation meant to be used as a helper for common getLeastSupertype
   * implementations.
   */
  @SuppressWarnings("AmbiguousMethodReference")
  static JSType getLeastSupertype(JSType thisType, JSType thatType) {
    boolean areEquivalent = thisType.isEquivalentTo(thatType);
    return areEquivalent ? thisType :
        filterNoResolvedType(
            thisType.registry.createUnionType(thisType, thatType));
  }

  public JSType meetWith(JSType that) {
    return getGreatestSubtype(this, that);
  }

  /**
   * Gets the greatest subtype of {@code this} and {@code that}. The greatest subtype is the meet
   * (&#8743;) or infimum of both types in the type lattice.
   *
   * <p>Examples
   *
   * <ul>
   *   <li><code>Number &#8743; Any</code> = {@code Any}
   *   <li><code>number &#8743; Object</code> = {@code Any}
   *   <li><code>Number &#8743; Object</code> = {@code Number}
   * </ul>
   *
   * @return <code>this &#8744; that</code>
   */
  @SuppressWarnings("AmbiguousMethodReference")
  public JSType getGreatestSubtype(JSType that) {
    return getGreatestSubtype(this, that);
  }

  /**
   * A generic implementation meant to be used as a helper for common getGreatestSubtype
   * implementations.
   */
  @SuppressWarnings("AmbiguousMethodReference")
  static JSType getGreatestSubtype(JSType thisType, JSType thatType) {
    if (thisType.isFunctionType() && thatType.isFunctionType()) {
      // The FunctionType sub-lattice is not well-defined. i.e., the
      // proposition
      // A < B => sup(A, B) == B
      // does not hold because of unknown parameters and return types.
      // See the comment in supAndInfHelper for more info on this.
      return thisType.toMaybeFunctionType().supAndInfHelper(
          thatType.toMaybeFunctionType(), false);
    } else if (thisType.isEquivalentTo(thatType)) {
      return thisType;
    } else if (thisType.isUnknownType() || thatType.isUnknownType()) {
      // The greatest subtype with any unknown type is the universal
      // unknown type, unless the two types are equal.
      return thisType.isEquivalentTo(thatType) ? thisType :
          thisType.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    } else if (thisType.isUnionType()) {
      return thisType.toMaybeUnionType().meet(thatType);
    } else if (thatType.isUnionType()) {
      return thatType.toMaybeUnionType().meet(thisType);
    } else if (thisType.isTemplatizedType()) {
      return thisType.toMaybeTemplatizedType().getGreatestSubtypeHelper(
          thatType);
    }  else if (thatType.isTemplatizedType()) {
      return thatType.toMaybeTemplatizedType().getGreatestSubtypeHelper(
          thisType);
    } else if (thisType.isSubtypeOf(thatType)) {
      return filterNoResolvedType(thisType);
    } else if (thatType.isSubtypeOf(thisType)) {
      return filterNoResolvedType(thatType);
    } else if (thisType.isRecordType()) {
      return thisType.toMaybeRecordType().getGreatestSubtypeHelper(thatType);
    } else if (thatType.isRecordType()) {
      return thatType.toMaybeRecordType().getGreatestSubtypeHelper(thisType);
    }

    if (thisType.isEnumElementType()) {
      JSType inf = thisType.toMaybeEnumElementType().meet(thatType);
      if (inf != null) {
        return inf;
      }
    } else if (thatType.isEnumElementType()) {
      JSType inf = thatType.toMaybeEnumElementType().meet(thisType);
      if (inf != null) {
        return inf;
      }
    }

    if (thisType.isObject() && thatType.isObject()) {
      return thisType.getNativeType(JSTypeNative.NO_OBJECT_TYPE);
    }
    return thisType.getNativeType(JSTypeNative.NO_TYPE);
  }

  /**
   * When computing infima, we may get a situation like
   * inf(Type1, Type2)
   * where both types are unresolved, so they're technically
   * subtypes of one another.
   *
   * If this happens, filter them down to NoResolvedType.
   */
  static JSType filterNoResolvedType(JSType type) {
    if (type.isNoResolvedType()) {
      // inf(UnresolvedType1, UnresolvedType2) needs to resolve
      // to the base unresolved type, so that the relation is symmetric.
      return type.getNativeType(JSTypeNative.NO_RESOLVED_TYPE);
    } else if (type.isUnionType()) {
      UnionType unionType = type.toMaybeUnionType();
      boolean needsFiltering = false;
      ImmutableList<JSType> alternatesList = unionType.getAlternates();
      for (int i = 0; i < alternatesList.size(); i++) {
        JSType alt = alternatesList.get(i);
        if (alt.isNoResolvedType()) {
          needsFiltering = true;
          break;
        }
      }

      if (needsFiltering) {
        UnionTypeBuilder builder = UnionTypeBuilder.create(type.registry);
        builder.addAlternate(type.getNativeType(JSTypeNative.NO_RESOLVED_TYPE));
        for (int i = 0; i < alternatesList.size(); i++) {
          JSType alt = alternatesList.get(i);
          if (!alt.isNoResolvedType()) {
            builder.addAlternate(alt);
          }
        }
        return builder.build();
      }
    }
    return type;
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
    if (outcome && areIdentical(this, getNativeType(JSTypeNative.UNKNOWN_TYPE))) {
      return getNativeType(JSTypeNative.CHECKED_UNKNOWN_TYPE);
    }

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
   * <table summary="">
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
    if (that.isUnionType()) {
      TypePair p = that.toMaybeUnionType().getTypesUnderEquality(this);
      return new TypePair(p.typeB, p.typeA);
    }

    // other types
    switch (testForEquality(that)) {
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
    if (that.isUnionType()) {
      TypePair p = that.toMaybeUnionType().getTypesUnderInequality(this);
      return new TypePair(p.typeB, p.typeA);
    }

    // other types
    switch (testForEquality(that)) {
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
    if (that.isUnionType()) {
      TypePair p = that.toMaybeUnionType().getTypesUnderShallowInequality(this);
      return new TypePair(p.typeB, p.typeA);
    }

    // Other types.
    // There are only two types whose shallow inequality is deterministically
    // true -- null and undefined. We can just enumerate them.
    if ((isNullType() && that.isNullType()) || (isVoidType() && that.isVoidType())) {
      return new TypePair(null, null);
    } else {
      return new TypePair(this, that);
    }
  }

  public Iterable<JSType> getUnionMembers() {
    return isUnionType()
        ? this.toMaybeUnionType().getAlternatesWithoutStructuralTyping()
        : null;
  }

  /**
   * If this is a union type, returns a union type that does not include
   * the null or undefined type.
   */
  public JSType restrictByNotNullOrUndefined() {
    return this;
  }

  /** If this is a union type, returns a union type that does not include the undefined type. */
  public JSType restrictByNotUndefined() {
    return this;
  }

  /**
   * the logic of this method is similar to isSubtype,
   * except that it does not perform structural interface matching
   *
   * This function is added for disambiguate properties,
   * and is deprecated for the other use cases.
   */
  public boolean isSubtypeWithoutStructuralTyping(JSType that) {
    return isSubtype(
        that, ImplCache.createWithoutStructuralTyping(), SubtypingMode.NORMAL);
  }

  /**
   * In files translated from Java, we typecheck null and undefined loosely.
   */
  public static enum SubtypingMode {
    NORMAL,
    IGNORE_NULL_UNDEFINED
  }

  /**
   * Checks whether {@code this} is a subtype of {@code that}.<p>
   * Note this function also returns true if this type structurally
   * matches the protocol define by that type (if that type is an
   * interface function type)
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
   * <code>(T<sub>1</sub>, &hellip;, T<sub>n</sub>) &lt;: U</code> if and only
   * <code>T<sub>k</sub> &lt;: U</code> for all <code>k &isin; 1..n</code>.</li>
   * <li>(union-r) &mdash; A type U is a subtype of a union type if it is a
   * subtype of one of the union type's constituents. Formally<br>
   * <code>U &lt;: (T<sub>1</sub>, &hellip;, T<sub>n</sub>)</code> if and only
   * if <code>U &lt;: T<sub>k</sub></code> for some index {@code k}.</li>
   * <li>(objects) &mdash; an Object <code>O<sub>1</sub></code> is a subtype
   * of an object <code>O<sub>2</sub></code> if it has more properties
   * than <code>O<sub>2</sub></code> and all common properties are
   * pairwise subtypes.</li>
   * </ul>
   *
   * @return <code>this &lt;: that</code>
   */
  public boolean isSubtype(JSType that) {
    return isSubtypeHelper(this, that,
        ImplCache.create(), SubtypingMode.NORMAL);
  }

  public boolean isSubtype(JSType that, SubtypingMode mode) {
    return isSubtype(that, ImplCache.create(), mode);
  }

  /**
   * checking isSubtype with structural interface matching
   * @param implicitImplCache a cache that records the checked
   * or currently checking type pairs, for example, if previous
   * checking found that constructor C is a subtype of interface I,
   * then in the cache, table key {@code <I,C>} maps to IMPLEMENT status.
   */
  protected boolean isSubtype(JSType that,
      ImplCache implicitImplCache, SubtypingMode subtypingMode) {
    return isSubtypeHelper(this, that, implicitImplCache, subtypingMode);
  }

  /**
   * if implicitImplCache is null, there is no structural interface matching
   */
  static boolean isSubtypeHelper(JSType thisType, JSType thatType,
      ImplCache implicitImplCache, SubtypingMode subtypingMode) {
    checkNotNull(thisType);
    // unknown
    if (thatType.isUnknownType()) {
      return true;
    }
    // all type
    if (thatType.isAllType()) {
      return true;
    }
    // equality
    if (thisType.isEquivalentTo(thatType, implicitImplCache.isStructuralTyping())) {
      return true;
    }
    // unions
    if (thatType.isUnionType()) {
      UnionType union = thatType.toMaybeUnionType();
      // use an indexed for-loop to avoid allocations
      ImmutableList<JSType> alternatesWithoutStucturalTyping =
          union.getAlternatesWithoutStructuralTyping();
      for (int i = 0; i < alternatesWithoutStucturalTyping.size(); i++) {
        JSType element = alternatesWithoutStucturalTyping.get(i);
        if (thisType.isSubtype(element, implicitImplCache, subtypingMode)) {
          return true;
        }
      }
      return false;
    }
    if (subtypingMode == SubtypingMode.IGNORE_NULL_UNDEFINED
        && (thisType.isNullType() || thisType.isVoidType())) {
      return true;
    }

    // TemplateTypeMaps. This check only returns false if the TemplateTypeMaps
    // are not equivalent.
    TemplateTypeMap thisTypeParams = thisType.getTemplateTypeMap();
    TemplateTypeMap thatTypeParams = thatType.getTemplateTypeMap();
    boolean templateMatch = true;
    if (isBivariantType(thatType)) {
      // Array and Object are exempt from template type invariance; their
      // template types maps are bivariant.  That is they are considered
      // a match if either ObjectElementKey values are subtypes of the
      // other.
      TemplateType key = thisType.registry.getObjectElementKey();
      JSType thisElement = thisTypeParams.getResolvedTemplateType(key);
      JSType thatElement = thatTypeParams.getResolvedTemplateType(key);

      templateMatch = thisElement.isSubtype(thatElement, implicitImplCache, subtypingMode)
          || thatElement.isSubtype(thisElement, implicitImplCache, subtypingMode);
    } else if (isIThenableSubtype(thatType)) {
      // NOTE: special case IThenable subclass (Promise, etc).  These classes are expected to be
      // covariant.
      // Also note that this ignores any additional template type arguments that might be defined
      // on subtypes.  If we expand this to other types, a more correct solution will be needed.
      TemplateType key = thisType.registry.getThenableValueKey();
      JSType thisElement = thisTypeParams.getResolvedTemplateType(key);
      JSType thatElement = thatTypeParams.getResolvedTemplateType(key);

      templateMatch = thisElement.isSubtype(thatElement, implicitImplCache, subtypingMode);
    } else {
      templateMatch = thisTypeParams.checkEquivalenceHelper(
          thatTypeParams, EquivalenceMethod.INVARIANT, subtypingMode);
    }
    if (!templateMatch) {
      return false;
    }

    // If the super type is a structural type, then we can't safely remove a templatized type
    // (since it might affect the types of the properties)
    if (implicitImplCache.isStructuralTyping()
        && thisType.isObject() && thatType.isStructuralType()) {
      return thisType.toMaybeObjectType().isStructuralSubtype(
          thatType.toMaybeObjectType(), implicitImplCache, subtypingMode);
    }

    // Templatized types. For nominal types, the above check guarantees TemplateTypeMap
    // equivalence; check if the base type is a subtype.
    if (thisType.isTemplatizedType()) {
      return thisType.toMaybeTemplatizedType().getReferencedType().isSubtype(
          thatType, implicitImplCache, subtypingMode);
    }

    // proxy types
    if (thatType instanceof ProxyObjectType) {
      return thisType.isSubtype(
          ((ProxyObjectType) thatType).getReferencedTypeInternal(),
          implicitImplCache, subtypingMode);
    }
    return false;
  }

  /**
   * Determines if the supplied type should be checked as a bivariant
   * templatized type rather the standard invariant templatized type
   * rules.
   */
  static boolean isBivariantType(JSType type) {
    ObjectType objType = type.toObjectType();
    return objType != null
        // && objType.isNativeObjectType()
        && BIVARIANT_TYPES.contains(objType.getReferenceName());
  }

  /**
   * Determines if the specified type is exempt from standard invariant templatized typing rules.
   */
  static boolean isIThenableSubtype(JSType type) {
    if (type.isTemplatizedType()) {
      TemplatizedType ttype = type.toMaybeTemplatizedType();
      return ttype.getTemplateTypeMap().hasTemplateKey(ttype.registry.getThenableValueKey());
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
   * Visit the types with the given visitor.
   * @see com.google.javascript.rhino.jstype.RelationshipVisitor
   * @return the value returned by the visitor
   */
  abstract <T> T visit(RelationshipVisitor<T> visitor, JSType that);

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
  public final JSType resolve(ErrorReporter reporter) {
    if (resolved) {
      if (resolveResult == null) {
        // If there is a circular definition, keep the NamedType around. This is not ideal,
        // but is still a better type than unknown.
        return this;
      }
      return resolveResult;
    }
    resolved = true;
    resolveResult = resolveInternal(reporter);
    resolveResult.setResolvedTypeInternal(resolveResult);
    return resolveResult;
  }

  /**
   * @see #resolve
   */
  abstract JSType resolveInternal(ErrorReporter reporter);

  void setResolvedTypeInternal(JSType type) {
    resolveResult = type;
    resolved = true;
  }

  /**
   * Returns whether the type has undergone resolution.
   *
   * <p>A value of {@code true} <em>does not</em> indicate that resolution was successful, only that
   * it was attempted and has finished.
   */
  public final boolean isResolved() {
    return resolved;
  }

  /** Returns whether the type has undergone resolution and resolved to a "useful" type. */
  public final boolean isSuccessfullyResolved() {
    return isResolved() && !isNoResolvedType();
  }

  /** Returns whether the type has undergone resolution and resolved to a "useless" type. */
  public final boolean isUnsuccessfullyResolved() {
    return isResolved() && isNoResolvedType();
  }

  /**
   * A null-safe resolve.
   * @see #resolve
   */
  static final JSType safeResolve(
      JSType type, ErrorReporter reporter) {
    return type == null ? null : type.resolve(reporter);
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

  /**
   * a data structure that represents a pair of types
   */
  public static class TypePair {
    public final JSType typeA;
    public final JSType typeB;

    public TypePair(JSType typeA, JSType typeB) {
      this.typeA = typeA;
      this.typeB = typeB;
    }
  }

  /**
   * A string representation of this type, suitable for printing
   * in warnings.
   */
  @Override
  public String toString() {
    return appendTo(new StringBuilder(), false).toString();
  }

  /**
   * A hash code function for diagnosing complicated issues
   * around type-identity.
   */
  public String toDebugHashCodeString() {
    return "{" + hashCode() + "}";
  }

  // Don't call from this package; use appendAsNonNull instead.
  public final String toAnnotationString(Nullability nullability) {
    return nullability == Nullability.EXPLICIT
        ? appendAsNonNull(new StringBuilder(), true).toString()
        : appendTo(new StringBuilder(), true).toString();
  }

  final StringBuilder appendAsNonNull(StringBuilder sb, boolean forAnnotations) {
    if (forAnnotations
        && isObject()
        && !isUnknownType()
        && !isTemplateType()
        && !isRecordType()
        && !isFunctionType()
        && !isUnionType()
        && !isLiteralObject()) {
      sb.append("!");
    }
    return appendTo(sb, forAnnotations);
  }

  abstract StringBuilder appendTo(StringBuilder sb, boolean forAnnotations);

  /**
   * Modify this type so that it matches the specified type.
   *
   * This is useful for reverse type-inference, where we want to
   * infer that an object literal matches its constraint (much like
   * how the java compiler does reverse-inference to figure out generics).
   * @param constraint
   */
  public void matchConstraint(JSType constraint) {}

  public boolean isSubtypeOf(JSType other) {
    return isSubtype(other);
  }

  public ObjectType toMaybeObjectType() {
    return toObjectType();
  }

  /**
   * describe the status of checking that a function
   * implicitly implements an interface.
   *
   * it also be used to describe the status of checking
   * that a record type structurally matches another
   * record type
   *
   * A function implicitly implements an interface if
   * the function does not use @implements to declare
   * that it implements the interface, but its class
   * structure complies with the protocol defined
   * by the interface
   */
  static enum MatchStatus {
    /**
     * indicate that a function implicitly
     * implements an interface (i.e., the function
     * structurally complies with the protocol
     * defined in interface)
     *
     * or a record type matches another record type
     */
    MATCH(true),
    /**
     * indicate that a function does not implicitly
     * implements an interface (i.e., the function
     * does not structurally comply with the protocol
     * defined in interface)
     *
     * or a record type does not match another
     * record type
     */
    NOT_MATCH(false),
    /**
     * indicate that the interface and function
     * relationship is under processing
     */
    PROCESSING(true);

    MatchStatus(boolean isSubtype) {
      this.isSubtype = isSubtype;
    }

    private final boolean isSubtype;
    boolean subtypeValue() {
      return this.isSubtype;
    }
  }

  /**
   * base cache data structure
   */
  private abstract static class MatchCache {
    private final boolean isStructuralTyping;

    MatchCache(boolean isStructuralTyping) {
      this.isStructuralTyping = isStructuralTyping;
    }

    boolean isStructuralTyping() {
      return isStructuralTyping;
    }
  }

  /**
   * cache used by equivalence check logic
   */
  static class EqCache extends MatchCache {
    private IdentityHashMap<Object, IdentityHashMap<Object, MatchStatus>> matchCache;

    static EqCache create() {
      return new EqCache(true);
    }

    static EqCache createWithoutStructuralTyping() {
      return new EqCache(false);
    }

    private EqCache(boolean isStructuralTyping) {
      super(isStructuralTyping);
      this.matchCache = null;
    }

    void updateCache(JSType left, JSType right, MatchStatus isMatch) {
      updateCacheAnyType(left, right, isMatch);
    }

    void updateCache(TemplateTypeMap left, TemplateTypeMap right, MatchStatus isMatch) {
      updateCacheAnyType(left, right, isMatch);
    }

    private void updateCacheAnyType(Object t1, Object t2, MatchStatus isMatch) {
      IdentityHashMap<Object, MatchStatus> map = this.matchCache.get(t1);
      if (map == null) {
        map = new IdentityHashMap<>();
      }
      map.put(t2, isMatch);
      this.matchCache.put(t1, map);
    }

    @Nullable
    MatchStatus checkCache(JSType left, JSType right) {
      return checkCacheAnyType(left, right);
    }

    @Nullable
    MatchStatus checkCache(TemplateTypeMap left, TemplateTypeMap right) {
      return checkCacheAnyType(left, right);
    }

    private MatchStatus checkCacheAnyType(Object t1, Object t2) {
      if (this.matchCache == null) {
        this.matchCache = new IdentityHashMap<>();
      }
      // check the cache
      if (this.matchCache.containsKey(t1) && this.matchCache.get(t1).containsKey(t2)) {
        return this.matchCache.get(t1).get(t2);
      } else if (this.matchCache.containsKey(t2) && this.matchCache.get(t2).containsKey(t1)) {
        return this.matchCache.get(t2).get(t1);
      } else {
        this.updateCacheAnyType(t1, t2, MatchStatus.PROCESSING);
        return null;
      }
    }
  }

  /**
   * cache used by check sub-type logic
   */
  static class ImplCache extends MatchCache {
    private HashBasedTable<JSType, JSType, MatchStatus> matchCache;

    static ImplCache create() {
      return new ImplCache(true);
    }

    static ImplCache createWithoutStructuralTyping() {
      return new ImplCache(false);
    }

    private ImplCache(boolean isStructuralTyping) {
      super(isStructuralTyping);
      this.matchCache = null;
    }

    void updateCache(JSType subType, JSType superType, MatchStatus isMatch) {
      this.matchCache.put(subType, superType, isMatch);
    }

    MatchStatus checkCache(JSType subType, JSType superType) {
      if (this.matchCache == null) {
        this.matchCache = HashBasedTable.create();
      }
      // check the cache
      if (this.matchCache.contains(subType, superType)) {
        return this.matchCache.get(subType, superType);
      } else {
        this.updateCache(subType, superType, MatchStatus.PROCESSING);
        return null;
      }
    }
  }

  /**
   * Specifies how to express nullability of reference types in annotation strings and error
   * messages. Note that this only applies to the outer-most type. Nullability of generic type
   * arguments is always explicit.
   */
  public enum Nullability {
    /**
     * Include an explicit '!' for non-nullable reference types. This is suitable for use
     * in most type contexts (particularly 'type', 'param', and 'return' annotations).
     */
    EXPLICIT,
    /**
     * Omit the explicit '!' from the outermost non-nullable reference type. This is suitable for
     * use in cases where a single reference type is expected (e.g. 'extends' and 'implements').
     */
    IMPLICIT,
  }

  /**
   * Returns the template type argument in this type's map corresponding to the supertype's template
   * parameter, or the UNKNOWN_TYPE if the supertype template key is not present.
   *
   * <p>Note: this only supports arguments that have a singleton list of template keys, and will
   * throw an exception for arguments with zero or multiple or template keys.
   */
  public JSType getInstantiatedTypeArgument(JSType supertype) {
    TemplateType templateType =
        Iterables.getOnlyElement(supertype.getTemplateTypeMap().getTemplateKeys());
    return getTemplateTypeMap().getResolvedTemplateType(templateType);
  }
}
