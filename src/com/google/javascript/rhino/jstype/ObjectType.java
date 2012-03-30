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

import static com.google.javascript.rhino.jstype.TernaryValue.FALSE;
import static com.google.javascript.rhino.jstype.TernaryValue.UNKNOWN;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.StaticReference;
import com.google.javascript.rhino.jstype.StaticSlot;

import java.io.Serializable;
import java.util.Set;

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
public abstract class ObjectType extends JSType implements StaticScope<JSType> {
  private boolean visited;
  private JSDocInfo docInfo = null;
  private boolean unknown = true;

  ObjectType(JSTypeRegistry registry) {
    super(registry);
  }

  @Override
  public Node getRootNode() { return null; }

  @Override
  public ObjectType getParentScope() {
    return getImplicitPrototype();
  }

  @Override
  public abstract Property getSlot(String name);

  @Override
  public Property getOwnSlot(String name) {
    if (hasOwnProperty(name)) {
      return getSlot(name);
    }
    return null;
  }

  @Override
  public ObjectType getTypeOfThis() {
    return null;
  }

  /**
   * Gets the declared default element type.
   * @see ParameterizedType
   */
  public JSType getParameterType() {
    return null;
  }

  /**
   * Gets the declared default index type.
   * @see IndexedType
   */
  public JSType getIndexType() {
    return null;
  }

