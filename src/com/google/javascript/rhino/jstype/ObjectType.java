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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.javascript.rhino.jstype.TernaryValue.FALSE;
import static com.google.javascript.rhino.jstype.TernaryValue.UNKNOWN;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;

/**
 * Object type.
 *
 * In JavaScript, all object types have properties, and each of those
 * properties has a type. Property types may be DECLARED, INFERRED, or
 * UNKNOWN.
 *
 * DECLARED properties have an explicit type annotation, as in:
 * <code>
 * /xx @type {number} x/
 * Foo.prototype.bar = 1;
 * </code>
 * This property may only hold number values, and an assignment to any
 * other type of value is an error.
 *
 * INFERRED properties do not have an explicit type annotation. Rather,
 * we try to find all the possible types that this property can hold.
 * <code>
 * Foo.prototype.bar = 1;
 * </code>
 * If the programmer assigns other types of values to this property,
 * the property will take on the union of all these types.
 *
 * UNKNOWN properties are properties on the UNKNOWN type. The UNKNOWN
 * type has all properties, but we do not know whether they are
 * declared or inferred.
 *
 */
public abstract class ObjectType extends JSType implements Serializable {
  private boolean visited;
  private JSDocInfo docInfo = null;
  private boolean unknown = true;

  ObjectType(JSTypeRegistry registry) {
    super(registry);
  }

  ObjectType(JSTypeRegistry registry, TemplateTypeMap templateTypeMap) {
    super(registry, templateTypeMap);
  }

  /**
   * Returns the property map that manages the set of properties for an object.
   */
  PropertyMap getPropertyMap() {
    return PropertyMap.immutableEmptyMap();
  }

  /**
   * Default getSlot implementation. This gets overridden by FunctionType
   * for lazily-resolved prototypes.
   */
  public Property getSlot(String name) {
    return getPropertyMap().getSlot(name);
  }

  public final Property getOwnSlot(String name) {
    return getPropertyMap().getOwnProperty(name);
  }

  public JSType getTypeOfThis() {
    return null;
  }

  /**
   * Gets the declared default element type.
   *
   * @see TemplatizedType
   */
  public ImmutableList<JSType> getTemplateTypes() {
    return null;
  }

  /**
   * Gets the docInfo for this type.
   */
  @Override
  public JSDocInfo getJSDocInfo() {
    return docInfo;
  }

  /**
   * Sets the docInfo for this type from the given
   * {@link JSDocInfo}. The {@code JSDocInfo} may be {@code null}.
   */
  public void setJSDocInfo(JSDocInfo info) {
    docInfo = info;
  }

  /**
   * Detects a cycle in the implicit prototype chain. This method accesses
   * the {@link #getImplicitPrototype()} method and must therefore be
   * invoked only after the object is sufficiently initialized to respond to
   * calls to this method.<p>
   *
   * @return True iff an implicit prototype cycle was detected.
   */
  final boolean detectImplicitPrototypeCycle() {
    // detecting cycle
    this.visited = true;
    ObjectType p = getImplicitPrototype();
    while (p != null) {
      if (p.visited) {
        return true;
      } else {
        p.visited = true;
      }
      p = p.getImplicitPrototype();
    }

    // clean up
    p = this;
    do {
      p.visited = false;
      p = p.getImplicitPrototype();
    } while (p != null);
    return false;
  }

  /**
   * Detects cycles in either the implicit prototype chain, or the implemented/extended
   * interfaces.<p>
   *
   * @return True iff a cycle was detected.
   */
  final boolean detectInheritanceCycle() {
    if (detectImplicitPrototypeCycle()
        || Iterables.contains(this.getCtorImplementedInterfaces(), this)) {
        return true;
    }
    FunctionType fnType = this.getConstructor();
    return fnType != null && fnType.checkExtendsLoop() != null;
  }

