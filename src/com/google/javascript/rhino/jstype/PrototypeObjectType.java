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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.util.Map;
import java.util.Set;

/**
 * The object type represents instances of JavaScript objects such as
 * {@code Object}, {@code Date}, {@code Function}.<p>
 *
 * Objects in JavaScript are unordered collections of properties.
 * Each property consists of a name, a value and a set of attributes.<p>
 *
 * Each instance has an implicit prototype property ({@code [[Prototype]]})
 * pointing to an object instance, which itself has an implicit property, thus
 * forming a chain.<p>
 *
 * A class begins life with no name.  Later, a name may be provided once it
 * can be inferred.  Note that the name in this case is strictly for
 * debugging purposes.  Looking up type name references goes through the
 * {@link JSTypeRegistry}.<p>
 */
class PrototypeObjectType extends ObjectType {
  private static final long serialVersionUID = 1L;

  private final String className;
  private final Map<String, Property> properties;
  private final boolean nativeType;

  // NOTE(nicksantos): The implicit prototype can change over time.
  // Modeling this is a bear. Always call getImplicitPrototype(), because
  // some subclasses override this to do special resolution handling.
  private ObjectType implicitPrototypeFallback;

  // If this is a function prototype, then this is the owner.
  // A PrototypeObjectType can only be the prototype of one function. If we try
  // to do this for multiple functions, then we'll have to create a new one.
  private FunctionType ownerFunction = null;

  // Whether the toString representation of this should be pretty-printed,
  // by printing all properties.
  private boolean prettyPrint = false;

  private static final int MAX_PRETTY_PRINTED_PROPERTIES = 4;

  /**
   * Creates an object type.
   *
   * @param className the name of the class.  May be {@code null} to
   *        denote an anonymous class.
   *
   * @param implicitPrototype the implicit prototype
   *        (a.k.a. {@code [[Prototype]]}) as defined by ECMA-262. If the
   *        implicit prototype is {@code null} the implicit prototype will be
   *        set to the {@link JSTypeNative#OBJECT_TYPE}.
   */
  PrototypeObjectType(JSTypeRegistry registry, String className,
      ObjectType implicitPrototype) {
    this(registry, className, implicitPrototype, false);
  }