  /**
   * Gets the docInfo for this type.
   */
  @Override
  public JSDocInfo getJSDocInfo() {
    if (docInfo != null) {
      return docInfo;
    } else if (getImplicitPrototype() != null) {
      return getImplicitPrototype().getJSDocInfo();
    } else {
      return super.getJSDocInfo();
    }
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
   * The method is not thread safe.<p>
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
   * Gets the reference name for this object. This includes named types
   * like constructors, prototypes, and enums. It notably does not include
   * literal types like strings and booleans and structural types.
   * @return the object's name or {@code null} if this is an anonymous
   *         object
   */
  public abstract String getReferenceName();

  /**
   * Due to the complexity of some of our internal type systems, sometimes
   * we have different types constructed by the same constructor.
   * In other parts of the type system, these are called delegates.
   * We construct these types by appending suffixes to the constructor name.
   *
   * The normalized reference name does not have these suffixes, and as such,
   * recollapses these implicit types back to their real type.
   */
  public String getNormalizedReferenceName() {
    String name = getReferenceName();
    if (name != null) {
      int pos = name.indexOf("(");
      if (pos != -1) {
        return name.substring(0, pos);
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

  /**
   * Returns true if the object is named.
   * @return true if the object is named, false if it is anonymous
   */
  public boolean hasReferenceName() {
    return false;
  }

  @Override
  public TernaryValue testForEquality(JSType that) {
    // super
    TernaryValue result = super.testForEquality(that);
    if (result != null) {
      return result;
    }
    // objects are comparable to everything but null/undefined
    if (that.isSubtype(
            getNativeType(JSTypeNative.OBJECT_NUMBER_STRING_BOOLEAN))) {
      return UNKNOWN;
    } else {
      return FALSE;
    }
  }

  /**
   * Gets this object's constructor.
   * @return this object's constructor or {@code null} if it is a native
   * object (constructed natively v.s. by instantiation of a function)
   */
  public abstract FunctionType getConstructor();

  /**
   * Gets the implicit prototype (a.k.a. the {@code [[Prototype]]} property).
   */
  public abstract ObjectType getImplicitPrototype();

  /**
   * Defines a property whose type is synthesized (i.e. not inferred).
   * @param propertyName the property's name
   * @param type the type
   * @param propertyNode the node corresponding to the declaration of property
   *        which might later be accessed using {@code getPropertyNode}.
   */
  public final boolean defineDeclaredProperty(String propertyName,
      JSType type, Node propertyNode) {
    boolean result = defineProperty(propertyName, type, false,
        propertyNode);

    // All property definitions go through this method
    // or defineDeclaredProperty. Because the properties defined an an
    // object can affect subtyping, it's slightly more efficient
    // to register this after defining the property.
    registry.registerPropertyOnType(propertyName, this);

    return result;
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
      JSType originalType = getPropertyType(propertyName);
      type = originalType == null ? type :
          originalType.getLeastSupertype(type);
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
  public Node getPropertyNode(String propertyName) {
    return null;
  }

  /**
   * Gets the docInfo on the specified property on this type.  This should not
   * be done implemented recursively, as you generally need to know exactly on
   * which type in the prototype chain the JSDocInfo exists.
   */
  public JSDocInfo getOwnPropertyJSDocInfo(String propertyName) {
    return null;
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

  @Override
  public JSType findPropertyType(String propertyName) {
    return hasProperty(propertyName) ?
        getPropertyType(propertyName) : null;
  }

  /**
   * Gets the property type of the property whose name is given. If the
   * underlying object does not have this property, the Unknown type is
   * returned to indicate that no information is available on this property.
   *
   * @return the property's type or {@link UnknownType}. This method never
   *         returns {@code null}.
   */
  public abstract JSType getPropertyType(String propertyName);

  /**
   * Checks whether the property whose name is given is present on the
   * object.
   */
  public abstract boolean hasProperty(String propertyName);

  /**
   * Checks whether the property whose name is given is present directly on
   * the object.  Returns false even if it is declared on a supertype.
   */
  public boolean hasOwnProperty(String propertyName) {
    return hasProperty(propertyName);
  }

  /** Returns the names of all the properties directly on this type. */
  public Set<String> getOwnPropertyNames() {
    return ImmutableSet.of();
  }

  /**
   * Checks whether the property's type is inferred.
   */
  public abstract boolean isPropertyTypeInferred(String propertyName);

  /**
   * Checks whether the property's type is declared.
   */
  public abstract boolean isPropertyTypeDeclared(String propertyName);

  /**
   * Whether the given property is declared on this object.
   */
  boolean hasOwnDeclaredProperty(String name) {
    return hasOwnProperty(name) && isPropertyTypeDeclared(name);
  }

  /** Checks whether the property was defined in the externs. */
  public boolean isPropertyInExterns(String propertyName) {
    return false;
  }

  /**
   * Gets the number of properties of this object.
   */
  public abstract int getPropertiesCount();

  /**
   * Returns a list of properties defined or inferred on this type and any of
   * its supertypes.
   */
  public Set<String> getPropertyNames() {
    Set<String> props = Sets.newTreeSet();
    collectPropertyNames(props);
    return props;
  }

  /**
   * Adds any properties defined on this type or its supertypes to the set.
   */
  abstract void collectPropertyNames(Set<String> props);

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseObjectType(this);
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
      if (implicitProto == null ||
          implicitProto.isNativeObjectType()) {
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

  /** Gets the owner of this if it's a function prototype. */
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
   * Intended to be overriden by subclasses.
   */
  public Iterable<ObjectType> getCtorExtendedInterfaces() {
    return ImmutableSet.of();
  }

  public static final class Property
      implements Serializable, StaticSlot<JSType>, StaticReference<JSType> {
    private static final long serialVersionUID = 1L;

    /**
     * Property's name.
     */
    private final String name;

    /**
     * Property's type.
     */
    private JSType type;

    /**
     * Whether the property's type is inferred.
     */
    private final boolean inferred;

    /**
     * The node corresponding to this property, e.g., a GETPROP node that
     * declares this property.
     */
    private Node propertyNode;

    /**  The JSDocInfo for this property. */
    private JSDocInfo docInfo = null;

    Property(String name, JSType type, boolean inferred,
        Node propertyNode) {
      this.name = name;
      this.type = type;
      this.inferred = inferred;
      this.propertyNode = propertyNode;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public Node getNode() {
      return propertyNode;
    }

    @Override
    public StaticSourceFile getSourceFile() {
      return propertyNode == null ? null : propertyNode.getStaticSourceFile();
    }

    @Override
    public Property getSymbol() {
      return this;
    }

    @Override
    public Property getDeclaration() {
      return propertyNode == null ? null : this;
    }

    @Override
    public JSType getType() {
      return type;
    }

    @Override
    public boolean isTypeInferred() {
      return inferred;
    }

    boolean isFromExterns() {
      return propertyNode == null ? false : propertyNode.isFromExterns();
    }

    void setType(JSType type) {
      this.type = type;
    }

    @Override public JSDocInfo getJSDocInfo() {
      return this.docInfo;
    }

    void setJSDocInfo(JSDocInfo info) {
      this.docInfo = info;
    }

    public void setNode(Node n) {
      this.propertyNode = n;
    }
  }
}