  /**
   * Gets the reference name for this object. This includes named types like constructors,
   * prototypes, and enums. It notably does not include literal types like strings and booleans and
   * structural types.
   *
   * <p>Returning an empty string means something different than returning null. An empty string may
   * indicate an anonymous constructor, which we treat differently than a literal type without a
   * reference name. e.g. in {@link InstanceObjectType#appendTo(StringBuilder, boolean)}
   *
   * @return the object's name or {@code null} if this is an anonymous object
   */
  @Nullable
  public abstract String getReferenceName();

  /**
   * INVARIANT: {@code hasReferenceName()} is true if and only if {@code getReferenceName()} returns
   * a non-null string.
   *
   * @return true if the object is named, false if it is anonymous
   */
  public final boolean hasReferenceName() {
    return getReferenceName() != null;
  }

  /**
   * Due to the complexity of some of our internal type systems, sometimes
   * we have different types constructed by the same constructor.
   * In other parts of the type system, these are called delegates.
   * We construct these types by appending suffixes to the constructor name.
   *
   * The normalized reference name does not have these suffixes, and as such,
   * recollapses these implicit types back to their real type.  Note that
   * suffixes such as ".prototype" can be added <i>after</i> the delegate
   * suffix, so anything after the parentheses must still be retained.
   */
  @Nullable
  public final String getNormalizedReferenceName() {
    String name = getReferenceName();
    if (name != null) {
      int start = name.indexOf('(');
      if (start != -1) {
        int end = name.lastIndexOf(')');
        String prefix = name.substring(0, start);
        return end + 1 % name.length() == 0 ? prefix : prefix + name.substring(end + 1);
      }
    }
    return name;
  }

  @Override
  public String getDisplayName() {
    return getNormalizedReferenceName();
  }

  /**
   * Creates a suffix for a proxy delegate.
   * @see #getNormalizedReferenceName
   */
  public static String createDelegateSuffix(String suffix) {
    return "(" + suffix + ")";
  }

  public boolean isAmbiguousObject() {
    return !hasReferenceName();
  }

  public final ObjectType getRawType() {
    TemplatizedType t = toMaybeTemplatizedType();
    return t == null ? this : t.getReferencedType();
  }

  public final ObjectType instantiateGenericsWithUnknown() {
    return this.registry.instantiateGenericsWithUnknown(this);
  }

  @Override
  public TernaryValue testForEquality(JSType that) {
    // super
    TernaryValue result = super.testForEquality(that);
    if (result != null) {
      return result;
    }

    // TODO: consider tighten "testForEquality" for subtypes of Object: if Foo and Bar
    // are not related we don't want to allow "==" on them (similiarly we should disallow
    // number == for non-number context values, etc).

    if (that.isSubtypeOf(getNativeType(JSTypeNative.OBJECT_NUMBER_STRING_BOOLEAN_SYMBOL))) {
      return UNKNOWN;
    }

    return FALSE;
  }

  /**
   * Gets this object's constructor.
   * @return this object's constructor or {@code null} if it is a native
   * object (constructed natively v.s. by instantiation of a function)
   */
  public abstract FunctionType getConstructor();

  public FunctionType getSuperClassConstructor() {
    ObjectType iproto = getImplicitPrototype();
    if (iproto == null) {
      return null;
    }
    iproto = iproto.getImplicitPrototype();
    return iproto == null ? null : iproto.getConstructor();
  }

  public final ObjectType getTopDefiningInterface(String propertyName) {
    ObjectType foundType = null;
    if (hasProperty(propertyName)) {
      foundType = this;
    }
    for (ObjectType interfaceType : getCtorExtendedInterfaces()) {
      if (interfaceType.hasProperty(propertyName)) {
        foundType = interfaceType.getTopDefiningInterface(propertyName);
      }
    }
    return foundType;
  }

  /**
   * Gets the implicit prototype (a.k.a. the {@code [[Prototype]]} property).
   */
  public abstract ObjectType getImplicitPrototype();

