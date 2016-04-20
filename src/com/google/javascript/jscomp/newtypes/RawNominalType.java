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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.LinkedHashSet;
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
  // The node (if any) that defines the type. Most times it's a function, in
  // rare cases it's a call node.
  private final Node defSite;
  // If true, we can't add more properties to this type.
  private boolean isFinalized;
  // Each instance of the class has these properties by default
  private PersistentMap<String, Property> classProps = PersistentMap.create();
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
  private NominalType superClass = null;
  private ImmutableSet<NominalType> interfaces = null;
  private final Kind kind;
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

  private enum Kind {
    CLASS,
    INTERFACE,
    RECORD
  }

  private RawNominalType(
      Node defSite, String name, ImmutableList<String> typeParameters,
      Kind kind, ObjectKind objectKind) {
    Preconditions.checkNotNull(objectKind);
    Preconditions.checkState(defSite == null || defSite.isFunction()
        || defSite.isCall(), "Expected function or call but found %s",
        Token.name(defSite.getType()));
    if (typeParameters == null) {
      typeParameters = ImmutableList.of();
    }
    this.name = name;
    this.defSite = defSite;
    this.typeParameters = typeParameters;
    this.kind = kind;
    this.objectKind = isBuiltinHelper(name, "IObject", defSite)
        ? ObjectKind.UNRESTRICTED : objectKind;
    this.wrappedAsNominal = new NominalType(ImmutableMap.<String, JSType>of(), this);
    ObjectType objInstance;

    if (isBuiltinHelper(name, "Function", defSite)) {
      objInstance = ObjectType.fromFunction(FunctionType.TOP_FUNCTION, this.wrappedAsNominal);
    } else if (isBuiltinHelper(name, "Object", defSite)) {
      // We do this to avoid having two instances of ObjectType that both
      // represent the top JS object.
      objInstance = ObjectType.TOP_OBJECT;
    } else {
      objInstance = ObjectType.fromNominalType(this.wrappedAsNominal);
    }
    this.wrappedAsJSType = JSType.fromObjectType(objInstance);
    this.wrappedAsNullableJSType = JSType.join(JSType.NULL, this.wrappedAsJSType);
  }

  public static RawNominalType makeUnrestrictedClass(
      Node defSite, String name, ImmutableList<String> typeParameters) {
    return new RawNominalType(
        defSite, name, typeParameters, Kind.CLASS, ObjectKind.UNRESTRICTED);
  }

  public static RawNominalType makeStructClass(
      Node defSite, String name, ImmutableList<String> typeParameters) {
    return new RawNominalType(
        defSite, name, typeParameters, Kind.CLASS, ObjectKind.STRUCT);
  }

  public static RawNominalType makeDictClass(
      Node defSite, String name, ImmutableList<String> typeParameters) {
    return new RawNominalType(
        defSite, name, typeParameters, Kind.CLASS, ObjectKind.DICT);
  }

  public static RawNominalType makeNominalInterface(
      Node defSite, String name, ImmutableList<String> typeParameters) {
    // interfaces are struct by default
    return new RawNominalType(
        defSite, name, typeParameters, Kind.INTERFACE, ObjectKind.STRUCT);
  }

  public static RawNominalType makeStructuralInterface(
      Node defSite, String name, ImmutableList<String> typeParameters) {
    // interfaces are struct by default
    return new RawNominalType(
        defSite, name, typeParameters, Kind.RECORD, ObjectKind.STRUCT);
  }

  public Node getDefSite() {
    return this.defSite;
  }

  private static boolean isBuiltinHelper(
      String nameToCheck, String builtinName, Node defSite) {
    return defSite != null && defSite.isFromExterns()
        && nameToCheck.equals(builtinName);
  }

  boolean isBuiltinWithName(String s) {
    return isBuiltinHelper(this.name, s, this.defSite);
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
    Preconditions.checkState(isFinalized() || isClass());
    return this.objectKind.isStruct();
  }

  public boolean isDict() {
    return this.objectKind.isDict();
  }

  public boolean isFinalized() {
    return this.isFinalized;
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
    Preconditions.checkState(!this.isFinalized);
    this.ctorFn = ctorFn;
  }

  boolean hasAncestorClass(RawNominalType ancestor) {
    Preconditions.checkState(ancestor.isClass());
    if (this == ancestor) {
      return true;
    } else if (this.superClass == null) {
      return false;
    } else {
      return this.superClass.hasAncestorClass(ancestor);
    }
  }

  /** @return Whether the superclass can be added without creating a cycle. */
  public boolean addSuperClass(NominalType superClass) {
    Preconditions.checkState(!this.isFinalized);
    Preconditions.checkState(this.superClass == null);
    if (superClass.hasAncestorClass(this)) {
      return false;
    }
    this.superClass = superClass;
    return true;
  }

  boolean hasAncestorInterface(RawNominalType ancestor) {
    Preconditions.checkState(ancestor.isInterface());
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

  private boolean inheritsFromIObject() {
    Preconditions.checkState(!this.isFinalized);
    if (isBuiltinWithName("IObject")) {
      return true;
    }
    if (this.interfaces != null) {
      for (NominalType interf : this.interfaces) {
        if (interf.getRawNominalType().inheritsFromIObject()) {
          return true;
        }
      }
    }
    return false;
  }

  /** @return Whether the interface can be added without creating a cycle. */
  public boolean addInterfaces(ImmutableSet<NominalType> interfaces) {
    Preconditions.checkState(!this.isFinalized);
    Preconditions.checkState(this.interfaces == null);
    Preconditions.checkNotNull(interfaces);
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
      if (interf.getRawNominalType().inheritsFromIObject()) {
        this.objectKind = ObjectKind.UNRESTRICTED;
      }
    }
    this.interfaces = interfaces;
    return true;
  }

  public NominalType getSuperClass() {
    return superClass;
  }

  public ImmutableSet<NominalType> getInterfaces() {
    return this.interfaces == null ? ImmutableSet.<NominalType>of() : this.interfaces;
  }

  private Property getOwnProp(String pname) {
    Property p = classProps.get(pname);
    if (p != null) {
      return p;
    }
    p = randomProps.get(pname);
    if (p != null) {
      return p;
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

  private Property getPropFromClass(String pname) {
    Preconditions.checkState(isClass());
    Property p = getOwnProp(pname);
    if (p != null) {
      return p;
    }
    if (superClass != null) {
      p = superClass.getProp(pname);
      if (p != null) {
        return p;
      }
    }
    return null;
  }

  private Property getPropFromInterface(String pname) {
    Preconditions.checkState(isInterface());
    Property p = getOwnProp(pname);
    if (p != null) {
      return p;
    }
    if (interfaces != null) {
      for (NominalType interf : interfaces) {
        p = interf.getProp(pname);
        if (p != null) {
          return p;
        }
      }
    }
    return null;
  }

  Property getProp(String pname) {
    if (isInterface()) {
      return getPropFromInterface(pname);
    }
    return getPropFromClass(pname);
  }

  public boolean mayHaveOwnProp(String pname) {
    return getOwnProp(pname) != null;
  }

  public boolean mayHaveProp(String pname) {
    return getProp(pname) != null;
  }

  public JSType getInstancePropDeclaredType(String pname) {
    Property p = getProp(pname);
    if (p == null) {
      return null;
    } else if (p.getDeclaredType() == null && superClass != null) {
      return superClass.getPropDeclaredType(pname);
    }
    return p.getDeclaredType();
  }

  public Set<String> getAllOwnProps() {
    Set<String> ownProps = new LinkedHashSet<>();
    ownProps.addAll(classProps.keySet());
    ownProps.addAll(protoProps.keySet());
    return ownProps;
  }

  ImmutableSet<String> getAllPropsOfInterface() {
    if (!this.isFinalized) {
      // During GlobalTypeInfo, we sometimes try to check subtyping between
      // structural interfaces, but it's not possible because we may have not
      // seen all their properties yet.
      return null;
    }
    if (isClass()) {
      Preconditions.checkState(this.name.equals("Object"));
      return getAllPropsOfClass();
    }
    if (this.allProps == null) {
      ImmutableSet.Builder<String> builder = ImmutableSet.builder();
      if (interfaces != null) {
        for (NominalType interf : interfaces) {
          builder.addAll(interf.getAllPropsOfInterface());
        }
      }
      this.allProps = builder.addAll(protoProps.keySet()).build();
    }
    return this.allProps;
  }

  ImmutableSet<String> getAllPropsOfClass() {
    Preconditions.checkState(isClass());
    Preconditions.checkState(this.isFinalized);
    if (this.allProps == null) {
      ImmutableSet.Builder<String> builder = ImmutableSet.builder();
      if (superClass != null) {
        builder.addAll(superClass.getAllPropsOfClass());
      }
      this.allProps = builder.addAll(classProps.keySet())
          .addAll(protoProps.keySet()).build();
    }
    return this.allProps;
  }

  public void addPropertyWhichMayNotBeOnAllInstances(String pname, JSType type) {
    Preconditions.checkState(!this.isFinalized);
    if (this.classProps.containsKey(pname) || this.protoProps.containsKey(pname)) {
      return;
    }
    if (this.objectKind == ObjectKind.UNRESTRICTED) {
      this.randomProps = this.randomProps.with(
          pname, Property.make(type == null ? JSType.UNKNOWN : type, type));
    }
  }

  //////////// Class Properties

  /** Add a new non-optional declared property to instances of this class */
  public void addClassProperty(String pname, Node defSite, JSType type, boolean isConstant) {
    Preconditions.checkState(!this.isFinalized);
    if (type == null && isConstant) {
      type = JSType.UNKNOWN;
    }
    this.classProps = this.classProps.with(pname, isConstant
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
  public void addUndeclaredClassProperty(String pname, JSType type, Node defSite) {
    Preconditions.checkState(!this.isFinalized);
    // Only do so if there isn't a declared prop already.
    if (mayHaveProp(pname)) {
      return;
    }
    classProps = classProps.with(pname, Property.makeWithDefsite(defSite, type, null));
  }

  //////////// Prototype Properties

  /** Add a new declared prototype property to this class */
  public void addProtoProperty(String pname, Node defSite, JSType type, boolean isConstant) {
    Preconditions.checkState(!this.isFinalized);
    if (type == null && isConstant) {
      type = JSType.UNKNOWN;
    }
    if (this.classProps.containsKey(pname)
        && this.classProps.get(pname).getDeclaredType() == null) {
      this.classProps = this.classProps.without(pname);
    }
    if (this.randomProps.containsKey(pname)) {
      this.randomProps = this.randomProps.without(pname);
    }
    Property newProp;
    if (isConstant) {
      newProp = Property.makeConstant(defSite, type, type);
    } else if (isStructuralInterface() && type != null
        && !type.isUnknown() && JSType.UNDEFINED.isSubtypeOf(type)) {
      // TODO(dimvar): Handle optional properties on @record of unknown type.
      // See how we do it in jstypecreatorfromjsdoc.
      newProp = Property.makeOptional(defSite, type, type);
    } else {
      newProp = Property.makeWithDefsite(defSite, type, type);
    }
    this.protoProps = this.protoProps.with(pname, newProp);
  }

  /** Add a new undeclared prototype property to this class */
  public void addUndeclaredProtoProperty(String pname, Node defSite) {
    Preconditions.checkState(!this.isFinalized);
    if (!this.protoProps.containsKey(pname)
        || this.protoProps.get(pname).getDeclaredType() == null) {
      this.protoProps = this.protoProps.with(pname,
          Property.makeWithDefsite(defSite, JSType.UNKNOWN, null));
      if (this.randomProps.containsKey(pname)) {
        this.randomProps = this.randomProps.without(pname);
      }
    }
  }

  //////////// Constructor Properties

  public boolean hasCtorProp(String pname) {
    return super.hasProp(pname);
  }

  /** Add a new non-optional declared property to this class's constructor */
  public void addCtorProperty(String pname, Node defSite, JSType type, boolean isConstant) {
    Preconditions.checkState(!this.isFinalized);
    super.addProperty(pname, defSite, type, isConstant);
  }

  /** Add a new undeclared property to this class's constructor */
  public void addUndeclaredCtorProperty(String pname, Node defSite) {
    Preconditions.checkState(!this.isFinalized);
    super.addUndeclaredProperty(pname, defSite, JSType.UNKNOWN, false);
  }

  public JSType getCtorPropDeclaredType(String pname) {
    return super.getPropDeclaredType(pname);
  }

  @Override
  public void finalize() {
    Preconditions.checkState(!this.isFinalized);
    Preconditions.checkNotNull(this.ctorFn);
    if (this.interfaces == null) {
      this.interfaces = ImmutableSet.of();
    }
    JSType protoObject = JSType.fromObjectType(ObjectType.makeObjectType(
        this.superClass, this.protoProps,
        null, null, false, ObjectKind.UNRESTRICTED));
    addCtorProperty("prototype", null, protoObject, false);
    this.isFinalized = true;
  }

  StringBuilder appendTo(StringBuilder builder) {
    builder.append(name);
    if (!this.typeParameters.isEmpty()) {
      builder.append("<" + Joiner.on(",").join(this.typeParameters) + ">");
    }
    return builder;
  }

  @Override
  public String toString() {
    return appendTo(new StringBuilder()).toString();
  }

  @Override
  protected JSType computeJSType(JSTypes commonTypes) {
    Preconditions.checkState(this.isFinalized);
    Preconditions.checkState(this.namespaceType == null);
    return JSType.fromObjectType(ObjectType.makeObjectType(
        commonTypes.getFunctionType(), null, ctorFn,
        this, ctorFn.isLoose(), ObjectKind.UNRESTRICTED));
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

  // equals and hashCode default to reference equality, which is what we want
}
