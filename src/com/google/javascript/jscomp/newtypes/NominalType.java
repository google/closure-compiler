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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.javascript.rhino.Node;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class NominalType {
  // In the case of a generic type (rawType.typeParameters non-empty) either:
  // a) typeMap is empty, this is an uninstantiated generic type (Foo<T>), or
  // b) typeMap's keys exactly correspond to the type parameters of rawType;
  //    this represents a completely instantiated generic type (Foo<number>).
  private final ImmutableMap<String, JSType> typeMap;
  private final RawNominalType rawType;
  private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");

  private NominalType(
      ImmutableMap<String, JSType> typeMap, RawNominalType rawType) {
    Preconditions.checkState(typeMap.isEmpty() ||
        typeMap.keySet().containsAll(rawType.typeParameters) &&
        rawType.typeParameters.containsAll(typeMap.keySet()));
    this.typeMap = typeMap;
    this.rawType = rawType;
  }

  // This should only be called during GlobalTypeInfo.
  public RawNominalType getRawNominalType() {
    Preconditions.checkState(!isFinalized());
    return this.rawType;
  }

  public JSType getInstanceAsJSType() {
    return (rawType.isGeneric() && !typeMap.isEmpty())
        ? JSType.fromObjectType(ObjectType.fromNominalType(this))
        : rawType.getInstanceAsJSType();
  }

  ObjectKind getObjectKind() {
    return rawType.objectKind;
  }

  Map<String, JSType> getTypeMap() {
    return typeMap;
  }

  boolean isClassy() {
    return !isFunction() && !isObject();
  }

  boolean isFunction() {
    return "Function".equals(rawType.name);
  }

  private boolean isObject() {
    return "Object".equals(rawType.name);
  }

  public boolean isStruct() {
    return rawType.isStruct();
  }

  public boolean isDict() {
    return rawType.isDict();
  }

  public boolean isGeneric() {
    return rawType.isGeneric();
  }

  public boolean isUninstantiatedGenericType() {
    return rawType.isGeneric() && typeMap.isEmpty();
  }

  NominalType instantiateGenerics(List<JSType> types) {
    Preconditions.checkState(types.size() == rawType.typeParameters.size());
    Map<String, JSType> typeMap = new LinkedHashMap<>();
    for (int i = 0; i < rawType.typeParameters.size(); i++) {
      typeMap.put(rawType.typeParameters.get(i), types.get(i));
    }
    return instantiateGenerics(typeMap);
  }

  NominalType instantiateGenerics(Map<String, JSType> newTypeMap) {
    if (newTypeMap.isEmpty()) {
      return this;
    }
    if (!this.rawType.isGeneric()) {
      return this.rawType.wrappedAsNominal;
    }
    ImmutableMap.Builder<String, JSType> builder = ImmutableMap.builder();
    ImmutableMap<String, JSType> resultMap;
    if (!typeMap.isEmpty()) {
      for (String oldKey : typeMap.keySet()) {
        builder.put(oldKey, typeMap.get(oldKey).substituteGenerics(newTypeMap));
      }
      resultMap = builder.build();
    } else {
      for (String newKey : rawType.typeParameters) {
        if (newTypeMap.containsKey(newKey)) {
          builder.put(newKey, newTypeMap.get(newKey));
        }
      }
      resultMap = builder.build();
      if (resultMap.isEmpty()) {
        return this;
      }
      // This works around a bug in FunctionType, because we can't know where
      // FunctionType#receiverType is coming from.
      // If the condition is true, receiverType comes from a method declaration,
      // and we should not create a new type here.
      if (resultMap.size() < rawType.typeParameters.size()) {
        return this;
      }
    }
    return new NominalType(resultMap, this.rawType);
  }

  // Methods that delegate to RawNominalType
  public String getName() {
    return rawType.name;
  }

  // Only used for keys in GlobalTypeInfo
  public RawNominalType getId() {
    return rawType;
  }

  public boolean isClass() {
    return rawType.isClass();
  }

  public boolean isInterface() {
    return rawType.isInterface();
  }

  /** True iff it has all properties and the RawNominalType is immutable */
  public boolean isFinalized() {
    return this.rawType.isFinalized;
  }

  public ImmutableSet<String> getAllPropsOfInterface() {
    return rawType.getAllPropsOfInterface();
  }

  public ImmutableSet<String> getAllPropsOfClass() {
    return rawType.getAllPropsOfClass();
  }

  public NominalType getInstantiatedSuperclass() {
    Preconditions.checkState(rawType.isFinalized);
    if (rawType.superClass == null) {
      return null;
    }
    return rawType.superClass.instantiateGenerics(typeMap);
  }

  public JSType getPrototype() {
    Preconditions.checkState(rawType.isFinalized);
    return rawType.getCtorPropDeclaredType("prototype")
        .substituteGenerics(typeMap);
  }

  public ImmutableSet<NominalType> getInstantiatedInterfaces() {
    Preconditions.checkState(rawType.isFinalized);
    ImmutableSet.Builder<NominalType> result = ImmutableSet.builder();
    for (NominalType interf : rawType.interfaces) {
      result.add(interf.instantiateGenerics(typeMap));
    }
    return result.build();
  }

  Property getProp(String pname) {
    if (this.rawType.name.equals("Array")
        && NUMERIC_PATTERN.matcher(pname).matches()) {
      if (typeMap.isEmpty()) {
        return Property.make(JSType.UNKNOWN, null);
      }
      Preconditions.checkState(typeMap.size() == 1);
      JSType elmType = Iterables.getOnlyElement(typeMap.values());
      return Property.make(elmType, null);
    }
    Property p = rawType.getProp(pname);
    return p == null ? null : p.substituteGenerics(typeMap);
  }

  public JSType getPropDeclaredType(String pname) {
    JSType type = rawType.getInstancePropDeclaredType(pname);
    if (type == null) {
      return null;
    }
    return type.substituteGenerics(typeMap);
  }

  public boolean hasConstantProp(String pname) {
    Property p = rawType.getProp(pname);
    return p != null && p.isConstant();
  }

  static JSType getConstructorObject(FunctionType ctorFn) {
    return ctorFn.nominalType.rawType.getConstructorObject(ctorFn);
  }

  boolean isSubtypeOf(NominalType other) {
    RawNominalType thisRaw = this.rawType;
    if (thisRaw == other.rawType) {
      return areTypeMapsCompatible(other);
    }
    if (other.isInterface()) {
      // If thisRaw is not finalized, thisRaw.interfaces may be null.
      for (NominalType i : thisRaw.getInterfaces()) {
        if (i.instantiateGenerics(this.typeMap).isSubtypeOf(other)) {
          return true;
        }
      }
    }
    // Note that other can still be an interface here (implemented by a superclass)
    return isClass() && thisRaw.superClass != null
        && thisRaw.superClass.instantiateGenerics(this.typeMap).isSubtypeOf(other);
  }

  private boolean areTypeMapsCompatible(NominalType other) {
    Preconditions.checkState(rawType.equals(other.rawType));
    if (this.typeMap.isEmpty()) {
      return other.instantiationIsUnknownOrIdentity();
    }
    if (other.typeMap.isEmpty()) {
      return instantiationIsUnknownOrIdentity();
    }
    for (String typeVar : rawType.getTypeParameters()) {
      Preconditions.checkState(this.typeMap.containsKey(typeVar),
          "Type variable %s not in the domain: %s",
          typeVar, this.typeMap.keySet());
      Preconditions.checkState(other.typeMap.containsKey(typeVar),
          "Other (%s) doesn't contain mapping (%s->%s) from this (%s)",
          other, typeVar, this.typeMap.get(typeVar), this);
      JSType thisType = this.typeMap.get(typeVar);
      JSType otherType = other.typeMap.get(typeVar);
      if (allowCovariantGenerics(this)) {
        if (!thisType.isSubtypeOf(otherType)) {
          return false;
        }
      } else if (!thisType.equals(otherType)
          && JSType.unifyUnknowns(thisType, otherType) == null) {
        return false;
      }
    }
    return true;
  }

  private static boolean allowCovariantGenerics(NominalType nt) {
    // TODO(dimvar): Add Object here when we handle parameterized Object.
    return nt.rawType.name.equals("Array");
  }

  private boolean instantiationIsUnknownOrIdentity() {
    if (this.typeMap.isEmpty()) {
      return true;
    }
    for (String typeVar : this.rawType.getTypeParameters()) {
      Preconditions.checkState(this.typeMap.containsKey(typeVar),
          "Type variable %s not in the domain: %s",
          typeVar, this.typeMap.keySet());
      JSType t = this.typeMap.get(typeVar);
      if (!t.isUnknown() && !t.equals(JSType.fromTypeVar(typeVar))) {
        return false;
      }
    }
    return true;
  }

  // A special-case of join
  static NominalType pickSuperclass(NominalType c1, NominalType c2) {
    if (c1 == null || c2 == null) {
      return null;
    }
    if (c1.isSubtypeOf(c2)) {
      return c2;
    }
    return c2.isSubtypeOf(c1) ? c1 : null;
  }

  // A special-case of meet
  static NominalType pickSubclass(NominalType c1, NominalType c2) {
    if (c1 == null) {
      return c2;
    } else if (c2 == null) {
      return c1;
    }
    if (c1.isSubtypeOf(c2)) {
      return c1;
    }
    return c2.isSubtypeOf(c1) ? c2 : null;
  }

  boolean unifyWithSubtype(NominalType other, List<String> typeParameters,
      Multimap<String, JSType> typeMultimap) {
    other = other.findMatchingAncestorWith(this);
    if (other == null) {
      return false;
    }
    if (!isGeneric()) {
      // Non-generic nominal types don't contribute to the unification.
      return true;
    }
    // Most of the time, both nominal types are already instantiated when
    // unifyWith is called. Rarely, when we call a polymorphic function from the
    // body of a method of a polymorphic class, then other.typeMap is empty.
    // For now, don't do anything fancy in that case.
    Preconditions.checkState(!typeMap.isEmpty());
    if (other.typeMap.isEmpty()) {
      return true;
    }
    boolean hasUnified = true;
    for (String typeParam : this.rawType.typeParameters) {
      JSType fromOtherMap = other.typeMap.get(typeParam);
      Preconditions.checkNotNull(fromOtherMap,
          "Type variable %s not found in map %s", typeParam, other.typeMap);
      hasUnified = hasUnified && this.typeMap.get(typeParam)
          .unifyWithSubtype(fromOtherMap, typeParameters, typeMultimap);
    }
    return hasUnified && (allowCovariantGenerics(this)
        || isInvariantWith(typeMultimap, other));
  }

  // Returns a type with the same raw type as other, but possibly different type maps.
  private NominalType findMatchingAncestorWith(NominalType other) {
    RawNominalType thisRaw = this.rawType;
    if (thisRaw == other.rawType) {
      return this;
    }
    if (other.isInterface()) {
      for (NominalType i : thisRaw.interfaces) {
        NominalType nt = i.instantiateGenerics(this.typeMap).findMatchingAncestorWith(other);
        if (nt != null) {
          return nt;
        }
      }
    }
    // Note that other can still be an interface here (implemented by a superclass)
    if (isClass() && thisRaw.superClass != null) {
      return thisRaw.superClass.instantiateGenerics(this.typeMap)
        .findMatchingAncestorWith(other);
    }
    return null;
  }

  private boolean isInvariantWith(Multimap<String, JSType> typeMultimap, NominalType other) {
    Preconditions.checkState(isGeneric());
    Preconditions.checkState(this.rawType == other.rawType);
    Map<String, JSType> newTypeMap = new LinkedHashMap<>();
    for (String typeVar : typeMultimap.keySet()) {
      Collection<JSType> c = typeMultimap.get(typeVar);
      if (c.size() != 1) {
        return false;
      }
      newTypeMap.put(typeVar, Preconditions.checkNotNull(Iterables.getOnlyElement(c)));
    }
    NominalType instantiated = instantiateGenerics(newTypeMap);
    return Objects.equals(instantiated.typeMap, other.typeMap);
  }

  @Override
  public String toString() {
    return appendTo(new StringBuilder()).toString();
  }

  StringBuilder appendTo(StringBuilder builder) {
    builder.append(rawType.name);
    rawType.appendGenericSuffixTo(builder, typeMap);
    return builder;
  }

  @Override
  public int hashCode() {
    return Objects.hash(typeMap, rawType);
  }

  @Override
  public boolean equals(Object other) {
    if (other == null) {
      return false;
    }
    Preconditions.checkState(other instanceof NominalType);
    NominalType o = (NominalType) other;
    return Objects.equals(typeMap, o.typeMap) && rawType.equals(o.rawType);
  }

  /**
   * Represents a class or interface as defined in the code.
   * If the raw nominal type has an @template, then many nominal types can be
   * created from it by instantiation.
   */
  public static class RawNominalType extends Namespace {
    private final String name;
    // The function node (if any) that defines the type
    private final Node defSite;
    // Each instance of the class has these properties by default
    private PersistentMap<String, Property> classProps = PersistentMap.create();
    // The object pointed to by the prototype property of the constructor of
    // this class has these properties
    private PersistentMap<String, Property> protoProps = PersistentMap.create();
    // For @unrestricted, we are less strict about inexistent-prop warnings than
    // for @struct. We use this map to remember the names of props added outside
    // the constructor and the prototype methods.
    private PersistentMap<String, Property> randomProps = PersistentMap.create();
    // The "static" properties of the constructor are stored in the namespace.
    boolean isFinalized = false;
    // Consider a generic type A<T> which inherits from a generic type B<T>.
    // All instantiated A classes, such as A<number>, A<string>, etc,
    // have the same superclass and interfaces fields, because they have the
    // same raw type. You need to instantiate these fields to get the correct
    // type maps, eg, see NominalType#isSubtypeOf.
    private NominalType superClass = null;
    private ImmutableSet<NominalType> interfaces = null;
    private final boolean isInterface;
    // Used in GlobalTypeInfo to find type mismatches in the inheritance chain.
    private ImmutableSet<String> allProps = null;
    // In GlobalTypeInfo, we request (wrapped) RawNominalTypes in various
    // places. Create them here and cache them to save mem.
    private final NominalType wrappedAsNominal;
    private final JSType wrappedAsJSType;
    private final JSType wrappedAsNullableJSType;
    // Empty iff this type is not generic
    private final ImmutableList<String> typeParameters;
    private final ObjectKind objectKind;
    private FunctionType ctorFn;
    private JSType ctorFnWrappedAsJSType;
    private JSTypes commonTypes;

    private RawNominalType(
        Node defSite, String name, ImmutableList<String> typeParameters,
        boolean isInterface, ObjectKind objectKind) {
      Preconditions.checkNotNull(objectKind);
      Preconditions.checkState(defSite == null || defSite.isFunction());
      if (typeParameters == null) {
        typeParameters = ImmutableList.of();
      }
      this.name = name;
      this.defSite = defSite;
      this.typeParameters = typeParameters;
      this.isInterface = isInterface;
      this.objectKind = objectKind;
      this.wrappedAsNominal = new NominalType(ImmutableMap.<String, JSType>of(), this);
      ObjectType objInstance;
      switch (name) {
        case "Function":
          objInstance = ObjectType.fromFunction(FunctionType.TOP_FUNCTION, this.wrappedAsNominal);
          break;
        case "Object":
          // We do this to avoid having two instances of ObjectType that both
          // represent the top JS object.
          objInstance = ObjectType.TOP_OBJECT;
          break;
        default:
          objInstance = ObjectType.fromNominalType(this.wrappedAsNominal);
      }
      this.wrappedAsJSType = JSType.fromObjectType(objInstance);
      this.wrappedAsNullableJSType = JSType.join(JSType.NULL, this.wrappedAsJSType);
    }

    public static RawNominalType makeUnrestrictedClass(
        Node defSite, QualifiedName name, ImmutableList<String> typeParameters) {
      return new RawNominalType(
          defSite, name.toString(), typeParameters, false, ObjectKind.UNRESTRICTED);
    }

    public static RawNominalType makeStructClass(
        Node defSite, QualifiedName name, ImmutableList<String> typeParameters) {
      return new RawNominalType(
          defSite, name.toString(), typeParameters, false, ObjectKind.STRUCT);
    }

    public static RawNominalType makeDictClass(
        Node defSite, QualifiedName name, ImmutableList<String> typeParameters) {
      return new RawNominalType(
          defSite, name.toString(), typeParameters, false, ObjectKind.DICT);
    }

    public static RawNominalType makeInterface(
        Node defSite, QualifiedName name, ImmutableList<String> typeParameters) {
      // interfaces are struct by default
      return new RawNominalType(
          defSite, name.toString(), typeParameters, true, ObjectKind.STRUCT);
    }

    public String getName() {
      return name;
    }

    public Node getDefSite() {
      return this.defSite;
    }

    public boolean isClass() {
      return !isInterface;
    }

    public boolean isInterface() {
      return isInterface;
    }

    boolean isGeneric() {
      return !typeParameters.isEmpty();
    }

    public boolean isStruct() {
      return objectKind.isStruct();
    }

    public boolean isDict() {
      return objectKind.isDict();
    }

    public boolean isFinalized() {
      return this.isFinalized;
    }

    ImmutableList<String> getTypeParameters() {
      return typeParameters;
    }

    public void setCtorFunction(
        FunctionType ctorFn, JSTypes commonTypes) {
      Preconditions.checkState(!isFinalized);
      this.ctorFn = ctorFn;
      this.commonTypes = commonTypes;
    }

    private boolean hasAncestorClass(RawNominalType ancestor) {
      Preconditions.checkState(ancestor.isClass());
      if (this == ancestor) {
        return true;
      } else if (this.superClass == null) {
        return false;
      } else {
        return this.superClass.rawType.hasAncestorClass(ancestor);
      }
    }

    /** @return Whether the superclass can be added without creating a cycle. */
    public boolean addSuperClass(NominalType superClass) {
      Preconditions.checkState(!isFinalized);
      Preconditions.checkState(this.superClass == null);
      if (superClass.rawType.hasAncestorClass(this)) {
        return false;
      }
      this.superClass = superClass;
      return true;
    }

    private boolean hasAncestorInterface(RawNominalType ancestor) {
      Preconditions.checkState(ancestor.isInterface);
      if (this == ancestor) {
        return true;
      } else if (this.interfaces == null) {
        return false;
      } else {
        for (NominalType superInter : interfaces) {
          if (superInter.rawType.hasAncestorInterface(ancestor)) {
            return true;
          }
        }
        return false;
      }
    }

    /** @return Whether the interface can be added without creating a cycle. */
    public boolean addInterfaces(ImmutableSet<NominalType> interfaces) {
      Preconditions.checkState(!isFinalized);
      Preconditions.checkState(this.interfaces == null);
      Preconditions.checkNotNull(interfaces);
      if (this.isInterface) {
        for (NominalType interf : interfaces) {
          if (interf.rawType.hasAncestorInterface(this)) {
            this.interfaces = ImmutableSet.of();
            return false;
          }
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

    private Property getPropFromClass(String pname) {
      Preconditions.checkState(!isInterface);
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
      Preconditions.checkState(isInterface);
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

    private Property getProp(String pname) {
      if (isInterface) {
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

    private ImmutableSet<String> getAllPropsOfInterface() {
      Preconditions.checkState(isInterface);
      Preconditions.checkState(isFinalized);
      if (allProps == null) {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        if (interfaces != null) {
          for (NominalType interf : interfaces) {
            builder.addAll(interf.rawType.getAllPropsOfInterface());
          }
        }
        allProps = builder.addAll(protoProps.keySet()).build();
      }
      return allProps;
    }

    private ImmutableSet<String> getAllPropsOfClass() {
      Preconditions.checkState(!isInterface);
      Preconditions.checkState(isFinalized);
      if (allProps == null) {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        if (superClass != null) {
          builder.addAll(superClass.rawType.getAllPropsOfClass());
        }
        allProps = builder.addAll(classProps.keySet()).addAll(protoProps.keySet()).build();
      }
      return allProps;
    }

    public void addPropertyWhichMayNotBeOnAllInstances(String pname, JSType type) {
      Preconditions.checkState(!isFinalized);
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
      Preconditions.checkState(!isFinalized);
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
    public void addUndeclaredClassProperty(String pname, Node defSite) {
      Preconditions.checkState(!isFinalized);
      // Only do so if there isn't a declared prop already.
      if (mayHaveProp(pname)) {
        return;
      }
      classProps = classProps.with(pname, Property.makeWithDefsite(defSite, JSType.UNKNOWN, null));
    }

    //////////// Prototype Properties

    /** Add a new non-optional declared prototype property to this class */
    public void addProtoProperty(String pname, Node defSite, JSType type, boolean isConstant) {
      Preconditions.checkState(!isFinalized);
      if (type == null && isConstant) {
        type = JSType.UNKNOWN;
      }
      if (this.classProps.containsKey(pname) &&
          this.classProps.get(pname).getDeclaredType() == null) {
        this.classProps = this.classProps.without(pname);
      }
      if (this.randomProps.containsKey(pname)) {
        this.randomProps = this.randomProps.without(pname);
      }
      this.protoProps = this.protoProps.with(pname, isConstant
          ? Property.makeConstant(defSite, type, type)
          : Property.makeWithDefsite(defSite, type, type));
    }

    /** Add a new undeclared prototype property to this class */
    public void addUndeclaredProtoProperty(String pname, Node defSite) {
      Preconditions.checkState(!isFinalized);
      if (!this.protoProps.containsKey(pname) ||
          this.protoProps.get(pname).getDeclaredType() == null) {
        this.protoProps = this.protoProps.with(pname,
            Property.makeWithDefsite(defSite, JSType.UNKNOWN, null));
        if (this.randomProps.containsKey(pname)) {
          this.randomProps = this.randomProps.without(pname);
        }
      }
    }

    // Returns the object referred to by the prototype property of the
    // constructor of this class.
    private JSType createProtoObject() {
      return JSType.fromObjectType(ObjectType.makeObjectType(
          superClass, protoProps, null, false, ObjectKind.UNRESTRICTED));
    }

    //////////// Constructor Properties

    public boolean hasCtorProp(String pname) {
      return super.hasProp(pname);
    }

    /** Add a new non-optional declared property to this class's constructor */
    public void addCtorProperty(String pname, Node defSite, JSType type, boolean isConstant) {
      Preconditions.checkState(!isFinalized);
      super.addProperty(pname, defSite, type, isConstant);
    }

    /** Add a new undeclared property to this class's constructor */
    public void addUndeclaredCtorProperty(String pname, Node defSite) {
      Preconditions.checkState(!isFinalized);
      super.addUndeclaredProperty(pname, defSite, JSType.UNKNOWN, false);
    }

    public JSType getCtorPropDeclaredType(String pname) {
      return super.getPropDeclaredType(pname);
    }

    // Returns the (function) object referred to by the constructor of this class.
    // TODO(dimvar): this function shouldn't take any arguments; it should
    // construct and cache the result based on the fields.
    // But currently a couple of unit tests break because of "structural"
    // constructors with a different number of arguments.
    // For those, we should just be creating a basic function type, not be
    // adding all the static properties.
    private JSType getConstructorObject(FunctionType ctorFn) {
      Preconditions.checkState(isFinalized);
      if (this.ctorFn != ctorFn || this.ctorFnWrappedAsJSType == null) {
        ObjectType ctorFnAsObj = ObjectType.makeObjectType(
            this.commonTypes.getFunctionType(), this.otherProps, ctorFn,
            ctorFn.isLoose(), ObjectKind.UNRESTRICTED);
        JSType result = withNamedTypes(this.commonTypes, ctorFnAsObj);
        if (this.ctorFn == ctorFn) {
          this.ctorFnWrappedAsJSType = result;
        }
        return result;
      }
      return this.ctorFnWrappedAsJSType;
    }

    private StringBuilder appendGenericSuffixTo(
        StringBuilder builder,
        Map<String, JSType> typeMap) {
      Preconditions.checkState(typeMap.isEmpty() ||
          typeMap.keySet().containsAll(typeParameters));
      if (typeParameters.isEmpty()) {
        return builder;
      }
      boolean firstIteration = true;
      builder.append("<");
      for (String typeParam : typeParameters) {
        if (firstIteration) {
          firstIteration = false;
        } else {
          builder.append(',');
        }
        JSType concrete = typeMap.get(typeParam);
        if (concrete != null) {
          concrete.appendTo(builder);
        } else {
          builder.append(typeParam);
        }
      }
      builder.append('>');
      return builder;
    }

    // If we try to mutate the class after the AST-preparation phase, error.
    public RawNominalType finalizeNominalType() {
      Preconditions.checkState(this.ctorFn != null);
      // System.out.println("Class " + name +
      //     " created with class properties: " + classProps +
      //     " and prototype properties: " + protoProps);
      if (this.interfaces == null) {
        this.interfaces = ImmutableSet.of();
      }
      addCtorProperty("prototype", null, createProtoObject(), false);
      this.isFinalized = true;
      return this;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(name);
      appendGenericSuffixTo(builder, ImmutableMap.<String, JSType>of());
      return builder.toString();
    }

    @Override
    public JSType toJSType(JSTypes commonTypes) {
      Preconditions.checkState(this.isFinalized);
      return getConstructorObject(this.ctorFn);
    }

    public NominalType getAsNominalType() {
      return wrappedAsNominal;
    }

    // Don't confuse with the toJSType method, inherited from Namespace.
    // The namespace is represented by the constructor, so that method wraps the
    // constructor in a JSType, and this method wraps the instance.
    public JSType getInstanceAsJSType() {
      return wrappedAsJSType;
    }

    public JSType getInstanceAsNullableJSType() {
      return wrappedAsNullableJSType;
    }

    // equals and hashCode default to reference equality, which is what we want
  }
}