  /**
   * Defines a property whose type is explicitly declared by the programmer.
   * @param propertyName the property's name
   * @param type the type
   * @param propertyNode the node corresponding to the declaration of property
   *        which might later be accessed using {@code getPropertyNode}.
   */
  public final boolean defineDeclaredProperty(String propertyName,
      JSType type, Node propertyNode) {
    boolean result = defineProperty(propertyName, type, false, propertyNode);
    // All property definitions go through this method
    // or defineInferredProperty. Because the properties defined an an
    // object can affect subtyping, it's slightly more efficient
    // to register this after defining the property.
    registry.registerPropertyOnType(propertyName, this);
    return result;
  }

  /**
   * Defines a property whose type is on a synthesized object. These objects
   * don't actually exist in the user's program. They're just used for
   * bookkeeping in the type system.
   */
  public final boolean defineSynthesizedProperty(String propertyName,
      JSType type, Node propertyNode) {
    return defineProperty(propertyName, type, false, propertyNode);
  }

  /**
   * Defines a property whose type is inferred.
   * @param propertyName the property's name
   * @param type the type
   * @param propertyNode the node corresponding to the inferred definition of
   *        property that might later be accessed using {@code getPropertyNode}.
   */
  public final boolean defineInferredProperty(String propertyName,
      JSType type, Node propertyNode) {
    if (hasProperty(propertyName)) {
      if (isPropertyTypeDeclared(propertyName)) {
        // We never want to hide a declared property with an inferred property.
        return true;
      }
      JSType originalType = getPropertyType(propertyName);
      type = originalType == null ? type : originalType.getLeastSupertype(type);
    }

    boolean result = defineProperty(propertyName, type, true,
        propertyNode);

    // All property definitions go through this method
    // or defineDeclaredProperty. Because the properties defined an an
    // object can affect subtyping, it's slightly more efficient
    // to register this after defining the property.
    registry.registerPropertyOnType(propertyName, this);

    return result;
  }

  /**
   * Defines a property.<p>
   *
   * For clarity, callers should prefer {@link #defineDeclaredProperty} and
   * {@link #defineInferredProperty}.
   *
   * @param propertyName the property's name
   * @param type the type
   * @param inferred {@code true} if this property's type is inferred
   * @param propertyNode the node that represents the definition of property.
   *        Depending on the actual sub-type the node type might be different.
   *        The general idea is to have an estimate of where in the source code
   *        this property is defined.
   * @return True if the property was registered successfully, false if this
   *        conflicts with a previous property type declaration.
   */
  abstract boolean defineProperty(String propertyName, JSType type,
      boolean inferred, Node propertyNode);

  /**
   * Removes the declared or inferred property from this ObjectType.
   *
   * @param propertyName the property's name
   * @return true if the property was removed successfully. False if the
   *         property did not exist, or could not be removed.
   */
  public boolean removeProperty(String propertyName) {
    return false;
  }

  /**
   * Gets the node corresponding to the definition of the specified property.
   * This could be the node corresponding to declaration of the property or the
   * node corresponding to the first reference to this property, e.g.,
   * "this.propertyName" in a constructor. Note this is mainly intended to be
   * an estimate of where in the source code a property is defined. Sometime
   * the returned node is not even part of the global AST but in the AST of the
   * JsDoc that defines a type.
   *
   * @param propertyName the name of the property
   * @return the {@code Node} corresponding to the property or null.
   */
  public final Node getPropertyNode(String propertyName) {
    Property p = getSlot(propertyName);
    return p == null ? null : p.getNode();
  }

  public final Node getPropertyDefSite(String propertyName) {
    return getPropertyNode(propertyName);
  }

  public final JSDocInfo getPropertyJSDocInfo(String propertyName) {
    Property p = getSlot(propertyName);
    return p == null ? null : p.getJSDocInfo();
  }