  /**
   * Creates an object type, allowing specification of the implicit prototype
   * when creating native objects.
   */
  PrototypeObjectType(JSTypeRegistry registry, String className,
      ObjectType implicitPrototype, boolean nativeType) {
    super(registry);
    this.properties = Maps.newTreeMap();
    this.className = className;
    this.nativeType = nativeType;
    if (nativeType || implicitPrototype != null) {
      setImplicitPrototype(implicitPrototype);
    } else {
      setImplicitPrototype(
          registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE));
    }
  }

  @Override
  public Property getSlot(String name) {
    if (properties.containsKey(name)) {
      return properties.get(name);
    }
    ObjectType implicitPrototype = getImplicitPrototype();
    if (implicitPrototype != null) {
      Property prop = implicitPrototype.getSlot(name);
      if (prop != null) {
        return prop;
      }
    }
    for (ObjectType interfaceType : getCtorExtendedInterfaces()) {
      Property prop = interfaceType.getSlot(name);
      if (prop != null) {
        return prop;
      }
    }
    return null;
  }

  /**
   * Gets the number of properties of this object.
   */
  @Override
  public int getPropertiesCount() {
    ObjectType implicitPrototype = getImplicitPrototype();
    if (implicitPrototype == null) {
      return this.properties.size();
    }
    int localCount = 0;
    for (String property : properties.keySet()) {
      if (!implicitPrototype.hasProperty(property)) {
        localCount++;
      }
    }
    return implicitPrototype.getPropertiesCount() + localCount;
  }

  @Override
  public boolean hasProperty(String propertyName) {
    // Unknown types have all properties.
    return isUnknownType() || getSlot(propertyName) != null;
  }

  @Override
  public boolean hasOwnProperty(String propertyName) {
    return properties.get(propertyName) != null;
  }

  @Override
  public Set<String> getOwnPropertyNames() {
    return properties.keySet();
  }

  @Override
  public boolean isPropertyTypeDeclared(String property) {
    StaticSlot<JSType> slot = getSlot(property);
    if (slot == null) {
      return false;
    }
    return !slot.isTypeInferred();
  }

  @Override
  void collectPropertyNames(Set<String> props) {
    for (String prop : properties.keySet()) {
      props.add(prop);
    }
    ObjectType implicitPrototype = getImplicitPrototype();
    if (implicitPrototype != null) {
      implicitPrototype.collectPropertyNames(props);
    }
  }

  @Override
  public boolean isPropertyTypeInferred(String property) {
    StaticSlot<JSType> slot = getSlot(property);
    if (slot == null) {
      return false;
    }
    return slot.isTypeInferred();
  }

  @Override
  public JSType getPropertyType(String property) {
    StaticSlot<JSType> slot = getSlot(property);
    if (slot == null) {
      return getNativeType(JSTypeNative.UNKNOWN_TYPE);
    }
    return slot.getType();
  }

  @Override
  public boolean isPropertyInExterns(String propertyName) {
    Property p = properties.get(propertyName);
    if (p != null) {
      return p.isFromExterns();
    }
    ObjectType implicitPrototype = getImplicitPrototype();
    if (implicitPrototype != null) {
      return implicitPrototype.isPropertyInExterns(propertyName);
    }
    return false;
  }

  @Override
  boolean defineProperty(String name, JSType type, boolean inferred,
      Node propertyNode) {
    if (hasOwnDeclaredProperty(name)) {
      return false;
    }
    Property newProp = new Property(
        name, type, inferred, propertyNode);
    Property oldProp = properties.get(name);
    if (oldProp != null) {
      // This is to keep previously inferred JsDoc info, e.g., in a
      // replaceScript scenario.
      newProp.setJSDocInfo(oldProp.getJSDocInfo());
    }
    properties.put(name, newProp);
    return true;
  }

  @Override
  public boolean removeProperty(String name) {
    return properties.remove(name) != null;
  }

  @Override
  public Node getPropertyNode(String propertyName) {
    Property p = properties.get(propertyName);
    if (p != null) {
      return p.getNode();
    }
    ObjectType implicitPrototype = getImplicitPrototype();
    if (implicitPrototype != null) {
      return implicitPrototype.getPropertyNode(propertyName);
    }
    return null;
  }

  @Override
  public JSDocInfo getOwnPropertyJSDocInfo(String propertyName) {
    Property p = properties.get(propertyName);
    if (p != null) {
      return p.getJSDocInfo();
    }
    return null;
  }

  @Override
  public void setPropertyJSDocInfo(String propertyName, JSDocInfo info) {
    if (info != null) {
      if (!properties.containsKey(propertyName)) {
        // If docInfo was attached, but the type of the property
        // was not defined anywhere, then we consider this an explicit
        // declaration of the property.
        defineInferredProperty(propertyName, getPropertyType(propertyName),
            null);
      }

      // The prototype property is not represented as a normal Property.
      // We probably don't want to attach any JSDoc to it anyway.
      Property property = properties.get(propertyName);
      if (property != null) {
        property.setJSDocInfo(info);
      }
    }
  }

  @Override
  public boolean matchesNumberContext() {
    return isNumberObjectType() || isDateType() || isBooleanObjectType() ||
        isStringObjectType() || hasOverridenNativeProperty("valueOf");
  }

  @Override
  public boolean matchesStringContext() {
    return isTheObjectType() || isStringObjectType() || isDateType() ||
        isRegexpType() || isArrayType() || isNumberObjectType() ||
        isBooleanObjectType() || hasOverridenNativeProperty("toString");
  }

  /**
   * Given the name of a native object property, checks whether the property is
   * present on the object and different from the native one.
   */
  private boolean hasOverridenNativeProperty(String propertyName) {
    if (isNativeObjectType()) {
      return false;
    }

    JSType propertyType = getPropertyType(propertyName);
    ObjectType nativeType =
        this.isFunctionType() ?
        registry.getNativeObjectType(JSTypeNative.FUNCTION_PROTOTYPE) :
        registry.getNativeObjectType(JSTypeNative.OBJECT_PROTOTYPE);
    JSType nativePropertyType = nativeType.getPropertyType(propertyName);
    return propertyType != nativePropertyType;
  }

  @Override
  public JSType unboxesTo() {
    if (isStringObjectType()) {
      return getNativeType(JSTypeNative.STRING_TYPE);
    } else if (isBooleanObjectType()) {
      return getNativeType(JSTypeNative.BOOLEAN_TYPE);
    } else if (isNumberObjectType()) {
      return getNativeType(JSTypeNative.NUMBER_TYPE);
    } else {
      return super.unboxesTo();
    }
  }

  @Override
  public boolean matchesObjectContext() {
    return true;
  }

  @Override
  public boolean canBeCalled() {
    return isRegexpType();
  }

  @Override
  String toStringHelper(boolean forAnnotations) {
    if (hasReferenceName()) {
      return getReferenceName();
    } else if (prettyPrint) {
      // Don't pretty print recursively.
      prettyPrint = false;

      // Use a tree set so that the properties are sorted.
      Set<String> propertyNames = Sets.newTreeSet();
      for (ObjectType current = this;
           current != null && !current.isNativeObjectType() &&
               propertyNames.size() <= MAX_PRETTY_PRINTED_PROPERTIES;
           current = current.getImplicitPrototype()) {
        propertyNames.addAll(current.getOwnPropertyNames());
      }

      StringBuilder sb = new StringBuilder();
      sb.append("{");

      int i = 0;
      for (String property : propertyNames) {
        if (i > 0) {
          sb.append(", ");
        }

        sb.append(property);
        sb.append(": ");
        sb.append(getPropertyType(property).toStringHelper(forAnnotations));

        ++i;
        if (!forAnnotations && i == MAX_PRETTY_PRINTED_PROPERTIES) {
          sb.append(", ...");
          break;
        }
      }

      sb.append("}");

      prettyPrint = true;
      return sb.toString();
    } else {
      return forAnnotations ? "?" : "{...}";
    }
  }

  void setPrettyPrint(boolean prettyPrint) {
    this.prettyPrint = prettyPrint;
  }

  boolean isPrettyPrint() {
    return prettyPrint;
  }

  @Override
  public FunctionType getConstructor() {
    return null;
  }

  @Override
  public ObjectType getImplicitPrototype() {
    return implicitPrototypeFallback;
  }

  /**
   * This should only be reset on the FunctionPrototypeType, only to fix an
   * incorrectly established prototype chain due to the user having a mismatch
   * in super class declaration, and only before properties on that type are
   * processed.
   */
  final void setImplicitPrototype(ObjectType implicitPrototype) {
    checkState(!hasCachedValues());
    this.implicitPrototypeFallback = implicitPrototype;
  }

  @Override
  public String getReferenceName() {
    if (className != null) {
      return className;
    } else if (ownerFunction != null) {
      return ownerFunction.getReferenceName() + ".prototype";
    } else {
      return null;
    }
  }

  @Override
  public boolean hasReferenceName() {
    return className != null || ownerFunction != null;
  }

  @Override
  public boolean isSubtype(JSType that) {
    if (JSType.isSubtypeHelper(this, that)) {
      return true;
    }

    // Union types
    if (that.isUnionType()) {
      // The static {@code JSType.isSubtype} check already decomposed
      // union types, so we don't need to check those again.
      return false;
    }

    // record types
    if (that.isRecordType()) {
      return RecordType.isSubtype(this, that.toMaybeRecordType());
    }

    // Interfaces
    // Find all the interfaces implemented by this class and compare each one
    // to the interface instance.
    ObjectType thatObj = that.toObjectType();
    FunctionType thatCtor = thatObj == null ? null : thatObj.getConstructor();

    if (getConstructor() != null && getConstructor().isInterface()) {
      for (ObjectType thisInterface : getCtorExtendedInterfaces()) {
        if (thisInterface.isSubtype(that)) {
          return true;
        }
      }
    } else if (thatCtor != null && thatCtor.isInterface()) {
      Iterable<ObjectType> thisInterfaces = getCtorImplementedInterfaces();
      for (ObjectType thisInterface : thisInterfaces) {
        if (thisInterface.isSubtype(that)) {
          return true;
        }
      }
    }

    // other prototype based objects
    if (isUnknownType() || implicitPrototypeChainIsUnknown()) {
      // If unsure, say 'yes', to avoid spurious warnings.
      // TODO(user): resolve the prototype chain completely in all cases,
      // to avoid guessing.
      return true;
    }
    return thatObj != null && this.isImplicitPrototype(thatObj);
  }

  private boolean implicitPrototypeChainIsUnknown() {
    ObjectType p = getImplicitPrototype();
    while (p != null) {
      if (p.isUnknownType()) {
        return true;
      }
      p = p.getImplicitPrototype();
    }
    return false;
  }

  @Override
  public boolean hasCachedValues() {
    return super.hasCachedValues();
  }

  /** Whether this is a built-in object. */
  @Override
  public boolean isNativeObjectType() {
    return nativeType;
  }

  @Override
  void setOwnerFunction(FunctionType type) {
    Preconditions.checkState(ownerFunction == null || type == null);
    ownerFunction = type;
  }

  @Override
  public FunctionType getOwnerFunction() {
    return ownerFunction;
  }

  @Override
  public Iterable<ObjectType> getCtorImplementedInterfaces() {
    return isFunctionPrototypeType()
        ? getOwnerFunction().getImplementedInterfaces()
        : ImmutableList.<ObjectType>of();
  }

  @Override
  public Iterable<ObjectType> getCtorExtendedInterfaces() {
    return isFunctionPrototypeType()
        ? getOwnerFunction().getExtendedInterfaces()
        : ImmutableList.<ObjectType>of();
  }

  @Override
  JSType resolveInternal(ErrorReporter t, StaticScope<JSType> scope) {
    setResolvedTypeInternal(this);

    ObjectType implicitPrototype = getImplicitPrototype();
    if (implicitPrototype != null) {
      implicitPrototypeFallback =
          (ObjectType) implicitPrototype.resolve(t, scope);
    }
    for (Property prop : properties.values()) {
      prop.setType(safeResolve(prop.getType(), t, scope));
    }
    return this;
  }

  @Override
  public void matchConstraint(JSType constraint) {
    // We only want to match constraints on anonymous types.
    if (hasReferenceName()) {
      return;
    }

    // Handle the case where the constraint object is a record type.
    //
    // param constraint {{prop: (number|undefined)}}
    // function f(constraint) {}
    // f({});
    //
    // We want to modify the object literal to match the constraint, by
    // taking any each property on the record and trying to match
    // properties on this object.
    if (constraint.isRecordType()) {
      matchRecordTypeConstraint(constraint.toObjectType());
    } else if (constraint.isUnionType()) {
      for (JSType alt : constraint.toMaybeUnionType().getAlternates()) {
        if (alt.isRecordType()) {
          matchRecordTypeConstraint(alt.toObjectType());
        }
      }
    }
  }

  public void matchRecordTypeConstraint(ObjectType constraintObj) {
    for (String prop : constraintObj.getOwnPropertyNames()) {
      JSType propType = constraintObj.getPropertyType(prop);
      if (!isPropertyTypeDeclared(prop)) {
        JSType typeToInfer = propType;
        if (!hasProperty(prop)) {
          typeToInfer = getNativeType(JSTypeNative.VOID_TYPE)
              .getLeastSupertype(propType);
        }
        defineInferredProperty(prop, typeToInfer, null);
      }
    }
  }

}
