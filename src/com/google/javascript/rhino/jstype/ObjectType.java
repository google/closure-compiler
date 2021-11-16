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
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.Property.OwnedProperty;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Object type.
 *
 * <p>In JavaScript, all object types have properties, and each of those properties has a type.
 * Property types may be DECLARED, INFERRED, or UNKNOWN.
 *
 * <p>DECLARED properties have an explicit type annotation, as in: <code>
 * /xx @type {number} x/
 * Foo.prototype.bar = 1;
 * </code> This property may only hold number values, and an assignment to any other type of value
 * is an error.
 *
 * <p>INFERRED properties do not have an explicit type annotation. Rather, we try to find all the
 * possible types that this property can hold. <code>
 * Foo.prototype.bar = 1;
 * </code> If the programmer assigns other types of values to this property, the property will take
 * on the union of all these types.
 *
 * <p>UNKNOWN properties are properties on the UNKNOWN type. The UNKNOWN type has all properties,
 * but we do not know whether they are declared or inferred.
 *
 */
public abstract class ObjectType extends JSType {
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
    OwnedProperty property = getPropertyMap().findClosest(name);
    return property == null ? null : property.getValue();
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
   * reference name. e.g. in {@link InstanceObjectType#appendTo(TypeStringBuilder)}
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

  public final ObjectType getRawType() {
    TemplatizedType t = toMaybeTemplatizedType();
    return t == null ? this : t.getReferencedType();
  }

  @Override
  public Tri testForEquality(JSType that) {
    // super
    Tri result = super.testForEquality(that);
    if (result != null) {
      return result;
    }

    // TODO: consider tighten "testForEquality" for subtypes of Object: if Foo and Bar
    // are not related we don't want to allow "==" on them (similiarly we should disallow
    // number == for non-number context values, etc).

    if (that.isUnknownType()
        || that.isSubtypeOf(getNativeType(JSTypeNative.OBJECT_TYPE))
        || that.isSubtypeOf(getNativeType(JSTypeNative.NUMBER_TYPE))
        || that.isSubtypeOf(getNativeType(JSTypeNative.STRING_TYPE))
        || that.isSubtypeOf(getNativeType(JSTypeNative.BOOLEAN_TYPE))
        || that.isSubtypeOf(getNativeType(JSTypeNative.SYMBOL_TYPE))
        || that.isSubtypeOf(getNativeType(JSTypeNative.BIGINT_TYPE))) {
      return Tri.UNKNOWN;
    }
    return Tri.FALSE;
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

  /** Returns the closest ancestor that defines the property including this type itself. */
  public final ObjectType getClosestDefiningType(String propertyName) {
    OwnedProperty property = getPropertyMap().findClosest(propertyName);
    return property == null ? null : property.getOwner();
  }

  /** Returns the closest definition of the property including this type itself. */
  public final OwnedProperty findClosestDefinition(String propertyName) {
    return getPropertyMap().findClosest(propertyName);
  }

  /**
   * Gets the implicit prototype (a.k.a. the {@code [[Prototype]]} property).
   */
  public abstract ObjectType getImplicitPrototype();

  /**
   * Returns a lazy, dynamic {@link Iterable} for the types forming the implicit prototype chain of
   * this type.
   *
   * <p>The chain is iterated bottom to top; from the nearest ancestor to the most distant.
   * Iteration stops when the next ancestor would be a {@code null} reference.
   *
   * <p>The created {@link Iterator}s will not reflect changes to the prototype chain of elements it
   * has already iterated past, but will reflect those of upcoming elements. Neither the {@link
   * Iterable} nor its {@link Iterator} support mutation.
   */
  public final Iterable<ObjectType> getImplicitPrototypeChain() {
    final ObjectType self = this;

    return () ->
        new AbstractIterator<ObjectType>() {

          private ObjectType next = self; // We increment past this type before first access.

          @Override
          public ObjectType computeNext() {
            next = next.getImplicitPrototype();
            return (next != null) ? next : endOfData();
          }
        };
  }

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
      JSType originalType = checkNotNull(getPropertyType(propertyName));
      type = originalType.getLeastSupertype(type);
    }
    // TODO(b/140764208): verify that if isResolved() then type.isResolved().
    // Defining unresolved properties on resolved types is dangerous because the property type
    // may never be resolved.

    boolean result = defineProperty(propertyName, type, true, propertyNode);

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
  protected JSType findPropertyTypeWithoutConsideringTemplateTypes(String propertyName) {
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
  public final boolean hasOwnDeclaredProperty(String name) {
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

  /** Returns a list of properties defined or inferred on this type and any of its supertypes. */
  public final ImmutableSortedSet<String> getPropertyNames() {
    return getPropertyMap().keySet();
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseObjectType(this);
  }

  @Override <T> T visit(RelationshipVisitor<T> visitor, JSType that) {
    return visitor.caseObjectType(this, that);
  }

  /**
   * Returns whether {@code this} is on the implicit prototype chain of {@code other}.
   *
   * <p>{@code this} need not be the immediate prototype of {@code other}.
   */
  final boolean isImplicitPrototypeOf(ObjectType other) {
    ObjectType unwrappedThis = deeplyUnwrap(this);

    for (other = deeplyUnwrap(other);
        other != null;
        other = deeplyUnwrap(other.getImplicitPrototype())) {
      // The prototype should match exactly.
      // NOTE: the use of "==" here rather than equals is deliberate.  This method
      // is very hot in the type checker and relying on identity improves performance of both
      // type checking/type inferrence and property disambiguation.
      if (identical(unwrappedThis, other)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Recursively unwraps all {@link ProxyObjectType}s, including unwrapping the raw type out of a
   * templatized type.
   *
   * <p>Guaranteed to return a non-proxy type (exception: will return an unresolved NamedType).
   */
  static ObjectType deeplyUnwrap(ObjectType original) {
    ObjectType current = original;
    while (current instanceof ProxyObjectType) {
      if (current.isTemplatizedType()) {
        current = current.toMaybeTemplatizedType().getReferencedType();
      } else if (current.isNamedType()) {
        if (!current.isSuccessfullyResolved()) {
          break;
        }
        current = current.toMaybeNamedType().getReferencedObjTypeInternal();
      } else {
        // TODO(lharker): remove this case and instead fail. Only the Rhino unit tests are
        // triggering this by creating new ProxyObjectTypes.
        current = ((ProxyObjectType) current).getReferencedObjTypeInternal();
      }
    }
    return current;
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
    return propTypeMap.buildOrThrow();
  }

  public JSType getEnumeratedTypeOfEnumObject() {
    return null;
  }
}