  /**
   * Gets the docInfo on the specified property on this type.  This should not
   * be implemented recursively, as you generally need to know exactly on
   * which type in the prototype chain the JSDocInfo exists.
   */
  public final JSDocInfo getOwnPropertyJSDocInfo(String propertyName) {
    Property p = getOwnSlot(propertyName);
    return p == null ? null : p.getJSDocInfo();
  }

  public final Node getOwnPropertyDefSite(String propertyName) {
    Property p = getOwnSlot(propertyName);
    return p == null ? null : p.getNode();
  }

  /**
   * Sets the docInfo for the specified property from the
   * {@link JSDocInfo} on its definition.
   * @param info {@code JSDocInfo} for the property definition. May be
   *        {@code null}.
   */
  public void setPropertyJSDocInfo(String propertyName, JSDocInfo info) {
    // by default, do nothing
  }

  /** Sets the node where the property was defined. */
  public void setPropertyNode(String propertyName, Node defSite) {
    // by default, do nothing
  }

  @Override
  public JSType findPropertyType(String propertyName) {
    return hasProperty(propertyName) ? getPropertyType(propertyName) : null;
  }

  /**
   * Gets the property type of the property whose name is given. If the
   * underlying object does not have this property, the Unknown type is
   * returned to indicate that no information is available on this property.
   *
   * This gets overridden by FunctionType for lazily-resolved call() and
   * bind() functions.
   *
   * @return the property's type or {@link UnknownType}. This method never
   *         returns {@code null}.
   */
  public JSType getPropertyType(String propertyName) {
    StaticTypedSlot slot = getSlot(propertyName);
    if (slot == null) {
      if (isNoResolvedType() || isCheckedUnknownType()) {
        return getNativeType(JSTypeNative.CHECKED_UNKNOWN_TYPE);
      } else if (isEmptyType()) {
        return getNativeType(JSTypeNative.NO_TYPE);
      }
      return getNativeType(JSTypeNative.UNKNOWN_TYPE);
    }
    return slot.getType();
  }

  @Override
  public HasPropertyKind getPropertyKind(String propertyName, boolean autobox) {
    // Unknown types have all properties.
    return HasPropertyKind.of(isEmptyType() || isUnknownType() || getSlot(propertyName) != null);
  }

  /**
   * Checks whether the property whose name is given is present directly on
   * the object.  Returns false even if it is declared on a supertype.
   */
  public final HasPropertyKind getOwnPropertyKind(String propertyName) {
    return getOwnSlot(propertyName) != null
        ? HasPropertyKind.KNOWN_PRESENT
        : HasPropertyKind.ABSENT;
  }

  /**
   * Checks whether the property whose name is given is present directly on
   * the object.  Returns false even if it is declared on a supertype.
   */
  public final boolean hasOwnProperty(String propertyName) {
    return !getOwnPropertyKind(propertyName).equals(HasPropertyKind.ABSENT);
  }

  /**
   * Returns the names of all the properties directly on this type.
   *
   * Overridden by FunctionType to add "prototype".
   */
  public Set<String> getOwnPropertyNames() {
    // TODO(sdh): ObjectType specifies that this should include prototype properties,
    // but currently it does not.  Check if this is a constructor and add them, but
    // this could possibly break things so it should be done separately.
    return getPropertyMap().getOwnPropertyNames();
  }

  /**
   * Checks whether the property's type is inferred.
   */
  public final boolean isPropertyTypeInferred(String propertyName) {
    StaticTypedSlot slot = getSlot(propertyName);
    return slot == null ? false : slot.isTypeInferred();
  }

  /**
   * Checks whether the property's type is declared.
   */
  public final boolean isPropertyTypeDeclared(String propertyName) {
    StaticTypedSlot slot = getSlot(propertyName);
    return slot == null ? false : !slot.isTypeInferred();
  }

  @Override
  public boolean isStructuralType() {
    FunctionType constructor = this.getConstructor();
    return constructor != null && constructor.isStructuralInterface();
  }

