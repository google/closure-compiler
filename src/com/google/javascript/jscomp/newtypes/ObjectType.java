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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class ObjectType extends TypeWithProperties {
  // TODO(dimvar): currently, we can't distinguish between an obj at the top of
  // the proto chain (nominalType = null) and an obj for which we can't figure
  // out its class
  private final NominalType nominalType;
  private final FunctionType fn;
  private final boolean isLoose;
  private final PersistentMap<String, Property> props;
  private final ObjectKind objectKind;

  static final ObjectType TOP_OBJECT = ObjectType.makeObjectType(
      null, null, null, false, ObjectKind.UNRESTRICTED);
  static final ObjectType TOP_STRUCT = ObjectType.makeObjectType(
      null, null, null, false, ObjectKind.STRUCT);
  static final ObjectType TOP_DICT = ObjectType.makeObjectType(
      null, null, null, false, ObjectKind.DICT);

  private ObjectType(NominalType nominalType,
      PersistentMap<String, Property> props, FunctionType fn, boolean isLoose,
      ObjectKind objectKind) {
    Preconditions.checkArgument(fn == null || fn.isLoose() == isLoose,
        "isLoose: %s, fn: %s", isLoose, fn);
    Preconditions.checkArgument(nominalType == null || !isLoose);
    Preconditions.checkArgument(nominalType == null || fn == null,
        "Cannot create object of %s that is callable", nominalType);
    this.nominalType = nominalType;
    this.props = props;
    this.fn = fn;
    this.isLoose = isLoose;
    this.objectKind = objectKind;
  }

  static ObjectType makeObjectType(NominalType nominalType,
      PersistentMap<String, Property> props, FunctionType fn,
      boolean isLoose, ObjectKind ok) {
    if (props == null) {
      props = PersistentMap.create();
    }
    return new ObjectType(nominalType, props, fn, isLoose, ok);
  }

  static ObjectType fromFunction(FunctionType fn) {
    return ObjectType.makeObjectType(
        null, null, fn, fn.isLoose(), ObjectKind.UNRESTRICTED);
  }

  public static ObjectType fromNominalType(NominalType cl) {
    return ObjectType.makeObjectType(cl, null, null, false, cl.getObjectKind());
  }

  /** Construct an object with the given declared non-optional properties. */
  static ObjectType fromProperties(Map<String, JSType> propTypes) {
    PersistentMap<String, Property> props = PersistentMap.create();
    for (Map.Entry<String, JSType> propTypeEntry : propTypes.entrySet()) {
      String propName = propTypeEntry.getKey();
      JSType propType = propTypeEntry.getValue();
      props = props.with(propName, Property.make(propType, propType));
    }
    return ObjectType.makeObjectType(
        null, props, null, false, ObjectKind.UNRESTRICTED);
  }

  boolean isInhabitable() {
    for (Property p : props.values()) {
      if (!p.getType().isInhabitable()) {
        return false;
      }
    }
    // TODO(dimvar): do we need a stricter check for functions?
    return true;
  }

  boolean isRecordType() {
    return nominalType == null && fn == null && !isLoose;
  }

  boolean isStruct() {
    return objectKind.isStruct();
  }

  boolean isLooseStruct() {
    return isLoose && objectKind.isStruct();
  }

  boolean isDict() {
    return objectKind.isDict();
  }

  static ImmutableSet<ObjectType> withLocation(
      Set<ObjectType> objs, String location) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj : objs) {
      newObjs.add(obj.withLocation(location));
    }
    return newObjs.build();
  }

  private ObjectType withLocation(String location) {
    if (props.isEmpty()) {
      return this;
    }
    PersistentMap<String, Property> pm = PersistentMap.create();
    for (Map.Entry<String, Property> entry : props.entrySet()) {
      pm = pm.with(entry.getKey(), entry.getValue().withLocation(location));
    }
    return ObjectType.makeObjectType(nominalType, pm, fn, isLoose, objectKind);
  }

  static ImmutableSet<ObjectType> withLooseObjects(Set<ObjectType> objs) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj : objs) {
      newObjs.add(obj.withLoose());
    }
    return newObjs.build();
  }

  private ObjectType withLoose() {
    // Don't loosen nominal types
    if (this.nominalType != null) {
      return this;
    }
    FunctionType fn = this.fn == null ? null : this.fn.withLoose();
    PersistentMap<String, Property> newProps = PersistentMap.create();
    for (String pname : this.props.keySet()) {
      // It's wrong to warn about a possibly absent property on loose objects.
      newProps = newProps.with(pname, this.props.get(pname).withRequired());
    }
    return ObjectType.makeObjectType(
        nominalType, newProps, fn, true, this.objectKind);
  }

  static ImmutableSet<ObjectType> withoutProperty(
      Set<ObjectType> objs, QualifiedName qname) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj : objs) {
      newObjs.add(obj.withProperty(qname, null));
    }
    return newObjs.build();
  }

  // If the property is already declared, but isDeclared is false, be careful
  // to not un-declare it.
  // If the property is already constant, but isConstant is false, be careful
  // to not un-const it.
  private ObjectType withPropertyHelper(QualifiedName qname, JSType type,
      boolean isDeclared, boolean isConstant) {
    // TODO(blickly): If the prop exists with right type, short circuit here.
    PersistentMap<String, Property> newProps = this.props;
    if (qname.isIdentifier()) {
      String pname = qname.getLeftmostName();
      JSType declType = getDeclaredProp(qname);
      Preconditions.checkState(declType == null || type == null ||
          type.isSubtypeOf(declType),
          "Cannot add property of type %s to a property declared of type %s",
          type, declType);
      if (type == null) {
        type = declType;
      }
      if (declType != null) {
        isDeclared = true;
        if (hasConstantProp(qname)) {
          isConstant = true;
        }
      } else if (isDeclared) {
        declType = type;
      }
      if (type == null && declType == null) {
        newProps = newProps.without(pname);
      } else {
        newProps = newProps.with(pname,
            isConstant ?
            Property.makeConstant(type, declType) :
            Property.make(type, isDeclared ? declType : null));
      }
    } else { // This has a nested object
      String objName = qname.getLeftmostName();
      QualifiedName objQname = new QualifiedName(objName);
      if (!mayHaveProp(objQname)) {
        Preconditions.checkState(type == null);
        return this;
      }
      QualifiedName innerProps = qname.getAllButLeftmost();
      Property objProp = getLeftmostProp(objQname);
      JSType inferred = type == null ?
          objProp.getType().withoutProperty(innerProps) :
          objProp.getType().withProperty(innerProps, type);
      JSType declared = objProp.getDeclaredType();
      newProps = newProps.with(objName, objProp.isOptional() ?
          Property.makeOptional(inferred, declared) :
          Property.make(inferred, declared));
    }
    return ObjectType.makeObjectType(
        nominalType, newProps, fn, isLoose, objectKind);
  }

  ObjectType withProperty(QualifiedName qname, JSType type) {
    return withPropertyHelper(qname, type, false, false);
  }

  static ImmutableSet<ObjectType> withProperty(
      Set<ObjectType> objs, QualifiedName qname, JSType type) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj : objs) {
      newObjs.add(obj.withProperty(qname, type));
    }
    return newObjs.build();
  }

  static ImmutableSet<ObjectType> withDeclaredProperty(Set<ObjectType> objs,
      QualifiedName qname, JSType type, boolean isConstant) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj : objs) {
      newObjs.add(obj.withPropertyHelper(qname, type, true, isConstant));
    }
    return newObjs.build();
  }

  private ObjectType withPropertyRequired(String pname) {
    Property oldProp = this.props.get(pname);
    Property newProp = oldProp == null ?
        Property.make(JSType.UNKNOWN, null) :
        Property.make(oldProp.getType(), oldProp.getDeclaredType());
    return ObjectType.makeObjectType(
        nominalType, this.props.with(pname, newProp), fn,
        isLoose, this.objectKind);
  }

  static ImmutableSet<ObjectType> withPropertyRequired(
      Set<ObjectType> objs, String pname) {
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    for (ObjectType obj : objs) {
      newObjs.add(obj.withPropertyRequired(pname));
    }
    return newObjs.build();
  }

  private static PersistentMap<String, Property> meetPropsHelper(
      boolean specializeProps1, NominalType resultNominalType,
      PersistentMap<String, Property> props1,
      PersistentMap<String, Property> props2) {
    PersistentMap<String, Property> newProps = props1;
    if (resultNominalType != null) {
      for (Map.Entry<String, Property> propsEntry : props1.entrySet()) {
        String pname = propsEntry.getKey();
        Property nomProp = resultNominalType.getProp(pname);
        if (nomProp != null) {
          newProps =
              addOrRemoveProp(newProps, pname, nomProp, propsEntry.getValue());
        }
      }
    }
    for (Map.Entry<String, Property> propsEntry : props2.entrySet()) {
      String pname = propsEntry.getKey();
      Property prop2 = propsEntry.getValue();
      Property newProp;
      if (!props1.containsKey(pname)) {
        newProp = prop2;
      } else {
        Property prop1 = props1.get(pname);
        if (prop1.equals(prop2)) {
          continue;
        }
        newProp = specializeProps1 ?
            prop1.specialize(prop2) :
            Property.meet(prop1, prop2);
      }
      if (resultNominalType != null &&
          resultNominalType.getProp(pname) != null) {
        Property nomProp = resultNominalType.getProp(pname);
        newProps = addOrRemoveProp(newProps, pname, nomProp, newProp);
      } else {
        newProps = newProps.with(pname, newProp);
      }
    }
    return newProps;
  }

  private static PersistentMap<String, Property> addOrRemoveProp(
      PersistentMap<String, Property> props,
      String pname, Property nomProp, Property objProp) {
    JSType propType = objProp.getType();
    JSType nomPropType = nomProp.getType();
    if (!propType.isUnknown() &&
        propType.isSubtypeOf(nomPropType) && !propType.equals(nomPropType)) {
      // We use specialize so that if nomProp is @const, we don't forget it.
      return props.with(pname, nomProp.specialize(objProp));
    }
    return props.without(pname);
  }

  private static PersistentMap<String, Property> joinProps(
      Map<String, Property> props1, Map<String, Property> props2) {
    PersistentMap<String, Property> newProps = PersistentMap.create();
    for (Map.Entry<String, Property> propsEntry : props1.entrySet()) {
      String pname = propsEntry.getKey();
      if (!props2.containsKey(pname)) {
        newProps = newProps.with(pname, propsEntry.getValue().withOptional());
      }
    }
    for (Map.Entry<String, Property> propsEntry : props2.entrySet()) {
      String pname = propsEntry.getKey();
      Property prop2 = propsEntry.getValue();
      if (props1.containsKey(pname)) {
        newProps = newProps.with(
            pname, Property.join(props1.get(pname), prop2));
      } else {
        newProps = newProps.with(pname, prop2.withOptional());
      }
    }
    return newProps;
  }

  private static PersistentMap<String, Property> joinPropsLoosely(
      Map<String, Property> props1, Map<String, Property> props2) {
    PersistentMap<String, Property> newProps = PersistentMap.create();
    for (Map.Entry<String, Property> propsEntry : props1.entrySet()) {
      String pname = propsEntry.getKey();
      if (!props2.containsKey(pname)) {
        newProps = newProps.with(pname, propsEntry.getValue().withRequired());
      }
    }
    for (Map.Entry<String, Property> propsEntry : props2.entrySet()) {
      String pname = propsEntry.getKey();
      Property prop2 = propsEntry.getValue();
      if (props1.containsKey(pname)) {
        newProps = newProps.with(pname,
            Property.join(props1.get(pname), prop2).withRequired());
      } else {
        newProps = newProps.with(pname, prop2.withRequired());
      }
    }
    return newProps;
  }

  static boolean isUnionSubtype(boolean keepLoosenessOfThis,
      Set<ObjectType> objs1, Set<ObjectType> objs2) {
    for (ObjectType obj1 : objs1) {
      boolean foundSupertype = false;
      for (ObjectType obj2 : objs2) {
        if (obj1.isSubtypeOf(keepLoosenessOfThis, obj2)) {
          foundSupertype = true;
          break;
        }
      }
      if (!foundSupertype) {
        return false;
      }
    }
    return true;
  }

  boolean isSubtypeOf(ObjectType obj2) {
    return isSubtypeOf(true, obj2);
  }

  /**
   * Required properties are acceptable where an optional is required,
   * but not vice versa.
   * Optional properties create cycles in the type lattice, eg,
   * { } \le { p: num= }  and also   { p: num= } \le { }.
   */
  boolean isSubtypeOf(boolean keepLoosenessOfThis, ObjectType obj2) {
    if (obj2 == TOP_OBJECT) {
      return true;
    }

    if ((keepLoosenessOfThis && this.isLoose) || obj2.isLoose) {
      return this.isLooseSubtypeOf(obj2);
    }

    if ((this.nominalType == null && obj2.nominalType != null)
        || this.nominalType != null && obj2.nominalType != null &&
        !this.nominalType.isSubclassOf(obj2.nominalType)) {
      return false;
    }

    if (!objectKind.isSubtypeOf(obj2.objectKind)) {
      return false;
    }

    // If nominalType1 < nominalType2, we only need to check that the
    // properties of obj2 are in (obj1 or nominalType1)
    for (Map.Entry<String, Property> entry : obj2.props.entrySet()) {
      String pname = entry.getKey();
      Property prop2 = entry.getValue();
      Property prop1 = this.getLeftmostProp(new QualifiedName(pname));

      if (prop2.isOptional()) {
        if (prop1 != null && !prop1.getType().isSubtypeOf(prop2.getType())) {
          return false;
        }
      } else {
        if (prop1 == null || prop1.isOptional() ||
            !prop1.getType().isSubtypeOf(prop2.getType())) {
          return false;
        }
      }
    }

    if (obj2.fn == null) {
      return true;
    } else if (this.fn == null) {
      // Can only be executed if we have declared types for callable objects.
      return false;
    }
    return this.fn.isSubtypeOf(obj2.fn);
  }

  // We never infer properties as optional on loose objects,
  // and we don't warn about possibly inexistent properties.
  boolean isLooseSubtypeOf(ObjectType obj2) {
    Preconditions.checkState(isLoose || obj2.isLoose);
    if (obj2 == TOP_OBJECT) {
      return true;
    }

    if (!isLoose) {
      if (!objectKind.isSubtypeOf(obj2.objectKind)) {
        return false;
      }
      for (String pname : obj2.props.keySet()) {
        QualifiedName qname = new QualifiedName(pname);
        if (!mayHaveProp(qname) ||
            !getProp(qname).isSubtypeOf(obj2.getProp(qname))) {
          return false;
        }
      }
    } else { // this is loose, obj2 may be loose
      for (String pname : props.keySet()) {
        QualifiedName qname = new QualifiedName(pname);
        if (obj2.mayHaveProp(qname) &&
            !getProp(qname).isSubtypeOf(obj2.getProp(qname))) {
          return false;
        }
      }
    }

    if (obj2.fn == null) {
      return true;
    } else if (this.fn == null) {
      // Can only be executed if we have declared types for callable objects.
      return false;
    }
    return fn.isLooseSubtypeOf(obj2.fn);
  }

  ObjectType specialize(ObjectType other) {
    Preconditions.checkState(
        areRelatedClasses(this.nominalType, other.nominalType));
    NominalType resultNominalType =
        NominalType.pickSubclass(this.nominalType, other.nominalType);
    if (resultNominalType != null) {
      if (fn != null || other.fn != null) {
        return null;
      }
      return ObjectType.makeObjectType(
          resultNominalType,
          meetPropsHelper(true, resultNominalType, this.props, other.props),
          null,
          false,
          ObjectKind.meet(this.objectKind, other.objectKind));
    }
    return ObjectType.makeObjectType(
        null,
        meetPropsHelper(true, resultNominalType, this.props, other.props),
        this.fn == null ? null : this.fn.specialize(other.fn),
        this.isLoose,
        ObjectKind.meet(this.objectKind, other.objectKind));
  }

  static ObjectType meet(ObjectType obj1, ObjectType obj2) {
    Preconditions.checkState(
        areRelatedClasses(obj1.nominalType, obj2.nominalType));
    NominalType resultNominalType =
        NominalType.pickSubclass(obj1.nominalType, obj2.nominalType);
    FunctionType fn = FunctionType.meet(obj1.fn, obj2.fn);
    boolean isLoose = obj1.isLoose && obj2.isLoose ||
        fn != null && fn.isLoose();
    PersistentMap<String, Property> props;
    if (isLoose) {
      props = joinPropsLoosely(obj1.props, obj2.props);
    } else {
      props = meetPropsHelper(false, resultNominalType, obj1.props, obj2.props);
    }
    return ObjectType.makeObjectType(
        resultNominalType,
        props,
        fn,
        isLoose,
        ObjectKind.meet(obj1.objectKind, obj2.objectKind));
  }

  static ObjectType join(ObjectType obj1, ObjectType obj2) {
    Preconditions.checkState(
        areRelatedClasses(obj1.nominalType, obj2.nominalType));
    boolean isLoose = obj1.isLoose || obj2.isLoose;
    FunctionType fn = FunctionType.join(obj1.fn, obj2.fn);
    PersistentMap<String, Property> props;
    if (isLoose) {
      fn = fn == null ? null : fn.withLoose();
      props = joinPropsLoosely(obj1.props, obj2.props);
    } else {
      props = joinProps(obj1.props, obj2.props);
    }
    return ObjectType.makeObjectType(
        NominalType.pickSuperclass(obj1.nominalType, obj2.nominalType),
        props,
        fn,
        isLoose,
        ObjectKind.join(obj1.objectKind, obj2.objectKind));
  }

  static ImmutableSet<ObjectType> joinSets(
      ImmutableSet<ObjectType> objs1, ImmutableSet<ObjectType> objs2) {
    if (objs1 == null) {
      return objs2;
    } else if (objs2 == null) {
      return objs1;
    }
    Set<ObjectType> newObjs = Sets.newHashSet(objs1);
    for (ObjectType obj2 : objs2) {
      boolean addedObj2 = false;
      for (ObjectType obj1 : objs1) {
        NominalType nominalType1 = obj1.nominalType;
        NominalType nominalType2 = obj2.nominalType;
        if (areRelatedClasses(nominalType1, nominalType2)) {
          if (nominalType2 == null && nominalType1 != null &&
              !obj1.isSubtypeOf(obj2) ||
              nominalType1 == null && nominalType2 != null &&
              !obj2.isSubtypeOf(obj1)) {
            // Don't merge other classes with record types
            break;
          }
          newObjs.remove(obj1);
          newObjs.add(join(obj1, obj2));
          addedObj2 = true;
          break;
        }
      }
      if (!addedObj2) {
        newObjs.add(obj2);
      }
    }
    return ImmutableSet.copyOf(newObjs);
  }

  private static boolean areRelatedClasses(NominalType c1, NominalType c2) {
    if (c1 == null || c2 == null) {
      return true;
    }
    return c1.isSubclassOf(c2) || c2.isSubclassOf(c1);
  }

  static ImmutableSet<ObjectType> meetSetsHelper(
      boolean specializeObjs1,
      Set<ObjectType> objs1, Set<ObjectType> objs2) {
    // TODO(dimvar): handle greatest lower bound of interface types
    if (objs1 == null || objs2 == null) {
      return null;
    }
    ImmutableSet.Builder<ObjectType> newObjs = ImmutableSet.builder();
    // This algorithm is a bit suspect since the behavior is not deterministic.
    // e.g. The results for both of the following depend on iteration order:
    // MEET[ {noNom1, Foo} ,  {noNom2, Bar} ]
    // MEET[ {Super} ,  {Sub1, Sub2} ]
    for (ObjectType obj2 : objs2) {
      for (ObjectType obj1 : objs1) {
        // TODO(blickly): Add nominal type for functions (Function) and rethink
        // the logic here.
        if (areRelatedClasses(obj1.nominalType, obj2.nominalType)) {
          ObjectType newObj;
          if (specializeObjs1) {
            newObj = obj1.specialize(obj2);
            if (newObj == null) {
              continue;
            }
          } else {
            newObj = meet(obj1, obj2);
          }
          newObjs.add(newObj);
          break;
        }
      }
    }
    return newObjs.build();
  }

  // This is never called from NewTypeInferenceTest
  @VisibleForTesting
  static ImmutableSet<ObjectType> meetSets(
      Set<ObjectType> objs1, Set<ObjectType> objs2) {
    return meetSetsHelper(false, objs1, objs2);
  }

  static ImmutableSet<ObjectType> specializeSet(
      Set<ObjectType> objs1, Set<ObjectType> objs2) {
    return meetSetsHelper(true, objs1, objs2);
  }

  FunctionType getFunType() {
    return fn;
  }

  NominalType getNominalType() {
    return nominalType;
  }

  @Override
  protected JSType getProp(QualifiedName qname) {
    Property p = getLeftmostProp(qname);
    if (qname.isIdentifier()) {
      return p == null ? JSType.UNDEFINED : p.getType();
    } else {
      Preconditions.checkState(p != null);
      return p.getType().getProp(qname.getAllButLeftmost());
    }
  }

  @Override
  protected JSType getDeclaredProp(QualifiedName qname) {
    Property p = getLeftmostProp(qname);
    if (p == null) {
      return null;
    } else if (qname.isIdentifier()) {
      return p.isDeclared() ? p.getDeclaredType() : null;
    }
    return p.getType().getDeclaredProp(qname.getAllButLeftmost());
  }

  private Property getLeftmostProp(QualifiedName qname) {
    String objName = qname.getLeftmostName();
    Property p = props.get(objName);
    if (p == null && nominalType != null) {
      p = nominalType.getProp(objName);
    }
    return p;
  }

  @Override
  protected boolean mayHaveProp(QualifiedName qname) {
    Property p = getLeftmostProp(qname);
    return p != null &&
        (qname.isIdentifier() ||
        p.getType().mayHaveProp(qname.getAllButLeftmost()));
  }

  @Override
  protected boolean hasProp(QualifiedName qname) {
    Preconditions.checkArgument(qname.isIdentifier());
    Property p = getLeftmostProp(qname);
    if (p == null || p.isOptional()) {
      return false;
    } else {
      return true;
    }
  }

  @Override
  protected boolean hasConstantProp(QualifiedName qname) {
    Preconditions.checkArgument(qname.isIdentifier());
    Property p = getLeftmostProp(qname);
    return p != null && p.isConstant();
  }

  /**
   * Unify the two types symmetrically, given that we have already instantiated
   * the type variables of interest in {@code t1} and {@code t2}, treating
   * JSType.UNKNOWN as a "hole" to be filled.
   * @return The unified type, or null if unification fails
   */
  static ObjectType unifyUnknowns(ObjectType t1, ObjectType t2) {
    if (t1.nominalType != t2.nominalType) {
      return null;
    }
    // TODO(blickly): Use functionType.unifyWith
    if (t1.fn != t2.fn) {
      throw new RuntimeException("Unification of functions not yet supported");
    }
    PersistentMap<String, Property> newProps = PersistentMap.create();
    for (String propName : t1.props.keySet()) {
      Property prop1 = t1.props.get(propName);
      Property prop2 = t2.props.get(propName);
      if (prop2 == null) {
        return null;
      }
      Property p = Property.unifyUnknowns(prop1, prop2);
      if (p == null) {
        return null;
      }
      newProps = newProps.with(propName, p);
    }
    return makeObjectType(t1.nominalType, newProps, t1.fn,
        t1.isLoose || t2.isLoose,
        ObjectKind.join(t1.objectKind, t2.objectKind));
  }

  /**
   * Unify {@code this}, which may contain free type variables,
   * with {@code other}, a concrete type, modifying the supplied
   * {@code typeMultimap} to add any new template varaible type bindings.
   * @return Whether unification succeeded
   */
  boolean unifyWith(ObjectType other, List<String> typeParameters,
      Multimap<String, JSType> typeMultimap) {
    if (nominalType != null && other.nominalType != null) {
      return nominalType.unifyWith(
          other.nominalType, typeParameters, typeMultimap);
    }
    if (nominalType != null || other.nominalType != null) {
      return false;
    }
    if (fn != null) {
      if (other.fn == null ||
          !fn.unifyWith(other.fn, typeParameters, typeMultimap)) {
        return false;
      }
    }
    for (String propName : this.props.keySet()) {
      Property thisProp = props.get(propName);
      Property otherProp = other.props.get(propName);
      if (otherProp == null ||
          !thisProp.unifyWith(otherProp, typeParameters, typeMultimap)) {
        return false;
      }
    }
    return true;
  }

  ObjectType substituteGenerics(Map<String, JSType> concreteTypes) {
    PersistentMap<String, Property> newProps = PersistentMap.create();
    for (String p : props.keySet()) {
      newProps =
          newProps.with(p, props.get(p).substituteGenerics(concreteTypes));
    }
    return new ObjectType(
        nominalType == null ? null :
        nominalType.instantiateGenerics(concreteTypes),
        newProps,
        fn == null ? null : fn.substituteGenerics(concreteTypes),
        isLoose,
        objectKind);
  }

  @Override
  public String toString() {
    return appendTo(new StringBuilder()).toString();
  }

  public StringBuilder appendTo(StringBuilder builder) {
    if (props.isEmpty() ||
         (props.size() == 1 && props.containsKey("prototype"))) {
      if (fn != null) {
        return fn.appendTo(builder);
      } else if (nominalType != null) {
        return nominalType.appendTo(builder);
      }
    }
    if (nominalType != null) {
      nominalType.appendTo(builder);
    } else if (isStruct()) {
      builder.append("struct");
    } else if (isDict()) {
      builder.append("dict");
    }
    if (nominalType == null || !props.isEmpty()) {
      builder.append('{');
      boolean firstIteration = true;
      for (String pname : Sets.newTreeSet(props.keySet())) {
        if (!firstIteration) {
          builder.append(", ");
          firstIteration = false;
        }
        builder.append(pname);
        builder.append(':');
        props.get(pname).appendTo(builder);
      }
      builder.append('}');
    }
    if (isLoose) {
      builder.append(" (loose)");
    }
    return builder;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    Preconditions.checkArgument(o instanceof ObjectType);
    ObjectType obj2 = (ObjectType) o;
    return Objects.equal(fn, obj2.fn) &&
        Objects.equal(nominalType, obj2.nominalType) &&
        Objects.equal(props, obj2.props);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(fn, props, nominalType);
  }
}
