/*
 * Copyright 2013 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.newtypes;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents a class or interface as defined in the code.
 * If the raw nominal type has a @template, then many nominal types can be
 * created from it by instantiation.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class RawNominalType extends Namespace {
  // If true, we can't add more properties to this type.
  private boolean isFrozen;
  // Each instance of the class has these properties by default
  private PersistentMap<String, Property> instanceProps = PersistentMap.create();
  // The object pointed to by the prototype property of the constructor of
  // this class has these properties
  private PersistentMap<String, Property> protoProps = PersistentMap.create();
  // For @unrestricted, we are less strict about inexistent-prop warnings than
  // for @struct. We use this map to remember the names of props added outside
  // the constructor and the prototype methods.
  private PersistentMap<String, Property> randomProps = PersistentMap.create();
  // Consider a generic type A<T> which inherits from a generic type B<T>.
  // All instantiated A classes, such as A<number>, A<string>, etc,
  // have the same superclass and interfaces fields, because they have the
  // same raw type. You need to instantiate these fields to get the correct
  // type maps, eg, see NominalType#isSubtypeOf.
  private NominalType superclass = null;
  // If a type A directly inherits from this type, we put it in the set.
  // If this type is generic, we don't record which instantiation A inherits from.
  // We don't store subclasses for Object because there are too many.

  // These two fields have handled by custom serialization to avoid deserialization NPEs due
  // to the problematic interaction caused by cycles in the graph on objects that implement
  // equals(), hashCode() and Sets.
  private transient Set<RawNominalType> subtypes = new LinkedHashSet<>();
  private transient Collection<NominalType> interfaces = null;

  private final Kind kind;
  private final boolean isAbstractClass;
  // Used in GlobalTypeInfo to find type mismatches in the inheritance chain.
  private ImmutableSet<String> allProps = null;
  // In GlobalTypeInfo, we request (wrapped) RawNominalTypes in various
  // places. Create them here and cache them to save mem.
  private final NominalType wrappedAsNominal;
  private final JSType wrappedAsJSType;
  private final JSType wrappedAsNullableJSType;
  // Empty iff this type is not generic
  private final ImmutableList<String> typeParameters;
  // Not final b/c interfaces that inherit from IObject mutate this during GTI
  private ObjectKind objectKind;
  private FunctionType ctorFn;
  private JSType protoObject;

  private enum Kind {
    CLASS,
    INTERFACE,
    RECORD
  }

  enum PropAccess {
    INCLUDE_STRAY_PROPS,
    EXCLUDE_STRAY_PROPS
  }

  private RawNominalType(
      JSTypes commonTypes, Node defSite, String name,
      ImmutableList<String> typeParameters, Kind kind, ObjectKind objectKind, boolean isAbstract) {
    super(commonTypes, name, defSite);
    checkNotNull(objectKind);
    Preconditions.checkState(isValidDefsite(defSite), "Invalid defsite %s", defSite);
    if (typeParameters == null) {
      typeParameters = ImmutableList.of();
    }
    this.typeParameters = typeParameters;
    // NTI considers IObject to be a record so that, eg, an object literal can
    // be considered to have any IObject type.
    // TODO(dimvar): look into declaring IObject as @record in the default
    // externs, and the special handling here can be removed.
    this.kind = isBuiltinHelper(name, "IObject", defSite) ? Kind.RECORD : kind;
    this.objectKind = isBuiltinHelper(name, "IObject", defSite)
        ? ObjectKind.UNRESTRICTED : objectKind;
    this.isAbstractClass = isAbstract;
    this.wrappedAsNominal = new NominalType(ImmutableMap.<String, JSType>of(), this);
    ObjectType objInstance;

    if (isBuiltinHelper(name, "Function", defSite)) {
      objInstance = ObjectType.fromFunction(this.commonTypes.TOP_FUNCTION, this.wrappedAsNominal);
    } else {
      objInstance = ObjectType.fromNominalType(this.wrappedAsNominal);
    }
    this.wrappedAsJSType = JSType.fromObjectType(objInstance);
    this.wrappedAsNullableJSType = JSType.join(this.commonTypes.NULL, this.wrappedAsJSType);
  }

  private static boolean isValidDefsite(Node defSite) {
    if (defSite == null) {
      return false;
    }
    if (defSite.isFunction()) {
      return true;
    }
    Node parent = defSite.getParent();
    if (defSite.isCall()) {
      return parent.isName() || parent.isAssign();
    }
    if (defSite.isName()) {
      return parent.isVar() && !defSite.hasChildren();
    }
    if (defSite.isGetProp()) {
      return parent.isExprResult();
    }
    return false;
  }

  public static RawNominalType makeClass(JSTypes commonTypes, Node defSite, String name,
      ImmutableList<String> typeParameters, ObjectKind objKind, boolean isAbstract) {
    return new RawNominalType(
        commonTypes, defSite, name, typeParameters, Kind.CLASS, objKind, isAbstract);
  }

  public static RawNominalType makeNominalInterface(JSTypes commonTypes,
      Node defSite, String name, ImmutableList<String> typeParameters, ObjectKind objKind) {
    if (objKind == ObjectKind.DICT) {
      objKind = ObjectKind.UNRESTRICTED;
    }
    return new RawNominalType(
        commonTypes, defSite, name, typeParameters, Kind.INTERFACE, objKind, false);
  }

  public static RawNominalType makeStructuralInterface(JSTypes commonTypes,
      Node defSite, String name, ImmutableList<String> typeParameters, ObjectKind objKind) {
    if (objKind == ObjectKind.DICT) {
      objKind = ObjectKind.UNRESTRICTED;
    }
    return new RawNominalType(
        commonTypes, defSite, name, typeParameters, Kind.RECORD, objKind, false);
  }

  JSTypes getCommonTypes() {
    return this.commonTypes;
  }

  JSType getPrototypeObject() {
    checkState(this.isFrozen);
    return this.protoObject;
  }

  private static boolean isBuiltinHelper(
      String nameToCheck, String builtinName, Node defSite) {
    return defSite != null && defSite.isFromExterns() && nameToCheck.equals(builtinName);
  }

  boolean isBuiltinWithName(String s) {
    return isBuiltinHelper(this.name, s, this.defSite);
  }

  public boolean isBuiltinObject() {
    return isBuiltinHelper(this.name, "Object", this.defSite);
  }

  public boolean isClass() {
    return this.kind == Kind.CLASS;
  }

  public boolean isInterface() {
    return this.kind != Kind.CLASS;
  }

  boolean isStructuralInterface() {
    return this.kind == Kind.RECORD;
  }

  boolean isGeneric() {
    return !typeParameters.isEmpty();
  }

  public boolean isStruct() {
    // The objectKind of interfaces can change during GTI.
    checkState(isFrozen() || isClass());
    return this.objectKind.isStruct();
  }

  public boolean isDict() {
    return this.objectKind.isDict();
  }

  public boolean isAbstractClass() {
    return this.isAbstractClass;
  }

  public boolean isFrozen() {
    return this.isFrozen;
  }

  ImmutableList<String> getTypeParameters() {
    return typeParameters;
  }

  ObjectKind getObjectKind() {
    return this.objectKind;
  }

  public FunctionType getConstructorFunction() {
    return this.ctorFn;
  }

  public void setCtorFunction(FunctionType ctorFn) {
    checkState(!this.isFrozen);
    this.ctorFn = ctorFn;
  }

  public boolean hasAncestorClass(RawNominalType ancestor) {
    checkState(ancestor.isClass());
    if (this == ancestor) {
      return true;
    } else if (this.superclass == null) {
      return false;
    } else {
      return this.superclass.hasAncestorClass(ancestor);
    }
  }

  /** @return Whether the superclass can be added without creating a cycle. */
  public boolean addSuperClass(NominalType superclass) {
    checkState(!this.isFrozen);
    checkState(this.superclass == null);
    if (superclass.hasAncestorClass(this)) {
      return false;
    }
    this.superclass = superclass;
    superclass.getRawNominalType().addSubtype(this);
    return true;
  }

  private void addSubtype(RawNominalType subtype) {
    checkState(!this.isFrozen);
    if (!isBuiltinObject()) {
      this.subtypes.add(subtype);
    }
  }

  Set<JSType> getSubtypesWithProperty(String pname) {
    if (mayHaveProp(pname)) {
      if (this.protoProps.containsKey(pname)) {
        return ImmutableSet.of(this.protoObject);
      }
      return ImmutableSet.of(getInstanceAsJSType());
    }
    HashSet<JSType> typesWithProp = new HashSet<>();
    for (RawNominalType subtype : this.subtypes) {
      typesWithProp.addAll(subtype.getSubtypesWithProperty(pname));
    }
    return typesWithProp;
  }

  boolean isPropDefinedOnSubtype(String pname) {
    return !getSubtypesWithProperty(pname).isEmpty();
  }

  boolean hasAncestorInterface(RawNominalType ancestor) {
    checkState(ancestor.isInterface());
    if (this == ancestor) {
      return true;
    } else if (this.interfaces == null) {
      return false;
    } else {
      for (NominalType superInter : interfaces) {
        if (superInter.hasAncestorInterface(ancestor)) {
          return true;
        }
      }
      return false;
    }
  }

  boolean inheritsFromIObjectReflexive() {
    if (isBuiltinWithName("IObject")) {
      return true;
    }
    if (this.interfaces != null) {
      for (NominalType interf : this.interfaces) {
        if (interf.inheritsFromIObjectReflexive()) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean inheritsFromIObject() {
    return !isBuiltinWithName("IObject") && inheritsFromIObjectReflexive();
  }

  /** @return Whether the interface can be added without creating a cycle. */
  public boolean addInterfaces(ImmutableSet<NominalType> interfaces) {
    checkState(!this.isFrozen);
    checkState(this.interfaces == null);
    checkNotNull(interfaces);
    if (this.isInterface()) {
      for (NominalType interf : interfaces) {
        if (interf.hasAncestorInterface(this)) {
          this.interfaces = ImmutableSet.of();
          return false;
        }
      }
    }
    // TODO(dimvar): When a class extends a class that inherits from IObject,
    // it should be unrestricted.
    for (NominalType interf : interfaces) {
      if (interf.getRawNominalType().inheritsFromIObjectReflexive()) {
        this.objectKind = ObjectKind.UNRESTRICTED;
      }
      interf.getRawNominalType().addSubtype(this);
    }
    this.interfaces = interfaces;
    return true;
  }

  public NominalType getSuperClass() {
    return this.superclass;
  }

  public Iterable<NominalType> getInterfaces() {
    return this.interfaces == null ? ImmutableSet.<NominalType>of() : this.interfaces;
  }

  Set<RawNominalType> getSubtypes() {
    return this.subtypes;
  }

  // Checks for subtyping without taking generics into account
  boolean isSubtypeOf(RawNominalType other) {
    if (this == other || other.isBuiltinObject()) {
      return true;
    }
    if (other.isInterface()) {
      for (NominalType i : getInterfaces()) {
        if (i.isRawSubtypeOf(other.getAsNominalType())) {
          return true;
        }
      }
    }
    // Note that other can still be an interface here (implemented by a superclass)
    return isClass() && this.superclass != null
        && this.superclass.isRawSubtypeOf(other.getAsNominalType());
  }

  Property getNonInheritedProp(String pname, PropAccess propAccess) {
    Property p = instanceProps.get(pname);
    if (p != null) {
      return p;
    }
    if (propAccess == PropAccess.INCLUDE_STRAY_PROPS && randomProps.containsKey(pname)) {
      return randomProps.get(pname);
    }
    return protoProps.get(pname);
  }

  public JSType getProtoPropDeclaredType(String pname) {
    if (this.protoProps.containsKey(pname)) {
      Property p = this.protoProps.get(pname);
      Node defSite = p.getDefSite();
      if (defSite != null && defSite.isGetProp()) {
        JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(defSite);
        JSType declType = p.getDeclaredType();
        if (declType != null
            // Methods have a "declared" type which represents their arity,
            // even when they don't have a jsdoc. Don't include that here.
            && (!declType.isFunctionType() || jsdoc != null)) {
          return declType;
        }
      }
    }
    return null;
  }

  boolean hasAbstractMethod(String pname) {
    Property p = getPropFromClass(pname, PropAccess.EXCLUDE_STRAY_PROPS);
    JSType ptype = p == null ? null : p.getType();
    return ptype != null && ptype.isFunctionType() && ptype.getFunType().isAbstract();
  }

  private Property getPropFromClass(String pname, PropAccess propAccess) {
    checkState(isClass());
    Property p = getNonInheritedProp(pname, propAccess);
    if (p != null) {
      return p;
    }
    if (this.superclass != null) {
      p = this.superclass.getProp(pname, propAccess);
      if (p != null) {
        return p;
      }
    }
    return null;
  }

  private Property getPropFromInterface(String pname, PropAccess propAccess) {
    checkState(isInterface());
    Property p = getNonInheritedProp(pname, propAccess);
    if (p != null) {
      return p;
    }
    if (interfaces != null) {
      for (NominalType interf : interfaces) {
        p = interf.getProp(pname, propAccess);
        if (p != null) {
          return p;
        }
      }
    }
    return null;
  }

  Property getProp(String pname, PropAccess propAccess) {
    if (isInterface()) {
      return getPropFromInterface(pname, propAccess);
    }
    return getPropFromClass(pname, propAccess);
  }

  public boolean mayHaveNonInheritedProp(String pname) {
    return getNonInheritedProp(pname, PropAccess.INCLUDE_STRAY_PROPS) != null;
  }

  public boolean mayHaveOwnNonStrayProp(String pname) {
    return getNonInheritedProp(pname, PropAccess.EXCLUDE_STRAY_PROPS) != null;
  }

  public boolean mayHaveProp(String pname) {
    return getProp(pname, PropAccess.INCLUDE_STRAY_PROPS) != null;
  }

  public boolean mayHaveNonStrayProp(String pname) {
    return getProp(pname, PropAccess.EXCLUDE_STRAY_PROPS) != null;
  }

  public JSType getInstancePropDeclaredType(String pname) {
    Property p = getProp(pname, PropAccess.INCLUDE_STRAY_PROPS);
    if (p == null) {
      return null;
    } else if (p.getDeclaredType() == null && this.superclass != null) {
      return this.superclass.getPropDeclaredType(pname);
    }
    return p.getDeclaredType();
  }

  public Set<String> getAllNonInheritedProps() {
    Set<String> nonInheritedProps = new LinkedHashSet<>();
    nonInheritedProps.addAll(instanceProps.keySet());
    nonInheritedProps.addAll(protoProps.keySet());
    return nonInheritedProps;
  }

  public Set<String> getAllNonInheritedInstanceProps() {
    return instanceProps.keySet();
  }

  /**
   * Returns a set of properties defined or inferred on this type or any of its supertypes.
   */
  ImmutableSet<String> getPropertyNames() {
    return isClass() ? getAllPropsOfClass() : getAllPropsOfInterface();
  }

  /**
   * Return all property names of this interface, including inherited properties.
   */
  private ImmutableSet<String> getAllPropsOfInterface() {
    if (!this.isFrozen) {
      // During GlobalTypeInfo, we sometimes try to check subtyping between
      // structural interfaces, but it's not possible because we may have not
      // seen all their properties yet.
      return null;
    }
    if (isClass()) {
      checkState(this.name.equals("Object"), this.name);
      return getAllPropsOfClass();
    }
    if (this.allProps == null) {
      ImmutableSet.Builder<String> builder = ImmutableSet.builder();
      if (interfaces != null) {
        for (NominalType interf : interfaces) {
          builder.addAll(interf.getPropertyNames());
        }
      }
      this.allProps = builder.addAll(protoProps.keySet()).build();
    }
    return this.allProps;
  }

  /**
   * Return all property names of this class, including inherited properties.
   * We don't look at ancestor interfaces because interface properties also appear as
   * prototype properties of classes.
   */
  private ImmutableSet<String> getAllPropsOfClass() {
    checkState(isClass());
    checkState(this.isFrozen);
    if (this.allProps == null) {
      ImmutableSet.Builder<String> builder = ImmutableSet.builder();
      if (this.superclass != null) {
        builder.addAll(this.superclass.getPropertyNames());
      }
      this.allProps = builder.addAll(instanceProps.keySet()).addAll(protoProps.keySet()).build();
    }
    return this.allProps;
  }

  public void addPropertyWhichMayNotBeOnAllInstances(String pname, JSType type) {
    checkState(!this.isFrozen);
    if (this.instanceProps.containsKey(pname) || this.protoProps.containsKey(pname)) {
      return;
    }
    if (this.objectKind == ObjectKind.UNRESTRICTED) {
      this.randomProps = this.randomProps.with(
          pname, Property.make(type == null ? this.commonTypes.UNKNOWN : type, type));
    }
  }

  //////////// Instance Properties

  /** Add a new non-optional declared property to instances of this class */
  public void addInstanceProperty(String pname, Node defSite, JSType type, boolean isConstant) {
    checkState(!this.isFrozen);
    if (type == null && isConstant) {
      type = this.commonTypes.UNKNOWN;
    }
    this.instanceProps = this.instanceProps.with(pname, isConstant
        ? Property.makeConstant(defSite, type, type)
        : Property.makeWithDefsite(defSite, type, type));
    // Upgrade any proto props to declared, if present
    if (this.protoProps.containsKey(pname)) {
      addProtoProperty(pname, defSite, type, isConstant);
    }
    if (this.randomProps.containsKey(pname)) {
      this.randomProps = this.randomProps.without(pname);
    }
  }

  /** Add a new undeclared property to instances of this class */
  public void addUndeclaredInstanceProperty(String pname, JSType type, Node defSite) {
    checkState(!this.isFrozen);
    // Only do so if there isn't a declared prop already.
    if (mayHaveProp(pname)) {
      return;
    }
    instanceProps = instanceProps.with(pname, Property.makeWithDefsite(defSite, type, null));
  }

  //////////// Prototype Properties

  /** Add a new declared prototype property to this class */
  public void addProtoProperty(String pname, Node defSite, JSType type, boolean isConstant) {
    checkState(!this.isFrozen);
    if (type == null && isConstant) {
      type = this.commonTypes.UNKNOWN;
    }
    // Type the receiver of a method on a @record without an explicit @this as unknown.
    if (isStructuralInterface() && type != null && type.isFunctionType()) {
      JSDocInfo jsdoc = defSite == null ? null : NodeUtil.getBestJSDocInfo(defSite);
      if (jsdoc == null || !jsdoc.hasThisType()) {
        FunctionType newMethodType = type.getFunTypeIfSingletonObj().withUnknownReceiver();
        type = this.commonTypes.fromFunctionType(newMethodType);
      }
    }
    if (this.instanceProps.containsKey(pname)
        && this.instanceProps.get(pname).getDeclaredType() == null) {
      this.instanceProps = this.instanceProps.without(pname);
    }
    if (this.randomProps.containsKey(pname)) {
      this.randomProps = this.randomProps.without(pname);
    }
    Property newProp;
    // If this property already exists and has a defsite, and the defsite we
    // currently have is null, then keep the old defsite.
    if (defSite == null && this.protoProps.containsKey(pname)) {
      defSite = this.protoProps.get(pname).getDefSite();
    }
    if (isConstant) {
      newProp = Property.makeConstant(defSite, type, type);
    } else if (isStructuralInterface() && type != null
        && !type.isUnknown() && this.commonTypes.UNDEFINED.isSubtypeOf(type)) {
      // TODO(dimvar): Handle optional properties on @record of unknown type.
      // See how we do it in jstypecreatorfromjsdoc.
      newProp = Property.makeOptional(defSite, type, type);
    } else {
      newProp = Property.makeWithDefsite(defSite, type, type);
    }
    this.protoProps = this.protoProps.with(pname, newProp);
  }

  /**
   * Update the type of an existing prototype property. We use this when properties are defined
   * on prototype methods.
   */
  public void updateProtoProperty(String pname, JSType type) {
    checkState(this.protoProps.containsKey(pname));
    Property newProp = this.protoProps.get(pname).withNewType(type);
    this.protoProps = this.protoProps.with(pname, newProp);
  }

  /** Add a new undeclared prototype property to this class */
  public void addUndeclaredProtoProperty(String pname, Node defSite, JSType inferredType) {
    checkState(!this.isFrozen);
    Property existingProp = this.protoProps.get(pname);
    if (existingProp != null && existingProp.isDeclared()) {
      return;
    }
    if (existingProp != null) {
      inferredType = JSType.join(existingProp.getType(), inferredType);
    }
    this.protoProps =
        this.protoProps.with(pname, Property.makeWithDefsite(defSite, inferredType, null));
    if (this.randomProps.containsKey(pname)) {
      this.randomProps = this.randomProps.without(pname);
    }
  }

  //////////// Constructor Properties

  /** Add a new non-optional declared property to this class's constructor */
  public void addCtorProperty(String pname, Node defSite, JSType type, boolean isConstant) {
    checkState(!this.isFrozen);
    super.addProperty(pname, defSite, type, isConstant);
  }

  /** Add a new undeclared property to this class's constructor */
  public void addUndeclaredCtorProperty(String pname, Node defSite, JSType inferredType) {
    checkState(!this.isFrozen);
    Property existingProp = getNsProp(pname);
    if (existingProp != null && !existingProp.isDeclared()) {
      inferredType = JSType.join(existingProp.getType(), inferredType);
    }
    super.addUndeclaredProperty(pname, defSite, inferredType, false);
  }

  public JSType getCtorPropDeclaredType(String pname) {
    return super.getPropDeclaredType(pname);
  }

  public void freeze() {
    Preconditions.checkState(
        !this.isFrozen, "Raw type already frozen: %s", this.defSite);
    Preconditions.checkNotNull(
        this.ctorFn, "Null constructor function for raw type: %s", this.defSite);
    if (this.interfaces == null) {
      this.interfaces = ImmutableSet.of();
    }
    if (isInterface()) {
      // When an interface property is not annotated with a type, we don't know
      // at the definition site if it's untyped; it may inherit a type from a
      // superinterface. At finalization, we have seen all supertypes, so we
      // can now safely declare the property with type ?.
      for (Map.Entry<String, Property> entry : this.protoProps.entrySet()) {
        Property prop = entry.getValue();
        if (!prop.isDeclared()) {
          this.protoProps = this.protoProps.with(
              entry.getKey(), Property.makeWithDefsite(
                  prop.getDefSite(), this.commonTypes.UNKNOWN, this.commonTypes.UNKNOWN));
        }
      }
    }
    // Remove random property definitions if a supertype defines these properties
    for (String pname : this.randomProps.keySet()) {
      if (this.superclass != null && this.superclass.mayHaveProp(pname)) {
        this.randomProps = this.randomProps.without(pname);
        continue;
      }
      for (NominalType interf : this.interfaces) {
        if (interf.mayHaveProp(pname)) {
          this.randomProps = this.randomProps.without(pname);
        }
      }
    }
    NominalType protoNT = this.superclass;
    if (protoNT == null) {
      NominalType builtinObj =
          checkNotNull(
              this.commonTypes.getObjectType(), "Missing externs for the builtin Object type");
      protoNT = builtinObj;
    }
    // When Bar<T> extends Foo<T>, all Bar instances (Bar<number>, Bar<string>, ...) have the same
    // prototype object. To avoid instantiating it again and again in
    // NominalType#getPrototypePropertyOfCtor, we instantiate it here with unknowns.
    if (protoNT.isGeneric()) {
      protoNT = protoNT.instantiateGenericsWithUnknown();
    }
    JSType ctorJstype = this.commonTypes.fromFunctionType(ctorFn);
    this.protoObject = JSType.fromObjectType(ObjectType.makeObjectType(
        this.commonTypes, protoNT,
        // NOTE(dimvar): We add the "constructor" property to the prototype object, but we
        // don't update the this.protoProps map. As a result, for a class Foo,
        // Foo.prototype.constructor has a more precise type than (new Foo).constructor,
        // which points back to the definition in Object.prototype.constructor.
        // This handling is a bit imprecise, but still more precise than the old type checker.
        // We do it to work around some tricky type checking issues.
        // For example, when passing an object literal to a context that expects some
        // record Bar, you don't want to include the "constructor" property in the comparison.
        this.protoProps.with("constructor", Property.make(ctorJstype, ctorJstype)),
        null, null, false, ObjectKind.UNRESTRICTED));
    addCtorProperty("prototype", null, this.protoObject, false);
    this.isFrozen = true;
  }

  StringBuilder appendTo(StringBuilder builder, ToStringContext ctx) {
    if (ctx.forAnnotation()) {
      // Note: some synthetic nominal types have a parenthesized segment in their name,
      // which is not compatible with type annotations, so remove it if found.
      int index = name.indexOf('(');
      if (index >= 0) {
        return builder.append(name, 0, index);
      }
    }
    return builder.append(name);
  }

  @Override
  public String toString() {
    return appendTo(new StringBuilder(), ToStringContext.TO_STRING).toString();
  }

  @Override
  protected JSType computeJSType() {
    Preconditions.checkState(this.isFrozen, "Unexpected not-frozen type: %s", this);
    checkState(this.namespaceType == null);
    return JSType.fromObjectType(ObjectType.makeObjectType(
        this.commonTypes, this.commonTypes.getFunctionType(), null, this.ctorFn,
        this, this.ctorFn.isLoose(), ObjectKind.UNRESTRICTED));
  }

  public NominalType getAsNominalType() {
    return this.wrappedAsNominal;
  }

  // Don't confuse with the toJSType method, inherited from Namespace.
  // The namespace is represented by the constructor, so that method wraps the
  // constructor in a JSType, and this method wraps the instance.
  public JSType getInstanceAsJSType() {
    return wrappedAsJSType;
  }

  public JSType getInstanceWithNullability(boolean includeNull) {
    return includeNull ? wrappedAsNullableJSType : wrappedAsJSType;
  }

  public void fixSubtypesAfterDeserialization() {
    if (this.superclass != null) {
      this.superclass.getRawNominalType().addSubtype(this);
    }
    for (NominalType superInterface : this.interfaces) {
      superInterface.getRawNominalType().addSubtype(this);
    }
  }

  public void unfreezeForDeserialization() {
    this.isFrozen = false;
  }

  public void refreezeAfterDeserialization() {
    this.isFrozen = true;
  }

  @GwtIncompatible("ObjectInputStream")
  @SuppressWarnings("unchecked")
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    this.subtypes = new LinkedHashSet<>();
    this.interfaces = (Collection<NominalType>) in.readObject();
  }

  @GwtIncompatible("ObjectOutputStream")
  private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    out.writeObject(new ArrayList<>(this.interfaces));
  }

  // equals and hashCode default to reference equality, which is what we want
}