  /**
   * Whether the given property is declared on this object.
   */
  final boolean hasOwnDeclaredProperty(String name) {
    return hasOwnProperty(name) && isPropertyTypeDeclared(name);
  }

  /** Checks whether the property was defined in the externs. */
  public final boolean isPropertyInExterns(String propertyName) {
    Property p = getSlot(propertyName);
    return p == null ? false : p.isFromExterns();
  }

  /**
   * Gets the number of properties of this object.
   */
  public final int getPropertiesCount() {
    return getPropertyMap().getPropertiesCount();
  }

  /**
   * Check for structural equivalence with {@code that}.
   * (e.g. two @record types with the same prototype properties)
   */
  final boolean checkStructuralEquivalenceHelper(
      ObjectType otherObject, EquivalenceMethod eqMethod, EqCache eqCache) {
    if (this.isTemplatizedType() && this.toMaybeTemplatizedType().wrapsSameRawType(otherObject)) {
      return this.getTemplateTypeMap().checkEquivalenceHelper(
          otherObject.getTemplateTypeMap(), eqMethod, eqCache, SubtypingMode.NORMAL);
    }

    MatchStatus result = eqCache.checkCache(this, otherObject);
    if (result != null) {
      return result.subtypeValue();
    }
    Set<String> keySet = getPropertyNames();
    Set<String> otherKeySet = otherObject.getPropertyNames();
    if (!otherKeySet.equals(keySet)) {
      eqCache.updateCache(this, otherObject, MatchStatus.NOT_MATCH);
      return false;
    }
    for (String key : keySet) {
      if (!otherObject.getPropertyType(key).checkEquivalenceHelper(
              getPropertyType(key), eqMethod, eqCache)) {
        eqCache.updateCache(this, otherObject, MatchStatus.NOT_MATCH);
        return false;
      }
    }
    eqCache.updateCache(this, otherObject, MatchStatus.MATCH);
    return true;
  }

  private static boolean isStructuralSubtypeHelper(
      ObjectType typeA, ObjectType typeB,
      ImplCache implicitImplCache, SubtypingMode subtypingMode) {

    // typeA is a subtype of record type typeB iff:
    // 1) typeA has all the non-optional properties declared in typeB.
    // 2) And for each property of typeB, its type must be
    //    a super type of the corresponding property of typeA.
    for (String property : typeB.getPropertyNames()) {
      JSType propB = typeB.getPropertyType(property);
      if (!typeA.hasProperty(property)) {
        // Currently, any type that explicitly includes undefined (eg, `?|undefined`) is optional.
        if (propB.isExplicitlyVoidable()) {
          continue;
        }
        return false;
      }
      JSType propA = typeA.getPropertyType(property);
      if (!propA.isSubtype(propB, implicitImplCache, subtypingMode)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Determine if {@code this} is a an implicit subtype of {@code superType}.
   */
  final boolean isStructuralSubtype(ObjectType superType,
      ImplCache implicitImplCache, SubtypingMode subtypingMode) {
    // Union types should be handled by isSubtype already
    checkArgument(!this.isUnionType());
    checkArgument(!superType.isUnionType());
    Preconditions.checkArgument(superType.isStructuralType(),
        "isStructuralSubtype should be called with structural supertype. Found %s", superType);

    MatchStatus cachedResult = implicitImplCache.checkCache(this, superType);
    if (cachedResult != null) {
      return cachedResult.subtypeValue();
    }

    boolean result = isStructuralSubtypeHelper(
        this, superType, implicitImplCache, subtypingMode);
    implicitImplCache.updateCache(
        this, superType, result ? MatchStatus.MATCH : MatchStatus.NOT_MATCH);
    return result;
  }

  /**
   * Returns a list of properties defined or inferred on this type and any of
   * its supertypes.
   */
  public final Set<String> getPropertyNames() {
    Set<String> props = new TreeSet<>();
    collectPropertyNames(props);
    return props;
  }

  /**
   * Adds any properties defined on this type or its supertypes to the set.
   */
  final void collectPropertyNames(Set<String> props) {
    getPropertyMap().collectPropertyNames(props);
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseObjectType(this);
  }

  @Override <T> T visit(RelationshipVisitor<T> visitor, JSType that) {
    return visitor.caseObjectType(this, that);
  }

  /**
   * Checks that the prototype is an implicit prototype of this object. Since
   * each object has an implicit prototype, an implicit prototype's
   * implicit prototype is also this implicit prototype's.
   *
   * @param prototype any prototype based object
   *
   * @return {@code true} if {@code prototype} is {@code equal} to any
   *         object in this object's implicit prototype chain.
   */
  final boolean isImplicitPrototype(ObjectType prototype) {
    for (ObjectType current = this;
         current != null;
         current = current.getImplicitPrototype()) {
      if (current.isTemplatizedType()) {
        current = current.toMaybeTemplatizedType().getReferencedType();
      }
      if (current.isEquivalentTo(prototype)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public BooleanLiteralSet getPossibleToBooleanOutcomes() {
    return BooleanLiteralSet.TRUE;
  }

  /**
   * We treat this as the unknown type if any of its implicit prototype
   * properties is unknown.
   */
  @Override
  public boolean isUnknownType() {
    // If the object is unknown now, check the supertype again,
    // because it might have been resolved since the last check.
    if (unknown) {
      ObjectType implicitProto = getImplicitPrototype();
      if (implicitProto == null || implicitProto.isNativeObjectType()) {
        unknown = false;
        for (ObjectType interfaceType : getCtorExtendedInterfaces()) {
          if (interfaceType.isUnknownType()) {
            unknown = true;
            break;
          }
        }
      } else {
        unknown = implicitProto.isUnknownType();
      }
    }
    return unknown;
  }

  @Override
  public boolean isObject() {
    return true;
  }

  /**
   * Returns true if any cached values have been set for this type.  If true,
   * then the prototype chain should not be changed, as it might invalidate the
   * cached values.
   */
  public boolean hasCachedValues() {
    return !unknown;
  }

  /**
   * Clear cached values. Should be called before making changes to a prototype
   * that may have been changed since creation.
   */
  public void clearCachedValues() {
    unknown = true;
  }

  /** Whether this is a built-in object. */
  public boolean isNativeObjectType() {
    return false;
  }

  public final JSType getLegacyResolvedType() {
    return toMaybeNamedType().getReferencedType();
  }

  /**
   * A null-safe version of JSType#toObjectType.
   */
  public static ObjectType cast(JSType type) {
    return type == null ? null : type.toObjectType();
  }

  @Override
  public final boolean isFunctionPrototypeType() {
    return getOwnerFunction() != null;
  }

  public FunctionType getOwnerFunction() {
    return null;
  }

  /** Sets the owner function. By default, does nothing. */
  void setOwnerFunction(FunctionType type) {}

  /**
   * Gets the interfaces implemented by the ctor associated with this type.
   * Intended to be overridden by subclasses.
   */
  public Iterable<ObjectType> getCtorImplementedInterfaces() {
    return ImmutableSet.of();
  }

  /**
   * Gets the interfaces extended by the interface associated with this type.
   * Intended to be overridden by subclasses.
   */
  public Iterable<ObjectType> getCtorExtendedInterfaces() {
    return ImmutableSet.of();
  }

  /**
   * get the map of properties to types covered in an object type
   * @return a Map that maps the property's name to the property's type */
  public Map<String, JSType> getPropertyTypeMap() {
    ImmutableMap.Builder<String, JSType> propTypeMap = ImmutableMap.builder();
    for (String name : this.getPropertyNames()) {
      propTypeMap.put(name, this.getPropertyType(name));
    }
    return propTypeMap.build();
  }

  public JSType getEnumeratedTypeOfEnumObject() {
    return null;
  }
}
